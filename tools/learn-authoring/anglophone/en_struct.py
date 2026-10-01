from common import *


def passive_fig():
    it = [T(210, 22, "Active to passive: the roles swap", 15, bold=True),
          T(30, 52, "Active", 13, anchor="start", bold=True), T(30, 152, "Passive", 13, anchor="start", bold=True)]
    xs = [20, 150, 280]
    top = [("Subject", "lightblue"), ("Verb", "lightgreen"), ("Object", "lightorange")]
    bot = [("Object", "lightorange"), ("be + pp", "lightgreen"), ("by + Subject", "lightblue")]
    for x, (lab, c) in zip(xs, top):
        it += [RECT(x, 62, 120, 34, fill=c, radius=6), T(x + 60, 84, lab, 13)]
    for x, (lab, c) in zip(xs, bot):
        it += [RECT(x, 162, 120, 34, fill=c, radius=6), T(x + 60, 184, lab, 13)]
    it += [LINE(80, 96, 340, 162, arrow="end", color="blue", width=2), LINE(340, 96, 80, 162, arrow="end", color="orange", width=2),
           LINE(210, 96, 210, 162, arrow="end", color="green", width=2)]
    return shapes(it, 420, 215)


def struct(p):
    ch = p.chapter("ch-structure", "Sentence structure, punctuation and spelling", "O Level English Language — sentence structure, punctuation and spelling (to be checked against the official texts)")

    # ------------------------------------------------------------------ sentence types
    build(ch, dict(
        slug="sentence-types", title="Clauses and sentences: simple, compound and complex", min=24,
        obj=["Find the subject and verb of a clause.", "Tell main clauses from subordinate clauses.", "Build simple, compound and complex sentences and avoid fragments and comma splices."],
        notes=["Terminology (main/independent, subordinate/dependent) follows common school grammars; check the terms used in the class textbook."],
        blocks=[
            ("text", "A **clause** is a group of words with a **subject and a verb**. A **phrase** has no verb of its own (*in the market*). Good writers vary their sentences: short ones for impact, longer ones to link ideas. Examiners reward correct variety."),
            ("key", "definition", "The three sentence structures",
             "- **Simple:** one clause. *The rain stopped.*\n- **Compound:** two or more **main** clauses joined by *and, but, or, so, yet, for, nor*. *The rain stopped, and the match began.*\n- **Complex:** one main clause + one or more **subordinate** clauses (they cannot stand alone). *When the rain stopped, the match began.*\n- **Compound-complex:** both."),
            ("fig", tree("Three structures", "Sentence", [("simple", "1 main clause"), ("compound", "2 main clauses"), ("complex", "main + subordinate")], h=185),
             "Count the clauses, then ask if they are equal (compound) or if one depends on the other (complex).", "A diagram with the word Sentence at the top and three arrows to simple (one main clause), compound (two main clauses) and complex (main plus subordinate clause)."),
            ("key", "definition", "Linking words",
             "- **Coordinating** (equal clauses): and, but, or, so, yet, for, nor.\n- **Subordinating** (clause depends on the main one): *because, since, as* (reason); *although, though, while* (contrast); *when, after, before, until, as soon as* (time); *if, unless* (condition); *so that* (purpose); *that* (a noun clause)."),
            ("key", "pieges", "Common mistakes",
             "- **Fragment:** *Because the road was flooded.* (no main clause) → *Because the road was flooded, we stayed at home.*\n- **Comma splice:** *I was tired, I slept.* → *I was tired, so I slept.* / *I was tired; I slept.*\n- **Double linking:** *Although he was ill, but he came.* → use only one of *although* and *but*."),
            ("ex", "Example 1: combining sentences", "Combine into (a) a compound and (b) a complex sentence: *It rained heavily. The match continued.*",
             ["(a) Compound: join equal clauses with *but*: *It rained heavily, but the match continued.*", "(b) Complex: make one clause depend on the other with *although*: *Although it rained heavily, the match continued.*"], "(a) …, but … (b) Although …, …"),
            ("ex", "Example 2: identifying clauses", "Name the main clause in: *Because Nana was late, she missed the bus.*",
             ["Find the clause that can stand alone: *she missed the bus*.", "*Because Nana was late* cannot stand alone: subordinate clause (reason)."], "main clause: she missed the bus"),
        ],
        ex=[
            ("mcq", "What kind of sentence is *Mbah plays football and Ngum plays volleyball*?", "compound", ["simple", "complex", "a fragment"], "Two main clauses joined by *and*."),
            ("mcq", "What kind of sentence is *Although she was tired, she finished the homework*?", "complex", ["simple", "compound", "compound-complex"], "A subordinate clause (although…) + one main clause."),
            ("tf", "A subordinate clause can stand alone as a complete sentence.", False, "That is what makes it subordinate: it depends on a main clause."),
            ("mcq", "Which of these is a sentence fragment?", "Because the road was flooded.", ["The road was flooded.", "We stayed at home.", "The road was flooded, so we stayed at home."], "It begins with *because* but gives no main clause."),
            ("mcq", "Join with *although*: *He was ill. He went to school.*", "Although he was ill, he went to school.", ["Although he was ill, but he went to school.", "He was ill although, he went to school.", "Although he was ill he, went to school."], "Use only one linking word and put the comma after the subordinate clause."),
            ("open", "Combine these five short sentences into **two** good sentences: one compound and one complex. Do not change the meaning.\n\nThe market was crowded. Nkeng was tired. She bought some tomatoes. She bought some plantain. She went home.",
             "Compound: The market was crowded, but Nkeng bought some tomatoes and some plantain. Complex: Because she was tired, she went home.",
             ["1 mark: a correct compound sentence.", "1 mark: a correct complex sentence.", "1 mark: all the information is used.", "1 mark: correct punctuation, no fragment or comma splice."]),
        ],
        sc=[("A clause must contain...", "a subject and a verb", ["only a noun", "a linking word", "a comma"], "A clause = subject + verb."),
            ("Which word begins a subordinate clause?", "because", ["and", "but", "so"], "Because is subordinating; the others are coordinating."),
            ("*I was tired, I slept.* has which mistake?", "comma splice", ["fragment", "wrong tense", "no mistake"], "Two main clauses joined only by a comma."),
            ("Choose the complex sentence.", "When the bell rang, the pupils left.", ["The bell rang.", "The bell rang and the pupils left.", "The pupils left."], "A subordinate clause (When…) + main clause."),
            ("*She sings, and he dances.* is...", "compound", ["simple", "complex", "a phrase"], "Two main clauses joined by *and*.")]))

    # ------------------------------------------------------------------ relative clauses and conditionals
    build(ch, dict(
        slug="relative-conditionals", title="Relative clauses and conditional sentences", min=25,
        obj=["Choose who, whom, whose, which, that, where correctly.", "Punctuate defining and non-defining relative clauses.", "Form the zero, first, second and third conditionals."],
        notes=["Some syllabi teach only first, second and third conditionals; the zero conditional is included here as an extra."],
        blocks=[
            ("key", "definition", "Relative pronouns",
             "A relative clause gives more information about a noun.\n- **who** (people): *the girl who won*\n- **whom** (people, object, formal): *the man whom I met*\n- **whose** (possession): *the boy whose bicycle was stolen*\n- **which** (things): *the book which I read*\n- **that** (people or things, in **defining** clauses only)\n- **where / when**: *the town where he lives*"),
            ("key", "definition", "Defining and non-defining",
             "- **Defining** (needed to know which one): **no commas**. *The woman who sells ndolé lives next door.*\n- **Non-defining** (extra information): **commas**, and you cannot use *that*. *Mr Tabi, who teaches physics, is ill.*\n- In a defining clause the pronoun can be **dropped when it is the object**: *the book (that) I read*."),
            ("key", "definition", "Conditional sentences",
             "- **Zero:** *If you heat ice, it melts.* (present + present: general truths)\n- **First:** *If it rains, we will stay home.* (present + will: real future possibility)\n- **Second:** *If I had money, I would buy a car.* (past + would: unreal present; use *were* for all persons: *If I were you…*)\n- **Third:** *If she had run, she would have caught the bus.* (past perfect + would have + past participle: unreal past)"),
            ("fig", cols("Conditionals at a glance", ["Type", "If-clause", "Main clause"], [["Zero", "present", "present"], ["First", "present", "will + verb"], ["Second", "past", "would + verb"], ["Third", "past perfect", "would have + pp"]], size=12),
             "Match the tense of the if-clause with the right main clause.", "A table with columns Type, If-clause and Main clause. Zero: present, present. First: present, will plus verb. Second: past, would plus verb. Third: past perfect, would have plus past participle."),
            ("key", "pieges", "Common mistakes",
             "- *If it **will** rain, …* → *If it **rains**, …* (no *will* after *if*).\n- *If I **would** have known, …* → *If I **had** known, I would have…*\n- *The boy **which** …* → *who*; *the book **who*** → *which*.\n- *Mr Tabi, **that** teaches…* → *who* (no *that* with commas).\n- **unless** = *if … not*: *Unless you hurry, you will be late.* (never *unless … not*)"),
            ("ex", "Example 1: joining with a relative clause", "Join with *who*: *The woman sells ndolé. She lives next door.*",
             ["Choose which sentence becomes the relative clause: *She lives next door* gives extra information about the woman.", "Put *who* right after *woman*: *The woman who lives next door sells ndolé.* (no commas: it tells us which woman)."], "The woman who lives next door sells ndolé."),
            ("ex", "Example 2: third conditional", "Rewrite as a third conditional: *She did not run, so she missed the bus.*",
             ["The situation is unreal and in the past: if-clause with the **past perfect** of the opposite idea: *If she had run*,", "main clause with *would have* + past participle: *she would not have missed the bus.*"], "If she had run, she would not have missed the bus."),
        ],
        ex=[
            ("mcq", "*The boy ______ bicycle was stolen reported it to the police.*", "whose", ["who", "which", "whom"], "Whose shows possession."),
            ("mcq", "*If it ______ tomorrow, the match will be cancelled.*", "rains", ["will rain", "would rain", "rained"], "First conditional: present in the if-clause."),
            ("mcq", "*If I ______ you, I would apologise.*", "were", ["am", "will be", "would be"], "Second conditional: *were*."),
            ("tf", "You can use *that* in a non-defining relative clause (the one with commas).", False, "Only *who* or *which* are possible with commas."),
            ("mcq", "*If he ______ harder, he would have passed the examination.*", "had worked", ["worked", "would work", "has worked"], "Third conditional: past perfect in the if-clause."),
            ("open", "Rewrite each sentence as instructed.\n1. Study hard or you will fail. (Begin: Unless …)\n2. Ngwa has a brother. He plays for Cotonsport. (Join with *who*)\n3. We did not leave early. We missed the train. (Third conditional)\n4. Mr Ayuk is our principal. He arrived late. (Non-defining clause)",
             "1. Unless you study hard, you will fail. 2. Ngwa has a brother who plays for Cotonsport. 3. If we had left early, we would not have missed the train. 4. Mr Ayuk, who is our principal, arrived late.",
             ["1 mark per correct sentence.", "In 4 the commas are required; a missing comma loses the mark."]),
        ],
        sc=[("Choose the correct word: *the man ______ lives next door*", "who", ["which", "whose", "where"], "Who for people as subject."),
            ("Which first-conditional sentence is correct?", "If it rains, we will stay.", ["If it will rain, we will stay.", "If it would rain, we will stay.", "If it rained, we will stay."], "No *will* after *if* in the first conditional."),
            ("*Unless you hurry, you ______ late.*", "will be", ["will not be", "would be", "are being"], "Unless already means if…not; the main verb stays positive."),
            ("Which type is *If I had known, I would have come*?", "third conditional", ["first conditional", "second conditional", "zero conditional"], "Past perfect + would have + past participle."),
            ("Which relative clause needs commas?", "Mr Tabi, who teaches physics, is ill.", ["The man who teaches physics is ill.", "The book that I read is long.", "The town where I live is hot."], "It gives extra information about a named person.")]))

    # ------------------------------------------------------------------ passive and reported speech extras
    build(ch, dict(
        slug="passive-transform", title="Passive voice and sentence transformations", min=25,
        obj=["Change active sentences to passive in all common tenses.", "Decide when the passive is the better choice.", "Report questions, commands, modals and mixed sentences."],
        pre=["gceol-english-reported"],
        notes=["Complements the existing lesson on reported speech: only extras are covered here."],
        blocks=[
            ("key", "definition", "Forming the passive",
             "Passive = **be** (in the tense of the active verb) + **past participle**. The object of the active sentence becomes the subject; the doer comes after **by** (and is often left out).\n- present simple: *The cook **prepares** the meal.* → *The meal **is prepared** (by the cook).*\n- past simple: *was / were + pp*\n- present perfect: *has / have been + pp*\n- present continuous: *is / are being + pp*\n- future: *will be + pp*; modals: *can / must be + pp*"),
            ("fig", passive_fig(), "Only verbs that take an object (transitive verbs) can be made passive.", "Two rows of three boxes. Active: Subject, Verb, Object. Passive: Object, be plus past participle, by plus Subject. Arrows cross from the subject to by plus subject and from the object to the first position."),
            ("key", "retenir", "When to use the passive",
             "- The **doer is unknown or unimportant**: *My bicycle has been stolen.*\n- You want to focus on the **action or the result**: *The results will be announced on Friday.*\n- **Formal or scientific writing**: *The water was heated to 100 °C.*\nUse the active when the doer matters: it is shorter and stronger."),
            ("key", "retenir", "Reported speech: extras",
             "- Modals: *can → could, will → would, may → might, must → had to*.\n- Questions: *asked (me) if / whether…* with statement word order: *He asked, \"Do you like rice?\"* → *He asked whether I liked rice.*\n- Commands: *told / ordered / asked + person + (not) to + verb*.\n- *say* has no person after it (*said that*); *tell* needs one (*told me that*).\n- Suggestion: *\"Let's go\"* → *He suggested going.*"),
            ("key", "pieges", "Common mistakes",
             "- *The thief **was caught by** …* with the wrong participle: *was catched* → **caught**.\n- Passive of an intransitive verb: *The accident was happened* → **The accident happened.**\n- Keeping the question order: *She asked where was the library* → *where the library was*.\n- *He said me that…* → *He told me that…*"),
            ("ex", "Example 1: active to passive", "Rewrite in the passive: *The farmers harvested the cocoa.*",
             ["Object → subject: *The cocoa*. Verb in the past simple → *was harvested*.", "Doer after *by*: *by the farmers*."], "The cocoa was harvested by the farmers."),
            ("ex", "Example 2: a command and a question", "Report: (a) *\"Don't touch the wires,\" said the electrician.* (b) *\"What time does the bus leave?\" she asked.*",
             ["(a) A negative command: *told us not to touch…* → *The electrician told us not to touch the wires.*", "(b) A wh-question: statement order and one tense back: *She asked what time the bus left.*"], "(a) The electrician told us not to touch the wires. (b) She asked what time the bus left."),
        ],
        ex=[
            ("mcq", "Passive of *The cook prepared the meal*:", "The meal was prepared by the cook.", ["The meal is prepared by the cook.", "The meal was preparing by the cook.", "The cook was prepared by the meal."], "Past simple active → *was/were + past participle*."),
            ("mcq", "*The window ______ by the storm last night.*", "was broken", ["is broke", "broken", "was breaking"], "Passive past simple: was + broken."),
            ("tf", "A verb like *arrive* (no object) cannot be turned into the passive.", True, "Only transitive verbs can be made passive."),
            ("mcq", "Report: *She said, \"I can swim.\"*", "She said that she could swim.", ["She said that she can swim.", "She said me that she could swim.", "She said that I could swim."], "can → could; I → she."),
            ("mcq", "Report: *The teacher asked, \"Have you finished?\"*", "The teacher asked whether we had finished.", ["The teacher asked have we finished.", "The teacher asked that we have finished.", "The teacher asked did we finish."], "Yes/no questions use *if / whether* and the past perfect in place of the present perfect."),
            ("open", "Rewrite as instructed.\n1. Someone has stolen my bicycle. (Passive)\n2. People speak English in Buea. (Passive)\n3. \"Don't be late,\" the principal told us. (Reported)\n4. \"Where do you live?\" he asked Ayuk. (Reported)",
             "1. My bicycle has been stolen. 2. English is spoken in Buea. 3. The principal told us not to be late. 4. He asked Ayuk where he lived.",
             ["1 mark per correct sentence.", "A correct tense but wrong word order in 4 earns no mark."]),
        ],
        sc=[("The passive of *They will announce the results* is...", "The results will be announced.", ["The results will announce.", "The results are announced.", "The results would announced."], "will + be + past participle."),
            ("Which verb cannot be passive?", "arrive", ["build", "eat", "write"], "Arrive has no object."),
            ("*He said me that he was tired.* is wrong because...", "said needs no person: told me", ["tired is wrong", "that is wrong", "no mistake"], "He told me that…"),
            ("In reported speech *must* often becomes...", "had to", ["must be", "could", "should"], "must → had to."),
            ("*The thief caught by the police.* misses...", "was", ["is", "by", "a comma"], "A form of *be* is needed before the past participle.")]))

    # ------------------------------------------------------------------ punctuation
    build(ch, dict(
        slug="punctuation", title="Punctuation", min=22,
        obj=["Use full stops, commas, apostrophes, colons and semicolons correctly.", "Punctuate direct speech.", "Avoid comma splices and apostrophe errors."],
        notes=["British punctuation conventions (single or double quotation marks accepted) — confirm the preference of the GCE Board."],
        blocks=[
            ("text", "Punctuation shows the reader where a sentence stops, where to pause and who owns what. A missing comma can change the meaning: *Let's eat, grandfather* is not the same as *Let's eat grandfather*."),
            ("fig", cols("Punctuation marks at a glance", ["Mark", "Main use"], [[".", "ends a statement"], [",", "separates items or clauses"], ["'", "short form or possession"], [";", "joins two related clauses"], [":", "introduces a list or example"]], size=13),
             "The most tested marks.", "A table of punctuation marks. The full stop ends a statement, the comma separates items or clauses, the apostrophe shows a short form or possession, the semicolon joins two related clauses and the colon introduces a list or example."),
            ("key", "definition", "Commas",
             "Use a comma:\n- between **items in a list**: *rice, beans, plantain and fish*;\n- after an **introductory** word or clause: *When the bell rang, we left.*;\n- before *and / but / so* joining two **main clauses**: *It was late, so we ran.*;\n- around **extra information**: *Douala, the economic capital, is hot.*;\n- to introduce **direct speech**: *Ali said, \"I am ready.\"*"),
            ("key", "definition", "Apostrophe, colon, semicolon",
             "- **Short form:** *don't, it's, I'm* (the apostrophe replaces letters).\n- **Possession:** one owner → *'s* (*the pupil's book*); many owners ending in s → *s'* (*the pupils' books*); irregular plural → *'s* (*the children's toys*).\n- **Colon:** introduces a list or explanation: *Bring three things: a pen, a ruler and a rubber.*\n- **Semicolon:** joins two related main clauses: *Some like football; others prefer volleyball.*"),
            ("key", "definition", "Direct speech",
             "- Quotation marks around the **spoken words only**.\n- Capital letter at the start of the speech; a **comma** before it after the verb of saying.\n- The mark (, ? !) goes **inside** the quotation marks.\n- *\"I am ready,\" said Tabi.* (comma, then a small letter for *said*)\n- A new speaker = a **new line**."),
            ("key", "pieges", "Common mistakes",
             "- **Comma splice:** *It was late, we left.* → *It was late, so we left.*\n- Apostrophe in plurals: *banana's* → **bananas**.\n- *its / it's* confusion.\n- No comma after a long introductory clause.\n- Capital letters forgotten for names, days and months."),
            ("ex", "Example 1: commas in a list", "Punctuate: *the pupils bought notebooks pens and rulers at the market*",
             ["Capital letter at the start and a full stop at the end.", "A comma between the first items of the list; *and* replaces the last comma."], "The pupils bought notebooks, pens and rulers at the market."),
            ("ex", "Example 2: direct speech", "Punctuate: *Tabi said I am ready*",
             ["Put a comma after *said* and quotation marks around the spoken words.", "Capital letter at the start of the speech and a full stop inside the closing mark."], "Tabi said, \"I am ready.\""),
        ],
        ex=[
            ("mcq", "Which phrase is correct for the hostel of many girls?", "the girls' hostel", ["the girl's hostel", "the girls hostel's", "the girls's hostel"], "Many owners: the apostrophe goes after the s."),
            ("mcq", "Choose the correctly punctuated sentence.", "Mr Ndi, our principal, arrived late.", ["Mr Ndi our principal, arrived late.", "Mr Ndi, our principal arrived, late.", "Mr Ndi our principal arrived late,"], "Extra information is placed between two commas."),
            ("tf", "A comma alone is enough to join two full sentences.", False, "That is a comma splice: use a conjunction, a semicolon or a full stop."),
            ("mcq", "Choose the correct direct speech.", "Ayuk asked, \"Where is the key?\"", ["Ayuk asked, \"where is the key\".", "Ayuk asked \"Where is the key?\"", "Ayuk asked, Where is the key?"], "A comma before the speech, a capital letter, and the question mark inside the quotation marks."),
            ("mcq", "Where is the semicolon correct?", "Some like football; others prefer volleyball.", ["Some like; football others prefer volleyball.", "Some like football others; prefer volleyball.", "Some; like football, others prefer volleyball."], "A semicolon joins two related main clauses."),
            ("open", "Punctuate the passage (rewrite it):\n\nlast saturday we went to the market in bafoussam we bought tomatoes onions fish and bread the seller said these are the best tomatoes in town",
             "Last Saturday we went to the market in Bafoussam. We bought tomatoes, onions, fish and bread. The seller said, \"These are the best tomatoes in town.\"",
             ["1 mark: capital letters (Last, Bafoussam, We, The, These).", "1 mark: full stops / sentence boundaries.", "1 mark: commas in the list.", "1 mark: direct speech punctuated."]),
        ],
        sc=[("Choose the correct possessive form.", "The children's toys", ["The childrens' toys", "The childrens toys", "The children toy's"], "Irregular plural: children's."),
            ("*It's* means...", "it is or it has", ["belonging to it", "its own", "it was"], "The apostrophe replaces letters."),
            ("Which sentence has a comma splice?", "It was late, we left.", ["It was late, so we left.", "It was late; we left.", "It was late. We left."], "Two main clauses joined by only a comma."),
            ("Where do the quotation marks go?", "around the spoken words only", ["around the whole sentence", "around the name", "nowhere"], "Quote only what is said."),
            ("A colon is used to...", "introduce a list", ["end a question", "show possession", "join a name"], "*Bring: a pen, a ruler.*")]))

    # ------------------------------------------------------------------ spelling
    build(ch, dict(
        slug="spelling", title="Spelling rules and commonly misspelt words", min=20,
        obj=["Apply the rules for plurals, doubling and ie/ei.", "Spell fifteen frequently misspelt words.", "Use memory tricks and proof-reading."],
        notes=["British spelling is used throughout (colour, practise). Check any word list required by the syllabus."],
        blocks=[
            ("text", "Spelling is marked in every written paper. Most errors come from a small number of words and rules. Learn the rules below, then learn the list by writing each word three times and **covering it** to test yourself."),
            ("key", "definition", "Plural rules",
             "- Add **-s**: *book → books*.\n- After **s, x, z, ch, sh** add **-es**: *bus → buses, box → boxes, church → churches*.\n- Consonant + **y** → **-ies**: *city → cities*; vowel + y → **-ys**: *day → days*.\n- Some words in **-f / -fe** → **-ves**: *leaf → leaves, knife → knives*.\n- Irregular: *child → children, man → men, tooth → teeth, sheep → sheep*."),
            ("key", "definition", "Other useful rules",
             "- **i before e, except after c** (same sound ee): *believe, receive*. Exceptions: *weird, seize*.\n- **Doubling:** one-syllable word ending in one vowel + one consonant: *stop → stopping*; stressed last syllable: *begin → beginning*; not when the stress is earlier: *visit → visiting*.\n- **Silent letters:** *knife, knowledge, honest, wrist, island, Wednesday*."),
            ("fig", cols("Fifteen words to master", ["Word", "Trick"], [["necessary", "one collar, two sleeves"], ["separate", "a RAT in sepaRATe"], ["definitely", "contains finite"], ["occasion", "two c, one s"], ["accommodation", "two c, two m"]], size=12),
             "Five of the fifteen words with a memory trick.", "A table with two columns, Word and Trick: necessary, one collar two sleeves; separate, a rat in separate; definitely, contains finite; occasion, two c one s; accommodation, two c two m."),
            ("key", "retenir", "The fifteen words",
             "necessary · separate · definitely · occasion · beginning · accommodation · government · environment · February · library · recommend · embarrass · success · address · receive.\nTrick: **government** has an *n* (govern**ment**); **library** has *ra* twice-looking (li**br**ary, not *libary*)."),
            ("key", "pieges", "Common mistakes",
             "- *recieve* → **receive**; *seperate* → **separate**; *definately* → **definitely**.\n- *begining* → **beginning** (doubled n).\n- *goverment, enviroment* → the *n* before the *m* is needed.\n- Mixing British and American spelling (*colour / color*): choose one and keep it.\n- Not proof-reading: always reread for spelling in the last five minutes."),
            ("ex", "Example 1: plural", "Write the plural of: city, box, knife, child.",
             ["Consonant + y: cities. Ends in x: boxes.", "-fe → -ves: knives. Irregular: children."], "cities, boxes, knives, children"),
            ("ex", "Example 2: choose the correct spelling", "Which is correct: *recieve / receive*?",
             ["Check the rule: after **c**, write **ei**.", "So *receive* is correct."], "receive"),
        ],
        ex=[
            ("mcq", "Which spelling is correct?", "necessary", ["neccessary", "necesary", "neccesary"], "One c, two s: necessary."),
            ("mcq", "The plural of *church* is...", "churches", ["churchs", "churchies", "churchen"], "Ends in ch: add -es."),
            ("tf", "The correct spelling is *recieve*.", False, "After c we write ei: receive."),
            ("mcq", "Choose the correct word: *Add the -ing form of begin.*", "beginning", ["begining", "beginings", "beggining"], "Stress on the last syllable: double the n."),
            ("mcq", "Which word is spelt correctly?", "government", ["goverment", "govenment", "governmet"], "The word contains govern + ment."),
            ("open", "Find and correct the **six** spelling mistakes:\n\n\"Last Febuary there was an occassion in our libary. The goverment sent a seperate team to help us recieve guests.\"",
             "February, occasion, library, government, separate, receive.",
             ["1 mark per word corrected (six words, but marks capped at 4).", "Marks lost if a correct word is changed."]),
        ],
        sc=[("The plural of *leaf* is...", "leaves", ["leafs", "leafes", "leavs"], "-f → -ves."),
            ("The plural of *day* is...", "days", ["daies", "dayes", "days'"], "Vowel + y: add s."),
            ("Which spelling of the adverb meaning « certainly » is correct?", "definitely", ["definately", "definitly", "defanitely"], "Think of finite."),
            ("Which word has a silent k?", "knowledge", ["kitchen", "kettle", "key"], "The k is silent in kn-."),
            ("Choose the correct spelling of the word meaning « not joined ».", "separate", ["seperate", "separete", "seprate"], "A rat in separate.")]))
    return ch
