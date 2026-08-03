package dev.mjablonski.rewordme.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mjablonski.rewordme.models.OllamaEndpoint
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RuleKind
import java.awt.Desktop
import java.net.URI

enum class SettingsTab(val titleKey: String) {
    PROVIDER("tab.provider"),
    REWRITING("tab.rewriting"),
    GENERAL("tab.general")
}

@Composable
fun SettingsContent(viewModel: SettingsViewModel, initialTab: SettingsTab? = null) {
    MaterialTheme(colors = darkColors(primary = Palette.accent, secondary = Palette.accent)) {
        // A broken shortcut is why the window opens by itself, so start on
        // the tab that can fix it.
        var tab by remember {
            mutableStateOf(
                initialTab
                    ?: if (viewModel.hotkeyStatus == HotkeyStatus.Active) SettingsTab.PROVIDER
                    else SettingsTab.GENERAL
            )
        }
        Column(Modifier.fillMaxSize().background(Palette.surface)) {
            Toolbar(tab) { selected ->
                if (tab == SettingsTab.GENERAL && selected != tab) {
                    viewModel.cancelRecording()
                }
                tab = selected
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                when (tab) {
                    SettingsTab.PROVIDER -> ProviderTab(viewModel)
                    SettingsTab.REWRITING -> RewritingTab(viewModel)
                    SettingsTab.GENERAL -> GeneralTab(viewModel)
                }
            }
        }
    }
}

/**
 * macOS hangs the tab picker in the window's own title bar. Windows owns its
 * title bar, so the picker gets a toolbar strip of its own right beneath it.
 */
@Composable
private fun Toolbar(selected: SettingsTab, onSelect: (SettingsTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Palette.toolbar)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.background(Palette.segmentTrack, pillShape).padding(2.dp)
            ) {
                SettingsTab.entries.forEachIndexed { index, entry ->
                    // macOS only draws a divider where two unselected segments meet.
                    if (index > 0) {
                        val neighbours = listOf(entry, SettingsTab.entries[index - 1])
                        Box(
                            Modifier
                                .size(1.dp, 13.dp)
                                .background(
                                    if (selected in neighbours) Color.Transparent
                                    else Color(0x2EFFFFFF)
                                )
                        )
                    }
                    Segment(entry, entry == selected) { onSelect(entry) }
                }
            }
        }
        Divider(color = Color(0x26000000), thickness = 1.dp)
    }
}

@Composable
private fun Segment(tab: SettingsTab, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(pillShape)
            .background(if (selected) Palette.segmentSelected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 4.dp)
    ) {
        Text(
            Strings[tab.titleKey],
            color = Palette.text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/**
 * One section of a macOS grouped Form: a bold header, a card of rows divided by
 * hairlines, and an optional footer under the card. Rows are declared through
 * [SectionScope.row] so the dividers cannot be forgotten or doubled up.
 */
@Composable
private fun Section(
    title: String,
    footer: String? = null,
    content: SectionScope.() -> Unit
) {
    val rows = SectionScope().apply(content).rows
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            color = Palette.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp, bottom = 7.dp)
        )
        Column(Modifier.fillMaxWidth().background(Palette.card, insetShape)) {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    Divider(
                        color = Palette.hairline,
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                row()
            }
        }
        if (footer != null) {
            Text(
                footer,
                color = Palette.secondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 7.dp)
            )
        }
    }
}

private class SectionScope {
    val rows = mutableListOf<@Composable () -> Unit>()

    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/** One row of a card: label on the left, controls trailing, as macOS lays it out. */
@Composable
private fun FormRow(label: String? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            // 1dp light on top: centring a line of text on its layout box sits
            // the ink low, because the ascent above the caps is far deeper than
            // the descent below the baseline.
            .padding(start = 12.dp, end = 12.dp, top = 5.dp, bottom = 7.dp)
    ) {
        if (label != null) {
            Text(label, color = Palette.text, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
        }
        content()
    }
}

/** A caption row living inside the card, as macOS renders Text in a Section. */
@Composable
private fun Caption(text: String, color: Color = Palette.secondary) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)
    )
}

@Composable
private fun LinkText(text: String, url: String) {
    Text(
        text,
        color = Palette.link,
        fontSize = 12.sp,
        modifier = Modifier.clickable { runCatching { Desktop.getDesktop().browse(URI(url)) } }
    )
}

/** macOS pop-up button: the current value plus the up/down chevrons. */
@Composable
private fun <T> PopUpButton(
    label: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    // Set alongside a fixed width on [modifier], to keep a column of pop-up
    // buttons the same size whatever the current value happens to be.
    fillWidth: Boolean = false
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // Centred, so a button given a fixed width to keep a column aligned
            // still carries its value in the middle rather than packed left.
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .clip(controlShape)
                .background(Palette.control)
                .clickable { open = true }
                .padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            Text(
                label,
                color = Palette.text,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                Icons.Rounded.UnfoldMore,
                contentDescription = null,
                tint = Palette.secondary,
                modifier = Modifier.size(14.dp)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(onClick = {
                    open = false
                    onSelect(option)
                }) {
                    Text(optionLabel(option), fontSize = 13.sp)
                }
            }
        }
    }
}

/** macOS bordered button: a grey fill on the card, not a tinted outline. */
@Composable
private fun BorderedButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    monospace: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (enabled) Palette.text else Palette.text.copy(alpha = 0.4f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .clip(controlShape)
            .background(if (enabled) Palette.control else Palette.control.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        }
        Text(
            label,
            color = tint,
            fontSize = 13.sp,
            maxLines = 1,
            fontFamily = if (monospace) FontFamily.Monospace else null
        )
    }
}

/**
 * The macOS switch, drawn rather than themed: Material's own is built around a
 * 48dp touch target that would push every row well past the 44dp macOS uses.
 */
@Composable
private fun MacSwitch(checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    val track by animateColorAsState(
        when {
            !enabled -> Palette.control
            checked -> Palette.accent
            else -> Color(0xFF5A5C60)
        }
    )
    val thumbOffset by animateDpAsState(if (checked) 16.dp else 0.dp)
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .size(38.dp, 22.dp)
            .clip(pillShape)
            .background(track)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(2.dp)
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(18.dp)
                .background(if (enabled) Color.White else Color(0xFF9A9AA0), CircleShape)
        )
    }
}

/**
 * A field the macOS way: no box at all. The label sits at the left of the row
 * and the value is typed against the right edge, exactly as SwiftUI lays a
 * TextField out inside a Form.
 */
@Composable
private fun FieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    masked: Boolean = false
) {
    FormRow {
        Text(label, color = Palette.text, fontSize = 13.sp, maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Palette.text,
                fontSize = 13.sp,
                textAlign = TextAlign.End
            ),
            cursorBrush = SolidColor(Palette.accent),
            visualTransformation =
                if (masked) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Borderless too, but filling its row and falling back to a placeholder. Used
 * where the row already carries a label of its own, so repeating it macOS-style
 * would leave the value nowhere to go.
 */
@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        if (value.isEmpty()) {
            Text(placeholder, color = Palette.secondary, fontSize = 13.sp, maxLines = 1)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Palette.text, fontSize = 13.sp),
            cursorBrush = SolidColor(Palette.accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** The near-black well macOS gives a multi-line editor. */
@Composable
private fun TextArea(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(color = Palette.text, fontSize = 13.sp, lineHeight = 18.sp),
        cursorBrush = SolidColor(Palette.accent),
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .background(Palette.editor, RoundedCornerShape(5.dp))
            .heightIn(min = 120.dp)
            .padding(8.dp)
    )
}

/** An icon-and-text status, the way macOS pairs a glyph with its label. */
@Composable
private fun StatusLabel(text: String, color: Color, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, color = color, fontSize = 12.sp)
    }
}

@Composable
private fun ProviderTab(viewModel: SettingsViewModel) {
    val provider = viewModel.config.provider

    Section(Strings["provider.section"]) {
        row {
            FormRow(Strings["provider.section"]) {
                PopUpButton(
                    label = provider.displayName,
                    options = ProviderKind.entries,
                    optionLabel = { it.displayName },
                    onSelect = viewModel::selectProvider
                )
            }
        }
    }

    if (provider.requiresApiKey) {
        Section(Strings["provider.apiKeySection"]) {
            row {
                FieldRow(
                    label = provider.keyPlaceholder,
                    value = viewModel.apiKey,
                    onValueChange = viewModel::editApiKey,
                    masked = true
                )
            }
            row {
                FormRow {
                    LinkText(
                        Strings.format("provider.getKey", provider.apiKeyConsoleName),
                        provider.apiKeyConsoleUrl
                    )
                    Spacer(Modifier.weight(1f))
                    if (viewModel.keySaved) {
                        StatusLabel(
                            Strings["provider.saved"],
                            Palette.ok,
                            Icons.Rounded.CheckCircle
                        )
                    }
                    BorderedButton(
                        Strings["provider.saveKey"],
                        enabled = viewModel.apiKey.isNotBlank(),
                        onClick = viewModel::saveApiKey
                    )
                }
            }
            if (viewModel.keySaveError != null) {
                row {
                    FormRow {
                        StatusLabel(
                            viewModel.keySaveError!!,
                            Palette.warn,
                            Icons.Rounded.Warning
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            row { Caption(Strings["provider.credentialCaption"]) }
        }
    } else {
        Section(
            Strings["ollama.section"],
            Strings.format("ollama.caption", OllamaEndpoint.DEFAULT_HOST)
        ) {
            row { Caption(Strings["ollama.blurb"], Palette.text) }
            row {
                FieldRow(
                    label = Strings["ollama.serverLabel"],
                    value = viewModel.config.ollamaHost,
                    onValueChange = viewModel::setOllamaHost
                )
            }
            row {
                FormRow {
                    LinkText(
                        Strings.format("ollama.getLink", provider.apiKeyConsoleName),
                        provider.apiKeyConsoleUrl
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    Section(Strings["provider.modelSection"]) {
        row {
            FormRow(Strings["provider.modelLabel"]) {
                PopUpButton(
                    label = viewModel.config.model ?: Strings["provider.automatic"],
                    options = listOf<String?>(null) +
                        viewModel.availableModels.sortedBy { it.id }.map { it.id },
                    optionLabel = { it ?: Strings["provider.automatic"] },
                    onSelect = viewModel::selectModel,
                    // Capped rather than weighted: a weight here would compete
                    // with the row's spacer and stop the button hugging the
                    // right edge, breaking the column the pickers line up in.
                    modifier = Modifier.widthIn(max = 260.dp)
                )
            }
        }
        row {
            FormRow {
                val error = viewModel.modelsError
                val hint = viewModel.automaticModelHint
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    when {
                        viewModel.isLoadingModels -> CircularProgressIndicator(
                            Modifier.size(14.dp),
                            color = Palette.accent,
                            strokeWidth = 2.dp
                        )
                        error != null -> Text(
                            error,
                            color = Palette.warn,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        hint != null -> Text(
                            Strings.format("provider.automaticHint", hint),
                            color = Palette.secondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                BorderedButton(
                    Strings["provider.loadModels"],
                    enabled = !viewModel.isLoadingModels &&
                        (!provider.requiresApiKey || viewModel.apiKey.isNotBlank()),
                    onClick = viewModel::loadModels
                )
            }
        }
    }
}

@Composable
private fun RewritingTab(viewModel: SettingsViewModel) {
    Section(Strings["rules.section"], Strings["rules.footer"]) {
        viewModel.config.rules.forEach { rule ->
            row {
                FormRow {
                    MacSwitch(rule.isEnabled) { enabled ->
                        viewModel.updateRule(rule.id) { it.copy(isEnabled = enabled) }
                    }
                    PopUpButton(
                        label = Strings[rule.kind.labelKey],
                        options = RuleKind.entries,
                        optionLabel = { Strings[it.labelKey] },
                        onSelect = { kind ->
                            viewModel.updateRule(rule.id) { it.copy(kind = kind) }
                        },
                        modifier = Modifier.width(88.dp),
                        fillWidth = true
                    )
                    PlainField(
                        value = rule.text,
                        onValueChange = { text ->
                            viewModel.updateRule(rule.id) { it.copy(text = text) }
                        },
                        placeholder = Strings["rules.placeholder"],
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Rounded.RemoveCircleOutline,
                        contentDescription = null,
                        tint = Palette.secondary,
                        modifier = Modifier
                            .size(17.dp)
                            .clip(CircleShape)
                            .clickable { viewModel.removeRule(rule.id) }
                    )
                }
            }
        }
        row {
            FormRow {
                BorderedButton(Strings["rules.add"], icon = Icons.Rounded.Add) {
                    viewModel.addRule()
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }

    Section(Strings["base.section"], Strings["base.footer"]) {
        row { TextArea(viewModel.config.basePrompt, viewModel::setBasePrompt) }
    }
}

@Composable
private fun GeneralTab(viewModel: SettingsViewModel) {
    Section(Strings["shortcut.section"]) {
        row { ShortcutRow(viewModel) }
        row {
            Caption(
                if (viewModel.isRecordingHotkey) Strings["shortcut.hintRecording"]
                else Strings["shortcut.hintIdle"]
            )
        }
        if (viewModel.hotkeyStatus == HotkeyStatus.Conflict ||
            viewModel.hotkeyStatus == HotkeyStatus.Failed
        ) {
            row {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = Palette.warn,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        Strings.format("shortcut.conflict", viewModel.config.hotkey.display),
                        color = Palette.warn,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }

    Section(Strings["general.startup"]) {
        row {
            FormRow(Strings["general.launchAtLogin"]) {
                MacSwitch(
                    checked = viewModel.launchAtLogin,
                    enabled = viewModel.launchAtLoginSupported,
                    onCheckedChange = viewModel::toggleLaunchAtLogin
                )
            }
        }
    }

    Section(Strings["general.support"]) {
        row {
            FormRow {
                LinkText(Strings["general.buyCoffee"], "https://buymeacoffee.com/kofcio94f")
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ShortcutRow(viewModel: SettingsViewModel) {
    val focus = remember { FocusRequester() }
    DisposableEffect(Unit) {
        onDispose { viewModel.cancelRecording() }
    }
    LaunchedEffect(viewModel.isRecordingHotkey) {
        if (viewModel.isRecordingHotkey) focus.requestFocus()
    }
    FormRow(Strings["shortcut.label"]) {
        BorderedButton(
            label = if (viewModel.isRecordingHotkey) Strings["shortcut.recording"]
            else viewModel.config.hotkey.display,
            monospace = true,
            modifier = Modifier
                .widthIn(min = 132.dp)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (!viewModel.isRecordingHotkey) return@onPreviewKeyEvent false
                    when (val outcome = HotkeyRecorder.interpret(event)) {
                        is RecorderOutcome.Recorded -> {
                            viewModel.applyRecorded(outcome.hotkey)
                            true
                        }
                        RecorderOutcome.Cancelled -> {
                            viewModel.cancelRecording()
                            true
                        }
                        // Swallow everything while recording so a stray key
                        // cannot activate the button or move focus away.
                        RecorderOutcome.Ignored -> true
                    }
                }
        ) {
            if (viewModel.isRecordingHotkey) viewModel.cancelRecording()
            else viewModel.beginRecording()
        }
    }
}

private val RuleKind.labelKey: String
    get() = if (this == RuleKind.DO) "rules.do" else "rules.dont"
