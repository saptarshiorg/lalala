package com.example.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.ActiveStreamSession
import com.example.data.CatalogItem
import com.example.data.LiveChannel
import com.example.data.MatchFixture
import com.example.data.MatchStatus
import com.example.data.VipIptvChannel
import com.example.player.EvMultiviewOverlay
import com.example.player.EvStreamPulseOverlay
import com.example.ui.components.EvAge18GateModal
import com.example.ui.components.EvChannelSquareTile
import com.example.ui.components.EvDmcaModal
import com.example.ui.components.EvHeroBannerSection
import com.example.ui.components.EvInAppLinkPopup
import com.example.ui.components.EvLoadingOverlay
import com.example.ui.components.EvLogoMark
import com.example.ui.components.EvMatchRailCard
import com.example.ui.components.EvMatchServerModal
import com.example.ui.components.EvPosterCard
import com.example.ui.components.EvTournamentTileCard
import com.example.ui.components.EvTvEpisodeModal
import com.example.ui.theme.EvMagenta
import com.example.ui.theme.EvMagentaLight
import com.example.ui.theme.EvTextMuted

private const val DISCORD_URL = "https://discord.gg/w46C6mQgrY"
private const val TELEGRAM_URL = "https://t.me/rishievofficial"
private const val INSTAGRAM_URL = "https://www.instagram.com/"
private val BarBg = Color(0xFF0C0D13)
private val Hairline = Color(0x1AFFFFFF)

// ───────────────────────────── ROOT ─────────────────────────────

@Composable
fun EvApp(vm: EvViewModel) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var drawerOpen by remember { mutableStateOf(false) }

    val openExternal: (String) -> Unit = { u ->
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    val playerOpen = ui.activeStreamSession != null
    val blockingModalOpen = ui.selectedMatchForServerModal != null ||
        ui.pendingTvItemForEpisodeModal != null ||
        ui.pendingAge18Confirm != null ||
        ui.showDmcaModal ||
        ui.inAppPopupUrl != null

    BackHandler(enabled = drawerOpen && !playerOpen) { drawerOpen = false }
    BackHandler(
        enabled = !drawerOpen && !playerOpen && !blockingModalOpen &&
            (ui.activeOverlay != OverlayPage.NONE || ui.homeMode != HomeMode.ALL)
    ) { vm.goHome() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!playerOpen) {
                EvTopBar(
                    onMenu = { drawerOpen = true },
                    onLogo = { vm.goHome() },
                    onVip = { vm.openOverlay(OverlayPage.VIP_PLAYER) }
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                EvScreenHost(vm = vm, ui = ui, openExternal = openExternal)
            }
            if (!playerOpen) {
                EvBottomTabs(
                    active = when {
                        ui.activeOverlay == OverlayPage.NONE && ui.homeMode != HomeMode.LIVE -> "home"
                        ui.activeOverlay == OverlayPage.SPORTS_FULL -> "matches"
                        ui.activeOverlay == OverlayPage.SEARCH -> "search"
                        ui.activeOverlay == OverlayPage.LIVE_TV_FULL -> "livetv"
                        ui.activeOverlay == OverlayPage.FAVOURITES -> "fav"
                        else -> ""
                    },
                    onHome = { vm.goHome() },
                    onMatches = { vm.openOverlay(OverlayPage.SPORTS_FULL) },
                    onSearch = { vm.openOverlay(OverlayPage.SEARCH) },
                    onLiveTv = { vm.openOverlay(OverlayPage.LIVE_TV_FULL) },
                    onFav = { vm.openOverlay(OverlayPage.FAVOURITES) }
                )
            }
        }

        // Side drawer
        EvDrawer(
            open = drawerOpen && !playerOpen,
            onClose = { drawerOpen = false },
            onNav = { page ->
                drawerOpen = false
                when (page) {
                    "home" -> vm.goHome()
                    "livetv" -> vm.openOverlay(OverlayPage.LIVE_TV_FULL)
                    "matches" -> vm.openOverlay(OverlayPage.SPORTS_FULL)
                    "search" -> vm.openOverlay(OverlayPage.SEARCH)
                    "fav" -> vm.openOverlay(OverlayPage.FAVOURITES)
                    "ott" -> vm.openOverlay(OverlayPage.FANCODE_OTT)
                    "movies" -> vm.openOverlay(OverlayPage.MOVIES_FULL)
                    "vip" -> vm.openOverlay(OverlayPage.VIP_PLAYER)
                    "multiview" -> vm.openOverlay(OverlayPage.MULTIVIEW)
                    "community" -> vm.openOverlay(OverlayPage.COMMUNITY)
                }
            }
        )

        // Modals (match server picker, TV episode picker, age gate, DMCA, in-app link)
        ui.selectedMatchForServerModal?.let { m ->
            EvMatchServerModal(
                match = m,
                onClose = { vm.closeMatchServerSelector() },
                onSelectServer = { idx -> vm.playMatchServer(m, idx) }
            )
        }
        ui.pendingTvItemForEpisodeModal?.let { item ->
            EvTvEpisodeModal(
                item = item,
                onDismiss = { vm.closeTvEpisodeModal() },
                onPlay = { s, e -> vm.playTvEpisode(item, s, e) }
            )
        }
        ui.pendingAge18Confirm?.let { (title, _) ->
            EvAge18GateModal(
                title = title,
                onConfirm = { vm.confirmAge18() },
                onCancel = { vm.dismissAge18() }
            )
        }

        // Stream player (LibVLC for direct HLS/DASH, WebView for embeds) — sits above everything
        ui.activeStreamSession?.let { session ->
            EvStreamPulseOverlay(
                session = session,
                repository = vm.repository,
                allMatches = ui.matches,
                allChannels = ui.channels,
                allMovies = ui.moviesPool,
                allTvShows = ui.tvShowsPool,
                onClose = { vm.setStreamSession(null) },
                onSelectServer = { vm.switchActiveStreamServer(it) },
                onSwitchFcxSource = { vm.switchFcxSourceMode(it) },
                onSwitchStream = { vm.setStreamSession(it) },
                onOpenInstagramCreator = { openExternal(INSTAGRAM_URL) },
                onViewAllMatches = {
                    vm.setStreamSession(null)
                    vm.openOverlay(OverlayPage.SPORTS_FULL)
                },
                onViewAllChannels = {
                    vm.setStreamSession(null)
                    vm.openOverlay(OverlayPage.LIVE_TV_FULL)
                },
                onOpenDmca = { vm.setShowDmca(true) }
            )
        }

        if (ui.showDmcaModal) {
            EvDmcaModal(onClose = { vm.setShowDmca(false) })
        }
        ui.inAppPopupUrl?.let { u ->
            EvInAppLinkPopup(
                url = u,
                onClose = { vm.setInAppPopupUrl(null) },
                onOpenExternalBrowser = { openExternal(it) }
            )
        }

        // Brand splash with logo animation
        EvLoadingOverlay(visible = ui.isInitialLoading, modifier = Modifier.fillMaxSize())
    }
}

// ───────────────────────────── SCREEN HOST ─────────────────────────────

@Composable
private fun EvScreenHost(vm: EvViewModel, ui: EvUiState, openExternal: (String) -> Unit) {
    when (ui.activeOverlay) {
        OverlayPage.NONE -> EvHomeScreen(vm, ui, openExternal)
        OverlayPage.SPORTS_FULL -> EvSportsPage(vm, ui)
        OverlayPage.LIVE_TV_FULL -> EvLiveTvPage(vm, ui)
        OverlayPage.MOVIES_FULL -> EvCatalogPage(vm, ui, isTv = false)
        OverlayPage.TV_SHOWS_FULL -> EvCatalogPage(vm, ui, isTv = true)
        OverlayPage.SEARCH -> EvSearchPage(vm, ui)
        OverlayPage.FAVOURITES -> EvFavouritesPage(vm, ui)
        OverlayPage.COMMUNITY, OverlayPage.ADMIN_CHAT -> EvCommunityPage(vm, ui, openExternal)
        OverlayPage.FANCODE_OTT, OverlayPage.WILLOW_OTT -> EvOttPage(vm, ui)
        OverlayPage.VIP_PLAYER -> EvVipPage(vm, ui)
        OverlayPage.MULTIVIEW -> EvMultiviewOverlay(
            channels = ui.channels,
            matches = ui.matches,
            onClose = { vm.openOverlay(OverlayPage.NONE) }
        )
    }
}

// ───────────────────────────── BARS ─────────────────────────────

@Composable
private fun EvTopBar(onMenu: () -> Unit, onLogo: () -> Unit, onVip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BarBg)
            .statusBarsPadding()
            .height(40.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable { onMenu() }
                .testTag("menu_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onLogo() }.padding(4.dp)
        ) { EvLogoMark(size = 24.dp) }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(EvMagenta, Color(0xFFFF3D96))))
                .clickable { onVip() }
                .padding(horizontal = 9.dp, vertical = 5.dp)
                .testTag("vip_button"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text("VIP Player", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(6.dp))
    }
}

@Composable
private fun EvBottomTabs(
    active: String,
    onHome: () -> Unit,
    onMatches: () -> Unit,
    onSearch: () -> Unit,
    onLiveTv: () -> Unit,
    onFav: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(BarBg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(44.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabIcon(Icons.Filled.Home, "Home", active == "home", onHome)
            TabIcon(Icons.Filled.CalendarMonth, "Live Matches", active == "matches", onMatches)
            TabIcon(Icons.Filled.Search, "Search", active == "search", onSearch)
            TabIcon(Icons.Filled.Tv, "Live TV", active == "livetv", onLiveTv)
            TabIcon(Icons.Filled.Favorite, "Favourites", active == "fav", onFav)
        }
    }
}

@Composable
private fun TabIcon(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .testTag("tab_$label"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) EvMagenta else Color(0x99FFFFFF),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun EvDrawer(open: Boolean, onClose: () -> Unit, onNav: (String) -> Unit) {
    AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
        )
    }
    AnimatedVisibility(
        visible = open,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it })
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(264.dp)
                .background(Color(0xFF0C0D13))
                .statusBarsPadding()
                .navigationBarsPadding()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EvLogoMark(size = 30.dp)
                Spacer(Modifier.width(10.dp))
                Text("EV SPORTS", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.Close, "Close", tint = Color.White,
                    modifier = Modifier.size(22.dp).clickable { onClose() }
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                listOf(
                    "Home" to "home", "Live TV" to "livetv", "Matches" to "matches",
                    "Search" to "search", "Favourites" to "fav", "OTT" to "ott",
                    "Movies" to "movies", "Multiview" to "multiview", "Community" to "community"
                ).forEach { (label, key) ->
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNav(key) }
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.horizontalGradient(listOf(EvMagenta, Color(0xFFFF3D96))))
                        .clickable { onNav("vip") }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("VIP Player", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }
        }
    }
}

// ───────────────────────────── SHARED PIECES ─────────────────────────────

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        if (action != null && onAction != null) {
            Text(
                action,
                color = EvMagentaLight,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.clickable { onAction() }.padding(4.dp)
            )
        }
    }
}

@Composable
private fun PageTitleBar(title: String, onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "‹",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onBack() }.padding(end = 12.dp)
        )
        Text(
            title,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.White else Color(0xB3FFFFFF),
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) EvMagenta else Color(0x14FFFFFF))
            .border(BorderStroke(1.dp, if (selected) EvMagenta else Hairline), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

@Composable
private fun PillRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { o -> Pill(o, o == selected) { onSelect(o) } }
    }
}

@Composable
private fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSubmit: (() -> Unit)? = null,
    onMic: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x0FFFFFFF))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = Color(0x99FFFFFF), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, color = Color(0x66FFFFFF), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(EvMagenta),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, keyboardType = KeyboardType.Text),
                keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Filled.Close, "Clear", tint = Color(0x99FFFFFF),
                modifier = Modifier.size(18.dp).clickable { onChange("") }
            )
        }
        if (onMic != null) {
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Filled.Mic, "Voice search", tint = EvMagentaLight,
                modifier = Modifier.size(20.dp).clickable { onMic() }
            )
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = EvTextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

private fun <T> LazyListScope.gridRows(list: List<T>, cols: Int, cell: @Composable (T) -> Unit) {
    val rows = list.chunked(cols)
    items(rows.size) { idx ->
        val row = rows[idx]
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            row.forEach { item -> Box(Modifier.weight(1f)) { cell(item) } }
            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun rememberVoiceSearch(vm: EvViewModel, lang: String, onResult: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) onResult(text)
        }
    }
    return {
        try {
            launcher.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Search EV SPORTS")
                }
            )
        } catch (_: Exception) {
        }
    }
}

@Composable
private fun cardWidthFor(cols: Int, horizontalPadding: Int = 24, gap: Int = 8): androidx.compose.ui.unit.Dp {
    val w = LocalConfiguration.current.screenWidthDp
    return ((w - horizontalPadding - gap * (cols - 1)) / cols).dp
}

// ───────────────────────────── HOME ─────────────────────────────

@Composable
private fun EvHomeScreen(vm: EvViewModel, ui: EvUiState, openExternal: (String) -> Unit) {
    val listState = rememberLazyListState()
    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 120 } }
    val live = remember(ui.matches) { ui.matches.filter { it.status == MatchStatus.LIVE } }
    val upcoming = remember(ui.matches) { ui.matches.filter { it.status == MatchStatus.UPCOMING }.take(24) }
    val tournaments = remember(ui.matches) {
        ui.matches.groupBy { it.tournament }.filterKeys { it.isNotBlank() }.entries
            .sortedByDescending { e -> e.value.count { it.status == MatchStatus.LIVE } }.take(12)
            .map { it.key to it.value.any { m -> m.status == MatchStatus.LIVE } }
    }
    val posterW3 = cardWidthFor(3, 24, 8)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("home_list"),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            val showSports = ui.homeMode == HomeMode.ALL || ui.homeMode == HomeMode.SPORTS
            val showMovies = ui.homeMode == HomeMode.ALL
            val showTv = ui.homeMode == HomeMode.ALL

            if (ui.homeMode == HomeMode.ALL || ui.homeMode == HomeMode.SPORTS) {
                item(key = "hero") {
                    EvHeroBannerSection(
                        slides = ui.heroSlides,
                        onPlaySlide = { slide ->
                            val m = slide.linkedMatch
                            val mv = slide.linkedMovie
                            when {
                                m != null -> vm.openMatchServerSelector(m)
                                mv != null -> vm.playCatalogItem(mv)
                                slide.btn1Url.isNotBlank() && slide.btn1Url != "#" ->
                                    vm.setStreamSession(ActiveStreamSession(slide.btn1Url, slide.title, "stream"))
                                else -> Unit
                            }
                        },
                        onMoreMatches = { vm.openOverlay(OverlayPage.SPORTS_FULL) }
                    )
                }
            }

            if (ui.homeMode == HomeMode.TV) {
                item(key = "tvswitch") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp, 14.dp, 14.dp, 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TvSwitchButton(
                            "TV SHOWS", ui.tvSubMode == TvSubMode.SHOWS,
                            Brush.linearGradient(listOf(Color(0xFF123A9C), Color(0xFF6A1FA0))), Color(0xFF2B7CFF),
                            Modifier.weight(1f)
                        ) { vm.setTvSubMode(TvSubMode.SHOWS) }
                        TvSwitchButton(
                            "LIVE TV", ui.tvSubMode == TvSubMode.LIVE,
                            Brush.horizontalGradient(listOf(Color(0xFFF0147A), Color(0xFFFF3D3D), Color(0xFFFFB300))),
                            Color(0xFFFF5C8A),
                            Modifier.weight(1f)
                        ) { vm.setTvSubMode(TvSubMode.LIVE) }
                    }
                }
                if (ui.tvSubMode == TvSubMode.LIVE) {
                    val chans = ui.channels.take(120)
                    gridRows(chans, 3) { ch ->
                        EvChannelSquareTile(
                            channel = ch,
                            isFavourite = ui.favouriteChannels.containsKey(ch.favKey),
                            onClick = { vm.playChannel(ch) },
                            onToggleFavourite = { vm.toggleChannelFavourite(ch) }
                        )
                    }
                } else {
                    gridRows(ui.tvShowsPool, 3) { it0 -> EvPosterCard(it0, { vm.playCatalogItem(it0) }, cardWidth = posterW3) }
                    item(key = "tv_more") { LoadMoreRow(ui.isTvShowsLoadingMore, ui.tvShowsPool.size) { vm.loadMoreTvShows() } }
                }
            }

            if (ui.homeMode == HomeMode.MOVIES) {
                item(key = "mv_h") { SectionHeader("Movies") }
                gridRows(ui.moviesPool, 3) { it0 -> EvPosterCard(it0, { vm.playCatalogItem(it0) }, cardWidth = posterW3) }
                item(key = "mv_more") { LoadMoreRow(ui.isMoviesLoadingMore, ui.moviesPool.size) { vm.loadMoreMovies() } }
            }

            if (showSports) {
                if (live.isNotEmpty()) {
                    item(key = "live_h") { SectionHeader("LIVE NOW", "More") { vm.openOverlay(OverlayPage.SPORTS_FULL) } }
                    item(key = "live_r") { MatchRail(live) { vm.openMatchServerSelector(it) } }
                }
                if (upcoming.isNotEmpty()) {
                    item(key = "up_h") { SectionHeader("Upcoming Today", "More") { vm.openOverlay(OverlayPage.SPORTS_FULL) } }
                    item(key = "up_r") { MatchRail(upcoming) { vm.openMatchServerSelector(it) } }
                }
                if (ui.homeMode == HomeMode.SPORTS && tournaments.isNotEmpty()) {
                    item(key = "tour_h") { SectionHeader("Popular Tournaments") }
                    item(key = "tour_r") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(tournaments, key = { it.first }) { (name, hasLive) ->
                                EvTournamentTileCard(name, hasLive, onClick = {
                                    vm.openOverlay(OverlayPage.SPORTS_FULL, tournamentFilter = name)
                                })
                            }
                        }
                    }
                }
            }

            if (showMovies && ui.moviesPool.isNotEmpty()) {
                item(key = "top10_h") { SectionHeader("Top 10 Movies", "More") { vm.openOverlay(OverlayPage.MOVIES_FULL) } }
                item(key = "top10_r") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(ui.moviesPool.take(10), key = { _, m -> "t10_${m.id}" }) { i, m ->
                            EvPosterCard(m, { vm.playCatalogItem(m) }, rankNumber = i + 1)
                        }
                    }
                }
                item(key = "mv_row_h") { SectionHeader("Movies") }
                item(key = "mv_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ui.moviesPool.drop(10), key = { "mv_${it.id}" }) { m ->
                            EvPosterCard(m, { vm.playCatalogItem(m) })
                        }
                        item(key = "mv_end") { LoadMoreRow(ui.isMoviesLoadingMore, ui.moviesPool.size) { vm.loadMoreMovies() } }
                    }
                }
            }

            if (showTv && ui.tvShowsPool.isNotEmpty()) {
                item(key = "tvr_h") { SectionHeader("TV Shows", "More") { vm.openOverlay(OverlayPage.TV_SHOWS_FULL) } }
                item(key = "tvr") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ui.tvShowsPool, key = { "tv_${it.id}" }) { m ->
                            EvPosterCard(m, { vm.playCatalogItem(m) })
                        }
                        item(key = "tv_end") { LoadMoreRow(ui.isTvShowsLoadingMore, ui.tvShowsPool.size) { vm.loadMoreTvShows() } }
                    }
                }
            }

            if (ui.homeMode == HomeMode.ALL && ui.channels.isNotEmpty()) {
                item(key = "ch_h") { SectionHeader("Live TV", "More") { vm.openOverlay(OverlayPage.LIVE_TV_FULL) } }
                gridRows(ui.channels.take(12), 3) { ch ->
                    EvChannelSquareTile(
                        channel = ch,
                        isFavourite = ui.favouriteChannels.containsKey(ch.favKey),
                        onClick = { vm.playChannel(ch) },
                        onToggleFavourite = { vm.toggleChannelFavourite(ch) }
                    )
                }
            }

            if (ui.homeMode != HomeMode.LIVE) {
                item(key = "footer") { EvFooter(ui, vm, openExternal) }
            }
        }

        EvModePill(
            mode = ui.homeMode,
            expanded = atTop,
            onSelect = { vm.setHomeMode(it) },
            onReset = { vm.setHomeMode(HomeMode.ALL) },
            onVoice = { vm.openOverlay(OverlayPage.SEARCH) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun TvSwitchButton(
    label: String,
    selected: Boolean,
    brush: Brush,
    border: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(brush)
            .border(2.dp, if (selected) border else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = if (selected) 1f else 0.62f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 17.sp
        )
    }
}

@Composable
private fun MatchRail(list: List<MatchFixture>, onClick: (MatchFixture) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(list, key = { it.id }) { m -> EvMatchRailCard(match = m, onClick = { onClick(m) }) }
    }
}

@Composable
private fun LoadMoreRow(loading: Boolean, key: Any, onMore: () -> Unit) {
    LaunchedEffect(key) { onMore() }
    if (loading) {
        Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            Text("Loading…", color = EvTextMuted, fontSize = 12.sp)
        }
    } else {
        Spacer(Modifier.size(1.dp))
    }
}

@Composable
private fun EvModePill(
    mode: HomeMode,
    expanded: Boolean,
    onSelect: (HomeMode) -> Unit,
    onReset: () -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xE6101018))
            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (expanded) {
            listOf(HomeMode.TV, HomeMode.MOVIES, HomeMode.SPORTS, HomeMode.LIVE).forEach { m ->
                Text(
                    m.label,
                    color = if (m == mode) EvMagentaLight else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onSelect(m) }.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            Icon(
                Icons.Filled.Mic, "Ask", tint = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp).size(18.dp).clickable { onVoice() }
            )
        } else {
            Text(
                mode.label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            if (mode != HomeMode.ALL) {
                Icon(
                    Icons.Filled.Close, "Reset", tint = Color.White,
                    modifier = Modifier.padding(end = 8.dp).size(16.dp).clickable { onReset() }
                )
            }
        }
    }
}

@Composable
private fun EvFooter(ui: EvUiState, vm: EvViewModel, openExternal: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (ui.socialLinks.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ui.socialLinks.forEach { s ->
                    Text(
                        text = "${s.emoji} ${s.label}".trim(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x14FFFFFF))
                            .clickable { if (s.url.isNotBlank()) openExternal(s.url) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Discord", false) { openExternal(DISCORD_URL) }
            Pill("Telegram", false) { openExternal(TELEGRAM_URL) }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "DMCA & Copyright Disclaimer",
            color = EvMagentaLight,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { vm.setShowDmca(true) }.padding(6.dp)
        )
        Text(
            "© EV SPORTS",
            color = Color(0x66FFFFFF),
            fontSize = 11.sp
        )
    }
}

// ───────────────────────────── FULL PAGES ─────────────────────────────

@Composable
private fun EvSportsPage(vm: EvViewModel, ui: EvUiState) {
    var sport by remember(ui.preselectedSportFilter) { mutableStateOf(ui.preselectedSportFilter.ifBlank { "All" }) }
    var tournament by remember(ui.preselectedTournamentFilter) { mutableStateOf(ui.preselectedTournamentFilter.ifBlank { "All" }) }
    val sports = remember(ui.matches) { listOf("All") + ui.matches.map { it.sport }.filter { it.isNotBlank() }.distinct() }
    val tournaments = remember(ui.matches, sport) {
        listOf("All") + ui.matches.filter { sport == "All" || it.sport == sport }
            .map { it.tournament }.filter { it.isNotBlank() }.distinct()
    }
    val filtered = remember(ui.matches, sport, tournament) {
        ui.matches.filter { (sport == "All" || it.sport == sport) && (tournament == "All" || it.tournament == tournament) }
            .sortedWith(compareBy({ it.status != MatchStatus.LIVE }, { it.startTimeMs }))
    }
    val w = cardWidthFor(2)
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageTitleBar("Matches", onBack = { vm.goHome() }) }
        item { PillRow(sports, sport) { sport = it; tournament = "All" } }
        item { PillRow(tournaments, tournament) { tournament = it } }
        if (filtered.isEmpty()) {
            item { EmptyState("No matches right now. Check back soon.") }
        } else {
            item { Spacer(Modifier.height(8.dp)) }
            gridRows(filtered, 2) { m -> EvMatchRailCard(m, { vm.openMatchServerSelector(m) }, cardWidth = w) }
        }
    }
}

@Composable
private fun EvLiveTvPage(vm: EvViewModel, ui: EvUiState) {
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("All") }
    val groups = remember(ui.channels) { listOf("All") + ui.channels.map { it.group }.filter { it.isNotBlank() }.distinct() }
    val filtered = remember(ui.channels, query, group) {
        ui.channels.filter { c ->
            (group == "All" || c.group == group) &&
                (query.isBlank() || c.name.contains(query, ignoreCase = true) || c.group.contains(query, ignoreCase = true))
        }
    }
    val voice = rememberVoiceSearch(vm, ui.voiceLanguageCode) { query = it }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageTitleBar("Live TV", onBack = { vm.goHome() }) }
        item { SearchField(query, { query = it }, "Search channels", Modifier.padding(horizontal = 12.dp), onMic = voice) }
        item { Spacer(Modifier.height(8.dp)) }
        item { PillRow(groups, group) { group = it } }
        item { Spacer(Modifier.height(8.dp)) }
        if (filtered.isEmpty()) {
            item { EmptyState("No channels found.") }
        } else {
            gridRows(filtered, 3) { ch ->
                EvChannelSquareTile(
                    channel = ch,
                    isFavourite = ui.favouriteChannels.containsKey(ch.favKey),
                    onClick = { vm.playChannel(ch) },
                    onToggleFavourite = { vm.toggleChannelFavourite(ch) }
                )
            }
        }
    }
}

@Composable
private fun EvCatalogPage(vm: EvViewModel, ui: EvUiState, isTv: Boolean) {
    var query by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("All") }
    val pool = if (isTv) ui.tvShowsPool else ui.moviesPool
    val loading = if (isTv) ui.isTvShowsLoadingMore else ui.isMoviesLoadingMore
    val genres = remember(pool) {
        listOf("All") + pool.flatMap { it.genre.split(",", "/") }.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(24)
    }
    val filtered = remember(pool, query, genre) {
        pool.filter { it0 ->
            (genre == "All" || it0.genre.contains(genre, ignoreCase = true)) &&
                (query.isBlank() || it0.title.contains(query, ignoreCase = true))
        }
    }
    val isId = remember(query) { Regex("^(tt\\d{5,}|\\d{3,})$").matches(query.trim()) }
    val w = cardWidthFor(3)
    val voice = rememberVoiceSearch(vm, ui.voiceLanguageCode) { query = it }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            PageTitleBar(
                if (isTv) "TV Shows" else "Movies",
                onBack = { vm.goHome() },
                trailing = {
                    Pill(if (isTv) "Movies" else "TV Shows", false) {
                        vm.openOverlay(if (isTv) OverlayPage.MOVIES_FULL else OverlayPage.TV_SHOWS_FULL)
                    }
                }
            )
        }
        item {
            SearchField(
                query, { query = it }, "Search, or paste IMDB (tt…) / TMDB id",
                Modifier.padding(horizontal = 12.dp),
                onSubmit = { if (isId) vm.playDirectId(query, if (isTv) "tv" else "movie") },
                onMic = voice
            )
        }
        if (isId) {
            item {
                Text(
                    "Play $query",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(EvMagenta)
                        .clickable { vm.playDirectId(query, if (isTv) "tv" else "movie") }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        item { PillRow(genres, genre) { genre = it } }
        item { Spacer(Modifier.height(8.dp)) }
        if (filtered.isEmpty() && !loading) {
            item { EmptyState("Nothing found yet.") }
        } else {
            gridRows(filtered, 3) { it0 -> EvPosterCard(it0, { vm.playCatalogItem(it0) }, cardWidth = w) }
        }
        item(key = "more") {
            LoadMoreRow(loading, pool.size) { if (isTv) vm.loadMoreTvShows() else vm.loadMoreMovies() }
        }
    }
}

@Composable
private fun EvSearchPage(vm: EvViewModel, ui: EvUiState) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val matches = remember(ui.matches, q) {
        if (q.isBlank()) emptyList() else ui.matches.filter {
            it.title.contains(q, true) || it.tournament.contains(q, true) || it.sport.contains(q, true) ||
                it.team1FullName.contains(q, true) || it.team2FullName.contains(q, true)
        }.take(30)
    }
    val channels = remember(ui.channels, q) {
        if (q.isBlank()) emptyList() else ui.channels.filter { it.name.contains(q, true) || it.group.contains(q, true) }.take(60)
    }
    val movies = remember(ui.moviesPool, q) {
        if (q.isBlank()) emptyList() else ui.moviesPool.filter { it.title.contains(q, true) }.take(30)
    }
    val shows = remember(ui.tvShowsPool, q) {
        if (q.isBlank()) emptyList() else ui.tvShowsPool.filter { it.title.contains(q, true) }.take(30)
    }
    val isId = remember(q) { Regex("^(tt\\d{5,}|\\d{3,})$").matches(q) }
    val voice = rememberVoiceSearch(vm, ui.voiceLanguageCode) { query = it }
    val w3 = cardWidthFor(3)
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageTitleBar("Search", onBack = { vm.goHome() }) }
        item {
            SearchField(
                query, { query = it }, "Search, or paste IMDB (tt…) / TMDB id",
                Modifier.padding(horizontal = 12.dp),
                onSubmit = { if (isId) vm.playDirectId(q, "movie") },
                onMic = voice
            )
        }
        if (isId) {
            item {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Play as Movie", true) { vm.playDirectId(q, "movie") }
                    Pill("Play as TV Show", false) { vm.playDirectId(q, "tv") }
                }
            }
        }
        if (q.isBlank()) {
            item { EmptyState("Search live matches, TV channels, movies and TV shows.") }
        } else if (matches.isEmpty() && channels.isEmpty() && movies.isEmpty() && shows.isEmpty() && !isId) {
            item { EmptyState("No results for \"$q\".") }
        } else {
            if (matches.isNotEmpty()) {
                item { SectionHeader("Matches") }
                item { MatchRail(matches) { vm.openMatchServerSelector(it) } }
            }
            if (channels.isNotEmpty()) {
                item { SectionHeader("Live Channels") }
                gridRows(channels, 3) { ch ->
                    EvChannelSquareTile(
                        channel = ch,
                        isFavourite = ui.favouriteChannels.containsKey(ch.favKey),
                        onClick = { vm.playChannel(ch) },
                        onToggleFavourite = { vm.toggleChannelFavourite(ch) }
                    )
                }
            }
            if (movies.isNotEmpty()) {
                item { SectionHeader("Movies") }
                gridRows(movies, 3) { it0 -> EvPosterCard(it0, { vm.playCatalogItem(it0) }, cardWidth = w3) }
            }
            if (shows.isNotEmpty()) {
                item { SectionHeader("TV Shows") }
                gridRows(shows, 3) { it0 -> EvPosterCard(it0, { vm.playCatalogItem(it0) }, cardWidth = w3) }
            }
        }
    }
}

@Composable
private fun EvFavouritesPage(vm: EvViewModel, ui: EvUiState) {
    val favs = remember(ui.favouriteChannels) { ui.favouriteChannels.values.toList() }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageTitleBar("Favourites", onBack = { vm.goHome() }) }
        if (favs.isEmpty()) {
            item { EmptyState("No favourites yet. Tap the heart on any channel to save it here.") }
        } else {
            gridRows(favs, 3) { ch ->
                EvChannelSquareTile(
                    channel = ch,
                    isFavourite = true,
                    onClick = { vm.playChannel(ch) },
                    onToggleFavourite = { vm.toggleChannelFavourite(ch) }
                )
            }
        }
    }
}

@Composable
private fun EvCommunityPage(vm: EvViewModel, ui: EvUiState, openExternal: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        PageTitleBar("Community", onBack = { vm.goHome() })
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LinkButton("Join our Discord", Color(0xFF5865F2)) { openExternal(DISCORD_URL) }
            LinkButton("Join our Telegram", Color(0xFF229ED9)) { openExternal(TELEGRAM_URL) }
            ui.socialLinks.forEach { s ->
                LinkButton("${s.emoji} ${s.label}".trim(), Color(0xFF1C1E2B)) {
                    if (s.url.isNotBlank()) openExternal(s.url)
                }
            }
        }
    }
}

@Composable
private fun LinkButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

@Composable
private fun EvOttPage(vm: EvViewModel, ui: EvUiState) {
    var provider by remember(ui.activeOverlay) {
        mutableStateOf(if (ui.activeOverlay == OverlayPage.WILLOW_OTT) "Willow" else "FanCode")
    }
    val list = when (provider) {
        "SonyLIV" -> ui.sonyLivMatches
        "Willow" -> ui.willowMatches
        else -> ui.fancodeMatches
    }
    val w = cardWidthFor(2)
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageTitleBar("OTT", onBack = { vm.goHome() }) }
        item { PillRow(listOf("FanCode", "SonyLIV", "Willow"), provider) { provider = it } }
        item { Spacer(Modifier.height(8.dp)) }
        if (list.isEmpty()) {
            item { EmptyState("No $provider events at the moment.") }
        } else {
            gridRows(list, 2) { m -> EvMatchRailCard(m, { vm.openMatchServerSelector(m) }, cardWidth = w) }
        }
    }
}

@Composable
private fun EvVipPage(vm: EvViewModel, ui: EvUiState) {
    var code by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("All") }

    if (!ui.vipUnlocked) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageTitleBar("VIP Player", onBack = { vm.goHome() })
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                EvLogoMark(size = 56.dp)
                Spacer(Modifier.height(16.dp))
                Text("Enter VIP access code", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Spacer(Modifier.height(14.dp))
                SearchField(
                    code, { code = it.filter { c -> c.isDigit() }.take(12); wrong = false }, "Access code",
                    onSubmit = { wrong = !vm.tryUnlockVip(code) }
                )
                if (wrong) {
                    Spacer(Modifier.height(8.dp))
                    Text("Incorrect code. Please try again.", color = Color(0xFFFF6B6B), fontSize = 12.sp)
                }
                Spacer(Modifier.height(14.dp))
                LinkButton("Unlock", EvMagenta) { wrong = !vm.tryUnlockVip(code) }
            }
        }
        return
    }

    LaunchedEffect(Unit) { if (ui.vipChannels.isEmpty() && !ui.isVipLoading) vm.loadVipPlaylist() }
    val groups = remember(ui.vipChannels) { listOf("All") + ui.vipChannels.map { it.group }.filter { it.isNotBlank() }.distinct().take(80) }
    val filtered = remember(ui.vipChannels, query, group) {
        ui.vipChannels.filter { c ->
            (group == "All" || c.group == group) && (query.isBlank() || c.name.contains(query, true))
        }.take(400)
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            PageTitleBar("VIP Player", onBack = { vm.goHome() }, trailing = { Pill("Lock", false) { vm.lockVip() } })
        }
        item { SearchField(query, { query = it }, "Search VIP channels", Modifier.padding(horizontal = 12.dp)) }
        item { Spacer(Modifier.height(8.dp)) }
        item { PillRow(groups, group) { group = it } }
        when {
            ui.isVipLoading -> item { EmptyState("Loading channel list…") }
            ui.vipError != null -> item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    EmptyState(ui.vipError.orEmpty())
                    Pill("Retry", true) { vm.loadVipPlaylist() }
                }
            }
            filtered.isEmpty() -> item { EmptyState("No channels found.") }
            else -> items(filtered, key = { it.url + it.name }) { c -> VipRow(c) { vm.setStreamSession(ActiveStreamSession(c.url, c.name, "channel")) } }
        }
    }
}

@Composable
private fun VipRow(c: VipIptvChannel, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF14161F)),
            contentAlignment = Alignment.Center
        ) {
            if (c.logo.isNotBlank()) {
                AsyncImage(model = c.logo, contentDescription = null, modifier = Modifier.fillMaxSize().padding(4.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(c.group, color = EvTextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
