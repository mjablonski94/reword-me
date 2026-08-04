package dev.mjablonski.rewordme.data

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

data class ProcessOutput(val status: Int, val stdout: String, val stderr: String) {
    val combined: String get() = listOf(stdout, stderr).filter(String::isNotEmpty).joinToString("\n")
}

/** Runs an explicit executable directly; selected text never passes through a shell. */
internal object ProcessRunner {
    suspend fun run(
        executable: Path,
        arguments: List<String>,
        input: String? = null,
        removeEnvironment: Set<String> = emptySet(),
        currentDirectory: Path? = null,
        timeoutMillis: Long = 300_000
    ): ProcessOutput = withContext(Dispatchers.IO) {
        // Files cannot fill a pipe buffer, so a verbose agent cannot deadlock
        // while the parent is still writing its prompt. They also let timeout
        // handling wait for only one blocking operation: process.waitFor().
        val captureDirectory = Files.createTempDirectory("rewordme-process-")
        val standardInput = captureDirectory.resolve("stdin")
        val standardOutput = captureDirectory.resolve("stdout")
        val standardError = captureDirectory.resolve("stderr")
        Files.writeString(standardInput, input.orEmpty())

        var process: Process? = null
        try {
            val builder = ProcessBuilder(listOf(executable.toString()) + arguments)
                .redirectInput(standardInput.toFile())
                .redirectOutput(standardOutput.toFile())
                .redirectError(standardError.toFile())
            currentDirectory?.let { builder.directory(it.toFile()) }
            removeEnvironment.forEach(builder.environment()::remove)
            val launched = builder.start()
            process = launched

            val exited = runInterruptible {
                launched.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            }
            if (!exited) {
                terminate(launched)
                throw TimeoutException("The provider command timed out.")
            }
            ProcessOutput(
                status = launched.exitValue(),
                stdout = Files.readString(standardOutput).trim(),
                stderr = Files.readString(standardError).trim()
            )
        } catch (error: Throwable) {
            process?.let(::terminate)
            if (error is CancellationException) throw error
            throw error
        } finally {
            deleteTree(captureDirectory)
        }
    }

    private fun terminate(process: Process) {
        if (!process.isAlive) return
        process.destroy()
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(2, TimeUnit.SECONDS)
        }
    }

    private fun deleteTree(root: Path) {
        runCatching {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
