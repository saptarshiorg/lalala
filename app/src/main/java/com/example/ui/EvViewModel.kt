package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ActiveStreamSession
import com.example.data.CatalogItem
import com.example.data.EvHelpers
import com.example.data.EvRepository
import com.example.data.HeroSlide
import com.example.data.LiveChannel
import com.example.data.MatchFixture
import com.example.data.MatchStatus
import com.example.data.SocialLinkItem
import com.example.data.StreamServer
import com.example.data.VipIptvChannel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class HomeMode(val label: String) {
    ALL("For You"),
    TV("TV"),
    MOVIES("Movies"),
    SPORTS("Sports"),
    LIVE("Live TV")
}

enum class TvSubMode {
    LIVE, SHOWS
}

enum class OverlayPage {
    NONE,
    SPORTS_FULL,
    LIVE_TV_FULL,
    MOVIES_FULL,
    TV_SHOWS_FULL,
    SEARCH,
    FAVOURITES,
    COMMUNITY,
    FANCODE_OTT,
    WILLOW_OTT,
    VIP_PLAYER,
    ADMIN_CHAT,
    MULTIVIEW
}

data class EvUiState(
    val isInitialLoading: Boolean = true,
    val homeMode: HomeMode = HomeMode.ALL,
    val tvSubMode: TvSubMode = TvSubMode.SHOWS,
    val activeOverlay: OverlayPage = OverlayPage.NONE,
    val settings: Map<String, String> = emptyMap(),
    val settingsNotes: Map<String, String> = emptyMap(),
    val visitorCountry: String = "IN",
    val heroSlides: List<HeroSlide> = emptyList(),
    val matches: List<MatchFixture> = emptyList(),
    val fancodeMatches: List<MatchFixture> = emptyList(),
    val sonyLivMatches: List<MatchFixture> = emptyList(),
    val willowMatches: List<MatchFixture> = emptyList(),
    val channels: List<LiveChannel> = emptyList(),
    val fifaChannels: List<LiveChannel> = emptyList(),
    val socialLinks: List<SocialLinkItem> = emptyList(),
    val moviesPool: List<CatalogItem> = emptyList(),
    val tvShowsPool: List<CatalogItem> = emptyList(),
    val moviesTotalPages: Int = 1,
    val tvShowsTotalPages: Int = 1,
    val isMoviesLoadingMore: Boolean = false,
    val isTvShowsLoadingMore: Boolean = false,
    val selectedMatchForServerModal: MatchFixture? = null,
    val activeStreamSession: ActiveStreamSession? = null,
    val pendingTvItemForEpisodeModal: CatalogItem? = null,
    val pendingAge18Confirm: Pair<String, () -> Unit>? = null,
    val isAge18Verified: Boolean = false,
    val showDmcaModal: Boolean = false,
    val comingSoonLabel: String? = null,
    val inAppPopupUrl: String? = null,
    val favouriteChannels: Map<String, LiveChannel> = emptyMap(),
    val vipUnlocked: Boolean = false,
    val vipChannels: List<VipIptvChannel> = emptyList(),
    val isVipLoading: Boolean = false,
    val vipError: String? = null,
    val voiceLanguageCode: String = "en-IN",
    val preselectedSportFilter: String = "",
    val preselectedTournamentFilter: String = ""
)

class EvViewModel(application: Application) : AndroidViewModel(application) {

    val repository = EvRepository()
    private val prefs = application.getSharedPreferences("ev_sports_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(EvUiState())
    val uiState: StateFlow<EvUiState> = _uiState.asStateFlow()

    private var moviesNextPage = 1
    private var tvShowsNextPage = 1
    private var refreshJob: Job? = null

    init {
        loadSavedPreferences()
        startInitialLoad()
        startAutoRefreshLoops()
    }

    private fun loadSavedPreferences() {
        val vipUnlocked = prefs.getBoolean("vipUnlocked", false)
        val voiceLang = prefs.getString("evVoiceLang", "en-IN") ?: "en-IN"
        val favJson = prefs.getString("sx_favourites_v1", null)
        val favMap = LinkedHashMap<String, LiveChannel>()
        if (!favJson.isNullOrBlank()) {
            try {
                val root = JSONObject(favJson)
                val chObj = root.optJSONObject("channels")
                chObj?.keys()?.forEach { key ->
                    val o = chObj.optJSONObject(key) ?: return@forEach
                    val ch = LiveChannel(
                        name = o.optString("name"),
                        group = o.optString("group", "Other"),
                        iconUrl = o.optString("iconUrl"),
                        watchUrl = o.optString("watchUrl"),
                        isLive = o.optBoolean("isLive", true),
                        quality = o.optString("quality", "HD"),
                        country = o.optString("country")
                    )
                    if (ch.name.isNotBlank()) favMap[key] = ch
                }
            } catch (_: Exception) {}
        }
        _uiState.update {
            it.copy(
                vipUnlocked = vipUnlocked,
                voiceLanguageCode = voiceLang,
                favouriteChannels = favMap
            )
        }
    }

    private fun saveFavourites(favs: Map<String, LiveChannel>) {
        try {
            val chObj = JSONObject()
            favs.forEach { (k, ch) ->
                val o = JSONObject().apply {
                    put("name", ch.name)
                    put("group", ch.group)
                    put("iconUrl", ch.iconUrl)
                    put("watchUrl", ch.watchUrl)
                    put("isLive", ch.isLive)
                    put("quality", ch.quality)
                    put("country", ch.country)
                }
                chObj.put(k, o)
            }
            val root = JSONObject().apply { put("channels", chObj) }
            prefs.edit().putString("sx_favourites_v1", root.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun startInitialLoad() {
        // Brand flash timer (700ms like BRAND_FLASH_MS in index.html) + background parallel fetch
        viewModelScope.launch {
            delay(850)
            _uiState.update { it.copy(isInitialLoading = false) }
        }
        viewModelScope.launch {
            val bundle = repository.loadAllCmsAndFeeds()
            _uiState.update {
                it.copy(
                    settings = bundle.settings,
                    settingsNotes = bundle.settingsNotes,
                    heroSlides = bundle.heroSlides,
                    matches = bundle.matches,
                    fancodeMatches = bundle.fancodeMatches,
                    sonyLivMatches = bundle.sonyLivMatches,
                    willowMatches = bundle.willowMatches,
                    channels = bundle.channels,
                    fifaChannels = bundle.fifaChannels,
                    socialLinks = bundle.socialLinks,
                    isInitialLoading = false
                )
            }
        }
        viewModelScope.launch {
            val country = repository.fetchVisitorCountry()
            _uiState.update { it.copy(visitorCountry = country) }
        }
        // Preload first 4 pages of Movies & TV Shows immediately
        viewModelScope.launch {
            repeat(4) { loadMoreMovies() }
            repeat(4) { loadMoreTvShows() }
        }
    }

    private fun startAutoRefreshLoops() {
        refreshJob = viewModelScope.launch {
            while (isActive) {
                delay(45_000L)
                try {
                    val bundle = repository.loadAllCmsAndFeeds()
                    _uiState.update {
                        it.copy(
                            settings = bundle.settings,
                            settingsNotes = bundle.settingsNotes,
                            heroSlides = bundle.heroSlides,
                            matches = bundle.matches,
                            fancodeMatches = bundle.fancodeMatches,
                            sonyLivMatches = bundle.sonyLivMatches,
                            willowMatches = bundle.willowMatches,
                            channels = bundle.channels
                        )
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun loadMoreMovies() {
        val st = _uiState.value
        if (st.isMoviesLoadingMore || (moviesNextPage > st.moviesTotalPages && st.moviesPool.isNotEmpty())) return
        _uiState.update { it.copy(isMoviesLoadingMore = true) }
        viewModelScope.launch {
            val (total, items) = repository.fetchCatalogPage("movie", moviesNextPage)
            if (items.isNotEmpty()) moviesNextPage++
            _uiState.update { curr ->
                val seen = curr.moviesPool.map { it.id }.toHashSet()
                val merged = curr.moviesPool + items.filter { seen.add(it.id) }
                curr.copy(
                    moviesPool = merged,
                    moviesTotalPages = total.coerceAtLeast(curr.moviesTotalPages),
                    isMoviesLoadingMore = false
                )
            }
        }
    }

    fun loadMoreTvShows() {
        val st = _uiState.value
        if (st.isTvShowsLoadingMore || (tvShowsNextPage > st.tvShowsTotalPages && st.tvShowsPool.isNotEmpty())) return
        _uiState.update { it.copy(isTvShowsLoadingMore = true) }
        viewModelScope.launch {
            val (total, items) = repository.fetchCatalogPage("tv", tvShowsNextPage)
            if (items.isNotEmpty()) tvShowsNextPage++
            _uiState.update { curr ->
                val seen = curr.tvShowsPool.map { it.id }.toHashSet()
                val merged = curr.tvShowsPool + items.filter { seen.add(it.id) }
                curr.copy(
                    tvShowsPool = merged,
                    tvShowsTotalPages = total.coerceAtLeast(curr.tvShowsTotalPages),
                    isTvShowsLoadingMore = false
                )
            }
        }
    }

    fun setHomeMode(mode: HomeMode) {
        _uiState.update {
            it.copy(
                homeMode = mode,
                activeOverlay = if (mode == HomeMode.LIVE) OverlayPage.LIVE_TV_FULL else OverlayPage.NONE
            )
        }
    }

    fun setTvSubMode(sub: TvSubMode) {
        _uiState.update { it.copy(tvSubMode = sub) }
    }

    fun openOverlay(page: OverlayPage, sportFilter: String = "", tournamentFilter: String = "") {
        _uiState.update {
            it.copy(
                activeOverlay = page,
                preselectedSportFilter = sportFilter,
                preselectedTournamentFilter = tournamentFilter
            )
        }
        if (page == OverlayPage.VIP_PLAYER && _uiState.value.vipUnlocked && _uiState.value.vipChannels.isEmpty()) {
            loadVipPlaylist()
        }
    }

    fun goHome() {
        _uiState.update {
            it.copy(
                activeOverlay = OverlayPage.NONE,
                homeMode = HomeMode.ALL,
                selectedMatchForServerModal = null,
                activeStreamSession = null,
                pendingTvItemForEpisodeModal = null,
                showDmcaModal = false,
                comingSoonLabel = null,
                inAppPopupUrl = null
            )
        }
    }

    fun openMatchServerSelector(match: MatchFixture) {
        _uiState.update { it.copy(selectedMatchForServerModal = match) }
    }

    fun closeMatchServerSelector() {
        _uiState.update { it.copy(selectedMatchForServerModal = null) }
    }

    fun playMatchServer(match: MatchFixture, serverIndex: Int) {
        val srv = match.servers.getOrNull(serverIndex) ?: return
        val resolvedUrl = resolveGeoFanCodeUrl(srv.url)
        _uiState.update {
            it.copy(
                selectedMatchForServerModal = null,
                activeStreamSession = ActiveStreamSession(
                    url = resolvedUrl,
                    title = srv.playName.ifBlank { match.title },
                    type = "match",
                    servers = match.servers,
                    selectedServerIndex = serverIndex,
                    fcxMatchId = match.fcxMatchId,
                    fcxWorldUrl = srv.url,
                    fcxIndiaM3u8 = match.fcxIndiaM3u8,
                    fcxSourceMode = "world"
                )
            )
        }
    }

    fun playChannel(channel: LiveChannel) {
        if (channel.watchUrl.isBlank() || channel.watchUrl == "#") return
        val resolvedUrl = resolveGeoFanCodeUrl(channel.watchUrl)
        _uiState.update {
            it.copy(
                activeStreamSession = ActiveStreamSession(
                    url = resolvedUrl,
                    title = channel.name,
                    type = "channel"
                )
            )
        }
    }

    fun playCatalogItem(item: CatalogItem) {
        val action = {
            if (item.type == "tv") {
                _uiState.update { it.copy(pendingTvItemForEpisodeModal = item) }
            } else {
                val url = item.embedUrl.ifBlank { "${EvRepository.VAPLAYER_BASE}/embed/movie/${item.id}" }
                val label = "${item.title}${if (item.year.isNotBlank()) " (${item.year})" else ""}"
                _uiState.update {
                    it.copy(
                        activeStreamSession = ActiveStreamSession(
                            url = url,
                            title = label,
                            type = "movie"
                        )
                    )
                }
            }
        }
        if (item.isAdult18 && !_uiState.value.isAge18Verified) {
            _uiState.update { it.copy(pendingAge18Confirm = item.title to action) }
        } else {
            action()
        }
    }

    fun playDirectId(id: String, type: String) {
        val cleanId = id.trim()
        if (cleanId.isEmpty()) return
        if (type == "tv") {
            _uiState.update {
                it.copy(
                    pendingTvItemForEpisodeModal = CatalogItem(
                        id = cleanId,
                        title = cleanId,
                        type = "tv"
                    )
                )
            }
        } else {
            val url = "${EvRepository.VAPLAYER_BASE}/embed/movie/$cleanId"
            _uiState.update {
                it.copy(
                    activeStreamSession = ActiveStreamSession(
                        url = url,
                        title = cleanId,
                        type = "movie"
                    )
                )
            }
        }
    }

    fun playTvEpisode(item: CatalogItem, season: Int, episode: Int) {
        val s = season.coerceAtLeast(1)
        val e = episode.coerceAtLeast(1)
        val id = item.tmdbId.ifBlank { item.imdbId }.ifBlank { item.id }
        val url = "${EvRepository.VAPLAYER_BASE}/embed/tv/$id/$s/$e"
        val label = "${item.title} S${s}E${e}"
        _uiState.update {
            it.copy(
                pendingTvItemForEpisodeModal = null,
                activeStreamSession = ActiveStreamSession(
                    url = url,
                    title = label,
                    type = "tv"
                )
            )
        }
    }

    fun closeTvEpisodeModal() {
        _uiState.update { it.copy(pendingTvItemForEpisodeModal = null) }
    }

    fun confirmAge18() {
        val pending = _uiState.value.pendingAge18Confirm
        _uiState.update {
            it.copy(
                isAge18Verified = true,
                pendingAge18Confirm = null
            )
        }
        pending?.second?.invoke()
    }

    fun dismissAge18() {
        _uiState.update { it.copy(pendingAge18Confirm = null) }
    }

    fun switchActiveStreamServer(index: Int) {
        val sess = _uiState.value.activeStreamSession ?: return
        val srv = sess.servers.getOrNull(index) ?: return
        _uiState.update {
            it.copy(
                activeStreamSession = sess.copy(
                    url = resolveGeoFanCodeUrl(srv.url),
                    selectedServerIndex = index,
                    fcxWorldUrl = srv.url,
                    fcxSourceMode = "world"
                )
            )
        }
    }

    fun switchFcxSourceMode(mode: String) {
        val sess = _uiState.value.activeStreamSession ?: return
        _uiState.update {
            it.copy(activeStreamSession = sess.copy(fcxSourceMode = mode))
        }
    }

    fun setStreamSession(session: ActiveStreamSession?) {
        _uiState.update { it.copy(activeStreamSession = session) }
    }

    fun toggleChannelFavourite(channel: LiveChannel) {
        val key = channel.favKey
        val updated = LinkedHashMap(_uiState.value.favouriteChannels)
        if (updated.containsKey(key)) {
            updated.remove(key)
        } else {
            updated[key] = channel
        }
        saveFavourites(updated)
        _uiState.update { it.copy(favouriteChannels = updated) }
    }

    fun tryUnlockVip(code: String): Boolean {
        if (code.trim() == "592600") {
            prefs.edit().putBoolean("vipUnlocked", true).apply()
            _uiState.update { it.copy(vipUnlocked = true) }
            if (_uiState.value.vipChannels.isEmpty()) {
                loadVipPlaylist()
            }
            return true
        }
        return false
    }

    fun lockVip() {
        prefs.edit().remove("vipUnlocked").apply()
        _uiState.update { it.copy(vipUnlocked = false) }
    }

    fun loadVipPlaylist() {
        if (_uiState.value.isVipLoading) return
        _uiState.update { it.copy(isVipLoading = true, vipError = null) }
        viewModelScope.launch {
            val list = repository.fetchVipPlaylist()
            if (list.isNotEmpty()) {
                _uiState.update { it.copy(vipChannels = list, isVipLoading = false, vipError = null) }
            } else {
                _uiState.update {
                    it.copy(
                        isVipLoading = false,
                        vipError = "The channel list couldn't be reached right now. Please try again."
                    )
                }
            }
        }
    }

    fun setVoiceLanguage(code: String) {
        prefs.edit().putString("evVoiceLang", code).apply()
        _uiState.update { it.copy(voiceLanguageCode = code) }
    }

    fun setShowDmca(show: Boolean) {
        _uiState.update { it.copy(showDmcaModal = show) }
    }

    fun setComingSoonLabel(label: String?) {
        _uiState.update { it.copy(comingSoonLabel = label) }
    }

    fun setInAppPopupUrl(url: String?) {
        _uiState.update { it.copy(inAppPopupUrl = url) }
    }

    private fun resolveGeoFanCodeUrl(url: String): String {
        val country = _uiState.value.visitorCountry
        if (url.contains("famcodexwebs.pages.dev", ignoreCase = true) && country != "IN") {
            return url.replace("/IN", "/WW").replace(Regex("[?&]q=1080"), "")
        }
        return url
    }
}
