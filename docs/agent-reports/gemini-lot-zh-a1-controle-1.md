# Contrôle de l'orchestrateur — lot Gemini `zh-a1-ma-famille-fr`, envoi 1 (2026-10-07)

Reçu via le propriétaire : rapport de QA (auto : 10 × 4/4), plan de 8 leçons, extraits `langue.json`/`media.json` (L1-L2 seulement), 5 demandes de médias, figures/scénario, journal, 10 règles, 5 pièges. Aucun outil n'a pu tourner : le JSON ne suit pas le schéma du lecteur.

## Bloquant (format : rien ne se charge)
- Schéma inventé (`"format":"langue"`, `content[]`, `audioId`, `strokeOrderSvg`, `minimal_pair`, `kind: ordering`, `correctOrder`). Schéma réel = `core/langues/LangPack.kt` + `content/langues/zh-a0-salut-fr/langue.json` : `format: 1`, `type: "langue"`, `id`, `version` (entier), `target: "zh"`, `level: "A1"`, `theme`, `source: "fr"`, `title`, `state: "review"`, `units[{id, title, minutes, skills, prerequisites, vocab[{id, term, reading, gloss, pos, audio}], dialogues[{id, title, lines[{who, text, reading, tr, audio}]}], grammar[{id, title, md, examples[{text, tr}]}], exercises[{id, kind, prompt, audio, answers, choices, correct, pairs, words, model, skill}], stories, cards[{id, vocab}], animations[{id, kind:"strokes", label}]}]`. Genres d'exercice : `dictation | match | order | mcq | cloze | translate | truefalse | speak | write | strokes`. Pas de champ `explanation` : champ **additif** autorisé (`CONTENT-ARCHITECTURE` § 5) : `explanation` (chaîne) et `wrongWhy` (liste alignée sur `choices`) ; le lecteur l'ignorera jusqu'à la mise à jour du cœur (à faire : LangPack additif).
- Livraison partielle (L1-L2) alors que le § 4 exige le lot complet (8 leçons) ; accepté en plusieurs messages, une leçon complète par message.
- `truefalse` L1-ex1 : réponse « Faux » mais `correct: 0` (= Vrai) : inversé.
- Demandes de médias : `lang` doit être `zh` (variété `zh-CN` à part) ; `voiceClass` = `A`/`B` (pas female/male) ; `licenseExpected` = `CASTBRIDGE-ORIGINAL` (CC BY-SA est la licence de sortie du lot) ; les figures vectorielles ne sont pas des demandes de médias (lot texte, `animations` kind `strokes`, données de traits fournies par l'outillage : KanjiVG accepté, Make Me a Hanzi en examen).

## Important (pédagogie / cahier)
- Réemploi réel : L1 7/13 = 54 %, L2 5/15 = 33 % (< 60 %) ; l'auto-QA annonce « 65 % en moyenne » : non démontré.
- Paires minimales : 四 sì / 十 shí et 岁 suì / 水 shuǐ diffèrent par DEUX traits (initiale + ton) : pas minimales. Paire exacte sur mots connus : **十 shí / 是 shì** (ton 2/4). Les autres (妈/马, 他/塔, 几/鸡, 买/卖, 师/十, 足/租) sont correctes.
- 呢 (« 你呢？ ») hors liste grammaticale A1 : à enseigner comme bloc figé (item) ou à retirer.
- « 我叫Ami。 » : lettres latines dans un texte à synthétiser : 阿米 (Āmǐ), cohérent avec 阿米娜 du lot A0.
- Auto-QA 10 × 4/4 non crédible : `accessibility` 4 sans aucun texte alternatif dans le JSON ; `workedExamples` 4 sans exemple résolu visible ; `progression` 4 avec un réemploi sous le seuil.
- Règle 4 du kit (« quota des 120 mots ») périmée : 50-70 mots nouveaux.
- Journal « Bloqué : rien » : faux : aucun relecteur natif, aucune voix approuvée ⇒ `bêta : non validé`.

## Décisions prises
- Audit croisé par Luna **après** le lot complet et conforme (un seul envoi d'audit) ; Luna continue sa production allemande.
- À faire côté outillage (Relève, Haiku) : `LangPack` additif `explanation`/`wrongWhy` ; `mp.py` tolérant à la casse de `level` ; exemple `zh-a0-salut-fr` passé en `A0`.
