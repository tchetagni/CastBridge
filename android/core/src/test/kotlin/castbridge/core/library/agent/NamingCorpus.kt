package castbridge.core.library.agent

/**
 * Realistic file names and what the agent is expected to propose. Written from the names seen on TVs and phones in
 * Cameroon / francophone Africa / English-speaking users (download sites, WhatsApp, cameras, courses), NOT from the parser.
 *
 * [name] = expected complete new file name, [folder] = expected folder (French labels), [kind] = expected kind.
 * [dur] = duration in minutes when it matters; [hide] = language tag the user usually watches (default VF).
 */
data class Case(val input: String, val name: String, val folder: String, val kind: Kind, val dur: Int = 0, val folderIn: String = "", val lang: String = "fr", val mtime: String? = null)

object NamingCorpus {
    private fun c(input: String, name: String, folder: String, kind: Kind, dur: Int = 0, folderIn: String = "", lang: String = "fr") = Case(input, name, folder, kind, dur, folderIn, lang)
    private fun s(input: String, name: String, folder: String, dur: Int = 0) = c(input, name, folder, Kind.SERIES, dur)
    private fun m(input: String, name: String, folder: String, dur: Int = 0) = c(input, name, folder, Kind.MOVIE, dur)

    /** Set A: written before the parser was run against it. */
    val A: List<Case> = listOf(
        // ---- series, classic release names
        s("Prison.Break.S01E04.FRENCH.DVDRip.XviD-JMT.avi", "Prison Break – S01E04.avi", "Séries/Prison Break/Saison 01"),
        s("prison break s01e04 vostfr 720p.mkv", "Prison Break – S01E04 [VOSTFR].mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break S02E11 FRENCH HDTV.mp4", "Prison Break – S02E11.mp4", "Séries/Prison Break/Saison 02"),
        s("PRISON.BREAK.S03E09.TRUEFRENCH.720p.HDTV.x264-LOST.mkv", "Prison Break – S03E09.mkv", "Séries/Prison Break/Saison 03"),
        s("Prison Break - 1x04 - Cut Off.avi", "Prison Break – S01E04 – Cut Off.avi", "Séries/Prison Break/Saison 01"),
        s("Prison_Break_S04E16_VF.mp4", "Prison Break – S04E16.mp4", "Séries/Prison Break/Saison 04"),
        s("[www.torrent9.ph] Breaking.Bad.S05E14.FRENCH.720p.WEB-DL.H264-Ghost.mkv", "Breaking Bad – S05E14.mkv", "Séries/Breaking Bad/Saison 05"),
        s("Breaking Bad S05E14 Ozymandias.mkv", "Breaking Bad – S05E14 – Ozymandias.mkv", "Séries/Breaking Bad/Saison 05"),
        s("Game.of.Thrones.S08E06.The.Iron.Throne.1080p.WEB-DL.DD5.1.H.264-GoT.mkv", "Game of Thrones – S08E06 – The Iron Throne.mkv", "Séries/Game of Thrones/Saison 08"),
        s("the.walking.dead.s10e22.720p.hdtv.x264-syncopy.mkv", "The Walking Dead – S10E22.mkv", "Séries/The Walking Dead/Saison 10"),
        s("Lost - 1x04 - Walkabout.avi", "Lost – S01E04 – Walkabout.avi", "Séries/Lost/Saison 01"),
        s("Friends.S01E01E02.mkv", "Friends – S01E01-E02.mkv", "Séries/Friends/Saison 01"),
        s("Scrubs.S02E05.VOSTFR.HDTV.mkv", "Scrubs – S02E05 [VOSTFR].mkv", "Séries/Scrubs/Saison 02"),
        s("The Office (US) S03E10 - The Convention.mp4", "The Office (US) – S03E10 – The Convention.mp4", "Séries/The Office (US)/Saison 03"),
        s("Dr.House.S03E05.FRENCH.HDTV.XviD.avi", "Dr House – S03E05.avi", "Séries/Dr House/Saison 03"),
        s("Money.Heist.S04E08.MULTi.1080p.WEB.x264-EXTREME.mkv", "Money Heist – S04E08 [MULTI].mkv", "Séries/Money Heist/Saison 04"),
        s("La.Casa.de.Papel.S01E01.VOSTFR.720p.mkv", "La Casa de Papel – S01E01 [VOSTFR].mkv", "Séries/La Casa de Papel/Saison 01"),
        s("Vikings.S06E10.1080p.WEB.H264-GGEZ[rarbg].mkv", "Vikings – S06E10.mkv", "Séries/Vikings/Saison 06"),
        s("Stranger.Things.S04E01.720p.NF.WEBRip.x264-GalaxyTV.mkv", "Stranger Things – S04E01.mkv", "Séries/Stranger Things/Saison 04"),
        s("The.Flash.2014.S02E03.720p.HDTV.x264.mkv", "The Flash (2014) – S02E03.mkv", "Séries/The Flash (2014)/Saison 02"),
        s("Saison 2 Episode 5 - Plus belle la vie.mp4", "Saison 2 Episode 5 – Plus belle la vie.mp4", "À trier"),
        s("Plus belle la vie Saison 12 Episode 34.mp4", "Plus belle la vie – S12E34.mp4", "Séries/Plus belle la vie/Saison 12"),
        s("Better.Call.Saul.S06E13.Saul.Gone.720p.AMZN.WEB-DL.DDP5.1.H.264-NTb.mkv", "Better Call Saul – S06E13 – Saul Gone.mkv", "Séries/Better Call Saul/Saison 06"),
        s("S01E04.mkv", "S01E04.mkv", "À trier"),
        // ---- series, African download sites and local series
        s("[NetNaija.com] Blood Sisters S01E02.mp4", "Blood Sisters – S01E02.mp4", "Séries/Blood Sisters/Saison 01"),
        s("www.o2tvseries.com - Shadow and Bone S01E03 (Mp4).mp4", "Shadow and Bone – S01E03.mp4", "Séries/Shadow and Bone/Saison 01"),
        s("Jenifa.Saison.2.Episode.5.mp4", "Jenifa – S02E05.mp4", "Séries/Jenifa/Saison 02"),
        s("Les Bobodiouf - Episode 12.mp4", "Les Bobodiouf – E12.mp4", "Séries/Les Bobodiouf"),
        s("Sa Majesté Afrique - Ep 4.mp4", "Sa Majesté Afrique – E04.mp4", "Séries/Sa Majesté Afrique"),
        s("Jacob's Cross Season 1 Episode 4 - NetNaija.mp4", "Jacob's Cross – S01E04.mp4", "Séries/Jacob's Cross/Saison 01"),
        s("[Erai-raws] One Piece - 1045 [1080p][Multiple Subtitle].mkv", "One Piece – E1045.mkv", "Séries/One Piece"),
        s("Naruto Shippuden - 123 [720p].mkv", "Naruto Shippuden – E123.mkv", "Séries/Naruto Shippuden"),
        s("Squid.Game.S01E07.VIP.VOSTFR.1080p.NF.WEB-DL.mkv", "Squid Game – S01E07 – VIP [VOSTFR].mkv", "Séries/Squid Game/Saison 01"),
        s("Nollywood Diaries Season 3 Episode 10.mp4", "Nollywood Diaries – S03E10.mp4", "Séries/Nollywood Diaries/Saison 03"),
        c("Episode 04.mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01", Kind.SERIES, folderIn = "Prison Break/Saison 1"),
        // ---- movies
        m("Inception.2010.1080p.BluRay.x264-SPARKS.mkv", "Inception (2010).mkv", "Films/Inception (2010)"),
        m("The.Dark.Knight.2008.FRENCH.BDRip.XviD-UTT.avi", "The Dark Knight (2008).avi", "Films/The Dark Knight (2008)"),
        m("Avatar 2009 720p.mp4", "Avatar (2009).mp4", "Films/Avatar (2009)"),
        m("2012.2009.720p.BluRay.x264.mkv", "2012 (2009).mkv", "Films/2012 (2009)"),
        m("1917.2019.1080p.WEB-DL.mkv", "1917 (2019).mkv", "Films/1917 (2019)"),
        m("Blade.Runner.2049.2017.1080p.BluRay.x264.mkv", "Blade Runner 2049 (2017).mkv", "Films/Blade Runner 2049 (2017)"),
        m("Mission.Impossible.Fallout.2018.TRUEFRENCH.1080p.BluRay.mkv", "Mission Impossible Fallout (2018).mkv", "Films/Mission Impossible Fallout (2018)"),
        m("Spider-Man.No.Way.Home.2021.1080p.WEBRip.mp4", "Spider-Man No Way Home (2021).mp4", "Films/Spider-Man No Way Home (2021)"),
        m("WALL-E (2008) 720p BrRip.mp4", "Wall-E (2008).mp4", "Films/Wall-E (2008)"),
        m("Le.Roi.Lion.1994.FRENCH.DVDRip.mkv", "Le Roi Lion (1994).mkv", "Films/Le Roi Lion (1994)"),
        m("le roi lion 1994.mp4", "Le Roi Lion (1994).mp4", "Films/Le Roi Lion (1994)"),
        m("LES MISERABLES 2012 720p.mkv", "Les Miserables (2012).mkv", "Films/Les Miserables (2012)"),
        m("Intouchables (2011) MULTi 1080p BluRay.mkv", "Intouchables (2011) [MULTI].mkv", "Films/Intouchables (2011)"),
        m("Fast.and.Furious.9.2021.1080p.mkv", "Fast and Furious 9 (2021).mkv", "Films/Fast and Furious 9 (2021)"),
        m("Avengers.Endgame.2019.IMAX.2160p.UHD.BluRay.x265.mkv", "Avengers Endgame (2019).mkv", "Films/Avengers Endgame (2019)"),
        m("Black Panther Wakanda Forever 2022 HDCAM.mp4", "Black Panther Wakanda Forever (2022).mp4", "Films/Black Panther Wakanda Forever (2022)"),
        m("Oppenheimer.1080p.WEB-DL.mkv", "Oppenheimer.mkv", "Films/Oppenheimer"),
        m("Skyfall.2012.720p.BluRay.x264.YIFY.mp4", "Skyfall (2012).mp4", "Films/Skyfall (2012)"),
        m("[YTS.MX] The Batman (2022) [1080p] [BluRay] [5.1] [YTS.MX].mp4", "The Batman (2022).mp4", "Films/The Batman (2022)"),
        m("Les.Misérables.2012.FRENCH.720p.mkv", "Les Misérables (2012).mkv", "Films/Les Misérables (2012)"),
        m("Astérix et Obélix - Mission Cléopâtre (2002) FRENCH DVDRip.avi", "Astérix et Obélix – Mission Cléopâtre (2002).avi", "Films/Astérix et Obélix – Mission Cléopâtre (2002)"),
        m("Amélie.Poulain.2001.mkv", "Amélie Poulain (2001).mkv", "Films/Amélie Poulain (2001)"),
        m("the lord of the rings 2001 1080p.mkv", "The Lord of the Rings (2001).mkv", "Films/The Lord of the Rings (2001)"),
        m("Interstellar.2014.IMAX.1080p.BluRay.DTS.x264-CtrlHD.mkv", "Interstellar (2014).mkv", "Films/Interstellar (2014)"),
        m("Star Wars Episode 1 The Phantom Menace 1999 1080p.mkv", "Star Wars Episode 1 The Phantom Menace (1999).mkv", "Films/Star Wars Episode 1 The Phantom Menace (1999)"),
        m("www.wawacity.xyz - Taxi 5 (2018) FRENCH 720p.mkv", "Taxi 5 (2018).mkv", "Films/Taxi 5 (2018)"),
        m("Copie de Inception 2010 720p.mkv", "Inception (2010).mkv", "Films/Inception (2010)"),
        m("Gladiator.2000.REMASTERED.1080p.BluRay.mkv", "Gladiator (2000).mkv", "Films/Gladiator (2000)"),
        // ---- subtitles follow their video
        c("Inception.2010.1080p.BluRay.x264.fr.srt", "Inception (2010).fr.srt", "Films/Inception (2010)", Kind.MOVIE),
        c("Prison.Break.S01E04.FRENCH.srt", "Prison Break – S01E04.fr.srt", "Séries/Prison Break/Saison 01", Kind.SERIES),
        // ---- personal media
        c("WhatsApp Video 2024-03-15 at 14.22.11.mp4", "Vidéo WhatsApp – 2024-03-15 14h22.mp4", "Famille", Kind.PERSONAL),
        c("WhatsApp Video 2024-03-15 at 14.22.11 (1).mp4", "Vidéo WhatsApp – 2024-03-15 14h22.mp4", "Famille", Kind.PERSONAL),
        c("VID-20240315-WA0012.mp4", "Vidéo WhatsApp – 2024-03-15 (12).mp4", "Famille", Kind.PERSONAL),
        c("IMG-20240315-WA0003.jpg", "Photo WhatsApp – 2024-03-15 (3).jpg", "Famille", Kind.PHOTO),
        c("PXL_20240315_142211234.mp4", "Vidéo – 2024-03-15 14h22.mp4", "Famille", Kind.PERSONAL),
        c("VID_20240315_142211.mp4", "Vidéo – 2024-03-15 14h22.mp4", "Famille", Kind.PERSONAL),
        c("20240315_142211.mp4", "Vidéo – 2024-03-15 14h22.mp4", "Famille", Kind.PERSONAL),
        c("Screenrecorder-2024-03-15-14-22-11-123.mp4", "Enregistrement d'écran – 2024-03-15 14h22.mp4", "Captures", Kind.PERSONAL),
        c("Screenshot_20240315-142211.jpg", "Capture d'écran – 2024-03-15 14h22.jpg", "Captures", Kind.PHOTO),
        c("video_2024-03-15_14-22-11.mp4", "Vidéo Telegram – 2024-03-15 14h22.mp4", "Famille", Kind.PERSONAL),
        c("AUD-20240315-WA0003.opus", "Audio WhatsApp – 2024-03-15 (3).opus", "Famille", Kind.PERSONAL),
        c("PTT-20240316-WA0021.opus", "Audio WhatsApp – 2024-03-16 (21).opus", "Famille", Kind.PERSONAL),
        c("IMG_1234.MOV", "IMG_1234.MOV", "Famille", Kind.PERSONAL),
        c("DSC_0001.mp4", "DSC_0001.mp4", "Famille", Kind.PERSONAL),
        // ---- music and clips
        c("Burna Boy - Last Last (Official Video).mp4", "Burna Boy – Last Last.mp4", "Clips", Kind.CLIP),
        c("Maître Gims - Sapés comme jamais ft Niska (Clip Officiel).mp4", "Maître Gims – Sapés comme jamais feat. Niska.mp4", "Clips", Kind.CLIP),
        c("Wizkid - Essence ft. Tems [Official Audio].mp3", "Wizkid – Essence feat. Tems.mp3", "Musique", Kind.MUSIC),
        c("Fally Ipupa - Eloko Oyo (Clip officiel).mp4", "Fally Ipupa – Eloko Oyo.mp4", "Clips", Kind.CLIP),
        c("01 - Davido - Fall.mp3", "Davido – Fall.mp3", "Musique", Kind.MUSIC),
        c("03. Ayra Starr - Rush.mp3", "Ayra Starr – Rush.mp3", "Musique", Kind.MUSIC),
        c("Koffi_Olomide_-_Loi.mp3", "Koffi Olomide – Loi.mp3", "Musique", Kind.MUSIC),
        c("www.naijavibes.com - Rema - Calm Down.mp3", "Rema – Calm Down.mp3", "Musique", Kind.MUSIC),
        c("Adele - Hello (320kbps).mp3", "Adele – Hello.mp3", "Musique", Kind.MUSIC),
        c("rihanna - diamonds.mp3", "Rihanna – Diamonds.mp3", "Musique", Kind.MUSIC),
        c("Davido - Unavailable (Lyrics).mp4", "Davido – Unavailable.mp4", "Clips", Kind.CLIP),
        c("Asake - Lonely At The Top.mp4", "Asake – Lonely At The Top.mp4", "Clips", Kind.CLIP, dur = 3),
        c("Tiken Jah Fakoly - Quitte le pouvoir | Clip officiel.mp4", "Tiken Jah Fakoly – Quitte le pouvoir.mp4", "Clips", Kind.CLIP),
        // ---- courses
        c("Cours de Maths - Chapitre 3 - Les Limites.mp4", "Cours de Maths – Chapitre 3 – Les Limites.mp4", "Cours/Mathématiques", Kind.COURSE),
        c("tuto python debutant 01.mp4", "Tuto Python Debutant 01.mp4", "Cours/Informatique", Kind.COURSE),
        c("Udemy - Complete Python Bootcamp.mp4", "Udemy – Complete Python Bootcamp.mp4", "Cours/Informatique", Kind.COURSE),
        c("Cours_Comptabilité_Générale_S1.pdf", "Cours Comptabilité Générale S1.pdf", "Cours/Économie", Kind.COURSE),
        c("BEPC 2019 Mathématiques corrigé.pdf", "BEPC 2019 Mathématiques corrigé.pdf", "Cours/Mathématiques", Kind.COURSE),
        c("Physique Chimie Terminale - Les ondes.mp4", "Physique Chimie Terminale – Les ondes.mp4", "Cours/Physique-Chimie", Kind.COURSE),
        // ---- documents, apps, archives
        c("CV_Esaie_Tchetagni_2024.pdf", "CV Esaie Tchetagni 2024.pdf", "Documents", Kind.DOCUMENT),
        c("facture-orange-mars.pdf", "facture-orange-mars.pdf", "Documents", Kind.DOCUMENT),
        c("[www.apkpure.com] WhatsApp_v2.24.apk", "WhatsApp_v2.24.apk", "Applications", Kind.APP),
        c("CastBridge-TV-0.11.1 (1).apk", "CastBridge-TV-0.11.1.apk", "Applications", Kind.APP),
        c("photos famille noel.zip", "photos famille noel.zip", "Archives", Kind.ARCHIVE),
        // ---- nothing to conclude: keep, send to "À trier"
        c("Mon voyage à Douala.mp4", "Mon voyage à Douala.mp4", "Famille", Kind.PERSONAL),
        c("Nollywood mix.mp4", "Nollywood mix.mp4", "À trier", Kind.UNKNOWN),
        c("video.mp4", "video.mp4", "À trier", Kind.UNKNOWN),
        // ---- English speaking user
        c("the.office.us.s02e01.720p.mkv", "The Office US – S02E01.mkv", "Series/The Office US/Season 02", Kind.SERIES, lang = "en"),
        c("Inception 2010 1080p.mkv", "Inception (2010).mkv", "Movies/Inception (2010)", Kind.MOVIE, lang = "en"),
        c("WhatsApp Video 2024-03-15 at 14.22.11.mp4", "Video WhatsApp – 2024-03-15 14h22.mp4", "Family", Kind.PERSONAL, lang = "en"),
    )
}
