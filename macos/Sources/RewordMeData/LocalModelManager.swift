import CryptoKit
import Foundation
import RewordMeModels

public struct LocalModelProgress: Equatable, Sendable {
    public let receivedBytes: Int64
    public let totalBytes: Int64

    public init(receivedBytes: Int64, totalBytes: Int64) {
        self.receivedBytes = receivedBytes
        self.totalBytes = totalBytes
    }

    public var fraction: Double {
        guard totalBytes > 0 else { return 0 }
        return min(1, max(0, Double(receivedBytes) / Double(totalBytes)))
    }
}

public enum LocalModelState: Equatable, Sendable {
    case notDownloaded
    case downloading(LocalModelProgress)
    case ready(bytes: Int64)
    case failed(String)
}

public struct LocalServerConnection: Equatable, Sendable {
    public let endpoint: URL
    public let apiKey: String
    public let modelID: String

    public init(endpoint: URL, apiKey: String, modelID: String = LocalModelCatalog.defaultModel.id) {
        self.endpoint = endpoint
        self.apiKey = apiKey
        self.modelID = modelID
    }
}

protocol DownloadTaskControlling: AnyObject {
    func resume()
    func cancel()
}

extension URLSessionTask: DownloadTaskControlling {}

/// Atomically publishes the URL task before either Start or Cancel can win.
/// A cancellation that arrives before installation is remembered, while one
/// that arrives between installation and resume sees and cancels the task.
final class DownloadTaskGate: @unchecked Sendable {
    private let lock = NSLock()
    private var task: (any DownloadTaskControlling)?
    private var cancellationRequested = false

    func installAndStart(_ task: any DownloadTaskControlling) {
        lock.lock()
        self.task = task
        let shouldCancel = cancellationRequested
        lock.unlock()
        if shouldCancel { task.cancel() } else { task.resume() }
    }

    func cancel() {
        lock.lock()
        cancellationRequested = true
        let task = self.task
        lock.unlock()
        task?.cancel()
    }

    func clear() {
        lock.lock()
        task = nil
        lock.unlock()
    }
}

private final class DownloadDelegate: NSObject, URLSessionDataDelegate, @unchecked Sendable {
    private let queue: OperationQueue = {
        let queue = OperationQueue()
        queue.maxConcurrentOperationCount = 1
        queue.name = "RewordMe local model download"
        return queue
    }()

    private var session: URLSession!
    private let taskGate = DownloadTaskGate()
    private var continuation: CheckedContinuation<Void, Error>?
    private var output: FileHandle?
    private var received: Int64 = 0
    private var expected: Int64
    private var pendingError: Error?
    private var progress: (@Sendable (LocalModelProgress) -> Void)?

    init(expectedBytes: Int64) {
        expected = expectedBytes
        super.init()
        session = URLSession(configuration: .ephemeral, delegate: self, delegateQueue: queue)
    }

    func start(
        source: URL,
        destination: URL,
        progress: @escaping @Sendable (LocalModelProgress) -> Void
    ) async throws {
        try Task.checkCancellation()
        FileManager.default.createFile(atPath: destination.path, contents: nil)
        output = try FileHandle(forWritingTo: destination)
        self.progress = progress
        try await withTaskCancellationHandler {
            try Task.checkCancellation()
            try await withCheckedThrowingContinuation { continuation in
                self.continuation = continuation
                let request = URLRequest(
                    url: source,
                    cachePolicy: .reloadIgnoringLocalAndRemoteCacheData,
                    timeoutInterval: 120
                )
                let task = session.dataTask(with: request)
                taskGate.installAndStart(task)
            }
        } onCancel: {
            self.cancel()
        }
    }

    func cancel() {
        taskGate.cancel()
    }

    func urlSession(
        _ session: URLSession,
        dataTask: URLSessionDataTask,
        didReceive response: URLResponse,
        completionHandler: @escaping (URLSession.ResponseDisposition) -> Void
    ) {
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            pendingError = RewordError.localModelDownloadFailed("Server returned HTTP \(status).")
            completionHandler(.cancel)
            return
        }
        if response.expectedContentLength > 0 {
            expected = response.expectedContentLength
        }
        progress?(LocalModelProgress(receivedBytes: received, totalBytes: expected))
        completionHandler(.allow)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        do {
            try output?.write(contentsOf: data)
            received += Int64(data.count)
            progress?(LocalModelProgress(receivedBytes: received, totalBytes: expected))
        } catch {
            pendingError = error
            dataTask.cancel()
        }
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didCompleteWithError error: Error?
    ) {
        try? output?.close()
        output = nil
        taskGate.clear()
        self.session.finishTasksAndInvalidate()
        guard let continuation else { return }
        self.continuation = nil

        if let pendingError {
            continuation.resume(throwing: pendingError)
        } else if let urlError = error as? URLError, urlError.code == .cancelled {
            continuation.resume(throwing: CancellationError())
        } else if let error {
            continuation.resume(throwing: error)
        } else {
            continuation.resume()
        }
    }
}

private final class ServerProcessHolder: @unchecked Sendable {
    private let lock = NSLock()
    private var process: Process?

    func set(_ process: Process?) {
        lock.lock()
        let previous = self.process
        self.process = process
        lock.unlock()
        if let previous, previous !== process, previous.isRunning { previous.terminate() }
    }

    func current() -> Process? {
        lock.lock()
        defer { lock.unlock() }
        return process
    }

    func terminate() {
        lock.lock()
        let process = self.process
        self.process = nil
        lock.unlock()
        if let process, process.isRunning { process.terminate() }
    }
}

/// Owns the pinned GGUF download and one loopback-only llama.cpp server. The
/// model is verified before it is made visible, and cancelled downloads never
/// replace a previously working model.
public actor LocalModelManager {
    private let fileManager: FileManager
    private let modelDirectory: URL
    private let runtimeOverride: URL?
    private let processHolder = ServerProcessHolder()
    private var downloader: DownloadDelegate?
    private var downloadingModelID: String?
    private var cachedConnection: LocalServerConnection?
    private var startupTask: Task<LocalServerConnection, Error>?
    private var startupID: UUID?
    private var startupModelID: String?
    private var isShuttingDown = false
    private var stateCache: [String: LocalModelState] = [:]

    public init(
        modelDirectory: URL? = nil,
        runtimeURL: URL? = nil,
        fileManager: FileManager = .default
    ) {
        self.fileManager = fileManager
        self.modelDirectory = modelDirectory ?? fileManager.urls(
            for: .applicationSupportDirectory, in: .userDomainMask
        )[0].appendingPathComponent("RewordMe/Models", isDirectory: true)
        runtimeOverride = runtimeURL
    }

    public func state(modelID: String = LocalModelCatalog.defaultModel.id) async -> LocalModelState {
        let manifest = LocalModelCatalog.model(id: modelID)
        if downloadingModelID == manifest.id,
           case let .downloading(progress) = stateCache[manifest.id] {
            return .downloading(progress)
        }
        do {
            let size = try await validateInstalledModel(manifest)
            stateCache[manifest.id] = .ready(bytes: size)
        } catch {
            stateCache[manifest.id] = .notDownloaded
        }
        return stateCache[manifest.id] ?? .notDownloaded
    }

    public func download(
        modelID: String = LocalModelCatalog.defaultModel.id,
        progress: @escaping @Sendable (LocalModelProgress) -> Void
    ) async throws {
        let manifest = LocalModelCatalog.model(id: modelID)
        if case .ready = await state(modelID: manifest.id) { return }
        guard downloader == nil else {
            throw RewordError.localModelDownloadFailed("Another offline model is already downloading.")
        }
        try Task.checkCancellation()
        try fileManager.createDirectory(at: modelDirectory, withIntermediateDirectories: true)
        let partialURL = partialURL(for: manifest)
        let modelURL = modelURL(for: manifest)
        let checksumURL = checksumURL(for: manifest)
        try? fileManager.removeItem(at: partialURL)

        let delegate = DownloadDelegate(expectedBytes: manifest.byteCount)
        downloader = delegate
        downloadingModelID = manifest.id
        let initial = LocalModelProgress(receivedBytes: 0, totalBytes: manifest.byteCount)
        stateCache[manifest.id] = .downloading(initial)
        progress(initial)

        var promotedModel = false
        do {
            try await delegate.start(
                source: manifest.downloadURL,
                destination: partialURL
            ) { [weak self] value in
                progress(value)
                Task { await self?.recordProgress(value, modelID: manifest.id) }
            }
            try Task.checkCancellation()
            let attributes = try fileManager.attributesOfItem(atPath: partialURL.path)
            let size = (attributes[.size] as? NSNumber)?.int64Value ?? 0
            guard size == manifest.byteCount else {
                throw RewordError.localModelDownloadFailed(
                    "Expected \(manifest.byteCount) bytes, received \(size)."
                )
            }
            let digest = try await Self.sha256(of: partialURL)
            try Task.checkCancellation()
            guard digest == manifest.sha256 else {
                throw RewordError.localModelDownloadFailed("The SHA-256 checksum did not match.")
            }

            try Task.checkCancellation()
            if fileManager.fileExists(atPath: modelURL.path) {
                try fileManager.removeItem(at: modelURL)
            }
            try Task.checkCancellation()
            try fileManager.moveItem(at: partialURL, to: modelURL)
            promotedModel = true
            try Task.checkCancellation()
            try digest.write(to: checksumURL, atomically: true, encoding: .utf8)
            try Task.checkCancellation()
            downloader = nil
            downloadingModelID = nil
            stateCache[manifest.id] = .ready(bytes: size)
        } catch is CancellationError {
            downloader = nil
            downloadingModelID = nil
            try? fileManager.removeItem(at: partialURL)
            if promotedModel {
                try? fileManager.removeItem(at: modelURL)
                try? fileManager.removeItem(at: checksumURL)
            }
            stateCache[manifest.id] = .notDownloaded
            throw CancellationError()
        } catch {
            downloader = nil
            downloadingModelID = nil
            try? fileManager.removeItem(at: partialURL)
            let message = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            stateCache[manifest.id] = .failed(message)
            if let rewordError = error as? RewordError { throw rewordError }
            throw RewordError.localModelDownloadFailed(message)
        }
    }

    public func cancelDownload() {
        downloader?.cancel()
    }

    public func removeModel(modelID: String = LocalModelCatalog.defaultModel.id) throws {
        let manifest = LocalModelCatalog.model(id: modelID)
        if startupModelID == manifest.id {
            startupTask?.cancel()
        }
        // Keep the cancelled single-flight registered until its cleanup has
        // completed. Otherwise that old cleanup could terminate a newer server.
        if cachedConnection?.modelID == manifest.id || startupModelID == manifest.id {
            processHolder.terminate()
            cachedConnection = nil
        }
        if downloadingModelID == manifest.id {
            downloader?.cancel()
            downloader = nil
            downloadingModelID = nil
        }
        for url in [modelURL(for: manifest), partialURL(for: manifest), checksumURL(for: manifest)]
        where fileManager.fileExists(atPath: url.path) {
            try fileManager.removeItem(at: url)
        }
        stateCache[manifest.id] = .notDownloaded
    }

    public func connection(modelID: String = LocalModelCatalog.defaultModel.id) async throws -> LocalServerConnection {
        let manifest = LocalModelCatalog.model(id: modelID)
        while true {
            try Task.checkCancellation()
            guard !isShuttingDown else { throw RewordError.localRuntimeUnavailable }
            _ = try await validateInstalledModel(manifest)
            try Task.checkCancellation()

            // Finish cancellation and cleanup for a different model before a
            // new startup is registered. Otherwise the older task's cleanup
            // can race with and terminate the newly selected server.
            if startupModelID != nil, startupModelID != manifest.id,
               let oldID = startupID, let oldTask = startupTask {
                oldTask.cancel()
                _ = try? await oldTask.value
                clearStartup(id: oldID)
                try Task.checkCancellation()
            }
            if let cachedConnection,
               cachedConnection.modelID == manifest.id,
               processHolder.current()?.isRunning == true {
                return cachedConnection
            }
            if cachedConnection?.modelID != manifest.id {
                processHolder.terminate()
                cachedConnection = nil
            }

            let requestID: UUID
            let task: Task<LocalServerConnection, Error>
            if startupModelID == manifest.id,
               let existingID = startupID, let existingTask = startupTask {
                requestID = existingID
                task = existingTask
            } else {
                requestID = UUID()
                task = Task { [weak self] in
                    guard let self else { throw CancellationError() }
                    return try await self.startLocalServer(manifest)
                }
                startupID = requestID
                startupTask = task
                startupModelID = manifest.id
            }

            do {
                let connection = try await withTaskCancellationHandler {
                    try await task.value
                } onCancel: { [weak self] in
                    Task { await self?.cancelStartup(id: requestID) }
                }
                try Task.checkCancellation()
                let isCurrent = cachedConnection == connection
                    && processHolder.current()?.isRunning == true
                clearStartup(id: requestID)
                if isCurrent { return connection }
            } catch is CancellationError {
                clearStartup(id: requestID)
                // If this caller was cancelled, leave immediately. If another
                // waiter cancelled the shared startup, retry and coalesce on a
                // fresh attempt instead of failing an unrelated rewrite.
                try Task.checkCancellation()
            } catch {
                clearStartup(id: requestID)
                throw error
            }
        }
    }

    public func shutdown() {
        isShuttingDown = true
        startupTask?.cancel()
        processHolder.terminate()
        cachedConnection = nil
    }

    /// App termination cannot await an actor hop; this only signals the child
    /// process and does not touch actor-isolated download/model state.
    public nonisolated func shutdownImmediately() {
        processHolder.terminate()
    }

    private func modelURL(for manifest: OfflineModelManifest) -> URL {
        modelDirectory.appendingPathComponent(manifest.fileName)
    }

    private func partialURL(for manifest: OfflineModelManifest) -> URL {
        modelDirectory.appendingPathComponent(manifest.fileName + ".partial")
    }

    private func checksumURL(for manifest: OfflineModelManifest) -> URL {
        modelDirectory.appendingPathComponent(manifest.fileName + ".sha256")
    }

    private func recordProgress(_ progress: LocalModelProgress, modelID: String) {
        if downloadingModelID == modelID { stateCache[modelID] = .downloading(progress) }
    }

    private func validateInstalledModel(_ manifest: OfflineModelManifest) async throws -> Int64 {
        let modelURL = modelURL(for: manifest)
        let checksumURL = checksumURL(for: manifest)
        let attributes = try fileManager.attributesOfItem(atPath: modelURL.path)
        let size = (attributes[.size] as? NSNumber)?.int64Value ?? 0
        guard size == manifest.byteCount else {
            throw RewordError.localModelNotDownloaded
        }
        let recorded = try? String(contentsOf: checksumURL, encoding: .utf8)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if recorded == manifest.sha256 { return size }
        let digest = try await Self.sha256(of: modelURL)
        guard digest == manifest.sha256 else {
            throw RewordError.localModelNotDownloaded
        }
        try digest.write(to: checksumURL, atomically: true, encoding: .utf8)
        return size
    }

    private func resolvedRuntimeURL() -> URL? {
        var candidates: [URL] = []
        if let runtimeOverride { candidates.append(runtimeOverride) }
        if let resourceURL = Bundle.main.resourceURL {
            candidates.append(resourceURL.appendingPathComponent("LocalAI/llama-server"))
        }
        if let explicit = ProcessInfo.processInfo.environment["REWORDME_LLAMA_SERVER"] {
            candidates.append(URL(fileURLWithPath: explicit))
        }
        candidates += [
            URL(fileURLWithPath: "/opt/homebrew/bin/llama-server"),
            URL(fileURLWithPath: "/usr/local/bin/llama-server")
        ]
        if let path = ProcessInfo.processInfo.environment["PATH"] {
            candidates += path.split(separator: ":").map {
                URL(fileURLWithPath: "\($0)/llama-server")
            }
        }
        return candidates.first { fileManager.isExecutableFile(atPath: $0.path) }
    }

    private func cancelStartup(id: UUID) {
        guard startupID == id else { return }
        startupTask?.cancel()
    }

    private func clearStartup(id: UUID) {
        guard startupID == id else { return }
        startupTask = nil
        startupID = nil
        startupModelID = nil
    }

    private func startLocalServer(_ manifest: OfflineModelManifest) async throws -> LocalServerConnection {
        do {
            try Task.checkCancellation()
            guard let runtime = resolvedRuntimeURL() else {
                throw RewordError.localRuntimeUnavailable
            }

            for _ in 0..<8 {
                try Task.checkCancellation()
                let port = Int.random(in: 49_152...65_535)
                let token = UUID().uuidString.replacingOccurrences(of: "-", with: "")
                let process = Process()
                process.executableURL = runtime
                process.currentDirectoryURL = runtime.deletingLastPathComponent()
                process.arguments = Self.serverArguments(
                    modelPath: modelURL(for: manifest).path,
                    port: port,
                    apiKey: token,
                    alias: manifest.id
                )
                process.standardOutput = FileHandle.nullDevice
                process.standardError = FileHandle.nullDevice
                do {
                    try process.run()
                } catch {
                    try Task.checkCancellation()
                    continue
                }
                do {
                    try Task.checkCancellation()
                    processHolder.set(process)
                    let endpoint = URL(string: "http://127.0.0.1:\(port)/v1")!
                    let connection = LocalServerConnection(
                        endpoint: endpoint, apiKey: token, modelID: manifest.id
                    )
                    if try await waitUntilHealthy(connection: connection, process: process) {
                        try Task.checkCancellation()
                        cachedConnection = connection
                        return connection
                    }
                } catch is CancellationError {
                    if process.isRunning { process.terminate() }
                    processHolder.set(nil)
                    cachedConnection = nil
                    throw CancellationError()
                }
                if process.isRunning { process.terminate() }
            }
            processHolder.set(nil)
            throw RewordError.localRuntimeUnavailable
        } catch is CancellationError {
            processHolder.terminate()
            cachedConnection = nil
            throw CancellationError()
        }
    }

    /// Qwen 3.5 is a hybrid reasoning model. llama.cpp otherwise selects its
    /// thinking template automatically, which can spend the entire context on
    /// hidden `reasoning_content` and return an empty visible response. Local
    /// rewriting needs the short non-reasoning path and a bounded fallback if
    /// the model ever fails to emit its end token.
    static func serverArguments(
        modelPath: String,
        port: Int,
        apiKey: String,
        alias: String = LocalModelCatalog.defaultModel.id
    ) -> [String] {
        [
            "--model", modelPath,
            "--host", "127.0.0.1",
            "--port", String(port),
            "--api-key", apiKey,
            "--alias", alias,
            "--no-webui",
            "--ctx-size", "4096",
            "--parallel", "1",
            "--n-predict", "1024",
            "--jinja",
            "--reasoning", "off"
        ]
    }

    private func waitUntilHealthy(
        connection: LocalServerConnection,
        process: Process
    ) async throws -> Bool {
        let health = connection.endpoint.deletingLastPathComponent().appendingPathComponent("health")
        for _ in 0..<300 {
            try Task.checkCancellation()
            guard process.isRunning else { return false }
            var request = URLRequest(url: health, timeoutInterval: 1)
            request.setValue("Bearer \(connection.apiKey)", forHTTPHeaderField: "Authorization")
            do {
                let (_, response) = try await URLSession.shared.data(for: request)
                if let http = response as? HTTPURLResponse,
                   (200...299).contains(http.statusCode) {
                    return true
                }
            } catch is CancellationError {
                throw CancellationError()
            } catch {
                try Task.checkCancellation()
            }
            try await Task.sleep(for: .milliseconds(200))
        }
        return false
    }

    static func sha256(of url: URL) async throws -> String {
        try Task.checkCancellation()
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        var hasher = SHA256()
        while true {
            try Task.checkCancellation()
            let data = try handle.read(upToCount: 4 * 1_024 * 1_024) ?? Data()
            if data.isEmpty { break }
            hasher.update(data: data)
        }
        try Task.checkCancellation()
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }
}
