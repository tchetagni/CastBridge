from common import *


def intonation_fig():
    it = [T(210, 20, "Intonation: the voice falls or rises", 15, bold=True),
          POLY([20, 60, 80, 48, 150, 105], closed=False, stroke="blue", width=3),
          LINE(150, 105, 160, 110, arrow="end", color="blue", width=3),
          POLY([240, 105, 310, 98, 395, 48], closed=False, stroke="orange", width=3),
          LINE(395, 48, 400, 42, arrow="end", color="orange", width=3),
          T(95, 150, "Where do you live?", 13), T(95, 170, "falls at the end", 12, color="blue", bold=True),
          T(315, 150, "Are you ready?", 13), T(315, 170, "rises at the end", 12, color="orange", bold=True)]
    return shapes(it, 420, 185)


def stress_fig():
    it = [T(210, 20, "Word stress: the big circle is the stressed syllable", 14, bold=True),
          CIRCLE(70, 80, 14, fill="blue", stroke="blue"), CIRCLE(110, 84, 7, fill="lightblue", stroke="blue"),
          T(90, 125, "REcord", 14, bold=True), T(90, 145, "noun (a record)", 12),
          CIRCLE(270, 84, 7, fill="lightorange", stroke="orange"), CIRCLE(310, 80, 14, fill="orange", stroke="orange"),
          T(290, 125, "reCORD", 14, bold=True), T(290, 145, "verb (to record)", 12)]
    return shapes(it, 420, 165)


def skills(p):
    ch = p.chapter("ch-skills", "Comprehension skills and summary extras", "O Level English Language — comprehension (inference, figurative language, tone, purpose) and summary (to be checked against the official texts)")

    # ------------------------------------------------------------------ inference, figures, tone, purpose
    build(ch, dict(
        slug="inference-figurative", title="Inference, figurative language, tone and the writer's purpose", min=28,
        obj=["Tell literal, inference and evaluation questions apart.", "Recognise and explain simile, metaphor, personification, hyperbole and irony.", "Identify the tone of a passage and the writer's purpose."],
        pre=["gceol-english-reading"],
        notes=["The passage is original; the question types follow the usual O Level pattern but the exact wording of Board questions may differ."],
        blocks=[
            ("text", "**The Last Bus**\nBy five o'clock the motor park was nearly empty. Mama Ngu pulled her scarf tighter and looked again at her watch. The last bus to Bamenda was an hour late. Around her, drivers argued about fares, and a boy sold groundnuts from a tray on his head. Her basket of cocoyams sat at her feet, heavy and forgotten. Then, through the dust, she saw two headlights, and her shoulders dropped as if a great load had been lifted. \"At last,\" she whispered, and she began to smile."),
            ("fig", flow("Three kinds of question", ["Literal: the answer is written in the text", "Inference: read between the lines", "Evaluation: give a reasoned opinion"], h=170),
             "Match your method to the type of question.", "Three boxes joined by arrows: literal where the answer is written in the text; inference where you read between the lines; evaluation where you give a reasoned opinion."),
            ("key", "definition", "Inference",
             "An **inference** is a conclusion you draw from clues. The text never says *Mama Ngu is worried*, but she **looked again at her watch**, the bus was **an hour late** and she **smiled** when it arrived. Method: find the clue, say what it suggests, and **quote briefly** to prove it."),
            ("key", "definition", "Figures of speech",
             "- **Simile:** compares with *like / as*: *her shoulders dropped as if a great load had been lifted*.\n- **Metaphor:** says one thing *is* another: *The market was a beehive.*\n- **Personification:** gives human actions to things: *The old truck groaned up the hill.*\n- **Hyperbole:** deliberate exaggeration: *I have told you a million times.*\n- **Irony:** the opposite of what is meant, or of what is expected: *« What lovely weather! »* (in heavy rain)."),
            ("key", "definition", "Tone and purpose",
             "**Tone** is the writer's attitude: *serious, humorous, sarcastic, angry, sad, hopeful, critical, nostalgic, formal*. Look at word choice and punctuation.\n**Purpose** is why the text was written: to **inform**, **persuade**, **entertain**, **describe**, **advise** or **warn**. A passage can have a mood too: the feeling it creates in the reader (*tense, relieved*)."),
            ("key", "pieges", "Common mistakes",
             "- Naming the device but not explaining its **effect**: always say what it makes us feel or see.\n- Copying a long part of the text as an answer to an inference question.\n- Confusing **simile** (like / as) with **metaphor** (is).\n- Giving the character's feeling without a **clue** from the text.\n- Choosing a tone word that is too weak or too general (*sad* instead of *hopeless*)."),
            ("ex", "Example 1: an inference question", "Why did Mama Ngu look at her watch again?",
             ["The text says the last bus was an hour late and the motor park was nearly empty.", "So she was anxious: she was afraid to be left without transport. She looked at her watch because she was impatient."], "She was anxious because the last bus was an hour late and she feared being stranded."),
            ("ex", "Example 2: a figure of speech", "Explain *her shoulders dropped as if a great load had been lifted*.",
             ["The device is a **simile** (*as if*).", "The effect: it compares her worry to a heavy load, so we feel her relief when the bus arrives."], "A simile; it shows how great her relief is."),
        ],
        ex=[
            ("mcq", "*Roofs lay in the yard like fallen playing cards.* This is a...", "simile", ["metaphor", "personification", "irony"], "It uses *like* to compare."),
            ("mcq", "Passage: *After the storm, roofs lay in the yard like fallen playing cards. The old mango tree, which had shaded three generations, lay across the path with its roots in the air.* What can we infer about the storm?", "It was very violent.", ["It was gentle.", "It lasted only one minute.", "It frightened only the animals."], "Roofs torn off and a very old tree uprooted suggest great violence."),
            ("tf", "In the passage above, the mango tree was still standing.", False, "It *lay across the path*."),
            ("mcq", "*The generator coughed twice, complained bitterly and died.* Which device is used?", "personification", ["simile", "hyperbole", "alliteration"], "A machine is given human actions: coughing and complaining."),
            ("mcq", "*« What a wonderful day! » said Ngu as the rain soaked his only shirt.* This is...", "irony", ["a metaphor", "a literal statement", "a simile"], "His words mean the opposite of the situation."),
            ("problem", "Read: *The generator coughed twice, complained bitterly and died. In the dark classroom the pupils groaned. \"Not again,\" sighed Mr Ndi, who had promised the Form 5 class a laboratory session since Monday.*", [
                part_mcq("Which figure of speech is *coughed twice, complained bitterly*?", "personification", ["simile", "metaphor", "hyperbole"], "The generator is given human actions."),
                part_mcq("What can we infer about power cuts at the school?", "They happen often.", ["They never happen.", "They happen only on Mondays.", "They are planned."], "*Not again* shows the problem has happened before."),
                part_open("Describe the tone of the passage in one or two words and give a reason.", "Humorous and slightly frustrated: the generator is described like a complaining person, and the teacher sighs.", ["1 mark: a suitable tone word (humorous, light-hearted, frustrated).", "1 mark: a reason from the text."])]),
        ],
        sc=[("A comparison using *as* or *like* is a...", "simile", ["metaphor", "irony", "pun"], "Simile."),
            ("*The market was a beehive.* is a...", "metaphor", ["simile", "hyperbole", "proverb"], "It says one thing is another."),
            ("To answer an inference question you must...", "use clues from the text", ["copy a paragraph", "guess without reason", "give a personal story"], "Inference needs evidence."),
            ("A writer who warns readers about malaria has the purpose to...", "warn and inform", ["only entertain", "describe a scene", "tell a joke"], "The purpose is to warn."),
            ("Hyperbole is...", "deliberate exaggeration", ["a comparison", "a rhyme", "a question"], "e.g. *I'm so hungry I could eat a horse.*")]))

    # ------------------------------------------------------------------ summary extras
    build(ch, dict(
        slug="summary-technique", title="Summary technique: notes, paraphrase and linking", min=26,
        obj=["Select only the points asked for in the question.", "Rewrite the points in your own words.", "Link the points into a fluent paragraph within the word limit."],
        pre=["gceol-english-summary"],
        notes=["Complements the existing summary lesson with a note-making routine. The word-limit allowances of the Board must be checked."],
        blocks=[
            ("text", "**The Cooperative**\nSince the farmers joined the cooperative, life has changed. Members sell their cocoa together, so buyers pay a better price than any single farmer could get. The cooperative keeps a small shop where fertiliser costs less, and its truck carries the harvest to Kumba every week, which saves the cost of hiring a car. A savings box also lets members borrow money when their children need school fees. Some farmers first refused to join, afraid of losing control of their crop, but they have since changed their minds."),
            ("fig", flow("The summary routine", ["1 Read the question: what points exactly?", "2 Read the text; underline relevant points", "3 Write notes in your own words", "4 Join the notes in one paragraph", "5 Count the words and check"], h=270),
             "Never write the final answer before you have made notes.", "A vertical flow of five boxes: read the question, read the text and underline, write notes in your own words, join the notes in one paragraph, count and check."),
            ("key", "methode", "From the text to notes",
             "Question: *In about 40 words, state the advantages the members of the cooperative enjoy.*\n- Underline only **advantages**: not the refusal of some farmers.\n- Notes: **1** better price for cocoa; **2** cheaper fertiliser; **3** transport to Kumba saves money; **4** loans for school fees.\n- Cross out **examples, repetitions and details**; keep the idea only."),
            ("key", "methode", "Paraphrase and linking",
             "- **Paraphrase** = say it in different words: *costs less* → *is cheaper*; *carries the harvest* → *transports the crop*. Keep technical words (*cocoa, fertiliser*).\n- **Join** with short linkers: *and, also, as well as, moreover, so that*.\n- Use **one paragraph**, complete sentences, **present tense** here (the same tense as the text).\n- **Count** words: contractions and numbers count as one word each, as taught in class."),
            ("key", "pieges", "Common mistakes",
             "- Including **details that are not asked for** (the refusal of some farmers).\n- **Lifting** whole sentences of the passage (little or no mark for language).\n- A **list of notes** instead of a paragraph.\n- Adding your **opinion**.\n- Going far over the limit: stop after the last point."),
            ("ex", "Example 1: selecting the points", "Which of these are advantages for members? (a) better price for cocoa (b) some farmers refused to join (c) cheaper fertiliser (d) the shop is small.",
             ["Ask: does the point give a benefit to the members?", "(a) and (c) do. (b) is a past attitude and (d) is a detail of the shop."], "(a) and (c)"),
            ("ex", "Example 2: from notes to a paragraph", "Join the notes: better price; cheaper fertiliser; transport to Kumba saves money; loans for school fees.",
             ["Use the first sentence for the first three notes, joined by *and* and a short linker.", "Add the last note with *also*."], "Members receive a better price for their cocoa and buy cheaper fertiliser. They also save money on transport to Kumba and can borrow money for school fees."),
        ],
        ex=[
            ("mcq", "In a summary you should...", "use your own words", ["copy sentences", "add your opinion", "give every detail"], "A summary shortens and paraphrases."),
            ("mcq", "Which phrase is a paraphrase of *costs less*?", "is cheaper", ["costs more", "is expensive", "is not sold"], "Same meaning, different words."),
            ("tf", "Notes are the final answer of a summary.", False, "The answer is a fluent paragraph written from the notes."),
            ("mcq", "Which of these should NOT appear in a summary of the advantages?", "Some farmers at first refused to join.", ["Cocoa is sold at a better price.", "Fertiliser is cheaper.", "Loans are available for school fees."], "It is not an advantage; the question asks for advantages only."),
            ("mcq", "Choose the best linking word: *Members get a better price. ______, they can borrow money.*", "Moreover", ["Although", "Unless", "Because"], "Moreover adds an extra point."),
            ("open", "Read: *The old market in Bali was rebuilt last year. The new market has a roof, so traders no longer stop work when it rains. Wide paths make it easier for customers to move about. Clean toilets and a water tap have also been added, and a guard now watches over the stalls at night. However, the cost of renting a stall has risen.*\nIn about 40 words, summarise the **improvements** made to the market.",
             "The rebuilt market has a roof so that trading continues in the rain, wider paths for customers, clean toilets, a water tap and a night guard.",
             ["1 mark per correct point (roof, wide paths, toilets and water, guard) up to 4.", "Deduct for the rise of rent (not an improvement) or for copying whole sentences.", "Language: own words, correct, about 40 words."]),
        ],
        sc=[("Underline in the text only...", "what the question asks for", ["everything", "the first line", "the examples"], "Selection is the first skill."),
            ("Paraphrase means...", "say it in other words", ["copy it", "translate it", "shorten it by cutting the verb"], "Same idea, new words."),
            ("A summary is normally written in...", "one paragraph", ["a list", "a poem", "several pages"], "A paragraph of complete sentences."),
            ("Which belongs in a summary?", "the main points", ["every example", "your opinion", "a quotation"], "Only the main points."),
            ("If you are over the word limit you should...", "remove details and repetitions", ["add a conclusion", "continue", "use shorter handwriting"], "Cut details first.")]))
    return ch


def oral(p):
    ch = p.chapter("ch-oral", "Oral English and listening", "O Level English Language — oral English, listening and note-taking (to be checked against the official texts)")

    # ------------------------------------------------------------------ oral english
    L = build(ch, dict(
        slug="oral-english", title="Oral English: stress, intonation, sounds and rhyme", min=26,
        obj=["Place the stress in common words, including noun/verb pairs.", "Use falling and rising intonation correctly.", "Tell apart frequent vowel and consonant contrasts and recognise rhyme."],
        notes=["Sounds are described by example words and in plain words, not with phonetic symbols. Pronunciation follows standard British English; a teacher or audio recording should confirm every example. Audio uses a text-to-speech voice that may not be perfect."],
        blocks=[
            ("text", "Oral English tests **how words sound**, not how they are spelt. Practise **out loud**, listen to a model speaker (a radio newsreader or your teacher) and compare. The audio buttons in this lesson read the examples aloud."),
            ("audio", "Listen to these pairs. Ship, sheep. Bad, bed. Think, sink. Ship, chip. Light, right."),
            ("key", "definition", "Word stress",
             "Every word of two or more syllables has one **stressed** syllable (said louder, longer, higher): *TAble, beGIN, eduCAtion, PHOtograph*.\n- Some words change class with the stress: noun **REcord** / verb **reCORD**; noun **PREsent** / verb **preSENT**; noun **OBject** / verb **obJECT**.\n- Words ending in **-tion, -sion, -ic, -ity** are stressed on the syllable **before** the ending: *eduCAtion, ecoNOMic, abilITy*."),
            ("fig", stress_fig(), "Mark the stress with capital letters.", "Two words. REcord as a noun with a big circle on the first syllable and a small one on the second. reCORD as a verb with a small circle then a big one."),
            ("key", "definition", "Sentence stress and intonation",
             "- In a sentence we stress the **content words** (nouns, main verbs, adjectives, adverbs), not *the, a, of, to*: *I WANT to GO to MARket.*\n- **Falling** intonation: statements, commands and *wh*- questions (*Where do you live?*).\n- **Rising** intonation: yes/no questions (*Are you ready?*), and the items of a list before the last one; the last item falls.\n- Moving the stress changes the meaning: *I did not take your pen* (somebody else did) / *I did not TAKE your pen* (I borrowed it)."),
            ("fig", intonation_fig(), "A wh- question falls; a yes/no question rises.", "Two lines. The blue line falls at the end of Where do you live. The orange line rises at the end of Are you ready."),
            ("key", "definition", "Vowel and consonant contrasts",
             "- *ship* (short i) / *sheep* (long ee); *full* / *fool*.\n- *bad* (a as in cat) / *bed* (e as in pen); *cup* (short u) / *cart* (long ah).\n- *think* (tongue between the teeth) / *sink*; *this* / *dis*.\n- *ship* (sh) / *chip* (ch); *light* / *right* (tongue position); *very* / *wary*; *bag* / *back*.\nA minimal pair differs by **one sound** only: practise pairs aloud."),
            ("key", "definition", "Rhyme, homophones and silent letters",
             "- Two words **rhyme** when they sound the same from the last stressed vowel: *cake – make*, *know – though – go*. Spelling can mislead: *cough* does not rhyme with *though*, and *bough* does not rhyme with *through*.\n- **Homophones** sound the same but are spelt differently: *right / write, hear / here, their / there*.\n- **Silent letters:** *knife, honest, wrist, island, climb*."),
            ("key", "pieges", "Common mistakes",
             "- Stressing every syllable equally (a flat, « machine-gun » rhythm).\n- Judging rhyme by **spelling**: *tough* and *though* do not rhyme.\n- Raising the voice at the end of every sentence.\n- Mixing the two *th* sounds with *t* or *d*, or *ship* with *sheep*.\n- Putting the stress mark on the wrong syllable in a written exercise."),
            ("ex", "Example 1: the stress", "Mark the stressed syllable in: (a) begin (b) teacher (c) education.",
             ["Two-syllable verbs often take the stress on the second syllable: **beGIN**; nouns on the first: **TEAcher**.", "Words ending in -tion are stressed just before the ending: **eduCAtion**."], "beGIN, TEAcher, eduCAtion"),
            ("ex", "Example 2: rhyme", "Which word rhymes with *though*: (a) cough (b) go (c) through?",
             ["Ignore the spelling and say the last sound: though = « thoh ».", "Go = « goh »: same sound. Cough = « koff » and through = « throo »."], "(b) go"),
        ],
        ex=[
            ("mcq", "Which word has the stress on the SECOND syllable?", "begin", ["table", "teacher", "happy"], "beGIN; the other three are stressed on the first syllable."),
            ("mcq", "In *a REcord* (the thing you listen to), the stress is on the...", "first syllable", ["second syllable", "third syllable", "both syllables equally"], "Nouns of this type are stressed on the first syllable; the verb reCORD on the second."),
            ("tf", "A yes/no question such as *Are you ready?* normally ends with a rising tone.", True, "A rising tone invites a yes or no answer."),
            ("mcq", "Which word rhymes with *though*?", "go", ["cough", "through", "bough"], "Rhyme depends on sound, not spelling."),
            ("mcq", "Which two words have the SAME vowel sound?", "sheep and leave", ["ship and sheep", "bad and bed", "hot and hat"], "Sheep and leave share the long ee."),
            ("open", "Write two sentences using the word *present*: once as a noun and once as a verb. Write the stressed syllable in capital letters and say which is which.",
             "I gave my mother a PREsent (noun). The principal will preSENT the prizes tomorrow (verb).",
             ["1 mark: a correct noun sentence with PREsent.", "1 mark: a correct verb sentence with preSENT.", "1 mark: both words labelled correctly."]),
        ],
        sc=[("Which words are normally stressed in a sentence?", "nouns and main verbs", ["the and a", "of and to", "none"], "Content words carry the stress."),
            ("Which pair is a minimal pair?", "ship – sheep", ["ship – boat", "bad – good", "go – went"], "They differ by one sound."),
            ("*Where do you live?* normally ends with a...", "falling tone", ["rising tone", "pause", "whisper"], "A wh- question falls."),
            ("Which word is a homophone of *write*?", "right", ["wrote", "white", "writer"], "Same sound, different spelling."),
            ("The word *education* is stressed on...", "the third syllable (CA)", ["the first syllable", "the last syllable", "the second syllable"], "eduCAtion: before -tion.")]))
    L.audio("Where do you live? Are you ready? I want to go to market. Bring a pen, a ruler, and a notebook.")

    # ------------------------------------------------------------------ listening and note-taking
    build(ch, dict(
        slug="listening-notes", title="Listening and note-taking", min=22,
        obj=["Listen for key words: names, numbers, dates and places.", "Use abbreviations and a clear layout when making notes.", "Turn notes into correct sentences."],
        notes=["The audio block uses a text-to-speech voice; a teacher should preferably read the passages aloud. The abbreviation list is a suggestion."],
        blocks=[
            ("text", "In listening tasks you hear a text **once or twice** and must write down the important points. You cannot write everything: **listen for key words** and write in **note form**. After listening you turn your notes into complete sentences."),
            ("audio", "Good morning, students. This is an announcement from the principal's office. The school sports day will take place on Thursday the fourteenth of May, not on Friday as announced earlier. Pupils should arrive at seven thirty in sports wear. Each class must bring two buckets of water and one first-aid box. Parents are invited and will sit under the tents near the football field. The first event is the one hundred metre race at eight o'clock."),
            ("fig", flow("Making notes while you listen", ["Title: topic of the text", "Key words: who, what, when, where", "Numbers, dates, names (exact)", "Signpost words: first, however, finally"], h=215),
             "Write only the words you will need later.", "Four boxes joined by arrows: title or topic; key words who what when and where; exact numbers dates and names; signpost words like first, however and finally."),
            ("key", "methode", "Before, during, after",
             "- **Before:** read the questions, predict what you will hear, and prepare a layout (headings or a grid).\n- **During:** write **key words only**; use **abbreviations** and **symbols**; leave a space if you miss something; do not stop to think about one word.\n- **After:** complete your notes at once, check numbers and names, and write answers in full sentences if asked."),
            ("key", "retenir", "Useful abbreviations",
             "- & = and; w/ = with; → = leads to / becomes; ↑ = increase; ↓ = decrease; no. = number; e.g. = for example.\n- Days and months: *Thu, May*; money: *FCFA 2,000*; times: *7.30 am*.\n- Use your own short forms if you can read them later. Write numbers as **digits**."),
            ("key", "pieges", "Common mistakes",
             "- Trying to write every word and missing the next sentence.\n- Writing the **wrong number** (fourteen / forty, thirteen / thirty): listen to the stress and the ending.\n- Notes that you cannot read afterwards.\n- Copying the speaker's wording instead of answering the exact question.\n- Missing a **correction**: *Thursday, not Friday*."),
            ("ex", "Example 1: notes from the announcement", "Make notes from the announcement above (sports day).",
             ["Title: **Sports Day**. Date: **Thu 14 May** (not Fri). Time: arrive **7.30 am**, first event **8.00 am** (100 m race).", "Bring: **2 buckets of water + 1 first-aid box** per class. Dress: sports wear. Parents: invited, tents near the football field."], "Sports Day: Thu 14 May (not Fri); 7.30 arrive; sports wear; each class 2 buckets water + 1 first-aid box; parents invited; 8.00 100 m race."),
            ("ex", "Example 2: from notes to a sentence", "Write a sentence from the notes: *Thu 14 May (not Fri); 7.30 arrive; sports wear.*",
             ["Use full words and the correct tense: this is an announcement about the future.", "Include the correction: Thursday, not Friday."], "The sports day will take place on Thursday 14 May, not on Friday; pupils must arrive at 7.30 in sports wear."),
        ],
        ex=[
            ("mcq", "While listening, you should write...", "key words only", ["every word", "nothing", "whole sentences"], "Key words and numbers save time."),
            ("mcq", "Which of these is a useful abbreviation for *and*?", "&", ["#", "@", "%"], "& is the common symbol for *and*."),
            ("tf", "You should leave a gap in your notes if you miss something and carry on listening.", True, "Stopping to think will make you miss the next point."),
            ("mcq", "Announcement: *The meeting is on Thursday at two o'clock, not at three.* When is the meeting?", "Thursday at 2", ["Thursday at 3", "Friday at 2", "Friday at 3"], "The correction replaces the earlier time."),
            ("mcq", "Which words in a talk often signal a change or contrast?", "however, but", ["first, second", "for example", "in the morning"], "However and but introduce a contrast."),
            ("open", "Listen (or read aloud): *Tomorrow the Red Cross will visit our school. Doctors will check the pupils' eyes from 9 a.m. in Room 3. Pupils from Form 1 to Form 3 go first. Pupils must bring their health booklets. The service is free.*\nMake **notes** in note form (under 30 words), then write one full sentence from them.",
             "Notes: Red Cross visit tomorrow; eye check 9 am Room 3; Forms 1-3 first; bring health booklet; free. Sentence: Tomorrow the Red Cross will check pupils' eyes free of charge from 9 a.m. in Room 3, and pupils must bring their health booklets.",
             ["1 mark: the day, time and room.", "1 mark: who goes first; what to bring; free.", "1 mark: the notes are short (under 30 words).", "1 mark: a correct full sentence."]),
        ],
        sc=[("In note-taking, a number should be written as...", "digits", ["words", "a drawing", "a letter"], "Digits are quicker."),
            ("What should you do before the recording starts?", "read the questions", ["close your eyes", "write a story", "talk to a friend"], "Predicting helps you listen for key words."),
            ("Notes should be written in...", "short note form", ["long sentences", "a poem", "capital letters only"], "Short form saves time."),
            ("Which is a signpost word?", "finally", ["table", "green", "Douala"], "It shows the order of ideas."),
            ("After listening you should...", "complete and check your notes", ["throw them away", "ask for more audio", "write a letter"], "Fill in the gaps while the memory is fresh.")]))
    return ch
