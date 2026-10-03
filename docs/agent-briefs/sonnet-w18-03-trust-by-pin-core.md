# w18-03 — Cœur : `TrustByPin` (un téléphone appairé qui présente le bon PIN dans HELLO devient de confiance sans la fenêtre « Autoriser » ; essais comptés par adresse Bluetooth)

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon · statut : PRÊT (pendant le gel : cœur seul)
> **Groupe : W18a-2** (vague W18a, cœur) · prérequis : w18-01 fusionné (zone `BtProtocol` en série) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.trust.*' --tests 'castbridge.core.BtLinkTest' --tests 'castbridge.core.HelloCompatTest'`
> **Jauge : ≈ 350 k jetons entrée / 18 k sortie** (effort M, ≈ 1,5 j) · audit Opus : échantillon

**Vague 18a · Effort M · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` § 2.1 (d), § 2.2 (étape 4), § 6, D-W18-4. Branche `claude/sonnet-w18-03`. Rapport : `docs/agent-reports/sonnet-w18-03.md`. Règle W15 R5 : aucun fichier `S/`, `R/`. Règle W18 R8 : sémantique du PIN de `docs/ADMIN.md` inchangée.

## Objectif
Le premier contact ne demande plus **deux** validations à la télécommande. Après l'appairage Android (comparaison numérique, que rien ne peut supprimer), le téléphone envoie HELLO **avec le PIN tapé par l'usager** sur le socket RFCOMM **appairé** ; la TV : PIN juste ⇒ confiance accordée et jeton rendu **sans** ouvrir la fenêtre « Ajouter un téléphone » ; PIN faux ⇒ compté par `PinGuard` **par adresse Bluetooth** (5 ⇒ 60 s, même que CBT1/CBTN), 1 octet d'erreur et un indice `ERR_BAD_PIN`/`ERR_LOCKED(retryAfter)` ; pair **non appairé** ⇒ rien ne change (1 octet d'erreur, aucun essai de PIN possible : le socket sécurisé l'exclut en amont). La fenêtre « Autoriser » reste pour un téléphone **sans** PIN (invité du foyer). Textes français des deux côtés.

## Pourquoi (preuves)
- `C/trust/HelloHandler.kt:36` `handle(peer, peerName, requestTrust)` ; `C/trust/PairingSession.kt` (fenêtre 2 min, 3 refus = 10 min) ; `C/tv/BtProtocol.kt` CBTH (drapeaux : bit 1 `HELLO_HAS_INSTALL_ID`), `pinProblem` pour CBT1/CBTN (PIN compté par adresse) ; `C/tv/Security.kt:27` `PinGuard(pin, 5, 60 s)` par clé `ip` (déjà utilisé avec `bt:<adresse>` pour RFCOMM, `docs/BT-PLUG-AND-PLAY.md` § Côté TV).
- `docs/BT-PLUG-AND-PLAY.md` § Sécurité : « un téléphone n'est de confiance qu'après (1) l'appairage Android et (2) Autoriser à la télécommande » ⇒ W18 remplace (2) par le PIN (présence devant l'écran) **ou** la fenêtre.
- `C/trust/PairFlow.kt:68` `WaitingOwner` ; `C/trust/LinkText.kt` (textes) ; `C/trust/PinBook.kt` `TvAuthReply` (`BadPin`, `Locked(retryAfterSec)`).

## Fichiers possédés
Nouveaux `C/trust/TrustByPin.kt`, `CT/trust/TrustByPinTest.kt`. Zones additives : `C/trust/HelloHandler.kt` (branche `pin != null` ≤ 20 lignes), `C/tv/BtProtocol.kt` (bit 2 `HELLO_HAS_PIN` : `u8 longueur | PIN` ; réponse d'erreur : octet d'indice `3` = PIN faux, `4` = verrouillé + `u16 secondes` ; ≤ 10 lignes), `C/trust/LinkText.kt` (3 phrases : « Tapez le code affiché sur la TV », « Code refusé. Il reste n essais. », « Trop d'essais : nouvel essai dans n s. »), `C/trust/PairFlow.kt` (étape `PairStep.WaitingPin` ≤ 15 lignes, émise **avant** `WaitingOwner` ; `run(tv, pin: String?)`). **Hors zone** : `S/`, `R/`, `C/trust/TrustRegistry.kt`.

## Étapes
1. **Rouge** : `TrustByPinTest` : (a) pair appairé, `requestTrust`, PIN juste ⇒ `HelloReply.Ok` avec jeton, registre contient le pair, **aucune** `PairingSession` ouverte ; (b) PIN faux ×4 ⇒ `Err(ERR_BAD_PIN, remaining=1)` ; ×5 ⇒ `Err(ERR_LOCKED, 60)` ; le **bon** PIN pendant le verrou ⇒ `ERR_LOCKED` (sinon le verrou ne ralentit rien) ; le compteur est **par adresse** : l'adresse B n'est pas verrouillée par A ; (c) pair **non appairé** avec PIN juste ⇒ `ERR_UNTRUSTED` 1 octet, **aucun** compteur touché (aucun essai possible) ; (d) téléphone de confiance qui envoie un PIN (inutile) ⇒ ignoré, `Ok` ; (e) ancienne TV (sans le bit) : le téléphone n'attend pas d'indice, parcours W7 (`WaitingOwner`) ; ancien téléphone (sans PIN) : réponse **identique** à aujourd'hui (fenêtre) ; (f) le PIN n'apparaît dans aucun `toString`, aucun `Redact.scrub` ne le laisse passer (`HelloCompatTest` étendu) ; (g) `PairFlow.run(tv, pin)` : `Bonding → WaitingPin → (Ok) Done` sans `WaitingOwner` ; `pin = null` ⇒ `WaitingOwner` comme avant.
2. `TrustByPin.decide(peerPaired, trusted, pin, guard: PinGuard, address)` ⇒ `Grant | Deny(code, retryAfter)` ; `HelloHandler` l'appelle **avant** `PairingSession` ; `PinGuard` partagé avec CBT1/CBTN (même instance, injectée).
3. Protocole : le PIN voyage **uniquement** dans la requête HELLO sur socket appairé ; jamais dans une URL ; longueur fixe 6 (`Pin.isValidFormat`) sinon `ERR_BAD_PIN` sans compter (format, pas un essai).
4. **Vert** : porte ; `:core:test` complet.

## Critères d'acceptation
Porte verte ; ≥ 10 tests rouges puis verts ; `HelloCompatTest` (vieux/nouveau des deux côtés) vert ; `grep -rn "\"X-CB-Pin\"" android/core/src/main/kotlin/castbridge/core/trust/TrustByPin.kt` vide ; aucune modification hors zone.

## Cas limites
PIN tourné pendant la saisie (`pinRotating`, `TvSignal`) ⇒ `ERR_BAD_PIN` avec `remaining` et le texte « Le code de la TV vient de changer : relisez-le » si la TV le sait ; `PairingSession` déjà ouverte par le propriétaire ⇒ le PIN gagne quand même (pas de double validation) ; TV verrouillée (W4, `locked`) ⇒ `ERR_LOCKED` sans compter ; 3 refus antérieurs ⇒ blocage 10 min de la session **n'empêche pas** le PIN (deux mécanismes distincts : dire lequel refuse).

## À ne pas faire
Ne pas changer la comparaison numérique Android (impossible) ; ne pas accepter un PIN sur un socket non sécurisé ; ne pas assouplir `PinGuard` ; ne pas écrire d'écran ; ne pas toucher `TrustRegistry`.

## Rapport
`STATUT`, sorties rouge/vert, le format exact du bit 2 et des indices, le point d'audit (où le PIN est lu), question : la fenêtre « Autoriser » doit-elle **aussi** se fermer quand un PIN juste arrive d'un autre téléphone (recommandé : non, elles sont indépendantes).
