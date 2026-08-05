import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val appProperties = Properties().apply {
    project.file("src/main/resources/app.properties").inputStream().use(::load)
}
val appVersion = requireNotNull(appProperties.getProperty("version")) {
    "app.properties must define version"
}

val localAiResourceDir = layout.buildDirectory.dir("generated/localAiResources")
val llamaRuntimeArchives = mapOf(
    "llama-b10246-bin-win-cpu-x64.zip" to Pair(
        "https://github.com/ggml-org/llama.cpp/releases/download/b10246/llama-b10246-bin-win-cpu-x64.zip",
        "1a4e9110cdc2092fc59a620c3a0d4c1ab13848df6ee784eef03d4ce41a3918b3"
    ),
    "llama-b10246-bin-win-cpu-arm64.zip" to Pair(
        "https://github.com/ggml-org/llama.cpp/releases/download/b10246/llama-b10246-bin-win-cpu-arm64.zip",
        "c0f757763a14777c15a1e2be2ceae13febe09ffc81cf4448c684dcc65e69157a"
    )
)

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(1_048_576)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val prepareLocalAiRuntime by tasks.registering {
    group = "build setup"
    description = "Downloads and verifies pinned llama.cpp Windows runtimes."
    val archiveOutputs = llamaRuntimeArchives.keys.map {
        localAiResourceDir.map { directory -> directory.file("local-ai/$it") }
    }
    outputs.files(archiveOutputs)
    outputs.file(localAiResourceDir.map { it.file("THIRD_PARTY_NOTICES.txt") })
    doLast {
        val destination = localAiResourceDir.get().dir("local-ai").asFile
        destination.mkdirs()
        llamaRuntimeArchives.forEach { (name, sourceAndHash) ->
            val target = destination.resolve(name)
            val expectedHash = sourceAndHash.second
            if (!target.isFile || sha256(target) != expectedHash) {
                val partial = destination.resolve("$name.partial")
                partial.delete()
                URI(sourceAndHash.first).toURL().openConnection().apply {
                    connectTimeout = 30_000
                    readTimeout = 120_000
                }.getInputStream().buffered().use { input ->
                    partial.outputStream().buffered().use(input::copyTo)
                }
                val actualHash = sha256(partial)
                check(actualHash == expectedHash) {
                    "SHA-256 mismatch for $name: expected $expectedHash, received $actualHash"
                }
                try {
                    Files.move(
                        partial.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING
                    )
                }
            }
        }
        rootProject.projectDir.parentFile.resolve("THIRD_PARTY_NOTICES.txt")
            .copyTo(localAiResourceDir.get().file("THIRD_PARTY_NOTICES.txt").asFile, overwrite = true)
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core:models"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":desktop:platform"))
    implementation(compose.desktop.currentOs)
    // SF Symbols have no Windows equivalent, so the icon set has to ship with
    // the app. The extended set carries the closest analogues to the glyphs
    // the macOS popup uses.
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    testImplementation(libs.kotlin.test)
}

sourceSets["main"].resources.srcDir(localAiResourceDir)
tasks.named("processResources") { dependsOn(prepareLocalAiRuntime) }

/** Offscreen render of every screen to PNGs, for reviewing layout changes. */
val renderUi by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Renders the popup and settings screens to build/ui-snapshots."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "dev.mjablonski.rewordme.app.UiSnapshotsKt"
    args(layout.buildDirectory.dir("ui-snapshots").get().asFile.absolutePath)
}

/** Regenerates the committed AppIcon.ico from AppIcon.kt. */
val makeAppIcon by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Writes icons/AppIcon.ico from the drawn app mark."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "dev.mjablonski.rewordme.app.MakeAppIconKt"
    args(project.file("icons/AppIcon.ico").absolutePath)
}

/** Documentation screenshots of the real settings window, for the README. */
val settingsShots by tasks.registering(JavaExec::class) {
    group = "documentation"
    description = "Captures the settings tabs from the real window into docs/media."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "dev.mjablonski.rewordme.app.SettingsShotsKt"
    args(rootProject.file("docs/media").absolutePath)
}

/** Cycles the real popup window through every stage to check frame sizing. */
val probePopupWindow by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Opens the popup window and prints the frame size per stage."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "dev.mjablonski.rewordme.app.PopupWindowProbeKt"
    args(layout.buildDirectory.dir("popup-frames").get().asFile.absolutePath)
}

compose.desktop {
    application {
        mainClass = "dev.mjablonski.rewordme.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "RewordMe"
            modules("java.net.http")
            // Release 1.0.1. All three components are required: jpackage accepts
            // only MAJOR.MINOR.BUILD for the Windows Exe and Msi.
            packageVersion = appVersion
            vendor = "Michal Jablonski"

            windows {
                iconFile.set(project.file("icons/AppIcon.ico"))
                // Stable identity so upgrades replace the install instead of
                // stacking a second copy in Programs and Features.
                upgradeUuid = "8B7CF6A1-4C9A-4E2D-9B6C-F6E86CA81234"
                menuGroup = "RewordMe"
                dirChooser = true
            }
        }
    }
}
