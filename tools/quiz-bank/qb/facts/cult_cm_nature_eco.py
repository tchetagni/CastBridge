"""Cameroun : faune, flore, environnement et économie (faits stables, sans statistiques changeantes)."""
from .cult_kit import rows, table, theme

theme("cm-nat-eco", "Cameroun : faune, flore, environnement et économie",
      "Comparer avec les fiches de l'UICN, de l'UNESCO (Dja, Sangha Tri-National), du MINFOF, du MINADER et un atlas du Cameroun ; "
      "ne garder que des faits stables (pas de chiffres de production ni de classements).")

# ------------------------------------------------------------------------------------------------ faune
rows("cm-nat-eco", "CM", "Faune", [
    (1, "Quel est le plus grand animal terrestre que l'on peut croiser dans le parc de Waza ?", "L'éléphant de savane", ["Le gorille", "Le drill", "Le lamantin", "Le pangolin géant"], "Waza, dans la plaine du Logone, abrite des éléphants, des lions et des girafes."),
    (1, "Quel grand singe vit dans les forêts du Sud et de l'Est du Cameroun ?", "Le gorille de plaine", ["Le babouin des steppes", "L'orang-outan", "Le gibbon", "Le lémurien catta"], "Le gorille de plaine de l'Ouest vit dans les forêts denses du Sud et de l'Est ; l'orang-outan n'existe qu'en Asie."),
    (1, "Quel animal a le plus long cou parmi ceux du parc de Waza ?", "La girafe", ["L'éléphant", "Le lion", "L'hippopotame", "Le buffle"], "Les girafes du Nord du Cameroun vivent dans les savanes sèches."),
    (1, "Quel oiseau, grand imitateur de la voix humaine et à queue rouge, est très prisé dans les forêts d'Afrique centrale ?", "Le perroquet gris", ["Le toucan", "Le flamant rose", "Le pélican", "Le héron cendré"], "Le perroquet gris (jaco) est menacé par la capture et le commerce d'oiseaux."),
    (1, "Quel mammifère est le seul à être couvert d'écailles ?", "Le pangolin", ["Le tatou", "Le hérisson", "Le porc-épic", "Le fourmilier"], "Les écailles du pangolin sont en kératine, comme les ongles humains ; il se roule en boule pour se défendre."),
    (1, "Quel animal de grande taille aime passer ses journées dans l'eau des fleuves du Nord, comme la Bénoué ?", "L'hippopotame", ["Le lion", "La girafe", "Le chimpanzé", "Le buffle"], "L'hippopotame sort la nuit pour brouter l'herbe des berges."),
    (2, "Quel mammifère marin d'eau douce, appelé aussi « vache de mer », vit dans certains cours d'eau et lacs du Cameroun ?", "Le lamantin d'Afrique", ["Le dauphin de mer", "Le phoque", "La baleine bleue", "Le dugong"], "Le lamantin d'Afrique est herbivore et menacé ; le lac Ossa lui sert de refuge."),
    (2, "Quel singe au visage coloré, proche du mandrill, n'existe que dans une petite région comprenant le Sud-Ouest du Cameroun, le Nigeria et l'île de Bioko ?", "Le drill", ["Le babouin olive", "Le colobe bai", "Le cercopithèque ascagne", "Le singe vert"], "Le drill (Mandrillus leucophaeus) est l'un des primates les plus menacés d'Afrique."),
    (2, "Quel animal fait partie des « Big Five » et se rencontre dans les savanes du Nord du Cameroun ?", "Le lion", ["Le gorille", "Le drill", "Le lamantin", "Le pangolin"], "Le lion vit à Waza, à la Bénoué et à Bouba Ndjida."),
    (2, "Quelle est la plus grande grenouille du monde, que l'on trouve dans les rivières du Cameroun ?", "La grenouille goliath", ["La grenouille taureau", "La rainette verte", "Le crapaud buffle", "La grenouille rousse"], "La grenouille goliath (Conraua goliath) vit dans les cours d'eau rapides du Cameroun et de Guinée équatoriale."),
    (2, "Quel animal de forêt est connu pour creuser et fouir en mangeant fourmis et termites, et se trouve en grand danger à cause du braconnage ?", "Le pangolin géant", ["Le colobe", "L'hylochère", "Le potamochère", "Le céphalophe bleu"], "Les pangolins sont parmi les mammifères les plus braconnés au monde."),
    (3, "Comment s'appelle l'éléphant qui vit dans les forêts denses d'Afrique centrale, plus petit que celui de la savane ?", "L'éléphant de forêt", ["L'éléphant d'Asie", "Le mammouth", "L'éléphant de mer", "L'éléphant pygmée de Bornéo"], "L'éléphant de forêt (Loxodonta cyclotis) a des défenses plus droites et dirigées vers le bas."),
    (3, "Quelle sous-espèce de gorille se trouve à la frontière entre le Nigeria et le Cameroun, dans la région de Takamanda ?", "Le gorille de la Cross River", ["Le gorille des montagnes", "Le gorille de Grauer", "Le gorille de Bwindi", "Le gorille de Bornéo"], "Le gorille de la Cross River est l'un des grands singes les plus rares de la planète."),
    (3, "Quelle antilope géante au pelage roux rayé de blanc, appelée éland de Derby, se rencontre dans les parcs du Nord (Bénoué, Faro, Bouba Ndjida) ?", "L'éland de Derby", ["L'okapi", "Le bongo", "Le koudou", "Le gnou"], "L'éland de Derby est la plus grande antilope au monde."),
    (3, "Quelle girafe vit dans le Nord du Cameroun ?", "La girafe du Kordofan", ["La girafe masaï", "La girafe réticulée", "La girafe de Rothschild", "La girafe du Cap"], "Les girafes du Cameroun sont rattachées à la sous-espèce du Kordofan, vivant aussi au Tchad et au Soudan."),
    (3, "Quel parc du Cameroun est l'un des meilleurs endroits d'Afrique pour observer les « baïs », clairières où les animaux viennent chercher des minéraux ?", "Lobéké", ["Waza", "Faro", "Kalamaloué", "Mozogo-Gokoro"], "Les baïs de Lobéké attirent éléphants, gorilles, buffles et bongos."),
    (3, "Quel poisson d'eau douce très répandu au Cameroun est surnommé « silure » et vendu grillé ?", "Le poisson-chat", ["La sole", "Le thon", "Le requin", "La sardine"], "Le poisson-chat vit dans les fleuves et lacs du Cameroun."),
    (3, "Quel animal dangereux, transmettant la maladie du sommeil, vit surtout dans les forêts galeries d'Afrique centrale ?", "La mouche tsé-tsé", ["Le moustique anophèle", "Le criquet pèlerin", "La mouche domestique", "La puce chique"], "La mouche tsé-tsé transmet le trypanosome responsable de la maladie du sommeil."),
    (3, "Quel moustique transmet le paludisme ?", "L'anophèle femelle", ["Le culex mâle", "L'aedes mâle", "La mouche tsé-tsé", "Le phlébotome mâle"], "Seule l'anophèle femelle pique et transmet le Plasmodium."),
    (4, "En quelle année l'UICN a-t-elle déclaré éteint le rhinocéros noir de l'Ouest, dont les derniers individus vivaient au Cameroun ?", "2011", ["1995", "2001", "2018", "2005"], "La sous-espèce occidentale du rhinocéros noir a été déclarée éteinte en 2011 après une disparition causée par le braconnage."),
    (4, "Quel perroquet forestier, aussi appelé « Psittacus erithacus », est protégé par la CITES ?", "Le perroquet gris du Gabon", ["L'ara bleu", "La perruche à collier", "Le cacatoès", "Le lori"], "Il est classé en danger par l'UICN à cause du trafic d'oiseaux de cage."),
    (4, "Quelle sous-espèce de chimpanzé vit au Cameroun et dans l'Est du Nigeria ?", "Le chimpanzé du Nigeria-Cameroun", ["Le chimpanzé de l'Ouest", "Le chimpanzé de l'Est", "Le bonobo", "Le chimpanzé de Bornéo"], "Pan troglodytes ellioti est la sous-espèce « Nigeria-Cameroun »."),
    (4, "Quel oiseau endémique des montagnes du Cameroun, vert et rouge, vit autour du mont Oku ?", "Le touraco de Bannerman", ["Le calao à casque", "Le quetzal", "Le perroquet gris", "Le perroquet vert d'Asie"], "Le touraco de Bannerman est endémique des hauts plateaux de l'Ouest et du Nord-Ouest."),
    (4, "Quel rapace consomme essentiellement les fruits du palmier à huile, ce qui lui vaut son nom ?", "Le palmiste africain", ["Le milan noir", "Le faucon pèlerin", "L'aigle royal", "Le vautour fauve"], "Le palmiste africain, ou vautour des palmiers, se nourrit surtout des fruits du palmier à huile."),
    (4, "Quel arbre est la nourriture favorite du palmiste africain ?", "Le palmier à huile", ["Le baobab", "L'acajou", "Le moabi", "Le karité"], "L'oiseau est lié au palmier à huile, qu'il fréquente autour des plantations."),
    (4, "Quelle est la particularité du lac Barombi Mbo, dans le Sud-Ouest ?", "Il abrite des poissons endémiques", ["Il est salé comme la mer", "Il est situé dans le désert", "Il est un glacier", "Il est entièrement artificiel"], "Ce lac de cratère abrite des poissons cichlidés que l'on ne trouve nulle part ailleurs."),
    (5, "Quel est le nom scientifique du drill ?", "Mandrillus leucophaeus", ["Mandrillus sphinx", "Pan troglodytes", "Gorilla gorilla", "Papio anubis"], "Le mandrill est Mandrillus sphinx ; le drill est Mandrillus leucophaeus."),
    (5, "Quel est le nom scientifique du gorille de plaine de l'Ouest ?", "Gorilla gorilla gorilla", ["Gorilla beringei", "Pan paniscus", "Pongo pygmaeus", "Gorilla gorilla diehli"], "G. g. diehli désigne en revanche le gorille de la Cross River."),
    (5, "Quel grand mammifère africain aime les mares de la plaine du Logone, où il vit dans l'écosystème Waza-Logone ?", "Le cob de Buffon", ["Le tigre", "Le zèbre de Grévy", "Le lycaon d'Asie", "Le yak"], "Le cob de Buffon est une antilope fréquente en savane humide."),
])

# ------------------------------------------------------------------------------------------------ parcs et réserves
rows("cm-nat-eco", "CM", "Parcs et réserves", [
    (2, "Dans quelle région se situe le parc national de Waza ?", "L'Extrême-Nord", ["Le Sud-Ouest", "L'Est", "Le Littoral", "L'Ouest"], "Waza se trouve dans le département du Logone-et-Chari, en zone de savane sahélienne."),
    (2, "Dans quelle région se trouve le parc national de Korup, grande forêt tropicale humide ?", "Le Sud-Ouest", ["L'Extrême-Nord", "L'Adamaoua", "L'Est", "Le Nord"], "Korup se situe dans le Sud-Ouest, près de la frontière du Nigeria."),
    (3, "Quel parc national du Cameroun borde l'océan Atlantique et la frontière avec la Guinée équatoriale ?", "Campo-Ma'an", ["Waza", "Bénoué", "Lobéké", "Mozogo-Gokoro"], "Campo-Ma'an est situé dans la région du Sud, dans le département de l'Océan."),
    (3, "Dans quelle région se situe le parc de Lobéké ?", "L'Est", ["Le Nord", "L'Ouest", "Le Littoral", "Le Nord-Ouest"], "Lobéké se situe à l'extrême Sud-Est, au contact du Congo et de la Centrafrique."),
    (3, "Quel parc, créé en 1968 sur la rive de la Bénoué, porte le nom du fleuve qui le traverse ?", "Le parc national de la Bénoué", ["Le parc de Waza", "Le parc de Campo-Ma'an", "Le parc de Korup", "Le parc de la Mefou"], "Le parc national de la Bénoué est situé dans la région du Nord."),
    (3, "Quelle réserve camerounaise a été inscrite au patrimoine mondial de l'UNESCO dès 1987 ?", "La réserve de faune du Dja", ["Le parc de Waza", "Le parc de Korup", "Le parc de Campo-Ma'an", "Le parc de Bouba Ndjida"], "La réserve du Dja est bordée par la rivière du même nom, qui l'entoure en boucle."),
    (3, "Dans quelle région se situe Bouba Ndjida, parc connu pour ses éléphants et sa savane soudanienne ?", "Le Nord", ["Le Sud", "L'Est", "Le Sud-Ouest", "L'Ouest"], "Bouba Ndjida est situé au Nord, près de la frontière tchadienne."),
    (3, "Quel est le nom du site de la Sangha, partagé entre le Cameroun, la République centrafricaine et le Congo, inscrit à l'UNESCO en 2012 ?", "Le Sangha Tri-National", ["Le Triangle du Chari", "Le Trifinio", "Le W-Arly-Pendjari", "Le parc de l'Odzala"], "Le Sangha Tri-National associe Lobéké (Cameroun), Dzanga-Sangha (Centrafrique) et Nouabalé-Ndoki (Congo)."),
    (4, "Quel parc camerounais fait partie du Sangha Tri-National ?", "Lobéké", ["Waza", "Faro", "Campo-Ma'an", "Korup"], "Le parc de Lobéké en est la composante camerounaise."),
    (4, "Quel parc du Sud-Ouest abrite le gorille de la Cross River, à la frontière avec le Nigeria ?", "Takamanda", ["Waza", "Faro", "Lobéké", "Bénoué"], "Takamanda jouxte le Cross River National Park, côté Nigeria."),
    (4, "Quelle zone humide de l'Extrême-Nord est située dans la plaine d'inondation du Logone, connue pour ses « yaérés » ?", "Waza-Logone", ["Mbam-et-Djerem", "Boumba-Bek", "Nki", "Campo-Ma'an"], "Les yaérés sont des plaines inondées chaque saison des pluies ; ce sont des pâturages et des zones de pêche."),
    (4, "Quel parc national de l'Extrême-Nord se situe près de Kousséri, à proximité du fleuve Chari ?", "Kalamaloué", ["Bénoué", "Lobéké", "Mbam-et-Djerem", "Mpem-et-Djim"], "Kalamaloué est un petit parc de l'Extrême-Nord, proche du Chari."),
    (4, "Dans quelle région se trouve le parc national de Faro ?", "Le Nord", ["L'Est", "Le Sud", "Le Littoral", "L'Ouest"], "Le parc de Faro est l'un des trois grands parcs de la région du Nord, avec la Bénoué et Bouba Ndjida."),
    (4, "Quel statut Waza a-t-il reçu de l'UNESCO en 1979 ?", "Réserve de biosphère", ["Patrimoine culturel immatériel", "Ville créative", "Géoparc", "Site Ramsar uniquement"], "Waza est une réserve de biosphère de l'UNESCO depuis 1979."),
    (5, "Quel centre de Limbé accueille des primates sauvés du commerce de la viande de brousse ?", "Le Limbe Wildlife Centre", ["Le centre de Waza", "Le Mefou Park", "Le Musée de Bamoun", "Le Jardin de Bamenda"], "Le Limbe Wildlife Centre recueille des gorilles, chimpanzés et drills."),
    (5, "Quel sanctuaire de primates se trouve près de Yaoundé, dans le parc de Mefou ?", "Ape Action Africa", ["Ape Alliance Nord", "Jane Goodall Waza", "Chimp Haven Douala", "Gorilla Camp Dja"], "Ape Action Africa gère le centre de Mefou, qui accueille des primates orphelins."),
])

PARKS = [("Kalamaloué", "l'Extrême-Nord"), ("Mozogo-Gokoro", "l'Extrême-Nord"), ("Takamanda", "le Sud-Ouest"), ("Boumba-Bek", "l'Est"), ("Nki", "l'Est")]
table("cm-nat-eco", "CM", "Parcs et réserves", [(a, b, 3) for a, b in PARKS],
      "Dans quelle région se situe le parc national de {a} ?", None, diff=3, expl="{a} se situe dans {b}.",
      extra_b=("le Nord", "le Centre", "le Littoral", "l'Adamaoua"))

# ------------------------------------------------------------------------------------------------ géographie physique
rows("cm-nat-eco", "CM", "Environnement", [
    (1, "Quel est le plus haut volcan du Cameroun ?", "Le mont Cameroun", ["Le mont Oku", "Le mont Kupe", "Le mont Manengouba", "Le mont Bamboutos"], "Le mont Cameroun, dans le Sud-Ouest, est un volcan actif surnommé « Mongo ma Loba »."),
    (2, "Quel est le surnom du mont Cameroun en langue bakweri ?", "Le Char des dieux", ["La Tête du lion", "Le Toit du monde", "Le Père des eaux", "La Montagne bleue"], "« Mongo ma Loba » signifie « Char des dieux »."),
    (2, "Quel fleuve, entièrement camerounais, est le plus long du pays ?", "La Sanaga", ["Le Wouri", "Le Ntem", "Le Logone", "La Bénoué"], "La Sanaga naît de la réunion du Lom et du Djérem et se jette dans l'Atlantique."),
    (2, "Dans quel océan se jette le Wouri, à Douala ?", "L'océan Atlantique", ["L'océan Indien", "La mer Méditerranée", "L'océan Pacifique", "L'océan Arctique"], "Le Wouri forme un estuaire dans le golfe de Guinée."),
    (2, "Quel fleuve a donné son nom au « Rio dos Camarões » des Portugais ?", "Le Wouri", ["La Sanaga", "Le Nyong", "Le Mungo", "Le Ntem"], "Les Portugais ont appelé « Rio dos Camarões » (fleuve des crevettes) l'estuaire du Wouri."),
    (2, "Quelle est la saison où souffle le vent sec et poussiéreux venu du Sahara, surtout dans le Nord ?", "L'harmattan", ["La mousson", "Le sirocco", "Le blizzard", "Le mistral"], "L'harmattan souffle en saison sèche ; il apporte brume sèche et poussière."),
    (2, "Quelle expression résume la diversité des paysages et des climats du Cameroun ?", "L'Afrique en miniature", ["La Perle de l'Atlantique", "Le pays des mille collines", "Le grenier du monde", "Le toit de l'Afrique"], "Le Cameroun réunit côte, forêt, montagne, savane et semi-désert."),
    (3, "Quel plateau couvre la région de Ngaoundéré et sépare le Sud forestier du Nord sahélien ?", "Le plateau de l'Adamaoua", ["Le plateau de Mokolo", "Le plateau des Bamboutos", "Le plateau de Nkongsamba", "Le plateau de Mamfé"], "L'Adamaoua est un plateau de moyenne altitude, au climat plus frais, favorable à l'élevage."),
    (3, "Quelles montagnes de l'Extrême-Nord se prolongent jusqu'au Nigeria ?", "Les monts Mandara", ["Les monts Atlas", "Les monts Oku", "Les monts Bamenda", "Les monts Adamaoua"], "Les monts Mandara sont situés au sud-ouest du lac Tchad."),
    (3, "Quels sont les deux cratères voisins du mont Manengouba appelés localement ?", "Le mâle et la femelle", ["Le jour et la nuit", "Le feu et l'eau", "Le père et le fils", "Les jumeaux d'Akwa"], "Les deux lacs du Manengouba sont dits « Mâle » et « Femelle » ; il se situe dans le Littoral."),
    (3, "Dans quelle région se trouve Rhumsiki, village connu pour ses pitons rocheux volcaniques ?", "L'Extrême-Nord", ["Le Sud", "Le Littoral", "L'Est", "L'Ouest"], "Rhumsiki, près de Mokolo, est connu pour ses pitons volcaniques."),
    (3, "Quel lac situé près d'Édéa abrite une réserve de lamantins ?", "Le lac Ossa", ["Le lac Tchad", "Le lac Nyos", "Le lac Oku", "Le lac Bamendjing"], "Le lac Ossa fait partie de la réserve de faune du lac Ossa, créée pour protéger les lamantins."),
    (3, "Quel est le plus grand lac du Cameroun, qu'il partage avec d'autres pays ?", "Le lac Tchad", ["Le lac Nyos", "Le lac Ossa", "Le lac Bamendjing", "Le lac Lagdo"], "Le lac Tchad est partagé entre le Cameroun, le Tchad, le Nigeria et le Niger."),
    (3, "Quelle est la cause principale du recul du lac Tchad au XXᵉ siècle ?", "La sécheresse et les prélèvements d'eau", ["Le déplacement d'un volcan", "L'ouverture d'un canal vers le Nil", "Un tremblement de terre unique", "La fonte d'un glacier"], "Le lac Tchad a fortement diminué depuis les années 1960 : peu profond, il est très sensible aux sécheresses."),
    (3, "Quel phénomène a causé la catastrophe du lac Monoun en 1984 ?", "Une éruption limnique de gaz carbonique", ["Une éruption de lave", "Un tsunami", "Un séisme", "Une marée noire"], "Une libération de CO₂ dissous a tué des dizaines de personnes en 1984 ; Nyos a connu la même chose en 1986."),
    (3, "Quel fleuve forme la frontière entre le Cameroun et le Tchad dans l'Extrême-Nord, avant d'alimenter le lac Tchad ?", "Le Logone", ["Le Wouri", "La Sanaga", "Le Nyong", "Le Ntem"], "Le Logone et le Chari se rejoignent à N'Djamena."),
    (4, "Quelle localité du Sud-Ouest, au pied du mont Cameroun, est réputée parmi les plus arrosées d'Afrique ?", "Debundscha", ["Garoua", "Maroua", "Kousséri", "Ngaoundéré"], "Debundscha reçoit des pluies très abondantes ; l'air chargé d'humidité vient du golfe de Guinée."),
    (4, "Quelle rivière de l'Est entoure la réserve du Dja en grande boucle ?", "Le Dja", ["Le Lom", "Le Djérem", "Le Kadéï", "Le Mbam"], "Le Dja est un affluent de la Sangha, dans le bassin du Congo."),
    (4, "Quel fleuve du Cameroun se jette dans le Niger via le Nigeria ?", "La Bénoué", ["La Sanaga", "Le Logone", "Le Ntem", "Le Nyong"], "La Bénoué naît dans l'Adamaoua et traverse Garoua."),
    (4, "Quel est le nom du fleuve de la frontière Ouest du Cameroun, dans la région de Mamfé, qui rejoint la côte nigériane ?", "La Cross River", ["Le Wouri", "La Sanaga", "Le Logone", "La Bénoué"], "La Cross River prend sa source au Cameroun, traverse le Nigeria et se jette dans le golfe de Guinée."),
    (5, "Quelle chute célèbre près de Kribi se jette directement dans l'océan Atlantique ?", "Les chutes de la Lobé", ["Les chutes de la Métché", "Les chutes d'Ekom-Nkam", "Les chutes de Ouro Kessoum", "Les chutes de la Sanaga"], "La Lobé tombe en cascade sur la plage, près de Kribi."),
    (5, "Quelles chutes de la Nkam servirent de décor au film « Greystoke » ?", "Les chutes d'Ekom-Nkam", ["Les chutes de la Lobé", "Les chutes de la Métché", "Les chutes de Mbam", "Les chutes de Mapé"], "Les chutes d'Ekom se trouvent dans le Littoral, sur la Nkam."),
    (5, "Dans quelle ville se trouvent les chutes de la Métché, en région de l'Ouest ?", "Bafoussam", ["Garoua", "Kribi", "Bertoua", "Ebolowa"], "Les chutes de la Métché se trouvent à Bafoussam, chef-lieu de la région de l'Ouest."),
])

# ------------------------------------------------------------------------------------------------ flore
rows("cm-nat-eco", "CM", "Flore", [
    (1, "Quel grand arbre au tronc énorme, aux fruits en forme de pain de singe, pousse dans la savane du Nord du Cameroun ?", "Le baobab", ["Le cocotier", "Le sapin", "Le chêne", "Le bouleau"], "Le baobab stocke l'eau dans son tronc ; ses fruits sont appelés « pain de singe »."),
    (1, "Quel arbre donne les fèves servant à fabriquer le chocolat ?", "Le cacaoyer", ["Le caféier", "Le palmier", "L'hévéa", "Le bananier"], "Le cacaoyer produit des cabosses contenant des fèves, que l'on fait fermenter et sécher."),
    (1, "Quel arbre est exploité pour obtenir le caoutchouc naturel ?", "L'hévéa", ["Le cacaoyer", "Le baobab", "Le karité", "Le caféier"], "Le latex de l'hévéa s'écoule du tronc après une entaille appelée « saignée »."),
    (2, "Quel arbre du Nord produit une noix dont on tire un beurre utilisé en cuisine et en cosmétique ?", "Le karité", ["L'acajou", "Le moabi", "Le cacaoyer", "L'ébène"], "Le beurre de karité provient des amandes du fruit du karité des savanes."),
    (2, "Quel palmier fournit des fibres et une sève fermentée appelée « vin de palme » dans l'Ouest et le Nord-Ouest ?", "Le raphia", ["Le dattier", "Le cocotier", "Le bambou", "Le palmier à huile"], "Le vin de raphia est très répandu dans les Grassfields ; ses feuilles servent aussi à fabriquer des toits et des nattes."),
    (2, "Quel bois précieux très noir, utilisé en sculpture et instruments, pousse en forêt camerounaise ?", "L'ébène", ["Le sapelli", "Le bambou", "Le pin", "Le teck"], "L'ébène du Cameroun (Diospyros crassiflora) a un cœur noir très dur."),
    (2, "Quel arbre au bois rouge est surnommé « acajou d'Afrique » ?", "Le khaya", ["Le baobab", "Le karité", "Le bananier", "Le raphia"], "Les acajous d'Afrique (Khaya et Entandrophragma) sont exportés comme bois d'œuvre."),
    (3, "Quel grand arbre de la forêt camerounaise, au tronc droit, donne une huile et une écorce, et est très recherché pour son bois ?", "Le moabi", ["Le karité", "Le cacaoyer", "L'hévéa", "Le raphia"], "Le moabi (Baillonella toxisperma) est un géant dont les graines fournissent une huile végétale."),
    (3, "Quel arbre, très utilisé pour la menuiserie sous le nom commercial « ayous » ou « wawa », est exporté du Cameroun ?", "Le Triplochiton", ["Le moabi", "L'ébène", "Le baobab", "Le karité"], "L'ayous (Triplochiton scleroxylon) est un bois léger, très courant dans l'exportation camerounaise."),
    (3, "Quel est le nom commercial du bois d'Entandrophragma cylindricum, très exporté ?", "Le sapelli", ["L'azobé", "Le padouk", "L'ébène", "Le moabi"], "Le sapelli est un bois proche de l'acajou, abondant dans les forêts de l'Est."),
    (3, "Quel bois rouge très dur, utilisé pour les ouvrages portuaires, vient de Lophira alata ?", "L'azobé", ["L'ayous", "Le sapelli", "Le fromager", "Le raphia"], "L'azobé est très résistant à l'eau : on l'utilise pour les pieux et les ponts."),
    (3, "Quel est le nom de l'arbre dont les noix, appelées « noix de cola », sont consommées dans l'Ouest et le Nord du pays ?", "Le colatier", ["Le baobab", "Le karité", "Le moabi", "Le cacaoyer"], "La noix de kola est partagée comme signe d'hospitalité dans plusieurs sociétés ouest-africaines et camerounaises."),
    (3, "Quelle plante donne les graines de « njansang » utilisées dans la cuisine du Sud ?", "Ricinodendron heudelotii", ["Elaeis guineensis", "Theobroma cacao", "Coffea arabica", "Musa paradisiaca"], "Le njansang (ricinodendron) est une graine oléagineuse aux usages culinaires."),
    (4, "De quel continent est originaire le cacaoyer ?", "L'Amérique du Sud", ["L'Afrique de l'Ouest", "L'Asie", "L'Europe", "L'Océanie"], "Le cacaoyer vient d'Amérique ; il a été introduit en Afrique à l'époque coloniale."),
    (4, "De quel continent est originaire le palmier à huile ?", "L'Afrique", ["L'Amérique du Sud", "L'Asie du Sud-Est", "L'Europe", "L'Océanie"], "Elaeis guineensis est originaire d'Afrique de l'Ouest et centrale, avant d'être cultivé en Asie du Sud-Est."),
    (4, "Quelle plante est à l'origine du café robusta ?", "Coffea canephora", ["Coffea arabica", "Theobroma cacao", "Musa paradisiaca", "Elaeis guineensis"], "Le robusta est le nom courant de Coffea canephora."),
    (4, "De quel pays est originaire le café arabica ?", "L'Éthiopie", ["Le Brésil", "Le Cameroun", "Le Vietnam", "La Colombie"], "L'arabica est issu des montagnes d'Éthiopie ; il est cultivé au Cameroun à haute altitude."),
    (5, "Quel est le nom scientifique de l'ébène du Cameroun ?", "Diospyros crassiflora", ["Baillonella toxisperma", "Khaya ivorensis", "Triplochiton scleroxylon", "Lophira alata"], "Diospyros crassiflora est inscrit sur les listes de protection du commerce des espèces."),
    (5, "Quel est le nom scientifique du palmier à huile ?", "Elaeis guineensis", ["Cocos nucifera", "Phoenix dactylifera", "Raphia vinifera", "Hevea brasiliensis"], "Le nom Elaeis guineensis renvoie au golfe de Guinée."),
    (5, "Quel est le nom scientifique de l'hévéa ?", "Hevea brasiliensis", ["Elaeis guineensis", "Theobroma cacao", "Coffea canephora", "Vitellaria paradoxa"], "L'hévéa est originaire du bassin de l'Amazone."),
    (5, "Quel est le nom scientifique du karité ?", "Vitellaria paradoxa", ["Theobroma cacao", "Adansonia digitata", "Hevea brasiliensis", "Elaeis guineensis"], "Le karité est un arbre des savanes sèches de l'Afrique sahélienne."),
])

# ------------------------------------------------------------------------------------------------ zones agro-écologiques
rows("cm-nat-eco", "CM", "Zones agro-écologiques", [
    (2, "Combien de zones agro-écologiques distingue-t-on classiquement au Cameroun ?", "Cinq", ["Deux", "Trois", "Sept", "Neuf"], "Zone soudano-sahélienne, hautes savanes guinéennes, hauts plateaux de l'Ouest, forêt bimodale et forêt monomodale."),
    (3, "Dans quelle zone agro-écologique se trouvent l'Extrême-Nord et le Nord ?", "La zone soudano-sahélienne", ["La zone forestière monomodale", "La zone des hauts plateaux", "La zone forestière bimodale", "La zone côtière"], "Le climat y est chaud, avec une longue saison sèche."),
    (3, "Quelle région correspond à la zone des hautes savanes guinéennes ?", "L'Adamaoua", ["Le Littoral", "Le Sud", "Le Nord-Ouest", "L'Est"], "L'Adamaoua est une région de plateau, propice à l'élevage."),
    (3, "Quelle zone agro-écologique regroupe l'Ouest et le Nord-Ouest ?", "La zone des hauts plateaux", ["La zone soudano-sahélienne", "La zone forestière monomodale", "La zone côtière", "La zone des savanes guinéennes"], "L'altitude y rend le climat frais et favorise le café arabica, le maïs et le chou."),
    (4, "Quelles régions relèvent de la zone forestière à pluviométrie monomodale ?", "Le Littoral et le Sud-Ouest", ["Le Centre et le Sud", "Le Nord et l'Extrême-Nord", "L'Ouest et le Nord-Ouest", "L'Adamaoua et l'Est"], "Une seule longue saison des pluies marque cette zone, très humide."),
    (4, "Quelles régions relèvent de la zone forestière à pluviométrie bimodale ?", "Le Centre, le Sud et l'Est", ["Le Littoral et le Sud-Ouest", "Le Nord et l'Extrême-Nord", "L'Ouest et le Nord-Ouest", "L'Adamaoua et le Nord"], "Elle connaît deux saisons des pluies et deux saisons sèches."),
    (3, "Combien de saisons de pluies compte le climat équatorial de type guinéen du Sud (Centre, Sud, Est) ?", "Deux", ["Une", "Trois", "Quatre", "Aucune"], "Ce climat alterne deux saisons des pluies et deux saisons sèches."),
])

# ------------------------------------------------------------------------------------------------ agriculture
rows("cm-nat-eco", "CM", "Agriculture", [
    (1, "Quelle culture de rente est cultivée dans le Nord autour de Garoua et exportée sous forme de fibre ?", "Le coton", ["Le cacao", "Le café", "La banane", "L'hévéa"], "Le coton est la grande culture de rente du Nord et de l'Extrême-Nord."),
    (1, "Quel fruit est cultivé en grandes plantations dans le Moungo (Njombé-Penja) pour l'exportation ?", "La banane dessert", ["La pomme", "La poire", "Le raisin", "La cerise"], "Les bananeraies de Njombé-Penja et de Mbanga exportent vers l'Europe."),
    (1, "Quelle plante est la matière première du chocolat produit à partir du Cameroun ?", "Le cacao", ["Le coton", "Le mil", "La canne à sucre", "Le tabac"], "Le cacao fait vivre de nombreux petits planteurs du Centre, du Sud et du Sud-Ouest."),
    (1, "Quelle plante donne le sucre à la SOSUCAM de Mbandjock ?", "La canne à sucre", ["La betterave", "Le sorgho", "Le coton", "Le palmier"], "La canne à sucre est transformée sur place à Mbandjock."),
    (2, "Quel aliment de base est obtenu à partir de la racine du manioc ?", "Le bâton de manioc", ["Le pain de blé", "Le riz gluant", "Le couscous de mil", "La polenta de maïs"], "Le manioc est cultivé dans le Sud et le Centre ; ses racines et ses feuilles sont consommées."),
    (2, "Quel céréale est typique des plaines sèches du Nord et de l'Extrême-Nord, avec le mil ?", "Le sorgho", ["Le blé", "Le seigle", "L'avoine", "L'orge"], "Le sorgho et le mil sont les céréales du Nord ; le muskuwaari est un sorgho de saison sèche."),
    (2, "Quelle culture vivrière, riche en féculents, est appelée « plantain » dans le Sud ?", "La banane plantain", ["La banane dessert", "Le manioc", "La patate douce", "L'igname"], "La banane plantain se cuit : bouillie, frite ou grillée."),
    (2, "Dans quelle région cultive-t-on surtout le café arabica ?", "L'Ouest", ["L'Extrême-Nord", "Le Sud", "L'Est", "Le Littoral"], "L'arabica se plaît en altitude, dans l'Ouest et le Nord-Ouest."),
    (3, "Quelle région produit le plus de cacao au Cameroun ?", "Le Centre", ["Le Nord", "L'Extrême-Nord", "L'Adamaoua", "Le Nord-Ouest"], "Le Centre et le Sud forment le cœur de la cacaoculture camerounaise."),
    (3, "Dans quelles régions produit-on surtout du café robusta ?", "Le Littoral et le Sud-Ouest", ["L'Extrême-Nord et le Nord", "L'Ouest et le Nord-Ouest", "L'Adamaoua et l'Est", "Le Nord et l'Adamaoua"], "Le robusta pousse en plaine humide, notamment dans le Littoral et le Sud-Ouest."),
    (3, "Quelle région est la grande zone de production de pommes de terre, de maïs et de haricots, grâce à ses sols volcaniques ?", "L'Ouest", ["L'Extrême-Nord", "Le Littoral", "L'Est", "Le Sud"], "Les hauts plateaux de l'Ouest nourrissent une grande partie du pays en produits vivriers."),
    (3, "Où cultive-t-on surtout le thé au Cameroun ?", "À Ndu, dans le Nord-Ouest, et à Tole, près de Buea", ["À Garoua, dans le Nord", "À Kribi, dans le Sud", "À Maroua, dans l'Extrême-Nord", "À Bertoua, dans l'Est"], "Le thé se cultive en altitude : plantations de Ndu et de Tole."),
    (3, "Quelle région de l'Extrême-Nord est connue pour la culture du riz irrigué grâce à la SEMRY ?", "La plaine de Yagoua", ["La plaine de Garoua", "Le plateau de Ngaoundéré", "La plaine de Mamfé", "Le delta du Wouri"], "La SEMRY développe la riziculture autour de Yagoua et de Maga."),
    (3, "Dans quelle vallée du Nord-Ouest la culture de riz est-elle encadrée par l'UNVDA ?", "La plaine de Ndop", ["La plaine de Maga", "La plaine du Logone", "La plaine de Waza", "La plaine du Mbam"], "L'UNVDA (Upper Nun Valley Development Authority) intervient dans la plaine de Ndop."),
    (3, "Quel produit tire-t-on de l'hévéa exploité à Niété, dans le Sud ?", "Le caoutchouc naturel", ["Le sucre", "Le coton", "L'huile", "Le chocolat"], "HEVECAM exploite l'hévéa à Niété, près de Kribi."),
    (3, "Quelle région est surtout connue pour le maraîchage de l'oignon et du mil de saison sèche ?", "L'Extrême-Nord", ["Le Sud", "Le Littoral", "Le Sud-Ouest", "L'Est"], "L'oignon est une culture importante de l'Extrême-Nord (Mokolo, Mora)."),
    (4, "Quelle région est la principale zone d'élevage bovin du Cameroun ?", "L'Adamaoua", ["Le Littoral", "Le Sud", "Le Sud-Ouest", "L'Est"], "Les éleveurs mbororo y conduisent des zébus."),
    (4, "Quel zébu à bosse, adapté au climat de l'Adamaoua, est élevé par les Mbororo ?", "Le zébu goudali", ["Le zébu brahman", "La vache normande", "Le yak", "Le buffle d'eau"], "Le goudali et le zébu bororo sont typiques de l'élevage de l'Adamaoua."),
    (4, "Quelle société de l'État a été créée en 1947 pour exploiter des plantations dans le Sud-Ouest (hévéa, banane, palme, thé) ?", "La CDC", ["La SOSUCAM", "La SODECOTON", "La SEMRY", "La SABC"], "La Cameroon Development Corporation (CDC) a son siège à Bota, près de Limbé."),
    (4, "Quelle société exploite le palmier à huile à Dibombari, Mbongo et Kienké ?", "La SOCAPALM", ["La SEMRY", "La SODECOTON", "La SOSUCAM", "La CAMRAIL"], "SOCAPALM : Société camerounaise de palmeraies, créée en 1968."),
    (4, "Où se trouve le siège de la SOSUCAM, société sucrière du Cameroun ?", "Mbandjock", ["Garoua", "Bafoussam", "Limbé", "Douala"], "Les plantations de canne à sucre de Mbandjock sont dans la région du Centre."),
    (4, "Dans quelle région est cultivée l'hévéa de la société HEVECAM ?", "Le Sud", ["Le Nord", "L'Extrême-Nord", "L'Ouest", "L'Adamaoua"], "HEVECAM exploite l'hévéa à Niété."),
    (5, "Quel nom porte la culture de saison sèche du sorgho repiqué dans l'Extrême-Nord ?", "Le muskuwaari", ["Le yaéré", "Le koko", "Le gari", "Le bobolo"], "Le muskuwaari, sorgho de contre-saison, est repiqué dans les sols argileux inondables."),
])

# ------------------------------------------------------------------------------------------------ monnaie et institutions
rows("cm-nat-eco", "CM", "Monnaie et finances", [
    (1, "Quelle est la monnaie utilisée au Cameroun ?", "Le franc CFA", ["Le dollar", "L'euro", "Le naira", "Le rand"], "Au Cameroun, on utilise le franc CFA de la CEMAC (code XAF)."),
    (2, "Que signifie CEMAC ?", "Communauté économique et monétaire de l'Afrique centrale", ["Communauté économique des États du Maghreb", "Centre d'études du marché africain central", "Commission économique du monde africain", "Conférence des États miniers d'Afrique centrale"], "Six pays en sont membres, dont le Cameroun."),
    (2, "Quelle institution émet le franc CFA utilisé au Cameroun ?", "La BEAC", ["La BCEAO", "La Banque mondiale", "La Banque de France", "Le FMI"], "La Banque des États de l'Afrique centrale (BEAC) a son siège à Yaoundé."),
    (2, "Où se situe le siège de la BEAC ?", "À Yaoundé", ["À Douala", "À Libreville", "À Bangui", "À N'Djamena"], "La BEAC a été créée en 1972 ; son siège est à Yaoundé."),
    (3, "Quelle banque émet le franc CFA de l'Afrique de l'Ouest, à ne pas confondre avec celui du Cameroun ?", "La BCEAO", ["La BEAC", "La COBAC", "La BDEAC", "La BAD"], "La BCEAO siège à Dakar ; le Cameroun relève de la BEAC."),
    (3, "Quel est le code international de la monnaie du Cameroun ?", "XAF", ["XOF", "CMF", "FCM", "CAF"], "XAF désigne le franc CFA d'Afrique centrale ; XOF celui d'Afrique de l'Ouest."),
    (3, "Que signifie FCFA dans la zone CEMAC ?", "Franc de la Coopération financière en Afrique centrale", ["Franc des Colonies françaises d'Afrique", "Franc fédéral du Cameroun et d'Afrique", "Fonds commun des finances africaines", "Franc central de la francophonie"], "Il porte le nom de « Coopération financière en Afrique centrale » ; autrefois « colonies françaises d'Afrique »."),
    (3, "Quels pays partagent le franc CFA de la CEMAC avec le Cameroun ?", "Le Gabon et le Tchad", ["Le Sénégal et le Mali", "La Côte d'Ivoire et le Bénin", "Le Nigeria et le Ghana", "Le Maroc et la Tunisie"], "La CEMAC regroupe le Cameroun, la Centrafrique, le Congo, le Gabon, la Guinée équatoriale et le Tchad."),
    (3, "Quelle ville camerounaise abrite la Douala Stock Exchange (DSX) ?", "Douala", ["Yaoundé", "Kribi", "Bafoussam", "Garoua"], "La Douala Stock Exchange est une bourse des valeurs implantée dans la capitale économique."),
    (3, "Quelle est la parité fixe entre le franc CFA et l'euro ?", "1 euro vaut 655,957 FCFA", ["1 euro vaut 100 FCFA", "1 euro vaut 1 000 FCFA", "1 euro vaut 500 FCFA", "1 euro vaut 250 FCFA"], "La parité fixe est de 655,957 FCFA pour 1 euro."),
    (4, "Quelle institution de contrôle des banques de la CEMAC est connue sous le sigle COBAC ?", "La Commission bancaire de l'Afrique centrale", ["La Caisse d'obligations bancaires d'Afrique centrale", "La Conférence des banquiers d'Afrique centrale", "Le Comité de la bourse d'Afrique centrale", "La Chambre du commerce d'Afrique centrale"], "La COBAC contrôle les établissements bancaires de la CEMAC."),
    (4, "Quel est le siège de la CEMAC ?", "Bangui", ["Yaoundé", "Libreville", "N'Djamena", "Malabo"], "Le siège de la CEMAC est à Bangui, en République centrafricaine."),
    (4, "En quelle année la zone CFA a-t-elle fait l'objet d'une dévaluation de 50 % ?", "1994", ["1960", "1975", "1986", "2008"], "Le franc CFA a été dévalué de moitié en janvier 1994."),
    (4, "À quelle monnaie le franc CFA était-il auparavant lié, avant l'euro ?", "Le franc français", ["Le deutschmark", "La livre sterling", "Le dollar", "La lire"], "Le franc CFA était rattaché au franc français, puis à l'euro en 1999."),
    (5, "Quelle banque de développement régionale a son siège à Brazzaville et est connue sous le sigle BDEAC ?", "La Banque de développement des États de l'Afrique centrale", ["La Banque des dépôts de l'Est africain central", "La Banque diamantaire d'Afrique centrale", "La Banque de dépôt de la Communauté", "La Banque du Congo et de l'Afrique centrale"], "La BDEAC finance des projets d'intégration dans la CEMAC."),
])

# ------------------------------------------------------------------------------------------------ ressources naturelles
rows("cm-nat-eco", "CM", "Ressources", [
    (1, "Quelle matière première liquide est extraite des gisements offshore de Rio del Rey, au large du Sud-Ouest ?", "Le pétrole", ["Le lait", "L'huile de palme", "Le vin", "Le lait de coco"], "Le pétrole est exploité au large du Sud-Ouest depuis les années 1970."),
    (2, "Quelle ville du Sud-Ouest abrite la SONARA ?", "Limbé", ["Douala", "Kribi", "Garoua", "Bafoussam"], "La Société nationale de raffinage est à Limbé."),
    (2, "Quelle ressource est extraite dans l'Est du Cameroun, autour de Batouri et Bétaré-Oya ?", "L'or", ["Le pétrole", "Le sel", "Le charbon", "L'uranium"], "L'exploitation de l'or alluvionnaire est ancienne dans l'Est."),
    (2, "Quelle ressource du sous-sol camerounais sert à fabriquer l'aluminium ?", "La bauxite", ["Le fer", "L'or", "Le cuivre", "Le sel"], "Les gisements de bauxite se situent dans l'Adamaoua (Minim-Martap, Ngaoundal)."),
    (3, "Dans quelle région se trouvent les gisements de bauxite de Minim-Martap et Ngaoundal ?", "L'Adamaoua", ["Le Littoral", "Le Sud", "Le Nord-Ouest", "L'Extrême-Nord"], "Ces gisements ne sont pas encore pleinement exploités."),
    (3, "Dans quelle région est situé le gisement de fer de Mbalam, projet minier majeur ?", "L'Est", ["Le Nord", "L'Ouest", "Le Littoral", "Le Sud-Ouest"], "Le gisement de Mbalam-Nabeba s'étend à la frontière avec le Congo."),
    (3, "Quel minerai, très recherché pour les batteries, est exploité à Nkamouna, dans l'Est ?", "Le cobalt", ["Le diamant", "L'étain", "Le platine", "Le sel gemme"], "Le projet cobalt-nickel-manganèse de Nkamouna se trouve dans l'Est."),
    (3, "Quel terminal camerounais reçoit le pétrole tchadien par un oléoduc long de plus de 1 000 km ?", "Kribi", ["Douala", "Limbé", "Garoua", "Tiko"], "Le pipeline Tchad-Cameroun relie Doba, au Tchad, au terminal flottant de Kribi."),
    (3, "Quel gaz est exploité à Logbaba, à Douala, pour alimenter l'industrie ?", "Le gaz naturel", ["L'hélium", "Le butane importé", "Le méthane de marais", "L'oxygène"], "Le gaz de Logbaba alimente notamment la centrale à gaz de Douala."),
    (3, "Quelle société d'État gère les hydrocarbures au Cameroun ?", "La SNH", ["La SONARA", "ENEO", "CAMTEL", "SOCAPALM"], "La Société nationale des hydrocarbures représente l'État dans le secteur pétrolier."),
    (4, "En quelle année le pipeline Tchad-Cameroun a-t-il commencé à transporter du pétrole ?", "2003", ["1975", "1990", "1999", "2010"], "Le premier pétrole a été acheminé en 2003."),
    (4, "Quel navire flottant de liquéfaction de gaz est amarré au large de Kribi ?", "Le Hilli Episeyo", ["Le Titanic", "Le Saint-Louis", "Le Wouri Star", "Le Kribi Queen"], "Le FLNG Hilli Episeyo a commencé à exporter du gaz naturel liquéfié en 2018."),
    (4, "Quel bassin pétrolier côtier est exploité depuis longtemps dans le Sud-Ouest ?", "Rio del Rey", ["Mbalam", "Mozogo", "Logone", "Nkongsamba"], "Le bassin de Rio del Rey est situé près de la frontière nigériane."),
    (4, "Quelle région est le grand producteur de bois d'œuvre du Cameroun ?", "L'Est", ["Le Nord", "L'Extrême-Nord", "L'Ouest", "Le Nord-Ouest"], "L'Est regroupe de vastes forêts exploitées."),
    (5, "Quel gisement de cobalt-nickel-manganèse du Sud-Est du Cameroun est situé près de Lomié ?", "Nkamouna", ["Mbalam", "Bétaré-Oya", "Rio del Rey", "Ngaoundal"], "Le site de Nkamouna se trouve dans l'Est, au sud de Lomié."),
])

# ------------------------------------------------------------------------------------------------ énergie et industrie
rows("cm-nat-eco", "CM", "Énergie et industrie", [
    (2, "Sur quel fleuve se situe le barrage d'Edéa ?", "La Sanaga", ["Le Wouri", "La Bénoué", "Le Ntem", "Le Nyong"], "Edéa est le plus ancien barrage sur la Sanaga."),
    (2, "Quelle source d'énergie fournit l'essentiel de l'électricité du Cameroun ?", "L'hydroélectricité", ["L'énergie nucléaire", "L'énergie éolienne", "Le charbon", "La géothermie"], "Les barrages de la Sanaga produisent l'essentiel du courant."),
    (3, "Sur quel fleuve du Nord se trouve le barrage de Lagdo ?", "La Bénoué", ["La Sanaga", "Le Logone", "Le Ntem", "Le Wouri"], "Le barrage de Lagdo alimente en électricité le Nord et sert à l'irrigation."),
    (3, "Quelle usine d'Edéa transforme l'alumine en aluminium grâce à l'hydroélectricité ?", "Alucam", ["Cimencam", "Sonara", "Camrail", "Camair-Co"], "Alucam, à Edéa, utilise l'énergie de la Sanaga."),
    (3, "Quelle société transporte voyageurs et marchandises par train au Cameroun ?", "Camrail", ["Camair-Co", "Camtel", "Eneo", "Camwater"], "Camrail gère le chemin de fer depuis 1999."),
    (3, "Quelle société assure la distribution de l'électricité au Cameroun ?", "Eneo", ["Camtel", "Camwater", "SNH", "SONARA"], "ENEO a repris l'activité d'AES-SONEL en 2014."),
    (3, "Quelle société gère la distribution de l'eau potable en milieu urbain ?", "Camwater", ["Eneo", "Camtel", "Cimencam", "Camrail"], "La Cameroon Water Utilities Corporation s'occupe de l'eau potable."),
    (3, "Quelle société publique est liée aux télécommunications et à la fibre optique ?", "Camtel", ["Camrail", "Alucam", "Eneo", "Sonara"], "Camtel, Cameroon Telecommunications, est l'opérateur historique."),
    (3, "Quelle société aérienne nationale est connue sous le sigle Camair-Co ?", "Cameroon Airlines Corporation", ["Camerounaise des airs commerciaux", "Centre d'aviation de la mer", "Compagnie du Mfoundi", "Cameroun Air Courrier"], "Camair-Co a succédé à la compagnie Cameroon Airlines."),
    (4, "Quel barrage de la Sanaga, situé entre Edéa et Yaoundé, a été mis en service dans les années 1980 ?", "Song Loulou", ["Mapé", "Memve'ele", "Lagdo", "Nachtigal"], "Song Loulou est l'une des centrales de la Sanaga."),
    (4, "Quel barrage est construit sur le Ntem, dans le Sud du pays ?", "Memve'ele", ["Song Loulou", "Edéa", "Lagdo", "Mapé"], "Memve'ele est une centrale hydroélectrique du Sud."),
    (4, "Quel barrage-réservoir de la Sanaga, dans l'Est, régule le débit pour les centrales en aval ?", "Lom Pangar", ["Memve'ele", "Lagdo", "Mapé", "Edéa"], "Lom Pangar sert à stocker l'eau et à réguler la Sanaga."),
    (4, "Quelle entreprise de Douala produit la bière « 33 Export » ?", "SABC", ["CIMENCAM", "SONARA", "SODECOTON", "SOCAPALM"], "La Société anonyme des brasseries du Cameroun est une grande brasserie."),
    (4, "Quelle usine de Bonabéri, à Douala, est liée à la production de ciment ?", "Cimencam", ["Alucam", "Eneo", "Sonara", "Camtel"], "Les Cimenteries du Cameroun ont une usine à Douala-Bonabéri."),
    (5, "Quelle ville du Littoral abrite l'usine d'aluminium Alucam ?", "Edéa", ["Douala", "Nkongsamba", "Kribi", "Limbé"], "Alucam a été créée en 1957 à Edéa."),
    (5, "Quelle usine de textile est connue sous le sigle CICAM ?", "La Cotonnière industrielle du Cameroun", ["La Chimie industrielle du Cameroun", "La Centrale industrielle du Cameroun", "La Compagnie des cotons d'Afrique et de Madagascar", "La Confection industrielle de Cameroun"], "La Cicam transforme le coton en tissus."),
])

# ------------------------------------------------------------------------------------------------ ports et transports
rows("cm-nat-eco", "CM", "Ports et transports", [
    (1, "Quel est le principal port du Cameroun ?", "Douala", ["Garoua", "Yaoundé", "Kribi", "Bamenda"], "Le port autonome de Douala se situe sur l'estuaire du Wouri."),
    (2, "Quel port camerounais en eau profonde se situe dans le Sud ?", "Kribi", ["Douala", "Limbé", "Tiko", "Garoua"], "Le port en eau profonde de Kribi a été conçu pour accueillir de grands navires."),
    (2, "Quelle ville est le terminus du chemin de fer Transcamerounais ?", "Ngaoundéré", ["Garoua", "Maroua", "Bafoussam", "Bertoua"], "La voie ferrée relie Douala à Ngaoundéré en passant par Yaoundé."),
    (3, "Quel pays enclavé exporte ses marchandises par le port de Douala ?", "Le Tchad", ["Le Maroc", "L'Algérie", "Le Sénégal", "Le Ghana"], "Des camions et des trains relient Douala au Tchad et à la Centrafrique."),
    (3, "Quel aéroport international est situé à Nsimalen, près de Yaoundé ?", "Yaoundé-Nsimalen", ["Douala-Obala", "Garoua-Ouest", "Bamenda-Nord", "Bafoussam-Centre"], "Yaoundé-Nsimalen est l'aéroport international de la capitale politique."),
    (4, "Quel est le nom du grand port fluvial du Nord sur la Bénoué ?", "Garoua", ["Maroua", "Kousséri", "Bafia", "Mora"], "Le port fluvial de Garoua n'est navigable qu'en saison des pluies."),
    (4, "Quelle ville du Littoral est le grand centre industriel du pays ?", "Douala", ["Yaoundé", "Bertoua", "Maroua", "Ebolowa"], "La plupart des industries sont concentrées à Douala."),
])

# ------------------------------------------------------------------------------------------------ sigles d'entreprises et d'organismes
SIGLES = [
    ("SONARA", "Société nationale de raffinage", 3), ("SODECOTON", "Société de développement du coton", 3),
    ("SOCAPALM", "Société camerounaise de palmeraies", 4), ("SOSUCAM", "Société sucrière du Cameroun", 4),
    ("HEVECAM", "Hévéa du Cameroun", 4), ("SABC", "Société anonyme des brasseries du Cameroun", 4),
    ("SNH", "Société nationale des hydrocarbures", 3), ("CDC", "Cameroon Development Corporation", 4),
    ("SEMRY", "Société d'expansion et de modernisation de la riziculture de Yagoua", 5),
    ("UNVDA", "Upper Nun Valley Development Authority", 5), ("CAMWATER", "Cameroon Water Utilities Corporation", 4),
    ("CAMTEL", "Cameroon Telecommunications", 3), ("CIMENCAM", "Cimenteries du Cameroun", 4),
    ("BEAC", "Banque des États de l'Afrique centrale", 2), ("COBAC", "Commission bancaire de l'Afrique centrale", 4),
    ("MINADER", "Ministère de l'Agriculture et du Développement rural", 4), ("MINFOF", "Ministère des Forêts et de la Faune", 4),
]
table("cm-nat-eco", "CM", "Sigles et organismes", SIGLES, "Que signifie le sigle {a} ?", "Quel sigle correspond à « {b} » ?",
      diff=3, expl="{a} signifie {b}.")

ACTIVITES = [
    ("SONARA", "raffiner le pétrole à Limbé", 3), ("SODECOTON", "encadrer la culture du coton dans le Nord", 3),
    ("SOCAPALM", "exploiter des plantations de palmiers à huile", 4), ("SOSUCAM", "produire du sucre à Mbandjock", 4),
    ("HEVECAM", "exploiter l'hévéa à Niété", 4), ("CAMRAIL", "gérer le transport ferroviaire", 3),
    ("Alucam", "produire de l'aluminium à Édéa", 4), ("SNH", "représenter l'État dans le secteur des hydrocarbures", 4),
]
table("cm-nat-eco", "CM", "Sigles et organismes", ACTIVITES, "Quelle entreprise a pour activité de {b} ?", None, diff=3, expl="{a} : {b}.")

# ------------------------------------------------------------------------------------------------ élevage, pêche, forêt, environnement
rows("cm-nat-eco", "CM", "Élevage, pêche et forêt", [
    (1, "Quel animal d'élevage fournit le lait et la viande de bœuf dans l'Adamaoua ?", "Le zébu", ["Le chameau", "Le renne", "Le lama", "Le yak"], "Les troupeaux de zébus sont emmenés en transhumance."),
    (2, "Comment appelle-t-on le déplacement saisonnier des troupeaux à la recherche de pâturages ?", "La transhumance", ["La migration d'hiver", "La jachère", "La rotation", "L'irrigation"], "La transhumance suit la saison des pluies et la saison sèche."),
    (2, "Quel peuple d'éleveurs nomades est connu pour son troupeau de zébus dans l'Adamaoua et le Nord-Ouest ?", "Les Mbororo", ["Les Bassa", "Les Douala", "Les Bakweri", "Les Beti"], "Les Mbororo sont des éleveurs peuls, nombreux dans l'Adamaoua."),
    (2, "Quelle activité artisanale fournit des poissons séchés ou fumés dans les zones côtières comme Kribi ?", "La pêche", ["L'orpaillage", "La chasse au gros gibier", "L'élevage de zébus", "La sylviculture"], "La pêche artisanale est très développée à Kribi, à Limbé et à Douala."),
    (3, "Quel mot portugais signifiant « crevettes » est à l'origine du nom Cameroun ?", "Camarões", ["Calamares", "Caracóis", "Cavalos", "Cabras"], "Rio dos Camarões signifie « fleuve des crevettes »."),
    (3, "Quel massif forestier de l'Est et du Sud fait partie du bassin du Congo, deuxième massif tropical du monde ?", "La forêt du bassin du Congo", ["La forêt amazonienne", "La forêt de Bornéo", "La forêt noire", "La forêt boréale"], "Le Cameroun possède une portion du bassin du Congo."),
    (3, "Quel est le nom du ministère chargé de la protection des parcs nationaux du Cameroun ?", "Le MINFOF", ["Le MINADER", "Le MINEPIA", "Le MINEPAT", "Le MINTP"], "Le ministère des Forêts et de la Faune supervise les parcs et la faune."),
    (3, "Quel ministère s'occupe de l'élevage, des pêches et des industries animales ?", "Le MINEPIA", ["Le MINFOF", "Le MINADER", "Le MINEPAT", "Le MINTP"], "Le MINEPIA est chargé de l'élevage, des pêches et des industries animales."),
    (4, "Quelle convention internationale réglemente le commerce des espèces menacées comme le perroquet gris ?", "La CITES", ["La convention de Ramsar", "La convention de Genève", "Le protocole de Kyoto", "La convention de Bâle"], "La Convention de Washington (CITES) encadre ce commerce."),
    (4, "Quelle convention internationale protège les zones humides, comme celles de Waza-Logone ?", "La convention de Ramsar", ["La CITES", "La convention de Genève", "Le protocole de Kyoto", "La convention de Bâle"], "La convention de Ramsar de 1971 protège les zones humides d'importance internationale."),
    (4, "Quel genre de produit forestier, ni bois ni gibier, est ramassé en forêt pour la cuisine, comme les noisettes de « njansang » ?", "Un produit forestier non ligneux", ["Un produit minier", "Un produit pétrolier", "Un produit chimique", "Un produit laitier"], "Les produits forestiers non ligneux comprennent fruits, graines, miel et plantes médicinales."),
    (5, "Quel est le nom de la politique de lutte contre l'exploitation illégale du bois, menée avec l'Union européenne ?", "L'APV-FLEGT", ["Le NEPAD", "Le CORAF", "La COMIFAC", "L'OHADA"], "L'accord de partenariat volontaire FLEGT vise à contrôler la légalité du bois exporté."),
    (5, "Quelle organisation d'Afrique centrale coordonne la politique commune de gestion des forêts du bassin du Congo ?", "La COMIFAC", ["La CEMAC", "La CEDEAO", "L'OHADA", "La CEEAC"], "La Commission des forêts d'Afrique centrale a été créée en 2005."),
    (5, "Quelle organisation sous-régionale a son siège à Libreville et regroupe les pays d'Afrique centrale (CEEAC) ?", "La CEEAC", ["La CEMAC", "La CEDEAO", "L'UEMOA", "La SADC"], "La CEEAC regroupe des États d'Afrique centrale."),
])

# ------------------------------------------------------------------------------------------------ cultures et plats
rows("cm-nat-eco", "CM", "Cultures vivrières", [
    (1, "Quelle graine oléagineuse, très consommée en pâte, pousse dans le sol ?", "L'arachide", ["Le café", "Le cacao", "Le blé", "Le coton"], "L'arachide pousse sous terre et est cultivée dans le Nord."),
    (2, "Quel fruit, appelé « safou », est très apprécié au Cameroun et se mange cuit ?", "Le safoutier", ["Le manguier", "L'avocatier", "Le papayer", "Le goyavier"], "La safou ou « prune d'Afrique » est consommée bouillie ou grillée."),
    (2, "Quelle plante, dont les feuilles amères servent à faire le « ndolé », est cultivée dans le Littoral ?", "Le vernonia", ["Le manioc", "Le macabo", "Le gombo", "Le cacao"], "Les feuilles de ndolé sont cuites avec des arachides."),
    (2, "Quel légume vert, gluant en sauce, est apprécié dans les plats du Nord et du Centre ?", "Le gombo", ["La carotte", "Le poireau", "Le radis", "La betterave"], "Le gombo donne une sauce épaisse, très utilisée dans la cuisine."),
    (3, "Quelle céréale cultivée dans l'Ouest sert à préparer le « fufu » de maïs ?", "Le maïs", ["Le blé", "Le sorgho", "Le riz", "Le mil"], "Le fufu de maïs est servi avec une sauce."),
    (3, "Quelle céréale, très résistante à la sécheresse, est cultivée dans l'Extrême-Nord ?", "Le mil", ["Le blé", "Le seigle", "Le riz", "Le maïs"], "Le mil pousse dans les zones à faible pluviométrie."),
    (3, "Quel légume-feuille est très cultivé dans l'Ouest et appelé « jama-jama » ?", "La morelle", ["Le chou", "La salade", "Le persil", "L'oseille de Guinée"], "Le jama-jama est une préparation de feuilles de morelle noire."),
    (3, "Quelle céréale tropicale, riche en amidon, pousse dans les plaines inondées de Yagoua ?", "Le riz", ["Le blé", "L'orge", "L'avoine", "Le seigle"], "Le riz est cultivé en périmètres irrigués autour de Yagoua."),
    (4, "Quelle plante fournit les « pistaches » ou « egusi », graines de courge utilisées dans la cuisine ?", "Une courge", ["Un haricot", "Un cacaoyer", "Un palmier", "Un baobab"], "Les graines d'egusi servent à épaissir les sauces."),
])

# ------------------------------------------------------------------------------------------------ compléments
rows("cm-nat-eco", "CM", "Faune", [
    (1, "Quel est le plus grand mammifère terrestre vivant au Cameroun ?", "L'éléphant", ["La girafe", "Le gorille", "L'hippopotame", "Le buffle"], "L'éléphant de savane et l'éléphant de forêt sont les plus grands animaux terrestres du pays."),
    (1, "Quel est le plus grand des singes vivant dans les forêts du Cameroun ?", "Le gorille", ["Le chimpanzé", "Le drill", "Le babouin", "Le cercopithèque"], "Le gorille est le plus grand des primates actuels."),
])
rows("cm-nat-eco", "CM", "Flore", [
    (1, "Quelle plante fournit les grains torréfiés pour le café ?", "Le caféier", ["Le cacaoyer", "Le bananier", "Le baobab", "L'hévéa"], "Le café est cultivé dans l'Ouest (arabica) et dans le Littoral (robusta)."),
    (2, "Comment appelle-t-on le fruit du cacaoyer qui renferme les fèves ?", "La cabosse", ["Le régime", "La noix", "Le cône", "La gousse de vanille"], "La cabosse est ouverte à la machette ; les fèves sont ensuite mises à fermenter."),
    (3, "Pourquoi cultive-t-on souvent le cacaoyer à l'ombre d'autres arbres ?", "Parce qu'il craint le plein soleil", ["Parce qu'il pousse sous terre", "Parce qu'il vit dans l'eau", "Parce qu'il ne supporte pas la pluie", "Parce qu'il craint l'humidité"], "Le cacaoyer est un arbre de sous-bois, qui se développe mieux sous un couvert végétal."),
    (4, "Quel arbre géant d'Afrique centrale fournit le kapok, fibre légère utilisée pour le rembourrage ?", "Le fromager", ["Le moabi", "Le karité", "Le raphia", "Le baobab"], "Le kapok est la bourre qui entoure les graines du fromager (Ceiba pentandra)."),
    (4, "Quelle épice, la première d'Afrique à avoir obtenu une indication géographique protégée, est associée à Penja ?", "Le poivre de Penja", ["La cannelle de Penja", "Le gingembre de Penja", "La vanille de Penja", "Le clou de girofle de Penja"], "Le poivre de Penja, dans le Moungo, bénéficie d'une indication géographique protégée depuis 2013."),
    (5, "Quel jardin créé à l'époque allemande, à Limbé, est l'un des plus anciens jardins botaniques d'Afrique ?", "Le jardin botanique de Limbé", ["Le jardin botanique de Kribi", "Le jardin de Garoua", "Le jardin de Maroua", "Le jardin de Bamenda"], "Le jardin botanique de Limbé a été fondé en 1892 par Paul Preuss."),
])
rows("cm-nat-eco", "CM", "Environnement", [
    (1, "Quel océan borde la côte du Cameroun ?", "L'océan Atlantique", ["L'océan Indien", "L'océan Pacifique", "L'océan Arctique", "L'océan Austral"], "Le Cameroun donne sur le golfe de Guinée, dans l'Atlantique."),
    (3, "À quelle altitude à peu près culmine le mont Cameroun ?", "À plus de 4 000 mètres", ["À 1 500 mètres", "À 2 000 mètres", "À 3 000 mètres", "À 5 000 mètres"], "Le mont Cameroun dépasse 4 000 mètres, ce qui en fait l'un des plus hauts sommets d'Afrique de l'Ouest et du Centre."),
    (3, "Quel vent humide venu de l'océan apporte la saison des pluies dans le Sud et le Nord du Cameroun ?", "La mousson", ["L'harmattan", "Le mistral", "Le sirocco", "Le blizzard"], "La mousson de l'Atlantique apporte la pluie ; l'harmattan souffle, lui, en saison sèche."),
    (4, "Que désigne le mot « yaérés », dans l'Extrême-Nord ?", "Des plaines inondables du Logone", ["Des montagnes", "Des forêts denses", "Des plateaux volcaniques", "Des mangroves"], "Les yaérés sont inondés chaque année ; on y trouve pâturages, pêche et oiseaux d'eau."),
    (4, "Quel type de forêt côtière, avec des arbres aux racines aériennes, borde l'estuaire du Wouri ?", "La mangrove", ["La taïga", "La forêt de conifères", "La garrigue", "La savane"], "La mangrove du Wouri est un milieu protégé et riche en poissons."),
])
rows("cm-nat-eco", "CM", "Monnaie et finances", [
    (5, "En quelle année le franc CFA a-t-il été créé ?", "1945", ["1960", "1914", "1972", "1999"], "Le franc des colonies françaises d'Afrique a été créé le 26 décembre 1945."),
    (1, "Quelle ville est la capitale économique du Cameroun ?", "Douala", ["Yaoundé", "Bafoussam", "Garoua", "Ebolowa"], "Douala est la grande ville portuaire et commerciale du pays."),
])

ARBRES = [("le cotonnier", "le coton", 1), ("le cacaoyer", "les fèves de cacao", 1), ("l'hévéa", "le caoutchouc naturel", 2),
          ("le palmier à huile", "l'huile de palme", 1), ("le karité", "le beurre de karité", 2), ("le colatier", "la noix de cola", 3),
          ("le fromager", "le kapok", 4), ("la canne à sucre", "le sucre", 1), ("le caféier", "les grains de café", 1)]
table("cm-nat-eco", "CM", "Flore", ARBRES, "Quel produit tire-t-on principalement de {a} ?", "De quelle plante tire-t-on {b} ?", diff=2, expl="{a} fournit {b}.")

LIEUX = [("Mbandjock", "le sucre", 3), ("Niété", "le caoutchouc", 4), ("Yagoua", "le riz", 3), ("Bétaré-Oya", "l'or", 3),
         ("Mbalam", "le fer", 4), ("Logbaba", "le gaz naturel", 4), ("Minim-Martap", "la bauxite", 5), ("Penja", "le poivre", 4),
         ("Nkamouna", "le cobalt", 5)]
table("cm-nat-eco", "CM", "Ressources", LIEUX, "Quelle production ou ressource est associée à {a}, au Cameroun ?", None, diff=3, expl="{a} : {b}.")
