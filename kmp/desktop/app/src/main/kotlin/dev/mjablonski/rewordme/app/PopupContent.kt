package dev.mjablonski.rewordme.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HighlightAlt
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Writing-Tools-style panel rendered over the DWM acrylic backdrop:
 * describe field with the gradient glow, Proofread/Rewrite actions,
 * tone presets - then the result view.
 */
@Composable
fun PopupContent(
    viewModel: PopupViewModel,
    // The window is undecorated, so the header strip stands in for the missing
    // title bar. The caller supplies the behaviour because dragging a window
    // needs the WindowScope, which offscreen renders do not have.
    dragHandle: @Composable (@Composable () -> Unit) -> Unit = { it() }
) {
    Column(
        modifier = Modifier.padding(14.dp).width(320.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        dragHandle { Header(viewModel) }
        when (viewModel.stage) {
            PopupViewModel.Stage.EMPTY -> EmptyView(viewModel)
            PopupViewModel.Stage.MENU -> Menu(viewModel)
            PopupViewModel.Stage.LOADING -> Loading(viewModel)
            PopupViewModel.Stage.RESULT -> ResultView(viewModel)
            PopupViewModel.Stage.FAILED -> ErrorView(viewModel)
        }
    }
}

@Composable
private fun Header(viewModel: PopupViewModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        // Spans the panel and is a little taller than the round buttons so the
        // strip is an easy target to grab the window by.
        modifier = Modifier.fillMaxWidth().heightIn(min = 26.dp)
    ) {
        if (viewModel.stage == PopupViewModel.Stage.RESULT ||
            viewModel.stage == PopupViewModel.Stage.FAILED
        ) {
            RoundIcon(Icons.Rounded.ChevronLeft) { viewModel.backToMenu() }
        }
        Text(
            "RewordMe",
            color = Palette.secondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        if (viewModel.stage == PopupViewModel.Stage.RESULT && viewModel.modelLabel.isNotEmpty()) {
            Text(
                viewModel.modelLabel,
                color = Palette.secondary.copy(alpha = 0.7f),
                fontSize = 10.sp,
                maxLines = 1
            )
        }
        RoundIcon(Icons.Rounded.Close) { viewModel.onClose?.invoke() }
    }
}

@Composable
private fun Menu(viewModel: PopupViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DescribeField(viewModel)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton(PopupViewModel.proofread, Modifier.weight(1f)) {
                viewModel.reword(PopupViewModel.proofread.instruction)
            }
            BigButton(PopupViewModel.rewrite, Modifier.weight(1f)) {
                viewModel.reword(null)
            }
        }
        Divider(color = Palette.panel)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PopupViewModel.tonePresets.forEach { preset ->
                PresetRow(preset) { viewModel.reword(preset.instruction) }
            }
        }
    }
}

@Composable
private fun PresetRow(preset: PopupViewModel.Preset, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
            Icon(
                preset.icon,
                contentDescription = null,
                tint = Palette.secondary,
                modifier = Modifier.size(15.dp)
            )
        }
        Text(preset.title, color = Palette.text, fontSize = 14.sp)
    }
}

@Composable
private fun DescribeField(viewModel: PopupViewModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.2.dp, Brush.sweepGradient(Palette.glow), RoundedCornerShape(50))
            .background(Palette.well, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(14.dp)
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
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            value = viewModel.steering,
            onValueChange = { viewModel.steering = it },
            singleLine = true,
            textStyle = TextStyle(color = Palette.text, fontSize = 13.sp),
            cursorBrush = SolidColor(Palette.accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (viewModel.steering.isEmpty()) {
                    Text(
                        Strings["popup.describePlaceholder"],
                        color = Palette.secondary,
                        fontSize = 13.sp
                    )
                }
                inner()
            }
        )
    }
}

@Composable
private fun BigButton(
    preset: PopupViewModel.Preset,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.panel)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp)
    ) {
        Icon(
            preset.icon,
            contentDescription = null,
            tint = Palette.text,
            modifier = Modifier.size(18.dp)
        )
        Text(preset.title, color = Palette.text, fontSize = 12.sp)
    }
}

@Composable
private fun Loading(viewModel: PopupViewModel) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp)
    ) {
        CircularProgressIndicator(
            Modifier.size(20.dp),
            color = Palette.accent,
            strokeWidth = 2.dp
        )
        Text(Strings["popup.rewording"], color = Palette.secondary, fontSize = 13.sp)
        PillButton(Strings["popup.cancel"]) { viewModel.backToMenu() }
    }
}

@Composable
private fun ResultView(viewModel: PopupViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            viewModel.result,
            color = Palette.text,
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 70.dp, max = 200.dp)
                .background(Palette.well, RoundedCornerShape(12.dp))
                .padding(10.dp)
                .verticalScroll(rememberScrollState())
        )
        DescribeField(viewModel)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            PillButton(Strings["popup.again"], icon = Icons.Rounded.Refresh) {
                viewModel.regenerate()
            }
            Spacer(Modifier.weight(1f))
            PillButton(Strings["popup.copy"]) { viewModel.copy() }
            PillButton(Strings["popup.replace"], primary = true) { viewModel.replace() }
        }
    }
}

@Composable
private fun ErrorView(viewModel: PopupViewModel) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.well, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = Palette.warn,
                modifier = Modifier.size(15.dp)
            )
            Text(
                Strings["popup.errorTitle"],
                color = Palette.warn,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(viewModel.errorMessage, color = Palette.secondary, fontSize = 12.sp)
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            PillButton(Strings["popup.tryAgain"]) { viewModel.regenerate() }
        }
    }
}

@Composable
private fun EmptyView(viewModel: PopupViewModel) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp)
    ) {
        Icon(
            Icons.Rounded.HighlightAlt,
            contentDescription = null,
            tint = Palette.secondary,
            modifier = Modifier.size(26.dp)
        )
        Text(
            Strings.format("popup.noSelection", viewModel.shortcutDisplay),
            color = Palette.secondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}
