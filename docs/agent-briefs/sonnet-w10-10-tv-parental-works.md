# w10-10 — CastBridge-TV : contrôle parental des œuvres (classification du catalogue, filtre par profil, code pour -16, boutique, rapport parental)

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon · statut : PRÊT
> **Groupe : W10c-3** (vague W10c) · prérequis : w10-01, interface WorkHub.rating ; après w5-18/w6-13/w11-04 s'ils sont lancés · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.Parental*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 10c · Effort M (≈ 1,5 j) · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W10 § 4.3, § 5.1-5.2 (profil enfant). Branche `claude/sonnet-w10-10`. Rapport : `docs/agent-reports/sonnet-w10-10.md`. Dépend de w10-01 (`Rating` d'une œuvre), de l'interface `WorkHub.rating(id)` (w10-08, en parallèle : coder contre l'interface). S'empile sur w5-18 (`Category.PURCHASES`) et w6-13 (collecte) **s'ils sont fusionnés** : aucun conflit attendu (ajouts).

## Objectif
Une œuvre est classée par le **catalogue** (plateforme ≥ producteur), jamais « non classée = adulte » comme une vidéo du foyer : un profil enfant ne **voit** que les œuvres que sa tranche autorise (`AgeBand.allows`), un adulte doit saisir le code parental pour une œuvre -16 si le réglage « code pour les vidéos -16 » existant est actif ; la boutique TV (si w5-15) masque les œuvres au-delà de la tranche ; la lecture d'une œuvre compte comme `UseKind.PLAY` ; le rapport parental mentionne le titre de l'œuvre selon `shareTitles` (W6) ou « une œuvre » sinon.

## Pourquoi (preuves)
- `C/parental/ParentalModel.kt:6-18` (`Rating`, `AgeBand.allows`) ; `docs/PARENTAL.md` (classement manuel par nom de fichier, « non classée = adulte », mode enfant, garde de lecture) ; `R/ParentalHub.kt` (`guardTile`, `filterHome`, garde `play*`, `categoryOf`).
- DESIGN-W5 § 6.6 : profil enfant ne commande jamais ; DESIGN-W6 § 2.2 (`shareTitles`).

## Fichiers possédés
`R/ParentalHub.kt`, `R/ParentalActivity.kt`, `C/parental/ParentalEngine.kt` (additif), `C/parental/ParentalReports.kt` (additif), `CT/Parental*Test.kt` ; `R/shop/ShopActivity.kt` (**si w5-15 fusionné** : filtre de classification, additif). **Hors zone** : `R/WorksActivity.kt` (w10-09 appelle les règles), `R/WorkHub.kt`.

## Étapes
1. `ParentalEngine` (pur) : `fun workAllowed(rating: Rating, profile: ChildProfile?): Decision` (`ALLOW`, `HIDE` (enfant, au-delà), `PIN` (adulte/sans profil, -16 et réglage actif)) ; `fun workPlayStarted(id)` ⇒ compte en `PLAY` ; tests (table tranche × classification × réglage).
2. `ParentalHub` : `visibleWork(id): Boolean`, `guardWorkPlay(activity, id, onAllowed)` (écran PIN existant si `PIN`), garde appelée **aussi** par la route `/api/oeuvres/play` (le téléphone ne contourne pas) ; `filterHome` : la tuile `oeuvres` reste visible en mode enfant si ≥ 1 œuvre autorisée ; `categoryOf(WorksActivity) = PLAY`.
3. `ParentalActivity` : page « Contrôle parental » gagne une ligne d'information : « Les œuvres locales sont classées par CastBridge (tous publics, -12, -16). Les profils enfants ne voient que celles de leur âge. » (pas de réglage nouveau : la classification n'est pas modifiable par le parent ; renversable).
4. Rapport parental : événement `work` dans `TvJournal`/`ReportBuilder` (champs additifs : `works: {played: n, min: m}` ; titres selon `shareTitles` si W6, sinon jamais de titre).
5. Boutique TV (si w5-15) : `ShopActivity` filtre les cartes d'œuvres par `visibleWork` et refuse « Louer » sous profil enfant (déjà la règle W5 : vérifier, additif).
6. Tests cœur : `HIDE` pour KID face à -12 ; `PIN` pour adulte face à -16 avec réglage ; `ALLOW` sinon ; aucune donnée parentale dans un événement de télémétrie (test `grep TvConnect` de w5-18 étendu à `WorksActivity` si présent).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.Parental*'   # vert
cd android && gradle --offline :receiver:compileDebugKotlin                     # compile
grep -rn 'TvConnect' android/receiver/src/main/kotlin/castbridge/receiver/Parental*.kt   # 0
```

## Cas limites
- Œuvre sans classification dans l'index (lot ancien) : traitée **-16** par prudence (jamais « adulte » : refusé à l'installation).
- Profil enfant actif pendant une lecture d'œuvre -16 déjà lancée par un adulte : la garde de reprise existante arrête à la bascule de profil.

## À ne pas faire
Pas de commit sur les branches partagées ; aucun envoi au serveur ; ne pas rendre la classification modifiable par le parent (décision) ; ne pas toucher `WorkHub` ni `WorksActivity` ; textes français.

## Rapport
`STATUT`, signatures de `workAllowed`/`visibleWork`/`guardWorkPlay` (pour w10-09), questions.
