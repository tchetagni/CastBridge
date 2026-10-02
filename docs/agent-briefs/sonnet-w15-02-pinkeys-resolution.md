# w15-02 — Téléphone : résolution tolérante des clés de PIN et de jeton (`PinKeys`), fin du « PIN demandé à un téléphone de confiance »
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (secret présenté aux transferts, règle « jamais deux fois le même PIN ») · statut : PRÊT
> **Groupe : W15-S0-a** (vague W15, tranche S0) · prérequis : aucun ; W13 w13-08 (`PinStore.putAll`) et W14 w14-05 (`PinKeys`) **se rebasent** sur ce cahier · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.trust.PinKeysTest' --tests '*SendChoice*' --tests '*Trust*'`
> **Jauge : ≈ 250 k jetons entrée / 14 k sortie** (effort S-M, ≈ 1 j) · audit Opus : oui

**Vague 15 S0 (cœur + téléphone) · Effort S-M · Modèle : sonnet · Statut PRÊT.** Branche `claude/sonnet-w15-02`. Rapport : `docs/agent-reports/sonnet-w15-02.md`. Règle : test rouge d'abord.

## Objectif
Toute clé qu'un écran du téléphone peut produire pour désigner une TV (nom mDNS, nom affiché, « X (Bluetooth) », `ip`, `ip:port`, URL complète, `bt:<adresse>`, `127.0.0.1:<port>` du tunnel, nom NSD « (2) ») retrouve la **TV enregistrée** et donc son **jeton** ; le PIN mémorisé n'est présenté que s'il n'existe aucun jeton ; la résolution est une fonction **pure du cœur**, testée par table.

## Pourquoi (preuves)
- `S/TvLink.kt:199-200` `savedFor` n'accepte que `mdns`, `name`, `bt:ADDR` ou `ip:port` des IP enregistrées ; `:208` `savedForHost` ne connaît que `lastIps` ⇒ `credentialForBase("http://127.0.0.1:18765")` toujours `null` (`S/DownloadService.kt:74`).
- Clés d'écran qui ratent : `S/TvDiscovery.kt:34` (« `${gw.tv} (Bluetooth)` »), `S/TvScreen.kt:77` (IP brute sans port ; `WifiDirect.BASE_URL`), NSD « (2) », deux TV homonymes (`firstOrNull`).
- `S/PinStore.kt:22` retombe en silence sur `sp.getString(key)` ⇒ `""` ⇒ aucun en-tête ⇒ compté comme PIN faux par la TV (`C/tv/Security.kt:44-46`).
- Chaîne de l'incident R-01 : `DESIGN-W13` § 1.2 et § 1.4 ; registre `PLAN-STABILISATION` § 2.3 L-01.
- Existant à réutiliser : `C/trust/PhoneLink.kt` (`SavedTv`, `SavedTvs`), `C/trust/SendChoice.kt` (modèle de fonction pure + test de table `CT/SendChoiceTest.kt`).

## Fichiers possédés
Nouveaux `C/trust/PinKeys.kt`, `CT/trust/PinKeysTest.kt` ; `S/PinStore.kt` ; `S/TvLink.kt` (**zone** `savedFor`/`savedForHost`/`credentialFor`/`credentialForBase` `:191-208` seulement). **Hors zone** : `C/trust/LinkDriver.kt`, `C/trust/TrustRegistry.kt`, tous les écrans (`TvHome`, `TvScreen`, `TvDiscovery`, `OpenWithActivity`…), `R/**`.

## Signatures (contrat pour W13 w13-08/09 et W14 w14-05)
```kotlin
package castbridge.core.trust
object PinKeys {
    /** Every key an app screen may use for this TV, normalised; never empty. */
    fun keysOf(tv: SavedTv, apiPort: Int = 8765, tunnelPort: Int? = null): List<String>
    /** Finds the saved TV a screen key designates, tolerant to "(Bluetooth)", "(2)", host without port, full URL, tunnel loopback (default TV), case. Null = unknown. */
    fun resolve(key: String, saved: List<SavedTv>, default: SavedTv?, apiPort: Int = 8765, tunnelPort: Int? = null): SavedTv?
    fun normalise(key: String): String
}
```

## Étapes
1. **Rouge** : `PinKeysTest` table ≥ 14 lignes, une par origine d'écran (`TvHome` nom, `TvScreen` IP sans port, `TvScreen` `ip:port`, `WifiDirect.BASE_URL`, `TvDiscovery` « (Bluetooth) », NSD « (2) », `bt:` majuscules/minuscules, `127.0.0.1:18765` ⇒ TV par défaut, nom mDNS, nom renommé, deux TV homonymes avec adresses BT distinctes ⇒ celle dont l'IP correspond, clé vide ⇒ `null`, clé inconnue ⇒ `null`) ; `keysOf` contient toutes ces formes ; aucun test ne passe avant l'implémentation (le fichier n'existe pas).
2. Implémenter `PinKeys` (pur, sans `android.*`).
3. `TvLink.savedFor/savedForHost` ⇒ `PinKeys.resolve(...)` ; `credentialForBase` gère le tunnel (`tunnelPort` = port de `BtSshGateway.ensureApi` s'il est connu, sinon `null`).
4. `PinStore.get(key)` : jeton par `PinKeys.resolve` ; sinon PIN mémorisé sous **n'importe quelle** clé de `keysOf(tv)` (lecture tolérante) ; sinon `""`. `put(key, pin)` : écrit sous **toutes** les clés de `keysOf(tv)` quand la TV est connue (c'est la moitié de D-W13-7 ; w13-08 ajoutera l'effacement sur `PIN_WRONG`).
5. Vert : porte ; `:sender:compileDebugKotlin`.

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -rn "import android" C/trust/PinKeys.kt` vide ; `grep -n "firstOrNull { it.mdns == key" S/TvLink.kt` vide (ancienne résolution retirée) ; les 14 lignes de table citées rouges (fichier absent) puis vertes ; `:sender:compileDebugKotlin` OK.

## Cas limites
TV sans `mdns` ni `lastIps` (Bluetooth seul) ⇒ résolue par nom et `bt:` ; port non standard ; IPv6 avec crochets ; nom contenant « (Bluetooth) » réellement.

## À ne pas faire
Ne pas changer la sémantique de `TvCredential` ni le format des préférences `castbridge_pins` (ajouts de clés seulement) ; aucun texte utilisateur ; pas de nouvel essai de PIN ; ne pas toucher aux écrans (w13-09 le fera).

## Rapport
`STATUT`, table des clés et résultat, lignes avant/après de `TvLink`, question d'audit : une clé ambiguë entre deux TV homonymes sans IP ⇒ choix proposé (`default`) et sa justification.
