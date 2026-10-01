"""L1 sociologie : méthodologie du travail universitaire et de la recherche (recherche documentaire, citation, plan, démarche
scientifique, variables, échantillonnage, enquête, esprit critique, éthique, organisation du travail).
Notions de cours générales, valables quel que soit le pays ; aucune règle de présentation propre à une université n'est posée. Tout est `review`."""
from .sup_kit import *  # noqa: F401,F403
from .sup_kit import mcq as _mcq


def mcq(course, tpl, rows, **kw):
    """Les énoncés à compléter (finissant par « : ») deviennent des questions."""
    fixed = []
    for q, right, wrongs, expl, d in rows:
        if q.rstrip().endswith(":"):
            q = "Complétez la phrase « %s … » : quelle suite est exacte ?" % q.rstrip()[:-1].rstrip()
        fixed.append((q, right, wrongs, expl, d))
    _mcq(course, tpl, fixed, **kw)


C = "l1-socio"
CAT = "Méthodologie"

source("sup-metho-doc", "Recherche documentaire, sources, citation, plagiat, bibliographie", "cours de méthodologie du travail universitaire",
       "Comparer avec un manuel de méthodologie du travail universitaire et les consignes de présentation de l'établissement (normes de citation variables).")
source("sup-metho-demarche", "Démarche scientifique, problématique, hypothèse, variables, plan", "cours de méthodologie de la recherche",
       "Comparer avec un manuel de méthodologie en sciences sociales (ex. Quivy et Van Campenhoudt) et les exigences de la faculté.")
source("sup-metho-enquete", "Échantillonnage, enquête, instruments de collecte, analyse", "cours de méthodologie de la recherche",
       "Comparer avec un manuel de méthodes d'enquête en sciences sociales et le cours du département.")
source("sup-metho-critique", "Esprit critique, biais, fiabilité et validité, éthique", "cours de méthodologie de la recherche",
       "Comparer avec un manuel de méthodologie, de logique et d'éthique de la recherche.")
source("sup-metho-travail", "Mémoire, rapport, exposé, prise de notes, gestion du temps", "cours de méthodologie du travail universitaire",
       "Comparer avec les consignes de l'établissement pour les travaux écrits et oraux.")

# ------------------------------------------------------------------ recherche documentaire, citation
table(C, "met-doc", [
    ("une source primaire", "un document original, produit au moment des faits ou directement par l'auteur de la recherche", 2),
    ("une source secondaire", "un document qui analyse, commente ou synthétise des sources primaires", 2),
    ("une source tertiaire", "un ouvrage de synthèse regroupant des connaissances déjà établies, comme une encyclopédie", 3),
    ("la littérature grise", "les documents diffusés hors des circuits de l'édition commerciale, comme des rapports ou des thèses", 4),
    ("une revue à comité de lecture", "une revue dont les articles sont évalués par des pairs avant publication", 3),
    ("un mot-clé", "un terme représentatif du sujet, utilisé pour interroger un catalogue ou une base de données", 1),
    ("un opérateur booléen", "un mot tel que ET, OU ou SAUF qui combine des termes dans une requête", 3),
    ("une base de données bibliographique", "un ensemble organisé de références permettant de retrouver des documents par interrogation", 3),
    ("un catalogue de bibliothèque", "un outil qui recense les documents d'une bibliothèque et permet de les localiser", 1),
    ("un résumé d'article (abstract)", "une courte présentation de l'objectif, de la méthode et des résultats d'un travail", 2),
    ("un index", "une liste alphabétique de termes ou de noms avec renvoi aux pages où ils apparaissent", 3),
    ("un identifiant DOI", "un code permanent attribué à un document électronique pour le retrouver durablement", 4),
    ("la recherche documentaire", "la démarche qui consiste à repérer, sélectionner et rassembler les documents utiles à un sujet", 1),
], cat=CAT, src="sup-metho-doc", fwd="Que désigne {a} ?", rev="Comment appelle-t-on {b} ?")

table(C, "met-cite", [
    ("le plagiat", "le fait de présenter comme siens le travail ou les idées d'autrui sans en indiquer l'origine", 1),
    ("une citation directe", "la reprise exacte d'un passage, placée entre guillemets avec sa référence", 2),
    ("une paraphrase", "la reformulation avec ses propres mots d'une idée d'autrui, avec indication de la source", 3),
    ("l'autoplagiat", "la réutilisation, sans le signaler, de son propre travail déjà rendu ou publié", 4),
    ("la mention « ibid. »", "le renvoi à la même référence que celle citée juste avant", 4),
    ("la mention « op. cit. »", "le renvoi à un ouvrage déjà cité plus haut dans le travail", 4),
    ("la mention « et al. »", "l'abréviation signalant la présence d'autres auteurs que celui nommé", 3),
    ("le signe […] dans une citation", "l'indication qu'un passage a été retranché du texte cité", 4),
    ("la mention « sic »", "l'indication que le terme ou l'erreur cité figure bien dans le texte d'origine", 5),
    ("une note de bas de page", "une remarque ou une référence placée en bas de la page à laquelle elle se rapporte", 2),
    ("une référence bibliographique", "l'ensemble des informations permettant d'identifier précisément une source", 2),
    ("une bibliographie", "la liste ordonnée des sources utilisées ou consultées pour réaliser un travail", 1),
], cat=CAT, src="sup-metho-doc", fwd="Que désigne ou signifie {a} ?", rev="Quelle notion correspond à ceci : {b} ?")

classify(C, "met-plag", {
    "du plagiat": ["copier un paragraphe d'un site internet sans guillemets ni source", "recopier le travail d'un camarade et le rendre sous son nom",
                   "traduire un passage d'un auteur étranger sans citer l'auteur", "reformuler légèrement un article en omettant de le mentionner"],
    "une citation correcte": ["reproduire une phrase entre guillemets avec auteur et année", "citer un passage exact en indiquant la page de l'ouvrage",
                              "mettre un extrait entre guillemets et renvoyer à la bibliographie", "signaler en note la source d'une idée reformulée"],
    "une pratique de falsification des données": ["inventer des réponses de questionnaires jamais recueillis", "supprimer des réponses qui contredisent l'hypothèse",
                                                 "modifier des chiffres pour obtenir un résultat attendu", "ajouter des enquêtés fictifs à l'échantillon"],
    "une bonne pratique de recherche": ["conserver les données brutes et noter la source de chaque information", "indiquer les limites de son enquête dans la conclusion",
                                       "citer les travaux qui contredisent son hypothèse", "faire relire son questionnaire avant de l'administrer"],
}, cat=CAT, src="sup-metho-doc",
    fwd="Comment qualifie-t-on le fait suivant : {item} ?", rev="Lequel de ces comportements relève de {group} ?", expl="« {item} » relève de {group}.")

mcq(C, "met-doc2", [
    ("Quelle est la première étape d'une recherche documentaire ?", "Délimiter le sujet et en dégager les mots-clés", ["Rédiger la conclusion", "Imprimer tous les documents trouvés", "Citer des sources au hasard", "Choisir la couverture"], "On ne cherche bien que lorsqu'on sait ce que l'on cherche.", 1),
    ("Pourquoi utilise-t-on des synonymes et des termes voisins dans une recherche documentaire ?", "Pour élargir la recherche aux documents qui emploient d'autres mots", ["Pour rendre le sujet plus flou", "Pour éviter de citer les auteurs", "Pour supprimer les doublons", "Pour allonger la bibliographie"], "Les documents n'emploient pas tous le même vocabulaire.", 2),
    ("Dans une requête, l'opérateur ET permet de :", "obtenir des documents contenant les deux termes à la fois", ["obtenir des documents contenant l'un ou l'autre terme", "exclure un terme", "chercher une expression approximative", "classer les résultats par année"], "ET restreint ; OU élargit ; SAUF exclut.", 3),
    ("Dans une requête, l'opérateur OU permet de :", "élargir la recherche aux documents contenant l'un ou l'autre terme", ["obtenir uniquement les documents contenant les deux termes", "exclure un terme", "limiter à une seule année", "supprimer les résultats"], "OU est utile pour réunir des synonymes.", 3),
    ("Dans une requête, l'opérateur SAUF (ou SANS) permet de :", "exclure les documents contenant un terme", ["réunir deux termes", "chercher par auteur", "trier par pertinence", "traduire un document"], "On écarte ainsi un sens indésirable.", 3),
    ("Pourquoi des guillemets autour d'une expression dans un moteur de recherche ?", "Pour rechercher cette expression exacte, mots dans le même ordre", ["Pour la traduire", "Pour la mettre en gras", "Pour exclure l'expression", "Pour augmenter la vitesse sans changer les résultats"], "La recherche porte sur la phrase telle quelle.", 3),
    ("Parmi ces documents, lequel est une source primaire pour un sociologue qui étudie les marchés d'une ville ?", "Ses notes d'observation prises sur le terrain", ["Un manuel de sociologie générale", "Une encyclopédie", "Un article de synthèse sur les marchés africains", "Un dictionnaire"], "Ce sont des données qu'il a produites lui-même.", 2),
    ("Parmi ces documents, lequel est une source secondaire pour un historien étudiant une période ?", "Un ouvrage d'un autre historien qui analyse cette période", ["Une lettre écrite à l'époque par un témoin", "Un registre officiel de l'époque", "Un journal de l'époque", "Un enregistrement d'un discours de l'époque"], "Il commente des sources primaires.", 2),
    ("Un manuel général de sociologie est le plus souvent une source :", "secondaire ou tertiaire", ["primaire", "falsifiée", "personnelle", "non scientifique par nature"], "Il synthétise des travaux d'autrui.", 3),
    ("Un entretien enregistré par le chercheur lui-même est, pour sa propre enquête :", "une donnée primaire", ["une source tertiaire", "un plagiat", "une revue de littérature", "une citation"], "Les données ont été produites par le chercheur.", 3),
    ("Quel est l'intérêt d'une revue de littérature ?", "Faire le point sur les connaissances déjà produites sur le sujet", ["Allonger le travail avec n'importe quel texte", "Éviter de formuler une problématique", "Supprimer la bibliographie", "Présenter les résultats de l'enquête"], "Elle situe le travail dans l'état de la recherche.", 2),
    ("Pourquoi vérifie-t-on l'auteur et la date d'une source en ligne ?", "Pour juger de sa fiabilité et de son actualité", ["Pour la rendre plus longue", "Pour la copier plus facilement", "Pour en faire un plagiat", "Pour changer son titre"], "L'origine et la date conditionnent la confiance accordée.", 1),
    ("Quel critère sert à juger la fiabilité d'un site internet ?", "L'identité de l'auteur ou de l'organisme et la présence de sources", ["Le nombre de couleurs", "La rapidité d'affichage", "Le nombre de publicités", "L'absence de date"], "Un site fiable présente son auteur et ses références.", 2),
    ("Une encyclopédie collaborative en ligne est, dans un travail universitaire :", "utile pour une première orientation mais rarement acceptée comme référence principale", ["La source scientifique idéale", "Interdite à la lecture", "Toujours falsifiée", "Équivalente à un article évalué par des pairs"], "On remonte aux sources qu'elle cite.", 3),
    ("Qu'est-ce qu'un article évalué par les pairs ?", "Un article relu et jugé par des spécialistes avant publication", ["Un article publié par des amis", "Un article payé par un sponsor", "Un article réservé aux étudiants", "Un article sans auteur"], "L'évaluation par les pairs garantit un contrôle de la qualité scientifique.", 3),
    ("Une bibliographie est :", "la liste des références des documents utilisés dans un travail", ["la liste des remerciements", "le résumé de l'enquête", "la table des matières", "la liste des abréviations"], "Elle figure généralement à la fin.", 1),
    ("Pourquoi une bibliographie est-elle généralement classée par ordre alphabétique des noms d'auteurs ?", "Pour retrouver facilement une référence", ["Pour cacher des sources", "Pour augmenter le nombre de pages", "Pour classer les auteurs par âge", "Pour placer les sources récentes en premier"], "L'ordre alphabétique facilite la consultation.", 2),
    ("Quelles informations se retrouvent généralement dans la référence d'un livre ?", "L'auteur, le titre, l'éditeur, le lieu et l'année", ["Le prix et la couleur de la couverture", "La date de lecture et l'avis du lecteur", "L'adresse du libraire", "Le nombre d'exemplaires vendus"], "Ces éléments identifient précisément le livre.", 2),
    ("Quelles informations se retrouvent dans la référence d'un article de revue ?", "L'auteur, le titre de l'article, le nom de la revue, l'année, le numéro et les pages", ["Le poids de la revue", "Le prix de l'abonnement", "L'âge de l'éditeur", "Le nom du lecteur"], "Elles permettent de retrouver l'article.", 3),
    ("Pourquoi cite-t-on ses sources ?", "Pour reconnaître le travail d'autrui et permettre de vérifier les informations", ["Pour allonger le texte", "Pour rendre l'auteur difficile à trouver", "Pour éviter de rédiger", "Pour faire plaisir à la bibliothèque"], "Citer relève de l'honnêteté intellectuelle.", 1),
    ("Un étudiant recopie mot à mot un paragraphe d'un livre et le place entre guillemets avec la référence. Est-ce du plagiat ?", "Non, c'est une citation correcte", ["Oui, car il a recopié", "Oui, car il a mis des guillemets", "Oui, car il a cité la source", "Oui, parce qu'il ne l'a pas reformulé"], "Guillemets et source suffisent à éviter le plagiat.", 2),
    ("Un étudiant reformule les idées d'un auteur avec ses propres mots mais ne cite pas l'auteur. Est-ce du plagiat ?", "Oui, car les idées restent celles d'autrui", ["Non, car les mots sont différents", "Non, car il a changé l'ordre des phrases", "Non, car il a changé la longueur du texte", "Non, tant que le texte est court"], "Le plagiat porte aussi sur les idées, pas seulement sur les mots.", 3),
    ("Faire rédiger son mémoire par une autre personne et le présenter comme sien constitue :", "une fraude, voisine du plagiat", ["Une bonne méthode de travail", "Une paraphrase", "Une citation", "Une revue de littérature"], "On s'attribue le travail d'autrui.", 2),
    ("Dans quel cas n'a-t-on pas besoin de citer une source ?", "Pour une connaissance très largement partagée et incontestée", ["Pour une statistique précise", "Pour une phrase copiée mot à mot", "Pour une idée originale d'un auteur", "Pour un résultat d'enquête d'autrui"], "On cite les idées, les données et les mots d'autrui, non les évidences communes.", 4),
    ("Quelle est la bonne façon d'insérer une longue citation directe ?", "La mettre entre guillemets (ou en retrait) et indiquer clairement la référence", ["La mêler au texte sans la signaler", "La retoucher sans le dire", "La placer en annexe sans référence", "La traduire sans l'indiquer"], "La fidélité de la citation est essentielle.", 3),
    ("Pourquoi une citation doit-elle être fidèle au texte d'origine ?", "Pour ne pas trahir la pensée de l'auteur", ["Pour augmenter la longueur du devoir", "Pour éviter les notes", "Pour éviter la bibliographie", "Pour changer l'avis de l'auteur"], "Toute coupure doit être signalée.", 2),
    ("Quelle différence existe-t-il entre résumer et paraphraser ?", "Le résumé condense l'ensemble d'un texte, la paraphrase reformule un passage précis dans ses propres mots", ["Il n'y en a aucune", "Le résumé copie mot à mot, la paraphrase invente", "La paraphrase est plus courte que le texte d'origine", "Le résumé ne cite jamais"], "Les deux exigent de citer la source.", 4),
    ("Que fait-on si l'on utilise une idée trouvée chez un auteur par l'intermédiaire d'un autre auteur ?", "On cite l'auteur réellement lu, en signalant l'auteur d'origine", ["On cite l'auteur original seulement sans l'avoir lu", "On ne cite personne", "On cite le premier venu", "On remplace l'idée"], "On ne cite que ce que l'on a réellement consulté, en signalant la source intermédiaire.", 5),
    ("Quelle différence entre une note de bas de page et une note de fin ?", "La première est placée en bas de chaque page, la seconde regroupée à la fin du texte ou du chapitre", ["La première est en fin, la seconde en début de page", "Aucune différence", "La première est écrite à la main", "La seconde est une citation directe"], "Même rôle, emplacement différent.", 3),
    ("Citer une source dans le texte avec le nom de l'auteur et l'année entre parenthèses relève :", "d'un système de renvoi auteur-date", ["d'un plagiat", "d'une annexe", "d'un glossaire", "d'une note de bas de page obligatoirement"], "La référence complète figure dans la bibliographie.", 4),
    ("Pourquoi vérifier que chaque référence citée dans le texte figure dans la bibliographie ?", "Pour assurer la cohérence et permettre au lecteur de retrouver la source", ["Pour allonger le travail", "Pour pouvoir supprimer le corps du travail", "Pour éviter de relire", "Pour tromper le correcteur"], "Texte et bibliographie doivent se correspondre.", 3),
    ("Un logiciel de gestion bibliographique sert à :", "enregistrer et mettre en forme les références", ["corriger l'orthographe seulement", "traduire les documents", "rédiger la conclusion à la place de l'étudiant", "supprimer les doublons dans les idées"], "Il aide à organiser les références et à générer la bibliographie.", 4),
    ("Un moteur de recherche généraliste :", "peut donner accès à des sources de qualité très variable, qu'il faut évaluer", ["ne donne que des sources scientifiques", "est toujours supérieur à une base spécialisée", "ne renvoie que des documents évalués par les pairs", "est interdit à l'université"], "Il faut juger la fiabilité des résultats.", 3),
    ("Une bibliothèque universitaire propose en général :", "des ouvrages, des revues et l'accès à des bases de données, ainsi que de l'aide à la recherche", ["uniquement des romans", "uniquement des manuels du primaire", "uniquement des journaux du jour", "aucun service d'aide"], "Les bibliothécaires aident à orienter les recherches.", 1),
], cat=CAT, src="sup-metho-doc")

# ------------------------------------------------------------------ démarche, problématique, plan
table(C, "met-dem", [
    ("la problématique", "la question centrale, issue d'un problème, à laquelle la recherche cherche à répondre", 2),
    ("l'hypothèse", "une réponse provisoire à la question de recherche, qu'il faudra vérifier", 2),
    ("l'objectif général", "le but principal que poursuit la recherche", 2),
    ("les objectifs spécifiques", "les étapes précises qui permettent d'atteindre l'objectif général", 3),
    ("la revue de littérature", "la synthèse des travaux déjà publiés sur le sujet", 2),
    ("le cadre théorique", "l'ensemble des concepts et théories qui éclairent et organisent l'analyse du problème", 3),
    ("la délimitation du sujet", "la précision du cadre de l'étude dans le temps, l'espace et la population concernée", 3),
    ("la méthodologie d'une recherche", "la présentation des méthodes et des outils de recueil et d'analyse des données", 2),
    ("le chronogramme", "le calendrier des étapes du travail de recherche", 3),
    ("un concept", "une idée générale et abstraite qui désigne une catégorie de phénomènes", 3),
    ("un indicateur", "une donnée observable qui permet de repérer et de mesurer un concept", 3),
    ("l'opérationnalisation d'un concept", "le passage du concept abstrait à des dimensions et indicateurs observables", 5),
    ("la pertinence d'un sujet", "l'intérêt scientifique et social qui justifie de l'étudier", 3),
    ("un terrain d'enquête", "le lieu et la population auprès desquels les données sont recueillies", 2),
], cat=CAT, src="sup-metho-demarche", fwd="Que désigne {a} dans un travail de recherche ?", rev="Comment appelle-t-on {b} ?")

table(C, "met-raison", [
    ("l'induction", "le raisonnement qui généralise à partir de cas particuliers observés", 3),
    ("la déduction", "le raisonnement qui part d'une règle générale pour en tirer une conséquence particulière", 3),
    ("la démarche hypothético-déductive", "la démarche qui formule une hypothèse puis la confronte aux faits pour la tester", 4),
    ("la falsifiabilité d'une affirmation", "la possibilité qu'elle soit contredite par une observation", 5),
    ("la corrélation entre deux phénomènes", "le fait qu'ils varient ensemble, sans que l'un cause forcément l'autre", 4),
    ("la causalité", "le lien par lequel un phénomène en produit un autre", 3),
    ("la rupture avec les prénotions", "l'abandon des idées toutes faites du sens commun au profit d'une analyse rigoureuse", 5),
    ("la neutralité axiologique", "l'effort de ne pas laisser ses jugements de valeur orienter l'analyse scientifique", 5),
    ("la généralisation", "l'extension des résultats observés sur un échantillon à l'ensemble d'une population", 3),
], cat=CAT, src="sup-metho-demarche", fwd="Que désigne {a} ?", rev="Comment appelle-t-on {b} ?")

mcq(C, "met-dem2", [
    ("Quelle est la différence entre un sujet et une problématique ?", "Le sujet délimite un thème, la problématique en fait une question à résoudre", ["Ce sont des synonymes", "La problématique est la conclusion", "Le sujet est la question et la problématique le thème", "La problématique est la liste des sources"], "La problématique transforme un thème en problème de recherche.", 2),
    ("Une bonne problématique est en général :", "formulée sous forme de question précise à laquelle une recherche peut répondre", ["une phrase affirmative sans question", "un thème très vaste sans limites", "une opinion personnelle", "un résumé de la bibliographie"], "Précision et faisabilité sont essentielles.", 2),
    ("Une question de recherche à laquelle on peut répondre simplement par une définition du dictionnaire est :", "trop pauvre pour constituer une problématique", ["idéale", "toujours une hypothèse", "obligatoirement qualitative", "une enquête exploratoire"], "Une problématique suppose un vrai questionnement.", 3),
    ("Qu'est-ce qu'une hypothèse de recherche ?", "Une réponse provisoire à la question de recherche, qui doit être testée", ["Une certitude déjà prouvée", "Un résumé des lectures", "Un titre du mémoire", "Une opinion sans lien avec le sujet"], "Elle sera confirmée ou infirmée par l'enquête.", 1),
    ("Une hypothèse doit être :", "formulée de façon claire et vérifiable", ["impossible à tester", "contradictoire", "imprécise", "dépourvue de variables"], "Sans possibilité de vérification, elle n'est pas scientifique.", 2),
    ("Laquelle de ces formulations est une hypothèse ?", "Plus le niveau d'études des parents est élevé, plus la réussite scolaire des enfants est forte", ["Quelle est l'influence de la famille sur l'école ?", "La réussite scolaire est importante", "Les parents aiment leurs enfants", "Tout le monde veut réussir"], "Elle propose une relation entre deux variables à vérifier.", 3),
    ("Que fait-on quand les résultats de l'enquête ne confirment pas l'hypothèse ?", "On la déclare infirmée et on l'explique honnêtement", ["On modifie les données pour la sauver", "On cache les résultats", "On change la question sans le dire", "On ferme l'enquête"], "Une hypothèse infirmée est aussi un résultat scientifique.", 3),
    ("Peut-on « prouver » définitivement une hypothèse par une seule enquête ?", "Non, on la confirme dans un cadre précis, sans certitude définitive", ["Oui, toujours", "Oui, si l'échantillon est de dix personnes", "Oui, si le chercheur est expert", "Oui, si les gens sont d'accord"], "La science progresse par accumulation et révision.", 4),
    ("Dans la démarche scientifique, l'ordre logique est généralement :", "observation, question, hypothèse, collecte et analyse des données, conclusion", ["conclusion, hypothèse, observation, collecte", "collecte, conclusion, question, hypothèse", "hypothèse, conclusion, observation, collecte", "analyse, observation, conclusion, hypothèse"], "On passe du questionnement à la vérification.", 2),
    ("Le principe de falsifiabilité est associé à :", "Karl Popper", ["Émile Durkheim", "Max Weber", "Auguste Comte", "Karl Marx"], "Pour Popper, une théorie scientifique doit pouvoir être réfutée par des faits.", 5),
    ("Quel ouvrage de Durkheim pose que les faits sociaux doivent être traités « comme des choses » ?", "Les Règles de la méthode sociologique", ["Le Suicide", "De la division du travail social", "L'Éthique protestante et l'esprit du capitalisme", "Le Capital"], "Il y expose une méthode d'étude objective des faits sociaux.", 4),
    ("Qui a défendu en sociologie la « compréhension » du sens que les acteurs donnent à leurs actions ?", "Max Weber", ["Émile Durkheim", "Auguste Comte", "Karl Popper", "Vilfredo Pareto"], "Weber est un représentant de la sociologie compréhensive.", 4),
    ("En sciences sociales, une prénotion est :", "une idée préconçue issue du sens commun, que le chercheur doit soumettre à critique", ["un résultat statistique", "une théorie scientifique validée", "un diplôme", "une méthode d'échantillonnage"], "La rupture avec les prénotions est un principe de la sociologie.", 4),
    ("Un cadre théorique sert à :", "choisir les concepts et théories qui permettent d'analyser le problème", ["rédiger les remerciements", "classer la bibliographie", "choisir les enquêtés au hasard", "calculer la taille de l'échantillon"], "Il donne un sens et une grille à l'analyse.", 3),
    ("Que fait-on au moment de l'introduction d'un mémoire ?", "On présente le contexte, la problématique, les hypothèses, la méthode et l'annonce du plan", ["On présente seulement les remerciements", "On donne uniquement les résultats", "On supprime la problématique", "On rédige la conclusion"], "L'introduction prépare le lecteur à comprendre l'ensemble.", 2),
    ("Que doit contenir une conclusion de travail universitaire ?", "Un bilan des résultats, les limites et des perspectives, sans nouvel argument majeur", ["Une nouvelle partie de développement", "Des remerciements uniquement", "Une longue citation", "Des annexes"], "Elle répond à la problématique posée.", 2),
    ("Pourquoi une conclusion ne doit-elle pas introduire de nouveaux développements majeurs ?", "Parce qu'elle doit répondre à la problématique à partir de ce qui a été montré", ["Parce qu'elle est facultative", "Parce qu'il faut allonger le texte", "Parce qu'elle remplace l'introduction", "Parce que la bibliographie suffit"], "Elle clôt la démonstration.", 3),
    ("Qu'est-ce qu'un plan en deux ou trois parties ?", "Une organisation logique du développement qui répond à la problématique", ["Une liste de lectures", "Un résumé de la conclusion seulement", "Un titre", "Un tableau de chiffres"], "Chaque partie fait progresser le raisonnement.", 2),
    ("Les titres des parties d'un plan doivent :", "être clairs, cohérents et refléter l'argumentation", ["être de simples numéros", "être vagues", "être contradictoires", "être choisis au hasard"], "Le plan est l'ossature du raisonnement.", 2),
    ("Une transition entre deux parties sert à :", "relier logiquement les parties et annoncer la suite", ["supprimer une partie", "résumer la conclusion", "citer des auteurs", "compter les pages"], "Elle assure la fluidité.", 2),
    ("Un plan « thématique » organise l'exposé :", "autour de grands thèmes ou aspects du sujet", ["uniquement par ordre chronologique", "uniquement par ordre alphabétique des auteurs", "uniquement par la longueur des paragraphes", "au hasard"], "Il s'oppose au plan chronologique.", 3),
    ("Un plan « chronologique » organise l'exposé :", "selon l'ordre du temps", ["selon les thèmes", "selon la longueur", "selon l'alphabet", "selon la couleur"], "Il convient aux sujets historiques.", 2),
    ("Les objectifs spécifiques d'une recherche doivent être :", "précis, réalistes et réalisables avec les moyens et le temps disponibles", ["flous et illimités", "nombreux et impossibles à atteindre", "identiques à la conclusion", "choisis sans lien avec le sujet"], "Ils concrétisent l'objectif général.", 3),
    ("Parmi ces verbes, lequel convient le mieux pour formuler un objectif spécifique mesurable ?", "Identifier les facteurs qui expliquent l'abandon scolaire dans un quartier donné", ["Comprendre le monde", "Réfléchir sur la société", "Parler de l'école en général", "Aimer la sociologie"], "Un objectif spécifique est précis et observable.", 4),
    ("Le sigle SMART appliqué à des objectifs renvoie à des objectifs :", "spécifiques, mesurables, atteignables, réalistes et définis dans le temps", ["simples, multiples, anciens, rares et traditionnels", "sociaux, mondiaux, annuels, rapides et techniques", "scolaires, moraux, abstraits, rigides et théoriques", "sérieux, multiples, aléatoires, risqués et temporaires"], "Critères classiques de formulation d'objectifs.", 4),
    ("À quoi sert la table des matières d'un mémoire ?", "À présenter la structure du travail avec la pagination", ["À présenter les sources", "À remercier les enseignants", "À résumer l'enquête", "À citer les auteurs"], "Elle aide le lecteur à se repérer.", 1),
    ("Les annexes d'un mémoire servent à :", "regrouper des documents complémentaires (questionnaire, guide d'entretien, tableaux détaillés)", ["remplacer la conclusion", "remplacer le développement", "présenter uniquement les remerciements", "cacher les sources"], "Elles allègent le corps du texte.", 2),
    ("Que présente la page de garde d'un mémoire ?", "Le titre, l'auteur, l'établissement, l'année et le cadre du travail", ["Le plan détaillé", "La bibliographie", "Les résultats chiffrés", "Les annexes"], "Les informations d'identification du travail s'y trouvent.", 1),
    ("Le résumé d'un mémoire présente :", "l'objet, la méthode et les principaux résultats en quelques lignes", ["la liste des mots difficiles", "l'ensemble de la bibliographie", "les remerciements", "le questionnaire en entier"], "C'est une synthèse du travail.", 2),
    ("Le glossaire d'un travail est :", "une liste de termes spécialisés avec leur définition", ["la liste des auteurs", "la liste des tableaux", "une conclusion", "un résumé de l'introduction"], "Il aide à comprendre le vocabulaire.", 3),
    ("La liste des abréviations sert à :", "expliquer les sigles utilisés dans le travail", ["raccourcir la bibliographie", "remplacer la table des matières", "présenter l'équipe de recherche", "résumer la problématique"], "Elle évite les ambiguïtés de lecture.", 2),
    ("Un rapport de stage a pour objectif principal de :", "rendre compte d'une expérience professionnelle de façon structurée et analytique", ["raconter ses vacances", "copier le règlement de l'entreprise", "remplacer le mémoire de recherche", "présenter uniquement des photos"], "Il décrit et analyse l'expérience vécue.", 2),
    ("Quelle différence entre un mémoire de recherche et un rapport de stage ?", "Le mémoire répond à une problématique scientifique, le rapport rend compte d'une expérience en entreprise", ["Aucune", "Le rapport est toujours plus long", "Le mémoire n'a pas de bibliographie", "Le rapport doit avoir un questionnaire"], "Deux genres aux finalités différentes.", 3),
    ("Que doit contenir un bon exposé oral ?", "Une introduction annonçant le plan, un développement structuré et une conclusion", ["Un texte lu mot à mot sans regarder l'auditoire", "Une longue suite de dates sans lien", "Uniquement des diapositives", "Une improvisation sans plan"], "Structure et clarté sont essentielles.", 2),
    ("Dans un exposé avec diapositives, les diapositives doivent :", "soutenir le propos avec peu de texte et des éléments lisibles", ["reprendre tout le texte de l'exposé", "être surchargées de paragraphes", "être illisibles", "rester vides"], "Le support accompagne l'orateur et ne le remplace pas.", 2),
    ("Pour une bonne prise de parole en public, il est conseillé de :", "regarder l'auditoire et parler à un rythme compréhensible", ["lire sans lever les yeux", "parler très vite", "parler tourné vers le mur", "ne jamais préparer"], "L'auditoire doit pouvoir suivre.", 1),
], cat=CAT, src="sup-metho-demarche")

# ------------------------------------------------------------------ variables et statistiques descriptives
classify(C, "met-var", {
    "une variable qualitative nominale": ["la profession", "la religion", "la ville de résidence", "le sexe", "la langue maternelle", "la filière d'études"],
    "une variable qualitative ordinale": ["le niveau de satisfaction (faible, moyen, élevé)", "le niveau d'études atteint", "la mention obtenue à un examen", "le rang à un concours", "le degré d'accord (pas du tout, plutôt, tout à fait)"],
    "une variable quantitative discrète": ["le nombre d'enfants d'un ménage", "le nombre de frères et sœurs", "le nombre de livres lus dans l'année", "le nombre de pièces d'un logement", "le nombre d'absences d'un étudiant"],
    "une variable quantitative continue": ["la taille d'une personne", "le poids d'un nouveau-né", "la durée d'un trajet", "la température d'une salle", "la distance entre le domicile et l'université"],
}, cat=CAT, src="sup-metho-demarche",
    fwd="De quel type de variable relève {item} ?", rev="Lequel de ces exemples est {group} ?", expl="{item} est {group}.",
    diffs={"le rang à un concours": 4, "le niveau d'études atteint": 3, "la mention obtenue à un examen": 4, "le nombre de pièces d'un logement": 4, "la langue maternelle": 3, "la filière d'études": 3})

table(C, "met-vardef", [
    ("une variable", "une caractéristique qui peut prendre plusieurs valeurs ou modalités selon les individus", 1),
    ("la variable indépendante", "la variable qui est supposée influencer ou expliquer une autre", 3),
    ("la variable dépendante", "la variable dont on cherche à expliquer les variations", 3),
    ("une modalité", "chacune des valeurs ou catégories que peut prendre une variable", 2),
    ("une variable de contrôle", "une variable que l'on maintient constante ou que l'on surveille pour isoler l'effet étudié", 4),
    ("une variable parasite", "un facteur extérieur non voulu qui peut brouiller la relation étudiée", 4),
    ("une unité d'analyse", "l'élément sur lequel portent les observations (individu, ménage, école…)", 3),
    ("la population mère", "l'ensemble des éléments sur lesquels porte l'étude et que l'on veut décrire", 2),
], cat=CAT, src="sup-metho-demarche", fwd="Que désigne {a} ?", rev="Comment appelle-t-on {b} ?")

table(C, "met-stat", [
    ("la moyenne arithmétique", "la somme des valeurs divisée par leur nombre", 1),
    ("la médiane", "la valeur qui partage une série ordonnée en deux moitiés d'effectifs égaux", 2),
    ("le mode", "la valeur ou la modalité la plus fréquente", 2),
    ("l'étendue", "la différence entre la plus grande et la plus petite valeur", 2),
    ("l'effectif", "le nombre d'individus présentant une modalité donnée", 1),
    ("la fréquence relative", "la part d'une modalité par rapport à l'effectif total", 2),
    ("l'écart type", "un indicateur de la dispersion des valeurs autour de la moyenne", 4),
    ("un tableau croisé", "un tableau qui présente la répartition simultanée de deux variables", 3),
], cat=CAT, src="sup-metho-enquete", fwd="Que désigne {a} ?", rev="Comment appelle-t-on {b} ?")

mcq(C, "met-var2", [
    ("Dans l'hypothèse « plus le niveau d'études est élevé, plus le revenu est élevé », quelle est la variable indépendante ?", "Le niveau d'études", ["Le revenu", "La ville", "Le sexe de l'enquêteur", "La date de l'enquête"], "Le niveau d'études est supposé influencer le revenu.", 3),
    ("Dans l'hypothèse « plus le niveau d'études est élevé, plus le revenu est élevé », quelle est la variable dépendante ?", "Le revenu", ["Le niveau d'études", "La taille de l'échantillon", "La profession de l'enquêteur", "Le mois de l'enquête"], "C'est la variable dont on explique les variations.", 3),
    ("Une moyenne peut être trompeuse lorsque :", "quelques valeurs extrêmes tirent fortement la moyenne", ["toutes les valeurs sont égales", "la série est très courte et sans valeurs extrêmes", "la série est triée", "la médiane est connue"], "Dans ce cas, la médiane est parfois plus représentative.", 4),
    ("Calculer la moyenne des notes 8, 10, 12 et 14 donne :", "11", ["10", "12", "44", "4"], "(8 + 10 + 12 + 14) / 4 = 11.", 2),
    ("Quelle est la médiane de la série 3, 5, 7, 9, 11 ?", "7", ["5", "9", "35", "3"], "La valeur centrale d'une série ordonnée de cinq valeurs est la troisième.", 2),
    ("Quel est le mode de la série 2, 3, 3, 4, 5, 3, 6 ?", "3", ["4", "2", "6", "5"], "La valeur 3 apparaît trois fois.", 2),
    ("L'étendue de la série 4, 9, 15, 20 vaut :", "16", ["20", "4", "48", "9"], "20 - 4 = 16.", 2),
    ("Dans un échantillon de 200 personnes dont 50 déclarent pratiquer un sport, la fréquence relative de cette modalité est :", "25 %", ["50 %", "4 %", "75 %", "10 %"], "50 / 200 = 0,25.", 3),
    ("Un groupe de 40 enquêtés compte 10 étudiants ; quelle est la part (fréquence relative) des étudiants ?", "25 %", ["10 %", "40 %", "4 %", "50 %"], "10 / 40 = 25 %.", 2),
    ("Dans un tableau, les pourcentages en colonne servent à :", "comparer la structure d'une variable au sein de chaque groupe", ["calculer la moyenne de toutes les colonnes", "supprimer les effectifs", "classer les enquêtés", "interpréter les différences sans calcul"], "On lit les proportions à l'intérieur de chaque colonne.", 5),
    ("Un diagramme en barres convient surtout pour représenter :", "une variable qualitative ou discrète", ["une série chronologique de dates seulement", "une carte", "une hypothèse", "une bibliographie"], "Chaque barre représente une modalité.", 3),
    ("Un histogramme est adapté à :", "une variable quantitative continue regroupée en classes", ["une variable nominale", "un texte", "un entretien", "une photo"], "Les barres sont jointives, car les classes se suivent.", 4),
    ("Un diagramme circulaire (secteurs) sert à :", "montrer la répartition d'un tout entre quelques modalités", ["suivre une évolution dans le temps", "comparer des moyennes sur dix ans", "classer par ordre alphabétique", "décrire un entretien"], "Chaque secteur représente une part du total.", 2),
    ("Une courbe est la mieux adaptée pour montrer :", "l'évolution d'un phénomène dans le temps", ["la répartition d'un tout", "une liste d'auteurs", "les modalités d'une variable nominale", "le plan du mémoire"], "L'axe horizontal représente le temps.", 2),
    ("Un échantillon de taille nettement plus grande réduit, toutes choses égales par ailleurs :", "l'erreur due au hasard de l'échantillonnage", ["tous les biais de conception", "le besoin d'un questionnaire", "la nécessité d'une base de sondage", "la fiabilité"], "Un grand échantillon mal conçu reste biaisé.", 4),
    ("Un tri à plat présente :", "la répartition des réponses pour une seule variable", ["la comparaison de deux variables", "des hypothèses", "la bibliographie", "le plan de l'enquête"], "Il donne l'effectif et le pourcentage de chaque modalité.", 3),
    ("Un tri croisé permet de :", "étudier le lien entre deux variables", ["calculer uniquement une moyenne", "recueillir des témoignages", "remplacer l'enquête", "éviter l'échantillonnage"], "On croise les modalités de deux variables.", 3),
    ("Peut-on conclure à une causalité à partir d'une simple corrélation ?", "Non, un lien statistique ne prouve pas à lui seul une relation de cause à effet", ["Oui, toujours", "Oui, si les deux variables varient dans le même sens", "Oui, si le sondage est téléphonique", "Oui, si la moyenne est élevée"], "Un troisième facteur peut expliquer les deux phénomènes.", 4),
    ("Exemple classique de corrélation sans causalité directe : les ventes de glaces et les noyades augmentent ensemble. Pourquoi ?", "Un troisième facteur, la chaleur de l'été, influence les deux", ["Les glaces causent les noyades", "Les noyades causent les ventes de glaces", "Les deux sont des erreurs de mesure", "Les glaces améliorent la natation"], "C'est un exemple de variable de confusion.", 5),
    ("Un pourcentage calculé sur un très petit effectif :", "peut être trompeur et doit être interprété avec prudence", ["est toujours exact", "est plus précis qu'un grand échantillon", "ne peut pas être calculé", "n'a aucun sens mathématique"], "Un effectif réduit rend le pourcentage instable.", 4),
    ("La population cible d'une enquête sur les étudiants d'une université est :", "l'ensemble des étudiants de cette université", ["les enseignants de cette université", "les étudiants d'un autre pays", "les parents des étudiants", "les habitants de la capitale"], "Elle correspond à l'ensemble que l'on veut décrire.", 2),
], cat=CAT, src="sup-metho-enquete")
