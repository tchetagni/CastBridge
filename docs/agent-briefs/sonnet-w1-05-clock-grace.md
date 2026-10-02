# w1-05 — Horloge de la TV : uptime cumulé persisté, règle AHEAD, grâce sans faille

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (D1 décidée : oui)
> **Groupe : W1-A** (vague W1) · prérequis : aucun · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Clock*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : oui

**Vague 1 · Effort M (≈ 2 j) · Statut PRÊT** (la mise de `graceDays=0` par défaut attend D1 : ce cahier **prépare** le réglage, ne le bascule pas). Branche `claude/sonnet-w1-05`. Rapport : `docs/agent-reports/sonnet-w1-05.md`.

## Objectif
1. `TvClock` persiste un **uptime cumulé** (somme des `elapsedRealtime` observés) et les plafonds d'usage expirent au **premier** de (date retenue, date d'installation + uptime cumulé). Un redémarrage avec horloge reculée ne fige plus rien.
2. Sauvegarde de l'horloge toutes les **5 minutes** de fonctionnement (pas seulement chaque heure).
3. Règle **AHEAD** : un saut en avant de plus de 45 jours en une observation suspend (`ClockDoubt.AHEAD`) l'évaluation des plafonds de `TvGate` (écran « Vérifiez l'heure ») au lieu d'être cru (< 400 j) et de brûler l'essai.
4. Grâce de migration : refusée si `clock.txt` est absent alors que `firstInstallTime < LOCK_GRACE_START_MS` (signature d'une réinstallation avec horloge reculée) ; la grâce applique `TrialPolicy` (restrictions de l'essai) plutôt que « tout ouvert ».

## Pourquoi (preuves)
- `android/core/src/main/kotlin/castbridge/core/owner/Keys.kt:86-90` : « RESIDUAL HOLE: rebooting while rolling the wall clock back loses the elapsed time… » ; `lastSeen` n'est sauvé qu'au démarrage et chaque heure (`android/receiver/src/main/kotlin/castbridge/receiver/PolicyHub.kt:15`). Redémarrer la TV toutes les < 1 h avec horloge reculée fige l'essai.
- `Keys.kt:105` : saut avant < 400 j cru → une pile d'horloge fausse d'un an consomme le plafond (audit Opus A1-3, non traité).
- `android/core/src/main/kotlin/castbridge/core/owner/Activation.kt:183-192` : `TvGate.evaluate(..., clockDoubt, monotonicNowMs)` n'utilise `monotonicNowMs` que pour `BEHIND`.
- `android/receiver/src/main/kotlin/castbridge/receiver/ActivationCenter.kt:226-228` (`loadGrace`) + `C/owner/FeatureGate.kt:37-42,52-53` : grâce = `GateState.Grace` **sans** `TrialPolicy` ; `firstInstallTime` est l'heure murale à l'installation → désinstaller, reculer la date, réinstaller ⇒ grâce tout ouvert jusqu'au 2026-11-01 d'uptime.
- Audit : SE-3, SE-4.

## Fichiers possédés
`C/owner/Keys.kt`, `C/owner/FeatureGate.kt` (**`FleetMigration` et `GateState.Grace` seulement** ; ne pas toucher `LockedTexts` ni `ActivationReceiver`), `C/owner/Activation.kt` (**`TvGate.evaluate` seulement**), `R/ActivationCenter.kt`, `R/PolicyHub.kt`, `android/receiver/build.gradle.kts` (bloc grâce : commentaire + propriété), `android/core/src/test/kotlin/castbridge/core/owner/ClockRollbackTest.kt`, `android/core/src/test/kotlin/castbridge/core/owner/GraceMigrationTest.kt`, `docs/TRIAL-EDITION.md` (§ 14, paragraphe grâce). **Hors zone** : `RentalEngine.judge` (règles des locations, déjà bonnes), `TrialPolicy.kt`, `ActivationActivity.kt`.

## Étapes
1. `TvClock` : champ `uptimeMs` (cumul persisté) + `uptimeAtSeen` (monotone au dernier `observe`) ; `observe()` ajoute `mono() - uptimeAtSeen` à `uptimeMs` ; `fun uptimeNow(): Long = uptimeMs + elapsed()`. Format de `clock.txt` v2 : `lastSeen floor uptimeMs` (3 champs ; l'ancien format à 2 champs est relu, `uptimeMs=0`). Fournir `TvClock.encode()/decode(text)` en cœur pour que `ActivationCenter` n'ait plus de format en dur.
2. `TvClock.now()` : si `clock > base + AHEAD_MAX_MS` (45 j, constante partagée avec `RentalEngine` — la lire depuis `castbridge.core.lots.RentalEngine` si elle y est, sinon la définir dans `TvClock` et la référencer) **et** aucun plancher signé ne le justifie → `doubtAhead = true`, temps retenu = `base + elapsed()`.
3. `TvGate.evaluate` : nouveau paramètre `uptimeNowMs: Long? = null` et `installedAtMs` par activation (déjà `installedAt` ? sinon utiliser `issuedAt`) : une activation ne compte plus si `uptimeNowMs >= (usage.endsAt - usage.startsAt)` accumulé depuis son installation (second plafond, indépendant de l'horloge). Si `clockDoubt == AHEAD` : état `TvAccess(keyInstalled = true, suspended = true, label = "Vérifiez l'heure de la TV")` (ajouter le champ ; le badge/`ActivationActivity` l'afficheront plus tard, w2-04 — ici seulement le modèle + test).
4. `ActivationCenter` : utiliser `TvClock.encode/decode` ; `saveClock()` appelé par `PolicyHub` toutes les 5 min (modifier la minuterie de `PolicyHub.observeClock`), au `onDestroy` du service si possible ; passer `uptimeNowMs` à `TvGate.evaluate`.
5. `loadGrace` : `existingInstall = FleetMigration.of(first, LOCK_GRACE_START_MS).existingInstall && clockFileExistedAtStart` (lire l'existence de `clock.txt` **avant** `loadClock()` qui le crée). `FeatureGate.state` : `GateState.Grace` porte `trialRestricted = true` ; `ActivationCenter.trial()` renvoie `true` aussi en grâce (vérifier les appelants : `grep -rn 'ActivationCenter.trial()' android/receiver` — ils lisent une fonction, pas de changement de signature).
6. `build.gradle.kts` : commentaire « D1 : passer `castbridge.graceDays` à 0 quand le parc est activé » ; ne pas changer la valeur.
7. Tests : `ClockRollbackTest` — rollback **à travers un redémarrage** (nouvelle instance `TvClock` décodée depuis le texte, `mono` repart à 0, l'uptime cumulé continue) ; saut +60 j → `AHEAD` suspend, +400 j idem, +30 j cru ; `GraceMigrationTest` — réinstallation sans `clock.txt` ⇒ verrouillé ; grâce ⇒ restrictions d'essai actives.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.owner.*'   # vert (≥ 170 tests + nouveaux)
cd android && gradle --offline :core:test --tests 'castbridge.core.lots.*'    # vert (RentalEngine inchangé)
python3 tools/activation/verify_vectors.py                                     # inchangé (aucun vecteur modifié ; nécessite `pip install cryptography`)
cd android && gradle --offline :receiver:compileDebugKotlin                   # compile (si SDK)
```
Observable (campagne w3-14, étapes 18-20, 23) : reculer l'heure de 10 j puis redémarrer 3 fois : `GET /api/activation` ne rajeunit jamais ; avancer de 60 j : badge « Vérifiez l'heure ».

## Cas limites
- Première observation (TV neuve, `base == 0`) : horloge crue (inchangé).
- `elapsedRealtime` ne compte pas le temps éteint : l'uptime cumulé est une **borne basse** du temps réel ; c'est voulu (jamais plus court que la vérité pour le client).
- Les locations gardent `RentalEngine.judge` tel quel ; ne pas dédoubler leurs règles.

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret ; ne pas modifier les vecteurs ni `ActivationIssuer` ; ne pas changer `graceDays` ; textes en français.

## Rapport
`STATUT`, format `clock.txt` v2, sorties des commandes, question D1 relayée.
