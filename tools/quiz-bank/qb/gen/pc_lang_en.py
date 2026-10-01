"""English language for the anglophone courses (Class 1-6, Form 1-3): plurals, articles, verb forms, comparison, prepositions,
homophones, spelling, vocabulary. Forms are produced by rules and hand-written tables (irregular verbs, plurals) and the answer
always follows from the table. Syllabus: Cameroon primary (MINEDUB) and secondary (MINESEC) English Language."""
import random

from ..core import Draft, norm
from .pc_common import EN_COURSES, reg

ENC = ["class1", "class2", "class3", "class4", "class5", "class6", "form1", "form2", "form3"]
E2 = ENC[1:]
E3 = ENC[2:]
E4 = ENC[3:]
SRC = "Official English syllabus (MINEDUB / MINESEC) - forms produced by rules and tables to be reviewed by a teacher"

PLURAL_REG = ["book", "pen", "table", "chair", "dog", "cat", "mango", "banana", "school", "teacher", "village", "river", "tree", "car", "bag", "desk", "door", "window", "flower", "orange", "pupil", "friend", "road", "market",
              "student", "farmer", "girl", "boy", "street", "town", "pencil", "ruler", "eraser", "lesson", "answer", "question", "country", "city", "story", "key", "day", "toy", "boat", "plane", "egg", "hand", "arm", "leg"]
PLURAL_ES = [("bus", "buses"), ("box", "boxes"), ("class", "classes"), ("dish", "dishes"), ("watch", "watches"), ("brush", "brushes"), ("glass", "glasses"), ("church", "churches"), ("fox", "foxes"), ("bench", "benches"),
             ("tomato", "tomatoes"), ("potato", "potatoes"), ("kiss", "kisses"), ("match", "matches"), ("wish", "wishes")]
PLURAL_IES = [("baby", "babies"), ("city", "cities"), ("country", "countries"), ("story", "stories"), ("lady", "ladies"), ("family", "families"), ("body", "bodies"), ("party", "parties"), ("berry", "berries"), ("fly", "flies")]
PLURAL_VES = [("leaf", "leaves"), ("knife", "knives"), ("wife", "wives"), ("life", "lives"), ("thief", "thieves"), ("wolf", "wolves"), ("shelf", "shelves"), ("half", "halves"), ("loaf", "loaves"), ("calf", "calves")]
PLURAL_IRR = [("man", "men"), ("woman", "women"), ("child", "children"), ("foot", "feet"), ("tooth", "teeth"), ("mouse", "mice"), ("person", "people"), ("goose", "geese"), ("sheep", "sheep"), ("fish", "fish"), ("ox", "oxen")]
PLURALS = [(w, w + "s") for w in PLURAL_REG] + PLURAL_ES + PLURAL_IES + PLURAL_VES + PLURAL_IRR


def plural_bad(s, p):
    third = (s[:-1] + "ies") if s.endswith("y") else (s[:-1] + "ves") if s.endswith(("f", "fe")) and False else (s + "en")
    cands = [s + "s", s + "es", third, s[:-1] + "ves" if s.endswith("f") else s + "ren", s + "e"]
    return [c for c in dict.fromkeys(cands) if c != p and c != s]


def plural_q(rng, d, g, lg):
    pool = PLURALS if (g >= 3 or d >= 3) else [(w, w + "s") for w in PLURAL_REG] + PLURAL_ES[:6] + PLURAL_IRR[:6]
    s, p = rng.choice(pool)
    return Draft(f"What is the plural of '{s}'?", p, plural_bad(s, p), f"The plural of '{s}' is '{p}'.")


VOW_SOUND = {"apple", "orange", "egg", "elephant", "umbrella", "ant", "arm", "ear", "eye", "island", "ink", "ice cream", "owl", "uncle", "animal", "airplane", "answer", "African", "hour", "honest", "old man",
             "elder", "engine", "exam", "envelope", "office", "onion", "orphan", "eraser", "artist", "army"}
CONS_SOUND = {"book", "pen", "table", "chair", "dog", "cat", "mango", "banana", "school", "teacher", "village", "river", "tree", "car", "bag", "desk", "door", "window", "flower", "pupil", "friend", "road",
              "market", "student", "farmer", "girl", "boy", "street", "town", "pencil", "ruler", "lesson", "question", "country", "city", "story", "key", "day", "toy", "boat", "plane", "hand", "university", "uniform", "unit", "one-eyed man",
              "European", "useful book", "hotel", "horse", "house", "hat", "ball", "bottle", "lion", "monkey", "fish", "kite", "goat", "nurse", "doctor", "driver", "baker", "queen"}


def article_q(rng, d, g, lg):
    if rng.random() < 0.5:
        w = rng.choice(sorted(VOW_SOUND - {"hour", "honest", "old man"} if d < 4 else VOW_SOUND))
    else:
        w = rng.choice(sorted(CONS_SOUND))
    an = w in VOW_SOUND
    right = "an" if an else "a"
    return Draft(f"Which word completes the phrase '… {w}'?", right, ["a" if an else "an", "the", "some"] if False else ["a" if an else "an", "some", "many", "two"],
                 f"We use '{right}' before the sound of '{w}': '{right} {w}'.")


PAST_IRR = [("go", "went"), ("see", "saw"), ("eat", "ate"), ("drink", "drank"), ("run", "ran"), ("come", "came"), ("take", "took"), ("give", "gave"), ("write", "wrote"), ("read", "read"), ("buy", "bought"), ("bring", "brought"),
            ("make", "made"), ("have", "had"), ("do", "did"), ("say", "said"), ("get", "got"), ("know", "knew"), ("think", "thought"), ("sit", "sat"), ("stand", "stood"), ("speak", "spoke"), ("sleep", "slept"),
            ("swim", "swam"), ("sing", "sang"), ("begin", "began"), ("break", "broke"), ("choose", "chose"), ("drive", "drove"), ("fall", "fell"), ("feel", "felt"), ("find", "found"), ("fly", "flew"), ("forget", "forgot"),
            ("grow", "grew"), ("hear", "heard"), ("keep", "kept"), ("leave", "left"), ("lose", "lost"), ("meet", "met"), ("pay", "paid"), ("put", "put"), ("ride", "rode"), ("send", "sent"), ("sell", "sold"), ("tell", "told"),
            ("teach", "taught"), ("understand", "understood"), ("wear", "wore"), ("win", "won"), ("build", "built"), ("catch", "caught"), ("draw", "drew"), ("hold", "held"), ("throw", "threw"), ("wake", "woke")]
PAST_PART = {"go": "gone", "see": "seen", "eat": "eaten", "drink": "drunk", "run": "run", "come": "come", "take": "taken", "give": "given", "write": "written", "read": "read", "buy": "bought", "bring": "brought", "make": "made",
             "have": "had", "do": "done", "say": "said", "get": "got", "know": "known", "think": "thought", "sit": "sat", "stand": "stood", "speak": "spoken", "sleep": "slept", "swim": "swum", "sing": "sung", "begin": "begun",
             "break": "broken", "choose": "chosen", "drive": "driven", "fall": "fallen", "feel": "felt", "find": "found", "fly": "flown", "forget": "forgotten", "grow": "grown", "hear": "heard", "keep": "kept",
             "leave": "left", "lose": "lost", "meet": "met", "pay": "paid", "put": "put", "ride": "ridden", "send": "sent", "sell": "sold", "tell": "told", "teach": "taught", "understand": "understood", "wear": "worn",
             "win": "won", "build": "built", "catch": "caught", "draw": "drawn", "hold": "held", "throw": "thrown", "wake": "woken"}
REG_PAST = [("walk", "walked"), ("play", "played"), ("like", "liked"), ("stop", "stopped"), ("study", "studied"), ("plan", "planned"), ("carry", "carried"), ("help", "helped"), ("want", "wanted"), ("live", "lived"),
            ("cry", "cried"), ("jump", "jumped"), ("dance", "danced"), ("close", "closed"), ("open", "opened"), ("clean", "cleaned"), ("watch", "watched"), ("try", "tried"), ("drop", "dropped"), ("hurry", "hurried"),
            ("visit", "visited"), ("ask", "asked"), ("work", "worked"), ("cook", "cooked"), ("listen", "listened"), ("wash", "washed"), ("pull", "pulled"), ("push", "pushed"), ("copy", "copied"), ("enjoy", "enjoyed")]


def past_bad(v, right):
    if v.endswith("e"):
        out = [v, v + "s", v[:-1] + "ing", "will " + v]
    elif v.endswith("y") and v[-2] not in "aeiou":
        out = [v + "ed", v + "d", v, v + "ing"]
    elif right.endswith(v[-1] + "ed") and v[-1] not in "aeiouy" and right != v + "ed":
        out = [v + "ed", v, v + "ing", v + "s"]
    else:
        out = [v, v + "ing", v + "s", "will " + v]
    return [x for x in dict.fromkeys(out) if x != right]


def past_q(rng, d, g, lg):
    if g <= 3 and d <= 3 or rng.random() < 0.35:
        v, p = rng.choice(REG_PAST)
        wr = past_bad(v, p)
    else:
        v, p = rng.choice(PAST_IRR)
        wr = [v, v + "ed", PAST_PART.get(v) if PAST_PART.get(v) not in (None, p) else v + "ing", v + "ing", v + "s"]
    return Draft(f"What is the simple past tense of the verb '{v}'?", p, [x for x in wr if x != p], f"The simple past of '{v}' is '{p}'.")


def past_gap(rng, d, g, lg):
    v, p = rng.choice(PAST_IRR + REG_PAST)
    subj = rng.choice(["Yesterday, I", "Last week, she", "Last year, we", "This morning, he", "On Monday, they", "Last night, Amina", "Yesterday, Mr Tabi"])
    obj = {"go": "to the market", "see": "a big snake", "eat": "rice and fish", "drink": "a glass of water", "run": "to school", "come": "home late", "take": "my bag", "give": "me a book", "write": "a letter",
           "read": "a story", "buy": "some mangoes", "bring": "her lunch", "make": "a cake", "have": "a sore throat", "do": "my homework", "say": "hello", "get": "a prize", "know": "the answer", "think": "about it",
           "sit": "under a tree", "stand": "near the door", "speak": "to the teacher", "sleep": "very well", "swim": "in the river", "sing": "a song", "begin": "the lesson", "break": "a plate", "choose": "a red shirt",
           "drive": "to Douala", "fall": "from the tree", "feel": "very tired", "find": "a coin", "fly": "to Yaoundé", "forget": "his book", "grow": "tall", "hear": "a loud noise", "keep": "the money", "leave": "early",
           "lose": "his pen", "meet": "an old friend", "pay": "for the food", "put": "the book on the desk", "ride": "a bicycle", "send": "a message", "sell": "fruits", "tell": "a story", "teach": "us English",
           "understand": "the lesson", "wear": "a blue dress", "win": "the match", "build": "a small house", "catch": "a fish", "draw": "a picture", "hold": "my hand", "throw": "the ball", "wake": "up at six"}.get(v, "at home")
    if v not in dict(PAST_IRR) and v in dict(REG_PAST):
        obj = {"walk": "to school", "play": "football", "like": "the film", "stop": "at the gate", "study": "hard", "plan": "the trip", "carry": "a heavy bag", "help": "her mother", "want": "some water", "live": "in Bamenda",
               "cry": "loudly", "jump": "over the ditch", "dance": "at the party", "close": "the window", "open": "the door", "clean": "the room", "watch": "a film", "try": "again", "drop": "the cup", "hurry": "home",
               "visit": "her aunt", "ask": "a question", "work": "in the field", "cook": "the dinner", "listen": "to the radio", "wash": "the plates", "pull": "the rope", "push": "the door", "copy": "the notes", "enjoy": "the game"}[v]
    base = v
    t = f"Choose the correct word: '{subj} … {obj}.' (verb: {base})"
    pool = past_bad(v, p) if v in dict(REG_PAST) else [v, v + "ed", PAST_PART.get(v) if PAST_PART.get(v) not in (None, p) else v + "s", v + "ing"]
    return Draft(f"Which verb form completes: '{subj} … {obj}.' ({v})?", p, [x for x in pool if x != p], f"The sentence is in the past, so we use '{p}'.")


def present_s(rng, d, g, lg):
    v = rng.choice(["play", "go", "watch", "study", "like", "eat", "fly", "teach", "wash", "carry", "do", "have", "work", "help", "cook", "pass", "catch", "try", "read", "write", "run", "sing"])
    irregular = {"go": "goes", "do": "does", "have": "has"}
    if v in irregular:
        r = irregular[v]
    elif v.endswith(("s", "sh", "ch", "x", "z", "o")):
        r = v + "es"
    elif v.endswith("y") and v[-2] not in "aeiou":
        r = v[:-1] + "ies"
    else:
        r = v + "s"
    subj = rng.choice(["She", "He", "My mother", "The teacher", "Our dog", "Amina", "My brother", "The farmer"])
    wr = [v, v + "ing", v + "ed" if v != "have" else "haves", v + "s" if v + "s" != r else v + "es", v[:-1] + "ies" if v.endswith("y") and r != v[:-1] + "ies" else v + "ses"]
    return Draft(f"Which verb form completes the sentence: '{subj} … every day.' (verb: {v})?", r, [x for x in dict.fromkeys(wr) if x != r], f"After '{subj.lower() if subj in ('She', 'He') else subj}', the verb takes the third-person singular form: '{r}'.")


def be_q(rng, d, g, lg):
    subj, right = rng.choice([("I", "am"), ("You", "are"), ("He", "is"), ("She", "is"), ("It", "is"), ("We", "are"), ("They", "are"), ("My father", "is"), ("The children", "are"), ("Awa and Kofi", "are"), ("The cat", "is"), ("My friends", "are")])
    tail = rng.choice(["in the classroom", "very happy", "from Cameroon", "at school", "tall", "ready", "in the garden", "my friend"])
    return Draft(f"Which word completes the sentence: '{subj} … {tail}.'?", right, [x for x in ("am", "is", "are", "be") if x != right], f"The verb 'to be' agrees with '{subj}': '{right}'.")


ADJ_COMP = [("tall", "taller", "tallest"), ("small", "smaller", "smallest"), ("big", "bigger", "biggest"), ("fast", "faster", "fastest"), ("old", "older", "oldest"), ("young", "younger", "youngest"),
            ("long", "longer", "longest"), ("short", "shorter", "shortest"), ("hot", "hotter", "hottest"), ("cold", "colder", "coldest"), ("happy", "happier", "happiest"), ("easy", "easier", "easiest"),
            ("heavy", "heavier", "heaviest"), ("good", "better", "best"), ("bad", "worse", "worst"), ("far", "farther", "farthest"), ("large", "larger", "largest"), ("nice", "nicer", "nicest"),
            ("thin", "thinner", "thinnest"), ("fat", "fatter", "fattest"), ("slow", "slower", "slowest"), ("strong", "stronger", "strongest"), ("rich", "richer", "richest"), ("early", "earlier", "earliest"),
            ("busy", "busier", "busiest"), ("wide", "wider", "widest"), ("light", "lighter", "lightest"), ("cheap", "cheaper", "cheapest"), ("clean", "cleaner", "cleanest"), ("wet", "wetter", "wettest")]
LONG_ADJ = [("beautiful", "more beautiful", "most beautiful"), ("expensive", "more expensive", "most expensive"), ("interesting", "more interesting", "most interesting"), ("difficult", "more difficult", "most difficult"),
            ("important", "more important", "most important"), ("dangerous", "more dangerous", "most dangerous"), ("careful", "more careful", "most careful"), ("famous", "more famous", "most famous")]


def comparative(rng, d, g, lg):
    pool = ADJ_COMP + (LONG_ADJ if g >= 4 else [])
    a, c, s = rng.choice(pool)
    sup = rng.random() < 0.4
    right = s if sup else c
    wr = [c if sup else s, a + "er" if not sup else a + "est", "more " + a if not sup else "most " + a, a + ("est" if not sup else "er")]
    if a in ("good", "bad"):
        wr = [("gooder" if a == "good" else "badder") if not sup else ("goodest" if a == "good" else "baddest"), "more " + a if not sup else "most " + a, c if sup else s]
    t = f"What is the {'superlative' if sup else 'comparative'} form of '{a}'?"
    return Draft(t, right, [x for x in dict.fromkeys(wr) if x != right and x != a], f"The {'superlative' if sup else 'comparative'} of '{a}' is '{right}'.")


PREP = [("She was born … 2012.", "in", ["on", "at"]), ("The lesson begins … 8 o'clock.", "at", ["in", "on"]), ("We have no school … Sunday.", "on", ["in", "at"]), ("My birthday is … March.", "in", ["on", "at"]),
        ("The book is … the table.", "on", ["in", "at"]), ("The cat is sleeping … the chair.", "under", ["on", "up"]), ("He lives … Douala.", "in", ["on", "at"]), ("The bus leaves … 6 a.m.", "at", ["in", "on"]),
        ("There is a picture … the wall.", "on", ["in", "at"]), ("The children are playing … the yard.", "in", ["on", "at"]), ("I will see you … Friday.", "on", ["in", "at"]), ("It rains a lot … July.", "in", ["on", "at"]),
        ("She walks … school every day.", "to", ["at", "in"]), ("He is sitting … his mother.", "beside", ["between", "above"]), ("The bird is flying … the tree.", "over", ["under", "between"]),
        ("The dog ran … the gate.", "through", ["between", "under"]), ("I am afraid … snakes.", "of", ["to", "at"]), ("She is good … mathematics.", "at", ["in", "on"]), ("We listen … the radio.", "to", ["at", "in"]),
        ("He is waiting … the bus.", "for", ["to", "at"]), ("The shop closes … night.", "at", ["in", "on"]), ("We eat breakfast … the morning.", "in", ["on", "at"]), ("Christmas is … 25 December.", "on", ["in", "at"]),
        ("The ball is … the box.", "in", ["on", "at"]), ("My school is … the church and the market.", "between", ["under", "of"]), ("He jumped … the river.", "into", ["on", "at"]), ("She is the tallest … all the girls.", "of", ["to", "at"]),
        ("They arrived … Bamenda on Monday.", "in", ["on", "at"]), ("He will be back … a week.", "in", ["on", "at"]), ("I have been here … Monday.", "since", ["for", "from"]), ("We stayed there … three days.", "for", ["since", "from"])]


def extra(rng, table, r, w):
    pool = sorted({x[1] for x in table if x[1] not in [r] + w and x[1].isalpha()})
    return [rng.choice(pool)]


def prep_q(rng, d, g, lg):
    s, r, w = rng.choice(PREP)
    return Draft(f"Which word completes the sentence: '{s}'?", r, w + extra(rng, PREP, r, w), "Complete sentence: '" + s.replace("…", r) + "'")


HOMO = [("Put your books over …", "there", ["their", "they're"]), ("… dogs are very friendly.", "Their", ["There", "They're"]), ("… going to the market.", "They're", ["There", "Their"]),
        ("I have … apples.", "two", ["to", "too"]), ("She is going … school.", "to", ["too", "two"]), ("It is … hot today.", "too", ["to", "two"]), ("The dog wagged … tail.", "its", ["it's", "its'"]),
        ("… raining outside.", "It's", ["Its", "Its'"]), ("… my friend.", "She's", ["Shes", "Shee's"]) if False else ("Is this … book?", "your", ["you're", "yore"]), ("… very kind.", "You're", ["Your", "Yore"]),
        ("Please … your name here.", "write", ["right", "rite"]), ("Turn … at the corner.", "right", ["write", "rite"]), ("Come … quickly!", "here", ["hear", "heer"]), ("I can … the birds singing.", "hear", ["here", "heer"]),
        ("I … the answer.", "know", ["no", "now"]), ("There is … water in the jug.", "no", ["know", "now"]), ("I can … a big ship.", "see", ["sea", "se"]), ("The ship sails on the …", "sea", ["see", "se"]),
        ("I … a new book yesterday.", "bought", ["by", "buy"]) if False else ("The house is … the river.", "by", ["buy", "bye"]), ("I want to … a pen.", "buy", ["by", "bye"]), ("The … is shining.", "sun", ["son", "sunn"]),
        ("He is my only …", "son", ["sun", "sonn"]), ("I will … you tomorrow.", "meet", ["meat", "mete"]), ("We eat … and rice.", "meat", ["meet", "mete"]), ("The … is blowing hard.", "wind", ["whined", "wend"]) if False else ("Our teacher … us a story.", "told", ["tolled", "toled"]),
        ("A week has seven …", "days", ["daze", "dayes"]), ("I … my brother to the party.", "brought", ["bought", "braught"]), ("She … a new dress yesterday.", "bought", ["brought", "boughed"])]


def homophone_q(rng, d, g, lg):
    s, r, w = rng.choice(HOMO)
    return Draft(f"Which word completes the sentence: '{s}'?", r, w + extra(rng, HOMO, r, w), "Complete sentence: '" + s.replace("…", r) + "'")


COUNT = [("How … books do you have?", "many", ["much", "lot"]), ("How … water is in the bucket?", "much", ["many", "few"]), ("How … rice did you buy?", "much", ["many", "few"]), ("How … pupils are in the class?", "many", ["much", "little"]),
         ("How … sugar do you need?", "much", ["many", "few"]), ("How … eggs are there?", "many", ["much", "little"]), ("There is not … milk left.", "much", ["many", "few"]), ("She has … friends.", "many", ["much", "little"]),
         ("There are only … chairs.", "few", ["little", "much"]), ("We have … time.", "little", ["few", "many"])]


def count_q(rng, d, g, lg):
    s, r, w = rng.choice(COUNT)
    return Draft(f"Which word completes the sentence: '{s}'?", r, w + extra(rng, COUNT, r, w), "Complete sentence: '" + s.replace("…", r) + "'")


PRON = [("… is my brother. (he / him)", "He", ["Him", "His"]), ("I like … very much. (she / her)", "her", ["she", "hers"]), ("This is … book. (I / my)", "my", ["me", "mine"]), ("The book is …. (I / mine)", "mine", ["my", "me"]),
        ("Give the pen to …. (they / them)", "them", ["they", "their"]), ("… are playing football. (we / us)", "We", ["Us", "Our"]), ("The teacher called …. (we / us)", "us", ["we", "our"]),
        ("That bag is …. (she / hers)", "hers", ["her", "she"]), ("Is this … pencil? (you / your)", "your", ["you", "yours"]), ("The dog hurt … leg. (it / its)", "its", ["it", "it's"]),
        ("Amina and I did … homework. (we / our)", "our", ["us", "ours"]), ("The house is …. (they / theirs)", "theirs", ["their", "they"]), ("I saw … at the market. (he / him)", "him", ["he", "his"])]


def pron_q(rng, d, g, lg):
    s, r, w = rng.choice(PRON)
    q = s[: s.index(" (")]
    return Draft(f"Which word completes the sentence: '{q}'?", r, w + extra(rng, PRON, r, w), "Complete sentence: '" + q.replace("…", r) + "'")


PRESENT_CONT = [("run", "running"), ("swim", "swimming"), ("sit", "sitting"), ("write", "writing"), ("make", "making"), ("play", "playing"), ("come", "coming"), ("stop", "stopping"), ("dance", "dancing"), ("get", "getting"),
                ("cut", "cutting"), ("have", "having"), ("lie", "lying"), ("study", "studying"), ("begin", "beginning"), ("plan", "planning"), ("hope", "hoping"), ("shop", "shopping"), ("sleep", "sleeping"), ("take", "taking")]


def ing_q(rng, d, g, lg):
    v, r = rng.choice(PRESENT_CONT)
    wr = [v + "ing", v[:-1] + "ing", v + v[-1] + "ing", (v[:-1] + v[-2] + "ing") if v.endswith("e") else v + v[-1] * 2 + "ing", v[:-2] + "ing"]
    wr = [x for x in dict.fromkeys(wr) if x != r]
    if len(wr) < 3:
        return None
    return Draft(f"What is the '-ing' form of the verb '{v}'?", r, wr, f"The '-ing' form of '{v}' is '{r}'.")


def perfect_q(rng, d, g, lg):
    v, p = rng.choice([(a, PAST_PART[a]) for a, _ in PAST_IRR if a in PAST_PART])
    past = dict(PAST_IRR)[v]
    wr = [past, v + "ed", v, v + "en", v + "ing"]
    return Draft(f"What is the past participle of the verb '{v}'?", p, [x for x in dict.fromkeys(wr) if x != p], f"'I have {p}' : the past participle of '{v}' is '{p}'." if False else f"The past participle of '{v}' is '{p}' (for example: 'I have {p}').")


def qword_q(rng, d, g, lg):
    s, r = rng.choice([("… is your name?", "What"), ("… old are you?", "How"), ("… do you live?", "Where"), ("… is your birthday?", "When"), ("… is that man?", "Who"), ("… are you late?", "Why"),
                       ("… books do you have?", "How many"), ("… bag is this?", "Whose"), ("… is the market?", "Where"), ("… did you go yesterday?", "Where"), ("… do you go to school?", "How"), ("… time is it?", "What")])
    pool = ["What", "How", "Where", "When", "Who", "Why", "Whose", "How many"]
    return Draft(f"Which question word completes: '{s}'?", r, [x for x in pool if x != r][:6], "Complete question: '" + s.replace("…", r) + "'")


SENT = [("The boy runs fast.", "runs", "verb"), ("The girl sings a song.", "sings", "verb"), ("My mother cooks rice.", "cooks", "verb"), ("The teacher writes on the board.", "writes", "verb"),
        ("The dog is under the table.", "dog", "noun"), ("She has a red bag.", "bag", "noun"), ("My brother plays football.", "football", "noun"), ("The farmer sells yams.", "farmer", "noun"),
        ("The big tree fell.", "big", "adjective"), ("She wears a beautiful dress.", "beautiful", "adjective"), ("This is a sweet mango.", "sweet", "adjective"), ("The road is long.", "long", "adjective"),
        ("He speaks slowly.", "slowly", "adverb"), ("She sings very well.", "well", "adverb"), ("They arrived late.", "late", "adverb"), ("The baby cried loudly.", "loudly", "adverb"),
        ("She sat beside me.", "beside", "preposition"), ("The cat is on the roof.", "on", "preposition"), ("He walked to school.", "to", "preposition"), ("I like tea and bread.", "and", "conjunction")]


def pos_q(rng, d, g, lg):
    s, w, pos = rng.choice(SENT)
    opts = ["noun", "verb", "adjective", "adverb", "preposition", "pronoun"] + (["conjunction"] if g >= 7 else [])
    if pos == "conjunction" and g < 7:
        return None
    return Draft(f"In the sentence '{s}', what part of speech is the word '{w}'?", pos, [o for o in opts if o != pos][:6], f"In '{s}', '{w}' is {'an' if pos[0] in 'aeiou' else 'a'} {pos}.")


OPP = [("big", "small"), ("hot", "cold"), ("up", "down"), ("day", "night"), ("old", "young"), ("happy", "sad"), ("fast", "slow"), ("open", "close"), ("long", "short"), ("light", "dark"), ("full", "empty"), ("clean", "dirty"),
       ("rich", "poor"), ("early", "late"), ("strong", "weak"), ("wet", "dry"), ("hard", "soft"), ("east", "west"), ("north", "south"), ("buy", "sell"), ("win", "lose"), ("laugh", "cry"), ("come", "go"), ("easy", "difficult"),
       ("tall", "short"), ("inside", "outside"), ("begin", "end"), ("remember", "forget"), ("love", "hate"), ("friend", "enemy"), ("true", "false"), ("heavy", "light"), ("thick", "thin"), ("new", "old"), ("good", "bad"),
       ("near", "far"), ("above", "below"), ("push", "pull"), ("always", "never"), ("sweet", "bitter")]
SYNO = [("big", "large"), ("small", "little"), ("begin", "start"), ("end", "finish"), ("quick", "fast"), ("happy", "glad"), ("sick", "ill"), ("close", "shut"), ("smart", "clever"), ("kind", "gentle"), ("ask", "enquire"),
        ("buy", "purchase"), ("tired", "weary"), ("angry", "cross"), ("rich", "wealthy"), ("brave", "courageous"), ("beautiful", "pretty"), ("silent", "quiet"), ("huge", "enormous"), ("tidy", "neat"), ("speak", "talk"),
        ("simple", "easy"), ("help", "assist"), ("choose", "select"), ("annoy", "irritate")]


def opposite_q(rng, d, g, lg):
    a, b = rng.choice(OPP if d >= 2 or g >= 3 else OPP[:20])
    pool = [y for _, y in OPP if y not in (a, b)]
    return Draft(f"What is the opposite of '{a}'?", b, pool, f"The opposite of '{a}' is '{b}'.")


def synonym_q(rng, d, g, lg):
    a, b = rng.choice(SYNO)
    pool = [y for _, y in SYNO if y not in (a, b)]
    return Draft(f"Which word has almost the same meaning as '{a}'?", b, pool, f"'{a}' and '{b}' have a similar meaning.")


EN_WORDS = ["school", "teacher", "pupil", "book", "pencil", "table", "chair", "window", "door", "market", "village", "river", "mountain", "forest", "mango", "banana", "orange", "water", "rice", "bread", "family", "mother",
            "father", "sister", "brother", "friend", "garden", "flower", "house", "road", "bus", "car", "bicycle", "goat", "chicken", "monkey", "elephant", "lion", "tiger", "fish", "bird", "sun", "moon", "star", "rain", "wind",
            "cloud", "fire", "farm", "doctor", "nurse", "driver", "farmer", "baker", "singer", "dancer", "ball", "game", "song", "story", "letter"]


def alpha_q(rng, d, g, lg):
    ws = rng.sample(EN_WORDS, 4)
    first = rng.random() < 0.6
    key = lambda w: w
    srt = sorted(ws, key=key)
    if len({w[0] for w in ws}) < 4 and d <= 2:
        return None
    right = srt[0] if first else srt[-1]
    return Draft(f"Which word comes {'first' if first else 'last'} in alphabetical order: {', '.join(ws)}?", right, [w for w in ws if w != right], f"In alphabetical order: {', '.join(srt)}.")


def letters_q(rng, d, g, lg):
    w = rng.choice(EN_WORDS)
    n = len(w)
    return Draft(f"How many letters are there in the word '{w}'?", str(n), [str(x) for x in range(max(2, n - 3), n + 4) if x != n][:6], f"'{w}' is spelled {'-'.join(w)}: {n} letters.")


def next_letter_q(rng, d, g, lg):
    al = "abcdefghijklmnopqrstuvwxyz"
    i = rng.randint(1, 23)
    if rng.random() < 0.5:
        return Draft(f"Which letter comes just after '{al[i]}' in the alphabet?", al[i + 1], [al[i], al[i + 2], al[i - 1]], f"Alphabet: … {al[i - 1]} {al[i]} {al[i + 1]} …")
    return Draft(f"Which letter comes just before '{al[i]}' in the alphabet?", al[i - 1], [al[i], al[i + 1], al[i + 2] if i + 2 < 26 else al[i - 2]], f"Alphabet: … {al[i - 1]} {al[i]} {al[i + 1]} …")


SPELL = ["beautiful", "because", "friend", "school", "teacher", "february", "calendar", "different", "interesting", "necessary", "address", "Wednesday", "library", "language", "believe", "receive", "country", "village",
         "mountain", "tomorrow", "yesterday", "together", "morning", "evening", "children", "vegetable", "chocolate", "umbrella", "elephant", "giraffe", "family", "enough", "though", "through", "thought", "island", "neighbour",
         "weather", "answer", "question", "exercise", "library", "biscuit", "bicycle", "holiday", "birthday", "Saturday", "separate", "opposite", "important", "dangerous", "usually", "careful", "writing", "running", "money",
         "people", "pretty", "special", "straight", "schedule", "science", "student", "tonight", "welcome", "whole", "village", "market", "mango", "banana", "kitchen", "garden", "orange", "pencil", "window", "doctor"]


def mis_spell_en(w):
    out = []
    for i in range(len(w) - 1):
        if w[i] == w[i + 1] and w[i] not in "aeiou":
            out.append(w[:i] + w[i + 1:])
    for i in range(1, len(w) - 1):
        if w[i] in "nmtlrsp" and w[i] != w[i - 1] and w[i] != w[i + 1]:
            out.append(w[:i] + w[i] + w[i:])
    for a, b in (("ie", "ei"), ("ei", "ie"), ("ou", "u"), ("ough", "of"), ("ph", "f"), ("tion", "shun"), ("ea", "ee"), ("ai", "ay"), ("ck", "k"), ("c", "s"), ("s", "z"), ("i", "y"), ("e", "a")):
        if a in w:
            out.append(w.replace(a, b, 1))
    for i in range(1, len(w) - 2):
        if w[i] != w[i + 1]:
            out.append(w[:i] + w[i + 1] + w[i] + w[i + 2:])
    seen, res = set(), []
    for o in out:
        if o != w and o not in seen and o not in SPELL and o not in EN_WORDS:
            seen.add(o)
            res.append(o)
    return res


def spelling_q(rng, d, g, lg):
    w = rng.choice(SPELL if d >= 2 else SPELL[:35])
    wr = mis_spell_en(w)
    rng.shuffle(wr)
    if len(wr) < 3:
        return None
    return Draft("Which is the correct spelling?", w, wr[:6], f"The correct spelling is '{w}'.")


def reg_all():
    reg("plural", plural_q, ENC, 200, cat="Spelling", src_en=SRC)
    reg("article", article_q, ENC[:5], 120, cat="Grammar", src_en=SRC)
    reg("past", past_q, E2, 200, cat="Verbs", src_en=SRC)
    reg("past-gap", past_gap, E3, 250, cat="Verbs", src_en=SRC)
    reg("present-s", present_s, E2, 130, cat="Verbs", src_en=SRC)
    reg("be", be_q, ENC[:5], 120, cat="Grammar", src_en=SRC)
    reg("comparative", comparative, E3, 150, cat="Grammar", src_en=SRC)
    reg("prep", prep_q, E2, 130, cat="Grammar", src_en=SRC)
    reg("homophone", homophone_q, E3, 130, cat="Spelling", src_en=SRC)
    reg("count", count_q, E4, 70, cat="Grammar", src_en=SRC)
    reg("pron", pron_q, E3, 100, cat="Grammar", src_en=SRC)
    reg("ing", ing_q, E3, 90, cat="Spelling", src_en=SRC)
    reg("participle", perfect_q, ENC[5:], 120, cat="Verbs", src_en=SRC)
    reg("qword", qword_q, ENC[:5], 100, cat="Grammar", src_en=SRC)
    reg("pos", pos_q, E3, 100, cat="Grammar", src_en=SRC)
    reg("opposite", opposite_q, ENC[:6], 70, cat="Vocabulary", src_en=SRC)
    reg("synonym", synonym_q, E3, 60, cat="Vocabulary", src_en=SRC)
    reg("alpha", alpha_q, ENC[:5], 160, cat="Vocabulary", src_en=SRC)
    reg("letters", letters_q, ENC[:2], 60, cat="Vocabulary", src_en=SRC)
    reg("next-letter", next_letter_q, ENC[:2], 50, cat="Vocabulary", src_en=SRC)
    reg("spelling", spelling_q, E2, 220, cat="Spelling", src_en=SRC)


reg_all()
