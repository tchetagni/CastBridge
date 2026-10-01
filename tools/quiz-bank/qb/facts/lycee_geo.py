"""Géographie (2nde, 1re, Tle) et Geography (GCE O / A Level) : capitales, Cameroun physique et économique, notions.
Faits écrits par l'assistant, tous `review` ; seuls les faits stables et vérifiables sont retenus (pas de chiffres qui changent)."""
from .. import lycee
from ..facts_engine import fq, pick, source

source("ly-geo-fr", "Géographie du second cycle francophone (2nde, 1re, Tle)", "programme scolaire", "Comparer avec le programme MINESEC et un atlas du Cameroun agréé ; vérifier surtout les noms de sites et d'ouvrages.")
source("ly-geo-en", "Geography, GCE Ordinary and Advanced Level", "programme scolaire", "Compare with the GCE Board syllabus and a Cameroon atlas; check place names and the names of dams and parks in particular.")
LV = {"2nde": "f5", "1re": "l6", "tle": "u6"}

# ---------------------------------------------------------------------------------------------- capitales
# (« du Sénégal », "Senegal", "Dakar", "Dakar")
AFRICA = [("du Sénégal", "Senegal", "Dakar", "Dakar"), ("du Nigeria", "Nigeria", "Abuja", "Abuja"), ("du Ghana", "Ghana", "Accra", "Accra"), ("du Kenya", "Kenya", "Nairobi", "Nairobi"),
          ("de l'Éthiopie", "Ethiopia", "Addis-Abeba", "Addis Ababa"), ("de l'Égypte", "Egypt", "Le Caire", "Cairo"), ("de la République démocratique du Congo", "the Democratic Republic of the Congo", "Kinshasa", "Kinshasa"),
          ("de la République du Congo", "the Republic of the Congo", "Brazzaville", "Brazzaville"), ("du Gabon", "Gabon", "Libreville", "Libreville"), ("de la République centrafricaine", "the Central African Republic", "Bangui", "Bangui"),
          ("du Tchad", "Chad", "N'Djamena", "N'Djamena"), ("du Niger", "Niger", "Niamey", "Niamey"), ("du Mali", "Mali", "Bamako", "Bamako"), ("du Burkina Faso", "Burkina Faso", "Ouagadougou", "Ouagadougou"),
          ("du Togo", "Togo", "Lomé", "Lomé"), ("de l'Angola", "Angola", "Luanda", "Luanda"), ("de la Zambie", "Zambia", "Lusaka", "Lusaka"), ("du Zimbabwe", "Zimbabwe", "Harare", "Harare"),
          ("du Mozambique", "Mozambique", "Maputo", "Maputo"), ("de la Namibie", "Namibia", "Windhoek", "Windhoek"), ("du Botswana", "Botswana", "Gaborone", "Gaborone"), ("de Madagascar", "Madagascar", "Antananarivo", "Antananarivo"),
          ("de l'Ouganda", "Uganda", "Kampala", "Kampala"), ("du Rwanda", "Rwanda", "Kigali", "Kigali"), ("du Soudan", "Sudan", "Khartoum", "Khartoum"), ("de la Libye", "Libya", "Tripoli", "Tripoli"),
          ("de la Tunisie", "Tunisia", "Tunis", "Tunis"), ("de l'Algérie", "Algeria", "Alger", "Algiers"), ("du Maroc", "Morocco", "Rabat", "Rabat"), ("de la Mauritanie", "Mauritania", "Nouakchott", "Nouakchott"),
          ("de la Guinée", "Guinea", "Conakry", "Conakry"), ("de la Sierra Leone", "Sierra Leone", "Freetown", "Freetown"), ("du Liberia", "Liberia", "Monrovia", "Monrovia"), ("de la Gambie", "The Gambia", "Banjul", "Banjul"),
          ("de la Guinée équatoriale", "Equatorial Guinea", "Malabo", "Malabo"), ("du Malawi", "Malawi", "Lilongwe", "Lilongwe"), ("de l'Érythrée", "Eritrea", "Asmara", "Asmara"), ("du Cameroun", "Cameroon", "Yaoundé", "Yaoundé")]
WORLD = [("de la France", "France", "Paris", "Paris"), ("de l'Allemagne", "Germany", "Berlin", "Berlin"), ("de l'Italie", "Italy", "Rome", "Rome"), ("de l'Espagne", "Spain", "Madrid", "Madrid"),
         ("du Portugal", "Portugal", "Lisbonne", "Lisbon"), ("du Royaume-Uni", "the United Kingdom", "Londres", "London"), ("de la Russie", "Russia", "Moscou", "Moscow"), ("de la Chine", "China", "Pékin", "Beijing"),
         ("du Japon", "Japan", "Tokyo", "Tokyo"), ("de l'Inde", "India", "New Delhi", "New Delhi"), ("du Brésil", "Brazil", "Brasília", "Brasília"), ("de l'Argentine", "Argentina", "Buenos Aires", "Buenos Aires"),
         ("du Canada", "Canada", "Ottawa", "Ottawa"), ("des États-Unis", "the United States", "Washington", "Washington, D.C."), ("de l'Australie", "Australia", "Canberra", "Canberra"), ("de la Turquie", "Turkey", "Ankara", "Ankara"),
         ("de l'Arabie saoudite", "Saudi Arabia", "Riyad", "Riyadh"), ("de la Corée du Sud", "South Korea", "Séoul", "Seoul"), ("du Mexique", "Mexico", "Mexico", "Mexico City"), ("du Pérou", "Peru", "Lima", "Lima"),
         ("du Chili", "Chile", "Santiago", "Santiago"), ("de la Suisse", "Switzerland", "Berne", "Bern"), ("de la Norvège", "Norway", "Oslo", "Oslo"), ("de la Suède", "Sweden", "Stockholm", "Stockholm"),
         ("de la Grèce", "Greece", "Athènes", "Athens"), ("de la Pologne", "Poland", "Varsovie", "Warsaw"), ("de la Belgique", "Belgium", "Bruxelles", "Brussels"), ("de l'Indonésie", "Indonesia", "Jakarta", "Jakarta"),
         ("du Pakistan", "Pakistan", "Islamabad", "Islamabad"), ("de l'Iran", "Iran", "Téhéran", "Tehran"), ("de l'Irak", "Iraq", "Bagdad", "Baghdad"), ("de Cuba", "Cuba", "La Havane", "Havana"),
         ("de la Colombie", "Colombia", "Bogota", "Bogotá"), ("du Venezuela", "Venezuela", "Caracas", "Caracas"), ("de la Nouvelle-Zélande", "New Zealand", "Wellington", "Wellington"), ("de la Thaïlande", "Thailand", "Bangkok", "Bangkok"),
         ("du Vietnam", "Vietnam", "Hanoï", "Hanoi")]
REGIONS = [("de l'Adamaoua", "Adamawa", "Ngaoundéré"), ("du Centre", "Centre", "Yaoundé"), ("de l'Est", "East", "Bertoua"), ("de l'Extrême-Nord", "Far North", "Maroua"), ("du Littoral", "Littoral", "Douala"),
           ("du Nord", "North", "Garoua"), ("du Nord-Ouest", "North-West", "Bamenda"), ("de l'Ouest", "West", "Bafoussam"), ("du Sud", "South", "Ebolowa"), ("du Sud-Ouest", "South-West", "Buea")]


def capital_set(rows, region, tpl, diff):
    fr_caps, en_caps = [r[2] for r in rows], [r[3] for r in rows]
    for de, en, cf, ce in rows:
        for lvl, d in (("2nde", diff), ("1re", diff + 1)):
            fq(lycee.fr(lvl, "geo"), tpl, f"Quelle est la capitale {de} ?", cf, pick(tpl + cf, fr_caps, cf, 8), f"{cf} est la capitale {de}.", "ly-geo-fr", region, "Capitales", min(d, 4))
        fq(lycee.en("f5", "geo"), tpl, f"What is the capital of {en}?", ce, pick(tpl + ce, en_caps, ce, 8), f"{ce} is the capital of {en}.", "ly-geo-en", region, "Capitals", diff)
        cap = lambda t: t[0].upper() + t[1:]
        fq(lycee.en("l6", "geo"), tpl, f"Which country has {ce} as its capital?", cap(en), pick(tpl + "rev" + en, [cap(r[1]) for r in rows], cap(en), 8), f"{ce} is the capital of {en}.", "ly-geo-en", region, "Capitals", diff + 1)


capital_set(AFRICA, "AF", "geo-cap-af", 2)
capital_set(WORLD, "WORLD", "geo-cap-world", 2)
for de, en, ch in REGIONS:
    cs = [r[2] for r in REGIONS]
    for lvl in ("2nde", "1re"):
        fq(lycee.fr(lvl, "geo"), "geo-chef-lieu", f"Quel est le chef-lieu de la région {de} au Cameroun ?", ch, pick("cl" + ch, cs + ["Kribi", "Limbé", "Édéa", "Bafia", "Garoua-Boulaï"], ch, 8), f"{ch} est le chef-lieu de la région {de}.", "ly-geo-fr", "CM", "Cameroun", 1)
    fq(lycee.en("f5", "geo"), "geo-chef-lieu", f"What is the regional capital of the {en} Region of Cameroon?", ch, pick("cl" + ch, cs + ["Kribi", "Limbe", "Edea", "Bafia", "Kumba"], ch, 8), f"{ch} is the capital of the {en} Region.", "ly-geo-en", "CM", "Cameroon", 1)

# ---------------------------------------------------------------------------------------------- notions bilingues
# (niveau fr, difficulté, région, (question fr, bonne, [mauvaises], explication), (question en, bonne, [mauvaises], explication))
B = [
    ("2nde", 1, "WORLD", ("Comment appelle-t-on la ligne imaginaire à 0° de latitude ?", "L'équateur", ["Le méridien de Greenwich", "Le tropique du Cancer", "Le cercle polaire", "Le tropique du Capricorne"], "L'équateur sépare les hémisphères Nord et Sud."),
     ("What is the imaginary line at 0° latitude called?", "The Equator", ["The Prime Meridian", "The Tropic of Cancer", "The Arctic Circle", "The Tropic of Capricorn"], "It divides the Northern and Southern Hemispheres.")),
    ("2nde", 1, "WORLD", ("Comment appelle-t-on le méridien d'origine (0° de longitude) ?", "Le méridien de Greenwich", ["L'équateur", "Le tropique du Cancer", "Le méridien de Paris", "La ligne de changement de date"], "Il passe par l'observatoire de Greenwich, près de Londres."),
     ("What is the line of 0° longitude called?", "The Prime Meridian (Greenwich Meridian)", ["The Equator", "The Tropic of Cancer", "The International Date Line", "The Arctic Circle"], "It passes through Greenwich, London.")),
    ("2nde", 1, "WORLD", ("Combien de continents compte-t-on classiquement ?", "7", ["5", "6", "8", "9"], "Afrique, Amérique du Nord, Amérique du Sud, Antarctique, Asie, Europe, Océanie."),
     ("How many continents are usually counted?", "7", ["5", "6", "8", "9"], "Africa, North America, South America, Antarctica, Asia, Europe and Oceania.")),
    ("2nde", 1, "WORLD", ("Quel est le plus grand océan du monde ?", "L'océan Pacifique", ["L'océan Atlantique", "L'océan Indien", "L'océan Arctique", "L'océan Austral"], "Le Pacifique couvre près d'un tiers de la surface du globe."),
     ("Which is the largest ocean in the world?", "The Pacific Ocean", ["The Atlantic Ocean", "The Indian Ocean", "The Arctic Ocean", "The Southern Ocean"], "It covers about a third of the Earth's surface.")),
    ("2nde", 2, "AF", ("Quel est le plus grand désert chaud du monde, situé en Afrique ?", "Le Sahara", ["Le Kalahari", "Le Namib", "Le Gobi", "Le Néguev"], "Le Sahara traverse le nord de l'Afrique."),
     ("Which is the largest hot desert in the world, located in Africa?", "The Sahara", ["The Kalahari", "The Namib", "The Gobi", "The Negev"], "The Sahara spans North Africa.")),
    ("2nde", 2, "AF", ("Quel est le plus haut sommet d'Afrique ?", "Le Kilimandjaro", ["Le mont Kenya", "Le mont Cameroun", "Le mont Stanley", "Le mont Blanc"], "Le Kilimandjaro se trouve en Tanzanie."),
     ("What is the highest mountain in Africa?", "Mount Kilimanjaro", ["Mount Kenya", "Mount Cameroon", "Mount Stanley", "Mount Elgon"], "It stands in Tanzania.")),
    ("2nde", 2, "AF", ("Quel est le plus grand lac d'Afrique par sa superficie ?", "Le lac Victoria", ["Le lac Tchad", "Le lac Tanganyika", "Le lac Malawi", "Le lac Nasser"], "Le lac Victoria est partagé entre l'Ouganda, le Kenya et la Tanzanie."),
     ("What is the largest lake in Africa by area?", "Lake Victoria", ["Lake Chad", "Lake Tanganyika", "Lake Malawi", "Lake Nasser"], "It is shared by Uganda, Kenya and Tanzania.")),
    ("2nde", 2, "AF", ("Quel fleuve africain traverse le Mali, le Niger et le Nigeria avant de se jeter dans le golfe de Guinée ?", "Le Niger", ["Le Congo", "Le Zambèze", "Le Nil", "La Sanaga"], "Le fleuve Niger a un delta au Nigeria."),
     ("Which African river flows through Mali, Niger and Nigeria into the Gulf of Guinea?", "The River Niger", ["The Congo", "The Zambezi", "The Nile", "The Sanaga"], "It forms a large delta in Nigeria.")),
    ("2nde", 2, "AF", ("Quel fleuve africain traverse l'Égypte et se jette dans la Méditerranée ?", "Le Nil", ["Le Niger", "Le Congo", "Le Zambèze", "Le Limpopo"], "Le Nil forme un delta au nord de l'Égypte."),
     ("Which African river flows through Egypt into the Mediterranean?", "The Nile", ["The Niger", "The Congo", "The Zambezi", "The Limpopo"], "It forms a delta in northern Egypt.")),
    ("2nde", 2, "AF", ("Quelle forêt tropicale africaine est la deuxième plus grande du monde après l'Amazonie ?", "La forêt du bassin du Congo", ["La forêt de Taï", "La forêt de Kakamega", "La forêt de Korup", "La forêt de Bwindi"], "Elle couvre une partie du Cameroun, du Gabon, du Congo, de la RDC et de la Centrafrique."),
     ("Which African rainforest is the second largest in the world after the Amazon?", "The Congo Basin rainforest", ["The Taï Forest", "The Kakamega Forest", "The Korup Forest", "The Bwindi Forest"], "It covers parts of Cameroon, Gabon, Congo, DRC and the Central African Republic.")),
    ("2nde", 1, "CM", ("Quel est le plus haut sommet du Cameroun et un volcan actif ?", "Le mont Cameroun", ["Le mont Oku", "Le mont Manengouba", "Le mont Kupe", "Les monts Mandara"], "Il se situe dans la région du Sud-Ouest, près de Buea."),
     ("What is the highest mountain in Cameroon and an active volcano?", "Mount Cameroon", ["Mount Oku", "Mount Manengouba", "Mount Kupe", "The Mandara Mountains"], "It stands in the South-West Region near Buea.")),
    ("2nde", 2, "CM", ("Quel plateau, appelé « château d'eau » du Cameroun, sépare le Sud du Nord ?", "Le plateau de l'Adamaoua", ["Le plateau Bamiléké", "Le plateau sud-camerounais", "Le plateau du Mbam", "Les monts Mandara"], "Plusieurs fleuves y prennent leur source."),
     ("Which plateau, called the 'water tower' of Cameroon, separates the South from the North?", "The Adamawa Plateau", ["The Bamileke Plateau", "The South Cameroon Plateau", "The Mbam Plateau", "The Mandara Mountains"], "Several rivers rise there.")),
    ("2nde", 2, "CM", ("Quel lac partagé entre le Cameroun, le Tchad, le Niger et le Nigeria se trouve à l'Extrême-Nord ?", "Le lac Tchad", ["Le lac Nyos", "Le lac Bamendjin", "Le lac Lagdo", "Le lac Barombi Mbo"], "Le lac Tchad a beaucoup diminué depuis les années 1960."),
     ("Which lake shared by Cameroon, Chad, Niger and Nigeria lies in the Far North?", "Lake Chad", ["Lake Nyos", "Lake Bamendjin", "Lake Lagdo", "Lake Barombi Mbo"], "Lake Chad has shrunk greatly since the 1960s.")),
    ("2nde", 3, "CM", ("Quel est le plus long fleuve entièrement camerounais ?", "La Sanaga", ["Le Wouri", "Le Nyong", "La Bénoué", "Le Logone"], "La Sanaga se jette dans l'océan Atlantique près d'Édéa."),
     ("Which is the longest river lying entirely in Cameroon?", "The Sanaga", ["The Wouri", "The Nyong", "The Benue", "The Logone"], "It reaches the Atlantic near Edéa.")),
    ("2nde", 2, "CM", ("Quel fleuve forme l'estuaire sur lequel est bâtie la ville de Douala ?", "Le Wouri", ["La Sanaga", "Le Nyong", "Le Mungo", "Le Ntem"], "Le Wouri se jette dans le golfe de Guinée à Douala."),
     ("Which river forms the estuary on which Douala is built?", "The Wouri", ["The Sanaga", "The Nyong", "The Mungo", "The Ntem"], "It reaches the Gulf of Guinea at Douala.")),
    ("2nde", 2, "CM", ("Quel est le principal port maritime du Cameroun ?", "Douala", ["Kribi", "Limbé", "Garoua", "Tiko"], "Le port de Douala assure l'essentiel des échanges, y compris pour le Tchad et la Centrafrique."),
     ("What is Cameroon's main seaport?", "Douala", ["Kribi", "Limbe", "Garoua", "Tiko"], "Douala handles most trade, including goods for Chad and the Central African Republic.")),
    ("2nde", 3, "CM", ("Quel port en eau profonde a été construit dans le Sud du Cameroun ?", "Kribi", ["Limbé", "Douala", "Tiko", "Idenau"], "Le port de Kribi est conçu pour accueillir de grands navires."),
     ("Which deep-sea port has been built in the south of Cameroon?", "Kribi", ["Limbe", "Douala", "Tiko", "Idenau"], "Kribi can receive very large ships.")),
    ("1re", 3, "CM", ("Sur quel fleuve est construit le barrage de Lagdo, dans le Nord du Cameroun ?", "La Bénoué", ["La Sanaga", "Le Logone", "Le Wouri", "Le Nyong"], "Le barrage de Lagdo produit de l'électricité et sert à l'irrigation."),
     ("On which river is the Lagdo dam in northern Cameroon built?", "The Benue", ["The Sanaga", "The Logone", "The Wouri", "The Nyong"], "It produces electricity and supplies irrigation.")),
    ("1re", 3, "CM", ("Sur quel fleuve se trouvent les barrages d'Édéa et de Song Loulou ?", "La Sanaga", ["La Bénoué", "Le Wouri", "Le Ntem", "Le Nyong"], "Ils alimentent le réseau électrique du Sud."),
     ("On which river are the Edéa and Song Loulou dams built?", "The Sanaga", ["The Benue", "The Wouri", "The Ntem", "The Nyong"], "They supply electricity to the southern grid.")),
    ("1re", 3, "CM", ("Dans quelle ville se trouve la raffinerie de pétrole SONARA ?", "Limbé", ["Douala", "Kribi", "Édéa", "Buea"], "La SONARA, société nationale de raffinage, est à Limbé."),
     ("In which town is the SONARA oil refinery located?", "Limbe", ["Douala", "Kribi", "Edea", "Buea"], "SONARA is Cameroon's national refinery.")),
    ("1re", 3, "CM", ("Dans quelle ville est installée l'usine d'aluminium Alucam ?", "Édéa", ["Douala", "Limbé", "Kribi", "Bafoussam"], "Alucam utilise l'électricité des barrages de la Sanaga."),
     ("In which town is the Alucam aluminium smelter?", "Edéa", ["Douala", "Limbe", "Kribi", "Bafoussam"], "It uses power from the Sanaga dams.")),
    ("1re", 2, "CM", ("Quel parc national du Nord du Cameroun est célèbre pour ses éléphants et ses lions ?", "Le parc national de Waza", ["Le parc de Korup", "Le parc de Campo Ma'an", "Le parc de Lobéké", "Le parc du mont Cameroun"], "Waza se trouve dans la région de l'Extrême-Nord."),
     ("Which national park in northern Cameroon is famous for elephants and lions?", "Waza National Park", ["Korup National Park", "Campo Ma'an National Park", "Lobéké National Park", "Mount Cameroon National Park"], "Waza lies in the Far North Region.")),
    ("1re", 3, "CM", ("Dans quelle région se trouve le parc national de Korup, forêt tropicale très riche en biodiversité ?", "Le Sud-Ouest", ["Le Nord", "L'Extrême-Nord", "L'Adamaoua", "L'Ouest"], "Korup est l'une des plus anciennes forêts tropicales d'Afrique."),
     ("In which region is Korup National Park, a very biodiverse rainforest?", "The South-West", ["The North", "The Far North", "Adamawa", "The West"], "Korup is one of Africa's oldest rainforests.")),
    ("1re", 3, "CM", ("Quelle réserve du Sud-Est du Cameroun est inscrite au patrimoine mondial de l'UNESCO ?", "La réserve de faune du Dja", ["La réserve de Waza", "La réserve de Bénoué", "La réserve de Douala-Édéa", "La réserve de Mbam et Djerem"], "La réserve du Dja est un site naturel du patrimoine mondial."),
     ("Which reserve in south-eastern Cameroon is a UNESCO World Heritage Site?", "The Dja Faunal Reserve", ["Waza Reserve", "Benue Reserve", "Douala-Edéa Reserve", "Mbam and Djerem Reserve"], "It is a World Heritage natural site.")),
    ("1re", 2, "CM", ("Quelle culture de rente est la plus importante dans les régions Centre et Sud du Cameroun ?", "Le cacao", ["Le coton", "Le thé", "L'arachide", "Le tabac"], "Le Cameroun est un producteur de cacao."),
     ("Which cash crop is most important in the Centre and South regions of Cameroon?", "Cocoa", ["Cotton", "Tea", "Groundnuts", "Tobacco"], "Cameroon is a cocoa producer.")),
    ("1re", 2, "CM", ("Quelle culture est la principale culture de rente du Nord du Cameroun ?", "Le coton", ["Le cacao", "La banane", "L'hévéa", "Le palmier à huile"], "Le coton est cultivé dans la zone soudano-sahélienne."),
     ("Which is the main cash crop of northern Cameroon?", "Cotton", ["Cocoa", "Bananas", "Rubber", "Oil palm"], "Cotton is grown in the Sudano-Sahelian zone.")),
    ("1re", 3, "CM", ("Quelle société de la région du Sud-Ouest produit entre autres des bananes, de l'hévéa et du palmier à huile ?", "La CDC (Cameroon Development Corporation)", ["La SOSUCAM", "La SODECOTON", "La SONARA", "La CAMRAIL"], "La CDC est une grande entreprise agro-industrielle de Fako."),
     ("Which company in the South-West Region grows bananas, rubber and oil palm among other crops?", "The Cameroon Development Corporation (CDC)", ["SOSUCAM", "SODECOTON", "SONARA", "CAMRAIL"], "CDC is a major agro-industrial company.")),
    ("1re", 3, "CM", ("Quelle société d'État est chargée de la production et de la commercialisation du coton au Cameroun ?", "La SODECOTON", ["La SONARA", "La CDC", "La SOSUCAM", "Camtel"], "SODECOTON opère dans le Nord et l'Extrême-Nord."),
     ("Which company is mainly responsible for cotton production and marketing in Cameroon?", "SODECOTON", ["SONARA", "CDC", "SOSUCAM", "Camtel"], "SODECOTON works in the North and Far North.")),
    ("tle", 3, "CM", ("Quel climat caractérise le Sud du Cameroun ?", "Le climat équatorial à quatre saisons", ["Le climat désertique", "Le climat tempéré", "Le climat polaire", "Le climat méditerranéen"], "Il y a deux saisons des pluies et deux saisons sèches."),
     ("Which climate characterises southern Cameroon?", "An equatorial climate with four seasons", ["A desert climate", "A temperate climate", "A polar climate", "A Mediterranean climate"], "There are two rainy and two dry seasons.")),
    ("tle", 3, "CM", ("Quel type de climat trouve-t-on à l'Extrême-Nord du Cameroun ?", "Le climat soudano-sahélien", ["Le climat équatorial", "Le climat de montagne", "Le climat méditerranéen", "Le climat polaire"], "Une longue saison sèche et peu de précipitations."),
     ("Which type of climate is found in the Far North of Cameroon?", "A Sudano-Sahelian climate", ["An equatorial climate", "A mountain climate", "A Mediterranean climate", "A polar climate"], "It has a long dry season and little rain.")),
    ("tle", 3, "AF", ("Comment appelle-t-on la région semi-aride située au sud du Sahara ?", "Le Sahel", ["La savane humide", "Le Kalahari", "Le Karoo", "La forêt équatoriale"], "Le Sahel est une bande de transition entre désert et savane."),
     ("What is the semi-arid region south of the Sahara called?", "The Sahel", ["The humid savanna", "The Kalahari", "The Karoo", "The equatorial forest"], "It is a transition belt between desert and savanna.")),
    ("tle", 3, "AF", ("Comment appelle-t-on le vent sec et poussiéreux qui souffle du Sahara vers le golfe de Guinée en saison sèche ?", "L'harmattan", ["La mousson", "Le sirocco", "Le mistral", "L'alizé maritime"], "L'harmattan souffle surtout de novembre à mars."),
     ("What is the dry dusty wind that blows from the Sahara towards the Gulf of Guinea in the dry season called?", "The harmattan", ["The monsoon", "The sirocco", "The mistral", "The trade wind"], "It blows mostly from November to March.")),
    ("tle", 4, "WORLD", ("Comment appelle-t-on la zone de basses pressions où convergent les alizés près de l'équateur ?", "La zone de convergence intertropicale", ["Le front polaire", "La dorsale anticyclonique", "Le courant-jet", "La zone des calmes tropicaux"], "Elle se déplace avec le soleil et explique les saisons des pluies."),
     ("What is the low-pressure zone where the trade winds meet near the Equator called?", "The Intertropical Convergence Zone", ["The polar front", "The subtropical ridge", "The jet stream", "The horse latitudes"], "It moves with the sun and explains the rainy seasons.")),
    ("tle", 3, "WORLD", ("Comment appelle-t-on le phénomène de réchauffement de l'atmosphère dû aux gaz comme le dioxyde de carbone ?", "L'effet de serre", ["L'effet Doppler", "La couche d'ozone", "L'érosion", "La mousson"], "Les gaz à effet de serre retiennent la chaleur."),
     ("What is the warming of the atmosphere caused by gases such as carbon dioxide called?", "The greenhouse effect", ["The Doppler effect", "The ozone layer", "Erosion", "The monsoon"], "Greenhouse gases trap heat.")),
    ("tle", 3, "WORLD", ("Quelle est la couche de la Terre située entre la croûte et le noyau ?", "Le manteau", ["La lithosphère seulement", "L'atmosphère", "L'hydrosphère", "Le magma superficiel"], "Le manteau est la couche la plus épaisse."),
     ("Which layer of the Earth lies between the crust and the core?", "The mantle", ["The lithosphere only", "The atmosphere", "The hydrosphere", "The surface magma"], "The mantle is the thickest layer.")),
    ("tle", 3, "WORLD", ("Quelles sont les trois grandes familles de roches ?", "Magmatiques, sédimentaires et métamorphiques", ["Calcaires, granites et basaltes", "Argileuses, sableuses et gréseuses", "Volcaniques, plutoniques et fossiles", "Minérales, organiques et fossiles"], "Elles se distinguent par leur origine."),
     ("What are the three main types of rock?", "Igneous, sedimentary and metamorphic", ["Limestone, granite and basalt", "Clay, sand and sandstone", "Volcanic, plutonic and fossil", "Mineral, organic and fossil"], "They are classified by how they form.")),
    ("tle", 4, "WORLD", ("Quel processus déplace lentement les plaques de l'écorce terrestre et explique volcans et séismes ?", "La tectonique des plaques", ["L'érosion fluviale", "La sédimentation", "L'effet de serre", "La mousson"], "Les plaques se rapprochent, s'écartent ou coulissent."),
     ("Which process slowly moves the plates of the Earth's crust and explains volcanoes and earthquakes?", "Plate tectonics", ["River erosion", "Sedimentation", "The greenhouse effect", "The monsoon"], "Plates converge, diverge or slide past each other.")),
    ("tle", 3, "WORLD", ("Comment appelle-t-on le départ de personnes d'un pays vers un autre pour s'y installer ?", "L'émigration vue du pays de départ, l'immigration vue du pays d'arrivée", ["L'exode rural uniquement", "La natalité", "L'urbanisation", "La transition démographique"], "Une même migration est une émigration et une immigration."),
     ("What do we call people leaving one country to settle in another?", "Emigration from the country they leave, immigration into the one they enter", ["Rural exodus only", "Natality", "Urbanisation", "The demographic transition"], "The same movement is both emigration and immigration.")),
    ("tle", 3, "WORLD", ("Comment appelle-t-on le départ massif des habitants des campagnes vers les villes ?", "L'exode rural", ["La transhumance", "Le nomadisme", "La déforestation", "L'immigration"], "Il alimente la croissance des villes."),
     ("What is the large-scale movement of people from the countryside to towns called?", "Rural–urban migration (rural exodus)", ["Transhumance", "Nomadism", "Deforestation", "Immigration"], "It fuels the growth of cities.")),
    ("tle", 4, "WORLD", ("Quels trois critères composent l'indice de développement humain (IDH) ?", "La santé (espérance de vie), l'éducation et le revenu", ["La population, la superficie et le PIB", "Le climat, la démographie et l'industrie", "La natalité, la mortalité et les migrations", "L'agriculture, l'industrie et les services"], "L'IDH est publié par le PNUD."),
     ("Which three dimensions make up the Human Development Index (HDI)?", "Health (life expectancy), education and income", ["Population, area and GDP", "Climate, demography and industry", "Birth, death and migration rates", "Agriculture, industry and services"], "The HDI is published by the UNDP.")),
    ("tle", 3, "AF", ("Combien d'États compte la CEMAC (Communauté économique et monétaire de l'Afrique centrale) ?", "6", ["4", "5", "8", "10"], "Cameroun, Congo, Gabon, Guinée équatoriale, Centrafrique, Tchad."),
     ("How many member states does CEMAC (the Economic and Monetary Community of Central Africa) have?", "6", ["4", "5", "8", "10"], "Cameroon, Congo, Gabon, Equatorial Guinea, the Central African Republic and Chad.")),
    ("tle", 2, "WORLD", ("Comment appelle-t-on les activités liées à l'agriculture, à l'élevage, à la pêche et à l'exploitation forestière ?", "Le secteur primaire", ["Le secteur secondaire", "Le secteur tertiaire", "Le secteur quaternaire", "Le secteur informel"], "Le primaire exploite directement les ressources naturelles."),
     ("What do we call activities such as farming, fishing, forestry and mining?", "The primary sector", ["The secondary sector", "The tertiary sector", "The quaternary sector", "The informal sector"], "The primary sector takes resources directly from nature.")),
    ("tle", 2, "WORLD", ("Dans quel secteur classe-t-on le commerce, les banques et les transports ?", "Le secteur tertiaire", ["Le secteur primaire", "Le secteur secondaire", "Le secteur extractif", "Le secteur informel"], "Le tertiaire regroupe les services."),
     ("In which sector are trade, banking and transport classed?", "The tertiary sector", ["The primary sector", "The secondary sector", "The extractive sector", "The informal sector"], "The tertiary sector covers services.")),
]
for lvl, d, region, fr_row, en_row in B:
    q, r, w, e = fr_row
    fq(lycee.fr(lvl, "geo"), "geo-notions", q, r, w, e, "ly-geo-fr", region, "Géographie", d)
    q, r, w, e = en_row
    fq(lycee.en(LV[lvl], "geo"), "geo-notions", q, r, w, e, "ly-geo-en", region, "Geography", d)
