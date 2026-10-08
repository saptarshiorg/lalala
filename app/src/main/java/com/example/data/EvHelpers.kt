package com.example.data

import kotlin.math.min

/**
 * Complete replication of all helper scripts in the HTML:
 * - `evTeamName`, `evStripLang`
 * - `sxDetectSport`, `sxSameSport`, `sxQual`
 * - `sxFlagUrl` (flagcdn.com resolver)
 * - `countryFlagUrl`
 * - `sxIsHlsUrl`, `sxIsDashUrl`, `sxIsStreamUrl`
 * - `evIs18` (18+ adult content detector)
 * - `evTournamentLogo` (recognized tournaments: WCL, ICC, IPL, FIFA, EPL, LaLiga, ISL)
 */
object EvHelpers {

    private val ACRONYMS = setOf(
        "fc", "cf", "sc", "ac", "afc", "usa", "uae", "uk", "nz", "sa", "wi", "psg",
        "rcb", "csk", "kkr", "srh", "dc", "lsg", "gt", "rr", "mi", "pbks",
        "ind", "aus", "pak", "eng", "sl", "ban", "afg", "zim"
    )
    private val MINOR_WORDS = setOf(
        "of", "and", "the", "de", "la", "del", "di", "da", "du", "von", "van", "al", "el"
    )

    private val LANG_NAMES = "english|hindi|bengali|bangla|tamil|telugu|kannada|malayalam|marathi|gujarati|punjabi|odia|oriya|urdu|assamese|bhojpuri|nepali|sinhala|spanish|arabic|french|portuguese|german|italian|russian|indonesian|thai|vietnamese|turkish|multi(?:[ -]?(?:audio|language|lang))?"
    private val LANG_TAG_REGEX = Regex(
        "\\s*[\\[\\(\\{]\\s*(?:$LANG_NAMES)(?:\\s*(?:[,/&+|]|and|-)\\s*(?:$LANG_NAMES))*\\s*(?:audio|commentary|feed|dub|language)?\\s*[\\]\\)\\}]",
        RegexOption.IGNORE_CASE
    )

    fun evStripLang(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text.replace(LANG_TAG_REGEX, "").replace(Regex("\\s{2,}"), " ").trim()
    }

    private fun formatSide(raw: String): String {
        val t = raw.replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty()) return t
        val words = t.split(" ")
        if (words.size == 1) {
            val w = words[0]
            if (Regex("^[A-Za-z0-9.&-]{1,4}$").matches(w) &&
                (w == w.uppercase() || (w == w.lowercase() && w.length <= 3))
            ) {
                return w.uppercase()
            }
        }
        return words.mapIndexed { idx, w ->
            val lo = w.lowercase()
            val up = w.uppercase()
            if (w != lo && w != up) {
                w
            } else if (ACRONYMS.contains(lo) || (w == up && w.length <= 3 && words.size > 1 && w.any { it in 'A'..'Z' })) {
                up
            } else if (idx > 0 && MINOR_WORDS.contains(lo)) {
                lo
            } else {
                w.replaceFirstChar { it.uppercase() }
            }
        }.joinToString(" ")
    }

    fun evTeamName(raw: String?): String {
        val clean = evStripLang(raw)
        if (clean.isEmpty()) return ""
        val match = Regex("^(.+?)\\s+(?:vs\\.?|v|x)\\s+(.+?)(\\s*[\\(\\[\\|\\-\\u2013\\u2014].*)?$", RegexOption.IGNORE_CASE)
            .find(clean) ?: return clean
        val side1 = formatSide(match.groupValues[1])
        val side2 = formatSide(match.groupValues[2])
        val tail = match.groupValues.getOrNull(3).orEmpty()
        return "$side1 vs $side2$tail"
    }

    fun sxIsHlsUrl(url: String?): Boolean {
        val u = url?.trim().orEmpty()
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) return false
        return Regex("\\.m3u8(\\?|#|/|$)", RegexOption.IGNORE_CASE).containsMatchIn(u) ||
            Regex("(^|[?&])(format|type)=(m3u8|hls)(&|$)", RegexOption.IGNORE_CASE).containsMatchIn(u)
    }

    fun sxIsDashUrl(url: String?): Boolean {
        val u = url?.trim().orEmpty()
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) return false
        return Regex("\\.mpd(\\?|#|/|$)", RegexOption.IGNORE_CASE).containsMatchIn(u) ||
            Regex("(^|[?&])(format|type)=(mpd|dash)(&|$)", RegexOption.IGNORE_CASE).containsMatchIn(u)
    }

    fun sxIsDirectStreamUrl(url: String?): Boolean {
        val u = url?.trim().orEmpty()
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) return false
        return sxIsHlsUrl(u) || sxIsDashUrl(u) ||
            Regex("\\.(m3u|ts|mp4|mkv)(\\?|#|$)", RegexOption.IGNORE_CASE).containsMatchIn(u)
    }

    fun isFanCodePlayUrl(url: String?): Boolean {
        val u = url?.trim().orEmpty()
        return Regex("(?:^|/)(?:play|play\\.html)\\?id=([^&#]+)", RegexOption.IGNORE_CASE).containsMatchIn(u)
    }

    fun extractFanCodeId(url: String?): Pair<String, Int>? {
        val u = url?.trim().orEmpty()
        val m = Regex("(?:^|/)(?:play|play\\.html)\\?id=([^&#]+)", RegexOption.IGNORE_CASE).find(u) ?: return null
        val idRaw = m.groupValues[1]
        val sMatch = Regex("[?&]s=(\\d+)").find(u)
        val sIndex = sMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return idRaw to sIndex
    }

    fun classifyStreamUrl(url: String?): StreamSourceKind {
        if (sxIsDirectStreamUrl(url)) return StreamSourceKind.DIRECT_LIBVLC
        if (isFanCodePlayUrl(url)) return StreamSourceKind.FANCODE_RESOLVER
        return StreamSourceKind.EMBED_WEBVIEW
    }

    private val ADULT_KEYWORDS = Regex(
        "\\b(xxx|porn\\w*|erotic\\w*|hentai|nude|naked|bdsm|fetish|kamasutra|uncensored|sexy|sex|sexual|sensual|seduc\\w*|adult(s)? only|adult film|18\\+|lust|call girl|escort|stripper|mistress|affair)\\b",
        RegexOption.IGNORE_CASE
    )

    fun isAdultContent(title: String?, genre: String?, adultFlag: Boolean = false): Boolean {
        if (adultFlag) return true
        if (!genre.isNullOrBlank() && Regex("\\b(adult|erotic|erotica)\\b", RegexOption.IGNORE_CASE).containsMatchIn(genre)) {
            return true
        }
        return !title.isNullOrBlank() && ADULT_KEYWORDS.containsMatchIn(title)
    }

    // Sport keywords from #sx-sport-detect
    private val SPORT_KEYWORDS: Map<String, List<String>> = mapOf(
        "Cricket" to listOf("cricket", "ipl", "bbl", "psl", "cpl", "t20", "t20i", "odi", "ranji", "wpl", "icc", "the hundred", "sa20", "ilt20", "lpl", "bpl", "wicket", "test match", "mumbai indians", "chennai super kings", "royal challengers", "knight riders", "sunrisers", "rajasthan royals", "gujarat titans", "lucknow super giants", "punjab kings", "wcl", "legends"),
        "Football" to listOf("football", "soccer", "association football", "futsal", "beach soccer", "fifa", "uefa", "premier league", "epl", "la liga", "laliga", "bundesliga", "serie a", "ligue 1", "mls", "isl", "i league", "champions league", "europa league", "conference league", "nations league", "afc", "caf", "conmebol", "copa america", "copa libertadores", "fa cup", "carabao cup", "asean cup", "fc", "cf"),
        "Tennis" to listOf("tennis", "atp", "wta", "wimbledon", "roland garros", "french open", "us open", "australian open", "grand slam", "davis cup", "itf"),
        "Basketball" to listOf("basketball", "nba", "wnba", "ncaab", "euroleague", "fiba", "nbl"),
        "Motorsport" to listOf("motorsport", "f1", "f2", "f3", "formula 1", "formula one", "formula e", "indycar", "nascar", "rally", "motogp", "moto gp", "superbike", "wsbk", "grand prix", "wrc", "le mans"),
        "Kabaddi" to listOf("kabaddi", "pkl", "pro kabaddi"),
        "Badminton" to listOf("badminton", "bwf", "thomas cup", "uber cup", "sudirman cup"),
        "Hockey" to listOf("hockey", "field hockey", "fih", "hockey india league", "hil"),
        "Ice Hockey" to listOf("ice hockey", "nhl", "khl", "stanley cup"),
        "MMA" to listOf("mma", "ufc", "bellator", "pfl", "one championship", "cage warriors"),
        "Wrestling" to listOf("wrestling", "wwe", "aew", "nxt"),
        "Boxing" to listOf("boxing", "wbc", "wba", "ibf", "wbo"),
        "Golf" to listOf("golf", "pga", "lpga", "liv golf", "ryder cup", "dp world tour"),
        "Table Tennis" to listOf("table tennis", "ping pong", "wtt", "utt"),
        "Volleyball" to listOf("volleyball", "beach volleyball", "vnl", "prime volleyball"),
        "Baseball" to listOf("baseball", "mlb", "npb", "kbo", "softball"),
        "Rugby" to listOf("rugby", "six nations", "super rugby", "nrl"),
        "American Football" to listOf("american football", "nfl", "ncaaf", "super bowl"),
        "Athletics" to listOf("athletics", "track and field", "diamond league", "marathon"),
        "Esports" to listOf("esports", "valorant", "csgo", "cs2", "dota", "pubg", "bgmi", "league of legends")
    )

    private val GENERIC_SPORT = Regex("^(sports?|live|other|others|misc|general|various|more|events?|special events?)?$", RegexOption.IGNORE_CASE)

    private fun norm(s: String?): String =
        s.orEmpty().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    fun detectSport(category: String?, title: String?, extra: String? = ""): String {
        val c = if (GENERIC_SPORT.matches(category?.trim().orEmpty())) "" else norm(category)
        val t = norm(title)
        val b = norm(extra)
        val sources = listOf(c to 4, t to 2, b to 1).filter { it.first.isNotEmpty() }
        if (sources.isEmpty()) return "Other"

        var best = ""
        var bestScore = 0.0
        for ((sportName, keywords) in SPORT_KEYWORDS) {
            var total = 0.0
            for ((srcText, weight) in sources) {
                var hit = false
                var maxLen = 0
                for (kw in keywords) {
                    val re = Regex("(^| )$kw( |$)")
                    if (re.containsMatchIn(srcText)) {
                        hit = true
                        if (kw.length > maxLen) maxLen = kw.length
                    }
                }
                if (hit) total += weight + maxLen / 1000.0
            }
            if (total > bestScore) {
                bestScore = total
                best = sportName
            }
        }
        if (best.isNotEmpty()) return best
        return if (c.isNotEmpty()) category!!.trim() else "Other"
    }

    fun sameSport(a: String?, b: String?): Boolean {
        val da = detectSport(a, "", "").lowercase()
        val db = detectSport(b, "", "").lowercase()
        return da.isNotEmpty() && da == db
    }

    // Known tournaments from #evt-js
    data class KnownTournamentInfo(
        val key: String,
        val name: String,
        val logo: String,
        val isDark: Boolean,
        val chips: List<String>,
        val desc: String,
        val regex: Regex
    )

    private val KNOWN_TOURNAMENTS = listOf(
        KnownTournamentInfo(
            key = "wcl",
            name = "World Championship of Legends",
            logo = "https://www.wclcricket.com/assets/wcl-bnw-season3-20260927.png",
            isDark = true,
            chips = listOf("Cricket", "Legends", "T20"),
            desc = "The World Championship of Legends (WCL) is a T20 cricket tournament starring retired international greats.",
            regex = Regex("\\bwcl\\b|world championship of legends|world champion|legend", RegexOption.IGNORE_CASE)
        ),
        KnownTournamentInfo(
            key = "icc",
            name = "ICC",
            logo = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR3RBl2jouCdUHrLSHRm2lopxkCV4ZpMhGoAxdCVrLYQkBkYOWnNCkb50U&s=10",
            isDark = false,
            chips = listOf("Cricket", "Global"),
            desc = "The International Cricket Council is cricket’s world governing body running World Cups & Champions Trophy.",
            regex = Regex("\\bicc\\b|international cricket council|champions trophy|t20 world cup|cricket world cup|world test championship", RegexOption.IGNORE_CASE)
        ),
        KnownTournamentInfo(
            key = "ipl",
            name = "Indian Premier League",
            logo = "https://crystalpng.com/wp-content/uploads/2025/09/ipl-logo.png",
            isDark = false,
            chips = listOf("Cricket", "T20", "India"),
            desc = "The Indian Premier League is a franchise T20 cricket league founded by the BCCI in 2008.",
            regex = Regex("\\bipl\\b|indian premier league", RegexOption.IGNORE_CASE)
        ),
        KnownTournamentInfo(
            key = "fifa",
            name = "FIFA",
            logo = "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/aa/FIFA_logo_without_slogan.svg/1280px-FIFA_logo_without_slogan.svg.png",
            isDark = false,
            chips = listOf("Football", "Global"),
            desc = "FIFA is football’s world governing body and organises the FIFA World Cup.",
            regex = Regex("\\bfifa\\b|(^|\\s)world cup(?!.*(cricket|t20|odi))", RegexOption.IGNORE_CASE)
        ),
        KnownTournamentInfo(
            key = "epl",
            name = "Premier League",
            logo = "https://thumb.wikimedia.org/wikipedia/en/thumb/f/f2/Premier_League_Logo.svg/1280px-Premier_League_Logo.svg.png",
            isDark = false,
            chips = listOf("Football", "England"),
            desc = "The Premier League is England’s top football division, contested by 20 clubs.",
            regex = Regex("^(english )?premier league|\\bepl\\b|english premier league", RegexOption.IGNORE_CASE)
        ),
        KnownTournamentInfo(
            key = "laliga",
            name = "LaLiga",
            logo = "https://thumb.wikimedia.org/wikipedia/commons/thumb/5/54/LaLiga_EA_Sports_2023_Vertical_Logo.svg/1280px-LaLiga_EA_Sports_2023_Vertical_Logo.svg.png",
            isDark = false,
            chips = listOf("Football", "Spain"),
            desc = "LaLiga is Spain’s top football division, with 20 clubs including Real Madrid and Barcelona.",
            regex = Regex("la ?liga", RegexOption.IGNORE_CASE)
        ),
        KnownTournamentInfo(
            key = "isl",
            name = "Indian Super League",
            logo = "https://images.seeklogo.com/logo-png/26/1/indian-super-league-logo-png_seeklogo-269160.png",
            isDark = false,
            chips = listOf("Football", "India"),
            desc = "The Indian Super League is the top tier of professional football in India.",
            regex = Regex("\\bisl\\b|indian super league", RegexOption.IGNORE_CASE)
        )
    )

    fun findTournamentMeta(name: String?): KnownTournamentInfo? {
        if (name.isNullOrBlank()) return null
        return KNOWN_TOURNAMENTS.firstOrNull { it.regex.containsMatchIn(name) }
    }

    // Country flag resolver from #sx-flagcdn-helper and COUNTRY_TO_ISO2
    private val COUNTRY_MAP = mapOf(
        "india" to "in", "ind" to "in", "in" to "in", "bharat" to "in",
        "australia" to "au", "aus" to "au", "au" to "au",
        "england" to "gb-eng", "eng" to "gb-eng", "uk" to "gb", "united kingdom" to "gb", "great britain" to "gb",
        "pakistan" to "pk", "pak" to "pk", "pk" to "pk",
        "new zealand" to "nz", "nzl" to "nz", "nz" to "nz",
        "south africa" to "za", "rsa" to "za", "sa" to "za", "zaf" to "za",
        "sri lanka" to "lk", "sl" to "lk", "lka" to "lk", "lk" to "lk",
        "bangladesh" to "bd", "ban" to "bd", "bgd" to "bd", "bd" to "bd",
        "afghanistan" to "af", "afg" to "af", "af" to "af",
        "west indies" to "jm", "wi" to "jm",
        "zimbabwe" to "zw", "zim" to "zw", "zwe" to "zw",
        "ireland" to "ie", "irl" to "ie", "ie" to "ie",
        "netherlands" to "nl", "ned" to "nl", "nld" to "nl", "nl" to "nl",
        "scotland" to "gb-sct", "sco" to "gb-sct",
        "nepal" to "np", "nep" to "np", "npl" to "np",
        "uae" to "ae", "united arab emirates" to "ae", "are" to "ae",
        "usa" to "us", "united states" to "us", "us" to "us", "america" to "us",
        "canada" to "ca", "can" to "ca", "ca" to "ca",
        "brazil" to "br", "bra" to "br", "br" to "br",
        "argentina" to "ar", "arg" to "ar", "ar" to "ar",
        "france" to "fr", "fra" to "fr", "fr" to "fr",
        "spain" to "es", "esp" to "es", "es" to "es",
        "germany" to "de", "ger" to "de", "deu" to "de", "de" to "de",
        "italy" to "it", "ita" to "it", "it" to "it",
        "portugal" to "pt", "por" to "pt", "prt" to "pt", "pt" to "pt",
        "japan" to "jp", "jpn" to "jp", "jp" to "jp",
        "south korea" to "kr", "kor" to "kr", "korea" to "kr", "kr" to "kr",
        "china" to "cn", "chn" to "cn", "cn" to "cn",
        "qatar" to "qa", "qat" to "qa", "qa" to "qa",
        "saudi arabia" to "sa", "sau" to "sa",
        "oman" to "om", "oma" to "om", "omn" to "om",
        "malaysia" to "my", "mys" to "my",
        "singapore" to "sg", "sgp" to "sg",
        "hong kong" to "hk", "hkg" to "hk"
    )

    fun sxFlagUrl(teamOrCountry: String?): String {
        val raw = teamOrCountry?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        val cleaned = raw.lowercase()
            .replace(Regex("\\s+(women|womens|woman|ladies|w|men|mens|u ?\\d{2}|under ?\\d{2}|xi|a|b)$"), "")
            .replace(Regex("[^a-z0-9 -]"), "")
            .trim()
        val iso = COUNTRY_MAP[cleaned] ?: if (cleaned.length == 2 && cleaned.all { it in 'a'..'z' }) cleaned else ""
        return if (iso.isNotEmpty()) "https://flagcdn.com/w80/$iso.png" else ""
    }

    fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        val dp = IntArray(n + 1) { it }
        for (i in 1..m) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..n) {
                val temp = dp[j]
                dp[j] = if (a[i - 1] == b[j - 1]) prev else 1 + min(prev, min(dp[j], dp[j - 1]))
                prev = temp
            }
        }
        return dp[n]
    }

    fun sameTeamName(a: String?, b: String?): Boolean {
        val na = norm(a).let { COUNTRY_MAP[it] ?: it }
        val nb = norm(b).let { COUNTRY_MAP[it] ?: it }
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na == nb) return true
        val minLen = min(na.length, nb.length)
        if (minLen >= 3 && (na.startsWith(nb) || nb.startsWith(na))) return true
        val maxLen = maxOf(na.length, nb.length)
        val sim = 1.0 - levenshtein(na, nb).toDouble() / maxLen.toDouble()
        return sim >= 0.78
    }
}
