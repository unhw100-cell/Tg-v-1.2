package com.example.ui.screens

import androidx.compose.foundation.background
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
import com.example.ui.theme.TelegramOnlineGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    repository: TelegramRepository,
    onContactClick: (TelegramChatModel) -> Unit,
    onCreateNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Telegram iOS Centered Header
        CenterAlignedTopAppBar(
            navigationIcon = {
                TextButton(
                    onClick = { sortByLastSeen = !sortByLastSeen },
                    modifier = Modifier.testTag("contacts_sort_btn")
                ) {
                    Text(
                        text = if (sortByLastSeen) "Sort: Status" else "Sort: Name",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp
                    )
                }
            },
            title = {
                Text(
                    text = "Contacts",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            actions = {
                IconButton(
                    onClick = onCreateNewChat,
                    modifier = Modifier.testTag("contacts_add_btn")
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "New Contact or Group",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // iOS Rounded Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp
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
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            thickness = 0.5.dp
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                IosContactQuickRow(
                    icon = Icons.Default.LocationOn,
                    label = "Find People Nearby",
                    onClick = {
                        infoBannerText = "Nearby discovery requires active Bluetooth & Location permissions."
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(start = 60.dp)
                )
                IosContactQuickRow(
                    icon = Icons.Default.PersonAdd,
                    label = "Invite Friends",
                    onClick = {
                        infoBannerText = "Share link: https://t.me/rustgram_app"
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    thickness = 0.5.dp
                )
            }

            items(displayedContacts, key = { it.id }) { contact ->
                IosContactRow(
                    contact = contact,
                    onClick = {
                        val chat = repository.openOrCreateContactChat(contact)
                        onContactClick(chat)
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(start = 74.dp)
                )
            }
        }
    }

    infoBannerText?.let { msg ->
        AlertDialog(
            onDismissRequest = { infoBannerText = null },
            title = { Text("RustGram", fontWeight = FontWeight.SemiBold) },
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
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun IosContactRow(
    contact: ContactModel,
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
            .padding(horizontal = 16.dp, vertical = 9.dp)
            .testTag("contact_item_${contact.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(gradient)),
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
                text = if (contact.isOnline) "online" else contact.lastSeen,
                fontSize = 13.sp,
                color = if (contact.isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (contact.isOnline) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(TelegramOnlineGreen)
            )
        }
    }
}
