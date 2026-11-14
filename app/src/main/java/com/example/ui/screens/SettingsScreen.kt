package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repository.TelegramRepository
import com.example.rustgram.NativeBridge
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: TelegramRepository,
    onOpenSavedMessages: () -> Unit,
    onOpenCalls: () -> Unit,
    onLogOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val userName by repository.currentUserName.collectAsState()
    val userPhone by repository.currentUserPhone.collectAsState()
    val username by repository.currentUserUsername.collectAsState()
    val userBio by repository.currentUserBio.collectAsState()
    val isDarkMode by repository.isDarkMode.collectAsState()
    val connectionStatus by repository.connectionStatus.collectAsState()
    val rustVersion by repository.rustEngineVersion.collectAsState()

    var showApiDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var cacheClearedMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = "Settings",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            actions = {
                TextButton(onClick = { showApiDialog = true }) {
                    Text(
                        "API Keys",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Centered Telegram iOS Profile Avatar & Handle
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFF5AC8FA), Color(0xFF007AFF)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userName.take(1).uppercase(),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = userName,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$userPhone  •  $username",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = userBio,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Section 1: Quick Access (Saved Messages, Calls, API Config)
            IosSettingsGroupCard {
                IosSettingsRow(
                    icon = Icons.Default.Bookmark,
                    badgeColor = IosBlueLight,
                    title = "Saved Messages",
                    onClick = onOpenSavedMessages
                )
                IosSettingsDivider()
                IosSettingsRow(
                    icon = Icons.Default.Call,
                    badgeColor = TelegramOnlineGreen,
                    title = "Recent Calls",
                    onClick = onOpenCalls
                )
                IosSettingsDivider()
                IosSettingsRow(
                    icon = Icons.Default.Key,
                    badgeColor = TelegramIosIndigo,
                    title = "Telegram API Credentials",
                    valueText = "ID ${repository.getApiId()}",
                    modifier = Modifier.testTag("settings_edit_api_btn"),
                    onClick = { showApiDialog = true }
                )
            }

            // Section 2: Appearance, Security & Storage
            IosSettingsGroupCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IosSettingsIconBadge(icon = Icons.Default.DarkMode, color = TelegramIosIndigo)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Night Mode",
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { repository.toggleDarkMode() },
                        modifier = Modifier.testTag("dark_mode_switch")
                    )
                }
                IosSettingsDivider()
                IosSettingsRow(
                    icon = Icons.Default.Lock,
                    badgeColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = "Privacy and Security",
                    valueText = "2FA On",
                    onClick = { showPrivacyDialog = true }
                )
                IosSettingsDivider()
                IosSettingsRow(
                    icon = Icons.Default.Storage,
                    badgeColor = TelegramOnlineGreen,
                    title = "Data and Storage",
                    valueText = cacheClearedMessage ?: "Clear Cache",
                    onClick = { cacheClearedMessage = "Freed 24.5 MB" }
                )
            }

            // Section 3: Live Rust Core & TDLib Diagnostics
            Text(
                text = "RUSTGRAM NATIVE ENGINE",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp)
            )

            IosSettingsGroupCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(
                                    if (NativeBridge.isRustCoreLoaded) TelegramOnlineGreen else TelegramIosOrange
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = connectionStatus,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    EngineSpecLine("Engine Version", rustVersion)
                    EngineSpecLine("Rust JNI Core (.so)", if (NativeBridge.isRustCoreLoaded) "Loaded" else "Fallback")
                    EngineSpecLine("TDLib (libtdjson.so)", if (repository.tdClient.isNativeTdLibLinked) "Linked v1.8.65" else "Loaded=${NativeBridge.isTdLibLoaded}")
                    EngineSpecLine("Storage", "Rust SQLite (rusqlite) + Tokio")
                    EngineSpecLine("Target Platform", "Android 16 (API 36)")
                }
            }

            // Section 4: Log Out
            IosSettingsGroupCard {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLogoutDialog = true }
                        .padding(vertical = 14.dp)
                        .testTag("logout_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Log Out",
                        color = TelegramIosRed,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showApiDialog) {
        ApiConfigDialog(
            repository = repository,
            onDismiss = { showApiDialog = false }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy & Security", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Two-Step Verification (2FA): Protected via Rust SHA-256 SRP digest.")
                    Text("• Secret Chats: End-to-end encrypted via MTProto 2.0.")
                    Text("• Active Sessions: Android 16 (API 36) • RustGram")
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("Done") }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out?") },
            text = { Text("Are you sure you want to log out from RustGram? You will need to re-verify your phone number.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        repository.logOut()
                        onLogOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramIosRed)
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun IosSettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
        content = content
    )
}

@Composable
private fun IosSettingsIconBadge(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun IosSettingsRow(
    icon: ImageVector,
    badgeColor: Color,
    title: String,
    valueText: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IosSettingsIconBadge(icon = icon, color = badgeColor)
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (valueText != null) {
            Text(
                text = valueText,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun IosSettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 56.dp)
    )
}

@Composable
private fun EngineSpecLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
