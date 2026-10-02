# w3-10 — Clé de signature de release : procédure, migration unique du parc, tags

**Vague 3 · Effort S (≈ 3 h) · Statut BLOQUÉ** — **D12** : le propriétaire décide de créer le keystore de release maintenant. L'agent n'exécute **aucune** commande de génération de clé ni ne touche `~/.android` ; il écrit la procédure et les scripts simulés. Branche `claude/sonnet-w3-10`. Rapport : `docs/agent-reports/sonnet-w3-10.md`.

## Objectif
Un document et deux scripts (simulation par défaut) qui permettent au propriétaire de : générer le keystore de release (sur son Mac, hors dépôt), le sauvegarder chiffré hors site, signer les APK, publier avec `SHA256SUMS`, poser les tags, et migrer **une seule fois** le parc signé avec la clé de debug (réinstallation manuelle, données internes perdues, clé USB conservée).

## Pourquoi (preuves)
- `docs/HANDOFF.md:150` : « les APK actuels sont signés avec la clé de debug du Mac (`~/.android/debug.keystore`) » ; perte du Mac ⇒ plus aucune mise à jour sans réinstallation ; aucune `signingConfigs` dans Gradle (`grep -rn signingConfigs android/*/build.gradle.kts` vide : la signature est externe).
- `git tag` : vide ; sha256 à la main dans HANDOFF ; `release.yml` neutralisé par w1-07.
- `docs/RELEASES.md` (w1-08) § 6 marque la migration « D12 en attente ».
- Audit : OP-3, R1-R3.

## Fichiers possédés
`docs/RELEASES.md` (§ 6 « Signature » et § 8 « Publication » : compléter), nouveau `tools/release/migrate-signing.md`, nouveaux `tools/release/sign-apk.sh` et `tools/release/tag-release.sh` (simulation sans `--apply`), `docs/HANDOFF.md` (§ 6 « Où sont les secrets » : une ligne sur l'emplacement **futur** du keystore de release, par chemin). **Hors zone** : Gradle, workflows, tout secret.

## Étapes
1. `migrate-signing.md` : (a) génération `keytool -genkeypair -v -keystore <chemin hors dépôt> -alias castbridge-release -keyalg RSA -keysize 4096 -validity 10950` (commande à exécuter par le propriétaire ; mot de passe choisi par lui, jamais écrit) ; (b) sauvegarde : copie chiffrée (`age`/`gpg`) sur deux supports hors site + empreinte SHA-256 du certificat notée dans `docs/RELEASES.md` (empreinte publique, pas un secret) ; (c) migration du parc : liste des TV (code d'appareil), pour chacune : exporter ce qui est en mémoire interne si nécessaire, désinstaller, installer l'APK release via l'explorateur de la clé USB, réinstaller l'activation (fichier `activation` conservé sur la clé USB ou réémission gratuite même matériel, `docs/TRIAL-EDITION.md` § 12), vérifier le badge ; (d) ordre : propriétaire d'abord, bêta-testeurs ensuite, clients avant la 10ᵉ vente.
2. `sign-apk.sh IN.apk OUT.apk` : `zipalign` + `apksigner sign --ks "$CB_KEYSTORE" --ks-key-alias castbridge-release` + `apksigner verify --print-certs` ; lit `CB_KEYSTORE` dans l'environnement ; refuse de tourner sans `--apply` et sans la variable ; n'imprime jamais de mot de passe.
3. `tag-release.sh tv|phone <version>` : vérifie que `version.properties` correspond, crée le tag annoté `tv-<ver>`/`phone-<ver>` avec les notes tirées de la dernière entrée de `docs/HANDOFF.md` § 0 ; simulation par défaut.
4. `docs/RELEASES.md` : § 6 et § 8 complétés avec ces scripts ; tableau § 9 initialisé.

## Critères d'acceptation
```sh
bash -n tools/release/sign-apk.sh tools/release/tag-release.sh
bash tools/release/sign-apk.sh a.apk b.apk          # refuse proprement sans --apply / CB_KEYSTORE
bash tools/release/tag-release.sh tv 0.14.15-beta   # affiche la commande git tag sans l'exécuter
git tag | wc -l                                      # 0 (rien créé par l'agent)
grep -n 'castbridge-release' docs/RELEASES.md tools/release/migrate-signing.md
```

## Cas limites
- Android exige la **même** clé pour une mise à jour : la migration est forcément une réinstallation ; le dire en gras.
- Les TV sans Play Store (GaiaOS) installent par l'explorateur : la procédure le rappelle (HANDOFF § 0, 2026-10-01).

## À ne pas faire
Pas de génération de clé, pas de signature réelle, pas de tag, pas de commit sur les branches partagées, aucun secret ni mot de passe.

## Rapport
`STATUT: BLOQUÉ` (D12) + documents livrés ; checklist de migration (nombre de TV concernées : à demander au propriétaire).
