package dev.mjablonski.rewordme.android.features.popup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
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

/** Lets tests find the card to measure its morph; no visual effect. */
internal const val POPUP_CARD_TAG = "popupCard"

private const val ENTER_MILLIS = 200
private const val EXIT_MILLIS = 160
private const val MORPH_MILLIS = 260

/**
 * The desktop popup as a centered card over a scrim. Tapping the scrim
 * dismisses like the desktop popup losing focus - except while a request is
 * running or its result is shown, where a stray tap would throw work away.
 *
 * [visibility] drives the fade in and out; the activity flips it to false
 * and finishes once the exit has played. The default shows the card at once.
 */
@Composable
fun PopupScreen(
    state: PopupState,
    visibility: MutableTransitionState<Boolean> = remember { MutableTransitionState(true) },
    onIntent: (PopupIntent) -> Unit
) {
    BackHandler { onIntent(PopupIntent.BackPressed) }
    AnimatedVisibility(
        visibleState = visibility,
        enter = fadeIn(tween(ENTER_MILLIS)),
        exit = fadeOut(tween(EXIT_MILLIS))
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onIntent(PopupIntent.ScrimTapped) }
                // union, not systemBars + ime stacked: the IME inset already
                // reaches up from the bottom of the window, so adding the nav
                // bar on top of it would lift the card twice. Pre-30 devices
                // report the IME inset only to adjustResize windows, hence the
                // manifest setting; the card then shrinks and scrolls.
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    // The card itself pops in slightly scaled on top of the fade.
                    .animateEnterExit(
                        enter = scaleIn(tween(ENTER_MILLIS), initialScale = 0.94f),
                        exit = scaleOut(tween(EXIT_MILLIS), targetScale = 0.96f)
                    )
                    .testTag(POPUP_CARD_TAG)
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    // 12dp sides, not 24: a 320dp-wide phone needs every pixel
                    // for the action row.
                    .padding(horizontal = 12.dp, vertical = 24.dp)
                    .clip(cardShape)
                    .background(Palette.surface)
                    .border(1.dp, Palette.hairline, cardShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* swallow taps so they don't reach the scrim */ }
                    // Large fonts, small screens and the keyboard all make the
                    // content taller than the window; it must scroll, not clip.
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Header(state, onIntent)
                StageContent(state, onIntent)
            }
        }
    }
}

/**
 * One stage cross-fades into the next while the card morphs to the new
 * size: the old content leaves quickly, the new one fades in once the
 * resize is under way, and the card's background follows the animated size.
 */
@Composable
private fun StageContent(state: PopupState, onIntent: (PopupIntent) -> Unit) {
    AnimatedContent(
        targetState = state.stage,
        transitionSpec = {
            (fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90)))
                .using(SizeTransform { _, _ -> tween(MORPH_MILLIS, easing = FastOutSlowInEasing) })
        },
        label = "popupStage"
    ) { stage ->
        when (stage) {
            PopupStage.EMPTY -> EmptyView(state.tooShort)
            PopupStage.SETUP -> SetupView(onIntent)
            PopupStage.MENU -> Menu(state, onIntent)
            PopupStage.LOADING -> Loading(onIntent)
            PopupStage.RESULT -> ResultView(state, onIntent)
            PopupStage.FAILED -> ErrorView(state, onIntent)
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
        AnimatedVisibility(
            visible = state.stage == PopupStage.RESULT || state.stage == PopupStage.FAILED,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            RoundIcon(
                Icons.Rounded.ChevronLeft,
                contentDescription = stringResource(R.string.android_back)
            ) { onIntent(PopupIntent.BackToMenu) }
        }
        Text(
            stringResource(R.string.app_name),
            color = Palette.secondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        // The label takes whatever width is left and ellipsizes inside it; a
        // capped width instead pushed the close button off narrow cards.
        val automatic = stringResource(R.string.provider_automatic)
        Text(
            if (state.providerName.isEmpty()) "" else
                "${state.modelName ?: automatic} · ${state.providerName}",
            color = Palette.secondary.copy(alpha = 0.7f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        RoundIcon(
            Icons.Rounded.Close,
            contentDescription = stringResource(R.string.android_close)
        ) { onIntent(PopupIntent.Dismiss) }
    }
}

@Composable
private fun Menu(state: PopupState, onIntent: (PopupIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SelectionPreview(state.original)
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

/**
 * A long-press selects a single word; without seeing the selection people
 * rewrite one word and wonder why the result is so short.
 */
@Composable
private fun SelectionPreview(text: String) {
    Text(
        text.trim(),
        color = Palette.secondary,
        fontSize = 13.sp,
        fontStyle = FontStyle.Italic,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.well, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
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
        var slow by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(SLOW_HINT_MILLIS)
            slow = true
        }
        AnimatedVisibility(visible = slow, enter = fadeIn() + expandVertically()) {
            Text(
                stringResource(R.string.android_slowHint),
                color = Palette.secondary.copy(alpha = 0.8f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
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
        AnimatedVisibility(
            visible = state.actionFailedCopy,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
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
        if (!state.canReplace) {
            Text(
                stringResource(R.string.android_replaceUnavailable),
                color = Palette.secondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        // The primary action gets a full-width row of its own: squeezed in
        // beside Again and Copy it wrapped to "Repl/ace" on 4-inch phones
        // and lost its label entirely in longer languages.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Again keeps its natural width ("Jeszcze raz" plus the icon does
            // not fit half a 320dp card); Copy takes what is left.
            PillButton(
                stringResource(R.string.popup_again),
                icon = Icons.Rounded.Refresh,
                modifier = if (state.canReplace) Modifier else Modifier.weight(1f)
            ) { onIntent(PopupIntent.Again) }
            if (state.canReplace) {
                PillButton(
                    stringResource(R.string.popup_copy),
                    modifier = Modifier.weight(1f)
                ) { onIntent(PopupIntent.Copy) }
            }
        }
        if (state.canReplace) {
            PillButton(
                stringResource(R.string.popup_replace),
                primary = true,
                modifier = Modifier.fillMaxWidth()
            ) { onIntent(PopupIntent.Replace) }
        } else {
            PillButton(
                stringResource(R.string.popup_copy),
                primary = true,
                modifier = Modifier.fillMaxWidth()
            ) { onIntent(PopupIntent.Copy) }
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
        val retryable = error?.isRetryable != false
        val fixInSettings = error?.needsSettings == true
        // Stacked full-width: side by side, "Spróbuj ponownie" next to
        // "Ustawienia" ran out of room on small screens with large fonts.
        if (retryable) {
            PillButton(
                stringResource(R.string.popup_tryAgain),
                icon = Icons.Rounded.Refresh,
                modifier = Modifier.fillMaxWidth()
            ) { onIntent(PopupIntent.Retry) }
        }
        if (fixInSettings) {
            PillButton(
                stringResource(R.string.home_openSettings),
                icon = Icons.Rounded.Settings,
                primary = !retryable,
                modifier = Modifier.fillMaxWidth()
            ) { onIntent(PopupIntent.OpenSettings) }
        }
    }
}

/** Caught when the popup opens, not after the user has picked an action. */
@Composable
private fun SetupView(onIntent: (PopupIntent) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Rounded.Settings,
            contentDescription = null,
            tint = Palette.secondary,
            modifier = Modifier.size(28.dp)
        )
        Text(
            stringResource(R.string.home_setupHint),
            color = Palette.text,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        PillButton(
            stringResource(R.string.home_openSettings),
            icon = Icons.Rounded.Settings,
            primary = true,
            modifier = Modifier.fillMaxWidth()
        ) { onIntent(PopupIntent.OpenSettings) }
    }
}

@Composable
private fun EmptyView(tooShort: Boolean) {
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
            // Its own string, not the tutorial's "text below" hint: here the
            // popup floats over someone else's app with nothing below it.
            if (tooShort) {
                stringResource(R.string.android_selectionTooShort)
            } else {
                stringResource(
                    R.string.android_noSelection,
                    stringResource(R.string.android_processText)
                )
            },
            color = Palette.secondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

private const val SLOW_HINT_MILLIS = 8_000L
