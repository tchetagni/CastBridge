"""Afrique : pays (capitales, monnaies, indépendance, langues), géographie, histoire, personnalités, sport.
Faits écrits par l'assistant d'après la connaissance générale : tous `review`."""
from ..facts_engine import fq, pairs, pick, source

CM, A = "general", "AF"
source("af-pays", "Pays d'Afrique : capitales, monnaies, langues officielles, indépendance", "atlas / encyclopédie",
       "Comparer avec l'Atlas mondial (Larousse / National Geographic), le site de l'Union africaine et les banques centrales (BCEAO, BEAC).")
source("af-geo", "Géographie physique et sites d'Afrique", "atlas",
       "Comparer avec un atlas et la liste du patrimoine mondial de l'UNESCO.")
source("af-hist", "Histoire et personnalités de l'Afrique", "ouvrage de référence",
       "Comparer avec l'Histoire générale de l'Afrique (UNESCO) et des biographies publiées.")
source("af-sport", "Sport et culture africains : palmarès", "palmarès officiels",
       "Comparer avec les sites de la CAF et de la FIFA ; les palmarès changent chaque année : ne garder que des résultats datés.")

# nom, capitale, monnaie, année d'indépendance (None = ambigu), puissance coloniale (None = ambigu), partie de l'Afrique, langue officielle (None = plusieurs)
C54 = [
    ("l'Algérie", "Alger", "le dinar algérien", 1962, "la France", "Afrique du Nord", None),
    ("l'Égypte", "Le Caire", "la livre égyptienne", None, None, "Afrique du Nord", "l'arabe"),
    ("la Libye", "Tripoli", "le dinar libyen", 1951, "l'Italie", "Afrique du Nord", "l'arabe"),
    ("le Maroc", "Rabat", "le dirham", 1956, "la France", "Afrique du Nord", None),
    ("la Tunisie", "Tunis", "le dinar tunisien", 1956, "la France", "Afrique du Nord", "l'arabe"),
    ("le Soudan", "Khartoum", "la livre soudanaise", 1956, None, "Afrique du Nord", None),
    ("le Soudan du Sud", "Djouba", "la livre sud-soudanaise", 2011, None, "Afrique de l'Est", "l'anglais"),
    ("le Bénin", "Porto-Novo", "le franc CFA (XOF)", 1960, "la France", "Afrique de l'Ouest", "le français"),
    ("le Burkina Faso", "Ouagadougou", "le franc CFA (XOF)", 1960, "la France", "Afrique de l'Ouest", "le français"),
    ("le Cap-Vert", "Praia", "l'escudo cap-verdien", 1975, "le Portugal", "Afrique de l'Ouest", "le portugais"),
    ("la Côte d'Ivoire", "Yamoussoukro", "le franc CFA (XOF)", 1960, "la France", "Afrique de l'Ouest", "le français"),
    ("la Gambie", "Banjul", "le dalasi", 1965, "le Royaume-Uni", "Afrique de l'Ouest", "l'anglais"),
    ("le Ghana", "Accra", "le cédi", 1957, "le Royaume-Uni", "Afrique de l'Ouest", "l'anglais"),
    ("la Guinée", "Conakry", "le franc guinéen", 1958, "la France", "Afrique de l'Ouest", "le français"),
    ("la Guinée-Bissau", "Bissau", "le franc CFA (XOF)", None, "le Portugal", "Afrique de l'Ouest", "le portugais"),
    ("le Libéria", "Monrovia", "le dollar libérien", None, None, "Afrique de l'Ouest", "l'anglais"),
    ("le Mali", "Bamako", "le franc CFA (XOF)", 1960, "la France", "Afrique de l'Ouest", "le français"),
    ("la Mauritanie", "Nouakchott", "l'ouguiya", 1960, "la France", "Afrique de l'Ouest", None),
    ("le Niger", "Niamey", "le franc CFA (XOF)", 1960, "la France", "Afrique de l'Ouest", "le français"),
    ("le Nigeria", "Abuja", "le naira", 1960, "le Royaume-Uni", "Afrique de l'Ouest", "l'anglais"),
    ("le Sénégal", "Dakar", "le franc CFA (XOF)", 1960, "la France", "Afrique de l'Ouest", "le français"),
    ("la Sierra Leone", "Freetown", "le leone", 1961, "le Royaume-Uni", "Afrique de l'Ouest", "l'anglais"),
    ("le Togo", "Lomé", "le franc CFA (XOF)", 1960, None, "Afrique de l'Ouest", "le français"),
    ("le Cameroun", "Yaoundé", "le franc CFA (XAF)", 1960, None, "Afrique centrale", None),
    ("la République centrafricaine", "Bangui", "le franc CFA (XAF)", 1960, "la France", "Afrique centrale", None),
    ("le Congo", "Brazzaville", "le franc CFA (XAF)", 1960, "la France", "Afrique centrale", "le français"),
    ("la République démocratique du Congo", "Kinshasa", "le franc congolais", 1960, "la Belgique", "Afrique centrale", "le français"),
    ("le Gabon", "Libreville", "le franc CFA (XAF)", 1960, "la France", "Afrique centrale", "le français"),
    ("la Guinée équatoriale", "Malabo", "le franc CFA (XAF)", 1968, "l'Espagne", "Afrique centrale", None),
    ("Sao Tomé-et-Principe", "São Tomé", "la dobra", 1975, "le Portugal", "Afrique centrale", "le portugais"),
    ("le Tchad", "N'Djamena", "le franc CFA (XAF)", 1960, "la France", "Afrique centrale", None),
    ("l'Angola", "Luanda", "le kwanza", 1975, "le Portugal", "Afrique australe", "le portugais"),
    ("le Burundi", None, "le franc burundais", 1962, "la Belgique", "Afrique de l'Est", None),
    ("les Comores", "Moroni", "le franc comorien", 1975, "la France", "Afrique de l'Est", None),
    ("Djibouti", "Djibouti", "le franc djiboutien", 1977, "la France", "Afrique de l'Est", None),
    ("l'Érythrée", "Asmara", "le nakfa", 1993, None, "Afrique de l'Est", None),
    ("l'Éthiopie", "Addis-Abeba", "le birr", None, None, "Afrique de l'Est", "l'amharique"),
    ("le Kenya", "Nairobi", "le shilling kényan", 1963, "le Royaume-Uni", "Afrique de l'Est", None),
    ("Madagascar", "Antananarivo", "l'ariary", 1960, "la France", "Afrique de l'Est", None),
    ("le Malawi", "Lilongwe", "le kwacha malawien", 1964, "le Royaume-Uni", "Afrique australe", None),
    ("Maurice", "Port-Louis", "la roupie mauricienne", 1968, "le Royaume-Uni", "Afrique de l'Est", None),
    ("le Mozambique", "Maputo", "le metical", 1975, "le Portugal", "Afrique australe", "le portugais"),
    ("l'Ouganda", "Kampala", "le shilling ougandais", 1962, "le Royaume-Uni", "Afrique de l'Est", None),
    ("le Rwanda", "Kigali", "le franc rwandais", 1962, "la Belgique", "Afrique de l'Est", None),
    ("les Seychelles", "Victoria", "la roupie seychelloise", 1976, "le Royaume-Uni", "Afrique de l'Est", None),
    ("la Somalie", "Mogadiscio", "le shilling somalien", 1960, None, "Afrique de l'Est", None),
    ("la Tanzanie", "Dodoma", "le shilling tanzanien", None, None, "Afrique de l'Est", None),
    ("la Zambie", "Lusaka", "le kwacha zambien", 1964, "le Royaume-Uni", "Afrique australe", "l'anglais"),
    ("le Zimbabwe", "Harare", None, 1980, "le Royaume-Uni", "Afrique australe", None),
    ("l'Afrique du Sud", None, "le rand", None, None, "Afrique australe", None),
    ("le Botswana", "Gaborone", "le pula", 1966, "le Royaume-Uni", "Afrique australe", None),
    ("l'Eswatini", None, "le lilangeni", 1968, "le Royaume-Uni", "Afrique australe", None),
    ("le Lesotho", "Maseru", "le loti", 1966, "le Royaume-Uni", "Afrique australe", None),
    ("la Namibie", "Windhoek", "le dollar namibien", 1990, None, "Afrique australe", "l'anglais"),
]
assert len(C54) == 54
NAMES = [c[0] for c in C54]
CAPS = [c[1] for c in C54 if c[1]]
OTHER_CAPS = ["Pretoria", "Le Cap", "Bujumbura", "Gitega", "Lagos", "Cotonou", "Abidjan", "Dar es Salaam", "Mbabane", "Casablanca", "Nouadhibou", "Lubumbashi"]
YEAR_POOL = [str(y) for y in range(1951, 1995) if y not in (1953,)]
COUNTRIES = [c[0][0].upper() + c[0][1:] if c[0].startswith(("l'", "le ", "la ", "les ")) else c[0] for c in C54]


def cap_name(n):
    """'le Cameroun' -> 'le Cameroun' ; used inside questions as is."""
    return n


# capitale
rows = [(n, c) for n, c, *_ in C54 if c]
pairs(CM, "af-capital", rows, "Quelle est la capitale {a} ?".replace("{a}", "{a}"), None, region=A, cat="Afrique : pays et capitales", src="af-pays", diff=2, extra_b=OTHER_CAPS,
      expl="La capitale {a} est {b}.") if False else None
ART_DE = lambda n: ("de " + n[3:]) if n.startswith("le ") else ("du " + n[3:] if False else n)


def de(n):
    """'le Mali' -> 'du Mali' ; 'la Guinée' -> 'de la Guinée' ; "l'Algérie" -> "de l'Algérie" ; 'les Comores' -> 'des Comores'."""
    if n.startswith("le "):
        return "du " + n[3:]
    if n.startswith("la "):
        return "de la " + n[3:]
    if n.startswith("les "):
        return "des " + n[4:]
    if n.startswith("l'"):
        return "de " + n
    return "de " + n


def au(n):
    if n.startswith("le "):
        return "au " + n[3:]
    if n.startswith("la "):
        return "en " + n[3:]
    if n.startswith("les "):
        return "aux " + n[4:]
    if n.startswith("l'"):
        return "en " + n[2:]
    return "à " + n


for n, c, cur, yr, col, part, lang in C54:
    A = "CM" if n == "le Cameroun" else "AF"
    if c:
        fq(CM, "af-capital-fwd", f"Quelle est la capitale {de(n)} ?", c, pick("cap" + n, CAPS + OTHER_CAPS, c, 9), f"La capitale {de(n)} est {c}.", "af-pays", A, "Afrique : pays et capitales", 2 if c in ("Alger", "Dakar", "Nairobi", "Le Caire", "Abuja", "Accra", "Tunis", "Rabat", "Addis-Abeba", "Kinshasa", "Yaoundé") else 3,
           alt=({"Cotonou"} if c == "Porto-Novo" else {"Abidjan"} if c == "Yamoussoukro" else {"Dar es Salaam"} if c == "Dodoma" else ()))
        if c not in ("Djibouti", "Victoria", "Porto-Novo", "Yamoussoukro", "Dodoma"):
            fq(CM, "af-capital-rev", f"{c} est la capitale de quel pays ?", n[0].upper() + n[1:], pick("capr" + n, [x[0].upper() + x[1:] for x in NAMES], n[0].upper() + n[1:], 9), f"{c} est la capitale {de(n)}.", "af-pays", A, "Afrique : pays et capitales", 3)
    if cur:
        fq(CM, "af-currency", f"Quelle est la monnaie {de(n)} ?", cur[0].upper() + cur[1:], pick("cur" + n, sorted({x[2][0].upper() + x[2][1:] for x in C54 if x[2]}), cur[0].upper() + cur[1:], 9),
           f"La monnaie {de(n)} est {cur}.", "af-pays", A, "Afrique : économie", 3 if "CFA" not in cur else 2)
    if yr:
        fq(CM, "af-indep-year", f"En quelle année {n} est-il devenu indépendant ?".replace("l'Algérie est-il", "l'Algérie est-elle").replace(f"{n} est-il", f"{n} est-{'elle' if n.startswith(('la ', 'les ')) or n in ('l\'Algérie', 'l\'Érythrée') else 'il'}")
           if False else f"En quelle année l'indépendance {de(n)} a-t-elle été proclamée ?", str(yr), pick("yr" + n, YEAR_POOL, str(yr), 9), f"L'indépendance {de(n)} date de {yr}.", "af-pays", A, "Afrique : histoire", 3)
    if col:
        fq(CM, "af-colonizer", f"Quel pays a colonisé {n} avant son indépendance ?".replace("colonisé le ", "colonisé le ") , col[0].upper() + col[1:], [x for x in ("La France", "Le Royaume-Uni", "Le Portugal", "La Belgique", "L'Espagne", "L'Italie", "L'Allemagne") if x != col[0].upper() + col[1:]],
           f"{n[0].upper() + n[1:]} a été colonisé{'e' if n.startswith(('la ', 'les ')) else ''} par {col}.", "af-pays", A, "Afrique : histoire", 2)
    fq(CM, "af-region", f"Dans quelle partie de l'Afrique se trouve {n} ?", part, [x for x in ("Afrique du Nord", "Afrique de l'Ouest", "Afrique centrale", "Afrique de l'Est", "Afrique australe") if x != part],
       f"{n[0].upper() + n[1:]} est en {part}.", "af-pays", A, "Afrique : pays et capitales", 2)
    if lang:
        fq(CM, "af-language", f"Quelle est la langue officielle {de(n)} ?" if lang not in ("l'arabe", "l'amharique") else f"Quelle langue officielle est parlée {au(n)} ?", lang[0].upper() + lang[1:],
           [x for x in ("Le français", "L'anglais", "Le portugais", "L'espagnol", "L'arabe", "Le swahili") if x != lang[0].upper() + lang[1:]], f"La langue officielle {de(n)} est {lang}.", "af-pays", A, "Afrique : langues", 2)

AF = [
    ("Quel est le plus long fleuve d'Afrique ?", "Le Nil", ["Le Congo", "Le Niger", "Le Zambèze", "Le Limpopo", "Le Sénégal"], "Le Nil (environ 6 650 km) traverse l'Ouganda, le Soudan et l'Égypte.", 1),
    ("Quel est le deuxième plus grand fleuve d'Afrique par son débit ?", "Le Congo", ["Le Nil", "Le Niger", "Le Zambèze", "Le Volta", "L'Orange"], "Le Congo est le fleuve au plus fort débit d'Afrique.", 3),
    ("Quel fleuve traverse le Mali, le Niger et le Nigeria avant de se jeter dans le golfe de Guinée ?", "Le Niger", ["Le Nil", "Le Congo", "Le Zambèze", "Le Sénégal", "La Volta"], "Le fleuve Niger forme un grand arc en Afrique de l'Ouest.", 2),
    ("Quel est le plus haut sommet d'Afrique ?", "Le Kilimandjaro", ["Le mont Kenya", "Le mont Cameroun", "Le Toubkal", "Le Rwenzori", "Le mont Elgon"], "Le Kilimandjaro (Tanzanie) culmine à 5 895 m.", 1),
    ("Dans quel pays se trouve le Kilimandjaro ?", "La Tanzanie", ["Le Kenya", "L'Ouganda", "L'Éthiopie", "Le Rwanda", "Le Mozambique"], "Il se trouve au nord-est de la Tanzanie, près de la frontière kényane.", 2),
    ("Quel est le plus grand désert chaud du monde ?", "Le Sahara", ["Le Kalahari", "Le Namib", "Le Gobi", "L'Atacama", "Le désert d'Arabie"], "Le Sahara couvre une grande partie de l'Afrique du Nord.", 1),
    ("Quel désert se trouve en Namibie et longe l'océan Atlantique ?", "Le Namib", ["Le Sahara", "Le Kalahari", "Le Gobi", "Le Sinaï", "Le Danakil"], "Le Namib est l'un des plus vieux déserts du monde.", 3),
    ("Quel est le plus grand lac d'Afrique ?", "Le lac Victoria", ["Le lac Tanganyika", "Le lac Malawi", "Le lac Tchad", "Le lac Turkana", "Le lac Kivu"], "Le lac Victoria est partagé entre l'Ouganda, le Kenya et la Tanzanie.", 2),
    ("Quel lac d'Afrique est le plus profond ?", "Le lac Tanganyika", ["Le lac Victoria", "Le lac Malawi", "Le lac Tchad", "Le lac Turkana", "Le lac Kivu"], "Le Tanganyika est le deuxième lac le plus profond du monde.", 4),
    ("Quelles chutes célèbres se trouvent entre la Zambie et le Zimbabwe ?", "Les chutes Victoria", ["Les chutes du Niagara", "Les chutes d'Iguaçu", "Les chutes d'Ekom", "Les chutes de la Lobé", "Les chutes d'Inga"], "Les chutes Victoria sont sur le Zambèze.", 2),
    ("Quel canal relie la mer Méditerranée à la mer Rouge ?", "Le canal de Suez", ["Le canal de Panama", "Le canal de Corinthe", "Le canal du Midi", "Le canal de Kiel", "Le canal de Bruges"], "Le canal de Suez, en Égypte, a été ouvert en 1869.", 2),
    ("Quel est le plus grand pays d'Afrique par sa superficie ?", "L'Algérie", ["La République démocratique du Congo", "Le Soudan", "La Libye", "L'Éthiopie", "L'Égypte"], "Depuis la partition du Soudan en 2011, l'Algérie est le plus étendu.", 3),
    ("Quel est le pays le plus peuplé d'Afrique ?", "Le Nigeria", ["L'Éthiopie", "L'Égypte", "La République démocratique du Congo", "L'Afrique du Sud", "La Tanzanie"], "Le Nigeria compte plus de 200 millions d'habitants.", 2),
    ("Quelle est la plus grande île d'Afrique ?", "Madagascar", ["Zanzibar", "Maurice", "Bioko", "La Réunion", "Socotra"], "Madagascar est aussi la quatrième plus grande île du monde.", 2),
    ("Quelle île, au large de la Tanzanie, est célèbre pour ses épices et sa vieille ville de Stone Town ?", "Zanzibar", ["Madagascar", "Maurice", "Bioko", "Gorée", "Lamu"], "Zanzibar a longtemps été un grand comptoir d'épices et d'esclaves.", 3),
    ("Quelle île sénégalaise face à Dakar est un lieu de mémoire de la traite négrière ?", "Gorée", ["Zanzibar", "Bioko", "Robben Island", "Saint-Louis", "Lamu"], "La Maison des esclaves de Gorée est inscrite au patrimoine mondial.", 3),
    ("Sur quelle île sud-africaine Nelson Mandela a-t-il été emprisonné ?", "Robben Island", ["Gorée", "Zanzibar", "Bioko", "Maurice", "Madagascar"], "Mandela y a passé 18 de ses 27 années de prison.", 3),
    ("Dans quel pays se trouvent la grande mosquée de Djenné et la ville de Tombouctou ?", "Le Mali", ["Le Niger", "La Mauritanie", "Le Tchad", "Le Burkina Faso", "Le Sénégal"], "Djenné et Tombouctou sont inscrites au patrimoine mondial.", 3),
    ("Dans quel pays se trouvent les pyramides de Gizeh ?", "L'Égypte", ["Le Soudan", "L'Éthiopie", "La Libye", "Le Maroc", "La Tunisie"], "Les pyramides de Khéops, Képhren et Mykérinos sont près du Caire.", 1),
    ("Dans quel pays se trouvent les églises monolithes de Lalibela ?", "L'Éthiopie", ["L'Érythrée", "Le Soudan", "Le Kenya", "L'Égypte", "La Somalie"], "Les églises de Lalibela sont taillées dans le roc.", 3),
    ("Dans quel pays se trouve le parc du Serengeti, célèbre pour la grande migration des gnous ?", "La Tanzanie", ["Le Kenya", "L'Ouganda", "Le Botswana", "L'Afrique du Sud", "La Namibie"], "Le Serengeti touche la réserve kényane du Masai Mara.", 2),
    ("Dans quel pays se trouve le delta de l'Okavango ?", "Le Botswana", ["La Namibie", "La Zambie", "Le Zimbabwe", "L'Angola", "Le Mozambique"], "Le delta de l'Okavango est une vaste zone humide du Botswana.", 4),
    ("Dans quel pays se trouve le site antique de Carthage ?", "La Tunisie", ["L'Algérie", "La Libye", "Le Maroc", "L'Égypte", "Le Liban"], "Carthage est aux portes de Tunis.", 2),
    ("Quel océan borde l'Afrique à l'est ?", "L'océan Indien", ["L'océan Atlantique", "L'océan Pacifique", "L'océan Arctique", "La mer Baltique", "La mer Noire"], "La côte orientale de l'Afrique donne sur l'océan Indien.", 1),
    ("Quelle ligne imaginaire traverse l'Afrique du Gabon à la Somalie ?", "L'équateur", ["Le tropique du Cancer", "Le méridien de Greenwich", "Le tropique du Capricorne", "Le cercle polaire", "La ligne de changement de date"], "L'équateur coupe notamment le Gabon, le Congo, la RDC, l'Ouganda, le Kenya et la Somalie.", 2),
    ("Combien de pays compte l'Afrique (membres de l'Union africaine, reconnus par l'ONU) ?", "54", ["48", "50", "52", "56", "60"], "L'Afrique compte 54 États reconnus par l'ONU.", 2),
    ("Quelle organisation a succédé à l'OUA en 2002 ?", "L'Union africaine", ["La CEDEAO", "La CEMAC", "L'Union européenne", "La Ligue arabe", "Le COMESA"], "L'Organisation de l'unité africaine est devenue l'Union africaine.", 3),
    ("Où se trouve le siège de l'Union africaine ?", "Addis-Abeba", ["Nairobi", "Abuja", "Yaoundé", "Dakar", "Le Caire"], "Le siège de l'UA est à Addis-Abeba, en Éthiopie.", 3),
    ("Que signifie CEDEAO ?", "Communauté économique des États de l'Afrique de l'Ouest", ["Conseil économique des États d'Afrique et d'Orient", "Communauté économique de l'Est africain", "Centre d'études du développement de l'Afrique", "Commission économique d'Afrique et d'Orient", "Confédération des États d'Afrique de l'Ouest"], "La CEDEAO regroupe des pays d'Afrique de l'Ouest.", 3),
    ("Que signifie CEMAC ?", "Communauté économique et monétaire de l'Afrique centrale", ["Communauté économique des États du Maghreb", "Conseil économique de l'Afrique centrale", "Centre d'études monétaires d'Afrique centrale", "Communauté des États de l'Afrique maritime", "Commission économique de l'Afrique centrale"], "La CEMAC (6 pays dont le Cameroun) utilise le franc CFA (XAF).", 3),
    ("Combien de pays sont membres de la CEMAC ?", "6", ["4", "5", "8", "10", "12"], "Cameroun, Centrafrique, Congo, Gabon, Guinée équatoriale et Tchad.", 3),
    ("Où se trouve le siège de la CEMAC ?", "Bangui", ["Yaoundé", "Libreville", "Brazzaville", "N'Djamena", "Malabo"], "La CEMAC a son siège à Bangui, en République centrafricaine.", 4),
]
for q, right, wr, expl, d in AF:
    fq(CM, "af-geo", q, right, wr, expl, "af-geo", A, "Afrique : géographie", d)

HIST = [
    ("Qui est Mansa Moussa, célèbre pour son pèlerinage à La Mecque en 1324 ?", "Un empereur du Mali", ["Un roi du Ghana", "Un sultan du Maroc", "Un pharaon d'Égypte", "Un empereur d'Éthiopie", "Un roi zoulou"], "Mansa Moussa était un empereur du Mali, réputé pour sa richesse.", 3),
    ("Qui est Soundiata Keïta ?", "Le fondateur de l'empire du Mali", ["Le premier président du Ghana", "Un roi zoulou", "Un empereur d'Éthiopie", "Un pharaon d'Égypte", "Le chef de l'armée du Niger"], "Soundiata Keïta unifia le Mali au XIIIe siècle.", 3),
    ("Quel dirigeant a proclamé l'indépendance du Ghana en 1957, premier pays d'Afrique noire à y parvenir ?", "Kwame Nkrumah", ["Jomo Kenyatta", "Julius Nyerere", "Félix Houphouët-Boigny", "Léopold Senghor", "Patrice Lumumba"], "Kwame Nkrumah devient le premier chef de gouvernement du Ghana indépendant.", 2),
    ("Qui fut le premier président de l'Afrique du Sud démocratique, élu en 1994 ?", "Nelson Mandela", ["Thabo Mbeki", "Desmond Tutu", "Jacob Zuma", "Steve Biko", "Oliver Tambo"], "Nelson Mandela a été libéré en 1990 et élu président en 1994.", 1),
    ("Quel système de ségrégation raciale a existé en Afrique du Sud jusqu'en 1991 ?", "L'apartheid", ["Le colonialisme", "Le panafricanisme", "Le fédéralisme", "La négritude", "Le socialisme"], "L'apartheid, mis en place en 1948, a été démantelé dans les années 1990.", 1),
    ("Qui fut le premier Premier ministre du Congo indépendant en 1960 ?", "Patrice Lumumba", ["Joseph Kasa-Vubu", "Mobutu Sese Seko", "Moïse Tshombé", "Laurent-Désiré Kabila", "Jomo Kenyatta"], "Patrice Lumumba est assassiné en janvier 1961.", 3),
    ("Qui fut le premier président du Sénégal indépendant et un grand poète de la négritude ?", "Léopold Sédar Senghor", ["Abdou Diouf", "Léon Gontran Damas", "Aimé Césaire", "Cheikh Anta Diop", "Mamadou Dia"], "Senghor, poète, est président de 1960 à 1980.", 2),
    ("Quel poète martiniquais est co-fondateur du mouvement de la négritude avec Senghor et Damas ?", "Aimé Césaire", ["Frantz Fanon", "Édouard Glissant", "Patrick Chamoiseau", "Maryse Condé", "Alain Mabanckou"], "Aimé Césaire est l'auteur du « Cahier d'un retour au pays natal ».", 3),
    ("Qui dirigeait le Burkina Faso (ex-Haute-Volta) de 1983 à 1987 et fut surnommé « le Che africain » ?", "Thomas Sankara", ["Blaise Compaoré", "Maurice Yaméogo", "Sangoulé Lamizana", "Modibo Keïta", "Sékou Touré"], "Thomas Sankara rebaptise le pays « Burkina Faso » en 1984.", 3),
    ("Quel président a dirigé la Tanzanie après l'indépendance et prôné l'« ujamaa » ?", "Julius Nyerere", ["Jomo Kenyatta", "Kenneth Kaunda", "Kwame Nkrumah", "Milton Obote", "Samora Machel"], "Julius Nyerere fut surnommé « Mwalimu », le professeur.", 3),
    ("Qui fut le premier président du Kenya indépendant en 1963 ?", "Jomo Kenyatta", ["Daniel arap Moi", "Mwai Kibaki", "Uhuru Kenyatta", "Julius Nyerere", "Milton Obote"], "Jomo Kenyatta dirige le Kenya de 1963 à 1978.", 3),
    ("Quelle écologiste kényane a reçu le prix Nobel de la paix en 2004 ?", "Wangari Maathai", ["Ellen Johnson Sirleaf", "Leymah Gbowee", "Winnie Mandela", "Miriam Makeba", "Graça Machel"], "Wangari Maathai a fondé le Mouvement de la ceinture verte.", 3),
    ("Qui fut le premier chef d'État africain démocratiquement élu, une femme, au Libéria en 2005 ?", "Ellen Johnson Sirleaf", ["Wangari Maathai", "Graça Machel", "Joyce Banda", "Leymah Gbowee", "Michaëlle Jean"], "Ellen Johnson Sirleaf devient présidente du Libéria en janvier 2006.", 4),
    ("Quel Ghanéen a été secrétaire général de l'ONU de 1997 à 2006 ?", "Kofi Annan", ["Boutros Boutros-Ghali", "Ban Ki-moon", "Kurt Waldheim", "Javier Pérez de Cuéllar", "Dag Hammarskjöld"], "Kofi Annan a reçu le prix Nobel de la paix en 2001.", 3),
    ("Qui fut le premier Africain à recevoir le prix Nobel de littérature, en 1986 ?", "Wole Soyinka", ["Chinua Achebe", "Naguib Mahfouz", "Nadine Gordimer", "Léopold Senghor", "Ngũgĩ wa Thiong'o"], "Le Nigérian Wole Soyinka fut le premier Africain Nobel de littérature.", 4),
    ("Quel écrivain nigérian est l'auteur du roman « Le monde s'effondre » (Things Fall Apart) ?", "Chinua Achebe", ["Wole Soyinka", "Ben Okri", "Chimamanda Ngozi Adichie", "Ahmadou Kourouma", "Camara Laye"], "Le roman de Chinua Achebe paraît en 1958.", 3),
    ("Quel écrivain guinéen est l'auteur du roman « L'Enfant noir » ?", "Camara Laye", ["Ahmadou Kourouma", "Sembène Ousmane", "Cheikh Hamidou Kane", "Mongo Beti", "Amadou Hampâté Bâ"], "« L'Enfant noir » est publié en 1953.", 3),
    ("Quel écrivain malien est célèbre pour la phrase « En Afrique, quand un vieillard meurt, c'est une bibliothèque qui brûle » ?", "Amadou Hampâté Bâ", ["Ahmadou Kourouma", "Yambo Ouologuem", "Modibo Keïta", "Cheikh Anta Diop", "Sembène Ousmane"], "Amadou Hampâté Bâ a défendu la tradition orale.", 4),
    ("Quel savant sénégalais est l'auteur de « Nations nègres et culture » ?", "Cheikh Anta Diop", ["Léopold Senghor", "Joseph Ki-Zerbo", "Théophile Obenga", "Amadou Hampâté Bâ", "Cheikh Hamidou Kane"], "Cheikh Anta Diop a défendu l'origine africaine de l'Égypte ancienne.", 4),
    ("Quelle chanteuse sud-africaine était surnommée « Mama Africa » ?", "Miriam Makeba", ["Cesária Évora", "Angélique Kidjo", "Oum Kalthoum", "Aretha Franklin", "Whitney Houston"], "Miriam Makeba a chanté « Pata Pata ».", 3),
    ("Quel musicien nigérian est le créateur de l'afrobeat ?", "Fela Kuti", ["King Sunny Adé", "Burna Boy", "Wizkid", "Femi Kuti", "Manu Dibango"], "Fela Anikulapo Kuti a inventé l'afrobeat dans les années 1970.", 3),
    ("Quelle chanteuse du Cap-Vert était surnommée « la diva aux pieds nus » ?", "Cesária Évora", ["Miriam Makeba", "Angélique Kidjo", "Oum Kalthoum", "Rokia Traoré", "Dobet Gnahoré"], "Cesária Évora a chanté la morna.", 3),
    ("Quel chanteur sénégalais est l'auteur du tube « 7 Seconds » avec Neneh Cherry ?", "Youssou N'Dour", ["Ismaël Lô", "Baaba Maal", "Salif Keïta", "Alpha Blondy", "Papa Wemba"], "« 7 Seconds » date de 1994.", 4),
    ("Quelle chanteuse béninoise est célèbre pour son album « Fifa » et ses Grammy Awards ?", "Angélique Kidjo", ["Miriam Makeba", "Cesária Évora", "Dobet Gnahoré", "Rokia Traoré", "Oumou Sangaré"], "Angélique Kidjo est née à Ouidah, au Bénin.", 4),
    ("Quel coureur éthiopien a gagné le marathon des Jeux de Rome en 1960 pieds nus ?", "Abebe Bikila", ["Haile Gebrselassie", "Eliud Kipchoge", "Kenenisa Bekele", "Tirunesh Dibaba", "Paul Tergat"], "Abebe Bikila est le premier Africain noir champion olympique.", 3),
    ("Quel marathonien kényan est le premier à avoir couru un marathon en moins de deux heures (2019, non homologué) ?", "Eliud Kipchoge", ["Haile Gebrselassie", "Kenenisa Bekele", "Abebe Bikila", "Paul Tergat", "David Rudisha"], "Eliud Kipchoge a couru 1 h 59 min 40 s à Vienne en 2019.", 3),
    ("Quel attaquant ivoirien a joué à Chelsea et remporté la Ligue des champions en 2012 ?", "Didier Drogba", ["Yaya Touré", "Salomon Kalou", "Samuel Eto'o", "Emmanuel Adebayor", "Wilfried Zaha"], "Didier Drogba a marqué le penalty décisif de la finale 2012.", 2),
    ("Quel Libérien est le seul Africain à avoir reçu le Ballon d'or (1995) ?", "George Weah", ["Samuel Eto'o", "Didier Drogba", "Mohamed Salah", "Sadio Mané", "Roger Milla"], "George Weah a été président du Libéria de 2018 à 2024.", 3),
    ("Quel attaquant égyptien joue à Liverpool et a été plusieurs fois meilleur buteur de Premier League ?", "Mohamed Salah", ["Sadio Mané", "Riyad Mahrez", "Achraf Hakimi", "Victor Osimhen", "Pierre-Emerick Aubameyang"], "Mohamed Salah est capitaine de l'équipe d'Égypte.", 2),
    ("Quel pays a remporté le plus de fois la Coupe d'Afrique des nations de football (jusqu'en 2023) ?", "L'Égypte", ["Le Cameroun", "Le Ghana", "Le Nigeria", "La Côte d'Ivoire", "L'Algérie"], "L'Égypte a gagné 7 fois la CAN.", 2),
    ("Quelle sélection africaine a été la première à atteindre les demi-finales d'une Coupe du monde, en 2022 ?", "Le Maroc", ["Le Sénégal", "Le Ghana", "Le Cameroun", "La Tunisie", "Le Nigeria"], "Le Maroc a terminé quatrième de la Coupe du monde 2022 au Qatar.", 3),
    ("Quelle équipe a remporté la CAN 2021 organisée au Cameroun ?", "Le Sénégal", ["L'Égypte", "L'Algérie", "Le Cameroun", "Le Maroc", "La Côte d'Ivoire"], "Le Sénégal a battu l'Égypte aux tirs au but en finale (2022).", 3),
    ("Quelle équipe a remporté la CAN 2019 organisée en Égypte ?", "L'Algérie", ["Le Sénégal", "Le Nigeria", "L'Égypte", "Le Cameroun", "La Tunisie"], "L'Algérie a battu le Sénégal 1-0 en finale.", 3),
    ("Quelle équipe a remporté la CAN 2017 organisée au Gabon ?", "Le Cameroun", ["L'Égypte", "Le Sénégal", "Le Ghana", "L'Algérie", "Le Burkina Faso"], "Le Cameroun a battu l'Égypte 2-1 en finale.", 2),
    ("Quelle équipe a remporté la CAN 2023 disputée en Côte d'Ivoire ?", "La Côte d'Ivoire", ["Le Nigeria", "Le Sénégal", "L'Égypte", "Le Cameroun", "Le Ghana"], "La Côte d'Ivoire a battu le Nigeria 2-1 en finale (février 2024).", 3),
]
for q, right, wr, expl, d in HIST:
    fq(CM, "af-hist", q, right, wr, expl, "af-hist" if "Quel" in q or "Qui" in q else "af-hist", A, "Afrique : histoire et culture", d)
