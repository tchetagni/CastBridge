# Consigne du propriétaire à Astra — 2026-10-29

« Améliore la formation de langue chinoise pour le niveau initial, afin que je valide ton efficacité d'experte avec ton puissant moteur multimédia génératif. »

## Périmètre
Les quatre unités chinoises A0 pour francophones livrées dans l'app : `content/langues/zh-a0-salut-fr`, `zh-a0-phonetique-fr`, `zh-a0-nombres-fr`, `zh-a0-famille-fr` (`langue.json`, `media.json`, `figures/`). Ne touche pas aux variantes `-en` ni aux autres langues. Lis d'abord `docs/LANGUES.md`, `docs/LEARN.md`, `docs/LEARN-REVIEW.md` et les validateurs/tests existants du format (`grep -rl "langue.json\|\"type\": \"langue\"" android/core/src tools` par exemple) pour rester strictement dans le format accepté par les apps.

## Ce qui est attendu
1. **Audit pédagogique court** (`work/astra-zh-a0/AUDIT.md`) : progression, charge (A0 = débutant complet), tons, pinyin, caractères, réemploi, exercices, ce qui manque pour un francophone d'Afrique centrale (exemples culturels pertinents, sans clichés).
2. **Améliorations du contenu** dans les `langue.json` : explications plus claires, exercices mieux gradués, réemploi, pièges de tons et paires minimales, sans dépasser la charge d'un A0. Incrémente `version` de chaque unité modifiée ; `state` reste `review`.
3. **Multimédia généré par toi** (c'est ce que le propriétaire veut évaluer) :
   - **Images** : illustrations utiles à la compréhension (scènes de salutation, famille, nombres sur les doigts à la chinoise, schéma des 4 tons, ordre des traits de quelques caractères…). Format final WebP ≤ 120 Ko, ≤ 1280 px, lisible sur TV 720p à distance ; aucun texte chinois rendu par l'image elle-même si tu ne peux pas garantir les bons caractères (mets le texte dans le contenu, pas dans l'image) ; aucune personne réelle, aucune marque. Range-les sous `content/langues/<unité>/media/` (ou l'emplacement que le format prévoit) et référence-les dans `media.json` selon le format existant.
   - **Audio / vidéo** : seulement si tu disposes réellement d'un outil de génération audio ; sinon ne fabrique rien (pas d'espeak, pas d'audio factice) et écris dans la passation les demandes d'audio précises (texte, pinyin, ton, débit) à produire plus tard. Aucune imitation de voix réelle.
   - Chaque média généré : provenance « synthétique (Astra, <outil>) », statut « bêta : non validé », prompt conservé dans `work/astra-zh-a0/PROMPTS.md`. Budget total ajouté ≤ 10 Mo pour les quatre unités.
4. **Contraintes produit** : TV pilotée par 5 touches (pas de glisser-déposer, pas de micro) ; téléphone aussi. Les identifiants existants restent stables (les médias `m:zh-…` existants et `zh-nihao` / `zh-d1-a` ne sont pas renommés). Tons, pinyin et caractères : signale tout doute ; la validation par un locuteur natif reste à faire, ne prétends jamais l'inverse.
5. **Passation** : `work/astra-zh-a0/PASSATION.md` : ce qui a changé par unité, liste des médias (chemin, taille, rôle), ce que Claude doit vérifier (validateurs/tests à lancer, intégration au build, essais sur TV et téléphone), et un auto-bilan honnête de ce que ton moteur a bien ou mal fait.
