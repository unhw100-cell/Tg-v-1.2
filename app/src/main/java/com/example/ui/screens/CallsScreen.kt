package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repository.TelegramRepository
import com.example.tdlib.CallHistoryItem
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.liquidGlass
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.TelegramIosRed
import com.example.ui.theme.TelegramOnlineGreen
import kotlinx.coroutines.delay

@Composable
fun CallsScreen(
    repository: TelegramRepository,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val calls by repository.calls.collectAsState()
    val contacts by repository.contacts.collectAsState()

    var showMissedOnly by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    val hiddenCallIds = remember { mutableStateListOf<Long>() }
    var activeCall by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var showNewCallPicker by remember { mutableStateOf(false) }

    val filteredCalls = remember(calls, showMissedOnly, hiddenCallIds.size) {
        calls.filter { !hiddenCallIds.contains(it.id) }
            .filter { if (showMissedOnly) it.isMissed else true }
    }

    LiquidGlassBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Floating Liquid Glass Top Header with All | Missed Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .liquidGlass(shape = RoundedCornerShape(50), strong = true)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = { isEditMode = !isEditMode },
                    modifier = Modifier.testTag("calls_edit_btn")
                ) {
                    Text(
                        text = if (isEditMode) strings.done else strings.edit,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier
                        .width(176.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        .padding(3.dp)
                ) {
                    listOf(strings.callsAll to false, strings.callsMissed to true).forEach { (label, missedFlag) ->
                        val selected = showMissedOnly == missedFlag
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
                                )
                                .clickable { showMissedOnly = missedFlag }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { showNewCallPicker = true },
                    modifier = Modifier.testTag("new_call_btn")
                ) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = strings.startEncryptedCall,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (filteredCalls.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (showMissedOnly) strings.noMissedCalls else strings.noRecentCalls,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredCalls, key = { it.id }) { call ->
                        LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                            IosCallRow(
                                call = call,
                                isEditMode = isEditMode,
                                outgoingLabel = strings.outgoing,
                                incomingLabel = strings.incoming,
                                onDelete = { hiddenCallIds.add(call.id) },
                                onClick = { activeCall = call.callerName to call.isVideo }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewCallPicker) {
        AlertDialog(
            onDismissRequest = { showNewCallPicker = false },
            title = { Text(strings.startEncryptedCall, fontWeight = FontWeight.SemiBold) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(contacts) { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showNewCallPicker = false
                                    activeCall = contact.name to false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(contact.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(contact.name, modifier = Modifier.weight(1f), fontSize = 15.sp)
                            IconButton(onClick = {
                                showNewCallPicker = false
                                activeCall = contact.name to false
                            }) {
                                Icon(Icons.Default.Call, contentDescription = strings.callAction, tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = {
                                showNewCallPicker = false
                                activeCall = contact.name to true
                            }) {
                                Icon(Icons.Default.Videocam, contentDescription = strings.videoAction, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNewCallPicker = false }) { Text(strings.cancel) }
            }
        )
    }

    activeCall?.let { (callerName, isVideo) ->
        ActiveCallModal(
            callerName = callerName,
            initialVideo = isVideo,
            onEndCall = { videoUsed ->
                repository.logCall(callerName, videoUsed)
                activeCall = null
            }
        )
    }
}

@Composable
private fun IosCallRow(
    call: CallHistoryItem,
    isEditMode: Boolean,
    outgoingLabel: String,
    incomingLabel: String,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isEditMode) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.RemoveCircle, contentDescription = "Delete", tint = TelegramIosRed)
            }
            Spacer(Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF64D2FF), Color(0xFF007AFF))))
                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = call.callerName.take(1).uppercase(),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 17.sp
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = call.callerName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = if (call.isMissed) TelegramIosRed else MaterialTheme.colorScheme.onBackground
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (call.isMissed) Icons.AutoMirrored.Filled.CallMissed
                    else if (call.isOutgoing) Icons.AutoMirrored.Filled.CallMade
                    else Icons.AutoMirrored.Filled.CallReceived,
                    contentDescription = null,
                    tint = if (call.isMissed) TelegramIosRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (call.isOutgoing) "$outgoingLabel (${call.durationText})" else "$incomingLabel (${call.durationText})",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = call.timestamp.substringAfter(", ").ifBlank { call.timestamp },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun ActiveCallModal(
    callerName: String,
    initialVideo: Boolean,
    onEndCall: (Boolean) -> Unit
) {
    val strings = LocalAppStrings.current
    var seconds by remember { mutableIntStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(false) }
    var isVideo by remember { mutableStateOf(initialVideo) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            seconds++
        }
    }

    val mins = seconds / 60
    val secs = seconds % 60
    val timerText = "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"

    AlertDialog(
        onDismissRequest = { onEndCall(isVideo) },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🔐 🦀 🚀 ⚡",
                    fontSize = 20.sp,
                    letterSpacing = 4.sp
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Color(0xFF34AADC), Color(0xFF007AFF)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = callerName.take(1).uppercase(),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(callerName, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (seconds < 2) strings.ringing else "${strings.encryptedCallPrefix} $timerText",
                    fontSize = 14.sp,
                    color = TelegramOnlineGreen
                )
            }
        },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(onClick = { isMuted = !isMuted }) {
                        Icon(
                            if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = strings.muteMic
                        )
                    }
                    Text(strings.muteMic, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(onClick = { isSpeaker = !isSpeaker }) {
                        Icon(
                            if (isSpeaker) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = strings.speaker
                        )
                    }
                    Text(strings.speaker, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(onClick = { isVideo = !isVideo }) {
                        Icon(
                            if (isVideo) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = strings.video
                        )
                    }
                    Text(strings.video, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onEndCall(isVideo) },
                colors = ButtonDefaults.buttonColors(containerColor = TelegramIosRed),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("end_call_btn")
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.endCall, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
