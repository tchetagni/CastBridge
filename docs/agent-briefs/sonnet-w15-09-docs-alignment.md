# w15-09 — Documentation alignée : CHANGELOG, HANDOFF § 0/§ 9, REGRESSIONS, STORAGE (UsbMigration), TRANSFER, tests Python locaux
<!-- routage Fable 2026-10-02 -->
> **Modèle : haiku** (mécanique : tout est donné ci-dessous) · escalade : sonnet si un fait manque · statut : PRÊT
> **Groupe : W15-S1** (vague W15, tranche S1) · prérequis : w15-01…06 fusionnés (les lignes à écrire citent leurs tests) ; décision D-W15-4 (par défaut : retirer la mention UsbMigration) · porte : `python3 -m unittest discover -s tools/tests -p 'test_content_tools.py'` et `python3 tools/release/check_versions.py`
> **Jauge : ≈ 120 k jetons entrée / 12 k sortie** (effort S, ≈ 0,5 j) · audit Opus : non

**Vague 15 S1 (docs) · Effort S · Modèle : haiku · Statut PRÊT.** Branche `claude/sonnet-w15-09`. Rapport : `docs/agent-reports/sonnet-w15-09.md`.

## Objectif
La documentation dit la vérité du dépôt : versions publiées, régressions du jour avec leurs tests, fonctions documentées mais absentes, limites réelles du transfert, et les tests Python ne sont plus rouges à cause de fichiers ignorés par git.

## Fichiers possédés
`docs/CHANGELOG.md`, `docs/HANDOFF.md` (§ 0 : une entrée « W15 stabilisation » ; § 9 : liste **NV** ; en-tête : branche d'intégration `integration/agents`, date), `docs/REGRESSIONS.md` (**créer si W14 w14-14 ne l'a pas encore fait ; sinon ajouter les lignes**), `docs/STORAGE.md` (§ sur « Déplacer les contenus vers la clé », `:182`), `docs/TRANSFER.md` (§ limites : 429, `abort`, `sessions`), `tools/tests/test_content_tools.py` (exclusion des chemins ignorés par git), `tools/content-split-repo/check_sizes.py` et `tools/content-media/check_media.py` (option `--respect-gitignore`, défaut vrai). **Hors zone** : tout code Kotlin, les autres docs.

## Éditions (dans l'ordre)
1. **CHANGELOG** : ajouter, sous `[Non publié]` ou en sections versionnées selon le format existant, les entrées manquantes : téléphone 1.2.30, 1.2.31, 1.2.32-beta (code 62 : « correctifs Ouvrir avec et Réassocier », commit `2c07510`) ; TV 0.14.18-beta (code 62/63 verrouillée) et 0.14.19-beta (code 64) ; propriétaire 0.2.4 (code 6). Sources : `version.properties`, `git log --oneline -- version.properties`, `docs/HANDOFF.md` § 0.
2. **REGRESSIONS.md** (table W14 § 4.4 : `R-NN | date | symptôme | appareils/versions | cause (fichier:ligne, commit) | parcours | test ajouté | version corrigée | statut`) : R-01 (L-01, L-02 ; tests `PinKeysTest`, `LinkDriverTest.bluetoothTunnelKeepaliveDoesNotRotateTokens`), R-02 (`9f6777f`, `SendChoiceTest`), R-03 (`cf47cc0`, `LinkDriverTest.reassociateKeepsTheTvSaved…`), R-04 (T-01, `MultipathServerTest.receivingShowsAFastTransferInFlight`), R-05 (L-04/L-06/L-11, cahiers w15-11/17), R-06 = F-06 (`CastSession` « TV injoignable, nouvel essai… » en boucle, logcat 17:17, P-18, cahier w15-15), R-07 = F-07 (notification media3 résiduelle, w15-15). Les identifiants et lignes sont dans `PLAN-STABILISATION-MULTIMEDIA-SYNC-2026-10-02.md` § 2.
3. **HANDOFF** : § 0 entrée datée « W15 : plan de stabilisation (lien), tranche S0 fusionnée : liste des cahiers et de leurs tests » ; § 9 : remplacer le paragraphe par la liste **NV** de l'inventaire (`PLAN` § 1, colonne Mat. = NV : 20 lignes) ; en-tête : « Dernière mise à jour » et branche d'intégration `integration/agents` ; ligne « Tests instables connus » : état après w15-07.
4. **STORAGE.md** `:182` : « Déplacer les contenus vers la clé » ⇒ « non branché dans l'application (`UsbMigration` existe dans le cœur, testée, sans route ni écran) ; le déplacement passe par `/api/storage/move` (`Mover`) » (D-W15-4).
5. **TRANSFER.md** § limites : balayage des sessions (30 min), éviction au 4ᵉ `begin`, `abort` à l'annulation, `GET /api/transfer/sessions`, `Déplacer` = preuve requise (w15-05), bornes de reprise (w15-04).
6. **Tests Python** : `check_sizes.check` et `check_media.check` ignorent tout chemin pour lequel `git check-ignore -q <chemin>` réussit (si `git` absent : comportement actuel) ; `test_content_tools.py` : les deux tests passent sur un poste avec `content/langues-media/` ; ajouter un test synthétique « un fichier ignoré par git n'est pas compté ». Pour le test « 70 % » (`:250-257`) : **mesurer** (`python3 -m unittest tools.tests.test_content_tools -k mapping`) et écrire le ratio réel dans le rapport ; ne pas baisser le seuil (décision propriétaire).

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -n "1.2.32" docs/CHANGELOG.md` et `grep -n "0.14.19" docs/CHANGELOG.md` non vides ; `grep -c "^| R-0" docs/REGRESSIONS.md` ≥ 7 ; `grep -n "UsbMigration" docs/STORAGE.md` dit « non branché » ; aucun secret ; un seul commit.

## À ne pas faire
Pas de code Kotlin ; ne pas réécrire les sections historiques du HANDOFF ; ne pas inventer un fait absent du plan ou de `git log` (poser `QUESTION:` dans le rapport).

## Rapport
`STATUT`, liste des fichiers, ratio mesuré du test « 70 % », questions.
