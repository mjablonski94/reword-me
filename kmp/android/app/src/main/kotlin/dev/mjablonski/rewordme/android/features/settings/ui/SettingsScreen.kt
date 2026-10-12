package dev.mjablonski.rewordme.android.features.settings.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mjablonski.rewordme.android.R
import dev.mjablonski.rewordme.android.common.Palette
import dev.mjablonski.rewordme.android.common.PillButton
import dev.mjablonski.rewordme.android.common.RoundIcon
import dev.mjablonski.rewordme.android.common.TextInput
import dev.mjablonski.rewordme.android.common.controlShape
import dev.mjablonski.rewordme.android.common.insetShape
import dev.mjablonski.rewordme.android.common.localized
import dev.mjablonski.rewordme.android.features.settings.presentation.SettingsIntent
import dev.mjablonski.rewordme.android.features.settings.presentation.SettingsState
import dev.mjablonski.rewordme.android.features.settings.presentation.SettingsTab
import dev.mjablonski.rewordme.models.ProviderKind

private const val SUPPORT_URL = "https://buymeacoffee.com/kofcio94f"

@Composable
fun SettingsScreen(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit = {}
) {
    // The activity is edge-to-edge; the surface paints behind the system
    // bars and the content starts below them.
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.surface)
            .statusBarsPadding()
    ) {
        Toolbar(state, onIntent, onBack)
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            // Inset padding sits OUTSIDE the scroll modifier: applied inside,
            // it would pad the content instead of shrinking the viewport and
            // the keyboard would cover the focused field.
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            when (state.tab) {
                SettingsTab.PROVIDER -> ProviderTab(state, onIntent)
                SettingsTab.REWRITING -> RewritingTab(state, onIntent)
                SettingsTab.GENERAL -> GeneralTab(state, onIntent)
            }
        }
    }
}

@Composable
private fun Toolbar(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(Palette.toolbar).padding(16.dp, 12.dp)) {
        // A visible way back: Settings is often reached from the popup, in
        // its own task, where the system back gesture is not obvious.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RoundIcon(
                Icons.Rounded.ChevronLeft,
                contentDescription = stringResource(R.string.android_back),
                onClick = onBack
            )
            Text(
                stringResource(R.string.settings_windowTitle),
                color = Palette.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.size(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Palette.segmentTrack)
                .padding(3.dp)
        ) {
            SegmentTab(R.string.tab_provider, state.tab == SettingsTab.PROVIDER) {
                onIntent(SettingsIntent.SelectTab(SettingsTab.PROVIDER))
            }
            SegmentTab(R.string.tab_rewriting, state.tab == SettingsTab.REWRITING) {
                onIntent(SettingsIntent.SelectTab(SettingsTab.REWRITING))
            }
            SegmentTab(R.string.tab_general, state.tab == SettingsTab.GENERAL) {
                onIntent(SettingsIntent.SelectTab(SettingsTab.GENERAL))
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SegmentTab(
    titleRes: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Palette.segmentSelected else Palette.segmentTrack)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp)
    ) {
        Text(
            stringResource(titleRes),
            color = if (selected) Palette.text else Palette.secondary,
            fontSize = 13.sp
        )
    }
}

/** macOS-style grouped section: caption above a raised card. */
@Composable
private fun SectionCard(
    title: String,
    footer: String? = null,
    body: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title.uppercase(),
            color = Palette.secondary.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.card, insetShape)
                .padding(12.dp)
        ) {
            body()
        }
        if (footer != null) {
            Text(
                footer,
                color = Palette.secondary.copy(alpha = 0.75f),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun ProviderTab(state: SettingsState, onIntent: (SettingsIntent) -> Unit) {
    val provider = state.config.provider

    SectionCard(stringResource(R.string.provider_section)) {
        Dropdown(
            current = provider.displayName,
            options = state.providers.map { it.displayName },
            onSelect = { index -> onIntent(SettingsIntent.SelectProvider(state.providers[index])) }
        )
    }

    if (provider.requiresApiKey) {
        SectionCard(
            stringResource(R.string.provider_apiKeySection),
            footer = stringResource(R.string.android_keystoreCaption)
        ) {
            TextInput(
                value = state.keyDraft,
                onValueChange = { onIntent(SettingsIntent.KeyChanged(it)) },
                placeholder = provider.keyPlaceholder,
                password = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PillButton(
                    stringResource(R.string.provider_saveKey),
                    primary = true,
                    enabled = state.keyDraft.isNotBlank()
                ) { onIntent(SettingsIntent.SaveKey) }
                // The vault never surfaces the key, so revocation needs its
                // own control - blank-and-save can't reach the delete branch.
                if (state.hasStoredKey) {
                    PillButton(stringResource(R.string.provider_removeKey)) {
                        onIntent(SettingsIntent.RemoveKey)
                    }
                }
                if (state.keySaved || (state.hasStoredKey && state.keyDraft.isEmpty())) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Palette.ok,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        stringResource(R.string.provider_saved),
                        color = Palette.ok,
                        fontSize = 13.sp
                    )
                }
                if (state.keySaveFailed) {
                    Text(
                        stringResource(R.string.provider_saveFailed),
                        color = Palette.warn,
                        fontSize = 13.sp
                    )
                }
            }
            LinkText(
                stringResource(R.string.provider_getKey, provider.apiKeyConsoleName)
            ) { onIntent(SettingsIntent.OpenUrl(provider.apiKeyConsoleUrl)) }
        }
    }

    if (provider == ProviderKind.OLLAMA) {
        SectionCard(
            stringResource(R.string.ollama_section),
            footer = stringResource(R.string.ollama_blurb)
        ) {
            LabeledRow(stringResource(R.string.ollama_serverLabel)) {
                TextInput(
                    value = state.ollamaHostDraft,
                    onValueChange = { onIntent(SettingsIntent.OllamaHostChanged(it)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // The shared default points at localhost, which on a phone is the
            // phone itself; spell out what to enter instead.
            Text(
                stringResource(R.string.android_ollamaPhoneHint),
                color = if (pointsAtThisDevice(state.ollamaHostDraft)) Palette.warn
                else Palette.secondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            LinkText(
                stringResource(R.string.ollama_getLink, provider.apiKeyConsoleName)
            ) { onIntent(SettingsIntent.OpenUrl(provider.apiKeyConsoleUrl)) }
        }
    }

    SectionCard(stringResource(R.string.provider_modelSection)) {
        val automatic = stringResource(R.string.provider_automatic)
        Dropdown(
            current = state.config.selectedModel ?: automatic,
            options = listOf(automatic) + state.models.map { it.id },
            onSelect = { index ->
                onIntent(
                    SettingsIntent.SelectModel(
                        if (index == 0) null else state.models[index - 1].id
                    )
                )
            }
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PillButton(
                stringResource(R.string.provider_loadModels),
                enabled = !state.isLoadingModels
            ) { onIntent(SettingsIntent.LoadModels) }
            if (state.isLoadingModels) {
                CircularProgressIndicator(
                    Modifier.size(16.dp),
                    color = Palette.accent,
                    strokeWidth = 2.dp
                )
            }
        }
        val context = LocalContext.current
        state.modelsError?.let { error ->
            Text(
                error.localized(context, stringResource(R.string.popup_errorTitle)),
                color = Palette.warn,
                fontSize = 12.sp
            )
        }
        state.automaticModelHint?.let { hint ->
            Text(
                stringResource(R.string.provider_automaticHint, hint),
                color = Palette.secondary.copy(alpha = 0.75f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun RewritingTab(state: SettingsState, onIntent: (SettingsIntent) -> Unit) {
    SectionCard(
        stringResource(R.string.rules_section),
        footer = stringResource(R.string.rules_footer)
    ) {
        state.config.rules.forEachIndexed { index, rule ->
            if (index > 0) HorizontalDivider(color = Palette.hairline)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = { onIntent(SettingsIntent.ToggleRule(rule.id, it)) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Palette.accent,
                        uncheckedTrackColor = Palette.control
                    )
                )
                TextInput(
                    value = state.ruleText(rule.id, rule.text),
                    onValueChange = { onIntent(SettingsIntent.RuleTextChanged(rule.id, it)) },
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = Palette.secondary,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(controlShape)
                        .clickable { onIntent(SettingsIntent.RemoveRule(rule.id)) }
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextInput(
                value = state.newRuleDraft,
                onValueChange = { onIntent(SettingsIntent.NewRuleChanged(it)) },
                placeholder = stringResource(R.string.rules_placeholder),
                modifier = Modifier.weight(1f)
            )
            PillButton(
                stringResource(R.string.rules_add),
                enabled = state.newRuleDraft.isNotBlank()
            ) { onIntent(SettingsIntent.AddRule) }
        }
    }

    SectionCard(
        stringResource(R.string.base_section),
        footer = stringResource(R.string.base_footer)
    ) {
        TextInput(
            value = state.basePromptDraft,
            onValueChange = { onIntent(SettingsIntent.BasePromptChanged(it)) },
            singleLine = false,
            minHeight = 88.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun GeneralTab(state: SettingsState, onIntent: (SettingsIntent) -> Unit) {
    val context = LocalContext.current

    FloatingIconSection(state, onIntent)

    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }

    SectionCard(stringResource(R.string.general_about)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.general_version),
                color = Palette.text,
                fontSize = 14.sp
            )
            Spacer(Modifier.weight(1f))
            Text(version, color = Palette.secondary, fontSize = 14.sp)
        }
    }

    SectionCard(stringResource(R.string.general_support)) {
        LinkText(stringResource(R.string.general_buyCoffee)) {
            onIntent(SettingsIntent.OpenUrl(SUPPORT_URL))
        }
    }
}

/**
 * The floating icon's switch. It reads as one switch, though it spans two:
 * Android's accessibility setting (only the user can flip it, and only after
 * the disclosure) and RewordMe's own, which hides the icon at once.
 */
@Composable
private fun FloatingIconSection(state: SettingsState, onIntent: (SettingsIntent) -> Unit) {
    SectionCard(
        stringResource(R.string.floating_title),
        footer = stringResource(R.string.floating_description)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(R.string.floating_switch),
                color = Palette.text,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state.floatingIconShown,
                onCheckedChange = { onIntent(SettingsIntent.FloatingIconToggled(it)) },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Palette.accent,
                    uncheckedTrackColor = Palette.control
                )
            )
        }
        // Wanted on, but Android's half is off: never enabled yet, or Android
        // (or a battery saver) switched the service off since.
        if (state.floatingIconOn && !state.floatingServiceOn) {
            HorizontalDivider(color = Palette.hairline)
            Text(
                stringResource(R.string.floating_serviceOff),
                color = Palette.warn,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            LinkText(stringResource(R.string.floating_openAccessibility)) {
                onIntent(SettingsIntent.OpenAccessibilitySettings)
            }
            // Android 13+ greys the switch out for apps installed from a file.
            if (Build.VERSION.SDK_INT >= 33) {
                Text(
                    stringResource(R.string.floating_restrictedHint),
                    color = Palette.secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                LinkText(stringResource(R.string.floating_openAppInfo)) {
                    onIntent(SettingsIntent.OpenAppInfo)
                }
            }
        }
    }

    if (state.floatingDisclosure) {
        AlertDialog(
            onDismissRequest = { onIntent(SettingsIntent.FloatingDisclosureDismissed) },
            containerColor = Palette.card,
            titleContentColor = Palette.text,
            textContentColor = Palette.secondary,
            title = { Text(stringResource(R.string.floating_disclosureTitle)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.floating_disclosureBody), fontSize = 14.sp)
                    Text(stringResource(R.string.floating_disclosureSteps), fontSize = 14.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { onIntent(SettingsIntent.FloatingDisclosureAccepted) }) {
                    Text(stringResource(R.string.floating_continue), color = Palette.link)
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(SettingsIntent.FloatingDisclosureDismissed) }) {
                    Text(stringResource(R.string.floating_notNow), color = Palette.secondary)
                }
            }
        )
    }
}

@Composable
private fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Palette.text, fontSize = 14.sp)
        content()
    }
}

@Composable
private fun LinkText(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Palette.link,
        fontSize = 13.sp,
        modifier = Modifier.clip(controlShape).clickable(onClick = onClick).padding(2.dp)
    )
}

@Composable
private fun Dropdown(
    current: String,
    options: List<String>,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(controlShape)
                .background(Palette.control)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                current,
                color = Palette.text,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = Palette.secondary,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 14.sp) },
                    trailingIcon = if (option == current) {
                        {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Palette.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    }
                )
            }
        }
    }
}

private fun pointsAtThisDevice(host: String): Boolean {
    val value = host.trim().lowercase()
    return value.isEmpty() || "localhost" in value || "127.0.0.1" in value
}
