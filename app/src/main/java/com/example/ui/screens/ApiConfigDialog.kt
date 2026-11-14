package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repository.TelegramRepository

@Composable
fun ApiConfigDialog(
    repository: TelegramRepository,
    onDismiss: () -> Unit
) {
    var apiIdText by remember { mutableStateOf(repository.getApiId().toString()) }
    var apiHashText by remember { mutableStateOf(repository.getApiHash()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Telegram API Credentials", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "To connect to official Telegram servers, obtain your API ID & API Hash from my.telegram.org/apps.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiIdText,
                    onValueChange = {
                        apiIdText = it.filter { ch -> ch.isDigit() }
                        errorMessage = null
                    },
                    label = { Text("API ID (App api_id)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_id_input")
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = apiHashText,
                    onValueChange = {
                        apiHashText = it.trim()
                        errorMessage = null
                    },
                    label = { Text("API HASH (App api_hash)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_hash_input")
                )

                if (errorMessage != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "How to get it:\n1. Open https://my.telegram.org/apps in a browser\n2. Log in with your Telegram account\n3. Click 'API development tools'\n4. Create app named 'RustGram' to get your api_id and api_hash",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val id = apiIdText.toIntOrNull()
                    if (id == null || id <= 0) {
                        errorMessage = "Please enter a valid numeric API ID"
                    } else if (apiHashText.length < 16) {
                        errorMessage = "Please enter a valid 32-character API Hash"
                    } else {
                        repository.saveApiCredentials(id, apiHashText)
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("save_api_creds_button")
            ) {
                Text("Save & Connect")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_api_creds_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
