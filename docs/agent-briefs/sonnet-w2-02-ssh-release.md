# w2-02 — SSH de CastBridge-TV : pas de shell ni d'exec en release, SFTP limité au média

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT réduit (D5 = NON : drapeau de build + SFTP limité seulement)
> **Groupe : W2-A** (vague W2) · prérequis : w1-09 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :sshd:test`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : oui

**Vague 2 · Effort M (≈ 1-2 j) · Statut PRÊT** (décision D5 recommandée ; si le propriétaire refuse, le cahier se réduit au drapeau de build). Après w1-09 (tests SSH fiabilisés). Branche `claude/sonnet-w2-02`. Rapport : `docs/agent-reports/sonnet-w2-02.md`.

## Objectif
En build **release** (et toute build verrouillée), le serveur SSH de la TV n'offre que SFTP/SCP **limité au dossier média** (bibliothèque sur USB ou interne) : ni shell interactif, ni exécution de commande (sauf la commande interne `cbdev` si `builtin != null`, qui n'ouvre pas de processus). Les builds debug/test gardent le shell. Un drapeau Gradle `-Pcastbridge.sshShell=true` permet de le forcer pour une build de diagnostic **non distribuée**.

## Pourquoi (preuves)
- `android/sshd/src/main/kotlin/castbridge/sshd/TvSshServer.kt:48` `shell = "/system/bin/sh"`, `:122` `PtyShell(listOf(shell, "-i"), sftpRoot)`, `:125` `ProcessShellFactory(cmd, shell, "-c", cmd)` : livrés sans distinction release/debug (`grep -n 'release\|DEBUG' android/sshd/build.gradle.kts android/receiver/build.gradle.kts` : aucun lien).
- `android/receiver/src/main/kotlin/castbridge/receiver/SshControl.kt:43` : seul l'**essai** ferme SSH ; en production, un client avec son PIN (affiché à l'écran « Connexion & réglages ») active SSH, obtient un shell sous l'uid de l'app et lit `files/rental/keys/*.key`, `clock.txt`, `rentals.json` (`docs/RENTAL-LOTS.md` § 11 reconnaît la limite pour une « TV de test », mais c'est une fonction livrée).
- Audit : SE-5 ; sans cela, w1-05, w1-02, w2-01 restent contournables par tout client payant.

## Fichiers possédés
`android/sshd/**` (main + test), `R/SshControl.kt`, `android/receiver/build.gradle.kts` (**ajout** `buildConfigField("boolean","SSH_SHELL", …)` seulement), `docs/ADMIN.md` (§ SSH). **Hors zone** : `ReceiverServer`, `TvService`, `TrialPolicy`, `ActivationCenter`.

## Étapes
1. `TvSshServer` : paramètre `shellEnabled: Boolean` (défaut `false`) ; si faux : `shellFactory` = fabrique qui écrit « Shell désactivé sur cette TV (CastBridge-TV distribuée). SFTP seulement. » et ferme (code 1) ; `commandFactory` = SCP + `cbdev` interne seulement, toute autre commande → même message ; `sftpRoot` **obligatoire** et vérifié (chemin canonique sous le dossier média ; interdire `..` et liens symboliques sortants : utiliser le `VirtualFileSystemFactory` de MINA avec racine fixée).
2. `SshControl` : passer `BuildConfig.SSH_SHELL` (vrai en `debug`, faux en `release` sauf `-Pcastbridge.sshShell=true`) ; `sftpRoot` = racine de la bibliothèque (demander à `TvService`/`VolumeRegistry` le dossier média courant via l'API déjà utilisée pour `sftpRoot` aujourd'hui).
3. `build.gradle.kts` : `buildConfigField("boolean", "SSH_SHELL", (findProperty("castbridge.sshShell")=="true").toString())` dans `defaultConfig`, et `true` forcé dans `buildTypes.debug` (via `buildConfigField` dans le bloc `debug`).
4. Tests `TvSshServerTest` : (a) `shellEnabled=false` : ouvrir un canal shell → message et fermeture ; `exec "ls"` → refus ; `cbdev …` → fonctionne si `builtin` fourni ; (b) SFTP : lire un fichier sous la racine → OK ; `../` → refus ; (c) `shellEnabled=true` : comportement d'aujourd'hui (tests existants).
5. `docs/ADMIN.md` § SSH : « sur une TV distribuée, SSH = SFTP du dossier média ; le shell n'existe que dans les builds de test (`-Pcastbridge.sshShell=true`) ».

## Critères d'acceptation
```sh
cd android && gradle --offline :sshd:test                                       # vert (après w1-09 : stable)
grep -n 'SSH_SHELL' android/receiver/build.gradle.kts android/receiver/src/main/kotlin/castbridge/receiver/SshControl.kt   # présent
cd android && gradle --offline :receiver:compileReleaseKotlin                   # compile (si SDK)
```
Observable (campagne) : sur une build release, `ssh -p 2222 tv@TV` affiche « Shell désactivé… » et se ferme ; `sftp -P 2222 tv@TV` liste le dossier média et refuse `cd ..` au-delà de la racine ; `ssh … 'cat files/rental/keys/x.key'` → refus.

## Cas limites
- Le tunnel Bluetooth SSH (`SshTunnel`) et le futur tunnel inverse (w2-15) utilisent le même serveur : ils héritent de la restriction (voulu).
- `cbdev` interne reste disponible pour CastBridge Dev (`builtin`), mais CastBridge Dev n'est pas distribuée.

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret ; ne pas retirer SSH en essai (déjà fermé) ; textes en français.

## Rapport
`STATUT`, comportement release vs debug, sorties des commandes, risque résiduel (SFTP média).
