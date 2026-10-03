# w20-12 — Docs : `QUIZ.md` § 10 « Jouer sur Internet », `HANDOFF.md`, parcours P-55…P-58, divulgation sécurité, règle « périmètre » dans le gabarit d'exécutant, `REGRESSIONS.md` (rubrique play)
<!-- routage Fable 2026-10-03 -->
> **Modèle : haiku** · escalade : aucune · statut : PRÊT (après w20-01…11 ; peut démarrer en brouillon après w20-03)
> **Groupe : W20-S3** (docs) · prérequis : w20-03 fusionné (brouillon), w20-11 (final) · porte : `python3 tools/docs/check_links.py` (s'il existe) sinon `grep -n '](' docs/QUIZ.md | …` vérifié à la main ; aucun test de code
> **Jauge : ≈ 150 k jetons entrée / 8 k sortie** (effort S, ≈ 0,5 j) · audit Opus : non

**Vague 20 · Effort S · Modèle : haiku.** Conception : `DESIGN-W20` (tout). Branche `claude/sonnet-w20-12`. Rapport : `docs/agent-reports/sonnet-w20-12.md`. Docs seulement : aucun fichier de code, aucun `S/`, `R/`.

## Objectif
(1) `docs/QUIZ.md` : nouveau § 10 « Jouer sur Internet » (pour l'usager : les trois périmètres avec icône et étiquette, le signe « Partie sûre » et sa légende, comment ouvrir sur Internet, le code `XXXX-XXXX`, le lien, rejoindre sans app, ce qui sort de la maison, ce qui ne sort jamais (PIN, fichiers), que faire si Internet tombe, enfants, jetons virtuels seulement) ; mise à jour du tableau « Façon de jouer | Réseau » § 1 (ajout de la colonne périmètre) ; renvoi vers `QUIZ-EN-LIGNE.md` (w20-10) et `PLAY-PROTOCOL.md` (w20-02). (2) `docs/HANDOFF.md` : état W20 (ce qui est fusionné, ce qui attend le gel, ce que le propriétaire doit exécuter : `PLAY-OPS.md`, décisions ouvertes), **sans secret**. (3) Parcours : P-55 « Ouvrir une salle locale sur Internet et jouer avec un téléphone distant », P-56 « Rejoindre sans app par le lien », P-57 « Perte d'Internet pendant une partie Internet », P-58 « Profil enfant et Internet » dans le fichier des parcours (`docs/test-plans/` ou `docs/PARCOURS*.md` : trouver l'existant, ne pas en créer un second). (4) `docs/SECURITY*.md` ou `docs/agent-briefs/protect-07-security-ethics-disclosure.md` : paragraphe « Quiz en ligne » (surface, limites T-15, contact). (5) `docs/agent-briefs/EXECUTOR-PROMPT-TEMPLATE.md` : règle **« Périmètre (W20) »** : « Tout changement du Quiz dit dans son rapport le périmètre touché (TV seule / Réseau local / Internet) et prouve qu'aucun appel réseau n'est ajouté aux deux premiers (`ProxySelector` espion) ». (6) `docs/REGRESSIONS.md` : rubrique « Quiz en ligne » vide avec la convention d'identifiant (`RP-nn`).

## Pourquoi (preuves)
- `docs/QUIZ.md:32-36` : tableau « Façon de jouer | Qui joue | Réseau » : lieu naturel du périmètre.
- Mémoire projet « Always write a handoff » : `HANDOFF.md` à jour à chaque vague.
- `DESIGN-W19` § 5 : la règle de symbiose a été ajoutée au gabarit : même mécanique pour la règle « périmètre ».

## Fichiers possédés
- Zones additives : `docs/QUIZ.md`, `docs/HANDOFF.md`, fichier des parcours existant, `docs/agent-briefs/EXECUTOR-PROMPT-TEMPLATE.md`, `docs/REGRESSIONS.md`, divulgation sécurité existante.
- Interdit : tout fichier de code ; `docs/ADMIN.md` (le PIN n'a pas changé : **ne rien y écrire**).

## Étapes
1. Lire les rapports `sonnet-w20-01…11` fusionnés ; relever les textes français **réels** (ne pas recopier la conception si le code a tranché autrement).
2. Écrire § 10, parcours, handoff, gabarit, régressions, divulgation.
3. Vérifier chaque lien et chaque nom de fichier cité (`ls`).

## Critères d'acceptation
- § 10 lisible par un parent en 3 minutes ; une capture ASCII du bandeau ; la légende des quatre niveaux reprend `TvSignal.LEGEND` mot pour mot.
- `HANDOFF.md` liste les décisions D-W20-* **encore ouvertes** et les faits B-W20-* à confirmer.
- Aucun secret, aucune adresse IP de production, aucun mot de passe.

## Cas limites
- Un cahier non fusionné au moment de la rédaction ⇒ section « à venir » datée, pas une description au présent.

## À ne pas faire
- Ne pas promettre de récompense, tournoi payant ou prix. Ne pas décrire le PIN autrement que `docs/ADMIN.md`. Ne pas créer un second fichier de parcours.

## Rapport
Format RAPPORT + liste des fichiers modifiés + liens vérifiés.
