package com.example.player

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.data.ActiveStreamSession
import com.example.data.CatalogItem
import com.example.data.EvHelpers
import com.example.data.EvRepository
import com.example.data.LiveChannel
import com.example.data.MatchFixture
import com.example.data.StreamSourceKind
import com.example.ui.theme.EvBg
import com.example.ui.theme.EvMagenta
import com.example.ui.theme.EvMagentaLight
import com.example.ui.theme.EvRedLive
import com.example.ui.theme.EvSurface2
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Replicates `#stream-player-overlay` and the embedded `#sx4k-player-src` (EV StreamPulse 4K UHD Player).
 *
 * CRITICAL ARCHITECTURE:
 * 1. Direct authorized HLS (`.m3u8`) and DASH (`.mpd`) streams — including FanCode streams resolved from `fancode.json` / `fan.json` and VIP IPTV `.m3u8` streams — play natively inside the app via embedded **LibVLC** (`org.videolan.android:libvlc-all`), while keeping the exact EV StreamPulse custom UI (`#stage`, `#top`, `#mctr`, `#bar`, `#seek`, `#golive`, `#menu`) as the visible interface.
 * 2. Third-party iframe/embed URLs (`vaplayer.ru`, external channel embed pages) play in a dedicated, clean-UA Android `WebView` with the `"Not playing? Reload / Open in browser"` helper chip (`#ev-embed-help`), without routing them through LibVLC.
 */
@Composable
fun EvStreamPulseOverlay(
    session: ActiveStreamSession,
    repository: EvRepository,
    allMatches: List<MatchFixture>,
    allChannels: List<LiveChannel>,
    allMovies: List<CatalogItem>,
    allTvShows: List<CatalogItem>,
    onClose: () -> Unit,
    onSelectServer: (Int) -> Unit,
    onSwitchFcxSource: (String) -> Unit,
    onSwitchStream: (ActiveStreamSession) -> Unit,
    onOpenInstagramCreator: () -> Unit,
    onViewAllMatches: () -> Unit,
    onViewAllChannels: () -> Unit,
    onOpenDmca: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    var isFullscreen by remember { mutableStateOf(false) }
    var showServersMenu by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showRailDrawer by remember { mutableStateOf(false) }
    var showAdblockBanner by remember { mutableStateOf(true) }

    // Resolve FanCode `play.html?id=...` to direct HLS `.m3u8` or classify direct vs embed
    var resolvedStreamUrl by remember(session.url, session.fcxSourceMode) { mutableStateOf("") }
    var fcxQualityVariants by remember(session.url) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isResolvingFcx by remember(session.url, session.fcxSourceMode) { mutableStateOf(false) }
    var fcxResolveError by remember(session.url, session.fcxSourceMode) { mutableStateOf<String?>(null) }

    val rawCandidateUrl = remember(session) {
        if (session.fcxSourceMode == "india" && session.fcxIndiaM3u8.isNotBlank()) {
            session.fcxIndiaM3u8
        } else {
            session.url
        }
    }

    val sourceKind = remember(rawCandidateUrl) {
        EvHelpers.classifyStreamUrl(rawCandidateUrl)
    }

    LaunchedEffect(rawCandidateUrl) {
        fcxResolveError = null
        if (sourceKind == StreamSourceKind.FANCODE_RESOLVER) {
            isResolvingFcx = true
            val res = repository.resolveFanCodePlayUrl(rawCandidateUrl)
            isResolvingFcx = false
            if (res != null && res.first.isNotBlank()) {
                resolvedStreamUrl = res.first
                fcxQualityVariants = res.second
            } else {
                fcxResolveError = "Stream not started or unavailable in feed yet"
                resolvedStreamUrl = ""
            }
        } else {
            resolvedStreamUrl = rawCandidateUrl
            fcxQualityVariants = emptyList()
        }
    }

    val useLibVlc = sourceKind == StreamSourceKind.DIRECT_LIBVLC ||
        sourceKind == StreamSourceKind.FANCODE_RESOLVER ||
        EvHelpers.sxIsDirectStreamUrl(resolvedStreamUrl)

    // Restore portrait orientation on exit if fullscreen locked landscape
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        activity?.requestedOrientation = if (isFullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler {
        if (isFullscreen) {
            toggleFullscreen()
        } else {
            onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isLandscape || isFullscreen) Color.Black else EvBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("stream_player_overlay")
    ) {
        if (isLandscape || isFullscreen) {
            // Full-bleed landscape player
            Box(modifier = Modifier.fillMaxSize()) {
                PlayerStageContent(
                    session = session,
                    useLibVlc = useLibVlc,
                    streamUrl = resolvedStreamUrl,
                    isResolving = isResolvingFcx,
                    resolveError = fcxResolveError,
                    fcxQualities = fcxQualityVariants,
                    isFullscreen = true,
                    onToggleFullscreen = ::toggleFullscreen,
                    onBack = onClose,
                    onOpenDmca = onOpenDmca,
                    onSelectQualityUrl = { newUrl -> resolvedStreamUrl = newUrl }
                )
            }
        } else {
            // Phone Portrait layout (`#ev-player-phone` CSS):
            // 16:9 sticky player at top -> Infobar (Title, EV STREAMPULSE · AD FREE badge, Servers, Quality, Fullscreen) -> Instagram Creator Banner & Related Rail underneath
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    PlayerStageContent(
                        session = session,
                        useLibVlc = useLibVlc,
                        streamUrl = resolvedStreamUrl,
                        isResolving = isResolvingFcx,
                        resolveError = fcxResolveError,
                        fcxQualities = fcxQualityVariants,
                        isFullscreen = false,
                        onToggleFullscreen = ::toggleFullscreen,
                        onBack = onClose,
                        onOpenDmca = onOpenDmca,
                        onSelectQualityUrl = { newUrl -> resolvedStreamUrl = newUrl }
                    )
                }

                // `#stream-player-infobar`
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EvBg)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = EvHelpers.evTeamName(session.title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                            Text(
                                text = "LIVE",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            )
                        }
                        if (useLibVlc) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0x99000000))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(5.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "EV STREAMPULSE · AD FREE",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 9.5.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Controls row: Servers dropdown, World/India switch, Fullscreen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x1FFFFFFF))
                                    .clickable { showServersMenu = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("player_servers_button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = "Servers",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Servers (${session.servers.size.coerceAtLeast(1)})",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            DropdownMenu(
                                expanded = showServersMenu,
                                onDismissRequest = { showServersMenu = false },
                                modifier = Modifier.background(Color(0xFF14151D))
                            ) {
                                val serversList = session.servers.ifEmpty {
                                    listOf(com.example.data.StreamServer(text = "Server 1", url = session.url))
                                }
                                serversList.forEachIndexed { idx, srv ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (idx == session.selectedServerIndex) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = EvMagenta,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                } else {
                                                    Spacer(modifier = Modifier.size(16.dp))
                                                }
                                                Text(
                                                    text = srv.text,
                                                    color = Color.White,
                                                    fontWeight = if (idx == session.selectedServerIndex) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        },
                                        onClick = {
                                            showServersMenu = false
                                            onSelectServer(idx)
                                        }
                                    )
                                }
                                if (session.fcxMatchId.isNotBlank()) {
                                    HorizontalDivider(color = Color(0x22FFFFFF))
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "FanCode Source: World",
                                                color = if (session.fcxSourceMode == "world") EvMagentaLight else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        },
                                        onClick = {
                                            showServersMenu = false
                                            onSwitchFcxSource("world")
                                        }
                                    )
                                    DropdownMenuItem(
                                        enabled = session.fcxIndiaM3u8.isNotBlank(),
                                        text = {
                                            Text(
                                                text = "FanCode Source: India (CNP-TV)",
                                                color = if (session.fcxSourceMode == "india") EvMagentaLight else Color.White.copy(alpha = if (session.fcxIndiaM3u8.isNotBlank()) 1f else 0.4f),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        },
                                        onClick = {
                                            showServersMenu = false
                                            onSwitchFcxSource("india")
                                        }
                                    )
                                }
                                HorizontalDivider(color = Color(0x22FFFFFF))
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = Color(0xFFFF8FBB),
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = "Report an Issue",
                                                color = Color(0xCCFFFFFF),
                                                fontSize = 13.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        showServersMenu = false
                                        Toast.makeText(context, "Stream not working? Tell us on Discord: rishi.ev", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }

                        if (!useLibVlc && showAdblockBanner) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x14FFFFFF))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Embed streamProtected by EV ad-guard",
                                    color = Color(0xBBFFFFFF),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        IconButton(
                            onClick = ::toggleFullscreen,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0x1FFFFFFF))
                                .testTag("player_fullscreen_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // `#stream-player-rail` (Instagram Creator Banner + Related 5 items + View All button)
                PlayerRelatedRailSection(
                    session = session,
                    allMatches = allMatches,
                    allChannels = allChannels,
                    allMovies = allMovies,
                    allTvShows = allTvShows,
                    onSwitchStream = onSwitchStream,
                    onOpenInstagramCreator = onOpenInstagramCreator,
                    onViewAllMatches = onViewAllMatches,
                    onViewAllChannels = onViewAllChannels,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PlayerStageContent(
    session: ActiveStreamSession,
    useLibVlc: Boolean,
    streamUrl: String,
    isResolving: Boolean,
    resolveError: String?,
    fcxQualities: List<Pair<String, String>>,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onOpenDmca: () -> Unit,
    onSelectQualityUrl: (String) -> Unit
) {
    if (useLibVlc) {
        LibVlcStreamPulseStage(
            streamUrl = streamUrl,
            title = session.title,
            isResolvingExternal = isResolving,
            externalError = resolveError,
            fcxQualities = fcxQualities,
            isFullscreen = isFullscreen,
            onToggleFullscreen = onToggleFullscreen,
            onBack = onBack,
            onOpenDmca = onOpenDmca,
            onSelectQualityUrl = onSelectQualityUrl
        )
    } else {
        EmbedWebViewStage(
            embedUrl = streamUrl,
            title = session.title,
            isFullscreen = isFullscreen,
            onToggleFullscreen = onToggleFullscreen,
            onBack = onBack
        )
    }
}

/**
 * Native LibVLC engine wrapped with the exact `EV StreamPulse` 4K UHD player UI from `#sx4k-player-src`.
 */
@Composable
private fun LibVlcStreamPulseStage(
    streamUrl: String,
    title: String,
    isResolvingExternal: Boolean,
    externalError: String?,
    fcxQualities: List<Pair<String, String>>,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onOpenDmca: () -> Unit,
    onSelectQualityUrl: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var hasStarted by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isMuted by remember { mutableStateOf(false) }
    var vividMode by remember { mutableStateOf(true) }
    var currentTimeMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var videoHeight by remember { mutableIntStateOf(0) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var selectedQualityLabel by remember { mutableStateOf("Auto") }
    var showControls by remember { mutableStateOf(true) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var doubleTapFlashSide by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    val libVlc = remember {
        val options = arrayListOf(
            "--network-caching=2000",
            "--live-caching=2000",
            "--http-reconnect",
            "--avcodec-skiploopfilter=1",
            "-vvv"
        )
        LibVLC(context, options)
    }

    val mediaPlayer = remember(libVlc) {
        MediaPlayer(libVlc)
    }

    // Bind MediaPlayer events
    DisposableEffect(mediaPlayer) {
        val listener = MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> {
                    isBuffering = true
                    errorMessage = null
                }
                MediaPlayer.Event.Buffering -> {
                    isBuffering = event.buffering < 95f
                }
                MediaPlayer.Event.Playing -> {
                    isPlaying = true
                    isBuffering = false
                    hasStarted = true
                    errorMessage = null
                }
                MediaPlayer.Event.Paused -> {
                    isPlaying = false
                }
                MediaPlayer.Event.Stopped, MediaPlayer.Event.EndReached -> {
                    isPlaying = false
                }
                MediaPlayer.Event.EncounteredError -> {
                    isPlaying = false
                    isBuffering = false
                    errorMessage = "Stream unavailable — The stream isn’t live right now, or its link has changed."
                }
                MediaPlayer.Event.TimeChanged -> {
                    currentTimeMs = event.timeChanged
                }
                MediaPlayer.Event.LengthChanged -> {
                    durationMs = event.lengthChanged
                }
                MediaPlayer.Event.Vout -> {
                    val track = mediaPlayer.currentVideoTrack
                    if (track != null) {
                        videoHeight = track.height
                        videoWidth = track.width
                    }
                }
            }
        }
        mediaPlayer.setEventListener(listener)
        onDispose {
            mediaPlayer.setEventListener(null)
            mediaPlayer.stop()
            mediaPlayer.release()
            libVlc.release()
        }
    }

    // Lifecycle pause/resume
    DisposableEffect(lifecycleOwner, mediaPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (mediaPlayer.isPlaying) mediaPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (hasStarted && !mediaPlayer.isPlaying && errorMessage == null) {
                        mediaPlayer.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Load stream URL into LibVLC
    LaunchedEffect(streamUrl, retryTrigger) {
        if (streamUrl.isBlank()) return@LaunchedEffect
        isBuffering = true
        errorMessage = null
        hasStarted = false
        try {
            mediaPlayer.stop()
            val media = Media(libVlc, Uri.parse(streamUrl)).apply {
                setHWDecoderEnabled(true, false)
                addOption(":network-caching=2000")
                addOption(":clock-jitter=0")
                addOption(":clock-synchro=0")
                addOption(":http-user-agent=Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36")
            }
            mediaPlayer.media = media
            media.release()
            mediaPlayer.volume = if (isMuted) 0 else 100
            mediaPlayer.play()
        } catch (e: Exception) {
            isBuffering = false
            errorMessage = "Stream unavailable — Could not open media stream."
        }
    }

    // Auto-hide controls after 3s when playing
    LaunchedEffect(showControls, isPlaying, showSettingsSheet) {
        if (showControls && isPlaying && !showSettingsSheet) {
            delay(3200)
            showControls = false
        }
    }

    // Clear double-tap flash
    LaunchedEffect(doubleTapFlashSide) {
        if (doubleTapFlashSide != null) {
            delay(500)
            doubleTapFlashSide = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        if (offset.x < width * 0.4f) {
                            val target = (mediaPlayer.time - 10_000L).coerceAtLeast(0L)
                            mediaPlayer.time = target
                            doubleTapFlashSide = "L"
                        } else if (offset.x > width * 0.6f) {
                            val target = mediaPlayer.time + 10_000L
                            mediaPlayer.time = target
                            doubleTapFlashSide = "R"
                        } else {
                            if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.play()
                        }
                    }
                )
            }
    ) {
        // VLCVideoLayout surface
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    keepScreenOn = true
                    mediaPlayer.attachViews(this, null, false, false)
                }
            },
            update = { _ -> },
            onRelease = {
                try {
                    mediaPlayer.detachViews()
                } catch (_: Exception) {}
            },
            modifier = Modifier.fillMaxSize()
        )

        // Double-tap ±10s indicator (`#dbl`)
        if (doubleTapFlashSide != null) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.35f)
                    .align(if (doubleTapFlashSide == "L") Alignment.CenterStart else Alignment.CenterEnd)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0x38FFFFFF), Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (doubleTapFlashSide == "L") "−10s" else "+10s",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }
        }

        // Initial Connecting Loader (`#loader`)
        if ((isResolvingExternal || (isBuffering && !hasStarted)) && errorMessage == null && externalError == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE6000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = EvMagenta,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(42.dp)
                    )
                    Text(
                        text = "CONNECTING TO STREAM",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Streams can take 5-15 seconds to load fully.",
                        color = Color(0x88FFFFFF),
                        fontSize = 11.5.sp
                    )
                }
            }
        }

        // Mid-stream buffering spinner (`#spin`)
        if (isBuffering && hasStarted && errorMessage == null) {
            CircularProgressIndicator(
                color = EvMagenta,
                strokeWidth = 3.dp,
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.Center)
            )
        }

        // Error overlay (`#err`)
        val activeErr = externalError ?: errorMessage
        if (activeErr != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xEB000000))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(340.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0x33F0146E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = EvMagentaLight,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Stream unavailable",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = activeErr,
                        color = Color(0xAAFFFFFF),
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(EvMagenta)
                                .clickable { retryTrigger++ }
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Try again",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x1AFFFFFF))
                                .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(10.dp))
                                .clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Stream URL", streamUrl))
                                    Toast.makeText(context, "Stream link copied", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Copy link",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Hotstar-style Mobile & Desktop Controls (`#mtop`, `#mctr`, `#bar`)
        AnimatedVisibility(
            visible = showControls && activeErr == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xB8000000), Color.Transparent)
                            )
                        )
                )
                // Bottom Scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xCC000000))
                            )
                        )
                )

                // Top Row: Back button, EV StreamPulse brand, LIVE badge, Title, Quality chip, Settings, Fullscreen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0x55000000))
                            .testTag("player_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = "EV StreamPulse",
                        color = Color(0xEEFFFFFF),
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic,
                        fontSize = 12.sp
                    )

                    Box(
                        modifier = Modifier
                            .background(Color.White)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black)
                            )
                            Text(
                                text = "LIVE",
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.5.sp
                            )
                        }
                    }

                    Text(
                        text = EvHelpers.evTeamName(title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (videoHeight > 0) {
                        val is4K = videoHeight >= 2000 || videoWidth >= 3800
                        val qLabel = if (is4K) "4K" else "${videoHeight}p"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (is4K) Brush.linearGradient(listOf(EvMagentaLight, EvMagenta))
                                    else Brush.linearGradient(listOf(Color(0x88000000), Color(0x88000000)))
                                )
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = qLabel,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            isMuted = !isMuted
                            mediaPlayer.volume = if (isMuted) 0 else 100
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { showSettingsSheet = !showSettingsSheet },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }

                // Center Play / Pause / ±10s (`#mctr`)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(36.dp)
                ) {
                    IconButton(
                        onClick = {
                            mediaPlayer.time = (mediaPlayer.time - 10_000L).coerceAtLeast(0L)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0x44000000))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Back 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.play()
                        },
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(Color(0x66000000))
                            .testTag("player_play_pause_btn")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            mediaPlayer.time = mediaPlayer.time + 10_000L
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0x44000000))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Forward 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Bottom Seekbar + LIVE indicator (`#bar`)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isSeekable = durationMs > 0L
                        var sliderPos by remember(currentTimeMs, durationMs) {
                            mutableFloatStateOf(
                                if (durationMs > 0L) (currentTimeMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                                else 1f
                            )
                        }
                        Slider(
                            value = sliderPos,
                            onValueChange = { v ->
                                sliderPos = v
                                if (isSeekable) {
                                    mediaPlayer.time = (v * durationMs).toLong()
                                }
                            },
                            enabled = isSeekable,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = EvMagenta,
                                inactiveTrackColor = Color(0x44FFFFFF),
                                disabledThumbColor = Color.White,
                                disabledActiveTrackColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(20.dp)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.clickable {
                                if (durationMs > 0L) mediaPlayer.time = durationMs - 1000L
                                if (!mediaPlayer.isPlaying) mediaPlayer.play()
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(EvRedLive)
                            )
                            Text(
                                text = "LIVE",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Settings Menu (`#menu` bottom sheet)
        if (showSettingsSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000))
                    .clickable { showSettingsSheet = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                        .background(EvSurface2)
                        .clickable(enabled = false) {}
                        .padding(16.dp)
                ) {
                    Text(
                        text = "QUALITY & PICTURE",
                        color = Color(0x88FFFFFF),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (fcxQualities.isNotEmpty()) {
                        fcxQualities.forEach { (qName, qUrl) ->
                            val isSelected = selectedQualityLabel == qName || streamUrl == qUrl
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0x33F0146E) else Color.Transparent)
                                    .clickable {
                                        selectedQualityLabel = qName
                                        onSelectQualityUrl(qUrl)
                                        showSettingsSheet = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = qName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = EvMagenta,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x33F0146E))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Auto / Highest Available (LibVLC Adaptive)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = EvMagenta,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(
                        color = Color(0x22FFFFFF),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Vivid picture",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Boosts contrast and colour",
                                color = Color(0x88FFFFFF),
                                fontSize = 11.5.sp
                            )
                        }
                        Switch(
                            checked = vividMode,
                            onCheckedChange = { vividMode = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = EvMagenta
                            )
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showSettingsSheet = false
                                onOpenDmca()
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DMCA & Copyright Notice",
                            color = Color(0xBBFFFFFF),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Preserved iframe/embed WebView player for third-party embeds (`vaplayer.ru`, web channel embeds).
 * Strips `; wv` from User-Agent (`#ev-webview-compat`) and shows the `"Not playing? Reload"` helper chip.
 */
@Composable
private fun EmbedWebViewStage(
    embedUrl: String,
    title: String,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit
) {
    var isLoading by remember(embedUrl) { mutableStateOf(true) }
    var showHelpChip by remember(embedUrl) { mutableStateOf(false) }
    var reloadNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(embedUrl, reloadNonce) {
        isLoading = true
        showHelpChip = false
        delay(8500)
        showHelpChip = true
        delay(15000)
        showHelpChip = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        loadsImagesAutomatically = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        // Clean UA without "; wv" marker as required by #ev-webview-compat
                        userAgentString = userAgentString
                            .replace("; wv)", ")")
                            .replace(Regex("\\sVersion/[\\d.]+"), "")
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val reqUrl = request?.url?.toString().orEmpty()
                            // Block unauthorized popups/redirects to ad domains while allowing same-embed navigation
                            if (!reqUrl.startsWith("http://", true) && !reqUrl.startsWith("https://", true)) {
                                return true
                            }
                            return false
                        }
                    }
                }
            },
            update = { webView ->
                if (embedUrl.isNotBlank() && webView.tag != "$embedUrl#$reloadNonce") {
                    webView.tag = "$embedUrl#$reloadNonce"
                    webView.loadUrl(embedUrl)
                }
            },
            onRelease = { webView ->
                webView.stopLoading()
                webView.destroy()
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = EvMagenta,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "LOADING STREAM",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // Top Back & Fullscreen floating buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (isFullscreen) {
                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x66000000))
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Exit Fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // `#ev-embed-help` chip ("Not playing? Reload")
        if (showHelpChip) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xEB0C0D13))
                    .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(999.dp))
                    .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Not playing?",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(EvMagenta)
                        .clickable {
                            reloadNonce++
                            showHelpChip = false
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Reload",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                }
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Hide",
                    tint = Color(0xB3FFFFFF),
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { showHelpChip = false }
                )
            }
        }
    }
}

/**
 * Replicates `#stream-player-rail`:
 * - `#ev-igb` Instagram Creator Banner (`@saaptaarshii`)
 * - 5 related items (`MORE MATCHES` / `MORE CHANNELS` / `More like this`)
 * - Premium `View All Matches` / `View All Channels` button (`.sp-rail-viewall`)
 */
@Composable
private fun PlayerRelatedRailSection(
    session: ActiveStreamSession,
    allMatches: List<MatchFixture>,
    allChannels: List<LiveChannel>,
    allMovies: List<CatalogItem>,
    allTvShows: List<CatalogItem>,
    onSwitchStream: (ActiveStreamSession) -> Unit,
    onOpenInstagramCreator: () -> Unit,
    onViewAllMatches: () -> Unit,
    onViewAllChannels: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMatch = session.type == "match"
    val isMovieOrTv = session.type == "movie" || session.type == "tv"

    val railHeader = when {
        isMovieOrTv -> "MORE LIKE THIS"
        isMatch -> "MORE MATCHES"
        else -> "MORE CHANNELS"
    }

    LazyColumn(
        modifier = modifier
            .background(EvBg)
            .padding(horizontal = 16.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp, bottom = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(15.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFFF5A623), Color(0xFFE0155F))
                            )
                        )
                )
                Text(
                    text = railHeader,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.5.sp,
                    letterSpacing = 0.8.sp
                )
            }
        }

        // `#ev-igb` Follow the creator on Instagram banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF140004),
                                Color(0xFF3A000C),
                                Color(0xFF7D0C20)
                            )
                        )
                    )
                    .clickable { onOpenInstagramCreator() }
                    .padding(16.dp)
                    .testTag("instagram_creator_banner")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "INSTAGRAM",
                        color = Color(0xFFFF5266),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Follow the creator of the Service",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "@saaptaarshii",
                        color = Color(0xFFFFB3BD),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFFF2D46), Color(0xFFB0001C))
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "Follow",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }
        }

        when {
            isMovieOrTv -> {
                val pool = if (session.type == "tv") allTvShows else allMovies
                val related = pool.filter { it.title != session.title && it.posterUrl.isNotBlank() }.take(5)
                itemsIndexed(related) { _, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSwitchStream(
                                    ActiveStreamSession(
                                        url = item.embedUrl,
                                        title = "${item.title}${if (item.year.isNotBlank()) " (${item.year})" else ""}",
                                        type = item.type
                                    )
                                )
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(
                            model = item.posterUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(96.dp)
                                .height(56.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF14151D))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = listOf(item.type.uppercase(), item.year, item.genre.split(",").firstOrNull().orEmpty())
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                color = Color(0x88FFFFFF),
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            isMatch -> {
                val liveOthers = allMatches.filter { it.title != session.title && it.servers.isNotEmpty() }.take(5)
                itemsIndexed(liveOthers) { _, m ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val firstSrv = m.servers.first()
                                onSwitchStream(
                                    ActiveStreamSession(
                                        url = firstSrv.url,
                                        title = m.title,
                                        type = "match",
                                        servers = m.servers,
                                        selectedServerIndex = 0,
                                        fcxMatchId = m.fcxMatchId,
                                        fcxIndiaM3u8 = m.fcxIndiaM3u8
                                    )
                                )
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val thumb = m.bannerUrl.ifBlank { m.team1IconUrl }
                        AsyncImage(
                            model = thumb,
                            contentDescription = m.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(96.dp)
                                .height(54.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF14151D))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = m.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "LIVE · ${m.tournament}",
                                color = EvMagentaLight,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFF5A623), Color(0xFFE0155F), Color(0xFF9B1FE8))
                                )
                            )
                            .clickable {
                                onViewAllMatches()
                            }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "VIEW ALL MATCHES ›",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            else -> {
                val otherChans = allChannels.filter { it.isLive && it.name != session.title }.take(5)
                itemsIndexed(otherChans) { _, ch ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSwitchStream(
                                    ActiveStreamSession(
                                        url = ch.watchUrl,
                                        title = ch.name,
                                        type = "channel"
                                    )
                                )
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(
                            model = ch.iconUrl,
                            contentDescription = ch.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF14151D))
                                .padding(4.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ch.name,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "LIVE · ${ch.group}",
                                color = EvMagentaLight,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFF5A623), Color(0xFFE0155F), Color(0xFF9B1FE8))
                                )
                            )
                            .clickable {
                                onViewAllChannels()
                            }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "VIEW ALL CHANNELS ›",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}
