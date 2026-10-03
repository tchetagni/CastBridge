STATUT: TERMINÉ
CAHIER: w17-10 · MODÈLE: sonnet (cahier écrit pour haiku) · BRANCHE: claude/cloud-w17-05-10 · COMMIT: voir git log (second commit)
JETONS: inconnu
PORTE: `bash -n tools/agents/gate-w17.sh && grep -c "^| " docs/test-plans/STORE-PILOT.md` → `bash -n` OK ; 11 lignes de table (1 en-tête + 10 points).
EXÉCUTION DE LA PORTE : `GATE_W17_CORE_HARNESS=1 tools/agents/gate-w17.sh` → « GATE W17 : VERT » (tests JVM store.*, journey.Store*, lint.* ; vecteurs : 11 contrôles, 0 échec ; routes : OK ; aucun « XAF » dans le cœur de la Boutique). Sans la variable, dans ce conteneur : « GATE W17 : ROUGE (tests JVM) » parce que le plugin Android d'android/ n'est pas résoluble hors ligne ici (même cause que pour le cahier w17-05) ; le chemin de repli en cas d'échec fonctionne (code 1).
FICHIERS: docs/test-plans/STORE-PILOT.md (neuf) ; tools/agents/gate-w17.sh (neuf, exécutable) ; docs/test-plans/README.md (CRÉÉ : il n'existait pas ; index de 4 lignes dont la ligne de STORE-PILOT). Hors zone : aucun. Aucun `adb` dans le script ; le point 10 (`dumpsys meminfo`) est écrit « propriétaire, jamais un agent ».
CHOIX:
1. `gate-w17.sh` lance gradle par `bash tools/agents/gradle-lock.sh` (le fichier du dépôt n'est pas exécutable) et depuis `android/` avec le chemin absolu du dépôt (il n'y a pas d'`android/tools`). Variable `GATE_W17_CORE_HARNESS=1` : repli sur `tools/core-harness/run.sh` pour les conteneurs sans plugin Android (ajout hors cahier, documenté dans l'en-tête du script).
2. Le contrôle « XAF » est un `if grep …; then fail` explicite (le `&& exit 1 || true` du cahier marche aussi, mais ne dit pas quelle étape est rouge).
3. `gate-w16.sh` n'existe pas dans le dépôt : la forme suit `tools/agents/gradle-lock.sh` et `docs/test-plans/CHECKLIST-HUMAINE-LIVRAISON.md` (même gabarit de table, verdict, compte rendu sans secret).
4. STORE-PILOT : colonnes n°, appareil, pré-condition, geste, résultat attendu, OK/KO ; en-tête avec date, versions CastBridge et CastBridge-TV, qui exécute, heure ; point 7 explicitement sur émulateur / TV d'essai dédiée, jamais la TV de référence.
RISQUES: la porte complète (mode par défaut) ne tourne que sur le Mac du propriétaire (plugin Android + gradle-lock) ; le point 9 (téléphone éteint 24 h) se prépare la veille ; STORE-PILOT cite `louer.py --demande` du bureau W16 : à vérifier à sa fusion (nom de l'option non contrôlé ici).
NON FAIT / À VALIDER SUR MATÉRIEL: les 10 points de STORE-PILOT sont, par nature, à faire sur la TV et le téléphone par le propriétaire. FUMÉE: sans objet.
QUESTION: aucune.
AUTOCONTRÔLE: [x] zone [x] porte [x] secrets [x] dépendances [x] FR [x] diff ≤ plafond [x] un commit
