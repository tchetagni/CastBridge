# Langues : la catégorie « Langues » d'Apprendre (conception, avant production)

> **Statut : conception à valider par le propriétaire.** Aucun contenu en masse n'est produit ici (un seul pack d'exemple, `content/langues/zh-a0-salut-fr`).
> Code : `android/core/src/main/kotlin/castbridge/core/langues/` (logique pure, 21 tests JVM `LanguesTest`). Outils : `tools/content-budget/langues_budget.py`, `tools/langues/gen-graph.py`.
> Documents liés : [LEARN](LEARN.md) (moteur, figures, animations), [LOTS](LOTS.md) (cadre des lots), [QUIZ](QUIZ.md), et, sur la branche `claude/net-architect` : `CONTENT-ARCHITECTURE`, `MEDIA-POLICY`, `PEDAGOGY-RUBRIC`, `CONTENT-PUBLISH` (pas encore fusionnés dans `integration/agents` ; voir § 10 pour les points à aligner).
> Tout le contenu reste **« bêta : non validé »** (`state: review`) jusqu'au passage des agents vérificateurs, puis des humains.

## 0. En bref

- **7 langues cibles** : chinois (mandarin), japonais, anglais, allemand, français, italien, espagnol. **Langues de départ** : français et anglais (les langues de nos élèves). 12 paires utiles (le français ne s'apprend pas depuis le français, l'anglais pas depuis l'anglais).
- **8 niveaux** : A0 (découverte), A1, A2, B1, B2, C1, C2, **natif**, chacun défini par des critères **observables** pour les quatre compétences (§ 1).
- **Enveloppe 6 Go, séparée** des 3 Go du reste de la base : 6 144 Mo répartis par langue et niveau (§ 4), vérifiés par un outil et par du code testé.
- **Deux sortes de lots** : **texte** (≤ 3 Mo, TV et téléphone) et **média** (≤ 100 Mo, téléphone, copiés vers la TV sur demande). La TV ne télécharge jamais d'Internet.
- **Faisabilité mesurée dans l'environnement cloud** (§ 8) : texte, figures, animations vectorielles et données structurées : oui. Audio : synthèse **espeak-ng** (robotique, marquée « synthétique ») ; voix naturelles et vidéo avec personnes : **non** ici, il faudra des enregistrements humains ou une autre chaîne.
- **Trois décisions prises par le propriétaire le 2026-10-01** (§ 11) : taille au choix de l'architecte (espace Langues du téléphone **2 Go réglable**), **maximum de licences libres** (CC BY-SA acceptée, contenu dérivé publié en CC BY-SA 4.0), **voix de synthèse d'excellente qualité** (neuronales libres, produites hors du cloud ; chaîne prête dans `tools/langues/tts_synth.py`).

## 1. Cadre pédagogique

### 1.1 Les huit niveaux

| Niveau | Correspondance CECRL | Idée |
|---|---|---|
| **A0** découverte | avant A1 (hors CECRL) | sons, écriture, 80 à 150 mots, salutations ; on reconnaît et on imite, on ne produit pas encore librement |
| **A1** | A1 | se débrouiller avec des phrases très simples sur soi et l'immédiat |
| **A2** | A2 | situations courantes, échanges simples et directs |
| **B1** | B1 | seuil d'indépendance : voyage, travail, opinions, récits |
| **B2** | B2 | argumenter, comprendre l'essentiel de textes complexes, spontanéité |
| **C1** | C1 | usage souple et efficace, textes longs et exigeants, implicite |
| **C2** | C2 | maîtrise : tout comprendre sans effort, nuancer finement |
| **natif** | au-delà du C2 | richesse idiomatique, **registres** (soutenu, courant, familier), **argot**, **dialectes et variétés**, **littérature**, humour, culture partagée ; ce que sait un locuteur éduqué de la langue |

Le « natif » n'est pas un niveau d'examen : c'est un **niveau de richesse** (voir § 1.3). Les descripteurs ci-dessous sont **originaux** (écrits pour CastBridge à partir de la logique générale du CECRL ; ils ne recopient aucun texte officiel ni manuel). Les ordres de grandeur de vocabulaire sont des **repères de conception**, à ajuster par langue (tableau 1.4).

### 1.2 Critères observables par niveau et compétence

Chaque cellule est ce qu'un évaluateur **voit** l'apprenant faire (tâche, durée, nombre de mots, taux d'aide). Elles deviennent les `objectives` des unités et les cibles des tests de positionnement.

| Niveau | Compréhension orale | Compréhension écrite | Expression orale | Expression écrite |
|---|---|---|---|---|
| **A0** | repère 10 mots isolés prononcés lentement (nombres, salutations) et les associe à une image ; distingue les sons nouveaux de la langue (tons, voyelles longues, etc.) | reconnaît l'écriture (alphabet / kana / 20 caractères) et lit 20 mots courts avec aide (lecture phonétique) | répète des mots et des formules (≥ 8 sur 10 compris par un auditeur bienveillant) ; dit son prénom | recopie / écrit des lettres, 20 mots, son prénom |
| **A1** | comprend 8 questions sur 10 sur soi (nom, âge, famille) dites lentement, avec répétition | lit un message de 30 à 50 mots (carte, menu, étiquette) et répond à 4 questions factuelles sur 5 | se présente et pose 5 questions simples ; phrases de 3 à 6 mots ; pauses fréquentes | écrit 5 phrases sur soi, un formulaire, un SMS de 20 mots |
| **A2** | suit un dialogue de 60 à 90 s sur un sujet courant (courses, horaires), débit lent à normal, 2 écoutes | lit un texte de 100 à 150 mots (annonce, courriel court) : idée générale et 3 détails sur 4 | tient un échange de 2 min sur un sujet familier ; décrit sa journée en 6 à 8 phrases reliées par « et / mais / parce que » | écrit un message de 50 à 80 mots (invitation, remerciement) avec ≤ 1 erreur gênante sur 3 phrases |
| **B1** | comprend l'essentiel d'un reportage de 2 à 3 min sur un sujet connu ; suit une conversation à débit normal | lit un article de 250 à 350 mots : idée principale, 4 détails sur 5, déduit un mot inconnu du contexte | raconte un événement, donne et justifie une opinion pendant 2 à 3 min ; se débrouille en voyage | rédige une lettre ou un récit de 120 à 180 mots, cohérent, avec connecteurs |
| **B2** | comprend un exposé de 5 min, un film en version originale (l'essentiel) ; repère l'attitude du locuteur | lit un texte argumentatif de 500 à 700 mots, distingue fait / opinion ; lit un roman simplifié sans dictionnaire permanent | discute spontanément avec un locuteur natif sans le fatiguer ; argumente 3 à 4 min avec exemples | rédige un essai ou un rapport de 200 à 300 mots, structuré, avec nuances |
| **C1** | suit un long discours, des émissions rapides, accents variés ; saisit l'implicite et l'ironie | lit des textes longs, littéraires ou spécialisés (800 à 1 200 mots) ; perçoit le style | s'exprime couramment, sans chercher ses mots ; adapte le registre ; présentation structurée de 5 à 8 min | rédige des textes clairs, détaillés, bien organisés (350 à 500 mots) ; résumés et comptes rendus précis |
| **C2** | comprend tout ce qui est dit, y compris à débit rapide, bruit, plusieurs accents, jeux de mots | lit tout texte, y compris abstrait, archaïque ou très stylisé (poésie, juridique, presse d'opinion) | s'exprime avec précision et nuance fines, reformule sans hésiter, négocie, plaisante | écrit des textes d'un style approprié, élégants et sans faute notable (≥ 500 mots) ; synthèse de sources multiples |
| **natif** | comprend l'argot, les dialectes et variétés régionales, les chansons, le théâtre, l'humour implicite, les références culturelles partagées | lit la littérature classique et contemporaine, la langue ancienne ou régionale, les textes à double sens | change de registre à volonté (soutenu, courant, familier, argotique) ; emploie idiomes et proverbes à bon escient ; raconte une anecdote avec le rythme d'un locuteur natif | écrit dans plusieurs registres et genres (lettre formelle, billet littéraire, message familier) ; pastiche un style ; emploie les expressions justes |

### 1.3 Le niveau « natif » : contenus et critères

Cinq piliers, chacun avec un critère d'évaluation propre (aucun n'est un examen officiel) :

1. **Idiomes et expressions** : 300 à 500 expressions par langue, comprises **et** employées dans le bon contexte (test : choisir l'expression qui convient, la remplacer par un équivalent neutre).
2. **Registres** : reconnaître et produire le même message en soutenu / courant / familier ; savoir quand c'est déplacé.
3. **Argot, variétés et dialectes** : les principales variétés (cf. § 2 : français de France / d'Afrique / du Québec ; anglais britannique / américain / camerounais et ouest-africain ; espagnol d'Espagne / d'Amérique ; allemand / suisse / autrichien ; chinois standard et accents régionaux ; japonais de Tokyo / du Kansai ; italien standard / régionalismes). **Compréhension d'abord** ; la production n'est exigée que dans le standard.
4. **Littérature et culture** : extraits du **domaine public** (voir § 5) accompagnés d'un travail de lecture ; références culturelles (proverbes, histoire, fêtes, humour) ; jamais d'extrait sous droits d'auteur au-delà du droit de citation, et par défaut aucun.
5. **Pragmatique** : implicite, politesse, ironie, sous-entendus, humour : l'apprenant explique **pourquoi** une phrase est drôle, impolie ou ambiguë.

### 1.4 Repères de vocabulaire et d'écriture par niveau

| Niveau | Vocabulaire actif (repère) | Chinois (mots / caractères) | Japonais (kanji) |
|---|---|---|---|
| A0 | 80 à 150 | 30 mots, 15 caractères | hiragana, katakana, 10 kanji |
| A1 | ≈ 500 | ≈ 150 mots (HSK 1) | ≈ 80 kanji (N5) |
| A2 | ≈ 1 000 | ≈ 300 mots (HSK 2) | ≈ 300 kanji (N4) |
| B1 | ≈ 2 000 | ≈ 600 mots (HSK 3) | ≈ 650 kanji (N3) |
| B2 | ≈ 4 000 | ≈ 1 200 à 2 500 mots (HSK 4-5) | ≈ 1 000 kanji (N2) |
| C1 | ≈ 8 000 | ≈ 5 000 mots (HSK 6) | ≈ 2 000 kanji (N1) |
| C2 | ≈ 16 000 | ≈ 8 000 mots et plus | ≈ 2 100 kanji d'usage courant, lecture libre |
| natif | 30 000 passifs, variété de registres | + chengyu, argot, littérature | + registres, dialectes, littérature |

### 1.5 Correspondance avec les examens de référence (information, pas préparation d'annales)

Les correspondances ci-dessous sont des **équivalences usuelles approximatives** (les organismes publient les leurs et les révisent) ; elles servent à **orienter** l'apprenant, pas à promettre une note. CastBridge propose des exercices **originaux « dans le style de »** ces épreuves (compréhension, expression, grammaire) : aucune annale ni sujet officiel n'est reproduit, aucun logo d'organisme n'est utilisé, les noms d'examens ne servent qu'à désigner l'objectif.

| Niveau | Français (DELF/DALF, TCF/TEF) | Anglais (Cambridge · IELTS · TOEFL iBT) | Allemand (Goethe) | Espagnol (DELE) | Italien (CILS · CELI) | Chinois (HSK 1–6) | Japonais (JLPT) |
|---|---|---|---|---|---|---|---|
| A0 | — | — | — | — | — | — | — |
| A1 | DELF A1 | — (Pre-A1/A1 Starters-Movers) | Start Deutsch 1 / Goethe A1 | DELE A1 | CILS A1 · CELI impatto | HSK 1 | JLPT N5 |
| A2 | DELF A2 | A2 Key · IELTS ≈ 3-4 | Goethe A2 | DELE A2 | CILS A2 · CELI 1 | HSK 2 | JLPT N4 |
| B1 | DELF B1 | B1 Preliminary · IELTS ≈ 4-5 · TOEFL ≈ 42-71 | Goethe B1 | DELE B1 | CILS B1 · CELI 2 | HSK 3 | JLPT N3 |
| B2 | DELF B2 | B2 First · IELTS ≈ 5,5-6,5 · TOEFL ≈ 72-94 | Goethe B2 | DELE B2 | CILS B2 · CELI 3 | HSK 4 (et début HSK 5) | JLPT N2 |
| C1 | DALF C1 | C1 Advanced · IELTS ≈ 7-8 · TOEFL ≈ 95-113 | Goethe C1 | DELE C1 | CILS C1 · CELI 4 | HSK 5 | JLPT N1 |
| C2 | DALF C2 | C2 Proficiency · IELTS ≈ 8,5-9 · TOEFL ≈ 114-120 | Goethe C2 (GDS) | DELE C2 | CILS C2 · CELI 5 | HSK 6 (≈ C1 haut) | — (N1 n'atteint pas le C2) |
| natif | — | — | — | — | — | — | — |

Remarques : HSK 6 et JLPT N1 se situent plutôt dans la zone C1 que C2 ; il n'existe pas d'examen du niveau « natif », il est évalué par nos propres tâches (§ 1.3). Les barèmes et numéros d'examens changent (nouveau HSK à 9 niveaux, par exemple) : **à confronter aux documents officiels** par les relecteurs.

### 1.6 Test de positionnement, par langue

Un test **par langue cible et par langue de départ** (consignes dans la langue de départ jusqu'à B1, en cible ensuite), 100 % hors ligne, sur TV et téléphone :

- **Escalier adaptatif par compétence** (code : `Placement`) : on commence à A1 ; **3 items par niveau et par compétence** ; le niveau est atteint avec ≥ 2 bonnes réponses sur 3 ; au premier échec, la compétence s'arrête ; on monte jusqu'au natif. Un apprenant qui échoue A1 est placé en **A0** (ce que le test lui propose alors est une découverte guidée, pas un échec).
- **Résultat** : un niveau **par compétence** (profil), plus un niveau global = **moyenne arrondie vers le bas** (une compétence faible ne cache pas les autres, mais le global reste prudent). L'apprenant peut **choisir un autre point de départ** que celui du test.
- **Items** : tirés d'une banque de **12 à 15 items par niveau et par compétence** (donc ≈ 400 par langue pour 8 niveaux × 4 compétences × 12 ; ≈ 40 Ko de texte), marqués `placement: true`, **jamais** réutilisés comme exercices du même lot. L'oral et l'écrit libre sont **auto-évalués** avec modèle (pas de reconnaissance vocale hors ligne garantie) ; l'écoute n'est proposée que si un lot média est installé (sinon la compétence « orale » est positionnée par la lecture seule, et le résultat le dit).
- **Durée** : 12 à 20 minutes. **Reprise** : le test peut être interrompu, repris, refait tous les 3 mois.
- **Lien avec le graphe** (§ 3.6) : le résultat marque comme **maîtrisés** les nœuds en dessous du niveau atteint ; le reste devient le parcours proposé.

## 2. Spécificités par langue

### 2.1 Écritures, lecture et rendu

| Langue | Écriture et lecture | Ce que le contenu doit porter | Difficultés pour un francophone / anglophone |
|---|---|---|---|
| **Chinois** | caractères simplifiés (traditionnels : option plus tard) ; **pinyin** avec tons ; **ordre des traits** | chaque mot : `term` (caractères), `reading` (pinyin avec tons en diacritiques), `gloss` ; animation des traits (§ 3.5) ; classificateurs ; pas de conjugaison, ordre des mots, particules (了, 过, 的, 吗) | **4 tons + neutre** (la hauteur change le sens), sons absents (zh, ch, sh, x, q, ü), caractères nombreux, homophones |
| **Japonais** | **hiragana, katakana, kanji** ; **furigana** au-dessus des kanji ; rōmaji en aide seulement | `reading` en kana pour chaque mot à kanji ; **trois systèmes d'écriture introduits dans l'ordre** (hiragana A0, katakana A0-A1, kanji dès A1) ; politesse (です/ます, forme neutre, keigo) comme **axe de contenu** | longueur des voyelles, particules (は, が, を, に), ordre sujet-objet-verbe, **politesse** et honorifiques, lectures multiples des kanji |
| **Allemand** | alphabet latin + ä ö ü ß ; **majuscules des noms** | genres (der/die/das) **appris avec le nom** (jamais un nom sans son article), **4 cas**, déclinaison, ordre des mots (verbe en 2e place, fin de subordonnée), mots composés | genres arbitraires, cas, séparabilité des verbes, prononciation de ch, r, ü |
| **Français** (cible) | alphabet latin + accents (é è ê à ç…) ; liaison, e muet | accords (genre, nombre), conjugaison, liaisons, homophones ; **variétés** francophones (France, Afrique, Québec, Belgique) | nasales, r, orthographe non phonétique, accords |
| **Anglais** | alphabet latin ; orthographe irrégulière | **IPA** pour la prononciation ; phrasal verbs, temps ; variétés (britannique, américain, **anglais camerounais / pidgin en compréhension**) | orthographe ≠ prononciation, th, voyelles courtes/longues, accent de mot |
| **Italien** | alphabet latin ; doubles consonnes | gn, gl, sc ; articles et prépositions articulées, conjugaisons ; « tu / Lei » | doubles consonnes, accent tonique, subjonctif |
| **Espagnol** | alphabet latin + ñ, ¿ ¡ ; accents écrits | ser / estar, subjonctif, ñ, rr, j ; variétés d'Espagne et d'Amérique (vosotros / ustedes) | rr roulé, subjonctif, verbes irréguliers |

### 2.2 Phonétique et prononciation

- Chaque unité d'A0 à A2 porte des **paires minimales** (ma / mǎ, 橋 / 箸, ship / sheep, Bahn / Bann, bue / boue, palla / pala, perro / pero) avec **audio** (lot média) et, **sans audio**, une description écrite (position de la langue, durée, ton) pour que le texte reste utilisable seul.
- **Alphabet phonétique** : IPA affiché (police à vérifier sur la TV), pinyin, kana. Un tableau « sons de la langue » par langue et par langue de départ (les sons qui n'existent pas dans la langue de l'apprenant sont mis en avant).
- **Animations d'articulation** (vectorielles, § 3.5) : coupe de la bouche (langue, lèvres, voile du palais) pour les sons difficiles ; courbes de ton du chinois.
- **Production orale** : sans reconnaissance vocale garantie hors ligne, le modèle est **écoute – répétition – auto-comparaison** : l'apprenant écoute le modèle, se réécoute (enregistrement local sur le téléphone, jamais envoyé), puis s'auto-évalue contre une grille.

### 2.3 Langue de départ et paires de langues

- Le **lot de texte** est lié à la paire (cible, départ) : glossaires, traductions, consignes, explications grammaticales dans la langue de départ. `zh-a1-salut-fr` et `zh-a1-salut-en` partagent le même contenu cible et diffèrent par la couche de départ.
- Le **lot média** est **partagé** : l'audio d'un dialogue chinois est le même pour un francophone et un anglophone (`zh-a1-salut`, sans suffixe de départ ; testé). Doubler le texte coûte quelques centaines de Ko, doubler l'audio coûterait des centaines de Mo : c'est la raison du découpage.
- **Paires** : zh, ja, de, it, es × {fr, en} = 10 ; en ← fr ; fr ← en ; soit **12 paires**. D'autres langues de départ (langues camerounaises, par exemple) pourront s'ajouter sans changer le format (`source` est un code de langue ; `Lang.sources` est la liste d'ouverture).
- **Interface** : l'application reste en français ou en anglais selon le réglage ; les noms des langues cibles ne sont jamais traduits que par leur nom d'origine à côté.

### 2.4 Polices et rendu sur la TV (32 bits)

- **Chinois et japonais** : on s'appuie sur la **police CJK du système Android** (`Noto Sans CJK` / `Droid Sans Fallback`) : zéro octet ajouté. **Non vérifiable dans le cloud** (aucune police CJK dans l'environnement, aucune TV) : test obligatoire sur la TV réelle avec une page d'essai (`你好 · こんにチは · 漢字`). Si la TV n'a pas la police : repli **par sous-ensemble** d'une police libre (Noto Sans CJK, licence OFL, redistribuable) limité aux caractères du contenu (≈ 3 000 caractères ≈ 1,5 à 2,5 Mo en WOFF2/TTF compressé) fourni **dans un lot média** (pas dans les 10 Mo de la TV) — ou, mieux, les **traits vectoriels** (§ 3.5) pour les caractères essentiels de A0 à A2.
- **Latin accentué, IPA, kana** : couverts par la police système ; l'IPA et certains diacritiques du pinyin (ǎ ǒ ǚ) sont à tester.
- **Taille** : texte de 26 à 30 px sur la grille 1280 × 720 (règle d'Apprendre) ; les caractères CJK et les kanji avec furigana demandent **+20 %** de hauteur de ligne et un corps ≥ 32 px pour le mot étudié ; furigana et pinyin à 60 % du corps, jamais sous 18 px.
- **Mise en page** : `LinearLayout` uniquement (règle d'Apprendre) ; texte vertical japonais **non géré** (horizontal seulement) ; pas de césure automatique en CJK (coupure possible entre deux caractères, sauf avant ponctuation fermante ; règle de kinsoku minimale).
- **Mémoire** : ≈ 1 Go de RAM : pas de chargement d'une police entière ; une seule Typeface partagée.

## 3. Modèle de contenu

### 3.1 Principe : un nouveau type de pack, rétrocompatible

Un **pack de langue** est un format **à part** (`"type": "langue"`, `format: 1`), qui vit sous `content/langues/<id>/` et **non** sous `content/learn/` : les lecteurs actuels d'Apprendre (`LessonJson`) ne le voient jamais, donc **rien ne casse**. Il réutilise : le format des **figures** et des **animations** d'Apprendre (même bloc `illustration` + `animation`, § 3.5), le **cadre de lots** (`LotId`), les **états de validation** (`state: review | validated | needs-fix | rejected`), le **marquage de l'exercice** (même vocabulaire que le quiz : choix, index, explication). Les clients qui ne connaissent pas la catégorie l'ignorent (le catalogue de lots est filtré par `feature`).

Fichiers d'un pack : `langue.json` (en-tête + unités), `media.json` (manifeste des médias référencés, § 5). Id de pack = scope du lot : `<cible>-<niveau>-<thème>-<départ>` (ex. `zh-a1-salut-fr`, thème : 1 à 16 caractères `a-z0-9`, sans tiret ; 4 segments ≤ 32 caractères, vérifié par les tests pour toutes les combinaisons).

### 3.2 Unités et leurs éléments

```json
{"format":1,"type":"langue","id":"zh-a0-salut-fr","version":1,"target":"zh","source":"fr","level":"A0","theme":"salut",
 "title":"…","state":"review",
 "units":[{"id":"zh-a0-salut-fr-u1","title":"…","minutes":10,"skills":["co","ce","po"],"prerequisites":[],
   "vocab":[…], "dialogues":[…], "grammar":[…], "exercises":[…], "stories":[…], "cards":[…], "animations":[…]}]}
```

| Élément | Champs | Remarque |
|---|---|---|
| **Vocabulaire** | `id`, `term` (écriture native), `reading` (pinyin / kana / IPA ; **obligatoire** pour le chinois et le japonais, validé), `gloss` (dans la langue de départ), `pos`, `audio` (`m:<id>`), `image` | une **carte** de répétition espacée par mot ; le mot est appris avec son article / classificateur |
| **Dialogue** | `id`, `title`, `lines[]` (`who`, `text`, `reading`, **`tr`** traduction obligatoire, `audio`), `audio` d'ensemble, `video` | **transcription et traduction** toujours présentes (le texte seul suffit sans média) |
| **Grammaire** | `id`, `title`, `md` (Markdown restreint d'Apprendre), `examples[]` (`text`, `tr`) | une règle par fiche, exemples d'abord, règle ensuite |
| **Exercices** | `id`, `kind`, `prompt`, `audio`, `answers`, `choices`/`correct`, `pairs`, `words`, `model`, `skill` | 10 genres (ci-dessous) |
| **Mini-histoires** | `id`, `title`, `paragraphs[]` (comme les répliques) | 40 à 200 mots selon le niveau, lecture + questions |
| **Cartes illustrées** | `id`, `vocab`, `image` | image = **figure vectorielle** (TV) ou WebP (lot média) |
| **Animations** | `id`, `kind` (`strokes`, `articulation`, `gesture`), `label` | contenu de l'animation = bloc `illustration` d'Apprendre (§ 3.5) |

Les **dix genres d'exercices** (`LangExerciseKind`) : `dictation` (écoute-dictée), `match` (appariement), `order` (ordre des mots), `mcq`, `cloze` (texte à trous), `translate`, `truefalse`, `speak` (production orale, auto-évaluée avec modèle), `write` (production écrite, modèle + grille), `strokes` (ordre des traits). La **correction automatique** (`LangMarking`) normalise : NFKC (pleine chasse → ASCII), casse, ponctuation latine et CJK, espaces ; **accents tolérés par défaut** (option « stricte ») ; en CJK les espaces sont ignorés ; les **tons du pinyin comptent** (taper « ma » pour « mǎ » est « presque » : `sameIgnoringTones`, crédit partiel décidé par l'écran).

### 3.3 Répétition espacée (vocabulaire)

Boîtes de Leitner (`Srs`) : intervalles **0, 1, 3, 7, 16, 35, 90 jours** ; note 0 (raté) = retour à la boîte 0, 1 (difficile) = reste, 2 (facile) = monte d'une boîte ; **20 cartes** par séance maximum, les plus basses boîtes d'abord. Le jour est un entier fourni par l'appelant (aucune horloge dans le cœur). L'état est local, sauvegardé avec la progression d'Apprendre, fusionné par `LearnMerge` entre appareils.

### 3.4 Validation d'un pack (`LangValidator`)

Ids uniques préfixés par l'id du pack ; unités et prérequis existants, sans cycle ; **lecture obligatoire** (pinyin / kana) pour le chinois et le japonais ; dialogues ≥ 2 répliques ; QCM 2-6 choix distincts avec index valide ; appariement 2-6 paires sans doublon ; ordre des mots ≥ 3 morceaux et phrase juste ; dictée = audio + réponse ; production = modèle ; toute référence `m:<id>` existe dans `media.json` ; chaque média : licence redistribuable, auteur/source/URL pour CC BY et CC BY-SA, **moteur de synthèse ⇒ `synthetic: true`**, taille > 0, `kind` connu. Les textes d'un bloc restent ≤ 650 caractères (page TV, règle d'Apprendre ; à ajouter au validateur quand la production démarre).

### 3.5 Courtes animations vectorielles

Réutilisation **telle quelle** du moteur d'animation d'Apprendre (`AnimatedFigure`, `docs/LEARN.md` § Animations) : images-clés JSON, ≤ 40 Ko, ≤ 3 flashs/s, repli statique obligatoire, « réduire les animations », `alt` obligatoire. Trois familles de modèles (`tools/anim` à étendre) :

- **Traits des caractères / kana / kanji** : chaque trait est un chemin SVG `draw` tracé progressivement, dans l'ordre ; mode `steps` (OK = trait suivant) ; ≈ 1 à 3 Ko par caractère. Sources de traits : à tracer par nos agents à partir de règles, ou données **libres** (KanjiVG, CC BY-SA 3.0 ; Make Me a Hanzi, licence Arphic : **à faire valider juridiquement avant tout usage**, voir § 11). Budget : 150 caractères A1 ≈ 300 Ko.
- **Articulation** : coupe de profil de la bouche, la langue se déplace (`move`/`rotate`) ; une par son difficile ; ≈ 3 Ko.
- **Gestes / culture** : salut (inclinaison au Japon), comptage des nombres chinois à la main, etc. ; vectoriel, sans personne réelle.

### 3.6 Graphe de compétences par langue

`content/graph/langue-<code>.json` — **même enveloppe** que les graphes de domaine de l'architecture du contenu (`format`, `domain: "langue-zh"`, `prefix`, `skills[]` avec `id` du type `zh.a1-co`, `title {fr,en}`, `prereq`, `minutes`, `tags`, `lots`) et deux champs **additifs** : `cefr` (a0…natif) et `skill` (co / ce / po / pe ou `null` pour un nœud propre à la langue). Le champ `level` (N0-N4) est un **pont approximatif** (A0→N0 ; A1-B1→N1 ; B2→N2 ; C1→N3 ; C2-natif→N4) uniquement pour trier dans les outils communs ; l'échelle d'une langue reste le CECRL.

Les 7 graphes sont **générés** (`tools/langues/gen-graph.py`, déterministe) : **44 nœuds** pour le chinois (4 compétences × 8 niveaux + 12 nœuds propres : pinyin, tons, traits, 150 / 300 / 600 / 1 200 caractères, classificateurs, aspect, constructions 把/被, chengyu, variétés), 44 pour le japonais (hiragana, katakana, kanji par paliers JLPT, politesse, keigo, registres), 39 à 41 pour les langues latines (41 pour l’allemand : genres et cas). Le code (`LangSkillGraph`) vérifie : identifiants uniques, prérequis existants, **aucun prérequis d'un niveau supérieur**, **aucun cycle**, et que tous les nœuds sont atteignables en maîtrisant progressivement (testé sur les 7 graphes).

**Relation avec les lots** : un nœud porte `lots: []` pour l'instant ; la vague 1 y mettra les scopes de lots (`zh-a1-salut-fr`…). L'intégration au validateur de graphe de `claude/net-architect` demande d'ajouter à `content/graph/scopes.json` des scopes de type `langue` (feature `langues`) et `langue-media` (feature `langues-media`, **non compatible TV**) : fragment prêt dans § 4.5.

## 4. Architecture des lots et budget de 6 Go

### 4.1 Découpage

Un lot de langue = (**cible**, **niveau**, **thème**) :

| | Lot texte | Lot média |
|---|---|---|
| Feature | `langues` | `langues-media` |
| Scope | `<cible>-<niveau>-<thème>-<départ>` (ex. `zh-a1-salut-fr`) | `<cible>-<niveau>-<thème>` (ex. `zh-a1-salut`) ; **partagé** par les deux langues de départ |
| Contenu | `langue.json`, `media.json`, figures et animations vectorielles, graphes d'unité | audio Opus, courtes vidéos, images WebP, polices de repli |
| Plafond | **≤ 3 Mo** (TV compatible) | **≤ 100 Mo** (téléphone) |
| Où | serveur → téléphone → TV (10 Mo) | serveur → téléphone → **copié vers la TV sur demande** (volume de la bibliothèque, USB d'abord) |
| Requis ? | oui (autonome : le texte suffit à apprendre) | **jamais** : le texte reste utilisable sans lui ; sa présence ajoute l'audio et la vidéo |

Un **thème** regroupe 8 à 15 unités ≈ 3 à 6 heures de travail (salutations, nombres, famille, nourriture, voyage, travail, santé, actualité, littérature…). Estimation : ≈ 10 thèmes par niveau et par langue ⇒ ≈ 560 lots texte, ≈ 560 lots média. Un lot média plus grand que 100 Mo est scindé en `-a`, `-b` (lettre, 1 caractère, dans le thème : `salut1`, `salut2`).

### 4.2 Répartition du budget

Source unique : `content/langues/budget.json` (lue par le code Kotlin **et** par l'outil Python ; un test vérifie la cohérence). **1 Go = 1 024 Mo**, soit **6 144 Mo**. Poids par langue : chinois et japonais **17 %** chacun (écriture, traits, tons/kana/kanji, polices de repli, plus d'animations et de vidéo de geste), anglais 13 % (demande mondiale, variétés, beaucoup d'examens), allemand 12 %, espagnol et français 11 %, italien 10 %, **transversal 3 %** (sons communs, IPA, interface audio, polices), **réserve 6 %** (corrections, mises à jour, nouvelles langues).

Répartition par niveau : profil « standard » A0 3 %, A1 9 %, A2 13 %, B1 17 %, B2 19 %, C1 17 %, C2 12 %, natif 10 % ; profil « CJK » (chinois, japonais) A0 6 %, A1 12 %, A2 14 %, B1 16 %, B2 17 %, C1 14 %, C2 11 %, natif 10 % (l'écriture alourdit le début). Les niveaux avancés pèsent plus : textes longs, audio plus long, vidéos d'actualité et de littérature.

Résultat (`tools/content-budget/langues_budget.py --plan --md`), en **Mo** :

| Langue | Part | A0 | A1 | A2 | B1 | B2 | C1 | C2 | NATIF | Total Mo |
|---|---|---|---|---|---|---|---|---|---|---|
| Chinois | 17 % | 63 | 125 | 146 | 167 | 178 | 146 | 115 | 104 | 1 044 |
| Japonais | 17 % | 63 | 125 | 146 | 167 | 178 | 146 | 115 | 104 | 1 044 |
| Anglais | 13 % | 24 | 72 | 104 | 136 | 152 | 136 | 96 | 80 | 799 |
| Allemand | 12 % | 22 | 66 | 96 | 125 | 140 | 125 | 88 | 74 | 737 |
| Espagnol | 11 % | 20 | 61 | 88 | 115 | 128 | 115 | 81 | 68 | 676 |
| Italien | 10 % | 18 | 55 | 80 | 104 | 117 | 104 | 74 | 61 | 614 |
| Français | 11 % | 20 | 61 | 88 | 115 | 128 | 115 | 81 | 68 | 676 |
| Transversal | 3 % | | | | | | | | | 184 |
| Réserve | 6 % | | | | | | | | | 369 |
| **Total** | 100 % | | | | | | | | | **6 144** |

Pour chaque case (langue, niveau) : **texte** estimé à 2 Mo (3 Mo pour le chinois et le japonais, soit **128 Mo de texte en tout, ≈ 2 %** du budget : le texte ne pèse presque rien) ; **média** = le reste, réparti en **audio 55 %**, **vidéo 25 %**, **images 12 %**, **autres 8 %** (polices de repli, animations lourdes, données). Exemple chinois B1 (167 Mo) : texte 3 Mo, média 164 Mo ≈ 90 Mo d'audio (≈ 8 h), 41 Mo de vidéo (≈ 13 min), 20 Mo d'images (≈ 800 images), 13 Mo d'autre ; ce qui fait **2 lots média** de ≤ 100 Mo. Au total ≥ 86 lots média pour 56 cases ; un thème par lot en donnera davantage et plus petits (≈ 560).

**Pourquoi cette taille est réaliste.** À 24 kbit/s en Opus mono (≈ 3 Ko/s, § 4.3), **1 heure d'audio ≈ 10,5 Mo** : les 55 % d'audio de l'enveloppe (≈ 3 000 Mo) représentent plus de 280 h, bien au-delà de ce que nous pourrons enregistrer ; **le plafond ne sera pas le facteur limitant, la production le sera** (§ 8). On peut donc réaffecter la marge audio à la vidéo si les enregistrements humains arrivent.

### 4.3 Politique d'encodage

| Média | Format | Réglage | Taille type |
|---|---|---|---|
| Audio de voix (mots, dialogues, histoires) | **Opus** (`libopus`, mono, 16 kHz) | **24 kbit/s** (voix), 16 kbit/s pour les mots isolés ; AAC-LC 32 kbit/s en secours si une TV ne lit pas Opus (lecteur libVLC existant : Opus pris en charge) | ≈ 3 Ko/s ; 30 s ≈ 90 Ko |
| Vidéo courte | **H.264** (`libx264`), **480p**, 25 i/s, profil *main*, CRF 28, audio Opus/AAC 32 kbit/s | ≤ 30 s, ≤ 15 Mo par clip (politique médias de `net-architect`) ; ≈ 400 kbit/s ≈ 3 Mo/min | 30 s ≈ 1,5 Mo |
| Images (cartes, photos libres, scènes) | **WebP** qualité 75 | ≤ 120 Ko, ≤ 1 280 px (politique médias) ; **les figures vectorielles restent préférées** | 25 à 80 Ko |
| Polices de repli | OFL, sous-ensemble | seulement si la TV n'a pas la police CJK | 1,5 à 2,5 Mo |

Vérifié dans le cloud : `ffmpeg` dispose de `libopus`, `libx264`, `libwebp`, `aac` ; un mot synthétisé de 1 s passe de 28 Ko (WAV) à **1,2 Ko** en Opus 16 kbit/s.

### 4.4 Manifeste des médias et des licences

`media.json` par pack, un objet par fichier : `id`, `file`, `kind` (`audio` | `video` | `image`), `bytes`, `durationMs`, `license`, `author`, `source`, `url`, `engine`, `synthetic`, `lang`. Les règles (validées par `LangValidator`) :

- licence ∈ { `CC0`, `PD` (domaine public), `CC-BY-4.0`, `CC-BY-SA-4.0`, `CASTBRIDGE-ORIGINAL` } ; **jamais** NC ni ND ;
- CC BY et CC BY-SA : `author`, `source`, `url` **obligatoires** (attribution écrite dans un écran « Crédits » généré à partir de ce manifeste) ;
- tout audio produit par un moteur de synthèse porte `engine` **et** `synthetic: true` ; l'interface affiche « voix de synthèse » sur ces pistes ;
- un média non listé dans `media.json`, ou listé et absent du lot média, est une erreur de construction ;
- le manifeste global du dépôt (`content/MEDIA-MANIFEST.json` de `net-architect`) reprendra ces entrées en y ajoutant `sha256`, `lot`, `alt`, `caption`, `transcript` (obligatoire pour l'audio : c'est ici la transcription des dialogues).

### 4.5 Vérification du budget

`tools/content-budget/langues_budget.py` (dossier `tools/content-budget/` choisi pour s'ajouter à `content_budget.py` de `net-architect` sans conflit de fichier) :

- `--plan [--md]` imprime le tableau chiffré ci-dessus ;
- `--check <dossier>` mesure les fichiers `castbridge-lot-langues[-media]-<scope>-v<n>.lot` : échec si un lot texte > 3 Mo, un lot média > 100 Mo, une langue > sa part, le total > 6 Go ;
- le même calcul existe en Kotlin (`LangBudget.check`) avec les mêmes poids ; un test le confirme (6 144 Mo, 56 cases, somme exacte à l'arrondi près).

**Extension à faire dans `content_budget.py` (net-architect)** : pour tout lot `feature in {langues, langues-media}`, **ne pas** l'ajouter au plafond de 3 Go du reste de la base ni à un « parcours de 100 Mo » (les lots média ne sont pas des parcours téléphone), appeler `langues_budget.py --check` et fusionner son code de retour ; plafond de 50 Mo par fichier à relever à 100 Mo pour `langues-media`. Fragment de `content/graph/scopes.json` à ajouter à l'intégration :

```json
{"id":"zh-a1-salut-fr","kind":"langue","feature":"langues","tv":true,"lang":"fr","levels":["N1"],"title":{"fr":"Chinois A1 — Se saluer (départ : français)","en":"Chinese A1 — Greetings (from French)"}}
{"id":"zh-a1-salut","kind":"langue-media","feature":"langues-media","tv":false,"lang":"zh","levels":["N1"],"title":{"fr":"Chinois A1 — Se saluer (audio, vidéo)","en":"Chinese A1 — Greetings (audio, video)"}}
```

### 4.6 Quota média : décision prise (le cadre des lots reste inchangé)

Aujourd'hui `LotBudget.PHONE_MAX_BYTES` = 100 Mo pour *tous* les lots du téléphone et le serveur refuse un lot de plus de 10 Mo. **Taille retenue (décision « à mon choix »)** :

- **Téléphone** : un **espace Langues** distinct, quota **2 Go par défaut**, réglable de 100 Mo à 6 Go (2 Go ≈ une langue complète jusqu'au B2, ou le début de plusieurs) ; éviction : jamais la langue active, sinon le lot média le moins récemment utilisé ; les 100 Mo des autres lots restent intacts.
- **Lot média** : **≤ 100 Mo** (inchangé), **lot texte ≤ 3 Mo** (inchangé).
- **Serveur** : limite propre à `langues-media` (≤ 100 Mo par lot), les autres lots restent à 10 Mo ; validateur `langues` (ZIP sain, `langue.json` valide, `LangValidator`).
- **TV** : médias copiés **dans le volume de la bibliothèque** (clé USB d'abord, règle de 1 Go libre), jamais dans les 10 Mo ; copie **par lot et sur demande** depuis le téléphone.

Rien de cela n'est implémenté (le cadre des lots est un autre chantier) ; `LangPlanner` calcule déjà avec un budget donné en paramètre, et `LangBudget.PHONE_LANG_DEFAULT_MB` (2 048) fixe le défaut.

## 5. Droits d'auteur et sources autorisées

**Règle : on ne redistribue que ce que nous avons le droit de redistribuer.** Rien n'est copié d'un manuel, d'une application ni d'un site sous droits.

| Source | Licence | Usage | Condition |
|---|---|---|---|
| Contenu créé par CastBridge (agents + relecteurs) | `CASTBRIDGE-ORIGINAL` (publié sous **CC BY-SA 4.0**, décision du § 11) | cœur du contenu : leçons, dialogues, exercices, figures, animations | original, pas de recopie d'un manuel |
| **Tatoeba** (phrases + traductions) | CC BY 2.0 FR (certaines CC0) | phrases d'exemple, traductions | attribution (`author` = contributeur, `url` de la phrase) |
| **Wiktionary / CC-CEDICT / JMdict / KANJIDIC** | CC BY-SA (3.0/4.0) | dictionnaires, lectures, traits | attribution + **partage dans les mêmes conditions** pour le dérivé |
| **Wikimedia Commons** (images, audio) | CC0, CC BY, CC BY-SA, domaine public | images, enregistrements | vérifier fichier par fichier ; exclure NC/ND et les fichiers « fair use » |
| **Common Voice** (Mozilla) | CC0 | voix humaines (phrases) | pour l'audio de phrases quand l'accès réseau existe |
| **Domaine public** (littérature : Hugo, Dickens, Goethe, Cervantes, Dante, Lu Xun/Natsume Sōseki selon pays…) | domaine public | extraits du niveau natif | vérifier la date de décès de l'auteur **et** de l'éditeur de la traduction |
| **Voix de synthèse libres** (Kokoro-82M Apache-2.0, MeloTTS MIT, Piper MIT + licence de chaque voix, espeak-ng GPL-3.0 en secours) | moteur **et voix** libres ; **la licence de la voix est inscrite** dans `media.json` (`voiceLicense`) et doit être libre | `synthetic: true` ; jamais une voix clonée d'une personne ; voix NC (p. ex. XTTS/CPML) exclues |
| **Données et polices libres** (MIT, Apache-2.0, BSD, OFL-1.1, CC BY 3.0/2.0, CC BY-SA 3.0) | libres | listes de fréquence, polices de repli, traits | attribution et partage selon la licence |

**Politique : le maximum de licences libres** (décision du propriétaire) : tout ce qui est redistribuable avec attribution et partage identique est accepté ; sont exclus seulement les licences NC / ND, « usage non commercial », « recherche seulement » et toute licence qui interdit la redistribution dans l'application.

Interdits : manuels et méthodes du commerce, annales officielles, vidéos YouTube, pistes sous licence NC, polices non libres. Chaque lot embarque un fichier de **crédits** produit depuis `media.json` ; chaque auteur est cité tel que sa licence l'exige.

## 6. Spécification d'intégration aux applications

(Le code `core` du squelette est testé ; l'écran Android restera simple et **sera compilé par le propriétaire**.)

### 6.1 Entrée et choix

- **Téléphone** : onglet **Apprendre** → tuile **Langues** (à côté des classes). **TV** : tuile **Apprendre** → **Langues**.
- **Premier écran** : « Quelle langue veux-tu apprendre ? » (7 choix, **sans drapeaux** : le nom dans la langue elle-même et dans la langue de l'interface, par exemple « Chinois — 中文 ») → « Je pars de… » (français / anglais) → « Faire le test de positionnement » ou « Commencer à zéro (A0) » ou « Choisir mon niveau ».
- **Plusieurs langues en parallèle** : jusqu'à 3 (profil d'élève de la TV) ; la dernière langue ouverte est reprise.
- Modèle : `LearnerLang(target, source, level)` ; la progression est locale, par profil, fusionnée par `LearnMerge` (même mécanisme que le reste d'Apprendre).

### 6.2 Parcours d'une unité

Écran **unité** : vocabulaire (carte → mot, lecture, audio si disponible) → dialogue (transcription, traduction masquable, audio) → grammaire → exercices → mini-histoire → **révision** (répétition espacée des mots du jour). Une page par bloc, textes ≤ 650 caractères, OK = suivant (règle d'Apprendre). Sans lot média : tout s'affiche, les boutons audio/vidéo sont **grisés avec la phrase** « Audio non installé — ouvre le téléphone pour l'envoyer à la TV ».

### 6.3 Audio et vidéo depuis un lot média déjà copié sur la TV

Pour lire `m:zh-nihao` : la TV cherche le lot média `zh-a1-salut` (le lot texte jumeau doit être installé : `playableMedia`) dans le volume de la bibliothèque, ouvre le fichier `audio/zh-nihao.opus`, le joue avec le lecteur existant (libVLC). Si le lot n'est pas là : message en français + chemin pour l'obtenir (« Envoyer depuis le téléphone »). Les lectures n'utilisent **jamais Internet**.

### 6.4 Hors ligne

Tout le code de `langues/` est pur (aucun `java.net`, aucun socket ; à vérifier par un test d'architecture comme pour `learn/`). Le téléphone télécharge (Wi-Fi seul, par défaut) puis livre ; la TV ne contacte jamais le serveur. Les deux appareils affichent la **fraîcheur** des données (« lot du 12 sept. »).

### 6.5 Télécommande

Flèches ◀ ▶ = page précédente / suivante (ou carte précédente / suivante) ; ▲ ▼ = choix dans une liste ; **OK** = valider / révéler la traduction / lancer l'audio ; **MENU** = menu de l'unité (rejouer l'audio, masquer la lecture, ralentir à 0,75×) ; **RETOUR** = revenir ; touche **JAUNE** = réduire les animations (règle d'Apprendre) ; touches chiffres = saisie des réponses numériques ; **▶/❚❚** = pause de l'audio. Pour la dictée : un **clavier à l'écran** adapté (pinyin avec choix de caractères, kana, accents : **à concevoir** ; en première version, la dictée devient un choix multiple sur TV et reste une saisie sur téléphone).

### 6.6 Squelette de code `core` (testé)

| Fichier | Contenu |
|---|---|
| `LangModel.kt` | `Lang` (7 cibles, `cjk`, langues de départ), `LangLevel` (A0…NATIF), `LangSkill` (co/ce/po/pe), `LangLots` (noms de scopes, analyse, jumeau média) |
| `LangPack.kt` | modèle de données, parseur `LangPackJson` (versionné, messages en français), `LangLicences`, `LangValidator` |
| `LangLogic.kt` | `LangMarking` (correction), `Srs` (répétition espacée), `Placement` (test), `LangSkillGraph` (graphe, validation, nœuds disponibles) |
| `LangBudget.kt` | `LangBudget` (6 Go, répartition, vérification de lots), `LangPlanner` (sélection des lots texte pour la TV et média pour le téléphone/la TV, lots média jouables) |

`gradle :core:test --tests 'castbridge.core.LanguesTest'` : 21 tests (scopes, parseur, validateur, licences, correction, SRS, positionnement, graphes, budget, sélection de lots). **Non vérifié ici** : `gradle` ne résout pas le plugin Kotlin dans le cloud (Maven : 429) ; les tests ont été exécutés avec le compilateur Kotlin livré avec Gradle et une petite doublure de `kotlin.test` (voir le rapport), **à relancer avec `gradle :core:test` sur le Mac**.

## 7. Plan de production par vagues

Les agents sont ceux du réseau de contenu (rôles : **pédagogue** = séquence, objectifs, exercices ; **linguiste** = exactitude, registre, variétés ; **illustrateur** = figures et animations ; **relecteur** = natif ou enseignant qui vérifie ; plus plus tard **vérificateur** automatique).

| Vague | Portée | Livrables | Agents | Taille estimée (texte TV) | Critères de qualité |
|---|---|---|---|---|---|
| **1** | **A0–A2 des 7 langues** (12 paires) : texte, figures, vocabulaire, dialogues, exercices, test de positionnement | ≈ 7 langues × 3 niveaux × ≈ 8 thèmes = 168 lots texte × 2 départs ≈ 340 lots ; 7 graphes à lots remplis | par langue : 1 pédagogue, 1 linguiste, 1 illustrateur (traits/kana pour zh, ja), 1 relecteur ; coordination par langue | ≈ 1 Mo par lot ⇒ ≈ 340 Mo de texte | voir § 7.1 ; tout `state: review` ; ≥ 80 % des items alignés (rubrique) ; aucun contenu copié |
| **2** | **B1–B2** | 7 × 2 × ≈ 10 thèmes × 2 départs ≈ 280 lots texte ; mini-histoires, grammaire avancée | idem + 1 linguiste « variétés » | ≈ 1,5 Mo par lot ⇒ ≈ 420 Mo | idem + textes authentiques **libres** (Tatoeba, Wikisource, domaine public) |
| **3** | **C1–C2 et natif** | idiomes, registres, argot, littérature du domaine public, culture, dialectes (compréhension) | idem + 1 spécialiste littérature / variétés par langue | ≈ 2 Mo par lot ⇒ ≈ 540 Mo | exigeant sur la fidélité au niveau : un natif de bon niveau doit trouver les items difficiles |
| **4** | **Médias** | audio d'abord (synthèse libre pour A0–B1 ; enregistrements humains pour la suite), animations d'articulation, images libres, courtes vidéos (selon faisabilité, § 8) | linguiste + voix + producteur audio ; chaîne d'encodage (`ffmpeg`) | jusqu'à 6 Go | voir § 7.2 |

Les vagues 1 à 3 s'enchaînent **par langue** (une langue peut être en vague 2 pendant qu'une autre est en vague 1) ; la vague 4 peut commencer **dès que les textes d'un niveau sont relus**, en commençant par A0-A1 (le plus utile : la prononciation).

### 7.1 Critères de qualité du texte (vagues 1 à 3)

- Chaque unité : objectifs observables (formulés comme au § 1.2), 8 à 15 mots, 1 dialogue de 6 à 12 répliques, 1 note de grammaire, 8 à 12 exercices répartis sur ≥ 3 genres et ≥ 2 compétences ; une mini-histoire à partir du A2.
- **Tout est dans la langue de départ jusqu'à B1** (consignes, explications), la langue cible domine ensuite.
- Lecture (pinyin / kana) et traduction présentes partout ; **aucune erreur de ton** en pinyin (relecture par un natif).
- **Culture** : contextes variés (Cameroun inclus quand c'est pertinent, par exemple l'anglais et le français camerounais), pas de stéréotype ; aucune personne réelle ; aucun contenu politique ou religieux partisan.
- **Accessibilité** : la couleur n'est jamais la seule information (tons : numéro + couleur + courbe) ; `alt` pour chaque figure ; pas de clignotement.
- Respect de la **rubrique pédagogique** de l'architecture du contenu (10 critères ; moyenne ≥ 3,0, aucun critère < 2,0 ; porte stricte d'accessibilité) dès qu'elle est fusionnée.

### 7.2 Critères de qualité des médias (vague 4)

- Chaque piste : transcription alignée au texte du lot ; débit de parole adapté au niveau (A1 lent, C1 naturel) ; bruit < −50 dBFS ; pas de coupure en milieu de mot ; **normalisation à −16 LUFS** ; un seul locuteur par piste de mots, 2 à 3 voix pour les dialogues.
- Voix synthétique (décision : **excellentes voix neuronales libres**, § 8) : tous niveaux, 1 voix de référence par langue et par genre (2 voix par langue pour les dialogues), débit réglé par niveau ; **relecture d'un natif obligatoire** pour les tons du chinois, la mélodie (accent de hauteur) du japonais, les noms propres et chiffres ; échantillon d'écoute validé avant la production de masse ; toujours marquée `synthetic`.
- Vidéos : ≤ 30 s, 480p, sous-titres (`caption`) dans la langue cible et la langue de départ ; gestes et situations ; **aucun visage identifiable sans autorisation écrite** (la politique médias interdit déjà les photos de personnes).

## 8. Ce qui est faisable ici et ce qui ne l'est pas

Mesuré dans l'environnement cloud de cette session (1er octobre 2026).

| Besoin | Faisable dans le cloud ? | Détail |
|---|---|---|
| Texte, dialogues, exercices, graphes, JSON | **oui** | c'est la vague 1 à 3 ; qualité à faire relire (natifs) |
| Figures et animations vectorielles (traits, articulation, gestes) | **oui** | moteur et modèles existants ; les **tracés de traits** exigent des données libres ou des tracés écrits à la main |
| Synthèse vocale libre | **partiellement** | `apt` installe `espeak-ng` ; testé : les 7 langues se synthétisent (cmn, ja, en, de, fr, it, es) ; la qualité est **robotique** (adéquate pour des mots isolés d'A0 ; **tons chinois approximatifs**, mélodie japonaise plate) ; **marquée synthétique** |
| Voix neuronales libres (Kokoro, MeloTTS, Piper) | **non dans le cloud ; chaîne prête** | les **modèles** sont sur Hugging Face, injoignable d'ici (`pip` joint PyPI) ; `tools/langues/tts_synth.py` synthétise un fichier de répliques avec le moteur choisi (repli espeak-ng), encode en Opus, calcule les tailles et écrit `media.json` ; **à exécuter sur un poste avec accès réseau** (Mac du propriétaire, session locale) ; modèles et licences de voix à vérifier au téléchargement |
| Enregistrements humains libres (Tatoeba, Common Voice, Wikimedia Commons) | **non** depuis le cloud | `tatoeba.org`, `commons.wikimedia.org`, Hugging Face : **refusés** par le proxy du cloud (réponse vide) ; à télécharger **hors du cloud** dans le dépôt privé `castbridge-content`, avec le manifeste de licences |
| Encodage Opus, H.264 480p, WebP | **oui** | `ffmpeg` avec `libopus`, `libx264`, `libwebp` et `aac` (mesuré : 28 Ko → 1,2 Ko pour 1 s de voix) |
| Vidéo avec personnes (dialogues filmés) | **non** | exige des acteurs, une prise de vue et des droits à l'image |
| Vidéo d'animation (sans personne) | **oui, en vectoriel** | rendue par le moteur d'animation (pas de MP4) ; un MP4 d'animation serait produit hors du cloud (`ffmpeg` à partir d'images) |
| Polices CJK | **non vérifiable** | aucune police CJK dans l'environnement ; test sur la TV |
| Reconnaissance vocale hors ligne | **non** | l'expression orale reste auto-évaluée |

**Ce qui exigera donc autre chose** : (1) des **enregistrements humains** (natifs, 2 à 3 voix par langue, un studio simple ou le téléphone dans une pièce calme) pour tout ce qui dépasse A0-A1 et pour tous les tons du chinois ; (2) une **chaîne d'outils sur un poste avec accès réseau** pour Piper/Common Voice/Tatoeba (script dans `castbridge-content`) ; (3) pour la vidéo avec personnes, une **autorisation écrite** et un tournage, ou un abandon au profit d'animations vectorielles.

## 9. Ce que les futurs agents vérificateurs devront contrôler

Par vague, un rapport JSON comme celui de la rubrique pédagogique (un par lot) :

| Domaine | Contrôle | Automatique ? |
|---|---|---|
| **Exactitude linguistique** | traductions justes, lecture correcte (**tons du pinyin**, furigana), genre / cas, conjugaisons, aucune faute d'orthographe | relecture par natif ; l'agent ne fait que signaler les incohérences |
| **Cohérence de niveau** | le vocabulaire et les structures d'une unité ne dépassent pas son niveau (liste de mots par niveau, comptage de mots hors liste) ; les items du test de positionnement sont à la bonne difficulté | oui, par liste de fréquence et comptage |
| **Réponses** | chaque réponse acceptée est correcte et chaque mauvaise est refusée (`LangMarking`) ; pas deux bonnes réponses dans un QCM | oui |
| **Licences** | chaque média a auteur/source/URL/licence acceptée ; aucun contenu copié (recherche de longues séquences identiques avec des sources connues) ; attribution générée | oui + revue |
| **Synthétique** | toute piste de synthèse marquée `synthetic` ; aucune voix imitant une personne | oui |
| **Budget** | lots ≤ 3 Mo / ≤ 100 Mo ; langue ≤ sa part ; total ≤ 6 144 Mo (`langues_budget.py --check`) | oui |
| **Accessibilité** | `alt`, transcription de tout audio, sous-titres de toute vidéo, pas de clignotement, couleur jamais seule | oui |
| **Rendu TV** | police CJK, taille des caractères, pas de chevauchement du pinyin/furigana | sur TV (manuel) |
| **Culture et sensibilité** | stéréotypes, contenu partisan, âges, personnes réelles, humour douteux | agent + humain |
| **Sécurité des fichiers** | ZIP sans chemin sortant, pas de gonflement (validateur de lots) | oui |
| **Progression** | graphe sans cycle, nœuds atteignables, aucun prérequis d'un niveau plus haut (`LangSkillGraph.errors`) | oui |

## 10. Alignement avec les autres chantiers (à traiter à l'intégration)

- **Politique médias de `net-architect`** : mêmes limites (image WebP ≤ 120 Ko / 1 280 px ; audio ≤ 200 Ko par 30 s, soit ≈ 53 kbit/s : nos 24 kbit/s y tiennent largement ; clip ≤ 30 s / ≤ 15 Mo / 480p). Notre lot média est de feature `langues-media` (et non `<classe>-media` de `learn`), à ajouter à `scopes.json`.
- **Graphe de compétences** : fichiers `content/graph/langue-<code>.json` au format de domaine ; le validateur actuel exige un `lots` valide et un niveau N0-N4 ; les valeurs sont fournies (pont `cefr` → N), `lots` à remplir en vague 1.
- **`content_budget.py`** : ajouter la prise en charge `langues*` (§ 4.5). Le plafond des 3 Go du reste de la base ne compte **pas** les langues.
- **Rubrique pédagogique** : critère `levelFidelity` à interpréter pour les langues en « fidélité au descripteur du niveau CECRL du § 1.2 » ; critères `assessmentAlignment` = item / compétence / niveau annoncés.
- **`LotApi`** : aucune modification ; deux `feature` nouvelles (`langues`, `langues-media`) suffisent au contrat existant (`LotId(feature, scope)`).

## 11. Décisions du propriétaire (2026-10-01) et questions restantes

| # | Question | Décision | Conséquence |
|---|---|---|---|
| 1 | taille du quota média | **au choix de l'architecte** | espace Langues du téléphone **2 Go par défaut, 100 Mo à 6 Go réglable** ; lot média ≤ 100 Mo, lot texte ≤ 3 Mo ; serveur 100 Mo pour `langues-media` ; TV : volume de la bibliothèque (§ 4.6) |
| 2 | licences | **le maximum de licences libres** | CC BY-SA acceptée donc contenu dérivé publié en **CC BY-SA 4.0** ; liste élargie (CC BY 2.0/3.0/4.0, CC BY-SA 3.0/4.0, MIT, Apache-2.0, BSD, OFL-1.1, GPL-3.0 pour la sortie d'un moteur) ; NC/ND exclues (§ 5, `LangLicences`) |
| 3 | audio | **excellentes voix de synthèse** | voix neuronales libres à tous les niveaux, produites hors du cloud par `tools/langues/tts_synth.py`, marquées synthétiques, tons et mélodie relus par un natif (§ 7.2, § 8) |

Questions restantes (non bloquantes) :
1. **Exécution de la synthèse** : qui la lance ? Il faut un poste avec accès à Hugging Face (modèles Kokoro / MeloTTS / Piper) : le Mac du propriétaire ou la session `castbridge-content` ; ~1 à 2 h de calcul par langue et par niveau sur un CPU récent.
2. **Données de traits** : Make Me a Hanzi (licence Arphic) reste à faire examiner ; KanjiVG (CC BY-SA 3.0) est accepté.
3. **Ordre des langues dans la vague 1** : recommandé, anglais, chinois, espagnol en tête.
4. **Variétés** (anglais camerounais / pidgin, français d'Afrique) : recommandé en compréhension, niveau natif.
5. **Langues de départ supplémentaires** (langues camerounaises) : ouvertes par le format, à décider plus tard.

## 12. Outils et fichiers

| Chemin | Rôle |
|---|---|
| `docs/LANGUES.md` | ce document |
| `content/langues/budget.json` | poids du budget (source unique) |
| `content/langues/lots.json` | registre des lots : familles (`free` / `reserved`) et versions (§ 14) |
| `content/langues/embedded.txt` | packs embarqués dans CastBridge-TV (§ 14) |
| `content/langues/zh-a0-salut-fr/` | pack d'exemple (une unité, textes uniquement ; audio décrit dans `media.json` mais non produit) |
| `content/graph/langue-<code>.json` | 7 graphes générés |
| `tools/langues/gen-graph.py` | générateur des graphes |
| `tools/langues/tts_synth.py` | synthèse vocale par lots (Kokoro / MeloTTS / Piper, repli espeak-ng) vers Opus + `media.json` |
| `tools/content-budget/langues_budget.py` | plan chiffré et vérification de l'enveloppe |
| `android/core/src/main/kotlin/castbridge/core/langues/` | squelette de code |
| `android/core/src/test/kotlin/castbridge/core/LanguesTest.kt` | 21 tests |

## 13. Décisions du propriétaire (journal, 2026-10-01, après le rapport de conception)
1. **Quota média du téléphone pour les Langues : 500 Mo**, réglable (et non les 1 à 2 Go évoqués dans le rapport). Le budget serveur de 6 Go reste l'enveloppe totale de la catégorie ; le téléphone n'en garde que ce quota.
2. **Contenu dérivé publié sous CC BY-SA 4.0 : oui.** Le propriétaire offrira la possibilité de **tout télécharger séparément** (archive publique complète). Conséquences que la conception doit respecter :
   - Le contenu **dérivé de sources CC BY-SA** est **libre** : il n'est **ni chiffré par TV, ni verrouillé par une mesure technique** qui empêcherait d'exercer les droits de la licence (CC BY-SA 4.0, § 2(a)(4)). Il porte l'**attribution** exigée, un **manifeste de licences par fichier**, et la **même licence** pour les œuvres dérivées.
   - Il doit exister une **archive téléchargeable séparément**, contenant tout ce qui est sous CC BY-SA, sans clé ni activation.
   - Le système de droits distingue deux familles de lots : **lots libres (SA)**, sans clé de contenu, et **lots réservés** (contenu **original** ou de domaine public / CC0), chiffrés par TV. **Ne mélange jamais** les deux dans un même lot. Le verrouillage « usage soumis à autorisation » de l'**application** reste un choix du propriétaire sur le logiciel, pas sur le contenu SA.
   - **Point à faire valider par un juriste** avant toute vente ou production en masse : l'articulation entre une licence de contenu libre, un logiciel soumis à autorisation et des lots payants (le coordinateur ne donne pas d'avis juridique).
3. **Voix : les deux.** Phase actuelle (petit budget) : **synthèse libre, clairement marquée comme synthétique**. **Enregistrements humains** prévus pour le contenu payant dès que le budget le permet. Les modèles neuronaux libres (Kokoro, MeloTTS, Piper) restent à **tester hors du cloud** (leurs modèles y sont injoignables) ; sans cela seule la voix `espeak-ng` (robotique) est disponible.

## 14. Lots `langues` construits, famille libre, démarrage embarqué (2026-10-02)

**Construire les lots texte** : `cd android && gradle --offline :core:buildLangLots` (ajouter `-Pupdate` pour incrémenter la version d'un lot dont le contenu a changé et réécrire `content/langues/lots.json`). Sortie dans `android/core/build/langues-lots/` (non versionné) :

- `castbridge-lot-langues-<scope>-v<N>.lot` : zip déterministe avec `langue.json` + `media.json` **à la racine** (c'est ce que lit `LangLotConsumer`) ; le lot est validé (`LangValidator`) et refusé au-dessus de 3 Mo avant d'être écrit. `"version"` dans `langue.json` doit être égal à la version du lot (contrôlée par le consommateur) : la relever en même temps que le lot.
- `lots-catalog.json` : même forme que celui des lots Apprendre (`feature`, `scope`, `version`, `bytes`, `sha256`, `title`, `minAppVersion`, `file`, `date`) plus `family`, `units`, `exercises`. **NON SIGNÉ** (`"signature":"UNSIGNED"`) : la signature appartient à la chaîne de publication, avec la vraie clé, hors dépôt.

**Registre** `content/langues/lots.json` :

```json
{"format":1,"free":["langues:zh-a0-salut-fr"],"reserved":[],"lots":{"zh-a0-salut-fr":{"version":1,"hash":"…","date":"2026-10-02"}}}
```

`free` = contenu dérivé de sources CC BY-SA : jamais chiffré, jamais loué ; `reserved` = contenu original ou de domaine public (chiffrable par TV, louable). Un lot est dans **une seule** liste (jamais mélangé) ; absent des deux, sa famille est inconnue et la construction échoue (et `LotFamilies.explicit` refuse la location). `LangLotRegistry.families()` donne l'objet `LotFamilies` correspondant.

**Démarrage embarqué** : `content/langues/embedded.txt` liste les packs copiés dans les ressources de l'APK (`castbridge/langues/embedded/<id>/{langue.json,media.json}` + `catalog.json`, tâche `:core:embedLanguesPacks`, branchée sur `processResources`). Libres uniquement, budget 1 Mo (testé), compté dans `StarterBudget`. `EmbeddedLangSource` les lit ; `LanguesHub.packs()` fusionne démarrage et lots installés : à nom égal, le lot installé gagne si sa version est au moins celle du démarrage. Le message « Aucune langue installée » n'apparaît donc que si aucun pack n'est embarqué. Tests : `LangLotBuildTest`.
