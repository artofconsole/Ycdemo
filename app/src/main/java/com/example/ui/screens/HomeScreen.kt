package com.example.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.VideoEntity
import com.example.data.model.VideoCategory
import com.example.ui.MinikTvUiState
import com.example.ui.components.CategoryChipBar
import com.example.ui.components.ParentGateDialog
import com.example.ui.components.VideoCard
import com.example.ui.components.tvFocusHighlight
import com.example.ui.theme.CherryRed
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.DeepInk
import com.example.ui.theme.MintGreen
import com.example.ui.theme.MutedInk
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SoftSurfaceVariant
import com.example.ui.theme.SunshineYellow
import com.example.ui.theme.SunshineYellowContainer
import com.example.ui.theme.WarmCream
import com.example.util.LockTaskHelper

private enum class GateAction {
    UNLOCK_KID_LOCK,
    OPEN_PARENT_PANEL,
    RESET_SLEEP_TIMER
}

@Composable
fun HomeScreen(
    uiState: MinikTvUiState,
    onSelectCategory: (VideoCategory) -> Unit,
    onPlayVideo: (VideoEntity) -> Unit,
    onToggleFavorite: (VideoEntity) -> Unit,
    onImportVideos: (List<Uri>) -> Unit,
    onImportFolderTree: (Uri) -> Unit,
    onScanAppMoviesFolder: () -> Unit,
    onEnableKidLock: (Activity) -> Unit,
    onDisableKidLock: (Activity) -> Unit,
    onResetSleepTimer: () -> Unit,
    onOpenParentPanel: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isTv = remember(context) { LockTaskHelper.isTvDevice(context) }
    val isLocked = uiState.lockSettings.isKidLockEnabled
    val overscanHorizontal = if (isTv) 28.dp else 16.dp

    var pendingGateAction by remember { mutableStateOf<GateAction?>(null) }
    val firstVideoFocusRequester = remember { FocusRequester() }
    val emptyActionFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv, uiState.filteredVideos.isNotEmpty()) {
        if (isTv) {
            runCatching {
                if (uiState.filteredVideos.isNotEmpty()) {
                    firstVideoFocusRequester.requestFocus()
                } else {
                    emptyActionFocusRequester.requestFocus()
                }
            }
        }
    }

    // Block hardware/remote Back press when Kid Lock is active so the child cannot leave the app.
    BackHandler(enabled = isLocked) {
        onShowMessage("Çocuk Ekran Kilidi açık! Çıkmak için sağ üstteki kilit simgesine basın.")
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) onImportVideos(uris)
        }
    )

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { treeUri ->
            treeUri?.let(onImportFolderTree)
        }
    )

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            if (uris.isNotEmpty()) onImportVideos(uris)
        }
    )

    fun launchDocumentPickerSafe() {
        runCatching {
            documentPickerLauncher.launch(arrayOf("video/*"))
        }.onFailure {
            onShowMessage("Bu TV'de sistem dosya seçicisi yok. Uygulama klasörünü taramayı deneyin.")
        }
    }

    fun launchFolderPickerSafe() {
        runCatching {
            folderPickerLauncher.launch(null)
        }.onFailure {
            onShowMessage("Bu TV'de klasör seçicisi desteklenmiyor. Uygulama klasörünü taramayı deneyin.")
        }
    }

    fun launchPhotoPickerSafe() {
        runCatching {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        }.onFailure {
            onShowMessage("Bu cihazda galeri seçicisi bulunamadı.")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = WarmCream,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                HomeTopBar(
                    isTvMode = isTv,
                    isKidLockEnabled = isLocked,
                    remainingTimerSeconds = uiState.remainingTimerSeconds,
                    horizontalPadding = overscanHorizontal,
                    onLockPillClick = {
                        if (isLocked) {
                            pendingGateAction = GateAction.UNLOCK_KID_LOCK
                        } else {
                            activity?.let(onEnableKidLock)
                        }
                    },
                    onParentButtonClick = {
                        if (isLocked) {
                            pendingGateAction = GateAction.OPEN_PARENT_PANEL
                        } else {
                            onOpenParentPanel()
                        }
                    }
                )
            },
            floatingActionButton = {
                if (!isLocked && !isTv && uiState.allVideos.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = { launchDocumentPickerSafe() },
                        containerColor = CherryRed,
                        contentColor = CrispWhite,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .navigationBarsPadding()
                            .testTag("home_add_video_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.cd_add_video)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Video Ekle", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                CategoryChipBar(
                    selectedCategory = uiState.selectedCategory,
                    onSelectCategory = onSelectCategory,
                    modifier = Modifier.padding(horizontal = if (isTv) 12.dp else 0.dp)
                )

                AnimatedVisibility(visible = uiState.isImporting) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = overscanHorizontal, vertical = 6.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = SunshineYellowContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = DeepInk
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Videolar hazırlanıyor ve kapak görselleri oluşturuluyor...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DeepInk
                            )
                        }
                    }
                }

                if (uiState.allVideos.isEmpty()) {
                    EmptyLibraryState(
                        isTvMode = isTv,
                        isKidLockEnabled = isLocked,
                        appMoviesPath = uiState.appMoviesFolderPath,
                        primaryFocusRequester = emptyActionFocusRequester,
                        onPickFromFiles = { launchDocumentPickerSafe() },
                        onPickFolderTree = { launchFolderPickerSafe() },
                        onScanAppFolder = onScanAppMoviesFolder,
                        onPickFromGallery = { launchPhotoPickerSafe() },
                        onOpenParentPanel = {
                            if (isLocked) {
                                pendingGateAction = GateAction.OPEN_PARENT_PANEL
                            } else {
                                onOpenParentPanel()
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = overscanHorizontal)
                    )
                } else if (uiState.filteredVideos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = uiState.selectedCategory.icon,
                                contentDescription = null,
                                tint = uiState.selectedCategory.accentColor,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "${uiState.selectedCategory.labelTr} kategorisinde henüz video yok.",
                                style = MaterialTheme.typography.titleMedium,
                                color = DeepInk,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Ebeveyn Paneli'nden videoları kategorilere ayırabilirsiniz.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedInk,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = if (isTv) 300.dp else 280.dp),
                        contentPadding = PaddingValues(
                            start = overscanHorizontal,
                            end = overscanHorizontal,
                            top = 12.dp,
                            bottom = if (isLocked || isTv) 32.dp else 96.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(if (isTv) 22.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isTv) 22.dp else 16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("home_video_grid")
                    ) {
                        itemsIndexed(
                            items = uiState.filteredVideos,
                            key = { _, video -> video.id }
                        ) { index, video ->
                            val cardModifier = if (index == 0) {
                                Modifier.focusRequester(firstVideoFocusRequester)
                            } else {
                                Modifier
                            }
                            VideoCard(
                                video = video,
                                isKidLockEnabled = isLocked,
                                isTvMode = isTv,
                                onPlayClick = { onPlayVideo(video) },
                                onToggleFavorite = { onToggleFavorite(video) },
                                modifier = cardModifier
                            )
                        }
                    }
                }
            }
        }

        // Sleep Timer Lock Overlay
        if (uiState.isSleepTimeExpired) {
            SleepTimerExpiredOverlay(
                onUnlockClick = { pendingGateAction = GateAction.RESET_SLEEP_TIMER }
            )
        }

        // Floating Status Pill Banner
        AnimatedVisibility(
            visible = uiState.statusBannerMessage != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 78.dp, start = 20.dp, end = 20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = DeepInk,
                shadowElevation = 8.dp
            ) {
                Text(
                    text = uiState.statusBannerMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CrispWhite,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }

        pendingGateAction?.let { action ->
            ParentGateDialog(
                title = when (action) {
                    GateAction.UNLOCK_KID_LOCK -> "Ekran Kilidini Kapat"
                    GateAction.OPEN_PARENT_PANEL -> "Ebeveyn Paneli"
                    GateAction.RESET_SLEEP_TIMER -> "Süreyi Sıfırla"
                },
                configuredPin = uiState.lockSettings.parentPin,
                onDismiss = { pendingGateAction = null },
                onUnlocked = {
                    val resolved = action
                    pendingGateAction = null
                    when (resolved) {
                        GateAction.UNLOCK_KID_LOCK -> activity?.let(onDisableKidLock)
                        GateAction.OPEN_PARENT_PANEL -> onOpenParentPanel()
                        GateAction.RESET_SLEEP_TIMER -> onResetSleepTimer()
                    }
                }
            )
        }
    }
}

@Composable
private fun HomeTopBar(
    isTvMode: Boolean,
    isKidLockEnabled: Boolean,
    remainingTimerSeconds: Long,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onLockPillClick: () -> Unit,
    onParentButtonClick: () -> Unit
) {
    Surface(
        color = WarmCream,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = horizontalPadding,
                    vertical = if (isTvMode) 16.dp else 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CherryRed,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(if (isTvMode) 48.dp else 42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.cd_app_logo),
                        tint = CrispWhite,
                        modifier = Modifier.padding(6.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "yCloud",
                        style = if (isTvMode) {
                            MaterialTheme.typography.headlineMedium
                        } else {
                            MaterialTheme.typography.headlineSmall
                        },
                        color = DeepInk
                    )
                    if (remainingTimerSeconds > 0L) {
                        val mins = (remainingTimerSeconds / 60L) + 1
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = CherryRed,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "$mins dk kaldı",
                                style = MaterialTheme.typography.labelSmall,
                                color = CherryRed
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val lockShape = RoundedCornerShape(22.dp)
                Surface(
                    onClick = onLockPillClick,
                    shape = lockShape,
                    color = if (isKidLockEnabled) CherryRed else SoftSurfaceVariant,
                    shadowElevation = if (isKidLockEnabled) 4.dp else 0.dp,
                    modifier = Modifier
                        .height(48.dp)
                        .tvFocusHighlight(
                            shape = lockShape,
                            focusBorderColor = if (isKidLockEnabled) DeepInk else CherryRed
                        )
                        .testTag("kid_lock_toggle_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isKidLockEnabled) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            contentDescription = stringResource(R.string.cd_kid_lock),
                            tint = if (isKidLockEnabled) CrispWhite else DeepInk,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isKidLockEnabled) {
                                stringResource(R.string.kid_lock_active_badge)
                            } else {
                                stringResource(R.string.kid_lock_inactive_badge)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isKidLockEnabled) CrispWhite else DeepInk
                        )
                    }
                }

                Surface(
                    onClick = onParentButtonClick,
                    shape = CircleShape,
                    color = CrispWhite,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .tvFocusHighlight(CircleShape, focusedScale = 1.1f)
                        .testTag("parent_panel_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.cd_parent_panel),
                            tint = DeepInk
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    isTvMode: Boolean,
    isKidLockEnabled: Boolean,
    appMoviesPath: String,
    primaryFocusRequester: FocusRequester,
    onPickFromFiles: () -> Unit,
    onPickFolderTree: () -> Unit,
    onScanAppFolder: () -> Unit,
    onPickFromGallery: () -> Unit,
    onOpenParentPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_kids_empty_tv_1795630261267),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(if (isTvMode) 0.55f else 0.78f)
                    .height(if (isTvMode) 170.dp else 200.dp)
                    .clip(RoundedCornerShape(28.dp))
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.empty_title),
                style = MaterialTheme.typography.headlineSmall,
                color = DeepInk,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (isTvMode) {
                    "USB bellekten veya TV klasöründen yerel videolarınızı yükleyerek güvenli çocuk alanı oluşturun."
                } else {
                    stringResource(R.string.empty_subtitle)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MutedInk,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))

            if (!isKidLockEnabled) {
                val btnShape = RoundedCornerShape(22.dp)
                if (isTvMode) {
                    Button(
                        onClick = onScanAppFolder,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CherryRed,
                            contentColor = CrispWhite
                        ),
                        shape = btnShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .focusRequester(primaryFocusRequester)
                            .tvFocusHighlight(btnShape, focusBorderColor = DeepInk)
                            .testTag("empty_scan_app_folder_button")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Uygulama Klasörünü Tara (TV / USB)",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onPickFolderTree,
                        shape = btnShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .tvFocusHighlight(btnShape)
                    ) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, tint = SkyBlue)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "USB / Klasör Seç (Toplu Yükle)",
                            style = MaterialTheme.typography.titleMedium,
                            color = DeepInk
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onPickFromFiles,
                        shape = btnShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .tvFocusHighlight(btnShape)
                            .testTag("empty_pick_files_button")
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = CherryRed)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.action_pick_from_files),
                            style = MaterialTheme.typography.titleMedium,
                            color = DeepInk
                        )
                    }

                    if (appMoviesPath.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SoftSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "TV dosya yöneticisiyle videoları şu klasöre kopyalayabilirsiniz:\n$appMoviesPath",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedInk,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onPickFromFiles,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CherryRed,
                            contentColor = CrispWhite
                        ),
                        shape = btnShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .focusRequester(primaryFocusRequester)
                            .tvFocusHighlight(btnShape, focusBorderColor = DeepInk)
                            .testTag("empty_pick_files_button")
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.action_pick_from_files),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onPickFromGallery,
                        shape = btnShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .tvFocusHighlight(btnShape)
                            .testTag("empty_pick_gallery_button")
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = SkyBlue)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.action_pick_from_gallery),
                            style = MaterialTheme.typography.titleMedium,
                            color = DeepInk
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onOpenParentPanel,
                    shape = btnShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .tvFocusHighlight(btnShape)
                        .testTag("empty_open_parent_panel_button")
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = null, tint = MintGreen)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "YouTube / Bulut / Eşitleme (Ebeveyn Paneli)",
                        style = MaterialTheme.typography.titleMedium,
                        color = DeepInk
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepTimerExpiredOverlay(
    onUnlockClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DeepInk.copy(alpha = 0.94f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = SunshineYellow,
                modifier = Modifier.size(88.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = null,
                    tint = DeepInk,
                    modifier = Modifier.padding(20.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Dinlenme Zamanı!",
                style = MaterialTheme.typography.displaySmall,
                color = CrispWhite,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Belirlenen izleme süresi doldu. Devam etmek için ebeveyn doğrulaması gerekiyor.",
                style = MaterialTheme.typography.bodyLarge,
                color = CrispWhite.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            val btnShape = RoundedCornerShape(22.dp)
            Button(
                onClick = onUnlockClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MintGreen,
                    contentColor = CrispWhite
                ),
                shape = btnShape,
                modifier = Modifier
                    .height(54.dp)
                    .focusRequester(focusRequester)
                    .tvFocusHighlight(btnShape, focusBorderColor = CrispWhite)
                    .testTag("sleep_timer_unlock_button")
            ) {
                Icon(Icons.Rounded.LockOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Ebeveyn Kilidini Aç", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
