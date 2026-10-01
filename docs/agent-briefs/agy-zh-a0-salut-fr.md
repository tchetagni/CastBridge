# Mission pour Agy : revue technique en lecture seule du lot `zh-a0-salut-fr`

Instruction pour Agy sous supervision de Claude Code (session cloud `castbridge-3e`, branche `claude/agy-mission`). Aucun média n'est produit ; aucune synthèse n'est lancée.

TÂCHE AGY
- Identifiant : AGY-001-audit-initial-zh-a0-salut-fr
- Scope : `zh-a0-salut-fr` (cible chinois mandarin, source français, niveau A0, thème « salut »)
- Branche ou référence Git à examiner : `origin/integration/agents` (ou cette branche) ; ne rien modifier hors du fichier de sortie
- Objectif : état des lieux factuel de ce que contient le dépôt pour ce lot (pack, `media.json`, budget, outil de synthèse) et de ce qui manque pour une production conforme à ton cahier (registres, preuves juridiques, ASR, mesures audio)
- Fichiers autorisés en lecture : `content/langues/**` (NB : ici les packs sont sous `content/langues/<scope>/langue.json`, pas `langues/<scope>/`), `tools/**` (outil `tts_synth.py` et vérifications), `docs/LANGUES*.md` s'ils existent, `docs/agent-briefs/**`
- Fichier de sortie attendu : `reports/reviews/zh-a0-salut-fr-agy.md` (seul chemin d'écriture ; format imposé par ton cahier)
- Commandes autorisées : `git status/branch/worktree list/log/diff/show`, lecture de fichiers, `ffprobe` si des fichiers audio existent, recherche de secrets sans afficher les valeurs
- Actions interdites : modifier un pack, un registre, un fichier juridique ou un script ; générer un média ; choisir une voix ; corriger ou compléter `reading` ; `git commit/push/merge/rebase/reset` ; accéder à un secret
- Critères de fin : rapport écrit, réponse finale au format prévu (STATUT, RAPPORT, SYNTHÈSE, ATTENTE), puis arrêt

## À contrôler (faits seulement)
1. Présence ou absence de `AGENTS.md`, `.claude/CLAUDE.md`, `registry/voices-registry.json`, `registry/engines-registry.json`, `legal/` : à ma connaissance **absents** de ce dépôt (BLOQUANT pour toute production, à confirmer).
2. Pack `content/langues/zh-a0-salut-fr/langue.json` : champs `audio` (`m:<id>`), `reading`, `tr` ; liste des identifiants, doublons, références `m:` sans entrée dans `media.json`. **Ne pas** juger le pinyin ni les tons : les signaler à valider par un natif.
3. `content/langues/zh-a0-salut-fr/media.json` : fichiers réellement présents ou non, taille, codec, fréquence, canaux, débit (« non vérifiable dans cet environnement » si le fichier est absent), mention `synthetic`, `statut` « bêta : non validé », voix, moteur, licence.
4. `content/langues/budget.json` et plafonds (lot ≤ 100 Mo, téléphone ≤ 500 Mo, total ≤ 6 Go ; les décisions du propriétaire citent un quota de 2 Go : signaler toute différence).
5. Outil `tts_synth.py` : quels moteurs et voix il appelle, où il lit ses identifiants (sans les afficher), s'il peut publier ou téléverser, s'il applique `synthetic: true`.
6. Recherche de secrets dans les fichiers du scope et de l'outil (chemin et type seulement).
7. Contrôle ASR : outil et résultats présents ou non ; sinon « contrôle ASR non vérifiable dans cet environnement » (BLOQUANT).

## Rappel
Tu n'étends pas le périmètre, tu ne crées aucune sous-tâche, et tu attends une instruction de Claude Code avant toute suite. La décision finale revient au responsable humain.
