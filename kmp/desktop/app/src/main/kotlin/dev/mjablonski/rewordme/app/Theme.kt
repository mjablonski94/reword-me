package dev.mjablonski.rewordme.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One palette and one set of controls for both windows. The popup and the
 * settings screen used to style themselves independently, which is how they
 * drifted into looking like two different apps.
 */
internal object Palette {
    val text = Color(0xFFF2F2F5)
    val secondary = Color(0xFFB9B9C2)

    // The settings window is opaque, so it cannot borrow the popup's acrylic and
    // has to build its own depth. Every grey below is sampled off the macOS
    // screenshots in macos/docs/media, so the two apps read as one product:
    // macOS draws a grouped Form as cards a shade *lighter* than the window,
    // never as outlines on black.
    /** Settings window base. */
    val surface = Color(0xFF292B2E)

    /** The strip carrying the tab picker, standing in for the macOS title bar. */
    val toolbar = Color(0xFF27292D)

    /** Grouped card raised off [surface] - one per settings section. */
    val card = Color(0xFF2F3134)

    /** The hairline macOS draws between two rows inside a card. */
    val hairline = Color(0xFF393B3E)

    /** Fill of a bordered or pop-up button sitting on a [card]. */
    val control = Color(0xFF3F4144)

    /** The near-black well macOS gives a multi-line text editor. */
    val editor = Color(0xFF1E1E1E)

    /** Segmented tab picker: the sunken track and the raised selected segment. */
    val segmentTrack = Color(0xFF1B1D20)
    val segmentSelected = Color(0xFF525457)

    /** Raised fills: pills, buttons, the selected tab. */
    val panel = Color(0x33FFFFFF)

    /** Inset fills: text fields, result and error wells. */
    val well = Color(0x22000000)

    val accent = Color(0xFF8B7CF6)
    val warn = Color(0xFFF0A860)
    val ok = Color(0xFF68CE67)

    /** macOS keeps links system-blue even when the accent colour is not. */
    val link = Color(0xFF4A9CFF)

    val glow = listOf(
        Color(0xFF4C9AFF), Color(0xFF9B6CF6), Color(0xFFE86CA8),
        Color(0xFFF0A860), Color(0xFF4C9AFF)
    )
    val sparkle = listOf(Color(0xFF9B6CF6), Color(0xFF4C9AFF))
}

internal val pillShape = RoundedCornerShape(50)

/** Shared by grouped cards, text fields and the result well. */
internal val insetShape = RoundedCornerShape(10.dp)

/** The tighter radius macOS gives buttons and pop-up buttons. */
internal val controlShape = RoundedCornerShape(6.dp)

/**
 * The one button shape in the app: a panel-filled pill, or accent-filled for
 * the single primary action on a screen.
 */
@Composable
internal fun PillButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val fill = if (primary) Palette.accent else Palette.panel
    val content = if (primary) Color.White else Palette.text
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .clip(pillShape)
            .background(if (enabled) fill else fill.copy(alpha = fill.alpha * 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (primary) 14.dp else 12.dp, vertical = 6.dp)
    ) {
        val tint = if (enabled) content else content.copy(alpha = 0.4f)
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        }
        Text(label, color = tint, fontSize = 13.sp)
    }
}

/** Small circular icon button - the popup header's back and close controls. */
@Composable
internal fun RoundIcon(icon: ImageVector, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(20.dp)
            .background(Palette.panel, CircleShape)
            .clickable(onClick = onClick)
    ) {
        Icon(icon, contentDescription = null, tint = Palette.secondary, modifier = Modifier.size(13.dp))
    }
}

/**
 * Inset text field. Material's outlined field carries its own floating label
 * and 56dp height, neither of which fits next to the popup's flat panels.
 */
@Composable
internal fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    password: Boolean = false,
    minHeight: Dp = 0.dp
) {
    Box(
        modifier
            .background(Palette.well, insetShape)
            .border(1.dp, Palette.panel, insetShape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(placeholder, color = Palette.secondary.copy(alpha = 0.7f), fontSize = 13.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(color = Palette.text, fontSize = 13.sp),
            cursorBrush = SolidColor(Palette.accent),
            visualTransformation =
                if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().heightIn(min = minHeight)
        )
    }
}
