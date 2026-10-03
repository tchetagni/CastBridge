# Quiz supérieur — Littérature (L1, L2, L3)

500 questions par niveau, soit 1 500 au total, en 12 fichiers `content/quiz/batches/higher-<l1|l2|l3>-litterature-<01..04>.json` (125 questions chacun, clés `l1-lit`, `l2-lit`, `l3-lit`). Les 12 fichiers passent `quizbank.py import --dry-run` (avec `python3.12` : `python3` 3.11 plante sur `qb/gen/sup_compta_l1.py`, fichier d'un autre rédacteur) avec 125 valides, 0 rejet, 0 avertissement.

Détail (comptes par sous-thème, rejets corrigés, faits à relire, limites) : `quiz-higher-litterature-l1.md`, `-l2.md`, `-l3.md`.

Résumé commun, identique dans chaque fichier :
- Difficultés 1 à 5 : 37-38 / 37-38 / 25 / 15 / 10 (≈ 30/30/20/12/8 %).
- Bonnes réponses : A 32, B 31, C 31, D 31.
- Sous-thèmes : tous couverts (L1 : 22, L2 et L3 : 26), 4 à 6 questions chacun, au plus 4,8 % d'un fichier.
- Statut `review` : relecture humaine requise.

Limites : la bonne réponse est parfois la plus longue (12 à 34 % selon le fichier) ; `quizbank.py check` sur toute la banque n'a pas été lancé ; les faits signalés « à relire » dans chaque rapport de niveau doivent être vérifiés par un humain.
