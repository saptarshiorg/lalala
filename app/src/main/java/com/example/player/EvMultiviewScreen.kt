package com.example.player

import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.data.EvHelpers
import com.example.data.LiveChannel
import com.example.data.MatchFixture
import com.example.data.MatchStatus
import com.example.data.MultiviewSlotState
import com.example.ui.theme.EvMagenta
import com.example.ui.theme.EvSurface2
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Replicates `#sx-multiview-overlay` and `#mv-picker` (2 / 3 / 4 simultaneous stream slots).
 * Direct HLS/DASH streams in slots use embedded LibVLC instances; embed URLs use WebViews.
 */
@Composable
fun EvMultiviewOverlay(
    channels: List<LiveChannel>,
    matches: List<MatchFixture>,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    var layoutCount by remember { mutableIntStateOf(2) }
    val slots = remember {
        mutableStateListOf<MultiviewSlotState?>(null, null, null, null)
    }
    var pickerSlotIndex by remember { mutableIntStateOf(-1) }
    var pickerQuery by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // `#mv-topbar`
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xF0000000))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.clickable { onClose() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Back",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Text(
                    text = "MULTIVIEW",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x14FFFFFF))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(2, 3, 4).forEach { n ->
                        val active = layoutCount == n
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) EvMagenta else Color.Transparent)
                                .clickable { layoutCount = n }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = n.toString(),
                                color = if (active) Color.White else Color(0x99FFFFFF),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // `#mv-grid`
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (i in 0 until layoutCount) {
                    val slot = slots[i]
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF0A0A0A))
                            .border(1.dp, Color(0x18FFFFFF))
                    ) {
                        if (slot == null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { pickerSlotIndex = i },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Stream",
                                    tint = Color(0x88FFFFFF),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "ADD STREAM",
                                    color = Color(0x88FFFFFF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        } else {
                            MultiviewSlotPlayer(
                                slot = slot,
                                onToggleMute = {
                                    slots[i] = slot.copy(isMuted = !slot.isMuted)
                                },
                                onSwap = { pickerSlotIndex = i },
                                onRemove = { slots[i] = null }
                            )
                        }
                    }
                }
            }
        }

        // `#mv-picker` modal
        if (pickerSlotIndex >= 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xC8000000))
                    .clickable { pickerSlotIndex = -1 },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(EvSurface2)
                        .clickable(enabled = false) {}
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Add to Multiview",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        IconButton(onClick = { pickerSlotIndex = -1 }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xAAFFFFFF)
                            )
                        }
                    }

                    BasicTextField(
                        value = pickerQuery,
                        onValueChange = { pickerQuery = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                        cursorBrush = SolidColor(EvMagenta),
                        decorationBox = { inner ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x14FFFFFF))
                                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                if (pickerQuery.isEmpty()) {
                                    Text(
                                        text = "Search live channels & matches",
                                        color = Color(0x66FFFFFF),
                                        fontSize = 13.5.sp
                                    )
                                }
                                inner()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val q = pickerQuery.trim().lowercase()
                    val pickableChannels = channels.filter {
                        it.isLive && it.watchUrl.isNotBlank() && (q.isEmpty() || it.name.lowercase().contains(q))
                    }
                    val pickableMatches = matches.filter {
                        it.status == MatchStatus.LIVE && it.servers.isNotEmpty() && (q.isEmpty() || it.title.lowercase().contains(q))
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(pickableMatches) { m ->
                            val firstUrl = m.servers.first().url
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        slots[pickerSlotIndex] = MultiviewSlotState(
                                            url = firstUrl,
                                            name = m.title,
                                            isDirectStream = EvHelpers.sxIsDirectStreamUrl(firstUrl),
                                            isMuted = true
                                        )
                                        pickerSlotIndex = -1
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AsyncImage(
                                    model = m.bannerUrl.ifBlank { m.team1IconUrl },
                                    contentDescription = m.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black)
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
                                        text = m.tournament,
                                        color = Color(0x88FFFFFF),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        items(pickableChannels) { ch ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        slots[pickerSlotIndex] = MultiviewSlotState(
                                            url = ch.watchUrl,
                                            name = ch.name,
                                            isDirectStream = EvHelpers.sxIsDirectStreamUrl(ch.watchUrl),
                                            isMuted = true
                                        )
                                        pickerSlotIndex = -1
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AsyncImage(
                                    model = ch.iconUrl,
                                    contentDescription = ch.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black)
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
                                        text = ch.group,
                                        color = Color(0x88FFFFFF),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiviewSlotPlayer(
    slot: MultiviewSlotState,
    onToggleMute: () -> Unit,
    onSwap: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        if (slot.isDirectStream) {
            val libVlc = remember(slot.url) {
                LibVLC(context, arrayListOf("--network-caching=2000"))
            }
            val player = remember(libVlc) { MediaPlayer(libVlc) }

            DisposableEffect(player, slot.url) {
                try {
                    val media = Media(libVlc, Uri.parse(slot.url)).apply {
                        setHWDecoderEnabled(true, false)
                    }
                    player.media = media
                    media.release()
                    player.volume = if (slot.isMuted) 0 else 100
                    player.play()
                } catch (_: Exception) {}
                onDispose {
                    player.stop()
                    player.release()
                    libVlc.release()
                }
            }

            LaunchedEffect(slot.isMuted) {
                player.volume = if (slot.isMuted) 0 else 100
            }

            AndroidView(
                factory = { ctx ->
                    VLCVideoLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        player.attachViews(this, null, false, false)
                    }
                },
                onRelease = {
                    try {
                        player.detachViews()
                    } catch (_: Exception) {}
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        webViewClient = WebViewClient()
                        loadUrl(slot.url)
                    }
                },
                onRelease = { wv ->
                    wv.stopLoading()
                    wv.destroy()
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // `.mv-slot-bar`
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xDD000000))
                    )
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = slot.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (slot.isDirectStream) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0x99000000))
                        .border(1.dp, if (slot.isMuted) Color(0x88F0146E) else Color(0x2EFFFFFF), RoundedCornerShape(7.dp))
                        .clickable { onToggleMute() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (slot.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Mute",
                        tint = if (slot.isMuted) Color(0xFFFF6B9D) else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0x99000000))
                    .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(7.dp))
                    .clickable { onSwap() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Swap",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0x99000000))
                    .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(7.dp))
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
