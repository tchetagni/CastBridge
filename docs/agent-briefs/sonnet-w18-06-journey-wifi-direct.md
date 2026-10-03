# w18-06 — Harnais W14 : `RadioSim` (P2P, Bluetooth, LAN factices) et 9 parcours J-WD de bout en bout sur la JVM

<!-- routage Fable 2026-10-03 -->
> **Amendement W19 (Fable, 2026-10-03)** : `RadioSim` de ce cahier et `RadioSim` de voie de w19-04 (`CT/compat/RadioSim.kt`) doivent être **un seul** fichier : si w19-04 est fusionné d'abord, l'étendre (P2P, Bluetooth, LAN) au lieu d'en créer un second ; sinon, écrire le vôtre de façon à ce que w19-04 l'étende (API `up(route)`, `down(route)`, `flip(seq)`, bases par route). Chaque parcours J-WD affirme désormais **l'état des deux côtés** (téléphone : `WdLine`/`XferState` ; TV : `TransferProgress`/registre des refus) avec `SymbiosisKit` (w19-08 ; sinon assertions locales équivalentes), et la perte du groupe pendant une copie (RS-02) ou un cast (RS-24) vérifie « aucun octet confirmé renvoyé » et « raison visible ». Rapport : ligne `SYMBIOSE: deux écrans=WdJourneyTest`.
> **Modèle : sonnet** · escalade : aucune · statut : PRÊT (pendant le gel : tests seulement)
> **Groupe : W18a-4** (vague W18a, harnais) · prérequis : w18-01…05 fusionnés · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.journey.WdJourneyTest' --tests 'castbridge.core.journey.*'`
> **Jauge : ≈ 450 k jetons entrée / 22 k sortie** (effort M, ≈ 2 j) · audit Opus : non

**Vague 18a · Effort M · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` § 2.2, § 3, § 5.3, § 7.2, § 9 ; cadre : `DESIGN-W14-BARRIERE-ANTI-REGRESSION-2026-10-02.md` (harnais `CT/journey/`). Branche `claude/sonnet-w18-06`. Rapport : `docs/agent-reports/sonnet-w18-06.md`. Règle W15 R5 : aucun fichier `S/`, `R/`.

## Objectif
Prouver sur la JVM, avec de **fausses radios** et une fausse horloge, que les décisions pures de W18 (w18-01…05) enchaînées avec le cœur existant (`HelloHandler`, `PairFlow`, `LinkDriver`, `TransferQueue` pure, vraie `ReceiverServer` sur boucle locale) donnent exactement les parcours promis au propriétaire, et que chaque issue a sa phrase. Le harnais sert aussi à w18-07/08 (ils rejouent ces parcours après câblage) et à la fumée W14.

## Pourquoi (preuves)
- `CT/journey/TvSim.kt`, `HarnessSmokeTest`, `PinJourneyTest`, `TransferJourneyTest` (W14/W15) : gabarit existant ; `CT/link/AutoWifiDirectTest.kt` (automate en temps simulé) ; `CopyQueueServerTest` (vraie `ReceiverServer`).
- Le Wi-Fi Direct ne se teste pas sur la JVM : on teste **tout ce qui décide autour** ; le reste est le test terrain § 7.

## Fichiers possédés
Nouveaux `CT/journey/RadioSim.kt` (P2P : `createGroup`, `connect(name, pass)` ⇒ `Joined`/`Denied`, `lost()`, clients ; BT : appairage, socket sécurisé/insécurisé, HELLO ; LAN : présent/absent/isolé), `CT/journey/WdPhoneSim.kt` (le téléphone : `WdSession` + `WdPolicy` + `PinBook` mémoire + `PairFlow` + file), `CT/journey/WdJourneyTest.kt` ; `CT/journey/TvSim.kt` (zone additive ≤ 8 lignes : `wdCredentials`, `trustByPin`). **Hors zone** : tout `C/`, `S/`, `R/`.

## Étapes
1. **Rouge** (chaque parcours échoue d'abord par assertion contre une ébauche qui compile, sortie collée au rapport) :
   - **J-WD-1 Première fois par Bluetooth + PIN** : téléphone inconnu, pas de LAN ; « Ajouter ma TV » ⇒ appairage (sim) ⇒ `WaitingPin` ⇒ PIN juste ⇒ `Done` **sans** `WaitingOwner` ⇒ identifiants reçus dans HELLO ⇒ `PinBook.wdOf` non nul ⇒ `WdSession.Up` sans CBTN ⇒ copie de 200 Mo sur 192.168.49.1 (vraie `ReceiverServer`) ⇒ `/api/info` la voit ; **aucun** mot de passe dans le journal de la sim.
   - **J-WD-2 Première fois par QR** : pas de Bluetooth ; lien profond avec `wd`/`wp` ⇒ `Up` ⇒ HELLO HTTP (PIN tapé) ⇒ copie.
   - **J-WD-3 Zéro geste** : lendemain, app ouverte, identifiants connus, groupe TV allumé ⇒ `Up` en ≤ 1 jonction, aucun effet `RequestGroup`, aucune `AskOnce` ; copie + télécommande + « Lire en direct » (URL sur 192.168.49.x) sur la même session.
   - **J-WD-4 Perte en plein cast** : `lost()` à 40 % ⇒ `Join` immédiat ⇒ `Up` ⇒ `playUrl` renvoyé à la position connue ; 3 pertes en 10 min ⇒ ligne orange « Bluetooth (lent) · Wi-Fi Direct : nouvel essai dans 10 min » ; vidéo ⇒ `Suggest(COPY_AND_PLAY)`.
   - **J-WD-5 Deux TV** : identifiants pour A et B ; copie vers A puis vers B ⇒ `Leave(A)`, `Join(B)` ; jamais deux `Up` ; identité vérifiée par `id` à la sonde (une sim qui répond avec l'`id` de A sur B ⇒ refus).
   - **J-WD-6 TV d'essai** : `trial = true` ⇒ WD permis ; `TrialPolicy` refuse toujours ce qu'il refusait.
   - **J-WD-7 Android 10-12** : `method = NETWORK_SPECIFIER` ⇒ première jonction `AskOnce(SYSTEM_DIALOG)` puis `rememberedApproval` ⇒ plus jamais.
   - **J-WD-8 Rotation** : « Retirer ce téléphone » sur la TV ⇒ nouveau secret ; téléphone B (toujours de confiance) réapprend au HELLO ; téléphone A (retiré) ⇒ `Denied` ⇒ `ForgetCreds` ⇒ ligne « Le mot de passe Wi-Fi de la TV a changé… » ; pas de boucle (≤ 1 CBTN, refusé `ERR_UNTRUSTED`).
   - **J-WD-9 Verdict rouge** : `wd.cap = 0` ⇒ route BT ; copie < 5 Mo par BT **VERT** ; vidéo 400 Mo ⇒ `BtPlan` absent (w18-13) ⇒ ligne orange avec ETA honnête (`RoutePolicy.etaMs` ou calcul local 200 ko/s) ; cast audio par le tunnel OK, cast vidéo refusé avec la phrase.
2. `RadioSim` : horloge injectée (`FakeClock` existant), événements ordonnés, **aucun** `Thread.sleep`.
3. Ajouter chaque parcours à `docs/test-plans/PARCOURS-CRITIQUES.md` (P-50…P-58) : **une ligne chacun** (fichier possédé par personne d'autre en W18 ; dire dans le rapport si un autre cahier l'a modifié : rebase).
4. **Vert** : porte ; `:core:test` complet.

## Critères d'acceptation
Porte verte ; 9 parcours, chacun rouge par assertion puis vert ; durée totale < 20 s ; `grep -rn "Thread.sleep" android/core/src/test/kotlin/castbridge/core/journey/RadioSim.kt` vide ; aucune modification hors zone.

## Cas limites
Groupe TV absent au moment de la jonction (TV en veille) ⇒ `JOIN_TIMEOUT` ⇒ CBTN (si BT) ⇒ la TV crée le groupe ⇒ `Up` ; TV R-14 (identifiants frais, `wd.persist` absent) ⇒ `VIA_BT` à chaque session ; LAN qui revient pendant `Up` ⇒ la copie suivante part par le LAN, la session WD part après 2 min.

## À ne pas faire
Aucun code de production ; aucune TV réelle ; ne pas copier la logique dans la sim (la sim n'a que des radios, les décisions viennent de `C/`).

## Rapport
`STATUT`, les 9 sorties rouge/vert, la liste des effets que w18-07/08 doivent exécuter dans le même ordre, les parcours ajoutés à `PARCOURS-CRITIQUES.md`.
