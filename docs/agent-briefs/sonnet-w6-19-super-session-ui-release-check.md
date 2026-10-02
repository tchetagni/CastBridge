# w6-19 — Console du propriétaire : session super administrateur (ouverture, durée, bandeau, fermeture, scellement), `PhoneGate` en `Super`, contrôle de publication « aucun haché dans un APK distribuable »

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (après w6-04)
> **Groupe : W6d-1** (vague W6d) · prérequis : w6-04 · porte : `python3 -m unittest discover -s tools/tests -p 'test_check_no_superadmin.py' && cd android && tools/agents/gradle-lock.sh gradle --offline :ownerlib:compileDebugKotlin`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : oui

**Vague 6d · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après w6-04 fusionné ; w6-16 en parallèle : contrat `PhoneGateRuntime.superActive` lu via `SuperSessionStore.active()`).** Conception : `DESIGN-W6-PARENTAL-PHONE-GATE.md` § 4. Branche `claude/sonnet-w6-19`. Rapport : `docs/agent-reports/sonnet-w6-19.md`.

## Objectif
(1) `OL/SuperSessionStore.kt` : `SuperSession` (w6-04) scellée dans `files/super-session.txt` par `SecretWrapper` sous une clé du `KeystoreWrapper` du téléphone (`setUserAuthenticationRequired` si disponible ; repli : clé fichier + le dire) ; `active()`, `open(duration)`, `close()`, `remaining()` ; (2) `SuperAdminActivity` : après `Result.Open` **et** coffre ouvert : écran « Ouvrir une session super administrateur ? » (1 h / 4 h / 12 h (défaut) / 24 h) + rappel « Le téléphone aura toutes ses fonctions sans preuve de TV ; aucune TV ni le serveur ne sont débloqués par cette session » ; `ConsoleActivity` : bouton « Fermer la session » et affichage du reste ; 5 échecs de mot de passe ⇒ `close()` ; (3) **bandeau permanent** dans CastBridge (`SuperBanner` composable fourni ici, placé par w6-16 dans `MainActivity` — fournir la fonction, demander le placement dans le rapport) : « Session super administrateur jusqu'à HH:MM · Fermer » ; (4) ligne « Mode : super administrateur » dans Réglages (texte fourni) ; (5) `tools/release/check_no_superadmin.sh <apk>` : échoue si l'APK contient `\$2[abxy]\$[0-9]{2}\$` ou une chaîne `SUPERADMIN_BCRYPT` non vide (`unzip -p … classes*.dex | strings`), test Python avec un faux dex ; `docs/RELEASES.md` : étape obligatoire avant publication ; (6) OWNER-CONSOLE : § 4.5 procédure de perte (texte fourni à w6-20 dans le rapport).

## Pourquoi (preuves)
- `OL/SuperAdmin.kt:36-71` (`SuperAdminActivity.Entry` : point d'ouverture), `OL/ConsoleActivity.kt:36-68` (`Screen`, verrou 2 min, « Verrouiller ») ; `OL/OwnerStore.kt` (`guard`, journal) ; `C/owner/SuperSession.kt` (w6-04) ; `C/owner/OwnerVault.kt:80` (`AuditChain`) ; `android/ownerlib/build.gradle.kts:9-23` ; `docs/coordination/AUDIT-PROJET-2026-10-02.md` A5-2 ; `R/KeystoreWrapper.kt` (w4-03 : modèle à **copier** côté téléphone dans `OL/PhoneKeystoreWrapper.kt`, pas à partager : modules différents).

## Fichiers possédés
`OL/{SuperAdmin,ConsoleActivity,OwnerStore}.kt`, nouveaux `OL/{SuperSessionStore,PhoneKeystoreWrapper,SuperBanner}.kt`, `tools/release/check_no_superadmin.sh`, `tools/tests/test_check_no_superadmin.py`, `docs/RELEASES.md` (§ contrôle). **Hors zone** : `S/**` (w6-16/17/18), `C/**`, `android/ownerlib/build.gradle.kts`, `.github/**` (w6-23).

## Étapes
1. `PhoneKeystoreWrapper` (AES-GCM dans l'AndroidKeyStore, `setUserAuthenticationRequired(true)` + validité 24 h si API ≥ 23 ; repli documenté).
2. `SuperSessionStore` ; `active()` lit, déchiffre, `SuperSession.active(now)` ; toute erreur ⇒ `false`.
3. Écrans ; `AuditChain` (journal du coffre) : `super.open`/`super.close`.
4. `SuperBanner()` composable ; `SuperTexts.modeLine()`.
5. Script + test + RELEASES.
6. Captures → `docs/img/owner/w6-*.png`.

## Critères d'acceptation
```sh
cd android && gradle --offline :ownerlib:compileDebugKotlin   # compile
python3 -m unittest discover -s tools/tests -p 'test_check_no_superadmin.py'   # vert
cd android && gradle --offline :sender:assembleDebug && tools/release/check_no_superadmin.sh android/sender/build/outputs/apk/debug/*.apk   # OK (aucun haché)
cd android && gradle --offline :sender:assembleDebug -Pcastbridge.superAdmin=true && tools/release/check_no_superadmin.sh android/sender/build/outputs/apk/debug/*.apk ; echo "exit=$?"   # exit≠0 (haché présent : build propriétaire)
```
Observable : ouverture ⇒ bandeau ; verrou de la console à 2 min ⇒ bandeau toujours là ; « Fermer » ⇒ disparaît ; horloge reculée ⇒ pas de prolongation ; retrait du verrouillage d'écran du téléphone ⇒ session fermée (si Keystore avec authentification).

## Cas limites
Build sans haché : `SuperSessionStore.active()` est toujours faux et le fichier n'est jamais créé ; `OwnerStore.Locked` pendant l'ouverture ⇒ pas de session ; désinstallation ⇒ fichier perdu (attendu).

## À ne pas faire
Ne pas lier la session à `SUPER_UNLIMITED` ; ne pas donner accès aux rapports parentaux (D-W6-3) ; ne pas changer le verrou de 2 min ; aucun haché dans le dépôt.

## Rapport
`STATUT`, captures, nom du composable pour w6-16, texte de la procédure de perte pour w6-20.

## Points reportés par l'audit Opus de w6-04 (2026-10-02) — à traiter dans ce cahier
1. Contrat de scellement : la session peut se fermer toute seule dans `active`, `activeState`, `remainingMs` et `decode`. Après TOUT appel, si `encode()` a changé, resceller ; si elle renvoie `""`, effacer le fichier. Sinon chaque démarrage relit le blob et ajoute un nouveau « super.close » à l'audit.
2. Écrire `clock.txt` (TvClock) au démarrage et à chaque scellement du blob de session : sinon une boucle de redémarrages avec l'heure murale reculée empêche la session de se terminer (uptime et lastSeen n'avancent pas avant la sauvegarde de 5 min).
3. (mineurs, Haiku possible) rendre `SuperSession.state` privé/internal ; analyse canonique `\d+` des champs du blob ; utiliser la raison « horloge » aussi dans `active` quand l'uptime recule.
4. Un ancien blob scellé (failures=0) peut être restauré : nécessite l'accès root au stockage privé, hors cœur ; envisager un compteur monotone côté Keystore.
