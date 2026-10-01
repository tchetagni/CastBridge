import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from p45_kit import *

TAG["v"] = "Class 4 English"
p = Pack("class4-english", "English Language — Class 4", level="Class 4", subject="english", cursus="primary",
         description="Class 4 English Language for the English-speaking primary subsystem: parts of speech, tenses, concord, punctuation, spelling, vocabulary, comprehension with original passages, letters, stories and descriptions, a poem and oral English.",
         programRef="MINEDUB primary school curriculum (English-speaking subsystem), Level II (Class 4) — English Language; to be checked against the official syllabus")
REF = p.programRef


def G(tier, sentence, right, wrongs, expl, **kw):
    return M(tier, "Choose the correct word to fill the gap: " + sentence, right, wrongs, expl, **kw)


# ============================================================ 1. grammar
ch = p.chapter("grammar", "Grammar: the parts of speech", REF)

fig = grid([("Person", "teacher"), ("Place", "Douala"), ("Animal", "goat"), ("Thing", "book")], 4, 88, 62, gap=8, size=14)
build(ch, "nouns", "Nouns: common, proper and collective", 25,
      ["Say what a noun is.", "Tell common and proper nouns apart and use capital letters.", "Use collective nouns and make simple plurals."],
      [("key", "definition", "Nouns", "A **noun** is a **naming word**. It names a person (teacher), a place (market), an animal (goat) or a thing (book). A **common noun** names any person, place or thing: *girl, town, river*. A **proper noun** names one special person, place or thing and begins with a **capital letter**: *Ngum, Douala, River Sanaga*."),
       ("fig", fig, "Nouns name people, places, animals and things.", "Four boxes: person, teacher; place, Douala; animal, goat; thing, book."),
       ("key", "retenir", "Collective nouns", "A **collective noun** names a group: a **class** of pupils, a **herd** of cattle, a **bunch** of bananas, a **flock** of birds, a **team** of players, a **swarm** of bees. One collective noun is one group: *The team is ready.*"),
       ("key", "pieges", "Common mistakes", "- Forgetting capital letters for proper nouns: write *Bamenda*, not *bamenda*.\n- Using a capital letter for common nouns in the middle of a sentence.\n- Mixing up a noun with a verb: *Run* can be a verb (*I run*) or a noun (*a long run*).")],
      [("ex", "Example 1", "Find the nouns in: 'Ako buys a bag of rice in Bafoussam.'", ["Look for names of people, places and things.", "Ako (person, proper), bag (thing), rice (thing), Bafoussam (place, proper)."], "Ako, bag, rice, Bafoussam.", None),
       ("ex", "Example 2", "Fill the gap with a collective noun: a ____ of bananas.", ["Think about how bananas grow or are sold together.", "We say a bunch of bananas."], "bunch", None)],
      [G("application", "A ____ of cattle was crossing the road near Bamenda.", "herd", ["bunch", "class", "swarm"], "We say a herd of cattle."),
       M("application", "Which word is a proper noun?", "Mount Cameroon", ["mountain", "river", "teacher"], "It names one special mountain."),
       M("application", "Which word is a common noun?", "market", ["Limbe", "Ako", "Kumba"], "It names any market."),
       TF("approfondissement", "A proper noun always begins with a small letter.", False, "A proper noun begins with a capital letter."),
       MA("approfondissement", "Match each collective noun to the right group.", [("a flock of", "birds"), ("a class of", "pupils"), ("a swarm of", "bees"), ("a team of", "players")], "Learn the common pairs."),
       P("Read: Mary and her brother Paul went to Buea on Saturday. They carried a bunch of plantains and a basket of oranges.",
         [PM("Which word is a proper noun?", "Buea", ["brother", "basket", "oranges"], "It names a special place."),
          PM("Which word is a collective noun?", "bunch", ["Saturday", "carried", "went"], "A bunch is a group of plantains."),
          PN("How many proper nouns are there? (Mary, Paul, Buea, Saturday)", 4, "Names of people, a place and a day all begin with capital letters.", points=2)])],
      [("A noun is a...", "naming word", ["doing word", "describing word", "joining word"], "Nouns name people, places, animals and things."),
       ("Which noun needs a capital letter?", "Yaoundé", ["town", "school", "teacher"], "It is the name of one special place."),
       ("A group of bees is a...", "swarm", ["herd", "class", "bunch"], "A swarm of bees."),
       ("Which is a collective noun?", "flock", ["table", "tree", "Ngum"], "A flock is a group of birds."),
       ("How many capital letters are needed in: 'ako lives in buea on monday.'?", "3", ["1", "2", "4"], "Ako, Buea and Monday.")],
      ["Months are capitalised in English; collective nouns taught are the common ones; to be checked against the textbook."])

fig = table([["Person", "Subject", "Possessive"], ["I", "I", "my"], ["you", "you", "your"], ["he / she / it", "he, she, it", "his, her, its"], ["we / they", "we, they", "our, their"]], [100, 110, 110], rh=28, size=14)
build(ch, "pronouns", "Pronouns and articles", 25,
      ["Use pronouns in place of nouns.", "Use my, your, his, her, its, our and their.", "Use a, an and the correctly."],
      [("key", "definition", "Pronouns", "A **pronoun** takes the place of a noun so that we do not repeat it. *Ako is a pupil. **He** is in Class 4.* Subject pronouns: **I, you, he, she, it, we, they**. **Possessive** words show who owns something: **my, your, his, her, its, our, their**."),
       ("fig", fig, "Pronouns and possessive words.", "A table with the pronouns I, you, he she it, we they and the matching possessive words my, your, his her its, our their."),
       ("key", "retenir", "A, an and the", "Use **a** before a consonant sound (*a book, a goat*) and **an** before a vowel sound (*an egg, an orange, an hour*). Use **the** when we speak about a particular thing we both know: *The teacher is in the classroom.*"),
       ("key", "pieges", "Common mistakes", "- *Me and Ako went home* is not correct: say *Ako and I went home*.\n- *An book* is wrong: it is *a book*.\n- Using *their* for one person: *Ngum lost his pen* (not *their pen*).\n- Mixing *it's* (it is) and *its* (belonging to it).")],
      [("ex", "Example 1", "Replace the noun with a pronoun: 'Mary and Paul are in the garden. Mary and Paul are planting maize.'", ["Mary and Paul are two people, so we use they.", "Mary and Paul are in the garden. They are planting maize."], "They", None),
       ("ex", "Example 2", "Choose a or an: ___ orange, ___ mango.", ["Orange begins with a vowel sound: an orange.", "Mango begins with a consonant sound: a mango."], "an orange, a mango", None)],
      [G("application", "Ngum lost ____ pen on the way to school.", "his", ["her", "their", "its"], "Ngum is a boy, so his."),
       G("application", "She has ____ umbrella in her bag.", "an", ["a", "the", "some"], "Umbrella begins with a vowel sound."),
       G("application", "The girls forgot ____ books.", "their", ["her", "his", "its"], "The girls is plural, so their."),
       TF("approfondissement", "In 'Ada and I went to market', the word 'I' is correctly used.", True, "We say Ada and I as the subject."),
       MA("approfondissement", "Match each noun with the right pronoun.", [("the girl", "she"), ("the boys", "they"), ("the book", "it"), ("Ako and I", "we")], "Choose by number and gender."),
       P("Read: Ako has a sister. Ako's sister is called Bih. Bih likes mangoes. Bih eats a mango every day.",
         [PM("Which pronoun can replace 'Ako's sister'?", "she", ["he", "it", "they"], "Bih is a girl."),
          PM("Which is a better way to write the last sentence?", "She eats a mango every day.", ["Bih eats Bih's mango every day.", "They eat a mango every day.", "It eats a mango every day."], "Use a pronoun to avoid repeating Bih."),
          PM("Which article goes before 'egg'?", "an", ["a", "the", "no article"], "Egg starts with a vowel sound.", points=2)])],
      [("Which is a pronoun?", "they", ["book", "run", "happy"], "They replaces a plural noun."),
       ("Complete: ___ apple is red.", "An", ["A", "Some", "Many"], "Apple starts with a vowel."),
       ("Which word shows ownership?", "our", ["we", "us", "and"], "Our is a possessive word."),
       ("'Neba and ___ are friends.'", "I", ["me", "my", "mine"], "Neba and I is the subject."),
       ("Replace 'the bag': 'The bag is heavy. ___ is heavy.'", "It", ["They", "He", "She"], "A bag is a thing: it.")],
      ["'An hour' (silent h) is mentioned; check how the school presents the sound rule."])

fig = table([["Past", "Present", "Future"], ["I walked", "I walk", "I will walk"], ["She ate", "She eats", "She will eat"], ["They went", "They go", "They will go"]], [110, 110, 110], rh=30, size=14)
irr = [("go", "went"), ("eat", "ate"), ("see", "saw"), ("buy", "bought"), ("run", "ran"), ("come", "came"), ("write", "wrote"), ("take", "took")]
build(ch, "verbs-tenses", "Verbs and simple tenses", 30,
      ["Say what a verb is.", "Use the simple present, simple past and simple future.", "Learn common irregular past forms."],
      [("key", "definition", "Verbs", "A **verb** is a **doing** or **being** word: *run, eat, write, is, are*. The **tense** of a verb tells **when** something happens: **present** (now or always), **past** (before now) or **future** (later). Present: *I walk.* Past: *I walked.* Future: *I will walk.*"),
       ("fig", fig, "Past, present and future.", "A table with three columns, past present future, and the sentences I walked, I walk, I will walk, then she ate, she eats, she will eat, then they went, they go, they will go."),
       ("key", "retenir", "Regular and irregular verbs", "Regular verbs add **-ed** in the past: *walk, walked; play, played*. **Irregular verbs** change in other ways: go - **went**, eat - **ate**, see - **saw**, buy - **bought**, run - **ran**, come - **came**, write - **wrote**, take - **took**. These must be learnt by heart."),
       ("key", "pieges", "Common mistakes", "- Writing **goed**, **eated** or **buyed**: the past forms are *went, ate, bought*.\n- Mixing tenses in one sentence: *Yesterday I go to the market* is wrong; say *Yesterday I went*.\n- Using *will* with *-ed*: *I will walked* is wrong.")],
      [("ex", "Example 1", "Write in the past tense: 'Ako goes to school and eats bread.'", ["The past of go is went; the past of eats is ate.", "Ako went to school and ate bread."], "Ako went to school and ate bread.", None),
       ("ex", "Example 2", "Write in the future tense: 'We plant maize.'", ["The future uses will + the simple verb.", "We will plant maize."], "We will plant maize.", None)],
      [G("application", "Yesterday, my father ____ a new bag for me.", "bought", ["buy", "buyed", "will buy"], "The past of buy is bought."),
       G("application", "Tomorrow we ____ visit our grandmother in Bali.", "will", ["did", "was", "have"], "Will shows the future."),
       G("application", "Mary ____ her homework every evening.", "does", ["do", "did", "doing"], "The present with 'she' takes does."),
       TF("approfondissement", "'Goed' is the past tense of 'go'.", False, "The past of go is went."),
       MA("approfondissement", "Match each verb with its past tense.", [("see", "saw"), ("run", "ran"), ("write", "wrote"), ("take", "took")], "Irregular verbs must be learnt."),
       P("Read: Last Saturday, Ngum goes to the market. He buyed some yams and plantains. Next Saturday he will helps his mother.",
         [PM("Which verb in the first sentence is wrong?", "goes", ["Last", "market", "to"], "Last Saturday needs the past: went."),
          PM("What is the right form of 'buyed'?", "bought", ["buy", "buys", "buying"], "Past of buy."),
          PM("What is the right form of 'will helps'?", "will help", ["will helped", "will helping", "helps"], "Will is followed by the simple verb.", points=2)])],
      [("The past of 'eat' is...", "ate", ["eated", "eaten up", "eating"], "Eat - ate."),
       ("Which sentence is in the future tense?", "I will play football.", ["I played football.", "I play football.", "I am playing football."], "Will plus the simple verb."),
       ("A verb is a...", "doing word", ["naming word", "joining word", "describing word"], "Run, eat and write are verbs."),
       ("The past of 'come' is...", "came", ["comed", "come", "coming"], "Come - came."),
       ("Which is correct?", "She saw a snake.", ["She seed a snake.", "She sees a snake yesterday.", "She will saw a snake."], "The past of see is saw.")],
      ["Only simple tenses are taught here; continuous and perfect tenses are in Class 5."])

fig = table([["Adjective", "Comparative", "Superlative"], ["tall", "taller", "tallest"], ["big", "bigger", "biggest"], ["good", "better", "best"], ["beautiful", "more beautiful", "most beautiful"]], [100, 120, 130], rh=28, size=13)
build(ch, "adjectives", "Adjectives and comparing", 25,
      ["Say what an adjective is.", "Use adjectives to describe nouns.", "Use the comparative and superlative."],
      [("key", "definition", "Adjectives", "An **adjective** is a **describing word**. It tells us more about a noun: a **tall** tree, a **sweet** mango, **three** goats, a **busy** market. An adjective usually goes **before** the noun or after the verb **to be**: *The road is long.*"),
       ("fig", fig, "Adjectives of comparison.", "A table with adjectives tall, big, good and beautiful, their comparatives taller, bigger, better, more beautiful and superlatives tallest, biggest, best, most beautiful."),
       ("key", "retenir", "Comparing", "To compare **two** things, add **-er** or use **more** (*Mount Cameroon is taller than the hill.*). To compare **three or more** things, add **-est** or use **most** (*It is the tallest mountain in Cameroon.*). Short words add -er/-est (doubling the last letter: big - bigger - biggest). Long words use *more/most*. Irregular: good - better - best, bad - worse - worst."),
       ("key", "pieges", "Common mistakes", "- *More taller* or *most tallest*: use only one of the two.\n- *Gooder* is wrong: say *better*.\n- Using *than* for the superlative: *the tallest of the three*, not *tallest than*.\n- Forgetting to double the letter: *bigger*, not *biger*.")],
      [("ex", "Example 1", "Fill the gap: Ako is ____ than Bih. (tall)", ["We compare two people, so we use the comparative.", "Tall adds -er: taller."], "taller", None),
       ("ex", "Example 2", "Fill the gap: This is the ____ mango in the basket. (sweet)", ["More than two mangoes: use the superlative.", "Sweet adds -est: sweetest."], "sweetest", None)],
      [G("application", "A lion is ____ than a goat.", "stronger", ["strongest", "more strong", "strong"], "Two animals are compared: stronger."),
       G("application", "Kumba is the ____ of the three towns. (big)", "biggest", ["bigger", "more big", "most bigger"], "Three or more: biggest."),
       G("application", "This book is ____ than that one. (good)", "better", ["gooder", "best", "more good"], "Good - better - best."),
       TF("approfondissement", "'More taller' is correct English.", False, "Use only taller."),
       MA("approfondissement", "Match each adjective with its superlative.", [("small", "smallest"), ("happy", "happiest"), ("bad", "worst"), ("interesting", "most interesting")], "Mind the spelling and the long words."),
       P("Read: Ako is 130 cm tall, Bih is 125 cm tall and Che is 135 cm tall.",
         [PM("Who is the tallest?", "Che", ["Ako", "Bih", "They are equal"], "135 cm is the most."),
          PM("Complete: Ako is ____ than Bih.", "taller", ["tallest", "more tall", "tall"], "Two children are compared."),
          PM("Complete: Bih is the ____ of the three.", "shortest", ["shorter", "most short", "short"], "Three children: superlative.", points=2)])],
      [("Which word is an adjective?", "beautiful", ["quickly", "Buea", "run"], "It describes a noun."),
       ("The comparative of 'small' is...", "smaller", ["smallest", "more small", "smallish"], "Small + er."),
       ("The superlative of 'good' is...", "best", ["goodest", "better", "most good"], "Good - better - best."),
       ("Which sentence is correct?", "Fish is cheaper than meat.", ["Fish is more cheaper than meat.", "Fish is cheaper then meat.", "Fish is cheapest than meat."], "Cheaper than."),
       ("'The road is long.' The adjective is...", "long", ["road", "is", "the"], "It describes the road.")],
      [])

fig = grid([("Where?", "in, on, under"), ("When?", "now, soon, often"), ("How?", "quickly, well")], 3, 112, 62, gap=8, size=13)
build(ch, "adverbs-prepositions", "Adverbs and prepositions", 25,
      ["Say what an adverb is and use adverbs of manner, time and place.", "Use common prepositions of place and time.", "Choose the right preposition."],
      [("key", "definition", "Adverbs", "An **adverb** tells us more about a **verb**: **how**, **when** or **where** something happens. *She walks **slowly**. (how) We will go **tomorrow**. (when) The children play **outside**. (where)* Many adverbs of manner end in **-ly**: *quickly, carefully, loudly*."),
       ("fig", fig, "Adverbs and prepositions answer questions.", "Three boxes: Where, with in, on, under; When, with now, soon, often; How, with quickly, well."),
       ("key", "definition", "Prepositions", "A **preposition** shows the **position** or **time** of a noun. Place: **in** the bag, **on** the table, **under** the tree, **between** two houses, **behind** the school, **near** the river. Time: **at** 7 o'clock, **on** Monday, **in** May."),
       ("key", "pieges", "Common mistakes", "- Using an adjective for an adverb: *She sings beautiful* should be *beautifully*.\n- Using *in* with days: say *on Monday*, not *in Monday*.\n- *Good* is an adjective, **well** is the adverb: *He plays well*.\n- Confusing *at* a time and *in* a month.")],
      [("ex", "Example 1", "Choose the adverb: 'The teacher speaks (soft / softly).'", ["The word describes the verb speaks, so we need an adverb.", "The adverb is softly."], "softly", None),
       ("ex", "Example 2", "Fill the gap: 'We have English ___ Monday.'", ["Before a day of the week we use on.", "We have English on Monday."], "on", None)],
      [G("application", "The tortoise walked ____.", "slowly", ["slow", "slowness", "slowest"], "An adverb of manner."),
       G("application", "The cat is sleeping ____ the table.", "under", ["at", "of", "since"], "Under shows the position."),
       G("application", "School starts ____ 7:30 a.m.", "at", ["on", "in", "between"], "At with clock times."),
       TF("approfondissement", "'Quickly' is an adverb.", True, "It tells how someone does something."),
       MA("approfondissement", "Match each sentence gap with the right word.", [("My birthday is ___ June.", "in"), ("We play football ___ Saturday.", "on"), ("The bus leaves ___ noon.", "at"), ("The ball is ___ the box.", "inside")], "Use at, on, in correctly."),
       P("Read: Last Friday, Ako walked carefully to school. She sat between Bih and Che. She worked hard and answered the questions well.",
         [PM("Which word is an adverb of manner?", "carefully", ["Friday", "school", "between"], "How she walked."),
          PM("Which word is a preposition of place?", "between", ["walked", "hard", "well"], "It shows where she sat."),
          PM("Which word is an adverb that goes with 'answered'?", "well", ["Bih", "questions", "She"], "Answered how? Well.", points=2)])],
      [("Which word is an adverb?", "loudly", ["loud", "noise", "louder"], "Adverbs often end in -ly."),
       ("'The book is ___ the shelf.'", "on", ["at", "to", "by"], "On shows the position on a surface."),
       ("Adverbs tell us more about...", "verbs", ["pronouns", "capital letters", "nouns only"], "How, when or where."),
       ("We say 'in' with...", "months", ["days", "clock times", "weekends only"], "In May, in June."),
       ("'He runs fast.' The adverb is...", "fast", ["He", "runs", "He runs"], "It tells how he runs.")],
      ["'Fast' is both an adjective and an adverb; explanation kept simple."])

fig = boxes(["One person: The boy plays.", "Two or more: The boys play.", "She / he / it: the verb takes s - plays", "I / you / we / they: no s - play"], size=14)
build(ch, "concord", "Subject-verb agreement (concord)", 25,
      ["Make the verb agree with its subject.", "Use is/are, was/were, has/have and does/do correctly.", "Spot and correct simple concord mistakes."],
      [("key", "definition", "Concord", "**Concord** (agreement) means that the verb agrees with its subject. A **singular** subject takes a singular verb: *The boy **plays**.* A **plural** subject takes a plural verb: *The boys **play**.* With *he, she, it* the present verb ends in **s**: *she plays, he runs, it eats*."),
       ("fig", fig, "The verb agrees with the subject.", "Four stacked boxes: one person, the boy plays; two or more, the boys play; he she it takes s; I you we they take no s."),
       ("key", "retenir", "Is, are, has, have, was, were", "**Is / was / has / does** go with *he, she, it* and singular nouns. **Are / were / have / do** go with *we, you, they* and plural nouns. *I* takes **am**, **was**, **have**, **do**. *There **is** a goat.* *There **are** two goats.*"),
       ("key", "pieges", "Common mistakes", "- Looking at the nearest word instead of the real subject: *The box of mangoes **is** heavy*.\n- *The children is playing* is wrong: *The children **are** playing*.\n- *He don't know*: say *He doesn't know*.\n- *Everybody are here*: *everybody* is singular (*is*).")],
      [("ex", "Example 1", "Choose the right word: 'The girls (sing / sings) in the choir.'", ["The subject girls is plural.", "A plural subject takes sing."], "sing", None),
       ("ex", "Example 2", "Correct: 'Ako and Bih is friends.'", ["Ako and Bih are two people, so the subject is plural.", "The verb must be are: Ako and Bih are friends."], "Ako and Bih are friends.", None)],
      [G("application", "The boy ____ to school every day.", "walks", ["walk", "walking", "are walking"], "The boy is singular: walks."),
       G("application", "The children ____ playing in the yard.", "are", ["is", "was", "has"], "The children is plural."),
       G("application", "She ____ not like fish.", "does", ["do", "are", "have"], "Singular subject she: does."),
       TF("approfondissement", "'There is two books on the table' is correct.", False, "Two books is plural: there are two books."),
       MA("approfondissement", "Match the subject to the right verb.", [("The teacher", "teaches"), ("The pupils", "learn"), ("I", "am"), ("They", "were")], "Choose singular or plural."),
       P("Read: My uncle live in Limbe. He have two sons. The sons goes to school near the sea.",
         [PM("Which is the correct form of the first sentence?", "My uncle lives in Limbe.", ["My uncle live in Limbe.", "My uncles lives in Limbe.", "My uncle living in Limbe."], "Singular subject: lives."),
          PM("Which is correct?", "He has two sons.", ["He have two sons.", "He haves two sons.", "He are two sons."], "He takes has."),
          PM("Which is correct?", "The sons go to school near the sea.", ["The sons goes to school near the sea.", "The sons going to school near the sea.", "The sons is going to school near the sea."], "Plural subject: go.", points=2)])],
      [("The dog ____ at the gate.", "barks", ["bark", "barking", "are barking"], "Singular: barks."),
       ("My friends ____ here.", "are", ["is", "am", "was"], "Plural subject: are."),
       ("'He ___ not know' - which word fits?", "does", ["do", "are", "have"], "He does not know."),
       ("There ___ many people at the market.", "are", ["is", "was", "has"], "Many people is plural."),
       ("Which sentence is correct?", "The books are on the desk.", ["The books is on the desk.", "The book are on the desk.", "The books was on the desk."], "Plural subject, plural verb.")],
      ["'Everybody' as singular follows standard English; some speakers use a plural in speech."])

# ============================================================ 2. writing skills
ch = p.chapter("skills", "Punctuation, spelling and vocabulary", REF)

fig = table([["Mark", "Name", "Use"], [".", "full stop", "ends a statement"], ["?", "question mark", "ends a question"], ["!", "exclamation mark", "shows surprise"], [",", "comma", "separates a list"], ["'", "apostrophe", "can't, Ako's"]], [60, 130, 150], rh=28, size=13)
build(ch, "punctuation", "Capital letters and punctuation", 25,
      ["Use capital letters correctly.", "Use full stops, question marks, exclamation marks and commas.", "Use the apostrophe in simple cases."],
      [("key", "definition", "Capital letters", "Use a **capital letter**: at the **start of a sentence**; for **names** of people and places (*Ako, Douala*); for the days and months (*Monday, May*); for the word **I**; and for the first word in a line of a poem. Everything else is in small letters."),
       ("fig", fig, "Punctuation marks and their use.", "A table of marks: full stop ends a statement; question mark ends a question; exclamation mark shows surprise; comma separates a list; apostrophe in can't and Ako's."),
       ("key", "retenir", "Punctuation", "End a statement with a **full stop** (.). End a question with a **question mark** (?). Show surprise or strong feeling with an **exclamation mark** (!). Use **commas** to separate items in a list: *I bought rice, beans, oil and salt.* Use an **apostrophe** in short forms (*don't = do not*) and to show ownership (*Ako's bag*)."),
       ("key", "pieges", "Common mistakes", "- No capital letter at the start of a sentence, or a capital letter in the middle of a sentence for no reason.\n- Using a full stop after a question.\n- Using a comma between two complete sentences (use a full stop).\n- Writing *its* instead of *it's* (it is).")],
      [("ex", "Example 1", "Punctuate: 'where do you live ako'", ["It is a question, so it ends with a question mark. Ako is a name (capital letter) and we separate it with a comma.", "Where do you live, Ako?"], "Where do you live, Ako?", None),
       ("ex", "Example 2", "Punctuate: 'we bought mangoes oranges bananas and pineapples'", ["Start with a capital letter and put commas between the items. There is no comma before 'and'.", "We bought mangoes, oranges, bananas and pineapples."], "We bought mangoes, oranges, bananas and pineapples.", None)],
      [M("application", "Which mark must end the question 'Where is the market'?", "a question mark", ["a full stop", "a comma", "an apostrophe"], "A question ends with ?"),
       M("application", "Which mark shows surprise?", "!", [".", ",", "?"], "The exclamation mark."),
       M("application", "Which word in 'I live in buea.' needs a capital letter?", "buea", ["live", "in", "I"], "Buea is the name of a town."),
       TF("approfondissement", "We put a comma between each item in a list, even before 'and' in all cases.", False, "In a simple list we usually write a comma between items but not before 'and'."),
       MA("approfondissement", "Match each short form with its long form.", [("don't", "do not"), ("it's", "it is"), ("can't", "cannot"), ("I'm", "I am")], "The apostrophe takes the place of the missing letters."),
       P("Read this sentence without punctuation: 'ako said where is my bag'",
         [PM("Which is correct?", "Ako said, \"Where is my bag?\"", ["Ako said where is my bag.", "ako said, where is my bag?", "Ako said \"where is my bag\"."], "Speech is put in quotation marks."),
          PM("Which word needs a capital letter?", "Ako", ["said", "bag", "is"], "It is a name."),
          PM("What mark ends the spoken question?", "question mark", ["full stop", "comma", "colon"], "The speaker asks a question.", points=2)])],
      [("A sentence starts with a...", "capital letter", ["small letter", "comma", "number"], "Always."),
       ("Which punctuation mark ends a question?", "?", [".", "!", ","], "The question mark."),
       ("The short form of 'do not' is...", "don't", ["dont", "do'nt", "d'not"], "The apostrophe replaces o."),
       ("Where does the comma go in the list 'I bought rice beans and oil'?", "between rice and beans", ["after bought", "after and", "before I"], "Commas separate the items of a list."),
       ("In 'Ako's bag', the apostrophe shows...", "ownership", ["a question", "surprise", "a list"], "The bag belongs to Ako.")],
      ["Quotation marks are mentioned lightly; direct and indirect speech is taught in Class 5."])

fig = table([["Singular", "Plural", "Rule"], ["book", "books", "add s"], ["box", "boxes", "add es"], ["city", "cities", "y to ies"], ["leaf", "leaves", "f to ves"], ["man", "men", "irregular"]], [100, 100, 120], rh=28, size=13)
build(ch, "spelling", "Spelling: plurals, silent letters and tricky words", 25,
      ["Make plurals using the main spelling rules.", "Recognise silent letters.", "Learn some common tricky words."],
      [("key", "definition", "Plural rules", "Most nouns just add **s**: *book - books*. Nouns ending in **s, x, ch, sh** add **es**: *box - boxes, church - churches, dish - dishes*. Nouns ending in **consonant + y** change **y to ies**: *city - cities, baby - babies*. After a vowel just add **s**: *boy - boys, day - days*. Some end in **f / fe** and change to **ves**: *leaf - leaves, knife - knives*."),
       ("fig", fig, "Plural spelling rules.", "A table with singular, plural and rule: book, books, add s; box, boxes, add es; city, cities, y to ies; leaf, leaves, f to ves; man, men, irregular."),
       ("key", "retenir", "Irregular plurals and silent letters", "Irregular plurals: **man - men, woman - women, child - children, foot - feet, tooth - teeth, mouse - mice**; **sheep** and **fish** do not change. **Silent letters** are not pronounced: **k** in *knife, know*; **w** in *write, wrong*; **b** in *lamb, comb*; **h** in *hour*."),
       ("key", "pieges", "Common mistakes", "- *Childs* or *mans*: use **children** and **men**.\n- *Citys*: it is **cities**.\n- *Wich* for *which*, or *thier* for *their*: learn the tricky spellings.\n- Leaving out silent letters: *nife*, *rite*.")],
      [("ex", "Example 1", "Write the plural of: bus, baby, tooth.", ["Bus ends in s: add es: buses. Baby ends in consonant + y: change y to ies: babies.", "Tooth is irregular: teeth."], "buses, babies, teeth", None),
       ("ex", "Example 2", "Which word has a silent letter: 'knee' or 'tree'?", ["In 'knee' the k is not pronounced.", "In 'tree' every letter is sounded."], "knee (silent k)", None)],
      [M("application", "What is the plural of 'church'?", "churches", ["churchs", "churchies", "churchen"], "Add es after ch."),
       M("application", "What is the plural of 'baby'?", "babies", ["babys", "babyes", "babyies"], "Consonant + y: change to ies."),
       M("application", "Which word has a silent w?", "write", ["will", "water", "well"], "The w in write is not pronounced."),
       TF("approfondissement", "The plural of 'child' is 'childs'.", False, "The plural is children."),
       MA("approfondissement", "Match each singular with its plural.", [("woman", "women"), ("leaf", "leaves"), ("sheep", "sheep"), ("day", "days")], "Irregular and regular plurals."),
       P("A pupil writes: 'The two mans carried boxs of tomatos to the markets. The childs helped them.'",
         [PM("Correct plural of 'mans':", "men", ["manes", "mens", "mans"], "Man - men."),
          PM("Correct plural of 'boxs':", "boxes", ["boxs", "boxies", "boxen"], "Add es after x."),
          PM("Correct plural of 'childs':", "children", ["childrens", "childes", "child"], "Irregular plural.", points=2)])],
      [("The plural of 'city' is...", "cities", ["citys", "cityes", "citis"], "Change y to ies."),
       ("The plural of 'foot' is...", "feet", ["foots", "feets", "footes"], "Irregular."),
       ("Which word has a silent k?", "know", ["kind", "kick", "keep"], "In 'know', k is not pronounced."),
       ("Which spelling is correct?", "friend", ["freind", "frend", "firend"], "Learn: friend."),
       ("What is the plural of 'fish'?", "fish", ["fishes only", "fishs", "fishies"], "Fish usually stays fish.")],
      ["The plural 'fishes' exists for different species; the simple rule is given."])

fig = table([["Word", "Same meaning", "Opposite"], ["big", "large", "small"], ["happy", "glad", "sad"], ["begin", "start", "end"], ["hot", "warm", "cold"]], [90, 120, 110], rh=28, size=14)
build(ch, "vocabulary", "Vocabulary: synonyms, antonyms and homophones", 25,
      ["Find synonyms (same meaning) and antonyms (opposite meaning).", "Tell apart common homophones.", "Use a dictionary to check a word."],
      [("key", "definition", "Synonyms and antonyms", "**Synonyms** are words with **nearly the same meaning**: *big - large, begin - start, glad - happy*. **Antonyms** are words with **opposite meanings**: *big - small, hot - cold, begin - end*. Using different words makes writing more interesting."),
       ("fig", fig, "Words with the same and the opposite meaning.", "A table with four words: big has the same meaning as large and the opposite small; happy glad sad; begin start end; hot warm cold."),
       ("key", "definition", "Homophones", "**Homophones** sound the same but have different spellings and meanings: **to / too / two**, **there / their**, **see / sea**, **write / right**, **buy / by**, **no / know**. Look at the sentence to choose: *I **see** the **sea**.* *Ako has **two** books **too**.*"),
       ("key", "pieges", "Common mistakes", "- Using *their* for place: *Put the box over **there**.*\n- Using *too* for *to*: *She went **to** Buea* (not *too*).\n- Thinking synonyms are exactly the same: *tiny* is much smaller than *small*.\n- Not checking a new word in a dictionary.")],
      [("ex", "Example 1", "Give a synonym and an antonym for 'quick'.", ["A word with the same meaning: fast.", "A word with the opposite meaning: slow."], "synonym: fast; antonym: slow.", None),
       ("ex", "Example 2", "Choose the right word: 'The children left (there / their) bags in the classroom.'", ["Bags belong to the children, so we need the possessive word.", "The word is their."], "their", None)],
      [M("application", "Which word means the opposite of 'empty'?", "full", ["small", "light", "dry"], "Empty and full are opposites."),
       M("application", "Which word means the same as 'start'?", "begin", ["end", "stop", "wait"], "Start and begin are synonyms."),
       G("application", "I will ____ you at school tomorrow. (see / sea)", "see", ["sea", "se", "cee"], "See is the verb; sea is water."),
       TF("approfondissement", "'Two' and 'too' sound the same.", True, "They are homophones."),
       MA("approfondissement", "Match each word with its opposite.", [("tall", "short"), ("early", "late"), ("wet", "dry"), ("old", "young")], "Think of the opposite."),
       P("Read: The (hot / cold) sun made the farmers tired. They sat down and ate (there / their) food. Then they began to (right / write) a letter.",
         [PM("Which word fits the first gap?", "hot", ["cold", "wet", "green"], "A sun is hot."),
          PM("Which word fits the second gap?", "their", ["there", "they're", "thare"], "Their means belonging to them."),
          PM("Which word fits the last gap?", "write", ["right", "rite", "wright"], "To write a letter.", points=2)])],
      [("The antonym of 'light' (not heavy) is...", "heavy", ["thin", "bright", "soft"], "Light and heavy are opposites."),
       ("A synonym of 'big' is...", "large", ["tiny", "narrow", "short"], "Same meaning."),
       ("Words that sound the same but have different spellings are...", "homophones", ["antonyms", "synonyms", "nouns"], "For example see and sea."),
       ("Choose: 'He put the book ___ the table.'", "on", ["one", "own", "of"], "On the table."),
       ("The opposite of 'clean' is...", "dirty", ["new", "wet", "tidy"], "Clean - dirty.")],
      ["Pronunciation-based homophones follow British English; check the regional accents used in the class."])

# ============================================================ 3. reading
ch = p.chapter("reading", "Comprehension", REF)

passage1 = ("Every Saturday, Ngum goes to the market in Bafoussam with his mother. The market opens at six o'clock and it is full of people. "
            "Women sell tomatoes, yams and fresh fish. Men sell shoes and clothes. A boy carries a heavy bag of maize on his head. "
            "Ngum's mother buys a bunch of plantains and some onions. Then Ngum buys a cold drink. 'What a busy morning!' says his mother with a smile.")
assert len(passage1) < 650
fig = boxes(["1. Read the passage two times", "2. Read the question carefully", "3. Find the answer in the passage", "4. Write a full sentence"], size=14)
build(ch, "comprehension-market", "Comprehension: A Saturday at the market", 30,
      ["Read a short passage and answer questions.", "Find facts in the text.", "Answer in full sentences using words from the text."],
      [("text", passage1),
       ("key", "methode", "How to answer", "Read the passage **twice**. Read each question carefully and look for the key words in the passage. Answer with a **full sentence**. For *who, what, where, when* find the fact in the text. For *why* look for a reason: *because*. If a word is new, use the other words in the sentence to guess its meaning."),
       ("fig", fig, "Four steps for comprehension.", "Four boxes: read the passage twice, read the question, find the answer, write a full sentence."),
       ("key", "pieges", "Common mistakes", "- Writing an answer from memory instead of checking the passage.\n- Copying a whole paragraph: use only the part that answers the question.\n- Answering with one word when a full sentence is asked.\n- Mixing up the people: Ngum is the boy, the mother buys the plantains.")],
      [("ex", "Example 1", "Question: Where does Ngum go on Saturday?", ["Look for 'Saturday' and 'goes' in the passage: Ngum goes to the market in Bafoussam.", "Write a full sentence."], "Ngum goes to the market in Bafoussam on Saturday.", None),
       ("ex", "Example 2", "Question: Why does Ngum's mother say 'What a busy morning!'?", ["Look at the sentence before: the market is full of people and many things happen.", "Give a reason with because."], "She says it because the market is full of busy people.", None)],
      [M("application", "At what time does the market open?", "six o'clock", ["seven o'clock", "noon", "eight o'clock"], "The market opens at six o'clock."),
       M("application", "What does the boy carry on his head?", "a heavy bag of maize", ["a bunch of plantains", "a cold drink", "shoes"], "A boy carries a heavy bag of maize."),
       M("application", "Who sells shoes and clothes?", "men", ["women", "children", "farmers only"], "Men sell shoes and clothes."),
       M("approfondissement", "The word 'busy' in the last sentence means...", "full of things to do", ["very quiet", "empty", "very cold"], "A busy morning has a lot of activity."),
       TF("approfondissement", "Ngum buys a bunch of plantains.", False, "His mother buys the plantains; Ngum buys a cold drink."),
       P("Read: Mama Bih sells puff-puff beside the road. She wakes up at five o'clock. She mixes flour, sugar and yeast and fries the puff-puff in hot oil. Many workers buy from her before they go to work.",
         [PM("At what time does Mama Bih wake up?", "five o'clock", ["six o'clock", "noon", "midnight"], "The passage says five o'clock."),
          PM("What does she use to fry the puff-puff?", "hot oil", ["cold water", "milk", "salt only"], "She fries in hot oil."),
          PM("Who buys from her?", "workers", ["teachers only", "nobody", "farmers only"], "Many workers buy before work.", points=2)])],
      [("In the passage, who goes to the market on Saturday?", "Ngum and his mother", ["Ngum's father", "the teacher", "the mother only"], "Ngum goes with his mother."),
       ("The market in the passage is in...", "Bafoussam", ["Buea", "Limbe", "Garoua"], "The passage says Bafoussam."),
       ("What does Ngum's mother buy?", "plantains and onions", ["shoes", "a cold drink", "fish and yams"], "She buys a bunch of plantains and some onions."),
       ("Before answering a comprehension question, we should...", "read the passage carefully", ["guess", "write a poem", "copy the title"], "Find the answer in the text."),
       ("The opposite of 'cold' in 'a cold drink' is...", "hot", ["wet", "dry", "tall"], "Cold and hot are opposites.")],
      ["Original passage written for CastBridge; no real people. Check reading level against the textbook."])

passage2 = ("Ada lived near a small river. One dry season, the river became very low and the village ran short of water. "
            "The people walked a long way to find water. Ada did not complain. She woke up early and carried a small bucket. "
            "On the way she shared some water with an old man who was tired. When the rains came, the river was full again. "
            "The old man thanked Ada and told everyone how kind she was.")
assert len(passage2) < 650
fig = shapes([T(200, 25, "Beginning", 14, bold=True), RECT(40, 35, 320, 28, fill="lightblue"), T(200, 55, "Ada's village has little water", 13),
              T(200, 90, "Middle", 14, bold=True), RECT(40, 100, 320, 28, fill="lightyellow"), T(200, 120, "Ada shares water with an old man", 13),
              T(200, 155, "End", 14, bold=True), RECT(40, 165, 320, 28, fill="lightgreen"), T(200, 185, "The rains come; everyone knows her kindness", 13)], 400, 210)
build(ch, "comprehension-story", "Comprehension: Ada and the river", 30,
      ["Understand the main idea and order of events of a story.", "Say what a character is like from what she does.", "Guess the meaning of new words from the text."],
      [("text", passage2),
       ("fig", fig, "The three parts of the story.", "Three boxes: beginning, the village has little water; middle, Ada shares water with an old man; end, the rains come and everyone knows her kindness."),
       ("key", "retenir", "Types of questions", "**Fact questions** (who, what, where, when): the answer is in the text. **Why / how questions**: look for the reason. **Meaning of a word**: read the sentence around it. **Character questions**: what the person does shows what she is like. **Title questions**: choose a short title that tells the main idea."),
       ("key", "pieges", "Common mistakes", "- Answering with your own idea when the passage gives the answer.\n- Choosing a word meaning that does not fit the sentence.\n- Mixing the order of events: use first, then, at last.\n- Writing opinions as if they were facts.")],
      [("ex", "Example 1", "Why did the village run short of water?", ["Look at the second sentence: the river became very low in the dry season.", "That is the reason."], "Because the river became very low in the dry season.", None),
       ("ex", "Example 2", "What kind of girl is Ada? Give one proof.", ["Ada shares her water with a tired old man.", "This shows that she is kind (and she did not complain, so she is also patient)."], "Ada is kind: she shared her water with the old man.", None)],
      [M("application", "Where did Ada live?", "near a small river", ["near the sea", "on a mountain", "in a big town"], "The story says near a small river."),
       M("application", "What did Ada carry?", "a small bucket", ["a big drum", "a bag of rice", "a stick"], "A small bucket."),
       M("application", "When did the river become full again?", "when the rains came", ["in the dry season", "when the old man left", "never"], "After the rains."),
       M("approfondissement", "The word 'complain' means...", "say you are unhappy about something", ["laugh loudly", "sing a song", "run fast"], "She did not complain, so she did not grumble."),
       TF("approfondissement", "The old man was tired.", True, "The text says that he was tired."),
       P("Read: Neba found a purse on the road near the school. There was money and a card inside. He did not keep it. He gave it to the teacher, who found its owner. The owner thanked Neba.",
         [PM("What did Neba find?", "a purse", ["a card only", "a goat", "a book"], "He found a purse."),
          PM("What does the story show about Neba?", "he is honest", ["he is lazy", "he is angry", "he is rich"], "He did not keep the money."),
          PM("Which is the best title?", "Neba the honest boy", ["A day at the sea", "The big market", "The lost goat"], "It tells the main idea.", points=2)])],
      [("In the story, why did the people walk a long way?", "to find water", ["to visit the sea", "to buy food", "to play"], "The river was low."),
       ("Ada shared her water with...", "an old man", ["a goat", "her teacher", "a trader"], "The old man was tired."),
       ("What does the story show about Ada?", "she is kind", ["she is lazy", "she is rich", "she is afraid"], "She shared and did not complain."),
       ("A good title for the story is...", "Ada and the river", ["The football match", "A trip to the sea", "The new bus"], "It tells the main idea."),
       ("The 'dry season' is a time with little...", "rain", ["sun", "wind", "light"], "Dry means no rain.")],
      ["Original story; the moral (kindness) is simple on purpose."])

# ============================================================ 4. writing
ch = p.chapter("writing", "Writing: letters, stories and descriptions", REF)

fig = shapes([T(300, 20, "12 Church Street, Bamenda", 12, anchor="end"), T(300, 38, "10 May 2026", 12, anchor="end"),
              T(30, 70, "Dear Aunt Mary,", 13, anchor="start", bold=True), T(30, 100, "I hope you are well. I am fine. We have a new teacher...", 12, anchor="start"),
              T(30, 125, "Please write soon.", 12, anchor="start"), T(300, 160, "Your loving niece,", 13, anchor="end", bold=True), T(300, 180, "Ada", 13, anchor="end")], 400, 200)
build(ch, "informal-letter", "Writing a friendly letter", 30,
      ["Know the parts of a friendly letter.", "Write a short friendly letter in good order.", "Use a polite beginning and ending."],
      [("key", "definition", "Parts of a friendly letter", "A friendly (informal) letter has: 1. the writer's **address**; 2. the **date**; 3. the **greeting** (*Dear Aunt Mary,*); 4. the **body** (paragraphs: why you write, news, questions); 5. the **ending** (*Please write soon.*); 6. the **closing** (*Your loving niece,*) and your **name**."),
       ("fig", fig, "The layout of a friendly letter.", "A letter with the address and date at the top right, Dear Aunt Mary on the left, a short body, and Your loving niece, Ada at the bottom right."),
       ("key", "methode", "Writing the body", "In the first paragraph say **why you are writing** and ask how the person is. In the next paragraph give your **news** (school, family, something fun). In the last paragraph **ask questions** and **say goodbye**. Use short sentences, correct tenses and your own words."),
       ("key", "pieges", "Common mistakes", "- Forgetting the date or the address.\n- A comma is needed after the greeting and after the closing.\n- Starting with *I am writing to inform you*: that is a formal style.\n- Writing everything in one paragraph.")],
      [("ex", "Example 1", "Write the greeting and the closing of a letter from Ada to her cousin Neba.", ["Greeting: Dear Neba,  (capital D, comma).", "Closing: Your loving cousin,  followed by the name: Ada."], "Dear Neba, ... Your loving cousin, Ada", None),
       ("ex", "Example 2", "Put these parts in order: closing, address, body, date, greeting.", ["The letter starts with the address and the date at the top.", "Then comes the greeting, the body and the closing."], "address, date, greeting, body, closing.", None)],
      [M("application", "Which is a good greeting for a friendly letter?", "Dear Ako,", ["To whom it may concern", "Dear Sir,", "Hello Ako."], "Dear + name + comma."),
       M("application", "Which closing is for a friendly letter?", "Your friend,", ["Yours faithfully,", "Yours sincerely,", "Thank you."], "Friendly letters end warmly."),
       M("application", "Where does the date go?", "at the top, under the address", ["at the end", "after the greeting", "in the middle"], "At the top right."),
       TF("approfondissement", "In a friendly letter we ask the person how he or she is.", True, "It is polite to ask."),
       MA("approfondissement", "Match each part with its example.", [("greeting", "Dear Bih,"), ("closing", "Your friend,"), ("date", "5 June 2026"), ("ending", "Please write soon.")], "These are the usual parts."),
       O("Write a friendly letter of about 60 words to a friend in another town. Tell your friend about your school and ask two questions.",
         "12 School Road, Limbe\n3 March 2026\nDear Bih,\nI hope you are well. I am fine. This term we have a new teacher and she is very kind. Our class won the football match on Friday. How is your family? What is your school like?\nPlease write soon.\nYour friend,\nAko",
         "Marks (6): address and date (1); greeting and closing with correct punctuation (1); a clear body with news about school (2); two questions (1); correct spelling, capital letters and full stops (1).", tier="examen", points=6, difficulty=2)],
      [("A friendly letter starts with the...", "address and the date", ["closing", "title only", "a poem"], "They go at the top."),
       ("The greeting of a friendly letter looks like...", "Dear Neba,", ["Dear Sir/Madam", "Neba", "To the Principal"], "Dear + name + comma."),
       ("Which sentence belongs to the end of a letter?", "Please write soon.", ["Dear Aunt Mary,", "12 Main Street", "My name is Ada."], "An ending sentence."),
       ("How many paragraphs does a good short friendly letter have?", "more than one", ["only one", "none", "ten"], "Introduction, news and goodbye."),
       ("The writer's name comes...", "at the end after the closing", ["at the start", "after the date", "after the greeting"], "Your friend, Ako.")],
      ["Layout (right-aligned address) may differ in local textbooks; the model letter is original."])

fig = shapes([RECT(20, 20, 110, 70, fill="lightblue", radius=6), T(75, 45, "Beginning", 14, bold=True), T(75, 68, "who, where", 12),
              RECT(145, 20, 110, 70, fill="lightyellow", radius=6), T(200, 45, "Middle", 14, bold=True), T(200, 68, "the problem", 12),
              RECT(270, 20, 110, 70, fill="lightgreen", radius=6), T(325, 45, "End", 14, bold=True), T(325, 68, "the solution", 12)], 400, 110)
build(ch, "story-description", "Writing a story and a description", 35,
      ["Plan a story with a beginning, a middle and an end.", "Describe a person or a place using the five senses.", "Write in the past tense with good sentences."],
      [("key", "methode", "Planning a story", "A story has three parts. **Beginning**: who, where and when. **Middle**: a problem happens. **End**: the problem is solved and we learn something. Write in the **past tense** and use **time words**: *one day, then, after that, at last*. Give your story a short **title**."),
       ("fig", fig, "The three parts of a story.", "Three boxes: beginning with who and where; middle with the problem; end with the solution."),
       ("key", "methode", "Describing", "To describe a person, say how he or she **looks** (tall, thin), **moves** and **behaves** (kind, shy). To describe a place, use the **five senses**: what you **see, hear, smell, taste and feel**. Use adjectives and adverbs: *The market is noisy and colourful. It smells of fresh fish and ripe mangoes.*"),
       ("key", "pieges", "Common mistakes", "- A story with no problem: nothing happens.\n- Changing tenses: *He walked ... and then he sees*.\n- Starting every sentence with *Then*.\n- Writing a list of adjectives instead of full sentences.")],
      [("ex", "Example 1", "Write two sentences that begin a story about Neba and a lost goat.", ["Say who, where and when.", "One morning, Neba was walking to school in Kumba. He heard a loud 'meh' behind a bush and found a lost goat."], "One morning, Neba was walking to school in Kumba. He heard a loud 'meh' behind a bush and found a lost goat.", None),
       ("ex", "Example 2", "Describe a market using three senses.", ["See: colourful cloths and piles of tomatoes. Hear: traders shouting prices.", "Smell: fresh fish and roasted plantain."], "The market is colourful and noisy, and it smells of fresh fish and roasted plantain.", None)],
      [M("application", "Which part of a story tells the problem?", "the middle", ["the title", "the beginning only", "the end only"], "The problem happens in the middle."),
       M("application", "In which tense do we usually write a story?", "past tense", ["future tense", "present only", "no tense"], "We tell what happened."),
       M("application", "Which is a time word for a story?", "one day", ["quickly", "green", "table"], "One day starts many stories."),
       TF("approfondissement", "The five senses are sight, hearing, smell, taste and touch.", True, "Use them to describe places."),
       MA("approfondissement", "Match each sense with an example.", [("see", "red tomatoes"), ("hear", "loud music"), ("smell", "roasted fish"), ("feel", "rough sack")], "Each sense gives a different detail."),
       O("Write a short story (about 70 words) called 'The Lost Goat'. Include a beginning, a problem and an end.",
         "One morning, Neba was walking to school in Kumba when he heard a sad 'meh' behind a bush. A small goat was caught in some wire. Neba carefully freed it, but it ran away. He followed it to a house where a woman was crying. 'My goat!' she said. Neba took the goat to her and she thanked him. He arrived at school late, but proud.",
         "Marks (6): a clear beginning with who, where and when (1); a problem in the middle (1); a solution at the end (1); past tense kept (1); good use of time words and sentences (1); spelling, capital letters and full stops (1).", tier="examen", points=6, difficulty=2)],
      [("A story usually ends with...", "the solution", ["a new problem every time", "only a title", "the date"], "The problem is solved."),
       ("Which sentence is in the past tense?", "The boy walked home.", ["The boy walks home.", "The boy will walk home.", "The boy walking home."], "Walked is past."),
       ("To describe a place we use...", "the five senses", ["only numbers", "only names", "a map only"], "See, hear, smell, taste and touch."),
       ("Which time word starts a story well?", "One morning", ["Because", "Under", "Green"], "A time phrase."),
       ("The middle of a story has...", "the problem", ["the title", "the date", "the solution only"], "Something goes wrong.")],
      ["The model texts are original; marking criteria are indicative."])

# ============================================================ 5. poems and oral
ch = p.chapter("oral-poetry", "Poems and oral English", REF)

poem = "Rain on the zinc roof,\ntap, tap, tap, tap, tap.\nRain on the green leaves,\nsplash, splash, splash, splash, splash."
fig = shapes([T(200, 28, "Rain on the zinc roof,", 15, bold=True), T(200, 52, "tap, tap, tap, tap, tap.", 15), T(200, 82, "Rain on the green leaves,", 15, bold=True), T(200, 106, "splash, splash, splash, splash, splash.", 15),
              T(200, 142, "Sound words: tap, splash", 13, color="red", bold=True)], 400, 160)
build(ch, "poems", "Poems: rhyme, rhythm and sound words", 25,
      ["Read a poem aloud with expression.", "Find rhyming words and sound words.", "Say how a poem makes us feel."],
      [("text", "Here is a short poem written for this lesson.\n\n*Rain on the zinc roof, tap, tap, tap, tap, tap. Rain on the green leaves, splash, splash, splash, splash, splash. The street is a river, the children all cheer. Rain, rain, stay and play! We are so glad you are here.*"),
       ("fig", fig, "The first two lines of the poem and its sound words.", "The lines Rain on the zinc roof, tap tap tap tap tap and Rain on the green leaves, splash splash splash splash splash, with the sound words tap and splash named below."),
       ("key", "definition", "Parts of a poem", "A poem is written in **lines**. A group of lines is a **stanza**. Words that **rhyme** end with the same sound: *cheer - here*. **Rhythm** is the beat of the lines. **Sound words** (onomatopoeia) sound like what they mean: *tap, splash, buzz, bang*. A poet may repeat words to make a pattern."),
       ("key", "retenir", "Reading a poem aloud", "Read slowly. Pause at commas and full stops. Make your voice **soft** for quiet lines and **loud** for happy lines. Say the **sound words** with feeling. Ask: *What picture do I see? How does the poet feel?*"),
       ("key", "pieges", "Common mistakes", "- Thinking that words rhyme because they begin alike: rhyme comes at the **end** (cheer - here).\n- Reading a poem like a list, without pauses.\n- Saying only what the poem is about, without saying how it feels.")],
      [("ex", "Example 1", "Find a pair of rhyming words in: 'The children all cheer. We are so glad you are here.'", ["Look at the last word of each line.", "Cheer and here end with the same sound."], "cheer - here", None),
       ("ex", "Example 2", "Name two sound words from the poem.", ["Read the poem and look for words that sound like their meaning.", "Tap and splash."], "tap, splash", None)],
      [M("application", "Which word rhymes with 'cheer'?", "here", ["care", "chair", "cherry"], "Cheer and here have the same ending sound."),
       M("application", "Which is a sound word in the poem?", "splash", ["roof", "children", "green"], "Splash sounds like water."),
       M("application", "A group of lines in a poem is called a...", "stanza", ["chapter", "paragraph", "title"], "Poems have stanzas."),
       TF("approfondissement", "'Buzz' is a sound word.", True, "It sounds like a bee."),
       MA("approfondissement", "Match the rhyming words.", [("cat", "hat"), ("tree", "bee"), ("night", "light"), ("sea", "key")], "They end with the same sound."),
       O("Write four lines about rain or sun. Use at least two rhyming words and one sound word.",
         "The sun is hot, the sky is blue,\nThe birds all sing, 'tweet, tweet, too!'\nWe play outside, the day is bright,\nAnd we go home when comes the night.",
         "Marks (4): four lines (1); at least one pair of rhyming words (1); a sound word (1); the poem makes sense and has a feeling (1).", tier="examen", points=4, difficulty=2)],
      [("Words that end with the same sound...", "rhyme", ["rhythm", "stanza", "title"], "Cheer - here."),
       ("A sound word is also called...", "onomatopoeia", ["synonym", "pronoun", "paragraph"], "Words like splash and bang."),
       ("The poem 'Rain on the zinc roof' has the sound word...", "tap", ["blue", "green", "cheer"], "Tap sounds like rain."),
       ("When we read a poem aloud we should...", "pause at commas and full stops", ["read as fast as possible", "whisper only", "skip the sound words"], "Pauses help the meaning."),
       ("Which pair rhymes?", "night - light", ["cat - dog", "sun - moon", "rain - hot"], "Same ending sound.")],
      ["Original poem written for CastBridge. Whether rhyme schemes are taught at Class 4 to be checked."])

fig = boxes(["Hello! Good morning, Madam.", "How are you? I am fine, thank you.", "Excuse me, please. Thank you very much.", "I am sorry. That is all right."], size=14)
build(ch, "oral-english", "Oral English: greetings, polite words and sounds", 25,
      ["Use greetings and polite expressions.", "Tell short vowel and long vowel sounds apart (ship / sheep).", "Speak clearly, in a polite voice."],
      [("key", "definition", "Polite words", "Use polite words every day. **Greetings**: *Good morning, Good afternoon, Good evening, Hello.* **Asking**: *Excuse me, please. May I come in? Could you help me?* **Thanking**: *Thank you (very much). You are welcome.* **Saying sorry**: *I am sorry. That is all right.* Look at the person and speak in a clear voice."),
       ("fig", fig, "Polite expressions for everyday talking.", "Four boxes with polite phrases: greetings, asking how someone is, excuse me and thank you, saying sorry."),
       ("key", "retenir", "Sounds", "Some words differ only by a **short** or **long** vowel: **ship / sheep**, **bit / beat**, **full / fool**, **pull / pool**. Listen carefully and say them slowly. Also practise the **th** sounds in *thank* and *this*, and say the **ends** of words clearly: *hand, bed, cold*."),
       ("key", "pieges", "Common mistakes", "- Speaking too fast or too softly, so that nobody can hear.\n- Forgetting *please* and *thank you*.\n- Dropping the end of a word: *han* instead of *hand*.\n- Mixing words that sound alike (ship/sheep).")],
      [("ex", "Example 1", "What do you say when you enter the headmaster's office?", ["First knock, then say a polite sentence.", "Excuse me, sir. May I come in, please?"], "Excuse me, sir. May I come in, please?", None),
       ("ex", "Example 2", "Which word has the long vowel: 'ship' or 'sheep'?", ["In 'ship' the vowel is short.", "In 'sheep' the vowel is long: sheeeep."], "sheep", None)],
      [M("application", "What do we say when someone thanks us?", "You are welcome.", ["Excuse me.", "Good night.", "I am sorry."], "It is the polite answer."),
       M("application", "Which greeting is best at 8 a.m.?", "Good morning", ["Good night", "Good evening", "Good afternoon"], "Morning is before noon."),
       M("application", "What do you say if you step on a friend's foot by mistake?", "I am sorry.", ["Thank you.", "Good morning.", "You are welcome."], "Say sorry."),
       TF("approfondissement", "'Ship' and 'sheep' have the same vowel sound.", False, "Ship has a short vowel; sheep has a long one."),
       MA("approfondissement", "Match each situation with the best words.", [("meeting your teacher in the morning", "Good morning, Madam."), ("asking to pass", "Excuse me, please."), ("receiving a gift", "Thank you very much."), ("being late", "I am sorry I am late.")], "Choose polite words."),
       O("Write a short conversation (4 lines) between a pupil and the teacher when the pupil arrives late.",
         "Pupil: Good morning, Madam. I am sorry I am late.\nTeacher: Good morning, Ako. Why are you late?\nPupil: The bus was late, Madam.\nTeacher: All right. Please sit down quietly. Thank you.",
         "Marks (4): a greeting (1); an apology (1); a reason (1); polite words (please / thank you) and correct punctuation (1).", tier="examen", points=4, difficulty=2)],
      [("A polite way to ask for something is...", "Please may I have a pen?", ["Give me a pen.", "Pen!", "You have a pen?"], "Use please and may I."),
       ("The answer to 'Thank you' is...", "You are welcome", ["Good night", "Excuse me", "Sorry"], "Polite reply."),
       ("Which pair differs by a long and a short vowel?", "bit / beat", ["cat / dog", "red / blue", "run / jump"], "Short i and long ee."),
       ("When we speak we should...", "speak clearly and politely", ["shout", "whisper", "speak very fast"], "Good manners."),
       ("What do we say to enter a classroom late?", "Excuse me, may I come in?", ["Hello everybody", "Move!", "Good night"], "Excuse me and ask politely.")],
      ["Pronunciation points follow standard British models; a teacher should adapt to the class and local accents. Audio is not included."])

p.write()
