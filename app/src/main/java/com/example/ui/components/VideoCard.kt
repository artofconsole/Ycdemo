package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.VideoEntity
import com.example.ui.theme.CherryRed
import com.example.ui.theme.CoralPink
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.DeepInk
import com.example.ui.theme.MutedInk
import java.io.File

@Composable
fun VideoCard(
    video: VideoEntity,
    isKidLockEnabled: Boolean,
    isTvMode: Boolean = false,
    onPlayClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val category = video.category
    val cardShape = RoundedCornerShape(24.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusHighlight(
                shape = cardShape,
                focusBorderColor = CherryRed,
                borderWidth = 4.dp,
                focusedScale = 1.05f
            )
            .clip(cardShape)
            .clickable(onClick = onPlayClick)
            .testTag("video_card_${video.id}"),
        shape = cardShape,
        color = CrispWhite,
        shadowElevation = 5.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(category.containerColor)
            ) {
                val thumbFile = video.thumbnailPath?.let { File(it) }?.takeIf { it.exists() }
                val remoteThumb = video.remoteThumbnailUrl?.takeIf { it.isNotBlank() }
                if (thumbFile != null || remoteThumb != null) {
                    AsyncImage(
                        model = thumbFile ?: remoteThumb,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        category.containerColor,
                                        category.accentColor.copy(alpha = 0.35f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = category.accentColor,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }

                // Source indicator badge (YouTube / Bulut)
                if (video.source != com.example.data.model.VideoSourceType.LOCAL) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (video.source == com.example.data.model.VideoSourceType.YOUTUBE) {
                            CherryRed.copy(alpha = 0.92f)
                        } else {
                            com.example.ui.theme.SkyBlue.copy(alpha = 0.92f)
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = if (video.isDownloadedOffline) "Bulut • Çevrimdışı" else video.source.labelTr,
                            style = MaterialTheme.typography.labelSmall,
                            color = CrispWhite,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Subtle bottom gradient for badge readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                            )
                        )
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = category.accentColor.copy(alpha = 0.92f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = category.icon,
                                    contentDescription = null,
                                    tint = CrispWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = category.labelTr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CrispWhite
                                )
                            }
                        }

                        if (video.durationMs > 0L) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Black.copy(alpha = 0.72f)
                            ) {
                                Text(
                                    text = video.formattedDuration(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CrispWhite,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Play badge in center
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp),
                    shape = CircleShape,
                    color = CherryRed.copy(alpha = 0.92f),
                    shadowElevation = 6.dp
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play_pause),
                        tint = CrispWhite,
                        modifier = Modifier
                            .padding(10.dp)
                            .fillMaxSize()
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = DeepInk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (video.lastPositionMs > 5000L && video.durationMs > 0L) {
                        val percent = ((video.lastPositionMs.toFloat() / video.durationMs) * 100f)
                            .toInt()
                            .coerceIn(1, 99)
                        Text(
                            text = "%$percent izlendi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedInk,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // On TV, keep video card as a single clean D-Pad focus target (favorite editable in Parent Panel)
                if (!isKidLockEnabled && !isTvMode) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.testTag("favorite_button_${video.id}")
                    ) {
                        Icon(
                            imageVector = if (video.isFavorite) {
                                Icons.Rounded.Favorite
                            } else {
                                Icons.Rounded.FavoriteBorder
                            },
                            contentDescription = stringResource(R.string.cd_favorite_toggle),
                            tint = if (video.isFavorite) CoralPink else MutedInk
                        )
                    }
                }
            }
        }
    }
}
