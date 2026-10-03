# w18-01 — Cœur : `WdCredentials` (secret de groupe Wi-Fi Direct persistant par TV, remis une fois par un canal authentifié), `BootstrapPlan` (chemin du premier contact)

<!-- routage Fable 2026-10-03 -->
> **Amendement W19 (Fable, 2026-10-03)** : la Règle de symbiose (`EXECUTOR-PROMPT-TEMPLATE.md`, DESIGN-W19 § 5) s'applique. Les lignes additives `wd.id`, `wd.name`, `wd.pass`, `wd.persist` de `LinkInfo` sont une **capacité** : la déclarer `wdpersist` dans `C/sync/Caps.kt` (si w19-02 n'est pas fusionné : constante locale `WD_PERSIST_CAP = "wdpersist"` nommée dans le rapport) ; une TV sans cette capacité ⇒ chemin R-14 (mot de passe frais par groupe) **dit** par `BootstrapPlan`. Les refus de jonction (`JOIN_DENIED`, `rotated`) passent par `Reason` (w19-01) dès qu'il existe : codes `WD_JOIN_DENIED`, `WD_ROTATED` à proposer dans le rapport. Rapport : ajouter la ligne `SYMBIOSE: cap=wdpersist · proto=inchangé · reason=WD_JOIN_DENIED,WD_ROTATED · deux écrans=<test>`.
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (identifiants) · statut : PRÊT (pendant le gel : cœur seul)
> **Groupe : W18a-1** (vague W18a, cœur) · prérequis : branche `claude/auto-wifi-direct` fusionnée ou rebasée dessus · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.link.*' --tests 'castbridge.core.trust.PinBookTest' --tests 'castbridge.core.BtLinkTest'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · audit Opus : obligatoire

**Vague 18a · Effort M · Modèle : sonnet · Statut PRÊT.** Conception : `docs/coordination/DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` § 2.2, § 3.1, § 6, D-W18-2, D-W18-5, D-W18-6, D-W18-7. Branche `claude/sonnet-w18-01`. Rapport : `docs/agent-reports/sonnet-w18-01.md`. Dire « CastBridge » (téléphone) / « CastBridge-TV ». Règle W15 R5 : aucun fichier `S/`, `R/`.

## Objectif
Le secret qui permet au téléphone de rejoindre le groupe de la TV **sans geste** devient **persistant par TV** (R-14 le tirait frais à chaque groupe) : (1) `WdCredentials(id, name, pass)` : génération (`WifiDirect.groupNetworkName/groupPassphrase`, existants), validation, codage/décodage dans `LinkInfo` (CBTN et HELLO d'un pair **de confiance seulement**) et dans le lien profond QR (`castbridge://tv?…&wd=&wp=`), masquage dans tout `toString` ; (2) `PinBook` range `wd.<tvId>` à côté du code (`pin.<tvId>`), lu par identité de TV (jamais par 192.168.49.1), effacé par « Oublier », remplacé à la rotation ; (3) `BootstrapPlan.decide(facts)` : quel chemin de premier contact proposer (Bluetooth + PIN ; QR ; code seul si TV ancienne) et quelle phrase ; (4) la TV d'essai **a droit** au groupe (D-W18-6).

## Pourquoi (preuves)
- `C/tv/WifiDirect.kt` (branche R-14) : `groupPassphrase()` 16 car. ≈ 93 bits, `groupNetworkName()` `DIRECT-CB-<6>`, `Err.TRIAL` ; `C/tv/BtProtocol.kt` `LinkInfo` (`wd.cap`, `wd.err`, nom + mot de passe du groupe dans la réponse CBTN ; HELLO ne porte le mot de passe que pour le groupe du MENU) ; `C/link/WdClient.kt` : `Event.Creds.toString` masque le mot de passe (test `linkInfoNeverPrintsThePassphrase…`).
- `C/trust/PinBook.kt` (R-10) : `tvId(key, scope)`, `read/write` par identité, `castbridge_pins.xml` hors sauvegarde, test `PinBookTest` (24).
- `C/trust/PhoneLink.kt:23` `SavedTv(address, name, mdns, lastIps, port, addedAt, installId)`.
- DESIGN-W7 § 4.1 rang 6 : QR `castbridge://tv?id=&bt=&ip=&port=&pub=&sas=` (w7-17 `DeepLinkTv` non écrit : ce cahier fournit le **codage** ; w7-17 le consommera).
- Décision propriétaire : zéro geste après le PIN ⇒ un secret durable.

## Fichiers possédés
Nouveaux `C/link/WdCredentials.kt`, `C/link/BootstrapPlan.kt`, `CT/link/WdCredentialsTest.kt`, `CT/link/BootstrapPlanTest.kt`. Zones additives : `C/tv/BtProtocol.kt` (`LinkInfo` : champs `wd.id`, `wd.name`, `wd.pass`, `wd.persist=1` ; codage/décodage ; ≤ 25 lignes, **aucune** trame changée), `C/trust/PinBook.kt` (`wdOf(key, scope): WdCredentials?`, `writeWd`, `forgetWd`, effacement dans `tvReset`/oubli ; ≤ 40 lignes), `C/tv/WifiDirect.kt` (`Err.ROTATED = "rotated"` ; `Err.TRIAL` **retiré** de `ALL`). **Hors zone** : `C/link/BulkRoute.kt`, `C/link/WdClient.kt` (w18-02), `C/trust/HelloHandler.kt` (w18-03), tout `S/`, `R/`.

## Étapes
1. **Rouge** : `WdCredentialsTest` : (a) `WdCredentials.fresh(random)` ⇒ nom `DIRECT-CB-` + 6, mot de passe 16 car. de l'alphabet sans sosies, `id` = 8 hex ; (b) `encode()`/`decode()` aller-retour dans `LinkInfo` (lignes `wd.id=`, `wd.name=`, `wd.pass=`, `wd.persist=1`) ; une ancienne TV sans ces lignes ⇒ `null` (pas d'exception) ; une valeur invalide (nom hors `isValidNetworkName`, mot de passe hors `isValidPassphrase`) ⇒ `null` ; (c) `toString()` de `WdCredentials`, de `LinkInfo` et du lien QR imprimé par le journal masquent le mot de passe (`••••••`) et le nom au-delà de `DIRECT-CB-k7…` ; (d) QR : `toDeepLink(base)` ajoute `wd=` et `wp=` (percent-encodés), `fromDeepLink(uri)` relit, refuse un `wp` de plus de 63 car. ou un schéma autre que `castbridge://tv` ; (e) `PinBook.writeWd(tvId, creds)` puis `wdOf` par clé d'écran (nom, IP, `bt:`) via `tvId` ; `forget(tv)` efface `wd.<tvId>` **et** `pin.<tvId>` ; `tvReset` efface `wd.` ; une clé `127.0.0.1` (tunnel) ou `192.168.49.1` **ne crée jamais** d'alias d'identité (DESIGN-TV-CONTEXT § 1.1) ; (f) rotation : `writeWd` d'un nouvel `id`-même TV remplace ; (g) la TV d'essai : `WifiDirect.Err.ALL` ne contient plus `trial`.
2. `WdCredentials` : `data class WdCredentials(val id: String, val name: String, val pass: String)` avec `toString` masqué ; `companion fresh(random: java.util.Random = SecureRandom())`, `encodeInto(lines: MutableList<String>)`, `decodeFrom(map: Map<String, String>): WdCredentials?`, `toDeepLinkParams()`, `fromDeepLinkParams(p)`, `isValid()`.
3. `BootstrapPlan` : `data class Facts(hasBluetooth: Boolean, btPermission: Boolean, api: Int, tvHasBluetooth: Boolean?, tvSupportsPinHello: Boolean?, cameraAvailable: Boolean, knownTv: Boolean, wdKnown: Boolean)` ⇒ `Choice(primary: Path, alternatives: List<Path>, text: String)` avec `enum Path { BT_PAIR_THEN_PIN, QR, CODE_ONLY, NOTHING }` : Bluetooth disponible ⇒ `BT_PAIR_THEN_PIN` (alternative QR) ; pas de Bluetooth ou TV sans Bluetooth ⇒ `QR` ; TV ancienne (`tvSupportsPinHello == false`) ⇒ `BT_PAIR_THEN_PIN` avec la fenêtre « Autoriser » (texte dit) ; `wdKnown` ⇒ `NOTHING` (rien à faire : § 3). Textes français dans le fichier (un par issue), aucun ailleurs.
4. `PinBook` : clés `wd.<tvId>` = `name\tpass\tid` ; lecture pure ; écriture via `PinKv.write` existant ; jamais de clé par adresse.
5. KDoc français ; aucune I/O ; `SecureRandom` injectable.
6. **Vert** : porte ; `:core:test` complet ; `AutoWifiDirectTest` (27) toujours vert.

## Critères d'acceptation
Porte verte ; ≥ 12 tests rouges puis verts ; `grep -rn "pass" android/core/src/main/kotlin/castbridge/core/link/WdCredentials.kt | grep -i "log\|println"` vide ; test de source : aucun `wd.pass` dans un `toString` non masqué (`grep -rn "wd.pass" android/core/src/main/kotlin | grep toString` vide) ; aucune modification hors zone (`git status --porcelain`) ; rapport d'audit Opus : « les identifiants ne sortent que par `encodeInto` appelé sur un pair de confiance » (le test `BtLinkTest.anUntrustedPeerWithoutThePinGetsNoCredentials` reste vert et un jumeau HELLO est ajouté).

## Cas limites
Deux TV de même nom (le `tvId` les distingue) ; `wd.<tvId>` présent mais `pin.<tvId>` absent (jeton seulement : normal) ; mot de passe avec `\t` (impossible par construction ; `decode` refuse) ; QR sans `wd` (TV ancienne : chemin W7 inchangé) ; `LinkInfo` d'une TV R-14 (groupe frais, sans `wd.persist`) ⇒ identifiants **non persistés** côté téléphone (utilisés une fois, comme aujourd'hui).

## À ne pas faire
Ne pas dériver quoi que ce soit du PIN (§ 2.1 c rejeté) ; ne pas écrire dans `SavedTv` ; ne pas toucher aux trames CBTN/HELLO existantes (lignes additives seulement) ; pas d'écran ; pas de persistance Android.

## Rapport
`STATUT`, sorties rouge/vert, la forme exacte des lignes `LinkInfo` ajoutées, le point d'audit Opus (où le mot de passe peut sortir), questions : faut-il une **date de rotation** dans la fiche (recommandé : non, l'`id` suffit).
