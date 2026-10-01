# Procédure d'arrêt (budget et plafonds)

**Arrêt immédiat, sans demander**, dès que l'une de ces conditions est vraie :
1. `mp.py plan` retourne le code 3 (plafond atteint) ou refuse (code 2 : plafond en monnaie sans tarif connu) ;
2. la dépense cumulée de `state.json` atteindrait `maxCost` de `AUTORISATION.md` avec le média suivant ;
3. un plafond de taille serait dépassé (lot ≤ 100 Mo, quota téléphone 500 Mo, enveloppe 6 Go) ;
4. un secret potentiel est détecté dans le dossier de travail ;
5. une erreur du service (quota, refus, facturation) se répète deux fois de suite sur des médias différents ;
6. un doute linguistique, une voix absente du registre, ou une demande bloquée.

Ce que tu fais à l'arrêt : **ne produis plus rien**, enregistre l'état (`state.json` est déjà à jour média par média), lance `mp.py receive` sur ce qui existe, écris dans `rapport-reception.md` : « ARRÊT : <condition> », combien est produit, combien reste, la dépense. Ne reprends que sur une nouvelle `AUTORISATION.md` du propriétaire. Une reprise saute les empreintes déjà produites : **rien n'est facturé deux fois**.
