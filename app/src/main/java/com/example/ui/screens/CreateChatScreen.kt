package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
import com.example.ui.theme.TelegramSecretGreen

@Composable
fun CreateChatScreen(
    repository: TelegramRepository,
    onBack: () -> Unit,
    onChatCreated: (TelegramChatModel) -> Unit
) {
    val strings = LocalAppStrings.current
    val contacts by repository.contacts.collectAsState()
    var mode by remember { mutableStateOf("menu") } // "menu", "group", "channel", "secret"
    var titleInput by remember { mutableStateOf("") }
    var descriptionInput by remember { mutableStateOf("") }
    val selectedMemberIds = remember { mutableStateListOf<Long>() }

    LiquidGlassBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Floating Liquid Glass Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .liquidGlass(shape = RoundedCornerShape(50), strong = true)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { if (mode == "menu") onBack() else mode = "menu" },
                    modifier = Modifier.testTag("create_chat_cancel_btn")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.back,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        if (mode == "menu") strings.cancel else strings.back,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp
                    )
                }

                Text(
                    text = when (mode) {
                        "group" -> strings.newGroupTitle
                        "channel" -> strings.newChannelTitle
                        "secret" -> strings.newSecretChatOption
                        else -> strings.newMessageTitle
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                if (mode == "group" || mode == "channel") {
                    TextButton(
                        onClick = {
                            if (titleInput.isNotBlank()) {
                                val chat = repository.createGroupOrChannel(
                                    title = titleInput.trim(),
                                    description = descriptionInput.trim(),
                                    isChannel = (mode == "channel"),
                                    memberCount = selectedMemberIds.size + 1,
                                    memberIds = selectedMemberIds.toList()
                                )
                                if (chat != null) onChatCreated(chat) else onBack()
                            }
                        },
                        enabled = titleInput.isNotBlank(),
                        modifier = Modifier.testTag("create_confirm_btn")
                    ) {
                        Text(strings.create, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                } else {
                    Spacer(Modifier.width(56.dp))
                }
            }

            if (mode == "menu" || mode == "secret") {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (mode == "menu") {
                        item {
                            LiquidGlassCard(strong = true, modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { mode = "group" }
                                            .padding(horizontal = 16.dp, vertical = 14.dp)
                                            .testTag("new_group_option"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(14.dp))
                                        Text(strings.newGroupTitle, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                    }
                                    LiquidGlassSectionDivider()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { mode = "secret" }
                                            .padding(horizontal = 16.dp, vertical = 14.dp)
                                            .testTag("new_secret_chat_option"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = TelegramSecretGreen)
                                        Spacer(Modifier.width(14.dp))
                                        Text(strings.newSecretChatOption, fontSize = 16.sp, color = TelegramSecretGreen, fontWeight = FontWeight.Medium)
                                    }
                                    LiquidGlassSectionDivider()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { mode = "channel" }
                                            .padding(horizontal = 16.dp, vertical = 14.dp)
                                            .testTag("new_channel_option"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(14.dp))
                                        Text(strings.newChannelTitle, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = if (mode == "secret") strings.secretChatBanner else strings.tabContacts.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (mode == "secret") TelegramSecretGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                        )
                    }

                    item {
                        LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                contacts.forEachIndexed { idx, contact ->
                                    ContactPickerRow(contact = contact, isSelectable = false, isSelected = false) {
                                        val chat = if (mode == "secret") {
                                            repository.createSecretChat(contact)
                                        } else {
                                            repository.openOrCreateContactChat(contact)
                                        }
                                        if (chat != null) onChatCreated(chat) else onBack()
                                    }
                                    if (idx < contacts.lastIndex) {
                                        LiquidGlassSectionDivider()
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        LiquidGlassCard(strong = true, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(62.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (mode == "channel") Icons.Default.Campaign else Icons.Default.CameraAlt,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    OutlinedTextField(
                                        value = titleInput,
                                        onValueChange = { titleInput = it },
                                        label = { Text(if (mode == "channel") strings.channelNameHint else strings.groupNameHint) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("group_channel_title_input")
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = descriptionInput,
                                    onValueChange = { descriptionInput = it },
                                    label = { Text(strings.descriptionOptional) },
                                    maxLines = 3,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("group_channel_desc_input")
                                )
                            }
                        }
                    }

                    if (mode == "group") {
                        item {
                            Text(
                                text = "${strings.addMembersHeader} (${selectedMemberIds.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        item {
                            LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    contacts.forEachIndexed { idx, contact ->
                                        val selected = selectedMemberIds.contains(contact.id)
                                        ContactPickerRow(
                                            contact = contact,
                                            isSelectable = true,
                                            isSelected = selected
                                        ) {
                                            if (selected) selectedMemberIds.remove(contact.id)
                                            else selectedMemberIds.add(contact.id)
                                        }
                                        if (idx < contacts.lastIndex) {
                                            LiquidGlassSectionDivider()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactPickerRow(
    contact: ContactModel,
    isSelectable: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectable) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() }
            )
            Spacer(Modifier.width(6.dp))
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.name.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(contact.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(contact.lastSeen, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
