"""English language: verb forms, comparatives, plurals, prepositions, conditionals, reported speech. The questions are written in
English for the English class of the francophone lycée (Anglais) and for GCE English Language. The forms come from the tables below
(standard British English); each question has exactly one correct form among distinct wrong ones."""
from .. import lycee
from ..core import Draft, gen

COURSES = [lycee.fr("2nde", "angl"), lycee.fr("1re", "angl"), lycee.fr("tle", "angl"), lycee.en("f5", "lang"), lycee.en("l6", "lang"), lycee.en("u6", "lang")]
SRC = "English grammar - standard British forms (tools/quiz-bank tables)"

IRREGULAR = [("be", "was/were", "been"), ("begin", "began", "begun"), ("break", "broke", "broken"), ("bring", "brought", "brought"), ("build", "built", "built"), ("buy", "bought", "bought"),
             ("catch", "caught", "caught"), ("choose", "chose", "chosen"), ("come", "came", "come"), ("do", "did", "done"), ("draw", "drew", "drawn"), ("drink", "drank", "drunk"), ("drive", "drove", "driven"),
             ("eat", "ate", "eaten"), ("fall", "fell", "fallen"), ("feel", "felt", "felt"), ("fight", "fought", "fought"), ("find", "found", "found"), ("fly", "flew", "flown"), ("forget", "forgot", "forgotten"),
             ("give", "gave", "given"), ("go", "went", "gone"), ("grow", "grew", "grown"), ("have", "had", "had"), ("hear", "heard", "heard"), ("hide", "hid", "hidden"), ("hold", "held", "held"),
             ("keep", "kept", "kept"), ("know", "knew", "known"), ("leave", "left", "left"), ("lend", "lent", "lent"), ("lose", "lost", "lost"), ("make", "made", "made"), ("meet", "met", "met"),
             ("pay", "paid", "paid"), ("ride", "rode", "ridden"), ("ring", "rang", "rung"), ("rise", "rose", "risen"), ("run", "ran", "run"), ("say", "said", "said"), ("see", "saw", "seen"),
             ("sell", "sold", "sold"), ("send", "sent", "sent"), ("sing", "sang", "sung"), ("sit", "sat", "sat"), ("sleep", "slept", "slept"), ("speak", "spoke", "spoken"), ("spend", "spent", "spent"),
             ("stand", "stood", "stood"), ("steal", "stole", "stolen"), ("swim", "swam", "swum"), ("take", "took", "taken"), ("teach", "taught", "taught"), ("tell", "told", "told"),
             ("think", "thought", "thought"), ("throw", "threw", "thrown"), ("understand", "understood", "understood"), ("wake", "woke", "woken"), ("wear", "wore", "worn"), ("win", "won", "won"), ("write", "wrote", "written")]


def regularised(base):
    return base + ("d" if base.endswith("e") else "ed")


def reg(tpl, cap, cat, diffs=(1, 2, 3, 4, 5)):
    def deco(fn):
        for c in COURSES:
            gen(c, tpl, cap=cap, cat=cat, diffs=diffs, source=SRC)(fn)
        return fn
    return deco


@reg("en-irregular-past", 80, "Verbs")
def irregular_past(rng, d):
    base, past, pp = rng.choice(IRREGULAR)
    if base == "be":
        return None
    forms = {x for _, a, b in IRREGULAR for x in (a, b)}
    wr = [base, pp if pp != past else regularised(base), regularised(base)] + rng.sample(sorted(forms - {past, base, pp}), 3)
    return Draft(f"What is the past simple of the verb 'to {base}'?", past, wr, f"{base} - {past} - {pp}.", src=SRC)


@reg("en-irregular-participle", 80, "Verbs")
def irregular_participle(rng, d):
    base, past, pp = rng.choice(IRREGULAR)
    if base == "be" or past == pp:
        return None
    forms = {x for _, a, b in IRREGULAR for x in (a, b)}
    wr = [base, past, regularised(base)] + rng.sample(sorted(forms - {past, base, pp}), 3)
    return Draft(f"What is the past participle of the verb 'to {base}'?", pp, wr, f"{base} - {past} - {pp}.", src=SRC)


@reg("en-present-perfect", 400, "Tenses", diffs=(2, 3, 4))
def present_perfect(rng, d):
    base, past, pp = rng.choice([v for v in IRREGULAR if v[0] in TRANSITIVE])
    subj, aux = rng.choice([("I", "have"), ("She", "has"), ("They", "have"), ("My brother", "has"), ("We", "have"), ("The students", "have"), ("He", "has"), ("My parents", "have")])
    marker = rng.choice(["already", "just", "never", "recently"])
    right = f"{aux} {pp}"
    other = "has" if aux == "have" else "have"
    wr = [f"{aux} {past}" if past != pp else f"{aux} {base}", f"{aux} {base}", f"{other} {pp}", f"did {pp}", f"{aux} {regularised(base)}"]
    sent = f"{subj} ___ {marker} {'' if marker != 'recently' else ''}".strip()
    text = {"already": f"{subj} ___ already (to {base}) that.", "just": f"{subj} ___ just (to {base}) it.", "never": f"{subj} ___ never (to {base}) that.", "recently": f"{subj} ___ (to {base}) it recently."}[marker]
    return Draft(f"{text} Which form completes the sentence correctly?", right, wr, f"The present perfect is have/has + past participle ({pp}).", src=SRC)


@reg("en-past-simple-sentence", 400, "Tenses", diffs=(1, 2, 3))
def past_simple_sentence(rng, d):
    base, past, pp = rng.choice([v for v in IRREGULAR if v[0] in TRANSITIVE])
    subj = rng.choice(["I", "She", "They", "My uncle", "We", "The teacher", "He", "The children"])
    marker = rng.choice(["yesterday", "last week", "two days ago", "in 2019", "last year"])
    wr = [f"{base}", f"has {pp}" if subj in ("She", "He", "My uncle", "The teacher") else f"have {pp}", f"{regularised(base)}", f"did {past}", f"was {base}ing"]
    return Draft(f"{subj} ___ it {marker} (to {base}). Which form completes the sentence?", past, wr, f"With {marker} we use the past simple: {past}.", src=SRC)


@reg("en-comparative", 150, "Adjectives", diffs=(1, 2, 3))
def comparative(rng, d):
    adj = [("good", "better", "best"), ("bad", "worse", "worst"), ("little", "less", "least"), ("much", "more", "most"), ("big", "bigger", "biggest"), ("happy", "happier", "happiest"),
           ("easy", "easier", "easiest"), ("hot", "hotter", "hottest"), ("beautiful", "more beautiful", "most beautiful"), ("expensive", "more expensive", "most expensive"), ("thin", "thinner", "thinnest"),
           ("busy", "busier", "busiest"), ("interesting", "more interesting", "most interesting"), ("large", "larger", "largest"), ("dangerous", "more dangerous", "most dangerous")]
    a, comp, sup = rng.choice(adj)
    if rng.random() < 0.5:
        wr = [a + "er" if not a.endswith("e") else a + "r", "more " + a, "most " + a, comp.replace("er", "est") if comp.endswith("er") else "most " + a + "er", a + "est"]
        return Draft(f"What is the comparative form of '{a}'?", comp, [w for w in wr if w != comp], f"{a} - {comp} - {sup}.", src=SRC)
    wr = [a + "est" if not a.endswith("e") else a + "st", "most " + a if not sup.startswith("most") else "more " + a, "more " + a, comp, "the " + a + "est" if False else a + "iest"]
    return Draft(f"What is the superlative form of '{a}'?", sup, [w for w in wr if w != sup], f"{a} - {comp} - {sup}.", src=SRC)


PLURALS = [("child", "children"), ("man", "men"), ("woman", "women"), ("foot", "feet"), ("tooth", "teeth"), ("mouse", "mice"), ("goose", "geese"), ("ox", "oxen"),
           ("leaf", "leaves"), ("knife", "knives"), ("wife", "wives"), ("life", "lives"), ("wolf", "wolves"), ("city", "cities"), ("baby", "babies"), ("box", "boxes"), ("church", "churches"),
           ("potato", "potatoes"), ("tomato", "tomatoes"), ("analysis", "analyses"), ("crisis", "crises"), ("phenomenon", "phenomena"), ("criterion", "criteria"), ("bus", "buses"), ("hero", "heroes"), ("thief", "thieves"), ("half", "halves")]


@reg("en-plural", 80, "Nouns", diffs=(1, 2, 3))
def plural(rng, d):
    s, p = rng.choice(PLURALS)
    wr = [s + "s", s + "es", s + "ies", p + "s", s[:-1] + "ves" if s[-1] in "fe" else s + "en"]
    return Draft(f"What is the plural of '{s}'?", p, [w for w in wr if w != p], f"{s} - {p}.", src=SRC)


PREP = [("I wake up ___ 6 o'clock.", "at", ["on", "in", "by", "to"]), ("We have no school ___ Sunday.", "on", ["at", "in", "by", "for"]), ("She was born ___ July.", "in", ["on", "at", "by", "from"]),
        ("My birthday is ___ 12 May.", "on", ["in", "at", "by", "for"]), ("They arrived ___ the morning.", "in", ["on", "at", "by", "to"]), ("He has lived here ___ 2015.", "since", ["for", "from", "in", "at"]),
        ("She has studied English ___ five years.", "for", ["since", "from", "in", "at"]), ("The meeting will end ___ midnight.", "at", ["on", "in", "by", "for"]), ("I'll finish the work ___ Friday (not later than Friday).", "by", ["until", "since", "on", "at"]),
        ("He is good ___ mathematics.", "at", ["in", "on", "with", "for"]), ("She is afraid ___ snakes.", "of", ["from", "at", "with", "about"]), ("I am interested ___ history.", "in", ["on", "at", "about", "for"]),
        ("Please listen ___ the teacher.", "to", ["at", "for", "on", "in"]), ("He is waiting ___ the bus.", "for", ["at", "to", "on", "by"]), ("We depend ___ rain for our crops.", "on", ["in", "of", "at", "from"]),
        ("She is married ___ a doctor.", "to", ["with", "by", "at", "for"]), ("The book is ___ the table.", "on", ["to", "in", "by", "from"]), ("The cat is hiding ___ the bed.", "under", ["over", "above", "into", "towards"]),
        ("We swam ___ the river to reach the other side.", "across", ["along", "between", "among", "beside"]), ("I congratulate you ___ your success.", "on", ["for", "at", "in", "about"]), ("She apologised ___ being late.", "for", ["of", "about", "at", "to"]), ("He was accused ___ stealing.", "of", ["for", "with", "about", "to"])]


@reg("en-prepositions", 60, "Prepositions", diffs=(1, 2, 3))
def prepositions(rng, d):
    s, r, w = rng.choice(PREP)
    return Draft(f"{s} Which word completes the sentence?", r, w, f"The correct preposition here is '{r}'.", src=SRC)


COND = [("If it rains tomorrow, we ___ at home.", "will stay", ["would stay", "stayed", "would have stayed", "stay not"], "First conditional: if + present, will + verb."),
        ("If I were rich, I ___ a big house.", "would buy", ["will buy", "bought", "would have bought", "buy"], "Second conditional: if + past, would + verb."),
        ("If she had studied, she ___ the exam.", "would have passed", ["would pass", "will pass", "passed", "had passed"], "Third conditional: if + past perfect, would have + past participle."),
        ("If you heat water to 100 °C, it ___.", "boils", ["will boiled", "would boil", "boiling", "boiled"], "Zero conditional: general truth, present simple in both clauses."),
        ("If he ___ harder, he would succeed.", "worked", ["works", "will work", "would work", "had worked"], "Second conditional: if + past simple."),
        ("Unless you hurry, you ___ the bus.", "will miss", ["would miss", "missed", "have missed", "miss not"], "Unless = if not; first conditional.")]


@reg("en-conditionals", 30, "Conditionals", diffs=(3, 4, 5))
def conditionals(rng, d):
    s, r, w, e = rng.choice(COND)
    return Draft(f"{s} Which form completes the sentence?", r, w, e, src=SRC)


TRANSITIVE = {"break", "bring", "build", "buy", "catch", "choose", "do", "draw", "drink", "drive", "eat", "find", "forget", "give", "have", "hear", "hide", "hold", "keep", "know", "leave", "lend", "lose", "make",
              "meet", "pay", "sell", "send", "see", "steal", "take", "teach", "tell", "throw", "understand", "wear", "win", "write"}
REPORTED = [("She said, 'I am tired.'", "She said that she was tired.", ["She said that she is tired.", "She said that I was tired.", "She said that she will be tired.", "She said me that she was tired."]),
            ("He said, 'I will come tomorrow.'", "He said that he would come the next day.", ["He said that he will come tomorrow.", "He said that I would come tomorrow.", "He said that he comes the next day.", "He said that he would came the next day."]),
            ("They said, 'We have finished.'", "They said that they had finished.", ["They said that we have finished.", "They said that they have finished.", "They said that they finished had.", "They said they were finish."]),
            ("She asked, 'Where do you live?'", "She asked where I lived.", ["She asked where do I live.", "She asked where I live did.", "She asked me where did I live.", "She asked where you lives."]),
            ("He said, 'I saw her yesterday.'", "He said that he had seen her the day before.", ["He said that he saw her yesterday.", "He said that he sees her the day before.", "He said that I had seen her yesterday.", "He said he has seen her the day before."])]


@reg("en-reported-speech", 30, "Reported speech", diffs=(3, 4, 5))
def reported(rng, d):
    s, r, w = rng.choice(REPORTED)
    return Draft(f"{s} Which sentence reports this correctly?", r, w, "In reported speech the tense moves back and time/place words change.", src=SRC)


@reg("en-articles", 60, "Articles", diffs=(1, 2, 3))
def articles(rng, d):
    items = [("She is ___ honest girl.", "an", ["a", "the", "no article"]), ("He plays ___ guitar very well.", "the", ["a", "an", "no article"]), ("I need ___ umbrella.", "an", ["a", "the", "no article"]),
             ("___ sun rises in the east.", "The", ["A", "An", "No article"]), ("My sister is ___ university student.", "a", ["an", "the", "no article"]), ("She goes to ___ school every day (as a pupil).", "no article", ["a", "an", "the"]),
             ("We ate ___ orange and ___ apple.", "an / an", ["a / a", "an / a", "the / an"]), ("He wants to be ___ engineer.", "an", ["a", "the", "no article"]), ("___ Nile is a long river.", "The", ["A", "An", "No article"]),
             ("Cameroon is ___ African country.", "an", ["a", "the", "no article"])]
    s, r, w = rng.choice(items)
    return Draft(f"{s} Which answer fills the gap correctly?", r, w, "Use 'an' before a vowel sound, 'a' before a consonant sound, 'the' for something specific.", src=SRC)
