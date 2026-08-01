package dev.mjablonski.rewordme.app

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Arc2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import kotlin.math.cos
import kotlin.math.sin

/**
 * A big white R inside a circular-arrows ring on a purple-indigo gradient
 * tile - the same mark the macOS build renders in make-icon.swift.
 *
 * Drawn rather than shipped as a bitmap so the tray glyph, the window icon
 * and the packaged .ico cannot drift apart.
 */
object AppIcon {

    fun render(px: Int): BufferedImage {
        val image = BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        )

        val dim = px.toFloat()
        // Edge to edge: Windows draws the icon on unpredictable backdrops, so
        // any transparency in the tile reads as a hole.
        g.paint = GradientPaint(
            0f, 0f, Color(148, 84, 237),
            dim, dim, Color(71, 77, 219)
        )
        g.fillRect(0, 0, px, px)

        drawRing(g, dim)
        drawLetter(g, dim)

        g.dispose()
        return image
    }

    /** Two clockwise arcs, each with an arrowhead at its trailing end. */
    private fun drawRing(g: java.awt.Graphics2D, dim: Float) {
        val center = dim / 2.0
        val radius = dim * 0.315
        val white = Color(255, 255, 255, 245)

        val saved: AffineTransform = g.transform
        // Flip to a y-up frame so the angles match the macOS source directly.
        g.translate(0.0, dim.toDouble())
        g.scale(1.0, -1.0)

        g.color = white
        g.stroke = BasicStroke(dim * 0.042f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

        for ((startDeg, endDeg) in listOf(150.0 to 30.0, 330.0 to 210.0)) {
            g.draw(
                Arc2D.Double(
                    center - radius, center - radius,
                    radius * 2, radius * 2,
                    startDeg, endDeg - startDeg, Arc2D.OPEN
                )
            )

            val angle = Math.toRadians(endDeg)
            val px0 = center + radius * cos(angle)
            val py0 = center + radius * sin(angle)
            // Clockwise tangent and the outward radial, as in the macOS source.
            val tangentX = sin(angle)
            val tangentY = -cos(angle)
            val normalX = cos(angle)
            val normalY = sin(angle)
            val length = dim * 0.085
            val width = dim * 0.055

            val head = Path2D.Double()
            head.moveTo(px0 + tangentX * length, py0 + tangentY * length)
            head.lineTo(px0 + normalX * width, py0 + normalY * width)
            head.lineTo(px0 - normalX * width, py0 - normalY * width)
            head.closePath()
            g.fill(head)
        }

        g.transform = saved
    }

    private fun drawLetter(g: java.awt.Graphics2D, dim: Float) {
        val font = letterFont(dim * 0.42f)
        val glyphs = font.createGlyphVector(g.fontRenderContext, "R")
        val bounds = glyphs.visualBounds
        // Centre on the ink, not the line box, or the R sits low in the ring.
        val outline = glyphs.getOutline(
            ((dim - bounds.width) / 2 - bounds.x).toFloat(),
            ((dim - bounds.height) / 2 - bounds.y).toFloat()
        )
        g.color = Color(255, 255, 255, 250)
        g.fill(outline)
    }

    private val availableFamilies: Set<String> by lazy {
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
    }

    private fun letterFont(size: Float): Font {
        val family = listOf("Segoe UI Black", "Segoe UI Semibold", "Segoe UI")
            .firstOrNull { it in availableFamilies }
            ?: Font.SANS_SERIF
        return Font(family, Font.BOLD, size.toInt()).deriveFont(size)
    }
}
