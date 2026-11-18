package com.openlauncher.app.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.openlauncher.app.model.AppInfo
import com.openlauncher.app.ui.theme.*

private const val GRID_COLUMNS = 5
private const val GRID_ROWS_PER_PAGE = 2
private const val APPS_PER_PAGE = GRID_COLUMNS * GRID_ROWS_PER_PAGE

/**
 * Mini AA paged app drawer: single unified 5 x 2 horizontal pager of all real launchable apps,
 * instant search, A-Z / Z-A toggle, and long-press menu ("Fissa nella Nav Rail" + "Nascondi app").
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppLibraryScreen(
    apps: List<AppInfo>,
    isLoading: Boolean,
    isPickerMode: Boolean,
    pickerSlot: Int?,
    isCarPlayPickerMode: Boolean,
    carPlayPickerLabel: String = "CHOOSE CARPLAY APP",
    accent: Color,
    onAppClick: (AppInfo) -> Unit,
    onPickerSelect: (Int, AppInfo) -> Unit,
    onCarPlaySelect: (AppInfo) -> Unit,
    pinnedPackages: Set<String> = emptySet(),
    hiddenPackages: Set<String> = emptySet(),
    showHiddenApps: Boolean = false,
    onPinApp: (AppInfo) -> Unit = {},
    onHideApp: (AppInfo) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val anyPickerMode = isPickerMode || isCarPlayPickerMode
    var query by remember { mutableStateOf("") }
    var ascending by remember { mutableStateOf(true) }

    val filtered = remember(apps, query, anyPickerMode, ascending, hiddenPackages, showHiddenApps) {
        val visibleApps = if (showHiddenApps || anyPickerMode) {
            apps
        } else {
            apps.filter { it.packageName !in hiddenPackages }
        }
        val byName = if (query.isBlank()) visibleApps
                     else visibleApps.filter { it.appName.contains(query, ignoreCase = true) }
        if (ascending) byName.sortedBy { it.appName.lowercase() }
        else byName.sortedByDescending { it.appName.lowercase() }
    }

    Column(modifier = modifier.fillMaxSize().background(AaBackground)) {
        // Header: Search bar + A-Z / Z-A toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            var searchFocused by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(AaCardShape)
                    .background(AaPanel)
                    .border(1.dp, if (searchFocused) accent else AaBorder, AaCardShape)
                    .padding(horizontal = 12.dp)
            ) {
                Icon(Icons.Default.Search, null, tint = AaTextSecondary, modifier = Modifier.size(20.dp))
                BasicTextField(
                    value         = query,
                    onValueChange = { query = it },
                    singleLine    = true,
                    textStyle     = TextStyle(color = AaTextPrimary, fontSize = 16.sp),
                    cursorBrush   = SolidColor(accent),
                    modifier      = Modifier.weight(1f).onFocusChanged { searchFocused = it.isFocused },
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    when {
                                        isCarPlayPickerMode -> carPlayPickerLabel
                                        anyPickerMode       -> "Scegli app..."
                                        else                -> "Cerca tra ${filtered.size} app..."
                                    },
                                    color = AaTextSecondary, fontSize = 16.sp
                                )
                            }
                            inner()
                        }
                    }
                )
            }

            // A-Z / Z-A toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .height(44.dp)
                    .clip(AaCardShape)
                    .background(AaPanelHigh)
                    .border(1.dp, AaBorder, AaCardShape)
                    .combinedClickable(onClick = { ascending = !ascending })
                    .padding(horizontal = 14.dp)
            ) {
                Icon(Icons.Default.SortByAlpha, null, tint = accent, modifier = Modifier.size(20.dp))
                Text(
                    if (ascending) "A-Z" else "Z-A",
                    color = AaTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accent, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
            }
            return@Column
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nessuna app trovata", color = AaTextSecondary, fontSize = 16.sp)
            }
            return@Column
        }

        // Paged grid (5 columns x 2 rows)
        val pages = filtered.chunked(APPS_PER_PAGE)
        val pagerState = rememberPagerState(pageCount = { pages.size })
        LaunchedEffect(query, ascending, showHiddenApps) { pagerState.scrollToPage(0) }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { pageIndex ->
            val pageApps = pages[pageIndex]
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (row in 0 until GRID_ROWS_PER_PAGE) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (col in 0 until GRID_COLUMNS) {
                            val app = pageApps.getOrNull(row * GRID_COLUMNS + col)
                            if (app == null) {
                                Spacer(Modifier.weight(1f))
                            } else {
                                AppTile(
                                    app          = app,
                                    pinned       = app.packageName in pinnedPackages,
                                    isHidden     = app.packageName in hiddenPackages,
                                    accent       = accent,
                                    isPickerMode = anyPickerMode,
                                    modifier     = Modifier.weight(1f).fillMaxHeight(),
                                    onClick      = {
                                        when {
                                            isCarPlayPickerMode                -> onCarPlaySelect(app)
                                            isPickerMode && pickerSlot != null -> onPickerSelect(pickerSlot, app)
                                            else                               -> {
                                                launchInFullscreen(context, app.packageName)
                                                onAppClick(app)
                                            }
                                        }
                                    },
                                    onTogglePin  = { onPinApp(app) },
                                    onToggleHide = { onHideApp(app) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Page indicators
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val shown = minOf(pages.size, 12)
            val current = pagerState.currentPage
            for (i in 0 until shown) {
                val active = i == current
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (active) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (active) accent else AaBorder)
                )
            }
            if (pages.size > shown) {
                Text("  ${current + 1}/${pages.size}", color = AaTextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: AppInfo,
    pinned: Boolean,
    isHidden: Boolean,
    accent: Color,
    isPickerMode: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleHide: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .clip(AaCardShape)
                .background(AaPanel)
                .border(1.dp, if (pinned) accent else AaBorder, AaCardShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { if (!isPickerMode) menuOpen = true }
                )
                .alpha(if (isHidden) 0.55f else 1f)
                .padding(8.dp)
        ) {
            val bmp = remember(app.packageName) {
                try { app.icon.toBitmap(128, 128) } catch (_: Exception) { null }
            }
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    painter            = BitmapPainter(bmp.asImageBitmap()),
                    contentDescription = app.appName,
                    modifier           = Modifier.size(52.dp)
                )
            } else {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Text(app.appName.take(1).uppercase(), color = accent, fontSize = 24.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text       = if (isHidden) "${app.appName} (nascosta)" else app.appName,
                color      = AaTextPrimary,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                textAlign  = TextAlign.Center,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = AaPanelHigh
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (pinned) "Rimuovi dalla Nav Rail" else "Fissa nella Nav Rail",
                        color = AaTextPrimary
                    )
                },
                leadingIcon = { Icon(Icons.Default.PushPin, null, tint = accent) },
                onClick = {
                    menuOpen = false
                    onTogglePin()
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (isHidden) "Ripristina app" else "Nascondi app",
                        color = AaTextPrimary
                    )
                },
                leadingIcon = {
                    Icon(
                        if (isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        null,
                        tint = accent
                    )
                },
                onClick = {
                    menuOpen = false
                    onToggleHide()
                }
            )
        }
    }
}
