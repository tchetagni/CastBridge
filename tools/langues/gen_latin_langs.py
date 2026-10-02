#!/usr/bin/env python3
"""Génère les packs « Langues » A0 des 5 langues latines : anglais, allemand, espagnol, italien, français
(le chinois et le japonais sont produits par gen_a0_pilot.py et gen_zh_prononciation.py).

3 thèmes (salut, nombres, famille) x sources utiles (en←fr, fr←en, de/es/it←fr+en) = 24 packs texte.
Multimédia : audio Opus (espeak-ng, marqué synthétique) + figures vectorielles (geste de salut, comptage).
Tout est « free » (contenu original CastBridge, publié CC BY-SA 4.0). Exécution : python3 tools/langues/gen_latin_langs.py
"""
import json, os, re, shutil, subprocess, sys, tempfile, wave

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "anim"))
import animlib  # noqa: E402

CONTENT_DIR = os.path.join(ROOT, "content", "langues")
MEDIA_DIR = os.path.join(ROOT, "content", "langues-media")
LEVEL = "a0"
ENGINE = "espeak-ng 1.52"
VOICE = {"en": "en", "de": "de", "es": "es", "it": "it", "fr": "fr"}


def slug(term):
    s = term.lower()
    s = re.sub(r"[^a-z0-9]+", "", s)
    return s or "w"


# vocab : (term, gloss_fr, gloss_en, pos)
CONTENT = {
    "en": {
        "name": {"fr": "Anglais", "en": "English"}, "sources": ["fr"],
        "salut": {
            "title": {"fr": "Anglais A0 — Se saluer", "en": "English A0 — Greetings"},
            "unit": {"fr": "Bonjour, merci, au revoir", "en": "Hello, thank you, goodbye"},
            "vocab": [("Hello", "bonjour", "hello", "interj"), ("Hi", "salut", "hi", "interj"), ("Good morning", "bonjour (le matin)", "good morning", "interj"),
                      ("Goodbye", "au revoir", "goodbye", "interj"), ("Thank you", "merci", "thank you", "interj"), ("Please", "s'il te plaît", "please", "interj"),
                      ("Sorry", "pardon", "sorry", "interj"), ("Yes", "oui", "yes", "interj"), ("No", "non", "no", "interj"), ("How are you?", "comment vas-tu ?", "how are you?", "phrase")],
            "dialogue": {"fr": "Une rencontre", "en": "A meeting",
                         "lines": [("A", "Hello!", "Bonjour !", "Hello!"), ("B", "Hi!", "Salut !", "Hi!"), ("A", "How are you?", "Comment vas-tu ?", "How are you?"),
                                   ("B", "I'm fine, thank you.", "Je vais bien, merci.", "I'm fine, thank you."), ("A", "Goodbye!", "Au revoir !", "Goodbye!"), ("B", "Goodbye!", "Au revoir !", "Goodbye!")]},
            "grammar": [{"fr": "L'ordre des mots", "en": "Word order",
                         "md_fr": "L'anglais suit l'ordre **sujet – verbe – complément** : *I am fine* (je suis bien). La question inverse le sujet : *How are you?*",
                         "md_en": "English follows **subject – verb – object** order: *I am fine*. Questions invert the subject: *How are you?*",
                         "ex": [("I am fine", "je vais bien", "I am fine")]}],
            "fact": ("« Thank you » veut dire « merci ».", "'Thank you' means 'thank you'."),
        },
        "nombres": {
            "title": {"fr": "Anglais A0 — Les nombres", "en": "English A0 — Numbers"},
            "unit": {"fr": "Compter de 0 à 10", "en": "Counting from 0 to 10"},
            "vocab": [("zero", "zéro", "zero", "num"), ("one", "un", "one", "num"), ("two", "deux", "two", "num"), ("three", "trois", "three", "num"),
                      ("four", "quatre", "four", "num"), ("five", "cinq", "five", "num"), ("six", "six", "six", "num"), ("seven", "sept", "seven", "num"),
                      ("eight", "huit", "eight", "num"), ("nine", "neuf", "nine", "num"), ("ten", "dix", "ten", "num")],
            "dialogue": {"fr": "Compter", "en": "Counting",
                         "lines": [("A", "One, two, three.", "Un, deux, trois.", "One, two, three."), ("B", "Four, five, six.", "Quatre, cinq, six.", "Four, five, six."),
                                   ("A", "Seven, eight, nine, ten.", "Sept, huit, neuf, dix.", "Seven, eight, nine, ten.")]},
            "grammar": [{"fr": "L'âge avec « years old »", "en": "Age with 'years old'",
                         "md_fr": "Pour dire son âge : **I am ten years old** (j'ai dix ans).",
                         "md_en": "To state your age: **I am ten years old**.",
                         "ex": [("I am ten", "j'ai dix ans", "I am ten")]}],
            "fact": ("« ten » veut dire « dix ».", "'ten' means 'ten'."),
        },
        "famille": {
            "title": {"fr": "Anglais A0 — Ma famille", "en": "English A0 — My family"},
            "unit": {"fr": "Papa, maman, et moi", "en": "Dad, mom, and me"},
            "vocab": [("family", "famille", "family", "n"), ("father", "père", "father", "n"), ("mother", "mère", "mother", "n"),
                      ("brother", "frère", "brother", "n"), ("sister", "sœur", "sister", "n"), ("grandfather", "grand-père", "grandfather", "n"),
                      ("grandmother", "grand-mère", "grandmother", "n"), ("I", "je, moi", "I, me", "pron")],
            "dialogue": {"fr": "Ma famille", "en": "My family",
                         "lines": [("A", "This is my father.", "C'est mon père.", "This is my father."), ("B", "Hello!", "Bonjour !", "Hello!"),
                                   ("A", "This is my mother.", "C'est ma mère.", "This is my mother."), ("B", "Nice family!", "Chouette famille !", "Nice family!")]},
            "grammar": [{"fr": "« my » = mon / ma / mes", "en": "'my' = my",
                         "md_fr": "**My** ne change pas : *my father* (mon père), *my mother* (ma mère).",
                         "md_en": "**My** never changes: *my father*, *my mother*.",
                         "ex": [("my father", "mon père", "my father")]}],
            "fact": ("« my father » veut dire « mon père ».", "'my father' means 'my father'."),
        },
    },
    "de": {
        "name": {"fr": "Allemand", "en": "German"}, "sources": ["fr", "en"],
        "salut": {
            "title": {"fr": "Allemand A0 — Se saluer", "en": "German A0 — Greetings"},
            "unit": {"fr": "Bonjour, merci, au revoir", "en": "Hello, thank you, goodbye"},
            "vocab": [("Hallo", "bonjour", "hello", "interj"), ("Guten Morgen", "bonjour (le matin)", "good morning", "interj"), ("Danke", "merci", "thank you", "interj"),
                      ("Bitte", "s'il te plaît / de rien", "please / you're welcome", "interj"), ("Auf Wiedersehen", "au revoir", "goodbye", "interj"),
                      ("Tschüss", "salut (au revoir)", "bye", "interj"), ("Ja", "oui", "yes", "interj"), ("Nein", "non", "no", "interj"),
                      ("Entschuldigung", "pardon", "sorry", "interj"), ("Wie geht es dir?", "comment vas-tu ?", "how are you?", "phrase")],
            "dialogue": {"fr": "Une rencontre", "en": "A meeting",
                         "lines": [("A", "Hallo!", "Bonjour !", "Hello!"), ("B", "Guten Morgen!", "Bonjour !", "Good morning!"), ("A", "Wie geht es dir?", "Comment vas-tu ?", "How are you?"),
                                   ("B", "Gut, danke.", "Bien, merci.", "Fine, thanks."), ("A", "Auf Wiedersehen!", "Au revoir !", "Goodbye!"), ("B", "Tschüss!", "Salut !", "Bye!")]},
            "grammar": [{"fr": "Les noms prennent une majuscule", "en": "Nouns are capitalized",
                         "md_fr": "En allemand, **tous les noms** prennent une majuscule : *Morgen*, *Wiedersehen*.",
                         "md_en": "In German, **all nouns** are capitalized: *Morgen*, *Wiedersehen*.",
                         "ex": [("Guten Morgen", "bonjour", "good morning")]}],
            "fact": ("« Danke » veut dire « merci ».", "'Danke' means 'thank you'."),
        },
        "nombres": {
            "title": {"fr": "Allemand A0 — Les nombres", "en": "German A0 — Numbers"},
            "unit": {"fr": "Compter de 0 à 10", "en": "Counting from 0 to 10"},
            "vocab": [("null", "zéro", "zero", "num"), ("eins", "un", "one", "num"), ("zwei", "deux", "two", "num"), ("drei", "trois", "three", "num"),
                      ("vier", "quatre", "four", "num"), ("fünf", "cinq", "five", "num"), ("sechs", "six", "six", "num"), ("sieben", "sept", "seven", "num"),
                      ("acht", "huit", "eight", "num"), ("neun", "neuf", "nine", "num"), ("zehn", "dix", "ten", "num")],
            "dialogue": {"fr": "Compter", "en": "Counting",
                         "lines": [("A", "Eins, zwei, drei.", "Un, deux, trois.", "One, two, three."), ("B", "Vier, fünf, sechs.", "Quatre, cinq, six.", "Four, five, six."),
                                   ("A", "Sieben, acht, neun, zehn.", "Sept, huit, neuf, dix.", "Seven, eight, nine, ten.")]},
            "grammar": [{"fr": "Compter en allemand", "en": "Counting in German",
                         "md_fr": "De **eins** (1) à **zehn** (10), chaque nombre est un mot. Le « s » de *sechs* se prononce comme un « z ».",
                         "md_en": "From **eins** (1) to **zehn** (10), each number is one word. The 's' in *sechs* sounds like 'z'.",
                         "ex": [("eins, zwei, drei", "un, deux, trois", "one, two, three")]}],
            "fact": ("« zehn » veut dire « dix ».", "'zehn' means 'ten'."),
        },
        "famille": {
            "title": {"fr": "Allemand A0 — Ma famille", "en": "German A0 — My family"},
            "unit": {"fr": "Papa, maman, et moi", "en": "Dad, mom, and me"},
            "vocab": [("Familie", "famille", "family", "n"), ("Vater", "père", "father", "n"), ("Mutter", "mère", "mother", "n"),
                      ("Bruder", "frère", "brother", "n"), ("Schwester", "sœur", "sister", "n"), ("Großvater", "grand-père", "grandfather", "n"),
                      ("Großmutter", "grand-mère", "grandmother", "n"), ("ich", "je, moi", "I, me", "pron")],
            "dialogue": {"fr": "Ma famille", "en": "My family",
                         "lines": [("A", "Das ist mein Vater.", "C'est mon père.", "This is my father."), ("B", "Hallo!", "Bonjour !", "Hello!"),
                                   ("A", "Das ist meine Mutter.", "C'est ma mère.", "This is my mother."), ("B", "Schöne Familie!", "Chouette famille !", "Nice family!")]},
            "grammar": [{"fr": "mein / meine = mon / ma", "en": "mein / meine = my",
                         "md_fr": "**Mein** varie : *mein Vater* (masculin), *meine Mutter* (féminin).",
                         "md_en": "**Mein** agrees with the noun: *mein Vater* (masc.), *meine Mutter* (fem.).",
                         "ex": [("mein Vater", "mon père", "my father"), ("meine Mutter", "ma mère", "my mother")]}],
            "fact": ("« mein Vater » veut dire « mon père ».", "'mein Vater' means 'my father'."),
        },
    },
    "es": {
        "name": {"fr": "Espagnol", "en": "Spanish"}, "sources": ["fr", "en"],
        "salut": {
            "title": {"fr": "Espagnol A0 — Se saluer", "en": "Spanish A0 — Greetings"},
            "unit": {"fr": "Bonjour, merci, au revoir", "en": "Hello, thank you, goodbye"},
            "vocab": [("Hola", "bonjour / salut", "hello / hi", "interj"), ("Buenos días", "bonjour (le matin)", "good morning", "interj"), ("Gracias", "merci", "thank you", "interj"),
                      ("Por favor", "s'il te plaît", "please", "interj"), ("Adiós", "au revoir", "goodbye", "interj"), ("Sí", "oui", "yes", "interj"),
                      ("No", "non", "no", "interj"), ("Perdón", "pardon", "sorry", "interj"), ("¿Cómo estás?", "comment vas-tu ?", "how are you?", "phrase")],
            "dialogue": {"fr": "Une rencontre", "en": "A meeting",
                         "lines": [("A", "¡Hola!", "Bonjour !", "Hello!"), ("B", "¡Buenos días!", "Bonjour !", "Good morning!"), ("A", "¿Cómo estás?", "Comment vas-tu ?", "How are you?"),
                                   ("B", "Bien, gracias.", "Bien, merci.", "Fine, thanks."), ("A", "¡Adiós!", "Au revoir !", "Goodbye!")]},
            "grammar": [{"fr": "¿ et ¡ : la ponctuation inversée", "en": "¿ and ¡ : inverted punctuation",
                         "md_fr": "L'espagnol ouvre les questions par **¿** et les exclamations par **¡** : *¿Cómo estás?*, *¡Hola!*.",
                         "md_en": "Spanish opens questions with **¿** and exclamations with **¡**: *¿Cómo estás?*, *¡Hola!*.",
                         "ex": [("¿Cómo estás?", "comment vas-tu ?", "how are you?")]}],
            "fact": ("« Gracias » veut dire « merci ».", "'Gracias' means 'thank you'."),
        },
        "nombres": {
            "title": {"fr": "Espagnol A0 — Les nombres", "en": "Spanish A0 — Numbers"},
            "unit": {"fr": "Compter de 0 à 10", "en": "Counting from 0 to 10"},
            "vocab": [("cero", "zéro", "zero", "num"), ("uno", "un", "one", "num"), ("dos", "deux", "two", "num"), ("tres", "trois", "three", "num"),
                      ("cuatro", "quatre", "four", "num"), ("cinco", "cinq", "five", "num"), ("seis", "six", "six", "num"), ("siete", "sept", "seven", "num"),
                      ("ocho", "huit", "eight", "num"), ("nueve", "neuf", "nine", "num"), ("diez", "dix", "ten", "num")],
            "dialogue": {"fr": "Compter", "en": "Counting",
                         "lines": [("A", "Uno, dos, tres.", "Un, deux, trois.", "One, two, three."), ("B", "Cuatro, cinco, seis.", "Quatre, cinq, six.", "Four, five, six."),
                                   ("A", "Siete, ocho, nueve, diez.", "Sept, huit, neuf, dix.", "Seven, eight, nine, ten.")]},
            "grammar": [{"fr": "Compter en espagnol", "en": "Counting in Spanish",
                         "md_fr": "De **uno** (1) à **diez** (10). Le « c » de *cinco* se dit comme un « k » anglais.",
                         "md_en": "From **uno** (1) to **diez** (10). The 'c' in *cinco* sounds like an English 'k'.",
                         "ex": [("uno, dos, tres", "un, deux, trois", "one, two, three")]}],
            "fact": ("« diez » veut dire « dix ».", "'diez' means 'ten'."),
        },
        "famille": {
            "title": {"fr": "Espagnol A0 — Ma famille", "en": "Spanish A0 — My family"},
            "unit": {"fr": "Papa, maman, et moi", "en": "Dad, mom, and me"},
            "vocab": [("familia", "famille", "family", "n"), ("padre", "père", "father", "n"), ("madre", "mère", "mother", "n"),
                      ("hermano", "frère", "brother", "n"), ("hermana", "sœur", "sister", "n"), ("abuelo", "grand-père", "grandfather", "n"),
                      ("abuela", "grand-mère", "grandmother", "n"), ("yo", "je, moi", "I, me", "pron")],
            "dialogue": {"fr": "Ma famille", "en": "My family",
                         "lines": [("A", "Este es mi padre.", "C'est mon père.", "This is my father."), ("B", "¡Hola!", "Bonjour !", "Hello!"),
                                   ("A", "Esta es mi madre.", "C'est ma mère.", "This is my mother."), ("B", "¡Bonita familia!", "Chouette famille !", "Nice family!")]},
            "grammar": [{"fr": "mi = mon / ma / mes", "en": "mi = my",
                         "md_fr": "**Mi** ne change pas : *mi padre*, *mi madre*. Le genre apparaît dans *este / esta*.",
                         "md_en": "**Mi** never changes: *mi padre*, *mi madre*. Gender shows in *este / esta*.",
                         "ex": [("mi padre", "mon père", "my father"), ("mi madre", "ma mère", "my mother")]}],
            "fact": ("« mi padre » veut dire « mon père ».", "'mi padre' means 'my father'."),
        },
    },
    "it": {
        "name": {"fr": "Italien", "en": "Italian"}, "sources": ["fr", "en"],
        "salut": {
            "title": {"fr": "Italien A0 — Se saluer", "en": "Italian A0 — Greetings"},
            "unit": {"fr": "Bonjour, merci, au revoir", "en": "Hello, thank you, goodbye"},
            "vocab": [("Ciao", "salut / bonjour", "hi / hello", "interj"), ("Buongiorno", "bonjour (le matin)", "good morning", "interj"), ("Grazie", "merci", "thank you", "interj"),
                      ("Per favore", "s'il te plaît", "please", "interj"), ("Arrivederci", "au revoir", "goodbye", "interj"), ("Sì", "oui", "yes", "interj"),
                      ("No", "non", "no", "interj"), ("Scusa", "pardon", "sorry", "interj"), ("Come stai?", "comment vas-tu ?", "how are you?", "phrase")],
            "dialogue": {"fr": "Une rencontre", "en": "A meeting",
                         "lines": [("A", "Ciao!", "Salut !", "Hi!"), ("B", "Buongiorno!", "Bonjour !", "Good morning!"), ("A", "Come stai?", "Comment vas-tu ?", "How are you?"),
                                   ("B", "Bene, grazie.", "Bien, merci.", "Fine, thanks."), ("A", "Arrivederci!", "Au revoir !", "Goodbye!")]},
            "grammar": [{"fr": "Les consonnes doubles comptent", "en": "Double consonants matter",
                         "md_fr": "Une consonne double se prononce plus longue : *bella* (belle) ≠ *bela*. Le « c » de *ciao* se dit « tch ».",
                         "md_en": "A double consonant is longer: *bella* (beautiful) ≠ *bela*. The 'c' in *ciao* sounds like 'ch'.",
                         "ex": [("Ciao", "salut", "hi")]}],
            "fact": ("« Grazie » veut dire « merci ».", "'Grazie' means 'thank you'."),
        },
        "nombres": {
            "title": {"fr": "Italien A0 — Les nombres", "en": "Italian A0 — Numbers"},
            "unit": {"fr": "Compter de 0 à 10", "en": "Counting from 0 to 10"},
            "vocab": [("zero", "zéro", "zero", "num"), ("uno", "un", "one", "num"), ("due", "deux", "two", "num"), ("tre", "trois", "three", "num"),
                      ("quattro", "quatre", "four", "num"), ("cinque", "cinq", "five", "num"), ("sei", "six", "six", "num"), ("sette", "sept", "seven", "num"),
                      ("otto", "huit", "eight", "num"), ("nove", "neuf", "nine", "num"), ("dieci", "dix", "ten", "num")],
            "dialogue": {"fr": "Compter", "en": "Counting",
                         "lines": [("A", "Uno, due, tre.", "Un, deux, trois.", "One, two, three."), ("B", "Quattro, cinque, sei.", "Quatre, cinq, six.", "Four, five, six."),
                                   ("A", "Sette, otto, nove, dieci.", "Sept, huit, neuf, dix.", "Seven, eight, nine, ten.")]},
            "grammar": [{"fr": "Compter en italien", "en": "Counting in Italian",
                         "md_fr": "De **uno** (1) à **dieci** (10). *cinque* s'écrit avec « qu », *sette* et *otto* doublent la consonne.",
                         "md_en": "From **uno** (1) to **dieci** (10). *cinque* uses 'qu', *sette* and *otto* double the consonant.",
                         "ex": [("uno, due, tre", "un, deux, trois", "one, two, three")]}],
            "fact": ("« dieci » veut dire « dix ».", "'dieci' means 'ten'."),
        },
        "famille": {
            "title": {"fr": "Italien A0 — Ma famille", "en": "Italian A0 — My family"},
            "unit": {"fr": "Papa, maman, et moi", "en": "Dad, mom, and me"},
            "vocab": [("famiglia", "famille", "family", "n"), ("padre", "père", "father", "n"), ("madre", "mère", "mother", "n"),
                      ("fratello", "frère", "brother", "n"), ("sorella", "sœur", "sister", "n"), ("nonno", "grand-père", "grandfather", "n"),
                      ("nonna", "grand-mère", "grandmother", "n"), ("io", "je, moi", "I, me", "pron")],
            "dialogue": {"fr": "Ma famille", "en": "My family",
                         "lines": [("A", "Questo è mio padre.", "C'est mon père.", "This is my father."), ("B", "Ciao!", "Salut !", "Hi!"),
                                   ("A", "Questa è mia madre.", "C'est ma mère.", "This is my mother."), ("B", "Che bella famiglia!", "Quelle belle famille !", "What a nice family!")]},
            "grammar": [{"fr": "mio / mia = mon / ma", "en": "mio / mia = my",
                         "md_fr": "**Mio** varie : *mio padre* (masculin), *mia madre* (féminin).",
                         "md_en": "**Mio** agrees: *mio padre* (masc.), *mia madre* (fem.).",
                         "ex": [("mio padre", "mon père", "my father"), ("mia madre", "ma mère", "my mother")]}],
            "fact": ("« mio padre » veut dire « mon père ».", "'mio padre' means 'my father'."),
        },
    },
    "fr": {
        "name": {"fr": "Français", "en": "French"}, "sources": ["en"],
        "salut": {
            "title": {"fr": "Français A0 — Se saluer", "en": "French A0 — Greetings"},
            "unit": {"fr": "Bonjour, merci, au revoir", "en": "Hello, thank you, goodbye"},
            "vocab": [("Bonjour", "bonjour", "hello", "interj"), ("Salut", "salut", "hi", "interj"), ("Bonne journée", "bonne journée", "have a good day", "interj"),
                      ("Au revoir", "au revoir", "goodbye", "interj"), ("Merci", "merci", "thank you", "interj"), ("S'il te plaît", "s'il te plaît", "please", "interj"),
                      ("Pardon", "pardon", "sorry", "interj"), ("Oui", "oui", "yes", "interj"), ("Non", "non", "no", "interj"), ("Comment ça va ?", "comment vas-tu ?", "how are you?", "phrase")],
            "dialogue": {"fr": "Une rencontre", "en": "A meeting",
                         "lines": [("A", "Bonjour !", "Bonjour !", "Hello!"), ("B", "Salut !", "Salut !", "Hi!"), ("A", "Comment ça va ?", "Comment vas-tu ?", "How are you?"),
                                   ("B", "Ça va bien, merci.", "Ça va bien, merci.", "I'm fine, thanks."), ("A", "Au revoir !", "Au revoir !", "Goodbye!")]},
            "grammar": [{"fr": "tu et vous", "en": "tu and vous",
                         "md_fr": "**Tu** est familier, **vous** est poli ou pluriel. À l'oral, la liaison relie les mots : *vous‿allez*.",
                         "md_en": "**Tu** is informal, **vous** is polite or plural. Liaison links words: *vous‿allez*.",
                         "ex": [("Comment ça va ?", "comment vas-tu ?", "how are you?")]}],
            "fact": ("« Merci » veut dire « merci ».", "'Merci' means 'thank you'."),
        },
        "nombres": {
            "title": {"fr": "Français A0 — Les nombres", "en": "French A0 — Numbers"},
            "unit": {"fr": "Compter de 0 à 10", "en": "Counting from 0 to 10"},
            "vocab": [("zéro", "zéro", "zero", "num"), ("un", "un", "one", "num"), ("deux", "deux", "two", "num"), ("trois", "trois", "three", "num"),
                      ("quatre", "quatre", "four", "num"), ("cinq", "cinq", "five", "num"), ("six", "six", "six", "num"), ("sept", "sept", "seven", "num"),
                      ("huit", "huit", "eight", "num"), ("neuf", "neuf", "nine", "num"), ("dix", "dix", "ten", "num")],
            "dialogue": {"fr": "Compter", "en": "Counting",
                         "lines": [("A", "Un, deux, trois.", "Un, deux, trois.", "One, two, three."), ("B", "Quatre, cinq, six.", "Quatre, cinq, six.", "Four, five, six."),
                                   ("A", "Sept, huit, neuf, dix.", "Sept, huit, neuf, dix.", "Seven, eight, nine, ten.")]},
            "grammar": [{"fr": "Compter en français", "en": "Counting in French",
                         "md_fr": "De **un** (1) à **dix** (10). Le « x » de *six* et *dix* se prononce « s ».",
                         "md_en": "From **un** (1) to **dix** (10). The 'x' in *six* and *dix* sounds like 's'.",
                         "ex": [("un, deux, trois", "un, deux, trois", "one, two, three")]}],
            "fact": ("« dix » veut dire « dix ».", "'dix' means 'ten'."),
        },
        "famille": {
            "title": {"fr": "Français A0 — Ma famille", "en": "French A0 — My family"},
            "unit": {"fr": "Papa, maman, et moi", "en": "Dad, mom, and me"},
            "vocab": [("famille", "famille", "family", "n"), ("père", "père", "father", "n"), ("mère", "mère", "mother", "n"),
                      ("frère", "frère", "brother", "n"), ("sœur", "sœur", "sister", "n"), ("grand-père", "grand-père", "grandfather", "n"),
                      ("grand-mère", "grand-mère", "grandmother", "n"), ("je", "je, moi", "I, me", "pron")],
            "dialogue": {"fr": "Ma famille", "en": "My family",
                         "lines": [("A", "C'est mon père.", "C'est mon père.", "This is my father."), ("B", "Bonjour !", "Bonjour !", "Hello!"),
                                   ("A", "C'est ma mère.", "C'est ma mère.", "This is my mother."), ("B", "Belle famille !", "Belle famille !", "Nice family!")]},
            "grammar": [{"fr": "mon / ma = mon / ma", "en": "mon / ma = my",
                         "md_fr": "**Mon** (masculin), **ma** (féminin) : *mon père*, *ma mère*.",
                         "md_en": "**Mon** (masc.), **ma** (fem.): *mon père*, *ma mère*.",
                         "ex": [("mon père", "mon père", "my father"), ("ma mère", "ma mère", "my mother")]}],
            "fact": ("« mon père » veut dire « mon père ».", "'mon père' means 'my father'."),
        },
    },
}


def wav_ms(path):
    with wave.open(path) as w:
        return int(1000 * w.getnframes() / w.getframerate())


def synth_opus(text, lang, dst):
    with tempfile.TemporaryDirectory() as t:
        wav = os.path.join(t, "x.wav")
        subprocess.run(["espeak-ng", "-v", VOICE[lang], "-s", "110", text, "-w", wav], check=True)
        ms = wav_ms(wav)
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", wav, "-ac", "1", "-ar", "16000",
                        "-af", "loudnorm=I=-16:TP=-1.5:LRA=11", "-c:a", "libopus", "-b:a", "24k", dst], check=True)
        return os.path.getsize(dst), ms


def mid(lang, theme, s):
    if s in ("d1", "count15"):
        return f"{lang}-{theme}-{s}"
    return f"{lang}-{s}"


def audio_list(lang, theme):
    th = CONTENT[lang][theme]
    out = []
    for term, _fr, _en, _pos in th["vocab"]:
        out.append((mid(lang, theme, slug(term)), term.strip("¿¡?!. ")))
    out.append((mid(lang, theme, "d1"), " ".join(l[1].rstrip(".!?¡¿") for l in th["dialogue"]["lines"])))
    return out


def build_media(lang, theme):
    scope = f"{lang}-{LEVEL}-{theme}"
    os.makedirs(os.path.join(MEDIA_DIR, scope, "audio"), exist_ok=True)
    media = []
    for m, tts in audio_list(lang, theme):
        rel = f"audio/{m}.opus"
        dst = os.path.join(MEDIA_DIR, scope, rel)
        b, ms = synth_opus(tts, lang, dst)
        media.append({"id": m, "file": rel, "kind": "audio", "bytes": b, "durationMs": ms,
                      "license": "CASTBRIDGE-ORIGINAL", "engine": f"{ENGINE} (voix {VOICE[lang]})",
                      "synthetic": True, "voiceLicense": "GPL-3.0", "lang": lang,
                      "source": "CastBridge, voix de synthèse libre (GPL-3.0, moteur uniquement)"})
    return media


def build_unit(lang, theme, src):
    th = CONTENT[lang][theme]
    L = "fr" if src == "fr" else "en"
    scope = f"{lang}-{LEVEL}-{theme}-{src}"
    vocab = []
    for i, (term, fr, en, pos) in enumerate(th["vocab"], 1):
        v = {"id": f"{scope}-v{i}", "term": term, "gloss": fr if L == "fr" else en, "pos": pos, "audio": f"m:{mid(lang, theme, slug(term))}"}
        vocab.append(v)
    lines = [{"who": w, "text": t, "tr": fr if L == "fr" else en} for w, t, fr, en in th["dialogue"]["lines"]]
    dialogue = [{"id": f"{scope}-d1", "title": th["dialogue"][L], "lines": lines, "audio": f"m:{mid(lang, theme, 'd1')}"}]
    grammar = []
    for i, g in enumerate(th["grammar"], 1):
        grammar.append({"id": f"{scope}-g{i}", "title": g[L], "md": g["md_" + L], "examples": [{"text": t, "tr": fr if L == "fr" else en} for t, fr, en in g["ex"]]})
    n = len(vocab)
    exercises = [
        {"id": f"{scope}-x1", "kind": "mcq", "prompt": (f"Que veut dire {vocab[0]['term']} ?" if L == "fr" else f"What does {vocab[0]['term']} mean?"),
         "choices": [vocab[0]["gloss"], vocab[1]["gloss"], vocab[2]["gloss"]], "correct": 0},
        {"id": f"{scope}-x2", "kind": "match", "prompt": ("Associe chaque mot à son sens." if L == "fr" else "Match each word to its meaning."),
         "pairs": [{"a": vocab[k]["term"], "b": vocab[k]["gloss"]} for k in range(min(5, n))]},
        {"id": f"{scope}-x3", "kind": "dictation", "prompt": ("Écoute et écris le mot." if L == "fr" else "Listen and write the word."),
         "audio": f"m:{mid(lang, theme, slug(th['vocab'][0][0]))}", "answers": [vocab[0]["term"]]},
        {"id": f"{scope}-x4", "kind": "speak", "prompt": (f"Dis « {vocab[1]['gloss']} » en {CONTENT[lang]['name'][L]}, puis compare." if L == "fr" else f"Say '{vocab[1]['gloss']}' in {CONTENT[lang]['name'][L]}, then compare."),
         "model": vocab[1]["term"], "audio": f"m:{mid(lang, theme, slug(th['vocab'][1][0]))}"},
        {"id": f"{scope}-x5", "kind": "truefalse", "prompt": th["fact"][0 if L == "fr" else 1], "correct": 0},
        {"id": f"{scope}-x6", "kind": "translate", "prompt": (f"Traduis : {vocab[2]['gloss']}." if L == "fr" else f"Translate: {vocab[2]['gloss']}."), "answers": [vocab[2]["term"]]},
        {"id": f"{scope}-x7", "kind": "write", "prompt": (f"Écris « {vocab[3]['gloss']} »." if L == "fr" else f"Write '{vocab[3]['gloss']}'."), "model": vocab[3]["term"]},
        {"id": f"{scope}-x8", "kind": "mcq", "prompt": (f"Que veut dire {vocab[4]['term']} ?" if L == "fr" else f"What does {vocab[4]['term']} mean?"),
         "choices": [vocab[3]["gloss"], vocab[4]["gloss"], vocab[0]["gloss"]], "correct": 1},
    ]
    cards = [{"id": f"{scope}-c{i}", "vocab": f"{scope}-v{i}"} for i in range(1, 6)]
    return {"id": f"{scope}-u1", "title": th["unit"][L], "minutes": 10, "skills": ["co", "ce", "po"], "prerequisites": [],
            "vocab": vocab, "dialogues": dialogue, "grammar": grammar, "exercises": exercises, "stories": [], "cards": cards, "animations": []}


def gesture_block(lang, theme, src):
    L = "fr" if src == "fr" else "en"
    if theme == "nombres":
        cap = {"fr": "Compter de 1 à 5", "en": "Counting from 1 to 5"}[L]
        alt = {"fr": "Cinq cases numérotées de un à cinq avec un, deux, trois, quatre puis cinq points.",
               "en": "Five boxes numbered one to five with one, two, three, four then five dots."}[L]
        a = animlib.Anim(480, 200, mode="steps")
        for k in range(1, 6):
            x = 40 + (k - 1) * 90
            a.add(animlib.rect(x, 60, 70, 70, "lightblue", "blue", 2, 8), id=f"b{k}", alpha=0)
            a.add(animlib.text(x + 35, 40, str(k), 22, "blue", bold=True), id=f"n{k}", alpha=0)
            dots = [a.add(animlib.circle(x + 18 + (d % 3) * 18, 82 + (d // 3) * 18, 5, "ink", None), id=f"p{k}d{d}", alpha=0) for d in range(k)]
            with a.step(f"{k}.") as s:
                s.show(f"b{k}", 0.3); s.show(f"n{k}", 0.3)
                for d in dots:
                    s.show(d, 0.2, delay=0.1)
        return a.block(alt, cap)
    return None


def write_pack(lang, theme, src, media):
    scope = f"{lang}-{LEVEL}-{theme}-{src}"
    L = "fr" if src == "fr" else "en"
    dstdir = os.path.join(CONTENT_DIR, scope)
    os.makedirs(dstdir, exist_ok=True)
    pack = {"format": 1, "type": "langue", "id": scope, "version": 1, "target": lang, "level": LEVEL, "theme": theme, "source": src,
            "title": CONTENT[lang][theme]["title"][L], "state": "review", "units": [build_unit(lang, theme, src)]}
    with open(os.path.join(dstdir, "langue.json"), "w", encoding="utf-8") as f:
        json.dump(pack, f, ensure_ascii=False, indent=1)
    with open(os.path.join(dstdir, "media.json"), "w", encoding="utf-8") as f:
        json.dump({"format": 1, "comment": "Médias référencés du lot texte ; fichiers dans le lot média jumeau.", "media": media}, f, ensure_ascii=False, indent=1)
    b = gesture_block(lang, theme, src)
    if b is not None:
        figdir = os.path.join(dstdir, "figures")
        os.makedirs(figdir, exist_ok=True)
        with open(os.path.join(figdir, f"{scope}-u1-a1.json"), "w", encoding="utf-8") as f:
            json.dump(b, f, ensure_ascii=False, indent=1)
        pack["units"][0]["animations"] = [{"id": f"{scope}-u1-a1", "kind": "gesture", "label": {"fr": "Compter de 1 à 5", "en": "Counting from 1 to 5"}[L]}]
        with open(os.path.join(dstdir, "langue.json"), "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=1)


def main():
    if not shutil.which("espeak-ng"):
        sys.exit("espeak-ng manquant")
    if not shutil.which("ffmpeg"):
        sys.exit("ffmpeg manquant")
    for lang in ("en", "de", "es", "it", "fr"):
        for theme in ("salut", "nombres", "famille"):
            media = build_media(lang, theme)
            for src in CONTENT[lang]["sources"]:
                write_pack(lang, theme, src, media)
    print("Packs latins générés : en(3), de(6), es(6), it(6), fr(3) = 24")


if __name__ == "__main__":
    main()
