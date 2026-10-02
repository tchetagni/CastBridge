# w7-23 — Documentation : `docs/PLUG-AND-PLAY-SYNC.md` (remplace `BT-PLUG-AND-PLAY.md`), mises à jour ADMIN / PARENTAL / TRANSFER / REMOTE-TUNNEL-TV / API-SERVER / TRIAL-EDITION / HANDOFF

**Vague 7d · Effort M (≈ 2 j) · Modèle : haiku · Statut PRÊT (après 7a-7c fusionnées).** Conception : `docs/coordination/DESIGN-W7-PLUG-AND-PLAY-SYNC.md` (tout). Branche `claude/sonnet-w7-23`. Rapport : `docs/agent-reports/sonnet-w7-23.md`.

## Objectif
Documenter **ce qui est fusionné** (lire les rapports `docs/agent-reports/sonnet-w7-*.md` : ne pas documenter un cahier non fusionné), en français, pour un futur agent et pour le propriétaire : (1) nouveau `docs/PLUG-AND-PLAY-SYNC.md` : architecture (schéma texte de la conception § 3.1), découverte en couches, fenêtre d'association, identité et ré-adoption, états `LinkManager`, canal `CBSX` (renvoi à `SYNC-PROTOCOL.md`), domaines, UX (parcours 3 touches, « Réparer la connexion », textes), diagnostic/journal, compatibilité, limites honnêtes, plan de mesure (renvoi à `TEST-CAMPAIGN.md` § W7) ; (2) `docs/BT-PLUG-AND-PLAY.md` : bandeau en tête « Historique : remplacé par PLUG-AND-PLAY-SYNC.md (2026-10) ; les sections Service API / Liaison persistante restent valables » ; (3) `docs/ADMIN.md` § 11 : services `…0006`, routes `/api/sync/*`, `/api/knock`, `/api/link/*` ; (4) `docs/PARENTAL.md` : « le domaine `par` n'annonce que des identifiants ; les rapports passent toujours par CBTP/W6 » ; (5) `docs/TRANSFER.md` : politique de voie, grand livre, file persistée ; (6) `docs/REMOTE-TUNNEL-TV.md` § 6 : état exposé dans `set` ; (7) `docs/API-SERVER.md` : rien côté serveur (le dire) ; (8) `docs/TRIAL-EDITION.md` : la synchro est ouverte en essai ; (9) `docs/HANDOFF.md` § 0 (entrée datée), § 9 (ce qui reste non vérifié sur la vraie TV), § 11 (liste des docs) ; (10) `docs/SYNC-PROTOCOL.md` : relecture de cohérence (§ 1-3 écrits par w7-02/03/04), ajout § 4 « Versions et compatibilité » et § 5 « Sécurité : ce que le canal garantit et ne garantit pas ».

## Pourquoi (preuves)
- `docs/BT-PLUG-AND-PLAY.md` (136 lignes, états/sécurité/tunnel) ; `docs/ADMIN.md:391-465` (§ 11) ; `docs/PARENTAL.md:160-200` ; `docs/TRANSFER.md` ; `docs/HANDOFF.md:182-186` (§ 9).
- Règle du propriétaire : HANDOFF tenu à jour, sans secret (`docs/HANDOFF.md:3-4`).

## Fichiers possédés
Nouveau : `docs/PLUG-AND-PLAY-SYNC.md`. Modifiés : `docs/BT-PLUG-AND-PLAY.md` (bandeau seulement), `docs/ADMIN.md`, `docs/PARENTAL.md`, `docs/TRANSFER.md`, `docs/REMOTE-TUNNEL-TV.md`, `docs/API-SERVER.md`, `docs/TRIAL-EDITION.md`, `docs/HANDOFF.md`, `docs/SYNC-PROTOCOL.md` (§ 4-5), `docs/CHANGELOG.md` (entrée W7). **Hors zone** : tout code, `docs/coordination/**`, `docs/agent-briefs/**`.

## Étapes
1. Lire la conception et les rapports fusionnés ; dresser la table « prévu / fusionné / reporté ».
2. Rédiger `PLUG-AND-PLAY-SYNC.md` (≤ 250 lignes, tables plutôt que prose) avec, pour chaque texte utilisateur, le nom de la constante `LinkTexts.*`.
3. Mises à jour ciblées des autres docs (diffs minimaux, pas de réécriture).
4. HANDOFF : entrée datée en tête de § 0 (branches, tests, non vérifié), § 9 complété, aucun secret.
5. Vérifier chaque chemin de fichier cité (`test -f`), chaque route citée présente dans `tools/routes/routes.txt`.

## Critères d'acceptation
```sh
for f in $(grep -o 'android/[A-Za-z0-9_/.-]*\.kt' docs/PLUG-AND-PLAY-SYNC.md | sort -u); do test -f "$f" || echo "MANQUE $f"; done   # rien
grep -c 'PLUG-AND-PLAY-SYNC' docs/HANDOFF.md docs/BT-PLUG-AND-PLAY.md   # ≥ 1 chacun
grep -in 'pin\s*[:=]\s*[0-9]\{6\}\|cbk_[0-9a-f]\{8\}' docs/PLUG-AND-PLAY-SYNC.md docs/HANDOFF.md | wc -l   # 0 (aucun secret)
```

## Cas limites
Un cahier partiellement fusionné ⇒ section « Reporté » explicite ; D-W7-5 non tranchée ⇒ documenter la ré-adoption **sans** code foyer.

## À ne pas faire
Ne pas documenter l'avenir comme présent ; ne pas supprimer `BT-PLUG-AND-PLAY.md` ; ne pas écrire de secret, d'IP réelle, d'adresse Bluetooth.

## Rapport
`STATUT`, table prévu/fusionné/reporté, fichiers modifiés.
