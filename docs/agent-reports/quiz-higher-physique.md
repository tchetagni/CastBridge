# Rapport — Quiz Supérieur, filière Physique (L1, L2, L3)

Date : 2026-10-03. Branche `claude/quiz-higher-physique`. 1 500 questions en 12 lots (`content/quiz/batches/higher-<niveau>-physique-01..04.json`), statut `review` à l'import. Aucune modification de `content/quiz/dist`, `android/` ni `backend/`.

## L1 (l1-phys) : 500 questions

Difficultés (1..5) : 1: 148, 2: 152, 3: 100, 4: 60, 5: 40  
Bonnes réponses : A: 126, B: 125, C: 124, D: 125

| Sous-thème | Questions |
|---|---|
| mécanique du point : cinématique, repères et vecteurs | 21 |
| mouvements rectiligne uniforme et uniformément varié | 21 |
| mouvement circulaire et accélération centripète | 21 |
| lois de Newton | 21 |
| forces usuelles (poids, tension, réaction, frottement) | 21 |
| chute libre et projectile | 20 |
| travail et puissance d'une force | 21 |
| énergie cinétique et théorème de l'énergie cinétique | 21 |
| énergie potentielle et conservation de l'énergie mécanique | 21 |
| oscillateur harmonique (ressort, pendule simple) | 21 |
| quantité de mouvement et chocs | 20 |
| gravitation universelle et lois de Kepler | 21 |
| statique des solides et moment d'une force | 21 |
| analyse dimensionnelle et unités SI | 21 |
| incertitudes et chiffres significatifs | 21 |
| thermodynamique de base : température, chaleur, calorimétrie | 20 |
| gaz parfaits et loi des gaz | 21 |
| premier principe (énergie interne) | 21 |
| ondes mécaniques : onde progressive, célérité, périodicité | 21 |
| son, intensité sonore et niveau en décibels | 21 |
| optique géométrique : réflexion, réfraction, loi de Snell-Descartes | 20 |
| lentilles minces et formation des images | 21 |
| électrostatique : charge, loi de Coulomb, champ et potentiel | 21 |
| circuits en courant continu : loi d'Ohm, lois de Kirchhoff | 21 |

## L2 (l2-phys) : 500 questions

Difficultés (1..5) : 1: 148, 2: 151, 3: 100, 4: 61, 5: 40  
Bonnes réponses : A: 126, B: 125, C: 125, D: 124

| Sous-thème | Questions |
|---|---|
| mécanique analytique d'introduction : coordonnées généralisées | 20 |
| référentiels non galiléens et forces d'inertie | 21 |
| forces centrales et moment cinétique | 21 |
| oscillateurs amortis et forcés, résonance | 21 |
| systèmes de points et centre de masse | 21 |
| mécanique du solide : moment d'inertie, rotation autour d'un axe | 21 |
| ondes : superposition, interférences, ondes stationnaires | 21 |
| diffraction et réseaux | 21 |
| polarisation de la lumière | 21 |
| thermodynamique : deuxième principe et entropie | 21 |
| cycles thermodynamiques et machines thermiques (Carnot) | 21 |
| changements d'état et diagramme de phases | 21 |
| transferts thermiques : conduction, convection, rayonnement | 21 |
| électrostatique : théorème de Gauss, dipôle, condensateurs | 21 |
| magnétostatique : champ magnétique, loi de Biot et Savart, théorème d'Ampère | 21 |
| induction électromagnétique : loi de Faraday, loi de Lenz | 21 |
| circuits RLC en régime sinusoïdal et impédances | 21 |
| équations de Maxwell (forme usuelle) | 21 |
| ondes électromagnétiques dans le vide | 21 |
| relativité restreinte : postulats, dilatation du temps, contraction des longueurs | 21 |
| introduction à la physique quantique : effet photoélectrique, dualité onde-corpuscule | 21 |
| atome de Bohr et spectres | 20 |
| méthodes mathématiques : équations différentielles, séries de Fourier | 20 |
| physique statistique d'introduction : distribution de Maxwell-Boltzmann | 20 |

## L3 (l3-phys) : 500 questions

Difficultés (1..5) : 1: 152, 2: 148, 3: 100, 4: 60, 5: 40  
Bonnes réponses : A: 128, B: 124, C: 124, D: 124

| Sous-thème | Questions |
|---|---|
| Mécanique quantique : postulats, fonction d'onde, équation de Schrödinger | 20 |
| Puits de potentiel et effet tunnel | 20 |
| Oscillateur harmonique quantique | 20 |
| Moment cinétique et spin | 20 |
| Atome d'hydrogène | 20 |
| Méthodes d'approximation : perturbations | 20 |
| Physique statistique : ensembles microcanonique, canonique, grand-canonique | 20 |
| Statistiques de Fermi-Dirac et de Bose-Einstein | 20 |
| Gaz de photons et rayonnement du corps noir | 20 |
| Thermodynamique des phénomènes irréversibles | 20 |
| Mécanique analytique : équations de Lagrange, principe de moindre action | 20 |
| Formalisme hamiltonien | 20 |
| Électromagnétisme dans la matière : diélectriques, milieux magnétiques | 20 |
| Rayonnement d'une charge accélérée et antennes | 20 |
| Guides d'ondes et optique ondulatoire | 20 |
| Relativité restreinte : quadrivecteurs, énergie-impulsion | 20 |
| Physique du solide : réseaux cristallins, diffraction des rayons X | 20 |
| Modèle des bandes et semi-conducteurs | 20 |
| Supraconductivité (phénoménologie) | 20 |
| Physique nucléaire : radioactivité, énergie de liaison, fission et fusion | 20 |
| Physique des particules : particules élémentaires et interactions | 20 |
| Astrophysique d'introduction : étoiles, cosmologie de base | 20 |
| Optique quantique et laser (principe) | 20 |
| Physique de l'énergie : photovoltaïque, énergies renouvelables en Afrique | 20 |
| Méthodes expérimentales : traitement du signal, électronique analogique et numérique | 20 |

## Contrôles et rejets corrigés

- Chaque lot : `python3.12 tools/quiz-bank/quizbank.py import --dry-run <fichier>` → 125 questions valides, 0 rejet, 0 avertissement.
- Rejets rencontrés puis corrigés par les rédacteurs : « choix identiques ou presque » (le contrôle ignore la ponctuation et les symboles : `+`/`−`, `/`, exposants — formules réécrites en toutes lettres), choix dépendant de l'ordre (« Les deux », « Tous les »), questions sans « ? », choix de plus de 100 caractères, espaces mal placés.
- 4 questions identiques entre niveaux (modes de transfert thermique, coordonnées généralisées, fréquence de résonance RLC, énergie du photon) ont été remplacées côté L2/L3 ; plus aucun doublon exact entre les 1 500 questions.

## Limites

- Difficultés attribuées manuellement (par sous-thème, schéma fixe) : quelques D4/D5 sont plutôt de niveau 3 ; relecture humaine nécessaire (statut `review`).
- Beaucoup de formules sont écrites en toutes lettres dans les choix à cause du contrôle d'unicité.
- Les calculs numériques (peu nombreux) ont été recalculés par script Python avant écriture.
- `python3` (3.11) échoue sur `qb/gen/sup_compta_l1.py` (f-string d'un autre rédacteur) : validations faites avec `python3.12`. `cells_report.py --batches-only` et `quizbank.py check` échouent (`KeyError: 'track'`) sur des lots bruts non normalisés ; non corrigé (hors périmètre).
- La bonne réponse est la plus longue dans environ 20 à 45 % des cas selon le lot.
