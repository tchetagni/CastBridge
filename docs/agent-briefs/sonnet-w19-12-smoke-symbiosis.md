# w19-12 — Fumée (conditionnel) : J-SYM-2, J-SYM-8 et H-REPRISE dans `tools/smoke/` contre la fausse TV, le jour où la fumée W14 existe

<!-- routage Fable 2026-10-03 -->
> **Modèle : haiku** · escalade : sonnet si `tools/smoke/` exige une structure non documentée · statut : **CONDITIONNEL** (`tools/smoke/smoke.py --tv fake` et `FakeTvMain` **n'existent pas** au 2026-10-03 : w14-07, w14-08, w14-10 sans rapport)
> **Groupe : W19-S3** · prérequis : w14-07 + w14-10 fusionnés, w19-08 fusionné · porte : `python3 tools/smoke/smoke.py --tv fake --only symbiosis`
> **Jauge : ≈ 120 k jetons entrée / 10 k sortie** (effort S, ≈ 0,5 j) · audit Opus : non

**Vague 19 · Effort S · Modèle : haiku.** Conception : DESIGN-W19 § 4.3 (dernier paragraphe), § 1.4 (H-REPRISE). Branche `claude/sonnet-w19-12`. Rapport : `docs/agent-reports/sonnet-w19-12.md`.

## Objectif
Ajouter au squelette de fumée (quand il existe) un groupe `symbiosis` : (1) `smoke_refusal_both_sides` : la fausse TV est lancée en mode « refuse NAME_TAKEN en fermant » ; un envoi depuis l'émulateur téléphone ; vérifier par `dumpsys notification` le texte `Reason` en ≤ 5 s, par `adb logcat -s CastBridge` (fausse TV) la ligne `CB-REFUS`, et qu'aucun second `begin` n'arrive en 60 s ; (2) `smoke_bounded_retry` : fausse TV qui ferme toujours ; compter les `begin` reçus en 10 min simulées (accéléré par le mode `--fast` de la fausse TV si disponible, sinon 3 min réelles et ≤ 6 essais attendus) ; (3) `smoke_resume_restart` : fausse TV redémarrée à 30 % ; même % des deux côtés après reprise ; (4) rappel imprimé de **H-REPRISE** (6 gestes) pour le relevé humain, avec les lectures attendues.

## Fichiers possédés
`tools/smoke/checks/symbiosis.py` (neuf), `tools/smoke/README.md` (section), rien d'autre. **Hors zone** : `android/`, le squelette de fumée lui-même (si une accroche manque ⇒ `QUESTION:`).

## Étapes
1. Vérifier l'existence de `tools/smoke/smoke.py` et de la fausse TV ; sinon `STATUT: BLOQUÉ`, `QUESTION: lancer w14-07/w14-10 d'abord ?` et s'arrêter.
2. Écrire les trois contrôles au format des contrôles existants ; aucune action destructrice ; jamais la TV du propriétaire (`--tv fake` seulement).
3. Porte ; rapport.

## Critères d'acceptation
Les trois contrôles passent sur l'émulateur du coordinateur ; aucun secret imprimé ; durée < 10 min.

## À ne pas faire
Cibler une TV réelle ; modifier le squelette ; attendre sans borne.

## Rapport
RAPPORT + `SYMBIOSE: fumée J-SYM-2/8/3`.
