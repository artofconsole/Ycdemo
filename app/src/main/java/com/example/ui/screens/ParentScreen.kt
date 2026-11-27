package com.example.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.VideoEntity
import com.example.data.model.VideoCategory
import com.example.data.model.VideoSourceType
import com.example.ui.MinikTvUiState
import com.example.ui.components.tvFocusHighlight
import com.example.ui.theme.CherryRed
import com.example.ui.theme.CherryRedContainer
import com.example.ui.theme.CoralPink
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.DeepInk
import com.example.ui.theme.MintGreen
import com.example.ui.theme.MintGreenContainer
import com.example.ui.theme.MutedInk
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SkyBlueContainer
import com.example.ui.theme.SoftSurfaceVariant
import com.example.ui.theme.WarmCream
import com.example.util.LockTaskHelper
import com.example.util.QrCodeHelper
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentScreen(
    uiState: MinikTvUiState,
    onBack: () -> Unit,
    onImportVideos: (List<Uri>, VideoCategory) -> Unit,
    onImportFolderTree: (Uri, VideoCategory) -> Unit,
    onScanAppMoviesFolder: (VideoCategory) -> Unit,
    onAddYouTubeVideo: (String, String?, VideoCategory) -> Unit,
    onImportYouTubePlaylist: (String, VideoCategory) -> Unit,
    onAddCloudVideoUrl: (String, String?, VideoCategory, Boolean) -> Unit,
    onImportJsonPlaylist: (String, VideoCategory) -> Unit,
    onDownloadCloudVideoOffline: (VideoEntity) -> Unit,
    onGoogleSignIn: () -> Unit,
    onGoogleSignOut: () -> Unit,
    onSyncNow: () -> Unit,
    onStartLocalLinkReceiver: () -> Unit,
    onStopLocalLinkReceiver: () -> Unit,
    onToggleFavorite: (VideoEntity) -> Unit,
    onEnableKidLock: (Activity) -> Unit,
    onDisableKidLock: (Activity) -> Unit,
    onRequestSystemPinning: (Activity) -> Unit,
    onSetParentPin: (String) -> Unit,
    onSetAutoplayNext: (Boolean) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onUpdateVideoDetails: (Long, String, VideoCategory) -> Unit,
    onDeleteVideo: (VideoEntity) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isTv = remember(context) { LockTaskHelper.isTvDevice(context) }

    var importTargetCategory by remember { mutableStateOf(VideoCategory.CARTOON) }
    var editingVideo by remember { mutableStateOf<VideoEntity?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showYouTubeDialog by remember { mutableStateOf(false) }
    var showYouTubePlaylistDialog by remember { mutableStateOf(false) }
    var showCloudUrlDialog by remember { mutableStateOf(false) }
    var showJsonPlaylistDialog by remember { mutableStateOf(false) }
    var showWifiReceiverDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            onStopLocalLinkReceiver()
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onImportVideos(uris, importTargetCategory)
            }
        }
    )

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { treeUri ->
            treeUri?.let { onImportFolderTree(it, importTargetCategory) }
        }
    )

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onImportVideos(uris, importTargetCategory)
            }
        }
    )

    Scaffold(
        containerColor = WarmCream,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmCream),
                title = {
                    Text(
                        text = "Ebeveyn Paneli",
                        style = MaterialTheme.typography.headlineSmall,
                        color = DeepInk
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .tvFocusHighlight(CircleShape)
                            .testTag("parent_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = DeepInk
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 820.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = if (isTv) 28.dp else 16.dp,
                    vertical = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Status Summary
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = CrispWhite,
                        shadowElevation = 3.dp
                    ) {
                        Column {
                            Image(
                                painter = painterResource(id = R.drawable.img_kids_hero_banner_1795630261642),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(136.dp)
                                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${uiState.libraryVideos.size} Video Hazır",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = DeepInk
                                    )
                                    Text(
                                        text = "Yerel dosyalar, YouTube linkleri ve Bulut videoları tek güvenli alanda",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MutedInk
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (uiState.lockSettings.isKidLockEnabled) {
                                        CherryRedContainer
                                    } else {
                                        MintGreenContainer
                                    }
                                ) {
                                    Text(
                                        text = if (uiState.lockSettings.isKidLockEnabled) "Kilitli" else "Açık",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (uiState.lockSettings.isKidLockEnabled) CherryRed else MintGreen,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Cloud Account & Cross-Device Sync Card
                item {
                    val cloud = uiState.cloudAccount
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = CrispWhite,
                        shadowElevation = 3.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(SkyBlueContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (cloud.isSignedIn) {
                                                Icons.Rounded.CloudDone
                                            } else {
                                                Icons.Rounded.CloudSync
                                            },
                                            contentDescription = null,
                                            tint = SkyBlue
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Bulut Senkronizasyonu (Google)",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = DeepInk
                                        )
                                        Text(
                                            text = if (cloud.isSignedIn) {
                                                cloud.email ?: cloud.displayName ?: "Google hesabı bağlı"
                                            } else {
                                                "Telefondan eklediğiniz YouTube ve Bulut linkleri Android TV'ye otomatik gelsin"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MutedInk
                                        )
                                    }
                                }
                            }

                            if (!cloud.syncError.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = CherryRedContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = cloud.syncError,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CherryRed,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            val btnShape = RoundedCornerShape(18.dp)
                            if (cloud.isSignedIn) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = onSyncNow,
                                        enabled = !cloud.isSyncing,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SkyBlue,
                                            contentColor = CrispWhite
                                        ),
                                        shape = btnShape,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .tvFocusHighlight(btnShape, focusBorderColor = DeepInk)
                                            .testTag("parent_cloud_sync_now_button")
                                    ) {
                                        if (cloud.isSyncing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = CrispWhite
                                            )
                                        } else {
                                            Icon(Icons.Rounded.CloudSync, contentDescription = null)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (cloud.isSyncing) "Eşitleniyor..." else "Şimdi Eşitle",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = onGoogleSignOut,
                                        shape = btnShape,
                                        modifier = Modifier
                                            .height(48.dp)
                                            .tvFocusHighlight(btnShape)
                                            .testTag("parent_google_signout_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                                            contentDescription = null,
                                            tint = CherryRed
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Çıkış", color = CherryRed)
                                    }
                                }
                            } else {
                                Button(
                                    onClick = onGoogleSignIn,
                                    enabled = !cloud.isSigningIn,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SkyBlue,
                                        contentColor = CrispWhite
                                    ),
                                    shape = btnShape,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .tvFocusHighlight(btnShape, focusBorderColor = DeepInk)
                                        .testTag("parent_google_signin_button")
                                ) {
                                    if (cloud.isSigningIn) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = CrispWhite
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text("Bağlanılıyor...", style = MaterialTheme.typography.titleSmall)
                                    } else {
                                        Icon(Icons.Rounded.AccountCircle, contentDescription = null)
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = "Google ile Giriş Yap (Bulut Eşitle)",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // YouTube & Cloud URL Import Section
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = CrispWhite,
                        shadowElevation = 3.dp
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "YouTube ve Bulut Video Ekle",
                                style = MaterialTheme.typography.titleLarge,
                                color = DeepInk
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Eklenecek içeriklerin kategorisi (aşağıdan seçilir): ${importTargetCategory.labelTr}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedInk
                            )
                            Spacer(Modifier.height(10.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(VideoCategory.assignableCategories, key = { it.id }) { cat ->
                                    FilterChip(
                                        selected = importTargetCategory == cat,
                                        onClick = { importTargetCategory = cat },
                                        label = { Text(cat.labelTr) },
                                        modifier = Modifier.tvFocusHighlight(RoundedCornerShape(12.dp))
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            val btnShape = RoundedCornerShape(18.dp)

                            Button(
                                onClick = {
                                    onStartLocalLinkReceiver()
                                    showWifiReceiverDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MintGreen,
                                    contentColor = CrispWhite
                                ),
                                shape = btnShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .tvFocusHighlight(btnShape, focusBorderColor = DeepInk)
                                    .testTag("parent_wifi_phone_send_button")
                            ) {
                                Icon(Icons.Rounded.QrCode2, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Telefondan Link Gönder (Wi-Fi / QR — Kumandasız)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            Button(
                                onClick = { showYouTubeDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CherryRed,
                                    contentColor = CrispWhite
                                ),
                                shape = btnShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .tvFocusHighlight(btnShape, focusBorderColor = DeepInk)
                                    .testTag("parent_add_youtube_button")
                            ) {
                                Icon(Icons.Rounded.PlayCircle, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "YouTube Linki Ekle",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = { showYouTubePlaylistDialog = true },
                                shape = btnShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .tvFocusHighlight(btnShape)
                                    .testTag("parent_import_youtube_playlist_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.PlaylistAdd,
                                    contentDescription = null,
                                    tint = CherryRed
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "YouTube Oynatma Listesi Ekle (Playlist)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = DeepInk
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = { showCloudUrlDialog = true },
                                shape = btnShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .tvFocusHighlight(btnShape)
                                    .testTag("parent_add_cloud_url_button")
                            ) {
                                Icon(Icons.Rounded.Link, contentDescription = null, tint = SkyBlue)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Bulut Video / Drive / HLS Linki Ekle",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = DeepInk
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = { showJsonPlaylistDialog = true },
                                shape = btnShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .tvFocusHighlight(btnShape)
                                    .testTag("parent_import_json_button")
                            ) {
                                Icon(Icons.Rounded.DataObject, contentDescription = null, tint = MintGreen)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Bulut JSON Video Listesi İçe Aktar",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = DeepInk
                                )
                            }
                        }
                    }
                }

                // Kid Lock & System Screen Pinning Section
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = CrispWhite,
                        shadowElevation = 3.dp
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(CherryRedContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Shield,
                                            contentDescription = null,
                                            tint = CherryRed
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Çocuk Ekran Kilidi",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = DeepInk
                                        )
                                        Text(
                                            text = "Uygulamadan geri çıkışını ve düzenlemeyi engeller",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MutedInk
                                        )
                                    }
                                }
                                Switch(
                                    checked = uiState.lockSettings.isKidLockEnabled,
                                    onCheckedChange = { enabled ->
                                        activity?.let { act ->
                                            if (enabled) onEnableKidLock(act) else onDisableKidLock(act)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(checkedTrackColor = CherryRed),
                                    modifier = Modifier
                                        .tvFocusHighlight(RoundedCornerShape(20.dp))
                                        .testTag("parent_kid_lock_switch")
                                )
                            }

                            Spacer(Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = SoftSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = if (uiState.isSystemPinned) {
                                            "Android Ekran Sabitleme aktif: Ana ekran ve Son Uygulamalar tuşları kilitlendi."
                                        } else {
                                            "Çocuk Kilidi açıldığında geri tuşu engellenir ve destekleyen cihazlarda Android Ekran Sabitleme başlatılır."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DeepInk
                                    )
                                    if (uiState.lockSettings.isKidLockEnabled && !uiState.isSystemPinned) {
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = { activity?.let(onRequestSystemPinning) },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.tvFocusHighlight(RoundedCornerShape(14.dp))
                                        ) {
                                            Icon(Icons.Rounded.Lock, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Ekran Sabitlemeyi Başlat")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Local Video Import Section
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = CrispWhite,
                        shadowElevation = 3.dp
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Yerel Video Yükle (Cihaz / USB)",
                                style = MaterialTheme.typography.titleLarge,
                                color = DeepInk
                            )
                            Spacer(Modifier.height(12.dp))
                            val btnShape = RoundedCornerShape(18.dp)

                            Button(
                                onClick = { onScanAppMoviesFolder(importTargetCategory) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MintGreen,
                                    contentColor = CrispWhite
                                ),
                                shape = btnShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .tvFocusHighlight(btnShape, focusBorderColor = DeepInk)
                                    .testTag("parent_scan_folder_button")
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Uygulama Klasörünü Tara (TV / USB)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = {
                                    runCatching { folderPickerLauncher.launch(null) }
                                        .onFailure {
                                            onShowMessage("Bu cihazda klasör seçici bulunamadı.")
                                        }
                                },
                                shape = btnShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .tvFocusHighlight(btnShape)
                                    .testTag("parent_pick_tree_button")
                            ) {
                                Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, tint = SkyBlue)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Klasör / USB Seç (Toplu Yükle)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = DeepInk
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = {
                                    runCatching { documentPickerLauncher.launch(arrayOf("video/*")) }
                                        .onFailure {
                                            onShowMessage("Bu cihazda sistem dosya seçicisi bulunamadı.")
                                        }
                                },
                                shape = btnShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .tvFocusHighlight(btnShape)
                                    .testTag("parent_pick_files_button")
                            ) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = CherryRed)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.action_pick_from_files),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = DeepInk
                                )
                            }

                            if (!isTv) {
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(
                                                    ActivityResultContracts.PickVisualMedia.VideoOnly
                                                )
                                            )
                                        }.onFailure {
                                            onShowMessage("Galeri seçicisi açılamadı.")
                                        }
                                    },
                                    shape = btnShape,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .tvFocusHighlight(btnShape)
                                        .testTag("parent_pick_gallery_button")
                                ) {
                                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = SkyBlue)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.action_pick_from_gallery),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = DeepInk
                                    )
                                }
                            }

                            if (uiState.appMoviesFolderPath.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = SoftSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "TV dosya yöneticisi ile videoları bu klasöre kopyalayıp 'Tara'ya basabilirsiniz:\n${uiState.appMoviesFolderPath}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MutedInk,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Security PIN, Sleep Timer & Autoplay Settings
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = CrispWhite,
                        shadowElevation = 3.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Password, contentDescription = null, tint = SkyBlue)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Ebeveyn PIN Kodu",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = DeepInk
                                        )
                                        Text(
                                            text = if (uiState.lockSettings.parentPin.length == 4) {
                                                "4 haneli özel PIN aktif"
                                            } else {
                                                "PIN yok (Matematik sorusu kullanılır)"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MutedInk
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { showPinDialog = true },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .tvFocusHighlight(RoundedCornerShape(14.dp))
                                        .testTag("parent_pin_setup_button")
                                ) {
                                    Text(
                                        if (uiState.lockSettings.parentPin.length == 4) "Değiştir" else "PIN Belirle"
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Otomatik Sonraki Video",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = DeepInk
                                    )
                                    Text(
                                        text = "Video bitince sıradaki video otomatik başlar",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MutedInk
                                    )
                                }
                                Switch(
                                    checked = uiState.lockSettings.autoplayNext,
                                    onCheckedChange = onSetAutoplayNext,
                                    colors = SwitchDefaults.colors(checkedTrackColor = MintGreen),
                                    modifier = Modifier.tvFocusHighlight(RoundedCornerShape(20.dp))
                                )
                            }

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Timer, contentDescription = null, tint = CherryRed)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "İzleme Süresi Zamanlayıcısı",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = DeepInk
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                val timerOptions = listOf(0, 15, 30, 45, 60)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(timerOptions) { mins ->
                                        val selected = uiState.lockSettings.sleepTimerDurationMinutes == mins
                                        FilterChip(
                                            selected = selected,
                                            onClick = { onSetSleepTimer(mins) },
                                            label = {
                                                Text(if (mins == 0) "Kapalı" else "$mins dk")
                                            },
                                            modifier = Modifier.tvFocusHighlight(RoundedCornerShape(12.dp))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Library Management Header
                if (uiState.libraryVideos.isNotEmpty()) {
                    item {
                        Text(
                            text = "Kütüphane Videoları (${uiState.libraryVideos.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = DeepInk,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    items(uiState.libraryVideos, key = { it.id }) { video ->
                        ParentVideoRow(
                            video = video,
                            onDownloadOffline = { onDownloadCloudVideoOffline(video) },
                            onToggleFavorite = { onToggleFavorite(video) },
                            onEditClick = { editingVideo = video },
                            onDeleteClick = { onDeleteVideo(video) }
                        )
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    // Add YouTube Video Dialog
    if (showYouTubeDialog) {
        var urlInput by remember { mutableStateOf("") }
        var titleInput by remember { mutableStateOf("") }
        var selectedCat by remember { mutableStateOf(importTargetCategory) }

        AlertDialog(
            onDismissRequest = { showYouTubeDialog = false },
            title = { Text("YouTube Linki Ekle", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "YouTube video bağlantısını veya 11 haneli video kodunu (örn: dQw4w9WgXcQ) girin. Çocuk kilidi açıkken dışarı çıkış engellenir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedInk
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("YouTube Linki veya Kodu") },
                        placeholder = { Text("https://youtu.be/... veya video kodu") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("youtube_url_input")
                    )
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Özel Başlık (İsteğe bağlı)") },
                        placeholder = { Text("Boş bırakılırsa otomatik alınır") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Kategori", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VideoCategory.assignableCategories, key = { it.id }) { cat ->
                            FilterChip(
                                selected = selectedCat == cat,
                                onClick = { selectedCat = cat },
                                label = { Text(cat.labelTr) },
                                modifier = Modifier.tvFocusHighlight(RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = urlInput.isNotBlank(),
                    onClick = {
                        onAddYouTubeVideo(urlInput, titleInput.ifBlank { null }, selectedCat)
                        showYouTubeDialog = false
                    },
                    modifier = Modifier
                        .tvFocusHighlight(RoundedCornerShape(20.dp))
                        .testTag("youtube_dialog_confirm")
                ) {
                    Text("Ekle")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showYouTubeDialog = false },
                    modifier = Modifier.tvFocusHighlight(RoundedCornerShape(20.dp))
                ) {
                    Text("İptal")
                }
            }
        )
    }

    // Add YouTube Playlist Dialog
    if (showYouTubePlaylistDialog) {
        var playlistInput by remember { mutableStateOf("") }
        var selectedCat by remember { mutableStateOf(importTargetCategory) }

        AlertDialog(
            onDismissRequest = { showYouTubePlaylistDialog = false },
            title = { Text("YouTube Oynatma Listesi Ekle", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Herkese açık veya liste dışı bir YouTube oynatma listesi linki yapıştırın. Listedeki videolar tek seferde eklenir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedInk
                    )
                    OutlinedTextField(
                        value = playlistInput,
                        onValueChange = { playlistInput = it },
                        label = { Text("YouTube Oynatma Listesi Linki") },
                        placeholder = { Text("https://www.youtube.com/playlist?list=PL...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("youtube_playlist_url_input")
                    )
                    Text("Kategori", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VideoCategory.assignableCategories, key = { it.id }) { cat ->
                            FilterChip(
                                selected = selectedCat == cat,
                                onClick = { selectedCat = cat },
                                label = { Text(cat.labelTr) },
                                modifier = Modifier.tvFocusHighlight(RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = playlistInput.isNotBlank(),
                    onClick = {
                        onImportYouTubePlaylist(playlistInput, selectedCat)
                        showYouTubePlaylistDialog = false
                    },
                    modifier = Modifier
                        .tvFocusHighlight(RoundedCornerShape(20.dp))
                        .testTag("youtube_playlist_dialog_confirm")
                ) {
                    Text("Listeyi Ekle")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showYouTubePlaylistDialog = false },
                    modifier = Modifier.tvFocusHighlight(RoundedCornerShape(20.dp))
                ) {
                    Text("İptal")
                }
            }
        )
    }

    // Phone-to-TV Wi-Fi / QR Link Receiver Dialog
    if (showWifiReceiverDialog) {
        val receiverUrl = uiState.localReceiverUrl
        val qrBitmap = remember(receiverUrl) {
            receiverUrl?.let { QrCodeHelper.generateQrBitmap(it, 340) }
        }

        AlertDialog(
            onDismissRequest = {
                showWifiReceiverDialog = false
                onStopLocalLinkReceiver()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Wifi, contentDescription = null, tint = MintGreen)
                    Spacer(Modifier.width(8.dp))
                    Text("Telefondan Link Gönder", style = MaterialTheme.typography.titleLarge)
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Telefonunuz ve TV aynı Wi-Fi ağındayken telefon kamerasıyla QR kodu okutun veya tarayıcıya aşağıdaki adresi yazın:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedInk,
                        textAlign = TextAlign.Center
                    )
                    if (qrBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = CrispWhite,
                            shadowElevation = 2.dp
                        ) {
                            Image(
                                bitmap = qrBitmap,
                                contentDescription = "Wi-Fi Bağlantı QR Kodu",
                                modifier = Modifier
                                    .size(180.dp)
                                    .padding(10.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SoftSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = receiverUrl ?: "Wi-Fi adresi hazırlanıyor...",
                            style = MaterialTheme.typography.titleMedium,
                            color = DeepInk,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                    Text(
                        text = "Telefondan gönderdiğiniz YouTube ve bulut videoları anında listeye eklenir. Bu pencereyi kapatana kadar alıcı açık kalır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedInk,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWifiReceiverDialog = false
                        onStopLocalLinkReceiver()
                    },
                    modifier = Modifier.tvFocusHighlight(RoundedCornerShape(20.dp))
                ) {
                    Text("Kapat")
                }
            }
        )
    }

    // Add Cloud Video URL Dialog
    if (showCloudUrlDialog) {
        var urlInput by remember { mutableStateOf("") }
        var titleInput by remember { mutableStateOf("") }
        var selectedCat by remember { mutableStateOf(importTargetCategory) }
        var downloadOffline by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCloudUrlDialog = false },
            title = { Text("Bulut Video / Drive Linki Ekle", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Doğrudan MP4, WebM, HLS (.m3u8), Dropbox veya Google Drive paylaşım linki girin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedInk
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Video URL / Drive Linki") },
                        placeholder = { Text("https://.../video.mp4") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cloud_url_input")
                    )
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Video Başlığı (İsteğe bağlı)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = downloadOffline,
                            onCheckedChange = { downloadOffline = it }
                        )
                        Text(
                            text = "Cihaza indir (İnternetsiz izle)",
                            style = MaterialTheme.typography.bodySmall,
                            color = DeepInk
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VideoCategory.assignableCategories, key = { it.id }) { cat ->
                            FilterChip(
                                selected = selectedCat == cat,
                                onClick = { selectedCat = cat },
                                label = { Text(cat.labelTr) },
                                modifier = Modifier.tvFocusHighlight(RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = urlInput.isNotBlank(),
                    onClick = {
                        onAddCloudVideoUrl(
                            urlInput,
                            titleInput.ifBlank { null },
                            selectedCat,
                            downloadOffline
                        )
                        showCloudUrlDialog = false
                    },
                    modifier = Modifier
                        .tvFocusHighlight(RoundedCornerShape(20.dp))
                        .testTag("cloud_dialog_confirm")
                ) {
                    Text("Ekle")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCloudUrlDialog = false },
                    modifier = Modifier.tvFocusHighlight(RoundedCornerShape(20.dp))
                ) {
                    Text("İptal")
                }
            }
        )
    }

    // Import Remote JSON Playlist Dialog
    if (showJsonPlaylistDialog) {
        var jsonUrlInput by remember { mutableStateOf("") }
        var selectedCat by remember { mutableStateOf(importTargetCategory) }

        AlertDialog(
            onDismissRequest = { showJsonPlaylistDialog = false },
            title = { Text("Bulut JSON Video Listesi", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "İçinde [{\"title\":\"...\",\"url\":\"...\",\"category\":\"cartoon\"}] formatında video listesi bulunan JSON adresini girin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedInk
                    )
                    OutlinedTextField(
                        value = jsonUrlInput,
                        onValueChange = { jsonUrlInput = it },
                        label = { Text("JSON Liste URL") },
                        placeholder = { Text("https://.../playlist.json") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("json_playlist_url_input")
                    )
                    Text("Varsayılan Kategori", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VideoCategory.assignableCategories, key = { it.id }) { cat ->
                            FilterChip(
                                selected = selectedCat == cat,
                                onClick = { selectedCat = cat },
                                label = { Text(cat.labelTr) },
                                modifier = Modifier.tvFocusHighlight(RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = jsonUrlInput.isNotBlank(),
                    onClick = {
                        onImportJsonPlaylist(jsonUrlInput, selectedCat)
                        showJsonPlaylistDialog = false
                    },
                    modifier = Modifier
                        .tvFocusHighlight(RoundedCornerShape(20.dp))
                        .testTag("json_dialog_confirm")
                ) {
                    Text("İçe Aktar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showJsonPlaylistDialog = false },
                    modifier = Modifier.tvFocusHighlight(RoundedCornerShape(20.dp))
                ) {
                    Text("İptal")
                }
            }
        )
    }

    // Edit Video Title & Category Dialog
    editingVideo?.let { video ->
        var editedTitle by remember(video.id) { mutableStateOf(video.title) }
        var editedCategory by remember(video.id) { mutableStateOf(video.category) }

        AlertDialog(
            onDismissRequest = { editingVideo = null },
            title = { Text("Videoyu Düzenle", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editedTitle,
                        onValueChange = { editedTitle = it },
                        label = { Text("Video Başlığı") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Kategori", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VideoCategory.assignableCategories, key = { it.id }) { cat ->
                            FilterChip(
                                selected = editedCategory == cat,
                                onClick = { editedCategory = cat },
                                label = { Text(cat.labelTr) },
                                modifier = Modifier.tvFocusHighlight(RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateVideoDetails(video.id, editedTitle, editedCategory)
                        editingVideo = null
                    },
                    modifier = Modifier.tvFocusHighlight(RoundedCornerShape(20.dp))
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { editingVideo = null },
                    modifier = Modifier.tvFocusHighlight(RoundedCornerShape(20.dp))
                ) {
                    Text("İptal")
                }
            }
        )
    }

    // 4-Digit PIN Setup Dialog
    if (showPinDialog) {
        var pinInput by remember { mutableStateOf(uiState.lockSettings.parentPin) }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("4 Haneli Ebeveyn PIN'i", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Çocuk kilidini açmak için 4 rakam belirleyin. Boş bırakıp kaydederseniz matematik sorusu kullanılır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedInk
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(4)
                            pinInput = digits
                        },
                        label = { Text("PIN (4 rakam)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = pinInput.isEmpty() || pinInput.length == 4,
                    onClick = {
                        onSetParentPin(pinInput)
                        showPinDialog = false
                    },
                    modifier = Modifier.tvFocusHighlight(RoundedCornerShape(20.dp))
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPinDialog = false },
                    modifier = Modifier.tvFocusHighlight(RoundedCornerShape(20.dp))
                ) {
                    Text("İptal")
                }
            }
        )
    }
}

@Composable
private fun ParentVideoRow(
    video: VideoEntity,
    onDownloadOffline: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CrispWhite,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(video.category.containerColor),
                contentAlignment = Alignment.Center
            ) {
                val thumb = remember(video.thumbnailPath) {
                    video.thumbnailPath?.let { File(it) }?.takeIf { it.exists() }
                }
                val remoteThumb = video.remoteThumbnailUrl?.takeIf { it.isNotBlank() }
                if (thumb != null || remoteThumb != null) {
                    AsyncImage(
                        model = thumb ?: remoteThumb,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = video.category.icon,
                        contentDescription = null,
                        tint = video.category.accentColor
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = DeepInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val sourceLabel = if (video.isDownloadedOffline) {
                    "${video.source.labelTr} (Çevrimdışı)"
                } else {
                    video.source.labelTr
                }
                val durationSuffix = if (video.durationMs > 0L) " • ${video.formattedDuration()}" else ""
                Text(
                    text = "${video.category.labelTr} • $sourceLabel$durationSuffix",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedInk
                )
            }

            if (video.source == VideoSourceType.CLOUD_URL && !video.isDownloadedOffline) {
                IconButton(
                    onClick = onDownloadOffline,
                    modifier = Modifier.tvFocusHighlight(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDownload,
                        contentDescription = "Çevrimdışı İndir",
                        tint = MintGreen
                    )
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.tvFocusHighlight(CircleShape)
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

            IconButton(
                onClick = onEditClick,
                modifier = Modifier.tvFocusHighlight(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Düzenle",
                    tint = SkyBlue
                )
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.tvFocusHighlight(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Sil",
                    tint = CherryRed
                )
            }
        }
    }
}
