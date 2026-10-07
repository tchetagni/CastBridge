# Contrôle OUTILLÉ (Relève) — lot Luna `de-a1-ma-famille-fr`, envoi 1 (2026-10-07)

Source : `castbridge-content-releve/work/de-a1-ma-famille-fr/controle-outils.md` (sonde Kotlin `LangPackJson`/`LangValidator`, `check_pack_a1.py`, `mp.py` 1ad317e). Lot copié tel quel dans le worktree de contenu (commits faae8a1, 505d140), non corrigé.

## Verdict : NON CHARGEABLE par le lecteur (3 bloquants)
1. **Identifiant / thème** `de-a1-ma-famille-fr` : le lecteur découpe en 4 segments et exige un thème `[a-z0-9]{1,16}` ⇒ `de-a1-mafamille-fr` (dossier, id du paquet, 245 identifiants d'unités/vocab/exercices à renommer).
2. **`media.json` sans `file` ni `bytes`** (91 entrées).
3. **`prerequisites` de L1 hors du paquet** : seuls des identifiants d'unités du même paquet sont acceptés ; les nœuds du graphe vont dans le champ additif `prereq`.
Corrigés hors dépôt à l'essai : parse OK, 0 problème.

## Autres écarts
- `wrongWhy` en chaîne (37 fois) au lieu d'une liste alignée sur `choices` ; exercices `order` à `answers` éclatées (9) jamais acceptées ; histoire L9 vide ; aucun champ de trait pour les paires (9 paires présentes, 6 distinctes ; *kommen/können* diffère de 3 lettres : pas minimale) ; 14 identifiants de médias non ASCII (ö, ß supprimés : collisions possibles).
- Articles et majuscules des noms : **0 écart** (bien).
- Demandes `mp.py` : 85 demandes, **16 bloquées, 9 conflits** (tous `de-hallo` : l'exercice x1 réutilise l'audio du mot avec un autre contexte) ; son `media-requests.jsonl` ne suit pas le schéma de l'outil (6 ids orphelins, 10 durées différentes) ; `estimate` plante dessus.
- Items : **108 enseignés** (annonce exacte), **41 distincts, 33 réellement nouveaux** (cible 50-70 : sous la fourchette) ; réemploi 66,7 % exact mais **toujours les 8 mêmes ancres** (la métrique est satisfaite mécaniquement, pas le rappel espacé).

## Décisions prises (orchestrateur)
- Thème sans tiret pour tous les paquets (`mafamille`) ; règles de format ajoutées à l'instruction A1 (ids ASCII, `media.json` file/bytes, `prerequisites` internes, `wrongWhy` liste, `order`, `text` sur les audios d'exercice, ancres variées).
- Outillage fusionné (`4b922684`) : `explanation`/`wrongWhy` lus par le lecteur, `mp.py` insensible à la casse du niveau ; en cours : lecteur tolérant aux thèmes commençant par `v`, `mp.py` prenant le `text` additif des exercices (plus de blocage des audios de QCM).
- Audit pédagogique croisé par Gemini **après** correction du format (envoi 2 de Luna).
