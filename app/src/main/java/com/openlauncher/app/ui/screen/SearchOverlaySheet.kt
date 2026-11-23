package com.openlauncher.app.ui.screen

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.openlauncher.app.R
import com.openlauncher.app.ui.theme.LocalCockpitColors
import com.openlauncher.app.util.AppLanguageManager
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

private const val NAV_SEARCH_PREFS = "puma_nav_search_prefs"
private const val KEY_HOME_PLACE = "saved_home_place_json"
private const val KEY_WORK_PLACE = "saved_work_place_json"
private const val KEY_RECENT_PLACES = "recent_searched_places_json"
private const val MAX_RECENT_PLACES = 10

internal enum class QuickSlotEditTarget {
    NONE,
    HOME,
    WORK
}

internal object NavSearchPrefsStore {

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(NAV_SEARCH_PREFS, Context.MODE_PRIVATE)

    fun loadHomePlace(
        context: Context,
        fallbackLabel: String? = null,
        fallbackLat: Double? = null,
        fallbackLon: Double? = null
    ): PhotonPlace? {
        val raw = prefs(context).getString(KEY_HOME_PLACE, null)
        val parsed = raw?.let { parsePlaceJson(it) }
        if (parsed != null) return parsed
        if (fallbackLat != null && fallbackLon != null) {
            return PhotonPlace(
                name = AppLanguageManager.localizedString(context, R.string.nav_home),
                subtitle = fallbackLabel.orEmpty(),
                lat = fallbackLat,
                lon = fallbackLon
            )
        }
        return null
    }

    fun saveHomePlace(context: Context, place: PhotonPlace) {
        prefs(context).edit()
            .putString(KEY_HOME_PLACE, placeToJson(place).toString())
            .apply()
    }

    fun clearHomePlace(context: Context) {
        prefs(context).edit().remove(KEY_HOME_PLACE).apply()
    }

    fun loadWorkPlace(context: Context): PhotonPlace? {
        val raw = prefs(context).getString(KEY_WORK_PLACE, null) ?: return null
        return parsePlaceJson(raw)
    }

    fun saveWorkPlace(context: Context, place: PhotonPlace) {
        prefs(context).edit()
            .putString(KEY_WORK_PLACE, placeToJson(place).toString())
            .apply()
    }

    fun clearWorkPlace(context: Context) {
        prefs(context).edit().remove(KEY_WORK_PLACE).apply()
    }

    fun loadRecentPlaces(context: Context): List<PhotonPlace> {
        val raw = prefs(context).getString(KEY_RECENT_PLACES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = ArrayList<PhotonPlace>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                parsePlaceObject(obj)?.let { list.add(it) }
            }
            list.take(MAX_RECENT_PLACES)
        }.getOrDefault(emptyList())
    }

    fun addRecentPlace(context: Context, place: PhotonPlace): List<PhotonPlace> {
        val current = loadRecentPlaces(context)
        val deduplicated = buildList {
            add(place)
            current.forEach { existing ->
                val sameCoords = abs(existing.lat - place.lat) < 0.0002 && abs(existing.lon - place.lon) < 0.0002
                val sameName = existing.name.equals(place.name, ignoreCase = true) &&
                    existing.subtitle.equals(place.subtitle, ignoreCase = true)
                if (!sameCoords && !sameName) {
                    add(existing)
                }
            }
        }.take(MAX_RECENT_PLACES)

        val arr = JSONArray()
        deduplicated.forEach { arr.put(placeToJson(it)) }
        prefs(context).edit().putString(KEY_RECENT_PLACES, arr.toString()).apply()
        return deduplicated
    }

    fun clearRecentPlaces(context: Context) {
        prefs(context).edit().remove(KEY_RECENT_PLACES).apply()
    }

    private fun placeToJson(place: PhotonPlace): JSONObject = JSONObject().apply {
        put("name", place.name)
        put("subtitle", place.subtitle)
        put("lat", place.lat)
        put("lon", place.lon)
    }

    private fun parsePlaceJson(raw: String): PhotonPlace? =
        runCatching { parsePlaceObject(JSONObject(raw)) }.getOrNull()

    private fun parsePlaceObject(obj: JSONObject): PhotonPlace? {
        val name = obj.optString("name", "").trim()
        val subtitle = obj.optString("subtitle", "").trim()
        val lat = obj.optDouble("lat", Double.NaN)
        val lon = obj.optDouble("lon", Double.NaN)
        if (name.isEmpty() || lat.isNaN() || lon.isNaN()) return null
        return PhotonPlace(name = name, subtitle = subtitle, lat = lat, lon = lon)
    }
}

private val SheetBgDay = Color(0xFFFFFFFF)
private val SheetBgNight = Color(0xFF1E1F20)
private val SheetElevatedDay = Color(0xFFF8F9FA)
private val SheetElevatedNight = Color(0xFF282A2C)
private val SheetBorderDay = Color(0xFFDADCE0)
private val SheetBorderNight = Color(0xFF3C4043)
private val SheetTextPrimaryDay = Color(0xFF202124)
private val SheetTextPrimaryNight = Color(0xFFE8EAED)
private val SheetTextSecondaryDay = Color(0xFF5F6368)
private val SheetTextSecondaryNight = Color(0xFF9AA0A6)
private val SheetBlueDay = Color(0xFF1A73E8)
private val SheetBlueNight = Color(0xFF8AB4F8)
private val SheetBlueTintDay = Color(0xFFE8F0FE)
private val SheetBlueTintNight = Color(0xFF1E3A5F)
private val SheetDangerDay = Color(0xFFD93025)
private val SheetDangerNight = Color(0xFFF28B82)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SearchOverlaySheet(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearching: Boolean,
    searchResults: List<PhotonPlace>,
    searchStatusMessage: String?,
    initialEditTarget: QuickSlotEditTarget = QuickSlotEditTarget.NONE,
    isAddingWaypoint: Boolean = false,
    hasActiveOrPreviewDestination: Boolean = false,
    fallbackHomeLabel: String? = null,
    fallbackHomeLat: Double? = null,
    fallbackHomeLon: Double? = null,
    onSaveHomeToSettings: (String, Double, Double) -> Unit = { _, _, _ -> },
    onClearHomeFromSettings: () -> Unit = {},
    onImmediateSearch: (String) -> Unit,
    onSelectDestination: (PhotonPlace) -> Unit,
    onAddWaypoint: (PhotonPlace) -> Unit,
    onClose: () -> Unit,
    onFeedbackMessage: (String) -> Unit = {},
    tightOverlay: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    val isLight = LocalCockpitColors.current.isLight
    val surfaceBg = if (isLight) SheetBgDay else SheetBgNight
    val elevatedBg = if (isLight) SheetElevatedDay else SheetElevatedNight
    val borderCol = if (isLight) SheetBorderDay else SheetBorderNight
    val textPrimary = if (isLight) SheetTextPrimaryDay else SheetTextPrimaryNight
    val textSecondary = if (isLight) SheetTextSecondaryDay else SheetTextSecondaryNight
    val blueCol = if (isLight) SheetBlueDay else SheetBlueNight
    val blueTint = if (isLight) SheetBlueTintDay else SheetBlueTintNight
    val dangerCol = if (isLight) SheetDangerDay else SheetDangerNight

    var homePlace by remember {
        mutableStateOf(
            NavSearchPrefsStore.loadHomePlace(context, fallbackHomeLabel, fallbackHomeLat, fallbackHomeLon)
        )
    }
    var workPlace by remember {
        mutableStateOf(NavSearchPrefsStore.loadWorkPlace(context))
    }
    var recentPlaces by remember {
        mutableStateOf(NavSearchPrefsStore.loadRecentPlaces(context))
    }
    var activeEditTarget by remember(initialEditTarget) {
        mutableStateOf(initialEditTarget)
    }
    var longPressedSlot by remember {
        mutableStateOf(QuickSlotEditTarget.NONE)
    }

    // Keep fallback settings synced if updated externally
    LaunchedEffect(fallbackHomeLabel, fallbackHomeLat, fallbackHomeLon) {
        if (homePlace == null && fallbackHomeLat != null && fallbackHomeLon != null) {
            val synced = PhotonPlace(
                name = AppLanguageManager.localizedString(context, R.string.nav_home),
                subtitle = fallbackHomeLabel.orEmpty(),
                lat = fallbackHomeLat,
                lon = fallbackHomeLon
            )
            NavSearchPrefsStore.saveHomePlace(context, synced)
            homePlace = synced
        }
    }

    // Auto-focus search bar when sheet opens or when switching into setting Casa/Lavoro
    LaunchedEffect(Unit, activeEditTarget) {
        delay(80L)
        runCatching {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    fun handlePlaceChosen(place: PhotonPlace) {
        focusManager.clearFocus()
        keyboardController?.hide()
        when (activeEditTarget) {
            QuickSlotEditTarget.HOME -> {
                NavSearchPrefsStore.saveHomePlace(context, place)
                homePlace = place
                onSaveHomeToSettings(place.displayName, place.lat, place.lon)
                recentPlaces = NavSearchPrefsStore.addRecentPlace(context, place)
                activeEditTarget = QuickSlotEditTarget.NONE
                onSearchQueryChange("")
                onFeedbackMessage(AppLanguageManager.localizedString(context, R.string.nav_home_saved_format, place.name))
            }
            QuickSlotEditTarget.WORK -> {
                NavSearchPrefsStore.saveWorkPlace(context, place)
                workPlace = place
                recentPlaces = NavSearchPrefsStore.addRecentPlace(context, place)
                activeEditTarget = QuickSlotEditTarget.NONE
                onSearchQueryChange("")
                onFeedbackMessage(AppLanguageManager.localizedString(context, R.string.nav_work_saved_format, place.name))
            }
            QuickSlotEditTarget.NONE -> {
                recentPlaces = NavSearchPrefsStore.addRecentPlace(context, place)
                onSelectDestination(place)
            }
        }
    }

    val isTypingQuery = searchQuery.trim().length >= 2
    val placeholderText = when {
        activeEditTarget == QuickSlotEditTarget.HOME -> stringResource(R.string.nav_search_set_home_placeholder)
        activeEditTarget == QuickSlotEditTarget.WORK -> stringResource(R.string.nav_search_set_work_placeholder)
        isAddingWaypoint -> stringResource(R.string.nav_search_stop_placeholder)
        else -> stringResource(R.string.nav_search_here)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (isLight) 0.25f else 0.50f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                focusManager.clearFocus()
                keyboardController?.hide()
                onClose()
            }
            .padding(if (tightOverlay) 8.dp else 12.dp)
            .zIndex(25f),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = surfaceBg,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, borderCol),
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* consume tap inside sheet */ }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = if (tightOverlay) 10.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(if (tightOverlay) 8.dp else 12.dp)
            ) {
                // 1. Top Search Bar with `<-`, auto-focus text field, and `X`
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = elevatedBg,
                    border = BorderStroke(1.dp, if (activeEditTarget != QuickSlotEditTarget.NONE) blueCol else borderCol),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (tightOverlay) 46.dp else 50.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp)
                    ) {
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                onClose()
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.nav_back),
                                tint = textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(blueCol),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    onImmediateSearch(searchQuery)
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = placeholderText,
                                            color = textSecondary,
                                            fontSize = 16.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    inner()
                                }
                            }
                        )

                        if (isSearching) {
                            CircularProgressIndicator(
                                color = blueCol,
                                strokeWidth = 2.dp,
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (searchQuery.isNotEmpty()) {
                                    onSearchQueryChange("")
                                    runCatching {
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                    }
                                } else if (activeEditTarget != QuickSlotEditTarget.NONE) {
                                    activeEditTarget = QuickSlotEditTarget.NONE
                                } else {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    onClose()
                                }
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.nav_clear),
                                tint = textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Active edit banner when setting Casa or Lavoro
                if (activeEditTarget != QuickSlotEditTarget.NONE) {
                    val slotLabel = stringResource(
                        if (activeEditTarget == QuickSlotEditTarget.HOME) R.string.nav_home else R.string.nav_work
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(blueTint)
                            .border(1.dp, blueCol.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (activeEditTarget == QuickSlotEditTarget.HOME) Icons.Default.Home else Icons.Default.Work,
                            contentDescription = null,
                            tint = blueCol,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.nav_type_tap_save_format, slotLabel),
                            color = textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.nav_preview_cancel),
                            color = blueCol,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { activeEditTarget = QuickSlotEditTarget.NONE }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // 2. Split 50/50 row with ONLY 2 quick addresses: Casa & Lavoro
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val homeSaved = homePlace
                    val homeLabel = stringResource(R.string.nav_home)
                    QuickAddressHalfCard(
                        icon = Icons.Default.Home,
                        title = homeLabel,
                        subtitle = homeSaved?.let { it.name.ifBlank { it.subtitle } } ?: stringResource(R.string.nav_set),
                        isConfigured = homeSaved != null,
                        isEditing = activeEditTarget == QuickSlotEditTarget.HOME,
                        elevatedBg = elevatedBg,
                        borderCol = borderCol,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        blueCol = blueCol,
                        blueTint = blueTint,
                        onClick = {
                            longPressedSlot = QuickSlotEditTarget.NONE
                            val saved = homePlace
                            if (saved != null && activeEditTarget != QuickSlotEditTarget.HOME) {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                onSelectDestination(
                                    saved.copy(
                                        name = homeLabel,
                                        subtitle = saved.displayName
                                    )
                                )
                            } else {
                                activeEditTarget = QuickSlotEditTarget.HOME
                                runCatching {
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                }
                            }
                        },
                        onLongClick = {
                            if (homePlace != null) {
                                longPressedSlot = QuickSlotEditTarget.HOME
                            } else {
                                activeEditTarget = QuickSlotEditTarget.HOME
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    val workSaved = workPlace
                    val workLabel = stringResource(R.string.nav_work)
                    QuickAddressHalfCard(
                        icon = Icons.Default.Work,
                        title = workLabel,
                        subtitle = workSaved?.let { it.name.ifBlank { it.subtitle } } ?: stringResource(R.string.nav_set),
                        isConfigured = workSaved != null,
                        isEditing = activeEditTarget == QuickSlotEditTarget.WORK,
                        elevatedBg = elevatedBg,
                        borderCol = borderCol,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        blueCol = blueCol,
                        blueTint = blueTint,
                        onClick = {
                            longPressedSlot = QuickSlotEditTarget.NONE
                            val saved = workPlace
                            if (saved != null && activeEditTarget != QuickSlotEditTarget.WORK) {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                onSelectDestination(
                                    saved.copy(
                                        name = workLabel,
                                        subtitle = saved.displayName
                                    )
                                )
                            } else {
                                activeEditTarget = QuickSlotEditTarget.WORK
                                runCatching {
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                }
                            }
                        },
                        onLongClick = {
                            if (workPlace != null) {
                                longPressedSlot = QuickSlotEditTarget.WORK
                            } else {
                                activeEditTarget = QuickSlotEditTarget.WORK
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Long-press action bar to Modify or Reset Casa / Lavoro
                if (longPressedSlot != QuickSlotEditTarget.NONE) {
                    val slotName = stringResource(
                        if (longPressedSlot == QuickSlotEditTarget.HOME) R.string.nav_home else R.string.nav_work
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(elevatedBg)
                            .border(1.dp, borderCol, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.nav_address_slot_format, slotName),
                            color = textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.nav_edit),
                            color = blueCol,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(blueTint)
                                .clickable {
                                    activeEditTarget = longPressedSlot
                                    longPressedSlot = QuickSlotEditTarget.NONE
                                    runCatching {
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                        Text(
                            text = stringResource(R.string.nav_reset),
                            color = dangerCol,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(dangerCol.copy(alpha = 0.14f))
                                .clickable {
                                    if (longPressedSlot == QuickSlotEditTarget.HOME) {
                                        NavSearchPrefsStore.clearHomePlace(context)
                                        homePlace = null
                                        onClearHomeFromSettings()
                                        onFeedbackMessage(AppLanguageManager.localizedString(context, R.string.nav_home_removed))
                                    } else {
                                        NavSearchPrefsStore.clearWorkPlace(context)
                                        workPlace = null
                                        onFeedbackMessage(AppLanguageManager.localizedString(context, R.string.nav_work_removed))
                                    }
                                    if (activeEditTarget == longPressedSlot) {
                                        activeEditTarget = QuickSlotEditTarget.NONE
                                    }
                                    longPressedSlot = QuickSlotEditTarget.NONE
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                        IconButton(
                            onClick = { longPressedSlot = QuickSlotEditTarget.NONE },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.nav_close),
                                tint = textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = borderCol, thickness = 0.75.dp)

                // 3. Bottom section: Live Photon Results when typing, OR "Recenti" (last 10 searched places)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = if (tightOverlay) 210.dp else 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (isTypingQuery) {
                        when {
                            isSearching && searchResults.isEmpty() -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 14.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = blueCol,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.nav_searching),
                                        color = textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            searchResults.isNotEmpty() -> {
                                searchResults.forEachIndexed { idx, place ->
                                    if (idx > 0) {
                                        HorizontalDivider(color = borderCol.copy(alpha = 0.6f), thickness = 0.5.dp)
                                    }
                                    SearchPlaceRowItem(
                                        icon = Icons.Default.Place,
                                        iconTint = blueCol,
                                        iconBg = blueTint,
                                        place = place,
                                        textPrimary = textPrimary,
                                        textSecondary = textSecondary,
                                        blueCol = blueCol,
                                        blueTint = blueTint,
                                        showAddWaypoint = activeEditTarget == QuickSlotEditTarget.NONE &&
                                            hasActiveOrPreviewDestination && !isAddingWaypoint,
                                        trailingIcon = when (activeEditTarget) {
                                            QuickSlotEditTarget.HOME -> Icons.Default.Home
                                            QuickSlotEditTarget.WORK -> Icons.Default.Work
                                            QuickSlotEditTarget.NONE -> Icons.Default.Directions
                                        },
                                        onClick = { handlePlaceChosen(place) },
                                        onAddWaypoint = {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            recentPlaces = NavSearchPrefsStore.addRecentPlace(context, place)
                                            onAddWaypoint(place)
                                        }
                                    )
                                }
                            }

                            searchStatusMessage != null -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = blueCol,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = searchStatusMessage,
                                        color = textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    } else {
                        // "Recenti" header + last 10 searched places
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.nav_recent),
                                color = textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (recentPlaces.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.nav_clear),
                                    color = blueCol,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            NavSearchPrefsStore.clearRecentPlaces(context)
                                            recentPlaces = emptyList()
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (recentPlaces.isEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.nav_no_recent_searches),
                                    color = textSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            recentPlaces.forEachIndexed { idx, place ->
                                if (idx > 0) {
                                    HorizontalDivider(color = borderCol.copy(alpha = 0.55f), thickness = 0.5.dp)
                                }
                                SearchPlaceRowItem(
                                    icon = Icons.Default.History,
                                    iconTint = textSecondary,
                                    iconBg = elevatedBg,
                                    place = place,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    blueCol = blueCol,
                                    blueTint = blueTint,
                                    showAddWaypoint = activeEditTarget == QuickSlotEditTarget.NONE &&
                                        hasActiveOrPreviewDestination && !isAddingWaypoint,
                                    trailingIcon = Icons.Default.NorthWest,
                                    onClick = { handlePlaceChosen(place) },
                                    onAddWaypoint = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        recentPlaces = NavSearchPrefsStore.addRecentPlace(context, place)
                                        onAddWaypoint(place)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickAddressHalfCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isConfigured: Boolean,
    isEditing: Boolean,
    elevatedBg: Color,
    borderCol: Color,
    textPrimary: Color,
    textSecondary: Color,
    blueCol: Color,
    blueTint: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val bgColor = if (isEditing) blueTint else elevatedBg
    val strokeColor = when {
        isEditing -> blueCol
        isConfigured -> blueCol.copy(alpha = 0.45f)
        else -> borderCol
    }
    val iconCircleBg = if (isConfigured || isEditing) blueTint else borderCol.copy(alpha = 0.35f)
    val iconTint = if (isConfigured || isEditing) blueCol else textSecondary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .border(if (isEditing) 1.5.dp else 1.dp, strokeColor, shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconCircleBg)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = if (isConfigured) textSecondary else blueCol,
                fontSize = 12.sp,
                fontWeight = if (isConfigured) FontWeight.Medium else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchPlaceRowItem(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    place: PhotonPlace,
    textPrimary: Color,
    textSecondary: Color,
    blueCol: Color,
    blueTint: Color,
    showAddWaypoint: Boolean,
    trailingIcon: ImageVector,
    onClick: () -> Unit,
    onAddWaypoint: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(iconBg)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = place.name,
                color = textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (place.subtitle.isNotBlank()) {
                Text(
                    text = place.subtitle,
                    color = textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (showAddWaypoint) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(blueTint)
                    .border(1.dp, blueCol.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onAddWaypoint)
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    text = stringResource(R.string.nav_add_stop_chip),
                    color = blueCol,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        Icon(
            imageVector = trailingIcon,
            contentDescription = null,
            tint = blueCol,
            modifier = Modifier.size(18.dp)
        )
    }
}
