package castbridge.core.library.agent

/** Hand-written DEV cases (tuning allowed). Written independently of [FrozenHand] / [FrozenHardHand]. */
object DevHand {
    private fun c(input: String, name: String, folder: String, kind: Kind, folderIn: String = "", lang: String = "fr", dur: Int = 0) = GCase("dev-hand", Case(input, name, folder, kind, dur, folderIn, lang))
    private fun s(input: String, name: String, folder: String, folderIn: String = "", lang: String = "fr") = c(input, name, folder, Kind.SERIES, folderIn, lang)
    private fun m(input: String, name: String, folder: String, lang: String = "fr") = c(input, name, folder, Kind.MOVIE, "", lang)

    val CASES: List<GCase> = listOf(
        // numbering and layout seen on TVs and phones
        s("Prison Break S1 E4.mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break - S01 - E04.mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break Season 1 - 04.mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break Saison 1 - 04.mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break S01E004.mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break Episode 4 Saison 1.mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break - Episode 4 - Season 1.mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break S01xE04.mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break s01.e04.mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break [1x04].mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break (1x04).mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break 1x04-05.mkv", "Prison Break – S01E04-E05.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break S01E04 & E05.mkv", "Prison Break – S01E04-E05.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break S01E04-E06.mkv", "Prison Break – S01E04-E06.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break S01E04E05E06.mkv", "Prison Break – S01E04-E06.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break S01E104.mkv", "Prison Break – S01E104.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break S11E04.mkv", "Prison Break – S11E04.mkv", "Séries/Prison Break/Saison 11"),
        s("Prison  Break   S01E04.mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s(" Prison Break S01E04 .mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison-Break-S01E04.mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison.Break.S01E04.MKV", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison.Break.S01E04.Cut.Off.720p.mkv", "Prison Break – S01E04 – Cut Off.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break - S01E04 - Cut Off [VOSTFR].mkv", "Prison Break – S01E04 – Cut Off [VOSTFR].mkv", "Séries/Prison Break/Saison 01"),
        s("S01E04 - Cut Off.mkv", "Prison Break – S01E04 – Cut Off.mkv", "Séries/Prison Break/Saison 01", folderIn = "Prison Break"),
        s("Episode 4.mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01", folderIn = "Prison Break/Season 1"),
        s("Ep04.mkv", "Prison Break – S02E04.mkv", "Séries/Prison Break/Saison 02", folderIn = "Prison Break/Saison 2"),
        s("04.mkv", "Prison Break – S02E04.mkv", "Séries/Prison Break/Saison 02", folderIn = "Prison Break/Saison 2"),
        s("Le Bureau des Légendes S01E01.mkv", "Le Bureau des Légendes – S01E01.mkv", "Séries/Le Bureau des Légendes/Saison 01"),
        // phones
        c("Capture d’écran 2023-09-07 à 08.41.55.png", "Capture d'écran – 2023-09-07 08h41.png", "Captures", Kind.PHOTO),
        c("Screenshot 2023-09-07 at 08.41.55.png", "Capture d'écran – 2023-09-07 08h41.png", "Captures", Kind.PHOTO),
        c("Screenshot_2023-09-07-08-41-55.png", "Capture d'écran – 2023-09-07 08h41.png", "Captures", Kind.PHOTO),
        c("Screen Shot 2023-09-07 at 08.41.55.png", "Capture d'écran – 2023-09-07 08h41.png", "Captures", Kind.PHOTO),
        c("IMG_20230907_084155_1.jpg", "Photo – 2023-09-07 08h41.jpg", "Famille", Kind.PHOTO),
        c("IMG_20230907_084155_BURST001.jpg", "Photo – 2023-09-07 08h41.jpg", "Famille", Kind.PHOTO),
        c("20230907_084155_HDR.jpg", "Photo – 2023-09-07 08h41.jpg", "Famille", Kind.PHOTO),
        c("20230907_084155(0).jpg", "Photo – 2023-09-07 08h41.jpg", "Famille", Kind.PHOTO),
        c("VID-20230907-WA0031(1).mp4", "Vidéo WhatsApp – 2023-09-07 (31).mp4", "Famille", Kind.PERSONAL),
        c("VID_20230907_084155_1.mp4", "Vidéo – 2023-09-07 08h41.mp4", "Famille", Kind.PERSONAL),
        c("WhatsApp Video 2023-09-07 at 08.41.55.MP4", "Vidéo WhatsApp – 2023-09-07 08h41.mp4", "Famille", Kind.PERSONAL),
        // music
        c("Tiwa Savage & Brandy - Beautiful Day.mp3", "Tiwa Savage & Brandy – Beautiful Day.mp3", "Musique", Kind.MUSIC),
        c("Tiwa Savage x Brandy - Beautiful Day.mp3", "Tiwa Savage x Brandy – Beautiful Day.mp3", "Musique", Kind.MUSIC),
        c("Track 06 - Magic System - Premier Gaou.mp3", "Magic System – Premier Gaou.mp3", "Musique", Kind.MUSIC),
        c("[320kbps] Ayra Starr - Bloody Samaritan.mp3", "Ayra Starr – Bloody Samaritan.mp3", "Musique", Kind.MUSIC),
        c("Ayra Starr - Bloody Samaritan.MP3", "Ayra Starr – Bloody Samaritan.mp3", "Musique", Kind.MUSIC),
        c("WIZKID - BLESSED.mp3", "Wizkid – Blessed.mp3", "Musique", Kind.MUSIC),
        c("Wizkid_-_Blessed_(Official_Video).mp4", "Wizkid – Blessed.mp4", "Clips", Kind.CLIP),
        c("Wizkid - Blessed (Official Video) | Prod. by Sarz.mp4", "Wizkid – Blessed.mp4", "Clips", Kind.CLIP),
        // movies
        m("Lawrence.of.Arabia.1962.1080p.BluRay.mkv", "Lawrence of Arabia (1962).mkv", "Films/Lawrence of Arabia (1962)"),
        m("21.Grams.2003.720p.BluRay.mkv", "21 Grams (2003).mkv", "Films/21 Grams (2003)"),
        m("2010 (1984) 720p.mkv", "2010 (1984).mkv", "Films/2010 (1984)"),
        m("Memories of Murder - 2003 - 1080p.mkv", "Memories of Murder (2003).mkv", "Films/Memories of Murder (2003)"),
        m("Memories of Murder [2003] [1080p].mkv", "Memories of Murder (2003).mkv", "Films/Memories of Murder (2003)"),
        m("MEMORIES.OF.MURDER.2003.MULTI.1080p.mkv", "Memories of Murder (2003) [MULTI].mkv", "Films/Memories of Murder (2003)"),
        m("Memories of Murder.2003.1080p.WEB.DL.AAC2.0.H.264.mkv", "Memories of Murder (2003).mkv", "Films/Memories of Murder (2003)"),
        m("Memories of Murder 2003 Blu-Ray 1080p 10bit.mkv", "Memories of Murder (2003).mkv", "Films/Memories of Murder (2003)"),
        // tags that need care
        s("Prison Break S01E04 PROPER.mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break S01E04 REPACK 720p.mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break S01E04 iNTERNAL 720p.mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break S01E04 4K HDR.mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break S01E04 Blu-Ray 1080p.mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break S01E04 [1080p] [x265] [10bit] [PSA].mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break S01E04 (1080p HEVC).mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break S01E04 - Copy.mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break S01E04 - Copie (2).mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break S01E04 Multi Subs.mkv", "Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01"),
        s("Prison Break S01E04 VOSTFR HD.mkv", "Prison Break – S01E04 [VOSTFR].mkv", "Séries/Prison Break/Saison 01"),
        // titles made of numbers and initials
        s("Station.19.S02E04.720p.mkv", "Station 19 – S02E04.mkv", "Séries/Station 19/Saison 02"),
        s("M.A.S.H.1972.S03E05.mkv", "M.A.S.H. (1972) – S03E05.mkv", "Séries/M.A.S.H. (1972)/Saison 03"),
        s("Law.and.Order.Organized.Crime.S02E05.mkv", "Law and Order Organized Crime – S02E05.mkv", "Séries/Law and Order Organized Crime/Saison 02"),
        s("NCIS.Hawaii.S01E01.720p.HDTV.mkv", "NCIS Hawaii – S01E01.mkv", "Séries/NCIS Hawaii/Saison 01"),
        s("Marvel's.Agents.of.S.H.I.E.L.D.S05E10.mkv", "Marvel's Agents of S.H.I.E.L.D. – S05E10.mkv", "Séries/Marvel's Agents of S.H.I.E.L.D./Saison 05"),
    )
}
