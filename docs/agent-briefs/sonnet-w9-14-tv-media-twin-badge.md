# w9-14 — CastBridge-TV : consommateur des lots média (Langues et Apprendre), lecture `.opus`/`.m4a`/`.mp4`, badge « voix de synthèse », journal local des lectures

**Vague 9d · Effort L (≈ 3-4 j) · Modèle : sonnet · Statut PRÊT (compilation et test sur la TV : propriétaire).** Conception : guide § 1.1 (points d'attention), § 1.3 (limite honnête), § 5.3, § 5.6, § 7 point 6 ; décision D6. Branche `claude/sonnet-w9-14`. Rapport : `docs/agent-reports/sonnet-w9-14.md`.

## Objectif
(1) Cœur (JVM, testé) : `LangMediaLotConsumer` (feature `langmedia`, scope `<cible>-<niveau>-<thème>`) et `LearnMediaLotConsumer` (feature `learn`, scope `<classe>-media`) implémentant `LotConsumer` (`install` atomique : déballer dans un dossier temporaire, vérifier que chaque fichier listé par `media.json` / `lot.json` existe avec la bonne taille, puis échanger ; `installed()` avec taille sur disque ; `unplace`) ; **emplacement** : le volume de la bibliothèque (`Download/CastBridge/Medias/lots/<feature>/<scope>/`, règle `docs/LANGUES.md:273`, `docs/STORAGE.md`), **jamais** les 10 Mo de `TvLotStore` ; (2) `LanguesHub.mediaFile` cherche d'abord ce volume, puis `filesDir/lots/langues-media` (compatibilité) ; `LanguesActivity.play` : si `.opus` échoue (`MediaPlayer` exception ou `onError`), essaie le `.m4a` jumeau ; **badge** : à côté de chaque bouton audio dont l'entrée `media.json` porte `synthetic: true`, le libellé « voix de synthèse » (texte, 22 px, couleur `TEXT_MEDIUM`, pas seulement une couleur) ; (3) lecture d'un short `.mp4` d'un lot média Apprendre depuis une fiche (`media: ["id"]`) via un écran minimal `VideoView`/`MediaPlayer` + `SurfaceView` (pas libVLC : trop lourd pour un clip de 30 s ; télécommande : OK = pause, RETOUR = fermer), affiche WebP affichée avant lecture ; (4) **journal local** des lectures de médias (`media_play` : `id` haché par `Telemetry.hashForCounting`, `kind`, `ms`, `pct`, `replays`), gardé dans le même magasin que les événements Apprendre (500 derniers) et exposé par `GET /api/learn/events?since=` **sans** être envoyé au serveur (aucun événement nouveau dans le catalogue : D3-b).

## Pourquoi (preuves)
- `android/receiver/.../LanguesHub.kt:14-15,52-56` (le jumeau média n'est pas livré ; chemin `filesDir/lots/langues-media/<scope>/<file>`), `LanguesActivity.kt:143-155` (bouton audio, `MediaPlayer`, aucun badge).
- `docs/LOTS.md:146-152` (brancher une fonction : `LotConsumer`, `LotsHub.register`, atomicité), `android/core/.../lots/LotApi.kt` (contrat, **ne pas modifier**), `TvLotStore.kt:52` (plafond strict 10 Mo : les médias n'y vont pas).
- `docs/LANGUES.md:271-273` (TV : médias dans le volume de la bibliothèque, par lot et sur demande), `docs/MEDIA-POLICY.md:15` (jamais de média lourd dans un lot TV), `docs/LEARN.md:168,512` (bloc `media`, journal des événements).
- `RECOMMANDATIONS-FABLE-2026-10-02.md:81` (CO-5 : badge `synthetic` absent) ; `docs/TELEMETRY.md:60` (`hashForCounting`), `:57-59` (clés interdites).
- minSdk 26 (`android/receiver/build.gradle.kts:14`) : Ogg/Opus et MP4/H.264/AAC lisibles en théorie par `MediaPlayer` ; **à prouver sur la TV de référence**.

## Fichiers possédés
Nouveaux `C/langues/LangMediaLotConsumer.kt`, `C/learn/LearnMediaLotConsumer.kt`, `CT/langues/LangMediaLotConsumerTest.kt`, `CT/learn/LearnMediaLotConsumerTest.kt` ; `R/LanguesHub.kt`, `R/LanguesActivity.kt`, `R/LearnHub.kt` (enregistrement du consommateur), nouveau `R/MediaClipActivity.kt` + entrée `AndroidManifest.xml` de `:receiver` (une ligne). **Hors zone** : `C/lots/*`, `R/LotsHub.kt` (utiliser `register`), `R/PlayerActivity.kt`, `R/LearnActivity.kt` (un seul point d'appel autorisé : ouverture de `MediaClipActivity` depuis un bloc `media` ; si cela exige plus, poser la question), `:sender`.

## Étapes
1. Consommateurs cœur + tests : installation atomique, échec laisse la version précédente, `installed()` exact, lot sans `media.json` refusé, chemin sortant refusé, taille comptée sur disque.
2. `LanguesHub` : enregistrement du consommateur `langmedia` dans `LotsHub` ; `mediaFile` à deux emplacements ; API inchangée.
3. `LanguesActivity` : badge ; secours `.m4a` ; bouton « Rejouer » compté ; journal.
4. `LearnHub` : consommateur `learn:<classe>-media` ; `MediaClipActivity` ; affiche puis lecture ; journal `pct` à la fermeture.
5. **Protocole de test sur la TV de référence** (dans le rapport, pour le propriétaire) : copier un lot média de test (fourni par w9-09 en dry-run réel sur le Mac) par clé USB ; vérifier `.opus`, `.m4a`, `.mp4` ; chronométrer l'ouverture d'un short (< 2 s) ; vérifier le badge ; lire `GET /api/learn/events`.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.langues.*' --tests 'castbridge.core.learn.LearnMediaLotConsumerTest'   # vert
cd android && gradle --offline :receiver:compileDebugKotlin   # compile (propriétaire)
grep -n "voix de synthèse" android/receiver/src/main/kotlin/castbridge/receiver/LanguesActivity.kt   # ≥ 1
grep -rn "DeviceClient\|TelemetryUploader" android/receiver/src/main/kotlin/castbridge/receiver/MediaClipActivity.kt   # 0 (rien n'est envoyé)
```
Observable (TV de référence) : un `.opus` de `zh-a0-salut` joue avec le libellé « voix de synthèse » ; un short MP4 480p s'ouvre en < 2 s et se ferme avec RETOUR ; `GET /api/lots` ne montre **aucune** augmentation d'`usedBytes` (les médias ne comptent pas dans les 10 Mo).

## Cas limites
Volume USB absent ⇒ consommateur refuse l'installation avec « clé USB absente » (la file du téléphone réessaie) ; `.opus` et `.m4a` absents ⇒ message existant « lot média absent » ; MP4 que `MediaPlayer` refuse ⇒ message « format non lisible sur cette TV » et journal `error` local ; TV à 1 Go : libérer `MediaPlayer` dans `onStop` ; mouvement réduit : l'affiche reste, pas de lecture automatique.

## À ne pas faire
Pas de nouvelle permission ; pas de libVLC pour les clips ; aucun événement vers le serveur ; ne pas toucher `TvLotStore`, `LotApi`, le lecteur de bibliothèque ; pas de lecture automatique d'un short à l'ouverture d'une fiche.

## Rapport
`STATUT`, points d'appel exacts, résultats du protocole TV (à remplir par le propriétaire), formats lus/refusés par la TV de référence, taille des changements.
