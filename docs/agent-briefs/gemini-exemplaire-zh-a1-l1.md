# À coller tel quel chez Gemini — ta leçon 1 convertie au schéma réel (exemplaire) → leçons 2 à 8 attendues

Gemini, ta leçon 1 a été convertie au **schéma réel du lecteur** et passe le contrôle outillé (`parse OK, 0 problème` ; `mp.py` : 10 demandes, 0 conflit). Elle est **l'exemplaire** : produis les leçons 2 à 8 **exactement dans ce format**, une leçon complète par message, puis le `media.json` complet du lot. Ne fournis pas de `media-requests.jsonl` (l'outil le génère depuis `langue.json`).

## Règles de format apprises (toutes imposées par le lecteur)
1. Identifiant et dossier du paquet : `zh-a1-mafamille-fr` (**thème sans tiret ni accent**, `[a-z0-9]{1,16}` : le lecteur découpe en 4 segments) ; chaque identifiant d'unité, de vocabulaire, de dialogue, d'exercice ou de carte commence par cet identifiant.
2. `prerequisites` d'une unité = identifiants d'**unités du même paquet** seulement (`[]` pour l'unité 1 ; `["zh-a1-mafamille-fr-u1"]` pour l'unité 2…) ; les 7 nœuds du graphe A0 vont dans le champ additif `prereq`.
3. `explanation` = chaîne ; `wrongWhy` = **liste alignée sur `choices`** (`"—"` sur la bonne réponse).
4. `truefalse` : `choices` = `["Vrai", "Faux"]`, `correct` **0 = Vrai, 1 = Faux**.
5. `order` : `words` = mots mélangés, `answers` = `["la phrase exacte"]` (sans espaces en chinois).
6. `mcq` à audio (paire minimale) : l'audio de l'exercice a **son propre identifiant** (`m:zh-shi-paire`) et l'exercice porte le champ additif `"text"` = ce qui est dit ; le trait distinctif est écrit dans `explanation`.
7. Médias : identifiants en **pinyin ASCII sans tons** (`zh-kamailong`, `zh-zhongguo`) ; `media.json` déclare pour chacun `file` (`audio/<id>.opus`), `kind`, `bytes` et `durationMs` **estimés** (16 kbit/s pour un mot, 24 kbit/s pour une phrase ou un dialogue), `license: "CASTBRIDGE-ORIGINAL"`, `lang: "zh"`, `variety: "zh-CN"`, `voiceClass` (`neutre` pour un mot ou un audio d'exercice, `A` réplique féminine, `B` masculine, `A+B` dialogue d'ensemble), `register: "lent"`, `status: "draft-not-authorized"`. Aucun fichier n'est produit.
8. Chaque dialogue porte `audio` (ensemble) et chaque réplique son `audio` ; **le clip court de la leçon** (le propriétaire y tient) se rattache au dialogue par `"video": "m:zh-u1-d1-clip"`, déclaré en `"kind": "video"` (estimation) ; son scénario tient en 8 lignes dans ton message (plan, personnages, texte dit = le dialogue, durée ≤ 40 s).
9. `animations` : `{"id", "kind": "strokes", "label"}` pour les caractères dont l'ordre des traits est enseigné ; les données de traits viennent de l'outillage, pas de toi.
10. `state: "review"` ; aucun mot réservé (« validé », « natif », « naturel ») ; 阿米 (Āmǐ), jamais « Ami » en lettres latines dans un texte à synthétiser ; 呢 enseigné comme bloc figé (fait en L1).

## Une correction à faire dans ta leçon 1 (puis à éviter ensuite)
`…-u1-x2` : « Vrai ou faux ? 阿米 est chinoise » marque **Faux**, mais le dialogue ne dit rien de la nationalité d'Ami : « non dit » n'est pas « faux ». Remplace par un énoncé **vérifiable dans le dialogue** (p. ex. « B est camerounais » → Vrai ; « 阿米 dit : 我是中国人 » → Faux).

## Cahier (rappel) pour les leçons 2 à 8
50-70 mots **réellement nouveaux** sur le lot (hors A0), 110-130 items, 12-15 par leçon ; chaque leçon réemploie ≥ 60 % de la précédente **en variant les ancres** (les items propres d'une leçon reviennent dans la suivante) ; une paire minimale **exacte** par leçon (十 shí / 是 shì oui ; 四/十 et 岁/水 non : deux traits changent) ; 4 à 6 exercices par leçon dont 1 `truefalse` ou `mcq` de compréhension, 1 `order` ou `cloze`, 1 `match`, 1 `speak` ou `dictation` ; commandes à 5 touches (pas de glisser-déposer, pas de micro obligatoire : `speak` a toujours un `model` lisible) ; une histoire courte en L8 (`stories[].paragraphs` = liste de `{"who": "", "text", "reading", "tr", "audio"}`).

Suite : lot complet (8 leçons) → contrôle outillé → audit pédagogique par Luna (un seul envoi). Ton audit du lot allemand de Luna commencera quand son envoi 2 (corrigé) sera reçu.

---

## Exemplaire : `langues/zh-a1-mafamille-fr/langue.json`
```json
{
 "format": 1,
 "type": "langue",
 "id": "zh-a1-mafamille-fr",
 "version": 1,
 "target": "zh",
 "level": "A1",
 "theme": "mafamille",
 "source": "fr",
 "title": "Chinois A1 — Ma famille et moi",
 "state": "review",
 "units": [
  {
   "id": "zh-a1-mafamille-fr-u1",
   "title": "Je me présente : mon nom et mon pays",
   "minutes": 10,
   "skills": ["co", "ce", "po", "pe"],
   "prerequisites": [],
   "prereq": ["zh.a0-co", "zh.a0-ce", "zh.a0-po", "zh.a0-pe", "zh.a0-pinyin", "zh.a0-tons", "zh.a0-traits"],
   "vocab": [
    {"id": "zh-a1-mafamille-fr-u1-v1", "term": "是", "reading": "shì", "gloss": "être", "pos": "v", "audio": "m:zh-shi"},
    {"id": "zh-a1-mafamille-fr-u1-v2", "term": "人", "reading": "rén", "gloss": "personne", "pos": "n", "audio": "m:zh-ren"},
    {"id": "zh-a1-mafamille-fr-u1-v3", "term": "喀麦隆", "reading": "Kāmàilóng", "gloss": "Cameroun", "pos": "npr", "audio": "m:zh-kamailong"},
    {"id": "zh-a1-mafamille-fr-u1-v4", "term": "中国", "reading": "Zhōngguó", "gloss": "Chine", "pos": "npr", "audio": "m:zh-zhongguo"},
    {"id": "zh-a1-mafamille-fr-u1-v5", "term": "呢", "reading": "ne", "gloss": "et … ? (particule, bloc figé : 你呢？ = et toi ?)", "pos": "part", "audio": "m:zh-ne"},
    {"id": "zh-a1-mafamille-fr-u1-v6", "term": "阿米", "reading": "Āmǐ", "gloss": "Ami (prénom)", "pos": "npr", "audio": "m:zh-ami"}
   ],
   "dialogues": [
    {
     "id": "zh-a1-mafamille-fr-u1-d1",
     "title": "Se présenter : nom et pays",
     "audio": "m:zh-u1-d1",
     "video": "m:zh-u1-d1-clip",
     "lines": [
      {"who": "A", "text": "你好！我叫阿米。", "reading": "nǐ hǎo! wǒ jiào Āmǐ.", "tr": "Bonjour ! Je m'appelle Ami.", "audio": "m:zh-u1-d1-a"},
      {"who": "B", "text": "你好！我是喀麦隆人。你呢？", "reading": "nǐ hǎo! wǒ shì Kāmàilóng rén. nǐ ne?", "tr": "Bonjour ! Je suis camerounais. Et toi ?", "audio": "m:zh-u1-d1-b"}
     ]
    }
   ],
   "grammar": [
    {
     "id": "zh-a1-mafamille-fr-u1-g1",
     "title": "是 + nom de pays + 人",
     "md": "Pour dire sa nationalité : **sujet + 是 + nom de pays + 人**. 是 (*shì*) joue le rôle du verbe « être ». 人 (*rén*) veut dire « personne » : 喀麦隆人 = « personne du Cameroun » = camerounais(e). Le verbe ne change jamais : 是 reste 是 pour tous.",
     "examples": [
      {"text": "我是喀麦隆人。", "tr": "wǒ shì Kāmàilóng rén. — Je suis camerounais(e)."},
      {"text": "我是中国人。", "tr": "wǒ shì Zhōngguó rén. — Je suis chinois(e)."}
     ]
    }
   ],
   "exercises": [
    {
     "id": "zh-a1-mafamille-fr-u1-x1",
     "kind": "mcq",
     "prompt": "Écoute : quel mot ?",
     "audio": "m:zh-shi-paire",
     "text": "是",
     "choices": ["十 shí — dix", "是 shì — être"],
     "correct": 1,
     "skill": "co",
     "explanation": "Seul le ton change : 十 shí est au 2e ton (montant), 是 shì est au 4e ton (descendant). Trait distinctif : ton 2 / ton 4.",
     "wrongWhy": ["十 shí monte (2e ton) ; le mot entendu descend (4e ton).", "—"]
    },
    {
     "id": "zh-a1-mafamille-fr-u1-x2",
     "kind": "truefalse",
     "prompt": "Vrai ou faux ? B est camerounais.",
     "choices": ["Vrai", "Faux"],
     "correct": 0,
     "skill": "ce",
     "explanation": "B dit 我是喀麦隆人 : je suis camerounais.",
     "wrongWhy": ["—", "B le dit lui-même : 我是喀麦隆人."]
    },
    {
     "id": "zh-a1-mafamille-fr-u1-x3",
     "kind": "order",
     "prompt": "Remets les mots dans l'ordre : « Je suis camerounais. »",
     "words": ["我", "喀麦隆", "人", "是"],
     "answers": ["我是喀麦隆人"],
     "skill": "pe",
     "explanation": "Sujet 我 + verbe 是 + pays 喀麦隆 + 人."
    },
    {
     "id": "zh-a1-mafamille-fr-u1-x4",
     "kind": "match",
     "prompt": "Rappel de l'A0 : associe chaque mot à son sens.",
     "pairs": [
      {"a": "我", "b": "je / moi"},
      {"a": "你", "b": "tu / toi"},
      {"a": "叫", "b": "s'appeler"},
      {"a": "名字", "b": "nom / prénom"},
      {"a": "再见", "b": "au revoir"},
      {"a": "谢谢", "b": "merci"}
     ],
     "skill": "ce",
     "explanation": "Mots déjà vus en A0 : 我 wǒ, 你 nǐ, 叫 jiào, 名字 míngzi, 再见 zàijiàn, 谢谢 xièxie."
    }
   ],
   "stories": [],
   "cards": [
    {"id": "zh-a1-mafamille-fr-u1-c1", "vocab": "zh-a1-mafamille-fr-u1-v1"},
    {"id": "zh-a1-mafamille-fr-u1-c2", "vocab": "zh-a1-mafamille-fr-u1-v2"},
    {"id": "zh-a1-mafamille-fr-u1-c3", "vocab": "zh-a1-mafamille-fr-u1-v3"},
    {"id": "zh-a1-mafamille-fr-u1-c4", "vocab": "zh-a1-mafamille-fr-u1-v4"},
    {"id": "zh-a1-mafamille-fr-u1-c5", "vocab": "zh-a1-mafamille-fr-u1-v5"},
    {"id": "zh-a1-mafamille-fr-u1-c6", "vocab": "zh-a1-mafamille-fr-u1-v6"}
   ],
   "animations": [
    {"id": "zh-a1-mafamille-fr-u1-a1", "kind": "strokes", "label": "Ordre des traits de 是"},
    {"id": "zh-a1-mafamille-fr-u1-a2", "kind": "strokes", "label": "Ordre des traits de 人"}
   ]
  }
 ]
}
```

## Exemplaire : `langues/zh-a1-mafamille-fr/media.json` (forme d'une entrée par type ; même forme pour chaque média)
```json
{
 "format": 1,
 "comment": "DÉCLARATIF : aucun fichier n'est produit (aucune voix ni moteur approuvé, aucune autorisation). bytes et durationMs sont des ESTIMATIONS (16 kbit/s pour un mot, 24 kbit/s pour une phrase ou un dialogue) ; mp.py receive les remplacera par les mesures des fichiers acceptés.",
 "media": [
  {"id": "zh-shi", "file": "audio/zh-shi.opus", "kind": "audio", "bytes": 1400, "durationMs": 700, "license": "CASTBRIDGE-ORIGINAL", "lang": "zh", "text": "是", "reading": "shì", "voiceClass": "neutre", "variety": "zh-CN", "register": "lent", "status": "draft-not-authorized"},
  {"id": "zh-ren", "file": "audio/zh-ren.opus", "kind": "audio", "bytes": 1400, "durationMs": 700, "license": "CASTBRIDGE-ORIGINAL", "lang": "zh", "text": "人", "reading": "rén", "voiceClass": "neutre", "variety": "zh-CN", "register": "lent", "status": "draft-not-authorized"},
  {"id": "zh-kamailong", "file": "audio/zh-kamailong.opus", "kind": "audio", "bytes": 2600, "durationMs": 1300, "license": "CASTBRIDGE-ORIGINAL", "lang": "zh", "text": "喀麦隆", "reading": "Kāmàilóng", "voiceClass": "neutre", "variety": "zh-CN", "register": "lent", "status": "draft-not-authorized"},
  {"id": "zh-zhongguo", "file": "audio/zh-zhongguo.opus", "kind": "audio", "bytes": 2000, "durationMs": 1000, "license": "CASTBRIDGE-ORIGINAL", "lang": "zh", "text": "中国", "reading": "Zhōngguó", "voiceClass": "neutre", "variety": "zh-CN", "register": "lent", "status": "draft-not-authorized"},
  {"id": "zh-ne", "file": "audio/zh-ne.opus", "kind": "audio", "bytes": 1200, "durationMs": 600, "license": "CASTBRIDGE-ORIGINAL", "lang": "zh", "text": "呢", "reading": "ne", "voiceClass": "neutre", "variety": "zh-CN", "register": "lent", "status": "draft-not-authorized"},
  {"id": "zh-ami", "file": "audio/zh-ami.opus", "kind": "audio", "bytes": 1800, "durationMs": 900, "license": "CASTBRIDGE-ORIGINAL", "lang": "zh", "text": "阿米", "reading": "Āmǐ", "voiceClass": "neutre", "variety": "zh-CN", "register": "lent", "status": "draft-not-authorized"},
  {"id": "zh-u1-d1", "file": "audio/zh-u1-d1.opus", "kind": "audio", "bytes": 18000, "durationMs": 6000, "license": "CASTBRIDGE-ORIGINAL", "lang": "zh", "text": "你好！我叫阿米。\n你好！我是喀麦隆人。你呢？", "reading": "nǐ hǎo! wǒ jiào Āmǐ.\nnǐ hǎo! wǒ shì Kāmàilóng rén. nǐ ne?", "voiceClass": "A+B", "variety": "zh-CN", "register": "lent", "status": "draft-not-authorized"},
  {"id": "zh-u1-d1-a", "file": "audio/zh-u1-d1-a.opus", "kind": "audio", "bytes": 6600, "durationMs": 2200, "license": "CASTBRIDGE-ORIGINAL", "lang": "zh", "text": "你好！我叫阿米。", "reading": "nǐ hǎo! wǒ jiào Āmǐ.", "voiceClass": "A", "variety": "zh-CN", "register": "lent", "status": "draft-not-authorized"},
  {"id": "zh-u1-d1-b", "file": "audio/zh-u1-d1-b.opus", "kind": "audio", "bytes": 10800, "durationMs": 3600, "license": "CASTBRIDGE-ORIGINAL", "lang": "zh", "text": "你好！我是喀麦隆人。你呢？", "reading": "nǐ hǎo! wǒ shì Kāmàilóng rén. nǐ ne?", "voiceClass": "B", "variety": "zh-CN", "register": "lent", "status": "draft-not-authorized"},
  {"id": "zh-shi-paire", "file": "audio/zh-shi-paire.opus", "kind": "audio", "bytes": 1400, "durationMs": 700, "license": "CASTBRIDGE-ORIGINAL", "lang": "zh", "text": "是", "reading": "shì", "voiceClass": "neutre", "variety": "zh-CN", "register": "lent", "status": "draft-not-authorized"},
  {"id": "zh-u1-d1-clip", "file": "video/zh-u1-d1-clip.mp4", "kind": "video", "bytes": 2400000, "durationMs": 30000, "license": "CASTBRIDGE-ORIGINAL", "lang": "zh", "text": "你好！我叫阿米。\n你好！我是喀麦隆人。你呢？", "voiceClass": "A+B", "variety": "zh-CN", "register": "lent", "status": "draft-not-authorized"}
 ]
}
```
