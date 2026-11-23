package com.openlauncher.app.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.material3.Surface
import com.openlauncher.app.R
import com.openlauncher.app.ui.theme.LocalCockpitColors
import com.openlauncher.app.util.AppLanguageManager

private val GeminiSparkleBlueNight = Color(0xFF8AB4F8)
private val GeminiSparkleBlueDay = Color(0xFF1A73E8)
private val GeminiBgNight = Color(0xFF282A2C)
private val GeminiBgDay = Color(0xFFE8F0FE)
private val GeminiBorderNight = Color(0xFF3C4043)
private val GeminiBorderDay = Color(0xFFD2E3FC)

/**
 * Safely launches the system Voice Assistant / Gemini via [Intent.ACTION_VOICE_COMMAND],
 * falling back to [RecognizerIntent.ACTION_WEB_SEARCH] or [RecognizerIntent.ACTION_RECOGNIZE_SPEECH]
 * with full [ActivityNotFoundException] protection.
 */
internal fun launchGeminiVoiceAssistant(context: Context) {
    val candidates = listOf(
        Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        Intent(RecognizerIntent.ACTION_WEB_SEARCH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )

    for (intent in candidates) {
        try {
            context.startActivity(intent)
            Log.d(NAV_LOG_TAG, "Assistente vocale Gemini avviato con action=${intent.action}")
            return
        } catch (_: ActivityNotFoundException) {
            // Try next fallback
        } catch (e: Exception) {
            Log.w(NAV_LOG_TAG, "Errore avvio assistente (${intent.action}): ${e.message}")
        }
    }

    Toast.makeText(
        context,
        AppLanguageManager.localizedString(context, R.string.nav_voice_assistant_unavailable),
        Toast.LENGTH_SHORT
    ).show()
}

@Composable
internal fun GeminiVoiceAssistantButton(
    onClick: () -> Unit,
    size: Dp = 48.dp,
    iconSize: Dp = 22.dp,
    shadowElevation: Dp = 4.dp,
    modifier: Modifier = Modifier
) {
    val isLight = LocalCockpitColors.current.isLight
    val bgColor = if (isLight) GeminiBgDay else GeminiBgNight
    val borderColor = if (isLight) GeminiBorderDay else GeminiBorderNight
    val starColor = if (isLight) GeminiSparkleBlueDay else GeminiSparkleBlueNight
    val assistantDesc = stringResource(R.string.nav_voice_assistant_desc)

    Surface(
        shape = CircleShape,
        color = bgColor,
        shadowElevation = shadowElevation,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .size(size)
            .semantics { contentDescription = assistantDesc }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
        ) {
            GeminiSparkleIcon(
                tint = starColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
internal fun GeminiSparkleIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Primary 4-point Gemini curved star
        val mainCx = w * 0.46f
        val mainCy = h * 0.52f
        val mainR = w * 0.42f
        val mainInner = mainR * 0.24f

        val mainStar = Path().apply {
            moveTo(mainCx, mainCy - mainR)
            quadraticTo(mainCx + mainInner, mainCy - mainInner, mainCx + mainR, mainCy)
            quadraticTo(mainCx + mainInner, mainCy + mainInner, mainCx, mainCy + mainR)
            quadraticTo(mainCx - mainInner, mainCy + mainInner, mainCx - mainR, mainCy)
            quadraticTo(mainCx - mainInner, mainCy - mainInner, mainCx, mainCy - mainR)
            close()
        }
        drawPath(path = mainStar, color = tint)

        // Secondary top-right sparkle star
        val subCx = w * 0.80f
        val subCy = h * 0.22f
        val subR = w * 0.17f
        val subInner = subR * 0.25f

        val subStar = Path().apply {
            moveTo(subCx, subCy - subR)
            quadraticTo(subCx + subInner, subCy - subInner, subCx + subR, subCy)
            quadraticTo(subCx + subInner, subCy + subInner, subCx, subCy + subR)
            quadraticTo(subCx - subInner, subCy + subInner, subCx - subR, subCy)
            quadraticTo(subCx - subInner, subCy - subInner, subCx, subCy - subR)
            close()
        }
        drawPath(path = subStar, color = tint.copy(alpha = 0.90f))
    }
}

@Composable
internal fun NavigationSearchOverlay(
    searchQueryProvider: () -> String,
    onSearchQueryChange: (String) -> Unit,
    searchFocused: Boolean,
    onSearchFocusedChange: (Boolean) -> Unit,
    isSearching: Boolean,
    isRouting: Boolean,
    searchResults: List<PhotonPlace>,
    searchStatusMessage: String?,
    avoidTolls: Boolean,
    avoidHighways: Boolean,
    preferScenicRoute: Boolean,
    avoidFerries: Boolean,
    showOptionsMenu: Boolean,
    onToggleOptionsMenu: () -> Unit,
    onDismissOptionsMenu: () -> Unit,
    tightOverlay: Boolean,
    hasHomeAddress: Boolean = false,
    isSettingHome: Boolean = false,
    hasActiveOrPreviewDestination: Boolean = false,
    focusSearchTrigger: Int = 0,
    onOpenSearchSheet: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onHomeLongClick: () -> Unit = {},
    onCancelSetHome: () -> Unit = {},
    onImmediateSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onToggleAvoidTolls: () -> Unit,
    onToggleAvoidHighways: () -> Unit,
    onTogglePreferScenic: () -> Unit,
    onToggleAvoidFerries: () -> Unit,
    onSelectPlace: (PhotonPlace) -> Unit,
    onAddWaypoint: (PhotonPlace) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val searchQuery = searchQueryProvider()
    var showClearHomeConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(isSettingHome) {
        if (isSettingHome) {
            showClearHomeConfirm = false
            onDismissOptionsMenu()
        }
    }

    LaunchedEffect(focusSearchTrigger) {
        if (focusSearchTrigger > 0) {
            onDismissOptionsMenu()
        }
    }

    LaunchedEffect(hasHomeAddress) {
        if (!hasHomeAddress) {
            showClearHomeConfirm = false
        }
    }

    val hasActiveOptions = avoidTolls || avoidHighways || preferScenicRoute || avoidFerries
    val btnSize = if (tightOverlay) 44.dp else 48.dp

    Column(
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(10f)
        ) {
            GoogleSearchBarPill(
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                searchFocused = searchFocused,
                onSearchFocusedChange = onSearchFocusedChange,
                isSearching = isSearching,
                isRouting = isRouting,
                tightOverlay = tightOverlay,
                hasHomeAddress = hasHomeAddress,
                isSettingHome = isSettingHome,
                focusRequester = focusRequester,
                onOpenSearchSheet = {
                    if (showOptionsMenu) onDismissOptionsMenu()
                    onOpenSearchSheet()
                },
                onImmediateSearch = onImmediateSearch,
                onClearSearch = onClearSearch,
                onHomeClick = {
                    showClearHomeConfirm = false
                    onDismissOptionsMenu()
                    onHomeClick()
                },
                onHomeLongClick = {
                    if (hasHomeAddress) {
                        onDismissOptionsMenu()
                        showClearHomeConfirm = true
                    }
                },
                onInteraction = {
                    if (showOptionsMenu) onDismissOptionsMenu()
                },
                modifier = Modifier.weight(1f)
            )

            GeminiVoiceAssistantButton(
                size = btnSize,
                onClick = {
                    if (showOptionsMenu) onDismissOptionsMenu()
                    launchGeminiVoiceAssistant(context)
                }
            )

            ValhallaMenuCircleButton(
                hasActiveOptions = hasActiveOptions,
                size = btnSize,
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onToggleOptionsMenu()
                }
            )
        }

        if (showOptionsMenu) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(15f),
                contentAlignment = Alignment.TopEnd
            ) {
                ValhallaOptionsPopupCard(
                    avoidTolls = avoidTolls,
                    avoidHighways = avoidHighways,
                    preferScenicRoute = preferScenicRoute,
                    avoidFerries = avoidFerries,
                    onToggleAvoidTolls = onToggleAvoidTolls,
                    onToggleAvoidHighways = onToggleAvoidHighways,
                    onTogglePreferScenic = onTogglePreferScenic,
                    onToggleAvoidFerries = onToggleAvoidFerries
                )
            }
        }

        HomeSearchStatusBanner(
            isSettingHome = isSettingHome,
            showClearHomeConfirm = showClearHomeConfirm,
            hasHomeAddress = hasHomeAddress,
            onCancelSetHome = onCancelSetHome,
            onDismissClearHome = { showClearHomeConfirm = false },
            onConfirmClearHome = {
                showClearHomeConfirm = false
                onHomeLongClick()
            }
        )

        val showDropdown = searchQuery.trim().length >= 2 &&
            (isSearching || searchResults.isNotEmpty() || searchStatusMessage != null)

        if (showDropdown) {
            SearchResultsDropdown(
                isSearching = isSearching,
                searchResults = searchResults,
                searchStatusMessage = searchStatusMessage,
                tightOverlay = tightOverlay,
                isSettingHome = isSettingHome,
                hasActiveOrPreviewDestination = hasActiveOrPreviewDestination,
                onSelectPlace = onSelectPlace,
                onAddWaypoint = onAddWaypoint
            )
        }
    }
}
