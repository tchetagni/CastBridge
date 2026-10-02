# w2-11 — « Langues » sur CastBridge-TV : badge « voix de synthèse », limites dites une fois, focus initial

**Vague 2 · Effort S (≈ 4 h) · Statut PRÊT.** Branche `claude/sonnet-w2-11`. Rapport : `docs/agent-reports/sonnet-w2-11.md`.

## Objectif
1. Quand un média est marqué `synthetic: true`, l'écran l'indique (« Voix de synthèse ») — exigence de `docs/LANGUES.md` § 11.3 et `docs/MEDIA-PIPELINE.md` § 5.
2. Les limites de la version actuelle sont dites **une fois** sur l'accueil de Langues (« Langues (aperçu) : sans audio ni suivi de progression sur la TV pour l'instant ») et plus dans le flux de l'unité.
3. Le focus initial d'une unité se pose en haut (ancre « Lire ») et non sur le dernier bouton.
4. Correction de doc : `docs/LANGUES.md` § 11 (2 Go) vs § 13 (500 Mo) alignés.

## Pourquoi (preuves)
- `C/langues/LangPack.kt:159-160` exige `synthetic: true` pour un audio produit par moteur, mais `grep -n synthetic android/receiver/src/main/kotlin/castbridge/receiver/LanguesActivity.kt` → 0.
- `R/LanguesActivity.kt:147` « Audio non disponible sur la TV (lot média absent). », `:221` « Cette TV ne garde pas encore la progression des langues. » : trois « pas encore » dans l'unité.
- `R/LanguesActivity.kt:81,92` : `focusFirst()` après une longue liste de libellés → le focus tombe en bas (« Écouter » / « Faire les exercices »).
- `docs/LANGUES.md:15` (espace téléphone 2 Go) vs § 13 (quota 500 Mo) : contradiction signalée par l'audit contenu (M3).
- Audit : UX-10 (partie Langues), CO-5 (M7).

## Fichiers possédés
`R/LanguesActivity.kt`, `R/LanguesHub.kt`, `C/langues/LangPack.kt` (**accesseur** `isSynthetic(mediaId)` seulement, additif), `docs/LANGUES.md` (§ 11 ligne 1 et § 13). **Hors zone** : `PlayerActivity.kt` (w2-04 gère la tuile en essai), `TrialPolicy`, contenu `content/langues/**` (w3-05).

## Étapes
1. `LangPack` : `fun media(id): Media?` existe ? Sinon ajouter `fun isSynthetic(id: String): Boolean` (lit `media.json` déjà analysé).
2. `LanguesActivity.Run` : quand un bloc audio est joué ou listé, ajouter une ligne 22 px « Voix de synthèse » (ou l'icône existante si une icône adaptée existe dans `res/drawable` ; sinon texte) ; quand le fichier est absent, un seul libellé court « Audio : à recevoir du téléphone ».
3. Accueil de Langues (`LanguesActivity` écran de liste des packs) : une ligne d'état en haut : « Aperçu : audio et suivi de progression arrivent avec les prochains lots. » ; retirer les deux phrases « pas encore » de l'unité.
4. `unit()` : insérer une vue focusable invisible (ou le titre focusable) en tête et `requestFocus()` dessus ; `ScrollView.smoothScrollTo(0,0)`.
5. `docs/LANGUES.md` : § 11 tableau ligne 1 → « 500 Mo par défaut, réglable » (ou l'inverse si § 13 est l'erreur : lire les deux et trancher d'après la date la plus récente, § 13 = journal du 2026-10-01 après rapport).

## Critères d'acceptation
```sh
grep -n 'Voix de synthèse' android/receiver/src/main/kotlin/castbridge/receiver/LanguesActivity.kt   # ≥ 1
grep -c 'pas encore' android/receiver/src/main/kotlin/castbridge/receiver/LanguesActivity.kt           # 0
cd android && gradle --offline :core:test --tests 'castbridge.core.LanguesTest' --tests 'castbridge.core.langues.*'   # vert
cd android && gradle --offline :receiver:compileDebugKotlin   # compile (si SDK)
```
Observable (campagne, étape 36) : ouvrir Langues → ligne d'état en haut ; ouvrir une unité → focus sur le titre, D-pad bas descend dans le contenu.

## Cas limites
- Pack sans `media.json` : `isSynthetic` renvoie `false`, aucune ligne.
- En essai, la tuile est fermée (w2-04) : rien à faire ici.

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret ; ne pas ajouter d'audio ni de contenu ; textes en français.

## Rapport
`STATUT`, extraits des textes, sorties des commandes.
