package com.openlauncher.app.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.openlauncher.app.R
import com.openlauncher.app.model.AppInfo
import com.openlauncher.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Mini AA paged app drawer: adaptive horizontal pager of all real launchable apps,
 * instant search, A-Z / Z-A toggle, and long-press menu ("Fissa nella Nav Rail" + "Nascondi app").
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppLibraryScreen(
    apps: List<AppInfo>,
    isLoading: Boolean,
    isPickerMode: Boolean,
    pickerSlot: Int?,
    accent: Color,
    onAppClick: (AppInfo) -> Unit,
    onPickerSelect: (Int, AppInfo) -> Unit,
    pinnedPackages: Set<String> = emptySet(),
    hiddenPackages: Set<String> = emptySet(),
    showHiddenApps: Boolean = false,
    onPinApp: (AppInfo) -> Unit = {},
    onHideApp: (AppInfo) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalCockpitColors.current
    var query by remember { mutableStateOf("") }
    var ascending by remember { mutableStateOf(true) }

    val filtered = remember(apps, query, isPickerMode, ascending, hiddenPackages, showHiddenApps) {
        val visibleApps = if (showHiddenApps || isPickerMode) {
            apps
        } else {
            apps.filter { it.packageName !in hiddenPackages }
        }
        val byName = if (query.isBlank()) visibleApps
                     else visibleApps.filter { it.appName.contains(query, ignoreCase = true) }
        if (ascending) byName.sortedBy { it.appName.lowercase() }
        else byName.sortedByDescending { it.appName.lowercase() }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(colors.canvasBackground)) {
        val aspectRatio = if (maxHeight > 0.dp) maxWidth / maxHeight else 1.77f
        val isCompactHeight = maxHeight < 520.dp
        val gridColumns = when {
            aspectRatio < 1.1f -> 3
            aspectRatio >= 2.1f -> 6
            else -> 5
        }
        val gridRows = if (aspectRatio < 1.1f) 4 else 2
        val appsPerPage = gridColumns * gridRows

        val headerHeight = if (isCompactHeight) 38.dp else 44.dp
        val headerVerticalPad = if (isCompactHeight) 6.dp else 10.dp
        val gridGap = if (isCompactHeight) 8.dp else 10.dp
        val indicatorPad = if (isCompactHeight) 6.dp else 10.dp
        val tileIconSize = if (isCompactHeight) 40.dp else 52.dp
        val tilePadding = if (isCompactHeight) 6.dp else 8.dp
        val tileSpacer = if (isCompactHeight) 4.dp else 6.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Header: Search bar + A-Z / Z-A toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = headerVerticalPad),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                var searchFocused by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(headerHeight)
                        .clip(AaCardShape)
                        .background(colors.cardSurface)
                        .border(1.dp, if (searchFocused) accent else colors.hairlineBorder, AaCardShape)
                        .padding(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Search, null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                    BasicTextField(
                        value         = query,
                        onValueChange = { query = it },
                        singleLine    = true,
                        textStyle     = TextStyle(color = colors.textPrimary, fontSize = if (isCompactHeight) 14.sp else 16.sp),
                        cursorBrush   = SolidColor(accent),
                        modifier      = Modifier.weight(1f).onFocusChanged { searchFocused = it.isFocused },
                        decorationBox = { inner ->
                            Box {
                                if (query.isEmpty()) {
                                    Text(
                                        if (isPickerMode) stringResource(R.string.drawer_choose_app)
                                        else stringResource(R.string.drawer_search_apps, filtered.size),
                                        color = colors.textSecondary,
                                        fontSize = if (isCompactHeight) 14.sp else 16.sp
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
                        .height(headerHeight)
                        .clip(AaCardShape)
                        .background(colors.cardSurface)
                        .border(1.dp, colors.hairlineBorder, AaCardShape)
                        .combinedClickable(onClick = { ascending = !ascending })
                        .padding(horizontal = 14.dp)
                ) {
                    Icon(Icons.Default.SortByAlpha, null, tint = accent, modifier = Modifier.size(20.dp))
                    Text(
                        if (ascending) "A-Z" else "Z-A",
                        color = colors.textPrimary,
                        fontSize = if (isCompactHeight) 14.sp else 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accent, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                }
                return@BoxWithConstraints
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.drawer_no_apps), color = colors.textSecondary, fontSize = 16.sp)
                }
                return@BoxWithConstraints
            }

            // Paged grid (adaptive columns x rows)
            val pages = filtered.chunked(appsPerPage)
            val pagerState = rememberPagerState(pageCount = { pages.size })
            val pagerScope = rememberCoroutineScope()
            LaunchedEffect(query, ascending, showHiddenApps, appsPerPage) {
                pagerState.scrollToPage(0)
            }

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = true,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { pageIndex ->
                val pageApps = pages.getOrNull(pageIndex).orEmpty()
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(gridGap)
                ) {
                    for (row in 0 until gridRows) {
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gridGap)
                        ) {
                            for (col in 0 until gridColumns) {
                                val app = pageApps.getOrNull(row * gridColumns + col)
                                if (app == null) {
                                    Spacer(Modifier.weight(1f))
                                } else {
                                    AppTile(
                                        app          = app,
                                        pinned       = app.packageName in pinnedPackages,
                                        isHidden     = app.packageName in hiddenPackages,
                                        accent       = accent,
                                        isPickerMode = isPickerMode,
                                        iconSize     = tileIconSize,
                                        tilePadding  = tilePadding,
                                        tileSpacer   = tileSpacer,
                                        modifier     = Modifier.weight(1f).fillMaxHeight(),
                                        onClick      = {
                                            if (isPickerMode && pickerSlot != null) {
                                                onPickerSelect(pickerSlot, app)
                                            } else {
                                                launchInFullscreen(context, app.packageName)
                                                onAppClick(app)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = (indicatorPad - 4.dp).coerceAtLeast(2.dp)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val shown = minOf(pages.size, 12)
                val current = pagerState.currentPage
                for (i in 0 until shown) {
                    val active = i == current
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                pagerScope.launch { pagerState.animateScrollToPage(i) }
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (active) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(if (active) accent else colors.hairlineBorder)
                        )
                    }
                }
                if (pages.size > shown) {
                    Text("  ${current + 1}/${pages.size}", color = colors.textSecondary, fontSize = 12.sp)
                }
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
    iconSize: Dp,
    tilePadding: Dp,
    tileSpacer: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleHide: () -> Unit
) {
    val colors = LocalCockpitColors.current
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .clip(AaCardShape)
                .background(colors.cardSurface)
                .border(1.dp, if (pinned) accent else colors.hairlineBorder, AaCardShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { if (!isPickerMode) menuOpen = true }
                )
                .alpha(if (isHidden) 0.55f else 1f)
                .padding(tilePadding)
        ) {
            val bmp = remember(app.packageName) {
                try { app.icon.toBitmap(128, 128) } catch (_: Exception) { null }
            }
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    painter            = BitmapPainter(bmp.asImageBitmap()),
                    contentDescription = app.appName,
                    modifier           = Modifier.size(iconSize)
                )
            } else {
                Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                    Text(app.appName.take(1).uppercase(), color = accent, fontSize = 24.sp)
                }
            }
            Spacer(Modifier.height(tileSpacer))
            Text(
                text       = if (isHidden) stringResource(R.string.drawer_hidden_suffix, app.appName) else app.appName,
                color      = colors.textPrimary,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                textAlign  = TextAlign.Center,
                fontSize   = if (iconSize < 46.dp) 12.sp else 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = colors.cardSurface
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(if (pinned) R.string.drawer_unpin_rail else R.string.drawer_pin_rail),
                        color = colors.textPrimary
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
                        stringResource(if (isHidden) R.string.drawer_unhide_app else R.string.drawer_hide_app),
                        color = colors.textPrimary
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
