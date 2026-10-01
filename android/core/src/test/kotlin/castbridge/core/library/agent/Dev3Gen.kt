package castbridge.core.library.agent

import kotlin.random.Random

/** Title pools of DEV-3 and GELÉ-3: disjoint from each other and from the older pools (checked by [Dev3CorpusTest]). */
object Pools3 {
    val DEV = Pools(
        series = listOf("Mythic Quest", "Sons of Anarchy", "Boardwalk Empire", "True Detective", "The Leftovers", "Halt and Catch Fire", "The Expanse", "Altered Carbon",
            "Locke and Key", "Sense8", "Monk", "Castle", "Bones", "Psych", "Burn Notice", "Spartacus", "Deadwood", "The Wire", "Veep", "Barry", "Atlanta", "Insecure", "Ramy",
            "Fleabag", "Pose", "Legacies", "Titans", "Doom Patrol", "Snowpiercer", "Dollface"),
        seriesFr = listOf("Les Rois du Bétail", "Mama Africa", "Le Village des Ombres", "Cousins de Douala", "Les Chroniques de Bonanjo", "Papa Lion", "Tontine", "La Case de Tantine"),
        anime = listOf("Blue Lock", "Spy x Family", "Vinland Saga", "Chainsaw Man", "Dr Stone", "Mob Psycho 100", "Kaguya Sama", "Oshi no Ko"),
        epTitles = listOf("Pilot", "Homecoming", "The Fall", "Reckoning", "Le Pacte", "Au Bord du Gouffre", "Endgame", "Les Adieux"),
        movies = listOf("Memento" to 2000..2000, "Zodiac" to 2007..2007, "Prisoners" to 2013..2013, "Sicario" to 2015..2015, "Arrival" to 2016..2016, "Drive" to 2011..2011,
            "Collateral" to 2004..2004, "Looper" to 2012..2012, "Moon" to 2009..2009, "Ex Machina" to 2014..2014, "Her" to 2013..2013, "Gravity" to 2013..2013, "Birdman" to 2014..2014,
            "Spotlight" to 2015..2015, "Argo" to 2012..2012, "Fences" to 2016..2016, "Moonlight" to 2016..2016, "Roma" to 2018..2018, "Ford v Ferrari" to 2019..2019,
            "Knives Out" to 2019..2019, "Mank" to 2020..2020, "Nomadland" to 2020..2020, "Minari" to 2020..2020, "Tar" to 2022..2022),
        moviesFr = listOf("Le Pacte des Loups" to 2001..2001, "Un Long Dimanche de Fiançailles" to 2004..2004, "La Môme" to 2007..2007, "Mesrine" to 2008..2008,
            "Les Petits Mouchoirs" to 2010..2010, "Polisse" to 2011..2011, "Rafiki" to 2018..2018, "Atlantique" to 2019..2019),
        artists = listOf("Tayc", "Gradur", "Dadi Freeman", "Fabregas", "Nasty C", "Focalistic", "Kizz Daniel", "Lojay", "Bnxn", "Ruger", "Libianca", "Blanche Bailly"),
        songs = listOf("Dodo", "Kpo Kpo", "Buga", "Monalisa", "Sensational", "People", "Overloading", "Ginseng", "Soweto Baby", "Water", "Nobody", "Stand Strong"),
        guests = listOf("Tems", "Phyno", "Olamide", "Asake", "Fally Ipupa", "Sauti Sol"),
    )

    val FROZEN = Pools(
        series = listOf("Severance", "Slow Horses", "Andor", "Foundation", "Silo", "Reacher", "Tulsa King", "Dopesick", "Pachinko", "Dahmer", "Wednesday", "Sweet Tooth",
            "Raised by Wolves", "Servant", "Ted Lasso", "Shrinking", "Hacks", "Abbott Elementary", "Reservation Dogs", "Poker Face", "Gen V", "Invincible", "Black Bird",
            "The Gilded Age", "Dead to Me", "Away", "Hightown", "Dickinson", "Maid", "Lovecraft Country"),
        seriesFr = listOf("Les Sentinelles", "Le Trône du Désert", "Fils de Roi", "Les Grandes Familles", "Madame Njoya", "Terre d'Espoir", "Rendez-vous à Kribi", "Les Veuves de Bafoussam"),
        anime = listOf("Mushoku Tensei", "Re Zero", "Konosuba", "Mashle", "Bocchi the Rock", "Frieren", "Dandadan", "Solo Leveling"),
        epTitles = listOf("Chapter Two", "The Departure", "Zero Hour", "Les Cendres", "Midnight Sun", "Le Dernier Soir", "Aftershock", "Dust"),
        movies = listOf("Rush" to 2013..2013, "Nightcrawler" to 2014..2014, "Widows" to 2018..2018, "Eternals" to 2021..2021, "Encanto" to 2021..2021, "Coco" to 2017..2017,
            "Moana" to 2016..2016, "Ratatouille" to 2007..2007, "Tangled" to 2010..2010, "Brave" to 2012..2012, "Cars" to 2006..2006, "Ford Road" to 1999..1999,
            "Sully" to 2016..2016, "Hacksaw Ridge" to 2016..2016, "Dunkirk Beach" to 2018..2018, "The Fighter" to 2010..2010, "Warrior" to 2011..2011, "Creed" to 2015..2015,
            "Southpaw" to 2015..2015, "Bumblebee" to 2018..2018, "Greyhound" to 2020..2020, "Oldboy" to 2003..2003, "Memories of Murder" to 2003..2003, "The Wailing" to 2016..2016),
        moviesFr = listOf("La Graine et le Mulet" to 2007..2007, "Entre les Murs" to 2008..2008, "Des Hommes et des Dieux" to 2010..2010, "Timbuktu" to 2014..2014,
            "Mustang" to 2015..2015, "Les Héritiers" to 2014..2014, "Le Sens de la Fête" to 2017..2017, "Hors Normes" to 2019..2019),
        artists = listOf("Gazo Junior", "Tiakola", "Ronisia", "Wendyam", "Josey", "Oxlade", "Victony", "Bien", "Simi", "Adekunle Gold", "Cheb Khaled", "Zaho"),
        songs = listOf("Ye", "Calm", "Imagination", "Soso", "Joha", "Kilometre", "Mood", "Ngozi", "Yoyo", "Jiggy", "Layers", "Ashawo"),
        guests = listOf("Burna", "Adekunle Gold", "Ayra", "Fireboy", "Mr Eazi", "Oxlade"),
    )
}

/**
 * Deterministic generator for the families of docs/NAMING-PATTERNS.md (new marker shapes, languages, dates, parts, noise, subtitles, music, courses),
 * with the expected output computed from the TRUE metadata. DEV-3 uses [Pools3.DEV]; GELÉ-3 uses [Pools3.FROZEN] and another seed.
 */
class Dev3Gen(seed: Long, private val pools: Pools) {
    private val r = Random(seed)
    private fun <T> pick(l: List<T>) = l[r.nextInt(l.size)]
    private fun chance(p: Double) = r.nextDouble() < p
    private fun p2(n: Int) = n.toString().padStart(2, '0')

    private val vext = listOf("mkv", "mp4", "avi")

    private data class Rendered(val text: String, val sn: Int?, val ep: Int, val epEnd: Int? = null)

    /** Marker shapes of the families "épisodes" (numeric forms) and "épisodes multiples". */
    private fun numericMarker(): Rendered {
        val sn = r.nextInt(1, 12); val ep = r.nextInt(1, 25)
        val s2 = p2(sn); val e2 = p2(ep)
        return when (r.nextInt(18)) {
            0 -> Rendered("[S$s2-E$e2]", sn, ep)
            1 -> Rendered("(S${s2}E$e2)", sn, ep)
            2 -> Rendered("[S${s2}E$e2]", sn, ep)
            3 -> Rendered("S$s2-E$e2", sn, ep)
            4 -> Rendered("S${s2}_E$e2", sn, ep)
            5 -> Rendered("S$s2.E$e2", sn, ep)
            6 -> Rendered("S${sn}E$ep", sn, ep)
            7 -> Rendered("S${s2}xE$e2", sn, ep)
            8 -> Rendered("${sn}x$e2", sn, ep)
            9 -> Rendered("[${sn}x$e2]", sn, ep)
            10 -> Rendered("Season $sn Episode $ep", sn, ep)
            11 -> Rendered("S$s2 Ep$e2", sn, ep)
            12 -> Rendered("S${s2}E${e2}E${p2(ep + 1)}", sn, ep, ep + 1)
            13 -> Rendered("S${s2}E$e2-E${p2(ep + 1)}", sn, ep, ep + 1)
            14 -> Rendered("S${s2}E$e2-${p2(ep + 1)}", sn, ep, ep + 1)
            15 -> Rendered("S${s2}E$e2+E${p2(ep + 1)}", sn, ep, ep + 1)
            16 -> Rendered("${sn}x$e2-${p2(ep + 1)}", sn, ep, ep + 1)
            else -> Rendered("s${sn}.e$ep", sn, ep)
        }
    }

    private val roman = listOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX")

    /** "Seasons / episodes in every language": the words of the families "mots dans toutes les langues". */
    private fun langMarker(): Rendered {
        val sn = r.nextInt(1, 9); val ep = r.nextInt(1, 25)
        val text = when (r.nextInt(17)) {
            0 -> "Temporada $sn Capítulo $ep"
            1 -> "Temporada $sn Capitulo $ep"
            2 -> "Temporada $sn Episódio $ep"
            3 -> "Staffel $sn Folge $ep"
            4 -> "Stagione $sn Episodio $ep"
            5 -> "Seizoen $sn Aflevering $ep"
            6 -> "Säsong $sn Avsnitt $ep"
            7 -> "Sezon $sn Odcinek $ep"
            8 -> "Sezon $sn Bölüm $ep"
            9 -> "$sn. Sezon $ep. Bölüm"
            10 -> "Сезон $sn Серия $ep"
            11 -> "$sn сезон $ep серия"
            12 -> "الموسم $sn الحلقة $ep"
            13 -> "第${sn}季第${ep}集"
            14 -> "第${sn}期 第${ep}話"
            15 -> "시즌 $sn ${ep}화"
            else -> "Saison ${roman[sn]} Episode $ep"
        }
        return Rendered(text, sn, ep)
    }

    private val platformTags = listOf("1080p AMZN WEB-DL DDP5.1 H.264", "2160p DSNP WEB-DL DDP5.1 HEVC", "720p HMAX WEB-DL DD5.1 x264", "1080p ATVP WEB-DL DDP5.1", "1080p NF WEB-DL DDP5.1 Atmos x264",
        "1080p 10bit AV1", "720p x265 10bit HEVC", "PROPER 720p HDTV x264", "REPACK 720p WEB", "iNTERNAL 720p HDTV", "1080p PCOK WEB-DL", "")
    private data class L(val raw: String, val tag: String)
    private val langs = listOf(L("", ""), L("", ""), L("VFF", ""), L("VF2", ""), L("SUBFRENCH", " [VOSTFR]"), L("TRUEFRENCH", ""), L("MULTi", " [MULTI]"), L("FRENCH", ""), L("VOSTFR", " [VOSTFR]"))
    private val noiseFront = listOf("", "", "", "", "@canal_films ", "TFPDL - ", "[9jaRocks.com] ", "Waploaded.com - ", "[NetNaija.com] ", "www.o2tvseries.com - ", "t.me_canal_")
    private val noiseBack = listOf("", "", "", "", " @canal_films", " t.me/canal_films", " (Waploaded.com)", " - NetNaija", "[TFPDL]")

    private fun sepIt(parts: String, sep: String) = if (sep == " ") parts else parts.replace(' ', sep[0])

    fun series(): GCase {
        val fr = chance(0.2)
        val title = pick(if (fr) pools.seriesFr else pools.series)
        val wordsForm = chance(0.35)
        val m = if (wordsForm) langMarker() else numericMarker()
        val sep = if (wordsForm && !m.text[0].let { it == '第' } && m.text.contains('第')) " " else pick(listOf(" ", " ", ".", "_"))
        val epTitle = if (chance(0.25)) pick(pools.epTitles) else null
        val l = pick(langs)
        val tag = pick(platformTags)
        val expectedMarker = buildString {
            append("S${p2(m.sn!!)}E${p2(m.ep)}"); m.epEnd?.let { append("-E${p2(it)}") }
        }
        val core = StringBuilder()
        core.append(sepIt(title, sep)).append(sep).append(if (m.text.contains('第') || sep == " ") m.text else sepIt(m.text, sep))
        if (epTitle != null) core.append(sep).append(sepIt(epTitle, sep))
        if (l.raw.isNotEmpty()) core.append(sep).append(l.raw)
        if (tag.isNotEmpty()) core.append(sep).append(sepIt(tag, sep))
        val base = pick(noiseFront) + core + pick(noiseBack)
        val ext = pick(vext)
        val name = "$title${Namer.SEP}$expectedMarker${epTitle?.let { Namer.SEP + it } ?: ""}${l.tag}.$ext"
        return GCase(if (wordsForm) "lang-words" else "ep-forms", Case("$base.$ext", name, "Séries/$title/Saison ${p2(m.sn!!)}", Kind.SERIES))
    }

    /** "101" / "0101": three or four digits, accepted only with series evidence (a series source tag and no year). */
    fun numbered(): GCase {
        val title = pick(pools.series)
        val sn = r.nextInt(1, 10); val ep = r.nextInt(1, 25)
        val digits = if (chance(0.5)) "$sn${p2(ep)}" else "${p2(sn)}${p2(ep)}"
        val tags = pick(listOf("HDTV.XviD", "720p.HDTV.x264", "hdtv-lol", "PDTV.XviD", "WEB-DL.x264", "720p.WEB.h264"))
        val ext = pick(vext)
        return GCase("ep-number", Case("${title.replace(' ', '.')}.$digits.$tags.$ext", "$title${Namer.SEP}S${p2(sn)}E${p2(ep)}.$ext", "Séries/$title/Saison ${p2(sn)}", Kind.SERIES))
    }

    fun camel(): GCase {
        val title = pick(pools.series)
        val sn = r.nextInt(1, 10); val ep = r.nextInt(1, 25)
        val glued = title.split(' ').joinToString("") { w -> w.replaceFirstChar { it.uppercase() } }
        val sep = pick(listOf("", ".", "_"))
        val ext = pick(vext)
        return GCase("sep", Case("$glued$sep" + "S${p2(sn)}E${p2(ep)}.$ext", "$title${Namer.SEP}S${p2(sn)}E${p2(ep)}.$ext", "Séries/$title/Saison ${p2(sn)}", Kind.SERIES))
    }

    fun daily(): GCase {
        val title = pick(pools.series)
        val y = r.nextInt(2010, 2026); val mo = r.nextInt(1, 13); val d = r.nextInt(13, 29)   // day > 12: never ambiguous
        val iso = "$y-${p2(mo)}-${p2(d)}"
        val form = pick(listOf("$y.${p2(mo)}.${p2(d)}", iso, "${p2(d)}-${p2(mo)}-$y", "${p2(d)}.${p2(mo)}.$y", "$y${p2(mo)}${p2(d)}"))
        val ext = pick(vext)
        val sep = pick(listOf(" ", "."))
        return GCase("ep-date", Case("${sepIt(title, sep)}$sep$form.$ext", "$title${Namer.SEP}$iso.$ext", "Séries/$title/Saison $y", Kind.SERIES))
    }

    fun special(): GCase {
        val title = pick(pools.series)
        val n = r.nextInt(1, 9)
        val ext = pick(vext)
        val (txt, ep) = when (r.nextInt(4)) { 0 -> "S00E${p2(n)}" to n; 1 -> "OVA $n" to n; 2 -> "SP${p2(n)}" to n; else -> "S0E$n" to n }
        return GCase("ep-special", Case("$title $txt.$ext", "$title${Namer.SEP}S00E${p2(ep)}.$ext", "Séries/$title/Saison 00", Kind.SERIES))
    }

    fun folderEpisode(): GCase {
        val title = pick(pools.series + pools.seriesFr)
        val sn = r.nextInt(1, 9); val ep = r.nextInt(1, 25)
        val sFolder = pick(listOf("Saison $sn", "Season $sn", "S${p2(sn)}", "Temporada $sn", "Staffel $sn", "Stagione $sn", "Saison ${roman[sn]}", "Season ${p2(sn)}"))
        val file = pick(listOf("Episode $ep", "E${p2(ep)}", "Ep ${p2(ep)}", "#${p2(ep)}", "[${p2(ep)}]", "${p2(ep)}", "Folge $ep", "Capítulo $ep"))
        val ext = pick(vext)
        return GCase("folder", Case("$file.$ext", "$title${Namer.SEP}S${p2(sn)}E${p2(ep)}.$ext", "Séries/$title/Saison ${p2(sn)}", Kind.SERIES, folderIn = "$title/$sFolder"))
    }

    fun anime(): GCase {
        val title = pick(pools.anime)
        val ep = r.nextInt(1, 400)
        val group = pick(listOf("SubsPlease", "Erai-raws", "Anime Land", "Judas", "ToonsHub"))
        val q = pick(listOf("1080p", "720p"))
        val epText = if (chance(0.5)) p2(ep) else ep.toString()
        val extra = pick(listOf("", "", " [A1B2C3D4]", " [Multiple Subtitle]", " [Dual Audio]"))
        val ext = pick(listOf("mkv", "mp4"))
        val ver = if (chance(0.1)) "v2" else ""
        val shown = title.replace("Spy x Family", "Spy x Family")
        val nice = shown.split(' ').joinToString(" ") { w -> if (w == "x") w else w }
        return GCase("ep-anime", Case("[$group] $nice - $epText$ver [$q]$extra.$ext", "$nice${Namer.SEP}E${p2(ep)}.$ext", "Séries/$nice", Kind.SERIES))
    }

    fun movie(): GCase {
        val fr = chance(0.25)
        val (title, years) = pick(if (fr) pools.moviesFr else pools.movies)
        val year = r.nextInt(years.first, years.last + 1)
        val sep = pick(listOf(".", " ", "_", " "))
        val t = sepIt(title, sep)
        val yr = pick(listOf("$year", "($year)", "[$year]"))
        val edition = pick(listOf("", "", "", "Director's Cut", "Directors.Cut", "Extended", "Extended Cut", "Unrated", "Remastered", "IMAX", "3D", "Final Cut", "Theatrical Cut", "Uncut"))
        val tags = pick(listOf("", "1080p BluRay x264", "720p WEB-DL", "2160p Remux HDR", "BDRip x265 10bit", "FRENCH BDRip XviD", "TRUEFRENCH 1080p", "1080p AMZN WEB-DL DDP5.1"))
        val l = pick(langs)
        val part = pick(listOf("", "", "", "", "CD1", "CD2", "Part 1", "Part2", "Pt.2", "Partie 1"))
        val partN = Regex("\\d").find(part)?.value
        val parts = listOf(t, yr, sepIt(edition, sep), l.raw, sepIt(tags, sep), sepIt(part, sep)).filter { it.isNotEmpty() }
        var base = parts.joinToString(sep)
        base = pick(noiseFront.filter { !it.startsWith("t.me") }) + base + pick(noiseBack.take(3))
        val ext = pick(vext)
        val dir = "$title ($year)"
        val partName = if (partN != null && part.isNotEmpty()) " - part$partN" else ""
        return GCase("film-year", Case("$base.$ext", "$dir$partName${l.tag}.$ext", "Films/$dir", Kind.MOVIE))
    }

    fun sequelMovie(): GCase {
        val base = pick(listOf("Toy Story", "Kung Fu Panda", "Shrek", "Ice Age", "Cars", "Taxi", "Rocky", "Creed", "Madagascar"))
        val n = r.nextInt(2, 6)
        val year = r.nextInt(1995, 2024)
        val sep = pick(listOf(".", " "))
        val numForm = pick(listOf("$n", roman[n], "Part $n", "Chapitre $n", "Vol. $n"))
        val title = "$base ${numForm.replace("Vol. ", "Vol ")}"
        val ext = pick(vext)
        return GCase("film-seq", Case("${sepIt("$base $numForm", sep)}$sep$year$sep" + "1080p.$ext", "$title ($year).$ext", "Films/$title ($year)", Kind.MOVIE))
    }

    fun numericTitleMovie(): GCase {
        val (t, y) = pick(listOf("300" to 2006, "1917" to 2019, "2012" to 2009, "21 Jump Street" to 2012, "10 Cloverfield Lane" to 2016, "12 Years a Slave" to 2013, "28 Days Later" to 2002,
            "127 Hours" to 2010, "Se7en" to 1995, "9 to 5" to 1980, "1408" to 2007, "47 Ronin" to 2013, "13 Hours" to 2016, "101 Dalmatians" to 1996, "8 Mile" to 2002, "50 First Dates" to 2004))
        val sep = pick(listOf(".", " "))
        val form = pick(listOf("$y", "($y)", "[$y]"))
        val tags = pick(listOf("", "1080p", "720p BluRay", "WEB-DL x264"))
        val parts = listOf(sepIt(t, sep), form, sepIt(tags, sep)).filter { it.isNotEmpty() }
        val ext = pick(vext)
        return GCase("film-numtitle", Case(parts.joinToString(sep) + ".$ext", "$t ($y).$ext", "Films/$t ($y)", Kind.MOVIE))
    }

    fun subtitle(): GCase {
        val langsOf = listOf("fr" to "fr", "fre" to "fr", "fra" to "fr", "en" to "en", "eng" to "en", "es" to "es", "spa" to "es", "pt-BR" to "pt-BR", "pt" to "pt", "de" to "de", "deu" to "de", "ger" to "de",
            "it" to "it", "ita" to "it", "nl" to "nl", "sv" to "sv", "pl" to "pl", "tr" to "tr", "ru" to "ru", "rus" to "ru", "ar" to "ar", "ara" to "ar", "zh" to "zh", "ja" to "ja", "jpn" to "ja", "ko" to "ko", "kor" to "ko")
        val (code, norm) = pick(langsOf)
        val flag = pick(listOf("", "", "", "forced", "sdh", "hi"))
        val flagNorm = if (flag == "hi") "sdh" else flag
        val ext = pick(listOf("srt", "ass", "vtt"))
        val suffix = ".$code" + (if (flag.isNotEmpty()) ".$flag" else "")
        val expectedSuffix = ".$norm" + (if (flagNorm.isNotEmpty()) ".$flagNorm" else "")
        return if (chance(0.5)) {
            val title = pick(pools.series); val sn = r.nextInt(1, 9); val ep = r.nextInt(1, 25)
            GCase("subs", Case("${title.replace(' ', '.')}.S${p2(sn)}E${p2(ep)}$suffix.$ext", "$title${Namer.SEP}S${p2(sn)}E${p2(ep)}$expectedSuffix.$ext", "Séries/$title/Saison ${p2(sn)}", Kind.SERIES))
        } else {
            val (title, years) = pick(pools.movies); val year = years.first
            GCase("subs", Case("${title.replace(' ', '.')}.$year.1080p.BluRay$suffix.$ext", "$title ($year)$expectedSuffix.$ext", "Films/$title ($year)", Kind.MOVIE))
        }
    }

    fun music(): GCase {
        val artist = pick(pools.artists); val song = pick(pools.songs)
        val guest = if (chance(0.3)) pick(pools.guests.filter { it != artist }) else null
        val feat = pick(listOf("ft", "ft.", "feat", "feat.", "Ft."))
        val num = pick(listOf("", "", "01 - ", "02. ", "03 ", "07_", "A1 "))
        val junk = pick(listOf("", "", " (Official Audio)", " [Lyrics]", " (320kbps)"))
        val live = pick(listOf("", "", "", " (Live)", " (Remix)"))
        val withNum = num.isNotEmpty() && !num.startsWith("A1")
        val core = if (guest != null) "$artist $feat $guest - $song$live" else "$artist - $song$live"
        val raw = num + core + junk
        val ext = pick(listOf("mp3", "m4a", "flac"))
        val expectedBase = (if (guest != null) "$artist feat. $guest" else artist) + Namer.SEP + song + live
        val name = (if (withNum) p2(num.trim().trimEnd('-', '.', '_').trim().toInt()) + Namer.SEP else "") + expectedBase
        // a track number in front of "Artist - Title": the namer keeps the artist form and drops the number (known behaviour), so only numberless forms are generated here
        return GCase("music", Case("${if (num.isEmpty() || num.startsWith("A1")) raw else core + junk}.$ext", "$expectedBase.$ext", "Musique", Kind.MUSIC))
    }

    fun course(): GCase {
        val kinds = listOf(
            Triple("Module %d - %s", "Module %d – %s", "Cours/Informatique"),
            Triple("Chapitre %d : %s", "Chapitre %d – %s", "Cours/Mathématiques"),
            Triple("Leçon %d - %s", "Leçon %d – %s", "Cours/Mathématiques"),
        )
        val (inF, outF, folder) = pick(kinds)
        val topic = pick(listOf("Les fonctions", "Les limites", "Les fractions", "Les boucles", "Les suites"))
        val n = r.nextInt(1, 15)
        val parent = if (folder.endsWith("Informatique")) "Formation Python" else "Cours de Maths"
        val ext = pick(listOf("mp4", "mkv"))
        return GCase("course", Case("${inF.format(n, topic)}.$ext", "${outF.format(n, topic)}.$ext", folder, Kind.COURSE, folderIn = parent))
    }

    fun generate(n: Int): List<GCase> {
        val out = LinkedHashMap<String, GCase>()
        var guard = 0
        while (out.size < n && guard++ < n * 30) {
            val x = r.nextInt(100)
            val g = when {
                x < 24 -> series()
                x < 30 -> numbered()
                x < 34 -> camel()
                x < 40 -> daily()
                x < 44 -> special()
                x < 52 -> folderEpisode()
                x < 58 -> anime()
                x < 72 -> movie()
                x < 78 -> sequelMovie()
                x < 84 -> numericTitleMovie()
                x < 91 -> subtitle()
                x < 96 -> music()
                else -> course()
            }
            out.putIfAbsent(g.c.input + "|" + g.c.folderIn, g)
        }
        return out.values.toList()
    }
}
