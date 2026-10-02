# w14-10 — Cœur (tests) : `FakeTvMain`, la fausse TV sacrifiable du Mac (vraie `ReceiverServer` + registre + HELLO par TCP), scénarios en ligne de commande, tâche Gradle `:core:fakeTv`
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : aucune · statut : PRÊT
> **Groupe : W14-d** (vague W14, tranche S2) · prérequis : w14-01 fusionné (réutilise `TvSim`) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.journey.FakeTvMainTest'`
> **Jauge : ≈ 300 k jetons entrée / 18 k sortie** (effort M) · audit Opus : non

**Vague 14d (cœur/outils) · Effort M (≈ 1,5 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W14` § 2.1 (mode `fake`), D-W14-3. Branche `claude/sonnet-w14-10`. Rapport : `docs/agent-reports/sonnet-w14-10.md`.

## Objectif
Lancer `TvSim` (w14-01) **hors tests**, depuis le Mac, sur un port fixe, avec un scénario choisi, de sorte qu'un **vrai** téléphone (émulateur avec `adb reverse`, ou S21+ sur le LAN) parle à une TV au comportement connu et destructible : neuve, réinstallée (nouveau `installId`, nouveau PIN), PIN tourné, essai, pleine, lente, occupée. Exposer une route de contrôle `/__smoke/*` (hors `routes.txt`, préfixe réservé, **jamais** dans la vraie TV) pour changer de scénario et lire des compteurs.

## Pourquoi (preuves)
- La vraie TV ne doit jamais être une cible active (D-W14-3) ; l'émulateur TV n'a pas de Bluetooth et ne sait pas « se réinstaller » vite ; les cas R-01/R-03 exigent une TV **réinstallée**.
- `TvSim` (w14-01) a déjà `reinstall()`, `rotatePin()`, `setTrial()`, `fill()`, `slow()` ; `FakeTv` fait le HELLO sur flux ; il manque une écoute **TCP** du HELLO (le téléphone émulé n'a pas de RFCOMM) : `C/tv/BtProtocol.serve(input, output, …)` fonctionne sur n'importe quel flux ⇒ un `ServerSocket` suffit côté fausse TV ; côté téléphone, la boucle n'appelle que `BtTransport.connect(address)` (`C/trust/PhoneLink.kt`) : une implémentation TCP de `BtTransport` **en build debug** du téléphone est hors zone (noter dans le rapport : cahier W14 suivant ou W7 w7-16 ; en attendant, la fausse TV sert le chemin **PIN** et les routes HTTP, et le HELLO TCP est consommable par `PhoneSim` seulement).
- `DemoTv.kt` existe dans `CT/` (fausse TV de démonstration) : lire, ne pas dupliquer ; si elle couvre déjà une partie, l'étendre ou la citer.

## Fichiers possédés
Nouveaux : `CT/journey/FakeTvMain.kt` (`fun main(args)`), `CT/journey/SmokeControlApi.kt` (`ApiExtension` : `GET /__smoke/stats` {requêtes par route, `pinFailures`, transferts}, `POST /__smoke/scenario?name=`, `POST /__smoke/reinstall`, `POST /__smoke/rotate-pin`, `GET /__smoke/pin` **seulement si `--show-pin`**), `CT/journey/FakeTvMainTest.kt` (démarre en processus, change de scénario, lit les stats), `tools/smoke/fake_tv.sh` (lance `gradle --offline :core:fakeTv --args='--port 8765 --scenario fresh'`, écrit le pid et le PIN généré dans `tools/smoke/out/fake-tv.{pid,pin}` 0600, `--stop`). Zone de `android/core/build.gradle.kts` : ajout d'une tâche `JavaExec` `fakeTv` (classpath = `sourceSets.test.runtimeClasspath`, `mainClass = castbridge.core.journey.FakeTvMainKt`) **uniquement** ; rien d'autre dans ce fichier. **Hors zone** : `TvSim.kt` (extension privée si besoin), `S/**`.

## Étapes
1. `FakeTvMain --port N --hello-port N+1 --scenario {fresh,reinstalled,pin-rotated,trial,full,slow,busy} [--pin 123456] [--show-pin] [--dir D]` : construit `TvSim` sur le port demandé (`TvSim` accepte un port explicite : l'ajouter par paramètre par défaut 0 si absent, dans une extension), installe `SmokeControlApi`, affiche `base`, `helloPort`, `installId`, et `pin` seulement avec `--show-pin` (sinon `******`).
2. HELLO TCP : `ServerSocket(helloPort)` ⇒ pour chaque connexion, `tv.bt`'s handler sur les flux du socket (réutiliser ce que `FakeTv.serve` fait avec `Piped*`).
3. `SmokeControlApi` : routes sous `/__smoke/` ; `stats` compte par route grâce à un compteur posé dans `routeGuard` ou dans une surcharge de `serve` (ne pas modifier `ReceiverServer` : envelopper).
4. `fake_tv.sh` : démarrage en arrière-plan, attente de `GET /api/hello` ≤ 20 s, `--stop` par pid ; message d'aide avec la commande `adb reverse tcp:8765 tcp:8765` pour l'émulateur téléphone.
5. Test : démarre sur port 0, `GET /api/hello` 200, `POST /__smoke/scenario?name=trial` puis `PUT /upload/x` ⇒ 403 `trial`, `reinstall` change `installId`, `stats` compte.

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -c '__smoke' tools/routes/routes.txt` ⇒ 0 (routes de contrôle hors du fichier des routes de la vraie TV : `python3 tools/routes/list_routes.py --check` reste vert, car le code est dans `CT/`) ; `bash -n tools/smoke/fake_tv.sh` ; le PIN n'apparaît dans aucun fichier du dépôt ni dans la sortie par défaut.

## Cas limites
Port 8765 déjà pris sur le Mac ⇒ message et sortie 2 ; `hostCheck` : accepter `Host: 127.0.0.1:8765` **et** l'IP LAN du Mac (téléphone réel) : passer `hostCheck=false` seulement si le scénario l'exige (`--lan`), le dire dans l'aide ; scénario `slow` : `TvSim.slow()` doit ralentir l'écriture, pas le réseau.

## À ne pas faire
Aucune classe de production modifiée ; rien dans `android/receiver` ; aucune route `/__smoke` dans la vraie TV ; aucun PIN par défaut en clair dans un fichier committé (`--pin` reste une option de ligne de commande).

## Rapport
`STATUT`, commandes exactes pour lancer et arrêter, ce qui manque pour qu'un téléphone **émulé** fasse la liaison de confiance (transport HELLO TCP côté téléphone : contrat proposé), scénarios livrés.
