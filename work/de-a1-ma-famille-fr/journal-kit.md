# Journal de production et kit de relève — Allemand A1

## Journal

- **Fait :** plan, langue.json (9 leçons), media.json déclaratif, demandes JSONL, QA JSON, figures décrites, scénario clip textuel, règles et pièges.
- **Bloqué :** aucune production de média ; voix/moteur non approuvés, AUTORISATION.md non fournie, clip non autorisé, aucun relecteur natif identifié. Prérequis graphe à confirmer par Claude.
- **Taille du lot texte :** estimée d’après les fichiers présents ; langue.json ~85958 octets ; tout le dossier ~153058 octets à ce stade, très inférieur à 3 Mo.
- **Lot média :** aucun actif produit (0 octet d’actifs) ; budget prévisionnel de demandes ~3 Mo audio comprimé, très inférieur au plafond de 100 Mo ; la taille réelle dépendra des voix et phrases retenues.
- **Figures :** descriptions uniquement ; budget de conception visé <8 Ko chacune une fois vectorisées. Animations non requises.
- **Écart au budget :** aucun dépassement estimé. Les estimations ne sont pas des mesures d’actifs générés.
- **Réemploi mesuré :** 8/12, soit 66,7 %, entre chaque paire d’unités consécutives.
- **Vocabulaire :** 108 occurrences de items (12 × 9). Le total de mots-types nouveaux reste sous 80 avec réemploi requis ; arbitrage de définition à faire si 80 types distincts sont exigés.

## Dix règles tranchées

1. Un nom est toujours affiché avec son article : `der Vater`, `die Mutter`, `das Kind`.
2. Une majuscule en début d’énoncé ne prouve pas qu’un mot est un nom : `Bitte` en tête, mais `bitte` et `danke` en milieu d’énoncé.
3. Chaque identifiant média porte un seul texte exact, ponctuation comprise ; texte différent = ID différent.
4. Les ID lexicaux suivent `de-<mot>` et restent stables entre leçons.
5. Les répliques ont des ID séparés des mots et une seule classe vocale A ou B par réplique.
6. Les demandes média restent `draft-not-authorized` sans voix/moteur approuvés et AUTORISATION.md.
7. Une activité TV n’exige ni glisser-déposer ni micro ; choix par touches et clavier à l’écran seulement.
8. Toute production orale a écoute modèle + répétition + auto-évaluation par télécommande, sans enregistrement.
9. Chaque unité réemploie au moins 8/12 items de la précédente ; calcul noté dans le rapport.
10. Les repères camerounais sont factuels et locaux ; toute affirmation sur les usages allemands porte « à confirmer par un natif ».

## Cinq pièges A1 allemands

1. **Article omis :** `Vater` → `der Vater` ; garder l’article dans le vocabulaire.
2. **Genre extrapolé :** `die Mädchen` est neutre au singulier `das Mädchen` ; vérifier chaque genre, à confirmer par un natif.
3. **Pluriel régulier supposé :** `der Vater` → `die Väter`, pas *Vaters* ; enseigner le pluriel seulement s’il est requis.
4. **Umlaut ignoré :** `Mutter` / `Mütter` change voyelle et sens/number ; écouter avant de produire.
5. **Majuscule mal généralisée :** écrire `Danke` au début d’une phrase, mais `danke` après `Ich sage danke`; les noms restent majuscules partout.
