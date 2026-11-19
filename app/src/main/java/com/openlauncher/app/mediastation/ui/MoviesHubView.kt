package com.openlauncher.app.mediastation.ui

import android.app.Activity
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.openlauncher.app.R
import com.openlauncher.app.mediastation.data.EpisodeItem
import com.openlauncher.app.mediastation.data.MediaPosterItem
import com.openlauncher.app.ui.theme.AaAmber
import com.openlauncher.app.ui.theme.AaAmberAlt
import com.openlauncher.app.ui.theme.LocalCockpitColors
import kotlinx.coroutines.launch

@Composable
internal fun MoviesHubView(
    viewModel: MediaStationViewModel,
    isCompactHeight: Boolean = false,
    isHomeVisible: Boolean = true,
    onClose: () -> Unit
) {
    val context = LocalContext.current

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
            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.movies_voice_prompt))
        }
        try {
            voiceUnavailableMessage = null
            voiceLauncher.launch(intent)
        } catch (_: Exception) {
            voiceUnavailableMessage = context.getString(R.string.movies_voice_unavailable)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetToCatalog()
        }
    }

    BackHandler(enabled = isHomeVisible && state.screen != MoviesScreen.CATALOG) {
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
            isCompactHeight = isCompactHeight,
            isHomeVisible = isHomeVisible,
            onCycleStream = { viewModel.cycleNextStream() },
            onStreamError = { viewModel.advanceStreamOnError() },
            onRetryResolve = { viewModel.retryResolveStreams() },
            onSyncProgress = { pos, dur -> viewModel.syncProgress(pos, dur) },
            onBack = { viewModel.navigateBack(onClose) },
            onClose = onClose
        )
        return
    }

    val colors = LocalCockpitColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.cardSurface)
            .imePadding()
    ) {
        MoviesTopBar(
            screen = state.screen,
            seriesTitle = state.selectedSeries?.title,
            searchQuery = state.searchQuery,
            isCompactHeight = isCompactHeight,
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
                    .background(colors.elevatedSurface)
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
    isCompactHeight: Boolean = false,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onOpenSearch: () -> Unit,
    onVoiceSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseOrBack: () -> Unit
) {
    val colors = LocalCockpitColors.current
    val iconButtonSize = if (isCompactHeight) 30.dp else 34.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isCompactHeight) 34.dp else 40.dp)
            .background(colors.elevatedSurface)
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
                    .background(colors.cardSurface)
                    .border(1.dp, colors.hairlineBorder, RoundedCornerShape(10.dp))
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
                        tint = colors.textSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.movies_search_placeholder),
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = colors.textPrimary,
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
                            contentDescription = stringResource(R.string.action_clear),
                            tint = colors.textSecondary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onSearchQueryChange("") }
                        )
                    }
                }
            }
        } else {
            val barTitle = when (screen) {
                MoviesScreen.CATALOG -> stringResource(R.string.movies_title_upper)
                MoviesScreen.SETTINGS -> stringResource(R.string.settings_title_upper)
                MoviesScreen.EPISODES -> seriesTitle ?: stringResource(R.string.movies_episodes_upper)
                else -> stringResource(R.string.movies_title_upper)
            }
            Text(
                text = barTitle,
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TopBarIconButton(
                onClick = onOpenSearch,
                contentDescription = stringResource(R.string.action_search),
                buttonSize = iconButtonSize
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.action_search),
                    tint = colors.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        TopBarIconButton(
            onClick = onVoiceSearch,
            contentDescription = stringResource(R.string.movies_voice_search),
            buttonSize = iconButtonSize
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = stringResource(R.string.movies_voice_search),
                tint = AaAmberAlt,
                modifier = Modifier.size(18.dp)
            )
        }

        TopBarIconButton(
            onClick = onOpenSettings,
            contentDescription = stringResource(R.string.nav_settings),
            buttonSize = iconButtonSize
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.nav_settings),
                tint = if (screen == MoviesScreen.SETTINGS) AaAmber else colors.textPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        TopBarIconButton(
            onClick = onCloseOrBack,
            contentDescription = stringResource(
                if (screen == MoviesScreen.CATALOG) R.string.action_close else R.string.action_back
            ),
            buttonSize = iconButtonSize
        ) {
            Text(
                text = "\u2715",
                color = colors.textPrimary,
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
    buttonSize: Dp = 34.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(buttonSize)
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
    val labels = listOf(
        stringResource(R.string.movies_tab_resume),
        stringResource(R.string.movies_tab_movies),
        stringResource(R.string.movies_tab_series)
    )

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
                    title = stringResource(R.string.movies_section_movies),
                    section = state.topMovies,
                    onPosterClick = onPosterClick,
                    onRetry = onRetryMovies
                )

                2 -> CatalogSectionPage(
                    title = stringResource(R.string.movies_section_series),
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
                                if (selected) AaAmberAlt else LocalCockpitColors.current.textSecondary.copy(alpha = 0.4f)
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
            title = stringResource(R.string.movies_continue_watching),
            isLoading = section.isLoading,
            onRefresh = if (authKeyConfigured) onRefresh else null
        )

        Spacer(Modifier.height(6.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val posterWidth = (maxHeight * 0.58f).coerceIn(78.dp, 138.dp)
            when {
                !authKeyConfigured && section.items.isEmpty() -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.movies_stremio_prompt),
                            color = LocalCockpitColors.current.textSecondary,
                            fontSize = 12.sp
                        )
                        Button(
                            onClick = onOpenSettings,
                            colors = ButtonDefaults.buttonColors(containerColor = AaAmberAlt),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.movies_open_settings),
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
                        text = stringResource(R.string.movies_nothing_to_resume),
                        color = LocalCockpitColors.current.textSecondary,
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
                                posterWidth = posterWidth,
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

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val posterWidth = (maxHeight * 0.58f).coerceIn(78.dp, 138.dp)
            when {
                section.isLoading && section.items.isEmpty() -> {
                    CircularProgressIndicator(color = AaAmberAlt, strokeWidth = 3.dp)
                }

                section.error != null && section.items.isEmpty() -> {
                    ErrorSectionBox(message = section.error, onRetry = onRetry)
                }

                section.items.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.movies_no_titles),
                        color = LocalCockpitColors.current.textSecondary,
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
                                posterWidth = posterWidth,
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
    val colors = LocalCockpitColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = colors.textPrimary,
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
                contentDescription = stringResource(R.string.action_refresh),
                tint = colors.textSecondary,
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
    posterWidth: Dp = 138.dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCockpitColors.current
    Column(
        modifier = modifier
            .width(posterWidth)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.elevatedSurface)
                .border(1.dp, colors.hairlineBorder, RoundedCornerShape(12.dp))
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
                        tint = colors.textSecondary.copy(alpha = 0.4f),
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
            color = colors.textPrimary,
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
    val colors = LocalCockpitColors.current
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
                    text = stringResource(R.string.movies_search_hint),
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            }

            results.isEmpty() -> {
                Text(
                    text = stringResource(R.string.movies_search_no_results, query),
                    color = colors.textSecondary,
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
    val colors = LocalCockpitColors.current
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
                .background(colors.elevatedSurface)
                .border(1.dp, colors.hairlineBorder, RoundedCornerShape(10.dp))
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
                    text = stringResource(
                        if (item.type == "series") R.string.movies_badge_series else R.string.movies_badge_movie
                    ),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = item.title,
            color = colors.textPrimary,
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
    val colors = LocalCockpitColors.current
    var authKey by remember(initialAuthKey) { mutableStateOf(initialAuthKey) }
    var addonUrl by remember(initialAddonUrl) { mutableStateOf(initialAddonUrl) }
    val clipboardManager = LocalClipboardManager.current

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        focusedBorderColor = AaAmberAlt,
        unfocusedBorderColor = colors.hairlineBorder,
        focusedLabelColor = AaAmberAlt,
        unfocusedLabelColor = colors.textSecondary,
        cursorColor = AaAmberAlt,
        focusedContainerColor = colors.elevatedSurface,
        unfocusedContainerColor = colors.elevatedSurface
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
                label = { Text(stringResource(R.string.movies_settings_auth_label), fontSize = 11.sp) },
                placeholder = { Text(stringResource(R.string.movies_settings_auth_placeholder), fontSize = 11.sp) },
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
                colors = ButtonDefaults.buttonColors(containerColor = colors.elevatedSurface),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.border(1.dp, colors.hairlineBorder, RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = stringResource(R.string.action_paste),
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
                label = { Text(stringResource(R.string.movies_settings_addon_label), fontSize = 11.sp) },
                placeholder = { Text(stringResource(R.string.movies_settings_addon_placeholder), fontSize = 11.sp) },
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
                colors = ButtonDefaults.buttonColors(containerColor = colors.elevatedSurface),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.border(1.dp, colors.hairlineBorder, RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = stringResource(R.string.action_paste),
                    color = AaAmberAlt,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = stringResource(R.string.movies_settings_addon_hint),
            color = colors.textSecondary,
            fontSize = 10.sp
        )

        Button(
            onClick = { onSave(authKey, addonUrl) },
            colors = ButtonDefaults.buttonColors(containerColor = AaAmberAlt),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.action_save),
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
    val colors = LocalCockpitColors.current
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
                    text = stringResource(R.string.movies_no_episodes),
                    color = colors.textSecondary,
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
                                    .background(if (selected) AaAmberAlt else colors.elevatedSurface)
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) AaAmberAlt else colors.hairlineBorder,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { onSelectSeason(season) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.movies_season_number, season),
                                    color = if (selected) Color.Black else colors.textPrimary,
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
    val colors = LocalCockpitColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isHighlighted) AaAmberAlt.copy(alpha = 0.16f) else colors.elevatedSurface)
            .border(
                width = 1.dp,
                color = if (isHighlighted) AaAmberAlt else colors.hairlineBorder,
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
                .background(if (isHighlighted) AaAmberAlt else colors.cardSurface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "E${episode.episode}",
                color = if (isHighlighted) Color.Black else colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isHighlighted) {
                Text(
                    text = stringResource(R.string.movies_resume_here),
                    color = AaAmberAlt,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = stringResource(R.string.action_play),
            tint = if (isHighlighted) AaAmberAlt else colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ErrorSectionBox(
    message: String,
    onRetry: () -> Unit
) {
    val colors = LocalCockpitColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            text = message,
            color = colors.textSecondary,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = AaAmber),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(stringResource(R.string.action_retry), color = Color.White, fontSize = 12.sp)
        }
    }
}
