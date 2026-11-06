package com.mechy007.nexus.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

private data class ProviderPreset(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String
)

private val providerPresets = listOf(
    ProviderPreset("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    ProviderPreset("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
    ProviderPreset("groq", "Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile")
)

@Composable
fun AIProviderSettings(onConnectionChanged: (Boolean) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(providerPresets.first()) }
    var model by rememberSaveable { mutableStateOf(selected.model) }
    var baseUrl by rememberSaveable { mutableStateOf(selected.baseUrl) }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var configured by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Choose a provider and add its API key.") }

    LaunchedEffect(Unit) {
        configured = AndroidNexusRuntime.isConfigured(androidx.compose.ui.platform.LocalContext.current)
        onConnectionChanged(configured)
        AndroidNexusRuntime.currentSettings()?.let { saved ->
            providerPresets.firstOrNull { it.id == saved.providerId }?.let { selected = it }
            model = saved.model
            baseUrl = saved.baseUrl
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Text("AI PROVIDER", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(
                if (configured) "Provider settings are saved on this device." else "Connect a compatible AI provider to enable real Allium replies.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                providerPresets.forEach { preset ->
                    FilterChip(
                        selected = selected.id == preset.id,
                        onClick = {
                            selected = preset
                            baseUrl = preset.baseUrl
                            model = preset.model
                        },
                        label = { Text(preset.name) }
                    )
                }
            }
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model") },
                singleLine = true
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API base URL") },
                singleLine = true
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (configured) "New API key (leave blank to keep saved key)" else "API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(
                    onClick = {
                        val existing = AndroidNexusRuntime.currentSettings()
                        val key = apiKey.trim()
                        if (model.isBlank() || baseUrl.isBlank() || (!configured && key.isBlank())) {
                            message = "Enter a model, API URL and API key."
                        } else if (!baseUrl.startsWith("https://")) {
                            message = "Use an HTTPS provider URL to protect requests."
                        } else {
                            runCatching {
                                val providerSettings = AndroidProviderSettings(
                                    selected.id,
                                    selected.name,
                                    baseUrl.trim().trimEnd('/'),
                                    model.trim()
                                )
                                if (key.isNotBlank()) {
                                    AndroidNexusRuntime.configure(
                                        NexusSettingsContext.current,
                                        providerSettings,
                                        key
                                    )
                                } else {
                                    requireNotNull(existing) { "Enter the API key to connect." }
                                    AndroidNexusRuntime.configureWithSavedKey(NexusSettingsContext.current, providerSettings)
                                }
                            }.onSuccess {
                                configured = true
                                apiKey = ""
                                message = "Provider saved. NEXUS will verify it with your first message."
                                onConnectionChanged(true)
                            }.onFailure {
                                message = it.message ?: "Could not save provider settings."
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(if (configured) "Save settings" else "Connect") }
                if (configured) {
                    Button(onClick = {
                        AndroidNexusRuntime.disconnect(NexusSettingsContext.current)
                        configured = false
                        message = "Provider disconnected and saved key deleted."
                        onConnectionChanged(false)
                    }) { Text("Disconnect") }
                }
            }
        }
    }
}

/** Set by the parent Settings screen so this composable does not retain an Activity. */
private object NexusSettingsContext {
    lateinit var current: android.content.Context
}
