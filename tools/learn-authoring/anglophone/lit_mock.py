from lit_prose import *


def mock(p):
    def lesson(slug):
        return next(l for c in p.chapters for l in c.lessons if l.slug == slug)
    pr, po, dr, ora, ctx, es = [lesson(s) for s in ["character-pov-style", "poetry-imagery-figures", "drama-elements", "african-oral-literature", "context-questions", "essay-on-text"]]
    ext = ("Ayuk had rehearsed the speech all week in front of the cracked bathroom mirror. Now, standing before the whole school, the words fled like startled birds. "
           "His palms were wet. In the front row the principal smiled encouragingly, and somebody at the back coughed. \"Good... good morning,\" he whispered, and the silence swallowed it.")
    A = [ctx.problem("Section A — Prose. Read the extract and answer.\n\n" + ext, [
        part_mcq("From which point of view is the extract told?", "third person (limited to Ayuk)", ["first person", "omniscient, entering every mind", "second person"], "He / his: the narrator follows Ayuk."),
        part_mcq("*The words fled like startled birds.* This is a...", "simile", ["metaphor", "irony", "alliteration"], "It uses *like*."),
        part_mcq("Which detail shows Ayuk's nervousness through his body?", "His palms were wet.", ["The principal smiled.", "Somebody coughed.", "He had rehearsed all week."], "Wet palms are a physical sign of fear."),
        part_mcq("What does *the silence swallowed it* suggest?", "His voice was too weak to be heard.", ["The audience was cheering.", "The hall was empty.", "He spoke loudly."], "Silence overcomes his whisper."),
        part_mcq("Which element of prose is the school hall mainly?", "setting", ["theme", "plot", "narrator"], "It is the place of the action.")], mock=True, tier="examen")]
    poem = ("The old drum sleeps behind the chief's house, its skin cracked like a dry river bed. Children run past without a glance. "
            "But on moonlit nights the elders whisper that it dreams of festivals, and the ground trembles a little in answer.")
    B_ = [po.problem("Section B — Poetry (unseen). Read the poem (line breaks omitted) and answer.\n\n" + poem, [
        part_mcq("*Its skin cracked like a dry river bed* is a...", "simile", ["metaphor", "oxymoron", "refrain"], "It uses *like*."),
        part_mcq("Which verbs are examples of personification?", "sleeps and dreams", ["run and whisper", "cracked and dry", "glance and answer"], "A drum cannot sleep or dream."),
        part_mcq("The mood of the poem is mainly...", "nostalgic with a touch of hope", ["angry", "cheerful and noisy", "frightened"], "It remembers the past and ends on a hopeful note."),
        part_mcq("The form of the poem is...", "free verse", ["a sonnet", "rhyming couplets", "a ballad"], "There is no regular rhyme or rhythm."),
        part_mcq("The main theme is...", "an old tradition is neglected but not dead", ["the price of drums", "a child's game", "hunger"], "The ground trembles in answer: the tradition lives on.")], mock=True, tier="examen")]
    scene = ("(A kitchen at night. ADA sits at the table with a school report. Her FATHER enters, tired.)\nFATHER: (gently) Still awake?\nADA: (hiding the paper) Just reading.\n"
             "FATHER: Reading... upside down? (He takes the report, reads it, and says nothing for a long time.)\nADA: Papa, I can explain.\nFATHER: (quietly) You do not need to. Your marks explain themselves.")
    assert len(scene) < 560, len(scene)
    C = [dr.problem("Section C — Drama. Read the scene and answer.\n\n" + scene, [
        part_mcq("What does the direction *(hiding the paper)* tell us about Ada?", "She is afraid or ashamed.", ["She is writing a letter.", "She is angry.", "She is cheerful."], "She does not want her father to see the report."),
        part_mcq("The father's long silence mainly creates...", "tension", ["comic relief", "a rhyme", "a refrain"], "The audience waits for his reaction."),
        part_open("(2 marks) Describe the father's attitude to Ada, using the text.", "He is gentle and calm, not angry: he speaks *gently* and *quietly*. But his last sentence is disappointed and severe, because her marks show the truth without any need for words.", ["1 mark: gentle, quiet, disappointed (attitude).", "1 mark: evidence from a stage direction or line."])], mock=True, tier="examen")]
    D = [
        es.open("Section D — Essay skills. Using the extract in Section A, write ONE PEEL paragraph (about 70 words) on: *How does the writer show Ayuk's fear?*",
                "The writer shows Ayuk's fear through his body and the audience's silence. The simile *the words fled like startled birds* suggests that he cannot control what he wants to say, and *His palms were wet* shows physical fear. The silence that *swallowed* his whisper makes him seem small. So the reader feels sympathy for him.",
                ["1 mark: a clear point.", "1 mark: a short quotation.", "1 mark: explanation of the effect.", "1 mark: link to the question and correct English."], mock=True, tier="examen", points=4),
        ora.match("Section D — Oral literature. Match each form with its description.", [("proverb", "a short wise saying"), ("folktale", "a story told aloud"), ("praise song", "words that honour a person")], "Forms differ in length and purpose.", mock=True, tier="examen", points=2),
    ]
    p.mock("skills", "GCE O Level mock — Literature in English: reading skills", 90,
           "Answer ALL the questions. Marks are shown for each section (total 20). All extracts are original CastBridge texts. This mock tests transferable skills only: the official set texts of the Cameroon GCE Board are not included, and the format of the paper must be checked against the official texts.",
           [("Section A — Prose extract (5 marks)", A), ("Section B — Unseen poem (5 marks)", B_), ("Section C — Drama scene (4 marks)", C), ("Section D — Essay skills and oral literature (6 marks)", D)])
