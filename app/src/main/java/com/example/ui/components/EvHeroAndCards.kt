package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.data.CatalogItem
import com.example.data.EvHelpers
import com.example.data.HeroSlide
import com.example.data.LiveChannel
import com.example.data.MatchFixture
import com.example.data.MatchStatus
import com.example.ui.theme.EvBg
import com.example.ui.theme.EvMagenta
import com.example.ui.theme.EvMagentaLight
import com.example.ui.theme.EvPurple
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Replicates `#hs-mhero` (Mobile Stacked-Card Hero with finger-follow swipe & auto-advance)
 * and `#hs-hero` (Wide screen / Tablet Hotstar Hero with blurred edge-dissolved backdrop & thumbnail strip).
 */
@Composable
fun EvHeroBannerSection(
    slides: List<HeroSlide>,
    onPlaySlide: (HeroSlide) -> Unit,
    onMoreMatches: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (slides.isEmpty()) return

    val config = LocalConfiguration.current
    val isWideScreen = config.screenWidthDp >= 840

    var currentIndex by remember { mutableIntStateOf(0) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(slides.size, currentIndex) {
        if (slides.size > 1) {
            delay(5200)
            currentIndex = (currentIndex + 1) % slides.size
        }
    }

    if (isWideScreen) {
        // Desktop / Tablet `#hs-hero` full-width banner with thumbnail strip
        val activeSlide = slides[currentIndex.coerceIn(slides.indices)]
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(440.dp)
                .background(Color(0xFF07080C))
        ) {
            // Blurred backdrop
            AsyncImage(
                model = activeSlide.bgImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(32.dp)
            )
            // Center sharp image
            AsyncImage(
                model = activeSlide.bgImageUrl,
                contentDescription = activeSlide.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Gradient Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xF5000000),
                                Color(0xCC000000),
                                Color(0x44000000),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x66000000),
                                Color(0xFF0C0D13)
                            )
                        )
                    )
            )

            // Left text info (`#hs-hero .txt`)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 40.dp, bottom = 56.dp)
                    .width(420.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (activeSlide.isLive) {
                        Row(
                            modifier = Modifier
                                .background(Color.White)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black)
                            )
                            Text(
                                text = "LIVE",
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = activeSlide.badge.uppercase(),
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.5.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = activeSlide.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    lineHeight = 29.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (activeSlide.subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = activeSlide.subtitle,
                        color = Color(0xB8FFFFFF),
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        maxLines = 2
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFF04F8C), Color(0xFFE0155F))
                                )
                            )
                            .clickable { onPlaySlide(activeSlide) }
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Watch Now",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x26FFFFFF))
                            .clickable { onMoreMatches() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "More Matches",
                            tint = Color.White
                        )
                    }
                }
            }

            // Right thumbnail strip (`#hs-hero .thumbs`)
            LazyRow(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 36.dp, bottom = 56.dp)
                    .width(360.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(slides) { idx, sl ->
                    val isSel = idx == currentIndex
                    AsyncImage(
                        model = sl.bgImageUrl,
                        contentDescription = sl.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(88.dp)
                            .height(50.dp)
                            .border(
                                width = if (isSel) 2.dp else 0.dp,
                                color = if (isSel) Color.White else Color.Transparent
                            )
                            .clickable { currentIndex = idx }
                    )
                }
            }
        }
    } else {
        // Mobile Stacked-Card Hero (`#hs-mhero` / `.mh-slide`)
        val cardWidthDp = (config.screenWidthDp * 0.86f).coerceAtMost(420f).dp
        val cardHeightDp = cardWidthDp * 1.15f

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(cardHeightDp + 20.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x4DD4147A),
                            Color(0x387B2FF7),
                            Color.Transparent
                        )
                    )
                )
                .pointerInput(slides.size) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount ->
                            dragOffsetX += dragAmount
                        },
                        onDragEnd = {
                            if (abs(dragOffsetX) > 65f && slides.isNotEmpty()) {
                                currentIndex = if (dragOffsetX < 0) {
                                    (currentIndex + 1) % slides.size
                                } else {
                                    (currentIndex - 1 + slides.size) % slides.size
                                }
                            }
                            dragOffsetX = 0f
                        },
                        onDragCancel = { dragOffsetX = 0f }
                    )
                }
                .padding(start = 16.dp, top = 8.dp)
                .testTag("mobile_stacked_hero")
        ) {
            // Render top 3 stacked cards in reverse so index 0 is on top
            for (stackPos in 2 downTo 0) {
                if (slides.isEmpty()) break
                val itemIdx = (currentIndex + stackPos) % slides.size
                val slide = slides[itemIdx]

                val animatedX by animateDpAsState(
                    targetValue = (stackPos * 22).dp,
                    animationSpec = tween(380, easing = FastOutSlowInEasing),
                    label = "cardX"
                )
                val animatedScale by animateFloatAsState(
                    targetValue = 1f - stackPos * 0.055f,
                    animationSpec = tween(380, easing = FastOutSlowInEasing),
                    label = "cardScale"
                )

                val extraDragPx = if (stackPos == 0) dragOffsetX.roundToInt() else 0

                Box(
                    modifier = Modifier
                        .width(cardWidthDp)
                        .height(cardHeightDp)
                        .offset {
                            IntOffset(animatedX.roundToPx() + extraDragPx, 0)
                        }
                        .scale(animatedScale)
                        .zIndex((10 - stackPos).toFloat())
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFF0C0D13))
                        .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(26.dp))
                        .clickable {
                            if (stackPos == 0) {
                                onPlaySlide(slide)
                            } else {
                                currentIndex = itemIdx
                            }
                        }
                ) {
                    // Blurred background fill (`#hs-hero-v2` `.mh-bg.mh-land::before`)
                    AsyncImage(
                        model = slide.bgImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(24.dp)
                    )

                    // Top 16:9 sharp image (`::after`)
                    AsyncImage(
                        model = slide.bgImageUrl,
                        contentDescription = slide.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .align(Alignment.TopCenter)
                    )

                    // Smooth gradient vignette fading top 16:9 image into dark bottom
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color(0x4408090E),
                                    0.25f to Color.Transparent,
                                    0.50f to Color(0x8808090E),
                                    0.72f to Color(0xE608090E),
                                    1.0f to Color(0xFA08090E)
                                )
                            )
                    )

                    // Depth dim overlay for back cards
                    if (stackPos > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = stackPos * 0.22f))
                        )
                    }

                    // Bottom-left info (`.mh-info`)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 18.dp, end = 82.dp, bottom = 20.dp)
                    ) {
                        // Category / LIVE badge (`.mh-badge`: white pill with black text)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White)
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = slide.badge.uppercase(),
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = slide.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            lineHeight = 23.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (slide.subtitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = slide.subtitle,
                                color = Color(0xB8FFFFFF),
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Round Play Button bottom-right (`.mh-play`)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 14.dp, bottom = 18.dp)
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFECEEF3), Color(0xFFA9AFBD))
                                )
                            )
                            .clickable { onPlaySlide(slide) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color(0xFF111111),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Replicates `.sxs-card` (Match card in Sports & Home rails):
 * 16:9 thumbnail (banner or team logos vs layout) + white/black `LIVE` badge or time badge + centered title bar below.
 */
@Composable
fun EvMatchRailCard(
    match: MatchFixture,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: androidx.compose.ui.unit.Dp = 152.dp
) {
    Column(
        modifier = modifier
            .width(cardWidth)
            .clickable { onClick() }
            .testTag("match_card_${match.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF1A1A1A), Color.Black)
                    )
                )
        ) {
            if (match.bannerUrl.isNotBlank()) {
                AsyncImage(
                    model = match.bannerUrl,
                    contentDescription = match.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // `.sxs-tm` Team 1 vs Team 2 badge layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TeamBadgeMini(
                        code = match.team1Code.ifBlank { match.title.substringBefore(" vs ").take(3) },
                        iconUrl = match.team1IconUrl
                    )
                    Text(
                        text = "v",
                        color = Color(0xB3FFFFFF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    TeamBadgeMini(
                        code = match.team2Code.ifBlank { match.title.substringAfter(" vs ", "TM").take(3) },
                        iconUrl = match.team2IconUrl
                    )
                }
            }

            // `.sxs-live` (White background, black text & black dot) or `.sxs-time` / `.sxs-end`
            when (match.status) {
                MatchStatus.LIVE -> {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color.White)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
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
                MatchStatus.FINISHED -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color(0xA6000000))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ENDED",
                            color = Color(0xFFE8EAF0),
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.5.sp
                        )
                    }
                }
                MatchStatus.UPCOMING -> {
                    if (match.dateLabel.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .background(Color(0xA6000000))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = match.dateLabel,
                                color = Color(0xFFE8EAF0),
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = EvHelpers.evTeamName(match.title),
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TeamBadgeMini(code: String, iconUrl: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (iconUrl.isNotBlank()) {
            AsyncImage(
                model = iconUrl,
                contentDescription = code,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(34.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4A0A2C))
                    .border(1.dp, Color(0x40FFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = code.take(3).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp
                )
            }
        }
        Text(
            text = code.take(4).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 10.5.sp,
            maxLines = 1
        )
    }
}

/**
 * Replicates `.sxs-po` / `.hsm-card` (Movie / TV Show 2:3 Poster Card)
 * with optional Top-10 outlined rank number (`#sxs-home .hs-k-movies .sxs-card:nth-child(-n+10)`)
 * and `18+` badge (`.ev18`).
 */
@Composable
fun EvPosterCard(
    item: CatalogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rankNumber: Int? = null,
    cardWidth: androidx.compose.ui.unit.Dp = 114.dp
) {
    Box(
        modifier = modifier
            .width(cardWidth)
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF161616))
            .clickable { onClick() }
            .testTag("poster_card_${item.id}")
    ) {
        if (item.posterUrl.isNotBlank()) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.title,
                    color = Color(0xFFE8EBF3),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // `18+` adult badge (`.ev18`)
        if (item.isAdult18) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE0155F))
                    .padding(horizontal = 5.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "18+",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.5.sp
                )
            }
        }

        // Top-10 outlined number (`counter(hsn)`)
        if (rankNumber != null && rankNumber in 1..10) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.7f)
                    .fillMaxHeight(0.55f)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xBF000000), Color.Transparent)
                        )
                    )
            )
            Text(
                text = rankNumber.toString(),
                style = TextStyle(
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 48.sp,
                    shadow = Shadow(
                        color = Color.Black,
                        blurRadius = 12f
                    )
                ),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 0.dp)
            )
        }
    }
}

/**
 * Replicates `.evc` (Mobile Live TV square tile with blurred logo backdrop + sharp centered logo + LIVE badge + heart icon).
 */
@Composable
fun EvChannelSquareTile(
    channel: LiveChannel,
    isFavourite: Boolean,
    onClick: () -> Unit,
    onToggleFavourite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .testTag("channel_tile_${channel.name}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF14161F))
        ) {
            if (channel.iconUrl.isNotBlank()) {
                // Blurred background copy (`img.bgi`)
                AsyncImage(
                    model = channel.iconUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.35f)
                        .blur(12.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000))
                )
                // Sharp foreground logo (`img.fg`)
                AsyncImage(
                    model = channel.iconUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            } else {
                Text(
                    text = channel.name.take(3).uppercase(),
                    color = Color(0x88FFFFFF),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // LIVE badge top-left
            if (channel.isLive) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .background(Color.White)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.5.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                    )
                    Text(
                        text = "LIVE",
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 8.sp
                    )
                }
            }

            // Heart favourite button top-right (`.sx-heart-btn`)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000))
                    .clickable { onToggleFavourite() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favourite",
                    tint = if (isFavourite) EvMagenta else Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = channel.name,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = channel.group,
            color = Color(0x73FFFFFF),
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Replicates `.sxs-tt.evt` (Popular Tournament card with official logo & frosted backdrop).
 */
@Composable
fun EvTournamentTileCard(
    tournamentName: String,
    hasLive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val meta = remember(tournamentName) { EvHelpers.findTournamentMeta(tournamentName) }
    val isDark = meta?.isDark == true

    Box(
        modifier = modifier
            .width(136.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (meta != null) {
                    if (isDark) Color.Black else Color.White
                } else {
                    Color(0xFF1A1B24)
                }
            )
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        if (meta != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AsyncImage(
                    model = meta.logo,
                    contentDescription = meta.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .weight(1f)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = tournamentName,
                    color = if (isDark) Color.White else Color(0xFF111111),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0x1FFFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tournamentName.take(2).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tournamentName,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (hasLive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color.White)
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "LIVE",
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 8.sp
                )
            }
        }
    }
}
