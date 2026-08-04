package dev.mjablonski.rewordme.data

import dev.mjablonski.rewordme.models.LocalModelCatalog
import dev.mjablonski.rewordme.models.OfflineModelManifest
import dev.mjablonski.rewordme.models.RewordError
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Duration
import java.util.Comparator
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LocalModelProgress(val receivedBytes: Long, val totalBytes: Long) {
    val fraction: Float
        get() = if (totalBytes <= 0) 0f else (receivedBytes.toDouble() / totalBytes).coerceIn(0.0, 1.0).toFloat()
}

sealed interface LocalModelState {
    data object NotDownloaded : LocalModelState
    data class Downloading(val progress: LocalModelProgress) : LocalModelState
    data class Ready(val bytes: Long) : LocalModelState
    data class Failed(val detail: String) : LocalModelState
}

data class LocalServerConnection(
    val endpoint: String,
    val apiKey: String,
    val modelId: String = LocalModelCatalog.DEFAULT.id
)

private object LocalRuntimeManifest {
    const val TAG = "b10246"
    const val X64_RESOURCE = "llama-b10246-bin-win-cpu-x64.zip"
    const val ARM64_RESOURCE = "llama-b10246-bin-win-cpu-arm64.zip"
    const val X64_SHA256 = "1a4e9110cdc2092fc59a620c3a0d4c1ab13848df6ee784eef03d4ce41a3918b3"
    const val ARM64_SHA256 = "c0f757763a14777c15a1e2be2ceae13febe09ffc81cf4448c684dcc65e69157a"
}

/** Verified model download plus a private loopback llama.cpp server. */
class LocalModelManager(
    private val directory: Path = defaultLocalDataDirectory().resolve("Models"),
    private val runtimeOverride: Path? = null,
    private val http: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(30))
        .build()
) {
    private val serverMutex = Mutex()
    @Volatile private var downloadInput: InputStream? = null
    @Volatile private var downloadingModelId: String? = null
    @Volatile private var server: Process? = null
    @Volatile private var cachedConnection: LocalServerConnection? = null

    suspend fun state(modelId: String = LocalModelCatalog.DEFAULT.id): LocalModelState = try {
        LocalModelState.Ready(validateInstalledModel(LocalModelCatalog.model(modelId)))
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        LocalModelState.NotDownloaded
    }

    suspend fun download(
        modelId: String = LocalModelCatalog.DEFAULT.id,
        onProgress: (LocalModelProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        val manifest = LocalModelCatalog.model(modelId)
        if (state(manifest.id) is LocalModelState.Ready) return@withContext
        if (downloadingModelId != null) {
            throw RewordError.LocalModelDownloadFailed("Another offline model is already downloading.")
        }
        Files.createDirectories(directory)
        val model = modelPath(manifest)
        val partial = partialPath(manifest)
        val checksum = checksumPath(manifest)
        Files.deleteIfExists(partial)
        val request = HttpRequest.newBuilder(URI(manifest.downloadUrl))
            .timeout(Duration.ofMinutes(10))
            .GET()
            .build()
        downloadingModelId = manifest.id
        var promotedModel = false
        try {
            val response = runInterruptible {
                http.send(request, HttpResponse.BodyHandlers.ofInputStream())
            }
            if (response.statusCode() !in 200..299) {
                throw RewordError.LocalModelDownloadFailed("Server returned HTTP ${response.statusCode()}.")
            }
            val total = response.headers().firstValueAsLong("content-length")
                .orElse(manifest.byteCount)
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            val input = BufferedInputStream(response.body())
            downloadInput = input
            input.use { source ->
                Files.newOutputStream(
                    partial, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
                ).buffered().use { output ->
                    val buffer = ByteArray(1_048_576)
                    onProgress(LocalModelProgress(0, total))
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        received += count
                        onProgress(LocalModelProgress(received, total))
                    }
                }
            }
            downloadInput = null
            if (received != manifest.byteCount) {
                throw RewordError.LocalModelDownloadFailed(
                    "Expected ${manifest.byteCount} bytes, received $received."
                )
            }
            coroutineContext.ensureActive()
            val hash = digest.digest().toHex()
            if (hash != manifest.sha256) {
                throw RewordError.LocalModelDownloadFailed("The SHA-256 checksum did not match.")
            }
            coroutineContext.ensureActive()
            try {
                Files.move(
                    partial, model, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(partial, model, StandardCopyOption.REPLACE_EXISTING)
            }
            promotedModel = true
            coroutineContext.ensureActive()
            Files.writeString(checksum, hash)
            coroutineContext.ensureActive()
            downloadingModelId = null
        } catch (error: Throwable) {
            downloadInput = null
            downloadingModelId = null
            Files.deleteIfExists(partial)
            if (error is CancellationException) {
                if (promotedModel) {
                    Files.deleteIfExists(model)
                    Files.deleteIfExists(checksum)
                }
                throw error
            }
            // Closing the response stream is how Cancel interrupts a blocking
            // read. Preserve the coroutine's cancellation instead of turning
            // that resulting IOException into a failed-download message.
            coroutineContext.ensureActive()
            if (error is RewordError) throw error
            throw RewordError.LocalModelDownloadFailed(error.message ?: error.javaClass.simpleName)
        }
    }

    fun cancelDownload() {
        runCatching { downloadInput?.close() }
        downloadInput = null
        downloadingModelId = null
    }

    suspend fun removeModel(modelId: String = LocalModelCatalog.DEFAULT.id) = withContext(Dispatchers.IO) {
        val manifest = LocalModelCatalog.model(modelId)
        if (cachedConnection?.modelId == manifest.id) shutdown()
        if (downloadingModelId == manifest.id) cancelDownload()
        listOf(modelPath(manifest), partialPath(manifest), checksumPath(manifest))
            .forEach(Files::deleteIfExists)
    }

    suspend fun connection(modelId: String = LocalModelCatalog.DEFAULT.id): LocalServerConnection = serverMutex.withLock {
        val manifest = LocalModelCatalog.model(modelId)
        try {
            coroutineContext.ensureActive()
            validateInstalledModel(manifest)
            coroutineContext.ensureActive()
            cachedConnection?.takeIf { it.modelId == manifest.id && server?.isAlive == true }
                ?.let { return@withLock it }
            if (cachedConnection?.modelId != manifest.id) shutdown()
            val executable = resolveRuntime()
            repeat(8) {
                coroutineContext.ensureActive()
                val port = availablePort()
                val key = UUID.randomUUID().toString().replace("-", "")
                val process = ProcessBuilder(
                    listOf(executable.toString()) + localServerArguments(
                        modelPath(manifest), port, key, manifest.id
                    )
                )
                    .directory(executable.parent.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                server?.destroy()
                server = process
                coroutineContext.ensureActive()
                val connection = LocalServerConnection(
                    "http://127.0.0.1:$port/v1", key, manifest.id
                )
                if (waitUntilHealthy(connection, process)) {
                    coroutineContext.ensureActive()
                    cachedConnection = connection
                    return@withLock connection
                }
                process.destroy()
            }
            server = null
            throw RewordError.LocalRuntimeUnavailable
        } catch (error: CancellationException) {
            server?.destroyForcibly()
            server = null
            cachedConnection = null
            throw error
        }
    }

    fun shutdown() {
        server?.destroy()
        server = null
        cachedConnection = null
    }

    private fun modelPath(manifest: OfflineModelManifest): Path = directory.resolve(manifest.fileName)
    private fun partialPath(manifest: OfflineModelManifest): Path =
        directory.resolve(manifest.fileName + ".partial")
    private fun checksumPath(manifest: OfflineModelManifest): Path =
        directory.resolve(manifest.fileName + ".sha256")

    private suspend fun validateInstalledModel(manifest: OfflineModelManifest): Long = withContext(Dispatchers.IO) {
        val model = modelPath(manifest)
        val checksum = checksumPath(manifest)
        if (!Files.isRegularFile(model) || Files.size(model) != manifest.byteCount) {
            throw RewordError.LocalModelNotDownloaded
        }
        val recorded = runCatching { Files.readString(checksum).trim() }.getOrNull()
        if (recorded != manifest.sha256) {
            val actual = sha256(model)
            coroutineContext.ensureActive()
            if (actual != manifest.sha256) throw RewordError.LocalModelNotDownloaded
            Files.writeString(checksum, actual)
        }
        Files.size(model)
    }

    private fun resolveRuntime(): Path {
        runtimeOverride?.takeIf(Files::isRegularFile)?.let { return it }
        System.getenv("REWORDME_LLAMA_SERVER")?.let(Path::of)
            ?.takeIf(Files::isRegularFile)?.let { return it }
        if (!isWindows) {
            System.getenv("PATH").orEmpty().split(File.pathSeparatorChar)
                .map { Path.of(it, "llama-server") }
                .firstOrNull(Files::isRegularFile)
                ?.let { return it }
        }
        return extractBundledRuntime()
    }

    private fun extractBundledRuntime(): Path {
        val arm = System.getProperty("os.arch").lowercase() in setOf("aarch64", "arm64")
        val resource = if (arm) LocalRuntimeManifest.ARM64_RESOURCE else LocalRuntimeManifest.X64_RESOURCE
        val archiveHash = if (arm) LocalRuntimeManifest.ARM64_SHA256 else LocalRuntimeManifest.X64_SHA256
        val runtimeDirectory = defaultLocalDataDirectory().resolve("Runtime")
            .resolve("${LocalRuntimeManifest.TAG}-${if (arm) "arm64" else "x64"}")
        val executable = runtimeDirectory.resolve("llama-server.exe")
        val marker = runtimeDirectory.resolve(".archive-sha256")
        if (Files.isRegularFile(executable) && runCatching { Files.readString(marker).trim() }.getOrNull() == archiveHash) {
            return executable
        }
        deleteTree(runtimeDirectory)
        Files.createDirectories(runtimeDirectory)
        val stream = LocalModelManager::class.java.getResourceAsStream("/local-ai/$resource")
            ?: throw RewordError.LocalRuntimeUnavailable
        ZipInputStream(BufferedInputStream(stream)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = runtimeDirectory.resolve(entry.name).normalize()
                if (!target.startsWith(runtimeDirectory)) throw RewordError.LocalRuntimeUnavailable
                if (entry.isDirectory) {
                    Files.createDirectories(target)
                } else {
                    Files.createDirectories(target.parent)
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING)
                }
                zip.closeEntry()
            }
        }
        Files.writeString(marker, archiveHash)
        if (!Files.isRegularFile(executable)) throw RewordError.LocalRuntimeUnavailable
        return executable
    }

    private suspend fun waitUntilHealthy(
        connection: LocalServerConnection,
        process: Process
    ): Boolean {
        val health = URI(connection.endpoint.removeSuffix("/v1") + "/health")
        repeat(300) {
            if (!process.isAlive) return false
            val request = HttpRequest.newBuilder(health)
                .timeout(Duration.ofSeconds(1))
                .header("Authorization", "Bearer ${connection.apiKey}")
                .GET()
                .build()
            val ready = withContext(Dispatchers.IO) {
                try {
                    runInterruptible {
                        http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() in 200..299
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    false
                }
            }
            if (ready) return true
            delay(200)
        }
        return false
    }

    private fun availablePort(): Int = ServerSocket(0).use { it.localPort }

    private suspend fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).buffered().use { input ->
            val buffer = ByteArray(1_048_576)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        coroutineContext.ensureActive()
        return digest.digest().toHex()
    }

    private fun deleteTree(root: Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private val isWindows: Boolean get() = System.getProperty("os.name").startsWith("Windows")
}

/**
 * Qwen 3.5 otherwise enters its hybrid thinking mode automatically. It can
 * consume the full context as hidden reasoning and leave the OpenAI-compatible
 * `message.content` empty. Rewording uses the fast non-reasoning path and a
 * bounded fallback for a response that never emits its end token.
 */
internal fun localServerArguments(
    model: Path,
    port: Int,
    apiKey: String,
    alias: String = LocalModelCatalog.DEFAULT.id
): List<String> = listOf(
    "--model", model.toString(),
    "--host", "127.0.0.1",
    "--port", port.toString(),
    "--api-key", apiKey,
    "--alias", alias,
    "--no-webui",
    "--ctx-size", "4096",
    "--parallel", "1",
    "--n-predict", "1024",
    "--jinja",
    "--reasoning", "off"
)

fun defaultLocalDataDirectory(): Path {
    val local = System.getenv("LOCALAPPDATA")
    return if (local != null && System.getProperty("os.name").startsWith("Windows")) {
        Path.of(local, "RewordMe")
    } else {
        Path.of(System.getProperty("user.home"), ".local", "share", "rewordme")
    }
}
