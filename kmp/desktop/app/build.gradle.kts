import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
            // Release 1.0. The trailing .0 is not optional: jpackage rejects
            // anything but MAJOR.MINOR.BUILD for the Windows Exe and Msi.
            packageVersion = "1.0.0"
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
