from common import *


def mock(p):
    def lesson(slug):
        return next(l for c in p.chapters for l in c.lessons if l.slug == slug)
    ids = {}
    wf, cw, si, ce, sp, pt, ps, inf, nd = [lesson(s) for s in ["word-formation", "confused-words", "synonyms-idioms", "cameroon-english", "sentence-types", "passive-transform", "punctuation", "inference-figurative", "narrative-descriptive"]]
    A = [
        wf.mcq("*The old man is very ______; he often forgets where he puts his glasses.* (FORGET)", "forgetful", ["forgetting", "forgetly", "forgetless"], "An adjective is needed after *very*: forget + -ful.", mock=True, tier="examen"),
        cw.mcq("*The new timetable will ______ all the Form 5 classes.*", "affect", ["effect", "affection", "effective"], "A verb meaning *influence* is needed: affect.", mock=True, tier="examen"),
        si.mcq("*We visit our grandfather in the village once in a blue moon.* This means we visit him...", "very rarely", ["every month", "only at night", "every week"], "Once in a blue moon = very rarely.", mock=True, tier="examen"),
        ce.mcq("Which sentence is correct?", "The school bought new equipment.", ["The school bought new equipments.", "The school have bought new equipments.", "The school bought a new equipments."], "Equipment is uncountable and has no plural.", mock=True, tier="examen"),
        si.mcq("Choose the antonym of *scarce*.", "plentiful", ["rare", "expensive", "small"], "Scarce means hard to find; plentiful means easy to find in large amounts.", mock=True, tier="examen"),
    ]
    B = [
        sp.match("Match each sentence with its structure.", [("The bell rang.", "simple"), ("She sings and he dances.", "compound"), ("Because it rained, we left.", "complex")], "Count the clauses, then see whether they are equal or one depends on the other.", mock=True, tier="examen", points=2),
        pt.mcq("Passive of *The farmers harvested the cocoa*:", "The cocoa was harvested by the farmers.", ["The cocoa harvested the farmers.", "The cocoa is harvested by the farmers.", "The cocoa was harvesting by the farmers."], "Past simple active becomes was/were + past participle.", mock=True, tier="examen"),
        lesson("relative-conditionals").mcq("*If she ______ harder, she would have passed the examination.*", "had worked", ["worked", "would work", "has worked"], "Third conditional: past perfect in the if-clause.", mock=True, tier="examen"),
        ps.mcq("Choose the correctly punctuated sentence.", "Mr Ndi, our principal, arrived late.", ["Mr Ndi our principal, arrived late.", "Mr Ndi, our principal arrived, late.", "Mr Ndi our principal arrived late,"], "Extra information goes between commas.", mock=True, tier="examen"),
    ]
    passage = ("Every Thursday, Ayuk's grandmother walked to the stream to wash clothes. The path was steep and slippery, yet she never complained. "
               "\"The stream is my radio,\" she used to say. \"It tells me the news of the whole village.\" Women arrived with buckets and bundles, their laughter bouncing off the rocks. "
               "When a tap was built near the church, many stopped coming to the stream. Grandmother, however, still went, pretending to need the exercise. Ayuk now understood that she was searching for something a tap could not give.")
    assert len(passage) < 650, len(passage)
    C = [
        inf.problem("Read the passage and answer.\n\n" + passage, [
            part_mcq("On which day did grandmother go to the stream?", "Thursday", ["Sunday", "Monday", "Saturday"], "The first sentence says *Every Thursday*."),
            part_mcq("*The stream is my radio* is a...", "metaphor", ["simile", "hyperbole", "rhyme"], "It says one thing is another without like or as."),
            part_mcq("Why did she go on visiting the stream after the tap was built?", "She wanted company and news.", ["She had no money for water.", "The tap was broken.", "She disliked the church."], "The stream gave her the news and the company of the village women."),
            part_mcq("The tone of the passage is best described as...", "warm and nostalgic", ["angry and bitter", "frightening", "humorous and mocking"], "The writer remembers the grandmother with affection.")], mock=True, tier="examen")]
    D = [
        lesson("summary-technique").open("Using the passage about the grandmother, say in about 35 words why the stream was important to her.",
            "The stream gave her company and news: women met there, talked and laughed, so for her it was like a radio. She kept going even after the tap arrived.",
            ["1 mark: company or meeting other women.", "1 mark: news of the village (the radio idea).", "1 mark: own words and about 35 words, no copying."], mock=True, tier="examen", points=3),
        nd.open("Write the opening paragraph (about 50 words) of an article entitled *Why We Must Keep Our Stream Clean*. Include a headline-style hook.",
            "Have you ever drunk water that smelled of soap and rubbish? In many villages across Cameroon the stream is the only source of water, yet it is often treated like a dustbin. Today we must ask ourselves what we are leaving for our children.",
            ["1 mark: a hook (question or surprising fact).", "1 mark: the topic and the purpose are clear.", "1 mark: correct English and about 50 words."], mock=True, tier="examen", points=3),
    ]
    p.mock("language-2", "GCE O Level mock 2 — English Language: vocabulary, structure and comprehension skills", 90,
           "Answer ALL the questions. Marks are shown for each section (total 20). This CastBridge practice paper is simplified; its format must be checked against the official texts of the Cameroon GCE Board.",
           [("Section A — Vocabulary and usage (5 marks)", A), ("Section B — Sentence structure (5 marks)", B), ("Section C — Comprehension (4 marks)", C), ("Section D — Writing (6 marks)", D)])
