package com.openlauncher.app.mediastation.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import coil.compose.AsyncImage
import com.openlauncher.app.mediastation.data.EpisodeItem
import com.openlauncher.app.mediastation.data.MediaPosterItem
import com.openlauncher.app.ui.theme.AaAmber
import com.openlauncher.app.ui.theme.AaAmberAlt
import com.openlauncher.app.ui.theme.AaBorder
import com.openlauncher.app.ui.theme.AaPanel
import com.openlauncher.app.ui.theme.AaPanelHigh
import com.openlauncher.app.ui.theme.AaTextPrimary
import com.openlauncher.app.ui.theme.AaTextSecondary
import kotlinx.coroutines.launch

@Composable
internal fun MoviesHubView(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val owner = checkNotNull(LocalViewModelStoreOwner.current) {
        "LocalViewModelStoreOwner is required"
    }
    val viewModel = remember(owner, context) {
        val app = context.applicationContext as Application
        ViewModelProvider(
            owner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        )[MediaStationViewModel::class.java]
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    var voiceUnavailableMessage by remember { mutableStateOf<String?>(null) }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrEmpty()) {
                voiceUnavailableMessage = null
                viewModel.submitSearch(spoken)
            }
        }
    }

    val launchVoiceSearch = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Cerca film o serie TV")
        }
        try {
            voiceUnavailableMessage = null
            voiceLauncher.launch(intent)
        } catch (_: Exception) {
            voiceUnavailableMessage = "Ricerca vocale non disponibile su questo dispositivo"
        }
    }

    BackHandler(enabled = state.screen != MoviesScreen.CATALOG) {
        viewModel.navigateBack(onClose)
    }

    if (state.screen == MoviesScreen.PLAYER) {
        MediaPlayerView(
            title = state.activePlayerTitle,
            streams = state.resolvedStreams,
            currentStreamIndex = state.currentStreamIndex,
            resumeOffsetMs = state.resumeOffsetMs,
            isResolvingStreams = state.isResolvingStreams,
            streamError = state.streamError,
            onCycleStream = { viewModel.cycleNextStream() },
            onStreamError = { viewModel.advanceStreamOnError() },
            onRetryResolve = { viewModel.retryResolveStreams() },
            onSyncProgress = { pos, dur -> viewModel.syncProgress(pos, dur) },
            onBack = { viewModel.navigateBack(onClose) },
            onClose = onClose
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AaPanel)
            .imePadding()
    ) {
        MoviesTopBar(
            screen = state.screen,
            seriesTitle = state.selectedSeries?.title,
            searchQuery = state.searchQuery,
            onSearchQueryChange = { viewModel.updateSearchQuery(it) },
            onSubmitSearch = {
                keyboardController?.hide()
                viewModel.submitSearch()
            },
            onOpenSearch = { viewModel.openSearch() },
            onVoiceSearch = launchVoiceSearch,
            onOpenSettings = {
                if (state.screen == MoviesScreen.SETTINGS) {
                    viewModel.navigateBack(onClose)
                } else {
                    viewModel.openSettings()
                }
            },
            onCloseOrBack = {
                keyboardController?.hide()
                viewModel.navigateBack(onClose)
            }
        )

        if (voiceUnavailableMessage != null) {
            Text(
                text = voiceUnavailableMessage.orEmpty(),
                color = AaAmberAlt,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AaPanelHigh)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (state.screen) {
                MoviesScreen.CATALOG -> CatalogPagerView(
                    state = state,
                    onPosterClick = { viewModel.onPosterClicked(it) },
                    onOpenSettings = { viewModel.openSettings() },
                    onRefreshContinue = { viewModel.refreshContinueWatching() },
                    onRetryMovies = { viewModel.loadTopMovies() },
                    onRetrySeries = { viewModel.loadTopSeries() }
                )

                MoviesScreen.SEARCH -> SearchResultsView(
                    query = state.searchQuery,
                    results = state.searchResults,
                    isLoading = state.isSearching,
                    error = state.searchError,
                    onPosterClick = {
                        keyboardController?.hide()
                        viewModel.onPosterClicked(it)
                    },
                    onRetry = { viewModel.submitSearch() }
                )

                MoviesScreen.SETTINGS -> MoviesSettingsView(
                    initialAuthKey = state.stremioAuthKey,
                    initialAddonUrl = state.addonBaseUrl,
                    onSave = { auth, addon ->
                        keyboardController?.hide()
                        viewModel.saveSettings(auth, addon)
                    }
                )

                MoviesScreen.EPISODES -> SeriesEpisodesView(
                    state = state,
                    onSelectSeason = { viewModel.selectSeason(it) },
                    onEpisodeClick = { viewModel.onEpisodeClicked(it) },
                    onRetry = { viewModel.retryLoadEpisodes() }
                )

                MoviesScreen.PLAYER -> Unit
            }
        }
    }
}

@Composable
private fun MoviesTopBar(
    screen: MoviesScreen,
    seriesTitle: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onOpenSearch: () -> Unit,
    onVoiceSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseOrBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(AaPanelHigh)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (screen == MoviesScreen.SEARCH) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AaPanel)
                    .border(1.dp, AaBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = AaTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Cerca film o serie...",
                                color = AaTextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = AaTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(AaAmberAlt),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancella",
                            tint = AaTextSecondary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onSearchQueryChange("") }
                        )
                    }
                }
            }
        } else {
            val barTitle = when (screen) {
                MoviesScreen.CATALOG -> "FILM & SERIE TV"
                MoviesScreen.SETTINGS -> "IMPOSTAZIONI"
                MoviesScreen.EPISODES -> seriesTitle ?: "EPISODI"
                else -> "FILM & SERIE TV"
            }
            Text(
                text = barTitle,
                color = AaTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TopBarIconButton(
                onClick = onOpenSearch,
                contentDescription = "Cerca"
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Cerca",
                    tint = AaTextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        TopBarIconButton(
            onClick = onVoiceSearch,
            contentDescription = "Ricerca vocale"
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Ricerca vocale",
                tint = AaAmberAlt,
                modifier = Modifier.size(18.dp)
            )
        }

        TopBarIconButton(
            onClick = onOpenSettings,
            contentDescription = "Impostazioni"
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Impostazioni",
                tint = if (screen == MoviesScreen.SETTINGS) AaAmber else AaTextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        TopBarIconButton(
            onClick = onCloseOrBack,
            contentDescription = if (screen == MoviesScreen.CATALOG) "Chiudi" else "Indietro"
        ) {
            Text(
                text = "\u2715",
                color = AaTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun TopBarIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun CatalogPagerView(
    state: MoviesUiState,
    onPosterClick: (MediaPosterItem) -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshContinue: () -> Unit,
    onRetryMovies: () -> Unit,
    onRetrySeries: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val labels = listOf("Riprendi", "Film", "Serie")

    Row(modifier = Modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) { page ->
            when (page) {
                0 -> ContinueWatchingPage(
                    authKeyConfigured = state.stremioAuthKey.isNotBlank(),
                    section = state.continueWatching,
                    onPosterClick = onPosterClick,
                    onOpenSettings = onOpenSettings,
                    onRefresh = onRefreshContinue
                )

                1 -> CatalogSectionPage(
                    title = "Film",
                    section = state.topMovies,
                    onPosterClick = onPosterClick,
                    onRetry = onRetryMovies
                )

                2 -> CatalogSectionPage(
                    title = "Serie TV",
                    section = state.topSeries,
                    onPosterClick = onPosterClick,
                    onRetry = onRetrySeries
                )
            }
        }

        // Edge pill indicators for single-tap category switching.
        Column(
            modifier = Modifier
                .width(26.dp)
                .fillMaxHeight()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            labels.forEachIndexed { index, label ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .size(width = 22.dp, height = if (selected) 36.dp else 22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .semantics { contentDescription = label }
                        .clickable {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(
                                width = if (selected) 6.dp else 6.dp,
                                height = if (selected) 26.dp else 10.dp
                            )
                            .clip(CircleShape)
                            .background(
                                if (selected) AaAmberAlt else AaTextSecondary.copy(alpha = 0.4f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingPage(
    authKeyConfigured: Boolean,
    section: CatalogSectionState,
    onPosterClick: (MediaPosterItem) -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
    ) {
        SectionHeader(
            title = "Riprendi a guardare",
            isLoading = section.isLoading,
            onRefresh = if (authKeyConfigured) onRefresh else null
        )

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when {
                !authKeyConfigured && section.items.isEmpty() -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "Inserisci la tua Stremio authKey per vedere i titoli iniziati.",
                            color = AaTextSecondary,
                            fontSize = 12.sp
                        )
                        Button(
                            onClick = onOpenSettings,
                            colors = ButtonDefaults.buttonColors(containerColor = AaAmberAlt),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Apri Impostazioni",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                section.isLoading && section.items.isEmpty() -> {
                    CircularProgressIndicator(color = AaAmberAlt, strokeWidth = 3.dp)
                }

                section.error != null && section.items.isEmpty() -> {
                    ErrorSectionBox(message = section.error, onRetry = onRefresh)
                }

                section.items.isEmpty() -> {
                    Text(
                        text = "Nessun contenuto da riprendere",
                        color = AaTextSecondary,
                        fontSize = 13.sp
                    )
                }

                else -> {
                    LazyRow(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(end = 10.dp)
                    ) {
                        items(section.items, key = { "${it.id}:${it.videoId}" }) { item ->
                            PosterCard(
                                item = item,
                                showProgress = true,
                                onClick = { onPosterClick(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogSectionPage(
    title: String,
    section: CatalogSectionState,
    onPosterClick: (MediaPosterItem) -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
    ) {
        SectionHeader(
            title = title,
            isLoading = section.isLoading,
            onRefresh = onRetry
        )

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when {
                section.isLoading && section.items.isEmpty() -> {
                    CircularProgressIndicator(color = AaAmberAlt, strokeWidth = 3.dp)
                }

                section.error != null && section.items.isEmpty() -> {
                    ErrorSectionBox(message = section.error, onRetry = onRetry)
                }

                section.items.isEmpty() -> {
                    Text(
                        text = "Nessun titolo disponibile",
                        color = AaTextSecondary,
                        fontSize = 13.sp
                    )
                }

                else -> {
                    LazyRow(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(end = 10.dp)
                    ) {
                        items(section.items, key = { it.id }) { item ->
                            PosterCard(
                                item = item,
                                showProgress = false,
                                onClick = { onPosterClick(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    isLoading: Boolean,
    onRefresh: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = AaTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = AaAmberAlt,
                strokeWidth = 2.dp
            )
        } else if (onRefresh != null) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Aggiorna",
                tint = AaTextSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onRefresh)
            )
        }
    }
}

@Composable
private fun PosterCard(
    item: MediaPosterItem,
    showProgress: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(138.dp)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(AaPanelHigh)
                .border(1.dp, AaBorder, RoundedCornerShape(12.dp))
        ) {
            if (!item.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = AaTextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            if (showProgress && item.episodeBadge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.episodeBadge.orEmpty(),
                        color = AaAmberAlt,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (showProgress && item.progressFraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.65f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(item.progressFraction)
                            .background(AaAmberAlt)
                    )
                }
            }
        }

        Text(
            text = item.title,
            color = AaTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun SearchResultsView(
    query: String,
    results: List<MediaPosterItem>,
    isLoading: Boolean,
    error: String?,
    onPosterClick: (MediaPosterItem) -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(color = AaAmberAlt, strokeWidth = 3.dp)
            }

            error != null -> {
                ErrorSectionBox(message = error, onRetry = onRetry)
            }

            query.isBlank() -> {
                Text(
                    text = "Digita o usa il microfono per cercare",
                    color = AaTextSecondary,
                    fontSize = 13.sp
                )
            }

            results.isEmpty() -> {
                Text(
                    text = "Nessun risultato per \"$query\"",
                    color = AaTextSecondary,
                    fontSize = 13.sp
                )
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(results, key = { "${it.type}:${it.id}" }) { item ->
                        SearchGridPosterCard(
                            item = item,
                            onClick = { onPosterClick(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchGridPosterCard(
    item: MediaPosterItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.67f)
                .clip(RoundedCornerShape(10.dp))
                .background(AaPanelHigh)
                .border(1.dp, AaBorder, RoundedCornerShape(10.dp))
        ) {
            if (!item.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (item.type == "series") "Serie" else "Film",
                    color = AaTextPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = item.title,
            color = AaTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 13.sp
        )
    }
}

@Composable
private fun MoviesSettingsView(
    initialAuthKey: String,
    initialAddonUrl: String,
    onSave: (authKey: String, addonUrl: String) -> Unit
) {
    var authKey by remember(initialAuthKey) { mutableStateOf(initialAuthKey) }
    var addonUrl by remember(initialAddonUrl) { mutableStateOf(initialAddonUrl) }
    val clipboardManager = LocalClipboardManager.current

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = AaTextPrimary,
        unfocusedTextColor = AaTextPrimary,
        focusedBorderColor = AaAmberAlt,
        unfocusedBorderColor = AaBorder,
        focusedLabelColor = AaAmberAlt,
        unfocusedLabelColor = AaTextSecondary,
        cursorColor = AaAmberAlt,
        focusedContainerColor = AaPanelHigh,
        unfocusedContainerColor = AaPanelHigh
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = authKey,
                onValueChange = { authKey = it },
                label = { Text("Stremio authKey", fontSize = 11.sp) },
                placeholder = { Text("Incolla authKey account Stremio", fontSize = 11.sp) },
                singleLine = true,
                colors = fieldColors,
                textStyle = TextStyle(fontSize = 12.sp),
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = {
                    val clip = clipboardManager.getText()?.text?.trim()
                    if (!clip.isNullOrEmpty()) {
                        authKey = clip
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AaPanelHigh),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.border(1.dp, AaBorder, RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = "Incolla",
                    color = AaAmberAlt,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = addonUrl,
                onValueChange = { addonUrl = it },
                label = { Text("URL Addon Resolver", fontSize = 11.sp) },
                placeholder = { Text("https://esempio-addon.org/manifest.json", fontSize = 11.sp) },
                singleLine = true,
                colors = fieldColors,
                textStyle = TextStyle(fontSize = 12.sp),
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = {
                    val clip = clipboardManager.getText()?.text?.trim()
                    if (!clip.isNullOrEmpty()) {
                        addonUrl = clip
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AaPanelHigh),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.border(1.dp, AaBorder, RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = "Incolla",
                    color = AaAmberAlt,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = "Eventuali /manifest.json o / finali vengono rimossi automaticamente.",
            color = AaTextSecondary,
            fontSize = 10.sp
        )

        Button(
            onClick = { onSave(authKey, addonUrl) },
            colors = ButtonDefaults.buttonColors(containerColor = AaAmberAlt),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Salva",
                color = Color.Black,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SeriesEpisodesView(
    state: MoviesUiState,
    onSelectSeason: (Int) -> Unit,
    onEpisodeClick: (EpisodeItem) -> Unit,
    onRetry: () -> Unit
) {
    val seasons = state.availableSeasons
    val seasonEpisodes = state.episodesForSelectedSeason
    val listState = rememberLazyListState()

    LaunchedEffect(state.selectedSeason, seasonEpisodes, state.preselectedEpisodeVideoId) {
        val targetId = state.preselectedEpisodeVideoId ?: return@LaunchedEffect
        val idx = seasonEpisodes.indexOfFirst { it.videoId == targetId }
        if (idx >= 0) {
            listState.scrollToItem(idx)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.isLoadingEpisodes -> {
                CircularProgressIndicator(color = AaAmberAlt, strokeWidth = 3.dp)
            }

            state.episodesError != null -> {
                ErrorSectionBox(message = state.episodesError, onRetry = onRetry)
            }

            state.episodes.isEmpty() -> {
                Text(
                    text = "Nessun episodio disponibile",
                    color = AaTextSecondary,
                    fontSize = 13.sp
                )
            }

            else -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Horizontal season selector chips.
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(seasons, key = { it }) { season ->
                            val selected = season == state.selectedSeason
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) AaAmberAlt else AaPanelHigh)
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) AaAmberAlt else AaBorder,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { onSelectSeason(season) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Stagione $season",
                                    color = if (selected) Color.Black else AaTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Scrollable episode list.
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(seasonEpisodes, key = { it.videoId }) { ep ->
                            val isResumeTarget = ep.videoId == state.preselectedEpisodeVideoId
                            EpisodeRowCard(
                                episode = ep,
                                isHighlighted = isResumeTarget,
                                onClick = { onEpisodeClick(ep) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRowCard(
    episode: EpisodeItem,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isHighlighted) AaAmberAlt.copy(alpha = 0.16f) else AaPanelHigh)
            .border(
                width = 1.dp,
                color = if (isHighlighted) AaAmberAlt else AaBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isHighlighted) AaAmberAlt else AaPanel),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "E${episode.episode}",
                color = if (isHighlighted) Color.Black else AaTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                color = AaTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isHighlighted) {
                Text(
                    text = "Riprendi da qui",
                    color = AaAmberAlt,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Riproduci",
            tint = if (isHighlighted) AaAmberAlt else AaTextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ErrorSectionBox(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            text = message,
            color = AaTextSecondary,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = AaAmber),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text("Riprova", color = Color.White, fontSize = 12.sp)
        }
    }
}
