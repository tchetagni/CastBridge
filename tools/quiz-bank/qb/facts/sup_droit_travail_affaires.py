"""L3 droit : droit du travail (principes) et droit commercial / droit OHADA général (principes).
Faits écrits par l'assistant : tous `review`. Aucun chiffre, aucune date, aucun numéro d'article : à faire relire par un juriste."""
from .sup_kit import *  # noqa: F401,F403

C = "l3-droit"
source("sup-trav", "Droit du travail : principes généraux", "cours de L3 droit", "Comparer avec un manuel de droit du travail (Code du travail camerounais, conventions de l'OIT) ; certaines règles diffèrent selon les pays.")
source("sup-comm", "Droit commercial général et droit OHADA : principes", "cours de L3 droit", "Comparer avec un manuel de droit commercial et les Actes uniformes de l'OHADA en vigueur (droit commercial général, sociétés, sûretés, procédures collectives, voies d'exécution, arbitrage, comptabilité).")

# =====================================================================================
# 1. DROIT DU TRAVAIL
# =====================================================================================
T = "Droit du travail"
table(C, "l3dt-notions", [
    ("le contrat de travail", "la convention par laquelle on travaille pour autrui, sous sa direction, moyennant rémunération", 1),
    ("le lien de subordination", "la soumission du travailleur aux ordres, au contrôle et aux sanctions de l'employeur", 2),
    ("l'employeur", "la personne qui engage un travailleur et le rémunère en contrepartie de son travail", 1),
    ("le salarié", "la personne qui fournit un travail sous la direction d'autrui en échange d'un salaire", 1),
    ("le salaire", "la contrepartie financière versée par l'employeur en échange du travail accompli", 1),
    ("le préavis", "le délai entre la notification de la rupture et la fin effective du contrat", 2),
    ("le licenciement", "la rupture du contrat de travail décidée par l'employeur", 1),
    ("la démission", "la rupture décidée par le salarié par une volonté claire et non équivoque", 2),
    ("le contrat à durée déterminée", "un contrat conclu pour une durée limitée ou pour la réalisation d'une tâche précise", 1),
    ("le contrat à durée indéterminée", "un contrat conclu sans fixation de terme", 1),
    ("la période d'essai", "le temps initial qui permet à chaque partie d'apprécier si l'emploi convient", 2),
    ("la convention collective", "un accord négocié entre syndicats et employeurs fixant les conditions de travail d'une branche", 2),
    ("le règlement intérieur", "un document de l'employeur fixant discipline, hygiène et sécurité dans l'entreprise", 2),
    ("le délégué du personnel", "un représentant élu des salariés auprès de l'employeur pour porter leurs réclamations", 2),
    ("le syndicat professionnel", "un groupement qui défend les intérêts professionnels de ses membres", 2),
    ("la grève", "l'arrêt collectif et concerté du travail pour appuyer des revendications", 1),
    ("l'inspecteur du travail", "un agent de l'État chargé de veiller à l'application de la législation du travail", 2),
    ("l'accident du travail", "l'accident survenu par le fait ou à l'occasion du travail", 2),
    ("la maladie professionnelle", "l'affection contractée du fait de l'exercice habituel d'une activité professionnelle", 3),
    ("le conflit collectif de travail", "le différend opposant un groupe de salariés à un employeur sur les conditions de travail", 3),
    ("le lock-out", "la fermeture de l'entreprise décidée par l'employeur à l'occasion d'un conflit collectif", 4),
    ("le certificat de travail", "le document remis en fin de contrat attestant l'emploi occupé et sa durée", 3),
], cat=T, src="sup-trav", fwd="Comment définit-on {a} ?", rev="Quelle notion de droit du travail correspond à cette définition : {b} ?")

table(C, "l3dt-rupture", [
    ("la faute grave", "une faute rendant impossible le maintien du salarié, même pendant le préavis", 4),
    ("la faute lourde", "une faute commise avec l'intention de nuire à l'employeur ou à l'entreprise", 5),
    ("le licenciement pour motif économique", "un licenciement dont la cause est étrangère à la personne du salarié", 4),
    ("le licenciement abusif", "un licenciement sans motif légitime ou irrégulier, ouvrant droit à réparation", 3),
    ("le licenciement nul", "un licenciement invalide car il viole une liberté fondamentale ou discrimine", 5),
    ("la force majeure", "un événement imprévisible, irrésistible et extérieur qui rend le contrat impossible à poursuivre", 4),
    ("la rupture d'un commun accord", "la fin du contrat résultant de la volonté concordante de l'employeur et du salarié", 3),
    ("la prise d'acte de la rupture", "la rupture par le salarié qui reproche à l'employeur des manquements graves", 5),
    ("le licenciement pour motif personnel", "un licenciement fondé sur un fait lié à la personne du salarié", 3),
    ("l'insuffisance professionnelle", "l'incapacité du salarié à bien accomplir son poste, sans faute volontaire", 4),
    ("le solde de tout compte", "le document qui récapitule les sommes versées au salarié lors de la fin du contrat", 3),
    ("la mise à pied conservatoire", "l'éloignement immédiat du salarié en attendant une décision, pour des faits graves", 5),
], cat=T, src="sup-trav", fwd="Que désigne {a} en droit du travail ?", rev="Quelle notion désigne : {b} ?")

table(C, "l3dt-sources", [
    ("la convention collective de branche", "un texte négocié qui s'applique à l'ensemble des entreprises d'un secteur d'activité couvert", 3),
    ("l'accord d'entreprise", "un texte négocié dans une seule entreprise entre l'employeur et les représentants des salariés", 3),
    ("l'usage d'entreprise", "une pratique constante, générale et fixe de l'employeur dont bénéficient les salariés", 5),
    ("l'engagement unilatéral de l'employeur", "une promesse faite par l'employeur de sa seule volonté, créatrice de droits pour les salariés", 5),
    ("la convention de l'OIT", "un traité du travail adopté par l'organisation internationale compétente, ouvert à ratification", 4),
    ("le contrat individuel de travail", "l'accord particulier entre un employeur et un salarié fixant leurs engagements", 2),
    ("le Code du travail", "le texte législatif qui rassemble les règles générales applicables aux relations de travail", 1),
    ("le principe de faveur", "la règle selon laquelle la norme la plus favorable au salarié s'applique en cas de concours", 5),
], cat=T, src="sup-trav", fwd="Qu'est-ce que {a} ?", rev="Quelle source du droit du travail est décrite ainsi : {b} ?")

table(C, "l3dt-acteurs", [
    ("l'Organisation internationale du travail", "une institution tripartite (États, employeurs, travailleurs) qui adopte des normes du travail", 3),
    ("le médecin du travail", "un médecin chargé de la santé des salariés et de la prévention des risques", 3),
    ("le délégué syndical", "un représentant désigné par un syndicat pour défendre ses intérêts dans l'entreprise", 4),
    ("l'organisation patronale", "un groupement d'employeurs qui défend leurs intérêts économiques et professionnels", 2),
    ("le conseil de prud'hommes", "une juridiction française paritaire jugeant les litiges individuels du travail", 4),
    ("la caisse de sécurité sociale", "un organisme qui verse des prestations sociales, dont celles liées aux accidents du travail", 3),
    ("le ministère du travail", "l'administration centrale chargée de la politique de l'emploi et du travail", 2),
    ("le comité d'hygiène et de sécurité", "une instance de l'entreprise consacrée à la prévention des risques professionnels", 4),
], cat=T, src="sup-trav", fwd="Quel est le rôle de {a} ?", rev="De quel acteur du monde du travail s'agit-il : {b} ?")

classify(C, "l3dt-pouvoirs", {
    "le pouvoir de direction de l'employeur": ["donner des instructions sur l'exécution des tâches", "répartir les missions entre les équipes", "organiser les horaires dans le respect de la loi"],
    "le pouvoir disciplinaire de l'employeur": ["prononcer un avertissement", "prononcer un blâme", "décider une mise à pied disciplinaire"],
    "une obligation de l'employeur": ["verser le salaire convenu", "fournir le travail prévu au contrat", "assurer la sécurité des salariés", "remettre un bulletin de paie"],
    "une obligation du salarié": ["exécuter personnellement sa prestation", "respecter un devoir de loyauté", "observer la discrétion sur les secrets de l'entreprise"],
}, cat=T, src="sup-trav", fwd="Dans la relation de travail, {item} relève de quoi ?", rev="Lequel de ces éléments relève de {group} ?", diff=3)

classify(C, "l3dt-fin", {
    "une démission": ["un salarié remet une lettre claire exprimant sa volonté de quitter l'entreprise", "un salarié informe librement son employeur qu'il met fin à son contrat pour partir ailleurs"],
    "un licenciement": ["l'employeur notifie la fin du contrat à un salarié pour insuffisance professionnelle", "l'employeur met fin au contrat d'un salarié à la suite d'une faute"],
    "une rupture d'un commun accord": ["employeur et salarié signent ensemble un document organisant la fin du contrat", "employeur et salarié conviennent ensemble de la date de départ et de ses conditions"],
    "l'arrivée du terme d'un contrat à durée déterminée": ["un contrat conclu jusqu'à une date fixée atteint cette date", "une mission temporaire prévue pour une tâche précise est achevée à l'échéance prévue"],
}, cat=T, src="sup-trav", fwd="Quelle qualification convient à cette situation : {item} ?", rev="Laquelle de ces situations constitue {group} ?", diff=3, expl="Cette situation correspond à {group}.")

classify(C, "l3dt-risque", {
    "un accident du travail": ["un ouvrier se blesse sur une machine pendant l'exécution de son travail", "une employée se blesse en déplacement professionnel demandé par son employeur", "un salarié est blessé à l'atelier par la chute d'un outil"],
    "un accident de trajet": ["un salarié se blesse sur le chemin normal entre son domicile et son lieu de travail", "une employée est renversée en rentrant directement du travail par l'itinéraire habituel"],
    "une maladie professionnelle": ["un travailleur développe une affection due à l'exposition habituelle à une substance de son poste", "un salarié contracte une maladie liée aux conditions répétées de son activité"],
    "un accident de la vie privée": ["un salarié se blesse en jouant au football le week-end", "une employée se foule la cheville pendant ses loisirs un dimanche"],
}, cat=T, src="sup-trav", fwd="Comment qualifier la situation suivante : {item} ?", rev="Laquelle de ces situations relève de {group} ?", diff=3, expl="Cette situation relève de {group}.")

mcq(C, "l3dt-mcq1", [
    ("Quels sont, de façon classique, les trois critères du contrat de travail ?", "Une prestation de travail, une rémunération et un lien de subordination", ["Un écrit, un témoin et un notaire", "Un capital, un bénéfice et une clientèle", "Un diplôme, un horaire et un uniforme", "Un apport, un partage des gains et une intention de s'associer"], "Ces trois éléments cumulés caractérisent le contrat de travail.", 1),
    ("Parmi les critères du contrat de travail, lequel le distingue d'un contrat d'entreprise ou de prestation de services indépendante ?", "Le lien de subordination", ["La prestation de travail", "L'existence d'un prix", "La capacité des parties", "L'accord de volontés"], "Le prestataire indépendant travaille sans être subordonné à son client.", 3),
    ("Le juge saisi doit qualifier une relation de travail. Sur quoi se fonde-t-il avant tout ?", "Sur les conditions de fait dans lesquelles l'activité est exercée", ["Sur le titre que les parties ont donné à leur contrat", "Sur la seule volonté déclarée par l'employeur", "Sur le montant du paiement", "Sur la nationalité du travailleur"], "Les parties ne peuvent écarter le statut de salarié par une simple étiquette : c'est le principe de primauté des faits.", 5),
    ("À quoi sert un « faisceau d'indices » en droit du travail ?", "Établir l'existence ou l'absence d'un lien de subordination", ["Calculer le montant du salaire", "Fixer la durée du préavis", "Désigner les délégués du personnel", "Mesurer le taux d'accident du travail"], "Le juge relève des indices concrets (ordres, horaires, contrôle, sanctions) pour déceler la subordination.", 4),
    ("Un travailleur choisit librement ses horaires, ses méthodes et ses clients, et facture ses prestations. Quelle qualification est a priori la plus probable ?", "Un travailleur indépendant", ["Un salarié en contrat à durée indéterminée", "Un salarié en période d'essai", "Un apprenti", "Un représentant du personnel"], "L'absence de subordination oriente vers l'indépendance.", 3),
    ("Un employeur reçoit des ordres précis, contrôle le travail et sanctionne les manquements d'une personne qu'il a qualifiée de « prestataire ». Que peut décider le juge ?", "Requalifier la relation en contrat de travail", ["Maintenir la qualification choisie par l'employeur", "Annuler toute relation entre les parties", "Transformer la relation en société", "Déclarer l'employeur incapable"], "La subordination effective l'emporte sur la dénomination.", 4),
    ("Dans un contrat de travail, quelle est l'obligation principale du salarié ?", "Fournir la prestation de travail convenue", ["Verser une caution à l'employeur", "Participer aux pertes de l'entreprise", "Garantir les dettes de l'employeur", "Recruter d'autres salariés"], "Le salarié met sa force de travail à la disposition de l'employeur.", 1),
    ("Quelle est l'obligation principale de l'employeur envers le salarié ?", "Payer la rémunération et fournir le travail convenu", ["Partager ses bénéfices dans tous les cas", "Garantir que l'entreprise ne ferme jamais", "Prendre en charge les dettes personnelles du salarié", "Accorder un logement"], "Rémunération et fourniture du travail sont les contreparties du travail du salarié.", 2),
    ("Le contrat de travail est-il, en principe, un contrat conclu intuitu personae à l'égard du salarié ?", "Oui, le salarié doit exécuter personnellement son travail", ["Non, le salarié peut toujours se faire remplacer par un tiers", "Non, il est conclu avec l'entreprise sans considération de la personne", "Oui, mais seulement pour les cadres", "Oui, mais uniquement après la période d'essai"], "Le salarié ne peut pas déléguer l'exécution de sa prestation.", 4),
    ("De façon générale, quelle forme de contrat est la forme normale et de référence de la relation de travail ?", "Le contrat à durée indéterminée", ["Le contrat à durée déterminée", "Le contrat d'intérim", "Le contrat de stage", "Le contrat de mission occasionnelle"], "Le contrat à durée déterminée reste en principe une exception encadrée.", 2),
    ("De façon générale, pourquoi le recours au contrat à durée déterminée est-il encadré ?", "Pour éviter qu'il serve à pourvoir durablement des emplois permanents", ["Parce qu'il est interdit de conclure des contrats limités dans le temps", "Parce qu'il coûte toujours plus cher au salarié", "Parce qu'il ne crée aucun droit au salaire", "Parce qu'il supprime tout pouvoir de direction"], "Le législateur veut limiter la précarité.", 3),
    ("En droit français, que risque l'employeur dont le contrat à durée déterminée ne respecte pas les conditions de forme et de fond ?", "Une requalification en contrat à durée indéterminée", ["La nullité de tous les contrats de l'entreprise", "La dissolution de l'entreprise", "Une peine de prison automatique", "La perte de la personnalité morale"], "La requalification protège le salarié contre l'usage abusif du contrat limité dans le temps.", 4),
    ("En principe, qu'entraîne l'arrivée du terme d'un contrat à durée déterminée ?", "La fin du contrat, sans licenciement ni préavis", ["Un licenciement obligatoire", "Un préavis à exécuter pendant une longue période", "Une transformation automatique en société", "La suspension du contrat"], "Le terme prévu met fin au contrat de plein droit.", 2),
    ("De façon générale, à quoi s'expose l'employeur qui rompt un CDD avant son terme hors des cas admis ?", "Des dommages-intérêts envers le salarié", ["Une obligation de réintégrer le salarié sans condition", "La dissolution de l'entreprise", "Une amende payée au salarié par le syndicat", "L'annulation du contrat de tous ses collègues"], "Les cas de rupture anticipée sont limités (faute grave, force majeure, accord) ; hors de ces cas, la responsabilité est engagée.", 4),
    ("Quel est l'objet de la période d'essai ?", "Permettre à chaque partie d'apprécier si le contrat lui convient", ["Éviter le versement de tout salaire", "Fixer définitivement la durée du préavis", "Désigner le délégué du personnel", "Transformer le contrat en CDD"], "Elle sert à vérifier les aptitudes du salarié et la convenance du poste.", 2),
    ("Pendant la période d'essai, le salarié est-il lié par un contrat de travail ?", "Oui, c'est un contrat de travail dont la rupture est assouplie", ["Non, il n'a aucun statut de salarié", "Non, il est un simple visiteur de l'entreprise", "Oui, mais sans droit au salaire", "Oui, mais sans lien de subordination"], "La période d'essai fait partie du contrat ; seule la facilité de rupture la distingue.", 3),
    ("Qu'est-ce que le pouvoir de direction de l'employeur ?", "Le pouvoir de donner des ordres et d'organiser le travail dans l'entreprise", ["Le pouvoir de légiférer pour tout le pays", "Le pouvoir de juger les litiges avec les salariés", "Le pouvoir de désigner les représentants syndicaux", "Le pouvoir de fixer le salaire minimum légal"], "Il est la manifestation de la subordination.", 2),
    ("Qu'est-ce que le pouvoir disciplinaire de l'employeur ?", "Le pouvoir de sanctionner les fautes commises par les salariés", ["Le pouvoir de choisir les lois applicables", "Le pouvoir d'arbitrer les conflits entre syndicats", "Le pouvoir de désigner les juges du travail", "Le pouvoir de créer des impôts"], "Les sanctions doivent respecter les règles et la proportionnalité.", 2),
    ("Quel principe doit respecter une sanction disciplinaire ?", "La proportionnalité à la faute reprochée", ["La sévérité maximale pour toute faute", "L'anonymat du juge disciplinaire", "L'accord préalable de tous les salariés", "L'absence totale de motif"], "La sanction doit être adaptée à la gravité de la faute.", 3),
    ("Peut-on, en principe, sanctionner deux fois le même fait fautif ?", "Non, un même fait ne peut donner lieu à deux sanctions", ["Oui, autant de fois que l'employeur le souhaite", "Oui, mais seulement par le syndicat", "Oui, si le salarié est cadre", "Oui, chaque jour de la semaine"], "Principe général : un fait fautif ne peut être sanctionné qu'une fois.", 4),
    ("Qui édicte le règlement intérieur d'une entreprise ?", "L'employeur, dans le cadre de son pouvoir de direction, sous contrôle", ["Le président de la République", "Le syndicat majoritaire seul", "La juridiction du travail", "L'inspecteur du travail à la place de l'employeur"], "L'employeur le rédige ; un contrôle administratif en vérifie la légalité.", 3),
    ("Que peut faire l'inspecteur du travail à l'égard d'un règlement intérieur contraire à la loi ?", "Exiger le retrait ou la modification des clauses illégales", ["Le déclarer valable malgré l'illégalité", "L'abroger en créant une loi", "Condamner pénalement le syndicat", "Dissoudre l'entreprise"], "Son contrôle porte sur la conformité du règlement aux textes.", 4),
    ("Que peut contenir un règlement intérieur ?", "Des règles de discipline, d'hygiène et de sécurité", ["Le niveau du salaire de chaque salarié", "Les statuts de l'entreprise", "Les décisions de justice rendues contre l'entreprise", "La liste des actionnaires"], "Il a un contenu limité à ces domaines.", 3),
    ("Que désigne la durée du travail, de façon générale ?", "Le temps pendant lequel le salarié est à la disposition de l'employeur", ["Le temps total passé dans la même entreprise sur la carrière", "Le temps de trajet entre le domicile et le travail", "Le temps compris dans le préavis uniquement", "La période d'essai"], "Le temps de travail effectif se compte à partir de la disposition de l'employeur.", 3),
    ("Que sont les heures supplémentaires ?", "Les heures effectuées au-delà de la durée normale du travail", ["Les heures effectuées en dessous de la durée normale", "Les heures de grève", "Les heures de formation syndicale", "Les heures de congé payé"], "Elles ouvrent généralement droit à une majoration.", 2),
    ("De façon générale, comment sont rémunérées les heures supplémentaires ?", "Avec une majoration par rapport au taux normal", ["À un taux inférieur au taux normal", "Elles ne sont jamais rémunérées", "Uniquement en nature", "Par le syndicat"], "La majoration compense la contrainte supplémentaire.", 3),
    ("À quoi sert le repos hebdomadaire ?", "À protéger la santé et la vie personnelle du travailleur par un temps de pause régulier", ["À éviter le paiement du salaire", "À mesurer la productivité", "À remplacer le préavis", "À organiser les élections des délégués"], "Les États imposent en général un repos périodique.", 1),
    ("Que désigne le salaire minimum interprofessionnel garanti (SMIG) ?", "Un salaire plancher en dessous duquel aucun travailleur ne peut être rémunéré", ["Le salaire moyen de l'entreprise", "Le salaire maximal autorisé par la loi", "Le salaire des fonctionnaires uniquement", "Une prime versée en fin d'année"], "Il garantit un minimum de rémunération.", 3),
    ("Que signifie le principe « à travail égal, salaire égal » ?", "Des salariés en situation comparable sont payés de la même façon, sauf raison objective", ["Chaque salarié de l'entreprise doit gagner exactement la même somme", "Le salaire dépend uniquement de l'ancienneté", "Le salaire doit baisser quand le travail augmente", "L'employeur fixe les salaires sans aucune limite"], "L'égalité s'apprécie à situation comparable, avec des différences possibles si elles sont justifiées.", 4),
    ("Quel document l'employeur remet-il périodiquement au salarié pour détailler sa rémunération ?", "Le bulletin de paie", ["Le certificat de travail", "Le reçu pour solde de tout compte", "Le règlement intérieur", "La lettre de démission"], "Le bulletin de paie détaille salaire et retenues.", 1),
    ("Parmi ces éléments, lequel est un avantage en nature ?", "Un logement fourni au salarié par l'employeur", ["Une somme versée chaque mois", "Une prime d'objectif versée en argent", "Un salaire de base", "Une commission en argent"], "Un avantage en nature est fourni sous une autre forme que de l'argent.", 3),
    ("Peut-on, en principe, retenir librement une partie du salaire pour sanctionner une faute ?", "Non, les retenues sur salaire sont strictement encadrées", ["Oui, l'employeur le décide seul", "Oui, si le syndicat le demande", "Oui, pour tout retard", "Oui, dès la première semaine"], "Les sanctions pécuniaires et les retenues sont limitées par la loi.", 4),
    ("Que garantit le privilège des salaires ?", "Un droit pour le salarié d'être payé de ses salaires avant les autres créanciers", ["L'exonération d'impôt de tous les salariés", "L'interdiction de licencier", "L'obligation pour l'employeur d'être commerçant", "Un salaire supérieur aux autres"], "Les salaires impayés sont payés en priorité lorsque l'employeur est en difficulté.", 4),
    ("Que sont les congés payés ?", "Des périodes de repos rémunérées accordées au salarié après un certain temps de travail", ["Des périodes de repos sans rémunération", "Des périodes de grève autorisée", "Une sanction disciplinaire", "Un préavis non effectué"], "Le salarié continue de toucher une rémunération pendant son congé.", 2),
    ("Le salarié peut-il, en principe, renoncer à son droit au congé annuel contre une somme d'argent ?", "Non, le congé est un droit de repos qu'on ne peut pas remplacer à l'avance par de l'argent", ["Oui, toujours, par accord écrit", "Oui, pour tous les salariés", "Oui, si l'employeur est un commerçant", "Oui, pendant la période d'essai"], "Le repos protège la santé du salarié ; l'indemnité compensatrice n'intervient qu'à la fin du contrat.", 5),
    ("Que signifie la suspension du contrat de travail ?", "L'arrêt temporaire des obligations principales (travail et salaire) sans rupture du contrat", ["La rupture définitive du contrat", "La transformation du contrat en CDD", "L'annulation rétroactive du contrat", "Le transfert du contrat à un autre employeur"], "Le contrat subsiste pendant la suspension.", 3),
    ("Laquelle de ces situations peut entraîner une suspension du contrat de travail ?", "Un arrêt de travail pour maladie", ["Une démission", "Un licenciement", "L'arrivée du terme d'un CDD", "Un départ à la retraite"], "La maladie suspend le contrat sans le rompre.", 2),
    ("Que protège, de façon générale, le congé de maternité ?", "La santé de la femme salariée et de l'enfant, ainsi que son emploi", ["Le droit de grève des salariées", "Le droit de licencier sans motif", "Le monopole de l'employeur sur les congés", "Le droit de ne pas être syndiquée"], "La salariée est protégée contre une rupture liée à sa maternité.", 2),
    ("Qu'est-ce que la mise à pied disciplinaire ?", "Une sanction consistant à écarter temporairement le salarié sans rémunération pour la période", ["La rupture définitive du contrat", "Une promotion", "Un congé payé supplémentaire", "Un transfert vers un autre employeur"], "C'est une sanction temporaire.", 3),
    ("Quelle différence existe-t-il entre mise à pied conservatoire et mise à pied disciplinaire ?", "La première est une mesure d'attente avant décision, la seconde est une sanction", ["La première est une sanction définitive, la seconde une mesure d'attente", "La première est décidée par le juge, la seconde par le syndicat", "Il n'y a aucune différence", "La première est réservée aux cadres, la seconde aux ouvriers"], "La mise à pied conservatoire n'est pas une sanction mais une mesure d'urgence.", 5),
    ("Quel est le principe de base de la démission ?", "Elle suppose une volonté claire et non équivoque du salarié de rompre le contrat", ["Elle se présume dès qu'un salarié est absent", "Elle peut être décidée par l'employeur", "Elle est toujours impossible pendant le préavis", "Elle exige l'accord du syndicat"], "La démission ne se présume pas.", 3),
    ("Qu'est-ce qu'une démission équivoque ?", "Une démission dont la volonté de rompre est douteuse ou liée à des reproches à l'employeur", ["Une démission rédigée à la main", "Une démission acceptée par l'employeur", "Une démission accompagnée d'un préavis", "Une démission prononcée devant témoin"], "Le juge peut la requalifier selon les circonstances.", 5),
    ("Sur quoi doit reposer un licenciement, de façon générale ?", "Un motif réel et sérieux", ["La seule volonté de l'employeur", "Un tirage au sort", "L'avis du syndicat obligatoirement", "La date de naissance du salarié"], "Un motif légitime est exigé, de façon générale, dans les systèmes protecteurs.", 3),
    ("En droit français, que doit faire l'employeur avant de licencier un salarié pour motif personnel ?", "Le convoquer à un entretien préalable", ["Demander l'accord du ministre", "Organiser un référendum dans l'entreprise", "Prévenir le tribunal de commerce", "Obtenir un diplôme spécial"], "L'entretien préalable permet au salarié de s'expliquer.", 4),
    ("Qu'est-ce que la lettre de licenciement ?", "Le document par lequel l'employeur notifie la rupture et en énonce les motifs", ["Le document par lequel le salarié démissionne", "Le contrat de travail initial", "Le reçu pour solde de tout compte", "Le règlement intérieur"], "Les motifs qui y figurent fixent les limites du litige.", 3),
    ("En droit français, qu'entraîne en principe un licenciement pour faute grave ?", "La perte du droit au préavis et à l'indemnité de licenciement", ["Une indemnité doublée", "Un préavis plus long", "Un transfert vers une autre entreprise", "L'obligation de reclasser le salarié"], "La gravité de la faute rend impossible tout maintien du contrat, même pendant le préavis.", 5),
    ("Quelle différence y a-t-il entre faute grave et faute lourde ?", "La faute lourde suppose l'intention de nuire à l'employeur, pas la faute grave", ["La faute lourde est involontaire, la faute grave intentionnelle", "La faute lourde est sanctionnée par un simple avertissement", "Il n'y a aucune différence", "La faute grave concerne les cadres seulement"], "L'intention de nuire est l'élément distinctif de la faute lourde.", 5),
    ("Un licenciement pour insuffisance professionnelle est-il, en principe, de nature disciplinaire ?", "Non, il n'est pas fondé sur une faute mais sur l'inaptitude à bien exécuter le travail", ["Oui, il sanctionne toujours une faute volontaire", "Oui, il suppose l'intention de nuire", "Oui, il équivaut à une démission", "Oui, il est décidé par le juge"], "L'insuffisance relève de la personne du salarié, non de la faute.", 5),
    ("En droit français, sur quoi repose un licenciement pour motif économique ?", "Repose sur des raisons non liées à la personne du salarié", ["Sanctionne une faute du salarié", "Est décidé par le syndicat", "Est toujours verbal", "Est interdit"], "Difficultés économiques, mutations technologiques, réorganisation : le motif est étranger à la personne.", 4),
    ("Que signifie l'obligation de reclassement qui peut précéder un licenciement économique ?", "L'employeur doit chercher à proposer au salarié un autre emploi avant de le licencier", ["Le salarié doit trouver lui-même un emploi dans le délai du préavis", "Le syndicat doit trouver un emploi au salarié", "L'État doit verser un salaire au salarié", "L'employeur doit vendre l'entreprise"], "Le licenciement est la solution de dernier recours.", 5),
    ("Quel est l'effet principal du caractère « abusif » d'un licenciement ?", "Le salarié peut obtenir des dommages-intérêts", ["Le salarié devient l'associé de l'entreprise", "L'employeur est dessaisi de la gestion", "Le syndicat obtient le contrôle de l'entreprise", "Le contrat devient un CDD"], "Le préjudice subi du fait d'une rupture injustifiée est indemnisé.", 3),
    ("Qu'est-ce que le préavis ?", "Un délai à respecter avant que la rupture produise ses effets, sauf exceptions", ["Une sanction pécuniaire", "Un congé payé", "Une période d'essai", "Un salaire de base"], "Il permet à chacun de s'organiser avant la fin du contrat.", 2),
    ("Dans quel cas l'employeur peut-il dispenser le salarié d'exécuter son préavis ?", "Quand il le décide en continuant en principe de lui payer le préavis", ["Jamais, la dispense est interdite", "Seulement quand le salarié est d'accord et sans rémunération", "Seulement avec l'autorisation du syndicat", "Seulement pendant une grève"], "La dispense ne prive pas le salarié de sa rémunération du préavis, sauf faute grave.", 5),
    ("Quelle est la conséquence d'un licenciement nul en droit français ?", "Le salarié peut en principe demander sa réintégration", ["Le contrat devient un CDD", "Le salarié perd tous ses droits", "L'entreprise est dissoute", "Le syndicat remplace l'employeur"], "La nullité efface le licenciement : la réintégration est possible.", 5),
    ("Pour quelle raison un licenciement fondé sur la participation légitime à une grève est-il en principe nul, en droit français ?", "Parce qu'il porte atteinte à un droit fondamental, sauf faute lourde du gréviste", ["Parce que la grève est obligatoire", "Parce que seul le juge peut licencier", "Parce que le gréviste est un représentant du personnel", "Parce que la grève ne concerne que les cadres"], "Le droit de grève est constitutionnellement protégé.", 5),
    ("Que fait le délégué du personnel ?", "Présente à l'employeur les réclamations individuelles ou collectives des salariés", ["Fixe les salaires dans l'entreprise", "Juge les litiges entre salariés et employeur", "Dirige le syndicat national", "Contrôle l'application du Code du travail dans toutes les entreprises"], "Il est l'interlocuteur des salariés auprès de l'employeur.", 2),
    ("Comment est désigné le délégué du personnel de façon générale ?", "Par élection parmi le personnel", ["Par nomination par l'employeur", "Par tirage au sort", "Par le ministre du travail", "Par l'inspecteur du travail"], "Il tire sa légitimité du vote des salariés.", 3),
    ("Quelle est la différence essentielle entre délégué du personnel et délégué syndical ?", "Le premier est élu par le personnel, le second est désigné par un syndicat", ["Le premier est désigné par un syndicat, le second est élu", "Le premier est un agent de l'État, le second un salarié", "Le premier représente l'employeur, le second les salariés", "Il n'y a aucune différence"], "Les deux représentent les salariés mais selon une légitimité différente.", 4),
    ("Pourquoi les représentants du personnel bénéficient-ils d'une protection spéciale contre le licenciement ?", "Pour qu'ils puissent exercer leur mandat sans craindre des représailles", ["Parce qu'ils sont fonctionnaires", "Parce qu'ils choisissent leur salaire", "Parce qu'ils sont choisis par l'employeur", "Parce qu'ils sont tous cadres"], "Leur protection garantit leur indépendance.", 4),
    ("En droit français, que nécessite généralement le licenciement d'un représentant du personnel protégé ?", "L'autorisation de l'inspecteur du travail", ["L'autorisation du procureur de la République", "L'accord du syndicat patronal", "Un vote du Parlement", "Un jugement du tribunal de commerce"], "L'autorité administrative contrôle que le licenciement n'est pas lié au mandat.", 5),
    ("Quel principe garantit la liberté syndicale ?", "Chacun est libre d'adhérer ou non à un syndicat et de s'y organiser", ["Chaque entreprise doit avoir un seul syndicat", "Seuls les cadres peuvent se syndiquer", "L'employeur choisit le syndicat de ses salariés", "L'adhésion syndicale est obligatoire"], "La liberté syndicale comprend aussi la liberté de ne pas adhérer.", 2),
    ("Un employeur peut-il, en principe, refuser d'embaucher une personne uniquement parce qu'elle est syndiquée ?", "Non, ce serait une discrimination syndicale", ["Oui, c'est son droit absolu", "Oui, si le poste est un poste de cadre", "Oui, si l'entreprise est une société", "Oui, tant que le salaire est correct"], "L'appartenance syndicale ne peut fonder une décision d'embauche.", 3),
    ("Qu'est-ce qu'une négociation collective ?", "Une discussion entre employeurs et représentants des salariés pour fixer des règles communes", ["Une réunion du conseil d'administration", "Une procédure de saisie", "Un entretien préalable de licenciement", "Un contrôle de l'inspection du travail"], "Elle débouche sur des conventions ou accords collectifs.", 2),
    ("Quelle est la portée d'une convention collective applicable à une entreprise ?", "Elle s'impose aux contrats individuels, qui ne peuvent y déroger en défaveur du salarié", ["Elle est facultative pour l'employeur", "Elle est supprimée par tout contrat individuel", "Elle s'applique seulement aux syndicats", "Elle ne vaut que pour les cadres"], "Les stipulations conventionnelles forment un socle minimal.", 4),
    ("Selon le principe de faveur, laquelle de deux normes concurrentes de même objet s'applique ?", "La plus favorable au salarié l'emporte traditionnellement", ["La plus ancienne l'emporte toujours", "La plus favorable à l'employeur l'emporte", "La plus courte l'emporte", "Aucune ne s'applique"], "C'est le principe de faveur ; son champ a été réduit dans certains droits.", 5),
    ("Qu'est-ce que l'extension d'une convention collective ?", "Une mesure publique qui rend la convention applicable à toute la branche", ["La prolongation de sa durée par le juge", "Le transfert de la convention à un autre pays", "L'ajout de nouveaux salariés dans une entreprise", "L'élargissement du droit de grève"], "L'extension dépasse les seuls signataires.", 5),
    ("Qu'est-ce qu'une grève licite, de façon générale ?", "Une cessation collective et concertée du travail pour des revendications professionnelles", ["Un arrêt individuel du travail sans motif", "Une occupation violente de l'entreprise", "Un départ non annoncé de tous les cadres", "Une démission collective"], "Elle suppose collectivité, concertation et revendications professionnelles.", 3),
    ("Quel est l'effet de la grève sur le contrat de travail des grévistes ?", "Il est suspendu pendant la grève, sauf faute lourde", ["Il est rompu", "Il est transformé en CDD", "Il est annulé", "Il est transféré à l'État"], "La grève suspend le contrat et ne le rompt pas.", 4),
    ("Que signifie le droit de grève, de façon générale ?", "Un droit reconnu aux salariés de cesser collectivement le travail dans le cadre fixé par la loi", ["Un droit illimité d'arrêter le travail à tout moment", "Un droit réservé à l'employeur", "Un droit d'occuper les locaux de l'entreprise de façon permanente", "Un droit de licencier les non-grévistes"], "Le droit de grève s'exerce dans le cadre de la loi.", 3),
    ("Qu'est-ce que le service minimum en cas de grève ?", "L'obligation d'assurer un minimum d'activité dans certains services essentiels", ["La suppression du droit de grève", "L'obligation de licencier les grévistes", "Un salaire minimal pendant la grève", "Une fermeture complète de l'entreprise"], "Il concilie la grève et la continuité des services essentiels.", 4),
    ("Quel est le rôle premier de l'inspecteur du travail ?", "Veiller à l'application de la législation du travail et conseiller employeurs et salariés", ["Juger les litiges individuels de travail", "Représenter les salariés dans les négociations", "Fixer les salaires de chaque entreprise", "Remplacer l'employeur"], "Il contrôle, conseille et constate les infractions.", 2),
    ("Comment l'inspecteur du travail constate-t-il les infractions ?", "Par des procès-verbaux", ["Par des jugements", "Par des arrêts de cassation", "Par des décisions du syndicat", "Par des délibérations d'assemblée générale"], "Le procès-verbal est transmis à l'autorité compétente.", 3),
    ("Quelle autorité tranche les litiges individuels nés du contrat de travail s'ils ne sont pas réglés à l'amiable ?", "La juridiction du travail compétente", ["L'inspecteur du travail seul", "Le syndicat du salarié", "Le commissaire aux comptes", "Le conseil de ministres de l'OHADA"], "La juridiction du travail règle les litiges individuels.", 2),
    ("En droit français, comment est composé le conseil de prud'hommes ?", "De conseillers élus ou désignés représentant à égalité employeurs et salariés", ["De magistrats professionnels uniquement", "De fonctionnaires de l'inspection du travail", "D'avocats seulement", "D'employeurs uniquement"], "La parité est la marque de cette juridiction.", 4),
    ("Qu'est-ce qu'un litige individuel de travail ?", "Un différend opposant un salarié à son employeur à propos du contrat", ["Un différend entre deux syndicats", "Un différend entre deux employeurs commerçants", "Un différend entre l'État et un salarié", "Un différend entre associés d'une société"], "Il se distingue du conflit collectif.", 2),
    ("Que prohibe, de façon générale, le principe de non-discrimination en matière d'emploi ?", "Les différences de traitement fondées sur des critères interdits comme le sexe ou l'origine", ["Toute différence de salaire quelle qu'en soit la cause", "Tout licenciement, même motivé", "Toute embauche par concours", "Toute évaluation professionnelle"], "Seules les différences fondées sur des critères prohibés sont visées.", 3),
    ("Un employeur refuse une promotion à une salariée au seul motif qu'elle est une femme. De quoi s'agit-il ?", "D'une discrimination en raison du sexe", ["D'une sanction disciplinaire légitime", "D'une mesure d'hygiène", "D'un conflit collectif", "D'une faute lourde du salarié"], "Le motif relève d'un critère prohibé.", 2),
    ("Une différence de traitement entre salariés est-elle toujours une discrimination ?", "Non, elle est licite si elle repose sur des raisons objectives et pertinentes", ["Oui, toujours", "Oui, si le salarié est syndiqué", "Oui, dès que le salaire diffère", "Oui, en cas de promotion"], "L'inégalité justifiée par un critère objectif n'est pas discriminatoire.", 4),
    ("Quelle est l'obligation générale de l'employeur en matière de sécurité ?", "Prendre les mesures nécessaires pour protéger la santé et la sécurité des travailleurs", ["Indemniser tout accident sans condition par des dons", "Interdire l'entrée de l'entreprise aux salariés", "Garantir l'absence de toute maladie", "Embaucher un médecin dans toute entreprise"], "L'employeur est tenu de prévenir les risques.", 2),
    ("Quel est le rôle de la médecine du travail ?", "Surveiller la santé des travailleurs et prévenir les risques liés au travail", ["Soigner tous les habitants du pays", "Remplacer l'inspection du travail", "Fixer le montant du salaire", "Désigner les délégués du personnel"], "Elle intervient en prévention, notamment par les visites médicales.", 2),
    ("En droit français, que permet le droit de retrait ?", "Au salarié de quitter son poste en cas de danger grave et imminent, sans sanction", ["À l'employeur de retirer sa confiance au salarié", "Au salarié de démissionner sans préavis", "Au syndicat de retirer sa représentation", "À l'inspecteur du travail de retirer la licence de l'entreprise"], "Le retrait doit être justifié par un danger réel et immédiat.", 5),
    ("Qu'est-ce que l'accident de trajet ?", "L'accident survenu sur le parcours normal entre le domicile et le lieu de travail", ["L'accident survenu pendant les congés", "L'accident survenu pendant une grève", "L'accident survenu à domicile", "L'accident survenu pendant un week-end de loisirs"], "Il est assimilé en général à un accident du travail pour la réparation.", 3),
    ("En droit français, que signifie la présomption d'imputabilité de l'accident du travail ?", "Un accident survenu au temps et au lieu du travail est présumé être un accident du travail", ["Le salarié doit toujours prouver la faute de l'employeur", "L'employeur est présumé innocent de toute faute", "L'accident est présumé fortuit", "Le salarié est présumé responsable"], "Cette présomption est favorable à la victime.", 5),
    ("Quel est le principe de la réparation des accidents du travail ?", "Une réparation forfaitaire fondée sur un risque professionnel pris en charge par un régime social", ["Une réparation intégrale payée par les collègues", "Une absence totale de réparation", "Une réparation payée par le syndicat", "Une réparation décidée par l'inspecteur du travail"], "Le salarié obtient une indemnisation sans avoir à prouver la faute de l'employeur.", 5),
    ("Quel organisme gère au Cameroun la prévoyance sociale des travailleurs, notamment les prestations liées aux accidents du travail ?", "La Caisse nationale de prévoyance sociale", ["La Caisse d'épargne postale", "La Cour des comptes", "Le Conseil économique et social", "La Direction générale des impôts"], "La CNPS est l'organisme de prévoyance sociale du secteur privé.", 3),
    ("Quelle est la finalité principale de l'Organisation internationale du travail ?", "Promouvoir la justice sociale et les droits au travail par des normes internationales", ["Financer les États déficitaires", "Réguler le commerce des marchandises", "Maintenir la paix par des forces armées", "Juger les crimes internationaux"], "Elle adopte des conventions et des recommandations.", 3),
    ("Que sont les recommandations de l'OIT ?", "Des instruments non soumis à ratification qui orientent l'action des États", ["Des traités obligatoires dès leur adoption pour tous", "Des jugements de la Cour pénale internationale", "Des décrets nationaux", "Des conventions collectives"], "Les conventions se ratifient, les recommandations guident.", 5),
    ("De façon générale, le transfert d'une entreprise à un nouvel employeur entraîne-t-il le maintien des contrats de travail ?", "Oui, en principe, les contrats en cours subsistent avec le nouvel employeur", ["Non, tous les contrats sont automatiquement rompus", "Non, seuls les cadres sont repris", "Oui, mais seulement pour une année", "Non, les contrats sont transférés à l'État"], "Principe protecteur de la continuité des contrats, notamment en droit français.", 5),
    ("Un employeur impose une modification de l'organisation des tâches du salarié sans changer les éléments essentiels du contrat. Quelle est, en principe, la situation ?", "Il s'agit d'un changement des conditions de travail relevant de son pouvoir de direction", ["Il s'agit d'une rupture", "Il s'agit d'un licenciement implicite", "Il s'agit d'une grève", "Il s'agit d'une démission"], "Le salarié ne peut en principe refuser un simple changement des conditions de travail.", 5),
    ("Qu'exige en principe une modification substantielle du contrat de travail, par exemple de la rémunération ?", "L'accord du salarié", ["La décision unilatérale de l'employeur", "L'avis du tribunal de commerce", "L'accord de l'inspecteur du travail", "Rien de particulier"], "Les éléments essentiels du contrat ne peuvent changer sans consentement.", 4),
    ("Que signifie l'obligation de loyauté du salarié ?", "Ne pas agir contre les intérêts légitimes de l'entreprise pendant le contrat", ["Rester dans l'entreprise toute sa vie", "Obéir à tout ordre, même illégal", "Dénoncer ses collègues", "Verser un cautionnement"], "La loyauté interdit notamment la concurrence déloyale pendant le contrat.", 3),
    ("En principe, que doit prévoir une clause de non-concurrence applicable après la fin du contrat pour être valable ?", "Une limitation dans le temps et l'espace, protégeant un intérêt légitime de l'entreprise", ["Une interdiction illimitée de travailler", "Un silence sur le périmètre", "Une renonciation à tout salaire", "Un engagement du salarié à ne jamais changer de métier"], "Elle ne peut priver le salarié de toute possibilité de travailler.", 5),
    ("Un employeur oblige les salariés à travailler en méconnaissant gravement les règles de sécurité. Quelle autorité peut intervenir pour constater les manquements ?", "L'inspection du travail", ["Le conseil de ministres de l'OHADA", "La CCJA", "L'ERSUMA", "Le commissaire aux comptes"], "L'inspection du travail contrôle l'hygiène et la sécurité.", 2),
    ("Que sont les conventions fondamentales de l'OIT, de façon générale ?", "Des conventions sur des droits essentiels comme la liberté syndicale", ["Des conventions sur le commerce international uniquement", "Des conventions sur la monnaie", "Des conventions sur les taxes", "Des conventions sur l'arbitrage"], "Elles garantissent des droits fondamentaux des travailleurs.", 4),
    ("Qu'est-ce que le travail forcé, de façon générale ?", "Un travail exigé sous la menace d'une peine et auquel la personne ne s'est pas offerte de plein gré", ["Un travail rémunéré au tarif minimum", "Un travail de nuit", "Un travail temporaire", "Un travail en équipe"], "Le travail forcé est interdit par les normes internationales.", 4),
    ("En quoi le contrat d'apprentissage se distingue-t-il d'un contrat de travail ordinaire ?", "Il combine l'exécution d'un travail et une formation professionnelle", ["Il exclut toute rémunération", "Il ne comporte aucun lien de subordination", "Il est conclu avec l'État uniquement", "Il est toujours à durée indéterminée"], "L'apprenti est un salarié en formation.", 4),
    ("De façon générale, un stagiaire d'une entreprise est-il automatiquement un salarié ?", "Non, sauf si la relation présente les critères d'un contrat de travail", ["Oui, toujours", "Oui, dès le premier jour", "Oui, s'il est majeur", "Oui, s'il est étranger"], "La qualification dépend des conditions réelles de la relation.", 4),
    ("Comment est qualifié en principe le bénévole qui travaille sans rémunération pour une association ?", "Hors du champ du contrat de travail, faute de rémunération", ["Un salarié de l'association", "Un employeur de l'association", "Un cadre dirigeant", "Un délégué syndical"], "Sans rémunération, un critère du contrat de travail manque.", 3),
    ("Le gérant non salarié ou le dirigeant mandataire social est-il, en principe, un salarié de la société ?", "Non, il exerce un mandat, sauf cumul distinct avec un véritable contrat de travail", ["Oui, automatiquement", "Oui, dès qu'il perçoit une rémunération", "Oui, pour tout mandat social", "Oui, uniquement dans les SARL"], "Le mandat social et le contrat de travail relèvent de régimes différents.", 5),
    ("Qu'est-ce que l'harmonisation du droit du travail à l'échelle régionale ?", "Un rapprochement des règles nationales par des textes communs", ["Une grève générale internationale", "Une fusion des syndicats nationaux", "Une centralisation des salaires", "Un transfert du pouvoir de direction à l'État"], "Elle vise des règles plus cohérentes entre pays.", 4),
    ("Que désigne la sous-traitance ou le prêt de main-d'œuvre lorsqu'il est utilisé pour contourner le droit du travail ?", "Une pratique que le juge peut sanctionner si elle vise à éluder les protections du salarié", ["Une pratique toujours obligatoire", "Une pratique réservée aux fonctionnaires", "Une pratique sans conséquence juridique", "Une pratique remplaçant le contrat de travail dans tous les cas"], "La fraude à la loi peut conduire à une requalification.", 5),
    ("Qu'est-ce que la transaction à la fin d'un contrat de travail ?", "Un accord par lequel les parties mettent fin à un litige moyennant des concessions réciproques", ["Un jugement du tribunal", "Un licenciement collectif", "Une grève de solidarité", "Une démission déguisée"], "La transaction met fin à la contestation née de la rupture.", 5),
    ("Qu'est-ce que l'obligation de discrétion du salarié ?", "Ne pas divulguer les informations confidentielles de l'entreprise", ["Ne jamais parler à ses collègues", "Ne jamais adhérer à un syndicat", "Accepter toute sanction sans discuter", "Cacher ses absences"], "Elle protège les secrets de l'entreprise.", 3),
    ("Qu'est-ce qu'un travailleur à temps partiel, de façon générale ?", "Un salarié dont la durée du travail est inférieure à la durée normale", ["Un salarié payé à la tâche seulement", "Un salarié d'un syndicat", "Un salarié saisonnier uniquement", "Un salarié en grève"], "Il est lié par un contrat de travail avec une durée réduite.", 2),
    ("Qu'est-ce qu'un travailleur saisonnier ?", "Un travailleur engagé pour des tâches liées à une saison ou à un cycle régulier d'activité", ["Un travailleur en congé permanent", "Un travailleur des services de l'État", "Un travailleur indépendant", "Un travailleur sans contrat"], "Le contrat saisonnier est adapté aux activités périodiques.", 3),
    ("Quel est le rôle d'un comité d'hygiène et de sécurité au sein d'une entreprise ?", "Contribuer à la prévention des risques et à l'amélioration des conditions de travail", ["Fixer le salaire de chaque salarié", "Juger les litiges entre associés", "Prononcer les licenciements", "Désigner le commissaire aux comptes"], "Il intervient dans la prévention des risques professionnels.", 3),
    ("En droit français, qu'est-ce que le comité social et économique ?", "L'instance de représentation du personnel dans l'entreprise", ["Un tribunal du travail", "Une caisse de sécurité sociale", "Une organisation patronale", "Un service de l'inspection du travail"], "Il regroupe, en droit français, des missions de représentation et de prévention.", 5),
    ("Qu'est-ce que le harcèlement moral au travail ?", "Des agissements répétés dégradant les conditions de travail et portant atteinte à la dignité", ["Une critique ponctuelle et objective du travail", "Un contrôle des horaires", "Une sanction disciplinaire proportionnée", "Un changement d'équipe décidé pour raison de service"], "La répétition et l'atteinte à la personne caractérisent le harcèlement.", 4),
    ("Un employeur peut-il licencier un salarié qui a refusé de subir des agissements de harcèlement ?", "Non, ce licenciement est en principe prohibé et peut être annulé", ["Oui, librement", "Oui, si le salarié est cadre", "Oui, si le syndicat l'accepte", "Oui, après l'entretien préalable seulement"], "Le salarié qui dénonce ou refuse un harcèlement est protégé.", 5),
    ("Dans un litige sur le motif d'un licenciement, le juge du travail peut-il apprécier si le motif invoqué est réel et sérieux ?", "Oui, il contrôle la réalité et le sérieux du motif", ["Non, il doit suivre l'avis de l'employeur", "Non, seul le syndicat le contrôle", "Non, seul le ministre peut le faire", "Oui, mais seulement pour les cadres"], "Le contrôle du juge est un élément central de la protection.", 4),
], cat=T, src="sup-trav")

# =====================================================================================
# 2. DROIT COMMERCIAL ET DROIT OHADA GÉNÉRAL
# =====================================================================================
K = "Droit commercial et OHADA"
table(C, "l3dc-ohada", [
    ("le Traité de l'OHADA", "l'acte international qui crée l'organisation et fixe ses institutions", 2),
    ("les Actes uniformes", "les textes communs qui harmonisent le droit des affaires des États parties", 2),
    ("le Conseil des ministres", "l'organe réunissant les ministres de la justice et des finances, qui adopte les Actes uniformes", 3),
    ("la Cour commune de justice et d'arbitrage", "la juridiction commune qui assure l'interprétation des Actes uniformes et gère l'arbitrage", 3),
    ("l'ERSUMA", "l'école chargée de la formation et du perfectionnement des magistrats et auxiliaires de justice", 3),
    ("le Secrétariat permanent", "l'organe exécutif qui assure le suivi de l'organisation et prépare les projets d'Actes uniformes", 4),
    ("la Conférence des chefs d'État et de gouvernement", "l'organe politique composé des chefs d'État et de gouvernement des États parties", 4),
], cat=K, src="sup-comm", region="AF", fwd="Quel est le rôle de {a} dans l'OHADA ?", rev="Quelle institution ou quel texte de l'OHADA correspond à ceci : {b} ?")

table(C, "l3dc-notions", [
    ("le commerçant", "la personne qui accomplit des actes de commerce et en fait sa profession habituelle", 1),
    ("l'acte de commerce", "l'opération soumise au droit commercial par sa nature, sa forme ou son accessoire", 3),
    ("le fonds de commerce", "l'ensemble des moyens qui permettent au commerçant d'attirer et de conserver une clientèle", 3),
    ("la clientèle", "l'ensemble des personnes qui s'adressent habituellement à un commerce", 2),
    ("le nom commercial", "la dénomination sous laquelle le commerçant exerce son activité", 2),
    ("l'enseigne", "le signe extérieur qui identifie l'établissement et le signale au public", 2),
    ("le registre du commerce et du crédit mobilier", "le registre de publicité où s'immatriculent les commerçants et s'inscrivent certaines sûretés", 3),
    ("l'entreprenant", "l'entrepreneur individuel qui exerce une petite activité sous un régime simplifié", 4),
    ("la concurrence déloyale", "l'emploi de procédés contraires aux usages honnêtes pour détourner la clientèle", 3),
    ("la cession de fonds de commerce", "le contrat par lequel le propriétaire transfère son fonds à un acquéreur", 3),
    ("la location-gérance", "le contrat par lequel le propriétaire confie l'exploitation de son fonds à un gérant", 4),
    ("le bail commercial", "le contrat de location d'un local où s'exerce une activité commerciale", 2),
    ("la capacité commerciale", "l'aptitude à accomplir des actes de commerce et à exercer le commerce à titre professionnel", 3),
], cat=K, src="sup-comm", region="AF", fwd="Comment définit-on {a} ?", rev="Quelle notion de droit commercial correspond à ceci : {b} ?")

table(C, "l3dc-interm", [
    ("le courtier", "l'intermédiaire qui met des personnes en relation sans être partie au contrat", 3),
    ("le commissionnaire", "l'intermédiaire qui agit en son nom propre pour le compte d'un commettant", 4),
    ("l'agent commercial", "le mandataire indépendant chargé de façon durable de négocier des contrats pour autrui", 4),
    ("le commettant", "la personne pour le compte de laquelle le commissionnaire conclut l'opération", 4),
    ("le transitaire", "l'intermédiaire qui accomplit les formalités de transit et d'expédition des marchandises", 4),
], cat=K, src="sup-comm", region="AF", fwd="Qui est {a} en droit commercial ?", rev="Quel professionnel ou quelle partie correspond à ceci : {b} ?")

table(C, "l3dc-formes", [
    ("la société en nom collectif", "les associés y sont tous commerçants et répondent indéfiniment et solidairement des dettes", 3),
    ("la société en commandite simple", "des commandités indéfiniment responsables y côtoient des commanditaires limités à leurs apports", 4),
    ("la société à responsabilité limitée", "son capital est divisé en parts sociales et les associés sont limités à leurs apports", 3),
    ("la société anonyme", "son capital est divisé en actions négociables et les actionnaires sont limités à leurs apports", 3),
    ("le groupement d'intérêt économique", "il facilite l'activité de ses membres sans chercher à réaliser de bénéfices pour lui-même", 4),
    ("la société en participation", "société sans personnalité morale, non immatriculée et non soumise à publicité", 4),
], cat=K, src="sup-comm", region="AF", fwd="Quel est le trait caractéristique de {a} ?", rev="Quelle forme de groupement présente ce trait : {b} ?", expl="{A} : {B}.")

table(C, "l3dc-societe", [
    ("l'apport en numéraire", "la somme d'argent mise à la disposition de la société par un associé", 1),
    ("l'apport en nature", "un bien autre que de l'argent, comme du matériel ou un immeuble, apporté à la société", 2),
    ("l'apport en industrie", "le travail, le savoir-faire ou les services qu'un associé met à la disposition de la société", 3),
    ("le capital social", "la valeur des apports en numéraire et en nature faits à la société par les associés", 4),
    ("l'action", "le titre représentatif d'une part du capital d'une société anonyme", 2),
    ("la part sociale", "le titre représentatif d'une part du capital dans une société à responsabilité limitée", 2),
    ("le dividende", "la part de bénéfice distribuée à chaque associé ou actionnaire", 2),
    ("l'affectio societatis", "la volonté des associés de collaborer sur un pied d'égalité à l'entreprise commune", 4),
    ("l'objet social", "l'activité que la société se propose d'exercer", 2),
    ("le siège social", "le lieu où la société est domiciliée et où se trouve sa direction", 2),
    ("la personnalité morale de la société", "son aptitude à être titulaire de droits et d'obligations distincts de ceux des associés", 3),
    ("le gérant", "le dirigeant chargé de la gestion de la société à responsabilité limitée", 2),
    ("l'assemblée générale", "la réunion des associés ou actionnaires pour prendre les décisions collectives", 2),
    ("le commissaire aux comptes", "le contrôleur indépendant chargé de certifier la régularité et la sincérité des comptes", 3),
    ("le conseil d'administration", "l'organe collégial qui dirige la société anonyme et détermine ses orientations", 3),
    ("le commanditaire", "l'associé dont la responsabilité est limitée à son apport dans une société en commandite", 3),
    ("le commandité", "l'associé tenu indéfiniment et solidairement des dettes dans une société en commandite", 3),
    ("la dissolution de la société", "l'événement ou la décision qui met fin à l'existence de la société et ouvre la liquidation", 4),
    ("la liquidation de la société", "l'ensemble des opérations qui règlent les dettes et répartissent l'actif restant", 4),
], cat=K, src="sup-comm", region="AF", fwd="Qu'est-ce que {a} ?", rev="Quelle notion du droit des sociétés correspond à ceci : {b} ?")

table(C, "l3dc-effets", [
    ("la lettre de change", "l'écrit par lequel le tireur ordonne à un tiré de payer une somme à un bénéficiaire", 2),
    ("le billet à ordre", "l'écrit par lequel le souscripteur promet de payer une somme à un bénéficiaire", 2),
    ("le chèque", "l'écrit par lequel le tireur ordonne à sa banque de payer une somme à vue", 2),
    ("le tireur", "la personne qui émet l'ordre de paiement de la lettre de change ou du chèque", 2),
    ("le tiré", "la personne à qui l'ordre de paiement est adressé", 3),
    ("le bénéficiaire", "la personne au profit de laquelle le paiement doit être fait", 2),
    ("le souscripteur", "la personne qui émet un billet à ordre et s'engage à le payer", 3),
    ("l'endossement", "l'acte par lequel le porteur transmet l'effet à un autre au moyen d'une mention signée", 3),
    ("l'acceptation", "l'engagement du tiré de payer la lettre de change à l'échéance", 3),
    ("l'aval", "la garantie du paiement d'un effet de commerce donnée par une personne qui s'y engage", 4),
    ("le protêt", "l'acte qui constate officiellement le défaut d'acceptation ou de paiement d'un effet", 4),
    ("la provision", "la créance du tireur sur le tiré qui permet de payer l'effet", 4),
    ("l'échéance", "la date à laquelle l'effet de commerce doit être payé", 2),
    ("le porteur", "le détenteur légitime de l'effet au moment de la présentation", 3),
], cat=K, src="sup-comm", region="AF", fwd="Qu'est-ce que {a} en matière d'effets de commerce ?", rev="Quelle notion correspond à ceci : {b} ?")

table(C, "l3dc-suretes", [
    ("le cautionnement", "le contrat par lequel la caution s'engage à payer si le débiteur ne paie pas", 2),
    ("la caution", "la personne qui s'engage envers le créancier à exécuter l'obligation d'autrui", 2),
    ("la garantie autonome", "l'engagement de payer indépendamment de l'obligation garantie", 4),
    ("la lettre d'intention", "l'engagement de faire ou de ne pas faire pour soutenir un débiteur", 4),
    ("le gage", "la sûreté réelle portant sur des biens meubles au profit du créancier", 3),
    ("le nantissement", "la sûreté réelle portant sur des meubles incorporels, comme une créance", 3),
    ("l'hypothèque", "la sûreté réelle portant sur un immeuble, sans dépossession du propriétaire", 3),
    ("le privilège", "le droit de préférence que la loi accorde à certains créanciers selon la nature de leur créance", 4),
    ("le droit de rétention", "le droit de garder un bien du débiteur jusqu'au paiement de sa dette", 4),
    ("le bénéfice de discussion", "le droit de la caution d'exiger que le créancier poursuive d'abord le débiteur", 5),
    ("le créancier chirographaire", "le créancier qui n'a ni sûreté ni privilège particulier", 4),
], cat=K, src="sup-comm", region="AF", fwd="Qu'est-ce que {a} ?", rev="Quelle notion du droit des sûretés correspond à ceci : {b} ?")

table(C, "l3dc-collectives", [
    ("la cessation des paiements", "l'impossibilité de payer le passif exigible avec l'actif disponible", 3),
    ("le règlement préventif", "la procédure destinée à éviter la cessation des paiements et à redresser l'entreprise", 3),
    ("la conciliation", "la procédure amiable où un conciliateur aide le débiteur à s'entendre avec ses créanciers", 3),
    ("le redressement judiciaire", "la procédure visant à permettre la poursuite de l'activité et l'apurement du passif", 3),
    ("la liquidation des biens", "la procédure de réalisation de l'actif du débiteur pour payer les créanciers", 3),
    ("le concordat", "l'accord entre le débiteur et ses créanciers qui organise le règlement des dettes", 4),
    ("la masse des créanciers", "le groupement des créanciers dont les droits sont nés avant le jugement d'ouverture", 4),
    ("le passif exigible", "l'ensemble des dettes échues dont le paiement peut être exigé", 4),
    ("l'actif disponible", "l'ensemble des biens immédiatement mobilisables pour payer les dettes", 4),
    ("la suspension des poursuites individuelles", "l'interdiction faite aux créanciers de poursuivre seuls le débiteur après le jugement", 4),
], cat=K, src="sup-comm", region="AF", fwd="Qu'est-ce que {a} ?", rev="Quelle notion de droit des procédures collectives correspond à ceci : {b} ?")

table(C, "l3dc-execution", [
    ("le titre exécutoire", "l'acte qui permet à son titulaire de procéder à l'exécution forcée", 2),
    ("l'huissier de justice", "l'officier ministériel chargé de signifier les actes et de procéder aux saisies", 2),
    ("la saisie conservatoire", "la mesure qui rend un bien indisponible pour préserver les droits du créancier", 3),
    ("la saisie-vente", "la saisie de biens meubles corporels en vue de leur vente pour payer le créancier", 3),
    ("la saisie-attribution", "la saisie des sommes que des tiers doivent au débiteur, attribuées au saisissant", 4),
    ("la saisie immobilière", "la procédure de vente forcée d'un immeuble du débiteur", 3),
    ("la saisie des rémunérations", "la saisie d'une fraction du salaire du débiteur", 3),
    ("l'injonction de payer", "la procédure simplifiée de recouvrement d'une créance certaine, liquide et exigible", 4),
    ("le commandement de payer", "l'acte qui somme le débiteur de payer avant la saisie", 3),
    ("le tiers saisi", "la personne qui détient des sommes ou des biens appartenant au débiteur saisi", 4),
    ("l'insaisissabilité", "le caractère d'un bien qui ne peut être saisi par les créanciers", 3),
    ("l'immunité d'exécution", "la protection des biens des personnes publiques contre l'exécution forcée", 5),
    ("le saisissant", "le créancier qui pratique la saisie", 2),
    ("le saisi", "le débiteur dont les biens font l'objet de la saisie", 2),
], cat=K, src="sup-comm", region="AF", fwd="Qu'est-ce que {a} ?", rev="Quelle notion relative à l'exécution forcée correspond à ceci : {b} ?")

table(C, "l3dc-arbitrage", [
    ("l'arbitrage", "le mode de règlement des litiges où des particuliers choisis jugent à la place du juge étatique", 2),
    ("la clause compromissoire", "la clause d'un contrat prévoyant l'arbitrage des litiges futurs", 3),
    ("le compromis d'arbitrage", "l'accord conclu après la naissance du litige pour le soumettre à l'arbitrage", 3),
    ("la convention d'arbitrage", "l'accord de soumettre un litige à l'arbitrage, sous forme de clause ou de compromis", 3),
    ("la sentence arbitrale", "la décision rendue par le tribunal arbitral", 2),
    ("l'arbitre", "le particulier investi par les parties du pouvoir de trancher le litige", 2),
    ("le tribunal arbitral", "la formation d'un ou plusieurs arbitres chargée de juger le litige", 2),
    ("l'exequatur", "la décision du juge étatique qui donne force exécutoire à la sentence arbitrale", 4),
    ("le recours en annulation", "la voie de recours ouverte contre la sentence pour des cas limités", 4),
    ("le principe de compétence-compétence", "le pouvoir de l'arbitre de statuer sur sa propre compétence", 5),
    ("la médiation", "le mode amiable où un tiers aide les parties à trouver elles-mêmes une solution", 3),
], cat=K, src="sup-comm", region="AF", fwd="Qu'est-ce que {a} ?", rev="Quelle notion de l'arbitrage et des modes alternatifs correspond à ceci : {b} ?")

table(C, "l3dc-compta", [
    ("le bilan", "l'état qui décrit l'actif et le passif de l'entreprise à une date donnée", 1),
    ("le compte de résultat", "l'état qui récapitule les charges et les produits de l'exercice", 2),
    ("l'actif", "l'ensemble des biens et des créances que possède l'entreprise", 2),
    ("le passif", "l'ensemble des ressources de l'entreprise : capitaux propres et dettes", 3),
    ("les capitaux propres", "les ressources apportées ou laissées à la disposition de l'entreprise par ses propriétaires", 3),
    ("l'immobilisation", "le bien destiné à rester durablement dans l'entreprise", 2),
    ("l'amortissement", "la constatation de la perte de valeur d'une immobilisation due à l'usage ou au temps", 3),
    ("la provision pour risques", "la charge probable, d'échéance ou de montant incertain, constatée par prudence", 4),
    ("la charge", "un coût supporté par l'entreprise au cours de l'exercice", 2),
    ("le produit", "un revenu ou un gain réalisé par l'entreprise au cours de l'exercice", 2),
    ("l'exercice comptable", "la période au terme de laquelle les comptes annuels sont arrêtés", 2),
    ("la comptabilité en partie double", "le principe où chaque opération est inscrite au débit d'un compte et au crédit d'un autre", 3),
    ("le plan comptable", "la liste normalisée et codifiée des comptes utilisables", 2),
    ("le SYSCOHADA", "le système comptable commun aux États parties à l'OHADA", 2),
    ("le principe de prudence", "la règle de ne pas anticiper les gains probables tout en tenant compte des risques", 4),
    ("le principe de continuité d'exploitation", "l'hypothèse que l'entreprise poursuivra son activité dans l'avenir prévisible", 4),
    ("l'annexe", "l'état qui complète et commente les informations du bilan et du compte de résultat", 3),
    ("le tableau des flux de trésorerie", "l'état qui retrace les entrées et les sorties de liquidités de l'exercice", 4),
    ("le journal", "le livre où les opérations sont enregistrées dans l'ordre chronologique", 2),
    ("le grand livre", "le livre qui regroupe les écritures par compte", 3),
], cat="Comptabilité (niveau général)", src="sup-comm", region="AF", fwd="Qu'est-ce que {a} en comptabilité ?", rev="Quelle notion comptable correspond à ceci : {b} ?")

classify(C, "l3dc-actes", {
    "un acte de commerce par nature": ["l'achat de marchandises pour les revendre", "une opération de banque", "une opération de transport de marchandises à titre professionnel", "une opération de courtage"],
    "un acte de commerce par accessoire": ["l'achat d'un camion par un commerçant pour livrer ses clients", "l'emprunt contracté par un commerçant pour financer son stock", "l'embauche d'un vendeur par un commerçant pour son magasin"],
    "un acte de commerce par la forme": ["la lettre de change"],
    "un acte civil": ["l'achat de pain par un particulier pour sa famille", "la vente de sa voiture personnelle par un salarié", "l'achat d'un téléviseur pour son salon par un particulier"],
}, cat=K, src="sup-comm", region="AF", fwd="Comment qualifier l'opération suivante en droit commercial : {item} ?", rev="Laquelle de ces opérations est {group} ?", diff=3, expl="Cette opération est {group}.")

classify(C, "l3dc-suretes-cl", {
    "une sûreté personnelle": ["le cautionnement", "la garantie autonome", "la lettre d'intention"],
    "une sûreté réelle mobilière": ["le gage", "le nantissement d'une créance"],
    "une sûreté réelle immobilière": ["l'hypothèque"],
    "une mesure d'exécution forcée, et non une sûreté": ["la saisie-vente", "la saisie-attribution", "la saisie immobilière"],
}, cat=K, src="sup-comm", region="AF", fwd="Comment classer {item} ?", rev="Lequel de ces éléments est {group} ?", diff=3, expl="{item} : {group}.")

classify(C, "l3dc-collectives-cl", {
    "la prévention des difficultés de l'entreprise": ["la demande d'un règlement préventif par un débiteur qui n'est pas en cessation des paiements", "l'intervention d'un conciliateur pour éviter la cessation des paiements"],
    "le redressement judiciaire": ["la poursuite de l'activité avec un concordat accepté par les créanciers", "la sauvegarde de l'activité et des emplois grâce à un plan de règlement du passif"],
    "la liquidation des biens": ["la vente de l'actif du débiteur pour payer les créanciers", "la fin de l'entreprise dont la situation est irrémédiablement compromise"],
    "l'exécution individuelle menée par un créancier isolé": ["la saisie-vente d'un bien pratiquée par un créancier", "la saisie-attribution d'une créance pratiquée par un seul créancier"],
}, cat=K, src="sup-comm", region="AF", fwd="À quel mécanisme se rattache la situation suivante : {item} ?", rev="Laquelle de ces situations relève de {group} ?", diff=4, expl="Cette situation relève de {group}.")

classify(C, "l3dc-compta-cl", {
    "un poste d'actif": ["un bâtiment appartenant à l'entreprise", "les marchandises en stock", "les sommes que les clients doivent à l'entreprise", "l'argent disponible en caisse"],
    "un poste de passif": ["le capital apporté par les associés", "un emprunt bancaire non encore remboursé", "une dette envers un fournisseur", "les réserves constituées avec les bénéfices"],
    "une charge de l'exercice": ["les salaires versés au personnel", "le loyer payé pour le local", "les intérêts payés sur un emprunt"],
    "un produit de l'exercice": ["les ventes de marchandises", "les intérêts reçus sur un placement", "une prestation de services facturée à un client"],
}, cat="Comptabilité (niveau général)", src="sup-comm", region="AF", fwd="Comment classer {item} dans les comptes de l'entreprise ?", rev="Lequel de ces éléments constitue {group} ?", diff=3, expl="{item} constitue {group}.")
