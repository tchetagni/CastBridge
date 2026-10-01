"""Literature in English (GCE O / A Level), English language notions, and ECM (éducation à la citoyenneté et à la morale, francophone).
Facts written by the assistant, all `review`. Set-text lists change from year to year: only authors and works that are widely taught are used."""
from .. import lycee
from ..facts_engine import FACTS, fq, pick, source
from .lycee_common import put_levels

source("ly-lit-en", "Literature in English, GCE Ordinary and Advanced Level", "programme scolaire", "Compare with the GCE Board literature syllabus and the current list of set texts; check publication years.")
source("ly-lang-en", "English Language, GCE Ordinary Level (and francophone Anglais class)", "programme scolaire", "Compare with the GCE Board English Language syllabus and the MINESEC English programmes.")
source("ly-ecm", "ECM, second cycle francophone (éducation à la citoyenneté et à la morale)", "programme scolaire", "Comparer avec le programme MINESEC d'ECM, la Constitution du 18 janvier 1996 et les textes internationaux cités.")

# ---------------------------------------------------------------------------------------------- Literature in English
WORKS = [  # (level, author, work, year)
    ("f5", "William Shakespeare", "Macbeth", 1606), ("f5", "William Shakespeare", "Romeo and Juliet", 1597), ("f5", "William Shakespeare", "Hamlet", 1603), ("f5", "William Shakespeare", "Othello", 1604),
    ("f5", "William Shakespeare", "The Tempest", 1611), ("f5", "William Shakespeare", "Julius Caesar", 1599), ("f5", "Chinua Achebe", "Things Fall Apart", 1958), ("f5", "Charles Dickens", "Oliver Twist", 1838),
    ("f5", "Charles Dickens", "Great Expectations", 1861), ("f5", "George Orwell", "Animal Farm", 1945), ("f5", "William Golding", "Lord of the Flies", 1954), ("f5", "Harper Lee", "To Kill a Mockingbird", 1960),
    ("f5", "Daniel Defoe", "Robinson Crusoe", 1719), ("f5", "Jonathan Swift", "Gulliver's Travels", 1726), ("f5", "Ngũgĩ wa Thiong'o", "Weep Not, Child", 1964),
    ("l6", "George Orwell", "Nineteen Eighty-Four", 1949), ("l6", "Jane Austen", "Pride and Prejudice", 1813), ("l6", "Charlotte Brontë", "Jane Eyre", 1847), ("l6", "Emily Brontë", "Wuthering Heights", 1847),
    ("l6", "Thomas Hardy", "Tess of the d'Urbervilles", 1891), ("l6", "Joseph Conrad", "Heart of Darkness", 1899), ("l6", "Mary Shelley", "Frankenstein", 1818), ("l6", "Oscar Wilde", "The Importance of Being Earnest", 1895),
    ("l6", "George Bernard Shaw", "Pygmalion", 1913), ("l6", "F. Scott Fitzgerald", "The Great Gatsby", 1925), ("l6", "Ernest Hemingway", "The Old Man and the Sea", 1952), ("l6", "Mark Twain", "Adventures of Huckleberry Finn", 1884),
    ("l6", "Arthur Miller", "The Crucible", 1953), ("l6", "Arthur Miller", "Death of a Salesman", 1949), ("l6", "Alan Paton", "Cry, the Beloved Country", 1948), ("l6", "Buchi Emecheta", "The Joys of Motherhood", 1979),
    ("u6", "Chimamanda Ngozi Adichie", "Half of a Yellow Sun", 2006), ("u6", "Chimamanda Ngozi Adichie", "Purple Hibiscus", 2003), ("u6", "Ayi Kwei Armah", "The Beautyful Ones Are Not Yet Born", 1968),
    ("u6", "Wole Soyinka", "Death and the King's Horseman", 1975), ("u6", "Maya Angelou", "I Know Why the Caged Bird Sings", 1969), ("u6", "Alice Walker", "The Color Purple", 1982),
    ("u6", "J. M. Coetzee", "Disgrace", 1999), ("u6", "Samuel Beckett", "Waiting for Godot", 1952), ("u6", "John Milton", "Paradise Lost", 1667), ("u6", "Geoffrey Chaucer", "The Canterbury Tales", 1400),
    ("u6", "Bole Butake", "Lake God", 1999), ("u6", "Kenjo Jumbam", "The White Man of God", 1980), ("u6", "Mbella Sonne Dipoko", "Because of Women", 1969),
]
AFRICAN = {"Chinua Achebe", "Ngũgĩ wa Thiong'o", "Alan Paton", "Buchi Emecheta", "Chimamanda Ngozi Adichie", "Ayi Kwei Armah", "Wole Soyinka", "J. M. Coetzee", "Bole Butake", "Kenjo Jumbam", "Mbella Sonne Dipoko"}
CAMEROON = {"Bole Butake", "Kenjo Jumbam", "Mbella Sonne Dipoko"}
for lvl, auth, work, year in WORKS:
    course = lycee.en(lvl, "lit")
    same = [w for w in WORKS if w[1] == auth]
    region = "CM" if auth in CAMEROON else ("AF" if auth in AFRICAN else "WORLD")
    others = sorted({w[1] for w in WORKS} - {auth})
    titles = sorted({w[2] for w in WORKS if w[1] != auth})
    d = {"f5": 2, "l6": 3, "u6": 3}[lvl]
    fq(course, "lit-author", f"Who wrote '{work}'?", auth, pick("la" + work, others, auth, 8), f"'{work}' was written by {auth}.", "ly-lit-en", region, "Literature", d)
    if len(same) == 1:
        fq(course, "lit-title", f"Which of these works was written by {auth}?", work, pick("lt" + auth, titles, work, 8), f"{auth} wrote '{work}'.", "ly-lit-en", region, "Literature", d + 1)

LIT = [
    ("f5", "What do we call a comparison that uses 'like' or 'as'?", "A simile", ["A metaphor", "Personification", "Irony", "Hyperbole"], "'As brave as a lion' is a simile.", 1),
    ("f5", "What do we call a comparison that says one thing IS another?", "A metaphor", ["A simile", "Alliteration", "Onomatopoeia", "Irony"], "'Time is a thief' is a metaphor.", 1),
    ("f5", "What is the repetition of the same initial consonant sound in nearby words called?", "Alliteration", ["Assonance", "Onomatopoeia", "Rhyme", "Irony"], "'Peter Piper picked a peck of pickled peppers'.", 2),
    ("f5", "What is the figure of speech in which words imitate the sound they describe?", "Onomatopoeia", ["Alliteration", "Hyperbole", "Simile", "Irony"], "Examples: buzz, hiss, bang.", 1),
    ("f5", "What is the figure of speech that gives human qualities to non-human things?", "Personification", ["Simile", "Irony", "Understatement", "Alliteration"], "'The wind whispered through the trees'.", 1),
    ("f5", "What is deliberate exaggeration for effect called?", "Hyperbole", ["Understatement", "Irony", "Paradox", "Oxymoron"], "'I've told you a million times.'", 1),
    ("f5", "How many lines does a sonnet have?", "14", ["10", "12", "16", "8"], "Shakespeare's sonnets end with a rhyming couplet.", 1),
    ("f5", "How many lines make up a couplet?", "2", ["3", "4", "6", "8"], "Two rhyming lines.", 1),
    ("f5", "What is the narrator called when the story is told with 'I'?", "A first-person narrator", ["A third-person narrator", "An omniscient narrator", "A second-person narrator", "An unreliable author"], "The narrator is a character in the story.", 1),
    ("f5", "What is the turning point of greatest tension in a story called?", "The climax", ["The exposition", "The denouement", "The setting", "The theme"], "After the climax comes the resolution.", 2),
    ("f5", "What do we call the ending of a story where the plot is resolved?", "The resolution (denouement)", ["The exposition", "The climax", "The prologue", "The rising action"], "Loose ends are tied up.", 2),
    ("f5", "What is the main character of a story called?", "The protagonist", ["The antagonist", "The narrator", "The chorus", "The foil"], "The antagonist opposes the protagonist.", 1),
    ("f5", "Who is the 'antagonist' in a story?", "The character or force that opposes the protagonist", ["The hero", "The narrator", "The author", "A minor friend of the hero"], "Villains are typical antagonists.", 2),
    ("f5", "Which type of play usually ends in the downfall of its hero?", "A tragedy", ["A comedy", "A farce", "A pantomime", "A satire"], "Macbeth and Hamlet are tragedies.", 1),
    ("f5", "Whom does Macbeth murder to become King of Scotland?", "King Duncan", ["Banquo", "Macduff", "Malcolm", "Lady Macbeth"], "Macbeth kills Duncan while he sleeps.", 2),
    ("f5", "Who is Okonkwo in Things Fall Apart?", "A proud Igbo warrior and leader of Umuofia", ["A white missionary", "A colonial district officer", "A village priestess", "A schoolteacher"], "He fears weakness above all.", 2),
    ("f5", "In Animal Farm, which animal leads the revolution and later becomes a dictator?", "Napoleon the pig", ["Boxer the horse", "Snowball the pig", "Squealer the pig", "Benjamin the donkey"], "Napoleon drives Snowball away.", 3),
    ("l6", "Which Shakespeare play includes the line 'To be, or not to be'?", "Hamlet", ["Macbeth", "Othello", "King Lear", "Romeo and Juliet"], "It opens Hamlet's famous soliloquy.", 2),
    ("l6", "In Shakespearean tragedy, what is 'hamartia'?", "The hero's tragic flaw or error", ["The final speech", "A comic scene", "The audience's relief", "The chorus"], "The term comes from Aristotle's Poetics.", 4),
    ("l6", "What, according to Aristotle, is 'catharsis'?", "The purging of pity and fear felt by the audience of a tragedy", ["The hero's death", "A comic relief scene", "The first act", "A soliloquy"], "Aristotle, Poetics.", 4),
    ("l6", "What is a soliloquy?", "A speech in which a character speaks their thoughts aloud alone on stage", ["A dialogue between two characters", "A speech to the audience by a narrator", "A song in a play", "A stage direction"], "Hamlet's 'To be or not to be' is a soliloquy.", 3),
    ("l6", "What is dramatic irony?", "When the audience knows something that a character does not", ["When a character says the opposite of what they mean", "When a story ends happily", "When the narrator lies", "When events are very unlikely"], "Examples occur in Romeo and Juliet.", 3),
    ("l6", "What is the usual line length of iambic pentameter in syllables?", "10", ["5", "8", "12", "14"], "Five iambs, each an unstressed then stressed syllable.", 3),
    ("l6", "What is the rhyme scheme of the three quatrains of a Shakespearean sonnet?", "ABAB CDCD EFEF", ["ABBA ABBA CDC", "AABB CCDD EEFF", "ABCD ABCD EFGH", "ABAB BCBC CDCD"], "It ends with a couplet GG.", 4),
    ("l6", "What is a motif in literature?", "A recurring element, image or idea that supports the theme", ["The title of a work", "The last line of a poem", "The author's biography", "A type of rhyme"], "Blood in Macbeth is a recurring motif.", 3),
    ("l6", "Which writer, awarded the Nobel Prize for Literature in 1986, wrote 'Death and the King's Horseman'?", "Wole Soyinka", ["Chinua Achebe", "Ngũgĩ wa Thiong'o", "Nadine Gordimer", "J. M. Coetzee"], "He was the first African Nobel laureate in literature.", 3),
    ("l6", "Which author's novel Things Fall Apart was published in 1958?", "Chinua Achebe", ["Wole Soyinka", "Ngũgĩ wa Thiong'o", "Ayi Kwei Armah", "Cyprian Ekwensi"], "It is one of the most widely read African novels.", 2),
    ("u6", "What is an unreliable narrator?", "A narrator whose account the reader cannot fully trust", ["A narrator who is absent", "A narrator who speaks in the third person", "A narrator who is the author", "A narrator who changes names"], "It creates ambiguity and irony.", 4),
    ("u6", "What is the Bildungsroman?", "A novel about a character's growth from youth to maturity", ["A novel about war", "A comic novel", "A detective novel", "A novel in letters"], "Jane Eyre and Great Expectations are examples.", 4),
    ("u6", "What is an epistolary novel?", "A novel told through letters or documents", ["A novel written in verse", "A novel with no characters", "A novel about travel", "A novel with a moral"], "Mariama Bâ's So Long a Letter (in French) is an example.", 4),
    ("u6", "What is satire?", "The use of humour and exaggeration to criticise people or society", ["A serious poem on death", "A story with a happy ending", "A form of rhyme", "A biography"], "Animal Farm and Gulliver's Travels are satires.", 3),
    ("u6", "Which movement in African literature, led by writers such as Achebe, responded to colonial portrayals of Africa?", "Postcolonial literature", ["Romanticism", "Modernism only", "Naturalism", "Surrealism"], "It writes back against colonial narratives.", 4),
    ("u6", "What does 'Things Fall Apart' take its title from?", "A poem by W. B. Yeats ('The Second Coming')", ["A Shakespeare sonnet", "An Igbo proverb alone", "A Bible verse", "A poem by Wordsworth"], "'Things fall apart; the centre cannot hold.'", 4),
]
put_levels(lycee.en, "lit", "lit-notions", LIT, "ly-lit-en", "Literature", "WORLD")

# ---------------------------------------------------------------------------------------------- English language notions
LANG = [
    ("f5", "Which word is a noun in the sentence 'The quick dog barked loudly'?", "dog", ["quick", "barked", "loudly", "The"], "A noun names a person, place or thing.", 1),
    ("f5", "Which word is an adverb in 'She sang beautifully'?", "beautifully", ["sang", "She", "in", "song"], "Adverbs describe verbs, adjectives or other adverbs.", 1),
    ("f5", "Which word is a verb in 'Birds fly south in winter'?", "fly", ["Birds", "south", "winter", "in"], "A verb expresses an action or state.", 1),
    ("f5", "What is the opposite of 'generous'?", "Mean (stingy)", ["Kind", "Rich", "Careful", "Honest"], "A generous person gives freely.", 2),
    ("f5", "What does the prefix 'un-' usually mean?", "Not", ["Again", "Before", "Over", "Too much"], "unhappy = not happy.", 1),
    ("f5", "What does the prefix 're-' usually mean?", "Again", ["Not", "Against", "Before", "Under"], "rewrite = write again.", 1),
    ("f5", "Which pair of words sound the same but have different spellings and meanings?", "Their and there", ["Light and night", "Read and write", "Big and small", "Quick and fast"], "They are homophones.", 2),
    ("f5", "Which word completes the sentence 'The dog wagged ___ tail'?", "its", ["it's", "its'", "it is", "they're"], "'its' is the possessive; 'it's' means 'it is'.", 2),
    ("f5", "What punctuation mark ends a question?", "A question mark", ["A full stop", "A comma", "A colon", "An exclamation mark"], "It shows that the sentence asks something.", 1),
    ("f5", "Which word is a synonym of 'big'?", "Large", ["Tiny", "Narrow", "Short", "Thin"], "A synonym has a similar meaning.", 1),
    ("f5", "Which word is an antonym of 'ancient'?", "Modern", ["Old", "Historic", "Aged", "Primitive"], "An antonym has the opposite meaning.", 2),
    ("f5", "Which of these is a complete sentence?", "The teacher marked our books.", ["Because it was late.", "Running down the road.", "After the lesson.", "When the bell rang."], "A complete sentence has a subject and a verb and expresses a full idea.", 2),
    ("f5", "What is a paragraph?", "A group of sentences about one main idea", ["A single word", "A line of poetry", "A title", "A list of names"], "A new paragraph starts a new idea.", 1),
    ("f5", "Which tense is used in 'I am reading a book now'?", "Present continuous", ["Present simple", "Past simple", "Present perfect", "Future simple"], "am/is/are + verb-ing.", 2),
    ("f5", "Which word is a conjunction in 'I like tea but she likes coffee'?", "but", ["like", "tea", "she", "coffee"], "A conjunction joins words or clauses.", 2),
    ("l6", "What is the purpose of a topic sentence?", "To state the main idea of a paragraph", ["To end the essay", "To give a quotation", "To list sources", "To show the date"], "It usually comes first.", 3),
    ("l6", "Which type of essay tries to persuade the reader to accept a point of view?", "An argumentative (persuasive) essay", ["A narrative essay", "A descriptive essay", "A reflective diary", "An expository report"], "It gives reasons and evidence.", 3),
    ("l6", "Which of these is a formal way of beginning a letter to an unknown person?", "Dear Sir or Madam,", ["Hey there!", "Yo!", "Hi buddy,", "My dear friend,"], "Formal letters avoid slang.", 2),
    ("l6", "How should a formal letter that begins 'Dear Sir' normally end?", "Yours faithfully", ["Yours sincerely", "Love", "Best wishes", "See you"], "'Yours sincerely' is used when you know the person's name.", 3),
    ("l6", "How should a formal letter that begins 'Dear Mr Tabi' normally end?", "Yours sincerely", ["Yours faithfully", "Yours truly always", "Cheers", "Regards to all"], "Name known: 'Yours sincerely'.", 3),
    ("l6", "What does the idiom 'to break the ice' mean?", "To start a conversation in an awkward situation", ["To destroy something", "To cool a drink", "To end a friendship", "To be very cold"], "It eases tension at a first meeting.", 3),
    ("l6", "What does the idiom 'a piece of cake' mean?", "Something very easy", ["Something sweet", "A small gift", "A difficult problem", "A party"], "The exam was a piece of cake.", 2),
    ("l6", "What is a formal synonym of 'get' in 'get permission'?", "Obtain", ["Grab", "Snatch", "Keep", "Borrow"], "'Obtain' is more formal.", 3),
    ("u6", "What is the term for the writer's attitude towards the subject?", "Tone", ["Plot", "Setting", "Rhyme", "Metre"], "Tone can be serious, ironic, humorous, etc.", 3),
    ("u6", "What is an oxymoron?", "A phrase combining contradictory terms, such as 'deafening silence'", ["A long sentence", "A word that sounds like its meaning", "An exaggeration", "A question that needs no answer"], "It creates a striking effect.", 3),
    ("u6", "What is a rhetorical question?", "A question asked for effect and not to get an answer", ["A question that has no punctuation", "A question to the teacher", "A very difficult question", "A question that repeats"], "'Who knows?'", 3),
    ("u6", "What is the function of a thesis statement in an essay?", "It states the main argument the essay will defend", ["It gives the date", "It lists the references", "It tells a joke", "It describes the author"], "It usually appears in the introduction.", 4),
    ("u6", "Which voice is used in 'The letter was written by Mary'?", "The passive voice", ["The active voice", "The imperative", "The subjunctive", "The continuous"], "The subject receives the action.", 3),
]
put_levels(lycee.en, "lang", "lang-notions", LANG, "ly-lang-en", "English Language", "WORLD")
ANGL = [(lvl_fr, q, r, w, e, d) for (lvl_en, q, r, w, e, d), lvl_fr in zip(LANG, [{"f5": "2nde", "l6": "1re", "u6": "tle"}[x[0]] for x in LANG])]
_before = len(FACTS)
put_levels(lycee.fr, "angl", "angl-notions", ANGL, "ly-lang-en", "Anglais", "WORLD")
for _entry in FACTS[_before:]:                                            # English questions inside the francophone literature course
    _entry[2].lang = "en"

# ---------------------------------------------------------------------------------------------- ECM
ECM = [
    ("2nde", "Quel texte du 10 décembre 1948 proclame les droits fondamentaux de tous les êtres humains ?", "La Déclaration universelle des droits de l'homme", ["La Charte de l'ONU", "La Constitution du Cameroun", "Le Code civil", "Le traité de Versailles"], "Elle a été adoptée par l'Assemblée générale de l'ONU à Paris.", 1),
    ("2nde", "Quelle journée célèbre-t-on le 10 décembre dans le monde ?", "La Journée des droits de l'homme", ["La fête du Travail", "La fête de la Jeunesse", "La Journée de la femme", "La fête nationale"], "En mémoire de l'adoption de la Déclaration de 1948.", 2),
    ("2nde", "Comment appelle-t-on la règle commune obligatoire, édictée par l'État, dont le non-respect est sanctionné ?", "La loi", ["La coutume", "Le conseil", "La politesse", "La tradition"], "Elle s'impose à tous.", 1),
    ("2nde", "Quels sont les trois pouvoirs distingués par Montesquieu ?", "Législatif, exécutif et judiciaire", ["Politique, économique et social", "Central, régional et local", "Civil, militaire et religieux", "Royal, noble et populaire"], "Leur séparation protège la liberté.", 1),
    ("2nde", "Quel pouvoir vote les lois ?", "Le pouvoir législatif", ["Le pouvoir exécutif", "Le pouvoir judiciaire", "Le pouvoir religieux", "Le pouvoir militaire"], "Au Cameroun : l'Assemblée nationale et le Sénat.", 1),
    ("2nde", "Quel pouvoir fait appliquer les lois et gouverne ?", "Le pouvoir exécutif", ["Le pouvoir législatif", "Le pouvoir judiciaire", "Le pouvoir religieux", "Le pouvoir coutumier"], "Président de la République et gouvernement.", 1),
    ("2nde", "Quel pouvoir rend la justice ?", "Le pouvoir judiciaire", ["Le pouvoir législatif", "Le pouvoir exécutif", "Le pouvoir constituant", "Le pouvoir médiatique"], "Les tribunaux et les magistrats.", 1),
    ("2nde", "Quelle date célèbre-t-on comme fête nationale du Cameroun ?", "Le 20 mai", ["Le 1er janvier", "Le 11 février", "Le 1er octobre", "Le 8 mars"], "Fête de l'Unité nationale.", 1),
    ("2nde", "Comment appelle-t-on un droit reconnu à tous les êtres humains, sans distinction ?", "Un droit fondamental (droit de l'homme)", ["Un privilège", "Une faveur", "Une coutume", "Un monopole"], "Exemples : vie, liberté, égalité.", 2),
    ("2nde", "Quel est le contraire d'un droit, c'est-à-dire ce que l'on doit faire pour la société ?", "Un devoir", ["Un privilège", "Un loisir", "Une liberté", "Un salaire"], "Droits et devoirs vont de pair.", 1),
    ("2nde", "Quel organe de l'ONU compte cinq membres permanents avec droit de veto ?", "Le Conseil de sécurité", ["L'Assemblée générale", "Le Conseil économique et social", "La Cour internationale de justice", "Le Secrétariat"], "États-Unis, Russie, Chine, France, Royaume-Uni.", 3),
    ("1re", "Quelle date commémore-t-on le 11 février au Cameroun ?", "La fête de la Jeunesse", ["La fête nationale", "La fête du Travail", "La Journée de la femme", "La Journée de l'Afrique"], "Elle est dédiée à la jeunesse du pays.", 1),
    ("1re", "Quelles sont les deux langues officielles du Cameroun ?", "Le français et l'anglais", ["Le français et le bassa", "L'anglais et l'ewondo", "Le français et l'arabe", "L'anglais et le pidgin"], "La Constitution les déclare d'égale valeur.", 1),
    ("1re", "Dans quelle ville siège la présidence de la République du Cameroun ?", "Yaoundé", ["Douala", "Buea", "Bamenda", "Garoua"], "Yaoundé est la capitale politique.", 1),
    ("1re", "Comment appelle-t-on la loi fondamentale d'un État qui organise les pouvoirs et garantit les droits ?", "La Constitution", ["Le Code pénal", "Le règlement intérieur", "Le décret", "L'ordonnance"], "Elle est au sommet de la hiérarchie des normes.", 2),
    ("1re", "Quelle est la devise du Cameroun ?", "Paix - Travail - Patrie", ["Unité - Travail - Progrès", "Liberté - Égalité - Fraternité", "Dieu - Patrie - Roi", "Union - Discipline - Travail"], "Elle figure sur les armoiries.", 1),
    ("1re", "Quel est le titre de l'hymne national du Cameroun ?", "Ô Cameroun, berceau de nos ancêtres", ["La Marseillaise", "Allons enfants", "Le Chant du départ", "Que Dieu protège le Cameroun"], "Il est chanté dans les deux langues officielles.", 2),
    ("1re", "Quelles sont les couleurs du drapeau du Cameroun ?", "Vert, rouge, jaune avec une étoile jaune au centre", ["Bleu, blanc, rouge", "Vert, blanc, rouge", "Noir, rouge, or", "Rouge, jaune, vert à bandes horizontales sans étoile"], "Trois bandes verticales et une étoile dorée.", 1),
    ("1re", "Comment appelle-t-on le vote par lequel les citoyens désignent leurs représentants ?", "Une élection", ["Un référendum", "Un recensement", "Un plébiscite", "Un sondage"], "Le suffrage est universel, égal et secret.", 1),
    ("1re", "Quel vote permet aux citoyens de répondre par oui ou non à une question précise ?", "Le référendum", ["L'élection législative", "Le recensement", "Le sondage", "La pétition"], "Il engage directement le peuple sur un texte.", 2),
    ("1re", "Qu'est-ce que la corruption ?", "L'abus d'une fonction ou d'un pouvoir pour obtenir un avantage personnel", ["Un contrat de travail", "Une peine légère", "Une forme d'impôt", "Un cadeau d'anniversaire"], "Elle est punie par la loi.", 2),
    ("1re", "Quel principe signifie que tous les citoyens sont égaux devant la loi ?", "L'égalité devant la loi", ["La séparation des pouvoirs", "Le fédéralisme", "La décentralisation", "L'immunité"], "Principe de tout État de droit.", 2),
    ("tle", "Quelle Constitution actuellement en vigueur au Cameroun date du 18 janvier 1996 ?", "La loi n° 96/06 portant révision de la Constitution", ["La Constitution de 1960", "La Constitution de 1961", "La Constitution de 1972", "La Charte de 1990"], "Elle a modifié la Constitution de 1972.", 3),
    ("tle", "Quelle chambre du Parlement camerounais représente notamment les régions et a été mise en place après la Constitution de 1996 ?", "Le Sénat", ["L'Assemblée nationale", "Le Conseil constitutionnel", "La Cour suprême", "Le Conseil économique et social"], "Chaque région y est représentée.", 3),
    ("tle", "Comment appelle-t-on le partage de certains pouvoirs de l'État avec les collectivités locales (communes, régions) ?", "La décentralisation", ["La fédération", "La dictature", "La colonisation", "La centralisation"], "Les communes et régions s'administrent librement.", 3),
    ("tle", "Quelles sont les collectivités territoriales décentralisées du Cameroun ?", "Les régions et les communes", ["Les départements et les arrondissements", "Les lamidats et les chefferies", "Les provinces et les cantons", "Les villes et les villages"], "Telles que prévues par la Constitution de 1996.", 4),
    ("tle", "Quelle organisation panafricaine, créée en 2002, regroupe les États africains ?", "L'Union africaine", ["La CEMAC", "La CEDEAO", "L'ONU", "La Francophonie"], "Elle a remplacé l'OUA.", 2),
    ("tle", "Qu'est-ce que la citoyenneté ?", "L'appartenance à une communauté politique avec des droits et des devoirs", ["Le fait d'habiter en ville", "Le fait d'être majeur seulement", "Un titre honorifique", "Le fait de voter une fois"], "Le citoyen participe à la vie de la cité.", 2),
    ("tle", "Quelle valeur est au cœur de l'État de droit ?", "Le respect de la loi par tous, y compris les gouvernants", ["L'obéissance au plus fort", "La rapidité de décision", "L'unité d'opinion", "La richesse des dirigeants"], "Nul n'est au-dessus de la loi.", 3),
    ("tle", "Que signifie la laïcité ?", "La neutralité de l'État à l'égard des religions", ["L'interdiction de toute religion", "Le choix d'une religion d'État", "L'enseignement religieux obligatoire", "Le droit de ne payer aucun impôt"], "Le Cameroun est un État laïc.", 3),
    ("tle", "Quel texte de 1989 protège spécifiquement les droits de l'enfant ?", "La Convention relative aux droits de l'enfant", ["La Déclaration de 1948", "La Charte africaine des droits de l'homme", "La Convention de Genève", "Le Pacte de Varsovie"], "Adoptée par l'ONU en 1989.", 3),
    ("tle", "Quelle charte régionale de 1981 protège les droits de l'homme et des peuples en Afrique ?", "La Charte africaine des droits de l'homme et des peuples", ["La Charte de l'ONU", "La Charte du commerce africain", "La Charte de l'OUA sur la monnaie", "La Charte des Nations africaines unies"], "Adoptée à Nairobi en 1981, parfois appelée Charte de Banjul.", 4),
]
for lvl, q, r, w, e, d in ECM:
    region = "CM" if any(k in q + e for k in ("Cameroun", "camerounais", "Yaoundé")) else "WORLD"
    fq(lycee.fr(lvl, "ecm"), "ecm-facts", q, r, w, e, "ly-ecm", region, "Citoyenneté et ECM", d)
