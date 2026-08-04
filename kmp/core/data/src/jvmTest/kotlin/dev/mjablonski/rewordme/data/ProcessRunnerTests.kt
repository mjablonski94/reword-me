package dev.mjablonski.rewordme.data

import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordError
import java.nio.file.Path
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ProcessRunnerTests {
    @Test
    fun localServerDisablesReasoningAndBoundsGeneration() {
        val arguments = localServerArguments(Path.of("model.gguf"), 55_555, "secret")

        assertEquals("off", arguments.valueAfter("--reasoning"))
        assertEquals("1024", arguments.valueAfter("--n-predict"))
        assertEquals("4096", arguments.valueAfter("--ctx-size"))
        assertEquals("model.gguf", arguments.valueAfter("--model"))
    }

    @Test
    fun codexRewriteArgumentsDisableEveryToolSurface() {
        val arguments = AccountProviderService.codexArguments(
            "automatic", Path.of("result.txt"), Path.of("empty-workspace")
        )

        assertTrue(arguments.containsPair("--sandbox", "read-only"))
        assertTrue("--ignore-user-config" in arguments)
        assertTrue("--ignore-rules" in arguments)
        listOf(
            "shell_tool", "unified_exec", "multi_agent", "browser_use",
            "computer_use", "apps", "plugins", "image_generation", "workspace_dependencies"
        ).forEach { feature ->
            assertTrue(arguments.containsPair("--disable", feature), feature)
        }
        listOf(
            "web_search=\"disabled\"", "apps._default.enabled=false",
            "agents.enabled=false", "tools.view_image=false"
        ).forEach { override ->
            assertTrue(arguments.containsPair("--config", override), override)
        }
        assertFalse("--model" in arguments)
        assertEquals("-", arguments.last())
    }

    @Test
    fun registeredStoreCandidateMustStayInsideItsInstallLocation() {
        val root = Path.of("C:/Program Files/WindowsApps/OpenAI.ChatGPT")
        assertTrue(AccountProviderService.isCandidateWithinInstallLocation(
            root, root.resolve("resources/codex.exe")
        ))
        assertFalse(AccountProviderService.isCandidateWithinInstallLocation(
            root, root.resolve("../Attacker/codex.exe")
        ))
        assertFalse(AccountProviderService.isCandidateWithinInstallLocation(
            root, root.resolve("resources/not-codex.exe")
        ))
    }

    @Test
    fun accountCommandExitOneKeepsTransientFailuresRetryable() {
        listOf(
            "Network connection lost while contacting the service",
            "Rate limit exceeded; retry after 20 seconds"
        ).forEach { output ->
            val error = AccountProviderService.accountCommandFailure(
                ProviderKind.CODEX, 1, output
            )
            assertTrue(error.isRetryable, output)
        }
    }

    @Test
    fun accountCommandClassifiesSetupAndPermanentPlanFailures() {
        assertEquals(
            RewordError.AccountNotSignedIn(ProviderKind.CODEX.displayName),
            AccountProviderService.accountCommandFailure(
                ProviderKind.CODEX, 1, "Not logged in. Please run codex login."
            )
        )
        val plan = AccountProviderService.accountCommandFailure(
            ProviderKind.CLAUDE_ACCOUNT,
            1,
            "Your plan does not include this feature. Upgrade your plan."
        )
        assertFalse(plan.isRetryable)
    }

    @Test
    fun directProcessRoundTripsStandardInput() = runBlocking {
        val marker = "RewordMe direct stdin marker"
        val output = ProcessRunner.run(
            executable = if (isWindows) {
                Path.of(System.getenv("SystemRoot"), "System32", "more.com")
            } else {
                Path.of("/bin/cat")
            },
            arguments = emptyList(),
            input = marker,
            timeoutMillis = 5_000
        )

        assertEquals(0, output.status)
        assertTrue(marker in output.stdout)
    }

    @Test
    fun timeoutTerminatesTheChild() = runBlocking {
        val executable: Path
        val arguments: List<String>
        if (isWindows) {
            executable = Path.of(System.getenv("SystemRoot"), "System32", "ping.exe")
            arguments = listOf("-n", "20", "127.0.0.1")
        } else {
            executable = Path.of("/bin/sleep")
            arguments = listOf("10")
        }

        assertFailsWith<TimeoutException> {
            ProcessRunner.run(executable, arguments, timeoutMillis = 100)
        }
        Unit // Keep the JUnit 4 test method's generated return type void.
    }

    private val isWindows: Boolean
        get() = System.getProperty("os.name").startsWith("Windows")

    private fun List<String>.containsPair(first: String, second: String): Boolean =
        zipWithNext().any { (left, right) -> left == first && right == second }
}

private fun List<String>.valueAfter(flag: String): String? =
    indexOf(flag).takeIf { it >= 0 && it + 1 < size }?.let { this[it + 1] }
