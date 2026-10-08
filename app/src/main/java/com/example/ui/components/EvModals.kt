package com.example.ui.components

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.data.CatalogItem
import com.example.data.EvHelpers
import com.example.data.EvRepository
import com.example.data.MatchFixture
import com.example.data.MatchStatus
import com.example.ui.theme.EvMagenta
import com.example.ui.theme.EvPurple
import com.example.ui.theme.EvSurface1
import com.example.ui.theme.EvSurface2
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Replicates `#sxv` / `#mwsOverlay` (Match Server Selector Modal):
 * - Banner / Team logos hero with live status badge & live countdown (`Starts in HH:MM:SS`)
 * - 30-minute pre-match unlock rule for sheet servers & live-status check for FanCode priority servers
 * - Red lock notice & shake + vibration feedback when tapping a locked pre-kickoff server
 */
@Composable
fun EvMatchServerModal(
    match: MatchFixture,
    onClose: () -> Unit,
    onSelectServer: (Int) -> Unit
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shakeOffset = remember { Animatable(0f) }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(match.id) {
        while (true) {
            delay(1000)
            nowMs = System.currentTimeMillis()
        }
    }

    val isLive = match.status == MatchStatus.LIVE ||
        (match.startTimeMs != Long.MAX_VALUE && match.startTimeMs > 0L && nowMs >= match.startTimeMs && match.status != MatchStatus.FINISHED)
    val isFinished = match.status == MatchStatus.FINISHED
    val prematchUnlockMs = 30 * 60 * 1000L
    val isSheetUnlocked = isLive || isFinished ||
        (match.startTimeMs != Long.MAX_VALUE && nowMs >= match.startTimeMs - prematchUnlockMs)

    val countdownText = remember(nowMs, match.startTimeMs, isLive, isFinished) {
        if (isLive || isFinished || match.startTimeMs == Long.MAX_VALUE || match.startTimeMs <= nowMs) {
            ""
        } else {
            val diffSec = ((match.startTimeMs - nowMs) / 1000L).coerceAtLeast(0L)
            val d = diffSec / 86400L
            val h = (diffSec % 86400L) / 3600L
            val m = (diffSec % 3600L) / 60L
            val s = diffSec % 60L
            val hhmmss = String.format("%02d:%02d:%02d", h, m, s)
            if (d > 0) "Starts in ${d}d $hhmmss" else "Starts in $hhmmss"
        }
    }

    fun triggerLockedFeedback() {
        try {
            val vib = context.getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {}
        scope.launch {
            listOf(-14f, 14f, -10f, 10f, -5f, 0f).forEach { target ->
                shakeOffset.animateTo(target, animationSpec = tween(45))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD4000000))
            .clickable { onClose() }
            .padding(16.dp)
            .testTag("match_server_modal"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .background(EvSurface1)
                .border(1.dp, Color(0x1FFFFFFF))
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState())
        ) {
            // `.sxv-hero`
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF161616))
            ) {
                if (match.bannerUrl.isNotBlank()) {
                    AsyncImage(
                        model = match.bannerUrl,
                        contentDescription = match.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (match.team1IconUrl.isNotBlank() || match.team2IconUrl.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally)
                    ) {
                        AsyncImage(
                            model = match.team1IconUrl,
                            contentDescription = match.team1Code,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(68.dp)
                        )
                        Text(
                            text = "v",
                            color = Color(0xCCFFFFFF),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        AsyncImage(
                            model = match.team2IconUrl,
                            contentDescription = match.team2Code,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(68.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x26101010), Color(0x8C101010), EvSurface1)
                            )
                        )
                )

                // Close button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0x99000000))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Bottom hero info (`.sxv-hi`)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(if (isLive) Color.White else Color(0x29FFFFFF))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (isLive) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black)
                                )
                            }
                            Text(
                                text = when {
                                    isLive -> "LIVE NOW"
                                    isFinished -> "FINISHED"
                                    else -> "UPCOMING"
                                },
                                color = if (isLive) Color.Black else Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = EvHelpers.evTeamName(match.title),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        lineHeight = 24.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = listOf(match.tournament, match.dateLabel).filter { it.isNotBlank() }.joinToString(" · "),
                            color = Color(0xFFC8C8C8),
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.5.sp
                        )
                        if (countdownText.isNotEmpty()) {
                            Text(
                                text = countdownText,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        }
                    }
                }
            }

            // `.sxv-body`
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (!isLive && !isFinished) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                            .background(Color(0x24E0155F))
                            .border(1.dp, Color(0x66E0155F))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "⏰ Match will be live at ${match.dateLabel.ifBlank { "kickoff time" }}",
                            color = Color(0xFFFFD0E0),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(
                    text = "Choose a server",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (match.servers.isEmpty()) {
                    Text(
                        text = "No servers available yet.",
                        color = Color(0xFFA7A7A7),
                        fontSize = 13.5.sp,
                        modifier = Modifier.padding(vertical = 14.dp)
                    )
                } else {
                    match.servers.forEachIndexed { idx, srv ->
                        val isReady = if (srv.isFcxServer) {
                            srv.fcxLive || isSheetUnlocked
                        } else {
                            isSheetUnlocked
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .background(Color(0xFF161616))
                                .border(
                                    width = 1.dp,
                                    color = if (srv.isPriority) Color(0xFFF5C518) else Color(0x18FFFFFF)
                                )
                                .clickable {
                                    if (isReady) {
                                        onSelectServer(idx)
                                    } else {
                                        triggerLockedFeedback()
                                    }
                                }
                                .padding(12.dp)
                                .testTag("server_item_$idx"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(52.dp)
                                    .height(36.dp)
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                val iconUrl = if (srv.isFcxServer) EvRepository.FANCODE_LOGO else srv.icon
                                if (iconUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = iconUrl,
                                        contentDescription = srv.text,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp)
                                    )
                                } else {
                                    Text(
                                        text = "${idx + 1}",
                                        color = Color(0xFFBABABA),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = srv.text,
                                        color = Color.White.copy(alpha = if (isReady) 1f else 0.55f),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (srv.isPriority) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFF5C518))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "PRIORITY",
                                                color = Color(0xFF111111),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 8.5.sp
                                            )
                                        }
                                    }
                                    if (srv.isLiveTvAutoConnected) {
                                        Box(
                                            modifier = Modifier
                                                .background(EvMagenta)
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "LIVE TV",
                                                color = Color.White,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 8.5.sp
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (isReady) srv.sub else "Opens ${match.dateLabel.ifBlank { "before kickoff" }}",
                                    color = Color(0xFFA7A7A7),
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(if (isReady) Color.White else Color(0x24FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isReady) Icons.Default.PlayArrow else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (isReady) Color.Black else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Replicates `#va-tv-modal` (TV Show Season & Episode picker modal).
 */
@Composable
fun EvTvEpisodeModal(
    item: CatalogItem,
    onDismiss: () -> Unit,
    onPlay: (Int, Int) -> Unit
) {
    var seasonText by remember { mutableStateOf("1") }
    var episodeText by remember { mutableStateOf("1") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB3000000))
            .clickable { onDismiss() }
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
                .clickable(enabled = false) {}
                .padding(20.dp)
        ) {
            Text(
                text = item.title.ifBlank { "Pick an episode" },
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Season",
                        color = Color(0x88FFFFFF),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    BasicTextField(
                        value = seasonText,
                        onValueChange = { seasonText = it.filter { ch -> ch.isDigit() } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(EvMagenta),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Episode",
                        color = Color(0x88FFFFFF),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    BasicTextField(
                        value = episodeText,
                        onValueChange = { episodeText = it.filter { ch -> ch.isDigit() } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(EvMagenta),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x18FFFFFF))
                        .clickable { onDismiss() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Cancel",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(EvMagenta)
                        .clickable {
                            val s = seasonText.toIntOrNull() ?: 1
                            val e = episodeText.toIntOrNull() ?: 1
                            onPlay(s, e)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "▶ Play",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * Replicates `#ev18-gate` (18+ Adult Content Age Confirmation Gate).
 */
@Composable
fun EvAge18GateModal(
    title: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD1000000))
            .clickable { onCancel() }
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(EvSurface2)
                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(18.dp))
                .clickable(enabled = false) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE0155F)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "18+",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Adult content",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 19.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "This title is meant for viewers aged 18 or older and may contain mature content. Please confirm your age to continue.",
                color = Color(0xB3FFFFFF),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFD4147A), EvPurple)
                        )
                    )
                    .clickable { onConfirm() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Yes, I am 18 or older",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x1AFFFFFF))
                    .clickable { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No, go back",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Replicates `#dmca-overlay` (Official DMCA & Copyright Disclaimer modal).
 */
@Composable
fun EvDmcaModal(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xBA000000))
            .clickable { onClose() }
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .heightIn(max = 560.dp)
                .background(Color.Black)
                .border(1.dp, Color(0x40F04F8C))
                .clickable(enabled = false) {}
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0x24F04F8C)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFFF04F8C)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "EV SPORTS",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Formerly SX SPORTS · Owned by Rishi Entertainment Verse Limited",
                        color = Color(0x73FFFFFF),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Official DMCA & Copyright Disclaimer",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 17.sp
            )
            Text(
                text = "DMCA NOTICE",
                color = Color(0xFFF04F8C),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            val paragraphs = listOf(
                "EV SPORTS is a browser-based platform and content discovery service. Formerly known as SX SPORTS, EV SPORTS does not host, upload, store, transmit, or broadcast sports streams, movies, TV channels, videos, or other media content on its own servers.",
                "EV SPORTS works primarily as a browser/interface that provides access to links and publicly available content hosted by independent third-party sources.",
                "All copyrights, trademarks, broadcasting rights, streaming rights, and other intellectual-property rights belong to their respective owners.",
                "If you are a copyright owner or an authorized representative and believe that material accessible through an external source infringes your rights, please contact the original third-party hosting provider or website operator responsible for that content.",
                "EV SPORTS — Formerly SX SPORTS • Owned by Rishi Entertainment Verse Limited"
            )
            paragraphs.forEach { p ->
                Text(
                    text = p,
                    color = Color(0xB8FFFFFF),
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x18FFFFFF))
                    .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(8.dp))
                    .clickable { onClose() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Close",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Replicates `#evlp` (In-App External Link Popup Viewer so Discord/Telegram/Social links stay inside the app).
 */
@Composable
fun EvInAppLinkPopup(
    url: String,
    onClose: () -> Unit,
    onOpenExternalBrowser: (String) -> Unit
) {
    BackHandler { onClose() }

    var reloadTick by remember { mutableStateOf(0) }
    val hostName = remember(url) {
        try {
            java.net.URI(url).host ?: url
        } catch (_: Exception) {
            url
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D13))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color.Black)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x1AFFFFFF))
                    .clickable { onClose() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = "✕", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Text(
                text = hostName,
                color = Color(0xCCFFFFFF),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x1AFFFFFF))
                    .clickable { reloadTick++ }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = "Reload", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x1AFFFFFF))
                    .clickable { onOpenExternalBrowser(url) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = "Open in browser", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    webViewClient = WebViewClient()
                }
            },
            update = { wv ->
                if (wv.tag != "$url#$reloadTick") {
                    wv.tag = "$url#$reloadTick"
                    wv.loadUrl(url)
                }
            },
            onRelease = { wv ->
                wv.stopLoading()
                wv.destroy()
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}
