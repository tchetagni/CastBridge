# Passation Astra → Claude — chinois A0 francophone

29 octobre 2026. Travail effectué directement dans les sources. **Tout reste bêta : non validé, `state: review`. Validation native à faire.** Aucune compilation Gradle, aucun test Android ou test long, aucun émulateur, commit, push, publication ou changement de version.

La règle directe du propriétaire « aucun changement de version » prime sur l’incrément demandé dans CONSIGNE.md : versions conservées (Salutations 3 ; Phonétique, Nombres, Famille 1). Claude gère ensuite les versions des packs et des lots, leurs empreintes et leur intégration. Aucun générateur global n’a été relancé.

## Changements par pack

- **Salutations** : trois formules prioritaires ; contexte d’atelier à Douala/Yaoundé ; glosses 是 / 不 et 请 précisées ; explications du pinyin, du ton neutre et de nǐ hǎo ; neuf exercices gradués avec corrigés. Dialogue et médias existants conservés. Image de rencontre reliée à 你好 et à sa carte.
- **Phonétique** : séances courtes, contrastes mā/mǎ et má/mà, troisième ton en isolation versus phrase, ton neutre contextuel ; indications zh/ch/sh, x/q et ü corrigées ; huit exercices par unité. Les quatre courbes de la figure étaient toutes à gauche : correction de la figure ET des éléments animés, mêmes identifiants/étapes, traits épaissis. Pas de nouveau média raster pour ce schéma.
- **Nombres** : progression 1–3, 4–5, puis 6–10 et zéro ; comptage de cahiers ; âge d’un personnage fictif, question surtout destinée à un enfant ; contraste 十 shí / 是 shì et avertissement sur 四 sì / 十 shí. Huit exercices. Image de trois cahiers reliée à 三 et à sa carte.
- **Famille** : correction de la possession avec 的, fratrie selon l’âge relatif, syllabes neutres ; dialogue raccourci à quatre répliques ; sept exercices. Image reliée à 家 et à sa carte. Le champ audio du dialogue a été retiré car le texte a changé : `zh-famille-d1` reste intact au manifeste, mais ne doit pas être reconnecté sans réenregistrement. Le modèle oral d’une phrase n’utilise plus à tort l’audio du mot seul 妈妈.

Total : 40 exercices (35 QCM, 5 productions facultatives avec modèle), mêmes identifiants et mêmes effectifs par unité. Pas de saisie, de glisser-déposer ni d’enregistrement requis. Les exercices restent utilisables sans les images ni l’audio. Les paires minimales sont pour l’instant travaillées en lecture ; aucune compétence d’écoute n’est certifiée.

## Fichiers touchés

Sous `content/langues/` :

- `zh-a0-salut-fr/langue.json`, `zh-a0-salut-fr/media.json`.
- `zh-a0-phonetique-fr/langue.json`, `zh-a0-phonetique-fr/figures/zh-a0-phonetique-fr-u1-a1.json`.
- `zh-a0-nombres-fr/langue.json`, `zh-a0-nombres-fr/media.json`.
- `zh-a0-famille-fr/langue.json`, `zh-a0-famille-fr/media.json`.
- Les trois nouveaux fichiers WebP listés ci-dessous.

Sous `work/astra-zh-a0/` : `AUDIT.md`, `PROMPTS.md`, `verify_content.py`, `VERIFICATIONS.txt`, cette `PASSATION.md`. `CONSIGNE.md` préexistait et n’a pas été modifié.

Aucune variante `-en`, autre langue, source Android, configuration de version ou fichier de test existant modifié. Toutes les entrées préexistantes des manifestes sont inchangées, dont `zh-nihao` et les identifiants `m:zh-…`. `zh-d1-a` n’est pas présent dans les quatre packs ciblés ; aucun renommage ni ajout de cet identifiant.

## Médias livrés et emplacement d’intégration

Chaque image : **synthétique (Astra, image_gen)** ; **bêta : non validé** ; WebP 960 × 640, qualité 75. Aucun texte chinois, aucune marque, aucun personnage réel. Prompts exacts dans [PROMPTS.md](PROMPTS.md), empreintes SHA-256 dans [VERIFICATIONS.txt](VERIFICATIONS.txt).

| Source durable dans le dépôt | Octets | Rôle / référence |
|---|---:|---|
| `content/langues/zh-a0-salut-fr/media/zh-a0-salut-astra-rencontre.webp` | 38 844 | Carte 你好 ; `m:zh-a0-salut-astra-rencontre` |
| `content/langues/zh-a0-famille-fr/media/zh-a0-famille-astra-scene.webp` | 47 630 | Carte 家 ; `m:zh-a0-famille-astra-scene` |
| `content/langues/zh-a0-nombres-fr/media/zh-a0-nombres-astra-trois.webp` | 16 484 | Carte 三 ; `m:zh-a0-nombres-astra-trois` |

Les manifestes référencent `images/<nom>.webp` car le format résout les fichiers dans le **lot média jumeau**. Des copies identiques sont déjà présentes localement dans :

- `content/langues-media/zh-a0-salut/images/zh-a0-salut-astra-rencontre.webp`.
- `content/langues-media/zh-a0-famille/images/zh-a0-famille-astra-scene.webp`.
- `content/langues-media/zh-a0-nombres/images/zh-a0-nombres-astra-trois.webp`.

**Attention d’intégration : `content/langues-media/` est ignoré par Git.** Les trois sources durables sous les packs évitent leur perte lors d’une passation. Claude doit recopier ces fichiers dans les lots médias / le dépôt de contenus lors de l’intégration. Ne pas supposer que leur présence locale suffit à les livrer aux appareils. Aucun manifeste anglais n’a été modifié.

Budget raster unique : **102 958 octets** ; avec les trois copies locales : **205 916 octets**, très inférieur aux 10 Mo. Chaque WebP est inférieur à 120 000 octets et 1280 px.

Compatibilité du manifeste : `LangValidator` impose actuellement une `voiceLicense` à TOUS les médias `synthetic`, y compris les images. Les nouvelles images portent donc `voiceLicense: CASTBRIDGE-ORIGINAL`, comme leur licence de contenu, avec un commentaire explicite : il n’y a aucune voix et ce champ ne désigne pas la licence du moteur. Claude devrait limiter ce contrôle aux médias vocaux à l’avenir. `source` porte la provenance et le statut en texte ; `state: review` et `alt` sont aussi stockés, mais le parseur actuel de `MediaEntry` ne les expose pas. Vérifier leur affichage effectif avant livraison.

## Contrôles réalisés

- `python3 work/astra-zh-a0/verify_content.py` : OK ; lecture JSON, conservation des versions et des identifiants, intégrité des anciennes entrées médias, références, index/choix QCM, modèles, pinyin du vocabulaire et des dialogues, fiches ≤ 650 caractères, images valides et budget, égalité des copies, repli de la figure identique aux éléments finaux de l’animation.
- `git diff --check` : OK.
- Inspection visuelle des trois sorties générées puis des trois WebP compressés : personnages et objets lisibles, trois cahiers distincts, aucun texte intégré.
- Aucun lancement du validateur Kotlin : les contrôles Python ciblés ne remplacent pas `LangValidator`, `AnimationRules`, le rendu Android ni la validation linguistique.

## Vérifications et intégration attendues de Claude

1. **Relecture par un locuteur natif enseignant** : dialogues, tons, pinyin, registre de 你好吗, question d’âge, termes familiaux et articulation. Vérifier aussi les figures d’ordre des traits non modifiées. Le dialogue Salutations conserve des suites de plusieurs 3es tons ; ne pas leur appliquer mécaniquement la règle de deux syllabes.
2. **Tests Kotlin à lancer après intégration** : `LanguesTest`, `LangLotBuildTest`, `LangLotConsumerTest` ; validation des quatre packs avec `LangPackJson` / `LangValidator`. Le registre des lots contient encore les anciennes empreintes : `LangLotBuilder` peut refuser le contenu changé tant que Claude n’a pas effectué la mise à jour autorisée des lots et versions.
3. **Deux attentes de tests à adapter explicitement** dans `LanguesTest` : `unknownExerciseKindIsAParseError` remplace la chaîne `dictation` du pack Salutations, désormais absente ; employer une fixture indépendante contenant un genre connu. `additiveExerciseFieldsAreReadWhenPresentAndIgnoredWhenMissing` suppose que le pack Salutations n’a aucun `explanation`, désormais faux ; garder le test de rétrocompatibilité via une fixture sans champs additifs. Ne pas supprimer ces garanties. Le test qui attend neuf exercices reste satisfait.
4. **Figure** : utiliser `LessonJson`, `AnimationRules.check` et `AnimationLint` sur le fichier modifié, contrôler toutes les pauses et le repli. Les tests `LearnAnimationTest` / `LearnAnimationExamplesTest` sont utiles mais la suite des exemples ne parcourt pas automatiquement ce dossier de contenu : prévoir un contrôle ciblé. Ne pas relancer `gen_zh_prononciation.py` sans reporter les corrections : il pourrait les écraser.
5. **Médias** : intégrer les trois WebP à leurs lots jumeaux, mettre à jour les empreintes/catalogues nécessaires, vérifier `tools/tests/test_free_content.py` et l’export des contenus libres sur un environnement possédant les anciens médias. Après construction par Claude, mesurer les lots avec `tools/content-budget/langues_budget.py --check <dossier-des-lots>`. Les générateurs de contenu peuvent écraser les sources éditées : conserver ces modifications.
6. **CastBridge-TV, vrai écran 720p** : essai à distance, quatre flèches + OK, focus visible, retour, texte et pinyin lisibles (ǎ, ǚ, ü), choix longs non tronqués, accès aux modèles sans micro. Vérifier affichage du statut bêta et des crédits. L’écran Langues inspecté affiche les textes mais **pas les images des cartes ni les animations**, et son retour de correction ne montre pas encore `explanation` : ce raccordement UX est à réaliser/contrôler par Claude, hors du périmètre contenu de cette intervention. Ne pas annoncer ces médias comme visibles dans l’application avant ce travail.
7. **CastBridge (téléphone)** : mêmes contenus, navigation tactile, lecture avec/sans lot média, chargement des WebP, mise à l’échelle, modèle oral facultatif sans enregistrement. Vérifier qu’une auto-évaluation de lecture n’est pas présentée comme une mesure de prononciation.

## Demandes audio précises — aucune génération audio/vidéo effectuée

Aucun outil de génération vocale adapté disponible dans la session. Aucun espeak, audio factice ni imitation de personne réelle produit. Les anciens audios synthétiques sont conservés sans validation de leur qualité. Demander une voix de mandarin standard, identité originale ou enregistrement consenti, puis écoute et approbation natives. Débit ci-dessous indicatif, à régler sans déformer les tons : version lente **2 à 2,5 syllabes/s**, puis naturelle **3 à 4 syllabes/s** ; mots isolés espacés de **800 ms**, répliques de **600 ms**. Ne pas étirer artificiellement les syllabes neutres.

| Priorité / cible | Texte exact | Pinyin lexical / tons et réalisation à contrôler |
|---|---|---|
| P0, nouveau dialogue Famille ; préparer avant de reconnecter `m:zh-famille-d1` | A : 这是我爸爸。 B : 你好！ A : 这是我妈妈。 B : 你好！ | zhè shì wǒ bàba. / nǐ hǎo! / zhè shì wǒ māma. / nǐ hǎo! ; 4-4-3-4-neutre / 3-3 (réalisation 2-3) / 4-4-3-1-neutre / 2-3. |
| P0, modèle de la phrase Famille x7 ; futur identifiant distinct du mot seul | 这是我妈妈。 | zhè shì wǒ māma ; 4-4-3-1-neutre ; wǒ bas devant mā. Une phrase complète par prise. |
| P1, contrastes phonétiques ; réévaluer `zh-ma1`, `zh-ma2`, `zh-ma3`, `zh-ma4`, `zh-ma0`, `zh-ma1234` | 妈。马。 / 麻。骂。 / 你好吗？ | mā (1), mǎ (3 isolé) / má (2), mà (4) / nǐ hǎo ma (3-3-neutre, réalisation 2-3-neutre). Enregistrer les paires dans les deux ordres et chaque mot isolé ; 吗 s’illustre surtout en contexte. |
| P1, salutations ; réévaluer `zh-nihao`, `zh-xiexie`, `zh-zaijian` | 你好！谢谢。再见！ | nǐ hǎo (réalisation ní hǎo, 2-3), xièxie (4-neutre), zàijiàn (4-4). Fichiers distincts, puis petite séquence. |
| P1, aspiration ; réévaluer `zh-zhi`, `zh-chi` | 知。吃。 | zhī / chī : même 1er ton, contraste de souffle uniquement ; deux ordres, puis mots isolés. |
| P1, nombres/tons ; réévaluer `zh-shiten`, `zh-shi`, `zh-si` | 十。是。 / 四。十。 | shí (2), shì (4) : paire minimale tonale ; sì (4), shí (2) : contraste du son initial et du ton. Deux séquences séparées. |
| P1, comptage ; réévaluer `zh-nombres-count15` | 一、二、三、四、五。 | yī, èr, sān, sì, wǔ : 1-4-1-4-3 ; nombres énumérés, 一 au 1er ton. Version lente avec pauses, puis naturelle. |
| P1, famille ; réévaluer les fichiers de mots existants | 爸爸。妈妈。哥哥。姐姐。弟弟。妹妹。 | bàba (4-neutre), māma (1-neutre), gēge (1-neutre), jiějie (3-neutre), dìdi (4-neutre), mèimei (4-neutre). Un mot par prise ; hauteur du neutre liée au ton précédent. |

Ne pas écraser aveuglément un fichier partagé avec les variantes anglaises. Valider texte/audio/transcription, durée, licence de voix et portée des identifiants avant remplacement ; Claude gère la stratégie des médias partagés.

## Auto-bilan honnête

Le moteur a produit trois scènes cohérentes, sans texte parasite, faciles à compresser et utiles comme supports. Le comptage de trois objets est non ambigu. L’image familiale reste une représentation particulière, explicitement présentée comme fictive ; elle ne démontre pas à elle seule les liens d’aînesse. La salutation pourrait aussi évoquer un au revoir sans contexte : le mot et la fiche apportent ce contexte.

Les arrière-plans sont plus décorés que demandé, mais restent secondaires après compression. Le moteur n’a produit ni audio, ni vidéo, ni gestes de comptage chinois validés. La correction des tons est du travail vectoriel déterministe, pas une démonstration de génération visuelle. La lisibilité réelle à distance, l’efficacité pédagogique, les gestes anatomiques et la langue ne sont pas certifiés par l’inspection d’images. Le principal obstacle restant est le raccordement des médias et des corrigés à l’interface, puis la validation native et les essais appareils.
