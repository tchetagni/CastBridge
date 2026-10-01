STATUT: TERMINÉ

- 2026-10-01 16:42 | départ, lecture du cahier | commit -
- 2026-10-01 16:54 | jeux DEV-3 (1 754 cas) et GELÉ-3 (851 cas, TSV + SHA-256) écrits ET mesurés AVANT toute règle : DEV-3 53,3 %, GELÉ-3 52,4 % | commit (voir git log)
- 2026-10-01 16:59 | règles : suffixes de sous-titres, mots saison/épisode en 12 langues, chiffres romains, dossiers parents, bruit WhatsApp/Telegram, camelCase, « Titre - NN - épisode » en dossier : DEV-3 78,5 %, GELÉ-3 79,5 % | commit voir git log
- 2026-10-01 17:19 | catalogue docs/NAMING-PATTERNS.md, LIBRARY-AGENT.md, HANDOFF.md, tests faux positifs / ReDoS / regroupement, `:core:test` complet | commit voir `git log`

## Résumé final
- **Livré** : `docs/NAMING-PATTERNS.md` (111 formes par famille, sources + licences « de mémoire », méthode, mesures, ReDoS, regroupement, limites) ; règles `NameParser` (sous-titres, 12 langues + romains + dossiers, dates, spéciaux, 101/0101 avec preuve, CD/Part, éditions, `@canal`/`t.me`/`Forwarded`, CamelCase) ; `Namer`, `SeriesClassifier.plan` (clé de regroupement, `Titre (année)`), `SeriesAliases` facultative et vérifiée.
- **Sources** : aucun accès réseau utilisé ; formes connues des conventions Kodi / Plex / Jellyfin / Sonarr / TheTVDB, rien copié (salle blanche) ; licences notées de mémoire, à confirmer.
- **Tests avant → après** : GELÉ-3 (neuf, écrit et mesuré avant toute règle) 447/850 = 52,6 % → 844/850 = 99,3 % (à la main 68,2 % → 98,7 %) ; mauvais type 141 → 0 ; GELÉ 2 073 → 2 074 / 2 087 ; GELÉ-DUR 799 / 812 inchangé ; DEV-3 (réglé dessus, non honnête) 53,8 % → 100 %.
- **Garde-fous** : `FalsePositiveTest` (≈ 80 noms), `PathologicalNamesTest` (≈ 990 noms de 250 caractères + 6 000 mélanges, délai 2 s, pire cas 194 ms, aucune expression à remplacer), `SeriesGroupingTest`. Un faux positif réel trouvé et corrigé (`Folge 2012`).
- **`:core:test` complet** (banc « core seul ») : 1 609 tests, 10 en échec, tous `LearnLotsTest` (préexistants, contenu `content/learn`, hors périmètre). **`:sender` et `:receiver` non compilés** (plugin Android introuvable dans le cloud) ; rien essayé sur la TV ni le téléphone.
- **Constat** : `Prison Break [S01-E08].avi` était déjà corrigé dans `integration/agents` ; verrouillé par tests, plus autres formes à crochets.
- **À valider / décider** : relire sur la vraie bibliothèque ; écrire l'édition d'un film dans le nom (change les noms produits) ; brancher la table d'alias dans l'écran « Séries » si voulu ; 2 attendus discutables restent dans GELÉ-3 (gelé, non modifié).
- **Branche** `claude/naming-patterns`, pas de PR.
