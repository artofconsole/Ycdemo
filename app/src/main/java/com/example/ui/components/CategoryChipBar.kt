package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.VideoCategory
import com.example.ui.theme.CherryRed
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.DeepInk
import com.example.ui.theme.SunshineYellow

@Composable
fun CategoryChipBar(
    selectedCategory: VideoCategory,
    onSelectCategory: (VideoCategory) -> Unit,
    modifier: Modifier = Modifier,
    categories: List<VideoCategory> = VideoCategory.entries
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(categories, key = { it.id }) { category ->
            val isSelected = category == selectedCategory
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.05f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "chipScale"
            )
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) category.accentColor else CrispWhite,
                label = "chipColor"
            )
            val contentColor = when {
                isSelected && category.accentColor == SunshineYellow -> DeepInk
                isSelected -> CrispWhite
                else -> DeepInk
            }

            val chipShape = RoundedCornerShape(24.dp)
            Surface(
                modifier = Modifier
                    .scale(scale)
                    .heightIn(min = 48.dp)
                    .tvFocusHighlight(
                        shape = chipShape,
                        focusBorderColor = if (isSelected) DeepInk else CherryRed,
                        borderWidth = 3.dp,
                        focusedScale = 1.08f
                    )
                    .clip(chipShape)
                    .clickable { onSelectCategory(category) }
                    .testTag("category_chip_${category.id.lowercase()}"),
                shape = chipShape,
                color = containerColor,
                shadowElevation = if (isSelected) 6.dp else 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            Color.White.copy(alpha = 0.25f)
                        } else {
                            category.containerColor
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = if (isSelected) contentColor else category.accentColor,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = category.labelTr,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor
                    )
                }
            }
        }
    }
}
