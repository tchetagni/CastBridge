package castbridge.core.library.agent

/** What the rules understood of one file name. Nothing here comes from the content of the file. */
data class Parsed(
    val media: Media,
    val kind: Kind,
    val ext: String,
    val title: String = "",
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeEnd: Int? = null,
    val episodeTitle: String? = null,
    val artist: String? = null,
    val track: Int? = null,
    /** Height in pixels (720, 1080, 2160), from the name. */
    val resolution: Int? = null,
    val audio: Audio? = null,
    /** Language code of a subtitle file ("fr", "en"). */
    val subLang: String? = null,
    /** Personal media: "2024-03-15", "14:22", sequence number, origin label ("WhatsApp"). */
    val date: String? = null,
    val time: String? = null,
    val seq: Int? = null,
    val origin: String? = null,
    /** Course subject ("Mathématiques"). */
    val subject: String? = null,
    /** Language of the name itself ("fr" / "en"): drives title case. */
    val nameLang: String = "fr",
    val confidence: Double = 0.0,
    /** Short rule id, for the explanation shown to the user and for the tests. */
    val rule: String = "",
    /** The name looked like a copy ("Copie de", "(1)"): a duplicate hint. */
    val copy: Boolean = false,
    /** Download-site junk or release tags were removed. */
    val hadJunk: Boolean = false,
    /** The text that remains after a light cleaning (used when nothing better is known). */
    val stem: String = "",
) {
    val titleKey: String get() = Text.key(title)

    /** What makes two files "the same thing" whatever their quality, or null if unknown. */
    val identity: String?
        get() = when {
            title.isBlank() -> null
            kind == Kind.SERIES && season != null && episode != null -> "s:$titleKey:$season:$episode"
            kind == Kind.MOVIE -> "m:$titleKey:${year ?: ""}"
            (kind == Kind.MUSIC || kind == Kind.CLIP) && artist != null -> "a:${Text.key(artist)}:$titleKey"
            else -> null
        }
}

/**
 * The rules engine: from a file name (and, when known, its folder and duration) to [Parsed].
 * 100 % local, deterministic, no network. See docs/LIBRARY-AGENT.md for the rules.
 */
object NameParser {
    /** Word boundary that also works around accented letters on the JVM and on Android (ICU does not accept the (?U) flag). */
    private const val UB = "(?:(?<=[\\p{L}\\p{N}_])(?![\\p{L}\\p{N}_])|(?<![\\p{L}\\p{N}_])(?=[\\p{L}\\p{N}_]))"
    private fun ur(p: String) = Regex(p.replace("\\b", UB), RegexOption.IGNORE_CASE)

    private val VIDEO_EXT = setOf("mp4", "m4v", "mkv", "webm", "avi", "mov", "ts", "m2ts", "mts", "mpg", "mpeg", "wmv", "flv", "3gp", "ogv", "vob", "divx", "rmvb")
    private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "ac3", "mka", "amr")
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")
    private val DOC_EXT = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "odt", "ods", "odp", "epub", "mobi", "csv", "rtf")
    private val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "iso")
    private val SUB_EXT = setOf("srt", "ass", "ssa", "vtt", "sub", "idx")
    private val APP_EXT = setOf("apk", "xapk", "apks", "apkm", "aab")

    fun mediaOf(ext: String): Media = when (ext.lowercase()) {
        in VIDEO_EXT -> Media.VIDEO
        in AUDIO_EXT -> Media.AUDIO
        in IMAGE_EXT -> Media.IMAGE
        in DOC_EXT -> Media.DOC
        in ARCHIVE_EXT -> Media.ARCHIVE
        in SUB_EXT -> Media.SUBTITLE
        in APP_EXT -> Media.APP
        else -> Media.OTHER
    }

    private val KNOWN_EXT = VIDEO_EXT + AUDIO_EXT + IMAGE_EXT + DOC_EXT + ARCHIVE_EXT + SUB_EXT + APP_EXT

    /** Splits "name.ext": a known extension, or a short alphanumeric one when the name has no space before it. */
    fun splitExt(fileName: String): Pair<String, String> {
        val dot = fileName.lastIndexOf('.')
        if (dot <= 0 || dot == fileName.length - 1) return fileName to ""
        val ext = fileName.substring(dot + 1)
        val known = ext.lowercase() in KNOWN_EXT
        val plausible = ext.length in 1..5 && ext.all { it.isLetterOrDigit() } && ext.any { it.isLetter() }
        return if (known || plausible && !ext.contains(' ')) fileName.substring(0, dot) to ext.lowercase() else fileName to ""
    }

    // ------------------------------------------------------------------ personal media (phone camera, messengers)

    /** The names the agent itself produces: "Vidéo WhatsApp – 2024-03-15 14h22", so that a second run changes nothing. */
    private val RX_OWN = Regex("^(Vidéo|Video|Audio|Photo|Capture d'écran|Screenshot|Enregistrement d'écran|Screen recording)( WhatsApp| Telegram)? – (\\d{4})-(\\d{2})-(\\d{2})(?: (\\d{2})h(\\d{2}))?(?: \\((\\d+)\\))?$")
    private val RX_WA_LONG = Regex("^WhatsApp[ _](Video|Image|Audio|Vid[ée]o|Ptt|Voice Note|Sticker|Document)[ _](\\d{4})-(\\d{2})-(\\d{2})[ _](?:at|à|a)[ _](\\d{1,2})[.:h](\\d{2})(?:[.:](\\d{2}))?(?:[ _]*([AP]M))?(?:[ _]*\\((\\d+)\\))?$", RegexOption.IGNORE_CASE)
    private val RX_WA_SHORT = Regex("^(VID|IMG|AUD|PTT|STK|DOC)-(\\d{4})(\\d{2})(\\d{2})-WA(\\d{3,5})(?:[ _]*\\(\\d+\\))?$", RegexOption.IGNORE_CASE)
    private val RX_CAM_DT = Regex("^(VID|IMG|MOV|PXL|PANO|BURST|VIDEO|Screenrecorder|Screen[ _]?Recording|Screenshot|Record|REC)[-_ ](\\d{4})-?(\\d{2})-?(\\d{2})[-_ ]+(?:at[-_ ])?(\\d{2})[-.:_]?(\\d{2})[-.:_]?(\\d{2})?\\d*(?:[-_ ].*|\\.[A-Za-z]{2,4})?$", RegexOption.IGNORE_CASE)
    private val RX_BARE_DT = Regex("^(\\d{4})(\\d{2})(\\d{2})[_-](\\d{2})(\\d{2})(\\d{2})(?:[_ .-]+(?:\\d{1,3}|HDR|BURST\\d{0,3}|COVER|MOTION|NIGHT|PORTRAIT|PANO|MP|EDITED?|ORIGINAL|LIVE))*(?:\\s*\\(\\d+\\))?$", RegexOption.IGNORE_CASE)
    private val RX_SHOT_TEXT = Regex("^(Screenshot|Screen Shot|Capture d['’]écran|Capture d['’]ecran)[ _]+(\\d{4})-(\\d{2})-(\\d{2})[ _]+(?:at|à|a)[ _]+(\\d{1,2})[.:h](\\d{2})(?:[.:]\\d{2})?(?:\\s*\\(\\d+\\))?$", RegexOption.IGNORE_CASE)
    private val RX_TELEGRAM = Regex("^(video|photo|voice|audio)[_ ](?:file[_ ])?(\\d{4})-(\\d{2})-(\\d{2})[_ ](\\d{2})-(\\d{2})-(\\d{2})$", RegexOption.IGNORE_CASE)
    private val RX_COUNTER = Regex("^(IMG|DSC|DSCN|DSCF|MVI|MOV|VID|GOPR|GX\\d{0,2}|P)[_-]?(\\d{3,7})$", RegexOption.IGNORE_CASE)

    private fun validDate(y: Int, m: Int, d: Int) = y in 1990..2100 && m in 1..12 && d in 1..31

    private fun personal(stem: String, media: Media, ext: String): Parsed? {
        fun mk(o: String, y: String, m: String, d: String, hh: String?, mm: String?, seq: Int?, rule: String): Parsed? {
            if (!validDate(y.toInt(), m.toInt(), d.toInt())) return null
            val time = if (hh != null && mm != null && hh.toInt() in 0..23 && mm.toInt() in 0..59) "${hh.padStart(2, '0')}:$mm" else null
            val kind = if (media == Media.IMAGE) Kind.PHOTO else Kind.PERSONAL
            return Parsed(media, kind, ext, date = "$y-$m-$d", time = time, seq = seq, origin = o, confidence = 0.95, rule = rule)
        }
        RX_OWN.matchEntire(stem)?.let { m ->
            val g = m.groupValues
            val origin = when { g[2].isNotEmpty() -> g[2].trim(); g[1].startsWith("Capture") -> "Capture"; g[1].startsWith("Enreg") -> "Écran"; else -> "Caméra" }
            mk(origin, g[3], g[4], g[5], g[6].ifEmpty { null }, g[7].ifEmpty { null }, g[8].toIntOrNull(), "personal.own")?.let { return it }
        }
        RX_WA_LONG.matchEntire(stem)?.let { m ->
            val g = m.groupValues
            var hh = g[5]
            if (g[8].isNotEmpty()) {   // "2.22.11 PM" -> 14:22
                val h = hh.toInt()
                val h24 = if (g[8].equals("PM", true)) (if (h < 12) h + 12 else h) else (if (h == 12) 0 else h)
                hh = h24.toString().padStart(2, '0')
            }
            return mk("WhatsApp", g[2], g[3], g[4], hh, g[6], g[9].toIntOrNull(), "personal.whatsapp")
        }
        RX_WA_SHORT.matchEntire(stem)?.let { m ->
            val g = m.groupValues
            return mk("WhatsApp", g[2], g[3], g[4], null, null, g[5].toIntOrNull(), "personal.whatsapp")
        }
        RX_TELEGRAM.matchEntire(stem)?.let { m ->
            val g = m.groupValues
            return mk("Telegram", g[2], g[3], g[4], g[5], g[6], null, "personal.telegram")
        }
        RX_CAM_DT.matchEntire(stem)?.let { m ->
            val g = m.groupValues
            val label = when {
                g[1].startsWith("Screenshot", true) -> "Capture"
                g[1].startsWith("Screen", true) || g[1].equals("Record", true) || g[1].equals("REC", true) -> "Écran"
                else -> "Caméra"
            }
            return mk(label, g[2], g[3], g[4], g[5], g[6], null, "personal.camera")
        }
        RX_SHOT_TEXT.matchEntire(stem)?.let { m ->
            val g = m.groupValues
            return mk("Capture", g[2], g[3], g[4], g[5], g[6], null, "personal.screenshot")
        }
        RX_BARE_DT.matchEntire(stem)?.let { m ->
            val g = m.groupValues
            return mk("Caméra", g[1], g[2], g[3], g[4], g[5], null, "personal.camera")
        }
        RX_COUNTER.matchEntire(stem)?.let { m ->
            val kind = if (media == Media.IMAGE) Kind.PHOTO else Kind.PERSONAL
            return Parsed(media, kind, ext, seq = m.groupValues[2].toIntOrNull(), origin = "Caméra", confidence = 0.8, rule = "personal.counter")
        }
        return null
    }

    // ------------------------------------------------------------------ cleaning

    private val RX_PERCENT = Regex("%[0-9A-Fa-f]{2}")
    private val RX_COPY_PREFIX = Regex("^(?:copie|copy)\\s+(?:de|of)\\s+", RegexOption.IGNORE_CASE)
    private val RX_COPY_SUFFIX = Regex("(?:\\s*[-–]?\\s*\\((?:copie|copy)(?:\\s*\\d+)?\\)|\\s+[-–]\\s+(?:copie|copy)(?:\\s*\\(?\\d+\\)?)?|\\s*\\(\\d{1,2}\\)|\\s+copy(?:\\s*\\d+)?|\\s+copie(?:\\s*\\d+)?)$", RegexOption.IGNORE_CASE)
    private val RX_WWW = ur("(?:https?://)?www\\.[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.[a-z]{2,}")
    private val RX_DOMAIN = ur("\\b[a-z0-9][a-z0-9-]{1,}\\.(?:com|net|org|info|biz|cm|cc|ws)\\b")
    private val SITES = listOf(
        "torrent9", "wawacity", "zone-telechargement", "zone telechargement", "yggtorrent", "ygg", "yts", "rarbg", "1337x", "eztv", "nyaa", "cpasbien",
        "o2tvseries", "netnaija", "naijaprey", "waploaded", "tfpdl", "mkvcage", "mkvking", "9jarocks", "toxicwap", "fzmovies", "tvseries", "coolmoviezone",
        "worldfree4u", "skymovieshd", "filmyzilla", "3gpmania", "mp3skull", "notjustok", "tooxclusive", "naijavibes", "justnaija", "9jaflaver", "y2mate",
        "savefrom", "snaptube", "ssyoutube", "mobilism", "limetorrents", "thepiratebay", "torlock", "ettv", "galaxyrg", "movierulz", "tamilrockers",
        "hdhub4u", "katmoviehd", "mkvhub", "extramovies", "sharewood", "lacale", "la cale", "french-stream", "frenchstream", "streamcomplet", "dpstream",
        "telecharger", "telechargement", "downloadhub", "filmesonlinegratis", "pahe", "psarips", "psa", "tgx", "evo", "ntb", "mkvcinemas", "bolly4u",
    )
    private val SITE_ALT = SITES.joinToString("|") { Regex.escape(it) }
    private val RX_SITE_PREFIX = ur("^\\s*(?:$SITE_ALT)(?:\\.[a-z]{2,4})?\\s*[-–|:_.]+\\s*")
    private val RX_SITE_SUFFIX = ur("\\s*[-–|:_]+\\s*(?:$SITE_ALT)(?:\\.[a-z]{2,4})?\\s*$")
    private val SITE_ALT_DISTINCT = SITES.filter { it.length >= 6 }.joinToString("|") { Regex.escape(it) }
    private val RX_SITE_BARE_SUFFIX = ur("\\s+(?:$SITE_ALT_DISTINCT)(?:\\.[a-z]{2,4})?\\s*$")
    private val RX_SITE_BRACKET = ur("[\\[({]\\s*(?:$SITE_ALT)[^\\])}]*[\\])}]")
    /** Something that looks like an episode number after a release group: "S01E04", "1x04", "- 045", "Episode 4". */
    private val EPISODE_SHAPE = ur("\\bs\\d{1,2}\\s?e\\d|\\b\\d{1,2}x\\d{2}\\b|\\s-\\s\\d{2,4}(?:v\\d)?(?=[\\s(\\[.]|$)|\\b(?:ep|episode)[ .]*\\d")
    /** A bracket whose WHOLE content is an episode marker (S01-E08, S1E4, 1x04): kept, never dropped as a tag. */
    private val RX_BRACKETED_EPISODE = ur("s\\d{1,2}\\s*[-x–.]?\\s*e\\d{1,3}(?:\\s?[-–&]?\\s?e?\\d{1,3})*|\\d{1,2}x\\d{2,3}")
    private val RX_EMPTY_BRACKET = Regex("[\\[({]\\s*[\\])}]")
    /** Messenger channels: « t.me/canal », « t.me_canal_ » (a slash turned into an underscore by a file system), « @canal »; and the « Forwarded » prefix. */
    private val RX_TME = Regex("(?<![\\p{L}\\p{N}])(?:https?://)?t\\.me/[A-Za-z0-9_]+|^t\\.me[_ ][A-Za-z0-9]+[_ ]", RegexOption.IGNORE_CASE)
    private val RX_HANDLE = Regex("(?<![\\p{L}\\p{N}_@.])@[A-Za-z0-9_]{3,32}(?![\\p{L}\\p{N}.@])")
    private val RX_FORWARDED = Regex("^(?:forwarded|transf[ée]r[ée]|fwd)\\s*[:_-]*\\s+", RegexOption.IGNORE_CASE)

    private val JUNK_PHRASE = ur(
        "^(?:official\\s+(?:music\\s+)?(?:video|audio|lyric\\s+video|visualizer|clip)|clip\\s+officiel|vid[eé]o\\s+officielle|audio\\s+officiel(?:le)?|" +
            "lyrics?(?:\\s+video)?|paroles|lyric\\s+video|music\\s+video|full\\s+(?:video|hd|movie|film)|hd|hq|4k|uhd|1080p|720p|480p|explicit|audio|video|vid[eé]o|" +
            "mv|m/v|visualizer|new|nouveau|nouveaut[eé]|exclusive|exclusivit[eé]|free\\s+download|download|t[eé]l[eé]charger|youtube|" +
            "\\d{2,3}\\s*kbps|\\d{3}k|clip|clip\\s+hd|son\\s+officiel|multiple\\s+subtitles?|multi[- ]?subs?|eng\\s*subs?|sub(?:s|titles?)?)$"
    )
    /** "Prod. by Sarz", "Directed by X": credits after a pipe or in brackets, not part of a title. */
    private val CREDIT_TAIL = ur("^(?:prod(?:uced)?\\.?|dir(?:ected)?\\.?|shot|mixed|mastered|music|filmed)\\s+by\\b.*")
    private val RX_BRACKET = Regex("[\\[({]([^\\])}]*)[\\])}]")
    private val RX_PIPE_TAIL = Regex("\\s*[|]\\s*([^|]*)$")

    /** Everything that only makes sense in a download: copy markers, site names, junk brackets. */
    internal fun stripJunk(raw: String): Triple<String, Boolean, Boolean> {
        var s = Text.nfc(raw)
        if (RX_PERCENT.containsMatchIn(s)) s = runCatching { java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)
        var junk = false
        var copy = false
        if (RX_COPY_PREFIX.containsMatchIn(s)) { s = s.replace(RX_COPY_PREFIX, ""); copy = true }
        val cs = RX_COPY_SUFFIX.find(s)
        if (cs != null && cs.range.first > 0 && !s.substring(0, cs.range.first).all { it.isDigit() }) {
            // "Movie (2019)" is a year, not a copy marker: the pattern only takes 1-2 digits.
            s = s.substring(0, cs.range.first); copy = true
        }
        fun sub(rx: Regex) { val n = s.replace(rx, " "); if (n != s) { junk = true; s = n } }
        run { val n = s.replace(RX_FORWARDED, ""); if (n != s) { junk = true; s = n } }
        sub(RX_TME); sub(RX_HANDLE)
        s = s.trim()
        sub(RX_WWW); sub(RX_SITE_BRACKET); sub(RX_DOMAIN)
        run { val n = s.replace(RX_SITE_PREFIX, ""); if (n != s) { junk = true; s = n } }
        run { val n = s.replace(RX_SITE_SUFFIX, ""); if (n != s) { junk = true; s = n } }
        run { val n = s.replace(RX_SITE_BARE_SUFFIX, ""); if (n != s) { junk = true; s = n } }
        // brackets: junk phrases and tags go, years stay as a plain token, other text stays without its brackets' meaning
        s = RX_BRACKET.replace(s) { m ->
            val c = m.groupValues[1].replace('_', ' ').trim()
            val open = m.value[0]
            when {
                c.isEmpty() -> { junk = true; " " }
                JUNK_PHRASE.matches(c) || CREDIT_TAIL.matches(c) -> { junk = true; " " }
                c.matches(Regex("(19|20)\\d\\d")) -> " $c "
                RX_BRACKETED_EPISODE.matches(c) -> " $c "      // « Prison Break [S01-E08] »: the marker is the episode, not a release tag
                isTagSequence(c) -> { junk = true; " " }
                open == '[' && m.range.first == 0 && c.length <= 24 && c.none { it == ' ' } && s.length - m.range.last > 3 -> { junk = true; " " }
                open == '[' && m.range.first == 0 && c.length <= 30 && c.split(' ').size <= 3 && EPISODE_SHAPE.containsMatchIn(s.substring(m.range.last + 1)) -> { junk = true; " " }
                open == '[' && m.range.last == s.length - 1 && c.length <= 24 && c.none { it == ' ' } && c == c.uppercase() && c.any { it.isLetter() } -> { junk = true; " " }
                else -> m.value
            }
        }
        // "Title | Official Video"
        while (true) {
            val m = RX_PIPE_TAIL.find(s) ?: break
            val tail = m.groupValues[1].replace('_', ' ').trim()
            if (JUNK_PHRASE.matches(tail) || isTagSequence(tail) || CREDIT_TAIL.matches(tail)) { s = s.substring(0, m.range.first); junk = true } else break
        }
        s = s.replace(RX_EMPTY_BRACKET, " ")
        return Triple(s, junk, copy)
    }

    // ------------------------------------------------------------------ tags

    private val RX_INITIALS = Regex("(?<![\\p{L}\\p{N}])(?:\\p{L}\\.){2,}")
    private val RX_H26X = ur("\\b([hx])[ ._]*(26[45])\\b")
    private val RX_AUDIO_CH = ur("\\b(dd|ddp|dd\\+|aac|ac3|eac3|dts|flac|opus|mp3|truehd|atmos)[ ._+-]?([257])[ .]([01])\\b")
    private val RX_RES_TOKEN = ur("^(\\d{3,4})[pi]$")
    private val RX_DIMS = ur("^(\\d{3,4})x(\\d{3,4})$")
    private val RX_YEAR = Regex("^(19\\d\\d|20\\d\\d)$")
    private val RX_KBPS = ur("^\\d{2,3}\\s*kbps$|^\\d{3}k$|^mp3-?\\d{3}$")

    private val RES_TAGS = setOf("4k", "8k", "uhd", "fhd", "qhd", "hd", "hq", "sd", "hdr", "hdr10", "hdr10+", "dv", "dolby", "vision", "sdr", "10bit", "8bit", "10bits", "hi10p", "hi10")
    private val SOURCE_TAGS = setOf("bluray", "blu-ray", "bdrip", "brrip", "bdremux", "remux", "web-dl", "webdl", "webrip", "web", "hdtv", "pdtv", "dvdrip", "dvdscr", "dvd", "hdrip", "hdcam", "hdts", "cam", "camrip", "ts", "tc", "telesync", "r5", "vhsrip", "hddvd", "amzn", "nf", "dsnp", "hmax", "hulu", "atvp", "pcok", "pmtp", "stan", "crav", "sho", "dl", "rip", "bd", "webhd", "hdlight", "light", "screener", "scr")
    private val CODEC_TAGS = setOf("x264", "x265", "h264", "h265", "hevc", "avc", "xvid", "divx", "av1", "vp9", "aac", "ac3", "eac3", "dts", "dts-hd", "dtshd", "truehd", "atmos", "flac", "ddp", "dd", "opus", "lpcm", "mp3", "mkv", "mp4", "avi", "5.1", "7.1", "2.0")
    private val MISC_TAGS = setOf("proper", "repack", "internal", "extended", "unrated", "uncut", "imax", "complete", "integrale", "intégrale", "remastered", "remaster", "directors", "cut", "final", "sample", "nfo", "readnfo", "subs", "sub", "subbed", "hardsub", "hc", "dubbed", "dual", "dual-audio", "multisubs", "multisub", "retail", "fansub", "torrent", "download", "telecharger", "gratuit", "streaming", "hq", "lossless", "audio", "video", "vidéo")
    /** Words that only ever appear in release names: after a language word they prove it is a tag, not part of a title. */
    private val RELEASE_WORDS = setOf("proper", "repack", "internal", "retail", "sample", "readnfo", "nfo", "hc", "dubbed", "subbed", "hardsub", "multisubs", "multisub", "extended", "unrated", "uncut", "remastered")
    private val LANG_STRONG = mapOf(
        "vf" to Audio.VF, "vff" to Audio.VF, "vfq" to Audio.VF, "vfi" to Audio.VF, "vf2" to Audio.VF, "truefrench" to Audio.VF,
        "vostfr" to Audio.VOSTFR, "vost" to Audio.VOSTFR, "subfrench" to Audio.VOSTFR, "subfr" to Audio.VOSTFR, "vostf" to Audio.VOSTFR,
        "multi" to Audio.MULTI, "multilang" to Audio.MULTI, "multi-vff" to Audio.MULTI, "multi-vf" to Audio.MULTI, "multi-vfq" to Audio.MULTI,
    )
    /** Language words that may also be part of a title ("The French Connection"): a tag only when followed by other tags or the end. */
    private val LANG_WEAK = mapOf("french" to Audio.VF, "francais" to Audio.VF, "français" to Audio.VF, "english" to Audio.VO, "eng" to Audio.VO, "vo" to Audio.VO, "fr" to Audio.VF, "en" to Audio.VO, "vostfr" to Audio.VOSTFR)

    private fun tagKind(t: String): Int { // 0 none, 1 strong tag, 2 weak tag
        val l = t.lowercase()
        if (l in RES_TAGS || l in SOURCE_TAGS || l in CODEC_TAGS || l in LANG_STRONG) return 1
        if (RX_RES_TOKEN.matches(l) && l.dropLast(1).toInt() in 240..4320 || RX_DIMS.matches(l) || RX_KBPS.matches(l)) return 1
        if (l.matches(Regex("(dd|ddp|aac|ac3|eac3|dts|flac|opus|mp3|truehd)\\d{2}"))) return 1
        if (l in MISC_TAGS) return 2
        if (l in LANG_WEAK) return 2
        if (l.contains('-')) { // "x264-RARBG", "DTS-HD", "WEB-DL-GROUP", "Multi-VFF"
            val parts = l.split('-')
            if (parts[0] in CODEC_TAGS || parts[0] in SOURCE_TAGS || parts[0] in LANG_STRONG || parts[0] in RES_TAGS) return 1
            if (parts.size == 2 && (parts[0] + "-" + parts[1]) in SOURCE_TAGS) return 1
        }
        return 0
    }

    private fun isTagSequence(text: String): Boolean {
        val toks = text.split(Regex("[\\s,_+/]+")).filter { it.isNotEmpty() }
        return toks.isNotEmpty() && toks.all { tagKind(it) > 0 }
    }

    /** Index of the first token (after the first) that starts the technical tail: a strong tag, or a weak one followed by a strong tag, a year or the end. */
    private fun cutIndex(toks: List<String>, currentYear: Int): Int {
        for (i in 1 until toks.size) {
            val k = tagKind(toks[i])
            if (k == 1) return i
            if (k == 2 && weakIsTag(toks, i, currentYear)) return i
        }
        return toks.size
    }

    private fun allCaps(t: String) = t.count { it.isLetter() } >= 2 && t.filter { it.isLetter() }.all { it.isUpperCase() }

    /** A weak tag ("FINAL", "FRENCH", "PROPER") counts as one when a strong tag, a year or the end follows, or when a run of CAPITALS follows. */
    private fun weakIsTag(toks: List<String>, i: Int, currentYear: Int): Boolean {
        val next = toks.getOrNull(i + 1)
        return next == null || tagKind(next) == 1 || isYear(next, currentYear) || tagKind(next) == 2 && (allCaps(toks[i]) && allCaps(next) || next.lowercase() in RELEASE_WORDS)
    }

    /** Dots and underscores become spaces, except inside numbers ("5.1", "2.0"); video codec / audio channel notations are made single tokens first. */
    internal fun normalizeSeparators(s: String, protectDots: Boolean = false): String {
        var t = s.replace('_', ' ')
        t = RX_H26X.replace(t) { it.groupValues[1].lowercase() + it.groupValues[2] }
        t = RX_AUDIO_CH.replace(t) { it.groupValues[1].lowercase().replace("+", "") + it.groupValues[2] + it.groupValues[3] }
        if (!protectDots) {
            // "E.T.", "S.W.A.T.": single letters followed by dots keep their dots (a space follows if the next text sticks to them)
            val initials = RX_INITIALS.findAll(t).map { it.range }.toList()
            val sb = StringBuilder(t.length)
            for (i in t.indices) {
                val c = t[i]
                val inInitials = initials.firstOrNull { i in it }
                if (inInitials != null) {
                    sb.append(c)
                    if (i == inInitials.last && i < t.lastIndex && t[i + 1].isLetterOrDigit()) sb.append(' ')
                    continue
                }
                val keep = c == '.' && i > 0 && i < t.lastIndex && t[i - 1].isDigit() && t[i + 1].isDigit() &&
                    (i < 2 || !t[i - 2].isDigit()) && (i + 2 > t.lastIndex || !t[i + 2].isDigit())   // "5.1", "2.0": single digits on both sides
                if (c == '.' && !keep) sb.append(' ') else sb.append(c)
            }
            t = sb.toString()
        }
        // a hyphen between spaces or dots is a separator; "Spider-Man" keeps its hyphen
        t = t.replace(Regex("\\s+[–—]\\s+"), " - ").replace(Regex("\\s{2,}"), " ")
        return t.trim()
    }

    // ------------------------------------------------------------------ series markers

    private class Marker(val range: IntRange, val season: Int?, val episode: Int?, val episodeEnd: Int?, val rule: String)

    /** « season » and « episode » in the languages of the library (fr, en, es, pt, de, it, nl, sv, pl, tr, ru): the words only, the shapes are below. */
    private const val SEASON_W = "saison|season|temporada|staffel|stagione|seizoen|säsong|sasong|sezon|сезон"
    private const val EPISODE_W = "episode|épisode|episodio|episódio|capítulo|capitulo|cap|folge|aflevering|avsnitt|odcinek|bölüm|bolum|серия"
    private const val NUM = "(\\d{1,2}|[ivx]{1,5})"   // a season number: digits, or a Roman numeral after a season word (« Saison II »)

    private val ROMANS = mapOf("i" to 1, "ii" to 2, "iii" to 3, "iv" to 4, "v" to 5, "vi" to 6, "vii" to 7, "viii" to 8, "ix" to 9, "x" to 10,
        "xi" to 11, "xii" to 12, "xiii" to 13, "xiv" to 14, "xv" to 15, "xvi" to 16, "xvii" to 17, "xviii" to 18, "xix" to 19, "xx" to 20)
    /** « 7 » or « VII » to 7; null for anything else. */
    private fun seasonNumber(t: String): Int? = t.toIntOrNull() ?: ROMANS[t.lowercase()]
    private fun firstNumber(m: MatchResult, from: Int = 1): Int? = m.groupValues.drop(from).firstOrNull { it.isNotEmpty() }?.let { seasonNumber(it) }

    private val RX_SXE = ur("\\bs(\\d{1,2})\\s*(?:[x–-]\\s*)?e(\\d{1,3})((?:\\s?(?:-|\\+|e|-e|\\+e|&|et)\\s?e?\\d{1,3})*)\\b")
    private val RX_LAST_NUM = Regex("(\\d{1,3})(?!.*\\d)")
    private val RX_SAISON_EP = ur("\\b(?:$SEASON_W)\\s*$NUM\\s*[,-]?\\s*(?:$EPISODE_W|ep|e)\\s*\\.?\\s*(\\d{1,3})\\b")
    /** « Temporada 2 Cap 5 » written « T02 Cap 05 », and the numbers-first shapes (« 2. Sezon 6. Bölüm », « 2 сезон 6 серия »). */
    private val RX_T_CAP = ur("\\bt(\\d{1,2})\\s*(?:$EPISODE_W)\\s*\\.?\\s*(\\d{1,3})\\b")
    private val RX_NUM_FIRST = ur("\\b(\\d{1,2})\\.?\\s*(?:$SEASON_W)\\s*[,-]?\\s*(\\d{1,3})\\.?\\s*(?:$EPISODE_W)\\b")
    /** CJK, Korean, Arabic: no word boundaries in those scripts. Season then episode. */
    private val RX_ASIAN_SE = Regex("第\\s*(\\d{1,2})\\s*[季期]\\s*第\\s*(\\d{1,4})\\s*[集話话]|시즌\\s*(\\d{1,2})\\s*(\\d{1,4})\\s*화|الموسم\\s*(\\d{1,2})\\s*الحلقة\\s*(\\d{1,4})")
    private val RX_NXM = ur("\\b(\\d{1,2})x(\\d{2,3})(?:\\s?[-–]\\s?(\\d{2,3}))?\\b")
    /** « Saison 2 - 05 »: the season word, then a bare episode number after a dash. */
    private val RX_SAISON_BARE = ur("\\b(?:$SEASON_W)\\s*$NUM\\s*[-–]\\s*(\\d{1,3})\\b")
    private val RX_EP_ONLY = ur("\\b(?:$EPISODE_W|ep)\\s*\\.?\\s*(\\d{1,4})\\b|\\be(\\d{2,4})\\b|\\b(\\d{1,3})\\s*\\.?\\s*(?:bölüm|bolum|серия)\\b|第\\s*(\\d{1,4})\\s*[集話话]|(\\d{1,4})\\s*화|الحلقة\\s*(\\d{1,4})")
    private val RX_SAISON_ONLY = ur("\\b(?:$SEASON_W)\\s*$NUM\\b|\\bs(\\d{1,2})\\b(?!\\s?e\\d)|\\b(\\d{1,2})\\.?\\s*(?:sezon|сезон)\\b|第\\s*(\\d{1,2})\\s*[季期]|시즌\\s*(\\d{1,2})|الموسم\\s*(\\d{1,2})")
    private val RX_ANIME = Regex("^(.+?)\\s+-\\s+(\\d{2,4})(?:v\\d)?(?=\\s|$)")

    private fun findMarker(s: String, hasGroupPrefix: Boolean, yearAfter: (Int) -> Boolean): Marker? {
        RX_SXE.find(s)?.let { m ->
            val last = RX_LAST_NUM.find(m.groupValues[3])?.groupValues?.get(1)?.toIntOrNull()
            return Marker(m.range, m.groupValues[1].toInt(), m.groupValues[2].toInt(), last, "series.sxxexx")
        }
        RX_ASIAN_SE.find(s)?.let { m ->
            val g = m.groupValues.drop(1).filter { it.isNotEmpty() }
            return Marker(m.range, g[0].toInt(), g[1].toInt(), null, "series.saison-episode")
        }
        RX_SAISON_EP.find(s)?.let { m -> seasonNumber(m.groupValues[1])?.let { sn -> return Marker(m.range, sn, m.groupValues[2].toInt(), null, "series.saison-episode") } }
        RX_NUM_FIRST.find(s)?.let { m -> return Marker(m.range, m.groupValues[1].toInt(), m.groupValues[2].toInt(), null, "series.saison-episode") }
        RX_T_CAP.find(s)?.let { m -> return Marker(m.range, m.groupValues[1].toInt(), m.groupValues[2].toInt(), null, "series.saison-episode") }
        RX_SAISON_BARE.find(s)?.let { m -> seasonNumber(m.groupValues[1])?.let { sn -> return Marker(m.range, sn, m.groupValues[2].toInt(), null, "series.saison-number") } }
        RX_NXM.find(s)?.let { m ->
            val sn = m.groupValues[1].toInt()
            if (sn in 1..40) return Marker(m.range, sn, m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull(), "series.nxm")
        }
        RX_EP_ONLY.find(s)?.let { m ->
            if (!yearAfter(m.range.last)) {
                val ep = firstNumber(m)!!
                val sm = RX_SAISON_ONLY.find(s)
                val sn = sm?.let { firstNumber(it) }
                val range = when {
                    sm != null && sm.range.first < m.range.first -> sm.range.first..m.range.last
                    // « Episode 4 - Season 1 », « Ep 7 Saison 8 »: the season written after the episode belongs to the marker
                    sm != null && sm.range.first > m.range.last && s.substring(m.range.last + 1, sm.range.first).all { it == ' ' || it == '-' || it == '–' || it == ',' } -> m.range.first..sm.range.last
                    else -> m.range
                }
                return Marker(range, sn, ep, null, "series.episode-only")
            }
        }
        RX_SAISON_ONLY.find(s)?.let { m ->
            return Marker(m.range, firstNumber(m), null, null, "series.season-only")
        }
        if (hasGroupPrefix) RX_ANIME.find(s)?.takeUnless { m -> m.groupValues[2].length == 4 && m.groupValues[2].toInt() in 1900..2100 }?.let { m ->
            return Marker(m.groupValues[1].length..m.range.last, null, m.groupValues[2].toInt(), null, "series.anime")
        }
        return null
    }

    /** « 04 », « E04 », « Ep.04 », « #04 », « [04] »: only a number, which the folder (« Show/Saison 2 ») turns into an episode. */
    private val RX_TITLE_NUMBER = Regex("^(.+?)\\s+-\\s+0*(\\d{1,3})(?:\\s+-\\s+(.*))?$")
    private val RX_BARE_EPISODE = Regex("^[#\\[(]?\\s*(?:ep?\\.?\\s*)?0*(\\d{1,3})\\s*[\\])]?$", RegexOption.IGNORE_CASE)

    // ------------------------------------------------------------------ course / clip / language hints

    private val COURSE_STRONG = ur("\\b(?:cours|course|tuto|tutoriel|tutorial|tutorials|le[cç]on|lesson|formation|masterclass|bootcamp|udemy|coursera|openclassrooms|khan\\s*academy|apprendre|learn|learning|enseignement)\\b")
    private val COURSE_WEAK = ur("\\b(?:chapitre|chapter|td|tp|lecture|exercices?|correction|corrig[eé]|examen|concours|bac|bepc|probatoire|terminale|seconde|premi[eè]re|licence|cm[12]|s[eé]quence|r[eé]vision|revision)\\b")
    private val SUBJECTS = listOf(
        ur("\\bmath(?:s|[eé]matiques?)?\\b") to "Mathématiques",
        ur("\\b(?:physique|chimie|physics|chemistry)\\b") to "Physique-Chimie",
        ur("\\b(?:svt|biologie|biology|anatomie)\\b") to "SVT",
        ur("\\b(?:histoire|g[eé]ographie|geography|history|histoire-g[eé]o)\\b") to "Histoire-Géographie",
        ur("\\b(?:fran[cç]ais|grammaire|conjugaison|litt[eé]rature)\\b") to "Français",
        ur("\\b(?:anglais|english\\s+course|english\\s+lesson)\\b") to "Anglais",
        ur("\\b(?:philosophie|philo)\\b") to "Philosophie",
        ur("\\b(?:informatique|python|java|javascript|excel|programmation|programming|html|css|sql|linux|r[eé]seaux?)\\b") to "Informatique",
        ur("\\b(?:[eé]conomie|comptabilit[eé]|gestion|droit|marketing)\\b") to "Économie",
    )
    private val PERSONAL_WORDS = ur("\\b(?:mariage|anniversaire|bapt[eê]me|f[eê]te|fun[eé]railles|deuil|naissance|d[oô]t|communion|vacances|voyage|birthday|wedding)\\b")
    private val CLIP_WORDS = ur("(?:clip\\s+officiel|official\\s+(?:music\\s+)?video|vid[eé]o\\s+officielle|lyric\\s+video|\\blyrics?\\b|\\bofficial\\s+audio\\b|\\bm/?v\\b|music\\s+video|visualizer|audio\\s+officiel)")
    private val FR_WORDS = setOf("le", "la", "les", "des", "du", "de", "et", "un", "une", "saison", "épisode", "episode", "vf", "vostfr", "french", "francais", "français", "truefrench", "vff", "vfq", "pour", "dans", "sur", "avec", "mon", "ma", "mes", "cours", "chapitre", "leçon", "au", "aux", "est", "qui", "que", "ce", "cette", "nous", "vous")
    private val EN_WORDS = setOf("the", "and", "of", "season", "to", "in", "for", "with", "my", "you", "me", "is", "lesson", "chapter", "english", "vo", "eng", "from", "love")

    /** Language of a TITLE: its own words decide ("house of cards" is English even in "... Saison 2 Épisode 4 VF"); the whole name only breaks a tie. */
    private fun titleLang(title: String, whole: String): String {
        val toks = title.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        val fr = toks.count { it in FR_WORDS } + (if (title.any { it in "éèêàùçôîâûë" }) 1 else 0)
        val en = toks.count { it in EN_WORDS }
        return if (fr == 0 && en == 0) nameLang(whole) else if (en > fr) "en" else "fr"
    }

    private fun nameLang(text: String): String {
        val toks = text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        var fr = toks.count { it in FR_WORDS }
        val en = toks.count { it in EN_WORDS }
        if (text.any { it in "éèêàùçôîâûë" }) fr += 1
        return if (en > fr) "en" else "fr"
    }

    // ------------------------------------------------------------------ entry point

    /**
     * Parses [fileName]. [folder] is the parent folder when known (phone): "Prison Break/Saison 1" helps to name "Episode 04.mkv".
     * [durationMs] (0 = unknown) only breaks ties (a 4-minute video with "Artist - Title" is a clip, not a film).
     */
    fun parse(fileName: String, folder: String = "", durationMs: Long = 0, currentYear: Int = java.time.LocalDate.now().year): Parsed {
        val (stem00, ext) = splitExt(fileName)
        val media = mediaOf(ext)
        personal(stem00, media, ext)?.let { return it.copy(stem = stem00) }
        val (stem0, subSuffix) = if (media == Media.SUBTITLE) SubtitleTags.peel(stem00) else stem00 to null

        val (stripped0, junk0, copy) = stripJunk(stem0)
        val stripped = if (media == Media.AUDIO && stripped0.contains('_') && !stripped0.contains(' ') && stripped0.count { it == '-' } == 1) stripped0.replace("-", " - ") else stripped0
        val hasGroupPrefix = Regex("^\\s*\\[[^\\]]+\\]").containsMatchIn(stem0)
        val clipHint = CLIP_WORDS.containsMatchIn(stem0)
        var junk = junk0
        val rawTags = rawTags(stem0)

        return when (media) {
            Media.APP, Media.ARCHIVE -> {
                val t = stripped.replace(Regex("\\s{2,}"), " ").trim().trim('-', '–', '_', ' ')
                val kind = if (media == Media.APP) Kind.APP else Kind.ARCHIVE
                Parsed(media, kind, ext, title = t, confidence = 0.6, rule = "file." + kind.name.lowercase(), copy = copy, hadJunk = junk, stem = t, nameLang = nameLang(t))
            }
            Media.IMAGE, Media.DOC, Media.OTHER -> lightParse(stripped, media, ext, folder, junk, copy)
            else -> mediaParse(stripped, media, ext, folder, durationMs, currentYear, hasGroupPrefix, clipHint, junk, copy, stem0, rawTags, subSuffix)
        }
    }

    private fun lightParse(stripped: String, media: Media, ext: String, folder: String, junk: Boolean, copy: Boolean): Parsed {
        var s = stripped.trim()
        // Unknown file types (exe, dmg…) keep their separators: a program name is not prose.
        if (media != Media.OTHER) { if (!s.contains(' ') && (s.count { it == '.' } >= 2 || s.contains('_'))) s = normalizeSeparators(s) else s = s.replace('_', ' ') }
        s = s.replace(Regex("\\s+[–—]\\s+"), " - ").replace(Regex("\\s{2,}"), " ").trim().trim('-', '–', ' ', '.')
        val lang = nameLang(s)
        val text = if (s.contains(' ')) Text.titleCaseIfNeeded(s, lang) else s
        val subject = subjectOf(s + " " + folder)
        val course = media == Media.DOC && (COURSE_STRONG.containsMatchIn(s) || COURSE_WEAK.containsMatchIn(s) && subject != null)
        return if (course) Parsed(media, Kind.COURSE, ext, title = text, subject = subject, nameLang = lang, confidence = 0.7, rule = "course.doc", copy = copy, hadJunk = junk, stem = text)
        else Parsed(media, when (media) { Media.IMAGE -> Kind.PHOTO; Media.DOC -> Kind.DOCUMENT; else -> Kind.UNKNOWN }, ext, title = text, nameLang = lang,
            confidence = if (media == Media.OTHER) 0.3 else 0.6, rule = "file." + media.name.lowercase(), copy = copy, hadJunk = junk, stem = text)
    }

    private fun subjectOf(text: String): String? = SUBJECTS.firstOrNull { it.first.containsMatchIn(text) }?.second

    private fun isYear(t: String, currentYear: Int) = RX_YEAR.matches(t) && t.toInt() in 1900..currentYear + 1

    private val RX_TRAILING_INITIALS = Regex("(?<![\\p{L}\\p{N}])(?:\\p{L}\\.){2,}$")
    private fun tidy(s: String): String {
        val t = s.replace(Regex("^[\\s\\-–—|:.,_#]+|[\\s\\-–—|:.,_#]+$"), "").replace(Regex("\\s{2,}"), " ")
        // "S.W.A.T." loses its last dot above: put it back when the text ended with initials
        return if (RX_TRAILING_INITIALS.containsMatchIn(s.trimEnd()) && !t.endsWith(".")) "$t." else t
    }
    /** A bracket opened right before a marker, or closed right after it ("Title [1x04] Name"): not part of the title. */
    private val RX_GLUED_MARKER = Regex("(?<=[a-z])(?=S\\d{1,2}E\\d{1,3}(?![\\p{L}\\p{N}]))")
    private val RX_CAMEL_WORD = Regex("^[A-Z][\\p{Ll}\\p{N}']*(?:-?[A-Z][\\p{Ll}\\p{N}']*)+$")
    private val RX_CAMEL_CUT = Regex("(?<=[\\p{Ll}\\p{N}])(?=\\p{Lu})")
    /** « PrisonBreak » is « Prison Break »: one token made of capitalised words (at least two) in front of a series marker. */
    private fun unglue(title: String): String {
        val t = title.trim()
        return if (!t.contains(' ') && RX_CAMEL_WORD.matches(t)) RX_CAMEL_CUT.replace(t, " ") else title
    }
    private fun dropOpenTail(s: String) = s.trimEnd().trimEnd('[', '(', '{', ' ', '-', '–')
    private fun dropCloseHead(s: String) = s.trimStart().trimStart(']', ')', '}', ' ')

    private fun mediaParse(stripped: String, media: Media, ext: String, folder: String, durationMs: Long, currentYear: Int,
                           hasGroupPrefix: Boolean, clipHint: Boolean, junk0: Boolean, copy: Boolean, rawStem: String, rawTags: Pair<Int?, Audio?>, subSuffix: String? = null): Parsed {
        var junk = junk0
        val text = normalizeSeparators(stripped).let { t -> if (!t.contains(' ') && t.count { it == '-' } >= 2) t.replace('-', ' ') else t }
            .replace(RX_GLUED_MARKER, " ")   // « PrisonBreakS01E08 »
        val lang0 = nameLang(text)
        var lang = lang0
        val subLangCode = if (media == Media.SUBTITLE && subSuffix == null) subtitleLang(text) else null
        val (textNoLang, subLang) = when {
            subSuffix != null -> text to subSuffix
            subLangCode != null -> text.replace(ur("\\s(?:" + Regex.escape(subLangCode.first) + ")$"), "") to subLangCode.second
            else -> text to null
        }

        // ---- gather tokens and tags (resolution, language version) over the whole text
        val allTokens = textNoLang.split(' ').filter { it.isNotEmpty() }
        var resolution: Int? = rawTags.first
        var audio: Audio? = rawTags.second
        for ((i, t) in allTokens.withIndex()) {
            val l = t.lowercase()
            RX_RES_TOKEN.matchEntire(l)?.let { if (resolution == null) resolution = it.groupValues[1].toInt() }
            RX_DIMS.matchEntire(l)?.let { if (resolution == null) resolution = it.groupValues[2].toInt() }
            if (l == "4k" || l == "uhd") resolution = 2160
            if (l == "fhd") resolution = 1080
            if (audio == null && !multiSubs(l, allTokens.getOrNull(i + 1))) LANG_STRONG[l]?.let { audio = it }
            if (audio == null && tagKind(t) == 2) {
                val next = allTokens.getOrNull(i + 1)
                if (LANG_WEAK[l] != null && i > 0 && (next == null || tagKind(next) > 0)) audio = LANG_WEAK[l]
            }
        }

        // ---- series
        val marker = findMarker(textNoLang, hasGroupPrefix || junk0) { end -> textNoLang.substring(end + 1).split(' ').any { isYear(it, currentYear) } }
        if (marker != null && media != Media.AUDIO) {
            val before = unglue(dropOpenTail(textNoLang.substring(0, marker.range.first)))
            val after = dropCloseHead(if (marker.range.last + 1 <= textNoLang.length) textNoLang.substring(marker.range.last + 1) else "")
            lang = titleLang(before, lang0)
            var title = cutAtTags(tidy(before), currentYear)
            // "The Flash 2014 S02E03": a year right before the marker belongs to the series name
            var year: Int? = null
            val tt = title.split(' ').toMutableList()
            if (tt.size > 1 && isYear(tt.last(), currentYear)) { year = tt.removeLast().toInt(); title = tt.joinToString(" ") }
            var season = marker.season
            var folderTitle = false
            if (title.isBlank() && folder.isNotBlank()) {
                val ft = seriesFromFolder(folder)
                if (ft.first != null) { title = ft.first!!; folderTitle = true }
                if (season == null) season = ft.second
            }
            if (season == null && marker.episode != null && folder.isNotBlank()) season = seriesFromFolder(folder).second
            val epTitle = episodeTitle(after, currentYear)
            val t = Text.titleCaseIfNeeded(title, lang)
            val conf = when {
                t.isBlank() -> 0.35
                folderTitle -> 0.8
                marker.rule == "series.episode-only" && season == null -> 0.7
                marker.rule == "series.season-only" -> 0.75
                marker.rule == "series.anime" -> 0.75
                else -> 0.95
            }
            return Parsed(if (media == Media.SUBTITLE) Media.SUBTITLE else media, Kind.SERIES, ext, title = t, year = year, season = season, episode = marker.episode, episodeEnd = marker.episodeEnd,
                episodeTitle = epTitle?.let { Text.titleCaseIfNeeded(it, lang) }, resolution = resolution, audio = audio, subLang = subLang, nameLang = lang, confidence = conf,
                rule = marker.rule, copy = copy, hadJunk = junk, stem = Text.titleCaseIfNeeded(tidy(before), lang))
        }

        // ---- "04.mkv" inside "Show/Saison 2": the folder says which show and which season, the file only its number
        if (marker == null && media != Media.AUDIO && folder.isNotBlank()) {
            val bare = RX_BARE_EPISODE.matchEntire(textNoLang.trim())
            val ft = if (bare != null) seriesFromFolder(folder) else null
            if (bare != null && ft?.first != null && ft.second != null) {
                val l = titleLang(ft.first!!, lang0)
                return Parsed(media, Kind.SERIES, ext, title = ft.first!!, season = ft.second, episode = bare.groupValues[1].toInt(), nameLang = l, confidence = 0.8,
                    rule = "series.folder-number", copy = copy, hadJunk = junk, stem = ft.first!!)
            }
        }

        // ---- "Prison Break - 04 - Cut Off.mkv" in "Prison Break/Saison 2": the folder confirms the show, so the bare number is an episode
        if (marker == null && media != Media.AUDIO && folder.isNotBlank()) {
            val ft = seriesFromFolder(folder)
            val m = RX_TITLE_NUMBER.matchEntire(textNoLang.trim())
            if (m != null && ft.first != null && ft.second != null && Text.key(m.groupValues[1]) == Text.key(ft.first!!)) {
                val l = titleLang(ft.first!!, lang0)
                return Parsed(media, Kind.SERIES, ext, title = ft.first!!, season = ft.second, episode = m.groupValues[2].toInt(),
                    episodeTitle = episodeTitle(m.groupValues[3], currentYear)?.let { Text.titleCaseIfNeeded(it, l) }, subLang = subLang, nameLang = l, confidence = 0.85,
                    rule = "series.folder-title-number", copy = copy, hadJunk = junk, stem = ft.first!!)
            }
        }

        // ---- audio files: music (or a course recorded as audio)
        if (media == Media.AUDIO) return musicParse(textNoLang, ext, lang, clipHint, copy, junk, Kind.MUSIC, folder)

        // ---- tokens before the first tag; the release year
        val tokens = textNoLang.split(' ').filter { it.isNotEmpty() }
        val tagIdx = cutIndex(tokens, currentYear)
        val yearIdxs = (0 until tagIdx).filter { isYear(tokens[it], currentYear) }
        var yearIdx = yearIdxs.lastOrNull()
        if (yearIdx == 0) yearIdx = null                                   // "2012 1080p": the year is the title
        val hasTagsAfter = tagIdx < tokens.size || tokens.any { tagKind(it) > 0 }
        val hasStrongTags = tokens.any { tagKind(it) == 1 && !it.equals("mkv", true) }
        val titleEnd = yearIdx ?: tagIdx
        val rawTitle = tidy(tokens.subList(0, titleEnd).joinToString(" "))
        lang = titleLang(rawTitle, lang0)
        val cutText = Text.titleCaseIfNeeded(rawTitle, lang)
        if (tagIdx < tokens.size || yearIdx != null) junk = true

        // ---- course
        val courseStrong = COURSE_STRONG.containsMatchIn(textNoLang) || COURSE_STRONG.containsMatchIn(folder)
        val courseWeak = COURSE_WEAK.containsMatchIn(textNoLang)
        if ((courseStrong || courseWeak && yearIdx == null && !hasStrongTags) && yearIdx == null && !hasStrongTags) {
            val ttl = Text.titleCaseIfNeeded(tidy(tokens.subList(0, tagIdx).joinToString(" ")), lang)
            return Parsed(media, Kind.COURSE, ext, title = ttl, subject = subjectOf(textNoLang + " " + folder), nameLang = lang, resolution = resolution, audio = audio, subLang = subLang,
                confidence = if (courseStrong) 0.75 else 0.55, rule = "course", copy = copy, hadJunk = junk, stem = ttl)
        }

        // ---- clip (music video): explicit words win over anything; otherwise "Artist - Title" of a short video
        val segs = splitSegments(tidy(tokens.subList(0, tagIdx).joinToString(" ")))
        val numberedTail = segs.size >= 2 && segs.last().length <= 4 && segs.last().all { it.isDigit() }   // « One Piece - 1045 »: a number, not a song title
        val shortVideo = durationMs in 1..(11 * 60_000L)
        if (clipHint || yearIdx == null && !hasStrongTags && segs.size >= 2 && !numberedTail && (shortVideo || durationMs == 0L && segs.size == 2)) {
            return musicParse(textNoLang, ext, lang, clipHint, copy, junk, Kind.CLIP, folder).copy(media = media)
        }

        // ---- family events: "Mariage Jean & Sophie 2022" is not a film
        if (!hasStrongTags && PERSONAL_WORDS.containsMatchIn(textNoLang)) {
            val t = tidy(tokens.joinToString(" "))
            return Parsed(media, Kind.PERSONAL, ext, title = t, nameLang = lang, confidence = 0.7, rule = "personal.event", copy = copy, hadJunk = junk, stem = t)
        }

        // ---- movie
        if (yearIdx != null && cutText.isNotBlank()) {
            val y = tokens[yearIdx].toInt()
            val short = durationMs in 1..(30 * 60_000L)
            val conf = when { hasStrongTags -> 0.92; short -> 0.5; else -> 0.8 }
            return Parsed(media, Kind.MOVIE, ext, title = cutText, year = y, resolution = resolution, audio = audio, subLang = subLang, nameLang = lang, confidence = conf,
                rule = "movie.year", copy = copy, hadJunk = junk, stem = cutText)
        }
        if (hasStrongTags && cutText.isNotBlank() && tokens.any { tagKind(it) == 1 && tokens.indexOf(it) > 0 }) {
            val short = durationMs in 1..(20 * 60_000L)
            return Parsed(media, Kind.MOVIE, ext, title = cutText, resolution = resolution, audio = audio, subLang = subLang, nameLang = lang, confidence = if (short) 0.45 else 0.7,
                rule = "movie.tags", copy = copy, hadJunk = true, stem = cutText)
        }

        // ---- nothing tells what it is: keep the name, only cleaned of separators (never re-cased: we do not know what it is)
        val codeLike = tokens.any { t -> t.count { it.isDigit() } >= 6 }
        val plain = if (codeLike) rawStem else tidy(tokens.joinToString(" "))
        return Parsed(media, Kind.UNKNOWN, ext, title = plain, resolution = resolution, audio = audio, subLang = subLang, nameLang = lang, confidence = if (codeLike) 0.2 else 0.3,
            rule = "unknown.video", copy = copy, hadJunk = junk, stem = plain)
    }

    /** "MULTi SUBS" means several subtitle languages, not a multi-language audio track. */
    private fun multiSubs(token: String, next: String?) = token.startsWith("multi") && next != null && next.lowercase().startsWith("sub")

    /** Resolution and language version written anywhere in the raw name, even inside brackets that cleaning removes ("[VOSTFR]"). */
    private fun rawTags(raw: String): Pair<Int?, Audio?> {
        var res: Int? = null
        var audio: Audio? = null
        val rawToks = raw.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        for ((i, t) in rawToks.withIndex()) {
            val l = t.lowercase()
            RX_RES_TOKEN.matchEntire(l)?.let { if (res == null && it.groupValues[1].toInt() in 240..4320) res = it.groupValues[1].toInt() }
            if (l == "4k" || l == "uhd") res = 2160
            if (audio == null && !multiSubs(l, rawToks.getOrNull(i + 1))) LANG_STRONG[l]?.let { audio = it }
        }
        return res to audio
    }

    private fun subtitleLang(text: String): Pair<String, String>? {
        val last = text.split(' ').lastOrNull()?.lowercase() ?: return null
        val code = when (last) {
            "fr", "fre", "fra", "french", "francais", "français" -> "fr"
            "en", "eng", "english" -> "en"
            "es", "spa", "spanish" -> "es"
            "ar", "ara", "arabic" -> "ar"
            "pt", "por" -> "pt"
            else -> return null
        }
        return last to code
    }

    private fun cutAtTags(title: String, currentYear: Int): String {
        val toks = title.split(' ').filter { it.isNotEmpty() }
        return toks.subList(0, cutIndex(toks, currentYear)).joinToString(" ")
    }

    private fun episodeTitle(after: String, currentYear: Int): String? {
        val toks = tidy(after).split(' ').filter { it.isNotEmpty() }
        var end = toks.size
        for (i in toks.indices) {
            val k = tagKind(toks[i])
            if (k == 1 || isYear(toks[i], currentYear) || k == 2 && weakIsTag(toks, i, currentYear)) { end = i; break }
        }
        val t = tidy(toks.subList(0, end).joinToString(" "))
        if (t.isBlank() || t.all { !it.isLetter() } || t.split(' ').size > 9) return null
        if (t.lowercase().matches(Regex("(?:complete|integrale|intégrale|vostfr|vf)"))) return null
        return t
    }

    private val RX_FOLDER_SEASON = ur("^(?:(?:$SEASON_W)\\s*0*$NUM|s\\s*0*(\\d{1,2}))$")
    private val RX_FOLDER_SPECIALS = ur("^(?:specials?|sp[ée]ciaux|speciali|extras?|bonus)$")

    /** The season a folder name stands for (« Saison 02 », « Temporada 2 », « S3 », « Saison II », « Specials » = 0), or null. */
    private fun folderSeason(name: String): Int? {
        val n = name.trim()
        if (RX_FOLDER_SPECIALS.matches(n)) return 0
        return RX_FOLDER_SEASON.matchEntire(n)?.let { m -> firstNumber(m) }
    }

    /** "Prison Break/Saison 1" -> ("Prison Break", 1); "Saison 02" -> (null, 2). */
    private fun seriesFromFolder(folder: String): Pair<String?, Int?> {
        val parts = folder.split('/', '\\').filter { it.isNotBlank() }
        var season: Int? = null
        var title: String? = null
        for (p in parts.asReversed()) {
            val sn = folderSeason(p)
            if (sn != null && season == null) { season = sn; continue }
            if (title == null && p.trim().isNotEmpty() && !p.equals("Séries", true) && !p.equals("Series", true) && !p.equals("Downloads", true) && !p.equals("Download", true)) { title = Text.titleCaseIfNeeded(tidy(normalizeSeparators(stripJunk(p).first)), nameLang(p)) }
        }
        return title to season
    }

    private fun splitSegments(s: String): List<String> = s.split(Regex("\\s+-\\s+|\\s*[–—]\\s*")).map { it.trim() }.filter { it.isNotEmpty() }

    private val RX_TRACK = Regex("^(?:track\\s*)?(\\d{1,3})\\s*[-.)]*\\s+(?=\\S)", RegexOption.IGNORE_CASE)
    /** Vinyl / tape side and number: "A1 Artist - Title". Only when the rest still has an "Artist - Title" shape. */
    private val RX_SIDE_TRACK = Regex("^[A-D](\\d{1,2})\\s+(?=\\S.*\\s-\\s)")
    private val RX_FEAT = ur("\\b(?:feat|ft|featuring)\\.?\\s+")

    private fun musicParse(text: String, ext: String, lang: String, clipHint: Boolean, copy: Boolean, junk: Boolean, kind: Kind, folder: String): Parsed {
        var toks = text.split(' ').filter { it.isNotEmpty() }
        // cut at the first strong tag (bitrate, flac…), keep years and words
        val end = toks.indices.firstOrNull { it > 0 && tagKind(toks[it]) == 1 } ?: toks.size
        toks = toks.subList(0, end)
        var s = tidy(toks.joinToString(" "))
        s = s.replace(RX_FEAT, "feat. ")
        var track: Int? = null
        RX_TRACK.find(s)?.let { m ->
            val rest = s.substring(m.range.last + 1)
            if (rest.isNotBlank()) { track = m.groupValues[1].toInt(); s = rest.trim() }
        }
        if (track == null) RX_SIDE_TRACK.find(s)?.let { m -> track = m.groupValues[1].toInt(); s = s.substring(m.range.last + 1).trim() }
        val segs = splitSegments(s)
        val artist: String?
        val title: String
        if (segs.size >= 2) { artist = segs[0]; title = segs.drop(1).joinToString(" – ") } else { artist = null; title = s }
        val subject = subjectOf(text + " " + folder)
        if (kind == Kind.MUSIC && (COURSE_STRONG.containsMatchIn(text) && artist == null || COURSE_STRONG.containsMatchIn(text) && subject != null)) {
            val ttl = Text.titleCaseIfNeeded(s, lang)
            return Parsed(Media.AUDIO, Kind.COURSE, ext, title = ttl, subject = subject, nameLang = lang, confidence = 0.65, rule = "course.audio", copy = copy, hadJunk = junk, stem = ttl)
        }
        val a = artist?.let { Text.titleCaseIfNeeded(it, lang) }
        val t = Text.titleCaseIfNeeded(title, lang)
        val conf = when {
            artist != null && kind == Kind.CLIP -> 0.8
            artist != null -> 0.85
            kind == Kind.CLIP -> 0.65
            else -> 0.6
        }
        return Parsed(if (kind == Kind.CLIP) Media.VIDEO else Media.AUDIO, kind, ext, title = t, artist = a, track = track, nameLang = lang, confidence = conf,
            rule = if (kind == Kind.CLIP) "clip" else "music", copy = copy, hadJunk = junk || end < text.split(' ').size, stem = Text.titleCaseIfNeeded(s, lang))
    }
}
