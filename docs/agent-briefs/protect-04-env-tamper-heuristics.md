# protect-04 — Heuristiques d'environnement (émulateur / root / hook) — drapeau, pas blocage

**Modèle recommandé : sonnet** (heuristiques + maîtrise des faux positifs).
**Vague B.** Dépend de protect-01. Parallélisable (fichiers neufs disjoints).

## But
Détecter un environnement manifestement instrumenté (émulateur, root Magisk, Frida, Xposed, `TracerPid` non nul) et **lever un drapeau** transmis au battement de cœur (traçabilité). **Aucune réponse destructive.** **PD2 (décidé par le propriétaire)** : refuser l'ouverture de contenu **LOUÉ uniquement** si l'environnement est clairement hooké (`HOOKED`), avec message FR clair + contact ; tout le reste (achats, médias du client, essai) reste accessible. **Jamais** de réaction sur simple `debuggable` ni sur root seul (faux positifs : nombre de firmwares TV, toutes marques, tournent en `userdebug` ou avec root d'usine). Indépendant de l'OS et de l'ABI.

## Fichiers possédés
- `android/core/src/main/kotlin/castbridge/core/owner/EnvTrust.kt` (**neuf**, partie pure : agrège des signaux booléens → `EnvTrust` niveau `TRUSTED | SUSPECT | HOOKED` + liste de raisons)
- `android/core/src/test/kotlin/castbridge/core/EnvTrustTest.kt` (**neuf**)
- `android/receiver/src/main/kotlin/castbridge/receiver/TamperSignals.kt` (**neuf**, collecte Android : `/proc/self/status` TracerPid, présence de binaires su/magisk, ports/libs Frida, propriétés émulateur, `ApplicationInfo.FLAG_DEBUGGABLE`)

## Point chaud partagé (édition minimale, signalée)
- `android/receiver/src/main/kotlin/castbridge/receiver/RentalHub.kt` : **un** point de consultation de `TamperSignals.current(ctx)` à l'ouverture d'un lot loué ; si `HOOKED` ⇒ refus avec message FR + contact (PD2). Rien d'autre.

## Étapes
1. `EnvTrust.kt` (pur) : `data class EnvSignals(val emulator, val rooted, val fridaLike, val xposed, val tracerPid, val debuggable: Boolean)` ; `fun assess(s): EnvTrust`. Pondération : `HOOKED` si `fridaLike||xposed||tracerPid` (instrumentation active) ; `SUSPECT` si `rooted||emulator` seuls ; `debuggable` seul **n'abaisse pas** le niveau (ignoré sauf cumulé). Fournir `reasons: List<String>` en clair (FR) pour le rapport serveur.
2. `EnvTrustTest.kt` : vecteurs (TV propre → TRUSTED ; émulateur → SUSPECT ; Frida → HOOKED ; debuggable seul → TRUSTED).
3. `TamperSignals.kt` : collecte réelle, tout en try/catch (jamais planter). Exposer `fun current(ctx): EnvTrust`. **Ne rien bloquer ici** : fournit la valeur à protect-05 (heartbeat) et, en option PD2, à RentalHub pour refuser l'ouverture d'un lot loué si `HOOKED` (message FR, pas de suppression).

## Commandes d'acceptation
- `./gradlew :core:test --tests "*EnvTrustTest*"` vert.
- Revue : `debuggable` seul ⇒ `TRUSTED` ; aucune action destructive dans TamperSignals.
- `grep -n "deleteRecursively\|System.exit\|Runtime.*exec.*rm" TamperSignals.kt` → aucun résultat.

## Cas limites / à préserver
- Vraies TV (toutes marques/OS) souvent en `userdebug`/root d'usine → **ne pas** les traiter comme hostiles (SUSPECT au plus, jamais de blocage dur).
- Le refus PD2 ne concerne que les lots **loués** : implémenter le point de refus dans `RentalHub` (ou exposer `EnvTrust` pour que RentalHub le consulte) ; ne jamais toucher aux achats ni aux médias personnels.
- Émulateur + `DEV_BUILD` : collecter mais ne déclencher aucun refus.
- Pas de faux positif bloquant pour un client.

## Ne PAS faire
- Pas de lib native anti-debug (cf. §5 du document de conception : gaspillage sur 32 bits). Pas de blocage sur debuggable. Pas de suppression de données. Ne pas committer.

## Format de rapport
Fichiers neufs, sortie des tests, table des vecteurs, confirmation « aucune action destructive ».
