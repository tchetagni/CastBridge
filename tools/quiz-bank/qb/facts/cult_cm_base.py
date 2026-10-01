"""Cameroun : questions de base pour familles et écoliers (faits sûrs, niveau facile à moyen).
Rédigé à la main par l'assistant : tout sort en `review`. Faits choisis pour compléter les autres modules cm_* / cult_cm_*."""
from .cult_kit import rows, table, theme

theme("cm-base", "Cameroun : questions de base (familles et écoliers)",
      "Comparer avec un atlas du Cameroun, le manuel de géographie et d'éducation civique du programme MINEDUB/MINESEC, une encyclopédie (Larousse, Britannica), "
      "les sites des fédérations sportives et des médias cités ; ne garder que des faits stables ou datés.")

# ------------------------------------------------------------------------------------------------ voisins et repères
rows("cm-base", "CM", "Voisins du Cameroun", [
    (4, "Lequel de ces voisins du Cameroun a la plus grande superficie ?", "Le Tchad",
     ["Le Nigeria", "La République centrafricaine", "Le Congo", "Le Gabon"], "Le Tchad dépasse 1,2 million de km², plus que le Nigeria, la Centrafrique, le Congo ou le Gabon."),
    (3, "Quel est le plus petit pays voisin du Cameroun par la superficie ?", "La Guinée équatoriale",
     ["Le Gabon", "Le Congo", "La République centrafricaine", "Le Tchad"], "La Guinée équatoriale ne couvre qu'environ 28 000 km², dont l'île de Bioko."),
    (2, "Quelle langue officielle est commune au Nigeria et à la partie anglophone du Cameroun ?", "L'anglais",
     ["Le portugais", "L'arabe", "L'espagnol", "L'allemand"], "Le Nigeria est un ancien territoire britannique, comme le Southern Cameroons."),
    (3, "Environ combien de kilomètres séparent Douala de Yaoundé par la route ?", "Environ 250 km",
     ["Environ 50 km", "Environ 100 km", "Environ 500 km", "Environ 800 km"], "La route nationale 3 relie les deux grandes villes en quelques heures de voiture ou de train."),
])

table("cm-base", "CM", "Voisins du Cameroun", [
    ("Calabar", "Le Nigeria", 4), ("Maiduguri", "Le Nigeria", 4), ("Moundou", "Le Tchad", 4), ("Abéché", "Le Tchad", 4), ("Sarh", "Le Tchad", 4),
    ("Bouar", "La République centrafricaine", 4), ("Berbérati", "La République centrafricaine", 4), ("Oyem", "Le Gabon", 4), ("Bitam", "Le Gabon", 5),
    ("Ebebiyín", "La Guinée équatoriale", 5), ("Ouesso", "Le Congo", 4), ("Impfondo", "Le Congo", 5),
], "Dans quel pays voisin du Cameroun se trouve la ville suivante : {a} ?", None, diff=4, expl="{a} est une ville de ce pays voisin du Cameroun : {b}.")

# ------------------------------------------------------------------------------------------------ nature et lieux
rows("cm-base", "CM", "Nature et lieux", [
    (2, "Pourquoi le sable de certaines plages de Limbé est-il noir ?", "Il provient de roches volcaniques du mont Cameroun",
     ["Il a été brûlé par le soleil", "Il vient du désert du Nord", "Il est mélangé à du charbon", "Il est coloré par les algues"], "Les coulées de lave du mont Cameroun, volcan voisin, se sont émiettées en sable sombre."),
    (4, "Quel grand lac artificiel de l'Extrême-Nord, dans le Mayo-Danay, sert surtout à irriguer des rizières ?", "Le lac de Maga",
     ["Le lac Nyos", "Le lac Ossa", "Le lac Mbakaou", "Le lac Barombi Mbo"], "Le lac de retenue de Maga, dans la plaine du Logone, alimente les rizières de la région."),
    (3, "Quelle pierre précieuse est extraite de façon artisanale dans l'Est du Cameroun ?", "Le diamant",
     ["L'émeraude", "Le rubis", "L'opale", "La turquoise"], "Des diamants alluviaux sont recherchés dans l'Est, dans la région de l'Est."),
])

# ------------------------------------------------------------------------------------------------ fêtes et vie sociale
rows("cm-base", "CM", "Fêtes et vie sociale", [
    (2, "Que portent traditionnellement de nombreuses femmes le 8 mars au Cameroun ?", "Le pagne imprimé de la Journée de la femme",
     ["Un costume gris", "Un uniforme militaire", "Une blouse blanche", "Un habit de pluie"], "Chaque année, un tissu imprimé aux couleurs de la fête est vendu pour la Journée internationale des droits des femmes."),
    (3, "Dans quelle ville se déroule traditionnellement le grand défilé de la fête nationale du 20 mai ?", "Yaoundé",
     ["Douala", "Garoua", "Bamenda", "Bafoussam"], "Le défilé officiel passe sur le boulevard du 20-Mai, dans la capitale politique."),
    (2, "Quelle fête musulmane, marquée par le sacrifice d'un mouton, est un jour férié au Cameroun ?", "L'Aïd el-Kébir (fête du Mouton)",
     ["L'Aïd el-Fitr", "Le Nouvel An chrétien", "La Pentecôte", "Le Nouvel An chinois"], "Lors de cette fête, les familles partagent la viande avec leurs proches et leurs voisins."),
    (2, "Quelle fête musulmane marque la fin du jeûne du ramadan ?", "L'Aïd el-Fitr",
     ["L'Aïd el-Kébir", "L'Assomption", "La fête du Travail", "La fête de la Jeunesse"], "Après un mois de jeûne, les familles se réunissent pour partager un grand repas."),
])

# ------------------------------------------------------------------------------------------------ civisme
rows("cm-base", "CM", "Éducation civique", [
    (1, "Dans un bus, que fait un élève poli quand une personne âgée reste debout ?", "Il lui cède sa place",
     ["Il fait semblant de dormir", "Il pose son sac sur le siège libre", "Il change de bus", "Il lui demande de payer"], "Le respect des aînés est une valeur importante dans la vie en société."),
    (1, "Que doit faire un élève pendant une composition pour respecter les règles ?", "Travailler seul, sans copier",
     ["Regarder la feuille de son voisin", "Échanger sa copie avec une autre", "Parler à voix haute", "Circuler dans la salle"], "Copier est de la triche : l'examen sert à mesurer ce que chacun a vraiment appris."),
    (1, "Quel geste économise l'électricité à la maison ?", "Éteindre la lumière en quittant une pièce vide",
     ["Laisser la télévision allumée", "Garder le réfrigérateur ouvert", "Allumer toutes les lampes en plein jour", "Brancher plus d'appareils"], "Éteindre ce que l'on n'utilise pas réduit la facture et ménage les centrales."),
    (1, "Que faut-il faire quand on voit une fontaine publique qui fuit ?", "Prévenir un adulte ou le service concerné",
     ["Rien, l'eau est gratuite", "Jouer dans l'eau qui coule", "Boucher la fuite avec du sable", "En profiter pour laver ses chaussures"], "Une fuite non signalée gaspille l'eau potable dont tout le quartier a besoin."),
    (2, "Pourquoi évite-t-on de mettre le feu à la brousse sans raison ?", "Le feu détruit la végétation et les animaux, et peut atteindre les maisons",
     ["Il fait fuir les nuages", "Il rend la terre plus dure", "Il fait pousser les arbres plus vite", "Il est interdit seulement la nuit"], "Les feux de brousse appauvrissent les sols et menacent les villages voisins."),
    (2, "Comment s'appelle la petite cabine où l'électeur choisit son bulletin à l'abri des regards ?", "L'isoloir",
     ["Le guichet", "La cabine d'essayage", "L'urne", "Le bureau de poste"], "L'isoloir garantit que personne ne voit pour qui la personne vote."),
    (2, "Que garantit le secret du vote ?", "Que personne ne peut savoir quel choix l'électeur a fait",
     ["Que tous les électeurs votent pareil", "Que le vote est réservé à un petit groupe", "Que le résultat est connu à l'avance", "Que l'électeur peut voter deux fois"], "Le vote secret protège la liberté de choix de chaque citoyen."),
    (2, "Que doit faire un élève qui voit un camarade se faire harceler ?", "En parler à un adulte de confiance",
     ["Rire avec les autres", "Filmer la scène pour la montrer", "Faire comme s'il n'avait rien vu", "Se joindre aux moqueries"], "Le harcèlement s'arrête plus vite quand un adulte est prévenu."),
    (2, "De quel côté de la route circule-t-on au Cameroun ?", "À droite",
     ["À gauche", "Au milieu", "Du côté du soleil", "Là où il y a le moins de monde"], "Au Cameroun, comme en France, les véhicules roulent à droite."),
    (3, "Sur une route sans trottoir, comment un piéton doit-il marcher pour voir arriver les véhicules ?", "Sur le bord gauche, face à la circulation",
     ["Sur le bord droit, dos aux véhicules", "Au milieu de la chaussée", "Sur la ligne blanche centrale", "N'importe où, sans regarder"], "En roulant à droite, les véhicules arrivent en face de celui qui marche sur la gauche."),
    (1, "Comment appelle-t-on les habitants du Cameroun ?", "Les Camerounais",
     ["Les Camerounois", "Les Cameroniens", "Les Camerons", "Les Camerounards"], "Le nom des habitants est « Camerounais » ou « Camerounaises »."),
    (2, "Comment appelle-t-on les habitants de Yaoundé ?", "Les Yaoundéens",
     ["Les Yaoundais", "Les Yaoundois", "Les Yaoundiens", "Les Yaoundettes"], "On dit aussi « Yaoundéennes » pour les femmes."),
    (2, "Comment appelle-t-on les habitants de Douala ?", "Les Doualais",
     ["Les Doualéens", "Les Doualiens", "Les Doualois", "Les Doualistes"], "On dit « Doualaises » pour les femmes."),
    (2, "Comment s'appelle le chef du gouvernement camerounais ?", "Le Premier ministre",
     ["Le gouverneur", "Le préfet", "Le sénateur", "Le maire"], "Le Premier ministre dirige le gouvernement ; le chef de l'État est le président de la République."),
])

# ------------------------------------------------------------------------------------------------ pidgin
table("cm-base", "CM", "Langues du Cameroun", [
    ("tok", "parler", 3), ("kam", "venir", 3), ("slip", "dormir", 3), ("wetin", "quoi", 3),
], "Dans le pidgin camerounais, que signifie le mot « {a} » ?", "Quel mot du pidgin camerounais signifie « {b} » ?", diff=3,
      expl="En pidgin camerounais, « {a} » veut dire « {b} ».", extra_b=["manger", "marcher", "donner", "courir", "regarder", "l'eau"])

rows("cm-base", "CM", "Langues du Cameroun", [
    (3, "En ewondo, que signifie le salut « mbolo » ?", "Bonjour",
     ["Merci", "Au revoir", "S'il vous plaît", "Bonne nuit"], "« Mbolo » est le salut courant chez les Ewondo et les Beti, à Yaoundé et alentour."),
    (4, "Que signifie « jaaraama » en fulfulde, la langue des Peuls ?", "Merci",
     ["Bonjour", "Au revoir", "Excusez-moi", "Bienvenue"], "« Jaaraama » est la formule de remerciement en fulfulde, répandue dans le Nord."),
])

# ------------------------------------------------------------------------------------------------ sport et médias
rows("cm-base", "CM", "Médias", [
    (4, "Quel quotidien d'information publié en français et en anglais appartient à l'État camerounais ?", "Cameroon Tribune",
     ["Le Messager", "Mutations", "L'Œil du Sahel", "Le Jour"], "Cameroon Tribune est le quotidien national, édité par la société SOPECAM."),
])

# ------------------------------------------------------------------------------------------------ symboles, institutions, histoire
rows("cm-base", "CM", "Symboles et institutions", [
    (2, "Dans quelles langues l'hymne national du Cameroun existe-t-il ?", "En français et en anglais",
     ["En ewondo et en duala", "En français et en arabe", "En anglais et en espagnol", "En fulfulde seulement"], "Les deux langues officielles du pays ont chacune leur version de l'hymne."),
    (4, "En quelle année la première élection présidentielle pluraliste de l'ère du multipartisme a-t-elle eu lieu au Cameroun ?", "1992",
     ["1972", "1984", "1996", "2004"], "Elle a suivi les lois de 1990 qui ont rétabli le multipartisme."),
    (4, "Où le Cameroun a-t-il participé pour la première fois aux Jeux olympiques, en 1964 ?", "À Tokyo",
     ["À Rome", "À Mexico", "À Munich", "À Montréal"], "Le pays est apparu aux Jeux olympiques d'été peu après son indépendance."),
])

rows("cm-base", "CM", "Santé et société", [
    (3, "Dans quelle ville se trouve l'hôpital Laquintinie, l'un des grands hôpitaux publics du pays ?", "Douala",
     ["Yaoundé", "Garoua", "Bafoussam", "Kribi"], "L'hôpital Laquintinie est l'un des principaux hôpitaux de la capitale économique."),
    (4, "Quel médecin militaire français a organisé dans les années 1920 la lutte contre la maladie du sommeil au Cameroun, notamment à Ayos ?", "Eugène Jamot",
     ["Louis Pasteur", "Albert Calmette", "Alexandre Yersin", "Alphonse Laveran"], "Un hôpital de Yaoundé porte son nom : l'hôpital Jamot."),
    (4, "Quel institut de recherche médicale, installé à Yaoundé, fait partie du réseau international des instituts Pasteur ?", "Le Centre Pasteur du Cameroun",
     ["L'institut Robert-Koch", "L'institut Alexander-Fleming", "L'institut Marie-Curie", "L'institut Jenner"], "Le Centre Pasteur du Cameroun travaille sur les maladies infectieuses."),
])

rows("cm-base", "CM", "Musique et danse", [
    (3, "Quelle chanteuse colombienne a repris en 2010 le refrain camerounais « Zangalewa » dans sa chanson « Waka Waka » ?", "Shakira",
     ["Beyoncé", "Rihanna", "Alicia Keys", "Jennifer Lopez"], "« Waka Waka (This Time for Africa) » reprend un air popularisé par le groupe camerounais Golden Sounds."),
])

# ------------------------------------------------------------------------------------------------ régions, distances, monnaie
rows("cm-base", "CM", "Repères du Cameroun", [
    (2, "Combien de régions du Cameroun ont le mot « Nord » dans leur nom ?", "Trois",
     ["Une", "Deux", "Quatre", "Cinq"], "Il s'agit du Nord, de l'Extrême-Nord et du Nord-Ouest."),
    (2, "Combien de régions du Cameroun ont le mot « Sud » dans leur nom ?", "Deux",
     ["Une", "Trois", "Quatre", "Aucune"], "Il s'agit du Sud et du Sud-Ouest."),
    (3, "Environ combien de kilomètres séparent Douala de Limbé par la route ?", "Environ 70 km",
     ["Environ 20 km", "Environ 150 km", "Environ 300 km", "Environ 600 km"], "Limbé est une ville côtière située au pied du mont Cameroun."),
    (4, "Environ combien de kilomètres de voie ferrée séparent Yaoundé de Ngaoundéré ?", "Environ 600 km",
     ["Environ 150 km", "Environ 300 km", "Environ 1 200 km", "Environ 1 800 km"], "Le train Camrail met une nuit pour relier les deux villes."),
    (3, "Quelle est la plus grosse coupure de billet de franc CFA utilisée au Cameroun ?", "10 000 francs",
     ["2 000 francs", "5 000 francs", "20 000 francs", "50 000 francs"], "Les billets de la zone CEMAC vont de 500 à 10 000 francs."),
])

table("cm-base", "CM", "Peuples du Cameroun", [
    ("Bulu", "Le Sud", 3), ("Eton", "Le Centre", 4), ("Bamiléké", "L'Ouest", 2), ("Mafa", "L'Extrême-Nord", 4), ("Toupouri", "L'Extrême-Nord", 4),
    ("Massa", "L'Extrême-Nord", 4), ("Bakossi", "Le Sud-Ouest", 4), ("Oroko", "Le Sud-Ouest", 5), ("Bafut", "Le Nord-Ouest", 4),
], "Dans quelle région du Cameroun vit principalement le peuple {a} ?", None, diff=4,
      expl="Région principale des {a} : {b}.", extra_b=["L'Adamaoua", "L'Est", "Le Littoral"])

# ------------------------------------------------------------------------------------------------ noms du pays
table("cm-base", "CM", "Langues du monde", [
    ("anglais", "Cameroon", 2), ("espagnol", "Camerún", 3), ("néerlandais", "Kameroen", 4), ("italien", "Camerun", 4), ("portugais", "Camarões", 3), ("allemand", "Kamerun", 3),
], "Comment écrit-on le nom « Cameroun » en {a} ?", None, diff=3, expl="En {a}, le pays s'écrit « {b} ».")

# ------------------------------------------------------------------------------------------------ vote et vie civique
rows("cm-base", "CM", "Éducation civique", [
    (2, "Comment appelle-t-on la boîte dans laquelle l'électeur dépose son bulletin de vote ?", "L'urne",
     ["L'isoloir", "Le coffre-fort", "La boîte aux lettres", "La corbeille"], "L'urne est scellée pour que les bulletins restent secrets jusqu'au dépouillement."),
    (3, "Qu'appelle-t-on le dépouillement après un vote ?", "Le comptage des bulletins trouvés dans l'urne",
     ["La distribution des cartes d'électeur", "L'inscription sur la liste électorale", "Le discours du candidat", "La fermeture des écoles"], "Il se fait devant les représentants des candidats, pour que chacun puisse contrôler."),
    (2, "Qu'est-ce que l'abstention lors d'une élection ?", "Le fait de ne pas aller voter",
     ["Le fait de voter deux fois", "Le fait d'être candidat", "Le fait de compter les voix", "Le fait de voter pour tous les candidats"], "Un électeur qui s'abstient reste chez lui ou ne dépose pas de bulletin."),
    (3, "Que signifie « suffrage universel » ?", "Chaque citoyen qui remplit les conditions d'âge peut voter",
     ["Seuls les hommes votent", "Seuls les plus riches votent", "Seuls les fonctionnaires votent", "Les enfants votent aussi"], "Chaque électeur a le même poids : une personne, une voix."),
    (1, "À quoi servent les ralentisseurs, ou « dos d'âne », sur la route ?", "À obliger les véhicules à ralentir",
     ["À faire du bruit", "À arroser la chaussée", "À garer les motos", "À décorer la route"], "On les place devant les écoles et dans les quartiers pour protéger les piétons."),
    (1, "Que veut dire être ponctuel ?", "Arriver à l'heure",
     ["Arriver très en retard", "Ne jamais venir", "Partir avant la fin", "Arriver sans prévenir"], "La ponctualité montre du respect pour les autres, par exemple à l'école."),
    (2, "Pourquoi regarde-t-on la date de péremption d'un produit ?", "Pour savoir jusqu'à quand on peut l'utiliser sans risque",
     ["Pour connaître son prix", "Pour savoir où il a été fabriqué", "Pour connaître sa couleur", "Pour savoir qui l'a acheté"], "Un aliment ou un médicament périmé peut rendre malade."),
    (2, "À quoi sert un trottoir ?", "À protéger les piétons de la circulation",
     ["À garer les camions", "À faire rouler les motos", "À faire sécher le linge", "À arrêter les bus"], "Les piétons y marchent en sécurité, à l'écart des véhicules."),
])

# ------------------------------------------------------------------------------------------------ classer les noms
rows("cm-base", "CM", "Repères du Cameroun", [
    (1, "Quelle région du Cameroun porte un nom qui commence par la lettre A ?", "L'Adamaoua",
     ["L'Est", "L'Extrême-Nord", "L'Ouest", "Le Nord-Ouest"], "L'Adamaoua est la seule région dont le nom commence par un A."),
    (3, "Parmi ces noms, lequel est celui d'une région du Cameroun ?", "Le Littoral",
     ["Le Wouri", "Le Mfoundi", "Le Moungo", "Le Noun"], "Le Wouri, le Mfoundi, le Moungo et le Noun sont des départements."),
    (3, "Parmi ces noms, lequel est celui d'un département du Cameroun ?", "Le Mfoundi",
     ["Le Littoral", "L'Adamaoua", "L'Extrême-Nord", "Le Sud-Ouest"], "Le Mfoundi est le département de Yaoundé ; les autres noms sont ceux de régions."),
    (2, "Parmi ces noms, lequel désigne un fleuve du Cameroun ?", "La Sanaga",
     ["Le mont Oku", "Le parc de Waza", "Les monts Mandara", "La forêt de Korup"], "La Sanaga est le plus long fleuve entièrement camerounais."),
    (2, "Parmi ces noms, lequel désigne une montagne du Cameroun ?", "Le mont Oku",
     ["La Sanaga", "Le Wouri", "La Bénoué", "Le Logone"], "Le mont Oku, dans le Nord-Ouest, dépasse 3 000 m."),
    (2, "Parmi ces noms, lequel est celui d'un parc national du Cameroun ?", "Waza",
     ["Sanaga", "Mandara", "Wouri", "Oku"], "Le parc de Waza se trouve dans l'Extrême-Nord."),
    (2, "Dans laquelle de ces villes l'anglais est-il parlé au quotidien par une grande partie de la population ?", "Bamenda",
     ["Garoua", "Bertoua", "Ebolowa", "Maroua"], "Bamenda est le chef-lieu du Nord-Ouest, l'une des deux régions anglophones."),
])

rows("cm-base", "CM", "Gastronomie camerounaise", [
    (2, "Quelle racine piquante entre dans la préparation d'un jus très apprécié au Cameroun, souvent mélangé au citron ?", "Le gingembre",
     ["La carotte", "Le radis", "Le manioc", "L'oignon"], "Le jus de gingembre se boit frais, parfois sucré avec un peu d'ananas."),
])

# ------------------------------------------------------------------------------------------------ sécurité au quotidien
rows("cm-base", "CM", "Éducation civique", [
    (1, "Quel service doit-on alerter en cas d'incendie ?", "Les sapeurs-pompiers",
     ["Les douaniers", "Les météorologues", "Les facteurs", "Les arbitres"], "Les sapeurs-pompiers éteignent les feux et secourent les personnes en danger."),
    (2, "À quoi sert un extincteur ?", "À éteindre un début d'incendie",
     ["À arroser les plantes", "À allumer un feu", "À réparer une prise électrique", "À chauffer l'eau"], "Il faut alerter les secours même si l'on tente d'éteindre soi-même un petit feu."),
    (2, "Que faut-il faire en cas d'orage avec foudre ?", "S'abriter dans un bâtiment et éviter de rester sous un arbre isolé",
     ["Se mettre à l'abri sous un grand arbre seul", "Courir au milieu d'un champ ouvert", "Monter au sommet d'une colline", "Se baigner dans la rivière"], "Un arbre isolé attire la foudre : il est dangereux de s'en approcher pendant un orage."),
    (2, "Pourquoi ne faut-il pas se baigner dans une rivière en crue ?", "Le courant est fort et peut emporter, même un bon nageur",
     ["L'eau est trop propre", "Les poissons dorment", "L'eau est trop froide pour les enfants uniquement", "Les ponts sont fermés"], "Après de fortes pluies, le niveau monte vite et le courant devient dangereux."),
])

# ------------------------------------------------------------------------------------------------ détails sûrs en plus
rows("cm-base", "CM", "Relief du Cameroun", [
    (4, "Comment appelle-t-on la bande de territoire camerounais qui s'étire vers le Tchad, dans l'Extrême-Nord, à cause de sa forme ?", "Le « bec de canard »",
     ["La « queue de lion »", "Le « pied d'éléphant »", "La « corne de zébu »", "La « main de gorille »"], "Cette étroite pointe suit le fleuve Logone, face au Tchad."),
    (4, "Qu'est-ce qu'une caldeira, comme celle du mont Manengouba ?", "Une grande dépression formée par l'effondrement d'un volcan",
     ["Un lac artificiel de barrage", "Une grotte creusée par la mer", "Un plateau de sable", "Une colline faite de termitières"], "Les caldeiras peuvent contenir des lacs de cratère."),
    (4, "Quel gaz s'est échappé du lac Nyos en août 1986 et a asphyxié les villages voisins ?", "Le dioxyde de carbone",
     ["L'hélium", "Le méthane des marais uniquement", "L'oxygène pur", "L'hydrogène"], "Ce gaz, plus lourd que l'air, avait lentement saturé les eaux profondes du lac."),
    (5, "À quoi servent les tuyaux installés dans le lac Nyos depuis le début des années 2000 ?", "À évacuer lentement le gaz accumulé au fond",
     ["À pomper l'eau pour les villages", "À produire de l'électricité", "À pêcher à grande profondeur", "À faire monter la température"], "Le dégazage contrôlé réduit le risque d'une nouvelle explosion de gaz."),
])
rows("cm-base", "CM", "Sport", [
    (3, "Quel est le code à trois lettres du Cameroun utilisé par les instances sportives, par exemple pour le football et les Jeux olympiques ?", "CMR",
     ["CAM", "CMN", "CRN", "CMO"], "Les sélections sportives du pays figurent sous le sigle CMR dans les tableaux de résultats."),
    (2, "Comment s'appelle la compétition à élimination directe du football camerounais, ouverte aux clubs de plusieurs divisions ?", "La Coupe du Cameroun",
     ["La Ligue des champions", "La Coupe de la Ligue", "La Supercoupe d'Afrique", "Le Tournoi des cinq nations"], "Le vainqueur de la Coupe du Cameroun peut représenter le pays en compétition africaine."),
])

# ------------------------------------------------------------------------------------------------ quartiers, clubs, écoles, sigles (compléments)
table("cm-base", "CM", "Villes du Cameroun", [
    ("Kondengui", "Yaoundé", 4), ("Nylon", "Douala", 4), ("Ndogbong", "Douala", 4), ("Yassa", "Douala", 4),
    ("Muea", "Buea", 4), ("Bota", "Limbé", 4),
], "Dans quelle ville se trouve le quartier ou la localité de {a} ?", None, diff=4, expl="{a} se trouve à {b}.",
      extra_b=["Garoua", "Bafoussam", "Kribi", "Édéa"])

table("cm-base", "CM", "Sport", [
    ("PWD Bamenda", "Bamenda", 4), ("Victoria United", "Limbé", 4), ("Stade Renard", "Melong", 5), ("Unisport du Haut-Nkam", "Bafang", 5),
    ("Sable FC", "Batié", 5), ("Bamboutos FC", "Mbouda", 5),
], "Dans quelle ville est basé le club de football {a} ?", None, diff=4, expl="Le club {a} est basé à {b}.",
      extra_b=["Garoua", "Douala", "Yaoundé", "Bafoussam", "Dschang", "Ebolowa"])

table("cm-base", "CM", "Éducation", [
    ("l'Université des Montagnes", "Bangangté", 4), ("l'Institut universitaire de la côte (IUC)", "Douala", 4),
], "Dans quelle ville se trouve {a} ?", None, diff=4, expl="{a} se trouve à {b}.", extra_b=["Bafoussam", "Dschang", "Garoua", "Buea", "Limbé"])

table("cm-base", "CM", "Institutions et entreprises", [
    ("MINDEF", "Ministère de la Défense", 3), ("MINREX", "Ministère des Relations extérieures", 4),
    ("FEICOM", "Fonds spécial d'équipement et d'intervention intercommunale", 5), ("FECAFOOT", "Fédération camerounaise de football", 3),
    ("CNOSC", "Comité national olympique et sportif du Cameroun", 5), ("IRAD", "Institut de recherche agricole pour le développement", 5),
    
], "Que signifie le sigle {a} au Cameroun ?", None, diff=4, expl="{a} : {b}.")
