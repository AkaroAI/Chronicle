package com.akaroai.chronicle.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.akaroai.chronicle.provider.ProviderSettings

@Composable
fun ProviderSettingsDialog(
    current: ProviderSettings,
    onDismiss: () -> Unit,
    onSave: (ProviderSettings) -> Unit
) {
    var enabled by remember(current) { mutableStateOf(current.enabled) }
    var autoReview by remember(current) { mutableStateOf(current.autoReviewEnabled) }
    var baseUrl by remember(current) { mutableStateOf(current.baseUrl) }
    var apiKey by remember(current) { mutableStateOf(current.apiKey) }
    var model by remember(current) { mutableStateOf(current.model) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI Provider") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                    Spacer(Modifier.width(8.dp))
                    Text(if (enabled) "Real AI enabled" else "Demo mode")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = autoReview,
                        onCheckedChange = { autoReview = it },
                        enabled = enabled
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Automatic Review suggestions")
                        Text(
                            "Uses one extra AI request after each reply.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Text(
                    "Automatic suggestions never become canon until you approve them in Review.",
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://provider.example/v1") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    ProviderSettings(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        model = model,
                        enabled = enabled,
                        autoReviewEnabled = autoReview
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
