"""Cameroun : découpage administratif, villes, géographie physique, parcs, infrastructures. Faits écrits par l'assistant
d'après la connaissance générale : tous en statut `review` (voir les fiches ci-dessous pour savoir où les vérifier)."""
from ..facts_engine import fq, pairs, pick, source

CM, G = "general", "CM"

source("cm-admin", "Découpage administratif du Cameroun (10 régions, 58 départements)", "texte officiel",
       "Comparer avec le décret portant organisation administrative du Cameroun et l'annuaire statistique de l'INS (chefs-lieux des départements).")
source("cm-geo", "Géographie physique et villes du Cameroun", "ouvrage de référence",
       "Comparer avec un atlas du Cameroun (INC / Atlas of Cameroon) et l'Institut national de cartographie.")
source("cm-nature", "Parcs, réserves et nature du Cameroun", "ouvrage de référence",
       "Comparer avec le MINFOF (ministère des Forêts et de la Faune) et la liste des aires protégées.")
source("cm-infra", "Infrastructures, villes et quartiers du Cameroun", "ouvrage de référence",
       "Comparer avec des sources locales (communes, ministères) ; les quartiers peuvent changer de nom.")

REGIONS = [("Adamaoua", "Ngaoundéré", 5), ("Centre", "Yaoundé", 10), ("Est", "Bertoua", 4), ("Extrême-Nord", "Maroua", 6), ("Littoral", "Douala", 4),
           ("Nord", "Garoua", 4), ("Nord-Ouest", "Bamenda", 7), ("Ouest", "Bafoussam", 8), ("Sud", "Ebolowa", 4), ("Sud-Ouest", "Buea", 6)]

# (département, chef-lieu, région)
DEPTS = [
    ("Djérem", "Tibati", "Adamaoua"), ("Faro-et-Déo", "Tignère", "Adamaoua"), ("Mayo-Banyo", "Banyo", "Adamaoua"), ("Mbéré", "Meiganga", "Adamaoua"), ("Vina", "Ngaoundéré", "Adamaoua"),
    ("Haute-Sanaga", "Nanga-Eboko", "Centre"), ("Lékié", "Monatélé", "Centre"), ("Mbam-et-Inoubou", "Bafia", "Centre"), ("Mbam-et-Kim", "Ntui", "Centre"), ("Méfou-et-Afamba", "Mfou", "Centre"),
    ("Méfou-et-Akono", "Ngoumou", "Centre"), ("Mfoundi", "Yaoundé", "Centre"), ("Nyong-et-Kéllé", "Éséka", "Centre"), ("Nyong-et-Mfoumou", "Akonolinga", "Centre"), ("Nyong-et-So'o", "Mbalmayo", "Centre"),
    ("Boumba-et-Ngoko", "Yokadouma", "Est"), ("Haut-Nyong", "Abong-Mbang", "Est"), ("Kadey", "Batouri", "Est"), ("Lom-et-Djérem", "Bertoua", "Est"),
    ("Diamaré", "Maroua", "Extrême-Nord"), ("Logone-et-Chari", "Kousséri", "Extrême-Nord"), ("Mayo-Danay", "Yagoua", "Extrême-Nord"), ("Mayo-Kani", "Kaélé", "Extrême-Nord"),
    ("Mayo-Sava", "Mora", "Extrême-Nord"), ("Mayo-Tsanaga", "Mokolo", "Extrême-Nord"),
    ("Moungo", "Nkongsamba", "Littoral"), ("Nkam", "Yabassi", "Littoral"), ("Sanaga-Maritime", "Édéa", "Littoral"), ("Wouri", "Douala", "Littoral"),
    ("Bénoué", "Garoua", "Nord"), ("Faro", "Poli", "Nord"), ("Mayo-Louti", "Guider", "Nord"), ("Mayo-Rey", "Tcholliré", "Nord"),
    ("Boyo", "Fonfuka", "Nord-Ouest"), ("Bui", "Kumbo", "Nord-Ouest"), ("Donga-Mantung", "Nkambé", "Nord-Ouest"), ("Menchum", "Wum", "Nord-Ouest"), ("Mezam", "Bamenda", "Nord-Ouest"),
    ("Momo", "Mbengwi", "Nord-Ouest"), ("Ngo-Ketunjia", "Ndop", "Nord-Ouest"),
    ("Bamboutos", "Mbouda", "Ouest"), ("Haut-Nkam", "Bafang", "Ouest"), ("Hauts-Plateaux", "Baham", "Ouest"), ("Koung-Khi", "Bandjoun", "Ouest"), ("Menoua", "Dschang", "Ouest"),
    ("Mifi", "Bafoussam", "Ouest"), ("Ndé", "Bangangté", "Ouest"), ("Noun", "Foumban", "Ouest"),
    ("Dja-et-Lobo", "Sangmélima", "Sud"), ("Mvila", "Ebolowa", "Sud"), ("Océan", "Kribi", "Sud"), ("Vallée-du-Ntem", "Ambam", "Sud"),
    ("Fako", "Limbé", "Sud-Ouest"), ("Koupé-Manengouba", "Bangem", "Sud-Ouest"), ("Lebialem", "Menji", "Sud-Ouest"), ("Manyu", "Mamfe", "Sud-Ouest"), ("Meme", "Kumba", "Sud-Ouest"), ("Ndian", "Mundemba", "Sud-Ouest"),
]
assert len(DEPTS) == 58
ALL_REGIONS = [r[0] for r in REGIONS]
ALL_CHEFS = [d[1] for d in DEPTS]
BIG_CITIES = ["Douala", "Yaoundé", "Garoua", "Maroua", "Bafoussam", "Bamenda", "Ngaoundéré", "Bertoua", "Ebolowa", "Buea", "Kribi", "Limbé", "Édéa", "Kumba", "Nkongsamba", "Foumban", "Dschang"]

# ------------------------------------------------------------------ régions
ART = {"Adamaoua": "de l'Adamaoua", "Centre": "du Centre", "Est": "de l'Est", "Extrême-Nord": "de l'Extrême-Nord", "Littoral": "du Littoral", "Nord": "du Nord",
       "Nord-Ouest": "du Nord-Ouest", "Ouest": "de l'Ouest", "Sud": "du Sud", "Sud-Ouest": "du Sud-Ouest"}
for r, c, n in REGIONS:
    fq(CM, "cm-region-chef-fwd", f"Quel est le chef-lieu de la région {ART[r]} ?", c, pick("rc" + r, ALL_CHEFS + BIG_CITIES, c, 9), f"Le chef-lieu de la région {ART[r]} est {c}.", "cm-admin", G, "Géographie du Cameroun", 1)
    fq(CM, "cm-region-chef-rev", f"{c} est le chef-lieu de quelle région ?", r, pick("rr" + r, ALL_REGIONS, r, 9), f"{c} est le chef-lieu de la région {ART[r]}.", "cm-admin", G, "Géographie du Cameroun", 1)
    fq(CM, "cm-region-nbdept", f"Combien de départements compte la région {ART[r]} ?", str(n), [str(x) for x in range(2, 12) if x != n], f"La région {ART[r]} compte {n} départements.", "cm-admin", G, "Géographie du Cameroun", 3)
fq(CM, "cm-anglophone-regions", "Quelles sont les deux régions anglophones du Cameroun ?", "Nord-Ouest et Sud-Ouest",
   ["Nord et Sud", "Littoral et Ouest", "Adamaoua et Est", "Centre et Sud", "Extrême-Nord et Nord"], "Les deux régions anglophones sont le Nord-Ouest (Bamenda) et le Sud-Ouest (Buea).", "cm-admin", G, "Géographie du Cameroun", 1)
fq(CM, "cm-nb-regions", "Combien de régions compte le Cameroun ?", "10", ["8", "9", "11", "12", "7", "6"], "Le Cameroun compte 10 régions (anciennes provinces).", "cm-admin", G, "Géographie du Cameroun", 1)
fq(CM, "cm-nb-depts", "Combien de départements compte le Cameroun ?", "58", ["48", "55", "60", "62", "50", "52"], "Le Cameroun compte 58 départements.", "cm-admin", G, "Géographie du Cameroun", 3)
fq(CM, "cm-capital-pol", "Quelle est la capitale politique du Cameroun ?", "Yaoundé", ["Douala", "Garoua", "Bafoussam", "Bamenda", "Buea"], "Yaoundé est la capitale politique ; Douala est la capitale économique.", "cm-geo", G, "Géographie du Cameroun", 1)
fq(CM, "cm-capital-eco", "Quelle est la capitale économique du Cameroun ?", "Douala", ["Yaoundé", "Kribi", "Limbé", "Édéa", "Garoua"], "Douala, sur l'estuaire du Wouri, est la capitale économique et la plus grande ville.", "cm-geo", G, "Géographie du Cameroun", 1)
fq(CM, "cm-biggest-city", "Quelle est la plus grande ville du Cameroun ?", "Douala", ["Yaoundé", "Garoua", "Bafoussam", "Maroua", "Bamenda"], "Douala est la ville la plus peuplée du pays.", "cm-geo", G, "Géographie du Cameroun", 1)
fq(CM, "cm-ex-provinces", "Comment appelait-on les régions du Cameroun avant 2008 ?", "Les provinces", ["Les cantons", "Les préfectures", "Les États", "Les districts", "Les wilayas"], "Jusqu'en 2008, les dix régions s'appelaient des provinces.", "cm-admin", G, "Géographie du Cameroun", 3)

# ------------------------------------------------------------------ départements
pairs(CM, "cm-dept-chef", [(d, c) for d, c, _ in DEPTS], "Quel est le chef-lieu du département {a} ?", "De quel département {b} est-il le chef-lieu ?",
      region=G, cat="Géographie du Cameroun", src="cm-admin", diff=3, expl="Le chef-lieu du département {a} est {b}.", rev_expl="{b} est le chef-lieu du département {a}.")
pairs(CM, "cm-dept-region", [(d, r) for d, _, r in DEPTS], "Dans quelle région se trouve le département {a} ?", None, region=G, cat="Géographie du Cameroun", src="cm-admin", diff=3,
      expl="Le département {a} fait partie de la région {b}.")
pairs(CM, "cm-chef-region", [(c, r) for _, c, r in DEPTS], "Dans quelle région se trouve la ville de {a} ?", None, region=G, cat="Géographie du Cameroun", src="cm-admin", diff=2,
      expl="{a}, chef-lieu de département, se trouve dans la région {b}.")
# which region of the department of a given chef-lieu: "Quel département a pour chef-lieu X" covered above; per-region listing questions
for reg in ALL_REGIONS:
    members = [d for d, c, r in DEPTS if r == reg]
    for d, c, r in DEPTS:
        if r != reg:
            continue
    others = [d for d, c, r in DEPTS if r != reg]
    for m in members:
        fq(CM, "cm-dept-in-region", f"Lequel de ces départements fait partie de la région {ART[reg]} ?" + f" ({m} ?)" if False else f"Quel département de la région {ART[reg]} a pour chef-lieu {[c for d, c, r in DEPTS if d == m][0]} ?",
           m, pick("dept-in" + m + reg, members, m, 9), f"{m} est un département de la région {reg} ({', '.join(members)}).", "cm-admin", G, "Géographie du Cameroun", 3) if len(members) >= 4 else None

# ------------------------------------------------------------------ villes (hors chefs-lieux de département) -> région
CITIES = [("Mbanga", "Littoral"), ("Loum", "Littoral"), ("Obala", "Centre"), ("Mbandjock", "Centre"), ("Saa", "Centre"),
          ("Tiko", "Sud-Ouest"), ("Muyuka", "Sud-Ouest"), ("Idenau", "Sud-Ouest"), ("Mutengene", "Sud-Ouest"), ("Fontem", "Sud-Ouest"), ("Bamusso", "Sud-Ouest"),
          ("Bali", "Nord-Ouest"), ("Bafut", "Nord-Ouest"), ("Batibo", "Nord-Ouest"), ("Ndu", "Nord-Ouest"), ("Fundong", "Nord-Ouest"), ("Jakiri", "Nord-Ouest"),
          ("Bamendjou", "Ouest"), ("Foumbot", "Ouest"), ("Galim", "Ouest"),
          ("Figuil", "Nord"), ("Lagdo", "Nord"), ("Pitoa", "Nord"), ("Rey-Bouba", "Nord"),
          ("Waza", "Extrême-Nord"), ("Maga", "Extrême-Nord"), ("Guidiguis", "Extrême-Nord"),
          ("Garoua-Boulaï", "Est"), ("Bétaré-Oya", "Est"), ("Lomié", "Est"), ("Mbang", "Est"), ("Ngoura", "Est"), ("Moloundou", "Est"),
          ("Ngaoundal", "Adamaoua"),
          ("Campo", "Sud"), ("Lolodorf", "Sud"), ("Akom II", "Sud"), ("Bipindi", "Sud"), ("Zoétélé", "Sud"), ("Djoum", "Sud"), ("Meyomessala", "Sud"), ("Mintom", "Sud")]
pairs(CM, "cm-city-region", CITIES, "Dans quelle région du Cameroun se trouve la ville de {a} ?", None, region=G, cat="Géographie du Cameroun", src="cm-geo", diff=4,
      expl="{a} se trouve dans la région {b}.")

# ------------------------------------------------------------------ géographie physique
PHYS = [
    ("Quel est le plus haut sommet du Cameroun ?", "Le mont Cameroun", ["Le mont Oku", "Le mont Manengouba", "Le mont Bamboutos", "Le mont Kupe", "Les monts Mandara"], "Le mont Cameroun (Fako), un volcan actif, culmine à environ 4 000 m.", 1),
    ("Dans quelle région se trouve le mont Cameroun ?", "Sud-Ouest", ["Littoral", "Ouest", "Nord-Ouest", "Centre", "Sud"], "Le mont Cameroun domine Buea et Limbé, dans le Sud-Ouest.", 2),
    ("Quel est le plus long fleuve entièrement camerounais ?", "La Sanaga", ["Le Wouri", "Le Nyong", "La Bénoué", "Le Ntem", "Le Logone"], "La Sanaga naît dans l'Adamaoua et se jette dans l'Atlantique près d'Édéa.", 2),
    ("Quel fleuve se jette dans l'estuaire de Douala ?", "Le Wouri", ["La Sanaga", "Le Nyong", "Le Ntem", "Le Mungo", "La Bénoué"], "Le Wouri forme l'estuaire sur lequel est bâtie Douala.", 2),
    ("Quel fleuve camerounais coule vers le Nigeria avant de rejoindre le Niger ?", "La Bénoué", ["La Sanaga", "Le Wouri", "Le Nyong", "Le Ntem", "Le Dja"], "La Bénoué traverse Garoua puis entre au Nigeria où elle se jette dans le Niger.", 3),
    ("Quel fleuve forme une partie de la frontière entre le Cameroun et le Tchad ?", "Le Logone", ["La Sanaga", "Le Wouri", "Le Nyong", "Le Ntem", "Le Mungo"], "Le Logone, affluent du Chari, longe la frontière avec le Tchad.", 3),
    ("Quel fleuve du Sud du Cameroun se jette dans l'Atlantique près de Campo, à la frontière avec la Guinée équatoriale ?", "Le Ntem", ["La Sanaga", "Le Wouri", "La Bénoué", "Le Logone", "Le Mungo"], "Le Ntem (ou Campo) arrose le Sud du Cameroun jusqu'à l'Atlantique.", 4),
    ("Quel lac partagé avec le Nigeria, le Niger et le Tchad borde l'Extrême-Nord du Cameroun ?", "Le lac Tchad", ["Le lac Nyos", "Le lac Barombi Mbo", "Le lac Bamendjing", "Le lac Lagdo", "Le lac Mbakaou"], "Le lac Tchad est partagé entre quatre pays, dont le Cameroun.", 2),
    ("Quel lac de cratère du Nord-Ouest est célèbre pour la catastrophe de gaz de 1986 ?", "Le lac Nyos", ["Le lac Tchad", "Le lac Barombi Mbo", "Le lac Bamendjing", "Le lac Lagdo", "Le lac Ossa"], "Le dégagement de gaz carbonique du lac Nyos fit environ 1 700 morts en août 1986.", 3),
    ("Sur quel fleuve se trouve le barrage de Lagdo ?", "La Bénoué", ["La Sanaga", "Le Wouri", "Le Logone", "Le Nyong", "Le Ntem"], "Le barrage de Lagdo est construit sur la Bénoué, dans le Nord.", 3),
    ("Sur quel fleuve se trouvent les barrages d'Édéa et de Song-Loulou ?", "La Sanaga", ["Le Wouri", "La Bénoué", "Le Nyong", "Le Logone", "Le Ntem"], "Les centrales d'Édéa et de Song-Loulou utilisent l'eau de la Sanaga.", 3),
    ("Quelles chutes de la région de Kribi se jettent directement dans l'océan Atlantique ?", "Les chutes de la Lobé", ["Les chutes d'Ekom-Nkam", "Les chutes de la Metche", "Les chutes de la Vina", "Les chutes de Nachtigal", "Les chutes de Mbiame"], "Les chutes de la Lobé, près de Kribi, tombent dans l'océan.", 2),
    ("Quelles chutes de la Vina se trouvent près de Ngaoundéré ?", "Les chutes de la Vina", ["Les chutes de la Lobé", "Les chutes d'Ekom-Nkam", "Les chutes de la Metche", "Les chutes de Nachtigal", "Les chutes de Mbiame"], "Les chutes de la Vina sont proches de Ngaoundéré, dans l'Adamaoua.", 3),
    ("Quel plateau, au centre du pays, sépare le Nord du Sud du Cameroun ?", "Le plateau de l'Adamaoua", ["Le plateau de Bamenda", "Le plateau du Mandara", "Le plateau de l'Est", "Le plateau de Kribi", "Le plateau de Makénéné"], "L'Adamaoua est un haut plateau qui sépare les régions du Nord de celles du Sud.", 2),
    ("Quels monts se trouvent à l'Extrême-Nord, à la frontière avec le Nigeria ?", "Les monts Mandara", ["Les monts Bamboutos", "Les monts Manengouba", "Les monts Rumpi", "Les monts Kupe", "Les monts Alantika"], "Les monts Mandara bordent l'Extrême-Nord (régions de Mokolo, Mora).", 3),
    ("Dans quel golfe s'ouvre le littoral camerounais ?", "Le golfe de Guinée", ["Le golfe d'Aden", "Le golfe du Mexique", "Le golfe de Gascogne", "Le golfe Persique", "Le golfe de Syrte"], "Le Cameroun donne sur le golfe de Guinée, dans l'océan Atlantique.", 1),
    ("Quel océan borde le Cameroun ?", "L'océan Atlantique", ["L'océan Indien", "L'océan Pacifique", "L'océan Arctique", "L'océan Austral", "La mer Rouge"], "Le littoral camerounais, long d'environ 400 km, est sur l'Atlantique.", 1),
    ("Combien de pays ont une frontière terrestre avec le Cameroun ?", "6", ["4", "5", "7", "8", "3"], "Nigeria, Tchad, Centrafrique, Congo, Gabon et Guinée équatoriale.", 2),
    ("Quel pays voisin est situé à l'ouest du Cameroun ?", "Le Nigeria", ["Le Tchad", "Le Gabon", "Le Congo", "La Centrafrique", "Le Niger"], "Le Nigeria borde le Cameroun à l'ouest.", 1),
    ("Quel pays voisin est situé au sud-est du Cameroun, de l'autre côté de la Sangha ?", "Le Congo", ["Le Nigeria", "Le Tchad", "Le Gabon", "La Guinée équatoriale", "Le Soudan"], "Le Congo (Brazzaville) touche le Sud-Est du Cameroun.", 3),
    ("Quel pays voisin du Cameroun se trouve au sud, avec la Guinée équatoriale ?", "Le Gabon", ["Le Tchad", "Le Nigeria", "La Centrafrique", "Le Niger", "Le Bénin"], "Le Gabon et la Guinée équatoriale bordent le Sud du Cameroun.", 2),
    ("Quel pays voisin du Cameroun se trouve à l'est ?", "La République centrafricaine", ["Le Nigeria", "Le Gabon", "Le Soudan", "La Guinée équatoriale", "Le Niger"], "La République centrafricaine borde l'Est du Cameroun.", 2),
    ("Quel pays voisin du Cameroun se trouve au nord-est ?", "Le Tchad", ["Le Niger", "Le Soudan", "La Libye", "La République centrafricaine", "Le Nigeria"], "Le Tchad borde le Nord-Est du Cameroun.", 1),
    ("Quel surnom donne-t-on souvent au Cameroun pour la diversité de ses paysages et de ses peuples ?", "L'Afrique en miniature", ["La perle du Sahel", "Le pays des mille collines", "La porte du désert", "Le géant d'Afrique", "Le jardin de l'Afrique"], "Le Cameroun regroupe presque tous les climats et paysages du continent.", 1),
    ("Quel type de climat domine dans le Sud du Cameroun ?", "Équatorial", ["Désertique", "Méditerranéen", "Polaire", "Tempéré océanique", "Continental"], "Le Sud est chaud et humide toute l'année (climat de type équatorial).", 2),
    ("Quel climat caractérise l'Extrême-Nord du Cameroun ?", "Sahélien", ["Équatorial", "Montagnard", "Océanique", "Méditerranéen", "Polaire"], "L'Extrême-Nord est chaud et sec, de type sahélien.", 2),
    ("Quel lieu du Sud-Ouest, au pied du mont Cameroun, compte parmi les plus pluvieux d'Afrique ?", "Debundscha", ["Maroua", "Garoua", "Ngaoundéré", "Bertoua", "Foumban"], "Debundscha, près de Limbé, reçoit une dizaine de mètres de pluie par an.", 4),
    ("Quelle est la monnaie utilisée au Cameroun ?", "Le franc CFA (XAF)", ["Le naira", "Le cédi", "Le dirham", "Le shilling", "Le rand"], "Le franc CFA de l'Afrique centrale (XAF) est émis par la BEAC.", 1),
    ("Quelle banque centrale émet le franc CFA utilisé au Cameroun ?", "La BEAC", ["La BCEAO", "La Banque de France", "La Banque mondiale", "La BAD", "La BDEAC"], "La Banque des États de l'Afrique centrale (BEAC) a son siège à Yaoundé.", 3),
    ("Quel est l'indicatif téléphonique international du Cameroun ?", "+237", ["+233", "+234", "+235", "+241", "+242"], "Le Cameroun a pour indicatif +237.", 2),
    ("Quelle extension de nom de domaine est celle du Cameroun ?", ".cm", [".ca", ".cn", ".cr", ".cy", ".cd"], "Le domaine Internet national du Cameroun est .cm.", 2),
]
for q, right, wr, expl, d in PHYS:
    fq(CM, "cm-phys", q, right, wr, expl, "cm-geo", G, "Géographie du Cameroun", d)

# ------------------------------------------------------------------ parcs et nature
PARKS = [("Waza", "Extrême-Nord"), ("Bouba Njida", "Nord"), ("Faro", "Nord"), ("Bénoué", "Nord"), ("Korup", "Sud-Ouest"), ("Takamanda", "Sud-Ouest"), ("Campo-Ma'an", "Sud"),
         ("Lobéké", "Est"), ("Boumba-Bek", "Est"), ("Nki", "Est"), ("Mbam-et-Djerem", "Centre"), ("Kalamaloué", "Extrême-Nord")]
pairs(CM, "cm-park-region", [(f"le parc national de {p}", r) for p, r in PARKS], "Dans quelle région se trouve {a} ?", None, region=G, cat="Nature du Cameroun", src="cm-nature", diff=3,
      expl="{a} est situé dans la région {b}.")
NATURE = [
    ("Quel parc national de l'Extrême-Nord est célèbre pour ses éléphants, lions et girafes ?", "Waza", ["Korup", "Lobéké", "Campo-Ma'an", "Boumba-Bek", "Takamanda"], "Le parc de Waza, dans la plaine du Logone, est l'un des plus visités du pays.", 2),
    ("Quel parc national du Sud-Ouest protège l'une des plus anciennes forêts tropicales d'Afrique ?", "Korup", ["Waza", "Bouba Njida", "Lobéké", "Faro", "Kalamaloué"], "Korup est une forêt tropicale humide très ancienne, riche en primates.", 3),
    ("Quelle réserve du Sud est classée au patrimoine mondial de l'UNESCO ?", "La réserve du Dja", ["La réserve de Waza", "La réserve de Korup", "La réserve de Lobéké", "La réserve de Kalamaloué", "La réserve de Faro"], "La réserve de faune du Dja est inscrite au patrimoine mondial depuis 1987.", 4),
    ("Quel grand primate vit dans les forêts du Sud et de l'Est du Cameroun ?", "Le gorille", ["Le babouin des steppes", "Le macaque", "Le lémurien", "L'orang-outan", "Le gibbon"], "Le gorille de plaine de l'Ouest vit dans les forêts camerounaises ; l'orang-outan vit en Asie.", 2),
    ("Quel animal est l'emblème de l'équipe nationale de football du Cameroun ?", "Le lion", ["L'éléphant", "Le léopard", "Le buffle", "L'aigle", "Le crocodile"], "L'équipe masculine s'appelle les Lions Indomptables.", 1),
    ("Quel peuple de chasseurs-cueilleurs vit dans les forêts de l'Est et du Sud du Cameroun ?", "Les Baka", ["Les Peuls", "Les Bamoun", "Les Douala", "Les Bamiléké", "Les Mousgoum"], "Les Baka (« pygmées ») vivent dans les forêts de l'Est et du Sud.", 3),
    ("Quel peuple du Nord-Ouest et de l'Adamaoua pratique surtout l'élevage de bovins ?", "Les Mbororo", ["Les Bassa", "Les Douala", "Les Ewondo", "Les Baka", "Les Bakweri"], "Les Mbororo (Peuls nomades) sont des éleveurs de bovins.", 3),
    ("Quelle culture commerciale est surtout produite dans le Nord du Cameroun (région de Garoua) ?", "Le coton", ["Le cacao", "La banane", "Le palmier à huile", "L'hévéa", "Le café"], "Le coton est la grande culture de rente du Nord (SODECOTON).", 2),
    ("Quelle culture est typique des régions du Centre, du Sud et du Sud-Ouest pour l'exportation ?", "Le cacao", ["Le coton", "Le mil", "Le sorgho", "L'arachide", "Le blé"], "Le Cameroun est un important producteur de cacao.", 2),
    ("Quel oléoduc relie les champs pétroliers du Tchad au terminal de Kribi ?", "L'oléoduc Tchad-Cameroun", ["Le gazoduc transsaharien", "L'oléoduc Doba-Lagos", "L'oléoduc Kribi-Douala", "L'oléoduc Chari-Logone", "L'oléoduc Congo-Pointe-Noire"], "L'oléoduc Doba-Kribi (1 070 km) a été mis en service en 2003.", 4),
]
for q, right, wr, expl, d in NATURE:
    fq(CM, "cm-nature", q, right, wr, expl, "cm-nature", G, "Nature du Cameroun", d)

# ------------------------------------------------------------------ villes, quartiers, infrastructures
QUARTIERS = [("Bonanjo", "Douala"), ("Akwa", "Douala"), ("Bonapriso", "Douala"), ("Deïdo", "Douala"), ("New Bell", "Douala"), ("Bonabéri", "Douala"), ("Ndokotti", "Douala"), ("Makepe", "Douala"),
             ("Bonamoussadi", "Douala"), ("Logbessou", "Douala"), ("Kotto", "Douala"), ("Bépanda", "Douala"), ("Japoma", "Douala"), ("Bali", "Douala"),
             ("Bastos", "Yaoundé"), ("Mvog-Ada", "Yaoundé"), ("Essos", "Yaoundé"), ("Biyem-Assi", "Yaoundé"), ("Mendong", "Yaoundé"), ("Nlongkak", "Yaoundé"), ("Mvan", "Yaoundé"), ("Melen", "Yaoundé"),
             ("Etoudi", "Yaoundé"), ("Ekounou", "Yaoundé"), ("Obili", "Yaoundé"), ("Tsinga", "Yaoundé"), ("Mimboman", "Yaoundé"), ("Nkolbisson", "Yaoundé"), ("Odza", "Yaoundé"), ("Mokolo", "Yaoundé"),
             ("Ngoa-Ekélé", "Yaoundé"), ("Emana", "Yaoundé"), ("Messa", "Yaoundé"), ("Briqueterie", "Yaoundé"), ("Elig-Essono", "Yaoundé"), ("Nsimeyong", "Yaoundé"),
             ("Mankon", "Bamenda"), ("Nkwen", "Bamenda"), ("Up Station", "Bamenda"), ("Commercial Avenue", "Bamenda"), ("Mile 4", "Bamenda"),
             ("Down Beach", "Limbé"), ("Mile 17", "Buea"), ("Molyko", "Buea"), ("Bomaka", "Buea"), ("Great Soppo", "Buea"), ("Bokwango", "Buea")]
pairs(CM, "cm-quartier", QUARTIERS, "Dans quelle ville se trouve le quartier de {a} ?", None, region=G, cat="Villes du Cameroun", src="cm-infra", diff=3,
      expl="{a} est un quartier de {b}.", extra_b=["Bafoussam", "Garoua", "Maroua", "Ngaoundéré", "Bertoua", "Ebolowa", "Kribi", "Édéa"])
INFRA = [
    ("Dans quelle ville se trouve le palais présidentiel d'Etoudi ?", "Yaoundé", ["Douala", "Garoua", "Bafoussam", "Buea", "Ebolowa"], "Le palais de l'Unité est à Etoudi, quartier de Yaoundé.", 1),
    ("Quel est le principal port maritime du Cameroun ?", "Le port de Douala", ["Le port de Kribi", "Le port de Limbé", "Le port de Garoua", "Le port de Tiko", "Le port d'Édéa"], "Douala assure l'essentiel du trafic maritime du pays et du Tchad.", 1),
    ("Quel port en eau profonde a été inauguré au sud du Cameroun en 2018 ?", "Le port de Kribi", ["Le port de Douala", "Le port de Limbé", "Le port de Tiko", "Le port d'Édéa", "Le port de Campo"], "Le port en eau profonde de Kribi est destiné aux gros navires.", 2),
    ("Quelle ville abrite la principale raffinerie de pétrole du Cameroun (SONARA) ?", "Limbé", ["Douala", "Kribi", "Édéa", "Garoua", "Yaoundé"], "La SONARA (Société nationale de raffinage) se trouve à Limbé.", 3),
    ("Quelle ville abrite l'usine d'aluminium Alucam ?", "Édéa", ["Douala", "Kribi", "Limbé", "Nkongsamba", "Bafoussam"], "Alucam est installée à Édéa, près des barrages de la Sanaga.", 3),
    ("Quelle voie ferrée relie Douala, Yaoundé et Ngaoundéré ?", "Le Transcamerounais", ["Le Transsahélien", "Le Transgabonais", "Le Transafricain", "Le Transcongolais", "Le Transnigérian"], "Le « Transcam » relie le Littoral à l'Adamaoua ; la gestion est confiée à Camrail.", 2),
    ("Quelle ville est le terminus nord de la ligne de chemin de fer venant de Douala ?", "Ngaoundéré", ["Garoua", "Maroua", "Bertoua", "Bamenda", "Kousséri"], "La voie ferrée s'arrête à Ngaoundéré, d'où l'on poursuit par la route vers le Tchad.", 3),
    ("Dans quelle ville se trouve le stade Ahmadou Ahidjo ?", "Yaoundé", ["Douala", "Garoua", "Bafoussam", "Limbé", "Bamenda"], "Le stade Ahmadou Ahidjo est le grand stade de la capitale.", 2),
    ("Dans quelle ville se trouve le stade de Japoma, inauguré en 2021 ?", "Douala", ["Yaoundé", "Garoua", "Limbé", "Bafoussam", "Buea"], "Le stade de Japoma a été construit à Douala pour la CAN.", 3),
    ("Dans quelle ville se trouve le stade Roumdé Adjia ?", "Garoua", ["Douala", "Yaoundé", "Limbé", "Bafoussam", "Maroua"], "Le stade Roumdé Adjia est à Garoua, dans la région du Nord.", 3),
    ("Dans quelle ville se trouve le stade d'Olembe ?", "Yaoundé", ["Douala", "Garoua", "Limbé", "Bafoussam", "Kribi"], "Le stade d'Olembe se trouve en périphérie nord de Yaoundé.", 3),
    ("Quelle ville est surnommée « la cité des Sept collines » ?", "Yaoundé", ["Douala", "Bafoussam", "Garoua", "Bamenda", "Dschang"], "Yaoundé est bâtie sur plusieurs collines.", 2),
    ("Quelle ville est le chef-lieu de la région de l'Ouest et un grand centre commercial ?", "Bafoussam", ["Bamenda", "Foumban", "Dschang", "Mbouda", "Bafang"], "Bafoussam est le chef-lieu de la région de l'Ouest.", 1),
    ("Quelle ville du Sud-Ouest abrite un célèbre jardin botanique ?", "Limbé", ["Buea", "Kumba", "Mamfe", "Tiko", "Mundemba"], "Le jardin botanique de Limbé, créé en 1892, est l'un des plus anciens d'Afrique.", 3),
    ("Quelle ville abrite le palais des sultans Bamoun et un grand musée ?", "Foumban", ["Bafoussam", "Dschang", "Bangangté", "Bafang", "Bamenda"], "Foumban, dans le Noun, est la capitale du royaume Bamoun.", 2),
    ("Combien d'arrondissements compte la ville de Douala (communes d'arrondissement) ?", "6", ["4", "5", "7", "8", "3"], "Douala est divisée en six arrondissements, de Douala Ier à Douala VIe.", 4),
    ("Combien d'arrondissements compte la ville de Yaoundé (communes d'arrondissement) ?", "7", ["4", "5", "6", "8", "10"], "Yaoundé est divisée en sept arrondissements, de Yaoundé Ier à Yaoundé VIIe.", 4),
    ("Quelle université du Cameroun se trouve à Buea ?", "L'Université de Buea", ["L'Université de Douala", "L'Université de Dschang", "L'Université de Maroua", "L'Université de Bamenda", "L'Université de Ngaoundéré"], "L'Université de Buea est l'université anglophone du Sud-Ouest.", 3),
    ("Quelle ville abrite l'Université de Dschang, spécialisée en agronomie ?", "Dschang", ["Bafoussam", "Foumban", "Bangangté", "Mbouda", "Bafang"], "L'Université de Dschang se trouve dans la Menoua.", 3),
    ("Quelle ville abrite l'Université de Ngaoundéré ?", "Ngaoundéré", ["Garoua", "Maroua", "Meiganga", "Tibati", "Banyo"], "L'Université de Ngaoundéré est dans l'Adamaoua.", 3),
    ("Quelle ville abrite l'Université de Maroua ?", "Maroua", ["Garoua", "Mokolo", "Kousséri", "Yagoua", "Mora"], "L'Université de Maroua est à l'Extrême-Nord.", 3),
    ("Quelle grande ville est le principal centre du Nord-Ouest anglophone ?", "Bamenda", ["Buea", "Kumba", "Limbé", "Mamfe", "Wum"], "Bamenda est le chef-lieu de la région du Nord-Ouest.", 1),
]
for q, right, wr, expl, d in INFRA:
    fq(CM, "cm-infra", q, right, wr, expl, "cm-infra", G, "Villes du Cameroun", d)
