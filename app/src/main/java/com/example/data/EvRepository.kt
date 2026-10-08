package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Real networking & parsing engine replicating all data flows in the supplied HTML:
 * 1. Google Sheets CSV CMS (`SHEET_ID = 1UcPVLGxSTJmAdtfPHsA_f5VCtbABwVdwef-va512HQU`)
 * 2. FanCode World (`fancode.json`) & CNP-TV India (`fan.json`) + `play.html?id=...` HLS master playlist builder
 * 3. SonyLIV (`sonyliv.json`) language-grouped live streams
 * 4. Willow OTT (`willow.json`) with proxy fallback chain
 * 5. VidAPI (`vidapi.ru` / `vaplayer.ru`) Movies & TV Shows with race-proxy & TMDB poster optimization
 * 6. VIP IPTV (`iptv-org` M3U parser)
 * 7. Geo check (`ipwho.is`)
 */
class EvRepository {

    companion object {
        const val SHEET_ID = "1UcPVLGxSTJmAdtfPHsA_f5VCtbABwVdwef-va512HQU"
        const val FCX_API_CNPTV = "https://raw.githubusercontent.com/kajju027/Fancode-Events-Json/main/fan.json"
        const val FCX_API_WORLD = "https://raw.githubusercontent.com/kajju027/Fancode-Events-Json/main/fancode.json"
        const val SLX_API = "https://raw.githubusercontent.com/kajju027/SonyLiv-Events-Json/refs/heads/main/sonyliv.json"
        const val WLX_API = "https://sonujson-v5.pages.dev/Data/willow.json"
        const val VIDAPI_BASE = "https://vidapi.ru"
        const val VAPLAYER_BASE = "https://vaplayer.ru"
        const val VIP_M3U_URL = "https://iptv-org.github.io/iptv/index.country.m3u"
        const val CORS_PROXY_1 = "https://api.allorigins.win/raw?url="
        const val CORS_PROXY_2 = "https://corsproxy.io/?"

        const val SONY_LOGO = "https://upload.wikimedia.org/wikipedia/commons/f/f7/SonyLIV_2020.png"
        const val STAR_LOGO = "https://i.pinimg.com/originals/3d/65/03/3d650388af374546ea7341abb6d57f00.png"
        const val FANCODE_LOGO = "https://www.fancode.com/skillup-uploads/fc-web-logo/fc_logo_white_bg.svg"
        const val WILLOW_LOGO = "https://bobgoldpr.com/wp-content/uploads/2024/05/logo-transparent-cb-1.png"
        const val DEFAULT_HERO_BG = "https://raw.githubusercontent.com/saptarshiorg/saptarshiorg.github.io/main/ChatGPT%20Image%20Jun%207,%202026,%2010_27_17%20AM.png"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val sheetCache = ConcurrentHashMap<String, Pair<Long, List<Map<String, String>>>>()
    private val sheetCacheTtlMs = 60_000L

    @Volatile
    private var cachedFancodeJson: JSONObject? = null
    @Volatile
    private var cachedFancodeAt: Long = 0L

    private suspend fun httpGet(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.string()
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun httpGetWithProxyFallback(url: String): String? {
        httpGet(url)?.let { return it }
        val enc = URLEncoder.encode(url, "UTF-8")
        httpGet(CORS_PROXY_1 + enc)?.let { return it }
        return httpGet(CORS_PROXY_2 + enc)
    }

    // ── CSV Parser (exact replica of parseCSV + toObj) ──
    fun parseCsv(text: String): List<Map<String, String>> {
        val rows = mutableListOf<List<String>>()
        val cur = StringBuilder()
        var inQ = false
        var row = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            val nx = if (i + 1 < text.length) text[i + 1] else '\u0000'
            if (inQ) {
                if (ch == '"' && nx == '"') {
                    cur.append('"')
                    i++
                } else if (ch == '"') {
                    inQ = false
                } else {
                    cur.append(ch)
                }
            } else {
                when {
                    ch == '"' -> inQ = true
                    ch == ',' -> {
                        row.add(cur.toString().trim())
                        cur.clear()
                    }
                    ch == '\r' && nx == '\n' -> {
                        row.add(cur.toString().trim())
                        if (row.any { it.isNotEmpty() }) rows.add(row)
                        row = mutableListOf()
                        cur.clear()
                        i++
                    }
                    ch == '\n' || ch == '\r' -> {
                        row.add(cur.toString().trim())
                        if (row.any { it.isNotEmpty() }) rows.add(row)
                        row = mutableListOf()
                        cur.clear()
                    }
                    else -> cur.append(ch)
                }
            }
            i++
        }
        if (cur.isNotEmpty() || row.isNotEmpty()) {
            row.add(cur.toString().trim())
            if (row.any { it.isNotEmpty() }) rows.add(row)
        }
        if (rows.size < 2) return emptyList()
        val headers = rows[0].map { h ->
            h.lowercase()
                .replace(Regex("[^a-z0-9_]"), "_")
                .replace(Regex("__+"), "_")
                .trim('_')
        }
        return rows.drop(1).map { r ->
            val map = LinkedHashMap<String, String>()
            headers.forEachIndexed { idx, key ->
                map[key] = r.getOrNull(idx)?.trim().orEmpty()
            }
            map
        }
    }

    private fun isRowActive(row: Map<String, String>): Boolean {
        val keys = listOf("active_true_false", "active", "is_active", "isactive", "enabled", "show", "visible")
        val raw = keys.firstNotNullOfOrNull { k -> row[k]?.takeIf { it.isNotBlank() } }.orEmpty()
        val clean = raw.trim().trim('\'', '"').uppercase()
        return clean != "FALSE" && clean != "NO" && clean != "N" && clean != "0" && clean != "OFF"
    }

    suspend fun fetchSheet(sheetName: String, forceRefresh: Boolean = false): List<Map<String, String>> {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            sheetCache[sheetName]?.let { (ts, data) ->
                if (now - ts < sheetCacheTtlMs) return data
            }
        }
        val encoded = URLEncoder.encode(sheetName, "UTF-8")
        val url = "https://docs.google.com/spreadsheets/d/$SHEET_ID/gviz/tq?tqx=out:csv&sheet=$encoded"
        val csv = httpGet(url) ?: return sheetCache[sheetName]?.second ?: emptyList()
        val parsed = parseCsv(csv)
        if (parsed.isNotEmpty()) {
            sheetCache[sheetName] = now to parsed
        }
        return parsed
    }

    // ── Geo Check ──
    suspend fun fetchVisitorCountry(): String {
        val body = httpGet("https://ipwho.is/") ?: return "IN"
        return try {
            val json = JSONObject(body)
            if (json.optBoolean("success", false)) {
                json.optString("country_code", "IN").ifBlank { "IN" }
            } else "IN"
        } catch (e: Exception) {
            "IN"
        }
    }

    // ── FanCode Feeds ──
    private fun parseFcxTime(str: String?): Long {
        if (str.isNullOrBlank()) return Long.MAX_VALUE
        val m = Regex("(\\d{1,2}):(\\d{2}):(\\d{2})\\s+(AM|PM)\\s+(\\d{1,2})-(\\d{1,2})-(\\d{4})", RegexOption.IGNORE_CASE)
            .find(str)
        if (m != null) {
            var h = m.groupValues[1].toIntOrNull() ?: 0
            val mm = m.groupValues[2].toIntOrNull() ?: 0
            val ss = m.groupValues[3].toIntOrNull() ?: 0
            val ampm = m.groupValues[4].uppercase()
            val d = m.groupValues[5].toIntOrNull() ?: 1
            val mo = (m.groupValues[6].toIntOrNull() ?: 1) - 1
            val y = m.groupValues[7].toIntOrNull() ?: 2026
            if (ampm == "PM" && h < 12) h += 12
            if (ampm == "AM" && h == 12) h = 0
            val cal = Calendar.getInstance()
            cal.set(y, mo, d, h, mm, ss)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(str)?.time ?: Long.MAX_VALUE
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
    }

    fun formatMatchTimeLabel(ms: Long): String {
        if (ms == Long.MAX_VALUE || ms <= 0L) return "TBA"
        val d = Date(ms)
        val dayFmt = SimpleDateFormat("dd MMM", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        val timeFmt = SimpleDateFormat("h:mm a", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        return "${dayFmt.format(d).uppercase()}, ${timeFmt.format(d).uppercase()}"
    }

    suspend fun fetchFanCodeMatches(): List<MatchFixture> = coroutineScope {
        val cnpDeferred = async { httpGet(FCX_API_CNPTV) }
        val worldDeferred = async { httpGet(FCX_API_WORLD) }
        val cnpStr = cnpDeferred.await()
        val worldStr = worldDeferred.await()

        val indiaMap = HashMap<String, String>()
        if (!cnpStr.isNullOrBlank()) {
            try {
                val arr = JSONObject(cnpStr).optJSONArray("matches") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("match_id")
                    val cdn = o.optString("cnptv_cdn")
                    if (id.isNotBlank() && cdn.isNotBlank()) {
                        indiaMap[id] = cdn
                    }
                }
            } catch (_: Exception) {}
        }

        val result = mutableListOf<MatchFixture>()
        if (!worldStr.isNullOrBlank()) {
            try {
                val root = JSONObject(worldStr)
                cachedFancodeJson = root
                cachedFancodeAt = System.currentTimeMillis()
                val arr = root.optJSONArray("matches") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    val id = m.optString("match_id")
                    if (id.isBlank()) continue
                    val title = EvHelpers.evTeamName(m.optString("title", "Match"))
                    val tournament = m.optString("tournament", "")
                    val cat = m.optString("category", "").trim()
                    val image = m.optString("image", "")
                    val statusRaw = m.optString("status", "").uppercase()
                    val status = when (statusRaw) {
                        "LIVE" -> MatchStatus.LIVE
                        "COMPLETED", "ENDED", "FINISHED" -> MatchStatus.FINISHED
                        else -> MatchStatus.UPCOMING
                    }
                    val startMs = parseFcxTime(m.optString("startTime"))
                    val timeLabel = formatMatchTimeLabel(startMs)
                    val sport = EvHelpers.detectSport(cat, title, tournament)
                    val playUrl = "play.html?id=${id}_eng&s=0"
                    val indiaUrl = indiaMap[id].orEmpty()

                    val servers = mutableListOf(
                        StreamServer(
                            text = "FanCode",
                            url = playUrl,
                            icon = FANCODE_LOGO,
                            sub = if (status == MatchStatus.LIVE) "Tap to play this stream" else "Starts $timeLabel",
                            playName = title,
                            isPriority = true,
                            isFcxServer = true,
                            fcxLive = status == MatchStatus.LIVE,
                            fcxTime = timeLabel
                        )
                    )
                    if (indiaUrl.isNotBlank()) {
                        servers.add(
                            StreamServer(
                                text = "FanCode India (CNP-TV)",
                                url = indiaUrl,
                                icon = FANCODE_LOGO,
                                sub = "Direct HLS Stream · India",
                                playName = "$title · India",
                                isFcxServer = false,
                                fcxLive = status == MatchStatus.LIVE,
                                fcxTime = timeLabel
                            )
                        )
                    }

                    result.add(
                        MatchFixture(
                            id = "fcx_$id",
                            title = title,
                            sport = sport,
                            tournament = tournament.ifBlank { "$sport Matches" },
                            details = tournament,
                            dateLabel = timeLabel,
                            startTimeMs = startMs,
                            status = status,
                            bannerUrl = image,
                            servers = servers,
                            fcxMatchId = id,
                            fcxIndiaM3u8 = indiaUrl,
                            sourceTag = "FanCode"
                        )
                    )
                }
            } catch (_: Exception) {}
        }
        result.sortedWith(compareBy<MatchFixture> { it.status.ordinal }.thenBy { it.startTimeMs })
    }

    /**
     * Resolves a `play.html?id=<matchId>_eng&s=0` FanCode URL directly into an playable `.m3u8` URL
     * (replicates `fcResolve` + `fcMasterUrl` from `#fcplay-glue`).
     */
    suspend fun resolveFanCodePlayUrl(playUrl: String): Pair<String, List<Pair<String, String>>>? {
        val pair = EvHelpers.extractFanCodeId(playUrl) ?: return null
        val idFull = pair.first
        val sIdx = pair.second
        val underIdx = idFull.indexOf('_')
        val matchId = if (underIdx > -1) idFull.substring(0, underIdx) else idFull
        val langCode = if (underIdx > -1) idFull.substring(underIdx + 1).lowercase() else "eng"
        val langMap = mapOf(
            "eng" to "ENGLISH", "hin" to "HINDI", "tam" to "TAMIL",
            "tel" to "TELUGU", "ben" to "BENGALI", "mar" to "MARATHI"
        )

        val now = System.currentTimeMillis()
        val root = if (cachedFancodeJson != null && now - cachedFancodeAt < 20_000L) {
            cachedFancodeJson!!
        } else {
            val str = httpGet(FCX_API_WORLD) ?: return null
            val j = JSONObject(str)
            cachedFancodeJson = j
            cachedFancodeAt = now
            j
        }

        val arr = root.optJSONArray("matches") ?: return null
        var matchObj: JSONObject? = null
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("match_id") == matchId) {
                matchObj = o
                break
            }
        }
        val m = matchObj ?: return null
        val qualitiesOrder = listOf("1080p", "720p", "540p", "480p", "360p", "240p")

        if (sIdx == 0) {
            val autoStreams = m.optJSONObject("auto_streams")
            if (autoStreams != null) {
                val keys = autoStreams.keys().asSequence().toList()
                if (keys.isNotEmpty()) {
                    val targetLang = langMap[langCode] ?: m.optString("language").ifBlank { keys[0] }
                    val langObj = autoStreams.optJSONObject(targetLang) ?: autoStreams.optJSONObject(keys[0])
                    val streamsObj = langObj?.optJSONObject("streams")
                    if (streamsObj != null) {
                        val qualityList = mutableListOf<Pair<String, String>>()
                        for (q in qualitiesOrder) {
                            val u = streamsObj.optString(q, "").trim()
                            if (u.isNotBlank()) {
                                qualityList.add(q to u)
                            }
                        }
                        if (qualityList.isNotEmpty()) {
                            return qualityList.first().second to qualityList
                        }
                    }
                }
            }
        }
        val st = m.optJSONObject("streams")
        if (st != null) {
            val candidates = listOf(
                st.optString("fancode_cdn"),
                st.optString("dai_cdn"),
                st.optString("fancode_lk_cdn"),
                st.optString("fancode_np_cdn"),
                st.optString("fancode_bd_cdn")
            ).filter { it.isNotBlank() }
            if (candidates.isNotEmpty()) {
                val chosen = candidates.getOrElse(sIdx.coerceAtLeast(0)) { candidates.first() }
                return chosen to listOf("Auto" to chosen)
            }
        }
        return null
    }

    // ── SonyLIV Live Matches (replicates `#slx-sonyliv-bridge`) ──
    suspend fun fetchSonyLivMatches(): List<MatchFixture> {
        val str = httpGet("$SLX_API?t=${System.currentTimeMillis()}") ?: return emptyList()
        val langMap = mapOf(
            "ENG" to "English", "EN" to "English", "HIN" to "Hindi", "HI" to "Hindi",
            "TAM" to "Tamil", "TA" to "Tamil", "TEL" to "Telugu", "TE" to "Telugu",
            "KAN" to "Kannada", "MAL" to "Malayalam", "BEN" to "Bengali", "MAR" to "Marathi"
        )
        return try {
            val arr = JSONObject(str).optJSONArray("matches") ?: return emptyList()
            val grouped = LinkedHashMap<String, MutableList<Triple<JSONObject, String, String>>>()

            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val status = e.optString("status", "").trim().lowercase()
                if (status != "live") continue
                val url = listOf(
                    e.optString("video_url"),
                    e.optString("dai_url"),
                    e.optString("pub_url")
                ).firstOrNull { EvHelpers.sxIsDirectStreamUrl(it) } ?: continue

                val rawName = e.optString("match_name").ifBlank { e.optString("event_name") }
                val langMatch = Regex("\\[([^\\]]+)\\]").find(rawName)
                val lang = if (langMatch != null) {
                    val code = langMatch.groupValues[1].trim().uppercase()
                    langMap[code] ?: code.lowercase().replaceFirstChar { it.uppercase() }
                } else {
                    val l = e.optString("language", "").trim().uppercase()
                    langMap[l] ?: "English"
                }

                val cleanName = EvHelpers.evTeamName(
                    rawName.replace(Regex("\\[[^\\]]*\\]"), " ")
                        .replace(Regex("^\\s*(live|upcoming|completed)\\s*[-:–—]\\s*", RegexOption.IGNORE_CASE), "")
                        .trim()
                )
                val key = cleanName.lowercase().replace(Regex("[^a-z0-9]"), "")
                grouped.getOrPut(key) { mutableListOf() }.add(Triple(e, url, lang))
            }

            grouped.entries.mapIndexed { idx, (_, items) ->
                val first = items.first().first
                val rawTitle = first.optString("match_name").ifBlank { first.optString("event_name") }
                    .replace(Regex("\\[[^\\]]*\\]"), " ").trim()
                val title = EvHelpers.evTeamName(rawTitle)
                val cat = first.optString("category", "Sports")
                val sport = EvHelpers.detectSport(cat, title, "")
                val poster = items.firstNotNullOfOrNull { it.first.optString("poster").takeIf { p -> p.isNotBlank() } }.orEmpty()
                val tour = first.optString("event_name").ifBlank { "SonyLIV · $sport" }

                val servers = items.mapIndexed { sIdx, (_, u, lang) ->
                    StreamServer(
                        text = if (items.size > 1) "$lang" else "SonyLIV ($lang)",
                        url = u,
                        icon = SONY_LOGO,
                        sub = "SonyLIV · tap to play",
                        playName = "$title · $lang"
                    )
                }

                MatchFixture(
                    id = "slx_$idx",
                    title = title,
                    sport = sport,
                    tournament = tour,
                    details = "SonyLIV · $sport",
                    dateLabel = "${servers.size} ${if (servers.size == 1) "language" else "languages"}",
                    startTimeMs = 0L,
                    status = MatchStatus.LIVE,
                    bannerUrl = poster,
                    servers = servers,
                    sourceTag = "SonyLIV"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── Willow OTT Matches (replicates `#wlx-willow-bridge`) ──
    suspend fun fetchWillowMatches(): List<MatchFixture> {
        val str = httpGetWithProxyFallback("$WLX_API?t=${System.currentTimeMillis()}") ?: return emptyList()
        return try {
            val root = JSONObject(str)
            val arr = root.optJSONArray("Matches") ?: root.optJSONArray("matches") ?: return emptyList()
            val out = mutableListOf<MatchFixture>()
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val stRaw = e.optString("status", "").trim().lowercase()
                val status = when {
                    stRaw == "live" -> MatchStatus.LIVE
                    Regex("^(ended|completed|finished|over)$").matches(stRaw) -> MatchStatus.FINISHED
                    else -> MatchStatus.UPCOMING
                }
                if (status == MatchStatus.FINISHED) continue

                val evName = e.optString("event_name", "Match")
                val segs = evName.split(Regex("\\s+-\\s*|\\s*-\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
                val vsIdx = segs.indexOfLast { Regex("\\s(?:vs\\.?|v)\\s", RegexOption.IGNORE_CASE).containsMatchIn(it) }
                val rawTitle = if (vsIdx >= 0) segs[vsIdx] else evName
                val title = EvHelpers.evTeamName(rawTitle)
                val tour = if (vsIdx >= 0) segs.filterIndexed { index, _ -> index != vsIdx }.joinToString(" - ") else "Willow Cricket"

                val urls = mutableListOf<String>()
                fun scanJson(any: Any?) {
                    when (any) {
                        is String -> if (EvHelpers.sxIsDirectStreamUrl(any) && !urls.contains(any)) urls.add(any)
                        is JSONArray -> for (k in 0 until any.length()) scanJson(any.opt(k))
                        is JSONObject -> any.keys().forEach { k -> scanJson(any.opt(k)) }
                    }
                }
                scanJson(e)

                val servers = urls.mapIndexed { uIdx, u ->
                    StreamServer(
                        text = if (urls.size > 1) "Willow ${uIdx + 1}" else "Willow",
                        url = u,
                        icon = WILLOW_LOGO,
                        sub = "Willow · tap to play",
                        playName = title
                    )
                }
                val whenTxt = listOf(e.optString("date"), e.optString("time")).filter { it.isNotBlank() }.joinToString(", ")

                out.add(
                    MatchFixture(
                        id = "wlx_$i",
                        title = title,
                        sport = "Cricket",
                        tournament = tour.ifBlank { "Willow Cricket" },
                        details = tour,
                        dateLabel = whenTxt.ifBlank { if (status == MatchStatus.LIVE) "LIVE" else "UPCOMING" },
                        startTimeMs = if (status == MatchStatus.LIVE) 0L else System.currentTimeMillis() + (i + 1) * 3600_000L,
                        status = status,
                        bannerUrl = e.optString("image", ""),
                        servers = servers,
                        sourceTag = "Willow"
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── Unified CMS + Multi-Source Match Merger (replicates `renderMatchCards` + `LMX` + `#sxs-script`) ──
    suspend fun loadAllCmsAndFeeds(): EvCmsBundle = coroutineScope {
        val settingsDef = async { fetchSheet("SETTINGS") }
        val slidesDef = async { fetchSheet("HERO SLIDES") }
        val matchesDef = async { fetchSheet("MATCH CARDS") }
        val channelsDef = async { fetchSheet("LIVE CHANNELS") }
        val socialDef = async { fetchSheet("SOCIAL LINKS") }
        val fifaDef = async { fetchSheet("FIFA CHANNELS") }
        val fcxDef = async { fetchFanCodeMatches() }
        val slxDef = async { fetchSonyLivMatches() }
        val wlxDef = async { fetchWillowMatches() }

        val settingsRows = settingsDef.await()
        val slideRows = slidesDef.await().filter { isRowActive(it) }
        val matchRows = matchesDef.await().filter { isRowActive(it) }
        val channelRows = channelsDef.await().filter { isRowActive(it) }
        val socialRows = socialDef.await().filter { isRowActive(it) }
        val fifaRows = fifaDef.await().filter { isRowActive(it) }
        val fcxList = fcxDef.await()
        val slxList = slxDef.await()
        val wlxList = wlxDef.await()

        val settings = LinkedHashMap<String, String>()
        val settingsNotes = LinkedHashMap<String, String>()
        settingsRows.forEach { r ->
            val k = r["setting_key"].orEmpty()
            if (k.isNotBlank()) {
                settings[k] = r["value"].orEmpty()
                val note = r["notes"].orEmpty()
                if (note.isNotBlank()) settingsNotes[k] = note
            }
        }

        // Parse Live Channels
        val liveChannels = channelRows.mapNotNull { c ->
            val name = c["channel_name"]?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            val group = c["group"]?.trim()?.ifBlank { "Other" } ?: "Other"
            val icon = c["icon_url"]?.trim().orEmpty()
            val url = c["watch_url"]?.trim().orEmpty()
            val isLive = (c["status_live_off"] ?: "LIVE").trim().uppercase() == "LIVE"
            val quality = (c["quality"] ?: "HD").trim().uppercase()
            val country = c["country"]?.trim().orEmpty()
            LiveChannel(
                name = name,
                group = group,
                iconUrl = icon,
                watchUrl = url,
                isLive = isLive,
                quality = quality,
                country = country,
                flagUrl = EvHelpers.sxFlagUrl(country),
                bgStyle = c["bg_style"].orEmpty()
            )
        }

        // Parse Sheet Match Cards & auto-connect servers + cross-link FanCode/SonyLIV/Willow
        val now = System.currentTimeMillis()
        val hideAfterMs = 12 * 3600_000L
        val cmsMatches = mutableListOf<MatchFixture>()

        matchRows.forEachIndexed { idx, c ->
            val iso = (c["countdown_to_iso_datetime"] ?: c["countdown_to"]).orEmpty().trim()
            val startMs = if (iso.isNotBlank()) {
                try {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(iso.take(19))?.time ?: Long.MAX_VALUE
                } catch (_: Exception) {
                    Long.MAX_VALUE
                }
            } else Long.MAX_VALUE

            // Auto-hide 12h after start
            if (startMs != Long.MAX_VALUE && now >= startMs + hideAfterMs) return@forEachIndexed

            val rawStatus = (c["status_live_upcoming"] ?: c["status"]).orEmpty().trim()
            val isFinished = Regex("finished|ended|\\bover\\b|complete|full[\\s-]?time|\\bft\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(rawStatus)
            val isAutoLive = !isFinished && startMs != Long.MAX_VALUE && now >= startMs
            val status = when {
                rawStatus.equals("live", true) || isAutoLive -> MatchStatus.LIVE
                isFinished -> MatchStatus.FINISHED
                else -> MatchStatus.UPCOMING
            }

            val t1Code = c["team1_code"].orEmpty().trim()
            val t1Full = c["team1_full_name"].orEmpty().trim()
            var t1Icon = c["team1_icon_url"].orEmpty().trim()
            if (t1Icon.isEmpty()) t1Icon = EvHelpers.sxFlagUrl(t1Full.ifBlank { t1Code })

            val t2Code = c["team2_code"].orEmpty().trim()
            val t2Full = c["team2_full_name"].orEmpty().trim()
            var t2Icon = c["team2_icon_url"].orEmpty().trim()
            if (t2Icon.isEmpty()) t2Icon = EvHelpers.sxFlagUrl(t2Full.ifBlank { t2Code })

            val rawTitle = if ((t1Full.isNotEmpty() || t1Code.isNotEmpty()) && (t2Full.isNotEmpty() || t2Code.isNotEmpty())) {
                "${t1Full.ifBlank { t1Code }} vs ${t2Full.ifBlank { t2Code }}"
            } else {
                c["details"] ?: c["game"] ?: "Match"
            }
            val title = EvHelpers.evTeamName(rawTitle)
            val game = c["game"]?.trim()?.ifBlank { "Other Matches" } ?: "Other Matches"
            val sport = EvHelpers.detectSport(c["sport"] ?: c["sports"], game, "$title ${c["details"]}")

            // Collect 5 server slots + auto-connect from Live Channels if URL is blank
            val rawSlots = listOf(
                c["btn_url"] to (c["btn_text"]?.ifBlank { "Server 1" } ?: "Server 1"),
                c["btn_url_2"] to (c["btn_text_2"]?.ifBlank { "Server 2" } ?: "Server 2"),
                c["btn_url_3"] to (c["btn_text_3"]?.ifBlank { "Server 3" } ?: "Server 3"),
                c["btn_url_4"] to (c["btn_text_4"]?.ifBlank { "Server 4" } ?: "Server 4"),
                c["btn_url_5"] to (c["btn_text_5"]?.ifBlank { "Server 5" } ?: "Server 5")
            )
            val servers = mutableListOf<StreamServer>()
            rawSlots.forEach { (uRaw, labelRaw) ->
                var u = uRaw?.trim().orEmpty()
                val label = labelRaw.trim()
                var autoConn = false
                if ((u.isEmpty() || u == "#") && label.isNotEmpty() && !label.startsWith("Server ")) {
                    val matchedCh = liveChannels.firstOrNull { it.name.equals(label, ignoreCase = true) }
                    if (matchedCh != null && matchedCh.watchUrl.isNotBlank()) {
                        u = matchedCh.watchUrl
                        autoConn = true
                    }
                }
                if (u.isNotEmpty() && u != "#") {
                    val chIcon = liveChannels.firstOrNull { it.name.equals(label, ignoreCase = true) }?.iconUrl
                        ?: when {
                            Regex("sony|sliv", RegexOption.IGNORE_CASE).containsMatchIn(label) -> SONY_LOGO
                            Regex("star|hotstar", RegexOption.IGNORE_CASE).containsMatchIn(label) -> STAR_LOGO
                            Regex("fancode", RegexOption.IGNORE_CASE).containsMatchIn(label) -> FANCODE_LOGO
                            Regex("willow", RegexOption.IGNORE_CASE).containsMatchIn(label) -> WILLOW_LOGO
                            else -> ""
                        }
                    servers.add(
                        StreamServer(
                            text = label,
                            url = u,
                            icon = chIcon,
                            sub = if (autoConn) "LIVE TV · Auto-connected" else "Tap to play this stream",
                            playName = "$title · $label",
                            isLiveTvAutoConnected = autoConn
                        )
                    )
                }
            }

            val dateLabel = if (startMs != Long.MAX_VALUE) {
                formatMatchTimeLabel(startMs)
            } else {
                c["date_label"].orEmpty()
            }

            cmsMatches.add(
                MatchFixture(
                    id = "cms_$idx",
                    title = title,
                    sport = sport,
                    tournament = game,
                    details = c["details"].orEmpty(),
                    dateLabel = dateLabel,
                    startTimeMs = startMs,
                    countdownIso = iso,
                    status = status,
                    bannerUrl = (c["banner_url"] ?: c["banner_image_url"]).orEmpty().trim(),
                    team1Code = t1Code,
                    team1FullName = t1Full,
                    team1IconUrl = t1Icon,
                    team2Code = t2Code,
                    team2FullName = t2Full,
                    team2IconUrl = t2Icon,
                    servers = servers,
                    sourceTag = "CMS"
                )
            )
        }

        // Merge CMS + FanCode + SonyLIV + Willow fixtures (one card per fixture, folding servers)
        val mergedMatches = mergeAllMatchSources(cmsMatches, fcxList, slxList, wlxList)

        // Build Hero Slides (linking "TEAM1 v TEAM2" titles to mergedMatches)
        val heroSlides = slideRows.mapIndexed { idx, s ->
            val rawTitle = s["title"].orEmpty().trim()
            val linked = findHeroLinkedMatch(rawTitle, mergedMatches)
            val isLive = s["show_live_badge"]?.uppercase() == "TRUE" || linked?.status == MatchStatus.LIVE
            val bg = s["bg_image_url"]?.trim()?.ifBlank { linked?.bannerUrl.orEmpty() }
                .orEmpty().ifBlank { DEFAULT_HERO_BG }
            HeroSlide(
                id = "hero_$idx",
                title = linked?.title ?: EvHelpers.evTeamName(rawTitle),
                subtitle = s["subtitle"]?.ifBlank { linked?.tournament.orEmpty() }.orEmpty(),
                badge = if (isLive) "LIVE NOW" else (s["sport_badge"] ?: linked?.sport ?: "SPORTS"),
                isLive = isLive,
                bgImageUrl = bg,
                btn1Text = s["btn1_text"]?.ifBlank { "Watch Now" } ?: "Watch Now",
                btn1Url = s["btn1_url"]?.ifBlank { "#" } ?: "#",
                btn2Text = s["btn2_text"].orEmpty(),
                btn2Url = s["btn2_url"].orEmpty(),
                location = s["location"].orEmpty(),
                linkedMatch = linked
            )
        }.toMutableList()

        // Append top FanCode/Willow live/upcoming slides + trailing Live TV logowall slide
        mergedMatches.filter { it.status != MatchStatus.FINISHED && it.bannerUrl.isNotBlank() }
            .take(4)
            .forEach { m ->
                if (heroSlides.none { it.linkedMatch?.id == m.id || it.title.equals(m.title, true) }) {
                    heroSlides.add(
                        HeroSlide(
                            id = "hero_auto_${m.id}",
                            title = m.title,
                            subtitle = listOf(m.tournament, if (m.status == MatchStatus.LIVE) "Streaming live now" else m.dateLabel)
                                .filter { it.isNotBlank() }.joinToString(" • "),
                            badge = if (m.status == MatchStatus.LIVE) "LIVE" else m.sport,
                            isLive = m.status == MatchStatus.LIVE,
                            bgImageUrl = m.bannerUrl,
                            linkedMatch = m
                        )
                    )
                }
            }

        val socials = socialRows.map {
            SocialLinkItem(
                label = it["label"].orEmpty(),
                emoji = it["icon_emoji"].orEmpty(),
                url = it["url"].orEmpty()
            )
        }

        val fifaChannels = fifaRows.mapNotNull { r ->
            val name = r["channel_name"]?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            LiveChannel(
                name = name,
                group = r["sub_label"]?.ifBlank { "FIFA 2026" } ?: "FIFA 2026",
                iconUrl = r["icon_url"].orEmpty(),
                watchUrl = r["watch_url"].orEmpty(),
                isLive = r["coming_soon"]?.uppercase() != "TRUE",
                quality = r["quality_badge"]?.ifBlank { "HD" } ?: "HD",
                country = r["flag_emoji"].orEmpty()
            )
        }

        EvCmsBundle(
            settings = settings,
            settingsNotes = settingsNotes,
            heroSlides = heroSlides,
            matches = mergedMatches,
            fancodeMatches = fcxList,
            sonyLivMatches = slxList,
            willowMatches = wlxList,
            channels = liveChannels,
            fifaChannels = fifaChannels,
            socialLinks = socials
        )
    }

    private fun extractSides(m: MatchFixture): Pair<List<String>, List<String>>? {
        val s1 = listOf(m.team1FullName, m.team1Code).filter { it.isNotBlank() }.toMutableList()
        val s2 = listOf(m.team2FullName, m.team2Code).filter { it.isNotBlank() }.toMutableList()
        val vs = Regex("^(.+?)\\s+(?:vs\\.?|v)\\s+(.+?)(?:\\s*[,|\\-–—(].*)?$", RegexOption.IGNORE_CASE)
            .find(m.title)
        if (vs != null) {
            s1.add(vs.groupValues[1].trim())
            s2.add(vs.groupValues[2].trim())
        }
        if (s1.isEmpty() || s2.isEmpty()) return null
        return s1 to s2
    }

    private fun isSameFixture(a: MatchFixture, b: MatchFixture): Boolean {
        if ((a.status == MatchStatus.FINISHED) != (b.status == MatchStatus.FINISHED)) return false
        val sidesA = extractSides(a) ?: return false
        val sidesB = extractSides(b) ?: return false
        val directMatch = sidesA.first.any { x -> sidesB.first.any { y -> EvHelpers.sameTeamName(x, y) } } &&
            sidesA.second.any { x -> sidesB.second.any { y -> EvHelpers.sameTeamName(x, y) } }
        val swappedMatch = sidesA.first.any { x -> sidesB.second.any { y -> EvHelpers.sameTeamName(x, y) } } &&
            sidesA.second.any { x -> sidesB.first.any { y -> EvHelpers.sameTeamName(x, y) } }
        return directMatch || swappedMatch
    }

    private fun mergeAllMatchSources(
        cms: List<MatchFixture>,
        fcx: List<MatchFixture>,
        slx: List<MatchFixture>,
        wlx: List<MatchFixture>
    ): List<MatchFixture> {
        val allInput = cms + fcx + slx + wlx
        val groups = mutableListOf<MutableList<MatchFixture>>()
        for (item in allInput) {
            val existing = groups.firstOrNull { grp -> grp.any { isSameFixture(it, item) } }
            if (existing != null) {
                existing.add(item)
            } else {
                groups.add(mutableListOf(item))
            }
        }

        return groups.map { grp ->
            val primary = grp.firstOrNull { it.sourceTag == "CMS" } ?: grp.first()
            val isLive = grp.any { it.status == MatchStatus.LIVE }
            val isFin = !isLive && grp.all { it.status == MatchStatus.FINISHED }
            val status = when {
                isLive -> MatchStatus.LIVE
                isFin -> MatchStatus.FINISHED
                else -> MatchStatus.UPCOMING
            }
            val startMs = grp.map { it.startTimeMs }.filter { it > 0L && it != Long.MAX_VALUE }.minOrNull()
                ?: primary.startTimeMs

            // Banner priority: CMS sheet banner -> Willow banner -> FanCode/SonyLIV banner
            val cmsBanner = grp.firstOrNull { it.sourceTag == "CMS" && it.bannerUrl.isNotBlank() }?.bannerUrl
            val willowBanner = grp.firstOrNull { it.sourceTag == "Willow" && it.bannerUrl.isNotBlank() }?.bannerUrl
            val anyBanner = grp.firstNotNullOfOrNull { it.bannerUrl.takeIf { b -> b.isNotBlank() } }.orEmpty()
            val banner = cmsBanner ?: willowBanner ?: anyBanner

            val combinedServers = mutableListOf<StreamServer>()
            val seenUrls = HashSet<String>()
            grp.forEach { m ->
                m.servers.forEach { s ->
                    val u = s.url.trim()
                    if (u.isNotEmpty() && seenUrls.add(u)) {
                        combinedServers.add(s)
                    }
                }
            }
            // Sort priority servers (FanCode) first
            combinedServers.sortByDescending { it.isPriority }

            val fcxItem = grp.firstOrNull { it.fcxMatchId.isNotBlank() }

            primary.copy(
                status = status,
                startTimeMs = startMs,
                bannerUrl = banner,
                servers = combinedServers,
                fcxMatchId = fcxItem?.fcxMatchId ?: primary.fcxMatchId,
                fcxIndiaM3u8 = fcxItem?.fcxIndiaM3u8 ?: primary.fcxIndiaM3u8
            )
        }.sortedWith(
            compareBy<MatchFixture> {
                when (it.status) {
                    MatchStatus.LIVE -> 0
                    MatchStatus.UPCOMING -> 1
                    MatchStatus.FINISHED -> 2
                }
            }.thenBy { it.startTimeMs }
        )
    }

    private fun findHeroLinkedMatch(rawTitle: String, matches: List<MatchFixture>): MatchFixture? {
        val m = Regex("^(.{2,40}?)\\s+v(?:s|\\.)?\\s+(.{2,40})$", RegexOption.IGNORE_CASE).find(rawTitle.trim())
            ?: return null
        val a = m.groupValues[1].trim()
        val b = m.groupValues[2].trim()
        return matches.firstOrNull { fix ->
            val sides = extractSides(fix) ?: return@firstOrNull false
            val direct = sides.first.any { EvHelpers.sameTeamName(it, a) } && sides.second.any { EvHelpers.sameTeamName(it, b) }
            val swapped = sides.first.any { EvHelpers.sameTeamName(it, b) } && sides.second.any { EvHelpers.sameTeamName(it, a) }
            direct || swapped
        }
    }

    // ── VidAPI Movies & TV Shows (`vidapi.ru/movies/latest/page-{n}.json`) ──
    suspend fun fetchCatalogPage(type: String, page: Int): Pair<Int, List<CatalogItem>> {
        val path = if (type == "tv") "tvshows" else "movies"
        val url = "$VIDAPI_BASE/$path/latest/page-$page.json"
        val body = httpGetWithProxyFallback(url) ?: return 1 to emptyList()
        return try {
            val root = JSONObject(body)
            val totalPages = root.optInt("total_pages", 1)
            val arr = root.optJSONArray("items") ?: JSONArray()
            val list = mutableListOf<CatalogItem>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val title = o.optString("title", "").trim()
                if (title.isEmpty()) continue
                val tmdb = o.optString("tmdb_id", "")
                val imdb = o.optString("imdb_id", "")
                val id = tmdb.ifBlank { imdb }.ifBlank { "$title-${o.optString("year")}" }
                var poster = o.optString("poster_url", "").trim()
                if (poster.contains("/t/p/original/") || Regex("/t/p/w\\d+/").containsMatchIn(poster)) {
                    poster = poster.replace(Regex("/t/p/(original|w\\d+)/"), "/t/p/w342/")
                }
                val genre = o.optString("genre", "")
                val adult = EvHelpers.isAdultContent(title, genre, o.optBoolean("adult", false))
                val embedUrl = o.optString("embed_url", "").ifBlank {
                    if (type == "tv") "$VAPLAYER_BASE/embed/tv/$id/1/1" else "$VAPLAYER_BASE/embed/movie/$id"
                }
                list.add(
                    CatalogItem(
                        id = id,
                        tmdbId = tmdb,
                        imdbId = imdb,
                        title = title,
                        year = o.optString("year", ""),
                        posterUrl = poster,
                        rating = o.optString("rating", ""),
                        genre = genre,
                        popularity = o.optString("popularity", "0").toDoubleOrNull() ?: 0.0,
                        embedUrl = embedUrl,
                        type = type,
                        isAdult18 = adult
                    )
                )
            }
            totalPages to list
        } catch (e: Exception) {
            1 to emptyList()
        }
    }

    // ── VIP IPTV M3U Playlist Parser (replicates `parseM3U` in VIP Player) ──
    suspend fun fetchVipPlaylist(): List<VipIptvChannel> {
        val text = httpGet(VIP_M3U_URL) ?: return emptyList()
        val lines = text.lines()
        val channels = mutableListOf<VipIptvChannel>()
        val attrRegex = Regex("([a-zA-Z0-9\\-]+)=(\"([^\"]*)\"|'([^']*)')")
        var pendingName = ""
        var pendingLogo = ""
        var pendingGroup = ""

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#")) {
                if (line.startsWith("#EXTINF")) {
                    val commaIdx = line.lastIndexOf(',')
                    val attrsPart = if (commaIdx > -1) line.substring(0, commaIdx) else line
                    val titlePart = if (commaIdx > -1) line.substring(commaIdx + 1).trim() else ""
                    var tvgName = ""
                    var tvgLogo = ""
                    var groupTitle = ""
                    attrRegex.findAll(attrsPart).forEach { m ->
                        val k = m.groupValues[1].lowercase()
                        val v = m.groupValues[3].ifEmpty { m.groupValues[4] }
                        when (k) {
                            "tvg-name" -> tvgName = v
                            "tvg-logo" -> tvgLogo = v
                            "group-title" -> groupTitle = v
                        }
                    }
                    pendingName = tvgName.ifBlank { titlePart }.ifBlank { "Untitled Channel" }
                    pendingLogo = tvgLogo
                    pendingGroup = groupTitle.trim().ifBlank { "Uncategorized" }
                }
                continue
            }
            if (line.startsWith("http://", true) || line.startsWith("https://", true) || line.contains("://")) {
                channels.add(
                    VipIptvChannel(
                        name = pendingName.ifBlank { "Untitled Channel" },
                        logo = pendingLogo,
                        group = pendingGroup.ifBlank { "Uncategorized" },
                        url = line
                    )
                )
                pendingName = ""
                pendingLogo = ""
                pendingGroup = ""
            }
        }
        return channels
    }
}

data class EvCmsBundle(
    val settings: Map<String, String>,
    val settingsNotes: Map<String, String>,
    val heroSlides: List<HeroSlide>,
    val matches: List<MatchFixture>,
    val fancodeMatches: List<MatchFixture>,
    val sonyLivMatches: List<MatchFixture>,
    val willowMatches: List<MatchFixture>,
    val channels: List<LiveChannel>,
    val fifaChannels: List<LiveChannel>,
    val socialLinks: List<SocialLinkItem>
)
