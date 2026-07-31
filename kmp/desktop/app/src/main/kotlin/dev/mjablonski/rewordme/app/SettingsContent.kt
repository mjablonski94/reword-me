package dev.mjablonski.rewordme.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mjablonski.rewordme.models.OllamaEndpoint
import dev.mjablonski.rewordme.models.ProviderKind

/**
 * Phase-1 settings: provider, API key, Ollama host. Rules, base prompt
 * and the shortcut recorder follow in phase 2.
 */
@Composable
fun SettingsContent(dependencies: AppDependencies) {
    MaterialTheme(colors = darkColors()) {
        var config by remember { mutableStateOf(dependencies.configStore.load()) }
        var apiKey by remember {
            mutableStateOf(dependencies.keyStore.apiKey(config.provider) ?: "")
        }
        var providerMenu by remember { mutableStateOf(false) }
        var saved by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Provider", style = MaterialTheme.typography.subtitle2)
            OutlinedButton(onClick = { providerMenu = true }) {
                Text(config.provider.displayName)
            }
            DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                ProviderKind.entries.forEach { kind ->
                    DropdownMenuItem(onClick = {
                        providerMenu = false
                        config = config.copy(provider = kind)
                        dependencies.configStore.save(config)
                        apiKey = dependencies.keyStore.apiKey(kind) ?: ""
                        saved = false
                    }) { Text(kind.displayName) }
                }
            }

            if (config.provider.requiresApiKey) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; saved = false },
                    label = { Text("API key (get one at ${config.provider.apiKeyConsoleUrl})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = config.ollamaHost,
                    onValueChange = {
                        config = config.copy(ollamaHost = it)
                        dependencies.configStore.save(config)
                    },
                    label = { Text("Ollama server (default ${OllamaEndpoint.DEFAULT_HOST})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = {
                    dependencies.keyStore.setApiKey(config.provider, apiKey)
                    saved = true
                }) { Text("Save") }
                if (saved) Text("Saved", style = MaterialTheme.typography.caption)
            }

            Text(
                "Model: automatic (least costly). Shortcut: ${config.hotkey.display}.",
                style = MaterialTheme.typography.caption
            )
        }
    }
}
