package com.example.ui.components

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.CherryRed
import com.example.ui.theme.CherryRedContainer
import com.example.ui.theme.DeepInk
import com.example.ui.theme.MintGreen
import com.example.ui.theme.MutedInk
import com.example.ui.theme.SoftSurfaceVariant
import kotlin.random.Random

@Composable
fun ParentGateDialog(
    title: String = "Ebeveyn Doğrulaması",
    subtitle: String? = null,
    configuredPin: String,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    val isPinMode = configuredPin.length == 4
    var factorA by remember { mutableIntStateOf(Random.nextInt(4, 10)) }
    var factorB by remember { mutableIntStateOf(Random.nextInt(4, 10)) }
    val expectedAnswer = if (isPinMode) configuredPin else (factorA * factorB).toString()

    var input by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val firstDigitFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { firstDigitFocusRequester.requestFocus() }
    }

    fun submitAnswer(currentInput: String) {
        if (currentInput == expectedAnswer) {
            onUnlocked()
        } else {
            errorMessage = if (isPinMode) {
                "PIN hatalı, tekrar deneyin."
            } else {
                "Yanlış cevap, yeni soru hazırlandı."
            }
            input = ""
            if (!isPinMode) {
                factorA = Random.nextInt(4, 10)
                factorB = Random.nextInt(4, 10)
            }
        }
    }

    fun appendDigit(digit: String) {
        errorMessage = null
        if (input.length < 4) {
            val updated = input + digit
            input = updated
            if (isPinMode && updated.length == 4) {
                submitAnswer(updated)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        val native = event.nativeKeyEvent
                        when (native.keyCode) {
                            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                                appendDigit((native.keyCode - KeyEvent.KEYCODE_0).toString())
                                true
                            }
                            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> {
                                appendDigit((native.keyCode - KeyEvent.KEYCODE_NUMPAD_0).toString())
                                true
                            }
                            KeyEvent.KEYCODE_DEL -> {
                                if (input.isNotEmpty()) input = input.dropLast(1)
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .testTag("parent_gate_dialog"),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(CherryRedContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = CherryRed
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                color = DeepInk
                            )
                            Text(
                                text = subtitle ?: if (isPinMode) {
                                    "4 haneli Ebeveyn PIN'ini girin"
                                } else {
                                    "Devam etmek için işlemi çözün"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedInk
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .tvFocusHighlight(CircleShape, focusedScale = 1.1f)
                            .testTag("parent_gate_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Kapat",
                            tint = MutedInk
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (!isPinMode) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SoftSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "$factorA × $factorB = ?",
                            style = MaterialTheme.typography.displaySmall,
                            color = DeepInk,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 14.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (isPinMode) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 10.dp)
                    ) {
                        repeat(4) { index ->
                            val filled = index < input.length
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (filled) CherryRed else SoftSurfaceVariant)
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CherryRedContainer.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (input.isEmpty()) "Cevap..." else input,
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (input.isEmpty()) MutedInk else DeepInk,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = CherryRed,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                val keypadRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    keypadRows.forEach { rowDigits ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            rowDigits.forEach { digit ->
                                val mod = if (digit == "1") {
                                    Modifier.focusRequester(firstDigitFocusRequester)
                                } else {
                                    Modifier
                                }
                                KeypadCircleButton(
                                    label = digit,
                                    modifier = mod,
                                    onClick = { appendDigit(digit) }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .tvFocusHighlight(CircleShape, focusedScale = 1.1f)
                                .clip(CircleShape)
                                .background(SoftSurfaceVariant)
                                .clickable {
                                    if (input.isNotEmpty()) {
                                        input = input.dropLast(1)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                contentDescription = "Sil",
                                tint = DeepInk
                            )
                        }

                        KeypadCircleButton(
                            label = "0",
                            onClick = { appendDigit("0") }
                        )

                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .tvFocusHighlight(CircleShape, focusBorderColor = DeepInk, focusedScale = 1.1f)
                                .clip(CircleShape)
                                .background(MintGreen)
                                .clickable(enabled = input.isNotEmpty()) {
                                    submitAnswer(input)
                                }
                                .testTag("parent_gate_submit_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Onayla",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.tvFocusHighlight(RoundedCornerShape(16.dp))
                ) {
                    Text("Vazgeç", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun KeypadCircleButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .size(60.dp)
            .tvFocusHighlight(CircleShape, focusedScale = 1.1f)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = SoftSurfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall,
                color = DeepInk
            )
        }
    }
}
