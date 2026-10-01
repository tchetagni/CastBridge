"""CM2 et 3e : sciences, histoire-géographie, éducation civique, SVT. Faits écrits par l'assistant : tous `review`.
Programmes de référence : MINEDUB (primaire) et MINESEC (secondaire) ; à relire par un enseignant."""
from ..facts_engine import fq, pairs, pick, source

W, CM_ = "WORLD", "CM"
source("p-sci", "Sciences d'observation, CM2 (corps humain, plantes, matière, énergie)", "programme scolaire", "Comparer avec le programme officiel du CM2 (MINEDUB) et le manuel agréé de sciences.")
source("p-hg", "Histoire-géographie et éducation civique, CM2 (Cameroun)", "programme scolaire", "Comparer avec le programme officiel du CM2 et les atlas scolaires du Cameroun.")
source("s3-svt", "SVT et sciences, 3e", "programme scolaire", "Comparer avec le programme MINESEC de 3e et les manuels agréés.")
source("s3-hg", "Histoire, géographie et éducation civique, 3e", "programme scolaire", "Comparer avec le programme MINESEC de 3e et les manuels agréés.")
source("s3-fr", "Français et anglais, 3e", "programme scolaire", "Comparer avec le programme MINESEC de 3e (grammaire, figures de style, anglais).")
source("t-svt", "SVT et chimie, Terminale", "programme scolaire", "Comparer avec le programme MINESEC de Terminale et les manuels agréés.")


def add(course, tpl, rows, src, cat, region=W):
    for q, right, wr, expl, d in rows:
        fq(course, tpl, q, right, wr, expl, src, region, cat, d)


CM2_SCI = [
    ("Quel organe sert à respirer ?", "Les poumons", ["Le cœur", "Le foie", "L'estomac", "Les reins", "Le cerveau"], "Les poumons échangent l'oxygène et le gaz carbonique.", 1),
    ("Quel organe pompe le sang dans tout le corps ?", "Le cœur", ["Les poumons", "Le foie", "Les reins", "L'estomac", "Le cerveau"], "Le cœur est un muscle qui se contracte.", 1),
    ("Quel organe sert à digérer les aliments en premier après l'œsophage ?", "L'estomac", ["Les poumons", "Le cœur", "Les reins", "Le cerveau", "La rate"], "L'estomac brasse les aliments avec le suc gastrique.", 2),
    ("Quel organe filtre le sang et fabrique l'urine ?", "Les reins", ["Le foie", "L'estomac", "Les poumons", "Le cœur", "La rate"], "Les reins éliminent les déchets du sang.", 3),
    ("Quel organe commande tout le corps ?", "Le cerveau", ["Le cœur", "Le foie", "Les poumons", "Les reins", "L'estomac"], "Le cerveau reçoit les informations et envoie les ordres.", 1),
    ("Combien de sens possède l'être humain (classiquement) ?", "5", ["3", "4", "6", "7", "2"], "La vue, l'ouïe, l'odorat, le goût et le toucher.", 1),
    ("Avec quel organe sent-on les odeurs ?", "Le nez", ["La langue", "L'oreille", "L'œil", "La peau", "La bouche"], "L'odorat est le sens du nez.", 1),
    ("Quel sens utilise-t-on avec la langue ?", "Le goût", ["La vue", "L'ouïe", "L'odorat", "Le toucher", "L'équilibre"], "La langue perçoit les saveurs.", 1),
    ("Quelle maladie est transmise par la piqûre de l'anophèle femelle ?", "Le paludisme", ["La tuberculose", "Le choléra", "La rougeole", "La typhoïde", "La varicelle"], "Le moustique anophèle transmet le parasite du paludisme (malaria).", 1),
    ("Quel moyen protège le mieux contre le paludisme pendant le sommeil ?", "La moustiquaire imprégnée", ["Le thé chaud", "Le ventilateur", "Les lunettes", "Une couverture en laine", "Le sirop"], "La moustiquaire évite les piqûres de moustiques.", 1),
    ("Pourquoi faut-il se laver les mains avant de manger ?", "Pour éliminer les microbes", ["Pour avoir plus faim", "Pour manger plus vite", "Pour sentir bon", "Pour se réchauffer", "Pour mieux voir"], "Les mains sales transmettent des microbes.", 1),
    ("Dans quel état se trouve l'eau d'un glaçon ?", "Solide", ["Liquide", "Gazeux", "Plasma", "Vapeur", "Aucun"], "La glace est l'état solide de l'eau.", 1),
    ("Comment s'appelle le passage de l'eau liquide à la vapeur ?", "L'évaporation (ébullition)", ["La fusion", "La solidification", "La condensation", "La sublimation", "La congélation"], "L'eau chauffée se transforme en vapeur.", 2),
    ("Comment s'appelle le passage de l'eau liquide à la glace ?", "La solidification", ["La fusion", "L'évaporation", "La condensation", "La liquéfaction", "La sublimation"], "L'eau gèle à 0 °C.", 2),
    ("Que fabriquent les plantes vertes grâce à la lumière du soleil ?", "Leur nourriture (photosynthèse)", ["De l'eau", "Du sel", "Du sable", "Du fer", "De l'huile"], "La photosynthèse produit des sucres et de l'oxygène.", 2),
    ("Quelle partie de la plante absorbe l'eau dans le sol ?", "Les racines", ["Les feuilles", "Les fleurs", "Les fruits", "La tige", "Les graines"], "Les racines puisent l'eau et les sels minéraux.", 1),
    ("Quelle partie de la plante donne les fruits après la fécondation ?", "La fleur", ["La racine", "La feuille", "La tige", "L'écorce", "Le bourgeon"], "La fleur se transforme en fruit.", 2),
    ("Quel animal pond des œufs : le poulet, le chien, le chat ou la vache ?", "Le poulet", ["Le chien", "Le chat", "La vache", "Le cheval", "La chèvre"], "Le poulet est un oiseau, donc ovipare.", 1),
    ("Comment appelle-t-on un animal qui mange uniquement des plantes ?", "Un herbivore", ["Un carnivore", "Un omnivore", "Un insectivore", "Un prédateur", "Un parasite"], "La vache et l'éléphant sont des herbivores.", 1),
    ("Comment appelle-t-on un animal qui mange de la viande ?", "Un carnivore", ["Un herbivore", "Un végétarien", "Un frugivore", "Un granivore", "Un omnivore"], "Le lion est un carnivore.", 1),
    ("Quel est le rôle des globules rouges ?", "Transporter l'oxygène", ["Combattre les microbes", "Digérer les aliments", "Fabriquer les os", "Filtrer l'urine", "Produire la sueur"], "Ils contiennent l'hémoglobine qui fixe l'oxygène.", 3),
    ("Quelle vitamine obtient-on en mangeant des agrumes et protège du scorbut ?", "La vitamine C", ["La vitamine A", "La vitamine D", "La vitamine K", "La vitamine B12", "La vitamine E"], "Les oranges et citrons sont riches en vitamine C.", 3),
    ("Quel aliment est riche en protéines ?", "Le poisson", ["Le sucre", "L'huile", "Le sel", "Le miel", "La gomme"], "Viande, poisson, œufs et haricots apportent des protéines.", 2),
    ("De quelle énergie dispose-t-on gratuitement grâce au soleil ?", "L'énergie solaire", ["L'énergie nucléaire", "Le pétrole", "Le charbon", "Le gaz", "L'essence"], "Les panneaux solaires transforment la lumière en électricité.", 2),
    ("Quel appareil mesure la température ?", "Le thermomètre", ["Le baromètre", "La balance", "Le chronomètre", "La règle", "Le compas"], "Le thermomètre indique la température en degrés.", 1),
    ("Quel appareil mesure la masse ?", "La balance", ["Le thermomètre", "Le chronomètre", "La règle", "Le compas", "Le baromètre"], "La balance mesure la masse en kilogrammes.", 1),
    ("Quelle est l'unité de masse la plus courante ?", "Le kilogramme", ["Le litre", "Le mètre", "Le watt", "Le degré", "La seconde"], "Le kilogramme (kg) est l'unité de masse.", 1),
    ("Qu'est-ce qui attire les objets vers le sol ?", "La pesanteur (gravité)", ["Le vent", "La chaleur", "Le magnétisme", "La lumière", "Le bruit"], "La Terre attire tous les objets.", 2),
    ("Comment appelle-t-on la saison où il pleut beaucoup dans le Sud du Cameroun ?", "La saison des pluies", ["La saison sèche", "L'harmattan", "L'été", "L'hiver", "La mousson"], "Le Sud du Cameroun connaît deux saisons des pluies.", 2),
    ("Quel vent sec et poussiéreux souffle en saison sèche dans le Nord du Cameroun ?", "L'harmattan", ["La mousson", "Le sirocco", "Le mistral", "Le blizzard", "L'alizé marin"], "L'harmattan vient du Sahara.", 3),
    ("Quelle est la source principale de lumière et de chaleur sur Terre ?", "Le Soleil", ["La Lune", "Les étoiles", "Le feu", "L'électricité", "Les nuages"], "Le Soleil est l'étoile la plus proche.", 1),
    ("Combien de temps la Terre met-elle pour tourner sur elle-même ?", "24 heures", ["12 heures", "1 semaine", "30 jours", "365 jours", "60 minutes"], "Un jour dure environ 24 h.", 1),
    ("Qu'est-ce qui provoque les marées ?", "L'attraction de la Lune", ["Le vent", "Les poissons", "La pluie", "Les volcans", "Le soleil seul"], "La Lune (et le Soleil) attire l'eau des océans.", 3),
]
add("cm2", "cm2-sci", CM2_SCI, "p-sci", "Sciences")

CM2_HG = [
    ("Quelle est la capitale politique du Cameroun ?", "Yaoundé", ["Douala", "Garoua", "Bafoussam", "Bamenda", "Buea"], "Yaoundé est la capitale.", 1),
    ("Quelle est la capitale économique du Cameroun ?", "Douala", ["Yaoundé", "Garoua", "Bafoussam", "Kribi", "Limbé"], "Douala est la capitale économique.", 1),
    ("Combien de régions compte le Cameroun ?", "10", ["8", "9", "12", "11", "7"], "Le Cameroun compte dix régions.", 1),
    ("Quel est le chef-lieu de la région de l'Ouest ?", "Bafoussam", ["Bamenda", "Dschang", "Foumban", "Bafang", "Mbouda"], "Bafoussam est le chef-lieu de l'Ouest.", 1),
    ("Quel est le chef-lieu de la région du Littoral ?", "Douala", ["Yaoundé", "Édéa", "Limbé", "Kribi", "Nkongsamba"], "Douala est le chef-lieu du Littoral.", 1),
    ("Quel est le chef-lieu de la région de l'Extrême-Nord ?", "Maroua", ["Garoua", "Ngaoundéré", "Kousséri", "Yagoua", "Mokolo"], "Maroua est le chef-lieu de l'Extrême-Nord.", 1),
    ("Quel est le chef-lieu de la région du Nord ?", "Garoua", ["Maroua", "Ngaoundéré", "Bertoua", "Poli", "Guider"], "Garoua est le chef-lieu du Nord.", 1),
    ("Quel est le chef-lieu de la région de l'Adamaoua ?", "Ngaoundéré", ["Garoua", "Maroua", "Meiganga", "Tibati", "Banyo"], "Ngaoundéré est le chef-lieu de l'Adamaoua.", 1),
    ("Quel est le chef-lieu de la région de l'Est ?", "Bertoua", ["Yokadouma", "Batouri", "Abong-Mbang", "Garoua-Boulaï", "Ngaoundéré"], "Bertoua est le chef-lieu de l'Est.", 1),
    ("Quel est le chef-lieu de la région du Sud ?", "Ebolowa", ["Kribi", "Sangmélima", "Ambam", "Mbalmayo", "Yaoundé"], "Ebolowa est le chef-lieu du Sud.", 1),
    ("Quel est le chef-lieu de la région du Sud-Ouest ?", "Buea", ["Limbé", "Kumba", "Mamfe", "Tiko", "Mundemba"], "Buea est le chef-lieu du Sud-Ouest.", 1),
    ("Quel est le chef-lieu de la région du Nord-Ouest ?", "Bamenda", ["Kumbo", "Wum", "Nkambé", "Ndop", "Bafut"], "Bamenda est le chef-lieu du Nord-Ouest.", 1),
    ("Quel est le chef-lieu de la région du Centre ?", "Yaoundé", ["Douala", "Bafia", "Mbalmayo", "Obala", "Nanga-Eboko"], "Yaoundé est aussi le chef-lieu de la région du Centre.", 1),
    ("Quelles sont les couleurs du drapeau du Cameroun ?", "Vert, rouge, jaune", ["Rouge, blanc, bleu", "Vert, blanc, rouge", "Noir, rouge, or", "Bleu, jaune, rouge", "Vert, jaune, noir"], "Trois bandes verticales avec une étoile jaune au centre.", 1),
    ("Quelle est la devise du Cameroun ?", "Paix - Travail - Patrie", ["Liberté - Égalité - Fraternité", "Unité - Travail - Progrès", "Dieu - Patrie - Roi", "Union - Discipline - Travail", "Fraternité - Justice - Paix"], "Paix, Travail, Patrie.", 1),
    ("Quel jour célèbre-t-on la fête nationale du Cameroun ?", "Le 20 mai", ["Le 1er janvier", "Le 11 février", "Le 1er mai", "Le 15 août", "Le 25 décembre"], "La fête de l'Unité nationale est le 20 mai.", 1),
    ("Quel jour célèbre-t-on la fête de la Jeunesse au Cameroun ?", "Le 11 février", ["Le 20 mai", "Le 1er janvier", "Le 8 mars", "Le 1er mai", "Le 15 août"], "Le 11 février.", 1),
    ("Quel jour célèbre-t-on la fête du Travail ?", "Le 1er mai", ["Le 20 mai", "Le 11 février", "Le 8 mars", "Le 15 août", "Le 1er janvier"], "Le 1er mai.", 1),
    ("Quelle journée célèbre-t-on le 8 mars ?", "La Journée internationale de la femme", ["La fête du Travail", "La fête de la Jeunesse", "La fête nationale", "La fête des Mères", "La Journée de l'enfant africain"], "Le 8 mars.", 1),
    ("Quel océan borde le Cameroun ?", "L'océan Atlantique", ["L'océan Indien", "L'océan Pacifique", "L'océan Arctique", "La mer Rouge", "La Méditerranée"], "Le littoral camerounais est sur l'Atlantique.", 1),
    ("Quel est le plus haut sommet du Cameroun ?", "Le mont Cameroun", ["Le mont Kenya", "Le Kilimandjaro", "Le mont Oku", "Le mont Manengouba", "Le Mont-Blanc"], "Le mont Cameroun est un volcan.", 1),
    ("Quel fleuve traverse Douala ?", "Le Wouri", ["La Sanaga", "Le Nyong", "La Bénoué", "Le Logone", "Le Ntem"], "Le Wouri forme l'estuaire de Douala.", 2),
    ("Quel est le plus grand lac partagé par le Cameroun, le Tchad, le Niger et le Nigeria ?", "Le lac Tchad", ["Le lac Nyos", "Le lac Victoria", "Le lac Bamendjing", "Le lac Tanganyika", "Le lac Lagdo"], "Le lac Tchad.", 2),
    ("Quel est le pays voisin du Cameroun situé à l'ouest ?", "Le Nigeria", ["Le Tchad", "Le Gabon", "Le Congo", "La Centrafrique", "Le Niger"], "Le Nigeria.", 1),
    ("Quelle culture est typique du Nord du Cameroun ?", "Le coton", ["Le cacao", "Le café arabica", "La banane plantain", "L'hévéa", "Le palmier à huile"], "Le coton est cultivé autour de Garoua.", 2),
    ("Quelle culture est typique des régions du Centre et du Sud ?", "Le cacao", ["Le coton", "Le sorgho", "Le mil", "L'arachide", "Le blé"], "Le cacao est cultivé dans le Centre, le Sud et le Sud-Ouest.", 2),
    ("Quel est le moyen de transport le plus utilisé pour traverser un fleuve en pirogue ?", "La pirogue", ["Le train", "Le bus", "L'avion", "Le vélo", "Le camion"], "Les pirogues traversent fleuves et lacs.", 1),
    ("Quelle est la monnaie utilisée au Cameroun ?", "Le franc CFA", ["Le naira", "Le dollar", "L'euro", "Le cédi", "Le shilling"], "Le franc CFA (XAF).", 1),
    ("Qui fut le premier président du Cameroun ?", "Ahmadou Ahidjo", ["Paul Biya", "John Ngu Foncha", "Ruben Um Nyobè", "André-Marie Mbida", "Charles Atangana"], "Ahmadou Ahidjo (1960-1982).", 2),
    ("En quelle année le Cameroun français est-il devenu indépendant ?", "1960", ["1957", "1961", "1958", "1972", "1945"], "Le 1er janvier 1960.", 2),
    ("Quelle est la langue officielle parlée dans la majorité du pays ?", "Le français", ["L'anglais", "Le fulfulde", "Le pidgin", "L'ewondo", "Le douala"], "Le français et l'anglais sont officiels ; le français est le plus parlé.", 1),
    ("À quoi sert le vote lors d'une élection ?", "À choisir ses représentants", ["À payer des impôts", "À partir en voyage", "À acheter un journal", "À créer une loi seul", "À obtenir un diplôme"], "Les électeurs choisissent leurs dirigeants.", 2),
    ("Qui fait respecter la loi et arrête les délinquants ?", "La police et la gendarmerie", ["Les enseignants", "Les infirmiers", "Les agriculteurs", "Les commerçants", "Les artistes"], "Forces de maintien de l'ordre.", 1),
    ("Que doit-on faire à un feu rouge de signalisation ?", "S'arrêter", ["Passer vite", "Klaxonner", "Ralentir seulement", "Tourner à gauche", "Faire demi-tour"], "Rouge = arrêt.", 1),
    ("Quel droit l'enfant a-t-il d'après la Convention internationale des droits de l'enfant ?", "Le droit à l'éducation", ["Le droit de conduire", "Le droit de voter à 10 ans", "Le droit de travailler la nuit", "Le droit de se marier", "Le droit de porter une arme"], "Tout enfant a droit à l'éducation, à la santé, à un nom et à une famille.", 2),
]
add("cm2", "cm2-hg", CM2_HG, "p-hg", "Histoire-géographie et civisme", CM_)

S3_SVT = [
    ("Quel est le rôle de la mitose ?", "Produire deux cellules identiques à la cellule mère", ["Produire des gamètes", "Digérer les aliments", "Fabriquer de la chlorophylle", "Transporter l'oxygène", "Détruire les microbes"], "La mitose permet la croissance et le renouvellement des cellules.", 3),
    ("Combien de chromosomes possède une cellule humaine ordinaire ?", "46", ["23", "44", "48", "92", "21"], "Les cellules humaines ont 23 paires de chromosomes.", 3),
    ("Quelle molécule porte l'information génétique ?", "L'ADN", ["L'ARN seul", "Les protéines", "Les lipides", "L'amidon", "L'insuline"], "L'ADN est présent dans le noyau des cellules.", 3),
    ("Quel gamète mâle féconde l'ovule ?", "Le spermatozoïde", ["Le globule blanc", "La plaquette", "Le neurone", "L'hormone", "La cellule musculaire"], "La fécondation unit un spermatozoïde et un ovule.", 2),
    ("Quelle maladie est causée par le virus VIH ?", "Le sida", ["Le paludisme", "La tuberculose", "Le choléra", "La rougeole", "La typhoïde"], "Le VIH affaiblit le système immunitaire.", 2),
    ("Comment prévient-on le mieux la transmission du VIH par voie sexuelle ?", "En utilisant un préservatif", ["En se lavant les mains", "En buvant de l'eau bouillie", "En dormant sous moustiquaire", "En évitant les moustiques", "En prenant de la vitamine C"], "Le préservatif protège du VIH et d'autres IST.", 2),
    ("Quel est l'agent du paludisme ?", "Un parasite (Plasmodium)", ["Un virus", "Une bactérie", "Un champignon", "Un ver", "Une toxine"], "Le Plasmodium est transmis par l'anophèle.", 3),
    ("Qu'est-ce qu'un vaccin ?", "Une préparation qui stimule les défenses de l'organisme", ["Un médicament contre la douleur", "Une vitamine", "Un antibiotique", "Un aliment", "Un désinfectant"], "Le vaccin prépare l'organisme à reconnaître un microbe.", 2),
    ("Que contient un antibiotique ?", "Une substance qui détruit ou arrête les bactéries", ["Une substance qui détruit les virus", "De la vitamine C", "Du sucre", "Des protéines", "De l'oxygène"], "Les antibiotiques ne sont pas efficaces contre les virus.", 3),
    ("Quel organe produit l'insuline ?", "Le pancréas", ["Le foie", "Le rein", "L'estomac", "La rate", "La thyroïde"], "L'insuline régule la glycémie.", 3),
    ("Quelle maladie est liée à un manque d'insuline ?", "Le diabète", ["L'anémie", "L'asthme", "L'hypertension", "Le goitre", "La cataracte"], "Le diabète est un excès de sucre dans le sang.", 3),
    ("Quel est le rôle des globules blancs ?", "Défendre l'organisme contre les microbes", ["Transporter l'oxygène", "Coaguler le sang", "Digérer les graisses", "Produire de l'énergie", "Fabriquer les os"], "Ils font partie du système immunitaire.", 2),
    ("Comment s'appelle le processus par lequel les plantes fabriquent leur matière organique ?", "La photosynthèse", ["La respiration", "La digestion", "La fermentation", "La transpiration", "La germination"], "La plante utilise lumière, eau et CO₂.", 2),
    ("Que rejettent les plantes pendant la photosynthèse ?", "Du dioxygène", ["Du dioxyde de carbone", "De l'azote", "De l'hydrogène", "Du méthane", "De l'hélium"], "La photosynthèse libère de l'oxygène.", 2),
    ("Quelle est la première étape de la digestion mécanique ?", "La mastication dans la bouche", ["L'absorption dans l'intestin", "Le brassage dans l'estomac", "L'élimination des selles", "La filtration par les reins", "La respiration"], "Les dents broient les aliments.", 2),
    ("Où se fait l'essentiel de l'absorption des nutriments ?", "Dans l'intestin grêle", ["Dans l'estomac", "Dans l'œsophage", "Dans le gros intestin", "Dans la bouche", "Dans le foie"], "L'intestin grêle absorbe les nutriments dans le sang.", 3),
    ("Qu'appelle-t-on un écosystème ?", "Un milieu et les êtres vivants qui y vivent", ["Un type de roche", "Un appareil scientifique", "Une maladie", "Un pays", "Un sol fertile"], "Biotope + biocénose.", 3),
    ("Comment appelle-t-on un être vivant qui fabrique sa propre matière organique ?", "Un producteur", ["Un consommateur", "Un décomposeur", "Un parasite", "Un prédateur", "Un herbivore"], "Les plantes vertes sont les producteurs d'une chaîne alimentaire.", 3),
    ("Quel est le rôle des décomposeurs (champignons, bactéries) ?", "Transformer la matière morte en matière minérale", ["Produire de l'oxygène", "Chasser les herbivores", "Fabriquer de l'énergie solaire", "Polliniser les fleurs", "Filtrer l'eau"], "Ils recyclent la matière.", 3),
    ("Quel gaz est principalement responsable du réchauffement climatique ?", "Le dioxyde de carbone", ["L'oxygène", "L'azote", "L'hélium", "L'argon", "Le néon"], "Le CO₂ renforce l'effet de serre.", 2),
    ("Qu'est-ce que la déforestation ?", "La destruction des forêts", ["La plantation d'arbres", "La culture du riz", "L'élevage des bovins", "La pêche en mer", "L'irrigation"], "Elle réduit la biodiversité et libère du CO₂.", 1),
]
add("3e", "3e-svt", S3_SVT, "s3-svt", "SVT")

S3_HG = [
    ("En quelle année la Première Guerre mondiale a-t-elle commencé ?", "1914", ["1905", "1912", "1918", "1939", "1945"], "1914-1918.", 2),
    ("En quelle année la Seconde Guerre mondiale s'est-elle terminée ?", "1945", ["1939", "1942", "1944", "1950", "1918"], "La Seconde Guerre mondiale s'est terminée en 1945.", 2),
    ("Quel traité de 1919 a mis fin à la Première Guerre mondiale avec l'Allemagne ?", "Le traité de Versailles", ["Le traité de Rome", "Le traité de Paris", "Le traité de Vienne", "Le traité de Maastricht", "La conférence de Berlin"], "Le traité de Versailles (28 juin 1919).", 3),
    ("Quelle conférence de 1884-1885 a organisé le partage de l'Afrique entre les puissances européennes ?", "La conférence de Berlin", ["La conférence de Yalta", "La conférence de Bandung", "La conférence de Brazzaville", "La conférence de Vienne", "La conférence de Paris"], "La conférence de Berlin a fixé les règles de la colonisation.", 3),
    ("Quelle conférence de 1944 en Afrique centrale a annoncé des réformes de l'empire colonial français ?", "La conférence de Brazzaville", ["La conférence de Berlin", "La conférence de Yalta", "La conférence de Bandung", "La conférence de Dakar", "La conférence de Paris"], "Organisée par le général de Gaulle en janvier-février 1944.", 4),
    ("Quelle organisation internationale a été créée en 1945 pour maintenir la paix ?", "L'ONU", ["La SDN", "L'OTAN", "L'OUA", "L'UE", "L'UNESCO"], "Organisation des Nations unies.", 2),
    ("Quelle guerre a opposé les États-Unis et l'URSS sans affrontement direct (1947-1991) ?", "La guerre froide", ["La guerre de Cent Ans", "La guerre de Sécession", "La guerre du Golfe", "La guerre des Six Jours", "La guerre de Corée"], "Deux blocs : Est (URSS) et Ouest (États-Unis).", 3),
    ("Quelle organisation africaine a été créée en 1963 à Addis-Abeba ?", "L'OUA", ["L'UA", "La CEDEAO", "La CEMAC", "L'ONU", "Le COMESA"], "L'Organisation de l'unité africaine, devenue l'Union africaine en 2002.", 3),
    ("Qui a dirigé la lutte contre l'apartheid et fut président de l'Afrique du Sud (1994-1999) ?", "Nelson Mandela", ["Thabo Mbeki", "Desmond Tutu", "Steve Biko", "Jacob Zuma", "Oliver Tambo"], "Mandela est libéré en 1990.", 2),
    ("Quel continent compte le plus d'habitants ?", "L'Asie", ["L'Afrique", "L'Europe", "L'Amérique du Nord", "L'Amérique du Sud", "L'Océanie"], "L'Asie rassemble environ 60 % de la population mondiale.", 1),
    ("Quel pays est le plus peuplé d'Afrique ?", "Le Nigeria", ["L'Éthiopie", "L'Égypte", "L'Afrique du Sud", "La RDC", "Le Cameroun"], "Le Nigeria compte plus de 200 millions d'habitants.", 2),
    ("Quelle est la devise de la République française ?", "Liberté, Égalité, Fraternité", ["Paix, Travail, Patrie", "Unité, Travail, Progrès", "Dieu et mon droit", "Union, Discipline, Travail", "In God we trust"], "La devise date de la Révolution et de la IIIe République.", 1),
    ("Quelle est la capitale des États-Unis d'Amérique ?", "Washington", ["New York", "Los Angeles", "Chicago", "Miami", "Boston"], "Washington D.C.", 1),
    ("Quel est le plus grand pays du monde par la superficie ?", "La Russie", ["Le Canada", "La Chine", "Les États-Unis", "Le Brésil", "L'Inde"], "Plus de 17 millions de km².", 1),
    ("Quelle zone climatique domine au Cameroun, au Gabon et au Congo ?", "Le climat équatorial", ["Le climat polaire", "Le climat désertique", "Le climat méditerranéen", "Le climat tempéré", "Le climat montagnard"], "Chaud et humide toute l'année.", 2),
    ("Que signifie « décentralisation » ?", "Le transfert de pouvoirs de l'État vers les collectivités locales", ["La concentration des pouvoirs dans la capitale", "La suppression des communes", "La création d'une armée régionale", "La fusion de deux pays", "L'indépendance d'une région"], "Régions et communes reçoivent des compétences.", 3),
    ("Qui élit les députés au Cameroun ?", "Les citoyens, par le vote", ["Le président seul", "Les chefs traditionnels", "L'armée", "Le Sénat", "La Cour suprême"], "Les députés sont élus au suffrage universel direct.", 2),
    ("Quelle institution vote les lois au Cameroun ?", "Le Parlement", ["La Cour suprême", "La police", "Les maires", "L'armée", "Les préfets"], "Assemblée nationale et Sénat.", 2),
    ("Que garantit la Déclaration universelle des droits de l'homme de 1948 ?", "Les droits fondamentaux de chaque personne", ["Le monopole des États", "L'obligation du service militaire", "La fin des frontières", "Le droit de ne pas payer d'impôts", "L'égalité des revenus"], "Adoptée par l'ONU le 10 décembre 1948.", 3),
]
add("3e", "3e-hg", S3_HG, "s3-hg", "Histoire-géographie et civisme", CM_)

S3_FR = [
    ("Quelle figure de style compare deux éléments à l'aide d'un outil de comparaison (comme, tel, ainsi que) ?", "La comparaison", ["La métaphore", "L'hyperbole", "La personnification", "L'antithèse", "L'allégorie"], "« Rapide comme l'éclair » est une comparaison.", 2),
    ("Quelle figure de style identifie deux éléments sans outil de comparaison ?", "La métaphore", ["La comparaison", "L'hyperbole", "L'euphémisme", "La litote", "L'ellipse"], "« Cet homme est un lion » est une métaphore.", 2),
    ("Quelle figure de style consiste à exagérer ?", "L'hyperbole", ["La litote", "L'euphémisme", "L'antithèse", "La métonymie", "L'ironie"], "« Je meurs de faim » est une hyperbole.", 3),
    ("Quelle figure de style donne des traits humains à une chose ou un animal ?", "La personnification", ["La comparaison", "L'hyperbole", "La litote", "L'antithèse", "La répétition"], "« La nuit avance sur la pointe des pieds. »", 3),
    ("Comment appelle-t-on l'opposition de deux idées contraires dans une même phrase ?", "L'antithèse", ["La métaphore", "La litote", "L'euphémisme", "L'anaphore", "L'ellipse"], "« Il est petit mais grand par le cœur. »", 3),
    ("Quel est le temps du verbe dans « Hier, nous avons joué au football » ?", "Le passé composé", ["Le présent", "L'imparfait", "Le futur simple", "Le plus-que-parfait", "Le conditionnel présent"], "Auxiliaire avoir au présent + participe passé.", 2),
    ("Quel est le mode du verbe dans « Viens ici ! » ?", "L'impératif", ["L'indicatif", "Le subjonctif", "Le conditionnel", "L'infinitif", "Le participe"], "L'impératif exprime un ordre.", 2),
    ("Quelle est la fonction du mot souligné dans « Le maître *lit* un livre » ?", "Verbe", ["Sujet", "Complément d'objet direct", "Complément de lieu", "Attribut du sujet", "Adjectif"], "« lit » est le verbe conjugué.", 2),
    ("Dans « Awa mange une mangue », quel est le complément d'objet direct ?", "une mangue", ["Awa", "mange", "mangue seulement", "Awa mange", "une"], "Il répond à la question « mange quoi ? ».", 3),
    ("Comment dit-on « Bonjour » en anglais ?", "Hello", ["Goodbye", "Please", "Thank you", "Sorry", "Welcome"], "« Hello » ou « Good morning ».", 1),
    ("Quel est le pluriel de « child » en anglais ?", "children", ["childs", "childes", "childrens", "childen", "childs'"], "Pluriel irrégulier.", 2),
    ("Quelle est la forme du verbe « to be » à la 3e personne du singulier au présent ?", "is", ["am", "are", "be", "was", "been"], "He / she / it is.", 1),
    ("Quel est le passé simple (past) du verbe anglais « go » ?", "went", ["goed", "gone", "going", "goes", "gone to"], "Go - went - gone.", 2),
    ("Que signifie « Thank you » en français ?", "Merci", ["S'il vous plaît", "Pardon", "Bonjour", "Au revoir", "De rien"], "Thank you = merci.", 1),
    ("Quel mot anglais signifie « école » ?", "School", ["Scool", "Skul", "Scholl", "Schule", "Escuela"], "« School » signifie école en anglais.", 1),
    ("Quel est le contraire de « strong » en anglais ?", "weak", ["tall", "big", "slow", "cold", "fast"], "Strong ≠ weak.", 2),
]
add("3e", "3e-fr", S3_FR, "s3-fr", "Français et anglais")

T_SVT = [
    ("Quel est le rôle de l'ATP dans la cellule ?", "Fournir l'énergie utilisable", ["Porter l'information génétique", "Transporter l'oxygène", "Digérer les protéines", "Fabriquer les membranes", "Produire de la chlorophylle"], "L'ATP est la molécule énergétique de la cellule.", 3),
    ("Où se déroule la respiration cellulaire chez les cellules eucaryotes ?", "Dans les mitochondries", ["Dans le noyau", "Dans les chloroplastes", "Dans le réticulum", "Dans la membrane", "Dans les ribosomes"], "Les mitochondries produisent l'ATP.", 4),
    ("Où se déroule la photosynthèse dans la cellule végétale ?", "Dans les chloroplastes", ["Dans les mitochondries", "Dans le noyau", "Dans les ribosomes", "Dans la paroi", "Dans le cytoplasme seul"], "Les chloroplastes contiennent la chlorophylle.", 3),
    ("Quelle est l'unité fonctionnelle du système nerveux ?", "Le neurone", ["Le néphron", "L'alvéole", "L'hématie", "Le sarcomère", "La synapse seule"], "Le neurone transmet l'influx nerveux.", 3),
    ("Comment s'appelle la zone de jonction entre deux neurones ?", "La synapse", ["L'axone", "Le dendrite", "Le noyau", "Le sarcomère", "Le ganglion"], "La synapse transmet le message par des neurotransmetteurs.", 3),
    ("Quelle est l'unité fonctionnelle du rein ?", "Le néphron", ["Le neurone", "L'alvéole", "Le glomérule seul", "La papille", "Le calice"], "Le néphron filtre le sang.", 4),
    ("Quelle loi de Mendel exprime que les hybrides de première génération (F1) sont tous identiques ?", "La loi d'uniformité des hybrides", ["La loi de ségrégation", "La loi d'indépendance", "La loi de Hardy-Weinberg", "La loi de dominance incomplète", "La loi de Lamarck"], "Première loi de Mendel.", 4),
    ("Que signifie être homozygote pour un caractère dominant A ?", "Posséder deux allèles dominants (AA)", ["Posséder un allèle dominant et un récessif (Aa)", "Posséder deux allèles récessifs (aa)", "Posséder un seul allèle du gène", "Posséder trois allèles du gène", "Ne posséder aucun allèle du gène"], "Les deux allèles du gène sont identiques et dominants.", 3),
    ("Quel est le rôle de l'ARN messager ?", "Transporter l'information de l'ADN vers les ribosomes", ["Stocker l'information génétique définitive", "Fournir l'énergie", "Fabriquer les lipides", "Transporter l'oxygène", "Détruire les virus"], "L'ARNm est copié à partir de l'ADN (transcription).", 4),
    ("Quelle est l'étape de fabrication des protéines à partir de l'ARNm ?", "La traduction", ["La transcription", "La réplication", "La mitose", "La méiose", "La fécondation"], "Se déroule dans les ribosomes.", 4),
    ("Quelle division cellulaire produit les gamètes ?", "La méiose", ["La mitose", "La fécondation", "La cytodiérèse seule", "La bipartition", "La réplication"], "La méiose divise par deux le nombre de chromosomes.", 3),
    ("Quelle hormone est produite par l'hypophyse et stimule la croissance ?", "L'hormone de croissance (GH)", ["L'insuline", "L'adrénaline", "Le cortisol", "La thyroxine", "Le glucagon"], "L'hypophyse commande plusieurs glandes.", 4),
    ("Quel est le produit de la fermentation alcoolique de la levure ?", "L'éthanol et le dioxyde de carbone", ["L'acide lactique", "L'eau seulement", "Le glucose", "L'oxygène", "L'acide acétique"], "La levure transforme le glucose en alcool et en CO₂.", 4),
    ("Qu'est-ce que le pH d'une solution neutre à 25 °C ?", "7", ["0", "1", "10", "14", "5"], "pH 7 = neutre ; < 7 acide ; > 7 basique.", 2),
    ("Quelle est la formule de l'ion hydroxyde présent dans les bases ?", "OH⁻", ["H⁺", "H₃O⁺", "Cl⁻", "Na⁺", "NH₄⁺"], "Les bases libèrent des ions OH⁻.", 3),
    ("Quel est le produit d'une réaction entre un acide et une base ?", "Un sel et de l'eau", ["Un gaz seulement", "Un métal", "Un alcool", "Un acide plus fort", "De l'huile"], "Neutralisation : acide + base → sel + eau.", 3),
    ("Quelle est la formule de l'ammoniac ?", "NH₃", ["NH₄", "N₂H₄", "NO₂", "HNO₃", "N₂O"], "L'ammoniac est composé d'azote et d'hydrogène.", 3),
    ("Quel est le nom du groupe fonctionnel -OH en chimie organique ?", "Hydroxyle (alcool)", ["Carboxyle", "Amine", "Cétone", "Aldéhyde", "Éther"], "Les alcools possèdent un groupe -OH.", 4),
    ("Quelle est la formule brute de l'éthanol ?", "C₂H₆O", ["CH₄O", "C₂H₄O₂", "C₃H₈O", "C₂H₂", "C₆H₁₂O₆"], "L'éthanol : CH₃CH₂OH.", 4),
]
add("tle", "tle-svt", T_SVT, "t-svt", "SVT et chimie")
