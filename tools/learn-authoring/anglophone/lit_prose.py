from common import *

SET = ("IMPORTANT: the official GCE Board set texts (novel, play, poetry anthology) change over time and are copyrighted; they are NOT summarised or quoted anywhere in this pack. "
       "A teacher or literature expert must add the lessons on the official set texts. All extracts and poems here are original, written for CastBridge.")


def B(ch, **s):
    s["notes"] = [SET] + list(s.get("notes", []))
    return build(ch, s)


def plot_fig():
    it = [T(210, 22, "The plot diagram", 15, bold=True),
          LINE(20, 190, 120, 125, width=3, color="blue"), LINE(120, 125, 210, 50, width=3, color="blue"),
          LINE(210, 50, 300, 125, width=3, color="orange"), LINE(300, 125, 400, 190, width=3, color="orange"),
          LINE(10, 190, 410, 190, width=2, color="grey"), CIRCLE(210, 50, 6, fill="red", stroke="red"),
          T(60, 212, "Exposition", 12, bold=True), T(135, 150, "Rising action", 12, bold=True, anchor="end"),
          T(222, 40, "Climax", 13, bold=True, color="red", anchor="start"),
          T(285, 150, "Falling action", 12, bold=True, anchor="start"), T(370, 212, "Resolution", 12, bold=True)]
    return shapes(it, 420, 225)


def prose(p):
    ch = p.chapter("ch-prose", "Prose fiction", "O Level Literature in English — the novel and the short story (transferable skills; official set texts to be added by a teacher)")

    B(ch, slug="plot-setting-theme", title="Prose: plot, setting and theme", min=26,
      obj=["Describe the five stages of a plot.", "Explain how setting creates mood and meaning.", "State the theme of a story in a full sentence, different from its subject."],
      blocks=[
          ("text", "**The Last Mango**\nNkem counted the coins again, though he knew the sum by heart: two hundred and fifty francs, not a franc more. Behind the school wall the mango tree swayed, heavy with fruit he could not reach. \"Next year,\" he told himself, in the voice his grandmother used when she wanted to be believed. The bell rang. He pushed the coins deep into his pocket, lifted his chin and walked into class as if he owned the whole world."),
          ("fig", plot_fig(), "Most stories follow this shape, though some skip or reorder stages.", "A mountain-shaped diagram. A rising line labelled exposition and rising action leads to the peak labelled climax, followed by a falling line labelled falling action and resolution."),
          ("key", "definition", "Plot",
           "The **plot** is the sequence of events and **why** they happen.\n- **Exposition:** introduces characters, setting and situation.\n- **Rising action:** a problem or conflict grows.\n- **Climax:** the turning point, the moment of greatest tension.\n- **Falling action:** the consequences.\n- **Resolution:** how the conflict ends.\nExtras: **flashback** (a scene from the past), **foreshadowing** (hints about what will come), **subplot** (a smaller second story)."),
          ("key", "definition", "Setting",
           "The **setting** is the **time, place and social background** of the story. It can create **mood** (a stormy night = danger), show **character** (a crowded one-room house) and even carry **meaning**. In the extract above the *school wall* and the *mango tree out of reach* suggest how far Nkem is from what he wants."),
          ("key", "definition", "Theme",
           "The **subject** is what the story is about (*poverty*). The **theme** is what the story says about it, written as a sentence: *Pride can help people to bear hardship.* To find the theme: ask what the main character **learns or fails to learn**, and what the **ending** suggests. A story usually has one main theme and several minor ones."),
          ("key", "pieges", "Common mistakes",
           "- **Retelling the story** when the question asks for analysis.\n- Giving a **one-word theme** (*love*) instead of a statement.\n- Mixing up **plot** (what happens) with **theme** (what it means).\n- Saying the setting is only the place: it includes **time and atmosphere**.\n- Claiming the writer's own life from the story without evidence."),
          ("ex", "Example 1: the plot stage", "In which stage of a plot would a writer first show the reader the village, the main character and her everyday life?",
           ["The stage that introduces characters and situation is the **exposition**.", "It comes before the conflict starts to build."], "the exposition"),
          ("ex", "Example 2: a theme sentence", "Write a theme sentence for *The Last Mango*.",
           ["Subject: poverty and pride. Evidence: he counts the little money but walks into class with his chin raised.", "Make it a full statement about people: *Pride can help us to face disappointment.*"], "Pride can help people to bear disappointment."),
      ],
      ex=[
          ("mcq", "The moment of greatest tension in a story is the...", "climax", ["exposition", "resolution", "setting"], "The climax is the turning point."),
          ("mcq", "Which is a THEME (not just a subject)?", "Greed destroys friendship.", ["Greed", "Friendship", "A story about two friends"], "A theme is a full statement about life."),
          ("tf", "The setting of a story includes only the place where events happen.", False, "It includes time, place and atmosphere."),
          ("mcq", "In *The Last Mango*, the tree whose fruit Nkem cannot reach most likely suggests...", "something he wants but cannot have", ["a school lesson", "a healthy diet", "the end of the term"], "A visible but unreachable fruit is a symbol of unfulfilled desire."),
          ("mcq", "A scene that shows an event from the past is a...", "flashback", ["foreshadowing", "climax", "subplot"], "A flashback interrupts the present to show the past."),
          ("open", "Read: *The rain fell for the third day. Ada sat in the dark kitchen and listened to the roof complain. In the next room her brother coughed, and she pressed the last piece of firewood to her chest.*\nExplain how the setting creates mood (3–4 sentences).",
           "The setting is a dark kitchen during three days of rain. The rain, the dark and the complaining roof create a gloomy, anxious mood. The cough in the next room and the last piece of firewood add worry and show that the family is in need.",
           ["1 mark: setting identified (dark kitchen, rain, firewood).", "1 mark: mood named (gloomy, anxious, tense).", "1 mark: a detail linked to the mood.", "1 mark: clear written explanation, not a retelling."]),
      ],
      sc=[("The stage that introduces the characters is the...", "exposition", ["climax", "resolution", "flashback"], "Exposition."),
          ("The resolution is...", "where the conflict ends", ["the beginning", "the title", "the climax"], "Conflict ends."),
          ("Hints about future events are called...", "foreshadowing", ["flashback", "irony", "dialogue"], "They prepare the reader."),
          ("A good theme sentence is...", "a statement about life", ["one word", "a title", "a character's name"], "e.g. Courage grows from fear."),
          ("Setting includes...", "time, place and atmosphere", ["the narrator's age", "only rooms", "only towns"], "All three parts.")])

    B(ch, slug="character-pov-style", title="Prose: character, point of view and style", min=26,
      obj=["Describe how a writer reveals character.", "Identify first-person, third-person limited and omniscient narration.", "Comment on style: diction, sentence length and tone."],
      blocks=[
          ("text", "**The Lamp**\nI did not tell Mama about the broken lamp. I told myself it was kindness: why spoil her evening? But when she lit the candle and looked at the glass on the floor, she only sighed, and I knew that she had known all along."),
          ("fig", tree("Point of view", "Narrator", [("first person", "I, we"), ("third limited", "one mind"), ("omniscient", "all minds")], h=185),
           "Ask who is telling the story, and how much he or she knows.", "A diagram with the word Narrator and three arrows to first person I and we, third limited one mind, and omniscient all minds."),
          ("key", "definition", "How writers reveal character",
           "1. What the character **says** (dialogue).\n2. What the character **does** (actions).\n3. What the character **thinks or feels** (thoughts).\n4. What **others say** about the character.\n5. What the **narrator tells** us directly.\nThe types: **protagonist** (main character), **antagonist** (opposes), **round** (complex, changes) and **flat** (simple, one trait)."),
          ("key", "definition", "Point of view",
           "- **First person** (*I*): we see only what the narrator sees and the narrator may be **unreliable**.\n- **Third person limited**: *he / she*, but we know the thoughts of **one** character.\n- **Omniscient** (all-knowing): the narrator knows every character's thoughts and may comment.\nPoint of view affects **sympathy**: in *The Lamp* we understand the child's guilt from inside."),
          ("key", "definition", "Style",
           "**Style** is *how* the writer writes:\n- **Diction** (word choice: simple / formal / local);\n- **Sentence length** (short sentences = speed or tension; long ones = calm or flow);\n- **Imagery and figures of speech**;\n- **Tone** (the attitude: gentle, ironic, serious).\nWhen you comment on style, quote a few words and say **what effect they create**."),
          ("key", "pieges", "Common mistakes",
           "- Saying a character is *good* or *bad* without evidence.\n- Confusing the **narrator** with the **author**.\n- Describing style with vague words (*nice language*).\n- Forgetting that a first-person narrator may not tell the whole truth.\n- Quoting a long passage without any comment."),
          ("ex", "Example 1: reading character", "What does *I told myself it was kindness* tell us about the narrator in *The Lamp*?",
           ["The narrator **told himself** it was kindness: this suggests he is making an excuse.", "Conclusion: he feels guilty and is not completely honest with himself."], "He feels guilty and excuses himself."),
          ("ex", "Example 2: identifying point of view", "Which point of view is used in *The Lamp*?",
           ["The pronoun is **I**: the narrator takes part in the story.", "So it is a first-person narration."], "first person"),
      ],
      ex=[
          ("mcq", "A narrator who says *I* and is part of the story is using the...", "first person", ["third person", "omniscient view", "second person"], "I = first person."),
          ("mcq", "A character who is complex and develops during the story is...", "round", ["flat", "minor", "an antagonist"], "Round characters change."),
          ("tf", "The narrator and the author are always the same person.", False, "The narrator is a voice created by the author."),
          ("mcq", "Which is an example of revealing character through action?", "He gave his only loaf to the stranger.", ["He was very kind.", "People said he was good.", "She thought he was kind."], "We infer kindness from what he does."),
          ("mcq", "A series of very short sentences usually creates...", "speed or tension", ["calm", "humour", "formality"], "Short sentences quicken the pace."),
          ("open", "Read: *\"I am fine,\" said Ada, pulling her sleeve over her bruised arm. She would not meet her teacher's eyes.*\nWhat do we learn about Ada? Explain in 3–4 sentences, using the words of the text.",
           "Ada is hiding something. She says she is *fine* but she *pulls her sleeve over her bruised arm* and will not meet her teacher's eyes, which suggests that she is hurt, afraid or ashamed and wants to hide it.",
           ["1 mark: a valid inference (hiding pain or fear).", "1 mark: a short quotation as evidence.", "1 mark: explains how the evidence supports the inference.", "1 mark: clear expression."]),
      ],
      sc=[("A narrator who knows everyone's thoughts is...", "omniscient", ["first person", "limited", "unreliable"], "All-knowing."),
          ("The main character is the...", "protagonist", ["antagonist", "narrator", "author"], "Protagonist."),
          ("Diction means...", "choice of words", ["the plot", "the setting", "the title"], "Style element."),
          ("A character with one dominant trait is...", "flat", ["round", "dynamic", "omniscient"], "Flat."),
          ("Which comment on style is best?", "The short sentences create tension.", ["The writing is nice.", "I liked it.", "It was interesting."], "It names the technique and effect.")])
    return ch
