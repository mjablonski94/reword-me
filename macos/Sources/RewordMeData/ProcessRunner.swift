import Darwin
import Foundation

struct ProcessOutput: Sendable {
    let status: Int32
    let stdout: String
    let stderr: String

    var combined: String {
        [stdout, stderr].filter { !$0.isEmpty }.joined(separator: "\n")
    }
}

enum ProcessRunnerError: Error, LocalizedError {
    case timedOut
    case couldNotStart(String)

    var errorDescription: String? {
        switch self {
        case .timedOut: return "The provider command timed out."
        case let .couldNotStart(message): return message
        }
    }
}

private final class ProcessBox: @unchecked Sendable {
    let process: Process

    init(_ process: Process) {
        self.process = process
    }

    func terminate() {
        guard process.isRunning else { return }
        process.terminate()
        let identifier = process.processIdentifier
        // A CLI stuck in native code may ignore SIGTERM. Escalate after a
        // short grace period so the advertised timeout remains a real bound.
        DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + 2) { [process] in
            if process.isRunning { _ = Darwin.kill(identifier, SIGKILL) }
        }
    }
}

private actor ProcessWaiter {
    private var didFinish = false
    private var continuation: CheckedContinuation<Void, Never>?

    func wait() async {
        guard !didFinish else { return }
        await withCheckedContinuation { continuation in
            self.continuation = continuation
        }
    }

    func finish() {
        guard !didFinish else { return }
        didFinish = true
        continuation?.resume()
        continuation = nil
    }
}

/// Runs only explicit executable URLs. No shell is involved, so prompts,
/// selected text, file paths and environment values cannot become shell code.
enum ProcessRunner {
    static func run(
        executable: URL,
        arguments: [String],
        input: String? = nil,
        environment: [String: String]? = nil,
        currentDirectory: URL? = nil,
        timeout: Duration = .seconds(300)
    ) async throws -> ProcessOutput {
        let process = Process()
        process.executableURL = executable
        process.arguments = arguments
        process.environment = environment
        process.currentDirectoryURL = currentDirectory

        // Capture to files instead of pipes. Account-backed rewrites can be
        // larger than a pipe buffer, and file capture cannot deadlock while a
        // child waits for its output to be drained.
        let captureDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("rewordme-process-\(UUID().uuidString)", isDirectory: true)
        do {
            try FileManager.default.createDirectory(
                at: captureDirectory, withIntermediateDirectories: true
            )
        } catch {
            throw ProcessRunnerError.couldNotStart(error.localizedDescription)
        }
        defer { try? FileManager.default.removeItem(at: captureDirectory) }
        let outputURL = captureDirectory.appendingPathComponent("stdout")
        let errorURL = captureDirectory.appendingPathComponent("stderr")
        FileManager.default.createFile(atPath: outputURL.path, contents: nil)
        FileManager.default.createFile(atPath: errorURL.path, contents: nil)
        let outputHandle: FileHandle
        let errorHandle: FileHandle
        do {
            outputHandle = try FileHandle(forWritingTo: outputURL)
            errorHandle = try FileHandle(forWritingTo: errorURL)
        } catch {
            throw ProcessRunnerError.couldNotStart(error.localizedDescription)
        }
        process.standardOutput = outputHandle
        process.standardError = errorHandle
        let waiter = ProcessWaiter()
        process.terminationHandler = { _ in
            Task { await waiter.finish() }
        }

        // A temporary input file avoids pipe-buffer stalls before the timeout
        // task has started, and still passes user text directly to the child.
        let inputHandle: FileHandle?
        if let input {
            let inputURL = captureDirectory.appendingPathComponent("stdin")
            do {
                try Data(input.utf8).write(to: inputURL, options: .atomic)
                let handle = try FileHandle(forReadingFrom: inputURL)
                inputHandle = handle
                process.standardInput = handle
            } catch {
                try? outputHandle.close()
                try? errorHandle.close()
                throw ProcessRunnerError.couldNotStart(error.localizedDescription)
            }
        } else {
            inputHandle = nil
        }

        do {
            try process.run()
        } catch {
            try? outputHandle.close()
            try? errorHandle.close()
            try? inputHandle?.close()
            throw ProcessRunnerError.couldNotStart(error.localizedDescription)
        }

        let box = ProcessBox(process)
        return try await withTaskCancellationHandler {
            try await withThrowingTaskGroup(of: ProcessOutput.self) { group in
                group.addTask {
                    await waiter.wait()
                    try? outputHandle.close()
                    try? errorHandle.close()
                    try? inputHandle?.close()
                    let stdoutData = (try? Data(contentsOf: outputURL)) ?? Data()
                    let stderrData = (try? Data(contentsOf: errorURL)) ?? Data()
                    return ProcessOutput(
                        status: process.terminationStatus,
                        stdout: String(decoding: stdoutData, as: UTF8.self)
                            .trimmingCharacters(in: .whitespacesAndNewlines),
                        stderr: String(decoding: stderrData, as: UTF8.self)
                            .trimmingCharacters(in: .whitespacesAndNewlines)
                    )
                }
                group.addTask {
                    try await Task.sleep(for: timeout)
                    box.terminate()
                    throw ProcessRunnerError.timedOut
                }
                guard let first = try await group.next() else {
                    throw ProcessRunnerError.couldNotStart("The provider command did not run.")
                }
                group.cancelAll()
                return first
            }
        } onCancel: {
            box.terminate()
        }
    }
}
