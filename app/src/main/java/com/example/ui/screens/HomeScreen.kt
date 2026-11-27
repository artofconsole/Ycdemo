package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.VideoItem
import com.example.model.VideoSourceType
import com.example.ui.theme.CoralAccent
import com.example.ui.theme.SkyAccent
import com.example.ui.theme.SunAccent

@Composable
fun HomeScreen(
    videos: List<VideoItem>,
    selectedCategory: String?,
    remainingMinutes: Int?,
    isLimitReached: Boolean,
    isChildLocked: Boolean,
    isCloudSynced: Boolean,
    feedbackMessage: String?,
    onFeedbackConsumed: () -> Unit,
    onSelectCategory: (String?) -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    onImportVideos: (List<Uri>) -> Unit,
    onAddVideoLink: (String, String?) -> Unit,
    onImportYouTubePlaylist: (String) -> Unit,
    onDeleteVideo: (VideoItem) -> Unit,
    onOpenParentPanel: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddLinkDialog by remember { mutableStateOf(false) }
    var showAddPlaylistDialog by remember { mutableStateOf(false) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportVideos(uris)
        }
    }

    LaunchedEffect(feedbackMessage) {
        if (!feedbackMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(feedbackMessage)
            onFeedbackConsumed()
        }
    }

    val categories = remember(videos) {
        videos.map { it.category }.distinct().sorted()
    }
    val visibleVideos = remember(videos, selectedCategory) {
        if (selectedCategory == null) videos else videos.filter { it.category == selectedCategory }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            HomeTopBar(
                remainingMinutes = remainingMinutes,
                isCloudSynced = isCloudSynced,
                onOpenParentPanel = onOpenParentPanel,
            )
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 180.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                HeroActionBanner(
                    totalVideos = videos.size,
                    isChildLocked = isChildLocked,
                    onPickLocalVideos = { videoPicker.launch(arrayOf("video/*")) },
                    onOpenLinkDialog = { showAddLinkDialog = true },
                    onOpenPlaylistDialog = { showAddPlaylistDialog = true },
                )
            }

            if (isLimitReached) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DailyLimitBanner(onOpenParentPanel = onOpenParentPanel)
                }
            }

            if (categories.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CategoryFilterRow(
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onSelectCategory = onSelectCategory,
                    )
                }
            }

            if (visibleVideos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyLibraryCard(
                        onPickLocalVideos = { videoPicker.launch(arrayOf("video/*")) },
                        onOpenLinkDialog = { showAddLinkDialog = true },
                        onOpenPlaylistDialog = { showAddPlaylistDialog = true },
                    )
                }
            } else {
                items(
                    items = visibleVideos,
                    key = { it.id },
                ) { video ->
                    VideoPosterCard(
                        video = video,
                        isChildLocked = isChildLocked,
                        onClick = { onOpenVideo(video) },
                        onDelete = { onDeleteVideo(video) },
                    )
                }
            }
        }
    }

    if (showAddLinkDialog) {
        AddLinkDialog(
            onDismiss = { showAddLinkDialog = false },
            onConfirm = { url, customTitle ->
                showAddLinkDialog = false
                onAddVideoLink(url, customTitle)
            },
        )
    }

    if (showAddPlaylistDialog) {
        AddPlaylistDialog(
            onDismiss = { showAddPlaylistDialog = false },
            onConfirm = { playlistUrl ->
                showAddPlaylistDialog = false
                onImportYouTubePlaylist(playlistUrl)
            },
        )
    }
}

@Composable
private fun HomeTopBar(
    remainingMinutes: Int?,
    isCloudSynced: Boolean,
    onOpenParentPanel: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(SkyAccent, CoralAccent),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tv,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "MinikTV",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        if (isCloudSynced) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDone,
                                contentDescription = "Bulut Senkronize",
                                tint = SkyAccent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Text(
                        text = if (remainingMinutes == null) {
                            "Sınırsız izleme modu"
                        } else {
                            "Bugün kalan süre: $remainingMinutes dk"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                onClick = onOpenParentPanel,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Ebeveyn Kontrolü",
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Ebeveyn",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroActionBanner(
    totalVideos: Int,
    isChildLocked: Boolean,
    onPickLocalVideos: () -> Unit,
    onOpenLinkDialog: () -> Unit,
    onOpenPlaylistDialog: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Güvenli Çocuk Sineması",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isChildLocked) {
                            "Çocuk Kilidi aktif: Yeni içerik eklemek için Ebeveyn panelini açın."
                        } else {
                            "Sadece senin seçtiğin çizgi filmler ve videolar gösterilir ($totalVideos içerik)."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
            }

            if (!isChildLocked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onPickLocalVideos,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VideoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Cihazdan",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenLinkDialog,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Tek Video",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenPlaylistDialog,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlaylistPlay,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Playlist",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyLimitBanner(
    onOpenParentPanel: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bugünkü izleme süresi doldu",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "Süreyi uzatmak için Ebeveyn panelini açın.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            TextButton(onClick = onOpenParentPanel) {
                Text("Ayarla", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<String>,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CategoryChip(
            label = "Tümü",
            selected = selectedCategory == null,
            onClick = { onSelectCategory(null) },
        )
        categories.forEach { category ->
            CategoryChip(
                label = category,
                selected = selectedCategory == category,
                onClick = { onSelectCategory(category) },
            )
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VideoPosterCard(
    video: VideoItem,
    isChildLocked: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val badgeColor = when (video.sourceType) {
        VideoSourceType.YOUTUBE -> CoralAccent
        VideoSourceType.LOCAL -> SkyAccent
        VideoSourceType.DIRECT_URL -> SunAccent
    }
    val badgeText = when (video.sourceType) {
        VideoSourceType.YOUTUBE -> "YouTube"
        VideoSourceType.LOCAL -> "Cihaz"
        VideoSourceType.DIRECT_URL -> "Bağlantı"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (!video.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        badgeColor.copy(alpha = 0.75f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tv,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Oynat",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    shape = CircleShape,
                    color = badgeColor,
                ) {
                    Text(
                        text = badgeText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                if (!isChildLocked) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Sil",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = video.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard(
    onPickLocalVideos: () -> Unit,
    onOpenLinkDialog: () -> Unit,
    onOpenPlaylistDialog: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = "Henüz video eklenmedi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Çocuğunuzun izlemesini istediğiniz videoları cihazdan seçin veya YouTube bağlantısı ekleyin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onPickLocalVideos) {
                    Text("Cihazdan Seç")
                }
                OutlinedButton(onClick = onOpenLinkDialog) {
                    Text("Link Ekle")
                }
                OutlinedButton(onClick = onOpenPlaylistDialog) {
                    Text("Playlist")
                }
            }
        }
    }
}

@Composable
private fun AddLinkDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video Bağlantısı Ekle", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("YouTube veya MP4 bağlantısı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Başlık (isteğe bağlı)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(url, title.takeIf { it.isNotBlank() }) },
                enabled = url.isNotBlank(),
            ) {
                Text("Ekle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        },
    )
}

@Composable
private fun AddPlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var playlistUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("YouTube Oynatma Listesi Ekle", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Herkese açık veya liste dışı bir YouTube oynatma listesi bağlantısı yapıştırın.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = playlistUrl,
                    onValueChange = { playlistUrl = it },
                    label = { Text("Playlist URL (list=...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(playlistUrl) },
                enabled = playlistUrl.isNotBlank(),
            ) {
                Text("Listeyi Ekle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        },
    )
}
