# Couverture du contenu « Apprendre » — maternelle et primaire francophone

*Généré le 2026-10-01 (branche `claude/content-learn-primaire`). Contenu **bêta** : tout est rédigé avec une IA, original, statut
`draft` (« Brouillon — à relire par un enseignant »), jamais `reviewed`/`validated`. Validation prévue par le propriétaire
3 mois après la distribution aux bêta-testeurs.*

## Comment lire ce document
- **Programme officiel** : le texte officiel MINEDUB n'a pas pu être consulté pendant la rédaction. Les « thèmes du programme »
  de chaque tableau sont **reconstitués** de mémoire (programmes connus du primaire francophone) et doivent être
  confrontés au programme officiel. Les pourcentages de couverture sont des **estimations des rédacteurs**, pas des mesures.
- Un pack = un niveau × une matière (un lot de distribution par classe est donc possible, chaque pack pèse de 20 à 250 ko).
- Matières du catalogue : maths, français, anglais (initiation), sciences, histoire-géo-ECM (histoire, géographie et éducation à
  la citoyenneté/morale), découverte du monde (maternelle). **EPS, éducation artistique, langues nationales** : aucune matière
  dans le catalogue actuel → non couverts (voir « Manques transversaux »).
- Chaque fiche porte des `reviewNotes` (points à faire vérifier) ; les exercices douteux sont marqués `review`.
- Validation : `LessonValidator` (via `learn-check`), puis `gradle :core:test --tests 'castbridge.core.*Learn*'` (voir HANDOFF).

## Totaux
**45 packs, 390 fiches, 3894 exercices (dont 1585 questions d'auto-évaluation), 15 épreuves blanches, 457 illustrations.**

## Vue d'ensemble par pack
| Pack | Examen | Niveau | Matière | Fiches | Exercices (dont auto-éval.) | Épreuves blanches | Illustrations | À vérifier |
|---|---|---|---|---|---|---|---|---|
| ps-decouverte | ? | PS | D?couverte du monde | 6 | 25 (0) | 0 | 2 | 7 |
| ps-francais | ? | PS | Fran?ais | 5 | 22 (0) | 0 | 5 | 7 |
| ps-maths | ? | PS | Math?matiques | 5 | 23 (0) | 0 | 17 | 6 |
| ms-francais | ? | MS | Fran?ais | 7 | 34 (0) | 0 | 7 | 7 |
| ms-maths | ? | MS | Math?matiques | 8 | 37 (0) | 0 | 25 | 10 |
| maternelle-decouverte | ? | MS | D?couverte du monde | 11 | 58 (0) | 0 | 33 | 13 |
| gs-decouverte | ? | GS | D?couverte du monde | 8 | 38 (0) | 0 | 5 | 10 |
| gs-english | ? | GS | Anglais | 6 | 27 (0) | 0 | 5 | 9 |
| gs-francais | ? | GS | Fran?ais | 8 | 38 (0) | 0 | 6 | 8 |
| gs-maths | ? | GS | Math?matiques | 9 | 43 (0) | 0 | 23 | 11 |
| sil-english | ? | SIL | Anglais | 5 | 55 (25) | 0 | 6 | 10 |
| sil-francais | ? | SIL | Fran?ais | 7 | 77 (35) | 0 | 8 | 14 |
| sil-histoire-geo | ? | SIL | Histoire-G?ographie-ECM | 5 | 55 (25) | 0 | 5 | 11 |
| sil-maths | ? | SIL | Math?matiques | 8 | 88 (40) | 0 | 15 | 17 |
| sil-sciences | ? | SIL | Sciences | 5 | 55 (25) | 0 | 6 | 10 |
| cp-english | ? | CP | Anglais | 7 | 77 (35) | 0 | 9 | 13 |
| cp-francais | ? | CP | Fran?ais | 13 | 143 (65) | 0 | 12 | 15 |
| cp-histoire-geo | ? | CP | Histoire-G?ographie-ECM | 7 | 77 (35) | 0 | 7 | 16 |
| cp-maths | ? | CP | Math?matiques | 14 | 154 (70) | 0 | 16 | 14 |
| cp-sciences | ? | CP | Sciences | 7 | 77 (35) | 0 | 7 | 13 |
| ce1-english | ? | CE1 | Anglais | 6 | 66 (30) | 0 | 7 | 12 |
| ce1-francais | ? | CE1 | Fran?ais | 16 | 176 (80) | 0 | 16 | 17 |
| ce1-histoire-geo | ? | CE1 | Histoire-G?ographie-ECM | 7 | 77 (35) | 0 | 8 | 19 |
| ce1-maths | ? | CE1 | Math?matiques | 15 | 165 (75) | 0 | 19 | 15 |
| ce1-sciences | ? | CE1 | Sciences | 7 | 77 (35) | 0 | 8 | 14 |
| ce2-english | ? | CE2 | Anglais | 6 | 66 (30) | 0 | 6 | 6 |
| ce2-francais | ? | CE2 | Fran?ais | 19 | 209 (95) | 0 | 19 | 22 |
| ce2-histoire-geo | ? | CE2 | Histoire-G?ographie-ECM | 7 | 77 (35) | 0 | 7 | 16 |
| ce2-maths | ? | CE2 | Math?matiques | 14 | 154 (70) | 0 | 14 | 16 |
| ce2-sciences | ? | CE2 | Sciences | 7 | 77 (35) | 0 | 7 | 9 |
| cm1-english | ? | CM1 | Anglais | 6 | 66 (30) | 0 | 6 | 18 |
| cm1-francais | ? | CM1 | Fran?ais | 12 | 132 (60) | 0 | 12 | 22 |
| cm1-histoire-geo | ? | CM1 | Histoire-G?ographie-ECM | 7 | 77 (35) | 0 | 7 | 32 |
| cm1-maths | ? | CM1 | Math?matiques | 12 | 132 (60) | 0 | 13 | 21 |
| cm1-sciences | ? | CM1 | Sciences | 8 | 88 (40) | 0 | 8 | 25 |
| cm2-english | ? | CM2 | Anglais | 7 | 77 (35) | 0 | 3 | 15 |
| cm2-francais | ? | CM2 | Fran?ais | 12 | 137 (60) | 1 | 5 | 13 |
| cm2-histoire-geo | ? | CM2 | Histoire-G?ographie-ECM | 8 | 88 (40) | 0 | 7 | 29 |
| cm2-maths | ? | CM2 | Math?matiques | 11 | 125 (55) | 1 | 10 | 12 |
| cm2-sciences | ? | CM2 | Sciences | 8 | 88 (40) | 0 | 5 | 21 |
| cep-english | CEP | CM2 | Anglais | 6 | 74 (30) | 2 | 6 | 12 |
| cep-francais | CEP | CM2 | Fran?ais | 11 | 135 (55) | 3 | 11 | 19 |
| cep-histoire-geo | CEP | CM2 | Histoire-G?ographie-ECM | 6 | 74 (30) | 2 | 6 | 15 |
| cep-maths | CEP | CM2 | Math?matiques | 12 | 146 (60) | 3 | 17 | 19 |
| cep-sciences | CEP | CM2 | Sciences | 9 | 108 (45) | 3 | 11 | 19 |

## Manques transversaux
- **EPS, éducation artistique, langues nationales, travail manuel/technologie** : pas de matière dans `LearnCatalog` (changement Kotlin hors périmètre) ; à ajouter côté plateforme avant d'écrire du contenu.
- **Cartes réelles, photos, audio enregistré, vidéos** : seulement des schémas dessinés (figures) et la synthèse vocale ; aucune ressource externe.
- **Épreuves blanches** : seulement dans les packs d'examen CEP (cep-*) et les bilans CM2 ; formats officiels du CEP (durée, barème, matières) à vérifier.
- **Histoire datée du Cameroun** : volontairement limitée aux repères les plus solides (flaggés `review`) ; pas de royaumes ni de personnages historiques détaillés faute de source sûre.
- **Programmes à confronter** : tous les thèmes ci-dessous sont reconstitués ; l'estimation de couverture réelle sera faite après comparaison avec les textes officiels.
- **Doublons de thèmes** : `cm2-*` (cours complet) et `cep-*` (révision orientée examen) se recoupent volontairement ; leurs identifiants sont distincts.

# Détail par classe et matière (fragments des rédacteurs)

## Maternelle (PS, MS, GS) — lot « maternelle »

Tous les contenus sont des **brouillons** (`status: draft`), rédigés avec une IA, à faire valider par un enseignant. Le programme
officiel de l'éducation maternelle (MINEDUB) n'a pas pu être consulté : les thèmes ci-dessous sont **reconstitués, à confronter
au programme officiel**. Les fiches sont courtes (6-10 min, lues à voix haute), avec 4 à 6 exercices (surtout QCM, appariements,
vrai/faux, comptage) et pas d'auto-évaluation (`selfCheck` vide). Aucune matière « EPS », « éducation artistique » ou « langues
nationales » n'existe dans le catalogue : ces thèmes sont notés comme manquants.

### PS — Découverte du monde (`ps-decouverte`)
| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Le corps, le visage | « Mon visage » | couvert |
| Les cinq sens | « Mes cinq sens » | couvert |
| Les couleurs | « Les couleurs » | couvert |
| Famille et école | « Ma famille et mon école » | couvert |
| Animaux familiers | « Les animaux de la ferme » | partiel |
| Hygiène | « Je me lave les mains » | partiel |
| Végétaux, matière (eau, sable), saisons, sécurité | — | manquant |

Existant : 6 fiches, 25 exercices, 2 illustrations. Manque : plantes, matière, temps qui passe, sécurité ; peu d'illustrations.

### PS — Mathématiques (`ps-maths`)
| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Nombres 1 à 3, puis 5 (dénombrer) | « Un, deux, trois », « Quatre et cinq » | couvert |
| Trier, classer (couleur, forme) | « Je trie » | partiel |
| Comparer des grandeurs (grand/petit, long/court) | « Grand, petit, long, court » | partiel |
| Formes (rond, carré, triangle) | « Rond, carré, triangle » | couvert |
| Repérage dans l'espace, suites, comparer des quantités | — | manquant |

Existant : 5 fiches, 23 exercices, 17 illustrations.

### PS — Français (`ps-francais`)
| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Langage oral : écouter, nommer (cris des animaux) | « Les cris des animaux » | partiel |
| Politesse, vie en groupe | « Les mots gentils » | couvert |
| Comptines | « Mes comptines » (comptine originale) | partiel |
| Prénom, première lettre | « La première lettre de mon prénom » | partiel |
| Graphisme (traits droits, ronds) | « Mes premiers traits » (reconnaissance seulement) | partiel |
| Vocabulaire de la classe et du corps, bruits, albums | — | manquant |

Existant : 5 fiches, 22 exercices, 5 illustrations. Le geste graphique ne peut pas être évalué à l'écran.

### MS — Mathématiques (`ms-maths`)
| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Nombres jusqu'à 5, puis 10 | « Les nombres de 1 à 5 », « de 6 à 10 » | couvert |
| Comparer des collections (plus, moins, autant) | « Plus, moins, autant » | couvert |
| Ajouter / enlever (petites collections) | « J'ajoute, j'enlève » | couvert |
| Trier, classer | « Trier et classer » | couvert |
| Suites logiques | « Les suites logiques » | couvert |
| Formes (rond, carré, triangle, rectangle) | « Rond, carré, triangle, rectangle » | couvert |
| Repérage dans l'espace (dessus, dessous, devant, derrière) | « Où est-ce ? » | partiel |
| Grandeurs et mesures, repérage dans le temps | — | manquant |

Existant : 8 fiches, 37 exercices, 25 illustrations.

### MS — Français (`ms-francais`)
| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Langage oral : phrases | « Je dis une phrase » | couvert |
| Compréhension d'une histoire écoutée | « J'écoute une histoire » (texte original) | couvert |
| Syllabes | « Les syllabes » | couvert |
| Rimes | « Les mots qui riment » | couvert |
| Lettres | « Les lettres de l'alphabet » | partiel |
| Prénom | « Mon prénom » | couvert |
| Graphisme (ponts, vagues, zigzag) | « Ponts, vagues et boucles » | partiel |
| Comptines, écriture du prénom, vocabulaire thématique | — | manquant |

Existant : 7 fiches, 34 exercices, 7 illustrations.

### MS — Découverte du monde (`maternelle-decouverte`, étendu à la version 2)
| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Couleurs | « Les couleurs » (existant) | couvert |
| Nombres 1 à 10 | « Compter jusqu'à 10 » (existant) | couvert |
| Formes | « Les formes » (existant) | couvert |
| Corps humain | « Mon corps » | couvert |
| Cinq sens | « Mes cinq sens » | couvert |
| Famille, école, vie en groupe | « Ma famille, mon école » | partiel |
| Animaux | « Les animaux de chez nous » | partiel |
| Fruits et légumes, alimentation | « Fruits et légumes » | partiel |
| Météo et saisons | « Le temps qu'il fait » | partiel |
| Hygiène | « Je suis propre, je suis en bonne santé » | couvert |
| Sécurité | « Je fais attention » | partiel |
| Plantes, matière (eau), repères temporels, patrimoine local | — | manquant |

Existant : 11 fiches (3 d'origine conservées + 8 nouvelles), 58 exercices, 33 illustrations.

### GS — Découverte du monde (`gs-decouverte`)
| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Repères temporels (semaine, journée) | « Les jours de la semaine », « Matin, midi, soir, nuit » | couvert |
| Vivant / non vivant | « Vivant ou pas vivant ? » | couvert |
| Plantes | « De la graine à la plante » | partiel |
| Matière : l'eau | « L'eau : flotte ou coule ? » | partiel |
| Santé et hygiène | « Je prends soin de mon corps » | couvert |
| Sécurité routière | « Traverser en sécurité » | partiel |
| Lieux du quartier, civisme | « Mon quartier : où va-t-on ? » | partiel |
| Saisons, corps humain, animaux, objets techniques | — (voir `maternelle-decouverte`) | manquant |

Existant : 8 fiches, 38 exercices, 5 illustrations.

### GS — Mathématiques (`gs-maths`)
| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Nombres jusqu'à 10 | « Les nombres de 1 à 10 » | couvert |
| Nombres jusqu'à 20 | « Les nombres de 11 à 20 » | couvert |
| Ordre, avant/après, comparaison | « Ranger les nombres » | couvert |
| Décomposition | « Décomposer un nombre » | couvert |
| Petits problèmes (ajouter, enlever) | « Petits problèmes » | couvert |
| Suites logiques | « Suites logiques » | couvert |
| Formes et côtés | « Les formes et leurs côtés » | couvert |
| Repérage (gauche, droite, entre) | « Se repérer » | partiel |
| Grandeurs (longueur, poids) | « Long, court, lourd, léger » | partiel |
| Nombres jusqu'à 30, double/moitié, quadrillages, algorithmes | — | manquant |

Existant : 9 fiches, 43 exercices, 23 illustrations.

### GS — Français (`gs-francais`)
| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Raconter dans l'ordre | « Je raconte avec des phrases » | couvert |
| Compréhension d'une histoire écoutée | « Une histoire à comprendre » (texte original) | couvert |
| Syllabes | « Les syllabes » | couvert |
| Rimes et sons initiaux | « Rimes et sons du début » | couvert |
| Alphabet (capitales, minuscules) | « L'alphabet » | couvert |
| Mots écrits (pré-lecture) | « Je lis des petits mots » | partiel |
| Phrase écrite, majuscule, point | « Écrire une phrase » | partiel |
| Écriture (lignes, sens du tracé) | « Écrire : sur les lignes » | partiel |
| Correspondance lettres-sons, écriture cursive, copie, dictée à l'adulte, comptines | — | manquant |

Existant : 8 fiches, 38 exercices, 6 illustrations.

### GS — Anglais d'éveil (`gs-english`)
| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Saluer, se présenter | « Hello ! Goodbye ! » | couvert |
| Couleurs | « Colours » | couvert |
| Nombres 1-10 | « Numbers » | couvert |
| Animaux | « Animals » | couvert |
| Corps | « My body » | couvert |
| Famille | « My family » | couvert |
| Comptines et chansons en anglais, objets de la classe | — | manquant |

Existant : 6 fiches, 27 exercices, 5 illustrations. L'anglais n'est pas certain au programme de la maternelle ; prononciation de
la synthèse vocale (en-GB) à vérifier.

### Récapitulatif et manques transversaux
- 10 packs : 73 fiches (dont 3 d'origine) et 345 exercices (voir tableaux ci-dessus).
- Estimation de couverture du programme reconstitué : environ 60 à 70 % (langage oral, numération, formes, découverte courante) ;
  plus faible pour l'EPS, l'éducation artistique et le graphisme (le geste n'est pas évaluable).
- Manquent : EPS et éducation artistique (pas de matière au catalogue), langues nationales, chants et comptines traditionnels,
  gestes graphiques et écriture manuscrite réels, mesure du temps, quantités au-delà de 20.
- Points « à vérifier » récurrents : quantités visées par section, découpage en syllabes, conventions de sécurité routière,
  conseils d'hygiène à faire relire par un agent de santé, cohérence des illustrations (feu de signalisation simplifié).

## SIL (Section d'initiation au langage, 1re année du primaire francophone)

Tous les packs SIL sont des brouillons (`status: draft`). Les programmes ci-dessous sont **reconstitués** et sont à confronter au programme officiel MINEDUB. Chaque fiche compte 6 exercices et 5 questions d'auto-évaluation (QCM à 4 choix), soit 11 exercices par fiche.

### SIL — Français

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Langage oral : écouter, se présenter, politesse | Fiche « Je parle et j'écoute » | couvert |
| Sons et lettres : voyelles | Fiche « Les voyelles : a, e, i, o, u » | partiel (pas de son [u] = « ou », ni consonnes) |
| Alphabet, majuscules et minuscules | Fiche « L'alphabet » | couvert (écriture d'imprimerie seule) |
| Syllabes | Fiche « Les syllabes » | couvert |
| Premières lectures de mots et de petites phrases | Fiche « Lire de premiers mots » | partiel (syllabes m, l, p seulement) |
| Écriture : tenue du crayon, tracé, copie | Fiche « Copier et écrire un mot » | partiel (l'appli ne corrige pas le tracé) |
| Première phrase : majuscule, point, ordre des mots | Fiche « Ma première phrase » | couvert |
| Comptines, poésie, récitation | aucune | manquant |
| Graphisme (lignes, ronds, ponts, boucles) | aucune | manquant |
| Lecture suivie de textes courts, dictée | aucune | manquant |

Existant : 7 fiches, 77 exercices (35 d'auto-évaluation), 8 illustrations. Manque : graphisme et cursive, autres sons (ou, ch, on, etc.), textes de lecture, dictée.

### SIL — Mathématiques

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Nombres de 0 à 10 | Fiche « Les nombres de 0 à 10 » | couvert |
| Nombres jusqu'à 20 et dizaine | Fiche « Les nombres de 11 à 20 » | couvert |
| Nombres jusqu'à 30, 50 ou 100 | aucune | manquant (borne exacte du programme à vérifier) |
| Comparer et ranger | Fiche « Comparer et ranger » | partiel (signes < et > non introduits) |
| Addition | Fiche « Ajouter : le signe + » | couvert (résultats ≤ 20) |
| Soustraction | Fiche « Enlever : le signe - » | couvert |
| Problèmes simples | exercices « problème » de chaque fiche | partiel |
| Formes : carré, rond, triangle, rectangle | Fiche « Les formes » | couvert |
| Repérage dans l'espace | Fiche « Se repérer : dessus, dessous, devant, derrière » | couvert |
| Monnaie (FCFA) | Fiche « La monnaie : les FCFA » | couvert (valeurs d'exemple, à vérifier) |
| Mesures de longueur, de masse, de temps (calendrier, heure) | aucune | manquant |
| Tableaux, suites logiques | aucune | manquant |

Existant : 8 fiches, 88 exercices (40 d'auto-évaluation), 15 illustrations. Manque : nombres au-delà de 20, mesures (longueur, temps), suites logiques.

### SIL — Sciences

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Le corps humain | Fiche « Mon corps » | couvert |
| Les cinq sens | Fiche « Les cinq sens » | couvert |
| Animaux domestiques et sauvages | Fiche « Les animaux autour de nous » | partiel (pas de classement par milieu de vie détaillé) |
| Les plantes | Fiche « Les plantes » | couvert |
| Hygiène et santé | Fiche « Je prends soin de moi : l'hygiène » | couvert |
| Alimentation | aucune | manquant |
| Eau, air, météo, saisons | aucune | manquant |
| Objets et matériaux | aucune | manquant |

Existant : 5 fiches, 55 exercices (25 d'auto-évaluation), 6 illustrations. Manque : alimentation, eau, météo et saisons, matériaux.

### SIL — Histoire-Géographie-ECM

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| La famille | Fiche « Ma famille » | couvert |
| L'école | Fiche « Mon école » | couvert |
| Quartier et village | Fiche « Mon quartier, mon village » | partiel (plan très simple, pas de repérage sur plan) |
| Règles de vie, civisme | Fiche « Les règles de vie ensemble » | couvert |
| Symboles du Cameroun (drapeau, capitale, 20 mai, devise) | Fiche « Les symboles du Cameroun » | partiel (hymne, 10 régions non traités) |
| Le temps (hier, aujourd'hui, demain ; jours de la semaine) | aucune | manquant |
| Métiers du quartier | aucune | manquant |
| Fêtes et traditions | aucune | manquant |

Existant : 5 fiches, 55 exercices (25 d'auto-évaluation), 5 illustrations. À valider : drapeau, devise, date de la fête nationale.

### SIL — Anglais

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Salutations, politesse, se présenter | Fiche « Hello ! Les salutations » | couvert |
| Couleurs | Fiche « Colours » | couvert |
| Nombres de 1 à 10 | Fiche « Numbers » | couvert |
| La famille | Fiche « My family » | couvert |
| L'école et le matériel | Fiche « At school » | partiel (5 mots) |
| Comptines et chansons en anglais | aucune | manquant |
| Animaux, corps, nourriture (mots simples) | aucune | manquant |

Existant : 5 fiches, 55 exercices (25 d'auto-évaluation), 6 illustrations. Les mots anglais sont lus avec la voix `en-GB`.

### Matières sans pack SIL (hors catalogue)

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| EPS (jeux, motricité) | aucune matière dans le catalogue | manquant |
| Éducation artistique (dessin, chant) | aucune matière dans le catalogue | manquant |
| Langues et cultures nationales | aucune | manquant |

### Synthèse SIL

Total : 30 fiches, 330 exercices, 40 illustrations. Couverture estimée du programme reconstitué : français environ 55 %, mathématiques environ 60 %, sciences environ 50 %, histoire-géo-ECM environ 50 %, anglais environ 55 %. Ces pourcentages sont indicatifs.

## Lot cp-core — CP (Français, Mathématiques)

Programme MINEDUB du CP : thèmes **reconstitués**, à confronter au programme officiel. Tous les contenus sont des brouillons (`draft`) à valider par un enseignant.

### CP — Français

Pack `cp-francais` : 13 fiches, 143 exercices (dont 65 d'auto-évaluation), 12 illustrations, 7 chapitres.

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Lecture : sons simples et complexes (ou, on, an, in, oi, ch, gn) | « Les sons : ou, on, an, in, oi, ch, gn » | partiel (autres sons : eu, ai/ei, au/eau, ill, ph, etc. manquants) |
| Lecture : syllabes, assemblage, mots | « Les syllabes et les mots » | couvert |
| Lecture : compréhension de petits textes | « Lire et comprendre un petit texte » (1 texte original de 5 phrases) | partiel (un seul texte ; pas de textes longs, ni de dialogues ou de lecture à voix haute guidée) |
| Écriture : copie, majuscule/point, lignes | « Copier sans erreur » | partiel (pas de geste graphique, ni d'écriture cursive, ni de dictée progressive) |
| Grammaire : phrase, majuscule, point | « La phrase : majuscule et point » | couvert |
| Grammaire : nom, article, genre et nombre | « Le nom et l'article » | couvert |
| Grammaire : verbe, qui fait l'action | « Le verbe : dire ce qu'on fait » | couvert |
| Conjugaison : présent de être et avoir | « Être et avoir au présent » | couvert |
| Conjugaison : présent des verbes en -er | « Les verbes en -er au présent » | couvert |
| Orthographe : accord simple (pluriel -s, féminin -e) | « Accorder : le pluriel et le féminin » | couvert |
| Orthographe : mots-outils (et/est, a/à) | « Les petits mots : et / est, a / à » | partiel (on/ont, son/sont, ce/se à venir) |
| Vocabulaire : classer, contraires | « Classer des mots et trouver les contraires » | partiel (synonymes, familles de mots, mots du quotidien camerounais manquants) |
| Expression : phrases, petite production | « Écrire une petite phrase, puis deux ou trois » | partiel (pas de récit suivi, pas de description d'image) |
| Dictée, poésie, comptines | — | manquant |
| Langues nationales / bilinguisme (anglais d'initiation) | — | manquant (hors lot) |

Manques : lecture de sons supplémentaires, textes de lecture plus nombreux et variés, dictées, écriture cursive, poésie, synonymes. Aucune épreuve blanche (non prévue au CP).

### CP — Mathématiques

Pack `cp-maths` : 14 fiches, 154 exercices (dont 70 d'auto-évaluation), 16 illustrations, 5 chapitres.

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Numération : nombres de 0 à 20 | « Les nombres de 0 à 20 » | couvert |
| Numération : nombres jusqu'à 100 (lecture, écriture, suite) | « Les nombres jusqu'à 100 » | couvert |
| Dizaines et unités | « Dizaines et unités » | couvert |
| Comparer et ranger | « Comparer et ranger les nombres » | couvert |
| Addition en ligne et posée (avec retenue) | « L'addition » | couvert |
| Soustraction en ligne et posée (sans retenue) | « La soustraction » | partiel (soustraction avec retenue absente, à confirmer hors programme) |
| Double et moitié | « Le double et la moitié » | couvert |
| Initiation à la multiplication (×2, ×5, ×10) | « Je découvre la multiplication » | partiel (pas de tables complètes) |
| Géométrie : lignes (droite, courbe, brisée, segment) | « Les lignes » | couvert |
| Géométrie : formes (carré, rectangle, triangle, rond) | « Les formes » | partiel (pas de repérage dans l'espace, ni de solides, ni de tracé sur quadrillage) |
| Mesures : longueur (cm, m) | « Mesurer des longueurs » | couvert |
| Mesures : monnaie FCFA | « L'argent : le franc CFA » | couvert (prix fictifs, à vérifier) |
| Mesures : heure (heures et demies) | « L'heure : heures et demi-heures » | partiel (quart d'heure, durées, calendrier manquants) |
| Problèmes simples (addition, soustraction, groupes égaux) | « Résoudre un petit problème » | partiel (une seule fiche ; pas de problèmes à deux étapes variés) |
| Masse et contenance (kg, litre) | — | manquant |
| Calcul mental (tables d'addition, compléments à 10) | — | manquant (partiellement abordé dans les fiches de calcul) |

Manques : masses et capacités, tables d'addition, repérage dans l'espace, calendrier et durées, soustraction avec retenue, plus de problèmes.

Estimation de couverture du programme CP (reconstitué) : environ 70 % en français et 75 % en mathématiques.

## Lot cp-other — CP : sciences, histoire-géo/ECM, anglais

Programme officiel MINEDUB non consulté : tous les thèmes ci-dessous sont **reconstitués, à confronter au programme officiel**. Chaque pack compte 7 fiches (77 exercices dont 35 d'auto-évaluation, 1 illustration par fiche au minimum), statut `draft`, validés par `LessonValidator` sans erreur ni avertissement.

### CP — Sciences (découverte du monde)

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Corps humain : parties du corps | Fiche « Mon corps et ma santé » (1 figure, 11 exercices) | couvert |
| Hygiène et santé (lavage des mains, dents, sommeil, eau propre) | Même fiche | couvert |
| Alimentation équilibrée | Mentionnée en une ligne dans la fiche corps/santé | partiel |
| Les cinq sens | Fiche « Les cinq sens » (1 figure, 11 exercices) | couvert |
| Animaux : domestiques/sauvages, déplacement, régime | Fiche « Les animaux » (1 figure, 11 exercices) | couvert |
| Reproduction et cycle de vie des animaux | — | manquant |
| Plantes : parties, besoins | Fiche « Les plantes » (1 figure, 11 exercices) | couvert |
| Germination, cultures locales | Seulement évoquées (manioc, arachide) | partiel |
| Eau : usages, propreté, économie | Fiche « L'eau » (1 figure, 11 exercices) | couvert |
| États de l'eau (glace, vapeur) | Liquide seulement | partiel |
| Air et vent | Fiche « L'air et le vent » (1 figure, 11 exercices) | couvert |
| Météo et saisons | Fiche « Le temps qu'il fait » (diagramme en barres, 11 exercices) | couvert |
| Santé : maladies courantes, vaccination, sécurité | — | manquant |
| Environnement, tri des déchets | — | manquant |
| Matière et objets (solide, liquide) | — | manquant |

### CP — Histoire-Géographie-ECM

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| La famille | Fiche « Ma famille » (arbre de famille, 11 exercices) | couvert |
| L'école et ses règles | Fiche « Mon école » (1 figure, 11 exercices) | couvert |
| Quartier, village, ville ; plan simple | Fiche « Mon quartier, mon village » (plan, 11 exercices) | couvert |
| Repères de temps : jour, semaine, mois | Fiche « Les jours, la semaine, les mois » (1 figure, 11 exercices) | couvert |
| Passé, présent, futur (frise, générations) | Hier / aujourd'hui / demain seulement | partiel |
| Orientation : droite, gauche, points cardinaux | Fiche « Me repérer » (carte simplifiée ; directions des villes marquées `review`) | couvert |
| Symboles du Cameroun : drapeau, devise, capitale, fête nationale, 10 régions | Fiche « Les symboles du Cameroun » (drapeau dessiné, 11 exercices) | couvert |
| Hymne national (titre, paroles) | Seulement mentionné, titre non cité | partiel |
| ECM : politesse, règles de vie, respect, propreté | Fiche « Vivre ensemble : règles et politesse » (tableau, 11 exercices) | couvert |
| Droits et devoirs de l'enfant, sécurité routière | — | manquant |
| Métiers, activités des hommes | — | manquant |
| Paysages et régions du Cameroun | Seulement quelques villes | manquant |
| Repères historiques (indépendance, réunification) | — (jugé hors CP) | manquant |

### CP — Anglais (initiation)

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Greetings : salutations, se présenter | Fiche « Hello ! » (1 figure, audio en-GB, 11 exercices) | couvert |
| Colours | Fiche « Les couleurs en anglais » (planche de 10 couleurs) | couvert |
| Numbers 1-10 | Fiche « Compter de 1 à 10 » (2 planches, 1 figure à compter) | couvert |
| Family | Fiche « La famille en anglais » (1 figure) | couvert |
| School objects et consignes de classe | Fiche « Les objets de la classe » (1 figure) | couvert |
| Animals | Fiche « Les animaux en anglais » (1 figure) | couvert |
| Body parts | Fiche « Le corps en anglais » (1 figure) | couvert |
| Nombres au-delà de 10, jours, météo, nourriture | — | manquant |
| Chansons et comptines en anglais | — (pas de contenu audio sous droits) | manquant |
| Compréhension orale de petits dialogues | Audio court dans chaque fiche, pas de pack dédié | partiel |

Points d'attention pour la relecture : directions entre villes du Cameroun (fiche orientation), formulation « deux grandes saisons », fiche sur le drapeau/devise/20 mai/10 régions (faits solides mais à confirmer), exercices personnels (famille, quartier) marqués `review`, prononciation des voix en-GB, pluriel « feet ».

### CE1 — Français

Programme reconstitué (non vérifié sur le texte officiel MINEDUB ; à confronter au programme officiel du cycle 2).
Pack `ce1-francais` : 16 fiches, 176 exercices (dont 80 d'auto-évaluation), 16 illustrations. Tout est en brouillon (`draft`).

| Thème du programme | Couvert par | Statut |
|---|---|---|
| Lecture-compréhension de textes courts (reconstitué) | « Lire et comprendre : Au marché » ; « Lire et comprendre : l'ordre d'une histoire » (2 textes originaux) | partiel |
| Lecture à voix haute, fluidité, déchiffrage (reconstitué) | lecture audio des textes (TTS) uniquement ; pas d'exercice de fluidité | manquant |
| Grammaire : phrase simple, types de phrases, ponctuation | « La phrase et ses quatre types » | couvert |
| Grammaire : groupe nominal, nom, déterminant, adjectif | « Le groupe nominal : nom, déterminant, adjectif » | couvert |
| Grammaire : sujet et verbe | « Le sujet et le verbe » | couvert |
| Conjugaison : présent (-er, être, avoir) | « Le présent des verbes en -er, être et avoir » | partiel (aller, faire, dire, venir, 2e/3e groupe non traités) |
| Conjugaison : futur | « Le futur : ce qui va arriver » | partiel (verbes en -er, être, avoir) |
| Conjugaison : passé composé | « Le passé composé : ce qui s'est passé » | partiel (avoir + -é ; auxiliaire être seulement mentionné) |
| Orthographe : accords singulier/pluriel | « Singulier et pluriel » | couvert |
| Orthographe : féminin des noms et adjectifs | « Masculin et féminin » | couvert |
| Orthographe : homophones a/à, et/est | « Les homophones : a / à et et / est » | couvert |
| Orthographe : homophones son/sont (on/ont non traités) | « Les homophones : son / sont » | partiel |
| Orthographe : dictée, copie, mots outils, lettres muettes | quelques dictées audio dans les fiches de lecture/expression ; pas de fiche dédiée | manquant |
| Vocabulaire : famille de mots | « La famille de mots » | couvert |
| Vocabulaire : synonymes, contraires | « Les synonymes et les contraires » | couvert |
| Vocabulaire : ordre alphabétique, dictionnaire, champs lexicaux | non traité | manquant |
| Expression écrite : décrire | « Écrire pour décrire » | couvert |
| Expression écrite : raconter | « Écrire pour raconter » | couvert |
| Écriture (graphisme, copie soignée) | non traité (hors contenu numérique) | manquant |

Bilan : environ 75 % du programme reconstitué est traité, avec des fiches de 15 à 20 minutes. Fiches : 16 ; exercices : 176 ; illustrations : 16.
À valider par un enseignant : choix des textes de lecture, niveau de difficulté, verbes retenus pour la conjugaison, exercices marqués `review`.

### CE1 — Mathématiques

Programme reconstitué (non vérifié sur le texte officiel MINEDUB ; à confronter au programme officiel du cycle 2).
Pack `ce1-maths` : 15 fiches, 165 exercices (dont 75 d'auto-évaluation), 19 illustrations. Calculs vérifiés par script. Tout est en brouillon (`draft`).

| Thème du programme | Couvert par | Statut |
|---|---|---|
| Nombres jusqu'à 1000 : lire, écrire, décomposer | « Les nombres jusqu'à 1000 » | couvert |
| Comparer, ranger, droite graduée | « Comparer et ranger les nombres » | couvert |
| Suites de nombres, pair/impair, doubles/moitiés | doubles et moitiés dans « Les tables… » ; pair/impair et suites non traités | partiel |
| Addition posée avec retenue | « L'addition posée » | couvert |
| Soustraction posée avec retenue | « La soustraction posée » | couvert |
| Calcul mental (compléments, sommes simples) | non traité en fiche dédiée | manquant |
| Multiplication posée par un nombre à un chiffre | « La multiplication par un nombre à un chiffre » | couvert |
| Tables de multiplication (2, 3, 4, 5, 10) | « Les tables de multiplication de 2, 3, 4, 5 et 10 » | partiel (tables de 6 à 9 non traitées) |
| Initiation à la division : partage, groupes, reste | « Partager : première approche de la division » | couvert (initiation) |
| Géométrie : droites, segments, angle droit, perpendiculaires | « Droites, segments et angle droit » | couvert |
| Géométrie : carré, rectangle, triangle | « Le carré, le rectangle et le triangle » | couvert |
| Géométrie : symétrie, quadrillage, cercle, solides | non traité | manquant |
| Longueurs (m, cm, règle) | « Mesurer des longueurs » | couvert |
| Masse et capacité (kg, g, L) | « Mesurer des masses et des capacités » | couvert |
| Heure (heures, demi-heure, quart d'heure) | « Lire l'heure » | couvert (jours, semaines, mois non traités) |
| Monnaie : FCFA, rendre la monnaie | « La monnaie : le franc CFA (FCFA) » | couvert |
| Problèmes additifs et soustractifs (1 et 2 étapes) | « Résoudre un problème : addition et soustraction » | couvert |
| Problèmes multiplicatifs et de partage | « Résoudre un problème : multiplication et partage » | couvert |
| Organisation de données (tableaux, graphiques simples) | un diagramme à barres dans la fiche masses ; fiche dédiée absente | partiel |

Bilan : environ 80 % du programme reconstitué est traité. Fiches : 15 ; exercices : 165 ; illustrations : 19.
À valider par un enseignant : valeurs des pièces et billets de FCFA, limite haute de la numération (999 ou 1 000), place de la division, périmètre et « moins le quart » (peut-être hors CE1), exercices marqués `review`.

## Lot ce1-other — CE1 : sciences, histoire-géographie-ECM, anglais

Programme MINEDUB du CE1 : thèmes **reconstitués**, à confronter au programme officiel. Tous les packs sont en `draft`. Chaque fiche : 11 exercices (6 + 5 d'auto-évaluation), 2 exemples résolus, au moins 1 illustration. Pas d'épreuve blanche (hors CEP).

### CE1 — Sciences

Pack `ce1-sciences` : 7 fiches, 77 exercices (dont 35 d'auto-évaluation), 8 illustrations.

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Corps humain : parties du corps, cinq sens | « Mon corps et ma santé » | couvert |
| Hygiène et santé (mains, dents, sommeil) | « Mon corps et ma santé » | partiel (pas de vaccins ni de croissance détaillés) |
| Alimentation : origine animale et végétale, repas varié, eau | « Bien manger » | couvert |
| Animaux : vertébrés / invertébrés, revêtement, insectes | « Les animaux autour de nous » | couvert |
| Animaux : régimes alimentaires, modes de reproduction et de déplacement, milieux de vie | cités en « Pour aller plus loin » | partiel |
| Plantes : parties, besoins, germination | « Les plantes et leur vie » | couvert |
| Eau : trois états, fonte, évaporation, eau potable | « L'eau et ses états » | couvert |
| Cycle de l'eau, pluie | simple mention | partiel |
| Air : existence, occupe de la place | « L'air et la matière » | couvert |
| Matière : solide, liquide, gaz, matériaux usuels | « L'air et la matière » | couvert |
| Environnement : propreté, déchets, protection de la nature | « Protéger mon environnement » | couvert |
| Technologie : objets techniques, sécurité domestique, énergie | aucune fiche | manquant |

Manque principal : technologie et objets techniques, expériences guidées plus détaillées, cycle de l'eau. Estimation de couverture : environ 80 %.

### CE1 — Histoire-Géographie-ECM

Pack `ce1-histoire-geo` : 7 fiches, 77 exercices (dont 35 d'auto-évaluation), 8 illustrations.

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Repères de temps : jours, semaine, mois, année, hier-aujourd'hui-demain | « Se repérer dans le temps » | couvert |
| Frise du temps simple | « Se repérer dans le temps » (frise de Ngono, fictive) | couvert |
| Famille, générations, arbre généalogique | « Ma famille et le passé » | couvert |
| Vie d'autrefois et d'aujourd'hui | « Ma famille et le passé » | partiel (généralités, à adapter à la région) |
| Histoire locale et nationale : indépendance, réunification, grandes figures | aucune fiche (dates non incluses par prudence) | manquant |
| Localités : quartier, village, ville, lieux publics, plan simple | « Mon quartier, mon village, ma ville » | couvert |
| Relief simple (montagne, plaine, plateau), eau (rivière, fleuve, lac, mer) | « Le relief et l'eau » | couvert |
| Le Cameroun : situation, capitale, dix régions et chefs-lieux, voisins | « Le Cameroun et ses régions » (schéma simple, pas une carte à l'échelle) | couvert |
| Points cardinaux, orientation | « Le Cameroun et ses régions », « Mon quartier » | partiel |
| Symboles nationaux : drapeau, devise, hymne, fête nationale | « Les symboles du Cameroun » | couvert |
| ECM : règles de vie, politesse, droits et devoirs | « Règles de vie et civisme » | couvert |
| Climats, végétation, activités économiques de la région | aucune fiche | manquant |

Manque principal : histoire du Cameroun (repères datés), climats et végétation, carte réelle à l'échelle (le schéma est approximatif). Estimation de couverture : environ 75 %.

### CE1 — English

Pack `ce1-english` : 6 fiches, 66 exercices (dont 30 d'auto-évaluation), 7 illustrations. Blocs audio en `en-GB` pour les mots et phrases anglais.

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Salutations, se présenter, phrases simples | « Hello! Se saluer en anglais » | couvert |
| Verbes to be et to have au présent | « I am… I have… » | couvert |
| Couleurs | « The colours » | couvert |
| Nombres de 1 à 20 | « Numbers 1 to 20 » | couvert |
| École : matériel et consignes | « At school » | couvert |
| Famille | « My family and my food » | couvert |
| Nourriture, « I like… » | « My family and my food » | couvert |
| Jours, mois, météo, corps, animaux, vêtements | aucune fiche | manquant |
| Compréhension et expression orales (chansons, comptines, dialogues) | audio de mots et de phrases seulement | partiel |

Manque principal : jours, animaux, parties du corps, vêtements, dialogues plus longs, chansons. Estimation de couverture : environ 70 %.

## Lot ce2-core — CE2 (environ 9 ans) : français et mathématiques

Tout le contenu est un **brouillon (`draft`)**, rédigé avec une IA, à faire valider par un enseignant. Les listes de thèmes ci-dessous sont **reconstituées** à partir du programme MINEDUB tel qu'il est connu ; elles sont à confronter au programme officiel. Chaque fiche a 11 exercices (6 d'entraînement : 3 d'application, 2 d'approfondissement, 1 de type problème ; 5 QCM d'auto-évaluation) et une illustration.

### CE2 — Français (pack `ce2-francais`)

Bilan : **19 fiches, 209 exercices (dont 95 d'auto-évaluation), 19 illustrations**, aucune épreuve blanche (non demandée au CE2).

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Lecture-compréhension d'un récit (qui, où, quand, quoi ; déduction) | « Lire et comprendre un petit récit » | couvert |
| Lecture d'un texte documentaire (informer, relever, résumer en fiche) | « Lire et comprendre un texte documentaire » | couvert |
| Types de phrases, forme affirmative et négative, ponctuation | « Types et formes de phrases, ponctuation » | couvert |
| Groupes de la phrase (groupe sujet, groupe verbal, complément de phrase) | « Les groupes de la phrase : sujet, verbe, compléments » | couvert |
| Nature des mots (nom, déterminant, adjectif, verbe) | « La nature des mots » | partiel (pas de préposition, d'adverbe ni de conjonction) |
| Fonctions simples (sujet, complément de phrase) | fiche « groupes » | partiel (COD, attribut et complément du nom non traités) |
| Pronoms personnels sujets | « Les pronoms personnels sujets » | couvert (pronoms compléments non traités) |
| Présent des verbes du 1er groupe, être, avoir | « Le présent : verbes du 1er groupe, être et avoir » | couvert |
| Présent des verbes du 2e et du 3e groupe (aller, faire, prendre, venir, dire) | « Le présent : verbes du 2e et du 3e groupe » | partiel (pouvoir, vouloir, voir, savoir absents) |
| Imparfait des trois groupes | « L'imparfait » | couvert |
| Futur simple des trois groupes | « Le futur » | couvert |
| Passé composé (auxiliaires avoir et être, accord avec être) | « Le passé composé » | couvert |
| Accords dans le groupe nominal et sujet-verbe, pluriel des noms | « Les accords : groupe nominal et sujet-verbe » | couvert |
| Homophones grammaticaux (a/à, et/est, on/ont, son/sont) | « Les homophones » | partiel (ce/se, ces/ses, la/là non traités) |
| Dictée préparée, relecture | « Préparer et réussir une dictée » | couvert (la dictée se fait avec l'audio ; pas de correction automatique d'écriture) |
| Vocabulaire : préfixes, suffixes, familles de mots | « Les préfixes et les suffixes » | couvert |
| Vocabulaire : sens propre et figuré, dictionnaire | « Sens propre, sens figuré et dictionnaire » | couvert |
| Expression écrite : description | « Écrire une description » | couvert |
| Expression écrite : récit | « Écrire un petit récit » | couvert |
| Expression écrite : lettre | « Écrire une lettre » | couvert |
| Copie, écriture (geste graphique), lecture à voix haute | — | manquant (hors capacités du lecteur : pas d'écriture manuscrite ni d'enregistrement) |
| Poésie, récitation, contes de la tradition orale | — | manquant (nécessite des textes à droits libres ou à relire) |
| Conjugaison de l'impératif | — | manquant |

À faire : un lot d'épreuves blanches d'entraînement ; des textes de lecture supplémentaires (les 2 fiches de lecture n'ont chacune que 4 à 5 textes courts) ; vérification de la terminologie officielle (impérative/injonctive, groupe verbal/prédicat).

### CE2 — Mathématiques (pack `ce2-maths`)

Bilan : **14 fiches, 154 exercices (dont 70 d'auto-évaluation), 14 illustrations** (tableau de numération, opérations posées, fractions, figures, symétrie, solides, horloge, monnaie, schéma en barres…), aucune épreuve blanche.

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Numération jusqu'à 9 999 et 10 000 (lire, écrire, comparer, ranger, décomposer) | « Les nombres jusqu'à 9 999 » | couvert |
| Addition et soustraction posées avec retenues, preuve, ordre de grandeur | « Addition et soustraction posées » | couvert |
| Multiplication posée par un nombre à un chiffre ; × 10, 100, 1 000 | « La multiplication posée » | partiel (multiplication par un nombre à 2 chiffres non traitée) |
| Tables de multiplication, calcul mental (doubles, moitiés, compléments) | « Les tables de multiplication et le calcul mental » | couvert |
| Division posée par un nombre à un chiffre (quotient, reste) | « La division par un nombre à un chiffre » | couvert |
| Fractions simples (demi, tiers, quart, dixième ; fraction d'une quantité) | « Les fractions simples » | partiel (pas de comparaison de fractions de même dénominateur ni de droite graduée) |
| Figures planes, angle droit, équerre, compas | « Les figures planes et l'angle droit » | partiel (tracés à la règle et au compas non vérifiables à l'écran) |
| Périmètre du carré, du rectangle, du triangle | « Le périmètre » | couvert |
| Symétrie axiale (axe, pliage, symétrique d'un point) | « La symétrie axiale » | partiel (pas de quadrillage à compléter) |
| Solides simples (cube, pavé, cylindre, cône, sphère, pyramide) | « Les solides simples » | couvert |
| Mesures : longueurs, masses, capacités ; conversions | « Longueurs, masses et capacités : les conversions » | couvert |
| Heure (lecture, 24 h) et durées | « L'heure et les durées » | couvert |
| Monnaie : FCFA, prix, rendu de monnaie | « Le franc CFA : prix et monnaie » | couvert (valeurs des pièces et billets à faire vérifier) |
| Problèmes à plusieurs étapes | « Les problèmes à plusieurs étapes » | couvert |
| Aire (notion), surface d'un rectangle au quadrillage | — | manquant |
| Tableaux et graphiques simples, organisation de données | — | manquant |
| Droites perpendiculaires et parallèles, alignement | — | manquant |

Lot « ce2-other » : CE2 (≈ 9 ans) — sciences, histoire-géographie-ECM, anglais. Tout le contenu est un brouillon (`draft`) ; les thèmes du programme sont **reconstitués, à confronter au programme officiel MINEDUB**.

### CE2 — Sciences

(a) Thèmes reconstitués : corps humain (digestion, respiration), hygiène et prévention des maladies courantes, animaux et milieux, plantes, eau et états de la matière, air.
(b) Pack `ce2-sciences` : 7 fiches, 77 exercices (dont 35 d'auto-évaluation), 7 illustrations.
(c) Manque : os et muscles, cinq sens, circulation, cycle de l'eau détaillé, reproduction des animaux, expériences guidées, épreuve blanche.

| Thème du programme | Couvert par | Statut |
|---|---|---|
| Digestion | « Le trajet des aliments dans le corps » | couvert |
| Respiration | « Respirer : le trajet de l'air » | couvert |
| Hygiène, paludisme, diarrhée (prévention seule) | « L'hygiène et la prévention des maladies courantes » | couvert |
| Animaux et leur milieu, régimes | « Les animaux et leur milieu de vie » | couvert |
| Plantes (parties, besoins, germination) | « Les plantes : leurs parties et leurs besoins » | couvert |
| États de la matière, eau | « L'eau et ses trois états » | couvert |
| Air | « L'air autour de nous » | couvert |
| Squelette, muscles, sens | aucune | manquant |
| Cycle de l'eau, reproduction animale | mentions dans les fiches « eau » et « animaux » | partiel |

### CE2 — Histoire-Géographie-ECM

(a) Thèmes reconstitués : repères de temps, localité et plan, Cameroun et ses dix régions, relief/climat/végétation, activités économiques, civisme (droits et devoirs), symboles de la nation.
(b) Pack `ce2-histoire-geo` : 7 fiches, 77 exercices (dont 35 d'auto-évaluation), 7 illustrations (dont une frise du temps).
(c) Manque : histoire locale (famille, ancêtres, traditions), grandes étapes de l'histoire du Cameroun (aucune date sûre n'a été avancée volontairement), carte dessinée des régions, hydrographie, santé et sécurité routière en ECM, épreuve blanche.

| Thème du programme | Couvert par | Statut |
|---|---|---|
| Repères de temps, frise | « Les repères de temps : jours, mois, années, frise » | couvert |
| La localité, le plan, points cardinaux | « Se repérer : ma localité, le plan et les points cardinaux » | couvert |
| Le Cameroun, régions et chefs-lieux | « Le Cameroun : capitale, voisins et dix régions » | couvert |
| Relief, climat, végétation | « Relief, climat et végétation du Cameroun » | partiel (description simplifiée) |
| Activités économiques | « Les activités économiques au Cameroun » | couvert |
| Droits et devoirs de l'enfant (ECM) | « Les droits et les devoirs de l'enfant » | couvert |
| Symboles de la nation | « Les symboles du Cameroun » | couvert |
| Histoire du passé du Cameroun, traditions | aucune | manquant |
| Hydrographie, cartes | aucune | manquant |

### CE2 — Anglais

(a) Thèmes reconstitués : vocabulaire de base, jours et mois, famille, nourriture, vie quotidienne (présent simple), dialogues courts, objets de la classe, couleurs, nombres.
(b) Pack `ce2-english` : 6 fiches, 66 exercices (dont 30 d'auto-évaluation), 6 illustrations, blocs audio de mots anglais en `en-GB`.
(c) Manque : lecture de petits textes, chansons et comptines, vocabulaire des animaux, des vêtements et du corps, heure, épreuve blanche.

| Thème du programme | Couvert par | Statut |
|---|---|---|
| Jours et mois | « Les jours et les mois : days and months » | couvert |
| Famille | « La famille : my family » | couvert |
| Nourriture | « La nourriture : food and drink » | couvert |
| Vie quotidienne, présent simple | « La vie de tous les jours : le présent simple » | couvert |
| Dialogues courts, salutations | « Se présenter : short dialogues » | couvert |
| La classe, couleurs, nombres | « La classe, les couleurs et les nombres » | couvert |
| Animaux, vêtements, corps, heure | aucune | manquant |

<!-- Lot cm1-core : CM1, français et mathématiques. Programmes reconstitués de mémoire, à confronter au programme officiel MINEDUB (niveau III, CM1-CM2). -->

### CM1 — Français

Pack `cm1-francais` : 12 fiches, 132 exercices (dont 60 questions d'auto-évaluation), 12 illustrations. Contenu entièrement original (textes de lecture rédigés pour l'occasion), statut brouillon. Pas d'épreuve blanche (le CM1 n'a pas d'examen).

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Lecture et compréhension d'un texte (informations explicites, inférences, résumé) | « Lire et comprendre un texte » (un seul texte narratif court + petits textes dans les exercices) | partiel |
| Lecture de textes variés (documentaire, poème, conte, théâtre) | aucune fiche | manquant |
| Grammaire : nature des mots (nom, déterminant, adjectif, verbe, pronom, adverbe, préposition, conjonction de coordination) | « La nature des mots » | couvert |
| Grammaire : fonctions (sujet, COD, COI, attribut du sujet, compléments circonstanciels) | « Les fonctions dans la phrase » | couvert |
| Phrase simple et complexe, propositions (juxtaposées, coordonnées, principale et subordonnée) | « Phrase simple, phrase complexe, propositions » | couvert |
| Types et formes de phrases (déclarative, interrogative, exclamative, impérative ; forme négative) | quelques mentions seulement (impératif, ponctuation du dialogue) | manquant |
| Conjugaison : présent (3 groupes, verbes irréguliers courants) et impératif | « Le présent et l'impératif » | couvert |
| Conjugaison : imparfait, futur simple, conditionnel présent | « L'imparfait, le futur et le conditionnel » | couvert |
| Conjugaison : passé composé (avoir/être), passé simple (3e personne) | « Le passé composé et le passé simple » | couvert |
| Conjugaison : plus-que-parfait, futur antérieur, subjonctif | non traités (probablement hors CM1) | manquant |
| Orthographe grammaticale : accords (groupe nominal, sujet-verbe, participe passé avec être et avoir simple) | « Les accords » | couvert |
| Orthographe : homophones grammaticaux (a/à, et/est, on/ont, son/sont, ou/où, ces/ses, ce/se) | « Les homophones grammaticaux » | partiel (c'est/s'est, leur/leurs, la/là/l'a, quand/quant/qu'en non traités) |
| Orthographe lexicale, dictée, copie | aucune fiche dédiée (une mini-dictée existe ailleurs dans le socle) | manquant |
| Vocabulaire : familles de mots, préfixes/suffixes, champs lexicaux, synonymes, antonymes | « Familles de mots, champs lexicaux, synonymes et contraires » | couvert |
| Vocabulaire : polysémie, sens propre et figuré, usage du dictionnaire | mentions rapides seulement | partiel |
| Expression écrite : récit (schéma, temps, connecteurs) et description | « Écrire un récit et une description » | couvert |
| Expression écrite : dialogue et lettre | « Le dialogue et la lettre » | couvert |
| Poésie, récitation, expression orale | aucune fiche | manquant |

Total estimé : environ 70 % du programme de français reconstitué. À confronter au programme officiel avant validation.

### CM1 — Mathématiques

Pack `cm1-maths` : 12 fiches, 132 exercices (dont 60 questions d'auto-évaluation), 13 illustrations (tableau de numération, droite graduée, bandes de fractions, tableaux de proportionnalité, angles, triangles, cercle, quadrillage d'aire, solides, diagramme en barres). Réponses numériques calculées par script. Statut brouillon.

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Numération : nombres jusqu'au million (lecture, écriture, valeur des chiffres, comparaison, rangement, arrondi) | « Les grands nombres jusqu'au million » | couvert |
| Addition, soustraction, multiplication posées | « Additions, soustractions et multiplications posées » | couvert |
| Division euclidienne par un nombre à deux chiffres | « La division par un nombre à deux chiffres » | couvert |
| Calcul mental (tables, multiples, calcul réfléchi), multiples et diviseurs | mentions rapides (ordre de grandeur, ×10) ; pas de fiche dédiée | partiel |
| Nombres décimaux (dixièmes, centièmes, comparaison, addition, soustraction, ×10 et ×100) | « Les nombres décimaux » | couvert |
| Multiplication et division d'un décimal par un entier | non traitées | manquant |
| Fractions simples, fraction d'une quantité, fractions décimales | « Les fractions » | couvert |
| Proportionnalité (tableau, coefficient, retour à l'unité) | « La proportionnalité » | couvert |
| Organisation de données (tableaux, graphiques en barres, lecture) | un diagramme en barres dans « Résoudre un problème » ; pas de fiche dédiée | partiel |
| Géométrie : angles (droit, aigu, obtus, équerre) | « Les angles et les triangles » | couvert |
| Géométrie : triangles (quelconque, isocèle, équilatéral, rectangle) | « Les angles et les triangles » | couvert |
| Géométrie : cercle (centre, rayon, diamètre, compas) | « Le cercle » | couvert |
| Géométrie : droites parallèles et perpendiculaires, symétrie axiale, quadrilatères | non traités | manquant |
| Solides : cube, pavé, prisme, pyramide, cylindre ; patron du cube | « Les solides » | couvert |
| Périmètre et aire (rectangle, carré, polygone) | « Périmètre et aire » | couvert |
| Mesures : longueurs, masses, capacités, durées, conversions | « Longueurs, masses, capacités et durées » | couvert |
| Aires : unités et conversions (cm², m²) ; volumes | seulement les unités cm² et m² | partiel |
| Problèmes (méthode, données inutiles, problèmes à étapes) | « Résoudre un problème » et problèmes finaux de chaque fiche | couvert |

Total estimé : environ 80 % du programme de mathématiques reconstitué. À confronter au programme officiel avant validation.

## CM1 — Sciences, Histoire-Géographie-ECM, Anglais (lot « cm1-other »)

Tous les thèmes ci-dessous sont **reconstitués** (programme officiel MINEDUB du niveau III non consulté), à confronter au programme officiel. Contenu en brouillon (`draft`), non relu par un enseignant.

### CM1 — Sciences

Pack `cm1-sciences` : 8 fiches, 88 exercices (dont 40 d'auto-évaluation), 8 illustrations, 25 points « à vérifier ».

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Corps humain et grandes fonctions (digestion, respiration, circulation) | Fiche « Digestion, respiration, circulation » | couvert |
| Santé et hygiène (gestes, paludisme, eau, vaccins) | Fiche « Rester en bonne santé : l'hygiène » | couvert |
| Nutrition et alimentation équilibrée | Fiche « Bien manger » | couvert |
| Reproduction des plantes (fleur, fruit, graine, germination) | Fiche « De la fleur à la graine » | couvert |
| Animaux, régimes alimentaires et chaînes alimentaires | Fiche « Qui mange qui ? » | couvert |
| Matière : états, changements d'état, mélanges, séparation | Fiche « États de la matière et mélanges » | couvert |
| Énergie simple (formes, sources, sécurité électrique) | Fiche « L'énergie autour de nous » | couvert |
| Environnement et protection (déchets, compost, forêts) | Fiche « Protéger l'environnement » | couvert |
| Squelette, muscles, organes des sens, excrétion | — | manquant |
| Reproduction asexuée (bouture, tubercule), classification des animaux (vertébrés) | — | manquant |
| Technologie (objets techniques, leviers, circuits électriques simples) | — | manquant |
| Épreuves blanches, expériences à faire en classe | — | manquant |

Manque : une seule fiche par thème, sans figures anatomiques détaillées ; pas d'épreuve blanche (le CM1 n'est pas une classe d'examen).

### CM1 — Histoire-Géographie-ECM

Pack `cm1-histoire-geo` : 7 fiches, 77 exercices (dont 35 d'auto-évaluation), 7 illustrations (frise, profil du relief, schéma des climats, tableau des régions, schéma des secteurs, droits et devoirs, drapeau), 32 points « à vérifier ».

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Repères dans le temps, frise, siècles, durées | Fiche « Repères dans le temps : la frise du Cameroun » | couvert |
| Histoire : grandes périodes (avant la colonisation, colonisation, indépendance et réunification) | même fiche, repères 1884, 1916, 1960, 1961, 1972 | partiel |
| Géographie : situation, voisins et relief du Cameroun | Fiche « Le relief du Cameroun » | couvert |
| Climats et végétation, fleuves et lac Tchad | Fiche « Climat et hydrographie du Cameroun » | couvert |
| Régions et chefs-lieux (10 régions) | Fiche « Les dix régions du Cameroun » | couvert |
| Activités économiques (secteurs, cultures, élevage, pêche, commerce) | Fiche « Les activités économiques du Cameroun » | partiel |
| ECM : droits et devoirs de l'enfant, règles de vie | Fiche « Droits et devoirs de l'enfant » | couvert |
| ECM : symboles, institutions simples, vivre ensemble | Fiche « Les symboles du pays et les institutions simples » | partiel |
| Histoire détaillée (royaumes, grands personnages, résistance, personnages) | — | manquant |
| Cartes réelles du Cameroun (aucune carte dessinée, seulement des schémas) | — | manquant |
| Population, villes, transports, ressources par région | — | manquant |
| Afrique et monde (continents, océans) | — | manquant |

Manque : pas de personnages historiques ni de chiffres (par prudence) ; dates à confirmer ; zones de production et rôles des autorités locales à valider.

### CM1 — Anglais (initiation)

Pack `cm1-english` : 6 fiches, 66 exercices (dont 30 d'auto-évaluation), 6 illustrations, 18 points « à vérifier ». Explications en français, exemples et audio en anglais britannique (`en-GB`).

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Present simple (habitudes, -s, négation, question) | Fiche « Le present simple : les habitudes » | couvert |
| Present continuous | Fiche « Le present continuous » | couvert |
| There is / There are | Fiche « There is, there are » | couvert |
| Possessifs (my, your, his, her…, 's) | Fiche « Les possessifs » | couvert |
| Ma journée, heures simples | Fiche « Ma journée : daily routine » | couvert |
| Ma ville, prépositions de lieu, petit texte | Fiche « Ma ville : my town » | partiel |
| Verbes to be et have got, nombres, couleurs, famille, nourriture | — | manquant |
| Past simple, can/can't, imperatif, directions | — | manquant |
| Lecture et expression écrite de textes plus longs, dialogues, chansons | — | manquant |
| Exercices d'écoute et de prononciation (audio limité aux exemples) | — | partiel |

## Lot cm2-core — CM2 (non-examen) : français et mathématiques

Les packs `cm2-francais` et `cm2-maths` sont le cours complet de la classe (pas des fiches d'examen ; les packs `cep-*` sont à part). Le programme MINEDUB du CM2 n'a pas été consulté sur le texte officiel : les thèmes ci-dessous sont **reconstitués, à confronter au programme officiel**. Tout est en statut `draft`. Chaque pack contient une épreuve de bilan sur 20 (sans exercice `review`).

### CM2 — Français

Existant : 12 fiches, 137 exercices (dont 60 d'auto-évaluation), 5 illustrations, 1 épreuve de bilan.

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Lecture-compréhension de textes (informations, déductions, sens des mots) | Lire et comprendre un texte (textes originaux courts) | couvert |
| Types et formes de phrase ; phrase simple/complexe ; propositions (indépendante, principale, subordonnée, juxtaposée, coordonnée) | La phrase : types, propositions | couvert |
| Fonctions (sujet, COD, COI, CC, attribut, épithète, complément du nom) | Les fonctions dans la phrase | couvert |
| Voix active/passive ; discours direct/indirect | Voix passive et discours rapporté | couvert |
| Conjugaison : présent, imparfait, futur, passé simple (3e pers.) | Conjugaison : présent, imparfait, futur, passé simple | couvert |
| Conjugaison : passé composé, plus-que-parfait, conditionnel présent, impératif | Passé composé, plus-que-parfait, conditionnel, impératif | couvert |
| Conjugaison : futur antérieur, subjonctif présent, passé simple aux autres personnes | mentionnés en « pour aller plus loin » seulement | partiel |
| Accords (groupe nominal, sujet-verbe, attribut, pluriels et féminins particuliers) | Les accords dans la phrase | couvert |
| Participe passé (terminaisons, accord avec être, avec avoir et COD placé avant) | Le participe passé | couvert |
| Homophones grammaticaux (a/à, on/ont, son/sont, et/est, ce/se, ces/ses, la/là/l'a, ou/où, leur/leurs, quel/qu'elle) | Les homophones grammaticaux | couvert |
| Vocabulaire (famille de mots, préfixes/suffixes, synonymes/contraires, sens figuré, dictionnaire) | Le vocabulaire | couvert |
| Expression écrite : récit et dialogue | Écrire un récit et un dialogue | couvert |
| Expression écrite : lettre, description, compte rendu | Écrire une lettre, une description, un compte rendu | couvert |
| Dictée, copie, poésie / récitation, lecture à voix haute | aucune (dictée seulement évoquée dans le pack CEP) | manquant |
| Lecture de textes documentaires et de textes longs (progression de lecture suivie) | un seul texte narratif par fiche | partiel |
| Illustrations : peu de figures (5), pas de support audio de dictée | — | partiel |

Manques principaux : dictée, poésie, textes documentaires, subjonctif et futur antérieur, banque d'exercices de lecture plus variée ; corrigés des rédactions à relire par un enseignant (grilles indicatives).

### CM2 — Mathématiques

Existant : 11 fiches, 125 exercices (dont 55 d'auto-évaluation), 10 illustrations, 1 épreuve de bilan. Tous les calculs numériques ont été vérifiés à la main.

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Nombres décimaux : lecture, écriture, comparaison, ×10/100/1 000, arrondi | Les nombres décimaux | couvert |
| Grands nombres entiers (tableau de numération jusqu'aux millions), nombres relatifs | non traités dans ce pack (voir le pack CEP) | manquant |
| Fractions : lecture, fraction d'une quantité, fractions décimales, comparaison, addition (même dénominateur) | Les fractions | couvert |
| Quatre opérations (entiers et décimaux), division euclidienne et décimale, ordre des calculs | Les quatre opérations | couvert |
| Proportionnalité (tableau, coefficient, retour à l'unité) | La proportionnalité | couvert |
| Pourcentages ; échelle simple | Pourcentages et échelle | couvert |
| Géométrie : angles, triangles, quadrilatères, polygones | Angles, triangles et quadrilatères | couvert |
| Cercle, périmètres, aires (rectangle, carré, triangle, disque) | Cercle, périmètres et aires | couvert |
| Solides et volumes ; capacités | Solides et volumes | couvert |
| Mesures et conversions (longueurs, masses, capacités, aires) | Mesures et conversions | couvert |
| Durées | Les durées | couvert |
| Problèmes à étapes, moyenne | Problèmes types et moyenne | couvert |
| Constructions géométriques aux instruments (tracés), symétrie, repérage sur quadrillage, lecture de tableaux et graphiques | seulement un diagramme en barres dans une fiche | partiel |
| Aire du parallélogramme et du trapèze, volume du cylindre, comparaison de fractions de dénominateurs différents, vitesse | quelques mentions en « pour aller plus loin » | partiel |

Manques principaux : grands nombres et numération des entiers, géométrie de tracé (symétrie, constructions), statistiques simples, vitesse. Estimation de couverture : français environ 80 %, mathématiques environ 75 % du programme reconstitué.

Points à faire valider : conventions (π ≈ 3,14, notation des durées), niveau exact de certaines notions (accord du participe avec COD placé avant, concordance des temps au discours indirect, aire du disque), noms de lieux et prix fictifs des exercices.

## Lot cm2-other — CM2 (sciences, histoire-géographie-ECM, anglais)

Packs non-examen (sans champ `exam`), tous `status: draft`, rédigés avec une IA, à valider par un enseignant. Les packs `cep-*` ne sont pas touchés. Les thèmes de programme ci-dessous sont **reconstitués, à confronter au programme officiel MINEDUB** (je ne dispose pas du texte officiel).

### CM2 — Sciences (`cm2-sciences`)

Existant : 8 fiches, 88 exercices (dont 40 d'auto-évaluation), 5 illustrations (tube digestif, plante, chaîne alimentaire, états de l'eau, circuit), 21 points « à vérifier ». Fiches : La digestion et l'alimentation équilibrée ; Se protéger des maladies (hygiène, eau, paludisme) ; Naître et grandir (reproduction) ; La plante verte ; Animaux, nourriture et milieux ; La matière (états et mélanges) ; L'énergie et le circuit électrique simple ; Protéger l'environnement.

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Nutrition : digestion, familles d'aliments, repas équilibré | Fiche digestion | couvert |
| Corps humain : respiration, circulation, os et muscles, organes des sens | — | manquant |
| Santé et hygiène : paludisme, eau, vaccination | Fiche santé | couvert |
| Reproduction : animaux ovipares/vivipares, plantes, étapes de la vie | Fiche vie | partiel (reproduction humaine non détaillée) |
| Plantes : organes, besoins, germination | Fiche plantes | couvert |
| Animaux : régimes, chaînes alimentaires, milieux | Fiche animaux | partiel (classification, adaptations peu détaillées) |
| Matière : états, changements d'état, mélanges | Fiche matière | partiel (volume, masse, mesures non traités) |
| Énergie : sources ; électricité simple, conducteurs/isolants, sécurité | Fiche énergie | partiel (pas de montages en série/dérivation, pas de magnétisme) |
| Environnement : déchets, déforestation, protection | Fiche environnement | couvert |
| Technologie simple : levier, machines simples | Fiche environnement (rubrique « pour aller plus loin » seulement) | manquant |
| Astronomie, air et météo | — | manquant |

Manques : respiration, circulation, sens ; air et météo ; astronomie (jour/nuit, saisons) ; technologie simple en fiche propre ; épreuve blanche (non demandée). Couverture estimée : environ 65 %.

### CM2 — Histoire-Géographie-ECM (`cm2-histoire-geo`)

Existant : 8 fiches, 88 exercices (dont 40 d'auto-évaluation), 7 illustrations (schéma des régions, végétation, productions, parties de l'Afrique, deux frises, drapeau), 29 points « à vérifier ». Fiches : Le Cameroun et ses dix régions ; Relief, climats et végétation ; Population et activités ; Le Cameroun dans l'Afrique ; Des royaumes à la colonisation ; Indépendance et réunification ; ECM : institutions et symboles ; ECM : droits, devoirs et paix.

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Géographie du Cameroun : situation, voisins, 10 régions et chefs-lieux | Fiche regions | couvert (schéma, pas de vraie carte) |
| Relief, climats, végétation, hydrographie | Fiche milieux | partiel (hydrographie : citée en « aller plus loin ») |
| Population (diversité) et activités économiques, transports | Fiche activites | partiel (aucune statistique, volontairement) |
| Géographie de l'Afrique : continent, océans, grandes parties, pays voisins et capitales | Fiche afrique | partiel (pas de carte, peu de pays) |
| Histoire : sociétés précoloniales, arrivée des Européens | Fiche colonisation | partiel (royaumes cités, sans détails ni personnages) |
| Histoire : colonisation allemande, mandats/tutelle France et Royaume-Uni | Fiche colonisation | partiel (dates 1884, 1916, 1946 à vérifier) |
| Histoire : indépendance, réunification, fédération, 1972 | Fiche independance | partiel (dates à vérifier ; mouvements nationalistes non traités) |
| ECM : institutions, symboles, fête nationale | Fiche institutions | couvert |
| ECM : droits et devoirs de l'enfant, citoyenneté, paix, environnement | Fiche citoyen | couvert |
| Histoire : vie sociale d'autrefois, personnages célèbres, grandes explorations | — | manquant |
| Géographie du monde (continents, océans), lecture de cartes, orientation | — | manquant |

Manques : vraie cartographie (nécessite des fonds de carte hors du format actuel) ; personnages historiques ; Afrique : pays, fleuves, grands ensembles régionaux plus détaillés ; histoire de la ville ou de la région de l'élève. Couverture estimée : environ 60 %.

### CM2 — Anglais (`cm2-english`)

Existant : 7 fiches, 77 exercices (dont 35 d'auto-évaluation), 3 illustrations (mots interrogatifs, ligne du temps, comptage), blocs audio `en-GB` pour les mots et dialogues, 15 points « à vérifier ». Fiches : To be et have got ; Le présent simple ; Poser des questions ; Le passé simple ; Le futur (will, going to) ; Vocabulaire (famille, école, nourriture, nombres, jours, mois) ; Dialogues et courts textes.

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Be et have got | Fiche be | couvert |
| Present simple (affirmatif, négatif, adverbes de fréquence) | Fiche present | couvert |
| Questions (be, do/does, mots interrogatifs), réponses courtes | Fiche questions | couvert |
| Past simple (réguliers, irréguliers usuels, négation, question) | Fiche past | couvert |
| Futur (will, going to) | Fiche future | couvert |
| Vocabulaire : famille, école, nourriture, nombres, jours, mois | Fiche vocab | partiel (couleurs, corps, vêtements, animaux, métiers à ajouter) |
| Dialogues : salutations, marché, présentation | Fiche dialogues | partiel (peu de situations) |
| Lecture de courts textes | Fiches dialogues et vocab | partiel |
| Present continuous, prépositions de lieu, there is / there are, pluriels, articles | — | manquant |
| Écriture guidée (lettre, description) et chansons / comptines | — | manquant |

Manques : present continuous, prépositions, there is/are, pluriels, articles, adjectifs possessifs ; vocabulaire thématique plus large ; plus de textes courts ; exercices d'écoute (seules les consignes audio existent, sans exercice d'écoute noté). Couverture estimée : environ 65 %.

## Lot cep — préparation du CEP (CM2, packs d'examen)

Packs `cep-maths`, `cep-francais`, `cep-sciences` (étendus en version 2) et `cep-histoire-geo`, `cep-english` (nouveaux). Ce sont des **synthèses de révision orientées examen** (fiche type : objectifs, l'essentiel, 2 exemples résolus, 3/2/1 exercices, 5 auto-évaluations), distinctes des packs `cm2-*` (cours complet). Les thèmes ci-dessous sont **reconstitués, à confronter au programme officiel** (MINEDUB) ; le format réel des épreuves du CEP (matières, durées, barèmes) est **incertain** : les épreuves blanches sont des exercices originaux notés sur 20 qui le signalent dans leurs instructions. Tout est en statut `draft`, aucun contenu n'a été relu par un enseignant.

### CM2 (CEP) — Mathématiques

Existant (`cep-maths`, v2) : 12 fiches, 146 exercices (dont 60 d'auto-évaluation), 17 illustrations, 3 épreuves blanches (90 min indicatives, aucun exercice `review`).
Fiches : Les grands nombres, les décimaux et les 4 opérations ; Les fractions simples ; Proportionnalité et pourcentages ; Mesures, périmètres, aires et volumes ; Résoudre des problèmes ; **nouvelles** : Les nombres décimaux (×10 ÷10, arrondi) ; Multiples, diviseurs et critères de divisibilité ; Droites, angles, triangles et symétrie ; Les solides, volume et capacité ; Les durées ; Échelle, plans et cartes ; Lire un diagramme et calculer une moyenne.

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Grands nombres, lecture/écriture, 4 opérations | Les grands nombres et les 4 opérations | couvert |
| Nombres décimaux : comparer, arrondir, multiplier/diviser par 10, 100, 1 000 | Les nombres décimaux | couvert |
| Multiples, diviseurs, critères de divisibilité | Multiples et diviseurs | couvert |
| Fractions simples, fraction d'une quantité | Les fractions simples | couvert |
| Proportionnalité, pourcentages, échelles | Proportionnalité et pourcentages ; Échelle, plans et cartes | couvert |
| Géométrie plane : droites, angles, triangles, quadrilatères, symétrie axiale | Droites, angles, triangles et symétrie | partiel (pas de construction instrumentée ni de rapporteur) |
| Solides et volumes (cube, pavé), capacités | Les solides ; Mesures, périmètres, aires et volumes | partiel (cylindre, prisme : volume non traité) |
| Mesures de longueur, masse, capacité, monnaie ; périmètres et aires | Mesures, périmètres, aires et volumes | couvert |
| Durées et calendrier | Les durées | couvert |
| Organisation de données : tableaux, diagrammes en bâtons, moyenne | Lire un diagramme et calculer une moyenne | partiel (pas de diagramme circulaire ni de graphique en courbe) |
| Problèmes : partages, prix, vitesse moyenne, intérêts simples | Résoudre des problèmes | couvert |
| Nombres relatifs, calcul littéral | — (hors CM2 supposé) | manquant |

### CM2 (CEP) — Français

Existant (`cep-francais`, v2) : 11 fiches, 135 exercices (dont 55 d'auto-évaluation), 11 illustrations, 3 épreuves blanches.
Fiches : Les accords ; Conjugaison (présent, imparfait, futur, passé composé) ; Les homophones grammaticaux ; La phrase (types, formes, nature, fonction) ; La rédaction (récit, description, lettre) ; **nouvelles** : Synonymes, contraires, familles de mots, préfixes et suffixes ; L'orthographe d'usage ; Préparer et réussir une dictée ; Comprendre un texte (textes originaux) ; Organiser son texte (connecteurs, dialogue) ; L'impératif et le passé simple.

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Lecture et compréhension de textes courts | Comprendre un texte et répondre aux questions | couvert (textes originaux courts uniquement) |
| Vocabulaire : synonymes, contraires, familles de mots, préfixes, suffixes | Fiche vocabulaire | couvert |
| Orthographe d'usage (m devant b/m/p, ç, ge, mots proches) | Fiche orthographe d'usage | partiel |
| Orthographe grammaticale : accords, participe passé, homophones | Les accords ; Les homophones | couvert |
| Dictée préparée et dictée | Préparer et réussir une dictée | couvert (mini-dictée originale) |
| Grammaire : types/formes de phrase, nature et fonction | La phrase | partiel (propositions, voix passive, discours indirect non traités) |
| Conjugaison : présent, imparfait, futur, passé composé | Fiche conjugaison | couvert |
| Conjugaison : impératif, passé simple (3e personne) | L'impératif et le passé simple | couvert |
| Conjugaison : plus-que-parfait, conditionnel, subjonctif | — | manquant |
| Expression écrite : récit, description, lettre, dialogue, connecteurs | La rédaction ; Organiser son texte | couvert |
| Poésie, récitation, lecture à voix haute | — | manquant |

### CM2 (CEP) — Sciences

Existant (`cep-sciences`, v2) : 9 fiches, 108 exercices (dont 45 d'auto-évaluation), 11 illustrations, 3 épreuves blanches (45 min indicatives).
Fiches : Le corps humain ; Hygiène et santé ; Les plantes et l'environnement ; La matière et l'énergie ; **nouvelles** : Les animaux (groupes, régimes) ; L'alimentation (repas équilibré) ; Le cycle de l'eau et les mélanges ; La Terre, le Soleil et la Lune ; Les cinq sens.

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Corps humain : digestion, respiration, circulation | Le corps humain | couvert |
| Les cinq sens | Les cinq sens | couvert |
| Hygiène, santé, paludisme, eau potable, vaccination | Hygiène et santé | couvert |
| Alimentation et repas équilibré | L'alimentation | couvert |
| Les plantes, germination, environnement, déchets | Les plantes et l'environnement | couvert |
| Les animaux : classification simple, régimes | Les animaux | partiel (reproduction et chaînes alimentaires non traitées) |
| Matière : états de l'eau, cycle de l'eau, mélanges et séparations | La matière et l'énergie ; Le cycle de l'eau et les mélanges | couvert |
| Énergie, circuit électrique, sécurité | La matière et l'énergie | partiel |
| Terre, Soleil, Lune, jour et nuit, ombres | La Terre, le Soleil et la Lune | partiel (pas de saisons ni de phases de la Lune) |
| Air, forces, technologie (leviers, magnétisme) | — | manquant |
| Protection de l'environnement (forêts, pollution) | Les plantes et l'environnement | partiel |

### CM2 (CEP) — Histoire-Géographie-ECM

Nouveau (`cep-histoire-geo`, v1) : 6 fiches, 74 exercices (dont 30 d'auto-évaluation), 6 illustrations, 2 épreuves blanches (60 min indicatives).
Fiches : Les dix régions et leurs chefs-lieux ; Relief, climats, végétation et cours d'eau ; Les symboles de la République ; Les grands repères de l'histoire ; Les activités économiques ; Droits, devoirs et règles de vie. Seuls des faits solidement établis sont utilisés ; les dates (1884, 1916, 1919, 1960, 1961, 1972) et les fêtes nationales sont à confronter aux manuels (voir `reviewNotes`).

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Situation du Cameroun, régions et chefs-lieux | Les dix régions ; Relief et climats | couvert |
| Relief, climats, végétation, fleuves | Relief, climats, végétation et cours d'eau | partiel (pas de carte dessinée, pas de chiffres) |
| Histoire : peuples anciens, colonisation, indépendance, réunification | Les grands repères | partiel (royaumes et personnages historiques non développés, faute de source sûre) |
| Symboles nationaux, fêtes nationales | Les symboles de la République | couvert |
| Activités économiques et produits | Les activités économiques | partiel (aucune statistique) |
| ECM : droits et devoirs, règles de vie, sécurité routière | Droits, devoirs et règles de vie | couvert |
| Institutions (État, administration, justice) | — | manquant |
| Afrique, monde, organisations internationales | — | manquant |

### CM2 (CEP) — Anglais d'initiation

Nouveau (`cep-english`, v1) : 6 fiches, 74 exercices (dont 30 d'auto-évaluation), 6 illustrations, 2 épreuves blanches, audio en `en-GB`. L'existence même d'une épreuve d'anglais au CEP francophone est incertaine : ces épreuves sont des révisions.
Fiches : Greetings ; Numbers, days, months, time ; To be and have got ; My family and my school ; Daily routine (présent simple) ; Can, there is/are, prépositions.

| Thème du programme (reconstitué) | Couvert par | Statut |
|---|---|---|
| Salutations, se présenter | Greetings and introductions | couvert |
| Nombres, jours, mois, heure | Numbers, days, months and time | couvert |
| Verbes to be et have got | To be and have got | couvert |
| Famille, école, corps | My family and my school | couvert |
| Présent simple et routine | My daily routine | couvert |
| Can, there is/are, prépositions de lieu | Can, there is / there are, prepositions | couvert |
| Nourriture, couleurs, météo, animaux | — | manquant |
| Compréhension orale longue, chansons, dialogues | — | partiel (mots et phrases courtes en audio seulement) |

