package castbridge.core.library.agent

import java.text.Normalizer
import kotlin.random.Random

/**
 * The "hard" shapes: unusual numbering, noisy spacing, decomposed accents, upper-case extensions, odd quality tags, titles made of digits,
 * different phone naming schemes. Same rule as [CorpusGen]: the expectation comes from the TRUE metadata, never from the parser.
 */
class CorpusGenHard(seed: Long, private val pools: Pools) {
    private val r = Random(seed)
    private fun <T> pick(l: List<T>) = l[r.nextInt(l.size)]
    private fun chance(p: Double) = r.nextDouble() < p
    private fun pad(n: Int, w: Int = 2) = n.toString().padStart(w, '0')
    private val vexts = listOf("mkv", "mp4", "avi", "MKV", "Mp4", "mp4", "mkv")
    private val oddTags = listOf("WEB DL", "Blu-Ray", "H 264", "HDTV-LOL", "AAC2.0", "DD+5.1", "10bit", "REPACK", "PROPER", "iNTERNAL", "WEB.DL", "x264-mSD", "HEVC-PSA", "BDRip", "480p", "2160p", "4K", "HDR")
    private val langs = listOf("" to "", "" to "", "VOSTFR" to " [VOSTFR]", "FRENCH" to "", "MULTi" to " [MULTI]", "TRUEFRENCH" to "", "VF" to "")

    private val STRONG = setOf("WEB DL", "Blu-Ray", "H 264", "HDTV-LOL", "AAC2.0", "DD+5.1", "10bit", "WEB.DL", "x264-mSD", "HEVC-PSA", "BDRip", "480p", "2160p", "4K", "HDR")
    private fun nfd(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)

    fun series(): GCase {
        val title = pick(pools.series + pools.seriesFr)
        val sn = if (chance(0.6)) r.nextInt(1, 10) else r.nextInt(1, 30)
        val ep = if (chance(0.7)) r.nextInt(1, 25) else r.nextInt(1, 400)
        val s2 = pad(sn); val e2 = pad(ep)
        val forms = listOf(
            "S${s2}E${pad(ep, 3)}" to "S${s2}E${e2}", "Saison $sn - $e2" to "S${s2}E${e2}", "Season $sn - $e2" to "S${s2}E${e2}",
            "Season $sn Ep.$ep" to "S${s2}E${e2}", "S$sn Ep $ep" to "S${s2}E${e2}", "Episode $ep - Season $sn" to "S${s2}E${e2}", "Ep $ep Saison $sn" to "S${s2}E${e2}",
            "s${sn}e${ep}" to "S${s2}E${e2}", "S${s2}.E${e2}" to "S${s2}E${e2}", "S${sn}xE${ep}" to "S${s2}E${e2}", "S${s2}E${e2}" to "S${s2}E${e2}",
            "${sn}x${e2}-${pad(ep + 1)}" to "S${s2}E${e2}-E${pad(ep + 1)}", "S${s2}E${e2}E${pad(ep + 1)}E${pad(ep + 2)}" to "S${s2}E${e2}-E${pad(ep + 2)}",
        )
        val (m, exp) = pick(forms)
        val sep = pick(listOf(" ", ".", "_", " "))
        val lang = pick(langs)
        val parts = ArrayList<String>()
        var t = title
        if (chance(0.3) && title.any { it in "éèêàùçôîâû" }) t = nfd(title)
        parts += t.replace(' ', sep[0])
        parts += m.replace(' ', sep[0])
        if (lang.first.isNotEmpty()) parts += lang.first
        repeat(r.nextInt(0, 4)) { parts += pick(oddTags).replace(' ', if (chance(0.5)) ' ' else sep[0]) }
        var base = parts.joinToString(sep)
        if (chance(0.25)) base = "  $base ".replace(" ", if (chance(0.5)) "  " else " ")
        if (chance(0.15)) base = base.trim() + " "
        val ext = pick(vexts)
        val name = "${Normalizer.normalize(title, Normalizer.Form.NFC)}${Namer.SEP}$exp${lang.second}.${ext.lowercase()}"
        return GCase("hard-series", Case("${base.trim()}.$ext", name, "Séries/${title.trimEnd('.')}/Saison ${pad(sn)}", Kind.SERIES))
    }

    fun digitsSeries(): GCase {
        val title = pick(listOf("24", "9-1-1", "The 100", "Station 19", "S.W.A.T.", "Law and Order SVU", "NCIS Los Angeles", "Ms Marvel", "Mr Inbetween", "Marvel's Agents of S.H.I.E.L.D."))
        val sn = r.nextInt(1, 9); val ep = r.nextInt(1, 25)
        val sep = pick(listOf(".", " ", "_"))
        val ext = pick(vexts)
        val base = title.replace(' ', sep[0]) + sep + "S${pad(sn)}E${pad(ep)}" + sep + pick(listOf("720p", "1080p.WEB.H264", "HDTV.x264", "FRENCH.720p"))
        return GCase("hard-digits", Case("$base.$ext", "$title${Namer.SEP}S${pad(sn)}E${pad(ep)}.${ext.lowercase()}", "Séries/${title.trimEnd('.')}/Saison ${pad(sn)}", Kind.SERIES))
    }

    fun movie(): GCase {
        val fr = chance(0.3)
        val (title, years) = pick(if (fr) pools.moviesFr else pools.movies)
        val year = r.nextInt(years.first, years.last + 1)
        val numbered = pick(listOf("2001 A Space Odyssey" to 1968, "300" to 2006, "1984" to 1984, "Apollo 13" to 1995, "Ocean's 8" to 2018, "Taxi 3" to 2003, "Rocky 3" to 1982, "X-Men Days of Future Past" to 2014, "Scream 4" to 2011, "Fast and Furious 6" to 2013))
        val useNum = chance(0.4)
        val (t, y) = if (useNum) numbered else title to year
        val sep = pick(listOf(".", " ", "_"))
        val noYear = chance(0.1) && !useNum
        val picked = (0 until r.nextInt(1, 4)).map { pick(oddTags) }.toMutableList()
        if (noYear && picked.none { it in STRONG }) picked += "1080p"      // a name with neither a year nor a quality tag is not recognisable as a film
        val tags = picked.joinToString(sep) { it.replace(' ', sep[0]) }
        val parts = ArrayList<String>()
        parts += t.replace(' ', sep[0])
        if (!noYear) parts += pick(listOf("$y", "($y)", "[$y]", "- $y"))
        parts += tags
        val base = parts.joinToString(sep)
        val ext = pick(vexts)
        val cap = if (noYear) t else "$t ($y)"
        return GCase("hard-movie", Case("$base.$ext", "$cap.${ext.lowercase()}", "Films/$cap", Kind.MOVIE))
    }

    fun music(): GCase {
        val a = pick(pools.artists); val b = pick(pools.guests.filter { it != a }); val song = pick(pools.songs)
        val join = pick(listOf(" & ", " x ", " et ", ", "))
        val ext = pick(listOf("mp3", "MP3", "m4a", "flac"))
        val pre = pick(listOf("", "", "01 - ", "A1 ", "[320kbps] ", "Track 05 - "))
        val base = "$pre$a$join$b - $song"
        val shown = a + join + b
        return GCase("hard-music", Case("$base.$ext", "$shown${Namer.SEP}$song.${ext.lowercase()}", "Musique", Kind.MUSIC))
    }

    fun personal(): GCase {
        val y = r.nextInt(2019, 2026); val mo = r.nextInt(1, 13); val d = r.nextInt(1, 29)
        val hh = r.nextInt(0, 24); val mi = r.nextInt(0, 60); val ss = r.nextInt(0, 60)
        val date = "$y-${pad(mo)}-${pad(d)}"; val hm = "${pad(hh)}h${pad(mi)}"; val ymd = "$y${pad(mo)}${pad(d)}"
        return when (r.nextInt(9)) {
            0 -> GCase("hard-personal", Case("IMG_${ymd}_${pad(hh)}${pad(mi)}${pad(ss)}_1.jpg", "Photo – $date $hm.jpg", "Famille", Kind.PHOTO))
            1 -> GCase("hard-personal", Case("${ymd}_${pad(hh)}${pad(mi)}${pad(ss)}_HDR.jpg", "Photo – $date $hm.jpg", "Famille", Kind.PHOTO))
            2 -> GCase("hard-personal", Case("Screenshot_$date-${pad(hh)}-${pad(mi)}-${pad(ss)}.png", "Capture d'écran – $date $hm.png", "Captures", Kind.PHOTO))
            3 -> GCase("hard-personal", Case("Screenshot $date at ${pad(hh)}.${pad(mi)}.${pad(ss)}.png", "Capture d'écran – $date $hm.png", "Captures", Kind.PHOTO))
            4 -> GCase("hard-personal", Case("Capture d’écran $date à ${pad(hh)}.${pad(mi)}.${pad(ss)}.png", "Capture d'écran – $date $hm.png", "Captures", Kind.PHOTO))
            5 -> { val n = r.nextInt(1, 999); GCase("hard-personal", Case("VID-$ymd-WA${pad(n, 4)}(1).mp4", "Vidéo WhatsApp – $date ($n).mp4", "Famille", Kind.PERSONAL)) }
            6 -> GCase("hard-personal", Case("WhatsApp Video $date at ${pad(hh)}.${pad(mi)}.${pad(ss)}.mp4", "Vidéo WhatsApp – $date $hm.mp4", "Famille", Kind.PERSONAL))
            7 -> GCase("hard-personal", Case("VID_${ymd}_${pad(hh)}${pad(mi)}${pad(ss)}_${r.nextInt(1, 9)}.mp4", "Vidéo – $date $hm.mp4", "Famille", Kind.PERSONAL))
            else -> GCase("hard-personal", Case("PXL_${ymd}_${pad(hh)}${pad(mi)}${pad(ss)}${r.nextInt(100, 999)}.MP.jpg", "Photo – $date $hm.jpg", "Famille", Kind.PHOTO))
        }
    }

    fun generate(n: Int): List<GCase> {
        val out = LinkedHashMap<String, GCase>()
        var guard = 0
        while (out.size < n && guard++ < n * 30) {
            val x = r.nextInt(100)
            val g = when { x < 45 -> series(); x < 55 -> digitsSeries(); x < 75 -> movie(); x < 88 -> music(); else -> personal() }
            out.putIfAbsent(g.c.input, g)
        }
        return out.values.toList()
    }
}
