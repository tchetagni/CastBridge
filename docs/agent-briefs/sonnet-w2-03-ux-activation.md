# w2-03 — Parcours d'activation lisible (TV et téléphone)

**Vague 2 · Effort M (≈ 2 j) · Statut BLOQUÉ PARTIEL** — tout est faisable sauf deux chaînes qui attendent la décision D7 : (1) le texte définitif de l'avis d'usage, (2) le **contact du vendeur** (numéro WhatsApp). L'agent prépare les emplacements (`OWNER_CONTACT` vide = ligne masquée) et pose `QUESTION: texte de l'avis d'usage et numéro de contact ?`. Branche `claude/sonnet-w2-03`. Rapport : `docs/agent-reports/sonnet-w2-03.md`.

## Objectif
Un parent sans compétence technique, télécommande en main, comprend sur le premier écran **ce qu'il doit faire, à qui envoyer le code, et ce qu'il obtiendra** ; chaque refus de clé dit quoi faire ensuite ; sur le téléphone, l'écran « Activer la TV » suit l'ordre réel (demander → recevoir → coller → envoyer) ; une clé reçue par Bluetooth s'installe sans second « Valider ».

## Pourquoi (preuves)
- `C/owner/FeatureGate.kt:170-172` (`LockedTexts.NOTICE`) : « [Texte à valider par le propriétaire : il ne constitue pas un avis juridique.] » affiché par `R/ActivationActivity.kt:77-78` ; le test `LicenseAndGateTest.kt:251` l'exige.
- Aucun contact : `grep -rni 'whatsapp\|contact' android/receiver android/sender --include=*.kt` ne donne que « Dernier contact » (télémétrie). `FeatureGate.kt:173` (`WAYS`) dit « partager … depuis le téléphone » sans destinataire.
- `R/ActivationActivity.kt:45-47` : clé reçue par Bluetooth **déjà vérifiée** → « Appuyez sur « Valider la clé » » (étape inutile) ; `:130` « Clé refusée par la saisie : Numéro de séquence déjà vu ou dépassé » (raisons de `C/owner/Activation.kt:94` `enum Rejection` brutes) ; `:89` chemin `storage/XXXX/Download/CastBridge/device-request.txt` à 15 sp ; `:68,87` gris #7B849C (≈ 3,6:1).
- `S/ActivateTvActivity.kt:123,154` : étape 1 « coller la clé » alors que le client n'en a pas encore ; `:63,137` phrases longues ; `:167-168` le partage WhatsApp envoie `code=… k=… factor=…` sans phrase humaine ; `:147-152` demande le PIN de la TV au milieu de l'activation.
- `S/MainActivity.kt:107-108` : « Activer la TV » et « Locations » en permanence dans la barre.
- Audit : UX-1, UX-2, UX-3, UX-8, UX-9, UX-12, UX-13.

## Fichiers possédés
`C/owner/FeatureGate.kt` (**objet `LockedTexts` seulement**), nouveau `C/owner/RejectionTexts.kt`, `C/owner/ActivationScreenState.kt`, `R/ActivationActivity.kt`, `S/ActivateTvActivity.kt`, `S/MainActivity.kt` (barre d'actions seulement), `android/receiver/build.gradle.kts` et `android/sender/build.gradle.kts` (**ajout** `buildConfigField("String","OWNER_CONTACT", …)` lu depuis `-Pcastbridge.ownerContact`, vide par défaut), `android/core/src/test/kotlin/castbridge/core/owner/LicenseAndGateTest.kt` (**la seule assertion sur l'avis**, et seulement si D7 est fourni), nouveau `android/core/src/test/kotlin/castbridge/core/owner/RejectionTextsTest.kt`. **Hors zone** : `KeyBadge*` (w2-04), `ActivationCenter` (w2-01), `TrialPolicy`.

## Étapes
1. `RejectionTexts.of(r: Rejection): Pair<String, String>` (phrase + action) en cœur, en français, pour **chaque** valeur de l'énumération, p. ex. `MALFORMED` → « Clé incomplète ou mal recopiée. » / « Vérifiez chaque caractère, ou refaites « Coller » sur le téléphone. » ; `WRONG_DEVICE` → « Cette clé est pour une autre TV. » / « Renvoyez le code d'appareil affiché ci-dessus. » ; `WINDOW_CLOSED` → « Clé périmée (valable 48 h). » / « Demandez une nouvelle clé. » ; `UNKNOWN_KEY`, `BAD_SIGNATURE`, `STALE_SEQUENCE`, `REVOKED_*` → « Clé non reconnue par cette TV. » / « Contactez le vendeur avec le code d'appareil. » Test : toutes les valeurs ont un texte non vide sans mot anglais ni jargon (`seq`, `signature`, `scope`).
2. `LockedTexts` : `NOTICE` sans la phrase entre crochets **si D7 fourni** (sinon inchangé, mais ne plus l'afficher sur la TV : afficher `NOTICE_SHORT` = la première phrase) ; nouvelle `fun contactLine(contact: String): String?` (« Envoyez ce code par WhatsApp au … » ou `null` si vide) ; définitions d'une ligne : « Clé d'essai : 30 jours, streaming + Sudoku + 12 h de leçons. Clé complète : toutes les fonctions pour la durée achetée. » ; remplacer « production » par « version complète » dans tous les textes de `LockedTexts` et `ActivationScreenState` (le mot `PRODUCTION` reste dans le code).
3. `ActivationActivity` : accepter directement une clé Bluetooth valide (`accept` au lieu de `stage`, message « Clé reçue du téléphone : activée ») ; afficher la ligne de contact (si `BuildConfig.OWNER_CONTACT` non vide) sous le code d'appareil, en 22 sp ; messages de refus via `RejectionTexts` ; texte gris → `#B7C0D4` ; chemin `device-request.txt` remplacé par « Fichier « device-request.txt » copié sur la clé USB » seulement si un volume amovible a été écrit ; dialogue « visible 300 s » demandé **une fois** par ouverture, puis ligne « Visible pour le téléphone encore m:ss » ; indication Bluetooth : « Bluetooth prêt : sur le téléphone, ouvrez CastBridge > Activer la TV » / « Bluetooth éteint : allumez-le dans les réglages de la TV ».
4. `ActivateTvActivity` : ordre 1 « Demander la clé » (ouvre le partage avec un message humain : « Bonjour, je souhaite activer ma TV CastBridge. Code : XXXX-XXXX-XXXX-XXXX. Détails techniques ci-dessous. » + le bloc existant), 2 « Coller la clé reçue », 3 « Envoyer à la TV » (Bluetooth d'abord quand une TV est choisie ; « Envoyer par le Wi-Fi (code de connexion de la TV) » en secondaire) ; phrases courtes ; conseil « Sur la TV, appuyez sur « Rendre la TV visible » puis « Chercher à nouveau » » quand la recherche échoue.
5. `MainActivity` : déplacer « Activer la TV » et « Locations » dans le menu Réglages (ou un bouton « Ma TV ») ; garder un bandeau visible seulement si la TV reliée est `trial`/`locked` (lecture de l'état déjà disponible via `/api/activation` ; si l'état n'est pas connu, ne rien afficher).
6. Tests JVM : `RejectionTextsTest` ; `ActivationScreenState` (textes, plus de « production »).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.owner.*'    # vert (adapter l'assertion de l'avis seulement si D7 fourni)
grep -rn 'production' android/core/src/main/kotlin/castbridge/core/owner/ActivationScreenState.kt android/core/src/main/kotlin/castbridge/core/owner/FeatureGate.kt | grep -v 'PRODUCTION\|//'   # 0 hit dans les chaînes utilisateur
grep -n 'Texte à valider' android/receiver/src/main/kotlin/castbridge/receiver/ActivationActivity.kt android/core/src/main/kotlin/castbridge/core/owner/FeatureGate.kt   # 0 hit affiché sur la TV
cd android && gradle --offline :receiver:compileDebugKotlin :sender:compileDebugKotlin   # compile (si SDK)
```
Observable (campagne, étapes 1-7) : premier écran = titre, code en grand, ligne de contact, une phrase par clé ; clé Bluetooth → activée sans second bouton ; clé d'une autre TV → « Cette clé est pour une autre TV… ».

## Cas limites
- `OWNER_CONTACT` vide (build de test) : aucune ligne vide ni « null ».
- Ne pas casser la saisie de la clé compacte (165 caractères) : garder le champ, le mettre en dernier avec l'indication « Clé courte (groupes de 4) ou clé complète ».

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret ; ne pas inventer de texte juridique ni de numéro ; textes en français ; « CastBridge » / « CastBridge-TV ».

## Rapport
`STATUT: BLOQUÉ` tant que D7 manque, avec `QUESTION:` ; liste des écrans modifiés ; captures d'émulateur si possible.
