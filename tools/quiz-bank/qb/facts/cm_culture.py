"""Cameroun : histoire, institutions, symboles, personnalités, sport, musique, littérature, gastronomie, peuples.
Faits écrits par l'assistant d'après la connaissance générale : tous `review`. Les fiches disent quoi comparer."""
import itertools

from ..facts_engine import SOURCES, fq, pairs, pick, source

CM, G = "general", "CM"
source("cm-hist", "Histoire du Cameroun (repères et dates)", "manuel d'histoire",
       "Comparer les dates avec le manuel d'histoire du Cameroun (programme MINESEC) et une encyclopédie (Britannica, Larousse).")
source("cm-inst", "Institutions, symboles et éducation au Cameroun", "texte officiel",
       "Comparer avec la Constitution du 18 janvier 1996 (modifiée), le Code électoral et les textes de l'éducation (MINEDUB, MINESEC, MINESUP).")
source("cm-people", "Personnalités camerounaises (sport, musique, lettres, politique)", "ouvrage de référence",
       "Comparer avec des biographies publiées (Wikipédia FR, Dictionnaire des auteurs africains) ; les palmarès évoluent : ne garder que des faits datés.")
source("cm-culture", "Culture, gastronomie, peuples et langues du Cameroun", "ouvrage de référence",
       "Comparer avec des références ethnographiques (Atlas linguistique, ouvrages sur les peuples du Cameroun) ; les attributions sont simplifiées.")

# ---------------------------------------------------------------------------------------- histoire : événement -> année
EVENTS = [
    ("l'arrivée des Portugais dans l'estuaire du Wouri (« Rio dos Camarões »)", 1472, 3),
    ("la signature du traité germano-douala entre Nachtigal et les rois Bell et Akwa", 1884, 2),
    ("le départ des Allemands du Cameroun (fin de la colonisation allemande)", 1916, 3),
    ("le partage du Cameroun entre la France et le Royaume-Uni (traité de Versailles)", 1919, 4),
    ("la mise du Cameroun sous mandat de la Société des Nations", 1922, 4),
    ("la mise du Cameroun sous tutelle de l'ONU", 1946, 4),
    ("la création de l'Union des populations du Cameroun (UPC)", 1948, 3),
    ("l'indépendance du Cameroun français", 1960, 1),
    ("le plébiscite qui rattache le Southern Cameroons au Cameroun (fête de la Jeunesse)", 1961, 2),
    ("la création de la République fédérale du Cameroun (réunification)", 1961, 2),
    ("le référendum instaurant l'État unitaire (République unie du Cameroun)", 1972, 2),
    ("l'arrivée de Paul Biya à la présidence de la République", 1982, 2),
    ("le retour au nom de « République du Cameroun »", 1984, 4),
    ("l'adoption de la loi sur le multipartisme", 1990, 4),
    ("la révision de la Constitution instaurant la décentralisation (Constitution du 18 janvier)", 1996, 3),
    ("la première participation du Cameroun à une Coupe du monde de football", 1982, 3),
    ("la qualification des Lions Indomptables en quarts de finale de la Coupe du monde (Italie)", 1990, 2),
    ("la première victoire du Cameroun en Coupe d'Afrique des nations", 1984, 3),
    ("la médaille d'or olympique du Cameroun en football (Sydney)", 2000, 2),
    ("la dernière victoire du Cameroun à la CAN (au Gabon)", 2017, 3),
    ("l'organisation de la CAN au Cameroun (reportée en 2021)", 2022, 3),
    ("le lancement du port en eau profonde de Kribi", 2018, 4),
    ("la mise en service de l'oléoduc Tchad-Cameroun", 2003, 4),
    ("la catastrophe du lac Nyos", 1986, 3),
    ("la fondation de l'Université fédérale du Cameroun (devenue Université de Yaoundé)", 1962, 4),
    ("l'exécution de Rudolf Douala Manga Bell par les Allemands", 1914, 4),
    ("la mort de Ruben Um Nyobè", 1958, 4),
    ("la visite de Nelson Mandela? ", 0, 0),
]
EVENTS = [e for e in EVENTS if e[1] > 0]
pairs(CM, "cm-event-year", [(e, str(y), d) for e, y, d in EVENTS], "En quelle année a eu lieu {a} ?", "Quel événement a eu lieu en {b} ?" if False else None,
      region=G, cat="Histoire du Cameroun", src="cm-hist", expl="{a} : {b}.",
      extra_b=[str(y) for y in (1884, 1900, 1914, 1922, 1948, 1956, 1959, 1960, 1961, 1962, 1964, 1966, 1970, 1972, 1975, 1980, 1982, 1984, 1986, 1990, 1992, 1994, 1996, 2000, 2002, 2004, 2008, 2010, 2014, 2018, 2020, 2022)])
# gaps between events: knowledge + subtraction
ev = [(e, y) for e, y, _ in EVENTS]
for (e1, y1), (e2, y2) in itertools.combinations(ev, 2):
    if y1 == y2 or abs(y1 - y2) > 120 or abs(y1 - y2) < 2:
        continue
    a, b = sorted([(e1, y1), (e2, y2)], key=lambda t: t[1])
    n = b[1] - a[1]
    fq(CM, "cm-event-gap", f"Combien d'années séparent {a[0]} de {b[0]} ?", str(n), [str(x) for x in (n + 1, n - 1, n + 2, n - 2, n + 10, n - 10, n + 5, n - 5) if x > 0 and x != n],
       f"{a[0].capitalize()} : {a[1]} ; {b[0]} : {b[1]} ; écart = {n} ans.", "cm-hist", G, "Histoire du Cameroun", 4)

HIST = [
    ("Quel explorateur allemand a signé des traités avec les chefs douala en 1884 ?", "Gustav Nachtigal", ["Henry Morton Stanley", "David Livingstone", "Pierre Savorgnan de Brazza", "Heinrich Barth", "Mungo Park"], "Nachtigal obtient le protectorat allemand sur la côte camerounaise.", 2),
    ("Quel pays a colonisé le Cameroun de 1884 à 1916 ?", "L'Allemagne", ["La France", "Le Royaume-Uni", "La Belgique", "Le Portugal", "L'Espagne"], "Le Kamerun était un protectorat allemand jusqu'en 1916.", 1),
    ("Quels deux pays ont administré le Cameroun après 1919 ?", "La France et le Royaume-Uni", ["La France et l'Allemagne", "Le Royaume-Uni et la Belgique", "L'Allemagne et le Portugal", "La France et l'Espagne", "La Belgique et l'Italie"], "Le Cameroun a été partagé entre une zone française (la plus grande) et une zone britannique.", 2),
    ("Quel parti nationaliste camerounais a été fondé en 1948 ?", "L'UPC", ["Le RDPC", "Le SDF", "L'UNC", "Le MDR", "L'UDC"], "L'Union des populations du Cameroun milite pour l'indépendance et la réunification.", 2),
    ("Qui fut le premier président de la République du Cameroun ?", "Ahmadou Ahidjo", ["Paul Biya", "Ruben Um Nyobè", "John Ngu Foncha", "André-Marie Mbida", "Félix-Roland Moumié"], "Ahmadou Ahidjo a dirigé le pays de 1960 à 1982.", 1),
    ("Quel dirigeant nationaliste de l'UPC, surnommé « Mpodol », a été tué en 1958 ?", "Ruben Um Nyobè", ["Félix-Roland Moumié", "Ernest Ouandié", "Abel Kingué", "Ahmadou Ahidjo", "Charles Assalé"], "Ruben Um Nyobè est tué dans le maquis près de Boumnyébel le 13 septembre 1958.", 3),
    ("Quel dirigeant de l'UPC est mort empoisonné à Genève en 1960 ?", "Félix-Roland Moumié", ["Ruben Um Nyobè", "Ernest Ouandié", "Abel Kingué", "Ahmadou Ahidjo", "Marcel Bebey Eyidi"], "Félix-Roland Moumié meurt à Genève en novembre 1960.", 4),
    ("Quel est le nom de la fête nationale du Cameroun le 20 mai ?", "La fête de l'Unité nationale", ["La fête de l'Indépendance", "La fête de la Jeunesse", "La fête du Travail", "La fête de la Réunification", "La fête de la Constitution"], "Le 20 mai 1972, un référendum a instauré l'État unitaire.", 1),
    ("Que fête-t-on au Cameroun le 11 février ?", "La Journée de la jeunesse", ["La fête nationale", "La fête du Travail", "La fête de l'Indépendance", "La fête de la Femme", "La fête des Mères"], "La fête de la Jeunesse rappelle le plébiscite du 11 février 1961.", 1),
    ("Quel roi bamoun a inventé l'écriture Shü-mom au début du XXe siècle ?", "Njoya", ["Mbombo", "Ibrahim", "Mbouombouo", "Bell", "Duala Manga"], "Le sultan Ibrahim Njoya (Foumban) crée l'alphabet bamoun vers 1896-1910.", 3),
    ("Qui est Rudolf Douala Manga Bell, mort en 1914 ?", "Un chef douala qui s'opposa à l'expropriation allemande", ["Un général français", "Le premier président du Cameroun", "Un explorateur portugais", "Un missionnaire anglais", "Un sultan de Foumban"], "Il fut pendu par les Allemands pour avoir défendu les terres des Douala.", 4),
    ("Quel traité a divisé l'ancien Kamerun entre la France et le Royaume-Uni en 1919 ?", "Le traité de Versailles", ["Le traité de Berlin", "Le traité de Paris", "Le traité de Vienne", "Le traité de Rome", "Le traité de Yalta"], "L'ancien territoire allemand est réparti entre les vainqueurs de la Première Guerre mondiale.", 4),
    ("Quelle organisation a confié le Cameroun à la France et au Royaume-Uni sous mandat ?", "La Société des Nations", ["L'ONU", "L'Union africaine", "L'OTAN", "L'OUA", "La CEMAC"], "La SDN a placé le Cameroun sous mandat en 1922.", 4),
    ("Quelle est la devise du Cameroun ?", "Paix - Travail - Patrie", ["Unité - Travail - Progrès", "Liberté - Égalité - Fraternité", "Dieu - Patrie - Liberté", "Union - Discipline - Travail", "Fraternité - Justice - Travail"], "Paix, Travail, Patrie est la devise de la République.", 1),
    ("Quelles sont les couleurs du drapeau du Cameroun ?", "Vert, rouge, jaune", ["Rouge, blanc, bleu", "Vert, blanc, rouge", "Bleu, jaune, rouge", "Noir, rouge, or", "Vert, jaune, noir"], "Trois bandes verticales vert-rouge-jaune.", 1),
    ("Quel symbole figure au centre de la bande rouge du drapeau du Cameroun ?", "Une étoile jaune", ["Un lion", "Un soleil", "Une croix", "Une balance", "Un croissant"], "Une étoile d'or sur la bande centrale rouge symbolise l'unité.", 1),
    ("Quelle est la couleur de la bande de gauche du drapeau du Cameroun ?", "Vert", ["Rouge", "Jaune", "Bleu", "Blanc", "Noir"], "De gauche à droite : vert, rouge, jaune.", 1),
    ("Quel est le titre de l'hymne national du Cameroun ?", "Ô Cameroun, berceau de nos ancêtres", ["La Marseillaise", "Fraternité", "Le Chant du départ", "L'Africaine", "Hymne à la liberté"], "L'hymne a été adopté en 1957 ; il existe en français et en anglais.", 2),
    ("Quelle langue officielle du Cameroun est parlée dans les régions du Nord-Ouest et du Sud-Ouest ?", "L'anglais", ["Le français", "Le portugais", "L'espagnol", "L'arabe", "Le haoussa"], "L'anglais et le français sont les deux langues officielles.", 1),
    ("Combien de langues officielles le Cameroun a-t-il ?", "2", ["1", "3", "4", "5", "10"], "Le français et l'anglais.", 1),
    ("Quelle est la durée du mandat du président de la République du Cameroun ?", "7 ans", ["4 ans", "5 ans", "6 ans", "10 ans", "3 ans"], "Le mandat présidentiel est de sept ans.", 3),
    ("Combien de députés siègent à l'Assemblée nationale du Cameroun ?", "180", ["100", "150", "200", "120", "250"], "L'Assemblée nationale compte 180 députés.", 4),
    ("Combien de sénateurs compte le Sénat du Cameroun ?", "100", ["70", "50", "30", "180", "120"], "Le Sénat compte 100 sénateurs : 70 élus et 30 nommés.", 4),
    ("Comment s'appelle la chambre basse du Parlement camerounais ?", "L'Assemblée nationale", ["Le Sénat", "Le Conseil constitutionnel", "La Cour suprême", "Le Conseil économique et social", "La Chambre des chefs"], "Le Parlement est composé de l'Assemblée nationale et du Sénat.", 2),
    ("Qui dirige une région au Cameroun ?", "Un gouverneur", ["Un maire", "Un préfet", "Un sous-préfet", "Un lamido", "Un sénateur"], "Les régions sont dirigées par des gouverneurs ; les départements par des préfets.", 3),
    ("Qui dirige un département au Cameroun ?", "Un préfet", ["Un gouverneur", "Un maire", "Un sous-préfet", "Un fon", "Un ministre"], "Un préfet administre le département ; le sous-préfet, l'arrondissement.", 3),
    ("Quel est le titre du chef traditionnel dans les royaumes du Nord (Foulbé) ?", "Lamido", ["Fon", "Sultan", "Mfumu", "Nana", "Chef de canton"], "Un lamido dirige un lamidat (Ngaoundéré, Rey-Bouba...).", 3),
    ("Quel est le titre du chef traditionnel dans les chefferies du Nord-Ouest et de l'Ouest ?", "Fon", ["Lamido", "Sultan", "Nana", "Mfumu", "Mbombo"], "Les chefs des royaumes de la grassland s'appellent « fon ».", 3),
    ("Quel est le nom du palais présidentiel du Cameroun ?", "Le palais de l'Unité", ["Le palais de la Renaissance", "Le palais des Congrès", "Le palais de la Nation", "Le palais de l'Indépendance", "Le palais de la République"], "Le palais de l'Unité est situé à Yaoundé (Etoudi).", 3),
    ("Quel examen sanctionne la fin du cycle primaire francophone au Cameroun ?", "Le CEPE", ["Le BEPC", "Le Probatoire", "Le Baccalauréat", "Le GCE O Level", "Le FSLC"], "Certificat d'études primaires et élémentaires.", 2),
    ("Quel examen sanctionne la fin du cycle primaire anglophone au Cameroun ?", "Le FSLC", ["Le CEPE", "Le BEPC", "Le GCE A Level", "Le Probatoire", "Le Baccalauréat"], "First School Leaving Certificate.", 3),
    ("Quel examen se passe à la fin de la classe de 3e dans le système francophone ?", "Le BEPC", ["Le CEPE", "Le Probatoire", "Le Baccalauréat", "Le GCE A Level", "Le Concours ENAM"], "Brevet d'études du premier cycle.", 2),
    ("Quel examen précède le baccalauréat dans le système francophone (en classe de 1re) ?", "Le Probatoire", ["Le BEPC", "Le CEPE", "Le GCE O Level", "Le FSLC", "Le Concours ENS"], "Le probatoire est passé en classe de première.", 3),
    ("Que signifie « GCE » dans le système éducatif anglophone du Cameroun ?", "General Certificate of Education", ["Grand Certificat d'Éducation", "General Council of Education", "Government Certificate of Exams", "Graduate Certificate of Engineering", "General Course of English"], "Les GCE Ordinary et Advanced Level sont les examens du secondaire anglophone.", 3),
    ("Quelle école forme les administrateurs et les magistrats du Cameroun ?", "L'ENAM", ["L'ENS", "L'ESSEC", "L'IRIC", "Polytechnique", "L'ISSEA"], "L'École nationale d'administration et de magistrature, à Yaoundé.", 4),
]
for q, right, wr, expl, d in HIST:
    fq(CM, "cm-hist", q, right, wr, expl, "cm-hist" if "Qui" in q or "Quel" in q else "cm-inst", G, "Histoire et institutions", d)

# ---------------------------------------------------------------------------------------- personnalités
FOOT = [
    ("Roger Milla", "l'attaquant devenu en 1994 le plus vieux buteur de l'histoire de la Coupe du monde"),
    ("Samuel Eto'o", "le joueur quatre fois élu Ballon d'or africain, vainqueur de la Ligue des champions avec le FC Barcelone et l'Inter Milan"),
    ("Thomas N'Kono", "le gardien légendaire des Coupes du monde 1982 et 1990"),
    ("Rigobert Song", "le défenseur exclu lors de deux Coupes du monde différentes (1994 et 1998)"),
    ("Vincent Aboubakar", "le buteur meilleur marqueur de la CAN 2021 organisée au Cameroun"),
    ("François Omam-Biyik", "l'auteur du but de la victoire contre l'Argentine à l'ouverture de la Coupe du monde 1990"),
    ("Marc-Vivien Foé", "le milieu de terrain mort sur le terrain pendant la Coupe des confédérations 2003"),
    ("Patrick Mboma", "l'attaquant champion olympique en 2000 et vainqueur de la CAN 2000 et 2002"),
    ("André Onana", "le gardien passé par l'Ajax Amsterdam, l'Inter Milan puis Manchester United"),
    ("Geremi", "le milieu passé par le Real Madrid et Chelsea, champion d'Afrique en 2000 et 2002"),
    ("Pierre Webó", "l'attaquant formé à l'Espanyol? ".strip()),
]
FOOT = [f for f in FOOT if "Espanyol" not in f[1]]
pairs(CM, "cm-foot", FOOT, "Quel footballeur camerounais est {b} ?".replace("{b}", "{b}"), "{a} est…",
      region=G, cat="Sport", src="cm-people", diff=3, expl="{a} est {b}.", extra_a=["Alex Song", "Eric Maxim Choupo-Moting", "Benoît Assou-Ekotto", "Stéphane Mbia", "Nicolas Nkoulou", "Samuel Umtiti", "Karl Toko Ekambi", "Joël Matip"]) if False else None
for name, clue in FOOT:
    others = [n for n, _ in FOOT if n != name] + ["Alex Song", "Eric Maxim Choupo-Moting", "Benoît Assou-Ekotto", "Stéphane Mbia", "Nicolas Nkoulou", "Joël Matip", "Karl Toko Ekambi"]
    fq(CM, "cm-foot-who", f"Quel footballeur camerounais est {clue} ?", name, pick("foot" + name, others, name, 9), f"{name} est {clue}.", "cm-people", G, "Sport", 3)
SPORT = [
    ("Comment appelle-t-on l'équipe nationale masculine de football du Cameroun ?", "Les Lions Indomptables", ["Les Éléphants", "Les Super Eagles", "Les Panthères", "Les Aigles de Carthage", "Les Black Stars"], "Les Lions Indomptables sont l'équipe nationale masculine.", 1),
    ("Comment appelle-t-on l'équipe nationale féminine de football du Cameroun ?", "Les Lionnes Indomptables", ["Les Lionnes de la Teranga", "Les Super Falcons", "Les Éléphantes", "Les Panthères", "Les Black Queens"], "Les Lionnes Indomptables sont l'équipe féminine.", 2),
    ("Combien de fois le Cameroun a-t-il remporté la Coupe d'Afrique des nations de football (jusqu'en 2022) ?", "5", ["3", "4", "6", "7", "2"], "1984, 1988, 2000, 2002 et 2017.", 3),
    ("Contre quelle équipe le Cameroun a-t-il gagné le match d'ouverture de la Coupe du monde 1990 ?", "L'Argentine", ["Le Brésil", "L'Italie", "L'Angleterre", "La Roumanie", "L'URSS"], "Victoire 1-0 sur l'Argentine de Maradona, tenante du titre.", 2),
    ("Contre quelle équipe le Cameroun a-t-il perdu en quart de finale de la Coupe du monde 1990 ?", "L'Angleterre", ["L'Argentine", "L'Italie", "Le Brésil", "L'Allemagne", "L'Espagne"], "Défaite 3-2 après prolongation contre l'Angleterre.", 3),
    ("Quel exploit a réalisé le Cameroun en 2022 contre le Brésil à la Coupe du monde ?", "Une victoire 1-0", ["Un match nul 2-2", "Une défaite 3-0", "Une victoire 3-2", "Un match nul 0-0", "Une défaite 1-0"], "Vincent Aboubakar a marqué l'unique but ; le Cameroun est la première équipe africaine à battre le Brésil en Coupe du monde.", 4),
    ("Dans quelle ville le Cameroun a-t-il remporté la médaille d'or olympique de football en 2000 ?", "Sydney", ["Athènes", "Atlanta", "Pékin", "Barcelone", "Séoul"], "Les Lions Indomptables ont battu l'Espagne aux tirs au but à Sydney.", 3),
    ("Quel club de football camerounais est basé à Garoua et a remporté de nombreux titres de champion ?", "Coton Sport", ["Canon Yaoundé", "Union Douala", "Tonnerre Yaoundé", "Les Astres", "Fovu Baham"], "Coton Sport de Garoua est l'un des clubs les plus titrés du pays.", 3),
    ("Quel club de Yaoundé a remporté trois fois la Ligue des champions africaine ?", "Canon Yaoundé", ["Union Douala", "Coton Sport", "Tonnerre Yaoundé", "Les Astres", "Dynamo Douala"], "Canon Yaoundé a été champion d'Afrique en 1971, 1978 et 1980.", 4),
    ("Quelle athlète camerounaise a remporté l'or olympique au triple saut en 2004 et 2008 ?", "Françoise Mbango Etone", ["Gisèle Mbanga", "Marie-José Pérec", "Maryse Éwanjé-Épée", "Blessing Okagbare", "Sarah Menezes"], "Françoise Mbango a réussi le doublé aux Jeux d'Athènes puis de Pékin.", 4),
    ("Quel combattant camerounais est devenu champion du monde poids lourds de l'UFC en 2021 ?", "Francis Ngannou", ["Israel Adesanya", "Kamaru Usman", "Jon Jones", "Tyson Fury", "Anthony Joshua"], "Francis Ngannou, né à Batié, a battu Stipe Miocic en 2021.", 3),
    ("Comment s'appelle le principal championnat de football professionnel du Cameroun ?", "Elite One", ["Ligue 1", "Premier League", "Super League", "Botola Pro", "Linafoot"], "Le championnat s'appelle Elite One (anciennement Division 1).", 3),
]
for q, right, wr, expl, d in SPORT:
    fq(CM, "cm-sport", q, right, wr, expl, "cm-people", G, "Sport", d)

# musiciens / écrivains
MUSIC = [
    ("Manu Dibango", "le saxophoniste auteur du titre « Soul Makossa » (1972)"),
    ("Richard Bona", "le bassiste et chanteur de jazz né à Minta, dans l'Est"),
    ("Francis Bebey", "le musicien et écrivain auteur du roman « Le Fils d'Agatha Moudio »"),
    ("Charlotte Dipanda", "la chanteuse de variété et de makossa révélée au début des années 2010"),
    ("Coco Mbassi", "la chanteuse de gospel et de world music née à Douala"),
    ("Sam Fan Thomas", "le chanteur du tube « Makassi » (1984)"),
    ("Eboa Lotin", "le chanteur de makossa surnommé « le Rossignol » ? ".strip()),
    ("Les Têtes Brûlées", "le groupe de bikutsi célèbre pour son style rock et ses pantalons déchirés"),
    ("Stanley Enow", "le rappeur connu pour le titre « Hein Père »"),
    ("Locko", "le chanteur d'afro-soul révélé avec « Je t'aime » ? ".strip()),
]
MUSIC = [m for m in MUSIC if "?" not in m[1]]
for name, clue in MUSIC:
    others = [n for n, _ in MUSIC if n != name] + ["Salif Keita", "Youssou N'Dour", "Fela Kuti", "Papa Wemba", "Angélique Kidjo", "Koffi Olomidé", "Alpha Blondy", "Ismaël Lô"]
    fq(CM, "cm-music-who", f"Quel artiste est {clue} ?", name, pick("mus" + name, others, name, 9), f"{name} est {clue}.", "cm-people", G, "Musique", 3)
STYLES = [("le makossa", "Douala"), ("le bikutsi", "Yaoundé"), ("l'assiko", "Douala"), ("l'ambass bey", "Douala")]
fq(CM, "cm-music", "Quel style musical camerounais vient de la région de Douala et du Littoral ?", "Le makossa", ["Le bikutsi", "Le coupé-décalé", "Le mbalax", "Le highlife", "Le soukous"], "Le makossa est né à Douala chez les Sawa.", "cm-people", G, "Musique", 2)
fq(CM, "cm-music", "Quel style musical camerounais vient du Centre et du Sud, chez les Beti ?", "Le bikutsi", ["Le makossa", "Le coupé-décalé", "Le mbalax", "Le highlife", "Le soukous"], "Le bikutsi, rythme beti, se danse à Yaoundé et dans la région du Centre.", "cm-people", G, "Musique", 2)
fq(CM, "cm-music", "Quel instrument est le symbole du makossa de Manu Dibango ?", "Le saxophone", ["La kora", "Le balafon", "Le djembé", "Le violon", "La guitare électrique"], "Manu Dibango est saxophoniste.", "cm-people", G, "Musique", 2)
BOOKS = [("Une vie de boy", "Ferdinand Oyono"), ("Le Vieux Nègre et la médaille", "Ferdinand Oyono"), ("Ville cruelle", "Mongo Beti"), ("Le Pauvre Christ de Bomba", "Mongo Beti"),
         ("Mission terminée", "Mongo Beti"), ("Le Fils d'Agatha Moudio", "Francis Bebey"), ("Temps de chien", "Patrice Nganang"), ("Contours du jour qui vient", "Léonora Miano"),
         ("C'est le soleil qui m'a brûlée", "Calixthe Beyala"), ("Les honneurs perdus", "Calixthe Beyala"), ("Les Chauves-Souris", "Bernard Nanga")]
pairs(CM, "cm-book", BOOKS, "Qui est l'auteur de « {a} » ?", None, region=G, cat="Littérature", src="cm-people", diff=3, expl="« {a} » est de {b}.",
      extra_b=["Camara Laye", "Ahmadou Kourouma", "Chinua Achebe", "Wole Soyinka", "Sembène Ousmane", "Cheikh Hamidou Kane", "Aimé Césaire", "Léopold Sédar Senghor"])
fq(CM, "cm-lit", "Quel est le vrai nom de l'écrivain Mongo Beti ?", "Alexandre Biyidi Awala", ["Ferdinand Oyono", "Bernard Nanga", "Francis Bebey", "René Philombe", "Patrice Nganang"], "Mongo Beti et Eza Boto sont les pseudonymes d'Alexandre Biyidi Awala.", "cm-people", G, "Littérature", 4)

# ---------------------------------------------------------------------------------------- gastronomie
FOOD = [
    ("le ndolé", "un plat de feuilles amères mijotées avec des arachides et de la viande ou des crevettes"),
    ("le poulet DG", "un plat de poulet sauté avec des plantains mûrs frits et des légumes"),
    ("le koki", "un gâteau de haricots niébé cuit à la vapeur dans des feuilles"),
    ("l'achu", "un plat de taro pilé servi avec une sauce jaune, typique du Nord-Ouest"),
    ("le mbongo tchobi", "une sauce noire aux épices et au poisson ou à la viande, typique des Bassa"),
    ("le bobolo", "un bâton de manioc fermenté cuit dans des feuilles"),
    ("le soya", "des brochettes de viande épicée grillées, vendues dans la rue"),
    ("l'okok (ou eru)", "un plat de feuilles de gnetum finement coupées, cuites avec de l'huile de palme"),
    ("le sanga", "un plat de maïs frais et de feuilles de manioc"),
    ("le folong", "un plat de légumes verts feuillus, spécialité de l'Ouest"),
    ("le kondré", "un ragoût de plantains verts et de viande épicée"),
]
for name, clue in FOOD:
    others = [n for n, _ in FOOD if n != name] + ["la sauce graine", "l'attiéké", "le tiep", "le thieboudienne", "le fufu", "le mafé"]
    fq(CM, "cm-food", f"Quel plat camerounais est {clue} ?", name[0].upper() + name[1:], pick("food" + name, [o[0].upper() + o[1:] for o in others], name[0].upper() + name[1:], 9), f"{name.capitalize()} est {clue}.", "cm-culture", G, "Gastronomie", 2)
DRINKS = [
    ("Comment appelle-t-on le vin de palme au Cameroun ?", "Matango", ["Bili-bili", "Folléré", "Dolo", "Tej", "Mbuh"], "Le vin de palme, boisson traditionnelle du Sud et de l'Ouest.", 3),
    ("Quelle boisson à base de sorgho fermenté est consommée dans le Nord du Cameroun ?", "La bili-bili", ["Le matango", "Le folléré", "Le bissap", "Le thé vert", "Le gingembre"], "La bili-bili est une bière de sorgho.", 3),
    ("Quelle boisson rouge à base d'hibiscus est très populaire au Cameroun ?", "Le folléré", ["Le matango", "La bili-bili", "Le thé", "Le café", "Le lait caillé"], "Le folléré (ou bissap) est une infusion d'hibiscus.", 3),
    ("Dans quelle région produit-on surtout le café arabica au Cameroun ?", "L'Ouest", ["Le Sud", "L'Extrême-Nord", "Le Littoral", "L'Est", "L'Adamaoua"], "Les hautes terres de l'Ouest (Bamiléké) produisent l'arabica.", 3),
    ("Quel fruit à pulpe verdâtre, cuit à l'eau, accompagne le maïs ou le plantain au Cameroun ?", "La safou", ["La goyave", "Le litchi", "La pomme cannelle", "Le fruit du dragon", "La figue"], "La safou (prune africaine) est consommée cuite.", 3),
]
for q, right, wr, expl, d in DRINKS:
    fq(CM, "cm-food-misc", q, right, wr, expl, "cm-culture", G, "Gastronomie", d)

# ---------------------------------------------------------------------------------------- peuples et langues
PEOPLES = [("les Bamiléké", "l'Ouest"), ("les Bamoun", "l'Ouest"), ("les Béti (Ewondo)", "le Centre"), ("les Bassa", "le Littoral et le Centre"), ("les Douala (Sawa)", "le Littoral"),
           ("les Bakweri", "le Sud-Ouest"), ("les Peuls (Foulbé)", "le Nord et l'Adamaoua"), ("les Toupouri", "l'Extrême-Nord"), ("les Mafa", "l'Extrême-Nord"), ("les Mousgoum", "l'Extrême-Nord"),
           ("les Kotoko", "l'Extrême-Nord"), ("les Gbaya", "l'Est"), ("les Baka", "l'Est"), ("les Bulu", "le Sud"), ("les Fang", "le Sud et le Centre"), ("les Oroko", "le Sud-Ouest"),
           ("les Nso", "le Nord-Ouest"), ("les Kom", "le Nord-Ouest"), ("les Bafut", "le Nord-Ouest"), ("les Tikar", "le Nord-Ouest et l'Adamaoua"), ("les Massa", "l'Extrême-Nord"),
           ("les Mbororo", "le Nord-Ouest et l'Adamaoua")]
pairs(CM, "cm-people-region", PEOPLES, "Dans quelle partie du Cameroun vivent principalement {a} ?", None, region=G, cat="Peuples du Cameroun", src="cm-culture", diff=3, expl="{a} vivent principalement dans {b}.",
      extra_b=["l'Ouest", "le Centre", "le Littoral", "le Sud-Ouest", "le Nord", "l'Extrême-Nord", "l'Est", "le Sud", "le Nord-Ouest", "l'Adamaoua"])
LANGS = [
    ("Quelle langue de communication, mélange d'anglais et de langues locales, est très répandue au Cameroun ?", "Le pidgin-english (Kamtok)", ["Le lingala", "Le swahili", "Le haoussa", "Le wolof", "Le zoulou"], "Le pidgin est parlé surtout à l'Ouest, au Nord-Ouest et au Sud-Ouest, ainsi qu'à Douala.", 2),
    ("Comment appelle-t-on le mélange de français, d'anglais et de langues locales parlé par les jeunes citadins ?", "Le camfranglais", ["Le pidgin", "Le franglais d'Afrique du Sud", "Le lingala", "Le verlan", "Le nouchi"], "Le camfranglais est surtout parlé à Douala et Yaoundé.", 3),
    ("Quelle langue est la langue véhiculaire du Nord du Cameroun ?", "Le fulfulde", ["L'ewondo", "Le douala", "Le bassa", "Le bamoun", "Le pidgin"], "Le fulfulde (langue des Peuls) sert de langue commune dans le Nord.", 3),
    ("Quelle langue est parlée par les Douala ?", "Le duala", ["L'ewondo", "Le bassa", "Le bamoun", "Le fulfulde", "Le ghomala"], "Le duala est la langue des Sawa du Littoral.", 3),
    ("Quelle langue est parlée par les Béti de Yaoundé ?", "L'ewondo", ["Le duala", "Le bassa", "Le bamoun", "Le fulfulde", "Le ghomala"], "L'ewondo est la langue des Ewondo, autour de Yaoundé.", 3),
    ("Approximativement combien de langues locales sont parlées au Cameroun ?", "Environ 250", ["Environ 20", "Environ 50", "Environ 100", "Environ 500", "Environ 1 000"], "Le Cameroun compte plus de 250 langues nationales.", 3),
]
for q, right, wr, expl, d in LANGS:
    fq(CM, "cm-lang", q, right, wr, expl, "cm-culture", G, "Langues du Cameroun", d)
CULT = [
    ("Quelle est la danse traditionnelle de la région du Littoral et de Douala, dansée aux fêtes ?", "L'ambass bey", ["Le bikutsi", "Le coupé-décalé", "Le ndombolo", "Le mapouka", "Le zouk"], "L'ambass bey est une danse du Littoral.", 4),
    ("Quelle fête traditionnelle des Sawa a lieu à Douala sur le Wouri avec des pirogues ?", "Le Ngondo", ["Le Nguon", "Le Lela", "Le Mbock", "Le Ngomo", "Le Medumba"], "Le Ngondo est l'assemblée traditionnelle du peuple Sawa.", 4),
    ("Quelle fête traditionnelle bamoun a lieu à Foumban ?", "Le Nguon", ["Le Ngondo", "Le Lela", "Le Mbock", "Le Ngomo", "Le Medumba"], "Le Nguon est le festival culturel du royaume bamoun.", 4),
    ("Quelle est la capitale du royaume bamoun ?", "Foumban", ["Bafoussam", "Dschang", "Bandjoun", "Bamenda", "Banyo"], "Foumban abrite le palais du sultan.", 2),
    ("Quelle chefferie de l'Ouest est célèbre pour son architecture en bois sculpté ?", "Bandjoun", ["Foumban", "Bafoussam", "Dschang", "Bangangté", "Mbouda"], "La chefferie de Bandjoun est réputée pour sa grande case.", 4),
    ("Quel monument de Yaoundé rappelle la réunification du pays ?", "Le monument de la Réunification", ["La statue de la Liberté", "L'arc de triomphe", "La tour Eiffel", "Le monument de l'Indépendance", "Le monument aux morts"], "Il se trouve dans le centre de Yaoundé.", 3),
    ("Comment appelle-t-on les mototaxis dans les villes du Cameroun ?", "Les bendskins", ["Les tuk-tuk", "Les boda-boda", "Les zémidjans", "Les okada", "Les matatus"], "Les « bendskins » sont les mototaxis de Douala, Yaoundé et du Nord.", 2),
    ("Quel est le nom des taxis-brousse ou minibus qui relient les villes camerounaises ?", "Les cars de transport interurbain (bus d'agences)", ["Les tuk-tuk", "Les dalal jàmm", "Les zémidjans", "Les matatus", "Les gbaka"], "Les agences de voyage assurent les liaisons interurbaines.", 4),
]
for q, right, wr, expl, d in CULT:
    fq(CM, "cm-culture", q, right, wr, expl, "cm-culture", G, "Culture camerounaise", d)
