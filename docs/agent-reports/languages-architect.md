STATUT: TERMINÉ
ORDRES-TRAITES: 5
QUESTION: (non bloquante, 3 décisions du propriétaire avant la vague 4, détail dans docs/LANGUES.md § 11) quota média du téléphone séparé des 100 Mo (recommandé : 1 Go réglable) ; contenu dérivé publié sous CC BY-SA 4.0 (oui/non) ; enregistrements humains ou synthèse libre marquée.
- 2026-10-01 15:20 | départ : cahier + COORDINATION lus, branche claude/languages-architect depuis origin/integration/agents ; faisabilité audio/vidéo mesurée | commit 7e2d6a4
- 2026-10-01 15:40 | squelette core (modèle, parseur, licences, correction, SRS, positionnement, graphe, budget, planificateur), 7 graphes, budget.json, outil de budget, pack d'exemple | commit 63f7932
- 2026-10-01 16:05 | docs/LANGUES.md complet (12 sections), HANDOFF, rapport ; relu les ordres (5, aucun pour ce chantier) et le cahier (aucune réponse du coordinateur)

## Résumé
- **Livré** : `docs/LANGUES.md` (niveaux A0→natif à critères observables, correspondances d'examens, positionnement, spécificités des 7 langues, modèle de contenu, lots texte ≤ 3 Mo / média ≤ 100 Mo, budget 6 144 Mo chiffré, licences, spécification d'intégration, 4 vagues, faisabilité, contrôles des vérificateurs) ; `content/langues/` (budget, pack d'exemple) ; `content/graph/langue-*.json` ; `tools/content-budget/langues_budget.py` ; `tools/langues/gen-graph.py` ; `core/.../langues/` + `LanguesTest`.
- **Tests** : avant, aucun test « langues » ; après, 20/20 en JVM via harnais (compilateur Kotlin de Gradle 8.14 + doublure kotlin.test) car `gradle` ne résout pas le plugin dans le cloud (Maven 429). Les tests existants n'ont pas été relancés (aucun fichier existant du `core` modifié).
- **Faisabilité cloud** : espeak-ng OK (robotique, 7 langues) ; Piper/Common Voice/Tatoeba/Commons injoignables depuis le cloud ; pas de police CJK ; ffmpeg Opus/H.264/WebP OK. Vidéo avec personnes et voix naturelles : impossible ici.
- **À valider sur matériel / par le propriétaire** : `gradle :core:test --tests 'castbridge.core.LanguesTest'` ; rendu CJK sur la TV 32 bits ; les 3 décisions ci-dessus ; extension de `content_budget.py` et de `scopes.json` de `claude/net-architect` à l'intégration (§ 10).
- **Écart au cahier** : `tools/content-budget` n'existait pas sur `integration/agents` ; la branche `net-architect` en fait un dossier, d'où `tools/content-budget/langues_budget.py` (pas de conflit de chemin).
- **Branche** : `claude/languages-architect` (pas de PR, pas de main).
