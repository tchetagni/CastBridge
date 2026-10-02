# w16-13 — Kit du pilote : procédure du propriétaire, fiche foyer, journal CSV, questionnaire S3, texte de la question de fin de location, LISEZMOI du dossier de livraison

<!-- routage Fable 2026-10-03 -->
> **Modèle : haiku** · escalade : aucune (tout est donné ; aucun texte juridique) · statut : PRÊT
> **Groupe : W16b-3** (vague W16b, docs/contenu, autorisé pendant le gel) · prérequis : aucun (textes alignés sur DESIGN-W16 § 1.3, § 5) · porte : `python3 -m unittest discover -s tools/tests -p 'test_pilot_kit.py'`
> **Jauge : ≈ 120 k jetons entrée / 12 k sortie** (effort S, ≈ 0,5 j) · audit Opus : non

**Vague 16b · Effort S · Modèle : haiku · Statut PRÊT.** Conception : DESIGN-W16 § 1.3 (libellés), § 1.4 (fin du pilote), § 5.2 (fiches, question), § 5.5 (calendrier). Branche `claude/sonnet-w16-13`. Rapport : `docs/agent-reports/sonnet-w16-13.md`. Français ; aucun nom de personne ; aucun montant ; aucun texte juridique (le texte d'information de la question est une ligne d'UX, pas une clause).

## Objectif
Tout ce qui est papier ou texte pour jouer le pilote de 3 semaines : la procédure pas à pas du propriétaire (S0 à S6), la fiche foyer (consentement télémétrie demandé, code TV masqué, numéro de fiche sans nom), le journal des demandes (CSV, colonnes du registre de w16-04), le questionnaire S3 (5 questions orales), le texte exact de la question de fin de location et de la ligne d'information, le `LISEZMOI.txt` type du dossier de livraison (unité, date de fin réelle, « test gratuit »), et l'affiche d'une page « Louer pour la durée que vous voulez : en jours ou en heures d'utilisation ».

## Fichiers possédés
Nouveaux `docs/pilot/LOCATION-DUREE-PILOTE.md`, `docs/pilot/fiche-foyer.md`, `docs/pilot/questionnaire-S3.md`, `docs/pilot/affiche.md`, `content/pilote/journal-demandes.csv` (en-tête seul), `content/pilote/LISEZMOI-livraison.txt`, `tools/tests/test_pilot_kit.py` (vérifie : CSV à l'en-tête attendu, aucun mot interdit, aucune conversion heures/jours). **Hors zone** : tout code.

## Étapes
1. Procédure : S0 décisions (liste D-W16-1…12 cochable), build TV verrouillé dans `Download` de la clé, `pilot.json`, recrutement ; S1-S3 : recevoir une demande (WhatsApp / point focal) ⇒ `louer.py` ⇒ remise ⇒ relevé hebdomadaire ; S4-S6 : relevés finaux, `bilan.py`, décision.
2. Fiche foyer : numéro, code TV `XXXX-…-XXXX`, point focal, consentement télémétrie (oui/non, date), locations (date, bouquet, choix, fin réelle), incidents.
3. Questionnaire S3 : (1) avez-vous loué ? quoi, combien de temps ? (2) pourquoi des heures / des jours / sans durée ? (3) avez-vous été surpris par la fin ? (4) auriez-vous payé ? pour quelle unité ? (5) que changer ?
4. Question de fin de location (TV, w16-10) : « Ce test est gratuit. Si ces 12 heures d'utilisation avaient coûté [palier], les auriez-vous prises ? » + ligne : « Votre réponse est anonyme et sert à fixer les prix. » ; variantes « ces 7 jours », « ces 30 jours ».
5. LISEZMOI : « Vous avez loué CM2 pour 12 heures d'utilisation. Les heures comptent seulement quand le contenu est ouvert sur la TV. À utiliser avant le 15/11/2026 (fin du test gratuit). » / « … pour 7 jours, jusqu'au 19/10/2026. Les jours passent même TV éteinte. » / « … sans durée précise : 30 jours, jusqu'au 11/11/2026. »
6. Test : en-tête CSV, mots interdits (`nom`, `téléphone`, `XAF`), aucune occurrence de « 96 h = », « = 4 jours ».

## Critères d'acceptation
Porte verte ; textes relus à voix haute (une phrase par idée) ; fiche imprimable (une page).

## À ne pas faire
Pas de montant ; pas de clause juridique ; pas de nom.

## Rapport
`STATUT`, liste des fichiers, questions de texte (si une phrase de la conception sonne faux à l'oral).
