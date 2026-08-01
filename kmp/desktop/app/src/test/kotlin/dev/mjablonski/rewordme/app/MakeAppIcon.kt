package dev.mjablonski.rewordme.app

import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * Writes the committed AppIcon.ico that jpackage stamps onto the exe and the
 * installer. The macOS counterpart is make-icon.swift.
 *
 * `./gradlew :desktop:app:makeAppIcon`
 */
fun main(args: Array<String>) {
    val target = File(args.firstOrNull() ?: "icons/AppIcon.ico")
    target.parentFile?.mkdirs()

    // Windows picks per context: 16 in the tray, 32 on the taskbar, 256 in
    // Explorer's extra-large view.
    val sizes = listOf(16, 24, 32, 48, 64, 128, 256)
    target.writeBytes(ico(sizes))
    println("wrote ${target.absolutePath} (${sizes.joinToString()})")

    // A PNG alongside it, purely so the mark can be eyeballed.
    val preview = File(target.parentFile, "AppIcon-256.png")
    ImageIO.write(AppIcon.render(256), "png", preview)
    println("wrote ${preview.absolutePath}")
}

/**
 * ICO container holding one PNG per size. Vista and later accept PNG payloads,
 * which avoids hand-rolling a BMP encoder with its upside-down rows and
 * separate AND mask.
 */
private fun ico(sizes: List<Int>): ByteArray {
    val images = sizes.map { px ->
        ByteArrayOutputStream().also { ImageIO.write(AppIcon.render(px), "png", it) }.toByteArray()
    }

    val out = ByteArrayOutputStream()
    fun u8(value: Int) = out.write(value and 0xFF)
    fun u16(value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }
    fun u32(value: Int) {
        u16(value and 0xFFFF)
        u16((value ushr 16) and 0xFFFF)
    }

    // ICONDIR
    u16(0)
    u16(1)
    u16(sizes.size)

    // ICONDIRENTRY per image, then the payloads back to back.
    var offset = 6 + 16 * sizes.size
    sizes.forEachIndexed { index, px ->
        // 256 is stored as 0; the field is a single byte.
        u8(if (px >= 256) 0 else px)
        u8(if (px >= 256) 0 else px)
        u8(0)
        u8(0)
        u16(1)
        u16(32)
        u32(images[index].size)
        u32(offset)
        offset += images[index].size
    }
    images.forEach(out::write)
    return out.toByteArray()
}
