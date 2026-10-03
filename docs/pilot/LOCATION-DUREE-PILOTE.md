# Procédure : pilot de location à durée choisie (3 semaines)

## S0 (05–11 octobre) — Préparation

**Décisions à cocher avant de démarrer :**
- [ ] D-W16-1 : modèle testé = unités heures + jours + défaut 30 jours
- [ ] D-W16-2 : bouquets pilote = Apprendre seulement (Langues reste gratuit)
- [ ] D-W16-3 : pas de changement d'unité pendant une location (attendre la fin)
- [ ] D-W16-4 : horloge de sûreté pour heures = 14 jours après la fin du pilote (15/11)
- [ ] D-W16-5 : jours et défaut honorés en entier jusqu'au 01/12, heures s'éteignent le 15/11
- [ ] D-W16-6 : pilote Apprendre ET Langues (Langues gratuit, jamais loué)
- [ ] D-W16-7 : 3 locations louables en parallèle par TV
- [ ] D-W16-8 : quota par licence = 3 contrats actifs
- [ ] D-W16-9 : build TV verrouillée avec cœur W16 dans Download de la clé
- [ ] D-W16-10 : compteur d'usage en minutes, sans relais serveur au pilote
- [ ] D-W16-11 : télémétrie avec consentement du foyer
- [ ] D-W16-12 : bilan le 16 novembre (heures) et 02 décembre (jours)

**Qui fait quoi :**
- Propriétaire (vous) : décisions, émission, relevés, bilan
- Point focal par foyer : accueil, recrutement, fiches de suivi, assistance
- Téléphone du foyer : relais des demandes, remise des dossiers de livraison
- TV loée : comptage des minutes, affichage de la fin, relevés sauvegardés

**Fichiers clés :**
- `tools/pilot/pilot.json` : réglages du pilote (durées, fins, quota)
- `tools/pilot/louer.py` : émission et remise d'une location
- `tools/pilot/bilan.py` : calcul des résultats après chaque bilan
- `~/.castbridge-activation/pilot-rentals.csv` : registre d'émission (propriétaire)

**Fenêtres :**
- Émission : 12 octobre au 01 novembre, 23:59 Douala
- Heures utilisées : avant le 15 novembre (14 jours de grâce)
- Jours et défaut : avant le 01 décembre
- Bilan heures : 16 novembre
- Bilan jours : 02 décembre

**Préparation technique :**
1. Copiez le build TV verrouillé dans le Download de la clé USB
2. Préparez `pilot.json` avec les paliers et fins
3. Listez les 15–25 foyers, 2 points focaux (par localité seulement, pas d'identifiants de personne)

---

## S1 (12–18 octobre) — Ouverture et premières locations

**Pas à pas :**

1. Recrutement du foyer (point focal) :
   - Consentement télémétrie (enregistrer oui/non et date sur la fiche foyer)
   - Copie du build TV verrouillé + dossier d'activation sur la clé USB
   - Signature de la charte : test gratuit, 3 semaines, questions en fin

2. Premier contact (foyer via point focal) :
   - « Je veux louer Apprendre pour **[12 heures d'utilisation / 7 jours / 30 jours]** »
   - Point focal note : date, bouquet, unité, quantité (aucun identifiant personnel)

3. Émission (propriétaire sur Mac) :
   ```
   tools/pilot/louer.py location --tv <code> --bouquet classe-cm2 \
     --choix 12h|7j|defaut --pilote tools/pilot/pilot.json
   ```
   - Émettre le dossier de livraison
   - Enregistrer dans `~/.castbridge-activation/pilot-rentals.csv`

4. Remise (point focal) :
   - Dossier de livraison reçu du propriétaire
   - Transmission au foyer sur clé USB ou via le téléphone
   - Inscription dans la fiche : date, unité, durée réelle affichée avant la fin
   - Relevé du code TV masqué : `XXXX-…-XXXX` (14 chiffres, aucune donnée confidentielle)

5. Activation (foyer à la maison) :
   - Sur le téléphone : « Activer la TV »
   - Sur la TV : « Locations sur la TV »
   - Le dossier de livraison est ouvert ; la location est active
   - **Date de fin réelle** s'affiche (« à utiliser avant le 15/11 » pour les heures)

6. Suivi hebdomadaire (point focal) :
   - Chaque semaine : appel rapide ou SMS au foyer
   - « Avez-vous loué cette semaine ? Tout fonctionne ? »
   - Incidents notés sur la fiche (aucune donnée confidentielle ou identifiant)

---

## S2 (19–25 octobre) — Prolongations, relocations, entretien

**Chaque foyer :**

1. Peut **prolonger** une location en cours (même unité) :
   ```
   tools/pilot/louer.py location --tv <code> --bouquet classe-cm2 \
     --choix 12h|7j --pilote pilot.json --prolonger
   ```
   - Les budgets s'additionnent (heures + heures, jours + jours)
   - Jamais mélanger les unités (heures + jours refusé)

2. Peut **relouer** un bouquet terminé = nouvelle location :
   ```
   tools/pilot/louer.py location --tv <code> --bouquet classe-cm2 \
     --choix 6h --pilote pilot.json
   ```

3. **Entretien à mi-parcours** (5 foyers environ) :
   - Point focal : appel individuel, questions orales (pré-questionnaire)
   - Naissent les idées pour le questionnaire S3
   - Relevé manuel : « Vous avez utilisé combien d'heures jusqu'à aujourd'hui ? »

---

## S3 (26 octobre – 01 novembre) — Dernières émissions et questionnaire

**Émissions closes le 01 novembre, 23:59 Douala.**

1. Dernier appel : les foyers qui n'ont pas encore loué
2. Sélecteur **réduit** chez un point focal (test sur place) :
   - Heures : 6 h, 24 h, 96 h (au lieu de 1, 3, 6, 12, 24, 48, 96)
   - Jours : 7 j (au lieu de 1, 3, 7, 14)
   - Défaut : 30 j
3. **Questionnaire S3** administré par le point focal (orale) :
   - Fiche remplie immédiatement (5 questions, 10 min)
   - Aucune donnée personnelle ou confidentielle enregistrée

---

## S4 (02–16 novembre) — Fermeture des heures

**Horloge des heures : 15 novembre, 23:59.**

1. Heures non utilisées **expirent** (date atteinte : fin du test gratuit)
2. Message TV : « Vos heures non utilisées ont expiré le 15/11. Relouer ? »
3. Point focal : appel rapide pour incidents ou questions

4. **Relevés finaux** (propriétaire) :
   - Relever manuellement ou via `tools/pilot/louer.py releve --tv <code>`
   - `castbridge-rental-usage-v1` présent sur la clé
   - Minutes utilisées et fin réelle chacune enregistrées

5. **Bilan heures : 16 novembre**
   ```
   tools/pilot/bilan.py --registre ~/.castbridge-activation/pilot-rentals.csv \
     --releves releves/ --telemetrie export.csv
   ```
   Résultats → `BILAN-PILOTE-LOCATION-DUREE.md`

---

## S5 (17–30 novembre) — En-cours des jours

**Les locations en jours et défaut restent actives.**

1. Point focal : suivi allégé (plus de nouvelles locations)
2. Les foyers qui veulent encore louer doivent renouveler après expiration
3. Aucune date limite pour les jours avant le 01/12

---

## S6 (01–02 décembre) — Fermeture des jours

**Horloge des jours : 01 décembre, 23:59.**

1. Locations en jours et défaut expirent
2. **Bilan complet : 02 décembre**
   ```
   tools/pilot/bilan.py --registre … --releves … --telemetrie …
   ```
   Intensité d'usage (jours vs heures) et complément

3. **Décision GO / AJUSTER / NO-GO** selon les seuils HP2, HP3, HP8, HP9

---

## Points d'attention

- **Confusion heures/jours** : rappeler à chaque remise « Les heures comptent seulement si le contenu est ouvert. Les jours passent même si la TV est éteinte. »
- **Code TV masqué** : `XXXX-…-XXXX` seulement, pas de SIM, pas d'adresse
- **Pas de montant affiché** : test gratuit, question d'intention (non payante)
- **Confidentialité** : numéro de fiche seulement, aucun identifiant personnel
- **Consentement télémétrie** : coché au S0, révoqué à tout moment (notifier le propriétaire)
