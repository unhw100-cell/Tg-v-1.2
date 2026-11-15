package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.components.liquidGlass

@Composable
fun PollCreatorDialog(
    onCreatePoll: (
        question: String,
        options: List<String>,
        isQuiz: Boolean,
        correctIndex: Int?,
        allowMultiple: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var isQuiz by remember { mutableStateOf(false) }
    var allowMultiple by remember { mutableStateOf(false) }
    var correctIndex by remember { mutableIntStateOf(0) }

    val validOptions = options.map { it.trim() }.filter { it.isNotEmpty() }
    val canCreate = question.isNotBlank() && validOptions.size >= 2

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.liquidGlass(shape = RoundedCornerShape(28.dp), strong = true),
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Poll, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(strings.createPollTitle, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text(strings.pollQuestionHint) },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("poll_question_input")
                )

                options.forEachIndexed { idx, value ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isQuiz) {
                            RadioButton(
                                selected = correctIndex == idx,
                                onClick = { correctIndex = idx }
                            )
                        }
                        OutlinedTextField(
                            value = value,
                            onValueChange = { options[idx] = it },
                            label = { Text("${strings.pollOptionHint} ${idx + 1}") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("poll_option_input_$idx")
                        )
                    }
                }

                if (options.size < 6) {
                    TextButton(
                        onClick = { options.add("") },
                        modifier = Modifier.testTag("poll_add_option_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.addOption)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(strings.quizMode, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Switch(
                        checked = isQuiz,
                        onCheckedChange = {
                            isQuiz = it
                            if (it) allowMultiple = false
                        }
                    )
                }

                if (!isQuiz) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(strings.allowMultipleAnswers, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = allowMultiple,
                            onCheckedChange = { allowMultiple = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (canCreate) {
                        onCreatePoll(
                            question.trim(),
                            validOptions,
                            isQuiz,
                            if (isQuiz) correctIndex.coerceIn(0, validOptions.lastIndex) else null,
                            allowMultiple
                        )
                    }
                },
                enabled = canCreate,
                modifier = Modifier.testTag("poll_create_confirm_btn")
            ) {
                Text(strings.create)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
