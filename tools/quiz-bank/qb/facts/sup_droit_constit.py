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
    ("la centralisation", "le regroupement de tous les pouvoirs de décision entre les mains des seules autorités centrales", 2),
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
    ("la révision constitutionnelle", "la modification d'une partie du texte constitutionnel selon la procédure qu'il prévoit", 2),
    ("le préambule d'une Constitution", "le texte placé en tête, qui énonce généralement des principes et des droits fondamentaux", 2),
    ("une loi organique", "une loi qui précise l'organisation des pouvoirs publics prévus par la Constitution", 3),
    ("le constitutionnalisme", "la doctrine selon laquelle le pouvoir doit être limité par une Constitution garantissant les droits", 4),
    ("la Constitution au sens matériel", "les règles relatives à l'organisation et à l'exercice du pouvoir, quelle que soit leur forme", 4),
    ("la Constitution au sens formel", "l'ensemble des règles contenues dans le document intitulé Constitution, quel que soit leur objet", 4),
], cat="La Constitution", src=S, fwd="Que désigne {a} ?", rev="Quelle notion correspond à la définition suivante : {b} ?")

# 4. Séparation et collaboration des pouvoirs
table(C, "dc-pouv", [
    ("la séparation des pouvoirs", "le principe qui répartit les fonctions législative, exécutive et judiciaire entre organes distincts", 2),
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
    ("le régime semi-présidentiel", "un régime à président élu au suffrage direct et à gouvernement responsable devant le parlement", 3),
    ("la monarchie constitutionnelle", "un régime où le monarque est chef de l'État mais où ses pouvoirs sont encadrés par la Constitution", 2),
    ("la monarchie absolue", "un régime où le roi concentre tous les pouvoirs sans limite constitutionnelle", 2),
    ("la république", "un régime où la fonction de chef de l'État n'est pas héréditaire mais élective", 2),
    ("la démocratie représentative", "un système où le peuple gouverne par l'intermédiaire de représentants élus", 2),
    ("la démocratie directe", "un système où les citoyens décident eux-mêmes des lois ou des grandes questions, sans intermédiaire", 2),
    ("la démocratie semi-directe", "un système représentatif complété par des procédures comme le référendum ou l'initiative populaire", 3),
    ("le parlementarisme rationalisé", "un parlementarisme dont les procédures sont encadrées pour assurer la stabilité du gouvernement", 4),
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
    ("le référendum consultatif", "un vote dont le résultat n'oblige pas juridiquement les autorités", 3),
    ("le référendum décisionnel", "un vote dont le résultat s'impose juridiquement aux autorités", 3),
    ("le veto populaire", "la possibilité pour les citoyens de demander un vote sur une loi déjà adoptée par le parlement", 5),
    ("la révocation populaire", "la procédure permettant aux électeurs de mettre fin avant terme au mandat d'un élu", 4),
    ("le mandat représentatif", "le principe selon lequel l'élu représente toute la nation et n'est pas lié par ses électeurs", 3),
    ("le mandat impératif", "le système où l'élu est tenu d'exécuter les instructions de ses électeurs et peut être révoqué", 3),
], cat="Partis, référendum et représentation", src=S, fwd="Comment définir {a} ?", rev="Quelle notion correspond à la définition suivante : {b} ?")

# 9. Contrôle de constitutionnalité
table(C, "dc-ctrl", [
    ("le contrôle de constitutionnalité", "la vérification de la conformité des lois et d'autres normes à la Constitution", 2),
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
    ("les libertés publiques", "l'ensemble des droits et libertés reconnus aux individus et garantis par l'État face au pouvoir", 2),
    ("les droits fondamentaux", "les droits jugés essentiels, protégés par la Constitution ou par des textes internationaux", 2),
    ("la liberté d'aller et venir", "la liberté de se déplacer sur le territoire et de le quitter", 2),
    ("la liberté d'association", "le droit de se grouper de façon durable pour poursuivre un but commun", 2),
    ("la liberté de réunion", "le droit de se rassembler de façon temporaire pour échanger ou exprimer des opinions", 3),
    ("la liberté de manifester", "le droit d'exprimer collectivement ses opinions sur la voie publique", 2),
    ("la liberté de conscience", "le droit de choisir ses croyances ou de n'en avoir aucune", 2),
    ("la liberté de la presse", "le droit de publier des informations et des opinions sans censure préalable", 2),
    ("le droit à la sûreté", "la protection contre les arrestations et les détentions arbitraires", 3),
    ("l'habeas corpus", "la garantie de demander à un juge de contrôler la légalité de sa détention", 4),
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
