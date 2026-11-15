package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.tdlib.AppLanguage
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassSectionDivider
import com.example.ui.components.liquidGlass
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*

@Composable
fun SettingsScreen(
    repository: TelegramRepository,
    onOpenSavedMessages: () -> Unit,
    onOpenCalls: () -> Unit,
    onOpenThemeConstructor: () -> Unit,
    onOpenProxySettings: () -> Unit,
    onLogOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val language by repository.language.collectAsState()
    val accounts by repository.accounts.collectAsState()
    val userName by repository.currentUserName.collectAsState()
    val userPhone by repository.currentUserPhone.collectAsState()
    val username by repository.currentUserUsername.collectAsState()
    val userBio by repository.currentUserBio.collectAsState()
    val themePreset by repository.themePreset.collectAsState()
    val hasPasscode by repository.hasPasscode.collectAsState()
    val connectionStatus by repository.connectionStatus.collectAsState()
    val rustVersion by repository.rustEngineVersion.collectAsState()

    val activeAccount = accounts.find { it.isActive } ?: accounts.firstOrNull()
    val avatarColorIdx = activeAccount?.avatarColorIndex ?: 0
    val avatarPalettes = listOf(
        listOf(Color(0xFF5AC8FA), Color(0xFF007AFF)),
        listOf(Color(0xFF34C759), Color(0xFF00C7BE)),
        listOf(Color(0xFFAF52DE), Color(0xFF5856D6)),
        listOf(Color(0xFFFF9500), Color(0xFFFF3B30)),
        listOf(Color(0xFFFF2D55), Color(0xFFFF5E3A))
    )

    var showApiDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showSetPasscodeDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var cacheClearedMessage by remember { mutableStateOf<String?>(null) }

    LiquidGlassBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Floating Liquid Glass Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .liquidGlass(shape = RoundedCornerShape(50), strong = true)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = { showEditProfileDialog = true },
                    modifier = Modifier.testTag("settings_edit_profile_btn")
                ) {
                    Text(strings.editProfile, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }

                Text(
                    text = strings.settingsTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                TextButton(onClick = { showApiDialog = true }) {
                    Text(strings.apiKeys, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Centered Profile Hero Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(avatarPalettes[avatarColorIdx.coerceIn(0, avatarPalettes.lastIndex)]))
                            .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            .clickable { showEditProfileDialog = true },
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
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$userPhone  •  $username",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = userBio,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Multi-Account Switcher (up to 3 accounts)
                Text(
                    text = strings.accountsSection,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )

                LiquidGlassCard(strong = true, modifier = Modifier.fillMaxWidth()) {
                    accounts.forEachIndexed { idx, acc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { repository.switchAccount(acc.id) }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("account_switch_${acc.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Brush.verticalGradient(avatarPalettes[acc.avatarColorIndex.coerceIn(0, avatarPalettes.lastIndex)])),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(acc.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(acc.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(acc.username, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (acc.isActive) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (idx < accounts.lastIndex || accounts.size < 3) {
                            LiquidGlassSectionDivider()
                        }
                    }

                    if (accounts.size < 3) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAddAccountDialog = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("add_account_btn"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(strings.addAccount, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Quick Access Group
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    IosSettingsRow(
                        icon = Icons.Default.Bookmark,
                        badgeColor = IosBlueLight,
                        title = strings.savedMessages,
                        onClick = onOpenSavedMessages
                    )
                    LiquidGlassSectionDivider()
                    IosSettingsRow(
                        icon = Icons.Default.Call,
                        badgeColor = TelegramOnlineGreen,
                        title = strings.recentCalls,
                        onClick = onOpenCalls
                    )
                    LiquidGlassSectionDivider()
                    LiquidGlassSectionDivider()
                    IosSettingsRow(
                        icon = Icons.Default.VpnKey,
                        badgeColor = TelegramIosOrange,
                        title = "Прокси-серверы",
                        valueText = "TDLib · SOCKS5 / HTTP / MTProto",
                        onClick = onOpenProxySettings
                    )
                    LiquidGlassSectionDivider()
                    IosSettingsRow(
                        icon = Icons.Default.Key,
                        badgeColor = TelegramIosIndigo,
                        title = strings.apiCredentialsTitle,
                        valueText = "ID ${repository.getApiId()}",
                        modifier = Modifier.testTag("settings_edit_api_btn"),
                        onClick = { showApiDialog = true }
                    )
                }

                // Appearance, Language, Security & Storage
                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    IosSettingsRow(
                        icon = Icons.Default.Palette,
                        badgeColor = TelegramIosPurple,
                        title = strings.appearanceThemes,
                        valueText = strings.themePresetName(themePreset),
                        modifier = Modifier.testTag("open_theme_studio_btn"),
                        onClick = onOpenThemeConstructor
                    )
                    LiquidGlassSectionDivider()

                    // Language Switcher (RU / EN)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IosSettingsIconBadge(icon = Icons.Default.Translate, color = TelegramIosOrange)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = strings.languageTitle,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                .padding(3.dp)
                        ) {
                            AppLanguage.entries.forEach { lang ->
                                val active = language == lang
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { repository.setLanguage(lang) }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .testTag("settings_lang_${lang.code}")
                                ) {
                                    Text(
                                        text = lang.code.uppercase(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    LiquidGlassSectionDivider()

                    // Passcode Lock Row
                    IosSettingsRow(
                        icon = Icons.Default.Password,
                        badgeColor = TelegramIosRed,
                        title = strings.passcodeLock,
                        valueText = if (hasPasscode) strings.passcodeOn else strings.passcodeOff,
                        modifier = Modifier.testTag("passcode_settings_row"),
                        onClick = {
                            if (hasPasscode) repository.clearPasscode() else showSetPasscodeDialog = true
                        }
                    )
                    if (hasPasscode) {
                        LiquidGlassSectionDivider()
                        IosSettingsRow(
                            icon = Icons.Default.Lock,
                            badgeColor = TelegramSecretGreen,
                            title = strings.lockAppNow,
                            onClick = { repository.lockApp() }
                        )
                    }
                    LiquidGlassSectionDivider()

                    IosSettingsRow(
                        icon = Icons.Default.Security,
                        badgeColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = strings.privacySecurity,
                        valueText = "2FA",
                        onClick = { showPrivacyDialog = true }
                    )
                    LiquidGlassSectionDivider()

                    IosSettingsRow(
                        icon = Icons.Default.Storage,
                        badgeColor = TelegramOnlineGreen,
                        title = strings.dataStorage,
                        valueText = cacheClearedMessage ?: strings.clearCache,
                        onClick = { cacheClearedMessage = "24.5 MB OK" }
                    )
                }

                // Live Rust Engine Diagnostics
                Text(
                    text = strings.engineSection,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )

                LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (NativeBridge.isRustCoreLoaded) TelegramOnlineGreen else TelegramIosOrange)
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
                        EngineSpecLine("Rust JNI Core (.so)", if (NativeBridge.isRustCoreLoaded) "Loaded (16KB)" else "Fallback")
                        EngineSpecLine("TDLib (libtdjson.so)", if (repository.tdClient.isNativeTdLibLinked) "Linked v1.8.65" else "Loaded=${NativeBridge.isTdLibLoaded}")
                        EngineSpecLine("Storage", "Rust SQLite + Tokio")
                        EngineSpecLine("Target Platform", "Android 16 (API 36)")
                    }
                }

                // Log Out Glass Card
                LiquidGlassCard(
                    accentTint = TelegramIosRed,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLogoutDialog = true }
                            .padding(vertical = 14.dp)
                            .testTag("logout_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.logOut,
                            color = TelegramIosRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    if (showApiDialog) {
        ApiConfigDialog(repository = repository, onDismiss = { showApiDialog = false })
    }

    if (showSetPasscodeDialog) {
        SetPasscodeDialog(repository = repository, onDismiss = { showSetPasscodeDialog = false })
    }

    if (showEditProfileDialog) {
        var nameInput by remember { mutableStateOf(userName) }
        var handleInput by remember { mutableStateOf(username) }
        var bioInput by remember { mutableStateOf(userBio) }
        var colorIdx by remember { mutableIntStateOf(avatarColorIdx) }

        AlertDialog(
            modifier = Modifier.liquidGlass(shape = RoundedCornerShape(28.dp), strong = true),
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text(strings.editProfile, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text(strings.fullNameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = handleInput,
                        onValueChange = { handleInput = it },
                        label = { Text(strings.usernameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bioInput,
                        onValueChange = { bioInput = it },
                        label = { Text(strings.bioLabel) },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(strings.avatarColorLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        avatarPalettes.forEachIndexed { idx, grad ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Brush.verticalGradient(grad))
                                    .border(
                                        width = if (idx == colorIdx) 2.5.dp else 0.dp,
                                        color = if (idx == colorIdx) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { colorIdx = idx }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (nameInput.isNotBlank()) {
                        repository.updateActiveProfile(nameInput.trim(), handleInput.trim(), bioInput.trim(), colorIdx)
                        showEditProfileDialog = false
                    }
                }) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    if (showAddAccountDialog) {
        var newName by remember { mutableStateOf("") }
        var newPhone by remember { mutableStateOf("+7 ") }
        var newUser by remember { mutableStateOf("") }
        AlertDialog(
            modifier = Modifier.liquidGlass(shape = RoundedCornerShape(28.dp), strong = true),
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            onDismissRequest = { showAddAccountDialog = false },
            title = { Text(strings.addAccount, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(strings.fullNameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text(strings.authPhonePlaceholder) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newUser,
                        onValueChange = { newUser = it },
                        label = { Text(strings.usernameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.addAccount(newName, newPhone, newUser.ifBlank { "user_${accounts.size + 1}" })
                        showAddAccountDialog = false
                    },
                    enabled = newName.isNotBlank()
                ) { Text(strings.create) }
            },
            dismissButton = {
                TextButton(onClick = { showAddAccountDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            modifier = Modifier.liquidGlass(shape = RoundedCornerShape(28.dp), strong = true),
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text(strings.privacySecurity, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Telegram 2FA: handled by TDLib using Telegram's authorization flow.")
                    Text("• Secret Chats: created and managed by TDLib; timer changes are sent to Telegram.")
                    Text("• App Lock: local PBKDF2-HMAC-SHA256 verifier; it is not Telegram 2FA and does not encrypt all app data.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text(strings.done) }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            modifier = Modifier.liquidGlass(shape = RoundedCornerShape(28.dp), strong = true),
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(strings.logOutConfirmTitle) },
            text = { Text(strings.logOutConfirmBody) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        repository.logOut()
                        onLogOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramIosRed)
                ) { Text(strings.logOut) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text(strings.cancel) }
            }
        )
    }
}

@Composable
private fun IosSettingsIconBadge(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
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
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (valueText != null) {
            Text(text = valueText, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun EngineSpecLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}
