package com.example.data


enum class MatchStatus {
    LIVE, UPCOMING, FINISHED
}

data class StreamServer(
    val text: String,
    val url: String,
    val icon: String = "",
    val sub: String = "Tap to play this stream",
    val playName: String = "",
    val isPriority: Boolean = false,
    val isLiveTvAutoConnected: Boolean = false,
    val isFcxServer: Boolean = false,
    val fcxLive: Boolean = true,
    val fcxTime: String = "",
    val appOnlyDrm: Boolean = false
)

data class MatchFixture(
    val id: String,
    val title: String,
    val sport: String,
    val tournament: String,
    val details: String = "",
    val dateLabel: String = "",
    val startTimeMs: Long = Long.MAX_VALUE,
    val countdownIso: String = "",
    val status: MatchStatus = MatchStatus.UPCOMING,
    val bannerUrl: String = "",
    val team1Code: String = "",
    val team1FullName: String = "",
    val team1IconUrl: String = "",
    val team2Code: String = "",
    val team2FullName: String = "",
    val team2IconUrl: String = "",
    val servers: List<StreamServer> = emptyList(),
    val fcxMatchId: String = "",
    val fcxIndiaM3u8: String = "",
    val sourceTag: String = "CMS"
)

data class LiveChannel(
    val name: String,
    val group: String,
    val iconUrl: String,
    val watchUrl: String,
    val isLive: Boolean = true,
    val quality: String = "HD",
    val country: String = "",
    val flagUrl: String = "",
    val bgStyle: String = ""
) {
    val favKey: String get() = name.trim().lowercase()
}

data class HeroSlide(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val isLive: Boolean,
    val bgImageUrl: String,
    val btn1Text: String = "Watch Now",
    val btn1Url: String = "#",
    val btn2Text: String = "",
    val btn2Url: String = "",
    val location: String = "",
    val linkedMatch: MatchFixture? = null,
    val linkedMovie: CatalogItem? = null,
    val isLogowallFallback: Boolean = false
)

data class CatalogItem(
    val id: String,
    val tmdbId: String = "",
    val imdbId: String = "",
    val title: String,
    val year: String = "",
    val posterUrl: String = "",
    val rating: String = "",
    val genre: String = "",
    val popularity: Double = 0.0,
    val embedUrl: String = "",
    val type: String = "movie", // "movie" or "tv"
    val isAdult18: Boolean = false
)

data class VipIptvChannel(
    val name: String,
    val logo: String,
    val group: String,
    val url: String
)

data class TournamentTile(
    val name: String,
    val count: Int,
    val liveCount: Int,
    val logoUrl: String = "",
    val isDarkCard: Boolean = false,
    val chips: List<String> = emptyList(),
    val description: String = ""
)

data class SocialLinkItem(
    val label: String,
    val emoji: String,
    val url: String
)

enum class StreamSourceKind {
    DIRECT_LIBVLC,
    FANCODE_RESOLVER,
    EMBED_WEBVIEW
}

data class ActiveStreamSession(
    val url: String,
    val title: String,
    val type: String, // "stream", "channel", "movie", "tv", "match"
    val servers: List<StreamServer> = emptyList(),
    val selectedServerIndex: Int = 0,
    val fcxMatchId: String = "",
    val fcxWorldUrl: String = "",
    val fcxIndiaM3u8: String = "",
    val fcxSourceMode: String = "world" // "world" or "india"
)

data class MultiviewSlotState(
    val url: String,
    val name: String,
    val isDirectStream: Boolean,
    val isMuted: Boolean = true
)
