"""CM2 : français (conjugaison par règles et tables de verbes irréguliers, pluriels, féminins, homophones, vocabulaire).
Les formes verbales sont produites par règles (1er et 2e groupes réguliers) ou par tables écrites à la main (verbes irréguliers) :
elles sont `computed` au sens « produites par programme », mais les tables restent à relire par un enseignant."""
from ..core import Draft, gen

C = "cm2"
SRC = "Programme MINEDUB CM2, français (conjugaison, orthographe, grammaire) - formes produites par règles et tables à relire"
SRC_T = "Tables de conjugaison (Bescherelle, Le conjugueur) - à relire"
PERS = ["je", "tu", "il", "nous", "vous", "ils"]
PERS_LABEL = ["je", "tu", "il / elle", "nous", "vous", "ils / elles"]

ER = ["chanter", "parler", "danser", "jouer", "marcher", "regarder", "écouter", "aimer", "donner", "travailler", "porter", "laver", "cuisiner", "dessiner", "demander", "penser", "trouver", "montrer",
      "arriver", "entrer", "tomber", "rester", "visiter", "étudier", "copier", "crier", "pêcher", "cultiver", "vendre?", "récolter", "planter", "habiter", "voyager?", "gagner", "refuser", "inviter", "préparer", "décorer"]
ER = [v for v in ER if "?" not in v]
IR = ["finir", "choisir", "grandir", "réussir", "remplir", "grossir", "obéir", "punir", "bondir", "rougir", "agir", "guérir", "applaudir", "nourrir", "démolir"]
IRREG = {
    "être": (["suis", "es", "est", "sommes", "êtes", "sont"], ["étais", "étais", "était", "étions", "étiez", "étaient"], ["serai", "seras", "sera", "serons", "serez", "seront"]),
    "avoir": (["ai", "as", "a", "avons", "avez", "ont"], ["avais", "avais", "avait", "avions", "aviez", "avaient"], ["aurai", "auras", "aura", "aurons", "aurez", "auront"]),
    "aller": (["vais", "vas", "va", "allons", "allez", "vont"], ["allais", "allais", "allait", "allions", "alliez", "allaient"], ["irai", "iras", "ira", "irons", "irez", "iront"]),
    "faire": (["fais", "fais", "fait", "faisons", "faites", "font"], ["faisais", "faisais", "faisait", "faisions", "faisiez", "faisaient"], ["ferai", "feras", "fera", "ferons", "ferez", "feront"]),
    "dire": (["dis", "dis", "dit", "disons", "dites", "disent"], ["disais", "disais", "disait", "disions", "disiez", "disaient"], ["dirai", "diras", "dira", "dirons", "direz", "diront"]),
    "venir": (["viens", "viens", "vient", "venons", "venez", "viennent"], ["venais", "venais", "venait", "venions", "veniez", "venaient"], ["viendrai", "viendras", "viendra", "viendrons", "viendrez", "viendront"]),
    "prendre": (["prends", "prends", "prend", "prenons", "prenez", "prennent"], ["prenais", "prenais", "prenait", "prenions", "preniez", "prenaient"], ["prendrai", "prendras", "prendra", "prendrons", "prendrez", "prendront"]),
    "pouvoir": (["peux", "peux", "peut", "pouvons", "pouvez", "peuvent"], ["pouvais", "pouvais", "pouvait", "pouvions", "pouviez", "pouvaient"], ["pourrai", "pourras", "pourra", "pourrons", "pourrez", "pourront"]),
    "vouloir": (["veux", "veux", "veut", "voulons", "voulez", "veulent"], ["voulais", "voulais", "voulait", "voulions", "vouliez", "voulaient"], ["voudrai", "voudras", "voudra", "voudrons", "voudrez", "voudront"]),
    "voir": (["vois", "vois", "voit", "voyons", "voyez", "voient"], ["voyais", "voyais", "voyait", "voyions", "voyiez", "voyaient"], ["verrai", "verras", "verra", "verrons", "verrez", "verront"]),
    "savoir": (["sais", "sais", "sait", "savons", "savez", "savent"], ["savais", "savais", "savait", "savions", "saviez", "savaient"], ["saurai", "sauras", "saura", "saurons", "saurez", "sauront"]),
    "devoir": (["dois", "dois", "doit", "devons", "devez", "doivent"], ["devais", "devais", "devait", "devions", "deviez", "devaient"], ["devrai", "devras", "devra", "devrons", "devrez", "devront"]),
    "mettre": (["mets", "mets", "met", "mettons", "mettez", "mettent"], ["mettais", "mettais", "mettait", "mettions", "mettiez", "mettaient"], ["mettrai", "mettras", "mettra", "mettrons", "mettrez", "mettront"]),
    "écrire": (["écris", "écris", "écrit", "écrivons", "écrivez", "écrivent"], ["écrivais", "écrivais", "écrivait", "écrivions", "écriviez", "écrivaient"], ["écrirai", "écriras", "écrira", "écrirons", "écrirez", "écriront"]),
    "lire": (["lis", "lis", "lit", "lisons", "lisez", "lisent"], ["lisais", "lisais", "lisait", "lisions", "lisiez", "lisaient"], ["lirai", "liras", "lira", "lirons", "lirez", "liront"]),
    "partir": (["pars", "pars", "part", "partons", "partez", "partent"], ["partais", "partais", "partait", "partions", "partiez", "partaient"], ["partirai", "partiras", "partira", "partirons", "partirez", "partiront"]),
    "boire": (["bois", "bois", "boit", "buvons", "buvez", "boivent"], ["buvais", "buvais", "buvait", "buvions", "buviez", "buvaient"], ["boirai", "boiras", "boira", "boirons", "boirez", "boiront"]),
}
TENSES = ["présent", "imparfait", "futur simple"]


def forms(verb):
    """(présent, imparfait, futur) six forms each."""
    if verb in IRREG:
        return IRREG[verb]
    if verb in ER or verb.endswith("er") and verb in ER:
        r = verb[:-2]
        return ([r + e for e in ("e", "es", "e", "ons", "ez", "ent")], [r + e for e in ("ais", "ais", "ait", "ions", "iez", "aient")], [verb + e for e in ("ai", "as", "a", "ons", "ez", "ont")])
    r = verb[:-2]       # 2e groupe
    return ([r + e for e in ("is", "is", "it", "issons", "issez", "issent")], [r + e for e in ("issais", "issais", "issait", "issions", "issiez", "issaient")], [verb + e for e in ("ai", "as", "a", "ons", "ez", "ont")])


def with_pronoun(p, f):
    if p == "je" and f[0] in "aeiouyéèêh":
        return "j'" + f
    return p + " " + f


VERBS = ER + IR + list(IRREG)


@gen(C, "cm2-conjugation", cap=480, cat="Conjugaison", source=SRC_T)
def conjugation(rng, d):
    v = rng.choice(VERBS if d >= 3 else (ER[:20] + list(IRREG)[:6] + IR[:5]))
    t = rng.randrange(3)
    p = rng.randrange(6)
    fs = forms(v)
    right = fs[t][p]
    if v in ("être", "avoir", "aller", "faire") and d < 2:
        pass
    wrongs = []
    for tt in range(3):
        for pp in range(6):
            if (tt, pp) != (t, p) and fs[tt][pp] != right and fs[tt][pp] not in wrongs:
                # the same person in another tense first, then other persons of the same tense
                wrongs.append((0 if pp == p else 1 if tt == t else 2, fs[tt][pp]))
    wrongs.sort(key=lambda x: (x[0], x[1]))
    pool = [w for _, w in wrongs]
    ans = with_pronoun(PERS[p], right)
    wr = [with_pronoun(PERS[p], w) for w in pool[:12]]
    tense_txt = ("au présent", "à l'imparfait", "au futur simple")[t]
    return Draft(f"Quelle est la bonne forme du verbe « {v} » {tense_txt}, avec « {PERS_LABEL[p]} » ?", ans, wr,
                 f"« {v} » au {TENSES[t]} : {', '.join(with_pronoun(PERS[i], fs[t][i]) for i in range(6))}.", src=SRC_T)


# ---------------------------------------------------------------------------------------------- pluriels
PLURALS = [("cheval", "chevaux"), ("animal", "animaux"), ("journal", "journaux"), ("hôpital", "hôpitaux"), ("travail", "travaux"), ("corail", "coraux"), ("bijou", "bijoux"), ("caillou", "cailloux"),
           ("chou", "choux"), ("genou", "genoux"), ("hibou", "hiboux"), ("joujou", "joujoux"), ("pou", "poux"), ("bateau", "bateaux"), ("château", "châteaux"), ("gâteau", "gâteaux"),
           ("tableau", "tableaux"), ("oiseau", "oiseaux"), ("rideau", "rideaux"), ("couteau", "couteaux"), ("cadeau", "cadeaux"), ("manteau", "manteaux"), ("eau", "eaux"), ("feu", "feux"),
           ("jeu", "jeux"), ("neveu", "neveux"), ("cheveu", "cheveux"), ("lieu", "lieux"), ("pneu", "pneus"), ("bleu", "bleus"), ("landau", "landaus"), ("sarrau", "sarraus"), ("fou", "fous"),
           ("clou", "clous"), ("trou", "trous"), ("verrou", "verrous"), ("sou", "sous"), ("pays", "pays"), ("souris", "souris"), ("nez", "nez"), ("bras", "bras"), ("prix", "prix"), ("voix", "voix"),
           ("gaz", "gaz"), ("fils", "fils"), ("tapis", "tapis"), ("autobus", "autobus"), ("riz", "riz"), ("mois", "mois"), ("repas", "repas"), ("corps", "corps"), ("temps", "temps"), ("sourire", "sourires"),
           ("ciel", "cieux"), ("œil", "yeux"), ("travail", "travaux"), ("vitrail", "vitraux"), ("détail", "détails"), ("chandail", "chandails"), ("éventail", "éventails"), ("portail", "portails"),
           ("bal", "bals"), ("carnaval", "carnavals"), ("festival", "festivals"), ("récital", "récitals"), ("régal", "régals"), ("chacal", "chacals"), ("cheval", "chevaux"), ("canal", "canaux"),
           ("signal", "signaux"), ("général", "généraux"), ("mal", "maux"), ("métal", "métaux"), ("tribunal", "tribunaux"), ("végétal", "végétaux"), ("épouvantail", "épouvantails"), ("table", "tables"),
           ("mouton", "moutons"), ("enfant", "enfants"), ("village", "villages"), ("marché", "marchés"), ("fleuve", "fleuves"), ("manguier", "manguiers"), ("pirogue", "pirogues"), ("tambour", "tambours")]


def bad_plurals(sing, right):
    cands = [sing + "s", sing + "x", sing[:-1] + "ux" if sing[-1] in "lu" else sing + "x", sing + "aux", sing[:-2] + "aux" if len(sing) > 3 else sing + "es", sing + "es", sing + "z"]
    return [c for c in cands if c != right]


@gen(C, "cm2-plural", cap=170, diffs=(2, 3, 4, 5), cat="Orthographe", source="Règles de formation du pluriel des noms (grammaire française) - à relire")
def plural(rng, d):
    s, p = rng.choice(PLURALS)
    return Draft(f"Quel est le pluriel du nom « {s} » ?", p, bad_plurals(s, p), f"Le pluriel de « {s} » est « {p} ».", src="Règles de formation du pluriel des noms (grammaire française) - à relire")


FEMS = [("beau", "belle"), ("nouveau", "nouvelle"), ("vieux", "vieille"), ("fou", "folle"), ("mou", "molle"), ("heureux", "heureuse"), ("joyeux", "joyeuse"), ("paresseux", "paresseuse"),
        ("bon", "bonne"), ("gentil", "gentille"), ("actif", "active"), ("sportif", "sportive"), ("naïf", "naïve"), ("neuf", "neuve"), ("blanc", "blanche"), ("long", "longue"), ("gros", "grosse"),
        ("gras", "grasse"), ("épais", "épaisse"), ("muet", "muette"), ("coquet", "coquette"), ("complet", "complète"), ("secret", "secrète"), ("discret", "discrète"), ("inquiet", "inquiète"),
        ("cruel", "cruelle"), ("pareil", "pareille"), ("moyen", "moyenne"), ("ancien", "ancienne"), ("canadien", "canadienne"), ("lion", "lionne"), ("chat", "chatte"), ("loup", "louve"),
        ("directeur", "directrice"), ("acteur", "actrice"), ("menteur", "menteuse"), ("danseur", "danseuse"), ("instituteur", "institutrice"), ("boulanger", "boulangère"), ("cuisinier", "cuisinière"),
        ("infirmier", "infirmière"), ("roi", "reine"), ("prince", "princesse"), ("neveu", "nièce"), ("oncle", "tante"), ("coq", "poule"), ("cheval", "jument"), ("garçon", "fille"), ("frère", "sœur")]


@gen(C, "cm2-feminine", cap=100, diffs=(2, 3, 4), cat="Orthographe", source="Formation du féminin (grammaire française) - à relire")
def feminine(rng, d):
    m, f = rng.choice(FEMS)
    bad = [m + "e", m + "te", m + "ne", m + "se", m[:-1] + "ve", m + "le", m + "sse", m[:-2] + "ère" if len(m) > 3 else m + "ère"]
    bad = [b for b in bad if b != f and b != m]
    return Draft(f"Quel est le féminin de « {m} » ?", f, bad, f"Le féminin de « {m} » est « {f} ».", src="Formation du féminin (grammaire française) - à relire")


# ---------------------------------------------------------------------------------------------- homophones
HOMOPHONES = [
    ("Il {} un grand frère.", "a", ["à", "as"]), ("Je vais {} Douala demain.", "à", ["a", "as"]), ("Tu {} gagné la course.", "as", ["a", "à"]),
    ("Mon père {} ma mère sont arrivés.", "et", ["est", "ès"]), ("Le ciel {} bleu.", "est", ["et", "ai"]), ("Il {} grand {} fort.", None, []),
    ("{} chien aboie fort.", "Son", ["Sont", "Sons"]), ("Ils {} partis hier.", "sont", ["son", "sons"]), ("Ma sœur range {} cahier.", "son", ["sont", "sons"]),
    ("Les enfants {} content.", "sont", ["son", "ont"]), ("Ils {} deux frères.", "ont", ["on", "ont-ils"]), ("{} mange du riz ce soir.", "On", ["Ont", "Hon"]),
    ("Ils {} faim après le match.", "ont", ["on", "sont"]), ("{} regarde le match à la télévision.", "On", ["Ont", "Son"]), ("{} livre est sur la table.", "Ce", ["Se", "Ceux"]),
    ("Elle {} lave les mains.", "se", ["ce", "ceux"]), ("{} garçons sont mes cousins.", "Ces", ["Ses", "C'est"]), ("Il a perdu {} clés.", "ses", ["ces", "c'est"]),
    ("{} un joli village.", "C'est", ["Ses", "Ces"]), ("Je ne sais pas {} il habite.", "où", ["ou", "u"]), ("Veux-tu du thé {} du café ?", "ou", ["où", "ous"]),
    ("Il {} pas venu à l'école.", "n'est", ["nais", "naît"]), ("Elle {} très bien le français.", "parle", ["parles", "parlent"]), ("Nous allons {} l'école.", "à", ["a", "as"]),
    ("Voici le cahier {} Awa.", "d'", ["dont", "dans"]), ("Il se promène {} le jardin.", "dans", ["d'en", "dents"]), ("Tu {} de la chance.", "as", ["a", "à"]),
    ("Je {} mon goûter.", "mange", ["manges", "mangent"]), ("Les oiseaux {} dans le manguier.", "chantent", ["chante", "chantes"]), ("Mes amis {} au marché.", "vont", ["vais", "va"]),
    ("Elle a {} ses devoirs.", "fait", ["faire", "faites"]), ("Il faut {} attention.", "faire", ["fait", "faites"]), ("Nous avons {} la pirogue.", "pris", ["prix", "prit"]),
    ("Elle {} un beau sourire.", "a", ["à", "as"]), ("Le maître {} ses élèves.", "écoute", ["écoutes", "écoutent"]), ("Vous {} très gentils.", "êtes", ["est", "et"]),
    ("Il a {} un beau cadeau.", "reçu", ["reçus", "reçue"]), ("La porte est {}.", "fermée", ["fermé", "fermer"]), ("Les fleurs sont {}.", "fanées", ["fané", "fanée"]),
    ("Elle est {} hier.", "partie", ["parti", "partis"]), ("Les filles sont {} à l'école.", "allées", ["allé", "allée"]), ("Il faut {} le riz avant de le cuire.", "laver", ["lavé", "lavez"]),
    ("Vous allez {} le livre.", "lire", ["lit", "lis"]), ("Tu dois {} ta leçon.", "apprendre", ["appris", "apprends"]), ("Je dois {} à la maison.", "rentrer", ["rentré", "rentrez"]),
    ("Ils ont {} un gros poisson.", "pêché", ["pêcher", "pêchez"]), ("Nous allons {} du manioc.", "planter", ["planté", "plantez"]), ("Elle a {} le ballon.", "lancé", ["lancer", "lancez"]),
]
HOMOPHONES = [h for h in HOMOPHONES if h[1]]


@gen(C, "cm2-homophone", cap=60, diffs=(2, 3), cat="Orthographe", source="Homophones grammaticaux et accord (grammaire française) - à relire")
def homophone(rng, d):
    s, right, wr = rng.choice(HOMOPHONES)
    return Draft("Quel mot complète correctement la phrase « " + s.replace("{}", "…") + " » ?", right, wr + ["ai"], "Phrase complète : « " + s.replace("{}", right).rstrip(".") + " ».",
                 src="Homophones grammaticaux et accord (grammaire française) - à relire")


NATURE = [
    ("Le chat dort sur le canapé.", "dort", "verbe"), ("Les enfants jouent dans la cour.", "jouent", "verbe"), ("Awa mange une mangue.", "mange", "verbe"), ("Le maître écrit au tableau.", "écrit", "verbe"),
    ("Le petit garçon court vite.", "garçon", "nom"), ("La belle maison est vendue.", "maison", "nom"), ("Mon frère aime le football.", "football", "nom"), ("Une grosse pirogue traverse le fleuve.", "fleuve", "nom"),
    ("Le grand arbre donne de l'ombre.", "grand", "adjectif"), ("Une jolie fleur pousse ici.", "jolie", "adjectif"), ("Ce village est tranquille.", "tranquille", "adjectif"), ("La mangue est mûre.", "mûre", "adjectif"),
    ("Il parle doucement.", "doucement", "adverbe"), ("Elle chante très bien.", "très", "adverbe"), ("Nous partons demain.", "demain", "adverbe"), ("Il travaille beaucoup.", "beaucoup", "adverbe"),
]


@gen(C, "cm2-grammar-nature", cap=60, diffs=(2, 3, 4), cat="Grammaire", source="Nature des mots (grammaire française) - à relire")
def grammar_nature(rng, d):
    s, w, nature = rng.choice(NATURE)
    return Draft(f"Dans la phrase « {s} », quelle est la nature du mot « {w} » ?", nature, [x for x in ("verbe", "nom", "adjectif", "adverbe", "pronom", "préposition") if x != nature],
                 f"Dans « {s} », « {w} » est un {nature}.", src="Nature des mots (grammaire française) - à relire")


SYN = [("rapide", "vite"), ("grand", "immense"), ("petit", "minuscule"), ("content", "heureux"), ("triste", "malheureux"), ("beau", "joli"), ("peur", "crainte"), ("maison", "demeure"), ("bateau", "navire"),
       ("enfant", "gamin"), ("travail", "tâche"), ("chemin", "route"), ("commencer", "débuter"), ("finir", "terminer"), ("regarder", "observer"), ("parler", "discuter"), ("marcher", "avancer"), ("fatigué", "épuisé")]
ANT = [("grand", "petit"), ("jour", "nuit"), ("chaud", "froid"), ("monter", "descendre"), ("ouvrir", "fermer"), ("joie", "tristesse"), ("riche", "pauvre"), ("lent", "rapide"), ("fort", "faible"),
       ("avant", "après"), ("entrer", "sortir"), ("plein", "vide"), ("sec", "mouillé"), ("dur", "mou"), ("facile", "difficile"), ("courageux", "peureux"), ("vrai", "faux"), ("hiver", "été"), ("aimer", "détester"),
       ("acheter", "vendre"), ("gagner", "perdre"), ("sombre", "clair"), ("propre", "sale"), ("jeune", "vieux")]


@gen(C, "cm2-synonym", cap=40, diffs=(2, 3), cat="Vocabulaire", source="Synonymes et contraires (dictionnaire) - à relire")
def synonym(rng, d):
    a, b = rng.choice(SYN)
    pool = [y for _, y in SYN if y != b and y != a]
    return Draft(f"Quel mot est un synonyme de « {a} » ?", b, pool, f"« {a} » et « {b} » ont un sens proche.", src="Synonymes et contraires (dictionnaire) - à relire")


@gen(C, "cm2-antonym", cap=60, diffs=(1, 2, 3), cat="Vocabulaire", source="Synonymes et contraires (dictionnaire) - à relire")
def antonym(rng, d):
    a, b = rng.choice(ANT)
    pool = [y for _, y in ANT if y != b and y != a]
    return Draft(f"Quel est le contraire de « {a} » ?", b, pool, f"Le contraire de « {a} » est « {b} ».", src="Synonymes et contraires (dictionnaire) - à relire")
