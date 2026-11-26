package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CherryRed

/**
 * Adds vivid D-Pad / TV remote focus scale and border ring feedback so focused items
 * are immediately visible from across the room on Android TV screens.
 */
fun Modifier.tvFocusHighlight(
    shape: Shape,
    focusBorderColor: Color = CherryRed,
    borderWidth: Dp = 3.5.dp,
    focusedScale: Float = 1.05f
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusedScale else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tvFocusScale"
    )

    this
        .scale(scale)
        .onFocusChanged { state ->
            isFocused = state.isFocused || state.hasFocus
        }
        .then(
            if (isFocused) {
                Modifier.border(width = borderWidth, color = focusBorderColor, shape = shape)
            } else {
                Modifier
            }
        )
}
