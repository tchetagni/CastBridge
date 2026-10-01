"""Français, CP-CM1 et 6e-4e : conjugaison (règles + tables), accords, homophones, grammaire, vocabulaire, orthographe.
Les phrases et les formes sont produites par programme à partir de listes écrites à la main (noms avec genre et pluriel,
verbes réguliers et irréguliers) : la réponse découle de la liste, jamais d'une supposition. Programmes MINEDUB / MINESEC."""
import random

from ..core import Draft, norm
from .cm2_french import ANT, ER, FEMS, HOMOPHONES, IR, IRREG, PERS, PERS_LABEL, PLURALS, SYN, forms, with_pronoun, bad_plurals
from .pc_common import reg

FRC = ["cp", "ce1", "ce2", "cm1", "6e", "5e", "4e"]
F2 = FRC[1:]
F3 = FRC[2:]
F4 = FRC[3:]
SEC_FR = ["6e", "5e", "4e"]
SRC = "Programme officiel de français (MINEDUB / MINESEC) - formes produites par règles et tables à relire"

PARTICIPLE = {"être": "été", "avoir": "eu", "aller": "allé", "faire": "fait", "dire": "dit", "venir": "venu", "prendre": "pris", "pouvoir": "pu", "vouloir": "voulu", "voir": "vu",
              "savoir": "su", "devoir": "dû", "mettre": "mis", "écrire": "écrit", "lire": "lu", "partir": "parti", "boire": "bu"}
ETRE_VERBS = {"aller", "venir", "partir", "arriver", "entrer", "tomber", "rester"}
MORE_ER = ["arriver", "entrer", "tomber", "rester"]


def participle(v):
    if v in PARTICIPLE:
        return PARTICIPLE[v]
    if v.endswith("er"):
        return v[:-2] + "é"
    return v[:-1]                          # finir -> fini


def all_verbs():
    return [v for v in ER + IR + list(IRREG) if v not in ("vendre?",)]


def passe_compose(v):
    aux_forms = forms("être" if v in ETRE_VERBS else "avoir")[0]
    pp = participle(v)
    return [aux_forms[i] + " " + pp + ("" if v not in ETRE_VERBS else ("s" if i >= 3 else "")) for i in range(6)]


def plus_que_parfait(v):
    aux = forms("être" if v in ETRE_VERBS else "avoir")[1]
    pp = participle(v)
    return [aux[i] + " " + pp + ("s" if v in ETRE_VERBS and i >= 3 else "") for i in range(6)]


def conditional(v):
    fut = forms(v)[2]
    return [fut[0][:-2] + e for e in ("ais", "ais", "ait", "ions", "iez", "aient")]


def pc_with_pron(p, f):
    """je + 'ai chanté' -> j'ai chanté."""
    return with_pronoun(p, f)


TENSE_FORMS = {
    "présent": (lambda v: forms(v)[0], "au présent"),
    "imparfait": (lambda v: forms(v)[1], "à l'imparfait"),
    "futur simple": (lambda v: forms(v)[2], "au futur simple"),
    "passé composé": (passe_compose, "au passé composé"),
    "plus-que-parfait": (plus_que_parfait, "au plus-que-parfait"),
    "conditionnel présent": (conditional, "au conditionnel présent"),
}
TENSES_BY_G = {2: ["présent"], 3: ["présent", "futur simple", "imparfait"], 4: ["présent", "futur simple", "imparfait", "passé composé"],
               7: ["présent", "futur simple", "imparfait", "passé composé"], 8: ["présent", "futur simple", "imparfait", "passé composé", "plus-que-parfait"],
               9: ["présent", "futur simple", "imparfait", "passé composé", "plus-que-parfait", "conditionnel présent"]}


def conjugation(rng, d, g, lg):
    tenses = TENSES_BY_G[g]
    v = rng.choice(all_verbs() if d >= 3 or g >= 4 else (ER[:20] + ["être", "avoir", "aller", "faire"]))
    t = rng.choice(tenses)
    fn, label = TENSE_FORMS[t]
    fs = fn(v)
    p = rng.randrange(6)
    right = pc_with_pron(PERS[p], fs[p])
    wr = []
    for tt in tenses:
        if tt != t:
            wr.append(pc_with_pron(PERS[p], TENSE_FORMS[tt][0](v)[p]))
    wr += [pc_with_pron(PERS[i], fs[i]) for i in range(6) if i != p]
    return Draft(f"Quelle est la bonne forme du verbe « {v} » {label}, avec « {PERS_LABEL[p]} » ?", right, wr,
                 f"« {v} » {label} : " + ", ".join(pc_with_pron(PERS[i], fs[i]) for i in range(6)) + ".")


def tense_id(rng, d, g, lg):
    tenses = TENSES_BY_G[g]
    v = rng.choice(all_verbs())
    t = rng.choice(tenses)
    p = rng.choice([0, 2, 3, 5])
    fs = TENSE_FORMS[t][0](v)
    shown = pc_with_pron(PERS[p], fs[p])
    # the shown form must belong to this tense only (among the tenses of the grade)
    for tt in tenses:
        if tt != t and fs[p] == TENSE_FORMS[tt][0](v)[p]:
            return None
    return Draft(f"À quel temps est conjugué le verbe dans « {shown} » ?", t, [x for x in tenses + ["passé simple", "subjonctif présent"] if x != t][:6],
                 f"« {shown} » est le verbe « {v} » {TENSE_FORMS[t][1]}.")


GROUP_1 = [v for v in ER if v not in ("aller",)]
GROUP_3 = ["aller", "faire", "dire", "venir", "prendre", "pouvoir", "vouloir", "voir", "savoir", "devoir", "mettre", "écrire", "lire", "partir", "boire"]


def verb_group(rng, d, g, lg):
    grp = rng.choice([1, 2, 3])
    v = rng.choice({1: GROUP_1, 2: IR, 3: GROUP_3}[grp])
    lab = {1: "du 1er groupe", 2: "du 2e groupe", 3: "du 3e groupe"}
    why = {1: "il finit par -er (sauf « aller »).", 2: "il finit par -ir et fait -issons à la 1re personne du pluriel.", 3: "ce verbe ne suit ni la règle du 1er ni celle du 2e groupe."}[grp]
    return Draft(f"À quel groupe appartient le verbe « {v} » ?", lab[grp].replace("du ", ""), [lab[k].replace("du ", "") for k in (1, 2, 3) if k != grp] + ["aucun groupe", "auxiliaire"],
                 f"« {v} » est {lab[grp]} : {why}")


def infinitive(rng, d, g, lg):
    v = rng.choice(all_verbs())
    t = rng.choice(TENSES_BY_G[g][:3])
    fs = TENSE_FORMS[t][0](v)
    p = rng.choice([0, 2, 3, 5])
    shown = pc_with_pron(PERS[p], fs[p])
    others = [x for x in all_verbs() if x != v]
    random.Random(shown).shuffle(others)
    # a distractor must not conjugate to the same shown form
    wr = [o for o in others if shown not in [pc_with_pron(PERS[i], TENSE_FORMS[tt][0](o)[i]) for tt in ("présent", "imparfait", "futur simple") for i in range(6)]][:8]
    return Draft(f"Quel est l'infinitif du verbe dans « {shown} » ?", v, wr, f"« {shown} » est une forme du verbe « {v} ».")


# ---------------------------------------------------------------------------------------------- noms, genre, pluriel
NOUNS = [  # (nom, genre, pluriel)
    ("chat", "m", "chats"), ("chien", "m", "chiens"), ("livre", "m", "livres"), ("cahier", "m", "cahiers"), ("stylo", "m", "stylos"), ("village", "m", "villages"), ("marché", "m", "marchés"),
    ("fleuve", "m", "fleuves"), ("manguier", "m", "manguiers"), ("tambour", "m", "tambours"), ("ballon", "m", "ballons"), ("poisson", "m", "poissons"), ("panier", "m", "paniers"),
    ("bateau", "m", "bateaux"), ("cheval", "m", "chevaux"), ("oiseau", "m", "oiseaux"), ("couteau", "m", "couteaux"), ("gâteau", "m", "gâteaux"), ("journal", "m", "journaux"),
    ("animal", "m", "animaux"), ("arbre", "m", "arbres"), ("soleil", "m", "soleils"), ("nuage", "m", "nuages"), ("camion", "m", "camions"), ("train", "m", "trains"),
    ("tableau", "m", "tableaux"), ("cadeau", "m", "cadeaux"), ("chapeau", "m", "chapeaux"), ("jardin", "m", "jardins"), ("maître", "m", "maîtres"), ("pagne", "m", "pagnes"),
    ("maison", "f", "maisons"), ("table", "f", "tables"), ("chaise", "f", "chaises"), ("porte", "f", "portes"), ("fenêtre", "f", "fenêtres"), ("école", "f", "écoles"),
    ("classe", "f", "classes"), ("fleur", "f", "fleurs"), ("mangue", "f", "mangues"), ("banane", "f", "bananes"), ("orange", "f", "oranges"), ("pirogue", "f", "pirogues"),
    ("voiture", "f", "voitures"), ("ville", "f", "villes"), ("rivière", "f", "rivières"), ("montagne", "f", "montagnes"), ("forêt", "f", "forêts"), ("route", "f", "routes"),
    ("ardoise", "f", "ardoises"), ("trousse", "f", "trousses"), ("règle", "f", "règles"), ("cuisine", "f", "cuisines"), ("étoile", "f", "étoiles"), ("lune", "f", "lunes"),
    ("feuille", "f", "feuilles"), ("bouteille", "f", "bouteilles"), ("case", "f", "cases"), ("vache", "f", "vaches"), ("chèvre", "f", "chèvres"), ("poule", "f", "poules"), ("tortue", "f", "tortues"),
]
ADJ = [("grand", "grande", "grands", "grandes"), ("petit", "petite", "petits", "petites"), ("joli", "jolie", "jolis", "jolies"), ("beau", "belle", "beaux", "belles"),
       ("nouveau", "nouvelle", "nouveaux", "nouvelles"), ("gros", "grosse", "gros", "grosses"), ("heureux", "heureuse", "heureux", "heureuses"), ("long", "longue", "longs", "longues"),
       ("vert", "verte", "verts", "vertes"), ("noir", "noire", "noirs", "noires"), ("blanc", "blanche", "blancs", "blanches"), ("fort", "forte", "forts", "fortes"),
       ("national", "nationale", "nationaux", "nationales"), ("principal", "principale", "principaux", "principales")]
PRE = {"grand", "petit", "joli", "beau", "nouveau", "gros", "long"}      # adjectifs placés avant le nom
VOWELS = "aeiouyéèêâîôhAEIOUYÉÈÊ"


def art_def(noun, gender):
    return "l'" + noun if noun[0] in VOWELS else ("le " if gender == "m" else "la ") + noun


def art_indef(gender):
    return "un" if gender == "m" else "une"


def article(rng, d, g, lg):
    nn, gd, _ = rng.choice(NOUNS)
    if rng.random() < 0.5:
        right = art_indef(gd)
        wr = ["un" if gd == "f" else "une", "le" if gd == "m" else "la", "les", "des"]
        t = f"Quel article convient devant le nom « {nn} » : « … {nn} » (un objet, un animal ou une chose, sans en désigner un en particulier) ?"
        t = f"Quel mot convient pour compléter : « … {nn} » ?"
        return Draft(t, right, wr[:3] + ["l'"], f"« {nn} » est un nom {'masculin' if gd == 'm' else 'féminin'} : « {right} {nn} ».", alt=([] if True else []))
    return None


def article_g(rng, d, g, lg):
    nn, gd, _ = rng.choice([n for n in NOUNS if n[0][0] not in VOWELS])
    right = "le" if gd == "m" else "la"
    wr = ["la" if gd == "m" else "le", "les", "un" if gd == "m" else "une"]
    # un/une is also correct after a determiner of indefinite meaning: avoid by making the definite article explicit
    return Draft(f"Quel article défini convient devant « {nn} » ?", right, ["la" if gd == "m" else "le", "les", "l'"], f"« {nn} » est un nom {'masculin' if gd == 'm' else 'féminin'} : « {right} {nn} ».")


def indef_article(rng, d, g, lg):
    nn, gd, _ = rng.choice(NOUNS)
    right = art_indef(gd)
    return Draft(f"Quel article indéfini convient devant « {nn} » : « {art_indef('m')} / {art_indef('f')} {nn} » ?", right, ["un" if gd == "f" else "une", "le", "les"],
                 f"« {nn} » est un nom {'masculin' if gd == 'm' else 'féminin'} : « {right} {nn} ».") if False else \
        Draft(f"Quel mot convient devant le nom « {nn} » : « … {nn} » ?", right, ["un" if gd == "f" else "une", "les", "des"], f"« {nn} » est un nom {'masculin' if gd == 'm' else 'féminin'} : « {right} {nn} ».")


def gender_q(rng, d, g, lg):
    nn, gd, _ = rng.choice(NOUNS)
    return Draft(f"Quel est le genre du nom « {nn} » ?", "masculin" if gd == "m" else "féminin", ["féminin" if gd == "m" else "masculin", "neutre", "pluriel"],
                 f"« {nn} » est un nom {'masculin' if gd == 'm' else 'féminin'} (on dit {art_def(nn, gd)}).")


def sing_plur_id(rng, d, g, lg):
    nn, gd, pl = rng.choice(NOUNS)
    sing = rng.random() < 0.5
    shown = f"les {pl}" if not sing else art_def(nn, gd)
    if pl[0] in VOWELS:
        shown = f"les {pl}" if not sing else art_def(nn, gd)
    return Draft(f"Le groupe « {shown} » est-il au singulier ou au pluriel ?", "singulier" if sing else "pluriel", ["pluriel" if sing else "singulier", "masculin", "féminin"],
                 f"« {shown} » est au {'singulier' if sing else 'pluriel'}.")


def plural_noun(rng, d, g, lg):
    pool = PLURALS if g >= 3 else [p for p in PLURALS if p[1] == p[0] + "s" or p[0][-3:] == "eau"]
    s, p = rng.choice(pool)
    wr = bad_plurals(s, p)
    return Draft(f"Quel est le pluriel du nom « {s} » ?", p, wr, f"Le pluriel de « {s} » est « {p} ».")


def plural_group(rng, d, g, lg):
    nn, gd, pl = rng.choice(NOUNS)
    a = rng.choice(ADJ)
    pre = a[0] in PRE
    if a[0] in ("beau", "nouveau") and gd == "m" and nn[0] in VOWELS:
        return None                       # « bel animal », « nouvel arbre » : exception left out
    adj_s = a[0] if gd == "m" else a[1]
    adj_p = a[2] if gd == "m" else a[3]
    other_p = a[3] if gd == "m" else a[2]
    if pre:
        base = (("le " if gd == "m" else "la ") + adj_s + " " + nn)
    elif nn[0] in VOWELS:
        base = "l'" + nn + " " + adj_s
    else:
        base = ("le " if gd == "m" else "la ") + nn + " " + adj_s

    def phrase(n, ad):
        return "les " + ((ad + " " + n) if pre else (n + " " + ad))
    right = phrase(pl, adj_p)
    wrong = [phrase(pl, adj_s), phrase(nn, adj_p), phrase(pl, other_p), phrase(nn + "s", adj_p)]
    return Draft(f"Quel est le pluriel de « {base} » ?", right, [w for w in wrong if w != right] + [phrase(pl + "s", adj_p + "s")],
                 f"Le nom et l'adjectif s'accordent au pluriel : « {right} ».")


def feminine(rng, d, g, lg):
    m, f = rng.choice(FEMS)
    bad = [m + "e", m + "te", m + "ne", m + "se", m[:-1] + "ve", m + "le", m + "sse"]
    return Draft(f"Quel est le féminin de « {m} » ?", f, [b for b in bad if b != f and b != m], f"Le féminin de « {m} » est « {f} ».")


def agreement_verb(rng, d, g, lg):
    nn, gd, pl = rng.choice([n for n in NOUNS if n[0] in ANIMATE])
    v = rng.choice(["chanter", "marcher", "jouer", "crier", "danser", "regarder", "écouter", "arriver", "rester", "travailler", "parler", "sauter"])
    plural = rng.random() < 0.5
    subj = ("les " + pl) if plural else art_def(nn, gd)
    pf = forms(v)[0]
    right = pf[5] if plural else pf[2]
    wrong = pf[2] if plural else pf[5]
    ending_extra = rng.choice(["dans la cour", "au marché", "avec joie", "près du fleuve", "tous les jours"])
    return Draft(f"Quelle forme du verbe « {v} » complète la phrase « {subj[0].upper() + subj[1:]} … {ending_extra}. » ?", right,
                 [wrong, pf[1], pf[3] if False else pf[0], pf[4]], f"Le verbe s'accorde avec son sujet « {subj} » ({'pluriel' if plural else 'singulier'}) : {right}.")


def etre_agreement(rng, d, g, lg):
    v = rng.choice(["aller", "partir", "venir", "arriver", "entrer", "tomber", "rester"])
    pp = participle(v)
    kind = rng.choice(["ms", "fs", "mp", "fp"])
    subj = {"ms": rng.choice(["Mon frère", "Le maître", "Le chat", "Mon cousin", "L'enfant"]), "fs": rng.choice(["Ma sœur", "La maîtresse", "Ma cousine", "La petite fille", "Awa"]),
            "mp": rng.choice(["Mes frères", "Les garçons", "Les maîtres", "Mes cousins"]), "fp": rng.choice(["Mes sœurs", "Les filles", "Mes cousines", "Les voisines"])}[kind]
    aux = "est" if kind in ("ms", "fs") else "sont"
    forms_ = {"ms": pp, "fs": pp + "e", "mp": pp + "s", "fp": pp + "es"}
    right = forms_[kind]
    wr = [forms_[k] for k in forms_ if k != kind] + [pp + "ent"]
    ex = rng.choice(["hier", "ce matin", "à l'école", "avant midi", "très tôt"])
    return Draft(f"Quelle est l'orthographe correcte : « {subj} {aux} … {ex}. » (verbe « {v} ») ?", right, wr,
                 f"Avec l'auxiliaire être, le participe passé s'accorde avec le sujet : « {subj} {aux} {right} ».")


# ---------------------------------------------------------------------------------------------- homophones par modèles
NAMES = ["Awa", "Kofi", "Mbarga", "Fanta", "Ngono", "Amina", "Junior", "Bih", "Hawa", "Brice", "Estelle", "Moussa", "Samuel", "Clarisse"]
OBJ_M = ["cahier", "ballon", "stylo", "livre", "chapeau", "cadeau", "panier", "sac", "pagne", "gâteau"]
OBJ_F = ["mangue", "règle", "trousse", "robe", "chaise", "pirogue", "ardoise", "bouteille", "poule", "voiture"]
PLACES = ["l'école", "Douala", "Yaoundé", "Bafoussam", "Garoua", "Kribi", "Limbé", "Bertoua", "Maroua", "Ebolowa", "Bamenda", "Buea", "Dschang"]
FOODS = ["du riz", "des beignets", "du manioc", "du poisson", "des arachides", "du plantain", "des haricots"]
ADJS = ["content", "fatigué", "malade", "en retard", "prêt", "gentil", "grand", "courageux"]


def homophone_tpl(rng, d, g, lg):
    nm, nm2 = rng.sample(NAMES, 2)
    om, of = rng.choice(OBJ_M), rng.choice(OBJ_F)
    kind = rng.choice(["a/à", "a/à", "et/est", "on/ont", "son/sont", "ces/ses", "ce/se", "ou/où", "c'est/s'est", "mes/mais", "ma/m'a", "la/là/l'a"][: 5 + 2 * (g - 1)] if g <= 4 else
                      ["a/à", "et/est", "on/ont", "son/sont", "ces/ses", "ce/se", "ou/où", "c'est/s'est", "mes/mais", "ma/m'a", "la/là/l'a", "leur/leurs", "quel/qu'elle"])
    if kind == "a/à":
        which = rng.choice(["a", "à", "as"])
        if which == "a":
            s, r, w = f"{nm} … un {om} neuf.", "a", ["à", "as", "ah"]
        elif which == "à":
            s, r, w = f"{nm} va … {rng.choice(PLACES)} demain.".replace("à l'", "à l'"), "à", ["a", "as", "ah"]
            s = s.replace("… l'école", "… l'école")
        else:
            s, r, w = f"Tu … {om if rng.random() < .5 else 'un ' + om} ?".replace("… ", "… un ", 1) if False else f"Tu … un {om} dans ton sac.", "as", ["a", "à", "ah"]
    elif kind == "et/est":
        if rng.random() < 0.5:
            s, r, w = f"{nm} … {rng.choice(ADJS)} aujourd'hui.", "est", ["et", "ai", "es"]
        else:
            s, r, w = f"{nm} … {nm2} jouent au football.", "et", ["est", "ai", "es"]
    elif kind == "on/ont":
        if rng.random() < 0.5:
            s, r, w = f"… {rng.choice(['mange', 'danse', 'chante', 'joue', 'travaille'])} ensemble ce soir.", "On", ["Ont", "Ons", "Hon"]
        else:
            s, r, w = f"Les enfants … {rng.choice(['faim', 'soif', 'sommeil', 'peur', 'chaud'])}.", "ont", ["on", "ons", "sont"]
    elif kind == "son/sont":
        if rng.random() < 0.5:
            s, r, w = f"{nm} range … {om}.", "son", ["sont", "sons", "s'ont"]
        else:
            s, r, w = f"Ils … {rng.choice(['arrivés', 'partis', 'contents', 'très fatigués'])}.", "sont", ["son", "sons", "s'ont"]
    elif kind == "ces/ses":
        if rng.random() < 0.5:
            s, r, w = f"{nm} a perdu … clés.", "ses", ["ces", "c'est", "s'est"]
        else:
            s, r, w = f"Regarde … oiseaux sur l'arbre.", "ces", ["ses", "c'est", "s'est"]
    elif kind == "ce/se":
        if rng.random() < 0.5:
            s, r, w = f"{nm} … lave les mains.", "se", ["ce", "ceux", "ces"]
        else:
            s, r, w = f"… {om} est à moi.", "Ce", ["Se", "Ceux", "Ces"]
    elif kind == "ou/où":
        if rng.random() < 0.5:
            s, r, w = f"Veux-tu du thé … du café ?", "ou", ["où", "u", "oû"]
        else:
            s, r, w = f"Je ne sais pas … est mon cahier.", "où", ["ou", "u", "oû"]
    elif kind == "c'est/s'est":
        if rng.random() < 0.5:
            s, r, w = f"… un joli village.", "C'est", ["S'est", "Sait", "Ces"]
        else:
            s, r, w = f"{nm} … levé très tôt.", "s'est", ["c'est", "sait", "ses"]
    elif kind == "mes/mais":
        if rng.random() < 0.5:
            s, r, w = f"Il pleut, … j'ai mon parapluie.", "mais", ["mes", "met", "mets"]
        else:
            s, r, w = f"J'aime … amis et ma famille.", "mes", ["mais", "met", "mets"]
    elif kind == "ma/m'a":
        if rng.random() < 0.5:
            s, r, w = f"… sœur est partie au marché.", "Ma", ["M'a", "Mas", "Mat"]
        else:
            s, r, w = f"Papa … offert un {om}.", "m'a", ["ma", "mas", "mat"]
    elif kind == "la/là/l'a":
        t3 = rng.choice([0, 1, 2])
        if t3 == 0:
            s, r, w = f"Pose le {om} … , sur la table.", "là", ["la", "l'a", "las"]
        elif t3 == 1:
            s, r, w = f"{nm} regarde … lune.", "la", ["là", "l'a", "las"]
        else:
            s, r, w = f"Ce {om} est cassé : {nm2} … laissé tomber.", "l'a", ["la", "là", "las"]
    elif kind == "leur/leurs":
        if rng.random() < 0.5:
            s, r, w = f"Les enfants rangent … cahiers.", "leurs", ["leur", "leures", "leure"]
        else:
            s, r, w = f"Je … donne des bonbons (à eux).", "leur", ["leurs", "leures", "leure"]
    else:
        if rng.random() < 0.5:
            s, r, w = f"… belle journée !", "Quelle", ["Qu'elle", "Quel", "Quels"]
        else:
            s, r, w = f"Je pense … viendra demain.", "qu'elle", ["quelle", "quel", "quels"]
    filled = s.replace("…", r, 1) if not r[0].isupper() or s.startswith("…") else s.replace("…", r, 1)
    fixed = s.replace("… ,", "…,")
    return Draft("Quel mot complète correctement la phrase « " + s + " » ?", r, w, "Phrase complète : « " + filled + " ».")


# ---------------------------------------------------------------------------------------------- grammaire
SUBJ = ["Le chat", "Le chien", "Mon frère", "La maîtresse", "Les enfants", "Ma sœur", "Le fermier", "L'oiseau", "Les élèves", "Mon oncle", "La poule", "Les pêcheurs", "Awa", "Le médecin"]
VERB_ITR = [("dort", "dormir"), ("court", "courir"), ("chante", "chanter"), ("joue", "jouer"), ("rit", "rire"), ("travaille", "travailler"), ("arrive", "arriver"), ("saute", "sauter")]
ANIMATE = {"chat", "chien", "oiseau", "poule", "vache", "chèvre", "tortue", "cheval", "animal", "maître"}
ADV = ["vite", "doucement", "souvent", "beaucoup", "toujours", "bien", "ici", "maintenant"]


def pl_verb(v):
    return v


def nature_q(rng, d, g, lg):
    subj = rng.choice(SUBJ)
    plural = subj.startswith(("Les ", "Mes "))
    v_inf = rng.choice([v for v in ER if v[-2:] == "er"])
    vf = forms(v_inf)[0][5 if plural else 2]
    adv = rng.choice(ADV)
    s = f"{subj} {vf} {adv}."
    target = rng.choice(["verbe", "adverbe", "nom"] + (["déterminant"] if g >= 4 else []))
    if target == "verbe":
        w = vf
    elif target == "adverbe":
        w = adv
    elif target == "nom":
        parts = subj.split()
        if len(parts) == 1:
            return None
        w = parts[1] if parts[0] in ("Le", "La", "L'", "Les", "Mon", "Ma", "Mes") else parts[-1]
        if subj.startswith("L'"):
            w = subj[2:]
    else:
        parts = subj.split()
        if len(parts) == 1:
            return None
        w = parts[0] if not subj.startswith("L'") else "L'"
    opts = ["verbe", "nom", "adverbe", "adjectif", "pronom", "préposition"] + (["déterminant"] if g >= 4 else [])
    if target == "déterminant":
        w2 = w
    return Draft(f"Dans la phrase « {s} », quelle est la nature du mot « {w} » ?", target, [o for o in opts if o != target][:6],
                 f"Dans « {s} », « {w} » est un {target}.")


SENTENCE_TYPES = {
    "déclarative": ["{S} {V} {A}.", "{S} {V} près de la maison.", "{S} {V} chaque matin."],
    "interrogative": ["Est-ce que {s} {v} {A} ?", "Pourquoi {s} {v} {A} ?", "{S} {V}-t-il {A} ?"],
    "exclamative": ["Comme {s} {v} bien !", "Quel beau jour pour {s} !", "{S} {V} si vite !"],
    "impérative": ["Regarde {s} !", "Écoute {s}.", "Va voir {s}."],
}


def sentence_type(rng, d, g, lg):
    kind = rng.choice(list(SENTENCE_TYPES))
    subj = rng.choice(["le chat", "le chien", "mon frère", "l'oiseau", "mon oncle", "le maître", "le fermier"])
    vinf = rng.choice(["chanter", "jouer", "marcher", "travailler", "danser", "parler"])
    v3 = forms(vinf)[0][2]
    adv = rng.choice(["vite", "souvent", "ici", "bien"])
    tpl = rng.choice(SENTENCE_TYPES[kind])
    S = subj[0].upper() + subj[1:]
    s = tpl.format(S=S, s=subj, V=v3, v=v3, A=adv)
    if "Quel beau jour" in tpl:
        s = f"Quel beau jour pour {subj} !"
    if kind == "interrogative" and "{S} {V}-t-il" in tpl:
        s = f"{S} {v3}-t-il {adv} ?"
        if subj.startswith("l'") or subj.startswith("mon") or subj.startswith("le"):
            s = f"{S} {v3}-t-il {adv} ?"
    return Draft(f"Quel est le type de la phrase « {s} » ?", kind, [k for k in SENTENCE_TYPES if k != kind], f"C'est une phrase {kind} : {({'déclarative': 'elle raconte ou affirme', 'interrogative': 'elle pose une question', 'exclamative': 'elle exprime un sentiment', 'impérative': 'elle donne un ordre'})[kind]}.")


def subject_q(rng, d, g, lg):
    subj = rng.choice([s for s in SUBJ if " " in s or s == "Awa"])
    plural = subj.startswith(("Les ", "Mes "))
    v_inf = rng.choice([v for v in ER if v[-2:] == "er"])
    vf = forms(v_inf)[0][5 if plural else 2]
    adv = rng.choice(ADV)
    s = f"{subj} {vf} {adv}."
    others = [adv, vf] + [w for w in subj.split()[:1] if len(subj.split()) > 1 and w not in ("Awa",)]
    return Draft(f"Dans la phrase « {s} », quel est le sujet du verbe « {vf} » ?", subj, [o for o in (adv, vf, "tout le monde", "personne", subj.split()[-1] + " " + adv) if o != subj][:6],
                 f"On pose la question : qui est-ce qui {vf} ? Réponse : {subj}.")


COD_S = [("Awa", "mange", "une mangue"), ("Le maître", "écrit", "une phrase"), ("Mon frère", "lit", "un livre"), ("Ma mère", "prépare", "le repas"), ("Le fermier", "plante", "du manioc"),
         ("Les enfants", "regardent", "le match"), ("Le pêcheur", "lance", "son filet"), ("Kofi", "ouvre", "la porte"), ("Les élèves", "écoutent", "la leçon"), ("Ma sœur", "range", "ses cahiers")]


def cod_q(rng, d, g, lg):
    s, v, cod = rng.choice(COD_S)
    adv = rng.choice(["chaque jour", "à la maison", "avec soin", "en ce moment", "chaque matin"])
    sent = f"{s} {v} {cod} {adv}."
    return Draft(f"Dans la phrase « {sent} », quel est le complément d'objet direct (COD) du verbe « {v} » ?", cod, [s, adv, v, "tout de suite"],
                 f"On pose la question : {v} quoi ? Réponse : {cod}.")


# ---------------------------------------------------------------------------------------------- vocabulaire et mots
SYN2 = SYN + [("route", "chemin"), ("habit", "vêtement"), ("auto", "voiture"), ("gamin", "enfant"), ("cesser", "arrêter"), ("rire", "sourire?"), ("courageux", "vaillant"), ("copain", "ami"), ("bagage", "valise")]
SYN2 = [x for x in SYN2 if "?" not in x[1]]


def synonym(rng, d, g, lg):
    a, b = rng.choice(SYN2)
    pool = [y for _, y in SYN2 if y != b and y != a]
    return Draft(f"Quel mot est un synonyme de « {a} » ?", b, pool, f"« {a} » et « {b} » ont un sens proche.")


def antonym(rng, d, g, lg):
    a, b = rng.choice(ANT)
    pool = [y for _, y in ANT if y != b and y != a]
    return Draft(f"Quel est le contraire de « {a} » ?", b, pool, f"Le contraire de « {a} » est « {b} ».")


WORDS = ["école", "maison", "village", "marché", "manguier", "pirogue", "tambour", "cahier", "fleuve", "montagne", "ballon", "poisson", "banane", "orange", "cuisine", "voisin", "cousin", "jardin", "soleil",
         "nuage", "bateau", "cheval", "oiseau", "arbre", "forêt", "route", "ville", "rivière", "étoile", "lune", "table", "chaise", "porte", "fenêtre", "classe", "fleur", "feuille", "bouteille", "poule", "vache",
         "chèvre", "tortue", "chat", "chien", "livre", "stylo", "règle", "ardoise", "trousse", "pagne", "panier", "camion", "train", "avion", "vélo", "médecin", "maître", "enfant", "famille", "ami"]


def alpha_order(rng, d, g, lg):
    k = 4
    ws = rng.sample(WORDS, k)
    key = lambda w: norm(w).replace(" ", "")
    if d <= 2:
        firsts = {key(w)[0] for w in ws}
        if len(firsts) < k:
            return None
    first = rng.random() < 0.6
    srt = sorted(ws, key=key)
    right = srt[0] if first else srt[-1]
    if len({key(w) for w in ws}) < k:
        return None
    return Draft(f"Quel mot vient {'en premier' if first else 'en dernier'} dans l'ordre alphabétique : {' ; '.join(ws)} ?", right, [w for w in ws if w != right],
                 f"Rangés dans l'ordre alphabétique : {' ; '.join(srt)}.")


def letters_count(rng, d, g, lg):
    w = rng.choice(WORDS)
    n = len(w)
    return Draft(f"Combien de lettres le mot « {w} » contient-il ?", str(n), [str(x) for x in range(max(2, n - 3), n + 4) if x != n][:6], f"« {w} » s'écrit {' - '.join(w)} : {n} lettres.")


def words_count(rng, d, g, lg):
    n = rng.randint(3, 8)
    ws = []
    pool = ["le", "chat", "dort", "sur", "la", "table", "Awa", "mange", "une", "mangue", "mûre", "mon", "frère", "joue", "au", "ballon", "dans", "cour", "de", "école", "les", "enfants", "chantent", "fort"]
    ws = rng.sample(pool, n)
    sent = " ".join(ws)
    return Draft(f"Combien de mots y a-t-il dans la suite « {sent} » ?", str(n), [str(x) for x in range(max(2, n - 2), n + 3) if x != n], f"On compte les mots séparés par des espaces : {n}.")


def next_letter(rng, d, g, lg):
    al = "abcdefghijklmnopqrstuvwxyz"
    i = rng.randint(0, 24)
    nxt = rng.random() < 0.5
    base = al[i + 1] if nxt else al[i]
    ask_ = al[i + 2] if nxt and i + 2 < 26 else None
    if nxt:
        if i + 2 > 25:
            return None
        a = al[i + 1]
        r = al[i + 2]
        wr = [al[i], al[i + 3] if i + 3 < 26 else al[i - 1], al[i - 1] if i >= 1 else al[i + 4]]
        return Draft(f"Quelle lettre vient juste après « {a} » dans l'alphabet ?", r, wr + [al[(i + 5) % 26]], f"Alphabet : … {al[i]} {a} {r} …")
    a = al[i + 1]
    r = al[i]
    wr = [al[i + 2] if i + 2 < 26 else al[i - 2], al[i - 1] if i >= 1 else al[i + 3], al[i + 3] if i + 3 < 26 else al[i - 3]]
    return Draft(f"Quelle lettre vient juste avant « {a} » dans l'alphabet ?", r, wr, f"Alphabet : … {al[i]} {a} …")


SPELL = ["ordinateur", "attention", "difficile", "bibliothèque", "février", "mangue", "pirogue", "village", "marché", "école", "enfant", "famille", "voyage", "cuisine", "toujours", "beaucoup", "maintenant",
         "aujourd'hui", "pendant", "devant", "derrière", "quelquefois", "pourquoi", "parce que", "croissant", "souvent", "autrefois", "bonjour", "merci", "gentil", "cahier", "crayon", "maître", "élève",
         "étoile", "rivière", "forêt", "montagne", "jardin", "sourire", "mouchoir", "oiseau", "chapeau", "tableau", "bateau", "cheval", "animal", "journal", "hôpital", "pharmacie", "docteur", "infirmier",
         "boulanger", "cultivateur", "agriculteur", "pêcheur", "vendeur", "chauffeur", "commerçant", "président", "ministre", "député", "capitale", "frontière", "continent", "océan", "désert", "savane",
         "équateur", "région", "département", "arrondissement", "village", "quartier", "calendrier", "dimanche", "septembre", "décembre", "semaine", "vacances", "récréation", "dictée", "problème", "leçon"]
SPELL = [w for w in SPELL if " " not in w]


def mis_spell(rng, w):
    out = []
    # double / single consonant
    for i in range(len(w) - 1):
        if w[i] == w[i + 1] and w[i] not in "aeiou":
            out.append(w[:i] + w[i + 1:])
    for i in range(1, len(w) - 1):
        if w[i] in "nmtlrsp" and w[i] != w[i - 1] and w[i] != w[i + 1]:
            out.append(w[:i] + w[i] + w[i:])
    # accents
    for a, b in (("é", "e"), ("è", "e"), ("ê", "e"), ("ô", "o"), ("î", "i"), ("â", "a"), ("é", "è"), ("è", "é"), ("e", "é"), ("û", "u")):
        if a in w:
            out.append(w.replace(a, b, 1))
    # adjacent swap
    for i in range(1, len(w) - 2):
        if w[i] != w[i + 1]:
            out.append(w[:i] + w[i + 1] + w[i] + w[i + 2:])
    # c / ç / s / ss
    for a, b in (("ss", "s"), ("s", "ss"), ("ph", "f"), ("ou", "u"), ("au", "o"), ("eau", "o"), ("qu", "k"), ("gn", "n"), ("ai", "è"), ("oi", "oa")):
        if a in w:
            out.append(w.replace(a, b, 1))
    seen, res = set(), []
    for o in out:
        if o != w and o not in seen and o not in SPELL and o not in WORDS:
            seen.add(o)
            res.append(o)
    return res


def spelling(rng, d, g, lg):
    w = rng.choice(SPELL if d >= 2 else SPELL[:30])
    wr = mis_spell(rng, w)
    rng.shuffle(wr)
    if len(wr) < 3:
        return None
    return Draft("Quelle est la bonne orthographe de ce mot ?", w, wr[:6], f"Le mot s'écrit « {w} ».")


def reg_all():
    reg("conjugation", conjugation, F2, 330, cat="Conjugaison", src_fr=SRC)
    reg("tense-id", tense_id, F3, 210, cat="Conjugaison", src_fr=SRC)
    reg("verb-group", verb_group, F3, 90, cat="Conjugaison", src_fr=SRC)
    reg("infinitive", infinitive, F2, 220, cat="Conjugaison", src_fr=SRC)
    reg("article-def", article_g, FRC[:3], 70, cat="Grammaire", src_fr=SRC)
    reg("article-indef", indef_article, FRC[:3], 70, cat="Grammaire", src_fr=SRC)
    reg("gender-noun", gender_q, FRC[:4], 80, cat="Grammaire", src_fr=SRC)
    reg("sing-plur", sing_plur_id, FRC[:3], 90, cat="Grammaire", src_fr=SRC)
    reg("plural-noun", plural_noun, F2, 130, cat="Orthographe", src_fr=SRC)
    reg("plural-group", plural_group, F3, 200, cat="Orthographe", src_fr=SRC)
    reg("feminine", feminine, F2, 50, cat="Orthographe", src_fr=SRC)
    reg("agree-verb", agreement_verb, F3, 180, cat="Orthographe", src_fr=SRC)
    reg("agree-etre", etre_agreement, F4, 150, cat="Orthographe", src_fr=SRC)
    reg("homophone", homophone_tpl, F2, 260, cat="Orthographe", src_fr=SRC)
    reg("nature", nature_q, F3, 220, cat="Grammaire", src_fr=SRC)
    reg("sentence-type", sentence_type, F3, 150, cat="Grammaire", src_fr=SRC)
    reg("subject", subject_q, F4, 200, cat="Grammaire", src_fr=SRC)
    reg("cod", cod_q, F4 + [], 130, cat="Grammaire", src_fr=SRC)
    reg("synonym", synonym, F3, 50, cat="Vocabulaire", src_fr=SRC)
    reg("antonym", antonym, F2, 60, cat="Vocabulaire", src_fr=SRC)
    reg("alpha-order", alpha_order, FRC[:5], 160, cat="Vocabulaire", src_fr=SRC)
    reg("letters-count", letters_count, FRC[:2], 60, cat="Vocabulaire", src_fr=SRC)
    reg("words-count", words_count, FRC[:2], 80, cat="Vocabulaire", src_fr=SRC)
    reg("next-letter", next_letter, FRC[:2], 60, cat="Vocabulaire", src_fr=SRC)
    reg("spelling", spelling, F2, 230, cat="Orthographe", src_fr=SRC)


reg_all()
