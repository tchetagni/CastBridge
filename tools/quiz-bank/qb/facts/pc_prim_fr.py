"""Primaire francophone (CP, CE1, CE2, CM1) : sciences d'observation, histoire-géographie, ECM. Faits écrits par l'assistant : tous `review`.
Programmes de référence : MINEDUB (primaire) ; à relire par un enseignant."""
from ..facts_engine import fq, source

source("pcp-sci", "Sciences d'observation, primaire (CP-CM1)", "programme scolaire", "Comparer avec le programme officiel MINEDUB (sections CP à CM1) et les manuels agréés de sciences d'observation.")
source("pcp-hg", "Histoire-géographie, primaire (CP-CM1)", "programme scolaire", "Comparer avec le programme MINEDUB, un atlas scolaire du Cameroun et les manuels agréés d'histoire et de géographie.")
source("pcp-ecm", "Éducation à la citoyenneté et à la morale (ECM), primaire (CP-CM1)", "programme scolaire", "Comparer avec le programme MINEDUB d'ECM, le code de la route camerounais (panneaux, feux) et la Convention relative aux droits de l'enfant.")

SCI, HG, ECM = "Sciences", "Histoire-géographie", "ECM"
CMR, AFR, WLD = "CM", "AF", "WORLD"


def add(course, tpl, rows, src, cat, region=WLD):
    for q, right, wr, expl, d in rows:
        fq(course, tpl, q, right, wr, expl, src, region, cat, d)


# ============================================================================================================ CP
add("cp", "pcp-cp-sci-corps", [
    ("Avec quoi voit-on ?", "Avec les yeux", ["Avec les oreilles", "Avec le nez", "Avec la langue"], "Les yeux servent à voir.", 1),
    ("Avec quoi entend-on les bruits ?", "Avec les oreilles", ["Avec les yeux", "Avec les mains", "Avec les pieds"], "Les oreilles servent à entendre.", 1),
    ("Avec quoi sent-on les odeurs ?", "Avec le nez", ["Avec les yeux", "Avec les oreilles", "Avec les genoux"], "Le nez sent les odeurs.", 1),
    ("Avec quoi goûte-t-on un fruit ?", "Avec la langue", ["Avec le nez", "Avec l'oreille", "Avec le doigt de pied"], "La langue reconnaît les goûts.", 1),
    ("Avec quoi sent-on le chaud et le froid ?", "Avec la peau", ["Avec les cheveux seulement", "Avec les dents", "Avec les ongles"], "La peau sert au toucher.", 2),
    ("Combien de doigts a une main ?", "5", ["4", "6", "3", "8"], "Une main a cinq doigts.", 1),
    ("Combien d'yeux avons-nous ?", "2", ["1", "3", "4", "5"], "Nous avons deux yeux.", 1),
    ("Combien d'oreilles avons-nous ?", "2", ["1", "3", "4", "5"], "Nous avons deux oreilles.", 1),
    ("Avec quoi marche-t-on ?", "Avec les jambes", ["Avec les oreilles", "Avec les yeux", "Avec les dents"], "Les jambes et les pieds nous font marcher.", 1),
    ("Avec quoi mâche-t-on les aliments ?", "Avec les dents", ["Avec les oreilles", "Avec les cheveux", "Avec les ongles"], "Les dents coupent et broient les aliments.", 1),
    ("Quel organe bat dans la poitrine ?", "Le cœur", ["Le nez", "Le genou", "L'oreille"], "Le cœur envoie le sang dans le corps.", 2),
    ("Que respire-t-on avec le nez ?", "L'air", ["L'eau", "Le sable", "Le pain"], "Nous respirons l'air.", 1),
], "pcp-sci", SCI)

add("cp", "pcp-cp-sci-sante", [
    ("Que doit-on boire quand on a soif ?", "De l'eau propre", ["De l'eau sale", "De l'eau de la mare", "De l'eau du caniveau"], "L'eau propre est bonne pour la santé.", 1),
    ("Avec quoi se brosse-t-on les dents ?", "Avec une brosse à dents", ["Avec un peigne", "Avec une cuillère", "Avec un balai"], "On se brosse les dents pour qu'elles restent saines.", 1),
    ("Avec quoi se lave-t-on le corps ?", "Avec de l'eau et du savon", ["Avec du sable", "Avec de la terre", "Avec de l'huile de moteur"], "L'eau et le savon enlèvent la saleté.", 1),
    ("Pourquoi se lave-t-on les mains avant de manger ?", "Pour retirer les microbes", ["Pour avoir plus faim", "Pour manger plus vite", "Pour avoir chaud"], "Les mains sales peuvent donner des maladies.", 1),
    ("Quand dort-on d'habitude ?", "La nuit", ["À midi", "Pendant la récréation", "Au marché"], "Dormir la nuit permet au corps de se reposer.", 1),
    ("Où soigne-t-on les malades ?", "À l'hôpital", ["Au marché", "À la gare", "Au stade"], "Les médecins et les infirmiers soignent les malades.", 1),
    ("Lequel de ces aliments est un fruit ?", "L'orange", ["Le riz", "Le pain", "Le poisson"], "L'orange est un fruit.", 1),
    ("La mangue est-elle un fruit, un animal, un outil ou une pierre ?", "Un fruit", ["Un animal", "Un outil", "Une pierre"], "On mange la mangue quand elle est mûre.", 1),
    ("Sous quoi dort-on pour éviter les piqûres de moustiques ?", "Sous une moustiquaire", ["Sous un seau", "Sous une chaise", "Sous un livre"], "La moustiquaire protège du paludisme.", 1),
], "pcp-sci", SCI)

add("cp", "pcp-cp-sci-animaux", [
    ("Quel animal fait « miaou » ?", "Le chat", ["Le chien", "La vache", "La poule"], "Le chat miaule.", 1),
    ("Quel animal fait « wouf » ?", "Le chien", ["Le chat", "Le mouton", "Le canard"], "Le chien aboie.", 1),
    ("Quel animal donne des œufs ?", "La poule", ["La chèvre", "La vache", "Le mouton"], "La poule pond des œufs.", 1),
    ("Quel animal a une trompe ?", "L'éléphant", ["Le lion", "Le singe", "Le serpent"], "L'éléphant se sert de sa trompe pour boire et prendre.", 1),
    ("Quel animal a un très long cou ?", "La girafe", ["Le chat", "La tortue", "Le cochon"], "La girafe mange les feuilles des arbres.", 1),
    ("Quel animal vit dans l'eau ?", "Le poisson", ["Le chat", "Le chien", "La poule"], "Le poisson nage dans l'eau.", 1),
    ("Quel animal vole avec ses ailes ?", "L'oiseau", ["Le serpent", "La tortue", "Le poisson"], "Les oiseaux ont des plumes et des ailes.", 1),
    ("Que donne la vache ?", "Du lait", ["Des œufs", "Du miel", "De la laine"], "On boit le lait de la vache.", 1),
    ("Que fabrique l'abeille ?", "Du miel", ["Du lait", "Du pain", "De l'huile"], "L'abeille fait le miel avec le nectar des fleurs.", 1),
    ("Quel animal rampe sans pattes ?", "Le serpent", ["Le chien", "Le lion", "Le singe"], "Le serpent n'a pas de pattes.", 1),
    ("Combien de pattes a un chien ?", "4", ["2", "6", "8"], "Le chien marche sur quatre pattes.", 1),
    ("Combien de pattes a une poule ?", "2", ["4", "6", "3"], "La poule a deux pattes.", 1),
    ("Que mange la chèvre ?", "De l'herbe", ["Des cailloux", "Du sable", "Du savon"], "La chèvre broute l'herbe et les feuilles.", 1),
    ("Quel animal donne de la laine ?", "Le mouton", ["La poule", "Le chat", "Le poisson"], "On fait la laine avec les poils du mouton.", 2),
], "pcp-sci", SCI)

add("cp", "pcp-cp-sci-nature", [
    ("Quelle partie de l'arbre est dans la terre ?", "Les racines", ["Les feuilles", "Les fruits", "Les branches"], "Les racines tiennent l'arbre dans le sol.", 1),
    ("De quelle couleur sont souvent les feuilles ?", "Vertes", ["Bleues", "Noires", "Roses"], "Les feuilles sont vertes à cause de la chlorophylle.", 1),
    ("Que faut-il à une plante pour grandir ?", "De l'eau et de la lumière", ["Du sel et du feu", "Du sable seul", "Du plastique"], "Sans eau ni lumière, la plante meurt.", 1),
    ("Que fait une graine quand on la plante et qu'on l'arrose ?", "Elle pousse", ["Elle fond", "Elle s'envole", "Elle devient un animal"], "La graine germe puis devient une plante.", 1),
    ("Que donne le Soleil ?", "De la lumière et de la chaleur", ["De la pluie", "Du vent froid", "De la neige"], "Le Soleil nous éclaire et nous réchauffe.", 1),
    ("Qu'est-ce qui brille dans le ciel la nuit ?", "La Lune et les étoiles", ["Le Soleil", "Un arc-en-ciel", "Les nuages de pluie"], "Le Soleil est visible le jour.", 1),
    ("D'où tombe la pluie ?", "Des nuages", ["Des arbres", "Des montagnes", "Des maisons"], "Les gouttes d'eau des nuages tombent en pluie.", 1),
    ("Que devient un glaçon au soleil ?", "Il fond", ["Il brûle", "Il s'envole", "Il grandit"], "La chaleur change la glace en eau.", 1),
    ("Quel animal est le plus grand sur la terre ferme ?", "L'éléphant", ["Le chat", "Le lion", "Le chien"], "L'éléphant est le plus grand animal terrestre.", 2),
], "pcp-sci", SCI)

add("cp", "pcp-cp-hg", [
    ("Comment s'appelle notre pays ?", "Le Cameroun", ["Le Gabon", "Le Tchad", "Le Nigeria"], "Le Cameroun est un pays d'Afrique.", 1),
    ("Quelle est la capitale du Cameroun ?", "Yaoundé", ["Douala", "Garoua", "Bamenda"], "Yaoundé est la capitale politique.", 1),
    ("Quelles sont les trois couleurs du drapeau du Cameroun ?", "Vert, rouge et jaune", ["Bleu, blanc et rouge", "Noir, blanc et vert", "Orange, bleu et jaune"], "Le drapeau a trois bandes verticales.", 1),
    ("De quelle couleur est l'étoile du drapeau du Cameroun ?", "Jaune", ["Rouge", "Verte", "Blanche"], "L'étoile jaune est au milieu de la bande rouge.", 1),
    ("Sur quelle bande est dessinée l'étoile du drapeau ?", "La bande rouge", ["La bande verte", "La bande jaune", "La bande bleue"], "La bande rouge est au centre.", 2),
    ("Sur quel continent se trouve le Cameroun ?", "L'Afrique", ["L'Europe", "L'Asie", "L'Amérique"], "Le Cameroun est en Afrique centrale.", 1),
    ("Quel moyen de transport roule sur des rails ?", "Le train", ["La pirogue", "L'avion", "Le vélo"], "Le train circule sur des rails.", 1),
    ("Quel moyen de transport vole dans le ciel ?", "L'avion", ["Le train", "La pirogue", "Le taxi"], "L'avion vole grâce à ses ailes.", 1),
    ("Quel moyen de transport glisse sur l'eau ?", "La pirogue", ["Le camion", "Le vélo", "Le train"], "On traverse les rivières en pirogue.", 1),
    ("Où achète-t-on des tomates et des légumes ?", "Au marché", ["À la mairie", "À l'hôpital", "À la gare"], "Au marché, on vend et on achète.", 1),
    ("Dans quoi habite une famille ?", "Dans une maison", ["Dans un avion", "Dans un camion", "Dans un train"], "La maison abrite la famille.", 1),
    ("Quelle saison apporte beaucoup de pluie ?", "La saison des pluies", ["La saison sèche", "L'été sans nuages", "La neige"], "Il pleut beaucoup pendant la saison des pluies.", 1),
    ("Quelles sont les deux langues officielles du Cameroun ?", "Le français et l'anglais", ["Le français et l'espagnol", "L'anglais et l'arabe", "Le français et le chinois"], "Ces deux langues sont officielles.", 2),
    ("Quel jour célèbre-t-on la fête nationale du Cameroun ?", "Le 20 mai", ["Le 1er janvier", "Le 25 décembre", "Le 8 mars"], "La fête nationale est le 20 mai.", 3),
    ("Quelle monnaie utilise-t-on au Cameroun ?", "Le franc CFA", ["Le dollar", "L'euro", "Le naira"], "Au Cameroun, on paie en francs CFA.", 3),
], "pcp-hg", HG, CMR)

add("cp", "pcp-cp-ecm", [
    ("Que dit-on quand on arrive le matin ?", "Bonjour", ["Au revoir", "Bonne nuit", "À demain"], "On salue poliment en arrivant.", 1),
    ("Que dit-on pour remercier quelqu'un ?", "Merci", ["Pardon", "Au revoir", "Bonne chance"], "Merci est un mot de politesse.", 1),
    ("Que dit-on quand on demande quelque chose poliment ?", "S'il vous plaît", ["Donne-moi vite", "Je veux", "Tais-toi"], "S'il vous plaît rend la demande polie.", 1),
    ("Que fait-on quand on bouscule un camarade sans le vouloir ?", "On dit pardon", ["On se moque", "On s'enfuit", "On crie"], "S'excuser montre le respect.", 1),
    ("Où jette-t-on les papiers sales ?", "À la poubelle", ["Dans la rue", "Dans la rivière", "Par terre"], "La poubelle garde la classe et la rue propres.", 1),
    ("Que veut dire le feu rouge ?", "Il faut s'arrêter", ["Il faut courir", "Il faut klaxonner", "Il faut sauter"], "Au feu rouge, tout le monde s'arrête.", 1),
    ("Où traverse-t-on la route ?", "Sur le passage pour piétons", ["Dans un virage", "Derrière un camion arrêté", "Au milieu de la route"], "Le passage piéton est fait pour traverser.", 1),
    ("Que regarde-t-on avant de traverser la rue ?", "À gauche et à droite", ["Le ciel", "Ses chaussures", "Les nuages"], "On vérifie qu'aucune voiture n'arrive.", 1),
    ("Qui aide à faire respecter les règles dans la rue ?", "Le policier", ["Le boulanger", "Le cuisinier", "Le maçon"], "Le policier surveille la circulation.", 2),
    ("Que fait-on quand le maître parle ?", "On écoute", ["On crie", "On court", "On dort"], "Écouter permet d'apprendre.", 1),
    ("Que fait-on pour poser une question en classe ?", "On lève le doigt", ["On crie fort", "On tape la table", "On sort de la classe"], "On attend son tour de parole.", 1),
    ("Comment parle-t-on aux personnes âgées ?", "Avec respect", ["Avec moquerie", "En criant", "En se fâchant"], "On respecte les plus âgés.", 1),
    ("Que fait un bon camarade ?", "Il partage et il aide", ["Il se moque", "Il frappe", "Il prend les affaires des autres"], "Aider les autres rend la classe agréable.", 1),
    ("Que fait-on d'un robinet ouvert quand on n'a plus besoin d'eau ?", "On le ferme", ["On le laisse couler", "On le casse", "On joue avec"], "L'eau est précieuse.", 1),
    ("Où les enfants apprennent-ils à lire ?", "À l'école", ["Au stade", "Au garage", "Au champ de manioc"], "L'école permet d'apprendre à lire et à écrire.", 1),
], "pcp-ecm", ECM, CMR)

# ============================================================================================================ CE1
add("ce1", "pcp-ce1-sci-corps", [
    ("Quel organe nous permet de respirer ?", "Les poumons", ["Le foie", "Les reins", "L'estomac"], "Les poumons se remplissent d'air à chaque inspiration.", 1),
    ("Quel organe pompe le sang dans tout le corps ?", "Le cœur", ["Le foie", "Les poumons", "L'estomac"], "Le cœur bat sans arrêt.", 1),
    ("Quelle partie du squelette protège le cerveau ?", "Le crâne", ["Le bassin", "Les côtes", "La colonne vertébrale"], "Le crâne est un os solide autour du cerveau.", 2),
    ("Combien de sens possède l'être humain ?", "Cinq", ["Trois", "Quatre", "Sept"], "La vue, l'ouïe, l'odorat, le goût et le toucher.", 1),
    ("Quel sens utilise-t-on surtout avec la peau ?", "Le toucher", ["La vue", "L'ouïe", "Le goût"], "La peau sent le chaud, le froid et la douceur.", 1),
    ("Quel gaz de l'air est nécessaire à la vie de notre corps ?", "L'oxygène", ["L'hélium", "Le méthane", "Le gaz carbonique"], "Les poumons absorbent l'oxygène de l'air.", 3),
    ("Quel organe reçoit les aliments avalés ?", "L'estomac", ["Les poumons", "Le cœur", "Le nez"], "L'estomac commence à digérer les aliments.", 2),
    ("Que protège la peau ?", "L'intérieur du corps", ["Seulement les os", "Seulement les cheveux", "Seulement les dents"], "La peau enveloppe et protège le corps.", 2),
], "pcp-sci", SCI)

add("ce1", "pcp-ce1-sci-sante", [
    ("Quand faut-il se brosser les dents ?", "Le matin et le soir", ["Une fois par an", "Seulement si on est malade", "Seulement à Noël"], "Se brosser les dents évite les caries.", 1),
    ("Pourquoi dort-on sous une moustiquaire ?", "Pour éviter les piqûres de moustiques", ["Pour avoir plus chaud", "Pour mieux voir", "Pour faire du bruit"], "Certains moustiques transmettent le paludisme.", 1),
    ("Quelle maladie certains moustiques peuvent-ils transmettre ?", "Le paludisme", ["La carie", "La fracture", "Le rhume des foins"], "Le paludisme est aussi appelé malaria.", 1),
    ("Comment rend-on l'eau plus sûre à boire à la maison ?", "En la faisant bouillir", ["En y ajoutant de la terre", "En la laissant dans la mare", "En la mélangeant à du sable"], "La chaleur détruit beaucoup de microbes.", 2),
    ("Que faut-il manger pour grandir en bonne santé ?", "Des aliments variés", ["Seulement des bonbons", "Seulement du sucre", "Rien le matin"], "Fruits, légumes, féculents et protéines sont utiles.", 1),
    ("Pourquoi se lave-t-on les mains avec du savon ?", "Pour enlever les microbes", ["Pour avoir les mains rouges", "Pour avoir plus faim", "Pour sécher plus vite"], "Les mains sales transmettent des maladies.", 1),
    ("Que faut-il faire des déchets de la maison ?", "Les mettre à la poubelle", ["Les jeter dans la rivière", "Les laisser dans la cour", "Les cacher sous le lit"], "Les déchets au sol attirent les mouches et les maladies.", 1),
], "pcp-sci", SCI)

add("ce1", "pcp-ce1-sci-animaux", [
    ("Comment s'appelle le petit de la poule ?", "Le poussin", ["Le chiot", "Le chaton", "L'agneau"], "Le poussin sort de l'œuf.", 1),
    ("Comment s'appelle le petit de la vache ?", "Le veau", ["L'agneau", "Le poulain", "Le chevreau"], "Le veau boit le lait de sa mère.", 1),
    ("Comment s'appelle le petit du mouton ?", "L'agneau", ["Le veau", "Le poussin", "Le chiot"], "L'agneau est le petit de la brebis.", 1),
    ("Comment s'appelle le petit de la chèvre ?", "Le chevreau", ["Le poulain", "Le veau", "Le chaton"], "Le chevreau est le petit de la chèvre.", 2),
    ("Comment s'appelle le petit du chien ?", "Le chiot", ["Le chaton", "Le veau", "Le poussin"], "Le chiot est le petit du chien.", 1),
    ("Combien de pattes a un insecte ?", "6", ["4", "8", "10"], "Un insecte a six pattes.", 2),
    ("Combien de pattes a une araignée ?", "8", ["6", "4", "10"], "L'araignée a huit pattes ; ce n'est pas un insecte.", 3),
    ("Lequel de ces animaux est un insecte ?", "La fourmi", ["L'araignée", "Le ver de terre", "L'escargot"], "La fourmi a six pattes et trois parties du corps.", 2),
    ("Par quoi le corps des oiseaux est-il couvert ?", "De plumes", ["De poils", "D'écailles", "De laine"], "Les plumes permettent à beaucoup d'oiseaux de voler.", 1),
    ("Quel animal allaite ses petits ?", "La vache", ["La poule", "Le serpent", "Le poisson"], "La vache est un mammifère.", 2),
    ("En quel animal une chenille se transforme-t-elle ?", "Un papillon", ["Une abeille", "Une fourmi", "Un criquet"], "La chenille devient un papillon après la chrysalide.", 2),
    ("Lequel de ces animaux mange de la viande ?", "Le lion", ["La vache", "La chèvre", "Le mouton"], "Le lion est un carnivore.", 1),
    ("Lequel de ces animaux mange seulement des plantes ?", "L'éléphant", ["Le lion", "Le crocodile", "Le serpent"], "L'éléphant est un herbivore.", 1),
    ("Quel animal respire sous l'eau avec des branchies ?", "Le poisson", ["Le chien", "Le singe", "Le chat"], "Les branchies prennent l'oxygène de l'eau.", 2),
], "pcp-sci", SCI)

add("ce1", "pcp-ce1-sci-nature", [
    ("Quelle partie de la plante est souvent colorée et parfumée ?", "La fleur", ["La racine", "La tige", "L'écorce"], "La fleur attire les insectes.", 2),
    ("Que mange-t-on quand on mange une carotte ?", "La racine", ["La feuille", "La fleur", "La graine"], "La carotte est la racine d'une plante.", 2),
    ("De quoi une graine a-t-elle besoin pour germer ?", "D'eau, d'air et de chaleur", ["De bruit et de vent", "De glace et de sel", "De fer et d'huile"], "La graine germe quand les conditions sont bonnes.", 2),
    ("Que devient l'eau quand elle gèle ?", "De la glace", ["De la vapeur", "Du sable", "De l'huile"], "L'eau gèle à 0 °C.", 1),
    ("Que voit-on s'échapper d'une casserole d'eau qui bout ?", "De la vapeur", ["De la glace", "De la poussière", "De la mousse"], "L'eau chaude se transforme en vapeur.", 2),
    ("Dans quel état est l'eau d'une rivière ?", "Liquide", ["Solide", "Gazeux", "En poudre"], "L'eau coule : elle est liquide.", 1),
    ("Quel objet flotte sur l'eau ?", "Un bouchon de liège", ["Une pierre", "Une clé en fer", "Une pièce de monnaie"], "Le liège est très léger.", 2),
    ("Quel objet est attiré par un aimant ?", "Un clou en fer", ["Un morceau de bois", "Un verre", "Une feuille de papier"], "L'aimant attire le fer.", 2),
    ("Qu'est-ce que le Soleil ?", "Une étoile", ["Une planète", "Un nuage", "Un satellite"], "Le Soleil est l'étoile la plus proche de la Terre.", 3),
    ("Quel astre tourne autour de la Terre ?", "La Lune", ["Le Soleil", "Mars", "Vénus"], "La Lune est le satellite naturel de la Terre.", 2),
    ("Quel instrument mesure la température ?", "Le thermomètre", ["La balance", "La règle", "Le sablier"], "Le thermomètre indique le chaud et le froid.", 1),
    ("Quel instrument mesure la masse d'un objet ?", "La balance", ["Le thermomètre", "La règle", "Le compas"], "La balance donne la masse en kilogrammes.", 1),
], "pcp-sci", SCI)

add("ce1", "pcp-ce1-hg-cm", [
    ("Quelle est la capitale politique du Cameroun ?", "Yaoundé", ["Douala", "Ebolowa", "Bafoussam"], "Yaoundé est la capitale politique.", 1),
    ("Quelle est la grande ville portuaire et économique du Cameroun ?", "Douala", ["Maroua", "Bertoua", "Ngaoundéré"], "Douala est la capitale économique.", 1),
    ("Combien de régions compte le Cameroun ?", "10", ["8", "12", "6"], "Le Cameroun est divisé en dix régions.", 2),
    ("Quel est le chef-lieu de la région de l'Ouest ?", "Bafoussam", ["Bamenda", "Dschang", "Foumban"], "Bafoussam est le chef-lieu de l'Ouest.", 2),
    ("Quel est le chef-lieu de la région du Littoral ?", "Douala", ["Édéa", "Limbé", "Kribi"], "Douala est le chef-lieu du Littoral.", 2),
    ("Quel est le chef-lieu de la région de l'Extrême-Nord ?", "Maroua", ["Garoua", "Mokolo", "Kousséri"], "Maroua est le chef-lieu de l'Extrême-Nord.", 2),
    ("Quel est le chef-lieu de la région du Nord ?", "Garoua", ["Maroua", "Poli", "Guider"], "Garoua est le chef-lieu du Nord.", 2),
    ("Quel est le chef-lieu de la région de l'Adamaoua ?", "Ngaoundéré", ["Garoua", "Meiganga", "Tibati"], "Ngaoundéré est le chef-lieu de l'Adamaoua.", 2),
    ("Quel est le chef-lieu de la région de l'Est ?", "Bertoua", ["Batouri", "Yokadouma", "Abong-Mbang"], "Bertoua est le chef-lieu de l'Est.", 2),
    ("Quel est le chef-lieu de la région du Sud ?", "Ebolowa", ["Kribi", "Ambam", "Sangmélima"], "Ebolowa est le chef-lieu du Sud.", 2),
    ("Quel est le chef-lieu de la région du Sud-Ouest ?", "Buea", ["Limbé", "Kumba", "Mamfe"], "Buea est le chef-lieu du Sud-Ouest.", 3),
    ("Quel est le chef-lieu de la région du Nord-Ouest ?", "Bamenda", ["Kumbo", "Wum", "Ndop"], "Bamenda est le chef-lieu du Nord-Ouest.", 3),
    ("Quel océan borde le Cameroun au sud-ouest ?", "L'océan Atlantique", ["L'océan Indien", "L'océan Pacifique", "La mer Rouge"], "Le littoral camerounais est sur l'Atlantique.", 2),
    ("Quel pays est voisin du Cameroun à l'ouest ?", "Le Nigeria", ["Le Gabon", "Le Tchad", "Le Congo"], "Le Nigeria est à l'ouest du Cameroun.", 2),
    ("Quel pays est voisin du Cameroun au nord-est ?", "Le Tchad", ["Le Gabon", "Le Niger", "Le Ghana"], "Le Tchad borde le Cameroun au nord-est.", 2),
    ("Quel pays n'est pas voisin du Cameroun ?", "Le Sénégal", ["Le Gabon", "Le Tchad", "Le Congo"], "Le Sénégal est en Afrique de l'Ouest, loin du Cameroun.", 3),
    ("Quel est le plus haut sommet du Cameroun ?", "Le mont Cameroun", ["Le mont Kenya", "Le mont Blanc", "Le Kilimandjaro"], "Le mont Cameroun est un volcan près de Buea.", 2),
    ("Quelle culture donne le chocolat ?", "Le cacao", ["Le coton", "Le mil", "L'arachide"], "On cultive le cacao surtout dans le Centre, le Sud et le Sud-Ouest.", 2),
    ("Quelle culture est typique du Nord, autour de Garoua ?", "Le coton", ["Le cacao", "La banane plantain", "L'hévéa"], "Le coton est une grande culture du Nord.", 3),
    ("Quel fleuve traverse la ville de Douala ?", "Le Wouri", ["Le Nil", "Le Niger", "La Seine"], "Le Wouri se jette dans l'océan à Douala.", 3),
    ("Quelle est la devise du Cameroun ?", "Paix, Travail, Patrie", ["Liberté, Égalité, Fraternité", "Unité, Travail, Progrès", "Dieu, Patrie, Roi"], "La devise tient en trois mots.", 2),
    ("Combien de bandes verticales a le drapeau du Cameroun ?", "Trois", ["Deux", "Quatre", "Cinq"], "Vert, rouge et jaune, avec une étoile jaune.", 1),
    ("Quel jour célèbre-t-on la fête de la Jeunesse ?", "Le 11 février", ["Le 20 mai", "Le 1er mai", "Le 8 mars"], "La fête de la Jeunesse a lieu le 11 février.", 2),
    ("Quel jour célèbre-t-on la fête du Travail ?", "Le 1er mai", ["Le 20 mai", "Le 11 février", "Le 8 mars"], "Le 1er mai est la fête du Travail.", 2),
    ("Dans quelle région se trouve la ville de Kribi ?", "Dans la région du Sud", ["Dans l'Extrême-Nord", "Dans l'Adamaoua", "Dans la région de l'Est"], "Kribi est une ville côtière du Sud.", 3),
], "pcp-hg", HG, CMR)

add("ce1", "pcp-ce1-hg-monde", [
    ("Quel est le plus grand continent du monde ?", "L'Asie", ["L'Europe", "L'Afrique", "L'Océanie"], "L'Asie est le plus grand continent.", 3),
    ("Sur quel continent se trouve la France ?", "L'Europe", ["L'Afrique", "L'Asie", "L'Amérique"], "La France est en Europe.", 2),
    ("Quelle est la capitale de la France ?", "Paris", ["Londres", "Rome", "Madrid"], "Paris est sur la Seine.", 1),
    ("Quelle est la capitale du Gabon ?", "Libreville", ["Brazzaville", "Bangui", "Abuja"], "Libreville est la capitale du Gabon.", 3),
    ("Quelle est la capitale du Nigeria ?", "Abuja", ["Lagos", "Accra", "Dakar"], "Abuja est la capitale du Nigeria, Lagos est sa plus grande ville.", 4),
    ("Quelle est la capitale du Tchad ?", "N'Djamena", ["Bangui", "Niamey", "Bamako"], "N'Djamena est la capitale du Tchad.", 4),
    ("Quel grand désert se trouve au nord de l'Afrique ?", "Le Sahara", ["Le Kalahari", "Le Gobi", "L'Arctique"], "Le Sahara est le plus grand désert chaud du monde.", 3),
    ("Quel fleuve traverse l'Égypte ?", "Le Nil", ["Le Congo", "Le Niger", "L'Amazone"], "Le Nil traverse l'Égypte jusqu'à la mer Méditerranée.", 3),
    ("Quel océan sépare l'Afrique de l'Amérique ?", "L'océan Atlantique", ["L'océan Indien", "L'océan Pacifique", "L'océan Arctique"], "L'Atlantique est entre l'Afrique et l'Amérique.", 3),
], "pcp-hg", HG, WLD)

add("ce1", "pcp-ce1-hg-histoire", [
    ("Quel est le premier président du Cameroun indépendant ?", "Ahmadou Ahidjo", ["Ruben Um Nyobè", "Charles de Gaulle", "Nelson Mandela"], "Ahmadou Ahidjo dirige le pays à l'indépendance.", 3),
    ("Comment les premiers hommes se nourrissaient-ils ?", "Par la chasse et la cueillette", ["Avec des supermarchés", "Avec des tracteurs", "Avec des fermes industrielles"], "Ils chassaient, pêchaient et ramassaient des fruits.", 3),
], "pcp-hg", HG, WLD)

add("ce1", "pcp-ce1-ecm", [
    ("Que signifie le feu rouge pour les voitures ?", "Elles doivent s'arrêter", ["Elles doivent accélérer", "Elles peuvent doubler", "Elles doivent klaxonner"], "Le rouge impose l'arrêt.", 1),
    ("Que signifie le feu orange ?", "Il faut se préparer à s'arrêter", ["Il faut accélérer", "Il faut faire demi-tour", "Il faut klaxonner"], "L'orange annonce le rouge.", 2),
    ("Où doivent marcher les piétons ?", "Sur le trottoir", ["Au milieu de la route", "Sur la bande du taxi", "Sur les rails"], "Le trottoir protège les piétons.", 1),
    ("Que doit porter un motocycliste pour protéger sa tête ?", "Un casque", ["Un chapeau", "Un bonnet", "Une casquette"], "Le casque évite des blessures graves.", 1),
    ("Qu'attache-t-on dans une voiture pour être en sécurité ?", "La ceinture de sécurité", ["La ceinture du pantalon", "Le sac", "Les lacets"], "La ceinture limite les blessures en cas de choc.", 1),
    ("Quel droit ont tous les enfants ?", "Aller à l'école", ["Conduire une voiture", "Voter", "Travailler la nuit"], "L'éducation est un droit de l'enfant.", 2),
    ("Quel document prouve la naissance d'un enfant ?", "L'acte de naissance", ["Le carnet de notes", "Le ticket de bus", "Le bulletin de pesée"], "L'acte de naissance donne un nom et une identité légale.", 3),
    ("Qui apprend aux élèves à lire et à écrire ?", "Le maître ou la maîtresse", ["Le gardien", "Le vendeur", "Le chauffeur"], "L'enseignant prépare et dirige les leçons.", 1),
    ("Qui est le chef de l'école ?", "Le directeur ou la directrice", ["Le gardien", "Un élève", "Le vendeur de beignets"], "Le directeur dirige l'école.", 1),
    ("Que fait-on quand l'hymne national est joué ?", "On se tient debout en silence", ["On court", "On bavarde", "On s'assoit"], "On montre ainsi son respect pour la patrie.", 2),
    ("Quel objet est un symbole de la République ?", "Le drapeau", ["Le ballon", "Le cartable", "Le tableau"], "Le drapeau, l'hymne et la devise sont des symboles.", 2),
    ("Que doit faire un élève avec le matériel de l'école ?", "En prendre soin", ["Le casser", "Le vendre", "Le cacher"], "Le matériel appartient à tous.", 1),
    ("Que fait-on en arrivant en retard en classe ?", "S'excuser poliment", ["Faire du bruit", "Se moquer", "Repartir sans rien dire"], "La politesse s'applique aussi au retard.", 2),
    ("Quel geste garde l'école propre ?", "Mettre les papiers à la poubelle", ["Jeter les papiers par terre", "Écrire sur les murs", "Casser les bancs"], "Chacun doit respecter la propreté des lieux.", 1),
    ("Que doit-on faire avec un camarade qui pleure ?", "Le consoler", ["Se moquer de lui", "Le pousser", "L'ignorer en riant"], "La solidarité, c'est aider les autres.", 1),
    ("Quelle journée célèbre l'unité du pays le 20 mai ?", "La fête nationale", ["La fête du Travail", "La fête de la Jeunesse", "Le jour de l'An"], "Le 20 mai est la fête nationale.", 2),
], "pcp-ecm", ECM, CMR)

# ============================================================================================================ CE2
add("ce2", "pcp-ce2-sci-corps", [
    ("Quel conduit amène les aliments de la bouche à l'estomac ?", "L'œsophage", ["La trachée", "L'intestin grêle", "Le larynx"], "L'œsophage pousse les aliments vers l'estomac.", 3),
    ("Quel organe fabrique la bile et aide à digérer les graisses ?", "Le foie", ["Le cœur", "La rate", "Le poumon"], "Le foie est un organe de la digestion.", 4),
    ("Combien de dents un adulte a-t-il normalement ?", "32", ["20", "28", "40"], "Les dents de sagesse comprises, l'adulte en a trente-deux.", 3),
    ("Comment appelle-t-on les premières dents de l'enfant ?", "Les dents de lait", ["Les dents de sagesse", "Les dents de fer", "Les dents d'adulte"], "Elles tombent et sont remplacées par les dents définitives.", 2),
    ("Que permettent de faire les muscles ?", "Bouger le corps", ["Respirer l'air", "Voir les couleurs", "Fabriquer le sang"], "Les muscles tirent sur les os pour provoquer le mouvement.", 2),
    ("Comment appelle-t-on l'endroit où deux os se rejoignent ?", "Une articulation", ["Une artère", "Une veine", "Un tendon"], "Le coude et le genou sont des articulations.", 3),
    ("Quelle partie de l'œil laisse entrer la lumière ?", "La pupille", ["La paupière", "Le sourcil", "Le cil"], "La pupille est le petit trou noir au centre de l'œil.", 3),
    ("Quel organe commande tout notre corps ?", "Le cerveau", ["Le cœur", "L'estomac", "Le foie"], "Le cerveau est protégé par le crâne.", 1),
    ("Quel est le rôle du squelette ?", "Soutenir et protéger le corps", ["Digérer les aliments", "Fabriquer la sueur", "Produire des odeurs"], "Les os soutiennent le corps et protègent des organes.", 2),
    ("Dans quels organes l'air passe-t-il quand on respire ?", "Dans les poumons", ["Dans l'estomac", "Dans les reins", "Dans le foie"], "L'air arrive aux poumons par le nez et la trachée.", 2),
], "pcp-sci", SCI)

add("ce2", "pcp-ce2-sci-sante", [
    ("Quelle maladie peut-on attraper en buvant une eau sale ?", "Le choléra", ["Le coup de soleil", "La fracture", "L'entorse"], "Le choléra se transmet par l'eau et les aliments contaminés.", 3),
    ("Contre quoi un vaccin protège-t-il ?", "Contre certaines maladies", ["Contre la pluie", "Contre la faim", "Contre le vent"], "Le vaccin aide le corps à se défendre.", 2),
    ("Que doit-on manger pour apporter des vitamines au corps ?", "Des fruits et des légumes", ["Seulement du sel", "Seulement des bonbons", "Seulement du sucre"], "Les fruits et légumes sont riches en vitamines.", 2),
    ("Lequel de ces aliments est riche en vitamine C ?", "L'orange", ["Le sel", "Le sucre en poudre", "Le bonbon"], "L'orange contient de la vitamine C.", 2),
    ("Quel aliment aide le corps à se construire (aliment bâtisseur) ?", "Le poisson", ["Le sucre", "Le sel", "Le bonbon"], "Viande, poisson, œufs et haricots apportent des protéines.", 3),
    ("Quel aliment contient surtout du sucre ?", "Le bonbon", ["Le poisson", "La viande", "L'avocat"], "Trop de sucre abîme les dents.", 2),
    ("Quel est l'effet des sucreries sans brossage des dents ?", "Des caries", ["Des os plus forts", "Une vue meilleure", "Des muscles plus gros"], "Les bactéries transforment le sucre en acide qui abîme les dents.", 2),
    ("Quel animal peut transmettre le paludisme à l'homme ?", "Le moustique", ["La mouche domestique", "Le cafard", "La fourmi"], "Seul le moustique anophèle femelle transmet le paludisme.", 2),
], "pcp-sci", SCI)

add("ce2", "pcp-ce2-sci-animaux", [
    ("Lequel de ces animaux naît du ventre de sa mère ?", "Le chien", ["La poule", "Le crocodile", "La tortue"], "Le chien est vivipare.", 2),
    ("Lequel de ces animaux pond des œufs ?", "Le canard", ["Le chat", "La chèvre", "Le mouton"], "Les oiseaux sont ovipares.", 2),
    ("Quel animal est omnivore ?", "Le porc", ["Le lion", "La vache", "La girafe"], "Le porc mange des plantes et de la viande.", 4),
    ("Quel animal est un mammifère qui vit dans l'eau ?", "Le dauphin", ["Le requin", "Le thon", "Le silure"], "Le dauphin respire de l'air et allaite ses petits.", 3),
    ("Quel est le seul mammifère capable de voler ?", "La chauve-souris", ["L'écureuil", "Le perroquet", "L'aigle"], "La chauve-souris vole avec ses ailes de peau.", 4),
    ("Que devient un têtard ?", "Une grenouille", ["Un poisson", "Un serpent", "Un lézard"], "Le têtard se transforme en grenouille.", 2),
    ("Dans la chaîne herbe - zèbre - lion, qui est le prédateur ?", "Le lion", ["L'herbe", "Le zèbre", "Le soleil"], "Le lion chasse le zèbre qui mange l'herbe.", 2),
    ("Quel animal est un insecte qui fabrique du miel ?", "L'abeille", ["La mouche", "Le moustique", "Le papillon"], "L'abeille récolte le nectar des fleurs.", 1),
    ("Quel est le plus grand animal terrestre ?", "L'éléphant", ["La girafe", "L'hippopotame", "Le rhinocéros"], "L'éléphant d'Afrique est le plus grand animal terrestre.", 3),
    ("Qu'est-ce qu'un herbivore ?", "Un animal qui mange des plantes", ["Un animal qui mange des insectes", "Un animal qui mange de la viande", "Un animal qui dort le jour"], "La vache et le mouton sont des herbivores.", 2),
], "pcp-sci", SCI)

add("ce2", "pcp-ce2-sci-nature", [
    ("Que fabrique une plante verte grâce à la lumière ?", "Sa nourriture", ["Des pierres", "Du sel", "De la farine"], "C'est la photosynthèse.", 3),
    ("Quelle partie de la plante transporte la sève ?", "La tige", ["La fleur", "La graine", "Le fruit"], "La tige relie les racines aux feuilles.", 3),
    ("Que contient souvent un fruit ?", "Des graines", ["Des os", "Des plumes", "Des écailles"], "Les graines permettent de faire naître de nouvelles plantes.", 1),
    ("Quel insecte aide beaucoup à transporter le pollen des fleurs ?", "L'abeille", ["Le cafard", "Le criquet", "Le moustique"], "L'abeille butine les fleurs.", 3),
    ("À quelle température l'eau gèle-t-elle ?", "0 °C", ["10 °C", "100 °C", "-50 °C"], "L'eau devient de la glace à zéro degré Celsius.", 3),
    ("À quelle température l'eau bout-elle au niveau de la mer ?", "100 °C", ["0 °C", "50 °C", "10 °C"], "L'eau devient de la vapeur à cent degrés.", 3),
    ("Comment appelle-t-on le passage de la vapeur d'eau à l'eau liquide ?", "La condensation", ["La fusion", "La solidification", "La combustion"], "Les nuages se forment par condensation.", 4),
    ("Que devient le sucre mélangé à de l'eau ?", "Il se dissout", ["Il brûle", "Il devient de la glace", "Il vole"], "Le sucre disparaît à la vue, mais il est toujours dans l'eau.", 2),
    ("Quelle eau est salée ?", "L'eau de la mer", ["L'eau de pluie", "L'eau du robinet", "L'eau d'une source"], "L'eau de mer contient beaucoup de sel.", 2),
    ("Comment sépare-t-on du sable mélangé à de l'eau ?", "Par filtration", ["Par aimantation", "Par fusion", "Par combustion"], "Le filtre retient le sable.", 3),
    ("Quel matériau laisse passer le courant électrique ?", "Le cuivre", ["Le plastique", "Le verre", "Le caoutchouc"], "Les fils électriques contiennent du cuivre.", 3),
    ("Que ne faut-il jamais toucher avec les mains mouillées ?", "Une prise électrique", ["Un seau", "Un livre", "Une cuillère en bois"], "L'eau conduit le courant : on risque l'électrocution.", 1),
    ("Quelle énergie vient du vent ?", "L'énergie éolienne", ["L'énergie solaire", "L'énergie nucléaire", "L'énergie du charbon"], "Les éoliennes transforment le vent en électricité.", 3),
    ("Quelle énergie vient de la lumière du Soleil ?", "L'énergie solaire", ["L'énergie éolienne", "L'énergie du pétrole", "L'énergie du gaz"], "Les panneaux solaires produisent de l'électricité.", 2),
    ("Quel déchet se décompose le plus vite ?", "Une épluchure de banane", ["Un sachet en plastique", "Une bouteille en verre", "Une canette en métal"], "Les déchets végétaux se décomposent facilement.", 3),
    ("Quelle est la planète sur laquelle nous vivons ?", "La Terre", ["Mars", "Vénus", "Jupiter"], "La Terre tourne autour du Soleil.", 1),
    ("Combien de temps la Terre met-elle pour faire un tour autour du Soleil ?", "Un an", ["Un jour", "Un mois", "Une semaine"], "La Terre fait un tour autour du Soleil en environ 365 jours.", 3),
    ("Quelle est la cause principale du jour et de la nuit ?", "La Terre tourne sur elle-même", ["Le Soleil tourne autour de la Lune", "La Lune cache le Soleil", "Les nuages cachent le Soleil"], "Elle fait un tour sur elle-même en 24 heures.", 3),
], "pcp-sci", SCI)

add("ce2", "pcp-ce2-hg-cm", [
    ("Quel est le chef-lieu de la région du Centre ?", "Yaoundé", ["Douala", "Obala", "Bafia"], "Yaoundé est capitale et chef-lieu de la région du Centre.", 2),
    ("Dans quelle région se trouve la ville de Garoua ?", "Dans la région du Nord", ["Dans la région de l'Ouest", "Dans la région du Sud", "Dans la région de l'Est"], "Garoua est le chef-lieu du Nord.", 3),
    ("Dans quelle région se trouve la ville de Limbé ?", "Dans la région du Sud-Ouest", ["Dans l'Adamaoua", "Dans la région du Nord", "Dans la région de l'Est"], "Limbé est au pied du mont Cameroun, au bord de l'océan.", 3),
    ("Dans quelle région se trouve Bertoua ?", "Dans la région de l'Est", ["Dans la région du Littoral", "Dans la région de l'Ouest", "Dans le Nord-Ouest"], "Bertoua est le chef-lieu de l'Est.", 2),
    ("Dans quelle région se trouve la ville de Dschang ?", "Dans la région de l'Ouest", ["Dans la région du Sud", "Dans le Littoral", "Dans l'Adamaoua"], "Dschang est connue pour son université.", 4),
    ("Quel est le chef-lieu de la région du Sud-Ouest ?", "Buea", ["Limbé", "Kumba", "Mamfe"], "Buea est au pied du mont Cameroun.", 3),
    ("Quel est le chef-lieu de la région du Nord-Ouest ?", "Bamenda", ["Kumbo", "Wum", "Nkambé"], "Bamenda est le chef-lieu du Nord-Ouest.", 3),
    ("Combien de pays sont voisins du Cameroun ?", "6", ["4", "8", "10"], "Nigeria, Tchad, Centrafrique, Congo, Gabon et Guinée équatoriale.", 4),
    ("Quel pays voisin se trouve au sud du Cameroun ?", "Le Gabon", ["Le Tchad", "Le Nigeria", "Le Niger"], "Le Gabon est au sud, avec le Congo et la Guinée équatoriale.", 3),
    ("Quel pays voisin borde le Cameroun à l'est ?", "La Centrafrique", ["Le Gabon", "Le Nigeria", "Le Congo"], "La République centrafricaine est à l'est.", 3),
    ("Quel fleuve de l'Adamaoua et du Centre se jette dans l'océan près d'Édéa ?", "La Sanaga", ["Le Logone", "La Bénoué", "Le Wouri"], "La Sanaga se jette dans l'océan Atlantique.", 4),
    ("Quel lac partagé entre quatre pays touche l'Extrême-Nord du Cameroun ?", "Le lac Tchad", ["Le lac Victoria", "Le lac Nyos", "Le lac Tanganyika"], "Le lac Tchad borde le Cameroun, le Nigeria, le Niger et le Tchad.", 3),
    ("Quel fleuve traverse la ville de Garoua ?", "La Bénoué", ["Le Wouri", "La Sanaga", "Le Nyong"], "La Bénoué traverse Garoua et coule vers le Nigeria.", 4),
    ("Dans quelle région élève-t-on surtout beaucoup de bovins ?", "L'Adamaoua", ["Le Littoral", "Le Sud", "Le Centre"], "L'Adamaoua est une grande région d'élevage de bovins.", 4),
    ("Quel est le climat du Sud du Cameroun ?", "Équatorial, chaud et humide", ["Désertique", "Polaire", "Tempéré froid"], "Il pleut beaucoup dans le Sud.", 3),
    ("Quelle végétation couvre une grande partie du Sud du Cameroun ?", "La forêt dense", ["Le désert", "La banquise", "La toundra"], "La forêt équatoriale couvre le Sud et l'Est.", 3),
    ("Quelle culture trouve-t-on souvent dans le Littoral et le Sud-Ouest ?", "La banane", ["Le mil", "Le blé", "Le coton"], "Les bananeraies sont nombreuses près du mont Cameroun et dans le Moungo.", 4),
    ("Dans quelle ville se trouve le grand port principal du Cameroun ?", "Douala", ["Maroua", "Bertoua", "Bamenda"], "Douala est le premier port du pays.", 2),
    ("Quel parc national célèbre se trouve dans l'Extrême-Nord ?", "Le parc de Waza", ["Le parc de Kruger", "Le parc de Yellowstone", "Le parc du Serengeti"], "Le parc de Waza abrite éléphants et lions.", 4),
    ("Quelle expression décrit le Cameroun avec ses climats et ses paysages variés ?", "L'Afrique en miniature", ["Le toit du monde", "Le pays des mille lacs", "Le désert vert"], "On y trouve forêt, savane, montagne, désert et côtes.", 3),
    ("Que signifie à l'origine le nom « Cameroun » ?", "Les crevettes (camarões)", ["Les montagnes", "Les lions", "Le grand fleuve"], "Des marins portugais ont appelé le Wouri le fleuve des crevettes.", 4),
], "pcp-hg", HG, CMR)

add("ce2", "pcp-ce2-hg-monde", [
    ("Quel est le plus long fleuve d'Afrique ?", "Le Nil", ["Le Niger", "Le Congo", "La Sanaga"], "Le Nil traverse plusieurs pays jusqu'en Égypte.", 3),
    ("Quel est le plus haut sommet d'Afrique ?", "Le Kilimandjaro", ["Le mont Cameroun", "Le mont Kenya", "Le mont Blanc"], "Le Kilimandjaro est en Tanzanie.", 4),
    ("Quelle est la plus grande île d'Afrique ?", "Madagascar", ["La Réunion", "Zanzibar", "Bioko"], "Madagascar est dans l'océan Indien.", 4),
    ("Quel est le plus grand lac d'Afrique ?", "Le lac Victoria", ["Le lac Tchad", "Le lac Nyos", "Le lac Volta"], "Le lac Victoria est partagé entre trois pays.", 4),
    ("Quelle est la capitale de l'Égypte ?", "Le Caire", ["Alexandrie", "Louxor", "Tunis"], "Le Caire se trouve sur le Nil.", 3),
    ("Quelle est la capitale de la République du Congo ?", "Brazzaville", ["Kinshasa", "Libreville", "Bangui"], "Brazzaville est en face de Kinshasa, de l'autre côté du fleuve.", 4),
    ("Quelle est la capitale de la Centrafrique ?", "Bangui", ["Brazzaville", "N'Djamena", "Yaoundé"], "Bangui est la capitale de la République centrafricaine.", 4),
    ("Quelle est la capitale du Ghana ?", "Accra", ["Lomé", "Abidjan", "Dakar"], "Accra est sur le golfe de Guinée.", 4),
    ("Quelle est la capitale du Sénégal ?", "Dakar", ["Bamako", "Conakry", "Niamey"], "Dakar est sur la côte atlantique.", 4),
    ("Quelle est la capitale du Kenya ?", "Nairobi", ["Kampala", "Dar es Salaam", "Addis-Abeba"], "Nairobi est la capitale du Kenya.", 4),
    ("Quel océan borde l'Afrique à l'est ?", "L'océan Indien", ["L'océan Atlantique", "L'océan Arctique", "L'océan Pacifique"], "L'Afrique est entourée par l'Atlantique et l'océan Indien.", 3),
    ("Quelle ligne imaginaire sépare la Terre en deux hémisphères Nord et Sud ?", "L'équateur", ["Le méridien", "Le tropique", "Le cercle polaire"], "L'équateur fait le tour de la Terre.", 4),
    ("Quel est le plus grand océan du monde ?", "L'océan Pacifique", ["L'océan Atlantique", "L'océan Indien", "L'océan Arctique"], "Le Pacifique est le plus vaste océan.", 3),
    ("Quel est le plus petit continent ?", "L'Océanie", ["L'Europe", "L'Afrique", "L'Amérique du Sud"], "L'Océanie regroupe l'Australie et de nombreuses îles.", 4),
    ("Quelle est la capitale des États-Unis ?", "Washington", ["New York", "Los Angeles", "Chicago"], "Washington est la capitale ; New York est la plus connue.", 4),
], "pcp-hg", HG, WLD)

add("ce2", "pcp-ce2-hg-histoire", [
    ("En quelle année le Cameroun sous administration française est-il devenu indépendant ?", "1960", ["1950", "1972", "1984"], "L'indépendance a été proclamée le 1er janvier 1960.", 3),
    ("Quel jour le Cameroun français a-t-il proclamé son indépendance ?", "Le 1er janvier 1960", ["Le 20 mai 1972", "Le 11 février 1961", "Le 14 juillet 1960"], "C'était le jour de l'An 1960.", 4),
    ("Quelle date marque la réunification du Cameroun en 1961 ?", "Le 1er octobre 1961", ["Le 1er janvier 1960", "Le 20 mai 1972", "Le 11 février 1966"], "Le Cameroun méridional britannique a rejoint le Cameroun indépendant.", 5),
    ("Que s'est-il passé le 20 mai 1972 ?", "Naissance de l'État unitaire", ["Indépendance du Cameroun", "Fin de la guerre mondiale", "Premier match des Lions Indomptables"], "Un référendum a créé la République unie du Cameroun.", 5),
    ("Comment s'appelle l'équipe nationale de football du Cameroun ?", "Les Lions Indomptables", ["Les Aigles de Carthage", "Les Éléphants", "Les Super Eagles"], "Ce surnom est connu dans le monde entier.", 2),
    ("Quel roi bamoun, à Foumban, a inventé une écriture ?", "Njoya", ["Rudolf Douala Manga Bell", "Ruben Um Nyobè", "Samori Touré"], "Le roi Njoya a inventé l'alphabet shümom.", 5),
    ("Qui a été le premier président du Cameroun ?", "Ahmadou Ahidjo", ["Paul Biya", "John Fru Ndi", "Ruben Um Nyobè"], "Ahmadou Ahidjo a présidé de 1960 à 1982.", 3),
    ("Quels bâtiments célèbres l'Égypte ancienne a-t-elle construits comme tombeaux ?", "Les pyramides", ["Les gratte-ciel", "Les cathédrales", "Les moulins à vent"], "Les pyramides de Gizeh sont connues dans le monde entier.", 3),
], "pcp-hg", HG, WLD)

add("ce2", "pcp-ce2-ecm", [
    ("Que signifie un panneau en forme d'octogone rouge ?", "Stop", ["Danger", "Parking", "Hôpital"], "Au stop, on s'arrête complètement.", 3),
    ("Que signifie un panneau triangulaire à bord rouge ?", "Un danger", ["Une interdiction", "Un parking", "Un hôpital"], "Le triangle annonce un danger.", 3),
    ("Que signifie un panneau rond à bord rouge ?", "Une interdiction", ["Un danger", "Une aire de repos", "Une indication de ville"], "Les panneaux ronds à bord rouge interdisent.", 4),
    ("Que doit-on faire au passage pour piétons avant de traverser ?", "Vérifier que les véhicules s'arrêtent", ["Courir sans regarder", "Fermer les yeux", "Jouer sur la route"], "On regarde toujours avant de traverser.", 2),
    ("Quel est le rôle des impôts ?", "Financer les écoles, routes et hôpitaux", ["Acheter des jouets aux enfants", "Payer les vacances du maire", "Remplacer le marché"], "Les impôts servent à payer les services publics.", 4),
    ("Qui fait respecter la loi dans les villes et les villages ?", "La police et la gendarmerie", ["Les commerçants", "Les artistes", "Les footballeurs"], "Ces forces de l'ordre protègent les personnes.", 2),
    ("Qui juge ceux qui ont enfreint la loi ?", "Le juge au tribunal", ["Le maçon", "Le boulanger", "Le chauffeur de taxi"], "Le juge applique la loi au tribunal.", 3),
    ("Lequel est un devoir de l'élève ?", "Respecter le règlement de l'école", ["Arriver à n'importe quelle heure", "Casser les bancs", "Écrire sur les murs"], "Respecter le règlement permet à tous d'apprendre.", 2),
    ("Lequel est un droit de l'enfant ?", "Être soigné quand il est malade", ["Conduire un camion", "Voter aux élections", "Travailler la nuit"], "Les enfants ont droit à la santé et à l'éducation.", 3),
    ("Que représente le drapeau pour les Camerounais ?", "Un symbole de la République", ["Un jouet", "Un vêtement de fête", "Une marque de savon"], "Le drapeau, l'hymne et la devise sont des symboles nationaux.", 2),
    ("Quelle est la devise du Cameroun ?", "Paix, Travail, Patrie", ["Honneur, Patrie, Liberté", "Union, Force, Travail", "Foi, Espoir, Charité"], "Elle est écrite sur les armoiries.", 2),
    ("Que doit-on faire avec les biens publics, comme un banc ou une fontaine ?", "En prendre soin", ["Les abîmer", "Les vendre", "Les emporter chez soi"], "Les biens publics appartiennent à tous.", 2),
    ("Quelle attitude montre le respect envers les camarades ?", "Écouter l'autre sans se moquer", ["Couper la parole", "Se moquer des fautes", "Cacher le matériel"], "Le respect permet de bien vivre ensemble.", 2),
    ("Qui est le chef de l'État au Cameroun ?", "Le président de la République", ["Le maire", "Le chef de classe", "Le directeur d'école"], "Le président est chef de l'État.", 3),
    ("Qui dirige une commune ?", "Le maire", ["Le gouverneur", "Le président", "Le chef de classe"], "Le maire dirige la mairie.", 4),
    ("Pourquoi faut-il respecter la file d'attente ?", "Pour que chacun passe à son tour", ["Pour passer en premier", "Pour pousser les autres", "Pour se faire remarquer"], "C'est une règle de politesse et d'équité.", 1),
    ("Quel geste économise l'eau ?", "Fermer le robinet pendant le brossage des dents", ["Laisser couler l'eau", "Arroser la route", "Remplir plusieurs seaux inutiles"], "L'eau potable est précieuse.", 2),
    ("Quelle action protège l'environnement ?", "Planter un arbre", ["Brûler des sachets en plastique", "Jeter des déchets au caniveau", "Couper des arbres sans raison"], "Les arbres purifient l'air.", 2),
    ("Que fait un bon citoyen ?", "Il respecte les lois", ["Il vole le bien d'autrui", "Il triche aux examens", "Il pollue la rivière"], "Respecter les lois est un devoir.", 2),
], "pcp-ecm", ECM, CMR)

# ============================================================================================================ CM1
add("cm1", "pcp-cm1-sci-corps", [
    ("Quel organe produit la bile ?", "Le foie", ["La rate", "Le pancréas", "L'estomac"], "La bile aide à digérer les graisses.", 4),
    ("Dans quel organe se fait l'essentiel de l'absorption des aliments digérés ?", "L'intestin grêle", ["L'estomac", "L'œsophage", "La bouche"], "Les nutriments passent de l'intestin grêle vers le sang.", 4),
    ("Quel est le trajet de l'air quand on inspire ?", "Nez, trachée, poumons", ["Bouche, estomac, intestin", "Nez, cœur, foie", "Trachée, reins, vessie"], "L'air arrive aux poumons par la trachée.", 3),
    ("Quel organe filtre le sang et produit l'urine ?", "Les reins", ["Le foie", "L'estomac", "Les poumons"], "Il y a deux reins dans le corps.", 3),
    ("Quel rôle ont les globules rouges ?", "Transporter l'oxygène", ["Combattre les microbes", "Produire la sueur", "Fabriquer les os"], "Les globules rouges transportent l'oxygène des poumons vers tout le corps.", 4),
    ("Que sont les artères ?", "Des vaisseaux qui partent du cœur", ["Des os du bras", "Des muscles du dos", "Des tuyaux de l'estomac"], "Les artères conduisent le sang du cœur vers les organes.", 5),
    ("Quelle partie de l'œil permet de voir les couleurs et les formes grâce à la lumière ?", "La rétine", ["La pupille", "La paupière", "Le sourcil"], "La rétine envoie les informations au cerveau par le nerf optique.", 5),
    ("Quel est le rôle du cerveau ?", "Recevoir des messages et commander le corps", ["Pomper le sang", "Digérer les aliments", "Filtrer l'urine"], "Le cerveau est le centre de commande du corps.", 3),
    ("Quelle vitamine le corps fabrique-t-il avec l'aide du soleil sur la peau ?", "La vitamine D", ["La vitamine C", "La vitamine K", "La vitamine B"], "La vitamine D aide à fixer le calcium dans les os.", 5),
    ("Quel aliment est une bonne source de calcium pour les os ?", "Le lait", ["Le sucre", "L'huile", "Le sel"], "Le calcium rend les os et les dents solides.", 3),
    ("Quelle maladie vient d'un manque de fer dans l'alimentation ?", "L'anémie", ["La carie", "Le rhume", "L'entorse"], "L'anémie fatigue et rend pâle.", 5),
    ("Le kwashiorkor est une maladie de l'enfant due à un manque de quoi ?", "De protéines", ["De sel", "De sucre", "De lumière"], "Elle apparaît quand l'alimentation manque de protéines.", 5),
    ("Que fait le tympan quand un son arrive dans l'oreille ?", "Il vibre", ["Il fabrique le cérumen", "Il produit des larmes", "Il s'ouvre en grand"], "Le tympan est une fine membrane qui vibre sous l'effet du son.", 5),
], "pcp-sci", SCI)

add("cm1", "pcp-cm1-sci-sante", [
    ("Quel est l'agent transmetteur du paludisme ?", "Le moustique anophèle femelle", ["La mouche tsé-tsé", "La puce", "Le cafard"], "Le parasite est transmis par la piqûre.", 3),
    ("Quel est le meilleur moyen de se protéger du paludisme la nuit ?", "La moustiquaire imprégnée", ["Un ventilateur", "Une couverture épaisse", "Un thé chaud"], "Elle empêche les moustiques de piquer.", 2),
    ("Que fait le virus du VIH dans le corps ?", "Il affaiblit les défenses du corps", ["Il casse les os", "Il abîme les dents", "Il brûle la peau"], "Il attaque le système immunitaire.", 4),
    ("Quelle maladie touche surtout les poumons et se transmet par la toux ?", "La tuberculose", ["Le paludisme", "La carie", "La conjonctivite"], "Elle se soigne avec un traitement long et suivi.", 4),
    ("Comment évite-t-on le choléra ?", "En buvant de l'eau potable et en se lavant les mains", ["En dormant sous une couverture", "En portant un chapeau", "En faisant du sport"], "L'hygiène de l'eau et des mains coupe la transmission.", 3),
    ("À quoi sert une vaccination ?", "À préparer le corps à se défendre", ["À guérir les os cassés", "À remplacer le repas", "À supprimer la fatigue"], "Le vaccin évite de tomber gravement malade.", 3),
    ("Quelle maladie les vaccins aident-ils à éviter chez les enfants ?", "La rougeole", ["La carie", "L'entorse", "Le coup de soleil"], "La rougeole est évitée par un vaccin.", 3),
    ("Quel est le meilleur moyen d'avoir de l'eau potable ?", "La filtrer et la faire bouillir", ["La laisser dans un seau ouvert", "La remuer avec une cuillère", "La mélanger à du sable"], "Faire bouillir détruit la plupart des microbes.", 3),
    ("Pourquoi faut-il laver les fruits et légumes avant de les manger ?", "Pour enlever la saleté et les microbes", ["Pour les rendre plus gros", "Pour les rendre sucrés", "Pour changer leur couleur"], "Le lavage retire terre et germes.", 2),
    ("À quoi servent les latrines propres ?", "À éviter la propagation des maladies", ["À arroser les plantes", "À conserver les aliments", "À laver les habits"], "Elles évitent que les selles contaminent l'eau.", 3),
    ("Quelle est l'abréviation de l'Organisation mondiale de la santé ?", "L'OMS", ["L'ONU", "L'UNESCO", "La FAO"], "L'OMS s'occupe de la santé dans le monde.", 4),
    ("Quelle organisation s'occupe en particulier des enfants dans le monde ?", "L'UNICEF", ["La FAO", "L'OMC", "L'OPEP"], "L'UNICEF aide les enfants à travers le monde.", 4),
], "pcp-sci", SCI)

add("cm1", "pcp-cm1-sci-vivant", [
    ("Quelles sont les trois parties du corps d'un insecte ?", "La tête, le thorax et l'abdomen", ["Le crâne, le tronc et les pattes", "La tête, la queue et le dos", "Le bec, l'aile et la queue"], "Le thorax porte les pattes et les ailes.", 4),
    ("Lequel de ces animaux est un vertébré ?", "Le crocodile", ["Le crabe", "Le ver de terre", "Le criquet"], "Les vertébrés ont une colonne vertébrale.", 3),
    ("Lequel de ces animaux est un reptile ?", "Le lézard", ["La grenouille", "Le poisson", "Le dauphin"], "Les reptiles ont la peau couverte d'écailles.", 3),
    ("Lequel de ces animaux est un amphibien ?", "La grenouille", ["Le lézard", "La tortue", "Le crocodile"], "Les amphibiens vivent d'abord dans l'eau puis sur terre.", 4),
    ("Comment appelle-t-on les animaux qui naissent d'un œuf ?", "Les ovipares", ["Les vivipares", "Les herbivores", "Les carnivores"], "Poule, tortue et crocodile sont ovipares.", 3),
    ("Comment appelle-t-on les animaux qui naissent du ventre de leur mère ?", "Les vivipares", ["Les ovipares", "Les carnivores", "Les omnivores"], "Chien, vache et dauphin sont vivipares.", 3),
    ("Comment un fruit comme la noix de coco peut-il voyager loin sur la mer ?", "Il flotte et est transporté par l'eau", ["Il vole avec ses ailes", "Il marche", "Il s'enterre seul"], "Les graines sont dispersées par le vent, l'eau ou les animaux.", 4),
    ("Que fait le vent pour certaines graines légères ?", "Il les disperse", ["Il les transforme en eau", "Il les mange", "Il les refroidit"], "Le vent emporte les graines ailées ou légères.", 3),
    ("Comment s'appelle le transport du pollen d'une fleur à l'autre ?", "La pollinisation", ["La respiration", "La digestion", "La condensation"], "Les insectes et le vent transportent le pollen.", 4),
    ("Que prend une plante verte dans l'air pour fabriquer sa matière ?", "Du gaz carbonique", ["De l'hélium", "Du méthane", "Du néon"], "La plante absorbe du gaz carbonique et rejette de l'oxygène.", 4),
    ("Quel gaz la plante rejette-t-elle en faisant la photosynthèse ?", "L'oxygène", ["Le gaz carbonique", "Le méthane", "L'hélium"], "L'oxygène est indispensable aux animaux.", 4),
    ("Qu'est-ce qu'un animal prédateur ?", "Un animal qui chasse d'autres animaux", ["Un animal qui vit en groupe", "Un animal qui mange l'herbe", "Un animal qui dort le jour"], "Le lion est un prédateur.", 3),
    ("Dans une chaîne alimentaire, quel est le premier maillon ?", "Un végétal", ["Un carnivore", "Un champignon", "Un oiseau"], "Les plantes fabriquent leur propre nourriture.", 4),
    ("Que fait un décomposeur comme un champignon ?", "Il transforme les restes en terre fertile", ["Il fabrique du lait", "Il chasse les proies", "Il produit de l'oxygène"], "Les décomposeurs recyclent la matière morte.", 5),
    ("Quelle est la première étape du développement d'un papillon ?", "L'œuf", ["La chrysalide", "Le papillon adulte", "La chenille géante"], "Le papillon passe par l'œuf, la chenille, la chrysalide puis l'adulte.", 4),
], "pcp-sci", SCI)

add("cm1", "pcp-cm1-sci-matiere", [
    ("Quel gaz est le plus abondant dans l'air ?", "L'azote", ["L'oxygène", "Le gaz carbonique", "L'hydrogène"], "L'air contient environ 78 % d'azote.", 5),
    ("Que faut-il pour qu'un feu brûle ?", "Un combustible et de l'air", ["De l'eau et du sable", "De la glace et du vent froid", "Un seau et de la terre"], "Le feu a besoin d'oxygène et d'un combustible.", 4),
    ("Pourquoi un feu s'éteint-il quand on le couvre d'une épaisse couverture ?", "Il n'a plus d'air", ["Il a trop chaud", "Il a trop de lumière", "Il manque de bruit"], "Sans oxygène, le feu s'éteint.", 4),
    ("Quelle est l'unité de mesure de la température ?", "Le degré Celsius", ["Le mètre", "Le kilogramme", "Le litre"], "On écrit °C.", 3),
    ("Quelle est l'unité de mesure des volumes de liquide ?", "Le litre", ["Le gramme", "Le mètre", "Le degré"], "Le litre mesure la contenance.", 2),
    ("Quelle est l'unité de mesure de la masse ?", "Le kilogramme", ["Le litre", "Le mètre", "L'heure"], "Le kilogramme se note kg.", 2),
    ("Comment appelle-t-on le passage de l'état solide à l'état liquide ?", "La fusion", ["La solidification", "La condensation", "La vaporisation"], "La glace fond à 0 °C.", 4),
    ("Comment appelle-t-on le passage de l'état liquide à l'état solide ?", "La solidification", ["La fusion", "La vaporisation", "La condensation"], "L'eau devient glace en gelant.", 4),
    ("Comment appelle-t-on le passage de l'état liquide à l'état gazeux ?", "La vaporisation", ["La fusion", "La solidification", "La condensation"], "L'eau chaude se transforme en vapeur.", 4),
    ("Quelles sont les trois étapes simples du cycle de l'eau ?", "Évaporation, condensation, précipitations", ["Fusion, mélange, filtration", "Combustion, brûlage, fumée", "Aimantation, chute, remontée"], "L'eau s'évapore, forme des nuages puis retombe.", 4),
    ("Que se passe-t-il quand deux pôles identiques de deux aimants se rapprochent ?", "Ils se repoussent", ["Ils s'attirent", "Ils fondent", "Ils s'éteignent"], "Deux pôles différents s'attirent.", 5),
    ("Pour qu'une ampoule s'allume, le circuit électrique doit-il être ouvert ou fermé ?", "Fermé", ["Ouvert", "Plongé dans l'eau", "Vide d'air"], "Le courant doit pouvoir faire tout le tour du circuit.", 4),
    ("Quel élément d'un circuit permet de l'ouvrir ou de le fermer ?", "L'interrupteur", ["L'ampoule", "La pile", "Le fil de cuivre"], "L'interrupteur coupe ou laisse passer le courant.", 4),
    ("Quel matériau est un bon isolant électrique ?", "Le plastique", ["Le cuivre", "Le fer", "L'aluminium"], "Le plastique enrobe les fils pour protéger.", 4),
    ("Comment se propage la lumière ?", "En ligne droite", ["En zigzag", "En cercle", "En spirale"], "Les ombres s'expliquent par la lumière qui va tout droit.", 4),
    ("Quelle source d'énergie est renouvelable ?", "Le vent", ["Le pétrole", "Le charbon", "Le gaz naturel"], "Le Soleil, le vent et l'eau sont renouvelables.", 3),
    ("Quelle énergie produit l'électricité des barrages comme celui d'Édéa ?", "L'énergie de l'eau", ["L'énergie du vent", "Le charbon", "L'énergie nucléaire"], "L'eau de la Sanaga fait tourner des turbines.", 4),
    ("À quoi sert un thermomètre ?", "À mesurer la température", ["À mesurer la masse", "À mesurer la longueur", "À mesurer le temps"], "On lit le résultat en degrés.", 1),
    ("Combien y a-t-il de planètes dans le système solaire ?", "8", ["6", "9", "12"], "Mercure, Vénus, Terre, Mars, Jupiter, Saturne, Uranus et Neptune.", 4),
    ("Quelle planète est la plus proche du Soleil ?", "Mercure", ["Vénus", "Mars", "Neptune"], "Mercure est la plus petite planète du système solaire.", 5),
    ("Combien de temps la Lune met-elle environ pour tourner autour de la Terre ?", "Environ un mois", ["Un jour", "Une semaine", "Un an"], "Les phases de la Lune se répètent chaque mois.", 4),
    ("Quand se produit une éclipse de Soleil ?", "Quand la Lune passe entre le Soleil et la Terre", ["Quand la Terre passe entre la Lune et le Soleil", "Quand les nuages cachent le Soleil", "Quand il pleut beaucoup"], "La Lune cache alors le Soleil.", 5),
    ("Combien de jours compte une année bissextile ?", "366", ["364", "365", "367"], "Février compte alors 29 jours.", 4),
    ("Quelle couche de la Terre se trouve au centre ?", "Le noyau", ["La croûte", "Le manteau", "L'atmosphère"], "La croûte est la couche externe sur laquelle on vit.", 5),
], "pcp-sci", SCI)

add("cm1", "pcp-cm1-sci-env", [
    ("Qu'est-ce que la déforestation ?", "La destruction des forêts", ["La plantation d'arbres", "L'arrosage des champs", "La pêche en rivière"], "Elle fait disparaître des animaux et des plantes.", 3),
    ("Que fait le reboisement ?", "Il replante des arbres", ["Il coupe des arbres", "Il brûle des déchets", "Il pollue l'air"], "Il aide à protéger les sols et le climat.", 3),
    ("Quel danger les sachets plastiques jetés dans la nature représentent-ils ?", "Ils polluent longtemps et bloquent les caniveaux", ["Ils donnent de l'engrais", "Ils nourrissent les poissons", "Ils font tomber la pluie"], "Le plastique met très longtemps à disparaître.", 3),
    ("Quel gaz renforce l'effet de serre ?", "Le gaz carbonique", ["L'oxygène", "L'azote", "L'hélium"], "Il vient surtout des combustibles brûlés.", 4),
    ("Comment appelle-t-on l'avancée du désert vers des terres autrefois cultivées ?", "La désertification", ["La reforestation", "L'irrigation", "La fertilisation"], "Elle touche notamment le Sahel.", 4),
    ("Qu'est-ce que le braconnage ?", "La chasse interdite d'animaux protégés", ["L'élevage de poules", "La pêche en mer", "La cueillette de fruits"], "Il met des espèces en danger.", 4),
    ("Que fait-on avec des épluchures et des restes végétaux pour produire de l'engrais ?", "Du compost", ["Du plastique", "Du ciment", "Du verre"], "Les restes végétaux se décomposent en terreau.", 4),
    ("Qu'est-ce que l'érosion du sol ?", "L'usure du sol emporté par l'eau ou le vent", ["La plantation des arbres", "L'arrosage régulier", "La récolte du mil"], "Les racines des plantes retiennent le sol.", 4),
    ("À quoi servent les parcs nationaux ?", "À protéger la faune et la flore", ["À construire des usines", "À stocker du pétrole", "À vendre du bois"], "Ce sont des zones protégées.", 3),
    ("Quelle forêt s'étend sur le bassin du fleuve Congo, dont une partie est au Cameroun ?", "La forêt équatoriale", ["La toundra", "La taïga", "Le désert"], "C'est l'une des plus grandes forêts tropicales du monde.", 4),
], "pcp-sci", SCI)

add("cm1", "pcp-cm1-hg-cm", [
    ("Quel est le chef-lieu de la région du Sud-Ouest ?", "Buea", ["Limbé", "Kumba", "Mundemba"], "Buea se trouve au pied du mont Cameroun.", 3),
    ("Dans quelle région se trouve la ville de Kribi ?", "Dans la région du Sud", ["Dans le Littoral", "Dans l'Adamaoua", "Dans le Nord"], "Kribi abrite un port en eau profonde.", 4),
    ("Dans quelle région se trouve la ville de Foumban ?", "Dans la région de l'Ouest", ["Dans le Centre", "Dans le Nord-Ouest", "Dans l'Adamaoua"], "Foumban est la capitale du royaume bamoun.", 4),
    ("Dans quelle région se trouve la ville de Kumba ?", "Dans la région du Sud-Ouest", ["Dans la région du Nord-Ouest", "Dans la région de l'Ouest", "Dans la région du Littoral"], "Kumba est une grande ville du Sud-Ouest.", 4),
    ("Dans quelle région se trouve la ville d'Édéa ?", "Dans la région du Littoral", ["Dans la région du Sud", "Dans la région de l'Est", "Dans la région du Centre"], "Édéa est sur la Sanaga.", 4),
    ("Dans quelle région se trouve la ville de Kousséri ?", "Dans l'Extrême-Nord", ["Dans l'Adamaoua", "Dans le Sud", "Dans l'Est"], "Kousséri est près du Tchad.", 4),
    ("Dans quelle région se trouve la ville de Yokadouma ?", "Dans la région de l'Est", ["Dans la région du Sud", "Dans la région du Littoral", "Dans l'Adamaoua"], "Yokadouma est une ville de l'Est.", 5),
    ("Dans quelle région se trouve la ville de Sangmélima ?", "Dans la région du Sud", ["Dans la région du Centre", "Dans la région de l'Est", "Dans la région du Littoral"], "Sangmélima est dans le département du Dja-et-Lobo.", 5),
    ("Dans quelle région se trouve la ville de Meiganga ?", "Dans l'Adamaoua", ["Dans le Nord", "Dans l'Est", "Dans le Centre"], "Meiganga est dans le département du Mbéré.", 5),
    ("Dans quelle région se trouve la ville de Nkongsamba ?", "Dans le Littoral", ["Dans le Sud-Ouest", "Dans l'Ouest", "Dans le Centre"], "Nkongsamba est dans le Moungo.", 5),
    ("Dans quelle région se trouve la ville de Bafang ?", "Dans l'Ouest", ["Dans le Centre", "Dans le Littoral", "Dans le Nord-Ouest"], "Bafang est une ville de l'Ouest.", 5),
    ("Quel pays voisin du Cameroun possède l'île de Bioko ?", "La Guinée équatoriale", ["Le Gabon", "Le Congo", "Le Nigeria"], "Bioko est une île du golfe de Guinée qui appartient à la Guinée équatoriale.", 5),
    ("Quelle est la capitale de la République centrafricaine ?", "Bangui", ["Brazzaville", "N'Djamena", "Libreville"], "Bangui est sur le fleuve Oubangui.", 4),
    ("Quelle est la capitale du Gabon ?", "Libreville", ["Port-Gentil", "Bangui", "Brazzaville"], "Libreville est au bord de l'Atlantique.", 3),
    ("Quelle est la capitale du Tchad ?", "N'Djamena", ["Abéché", "Bangui", "Niamey"], "N'Djamena est près du Cameroun, au bord du Chari.", 4),
    ("Quelle est la capitale du Nigeria ?", "Abuja", ["Lagos", "Kano", "Accra"], "Abuja a remplacé Lagos comme capitale en 1991.", 4),
    ("Quel est le plus haut sommet du Cameroun ?", "Le mont Cameroun", ["Le mont Oku", "Le mont Manengouba", "Le mont Mandara"], "Le mont Cameroun est un volcan actif de plus de 4 000 m.", 3),
    ("Quel massif se trouve dans l'Extrême-Nord du Cameroun ?", "Les monts Mandara", ["Les monts Bamboutos", "Le massif du Hoggar", "Les Alpes"], "Les monts Mandara sont à la frontière du Nigeria.", 4),
    ("Quel plateau occupe le centre du Cameroun ?", "Le plateau de l'Adamaoua", ["Le plateau de Bamenda", "Le plateau du Tibesti", "Le plateau Bateke"], "Le plateau de l'Adamaoua sépare le Nord du Sud.", 4),
    ("Quel fleuve se jette dans le golfe de Guinée à Douala ?", "Le Wouri", ["Le Nyong", "La Bénoué", "Le Logone"], "Le Wouri forme un estuaire à Douala.", 3),
    ("Dans quel pays la Bénoué se jette-t-elle dans le Niger ?", "Au Nigeria", ["Au Tchad", "Au Gabon", "Au Congo"], "La Bénoué est un affluent du Niger.", 5),
    ("Quel est le plus long fleuve entièrement camerounais ?", "La Sanaga", ["Le Logone", "Le Nyong", "Le Wouri"], "La Sanaga est le plus long fleuve du Cameroun.", 5),
    ("Quel lac du Nord-Ouest est connu pour une catastrophe de gaz en 1986 ?", "Le lac Nyos", ["Le lac Tchad", "Le lac Lagdo", "Le lac Bamendjing"], "Un gaz toxique s'est échappé du lac Nyos en 1986.", 5),
    ("Quel grand lac a beaucoup diminué à cause de la sécheresse ?", "Le lac Tchad", ["Le lac Victoria", "Le lac Tanganyika", "Le lac Nyos"], "Il touche le Cameroun, le Niger, le Nigeria et le Tchad.", 4),
    ("Quel est le climat du Nord du Cameroun ?", "Tropical sec à saison des pluies courte", ["Équatorial toute l'année", "Polaire", "Montagnard glacé"], "Le Nord est plus sec que le Sud.", 4),
    ("Quelle culture est typique de la région du Nord ?", "Le coton", ["Le cacao", "L'hévéa", "La banane plantain"], "Le coton est cultivé autour de Garoua.", 3),
    ("Quelle culture est très importante dans les régions du Centre et du Sud ?", "Le cacao", ["Le coton", "Le mil", "L'arachide"], "Le cacao est une grande culture d'exportation.", 3),
    ("Quelle région du Cameroun est la plus connue pour l'élevage de bovins ?", "L'Adamaoua", ["Le Littoral", "Le Sud", "Le Centre"], "Ses pâturages sont frais en altitude.", 4),
    ("Quel train relie Yaoundé à Ngaoundéré ?", "Le Transcamerounais", ["Le TGV", "Le Dakar-Niger", "Le Lagos-Express"], "Ce train facilite le transport entre le Sud et le Nord.", 4),
    ("Quel oléoduc part du Tchad et arrive à Kribi ?", "L'oléoduc Tchad-Cameroun", ["L'oléoduc Nigeria-Gabon", "L'oléoduc Niger-Bénin", "L'oléoduc Congo-Angola"], "Il transporte le pétrole vers la côte.", 5),
    ("Quel grand port en eau profonde se trouve dans le Sud du Cameroun ?", "Kribi", ["Limbé", "Yokadouma", "Bafoussam"], "Kribi est un port moderne sur l'Atlantique.", 4),
    ("Quel titre porte le chef traditionnel de nombreuses chefferies du Nord ?", "Lamido", ["Fon", "Pharaon", "Calife"], "Le lamido dirige un lamidat.", 5),
    ("Quel titre porte le chef traditionnel dans les chefferies des Grassfields ?", "Fon", ["Lamido", "Pharaon", "Calife"], "Fon est le titre du chef dans les hautes terres de l'Ouest et du Nord-Ouest.", 5),
], "pcp-hg", HG, CMR)

add("cm1", "pcp-cm1-hg-hist", [
    ("Quelle puissance européenne a installé le protectorat sur le Cameroun en 1884 ?", "L'Allemagne", ["L'Italie", "L'Espagne", "La Belgique"], "Le traité a été signé avec des chefs douala en 1884.", 4),
    ("Quelle puissance européenne a dû quitter le Cameroun pendant la Première Guerre mondiale ?", "L'Allemagne", ["La France", "L'Angleterre", "L'Italie"], "L'Allemagne a perdu le Cameroun pendant la guerre de 1914-1918.", 4),
    ("Après la Première Guerre mondiale, quels pays ont reçu des mandats sur le Cameroun ?", "La France et le Royaume-Uni", ["L'Allemagne et l'Italie", "L'Espagne et le Portugal", "Les États-Unis et le Canada"], "La Société des Nations a partagé le territoire.", 5),
    ("Quel chef douala est mort pendu en 1914 pour avoir résisté aux Allemands ?", "Rudolf Douala Manga Bell", ["Ruben Um Nyobè", "Ahmadou Ahidjo", "Charles Atangana"], "Il s'opposait à la prise des terres des Douala.", 5),
    ("En quelle année a eu lieu l'indépendance du Cameroun français ?", "1960", ["1948", "1972", "1984"], "L'indépendance est proclamée le 1er janvier 1960.", 3),
    ("Quelle date correspond à la réunification du Cameroun ?", "Le 1er octobre 1961", ["Le 1er janvier 1960", "Le 20 mai 1972", "Le 11 février 1961"], "Les Cameroons britanniques du Sud ont rejoint le Cameroun.", 5),
    ("En quelle année la République unie du Cameroun est-elle née ?", "1972", ["1960", "1961", "1984"], "Le 20 mai 1972, un référendum met fin au fédéralisme.", 5),
    ("Quel parti nationaliste camerounais fondé en 1948 réclamait l'indépendance ?", "L'UPC", ["Le RDA", "Le PAI", "L'UNC"], "L'Union des populations du Cameroun a été fondée en 1948.", 5),
    ("Quel dirigeant de l'UPC est connu pour avoir lutté pour l'indépendance ?", "Ruben Um Nyobè", ["Samory Touré", "Patrice Lumumba", "Kwame Nkrumah"], "Ruben Um Nyobè a été secrétaire général de l'UPC.", 5),
    ("Qui a succédé à Ahmadou Ahidjo à la présidence en 1982 ?", "Paul Biya", ["John Ngu Foncha", "André-Marie Mbida", "Ruben Um Nyobè"], "Paul Biya devient président en novembre 1982.", 4),
    ("En quelle année le pays reprend-il le nom de République du Cameroun ?", "1984", ["1960", "1972", "1961"], "Le pays porte ce nom depuis 1984.", 5),
    ("Quel organisme international fut créé en 1945 pour garder la paix ?", "L'ONU", ["La FIFA", "L'OMS", "L'OPEP"], "L'Organisation des Nations unies a été créée en 1945.", 4),
    ("Quel navigateur a atteint l'Amérique en 1492 ?", "Christophe Colomb", ["Vasco de Gama", "Magellan", "Livingstone"], "Il croyait avoir atteint les Indes.", 4),
    ("Quel premier pays d'Afrique noire est devenu indépendant en 1957 ?", "Le Ghana", ["Le Sénégal", "Le Cameroun", "Le Mali"], "Le Ghana, ex-Gold Coast, est indépendant en 1957.", 5),
    ("Quel grand dirigeant sud-africain est resté longtemps en prison puis est devenu président en 1994 ?", "Nelson Mandela", ["Kwame Nkrumah", "Julius Nyerere", "Patrice Lumumba"], "Il a lutté contre l'apartheid.", 4),
    ("En quelle année l'homme a-t-il marché sur la Lune pour la première fois ?", "1969", ["1945", "1957", "1989"], "Neil Armstrong a marché sur la Lune en 1969.", 4),
    ("Quelle révolution commence en 1789 en France ?", "La Révolution française", ["La révolution russe", "La révolution industrielle américaine", "La révolution de velours"], "Elle a donné la Déclaration des droits de l'homme.", 4),
    ("Que fut l'apartheid ?", "Un système de séparation raciale en Afrique du Sud", ["Un parti politique camerounais", "Une danse traditionnelle", "Un traité de commerce"], "Il a pris fin dans les années 1990.", 4),
], "pcp-hg", HG, WLD)

add("cm1", "pcp-cm1-hg-monde", [
    ("Quel est le plus grand pays d'Afrique par la superficie ?", "L'Algérie", ["Le Nigeria", "Le Cameroun", "L'Égypte"], "L'Algérie est le plus grand pays d'Afrique.", 5),
    ("Quel canal relie la mer Méditerranée à la mer Rouge ?", "Le canal de Suez", ["Le canal de Panama", "Le canal du Midi", "Le canal de Corinthe"], "Le canal de Suez est en Égypte.", 5),
    ("Quel est le plus grand désert chaud du monde ?", "Le Sahara", ["Le Kalahari", "Le Gobi", "Le Namib"], "Le Sahara occupe le nord de l'Afrique.", 3),
    ("Quel est le fleuve le plus long d'Afrique ?", "Le Nil", ["Le Congo", "Le Niger", "Le Zambèze"], "Le Nil traverse l'Égypte avant la Méditerranée.", 3),
    ("Quelle organisation africaine a son siège à Addis-Abeba ?", "L'Union africaine", ["L'ONU", "La CEMAC", "La FIFA"], "Elle regroupe presque tous les États africains.", 5),
    ("Le Cameroun est membre de quelle organisation qui rassemble surtout d'anciens territoires britanniques ?", "Le Commonwealth", ["L'OPEP", "L'OTAN", "L'Union européenne"], "Le Cameroun est aussi membre de la Francophonie.", 5),
    ("Quel est le plus haut sommet du monde ?", "L'Everest", ["Le Kilimandjaro", "Le mont Blanc", "Le mont Cameroun"], "L'Everest se trouve dans l'Himalaya.", 4),
    ("Quel est le plus grand fleuve d'Amérique du Sud ?", "L'Amazone", ["Le Mississippi", "Le Nil", "Le Danube"], "L'Amazone traverse surtout le Brésil.", 4),
    ("Quel est le méridien d'origine de la Terre ?", "Le méridien de Greenwich", ["Le méridien de Paris", "Le méridien de Rome", "Le méridien d'Accra"], "Il passe par Greenwich, près de Londres.", 5),
    ("Quelle ligne imaginaire sépare l'hémisphère nord de l'hémisphère sud ?", "L'équateur", ["Le tropique du Cancer", "Le méridien de Greenwich", "Le cercle polaire"], "Le Cameroun se trouve au nord de l'équateur.", 4),
    ("Quel océan borde la côte ouest de l'Afrique ?", "L'océan Atlantique", ["L'océan Indien", "L'océan Arctique", "L'océan Pacifique"], "Le golfe de Guinée fait partie de l'Atlantique.", 3),
    ("Quel continent compte le Sahara ?", "L'Afrique", ["L'Asie", "L'Europe", "L'Amérique"], "Le Sahara est en Afrique du Nord.", 2),
    ("Quel est le plus petit continent par la surface ?", "L'Océanie", ["L'Europe", "L'Antarctique", "L'Amérique du Sud"], "L'Océanie réunit l'Australie et de nombreuses îles.", 4),
    ("Quel est le plus grand océan du monde ?", "L'océan Pacifique", ["L'océan Atlantique", "L'océan Indien", "L'océan Arctique"], "Il recouvre près d'un tiers de la Terre.", 3),
], "pcp-hg", HG, WLD)

add("cm1", "pcp-cm1-ecm", [
    ("Comment s'appelle la loi fondamentale d'un pays ?", "La Constitution", ["Le règlement de l'école", "Le code de la route", "Le manuel scolaire"], "La Constitution organise les pouvoirs de l'État.", 3),
    ("Quelles sont les deux chambres du Parlement camerounais ?", "L'Assemblée nationale et le Sénat", ["Le Sénat et la Mairie", "La Cour et le Tribunal", "La Préfecture et la Région"], "Le Parlement vote les lois.", 5),
    ("Quel est le rôle du Parlement ?", "Voter les lois", ["Juger les voleurs", "Soigner les malades", "Vendre les billets d'avion"], "Les députés et les sénateurs votent les lois.", 4),
    ("Qui dirige une région ?", "Le gouverneur", ["Le maire", "Le chef de classe", "Le directeur d'école"], "Le gouverneur représente l'État dans la région.", 5),
    ("Qui dirige un département ?", "Le préfet", ["Le maire", "Le gouverneur", "Le proviseur"], "Le préfet est le chef du département.", 5),
    ("Qui dirige une commune ?", "Le maire", ["Le préfet", "Le gouverneur", "Le proviseur"], "Le maire est élu par le conseil municipal.", 4),
    ("Quel est le rôle de la justice ?", "Faire respecter la loi et trancher les conflits", ["Voter le budget", "Construire des routes", "Soigner les malades"], "Les juges appliquent la loi.", 4),
    ("En quelle année a été adoptée la Convention relative aux droits de l'enfant ?", "1989", ["1945", "1960", "2005"], "Elle a été adoptée par l'ONU en 1989.", 5),
    ("Quelle journée célèbre-t-on le 16 juin en Afrique ?", "La Journée de l'enfant africain", ["La fête du Travail", "La fête de la Jeunesse", "La fête de l'Unité"], "Elle rappelle des droits de l'enfant.", 5),
    ("Quel droit de l'enfant est garanti par la Convention ?", "Le droit d'avoir un nom et une nationalité", ["Le droit de conduire", "Le droit de voter", "Le droit de se marier"], "Chaque enfant a droit à un nom dès la naissance.", 4),
    ("À quoi sert la carte nationale d'identité ?", "À prouver son identité", ["À payer ses achats", "À prendre le train gratuitement", "À entrer au stade"], "Elle contient les informations d'identité de son titulaire.", 3),
    ("Que fait un électeur le jour d'une élection ?", "Il vote", ["Il casse les urnes", "Il vole les bulletins", "Il ferme les bureaux"], "Voter est un droit et un acte de citoyenneté.", 3),
    ("Que signifie la devise « Paix, Travail, Patrie » ?", "La paix, le travail et l'amour du pays", ["La liberté, l'égalité et la fraternité", "L'ordre, le progrès et la force", "La foi, l'espoir et la charité"], "Elle résume les valeurs de la République.", 3),
    ("Que symbolise l'étoile du drapeau camerounais ?", "L'unité nationale", ["La richesse du pays", "La paix mondiale", "Le soleil du Nord"], "L'étoile est placée au centre de la bande rouge.", 4),
    ("Quel est le titre de l'hymne national du Cameroun ?", "Ô Cameroun, berceau de nos ancêtres", ["La Camerounaise du matin", "Debout, peuples du Nord", "Chant de l'unité"], "Il est chanté debout lors des cérémonies.", 4),
    ("Le Cameroun a deux langues officielles. Lesquelles ?", "Le français et l'anglais", ["Le français et l'arabe", "L'anglais et l'espagnol", "Le français et le portugais"], "Elles sont égales dans la Constitution.", 3),
    ("Quel type d'État est le Cameroun d'après sa Constitution ?", "Un État unitaire décentralisé", ["Une monarchie", "Une fédération de villes", "Un empire"], "Il est dirigé par un président de la République.", 5),
    ("Que doit faire un piéton avant de traverser ?", "Regarder des deux côtés et traverser sur le passage piéton", ["Courir sans regarder", "Traverser derrière un bus", "Traverser dans un virage"], "La prudence évite les accidents.", 2),
    ("Que doit faire un conducteur quand le feu est rouge ?", "S'arrêter", ["Accélérer", "Klaxonner", "Doubler la file"], "Le rouge est un arrêt obligatoire.", 2),
    ("Que signifie un panneau carré ou rectangulaire bleu ?", "Une indication", ["Une interdiction", "Un danger", "Un arrêt obligatoire"], "Les panneaux bleus donnent des informations.", 4),
    ("Que signifie un panneau rond bleu ?", "Une obligation", ["Un danger", "Une interdiction", "Une indication de ville"], "Par exemple, une direction obligatoire.", 5),
    ("Quel équipement un motocycliste doit-il porter ?", "Un casque", ["Un chapeau", "Un bonnet de laine", "Un foulard"], "Le casque sauve des vies.", 2),
    ("Que faire en cas d'incendie en classe ?", "Sortir calmement par l'issue prévue", ["Courir dans tous les sens", "Se cacher sous la table", "Ouvrir les fenêtres et attendre"], "On suit les consignes et on garde son calme.", 3),
    ("Qu'est-ce que la corruption ?", "Obtenir un avantage en donnant de l'argent de façon illégale", ["Payer une facture en banque", "Faire un cadeau d'anniversaire", "Aider un voisin à porter un sac"], "La corruption est interdite par la loi.", 5),
    ("Que fait-on pour respecter les biens publics ?", "On évite de les casser ou de les abîmer", ["On les emporte chez soi", "On écrit dessus", "On les vend"], "Ils appartiennent à toute la communauté.", 3),
    ("À quoi servent les impôts et les taxes ?", "À financer les services publics", ["À acheter des cadeaux aux ministres", "À remplacer l'école", "À payer les vacances des citoyens"], "Les impôts servent à construire routes, écoles et hôpitaux.", 4),
    ("Qu'est-ce que la tolérance ?", "Accepter que les autres pensent ou vivent différemment", ["Se moquer des différences", "Imposer son avis", "Éviter tout le monde"], "La tolérance aide à vivre ensemble dans un pays divers.", 4),
    ("Quelle journée célèbre-t-on le 8 mars dans le monde ?", "La Journée internationale des droits des femmes", ["La fête de la Jeunesse", "La fête de l'Unité", "La fête du Travail"], "Elle rappelle le droit des femmes.", 4),
    ("Quel droit permet de dire ce qu'on pense dans le respect des autres ?", "La liberté d'expression", ["Le droit de conduire", "Le droit de retraite", "La liberté de voler"], "Elle a des limites fixées par la loi.", 5),
    ("Quelle date célèbre-t-on la fête de la Jeunesse au Cameroun ?", "Le 11 février", ["Le 20 mai", "Le 1er mai", "Le 15 août"], "Elle rend hommage à la jeunesse du pays.", 3),
    ("Quelle est la date de la fête nationale du Cameroun ?", "Le 20 mai", ["Le 11 février", "Le 1er janvier", "Le 1er octobre"], "On commémore la naissance de l'État unitaire.", 3),
], "pcp-ecm", ECM, CMR)
