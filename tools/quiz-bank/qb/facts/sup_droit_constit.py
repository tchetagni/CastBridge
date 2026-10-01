"""Droit constitutionnel et institutions politiques (L1 droit) : théorie de l'État, Constitution, régimes, suffrage,
contrôle de constitutionnalité, libertés publiques, états d'exception, décentralisation.
Faits écrits par l'assistant : tous `review`. Aucune donnée camerounaise précise (articles, chiffres, dates de réforme) : seulement l'organisation générale."""
from .sup_kit import *  # noqa: F401,F403

C = "l1-droit"
source("sup-constit", "Droit constitutionnel et institutions politiques (théorie générale)", "cours de L1 droit",
       "Comparer avec un manuel général de droit constitutionnel et d'institutions politiques (théorie de l'État, régimes politiques, libertés publiques), "
       "avec la DUDH, les pactes de 1966, la Charte africaine, et, pour le Cameroun, avec la Constitution du 18 janvier 1996 telle que modifiée.")
S = "sup-constit"


def mcqr(course, tpl, rows, **kw):
    """Comme mcq, mais les questions qui portent sur le Cameroun sont marquées région CM."""
    cm = [r for r in rows if "Cameroun" in r[0] or "camerounais" in r[0].lower() or "Yaoundé" in r[0] or "Cameroun" in r[1]]
    ot = [r for r in rows if r not in cm]
    if ot:
        mcq(course, tpl, ot, **kw)
    if cm:
        mcq(course, tpl + "-cm", cm, region="CM", **kw)

# ----------------------------------------------------------------------------------------------------------------------------
# 1. Théorie de l'État : notions
table(C, "dc-etat", [
    ("la souveraineté", "le pouvoir suprême de l'État, sans supérieur à l'intérieur ni dépendance à l'extérieur", 2),
    ("le territoire de l'État", "l'espace délimité sur lequel l'État exerce de façon exclusive ses compétences", 2),
    ("la population d'un État", "l'ensemble des personnes, nationaux et étrangers, établies sur son territoire", 2),
    ("la nationalité", "le lien juridique qui rattache une personne à un État déterminé", 2),
    ("la nation au sens sociologique", "une communauté humaine unie par le sentiment de partager une histoire et un destin communs", 3),
    ("la personnalité morale de l'État", "l'aptitude de l'État, distinct des gouvernants, à être titulaire de droits et d'obligations", 3),
    ("l'État de droit", "un État dont les gouvernants sont eux-mêmes soumis au droit et au contrôle d'un juge", 2),
    ("le pouvoir politique", "la capacité de commander au nom de la collectivité et de se faire obéir", 2),
    ("la légitimité d'un pouvoir", "la qualité d'un pouvoir accepté comme juste par ceux à qui il commande", 3),
    ("la légalité d'un acte", "la conformité de cet acte aux règles de droit en vigueur", 2),
    ("la continuité de l'État", "le principe selon lequel l'État subsiste malgré le changement de gouvernants ou de régime", 4),
    ("un État-nation", "un État dont la population se reconnaît majoritairement comme formant une même nation", 3),
    ("la reconnaissance d'un État", "l'acte par lequel un État admet qu'une entité nouvelle possède la qualité d'État", 4),
    ("le droit des peuples à disposer d'eux-mêmes", "le droit d'un peuple de décider librement de son statut politique", 3),
    ("la puissance publique", "l'ensemble des prérogatives exorbitantes du droit commun dont disposent les autorités de l'État", 4),
    ("un gouvernement de fait", "un pouvoir installé hors des procédures constitutionnelles, qui exerce effectivement l'autorité", 4),
], cat="Théorie de l'État", src=S, fwd="Comment définir {a} ?", rev="Quelle notion correspond à la définition suivante : {b} ?")

# 2. Formes de l'État, décentralisation
table(C, "dc-forme", [
    ("l'État unitaire", "un État à un seul centre de décision politique, une seule Constitution et un seul ordre juridique", 2),
    ("l'État fédéral", "un État composé d'entités fédérées autonomes, qui participent à la décision fédérale", 3),
    ("la confédération d'États", "une association d'États qui restent souverains et sont liés par un traité", 3),
    ("la décentralisation", "le transfert de compétences à des collectivités dotées de la personnalité morale et d'organes élus", 2),
    ("la déconcentration", "la délégation de pouvoirs de décision à des agents de l'État placés dans les circonscriptions", 2),
    ("la tutelle administrative", "le contrôle de l'État sur les collectivités décentralisées pour veiller à la légalité de leurs actes", 3),
    ("le pouvoir hiérarchique", "le pouvoir d'un supérieur de donner des ordres à ses subordonnés et de réformer leurs décisions", 3),
    ("une collectivité territoriale", "une personne morale de droit public distincte de l'État, administrée par des organes élus", 3),
    ("la libre administration des collectivités", "le principe selon lequel les collectivités gèrent leurs propres affaires par des conseils élus", 3),
    ("le principe de subsidiarité", "la règle qui confie une compétence à l'échelon le plus proche du citoyen capable de l'exercer", 4),
    ("la sécession", "la séparation d'une partie du territoire d'un État pour former un nouvel État", 3),
], cat="Formes de l'État et décentralisation", src=S, fwd="Comment définir {a} ?", rev="Quelle notion correspond à la définition suivante : {b} ?")

# 3. Constitution
table(C, "dc-const", [
    ("une Constitution rigide", "un texte dont la révision exige une procédure plus exigeante que celle des lois ordinaires", 2),
    ("une Constitution souple", "un texte modifiable selon la procédure applicable aux lois ordinaires", 2),
    ("une Constitution coutumière", "un ensemble de règles non codifiées, nées de la pratique et de textes épars", 3),
    ("une Constitution octroyée", "un texte accordé unilatéralement par le monarque à ses sujets", 3),
    ("une Constitution pactée", "un texte issu d'un compromis entre le monarque et les représentants de la nation", 4),
    ("une Constitution démocratique", "un texte établi par le peuple ou ses représentants élus, par constituante ou référendum", 3),
    ("le pouvoir constituant originaire", "le pouvoir d'établir une Constitution nouvelle sans être lié par les règles antérieures", 3),
    ("le pouvoir constituant dérivé", "le pouvoir de réviser la Constitution selon les règles que celle-ci prévoit elle-même", 3),
    ("une assemblée constituante", "une assemblée élue pour rédiger ou adopter une Constitution", 2),
    ("une clause d'éternité", "une disposition interdisant de réviser certains éléments, comme la forme républicaine de l'État", 4),
    ("le préambule d'une Constitution", "le texte placé en tête, qui énonce généralement des principes et des droits fondamentaux", 2),
    ("une loi organique", "une loi qui précise l'organisation des pouvoirs publics prévus par la Constitution", 3),
    ("le constitutionnalisme", "la doctrine selon laquelle le pouvoir doit être limité par une Constitution garantissant les droits", 4),
    ("la Constitution au sens matériel", "les règles relatives à l'organisation et à l'exercice du pouvoir, quelle que soit leur forme", 4),
    ("la Constitution au sens formel", "l'ensemble des règles contenues dans le document intitulé Constitution, quel que soit leur objet", 4),
], cat="La Constitution", src=S, fwd="Que désigne {a} ?", rev="Quelle notion correspond à la définition suivante : {b} ?")

# 4. Séparation et collaboration des pouvoirs
table(C, "dc-pouv", [
    ("la séparation rigide des pouvoirs", "une organisation où chaque pouvoir est indépendant des autres, sans moyens d'action réciproques", 3),
    ("la collaboration des pouvoirs", "une organisation où exécutif et législatif coopèrent et ont des moyens d'action réciproques", 3),
    ("la responsabilité politique du gouvernement", "l'obligation pour le gouvernement de démissionner s'il perd la confiance de la majorité", 3),
    ("la motion de censure", "le vote par lequel le parlement met en cause la responsabilité du gouvernement et peut le renverser", 3),
    ("la question de confiance", "la demande faite par le gouvernement au parlement de lui renouveler son soutien sur un texte", 3),
    ("la dissolution", "la décision de mettre fin avant terme au mandat de l'assemblée pour de nouvelles élections", 2),
    ("le contreseing", "la signature d'un ministre jointe à celle du chef de l'État pour endosser l'acte", 4),
    ("l'impeachment américain", "la destitution de hauts responsables américains : accusation par la Chambre, jugement par le Sénat", 4),
    ("la délégation législative", "l'autorisation donnée au gouvernement de prendre par ordonnances des mesures relevant de la loi", 3),
    ("le pouvoir réglementaire", "la compétence de l'exécutif pour édicter des règles générales hors du domaine réservé à la loi", 3),
    ("une commission d'enquête parlementaire", "un organe temporaire du parlement chargé de recueillir des informations sur certains faits", 3),
    ("la navette parlementaire", "le va-et-vient d'un texte entre les deux chambres jusqu'à son adoption dans les mêmes termes", 3),
    ("le veto du chef de l'exécutif", "le droit de s'opposer à l'entrée en vigueur d'une loi votée par le parlement", 3),
    ("le bicamérisme", "l'organisation du parlement en deux chambres", 1),
    ("le monocamérisme", "l'organisation du parlement en une seule chambre", 1),
], cat="Séparation et collaboration des pouvoirs", src=S, fwd="Comment définir {a} ?", rev="Quelle notion correspond à la définition suivante : {b} ?")

# 5. Régimes politiques
table(C, "dc-regime", [
    ("le régime présidentiel", "un régime où un chef d'État élu à part dirige l'exécutif sans être responsable devant le parlement", 2),
    ("le régime parlementaire", "un régime où le gouvernement répond devant le parlement, lui-même dissoluble par l'exécutif", 2),
    ("le régime d'assemblée", "un régime où l'exécutif est subordonné à l'assemblée, qui concentre l'essentiel du pouvoir", 3),
    ("la monarchie constitutionnelle", "un régime où le monarque est chef de l'État mais où ses pouvoirs sont encadrés par la Constitution", 2),
    ("la monarchie absolue", "un régime où le roi concentre tous les pouvoirs sans limite constitutionnelle", 2),
    ("la république", "un régime où la fonction de chef de l'État n'est pas héréditaire mais élective", 2),
    ("la démocratie représentative", "un système où le peuple gouverne par l'intermédiaire de représentants élus", 2),
    ("la démocratie directe", "un système où les citoyens décident eux-mêmes des lois ou des grandes questions, sans intermédiaire", 2),
], cat="Régimes politiques", src=S, fwd="Comment caractériser {a} ?", rev="Quel régime ou système politique est décrit ainsi : {b} ?")

# 6. Suffrage
table(C, "dc-suff", [
    ("le suffrage universel", "un droit de vote reconnu à tous les citoyens qui remplissent les conditions générales de capacité", 2),
    ("le suffrage censitaire", "un droit de vote réservé aux citoyens qui paient un certain niveau d'impôt", 3),
    ("le suffrage capacitaire", "un droit de vote réservé aux personnes justifiant d'un certain niveau d'instruction", 3),
    ("le suffrage direct", "un vote par lequel les électeurs désignent eux-mêmes les élus", 2),
    ("le suffrage indirect", "un vote où des électeurs désignent des grands électeurs, qui choisissent à leur tour les élus", 3),
    ("le suffrage égal", "le principe selon lequel chaque électeur dispose d'une voix et toutes les voix pèsent autant", 2),
    ("le vote plural", "la pratique donnant à certains électeurs plusieurs voix", 4),
    ("le vote secret", "un vote émis de manière que le choix de chaque électeur ne soit pas connu", 2),
    ("le vote obligatoire", "l'obligation légale de se rendre aux urnes, sanctionnée dans certains pays", 2),
    ("le scrutin uninominal", "un vote où l'électeur choisit un seul candidat dans sa circonscription", 2),
    ("le scrutin de liste", "un vote où l'électeur choisit une liste de plusieurs candidats", 2),
    ("la circonscription électorale", "le cadre géographique dans lequel les voix sont comptées pour attribuer les sièges", 2),
    ("le gerrymandering", "le découpage des circonscriptions à des fins partisanes pour avantager un camp", 4),
    ("le corps électoral", "l'ensemble des citoyens qui ont le droit de voter", 1),
    ("l'abstention", "le fait pour un électeur inscrit de ne pas participer au vote", 1),
    ("le suffrage-fonction", "une conception selon laquelle voter est une fonction que la nation confie à certains citoyens", 5),
    ("le suffrage-droit", "une conception selon laquelle voter est un droit individuel appartenant à chaque citoyen", 5),
], cat="Suffrage et élections", src=S, fwd="Que signifie {a} ?", rev="Quelle notion électorale correspond à la définition suivante : {b} ?")

# 7. Modes de scrutin
table(C, "dc-scrut", [
    ("le scrutin majoritaire uninominal à un tour", "un scrutin où le candidat arrivé en tête l'emporte, même sans la majorité absolue", 2),
    ("le scrutin majoritaire à deux tours", "un scrutin où, faute de majorité au premier tour, les candidats restants s'affrontent au second", 2),
    ("le scrutin proportionnel de liste", "un scrutin où les sièges sont répartis entre les listes en proportion de leurs voix", 2),
    ("le scrutin mixte", "un scrutin combinant des sièges attribués à la majoritaire et d'autres à la proportionnelle", 3),
    ("la méthode du quotient", "une répartition fondée sur la division du nombre de suffrages par le nombre de sièges à pourvoir", 3),
    ("la méthode de la plus forte moyenne", "une répartition donnant chaque siège à la liste ayant la moyenne de voix par siège la plus élevée", 4),
    ("le vote préférentiel", "un mécanisme permettant à l'électeur de choisir ou de classer des candidats au sein d'une liste", 4),
    ("le panachage", "un mécanisme permettant à l'électeur de composer sa liste avec des candidats de listes différentes", 4),
    ("la liste bloquée", "une liste dont l'ordre de présentation ne peut pas être modifié par l'électeur", 3),
    ("le seuil électoral", "un pourcentage minimal de suffrages exigé d'une liste pour participer à la répartition des sièges", 3),
    ("la prime majoritaire", "un supplément de sièges accordé à la liste ou à la coalition arrivée en tête", 4),
    ("la majorité absolue", "plus de la moitié des suffrages pris en compte dans le calcul", 3),
    ("la majorité relative", "le plus grand nombre de voix, sans exiger plus de la moitié des suffrages", 3),
], cat="Modes de scrutin", src=S, fwd="Comment définir {a} ?", rev="Quel mécanisme électoral est décrit ainsi : {b} ?")

# 8. Partis, référendum, représentation
table(C, "dc-parti", [
    ("un parti politique", "une organisation durable qui vise à exercer le pouvoir en présentant des candidats aux élections", 2),
    ("un parti de cadres", "un parti formé de notables, peu structuré en dehors des périodes électorales", 4),
    ("un parti de masse", "un parti à large base d'adhérents cotisants et fortement organisé", 4),
    ("le bipartisme", "un système dominé par deux grands partis qui alternent au pouvoir", 2),
    ("le multipartisme", "un système où plusieurs partis, sans hégémonie de l'un d'eux, se disputent le pouvoir", 2),
    ("le parti unique", "un système où un seul parti est autorisé ou domine sans concurrence réelle", 2),
    ("une coalition gouvernementale", "une alliance de partis qui gouvernent ensemble faute de majorité d'un seul", 3),
    ("la cohabitation", "la situation où le président et la majorité parlementaire relèvent de camps politiques opposés", 3),
    ("le référendum", "la consultation directe des citoyens, qui votent pour ou contre un texte ou une question", 1),
    ("le plébiscite", "un vote par lequel les électeurs se prononcent surtout sur la personne qui détient le pouvoir", 4),
    ("l'initiative populaire", "la faculté pour un certain nombre de citoyens de proposer un texte soumis au vote", 3),
    ("le veto populaire", "la possibilité pour les citoyens de demander un vote sur une loi déjà adoptée par le parlement", 5),
    ("la révocation populaire", "la procédure permettant aux électeurs de mettre fin avant terme au mandat d'un élu", 4),
    ("le mandat représentatif", "le principe selon lequel l'élu représente toute la nation et n'est pas lié par ses électeurs", 3),
    ("le mandat impératif", "le système où l'élu est tenu d'exécuter les instructions de ses électeurs et peut être révoqué", 3),
], cat="Partis, référendum et représentation", src=S, fwd="Comment définir {a} ?", rev="Quelle notion correspond à la définition suivante : {b} ?")

# 9. Contrôle de constitutionnalité
table(C, "dc-ctrl", [
    ("le contrôle a priori", "un contrôle exercé sur un texte voté avant sa promulgation", 3),
    ("le contrôle a posteriori", "un contrôle exercé sur une loi déjà promulguée et en vigueur", 3),
    ("le contrôle diffus", "un système où tout juge peut écarter une loi inconstitutionnelle dans le litige dont il est saisi", 3),
    ("le contrôle concentré", "un système où une seule juridiction spécialisée apprécie la constitutionnalité des lois", 3),
    ("la voie d'action", "la saisine directe du juge constitutionnel contre une loi, indépendamment de tout procès", 4),
    ("la voie d'exception", "le moyen soulevé par une partie à un procès pour écarter l'application d'une loi inconstitutionnelle", 4),
    ("l'effet erga omnes", "l'autorité d'une décision qui s'impose à tous et non aux seules parties", 4),
    ("l'effet inter partes", "l'autorité d'une décision limitée aux parties au litige", 4),
    ("le contrôle de conventionnalité", "la vérification de la conformité d'une loi à un traité international", 4),
    ("la réserve d'interprétation", "une technique validant une loi sous réserve qu'elle soit comprise dans un sens précis", 5),
    ("le bloc de constitutionnalité", "l'ensemble des normes de référence du contrôle, qui dépasse parfois le seul texte constitutionnel", 5),
    ("la hiérarchie des normes", "l'ordre dans lequel les normes se classent, chacune devant respecter celles qui lui sont supérieures", 2),
], cat="Contrôle de constitutionnalité", src=S, fwd="Comment définir {a} ?", rev="Quelle notion correspond à la définition suivante : {b} ?")

# 10. Libertés publiques et droits fondamentaux
table(C, "dc-lib", [
    ("la liberté d'aller et venir", "la liberté de se déplacer sur le territoire et de le quitter", 2),
    ("la liberté d'association", "le droit de se grouper de façon durable pour poursuivre un but commun", 2),
    ("la liberté de réunion", "le droit de se rassembler de façon temporaire pour échanger ou exprimer des opinions", 3),
    ("la liberté de manifester", "le droit d'exprimer collectivement ses opinions sur la voie publique", 2),
    ("la liberté de conscience", "le droit de choisir ses croyances ou de n'en avoir aucune", 2),
    ("la liberté de la presse", "le droit de publier des informations et des opinions sans censure préalable", 2),
    ("le droit à la sûreté", "la protection contre les arrestations et les détentions arbitraires", 3),
    ("la présomption d'innocence", "le principe selon lequel nul n'est tenu pour coupable tant que sa culpabilité n'est pas établie", 1),
    ("le droit à un procès équitable", "le droit d'être jugé par un tribunal indépendant et impartial dans un délai raisonnable", 3),
    ("le principe de légalité des délits et des peines", "la règle selon laquelle nul ne peut être puni pour un acte que la loi n'interdisait pas à l'époque", 3),
    ("le droit au respect de la vie privée", "la protection de l'intimité personnelle et familiale, du domicile et de la correspondance", 2),
    ("le droit d'asile", "la protection accordée par un État à une personne persécutée dans son pays d'origine", 3),
    ("le droit de grève", "le droit pour des travailleurs de cesser ensemble le travail pour appuyer leurs revendications", 2),
], cat="Libertés publiques et droits fondamentaux", src=S, fwd="Comment définir {a} ?", rev="Quel droit ou quelle liberté correspond à la définition suivante : {b} ?")

# 11. Textes internationaux
table(C, "dc-txt", [
    ("la Déclaration universelle des droits de l'homme", "un texte adopté en 1948 par l'Assemblée générale de l'ONU, qui énonce des droits fondamentaux", 2),
    ("le Pacte international relatif aux droits civils et politiques", "un traité de 1966 qui consacre notamment les libertés civiles et politiques", 4),
    ("le Pacte international relatif aux droits économiques, sociaux et culturels", "un traité de 1966 qui consacre notamment les droits au travail, à l'éducation et à la santé", 4),
    ("la Charte africaine des droits de l'homme et des peuples", "un traité africain de 1981 qui proclame aussi des droits des peuples et des devoirs des individus", 3),
    ("la Convention européenne des droits de l'homme", "un traité de 1950 du Conseil de l'Europe, contrôlé par une cour siégeant à Strasbourg", 3),
    ("la Déclaration des droits de l'homme et du citoyen", "un texte français de 1789 qui proclame des droits naturels et imprescriptibles", 2),
    ("la Convention des Nations unies relative aux droits de l'enfant", "un traité onusien de 1989 consacré à la protection des enfants", 3),
    ("la Convention des Nations unies contre la torture", "un traité onusien visant à prévenir et à réprimer la torture et les peines ou traitements cruels", 3),
    ("la Convention sur l'élimination des discriminations à l'égard des femmes", "un traité onusien de 1979 consacré à l'égalité entre les sexes", 4),
    ("le Statut de Rome", "le traité de 1998 qui a créé la Cour pénale internationale", 3),
    ("la Convention de Genève sur les réfugiés", "le traité de 1951 qui définit le statut de réfugié et les droits qui s'y attachent", 4),
    ("le Protocole de Maputo", "un protocole à la Charte africaine consacré aux droits des femmes en Afrique", 5),
    ("la Charte africaine des droits et du bien-être de l'enfant", "un traité africain consacré aux droits et au bien-être de l'enfant", 4),
], cat="Textes internationaux de droits de l'homme", src=S, fwd="Comment présenter {a} ?", rev="De quel texte s'agit-il : {b} ?")

# ----------------------------------------------------------------------------------------------------------------------------
# Classements
classify(C, "dc-cl-pouv", {
    "pouvoir exécutif": ["assurer l'exécution des lois", "diriger l'administration de l'État", "prendre des règlements d'application", "organiser l'action des services publics"],
    "pouvoir législatif": ["voter la loi", "autoriser le budget de l'État", "constituer une commission d'enquête", "voter une motion de censure"],
    "pouvoir judiciaire": ["trancher un litige entre deux particuliers", "prononcer une peine contre une personne condamnée", "statuer sur la légalité d'une détention", "interpréter la loi pour résoudre un cas concret"],
    "pouvoir constituant": ["rédiger une nouvelle Constitution", "réviser la Constitution selon la procédure qu'elle prévoit", "adopter un texte fondamental par référendum constituant", "élaborer le statut fondamental d'un nouvel État"],
}, cat="Séparation et collaboration des pouvoirs", src=S, diff=2,
    fwd="À quel pouvoir se rattache la fonction suivante : {item} ?", rev="Laquelle de ces fonctions relève du {group} ?",
    expl="Cette fonction relève classiquement du {group}.")

classify(C, "dc-cl-lib", {
    "libertés individuelles": ["la liberté d'aller et venir", "le droit à la sûreté", "le droit au respect de la vie privée", "la liberté de conscience", "l'inviolabilité du domicile"],
    "libertés collectives": ["la liberté d'association", "la liberté de réunion", "la liberté syndicale", "la liberté de manifester"],
    "droits économiques et sociaux (droits-créances)": ["le droit à l'éducation", "le droit à la protection de la santé", "le droit au travail", "le droit à la sécurité sociale", "le droit à un niveau de vie suffisant"],
    "droits de solidarité (troisième génération)": ["le droit à un environnement sain", "le droit à la paix", "le droit au développement", "le droit à la protection du patrimoine commun de l'humanité"],
}, cat="Libertés publiques et droits fondamentaux", src=S, diff=3,
    fwd="Dans quelle catégorie classe-t-on classiquement : {item} ?", rev="Lequel de ces droits relève de la catégorie « {group} » ?",
    expl="Dans la classification courante, cet élément relève de la catégorie : {group}.")

classify(C, "dc-cl-aut", {
    "Montesquieu": ["De l'esprit des lois", "l'idée que le pouvoir doit arrêter le pouvoir"],
    "Jean-Jacques Rousseau": ["Du contrat social", "la volonté générale", "la souveraineté populaire"],
    "Emmanuel-Joseph Sieyès": ["Qu'est-ce que le tiers état ?", "la théorie du pouvoir constituant de la nation"],
    "Thomas Hobbes": ["le Léviathan", "la soumission des individus à un souverain fort pour sortir de l'état de nature"],
    "Hans Kelsen": ["la pyramide des normes", "la théorie pure du droit", "le modèle de la cour constitutionnelle spécialisée"],
    "John Locke": ["Deux traités du gouvernement civil", "la défense des droits naturels que le pouvoir doit respecter"],
}, cat="Théorie de l'État", src=S, diff=3,
    fwd="À quel auteur se rattache : {item} ?", rev="Lequel de ces éléments se rattache à {group} ?",
    expl="Cet élément est classiquement associé à {group}.")

# ----------------------------------------------------------------------------------------------------------------------------
# Questions rédigées à la main


# --- Théorie de l'État
mcqr(C, "dc-m-etat", [
    ("Quels sont les trois éléments constitutifs classiques de l'État ?", "Un territoire, une population et un pouvoir politique organisé", ["Une armée, une monnaie et un drapeau", "Une langue, une religion et une capitale", "Un parlement, un gouvernement et une cour suprême"], "La définition classique de l'État repose sur le territoire, la population et un pouvoir public organisé.", 1),
    ("Lequel de ces éléments n'est pas exigé par la définition classique de l'État ?", "Une religion commune à toute la population", ["Un territoire délimité", "Une population", "Un pouvoir politique organisé"], "L'État peut être pluriconfessionnel ou laïque : la religion commune n'est pas un élément constitutif.", 2),
    ("Que traduit la souveraineté externe d'un État ?", "Son indépendance à l'égard des autres États dans l'ordre international", ["Son pouvoir de commander à tous les habitants de son territoire", "La séparation de ses pouvoirs législatif et exécutif", "Son appartenance à une organisation régionale"], "La souveraineté externe est l'indépendance de l'État dans ses relations avec les autres sujets du droit international.", 3),
    ("Que traduit la souveraineté interne d'un État ?", "Le pouvoir de commander à tous sur son territoire, sans concurrent", ["L'obligation de respecter les décisions des organisations internationales", "Le droit d'annexer les territoires voisins", "La liberté des collectivités locales de s'administrer seules"], "À l'intérieur, l'État détient la plus haute autorité : aucune autre puissance ne lui est supérieure.", 3),
    ("Quel auteur du XVIe siècle a théorisé la souveraineté comme puissance absolue et perpétuelle d'une République ?", "Jean Bodin", ["Montesquieu", "Jean-Jacques Rousseau", "Hans Kelsen"], "Jean Bodin, dans Les Six livres de la République, définit la souveraineté.", 3),
    ("Quel penseur a écrit De l'esprit des lois, où il défend la séparation des pouvoirs ?", "Montesquieu", ["John Locke", "Emmanuel-Joseph Sieyès", "Thomas Hobbes"], "Montesquieu y expose que le pouvoir doit arrêter le pouvoir.", 2),
    ("Pourquoi Montesquieu estime-t-il qu'il faut séparer les pouvoirs ?", "Pour que le pouvoir arrête le pouvoir et prévenir les abus", ["Pour accélérer l'adoption des lois", "Pour que le peuple gouverne directement", "Pour supprimer le pouvoir judiciaire"], "La séparation vise à empêcher la concentration du pouvoir, source d'abus.", 2),
    ("Qui a développé l'idée de souveraineté populaire et de volonté générale dans Du contrat social ?", "Jean-Jacques Rousseau", ["Montesquieu", "Jean Bodin", "Hans Kelsen"], "Rousseau fonde la légitimité sur la volonté générale du peuple.", 2),
    ("Quelle différence oppose la souveraineté nationale à la souveraineté populaire ?", "La première appartient à la nation, entité indivisible ; la seconde est partagée entre les citoyens", ["La première appartient au monarque ; la seconde au Parlement seul", "La première est interne, la seconde est externe", "La première n'admet que le vote direct, la seconde que le vote indirect"], "Souveraineté nationale : la nation, entité abstraite ; souveraineté populaire : chaque citoyen en détient une part.", 4),
    ("Quelle conséquence est classiquement attachée à la souveraineté nationale ?", "Un mandat représentatif et la possibilité d'un suffrage restreint", ["Un mandat impératif et un référendum obligatoire", "La révocation de tout élu par ses électeurs", "L'obligation du suffrage universel direct"], "La nation s'exprime par des représentants libres ; le suffrage peut être conçu comme une fonction.", 4),
    ("Quelle conséquence est classiquement attachée à la souveraineté populaire ?", "Le suffrage universel et la possibilité d'un mandat impératif", ["Un suffrage obligatoirement censitaire", "L'interdiction de tout référendum", "La suppression des élections"], "Chaque citoyen détenant une part de souveraineté, le droit de vote revient à tous.", 4),
    ("Quel auteur a défini la nation comme « un plébiscite de tous les jours » ?", "Ernest Renan", ["Jean Bodin", "Léon Duguit", "Raymond Carré de Malberg"], "Pour Renan, la nation repose sur la volonté de vivre ensemble, sans cesse renouvelée.", 4),
    ("Selon la conception dite objective de la nation, sur quoi celle-ci repose-t-elle surtout ?", "des éléments communs comme la langue, l'origine ou la culture", ["la seule volonté de vivre ensemble", "l'adhésion à un même parti politique", "la possession d'une armée commune"], "La conception objective (allemande) insiste sur des critères objectifs ; la conception subjective sur la volonté.", 4),
    ("Qui est associé à la théorie de la hiérarchie des normes en forme de pyramide ?", "Hans Kelsen", ["Léon Duguit", "Maurice Hauriou", "Jean Bodin"], "Kelsen décrit l'ordre juridique comme une hiérarchie de normes.", 2),
    ("Quelle notion Kelsen place-t-il au fondement de la pyramide des normes ?", "La norme fondamentale (Grundnorm), supposée", ["La volonté générale", "La coutume internationale", "La jurisprudence de la cour suprême"], "La norme fondamentale est une hypothèse qui fonde la validité de toute la hiérarchie.", 5),
    ("Dans la hiérarchie des normes internes, quelle norme se situe au-dessus de la loi ordinaire ?", "La Constitution", ["Le décret d'application", "L'arrêté municipal", "La circulaire ministérielle"], "La Constitution est la norme suprême de l'ordre juridique interne.", 1),
    ("Quel est l'ordre décroissant correct des normes internes ?", "Constitution, loi, décret, arrêté", ["Loi, Constitution, décret, arrêté", "Constitution, décret, loi, arrêté", "Décret, loi, Constitution, arrêté"], "Chaque norme doit respecter celles qui lui sont supérieures.", 2),
    ("Quel penseur est associé à l'idée de contrat par lequel les individus se soumettent à un souverain fort pour échapper à la guerre de tous contre tous ?", "Thomas Hobbes", ["John Locke", "Montesquieu", "Ernest Renan"], "Dans le Léviathan, Hobbes justifie un pouvoir fort contre l'état de nature.", 3),
    ("Quelle thèse défend John Locke ?", "Le pouvoir politique repose sur le consentement et doit respecter des droits naturels", ["Le souverain doit disposer d'un pouvoir absolu non limité", "Le droit est une pyramide de normes", "La nation est un plébiscite de tous les jours"], "Locke fonde le gouvernement civil sur le consentement et la protection des droits naturels.", 4),
    ("Que désigne la théorie du contrat social ?", "L'idée que l'autorité politique repose sur un accord, réel ou fictif, entre les individus", ["Un contrat de travail collectif entre l'État et les syndicats", "Un traité entre États voisins", "Un accord entre le juge et le législateur"], "Hobbes, Locke et Rousseau en proposent des versions différentes.", 2),
    ("Pour Léon Duguit, quel est le fondement du droit ?", "La solidarité sociale", ["La volonté du souverain", "La norme fondamentale", "Le contrat social"], "Duguit, chef de l'école du service public, rattache le droit à la solidarité sociale.", 5),
    ("Quel auteur a distingué l'État légal de l'État de droit dans sa Contribution à la théorie générale de l'État ?", "Raymond Carré de Malberg", ["Maurice Duverger", "Jean Bodin", "Ernest Renan"], "Carré de Malberg oppose l'État légal, centré sur la loi, à l'État de droit, qui soumet aussi le législateur à des normes supérieures.", 5),
    ("Qu'ajoute la conception substantielle (matérielle) de l'État de droit à sa conception formelle ?", "L'exigence que le contenu des normes respecte des droits fondamentaux", ["L'obligation d'élire le chef de l'État", "Le monopole du parlement sur la justice", "La suppression de la hiérarchie des normes"], "La conception formelle exige la légalité et la hiérarchie des normes ; la conception substantielle exige aussi des normes respectueuses des droits.", 5),
    ("Selon la Déclaration de 1789, où réside le principe de toute souveraineté ?", "Essentiellement dans la nation", ["Dans le roi", "Dans le Parlement", "Dans l'armée"], "La Déclaration des droits de l'homme et du citoyen consacre la souveraineté nationale.", 3),
    ("Comment la Déclaration de 1789 qualifie-t-elle la loi ?", "Comme l'expression de la volonté générale", ["Comme le commandement du monarque", "Comme une coutume écrite", "Comme un instrument du juge"], "La loi exprime la volonté générale selon ce texte révolutionnaire.", 3),
    ("Selon la Déclaration de 1789, quelles garanties une société doit-elle avoir pour posséder une Constitution ?", "La garantie des droits et la séparation des pouvoirs", ["Un roi héréditaire et une armée", "Une religion d'État et un parlement", "Une monnaie unique et un drapeau"], "Le texte de 1789 lie l'idée même de Constitution aux droits garantis et à la séparation des pouvoirs.", 4),
    ("Que garantit le principe de légalité dans un État de droit ?", "Que l'administration agit dans le cadre des règles de droit en vigueur", ["Que les juges peuvent créer librement la loi", "Que les lois sont votées à l'unanimité", "Que le chef de l'État n'est jamais élu"], "L'autorité publique est soumise au droit qu'elle applique.", 2),
    ("Que signifie l'indépendance du pouvoir judiciaire ?", "Les juges tranchent les litiges sans recevoir d'ordres de l'exécutif ni du législatif", ["Les juges votent les lois", "Les juges dirigent le gouvernement", "Les juges sont élus par les ministres pour un an"], "Elle est une condition de l'État de droit et du procès équitable.", 2),
    ("Que signifie l'inamovibilité des magistrats du siège ?", "Qu'ils ne peuvent être déplacés ou révoqués que dans les conditions que fixe la loi", ["Qu'ils ne peuvent jamais être sanctionnés", "Qu'ils sont élus à vie par le peuple", "Qu'ils sont désignés par tirage au sort"], "Cette garantie protège le juge des pressions liées à sa carrière.", 4),
    ("À quoi sert, dans son principe, un conseil supérieur de la magistrature ?", "garantir l'indépendance des magistrats, notamment pour leur carrière et leur discipline", ["voter les lois de finances", "contrôler la constitutionnalité des lois", "diriger la police judiciaire"], "Dans de nombreux États, cet organe intervient dans les nominations et la discipline des magistrats.", 4),
    ("Que désigne l'expression « démocratie militante » ?", "Un système où l'État peut limiter les libertés de ceux qui visent à détruire l'ordre démocratique", ["Une démocratie dirigée par l'armée", "Un régime fondé sur le service militaire obligatoire", "Une démocratie sans partis politiques"], "L'idée, associée à la Loi fondamentale allemande, est de défendre la démocratie contre ses ennemis.", 5),
], cat="Théorie de l'État", src=S)

# --- Formes de l'État
mcqr(C, "dc-m-forme", [
    ("Dans un État fédéral, qui possède en principe la souveraineté internationale ?", "L'État fédéral", ["Chaque entité fédérée séparément", "Le seul Sénat fédéral", "Les régions les plus peuplées"], "Les entités fédérées sont autonomes mais ne sont généralement pas des sujets du droit international.", 3),
    ("Quelle caractéristique distingue l'État fédéral de l'État unitaire décentralisé ?", "Les entités fédérées disposent d'un pouvoir normatif autonome garanti par la Constitution fédérale", ["Les collectivités locales ont des organes élus", "L'État dispose d'un territoire délimité", "Les collectivités ont la personnalité morale"], "Les trois autres éléments se retrouvent aussi dans un État unitaire décentralisé.", 4),
    ("Dans un État unitaire décentralisé, par quoi les compétences des collectivités territoriales sont-elles fixées ?", "la Constitution et la loi de l'État", ["une constitution propre à chaque collectivité", "un traité conclu entre chaque collectivité et l'État", "une souveraineté partagée avec l'État"], "Les collectivités n'ont pas de pouvoir constituant propre.", 3),
    ("Quelle est la différence essentielle entre décentralisation et déconcentration ?", "La décentralisation crée des entités distinctes de l'État, la déconcentration non", ["La déconcentration suppose des organes élus, la décentralisation non", "La décentralisation concerne la justice et la déconcentration la police", "Il n'y a aucune différence, ce sont des synonymes"], "Dans la déconcentration, les agents locaux restent subordonnés à l'autorité centrale.", 3),
    ("Quel acte fonde une confédération d'États ?", "Un traité", ["Une Constitution fédérale", "Une loi organique", "Un décret présidentiel"], "Les États confédérés restent souverains et se lient par traité.", 3),
    ("Quelle chambre représente classiquement les États fédérés dans un parlement fédéral bicaméral ?", "La chambre haute, souvent appelée Sénat", ["La chambre basse", "Le gouvernement fédéral", "La cour suprême"], "La chambre haute assure la représentation des entités fédérées.", 2),
    ("Dans la Constitution des États-Unis, combien de sénateurs chaque État compte-t-il ?", "Deux", ["Un", "Un nombre proportionnel à sa population", "Trois"], "Chaque État dispose du même nombre de sénateurs, quelle que soit sa population.", 3),
    ("Lequel de ces États est un exemple classique d'État fédéral ?", "Les États-Unis", ["La France", "Le Japon", "La Tunisie"], "Les États-Unis sont une fédération de cinquante États.", 1),
    ("Que dire de la « Confédération suisse », malgré son nom ?", "C'est un État fédéral", ["C'est une confédération au sens strict", "C'est un État unitaire", "C'est une monarchie constitutionnelle"], "Son nom est historique ; juridiquement la Suisse est une fédération de cantons.", 4),
    ("Quel est l'avantage généralement attendu de la décentralisation ?", "Rapprocher la décision des citoyens et favoriser la démocratie locale", ["Supprimer l'État", "Éliminer les élections locales", "Créer des États souverains"], "Elle permet une gestion locale par des élus.", 1),
    ("Quel est l'organe délibérant d'une commune dans la plupart des systèmes décentralisés ?", "Le conseil municipal", ["Le conseil des ministres", "Le Sénat", "La cour d'appel"], "Les affaires de la commune sont réglées par une assemblée élue localement.", 2),
    ("Pourquoi le bicamérisme est-il fréquent dans les États fédérés ?", "Une chambre permet de représenter les entités fédérées", ["Le droit international l'impose à tous les États fédéraux", "Le chef de l'État doit être élu par deux chambres", "Les juges y sont élus par les chambres"], "La seconde chambre assure l'expression de l'intérêt des États membres.", 3),
    ("Quelle est la portée de la libre administration des collectivités territoriales ?", "Elles gèrent leurs affaires locales par des organes élus, dans le cadre fixé par la loi", ["Elles sont totalement indépendantes de l'État", "Elles votent librement leur propre Constitution", "Elles disposent du droit de battre monnaie"], "La libre administration s'exerce dans les limites des lois et de l'unité de l'État.", 3),
    ("Au Cameroun, comment la Constitution de 1996 qualifie-t-elle l'organisation de l'État ?", "Un État unitaire décentralisé", ["Une fédération de dix États", "Une confédération", "Une union personnelle"], "L'État y est unitaire, avec des collectivités territoriales décentralisées.", 2),
    ("Au Cameroun, quelles sont les collectivités territoriales décentralisées prévues par la Constitution ?", "Les régions et les communes", ["Les États fédérés et les cantons", "Les provinces souveraines et les comtés", "Les Länder et les communes"], "La Constitution de 1996 consacre la région et la commune comme collectivités décentralisées.", 2),
    ("Selon la Constitution du Cameroun, que représente le Sénat ?", "Les collectivités territoriales décentralisées", ["Les partis politiques", "Les confessions religieuses", "Les forces armées"], "Le Sénat, seconde chambre du Parlement, y est lié aux collectivités territoriales décentralisées.", 3),
    ("De combien de chambres le Parlement camerounais se compose-t-il ?", "Deux : l'Assemblée nationale et le Sénat", ["Une seule : l'Assemblée nationale", "Deux : le Sénat et le Conseil constitutionnel", "Trois : l'Assemblée, le Sénat et la Cour suprême"], "Le Parlement du Cameroun est bicaméral.", 1),
    ("Au Cameroun, quelle chambre du Parlement est composée de députés ?", "L'Assemblée nationale", ["Le Sénat", "Le Conseil constitutionnel", "La Cour suprême"], "Les députés siègent à l'Assemblée nationale ; les sénateurs au Sénat.", 1),
    ("Selon la Constitution camerounaise, qui est garant de l'indépendance du pouvoir judiciaire ?", "Le président de la République", ["Le Premier ministre", "Le président du Sénat", "Le président de l'Assemblée nationale"], "La Constitution confie cette garantie au chef de l'État.", 3),
    ("Au Cameroun, comment le président de la République est-il élu ?", "Au suffrage universel direct", ["Par l'Assemblée nationale", "Par le Sénat", "Par un collège de chefs traditionnels"], "Il est élu directement par les électeurs.", 2),
    ("Quelle est la place du préambule dans la Constitution camerounaise de 1996 ?", "Il fait partie intégrante de la Constitution", ["Il est exclu du texte constitutionnel", "Il est une simple déclaration sans portée", "Il est annexé à une loi ordinaire"], "Le préambule y est intégré au texte constitutionnel.", 3),
    ("Quelles sont les langues officielles de la République du Cameroun ?", "Le français et l'anglais", ["Le français et l'arabe", "L'anglais et le fulfulde", "Le français seul"], "La Constitution consacre le bilinguisme officiel d'égale valeur.", 1),
    ("Quel principe la décentralisation camerounaise vise-t-elle à promouvoir selon la Constitution ?", "Le développement et la participation des populations à la gestion des affaires locales", ["La fédéralisation du pays", "La suppression de l'administration de l'État", "La souveraineté propre de chaque région"], "La décentralisation y est présentée comme un axe de développement, de démocratie et de bonne gouvernance locale.", 3),
    ("Où siège l'Assemblée nationale du Cameroun ?", "À Yaoundé", ["À Douala", "À Buea", "À Bamenda"], "Yaoundé, capitale politique, accueille les institutions de la République.", 1),
], cat="Formes de l'État et décentralisation", src=S)

# --- Constitution
mcqr(C, "dc-m-cst", [
    ("Quel est le caractère du pouvoir constituant originaire ?", "Il est en principe initial et non encadré par la Constitution antérieure", ["Il est encadré par les règles de la Constitution qu'il remplace", "Il appartient toujours au juge constitutionnel", "Il est exercé uniquement par le Parlement ordinaire"], "Il fonde un ordre nouveau, tandis que le pouvoir dérivé agit dans le cadre d'une Constitution existante.", 4),
    ("Selon Sieyès, à qui appartient le pouvoir constituant ?", "À la nation", ["Au monarque", "Au gouvernement", "Aux juges"], "Sieyès affirme que la nation est le titulaire du pouvoir constituant.", 3),
    ("Une Constitution peut-elle être à la fois écrite et souple ?", "Oui, car la souplesse dépend de la procédure de révision, non de l'existence d'un texte", ["Non, toute Constitution écrite est rigide", "Non, seule une Constitution coutumière est souple", "Oui, mais seulement dans une monarchie"], "Le critère souple/rigide tient à la procédure de révision comparée à celle des lois ordinaires.", 4),
    ("Pourquoi qualifie-t-on la Constitution du Royaume-Uni de largement non codifiée ?", "Elle ne figure pas dans un document unique mais dans des lois, des conventions et des coutumes", ["Elle a été abrogée par le Parlement", "Elle n'existe que sous forme orale dans les provinces", "Elle est contenue entièrement dans un traité international"], "Les règles constitutionnelles britanniques sont dispersées entre lois, jurisprudence et conventions.", 3),
    ("Quel est l'intérêt principal de la rigidité constitutionnelle ?", "Protéger la Constitution contre des changements trop faciles par la majorité du moment", ["Permettre de la modifier chaque année", "Supprimer tout contrôle de constitutionnalité", "Interdire définitivement toute révision"], "Une procédure exigeante donne de la stabilité et de la supériorité à la Constitution.", 2),
    ("En droit français, quel élément ne peut faire l'objet d'une révision constitutionnelle ?", "La forme républicaine du gouvernement", ["Le mode d'élection des sénateurs", "Le nombre des ministres", "Le calendrier de la session budgétaire"], "La Constitution interdit de réviser la forme républicaine du gouvernement.", 4),
    ("Quel principe est protégé par la clause d'éternité de la Loi fondamentale allemande ?", "La dignité humaine", ["La liberté du commerce", "Le calendrier des élections", "Le nom de la capitale"], "La Loi fondamentale protège notamment le principe de dignité de la personne contre toute révision.", 5),
    ("Qu'appelle-t-on limite circonstancielle à la révision constitutionnelle ?", "L'interdiction de réviser pendant certaines circonstances, par exemple en période de crise grave", ["L'interdiction de réviser certains principes comme la forme de l'État", "L'obligation de recourir à un référendum", "L'obligation d'obtenir l'accord des collectivités territoriales"], "La limite est liée aux circonstances et non au contenu de la révision.", 4),
    ("Qu'est-ce qui distingue la révision de l'abrogation d'une Constitution ?", "La révision modifie le texte selon ses règles ; l'abrogation le remplace par un ordre nouveau", ["La révision supprime tout le texte ; l'abrogation change un article", "La révision est faite par le juge, l'abrogation par le gouvernement", "La révision est réservée aux monarchies, l'abrogation aux républiques"], "Le remplacement complet relève du pouvoir constituant originaire.", 4),
    ("Que désigne la promulgation d'une loi ?", "L'acte par lequel le chef de l'État constate l'adoption de la loi et ordonne son exécution", ["Le vote de la loi par le parlement", "La publication d'un arrêté municipal", "La saisine du juge constitutionnel"], "La promulgation est postérieure au vote et précède la publication.", 3),
    ("Quel rôle joue la publication d'une loi ?", "Elle rend la loi accessible au public, condition de son entrée en vigueur", ["Elle permet de voter la loi une seconde fois", "Elle remplace la promulgation", "Elle supprime tout recours"], "Une loi non publiée ne peut être opposable à tous.", 3),
    ("Laquelle de ces règles relève typiquement d'une Constitution au sens matériel ?", "Celle qui fixe le mode de désignation du chef de l'État", ["Celle qui fixe le prix du pain", "Celle qui encadre la vente de médicaments", "Celle qui fixe les tarifs des taxis"], "Les règles sur l'organisation et l'exercice du pouvoir sont constitutionnelles par leur objet.", 3),
    ("Quelle valeur le juge constitutionnel peut-il reconnaître au préambule d'une Constitution ?", "Une valeur constitutionnelle, s'il l'intègre aux normes de référence du contrôle", ["Aucune valeur juridique, par principe, dans tous les États", "Une valeur uniquement morale dans tous les cas", "Une valeur supérieure à la Constitution entière"], "Selon les systèmes, le préambule peut faire partie du bloc de constitutionnalité.", 4),
    ("Quel auteur britannique est associé à la distinction entre Constitutions souples et rigides ?", "James Bryce", ["A. V. Dicey", "Walter Bagehot", "John Stuart Mill"], "Bryce a popularisé cette classification à la fin du XIXe siècle.", 5),
    ("Quel auteur a distingué, à propos du régime britannique, les parties « dignes » et les parties « efficientes » de la Constitution ?", "Walter Bagehot", ["A. V. Dicey", "James Bryce", "Edmund Burke"], "Bagehot, dans The English Constitution, oppose les éléments de prestige et ceux qui gouvernent réellement.", 5),
    ("Quel juriste britannique a systématisé la souveraineté du Parlement et la rule of law ?", "A. V. Dicey", ["Walter Bagehot", "Hans Kelsen", "Raymond Carré de Malberg"], "Dicey est l'auteur de l'Introduction à l'étude du droit constitutionnel.", 5),
    ("Que signifie traditionnellement la souveraineté du Parlement dans la pensée britannique ?", "Le Parlement peut faire ou défaire toute loi, qu'aucun juge n'annule pour inconstitutionnalité", ["La Cour suprême peut annuler les lois", "Le monarque détient seul le pouvoir législatif", "Chaque loi exige un référendum"], "C'est la conception classique, indépendamment de ses nuances contemporaines.", 4),
    ("Que sont les « conventions de la Constitution » au Royaume-Uni ?", "Des règles de pratique politique habituellement respectées, mais non sanctionnées par un juge", ["Des traités conclus avec l'Union européenne", "Des lois votées à l'unanimité", "Des arrêts de la Cour suprême"], "Leur respect repose sur l'usage et la responsabilité politique.", 5),
    ("Quel texte allemand de 1949 tient lieu de Constitution ?", "La Loi fondamentale", ["La Constitution de Weimar", "Le traité de Versailles", "Le Code civil allemand"], "La Loi fondamentale (Grundgesetz) a été adoptée en 1949.", 3),
    ("Dans quelle ville la Constitution américaine a-t-elle été rédigée en 1787 ?", "Philadelphie", ["Washington", "New York", "Boston"], "La convention constitutionnelle s'est tenue à Philadelphie.", 3),
    ("Qui a écrit Le Fédéraliste pour défendre la Constitution américaine ?", "Hamilton, Madison et Jay", ["Jefferson, Adams et Franklin", "Washington, Lincoln et Jackson", "Marshall, Story et Kent"], "Ces trois auteurs ont rédigé les articles publiés sous le pseudonyme de Publius.", 5),
    ("Comment s'appelle l'ensemble des dix premiers amendements de la Constitution américaine ?", "Le Bill of Rights", ["La Déclaration d'indépendance", "Le Mayflower Compact", "Le Fédéraliste"], "Ces amendements protègent des libertés fondamentales face au pouvoir fédéral.", 3),
    ("Quel texte anglais de 1215 est un symbole de la limitation du pouvoir royal ?", "La Magna Carta", ["Le Bill of Rights de 1689", "L'Habeas Corpus Act de 1679", "La Déclaration d'indépendance"], "La Grande Charte de 1215 est un jalon du constitutionnalisme.", 3),
    ("Dans quel pays est né l'habeas corpus ?", "En Angleterre", ["En France", "En Allemagne", "En Espagne"], "L'Habeas corpus est une garantie historique de la liberté individuelle en droit anglais.", 4),
    ("Qu'est-ce que le référendum constituant ?", "Un référendum portant sur l'adoption ou la révision de la Constitution", ["Un référendum portant sur une loi ordinaire", "Un référendum destiné à révoquer un élu", "Une élection présidentielle"], "Il se distingue du référendum législatif par son objet.", 3),
    ("En quoi un régime constitutionnel se distingue-t-il d'un régime sans Constitution au sens du constitutionnalisme ?", "Le pouvoir y est limité par des règles supérieures et les droits y sont garantis", ["Il comporte nécessairement un monarque", "Il interdit toute élection", "Il confie tous les pouvoirs au même organe"], "Le constitutionnalisme associe limitation du pouvoir et garantie des droits.", 4),
    ("Selon la théorie dualiste, comment le droit international s'applique-t-il en droit interne ?", "Il doit être reçu ou transposé par un acte interne, car les deux ordres sont distincts", ["Il s'applique sans aucune formalité et prime la Constitution", "Il ne peut jamais s'appliquer", "Il est identique au droit interne"], "Le dualisme sépare les deux ordres, d'où la nécessité d'une réception.", 5),
    ("Qu'est-ce qu'une disposition d'un traité dite « self-executing » ?", "Une disposition précise que l'on peut invoquer directement devant le juge sans mesure d'application", ["Une disposition qui s'applique seulement aux États non signataires", "Une disposition qui exige un référendum", "Une disposition réservée aux organisations internationales"], "L'applicabilité directe permet à un justiciable de s'en prévaloir devant le juge.", 5),
    ("En droit français, sous quelles conditions un traité a-t-il une autorité supérieure à celle des lois ?", "S'il est régulièrement ratifié, publié et appliqué aussi par l'autre partie", ["Dès sa signature par le chef de l'État", "Seulement s'il a été approuvé par référendum", "Jamais : la loi l'emporte toujours"], "La Constitution française pose ces conditions, dont la réciprocité.", 5),
], cat="La Constitution", src=S)

# --- Séparation des pouvoirs, régimes
mcqr(C, "dc-m-reg", [
    ("Laquelle de ces caractéristiques est propre au régime présidentiel de type américain ?", "Le président ne peut pas dissoudre le Congrès, qui ne peut pas le renverser par censure", ["Le gouvernement est responsable devant le parlement par motion de censure", "Le chef de l'État est un monarque irresponsable", "Le parlement choisit et révoque librement le chef de l'exécutif"], "La séparation est rigide : pas de dissolution, pas de motion de censure.", 3),
    ("Quel est le critère essentiel du régime parlementaire ?", "La responsabilité politique du gouvernement devant le parlement", ["L'élection du chef de l'État au suffrage universel direct", "L'absence de toute séparation des pouvoirs", "La suppression du pouvoir judiciaire"], "Le gouvernement doit disposer de la confiance du parlement.", 2),
    ("Dans un régime parlementaire, quel moyen d'action l'exécutif possède-t-il en principe contre l'assemblée ?", "La dissolution", ["Le veto absolu", "La révocation individuelle des députés", "L'impeachment"], "Le droit de dissolution équilibre la responsabilité du gouvernement.", 3),
    ("Quel moyen d'action le parlement possède-t-il contre le gouvernement dans un régime parlementaire ?", "La motion de censure ou le refus de confiance", ["La dissolution", "Le veto", "La promulgation"], "Il peut mettre en jeu la responsabilité politique du gouvernement.", 2),
    ("Dans le régime parlementaire dualiste, comment l'exécutif est-il organisé ?", "bicéphale : un chef de l'État et un gouvernement dirigé par un chef de gouvernement", ["monocéphale : une seule personne détient tout", "remplacé par un comité du parlement", "confié aux juges"], "On distingue le chef de l'État et le chef du gouvernement.", 3),
    ("Dans le parlementarisme dualiste (orléaniste), devant qui le gouvernement est-il responsable ?", "Devant le parlement et devant le chef de l'État", ["Devant le seul parlement", "Devant le seul chef de l'État", "Devant les seules collectivités territoriales"], "Dans le parlementarisme moniste, il ne répond que devant le parlement.", 5),
    ("Qu'est-ce qui caractérise le régime d'assemblée ?", "L'organe législatif domine l'exécutif, qui lui est subordonné et révocable", ["Le chef de l'État ne peut pas être renversé", "L'exécutif peut dissoudre l'assemblée à tout moment", "Le juge dirige l'exécutif"], "L'assemblée concentre l'essentiel du pouvoir.", 3),
    ("Quel régime est associé historiquement à la Convention nationale française de 1792-1795 ?", "Le gouvernement d'assemblée, dit régime conventionnel", ["Le régime présidentiel", "Le régime semi-présidentiel", "La monarchie absolue"], "La Convention concentrait les pouvoirs.", 4),
    ("Quel pays est cité comme exemple de régime directorial ou collégial ?", "La Suisse", ["Les États-Unis", "Le Royaume-Uni", "L'Allemagne"], "Le Conseil fédéral suisse est un exécutif collégial élu par l'Assemblée fédérale.", 5),
    ("Qui a proposé la notion de régime semi-présidentiel ?", "Maurice Duverger", ["Montesquieu", "Hans Kelsen", "Georges Vedel"], "Duverger a forgé cette expression pour décrire notamment la Ve République.", 4),
    ("Quel trait distingue le régime semi-présidentiel du parlementarisme classique ?", "Le chef de l'État est élu au suffrage universel direct et dispose de pouvoirs propres", ["Le gouvernement n'est pas responsable devant le parlement", "Le parlement est monocaméral", "Le chef de l'État est héréditaire"], "Le gouvernement reste responsable devant le parlement.", 3),
    ("Quelle République française est citée comme exemple de régime semi-présidentiel ?", "La Cinquième République", ["La Troisième République", "La Convention", "Le Second Empire"], "La Ve République combine président élu et gouvernement responsable.", 2),
    ("En quelle année l'élection du président français au suffrage universel direct a-t-elle été instaurée par référendum ?", "1962", ["1958", "1969", "1974"], "Le référendum d'octobre 1962 a instauré l'élection directe.", 4),
    ("À quoi sert le contreseing ministériel dans un régime parlementaire ?", "À faire endosser par un ministre la responsabilité d'un acte du chef de l'État irresponsable", ["À prouver l'authenticité de la signature", "À réduire le pouvoir du juge constitutionnel", "À remplacer la promulgation"], "Le chef de l'État étant irresponsable, un ministre répond de ses actes.", 4),
    ("Quel exemple de régime parlementaire à monarchie constitutionnelle cite-t-on couramment ?", "Le Royaume-Uni", ["Les États-Unis", "La Suisse", "Le Brésil"], "Le monarque y règne, le gouvernement gouverne devant la Chambre des communes.", 1),
    ("Comment s'appelle le chef du gouvernement fédéral allemand ?", "Le chancelier", ["Le Premier ministre", "Le président de la République fédérale", "Le président du Conseil"], "Le chancelier fédéral dirige le gouvernement allemand.", 2),
    ("Qu'est-ce que la motion de défiance constructive connue en Allemagne ?", "Le parlement ne peut renverser le chancelier qu'en élisant simultanément un successeur", ["Le chancelier ne peut jamais être renversé", "Le président dissout le Bundestag sans condition", "Le Bundesrat nomme le chancelier"], "Ce mécanisme de parlementarisme rationalisé vise la stabilité gouvernementale.", 5),
    ("Comment le président des États-Unis est-il formellement élu ?", "Par un collège de grands électeurs désignés par le vote populaire dans chaque État", ["Directement par la majorité nationale des voix", "Par le Sénat", "Par la Cour suprême"], "L'élection présidentielle américaine est indirecte.", 4),
    ("Que désigne le système des « checks and balances » ?", "Un équilibre où chaque pouvoir dispose de moyens de freiner les autres", ["La suppression de l'exécutif", "Une séparation sans aucun contrôle", "La domination du pouvoir judiciaire"], "Il est caractéristique du système constitutionnel américain.", 3),
    ("Quel organe vote les lois au niveau fédéral aux États-Unis ?", "Le Congrès", ["La Cour suprême", "Le Cabinet présidentiel", "La Réserve fédérale"], "Le Congrès est le pouvoir législatif fédéral.", 1),
    ("De quelles chambres le Congrès américain est-il composé ?", "La Chambre des représentants et le Sénat", ["Le Sénat et la Cour suprême", "Le Bundestag et le Bundesrat", "L'Assemblée nationale et le Conseil d'État"], "Le Congrès est bicaméral.", 2),
    ("Qui préside le Sénat des États-Unis ?", "Le vice-président des États-Unis", ["Le président des États-Unis", "Le président de la Cour suprême", "Le président de la Chambre des représentants"], "Le vice-président préside le Sénat et ne vote qu'en cas d'égalité.", 4),
    ("Dans quelle juridiction spéciale juge-t-on, dans de nombreux États, le chef de l'État pour haute trahison ?", "Une haute cour de justice", ["Le tribunal de commerce", "Le conseil municipal", "Le tribunal de simple police"], "Cette juridiction spéciale traite de la responsabilité pénale du chef de l'État.", 3),
    ("Qu'est-ce que l'irresponsabilité politique du chef de l'État dans un régime parlementaire ?", "Il ne peut être renversé par le parlement pour sa politique, car le gouvernement en répond", ["Il n'a aucune fonction", "Il ne peut jamais être jugé pénalement dans aucun cas", "Il est élu à vie"], "Le contreseing reporte la responsabilité sur les ministres.", 4),
    ("À quoi sert le droit de dissolution dans un régime parlementaire ?", "À rétablir l'équilibre en permettant à l'exécutif de demander l'arbitrage des électeurs", ["À supprimer le parlement définitivement", "À nommer les juges", "À modifier la Constitution sans vote"], "Il équilibre la menace de la motion de censure.", 3),
    ("Laquelle de ces autorités n'appartient pas au pouvoir exécutif dans un régime classique ?", "Le président du parlement", ["Le chef de l'État", "Le chef du gouvernement", "Les ministres"], "Le président du parlement relève du pouvoir législatif.", 2),
    ("Qu'est-ce que le domaine de la loi ?", "L'ensemble des matières que la Constitution réserve au législateur", ["Les matières confiées exclusivement au juge", "L'ensemble des textes de l'ONU", "Les décrets du chef de l'État"], "En dehors du domaine de la loi, l'exécutif peut agir par règlement.", 3),
    ("Que désigne le pouvoir réglementaire autonome ?", "Le pouvoir de l'exécutif de régler par décret ce que la Constitution ne réserve pas à la loi", ["Le pouvoir du juge d'annuler une loi", "Le pouvoir des collectivités de voter leur propre Constitution", "Le pouvoir du parlement de gouverner par arrêté"], "Il s'exerce hors du domaine réservé à la loi.", 4),
    ("Quelle institution vote traditionnellement la loi de finances dans un régime démocratique ?", "Le parlement", ["La cour suprême", "Le chef de l'État seul", "Les collectivités locales"], "Le consentement à l'impôt et au budget relève des représentants du peuple.", 2),
    ("Pourquoi le parlement vote-t-il l'impôt dans la tradition démocratique ?", "Parce que le consentement des représentants du peuple à l'impôt est un principe démocratique ancien", ["Parce que les juges ne peuvent pas le faire", "Parce que l'impôt est facultatif", "Parce que le chef de l'État ne peut pas lever d'impôt sur les étrangers"], "Le principe « pas d'impôt sans représentation » exprime cette idée.", 3),
    ("Laquelle de ces actions n'est pas un moyen de contrôle parlementaire de l'exécutif ?", "La promulgation des lois", ["Les questions au gouvernement", "La commission d'enquête", "L'interpellation"], "La promulgation est un acte du chef de l'État.", 3),
    ("Quelle est la différence entre une loi ordinaire et une loi organique ?", "La loi organique précise l'organisation des pouvoirs publics, souvent selon une procédure renforcée", ["La loi organique ne concerne que les lois fiscales", "La loi organique n'est jamais contrôlée", "La loi organique n'existe que dans les monarchies"], "Elle complète la Constitution et obéit souvent à une procédure particulière.", 4),
    ("Qui édicte une ordonnance prise sur habilitation ?", "Le gouvernement, sur autorisation du parlement", ["Le juge constitutionnel", "Le président de l'Assemblée", "La cour d'appel"], "Le parlement délègue temporairement une partie de sa compétence.", 3),
], cat="Séparation des pouvoirs et régimes politiques", src=S)

# --- Suffrage, scrutins, partis, référendum
mcqr(C, "dc-m-elec", [
    ("Quel auteur est associé aux « lois » reliant mode de scrutin et système de partis ?", "Maurice Duverger", ["Hans Kelsen", "Georges Vedel", "Raymond Carré de Malberg"], "Duverger a étudié les effets des modes de scrutin sur le nombre de partis.", 3),
    ("Selon Duverger, que tend à favoriser le scrutin majoritaire à un tour ?", "Le bipartisme", ["Le multipartisme intégral", "Le parti unique obligatoire", "L'absence de partis"], "Il tend à regrouper les forces politiques autour de deux grands partis.", 3),
    ("Selon Duverger, que tend à favoriser la représentation proportionnelle ?", "Le multipartisme", ["Le bipartisme strict", "Le parti unique", "La disparition des partis"], "La proportionnelle permet à plusieurs partis d'obtenir des sièges.", 3),
    ("Selon Duverger, que tend à favoriser le scrutin majoritaire à deux tours ?", "Le multipartisme atténué par des alliances", ["Le bipartisme parfait et automatique", "Le parti unique", "La suppression du second tour"], "Le second tour incite aux alliances entre partis.", 4),
    ("Quel est le principal reproche fait au scrutin majoritaire à un tour ?", "Il peut sous-représenter fortement les petits partis", ["Il impose une proportionnelle intégrale", "Il interdit de voter pour un candidat", "Il favorise toujours les petites listes"], "Il peut produire un décalage entre voix et sièges.", 2),
    ("Quel est le principal avantage souvent attribué au scrutin proportionnel ?", "Une représentation plus fidèle des courants d'opinion", ["Une majorité stable garantie", "Une élection sans partis", "Un lien exclusif entre un député et sa circonscription"], "Les sièges reflètent mieux la répartition des voix.", 2),
    ("Quel inconvénient est fréquemment reproché à la proportionnelle ?", "Le risque de fragmentation et de coalitions instables", ["L'impossibilité de voter pour une liste", "La disparition des partis", "L'interdiction du second tour"], "La multiplication des partis complique la formation de majorités.", 3),
    ("Dans une circonscription, 60 000 suffrages exprimés et 6 sièges à pourvoir : quel est le quotient électoral simple ?", "10 000", ["6 000", "360 000", "66 000"], "Le quotient est obtenu en divisant les suffrages exprimés par le nombre de sièges : 60 000 / 6.", 3),
    ("Une liste obtient 35 000 voix, le quotient électoral est de 10 000 : combien de sièges obtient-elle au quotient ?", "3", ["4", "5", "2"], "35 000 contient trois fois 10 000 ; le reste de 5 000 sert au calcul du plus fort reste.", 3),
    ("Avec la méthode de la plus forte moyenne (d'Hondt), 4 sièges, la liste A a 60 000 voix et la liste B 28 000 : quelle répartition ?", "A obtient 3 sièges, B obtient 1 siège", ["A obtient 2 sièges, B obtient 2 sièges", "A obtient 4 sièges, B n'en obtient aucun", "A obtient 1 siège, B obtient 3 sièges"], "Les moyennes successives sont : A 60, 30, 20 ; B 28 ; le quatrième siège va à A (20) avant B (14).", 5),
    ("À quoi sert un seuil électoral ?", "À limiter la fragmentation en écartant les listes très faibles", ["À garantir au moins un siège à chaque liste", "À fixer le nombre de sièges de la chambre", "À empêcher toute coalition"], "Il exige un minimum de voix pour participer à la répartition des sièges.", 2),
    ("Comment appelle-t-on dans le monde anglo-saxon le scrutin uninominal majoritaire à un tour ?", "Le « first past the post »", ["Le vote alternatif", "La liste bloquée", "Le vote préférentiel"], "Le candidat arrivé en tête l'emporte.", 3),
    ("Laquelle de ces conditions n'est pas exigée pour voter dans un système de suffrage universel ?", "Payer un impôt d'un certain montant", ["Avoir la nationalité en principe", "Atteindre l'âge fixé par la loi", "Jouir de ses droits civiques"], "Le critère de richesse caractérise le suffrage censitaire.", 2),
    ("Quelle conception du suffrage est liée à la souveraineté nationale ?", "Le suffrage-fonction", ["Le suffrage-droit", "Le vote obligatoire absolu", "Le plébiscite permanent"], "Dans cette conception, la nation confie la fonction électorale aux citoyens qu'elle choisit.", 5),
    ("Quelle conception du suffrage est liée à la souveraineté populaire ?", "Le suffrage-droit", ["Le suffrage-fonction", "Le suffrage restreint obligatoire", "Le vote plural nécessaire"], "Chacun détient une part de souveraineté, donc un droit de vote.", 5),
    ("Quel principe est violé par un vote plural ?", "L'égalité du suffrage", ["Le secret du vote", "L'universalité du vote", "La liberté du vote"], "Certains électeurs disposent de plus de voix que d'autres.", 3),
    ("Qu'est-ce que le gerrymandering ?", "Un découpage des circonscriptions fait pour avantager un camp politique", ["Une méthode de calcul du quotient", "Un mode de scrutin à liste bloquée", "Un contrôle de la régularité du scrutin"], "Le terme vient d'un découpage jugé avantageux pour un parti aux États-Unis.", 4),
    ("Quelle différence y a-t-il entre inéligibilité et incompatibilité ?", "L'inéligibilité empêche d'être élu ; l'incompatibilité oblige à choisir entre deux fonctions", ["L'incompatibilité empêche d'être candidat ; l'inéligibilité concerne les électeurs", "L'inéligibilité vise les seuls étrangers, l'incompatibilité les nationaux", "L'inéligibilité est pénale, l'incompatibilité est fiscale"], "L'inéligibilité vise la candidature ; l'incompatibilité vise le cumul de fonctions.", 4),
    ("Quel est le rôle des partis politiques dans une démocratie pluraliste ?", "Structurer l'expression des opinions et présenter des candidats", ["Rendre la justice", "Voter à la place des citoyens", "Exécuter les décisions de justice"], "Ils animent la compétition politique et la représentation.", 1),
    ("Quel auteur a opposé les partis de cadres aux partis de masse ?", "Maurice Duverger", ["Hans Kelsen", "Léon Duguit", "Ernest Renan"], "Cette classification figure dans son étude des partis politiques.", 4),
    ("Quelle différence distingue un parti politique d'un groupe de pression ?", "Le parti vise le pouvoir par les élections ; le groupe de pression influence les décisions", ["Le parti est illégal, le groupe de pression légal", "Le groupe de pression présente des candidats, le parti non", "Le parti défend un seul métier, le groupe de pression un territoire"], "Le groupe de pression défend des intérêts sans vouloir gouverner.", 3),
    ("Quelle solution est fréquente lorsqu'aucun parti n'a la majorité absolue en régime parlementaire ?", "La formation d'une coalition entre plusieurs partis", ["La suppression des élections suivantes", "La nomination automatique du plus âgé des élus", "Un référendum obligatoire sur chaque loi"], "Un gouvernement de coalition réunit une majorité.", 3),
    ("Pourquoi le financement des partis politiques est-il réglementé dans de nombreux pays ?", "Pour assurer la transparence et limiter l'influence de l'argent", ["Pour interdire la compétition politique", "Pour supprimer les campagnes électorales", "Pour réserver les partis aux fonctionnaires"], "Les règles visent l'égalité et l'intégrité de la compétition politique.", 2),
    ("Dans quel type de démocratie le référendum et l'initiative populaire sont-ils des outils centraux ?", "La démocratie semi-directe", ["La démocratie purement représentative", "L'autocratie", "La monarchie absolue"], "Ces procédures complètent le système représentatif.", 2),
    ("Quelle différence y a-t-il entre référendum obligatoire et référendum facultatif ?", "Le premier est imposé par le droit ; le second dépend d'une décision libre d'une autorité", ["Le premier est consultatif, le second décisionnel", "Le premier se tient avant la loi, le second après", "Le premier concerne les élus, le second les juges"], "L'obligation tient à l'exigence du texte constitutionnel ou légal.", 3),
    ("Quel danger est souvent associé au référendum utilisé comme vote de confiance personnel ?", "Les électeurs répondent à la personne qui pose la question plutôt qu'à la question", ["Le référendum devient secret", "Le vote devient censitaire", "Le chef de l'État perd le droit de dissoudre"], "C'est le glissement plébiscitaire.", 4),
    ("Quel pays est réputé pour le recours fréquent à la votation populaire ?", "La Suisse", ["La Chine", "L'Allemagne au niveau fédéral", "Les États-Unis au niveau fédéral"], "La démocratie semi-directe y est très développée.", 3),
    ("Qu'est-ce que le vote blanc ?", "Un bulletin ne portant aucun choix, déposé dans l'urne", ["Le fait de ne pas se rendre aux urnes", "Un vote par procuration", "Un bulletin déposé hors de l'urne"], "Il se distingue de l'abstention, où l'électeur ne vote pas.", 2),
    ("Que garantit le caractère secret du vote ?", "Que personne ne puisse connaître ni imposer le choix de l'électeur", ["Que les résultats restent secrets", "Que les candidats soient inconnus", "Que le vote soit obligatoire"], "Le secret protège la liberté du suffrage.", 2),
    ("Quel est l'objet d'un contentieux électoral ?", "Les contestations portant sur la régularité des élections et de leurs résultats", ["Les litiges commerciaux entre partis", "Le financement des tribunaux", "La naturalisation des candidats"], "Un juge, souvent constitutionnel ou administratif, tranche ces litiges.", 3),
    ("Qu'est-ce que le suffrage universel indirect ?", "Un vote où tous les électeurs désignent des grands électeurs, qui choisissent ensuite les élus", ["Un vote réservé aux riches", "Un vote par correspondance uniquement", "Un vote où chaque électeur a plusieurs voix"], "L'universalité concerne le droit de voter ; l'indirection, le nombre de degrés.", 3),
    ("En droit électoral, que désigne l'inscription sur les listes électorales ?", "La formalité qui permet à un citoyen d'exercer son droit de vote", ["Une candidature à un mandat", "Un impôt électoral", "Un vote par procuration"], "Sans inscription, l'électeur ne peut généralement pas voter.", 2),
    ("Que signifie le mandat représentatif quant aux instructions des électeurs ?", "L'élu n'est pas juridiquement tenu de les suivre", ["L'élu doit les suivre sous peine de révocation", "L'élu doit les soumettre au juge", "L'élu ne peut prendre aucune décision"], "Il représente la nation entière.", 3),
    ("À quoi sert un référendum dit « ratificatif » ?", "À faire approuver par les citoyens un texte préparé par les pouvoirs publics", ["À désigner les membres du gouvernement", "À révoquer le chef de l'État par la rue", "À contrôler la constitutionnalité d'une loi"], "Les citoyens confirment ou refusent le texte qui leur est soumis.", 4),
], cat="Suffrage, scrutins et partis", src=S)

# --- Contrôle de constitutionnalité
mcqr(C, "dc-m-ctr", [
    ("Quel arrêt américain de 1803 est considéré comme fondateur du contrôle de constitutionnalité par le juge ?", "Marbury v. Madison", ["Brown v. Board of Education", "Roe v. Wade", "Miranda v. Arizona"], "La Cour suprême y a affirmé son pouvoir d'écarter une loi contraire à la Constitution.", 4),
    ("Quel juge a rédigé l'opinion de la Cour dans l'arrêt Marbury v. Madison ?", "John Marshall", ["Earl Warren", "Oliver Wendell Holmes", "Benjamin Cardozo"], "Marshall était alors Chief Justice.", 5),
    ("Quel modèle de contrôle de constitutionnalité est associé aux États-Unis ?", "Un contrôle diffus exercé par tous les juges à l'occasion d'un litige", ["Un contrôle exclusivement préventif par une cour spéciale", "Un contrôle politique par le chef de l'État", "Une absence totale de contrôle"], "Tout juge peut écarter une loi inconstitutionnelle, la Cour suprême ayant le dernier mot.", 3),
    ("À quel modèle est associée la cour constitutionnelle autrichienne de 1920, inspirée par Kelsen ?", "Un contrôle concentré confié à une cour spécialisée", ["Un contrôle diffus par tous les juges", "Un contrôle confié à l'armée", "Un contrôle exercé par le monarque"], "C'est le modèle dit européen ou kelsénien.", 4),
    ("Dans le modèle américain, quel est l'effet formel d'une décision écartant une loi ?", "La loi est écartée dans le litige, avec une forte portée par le précédent", ["La loi est annulée pour tous immédiatement", "La décision est purement consultative", "La loi est automatiquement remplacée par un décret"], "L'effet est formellement limité aux parties, mais le précédent s'impose en pratique.", 5),
    ("Dans le modèle européen, quel est l'effet habituel d'une décision déclarant une loi inconstitutionnelle ?", "La disposition est annulée ou abrogée, avec effet erga omnes", ["La disposition reste en vigueur sauf pour les parties", "L'effet est limité au litige en cours", "Le juge réécrit lui-même la loi"], "La décision de la cour constitutionnelle s'impose à tous.", 4),
    ("Quel organe français exerce le contrôle de constitutionnalité des lois ?", "Le Conseil constitutionnel", ["Le Conseil d'État", "La Cour de cassation", "La Cour des comptes"], "Le Conseil constitutionnel est le juge constitutionnel en France.", 1),
    ("Quel type de contrôle a longtemps caractérisé le système français ?", "Un contrôle a priori, avant promulgation", ["Un contrôle uniquement a posteriori par tous les juges", "Un contrôle exercé par le monarque", "Aucun contrôle"], "Le Conseil constitutionnel examinait les lois avant leur promulgation.", 4),
    ("Que devient une loi déclarée contraire à la Constitution avant promulgation ?", "Elle ne peut pas être promulguée en l'état", ["Elle est promulguée avec un avertissement", "Elle est applicable seulement aux étrangers", "Elle est confiée au gouvernement"], "Le contrôle a priori bloque la promulgation du texte contraire.", 3),
    ("Quel pays connaît la plainte constitutionnelle individuelle devant la cour de Karlsruhe ?", "L'Allemagne", ["L'Italie", "Le Royaume-Uni", "Les États-Unis"], "La Verfassungsbeschwerde permet à un individu de saisir la cour constitutionnelle fédérale.", 5),
    ("Quelle juridiction siège à Karlsruhe ?", "La Cour constitutionnelle fédérale allemande", ["Le Conseil constitutionnel", "La Cour suprême des États-Unis", "La Cour de justice de l'Union européenne"], "Karlsruhe est le siège de la Cour constitutionnelle fédérale.", 5),
    ("Que caractérise un contrôle politique de constitutionnalité ?", "Il est confié à un organe non juridictionnel, de composition politique", ["Il est confié à tous les tribunaux de première instance", "Il est exercé après le procès", "Il est interdit par la Constitution"], "Il se distingue du contrôle juridictionnel par la nature de l'organe.", 3),
    ("Quelle différence y a-t-il entre contrôle de constitutionnalité et contrôle de conventionnalité ?", "Le premier compare la loi à la Constitution, le second à un traité", ["Le premier concerne les décrets, le second les lois", "Le premier est politique, le second toujours militaire", "Ils portent exactement sur le même objet"], "Le contrôle de conventionnalité vérifie la conformité au droit international.", 3),
    ("Qu'est-ce qui fonde logiquement le contrôle de constitutionnalité des lois ?", "La supériorité de la Constitution sur la loi", ["L'égalité de la Constitution et de la loi", "La supériorité de la loi sur la Constitution", "La souveraineté du juge pénal"], "La loi doit respecter la norme qui lui est supérieure.", 3),
    ("En droit français, quelle institution veille à la régularité de l'élection présidentielle et en proclame les résultats ?", "Le Conseil constitutionnel", ["Le Conseil d'État", "La Cour des comptes", "La Cour de cassation"], "Cette mission relève du Conseil constitutionnel.", 3),
    ("Qu'est-ce que le stare decisis ?", "La règle selon laquelle les juges suivent les précédents des juridictions supérieures", ["La règle du huis clos", "Le principe d'impartialité du juge", "La primauté de la loi sur la jurisprudence"], "Cette règle est caractéristique des systèmes de common law.", 4),
    ("Dans quel pays le contrôle de constitutionnalité a-t-il été théorisé par Kelsen pour la Constitution de 1920 ?", "L'Autriche", ["La France", "Les États-Unis", "Le Royaume-Uni"], "La Constitution autrichienne de 1920 a créé une cour constitutionnelle.", 4),
    ("Qu'est-ce que l'exception d'inconstitutionnalité soulevée devant un juge ?", "Un moyen par lequel une partie soutient qu'une loi applicable au litige est inconstitutionnelle", ["Une demande de révision de la Constitution", "Un recours contre le chef de l'État", "Une demande d'élection anticipée"], "Elle permet un contrôle à l'occasion d'un procès.", 3),
    ("Pourquoi l'indépendance de la juridiction constitutionnelle est-elle essentielle ?", "Pour qu'elle puisse contrôler les pouvoirs publics sans dépendre d'eux", ["Pour qu'elle puisse voter les lois", "Pour qu'elle remplace le chef de l'État", "Pour qu'elle gère le budget"], "Un contrôle efficace suppose l'indépendance à l'égard des organes contrôlés.", 3),
    ("Qu'est-ce qu'une cour constitutionnelle ?", "Une juridiction spécialisée chargée de contrôler la conformité des normes à la Constitution", ["Une cour d'appel chargée des affaires de famille", "Un tribunal de commerce", "Une cour des comptes"], "C'est l'organe central du modèle concentré.", 2),
    ("Dans un système de contrôle a priori, quand la saisine intervient-elle ?", "Avant la promulgation de la loi", ["Après plusieurs années d'application", "Seulement lors d'un procès pénal", "Après la publication au journal officiel"], "Le contrôle porte sur un texte voté mais non encore promulgué.", 2),
    ("Quelle expression désigne la possibilité pour un justiciable de soulever l'inconstitutionnalité d'une loi appliquée à son litige ?", "La voie d'exception", ["La voie d'action", "La motion de censure", "L'impeachment"], "La contestation passe par un procès en cours.", 3),
], cat="Contrôle de constitutionnalité", src=S)

# --- Parlement, bicamérisme, immunités
mcqr(C, "dc-m-parl", [
    ("Quelle est la fonction première du parlement ?", "Voter la loi et contrôler l'action du gouvernement", ["Juger les litiges civils", "Diriger l'administration", "Commander l'armée"], "Le parlement légifère et contrôle l'exécutif.", 1),
    ("Quel est l'un des arguments classiques en faveur du bicamérisme ?", "Permettre une seconde réflexion sur les textes et représenter les territoires", ["Accélérer toujours le vote des lois", "Supprimer le gouvernement", "Remplacer le référendum"], "Une seconde chambre modère et peut représenter d'autres intérêts.", 2),
    ("Qu'est-ce que le bicamérisme égalitaire ?", "Chaque chambre a des pouvoirs législatifs équivalents à ceux de l'autre", ["Une chambre est élue et l'autre désignée", "Chaque chambre compte autant de membres que l'autre", "Chaque chambre siège dans une ville différente"], "L'égalité s'entend des pouvoirs, non du nombre de membres.", 4),
    ("Qu'est-ce que le bicamérisme inégalitaire ?", "Une chambre, généralement la chambre basse, a le dernier mot en cas de désaccord", ["Une chambre ne siège jamais", "Le chef de l'État choisit le vote de chaque chambre", "Les chambres se réunissent à tour de rôle chaque année"], "Les pouvoirs des chambres ne sont pas équivalents.", 4),
    ("Quel mode de désignation est fréquent pour une chambre haute ?", "Un suffrage indirect ou une désignation tenant compte des territoires", ["Un concours administratif", "Un tirage au sort parmi tous les électeurs", "Une désignation par le chef de l'État dans tous les pays"], "Beaucoup de chambres hautes représentent des collectivités ou des États fédérés.", 3),
    ("Qu'est-ce que l'irresponsabilité parlementaire ?", "La protection absolue pour les opinions et les votes émis dans l'exercice du mandat", ["La protection contre toute arrestation pour toute infraction", "L'interdiction de poursuivre le parlementaire pour dettes civiles", "Le droit de ne pas voter"], "Elle garantit la liberté d'expression et de vote de l'élu.", 3),
    ("Que protège l'inviolabilité parlementaire ?", "Une protection temporaire contre certaines poursuites pénales, qui peut être levée", ["Une protection absolue et perpétuelle pour les propos tenus en séance", "Une immunité fiscale permanente", "Un privilège de ne jamais être jugé après le mandat"], "Elle est temporaire et susceptible d'être levée selon la procédure prévue.", 3),
    ("Laquelle de ces protections subsiste après la fin du mandat pour les actes qu'elle couvre ?", "L'irresponsabilité pour les opinions et votes émis dans l'exercice du mandat", ["L'inviolabilité, qui ne couvre que la durée du mandat", "L'immunité de juridiction pour tous les actes privés", "Le privilège de ne pas payer l'impôt"], "L'irresponsabilité est permanente ; l'inviolabilité est temporaire.", 5),
    ("Pourquoi accorde-t-on des immunités aux parlementaires ?", "Pour garantir leur liberté d'expression et d'action face à des pressions", ["Pour les placer au-dessus des lois en toute matière", "Pour leur verser un salaire", "Pour les dispenser de voter"], "L'immunité protège la fonction, non la personne.", 2),
    ("Quelle convention de 1961 règle les relations diplomatiques et les immunités des diplomates ?", "La Convention de Vienne sur les relations diplomatiques", ["La Convention de Genève sur les réfugiés", "La Convention de Montego Bay", "La Charte de Banjul"], "La Convention de Vienne de 1961 fixe le statut des missions diplomatiques.", 4),
    ("Qu'est-ce que l'incompatibilité des fonctions pour un parlementaire ?", "L'impossibilité d'exercer en même temps le mandat et certaines autres fonctions", ["L'impossibilité d'être candidat deux fois", "L'interdiction de voter pour soi-même", "L'obligation d'être célibataire"], "Elle protège l'indépendance de l'élu.", 3),
    ("Qu'est-ce que le cumul de mandats ?", "L'exercice simultané de plusieurs mandats électifs", ["Le vote de deux textes en une seule séance", "Le cumul de deux nationalités", "L'adoption d'une loi par deux chambres"], "Beaucoup de systèmes limitent ce cumul.", 2),
    ("Que désigne une question orale au gouvernement ?", "Un moyen par lequel un parlementaire interroge un ministre en séance", ["Un examen d'entrée à l'administration", "Un recours devant le juge constitutionnel", "Une motion de censure"], "Les questions sont un instrument de contrôle de l'exécutif.", 2),
    ("Dans un parlement bicaméral, que fait la navette parlementaire ?", "Elle fait circuler le texte entre les deux chambres jusqu'à un accord ou un dernier mot", ["Elle envoie le texte au juge", "Elle soumet le texte au référendum automatiquement", "Elle supprime l'une des chambres"], "Le va-et-vient assure l'examen par les deux chambres.", 3),
], cat="Parlement et immunités", src=S)

# --- Libertés publiques, droits fondamentaux
mcqr(C, "dc-m-lib", [
    ("Que sont les droits de la première génération ?", "Les droits civils et politiques", ["Les droits économiques et sociaux", "Les droits de solidarité", "Les droits des animaux"], "Ce sont les droits-libertés classiques.", 2),
    ("Que sont les droits de la deuxième génération ?", "Les droits économiques, sociaux et culturels", ["Les droits civils et politiques", "Les droits de solidarité", "Les droits procéduraux"], "Ils exigent généralement une action positive de l'État.", 2),
    ("Que sont les droits de la troisième génération ?", "Les droits de solidarité, comme la paix, le développement ou l'environnement", ["Les droits civils et politiques", "Les droits des travailleurs seulement", "Les droits procéduraux seulement"], "Ils sont souvent qualifiés de droits collectifs ou de solidarité.", 2),
    ("À quel juriste attribue-t-on l'expression « trois générations de droits de l'homme » ?", "Karel Vasak", ["René Cassin", "Léon Duguit", "Louis Joinet"], "Vasak a proposé cette classification dans les années 1970.", 5),
    ("Quel juriste français a participé à la rédaction de la Déclaration universelle de 1948 ?", "René Cassin", ["Maurice Hauriou", "Robert Badinter", "Jean Monnet"], "René Cassin est l'un des artisans de la DUDH.", 4),
    ("Quelle institution a adopté la Déclaration universelle des droits de l'homme ?", "L'Assemblée générale des Nations unies", ["Le Conseil de sécurité", "L'Union africaine", "La Cour internationale de justice"], "Elle l'a adoptée en 1948.", 3),
    ("Quelle est la valeur juridique originelle de la Déclaration universelle des droits de l'homme ?", "Celle d'une déclaration et non d'un traité, avec une grande autorité morale et politique", ["Celle d'un traité ratifié par tous les États en 1948", "Celle d'une loi mondiale votée par un parlement mondial", "Celle d'un arrêt de la Cour internationale de justice"], "Elle n'est pas un traité, mais son influence est considérable.", 4),
    ("Lequel de ces textes protège le droit à l'éducation ?", "Le Pacte relatif aux droits économiques, sociaux et culturels", ["Le Pacte relatif aux droits civils et politiques", "La Convention européenne des droits de l'homme", "Le Statut de Rome"], "Il consacre notamment les droits à l'éducation, au travail et à la santé.", 4),
    ("Quelle institution applique la Convention européenne des droits de l'homme ?", "La Cour européenne des droits de l'homme, à Strasbourg", ["La Cour de justice de l'Union africaine", "La Cour pénale internationale", "Le Conseil de sécurité"], "La Cour de Strasbourg contrôle le respect de la Convention.", 2),
    ("Où siège la Commission africaine des droits de l'homme et des peuples ?", "À Banjul", ["À Arusha", "À Genève", "À La Haye"], "La Commission siège à Banjul, en Gambie.", 4),
    ("Où siège la Cour africaine des droits de l'homme et des peuples ?", "À Arusha", ["À Banjul", "À Genève", "À La Haye"], "La Cour africaine siège à Arusha, en Tanzanie.", 4),
    ("Que prévoit la Charte africaine, outre des droits individuels ?", "Des droits des peuples et des devoirs de l'individu", ["Uniquement des devoirs de l'État", "Seulement des droits économiques", "La suppression de tout devoir"], "Elle est originale par sa dimension collective et ses devoirs.", 3),
    ("Où siège la Cour pénale internationale ?", "À La Haye", ["À Genève", "À Banjul", "À Strasbourg"], "La CPI siège à La Haye.", 2),
    ("Comment appelle-t-on les droits qui ne peuvent souffrir aucune dérogation même en cas de danger public ?", "Les droits indérogeables", ["Les droits-créances", "Les libertés collectives", "Les droits subjectifs privés"], "Ils forment le noyau dur des droits protégés.", 4),
    ("Lequel de ces droits est considéré comme indérogeable dans les grands textes de droits de l'homme ?", "L'interdiction de la torture", ["La liberté de réunion", "La liberté de circulation", "Le droit de grève"], "La torture est interdite même en situation d'urgence.", 3),
    ("Que signifie le principe de proportionnalité en matière de restriction des libertés ?", "La restriction doit être adaptée, nécessaire et proportionnée au but d'intérêt général poursuivi", ["Elle doit être égale pour tous les États", "Elle doit être identique à celle de l'année précédente", "Elle doit être décidée à l'unanimité"], "La mesure ne doit pas excéder ce qui est nécessaire.", 3),
    ("Quelle différence y a-t-il entre régime préventif et régime répressif d'une liberté ?", "Le préventif exige une autorisation préalable ; le répressif sanctionne les abus après coup", ["Le préventif sanctionne après coup ; le répressif exige une autorisation", "Le préventif est pénal, le répressif est civil", "Ils sont synonymes"], "Un régime répressif laisse la liberté s'exercer librement et punit seulement les abus.", 3),
    ("La liberté de la presse s'exerce sans censure préalable : quelle est sa contrepartie ?", "La responsabilité en cas d'abus, par exemple la diffamation", ["L'absence de toute responsabilité", "L'accord préalable du ministre", "La limitation à la seule presse écrite"], "La liberté s'accompagne de la responsabilité pour les abus.", 3),
    ("Quelle est la différence entre une liberté et un droit-créance ?", "La liberté exige surtout l'abstention de l'État ; le droit-créance exige une prestation de sa part", ["La liberté exige une prestation de l'État ; le droit-créance une abstention", "La liberté est collective, le droit-créance individuel", "Il n'y a pas de différence"], "Le droit à la santé ou à l'éducation suppose des moyens fournis par l'État.", 4),
    ("Que signifie la non-rétroactivité de la loi pénale plus sévère ?", "Une loi pénale plus sévère ne s'applique pas aux faits commis avant son entrée en vigueur", ["Une loi pénale s'applique toujours aux faits passés", "Une loi pénale ne peut pas être votée par le parlement", "Une loi pénale s'applique avant sa publication"], "C'est une garantie de la sécurité juridique et de la légalité pénale.", 3),
    ("Que garantit l'égalité devant la loi ?", "Que la loi soit la même pour tous, sans discrimination injustifiée", ["Que tous aient les mêmes revenus", "Que les mineurs aient les mêmes droits que les majeurs", "Que toute loi spéciale soit interdite"], "Elle n'interdit pas toute différence de traitement.", 2),
    ("Une différence de traitement est-elle toujours contraire au principe d'égalité ?", "Non, si elle repose sur une différence de situation ou un motif d'intérêt général lié à la loi", ["Oui, toujours", "Oui, sauf pour les étrangers", "Non, mais jamais en matière électorale"], "L'égalité n'exige pas un traitement identique de situations différentes.", 4),
    ("Que distingue les droits de l'homme des droits du citoyen ?", "Les premiers appartiennent à tout être humain ; les seconds sont liés à la qualité de citoyen", ["Les premiers ne concernent que les citoyens", "Les seconds appartiennent à tous les êtres humains", "Il n'y a aucune distinction"], "Les droits politiques sont réservés aux citoyens.", 3),
    ("Qu'est-ce que l'effet horizontal des droits fondamentaux ?", "Leur invocation dans les rapports entre particuliers", ["Leur invocation uniquement contre l'État", "Leur application hors du territoire", "Leur extension aux personnes morales étrangères"], "On l'oppose à l'effet vertical, dirigé contre les pouvoirs publics.", 5),
    ("Quel principe interdit de condamner une personne sans qu'elle ait pu se défendre ?", "Le respect des droits de la défense", ["Le secret du vote", "La liberté de réunion", "La libre administration"], "Il fait partie du procès équitable.", 2),
    ("Que désigne le principe du contradictoire dans un procès ?", "Chaque partie peut connaître et discuter les arguments et pièces de l'autre", ["Les juges ne siègent qu'à un seul", "Le jugement est secret", "Les parties ne peuvent pas parler"], "Il garantit un débat loyal.", 3),
    ("Quelle liberté permet de croire, de ne pas croire ou de changer de croyance ?", "La liberté de conscience et de religion", ["La liberté de circulation", "Le droit de grève", "La liberté d'entreprendre"], "Elle protège les convictions de chacun.", 2),
    ("Que protège la liberté syndicale ?", "Le droit de constituer des syndicats et d'y adhérer pour défendre des intérêts professionnels", ["Le droit de voter les lois", "Le droit de détenir un monopole d'entreprise", "Le droit de rendre la justice"], "Elle est à la fois individuelle et collective.", 2),
    ("Que protège le principe d'inviolabilité du domicile ?", "Contre les intrusions et perquisitions arbitraires", ["Contre tout contrôle fiscal", "Contre le paiement des loyers", "Contre les décisions de justice"], "Le domicile ne peut être pénétré que dans les cas et formes prévus par la loi.", 3),
    ("Que désigne le droit à un recours effectif ?", "Le droit de pouvoir saisir un juge pour faire respecter ses droits", ["Le droit de renoncer à tout procès", "Le droit d'élire les juges", "Le droit de modifier la loi"], "Il garantit l'accès à la justice.", 3),
], cat="Libertés publiques et droits fondamentaux", src=S)

# --- États d'exception
mcqr(C, "dc-m-exc", [
    ("Quel est le but d'un régime d'état d'urgence ?", "Faire face à un péril grave en renforçant temporairement les pouvoirs de l'autorité publique", ["Supprimer définitivement les libertés", "Remplacer la Constitution", "Dissoudre le pouvoir judiciaire"], "Il doit rester temporaire et encadré.", 2),
    ("Quelles conditions encadrent un régime d'exception dans un État de droit ?", "Une durée limitée, des mesures proportionnées et un contrôle", ["Une durée illimitée et aucun contrôle", "Une décision secrète sans publication", "L'interdiction de tout recours"], "Les garanties empêchent que l'exception devienne un régime d'arbitraire.", 3),
    ("Quelle est la différence de principe entre état d'urgence et état de siège ?", "L'urgence renforce la police civile ; le siège peut transférer des pouvoirs à l'autorité militaire", ["L'état de siège concerne les seuls étrangers", "L'état d'urgence supprime le parlement", "Ils sont strictement identiques"], "L'état de siège est classiquement lié à un transfert de la police à l'armée.", 4),
    ("Laquelle de ces mesures peut être prise dans le cadre d'un état d'urgence ?", "Un couvre-feu", ["La suppression des tribunaux", "L'abolition du Parlement", "La suspension de la Constitution pour dix ans"], "Il restreint temporairement la circulation.", 2),
    ("Pendant un état d'urgence, le juge conserve-t-il un rôle ?", "Oui, il contrôle la nécessité et la proportionnalité des mesures", ["Non, tout contrôle est suspendu", "Oui, mais seulement pour voter la loi", "Non, car le chef de l'État rend la justice"], "Les mesures d'exception restent soumises au droit.", 3),
    ("Que permet la théorie des circonstances exceptionnelles en droit administratif français ?", "À l'administration d'écarter la légalité ordinaire en cas de crise, sous contrôle du juge", ["Au juge de modifier la Constitution", "Au parlement de se dissoudre", "Aux particuliers de suspendre les lois"], "Cette jurisprudence adapte la légalité aux situations de crise.", 5),
    ("Quel grand texte de droits de l'homme permet aux États de déroger à certains droits en cas de danger public exceptionnel, sous conditions ?", "Le Pacte international relatif aux droits civils et politiques", ["Le Statut de Rome", "La Convention de Vienne sur les relations diplomatiques", "La Convention de Montego Bay"], "Il prévoit des dérogations limitées, avec des droits indérogeables.", 5),
    ("Quel traité de protection des droits de l'homme ne comporte pas de clause générale de dérogation en cas d'urgence ?", "La Charte africaine des droits de l'homme et des peuples", ["La Convention européenne des droits de l'homme", "La Convention américaine relative aux droits de l'homme", "Le Pacte relatif aux droits civils et politiques"], "Les trois autres traités prévoient une clause de dérogation, pas la Charte africaine.", 5),
    ("Pourquoi les mesures d'exception doivent-elles être temporaires ?", "Pour éviter que des pouvoirs renforcés deviennent permanents", ["Parce qu'elles sont toujours inconstitutionnelles", "Parce que seul le juge peut les décider", "Parce qu'elles ne s'appliquent qu'aux étrangers"], "La limitation dans le temps protège les libertés.", 2),
    ("Dans quelle situation un État peut-il envisager d'instaurer un régime d'exception ?", "Face à un péril grave menaçant l'ordre public ou la vie de la nation", ["Pour éviter une élection locale", "Pour réduire la durée d'un procès", "Pour modifier la monnaie"], "Le péril doit être grave et le régime prévu par la loi ou la Constitution.", 2),
    ("Que signifie qu'une mesure de police doit être nécessaire ?", "Elle doit répondre à un besoin réel de maintien de l'ordre et ne pas aller au-delà", ["Elle doit être secrète", "Elle doit être prise par le juge", "Elle doit toujours être pénale"], "La nécessité justifie l'atteinte à une liberté.", 3),
    ("Quelle est la finalité classique de la police administrative ?", "Prévenir les troubles à l'ordre public", ["Punir les infractions déjà commises", "Voter les lois pénales", "Juger les litiges civils"], "La police administrative est préventive ; la police judiciaire est répressive.", 3),
], cat="États d'exception", src=S)

# --- Décentralisation, collectivités, pouvoir local
mcqr(C, "dc-m-dec", [
    ("Qu'est-ce qui distingue une collectivité décentralisée d'un service déconcentré de l'État ?", "Elle a la personnalité morale et des élus ; le service déconcentré reste à l'État", ["Elle est dirigée par des fonctionnaires nommés", "Elle exerce la souveraineté", "Elle peut modifier la Constitution"], "La personnalité morale et l'élection caractérisent la décentralisation.", 3),
    ("Quel contrôle l'État exerce-t-il en principe sur les actes d'une collectivité décentralisée ?", "Un contrôle de légalité, c'est-à-dire de conformité au droit", ["Un contrôle hiérarchique complet", "Aucun contrôle", "Un contrôle de convenance sur chaque décision"], "La tutelle ne se substitue pas au pouvoir de décision local.", 4),
    ("Que signifie le principe de l'unité de l'État dans un système décentralisé ?", "Les collectivités ne peuvent pas porter atteinte à l'unité juridique et politique de l'État", ["Elles peuvent adopter leur propre Constitution", "Elles ont chacune leur propre armée", "Elles peuvent déclarer la guerre"], "La décentralisation s'exerce dans le cadre de l'État unitaire.", 3),
    ("Qu'est-ce que la déconcentration impose quant au pouvoir hiérarchique ?", "Les agents déconcentrés restent soumis à l'autorité de l'administration centrale", ["Ils deviennent indépendants de l'État", "Ils sont élus localement", "Ils forment une collectivité autonome"], "Elle rapproche l'administration sans créer de personne juridique distincte.", 3),
    ("Quelle est l'une des ressources principales des collectivités territoriales ?", "Des ressources fiscales et des dotations prévues par la loi", ["Un droit de créer leur monnaie", "Des taxes douanières internationales", "Des droits de vote au Conseil de sécurité"], "Leur financement est organisé par la loi dans le cadre de l'État.", 3),
    ("Au Cameroun, comment la Constitution désigne-t-elle les régions et les communes ?", "Des collectivités territoriales décentralisées", ["Des États fédérés", "Des circonscriptions militaires", "Des protectorats"], "Elles sont les collectivités décentralisées de la République.", 2),
    ("Quelle caractéristique du Cameroun correspond à un État unitaire décentralisé ?", "Un seul ordre juridique et des collectivités territoriales décentralisées", ["Des États fédérés dotés de constitutions propres", "Une association de deux États souverains", "Une monarchie héréditaire"], "L'unité de l'État s'accompagne d'une décentralisation.", 3),
    ("Quelle est la devise de la République du Cameroun ?", "Paix – Travail – Patrie", ["Unité – Progrès – Justice", "Union – Discipline – Travail", "Liberté – Égalité – Fraternité"], "La devise nationale du Cameroun est Paix – Travail – Patrie.", 1),
], cat="Décentralisation et institutions du Cameroun", src=S)

# --- Compléments : auteurs, distinctions fines
mcqr(C, "dc-m-fin", [
    ("Dans le parlementarisme moniste, devant qui le gouvernement est-il responsable ?", "Devant le seul parlement", ["Devant le parlement et devant le chef de l'État", "Devant le seul chef de l'État", "Devant les seuls juges"], "Le parlementarisme moniste ne connaît que la responsabilité devant le parlement.", 5),
    ("Quelle expression Kelsen utilise-t-il pour qualifier la cour constitutionnelle qui annule une loi ?", "Un « législateur négatif »", ["Un législateur positif qui réécrit la loi", "Un organe de pouvoir constituant originaire", "Un organe du gouvernement"], "Elle supprime des normes sans en créer librement de nouvelles.", 5),
    ("Qui a popularisé en France la critique du « gouvernement des juges » à propos de la Cour suprême américaine ?", "Édouard Lambert", ["Maurice Duverger", "Hans Kelsen", "Raymond Carré de Malberg"], "Lambert en a fait le titre de son ouvrage de 1921.", 5),
    ("Dans la théorie de Sieyès, quelle différence sépare le pouvoir constituant des pouvoirs constitués ?", "Le constituant précède et domine les pouvoirs constitués, créés par la Constitution", ["Les pouvoirs constitués créent le pouvoir constituant", "Ils sont de même rang et interchangeables", "Le pouvoir constituant est exercé par le juge ordinaire"], "Les organes de l'État sont créés et limités par la Constitution.", 5),
    ("Pourquoi la ratification d'un traité peut-elle exiger une révision constitutionnelle ?", "Un traité contraire à la Constitution ne peut être ratifié sans modifier celle-ci", ["La ratification est toujours un acte législatif ordinaire", "Les traités priment par principe sur la Constitution", "La ratification supprime le parlement"], "Dans de nombreux systèmes, un contrôle préalable est prévu.", 5),
    ("Comparée au quotient avec plus fort reste, quelles listes la méthode d'Hondt tend-elle à favoriser ?", "Les grandes listes", ["Les petites listes", "Les listes arrivées en dernière position", "Les candidats sans liste"], "La plus forte moyenne avantage généralement les listes qui ont le plus de voix.", 5),
    ("Quelle est la différence entre l'annulation et l'abrogation d'une loi ?", "L'annulation fait disparaître la loi rétroactivement ; l'abrogation seulement pour l'avenir", ["L'abrogation est rétroactive, l'annulation seulement pour l'avenir", "L'annulation est décidée par le parlement, l'abrogation par le juge", "L'annulation vise les décrets, l'abrogation les arrêtés"], "L'annulation produit un effet rétroactif ; l'abrogation, un effet pour le futur.", 5),
    ("Quel juriste allemand est associé à la théorie de l'autolimitation de l'État par le droit ?", "Georg Jellinek", ["Hans Kelsen", "Carl Schmitt", "Max Weber"], "Jellinek explique que l'État se soumet lui-même au droit qu'il crée.", 5),
    ("Quel juriste allemand a écrit : « Est souverain celui qui décide de l'état d'exception » ?", "Carl Schmitt", ["Hans Kelsen", "Max Weber", "Georg Jellinek"], "Cette formule est tirée de sa Théologie politique.", 5),
    ("Quelle distinction Benjamin Constant a-t-il proposée en 1819 ?", "Celle de la liberté des Anciens et de la liberté des Modernes", ["Celle de la souveraineté nationale et populaire", "Celle des Constitutions souples et rigides", "Celle du contrôle diffus et concentré"], "Il oppose la participation directe des Anciens à l'indépendance individuelle des Modernes.", 5),
    ("Quel sociologue a défini l'État comme détenant le monopole de la violence physique légitime ?", "Max Weber", ["Karl Marx", "Émile Durkheim", "Alexis de Tocqueville"], "Cette définition est classique en sociologie politique.", 4),
    ("Quel penseur a écrit De la démocratie en Amérique ?", "Alexis de Tocqueville", ["Montesquieu", "Benjamin Constant", "Max Weber"], "Tocqueville y analyse la démocratie américaine du XIXe siècle.", 3),
    ("Quel terme allemand désigne l'État de droit ?", "Rechtsstaat", ["Bundesstaat", "Sozialstaat", "Grundgesetz"], "Rechtsstaat désigne l'État soumis au droit, à ne pas confondre avec l'État fédéral (Bundesstaat).", 4),
    ("Que désigne la « rule of law » dans la tradition anglo-saxonne ?", "Le principe selon lequel nul n'est au-dessus de la loi et le pouvoir est soumis au droit", ["Le pouvoir absolu du Parlement sur les juges", "Le monopole de l'exécutif sur la loi", "L'obligation du suffrage universel"], "Elle est proche de l'idée d'État de droit.", 3),
    ("Qu'est-ce que le fait majoritaire ?", "L'existence d'une majorité parlementaire stable et cohérente qui soutient le gouvernement", ["La règle de vote à la majorité absolue", "L'élection d'un parti unique", "L'adoption d'une loi à l'unanimité"], "Il stabilise le gouvernement dans un régime parlementaire.", 4),
    ("Où siège le Conseil des droits de l'homme des Nations unies ?", "À Genève", ["À Banjul", "À La Haye", "À Strasbourg"], "Il siège à Genève, en Suisse.", 4),
    ("Cinq sièges sont à pourvoir ; les listes A, B et C obtiennent 50 000, 30 000 et 10 000 voix. Avec le quotient et le plus fort reste, quelle est la répartition ?", "A obtient 3 sièges, B 2, C aucun", ["A obtient 3 sièges, B 1, C 1", "A obtient 2 sièges, B 2, C 1", "A obtient 4 sièges, B 1, C aucun"], "Quotient 18 000 : A 2 (reste 14 000), B 1 (reste 12 000), C 0 ; les deux sièges restants vont aux plus forts restes, A et B.", 4),
], cat="Théorie de l'État et droit constitutionnel", src=S)

mcqr(C, "dc-m-add", [
    ("Que signifie qu'un référendum est consultatif ?", "Son résultat ne lie pas juridiquement les autorités", ["Il ne peut porter que sur la Constitution", "Il est réservé aux élus", "Il est organisé par le juge"], "Les autorités restent libres d'en tenir compte ou non.", 3),
    ("Que signifie qu'un référendum est décisionnel ?", "Son résultat s'impose juridiquement aux autorités", ["Il ne concerne que les collectivités locales", "Il est réservé aux partis politiques", "Il remplace l'élection présidentielle"], "Le texte soumis au vote est adopté ou rejeté selon le résultat.", 3),
    ("Que vise le parlementarisme rationalisé ?", "Encadrer le jeu parlementaire par la Constitution pour stabiliser le gouvernement", ["Supprimer le parlement", "Confier le pouvoir exécutif aux juges", "Interdire toute motion de censure en toutes circonstances"], "Il organise des procédures qui limitent l'instabilité gouvernementale.", 4),
    ("Qu'est-ce que le régime semi-présidentiel selon Duverger ?", "Un président élu directement, avec pouvoirs propres, et un gouvernement qui répond au parlement", ["Un régime où le président est désigné par le parlement et sans pouvoir", "Un régime où l'exécutif est collégial et sans chef", "Un régime où le monarque gouverne seul"], "Le pouvoir exécutif y est partagé entre le président et le gouvernement.", 3),
    ("Que désigne la centralisation ?", "La concentration de tous les pouvoirs de décision entre les mains des autorités centrales", ["Le transfert de compétences à des collectivités élues", "La répartition des pouvoirs entre deux chambres", "La séparation d'une partie du territoire"], "C'est l'opposé de la décentralisation.", 2),
    ("Comment définir la révision constitutionnelle ?", "La modification du texte constitutionnel selon la procédure que celui-ci prévoit", ["Le remplacement de la Constitution par la force", "Le contrôle d'une loi par le juge", "L'adoption d'une loi ordinaire"], "Elle relève du pouvoir constituant dérivé.", 2),
    ("Qu'est-ce que le principe de séparation des pouvoirs ?", "La répartition des fonctions législative, exécutive et judiciaire entre organes distincts", ["La concentration de toutes les fonctions dans un seul organe", "La séparation de l'État et des collectivités locales", "La séparation des partis et du gouvernement"], "Il vise à empêcher les abus de pouvoir.", 2),
], cat="Séparation des pouvoirs et régimes politiques", src=S)
