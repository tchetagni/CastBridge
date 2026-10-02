# w6-21 — Brouillons juridiques W6 : information du foyer (contrôle parental de toute la TV), divulgation de l'accès aux données d'utilisation, politique de confidentialité (parental, mode minimal, session super), registre des traitements

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : BLOQUÉ partiel (D-W6-4 juriste, D7)
> **Groupe : W6e-1** (vague W6e) · prérequis : w6-06 · porte : `grep -L 'pas un avis juridique' docs/legal/*.md | wc -l`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 6e · Effort M (≈ 2 j) · Modèle : sonnet · Statut BLOQUÉ partiel (D-W6-4 : validation par un juriste ; D7 : nom et contact). L'agent rédige tout ce qui ne dépend pas de ces faits, avec des emplacements `[À COMPLÉTER : …]`.** Conception : `DESIGN-W6-PARENTAL-PHONE-GATE.md` § 2.1, § 2.2, § 2.6, § 6, § 3.5. Branche `claude/sonnet-w6-21`. Rapport : `docs/agent-reports/sonnet-w6-21.md`.

## Objectif
Quatre brouillons **en français, clairement marqués « projet, pas un avis juridique »** dans `docs/legal/` : (1) `INFORMATION-FOYER-CONTROLE-PARENTAL.md` : texte affiché/remis aux membres du foyer (adultes et enfants, deux niveaux de langue) : qui reçoit quoi, à quelle fréquence, ce qui n'est jamais collecté, comment savoir que c'est actif (indicateur), comment le faire arrêter (administrateur de la TV), aucune transmission à l'éditeur ; (2) `DIVULGATION-ACCES-DONNEES-UTILISATION.md` : texte de divulgation proéminente pour `PACKAGE_USAGE_STATS` (en app, avant l'écran système) et texte « fiche magasin » (si un jour publié : règles Google Play *Families*/*stalkerware* : indicateur persistant, finalité parentale, pas d'usage caché) ; (3) `POLITIQUE-CONFIDENTIALITE-complement-W6.md` : paragraphes à insérer dans « CastBridge et vos données » (TELEMETRY § 7) : données parentales locales, rapports TV→téléphone, mode minimal du téléphone (ce qui est vérifié : la preuve de la TV, aucune donnée personnelle), session super administrateur (interne à l'éditeur, aucune donnée client), séparation avec l'assistance à distance ; (4) `REGISTRE-TRAITEMENTS-parental.md` : fiche de traitement (finalité, base, données, destinataires = parents détenteurs, durée, mesures : chiffrement, local, indicateur) ; plus les **indicateurs** (Cameroun 2024/017, OHADA consommation, UE si applicable, magasins) sous forme de questions au juriste.

## Pourquoi (preuves)
- `docs/TELEMETRY.md § 7` (texte existant, modèle), `docs/CONDITIONS-ASSISTANCE-A-DISTANCE.md` (séparation), `docs/legal/**` (w3-13, w5-21 : même dossier et même style s'ils existent ; sinon créer `docs/legal/README.md`), `C/parental/ParentalPrivacy.kt` (w6-06 : **copier les listes mot pour mot**), DESIGN W6 § 6 (tableau des risques).

## Fichiers possédés
Nouveaux `docs/legal/{INFORMATION-FOYER-CONTROLE-PARENTAL,DIVULGATION-ACCES-DONNEES-UTILISATION,POLITIQUE-CONFIDENTIALITE-complement-W6,REGISTRE-TRAITEMENTS-parental}.md` (+ `docs/legal/README.md` s'il manque). **Hors zone** : tout le reste.

## Étapes
1. Lire les sources ; copier les listes de `ParentalPrivacy`.
2. Rédiger les quatre textes (chaque paragraphe cite la mesure technique qui le rend vrai : indicateur, chiffrement, aucune route serveur).
3. Section « Questions au juriste » numérotées (QJ-1…) : surveillance d'adultes du foyer, mineurs, formalités auprès de l'autorité, hébergement, magasins.

## Critères d'acceptation
```sh
ls docs/legal/INFORMATION-FOYER-CONTROLE-PARENTAL.md docs/legal/DIVULGATION-ACCES-DONNEES-UTILISATION.md docs/legal/POLITIQUE-CONFIDENTIALITE-complement-W6.md docs/legal/REGISTRE-TRAITEMENTS-parental.md
grep -c 'pas un avis juridique' docs/legal/*W6*.md docs/legal/INFORMATION-FOYER-CONTROLE-PARENTAL.md   # ≥ 1 chacun
grep -n 'captures d.écran' docs/legal/INFORMATION-FOYER-CONTROLE-PARENTAL.md   # ≥ 1 (liste « jamais »)
```

## Cas limites
Si `ParentalPrivacy` n'est pas fusionné : utiliser la liste de la conception § 2.2 et le dire.

## À ne pas faire
Ne pas promettre plus que le code (pas « inviolable », pas « impossible ») ; ne pas trancher une question juridique ; pas de prix.

## Rapport
`STATUT: BLOQUÉ` (juriste, D7) + ce qui est rédigé, `QUESTION:` liste QJ.
