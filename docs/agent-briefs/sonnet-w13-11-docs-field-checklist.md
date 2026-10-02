# w13-11 — Documentation : `docs/BLOCAGES.md` (catalogue, codes support, liste de reproduction terrain `adb`/`curl`), mises à jour TRANSFER / ADMIN / HANDOFF / CHANGELOG
<!-- routage Fable 2026-10-02 -->
> **Modèle : haiku** · escalade : aucune (copie structurée depuis la conception) · statut : PRÊT
> **Groupe : W13-d** (vague W13, tranche S2) · prérequis : w13-01 … w13-10 fusionnés (pour citer les noms définitifs) · porte : `python3 -m unittest discover -s tools/tests -p 'test_docs*.py' ; grep -c '^| `' docs/BLOCAGES.md`
> **Jauge : ≈ 150 k jetons entrée / 15 k sortie** (effort S) · audit Opus : non

**Vague 13d (docs) · Effort S (≈ 1 j) · Modèle : haiku · Statut PRÊT.** Conception : `DESIGN-W13` § 2, § 3.6, § 4.2, § 6. Branche `claude/sonnet-w13-11`. Rapport : `docs/agent-reports/sonnet-w13-11.md`.

## Objectif
(1) Nouveau `docs/BLOCAGES.md` : § 1 principe (« aucun blocage silencieux »), § 2 catalogue (table du § 2 de la conception, colonne message = `BlockerTexts` définitif), § 3 puce à trois vérités (ce que vert/ambre/rouge/gris veulent dire), § 4 code support (format, où le lire sur le téléphone et la TV, comment le relier au journal exporté), § 5 **liste de reproduction terrain** (table § 4.2 de la conception, commandes `adb`/`curl` **sans aucun secret** : `$PIN` lu sur la TV, jamais écrit), § 6 ce que la TV affiche (bandeau, page « Derniers blocages », réglage) ; (2) `docs/TRANSFER.md` : section « Blocages et reprise » qui renvoie à `BLOCAGES.md` et remplace les anciennes phrases « En pause : … » ; (3) `docs/ADMIN.md` : route `GET /api/link/blockers` et champ `code` des corps d'erreur ; (4) `docs/HANDOFF.md` § 0 (entrée datée : W13 fusionnée, ce qui reste non vérifié sur la vraie TV) et § 9 ; (5) `docs/CHANGELOG.md`.

## Pourquoi (preuves)
- Règle du propriétaire : `docs/HANDOFF.md` à jour à chaque étape, sans secret (`HANDOFF.md:3`).
- `docs/TRANSFER.md` décrit le protocole multi-connexions ; `docs/ADMIN.md` liste les routes et en-têtes (`X-CB-Pin`, `X-CB-Token`).
- Noms : « CastBridge » / « CastBridge-TV ».

## Fichiers possédés
Nouveau : `docs/BLOCAGES.md` ; `docs/TRANSFER.md` (une section), `docs/ADMIN.md` (une sous-section), `docs/HANDOFF.md` (§ 0 une entrée, § 9 une ligne), `docs/CHANGELOG.md` (une entrée). **Hors zone** : code, autres docs, `docs/coordination/**`.

## Étapes
1. Lire `C/link/BlockerTexts.kt` (fusionné) et copier les textes **définitifs** (pas ceux de la conception s'ils diffèrent) ; une ligne par `BlockerCode` : code · symptôme · cause · ce que fait l'app · ce que l'utilisateur fait.
2. § 5 : chaque ligne = situation, commande(s), résultat attendu téléphone, résultat attendu TV, journal attendu (`CB-…`) ; commandes `curl` avec `-H "X-CB-Pin: $PIN"` ; aucune IP réelle (utiliser `$TV`).
3. HANDOFF § 0 : « 2026-MM-JJ : W13 (aucun blocage silencieux) fusionnée : … ; **non vérifié sur la vraie TV** : bandeau TV, verrou non compté, lien profond, chien de garde sur Bluetooth » ; § 9 : une ligne.
4. CHANGELOG : entrée « Envoi vers la TV : chaque blocage est nommé, expliqué, réparable (code de la TV à saisir dans la carte, puce à trois niveaux, notification « Envoi bloqué »), codes support ».

## Critères d'acceptation (hors ligne)
`grep -c '^| `' docs/BLOCAGES.md` ≥ 47 ; `grep -nE '[0-9]{6}|cbk_|192\.168\.[0-9]+\.[0-9]+' docs/BLOCAGES.md` vide ; `grep -n 'W13' docs/HANDOFF.md` ≥ 2 ; aucun « sender »/« receiver » dans un texte utilisateur.

## À ne pas faire
Pas de secret, pas d'IP réelle, pas de reformulation des textes de `BlockerTexts` ; ne pas éditer `docs/coordination/**`.

## Rapport
`STATUT`, fichiers touchés, écarts entre la conception et `BlockerTexts` constatés.
