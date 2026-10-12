package dev.mjablonski.rewordme.android.features.home.ui

import android.graphics.Color as AndroidColor
import android.util.TypedValue
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.mjablonski.rewordme.android.R
import dev.mjablonski.rewordme.android.common.Palette
import dev.mjablonski.rewordme.android.common.PillButton
import dev.mjablonski.rewordme.android.common.insetShape
import dev.mjablonski.rewordme.android.features.home.presentation.HomeIntent
import dev.mjablonski.rewordme.android.features.home.presentation.HomeState

/**
 * The live tutorial: a description of the selection-menu flow and a prefilled
 * editor whose text the user can select to run the real thing end to end -
 * including the OS writing the rewrite back into the field.
 */
@Composable
fun HomeScreen(state: HomeState, onIntent: (HomeIntent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        // Bar and IME padding come BEFORE the scroll modifier so they shrink
        // the viewport; inside it they'd just add scrollable dead space while
        // the keyboard or navigation bar covers the content.
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = Palette.accent,
                modifier = Modifier.size(22.dp)
            )
            Text(
                stringResource(R.string.home_title),
                color = Palette.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
        }

        Text(
            stringResource(R.string.home_description),
            color = Palette.secondary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        if (state.isConfigured == false) {
            Text(
                stringResource(R.string.home_setupHint),
                color = Palette.warn,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Palette.well, insetShape)
                    .border(1.dp, Palette.warn.copy(alpha = 0.4f), insetShape)
                    .padding(12.dp)
            )
        }

        PillButton(
            stringResource(R.string.home_openSettings),
            icon = Icons.Rounded.Settings,
            primary = state.isConfigured == false
        ) { onIntent(HomeIntent.OpenSettings) }

        Text(
            stringResource(R.string.home_tryTitle),
            color = Palette.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            stringResource(R.string.home_tryHint),
            color = Palette.secondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        // Most selection toolbars park third-party actions in the overflow;
        // the tutorial has to say so or the entry looks missing.
        Text(
            stringResource(R.string.android_menuHint),
            color = Palette.secondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        // Since Android 11 many apps cannot list RewordMe in that menu at
        // all; Share is the way in there.
        Text(
            stringResource(R.string.android_shareHint),
            color = Palette.secondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        TryItEditor()
    }
}

@Composable
private fun TryItEditor() {
    val sample = stringResource(R.string.home_sample)
    val textColor = Palette.text.toArgb()
    val highlight = Palette.accent.copy(alpha = 0.4f).toArgb()
    // A framework EditText rather than a Compose field, deliberately: the
    // classic TextView is what surfaces ACTION_PROCESS_TEXT entries in its
    // selection toolbar and writes the returned rewrite back over the
    // selection - so the tutorial exercises the exact production path other
    // apps' fields will use.
    AndroidView(
        factory = { context ->
            EditText(context).apply {
                // No ID means no process-text menu: TextView.canProcessText()
                // requires one to route the replacement result back.
                id = R.id.home_try_it_editor
                setText(sample)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setLineSpacing(0f, 1.25f)
                setBackgroundColor(AndroidColor.TRANSPARENT)
                // View padding is raw pixels; convert or low-density devices
                // get triple the inset and high-density ones almost none.
                val horizontal = (12 * resources.displayMetrics.density).toInt()
                val vertical = (10 * resources.displayMetrics.density).toInt()
                setPadding(horizontal, vertical, horizontal, vertical)
                highlightColor = highlight
                // Editable so the rewrite can land back in it, but the sample
                // is for selecting, not typing: a keyboard popping up on every
                // long-press covered the very text on small phones.
                showSoftInputOnFocus = false
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.editor, insetShape)
            .border(1.dp, Palette.hairline, insetShape)
    )
}
