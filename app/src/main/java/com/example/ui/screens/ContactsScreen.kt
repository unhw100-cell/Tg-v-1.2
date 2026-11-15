package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repository.TelegramRepository
import com.example.tdlib.ContactModel
import com.example.tdlib.TelegramChatModel
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassSectionDivider
import com.example.ui.components.liquidGlass
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.TelegramOnlineGreen

@Composable
fun ContactsScreen(
    repository: TelegramRepository,
    onContactClick: (TelegramChatModel) -> Unit,
    onCreateNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val contacts by repository.contacts.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var sortByLastSeen by remember { mutableStateOf(true) }
    var infoBannerText by remember { mutableStateOf<String?>(null) }

    val displayedContacts = remember(contacts, searchQuery, sortByLastSeen) {
        val filtered = contacts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.phone.contains(searchQuery, ignoreCase = true) ||
                it.username.contains(searchQuery, ignoreCase = true)
        }
        if (sortByLastSeen) {
            filtered.sortedByDescending { it.isOnline }
        } else {
            filtered.sortedBy { it.name }
        }
    }

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
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { sortByLastSeen = !sortByLastSeen },
                    modifier = Modifier.testTag("contacts_sort_btn")
                ) {
                    Text(
                        text = if (sortByLastSeen) strings.contactsSortStatus else strings.contactsSortName,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = strings.tabContacts,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                IconButton(
                    onClick = onCreateNewChat,
                    modifier = Modifier.testTag("contacts_add_btn")
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = strings.newMessageTitle,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Floating Glass Search Capsule
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .height(42.dp)
                    .liquidGlass(shape = RoundedCornerShape(50), strong = false, elevation = 3.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = strings.searchContactsHint,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("contacts_search_input")
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = strings.close,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                        IosContactQuickRow(
                            icon = Icons.Default.LocationOn,
                            label = strings.findPeopleNearby,
                            onClick = { infoBannerText = "Bluetooth & GPS discovery ready." }
                        )
                        LiquidGlassSectionDivider()
                        IosContactQuickRow(
                            icon = Icons.Default.PersonAdd,
                            label = strings.inviteFriends,
                            onClick = { infoBannerText = "https://t.me/rustgram_app" }
                        )
                    }
                }

                items(displayedContacts, key = { it.id }) { contact ->
                    LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                        IosContactRow(
                            contact = contact,
                            onlineLabel = strings.onlineStatus,
                            onClick = {
                                val chat = repository.openOrCreateContactChat(contact)
                                if (chat != null) onContactClick(chat)
                            }
                        )
                    }
                }
            }
        }
    }

    infoBannerText?.let { msg ->
        AlertDialog(
            modifier = Modifier.liquidGlass(shape = RoundedCornerShape(28.dp), strong = true),
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            onDismissRequest = { infoBannerText = null },
            title = { Text(strings.appName, fontWeight = FontWeight.SemiBold) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { infoBannerText = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun IosContactQuickRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(34.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun IosContactRow(
    contact: ContactModel,
    onlineLabel: String,
    onClick: () -> Unit
) {
    val gradient = remember(contact.id) {
        val palettes = listOf(
            listOf(Color(0xFF64D2FF), Color(0xFF007AFF)),
            listOf(Color(0xFF30D158), Color(0xFF00C7BE)),
            listOf(Color(0xFFBF5AF2), Color(0xFF5E5CE6)),
            listOf(Color(0xFFFFB340), Color(0xFFFF9500))
        )
        palettes[(kotlin.math.abs(contact.id) % palettes.size).toInt()]
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("contact_item_${contact.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(gradient))
                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.name.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (contact.isOnline) onlineLabel else contact.lastSeen,
                fontSize = 13.sp,
                color = if (contact.isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (contact.isOnline) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(TelegramOnlineGreen)
            )
        }
    }
}
