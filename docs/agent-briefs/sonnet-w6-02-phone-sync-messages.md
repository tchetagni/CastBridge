# w6-02 — `PhoneSync` (machine d'états de synchronisation par TV) et `PhoneGateTexts` (catalogue unique des messages de déblocage)

**Vague 6a · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (en parallèle de w6-01 : codez contre les constantes `PhoneMessages.*` de § 3.8 ; si w6-01 n'est pas encore fusionné, définissez-les localement dans un `private object` et dites-le).** Conception : `DESIGN-W6-PARENTAL-PHONE-GATE.md` § 3.5, § 3.8, § 3.9. Branche `claude/sonnet-w6-02`. Rapport : `docs/agent-reports/sonnet-w6-02.md`.

## Objectif
(1) `C/owner/PhoneSync.kt` : état de synchronisation **par TV** (`NEVER_PAIRED, PAIRED_NEVER_SYNCED, SYNCING, SYNCED_FRESH(ageMs), SYNCED_STALE(ageMs), EXPIRED(ageMs), SYNC_FAILED(reason, lastKnownTv, ageMs)`), transitions sur une horloge **monotone** injectée, persistance texte ; (2) `C/owner/PhoneGateTexts.kt` : **l'unique** fonction `refusal(feature, tv: TvSummary?, sync: PhoneSync.State, nowMs): Refusal?` qui produit titre, corps, actions et « preuve il y a N j » pour chaque cellule ✖/◐ de la matrice, plus l'avertissement `M-PROOF-STALE` et les textes du mur (§ 3.5) ; (3) tests JVM exhaustifs.

## Pourquoi (preuves)
- Le téléphone a déjà une machine d'états de **liaison** (`C/trust/LinkMachine.kt`, `LinkView`, `LinkText.kt`) : `PhoneSync` est **au-dessus** (la liaison est bonne, la preuve est à part) ; ne pas la dupliquer : `SYNC_FAILED(BT_OFF|TV_UNREACHABLE|WRONG_NETWORK)` reprend les motifs de `BtUnavailable.Reason` (`C/trust/*`) et `LinkState`.
- `C/owner/Keys.kt:81-153` (`TvClock` : haut-fond + monotone + uptime) : réutiliser **tel quel** pour l'âge et l'expiration (ne pas réécrire une horloge).
- `C/owner/Activation.kt:94-97` (`Rejection`) : raisons françaises de `PROOF_REJECTED` ; `C/owner/FeatureGate.kt:176-189` (`LockedTexts.shareMessage`) pour l'action `GET_PRODUCTION_KEY`.

## Fichiers possédés
Nouveaux `C/owner/PhoneSync.kt`, `C/owner/PhoneGateTexts.kt`, `CT/owner/PhoneSyncTest.kt`, `CT/owner/PhoneGateTextsTest.kt`. **Hors zone** : `C/owner/PhoneGate.kt` (w6-01), `C/trust/**`, `S/**`.

## Étapes
1. `PhoneSync` : `data class Model(states: Map<tvCode, State>, clock: TvClock)` ; événements `paired(tv)`, `unpaired(tv)`, `syncStarted(tv)`, `proofOk(tv, verifiedAt)`, `proofFailed(tv, reason)`, `tick(nowMs)` ; constantes `STALE_AFTER_MS = 7 j`, `VALIDITY_MS = 14 j` (`ProofPolicy.VALIDITY_DAYS`, 1-30) ; `ageMs` = temps monotone depuis `verifiedAt` (un recul d'horloge **n'augmente ni ne réduit** l'âge ; un saut en avant > 45 j ⇒ `SYNC_FAILED(CLOCK_DOUBT)` jusqu'à confirmation) ; `encode()/decode()` texte (une ligne par TV) sans secret.
2. `Reason` : `BT_OFF, TV_UNREACHABLE, WRONG_NETWORK, CLOCK_DOUBT, TV_OLD_VERSION, PROOF_REJECTED(rejection: Rejection), TV_TRIAL, TV_GRACE(untilMs), TV_LOCKED, TV_DEGRADED(endedAt), TV_SUSPENDED, IDENTITY_CHANGED(fingerprint)` ; `isProofFailure` (TV joignable, pas en production) vs `isLinkFailure`.
3. `TvSummary(code, name, edition: TvEditionState, endedAt?, graceUntil?, kidProfileName?)`.
4. `PhoneGateTexts.refusal(...)` : table exacte du § 3.8 (identifiants, titres, corps, actions) ; la cellule vient de `PhoneGate.cell` (w6-01) ou, à défaut, d'une table locale identique ; `Refusal(id, title, body, actions, proofAge: String?)` ; `Action` enum (§ 3.8, avec `SEND_TO_OTHER_TV(name)` pour le cas essai + production § 3.9) ; `wall(state)` pour le mur (§ 3.5) ; `ageText(ms)` (« il y a 2 h », « il y a 3 j ») ; `staleWarning(sync)` ; tout texte **français**, injection du nom de la TV, jamais d'adresse/jeton/PIN.
5. Pass-through : `refusalFromTv(tvMessage: String, tv)` → `M-TV-REFUSED` ; `parentalBlocked(name, reason)` → `M-PARENTAL-BLOCKED`.
6. Tests : chaque identifiant du catalogue produit par au moins un cas ; chaque message nomme la TV quand `tv != null` ; liste noire (`Regex("[0-9a-f]{2}(:[0-9a-f]{2}){5}")`, « pin », « jeton », `cbx1.`) absente de tous les textes ; transitions : fraîche → périmée à 7 j → expirée à 14 j, recul d'horloge (âge inchangé), saut en avant (CLOCK_DOUBT), `SYNC_FAILED(TV_UNREACHABLE)` **garde** `lastKnownTv = PRODUCTION` (colonne F, pas G) ; `encode/decode` aller-retour ; `refusal` déterministe (même entrée ⇒ même sortie, sans horloge murale).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.owner.PhoneSyncTest' --tests 'castbridge.core.owner.PhoneGateTextsTest'   # vert, ≥ 30 cas
grep -c '"M-' android/core/src/main/kotlin/castbridge/core/owner/PhoneGateTexts.kt   # ≥ 20 identifiants
grep -rn 'import android' android/core/src/main/kotlin/castbridge/core/owner/PhoneSync.kt android/core/src/main/kotlin/castbridge/core/owner/PhoneGateTexts.kt   # 0 hit
```

## Cas limites
TV inconnue (`tv == null`) : messages génériques (« votre TV ») ; plusieurs TV : la fonction est appelée **par TV cible** (le choix de la cible est w6-16) ; `M-PROOF-STALE` n'est **jamais** un refus (`refusal` renvoie `null`, `staleWarning` renvoie le texte) ; `SYNCING` ⇒ `M-SYNCING` sans action ; `TV_GRACE` ⇒ texte avec la date.

## À ne pas faire
Aucune chaîne de refus ailleurs que dans `PhoneGateTexts` ; pas de dépendance à `S/` ; ne pas toucher `LinkText.kt` ; ne pas inventer de nouvel identifiant sans l'ajouter au tableau du rapport.

## Rapport
`STATUT`, signatures publiques (pour w6-16, w6-17, w6-18), tableau identifiant → texte final, cas non couverts.
