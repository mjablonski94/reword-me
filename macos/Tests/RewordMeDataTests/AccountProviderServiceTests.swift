import Foundation
import XCTest
import RewordMeModels
@testable import RewordMeData

final class AccountProviderServiceTests: XCTestCase {
    func testDownloadGateRemembersCancellationBeforeTaskInstallation() {
        let gate = DownloadTaskGate()
        let task = FakeDownloadTask()

        gate.cancel()
        gate.installAndStart(task)

        XCTAssertEqual(task.resumeCount, 0)
        XCTAssertEqual(task.cancelCount, 1)
    }

    func testCodexRewriteArgumentsDisableEveryToolSurface() {
        let arguments = AccountProviderService.codexArguments(
            model: "automatic",
            outputFile: URL(fileURLWithPath: "/tmp/rewordme-result.txt"),
            workingDirectory: URL(fileURLWithPath: "/tmp/rewordme-empty")
        )

        XCTAssertTrue(arguments.containsConsecutive("--sandbox", "read-only"))
        XCTAssertTrue(arguments.contains("--ignore-user-config"))
        XCTAssertTrue(arguments.contains("--ignore-rules"))
        for feature in [
            "shell_tool", "unified_exec", "multi_agent", "browser_use",
            "computer_use", "apps", "plugins", "image_generation",
            "workspace_dependencies"
        ] {
            XCTAssertTrue(arguments.containsConsecutive("--disable", feature), feature)
        }
        for override in [
            "web_search=\"disabled\"", "apps._default.enabled=false",
            "agents.enabled=false", "tools.view_image=false"
        ] {
            XCTAssertTrue(arguments.containsConsecutive("--config", override), override)
        }
        XCTAssertFalse(arguments.contains("--model"), "automatic must remain a CLI-selected model")
        XCTAssertEqual(arguments.last, "-")
    }

    func testAccountCommandExitOneKeepsTransientFailuresRetryable() {
        for output in [
            "Network connection lost while contacting the service",
            "Rate limit exceeded; retry after 20 seconds"
        ] {
            let error = AccountProviderService.accountCommandFailure(
                provider: .codex,
                exitStatus: 1,
                output: output
            )
            XCTAssertTrue(error.isRetryable, output)
        }
    }

    func testAccountCommandClassifiesSetupAndPermanentPlanFailures() {
        XCTAssertEqual(
            AccountProviderService.accountCommandFailure(
                provider: .codex,
                exitStatus: 1,
                output: "Not logged in. Please run codex login."
            ),
            .accountNotSignedIn(ProviderKind.codex.displayName)
        )
        let plan = AccountProviderService.accountCommandFailure(
            provider: .claudeAccount,
            exitStatus: 1,
            output: "Your plan does not include this feature. Upgrade your plan."
        )
        XCTAssertFalse(plan.isRetryable)
    }

    func testDirectProcessRunnerRoundTripsStandardInput() async throws {
        let output = try await ProcessRunner.run(
            executable: URL(fileURLWithPath: "/bin/cat"),
            arguments: [],
            input: "hello from RewordMe",
            timeout: .seconds(5)
        )
        XCTAssertEqual(output.status, 0)
        XCTAssertEqual(output.stdout, "hello from RewordMe")
    }

    func testProcessRunnerTimeoutTerminatesTheChild() async {
        do {
            _ = try await ProcessRunner.run(
                executable: URL(fileURLWithPath: "/bin/sleep"),
                arguments: ["10"],
                timeout: .milliseconds(100)
            )
            XCTFail("Expected the command to time out")
        } catch ProcessRunnerError.timedOut {
            // Expected.
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    func testLocalModelHashingHonorsStructuredTaskCancellation() async throws {
        let file = FileManager.default.temporaryDirectory
            .appendingPathComponent("rewordme-hash-\(UUID().uuidString)")
        try Data("checksum fixture".utf8).write(to: file)
        defer { try? FileManager.default.removeItem(at: file) }

        let task = Task { try await LocalModelManager.sha256(of: file) }
        task.cancel()
        do {
            _ = try await task.value
            XCTFail("Expected hashing to stop when its parent task is cancelled")
        } catch is CancellationError {
            // Expected.
        }
    }

    func testLocalServerDisablesReasoningAndBoundsGeneration() {
        let arguments = LocalModelManager.serverArguments(
            modelPath: "/tmp/model.gguf",
            port: 55_555,
            apiKey: "secret"
        )

        XCTAssertEqual(arguments.value(after: "--reasoning"), "off")
        XCTAssertEqual(arguments.value(after: "--n-predict"), "1024")
        XCTAssertEqual(arguments.value(after: "--ctx-size"), "4096")
        XCTAssertEqual(arguments.value(after: "--model"), "/tmp/model.gguf")
    }

    func testInstalledOfficialAccountExecutablesReportStatus() async throws {
        let service = AccountProviderService()
        var installed = 0
        for provider in [ProviderKind.codex, .claudeAccount] {
            guard service.executableURL(for: provider) != nil else { continue }
            installed += 1
            let status = await service.status(for: provider)
            XCTAssertEqual(status.provider, provider)
            XCTAssertTrue(status.isInstalled)
            XCTAssertFalse(status.version?.isEmpty ?? true)
        }
        // Keep CI portable while exercising every executable on developer Macs.
        if installed == 0 { throw XCTSkip("No account-provider CLI is installed") }
    }
}

private extension Array where Element == String {
    func value(after flag: String) -> String? {
        guard let index = firstIndex(of: flag), indices.contains(index + 1) else { return nil }
        return self[index + 1]
    }
}

private final class FakeDownloadTask: DownloadTaskControlling {
    private(set) var resumeCount = 0
    private(set) var cancelCount = 0

    func resume() { resumeCount += 1 }
    func cancel() { cancelCount += 1 }
}

private extension Array where Element == String {
    func containsConsecutive(_ first: String, _ second: String) -> Bool {
        zip(self, dropFirst()).contains { $0 == first && $1 == second }
    }
}
