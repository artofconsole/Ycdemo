package com.example.ui

import android.app.Activity
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.GoogleAuthHelper
import com.example.data.local.MinikTvDatabase
import com.example.data.local.VideoEntity
import com.example.data.model.VideoCategory
import com.example.data.model.VideoSourceType
import com.example.data.preferences.KidLockPreferences
import com.example.data.preferences.KidLockSettings
import com.example.data.remote.CloudSyncRepository
import com.example.data.repository.VideoRepository
import com.example.util.LocalLinkReceiverServer
import com.example.util.LockTaskHelper
import com.example.util.RemoteIncomingLink
import com.example.util.VideoRecommendationMixer
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CloudAccountUiState(
    val uid: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val isSyncing: Boolean = false,
    val isSigningIn: Boolean = false,
    val syncError: String? = null
) {
    val isSignedIn: Boolean get() = !uid.isNullOrBlank()
}

data class MinikTvUiState(
    val allVideos: List<VideoEntity> = emptyList(),
    val libraryVideos: List<VideoEntity> = emptyList(),
    val filteredVideos: List<VideoEntity> = emptyList(),
    val selectedCategory: VideoCategory = VideoCategory.ALL,
    val searchQuery: String = "",
    val mixSeed: Long = 0L,
    val lockSettings: KidLockSettings = KidLockSettings(),
    val isSystemPinned: Boolean = false,
    val remainingTimerSeconds: Long = 0L,
    val isSleepTimeExpired: Boolean = false,
    val isImporting: Boolean = false,
    val statusBannerMessage: String? = null,
    val appMoviesFolderPath: String = "",
    val cloudAccount: CloudAccountUiState = CloudAccountUiState(),
    val localReceiverUrl: String? = null
)

class MinikTvViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MinikTvDatabase.getInstance(application)
    private val repository = VideoRepository(application, database.videoDao())
    private val cloudSyncRepository = CloudSyncRepository(application)
    private val preferences = KidLockPreferences(application)

    private val selectedCategoryFlow = MutableStateFlow(VideoCategory.ALL)
    private val searchQueryFlow = MutableStateFlow("")
    private val mixSeedFlow = MutableStateFlow(Random.nextLong())
    private val isSystemPinnedFlow = MutableStateFlow(false)
    private val isImportingFlow = MutableStateFlow(false)
    private val statusMessageFlow = MutableStateFlow<String?>(null)
    private val currentTimeMsFlow = MutableStateFlow(System.currentTimeMillis())
    private val cloudAccountFlow = MutableStateFlow(CloudAccountUiState())
    private val localReceiverUrlFlow = MutableStateFlow<String?>(null)

    private var activeCloudSyncJob: Job? = null
    private var activeObservedUid: String? = null

    private val localLinkReceiverServer = LocalLinkReceiverServer(
        scope = viewModelScope,
        onReceiveLink = { incoming -> handleIncomingPhoneLink(incoming) }
    )

    private val mixedVideosFlow: Flow<Pair<List<VideoEntity>, List<VideoEntity>>> = combine(
        repository.allVideos,
        mixSeedFlow
    ) { rawVideos, mixSeed ->
        val mixed = VideoRecommendationMixer.mixVideosForDiscovery(
            videos = rawVideos,
            seed = mixSeed
        )
        rawVideos to mixed
    }.flowOn(Dispatchers.Default)

    private val filteredVideosFlow: Flow<Triple<List<VideoEntity>, List<VideoEntity>, List<VideoEntity>>> =
        combine(
            mixedVideosFlow,
            selectedCategoryFlow,
            searchQueryFlow
        ) { (rawVideos, mixedVideos), category, searchQuery ->
            val categoryFiltered = when (category) {
                VideoCategory.ALL -> mixedVideos
                VideoCategory.FAVORITES -> mixedVideos.filter { it.isFavorite }
                else -> mixedVideos.filter { it.categoryId == category.id }
            }
            val filtered = if (searchQuery.isBlank()) {
                categoryFiltered
            } else {
                categoryFiltered.filter { VideoRecommendationMixer.matchesSearchQuery(it, searchQuery) }
            }
            Triple(rawVideos, mixedVideos, filtered)
        }.flowOn(Dispatchers.Default)

    private val sleepTimerStatusFlow: Flow<Pair<Long, Boolean>> = combine(
        preferences.settingsFlow,
        currentTimeMsFlow
    ) { settings, nowMs ->
        val hasTimer = settings.sleepTimerDurationMinutes > 0 && settings.sleepTimerEndTimestampMs > 0L
        val remainingSec = if (hasTimer) {
            ((settings.sleepTimerEndTimestampMs - nowMs) / 1000L).coerceAtLeast(0L)
        } else {
            0L
        }
        val expired = hasTimer && nowMs >= settings.sleepTimerEndTimestampMs
        remainingSec to expired
    }.distinctUntilChanged()

    val uiState: StateFlow<MinikTvUiState> = combine(
        filteredVideosFlow,
        selectedCategoryFlow,
        searchQueryFlow,
        mixSeedFlow,
        preferences.settingsFlow,
        isSystemPinnedFlow,
        isImportingFlow,
        statusMessageFlow,
        sleepTimerStatusFlow,
        cloudAccountFlow,
        localReceiverUrlFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val videoTriplet = values[0] as Triple<List<VideoEntity>, List<VideoEntity>, List<VideoEntity>>
        val category = values[1] as VideoCategory
        val searchQuery = values[2] as String
        val mixSeed = values[3] as Long
        val settings = values[4] as KidLockSettings
        val systemPinned = values[5] as Boolean
        val importing = values[6] as Boolean
        val statusMessage = values[7] as String?
        @Suppress("UNCHECKED_CAST")
        val timerPair = values[8] as Pair<Long, Boolean>
        val cloudAccount = values[9] as CloudAccountUiState
        val receiverUrl = values[10] as String?

        MinikTvUiState(
            allVideos = videoTriplet.second,
            libraryVideos = videoTriplet.first,
            filteredVideos = videoTriplet.third,
            selectedCategory = category,
            searchQuery = searchQuery,
            mixSeed = mixSeed,
            lockSettings = settings,
            isSystemPinned = systemPinned,
            remainingTimerSeconds = timerPair.first,
            isSleepTimeExpired = timerPair.second,
            isImporting = importing,
            statusBannerMessage = statusMessage,
            appMoviesFolderPath = repository.getAppMoviesFolderPath(),
            cloudAccount = cloudAccount,
            localReceiverUrl = receiverUrl
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MinikTvUiState()
    )

    init {
        viewModelScope.launch {
            preferences.settingsFlow.collectLatest { settings ->
                if (settings.sleepTimerDurationMinutes > 0 && settings.sleepTimerEndTimestampMs > 0L) {
                    while (true) {
                        currentTimeMsFlow.value = System.currentTimeMillis()
                        delay(1000L)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        localLinkReceiverServer.stop()
        super.onCleared()
    }

    fun startLocalLinkReceiver() {
        val url = localLinkReceiverServer.start()
        localReceiverUrlFlow.value = url
        if (url == null) {
            showTemporaryMessage("Yerel Wi-Fi sunucusu başlatılamadı.")
        }
    }

    fun stopLocalLinkReceiver() {
        localLinkReceiverServer.stop()
        localReceiverUrlFlow.value = null
    }

    private suspend fun handleIncomingPhoneLink(incoming: RemoteIncomingLink): Result<String> {
        return when (incoming.mode) {
            "youtube" -> {
                val ytId = com.example.util.VideoLinkParser.extractYouTubeVideoId(incoming.url)
                val playlistId = com.example.util.VideoLinkParser.extractYouTubePlaylistId(incoming.url)
                if (ytId == null && playlistId != null) {
                    importYouTubePlaylistInternal(incoming.url, incoming.category, fromPhone = true)
                } else {
                    val res = repository.addYouTubeVideo(incoming.url, incoming.title, incoming.category)
                    res.map { entity ->
                        if (Firebase.auth.currentUser != null) {
                            cloudSyncRepository.upsertCloudVideo(
                                title = entity.title,
                                sourceType = VideoSourceType.YOUTUBE,
                                videoUrl = entity.uriString,
                                thumbnailUrl = entity.remoteThumbnailUrl.orEmpty(),
                                category = entity.category,
                                favorite = entity.isFavorite,
                                durationMs = entity.durationMs,
                                existingDocId = entity.cloudDocId
                            ).onSuccess { repository.markSyncedFromCloud(entity.id, true) }
                        }
                        showTemporaryMessage("Telefondan YouTube videosu geldi: ${entity.title}")
                        "Eklendi: ${entity.title}"
                    }
                }
            }
            "youtube_playlist" -> {
                importYouTubePlaylistInternal(incoming.url, incoming.category, fromPhone = true)
            }
            "json" -> {
                val res = repository.importJsonPlaylist(incoming.url, incoming.category)
                res.map { list ->
                    syncOnlineListInBackground(list)
                    val msg = if (list.isNotEmpty()) "${list.size} video TV'ye eklendi!" else "Tüm videolar zaten mevcut."
                    showTemporaryMessage("Telefondan liste alındı: $msg")
                    msg
                }
            }
            else -> {
                val res = repository.addCloudVideoUrl(
                    incoming.url,
                    incoming.title,
                    incoming.category,
                    incoming.downloadOffline
                )
                res.map { entity ->
                    if (Firebase.auth.currentUser != null) {
                        cloudSyncRepository.upsertCloudVideo(
                            title = entity.title,
                            sourceType = VideoSourceType.CLOUD_URL,
                            videoUrl = entity.uriString,
                            thumbnailUrl = entity.remoteThumbnailUrl.orEmpty(),
                            category = entity.category,
                            favorite = entity.isFavorite,
                            durationMs = entity.durationMs,
                            existingDocId = entity.cloudDocId
                        ).onSuccess { repository.markSyncedFromCloud(entity.id, true) }
                    }
                    showTemporaryMessage("Telefondan bulut videosu geldi: ${entity.title}")
                    "Eklendi: ${entity.title}"
                }
            }
        }
    }

    /**
     * Auth-gated cloud sync lifecycle: only attaches Firestore snapshots() when uid != null.
     * Called from key(uid) DisposableEffect in MainActivity.
     */
    fun attachCloudSyncForUser(uid: String, email: String?, displayName: String?) {
        if (uid.isBlank()) return
        if (activeObservedUid == uid && activeCloudSyncJob?.isActive == true) return

        activeCloudSyncJob?.cancel()
        activeObservedUid = uid
        cloudAccountFlow.value = CloudAccountUiState(
            uid = uid,
            email = email,
            displayName = displayName,
            isSyncing = true,
            syncError = null
        )

        activeCloudSyncJob = viewModelScope.launch {
            pushPendingLocalOnlineVideos()

            cloudSyncRepository.observeCloudVideos(uid).collect { result ->
                result.onSuccess { remoteVideos ->
                    repository.reconcileCloudVideos(remoteVideos)
                    cloudAccountFlow.value = cloudAccountFlow.value.copy(
                        isSyncing = false,
                        syncError = null
                    )
                }.onFailure { error ->
                    if (Firebase.auth.currentUser?.uid == uid) {
                        cloudAccountFlow.value = cloudAccountFlow.value.copy(
                            isSyncing = false,
                            syncError = error.localizedMessage ?: "Bulut eşitleme hatası"
                        )
                    }
                }
            }
        }
    }

    fun detachCloudSync() {
        activeCloudSyncJob?.cancel()
        activeCloudSyncJob = null
        activeObservedUid = null
        cloudAccountFlow.value = CloudAccountUiState()
    }

    fun signInWithGoogle(activity: Activity) {
        if (cloudAccountFlow.value.isSigningIn) return
        viewModelScope.launch {
            cloudAccountFlow.value = cloudAccountFlow.value.copy(isSigningIn = true, syncError = null)
            when (val result = GoogleAuthHelper.signInWithGoogle(activity)) {
                is GoogleAuthHelper.InteractiveSignInResult.Success -> {
                    cloudAccountFlow.value = cloudAccountFlow.value.copy(isSigningIn = false)
                    showTemporaryMessage(
                        "Google hesabı bağlandı${result.email?.let { ": $it" } ?: "!"} Videolarınız eşitleniyor."
                    )
                }
                is GoogleAuthHelper.InteractiveSignInResult.Cancelled -> {
                    cloudAccountFlow.value = cloudAccountFlow.value.copy(isSigningIn = false)
                }
                is GoogleAuthHelper.InteractiveSignInResult.Error -> {
                    cloudAccountFlow.value = cloudAccountFlow.value.copy(
                        isSigningIn = false,
                        syncError = result.message
                    )
                    showTemporaryMessage(result.message)
                }
            }
        }
    }

    fun signOutGoogle() {
        viewModelScope.launch {
            val appContext = getApplication<Application>()
            GoogleAuthHelper.signOut(appContext) {
                withContext(NonCancellable) {
                    detachCloudSync()
                    repository.clearCloudSyncedVideos()
                }
            }
            showTemporaryMessage("Google hesabından çıkış yapıldı. Yerel videolar korundu.")
        }
    }

    fun syncNow() {
        val uid = Firebase.auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            showTemporaryMessage("Bulut eşitleme için önce Google hesabıyla giriş yapın.")
            return
        }
        viewModelScope.launch {
            cloudAccountFlow.value = cloudAccountFlow.value.copy(isSyncing = true, syncError = null)
            val pushed = pushPendingLocalOnlineVideos()
            cloudAccountFlow.value = cloudAccountFlow.value.copy(isSyncing = false)
            showTemporaryMessage(
                if (pushed > 0) "$pushed video bulut hesabınızla eşitlendi!"
                else "Bulut kütüphaneniz güncel."
            )
        }
    }

    private suspend fun pushPendingLocalOnlineVideos(): Int {
        if (Firebase.auth.currentUser == null) return 0
        var count = 0
        val localVideos = repository.getAllVideosOnce()
        for (video in localVideos) {
            if (video.source == VideoSourceType.LOCAL) continue
            val res = cloudSyncRepository.upsertCloudVideo(
                title = video.title,
                sourceType = video.source,
                videoUrl = video.uriString,
                thumbnailUrl = video.remoteThumbnailUrl.orEmpty(),
                category = video.category,
                favorite = video.isFavorite,
                durationMs = video.durationMs,
                existingDocId = video.cloudDocId
            )
            if (res.isSuccess) {
                repository.markSyncedFromCloud(video.id, true)
                count++
            }
        }
        return count
    }

    fun addYouTubeVideo(rawUrl: String, customTitle: String?, category: VideoCategory) {
        val ytId = com.example.util.VideoLinkParser.extractYouTubeVideoId(rawUrl)
        val playlistId = com.example.util.VideoLinkParser.extractYouTubePlaylistId(rawUrl)
        if (ytId == null && playlistId != null) {
            importYouTubePlaylist(rawUrl, category)
            return
        }
        viewModelScope.launch {
            isImportingFlow.value = true
            val result = repository.addYouTubeVideo(rawUrl, customTitle, category)
            isImportingFlow.value = false
            result.onSuccess { entity ->
                if (Firebase.auth.currentUser != null) {
                    cloudSyncRepository.upsertCloudVideo(
                        title = entity.title,
                        sourceType = VideoSourceType.YOUTUBE,
                        videoUrl = entity.uriString,
                        thumbnailUrl = entity.remoteThumbnailUrl.orEmpty(),
                        category = entity.category,
                        favorite = entity.isFavorite,
                        durationMs = entity.durationMs,
                        existingDocId = entity.cloudDocId
                    ).onSuccess {
                        repository.markSyncedFromCloud(entity.id, true)
                    }
                }
                showTemporaryMessage("YouTube videosu eklendi: ${entity.title}")
            }.onFailure { err ->
                showTemporaryMessage(err.localizedMessage ?: "YouTube bağlantısı eklenemedi.")
            }
        }
    }

    fun importYouTubePlaylist(rawUrl: String, category: VideoCategory) {
        viewModelScope.launch {
            isImportingFlow.value = true
            val result = repository.importYouTubePlaylist(rawUrl, category)
            isImportingFlow.value = false
            result.onSuccess { addedList ->
                syncOnlineListInBackground(addedList)
                if (addedList.isNotEmpty()) {
                    showTemporaryMessage("YouTube listesinden ${addedList.size} video eklendi!")
                } else {
                    showTemporaryMessage("YouTube listesindeki tüm videolar zaten kütüphanenizde mevcut.")
                }
            }.onFailure { err ->
                showTemporaryMessage(err.localizedMessage ?: "YouTube oynatma listesi alınamadı.")
            }
        }
    }

    private suspend fun importYouTubePlaylistInternal(
        rawUrl: String,
        category: VideoCategory,
        fromPhone: Boolean
    ): Result<String> {
        val res = repository.importYouTubePlaylist(rawUrl, category)
        return res.map { list ->
            syncOnlineListInBackground(list)
            val msg = if (list.isNotEmpty()) {
                "${list.size} YouTube videosu TV'ye eklendi!"
            } else {
                "Listedeki videolar zaten mevcut."
            }
            if (fromPhone) {
                showTemporaryMessage("Telefondan YouTube listesi geldi: $msg")
            }
            msg
        }
    }

    private fun syncOnlineListInBackground(entities: List<VideoEntity>) {
        if (entities.isEmpty() || Firebase.auth.currentUser == null) return
        viewModelScope.launch(Dispatchers.IO) {
            entities.chunked(5).forEach { chunk ->
                coroutineScope {
                    chunk.map { entity ->
                        async {
                            cloudSyncRepository.upsertCloudVideo(
                                title = entity.title,
                                sourceType = entity.source,
                                videoUrl = entity.uriString,
                                thumbnailUrl = entity.remoteThumbnailUrl.orEmpty(),
                                category = entity.category,
                                favorite = entity.isFavorite,
                                durationMs = entity.durationMs,
                                existingDocId = entity.cloudDocId
                            ).onSuccess { repository.markSyncedFromCloud(entity.id, true) }
                        }
                    }.awaitAll()
                }
            }
        }
    }

    fun addCloudVideoUrl(
        rawUrl: String,
        customTitle: String?,
        category: VideoCategory,
        downloadOffline: Boolean
    ) {
        viewModelScope.launch {
            isImportingFlow.value = true
            val result = repository.addCloudVideoUrl(rawUrl, customTitle, category, downloadOffline)
            isImportingFlow.value = false
            result.onSuccess { entity ->
                if (Firebase.auth.currentUser != null) {
                    cloudSyncRepository.upsertCloudVideo(
                        title = entity.title,
                        sourceType = VideoSourceType.CLOUD_URL,
                        videoUrl = entity.uriString,
                        thumbnailUrl = entity.remoteThumbnailUrl.orEmpty(),
                        category = entity.category,
                        favorite = entity.isFavorite,
                        durationMs = entity.durationMs,
                        existingDocId = entity.cloudDocId
                    ).onSuccess {
                        repository.markSyncedFromCloud(entity.id, true)
                    }
                }
                showTemporaryMessage(
                    if (downloadOffline) "Bulut videosu indirildi ve eklendi: ${entity.title}"
                    else "Bulut videosu eklendi: ${entity.title}"
                )
            }.onFailure { err ->
                showTemporaryMessage(err.localizedMessage ?: "Bulut bağlantısı eklenemedi.")
            }
        }
    }

    fun downloadCloudVideoOffline(video: VideoEntity) {
        viewModelScope.launch {
            isImportingFlow.value = true
            val result = repository.downloadCloudVideoOffline(video)
            isImportingFlow.value = false
            result.onSuccess {
                showTemporaryMessage("\"${video.title}\" çevrimdışı izleme için indirildi!")
            }.onFailure { err ->
                showTemporaryMessage(err.localizedMessage ?: "Video indirilemedi.")
            }
        }
    }

    fun importJsonPlaylist(rawJsonUrl: String, fallbackCategory: VideoCategory) {
        viewModelScope.launch {
            isImportingFlow.value = true
            val result = repository.importJsonPlaylist(rawJsonUrl, fallbackCategory)
            isImportingFlow.value = false
            result.onSuccess { addedList ->
                syncOnlineListInBackground(addedList)
                if (addedList.isNotEmpty()) {
                    showTemporaryMessage("Bulut listeden ${addedList.size} video eklendi!")
                } else {
                    showTemporaryMessage("Listedeki tüm videolar zaten kütüphanenizde mevcut.")
                }
            }.onFailure { err ->
                showTemporaryMessage(err.localizedMessage ?: "Bulut listesi içe aktarılamadı.")
            }
        }
    }

    fun selectCategory(category: VideoCategory) {
        selectedCategoryFlow.value = category
    }

    fun updateSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun reshuffleFeed() {
        mixSeedFlow.value = Random.nextLong()
        showTemporaryMessage("Videolar yeniden karıştırıldı!")
    }

    fun refreshSystemPinStatus(activity: Activity) {
        isSystemPinnedFlow.value = LockTaskHelper.isSystemPinned(activity)
    }

    fun enableKidLock(activity: Activity) {
        viewModelScope.launch {
            preferences.setKidLockEnabled(true)
            LockTaskHelper.setImmersiveMode(activity, true)
            LockTaskHelper.startSystemPinning(activity)
            delay(350L)
            isSystemPinnedFlow.value = LockTaskHelper.isSystemPinned(activity)
            showTemporaryMessage("Çocuk Ekran Kilidi açıldı! Uygulamadan çıkış engellendi.")
        }
    }

    fun disableKidLock(activity: Activity) {
        viewModelScope.launch {
            preferences.setKidLockEnabled(false)
            LockTaskHelper.stopSystemPinning(activity)
            LockTaskHelper.setImmersiveMode(activity, false)
            isSystemPinnedFlow.value = false
            showTemporaryMessage("Ekran Kilidi kapatıldı. Ebeveyn kontrolleri açık.")
        }
    }

    fun requestSystemPinningAgain(activity: Activity) {
        viewModelScope.launch {
            LockTaskHelper.startSystemPinning(activity)
            delay(350L)
            isSystemPinnedFlow.value = LockTaskHelper.isSystemPinned(activity)
        }
    }

    fun setParentPin(pin: String) {
        viewModelScope.launch {
            preferences.setParentPin(pin)
            showTemporaryMessage(
                if (pin.length == 4) "4 haneli Ebeveyn PIN'i kaydedildi." else "PIN kaldırıldı, matematik sorusu aktif."
            )
        }
    }

    fun setAutoplayNext(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAutoplayNext(enabled)
        }
    }

    fun setSleepTimer(minutes: Int) {
        viewModelScope.launch {
            preferences.setSleepTimer(minutes)
            currentTimeMsFlow.value = System.currentTimeMillis()
            if (minutes > 0) {
                showTemporaryMessage("İzleme süresi $minutes dakika olarak ayarlandı.")
            } else {
                showTemporaryMessage("İzleme zamanlayıcısı kapatıldı.")
            }
        }
    }

    fun importVideos(uris: List<Uri>, defaultCategory: VideoCategory? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            isImportingFlow.value = true
            val inserted = repository.importVideos(uris, defaultCategory, fromPersistedTree = false)
            isImportingFlow.value = false
            if (inserted.isNotEmpty()) {
                showTemporaryMessage("${inserted.size} yeni video kütüphaneye eklendi!")
                launch(Dispatchers.IO) {
                    repository.enrichLocalVideos(inserted, fromPersistedTree = false)
                }
            } else {
                showTemporaryMessage("Seçilen videolar zaten listede veya eklenemedi.")
            }
        }
    }

    fun importFromFolderTree(treeUri: Uri, defaultCategory: VideoCategory? = null) {
        viewModelScope.launch {
            isImportingFlow.value = true
            val inserted = repository.importFromFolderTree(treeUri, defaultCategory)
            isImportingFlow.value = false
            if (inserted.isNotEmpty()) {
                showTemporaryMessage("Klasörden ${inserted.size} yeni video yüklendi!")
                launch(Dispatchers.IO) {
                    repository.enrichLocalVideos(inserted, fromPersistedTree = true)
                }
            } else {
                showTemporaryMessage("Bu klasörde yeni video dosyası bulunamadı.")
            }
        }
    }

    fun scanAppMoviesFolders(defaultCategory: VideoCategory? = null) {
        viewModelScope.launch {
            isImportingFlow.value = true
            val inserted = repository.scanAppMoviesFolders(defaultCategory)
            isImportingFlow.value = false
            if (inserted.isNotEmpty()) {
                showTemporaryMessage("Uygulama klasöründen ${inserted.size} yeni video eklendi!")
                launch(Dispatchers.IO) {
                    repository.enrichLocalVideos(inserted, fromPersistedTree = true)
                }
            } else {
                showTemporaryMessage("Uygulama klasöründe yeni video bulunamadı.")
            }
        }
    }

    fun toggleFavorite(video: VideoEntity) {
        viewModelScope.launch {
            val newFavorite = !video.isFavorite
            repository.toggleFavorite(video.id, video.isFavorite)
            val docId = video.cloudDocId
            if (Firebase.auth.currentUser != null && video.source != VideoSourceType.LOCAL && !docId.isNullOrBlank()) {
                cloudSyncRepository.updateCloudVideoDetails(
                    docId = docId,
                    title = video.title,
                    category = video.category,
                    favorite = newFavorite
                )
            }
        }
    }

    fun updateVideoDetails(id: Long, title: String, category: VideoCategory) {
        viewModelScope.launch {
            val existing = repository.getVideoById(id)
            repository.updateVideoDetails(id, title, category)
            val docId = existing?.cloudDocId
            if (Firebase.auth.currentUser != null && existing != null &&
                existing.source != VideoSourceType.LOCAL && !docId.isNullOrBlank()
            ) {
                cloudSyncRepository.updateCloudVideoDetails(
                    docId = docId,
                    title = title,
                    category = category,
                    favorite = existing.isFavorite
                )
            }
            showTemporaryMessage("Video bilgileri güncellendi.")
        }
    }

    fun deleteVideo(video: VideoEntity) {
        viewModelScope.launch {
            try {
                repository.deleteVideo(video)
                val docId = video.cloudDocId
                if (Firebase.auth.currentUser != null && video.source != VideoSourceType.LOCAL && !docId.isNullOrBlank()) {
                    cloudSyncRepository.deleteCloudVideo(docId)
                }
                showTemporaryMessage("\"${video.title}\" kaldırıldı.")
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    fun updatePlaybackPosition(id: Long, positionMs: Long) {
        viewModelScope.launch {
            repository.updatePlaybackPosition(id, positionMs)
        }
    }

    fun incrementPlayCount(id: Long) {
        viewModelScope.launch {
            repository.incrementPlayCount(id)
        }
    }

    fun showTemporaryMessage(message: String) {
        viewModelScope.launch {
            statusMessageFlow.value = message
            delay(3200L)
            if (statusMessageFlow.value == message) {
                statusMessageFlow.value = null
            }
        }
    }

    fun clearStatusMessage() {
        statusMessageFlow.value = null
    }
}
