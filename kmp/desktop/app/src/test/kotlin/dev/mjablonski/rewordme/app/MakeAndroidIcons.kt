package dev.mjablonski.rewordme.app

import java.io.File
import javax.imageio.ImageIO

/**
 * Writes the Android launcher mipmaps from the same drawn mark the Windows
 * .ico and the tray glyph come from, so no platform's icon can drift.
 * Run via the android:app `makeAndroidIcons` docs in kmp/README.md:
 *   ./gradlew :desktop:app:makeAndroidIcons
 */
fun main(arguments: Array<String>) {
    val resDirectory = File(
        arguments.firstOrNull()
            ?: error("Usage: MakeAndroidIconsKt <android res directory>")
    )

    // Legacy square icons for launchers that ignore adaptive layers.
    val densities = mapOf(
        "mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192
    )
    densities.forEach { (qualifier, px) ->
        val directory = File(resDirectory, "mipmap-$qualifier").apply { mkdirs() }
        ImageIO.write(AppIcon.render(px), "png", File(directory, "ic_launcher.png"))
        // Adaptive foreground: the mark inside the 66/108 safe zone.
        val foregroundPx = px * 108 / 48
        ImageIO.write(
            AppIcon.renderMark(foregroundPx, contentScale = 66f / 108f),
            "png",
            File(directory, "ic_launcher_foreground.png")
        )
    }
    println("Wrote launcher icons under ${resDirectory.absolutePath}")
}
