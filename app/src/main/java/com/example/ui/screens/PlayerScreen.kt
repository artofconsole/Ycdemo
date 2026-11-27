package com.example.ui.screens

import android.app.Activity
import android.content.res.Configuration
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.VideoEntity
import com.example.data.model.VideoCategory
import com.example.data.model.VideoSourceType
import com.example.ui.components.YouTubeEmbedPlayer
import com.example.ui.components.YouTubePlayerController
import com.example.ui.components.tvFocusHighlight
import com.example.ui.theme.CherryRed
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.TheaterBlack
import com.example.util.LockTaskHelper
import com.example.util.VideoLinkParser
import com.example.util.VideoRecommendationMixer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PlayerScreen(
    initialVideoId: Long,
    allVideos: List<VideoEntity>,
    mixSeed: Long = 0L,
    autoplayNext: Boolean,
    isKidLockEnabled: Boolean,
    onSavePlaybackPosition: (Long, Long) -> Unit,
    onIncrementPlayCount: (Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isTv = remember(context) { LockTaskHelper.isTvDevice(context) }
    val scope = rememberCoroutineScope()

    var currentVideoId by remember(initialVideoId) { mutableLongStateOf(initialVideoId) }
    val currentVideo = remember(currentVideoId, allVideos) {
        allVideos.firstOrNull { it.id == currentVideoId } ?: allVideos.firstOrNull()
    }

    val isYouTube = currentVideo?.source == VideoSourceType.YOUTUBE
    val youtubeVideoId = remember(currentVideo?.uriString, isYouTube) {
        val active = currentVideo
        if (isYouTube && active != null) {
            VideoLinkParser.extractYouTubeVideoId(active.uriString)
        } else {
            null
        }
    }

    val currentIndex = remember(currentVideo, allVideos) {
        allVideos.indexOfFirst { it.id == currentVideo?.id }.coerceAtLeast(0)
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var overlayInteractionTick by remember { mutableIntStateOf(0) }
    var youTubeHasError by remember(currentVideo?.id) { mutableStateOf(false) }
    var isTouchLocked by remember { mutableStateOf(false) }
    var touchLockHintVisible by remember { mutableStateOf(false) }
    var holdUnlockProgress by remember { mutableFloatStateOf(0f) }
    var isZoomFill by remember { mutableStateOf(false) }
    var autoplayCountdown by remember { mutableIntStateOf(0) }
    var drawerCategoryFilter by remember { mutableStateOf(VideoCategory.ALL) }
    var dpadUnlockJob by remember { mutableStateOf<Job?>(null) }

    val playerRootFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val drawerFirstCardFocusRequester = remember { FocusRequester() }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }
    val youtubeController = remember { YouTubePlayerController() }

    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.let {
                LockTaskHelper.setLandscape(it, false)
                if (!isKidLockEnabled) {
                    LockTaskHelper.setImmersiveMode(it, false)
                }
            }
        }
    }

    LaunchedEffect(activity) {
        activity?.let { LockTaskHelper.setImmersiveMode(it, true) }
    }

    LaunchedEffect(Unit) {
        runCatching {
            if (controlsVisible) {
                playPauseFocusRequester.requestFocus()
            } else {
                playerRootFocusRequester.requestFocus()
            }
        }
    }

    LaunchedEffect(currentVideo?.id) {
        val video = currentVideo ?: return@LaunchedEffect
        autoplayCountdown = 0
        currentPositionMs = video.lastPositionMs
        totalDurationMs = video.durationMs
        if (video.source == VideoSourceType.YOUTUBE) {
            exoPlayer.pause()
            exoPlayer.clearMediaItems()
            isPlaying = true
        } else {
            val playbackUri = video.effectivePlaybackUri().toUri()
            exoPlayer.setMediaItem(MediaItem.fromUri(playbackUri))
            exoPlayer.prepare()
            if (video.lastPositionMs > 2000L &&
                (video.durationMs <= 0L || video.lastPositionMs < video.durationMs - 5000L)
            ) {
                exoPlayer.seekTo(video.lastPositionMs)
            }
            exoPlayer.playWhenReady = true
        }
        onIncrementPlayCount(video.id)
    }

    fun seekToPositionMs(targetMs: Long) {
        val clamped = if (totalDurationMs > 0L) {
            targetMs.coerceIn(0L, totalDurationMs)
        } else {
            targetMs.coerceAtLeast(0L)
        }
        currentPositionMs = clamped
        if (isYouTube) {
            youtubeController.seekToMs(clamped)
        } else {
            exoPlayer.seekTo(clamped)
        }
    }

    fun togglePlayPause() {
        if (isYouTube) {
            if (isPlaying) {
                youtubeController.pause()
            } else {
                youtubeController.play()
            }
        } else {
            if (exoPlayer.isPlaying) {
                exoPlayer.pause()
            } else {
                if (exoPlayer.playbackState == Player.STATE_ENDED) {
                    exoPlayer.seekTo(0L)
                }
                exoPlayer.play()
            }
        }
    }

    fun openUnifiedOverlay(focusStripFirst: Boolean = false) {
        controlsVisible = true
        overlayInteractionTick++
        if (isTv) {
            scope.launch {
                delay(70L)
                runCatching {
                    if (focusStripFirst) {
                        drawerFirstCardFocusRequester.requestFocus()
                    } else {
                        playPauseFocusRequester.requestFocus()
                    }
                }
            }
        }
    }

    fun hideUnifiedOverlay() {
        controlsVisible = false
        runCatching { playerRootFocusRequester.requestFocus() }
    }

    fun switchToVideo(target: VideoEntity) {
        currentVideo?.let { prev ->
            val pos = if (prev.source == VideoSourceType.YOUTUBE) currentPositionMs else exoPlayer.currentPosition
            onSavePlaybackPosition(prev.id, pos)
        }
        autoplayCountdown = 0
        youTubeHasError = false
        isPlaying = true
        currentVideoId = target.id
        overlayInteractionTick++
    }

    fun playNextVideo() {
        if (allVideos.size <= 1) {
            seekToPositionMs(0L)
            if (isYouTube) youtubeController.play() else exoPlayer.play()
            return
        }
        val nextIndex = (currentIndex + 1) % allVideos.size
        switchToVideo(allVideos[nextIndex])
    }

    fun playPreviousVideo() {
        val pos = if (isYouTube) currentPositionMs else exoPlayer.currentPosition
        if (pos > 4000L || allVideos.size <= 1) {
            seekToPositionMs(0L)
            if (isYouTube) youtubeController.play() else exoPlayer.play()
            return
        }
        val prevIndex = if (currentIndex - 1 < 0) allVideos.lastIndex else currentIndex - 1
        switchToVideo(allVideos[prevIndex])
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                if (!isYouTube) {
                    isPlaying = playing
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (isYouTube) return
                if (playbackState == Player.STATE_READY) {
                    totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                } else if (playbackState == Player.STATE_ENDED) {
                    currentVideo?.let { onSavePlaybackPosition(it.id, 0L) }
                    if (autoplayNext && allVideos.size > 1) {
                        autoplayCountdown = 3
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            currentVideo?.let {
                val pos = if (it.source == VideoSourceType.YOUTUBE) currentPositionMs else exoPlayer.currentPosition
                onSavePlaybackPosition(it.id, pos)
            }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, isScrubbing, isYouTube) {
        while (true) {
            if (!isYouTube && !isScrubbing) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration
                if (dur > 0L) totalDurationMs = dur
            }
            delay(250L)
        }
    }

    // Auto-hide unified controls + Diğer Videolar after 5.5s of inactivity when playing
    LaunchedEffect(controlsVisible, isPlaying, isTouchLocked, isScrubbing, overlayInteractionTick) {
        if (controlsVisible && isPlaying && !isTouchLocked && !isScrubbing) {
            delay(5500L)
            hideUnifiedOverlay()
        }
    }

    LaunchedEffect(touchLockHintVisible) {
        if (touchLockHintVisible) {
            delay(3000L)
            if (holdUnlockProgress == 0f) {
                touchLockHintVisible = false
            }
        }
    }

    LaunchedEffect(autoplayCountdown) {
        if (autoplayCountdown > 0) {
            delay(1000L)
            if (autoplayCountdown == 1) {
                autoplayCountdown = 0
                playNextVideo()
            } else if (autoplayCountdown > 1) {
                autoplayCountdown -= 1
            }
        }
    }

    BackHandler {
        if (isTouchLocked) {
            touchLockHintVisible = true
        } else if (isTv && controlsVisible) {
            hideUnifiedOverlay()
        } else {
            onBack()
        }
    }

    val drawerVideos = remember(allVideos, drawerCategoryFilter, currentVideo?.id, mixSeed) {
        val categoryFiltered = when (drawerCategoryFilter) {
            VideoCategory.ALL -> allVideos
            VideoCategory.FAVORITES -> allVideos.filter { it.isFavorite }
            else -> allVideos.filter { it.categoryId == drawerCategoryFilter.id }
        }
        val candidates = if (currentVideo != null && categoryFiltered.size > 1) {
            categoryFiltered.filterNot { it.id == currentVideo.id }
        } else {
            categoryFiltered
        }
        VideoRecommendationMixer.mixVideosForDiscovery(
            videos = candidates,
            seed = mixSeed xor (currentVideo?.id ?: 0L),
            currentPlayingVideo = currentVideo
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TheaterBlack)
            .focusRequester(playerRootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                val native = keyEvent.nativeKeyEvent
                val code = native.keyCode

                // Handle Touch/Remote Lock hold-to-unlock with OK / Enter key on TV remotes
                if (isTouchLocked) {
                    val isSelectKey = code == KeyEvent.KEYCODE_DPAD_CENTER ||
                        code == KeyEvent.KEYCODE_ENTER ||
                        code == KeyEvent.KEYCODE_NUMPAD_ENTER
                    if (isSelectKey) {
                        if (keyEvent.type == KeyEventType.KeyDown && dpadUnlockJob == null) {
                            touchLockHintVisible = true
                            dpadUnlockJob = scope.launch {
                                val steps = 30
                                for (i in 1..steps) {
                                    delay(50L)
                                    holdUnlockProgress = i / steps.toFloat()
                                }
                                isTouchLocked = false
                                holdUnlockProgress = 0f
                                touchLockHintVisible = false
                                controlsVisible = true
                                dpadUnlockJob = null
                            }
                        } else if (keyEvent.type == KeyEventType.KeyUp) {
                            dpadUnlockJob?.cancel()
                            dpadUnlockJob = null
                            holdUnlockProgress = 0f
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (keyEvent.type == KeyEventType.KeyDown && code != KeyEvent.KEYCODE_VOLUME_UP &&
                        code != KeyEvent.KEYCODE_VOLUME_DOWN && code != KeyEvent.KEYCODE_VOLUME_MUTE
                    ) {
                        touchLockHintVisible = true
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }

                // Normal TV Remote / D-Pad Handling
                if (keyEvent.type == KeyEventType.KeyDown) {
                    if (controlsVisible) {
                        overlayInteractionTick++
                    }
                    when (code) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                            togglePlayPause()
                            openUnifiedOverlay()
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            if (isYouTube) youtubeController.play() else exoPlayer.play()
                            openUnifiedOverlay()
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            if (isYouTube) youtubeController.pause() else exoPlayer.pause()
                            openUnifiedOverlay()
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            playNextVideo()
                            openUnifiedOverlay(focusStripFirst = true)
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            playPreviousVideo()
                            openUnifiedOverlay(focusStripFirst = true)
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            seekToPositionMs(currentPositionMs - 10_000L)
                            openUnifiedOverlay()
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            seekToPositionMs(currentPositionMs + 10_000L)
                            openUnifiedOverlay()
                            return@onPreviewKeyEvent true
                        }
                    }

                    if (!controlsVisible) {
                        when (code) {
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                openUnifiedOverlay(focusStripFirst = true)
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
                                openUnifiedOverlay(focusStripFirst = false)
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                seekToPositionMs(currentPositionMs - 10_000L)
                                openUnifiedOverlay()
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                seekToPositionMs(currentPositionMs + 10_000L)
                                openUnifiedOverlay()
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                togglePlayPause()
                                openUnifiedOverlay()
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                }
                false
            }
            .pointerInput(isTouchLocked, controlsVisible) {
                detectTapGestures(
                    onTap = {
                        if (isTouchLocked) {
                            touchLockHintVisible = true
                        } else if (controlsVisible) {
                            hideUnifiedOverlay()
                        } else {
                            openUnifiedOverlay()
                        }
                    }
                )
            }
    ) {
        if (isYouTube && youtubeVideoId != null && currentVideo != null) {
            YouTubeEmbedPlayer(
                videoId = youtubeVideoId,
                startPositionMs = currentVideo.lastPositionMs,
                controller = youtubeController,
                onPlayingChanged = { playing -> isPlaying = playing },
                onProgressChanged = { posMs, durMs ->
                    if (!isScrubbing) {
                        currentPositionMs = posMs
                        if (durMs > 0L) totalDurationMs = durMs
                    }
                },
                onEnded = {
                    onSavePlaybackPosition(currentVideo.id, 0L)
                    if (autoplayNext && allVideos.size > 1) {
                        autoplayCountdown = 3
                    }
                },
                onPlayNextRequested = { playNextVideo() },
                onBackRequested = onBack,
                onHasErrorChanged = { hasErr ->
                    youTubeHasError = hasErr
                    if (hasErr) controlsVisible = false
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (isYouTube) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TheaterBlack)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Geçersiz YouTube video bağlantısı.",
                    style = MaterialTheme.typography.titleLarge,
                    color = CrispWhite
                )
            }
        } else {
            // Keep ContentFrame mounted for Local & Cloud direct URL videos
            ContentFrame(
                player = exoPlayer,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (isZoomFill) ContentScale.Crop else ContentScale.Fit
            )
        }

        // Single-tap capture layer above WebView so tapping once anywhere during YouTube playback
        // reliably opens both controls and "Diğer Videolar" together (disabled during YouTube error overlay).
        if (isYouTube && !controlsVisible && !isTouchLocked && !youTubeHasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { openUnifiedOverlay() }
                        )
                    }
            )
        }

        // Unified YouTube Kids-style Overlay: Controls + "Diğer Videolar" shown together on single tap
        AnimatedVisibility(
            visible = controlsVisible && !isTouchLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.68f),
                                Color.Black.copy(alpha = 0.30f),
                                Color.Black.copy(alpha = 0.52f),
                                Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { hideUnifiedOverlay() }
                    )
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                val isCompactHeight = maxHeight < 460.dp
                val horizontalEdgePadding = if (isTv) 28.dp else 16.dp
                val stripCardWidth = when {
                    isTv -> 196.dp
                    isCompactHeight -> 136.dp
                    else -> 168.dp
                }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. Top Player Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = horizontalEdgePadding,
                                vertical = if (isCompactHeight) 6.dp else if (isTv) 18.dp else 10.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                onClick = onBack,
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .size(if (isCompactHeight) 42.dp else 48.dp)
                                    .tvFocusHighlight(CircleShape, focusBorderColor = CrispWhite, focusedScale = 1.1f)
                                    .testTag("player_back_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = stringResource(R.string.cd_back),
                                        tint = CrispWhite
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentVideo?.title ?: "",
                                    style = if (isCompactHeight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                                    color = CrispWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                currentVideo?.let { vid ->
                                    Text(
                                        text = "${vid.category.labelTr} • ${vid.source.labelTr}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = vid.category.accentColor
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val lockBtnShape = RoundedCornerShape(22.dp)
                            Surface(
                                onClick = {
                                    isTouchLocked = true
                                    controlsVisible = false
                                    touchLockHintVisible = true
                                    runCatching { playerRootFocusRequester.requestFocus() }
                                },
                                shape = lockBtnShape,
                                color = Color.Black.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .height(if (isCompactHeight) 42.dp else 48.dp)
                                    .tvFocusHighlight(lockBtnShape, focusBorderColor = CrispWhite, focusedScale = 1.08f)
                                    .testTag("player_touch_lock_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = stringResource(R.string.cd_touch_lock),
                                        tint = CrispWhite,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Kilitle",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = CrispWhite
                                    )
                                }
                            }

                            if (!isYouTube) {
                                Surface(
                                    onClick = {
                                        isZoomFill = !isZoomFill
                                        overlayInteractionTick++
                                    },
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.55f),
                                    modifier = Modifier
                                        .size(if (isCompactHeight) 42.dp else 48.dp)
                                        .tvFocusHighlight(CircleShape, focusBorderColor = CrispWhite, focusedScale = 1.1f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.AspectRatio,
                                            contentDescription = stringResource(R.string.cd_fullscreen_toggle),
                                            tint = CrispWhite
                                        )
                                    }
                                }
                            }

                            if (!isTv) {
                                Surface(
                                    onClick = {
                                        val isCurrentlyLandscape =
                                            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                                        activity?.let { LockTaskHelper.setLandscape(it, !isCurrentlyLandscape) }
                                        overlayInteractionTick++
                                    },
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.55f),
                                    modifier = Modifier
                                        .size(if (isCompactHeight) 42.dp else 48.dp)
                                        .tvFocusHighlight(CircleShape, focusBorderColor = CrispWhite, focusedScale = 1.1f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.ScreenRotation,
                                            contentDescription = stringResource(R.string.cd_orientation_toggle),
                                            tint = CrispWhite
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Center Play / Pause / Skip / Seek Controls
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (isTv) 24.dp else if (isCompactHeight) 14.dp else 16.dp)
                        ) {
                            val sideBtnSize = if (isCompactHeight) 44.dp else 54.dp
                            val sideIconSize = if (isCompactHeight) 24.dp else 30.dp
                            val playBtnSize = when {
                                isTv -> 80.dp
                                isCompactHeight -> 58.dp
                                else -> 72.dp
                            }

                            IconButton(
                                onClick = {
                                    playPreviousVideo()
                                    overlayInteractionTick++
                                },
                                modifier = Modifier
                                    .size(sideBtnSize)
                                    .tvFocusHighlight(CircleShape, focusBorderColor = CrispWhite, focusedScale = 1.12f)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .testTag("player_prev_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipPrevious,
                                    contentDescription = stringResource(R.string.cd_previous_video),
                                    tint = CrispWhite,
                                    modifier = Modifier.size(sideIconSize)
                                )
                            }

                            IconButton(
                                onClick = {
                                    seekToPositionMs(currentPositionMs - 10_000L)
                                    overlayInteractionTick++
                                },
                                modifier = Modifier
                                    .size(sideBtnSize)
                                    .tvFocusHighlight(CircleShape, focusBorderColor = CrispWhite, focusedScale = 1.12f)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .testTag("player_replay_10_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Replay10,
                                    contentDescription = stringResource(R.string.cd_seek_back),
                                    tint = CrispWhite,
                                    modifier = Modifier.size(sideIconSize)
                                )
                            }

                            Surface(
                                onClick = {
                                    togglePlayPause()
                                    overlayInteractionTick++
                                },
                                shape = CircleShape,
                                color = CherryRed,
                                shadowElevation = 10.dp,
                                modifier = Modifier
                                    .size(playBtnSize)
                                    .focusRequester(playPauseFocusRequester)
                                    .tvFocusHighlight(CircleShape, focusBorderColor = CrispWhite, borderWidth = 4.dp, focusedScale = 1.1f)
                                    .testTag("player_play_pause_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = stringResource(R.string.cd_play_pause),
                                        tint = CrispWhite,
                                        modifier = Modifier.size(if (isCompactHeight) 34.dp else 44.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    seekToPositionMs(currentPositionMs + 10_000L)
                                    overlayInteractionTick++
                                },
                                modifier = Modifier
                                    .size(sideBtnSize)
                                    .tvFocusHighlight(CircleShape, focusBorderColor = CrispWhite, focusedScale = 1.12f)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .testTag("player_forward_10_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Forward10,
                                    contentDescription = stringResource(R.string.cd_seek_forward),
                                    tint = CrispWhite,
                                    modifier = Modifier.size(sideIconSize)
                                )
                            }

                            IconButton(
                                onClick = {
                                    playNextVideo()
                                    overlayInteractionTick++
                                },
                                modifier = Modifier
                                    .size(sideBtnSize)
                                    .tvFocusHighlight(CircleShape, focusBorderColor = CrispWhite, focusedScale = 1.12f)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .testTag("player_next_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = stringResource(R.string.cd_next_video),
                                    tint = CrispWhite,
                                    modifier = Modifier.size(sideIconSize)
                                )
                            }
                        }
                    }

                    // 3. Scrubber + YouTube Kids-Style "Diğer Videolar" Strip Together
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (isCompactHeight) 6.dp else 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = horizontalEdgePadding)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatMillis(currentPositionMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CrispWhite
                                )
                                Text(
                                    text = formatMillis(totalDurationMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CrispWhite.copy(alpha = 0.85f)
                                )
                            }
                            Slider(
                                value = if (totalDurationMs > 0L) {
                                    (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    0f
                                },
                                onValueChange = { fraction ->
                                    isScrubbing = true
                                    currentPositionMs = (fraction * totalDurationMs).toLong()
                                    overlayInteractionTick++
                                },
                                onValueChangeFinished = {
                                    seekToPositionMs(currentPositionMs)
                                    isScrubbing = false
                                    overlayInteractionTick++
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = CherryRed,
                                    activeTrackColor = CherryRed,
                                    inactiveTrackColor = CrispWhite.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // "Diğer Videolar" Header + Inline Category Filter Pills
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = horizontalEdgePadding)
                                .padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.other_videos_title),
                                    style = if (isCompactHeight) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                                    color = CrispWhite
                                )
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = CherryRed
                                ) {
                                    Text(
                                        text = "${drawerVideos.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CrispWhite,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(VideoCategory.entries, key = { _, cat -> cat.id }) { _, cat ->
                                    val selected = cat == drawerCategoryFilter
                                    val pillShape = RoundedCornerShape(14.dp)
                                    Surface(
                                        onClick = {
                                            drawerCategoryFilter = cat
                                            overlayInteractionTick++
                                        },
                                        shape = pillShape,
                                        color = if (selected) cat.accentColor else CrispWhite.copy(alpha = 0.16f),
                                        modifier = Modifier.tvFocusHighlight(
                                            shape = pillShape,
                                            focusBorderColor = CrispWhite,
                                            focusedScale = 1.07f
                                        )
                                    ) {
                                        Text(
                                            text = cat.labelTr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CrispWhite,
                                            modifier = Modifier.padding(
                                                horizontal = if (isCompactHeight) 10.dp else 12.dp,
                                                vertical = if (isCompactHeight) 4.dp else 6.dp
                                            )
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { hideUnifiedOverlay() },
                                modifier = Modifier
                                    .size(34.dp)
                                    .tvFocusHighlight(CircleShape, focusBorderColor = CrispWhite)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Gizle",
                                    tint = CrispWhite
                                )
                            }
                        }

                        // Horizontal Video Strip
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = horizontalEdgePadding),
                            horizontalArrangement = Arrangement.spacedBy(if (isCompactHeight) 10.dp else 14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("other_videos_carousel")
                        ) {
                            itemsIndexed(drawerVideos, key = { _, item -> item.id }) { index, item ->
                                val isCurrent = item.id == currentVideo?.id
                                DrawerVideoCard(
                                    video = item,
                                    isPlayingNow = isCurrent,
                                    cardWidth = stripCardWidth,
                                    compactTitle = isCompactHeight,
                                    modifier = if (index == 0) {
                                        Modifier.focusRequester(drawerFirstCardFocusRequester)
                                    } else {
                                        Modifier
                                    },
                                    onClick = { switchToVideo(item) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Autoplay Next Countdown Overlay
        if (autoplayCountdown > 0 && allVideos.size > 1) {
            val nextVideo = allVideos[(currentIndex + 1) % allVideos.size]
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.78f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Sonraki Video $autoplayCountdown sn içinde başlıyor...",
                        style = MaterialTheme.typography.titleLarge,
                        color = CrispWhite
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = nextVideo.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = SkyBlue
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { autoplayCountdown = 0 },
                            border = BorderStroke(1.dp, CrispWhite),
                            modifier = Modifier.tvFocusHighlight(RoundedCornerShape(20.dp), focusBorderColor = CrispWhite)
                        ) {
                            Text("İptal", color = CrispWhite)
                        }
                        Button(
                            onClick = {
                                autoplayCountdown = 0
                                playNextVideo()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CherryRed),
                            modifier = Modifier.tvFocusHighlight(RoundedCornerShape(20.dp), focusBorderColor = CrispWhite)
                        ) {
                            Text("Hemen Oynat", color = CrispWhite)
                        }
                    }
                }
            }
        }

        // Touch / Remote Lock Hold-To-Unlock Overlay
        if (isTouchLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        touchLockHintVisible = true
                    }
            ) {
                AnimatedVisibility(
                    visible = touchLockHintVisible || holdUnlockProgress > 0f,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = Color.Black.copy(alpha = 0.84f),
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        val job = scope.launch {
                                            val steps = 30
                                            for (i in 1..steps) {
                                                delay(50L)
                                                holdUnlockProgress = i / steps.toFloat()
                                            }
                                            isTouchLocked = false
                                            holdUnlockProgress = 0f
                                            touchLockHintVisible = false
                                            controlsVisible = true
                                        }
                                        tryAwaitRelease()
                                        if (job.isActive) {
                                            job.cancel()
                                            holdUnlockProgress = 0f
                                        }
                                    }
                                )
                            }
                            .testTag("touch_lock_unlock_button")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { holdUnlockProgress },
                                    modifier = Modifier.size(68.dp),
                                    color = CherryRed,
                                    trackColor = CrispWhite.copy(alpha = 0.25f),
                                    strokeWidth = 5.dp
                                )
                                Icon(
                                    imageVector = if (holdUnlockProgress > 0.85f) {
                                        Icons.Rounded.LockOpen
                                    } else {
                                        Icons.Rounded.Lock
                                    },
                                    contentDescription = stringResource(R.string.cd_touch_lock),
                                    tint = CrispWhite,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (isTv) "Kumanda Kilidi Açık" else "Dokunma Kilidi Açık",
                                style = MaterialTheme.typography.titleMedium,
                                color = CrispWhite
                            )
                            Text(
                                text = if (isTv) {
                                    "Kilidi açmak için OK tuşuna 2 sn basılı tutun"
                                } else {
                                    "Kilidi açmak için basılı tutun"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = CrispWhite.copy(alpha = 0.78f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerVideoCard(
    video: VideoEntity,
    isPlayingNow: Boolean,
    cardWidth: Dp,
    compactTitle: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        shape = cardShape,
        color = Color.White.copy(alpha = 0.12f),
        border = if (isPlayingNow) BorderStroke(2.5.dp, CherryRed) else null,
        modifier = modifier
            .width(cardWidth)
            .tvFocusHighlight(
                shape = cardShape,
                focusBorderColor = CrispWhite,
                borderWidth = 3.5.dp,
                focusedScale = 1.07f
            )
            .testTag("drawer_video_card_${video.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(video.category.containerColor)
            ) {
                val thumbFile = remember(video.thumbnailPath) {
                    video.thumbnailPath?.let { File(it) }?.takeIf { it.exists() }
                }
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
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = video.category.icon,
                            contentDescription = null,
                            tint = video.category.accentColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                if (isPlayingNow) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CherryRed,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "Oynatılıyor",
                            style = MaterialTheme.typography.labelSmall,
                            color = CrispWhite,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                if (video.durationMs > 0L) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = video.formattedDuration(),
                            style = MaterialTheme.typography.labelSmall,
                            color = CrispWhite,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = video.title,
                style = MaterialTheme.typography.titleSmall,
                color = CrispWhite,
                maxLines = if (compactTitle) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    horizontal = 10.dp,
                    vertical = if (compactTitle) 6.dp else 8.dp
                )
            )
        }
    }
}

private fun formatMillis(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
