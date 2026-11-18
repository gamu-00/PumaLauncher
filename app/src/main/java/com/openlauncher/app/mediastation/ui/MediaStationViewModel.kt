package com.openlauncher.app.mediastation.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openlauncher.app.mediastation.data.EpisodeItem
import com.openlauncher.app.mediastation.data.MediaPosterItem
import com.openlauncher.app.mediastation.data.MediaStationRepository
import com.openlauncher.app.mediastation.data.MediaStationStorage
import com.openlauncher.app.mediastation.data.PlayableStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MoviesScreen {
    CATALOG,
    SEARCH,
    SETTINGS,
    EPISODES,
    PLAYER
}

data class CatalogSectionState(
    val items: List<MediaPosterItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class MoviesUiState(
    val screen: MoviesScreen = MoviesScreen.CATALOG,
    val stremioAuthKey: String = "",
    val addonBaseUrl: String = "",
    val continueWatching: CatalogSectionState = CatalogSectionState(),
    val topMovies: CatalogSectionState = CatalogSectionState(),
    val topSeries: CatalogSectionState = CatalogSectionState(),
    val searchQuery: String = "",
    val searchResults: List<MediaPosterItem> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val selectedSeries: MediaPosterItem? = null,
    val episodes: List<EpisodeItem> = emptyList(),
    val selectedSeason: Int = 1,
    val preselectedEpisodeVideoId: String? = null,
    val isLoadingEpisodes: Boolean = false,
    val episodesError: String? = null,
    val activeItem: MediaPosterItem? = null,
    val activeVideoId: String? = null,
    val activePlayerTitle: String = "",
    val resumeOffsetMs: Long = 0L,
    val resolvedStreams: List<PlayableStream> = emptyList(),
    val currentStreamIndex: Int = 0,
    val isResolvingStreams: Boolean = false,
    val streamError: String? = null
) {
    val availableSeasons: List<Int>
        get() = episodes.map { it.season }.distinct().sorted()

    val episodesForSelectedSeason: List<EpisodeItem>
        get() = episodes.filter { it.season == selectedSeason }

    val currentStream: PlayableStream?
        get() = resolvedStreams.getOrNull(currentStreamIndex)
}

class MediaStationViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = MediaStationStorage(application.applicationContext)
    private val repository = MediaStationRepository(storage)

    private val _uiState = MutableStateFlow(MoviesUiState())
    val uiState: StateFlow<MoviesUiState> = _uiState.asStateFlow()

    private val backStack = ArrayDeque<MoviesScreen>()
    private var searchJob: Job? = null
    private var episodesJob: Job? = null
    private var resolveJob: Job? = null

    init {
        observeCacheAndSettings()
        loadTopCatalogs()
    }

    private fun observeCacheAndSettings() {
        viewModelScope.launch {
            storage.continueWatchingCacheFlow.collect { cached ->
                _uiState.update { current ->
                    current.copy(
                        continueWatching = current.continueWatching.copy(items = cached)
                    )
                }
            }
        }

        viewModelScope.launch {
            storage.settingsFlow
                .distinctUntilChanged()
                .collect { settings ->
                    val prevAuth = _uiState.value.stremioAuthKey
                    _uiState.update {
                        it.copy(
                            stremioAuthKey = settings.stremioAuthKey,
                            addonBaseUrl = settings.addonBaseUrl
                        )
                    }
                    if (settings.stremioAuthKey.isNotBlank() && settings.stremioAuthKey != prevAuth) {
                        refreshContinueWatching()
                    } else if (settings.stremioAuthKey.isBlank()) {
                        _uiState.update {
                            it.copy(continueWatching = it.continueWatching.copy(isLoading = false, error = null))
                        }
                    }
                }
        }
    }

    fun loadTopCatalogs() {
        loadTopMovies()
        loadTopSeries()
    }

    fun loadTopMovies() {
        viewModelScope.launch {
            _uiState.update { it.copy(topMovies = it.topMovies.copy(isLoading = true, error = null)) }
            runCatching { repository.fetchTopCatalog("movie") }
                .onSuccess { items ->
                    _uiState.update {
                        it.copy(topMovies = CatalogSectionState(items = items, isLoading = false, error = null))
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            topMovies = it.topMovies.copy(
                                isLoading = false,
                                error = e.message ?: "Errore caricamento film"
                            )
                        )
                    }
                }
        }
    }

    fun loadTopSeries() {
        viewModelScope.launch {
            _uiState.update { it.copy(topSeries = it.topSeries.copy(isLoading = true, error = null)) }
            runCatching { repository.fetchTopCatalog("series") }
                .onSuccess { items ->
                    _uiState.update {
                        it.copy(topSeries = CatalogSectionState(items = items, isLoading = false, error = null))
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            topSeries = it.topSeries.copy(
                                isLoading = false,
                                error = e.message ?: "Errore caricamento serie TV"
                            )
                        )
                    }
                }
        }
    }

    fun refreshContinueWatching() {
        val authKey = _uiState.value.stremioAuthKey
        if (authKey.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(continueWatching = it.continueWatching.copy(isLoading = true, error = null))
            }
            runCatching { repository.fetchContinueWatching(authKey) }
                .onSuccess { items ->
                    _uiState.update {
                        it.copy(
                            continueWatching = CatalogSectionState(
                                items = items,
                                isLoading = false,
                                error = null
                            )
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            continueWatching = it.continueWatching.copy(
                                isLoading = false,
                                error = e.message ?: "Errore sincronizzazione Stremio"
                            )
                        )
                    }
                }
        }
    }

    fun openSearch() {
        navigateTo(MoviesScreen.SEARCH)
    }

    fun openSettings() {
        navigateTo(MoviesScreen.SETTINGS)
    }

    fun saveSettings(authKey: String, addonBaseUrl: String) {
        viewModelScope.launch {
            storage.saveSettings(authKey, addonBaseUrl)
            val cleanAuth = authKey.trim()
            _uiState.update { it.copy(stremioAuthKey = cleanAuth) }
            // Pop back from settings to previous screen (usually CATALOG).
            navigateBackInternal()
            if (cleanAuth.isNotBlank()) {
                refreshContinueWatching()
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun submitSearch(overrideQuery: String? = null) {
        val query = (overrideQuery ?: _uiState.value.searchQuery).trim()
        if (overrideQuery != null) {
            _uiState.update { it.copy(searchQuery = overrideQuery) }
        }
        if (_uiState.value.screen != MoviesScreen.SEARCH) {
            navigateTo(MoviesScreen.SEARCH)
        }
        if (query.isEmpty()) {
            searchJob?.cancel()
            _uiState.update {
                it.copy(searchResults = emptyList(), isSearching = false, searchError = null)
            }
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null) }
            runCatching { repository.searchCatalog(query) }
                .onSuccess { results ->
                    _uiState.update {
                        it.copy(searchResults = results, isSearching = false, searchError = null)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            searchError = e.message ?: "Errore durante la ricerca"
                        )
                    }
                }
        }
    }

    fun onPosterClicked(item: MediaPosterItem) {
        if (item.type == "series") {
            openSeriesEpisodes(item)
        } else {
            val resumeMs = if (item.videoId == item.id && item.timeOffsetMs > 0L) {
                item.timeOffsetMs
            } else {
                0L
            }
            startPlayback(
                item = item,
                videoId = item.id,
                playerTitle = item.title,
                resumeOffsetMs = resumeMs
            )
        }
    }

    private fun openSeriesEpisodes(series: MediaPosterItem) {
        episodesJob?.cancel()
        val initialSeason = series.season ?: 1
        val preselectedVideo = if (series.season != null && series.episode != null) series.videoId else null

        _uiState.update {
            it.copy(
                selectedSeries = series,
                episodes = emptyList(),
                selectedSeason = initialSeason,
                preselectedEpisodeVideoId = preselectedVideo,
                isLoadingEpisodes = true,
                episodesError = null
            )
        }
        navigateTo(MoviesScreen.EPISODES)

        episodesJob = viewModelScope.launch {
            runCatching { repository.fetchSeriesEpisodes(series.id) }
                .onSuccess { episodes ->
                    val seasons = episodes.map { it.season }.distinct().sorted()
                    val activeSeason = when {
                        series.season != null && seasons.contains(series.season) -> series.season
                        seasons.isNotEmpty() -> seasons.first()
                        else -> 1
                    }
                    _uiState.update {
                        it.copy(
                            episodes = episodes,
                            selectedSeason = activeSeason,
                            isLoadingEpisodes = false,
                            episodesError = null
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoadingEpisodes = false,
                            episodesError = e.message ?: "Impossibile caricare gli episodi"
                        )
                    }
                }
        }
    }

    fun retryLoadEpisodes() {
        val series = _uiState.value.selectedSeries ?: return
        episodesJob?.cancel()
        _uiState.update { it.copy(isLoadingEpisodes = true, episodesError = null) }
        episodesJob = viewModelScope.launch {
            runCatching { repository.fetchSeriesEpisodes(series.id) }
                .onSuccess { episodes ->
                    val seasons = episodes.map { it.season }.distinct().sorted()
                    val activeSeason = when {
                        series.season != null && seasons.contains(series.season) -> series.season
                        seasons.isNotEmpty() -> seasons.first()
                        else -> 1
                    }
                    _uiState.update {
                        it.copy(
                            episodes = episodes,
                            selectedSeason = activeSeason,
                            isLoadingEpisodes = false,
                            episodesError = null
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoadingEpisodes = false,
                            episodesError = e.message ?: "Impossibile caricare gli episodi"
                        )
                    }
                }
        }
    }

    fun selectSeason(season: Int) {
        _uiState.update { it.copy(selectedSeason = season) }
    }

    fun onEpisodeClicked(episode: EpisodeItem) {
        val series = _uiState.value.selectedSeries ?: return
        val resumeMs = if (series.videoId == episode.videoId && series.timeOffsetMs > 0L) {
            series.timeOffsetMs
        } else {
            0L
        }
        val title = "${series.title} • S${episode.season}:E${episode.episode}"
        startPlayback(
            item = series,
            videoId = episode.videoId,
            playerTitle = title,
            resumeOffsetMs = resumeMs
        )
    }

    private fun startPlayback(
        item: MediaPosterItem,
        videoId: String,
        playerTitle: String,
        resumeOffsetMs: Long
    ) {
        resolveJob?.cancel()
        _uiState.update {
            it.copy(
                activeItem = item,
                activeVideoId = videoId,
                activePlayerTitle = playerTitle,
                resumeOffsetMs = resumeOffsetMs,
                resolvedStreams = emptyList(),
                currentStreamIndex = 0,
                isResolvingStreams = true,
                streamError = null
            )
        }
        navigateTo(MoviesScreen.PLAYER)
        resolveCurrentStreams()
    }

    fun retryResolveStreams() {
        _uiState.update {
            it.copy(
                resolvedStreams = emptyList(),
                currentStreamIndex = 0,
                isResolvingStreams = true,
                streamError = null
            )
        }
        resolveCurrentStreams()
    }

    private fun resolveCurrentStreams() {
        val state = _uiState.value
        val item = state.activeItem ?: return
        val videoId = state.activeVideoId ?: return

        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            runCatching {
                repository.resolveStreams(
                    addonBaseUrl = state.addonBaseUrl,
                    type = item.type,
                    videoId = videoId
                )
            }.onSuccess { streams ->
                if (streams.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            resolvedStreams = emptyList(),
                            currentStreamIndex = 0,
                            isResolvingStreams = false,
                            streamError = "Nessun flusso in italiano trovato in cache"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            resolvedStreams = streams,
                            currentStreamIndex = 0,
                            isResolvingStreams = false,
                            streamError = null
                        )
                    }
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isResolvingStreams = false,
                        streamError = e.message ?: "Errore risoluzione sorgenti"
                    )
                }
            }
        }
    }

    fun cycleNextStream() {
        val state = _uiState.value
        val total = state.resolvedStreams.size
        if (total <= 1) return
        val next = (state.currentStreamIndex + 1) % total
        _uiState.update { it.copy(currentStreamIndex = next, streamError = null) }
    }

    fun advanceStreamOnError() {
        val state = _uiState.value
        val nextIndex = state.currentStreamIndex + 1
        if (nextIndex < state.resolvedStreams.size) {
            _uiState.update { it.copy(currentStreamIndex = nextIndex, streamError = null) }
        } else {
            _uiState.update {
                it.copy(streamError = "Tutte le sorgenti disponibili hanno fallito la riproduzione.")
            }
        }
    }

    fun syncProgress(positionMs: Long, durationMs: Long) {
        val state = _uiState.value
        val authKey = state.stremioAuthKey
        val item = state.activeItem ?: return
        val videoId = state.activeVideoId ?: return
        if (authKey.isBlank() || positionMs <= 0L) return

        // Update in-memory resume offset in case user switches stream or reopens quickly.
        _uiState.update { it.copy(resumeOffsetMs = positionMs) }

        viewModelScope.launch {
            withContext(NonCancellable) {
                runCatching {
                    repository.syncWatchProgress(
                        authKey = authKey,
                        item = item,
                        videoId = videoId,
                        timeOffsetMs = positionMs,
                        durationMs = durationMs
                    )
                }
            }
        }
    }

    fun navigateBack(onExitToIdle: () -> Unit) {
        if (!navigateBackInternal()) {
            onExitToIdle()
        }
    }

    private fun navigateTo(target: MoviesScreen) {
        val current = _uiState.value.screen
        if (current == target) return
        if (backStack.lastOrNull() != current) {
            backStack.addLast(current)
        }
        _uiState.update { it.copy(screen = target) }
    }

    private fun navigateBackInternal(): Boolean {
        val current = _uiState.value.screen
        if (current == MoviesScreen.CATALOG) {
            return false
        }
        if (current == MoviesScreen.PLAYER) {
            resolveJob?.cancel()
            _uiState.update {
                it.copy(
                    resolvedStreams = emptyList(),
                    currentStreamIndex = 0,
                    isResolvingStreams = false,
                    streamError = null
                )
            }
        }
        val previous = if (backStack.isNotEmpty()) backStack.removeLast() else MoviesScreen.CATALOG
        _uiState.update { it.copy(screen = previous) }
        return true
    }

    override fun onCleared() {
        searchJob?.cancel()
        episodesJob?.cancel()
        resolveJob?.cancel()
        super.onCleared()
    }
}
