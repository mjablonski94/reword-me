package dev.mjablonski.rewordme.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Writing-Tools-style panel rendered over the DWM acrylic backdrop:
 * describe field with the gradient glow, Proofread/Rewrite actions,
 * tone presets - then the result view.
 */
private object Palette {
    val text = Color(0xFFF2F2F5)
    val secondary = Color(0xFFB9B9C2)
    val panel = Color(0x33FFFFFF)
    val well = Color(0x22000000)
    val accent = Color(0xFF8B7CF6)
    val glow = listOf(Color(0xFF4C9AFF), Color(0xFF9B6CF6), Color(0xFFE86CA8), Color(0xFF4C9AFF))
}

@Composable
fun PopupContent(viewModel: PopupViewModel) {
    Column(
        modifier = Modifier.padding(14.dp).width(320.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Header(viewModel)
        when (viewModel.stage) {
            PopupViewModel.Stage.MENU -> Menu(viewModel)
            PopupViewModel.Stage.LOADING -> Loading(viewModel)
            PopupViewModel.Stage.RESULT -> ResultView(viewModel)
            PopupViewModel.Stage.FAILED -> ErrorView(viewModel)
        }
    }
}

@Composable
private fun Header(viewModel: PopupViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (viewModel.stage == PopupViewModel.Stage.RESULT ||
            viewModel.stage == PopupViewModel.Stage.FAILED
        ) {
            RoundIcon("<") { viewModel.backToMenu() }
            Spacer(Modifier.width(6.dp))
        }
        Text("RewordMe", color = Palette.secondary, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        if (viewModel.stage == PopupViewModel.Stage.RESULT) {
            Text(
                viewModel.modelLabel,
                color = Palette.secondary.copy(alpha = 0.7f),
                fontSize = 10.sp,
                maxLines = 1
            )
            Spacer(Modifier.width(6.dp))
        }
        RoundIcon("x") { viewModel.onClose?.invoke() }
    }
}

@Composable
private fun RoundIcon(glyph: String, onClick: () -> Unit) {
    Text(
        glyph,
        color = Palette.secondary,
        fontSize = 11.sp,
        modifier = Modifier
            .size(20.dp)
            .background(Palette.panel, CircleShape)
            .clickable(onClick = onClick)
            .padding(top = 1.dp)
            .wrapContentCenter()
    )
}

private fun Modifier.wrapContentCenter() = this

@Composable
private fun Menu(viewModel: PopupViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DescribeField(viewModel)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton(PopupViewModel.proofread.title, Modifier.weight(1f)) {
                viewModel.reword(PopupViewModel.proofread.instruction)
            }
            BigButton(PopupViewModel.rewrite.title, Modifier.weight(1f)) {
                viewModel.reword(null)
            }
        }
        Divider(color = Palette.panel)
        PopupViewModel.tonePresets.forEach { preset ->
            Text(
                preset.title,
                color = Palette.text,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.reword(preset.instruction) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
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
        BasicTextField(
            value = viewModel.steering,
            onValueChange = { viewModel.steering = it },
            singleLine = true,
            textStyle = TextStyle(color = Palette.text, fontSize = 13.sp),
            cursorBrush = SolidColor(Palette.accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (viewModel.steering.isEmpty()) {
                    Text("Describe your change", color = Palette.secondary, fontSize = 13.sp)
                }
                inner()
            }
        )
    }
}

@Composable
private fun BigButton(title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(Palette.panel, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Text(title, color = Palette.text, fontSize = 12.sp)
    }
}

@Composable
private fun Loading(viewModel: PopupViewModel) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
    ) {
        Text("Rewording...", color = Palette.secondary, fontSize = 13.sp)
        Text(
            "Cancel",
            color = Palette.accent,
            fontSize = 12.sp,
            modifier = Modifier.clickable { viewModel.backToMenu() }
        )
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
                .heightIn(min = 60.dp, max = 200.dp)
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
            ActionText("Again") { viewModel.regenerate() }
            Spacer(Modifier.weight(1f))
            ActionText("Copy") { viewModel.copy() }
            Text(
                "Replace",
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .background(Palette.accent, RoundedCornerShape(50))
                    .clickable { viewModel.replace() }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ActionText(title: String, onClick: () -> Unit) {
    Text(
        title,
        color = Palette.text,
        fontSize = 13.sp,
        modifier = Modifier
            .background(Palette.panel, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun ErrorView(viewModel: PopupViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Something went wrong", color = Color(0xFFF0A860), fontSize = 13.sp)
        Text(viewModel.errorMessage, color = Palette.secondary, fontSize = 12.sp)
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            ActionText("Try Again") { viewModel.regenerate() }
        }
    }
}
