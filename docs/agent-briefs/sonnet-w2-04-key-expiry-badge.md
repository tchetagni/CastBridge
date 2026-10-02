# w2-04 — Fin de clé annoncée, badge lisible, tuile Langues honnête

**Vague 2 · Effort M (≈ 1,5 j) · Statut PRÊT** (le **mode dégradé** à l'échéance d'une production attend D6 : ce cahier prépare l'écran et les rappels, pas le changement de `TvGate`). Branche `claude/sonnet-w2-04`. Rapport : `docs/agent-reports/sonnet-w2-04.md`.

## Objectif
1. Avant l'échéance d'une clé : rappels sur l'accueil à J-7, J-3, J-1 (« Clé : il reste 3 jours — pensez à la renouveler ») et bandeau jaune.
2. À l'échéance : l'écran verrouillé dit « Votre clé s'est terminée le JJ/MM/AAAA. Pour continuer, demandez une nouvelle clé avec ce code : … » (au lieu de l'avis générique).
3. Le badge de clé est lisible à 3 m (≥ 20 sp) et accessible (TalkBack), contraste ≥ 4,5:1.
4. En essai, la tuile Langues est grise avec « Version complète requise » ; « Passer en production » devient « Version complète » avec l'état « Essai : N jours restants ».

## Pourquoi (preuves)
- `C/owner/Activation.kt:189-193` : à l'échéance `TvGate.evaluate` renvoie `keyInstalled=false` → `GateState.Locked` → écran d'activation générique ; `C/owner/KeyBadge.kt:27` « ACTIVATION TERMINÉE · Entrez un nouveau code valide » ; aucun préavis ailleurs que le badge.
- `R/KeyBadgeOverlay.kt:30` : `textSize = 14f`, `importantForAccessibility = NO` → illisible à 3 m sur 720p, invisible à TalkBack.
- `R/PlayerActivity.kt:507` tuile « Passer en production » avec icône clé USB ; `:517` tuile Langues affichée en essai alors que `C/owner/TrialPolicy.kt:11` la ferme.
- `receiver/src/main/res/values/cb_colors.xml` : `text_low` ≈ 4,0:1 sur `surface`.
- Audit : UX-4, UX-10, UX-13, MO-3.

## Fichiers possédés
`C/owner/KeyBadge.kt`, `R/KeyBadgeOverlay.kt`, `R/PlayerActivity.kt`, `R/HomeScreen.kt`, `android/core/src/test/kotlin/castbridge/core/owner/KeyBadgeTest.kt`, `android/receiver/src/main/res/values/cb_colors.xml`. **Hors zone** : `ActivationActivity.kt` (w2-03 : lui fournir l'API ci-dessous), `Activation.kt`/`ActivationCenter.kt` (w2-01), `TrialPolicy`, `LearnActivity`.

## Étapes
1. `KeyBadge` (cœur, pur) : `fun reminder(activations, nowMs): Reminder?` (`J7`, `J3`, `J1`, avec date de fin formatée) ; `fun endedSummary(activations, nowMs): String?` (« Votre clé s'est terminée le … » quand il existe une activation installée mais aucune qui compte) ; `fun trialStatusLine(activations, nowMs): String?` (« Essai : N jours restants »). Tests `KeyBadgeTest` pour les trois (bornes J-7/J-3/J-1, aucune activation, production illimitée → `null`).
2. `KeyBadgeOverlay` : 20 sp minimum, `importantForAccessibility = YES`, `contentDescription` = texte ; couleur de texte `cb_text_high` sur fond opaque ; ne pas gêner le focus (`isFocusable=false` conservé).
3. `PlayerActivity`/`HomeScreen` : au `onResume` et toutes les 30 s (minuterie existante du badge), si `reminder != null` et pas encore montré aujourd'hui (préférence `castbridge_tv` clé `reminder_shown_day`) → `notice(...)` + bandeau jaune sur le héros de l'accueil ; tuile « Version complète » (icône `ic_cb_cle` si elle existe dans `res/drawable`, sinon l'icône clé la plus proche ; ne pas créer d'icône) avec sous-titre `trialStatusLine` ; tuile Langues : si `ActivationCenter.trial()` (lecture seule) → état visuel désactivé + sous-titre « Version complète requise », l'ouverture affiche la même phrase.
4. `cb_colors.xml` : `cb_text_low` → `#B7C0D4` (≈ 9:1) ou nouvelle couleur `cb_text_small` utilisée pour tout texte < 24 sp dans les fichiers possédés.
5. Exposer pour w2-03 : `KeyBadge.endedSummary` (cœur) — w2-03 l'affichera dans `ActivationActivity` ; le dire dans le rapport.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.owner.KeyBadgeTest'   # vert, ≥ 6 nouveaux tests
grep -n 'textSize = 14f\|IMPORTANT_FOR_ACCESSIBILITY_NO' android/receiver/src/main/kotlin/castbridge/receiver/KeyBadgeOverlay.kt   # 0 hit
grep -n 'Passer en production' android/receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt   # 0 hit
cd android && gradle --offline :receiver:compileDebugKotlin   # compile (si SDK)
```
Observable (campagne, étapes 10, 15, 23) : badge lisible depuis le canapé ; tuile Langues grise en essai ; avec une clé d'essai de test à J+1, l'accueil affiche le rappel ; après l'échéance, l'écran verrouillé affiche la date de fin (via w2-03).

## Cas limites
- `SUPER_UNLIMITED` et production illimitée : aucun rappel.
- Horloge en doute (`AHEAD`, w1-05) : afficher « Vérifiez l'heure de la TV » dans le badge si `TvAccess.suspended` existe (sinon ignorer et le noter).

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret ; ne pas modifier `TvGate` (D6 en attente) ; textes en français ; « CastBridge-TV ».

## Rapport
`STATUT`, API cœur ajoutée (signatures), captures émulateur si possible, question D6 relayée.
