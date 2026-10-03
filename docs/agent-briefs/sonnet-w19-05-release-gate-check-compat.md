# w19-05 — Outils : porte de livraison `tools/release/check_compat.py` (refuse l'étiquette quand la matrice de compatibilité ou le chaos sont rouges, ou qu'un changement de protocole n'a pas sa note additive), `docs/PROTOCOL-CHANGES.md`, crochet dans `tag-plan.sh --apply`

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : aucune · statut : PRÊT (après w19-03 et w19-04)
> **Groupe : W19-S1** · prérequis : w19-03, w19-04 fusionnés · porte : `python3 -m pytest -q tools/tests/test_check_compat.py tools/tests/test_release_tools.py`
> **Jauge : ≈ 150 k jetons entrée / 8 k sortie** (effort S, ≈ 0,5 j) · audit Opus : non

**Vague 19 · Effort S · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W19 § 4.4, § 5, D-W19-7. Branche `claude/sonnet-w19-05`. Rapport : `docs/agent-reports/sonnet-w19-05.md`.

## Objectif
(1) `tools/release/check_compat.py` (lecture seule, bibliothèque standard, codes 0/1/2 comme `check_versions.py`) : **(a)** matrice et chaos : lance `gradle --offline :core:test --tests 'castbridge.core.compat.*' --tests 'castbridge.core.journey.Symbiosis*'` (via `tools/agents/gradle-lock.sh`) **ou** lit des `TEST-*.xml` fournis par `--results <dossier>` (CI) ; rouge ⇒ 1 ; **(b)** changement de protocole sans note : `git diff <dernier tag de l'app>..HEAD --` sur `C/sync/Caps.kt`, `C/sync/Reason.kt`, `C/tv/BtProtocol.kt`, `C/trust/HelloHandler.kt`, et sur les lignes `/api/hello` / `VERSION` de `C/tv/ReceiverServer.kt` ; non vide **et** `docs/PROTOCOL-CHANGES.md` sans entrée pour `version.properties` courante ⇒ 1 ; une entrée doit porter `additif: oui` ou `protocol: <n>` + `migration:` ; **(c)** persona de la **version précédente** de la même app absente (`android/core/src/test/resources/compat/<tag précédent>/manifest.json`) ⇒ 1 (« trou dans la matrice ») ; **(d)** `check_versions.py --strict` rouge ⇒ 1 ; `--allow-red "raison"` : passe en avertissement **et** ajoute la raison datée à `PROTOCOL-CHANGES.md` (seule écriture du script, explicite) ; `--record` : après l'étiquette, appelle `tools/compat/record_persona.py --tag <nouveau tag>`. (2) `docs/PROTOCOL-CHANGES.md` : format (une entrée par version d'app : date, `protocol`, caps ajoutées, champs ajoutés, `additif`, migration, personas), première entrée **W19** (protocol 7 ⇒ 8 : `/api/hello` enrichi, `X-CB-Reason`, `X-CB-App`, `lastRejection`, `/api/sync/state`, `purged`, `resumed` ; tout additif). (3) `tools/release/tag-plan.sh` : `--apply` appelle `check_compat.py --strict` avant toute création d'étiquette et s'arrête sur 1 (option `--skip-compat` **absente** ; seul `--allow-red` du script Python permet de passer, avec trace). (4) `tools/tests/test_check_compat.py` : dépôt git temporaire, cas (a)…(d), `--allow-red` écrit la raison, `--results` lit un XML rouge/vert.

## Pourquoi (preuves)
- `tools/release/check_versions.py` (en-tête : lecture seule, codes 0/1/2) et `tag-plan.sh` (DRY-RUN par défaut, jamais de push) : le style et le point d'accroche existent.
- `tools/agents/gate-w17.sh` : modèle de porte « VERT/ROUGE (étape) ».
- R-17 : 1.2.39 et 0.14.25 ont été étiquetés sans qu'aucun test ne fasse tourner le client de l'un contre le serveur de l'autre.

## Fichiers possédés
Nouveaux : `tools/release/check_compat.py`, `docs/PROTOCOL-CHANGES.md`, `tools/tests/test_check_compat.py`. Zone additive : `tools/release/tag-plan.sh` (≤ 15 lignes : appel + arrêt), `docs/RELEASES.md` § 7 « Contrôles » (3 lignes : la porte et son usage). **Hors zone** : tout `android/`, `tools/compat/*` (appelé, non modifié).

## Étapes
1. Tests rouges (dépôt temporaire : tags factices `tv-0.0.1-beta`, fichiers de résultats XML).
2. Script ; messages français ; `--help`.
3. `PROTOCOL-CHANGES.md` + crochet `tag-plan.sh` + 3 lignes `RELEASES.md`.
4. **Vert** : porte ; `bash tools/release/tag-plan.sh` (dry-run) affiche l'étape « compat » sans créer d'étiquette.

## Critères d'acceptation
- Un diff sur `Caps.kt` sans entrée ⇒ 1 avec le nom du fichier et la version attendue dans le message ; avec entrée `additif: oui` ⇒ 0.
- Persona précédente absente ⇒ 1 nommant le dossier attendu.
- `tag-plan.sh --apply` ne crée rien si la porte est rouge (test shell dans `test_release_tools.py` ou simulation).
- Aucun accès réseau, aucune écriture hors `--allow-red`.

## Cas limites
Première version d'une app (aucun tag précédent) ⇒ (c) avertissement, pas erreur ; `gradle` absent ⇒ exige `--results` (code 2) ; dépôt sale ⇒ délégué à `check_versions.py`.

## À ne pas faire
Modifier `version.properties` ; pousser ; créer un tag ; appeler un appareil.

## Rapport
RAPPORT + sortie de `check_compat.py --strict` sur HEAD (attendue : rouge tant que la persona `tv-0.14.26-beta` n'est pas enregistrée ou verte si w19-03 l'a faite : dire laquelle).
