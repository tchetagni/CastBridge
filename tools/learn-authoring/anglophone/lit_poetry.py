from lit_prose import *


def rhyme_fig():
    lines = ["Rain, rain, on the roof it drums,", "Tap-tap-tapping as it comes,", "Bending banana leaves to sing,", "Washing dust from everything.", "Streams awake and start to run,", "Mud-brown rivers chase the sun."]
    letters = ["A", "A", "B", "B", "C", "C"]
    cols_ = ["blue", "blue", "green", "green", "orange", "orange"]
    it = [T(210, 22, "Rhyme scheme: AABBCC", 15, bold=True)]
    for i, (l, a, c) in enumerate(zip(lines, letters, cols_)):
        y = 56 + i * 28
        it += [T(20, y, l, 14, anchor="start"), T(320, y, a, 15, bold=True, color=c)]
    for k, c in enumerate(["blue", "green", "orange"]):
        y1 = 44 + k * 56
        it += [LINE(300, y1, 300, y1 + 30, color=c, width=3)]
    return shapes(it, 400, 215)


def poetry(p):
    ch = p.chapter("ch-poetry", "Poetry", "O Level Literature in English — poetry: imagery, sound, form and tone (transferable skills; official set poems to be added by a teacher)")

    B(ch, slug="poetry-imagery-figures", title="Poetry: imagery and figures of speech", min=28,
      obj=["Identify imagery that appeals to the five senses.", "Name and explain simile, metaphor, personification, hyperbole, irony and symbol.", "Write a comment that names the device, quotes it and explains the effect."],
      blocks=[
          ("text", "**Dawn at the Market**\nThe sun climbs over the tin roofs\nlike a slow, orange fruit.\nWomen unpack the morning:\ntomatoes red as small alarms,\nplantains stacked like golden bricks.\nThe air is thick with smoke and bargaining,\nand the old scale, tired, creaks its one complaint.\nA child laughs; the whole street laughs with her."),
          ("key", "definition", "Imagery",
           "**Imagery** is language that makes the reader **see, hear, smell, taste or feel**. In *Dawn at the Market*: **sight** (*orange fruit, red tomatoes*), **sound** (*creaks, laughs*), **smell** (*smoke*). Imagery makes an experience vivid and often carries the poem's mood: here, warmth and busy life."),
          ("fig", cols("Figures of speech", ["Figure", "Example"], [["simile", "red as small alarms"], ["metaphor", "unpack the morning"], ["personification", "the scale creaks its complaint"], ["hyperbole", "the whole street laughs"]], size=12),
           "Each example comes from the poem above.", "A table of figures of speech. Simile: red as small alarms. Metaphor: unpack the morning. Personification: the scale creaks its complaint. Hyperbole: the whole street laughs."),
          ("key", "definition", "The main figures",
           "- **Simile:** compares using *like / as*.\n- **Metaphor:** says one thing *is* or acts as another, without *like / as*.\n- **Personification:** gives human feelings or actions to things.\n- **Hyperbole:** deliberate exaggeration.\n- **Irony:** a gap between what is said and what is meant (or between expectation and reality).\n- **Symbol:** a thing that stands for an idea (a *dove* for peace)."),
          ("key", "methode", "How to comment: P-Q-E",
           "1. **Point:** name the device or image.\n2. **Quote:** give the few words.\n3. **Effect:** say what it makes us see or feel and why the poet chose it.\nExample: *The simile « like a slow, orange fruit » compares the sun to something ripe and sweet, so the dawn seems warm and welcoming.*"),
          ("key", "pieges", "Common mistakes",
           "- Naming a device without any **effect**.\n- Confusing **simile** and **metaphor**.\n- Treating every adjective as a figure of speech.\n- Writing that the poet *uses imagery to make it interesting* (too vague): say **which** image and **what** it suggests.\n- Retelling the poem line by line."),
          ("ex", "Example 1: naming the figure", "Name the figure of speech in *the old scale, tired, creaks its one complaint*.",
           ["The scale (an object) is given a human state (*tired*) and action (*complaint*).", "Giving human qualities to things is personification."], "personification"),
          ("ex", "Example 2: a full comment", "Comment on *tomatoes red as small alarms*.",
           ["Point: a simile. Quote: *red as small alarms*.", "Effect: the colour is so bright that it attracts attention like an alarm: the market is lively and impossible to ignore."], "A simile comparing the tomatoes to alarms: their red colour is bright and striking, so the market feels lively."),
      ],
      ex=[
          ("mcq", "*The sun climbs over the tin roofs like a slow, orange fruit* is a...", "simile", ["metaphor", "irony", "symbol"], "It uses *like*."),
          ("mcq", "Which line contains an image of SOUND?", "The old scale creaks its one complaint.", ["Tomatoes red as small alarms.", "Plantains stacked like golden bricks.", "The sun climbs over the tin roofs."], "*Creaks* appeals to hearing."),
          ("tf", "*Time is a thief* (time steals our years) is a simile.", False, "It states that time *is* a thief: it is a metaphor."),
          ("mcq", "*The wind whispered through the maize.* This is...", "personification", ["hyperbole", "metaphor", "onomatopoeia only"], "The wind is given a human action."),
          ("mcq", "Which comment is best?", "The metaphor « unpack the morning » makes the day seem like a fresh load of goods being opened.", ["The poet uses a metaphor to make it more interesting.", "The poem is about a market.", "I like the word unpack."], "It names, quotes and explains the effect."),
          ("open", "Read: *The old bus coughed and shuddered up the hill, / its paint peeling like tired skin.*\nName two devices and explain the effect of one of them (4–5 sentences).",
           "The poem uses personification (*coughed and shuddered*) and a simile (*peeling like tired skin*). The personification makes the bus seem like an ill old person struggling up the hill, so we feel pity for it and sense how hard the climb is.",
           ["1 mark: personification named with a quotation.", "1 mark: simile named with a quotation.", "1 mark: the effect explained.", "1 mark: clear written comment, not a list."]),
      ],
      sc=[("*Red as small alarms* is a...", "simile", ["metaphor", "symbol", "irony"], "It uses *as*."),
          ("Personification means...", "giving human qualities to things", ["exaggeration", "comparing with like", "a rhyme"], "Human traits to objects."),
          ("Imagery appeals to...", "the senses", ["only sight", "the grammar", "the title"], "Seeing, hearing, smelling, tasting, touching."),
          ("A dove is a common symbol of...", "peace", ["hunger", "speed", "anger"], "A well-known symbol."),
          ("A good comment on a device always includes...", "its effect", ["the poet's age", "a summary", "the page number"], "Effect is what earns marks.")])

    B(ch, slug="poetry-sound-form", title="Poetry: sound, rhythm and form", min=28,
      obj=["Identify rhyme, rhyme scheme, alliteration, assonance and onomatopoeia.", "Describe rhythm and stanza form.", "Distinguish rhyming forms, free verse, ballad and sonnet in general terms."],
      notes=["A beat count is given by ear; scansion of the sample poem should be confirmed by a literature teacher."],
      blocks=[
          ("text", "**The Rain Song**\nRain, rain, on the roof it drums,\nTap-tap-tapping as it comes,\nBending banana leaves to sing,\nWashing dust from everything.\nStreams awake and start to run,\nMud-brown rivers chase the sun."),
          ("fig", rhyme_fig(), "Give each new end-sound a new letter: here the pairs of lines rhyme AA, BB, CC.", "A six-line poem with letters A A B B C C beside the lines. Lines one and two end in drums and comes, three and four in sing and everything, five and six in run and sun."),
          ("key", "definition", "Sound devices",
           "- **Rhyme:** words with the same ending sound (*drums – comes*). The **rhyme scheme** names the pattern with letters (AABBCC).\n- **Alliteration:** repeated first consonant sounds: **b**ending **b**anana.\n- **Assonance:** repeated vowel sounds inside words: *brown clouds drown the town*.\n- **Onomatopoeia:** words that imitate sounds: *tap-tap, creak, buzz*.\n- **Repetition and refrain:** a repeated word or line that stresses an idea."),
          ("key", "definition", "Rhythm and stanza",
           "- **Rhythm** is the pattern of stressed and unstressed syllables, the beat of the poem. *Rain, rain, on the roof it drums* has four strong beats and a quick, drumming movement that matches the subject.\n- A **stanza** is a group of lines (like a paragraph). A **couplet** has 2 lines, a **tercet** 3, a **quatrain** 4.\n- A **line break** shows where to pause; **enjambment** continues a sentence over the break."),
          ("key", "definition", "Common forms",
           "- **Free verse:** no fixed rhyme or regular rhythm.\n- **Ballad:** a story told in short rhyming stanzas, often with a refrain.\n- **Sonnet:** a poem of **14 lines** in a fixed rhyme pattern, usually on a single idea.\n- **Lyric:** a short poem expressing feelings.\n- **Elegy:** a poem of mourning. **Ode:** a poem of praise addressed to someone or something."),
          ("key", "pieges", "Common mistakes",
           "- Judging rhyme by **spelling** instead of sound (*tough / though* do not rhyme).\n- Listing the devices without saying **why** the poet uses them.\n- Believing that **all** poems must rhyme.\n- Confusing **rhyme** (end sounds) with **alliteration** (beginning sounds).\n- Saying the rhythm is *good* instead of describing it (fast, slow, steady, broken)."),
          ("ex", "Example 1: the rhyme scheme", "Give the rhyme scheme of: *The sun is high / the goats are slow / the dust is dry / the rivers low*.",
           ["Line 1 ends in *high* = A, line 2 in *slow* = B.", "Line 3 *dry* rhymes with *high* = A; line 4 *low* rhymes with *slow* = B."], "ABAB"),
          ("ex", "Example 2: sound and effect", "Comment on the sound of *Tap-tap-tapping as it comes*.",
           ["Device: onomatopoeia and repetition (*tap-tap-tapping*).", "Effect: we hear the rhythm of raindrops on the roof, so the poem seems to drum."], "Onomatopoeia and repetition imitate the sound of rain."),
      ],
      ex=[
          ("mcq", "*Bending banana leaves* uses...", "alliteration", ["rhyme", "onomatopoeia", "personification only"], "Repeated initial b sound."),
          ("mcq", "Which word is onomatopoeic?", "buzz", ["house", "green", "slowly"], "The word sounds like the noise."),
          ("tf", "*Tough* and *though* rhyme because they end with *ough*.", False, "Rhyme is about sound; they sound different."),
          ("mcq", "A poem of fourteen lines is generally called a...", "sonnet", ["ballad", "couplet", "ode"], "A sonnet has 14 lines."),
          ("mcq", "Give the rhyme scheme: *sea / sand / me / hand*.", "ABAB", ["AABB", "ABBA", "AAAA"], "sea–me = A; sand–hand = B."),
          ("open", "Read: *The drums beat, beat, beat. / The bare feet stamp and stamp. / Dust rises in the heat.*\nName two sound devices and describe their effect on the reader (4–5 sentences).",
           "The poem uses repetition (*beat, beat, beat; stamp and stamp*) and alliteration (*bare feet*, *beat*, *beat*). The repetition makes the line move like a drumbeat, so the reader can feel the pounding rhythm of the dance. The b sounds in *beat* and *bare* are strong and heavy, which adds energy.",
           ["1 mark: first device named with a quotation.", "1 mark: second device named with a quotation.", "1 mark: effect on rhythm or mood.", "1 mark: clear written explanation."]),
      ],
      sc=[("Words that imitate sounds are...", "onomatopoeic", ["symbolic", "ironic", "enjambed"], "e.g. *creak*."),
          ("A group of four lines is a...", "quatrain", ["couplet", "tercet", "ode"], "Four lines."),
          ("A poem with no fixed rhyme or rhythm is...", "free verse", ["a sonnet", "a ballad", "an ode"], "No fixed pattern."),
          ("Repeated beginning consonant sounds are...", "alliteration", ["assonance", "rhyme", "refrain"], "Same first sound."),
          ("An elegy is a poem of...", "mourning", ["praise", "war", "nonsense"], "Mourning.")])
    return ch
