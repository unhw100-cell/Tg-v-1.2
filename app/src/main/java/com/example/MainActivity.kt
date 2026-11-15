package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.tdlib.MainTab
import com.example.tdlib.TdAuthState
import com.example.tdlib.TelegramChatModel
import com.example.tdlib.TelegramThemePreset
import com.example.ui.components.liquidGlass
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.stringsForLanguage
import com.example.ui.screens.*
import com.example.ui.theme.RustGramTheme

sealed class Screen {
    object Auth : Screen()
    data class MainTabs(val activeTab: MainTab = MainTab.CHATS) : Screen()
    data class ChatDetail(val chat: TelegramChatModel) : Screen()
    data class Profile(val chat: TelegramChatModel) : Screen()
    object CreateChat : Screen()
    object ThemeConstructor : Screen()
    object ProxySettings : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val repository = RustGramApp.instance.repository
            val language by repository.language.collectAsState()
            val themePreset by repository.themePreset.collectAsState()
            val accentIndex by repository.accentIndex.collectAsState()
            val authState by repository.authState.collectAsState()
            val chats by repository.chats.collectAsState()
            val isAppLocked by repository.isAppLocked.collectAsState()

            val isDark = themePreset == TelegramThemePreset.TINTED_DARK || themePreset == TelegramThemePreset.OLED_NIGHT
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as? Activity)?.window ?: return@SideEffect
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !isDark
                    controller.isAppearanceLightNavigationBars = !isDark
                }
            }

            var currentScreen by remember {
                mutableStateOf<Screen>(
                    if (authState is TdAuthState.Ready) Screen.MainTabs(MainTab.CHATS) else Screen.Auth
                )
            }
            var activeCallOverlay by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

            LaunchedEffect(authState) {
                if (authState is TdAuthState.Ready && currentScreen is Screen.Auth) {
                    currentScreen = Screen.MainTabs(MainTab.CHATS)
                } else if (authState !is TdAuthState.Ready && currentScreen !is Screen.Auth) {
                    currentScreen = Screen.Auth
                }
            }

            CompositionLocalProvider(LocalAppStrings provides stringsForLanguage(language)) {
                val strings = LocalAppStrings.current

                RustGramTheme(themePreset = themePreset, accentIndex = accentIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        when (val screen = currentScreen) {
                            is Screen.Auth -> {
                                AuthScreen(
                                    repository = repository,
                                    onAuthSuccess = { currentScreen = Screen.MainTabs(MainTab.CHATS) }
                                )
                            }

                            is Screen.MainTabs -> {
                                if (screen.activeTab != MainTab.CHATS) {
                                    BackHandler {
                                        currentScreen = Screen.MainTabs(MainTab.CHATS)
                                    }
                                }
                                val totalUnread = remember(chats) {
                                    chats.filter { !it.isArchived }.sumOf { it.unreadCount }
                                }

                                Box(modifier = Modifier.fillMaxSize()) {
                                    when (screen.activeTab) {
                                        MainTab.CONTACTS -> ContactsScreen(
                                            repository = repository,
                                            onContactClick = { chat ->
                                                repository.setActiveChat(chat)
                                                currentScreen = Screen.ChatDetail(chat)
                                            },
                                            onCreateNewChat = { currentScreen = Screen.CreateChat }
                                        )
                                        MainTab.CALLS -> CallsScreen(repository = repository)
                                        MainTab.CHATS -> ChatListScreen(
                                            repository = repository,
                                            onChatClick = { chat ->
                                                repository.setActiveChat(chat)
                                                currentScreen = Screen.ChatDetail(chat)
                                            },
                                            onCreateNewChat = { currentScreen = Screen.CreateChat }
                                        )
                                        MainTab.SETTINGS -> SettingsScreen(
                                            repository = repository,
                                            onOpenSavedMessages = {
                                                val savedChat = chats.find { it.title == "Saved Messages" }
                                                    ?: TelegramChatModel(
                                                        id = 101L,
                                                        title = "Saved Messages",
                                                        type = "private",
                                                        lastMessageText = "Personal cloud storage",
                                                        lastMessageTime = "Now"
                                                    )
                                                repository.setActiveChat(savedChat)
                                                currentScreen = Screen.ChatDetail(savedChat)
                                            },
                                            onOpenCalls = {
                                                currentScreen = Screen.MainTabs(MainTab.CALLS)
                                            },
                                            onOpenThemeConstructor = {
                                                currentScreen = Screen.ThemeConstructor
                                            },
                                            onOpenProxySettings = {
                                                currentScreen = Screen.ProxySettings
                                            },
                                            onLogOut = {
                                                currentScreen = Screen.Auth
                                            }
                                        )
                                    }

                                    // Floating iPhone Liquid Glass Bottom Capsule Tab Bar
                                    LiquidGlassBottomTabBar(
                                        activeTab = screen.activeTab,
                                        unreadCount = totalUnread,
                                        contactsLabel = strings.tabContacts,
                                        callsLabel = strings.tabCalls,
                                        chatsLabel = strings.tabChats,
                                        settingsLabel = strings.tabSettings,
                                        onSelectTab = { tab ->
                                            currentScreen = Screen.MainTabs(tab)
                                        },
                                        modifier = Modifier.align(Alignment.BottomCenter)
                                    )
                                }
                            }

                            is Screen.ChatDetail -> {
                                BackHandler {
                                    repository.clearActiveChat()
                                    currentScreen = Screen.MainTabs(MainTab.CHATS)
                                }
                                ChatDetailScreen(
                                    repository = repository,
                                    chat = screen.chat,
                                    onBack = {
                                        repository.clearActiveChat()
                                        currentScreen = Screen.MainTabs(MainTab.CHATS)
                                    },
                                    onOpenProfile = {
                                        currentScreen = Screen.Profile(screen.chat)
                                    }
                                )
                            }

                            is Screen.Profile -> {
                                BackHandler {
                                    currentScreen = Screen.ChatDetail(screen.chat)
                                }
                                ProfileScreen(
                                    repository = repository,
                                    chat = screen.chat,
                                    onBack = { currentScreen = Screen.ChatDetail(screen.chat) },
                                    onStartCall = { isVideo ->
                                        activeCallOverlay = screen.chat.title to isVideo
                                    }
                                )
                            }

                            is Screen.CreateChat -> {
                                BackHandler {
                                    currentScreen = Screen.MainTabs(MainTab.CHATS)
                                }
                                CreateChatScreen(
                                    repository = repository,
                                    onBack = { currentScreen = Screen.MainTabs(MainTab.CHATS) },
                                    onChatCreated = { newChat ->
                                        repository.setActiveChat(newChat)
                                        currentScreen = Screen.ChatDetail(newChat)
                                    }
                                )
                            }

                            is Screen.ThemeConstructor -> {
                                BackHandler {
                                    currentScreen = Screen.MainTabs(MainTab.SETTINGS)
                                }
                                ThemeConstructorScreen(
                                    repository = repository,
                                    onBack = { currentScreen = Screen.MainTabs(MainTab.SETTINGS) }
                                )
                            }

                            is Screen.ProxySettings -> {
                                BackHandler {
                                    currentScreen = Screen.MainTabs(MainTab.SETTINGS)
                                }
                                ProxySettingsScreen(
                                    tdClient = repository.tdClient,
                                    onBack = { currentScreen = Screen.MainTabs(MainTab.SETTINGS) }
                                )
                            }
                        }

                        activeCallOverlay?.let { (callerName, isVideo) ->
                            ActiveCallModal(
                                callerName = callerName,
                                initialVideo = isVideo,
                                onEndCall = { usedVideo ->
                                    repository.logCall(callerName, usedVideo)
                                    activeCallOverlay = null
                                }
                            )
                        }

                        if (isAppLocked) {
                            PasscodeLockOverlay(repository = repository)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidGlassBottomTabBar(
    activeTab: MainTab,
    unreadCount: Int,
    contactsLabel: String,
    callsLabel: String,
    chatsLabel: String,
    settingsLabel: String,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .height(62.dp)
                .liquidGlass(shape = RoundedCornerShape(50), strong = true, elevation = 14.dp)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidTabItem(
                label = contactsLabel,
                selected = activeTab == MainTab.CONTACTS,
                selectedIcon = Icons.Filled.AccountCircle,
                unselectedIcon = Icons.Outlined.AccountCircle,
                badgeCount = 0,
                testTag = "tab_contacts",
                onClick = { onSelectTab(MainTab.CONTACTS) }
            )
            LiquidTabItem(
                label = callsLabel,
                selected = activeTab == MainTab.CALLS,
                selectedIcon = Icons.Filled.Call,
                unselectedIcon = Icons.Outlined.Call,
                badgeCount = 0,
                testTag = "tab_calls",
                onClick = { onSelectTab(MainTab.CALLS) }
            )
            LiquidTabItem(
                label = chatsLabel,
                selected = activeTab == MainTab.CHATS,
                selectedIcon = Icons.Filled.ChatBubble,
                unselectedIcon = Icons.Outlined.ChatBubbleOutline,
                badgeCount = unreadCount,
                testTag = "tab_chats",
                onClick = { onSelectTab(MainTab.CHATS) }
            )
            LiquidTabItem(
                label = settingsLabel,
                selected = activeTab == MainTab.SETTINGS,
                selectedIcon = Icons.Filled.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                badgeCount = 0,
                testTag = "tab_settings",
                onClick = { onSelectTab(MainTab.SETTINGS) }
            )
        }
    }
}

@Composable
private fun RowScope.LiquidTabItem(
    label: String,
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    badgeCount: Int,
    testTag: String,
    onClick: () -> Unit
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else Color.Transparent
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(22.dp)
            )
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = 2.dp, y = (-3).dp)
                        .defaultMinSize(minWidth = 17.dp, minHeight = 17.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(1.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = tint,
            maxLines = 1
        )
    }
}
