"""Cameroun : culture, langues, gastronomie, musique, arts, architecture, patrimoine, littérature et cinéma.
Module rédigé à la main : tout sort en `review`. Les attributions de plats, de fêtes et de langues sont formulées
« traditionnellement associé à » : elles décrivent des usages, pas des appartenances exclusives."""
from .cult_kit import rows, table, theme

theme("cm-culture2", "Culture camerounaise : plats, langues, fêtes, musique, arts, patrimoine, lettres et cinéma",
      "Comparer avec des ouvrages ethnographiques et culinaires, la liste du patrimoine mondial de l'UNESCO (whc.unesco.org), "
      "les sites des festivals et des musées, les catalogues d'éditeurs (Présence africaine, Gallimard) et le FESPACO ; "
      "les usages varient d'un village à l'autre.")

# ------------------------------------------------------------------------------------------------ gastronomie
rows("cm-culture2", "CM", "Gastronomie camerounaise", [
    (1, "Le ndolé est un plat emblématique de quel pays ?", "Le Cameroun", ["Le Sénégal", "Le Kenya", "L'Éthiopie", "Le Maroc"],
     "Plat de feuilles amères, d'arachides et de viande ou de poisson, souvent présenté comme un plat national."),
    (1, "Le poulet DG est une spécialité de quel pays ?", "Le Cameroun", ["La Côte d'Ivoire", "Le Bénin", "Madagascar", "La Tunisie"],
     "Poulet sauté aux plantains mûrs frits et aux légumes, très servi lors des fêtes."),
    (1, "Que signifient les lettres « DG » dans le nom du plat « poulet DG » ?", "Directeur général",
     ["Dieu garde", "Délice du golfe", "Dîner gourmand", "Dos grillé"],
     "Le nom évoque les « directeurs généraux » à qui ce plat de fête serait servi."),
    (1, "Quelle couleur a l'huile de palme brute utilisée dans de nombreux plats camerounais ?", "Rouge-orangé",
     ["Verte", "Blanche", "Noire", "Bleutée"], "L'huile de palme brute est riche en caroténoïdes, d'où sa couleur rouge-orangé."),
    (1, "Le bâton de manioc est fabriqué à partir de quelle plante ?", "Le manioc", ["L'igname", "Le plantain", "La patate douce", "Le mil"],
     "La pâte de manioc est enveloppée dans des feuilles puis cuite à la vapeur."),
    (2, "Dans quelle région le ndolé est-il traditionnellement associé ?", "Le Littoral",
     ["L'Extrême-Nord", "L'Est", "L'Adamaoua", "Le Nord"], "Le ndolé est lié à la cuisine des peuples sawa du Littoral, notamment à Douala."),
    (2, "Quel légume-feuille donne son goût amer au ndolé ?", "Les feuilles de ndolé", ["Le gombo", "La laitue", "Le chou pommé", "Le poireau"],
     "Les feuilles, lavées et bouillies, sont ensuite mêlées à une pâte d'arachides."),
    (2, "Quelle graine pilée donne sa texture crémeuse au ndolé ?", "L'arachide", ["Le sésame", "La noix de cajou", "Le soja", "L'amande de karité"],
     "L'arachide pilée lie la sauce du ndolé et en adoucit l'amertume."),
    (2, "Quel haricot sec écrasé est la base du koki ?", "Le niébé", ["La lentille", "Le pois chiche", "Le haricot rouge", "Le soja"],
     "Le koki est une pâte de niébé, mêlée d'huile de palme."),
    (2, "Comment cuit-on traditionnellement le koki ?", "Enveloppé dans des feuilles et cuit à la vapeur",
     ["Frit dans l'huile", "Grillé sur la braise", "Cuit au four à pain", "Séché au soleil"],
     "La pâte de niébé est emballée dans des feuilles, d'où sa forme de petit pain."),
    (2, "Quelle huile colore et parfume le koki et l'achu ?", "L'huile de palme", ["L'huile d'olive", "L'huile de colza", "L'huile de tournesol", "L'huile de lin"],
     "L'huile de palme rouge est une base de cuisson classique de la cuisine camerounaise."),
    (2, "Quel tubercule pilé forme la base de l'achu ?", "Le taro", ["Le manioc", "L'igname", "La patate douce", "La pomme de terre"],
     "L'achu est une pâte de taro pilé, servie avec une sauce jaune."),
    (2, "Quelle sauce accompagne traditionnellement l'achu ?", "La sauce jaune", ["La sauce tomate", "La sauce noire", "La sauce verte", "La sauce gombo"],
     "Sa couleur vient de l'huile de palme émulsionnée avec de la potasse alimentaire."),
    (3, "Dans quelle région l'achu est-il traditionnellement associé ?", "Le Nord-Ouest",
     ["L'Extrême-Nord", "Le Littoral", "L'Est", "Le Sud"], "Ce plat de taro et de sauce jaune est lié aux hautes terres de l'Ouest anglophone."),
    (5, "Quel ingrédient alcalin est ajouté à l'huile de palme pour obtenir la sauce jaune de l'achu ?", "La potasse alimentaire (kanwa)",
     ["Le jus de citron vert", "Le vinaigre blanc", "Le sucre roux", "Le lait de coco"],
     "La potasse (ou natron) fait émulsionner l'huile de palme avec l'eau et donne la couleur jaune."),
    (2, "Quelle couleur caractérise la sauce du mbongo tchobi ?", "Noire", ["Rouge", "Jaune", "Verte", "Blanche"],
     "La sauce est noircie par des épices grillées."),
    (3, "Dans quelle région le mbongo tchobi est-il traditionnellement associé ?", "Le Littoral",
     ["L'Adamaoua", "L'Extrême-Nord", "L'Est", "Le Nord-Ouest"], "Cette sauce noire à base d'épices est liée aux peuples du Littoral, comme les Bassa."),
    (4, "Que fait-on subir aux épices du mbongo tchobi pour obtenir la couleur noire de la sauce ?", "On les grille longuement avant de les moudre",
     ["On les fait fermenter dans l'eau", "On les mélange à de l'encre végétale", "On les sale puis on les sèche au soleil", "On les congèle puis on les râpe"],
     "Les épices grillées jusqu'à noircir donnent leur teinte et leur arôme à la sauce."),
    (2, "Quel fruit-légume frit accompagne le poulet dans le poulet DG ?", "Le plantain mûr", ["Le manioc", "L'igname", "La patate douce", "La mangue verte"],
     "Le plantain frit apporte une note sucrée ; carottes et haricots verts complètent le plat."),
    (2, "Quel nom, utilisé à Douala, désigne le bâton de manioc ?", "Miondo", ["Koki", "Achu", "Soya", "Kondré"],
     "Le miondo accompagne souvent le ndolé ou le poisson braisé."),
    (3, "Quelle étape de préparation précède la cuisson du bâton de manioc ?", "Le rouissage du manioc dans l'eau",
     ["Le fumage sur braise", "La torréfaction à sec", "La congélation", "La distillation"],
     "Le manioc trempe pour fermenter et perdre son amertume, puis il est moulu et enveloppé."),
    (3, "Quel nom les Beti donnent-ils au plat de feuilles de gnetum finement hachées ?", "Okok", ["Ndolé", "Koki", "Kondré", "Achu"],
     "L'okok est cuit avec de l'huile de palme, du poisson fumé ou de la viande."),
    (4, "Dans quelle région l'eru, plat de gnetum, est-il traditionnellement associé ?", "Le Sud-Ouest",
     ["L'Est", "L'Adamaoua", "Le Nord", "L'Extrême-Nord"], "L'eru est un plat des communautés du Sud-Ouest, avec le waterleaf et la viande."),
    (4, "Quelle plante donne les feuilles de l'eru ?", "Le gnetum", ["Le manioc", "Le baobab", "Le moringa", "Le gombo"],
     "Le gnetum (Gnetum africanum), liane forestière, fournit les feuilles cuisinées en eru ou en okok."),
    (3, "Quel petit poisson fumé du Littoral est souvent ajouté aux sauces ?", "Le bonga", ["Le thon", "La carpe", "La truite", "La morue"],
     "Le bonga, poisson de l'Atlantique, est fumé puis utilisé pour parfumer les plats."),
    (4, "Quelle spécialité bassa de poisson ou de viande est cuite dans des feuilles de bananier avec des épices ?", "Le ndomba",
     ["Le soya", "Le bobolo", "Le folléré", "Le matango"], "Le ndomba est une cuisson à l'étouffée dans des feuilles."),
    (3, "Quelle boisson de lait caillé est associée aux éleveurs peuls du Nord ?", "Le kossam", ["Le matango", "La bili-bili", "Le folléré", "Le gingembre"],
     "Le kossam est un lait fermenté consommé par les communautés d'éleveurs."),
    (2, "Que désigne le mot « soya » dans les rues du Cameroun ?", "Des brochettes de viande épicée grillées",
     ["Une boisson de gingembre", "Un gâteau de maïs", "Un plat de poisson fumé", "Une bouillie de mil"],
     "La soya se vend le soir sur les trottoirs, avec du piment et des oignons."),
    (3, "Quel produit camerounais a obtenu une indication géographique protégée européenne en 2013 ?", "Le poivre de Penja",
     ["Le miel de Kribi", "Le café de Maroua", "Le cacao d'Ebolowa", "La banane de Douala"],
     "Le poivre de Penja, cultivé dans le Moungo (Littoral), fut un des premiers produits africains protégés par l'UE."),
    (4, "Quel miel blanc des hauts plateaux du Nord-Ouest est issu des environs du mont Oku ?", "Le miel d'Oku",
     ["Le miel de Penja", "Le miel de Kribi", "Le miel de Waza", "Le miel de Maroua"],
     "Le miel d'Oku, blanc et crémeux, vient de la forêt de Kilum-Ijim."),
    (3, "Quelle céréale est traditionnellement la base de la boule consommée dans l'Extrême-Nord ?", "Le mil", ["Le riz", "Le blé", "L'avoine", "Le quinoa"],
     "Boule de mil ou de sorgho avec sauce de feuilles ou de gombo est un repas courant du Nord."),
    (2, "Quel plat de rue associe des beignets de farine, des haricots et une bouillie ?", "Les beignets-haricots-bouillie",
     ["Le poulet DG", "Le ndolé", "Le mbongo tchobi", "Le kondré"], "C'est un petit-déjeuner populaire dans les villes camerounaises."),
    (3, "Quelle boisson rouge, à base d'hibiscus, est servie glacée dans toute l'Afrique de l'Ouest et au Cameroun ?", "Le bissap",
     ["Le matango", "La bili-bili", "Le kossam", "Le ginger-ale"], "Le bissap est une infusion de fleurs d'hibiscus, aussi appelée folléré."),
    (4, "Que désigne le mot « mbeng » en camfranglais ?", "L'Europe ou l'étranger occidental", ["Le village", "L'argent", "La forêt", "Un taxi"],
     "Le mot est entré dans le vocabulaire des jeunes ; « mbenguiste » désigne celui qui vit en Europe.", "Langues du Cameroun"),
])

# ------------------------------------------------------------------------------------------------ langues
rows("cm-culture2", "CM", "Langues du Cameroun", [
    (1, "Quel surnom le Cameroun doit-il à la diversité de ses paysages et de ses cultures ?", "L'Afrique en miniature",
     ["Le pays des mille collines", "La perle de l'Afrique", "Le géant d'Afrique", "Le pays de la téranga"],
     "On y trouve côte, forêt, savane, montagnes et désert : toute l'Afrique en petit."),
    (2, "Comment s'appelle le mélange de français, d'anglais et de langues locales parlé surtout par les jeunes des villes ?", "Le camfranglais",
     ["Le pidgin-english", "Le swahili", "Le lingala", "Le verlan"], "Le nom associe « Cameroun », « français » et « anglais »."),
    (3, "Que signifie le mot « kamtok » ?", "Le pidgin-english camerounais", ["Le français du Nord", "La langue des Bamoun", "Un dialecte peul", "Un parler des pêcheurs"],
     "« Kamtok » vient de « Cameroon talk » : c'est le nom courant du pidgin camerounais."),
    (4, "Quel sigle désigne l'alphabet commun proposé pour écrire les langues camerounaises ?", "L'AGLC",
     ["L'API", "L'AFNOR", "L'ALCAM", "L'OAPI"], "L'Alphabet général des langues camerounaises est la référence pour noter les langues nationales."),
    (5, "Quel projet d'enseignement bilingue en langues maternelles a été lancé au Cameroun sous l'impulsion de Maurice Tadadjeu ?", "PROPELCA",
     ["PNDP", "PRODEM", "ALCAM", "MINEDUB"],
     "Le PROPELCA (Projet de recherche opérationnelle pour l'enseignement des langues au Cameroun) date des années 1980."),
    (2, "Quelle langue peule sert de langue de communication dans une grande partie du Nord ?", "Le fulfulde",
     ["L'ewondo", "Le duala", "Le bassa", "Le ghomala"], "Elle est parlée bien au-delà des communautés peules."),
    (3, "Quelle langue nationale est traditionnellement associée aux Bulu du Sud ?", "Le bulu", ["Le duala", "Le bassa", "Le bamoun", "Le fulfulde"],
     "Bulu et ewondo appartiennent au groupe fang-beti."),
    (4, "À quel grand groupe appartiennent les peuples ewondo, eton, bulu et fang ?", "Le groupe béti-pahouin (fang-béti)",
     ["Le groupe sawa", "Le groupe bamiléké", "Le groupe choa", "Le groupe soudanais"],
     "Ces peuples de langues proches sont répartis entre le Centre, le Sud du Cameroun et les pays voisins."),
    (4, "Quelle langue est traditionnellement associée aux Nso et à la ville de Kumbo ?", "Le lamnso'", ["Le mungaka", "Le medumba", "Le ghomala", "Le fe'efe'e"],
     "Kumbo est la ville principale du pays nso, dans le Bui, au Nord-Ouest."),
    (5, "Quelle langue est traditionnellement associée au peuple de Bali Nyonga, dans le Nord-Ouest ?", "Le mungaka",
     ["Le lamnso'", "Le medumba", "Le yemba", "Le ghomala"], "Bali Nyonga célèbre aussi la fête du Lela."),
    (5, "Quel nom les Bakweri donnent-ils à leur langue ?", "Le mokpwe", ["Le duala", "Le bassa", "Le oroko", "Le ewondo"],
     "Les Bakweri vivent sur les flancs du mont Cameroun, dans le Sud-Ouest."),
    (4, "Quelle ville est le centre de la langue yemba ?", "Dschang", ["Bafang", "Bangangté", "Mbouda", "Foumban"], "Le yemba est une langue bamiléké associée au pays de Dschang."),
    (4, "Comment les Peuls se désignent-ils eux-mêmes au pluriel dans leur langue ?", "Fulbe", ["Haoussa", "Kanouri", "Choa", "Gbaya"],
     "Au singulier on dit pullo, au pluriel fulbe ; en français on dit aussi Foulbé."),
    (3, "Comment appelle-t-on les hautes terres herbeuses de l'Ouest et du Nord-Ouest, où vivent Bamiléké, Bamoun et Tikar ?", "Les Grassfields",
     ["Les monts Mandara", "Le plateau de l'Adamaoua", "Les Hauts-Plateaux sahéliens", "La plaine du Logone"], "Ce mot anglais signifie « champs d'herbe »."),
    (3, "Que désigne le mot « njangi » en pidgin et dans l'anglais camerounais ?", "Une tontine", ["Un marché de nuit", "Une danse de mariage", "Un tambour d'appel", "Une fête de deuil"],
     "Chacun cotise à tour de rôle ; en français camerounais on dit « ndjangui »."),
    (4, "Que signifie « bushfalling » dans le parler camerounais ?", "Partir tenter sa chance à l'étranger",
     ["Aller en brousse chasser", "Tomber en panne en route", "Déboiser un champ", "Danser toute la nuit"],
     "Le « bushfaller » est celui qui part vers l'Occident pour chercher fortune."),
    (3, "Dans quel milieu le camfranglais s'est-il surtout répandu ?", "Chez les jeunes citadins et les élèves",
     ["Chez les éleveurs nomades", "Chez les pêcheurs du Nord", "Dans les chefferies uniquement", "Dans les écoles coraniques"],
     "Il sert de code entre jeunes, mêlant français, anglais, pidgin et langues locales."),
    (5, "Quel nom l'on donne au roi des Bamoun dans leur langue ?", "Mfon", ["Lamido", "Mbombo", "Nana", "Fumu"],
     "Le « mfon » règne à Foumban ; le français emploie aussi « sultan »."),
])
table("cm-culture2", "CM", "Langues du Cameroun",
      [("le duala", "Douala", 2), ("l'ewondo", "Yaoundé", 3), ("le medumba", "Bangangté", 4), ("le yemba", "Dschang", 4),
       ("le fe'efe'e", "Bafang", 4), ("le shü-mom (bamoun)", "Foumban", 3), ("le lamnso'", "Kumbo", 5), ("le mungaka", "Bali", 5)],
      "Dans quelle localité {a} est-il traditionnellement associé ?", "Quelle langue est traditionnellement associée à {b} ?", diff=4,
      expl="{a} est traditionnellement associé à {b}.", extra_b=["Bafoussam", "Ebolowa", "Maroua", "Bertoua", "Mbouda"])
table("cm-culture2", "CM", "Langues du Cameroun",
      [("wuna", "vous (pluriel)", 3), ("pikin", "enfant", 2), ("chop", "manger", 2), ("sabi", "savoir", 3), ("waka", "marcher", 3), ("moni", "argent", 2)],
      "Que signifie « {a} » en pidgin-english camerounais ?", "Quel mot du pidgin camerounais signifie « {b} » ?", diff=3,
      expl="« {a} » signifie « {b} » en pidgin camerounais.", extra_b=["maison", "route", "travail", "nuit"])

# ------------------------------------------------------------------------------------------------ peuples
rows("cm-culture2", "CM", "Peuples du Cameroun", [
    (3, "Quel mot duala désigne les peuples côtiers du Littoral, d'où vient le nom « sawa » ?", "La côte ou le rivage",
     ["La forêt", "La montagne", "Le désert", "Le marché"], "« Sawa » évoque la côte : on parle des « peuples de la côte »."),
    (4, "Quel peuple du Littoral organise le Ngondo ?", "Les Sawa (Douala)", ["Les Bamoun", "Les Peuls", "Les Mafa", "Les Baka"],
     "Cette assemblée traditionnelle se tient à Douala, au bord du Wouri."),
    (4, "Quelle chaîne de montagnes de l'Extrême-Nord est associée aux Mafa ?", "Les monts Mandara", ["Les monts Bamboutos", "Les monts Rumpi", "Les monts Alantika", "Le mont Manengouba"],
     "Les Mafa y cultivent des champs en terrasses entre des pitons volcaniques."),
    (5, "Quel peuple de chasseurs-cueilleurs est associé aux forêts proches de Kribi et de Lolodorf ?", "Les Bagyeli", ["Les Baka", "Les Mbororo", "Les Choa", "Les Mafa"],
     "Les Bagyeli (ou Bakola) vivent dans les forêts du Sud ; les Baka sont plutôt à l'Est."),
    (4, "Quel peuple est associé au village de Rhumsiki, près de Mokolo ?", "Les Kapsiki", ["Les Mousgoum", "Les Mbororo", "Les Baka", "Les Bamiléké"],
     "Rhumsiki est célèbre pour ses pitons volcaniques et ses forgerons."),
    (4, "Quel peuple de langue arabe vit surtout dans l'Extrême-Nord, près du lac Tchad ?", "Les Arabes choa", ["Les Bagyeli", "Les Tikar", "Les Bassa", "Les Bulu"],
     "Les Arabes choa sont présents autour du lac Tchad et dans le Logone-et-Chari."),
    (3, "Quelle activité est traditionnellement associée aux Mbororo ?", "L'élevage de bovins en pasteurs",
     ["La fonte du bronze", "La cueillette en forêt dense", "La pêche en haute mer", "La culture du cacao"], "Les Mbororo, éleveurs de zébus, vivent notamment dans l'Adamaoua et le Nord-Ouest."),
    (4, "Quelle forme de chant est célèbre chez les Baka ?", "La polyphonie vocale avec yodel", ["Le chant grégorien", "L'opéra", "Le chant militaire", "Le jazz manouche"],
     "Les voix s'entrelacent en plusieurs lignes : une pratique reconnue bien au-delà du Cameroun."),
    (5, "Quel animal est traditionnellement associé au pouvoir des fons des Grassfields ?", "La panthère", ["Le lion", "Le guépard", "La hyène", "Le crocodile"],
     "La panthère, symbole royal, figure sur les trônes, les masques et les tissus."),
    (4, "Quelle société d'hommes des Grassfields est associée aux masques d'éléphant en perles ?", "Le kuosi", ["Le ngondo", "Le lela", "Le nguon", "Le mvet"],
     "Les masques d'éléphant sont portés lors de fêtes de chefferie, notamment à l'Ouest."),
    (4, "Que portent traditionnellement des perles de verre dans les chefferies de l'Ouest ?", "Les trônes, statues, pipes et calebasses",
     ["Les toits des cases", "Les poteaux de routes", "Les sacs de grains", "Les pirogues de pêche"],
     "Perles de verre importées cousues sur toile, bois ou calebasse : un art de prestige des Grassfields."),
])

# ------------------------------------------------------------------------------------------------ fêtes et festivals
rows("cm-culture2", "CM", "Fêtes et festivals", [
    (1, "Dans quelle grande ville se tient le Ngondo ?", "Douala", ["Yaoundé", "Bafoussam", "Garoua", "Bamenda"], "Le Ngondo se déroule au bord du Wouri."),
    (2, "Quel fleuve est au cœur des rites du Ngondo ?", "Le Wouri", ["La Sanaga", "Le Nyong", "La Bénoué", "Le Logone"], "Les cérémonies se déroulent sur et dans le Wouri."),
    (5, "Quel esprit des eaux est traditionnellement associé au Ngondo ?", "Le jengu", ["Le mvet", "Le kuosi", "Le lela", "Le mbock"],
     "Le jengu (pluriel miengu) est un esprit de l'eau dans les traditions sawa."),
    (3, "Dans quelle ville se tient le festival de cinéma Écrans noirs ?", "Yaoundé", ["Douala", "Bafoussam", "Garoua", "Limbé"], "Il se déroule dans la capitale et présente des films africains."),
    (4, "Qui a fondé le festival Écrans noirs, consacré au cinéma africain ?", "Bassek Ba Kobhio", ["Jean-Pierre Bekolo", "Manu Dibango", "Mongo Beti", "Daniel Kamwa"],
     "Ce cinéaste est aussi l'auteur de « Sango Malo » (1991)."),
    (5, "Quelle fête, célébrée à Bali Nyonga, est connue sous le nom de Lela ?", "Une fête traditionnelle annuelle de la chefferie de Bali Nyonga",
     ["Un concours de pirogues à Douala", "Un festival de cinéma à Yaoundé", "Une course de montagne à Buea", "Un salon d'artisanat à Foumban"],
     "Le Lela, fête du peuple de Bali Nyonga, se tient dans le Nord-Ouest."),
    (4, "Dans quelle ville débute la Course de l'Espoir sur le mont Cameroun ?", "Buea", ["Limbé", "Kumba", "Tiko", "Mamfe"], "Cette course de montagne part de la ville située au pied du volcan."),
    (3, "Quel peuple célèbre le Nguon à Foumban ?", "Les Bamoun", ["Les Bassa", "Les Peuls", "Les Mafa", "Les Baka"], "Le Nguon est le festival culturel du royaume bamoun."),
])

# ------------------------------------------------------------------------------------------------ musique et danses
rows("cm-culture2", "CM", "Musique et danse", [
    (1, "Le makossa est un style de musique de quel pays ?", "Le Cameroun", ["Le Mali", "Le Bénin", "Le Kenya", "Le Zimbabwe"], "Il est né à Douala et s'est répandu partout dans le pays."),
    (1, "Dans quel pays le bikutsi est-il né ?", "Le Cameroun", ["Le Ghana", "La Côte d'Ivoire", "Le Mali", "L'Angola"], "Le bikutsi est un rythme du Centre et du Sud, lié aux Beti."),
    (1, "Dans quelle grande ville est né le makossa ?", "Douala", ["Yaoundé", "Garoua", "Bafoussam", "Maroua"], "Le makossa vient de la côte, chez les peuples sawa."),
    (4, "Que signifie « bikutsi » en langue ewondo, selon l'explication la plus courante ?", "Frapper la terre", ["Chanter à l'aube", "Danser sur l'eau", "Appeler les ancêtres", "Jouer du tambour"],
     "Le nom évoque le battement des pieds sur le sol."),
    (3, "Quel instrument à lames de bois les guitaristes du bikutsi imitent-ils ?", "Le balafon", ["La kora", "Le violon", "Le cor", "La flûte de Pan"],
     "Le bikutsi traditionnel est joué au balafon ; la guitare électrique a repris ses motifs."),
    (5, "De quel verbe duala vient le mot « makossa » ?", "Un verbe qui signifie « danser »", ["Un verbe qui signifie « pêcher »", "Un verbe qui signifie « chanter »", "Un verbe qui signifie « marcher »", "Un verbe qui signifie « cultiver »"],
     "« Makossa » viendrait du verbe duala « kossa », danser."),
    (3, "Dans quelle ville est né le saxophoniste Manu Dibango ?", "Douala", ["Yaoundé", "Paris", "Kinshasa", "Libreville"], "Il est né à Douala en 1933."),
    (3, "En quelle année est mort Manu Dibango ?", "2020", ["2016", "2018", "2019", "2021"], "Il est mort à Paris, en mars 2020."),
    (4, "Quel chanteur américain a repris le refrain « Soul Makossa » dans « Wanna Be Startin' Somethin' » ?", "Michael Jackson", ["Prince", "Stevie Wonder", "Lionel Richie", "Marvin Gaye"],
     "Le refrain « mama-ko mama-sa » de Manu Dibango est cité dans ce titre de 1983."),
    (4, "Quel peuple est généralement cité comme berceau de l'assiko ?", "Les Bassa", ["Les Bamoun", "Les Mafa", "Les Baka", "Les Peuls"], "Ce rythme est lié au Littoral et au Centre."),
    (4, "Quel instrument accompagne le récit épique du mvet chez les Fang et les Beti ?", "Une cithare-harpe", ["Un xylophone", "Un tambour d'eau", "Une flûte de roseau", "Un cor d'antilope"],
     "Le mvet est un instrument à cordes ; le joueur raconte des épopées."),
    (5, "Comment appelle-t-on le conteur qui joue du mvet ?", "Le mbom mvet", ["Le griot", "Le lamido", "Le fon", "Le sawa"], "Le mbom mvet récite des épopées des peuples fang-béti."),
    (2, "Quel instrument est un xylophone de lames de bois posées sur des calebasses ?", "Le balafon", ["La kora", "Le djembé", "Le mvet", "La flûte"], "Le balafon est répandu en Afrique de l'Ouest et du Centre."),
    (3, "À quoi sert traditionnellement un tam-tam d'appel au village ?", "À transmettre des messages à distance", ["À moudre le grain", "À pêcher le poisson", "À compter le temps", "À fondre le métal"],
     "Les rythmes sont des signaux : annonce d'une cérémonie, d'un deuil, d'un danger."),
    (5, "Quel est le titre du premier album de Richard Bona, sorti en 1999 ?", "Scenes from My Life", ["Munia", "Reverence", "Tiki", "Bonafied"], "Ce bassiste et chanteur est né à Minta, dans l'Est du Cameroun."),
])

# ------------------------------------------------------------------------------------------------ artisanat et arts
rows("cm-culture2", "CM", "Artisanat et arts", [
    (3, "Quelle technique de fonte de métal est pratiquée à Foumban ?", "La cire perdue", ["Le moulage au plâtre", "La galvanoplastie", "Le pressage à froid", "La soudure à l'arc"],
     "Un modèle en cire est recouvert d'argile ; la cire fondue laisse la place au métal."),
    (4, "Comment s'appelle le tissu à l'indigo aux motifs réservés des Grassfields ?", "Le ndop", ["Le kente", "Le bogolan", "Le wax", "Le shweshwe"],
     "Le ndop est teint à l'indigo ; il est associé aux Grassfields, notamment à la région de Bamessing."),
    (4, "Quel vêtement de velours brodé, aux couleurs vives, est associé aux Grassfields du Nord-Ouest ?", "Le toghu", ["Le kaba", "Le boubou", "Le kente", "Le kanga"],
     "Le toghu est porté lors de cérémonies et est devenu un symbole d'élégance camerounaise."),
    (3, "Quelle ville de l'Extrême-Nord est réputée pour sa maroquinerie ?", "Maroua", ["Ebolowa", "Kribi", "Limbé", "Bafia"], "Le cuir de Maroua est travaillé en sacs, coussins et sandales."),
    (4, "Qui a fondé Bandjoun Station, centre d'art contemporain à Bandjoun ?", "Barthélémy Toguo", ["Pascale Marthine Tayou", "Joseph-Francis Sumégné", "Ferdinand Oyono", "Manu Dibango"],
     "Cet artiste international a installé son centre dans la chefferie voisine, à l'Ouest."),
    (5, "Qui a fondé le centre d'art Doual'art à Douala en 1991, avec Didier Schaub ?", "Marilyn Douala Bell", ["Koyo Kouoh", "Léonora Miano", "Calixthe Beyala", "Barthélémy Toguo"],
     "Doual'art est aussi à l'origine du Salon urbain de Douala (SUD)."),
    (5, "Quel artiste camerounais a réalisé « La Nouvelle Liberté », monument de Deïdo à Douala, fait de pièces mécaniques récupérées ?", "Joseph-Francis Sumégné",
     ["Barthélémy Toguo", "Pascale Marthine Tayou", "Hervé Youmbi", "Goddy Leye"], "L'œuvre date de 1996 et fut portée par Doual'art."),
    (4, "Quel plasticien camerounais est connu pour ses « Plastic Trees », arbres de sacs plastiques colorés ?", "Pascale Marthine Tayou", ["Barthélémy Toguo", "Joseph-Francis Sumégné", "Goddy Leye", "Hervé Youmbi"],
     "Cet artiste camerounais vit et travaille en Belgique."),
    (5, "Quel célèbre trône bamoun, offert à l'empereur allemand en 1908, se trouve aujourd'hui à Berlin ?", "Le Mandu Yenu", ["Le Ngondo", "Le Kuosi", "Le Toghu", "Le Njangi"],
     "Le roi Njoya a offert ce trône à Guillaume II : il est conservé au musée ethnologique de Berlin."),
])

# ------------------------------------------------------------------------------------------------ architecture et sites
rows("cm-culture2", "CM", "Architecture et patrimoine", [
    (3, "Quel peuple de l'Extrême-Nord est célèbre pour ses cases en forme d'obus ?", "Les Mousgoum", ["Les Baka", "Les Gbaya", "Les Bassa", "Les Bamoun"],
     "Ces cases hautes sont faites de terre crue modelée à la main."),
    (3, "Quel matériau principal sert à construire les cases-obus ?", "La terre crue", ["La pierre de taille", "Le bambou", "Le béton", "La tôle"], "L'argile malaxée est montée en anneaux successifs, ornée de reliefs."),
    (4, "De quoi est traditionnellement couvert le toit conique des grandes cases des chefferies bamiléké ?", "De chaume", ["De tuiles romaines", "D'ardoises", "De plaques de zinc", "De béton"], "Les poteaux de bois sculptés portent un toit de chaume."),
    (3, "Dans quel matériau sont sculptés les poteaux des grandes cases des chefferies de l'Ouest ?", "Le bois", ["Le marbre", "L'ivoire", "Le fer", "La pierre ponce"], "Visages, animaux et motifs géométriques ornent les poteaux."),
    (5, "Quelle enceinte royale du Nord-Ouest, appelée Achum, est associée aux Bafut ?", "Le palais de Bafut", ["Le palais de Foumban", "La chefferie de Bandjoun", "Le palais de Laikom", "Le lamidat de Rey-Bouba"],
     "Le palais de Bafut, résidence du fon, est un monument majeur des Grassfields."),
    (3, "Quelle ville de l'Ouest abrite le palais du sultan des Bamoun ?", "Foumban", ["Dschang", "Mbouda", "Bafang", "Bangangté"], "Le palais est aujourd'hui aussi un musée."),
    (4, "Quel style architectural le roi Njoya a-t-il mêlé aux traditions locales pour son palais ?", "L'architecture coloniale allemande", ["Le style gothique", "Le style mauresque", "L'art déco", "Le style soudano-sahélien"],
     "Le palais associe des influences européennes et des motifs bamoun."),
    (5, "Quel paysage culturel camerounais a été inscrit au patrimoine mondial en 2024 ?", "Diy-Gid-Biy, dans les monts Mandara", ["Le palais de Foumban", "La chefferie de Bandjoun", "Le Ngondo de Douala", "Rhumsiki"],
     "Ce paysage culturel des Mafa associe terrasses, habitats et rites des monts Mandara."),
    (4, "Quel parc camerounais fait partie du site « Sangha Trinational » classé par l'UNESCO ?", "Lobéké", ["Waza", "Korup", "Bouba Ndjida", "Campo-Ma'an"], "Le site réunit des aires protégées du Cameroun, du Congo et de la République centrafricaine."),
    (4, "Quels pays partagent le site « Sangha Trinational » ?", "Le Cameroun, le Congo et la République centrafricaine",
     ["Le Cameroun, le Gabon et la Guinée équatoriale", "Le Cameroun, le Tchad et le Nigeria", "Le Cameroun, le Congo et le Gabon", "Le Cameroun, le Nigeria et le Bénin"],
     "Il a été inscrit en 2012."),
])

# ------------------------------------------------------------------------------------------------ musées
rows("cm-culture2", "CM", "Musées", [
    (2, "Dans quelle ville se trouve le Musée national du Cameroun ?", "Yaoundé", ["Douala", "Bafoussam", "Garoua", "Foumban"], "Il est installé dans la capitale politique du pays."),
    (3, "Quelle ville de l'Ouest abrite le Musée des civilisations ?", "Dschang", ["Bafang", "Mbouda", "Foumban", "Bafoussam"], "Ce musée présente des objets issus des cultures de l'Ouest."),
    (3, "Quel roi bamoun a fait construire le palais de Foumban qui abrite aujourd'hui un musée ?", "Njoya", ["Mbombo", "Ibrahim", "Bell", "Akwa"], "Le palais renferme objets royaux, costumes et trône."),
])

# ------------------------------------------------------------------------------------------------ littérature
rows("cm-culture2", "CM", "Littérature", [
    (3, "En quelle année est paru « Une vie de boy » de Ferdinand Oyono ?", "1956", ["1946", "1950", "1960", "1966"], "Ce roman, sous forme de journal, est l'un des textes majeurs de la littérature africaine francophone."),
    (4, "Comment s'appelle le jeune héros dont le journal forme « Une vie de boy » ?", "Toundi", ["Meka", "Denis", "Banda", "Mor-Zamba"], "Toundi, boy d'un commandant colonial, tient son journal."),
    (4, "Comment s'appelle le héros du roman « Le Vieux Nègre et la médaille » ?", "Meka", ["Toundi", "Denis", "Banda", "Mor-Zamba"], "Le roman d'Oyono se passe en 1956 dans un village sous administration coloniale."),
    (5, "Sous quel pseudonyme Mongo Beti a-t-il publié « Ville cruelle » en 1954 ?", "Eza Boto", ["Mongo Kuma", "Jean-Marc Ela", "Eto Bassa", "René Philombe"], "Le roman a été publié par Présence africaine."),
    (5, "Quel est le titre du roman de Léonora Miano couronné par le prix Goncourt des lycéens en 2006 ?", "Contours du jour qui vient", ["La Saison de l'ombre", "Les Aubes écarlates", "Blues pour Élise", "Crépuscule du tourment"],
     "C'est son deuxième roman, après « L'Intérieur de la nuit »."),
    (5, "Quel roman de Léonora Miano a reçu le prix Femina en 2013 ?", "La Saison de l'ombre", ["Contours du jour qui vient", "L'Intérieur de la nuit", "Les Aubes écarlates", "Blues pour Élise"], "Le roman évoque la traite et ses silences."),
    (5, "Quel roman de Calixthe Beyala a reçu le Grand Prix du roman de l'Académie française en 1996 ?", "Les Honneurs perdus", ["Maman a un amant", "Assèze l'Africaine", "Le Petit Prince de Belleville", "Tu t'appelleras Tanga"], "C'est un des romans les plus connus de cette autrice."),
    (4, "Quel philosophe camerounais a publié « Critique de la raison nègre » en 2013 ?", "Achille Mbembe", ["Fabien Eboussi Boulaga", "Jean-Marc Ela", "Engelbert Mveng", "Patrice Nganang"], "Cet essai interroge l'histoire de la figure du « Nègre » dans la pensée moderne."),
    (4, "Quel prix a reçu Francis Bebey en 1968 pour « Le Fils d'Agatha Moudio » ?", "Le Grand Prix littéraire d'Afrique noire", ["Le prix Goncourt", "Le prix Renaudot", "Le prix Femina", "Le prix Nobel de littérature"], "Ce roman d'amour et de traditions est paru en 1967."),
    (3, "Quel écrivain camerounais est aussi musicien, auteur de « La Poupée ashanti » ?", "Francis Bebey", ["Mongo Beti", "Ferdinand Oyono", "Patrice Nganang", "Bernard Nanga"], "Francis Bebey a écrit des romans, des poèmes et composé de la musique."),
])
table("cm-culture2", "CM", "Littérature",
      [("Remember Ruben", "Mongo Beti", 3), ("Perpétue et l'habitude du malheur", "Mongo Beti", 4), ("Le Roi miraculé", "Mongo Beti", 4), ("Chemin d'Europe", "Ferdinand Oyono", 4),
       ("L'Intérieur de la nuit", "Léonora Miano", 4), ("Les Aubes écarlates", "Léonora Miano", 5), ("Le Petit Prince de Belleville", "Calixthe Beyala", 4),
       ("Maman a un amant", "Calixthe Beyala", 4), ("Mont Plaisant", "Patrice Nganang", 5)],
      "Qui est l'auteur ou l'autrice de « {a} » ?", None, diff=4, expl="« {a} » est une œuvre de {b}.",
      extra_b=["Ahmadou Kourouma", "Chinua Achebe", "Aimé Césaire", "Camara Laye", "Alain Mabanckou"])

# ------------------------------------------------------------------------------------------------ cinéma
rows("cm-culture2", "CM", "Cinéma camerounais", [
    (5, "Quel film camerounais a remporté l'Étalon d'or de Yennenga au FESPACO en 1976 ?", "Muna Moto", ["Sango Malo", "Quartier Mozart", "Pousse-pousse", "Les Saignantes"], "Ce film de Jean-Pierre Dikongue-Pipa est un classique du cinéma africain."),
    (4, "Quel film camerounais de 1992 est réalisé par Jean-Pierre Bekolo ?", "Quartier Mozart", ["Sango Malo", "Muna Moto", "Pousse-pousse", "Le Grand Blanc de Lambaréné"], "C'est une comédie fantastique tournée à Yaoundé."),
])
table("cm-culture2", "CM", "Cinéma camerounais",
      [("Sango Malo", "Bassek Ba Kobhio", 4), ("Le Grand Blanc de Lambaréné", "Bassek Ba Kobhio", 5), ("Les Saignantes", "Jean-Pierre Bekolo", 4),
       ("Afrique, je te plumerai", "Jean-Marie Teno", 4), ("Le Malentendu colonial", "Jean-Marie Teno", 5), ("Pousse-pousse", "Daniel Kamwa", 4),
       ("Muna Moto", "Jean-Pierre Dikongue-Pipa", 5), ("Une affaire de nègres", "Osvalde Lewat", 5)],
      "Quel réalisateur ou quelle réalisatrice a signé « {a} » ?", None, diff=4, expl="« {a} » est un film de {b}.",
      extra_b=["Ousmane Sembène", "Souleymane Cissé", "Idrissa Ouédraogo", "Abderrahmane Sissako", "Mahamat-Saleh Haroun"])

# ------------------------------------------------------------------------------------------------ vie quotidienne et cuisine (suite)
rows("cm-culture2", "CM", "Gastronomie camerounaise", [
    (1, "Quel fruit proche de la banane se mange cuit, frit ou braisé au Cameroun ?", "Le plantain", ["La mangue", "La papaye", "L'ananas", "La goyave"], "Le plantain, riche en amidon, se prépare mûr ou vert."),
    (1, "De quel arbre provient l'huile de palme ?", "Le palmier à huile", ["L'olivier", "Le cocotier", "Le baobab", "Le karité"], "Le fruit du palmier à huile donne l'huile rouge très utilisée en cuisine."),
    (1, "Quel nom les anglophones camerounais donnent-ils aux beignets de pâte levée frits ?", "Puff-puff", ["Pancake", "Muffin", "Scone", "Waffle"], "Ces boules de pâte dorées se vendent dans la rue."),
    (2, "Qu'est-ce que le macabo ?", "Un tubercule cuit à l'eau ou pilé", ["Un poisson fumé", "Une épice", "Une boisson de palme", "Un fruit sucré"], "Le macabo se mange bouilli, avec une sauce ou pilé."),
    (2, "De quoi est faite la pâte appelée « fufu corn » ?", "De farine de maïs", ["De plantain vert", "De taro", "D'igname", "De riz"], "Le fufu de maïs accompagne des sauces de feuilles."),
    (3, "De quoi est fait le « water fufu » ?", "De manioc fermenté", ["De maïs frais", "De riz pilé", "De plantain vert", "De taro cuit"], "La pâte de manioc trempé se prépare en boule ou en pâte souple."),
    (2, "Dans quel ustensile pile-t-on le taro cuit pour préparer l'achu ?", "Un mortier en bois", ["Un moule à gâteau", "Une cocotte-minute", "Un four en terre", "Une presse à huile"], "Le taro est pilé jusqu'à obtenir une pâte lisse et élastique."),
    (2, "Quel nom donne-t-on à Yaoundé au bâton de manioc ?", "Le bobolo", ["Le koki", "Le soya", "Le kossam", "Le folléré"], "À Douala, on dit plutôt « miondo »."),
    (2, "Quelle noix, offerte en signe d'hospitalité, est mâchée en Afrique de l'Ouest et du Centre ?", "La noix de kola", ["La noix de coco", "La noix de cajou", "La noix de pécan", "La noix de muscade"], "On la partage lors de rencontres ou de cérémonies."),
    (2, "Comment prépare-t-on le « poisson braisé » camerounais ?", "Grillé sur la braise, souvent épicé", ["Cru, mariné au citron", "Séché au soleil", "Fumé pendant trois semaines", "Bouilli dans du lait"], "Il est vendu dans des maquis, avec du bâton de manioc ou du plantain."),
])
table("cm-culture2", "CM", "Gastronomie camerounaise",
      [("le koki", "le niébé", 2), ("l'achu", "le taro", 2), ("le ndolé", "les feuilles de ndolé", 2), ("l'okok", "les feuilles de gnetum", 3),
       ("le bâton de manioc", "le manioc fermenté", 2), ("le poulet DG", "le plantain mûr frit", 2), ("le sanga", "le maïs frais", 3), ("le mbongo tchobi", "les épices grillées", 3)],
      "Quel ingrédient est caractéristique de {a} ?", "Quel plat camerounais se caractérise par {b} ?", diff=2, expl="{a} se caractérise par {b}.",
      extra_b=["le riz blanc", "les lentilles", "le chou"])

# ------------------------------------------------------------------------------------------------ localités et patrimoine
table("cm-culture2", "CM", "Architecture et patrimoine",
      [("Foumban", "le palais du roi Njoya", 3), ("Bandjoun", "une chefferie à grande case et poteaux sculptés", 3), ("Bafut", "le palais Achum", 5),
       ("Rhumsiki", "des pitons volcaniques et un village du pays kapsiki", 4), ("Buea", "le départ de la Course de l'Espoir", 4)],
      "Pour quel élément de culture ou de patrimoine la localité de {a} est-elle connue ?", None, diff=3, expl="{a} est connue pour {b}.")
table("cm-culture2", "CM", "Peuples du Cameroun",
      [("les Bamoun", "Foumban", 2), ("les Nso", "Kumbo", 4), ("les Bafut", "Bafut", 4), ("les Bali Nyonga", "Bali", 5), ("les Douala", "Douala", 2)],
      "Quelle localité est traditionnellement associée à {a} ?", "Quel peuple est traditionnellement associé à {b} ?", diff=3,
      expl="{a} sont traditionnellement associés à {b}.", extra_b=["Maroua", "Ebolowa", "Bertoua"])

rows("cm-culture2", "CM", "Architecture et patrimoine", [
    (5, "En quelle année Diy-Gid-Biy a-t-il été inscrit au patrimoine mondial ?", "2024", ["2019", "2021", "2022", "2023"], "L'inscription a eu lieu lors de la session du Comité du patrimoine mondial tenue à New Delhi."),
    (4, "Dans quelle région administrative se trouve le paysage culturel de Diy-Gid-Biy ?", "L'Extrême-Nord", ["Le Nord", "L'Adamaoua", "Le Nord-Ouest", "L'Est"], "Il couvre des zones des monts Mandara."),
    (4, "Dans quelle région administrative se trouve le lamidat historique de Rey-Bouba ?", "Le Nord", ["L'Extrême-Nord", "L'Adamaoua", "L'Est", "L'Ouest"], "Rey-Bouba est un ancien État peul du Nord, dans le Mayo-Rey."),
    (5, "Que signifie le sigle SUD, triennale d'art contemporain née à Douala ?", "Salon urbain de Douala", ["Sommet urbain de Douala", "Salon universitaire de Douala", "Semaine urbaine du delta", "Symposium de l'union des dessinateurs"],
     "Ce rendez-vous est lié à l'action de Doual'art dans l'espace public."),
    (5, "Quelles trois grandes familles de langues africaines sont représentées au Cameroun ?", "Niger-Congo, afro-asiatique et nilo-saharienne",
     ["Niger-Congo, khoïsane et austronésienne", "Indo-européenne, sino-tibétaine et dravidienne", "Khoïsane, afro-asiatique et créole", "Nilo-saharienne, khoïsane et austronésienne"],
     "Cette diversité linguistique explique les quelque 250 langues du pays."),
])

# ------------------------------------------------------------------------------------------------ lettres et cinéma (suite)
rows("cm-culture2", "CM", "Littérature", [
    (5, "Quelle revue Mongo Beti a-t-il fondée en France en 1978 ?", "Peuples noirs-Peuples africains", ["Présence africaine", "Jeune Afrique", "Africultures", "Les Cahiers d'études africaines"], "Cette revue critique a paru jusqu'au début des années 1990."),
    (5, "Près de quelle ville Mongo Beti est-il né en 1932 ?", "Mbalmayo", ["Ebolowa", "Douala", "Sangmélima", "Bertoua"], "Alexandre Biyidi Awala est né à Akométam, près de Mbalmayo."),
    (4, "Quel est le premier roman de Léonora Miano, paru en 2005 ?", "L'Intérieur de la nuit", ["Contours du jour qui vient", "Les Aubes écarlates", "La Saison de l'ombre", "Blues pour Élise"], "Il a été suivi l'année suivante par « Contours du jour qui vient »."),
])
rows("cm-culture2", "CM", "Cinéma camerounais", [
    (5, "Que signifie « Muna Moto », titre duala du film de Dikongue-Pipa ?", "L'enfant d'un autre", ["La mère du village", "Le chemin du fleuve", "La nuit du roi", "La fille de l'eau"], "Le film raconte une histoire d'amour et de dot dans une société traditionnelle."),
])
rows("cm-culture2", "AF", "Cinéma africain", [
    (2, "Dans quelle ville se tient le FESPACO, grand festival du cinéma africain ?", "Ouagadougou", ["Yaoundé", "Dakar", "Abidjan", "Cotonou"], "Le Festival panafricain du cinéma et de la télévision de Ouagadougou décerne l'Étalon de Yennenga."),
])

# ------------------------------------------------------------------------------------------------ boissons, instruments, repères datés
table("cm-culture2", "CM", "Boissons et instruments",
      [("le matango", "la sève de palmier", 3), ("la bili-bili", "le sorgho", 3), ("le bissap (folléré)", "les fleurs d'hibiscus", 2), ("le kossam", "le lait caillé", 3),
       ("le jus de gingembre", "le gingembre", 1)],
      "Quelle est la matière première de {a} ?", "Quelle boisson est préparée à partir de {b} ?", diff=3, expl="{a} est préparé à partir de {b}.",
      extra_b=["le raisin", "la canne à sucre"])
table("cm-culture2", "CM", "Boissons et instruments",
      [("le balafon", "un xylophone à lames de bois", 2), ("le mvet", "une cithare-harpe", 4), ("le tam-tam d'appel", "un tambour de transmission de messages", 3)][1:],
      "De quel type d'instrument s'agit-il pour {a} ?", "Quel instrument est {b} ?", diff=3, expl="{a} est {b}.",
      extra_b=["une flûte de roseau", "un luth à manche court"], extra_a=["la kora", "le djembé", "le balafon"])
rows("cm-culture2", "CM", "Repères datés", [
    (3, "En quelle année est sorti le titre « Soul Makossa » de Manu Dibango ?", "1972", ["1965", "1968", "1975", "1980"], "Le morceau s'est diffusé aux États-Unis grâce aux discothèques."),
    (4, "En quelle année est né Manu Dibango ?", "1933", ["1929", "1936", "1940", "1943"], "Il est né à Douala puis a étudié en France."),
    (4, "En quelle année est paru « Le Fils d'Agatha Moudio » de Francis Bebey ?", "1967", ["1957", "1962", "1972", "1977"], "Le roman raconte l'histoire de Mbenda, un pêcheur du Littoral."),
    (5, "En quelle année Mongo Beti a-t-il publié « Remember Ruben » ?", "1974", ["1964", "1968", "1978", "1982"], "Ce roman évoque la lutte pour l'indépendance."),
    (5, "En quelle année est sorti le documentaire « Afrique, je te plumerai » de Jean-Marie Teno ?", "1992", ["1988", "1996", "2001", "1985"], "Le film interroge l'héritage colonial au Cameroun."),
    (3, "Quel jour célèbre-t-on la Journée internationale de la langue maternelle ?", "Le 21 février", ["Le 21 mars", "Le 8 mars", "Le 20 mai", "Le 5 octobre"], "Cette journée proclamée par l'UNESCO rappelle l'importance des langues locales."),
])
