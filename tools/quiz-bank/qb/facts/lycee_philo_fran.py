"""Philosophie (Tle) et français / littérature (2nde, 1re, Tle) : auteurs, œuvres, notions, figures de style, versification.
Faits écrits par l'assistant, tous `review` : à relire par un professeur de philosophie et de lettres. Questions originales."""
from .. import lycee
from ..facts_engine import fq, pick, source
from .lycee_common import put, put_levels

source("ly-philo", "Philosophie, Terminale (notions, auteurs, œuvres)", "programme scolaire", "Comparer avec le programme MINESEC de philosophie (séries A, C, D, E) et les manuels agréés ; vérifier les dates d'œuvres.")
source("ly-fran", "Français et littérature, second cycle (œuvres, genres, figures, versification)", "programme scolaire", "Comparer avec les programmes MINESEC de français (2nde, 1re, Tle) et la liste des œuvres au programme ; vérifier les dates de première publication.")
PH = lycee.fr("tle", "philo")

# ---------------------------------------------------------------------------------------------- philosophie
AUTH_WORKS = [   # (auteur, œuvre)
    ("Platon", "La République"), ("Aristote", "Éthique à Nicomaque"), ("Descartes", "Discours de la méthode"), ("Pascal", "Pensées"), ("Spinoza", "Éthique"), ("Hobbes", "Léviathan"),
    ("Locke", "Essai sur l'entendement humain"), ("Montesquieu", "De l'esprit des lois"), ("Rousseau", "Du contrat social"), ("Kant", "Critique de la raison pure"), ("Hegel", "Phénoménologie de l'esprit"),
    ("Marx", "Le Capital"), ("Nietzsche", "Ainsi parlait Zarathoustra"), ("Bergson", "Essai sur les données immédiates de la conscience"), ("Freud", "L'Interprétation des rêves"),
    ("Sartre", "L'Être et le Néant"), ("Camus", "Le Mythe de Sisyphe"), ("Simone de Beauvoir", "Le Deuxième Sexe"), ("Cheikh Anta Diop", "Nations nègres et culture"), ("Placide Tempels", "La Philosophie bantoue"),
]
for a, b in AUTH_WORKS:
    wa = pick("phw" + b, [x[0] for x in AUTH_WORKS], a, 8)
    wb = pick("phb" + a, [x[1] for x in AUTH_WORKS], b, 8)
    fq(PH, "ph-works-auth", f"Quel penseur est l'auteur de « {b} » ?", a, wa, f"« {b} » est un ouvrage de {a}.", "ly-philo", "WORLD", "Philosophie", 3)
    fq(PH, "ph-works-title", f"Lequel de ces ouvrages a été écrit par {a} ?", b, wb, f"{a} est l'auteur de « {b} ».", "ly-philo", "WORLD", "Philosophie", 3)

PHILO = [
    ("Qui a énoncé la formule « Je pense, donc je suis » ?", "Descartes", ["Pascal", "Kant", "Platon", "Voltaire"], "C'est le cogito de Descartes, point de départ de sa recherche de certitude.", 1),
    ("Dans quelle œuvre de Platon trouve-t-on l'allégorie de la caverne ?", "La République", ["Le Banquet", "Phédon", "Les Lois", "Apologie de Socrate"], "Elle ouvre le livre VII de La République.", 2),
    ("Que symbolise la sortie de la caverne dans l'allégorie de Platon ?", "L'accès à la connaissance vraie", ["La mort physique", "Le voyage", "La richesse", "Le sommeil"], "Le prisonnier libéré découvre le monde réel puis le Soleil, image du Bien.", 2),
    ("Quelle formule est attribuée à Socrate et gravée au temple de Delphes ?", "Connais-toi toi-même", ["Pense par toi-même", "L'homme est la mesure de toute chose", "Je pense, donc je suis", "Rien de trop"], "Socrate en fait le principe de sa démarche philosophique.", 2),
    ("Selon Aristote, l'homme est par nature…", "Un animal politique", ["Un loup pour l'homme", "Un roseau pensant", "Un être pour la mort", "Un animal sans raison"], "L'homme vit naturellement dans la cité (Politique).", 2),
    ("Quelle expression de Pascal désigne l'être humain, fragile mais pensant ?", "Un roseau pensant", ["Un animal politique", "Un loup pour l'homme", "Une passion inutile", "Un automate"], "« L'homme n'est qu'un roseau, le plus faible de la nature, mais c'est un roseau pensant. »", 2),
    ("Selon Hobbes, comment est l'homme à l'état de nature ?", "En guerre de tous contre tous", ["Naturellement bon", "Heureux et libre", "Soumis à Dieu", "Vivant en société parfaite"], "L'état de nature est une guerre de chacun contre chacun, d'où le besoin d'un pouvoir.", 3),
    ("Selon Rousseau, que devient le pouvoir dans un contrat social légitime ?", "Il repose sur la volonté générale", ["Il appartient au plus fort", "Il vient de Dieu seul", "Il est héréditaire", "Il est partagé par tirage au sort"], "La souveraineté appartient au peuple qui exprime la volonté générale.", 3),
    ("Quel penseur des Lumières a défendu la séparation des pouvoirs législatif, exécutif et judiciaire ?", "Montesquieu", ["Rousseau", "Hobbes", "Descartes", "Pascal"], "Elle protège la liberté contre l'abus de pouvoir (De l'esprit des lois).", 2),
    ("Quelle devise kantienne résume l'esprit des Lumières ?", "Sapere aude : ose te servir de ton propre entendement", ["Je pense, donc je suis", "Rien n'est sans raison", "Connais-toi toi-même", "L'enfer, c'est les autres"], "Kant, Qu'est-ce que les Lumières ? (1784).", 3),
    ("Quelle règle morale Kant énonce-t-il comme impératif catégorique ?", "Agir selon une maxime qui puisse valoir comme loi universelle", ["Agir pour son plaisir", "Obéir à l'État", "Chercher le plus grand bonheur", "Suivre ses désirs"], "L'impératif est inconditionnel et universalisable.", 4),
    ("Quelle doctrine soutient que toute connaissance vient de l'expérience sensible ?", "L'empirisme", ["Le rationalisme", "L'idéalisme absolu", "Le dogmatisme", "Le fidéisme"], "Locke et Hume sont des empiristes.", 2),
    ("Quelle doctrine affirme que la raison est la source principale de la connaissance ?", "Le rationalisme", ["L'empirisme", "Le scepticisme", "Le sensualisme", "Le pragmatisme"], "Descartes est un rationaliste.", 2),
    ("Quelle attitude consiste à douter de la possibilité d'atteindre la vérité ?", "Le scepticisme", ["Le dogmatisme", "Le positivisme", "Le rationalisme", "L'optimisme"], "Le sceptique suspend son jugement.", 2),
    ("Comment appelle-t-on le raisonnement qui va du général au particulier ?", "La déduction", ["L'induction", "L'analogie", "L'intuition", "L'hypothèse"], "Le syllogisme est un raisonnement déductif.", 2),
    ("Comment appelle-t-on le raisonnement qui part de cas particuliers pour établir une loi générale ?", "L'induction", ["La déduction", "Le syllogisme", "La démonstration", "La définition"], "L'induction généralise à partir d'observations.", 2),
    ("Quelle est la structure classique du syllogisme « Tous les hommes sont mortels ; Socrate est un homme ; donc Socrate est mortel » ?", "Majeure, mineure, conclusion", ["Thèse, antithèse, synthèse", "Introduction, développement, conclusion", "Hypothèse, expérience, loi", "Problème, solution, preuve"], "La majeure et la mineure sont les prémisses.", 3),
    ("Que signifie « a priori » en philosophie ?", "Indépendant de l'expérience", ["Après l'expérience", "Probable", "Faux", "Incertain"], "Une connaissance a priori ne dépend pas de l'expérience.", 3),
    ("Qui est l'auteur de la formule « L'existence précède l'essence » ?", "Jean-Paul Sartre", ["Albert Camus", "Henri Bergson", "Emmanuel Kant", "Blaise Pascal"], "Pour Sartre, l'homme se définit par ses actes.", 2),
    ("Qui a écrit « On ne naît pas femme : on le devient » ?", "Simone de Beauvoir", ["Hannah Arendt", "Simone Weil", "Marguerite Yourcenar", "Mariama Bâ"], "Le Deuxième Sexe (1949).", 3),
    ("Quel philosophe a annoncé « Dieu est mort » ?", "Friedrich Nietzsche", ["Karl Marx", "Sigmund Freud", "Arthur Schopenhauer", "Jean-Paul Sartre"], "Formule du Gai Savoir, souvent mal comprise comme un slogan athée.", 3),
    ("Quelles sont les trois instances de l'appareil psychique selon Freud (seconde topique) ?", "Le ça, le moi et le surmoi", ["L'âme, le corps et l'esprit", "La raison, la volonté et l'imagination", "Le conscient, le réel et le virtuel", "La mémoire, l'oubli et l'habitude"], "Freud est le fondateur de la psychanalyse.", 3),
    ("Quelle théorie, fondée par Freud, étudie les processus inconscients ?", "La psychanalyse", ["La phénoménologie", "Le behaviorisme", "Le positivisme", "L'existentialisme"], "Elle explore rêves, lapsus et symptômes.", 2),
    ("Quel est le principe central du marxisme dans l'analyse de l'histoire ?", "La lutte des classes", ["La volonté générale", "Le cogito", "L'éternel retour", "Le contrat social"], "Marx et Engels : l'histoire des sociétés est une histoire de luttes de classes.", 2),
    ("Quel ouvrage Marx et Engels ont-ils publié en 1848 ?", "Le Manifeste du parti communiste", ["Le Capital", "L'Idéologie allemande", "La Sainte Famille", "Misère de la philosophie"], "Il se termine par « Prolétaires de tous les pays, unissez-vous ! ».", 3),
    ("Quelle notion bergsonienne désigne le temps vécu par la conscience, par opposition au temps mesuré ?", "La durée", ["L'instant", "La chronologie", "L'éternité", "Le moment"], "Bergson distingue durée vécue et temps spatialisé.", 3),
    ("Quel philosophe a fait du mythe de Sisyphe l'image de l'absurde ?", "Albert Camus", ["Jean-Paul Sartre", "Platon", "Friedrich Nietzsche", "Emmanuel Levinas"], "Le Mythe de Sisyphe, 1942.", 2),
    ("Qu'appelle-t-on la « loi des trois états » d'Auguste Comte ?", "Les états théologique, métaphysique et positif", ["Les états solide, liquide et gazeux", "Les états de nature, civil et moral", "Les états primaire, secondaire et tertiaire", "Les états de veille, de rêve et de sommeil"], "Elle décrit les étapes de l'esprit humain.", 4),
    ("Quelle philosophie affirme que seuls les faits observables fondent la connaissance scientifique ?", "Le positivisme", ["Le romantisme", "L'idéalisme", "Le stoïcisme", "Le fatalisme"], "Auguste Comte en est le fondateur.", 3),
    ("Que désigne le déterminisme ?", "Le principe selon lequel tout phénomène a des causes qui le rendent nécessaire", ["Le droit de choisir librement", "La croyance au hasard", "Le contrôle des émotions", "La loi de l'État"], "Il s'oppose, en apparence, à la liberté.", 3),
    ("Selon Kant, qu'est-ce qui rend un acte moralement bon ?", "L'intention de faire son devoir", ["Le plaisir qu'il procure", "Le résultat obtenu", "L'approbation d'autrui", "L'utilité sociale"], "La bonne volonté est la seule chose bonne sans restriction.", 4),
    ("Comment appelle-t-on la doctrine morale qui juge une action à ses conséquences, au plus grand bonheur du plus grand nombre ?", "L'utilitarisme", ["Le stoïcisme", "Le formalisme kantien", "L'ascétisme", "Le scepticisme"], "Bentham et Mill en sont les représentants.", 4),
    ("Quel philosophe camerounais a publié « La crise du Muntu » (1977) ?", "Fabien Eboussi Boulaga", ["Marcien Towa", "Cheikh Anta Diop", "Achille Mbembe", "Paulin Hountondji"], "Il y discute l'authenticité africaine et la philosophie.", 4),
    ("Quel philosophe camerounais a écrit « Essai sur la problématique philosophique dans l'Afrique actuelle » (1971) ?", "Marcien Towa", ["Fabien Eboussi Boulaga", "Paulin Hountondji", "Léopold Sédar Senghor", "Alexis Kagame"], "Marcien Towa critique la philosophie de la négritude.", 4),
    ("Quelle expression désigne le mouvement culturel lancé par Césaire, Senghor et Damas dans les années 1930 ?", "La négritude", ["Le panafricanisme", "Le surréalisme", "L'existentialisme", "L'afrocentrisme"], "Elle affirme la valeur de la culture noire.", 2),
]
put(PH, "ph-notions", PHILO, "ly-philo", "Philosophie", "WORLD")

# ---------------------------------------------------------------------------------------------- français / littérature
F2, F1, FT = lycee.fr("2nde", "fran"), lycee.fr("1re", "fran"), lycee.fr("tle", "fran")
AFRICAN = {"Mongo Beti", "Ferdinand Oyono", "Cheikh Hamidou Kane", "Camara Laye", "Ahmadou Kourouma", "Mariama Bâ", "Aimé Césaire", "Sembène Ousmane", "Francis Bebey", "Calixthe Beyala", "Léonora Miano", "Léopold Sédar Senghor"}
OEUVRES = [   # (niveau, auteur, œuvre, année)
    ("2nde", "Molière", "Tartuffe", 1664), ("2nde", "Molière", "L'Avare", 1668), ("2nde", "Molière", "Le Bourgeois gentilhomme", 1670), ("2nde", "Molière", "Le Misanthrope", 1666),
    ("2nde", "Racine", "Andromaque", 1667), ("2nde", "Racine", "Phèdre", 1677), ("2nde", "Corneille", "Le Cid", 1637), ("2nde", "La Fontaine", "Fables", 1668), ("2nde", "Voltaire", "Candide", 1759),
    ("2nde", "Victor Hugo", "Les Misérables", 1862), ("2nde", "Victor Hugo", "Les Contemplations", 1856), ("2nde", "Victor Hugo", "Hernani", 1830), ("2nde", "Baudelaire", "Les Fleurs du mal", 1857),
    ("1re", "Flaubert", "Madame Bovary", 1857), ("1re", "Balzac", "Le Père Goriot", 1835), ("1re", "Stendhal", "Le Rouge et le Noir", 1830), ("1re", "Émile Zola", "Germinal", 1885),
    ("1re", "Maupassant", "Bel-Ami", 1885), ("1re", "Albert Camus", "L'Étranger", 1942), ("1re", "Albert Camus", "La Peste", 1947), ("1re", "Jean-Paul Sartre", "La Nausée", 1938),
    ("1re", "Guillaume Apollinaire", "Alcools", 1913), ("1re", "Marcel Proust", "Du côté de chez Swann", 1913), ("1re", "Samuel Beckett", "En attendant Godot", 1952), ("1re", "Eugène Ionesco", "La Cantatrice chauve", 1950),
    ("tle", "Mongo Beti", "Le Pauvre Christ de Bomba", 1956), ("tle", "Ferdinand Oyono", "Une vie de boy", 1956), ("tle", "Ferdinand Oyono", "Le Vieux Nègre et la médaille", 1956),
    ("tle", "Cheikh Hamidou Kane", "L'Aventure ambiguë", 1961), ("tle", "Camara Laye", "L'Enfant noir", 1953), ("tle", "Ahmadou Kourouma", "Les Soleils des indépendances", 1968),
    ("tle", "Mariama Bâ", "Une si longue lettre", 1979), ("tle", "Aimé Césaire", "Cahier d'un retour au pays natal", 1939), ("tle", "Sembène Ousmane", "Les Bouts de bois de Dieu", 1960),
    ("tle", "Francis Bebey", "Le Fils d'Agatha Moudio", 1967), ("tle", "Calixthe Beyala", "C'est le soleil qui m'a brûlée", 1987), ("tle", "Léonora Miano", "Contours du jour qui vient", 2006),
    ("tle", "Léopold Sédar Senghor", "Chants d'ombre", 1945), ("tle", "Mongo Beti", "Mission terminée", 1957), ("tle", "Malraux", "La Condition humaine", 1933),
]
for lvl, auth, work, year in OEUVRES:
    course = lycee.fr(lvl, "fran")
    same = [o for o in OEUVRES if o[1] == auth]
    auths = sorted({o[1] for o in OEUVRES} - {auth})
    works = sorted({o[2] for o in OEUVRES if o[1] != auth})
    region = "AF" if auth in AFRICAN else "WORLD"
    fq(course, "fr-oeuvre-auteur", f"Qui est l'auteur de « {work} » ?", auth, pick("fa" + work, auths, auth, 8), f"« {work} » ({year}) est de {auth}.", "ly-fran", region, "Littérature", 2 if lvl != "tle" else 3)
    if len(same) == 1:
        fq(course, "fr-auteur-oeuvre", f"Laquelle de ces œuvres a été écrite par {auth} ?", work, pick("fw" + auth, works, work, 8), f"{auth} est l'auteur de « {work} » ({year}).", "ly-fran", region, "Littérature", 3)

FRAN = [
    ("2nde", "Comment appelle-t-on une comparaison sans outil de comparaison, qui identifie deux réalités ?", "Une métaphore", ["Une comparaison", "Une litote", "Une antithèse", "Une anaphore"], "« Cet homme est un lion » est une métaphore.", 1),
    ("2nde", "Quelle figure rapproche deux termes par un outil de comparaison (comme, tel, semblable à) ?", "La comparaison", ["La métaphore", "L'hyperbole", "L'oxymore", "L'allégorie"], "« Rapide comme l'éclair ».", 1),
    ("2nde", "Quelle figure exagère pour frapper l'esprit (« Je meurs de faim ») ?", "L'hyperbole", ["La litote", "L'euphémisme", "La métonymie", "L'ellipse"], "Elle amplifie volontairement.", 1),
    ("2nde", "Quelle figure dit moins pour faire entendre plus (« Va, je ne te hais point ») ?", "La litote", ["L'hyperbole", "L'oxymore", "La gradation", "La personnification"], "Chimène à Rodrigue dans Le Cid : « Va, je ne te hais point ».", 2),
    ("2nde", "Quelle figure associe deux mots de sens contraires (« cette obscure clarté ») ?", "L'oxymore", ["L'antithèse seulement", "La comparaison", "La métonymie", "L'ironie"], "L'oxymore réunit deux termes contradictoires dans un même groupe.", 2),
    ("2nde", "Quelle figure oppose deux idées ou deux mots dans une même phrase (« Le cœur est chaud, la tête est froide ») ?", "L'antithèse", ["L'oxymore", "L'allégorie", "La métaphore filée", "La métonymie"], "Les termes opposés restent distincts.", 2),
    ("2nde", "Quelle figure consiste à répéter un mot ou un groupe de mots en début de phrases ou de vers successifs ?", "L'anaphore", ["L'allitération", "La rime", "L'ellipse", "La gradation"], "Elle crée un effet de rythme et d'insistance.", 2),
    ("2nde", "Comment appelle-t-on la figure qui donne des traits humains à une chose ou à un animal ?", "La personnification", ["La métonymie", "L'hyperbole", "La périphrase", "La litote"], "« La mer murmure ».", 1),
    ("2nde", "Quelle figure remplace un mot par un autre qui lui est lié (« boire un verre »), la partie pour le tout, le contenant pour le contenu ?", "La métonymie", ["La métaphore", "La comparaison", "L'allégorie", "L'ironie"], "« Boire un verre » : le contenant pour le contenu.", 3),
    ("2nde", "Comment appelle-t-on l'énumération d'éléments rangés par ordre croissant ou décroissant d'intensité ?", "La gradation", ["La litote", "L'anaphore", "L'ellipse", "La chute"], "« C'est un roc ! C'est un pic ! C'est un cap ! »", 2),
    ("2nde", "Combien de syllabes comporte un alexandrin ?", "12", ["8", "10", "14", "6"], "Il se coupe en deux hémistiches de 6 syllabes.", 1),
    ("2nde", "Combien de vers compte un sonnet ?", "14", ["10", "12", "16", "8"], "Deux quatrains suivis de deux tercets.", 1),
    ("2nde", "Comment se compose un sonnet classique ?", "Deux quatrains puis deux tercets", ["Trois quatrains puis un distique", "Quatre tercets", "Un huitain et un sizain", "Deux quintiles et un distique"], "14 vers au total.", 2),
    ("2nde", "Comment appelle-t-on une strophe de quatre vers ?", "Un quatrain", ["Un tercet", "Un distique", "Un sizain", "Un huitain"], "Quatre vers.", 1),
    ("2nde", "Comment appelle-t-on une strophe de trois vers ?", "Un tercet", ["Un quatrain", "Un distique", "Un quintil", "Un sizain"], "Trois vers.", 1),
    ("2nde", "Quelle disposition de rimes correspond au schéma ABAB ?", "Les rimes croisées", ["Les rimes plates", "Les rimes embrassées", "Les rimes mêlées", "Les rimes suivies"], "Elles alternent.", 2),
    ("2nde", "Quelle disposition de rimes correspond au schéma AABB ?", "Les rimes plates (suivies)", ["Les rimes croisées", "Les rimes embrassées", "Les rimes alternées", "Les rimes pauvres"], "Les rimes se suivent par paires.", 2),
    ("2nde", "Quelle disposition de rimes correspond au schéma ABBA ?", "Les rimes embrassées", ["Les rimes plates", "Les rimes croisées", "Les rimes suivies", "Les rimes riches"], "Les rimes B sont « embrassées » par les rimes A.", 2),
    ("2nde", "Comment appelle-t-on la pause à l'intérieur d'un alexandrin, le plus souvent après la sixième syllabe ?", "La césure", ["La rime", "L'enjambement", "Le rejet", "La diérèse"], "Elle sépare les deux hémistiches.", 3),
    ("2nde", "Comment appelle-t-on un poème de forme fixe à vocation lyrique de quatorze vers ?", "Un sonnet", ["Une ode", "Une ballade", "Un rondeau", "Une épigramme"], "Pétrarque puis Ronsard l'ont popularisé.", 2),
    ("2nde", "Quel siècle est celui du classicisme français (Corneille, Racine, Molière, La Fontaine) ?", "Le XVIIe siècle", ["Le XVIe siècle", "Le XVIIIe siècle", "Le XIXe siècle", "Le XXe siècle"], "Le règne de Louis XIV en est le cadre.", 2),
    ("2nde", "Quel mouvement du XVIIIe siècle défend la raison, la tolérance et le progrès ?", "Les Lumières", ["Le romantisme", "Le réalisme", "Le symbolisme", "Le surréalisme"], "Voltaire, Diderot, Montesquieu, Rousseau.", 2),
    ("2nde", "Qu'est-ce qu'une comédie ?", "Une pièce de théâtre qui vise à faire rire en peignant les mœurs", ["Une pièce qui finit toujours mal", "Un poème épique", "Un roman policier", "Un récit autobiographique"], "Molière en est le maître au XVIIe siècle.", 1),
    ("2nde", "Qu'est-ce qu'une tragédie classique ?", "Une pièce en vers où des personnages nobles affrontent un destin funeste", ["Une pièce comique en prose", "Un récit de voyage", "Un conte moral", "Un roman réaliste"], "Phèdre de Racine en est un exemple.", 2),
    ("2nde", "Comment appelle-t-on un récit bref à visée morale, souvent en vers, mettant en scène des animaux ?", "Une fable", ["Un conte philosophique", "Une épopée", "Une nouvelle", "Une chronique"], "Les Fables de La Fontaine.", 1),
    ("1re", "Quel mouvement littéraire du XIXe siècle cherche à représenter fidèlement la société (Balzac, Flaubert) ?", "Le réalisme", ["Le romantisme", "Le surréalisme", "Le symbolisme", "Le classicisme"], "Il observe la société avec précision.", 2),
    ("1re", "Quel mouvement, dont Zola est le chef de file, applique à la littérature la méthode scientifique ?", "Le naturalisme", ["Le romantisme", "Le réalisme", "Le surréalisme", "Le parnasse"], "Zola écrit le cycle des Rougon-Macquart.", 3),
    ("1re", "Quel mouvement du XIXe siècle exalte les sentiments, la nature et le moi (Hugo, Lamartine, Musset) ?", "Le romantisme", ["Le classicisme", "Le naturalisme", "Le réalisme", "Le surréalisme"], "Il refuse les règles du classicisme.", 2),
    ("1re", "Quel mouvement poétique de la fin du XIXe siècle (Verlaine, Mallarmé, Rimbaud) cherche à suggérer plutôt qu'à décrire ?", "Le symbolisme", ["Le parnasse", "Le naturalisme", "Le classicisme", "Le réalisme"], "Il privilégie la musicalité et le symbole.", 3),
    ("1re", "Quel mouvement, dont André Breton publie le manifeste en 1924, exalte le rêve et l'inconscient ?", "Le surréalisme", ["Le dadaïsme", "L'existentialisme", "Le symbolisme", "Le nouveau roman"], "Breton, Éluard, Aragon, Desnos.", 3),
    ("1re", "Quel mouvement littéraire a été lancé dans les années 1930 à Paris par Césaire, Senghor et Damas ?", "La négritude", ["Le surréalisme", "Le panafricanisme", "L'existentialisme", "Le naturalisme"], "La revue L'Étudiant noir (1934) en est l'un des berceaux.", 2),
    ("1re", "Quelle œuvre de Camus est un roman de l'absurde, avec le personnage de Meursault ?", "L'Étranger", ["La Peste", "La Chute", "Le Premier Homme", "Les Justes"], "Meursault, condamné à mort après un meurtre sur une plage d'Alger.", 2),
    ("1re", "Quel courant philosophique et littéraire est associé à Sartre ?", "L'existentialisme", ["Le romantisme", "Le naturalisme", "Le classicisme", "Le surréalisme"], "« L'existence précède l'essence ».", 3),
    ("1re", "Quelle forme théâtrale du XXe siècle est liée à Beckett et Ionesco ?", "Le théâtre de l'absurde", ["Le drame romantique", "La tragédie classique", "Le vaudeville", "Le théâtre épique"], "Dialogue éclaté, situations privées de sens apparent.", 3),
    ("1re", "Quel point de vue narratif sait tout des personnages, y compris leurs pensées ?", "Le point de vue omniscient (focalisation zéro)", ["Le point de vue externe", "Le point de vue interne", "Le point de vue subjectif", "Le point de vue d'un témoin"], "Le narrateur en sait plus que les personnages.", 3),
    ("1re", "Comment appelle-t-on un récit où le narrateur raconte sa propre vie ?", "Une autobiographie", ["Une biographie", "Un mémoire de recherche", "Un roman historique", "Une chronique"], "Auteur, narrateur et personnage sont une même personne.", 2),
    ("1re", "Quel temps verbal exprime une action brève et achevée dans un récit au passé écrit ?", "Le passé simple", ["L'imparfait", "Le plus-que-parfait", "Le futur antérieur", "Le conditionnel présent"], "Le passé simple rythme les événements.", 2),
    ("1re", "Quel temps verbal décrit habituellement le cadre ou une action qui dure dans un récit au passé ?", "L'imparfait", ["Le passé simple", "Le passé composé", "Le futur simple", "Le présent de vérité générale"], "Il sert aux descriptions et aux habitudes.", 2),
    ("1re", "Quelle est la nature du mot « mais » ?", "Une conjonction de coordination", ["Une préposition", "Un adverbe", "Une conjonction de subordination", "Un pronom"], "Mais, ou, et, donc, or, ni, car.", 2),
    ("1re", "Quelle est la fonction de « le livre » dans « Marie lit le livre » ?", "Complément d'objet direct", ["Sujet", "Complément circonstanciel", "Attribut du sujet", "Complément d'objet indirect"], "Le livre complète le verbe sans préposition.", 2),
    ("tle", "Quel roman de Mongo Beti raconte la tournée d'un missionnaire en pays beti, vue par son jeune boy ?", "Le Pauvre Christ de Bomba", ["Mission terminée", "Ville cruelle", "Le Roi miraculé", "Perpétue et l'habitude du malheur"], "Roman qui critique la mission coloniale.", 3),
    ("tle", "Quel roman de Ferdinand Oyono suit Toundi, un jeune boy au service d'un commandant ?", "Une vie de boy", ["Le Vieux Nègre et la médaille", "Chemin d'Europe", "Le Pauvre Christ de Bomba", "L'Enfant noir"], "Toundi tient son journal.", 3),
    ("tle", "Quel auteur camerounais a écrit « Le Fils d'Agatha Moudio » ?", "Francis Bebey", ["Mongo Beti", "Ferdinand Oyono", "Calixthe Beyala", "René Philombe"], "Roman humoristique, Grand prix littéraire de l'Afrique noire.", 3),
    ("tle", "Quel roman sénégalais est écrit sous forme de lettre ?", "Une si longue lettre", ["L'Aventure ambiguë", "Les Bouts de bois de Dieu", "Le Devoir de violence", "Ville cruelle"], "Mariama Bâ y raconte la polygamie et le veuvage.", 3),
    ("tle", "Quel poète a écrit « Cahier d'un retour au pays natal » ?", "Aimé Césaire", ["Léopold Sédar Senghor", "Léon-Gontran Damas", "David Diop", "Tchicaya U Tam'si"], "Poème fondateur de la négritude.", 3),
    ("tle", "Dans quel mouvement littéraire s'inscrit le poème « Femme noire » de Senghor ?", "La négritude", ["Le surréalisme", "Le réalisme", "Le naturalisme", "L'existentialisme"], "Senghor chante la beauté de la femme noire.", 3),
    ("tle", "Quelle est la caractéristique principale d'un roman à thèse ?", "Il défend explicitement une idée ou une cause", ["Il raconte une enquête policière", "Il est écrit en vers", "Il décrit uniquement la nature", "Il est sans personnages"], "Il vise à convaincre le lecteur.", 3),
    ("tle", "Comment appelle-t-on un texte qui cherche à convaincre en utilisant des arguments et des exemples ?", "Un texte argumentatif", ["Un texte narratif", "Un texte descriptif", "Un texte explicatif", "Un texte injonctif"], "Il comporte une thèse, des arguments et des exemples.", 2),
    ("tle", "Quelle est la différence entre « convaincre » et « persuader » ?", "Convaincre s'adresse à la raison, persuader aux sentiments", ["Il n'y en a aucune", "Convaincre est plus fort que persuader en tout cas", "Persuader s'adresse à la raison, convaincre aux sentiments", "Convaincre se fait à l'écrit, persuader à l'oral"], "On convainc par les arguments, on persuade en touchant l'émotion.", 3),
    ("tle", "Qu'appelle-t-on un plaidoyer ?", "Un discours qui défend une cause ou une personne", ["Un récit de voyage", "Une description d'un paysage", "Un journal intime", "Un poème lyrique"], "Il est caractéristique de l'éloquence judiciaire et politique.", 3),
    ("tle", "Quel registre cherche à émouvoir par la compassion ou la tristesse ?", "Le registre pathétique", ["Le registre comique", "Le registre épique", "Le registre polémique", "Le registre didactique"], "Il touche la sensibilité du lecteur.", 3),
    ("tle", "Quel registre vise à faire rire ?", "Le registre comique", ["Le registre tragique", "Le registre lyrique", "Le registre épique", "Le registre pathétique"], "Il passe par le comique de gestes, de mots, de situation ou de caractère.", 2),
]
put_levels(lycee.fr, "fran", "fr-notions", [(l, q, r, w, e, d) for l, q, r, w, e, d in FRAN], "ly-fran", "Français et littérature", "WORLD")
