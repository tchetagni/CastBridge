from common import *


def mountain():
    it = [T(210, 22, "The story mountain", 15, bold=True),
          LINE(20, 190, 130, 110, width=3, color="blue"), LINE(130, 110, 210, 50, width=3, color="blue"),
          LINE(210, 50, 300, 110, width=3, color="orange"), LINE(300, 110, 400, 190, width=3, color="orange"),
          LINE(10, 190, 410, 190, width=2, color="grey"),
          CIRCLE(210, 50, 6, fill="red", stroke="red"),
          T(60, 212, "1 Opening", 12, bold=True), T(150, 212, "2 Build-up", 12, bold=True), T(250, 36, "3 Climax", 13, bold=True, color="red"),
          T(335, 212, "4 Falling", 12, bold=True), T(380, 228, "5 Ending", 12, bold=True)]
    return shapes(it, 420, 240)


def writing(p):
    ch = p.chapter("ch-writing", "Writing for different purposes", "O Level English Language — continuous writing: letters, reports, speeches, articles, narrative and descriptive essays (to be checked against the official texts)")

    # ------------------------------------------------------------------ application letter
    build(ch, dict(
        slug="application-letter", title="Application letters and letters to the editor", min=24,
        obj=["Lay out and write a formal application letter.", "Write a short curriculum vitae outline.", "Write a letter to a newspaper editor giving an opinion."],
        pre=["gceol-english-composition"],
        notes=["Layout conventions (position of the date, « Yours faithfully ») follow the commonly taught British pattern, as in the existing composition lesson; confirm with the GCE Board marking guide."],
        blocks=[
            ("text", "An application letter is a **formal letter** with one aim: to persuade the reader to give you an interview or a place. It must be short (one page), correct, and tailored to **this** job or course. A letter to the editor is also formal, but its aim is to give an **opinion** about a public matter."),
            ("fig", flow("Plan of an application letter", ["Your address and the date (top right)", "Employer's title and address (left)", "Dear Sir or Madam,", "SUBJECT LINE: APPLICATION FOR THE POST OF …", "Para 1: the post, where you saw it", "Para 2: qualifications and skills", "Para 3: availability, thanks, closing"], h=345),
             "The same layout works for a job or a scholarship.", "A vertical chain of seven boxes joined by arrows: your address and date; the employer's address; Dear Sir or Madam; the subject line in capitals; paragraph one about the post; paragraph two about qualifications; paragraph three about availability and a polite closing."),
            ("key", "methode", "Content of each paragraph",
             "1. **Opening:** *I wish to apply for the post of … advertised in … on …*\n2. **Qualifications:** your GCE results, relevant skills, experience or school responsibilities. Give **evidence** (*I was the library prefect for two years*), not empty praise.\n3. **Closing:** you are available for interview, you enclose a CV and copies of certificates; *I look forward to hearing from you.* End with **Yours faithfully** (after *Dear Sir or Madam*) and your full name."),
            ("key", "definition", "A short CV (curriculum vitae)",
             "Headings, each with a short list:\n- **Personal details** (name, date of birth, nationality, address, telephone);\n- **Education** (schools, exams, years; the latest first);\n- **Experience or responsibilities**;\n- **Skills** (languages, computing, driving…);\n- **Referees** (two people who know you, with their positions).\nKeep it on one page; use the same tidy layout for every heading."),
            ("key", "retenir", "The letter to the editor",
             "- Formal layout, addressed to **The Editor** of the newspaper.\n- Subject line giving the topic: *RE: LITTER IN OUR MARKETS*.\n- Paragraph 1: what you are writing about and why. Paragraphs 2–3: your **reasons** and **examples**, politely argued. Final paragraph: a **suggestion** or a call for action.\n- Close with *Yours faithfully* and your name, or your title (*A concerned parent*)."),
            ("key", "pieges", "Common mistakes",
             "- *Yours sincerely* after *Dear Sir* (use **faithfully**).\n- Slang or abbreviations (*pls, u*).\n- Praising yourself without proof: *I am the best candidate.*\n- A letter that could be sent to any employer: refer to the **specific post**.\n- Forgetting the subject line, or writing more than one page."),
            ("ex", "Example 1: the opening paragraph", "You saw an advertisement for a sales assistant in a bookshop in Bamenda. Write the first paragraph of your application.",
             ["Name the post and where and when you saw the advertisement.", "Add one short sentence saying you are applying and why the job interests you, in a formal tone with no contractions."], "I wish to apply for the post of sales assistant advertised in the Cameroon Tribune of 3 March. I enjoy reading and helping people, and I believe I would be a reliable member of your team."),
            ("ex", "Example 2: choosing the right evidence", "Which of these is better for the second paragraph: (a) I am very hardworking. (b) I helped my class prefect to keep the class library for two years.",
             ["(a) is a claim without any proof.", "(b) gives a concrete fact that shows responsibility: use it."], "(b)"),
        ],
        ex=[
            ("mcq", "A letter begins *Dear Sir or Madam*. How should it end?", "Yours faithfully", ["Yours sincerely", "Your friend", "Best wishes"], "Without a name: Yours faithfully. With a name (Dear Mr Tabi): Yours sincerely."),
            ("mcq", "Which opening sentence is the most suitable?", "I wish to apply for the post of laboratory assistant advertised in your school.", ["Hello! I want the lab job.", "Please give me work, I am poor.", "I am the best, so employ me."], "It is formal, clear and names the post."),
            ("tf", "Slang makes an application letter more friendly and is recommended.", False, "A formal letter uses formal, correct English."),
            ("mcq", "Which item does NOT belong in a CV?", "A long story about your childhood", ["Education", "Skills", "Referees"], "A CV lists facts under headings; it is not a story."),
            ("mcq", "In a letter to the editor, the last paragraph usually...", "makes a suggestion or calls for action", ["repeats the date", "gives your age", "tells a joke"], "A strong closing offers a solution."),
            ("open", "You are applying for the post of library assistant in a school in Kumba. Write the **second paragraph** (about 70 words) giving your qualifications and skills. Invent realistic details (no real names).",
             "I sat the GCE Ordinary Level in 2025 and obtained passes in six subjects, including English Language and Mathematics. For two years I was the library prefect at my school, where I arranged books, recorded the borrowing of textbooks and helped younger pupils to find what they needed. I can also use a computer to type and to keep simple lists.",
             ["1 mark: qualifications are stated.", "1 mark: concrete evidence of experience.", "1 mark: formal tone and correct grammar.", "1 mark: length 60–80 words and paragraph unity."]),
        ],
        sc=[("In an application letter, the subject line...", "states the post applied for", ["is optional slang", "gives your age", "is put at the end"], "e.g. APPLICATION FOR THE POST OF …"),
            ("Referees on a CV are...", "people who can speak for your character", ["your friends only", "your enemies", "football referees"], "Usually a teacher or an employer."),
            ("Which closing is suitable?", "I look forward to hearing from you.", ["Reply now.", "See you soon!", "Whatever you decide."], "Polite and formal."),
            ("A letter to the editor is addressed to...", "The Editor", ["The Reader", "Dear friend", "The Minister"], "It is sent to the newspaper."),
            ("Evidence in an application means...", "facts that show your skills", ["a long list of adjectives", "your photograph", "a family history"], "Facts convince better than compliments.")]))

    # ------------------------------------------------------------------ reports, notices, minutes
    build(ch, dict(
        slug="report-notice-minutes", title="Reports, notices and minutes", min=25,
        obj=["Lay out a report with headings.", "Write a clear notice with all the key information.", "Write the minutes of a meeting in the past tense."],
        notes=["The layout of reports, notices and minutes varies between textbooks; the version here is a common school pattern and should be checked against the GCE Board's guidance."],
        blocks=[
            ("text", "Practical writing is judged on **layout, accuracy of details and tone**. Reports and minutes are written in **formal, impersonal** English and in the **past tense** because they record something that has happened. A notice is short and gives **all the facts at a glance**."),
            ("fig", flow("Plan of a report", ["TITLE: REPORT ON … (to whom, by whom, date)", "1 Introduction: purpose and method", "2 Findings: under short headings", "3 Conclusion: what the findings show", "4 Recommendations: what should be done"], h=270),
             "Use numbered headings so the reader finds each part quickly.", "A vertical flow of five boxes: report title; introduction; findings; conclusion; recommendations."),
            ("key", "definition", "The report",
             "- **Heading:** *REPORT ON THE STATE OF THE SCHOOL LIBRARY*, then *To:*, *From:*, *Date:*.\n- **Introduction:** who asked for the report, the purpose, how you collected the information.\n- **Findings:** facts, in short paragraphs or under headings. No opinions here.\n- **Conclusion:** a short summary of what the facts show.\n- **Recommendations:** practical actions, each starting with a verb: *Buy…, Repair…*."),
            ("key", "definition", "The notice",
             "A notice has a **title in capitals** and answers **what, when, where, who, how** (and **contact**):\n- NOTICE / the event (e.g. CLEAN-UP DAY);\n- **Date, time and venue**;\n- **Who** may or must attend;\n- What to **bring** or do;\n- **Issued by** (name and position) and **date of issue**.\nKeep it under 60 words: no long sentences, no greetings."),
            ("key", "definition", "The minutes",
             "Minutes are the official record of a meeting.\n- **Heading:** name of the club, *Minutes of the meeting held on … at … in …*.\n- **Present / Absent with apologies.**\n- **Numbered items:** 1 Opening, 2 Minutes of the last meeting, 3 Matters discussed with the **decisions taken**, 4 Any other business, 5 Closure: *The meeting ended at …*\n- Signed by the secretary and the chairperson. Use the **past tense** and reported style: *It was agreed that …*."),
            ("key", "pieges", "Common mistakes",
             "- A notice without a **date, time or venue**.\n- Opinions in the findings: *The library is terrible.* → *Only 40 of the 200 books are in good condition.*\n- Present tense in the minutes: *The chairman says* → **said / stated**.\n- Writing the minutes as a story: keep them short and numbered.\n- Forgetting who writes and who signs."),
            ("ex", "Example 1: planning a notice", "The school clubs will clean the compound on Saturday 12 April from 8 a.m. to 10 a.m. Pupils bring brooms and gloves. The prefect, Ngu Ayuk, issues the notice.",
             ["Title: **NOTICE — CLEAN-UP DAY**. Then date, time, venue (the school compound).", "Who: all club members. Bring: brooms and gloves. Issued by: Ngu Ayuk, Senior Prefect, with the date."], "A short notice with title, date, time, place, who, what to bring, issuer."),
            ("ex", "Example 2: minutes in the right tense", "Rewrite for the minutes: *The treasurer says that the club has 15,000 FCFA.*",
             ["Minutes record the past, so the verb becomes the past: *said* or *reported*.", "The tense inside the clause moves back too: *had*."], "The treasurer reported that the club had 15,000 FCFA."),
        ],
        ex=[
            ("mcq", "In which order do the parts of a report usually appear?", "Introduction, findings, conclusion, recommendations", ["Findings, introduction, recommendations, conclusion", "Conclusion, introduction, findings, recommendations", "Recommendations, findings, introduction, conclusion"], "The report moves from purpose to facts to advice."),
            ("mcq", "Which item is MISSING from this notice? *NOTICE. There will be a football match. Come and watch.*", "date, time and venue", ["a title", "a joke", "the weather"], "A notice must say when and where."),
            ("tf", "Minutes are normally written in the past tense.", True, "They record what happened at the meeting."),
            ("mcq", "Which sentence belongs in the *Findings* of a report?", "Only 40 of the 200 books are in good condition.", ["The library must be rebuilt at once.", "I think the librarian is lazy.", "Everybody should read more."], "Findings state facts; opinions and advice go elsewhere."),
            ("mcq", "Which recommendation is correctly written?", "Buy fifty new textbooks before September.", ["The textbooks are old.", "Fifty new textbooks, why not.", "I feel sad about the library."], "A recommendation is an action that starts with a verb."),
            ("open", "The Science Club of your school will hold a quiz on Friday 20 June at 2 p.m. in Room 4. All Form 4 and Form 5 pupils may take part. Write the **notice** (maximum 60 words). You are the club secretary, Mbuh Ngwa.",
             "NOTICE\nSCIENCE CLUB QUIZ\nThe Science Club will hold a quiz on Friday 20 June at 2 p.m. in Room 4. All Form 4 and Form 5 pupils are invited to take part. Interested pupils should give their names to the secretary before Wednesday.\nMbuh Ngwa\nSecretary, Science Club\n10 June",
             ["1 mark: a title in capitals.", "1 mark: date, time and venue are correct.", "1 mark: who may attend; a contact or deadline.", "1 mark: issuer, position and date; under 60 words and correct English."]),
        ],
        sc=[("Recommendations in a report...", "say what should be done", ["repeat the title", "give opinions only", "come first"], "They are practical actions."),
            ("Minutes record...", "what was discussed and decided", ["the secretary's dreams", "the weather", "only the names"], "They are the official record."),
            ("A notice should be...", "short and complete", ["very long", "a story", "without a date"], "Give the key facts at a glance."),
            ("Which tone suits a report?", "formal and impersonal", ["slangy", "angry", "playful"], "Reports are objective."),
            ("Who usually signs the minutes?", "the secretary and the chairperson", ["the guests only", "the cleaner", "nobody"], "They certify the record.")]))

    # ------------------------------------------------------------------ speech and article
    build(ch, dict(
        slug="speech-article", title="Speeches and articles", min=24,
        obj=["Structure a speech for a school or community audience.", "Use persuasive devices: rhetorical questions, repetition, direct address.", "Write an article with a headline, introduction and sub-headings."],
        notes=["Titles of address (Honourable Guests, Mr Principal) are common school conventions; check local practice."],
        blocks=[
            ("text", "A **speech** is written to be **heard**; an **article** is written to be **read** in a magazine or newspaper. Both need a clear purpose, a known audience and a structure. Decide first: **Who** is listening or reading, **why**, and what should they **think or do** afterwards?"),
            ("fig", hflow("Plan of a speech", ["Greeting|audience", "Opening|a hook", "Body|2–3 points", "Close|call to act"]),
             "Every speech follows these four steps.", "Four boxes joined by arrows: greeting the audience, opening with a hook, body with two or three points, closing with a call to action."),
            ("key", "methode", "Writing a speech",
             "1. **Greet the audience** by title: *Mr Principal, teachers, my dear colleagues,*\n2. **Open with a hook:** a question, a surprising fact or a short story.\n3. **Body:** two or three points, each with one example. Use short, clear sentences.\n4. **Close** with a call to action and thanks: *Let us… Thank you.*\nDevices: **rhetorical question** (*Who will clean our town if we do not?*), **repetition** (*We can… we must… we will…*), **direct address** (*you*), **the rule of three**."),
            ("key", "methode", "Writing an article",
             "- **Headline:** short and catchy (*Plastic Bags: A Silent Threat to Our Gutters*).\n- **Byline:** *By Ayuk Nkeng, Form 5*.\n- **Introduction:** a hook plus the topic.\n- **Body:** short paragraphs, sometimes with **sub-headings**; facts, examples, a quotation from someone.\n- **Conclusion:** a final thought or a recommendation.\n- Tone: lively and clear, but still correct English."),
            ("key", "pieges", "Common mistakes",
             "- Beginning a speech with *Good morning* only and no title of address.\n- A speech full of long, complicated sentences that are hard to follow aloud.\n- An article without a headline, or with a headline that is a whole sentence.\n- Copying a speech into a letter layout (no *Dear Sir*, no *Yours faithfully*).\n- No clear point of view: say **what you want** the audience to think."),
            ("ex", "Example 1: an opening", "You must speak at the prize-giving about the importance of reading. Write the opening (about 40 words).",
             ["Greet the audience by title and thank them for being present.", "Add a hook: a question or a surprising fact. Example: a rhetorical question about what we would know without books."], "Mr Principal, honoured guests, fellow pupils: good afternoon. Tell me, how many of the things you know today did you learn from a book? Today I want to show you why reading is the best friend of every student."),
            ("ex", "Example 2: a headline", "Choose the better headline for an article on cleaning a market: (a) The people of the market in our town have decided on Saturday to clean it (b) Clean Market, Healthy Town.",
             ["A headline is short and catchy, not a full sentence.", "(b) is short, rhythmic and states the idea."], "(b)"),
        ],
        ex=[
            ("mcq", "Which is the best way to start a speech?", "Mr Principal, teachers and fellow pupils, good morning.", ["Hello guys.", "I don't have anything to say.", "Dear Sir or Madam,"], "Greet the audience by title."),
            ("mcq", "*Who will clean our town if we do not?* is a...", "rhetorical question", ["command", "fact", "quotation"], "It is asked for effect, not for an answer."),
            ("tf", "An article should have a headline.", True, "A headline attracts the reader and gives the topic."),
            ("mcq", "Which closing is best for a speech about road safety?", "Let us all drive carefully and walk safely. Thank you.", ["The end.", "Yours faithfully, Ngu", "I have finished talking."], "A call to action and thanks."),
            ("mcq", "Which headline is most suitable for a school magazine article about water?", "Every Drop Counts", ["Water is a liquid which people drink, wash with and cook with in all parts of Cameroon", "The water", "Dear Editor"], "Short and catchy."),
            ("open", "Write the **introduction** (about 60 words) of a speech to your classmates persuading them to keep the school compound clean. Use one rhetorical question and direct address.",
             "Good morning, Mr Principal, teachers and dear classmates. Have you ever walked across our compound after the lunch break and wondered where all this rubbish comes from? You and I made it. Today I want to persuade you that a clean school is a happier, healthier school, and that every one of us can make it so.",
             ["1 mark: correct greeting by title.", "1 mark: a rhetorical question.", "1 mark: direct address (you / we).", "1 mark: the aim of the speech is clear; length about 60 words."]),
        ],
        sc=[("A rhetorical question...", "does not need an answer", ["needs a written answer", "is always a lie", "is a command"], "It is used to make the audience think."),
            ("A byline shows...", "the name of the writer", ["the date", "the price", "the title"], "By Ayuk Nkeng."),
            ("Which belongs only in a speech?", "Greeting the audience", ["A byline", "Sub-headings", "A headline"], "Speeches begin with a greeting."),
            ("Repetition in speeches is used to...", "give emphasis", ["fill the time", "confuse the audience", "make it formal"], "It makes the idea memorable."),
            ("The best length for sentences in a speech is...", "short and clear", ["very long", "one word", "ten lines"], "The audience hears them only once.")]))

    # ------------------------------------------------------------------ narrative / descriptive / dialogue
    build(ch, dict(
        slug="narrative-descriptive", title="Narrative and descriptive essays, and dialogue", min=26,
        obj=["Plan a story with the story mountain.", "Describe a place using the five senses and figurative language.", "Write and punctuate dialogue naturally."],
        pre=["gceol-english-punctuation"],
        notes=["The story-mountain model is a common teaching tool, not an official GCE requirement."],
        blocks=[
            ("text", "A **narrative essay** tells a story; a **descriptive essay** paints a picture in words; **dialogue** brings characters alive with their own words. Examiners reward **ideas, organisation, vocabulary and accuracy**. Stay within the title and choose a limited setting."),
            ("fig", mountain(), "Plan your story along the mountain: the climax is the most exciting moment.", "A mountain shape: a rising line labelled opening and build-up, a peak labelled climax, then a falling line labelled falling action and ending."),
            ("key", "methode", "Narrative: the five steps",
             "1. **Opening:** who, where, when (one short paragraph).\n2. **Build-up:** the problem begins; tension grows.\n3. **Climax:** the most exciting or critical moment.\n4. **Falling action:** the effects of the climax.\n5. **Ending:** how things are resolved, and often a lesson or a feeling.\nUse the **past tense**; keep one point of view; use a **title** that fits."),
            ("key", "methode", "Description and « show, don't tell »",
             "- Use the **five senses**: sight, sound, smell, taste, touch.\n- Choose **precise words**: *a rusty tin roof*, not *a bad roof*.\n- Use **figurative language** sparingly: *the market hummed like a hive*.\n- **Show** instead of **telling**: not *Ngum was scared* but *Ngum's hands shook and he could not swallow*.\n- Follow an **order**: near to far, or morning to night."),
            ("key", "retenir", "Dialogue",
             "- Each new speaker starts a **new line**.\n- Spoken words go inside quotation marks; the punctuation mark is inside.\n- Vary the speech verbs (*said, whispered, shouted, replied*) but do not overdo it.\n- Dialogue should sound **natural** and move the story forward.\n*\"Where have you been?\" asked Mama. \"At the river,\" replied Ngum quietly.*"),
            ("key", "pieges", "Common mistakes",
             "- A story that begins at birth and covers years: choose **one event**.\n- *Suddenly* in every sentence; an ending like *and I woke up*.\n- Telling the feeling instead of showing it.\n- Description that is only a **list** of objects.\n- Dialogue in one big paragraph with no punctuation."),
            ("ex", "Example 1: show, don't tell", "Improve: *Ngum was scared.* Rewrite in one sentence that shows the fear.",
             ["Choose physical signs: shaking, a dry throat, a fast heartbeat.", "Add the setting for atmosphere: the sound of thunder."], "Ngum's hands shook, his throat was dry and every roll of thunder made his heart beat faster."),
            ("ex", "Example 2: punctuating dialogue", "Punctuate: *where have you been asked Mama at the river replied Ngum quietly*",
             ["Two speakers means two lines.", "Quotation marks, capital letters, a question mark inside the first speech and a comma inside the second."], "\"Where have you been?\" asked Mama.\n\"At the river,\" replied Ngum quietly."),
        ],
        ex=[
            ("mcq", "In the story mountain, the most exciting moment is the...", "climax", ["opening", "ending", "build-up"], "The peak is the climax."),
            ("mcq", "Which sentence shows rather than tells?", "Her hands trembled as she opened the letter.", ["She was nervous.", "She felt very bad.", "It was a scary moment."], "The first gives a physical sign."),
            ("tf", "A narrative essay for an O Level paper should cover many years of a life.", False, "Choose one event: a short time period is easier to control."),
            ("mcq", "Which description uses the sense of smell?", "The air was heavy with the smell of roasted plantain.", ["The roof was rusty.", "Children shouted across the road.", "The cloth felt rough."], "Smell is one of the five senses."),
            ("mcq", "How should a new speaker's words be written?", "On a new line", ["In the same line", "Without quotation marks", "In capital letters"], "Each new speaker starts a new line."),
            ("open", "Write the **opening paragraph** (about 80 words) of a descriptive essay entitled \"A market on Saturday morning\". Use at least three senses and one simile.",
             "By seven o'clock the market in Bafoussam is awake. Mountains of tomatoes glow red beside baskets of green peppers, and the smell of roasting plantain drifts over the stalls. Sellers call out their prices, and the crowd hums like a hive of bees. Under my sandals the ground is damp and sticky, and the sun is already warm on my neck. It is the noisiest, brightest place in the town.",
             ["1 mark: at least three senses.", "1 mark: a correct and fresh simile or metaphor.", "1 mark: precise, varied vocabulary.", "1 mark: well-organised paragraph, correct English, about 80 words."]),
        ],
        sc=[("The ending of a story is the...", "resolution", ["climax", "opening", "title"], "Problems are solved at the ending."),
            ("A simile uses...", "like or as", ["because", "although", "unless"], "*Brave as a lion*."),
            ("« Show, don't tell » means...", "use details instead of naming the feeling", ["write very little", "tell everything", "use only dialogue"], "Let the reader infer the emotion."),
            ("In which tense is a story normally told?", "the past", ["the future", "the passive only", "the present perfect only"], "Narratives look back."),
            ("What should be avoided in a narrative?", "ending with « and then I woke up »", ["a clear climax", "a fitting title", "use of senses"], "It is a weak, overused ending.")]))
    return ch
