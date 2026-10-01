"""Monde : capitales, monnaies, géographie, sciences, histoire, arts, sports. Faits écrits par l'assistant d'après la
connaissance générale : tous `review`."""
from ..facts_engine import fq, pairs, pick, source

CM, W = "general", "WORLD"
source("w-pays", "Pays du monde : capitales et continents", "atlas", "Comparer avec un atlas mondial et la liste des États membres de l'ONU ; les capitales contestées ne sont pas utilisées.")
source("w-sci", "Sciences et nature (culture générale)", "manuel de sciences", "Comparer avec un manuel de sciences (programmes de collège) et une encyclopédie scientifique.")
source("w-hist", "Histoire, arts et découvertes du monde", "manuel d'histoire / encyclopédie", "Comparer avec un manuel d'histoire et l'Encyclopædia Britannica ; les dates des événements sont à vérifier une à une.")
source("w-sport", "Sport mondial : palmarès", "palmarès officiels", "Comparer avec les sites de la FIFA, du CIO et des fédérations ; les palmarès évoluent chaque année.")

CAPS = [
    # Europe
    ("la France", "Paris", "Europe"), ("l'Allemagne", "Berlin", "Europe"), ("l'Italie", "Rome", "Europe"), ("l'Espagne", "Madrid", "Europe"), ("le Portugal", "Lisbonne", "Europe"),
    ("le Royaume-Uni", "Londres", "Europe"), ("l'Irlande", "Dublin", "Europe"), ("la Belgique", "Bruxelles", "Europe"), ("les Pays-Bas", "Amsterdam", "Europe"), ("le Luxembourg", "Luxembourg", "Europe"),
    ("la Suisse", "Berne", "Europe"), ("l'Autriche", "Vienne", "Europe"), ("la Pologne", "Varsovie", "Europe"), ("la Tchéquie", "Prague", "Europe"), ("la Hongrie", "Budapest", "Europe"),
    ("la Roumanie", "Bucarest", "Europe"), ("la Bulgarie", "Sofia", "Europe"), ("la Grèce", "Athènes", "Europe"), ("la Suède", "Stockholm", "Europe"), ("la Norvège", "Oslo", "Europe"),
    ("le Danemark", "Copenhague", "Europe"), ("la Finlande", "Helsinki", "Europe"), ("l'Islande", "Reykjavik", "Europe"), ("la Russie", "Moscou", "Europe"), ("la Biélorussie", "Minsk", "Europe"),
    ("la Lituanie", "Vilnius", "Europe"), ("la Lettonie", "Riga", "Europe"), ("l'Estonie", "Tallinn", "Europe"), ("la Serbie", "Belgrade", "Europe"), ("la Croatie", "Zagreb", "Europe"),
    ("la Slovénie", "Ljubljana", "Europe"), ("l'Albanie", "Tirana", "Europe"), ("Malte", "La Valette", "Europe"), ("la Slovaquie", "Bratislava", "Europe"),
    # Asie
    ("la Chine", "Pékin", "Asie"), ("le Japon", "Tokyo", "Asie"), ("la Corée du Sud", "Séoul", "Asie"), ("la Corée du Nord", "Pyongyang", "Asie"), ("l'Inde", "New Delhi", "Asie"), ("le Pakistan", "Islamabad", "Asie"),
    ("le Bangladesh", "Dacca", "Asie"), ("le Népal", "Katmandou", "Asie"), ("la Thaïlande", "Bangkok", "Asie"), ("le Vietnam", "Hanoï", "Asie"), ("le Cambodge", "Phnom Penh", "Asie"), ("le Laos", "Vientiane", "Asie"),
    ("la Malaisie", "Kuala Lumpur", "Asie"), ("les Philippines", "Manille", "Asie"), ("la Mongolie", "Oulan-Bator", "Asie"), ("le Kazakhstan", "Astana", "Asie"), ("l'Ouzbékistan", "Tachkent", "Asie"),
    ("l'Afghanistan", "Kaboul", "Asie"), ("l'Iran", "Téhéran", "Asie"), ("l'Irak", "Bagdad", "Asie"), ("la Syrie", "Damas", "Asie"), ("le Liban", "Beyrouth", "Asie"), ("la Jordanie", "Amman", "Asie"),
    ("l'Arabie saoudite", "Riyad", "Asie"), ("Oman", "Mascate", "Asie"), ("les Émirats arabes unis", "Abou Dabi", "Asie"), ("le Qatar", "Doha", "Asie"), ("le Koweït", "Koweït", "Asie"),
    ("l'Arménie", "Erevan", "Asie"), ("l'Azerbaïdjan", "Bakou", "Asie"), ("la Géorgie", "Tbilissi", "Asie"), ("la Turquie", "Ankara", "Asie"), ("le Bhoutan", "Thimphou", "Asie"), ("les Maldives", "Malé", "Asie"),
    # Amériques
    ("les États-Unis", "Washington", "Amérique"), ("le Canada", "Ottawa", "Amérique"), ("le Mexique", "Mexico", "Amérique"), ("Cuba", "La Havane", "Amérique"), ("le Brésil", "Brasilia", "Amérique"),
    ("l'Argentine", "Buenos Aires", "Amérique"), ("le Chili", "Santiago", "Amérique"), ("le Pérou", "Lima", "Amérique"), ("la Colombie", "Bogota", "Amérique"), ("le Venezuela", "Caracas", "Amérique"),
    ("l'Équateur", "Quito", "Amérique"), ("le Paraguay", "Asunción", "Amérique"), ("l'Uruguay", "Montevideo", "Amérique"), ("Haïti", "Port-au-Prince", "Amérique"), ("la Jamaïque", "Kingston", "Amérique"),
    ("le Panama", "Panama", "Amérique"), ("le Costa Rica", "San José", "Amérique"), ("le Nicaragua", "Managua", "Amérique"), ("le Honduras", "Tégucigalpa", "Amérique"), ("le Guatemala", "Guatemala", "Amérique"),
    # Océanie
    ("l'Australie", "Canberra", "Océanie"), ("la Nouvelle-Zélande", "Wellington", "Océanie"), ("les Fidji", "Suva", "Océanie"), ("Samoa", "Apia", "Océanie"),
]
NOT_CAPS = ["Sydney", "Istanbul", "Rio de Janeiro", "Zurich", "Genève", "Milan", "Barcelone", "Shanghai", "Mumbai", "New York", "Toronto", "Melbourne", "Auckland", "Lagos", "Casablanca", "Karachi", "Osaka", "Munich", "Saint-Pétersbourg"]


def de(n):
    if n.startswith("le "):
        return "du " + n[3:]
    if n.startswith("la "):
        return "de la " + n[3:]
    if n.startswith("les "):
        return "des " + n[4:]
    return "de " + n


ALL_CAPS = [c for _, c, _ in CAPS]
for n, c, cont in CAPS:
    fq(CM, "w-capital-fwd", f"Quelle est la capitale {de(n)} ?", c, pick("wcap" + n, ALL_CAPS + NOT_CAPS, c, 9), f"La capitale {de(n)} est {c}.", "w-pays", W, "Monde : pays et capitales",
       1 if c in ("Paris", "Londres", "Rome", "Berlin", "Madrid", "Washington", "Pékin", "Tokyo", "Moscou", "Brasilia", "Ottawa") else 3)
    if ALL_CAPS.count(c) == 1 and c not in ("Luxembourg", "Panama", "Guatemala", "Koweït"):
        fq(CM, "w-capital-rev", f"{c} est la capitale de quel pays ?", n[0].upper() + n[1:], pick("wcapr" + n, [x[0][0].upper() + x[0][1:] for x in CAPS], n[0].upper() + n[1:], 9), f"{c} est la capitale {de(n)}.", "w-pays", W, "Monde : pays et capitales", 3)
    if cont:
        fq(CM, "w-continent", f"Sur quel continent se trouve {n} ?", cont if cont != "Amérique" else "L'Amérique", [x for x in ("Europe", "Asie", "L'Amérique", "Océanie", "Afrique") if x != (cont if cont != "Amérique" else "L'Amérique")],
           f"{n[0].upper() + n[1:]} est en {cont}.", "w-pays", W, "Monde : pays et capitales", 2)

CURR = [("le Japon", "Le yen"), ("la Chine", "Le yuan (renminbi)"), ("l'Inde", "La roupie"), ("le Royaume-Uni", "La livre sterling"), ("la Suisse", "Le franc suisse"), ("la Russie", "Le rouble"),
        ("le Brésil", "Le réal"), ("le Mexique", "Le peso mexicain"), ("la Corée du Sud", "Le won"), ("la Turquie", "La livre turque"), ("la Pologne", "Le zloty"), ("la Suède", "La couronne suédoise"),
        ("la Thaïlande", "Le baht"), ("l'Afrique du Sud", "Le rand"), ("l'Arabie saoudite", "Le riyal"), ("le Vietnam", "Le dong"), ("les Émirats arabes unis", "Le dirham des Émirats"), ("l'Argentine", "Le peso argentin")]
pairs(CM, "w-currency", [(n, c) for n, c in CURR], "Quelle est la monnaie {a} ?".replace("{a}", "{a}"), None, region=W, cat="Monde : économie", src="w-pays", diff=3,
      expl="La monnaie {a} est {b}.", extra_b=["L'euro", "Le dollar américain", "Le dollar canadien", "La couronne norvégienne", "Le dinar", "Le shekel"]) if False else None
for n, cur in CURR:
    fq(CM, "w-currency", f"Quelle est la monnaie {de(n)} ?", cur, pick("wcur" + n, [x[1] for x in CURR] + ["L'euro", "Le dollar américain", "Le dollar canadien", "Le shekel"], cur, 9), f"La monnaie {de(n)} est {cur.lower()}.", "w-pays", W, "Monde : économie", 3)

SCI = [
    ("Combien de planètes compte le système solaire ?", "8", ["7", "9", "10", "6", "12"], "Mercure, Vénus, Terre, Mars, Jupiter, Saturne, Uranus, Neptune.", 1),
    ("Quelle est la planète la plus proche du Soleil ?", "Mercure", ["Vénus", "La Terre", "Mars", "Jupiter", "Saturne"], "Mercure est la première planète à partir du Soleil.", 1),
    ("Quelle est la plus grande planète du système solaire ?", "Jupiter", ["Saturne", "Neptune", "Uranus", "La Terre", "Mars"], "Jupiter est une planète géante gazeuse.", 1),
    ("Quelle planète est surnommée la « planète rouge » ?", "Mars", ["Vénus", "Jupiter", "Mercure", "Saturne", "Neptune"], "Mars doit sa couleur à l'oxyde de fer de son sol.", 1),
    ("Quelle planète est célèbre pour ses anneaux ?", "Saturne", ["Mars", "Mercure", "Vénus", "La Terre", "Pluton"], "Saturne a le système d'anneaux le plus visible.", 1),
    ("Quelle est la planète la plus chaude du système solaire ?", "Vénus", ["Mercure", "Mars", "Jupiter", "La Terre", "Neptune"], "L'effet de serre fait de Vénus la planète la plus chaude (environ 460 °C).", 3),
    ("Quelle planète est la troisième à partir du Soleil ?", "La Terre", ["Mars", "Vénus", "Mercure", "Jupiter", "Saturne"], "Mercure, Vénus, Terre.", 1),
    ("Quel est le seul satellite naturel de la Terre ?", "La Lune", ["Phobos", "Titan", "Europe", "Io", "Triton"], "La Lune tourne autour de la Terre en environ 27 jours.", 1),
    ("Quel astronaute américain a été le premier homme à marcher sur la Lune en 1969 ?", "Neil Armstrong", ["Buzz Aldrin", "Youri Gagarine", "John Glenn", "Alan Shepard", "Michael Collins"], "La mission Apollo 11 s'est posée le 20 juillet 1969.", 1),
    ("Qui fut le premier homme dans l'espace, en 1961 ?", "Youri Gagarine", ["Neil Armstrong", "Alan Shepard", "Buzz Aldrin", "Valentina Terechkova", "Laïka"], "Youri Gagarine a effectué un tour de la Terre le 12 avril 1961.", 2),
    ("Quel est le nom du premier satellite artificiel, lancé en 1957 ?", "Spoutnik 1", ["Explorer 1", "Apollo 1", "Vostok 1", "Hubble", "Voyager 1"], "Spoutnik 1 a été lancé par l'URSS le 4 octobre 1957.", 3),
    ("Quelle étoile est au centre du système solaire ?", "Le Soleil", ["Sirius", "Alpha du Centaure", "Polaris", "Bételgeuse", "Proxima"], "Le Soleil est une étoile naine jaune.", 1),
    ("Combien de temps la Terre met-elle pour faire le tour du Soleil ?", "Environ 365 jours", ["Environ 30 jours", "Environ 24 heures", "Environ 12 mois et demi", "Environ 700 jours", "Environ 100 jours"], "Une année dure environ 365,25 jours.", 1),
    ("Quelle est la vitesse approximative de la lumière dans le vide ?", "300 000 km/s", ["3 000 km/s", "30 000 km/s", "3 000 000 km/s", "1 000 km/s", "340 m/s"], "La lumière parcourt environ 300 000 kilomètres par seconde.", 3),
    ("Combien d'os compte le squelette d'un adulte ?", "206", ["106", "186", "306", "250", "150"], "Un adulte a 206 os, un nouveau-né environ 300.", 3),
    ("Combien de cavités (chambres) compte le cœur humain ?", "4", ["2", "3", "5", "6", "1"], "Deux oreillettes et deux ventricules.", 2),
    ("Quel est le plus grand organe du corps humain ?", "La peau", ["Le foie", "Le cœur", "Les poumons", "L'intestin", "Le cerveau"], "La peau couvre environ 2 m² chez l'adulte.", 2),
    ("Quel est l'organe qui pompe le sang dans le corps ?", "Le cœur", ["Le foie", "Les poumons", "Les reins", "L'estomac", "Le cerveau"], "Le cœur est un muscle.", 1),
    ("Quel est l'os le plus long du corps humain ?", "Le fémur", ["Le tibia", "L'humérus", "Le radius", "Le péroné", "La colonne vertébrale"], "Le fémur est dans la cuisse.", 3),
    ("Combien de dents compte un adulte (dents de sagesse comprises) ?", "32", ["28", "30", "34", "36", "24"], "Un adulte a 32 dents, un enfant 20 dents de lait.", 3),
    ("Quel gaz les plantes absorbent-elles pour la photosynthèse ?", "Le dioxyde de carbone", ["L'oxygène", "L'azote", "L'hydrogène", "Le méthane", "L'hélium"], "Les plantes absorbent le CO₂ et rejettent de l'oxygène.", 2),
    ("Comment s'appelle le pigment vert des plantes ?", "La chlorophylle", ["L'hémoglobine", "La mélanine", "La kératine", "L'insuline", "La caféine"], "La chlorophylle capte la lumière pour la photosynthèse.", 2),
    ("Quel est le plus grand animal du monde ?", "La baleine bleue", ["L'éléphant d'Afrique", "Le requin-baleine", "La girafe", "Le cachalot", "L'hippopotame"], "La baleine bleue peut dépasser 25 mètres.", 1),
    ("Quel est l'animal terrestre le plus rapide ?", "Le guépard", ["Le lion", "L'antilope", "Le lévrier", "Le cheval", "L'autruche"], "Le guépard peut dépasser 100 km/h sur de courtes distances.", 2),
    ("Combien de pattes a un insecte ?", "6", ["4", "8", "10", "12", "2"], "Les insectes ont six pattes ; les araignées en ont huit.", 1),
    ("À quelle classe d'animaux appartiennent les baleines ?", "Les mammifères", ["Les poissons", "Les reptiles", "Les amphibiens", "Les mollusques", "Les oiseaux"], "Les baleines respirent de l'air et allaitent leurs petits.", 2),
    ("Quel est le symbole chimique de l'or ?", "Au", ["Or", "Ag", "Go", "Fe", "Gd"], "Au vient du latin aurum.", 2),
    ("Quel est le symbole chimique du fer ?", "Fe", ["F", "Fr", "Ir", "Fi", "Fa"], "Fe vient du latin ferrum.", 2),
    ("Quel est le symbole chimique de l'argent ?", "Ag", ["Ar", "Au", "Al", "Am", "Ay"], "Ag vient du latin argentum.", 3),
    ("Quel est le symbole chimique du sodium ?", "Na", ["So", "S", "Sd", "N", "Ni"], "Na vient du latin natrium.", 3),
    ("Quel est le symbole chimique du potassium ?", "K", ["P", "Po", "Pt", "Pa", "Ka"], "K vient du latin kalium.", 3),
    ("Quel est le symbole chimique du plomb ?", "Pb", ["Pl", "P", "Po", "Pm", "Pu"], "Pb vient du latin plumbum.", 3),
    ("Quel est le symbole chimique de l'hélium ?", "He", ["H", "Hl", "Hm", "Ea", "Hg"], "He est le deuxième élément du tableau périodique.", 2),
    ("Quel est l'élément le plus abondant dans l'univers ?", "L'hydrogène", ["L'oxygène", "Le carbone", "L'hélium", "Le fer", "L'azote"], "L'hydrogène représente environ 75 % de la matière ordinaire.", 3),
    ("Quelle est la formule chimique de l'eau ?", "H₂O", ["CO₂", "O₂", "H₂O₂", "NaCl", "CH₄"], "Une molécule d'eau compte deux atomes d'hydrogène et un d'oxygène.", 1),
    ("Quelle est la formule chimique du sel de cuisine ?", "NaCl", ["KCl", "H₂SO₄", "CaCO₃", "NaOH", "HCl"], "Chlorure de sodium.", 2),
    ("À quelle température l'eau pure bout-elle au niveau de la mer ?", "100 °C", ["90 °C", "80 °C", "120 °C", "212 °C", "0 °C"], "100 °C ou 373 K.", 1),
    ("À quelle température l'eau pure gèle-t-elle au niveau de la mer ?", "0 °C", ["−10 °C", "4 °C", "10 °C", "32 °C", "−273 °C"], "0 °C ou 273 K.", 1),
    ("Combien de degrés compte un angle droit ?", "90", ["45", "60", "100", "180", "360"], "Un angle droit mesure 90°.", 1),
    ("Combien de degrés la somme des angles d'un triangle vaut-elle ?", "180", ["90", "270", "360", "100", "120"], "La somme des angles d'un triangle est de 180°.", 1),
    ("Combien de côtés a un hexagone ?", "6", ["5", "7", "8", "4", "10"], "Hexa = six.", 1),
    ("Quelle est la valeur approchée du nombre π ?", "3,14", ["2,71", "1,41", "1,62", "3,41", "4,13"], "π ≈ 3,14159.", 1),
    ("Quel scientifique a formulé la théorie de la relativité ?", "Albert Einstein", ["Isaac Newton", "Galilée", "Stephen Hawking", "Niels Bohr", "Nikola Tesla"], "Einstein publie la relativité restreinte en 1905.", 1),
    ("Quel scientifique a découvert la loi de la gravitation universelle ?", "Isaac Newton", ["Albert Einstein", "Galilée", "Johannes Kepler", "Copernic", "Archimède"], "Newton publie ses Principia en 1687.", 2),
    ("Quelle femme est la seule à avoir reçu deux prix Nobel dans deux sciences différentes ?", "Marie Curie", ["Rosalind Franklin", "Ada Lovelace", "Dorothy Hodgkin", "Irène Joliot-Curie", "Lise Meitner"], "Marie Curie a reçu le Nobel de physique (1903) et de chimie (1911).", 3),
    ("Qui a découvert la pénicilline en 1928 ?", "Alexander Fleming", ["Louis Pasteur", "Robert Koch", "Marie Curie", "Edward Jenner", "Charles Darwin"], "Fleming remarque l'action antibactérienne d'une moisissure.", 3),
    ("Qui a développé la théorie de l'évolution par sélection naturelle ?", "Charles Darwin", ["Gregor Mendel", "Louis Pasteur", "Carl Linné", "Jean-Baptiste Lamarck", "Alfred Wegener"], "« De l'origine des espèces » paraît en 1859.", 2),
    ("Qui a inventé le téléphone en 1876 ?", "Alexander Graham Bell", ["Thomas Edison", "Nikola Tesla", "Guglielmo Marconi", "Samuel Morse", "Charles Babbage"], "Bell dépose son brevet en 1876.", 3),
    ("Qui a inventé l'ampoule électrique à incandescence (version commerciale, 1879) ?", "Thomas Edison", ["Nikola Tesla", "Alexander Bell", "Benjamin Franklin", "Alessandro Volta", "James Watt"], "Edison et son équipe mettent au point une ampoule durable.", 3),
    ("Qui a créé le World Wide Web (le Web) en 1989 ?", "Tim Berners-Lee", ["Bill Gates", "Steve Jobs", "Mark Zuckerberg", "Linus Torvalds", "Vint Cerf"], "Tim Berners-Lee travaillait au CERN.", 3),
    ("Que signifie l'abréviation « ADN » ?", "Acide désoxyribonucléique", ["Acide diazonucléique", "Association de défense nationale", "Agent de dégradation nucléaire", "Acide dioxynucléique", "Acide désoxyribonique"], "L'ADN porte l'information génétique.", 3),
    ("Qui a découvert la structure en double hélice de l'ADN en 1953 (avec Crick) ?", "James Watson", ["Rosalind Franklin", "Gregor Mendel", "Louis Pasteur", "Linus Pauling", "Francis Bacon"], "Watson et Crick, en s'appuyant sur les clichés de Rosalind Franklin.", 4),
]
for q, right, wr, expl, d in SCI:
    fq(CM, "w-sci", q, right, wr, expl, "w-sci", W, "Sciences et nature", d)

HIST = [
    ("En quelle année a eu lieu la Révolution française ?", "1789", ["1799", "1776", "1815", "1848", "1914"], "La prise de la Bastille a eu lieu le 14 juillet 1789.", 1),
    ("En quelle année Christophe Colomb a-t-il atteint l'Amérique ?", "1492", ["1453", "1519", "1415", "1607", "1776"], "Colomb accoste aux Bahamas le 12 octobre 1492.", 2),
    ("En quelle année a débuté la Première Guerre mondiale ?", "1914", ["1912", "1916", "1918", "1939", "1905"], "La guerre commence en juillet-août 1914.", 1),
    ("En quelle année s'est terminée la Seconde Guerre mondiale ?", "1945", ["1940", "1944", "1946", "1950", "1918"], "La guerre se termine en 1945 (mai en Europe, septembre en Asie).", 1),
    ("En quelle année est tombé le mur de Berlin ?", "1989", ["1961", "1975", "1991", "1985", "1979"], "Le mur est ouvert le 9 novembre 1989.", 2),
    ("En quelle année l'Organisation des Nations unies (ONU) a-t-elle été créée ?", "1945", ["1919", "1939", "1950", "1960", "1948"], "La Charte de l'ONU entre en vigueur le 24 octobre 1945.", 3),
    ("En quelle année le paquebot Titanic a-t-il coulé ?", "1912", ["1905", "1914", "1920", "1898", "1923"], "Le Titanic coule dans l'Atlantique Nord dans la nuit du 14 au 15 avril 1912.", 2),
    ("En quelle année l'homme a-t-il marché pour la première fois sur la Lune ?", "1969", ["1961", "1965", "1972", "1957", "1981"], "Apollo 11, le 20 juillet 1969.", 2),
    ("Qui fut empereur des Français et vaincu à Waterloo en 1815 ?", "Napoléon Ier", ["Louis XIV", "Napoléon III", "Charlemagne", "Louis XVI", "Charles de Gaulle"], "Napoléon est battu à Waterloo puis exilé à Sainte-Hélène.", 2),
    ("Quel roi de France était surnommé « le Roi-Soleil » ?", "Louis XIV", ["Louis XVI", "Henri IV", "François Ier", "Charlemagne", "Napoléon"], "Louis XIV a régné de 1643 à 1715.", 2),
    ("Quel empereur fut couronné à Rome en l'an 800 ?", "Charlemagne", ["Napoléon", "Clovis", "Louis XIV", "Jules César", "Otton Ier"], "Charlemagne est couronné empereur le jour de Noël 800.", 3),
    ("Quelle civilisation a construit les pyramides de Gizeh ?", "Les Égyptiens de l'Antiquité", ["Les Romains", "Les Grecs", "Les Aztèques", "Les Mayas", "Les Mésopotamiens"], "Les pyramides ont été bâties il y a environ 4 500 ans.", 1),
    ("Quelle civilisation a construit le Machu Picchu ?", "Les Incas", ["Les Mayas", "Les Aztèques", "Les Égyptiens", "Les Romains", "Les Olmèques"], "Le Machu Picchu se trouve au Pérou.", 2),
    ("Qui a été le chef de la lutte contre l'apartheid et prix Nobel de la paix 1993 avec F. W. de Klerk ?", "Nelson Mandela", ["Desmond Tutu", "Steve Biko", "Martin Luther King", "Mahatma Gandhi", "Kofi Annan"], "Mandela et de Klerk ont reçu le prix Nobel de la paix en 1993.", 3),
    ("Qui a mené la marche du sel et l'indépendance de l'Inde par la non-violence ?", "Mahatma Gandhi", ["Jawaharlal Nehru", "Nelson Mandela", "Martin Luther King", "Mao Zedong", "Ho Chi Minh"], "L'Inde obtient son indépendance en 1947.", 2),
    ("Qui a prononcé le discours « I have a dream » en 1963 ?", "Martin Luther King", ["Malcolm X", "Barack Obama", "Nelson Mandela", "Abraham Lincoln", "John F. Kennedy"], "Le discours a été prononcé à Washington le 28 août 1963.", 2),
    ("Qui a peint la Joconde (Mona Lisa) ?", "Léonard de Vinci", ["Michel-Ange", "Raphaël", "Vincent van Gogh", "Pablo Picasso", "Claude Monet"], "La Joconde est exposée au musée du Louvre à Paris.", 1),
    ("Qui a peint « La Nuit étoilée » ?", "Vincent van Gogh", ["Claude Monet", "Pablo Picasso", "Paul Cézanne", "Salvador Dalí", "Rembrandt"], "Van Gogh peint ce tableau en 1889.", 2),
    ("Qui a peint « Guernica » ?", "Pablo Picasso", ["Salvador Dalí", "Joan Miró", "Henri Matisse", "Francisco de Goya", "Vincent van Gogh"], "Guernica (1937) dénonce le bombardement de la ville espagnole.", 3),
    ("Qui a écrit « Les Misérables » ?", "Victor Hugo", ["Émile Zola", "Alexandre Dumas", "Gustave Flaubert", "Honoré de Balzac", "Molière"], "Le roman est publié en 1862.", 2),
    ("Qui a écrit « Roméo et Juliette » ?", "William Shakespeare", ["Molière", "Victor Hugo", "Charles Dickens", "Oscar Wilde", "Goethe"], "La pièce date des années 1590.", 1),
    ("Qui a écrit « Le Petit Prince » ?", "Antoine de Saint-Exupéry", ["Jules Verne", "Victor Hugo", "Albert Camus", "Marcel Pagnol", "Jean de La Fontaine"], "Le livre paraît en 1943.", 2),
    ("Qui a écrit « Don Quichotte » ?", "Miguel de Cervantès", ["Gabriel García Márquez", "Federico García Lorca", "Lope de Vega", "Jorge Luis Borges", "Pablo Neruda"], "Le roman est publié en 1605.", 3),
    ("Quel compositeur a écrit la « Neuvième Symphonie » avec l'« Ode à la joie » ?", "Ludwig van Beethoven", ["Wolfgang Amadeus Mozart", "Johann Sebastian Bach", "Antonio Vivaldi", "Frédéric Chopin", "Joseph Haydn"], "Beethoven achève sa 9e symphonie en 1824.", 2),
    ("Qui a composé « Les Quatre Saisons » ?", "Antonio Vivaldi", ["Johann Sebastian Bach", "Ludwig van Beethoven", "Wolfgang Amadeus Mozart", "Georg Friedrich Haendel", "Franz Schubert"], "Concertos pour violon de Vivaldi (vers 1723).", 3),
    ("Quel est le plus grand océan du monde ?", "L'océan Pacifique", ["L'océan Atlantique", "L'océan Indien", "L'océan Arctique", "L'océan Austral", "La Méditerranée"], "Le Pacifique couvre plus de 30 % de la surface du globe.", 1),
    ("Quel est le plus haut sommet du monde ?", "L'Everest", ["Le K2", "Le Kilimandjaro", "Le mont Blanc", "L'Aconcagua", "Le Denali"], "L'Everest culmine à 8 849 m dans l'Himalaya.", 1),
    ("Quel est le plus haut sommet des Alpes ?", "Le mont Blanc", ["Le Cervin", "L'Everest", "Le Mont-Rose", "L'Éiger", "Le Grand Paradis"], "Le mont Blanc culmine à environ 4 806 m.", 2),
    ("Quel est le plus grand pays du monde par sa superficie ?", "La Russie", ["Le Canada", "La Chine", "Les États-Unis", "Le Brésil", "L'Australie"], "La Russie s'étend sur plus de 17 millions de km².", 1),
    ("Quel est le plus petit pays du monde ?", "Le Vatican", ["Monaco", "Saint-Marin", "Liechtenstein", "Malte", "Andorre"], "Le Vatican fait environ 0,44 km².", 2),
    ("Quel fleuve traverse Paris ?", "La Seine", ["La Loire", "Le Rhône", "La Garonne", "Le Rhin", "La Tamise"], "La Seine passe par Paris et se jette dans la Manche.", 1),
    ("Quel fleuve traverse Londres ?", "La Tamise", ["La Seine", "Le Danube", "Le Rhin", "La Tweed", "La Mersey"], "La Tamise traverse la capitale britannique.", 2),
    ("Quel est le plus long fleuve d'Europe ?", "La Volga", ["Le Danube", "Le Rhin", "La Seine", "La Loire", "L'Oural"], "La Volga mesure environ 3 500 km.", 3),
    ("Dans quel pays se trouve le Taj Mahal ?", "L'Inde", ["Le Pakistan", "Le Bangladesh", "L'Iran", "L'Égypte", "Le Népal"], "Le Taj Mahal est à Agra.", 2),
    ("Dans quel pays se trouve la Grande Muraille ?", "La Chine", ["Le Japon", "L'Inde", "La Mongolie", "La Corée du Sud", "Le Vietnam"], "La Grande Muraille de Chine s'étend sur des milliers de kilomètres.", 1),
    ("Dans quelle ville se trouve la tour Eiffel ?", "Paris", ["Lyon", "Marseille", "Bruxelles", "Londres", "Rome"], "La tour Eiffel a été inaugurée en 1889.", 1),
    ("Dans quelle ville se trouve le Colisée ?", "Rome", ["Athènes", "Paris", "Naples", "Istanbul", "Madrid"], "Le Colisée est un amphithéâtre romain.", 1),
    ("Dans quel pays se trouve le Machu Picchu ?", "Le Pérou", ["Le Mexique", "La Bolivie", "Le Chili", "La Colombie", "L'Équateur"], "Le Machu Picchu domine la vallée de l'Urubamba.", 3),
    ("Quelle est la langue la plus parlée au monde comme langue maternelle ?", "Le chinois mandarin", ["L'anglais", "L'espagnol", "Le hindi", "L'arabe", "Le français"], "Le mandarin compte le plus de locuteurs natifs.", 3),
    ("Combien d'États membres l'Union européenne comptait-elle en 2023 ?", "27", ["25", "28", "30", "15", "12"], "Depuis le départ du Royaume-Uni en 2020, l'UE compte 27 pays.", 3),
    ("Où se trouve le siège de l'ONU ?", "New York", ["Genève", "Paris", "Bruxelles", "Washington", "La Haye"], "Le siège principal est à New York.", 2),
    ("Où se trouve le siège de l'UNESCO ?", "Paris", ["New York", "Genève", "Rome", "Vienne", "Londres"], "L'UNESCO a son siège à Paris.", 3),
    ("Où se trouve le siège de l'OMS (Organisation mondiale de la santé) ?", "Genève", ["New York", "Paris", "Rome", "Vienne", "Londres"], "L'OMS a son siège à Genève.", 3),
]
for q, right, wr, expl, d in HIST:
    fq(CM, "w-hist", q, right, wr, expl, "w-hist", W, "Histoire, arts et monde", d)

SPORT = [
    ("Quelle équipe a gagné la Coupe du monde de football en 2018 ?", "La France", ["La Croatie", "La Belgique", "Le Brésil", "L'Allemagne", "L'Angleterre"], "La France bat la Croatie 4-2 en finale à Moscou.", 1),
    ("Quelle équipe a gagné la Coupe du monde de football en 2022 ?", "L'Argentine", ["La France", "Le Maroc", "La Croatie", "Le Brésil", "L'Angleterre"], "L'Argentine bat la France aux tirs au but à Lusail.", 1),
    ("Quelle équipe a gagné la Coupe du monde de football en 2014 ?", "L'Allemagne", ["L'Argentine", "Le Brésil", "Les Pays-Bas", "L'Espagne", "La France"], "L'Allemagne bat l'Argentine 1-0 en finale à Rio.", 2),
    ("Quelle équipe a gagné la Coupe du monde de football en 2010 ?", "L'Espagne", ["Les Pays-Bas", "L'Allemagne", "Le Brésil", "L'Italie", "L'Uruguay"], "L'Espagne bat les Pays-Bas en Afrique du Sud.", 2),
    ("Quelle équipe a gagné la Coupe du monde de football en 1998 ?", "La France", ["Le Brésil", "L'Italie", "L'Allemagne", "L'Argentine", "La Croatie"], "La France bat le Brésil 3-0 au Stade de France.", 1),
    ("Quel pays a remporté le plus de Coupes du monde de football masculines ?", "Le Brésil", ["L'Allemagne", "L'Italie", "L'Argentine", "La France", "L'Uruguay"], "Le Brésil a gagné cinq fois (1958, 1962, 1970, 1994, 2002).", 2),
    ("Dans quel pays s'est déroulée la première Coupe du monde de football en 1930 ?", "L'Uruguay", ["Le Brésil", "L'Italie", "La France", "L'Argentine", "Le Mexique"], "L'Uruguay, pays hôte, a aussi gagné le tournoi.", 3),
    ("Quel footballeur argentin a reçu huit Ballons d'or (jusqu'en 2023) ?", "Lionel Messi", ["Cristiano Ronaldo", "Diego Maradona", "Neymar", "Kylian Mbappé", "Pelé"], "Lionel Messi a reçu son huitième Ballon d'or en 2023.", 2),
    ("Quel footballeur brésilien a remporté trois Coupes du monde (1958, 1962, 1970) ?", "Pelé", ["Maradona", "Ronaldo", "Romário", "Neymar", "Zico"], "Pelé est le seul joueur triple champion du monde.", 2),
    ("Quelle ville a accueilli les Jeux olympiques d'été de 2016 ?", "Rio de Janeiro", ["Londres", "Pékin", "Tokyo", "Athènes", "Paris"], "Rio a accueilli les premiers Jeux d'Amérique du Sud.", 2),
    ("Quelle ville a accueilli les Jeux olympiques d'été de 2008 ?", "Pékin", ["Athènes", "Londres", "Sydney", "Rio de Janeiro", "Séoul"], "Les Jeux de Pékin ont eu lieu en août 2008.", 2),
    ("Quelle ville a accueilli les Jeux olympiques d'été de 2012 ?", "Londres", ["Pékin", "Paris", "Rio de Janeiro", "Athènes", "Tokyo"], "Londres a accueilli les Jeux en 2012.", 2),
    ("Dans quelle ville se sont déroulés les premiers Jeux olympiques modernes en 1896 ?", "Athènes", ["Paris", "Londres", "Rome", "Berlin", "Stockholm"], "Pierre de Coubertin a ressuscité les Jeux olympiques.", 2),
    ("Dans quelle ville se sont tenus les Jeux olympiques d'été de 2000 ?", "Sydney", ["Atlanta", "Athènes", "Pékin", "Barcelone", "Londres"], "Sydney a accueilli les Jeux en septembre 2000.", 2),
    ("Combien d'anneaux figurent sur le drapeau olympique ?", "5", ["4", "6", "7", "3", "8"], "Cinq anneaux entrelacés symbolisent les cinq continents.", 1),
    ("Quel sprinteur jamaïcain détient les records du monde du 100 m (9 s 58) et du 200 m ?", "Usain Bolt", ["Yohan Blake", "Carl Lewis", "Justin Gatlin", "Asafa Powell", "Tyson Gay"], "Usain Bolt a établi ses records aux Mondiaux de Berlin en 2009.", 2),
    ("Combien de joueurs une équipe de football compte-t-elle sur le terrain ?", "11", ["9", "10", "12", "7", "15"], "Onze joueurs par équipe, gardien compris.", 1),
    ("Combien de joueurs une équipe de basket-ball compte-t-elle sur le terrain ?", "5", ["4", "6", "7", "9", "11"], "Cinq joueurs par équipe.", 1),
    ("Combien de joueurs une équipe de rugby à XV compte-t-elle sur le terrain ?", "15", ["11", "13", "14", "16", "12"], "Quinze joueurs, huit avants et sept arrières.", 2),
    ("Quel sport se joue à Wimbledon ?", "Le tennis", ["Le golf", "Le cricket", "Le football", "Le rugby", "Le polo"], "Wimbledon est le plus ancien tournoi de tennis du Grand Chelem.", 1),
    ("Quelle est la distance d'un marathon ?", "42,195 km", ["40 km", "21,1 km", "50 km", "36 km", "45 km"], "42,195 kilomètres.", 2),
]
for q, right, wr, expl, d in SPORT:
    fq(CM, "w-sport", q, right, wr, expl, "w-sport", W, "Sport mondial", d)
