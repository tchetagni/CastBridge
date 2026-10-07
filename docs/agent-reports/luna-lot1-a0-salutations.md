# Réception du lot 1 de Luna (GPT 6) — A0 « Salutations et se présenter » (zh, ja, de) + kit de relève v1

Reçu le 2026-10-07 via le propriétaire (copie collée). Luna n'a modifié aucun dépôt et n'a produit aucun média. Statut Luna : « à corriger avant production », QA auto-évaluée 34/40.

## Contrôle de l'orchestrateur (lecture, sans outil : les JSON n'existent pas encore)

Conformes au cahier : objectifs observables A0 repris de `LANGUES.md` § 1.2 ; six leçons, 12 items/leçon, ≥ 8 réemployés (67 %) ; parcours complet sans médias ; budgets très en dessous des plafonds (lot média ≈ 5 Mo, texte ≈ 0,4 Mo) ; un ID média = un texte exact ; aucune voix/validation inventée ; clips conditionnés à l'autorisation ; relecteurs natifs demandés et non présumés.

À corriger ou à faire relire (transmis à Luna au prochain ordre) :
1. **Clip zh** : 很高兴认识你 dépasse la liste A0 (cinq syllabes, tons 3-1-4-4-2/3) ; à garder en reconnaissance seulement ou remplacer par 再见 / 谢谢 ; décision native.
2. **Clip ja** : ordre des répliques — はじめまして se dit d'ordinaire *avant* d'échanger les noms ; よろしくお願いします en clôture de présentation, puis さようなら. À faire trancher par le relecteur japonophone.
3. **Prérequis du graphe** : « à confirmer par Claude » — les nœuds `graph/langue-{zh,ja,de}.json` existent dans CastBridge (`content/graph/`) ; à aligner à l'intégration.
4. **Gabarit JSON de demande** : champs éditoriaux ; le schéma de `mp.py requests` (CastBridge `tools/media-pipeline/`) fait foi, Luna l'a dit.
5. **Taux de réemploi** affirmé, non vérifiable avant les scripts : à contrôler par outil sur `langue.json`.

## Décisions du propriétaire encore nécessaires (identiques à ma revue du 2026-10-07)
Branche de production dans castbridge-content (proposée : `content/langues-a0-zh-ja-de`) ; conflit `zh-nihao` (A : deux identifiants, recommandé) ; moteurs/voix `approved` dans les registres + `AUTORISATION.md` complète ; sort des 361 fichiers de `media/langues-audio-proprietaire` ; clips ; relecteurs natifs nommés.

## Suite donnée
- Agent d'exécution **Relève-Langues** (Sonnet) lancé sur l'exercice d'imitation n° 1 : **allemand A0 « Nombres 0-20 »**, dans un arbre de travail de castbridge-content sur la branche provisoire `content/langues-a0-zh-ja-de` (locale, non poussée, renommable), kit v1 de Luna appliqué, aucun média.
- Le kit v1 (procédure, exemplaire doré L4, gabarits, pièges, grille) est reporté tel quel dans `castbridge-content/work/releve/KIT-RELEVE.md` par la Relève, avec mention de la source (Luna, 2026-10-07).
- Le texte intégral du lot de Luna est conservé par le propriétaire (conversation Luna) ; ce fichier n'en garde que le contrôle et les décisions.

## 2026-10-07 : exercice d'imitation n° 1 livré par la Relève (à noter par Luna)
Branche `content/langues-a0-zh-ja-de` (worktree `../castbridge-content-releve`, commits cc2599d, 9fea490, fafcb8a, 942feb5, non poussés) : `langues/de-a0-nombres-fr/{langue.json,media.json}` (87 items, 6 leçons, 60 exercices, 7 dialogues, 53 IDs média à texte unique, réemploi 60,9 %, 48 Ko), `work/releve/{KIT-RELEVE.md,plan-de-a0-nombres.md,qa-de-a0-nombres.md}` (QA auto 2,8/4 « revise »), `work/de-a0-nombres/{media-requests.provisoire.jsonl (53, 0 conflit),CONFLITS.md,check_pack.py,build_pack.py,gen_docs.py}`, `docs/agent-reports/content-langues-w1.md` créé. zh-nihao A appliqué (6 demandes, 0 conflit ; zh-xiexie oral x4 tracé `sharedWith` par l'outil du dépôt contenu). Faits pour le kit v2 : `level` en majuscule (`A0`) sinon registre « naturel » ; le schéma de `mp.py` fait foi sur le gabarit du kit ; dossier = `<cible>-<niveau>-<thème>-<départ>`.
