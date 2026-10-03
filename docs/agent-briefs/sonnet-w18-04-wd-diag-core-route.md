# w18-04 — Cœur + une ligne : `WdDiag` (faits de la puce et du groupe ⇒ phrases françaises ⇒ verdict de la table terrain), route `GET/POST /api/diag/wifi-direct`

<!-- routage Fable 2026-10-03 -->
> **Amendement W19 (Fable, 2026-10-03)** : la Règle de symbiose s'applique. La route `GET/POST /api/diag/wifi-direct` est une **capacité** `diagwd` (`C/sync/Caps.kt`, w19-02 ; sinon constante locale nommée) : le téléphone ne l'appelle que si la TV la déclare, sinon la carte dit « Diagnostic Wi-Fi Direct : TV à mettre à jour ». Ses refus (401/403/400) passent par `Reason.envelope` (w19-01) : ne pas écrire un `{"error":…}` nu. Ses faits (`WdFacts`) peuvent être repris dans `GET /api/sync/state` par w19-06 : exposer une fonction pure `WdFacts.summaryJson()` sans secret (nom de groupe masqué, jamais de mot de passe). Rapport : ligne `SYMBIOSE: cap=diagwd · proto=inchangé · reason=<codes> · deux écrans=<test>`.
> **Modèle : sonnet** · escalade : aucune · statut : PRÊT (pendant le gel : cœur + une ligne de délégation)
> **Groupe : W18a-2** (vague W18a, cœur) · prérequis : aucun · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.link.WdDiagTest' --tests 'castbridge.core.tv.WdDiagRoutesTest' --tests 'castbridge.core.tv.*Routes*'`
> **Jauge : ≈ 250 k jetons entrée / 12 k sortie** (effort S, ≈ 1 j) · audit Opus : non

**Vague 18a · Effort S · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` § 7.2, § 7.3. Branche `claude/sonnet-w18-04`. Rapport : `docs/agent-reports/sonnet-w18-04.md`. Règle W15 R2/R9 : **une seule ligne** dans `C/tv/ReceiverServer.kt` (délégation), pas d'autre fichier chaud.

## Objectif
Imprimer en français, sur la TV et par l'API, les faits dont dépend tout W18 : la puce peut-elle être propriétaire de groupe (ou point d'accès), le groupe est-il monté, combien de clients, le module partage-t-il le bus USB avec la clé, quel débit a été mesuré, quelle erreur de création ; et rendre le **verdict** de la table § 7.2 (VERT / ORANGE a / ORANGE b / ROUGE) avec la suite recommandée. Tout ce qui décide est pur (`WdDiag`) ; la route est une classe additive (`WdDiagRoutes`) que `ReceiverServer` appelle en **une ligne** ; le relevé Android (sysfs, `WifiP2pManager`) appartient à w18-07.

## Pourquoi (preuves)
- `C/tv/UsbHardware.kt` (`UsbKeyInfo.sharesBusWithWifi`, `speedLabel`) ; `C/tv/WifiDirect.kt` `Err` ; `R/WifiDirectGroup.kt` (branche R-14 : `lastError`, `clients()`, `capable()`) ; `C/tv/ReceiverServer.kt:939` (`discard=1` : mode « réseau seul » du banc, pour mesurer sans disque) ; `C/trust/Diagnostics.kt` (« Copier le rapport », `Redact.scrub`) ; `tools/routes/routes.txt` (liste des routes, test de source).
- Le propriétaire doit lire ces faits en 10 minutes sans `iw` (binaire souvent absent) : on lit `/sys/class/net/*/`, `/sys/bus/usb/devices/*/product`, `PackageManager`.

## Fichiers possédés
Nouveaux `C/link/WdDiag.kt`, `C/tv/WdDiagRoutes.kt`, `CT/link/WdDiagTest.kt`, `CT/tv/WdDiagRoutesTest.kt` ; `C/tv/ReceiverServer.kt` (**une** ligne : `path.startsWith("/api/diag/wifi-direct") -> wdDiag.handle(s, p)`), `tools/routes/routes.txt` (2 lignes). **Hors zone** : `R/`, `S/`, `C/tv/UsbHardware.kt`.

## Étapes
1. **Rouge** : `WdDiagTest` : (a) `WdFacts` complets ⇒ une `Line(level, text)` par fait (feature, permission, Wi-Fi allumé, interface P2P, groupe actif + clients, point d'accès possible, concurrence STA, LAN, bus USB partagé, dernière erreur, débit mesuré, temps de jonction) ; **aucun** fait sans phrase (test de table) ; (b) `verdict(facts)` reproduit la table § 7.2 : GO oui + ≥ 3 Mo/s + LAN gardé ⇒ `GREEN_A_PERSISTENT` ; LAN perdu ⇒ `GREEN_A_ON_DEMAND_WITH_LAN` ; 1-3 Mo/s ⇒ `ORANGE_A` ; GO non + AP oui ⇒ `ORANGE_B_SOFTAP` ; ni l'un ni l'autre ⇒ `RED_BLUETOOTH` ; < 1 Mo/s ⇒ `RED_CAST_ORANGE_COPY` ; faits inconnus ⇒ `UNKNOWN` avec la phrase « Lancez le test terrain (10 min) » ; (c) `report(facts)` texte brut sans secret : nom du groupe masqué `DIRECT-CB-k7…`, jamais le mot de passe (il n'est pas dans `WdFacts` : le type l'interdit), adresses tronquées.
2. **Rouge** : `WdDiagRoutesTest` (vraie `ReceiverServer` sur boucle locale, comme `ContentIndexServerTest`) : `GET /api/diag/wifi-direct` sans identifiant ⇒ 401 ; avec PIN ou jeton ⇒ JSON `{facts, lines:[{level,text}], verdict, next}` ; `POST /api/diag/wifi-direct` corps `{"throughputBps":…, "joinMs":…}` (jeton) ⇒ mémorisé dans les faits (dernière mesure), 400 si hors bornes ; une TV d'essai ⇒ même réponse (route autorisée par `TrialPolicy` : ligne additive si nécessaire, sinon dire pourquoi non).
3. `WdDiag` : `data class WdFacts(feature: Boolean?, permission: Boolean?, wifiOn: Boolean?, p2pIface: String?, goActive: Boolean, clients: Int?, apCapable: Boolean?, staConcurrent: Boolean?, lanUp: Boolean, usbSharesBus: Boolean?, usbModule: String?, lastCreateError: String?, lastThroughputBps: Long?, lastJoinMs: Long?, groupNameMasked: String?)` ; `enum Verdict` ; `lines()`, `verdict()`, `next(verdict)` (phrase : « Lancer 18b », « Variante point d'accès », « Bluetooth optimisé »), `report()`.
4. `WdDiagRoutes(facts: () -> WdFacts, record: (Long?, Long?) -> Unit, auth)` : lit l'authentification par le même chemin que les autres routes (`TvCredential`/`PinGuard` : réutiliser l'aide existante de `ReceiverServer`, ne pas la dupliquer).
5. **Vert** : porte ; `tools/routes` test de source vert.

## Critères d'acceptation
Porte verte ; ≥ 12 tests rouges puis verts ; `git diff --stat android/core/src/main/kotlin/castbridge/core/tv/ReceiverServer.kt` = 1 ligne ajoutée ; `grep -n "pass" android/core/src/main/kotlin/castbridge/core/link/WdDiag.kt` vide ; aucune modification hors zone.

## Cas limites
`clients = null` (Android n'a pas répondu) ⇒ « nombre de téléphones inconnu » ; `feature = true` mais `lastCreateError = unsupported` ⇒ verdict ROUGE **avec** la phrase « la plateforme déclare le Wi-Fi Direct mais le pilote le refuse » ; mesure de débit vieille de plus de 7 jours ⇒ marquée « ancienne » ; `usbModule` contient un numéro de série ⇒ masqué (`UsbHardware.maskSerial`).

## À ne pas faire
Pas de relevé Android ici (w18-07) ; pas d'écran ; pas de secret dans `WdFacts` ; pas de nouvelle authentification ; ne pas toucher `UsbHardware`.

## Rapport
`STATUT`, sorties rouge/vert, l'exemple de JSON, la table verdict ⇒ suite, la ligne exacte ajoutée à `ReceiverServer`.
