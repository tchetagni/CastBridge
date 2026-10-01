# Catalogue des formes de noms de fichiers reconnues par le moteur d'organisation

> Branche `claude/naming-patterns` (cahier `docs/agent-briefs/naming-patterns.md`). Le moteur est celui de `core/library/agent/` (`NameParser`, `Namer`, `SeriesClassifier`) : 100 % local, déterministe, sans IA ni réseau. Ce document est la **référence des formes** (famille, exemple, règle, remarque, source, reconnu avant / après), la **méthode de mesure**, les **mesures**, la **sécurité des expressions** (ReDoS), le **regroupement des titres** et les **limites connues**. Voir aussi `docs/LIBRARY-AGENT.md` § 3.

## 1. Pourquoi et règle d'or

Un défaut réel avait été trouvé sur la TV du propriétaire : `Prison Break [S01-E08].avi` n'était pas reconnu (les crochets faisaient prendre le marqueur d'épisode pour une étiquette de téléchargement) alors que `(S01-E08)` l'était. Ce défaut était **déjà corrigé** dans `integration/agents` (`RX_BRACKETED_EPISODE`) ; ce travail le verrouille par des tests et cherche **méthodiquement** les autres : une famille après l'autre (épisodes, langues, films, bruit, sous-titres, musique, cours, séparateurs), avec un jeu de réglage et un jeu gelé neufs.

**Les faux positifs sont plus graves que les oublis.** Ranger un film dans un dossier de série est pire que laisser un fichier là où il est. Chaque règle ajoutée demande donc une **preuve** (un mot de saison, une étiquette de source télé, un dossier de saison, un groupe de diffusion…), les seuils de confiance existants sont inchangés (`SeriesClassifier.MIN_CONFIDENCE = 0.7`), et en cas de doute la réponse est « inconnu » (le fichier n'est pas touché). `FalsePositiveTest` (≈ 80 noms qui ressemblent à des épisodes, dates, parties ou langues sans en être) en est le garde-fou.

## 2. Sources, honnêtement

- **Aucun accès réseau n'a été utilisé** pour ce travail (la session n'a consulté aucune page). Les formes viennent des **conventions de nommage publiées** que l'auteur connaît, et des noms rencontrés dans les bibliothèques réelles (sites de téléchargement, messageries, appareils photo). Rien n'a été relu pendant la session : les noms de pages et les licences ci-dessous sont **de mémoire, à confirmer** avant toute citation officielle.
- **Salle blanche** : aucune expression régulière ni aucun code de ces projets n'a été copié ; chaque règle est dérivée ici à partir de la **liste des formes** (exemples), puis testée. Les licences de ces projets (GPL, propriétaire) sont incompatibles avec la nôtre : on ne s'en sert que comme **documentation de formes**.

| Sigle | Source documentaire (forme listée, pas de code) | Licence (de mémoire) |
|---|---|---|
| K | Kodi, wiki « Naming video files » (séries, films, parties `cd1`/`part1`, épisodes multiples) | code GPL-2.0+, texte du wiki CC BY-SA |
| P | Plex, articles d'aide « Naming and organizing your TV Show / Movie files » (SxxExx, dates, spéciaux en saison 0, parties, éditions) | propriétaire |
| J | Jellyfin, documentation « TV shows / Movies / Music » (saison 00, `-part1`, sous-titres `.forced` / `.sdh`, langues sur 3 lettres) | code GPL-2.0 |
| S | Sonarr, wiki « Naming » (séries quotidiennes par date, numérotation absolue des animés, épisodes multiples, numérotation à 3 chiffres) | code GPL-3.0 |
| T | TheTVDB, wiki d'ordre des épisodes (aired / absolute / spéciaux = saison 0) | propriétaire / conditions d'utilisation |
| U | Usage observé (noms vus sur des TV et téléphones : sites africains et nigérians, WhatsApp / Telegram, animés, langues du monde) | connaissance de l'auteur, **non vérifiée par accès réseau** |

## 3. Catalogue

Colonnes « Avant » / « Après » : ce que **le moteur de `integration/agents`** (commit `9f01453`) et **celui de cette branche** répondent à l'exemple, jugé contre le nom, le dossier et le type attendus (« oui » = exactement le résultat attendu ; « laissé tel quel » = le fichier n'est pas reconnu et n'est pas touché ; « mal reconnu » = un autre type, un autre titre, du bruit gardé, une saison ou un épisode faux). Les exemples sont tirés des 267 cas écrits à la main du jeu DEV-3 (`Dev3Hand`) ; sur les 111 exemples ci-dessous, **41 ne sont pas « oui » avant, 0 après** (c'est un jeu de réglage : voir § 5 pour les chiffres honnêtes). Le tableau ne reprend qu'un exemple par forme ; tous les cas sont dans `Dev3Hand.kt`.

Le moteur produit des noms **stables** : repasser l'agent sur un nom qu'il a produit ne change rien (tests `ourOwnNamesAreStable`, `ourNamesAreStable` et `FalsePositiveTest`).

### Épisodes

| Exemple | Dossier | Règle (expression dérivée) | Remarques | Source | Avant | Après |
|---|---|---|---|---|---|---|
| `Prison Break [S01-E08].avi` |  | `S` n `E` n, séparateur facultatif (`-`, `_`, `.`, `x`, espace), seul ou entre `[]` / `()` | **le défaut réel trouvé sur la TV** : un crochet dont tout le contenu est un marqueur d'épisode n'est jamais une étiquette (corrigé avant ce travail, verrouillé par tests) | K P J S | oui | oui |
| `Prison Break (S01-E08).avi` |  | idem |  | K P J S | oui | oui |
| `Prison Break [S01E08].avi` |  | idem |  | K P J S | oui | oui |
| `Prison Break S01_E08.avi` |  | `_` devient espace avant la recherche |  | P J S | oui | oui |
| `Prison.Break.S01.E08.avi` |  | `.` devient espace ; `S01 E08` accepté |  | K P J | oui | oui |
| `Prison Break S1E8.avi` |  | 1 à 2 chiffres pour la saison, 1 à 3 pour l'épisode |  | K P J S | oui | oui |
| `Prison Break S01xE08.avi` |  | `S` n `x` `E` n |  | S U | oui | oui |
| `Prison Break [1x08].avi` |  | n `x` nn, seul ou entre crochets | saison 1 à 40 seulement (évite `1920x1080`) | K P S | oui | oui |
| `Prison Break - [S01E08] - Buried.avi` |  | marqueur entre crochets au milieu, titre d'épisode conservé |  | U | oui | oui |
| `Prison Break - Season 1 Episode 8.avi` |  | mot saison + n, mot épisode + n (voir « langues ») |  | K P J | oui | oui |
| `Prison Break S01 Ep08.avi` |  | `S` n puis `Ep` n |  | U | oui | oui |
| `Prison Break Ep08.avi` |  | `Ep`/`Episode` n (1 à 4 chiffres) sans saison | un nombre de 1900 à 2100 n'est **jamais** un épisode (`Folge 2012`) | K P J | oui | oui |
| `Prison Break E08.avi` |  | `E` + 2 à 4 chiffres, entouré de frontières |  | K P | oui | oui |
| `Prison Break Season 1.mkv` |  | saison seule : classé sous le titre, sans épisode | confiance 0.75 | U | oui | oui |
| `PrisonBreakS01E08.avi` |  | `S`nn`E`nn collé derrière une minuscule : on insère une frontière |  | U | non (laissé tel quel) | oui |
| `PrisonBreak.S01E08.avi` |  | titre fait d'un seul mot en CamelCase (≥ 2 majuscules) → mots séparés | jamais appliqué hors marqueur de série ; `Raised by Wolves` collé ne retrouve pas « by » | U | non (mal reconnu) | oui |
| `Prison.Break.108.HDTV.XviD.avi` |  | 3 chiffres `snn` (saison 1-9, épisode 1-30) **seulement** avec une étiquette de source télé (HDTV, PDTV, WEB, WEBRip, WEB-DL, DVDRip), sans année, sans BluRay/CAM/TS, hors nombres de résolution | confiance 0.72 ; `Fahrenheit 451`, `Apollo 13` jamais pris pour des séries (testés) | S P | non (mal reconnu) | oui |
| `Prison Break 0108 HDTV.avi` |  | 4 chiffres `ssee` : saison 01-30 (hors 19-21), épisode 1-30, saison à 1 chiffre seulement avec zéro devant | `1917`, `2049`, `2160` exclus | S | non (mal reconnu) | oui |
| `Prison Break - 1x08 - Buried.avi` |  | titre d'épisode après le marqueur |  | K P J S | oui | oui |
| `Série Maison #04.mp4` |  | `#` n sans autre indice : **inconnu volontaire** | rien ne prouve que ce soit une série | U | oui | oui |
| `#04.mp4` | `Prison Break/Saison 2` | nombre nu (`04`, `E04`, `Ep.04`, `#04`, `[04]`) dans un dossier de saison | le dossier donne la série et la saison | K P J | non (laissé tel quel) | oui |
| `Prison Break - 04 - Cut Off.mkv` | `Prison Break/Saison 2` | `Titre - nn - titre d'épisode` si le titre est celui du dossier |  | U | non (laissé tel quel) | oui |
| `Dragon Ball Z Kai - 12.mkv` |  | `Titre - nn` sans groupe ni marqueur : **inconnu volontaire** (n'est plus pris pour un clip) | un film `Mission - 12` ou une chanson ne doivent pas devenir des séries | S U | non (mal reconnu) | oui |
| `[SubsPlease] Spy x Family - 12 (1080p) [A1B2C3D4].mkv` |  | groupe entre crochets en tête + `Titre - nn` (2 à 4 chiffres, `v2` toléré) = numérotation absolue | somme de contrôle `[A1B2C3D4]` retirée | S T | oui | oui |
| `[Group] Mob Psycho 100 - 03 [1080p].mkv` |  | le « 100 » du titre n'est pas le numéro : c'est le dernier `- nn` qui compte |  | S | oui | oui |
| `[Judas] Vinland Saga - S02E05.mkv` |  | groupe + `SnnEnn` : le marqueur classique l'emporte |  | S | oui | oui |
| `Naruto Episode 220 VOSTFR.mkv` |  | `Episode n` seul : numérotation absolue, étiquette de langue conservée |  | S | oui | oui |
| `Friends.S02E01E02.mkv` |  | épisodes enchaînés `E01E02` |  | K P J S | oui | oui |
| `Friends S02E01-E02.mkv` |  | `E01-E02` |  | K P J S | oui | oui |
| `Friends S02E01-02.mkv` |  | `E01-02` |  | K P J S | oui | oui |
| `Friends S02E01+E02.mkv` |  | `E01+E02` |  | P S | non (mal reconnu) | oui |
| `Friends 2x01-02.mkv` |  | `2x01-02` |  | K S | oui | oui |
| `Friends.S02E01E02E03.mkv` |  | plage = premier et dernier numéro | nom produit `S02E01-E03` | K P J S | oui | oui |
| `Doctor Who S00E01 Time Crash.mkv` |  | `S00` = saison 0 (spéciaux) | dossier `Saison 00` | K P J T | oui | oui |
| `Fairy Tail OVA 2.mkv` |  | `OVA`/`OAD`/`ONA` [n] → saison 0, épisode n (1 par défaut) | jamais avec une année derrière | S T | non (laissé tel quel) | oui |
| `Black Mirror SP01.mkv` |  | `SP`nn → saison 0 |  | K P | non (laissé tel quel) | oui |
| `Episode 01.mkv` | `Sherlock/Specials` | dossier `Specials` / `Spéciaux` / `Extras` = saison 0 |  | K P J T | non (mal reconnu) | oui |
| `Special.Forces.2011.1080p.BluRay.mkv` |  | `Special` seul **n'est pas** un spécial : il faut un nombre derrière et pas d'année | `Special Forces`, `Special Edition`, `Special 26 (2013)` restent des films (testés) | U | oui | oui |
| `The Daily Show 2024.03.15.mp4` |  | date `aaaa mm jj` (séparateur point, tiret, espace) = identité de l'épisode ; dossier « Saison <année> » | confiance 0.8 | S P | non (mal reconnu) | oui |
| `The Daily Show 15-03-2024.mp4` |  | date `jj-mm-aaaa` **seulement si** le jour ou le mois est > 12 | `03-04-2024` n'est jamais deviné (inconnu) ; une date impossible (31-02) est refusée | S | non (laissé tel quel) | oui |
| `The Daily Show 20240315.mp4` |  | date compacte `aaaammjj` valide, derrière un titre | les noms d'appareil photo (`VID_20240315_…`) sont traités avant | S U | non (laissé tel quel) | oui |
| `Kill Bill 2003 CD1.avi` |  | `CD`n / `Disc`n n'importe où après le titre, ou `Part n` / `Pt n` / `Partie n` **après l'année** = partie ; nom produit `Titre (année) - part1` | `Dune Part Two 2024` et `Deathly Hallows Part 2 2011` gardent « Part » dans le titre (testés) | K P J | non (mal reconnu) | oui |
| `Heat 1995 Pt.2.avi` |  | `Pt.` n après l'année |  | K J | non (mal reconnu) | oui |

### Langues

| Exemple | Dossier | Règle (expression dérivée) | Remarques | Source | Avant | Après |
|---|---|---|---|---|---|---|
| `Narcos Temporada 2 Capítulo 5.mkv` |  | es : `Temporada` n `Capítulo`/`Capitulo`/`Cap` n |  | U | non (laissé tel quel) | oui |
| `Narcos T02 Cap 05.mkv` |  | es : `T`nn juste devant `Cap` |  | U | non (laissé tel quel) | oui |
| `Dark Temporada 3 Episódio 4.mkv` |  | pt : `Temporada` n `Episódio` n |  | U | non (laissé tel quel) | oui |
| `Dark Staffel 3 Folge 4.mkv` |  | de : `Staffel` n `Folge` n |  | U | non (laissé tel quel) | oui |
| `Gomorra Stagione 2 Episodio 6.mkv` |  | it : `Stagione` n `Episodio` n |  | U | non (laissé tel quel) | oui |
| `Penoza Seizoen 2 Aflevering 6.mkv` |  | nl : `Seizoen` / `Aflevering` |  | U | non (laissé tel quel) | oui |
| `Bonusfamiljen Säsong 2 Avsnitt 6.mkv` |  | sv : `Säsong` / `Avsnitt` |  | U | non (laissé tel quel) | oui |
| `Wiedźmin Sezon 2 Odcinek 6.mkv` |  | pl : `Sezon` / `Odcinek` |  | U | non (laissé tel quel) | oui |
| `Diriliş 2. Sezon 6. Bölüm.mkv` |  | tr : `Sezon`, `Bölüm`, ordre « n. Sezon n. Bölüm » |  | U | non (laissé tel quel) | oui |
| `Игра престолов 2 сезон 6 серия.mkv` |  | ru : `Сезон`/`сезон`, `Серия`/`серия`, nombre avant ou après |  | U | non (laissé tel quel) | oui |
| `المسلسل الموسم 2 الحلقة 6.mkv` |  | ar : `الموسم` n `الحلقة` n (pas de frontière de mot dans cette écriture) | le titre arabe est conservé tel quel | U | non (laissé tel quel) | oui |
| `权力的游戏 第2季第6集.mkv` |  | zh : `第`n`季` `第`n`集` (espaces facultatifs) |  | U | non (laissé tel quel) | oui |
| `進撃の巨人 第2期 第6話.mkv` |  | ja : `第`n`期` `第`n`話` (`话` accepté) |  | U | non (laissé tel quel) | oui |
| `오징어 게임 시즌 2 6화.mkv` |  | ko : `시즌` n n`화` |  | U | non (laissé tel quel) | oui |
| `Les Revenants Saison II Episode 6.mkv` |  | chiffres romains I à XX **seulement derrière un mot « saison »** | `Season of the Witch`, `Saison 2021` restent des films | U | non (mal reconnu) | oui |
| `Episode 6.mkv` | `Narcos/Temporada 2` | dossier parent : `Temporada`, `Staffel`, `Stagione`, `Saison`, `Season`, `S3`, `Saison II` |  | K P J | non (mal reconnu) | oui |
| `06.mkv` | `Les Revenants/Saison II` | nombre nu + dossier de saison |  | K P J | non (laissé tel quel) | oui |

### Films

| Exemple | Dossier | Règle (expression dérivée) | Remarques | Source | Avant | Après |
|---|---|---|---|---|---|---|
| `Inception [2010].mkv` |  | année 1900 → année en cours + 1 entre `()` `[]` ou séparateurs |  | K P J | oui | oui |
| `Inception.2010.mkv` |  | idem, séparée par des points |  | K P J | oui | oui |
| `Toy Story 4 (2019).mkv` |  | le numéro de suite fait partie du titre |  | K P J | oui | oui |
| `Rocky.III.1982.720p.mkv` |  | chiffres romains dans le titre conservés en majuscules |  | K P | oui | oui |
| `Kill Bill Vol. 2 2004.avi` |  | `Vol. 2` dans le titre (le point disparaît) |  | U | oui | oui |
| `Fast X Part Two 2025 1080p.mkv` |  | `Part Two` (en lettres) avant l'année = titre |  | U | oui | oui |
| `Blade Runner 1982 Director's Cut 1080p.mkv` |  | éditions `Director's Cut`, `Directors Cut`, `Extended (Cut)`, `Unrated`, `Uncut`, `Remastered`, `IMAX`, `3D`, `Final Cut`, `Theatrical (Cut)` : retirées du nom, **gardées dans l'identité** | deux éditions d'un même film ne sont jamais des « doublons » (le nom proposé reste sans l'édition, conforme aux jeux gelés existants ; une collision de nom est déjà levée par `Planner.uniqueName`) | P J | oui | oui |
| `Avatar 2009 3D HSBS 1080p.mkv` |  | `3D` + `HSBS` retirés |  | U | oui | oui |
| `300 (2006).mkv` |  | titre fait d'un nombre, suivi d'une année |  | K P J | oui | oui |
| `1917.2019.1080p.mkv` |  | nombre-titre en tête, année derrière : la **dernière** année est l'année |  | U | oui | oui |
| `21 Jump Street (2012).mkv` |  | titre commençant par un nombre |  | K P J | oui | oui |
| `2001 A Space Odyssey 1968.mkv` |  | `2001` en tête n'est jamais l'année si une autre année suit |  | U | oui | oui |
| `1917.mkv` |  | nombre seul sans année ni étiquette : inconnu (jamais série, jamais année) |  | U | oui | oui |
| `101.mkv` |  | idem |  | U | oui | oui |

### Bruit de téléchargement

| Exemple | Dossier | Règle (expression dérivée) | Remarques | Source | Avant | Après |
|---|---|---|---|---|---|---|
| `Prison.Break.S01E08.VFF.720p.WEB.mkv` |  | langues fortes : `VF`, `VFF`, `VFQ`, `VFI`, `VF2`, `TRUEFRENCH`, `VOSTFR`, `SUBFRENCH`, `MULTi`… | `FRENCH` seul n'est une étiquette que suivie d'étiquettes ou de la fin (`The French Connection`) | U | oui | oui |
| `Prison.Break.S01E08.1080p.AMZN.WEB-DL.DDP5.1.H.264-NTb.mkv` |  | plateformes `AMZN NF DSNP HMAX HULU ATVP PCOK PMTP STAN CRAV SHO`, sources, codecs, canaux audio, groupe `-XXX` |  | S U | oui | oui |
| `Prison.Break.S01E08.1080p.10bit.AV1.mkv` |  | `10bit`, `AV1`, `HEVC`, `x265`, `Atmos` |  | S U | oui | oui |
| `Prison.Break.S01E08.PROPER.720p.HDTV.x264-DIMENSION.mkv` |  | `PROPER`, `REPACK`, `iNTERNAL` : mots de publication |  | S U | oui | oui |
| `Inception.2010.1080p.BluRay.DTS-HD.MA.5.1.x264.mkv` |  | `DTS-HD.MA.5.1`, `TrueHD.7.1` |  | S U | oui | oui |
| `[NetNaija.com] Blood Sisters S01E02.mp4` |  | sites : préfixe `[site]`, `site.com -`, `site -`, `site.` (point), suffixe `- site`, `(site.com)`, `[site]` | liste fermée (NetNaija, o2tvseries, TFPDL, 9jaRocks, Waploaded, torrent9, YTS, RARBG… ) + tout `xxx.com/.net/.org…` | U | oui | oui |
| `TFPDL.Lionheart.2018.720p.WEB.mkv` |  | nom de site collé au titre par un point | nouveau : le point est un séparateur de site | U | non (mal reconnu) | oui |
| `@NollyMovies Blood Sisters S01E02.mp4` |  | `@canal` (3 à 32 lettres/chiffres/`_`), jamais une adresse e-mail |  | U | non (mal reconnu) | oui |
| `Blood Sisters S01E02 t.me/nollyfilms.mp4` |  | `t.me/canal`, `t.me_canal_` en tête | un canal contenant `_` laisse un mot : limite connue | U | non (mal reconnu) | oui |
| `Forwarded Lionheart 2018 720p.mp4` |  | préfixe `Forwarded` / `Transféré` / `Fwd` |  | U | non (mal reconnu) | oui |
| `VID-20240402-WA0044.mp4` |  | `VID-aaaammjj-WAnnnn`, `IMG-`, `AUD-`, `PTT-` : vidéo / photo / audio WhatsApp | traité avant tout le reste, jamais film ni série | U | oui | oui |
| `WhatsApp Video 2024-04-02 at 08.05.09.mp4` |  | `WhatsApp Video aaaa-mm-jj at hh.mm.ss` (+ `(1)`, AM/PM) |  | U | oui | oui |
| `video_2024-03-15_14-22-11.mp4` |  | `video_aaaa-mm-jj_hh-mm-ss` = Telegram |  | U | oui | oui |

### Sous-titres

| Exemple | Dossier | Règle (expression dérivée) | Remarques | Source | Avant | Après |
|---|---|---|---|---|---|---|
| `Prison.Break.S01E08.fr.srt` |  | suffixe de points en fin de nom : langue connue (2 lettres) … | le sous-titre suit sa vidéo | J K P | oui | oui |
| `Prison.Break.S01E08.eng.forced.srt` |  | … ou 3 lettres (ISO 639-2/B ou T), nom de langue ; + drapeau `forced` | nom produit `….en.forced.srt` | J | non (mal reconnu) | oui |
| `Prison.Break.S01E08.fr.sdh.srt` |  | drapeaux `sdh`, `hi`, `cc` → `sdh`, `default` |  | J P | non (mal reconnu) | oui |
| `Prison.Break.S01E08.pt-BR.ass` |  | région `pt-BR`, `zh-CN`, `zh-Hans` |  | J | non (mal reconnu) | oui |
| `Inception.2010.1080p.BluRay.kor.srt` |  | codes fr en es pt de it nl sv pl tr ru ar zh ja ko (+ 3 lettres) | un mot de titre n'est une langue **que derrière un point** : `Stranger Than It.srt` reste un titre | J | non (mal reconnu) | oui |
| `Inception.2010.1080p.BluRay.vtt` |  | `.vtt` reconnu comme sous-titre |  | J | oui | oui |

### Musique

| Exemple | Dossier | Règle (expression dérivée) | Remarques | Source | Avant | Après |
|---|---|---|---|---|---|---|
| `01 - Last Last.mp3` |  | `nn - Titre`, `nn. Titre`, `nn Titre`, `nn_Titre` | nom produit `01 – Titre` | K J | oui | oui |
| `A1 Burna Boy - Last Last.mp3` |  | face de disque `A1` devant `Artiste - Titre` |  | U | oui | oui |
| `Burna Boy ft. Tems - Last Last.mp3` |  | `ft` / `ft.` / `feat` / `feat.` / `featuring` → `feat.` |  | J | oui | oui |
| `Burna Boy - Last Last (feat. Tems).mp3` |  | invité entre parenthèses conservé |  | J | oui | oui |
| `Burna Boy - Last Last (Live).mp3` |  | `(Live)`, `(Remix)` font partie du titre |  | U | oui | oui |
| `Burna Boy - Last Last (Official Audio).mp3` |  | `(Official Audio)`, `[Lyrics]`, `(320kbps)` retirés |  | U | oui | oui |
| `03 - Last Last.mp3` | `Burna Boy/CD1` | dossier `CD1` / `Disc 2` : sans effet sur le nom | limite : deux disques avec la même piste ⇒ `Planner.uniqueName` ajoute `(2)` | K J | oui | oui |

### Cours

| Exemple | Dossier | Règle (expression dérivée) | Remarques | Source | Avant | Après |
|---|---|---|---|---|---|---|
| `01. Introduction.mp4` | `Cours de Python` | `nn. Titre` + dossier à mot de cours → cours, matière du dossier |  | U | oui | oui |
| `Section 1 - Lecture 2 - Variables.mp4` |  | `Section n - Lecture n` (mot fort) |  | U | oui | oui |
| `Module 3 - Les fonctions.mp4` | `Formation Python` | `Module n` + dossier `Formation` |  | U | oui | oui |
| `Chapitre 9 : Les dérivées.mp4` | `Cours de Maths` | `Chapitre n : Titre` (`:` remplacé) |  | U | oui | oui |
| `Leçon 5 - Les fractions.mp4` |  | `Leçon n` | sans matière reconnue : dossier `Cours` seul | U | oui | oui |

### Séparateurs et casse

| Exemple | Dossier | Règle (expression dérivée) | Remarques | Source | Avant | Après |
|---|---|---|---|---|---|---|
| `prison_break_s01e08.avi` |  | minuscules ou majuscules seules → casse de titre ; mélange conservé |  | U | oui | oui |
| `Prison     Break    S01E08.avi` |  | espaces multiples réduits |  | U | oui | oui |
| `Prison-Break-S01E08.avi` |  | nom fait seulement de tirets → espaces |  | U | oui | oui |
| `Prison.Break.S01E08.AVI` |  | extension en majuscules → minuscules |  | U | oui | oui |
| `Sœurs de Yaoundé S01E01.mkv` |  | ligature `œ` conservée (clé de regroupement : `oe`) |  | U | oui | oui |
| `Abbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb S01E08.avi` |  | longueur : nom ≤ 150, dossier ≤ 80 par segment |  | U | oui | oui |


## 4. Règles ajoutées (résumé)

1. **Sous-titres** (`SubtitleTags`) : suffixe de points en fin de nom (`.fr`, `.eng.forced`, `.fr.sdh`, `.pt-BR`, 3 lettres), canonique `fr`, `en.forced`, `fr.sdh`, `pt-BR` ; jamais pour un mot de titre sans point.
2. **Mots de saison / épisode** en 12 langues + chiffres romains derrière un mot de saison + dossiers parents (`Saison II`, `Temporada 2`, `Specials`).
3. **Bruit de messageries** : `@canal`, `t.me/…`, `Forwarded` ; sites collés par un point (`TFPDL.Titre`) ; plateformes (`ATVP`, `PCOK`, `PMTP`, `STAN`, `CRAV`, `SHO`).
4. **CamelCase** : `PrisonBreakS01E08`, `HowIMetYourMother_S02E22`.
5. **`Titre - nn - Épisode`** dans un dossier de la même série ; `#04` / `[04]` en dossier de saison ; `Titre - 12` n'est plus pris pour un clip.
6. **Dates** (`Parsed.date`) : séries quotidiennes ; jour/mois ambigus jamais devinés.
7. **Spéciaux** : `S00E01`, `OVA n`, `SP nn`, dossier `Specials`.
8. **Numérotation à 3-4 chiffres** avec preuve de série.
9. **Parties et éditions de films** (`Parsed.part`, `Parsed.edition`) : `CD1` / `Part 2` → `Titre (année) - part1` ; l'identité (doublons) distingue éditions et parties.
10. **Défaut trouvé par le garde-fou** : `Folge 2012.mkv` devenait l'épisode 2012 d'une série sans titre ; un nombre de 1900 à 2100 n'est plus un épisode.
11. **Regroupement** (§ 7) et **sécurité** (§ 6).

## 5. Méthode de mesure et mesures

**Jeux** (voir aussi `docs/LIBRARY-AGENT.md` § 3) :

| Jeu | Contenu | Rôle |
|---|---|---|
| **DEV-3** | 267 cas écrits à la main (`Dev3Hand`) + 1 500 générés (`Dev3Gen`, graine 31, titres `Pools3.DEV`) | **sert à régler** ; échecs dans `build/reports/naming-dev3-failures.txt` |
| **GELÉ-3** | `naming/frozen-3.tsv` (SHA-256 vérifié par `Frozen3CorpusTest`) : 151 écrits à la main (`Frozen3Hand`, autres titres, autres formes) + 699 générés (`Dev3Gen`, graine 20261003, titres `Pools3.FROZEN`, disjoints de DEV-3 et des anciens jeux) | **jamais utilisé pour régler une règle** ; le test n'affiche que les totaux par famille |
| GELÉ, GELÉ-DUR (existants) | 2 087 et 812 cas | jamais utilisés pour régler (inchangés) |

Les attendus sont écrits **d'après les vraies métadonnées** (titre, saison, épisode, année, date) du cas, jamais d'après la réponse du moteur. GELÉ-3 a été écrit **et mesuré avant toute règle** de ce travail (premier commit de la branche) ; deux erreurs d'attendu des générateurs (extension en majuscules, titre collé sans « by ») ont été corrigées **avant** la première règle et GELÉ-3 régénéré (historique Git). Un test interdit qu'un cas DEV-3 écrit à la main soit aussi un cas gelé.

**Verdicts** (`Dev3Eval`) : OK ; **omission** (le moteur répond « inconnu » : fichier laissé tel quel, sans dommage) ; **faux** (autre type, autre titre, bruit gardé, saison ou épisode faux) dont **mauvais type** (un film pris pour une série ou l'inverse : le plus grave).

| Mesure | Avant (`integration/agents`) | Après (`claude/naming-patterns`) |
|---|---|---|
| **GELÉ-3** (jeu gelé, honnête) | 447/850 = 52.6 %  (omissions 94, faux 309 = 36.4 %, dont mauvais type 141) | 844/850 = 99.3 %  (omissions 4, faux 2 = 0.2 %, dont mauvais type 0) |
| dont écrits à la main | 103/151 = 68.2 % (omissions 20, faux 28) ; générés : 344/699 = 49.2 % (omissions 74, faux 281) | 149/151 = 98.7 % (omissions 0, faux 2) ; générés : 695/699 = 99.4 % (omissions 4, faux 0) |
| DEV-3 (réglé dessus : **pas une mesure honnête**) | 945/1757 = 53.8 %  (omissions 210, faux 602 = 34.3 %, dont mauvais type 251) | 1757/1757 = 100.0 %  (omissions 0, faux 0 = 0.0 %, dont mauvais type 0) |
| GELÉ (existant, formes courantes) | 2 073 / 2 087 = 99,3 % | 2074/2087 = 99.4 % |
| GELÉ-DUR (existant, formes inhabituelles) | 799 / 812 = 98,4 % | 799/812 = 98.4 % |
| DEV (existant, réglé dessus) | 5 251 / 5 252 | 5 251 / 5 252 (inchangé) |

**Par famille, GELÉ-3** (avant, après ; « omis. » = omissions, « faux » = faux, « mauvais type » = film pris pour série ou l'inverse) :

| Famille | Avant | Après |
|---|---|---|
| course | 29 / 30 = 96.7 % (omis.   0   faux   1 mauvais type 0) | 29 / 30 = 96.7 % (omis.   0   faux   1 mauvais type 0) |
| ep-anime | 47 / 50 = 94.0 % (omis.   0   faux   3 mauvais type 3) | 48 / 50 = 96.0 % (omis.   2   faux   0 mauvais type 0) |
| ep-bare | 1 / 3 = 33.3 % (omis.   2   faux   0 mauvais type 0) | 3 / 3 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| ep-date | 1 / 57 = 1.8 % (omis.  30   faux  26 mauvais type 26) | 57 / 57 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| ep-forms | 92 / 113 = 81.4 % (omis.   0   faux  21 mauvais type 0) | 113 / 113 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| ep-number | 4 / 51 = 7.8 % (omis.   0   faux  47 mauvais type 47) | 51 / 51 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| ep-part | 0 / 3 = 0.0 % (omis.   0   faux   3 mauvais type 0) | 3 / 3 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| ep-special | 13 / 26 = 50.0 % (omis.  12   faux   1 mauvais type 0) | 26 / 26 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| film-edition | 7 / 7 = 100.0 % (omis.   0   faux   0 mauvais type 0) | 7 / 7 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| film-numtitle | 49 / 49 = 100.0 % (omis.   0   faux   0 mauvais type 0) | 49 / 49 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| film-seq | 42 / 42 = 100.0 % (omis.   0   faux   0 mauvais type 0) | 42 / 42 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| film-year | 44 / 96 = 45.8 % (omis.   0   faux  52 mauvais type 0) | 96 / 96 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| folder | 18 / 53 = 34.0 % (omis.  27   faux   8 mauvais type 0) | 53 / 53 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| lang-words | 1 / 83 = 1.2 % (omis.  13   faux  69 mauvais type 65) | 83 / 83 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| music | 41 / 41 = 100.0 % (omis.   0   faux   0 mauvais type 0) | 41 / 41 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| noise | 14 / 14 = 100.0 % (omis.   0   faux   0 mauvais type 0) | 14 / 14 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| noise-africa | 8 / 8 = 100.0 % (omis.   0   faux   0 mauvais type 0) | 8 / 8 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| noise-chat | 4 / 9 = 44.4 % (omis.   0   faux   5 mauvais type 0) | 9 / 9 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| sep | 18 / 41 = 43.9 % (omis.  10   faux  13 mauvais type 0) | 38 / 41 = 92.7 % (omis.   2   faux   1 mauvais type 0) |
| subs | 14 / 74 = 18.9 % (omis.   0   faux  60 mauvais type 0) | 74 / 74 = 100.0 % (omis.   0   faux   0 mauvais type 0) |


**Par famille, DEV-3** (réglé dessus) :

| Famille | Avant | Après |
|---|---|---|
| course | 58 / 58 = 100.0 % (omis.   0   faux   0 mauvais type 0) | 58 / 58 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| ep-anime | 110 / 111 = 99.1 % (omis.   0   faux   1 mauvais type 1) | 111 / 111 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| ep-bare | 3 / 8 = 37.5 % (omis.   3   faux   2 mauvais type 1) | 8 / 8 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| ep-date | 1 / 99 = 1.0 % (omis.  58   faux  40 mauvais type 40) | 99 / 99 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| ep-forms | 199 / 244 = 81.6 % (omis.   1   faux  44 mauvais type 0) | 244 / 244 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| ep-multi | 6 / 7 = 85.7 % (omis.   0   faux   1 mauvais type 0) | 7 / 7 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| ep-number | 3 / 98 = 3.1 % (omis.   0   faux  95 mauvais type 95) | 98 / 98 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| ep-part | 2 / 7 = 28.6 % (omis.   0   faux   5 mauvais type 0) | 7 / 7 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| ep-special | 32 / 70 = 45.7 % (omis.  37   faux   1 mauvais type 0) | 70 / 70 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| film-edition | 12 / 12 = 100.0 % (omis.   0   faux   0 mauvais type 0) | 12 / 12 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| film-numtitle | 104 / 104 = 100.0 % (omis.   0   faux   0 mauvais type 0) | 104 / 104 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| film-seq | 96 / 96 = 100.0 % (omis.   0   faux   0 mauvais type 0) | 96 / 96 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| film-year | 98 / 240 = 40.8 % (omis.   0   faux 142 mauvais type 0) | 240 / 240 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| folder | 33 / 112 = 29.5 % (omis.  60   faux  19 mauvais type 0) | 112 / 112 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| lang-words | 1 / 153 = 0.7 % (omis.  27   faux 125 mauvais type 114) | 153 / 153 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| music | 100 / 100 = 100.0 % (omis.   0   faux   0 mauvais type 0) | 100 / 100 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| noise | 22 / 22 = 100.0 % (omis.   0   faux   0 mauvais type 0) | 22 / 22 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| noise-africa | 10 / 11 = 90.9 % (omis.   0   faux   1 mauvais type 0) | 11 / 11 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| noise-chat | 8 / 14 = 57.1 % (omis.   0   faux   6 mauvais type 0) | 14 / 14 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| sep | 22 / 68 = 32.4 % (omis.  24   faux  22 mauvais type 0) | 68 / 68 = 100.0 % (omis.   0   faux   0 mauvais type 0) |
| subs | 25 / 123 = 20.3 % (omis.   0   faux  98 mauvais type 0) | 123 / 123 = 100.0 % (omis.   0   faux   0 mauvais type 0) |


**Comment lire ces chiffres.**
- La partie **générée** de GELÉ-3 réutilise les **gabarits** de DEV-3 avec d'autres titres et une autre graine : elle mesure « une forme déjà vue sur d'autres titres », pas « une forme jamais vue » ; ses 99 % sont **optimistes**. La partie **écrite à la main** est plus indépendante (98,7 %), mais l'auteur est le même que celui des règles.
- Les 6 échecs restants de GELÉ-3 n'ont pas été examinés un par un (règle du jeu gelé). Deux sont connus parce que leur **attendu** est discutable, pas le moteur : un nom de 120 « B » (le moteur le remet en casse de titre) et « Leçon 3 - Les limites » sans dossier (aucune matière reconnue, dossier `Cours`).
- Les formes **jamais prévues** (un site inconnu, une langue non listée) ne sont pas mesurées : il faut s'attendre à moins sur une bibliothèque réelle (`docs/LIBRARY-AGENT.md` § 9, point 1 : passer l'assistant en lecture seule et relever les erreurs).
- Les GELÉ et GELÉ-DUR existants n'ont pas servi à régler une règle. Une régression de GELÉ-DUR (−19 cas) a été **vue dans son total** pendant le travail ; la cause (un nombre devant « Saison » pris pour une saison à la turque `2. Sezon`) a été **retrouvée avec le jeu DEV** (`naming-dev-failures.txt`), pas avec les cas gelés, et corrigée : les deux totaux sont revenus à leur valeur (799) ou l'ont dépassée (2 074).

## 6. Sécurité des expressions (ReDoS)

`PathologicalNamesTest` fait passer le moteur entier (`NameParser.parse` avec et sans durée, `Namer.propose`, `SeriesClassifier.folderFor`) sur :
- ≈ 990 noms **pathologiques** de 250 caractères (une unité répétée : espaces, points, `[`, `(`, `S01E01`, `1x01-`, ` - 1`, marques combinantes, `%41`, `t.me/`, `第`, `الموسم`…, chacune avec 7 extensions, crochets imbriqués, centaines de variantes presque valides) ;
- des **dossiers** pathologiques (250 niveaux, `Saison ` répété) ;
- **6 000 mélanges aléatoires** de 85 « briques » du moteur (graine fixe), avec dossier une fois sur cinq ;
- chaque appel sous un **délai maximal de 2 s** (`Future.get`) : une expression à retour arrière catastrophique ferait échouer le test au lieu de bloquer la compilation.

Résultat mesuré : le pire nom met **194 ms** (le premier, JIT à froid) ; les 6 000 mélanges durent 13 s au total (≈ 2 ms par nom, 4 passes chacun), le pire 14 ms. **Aucune expression n'a eu besoin d'être remplacée** ; les expressions ajoutées n'emploient ni quantificateur imbriqué ni alternance à préfixe commun (bornes `{1,n}` partout). Limite : ce n'est pas une preuve formelle ; `java.util.regex` n'a pas de délai intégré, donc **toute nouvelle expression doit ajouter ses cas** à ce test.

## 7. Regroupement des titres pour « Titre / Saison NN »

- **Clé de regroupement** (`Text.groupKey`, `Parsed.groupKey`) : la clé de correspondance (minuscules, sans accent) **sans espaces** : `Prison Break`, `Prison.Break`, `PrisonBreak`, `Prison Break (2005)`, `prison break` → `prisonbreak` → **un seul dossier** (`SeriesClassifier.plan`), écrit comme l'orthographe la plus fréquente. Test : `SeriesGroupingTest`.
- **Deux séries de même nom jamais fusionnées** : si la bibliothèque contient deux années (`The Flash (1990)` / `(2014)`, `Doctor Who` 1963 / 2005), chaque année a son dossier `Titre (année)` ; un fichier **sans année** dont la saison et l'épisode existent déjà sous une année reste à part. Sans ambiguïté, le nom du dossier est **inchangé** (`The Flash/Saison 02` pour un seul `The Flash (2014)`).
- **Table d'alias facultative et vérifiable** (`SeriesAliases`, ressource `castbridge/library/series-aliases.tsv`, 10 entrées : `La Casa de Papel` = `Money Heist`, `Dix pour Cent` = `Call My Agent`, `Le Bureau des Légendes` = `The Bureau`, `Dr House` = `House`, `Le Trône de Fer` = `Game of Thrones`, `Les Simpson`, `Esprits Criminels`, `L'Attaque des Titans`, `Boku no Hero Academia`, `Kimetsu no Yaiba`). Elle ne sert **que** si les deux titres sont présents dans la même bibliothèque (jamais de renommage d'un fichier seul) et n'est appliquée que si l'appelant la passe (`plan(files, fr, SeriesAliases.builtin())`) : **par défaut rien ne change**. Règles vérifiées par test : pas d'année dans un titre, aucun titre listé deux fois, pas de titre partagé par deux séries (`Les Revenants` / `The Returned` désignent deux séries : volontairement absent), et des paires qui ne doivent jamais fusionner (`CSI` / `CSI Miami`, `The Office US / UK`, `House` / `House of Cards`…).
- L'identité d'un épisode (`Parsed.identity`, doublons de qualité) utilise aussi cette clé : `PrisonBreak S01E01` et `Prison Break S01E01` sont reconnus comme le même épisode.

## 8. Limites connues

- **Inconnu volontaire** (jamais deviné) : `Titre - 12` sans groupe ni marqueur ; `Titre #04` sans dossier ; `Titre 03-04-2024` (jour/mois ambigus) ; `101` seul ; `Special` sans nombre ; un nombre 3-4 chiffres sans étiquette de source télé ; `Titre 108 BluRay`.
- Les **éditions** (`Director's Cut`, `Extended`…) sont reconnues et retirées du nom proposé (comme les jeux gelés existants l'exigent) : deux éditions du même film reçoivent le même nom, que `Planner.uniqueName` rend unique (`(2)`) ; elles ne sont **jamais prises pour des doublons**. Une étape possible : écrire l'édition dans le nom (`Titre (année) {edition-…}`), ce qui change les noms produits (à décider par le propriétaire).
- **Musique** : `CD1/` / `Disc 2` n'entrent pas dans le nom ; deux disques avec la même piste reçoivent `(2)`.
- **CamelCase** : `RaisedByWolves` redonne `Raised By Wolves` (les mots courts en minuscule ne se retrouvent pas) ; jamais appliqué hors marqueur de série.
- **Messageries** : un canal `t.me_mon_canal_` à tirets bas laisse un mot ; `@canal` collé sans espace n'est pas retiré.
- **Langues** : les mots listés au § 3 ; pas d'hébreu, hindi, thaï, vietnamien, indonésien… Les titres non latins sont conservés tels quels (pas de translittération). Le code de langue des sous-titres ne couvre que les 15 langues listées.
- **Plateformes** : liste fermée ; `MAX`, `iT` (ambigus avec des mots de titre) ne sont pas des étiquettes.
- Les **animés à numérotation absolue sans groupe** (`One Piece - 1045.mkv`) restent inconnus.
- **Non compilé / non essayé** : `:sender` et `:receiver` (le plugin Android 8.9.1 est introuvable dans le cloud) ; rien n'a été essayé sur la TV ni sur le téléphone ; `gradle :core:test` complet : voir `docs/agent-reports/naming-patterns.md` (banc « core seul »).

## 9. Ajouter une forme (méthode)

1. Écrire d'abord le cas (nom, dossier, nom et dossier attendus calculés **d'après les métadonnées**, pas d'après le moteur) dans `Dev3Hand` (DEV-3) ; ne **jamais** toucher `frozen*.tsv`.
2. Regarder `build/reports/naming-dev3-failures.txt`, écrire la **plus petite** règle qui exige une preuve, dans `NameParser`.
3. Ajouter au moins un cas de `FalsePositiveTest` qui prouve qu'elle ne prend pas un film pour une série, et un nom pathologique à `PathologicalNamesTest` si elle ajoute une expression.
4. `tools/core-harness/run.sh :core:test --tests 'castbridge.core.library.agent.*'` : GELÉ, GELÉ-DUR, GELÉ-3 ne doivent pas baisser.
