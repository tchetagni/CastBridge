#!/usr/bin/env python3
"""Pilote « Langues » A0 : génère les packs chinois (zh) et japonais (ja), niveau A0,
3 thèmes (salut, nombres, famille) x 2 langues de départ (fr, en) + le lot média partagé.

Produit, sous le dépôt :
  content/langues/<cible>-a0-<theme>-<depart>/langue.json   (pack texte, validé par LangValidator)
  content/langues/<cible>-a0-<theme>-<depart>/media.json    (manifeste des médias référencés)
  content/langues/<cible>-a0-<theme>-<depart>/figures/*.json(blocs « illustration » : traits, gestes)
  content/langues-media/<cible>-a0-<theme>/audio/*.opus     (voix de synthèse espeak-ng, licence GPL-3.0)

La voix est synthétique (espeak-ng, robotique) : marquée `synthetic`, adaptée aux mots isolés A0.
Les ordres de traits sont INDICATIFS : à faire valider par un locuteur natif (docs/LANGUES.md § 3.5).
Exécution : python3 tools/langues/gen_a0_pilot.py  (exige espeak-ng et ffmpeg sur le PATH).
"""
import json, os, shutil, subprocess, sys, tempfile, wave

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ANIM = os.path.join(ROOT, "tools", "anim")
sys.path.insert(0, ANIM)
import animlib  # noqa: E402

CONTENT_DIR = os.path.join(ROOT, "content", "langues")
MEDIA_DIR = os.path.join(ROOT, "content", "langues-media")

ENGINE = "espeak-ng 1.52"
VOICE = {"zh": ("cmn", "cmn"), "ja": ("ja", "ja")}
SCOPE_LEVEL = "a0"


# ---------------------------------------------------------------------------------------------- contenu
# Chaque mot : term (écriture), reading (pinyin/kana), gloss {fr,en}, pos, audio (slug), rom (romaji TTS ja).
# Chaque dialogue : title{fr,en}, audio, lines[{who,text,reading,{fr,en}}].
# Chaque exercice : kind, prompt{fr,en}, audio?, answers[], choices[{fr,en}], correct?, pairs[{a,{fr,en}}],
#   words[], model?, model_audio? (slug d'un audio à jouer pour `speak`).
CONTENT = {
    "zh": {
        "salut": {
            "title": {"fr": "Chinois A0 — Se saluer", "en": "Chinese A0 — Greetings"},
            "unit": {"title": {"fr": "Bonjour, merci, au revoir", "en": "Hello, thank you, goodbye"}, "minutes": 10, "skills": ["co", "ce", "po"]},
            "vocab": [
                ("你好", "nǐ hǎo", "bonjour", "hello", "interj", "nihao", None),
                ("谢谢", "xièxie", "merci", "thank you", "interj", "xiexie", None),
                ("再见", "zàijiàn", "au revoir", "goodbye", "interj", "zaijian", None),
                ("请", "qǐng", "s'il te plaît", "please", "interj", "qing", None),
                ("对不起", "duìbuqǐ", "pardon", "sorry", "interj", "duibuqi", None),
                ("是", "shì", "oui (être)", "yes (to be)", "v", "shi", None),
                ("不", "bù", "non", "no", "adv", "bu", None),
                ("你好吗", "nǐ hǎo ma", "comment vas-tu ?", "how are you?", "phrase", "nihaoma", None),
                ("很好", "hěn hǎo", "très bien", "very well", "phrase", "henhao", None),
                ("早上好", "zǎoshang hǎo", "bonjour (le matin)", "good morning", "interj", "zaoshanghao", None),
            ],
            "dialogue": {
                "title": {"fr": "Une rencontre", "en": "A meeting"}, "audio": "d1",
                "lines": [
                    ("A", "你好！", "nǐ hǎo!", "Bonjour !", "Hello!"),
                    ("B", "你好！", "nǐ hǎo!", "Bonjour !", "Hello!"),
                    ("A", "你好吗？", "nǐ hǎo ma?", "Comment vas-tu ?", "How are you?"),
                    ("B", "我很好，谢谢。你呢？", "wǒ hěn hǎo, xièxie. nǐ ne?", "Je vais très bien, merci. Et toi ?", "I'm very well, thanks. And you?"),
                    ("A", "我也很好。", "wǒ yě hěn hǎo.", "Moi aussi, je vais très bien.", "I'm very well too."),
                    ("B", "再见！", "zàijiàn!", "Au revoir !", "Goodbye!"),
                    ("A", "再见！", "zàijiàn!", "Au revoir !", "Goodbye!"),
                ],
            },
            "grammar": [
                {"title": {"fr": "Pas de conjugaison, pas d'article", "en": "No conjugation, no articles"},
                 "md": {"fr": "Le chinois ne conjugue pas les verbes et n'utilise pas d'articles. L'ordre des mots suffit : **你好** (littéralement « toi bien ») veut dire « bonjour ». Un même mot sert pour tout le monde.",
                        "en": "Chinese does not conjugate verbs and uses no articles. Word order is enough: **你好** (literally 'you good') means 'hello'. The same word works for everyone."},
                 "examples": [{"text": "你好", "fr": "bonjour (littéralement : toi bien)", "en": "hello (literally: you good)"},
                              {"text": "我很好", "fr": "je vais très bien (littéralement : moi très bien)", "en": "I am very well (literally: I very good)"}]},
                {"title": {"fr": "La particule 吗 forme la question", "en": "The particle 吗 makes a question"},
                 "md": {"fr": "On ajoute **吗** (ma) en fin de phrase pour poser une question oui/non : **你好吗 ?** (comment vas-tu ?). La réponse reprend le mot clé : **好** (bien).",
                        "en": "Add **吗** (ma) at the end of a sentence to ask a yes/no question: **你好吗?** (how are you?). The answer repeats the key word: **好** (well)."},
                 "examples": [{"text": "你好吗？", "fr": "Comment vas-tu ?", "en": "How are you?"}]},
            ],
            "exercises": [
                {"kind": "dictation", "prompt": {"fr": "Écoute et écris en caractères.", "en": "Listen and write in characters."}, "audio": "xiexie", "answers": ["谢谢"]},
                {"kind": "mcq", "prompt": {"fr": "Que veut dire 再见 ?", "en": "What does 再见 mean?"}, "choices": [{"fr": "bonjour", "en": "hello"}, {"fr": "merci", "en": "thank you"}, {"fr": "au revoir", "en": "goodbye"}], "correct": 2},
                {"kind": "match", "prompt": {"fr": "Associe chaque mot à son sens.", "en": "Match each word to its meaning."}, "pairs": [{"a": "你好", "fr": "bonjour", "en": "hello"}, {"a": "谢谢", "fr": "merci", "en": "thank you"}, {"a": "再见", "fr": "au revoir", "en": "goodbye"}, {"a": "是", "fr": "oui", "en": "yes"}, {"a": "不", "fr": "non", "en": "no"}]},
                {"kind": "speak", "prompt": {"fr": "Dis « merci » en chinois, puis compare avec l'enregistrement.", "en": "Say 'thank you' in Chinese, then compare with the recording."}, "model": "xièxie (4e ton + ton neutre)", "audio": "xiexie"},
                {"kind": "order", "prompt": {"fr": "Remets les mots dans l'ordre.", "en": "Put the words in the right order."}, "words": ["我", "很", "好"], "answers": ["我很好"]},
                {"kind": "truefalse", "prompt": {"fr": "« 你好吗 ? » veut dire « Comment vas-tu ? ».", "en": "'你好吗?' means 'How are you?'."}, "correct": 0},
                {"kind": "cloze", "prompt": {"fr": "Complète : 你好__ ? (particule de question).", "en": "Fill in: 你好__ ? (question particle)."}, "answers": ["吗"]},
                {"kind": "translate", "prompt": {"fr": "Traduis : merci.", "en": "Translate: thank you."}, "answers": ["谢谢"]},
                {"kind": "write", "prompt": {"fr": "Écris « bonjour » en caractères.", "en": "Write 'hello' in characters."}, "model": "你好"},
            ],
            "cards": [0, 1, 2, 5, 6],
            "animations": [{"kind": "strokes", "label": {"fr": "Ordre des traits de 你", "en": "Stroke order of 你"}, "char": "你", "reading": "nǐ"},
                           {"kind": "strokes", "label": {"fr": "Ordre des traits de 好", "en": "Stroke order of 好"}, "char": "好", "reading": "hǎo"},
                           {"kind": "strokes", "label": {"fr": "Ordre des traits de 再", "en": "Stroke order of 再"}, "char": "再", "reading": "zài"}],
        },
        "nombres": {
            "title": {"fr": "Chinois A0 — Les nombres", "en": "Chinese A0 — Numbers"},
            "unit": {"title": {"fr": "Compter de 0 à 10", "en": "Counting from 0 to 10"}, "minutes": 10, "skills": ["co", "ce"]},
            "vocab": [
                ("零", "líng", "zéro", "zero", "num", "ling", None),
                ("一", "yī", "un", "one", "num", "yi", None),
                ("二", "èr", "deux", "two", "num", "er", None),
                ("三", "sān", "trois", "three", "num", "san", None),
                ("四", "sì", "quatre", "four", "num", "si", None),
                ("五", "wǔ", "cinq", "five", "num", "wu", None),
                ("六", "liù", "six", "six", "num", "liu", None),
                ("七", "qī", "sept", "seven", "num", "qi", None),
                ("八", "bā", "huit", "eight", "num", "ba", None),
                ("九", "jiǔ", "neuf", "nine", "num", "jiu", None),
                ("十", "shí", "dix", "ten", "num", "shiten", None),
            ],
            "dialogue": {
                "title": {"fr": "Compter", "en": "Counting"}, "audio": "d1",
                "lines": [
                    ("A", "一、二、三。", "yī, èr, sān.", "Un, deux, trois.", "One, two, three."),
                    ("B", "四、五、六。", "sì, wǔ, liù.", "Quatre, cinq, six.", "Four, five, six."),
                    ("A", "七、八、九、十。", "qī, bā, jiǔ, shí.", "Sept, huit, neuf, dix.", "Seven, eight, nine, ten."),
                    ("B", "你几岁？", "nǐ jǐ suì?", "Quel âge as-tu ?", "How old are you?"),
                    ("A", "我十岁。", "wǒ shí suì.", "J'ai dix ans.", "I'm ten."),
                    ("B", "谢谢！", "xièxie!", "Merci !", "Thank you!"),
                ],
            },
            "grammar": [
                {"title": {"fr": "Compter sur les doigts", "en": "Counting on your fingers"},
                 "md": {"fr": "De **一** (1) à **十** (10), chaque chiffre s'écrit d'un seul caractère. En Chine, on compte souvent sur une seule main avec des gestes précis de la main.",
                        "en": "From **一** (1) to **十** (10), each number is written with a single character. In China people often count on one hand with specific hand gestures."},
                 "examples": [{"text": "一二三", "fr": "un, deux, trois", "en": "one, two, three"}]},
                {"title": {"fr": "L'âge avec 岁 (suì)", "en": "Age with 岁 (suì)"},
                 "md": {"fr": "Pour dire son âge, on ajoute **岁** (suì) après le nombre : **我十岁** (j'ai dix ans, littéralement « moi dix ans »).",
                        "en": "To say your age, add **岁** (suì) after the number: **我十岁** (I am ten, literally 'I ten years')."},
                 "examples": [{"text": "我十岁", "fr": "J'ai dix ans", "en": "I am ten (years old)"}]},
            ],
            "exercises": [
                {"kind": "dictation", "prompt": {"fr": "Écoute et écris le chiffre en caractères.", "en": "Listen and write the number in characters."}, "audio": "san", "answers": ["三"]},
                {"kind": "mcq", "prompt": {"fr": "Que veut dire 五 ?", "en": "What does 五 mean?"}, "choices": [{"fr": "trois", "en": "three"}, {"fr": "cinq", "en": "five"}, {"fr": "sept", "en": "seven"}], "correct": 1},
                {"kind": "match", "prompt": {"fr": "Associe chaque chiffre à sa valeur.", "en": "Match each number to its value."}, "pairs": [{"a": "一", "fr": "un", "en": "one"}, {"a": "二", "fr": "deux", "en": "two"}, {"a": "三", "fr": "trois", "en": "three"}, {"a": "四", "fr": "quatre", "en": "four"}, {"a": "五", "fr": "cinq", "en": "five"}]},
                {"kind": "order", "prompt": {"fr": "Remets les mots dans l'ordre.", "en": "Put the words in the right order."}, "words": ["我", "十", "岁"], "answers": ["我十岁"]},
                {"kind": "truefalse", "prompt": {"fr": "« 十 » veut dire « dix ».", "en": "'十' means 'ten'."}, "correct": 0},
                {"kind": "translate", "prompt": {"fr": "Traduis : huit.", "en": "Translate: eight."}, "answers": ["八"]},
                {"kind": "mcq", "prompt": {"fr": "Combien font 二 + 三 ?", "en": "How much is 二 + 三?"}, "choices": [{"fr": "四", "en": "四"}, {"fr": "五", "en": "五"}, {"fr": "六", "en": "六"}], "correct": 1},
                {"kind": "speak", "prompt": {"fr": "Compte de 1 à 5 en chinois.", "en": "Count from 1 to 5 in Chinese."}, "model": "yī, èr, sān, sì, wǔ", "audio": "count15"},
            ],
            "cards": [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10],
            "animations": [{"kind": "strokes", "label": {"fr": "Ordre des traits de 一, 二, 三", "en": "Stroke order of 一, 二, 三"}, "char": "三", "reading": "sān"},
                           {"kind": "gesture", "label": {"fr": "Compter de 1 à 5 avec les doigts", "en": "Counting 1 to 5 on one hand"}}],
        },
        "famille": {
            "title": {"fr": "Chinois A0 — Ma famille", "en": "Chinese A0 — My family"},
            "unit": {"title": {"fr": "Papa, maman, et moi", "en": "Dad, mom, and me"}, "minutes": 10, "skills": ["co", "ce", "po"]},
            "vocab": [
                ("家", "jiā", "famille, maison", "family, home", "n", "jia", None),
                ("爸爸", "bàba", "papa", "dad", "n", "baba", None),
                ("妈妈", "māma", "maman", "mom", "n", "mama", None),
                ("哥哥", "gēge", "grand frère", "older brother", "n", "gege", None),
                ("姐姐", "jiějie", "grande sœur", "older sister", "n", "jiejie", None),
                ("弟弟", "dìdi", "petit frère", "younger brother", "n", "didi", None),
                ("妹妹", "mèimei", "petite sœur", "younger sister", "n", "meimei", None),
                ("我", "wǒ", "je, moi", "I, me", "pron", "wo", None),
            ],
            "dialogue": {
                "title": {"fr": "Ma famille", "en": "My family"}, "audio": "d1",
                "lines": [
                    ("A", "这是我爸爸。", "zhè shì wǒ bàba.", "C'est mon papa.", "This is my dad."),
                    ("B", "你好！", "nǐ hǎo!", "Bonjour !", "Hello!"),
                    ("A", "这是我妈妈。", "zhè shì wǒ māma.", "C'est ma maman.", "This is my mom."),
                    ("B", "你弟弟呢？", "nǐ dìdi ne?", "Et ton petit frère ?", "What about your little brother?"),
                    ("A", "我弟弟很小。", "wǒ dìdi hěn xiǎo.", "Mon petit frère est tout petit.", "My little brother is small."),
                    ("B", "家很好！", "jiā hěn hǎo!", "Ta famille est chouette !", "Your family is nice!"),
                ],
            },
            "grammar": [
                {"title": {"fr": "« 我 + nom » = mon / mes", "en": "« 我 + noun » = my"},
                 "md": {"fr": "Le chinois n'a pas de mot pour « mon ». On place simplement **我** (je) devant le nom : **我爸爸** (mon papa), **我妈妈** (ma maman).",
                        "en": "Chinese has no word for 'my'. You just put **我** (I) before the noun: **我爸爸** (my dad), **我妈妈** (my mom)."},
                 "examples": [{"text": "我爸爸", "fr": "mon papa", "en": "my dad"}, {"text": "我妈妈", "fr": "ma maman", "en": "my mom"}]},
                {"title": {"fr": "« 这是… » = c'est…", "en": "« 这是… » = this is…"},
                 "md": {"fr": "**这是** (zhè shì) signifie « c'est » : **这是我爸爸** (c'est mon papa).",
                        "en": "**这是** (zhè shì) means 'this is': **这是我爸爸** (this is my dad)."},
                 "examples": [{"text": "这是我爸爸", "fr": "C'est mon papa", "en": "This is my dad"}]},
            ],
            "exercises": [
                {"kind": "mcq", "prompt": {"fr": "Que veut dire 妈妈 ?", "en": "What does 妈妈 mean?"}, "choices": [{"fr": "papa", "en": "dad"}, {"fr": "maman", "en": "mom"}, {"fr": "frère", "en": "brother"}], "correct": 1},
                {"kind": "match", "prompt": {"fr": "Associe chaque personne.", "en": "Match each person."}, "pairs": [{"a": "爸爸", "fr": "papa", "en": "dad"}, {"a": "妈妈", "fr": "maman", "en": "mom"}, {"a": "哥哥", "fr": "grand frère", "en": "older brother"}, {"a": "姐姐", "fr": "grande sœur", "en": "older sister"}, {"a": "弟弟", "fr": "petit frère", "en": "younger brother"}]},
                {"kind": "truefalse", "prompt": {"fr": "« 我爸爸 » veut dire « mon papa ».", "en": "'我爸爸' means 'my dad'."}, "correct": 0},
                {"kind": "translate", "prompt": {"fr": "Traduis : maman.", "en": "Translate: mom."}, "answers": ["妈妈"]},
                {"kind": "dictation", "prompt": {"fr": "Écoute et écris en caractères.", "en": "Listen and write in characters."}, "audio": "mama", "answers": ["妈妈"]},
                {"kind": "order", "prompt": {"fr": "Remets les mots dans l'ordre.", "en": "Put the words in the right order."}, "words": ["这", "是", "我", "爸爸"], "answers": ["这是我爸爸"]},
                {"kind": "speak", "prompt": {"fr": "Présente ta mère en chinois.", "en": "Introduce your mother in Chinese."}, "model": "这是我妈妈 (zhè shì wǒ māma)", "audio": "mama"},
            ],
            "cards": [0, 1, 2, 3, 4, 5, 6, 7],
            "animations": [{"kind": "strokes", "label": {"fr": "Ordre des traits de 人 (dans 家人)", "en": "Stroke order of 人 (in 家人)"}, "char": "人", "reading": "rén"},
                           {"kind": "strokes", "label": {"fr": "Ordre des traits de 口", "en": "Stroke order of 口"}, "char": "口", "reading": "kǒu"}],
        },
    },
    "ja": {
        "salut": {
            "title": {"fr": "Japonais A0 — Se saluer", "en": "Japanese A0 — Greetings"},
            "unit": {"title": {"fr": "Bonjour, merci, au revoir", "en": "Hello, thank you, goodbye"}, "minutes": 10, "skills": ["co", "ce", "po"]},
            "vocab": [
                ("こんにちは", "こんにちは", "bonjour", "hello", "interj", "konnichiwa", "konnichiwa"),
                ("ありがとう", "ありがとう", "merci", "thank you", "interj", "arigatou", "arigatou"),
                ("さようなら", "さようなら", "au revoir", "goodbye", "interj", "sayounara", "sayounara"),
                ("お願いします", "おねがいします", "s'il te plaît", "please", "interj", "onegaishimasu", "onegai shimasu"),
                ("はい", "はい", "oui", "yes", "interj", "hai", "hai"),
                ("いいえ", "いいえ", "non", "no", "interj", "iie", "iie"),
                ("すみません", "すみません", "excuse-moi, pardon", "excuse me, sorry", "interj", "sumimasen", "sumimasen"),
                ("おはよう", "おはよう", "bonjour (le matin)", "good morning", "interj", "ohayou", "ohayou"),
                ("お元気ですか", "おげんきですか", "comment vas-tu ?", "how are you?", "phrase", "ogenkidesuka", "ogenki desu ka"),
                ("元気です", "げんきです", "je vais bien", "I'm fine", "phrase", "genkidesu", "genki desu"),
            ],
            "dialogue": {
                "title": {"fr": "Une rencontre", "en": "A meeting"}, "audio": "d1",
                "tts": "konnichiwa. konnichiwa. ogenki desu ka. hai, genki desu. arigatou. iie. sayounara. sayounara.",
                "lines": [
                    ("A", "こんにちは。", "こんにちは。", "Bonjour !", "Hello!"),
                    ("B", "こんにちは。", "こんにちは。", "Bonjour !", "Hello!"),
                    ("A", "お元気ですか。", "おげんきですか。", "Comment vas-tu ?", "How are you?"),
                    ("B", "はい、元気です。ありがとう。", "はい、げんきです。ありがとう。", "Je vais bien, merci.", "I'm fine, thank you."),
                    ("A", "いいえ。", "いいえ。", "De rien.", "You're welcome."),
                    ("B", "さようなら。", "さようなら。", "Au revoir !", "Goodbye!"),
                    ("A", "さようなら。", "さようなら。", "Au revoir !", "Goodbye!"),
                ],
            },
            "grammar": [
                {"title": {"fr": "La politesse : です (desu)", "en": "Politeness: です (desu)"},
                 "md": {"fr": "Le japonais est très poli. On termine souvent par **です** (desu) : **元気です** (je vais bien). Les salutations sont des formules fixes : **こんにちは** (bonjour).",
                        "en": "Japanese is very polite. Sentences often end with **です** (desu): **元気です** (I'm fine). Greetings are fixed expressions: **こんにちは** (hello)."},
                 "examples": [{"text": "元気です", "fr": "je vais bien", "en": "I'm fine"}]},
                {"title": {"fr": "La question avec か (ka)", "en": "Questions with か (ka)"},
                 "md": {"fr": "On ajoute **か** (ka) en fin de phrase pour poser une question : **お元気ですか** (comment vas-tu ?).",
                        "en": "Add **か** (ka) at the end to ask a question: **お元気ですか** (how are you?)."},
                 "examples": [{"text": "お元気ですか", "fr": "Comment vas-tu ?", "en": "How are you?"}]},
            ],
            "exercises": [
                {"kind": "dictation", "prompt": {"fr": "Écoute et écris en hiragana.", "en": "Listen and write in hiragana."}, "audio": "arigatou", "answers": ["ありがとう"]},
                {"kind": "mcq", "prompt": {"fr": "Que veut dire さようなら ?", "en": "What does さようなら mean?"}, "choices": [{"fr": "bonjour", "en": "hello"}, {"fr": "merci", "en": "thank you"}, {"fr": "au revoir", "en": "goodbye"}], "correct": 2},
                {"kind": "match", "prompt": {"fr": "Associe chaque mot à son sens.", "en": "Match each word to its meaning."}, "pairs": [{"a": "こんにちは", "fr": "bonjour", "en": "hello"}, {"a": "ありがとう", "fr": "merci", "en": "thank you"}, {"a": "さようなら", "fr": "au revoir", "en": "goodbye"}, {"a": "はい", "fr": "oui", "en": "yes"}, {"a": "いいえ", "fr": "non", "en": "no"}]},
                {"kind": "speak", "prompt": {"fr": "Dis « merci » en japonais.", "en": "Say 'thank you' in Japanese."}, "model": "arigatou (ありがとう)", "audio": "arigatou"},
                {"kind": "order", "prompt": {"fr": "Remets les mots dans l'ordre.", "en": "Put the words in the right order."}, "words": ["はい", "元気", "です"], "answers": ["はい、元気です"]},
                {"kind": "truefalse", "prompt": {"fr": "« ありがとう » veut dire « merci ».", "en": "'ありがとう' means 'thank you'."}, "correct": 0},
                {"kind": "cloze", "prompt": {"fr": "Complète : お元気です__ ? (particule de question).", "en": "Fill in: お元気です__ ? (question particle)."}, "answers": ["か"]},
                {"kind": "translate", "prompt": {"fr": "Traduis : merci.", "en": "Translate: thank you."}, "answers": ["ありがとう"]},
                {"kind": "write", "prompt": {"fr": "Écris « bonjour » en hiragana.", "en": "Write 'hello' in hiragana."}, "model": "こんにちは"},
            ],
            "cards": [0, 1, 2, 4, 5],
            "animations": [{"kind": "strokes", "label": {"fr": "Ordre des traits de こ", "en": "Stroke order of こ"}, "char": "こ", "reading": "ko"},
                           {"kind": "strokes", "label": {"fr": "Ordre des traits de ん", "en": "Stroke order of ん"}, "char": "ん", "reading": "n"},
                           {"kind": "strokes", "label": {"fr": "Ordre des traits de に", "en": "Stroke order of に"}, "char": "に", "reading": "ni"},
                           {"kind": "gesture", "label": {"fr": "Se saluer en s'inclinant (ojigi)", "en": "Greeting with a bow (ojigi)"}}],
        },
        "nombres": {
            "title": {"fr": "Japonais A0 — Les nombres", "en": "Japanese A0 — Numbers"},
            "unit": {"title": {"fr": "Compter de 0 à 10", "en": "Counting from 0 to 10"}, "minutes": 10, "skills": ["co", "ce"]},
            "vocab": [
                ("れい", "れい", "zéro", "zero", "num", "rei", "rei"),
                ("いち", "いち", "un", "one", "num", "ichi", "ichi"),
                ("に", "に", "deux", "two", "num", "ni", "ni"),
                ("さん", "さん", "trois", "three", "num", "san", "san"),
                ("よん", "よん", "quatre", "four", "num", "yon", "yon"),
                ("ご", "ご", "cinq", "five", "num", "go", "go"),
                ("ろく", "ろく", "six", "six", "num", "roku", "roku"),
                ("なな", "なな", "sept", "seven", "num", "nana", "nana"),
                ("はち", "はち", "huit", "eight", "num", "hachi", "hachi"),
                ("きゅう", "きゅう", "neuf", "nine", "num", "kyuu", "kyuu"),
                ("じゅう", "じゅう", "dix", "ten", "num", "juu", "juu"),
            ],
            "dialogue": {
                "title": {"fr": "Compter", "en": "Counting"}, "audio": "d1",
                "tts": "ichi, ni, san. yon, go, roku. nana, hachi, kyuu, juu. nan sai desu ka. juu sai desu. arigatou.",
                "lines": [
                    ("A", "いち、に、さん。", "いち、に、さん。", "Un, deux, trois.", "One, two, three."),
                    ("B", "よん、ご、ろく。", "よん、ご、ろく。", "Quatre, cinq, six.", "Four, five, six."),
                    ("A", "なな、はち、きゅう、じゅう。", "なな、はち、きゅう、じゅう。", "Sept, huit, neuf, dix.", "Seven, eight, nine, ten."),
                    ("B", "何さいですか。", "なんさいですか。", "Quel âge as-tu ?", "How old are you?"),
                    ("A", "十さいです。", "じゅっさいです。", "J'ai dix ans.", "I'm ten."),
                    ("B", "ありがとう。", "ありがとう。", "Merci !", "Thank you!"),
                ],
            },
            "grammar": [
                {"title": {"fr": "Compter en japonais", "en": "Counting in Japanese"},
                 "md": {"fr": "De **いち** (1) à **じゅう** (10), les nombres se disent d'un mot. 4 se dit **よん** (ou し), 7 **なな** (ou しち).",
                        "en": "From **いち** (1) to **じゅう** (10), each number is one word. 4 is **よん** (or し), 7 is **なな** (or しち)."},
                 "examples": [{"text": "いち、に、さん", "fr": "un, deux, trois", "en": "one, two, three"}]},
                {"title": {"fr": "L'âge avec さい (sai)", "en": "Age with さい (sai)"},
                 "md": {"fr": "Pour dire son âge, on ajoute **さい** (sai) après le nombre : **十さい** (dix ans).",
                        "en": "To say your age, add **さい** (sai) after the number: **十さい** (ten years old)."},
                 "examples": [{"text": "十さいです", "fr": "J'ai dix ans", "en": "I am ten"}]},
            ],
            "exercises": [
                {"kind": "dictation", "prompt": {"fr": "Écoute et écris le chiffre en hiragana.", "en": "Listen and write the number in hiragana."}, "audio": "san", "answers": ["さん"]},
                {"kind": "mcq", "prompt": {"fr": "Que veut dire ご ?", "en": "What does ご mean?"}, "choices": [{"fr": "trois", "en": "three"}, {"fr": "cinq", "en": "five"}, {"fr": "sept", "en": "seven"}], "correct": 1},
                {"kind": "match", "prompt": {"fr": "Associe chaque chiffre à sa valeur.", "en": "Match each number to its value."}, "pairs": [{"a": "いち", "fr": "un", "en": "one"}, {"a": "に", "fr": "deux", "en": "two"}, {"a": "さん", "fr": "trois", "en": "three"}, {"a": "よん", "fr": "quatre", "en": "four"}, {"a": "ご", "fr": "cinq", "en": "five"}]},
                {"kind": "order", "prompt": {"fr": "Remets les mots dans l'ordre.", "en": "Put the words in the right order."}, "words": ["十", "さい", "です"], "answers": ["十さいです"]},
                {"kind": "truefalse", "prompt": {"fr": "« じゅう » veut dire « dix ».", "en": "'じゅう' means 'ten'."}, "correct": 0},
                {"kind": "translate", "prompt": {"fr": "Traduis : huit.", "en": "Translate: eight."}, "answers": ["はち"]},
                {"kind": "mcq", "prompt": {"fr": "Combien font に + さん ?", "en": "How much is に + さん?"}, "choices": [{"fr": "よん", "en": "よん"}, {"fr": "ご", "en": "ご"}, {"fr": "ろく", "en": "ろく"}], "correct": 1},
                {"kind": "speak", "prompt": {"fr": "Compte de 1 à 5 en japonais.", "en": "Count from 1 to 5 in Japanese."}, "model": "ichi, ni, san, yon, go", "audio": "count15"},
            ],
            "cards": [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10],
            "animations": [{"kind": "strokes", "label": {"fr": "Ordre des traits de い", "en": "Stroke order of い"}, "char": "い", "reading": "i"},
                           {"kind": "strokes", "label": {"fr": "Ordre des traits de ち", "en": "Stroke order of ち"}, "char": "ち", "reading": "chi"},
                           {"kind": "gesture", "label": {"fr": "Compter de 1 à 5 avec les doigts", "en": "Counting 1 to 5 on one hand"}}],
        },
        "famille": {
            "title": {"fr": "Japonais A0 — Ma famille", "en": "Japanese A0 — My family"},
            "unit": {"title": {"fr": "Papa, maman, et moi", "en": "Dad, mom, and me"}, "minutes": 10, "skills": ["co", "ce", "po"]},
            "vocab": [
                ("家族", "かぞく", "famille", "family", "n", "kazoku", "kazoku"),
                ("お父さん", "おとうさん", "papa", "dad", "n", "otousan", "otousan"),
                ("お母さん", "おかあさん", "maman", "mom", "n", "okaasan", "okaasan"),
                ("お兄さん", "おにいさん", "grand frère", "older brother", "n", "oniisan", "oniisan"),
                ("お姉さん", "おねえさん", "grande sœur", "older sister", "n", "oneesan", "oneesan"),
                ("弟", "おとうと", "petit frère", "younger brother", "n", "otouto", "otouto"),
                ("妹", "いもうと", "petite sœur", "younger sister", "n", "imouto", "imouto"),
                ("私", "わたし", "je, moi", "I, me", "pron", "watashi", "watashi"),
            ],
            "dialogue": {
                "title": {"fr": "Ma famille", "en": "My family"}, "audio": "d1",
                "tts": "kore wa watashi no otousan desu. konnichiwa. kore wa watashi no okaasan desu. otouto san wa. otouto wa chiisai desu. ii kazoku desu.",
                "lines": [
                    ("A", "これは私のお父さんです。", "これはわたしのおとうさんです。", "C'est mon papa.", "This is my dad."),
                    ("B", "こんにちは。", "こんにちは。", "Bonjour !", "Hello!"),
                    ("A", "これは私のお母さんです。", "これはわたしのおかあさんです。", "C'est ma maman.", "This is my mom."),
                    ("B", "弟さんは？", "おとうとさんは？", "Et ton petit frère ?", "What about your little brother?"),
                    ("A", "弟は小さいです。", "おとうとはちいさいです。", "Mon petit frère est tout petit.", "My little brother is small."),
                    ("B", "いい家族です。", "いいかぞくです。", "C'est une chouette famille !", "Nice family!"),
                ],
            },
            "grammar": [
                {"title": {"fr": "La possession avec の (no)", "en": "Possession with の (no)"},
                 "md": {"fr": "On marque la possession avec **の** (no) : **私の** (watashi no) = « mon / ma / mes ».",
                        "en": "Mark possession with **の** (no): **私の** (watashi no) = 'my'."},
                 "examples": [{"text": "私のお父さん", "fr": "mon papa", "en": "my dad"}]},
                {"title": {"fr": "« これは…です » = c'est…", "en": "« これは…です » = this is…"},
                 "md": {"fr": "**これは…です** (kore wa… desu) signifie « c'est… » : **これは私のお父さんです** (c'est mon papa).",
                        "en": "**これは…です** (kore wa… desu) means 'this is…': **これは私のお父さんです** (this is my dad)."},
                 "examples": [{"text": "これは私のお母さんです", "fr": "C'est ma maman", "en": "This is my mom"}]},
            ],
            "exercises": [
                {"kind": "mcq", "prompt": {"fr": "Que veut dire お母さん ?", "en": "What does お母さん mean?"}, "choices": [{"fr": "papa", "en": "dad"}, {"fr": "maman", "en": "mom"}, {"fr": "frère", "en": "brother"}], "correct": 1},
                {"kind": "match", "prompt": {"fr": "Associe chaque personne.", "en": "Match each person."}, "pairs": [{"a": "お父さん", "fr": "papa", "en": "dad"}, {"a": "お母さん", "fr": "maman", "en": "mom"}, {"a": "お兄さん", "fr": "grand frère", "en": "older brother"}, {"a": "お姉さん", "fr": "grande sœur", "en": "older sister"}, {"a": "弟", "fr": "petit frère", "en": "younger brother"}]},
                {"kind": "truefalse", "prompt": {"fr": "« 私の » veut dire « mon ».", "en": "'私の' means 'my'."}, "correct": 0},
                {"kind": "translate", "prompt": {"fr": "Traduis : maman.", "en": "Translate: mom."}, "answers": ["お母さん"]},
                {"kind": "dictation", "prompt": {"fr": "Écoute et écris en hiragana.", "en": "Listen and write in hiragana."}, "audio": "okaasan", "answers": ["お母さん"]},
                {"kind": "order", "prompt": {"fr": "Remets les mots dans l'ordre.", "en": "Put the words in the right order."}, "words": ["これ", "は", "私", "の", "お父さん", "です"], "answers": ["これは私のお父さんです"]},
                {"kind": "speak", "prompt": {"fr": "Présente ta mère en japonais.", "en": "Introduce your mother in Japanese."}, "model": "これは私のお母さんです (kore wa watashi no okaasan desu)", "audio": "okaasan"},
            ],
            "cards": [0, 1, 2, 3, 4, 5, 6, 7],
            "animations": [{"kind": "strokes", "label": {"fr": "Ordre des traits de あ", "en": "Stroke order of あ"}, "char": "あ", "reading": "a"},
                           {"kind": "strokes", "label": {"fr": "Ordre des traits de り", "en": "Stroke order of り"}, "char": "り", "reading": "ri"}],
        },
    },
}


# ---------------------------------------------------------------------------------------------- traits (indicatifs)
# Ordre des traits INDICATIF, chemins SVG simples (à faire valider par un natif).
STROKES = {
    "zh": {
        "一": ["M30 60 L90 60"],
        "二": ["M30 45 L90 45", "M30 75 L90 75"],
        "三": ["M30 35 L90 35", "M30 60 L90 60", "M30 85 L90 85"],
        "人": ["M58 22 L30 95", "M58 22 L86 95"],
        "口": ["M35 30 L35 90", "M35 30 L85 30 L85 90", "M35 90 L85 90"],
        "你": ["M42 18 L32 46", "M44 26 L44 80", "M60 18 L50 42", "M62 22 L86 22", "M72 22 L72 60", "M62 46 L52 72", "M80 46 L82 58"],
        "好": ["M34 20 L28 48 L46 62", "M52 22 L34 62", "M26 48 L54 48", "M66 24 L92 24 L66 44", "M80 32 L80 88", "M64 56 L94 56"],
        "再": ["M30 28 L90 28", "M52 20 L48 80", "M64 28 L90 62", "M64 62 L90 66", "M30 78 L90 78", "M52 70 L48 96"],
    },
    "ja": {
        "こ": ["M25 40 L75 40", "M75 40 L75 72 L25 72 L25 90"],
        "ん": ["M25 90 C32 60 70 50 58 78 C50 96 85 94 85 78"],
        "に": ["M32 26 L46 52", "M46 52 L86 52", "M28 84 L82 84"],
        "い": ["M40 24 L40 78", "M66 24 L66 70 C66 86 50 86 50 72"],
        "ち": ["M25 52 L72 52", "M72 52 L72 90 C60 96 42 90 46 72"],
        "あ": ["M28 48 L84 48", "M56 34 L56 92 C40 92 38 62 56 62", "M56 78 C68 86 76 94 56 94"],
        "り": ["M44 22 L44 80 C44 92 26 92 26 80", "M68 22 L68 80 C68 92 50 92 50 80"],
    },
}


# ---------------------------------------------------------------------------------------------- figures (gestes)
def gesture_block(lang, theme, src):
    """Petites figures vectorielles (pas de personne réelle). Retourne un bloc « illustration ». """
    L = {"fr": "fr", "en": "en"}[src]
    if theme == "nombres":
        cap = {"fr": "Compter de 1 à 5", "en": "Counting from 1 to 5"}[L]
        alt = {"fr": "Cinq cases numérotées de un à cinq avec un, deux, trois, quatre puis cinq points.",
               "en": "Five boxes numbered one to five with one, two, three, four then five dots."}[L]
        a = animlib.Anim(480, 200, mode="steps")
        say = {"fr": "On lève un doigt pour un, deux doigts pour deux, et ainsi de suite.",
               "en": "Raise one finger for one, two fingers for two, and so on."}[L]
        for k in range(1, 6):
            x = 40 + (k - 1) * 90
            a.add(animlib.rect(x, 60, 70, 70, "lightblue", "blue", 2, 8), id=f"b{k}", alpha=0)
            a.add(animlib.text(x + 35, 40, str(k), 22, "blue", bold=True), id=f"n{k}", alpha=0)
            dots = [a.add(animlib.circle(x + 18 + (d % 3) * 18, 82 + (d // 3) * 18, 5, "ink", None), id=f"p{k}d{d}", alpha=0) for d in range(k)]
            with a.step(f"{k}." if L == "fr" else f"{k}.") as s:
                s.show(f"b{k}", 0.3); s.show(f"n{k}", 0.3)
                for d in dots:
                    s.show(d, 0.2, delay=0.1)
        return a.block(alt, cap)
    if theme == "salut":
        cap = {"fr": "Se saluer en s'inclinant (ojigi)", "en": "Greeting with a bow (ojigi)"}[L]
        alt = {"fr": "Un personnage schématique s'incline pour saluer.",
               "en": "A simple figure bows to greet."}[L]
        say = {"fr": "Au Japon, on salue en s'inclinant légèrement.", "en": "In Japan, people greet by bowing slightly."}[L]
        a = animlib.Anim(360, 260, mode="steps")
        a.add(animlib.circle(180, 60, 22, None, "ink", 3), id="head")
        a.add(animlib.line(180, 82, 180, 160, "ink", 3), id="body")
        a.add(animlib.line(180, 160, 150, 200, "ink", 3), id="lleg")
        a.add(animlib.line(180, 160, 210, 200, "ink", 3), id="rleg")
        arms = animlib.line(180, 110, 130, 90, "ink", 3)
        a.add(arms, id="arms", pivot=(180, 110))
        with a.step(say) as s:
            s.rotate("body", -12, 1.0, 0, "inout"); s.rotate("head", -12, 1.0, 0, "inout")
            s.rotate("lleg", -12, 1.0, 0, "inout"); s.rotate("rleg", -12, 1.0, 0, "inout"); s.rotate("arms", -12, 1.0, 0, "inout")
        return a.block(alt, cap)
    return None


# ---------------------------------------------------------------------------------------------- génération
def wav_ms(path):
    with wave.open(path) as w:
        return int(1000 * w.getnframes() / w.getframerate())


def synth_opus(text, lang, dst):
    """espeak-ng -> WAV -> Opus 16 kHz mono. Retourne (bytes, ms)."""
    voice = VOICE[lang][0]
    with tempfile.TemporaryDirectory() as t:
        wav = os.path.join(t, "x.wav")
        subprocess.run(["espeak-ng", "-v", voice, "-s", "110", text, "-w", wav], check=True)
        ms = wav_ms(wav)
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", wav, "-ac", "1", "-ar", "16000",
                        "-af", "loudnorm=I=-16:TP=-1.5:LRA=11", "-c:a", "libopus", "-b:a", "24k", dst], check=True)
        return os.path.getsize(dst), ms


def mid(lang, theme, slug):
    """Identifiant média stable et unique par lot : les pistes communes à un thème (dialogue, comptage) portent le thème."""
    if slug in ("d1", "count15"):
        return f"{lang}-{theme}-{slug}"
    return f"{lang}-{slug}"


def audio_entries(lang, theme):
    """Liste des pistes à générer : (mid, texte à synthétiser). Le texte ja est du romaji, le zh des caractères."""
    th = CONTENT[lang][theme]
    out = []
    for v in th["vocab"]:
        term, reading, _fr, _en, _pos, slug, rom = v
        tts = rom if lang == "ja" and rom else term
        out.append((mid(lang, theme, slug), tts))
    out.append((mid(lang, theme, "d1"), dialogue_tts(lang, th)))
    for ex in th["exercises"]:
        a = ex.get("audio")
        if not a:
            continue
        m = mid(lang, theme, a)
        if a == "count15":
            txt = "一二三四五" if lang == "zh" else "ichi ni san yon go"
            out.append((m, txt))
        elif m not in {i for i, _ in out}:
            out.append((m, vocab_tts(lang, th, a)))
    return out


def vocab_tts(lang, th, slug):
    for v in th["vocab"]:
        if v[5] == slug:
            return v[6] if lang == "ja" and v[6] else v[0]
    return slug


def dialogue_tts(lang, th):
    if "tts" in th["dialogue"]:
        return th["dialogue"]["tts"]
    if lang == "ja":
        return " ".join(romaji_line(line[1]) for line in th["dialogue"]["lines"])
    return "。".join(line[1].rstrip("。！") for line in th["dialogue"]["lines"])


def romaji_line(text):
    return text


def build_media(lang, theme):
    """Génère les Opus et le manifeste media.json (partagé fr/en). Retourne le manifeste (liste de dict)."""
    scope = f"{lang}-{SCOPE_LEVEL}-{theme}"
    os.makedirs(os.path.join(MEDIA_DIR, scope, "audio"), exist_ok=True)
    media = []
    for m, tts in audio_entries(lang, theme):
        rel = f"audio/{m}.opus"
        dst = os.path.join(MEDIA_DIR, scope, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        b, ms = synth_opus(tts, lang, dst)
        media.append({"id": m, "file": rel, "kind": "audio", "bytes": b, "durationMs": ms,
                      "license": "CASTBRIDGE-ORIGINAL", "engine": f"{ENGINE} (voix {VOICE[lang][0]})",
                      "synthetic": True, "voiceLicense": "GPL-3.0", "lang": lang,
                      "source": "CastBridge, voix de synthèse libre (GPL-3.0, moteur uniquement)"})
    return media


def unit_json(lang, theme, src):
    th = CONTENT[lang][theme]
    scope = f"{lang}-{SCOPE_LEVEL}-{theme}-{src}"
    L = "fr" if src == "fr" else "en"
    u = th["unit"]

    vocab = []
    for i, v in enumerate(th["vocab"], 1):
        term, reading, _fr, _en, pos, slug, _rom = v
        vocab.append({"id": f"{scope}-v{i}", "term": term, "reading": reading,
                      "gloss": _fr if src == "fr" else _en, "pos": pos, "audio": f"m:{lang}-{slug}"})

    d = th["dialogue"]
    lines = [{"who": w, "text": t, "reading": r, "tr": fr if src == "fr" else en} for w, t, r, fr, en in d["lines"]]
    dialogue = [{"id": f"{scope}-d1", "title": d["title"][L], "lines": lines, "audio": f"m:{mid(lang, theme, 'd1')}"}]

    grammar = []
    for i, g in enumerate(th["grammar"], 1):
        examples = [{"text": e["text"], "tr": e[src]} for e in g["examples"]]
        grammar.append({"id": f"{scope}-g{i}", "title": g["title"][L], "md": g["md"][L], "examples": examples})

    exercises = []
    for i, x in enumerate(th["exercises"], 1):
        ex = {"id": f"{scope}-x{i}", "kind": x["kind"], "prompt": x["prompt"][L]}
        if x.get("audio"):
            ex["audio"] = f"m:{mid(lang, theme, x['audio'])}"
        if x.get("answers"):
            ex["answers"] = x["answers"]
        if x.get("choices"):
            ex["choices"] = [c[src] for c in x["choices"]]
        if x.get("correct") is not None:
            ex["correct"] = x["correct"]
        if x.get("pairs"):
            ex["pairs"] = [{"a": p["a"], "b": p[src]} for p in x["pairs"]]
        if x.get("words"):
            ex["words"] = x["words"]
        if x.get("model"):
            ex["model"] = x["model"]
        exercises.append(ex)

    cards = [{"id": f"{scope}-c{i}", "vocab": f"{scope}-v{vocab_idx + 1}"} for i, vocab_idx in enumerate(th["cards"], 1)]

    animations = []
    for i, a in enumerate(th["animations"], 1):
        animations.append({"id": f"{scope}-a{i}", "kind": a["kind"], "label": a["label"][L]})

    return {
        "id": f"{scope}-u1", "title": u["title"][L], "minutes": u["minutes"], "skills": u["skills"], "prerequisites": [],
        "vocab": vocab, "dialogues": dialogue, "grammar": grammar, "exercises": exercises,
        "stories": [], "cards": cards, "animations": animations,
    }


def pack_json(lang, theme, src, media):
    scope = f"{lang}-{SCOPE_LEVEL}-{theme}-{src}"
    th = CONTENT[lang][theme]
    L = "fr" if src == "fr" else "en"
    return {
        "format": 1, "type": "langue", "id": scope, "version": 1,
        "target": lang, "level": SCOPE_LEVEL, "theme": theme, "source": src,
        "title": th["title"][L], "state": "review",
        "units": [unit_json(lang, theme, src)],
    }


def write_figures(lang, theme, src):
    """Écrit les blocs « illustration » (traits + gestes) sous figures/<id>.json."""
    scope = f"{lang}-{SCOPE_LEVEL}-{theme}-{src}"
    th = CONTENT[lang][theme]
    L = "fr" if src == "fr" else "en"
    dstdir = os.path.join(CONTENT_DIR, scope, "figures")
    os.makedirs(dstdir, exist_ok=True)
    for i, a in enumerate(th["animations"], 1):
        aid = f"{scope}-a{i}"
        if a["kind"] == "strokes":
            strokes = STROKES[lang].get(a["char"], [a["char"]])
            block = stroke_block(strokes, a["char"], a["reading"], a["label"][L], L)
        else:
            block = gesture_block(lang, theme, src)
            if block is None:
                continue
        with open(os.path.join(dstdir, f"{aid}.json"), "w", encoding="utf-8") as f:
            json.dump(block, f, ensure_ascii=False, indent=1)


def stroke_block(strokes, char, reading, caption, lang):
    alt_fr = f"Caractère {char} tracé trait par trait dans l'ordre (ordre indicatif, à faire valider par un natif)."
    alt_en = f"The character {char} drawn stroke by stroke in order (indicative order, to be checked by a native speaker)."
    a = animlib.Anim(360, 260, mode="steps")
    a.add(animlib.rect(130, 20, 120, 120, None, "lightgrey", 2))
    a.add(animlib.text(190, 190, char, 30, bold=True))
    a.add(animlib.text(190, 225, reading, 16, "grey"))
    for k, d in enumerate(strokes, 1):
        pid = a.add(animlib.path(d, None, "ink", 3), draw=0)
        say = f"Trait {k}." if lang == "fr" else f"Stroke {k}."
        with a.step(say) as s:
            s.draw(pid, 0.7)
    return a.block(alt_fr if lang == "fr" else alt_en, caption)


def write_pack(lang, theme, src, media):
    scope = f"{lang}-{SCOPE_LEVEL}-{theme}-{src}"
    dstdir = os.path.join(CONTENT_DIR, scope)
    os.makedirs(dstdir, exist_ok=True)
    with open(os.path.join(dstdir, "langue.json"), "w", encoding="utf-8") as f:
        json.dump(pack_json(lang, theme, src, media), f, ensure_ascii=False, indent=1)
    with open(os.path.join(dstdir, "media.json"), "w", encoding="utf-8") as f:
        json.dump({"format": 1, "comment": "Médias du lot texte (référencés) ; les fichiers vivent dans le lot média jumeau.",
                   "media": media}, f, ensure_ascii=False, indent=1)
    write_figures(lang, theme, src)


def main():
    if not shutil.which("espeak-ng"):
        sys.exit("espeak-ng manquant (brew install espeak-ng)")
    if not shutil.which("ffmpeg"):
        sys.exit("ffmpeg manquant")
    totals = {}
    for lang in ("zh", "ja"):
        for theme in ("salut", "nombres", "famille"):
            media = build_media(lang, theme)
            for src in ("fr", "en"):
                write_pack(lang, theme, src, media)
            totals[f"{lang}-{theme}"] = (len(media), sum(m["bytes"] for m in media))
    print("Packs générés :")
    for k, (n, b) in totals.items():
        print(f"  {k}: {n} pistes, {b/1024:.1f} Ko")


if __name__ == "__main__":
    main()
