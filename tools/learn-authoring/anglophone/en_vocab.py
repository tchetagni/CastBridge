from common import *


def vocab(p):
    ch = p.chapter("ch-vocab", "Vocabulary and usage", "O Level English Language — vocabulary, word formation and usage (to be checked against the official texts)")

    # ------------------------------------------------------------------ word formation
    build(ch, dict(
        slug="word-formation", title="Word formation: prefixes, suffixes and word families", min=22,
        obj=["Name the root, prefix and suffix of a word.", "Form nouns, adjectives, adverbs and opposites correctly.",
             "Apply the spelling changes that happen when a suffix is added."],
        notes=["Lists of prefixes and suffixes are common examples, not the complete syllabus list; the teacher may add the items taught in class."],
        blocks=[
            ("text", "A word is built from a **root** (the main part) with a **prefix** at the front and/or a **suffix** at the end. Knowing these parts helps you to spell, to guess the meaning of new words and to change a word from one class (noun, verb, adjective, adverb) to another. Gap-filling questions in the GCE often ask you to do exactly this."),
            ("key", "definition", "Prefixes change the meaning",
             "- **un-, in-, im-, il-, ir-, dis-, non-** = not / the opposite: *unkind, incorrect, impossible, illegal, irregular, disagree*.\n- **im-** is used before b, m, p; **il-** before l; **ir-** before r.\n- **re-** = again: *rewrite*. **mis-** = wrongly: *misunderstand*. **pre-** = before: *preschool*. **over-** = too much: *overcook*. **under-** = too little: *underpaid*."),
            ("key", "definition", "Suffixes change the word class",
             "- **Nouns:** -tion / -sion (*decide → decision*), -ment (*develop → development*), -ness (*kind → kindness*), -ity (*able → ability*), -er / -or (*teach → teacher*), -ship (*friend → friendship*).\n- **Adjectives:** -ful (*care → careful*), -less (*care → careless*), -able / -ible, -ous (*danger → dangerous*), -ive, -al.\n- **Adverbs:** -ly (*slow → slowly*).\n- **Verbs:** -en (*sharp → sharpen*), -ify (*simple → simplify*), -ise."),
            ("fig", tree("A word family from the root 'care'", "care", [("careful", "adjective"), ("careless", "adjective"), ("carefully", "adverb"), ("carelessness", "noun")]),
             "One root gives a whole family of words of different classes.",
             "A diagram with the root care at the top and four arrows pointing to careful (adjective), careless (adjective), carefully (adverb) and carelessness (noun)."),
            ("key", "methode", "Spelling changes when you add a suffix",
             "1. Final **-y** after a consonant becomes **-i**: *happy → happiness, beauty → beautiful*.\n2. Final silent **-e** is usually dropped before a vowel suffix: *hope → hoping*, but kept before a consonant suffix: *hopeful, hopeless*.\n3. Short words ending in one vowel + one consonant double the consonant: *begin → beginning, run → runner*.\n4. Check the class you need: ask « is a noun, an adjective or an adverb missing here? » before you choose the suffix."),
            ("key", "pieges", "Common mistakes",
             "- Writing *carefull* (the suffix is **-ful**, with one l).\n- Using the wrong class: *He is a very **success** man* → *successful*; *She sings **beautiful*** → *beautifully*.\n- Wrong negative prefix: *unpossible, inpossible* → **impossible**.\n- Forgetting that some words keep their form but change class (*to visit / a visit*, *to water / water*)."),
            ("ex", "Example 1: a gap-filling question", "Fill the gap with the correct form of the word in capitals. The ______ of the committee was announced on Friday. (DECIDE)",
             ["The gap comes after *The* and before *of*, so a **noun** is needed.", "The noun from *decide* uses the suffix -sion: de**cis**ion (the d of decide becomes s)."], "decision"),
            ("ex", "Example 2: opposites with prefixes", "Give the opposite of: (a) possible (b) regular (c) legal.",
             ["Check the first letter of the root: p → **im-**; r → **ir-**; l → **il-**.", "possible → **impossible**; regular → **irregular**; legal → **illegal**."], "impossible, irregular, illegal"),
        ],
        ex=[
            ("mcq", "What is the opposite of the word *possible*?", "impossible", ["unpossible", "inpossible", "dispossible"], "Before the letter p, the prefix meaning « not » is im-: impossible."),
            ("tf", "In the word *careless*, the suffix -less means « without ».", True, "Careless = without care. Compare *hopeless* (without hope) and *homeless* (without a home)."),
            ("match", "Match each word with its noun form.", [("develop", "development"), ("kind", "kindness"), ("educate", "education"), ("friend", "friendship")], "The suffixes are -ment, -ness, -tion and -ship."),
            ("mcq", "Choose the right word to complete the sentence: *The student answered every question ______.* (CONFIDENCE)", "confidently", ["confident", "confidence", "confidences"], "An adverb is needed to describe the verb *answered*: confident + -ly = confidently."),
            ("mcq", "Which word begins with a prefix that means « wrongly »?", "misunderstand", ["reappear", "unhappy", "preview"], "mis- means wrongly. re- = again, un- = not, pre- = before."),
            ("problem", "Word formation (answer all parts).", [
                part_mcq("Form an adjective from *danger*: *The road to the mountain is ______ in the rainy season.*", "dangerous", ["dangerly", "dangering", "dangerful"], "danger + -ous = dangerous."),
                part_mcq("Form a noun from *arrive*: *The ______ of the bus was late.*", "arrival", ["arrive", "arriving", "arrivance"], "arrive → arrival (suffix -al)."),
                part_open("Write one sentence using the noun made from the verb *move*.", "The movement of the crowd towards the stadium was slow.", ["1 mark: the noun *movement* is used.", "1 mark: the sentence is correct and makes sense."])]),
        ],
        sc=[("Which suffix forms a noun from the adjective *kind*?", "-ness", ["-ly", "-ful", "-ise"], "kind + -ness = kindness."),
            ("What is the root of *unhappiness*?", "happy", ["un", "ness", "unhappy"], "un- (prefix) + happy (root, spelt happi- before a suffix) + -ness (suffix)."),
            ("Which spelling of the adjective from *beauty* is correct?", "beautiful", ["beautifull", "beutiful", "beatiful"], "beauty → beautiful (y becomes i; suffix -ful)."),
            ("Which word is an adverb?", "slowly", ["slowness", "slowest", "slower"], "-ly makes adverbs: slowly."),
            ("The opposite of *regular* is...", "irregular", ["unregular", "inregular", "disregular"], "ir- is used before r.")]))

    # ------------------------------------------------------------------ synonyms antonyms idioms
    build(ch, dict(
        slug="synonyms-idioms", title="Synonyms, antonyms and idioms", min=22,
        obj=["Choose a synonym or antonym that fits the context.", "Explain common idioms and use them correctly.", "Avoid word-for-word translation of idioms."],
        notes=["The idioms listed are widely used British English idioms; the teacher may replace them with those in the class textbook."],
        blocks=[
            ("text", "**Synonyms** are words with nearly the same meaning (*big, large, huge*). **Antonyms** are words with opposite meanings (*generous – mean*). In an exam you must pick the word that fits **the sentence**, not just the dictionary meaning: synonyms are almost never exactly equal."),
            ("key", "definition", "Choosing the right synonym",
             "- Same **word class** (a noun is replaced by a noun).\n- Same **strength**: *good – excellent – superb* are different degrees.\n- Same **register**: *start / begin* (everyday), *commence* (formal); *help* / *assist*; *buy* / *purchase*.\n- Same **feeling**: *slim* (positive), *thin* (neutral), *skinny* (negative).\nMethod: put your word into the sentence and read it again."),
            ("fig", spokes("Synonyms of walk: shades of meaning", "walk", ["stroll (slow)", "march (firm)", "limp (hurt)", "stride (long)"]),
             "The word walk has many synonyms, each with a different shade.", "A centre box with the word walk and four arrows pointing to stroll (slow), march (firm), limp (hurt) and stride (long)."),
            ("key", "definition", "Idioms (1)",
             "An **idiom** is a fixed expression whose meaning is not the sum of its words.\n- **a piece of cake** = very easy\n- **break the ice** = make people feel relaxed at a first meeting\n- **under the weather** = slightly ill\n- **spill the beans** = reveal a secret\n- **let the cat out of the bag** = reveal a secret by mistake"),
            ("key", "definition", "Idioms (2)",
             "- **once in a blue moon** = very rarely\n- **hit the nail on the head** = say exactly the right thing\n- **bite the bullet** = accept something unpleasant bravely\n- **the last straw** = the final problem that makes a situation unbearable\n- **cost an arm and a leg** = be very expensive"),
            ("key", "pieges", "Common mistakes",
             "- Translating an idiom word for word from your mother tongue: it must keep its exact English words (*a piece of cake*, not *a slice of cake*).\n- Taking the idiom literally in a comprehension question.\n- Using many idioms in one essay: one or two well-chosen ones are enough.\n- Choosing an antonym of the wrong word class or sense."),
            ("ex", "Example 1: a synonym in context", "*The principal was furious when the pupils broke the window.* Choose the closest meaning of *furious*: (a) very angry (b) slightly worried (c) surprised.",
             ["*Furious* is a strong form of *angry*.", "(b) is too weak and (c) describes another feeling."], "(a) very angry"),
            ("ex", "Example 2: an idiom", "*Nkeng told me about the surprise party and I almost let the cat out of the bag.* What did the speaker nearly do?",
             ["The idiom *let the cat out of the bag* means to reveal a secret by mistake.", "So the speaker nearly told someone about the surprise party."], "He or she nearly revealed the secret."),
        ],
        ex=[
            ("mcq", "Choose the word closest in meaning to *reluctant*.", "unwilling", ["eager", "tired", "polite"], "A reluctant person does not want to do something: unwilling."),
            ("mcq", "Choose the antonym of *generous*.", "mean", ["kind", "wealthy", "careful"], "A generous person gives freely; a mean person does not."),
            ("mcq", "*Passing the test was a piece of cake.* This means that the test was...", "very easy", ["very sweet", "very long", "very difficult"], "A piece of cake = very easy."),
            ("match", "Match each idiom with its meaning.", [("spill the beans", "reveal a secret"), ("under the weather", "slightly ill"), ("once in a blue moon", "very rarely"), ("hit the nail on the head", "say exactly the right thing")], "Learn idioms as whole units."),
            ("mcq", "In which sentence can *assist* NOT replace *help*?", "Help yourself to more plantain.", ["Can you help me carry this bag?", "The nurse will help the patient.", "Volunteers help the elderly."], "*Help yourself to* is a fixed expression meaning « take what you want ». The other three can use *assist*."),
            ("open", "Write **two** sentences (one idiom in each) from this list: *a piece of cake, break the ice, under the weather, cost an arm and a leg, the last straw*. Each sentence must be about life in Cameroon.",
             "Our new neighbour from Bafoussam brought some puff-puff to break the ice. A taxi from Douala to Yaoundé at Christmas can cost an arm and a leg.",
             ["1 mark per sentence: the idiom is used with its correct English words.", "1 mark per sentence: the meaning fits the context.", "Minus 1 mark if an idiom is changed (e.g. *a slice of cake*)."]),
        ],
        sc=[("The antonym of *ancient* is...", "modern", ["old", "historic", "famous"], "Ancient = very old; the opposite is modern."),
            ("Which word is the most formal synonym of *begin*?", "commence", ["kick off", "start", "get going"], "Commence is formal."),
            ("*Cost an arm and a leg* means...", "be very expensive", ["be dangerous", "be free", "hurt someone"], "It is a fixed idiom about high price."),
            ("*He bit the bullet and told his father about the broken radio.* He...", "faced something unpleasant bravely", ["bit a piece of metal", "ran away", "lied"], "Bite the bullet = accept an unpleasant duty."),
            ("A synonym of *assist* is...", "help", ["refuse", "delay", "hide"], "Assist and help mean nearly the same.")]))

    # ------------------------------------------------------------------ confused words
    build(ch, dict(
        slug="confused-words", title="Words often confused", min=22,
        obj=["Choose between look-alike and sound-alike words.", "Distinguish noun and verb spellings (advice/advise).", "Use a quick test for each pair."],
        notes=["British spelling is used (practise as verb); the teacher should confirm the convention expected by the GCE Board."],
        blocks=[
            ("text", "Many mistakes in exam scripts come from pairs of words that look or sound alike. Learn them in **pairs with a short example**, and build your own memory trick."),
            ("key", "definition", "Pairs (1)",
             "- **affect** (verb: to influence) / **effect** (noun: a result). *Drought affects crops. The effect is hunger.*\n- **advice** (noun) / **advise** (verb). *She gave me advice. She advised me.*\n- **practice** (noun) / **practise** (verb, British spelling). *Football practice. We practise daily.*\n- **principal** (head of a school; main) / **principle** (a rule or belief).\n- **lose** (to misplace) / **loose** (not tight)."),
            ("key", "definition", "Pairs (2)",
             "- **accept** (receive) / **except** (apart from).\n- **stationary** (not moving) / **stationery** (paper, pens).\n- **quiet** (silent) / **quite** (fairly).\n- **complement** (to complete) / **compliment** (a kind remark).\n- **borrow** (take for a while) / **lend** (give for a while). *Lend me your pen* = I take it; *I borrowed his pen*.\n- **bring** (towards here) / **take** (away from here)."),
            ("key", "definition", "Pairs (3)",
             "- **fewer** (things you can count: *fewer pupils*) / **less** (things you cannot count: *less water*).\n- **between** (two) / **among** (three or more).\n- **their** (belonging to them) / **there** (place) / **they're** (they are).\n- **its** (belonging to it) / **it's** (it is or it has).\n- **your** (belonging to you) / **you're** (you are).\n- **whose** (belonging to whom) / **who's** (who is)."),
            ("fig", cols("Affect or effect?", ["Word", "Class", "Meaning"], [["affect", "verb", "to influence"], ["effect", "noun", "a result"]]),
             "A quick test: if you can put a, an or the in front of the word, use the noun, effect.",
             "A small table with three columns. Row one: affect, verb, to influence. Row two: effect, noun, a result."),
            ("key", "methode", "Quick tests",
             "- Can you put **the / an** before the word? Then it is the **noun** (advice, effect, practice).\n- **its / it's**: replace with *it is*. If the sentence still works, write **it's**.\n- **fewer / less**: can you count them? Then **fewer**.\n- **lose / loose**: *lose* has the sound of « looz »; *loose* is the opposite of *tight*."),
            ("key", "pieges", "Common mistakes", "- *The teacher gave us an advice.* (advice cannot take *an* or a plural).\n- *Don't loose your ticket.* → lose.\n- *It's wheels are broken.* → its.\n- *There were less pupils.* → fewer.\n- *Everybody accept Ali came.* → except."),
            ("ex", "Example 1: affect or effect", "*The heavy rain ______ the road to Bamenda.* Choose: affected / effect.",
             ["The gap needs a **verb** (the rain did something to the road).", "The verb is *affected*; *effect* is a noun."], "affected"),
            ("ex", "Example 2: accept or except", "*Everyone ______ Tabe came to the meeting.* Choose: accept / except.",
             ["The word means « apart from » so it must be *except*.", "*Accept* is a verb meaning to receive."], "except"),
        ],
        ex=[
            ("mcq", "*The new teacher gave us useful ______ about the exam.*", "advice", ["advise", "advices", "advised"], "A noun is needed after *useful*, and advice has no plural."),
            ("mcq", "*I have forgotten my dictionary. Please ______ me yours.*", "lend", ["borrow", "loose", "rent"], "You ask the other person to give it to you for a while: lend."),
            ("tf", "In *The dog wagged its tail*, the spelling *its* is correct.", True, "It means « belonging to the dog », so no apostrophe."),
            ("mcq", "*It was so ______ in the library that nobody heard her enter.*", "quiet", ["quite", "quit", "quieter"], "Quiet = silent; quite = fairly."),
            ("match", "Match each word with its meaning.", [("stationary", "not moving"), ("stationery", "paper and pens"), ("complement", "make complete"), ("compliment", "a kind remark")], "Memory trick: stationERy = pap-ER; compLEment = compLEte."),
            ("problem", "Choose the correct word (answer all parts).", [
                part_mcq("*The ______ of the long drought was a poor maize harvest.*", "effect", ["affect", "affected", "effects of"], "A noun is needed after *The*: effect."),
                part_mcq("*Nobody ______ the headmaster knew the secret.*", "except", ["accept", "expect", "excepts"], "Apart from the headmaster: except."),
                part_mcq("*There are ______ pupils this year than last year.*", "fewer", ["less", "lesser", "more fewer"], "Pupils can be counted: fewer."),
                part_mcq("*Our teacher ______ us to read every day.*", "advised", ["advice", "advices", "adviced"], "A verb is needed: advised.")]),
        ],
        sc=[("Which sentence uses *principal* correctly?", "The principal addressed the school.", ["The principle addressed the school.", "The principal address the school.", "The principals addressed school."], "The head of a school is the principal."),
            ("*I hope you don't ______ your keys.*", "lose", ["loose", "loss", "lost"], "After *don't* we need the base verb: lose."),
            ("Choose the correct word: *There were ______ girls than boys.*", "fewer", ["less", "little", "much"], "Countable → fewer."),
            ("Choose the correct word: *The match was cancelled; ______ raining heavily.*", "it's", ["its", "its'", "it"], "It's = it is."),
            ("Which is a NOUN?", "advice", ["advise", "affect", "practise"], "Advice (noun), advise (verb), affect (verb), practise (verb).")]))

    # ------------------------------------------------------------------ Cameroon English pitfalls
    build(ch, dict(
        slug="cameroon-english", title="Cameroon English and standard English: common pitfalls", min=22,
        obj=["Recognise frequent errors heard in everyday speech.", "Replace them with standard written English.", "Understand why the exam expects standard English."],
        notes=["The expressions are based on widely observed learner errors in West African and Cameroonian English; a Cameroonian English teacher must confirm the list and the examples. Items such as stay/live and open/close the light depend on regional usage: kept as advice for the exam only."],
        blocks=[
            ("text", "English in Cameroon is rich and has its own colour: expressions, rhythm and words of local origin. It is a living variety. But the GCE marks **standard written English**, which is understood everywhere. This lesson lists frequent slips that cost marks."),
            ("key", "definition", "Grammar slips (1)",
             "- *She has lived here **since** five years* → **for** five years (**since** + a starting point: *since 2019*; **for** + a length of time).\n- *We discussed **about** the problem* → *discussed the problem*. Same with *return back* → *return*, *emphasise on* → *emphasise*, *cope up with* → *cope with*.\n- *informations, advices, furnitures, equipments, luggages* → these are **uncountable**: *information, advice, furniture, equipment, luggage* (say *a piece of advice*)."),
            ("key", "definition", "Grammar slips (2)",
             "- **State verbs** are not used in the continuous: *I am knowing* → **I know**; *He is having a car* → **He has**; also *want, like, understand, believe, belong*.\n- **Repeated subject:** *My mother she sells fish* → **My mother sells fish.** One subject, one pronoun at most.\n- **Prepositions:** *congratulate somebody **on***, *depend **on***, *different **from***, *listen **to***."),
            ("key", "definition", "Word choice",
             "- *Borrow me your pen* → **Lend me your pen** (or: *May I borrow your pen?*).\n- *Open / close the light* → **switch on / switch off** the light.\n- *Where do you stay?* is understood locally; in an exam write **Where do you live?** for a permanent home.\n- Local words (names of dishes, titles) are fine in a story or a letter if the reader will understand, but not for general vocabulary in formal essays."),
            ("fig", cols("Heard often → exam English", ["Often heard", "Exam English"], [["discuss about it", "discuss it"], ["return back", "return"], ["an advice", "a piece of advice"], ["I am knowing", "I know"], ["borrow me a pen", "lend me a pen"]], size=12),
             "Five frequent slips and their standard form.", "A two-column table. Left column: discuss about it, return back, an advice, I am knowing, borrow me a pen. Right column: discuss it, return, a piece of advice, I know, lend me a pen."),
            ("key", "methode", "How to correct yourself",
             "1. Read your answer slowly and look at **prepositions** and **verb forms** first.\n2. Ask: is this word **countable**?\n3. Ask: is this verb a **state** verb?\n4. Does the sentence have a **doubled subject**?\n5. In speech, relax and be natural; in the exam, **check** these points."),
            ("key", "pieges", "Common mistakes (even after correction)", "- Over-correcting: *I look forward to **see** you* is wrong; the form is *to seeing* (look forward **to** + -ing).\n- Writing *since two weeks* in a letter.\n- Using speech-style short forms (*gonna, wanna*) in formal writing.\n- Mixing standard and non-standard forms in the same paragraph."),
            ("ex", "Example 1: correcting a sentence", "Correct: *My brother he is having many informations about the scholarship.*",
             ["Remove the doubled subject: *My brother…*", "*is having* → **has**; *informations* → **information** (uncountable): *a lot of information*."], "My brother has a lot of information about the scholarship."),
            ("ex", "Example 2: since or for", "Complete: *We have been waiting at the garage ______ two hours.*",
             ["Two hours is a **length of time**.", "Length of time → **for**."], "for"),
        ],
        ex=[
            ("mcq", "Choose the correct sentence.", "We discussed the problem for two hours.", ["We discussed about the problem since two hours.", "We discussed about the problem for two hours.", "We discussed the problem since two hours."], "*Discuss* takes no preposition; *for* is used with a length of time."),
            ("mcq", "*She has lived in Limbe ______ five years.*", "for", ["since", "from", "during"], "Five years is a length of time."),
            ("tf", "*Informations* is the correct plural of *information*.", False, "Information is uncountable and has no plural."),
            ("mcq", "Choose the correct sentence.", "My mother sells fish at the market.", ["My mother she sells fish at the market.", "My mother selling fish at the market.", "My mother is sell fish at the market."], "No repeated subject; the verb is correctly formed."),
            ("open", "Correct these four sentences:\n1. I am wanting a cold drink.\n2. Please borrow me your ruler.\n3. We returned back home at six.\n4. The teacher gave us many advices.",
             "1. I want a cold drink. 2. Please lend me your ruler. 3. We returned home at six. 4. The teacher gave us a lot of advice.", ["1 mark per correct sentence.", "Half a mark if the error is found but a new one is made."]),
            ("problem", "Find the standard English (answer all parts).", [
                part_mcq("Which is correct?", "I know the answer.", ["I am knowing the answer.", "I am know the answer.", "I knowing the answer."], "*Know* is a state verb: no continuous form."),
                part_mcq("Which is correct?", "Please lend me your ruler.", ["Please borrow me your ruler.", "Please borrowed me your ruler.", "Please lend to me your rulers."], "You *lend* to someone; you *borrow* from someone."),
                part_mcq("Which is correct?", "The school has new equipment.", ["The school has new equipments.", "The school have new equipments.", "The school has a new equipments."], "Equipment is uncountable."),
                part_open("Write one sentence using *information* correctly (not *informations*).", "I need more information about the exam dates.", ["1 mark: *information* without s.", "1 mark: a correct, complete sentence."])]),
        ],
        sc=[("Which is standard English?", "She is waiting for the bus.", ["She is waiting the bus.", "She waits to the bus.", "She is wait for bus."], "*Wait for* is the correct phrasal verb."),
            ("Choose the correct word: *He is married ______ a teacher.*", "to", ["with", "by", "for"], "Marry/be married **to**."),
            ("Choose the correct sentence.", "The furniture is new.", ["The furnitures are new.", "The furnitures is new.", "The furniture are new."], "Furniture is uncountable and takes a singular verb."),
            ("*Please ______ the light, it is dark.*", "switch on", ["open", "burn", "make"], "Lights are switched on and off."),
            ("*I look forward to ______ you.*", "seeing", ["see", "saw", "seen"], "Here *to* is a preposition, so -ing follows.")]))
    return ch
