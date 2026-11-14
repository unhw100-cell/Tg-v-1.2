package com.example

import android.content.Intent
import android.os.Build
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.service.TelegramSyncService
import com.example.tdlib.MainTab
import com.example.tdlib.TdAuthState
import com.example.tdlib.TelegramChatModel
import com.example.ui.screens.*
import com.example.ui.theme.IosDarkTabBar
import com.example.ui.theme.IosLightTabBar
import com.example.ui.theme.RustGramTheme

sealed class Screen {
    object Auth : Screen()
    data class MainTabs(val activeTab: MainTab = MainTab.CHATS) : Screen()
    data class ChatDetail(val chat: TelegramChatModel) : Screen()
    data class Profile(val chat: TelegramChatModel) : Screen()
    object CreateChat : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            val serviceIntent = Intent(this, TelegramSyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (_: Exception) {
        }

        setContent {
            val repository = RustGramApp.instance.repository
            val isDarkMode by repository.isDarkMode.collectAsState()
            val authState by repository.authState.collectAsState()
            val chats by repository.chats.collectAsState()

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

            RustGramTheme(darkTheme = isDarkMode) {
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
                                chats.sumOf { it.unreadCount }
                            }

                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f)) {
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
                                            onLogOut = {
                                                currentScreen = Screen.Auth
                                            }
                                        )
                                    }
                                }

                                IosBottomTabBar(
                                    activeTab = screen.activeTab,
                                    unreadCount = totalUnread,
                                    isDarkMode = isDarkMode,
                                    onSelectTab = { tab ->
                                        currentScreen = Screen.MainTabs(tab)
                                    }
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
            }
        }
    }
}

@Composable
private fun IosBottomTabBar(
    activeTab: MainTab,
    unreadCount: Int,
    isDarkMode: Boolean,
    onSelectTab: (MainTab) -> Unit
) {
    val barBg = if (isDarkMode) IosDarkTabBar else IosLightTabBar

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(barBg)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            thickness = 0.5.dp
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IosTabItem(
                label = "Contacts",
                selected = activeTab == MainTab.CONTACTS,
                selectedIcon = Icons.Filled.AccountCircle,
                unselectedIcon = Icons.Outlined.AccountCircle,
                badgeCount = 0,
                testTag = "tab_contacts",
                onClick = { onSelectTab(MainTab.CONTACTS) }
            )
            IosTabItem(
                label = "Calls",
                selected = activeTab == MainTab.CALLS,
                selectedIcon = Icons.Filled.Call,
                unselectedIcon = Icons.Outlined.Call,
                badgeCount = 0,
                testTag = "tab_calls",
                onClick = { onSelectTab(MainTab.CALLS) }
            )
            IosTabItem(
                label = "Chats",
                selected = activeTab == MainTab.CHATS,
                selectedIcon = Icons.Filled.ChatBubble,
                unselectedIcon = Icons.Outlined.ChatBubbleOutline,
                badgeCount = unreadCount,
                testTag = "tab_chats",
                onClick = { onSelectTab(MainTab.CHATS) }
            )
            IosTabItem(
                label = "Settings",
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
private fun RowScope.IosTabItem(
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
                    .size(24.dp)
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
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = tint
        )
    }
}
