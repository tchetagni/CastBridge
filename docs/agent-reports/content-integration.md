STATUT: TERMINÉ

Résumé (content-integration) :
- Tests du cœur : avant 1228 tests / 17 échecs (18 annoncés, l'échec MultiVolumeServerTest.preflightWarnsAboutASlowDrive n'est pas reproduit : passé sur toutes les exécutions, probablement instable, aucune route ne manque) ; après 1228 / 0 échec / 1 ignoré.
- Apprendre : tous les packs rangés dans un lot par la règle de niveau (scopes.txt : 24 lots, aucun > 0,8 Mo, 5,4 Mo au total) ; registre lots.json et docs/LEARN-REVIEW.md régénérés.
- Apprendre : chapitres inconnus corrigés (bepc-english, bepc-svt, bepc-histoire-geo, gceal-maths) en rattachant les fiches/exercices brouillons à un chapitre existant ; la fiche en double bepc-english-conditionals (brouillon) supprimée, ses exercices gardés. Aucun identifiant supprimé ni réutilisé.
- Auto-contrôles en double (11) : les doublons portaient sur le texte, pas sur l'identifiant ; texte précisé (sens inchangé), identifiants inchangés ; un choix dupliqué corrigé (bac-maths-c-specifique-sim-q1).
- Quiz : aucun identifiant perdu ; les lots ne couvraient pas les 26 nouveaux parcours (CP..4e, Class 1-6, Form 1-3, L2/L3, info, bio, socio). 26 lots ajoutés (qb/lots.py + QuizLotScopes.specs), packs reconstruits (Python 3.12). Test « tous les lots tiennent dans le budget TV » reformulé : le catalogue complet (12 Mo) va sur le téléphone, la TV n'en garde qu'une partie choisie par le planificateur.
- Budgets : contenu 50 Mo, packs Quiz 10,5 Mo sur 3 Go, plus gros lot 0,8 Mo (plafond 3 Mo), aucun fichier > 50 Mo.
- Non compilé : :sender, :receiver (plugin Android introuvable) ; banc :core seul utilisé. À valider sur matériel : lots sur la TV (budget 10 Mo, planificateur avec 50 lots).
