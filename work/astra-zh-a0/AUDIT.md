# Audit pédagogique — chinois A0, départ français

Astra, 29 octobre 2026. Périmètre : quatre packs, cinq unités internes. Statut : **bêta : non validé**. Aucune validation par un locuteur natif.

## Constats et réponses

| Axe | Avant | Intervention / limite |
|---|---|---|
| Progression | 10 mots de salutation, 13 repères phonétiques, 11 nombres, 8 mots familiaux ; la liste peut sembler à apprendre d’un coup. | Petits groupes prioritaires et seconde séance annoncés dans les fiches ; vocabulaire et identifiants conservés. L’écran présente encore toute la liste avant les conseils : le séquençage réel reste un chantier UX. |
| A0 complet | Dictée et traduction en caractères dès le début ; phrases à saisir sur TV. | Reconnaissance guidée avec pinyin, 35 QCM et 5 productions facultatives avec modèle, sans saisie ni micro. Même nombre d’exercices par unité. |
| Tons | Troisième ton présenté comme toujours descendant-remontant ; ton neutre « sans contour ». Courbes toutes superposées dans le premier panneau. | Distinction isolation / parole liée, hauteur contextuelle du neutre, nǐ hǎo écrit / ní hǎo prononcé. Courbes replacées et épaissies dans les deux états de la figure. |
| Paires minimales | Les caractères et les sens prenaient le pas sur les sons. | mā/mǎ, má/mà, shí/shì ; zhī/chī à ton constant pour l’aspiration. sì/shí explicitement distingué d’une paire ne changeant que le ton. |
| Pinyin | Plusieurs exercices exigeaient de reconnaître un caractère seul. | Pinyin présent dans les nouveaux choix chinois ; explications de x, q, ü sans équivalences françaises trompeuses. |
| Grammaire | 是/不 = « oui/non », « le chinois n’a pas de mot pour mon », ordre des mots présenté comme suffisant. | Traductions contextualisées ; possession avec omission possible de 的 pour les proches ; trois formules prioritaires au lieu d’une règle générale trompeuse. |
| Dialogue Famille | Six répliques, plusieurs mots non enseignés et 家很好 traduit abusivement par « ta famille est chouette ». | Quatre répliques courtes, présentations + 你好. Ancien audio détaché du dialogue, entrée du manifeste conservée. |
| Réemploi | Peu de liens explicites entre unités. | 我, 是, 你好, 谢谢, 七, 十 et les syllabes neutres réapparaissent dans les notes et les questions. |
| Caractères | Copie de mémoire prématurée ; animations existantes à contrôler. | Reconnaissance prioritaire. Aucun caractère généré dans les images ; autres ordres de traits laissés intacts, à relire nativement. |
| Afrique centrale | Contextes génériques ; peu d’ancrage. | Atelier à Douala ou Yaoundé, prêt et comptage de cahiers, personnages fictifs en vêtements ordinaires. Aucun cliché, aucune identité réelle ; pas de supposition sur la famille de l’élève. |
| Évaluation | Les exercices d’écoute dépendaient d’audios synthétiques existants non validés. | On distingue lecture de pinyin et audition. Le résultat aux QCM ne prouve pas une compétence orale. Les productions facultatives restent auto-évaluées ; l’app peut néanmoins les compter dans son score. |

## Multimédia

Trois images générées avec image_gen : rencontre, famille fictive, trois cahiers. Les images servent de supports de vocabulaire et de cartes ; aucune question ne nécessite leur affichage pour réussir. WebP 960 × 640, sans texte, chacune sous 48 Ko. Sources durables sous les trois dossiers `media/` des packs ; copies d’intégration sous `content/langues-media/`, emplacement attendu par le lecteur et l’exporteur.

Le schéma des tons reste vectoriel et déterministe : plus fiable pour la géométrie et le pinyin. Pas de nouvelle planche de gestes chinois ni de nouveaux traits : l’anatomie générative et les variantes régionales nécessiteraient une validation dédiée. Les illustrations proposées dans la consigne sont traitées comme exemples de supports utiles, pas comme obligation de tous les produire.

## Références et points de vigilance

Lecture de `docs/LANGUES.md` (format, licences, budget, parcours), des sections applicables de `docs/LEARN.md` (télécommande, états, figures et animations), et du cadre de `docs/LEARN-REVIEW.md` : ce dernier est un rapport généré sur les lots scolaires, pas une certification de ces unités Langues. Inspection du parseur et de `LangValidator` dans `LangPack.kt`, des tests `LanguesTest`, `LangLotBuildTest`, `LearnAnimationExamplesTest`, de l’exporteur des contenus libres et de l’écran Langues de CastBridge-TV.

Recoupement documentaire des changements de tons : [Chinese Language Institute — Tone Changes in Mandarin](https://studycli.org/learn-chinese/tone-changes-in-mandarin/). Aperçu grammatical consulté dans les résultats : [Chinese: An Essential Grammar, troisième édition](https://api.pageplace.de/preview/DT0400.9781000345032_A41207579/preview-9781000345032_A41207579.pdf). Les pages AllSet visées n’étaient pas accessibles (403/erreur) ; elles ne sont pas comptées comme vérification. Ces consultations ne remplacent pas une relecture native.

À faire vérifier : naturel des dialogues, pinyin lexical versus réalisation parlée, suites de trois 3es tons dans le dialogue Salutations inchangé, neutralisation des termes familiaux selon le modèle standard choisi, indications d’articulation, naturel de la question d’âge adressée à un enfant, ordre des traits existant. Aucun doute n’est présenté comme une validation acquise.
