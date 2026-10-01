package castbridge.core.library.agent

/**
 * Set B: harder and more varied, written AFTER set A and run once before any tuning of the rules against it; its
 * first-run score is reported in docs/LIBRARY-AGENT.md (the tests keep it green afterwards, the honest number is the first-run one).
 */
object NamingCorpusB {
    private fun c(input: String, name: String, folder: String, kind: Kind, dur: Int = 0, folderIn: String = "", lang: String = "fr") = Case(input, name, folder, kind, dur, folderIn, lang)
    private fun s(input: String, name: String, folder: String) = c(input, name, folder, Kind.SERIES)
    private fun m(input: String, name: String, folder: String) = c(input, name, folder, Kind.MOVIE)

    val B: List<Case> = listOf(
        // ---- series
        s("Grey's Anatomy S15E03 VOSTFR 720p.mp4", "Grey's Anatomy – S15E03 [VOSTFR].mp4", "Séries/Grey's Anatomy/Saison 15"),
        s("Vampire.Diaries.S08E16.FINAL.FRENCH.HDTV.x264.mkv", "Vampire Diaries – S08E16.mkv", "Séries/Vampire Diaries/Saison 08"),
        s("Brooklyn.Nine-Nine.S05E10.720p.mkv", "Brooklyn Nine-Nine – S05E10.mkv", "Séries/Brooklyn Nine-Nine/Saison 05"),
        s("Suits.S09E10.Good-Bye.720p.mkv", "Suits – S09E10 – Good-Bye.mkv", "Séries/Suits/Saison 09"),
        c("E01 - Pilot.mp4", "Lucifer – S03E01 – Pilot.mp4", "Séries/Lucifer/Saison 03", Kind.SERIES, folderIn = "Lucifer/Season 3"),
        c("Season 1 - Episode 3.mkv", "Dark – S01E03.mkv", "Séries/Dark/Saison 01", Kind.SERIES, folderIn = "Dark"),
        s("Peaky Blinders S1 E3.mkv", "Peaky Blinders – S01E03.mkv", "Séries/Peaky Blinders/Saison 01"),
        s("the.100.s05e03.mkv", "The 100 – S05E03.mkv", "Séries/The 100/Saison 05"),
        s("24.S08E24.VOSTFR.720p.mkv", "24 – S08E24 [VOSTFR].mkv", "Séries/24/Saison 08"),
        s("Teen Wolf 3x05 Fireflies.avi", "Teen Wolf – S03E05 – Fireflies.avi", "Séries/Teen Wolf/Saison 03"),
        s("Dexter.S08E12.FINAL.FRENCH.720p.HDTV.mkv", "Dexter – S08E12.mkv", "Séries/Dexter/Saison 08"),
        s("Dragon.Ball.Z.Kai.ep.45.vostfr.mp4", "Dragon Ball Z Kai – E45 [VOSTFR].mp4", "Séries/Dragon Ball Z Kai"),
        s("One.Piece.Episode.1000.VOSTFR.1080p.mkv", "One Piece – E1000 [VOSTFR].mkv", "Séries/One Piece"),
        s("Tom.et.Jerry.Saison.1.Episode.12.mp4", "Tom et Jerry – S01E12.mp4", "Séries/Tom et Jerry/Saison 01"),
        s("Gen-Z.Season.1.Episode.3.NetNaija.com.mp4", "Gen-Z – S01E03.mp4", "Séries/Gen-Z/Saison 01"),
        s("[ToonsHub] Jujutsu Kaisen S02E10 1080p.mkv", "Jujutsu Kaisen – S02E10.mkv", "Séries/Jujutsu Kaisen/Saison 02"),
        s("Prison Break – S01E04.mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison%20Break%20S01E04.mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("PRISON BREAK S01E04 VF.MKV", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Mr.Robot.S01E01.mkv", "Mr Robot – S01E01.mkv", "Séries/Mr Robot/Saison 01"),
        s("What If...? S01E01.mkv", "What If – S01E01.mkv", "Séries/What If/Saison 01"),
        s("Prison Break S01E04 (1).mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        c("the.last.of.us.s01e03.1080p.web.h264-cakes.mkv", "The Last of Us – S01E03.mkv", "Series/The Last of Us/Season 01", Kind.SERIES, lang = "en"),
        c("Breaking.Bad.S01E01.720p.BluRay.x264-DEMAND.mkv", "Breaking Bad – S01E01.mkv", "Series/Breaking Bad/Season 01", Kind.SERIES, lang = "en"),
        // ---- movies
        m("Toy.Story.4.2019.FRENCH.720p.BluRay.x264.mkv", "Toy Story 4 (2019).mkv", "Films/Toy Story 4 (2019)"),
        m("The.Godfather.1972.REMASTERED.1080p.BluRay.mkv", "The Godfather (1972).mkv", "Films/The Godfather (1972)"),
        m("Les.Visiteurs.1993.FRENCH.DVDRip.XviD.avi", "Les Visiteurs (1993).avi", "Films/Les Visiteurs (1993)"),
        m("Taxi.2.2000.FRENCH.DVDRip.mkv", "Taxi 2 (2000).mkv", "Films/Taxi 2 (2000)"),
        m("Django.Unchained.2012.MULTi.TRUEFRENCH.1080p.BluRay.x264-Jojo.mkv", "Django Unchained (2012) [MULTI].mkv", "Films/Django Unchained (2012)"),
        m("Kirikou et la sorcière 1998.avi", "Kirikou et la sorcière (1998).avi", "Films/Kirikou et la sorcière (1998)"),
        m("Le Seigneur des Anneaux - La Communauté de l'Anneau (2001).mkv", "Le Seigneur des Anneaux – La Communauté de l'Anneau (2001).mkv", "Films/Le Seigneur des Anneaux – La Communauté de l'Anneau (2001)"),
        m("Top.Gun.Maverick.2022.IMAX.2160p.WEB-DL.DDP5.1.Atmos.HDR.HEVC-CM.mkv", "Top Gun Maverick (2022).mkv", "Films/Top Gun Maverick (2022)"),
        m("Bad Boys for Life (2020) [WEBRip] [1080p] [YTS.MX].mp4", "Bad Boys for Life (2020).mp4", "Films/Bad Boys for Life (2020)"),
        m("Bahubali 2 The Conclusion 2017 Hindi 720p BluRay.mp4", "Bahubali 2 The Conclusion (2017).mp4", "Films/Bahubali 2 The Conclusion (2017)"),
        m("Living in Bondage Breaking Free 2019 Nollywood.mp4", "Living in Bondage Breaking Free (2019).mp4", "Films/Living in Bondage Breaking Free (2019)"),
        m("Black.Adam.2022.1080p.WEB-DL.DDP5.1.Atmos.H.264-CMRG.mkv", "Black Adam (2022).mkv", "Films/Black Adam (2022)"),
        m("Titanic 1997.avi", "Titanic (1997).avi", "Films/Titanic (1997)"),
        m("Mr. Bean's Holiday (2007).mp4", "Mr Bean's Holiday (2007).mp4", "Films/Mr Bean's Holiday (2007)"),
        m("Mission: Impossible (1996).mkv", "Mission – Impossible (1996).mkv", "Films/Mission – Impossible (1996)"),
        m("Les.Misérables.2012.720p.mkv", "Les Misérables (2012).mkv", "Films/Les Misérables (2012)"),
        m("Pirates des Caraïbes - La Malédiction du Black Pearl (2003) MULTi 1080p.mkv", "Pirates des Caraïbes – La Malédiction du Black Pearl (2003) [MULTI].mkv", "Films/Pirates des Caraïbes – La Malédiction du Black Pearl (2003)"),
        m("Inception (2010).mkv", "Inception (2010).mkv", "Films/Inception (2010)"),
        m("E.T. the Extra-Terrestrial (1982).mkv", "E.T. the Extra-Terrestrial (1982).mkv", "Films/E.T. the Extra-Terrestrial (1982)"),
        m("Joker.2019.720p.HDRip.AC3.x264.mkv", "Joker (2019).mkv", "Films/Joker (2019)"),
        // ---- unknown or personal videos that only look like films
        c("FILM COMPLET - Ma Famille 2 - Comédie camerounaise.mp4", "FILM COMPLET – Ma Famille 2 – Comédie camerounaise.mp4", "À trier", Kind.UNKNOWN),
        c("Scary Movie 3.mp4", "Scary Movie 3.mp4", "À trier", Kind.UNKNOWN),
        c("Mariage Jean & Sophie 2022.mp4", "Mariage Jean & Sophie 2022.mp4", "Famille", Kind.PERSONAL),
        c("Anniversaire de Maman 2023.mp4", "Anniversaire de Maman 2023.mp4", "Famille", Kind.PERSONAL),
        c("FB_VID_1234567890.mp4", "FB_VID_1234567890.mp4", "À trier", Kind.UNKNOWN),
        // ---- phone media
        c("WhatsApp Video 2023-12-25 at 18.05.44 (2).mp4", "Vidéo WhatsApp – 2023-12-25 18h05.mp4", "Famille", Kind.PERSONAL),
        c("WhatsApp Image 2024-01-02 at 09.10.11.jpeg", "Photo WhatsApp – 2024-01-02 09h10.jpeg", "Famille", Kind.PHOTO),
        c("WhatsApp Audio 2024-01-02 at 09.10.11.opus", "Audio WhatsApp – 2024-01-02 09h10.opus", "Famille", Kind.PERSONAL),
        c("WhatsApp Vidéo 2024-02-14 à 20.30.15.mp4", "Vidéo WhatsApp – 2024-02-14 20h30.mp4", "Famille", Kind.PERSONAL),
        c("IMG_20240101_120000.jpg", "Photo – 2024-01-01 12h00.jpg", "Famille", Kind.PHOTO),
        c("20240102_101010 (1).mp4", "Vidéo – 2024-01-02 10h10.mp4", "Famille", Kind.PERSONAL),
        c("Record_2024-03-15-14-22-11_abc123.mp4", "Enregistrement d'écran – 2024-03-15 14h22.mp4", "Captures", Kind.PERSONAL),
        c("MVI_0456.MP4", "MVI_0456.MP4", "Famille", Kind.PERSONAL),
        // ---- music and clips
        c("Davido ft Chris Brown - Shoulda Known Better.mp3", "Davido feat. Chris Brown – Shoulda Known Better.mp3", "Musique", Kind.MUSIC),
        c("Yemi Alade - Johnny (Official Music Video).mp4", "Yemi Alade – Johnny.mp4", "Clips", Kind.CLIP),
        c("Stromae - Papaoutai (Clip officiel).mp4", "Stromae – Papaoutai.mp4", "Clips", Kind.CLIP),
        c("Dadju feat. Anitta - Reine (Clip Officiel).mp4", "Dadju feat. Anitta – Reine.mp4", "Clips", Kind.CLIP),
        c("Youssou N'Dour - 7 Seconds.mp3", "Youssou N'Dour – 7 Seconds.mp3", "Musique", Kind.MUSIC),
        c("05 Angélique Kidjo - Afrika.mp3", "Angélique Kidjo – Afrika.mp3", "Musique", Kind.MUSIC),
        c("Diamond Platnumz - Jeje [Official Video] 4K.mp4", "Diamond Platnumz – Jeje.mp4", "Clips", Kind.CLIP),
        c("DJ Arafat - Moto Moto.mp3", "DJ Arafat – Moto Moto.mp3", "Musique", Kind.MUSIC),
        c("mix afro 2024 by dj kerozen.mp3", "Mix Afro 2024 by DJ Kerozen.mp3", "Musique", Kind.MUSIC),
        c("Burna Boy – Last Last.mp3", "Burna Boy – Last Last.mp3", "Musique", Kind.MUSIC),
        // ---- courses and documents
        c("Cours Anglais - Les temps du passé.mp3", "Cours Anglais – Les temps du passé.mp3", "Cours/Anglais", Kind.COURSE),
        c("Leçon 12 - Les fractions.mp4", "Leçon 12 – Les fractions.mp4", "Cours", Kind.COURSE),
        c("Formation Excel avancé - Tableaux croisés dynamiques.mp4", "Formation Excel avancé – Tableaux croisés dynamiques.mp4", "Cours/Informatique", Kind.COURSE),
        c("SVT Terminale D chap 4.mp4", "SVT Terminale D chap 4.mp4", "Cours/SVT", Kind.COURSE),
        c("Khan Academy - Algebra basics.mp4", "Khan Academy – Algebra basics.mp4", "Cours", Kind.COURSE),
        c("TD 3 Probabilités.pdf", "TD 3 Probabilités.pdf", "Documents", Kind.DOCUMENT),
        c("Attestation_de_naissance.pdf", "Attestation de naissance.pdf", "Documents", Kind.DOCUMENT),
        c("Livre - Les soleils des indépendances.epub", "Livre – Les soleils des indépendances.epub", "Documents", Kind.DOCUMENT),
        c("com.whatsapp_2.24.3.apk", "com.whatsapp_2.24.3.apk", "Applications", Kind.APP),
        c("TeamViewer_Setup.exe", "TeamViewer_Setup.exe", "À trier", Kind.UNKNOWN),
    )
}
