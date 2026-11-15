package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.repository.TelegramRepository
import com.example.tdlib.StoryItem
import com.example.ui.components.liquidGlass
import com.example.ui.i18n.LocalAppStrings

@Composable
fun StoryViewerModal(
    story: StoryItem,
    onViewed: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var slideIndex by remember(story.id) { mutableIntStateOf(0) }
    val slide = story.slides.getOrElse(slideIndex) { story.slides.first() }

    LaunchedEffect(story.id) {
        onViewed(story.id)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(slide.startColorHex), Color(slide.endColorHex))
                    )
                )
        ) {
            // Left/Right tap zones to advance/rewind story slides
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (slideIndex > 0) slideIndex-- else onDismiss()
                        }
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (slideIndex < story.slides.lastIndex) slideIndex++ else onDismiss()
                        }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Top Progress Bars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    story.slides.forEachIndexed { idx, _ ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (idx <= slideIndex) Color.White else Color.White.copy(alpha = 0.32f)
                                )
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Floating Glass Author Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(shape = RoundedCornerShape(50), strong = false)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = story.authorName.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = story.authorName,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = slide.timestamp,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("story_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Glass Story Caption Pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                        .liquidGlass(shape = RoundedCornerShape(26.dp), strong = true)
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = slide.text,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 27.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AddStoryDialog(
    repository: TelegramRepository,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    var storyText by remember { mutableStateOf("") }

    AlertDialog(
        modifier = Modifier.liquidGlass(shape = RoundedCornerShape(28.dp), strong = true),
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        onDismissRequest = onDismiss,
        title = { Text(strings.addStoryPrompt, fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = storyText,
                onValueChange = { storyText = it },
                placeholder = { Text(strings.storyPlaceholder) },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_story_input")
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (storyText.isNotBlank()) {
                        repository.addOwnStory(storyText)
                        onDismiss()
                    }
                },
                enabled = storyText.isNotBlank(),
                modifier = Modifier.testTag("publish_story_btn")
            ) {
                Text(strings.publishStory)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
