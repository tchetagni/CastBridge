# protect-09 — Ordre serveur signé « classe d'appareil » (block / allow), autoritaire, persistant, levable

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (après protect-02, w2-01)
> **Groupe : P-C2** (vague PROTECT) · prérequis : protect-02, w2-01 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*DeviceClassOrder*' --tests '*Policy*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : oui

**Modèle recommandé : sonnet** (extension d'un moteur de politiques signé, vérification de cible/scope/séquence, persistance, UX de blocage, tests).
**Vague C+ (après B).** Dépend de **protect-02** (classe d'appareil calculée et persistée) et de **w2-01** (persistance SeqState/RevocationState + canaux de livraison USB/Bluetooth/HTTP des listes signées). Décision propriétaire **PD4** : INCERTAIN passe par défaut, mais le serveur doit pouvoir **bloquer ou autoriser ensuite par un ordre signé autoritaire**.

## But
Permettre à l'éditeur de **bloquer** (ou ré-**autoriser**) l'usage de CastBridge-TV par un ordre signé, ciblant **un appareil**, **une licence** ou **tout le parc de classe INCERTAIN**, livré comme les révocations (battement de cœur, relais téléphone, fichier USB), **persisté** sur la TV, **levable** par un ordre ultérieur. Blocage = écran FR clair + contact d'assistance, **aucune donnée touchée**.

## Réutiliser, ne pas réinventer
- Enveloppe signée existante `cbx1` : type **`order`** (`core/owner/Order.kt`, `Orders.TYPE`), scope de clé **`KeyScope.POLICY`**, cibles `Envelope.Target.Any | Device(k, factors) | License(id) | Group(id)`, `seq` anti-rejeu, `notBefore/expiresAt`.
- Moteur côté TV : `core/policy/PolicyEngine.kt`, `PolicyActions.kt` (table d'actions + rejets `UNKNOWN_ACTION`, `BAD_PARAMS`, `WRONG_TARGET`, `STALE_SEQUENCE`, `SCOPE_EXCEEDED`…), `PolicyState.kt`, `PolicyGate.kt`, `PolicyStorage` (JSON `policy/state.json`) ; câblage receiver `PolicyHub.kt` ; transport `OrderCourier/OrderFrames/OrderTransport` (canaux déjà prévus).
- Les rejets et messages suivent le style existant (`Rejection`, textes FR).

## Fichiers possédés
- `android/core/src/main/kotlin/castbridge/core/policy/PolicyActions.kt` (nouvelle action)
- `android/core/src/main/kotlin/castbridge/core/policy/PolicyState.kt` (nouvel état persistant)
- `android/core/src/main/kotlin/castbridge/core/policy/PolicyGate.kt` (effet sur la porte)
- `android/core/src/test/kotlin/castbridge/core/policy/DeviceClassOrderTest.kt` (**neuf**)
- `android/receiver/src/main/kotlin/castbridge/receiver/DeviceClassBlockActivity.kt` (**neuf**, écran de blocage)
- `docs/ORDRES.md` (section « Classe d'appareil »)

## Points chauds partagés (édition minimale, signalée)
- `android/receiver/src/main/kotlin/castbridge/receiver/PolicyHub.kt` : exposer l'état « bloqué par ordre » et lancer `DeviceClassBlockActivity` quand il s'applique (quelques lignes).
- **Ne pas** éditer `PlayerActivity.kt` (protect-02) ni `ActivationCenter.kt` (protect-03) : passer par `PolicyGate.effective(...)`/`PolicyHub` déjà prévus pour agir sur le `GateState`.

## Spécification de l'ordre
- Action : `device-class` ; paramètres : `verdict=block|allow`, `scope=device|license|uncertain-fleet`, `reason=<texte FR court>` (affiché), `contact=<optionnel>` (sinon contact par défaut, placeholder D7), `until=<ms, optionnel>` (blocage à durée limitée).
- **Cible de l'enveloppe** cohérente avec `scope` : `device` ⇒ `Target.Device` (k-parmi-n, comme les activations) ; `license` ⇒ `Target.License(id)` (la TV compare à la licence de ses activations installées) ; `uncertain-fleet` ⇒ `Target.Any` **et** la TV n'applique l'ordre **que si sa classe persistée (protect-02) est `UNSURE`** (une TV classée `TV` l'ignore ; une `NOT_TV` est déjà refusée par protect-02).
- **Autorité** : clé avec scope `POLICY` seulement (sinon `KEY_NOT_ALLOWED`/`SCOPE_EXCEEDED`) ; `seq` strictement croissant par clé (sinon `STALE_SEQUENCE`) ; fenêtre `notBefore/expiresAt` respectée ; clé révoquée refusée.
- **Levée** : un ordre `allow` de `seq` supérieur, même cible, annule le `block` ; `until` échu lève automatiquement. L'état conserve le **dernier ordre appliqué par (scope, cible)**.
- **Persistance** : dans `policy/state.json` via `PolicyStorage` (écriture atomique/SafeFile, cf. w1-02) ; survit au redémarrage ; réappliqué au démarrage.
- **Effet** : `PolicyGate.effective` renvoie un état « bloqué par l'éditeur » ⇒ seule la surface d'activation/aide reste (réutiliser la logique `LOCKED_WHITELIST`) ; **aucune suppression**, médias et activations intacts ; dès la levée, tout revient.
- **Écran** (`DeviceClassBlockActivity`) : titre FR, `reason`, « Cet appareil a été bloqué par l'éditeur », contact d'assistance, code d'appareil affiché (pour que le support retrouve la TV), état « réappliqué au prochain ordre ». Pas d'action destructive, pas de bouton piège.
- **Livraison** : mêmes canaux que les révocations de w2-01 (fichier `order`/`policy` sur USB `Download/CastBridge`, trame Bluetooth du canal propriétaire via `PolicyHub.onOwnerFrame`, HTTP via relais téléphone/battement de cœur). Ce cahier **n'implémente pas** les canaux (w2-01/w3-09) : il consomme ce qu'ils livrent et, à défaut, est testable par fichier.

## Étapes
1. `PolicyActions` : ajouter l'action `device-class` avec validation des paramètres (`BAD_PARAMS` si verdict/scope absents ou cible incohérente avec `scope`).
2. `PolicyState` : `deviceClassOrders: Map<"scope|cible", Applied(verdict, reason, contact, until, seq, keyId, at)>` sérialisé en JSON rétro-compatible (absent ⇒ vide).
3. `PolicyEngine` : à la réception, vérifier (type, clé/scope POLICY, signature, cible, seq, fenêtre), appliquer la règle `uncertain-fleet` ⇒ seulement si classe persistée = `UNSURE` (lire la valeur fournie par protect-02 via `DeviceContext`, à étendre d'un champ `deviceClass`), stocker, journaliser (`journalLines`).
4. `PolicyGate` : si un `block` actif s'applique ⇒ état bloqué (message + contact) ; sinon inchangé.
5. Receiver : `PolicyHub` expose l'état ; `DeviceClassBlockActivity` l'affiche ; vérification au démarrage et à chaque application d'ordre.
6. Tests (`DeviceClassOrderTest`) : block device accepté → bloqué ; allow seq+1 → levé ; allow seq ancien → `STALE_SEQUENCE`, toujours bloqué ; clé sans POLICY → refusé ; `uncertain-fleet` sur TV classée `TV` → ignoré ; `uncertain-fleet` sur `UNSURE` → bloqué ; `until` échu → levé ; persistance : sérialiser/désérialiser l'état et retrouver le blocage ; mauvaise cible licence → `WRONG_TARGET`.
7. `docs/ORDRES.md` : documenter l'action, les paramètres, la règle de cible, la levée, l'écran, et un exemple d'émission avec l'outil propriétaire (sans secret).

## Commandes d'acceptation
- `./gradlew :core:test --tests "*DeviceClassOrderTest*"` vert ; la suite `core/policy` existante reste verte.
- `grep -n "device-class" android/core/src/main/kotlin/castbridge/core/policy/PolicyActions.kt docs/ORDRES.md` → présent.
- Revue : aucun chemin de suppression de données ; levée possible par ordre signé ; `uncertain-fleet` inopérant sur une TV classée `TV`.

## Cas limites / à préserver
- TV hors ligne : l'ordre arrive par USB/Bluetooth ; une TV jamais atteinte n'est pas bloquée (acceptable, documenté).
- Changement de matériel (k-parmi-n) : même tolérance que les activations.
- Émulateur/`DEV_BUILD` : le moteur fonctionne (tests) mais l'écran de blocage peut être ignoré en dev si un drapeau le demande ; par défaut il s'affiche (pour tester).
- Ordre `allow` reçu **avant** tout `block` : accepté et stocké (no-op), empêche un `block` de `seq` inférieur.
- Ne jamais bloquer sur simple INCERTAIN sans ordre : PD4 = laisser passer par défaut.

## Ne PAS faire
- Pas de nouvelle crypto ni de nouveau type d'enveloppe : réutiliser `order`/`POLICY`. Pas d'implémentation des canaux (w2-01/w3-09). Pas de suppression de données. Ne pas committer.

## Format de rapport
Diffs par fichier, sortie des tests, table des cas (accepté/refusé/levé), confirmation « aucune suppression, levable, scope POLICY requis », exemple d'ordre (sans secret).
