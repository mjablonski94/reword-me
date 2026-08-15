package dev.mjablonski.rewordme.android.features.popup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HighlightAlt
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mjablonski.rewordme.android.R
import dev.mjablonski.rewordme.android.common.Palette
import dev.mjablonski.rewordme.android.common.PillButton
import dev.mjablonski.rewordme.android.common.RoundIcon
import dev.mjablonski.rewordme.android.common.localized
import dev.mjablonski.rewordme.android.features.popup.presentation.PopupAction
import dev.mjablonski.rewordme.android.features.popup.presentation.PopupIntent
import dev.mjablonski.rewordme.android.features.popup.presentation.PopupStage
import dev.mjablonski.rewordme.android.features.popup.presentation.PopupState

private val cardShape = RoundedCornerShape(18.dp)

/** UI face of a [PopupAction]: the icon and the localized title. */
private val PopupAction.icon: ImageVector
    get() = when (this) {
        PopupAction.PROOFREAD -> Icons.AutoMirrored.Rounded.ManageSearch
        PopupAction.REWRITE -> Icons.Rounded.Autorenew
        PopupAction.FRIENDLY -> Icons.Rounded.Mood
        PopupAction.PROFESSIONAL -> Icons.Rounded.BusinessCenter
        PopupAction.CONCISE -> Icons.AutoMirrored.Rounded.ShortText
    }

private val PopupAction.titleRes: Int
    get() = when (this) {
        PopupAction.PROOFREAD -> R.string.popup_proofread
        PopupAction.REWRITE -> R.string.popup_rewrite
        PopupAction.FRIENDLY -> R.string.popup_friendly
        PopupAction.PROFESSIONAL -> R.string.popup_professional
        PopupAction.CONCISE -> R.string.popup_concise
    }

/**
 * The desktop popup as a centered card over a scrim. Tapping the scrim
 * dismisses, exactly like the desktop popup losing focus.
 */
@Composable
fun PopupScreen(state: PopupState, onIntent: (PopupIntent) -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onIntent(PopupIntent.Dismiss) }
            .systemBarsPadding()
            .imePadding()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .padding(24.dp)
                .clip(cardShape)
                .background(Palette.surface)
                .border(1.dp, Palette.hairline, cardShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* swallow taps so they don't reach the scrim */ }
                .padding(16.dp)
        ) {
            Header(state, onIntent)
            when (state.stage) {
                PopupStage.EMPTY -> EmptyView()
                PopupStage.MENU -> Menu(state, onIntent)
                PopupStage.LOADING -> Loading(onIntent)
                PopupStage.RESULT -> ResultView(state, onIntent)
                PopupStage.FAILED -> ErrorView(state, onIntent)
            }
        }
    }
}

@Composable
private fun Header(state: PopupState, onIntent: (PopupIntent) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp)
    ) {
        if (state.stage == PopupStage.RESULT || state.stage == PopupStage.FAILED) {
            RoundIcon(Icons.Rounded.ChevronLeft) { onIntent(PopupIntent.BackToMenu) }
        }
        Text(
            stringResource(R.string.app_name),
            color = Palette.secondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        if (state.modelLabel.isNotEmpty()) {
            Text(
                state.modelLabel,
                color = Palette.secondary.copy(alpha = 0.7f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 170.dp)
            )
        }
        RoundIcon(Icons.Rounded.Close) { onIntent(PopupIntent.Dismiss) }
    }
}

@Composable
private fun Menu(state: PopupState, onIntent: (PopupIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DescribeField(state, onIntent)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton(PopupAction.PROOFREAD, Modifier.weight(1f)) {
                onIntent(PopupIntent.Run(PopupAction.PROOFREAD))
            }
            BigButton(PopupAction.REWRITE, Modifier.weight(1f)) {
                onIntent(PopupIntent.Run(PopupAction.REWRITE))
            }
        }
        HorizontalDivider(color = Palette.panel)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(PopupAction.FRIENDLY, PopupAction.PROFESSIONAL, PopupAction.CONCISE)
                .forEach { preset ->
                    PresetRow(preset) { onIntent(PopupIntent.Run(preset)) }
                }
        }
    }
}

@Composable
private fun PresetRow(preset: PopupAction, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
            Icon(
                preset.icon,
                contentDescription = null,
                tint = Palette.secondary,
                modifier = Modifier.size(17.dp)
            )
        }
        Text(stringResource(preset.titleRes), color = Palette.text, fontSize = 15.sp)
    }
}

@Composable
private fun DescribeField(state: PopupState, onIntent: (PopupIntent) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.2.dp, Brush.sweepGradient(Palette.glow), RoundedCornerShape(50))
            .background(Palette.well, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(15.dp)
                // Offscreen compositing so SrcIn has the glyph alone to clip
                // against; without it the gradient would flood the whole row.
                .graphicsLayer(alpha = 0.99f)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(Palette.sparkle),
                        blendMode = BlendMode.SrcIn
                    )
                }
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = state.steering,
            onValueChange = { onIntent(PopupIntent.SteeringChanged(it)) },
            singleLine = true,
            textStyle = TextStyle(color = Palette.text, fontSize = 14.sp),
            cursorBrush = SolidColor(Palette.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = { onIntent(PopupIntent.SubmitSteering) }
            ),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (state.steering.isEmpty()) {
                    Text(
                        stringResource(R.string.popup_describePlaceholder),
                        color = Palette.secondary,
                        fontSize = 14.sp
                    )
                }
                inner()
            }
        )
    }
}

@Composable
private fun BigButton(action: PopupAction, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.panel)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Icon(
            action.icon,
            contentDescription = null,
            tint = Palette.text,
            modifier = Modifier.size(20.dp)
        )
        Text(stringResource(action.titleRes), color = Palette.text, fontSize = 13.sp)
    }
}

@Composable
private fun Loading(onIntent: (PopupIntent) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
    ) {
        CircularProgressIndicator(
            Modifier.size(22.dp),
            color = Palette.accent,
            strokeWidth = 2.dp
        )
        Text(
            stringResource(R.string.popup_rewording),
            color = Palette.secondary,
            fontSize = 14.sp
        )
        PillButton(stringResource(R.string.popup_cancel)) {
            onIntent(PopupIntent.BackToMenu)
        }
    }
}

@Composable
private fun ResultView(state: PopupState, onIntent: (PopupIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            state.result,
            color = Palette.text,
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp, max = 240.dp)
                .background(Palette.well, RoundedCornerShape(12.dp))
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        )
        DescribeField(state, onIntent)
        if (state.actionFailedCopy) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = Palette.warn,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    stringResource(R.string.error_copyFailed),
                    color = Palette.warn,
                    fontSize = 12.sp
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            PillButton(stringResource(R.string.popup_again), icon = Icons.Rounded.Refresh) {
                onIntent(PopupIntent.Again)
            }
            Spacer(Modifier.weight(1f))
            PillButton(stringResource(R.string.popup_copy)) { onIntent(PopupIntent.Copy) }
            if (state.canReplace) {
                PillButton(stringResource(R.string.popup_replace), primary = true) {
                    onIntent(PopupIntent.Replace)
                }
            }
        }
    }
}

@Composable
private fun ErrorView(state: PopupState, onIntent: (PopupIntent) -> Unit) {
    val context = LocalContext.current
    val error = state.error
    val fallback = stringResource(R.string.popup_errorTitle)
    val message = error?.localized(context, fallback) ?: fallback
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.well, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = Palette.warn,
                modifier = Modifier.size(16.dp)
            )
            Text(
                stringResource(R.string.popup_errorTitle),
                color = Palette.warn,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(message, color = Palette.secondary, fontSize = 13.sp)
        if (error?.isRetryable != false) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                PillButton(stringResource(R.string.popup_tryAgain)) {
                    onIntent(PopupIntent.Retry)
                }
            }
        }
    }
}

@Composable
private fun EmptyView() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp)
    ) {
        Icon(
            Icons.Rounded.HighlightAlt,
            contentDescription = null,
            tint = Palette.secondary,
            modifier = Modifier.size(28.dp)
        )
        Text(
            stringResource(R.string.home_tryHint),
            color = Palette.secondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}
