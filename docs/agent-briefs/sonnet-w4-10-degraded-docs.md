# w4-10 — Documentation du mode réduit (formats, édition d'essai, console, CGV)

<!-- routage Fable 2026-10-02 -->
> **Modèle : haiku** · escalade : sonnet au 2e échec ou fichier sensible · statut : PRÊT (après 4b)
> **Groupe : W4b-3** (vague W4b) · prérequis : w4-07, w4-08, w4-09 · porte : `grep -c 'mode réduit' docs/TRIAL-EDITION.md`
> **Jauge : ≈ 60 k jetons entrée / 5 k sortie** (effort S) · audit Opus : non

**Vague 4b · Effort S (≈ 0,5 j) · Statut PRÊT (après w4-07, w4-08, w4-09).** Conception : `docs/coordination/DESIGN-W4-MODE-DEGRADE.md`. Branche `claude/sonnet-w4-10`. Rapport : `docs/agent-reports/sonnet-w4-10.md`.

## Objectif
Partout où il est écrit « à la fin du plafond, la TV se verrouille », dire ce qui est vrai désormais : **production** ⇒ mode réduit (ce qui reste, ce qui s'arrête, comment renouveler) ; **essai** ⇒ verrouillage. Le brouillon de CGV (w3-13) reçoit la clause correspondante.

## Fichiers possédés
`docs/ACTIVATION-FORMAT.md` (§ 3.3 « Plafond d'usage », dernière phrase), `docs/TRIAL-EDITION.md` (§ 11 liste blanche : ajouter la liste réduite ; § 15 ; nouveau § 17 « Mode réduit (D6, 2026-10-02) » avec le tableau de la conception), `docs/OWNER-CONSOLE.md` (phrase « la TV ne se reverrouille jamais » → préciser « illimitée ; sinon mode réduit à l'échéance »), `docs/ADMIN.md` (API : champs `degraded`, `endedAt`, 403 des routes fermées), `docs/legal/CGV-location.md` (si présent : clause « fin de la durée de la clé » ; sinon noter dans `docs/legal/README.md` ce qui manque, sans créer la CGV), `docs/HANDOFF.md` (entrée § 0). **Hors zone** : code, `RENTAL-LOTS.md` (w4-06/w4-18).

## Étapes
1. ACTIVATION-FORMAT § 3.3 : remplacer « la TV se verrouille et demande un nouveau code » par le double comportement + libellés exacts (`"Mode réduit : clé à renouveler"`, `"Activation terminée"` pour l'essai) ; les anciennes TV (avant vague 4b) verrouillent : le dire.
2. TRIAL-EDITION § 17 : tableau reste/s'arrête, règle en une phrase pour le client, rappel J-7/J-3/J-1 (w2-04), rappel quotidien de l'écran, sortie par une nouvelle clé (point focal, W4-C), achats conservés après renouvellement.
3. ADMIN.md : routes 403 en mode réduit (liste = `DegradedPolicy` du rapport w4-07), JSON.
4. CGV : clause brève (« À l'échéance de la durée de votre clé, CastBridge-TV passe en mode réduit : … »).
5. HANDOFF § 0.

## Critères d'acceptation
```sh
grep -n 'Mode réduit' docs/ACTIVATION-FORMAT.md docs/TRIAL-EDITION.md docs/ADMIN.md   # ≥ 3
grep -n 'se verrouille et demande un nouveau code valide' docs/ACTIVATION-FORMAT.md docs/TRIAL-EDITION.md   # 0 hit hors du cas essai (relire)
```

## À ne pas faire
Pas de code ; pas d'avis juridique (le dire dans la clause CGV : « brouillon à valider par un juriste ») ; français.

## Rapport
`STATUT`, sections modifiées, écarts conception/code remontés.
