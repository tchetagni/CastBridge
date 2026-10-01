package castbridge.core.library.agent

import kotlin.random.Random

/** A generated case plus the family it belongs to (for the per-family report). */
data class GCase(val cat: String, val c: Case)

/** Title pools. The DEV pools tune the rules; the FROZEN pools are disjoint and were only ever used to build `naming/frozen.tsv`. */
class Pools(
    val series: List<String>,
    val seriesFr: List<String>,
    val anime: List<String>,
    val epTitles: List<String>,
    val movies: List<Pair<String, IntRange>>,
    val moviesFr: List<Pair<String, IntRange>>,
    val artists: List<String>,
    val songs: List<String>,
    val guests: List<String>,
)

object CorpusPools {
    val DEV = Pools(
        series = listOf("Prison Break", "Breaking Bad", "Game of Thrones", "The Walking Dead", "Vikings", "Lost", "Friends", "Dr House", "Money Heist", "Stranger Things",
            "Better Call Saul", "Squid Game", "Brooklyn Nine-Nine", "Peaky Blinders", "Dark", "Lucifer", "Dexter", "Suits", "Teen Wolf", "Grey's Anatomy",
            "Vampire Diaries", "How I Met Your Mother", "The Big Bang Theory", "Modern Family", "Narcos", "Ozark", "The Crown", "Shadow and Bone", "Blood Sisters", "Jacob's Cross",
            "Nollywood Diaries", "The Last of Us", "Gen-Z", "Mr Robot", "House of Cards", "Prodigal Son", "Rick and Morty", "The Good Doctor", "Elite", "Emily in Paris"),
        seriesFr = listOf("La Casa de Papel", "Plus belle la vie", "Les Bobodiouf", "Sa Majesté Afrique", "Jenifa", "Un Si Grand Soleil", "Scènes de Ménages", "Capitaine Marleau",
            "Engrenages", "Les Mystères de l'Amour", "Maman Ngono", "Les Voisines", "Madame Kamga", "Saga Douala", "Lagos Housewives", "Cités Futures"),
        anime = listOf("One Piece", "Naruto Shippuden", "Dragon Ball Z Kai", "Bleach", "Jujutsu Kaisen", "Attack on Titan", "Fairy Tail", "Black Clover", "Demon Slayer", "Hunter x Hunter"),
        epTitles = listOf("Pilot", "The Iron Throne", "Cut Off", "Good-Bye", "Ozymandias", "The Long Night", "Winter Is Coming", "Le Retour", "La Vérité", "Walkabout", "Final Offer",
            "The Reunion", "Home", "Plus jamais", "Aftermath", "The Convention", "Saul Gone", "VIP", "Revelations", "Le Grand Départ"),
        movies = listOf("Inception" to 2010..2010, "The Dark Knight" to 2008..2008, "Avatar" to 2009..2009, "Blade Runner 2049" to 2017..2017, "Spider-Man No Way Home" to 2021..2021,
            "Interstellar" to 2014..2014, "The Batman" to 2022..2022, "Skyfall" to 2012..2012, "Joker" to 2019..2019, "Gladiator" to 2000..2000, "Titanic" to 1997..1997,
            "Fast and Furious 9" to 2021..2021, "Black Panther Wakanda Forever" to 2022..2022, "Top Gun Maverick" to 2022..2022, "Django Unchained" to 2012..2012,
            "The Godfather" to 1972..1972, "Taxi 2" to 2000..2000, "Toy Story 4" to 2019..2019, "Mad Max Fury Road" to 2015..2015, "The Matrix" to 1999..1999,
            "Pulp Fiction" to 1994..1994, "Forrest Gump" to 1994..1994, "Jurassic Park" to 1993..1993, "Die Hard" to 1988..1988, "Casino Royale" to 2006..2006,
            "Bad Boys for Life" to 2020..2020, "John Wick" to 2014..2014, "No Time to Die" to 2021..2021, "The Lion King" to 1994..1994, "Frozen II" to 2019..2019),
        moviesFr = listOf("Le Roi Lion" to 1994..1994, "Les Visiteurs" to 1993..1993, "Intouchables" to 2011..2011, "Astérix et Obélix Mission Cléopâtre" to 2002..2002,
            "Amélie Poulain" to 2001..2001, "Kirikou et la sorcière" to 1998..1998, "Les Misérables" to 2012..2012, "Le Fabuleux Destin d'Amélie Poulain" to 2001..2001,
            "La Haine" to 1995..1995, "Taxi 5" to 2018..2018, "Le Dîner de Cons" to 1998..1998, "Bienvenue chez les Ch'tis" to 2008..2008, "Les Tuche" to 2011..2011,
            "Le Prénom" to 2012..2012, "Un Prophète" to 2009..2009, "Les Choristes" to 2004..2004, "Le Grand Bleu" to 1988..1988, "La Vie est un long fleuve tranquille" to 1988..1988),
        artists = listOf("Burna Boy", "Wizkid", "Davido", "Fally Ipupa", "Ayra Starr", "Koffi Olomide", "Rema", "Adele", "Rihanna", "Asake", "Tiken Jah Fakoly", "Yemi Alade",
            "Stromae", "Dadju", "Youssou N'Dour", "Angélique Kidjo", "Diamond Platnumz", "Maître Gims", "Tems", "Locko", "Charlotte Dipanda", "Mr Leo", "Magasco", "Salatiel"),
        songs = listOf("Last Last", "Essence", "Fall", "Eloko Oyo", "Rush", "Loi", "Calm Down", "Hello", "Diamonds", "Lonely At The Top", "Quitte le pouvoir", "Johnny",
            "Papaoutai", "Reine", "7 Seconds", "Afrika", "Jeje", "Sapés comme jamais", "Free Mind", "Je t'aime", "Ma Chérie", "Plus rien", "Mon Amour", "Beautiful Day"),
        guests = listOf("Niska", "Tems", "Chris Brown", "Anitta", "Fally Ipupa", "Wizkid", "Dadju", "Tiwa Savage"),
    )

    val FROZEN = Pools(
        series = listOf("Westworld", "Succession", "House of the Dragon", "The Mandalorian", "Black Mirror", "Sherlock", "Fargo", "Mindhunter", "The Boys", "Euphoria",
            "Chernobyl", "Homeland", "Arrow", "Supernatural", "Riverdale", "Gotham", "Lupin", "Ragnarok", "Sacred Games", "Orange Is the New Black", "The Witcher",
            "Bridgerton", "Cobra Kai", "Loki", "Hawkeye", "Yellowstone", "Billions", "Shameless", "Heroes", "Fringe", "Legion", "Mare of Easttown", "Beef", "The Bear",
            "Blue Bloods", "Young Sheldon", "Outlander", "Peacemaker", "Warrior Nun", "Lagos Nights"),
        seriesFr = listOf("Kaamelott", "Dix pour Cent", "Baron Noir", "Les Revenants", "Le Bureau des Légendes", "Cheyenne et Lola", "Fais pas ci Fais pas ça", "Les Petits Meurtres d'Agatha Christie",
            "Section de Recherches", "Camping Paradis", "Hôtel Mbamba", "Bamenda Nights", "Mariage à l'Africaine", "Les Dossiers de Mama Rose", "Quartier Latin Douala", "Sœurs de Yaoundé"),
        anime = listOf("Bleach Thousand Year Blood War", "My Hero Academia", "Death Note", "Fullmetal Alchemist", "One Punch Man", "Tokyo Ghoul", "Sword Art Online", "Gintama", "Boruto", "Haikyuu"),
        epTitles = listOf("Chapter One", "Le Chant du Cygne", "Hello World", "Dernière Chance", "Gone Fishing", "The Wedding", "Rien ne va plus", "First Blood", "Burned", "L'Appel",
            "Hide and Seek", "Les Retrouvailles", "Ghost Town", "Night Shift", "La Fin", "Fire Walk With Me", "The Letter", "Au Revoir"),
        movies = listOf("Parasite" to 2019..2019, "Whiplash" to 2014..2014, "The Prestige" to 2006..2006, "Shutter Island" to 2010..2010, "Se7en" to 1995..1995,
            "Gone Girl" to 2014..2014, "Black Swan" to 2010..2010, "Blade Runner" to 1982..1982, "Ocean's Eleven" to 2001..2001, "The Revenant" to 2015..2015,
            "Dune Part Two" to 2024..2024, "Oppenheimer" to 2023..2023, "Barbie" to 2023..2023, "Nope" to 2022..2022, "Creed III" to 2023..2023, "Scream VI" to 2023..2023,
            "The Equalizer 3" to 2023..2023, "Wonder Woman 1984" to 2020..2020, "Tenet" to 2020..2020, "Soul" to 2020..2020, "The Irishman" to 2019..2019,
            "Rocky IV" to 1985..1985, "Back to the Future" to 1985..1985, "Alien" to 1979..1979, "Scarface" to 1983..1983, "Heat" to 1995..1995, "Troy" to 2004..2004),
        moviesFr = listOf("Le Péril Jeune" to 1994..1994, "Les Randonneurs" to 1997..1997, "Qu'est-ce qu'on a fait au Bon Dieu" to 2014..2014, "La Famille Bélier" to 2014..2014,
            "Les Évadés du Sud" to 2015..2015, "Cyrano de Bergerac" to 1990..1990, "Le Cinquième Élément" to 1997..1997, "Subway" to 1985..1985, "Léon" to 1994..1994,
            "Camp de Thiaroye" to 1988..1988, "Mamadou et Moi" to 2012..2012, "Le Mec de la Tombe d'à Côté" to 2010..2010, "Les Aventures de Tintin" to 2011..2011),
        artists = listOf("Ckay", "Fireboy DML", "Joeboy", "Omah Lay", "Black Sherif", "Innoss'B", "Ténor", "Franko", "Vanessa Mdee", "Aya Nakamura", "Booba", "Jul",
            "Soolking", "Ninho", "Coco Jr Van Gogh", "Dobet Gnahoré", "Salif Keita", "Ismaël Lô", "Mory Kanté", "Richard Bona", "Sade", "Bruno Mars", "Rosalía", "Dua Lipa"),
        songs = listOf("Love Nwantiti", "Peru", "Soweto", "Ozeba", "Kwaku the Traveller", "Bad Boy", "Dolce Vita", "Djadja", "Vroum Vroum", "Bella Ciao", "La Danse des Mots",
            "Soro", "Yele", "Sweet Child", "Levitating", "Just the Two of Us", "Mon Pays", "Cœur de Lion", "Nuit Blanche", "Au Clair de la Lune", "Doucement", "Ma Vie"),
        guests = listOf("Davido", "Burna Boy", "Aya Nakamura", "Koffi Olomide", "Femi Kuti", "Teni", "Naza", "Gazo"),
    )
}

/** Deterministic generator of realistic names with the expected output computed from the TRUE metadata (never from the parser). */
class CorpusGen(seed: Long, private val pools: Pools) {
    private val r = Random(seed)
    private fun <T> pick(l: List<T>) = l[r.nextInt(l.size)]
    private fun chance(p: Double) = r.nextDouble() < p
    private fun pad(n: Int, w: Int = 2) = n.toString().padStart(w, '0')

    private val sites = listOf("[www.torrent9.ph] ", "www.o2tvseries.com - ", "[NetNaija.com] ", "www.wawacity.xyz - ", "[YTS.MX] ", "www.zone-telechargement.ws - ", "[www.cpasbien.cm] ")
    private val siteSuffix = listOf(" - NetNaija.com", " [YTS.MX]", " - o2tvseries", "-RARBG", "[rarbg]", " (www.torrent9.ph)")
    private val vtags = listOf("720p", "1080p", "HDTV", "WEB-DL", "WEBRip", "BluRay", "BDRip", "x264", "x265", "H.264", "HEVC", "AAC", "DD5.1", "DVDRip", "XviD", "480p", "NF", "AMZN", "10bit", "HDRip")
    private val groups = listOf("-RARBG", "-NTb", "-SPARKS", "-YIFY", "-GalaxyTV", "-DEMAND", "-JMT", "-LOST", "-EXTREME", "-CtrlHD", "-GGEZ")
    private val vexts = listOf("mkv", "mp4", "avi", "mp4", "mkv")

    private fun tail(sep: String): String {
        val n = r.nextInt(0, 5)
        val l = (0 until n).map { pick(vtags) }.distinct().toMutableList()
        var t = l.joinToString(sep)
        if (chance(0.3) && l.any { it.startsWith("x26") || it == "H.264" || it == "HEVC" }) t += pick(groups)
        return t
    }

    private data class Lang(val raw: String, val tag: String)
    private val langs = listOf(Lang("", ""), Lang("", ""), Lang("", ""), Lang("VF", ""), Lang("FRENCH", ""), Lang("TRUEFRENCH", ""), Lang("VOSTFR", " [VOSTFR]"), Lang("vostfr", " [VOSTFR]"),
        Lang("MULTi", " [MULTI]"), Lang("MULTI", " [MULTI]"), Lang("SUBFRENCH", " [VOSTFR]"))

    private val SMALL_ALL = setOf("a", "an", "the", "of", "and", "in", "on", "at", "to", "for", "or", "de", "du", "des", "la", "le", "les", "un", "une", "et", "au", "aux", "en", "sur", "d", "l", "à")
    /** Only titles written in Title Case can be rendered in lower / upper case and be expected back in Title Case. */
    private fun titleCased(t: String) = t.split(' ').withIndex().all { (i, w) -> w.isEmpty() || w[0].isUpperCase() || w[0].isDigit() || (i > 0 && w.lowercase() in SMALL_ALL) }
    private fun style(title: String, sep: String, cs0: Int): String {
        val cs = if (cs0 != 0 && titleCased(title) && !title.contains('\'')) cs0 else 0
        val t = when (cs) { 1 -> title.lowercase(); 2 -> title.uppercase(); else -> title }
        return t.split(' ').joinToString(sep)
    }

    // ---------------------------------------------------------------- series
    private fun marker(sn: Int, ep: Int, sep: String, double: Int?): Pair<String, String> {   // rendered, expected
        val expected = "S${pad(sn)}E${pad(ep)}" + (double?.let { "-E${pad(it)}" } ?: "")
        if (double != null) return pick(listOf("S${pad(sn)}E${pad(ep)}E${pad(double)}", "S${pad(sn)}E${pad(ep)}-E${pad(double)}", "s${pad(sn)}e${pad(ep)}-e${pad(double)}", "S${pad(sn)}E${pad(ep)}-${pad(double)}")) to expected
        val s2 = pad(sn); val e2 = pad(ep)
        val forms = listOf("S${s2}E$e2", "S${s2}E$e2", "S${s2}E$e2", "s${s2}e$e2", "S${sn}E$ep", "S$s2.E$e2", "S$s2 E$e2", "${sn}x$e2", "Saison $sn Episode $ep", "Season $sn Episode $ep",
            "S${s2}xE$e2", "Season $sn - Episode $ep", "S$s2 - E$e2", "Saison $sn Épisode $ep", "s${s2}E$e2", "S${s2}e$e2", "Season $sn Ep $ep", "Saison $sn Ep $ep", "[${sn}x$e2]", "S${s2}.Ep$e2")
        var m = pick(forms)
        if (sep != " " && sep != "-") m = m.replace(" - ", sep).replace(" ", sep)
        return m to expected
    }

    fun series(): GCase {
        val fr = chance(0.3)
        val title = pick(if (fr) pools.seriesFr else pools.series)
        val sn = if (chance(0.7)) r.nextInt(1, 10) else r.nextInt(1, 21)
        val ep = if (chance(0.85)) r.nextInt(1, 25) else r.nextInt(1, 130)
        val double = if (chance(0.06)) ep + 1 else null
        val sep = pick(listOf(".", ".", "_", " ", " ", " - "))
        val cs = pick(listOf(0, 0, 0, 0, 1, 2))
        val lang = pick(langs)
        val epTitle = if (cs == 0 && chance(0.3)) pick(pools.epTitles) else null
        val (m, exp) = marker(sn, ep, if (sep == " - ") " " else sep, double)
        val wordSep = if (sep == " - ") " " else sep
        val parts = ArrayList<String>()
        parts += style(title, wordSep, cs)
        parts += if (sep == " - " && !m.startsWith("[")) "- $m" else m
        epTitle?.let { parts += if (sep == " - ") "- ${it}" else it.split(' ').joinToString(wordSep) }
        if (lang.raw.isNotEmpty()) parts += lang.raw
        val t = tail(wordSep); if (t.isNotEmpty()) parts += t
        var base = parts.joinToString(wordSep)
        if (chance(0.18)) base = pick(sites) + base else if (chance(0.12)) base += pick(siteSuffix)
        if (chance(0.04)) base = "Copie de $base" else if (chance(0.04)) base += " (1)"
        val ext = pick(vexts)
        val name = "$title${Namer.SEP}$exp${epTitle?.let { Namer.SEP + it } ?: ""}${lang.tag}.$ext"
        return GCase("series", Case("$base.$ext", name, "Séries/$title/Saison ${pad(sn)}", Kind.SERIES, lang = "fr"))
    }

    fun anime(): GCase {
        val title = pick(pools.anime)
        val ep = r.nextInt(1, 1100)
        val group = pick(listOf("Erai-raws", "SubsPlease", "HorribleSubs", "ToonsHub", "Judas", "Anime Time"))
        val form = r.nextInt(4)
        val lang = pick(listOf("", "", "VOSTFR", "[Multiple Subtitle]"))
        val q = pick(listOf("1080p", "720p", "480p"))
        val epText = if (ep < 100 && chance(0.6)) pad(ep, 3) else ep.toString().padStart(2, '0')
        val ext = pick(listOf("mkv", "mp4"))
        val base = when (form) {
            0 -> "[$group] $title - $epText [$q]" + (if (lang.isNotEmpty()) " $lang" else "")
            1 -> "[$group] $title - $epText ($q)"
            2 -> "$title.ep.$ep.${if (lang == "VOSTFR") "vostfr." else ""}$q"
            else -> "$title Episode $ep" + (if (lang == "VOSTFR") " VOSTFR" else "") + " $q"
        }.let { if (form >= 2) it.replace(' ', if (form == 2) '.' else ' ') else it }
        val tag = if (lang == "VOSTFR" && form != 1) " [VOSTFR]" else ""
        return GCase("anime", Case("$base.$ext", "$title${Namer.SEP}E${pad(ep)}$tag.$ext", "Séries/$title", Kind.SERIES))
    }

    /** "Episode 04.mkv" in a "Show/Saison 1" folder, and a few other folder-context forms. */
    fun seriesFolder(): GCase {
        val title = pick(pools.series + pools.seriesFr)
        val sn = r.nextInt(1, 9); val ep = r.nextInt(1, 25)
        val sFolder = pick(listOf("Saison $sn", "Season $sn", "S${pad(sn)}", "Saison ${pad(sn)}"))
        val file = pick(listOf("Episode $ep", "Episode ${pad(ep)}", "E${pad(ep)}", "Ep ${pad(ep)}", "Épisode $ep"))
        val ext = pick(vexts)
        return GCase("series-folder", Case("$file.$ext", "$title${Namer.SEP}S${pad(sn)}E${pad(ep)}.$ext", "Séries/$title/Saison ${pad(sn)}", Kind.SERIES, folderIn = "$title/$sFolder"))
    }

    // ---------------------------------------------------------------- movies
    fun movie(): GCase {
        val fr = chance(0.35)
        val (title, years) = pick(if (fr) pools.moviesFr else pools.movies)
        val year = r.nextInt(years.first, years.last + 1)
        val sep = pick(listOf(".", ".", "_", " ", " "))
        val cs = if (title.contains('\'') || title.any { it.isDigit() }) 0 else pick(listOf(0, 0, 0, 1))
        val lang = pick(langs)
        val form = r.nextInt(5)
        val t = style(title, sep, cs)
        val yr = year.toString()
        val tl = tail(sep)
        val parts = ArrayList<String>()
        parts += t
        parts += when (form) { 0, 1 -> yr; 2 -> "($yr)"; 3 -> "[$yr]"; else -> "($yr)" }
        if (lang.raw.isNotEmpty()) parts += lang.raw
        if (tl.isNotEmpty()) parts += tl
        var base = parts.joinToString(sep)
        if (chance(0.18)) base = pick(sites) + base else if (chance(0.1)) base += pick(siteSuffix)
        if (chance(0.04)) base = "Copie de $base"
        val ext = pick(vexts)
        return GCase("movie", Case("$base.$ext", "$title ($year)${lang.tag}.$ext", "Films/$title ($year)", Kind.MOVIE))
    }

    // ---------------------------------------------------------------- music
    fun music(): GCase {
        val artist = pick(pools.artists); val song = pick(pools.songs)
        val guest = if (chance(0.2)) pick(pools.guests.filter { it != artist }) else null
        val gform = pick(listOf("ft", "ft.", "feat.", "feat", "Ft.", "Feat."))
        val junk = pick(listOf("", "", "", " (Official Video)", " [Official Audio]", " (Clip Officiel)", " [Lyrics]", " (320kbps)", " (Official Music Video)", " | Clip officiel", " (Audio Officiel)", " [Official Video]"))
        val isVideoJunk = junk.contains("Video") || junk.contains("Clip") || junk.contains("Lyrics")
        val lowerAll = chance(0.1) && guest == null && !artist.contains('\'') && !song.contains('\'') && song.lowercase().split(' ').none { it in SMALL }
        val num = if (chance(0.15)) pick(listOf("01 - ", "03. ", "07 ", "12 - ", "1. ")) else ""
        val site = if (chance(0.1)) pick(sites.filter { it.contains("naija") || it.contains("netnaija") || it.contains("torrent9") }.ifEmpty { listOf("www.naijavibes.com - ") }) else ""
        val underscore = chance(0.12) && guest == null
        var base = site + num + (if (guest != null) "$artist $gform $guest - $song" else "$artist - $song") + junk
        if (lowerAll) base = base.lowercase()
        if (underscore) base = base.replace(" - ", "_-_").replace(' ', '_')
        val ext: String; val kind: Kind
        if (isVideoJunk && chance(0.85)) { ext = "mp4"; kind = Kind.CLIP }
        else if (junk.contains("Audio") || junk.contains("320") || chance(0.9)) { ext = pick(listOf("mp3", "mp3", "m4a")); kind = Kind.MUSIC }
        else { ext = "mp4"; kind = Kind.CLIP }
        val name = (if (guest != null) "$artist feat. $guest" else artist) + Namer.SEP + song + "." + ext
        val cap = if (lowerAll) { // expected title case in French or English; only simple names are generated lower case
            val c = { s: String -> s.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } } }
            c(artist) + Namer.SEP + c(song) + "." + ext
        } else name
        return GCase("music", Case("$base.$ext", cap, if (kind == Kind.CLIP) "Clips" else "Musique", kind, dur = if (kind == Kind.CLIP) 4 else 0))
    }

    // ---------------------------------------------------------------- personal media
    fun personal(): GCase {
        val y = r.nextInt(2019, 2026); val mo = r.nextInt(1, 13); val d = r.nextInt(1, 29)
        val hh = r.nextInt(0, 24); val mi = r.nextInt(0, 60); val ss = r.nextInt(0, 60)
        val date = "$y-${pad(mo)}-${pad(d)}"; val hm = "${pad(hh)}h${pad(mi)}"
        val ymd = "$y${pad(mo)}${pad(d)}"
        val dup = if (chance(0.15)) " (${r.nextInt(1, 4)})" else ""
        return when (r.nextInt(10)) {
            0 -> GCase("personal", Case("WhatsApp Video $date at ${pad(hh)}.${pad(mi)}.${pad(ss)}$dup.mp4", "Vidéo WhatsApp$ES$date $hm.mp4", "Famille", Kind.PERSONAL))
            1 -> GCase("personal", Case("WhatsApp Image $date at ${pad(hh)}.${pad(mi)}.${pad(ss)}$dup.jpeg", "Photo WhatsApp$ES$date $hm.jpeg", "Famille", Kind.PHOTO))
            2 -> GCase("personal", Case("WhatsApp Audio $date at ${pad(hh)}.${pad(mi)}.${pad(ss)}$dup.opus", "Audio WhatsApp$ES$date $hm.opus", "Famille", Kind.PERSONAL))
            3 -> { val n = r.nextInt(1, 999); GCase("personal", Case("VID-$ymd-WA${pad(n, 4)}.mp4", "Vidéo WhatsApp$ES$date ($n).mp4", "Famille", Kind.PERSONAL)) }
            4 -> { val n = r.nextInt(1, 999); GCase("personal", Case("IMG-$ymd-WA${pad(n, 4)}.jpg", "Photo WhatsApp$ES$date ($n).jpg", "Famille", Kind.PHOTO)) }
            5 -> GCase("personal", Case("PXL_${ymd}_${pad(hh)}${pad(mi)}${pad(ss)}${r.nextInt(100, 999)}.mp4", "Vidéo$ES$date $hm.mp4", "Famille", Kind.PERSONAL))
            6 -> GCase("personal", Case("IMG_${ymd}_${pad(hh)}${pad(mi)}${pad(ss)}.jpg", "Photo$ES$date $hm.jpg", "Famille", Kind.PHOTO))
            7 -> GCase("personal", Case("VID_${ymd}_${pad(hh)}${pad(mi)}${pad(ss)}.mp4", "Vidéo$ES$date $hm.mp4", "Famille", Kind.PERSONAL))
            8 -> GCase("personal", Case("Screenshot_${ymd}-${pad(hh)}${pad(mi)}${pad(ss)}.png", "Capture d'écran$ES$date $hm.png", "Captures", Kind.PHOTO))
            else -> GCase("personal", Case("video_${date}_${pad(hh)}-${pad(mi)}-${pad(ss)}.mp4", "Vidéo Telegram$ES$date $hm.mp4", "Famille", Kind.PERSONAL))
        }
    }

    // ---------------------------------------------------------------- courses
    fun course(): GCase {
        val subj = pick(listOf("Maths" to "Mathématiques", "Physique Chimie" to "Physique-Chimie", "SVT" to "SVT", "Anglais" to "Anglais", "Philosophie" to "Philosophie", "Informatique" to "Informatique"))
        val topics = listOf("Les Limites", "Les Fonctions", "La Photosynthèse", "Les Temps du Passé", "Les Suites", "Les Ondes", "La Dissertation", "Les Dérivées", "Les Fractions", "La Cellule")
        val level = pick(listOf("", " Terminale", " Seconde", " 3ème", " Première"))
        val ch = r.nextInt(1, 15)
        val topic = pick(topics)
        val head = pick(listOf("Cours de", "Cours"))
        val base = "$head ${subj.first}$level - Chapitre $ch - $topic"
        val ext = pick(listOf("mp4", "mp4", "mkv", "pdf"))
        val kind = Kind.COURSE
        return GCase("course", Case("$base.$ext", base.replace(" - ", Namer.SEP) + "." + ext, "Cours/${subj.second}", kind))
    }

    companion object {
        private const val ES = " – "
        private val SMALL = setOf("at", "the", "of", "de", "la", "le", "les", "et", "un", "une", "du", "des", "in", "on", "to", "for", "and", "je", "ma", "mon", "est")
    }

    /** [n] cases with the family mix of a real library (series dominate), unique by input. */
    fun generate(n: Int): List<GCase> {
        val out = LinkedHashMap<String, GCase>()
        var guard = 0
        while (out.size < n && guard++ < n * 20) {
            val x = r.nextInt(100)
            val g = when {
                x < 40 -> series()
                x < 47 -> anime()
                x < 52 -> seriesFolder()
                x < 76 -> movie()
                x < 88 -> music()
                x < 97 -> personal()
                else -> course()
            }
            out.putIfAbsent(g.c.input + "|" + g.c.folderIn, g)
        }
        return out.values.toList()
    }
}
