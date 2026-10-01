STATUT: TERMINÉ
ORDRES-TRAITES: 3

Résumé (ordre 2, cahier integration-2), branche claude/integration-2 depuis 3115b2e :
- Fusionné : usb-data, net-architect, content-quiz-culture, content-quiz-lycee, content-quiz-superieur (8), smart-remote (4), content-learn-anglophone (10). HANDOFF : deux versions gardées.
- Tests : :core:test 1378 / 0 échec (avant fusions 1267 vert chez le coordinateur) ; tests Python quiz-bank 21 OK. NON compilés : :sender, :receiver (plugin Android introuvable). À compiler : conflits à la main dans sender RemoteController.kt et RemoteScreen.kt (smart-remote + voies Bluetooth : les deux gardées, un test visuel utile).
- usb-data : couvre racine Download/CastBridge, réadoption, réglages non secrets, migration, API, tests, protocole manuel (docs/STORAGE.md). Manque : écran « Contenus lourds : clé USB » côté téléphone ; robustesse FAT32/clé pleine à vérifier sur matériel.
- Quiz : dist reconstruit (217 494 questions). 158 identifiants du socle ont disparu (117 culture, 41 droit : quasi-doublons retirés par cult_zz_dedupe ou textes réécrits) : listés dans content/quiz/retired-ids.json, jamais à réutiliser. DÉCISION à confirmer : les ressusciter ou accepter ce retrait.
- Lots : 84 lots Quiz (58 lycée par matière + 26) ; lycée droit/ECM très minces (15-16 questions). 29 lots Apprendre (Class 1-5 ajoutés). Registre lots.json régénéré avec LC_ALL=C.UTF-8 : le hash d'un lot dépend de la locale de la machine (sans locale UTF-8 le test échoue) : à régénérer pareil chez vous.
- Tests adaptés : plafond « tous les packs » (13 Mo > cache TV 11 Mo) et « tous les lots dans le budget TV » : le catalogue entier va sur le téléphone, la TV n'en garde qu'une partie ; plancher par lot Quiz abaissé à 10 questions.
- Budgets (content-budget) : total 44,8 Mo sur 3 Go, plus gros lot ≈ 0,8 Mo (plafond 3 Mo), aucun fichier > 5 Mo. Erreur : 2 figures de 8,6 Ko > 8 Ko (ce2-maths 3-tables, form3-physics 1-measurement).
