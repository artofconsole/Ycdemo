package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.auth.GoogleAuthHelper
import com.example.ui.MinikTvViewModel
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.ParentRoute
import com.example.ui.navigation.PlayerRoute
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ParentScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.MinikTVTheme
import com.example.util.LockTaskHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MinikTVTheme {
                val viewModel: MinikTvViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val navController = rememberNavController()
                val lifecycleOwner = LocalLifecycleOwner.current

                // Observe FirebaseAuth state and gate Firestore sync strictly behind authenticated uid
                var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
                DisposableEffect(Unit) {
                    val authListener = FirebaseAuth.AuthStateListener { auth ->
                        currentUser = auth.currentUser
                    }
                    Firebase.auth.addAuthStateListener(authListener)
                    onDispose {
                        Firebase.auth.removeAuthStateListener(authListener)
                    }
                }

                // Silent Credential Manager auto-sign-in for returning parents
                LaunchedEffect(Unit) {
                    if (Firebase.auth.currentUser == null) {
                        GoogleAuthHelper.attemptAutoSignIn(this@MainActivity)
                    }
                }

                val signedInUser = currentUser
                if (signedInUser != null) {
                    val uid = signedInUser.uid
                    key(uid) {
                        DisposableEffect(uid) {
                            viewModel.attachCloudSyncForUser(
                                uid = uid,
                                email = signedInUser.email,
                                displayName = signedInUser.displayName
                            )
                            onDispose {
                                viewModel.detachCloudSync()
                            }
                        }
                    }
                }

                // Keep immersive mode & system pin status synchronized across lifecycle transitions
                DisposableEffect(lifecycleOwner, uiState.lockSettings.isKidLockEnabled) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            viewModel.refreshSystemPinStatus(this@MainActivity)
                            if (uiState.lockSettings.isKidLockEnabled || LockTaskHelper.isTvDevice(this@MainActivity)) {
                                LockTaskHelper.setImmersiveMode(this@MainActivity, true)
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                // If sleep timer expires while watching a video, return to Home so lock screen takes over
                LaunchedEffect(uiState.isSleepTimeExpired) {
                    if (uiState.isSleepTimeExpired) {
                        navController.popBackStack(HomeRoute, inclusive = false)
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = HomeRoute
                ) {
                    composable<HomeRoute> {
                        HomeScreen(
                            uiState = uiState,
                            onSelectCategory = viewModel::selectCategory,
                            onPlayVideo = { video ->
                                navController.navigate(PlayerRoute(videoId = video.id))
                            },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onImportVideos = { uris -> viewModel.importVideos(uris) },
                            onImportFolderTree = { treeUri ->
                                viewModel.importFromFolderTree(treeUri)
                            },
                            onScanAppMoviesFolder = { viewModel.scanAppMoviesFolders() },
                            onEnableKidLock = viewModel::enableKidLock,
                            onDisableKidLock = viewModel::disableKidLock,
                            onResetSleepTimer = { viewModel.setSleepTimer(0) },
                            onOpenParentPanel = { navController.navigate(ParentRoute) },
                            onShowMessage = viewModel::showTemporaryMessage
                        )
                    }

                    composable<PlayerRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<PlayerRoute>()
                        PlayerScreen(
                            initialVideoId = route.videoId,
                            allVideos = uiState.allVideos,
                            autoplayNext = uiState.lockSettings.autoplayNext,
                            isKidLockEnabled = uiState.lockSettings.isKidLockEnabled,
                            onSavePlaybackPosition = viewModel::updatePlaybackPosition,
                            onIncrementPlayCount = viewModel::incrementPlayCount,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<ParentRoute> {
                        ParentScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onImportVideos = { uris, category ->
                                viewModel.importVideos(uris, category)
                            },
                            onImportFolderTree = { treeUri, category ->
                                viewModel.importFromFolderTree(treeUri, category)
                            },
                            onScanAppMoviesFolder = { category ->
                                viewModel.scanAppMoviesFolders(category)
                            },
                            onAddYouTubeVideo = viewModel::addYouTubeVideo,
                            onAddCloudVideoUrl = viewModel::addCloudVideoUrl,
                            onImportJsonPlaylist = viewModel::importJsonPlaylist,
                            onDownloadCloudVideoOffline = viewModel::downloadCloudVideoOffline,
                            onGoogleSignIn = { viewModel.signInWithGoogle(this@MainActivity) },
                            onGoogleSignOut = viewModel::signOutGoogle,
                            onSyncNow = viewModel::syncNow,
                            onStartLocalLinkReceiver = viewModel::startLocalLinkReceiver,
                            onStopLocalLinkReceiver = viewModel::stopLocalLinkReceiver,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onEnableKidLock = viewModel::enableKidLock,
                            onDisableKidLock = viewModel::disableKidLock,
                            onRequestSystemPinning = viewModel::requestSystemPinningAgain,
                            onSetParentPin = viewModel::setParentPin,
                            onSetAutoplayNext = viewModel::setAutoplayNext,
                            onSetSleepTimer = viewModel::setSleepTimer,
                            onUpdateVideoDetails = viewModel::updateVideoDetails,
                            onDeleteVideo = viewModel::deleteVideo,
                            onShowMessage = viewModel::showTemporaryMessage
                        )
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && (LockTaskHelper.isSystemPinned(this) || LockTaskHelper.isTvDevice(this))) {
            LockTaskHelper.setImmersiveMode(this, true)
        }
    }
}
