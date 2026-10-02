# w10-09 — CastBridge-TV : écran « Œuvres » (D-pad, 720p), tuile d'accueil, fiche, lecture, compte à rebours, « Louer », télémétrie

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon · statut : PRÊT
> **Groupe : W10c-2** (vague W10c) · prérequis : interface WorkHub (w10-08) ; après w11-10 s'il est lancé · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : non (TV : propriétaire)

**Vague 10c · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W10 § 5.2, § 4.4, § 5.5. Branche `claude/sonnet-w10-09`. Rapport : `docs/agent-reports/sonnet-w10-09.md`. Dépend de l'interface `WorkHub` publiée par w10-08 (coder contre une **implémentation mémoire** `FakeWorkHub` dans le module tant que w10-08 n'est pas fusionné) et de w10-07 (`Telemetry.workPlay`, sinon appel derrière une interface).

## Objectif
Depuis l'accueil, la tuile **« Œuvres »** ouvre une activité D-pad : catégories (Tout, Sketches, Vidéos, Musique, Contes, Cours, Radio, Mes locations), grille de cartes (couverture, titre, producteur, durée, badge « Aperçu » / « Loué · 12 j restants » / « Louer »), fiche (synopsis, crédits, langue, classification, OK = lire, verte = Louer, jaune = Chaîne, bleue = Aide), lecture (audio : couverture plein écran + progression ; vidéo : lecteur existant), compte à rebours des locations (phrases de `RentalEngine`), hors ligne, profil enfant (filtre, pas de « Louer » : w10-10 fournit la règle), essai (aperçus seuls + « Passez en version complète »), mode réduit.

## Pourquoi (preuves)
- `R/PlayerActivity.kt` (`homeTools()`, `tile(feature, …)` via `TvConnect.feature` et `ParentalHub.guardTile` : DESIGN-W5 § 15 lignes « Accueil TV ») ; `R/GamesUi.kt` (`Dx`, grille 1920×1080, focus explicite) : patron à copier ; `R/TvCards.kt`.
- `C/lots/RentalEngine.kt` (phrases « Il vous reste 12 jours », avertissements) ; `R/RentalHub.kt` (état des locations).
- DESIGN-W5 § 8 (boutique TV : touches couleur, textes ≥ 28 px) ; `docs/PARENTAL.md` (mode enfant).

## Fichiers possédés
Nouveaux `R/WorksActivity.kt`, `R/WorksViews.kt`, `R/WorksTexts.kt`, `R/FakeWorkHub.kt` (debug/test seulement, derrière `BuildConfig.DEBUG`), `android/receiver/src/main/res/drawable/ic_t_works.xml` ; `R/PlayerActivity.kt` (**`homeTools` : une tuile**), `R/HomeScreen.kt` (si une liste d'ids y est figée), `android/receiver/src/main/AndroidManifest.xml` (une activité `exported=false`), `C/tv/HomeTools.kt` (si w3-11 fusionné : l'id `oeuvres`). **Hors zone** : `R/WorkHub.kt` (w10-08), `R/ParentalHub.kt` (w10-10), `R/shop/**` (W5).

## Étapes
1. `WorksTexts` : tous les textes (FR) : titre « Œuvres locales », sous-titre « Sketches, courts métrages, musique, contes, cours, radio : des artistes d'ici », « Aperçu gratuit », « Louer 30 jours », « Loué · il vous reste … », « Location terminée : … Reprendre la location ? », « Classé -12 : réservé aux plus de 12 ans », essai : « Version d'essai : seuls les aperçus sont disponibles. Passez en version complète pour louer. », réduit : `DegradedPolicy.MESSAGE`, hors boutique : « Pour louer : ouvrez CastBridge sur votre téléphone (Boutique) ou voyez votre point focal. Code TV : XXXX-XXXX-XXXX-XXXX ».
2. `WorksActivity` : vues classiques (pas de Compose, pas de WebView), colonne gauche + grille (cartes 4 × 2 à 720p), focus visible, `contentDescription` partout, textes ≥ 28 px, jamais la couleur seule ; données : `WorkHub.list()` + catalogue des œuvres (`WorkHub.catalog()` : titres, synopsis, producteurs pour les œuvres **non encore sur la TV** : affichées « À louer » avec couverture générique) ; filtre par `ParentalHub` (règle de w10-10 : `visibleWork(id)` ; en attendant, filtre local par `AgeBand.allows`) ; Retour ferme.
3. Fiche : OK ⇒ `WorkHub.play(id)` (aperçu ou œuvre louée) ; audio : écran de lecture (couverture, titre, producteur, barre de progression, lecture/pause/±10 s, Retour) ; vidéo : `PlayerActivity` existant via l'URL loopback ; position reprise.
4. « Louer » (verte) : si `ShopActivity` (w5-15) existe (`Class.forName` ou drapeau) ⇒ l'ouvrir sur l'article `loc-oeuvre-<id>|30` ; sinon écran texte avec le code TV ; en essai : message d'essai ; en réduit : message réduit ; profil enfant : « Demandez à un parent ».
5. « Chaîne » (jaune) : page producteur (nom, bio du catalogue, ses œuvres) ; « Louer toute la chaîne » même règle.
6. Mes locations : contrats couvrant des œuvres (`RentalHub` : bouquets `oeuvre-*`, `chaine-*`), compte à rebours, « Renouveler » (même règle que « Louer »).
7. Tuile `oeuvres` dans `homeTools` après `learn` ; `TrialPolicy.tileAllowed` : visible en essai (aperçus) ; `ParentalHub.guardTile` ; en mode enfant : visible si au moins une œuvre autorisée.
8. Télémétrie : à la fin d'une lecture (ou arrêt), `Telemetry.workPlay(id, pct, ms, teaser)` (niveau usage : rien sans consentement) ; `feature_used{feature: oeuvres}`.
9. Tests : logique de présentation extraite dans `C/`-style objets purs si possible (`WorksPresenter` dans `R/` testé par Robolectric **non requis** : tests JVM sur les fonctions pures de tri/filtre/badges dans `CT/` **interdits** (hors zone) ⇒ les mettre dans `R/WorksViews.kt` avec un test `:receiver` unitaire si l'infrastructure existe, sinon vérification manuelle listée au rapport).

## Critères d'acceptation
```sh
cd android && gradle --offline :receiver:compileDebugKotlin     # compile
grep -c 'oeuvres' android/receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt   # ≥ 1 (tuile)
grep -n 'exported="false"' android/receiver/src/main/AndroidManifest.xml | grep -i works    # activité non exportée
# TV de référence : navigation complète à la télécommande ; aperçu ; œuvre louée ; compte à rebours ; profil enfant -12 ne voit pas une œuvre -16 ; essai : aperçus seuls ; captures dans docs/img/works/
```

## Cas limites
- Catalogue des œuvres absent (jamais relayé) : la grille montre seulement ce qui est installé.
- Œuvre installée mais clé de location détruite (fin) : carte « Location terminée », OK ⇒ message, pas de lecture.
- Clé USB retirée : cartes « indisponible (clé USB retirée) ».

## À ne pas faire
Pas de commit sur les branches partagées ; pas de Compose ni de WebView sur la TV ; pas de téléchargement ; ne pas modifier `WorkHub`, `ParentalHub`, `ShopActivity` ; textes français ; aucun montant codé (les prix viennent de la grille via W5).

## Rapport
`STATUT`, captures, mesures (temps d'ouverture, mémoire), ce qui dépend de w5-15, questions.
