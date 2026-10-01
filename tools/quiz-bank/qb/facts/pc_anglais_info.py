"""Primaire et collège : anglais (langue étrangère), informatique, ECM (compléments CM2 et 3e).
Faits écrits par l'assistant : tous `review` ; à relire par un enseignant (programmes MINEDUB / MINESEC)."""
from ..facts_engine import fq, pairs, pick, source

W, CM_ = "WORLD", "CM"
source("pca-en-voc", "Anglais, vocabulaire de base (CP à 3e)", "dictionnaire bilingue", "Comparer avec un dictionnaire bilingue anglais-français (Oxford, Collins, Larousse) et le programme d'anglais MINEDUB / MINESEC.")
source("pca-en-gram", "Anglais, grammaire et orthographe (CP à 3e)", "grammaire de référence", "Comparer avec une grammaire de référence (Oxford, Cambridge, Bescherelle anglais) et le programme d'anglais MINEDUB / MINESEC.")
source("pca-info-mat", "Informatique, matériel et logiciels (CE2 à 3e)", "programme scolaire", "Comparer avec le programme d'informatique MINEDUB / MINESEC et un manuel d'initiation à l'informatique.")
source("pca-info-net", "Informatique, Internet, sécurité et usages (CE2 à 3e)", "programme scolaire", "Comparer avec le programme d'informatique MINEDUB / MINESEC et les conseils de sécurité de l'ANTIC ou de Cybermalveillance.")
source("pca-info-algo", "Informatique, codage, tableur et algorithmique (CE2 à 3e)", "programme scolaire", "Comparer avec le programme d'informatique MINEDUB / MINESEC, la documentation Python et celle d'un tableur (LibreOffice Calc, Excel).")
source("pca-ecm-cm", "ECM, institutions et civisme du Cameroun (CM2 et 3e)", "texte officiel", "Comparer avec la Constitution du 18 janvier 1996 (loi n° 96/06), le Code électoral et le programme d'ECM MINEDUB / MINESEC.")
source("pca-ecm-mon", "ECM, droits de l'enfant, ONU, Union africaine et citoyenneté (CM2 et 3e)", "texte officiel", "Comparer avec la Convention relative aux droits de l'enfant (1989), la Charte des Nations unies, l'Acte constitutif de l'Union africaine et le programme d'ECM.")


def add(course, tpl, rows, src, cat, region=W):
    for q, right, wr, expl, d in rows:
        fq(course, tpl, q, right, wr, expl, src, region, cat, d)


def en(course, tpl, rows, diff=2, extra_a=(), extra_b=(), fwd="Comment dit-on « {a} » en anglais ?", rev="Que veut dire « {b} » en français ?", expl="En anglais, « {a} » se dit « {b} »."):
    pairs(course, tpl, rows, fwd, rev, region=W, cat="Anglais", src="pca-en-voc", diff=diff, extra_a=extra_a, extra_b=extra_b, expl=expl)


def verbs(course, tpl, rows, what, diff=3):
    """Verbes irréguliers : (infinitif, forme) ; what = « le prétérit » ou « le participe passé »."""
    pairs(course, tpl, rows, "Quel est %s du verbe anglais « {a} » ?" % what, "Quel verbe anglais a pour %s « {b} » ?" % what.replace("le ", "", 1),
          region=W, cat="Anglais", src="pca-en-gram", diff=diff, expl="« {a} » : %s est « {b} »." % what, rev_expl="« {b} » est %s du verbe « {a} »." % what)


def dpairs(course, tpl, rows, fwd, rev, src, cat, region=W, diff=2, expl="{a} : {low}."):
    """(a, b) avec b qui commence par une majuscule ; dans rev, {b} est repris en minuscule initiale."""
    col_a, col_b = [r[0] for r in rows], [r[1] for r in rows]
    for r in rows:
        a, b = r[0], r[1]
        d = r[2] if len(r) > 2 else diff
        low = b[0].lower() + b[1:]
        if fwd:
            fq(course, tpl + "-fwd", fwd.format(a=a), b, pick(tpl + a, col_b, b, 9), expl.format(a=a, low=low), src, region, cat, d)
        if rev:
            fq(course, tpl + "-rev", rev.format(b=low), a, pick(tpl + b, col_a, a, 9), expl.format(a=a, low=low), src, region, cat, d)


# ============================================================ ANGLAIS : CP
en("cp", "pca-cp-en-salut", [("bonjour", "hello"), ("au revoir", "goodbye"), ("merci", "thank you"), ("oui", "yes"), ("non", "no"), ("s'il te plaît", "please"), ("pardon (je m'excuse)", "sorry")], 1)
en("cp", "pca-cp-en-animaux", [("chat", "cat"), ("chien", "dog"), ("oiseau", "bird"), ("poisson", "fish"), ("vache", "cow"), ("cheval", "horse"), ("singe", "monkey")], 1)
en("cp", "pca-cp-en-couleurs", [("rouge", "red"), ("bleu", "blue"), ("vert", "green"), ("jaune", "yellow"), ("noir", "black"), ("blanc", "white")], 1,
   expl="La couleur « {a} » se dit « {b} » en anglais.")
en("cp", "pca-cp-en-nombres", [("1", "one"), ("2", "two"), ("3", "three"), ("4", "four"), ("5", "five")], 1,
   fwd="Comment dit-on le nombre « {a} » en anglais ?", rev="Quel nombre désigne le mot anglais « {b} » ?", expl="Le nombre {a} s'écrit « {b} » en anglais.")
en("cp", "pca-cp-en-objets", [("livre", "book"), ("crayon", "pencil"), ("stylo", "pen"), ("maison", "house"), ("pomme", "apple"), ("école", "school")], 1)
add("cp", "pca-cp-en-phrases", [
    ("Que veut dire « Good morning » ?", "Bonjour (le matin)", ["Bonne nuit", "Bon appétit", "Bon anniversaire", "Au revoir"], "On dit « Good morning » le matin, jusqu'à midi environ.", 1),
    ("Comment dit-on « Bonne nuit » en anglais ?", "Good night", ["Good morning", "Good afternoon", "Thank you", "Welcome"], "« Good night » se dit avant d'aller dormir.", 1),
    ("Comment dit-on « Je m'appelle Awa » en anglais ?", "My name is Awa", ["His name is Awa", "Your name is Awa", "Where is Awa", "I am five Awa"], "« My name is... » sert à se présenter.", 1),
    ("Que veut dire « What is your name » ?", "Comment t'appelles-tu ?", ["Quel âge as-tu ?", "Où habites-tu ?", "Comment vas-tu ?", "Qui est ton ami ?"], "On répond « My name is... ».", 1),
    ("Que veut dire « How are you » ?", "Comment vas-tu ?", ["Comment t'appelles-tu ?", "Quel âge as-tu ?", "Qui es-tu ?", "Où vas-tu ?"], "On peut répondre « I'm fine, thank you ».", 1),
    ("Que veut dire « Sit down » ?", "Assieds-toi", ["Lève-toi", "Écoute", "Viens ici", "Regarde le tableau"], "« Sit down » est un ordre en classe.", 1),
    ("Que peut-on répondre quand on te dit « Thank you » ?", "You're welcome", ["Good night", "Sorry", "Hello", "Goodbye"], "« You're welcome » veut dire « de rien ».", 2),
    ("Que dit-on pour s'excuser après avoir bousculé quelqu'un ?", "Sorry", ["Please", "Thank you", "Welcome", "Hello"], "« Sorry » veut dire « pardon ».", 1),
    ("Que dit-on pour demander poliment quelque chose ?", "Please", ["Sorry", "Goodbye", "Welcome", "Good night"], "« Please » veut dire « s'il te plaît ».", 1),
    ("Dans « I am Kofi », que veut dire « I » ?", "Je", ["Tu", "Il", "Nous", "Elle"], "« I » est le pronom de la 1re personne du singulier.", 2),
    ("Quel petit mot anglais sert à dire bonjour à un ami, comme « Hello » ?", "Hi", ["Bye", "Yes", "Please", "Sorry"], "« Hi » est une forme familière de « Hello ».", 1),
], "pca-en-gram", "Anglais")

# ============================================================ ANGLAIS : CE1
en("ce1", "pca-ce1-en-nombres", [("6", "six"), ("7", "seven"), ("8", "eight"), ("9", "nine"), ("10", "ten"), ("11", "eleven"), ("12", "twelve"), ("20", "twenty")], 2,
   fwd="Comment dit-on le nombre « {a} » en anglais ?", rev="Quel nombre désigne le mot anglais « {b} » ?", expl="Le nombre {a} s'écrit « {b} » en anglais.")
en("ce1", "pca-ce1-en-famille", [("mère", "mother"), ("père", "father"), ("frère", "brother"), ("sœur", "sister"), ("grand-mère", "grandmother"), ("grand-père", "grandfather"), ("fils", "son")], 1)
en("ce1", "pca-ce1-en-jours", [("lundi", "Monday"), ("mardi", "Tuesday"), ("mercredi", "Wednesday"), ("jeudi", "Thursday"), ("vendredi", "Friday"), ("samedi", "Saturday"), ("dimanche", "Sunday")], 2,
   expl="En anglais, les jours de la semaine s'écrivent avec une majuscule : « {b} ».")
en("ce1", "pca-ce1-en-corps", [("tête", "head"), ("main", "hand"), ("pied", "foot"), ("bras", "arm"), ("jambe", "leg"), ("nez", "nose"), ("bouche", "mouth"), ("œil", "eye"), ("oreille", "ear")], 2)
en("ce1", "pca-ce1-en-nourriture", [("pain", "bread"), ("eau", "water"), ("lait", "milk"), ("riz", "rice"), ("œuf", "egg"), ("mangue", "mango"), ("poulet", "chicken"), ("sucre", "sugar")], 2)
en("ce1", "pca-ce1-en-classe", [("chaise", "chair"), ("porte", "door"), ("fenêtre", "window"), ("règle", "ruler"), ("cahier", "notebook"), ("sac", "bag"), ("tableau noir", "blackboard")], 2)
add("ce1", "pca-ce1-en-phrases", [
    ("Combien de lettres compte l'alphabet anglais ?", "26", ["24", "25", "27", "28"], "L'alphabet anglais compte 26 lettres, comme l'alphabet français.", 2),
    ("Que veut dire « Stand up » ?", "Lève-toi", ["Assieds-toi", "Écris", "Ferme la porte", "Écoute"], "« Stand up » est le contraire de « Sit down ».", 1),
    ("Que veut dire « Open your book » ?", "Ouvre ton livre", ["Ferme ton livre", "Lis ton cahier", "Prends ton stylo", "Regarde le tableau"], "« Open » veut dire « ouvrir ».", 1),
    ("Que veut dire « Close the door » ?", "Ferme la porte", ["Ouvre la porte", "Ferme la fenêtre", "Ouvre la fenêtre", "Regarde la porte"], "« Close » veut dire « fermer ».", 1),
    ("Quel mot complète la phrase « I ___ a pupil » ?", "am", ["is", "are", "be"], "Avec « I », le verbe « to be » devient « am ».", 1),
    ("Quel mot complète la phrase « She ___ my sister » ?", "is", ["am", "are", "be"], "Avec « he », « she » et « it », on emploie « is ».", 1),
    ("Quel mot complète la phrase « They ___ happy » ?", "are", ["is", "am", "be"], "Avec « they », « we » et « you », on emploie « are ».", 1),
    ("Quel petit mot met-on devant « apple » pour dire « une pomme » ?", "an", ["a", "the", "some"], "On emploie « an » devant un mot qui commence par un son voyelle.", 2),
    ("Quel petit mot met-on devant « book » pour dire « un livre » ?", "a", ["an", "the", "some"], "On emploie « a » devant un mot qui commence par un son consonne.", 1),
    ("Comment dit-on « Je suis content » en anglais ?", "I am happy", ["I am hungry", "I am sad", "I am tired", "I am cold"], "« Happy » veut dire « content, heureux ».", 1),
    ("Comment dit-on « J'ai faim » en anglais ?", "I am hungry", ["I am happy", "I am tired", "I am thirsty", "I am angry"], "En anglais, on dit « je suis affamé » (I am hungry).", 2),
    ("Quel est le pluriel de « cat » ?", "cats", ["cates", "cat's", "cattes", "caties"], "En général, on ajoute un « s » au singulier.", 1),
    ("Que veut dire « Where is the cat » ?", "Où est le chat ?", ["Qui est le chat ?", "Quel est le chat ?", "Comment va le chat ?", "Quand vient le chat ?"], "« Where » veut dire « où ».", 2),
], "pca-en-gram", "Anglais")

# ============================================================ ANGLAIS : CE2
en("ce2", "pca-ce2-en-mois", [("janvier", "January"), ("février", "February"), ("mars", "March"), ("avril", "April"), ("mai", "May"), ("juin", "June"), ("juillet", "July"), ("août", "August"), ("septembre", "September"), ("octobre", "October"), ("novembre", "November"), ("décembre", "December")], 2,
   expl="En anglais, les mois s'écrivent avec une majuscule : « {b} ».")
en("ce2", "pca-ce2-en-couleurs", [("rose", "pink"), ("marron", "brown"), ("violet", "purple"), ("gris", "grey")], 2, extra_a=["rouge", "bleu", "vert", "jaune", "noir"], extra_b=["red", "blue", "green", "yellow", "black"],
   expl="La couleur « {a} » se dit « {b} » en anglais (« grey » s'écrit « gray » en anglais américain).")
en("ce2", "pca-ce2-en-animaux", [("chèvre", "goat"), ("mouton", "sheep"), ("cochon", "pig"), ("canard", "duck"), ("lapin", "rabbit"), ("serpent", "snake"), ("grenouille", "frog"), ("éléphant", "elephant")], 2)
en("ce2", "pca-ce2-en-habits", [("chemise", "shirt"), ("robe", "dress"), ("chaussures", "shoes"), ("chapeau", "hat"), ("pantalon", "trousers"), ("chaussette", "sock")], 2)
en("ce2", "pca-ce2-en-contraires", [("hot", "cold"), ("big", "small"), ("happy", "sad"), ("fast", "slow"), ("day", "night"), ("full", "empty"), ("rich", "poor"), ("up", "down"), ("good", "bad"), ("clean", "dirty"), ("early", "late"), ("buy", "sell")], 3,
   fwd="Quel est le contraire du mot anglais « {a} » ?", rev="Quel est le contraire du mot anglais « {b} » ?", expl="« {a} » et « {b} » sont des contraires.")
en("ce2", "pca-ce2-en-nombres", [("13", "thirteen"), ("14", "fourteen"), ("15", "fifteen"), ("16", "sixteen"), ("17", "seventeen"), ("18", "eighteen"), ("19", "nineteen"), ("30", "thirty"), ("50", "fifty"), ("100", "one hundred")], 3,
   fwd="Comment dit-on le nombre « {a} » en anglais ?", rev="Quel nombre désigne le mot anglais « {b} » ?", expl="Le nombre {a} s'écrit « {b} » en anglais.")
add("ce2", "pca-ce2-en-phrases", [
    ("Comment répondre à « How old are you ? » quand on a neuf ans ?", "I am nine", ["I am nice", "I have nine", "My name is nine", "I am nine years old of age"], "Pour dire son âge, on utilise « to be » : « I am nine ».", 2),
    ("Quel mot complète « This is ___ book » pour dire « C'est mon livre » ?", "my", ["me", "I", "mine", "mys"], "« My » est l'adjectif possessif de « I ».", 2),
    ("Quel mot complète « I ___ a brother » pour dire « J'ai un frère » ?", "have", ["has", "am", "is", "are"], "Avec « I », « you », « we » et « they », on emploie « have ».", 2),
    ("Quel mot complète « He ___ a bicycle » pour dire « Il a un vélo » ?", "has", ["have", "is", "are", "haves"], "Avec « he », « she » et « it », on emploie « has ».", 2),
    ("Dans « The cat is under the table », que veut dire « under » ?", "sous", ["sur", "dans", "devant", "derrière"], "« Under » indique que le chat est sous la table.", 2),
    ("Dans « The book is on the table », que veut dire « on » ?", "sur", ["sous", "dans", "derrière", "entre"], "« On » indique le contact avec le dessus d'une surface.", 2),
    ("Dans « The pen is in the bag », que veut dire « in » ?", "dans", ["sur", "sous", "devant", "à côté de"], "« In » veut dire « à l'intérieur de ».", 2),
    ("Comment dit-on « Quelle couleur est-ce ? » en anglais ?", "What colour is it", ["Where is it", "Who is it", "How old is it", "What time is it"], "« Colour » s'écrit « color » en anglais américain.", 3),
    ("Quel est le pluriel de « box » ?", "boxes", ["boxs", "boxies", "box's", "boxen"], "Après -x, -s, -ch ou -sh, on ajoute « es ».", 3),
    ("Quel est le pluriel de « baby » ?", "babies", ["babys", "babyes", "babis", "baby's"], "Après une consonne, « y » devient « ies ».", 3),
    ("Quel est le pluriel de « man » ?", "men", ["mans", "mens", "manes", "man's"], "« Man » a un pluriel irrégulier.", 2),
    ("Quel est le pluriel de « child » ?", "children", ["childs", "childrens", "childen", "childes"], "« Children » est un pluriel irrégulier.", 2),
    ("Que veut dire « Where are you from » ?", "D'où viens-tu ?", ["Où vas-tu ?", "Comment vas-tu ?", "Qui es-tu ?", "Que fais-tu ?"], "On répond « I am from Cameroon ».", 3),
    ("Comment dit-on « Quelle heure est-il ? » en anglais ?", "What time is it", ["What is your name", "How old are you", "Where is it", "Who is it"], "On répond par exemple « It is ten o'clock ».", 2),
    ("Comment écrit-on le nombre 40 en lettres, en anglais ?", "forty", ["fourty", "fortie", "forthy", "fourti"], "« Four » prend un « u », mais « forty » n'en a pas.", 3),
], "pca-en-gram", "Anglais")

# ============================================================ ANGLAIS : CM1
en("cm1", "pca-cm1-en-questions", [("quoi", "what"), ("où", "where"), ("qui", "who"), ("quand", "when"), ("pourquoi", "why"), ("comment", "how")], 2,
   fwd="Comment dit-on le mot interrogatif « {a} » en anglais ?", rev="Que veut dire le mot interrogatif « {b} » en français ?", expl="Le mot interrogatif « {a} » se dit « {b} ».")
en("cm1", "pca-cm1-en-maison", [("cuisine", "kitchen"), ("chambre", "bedroom"), ("salle de bains", "bathroom"), ("salon", "living room"), ("jardin", "garden"), ("toit", "roof")], 2)
en("cm1", "pca-cm1-en-metiers", [("médecin", "doctor"), ("infirmier ou infirmière", "nurse"), ("agriculteur", "farmer"), ("chauffeur", "driver"), ("boulanger", "baker"), ("pêcheur", "fisherman"), ("enseignant", "teacher"), ("policier", "police officer"), ("tailleur", "tailor")], 2,
   fwd="Comment dit-on le métier « {a} » en anglais ?", rev="Quel métier désigne le mot anglais « {b} » ?")
en("cm1", "pca-cm1-en-lieux", [("hôpital", "hospital"), ("marché", "market"), ("église", "church"), ("bibliothèque", "library"), ("bureau de poste", "post office"), ("gare routière", "bus station"), ("pharmacie", "pharmacy"), ("stade", "stadium")], 2)
en("cm1", "pca-cm1-en-meteo", [("ensoleillé", "sunny"), ("pluvieux", "rainy"), ("venteux", "windy"), ("nuageux", "cloudy"), ("orageux", "stormy")], 2,
   fwd="Comment dit-on « {a} » (à propos du temps) en anglais ?", rev="Que veut dire « {b} » à propos du temps ?")
en("cm1", "pca-cm1-en-verbes", [("manger", "eat"), ("boire", "drink"), ("dormir", "sleep"), ("courir", "run"), ("marcher", "walk"), ("écrire", "write"), ("lire", "read"), ("chanter", "sing"), ("nager", "swim"), ("sauter", "jump"), ("rire", "laugh"), ("pleurer", "cry")], 2,
   fwd="Comment dit-on le verbe « {a} » en anglais (sans « to ») ?", rev="Que veut dire le verbe anglais « {b} » ?", expl="Le verbe « {a} » se dit « {b} » en anglais.")
pairs("cm1", "pca-cm1-en-rangs", [("1er", "first"), ("2e", "second"), ("3e", "third"), ("5e", "fifth"), ("8e", "eighth"), ("9e", "ninth"), ("12e", "twelfth")],
      "Comment écrit-on le rang « {a} » en anglais ?", "Quel rang désigne le mot anglais « {b} » ?", region=W, cat="Anglais", src="pca-en-gram", diff=3, expl="Le rang {a} se dit « {b} ».")
add("cm1", "pca-cm1-en-gram", [
    ("Quelle forme complète la phrase « She ___ football every Saturday » ?", "plays", ["play", "playing", "are play", "playes"], "Au présent simple, la 3e personne du singulier prend un « s ».", 2),
    ("Quelle forme complète la phrase « He ___ to school by bus » ?", "goes", ["go", "going", "gos", "are going"], "Après « he », « go » devient « goes ».", 2),
    ("Quel mot complète « ___ you like rice » ?", "Do", ["Does", "Are", "Is", "Am"], "La question au présent simple se forme avec « do » (ou « does » à la 3e personne).", 2),
    ("Quel mot complète « ___ she live in Douala » ?", "Does", ["Do", "Is", "Are", "Am"], "Avec « she », on utilise « does ».", 2),
    ("Quel mot complète « She ___ swim » pour dire « Elle sait nager » ?", "can", ["cans", "is can", "does can", "cannot to"], "« Can » ne prend pas de « s » à la 3e personne.", 2),
    ("Quel mot complète « There ___ three books on the table » ?", "are", ["is", "am", "be", "has"], "Avec un pluriel, on dit « there are ».", 2),
    ("Quel mot complète « There ___ a cat in the garden » ?", "is", ["are", "am", "be", "have"], "Avec un singulier, on dit « there is ».", 2),
    ("Quel est le pluriel de « foot » ?", "feet", ["foots", "feets", "footes", "foot's"], "« Foot » a un pluriel irrégulier.", 2),
    ("Quel est le pluriel de « tooth » ?", "teeth", ["tooths", "teeths", "toothes", "tooth's"], "« Tooth » a un pluriel irrégulier.", 2),
    ("Quel est le pluriel de « woman » ?", "women", ["womans", "womens", "womanes", "woman's"], "« Woman » a un pluriel irrégulier (le mot se prononce différemment).", 3),
    ("Quel est le pluriel de « mouse » (l'animal) ?", "mice", ["mouse's", "mices", "mouzes", "meese"], "« Mouse » a un pluriel irrégulier.", 3),
    ("Que veut dire « It's half past two » ?", "Il est deux heures et demie", ["Il est deux heures moins le quart", "Il est deux heures et quart", "Il est trois heures", "Il est deux heures moins dix"], "« Half past » veut dire « et demie ».", 3),
    ("Que veut dire « It's quarter to five » ?", "Il est cinq heures moins le quart", ["Il est cinq heures et quart", "Il est cinq heures et demie", "Il est quatre heures et demie", "Il est quatre heures et quart"], "« Quarter to » veut dire « moins le quart ».", 3),
    ("Que veut dire le mot anglais « today » ?", "aujourd'hui", ["demain", "hier", "ce soir", "toujours"], "« Today » désigne le jour présent.", 1),
    ("Que veut dire le mot anglais « tomorrow » ?", "demain", ["aujourd'hui", "hier", "toujours", "bientôt"], "« Tomorrow » désigne le jour suivant.", 2),
    ("Que veut dire le mot anglais « yesterday » ?", "hier", ["aujourd'hui", "demain", "ce soir", "jamais"], "« Yesterday » désigne le jour précédent.", 2),
    ("Comment demande-t-on « Combien coûte ce livre ? » en anglais ?", "How much is this book", ["How many is this book", "How old is this book", "What is this book", "Who is this book"], "On utilise « how much » pour demander un prix.", 3),
    ("Comment demande-t-on « Combien de frères as-tu ? » en anglais ?", "How many brothers do you have", ["How much brothers do you have", "How old brothers do you have", "What brothers do you have", "Where brothers do you have"], "On utilise « how many » avec des noms que l'on peut compter.", 3),
], "pca-en-gram", "Anglais")

# ============================================================ ANGLAIS : CM2
verbs("cm2", "pca-cm2-en-prets", [("go", "went"), ("eat", "ate"), ("see", "saw"), ("have", "had"), ("buy", "bought"), ("come", "came"), ("take", "took"), ("give", "gave"), ("make", "made"), ("write", "wrote"), ("drink", "drank"), ("run", "ran"), ("get", "got"), ("say", "said")], "le prétérit", 3)
pairs("cm2", "pca-cm2-en-compar", [("tall", "taller"), ("big", "bigger"), ("small", "smaller"), ("fast", "faster"), ("happy", "happier"), ("good", "better"), ("bad", "worse"), ("long", "longer"), ("hot", "hotter"), ("easy", "easier"), ("young", "younger")],
      "Quel est le comparatif de supériorité de l'adjectif « {a} » ?", "De quel adjectif « {b} » est-il le comparatif de supériorité ?", region=W, cat="Anglais", src="pca-en-gram", diff=3, expl="Le comparatif de « {a} » est « {b} ».")
en("cm2", "pca-cm2-en-matieres", [("histoire", "history"), ("géographie", "geography"), ("langue française", "French"), ("langue anglaise", "English"), ("sciences", "science"), ("musique", "music")], 2,
   fwd="Comment dit-on la matière « {a} » en anglais ?", rev="Quelle matière scolaire désigne « {b} » ?")
en("cm2", "pca-cm2-en-transport", [("voiture", "car"), ("avion", "plane"), ("vélo", "bicycle"), ("bateau", "boat"), ("pirogue", "canoe"), ("route", "road"), ("pont", "bridge"), ("rue", "street")], 2)
en("cm2", "pca-cm2-en-emotions", [("fatigué", "tired"), ("affamé (qui a faim)", "hungry"), ("assoiffé (qui a soif)", "thirsty"), ("en colère", "angry"), ("effrayé", "afraid"), ("content", "happy")], 2,
   fwd="Comment dit-on « {a} » en anglais ?", rev="Que veut dire l'adjectif « {b} » ?")
add("cm2", "pca-cm2-en-gram", [
    ("Quel est le prétérit du verbe régulier « play » ?", "played", ["plaied", "playd", "plays", "plaid"], "On ajoute « ed » au verbe régulier.", 2),
    ("Quel est le prétérit du verbe « study » ?", "studied", ["studyed", "studed", "studys", "studdied"], "Après une consonne, « y » devient « ied ».", 3),
    ("Quel est le prétérit du verbe « stop » ?", "stopped", ["stoped", "stopt", "stopeed", "stoping"], "On double la consonne finale après une voyelle courte.", 3),
    ("Comment dit-on « Je ne suis pas allé à l'école hier » ?", "I did not go to school yesterday", ["I did not went to school yesterday", "I not go to school yesterday", "I am not go to school yesterday", "I do not went to school yesterday"], "Après « did not », le verbe reste à l'infinitif.", 4),
    ("Quel mot complète « Yesterday, I ___ a mango » ?", "ate", ["eat", "eaten", "eats", "eated"], "Le prétérit de « eat » est « ate ».", 3),
    ("Quelle forme complète « Look ! The children ___ football » ?", "are playing", ["plays", "are play", "is playing", "play"], "Pour une action en cours, on emploie « be + verbe en -ing ».", 3),
    ("Quel mot complète « I ___ reading a book now » ?", "am", ["is", "are", "do", "be"], "Avec « I », on emploie « am » devant le verbe en -ing.", 2),
    ("Que veut dire « behind » dans « The dog is behind the house » ?", "derrière", ["devant", "entre", "à côté de", "sur"], "« Behind » est le contraire de « in front of ».", 2),
    ("Que veut dire « between » dans « The ball is between the chairs » ?", "entre", ["devant", "derrière", "sous", "contre"], "« Between » désigne ce qui est au milieu de deux choses.", 2),
    ("Que veut dire « next to » dans « The school is next to the market » ?", "à côté de", ["en face de", "derrière", "sous", "loin de"], "« Next to » veut dire « tout près de ».", 3),
    ("Que veut dire « in front of » dans « The car is in front of the school » ?", "devant", ["derrière", "entre", "au-dessus de", "dans"], "« In front of » est le contraire de « behind ».", 3),
    ("Quel mot complète « I have ___ friends » pour dire « J'ai quelques amis » ?", "some", ["any", "much", "a", "an"], "On emploie « some » dans une phrase affirmative.", 3),
    ("Quel mot complète « We haven't got ___ bread » ?", "any", ["some", "a", "an", "many"], "Dans une phrase négative, on emploie « any ».", 3),
    ("Quel mot complète « Give the book to ___ » pour dire « Donne le livre à lui » ?", "him", ["he", "his", "hers", "himself"], "« Him » est le pronom complément de « he ».", 3),
    ("Quel adjectif désigne un habitant du Cameroun ?", "Cameroonian", ["Camerounian", "Cameroonish", "Cameroun", "Camerounese"], "Le pays s'écrit « Cameroon » en anglais.", 3),
    ("Que veut dire « Where do you live » ?", "Où habites-tu ?", ["D'où viens-tu ?", "Comment vas-tu ?", "Que fais-tu ?", "Qui es-tu ?"], "On répond par exemple « I live in Yaoundé ».", 2),
    ("Comment dit-on « Je voudrais de l'eau, s'il vous plaît » ?", "I would like some water, please", ["I am like some water, please", "I do like would water, please", "I would likes some water, please", "I have like some water, please"], "« I would like » est une façon polie de demander quelque chose.", 4),
    ("Quel mois vient après « March » ?", "April", ["May", "February", "June", "January"], "L'ordre est January, February, March, April...", 2),
    ("Quel jour vient après « Wednesday » ?", "Thursday", ["Tuesday", "Friday", "Saturday", "Monday"], "L'ordre est Monday, Tuesday, Wednesday, Thursday...", 2),
    ("Comment dit-on « Il est huit heures » ?", "It is eight o'clock", ["It is eight clock", "He is eight o'clock", "It has eight o'clock", "It is eighth o'clock"], "« O'clock » s'emploie pour les heures pleines.", 3),
    ("Quel est le contraire de l'adverbe « always » ?", "never", ["often", "sometimes", "usually", "soon"], "« Always » veut dire « toujours » et « never » « jamais ».", 3),
], "pca-en-gram", "Anglais")

# ============================================================ ANGLAIS : 6e
en("6e", "pca-6e-en-famille", [("oncle", "uncle"), ("tante", "aunt"), ("cousin ou cousine", "cousin"), ("neveu", "nephew"), ("nièce", "niece"), ("mari", "husband"), ("épouse", "wife"), ("petit-fils", "grandson"), ("petite-fille", "granddaughter")], 2)
en("6e", "pca-6e-en-aliments", [("viande", "meat"), ("légumes", "vegetables"), ("sel", "salt"), ("huile", "oil"), ("manioc", "cassava"), ("fromage", "cheese"), ("soupe", "soup"), ("haricots", "beans"), ("igname", "yam"), ("gâteau", "cake")], 2)
en("6e", "pca-6e-en-qualites", [("beau ou belle", "beautiful"), ("laid", "ugly"), ("gentil", "kind"), ("paresseux", "lazy"), ("courageux", "brave"), ("honnête", "honest"), ("poli", "polite"), ("généreux", "generous"), ("timide", "shy"), ("drôle", "funny")], 3,
   fwd="Comment dit-on l'adjectif « {a} » en anglais ?", rev="Que veut dire l'adjectif anglais « {b} » ?")
en("6e", "pca-6e-en-nature", [("montagne", "mountain"), ("forêt", "forest"), ("mer", "sea"), ("arbre", "tree"), ("fleur", "flower"), ("lune", "moon"), ("étoile", "star"), ("ciel", "sky"), ("colline", "hill"), ("lac", "lake"), ("pluie", "rain"), ("sable", "sand")], 2)
add("6e", "pca-6e-en-gram", [
    ("Quelle est la forme contractée de « I am » ?", "I'm", ["I'am", "Im'", "I'me", "Is'm"], "L'apostrophe remplace la lettre « a ».", 2),
    ("Quelle phrase est la forme négative correcte de « She is a pupil » ?", "She is not a pupil", ["She not is a pupil", "She does not a pupil", "She is no a pupil", "She do not a pupil"], "Avec « to be », on place « not » après le verbe.", 2),
    ("Quelle est la forme contractée de « do not » ?", "don't", ["do'nt", "dont'", "d'not", "doesn't"], "L'apostrophe remplace le « o » de « not ».", 2),
    ("Quelle est la forme contractée de « does not » ?", "doesn't", ["does'nt", "doesnt'", "don't", "do'esnt"], "On l'emploie avec « he », « she » et « it ».", 2),
    ("Quel est l'article défini en anglais (le, la, les) ?", "the", ["a", "an", "some", "this"], "« The » ne change ni en genre ni en nombre.", 1),
    ("Quel est l'adjectif possessif de « she » ?", "her", ["his", "its", "their", "your"], "« Her » signifie « son, sa, ses » (d'une femme).", 2),
    ("Quel est l'adjectif possessif de « they » ?", "their", ["there", "they're", "them", "theirs"], "« Their » signifie « leur, leurs ».", 3),
    ("Quelle est la 3e personne du singulier du verbe « have » au présent ?", "has", ["haves", "have", "haved", "having"], "On dit « he has », « she has », « it has ».", 2),
    ("Quelle est la 3e personne du singulier du verbe « do » au présent ?", "does", ["do", "dos", "doed", "doing"], "On dit « he does », « she does », « it does ».", 3),
    ("Quel auxiliaire complète la question « ___ you speak English ? » ?", "Do", ["Are", "Does", "Is", "Am"], "Au présent simple, la question commence par « do » ou « does ».", 2),
    ("Quel mot interrogatif complète « ___ is your name ? » ?", "What", ["Who", "Where", "Why", "When"], "« What is your name ? » veut dire « Comment t'appelles-tu ? ».", 2),
    ("Quel mot interrogatif sert à demander le lieu ?", "Where", ["When", "Why", "Who", "How"], "« Where » veut dire « où ».", 1),
    ("Quel mot interrogatif sert à demander le moment ?", "When", ["Where", "Who", "How", "Whose"], "« When » veut dire « quand ».", 2),
    ("Quel mot interrogatif sert à demander la cause ?", "Why", ["What", "Where", "How", "Which"], "« Why » veut dire « pourquoi ».", 2),
    ("Quelle préposition complète « I get up ___ six o'clock » ?", "at", ["on", "in", "by", "to"], "On emploie « at » devant une heure précise.", 2),
    ("Quelle préposition complète « My birthday is ___ Monday » ?", "on", ["at", "in", "by", "to"], "On emploie « on » devant un jour de la semaine.", 2),
    ("Quelle préposition complète « We have a holiday ___ August » ?", "in", ["on", "at", "by", "to"], "On emploie « in » devant un mois.", 2),
    ("Quel est le pluriel de « city » ?", "cities", ["citys", "cityes", "city's", "citis"], "Après une consonne, « y » devient « ies ».", 3),
    ("Quel est le pluriel de « bus » ?", "buses", ["buss", "bus's", "busies", "busen"], "Après « s », on ajoute « es ».", 3),
    ("Quel est le pluriel de « knife » ?", "knives", ["knifes", "knifs", "knifees", "knife's"], "Avec « f » ou « fe », le pluriel devient parfois « ves ».", 3),
    ("Quel est le pluriel de « sheep » ?", "sheep", ["sheeps", "sheepes", "sheep's", "sheepen"], "« Sheep » ne change pas au pluriel.", 3),
    ("Quel pronom sujet remplace « Kofi and I » ?", "we", ["us", "they", "you", "he"], "« We » signifie « nous ».", 2),
    ("Quel pronom sujet remplace « the girls » ?", "they", ["we", "it", "she", "you"], "« They » signifie « ils » ou « elles ».", 2),
    ("Quel pronom sujet remplace « the book » ?", "it", ["he", "she", "they", "we"], "On emploie « it » pour une chose ou un animal.", 2),
    ("Quelle phrase est correcte ?", "There are two pens on the desk", ["There is two pens on the desk", "There are a pen on the desk", "There be two pens on the desk", "There am two pens on the desk"], "Avec un nom pluriel, on dit « there are ».", 3),
    ("Quel verbe complète « Please, ___ the window » (ordre poli) ?", "open", ["opens", "opening", "are open", "to opens"], "À l'impératif, on emploie le verbe à l'infinitif sans « to ».", 3),
    ("Que veut dire « Don't run » ?", "Ne cours pas !", ["Cours !", "Je ne cours pas", "Il ne court pas", "Nous courons"], "« Don't » + verbe exprime une défense.", 2),
    ("Quel mot complète « How ___ pens do you have » ?", "many", ["much", "old", "long", "far"], "On emploie « many » avec des noms que l'on peut compter.", 2),
    ("Quel mot complète « How ___ is this bread » (le prix) ?", "much", ["many", "old", "tall", "far"], "« How much » sert à demander un prix.", 2),
    ("Que veut dire « Can I come in » ?", "Puis-je entrer ?", ["Dois-je sortir ?", "Je ne peux pas entrer", "Peux-tu venir ?", "Veux-tu entrer ?"], "« Can I... ? » sert à demander une permission.", 2),
    ("Que veut dire « I can't swim » ?", "Je ne sais pas nager", ["Je sais nager", "Je ne veux pas nager", "Je nage bien", "Je vais nager"], "« Can't » est la forme contractée de « cannot ».", 2),
    ("Comment dit-on « Il pleut » (en ce moment) ?", "It is raining", ["It rain now", "It is rain", "It raining are", "He is raining"], "Pour une action en cours, on emploie « be + verbe en -ing ».", 3),
    ("Quelle est la bonne orthographe de « mercredi » en anglais ?", "Wednesday", ["Wensday", "Wendsday", "Wednsday", "Wedensday"], "Le « d » de « Wednesday » est écrit mais ne se prononce pas.", 3),
    ("Quelle est la bonne orthographe de « février » en anglais ?", "February", ["Febuary", "Feburary", "Febrary", "Februry"], "Le premier « r » ne doit pas être oublié.", 3),
    ("Quelle est la bonne orthographe de « ami » en anglais ?", "friend", ["freind", "frend", "firend", "friand"], "On écrit « i » avant « e » : fr-i-e-nd.", 3),
    ("Quelle est la bonne orthographe de « parce que » en anglais ?", "because", ["becuase", "becouse", "becase", "becaus"], "« Because » s'écrit be-cau-se.", 3),
    ("Quelle est la bonne orthographe de « beau » en anglais ?", "beautiful", ["beutiful", "beatiful", "beautifull", "beautyful"], "« Beautiful » se termine par un seul « l ».", 3),
    ("Quelle est la bonne orthographe de « différent » en anglais ?", "different", ["diferent", "diffrent", "differant", "differrent"], "« Different » s'écrit avec deux « f ».", 3),
], "pca-en-gram", "Anglais")

# ============================================================ ANGLAIS : 5e
verbs("5e", "pca-5e-en-prets", [("begin", "began"), ("break", "broke"), ("bring", "brought"), ("choose", "chose"), ("drive", "drove"), ("fall", "fell"), ("feel", "felt"), ("find", "found"), ("fly", "flew"), ("forget", "forgot"), ("grow", "grew"), ("hear", "heard"), ("keep", "kept"), ("know", "knew"), ("leave", "left"), ("lose", "lost"), ("meet", "met"), ("pay", "paid"), ("sell", "sold"), ("sit", "sat"), ("sleep", "slept"), ("speak", "spoke"), ("stand", "stood"), ("swim", "swam")], "le prétérit", 3)
en("5e", "pca-5e-en-corps", [("estomac", "stomach"), ("dos", "back"), ("gorge", "throat"), ("cœur", "heart"), ("peau", "skin"), ("cheveux", "hair"), ("doigt", "finger"), ("genou", "knee"), ("cou", "neck"), ("épaule", "shoulder"), ("coude", "elbow"), ("cheville", "ankle")], 3)
en("5e", "pca-5e-en-metiers", [("avocat", "lawyer"), ("ingénieur", "engineer"), ("mécanicien", "mechanic"), ("soldat", "soldier"), ("juge", "judge"), ("cuisinier", "cook"), ("commerçant", "shopkeeper"), ("électricien", "electrician"), ("menuisier", "carpenter"), ("coiffeur", "hairdresser")], 3,
   fwd="Comment dit-on le métier « {a} » en anglais ?", rev="Quel métier désigne le mot anglais « {b} » ?")
add("5e", "pca-5e-en-gram", [
    ("Quel est le comparatif de supériorité de « interesting » ?", "more interesting", ["interestinger", "most interesting", "more interestinger", "interestinger than"], "Les adjectifs longs forment leur comparatif avec « more ».", 3),
    ("Quel est le superlatif de « tall » ?", "the tallest", ["the most tall", "most tall", "the more tall", "the talller"], "Les adjectifs courts prennent « the » devant et « est » à la fin.", 3),
    ("Quel est le superlatif de « good » ?", "the best", ["the goodest", "the most good", "the better", "the more good"], "« Good, better, best » est une série irrégulière.", 3),
    ("Quel est le comparatif de « bad » ?", "worse", ["badder", "more bad", "worser", "baddest"], "« Bad, worse, worst » est une série irrégulière.", 3),
    ("Quel est le superlatif de « expensive » ?", "the most expensive", ["the expensivest", "the more expensive", "the expensive most", "the expensiver"], "Les adjectifs longs forment leur superlatif avec « the most ».", 3),
    ("Quel mot complète « Kofi is ___ than Awa » pour dire « Kofi est plus grand que Awa » ?", "taller", ["tall", "more tall", "tallest", "the tallest"], "Comparatif de supériorité + « than ».", 3),
    ("Quel mot complète « She is as tall ___ her sister » ?", "as", ["than", "like", "that", "of"], "L'égalité se forme avec « as » + adjectif + « as ».", 3),
    ("Quelle forme complète « They ___ for the bus now » ?", "are waiting", ["waits", "is waiting", "are wait", "waited"], "Pour une action en cours, on emploie « be + verbe en -ing ».", 3),
    ("Quelle phrase exprime correctement le futur avec « will » ?", "I will visit my uncle tomorrow", ["I will visited my uncle tomorrow", "I will to visit my uncle tomorrow", "I wills visit my uncle tomorrow", "I will visiting my uncle tomorrow"], "« Will » est suivi du verbe à l'infinitif sans « to ».", 3),
    ("Que veut dire « I'm going to cook » ?", "Je vais cuisiner", ["J'ai cuisiné", "Je cuisinais", "Je cuisine toujours", "Je sais cuisiner"], "« Be going to » exprime une intention ou un futur proche.", 3),
    ("Quel est le prétérit du verbe « walk » ?", "walked", ["walkt", "waked", "walken", "walkd"], "Verbe régulier : on ajoute « ed ».", 2),
    ("Quel est le prétérit du verbe « carry » ?", "carried", ["carryed", "carred", "caried", "carryd"], "Après une consonne, « y » devient « ied ».", 3),
    ("Quel est le prétérit du verbe « plan » ?", "planned", ["planed", "plannd", "planen", "plannt"], "On double la consonne finale après une voyelle courte.", 4),
    ("Quel est le prétérit du verbe « like » ?", "liked", ["likeed", "liken", "likt", "likd"], "Le verbe se termine déjà par « e » : on ajoute seulement « d ».", 3),
    ("Quelle phrase est correcte ?", "She didn't come yesterday", ["She didn't came yesterday", "She not came yesterday", "She doesn't come yesterday", "She don't came yesterday"], "Après « didn't », le verbe reste à l'infinitif.", 3),
    ("Quelle forme complète « Did you ___ the film » ?", "see", ["saw", "seen", "sees", "seeing"], "Après « did », le verbe reste à l'infinitif.", 3),
    ("Que veut dire « You must study » ?", "Tu dois étudier", ["Tu peux étudier", "Tu voudrais étudier", "Tu as étudié", "Tu étudieras"], "« Must » exprime l'obligation.", 3),
    ("Que veut dire « You mustn't smoke » ?", "Il est interdit de fumer", ["Il n'est pas nécessaire de fumer", "Tu dois fumer", "Tu peux fumer", "Tu fumes souvent"], "« Mustn't » exprime l'interdiction.", 4),
    ("Qu'exprime « You should see a doctor » ?", "Un conseil", ["Une interdiction", "Une obligation stricte", "Un souvenir", "Une habitude passée"], "« Should » exprime un conseil.", 3),
    ("Lequel de ces noms est dénombrable en anglais ?", "book", ["water", "rice", "sugar", "milk"], "On peut compter les livres : one book, two books.", 3),
    ("Quel mot complète « How ___ sugar do you take » ?", "much", ["many", "long", "old", "far"], "« Sugar » n'est pas dénombrable : on emploie « much ».", 3),
    ("Quel mot complète « There isn't ___ water » ?", "any", ["some", "a", "many", "an"], "Dans une phrase négative, on emploie « any ».", 3),
    ("Quel adverbe de fréquence veut dire « toujours » ?", "always", ["never", "sometimes", "often", "rarely"], "« Always » = 100 % du temps.", 2),
    ("Que veut dire l'adverbe « rarely » ?", "rarement", ["souvent", "toujours", "jamais", "parfois"], "« Rarely » = presque jamais.", 3),
    ("Quelle phrase est correcte ?", "She always arrives early", ["She arrives always early", "She always is arrive early", "She arrive always early", "She early always arrives"], "L'adverbe de fréquence se place avant le verbe (sauf « be »).", 4),
    ("Comment dit-on « le livre de Kofi » avec le génitif ?", "Kofi's book", ["Kofis book", "Kofi book's", "Book Kofi's", "Kofi' book"], "Le possesseur est suivi de « 's ».", 3),
    ("Quelle est la bonne orthographe de « demain » en anglais ?", "tomorrow", ["tommorow", "tomorow", "tommorrow", "tomorrov"], "« Tomorrow » : un seul « m » et deux « r ».", 3),
    ("Quelle est la bonne orthographe de « le début » en anglais ?", "beginning", ["begining", "beginnning", "beggining", "beginning's"], "On double le « n » : begin-n-ing.", 3),
    ("Quelle est la bonne orthographe de « bibliothèque » en anglais ?", "library", ["libary", "liberry", "libarry", "librery"], "« Library » contient deux « r ».", 3),
    ("Quelle est la bonne orthographe de « intéressant » en anglais ?", "interesting", ["intresting", "interessting", "inerresting", "interasting"], "« Interesting » : inter-est-ing.", 3),
    ("Quelle est la bonne orthographe de « le temps (météo) » en anglais ?", "weather", ["wheather", "wether", "weahter", "weathar"], "Ne pas confondre avec « whether » (si).", 3),
], "pca-en-gram", "Anglais")

# ============================================================ ANGLAIS : 4e
verbs("4e", "pca-4e-en-pp", [("go", "gone"), ("do", "done"), ("see", "seen"), ("eat", "eaten"), ("give", "given"), ("take", "taken"), ("write", "written"), ("begin", "begun"), ("drink", "drunk"), ("swim", "swum"), ("sing", "sung"), ("buy", "bought"), ("bring", "brought"), ("catch", "caught"), ("teach", "taught"), ("know", "known"), ("grow", "grown"), ("fly", "flown"), ("forget", "forgotten")], "le participe passé", 3)
pairs("4e", "pca-4e-en-phrasal", [("look for", "chercher"), ("give up", "abandonner"), ("turn on", "allumer"), ("turn off", "éteindre"), ("get up", "se lever"), ("look after", "s'occuper de"), ("find out", "découvrir (une information)"), ("put on", "mettre (un vêtement)"), ("wake up", "se réveiller"), ("sit down", "s'asseoir"), ("come back", "revenir"), ("fill in", "remplir (un formulaire)"), ("run out of", "ne plus avoir de"), ("carry on", "continuer")],
      "Que veut dire le verbe à particule « {a} » ?", "Quel verbe à particule anglais veut dire « {b} » ?", region=W, cat="Anglais", src="pca-en-gram", diff=4, expl="« {a} » veut dire « {b} ».")
en("4e", "pca-4e-en-societe", [("environnement", "environment"), ("gouvernement", "government"), ("santé", "health"), ("pauvreté", "poverty"), ("chômage", "unemployment"), ("liberté", "freedom"), ("paix", "peace"), ("guerre", "war"), ("connaissance", "knowledge"), ("échec", "failure"), ("conseil", "advice"), ("rêve", "dream"), ("vérité", "truth"), ("peur", "fear")], 3)
add("4e", "pca-4e-en-gram", [
    ("Quelle phrase est correcte ?", "I have lived here for five years", ["I have live here for five years", "I am living here since five years", "I lived here since five years", "I have lived here since five years"], "Le present perfect se forme avec « have + participe passé ».", 4),
    ("Quel mot complète « She has worked here ___ 2019 » ?", "since", ["for", "ago", "during", "from"], "« Since » introduit un point de départ dans le temps.", 4),
    ("Quel mot complète « They have been friends ___ ten years » ?", "for", ["since", "ago", "from", "during"], "« For » introduit une durée.", 4),
    ("Que veut dire « two years ago » ?", "il y a deux ans", ["dans deux ans", "pendant deux ans", "depuis deux ans", "une fois tous les deux ans"], "« Ago » se place après la durée et s'emploie avec le prétérit.", 4),
    ("Quelle est la bonne question ?", "Have you ever been to Yaoundé", ["Did you ever been to Yaoundé", "Are you ever been to Yaoundé", "Have you ever be to Yaoundé", "Do you ever been to Yaoundé"], "« Have you ever + participe passé » demande une expérience de vie.", 4),
    ("Quelle phrase est au passif ?", "The window was broken by a boy", ["A boy broke the window", "A boy is breaking the window", "A boy has broken the window", "A boy will break the window"], "Le passif se forme avec « be + participe passé ».", 4),
    ("Quelle est la forme passive de « Kofi wrote the letter » ?", "The letter was written by Kofi", ["The letter wrote by Kofi", "The letter was wrote by Kofi", "The letter is written by Kofi", "The letter has written by Kofi"], "Au passif, le temps de « be » reste celui de la phrase active.", 5),
    ("Quel auxiliaire emploie-t-on pour former le passif ?", "be", ["have", "do", "can", "will"], "Le passif se forme avec « be + participe passé ».", 4),
    ("Quel groupe complète « If it rains, we ___ at home » ?", "will stay", ["would stay", "stayed", "stays", "would have stayed"], "Condition possible : « if + présent, will + verbe ».", 4),
    ("Quel mot complète « If I ___ a car, I would drive to Kribi » ?", "had", ["have", "will have", "would have", "has"], "Condition imaginaire : « if + prétérit, would + verbe ».", 4),
    ("Quel mot complète « He said that he ___ tired » après « I am tired », he said ?", "was", ["am", "were", "be", "will be"], "Au discours indirect, le présent devient souvent un prétérit.", 5),
    ("Quel pronom relatif complète « The man ___ lives next door is a doctor » ?", "who", ["which", "whose", "whom", "where"], "« Who » remplace une personne sujet.", 4),
    ("Quel pronom relatif complète « This is the book ___ I bought yesterday » ?", "which", ["who", "whose", "where", "whom"], "« Which » remplace une chose.", 4),
    ("Quel pronom relatif complète « The girl ___ bag is red is my cousin » ?", "whose", ["who", "which", "whom", "where"], "« Whose » exprime la possession.", 4),
    ("Quelle est la question tag correcte pour « She is a nurse, ___ ? » ?", "isn't she", ["doesn't she", "is she", "aren't she", "does she"], "Phrase affirmative : tag négatif avec le même auxiliaire.", 4),
    ("Quelle est la question tag correcte pour « You like rice, ___ ? » ?", "don't you", ["aren't you", "do you", "didn't you", "haven't you"], "Au présent simple, le tag reprend « do ».", 4),
    ("Que veut dire « I used to play football » ?", "Avant, je jouais au football", ["Je vais jouer au football", "Je joue au football en ce moment", "J'ai joué hier au football", "Je jouerai au football"], "« Used to » exprime une habitude passée.", 4),
    ("Quelle forme complète « She enjoys ___ » ?", "dancing", ["to dance", "dance", "danced", "dances"], "Après « enjoy », le verbe prend la forme en -ing.", 4),
    ("Quelle forme complète « I want ___ a doctor » ?", "to be", ["being", "be", "been", "am"], "Après « want », on emploie « to + infinitif ».", 3),
    ("Quel mot complète « It is ___ hot to work » pour dire « Il fait trop chaud pour travailler » ?", "too", ["enough", "very", "so", "much"], "« Too » exprime l'excès.", 4),
    ("Que veut dire le mot anglais « actually » ?", "en fait", ["actuellement", "activement", "autrefois", "bientôt"], "« Actually » est un faux ami : « actuellement » se dit « currently ».", 4),
    ("Que veut dire le mot anglais « library » ?", "bibliothèque", ["librairie", "laboratoire", "livre", "lecture"], "Une librairie se dit « bookshop ».", 3),
    ("Que veut dire le mot anglais « to attend » (une réunion) ?", "assister à", ["attendre", "attaquer", "atteindre", "attacher"], "« Attendre » se dit « to wait ».", 4),
    ("Que veut dire le verbe anglais « to pretend » ?", "faire semblant", ["prétendre", "attendre", "préparer", "protéger"], "Faux ami : « prétendre » se dit « to claim ».", 5),
    ("Que veut dire le verbe anglais « to demand » ?", "exiger", ["demander", "dépenser", "désirer", "décider"], "Faux ami : « demander » se dit « to ask ».", 4),
    ("Que veut dire l'adverbe anglais « eventually » ?", "finalement", ["éventuellement", "évidemment", "rapidement", "rarement"], "Faux ami : « éventuellement » se dit « possibly ».", 5),
    ("Quelle est la bonne orthographe de « nécessaire » en anglais ?", "necessary", ["neccessary", "necesary", "necessery", "necesssary"], "Un seul « c » et deux « s ».", 4),
    ("Quelle est la bonne orthographe du prétérit de « occur » ?", "occurred", ["occured", "ocurred", "occurrd", "occurered"], "On double le « r » avant « ed ».", 5),
    ("Quelle est la bonne orthographe de « séparé » en anglais ?", "separate", ["seperate", "separete", "seperete", "separat"], "« Separate » contient « par » au milieu.", 4),
    ("Quelle est la bonne orthographe de « certainement » en anglais ?", "definitely", ["definately", "definitly", "defenitely", "definatly"], "« Definitely » se termine par « -itely ».", 5),
], "pca-en-gram", "Anglais")

# ============================================================ ANGLAIS : 3e
verbs("3e", "pca-3e-en-pp", [("speak", "spoken"), ("break", "broken"), ("choose", "chosen"), ("steal", "stolen"), ("drive", "driven"), ("ride", "ridden"), ("rise", "risen"), ("fall", "fallen"), ("wear", "worn"), ("tear", "torn"), ("shake", "shaken"), ("hide", "hidden"), ("bite", "bitten"), ("freeze", "frozen"), ("throw", "thrown"), ("draw", "drawn"), ("blow", "blown"), ("wake", "woken")], "le participe passé", 4)
pairs("3e", "pca-3e-en-liens", [("cependant", "however"), ("donc", "therefore"), ("bien que", "although"), ("malgré", "despite"), ("alors que", "whereas"), ("de plus", "moreover"), ("à moins que", "unless"), ("dès que", "as soon as"), ("jusqu'à ce que", "until"), ("à cause de", "because of"), ("afin que", "so that")],
      "Comment dit-on « {a} » en anglais (mot de liaison) ?", "Que veut dire le mot de liaison « {b} » ?", region=W, cat="Anglais", src="pca-en-gram", diff=4, expl="« {b} » se traduit par « {a} ».")
en("3e", "pca-3e-en-verbes", [("emprunter", "borrow"), ("prêter", "lend"), ("gagner (de l'argent)", "earn"), ("gagner (un match)", "win"), ("dépenser (de l'argent)", "spend"), ("oublier", "forget"), ("se souvenir de", "remember"), ("apprendre (une leçon)", "learn"), ("enseigner", "teach"), ("rater (un bus)", "miss")], 4,
   fwd="Comment dit-on le verbe « {a} » en anglais ?", rev="Que veut dire le verbe anglais « {b} » ?")
add("3e", "pca-3e-en-gram", [
    ("Quel groupe complète « If I had studied, I ___ the exam » ?", "would have passed", ["would pass", "will pass", "had passed", "would have pass"], "Condition irréelle du passé : « if + past perfect, would have + participe ».", 5),
    ("Quel mot complète « If she ___ earlier, she would catch the bus » ?", "left", ["leaves", "will leave", "had left", "would leave"], "Condition imaginaire du présent : « if + prétérit, would + verbe ».", 5),
    ("Que veut dire « Unless you hurry, you will be late » ?", "Si tu ne te dépêches pas, tu seras en retard", ["Si tu te dépêches, tu seras en retard", "Parce que tu te dépêches, tu seras en retard", "Bien que tu te dépêches, tu seras en retard", "Quand tu seras en retard, tu te dépêcheras"], "« Unless » veut dire « à moins que » ou « si... ne... pas ».", 5),
    ("Quel mot exprime la cause ?", "because", ["although", "so that", "however", "unless"], "« Because » veut dire « parce que ».", 3),
    ("Quelle expression exprime le but ?", "so that", ["because", "although", "unless", "whereas"], "« So that » veut dire « afin que ».", 4),
    ("Quelle expression exprime l'opposition ?", "although", ["because", "so that", "as soon as", "therefore"], "« Although » veut dire « bien que ».", 4),
    ("Quelle est la forme indirecte de « I will come tomorrow », she said ?", "She said that she would come the next day", ["She said that she will come tomorrow", "She said that she comes the next day", "She said that she has come the next day", "She said that she would came the next day"], "Au discours indirect, « will » devient « would » et « tomorrow » devient « the next day ».", 5),
    ("Quelle est la forme indirecte de « Where do you live ? », he asked ?", "He asked where I lived", ["He asked where did I live", "He asked where lived I", "He asked me where do I lived", "He asked where I am lived"], "La question indirecte garde l'ordre sujet-verbe, sans « do ».", 5),
    ("Quelle est la forme passive de « They are building a bridge » ?", "A bridge is being built", ["A bridge is built", "A bridge was building", "A bridge has built", "A bridge is being build"], "Passif du présent continu : « is being + participe passé ».", 5),
    ("Quel est le participe passé du verbe « catch » dans « The thief was caught » ?", "caught", ["catched", "catch", "cought", "caughten"], "« Catch, caught, caught » est un verbe irrégulier.", 3),
    ("Quelle forme complète « The letters ___ yesterday » ?", "were written", ["are written", "was written", "were wrote", "have written"], "Passif du prétérit avec sujet pluriel : « were + participe passé ».", 4),
    ("Quelle forme complète « I wish I ___ taller » ?", "were", ["am", "will be", "would be", "have been"], "Après « I wish », on emploie un temps du passé pour un souhait irréel.", 5),
    ("Que veut dire « You should have studied » ?", "Tu aurais dû étudier", ["Tu dois étudier", "Tu as étudié", "Tu pourrais étudier", "Tu étudieras"], "« Should have + participe » exprime un regret ou un reproche.", 5),
    ("Que veut dire « She may be late » ?", "Elle sera peut-être en retard", ["Elle doit être en retard", "Elle était en retard", "Elle ne sera pas en retard", "Elle est toujours en retard"], "« May » exprime une possibilité.", 4),
    ("Quel mot complète « This is the town ___ I was born » ?", "where", ["who", "whose", "which", "whom"], "« Where » remplace un lieu.", 4),
    ("Quelle forme complète « She is good at ___ » ?", "cooking", ["cook", "to cook", "cooked", "cooks"], "Après une préposition, le verbe prend la forme en -ing.", 4),
    ("Quelle forme complète « I look forward to ___ you » ?", "seeing", ["see", "to see", "saw", "seen"], "Dans « look forward to », « to » est une préposition : on emploie le -ing.", 5),
    ("Quelle expression anglaise signifie « avoir hâte de » ?", "look forward to", ["look after", "look for", "look at", "look up"], "« I look forward to your reply » = « J'attends votre réponse avec impatience ».", 4),
    ("Que veut dire « I had my hair cut » ?", "Je me suis fait couper les cheveux", ["J'ai coupé les cheveux de quelqu'un", "Je coupe mes cheveux moi-même", "Je n'ai pas de cheveux", "Mes cheveux étaient longs"], "« Have + objet + participe » indique qu'on fait faire quelque chose.", 5),
    ("Quelle phrase est correcte ?", "I have lived in Bamenda since 2015", ["I live in Bamenda since 2015", "I am living in Bamenda since 2015", "I lived in Bamenda since 2015", "I have lived in Bamenda for 2015"], "Avec « since » et un état qui continue, on emploie le present perfect.", 4),
    ("Quel mot complète « Neither Awa ___ Kofi came » ?", "nor", ["or", "and", "but", "neither"], "« Neither... nor » veut dire « ni... ni ».", 4),
    ("Quel mot complète « She is old ___ to vote » ?", "enough", ["too", "very", "so", "much"], "« Enough » se place après l'adjectif.", 4),
    ("Que veut dire « I'd rather stay » ?", "Je préférerais rester", ["Je devrais rester", "J'ai déjà resté", "Je ne reste pas", "Je suis resté"], "« Would rather » exprime une préférence.", 5),
    ("Quelle forme complète « He ___ me a story last night » ?", "told", ["said", "spoke", "talked", "tell"], "On dit « tell someone a story ».", 4),
    ("Quelle est la bonne orthographe de « gouvernement » en anglais ?", "government", ["goverment", "govenment", "governmant", "governement"], "Le « n » se prononce peu mais s'écrit.", 4),
    ("Quelle est la bonne orthographe de « environnement » en anglais ?", "environment", ["enviroment", "environement", "envirnoment", "environmant"], "Il faut écrire « -iron- ».", 4),
    ("Quelle est la bonne orthographe de « recevoir » en anglais ?", "receive", ["recieve", "receeve", "recive", "receve"], "Règle : « i » avant « e » sauf après « c ».", 4),
    ("Quelle est la bonne orthographe de « adresse » en anglais ?", "address", ["adress", "addres", "adresse", "addresss"], "« Address » s'écrit avec deux « d » et deux « s ».", 4),
    ("Quelle est la bonne orthographe de « connaissance (savoir) » en anglais ?", "knowledge", ["knowlege", "nollege", "knoledge", "knowlidge"], "Le « k » initial ne se prononce pas.", 4),
    ("Quel est le contraire du verbe « succeed » ?", "fail", ["win", "start", "finish", "learn"], "« Succeed » veut dire « réussir » et « fail » « échouer ».", 4),
], "pca-en-gram", "Anglais")

# ============================================================ INFORMATIQUE
I_ = "Informatique"


def info(course, tpl, rows, src="pca-info-mat"):
    add(course, tpl, rows, src, I_)


# ---------------------------------------------------------------- CE2
dpairs("ce2", "pca-ce2-info-materiel", [
    ("l'écran", "Afficher les images et les textes"), ("le clavier", "Écrire des lettres et des chiffres"), ("la souris", "Déplacer le pointeur et cliquer"),
    ("l'imprimante", "Imprimer un document sur papier"), ("la clé USB", "Transporter et copier des fichiers"), ("le haut-parleur", "Diffuser les sons"),
    ("le microphone", "Enregistrer la voix"), ("la webcam", "Filmer pour les appels vidéo"), ("le scanner", "Numériser un document papier"),
    ("l'unité centrale", "Traiter les informations de l'ordinateur"),
], "À quoi sert %s ?" % "{a}", "Quel élément de l'informatique sert à {b} ?", "pca-info-mat", I_, W, 1, "Rôle de {a} : {low}.")
info("ce2", "pca-ce2-info", [
    ("Quelle touche efface le caractère situé à gauche du curseur ?", "Retour arrière", ["Entrée", "Tabulation", "Verrouillage majuscule", "Échap"], "La touche Suppr efface le caractère situé à droite du curseur.", 2),
    ("Quelle touche du clavier permet d'aller à la ligne ?", "Entrée", ["Échap", "Tabulation", "Alt", "Suppr"], "La touche Entrée valide aussi une commande.", 1),
    ("Quelle grande touche du clavier sert à écrire un espace entre deux mots ?", "La barre d'espace", ["La touche Échap", "La touche Alt", "La touche Suppr", "La touche Entrée"], "C'est la plus longue touche du clavier.", 1),
    ("Quelle touche, maintenue enfoncée, permet d'écrire une lettre en majuscule ?", "Majuscule (Shift)", ["Tabulation", "Échap", "Suppr", "Alt"], "On peut aussi activer le verrouillage majuscule.", 2),
    ("Comment appelle-t-on l'action de cliquer deux fois très vite sur un élément ?", "Un double-clic", ["Un clic droit", "Un glisser", "Un survol", "Un défilement"], "Le double-clic ouvre un fichier ou un dossier.", 1),
    ("Quel bouton de la souris ouvre en général un petit menu d'options ?", "Le bouton droit", ["Le bouton gauche", "Le double-clic gauche", "La molette", "Aucun bouton"], "C'est le menu contextuel.", 2),
    ("Comment s'appelle la petite flèche qui bouge à l'écran quand on déplace la souris ?", "Le pointeur", ["L'icône", "Le dossier", "Le fichier", "Le menu"], "On l'appelle aussi curseur de la souris.", 1),
    ("Comment doit-on éteindre correctement un ordinateur ?", "Avec la commande Arrêter du système", ["En débranchant le câble", "En fermant seulement l'écran", "En appuyant sur Échap", "En retirant le clavier"], "Cela permet de fermer les programmes proprement.", 1),
    ("Pourquoi faut-il enregistrer son travail ?", "Pour ne pas le perdre", ["Pour l'effacer", "Pour changer la couleur de l'écran", "Pour éteindre l'ordinateur", "Pour imprimer plus vite"], "Un travail non enregistré disparaît en cas de coupure de courant.", 1),
    ("À quoi sert un mot de passe ?", "À protéger l'accès à un compte", ["À accélérer l'ordinateur", "À colorier l'écran", "À imprimer un document", "À agrandir les lettres"], "Il doit rester secret.", 1),
    ("Comment appelle-t-on un petit dessin qui représente un programme ou un fichier ?", "Une icône", ["Un pointeur", "Un câble", "Un clavier", "Un écran"], "On clique sur l'icône pour ouvrir le programme.", 1),
    ("Quel périphérique permet d'écouter de la musique sans déranger les autres ?", "Un casque", ["Une imprimante", "Un scanner", "Une souris", "Une clé USB"], "Il se branche sur la sortie audio.", 1),
    ("Comment appelle-t-on un écran sur lequel on agit avec le doigt ?", "Un écran tactile", ["Un clavier", "Une imprimante", "Un scanner", "Un casque"], "Les tablettes et les téléphones en possèdent.", 1),
    ("Où range-t-on plusieurs fichiers pour s'y retrouver ?", "Dans un dossier", ["Dans un câble", "Dans la souris", "Dans l'imprimante", "Dans le clavier"], "Un dossier regroupe des fichiers et d'autres dossiers.", 1),
    ("Lequel de ces éléments est un logiciel ?", "Un traitement de texte", ["Un écran", "Un clavier", "Une souris", "Une imprimante"], "Un logiciel est un programme, on ne peut pas le toucher.", 2),
    ("Lequel de ces éléments fait partie du matériel d'un ordinateur ?", "Le clavier", ["Un traitement de texte", "Un jeu vidéo", "Un navigateur", "Un antivirus"], "Le matériel est ce que l'on peut toucher.", 2),
], "pca-info-mat")

# ---------------------------------------------------------------- CM1
dpairs("cm1", "pca-cm1-info-raccourci", [
    ("Ctrl + C", "Copier"), ("Ctrl + V", "Coller"), ("Ctrl + X", "Couper"), ("Ctrl + Z", "Annuler la dernière action"),
    ("Ctrl + S", "Enregistrer"), ("Ctrl + A", "Tout sélectionner"), ("Ctrl + P", "Imprimer"), ("Ctrl + F", "Rechercher"),
], "Que fait le raccourci clavier {a} ?", "Quel raccourci clavier permet de {b} ?", "pca-info-mat", I_, W, 2, "Le raccourci {a} permet de {low}.")
dpairs("cm1", "pca-cm1-info-extension", [
    (".jpg", "Une image"), (".mp3", "Un son ou une musique"), (".docx", "Un document de traitement de texte"), (".xlsx", "Un classeur de tableur"),
    (".mp4", "Une vidéo"), (".txt", "Un texte brut sans mise en forme"), (".pptx", "Une présentation en diapositives"), (".zip", "Une archive compressée"),
], "Quel type de fichier porte l'extension « {a} » ?", None, "pca-info-mat", I_, W, 3, "L'extension {a} désigne : {low}.")
info("cm1", "pca-cm1-info", [
    ("Que veut dire « logiciel » ?", "Un programme qui fonctionne sur un ordinateur", ["Une pièce de l'ordinateur", "Un câble", "Un écran", "Une prise électrique"], "Un logiciel ne se touche pas : c'est une suite d'instructions.", 1),
    ("Quel élément est à la fois un écran et un moyen de commande avec le doigt ?", "Un écran tactile", ["Un clavier", "Une imprimante", "Un scanner", "Une souris"], "Il sert de périphérique d'entrée et de sortie.", 2),
    ("Que contient l'unité centrale d'un ordinateur ?", "Le processeur et la mémoire", ["Seulement l'écran", "Seulement le clavier", "Les haut-parleurs seulement", "Le papier de l'imprimante"], "Le processeur effectue les calculs.", 2),
    ("Combien de bits y a-t-il dans un octet ?", "8", ["4", "10", "16", "2"], "1 octet = 8 bits.", 2),
    ("Que veut dire « télécharger » un fichier ?", "Le copier depuis Internet vers son appareil", ["L'envoyer vers Internet", "Le supprimer", "L'imprimer", "Le renommer"], "Envoyer un fichier vers Internet se dit « téléverser ».", 3),
    ("Quel logiciel permet de naviguer sur Internet ?", "Un navigateur", ["Un traitement de texte", "Un tableur", "Un antivirus", "Un lecteur de musique"], "Chrome et Firefox sont des navigateurs.", 1),
    ("Lequel de ces logiciels est un navigateur web ?", "Mozilla Firefox", ["Microsoft Word", "Microsoft Excel", "Paint", "Adobe Reader"], "Un navigateur affiche les pages web.", 2),
    ("À quoi sert un moteur de recherche ?", "À trouver des pages web grâce à des mots-clés", ["À écrire des lettres", "À faire des calculs", "À imprimer des pages", "À éteindre l'ordinateur"], "Google et Bing sont des moteurs de recherche.", 2),
    ("Comment appelle-t-on un message envoyé par Internet ?", "Un e-mail (ou courriel)", ["Un fichier PDF", "Une fenêtre", "Un virus", "Un dossier"], "Une adresse e-mail contient le signe @.", 1),
    ("Que sépare le signe @ dans une adresse e-mail ?", "Le nom de l'utilisateur et le nom du serveur de messagerie", ["Le mot de passe et le nom", "L'objet et le message", "Le jour et l'heure", "Le prénom et le nom de famille"], "Exemple : nom@exemple.com.", 3),
    ("Peut-on donner son mot de passe à un camarade ?", "Non, il doit rester secret", ["Oui, à tout le monde", "Oui, s'il le demande poliment", "Oui, par e-mail", "Oui, sur un réseau social"], "Un mot de passe est personnel.", 1),
    ("Que faut-il faire si un inconnu te demande ton adresse sur Internet ?", "Ne pas répondre et prévenir un adulte", ["Lui donner mon adresse", "Lui envoyer ma photo", "Lui donner mon numéro", "Lui dire où est mon école"], "On ne donne jamais d'informations personnelles à un inconnu.", 1),
    ("Que veut dire « WWW » dans une adresse de site ?", "World Wide Web", ["World Wide Window", "Web World Wire", "Wide Web World", "Wireless World Web"], "C'est le nom anglais de la « toile mondiale ».", 3),
    ("Quelle opération permet de déplacer un texte d'un endroit à un autre ?", "Couper puis coller", ["Copier puis enregistrer", "Imprimer puis fermer", "Annuler puis éteindre", "Renommer puis copier"], "Couper enlève le texte, coller le replace ailleurs.", 2),
    ("Comment appelle-t-on les fichiers et dossiers rangés sur l'ordinateur pour les retrouver ?", "Une arborescence", ["Un virus", "Un pointeur", "Une icône", "Un réseau"], "Elle représente les dossiers contenus dans d'autres dossiers.", 4),
    ("Quel appareil permet de stocker des fichiers et de les emporter facilement ?", "Une clé USB", ["Une souris", "Un clavier", "Un haut-parleur", "Un écran"], "Elle se branche sur un port USB.", 1),
], "pca-info-mat")

# ---------------------------------------------------------------- CM2
info("cm2", "pca-cm2-info", [
    ("Quelle unité de stockage est la plus grande ?", "Le gigaoctet (Go)", ["Le kilooctet (Ko)", "L'octet", "Le mégaoctet (Mo)", "Le bit"], "Ordre croissant : octet, Ko, Mo, Go.", 2),
    ("Quelle unité vient juste après le mégaoctet (Mo) ?", "Le gigaoctet (Go)", ["Le kilooctet (Ko)", "L'octet", "Le bit", "Le pixel"], "Mo puis Go puis To.", 3),
    ("Quels chiffres utilise-t-on en binaire ?", "0 et 1", ["0 à 9", "1 et 2", "0, 1 et 2", "Les lettres A et B"], "Un ordinateur code l'information avec deux états.", 2),
    ("Que vaut le nombre binaire 101 en écriture décimale ?", "5", ["4", "6", "7", "3"], "1 × 4 + 0 × 2 + 1 × 1 = 5.", 4),
    ("Que vaut le nombre binaire 11 en écriture décimale ?", "3", ["2", "4", "11", "1"], "1 × 2 + 1 × 1 = 3.", 3),
    ("Que vaut le nombre binaire 1000 en écriture décimale ?", "8", ["4", "10", "16", "1000"], "Le 1 est à la place des huit.", 4),
    ("Que vaut le nombre binaire 110 en écriture décimale ?", "6", ["4", "5", "7", "3"], "1 × 4 + 1 × 2 + 0 × 1 = 6.", 4),
    ("Que veut dire « formater » un texte ?", "Modifier sa présentation (police, taille, couleur)", ["Effacer tout le texte", "L'imprimer", "L'envoyer par e-mail", "Le traduire"], "Le formatage ne change pas le sens des mots.", 2),
    ("Qu'appelle-t-on la « police de caractères » ?", "Le style des lettres du texte", ["Le service de sécurité", "La taille de l'écran", "Le type de fichier", "La couleur du fond"], "Arial et Times New Roman sont des polices.", 2),
    ("Comment appelle-t-on l'intersection d'une ligne et d'une colonne dans un tableur ?", "Une cellule", ["Un dossier", "Une icône", "Un pointeur", "Un onglet"], "Une cellule peut contenir du texte, un nombre ou une formule.", 2),
    ("Dans un tableur, comment les colonnes sont-elles repérées ?", "Par des lettres", ["Par des numéros", "Par des couleurs", "Par des symboles", "Par des noms d'animaux"], "Les lignes sont repérées par des numéros.", 2),
    ("Dans un tableur, comment les lignes sont-elles repérées ?", "Par des numéros", ["Par des lettres", "Par des couleurs", "Par des symboles", "Par des dates"], "Colonne B et ligne 3 donnent la cellule B3.", 2),
    ("Quelle est la cellule située dans la colonne B, à la ligne 3 ?", "B3", ["3B", "BB3", "C3", "B33"], "On écrit d'abord la lettre de la colonne, puis le numéro de la ligne.", 2),
    ("Quel résultat donne la formule =2+3 dans un tableur ?", "5", ["23", "2+3", "6", "1"], "Le tableur calcule le résultat de la formule.", 2),
    ("Quel signe commence en général une formule dans un tableur ?", "Le signe égal (=)", ["Le dièse (#)", "Le dollar ($)", "L'arobase (@)", "L'esperluette (&)"], "Sans ce signe, le tableur lit un simple texte.", 3),
    ("Que calcule la formule =SOMME(A1:A3) dans un tableur en français ?", "La somme des cellules A1, A2 et A3", ["Le produit de A1, A2 et A3", "La moyenne de A1, A2 et A3", "Le plus grand nombre de A1 à A3", "Le nombre de lettres de A1"], "A1:A3 désigne une plage de trois cellules.", 3),
    ("Qu'est-ce qu'un virus informatique ?", "Un programme malveillant", ["Un logiciel de dessin", "Une pièce de l'ordinateur", "Un moteur de recherche", "Un type de fichier image"], "Il peut abîmer des fichiers ou voler des informations.", 2),
    ("À quoi sert un antivirus ?", "À détecter et supprimer des programmes malveillants", ["À accélérer Internet", "À écrire des lettres", "À faire des calculs", "À imprimer plus vite"], "Il doit être mis à jour régulièrement.", 2),
    ("Que doit contenir un mot de passe solide ?", "Des lettres, des chiffres et des symboles", ["Ton prénom seul", "Ta date de naissance", "Le mot 1234", "Le nom de ton école"], "Un mot de passe long et varié est plus difficile à deviner.", 2),
    ("Que fais-tu si un e-mail inconnu te demande ton mot de passe ?", "Je ne réponds pas et je le supprime", ["Je donne mon mot de passe", "Je clique sur le lien", "Je le transfère à tous mes amis", "Je réponds avec mon adresse"], "Personne de sérieux ne demande un mot de passe par e-mail.", 2),
    ("Que désigne le Wi-Fi ?", "Une connexion sans fil à un réseau", ["Un câble réseau", "Une clé USB", "Un écran", "Un logiciel de dessin"], "Les ordinateurs, tablettes et téléphones s'y connectent.", 2),
    ("Qu'est-ce qu'un algorithme ?", "Une suite d'instructions pour résoudre un problème", ["Un type d'ordinateur", "Une marque de téléphone", "Un virus", "Un fichier image"], "Il décrit les étapes à suivre dans l'ordre.", 3),
    ("Quelle activité de la vie quotidienne ressemble à un algorithme ?", "Suivre une recette de cuisine", ["Regarder un film", "Courir dans la cour", "Dessiner librement", "Dormir"], "Une recette est une suite d'étapes ordonnées.", 3),
    ("Que veut dire USB ?", "Universal Serial Bus", ["Universal Server Block", "Unified Serial Box", "United System Bus", "Universal Storage Bar"], "C'est une norme de connexion pour les périphériques.", 4),
    ("Qu'est-ce qu'un bit ?", "Le plus petit élément d'information, qui vaut 0 ou 1", ["Un gros fichier", "Une partie du clavier", "Un type d'écran", "Un virus"], "Huit bits forment un octet.", 3),
    ("Qu'est-ce qu'un pixel ?", "Le plus petit point d'une image à l'écran", ["Un type de souris", "Un logiciel de calcul", "Un câble", "Une touche du clavier"], "Une image est formée de nombreux pixels.", 3),
    ("Quel périphérique permet d'envoyer un document papier vers l'ordinateur ?", "Le scanner", ["L'imprimante", "Le haut-parleur", "Le casque", "L'écran"], "Il numérise le document.", 2),
    ("Comment appelle-t-on la copie de sécurité de ses fichiers ?", "Une sauvegarde", ["Un virus", "Un pointeur", "Une icône", "Un clic"], "Elle permet de retrouver ses fichiers en cas de panne.", 3),
    ("Que fait le raccourci clavier Ctrl + Z ?", "Il annule la dernière action", ["Il copie", "Il colle", "Il enregistre", "Il imprime"], "Il sert à corriger une erreur.", 2),
    ("Que fait le raccourci clavier Ctrl + S ?", "Il enregistre le document", ["Il supprime le document", "Il ferme Internet", "Il éteint l'ordinateur", "Il imprime le document"], "Pensez à l'utiliser souvent.", 2),
    ("Qu'est-ce que la mémoire de stockage (disque dur ou SSD) ?", "L'endroit où les fichiers sont conservés même éteint", ["Un écran", "Un virus", "Un clavier", "Un câble de souris"], "Elle garde les données quand l'ordinateur est éteint.", 3),
    ("Qu'est-ce qu'un réseau informatique ?", "Des appareils reliés pour échanger des informations", ["Un seul ordinateur isolé", "Un type de clavier", "Un logiciel de dessin", "Une marque d'écran"], "Internet est un réseau de réseaux.", 3),
    ("Quelle touche supprime le caractère situé à droite du curseur ?", "La touche Suppr", ["La touche Entrée", "La touche Échap", "La touche Tabulation", "La barre d'espace"], "Retour arrière efface le caractère situé à gauche.", 2),
    ("Quel logiciel sert à créer des tableaux de calcul ?", "Un tableur", ["Un navigateur", "Un antivirus", "Un lecteur vidéo", "Un logiciel de dessin"], "Excel et LibreOffice Calc sont des tableurs.", 2),
    ("Quel logiciel sert à écrire et mettre en forme une lettre ?", "Un traitement de texte", ["Un tableur", "Un navigateur", "Un antivirus", "Un lecteur de musique"], "Word et LibreOffice Writer sont des traitements de texte.", 2),
    ("Comment appelle-t-on l'image affichée en arrière-plan du bureau de l'ordinateur ?", "Le fond d'écran", ["Le pointeur", "Le dossier", "La corbeille", "La police"], "On peut le personnaliser avec une photo.", 1),
    ("Quelle est l'extension habituelle d'un document de traitement de texte Word récent ?", ".docx", [".xlsx", ".mp3", ".jpg", ".zip"], "L'extension se trouve à la fin du nom du fichier.", 3),
], "pca-info-algo")

# ---------------------------------------------------------------- 6e
info("6e", "pca-6e-info", [
    ("Quel composant effectue les calculs et exécute les instructions d'un ordinateur ?", "Le processeur", ["Le disque dur", "L'écran", "La souris", "Le clavier"], "Le processeur est le « cerveau » de l'ordinateur.", 2),
    ("Quelle mémoire est vidée quand l'ordinateur est éteint ?", "La mémoire vive (RAM)", ["Le disque dur", "La clé USB", "Le disque SSD", "La carte mémoire"], "La RAM sert au travail en cours.", 3),
    ("Où les fichiers sont-ils conservés après l'extinction de l'ordinateur ?", "Sur le disque de stockage", ["Dans la mémoire vive", "Dans l'écran", "Dans le clavier", "Dans la souris"], "Le disque dur ou SSD garde les données.", 3),
    ("Quel est le rôle d'un système d'exploitation ?", "Gérer le matériel et permettre de lancer des logiciels", ["Dessiner des images", "Naviguer sur Internet seulement", "Faire des calculs de tableur", "Imprimer les documents"], "Windows, Linux, macOS et Android sont des systèmes d'exploitation.", 3),
    ("Lequel de ces éléments est un système d'exploitation ?", "Linux", ["Excel", "Chrome", "Photoshop", "Word"], "Linux est un système d'exploitation libre.", 3),
    ("Que veut dire PC ?", "Personal Computer", ["Public Computer", "Portable Card", "Private Console", "Power Cable"], "Ce mot anglais signifie « ordinateur personnel ».", 3),
    ("Que désigne l'URL d'une page web ?", "Son adresse", ["Son auteur", "Son prix", "Sa couleur", "Sa police"], "Exemple : https://www.exemple.com.", 3),
    ("Qu'est-ce qu'Internet ?", "Un réseau mondial qui relie des réseaux d'ordinateurs", ["Un logiciel de dessin", "Un seul gros ordinateur", "Un moteur de recherche", "Un type de clavier"], "Le Web n'est qu'un des services d'Internet.", 3),
    ("Quel est le lien entre Internet et le Web ?", "Le Web est un service qui fonctionne sur Internet", ["Le Web et Internet sont des marques de téléphone", "Internet est un service du Web", "Ils n'ont aucun rapport", "Le Web est le câble d'Internet"], "L'e-mail est un autre service d'Internet.", 4),
    ("Qui a inventé le Web (World Wide Web) au CERN à la fin des années 1980 ?", "Tim Berners-Lee", ["Bill Gates", "Steve Jobs", "Alan Turing", "Mark Zuckerberg"], "Il a proposé le Web en 1989.", 4),
    ("Que veut dire « CC » dans un e-mail ?", "Copie carbone : une copie visible pour une autre personne", ["Clôture complète", "Contrôle de contenu", "Clic cassé", "Compression de chiffres"], "Cci signifie « copie cachée ».", 4),
    ("Qu'est-ce qu'une pièce jointe dans un e-mail ?", "Un fichier envoyé avec le message", ["Un mot de passe", "Un virus obligatoire", "Le titre du message", "Un nouvel écran"], "Une photo ou un document peut être joint à un message.", 2),
    ("Qu'appelle-t-on un spam ?", "Un message non sollicité envoyé en grand nombre", ["Un message de ton enseignant", "Un fichier image", "Une sauvegarde", "Un document Word"], "Il faut éviter de répondre aux spams.", 3),
    ("Que faire en cas de harcèlement en ligne ?", "En parler à un adulte de confiance", ["Répondre par des insultes", "Cacher le problème", "Partager le message à tous", "Supprimer son téléphone sans rien dire"], "On peut aussi conserver les preuves et bloquer la personne.", 2),
    ("Une information trouvée sur Internet est-elle toujours vraie ?", "Non, il faut vérifier la source", ["Oui, toujours", "Oui, si elle est sur une belle page", "Oui, si elle est courte", "Oui, si beaucoup de gens la lisent"], "Toute personne peut publier sur Internet.", 2),
    ("Laquelle de ces informations faut-il garder secrète ?", "Son mot de passe", ["Son prénom en classe", "Le nom de son école sur sa carte d'élève", "Sa couleur préférée", "Son sport préféré"], "Un mot de passe ne se partage pas.", 1),
    ("Quel est l'intérêt de copier-coller le texte d'un site sans le dire ?", "Aucun : c'est du plagiat", ["C'est toujours autorisé", "Cela améliore la note", "C'est une sauvegarde", "C'est une règle de politesse"], "Il faut citer ses sources.", 3),
    ("Quel signe sert à la multiplication dans une formule de tableur ?", "L'astérisque (*)", ["La lettre x", "Le signe ×", "Le point", "Le pourcentage (%)"], "Le tableur n'accepte pas la lettre x.", 3),
    ("Quel signe sert à la division dans une formule de tableur ?", "La barre oblique (/)", ["Le signe ÷", "Le double point (:)", "L'antislash (\\)", "Le pourcentage (%)"], "Exemple : =12/4 donne 3.", 3),
    ("Quel résultat donne =3*4 dans un tableur ?", "12", ["7", "34", "3*4", "1"], "L'étoile (*) multiplie.", 2),
    ("Que fait une « boucle » dans un algorithme ?", "Elle répète des instructions", ["Elle arrête le programme", "Elle efface les variables", "Elle change la police", "Elle ouvre Internet"], "On évite ainsi de recopier plusieurs fois les mêmes instructions.", 3),
    ("Qu'est-ce que Scratch ?", "Un langage de programmation visuel avec des blocs", ["Un navigateur web", "Un tableur", "Un antivirus", "Un système d'exploitation"], "On assemble des blocs pour créer des animations et des jeux.", 3),
    ("Qu'est-ce qu'un « bug » dans un programme ?", "Une erreur qui l'empêche de fonctionner comme prévu", ["Un virus utile", "Un nouvel écran", "Une touche du clavier", "Une mise à jour obligatoire"], "Corriger les bugs s'appelle déboguer.", 2),
    ("Qu'est-ce qu'une variable dans un programme ?", "Un nom qui permet de stocker une valeur", ["Un câble", "Un fichier PDF", "Un écran", "Une marque d'ordinateur"], "La valeur de la variable peut changer.", 3),
    ("Dans un algorithme, l'ordre des instructions est-il important ?", "Oui, elles s'exécutent les unes après les autres", ["Non, jamais", "Seulement pour la dernière", "Seulement avec Internet", "Seulement avec un tableur"], "Une séquence est une suite d'instructions dans un ordre précis.", 2),
    ("Combien de bits forment un octet ?", "8", ["4", "10", "16", "32"], "1 octet = 8 bits.", 2),
    ("Que veut dire « Ko » ?", "Kilooctet", ["Kilobit seulement", "Kilogramme", "Kilomètre", "Kilowatt"], "C'est une unité de taille de fichier.", 3),
    ("Que fait le raccourci Ctrl + A ?", "Il sélectionne tout le contenu", ["Il annule", "Il ferme la fenêtre", "Il enregistre", "Il imprime"], "Il sert à tout sélectionner avant de copier.", 2),
    ("À quoi sert un pare-feu (firewall) ?", "À filtrer les connexions réseau pour protéger l'ordinateur", ["À refroidir l'ordinateur", "À augmenter la taille de l'écran", "À écrire des courriels", "À changer le fond d'écran"], "Il peut être un logiciel ou un matériel.", 4),
    ("Que fait la commande « Enregistrer sous » ?", "Elle enregistre le document avec un autre nom ou à un autre endroit", ["Elle imprime le document", "Elle supprime le document", "Elle ferme Internet", "Elle copie le document sur l'écran"], "On garde ainsi le document d'origine.", 3),
    ("Quel est le rôle de la corbeille ?", "Conserver les fichiers supprimés jusqu'à ce qu'on la vide", ["Imprimer les fichiers", "Compresser les fichiers", "Envoyer les fichiers par e-mail", "Protéger du virus"], "On peut y récupérer un fichier supprimé par erreur.", 2),
    ("Quel dispositif permet d'accéder à un réseau Wi-Fi ?", "Un routeur ou une box", ["Une souris", "Un scanner", "Un clavier", "Une imprimante"], "Le routeur relie les appareils au réseau.", 3),
    ("Que veut dire « Mo » dans la taille d'un fichier ?", "Mégaoctet", ["Millioctet", "Mégabit seulement", "Mégamètre", "Mégaohm"], "Un mégaoctet est plus grand qu'un kilooctet.", 3),
    ("Windows est un exemple de quoi ?", "Un système d'exploitation", ["Un tableur", "Un navigateur", "Un antivirus", "Un moteur de recherche"], "Il gère le matériel et lance les logiciels.", 2),
    ("Lequel de ces périphériques est un périphérique d'entrée ?", "Le clavier", ["L'imprimante", "Le haut-parleur", "Le vidéoprojecteur", "Le casque"], "Un périphérique d'entrée envoie des informations à l'ordinateur.", 3),
    ("Lequel de ces périphériques est un périphérique de sortie ?", "L'imprimante", ["Le clavier", "La souris", "Le microphone", "Le scanner"], "Un périphérique de sortie restitue des informations.", 3),
], "pca-info-net")

# ---------------------------------------------------------------- 5e
info("5e", "pca-5e-info", [
    ("Combien de valeurs différentes peut-on coder avec 8 bits ?", "256", ["8", "16", "64", "128"], "2 puissance 8 vaut 256.", 4),
    ("Combien de valeurs différentes peut-on coder avec 3 bits ?", "8", ["3", "6", "9", "16"], "2 puissance 3 vaut 8.", 4),
    ("Combien de valeurs différentes peut-on coder avec 4 bits ?", "16", ["4", "8", "12", "32"], "2 puissance 4 vaut 16.", 4),
    ("Que vaut le nombre binaire 1010 en écriture décimale ?", "10", ["8", "12", "5", "1010"], "8 + 0 + 2 + 0 = 10.", 4),
    ("Que vaut le nombre binaire 1111 en écriture décimale ?", "15", ["4", "8", "14", "16"], "8 + 4 + 2 + 1 = 15.", 4),
    ("Comment s'écrit le nombre 9 en binaire ?", "1001", ["1010", "1000", "1101", "0111"], "9 = 8 + 1.", 4),
    ("Comment s'écrit le nombre 12 en binaire ?", "1100", ["1010", "1001", "1110", "1000"], "12 = 8 + 4.", 4),
    ("Quel nombre représente la lettre « A » majuscule dans le code ASCII ?", "65", ["1", "64", "97", "48"], "Le « a » minuscule vaut 97.", 5),
    ("Que fait une adresse IP ?", "Elle identifie un appareil sur un réseau", ["Elle protège un fichier", "Elle colorie une page", "Elle calcule une moyenne", "Elle compresse une image"], "Chaque appareil connecté à un réseau en possède une.", 4),
    ("Quel est le rôle du DNS ?", "Traduire un nom de site en adresse IP", ["Protéger contre les virus", "Imprimer les pages", "Écrire du code", "Compresser les vidéos"], "On tape un nom, le DNS retrouve l'adresse numérique.", 5),
    ("Que signifie le « S » de HTTPS ?", "Sécurisé", ["Simple", "Serveur", "Site", "Standard"], "HTTPS chiffre les échanges entre le navigateur et le site.", 4),
    ("Qu'est-ce qu'un cookie sur le web ?", "Un petit fichier déposé par un site dans le navigateur", ["Un virus destructeur", "Un type d'écran", "Un câble réseau", "Un document Word"], "Il peut mémoriser des préférences ou suivre la navigation.", 4),
    ("Qu'est-ce que l'hameçonnage (phishing) ?", "Une tentative de voler des informations en imitant un message de confiance", ["Une sauvegarde automatique", "Un jeu en ligne", "Une mise à jour obligatoire", "Un antivirus gratuit"], "Il faut se méfier des liens reçus par message.", 3),
    ("Que fait la double authentification ?", "Elle demande une seconde preuve d'identité en plus du mot de passe", ["Elle double la vitesse d'Internet", "Elle copie le compte", "Elle supprime le mot de passe", "Elle ouvre deux fenêtres"], "Par exemple un code reçu sur un téléphone.", 4),
    ("Quel format d'image est compressé avec perte de qualité ?", "JPEG", ["TXT", "ZIP", "XLSX", "PDF"], "Le JPEG réduit la taille en perdant des détails.", 5),
    ("Quel format d'image permet de gérer la transparence ?", "PNG", ["JPEG", "MP3", "TXT", "DOCX"], "Le PNG conserve les détails sans perte.", 5),
    ("Que signifie RVB pour une couleur à l'écran ?", "Rouge, vert, bleu", ["Rose, violet, blanc", "Rouge, violet, blanc", "Rond, vide, brillant", "Réseau, vidéo, bit"], "Chaque pixel mélange ces trois lumières.", 3),
    ("Que désigne la résolution d'une image ?", "Le nombre de pixels qui la compose", ["Le nom du fichier", "Le poids du papier", "La vitesse d'Internet", "La couleur de l'écran"], "Plus il y a de pixels, plus l'image est détaillée.", 4),
    ("Que veut dire LAN ?", "Réseau local", ["Réseau mondial", "Logiciel antivirus", "Langage de programmation", "Lecteur de mémoire"], "LAN vient de Local Area Network.", 5),
    ("Dans un tableur, combien de cellules contient la plage A1:A4 ?", "4", ["1", "2", "3", "5"], "A1, A2, A3 et A4.", 3),
    ("Dans un tableur, combien de cellules contient la plage B2:C3 ?", "4", ["2", "3", "6", "9"], "Deux colonnes sur deux lignes : B2, B3, C2, C3.", 4),
    ("Que calcule =MOYENNE(2;4;6) dans un tableur en français ?", "4", ["12", "6", "2", "3"], "(2 + 4 + 6) / 3 = 4.", 3),
    ("Que calcule =MAX(3;9;5) dans un tableur en français ?", "9", ["3", "5", "17", "8"], "MAX donne la plus grande valeur.", 3),
    ("Quel graphique convient le mieux pour montrer des proportions d'un total ?", "Un diagramme circulaire (camembert)", ["Un nuage de points", "Un histogramme de dates", "Un tableau de nombres brouillon", "Un diagramme de classes"], "Chaque part représente une proportion du total.", 4),
    ("Que signifie une condition « si, alors, sinon » dans un algorithme ?", "On choisit des instructions selon que la condition est vraie ou fausse", ["On répète toujours trois fois", "On arrête le programme", "On efface les données", "On change la police"], "C'est une structure conditionnelle.", 4),
    ("Que fait une boucle « tant que » ?", "Elle répète des instructions tant que la condition est vraie", ["Elle les exécute une seule fois", "Elle efface le programme", "Elle ouvre une page web", "Elle enregistre le fichier"], "Il faut que la condition finisse par devenir fausse.", 4),
    ("Qu'est-ce que Python ?", "Un langage de programmation", ["Un navigateur web", "Un tableur", "Un système d'exploitation", "Un moteur de recherche"], "Il est très utilisé pour apprendre à programmer.", 3),
    ("Qu'affiche en Python l'instruction print(\"Bonjour\") ?", "Bonjour", ["print", "\"Bonjour\" avec les guillemets", "Rien", "Une erreur"], "La fonction print affiche le texte sur l'écran.", 3),
    ("Que fait un serveur sur un réseau ?", "Il fournit des services ou des pages à d'autres ordinateurs", ["Il imprime sur papier", "Il remplace le clavier", "Il sert d'écran", "Il protège du soleil"], "Les ordinateurs clients lui envoient des demandes.", 4),
    ("Qu'est-ce que le cloud (informatique en nuage) ?", "Des services et des données hébergés sur des serveurs distants via Internet", ["Un ordinateur sans écran", "Un câble très long", "Un type de souris", "Une clé USB géante"], "On y accède avec une connexion Internet.", 4),
    ("Qu'est-ce qu'un logiciel libre (open source) ?", "Un logiciel dont le code source est public", ["Un logiciel toujours gratuit pour les entreprises", "Un virus", "Un jeu vidéo", "Un logiciel sans mot de passe"], "Linux est un exemple de logiciel libre.", 5),
    ("Quelle extension désigne une archive compressée ?", ".zip", [".mp3", ".txt", ".jpg", ".docx"], "Compresser réduit la taille des fichiers.", 3),
    ("Que fait le raccourci Alt + Tab sous Windows ?", "Il change de fenêtre", ["Il ferme Internet", "Il enregistre le fichier", "Il imprime", "Il efface le texte"], "Il permet de passer d'une application ouverte à une autre.", 4),
    ("Combien d'octets y a-t-il dans 16 bits ?", "2", ["1", "4", "8", "16"], "8 bits forment 1 octet.", 4),
    ("Combien de bits y a-t-il dans 2 octets ?", "16", ["8", "2", "32", "4"], "2 × 8 = 16.", 4),
    ("Comment s'écrit le nombre 5 en binaire ?", "101", ["110", "11", "100", "111"], "5 = 4 + 1.", 4),
    ("Que désigne le débit d'une connexion Internet ?", "La quantité de données transmises par seconde", ["La couleur de l'écran", "Le poids du câble", "Le nombre de pixels", "La taille du clavier"], "Il s'exprime en bits par seconde.", 4),
], "pca-info-algo")

# ---------------------------------------------------------------- 4e
info("4e", "pca-4e-info", [
    ("Que signifie HTML ?", "HyperText Markup Language", ["High Text Machine Language", "Home Tool Mark Link", "Hyper Transfer Mail Link", "Hard Text Memory Language"], "C'est le langage qui décrit la structure des pages web.", 4),
    ("Quelle balise HTML crée un paragraphe ?", "<p>", ["<h1>", "<img>", "<a>", "<ul>"], "<p> signifie paragraph.", 4),
    ("Quelle balise HTML sert au titre principal d'une page ?", "<h1>", ["<p>", "<br>", "<li>", "<img>"], "<h1> est le niveau de titre le plus important.", 4),
    ("Quelle balise HTML sert à créer un lien hypertexte ?", "<a>", ["<p>", "<h1>", "<br>", "<ul>"], "On indique l'adresse avec l'attribut href.", 4),
    ("Quelle balise HTML sert à insérer une image ?", "<img>", ["<a>", "<p>", "<li>", "<h1>"], "L'attribut src donne le chemin de l'image.", 4),
    ("Quelle balise HTML crée une liste à puces ?", "<ul>", ["<p>", "<a>", "<img>", "<h1>"], "Chaque élément de la liste est placé dans un <li>.", 4),
    ("À quoi sert le CSS ?", "À décrire l'apparence des pages web", ["À stocker des mots de passe", "À envoyer des e-mails", "À protéger du virus", "À réparer le matériel"], "CSS signifie feuilles de style en cascade.", 4),
    ("Qu'ajoute en général le JavaScript à une page web ?", "De l'interactivité", ["Une couleur de fond seulement", "Un virus", "Du papier", "Une adresse IP"], "Il permet par exemple de réagir à un clic.", 5),
    ("Que fait en Python print(2 + 3) ?", "Il affiche 5", ["Il affiche 23", "Il affiche 2 + 3", "Il affiche 6", "Il n'affiche rien"], "L'addition est calculée avant l'affichage.", 3),
    ("Que renvoie en Python len(\"chat\") ?", "4", ["3", "5", "chat", "0"], "len donne le nombre de caractères.", 3),
    ("Que renvoie en Python 7 % 2 ?", "1", ["3", "0", "3,5", "2"], "% donne le reste de la division entière.", 4),
    ("Que renvoie en Python 7 // 2 ?", "3", ["1", "3,5", "4", "2"], "// donne le quotient de la division entière.", 4),
    ("Que renvoie en Python 2 ** 3 ?", "8", ["6", "5", "9", "23"], "** est l'opération puissance : 2 × 2 × 2.", 4),
    ("Comment appelle-t-on en programmation un nombre entier comme 12 ?", "Un entier (int)", ["Une chaîne (str)", "Un booléen (bool)", "Un flottant", "Une liste"], "Les décimaux sont des flottants.", 4),
    ("Quel type de valeur peut seulement être vrai ou faux ?", "Un booléen", ["Un entier", "Une chaîne de caractères", "Un flottant", "Une liste"], "True et False sont les deux valeurs possibles en Python.", 4),
    ("Quel est le résultat logique de VRAI ET FAUX ?", "FAUX", ["VRAI", "Vrai et faux à la fois", "On ne sait pas", "1"], "Avec ET, les deux conditions doivent être vraies.", 4),
    ("Quel est le résultat logique de VRAI OU FAUX ?", "VRAI", ["FAUX", "Vrai et faux à la fois", "On ne sait pas", "0"], "Avec OU, une seule condition vraie suffit.", 4),
    ("Quel est le résultat logique de NON VRAI ?", "FAUX", ["VRAI", "Aucun", "On ne sait pas", "1"], "NON inverse la valeur logique.", 4),
    ("Quelle valeur décimale représente le binaire 1101 ?", "13", ["11", "12", "14", "15"], "8 + 4 + 0 + 1 = 13.", 4),
    ("Quelle valeur décimale représente le binaire 10000 ?", "16", ["8", "10", "32", "10000"], "Le 1 est à la place des seize.", 4),
    ("Combien de nombres compose une adresse IPv4 ?", "Quatre nombres de 0 à 255", ["Deux nombres de 0 à 9", "Huit lettres", "Un nombre de 0 à 255", "Six nombres de 0 à 99"], "Exemple : 192.168.0.1.", 5),
    ("Que fait un navigateur quand on tape une adresse ?", "Il demande la page à un serveur", ["Il imprime la page", "Il installe un virus", "Il éteint l'ordinateur", "Il fabrique le site"], "Le serveur lui renvoie la page à afficher.", 4),
    ("Que fait la compression d'un fichier ?", "Elle réduit sa taille", ["Elle change son nom", "Elle le protège d'un virus", "Elle l'imprime", "Elle le supprime"], "Un fichier ZIP est compressé.", 3),
    ("Qu'est-ce que le Bluetooth ?", "Une liaison sans fil de courte portée", ["Un câble sous-marin", "Un système d'exploitation", "Un navigateur", "Un tableur"], "Il relie casques, enceintes et téléphones.", 4),
    ("Qu'est-ce qu'une base de données ?", "Un ensemble organisé de données que l'on peut consulter et modifier", ["Un virus", "Un moteur de recherche", "Un type d'écran", "Une image compressée"], "Les tableaux d'élèves ou de produits en sont des exemples.", 4),
    ("Qu'est-ce qu'une fonction en programmation ?", "Un bloc d'instructions réutilisable qui porte un nom", ["Une touche du clavier", "Un fichier image", "Un virus", "Un mot de passe"], "On l'appelle chaque fois que l'on en a besoin.", 5),
    ("Que fait la référence absolue $A$1 dans un tableur ?", "Elle reste fixe quand on recopie la formule", ["Elle se déplace toujours", "Elle supprime la cellule A1", "Elle colore la cellule A1", "Elle cache la ligne 1"], "Les signes $ bloquent la colonne et la ligne.", 5),
    ("Que fait =SI(A1>10;\"Admis\";\"Refusé\") quand A1 contient 12 ?", "Elle affiche Admis", ["Elle affiche Refusé", "Elle affiche 12", "Elle affiche 10", "Elle donne une erreur"], "12 est plus grand que 10 : la condition est vraie.", 5),
    ("Quel est le rôle d'une sauvegarde régulière ?", "Retrouver ses fichiers en cas de panne ou de perte", ["Accélérer Internet", "Éviter d'avoir un mot de passe", "Changer le fond d'écran", "Augmenter la taille de l'écran"], "Elle doit être placée sur un autre support.", 3),
    ("Quelle est la bonne pratique pour un mot de passe ?", "Utiliser un mot de passe différent pour chaque compte", ["Utiliser le même partout", "L'écrire sur l'écran", "Le donner à ses amis", "Choisir sa date de naissance"], "Si un compte est piraté, les autres restent protégés.", 3),
    ("Que fait un rançongiciel ?", "Il bloque des fichiers et réclame de l'argent pour les rendre", ["Il nettoie le disque", "Il accélère le processeur", "Il protège les données", "Il imprime des factures"], "Il vaut mieux avoir des sauvegardes hors ligne.", 5),
    ("Que garantit le chiffrement d'un message ?", "Que seules les personnes autorisées peuvent le lire", ["Qu'il arrive plus vite", "Qu'il est drôle", "Qu'il est plus petit", "Qu'il est toujours vrai"], "Le chiffrement rend le message illisible sans la clé.", 5),
    ("Quel est un risque du partage de photos personnelles en ligne ?", "Elles peuvent être copiées et diffusées sans contrôle", ["Elles perdent leurs couleurs", "Elles ralentissent l'écran", "Elles sont automatiquement supprimées", "Elles protègent le mot de passe"], "Ce qui est publié en ligne est difficile à effacer.", 3),
    ("Que veut dire « mégapixel » ?", "Un million de pixels", ["Mille pixels", "Un milliard de pixels", "Cent pixels", "Dix pixels"], "Les appareils photo se comparent souvent en mégapixels.", 4),
    ("Que fait la fonction input() en Python ?", "Elle lit un texte saisi par l'utilisateur", ["Elle affiche un texte", "Elle efface l'écran", "Elle ferme le programme", "Elle trie une liste"], "Le texte saisi est ensuite stocké, souvent dans une variable.", 4),
    ("Quel mot-clé Python permet de tester une condition ?", "if", ["for", "def", "import", "print"], "if signifie « si ».", 4),
    ("Que renvoie en Python l'expression 3 == 3 ?", "True", ["False", "3", "6", "Une erreur"], "== teste l'égalité de deux valeurs.", 4),
    ("Quel symbole débute un commentaire en Python ?", "Le dièse (#)", ["Les deux barres (//)", "Le point-virgule", "L'arobase (@)", "Le dollar ($)"], "Python ignore ce qui suit le # sur la ligne.", 4),
], "pca-info-algo")

# ---------------------------------------------------------------- 3e
info("3e", "pca-3e-info", [
    ("Que vaut le nombre binaire 11111111 en écriture décimale ?", "255", ["256", "128", "127", "511"], "C'est la plus grande valeur codée sur un octet.", 5),
    ("Combien vaut A en hexadécimal ?", "10", ["A", "11", "9", "16"], "Les chiffres hexadécimaux vont de 0 à 9 puis de A à F.", 5),
    ("Combien vaut F en hexadécimal ?", "15", ["16", "14", "10", "255"], "A = 10, B = 11, C = 12, D = 13, E = 14, F = 15.", 5),
    ("Combien vaut FF en hexadécimal ?", "255", ["256", "15", "225", "16"], "15 × 16 + 15 = 255.", 5),
    ("Que vaut 10 en hexadécimal ?", "16", ["10", "8", "2", "100"], "1 × 16 + 0 = 16.", 5),
    ("Quelle couleur correspond à rgb(255, 0, 0) ?", "Rouge", ["Vert", "Bleu", "Jaune", "Noir"], "Le rouge est à son maximum, le vert et le bleu à zéro.", 4),
    ("Quelle couleur correspond à rgb(0, 0, 0) ?", "Noir", ["Blanc", "Rouge", "Gris clair", "Jaune"], "Aucune lumière : l'écran est noir.", 4),
    ("Quelle couleur correspond à rgb(255, 255, 255) ?", "Blanc", ["Noir", "Rouge", "Gris foncé", "Bleu"], "Les trois couleurs sont au maximum.", 4),
    ("Quelle couleur correspond à rgb(255, 255, 0) ?", "Jaune", ["Violet", "Vert", "Cyan", "Orange vif"], "Rouge plus vert donne du jaune à l'écran.", 5),
    ("Combien de pixels contient une image de 1000 × 500 pixels ?", "500 000", ["1 500", "50 000", "5 000", "5 000 000"], "1000 × 500 = 500 000.", 4),
    ("Quel est le nombre maximum d'essais nécessaires pour trouver un nombre entre 1 et 100 par dichotomie ?", "7", ["5", "10", "50", "100"], "2 puissance 7 vaut 128, qui dépasse 100 ; 2 puissance 6 vaut 64.", 5),
    ("Quel est le nombre maximum d'essais pour trouver un nombre entre 1 et 1000 par dichotomie ?", "10", ["7", "100", "500", "1000"], "2 puissance 10 vaut 1024, qui dépasse 1000.", 5),
    ("Que valent x en Python après x = 5 puis x = x + 2 ?", "7", ["5", "2", "52", "x + 2"], "La nouvelle valeur est calculée avec l'ancienne.", 4),
    ("Que fait en Python for i in range(3): print(i) ?", "Il affiche 0, 1 puis 2", ["Il affiche 1, 2 puis 3", "Il affiche 3 fois le mot i", "Il affiche 0, 1, 2 et 3", "Il n'affiche rien"], "range(3) produit les entiers 0, 1 et 2.", 5),
    ("Quels nombres produit range(1, 4) en Python ?", "1, 2 et 3", ["1, 2, 3 et 4", "0, 1, 2 et 3", "2, 3 et 4", "1 et 4"], "La borne de fin est exclue.", 5),
    ("Que renvoie en Python 10 % 3 ?", "1", ["3", "0", "3,33", "7"], "10 = 3 × 3 + 1.", 4),
    ("Que renvoie en Python \"2\" + \"3\" ?", "\"23\"", ["5", "\"5\"", "6", "Une erreur"], "Avec des chaînes de caractères, + les met bout à bout.", 5),
    ("Que renvoie en Python len([1, 2, 3]) ?", "3", ["6", "1", "2", "123"], "La liste contient trois éléments.", 4),
    ("Que renvoie en Python \"a\" * 3 ?", "\"aaa\"", ["\"a3\"", "3", "\"3a\"", "Une erreur"], "Multiplier une chaîne la répète.", 5),
    ("Que renvoie en Python max(3, 9, 5) ?", "9", ["3", "5", "17", "8"], "max donne la plus grande valeur.", 3),
    ("Que renvoie en Python int(\"12\") + 1 ?", "13", ["121", "12", "\"13\"", "Une erreur"], "int convertit le texte en nombre entier.", 5),
    ("Quel est le type de la valeur 3.5 en Python ?", "float (nombre décimal)", ["int (entier)", "str (texte)", "bool (booléen)", "list (liste)"], "Un float est un nombre à virgule.", 4),
    ("Que vaut not True en Python ?", "False", ["True", "0 et 1", "None seulement", "Une erreur"], "not inverse la valeur booléenne.", 4),
    ("Que vaut la valeur de n à la fin de n = 3 puis while n > 0: n = n - 1 ?", "0", ["3", "1", "-1", "2"], "La boucle s'arrête dès que n n'est plus supérieur à 0.", 5),
    ("Que vaut (5 > 3) and (2 > 4) en Python ?", "False", ["True", "5", "2", "Une erreur"], "La seconde condition est fausse.", 5),
    ("Que fait =SOMME(A1:A3) quand A1, A2 et A3 valent 1, 2 et 3 ?", "Elle donne 6", ["Elle donne 123", "Elle donne 3", "Elle donne 2", "Elle donne 0"], "1 + 2 + 3 = 6.", 3),
    ("Avec un chiffrement de César de décalage 3, la lettre A devient quelle lettre ?", "D", ["B", "C", "Z", "E"], "A se décale de trois places : B, C, D.", 5),
    ("Que fait le protocole HTTPS par rapport à HTTP ?", "Il chiffre les échanges entre le navigateur et le site", ["Il accélère toujours la connexion", "Il supprime les cookies", "Il remplace le navigateur", "Il fabrique les pages"], "Le cadenas du navigateur indique un site en HTTPS.", 4),
    ("Qu'est-ce que le chiffrement ?", "Transformer un message pour le rendre illisible sans la clé", ["Compter les caractères d'un message", "Imprimer un message", "Changer la police d'un message", "Envoyer un message plus vite"], "Il protège les données qui circulent.", 4),
    ("Que signifie une licence Creative Commons ?", "L'auteur autorise la réutilisation de son œuvre sous certaines conditions", ["L'œuvre est détruite", "L'œuvre ne peut jamais être copiée", "L'œuvre est un virus", "L'œuvre appartient à l'État"], "Les conditions sont indiquées par la licence choisie.", 5),
    ("Que fait la mise à jour régulière d'un système ?", "Elle corrige des failles de sécurité", ["Elle vide la corbeille", "Elle augmente la taille de l'écran", "Elle supprime les mots de passe", "Elle ralentit toujours l'ordinateur"], "Les pirates exploitent les failles non corrigées.", 4),
    ("Quelle attitude respecte le droit d'auteur ?", "Citer la source d'une image ou d'un texte utilisé", ["Copier sans rien dire", "Prétendre en être l'auteur", "Supprimer le nom de l'auteur", "Vendre l'œuvre d'un autre"], "On cite l'auteur et l'origine du document.", 3),
    ("À quoi sert la dichotomie dans une recherche ?", "À couper chaque fois l'ensemble en deux pour trouver plus vite", ["À chercher au hasard", "À copier tous les éléments", "À supprimer les doublons", "À trier par couleur"], "Elle exige un ensemble déjà trié.", 5),
    ("Qu'est-ce qu'un tri en informatique ?", "Mettre des éléments dans un ordre (croissant ou décroissant)", ["Effacer des éléments", "Copier des éléments", "Compter des éléments seulement", "Chiffrer des éléments"], "On trie par exemple des noms ou des nombres.", 3),
    ("Quel type d'attaque consiste à essayer très vite beaucoup de mots de passe ?", "Une attaque par force brute", ["Une mise à jour", "Une sauvegarde", "Un antivirus", "Un pare-feu"], "Un mot de passe long résiste mieux.", 5),
    ("Que fait un compilateur ou un interpréteur ?", "Il permet à l'ordinateur d'exécuter un programme écrit dans un langage", ["Il imprime le programme", "Il sert d'écran", "Il protège du virus", "Il refroidit le processeur"], "Python utilise un interpréteur.", 5),
], "pca-info-algo")
