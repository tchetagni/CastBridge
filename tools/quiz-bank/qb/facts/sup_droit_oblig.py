"""L2 droit : droit des obligations, responsabilité, droit des biens et sûretés (principes uniquement, sans durées ni numéros d'articles).
Faits écrits par l'assistant : tous `review` ; relecture par un juriste recommandée (réformes récentes du droit des contrats en droit français)."""
from .sup_kit import *  # noqa: F401,F403

source("sup-oblig", "Droit des obligations : principes généraux", "cours de L2 droit", "Comparer avec un manuel de droit des obligations (théorie générale du contrat, régime de l'obligation, responsabilité) et le droit positif applicable (OHADA, droit civil local).")
source("sup-biens", "Droit des biens et sûretés : principes", "cours de L2 droit", "Comparer avec un manuel de droit des biens et de sûretés ; en Afrique OHADA, voir l'Acte uniforme portant organisation des sûretés.")

D = "l2-droit"
O = "sup-oblig"
B = "sup-biens"
DEF = dict(fwd="Comment définit-on {a} ?", rev="Quelle notion est ainsi définie : {b} ?")

# ---------------------------------------------------------------- Classification des obligations
table(D, "ob-clas", [
    ("une obligation de donner", "transférer la propriété d'une chose ou d'un droit réel", 2),
    ("une obligation de faire", "accomplir une prestation positive, un travail ou un service", 1),
    ("une obligation de ne pas faire", "s'abstenir d'un acte que l'on aurait été libre d'accomplir", 2),
    ("une obligation de moyens", "mettre en œuvre la diligence nécessaire sans garantir le résultat", 2),
    ("une obligation de résultat", "atteindre le résultat promis, sauf cause étrangère prouvée", 2),
    ("une obligation naturelle", "un devoir de conscience valablement exécuté mais non exigible en justice", 3),
    ("une obligation civile", "une obligation dont le créancier peut obtenir l'exécution forcée", 2),
    ("une obligation conditionnelle", "une obligation dont l'existence dépend d'un événement futur et incertain", 2),
    ("une obligation à terme", "une obligation dont l'exigibilité dépend d'un événement futur et certain", 2),
    ("une obligation solidaire", "une obligation où chaque débiteur peut être poursuivi pour la totalité", 3),
    ("une obligation alternative", "une obligation portant sur plusieurs prestations dont une seule libère le débiteur", 3),
    ("une obligation facultative", "une seule prestation due, le débiteur pouvant se libérer par une autre", 4),
    ("une obligation cumulative", "une obligation portant sur plusieurs prestations toutes dues ensemble", 4),
    ("une obligation indivisible", "une obligation dont l'objet ne peut être exécuté qu'en entier", 4),
    ("une obligation conjointe", "une obligation où chaque débiteur ne doit que sa part de la dette", 4),
    ("une obligation de garantie", "une obligation dont le débiteur ne s'exonère pas en prouvant son absence de faute", 5),
], cat="Régime général des obligations", src=O, **DEF)

table(D, "ob-sources", [
    ("un acte juridique", "une manifestation de volonté destinée à produire des effets de droit", 1),
    ("un fait juridique", "un événement auquel la loi attache des effets indépendamment de la volonté de les produire", 2),
    ("un contrat", "un accord de volontés destiné à créer, modifier, transmettre ou éteindre des obligations", 1),
    ("un quasi-contrat", "un fait volontaire licite dont résulte un engagement envers autrui sans accord de volontés", 3),
    ("un délit civil", "un fait illicite intentionnel causant un dommage et obligeant son auteur à réparer", 3),
    ("un quasi-délit", "un fait illicite non intentionnel (imprudence, négligence) causant un dommage à autrui", 3),
    ("l'obligation légale", "une obligation qui naît directement de la loi, sans que les parties l'aient voulue", 3),
], cat="Sources des obligations", src=O, **DEF)

classify(D, "ob-srcgrp", {
    "un acte juridique": ["la conclusion d'une vente", "la rédaction d'un testament", "la signature d'un bail", "l'acceptation d'une offre de contrat", "la constitution d'un cautionnement"],
    "un fait juridique": ["un accident de la circulation", "la naissance d'un enfant", "un décès", "la destruction d'une chose par un incendie", "un dommage causé par un chien"],
    "un quasi-contrat": ["la gestion d'affaires", "le paiement de l'indu", "l'enrichissement injustifié"],
    "une obligation légale": ["l'obligation alimentaire entre proches", "l'obligation d'entretien des enfants par leurs parents", "l'obligation de payer l'impôt"],
}, cat="Sources des obligations", src=O,
   fwd="Dans quelle catégorie de sources des obligations range-t-on {item} ?", rev="Lequel de ces exemples relève de la catégorie suivante : {group} ?")

# ---------------------------------------------------------------- Classification des contrats
table(D, "ct-clas", [
    ("un contrat synallagmatique", "un contrat qui crée des obligations réciproques à la charge des deux parties", 1),
    ("un contrat unilatéral", "un contrat qui n'oblige qu'une partie envers l'autre, sans réciprocité", 2),
    ("un contrat à titre onéreux", "un contrat où chaque partie reçoit un avantage en contrepartie de celui qu'elle procure", 1),
    ("un contrat à titre gratuit", "un contrat où une partie procure un avantage à l'autre sans attendre de contrepartie", 1),
    ("un contrat commutatif", "un contrat où l'étendue des prestations est connue des parties dès la formation", 3),
    ("un contrat aléatoire", "un contrat où l'avantage de chacun dépend d'un événement incertain", 3),
    ("un contrat consensuel", "un contrat qui se forme par le seul échange des consentements", 2),
    ("un contrat solennel", "un contrat dont la validité exige le respect d'une forme prescrite par la loi", 3),
    ("un contrat réel", "un contrat qui n'est formé que par la remise de la chose", 3),
    ("un contrat nommé", "un contrat qui a reçu de la loi une dénomination et un régime particulier", 2),
    ("un contrat innommé", "un contrat non réglementé spécialement, soumis au droit commun des contrats", 3),
    ("un contrat de gré à gré", "un contrat dont les stipulations sont librement négociées entre les parties", 3),
    ("un contrat d'adhésion", "un contrat dont les conditions générales sont fixées à l'avance par une partie", 3),
    ("un contrat à exécution instantanée", "un contrat dont les obligations s'exécutent en une prestation unique", 3),
    ("un contrat à exécution successive", "un contrat dont les obligations s'échelonnent dans le temps", 3),
    ("un contrat-cadre", "un accord fixant les caractéristiques générales de contrats d'application à venir", 4),
    ("un contrat conclu intuitu personae", "un contrat conclu en considération de la personne du cocontractant", 4),
], cat="Classification des contrats", src=O, **DEF)

classify(D, "ct-exgrp", {
    "un contrat synallagmatique": ["la vente", "le bail", "le contrat d'entreprise", "l'échange"],
    "un contrat unilatéral": ["la promesse unilatérale de vente", "la donation sans charge", "le cautionnement"],
    "un contrat aléatoire": ["le contrat d'assurance", "la rente viagère", "le pari"],
    "un contrat réel": ["le prêt à usage", "le dépôt", "le gage avec dépossession"],
}, cat="Classification des contrats", src=O, fwd="Comment peut-on classer {item} ?", rev="Lequel de ces contrats est classé comme {group} ?",
   diffs={"le cautionnement": 4, "la promesse unilatérale de vente": 4, "le gage avec dépossession": 5, "le dépôt": 5, "le prêt à usage": 4, "le contrat d'assurance": 4})

# ---------------------------------------------------------------- Formation du contrat
table(D, "ct-form", [
    ("le consentement", "la volonté libre et éclairée de chaque partie de s'engager", 1),
    ("la capacité", "l'aptitude à s'engager valablement, qui est la règle sauf incapacité légale", 2),
    ("l'offre", "la proposition ferme de contracter, exprimant la volonté d'être lié en cas d'acceptation", 2),
    ("l'acceptation", "la manifestation de volonté de celui qui accueille l'offre sans réserve", 2),
    ("la promesse unilatérale", "le contrat donnant au bénéficiaire le droit d'opter pour la conclusion d'un contrat déterminé", 4),
    ("le pacte de préférence", "l'engagement de proposer en priorité à son bénéficiaire de traiter si l'on décide de contracter", 4),
    ("les pourparlers", "la phase de discussions précédant la conclusion éventuelle du contrat", 2),
    ("l'avant-contrat", "un accord préparatoire qui prépare ou encadre la formation du contrat définitif", 3),
    ("le droit de rétractation", "la faculté légale, pour certains contractants, de revenir sur leur engagement", 3),
], cat="Formation du contrat", src=O, fwd="Que désigne {a} dans la formation du contrat ?", rev="Quelle notion de la formation du contrat est ainsi définie : {b} ?")

table(D, "ct-vices", [
    ("le dol", "une tromperie par manœuvres, mensonge ou dissimulation destinée à obtenir le consentement", 2),
    ("la violence", "une contrainte physique ou morale qui inspire la crainte d'un mal considérable", 2),
    ("la lésion", "un déséquilibre économique entre prestations, sanctionné seulement dans des cas prévus par la loi", 4),
    ("l'abus de dépendance", "le fait d'obtenir un engagement excessif en profitant de l'état de dépendance d'autrui", 4),
    ("l'erreur obstacle", "une erreur si grave qu'elle empêche toute rencontre des volontés", 5),
    ("l'erreur sur la valeur", "une appréciation économique inexacte, qui n'est en principe pas une cause de nullité", 5),
], cat="Consentement et vices du consentement", src=O, fwd="Qu'est-ce que {a} en droit des contrats ?", rev="Quel vice ou quelle notion est ainsi défini : {b} ?")

table(D, "ct-capa", [
    ("la capacité de jouissance", "l'aptitude à être titulaire de droits et d'obligations", 2),
    ("la capacité d'exercice", "l'aptitude à exercer soi-même ses droits et à passer des actes juridiques", 2),
    ("la représentation", "le mécanisme par lequel une personne agit au nom et pour le compte d'une autre", 3),
    ("la tutelle", "un régime où le représentant accomplit les actes à la place de la personne protégée", 3),
    ("la curatelle", "un régime où le majeur protégé est assisté pour les actes importants", 4),
    ("l'incapacité de protection", "l'inaptitude légale à contracter instituée pour protéger la personne elle-même", 4),
    ("l'incapacité de défiance", "l'interdiction de contracter entre certaines personnes pour prévenir un risque d'abus", 5),
], cat="Capacité", src=O, **DEF)

table(D, "ct-contenu", [
    ("l'objet de l'obligation", "la prestation promise, qui doit être possible, déterminée ou déterminable et licite", 3),
    ("la cause (théorie classique)", "le but immédiat de l'engagement, qui est la contrepartie dans un contrat synallagmatique", 4),
    ("la cause subjective", "le motif déterminant et connu des deux parties qui les a poussées à contracter", 5),
    ("l'ordre public", "l'ensemble des règles essentielles auxquelles les parties ne peuvent déroger par convention", 2),
    ("les bonnes mœurs", "les exigences de la morale sociale dominante dont la violation rend un contrat illicite", 3),
    ("la contrepartie illusoire", "une contrepartie purement apparente qui prive de sens l'engagement de l'autre partie", 5),
    ("le prix déterminable", "un prix qui peut être fixé sans nouvel accord grâce à des éléments objectifs convenus", 5),
], cat="Objet et contenu du contrat", src=O, **DEF)

table(D, "ct-forme", [
    ("le formalisme ad validitatem", "la forme exigée comme condition de validité de l'acte, sous peine de nullité", 3),
    ("le formalisme ad probationem", "la forme exigée seulement pour pouvoir prouver l'acte", 4),
    ("le formalisme d'opposabilité", "la forme destinée à rendre l'acte opposable aux tiers", 4),
    ("l'acte authentique", "un acte reçu par un officier public compétent selon les formes requises", 2),
    ("l'acte sous seing privé", "un acte établi par les parties elles-mêmes sans intervention d'un officier public", 2),
    ("le principe du consensualisme", "la règle selon laquelle le contrat se forme par le seul échange des consentements", 2),
    ("le formalisme informatif", "la forme destinée à garantir l'information du contractant avant son engagement", 4),
], cat="Formalisme", src=O, **DEF)

# ---------------------------------------------------------------- Effets du contrat
table(D, "ct-effets", [
    ("la force obligatoire", "le principe selon lequel le contrat légalement formé tient lieu de loi aux parties", 2),
    ("l'effet relatif", "le principe selon lequel le contrat ne crée d'obligations qu'entre les parties", 3),
    ("l'opposabilité du contrat", "la possibilité d'invoquer l'existence du contrat comme un fait, y compris contre les tiers", 4),
    ("la bonne foi contractuelle", "le devoir de loyauté qui gouverne la négociation, la formation et l'exécution", 2),
    ("la stipulation pour autrui", "le contrat par lequel une partie fait promettre à l'autre une prestation au profit d'un tiers", 4),
    ("la promesse de porte-fort", "l'engagement de faire ratifier par un tiers un engagement que l'on ne peut prendre pour lui", 5),
    ("l'imprévision", "le déséquilibre causé par un changement imprévisible qui rend l'exécution excessivement onéreuse", 4),
    ("l'interprétation du contrat", "la recherche de la commune intention des parties plutôt que du sens littéral des termes", 3),
    ("la révision pour imprévision", "l'adaptation du contrat par le juge après échec de la renégociation, là où elle est admise", 5),
], cat="Effets du contrat", src=O, **DEF)

# ---------------------------------------------------------------- Inexécution
table(D, "ct-inex", [
    ("l'exception d'inexécution", "le moyen par lequel une partie suspend sa prestation tant que l'autre n'exécute pas la sienne", 2),
    ("la résolution", "la sanction d'une inexécution suffisamment grave qui met fin au contrat", 3),
    ("l'exécution forcée en nature", "la contrainte par laquelle le créancier obtient la prestation promise plutôt que de l'argent", 3),
    ("les dommages et intérêts", "la somme d'argent due pour réparer le préjudice causé par l'inexécution", 2),
    ("la clause pénale", "la clause fixant à l'avance le montant forfaitaire dû en cas d'inexécution", 3),
    ("la clause résolutoire", "la clause prévoyant la résolution de plein droit en cas d'inexécution déterminée", 3),
    ("la mise en demeure", "l'acte par lequel le créancier somme le débiteur d'exécuter dans un délai raisonnable", 3),
    ("la réduction du prix", "la diminution proportionnelle du prix demandée par le créancier d'une exécution imparfaite", 4),
    ("l'exécution par un tiers", "la faculté du créancier de faire exécuter la prestation par autrui aux frais du débiteur", 4),
    ("la clause limitative de responsabilité", "la stipulation qui plafonne le montant de la réparation due par le débiteur", 4),
    ("la clause exonératoire", "la stipulation écartant la responsabilité du débiteur, sauf faute lourde ou dol", 5),
    ("l'inexécution par anticipation", "la certitude que le débiteur n'exécutera pas à l'échéance, justifiant des mesures avant terme", 5),
], cat="Inexécution du contrat", src=O, **DEF)

table(D, "ct-fm", [
    ("la force majeure", "un événement échappant au débiteur, imprévisible et dont les effets sont inévitables", 3),
    ("l'extériorité", "le caractère d'un événement étranger à la sphère de contrôle du débiteur", 4),
    ("l'imprévisibilité", "le caractère d'un événement qu'on ne pouvait raisonnablement prévoir à la conclusion", 3),
    ("l'irrésistibilité", "le caractère d'un événement dont les effets ne peuvent être évités par des mesures appropriées", 3),
    ("le fait du créancier", "le comportement fautif ou non du créancier qui exonère le débiteur en tout ou partie", 5),
    ("la suspension pour empêchement temporaire", "l'effet d'un empêchement non définitif qui retarde l'exécution sans éteindre l'obligation", 5),
], cat="Inexécution du contrat", src=O, **DEF)

table(D, "ct-sanct", [
    ("la nullité absolue", "la nullité qui sanctionne la violation d'une règle protégeant l'intérêt général", 3),
    ("la nullité relative", "la nullité qui sanctionne la violation d'une règle protégeant un intérêt privé", 3),
    ("la caducité", "la disparition d'un élément essentiel d'un contrat pourtant valablement formé", 4),
    ("la confirmation", "l'acte par lequel on renonce à se prévaloir d'une nullité relative", 4),
    ("l'inopposabilité", "la sanction laissant l'acte valable entre les parties mais sans effet à l'égard des tiers", 4),
    ("les restitutions", "la remise des parties dans l'état antérieur après nullité ou résolution", 4),
    ("la nullité partielle", "l'anéantissement limité à une clause lorsqu'elle n'a pas été déterminante", 5),
    ("la clause réputée non écrite", "la clause considérée comme n'ayant jamais existé, sans affecter le reste du contrat", 5),
], cat="Nullité, caducité, résolution", src=O, **DEF)

# ---------------------------------------------------------------- Responsabilité civile
table(D, "rc-notions", [
    ("la responsabilité contractuelle", "l'obligation de réparer le dommage causé par l'inexécution d'un contrat", 2),
    ("la responsabilité extracontractuelle", "l'obligation de réparer un dommage causé hors de tout lien contractuel entre les personnes", 2),
    ("la faute", "un comportement ne correspondant pas à celui qu'aurait eu une personne prudente et diligente", 2),
    ("le préjudice réparable", "une atteinte à un intérêt légitime, certaine et personnelle à la victime", 3),
    ("le lien de causalité", "le rapport de cause à effet entre le fait générateur et le dommage subi", 2),
    ("la réparation intégrale", "le principe de réparation replaçant la victime dans la situation antérieure, sans perte ni profit", 4),
    ("le préjudice moral", "une atteinte extrapatrimoniale, par exemple à l'affection, à l'honneur ou à la douleur ressentie", 2),
    ("le préjudice matériel", "une atteinte au patrimoine de la victime, comme une perte ou un gain manqué", 2),
    ("la perte de chance", "la disparition d'une éventualité favorable, indemnisée selon sa probabilité de réalisation", 4),
    ("le dommage par ricochet", "le dommage subi par un proche de la victime directe à la suite du dommage initial", 4),
], cat="Responsabilité civile", src=O, **DEF)

table(D, "rc-causal", [
    ("la théorie de l'équivalence des conditions", "toutes les conditions sans lesquelles le dommage ne serait pas survenu en sont causes", 5),
    ("la théorie de la causalité adéquate", "seul l'événement qui, normalement, entraîne le dommage en est la cause juridique", 5),
    ("la cause étrangère exonératoire", "un événement non imputable au défendeur qui rompt le lien de causalité", 4),
    ("la faute de la victime", "le comportement fautif de la victime qui peut exonérer totalement ou partiellement l'auteur", 3),
    ("le fait d'un tiers", "l'intervention d'un étranger au litige, exonératoire si elle est imprévisible et irrésistible", 5),
    ("la pluralité de causes", "la situation où plusieurs faits ont concouru au dommage, permettant un partage de responsabilité", 4),
], cat="Responsabilité civile", src=O, fwd="Que désigne {a} en matière de causalité ?", rev="Quelle notion est ainsi définie : {b} ?")

table(D, "rc-regimes", [
    ("la responsabilité du fait personnel", "le régime où l'on répond de sa propre faute, de son imprudence ou de sa négligence", 2),
    ("la responsabilité du fait d'autrui", "le régime où l'on répond du dommage causé par une autre personne dont on a la charge", 3),
    ("la responsabilité du fait des choses", "le régime où le gardien répond du dommage causé par la chose qu'il a sous sa garde", 3),
    ("la responsabilité du fait des animaux", "le régime où le propriétaire ou l'utilisateur de l'animal répond du dommage qu'il cause", 3),
    ("la responsabilité des parents du fait de leurs enfants mineurs", "le régime pesant sur les parents exerçant l'autorité parentale pour le dommage causé par l'enfant", 4),
    ("la responsabilité du commettant du fait de son préposé", "le régime où l'employeur répond du dommage causé par son subordonné dans ses fonctions", 4),
    ("la responsabilité du fait des produits défectueux", "le régime spécial où le producteur répond du dommage causé par un défaut de son produit", 5),
    ("la responsabilité sans faute", "le régime où la victime n'a pas à prouver une faute du défendeur", 3),
    ("la responsabilité pour faute", "le régime où la victime doit prouver la faute du défendeur", 2),
], cat="Responsabilité civile", src=O, fwd="Que désigne {a} ?", rev="Quel régime est ainsi défini : {b} ?")

table(D, "rc-gardien", [
    ("le gardien de la chose", "celui qui a l'usage, la direction et le contrôle de la chose au moment du dommage", 3),
    ("le fait de la chose", "l'intervention causale de la chose dans la production du dommage", 4),
    ("le commettant", "la personne qui donne des ordres à une autre dans une relation de subordination", 3),
    ("le préposé", "la personne qui agit sous l'autorité et pour le compte d'un commettant", 3),
    ("l'abus de fonctions du préposé", "l'acte accompli hors des fonctions, sans autorisation et à des fins étrangères aux attributions", 5),
    ("l'action récursoire", "l'action permettant à celui qui a payé pour un autre de se faire rembourser par le responsable", 4),
    ("la garde en commun", "la situation où plusieurs personnes exercent ensemble les pouvoirs de garde sur une même chose", 5),
    ("le transfert de la garde", "le passage des pouvoirs d'usage, de direction et de contrôle d'une personne à une autre", 5),
], cat="Responsabilité civile", src=O, **DEF)

# ---------------------------------------------------------------- Quasi-contrats
table(D, "qc-notions", [
    ("la gestion d'affaires", "le fait d'intervenir volontairement et utilement dans les affaires d'autrui à son insu", 2),
    ("le paiement de l'indu", "le fait de payer ce que l'on ne doit pas, ouvrant droit à restitution", 2),
    ("l'enrichissement injustifié", "l'avantage obtenu sans cause légitime au détriment d'autrui, qui doit une indemnité", 3),
    ("le gérant d'affaires", "la personne qui agit spontanément dans l'intérêt d'autrui sans en avoir reçu mission", 2),
    ("le maître de l'affaire", "la personne dont les affaires sont gérées par un gérant", 3),
    ("l'action en répétition de l'indu", "l'action par laquelle celui qui a payé indûment demande la restitution", 3),
    ("l'appauvrissement", "la perte pécuniaire subie par celui qui agit en enrichissement injustifié", 4),
    ("le caractère subsidiaire de l'action", "la règle selon laquelle l'action ne s'ouvre pas si une autre action est disponible", 5),
    ("l'utilité de la gestion", "la condition tenant à ce que l'intervention du gérant servait réellement l'intérêt du maître", 4),
], cat="Quasi-contrats", src=O, **DEF)

# ---------------------------------------------------------------- Extinction
table(D, "ex-modes", [
    ("le paiement", "l'exécution volontaire de la prestation due, qui éteint l'obligation", 1),
    ("la compensation", "l'extinction simultanée de deux obligations réciproques entre deux personnes à due concurrence", 2),
    ("la novation", "le contrat qui éteint une obligation en lui substituant une obligation nouvelle", 3),
    ("la remise de dette", "l'acte par lequel le créancier renonce gratuitement à sa créance", 2),
    ("la confusion", "la réunion sur une même personne des qualités de créancier et de débiteur de la même obligation", 3),
    ("l'impossibilité d'exécuter", "l'extinction de l'obligation devenue définitivement impossible à exécuter sans faute du débiteur", 4),
    ("la dation en paiement", "le paiement par une prestation différente de celle due, avec l'accord du créancier", 4),
    ("la consignation", "le dépôt de la somme offerte auprès d'un tiers lorsque le créancier refuse de la recevoir", 4),
    ("la subrogation personnelle", "le transfert de la créance au tiers qui a payé à la place du débiteur", 4),
], cat="Extinction des obligations", src=O, **DEF)

table(D, "ex-comp", [
    ("la compensation légale", "celle qui opère de plein droit entre dettes certaines, liquides et exigibles", 4),
    ("la compensation judiciaire", "celle qui est prononcée par le juge saisi d'une demande", 4),
    ("la compensation conventionnelle", "celle qui résulte d'un accord des parties", 4),
    ("la créance certaine", "une créance dont l'existence n'est pas contestée sérieusement", 5),
    ("la créance liquide", "une créance dont le montant est déterminé ou déterminable", 5),
    ("la créance exigible", "une créance dont le créancier peut demander l'exécution immédiate", 4),
], cat="Extinction des obligations", src=O, fwd="Que désigne {a} ?", rev="Quelle notion est ainsi définie : {b} ?")

table(D, "ex-cession", [
    ("la cession de créance", "le contrat par lequel le créancier transmet sa créance à un tiers", 3),
    ("la cession de dette", "le contrat par lequel un tiers prend la place du débiteur avec l'accord du créancier", 4),
    ("la cession de contrat", "le transfert à un tiers de la qualité de partie à un contrat avec l'accord du cocontractant", 4),
    ("le cédant", "celui qui transmet sa créance à un tiers", 2),
    ("le cessionnaire", "celui qui acquiert la créance transmise", 2),
    ("le cédé", "le débiteur de la créance cédée", 3),
    ("la délégation", "l'opération par laquelle un débiteur fait obliger une autre personne envers son créancier", 5),
    ("l'opposabilité de la cession aux tiers", "la possibilité d'invoquer la cession contre les tiers, soumise à des formalités", 5),
], cat="Régime général des obligations", src=O, **DEF)

table(D, "ex-prescr", [
    ("la prescription extinctive", "le mode d'extinction d'un droit d'agir par non-usage pendant un temps fixé par la loi", 2),
    ("la prescription acquisitive", "le mode d'acquisition d'un droit réel par une possession prolongée", 3),
    ("le point de départ du délai", "le moment où le titulaire du droit a pu agir, selon sa connaissance des faits", 4),
    ("la suspension de la prescription", "l'arrêt temporaire du cours du délai, sans effacer le temps déjà écoulé", 4),
    ("l'interruption de la prescription", "l'événement qui efface le délai déjà couru et fait repartir un nouveau délai", 4),
    ("la renonciation à la prescription", "l'acte par lequel le débiteur renonce à invoquer la prescription acquise", 4),
    ("l'exception de prescription", "le moyen de défense par lequel on oppose le temps écoulé à la demande adverse", 3),
    ("la forclusion", "la déchéance d'un droit faute de l'avoir exercé dans un délai préfix", 5),
], cat="Extinction des obligations", src=O, **DEF)

# ---------------------------------------------------------------- Preuve
table(D, "pv-notions", [
    ("la charge de la preuve", "la règle selon laquelle celui qui réclame l'exécution d'une obligation doit la prouver", 2),
    ("la preuve littérale", "la preuve par un écrit établi pour constater l'acte", 2),
    ("le commencement de preuve par écrit", "un écrit émanant de l'adversaire qui rend vraisemblable le fait allégué", 4),
    ("la présomption légale", "une conséquence que la loi tire d'un fait connu pour établir un fait inconnu", 3),
    ("la présomption simple", "une présomption que la preuve contraire peut renverser", 3),
    ("la présomption irréfragable", "une présomption qui n'admet pas la preuve contraire", 4),
    ("l'aveu judiciaire", "la déclaration faite en justice par une partie, qui reconnaît un fait contre elle", 4),
    ("le serment décisoire", "le serment déféré par une partie à l'autre pour mettre fin au litige", 5),
    ("la preuve par témoignage", "une preuve orale, d'un poids limité lorsqu'une preuve écrite est exigée", 3),
    ("le principe de la liberté de la preuve", "la règle selon laquelle les faits juridiques peuvent être prouvés par tout moyen", 3),
    ("l'autorité de la chose jugée", "l'effet d'une décision qui interdit de remettre en cause ce qu'elle a définitivement tranché", 4),
], cat="Preuve des obligations", src=O, **DEF)

# ---------------------------------------------------------------- Droits réels, propriété, possession
table(D, "bi-droits", [
    ("un droit réel", "un droit qui porte directement sur une chose et se suit en quelque main qu'elle passe", 2),
    ("un droit personnel", "un droit de créance qui permet d'exiger une prestation d'une personne déterminée, le débiteur", 2),
    ("le droit de suite", "la prérogative du titulaire d'un droit réel de poursuivre la chose entre les mains d'autrui", 3),
    ("le droit de préférence", "la prérogative d'être payé avant les autres créanciers sur le prix d'un bien", 3),
    ("un droit réel principal", "un droit réel qui existe par lui-même, comme la propriété ou l'usufruit", 3),
    ("un droit réel accessoire", "un droit réel qui garantit une créance, comme l'hypothèque ou le gage", 3),
    ("le principe du numerus clausus", "l'idée classique selon laquelle les droits réels sont en nombre limité", 5),
    ("une obligation réelle (propter rem)", "une obligation attachée à une chose, qui pèse sur son titulaire en cette qualité", 5),
], cat="Droit des biens", src=B, **DEF)

table(D, "bi-prop", [
    ("l'usus", "le droit d'utiliser la chose", 2),
    ("le fructus", "le droit d'en percevoir les fruits", 2),
    ("l'abusus", "le droit de disposer de la chose, matériellement ou juridiquement", 2),
    ("le caractère absolu de la propriété", "la propriété confère le plus large pouvoir sur la chose, dans les limites fixées par la loi", 3),
    ("le caractère exclusif de la propriété", "le propriétaire peut écarter les tiers de l'usage de la chose", 3),
    ("le caractère perpétuel de la propriété", "la propriété ne s'éteint pas par le non-usage en tant que tel", 4),
    ("l'accession", "le mode d'acquisition de la propriété par l'incorporation à ce que l'on possède déjà", 3),
    ("l'abus de droit", "l'exercice d'un droit dans le but de nuire ou en dehors de sa finalité", 4),
    ("les troubles anormaux de voisinage", "les nuisances excédant les inconvénients normaux du voisinage, source d'une responsabilité", 4),
    ("l'expropriation pour cause d'utilité publique", "le transfert forcé de la propriété à l'autorité publique moyennant une indemnité", 3),
], cat="Droit des biens", src=B, **DEF)

table(D, "bi-classif", [
    ("un meuble par nature", "un bien susceptible d'être déplacé sans altération, comme un véhicule ou un livre", 2),
    ("un immeuble par nature", "un bien qui ne peut être déplacé, comme un fonds de terre ou un bâtiment", 2),
    ("un immeuble par destination", "un meuble considéré comme immeuble parce qu'il est affecté au service d'un immeuble", 4),
    ("un bien fongible", "un bien interchangeable avec d'autres de même espèce, quantité et qualité", 3),
    ("un bien consomptible", "un bien dont le premier usage entraîne la disparition, comme une denrée", 3),
    ("un bien corporel", "un bien qui a une existence matérielle et qui peut être touché", 2),
    ("un bien incorporel", "un bien sans existence matérielle, comme une créance ou un brevet", 2),
    ("une chose commune", "une chose dont l'usage appartient à tous, sans appropriation privative", 4),
    ("un bien du domaine public", "un bien appartenant à une personne publique et affecté à l'usage du public ou à un service public", 3),
    ("un bien frugifère", "un bien qui produit des fruits, naturels ou civils", 4),
    ("un fruit civil", "un revenu périodique tiré d'un bien, comme un loyer ou des intérêts", 3),
    ("un produit", "ce qui est tiré de la substance même du bien et en diminue la valeur", 5),
], cat="Droit des biens", src=B, **DEF)

classify(D, "bi-grp", {
    "un meuble corporel": ["un véhicule", "un livre", "un téléphone", "une table"],
    "un meuble incorporel": ["une créance", "un brevet d'invention", "des parts sociales", "un fonds de commerce"],
    "un immeuble par nature": ["un terrain", "une maison", "un immeuble d'habitation", "un bâtiment industriel"],
    "un fruit civil": ["un loyer", "les intérêts d'une somme prêtée", "les arrérages d'une rente", "un fermage"],
    "un fruit naturel": ["les récoltes d'un champ", "le lait d'une vache", "le croît d'un troupeau"],
}, cat="Droit des biens", src=B, fwd="Comment qualifier {item} ?", rev="Lequel de ces éléments relève de la catégorie suivante : {group} ?",
   diffs={"un fonds de commerce": 4, "des parts sociales": 4, "les arrérages d'une rente": 5, "un fermage": 4, "le croît d'un troupeau": 5})

table(D, "bi-demembr", [
    ("l'usufruit", "le droit d'user de la chose d'autrui et d'en percevoir les fruits, sans pouvoir en disposer", 3),
    ("la nue-propriété", "la propriété d'un bien grevé d'usufruit, dont le titulaire retrouvera la pleine jouissance", 3),
    ("le droit d'usage", "le droit d'utiliser une chose et de percevoir les fruits dans la limite des besoins du titulaire", 4),
    ("le droit d'habitation", "le droit d'occuper une maison d'habitation dans la limite des besoins du titulaire et de sa famille", 4),
    ("la servitude", "une charge imposée à un fonds, dit servant, au profit d'un autre fonds, dit dominant", 3),
    ("l'emphytéose", "un droit réel de longue durée permettant de jouir d'un immeuble moyennant une redevance", 5),
    ("l'usufruitier", "le titulaire du droit d'user de la chose d'autrui et d'en recueillir les fruits", 2),
    ("le nu-propriétaire", "le propriétaire dont les droits sont grevés d'un usufruit", 3),
    ("le quasi-usufruit", "l'usufruit portant sur des choses consomptibles, avec obligation de restituer l'équivalent", 5),
    ("l'obligation de conserver la substance", "l'obligation de l'usufruitier de rendre la chose sans en altérer la substance", 4),
], cat="Droit des biens", src=B, **DEF)

table(D, "bi-servit", [
    ("une servitude de passage", "une servitude permettant au propriétaire d'un fonds enclavé de passer sur le fonds voisin", 3),
    ("une servitude de vue", "une servitude qui règle les ouvertures donnant sur le fonds voisin", 4),
    ("le fonds dominant", "le fonds qui bénéficie de l'avantage d'une servitude", 3),
    ("le fonds servant", "le fonds qui supporte la charge d'une servitude", 3),
    ("une servitude légale", "une servitude qui dérive directement de la loi", 4),
    ("une servitude conventionnelle", "une servitude établie par un contrat ou un testament", 4),
    ("la servitude continue", "une servitude dont l'exercice n'exige pas le fait actuel de l'homme", 5),
    ("la servitude discontinue", "une servitude dont l'exercice suppose le fait actuel de l'homme, comme le passage", 5),
], cat="Droit des biens", src=B, **DEF)

table(D, "bi-copro", [
    ("l'indivision", "la situation de plusieurs personnes titulaires de droits de même nature sur un même bien", 3),
    ("la copropriété d'un immeuble bâti", "le régime d'un immeuble divisé en lots comprenant parties privatives et parties communes", 3),
    ("le lot de copropriété", "une fraction d'immeuble composée d'une partie privative et d'une quote-part de parties communes", 4),
    ("la quote-part indivise", "la fraction abstraite du droit de chaque indivisaire sur l'ensemble du bien", 4),
    ("le partage", "l'opération qui met fin à l'indivision en attribuant à chacun des biens privatifs", 3),
    ("le syndicat des copropriétaires (droit français)", "le groupement doté de la personnalité morale qui administre les parties communes", 4),
], cat="Droit des biens", src=B, **DEF)

table(D, "bi-poss", [
    ("la possession", "le fait de détenir une chose et de se comporter comme son titulaire", 2),
    ("la détention précaire", "le fait de détenir une chose pour autrui, par exemple en qualité de locataire", 3),
    ("le corpus", "l'élément matériel de la possession, c'est-à-dire le pouvoir de fait sur la chose", 3),
    ("l'animus", "l'élément intentionnel de la possession, c'est-à-dire la volonté de se comporter en propriétaire", 3),
    ("la possession utile", "une possession continue, paisible, publique et non équivoque", 4),
    ("le vice de clandestinité", "le défaut d'une possession exercée de façon dissimulée", 5),
    ("le vice de violence", "le défaut d'une possession acquise ou maintenue par des voies de fait", 5),
    ("l'usucapion", "le mode d'acquisition de la propriété par une possession utile prolongée", 3),
    ("la possession de bonne foi", "la possession de celui qui se croit propriétaire en vertu d'un titre dont il ignore le vice", 4),
    ("la jonction des possessions", "la faculté d'ajouter à sa possession celle de son auteur", 5),
    ("les actions possessoires", "les actions destinées à protéger la possession en tant que fait, sans débat sur la propriété", 4),
    ("la revendication", "l'action par laquelle le propriétaire réclame la restitution de sa chose", 3),
    ("la règle « en fait de meubles, la possession vaut titre » (droit français)", "la protection de l'acquéreur de bonne foi d'un meuble corporel, avec des exceptions", 5),
], cat="Possession et usucapion", src=B, **DEF)

table(D, "bi-pub", [
    ("la publicité foncière", "l'ensemble des procédés rendant les actes relatifs aux immeubles opposables aux tiers", 3),
    ("le titre foncier", "un document public constatant la propriété d'un immeuble immatriculé", 3),
    ("l'immatriculation", "l'inscription d'un immeuble sur un registre foncier qui en établit la situation juridique", 4),
    ("le conservateur de la propriété foncière", "le fonctionnaire chargé de tenir le registre foncier et de procéder aux inscriptions", 3),
    ("le bornage", "l'opération qui fixe la limite entre deux fonds contigus", 3),
], cat="Droit des biens", src=B, **DEF)

# ---------------------------------------------------------------- Sûretés
table(D, "su-notions", [
    ("une sûreté", "un mécanisme juridique qui garantit au créancier le paiement de sa créance", 1),
    ("une sûreté personnelle", "une garantie fondée sur l'engagement d'une personne autre que le débiteur", 2),
    ("une sûreté réelle", "une garantie fondée sur l'affectation d'un bien au paiement de la créance", 2),
    ("le gage commun des créanciers", "le principe selon lequel les biens du débiteur répondent de ses dettes", 3),
    ("le principe de l'accessoire", "la règle selon laquelle la garantie suit le sort de la créance garantie", 4),
    ("la subsidiarité", "le caractère d'une garantie dont le garant n'est tenu qu'en cas de défaillance du débiteur", 4),
    ("la sûreté réelle sans dépossession", "une garantie où le constituant garde la détention du bien, comme l'hypothèque", 4),
    ("la sûreté réelle avec dépossession", "une garantie où le bien est remis au créancier ou à un tiers, comme le gage classique", 4),
    ("le pacte commissoire", "la clause attribuant au créancier la propriété du bien en cas de non-paiement, restreinte par la loi", 5),
], cat="Sûretés", src=B, fwd="Que désigne {a} ?", rev="Quelle notion est ainsi définie : {b} ?")

table(D, "su-types", [
    ("le cautionnement", "le contrat par lequel on s'engage envers le créancier à payer si le débiteur ne paie pas", 2),
    ("la garantie autonome", "l'engagement de payer à première demande, indépendamment de la validité du contrat de base", 4),
    ("la lettre d'intention", "un engagement de faire ou de ne pas faire pour soutenir un débiteur dans l'exécution de sa dette", 5),
    ("l'hypothèque", "une sûreté réelle sans dépossession portant en principe sur un immeuble", 2),
    ("le gage", "une sûreté réelle par laquelle un bien meuble est affecté en garantie d'une dette", 2),
    ("le nantissement", "une sûreté réelle portant sur un meuble incorporel, comme une créance ou un fonds de commerce", 3),
    ("le privilège", "un droit de préférence attribué par la loi à certaines créances en raison de leur qualité", 3),
    ("la propriété retenue à titre de garantie", "la réserve de propriété qui retarde le transfert jusqu'au paiement complet", 4),
    ("la fiducie-sûreté", "le transfert de propriété d'un bien à un fiduciaire pour garantir une obligation", 5),
    ("le droit de rétention", "la faculté du détenteur d'une chose de la garder jusqu'au paiement de ce qui lui est dû", 4),
], cat="Sûretés", src=B, fwd="Qu'est-ce que {a} ?", rev="Quelle garantie est ainsi définie : {b} ?")

table(D, "su-caution", [
    ("la caution", "la personne qui s'engage à payer la dette d'autrui si le débiteur ne paie pas", 2),
    ("le bénéfice de discussion", "le droit de la caution d'exiger que le créancier poursuive d'abord les biens du débiteur", 4),
    ("le bénéfice de division", "le droit d'une caution, lorsqu'il y a plusieurs cautions, de n'être tenue que pour sa part", 4),
    ("la caution solidaire", "la caution qui ne peut invoquer le bénéfice de discussion", 4),
    ("le recours personnel de la caution", "l'action de la caution contre le débiteur principal après paiement", 4),
    ("le recours subrogatoire de la caution", "l'action de la caution agissant aux droits du créancier qu'elle a désintéressé", 5),
    ("la certification de caution", "l'engagement d'une personne qui garantit la solvabilité d'une caution", 5),
    ("la proportionnalité de l'engagement", "l'exigence de ne pas engager une caution personne physique de façon manifestement disproportionnée", 5),
    ("le devoir de mise en garde", "l'obligation du créancier professionnel d'alerter la caution non avertie sur un risque d'endettement", 5),
    ("le cautionnement réel", "l'affectation d'un bien en garantie de la dette d'autrui sans engagement personnel pour la dette", 5),
], cat="Sûretés", src=B, **DEF)

table(D, "su-reel", [
    ("l'hypothèque conventionnelle", "celle qui résulte d'un contrat entre le constituant et le créancier", 4),
    ("l'hypothèque légale", "celle qui résulte directement de la loi au profit de certaines créances", 4),
    ("l'hypothèque judiciaire", "celle qui résulte d'une décision de justice", 4),
    ("le rang des créanciers hypothécaires", "l'ordre de paiement entre créanciers déterminé en principe par la date de l'inscription", 4),
    ("le privilège immobilier", "le droit de préférence légal portant sur un immeuble", 4),
    ("le privilège mobilier général", "le droit de préférence légal portant sur l'ensemble des meubles du débiteur", 5),
    ("la purge des hypothèques", "la procédure permettant à l'acquéreur d'un immeuble de se libérer des inscriptions", 5),
    ("la saisie immobilière", "la procédure d'exécution par laquelle l'immeuble du débiteur est vendu pour payer ses créanciers", 3),
    ("la réalisation de la sûreté", "la mise en œuvre de la garantie pour désintéresser le créancier impayé", 4),
    ("l'inscription hypothécaire", "la formalité qui assure la publicité de l'hypothèque et lui permet de prendre rang", 4),
], cat="Sûretés", src=B, **DEF)

classify(D, "su-grp", {
    "une sûreté personnelle": ["le cautionnement", "la garantie autonome", "la lettre d'intention"],
    "une sûreté réelle immobilière": ["l'hypothèque conventionnelle", "l'hypothèque légale", "l'hypothèque judiciaire"],
    "une sûreté réelle mobilière": ["le gage de meubles corporels", "le nantissement de créance", "le nantissement de fonds de commerce"],
    "une mesure de saisie": ["la saisie-vente", "la saisie immobilière", "la saisie-attribution"],
}, cat="Sûretés", src=B, fwd="Comment qualifier {item} ?", rev="Lequel de ces éléments relève de la catégorie suivante : {group} ?",
   diffs={"la lettre d'intention": 5, "la garantie autonome": 4, "la saisie-attribution": 4, "la saisie-vente": 4, "l'hypothèque légale": 4})

# ================================================================ Questions rédigées à la main
mcq(D, "mq-oblig", [
    ("Un chirurgien s'engage à opérer un patient avec toute la diligence requise, sans garantir la guérison. De quel type d'obligation s'agit-il, en principe ?", "Une obligation de moyens", ["Une obligation de résultat", "Une obligation de garantie", "Une obligation naturelle"], "Il doit soigner avec diligence, sans promettre la guérison.", 2),
    ("Un transporteur s'engage à livrer une marchandise à destination. Quelle est, en principe, la nature de son obligation de livraison ?", "Une obligation de résultat", ["Une obligation de moyens", "Une obligation naturelle", "Une obligation facultative"], "Le transporteur promet l'arrivée de la marchandise.", 2),
    ("Dans une obligation de moyens, que doit prouver le créancier insatisfait pour engager la responsabilité du débiteur ?", "Que le débiteur n'a pas déployé la diligence requise", ["Seulement que le résultat n'a pas été atteint", "Que le débiteur a agi de mauvaise foi", "Que le débiteur est insolvable"], "Le créancier doit établir le manquement à la diligence.", 3),
    ("Dans une obligation de résultat, comment le débiteur peut-il en principe s'exonérer lorsque le résultat n'est pas atteint ?", "En prouvant une cause étrangère", ["En prouvant qu'il a fait tous les efforts possibles", "En invoquant sa bonne foi", "En offrant de recommencer gratuitement"], "L'absence de faute ne suffit pas ; seule la cause étrangère exonère.", 3),
    ("Que se passe-t-il lorsqu'un débiteur exécute volontairement une obligation naturelle ?", "Il ne peut pas en demander la restitution", ["Il peut en demander la restitution comme d'un indu", "L'exécution est nulle de plein droit", "L'obligation devient une obligation conditionnelle"], "Le paiement volontaire d'une obligation naturelle est valable.", 4),
    ("Le créancier d'une obligation naturelle non exécutée peut-il en obtenir l'exécution forcée ?", "Non, elle n'est pas exigible en justice", ["Oui, comme pour toute obligation", "Oui, mais seulement par voie de saisie", "Oui, si le débiteur est solvable"], "Ce qui la distingue de l'obligation civile est l'absence d'action en exécution.", 3),
    ("Dans une obligation conditionnelle suspensive, que se passe-t-il tant que la condition n'est pas réalisée ?", "Elle est en suspens : le créancier ne peut pas en exiger l'exécution", ["L'obligation est immédiatement exigible", "L'obligation est nulle", "L'obligation est éteinte"], "L'obligation est en suspens jusqu'à l'événement.", 3),
    ("Quel est l'effet de la défaillance d'une condition suspensive ?", "L'obligation est réputée n'avoir jamais existé", ["L'obligation est exécutable sans condition", "L'obligation est transformée en obligation à terme", "L'obligation devient une obligation naturelle"], "Sans la condition, l'obligation disparaît rétroactivement.", 4),
    ("Qu'est-ce qui distingue principalement la condition du terme ?", "La condition est un événement incertain, le terme un événement certain", ["La condition est écrite, le terme est oral", "La condition est légale, le terme est conventionnel", "La condition concerne les biens, le terme les personnes"], "Certitude de l'événement : seul le terme est certain.", 2),
    ("Le décès d'une personne pris comme point de départ d'une obligation est-il un terme ou une condition ?", "Un terme, car il est certain même si sa date est incertaine", ["Une condition, car sa date est incertaine", "Ni l'un ni l'autre", "Une condition potestative"], "Le décès est certain ; seule sa date ne l'est pas.", 4),
    ("Qu'est-ce qu'une condition potestative pure, qui dépend de la seule volonté du débiteur ?", "Une condition qui rend l'engagement illusoire et est en principe nulle", ["Une condition qui dépend d'un tiers", "Une condition qui dépend du hasard", "Une condition toujours valable"], "Le débiteur qui promet « si je le veux » ne s'engage pas réellement.", 5),
    ("Quel est l'effet principal de la solidarité passive entre codébiteurs envers le créancier ?", "Le créancier peut demander la totalité à l'un quelconque d'entre eux", ["Chaque débiteur ne doit que sa part", "Le créancier doit poursuivre d'abord le plus solvable", "Le créancier ne peut poursuivre que le premier désigné"], "Chaque codébiteur solidaire est tenu pour le tout.", 3),
    ("Entre codébiteurs solidaires, que se passe-t-il après que l'un a payé la totalité ?", "Il dispose d'un recours contre les autres pour leur part", ["Il perd tout recours", "Il est subrogé dans les droits du débiteur de la créance", "Les autres sont libérés sans contrepartie"], "La contribution à la dette se règle entre codébiteurs.", 4),
    ("En principe, d'où résulte la solidarité entre débiteurs en matière civile ?", "De la loi ou de la convention des parties", ["De la seule volonté du créancier", "Du seul fait qu'il y a plusieurs débiteurs", "D'une décision automatique du juge"], "La solidarité ne se présume pas en droit civil.", 4),
    ("Dans une obligation alternative, qui choisit en principe la prestation à fournir ?", "Le débiteur", ["Le créancier", "Le juge", "Le notaire"], "Sauf clause contraire, le choix appartient au débiteur.", 4),
    ("Quelle différence y a-t-il entre l'obligation alternative et l'obligation facultative ?", "L'alternative porte sur plusieurs prestations dues, la facultative sur une seule", ["L'alternative est conditionnelle, la facultative à terme", "L'alternative concerne les contrats, la facultative les délits", "Aucune différence"], "Dans l'obligation facultative, une seule prestation est due in obligatione.", 5),
    ("Quel est le critère d'une obligation divisible ?", "Son objet peut être exécuté par parties sans altération", ["Elle a plusieurs créanciers", "Elle est affectée d'un terme", "Elle est de nature civile"], "La divisibilité tient à la nature de la prestation.", 4),
    ("Qu'est-ce qu'une obligation à exécution successive ?", "Une obligation qui s'exécute de façon échelonnée, comme le loyer", ["Une obligation qui ne peut être payée qu'une fois", "Une obligation transmise par héritage", "Une obligation née de plusieurs contrats"], "Exemple type : bail, abonnement.", 2),
    ("Comment s'exécute une obligation de ne pas faire ?", "Par l'abstention du débiteur", ["Par le paiement d'une somme", "Par la remise d'une chose", "Par une action positive"], "Il suffit de s'abstenir.", 1),
    ("Qu'est-ce que l'action oblique ?", "L'action par laquelle un créancier exerce les droits négligés de son débiteur", ["L'action contre un acte frauduleux du débiteur", "L'action en nullité d'un contrat", "L'action en paiement contre une caution"], "Le créancier agit à la place du débiteur inactif.", 5),
    ("Qu'est-ce que l'action paulienne ?", "L'action permettant au créancier de faire déclarer inopposable l'acte frauduleux de son débiteur", ["L'action contre un débiteur négligent", "L'action en répétition de l'indu", "L'action en revendication"], "Elle protège le gage commun contre la fraude.", 5),
    ("Quelle est la condition essentielle de l'action paulienne ?", "Un acte du débiteur accompli en fraude des droits du créancier", ["Une dette garantie par une hypothèque", "Un débiteur âgé", "Une créance prescrite"], "La fraude est la condition centrale.", 5),
    ("Quelle différence y a-t-il entre l'action oblique et l'action paulienne ?", "L'oblique pallie l'inaction du débiteur, la paulienne combat un acte frauduleux", ["L'oblique est pénale, la paulienne civile", "L'oblique vise le paiement, la paulienne la preuve", "Aucune différence de finalité"], "Inaction contre fraude.", 5),
    ("Qu'est-ce que l'action directe ?", "L'action permettant à un créancier d'agir contre le débiteur de son débiteur", ["L'action exercée sans passer par le juge", "L'action en réparation d'un dommage corporel", "L'action contre le gérant d'affaires"], "Exceptionnelle, elle est accordée dans des cas précis.", 5),
    ("À quoi sert l'exception de nullité ?", "À refuser d'exécuter un contrat nul dont l'exécution n'a pas commencé, même passé le délai d'agir", ["Elle permet de réclamer des dommages et intérêts", "Elle suspend la prescription", "Elle valide le contrat"], "Elle est un moyen de défense perpétuel dans la limite de l'exécution.", 5),
    ("Qu'appelle-t-on subrogation personnelle ?", "Le transfert de la créance au tiers qui paie à la place du débiteur", ["L'extinction de la dette par remise gratuite", "Le changement de débiteur par novation", "Le dépôt de la somme chez un officier public"], "Le tiers solvens est substitué au créancier payé.", 4),
], cat="Régime général des obligations", src=O)

mcq(D, "mq-contrat", [
    ("Quelles sont, en droit français, les conditions de validité du contrat énoncées depuis la réforme du droit des contrats ?", "Le consentement, la capacité et un contenu licite et certain", ["L'offre, l'acceptation et la cause", "La signature, la date et le lieu", "La forme écrite, le prix et l'objet"], "La réforme a remplacé l'objet et la cause par le contenu.", 4),
    ("Le silence vaut-il en principe acceptation d'une offre ?", "Non, sauf circonstances particulières ou usages", ["Oui, toujours", "Oui, si l'offre est écrite", "Oui, après un certain délai"], "Le silence ne vaut pas acceptation, sauf exceptions.", 3),
    ("En droit français, que se passe-t-il, en principe, lorsque l'offrant retire l'offre avant l'acceptation et avant le délai fixé ?", "Il engage sa responsabilité, sans pouvoir être contraint de conclure", ["Le contrat est conclu malgré tout", "Rien, l'offre n'oblige jamais", "L'acceptation devient nulle"], "Le retrait fautif peut engager la responsabilité extracontractuelle.", 5),
    ("En droit français, quel est l'effet d'une offre assortie d'un délai ?", "Elle ne peut en principe pas être retirée avant l'expiration du délai", ["Elle peut être retirée à tout moment", "Elle devient une promesse unilatérale", "Elle est automatiquement acceptée"], "Le délai exprime l'engagement de maintien.", 4),
    ("Un contrat est-il formé si l'acceptation modifie les conditions de l'offre ?", "Non, c'est une contre-proposition", ["Oui, avec les conditions de l'offre", "Oui, avec les conditions de l'acceptation", "Oui, si la modification est mineure"], "L'acceptation doit être conforme à l'offre.", 4),
    ("Que protège le devoir précontractuel d'information ?", "Le consentement éclairé de celui qui ignore une information déterminante", ["Le secret de toute information utile", "L'exclusivité du premier négociateur", "La liberté de ne jamais négocier"], "Il évite qu'une partie contracte dans l'ignorance légitime.", 3),
    ("L'erreur sur la substance est-elle une cause de nullité ?", "Oui, lorsqu'elle porte sur une qualité essentielle déterminante pour le consentement", ["Non, jamais", "Oui, même si elle est indifférente à la décision", "Oui, mais seulement si elle est inexcusable"], "Elle doit être déterminante et porter sur la substance.", 3),
    ("Qu'est-ce qui distingue le dol de l'erreur ?", "Le dol résulte de la tromperie intentionnelle du cocontractant", ["Le dol est involontaire", "Le dol ne concerne que les tiers", "Le dol est toujours un défaut de forme"], "L'erreur est une fausse idée, le dol est provoqué.", 2),
    ("En droit français, une victime de violence obtient-elle l'annulation si la contrainte vient d'un tiers ?", "Oui, la violence est un vice même exercée par un tiers", ["Non, seulement si elle vient du cocontractant", "Non, jamais", "Oui, si l'acte est notarié"], "La violence ne dépend pas de l'auteur de la contrainte.", 4),
    ("Qu'est-ce que la crainte révérencielle ?", "La crainte de déplaire à un proche respecté, insuffisante seule à vicier le consentement", ["La peur d'un mal physique", "La crainte d'un procès", "La peur de la perte d'un emploi précaire"], "Elle ne constitue pas, seule, une violence.", 5),
    ("Quels sont les effets de la nullité d'un contrat à exécution instantanée ?", "L'anéantissement rétroactif et les restitutions", ["L'anéantissement pour l'avenir seulement", "La survie du contrat avec ajustement du prix", "La mise en demeure du débiteur"], "La nullité efface le contrat dès l'origine.", 3),
    ("Peut-on confirmer un contrat nul pour violation de l'ordre public ?", "Non, la nullité absolue ne se couvre pas par confirmation", ["Oui, par simple silence", "Oui, par l'accord des parties", "Oui, par un juge uniquement"], "La nullité absolue protège l'intérêt général.", 5),
    ("Qui peut en principe invoquer la nullité relative ?", "La personne que la règle violée visait à protéger", ["Tout intéressé", "Le ministère public seul", "Le cocontractant fautif"], "Elle est ouverte à la partie protégée.", 3),
    ("Qui peut en principe invoquer la nullité absolue ?", "Toute personne justifiant d'un intérêt, ainsi que le ministère public", ["La seule partie lésée", "Le seul cocontractant de bonne foi", "Le seul notaire"], "L'intérêt général justifie une large ouverture.", 4),
    ("Qu'est-ce qu'un contrat d'adhésion ?", "Un contrat dont les clauses essentielles sont fixées à l'avance par une partie", ["Un contrat conclu avec un mineur", "Un contrat négocié clause par clause", "Un contrat verbal"], "L'adhérent accepte les clauses sans les négocier.", 2),
    ("Dans un contrat d'adhésion, comment l'interprète-t-on en cas de doute ?", "Contre celui qui a proposé les clauses", ["Toujours en faveur du plus fort", "Toujours en faveur du plus âgé", "Selon la lettre exclusive"], "Les clauses obscures s'interprètent contre le prédisposant.", 4),
    ("Quelle sanction frappe une clause créant un déséquilibre significatif dans un contrat d'adhésion (droit français) ?", "Elle est réputée non écrite", ["Le contrat est résolu entier", "Le contrat est caduc", "La clause reste valable"], "La clause abusive est écartée sans anéantir le contrat.", 5),
    ("La vente est-elle, en principe, un contrat consensuel ?", "Oui, elle est parfaite par l'accord sur la chose et sur le prix", ["Non, elle exige toujours un acte notarié", "Non, elle exige la remise de la chose", "Non, elle exige toujours un écrit"], "L'accord suffit, sauf exceptions pour certains biens.", 3),
    ("Un contrat est-il valable lorsque son prix n'est pas fixé au départ mais déterminable ?", "Oui, si le prix peut être déterminé sans nouvel accord", ["Non, jamais", "Oui, uniquement si la forme est solennelle", "Oui, uniquement pour les contrats gratuits"], "Le caractère déterminable suffit.", 4),
    ("Un contrat portant sur une chose future est-il possible ?", "Oui, si la chose est déterminable et licite", ["Non, jamais", "Oui, seulement s'il s'agit d'une succession", "Oui, mais seulement en droit commercial"], "La chose future peut faire l'objet d'un contrat.", 4),
    ("Peut-on en principe contracter sur la succession d'une personne vivante ?", "Non, ce pacte sur succession future est en principe prohibé", ["Oui, sans limite", "Oui, par acte sous seing privé", "Oui, avec l'accord du fisc"], "Les pactes sur succession future sont limités par la loi.", 5),
    ("Le principe de la liberté contractuelle comprend-il la liberté de choisir son cocontractant ?", "Oui, dans la limite de l'ordre public et de la loi", ["Non, jamais", "Oui, sans aucune limite", "Non, seulement pour les contrats gratuits"], "Elle est limitée par l'ordre public et par certaines interdictions.", 2),
    ("Qu'est-ce que la clause de réserve de propriété ?", "Une clause retardant le transfert de propriété jusqu'au paiement intégral", ["Une clause garantissant la propriété à tous les tiers", "Une clause interdisant la vente", "Une clause d'indexation des prix"], "Le vendeur reste propriétaire jusqu'au paiement.", 4),
    ("La promesse unilatérale de vente lie-t-elle le bénéficiaire à acheter ?", "Non, il a seulement une option d'achat", ["Oui, dès la signature", "Oui, après un délai", "Oui, si le prix est fixé"], "Il décide de lever l'option ou non.", 3),
    ("En droit français, la promesse synallagmatique de vente vaut-elle, en principe, vente ?", "Oui, lorsque les parties sont d'accord sur la chose et sur le prix", ["Non, jamais", "Non, c'est toujours un simple projet", "Oui, mais seulement pour les immeubles"], "L'accord réciproque suffit en droit de la vente.", 4),
    ("Dans le régime français de l'imprévision, que doit faire la partie touchée avant de saisir le juge ?", "Demander une renégociation à son cocontractant en continuant d'exécuter", ["Cesser immédiatement d'exécuter", "Doubler le prix de sa prestation", "Notifier la résolution au tiers"], "Dans le régime de l'imprévision, la renégociation précède la saisine du juge.", 5),
    ("À quoi sert la clause de hardship dans les contrats ?", "À prévoir l'adaptation du contrat en cas de bouleversement de l'équilibre économique", ["À garantir la livraison", "À fixer la juridiction compétente", "À interdire toute modification"], "Elle organise la renégociation en cas de difficulté économique.", 5),
    ("Une clause attributive de compétence est-elle toujours valable ?", "Non, elle est soumise à des conditions et limites légales", ["Oui, toujours", "Non, jamais", "Oui, si elle est orale"], "Elle ne s'applique pas dans tous les cas.", 4),
    ("Qu'est-ce qu'une clause compromissoire ?", "Une clause qui soumet les litiges futurs à l'arbitrage", ["Une clause fixant une peine", "Une clause garantissant le prix", "Une clause d'indexation"], "Les parties choisissent l'arbitrage à l'avance.", 3),
    ("Qu'est-ce qu'un compromis d'arbitrage ?", "Une convention soumettant à l'arbitrage un litige déjà né", ["Une clause pour un litige à venir", "Un jugement d'une cour d'appel", "Un acte de vente"], "Il vise un litige existant.", 4),
    ("Que doit éviter le juge devant les termes clairs et précis d'un contrat ?", "Les dénaturer par une interprétation", ["Les appliquer à la lettre dans tous les cas", "Se référer aux usages", "Les citer dans sa décision"], "La dénaturation est sanctionnée.", 5),
    ("Quelle est la différence entre simple détention et possession pour un locataire ?", "Le locataire détient pour le compte du propriétaire sans se comporter en propriétaire", ["Le locataire possède comme propriétaire", "Le locataire n'a aucun droit", "Le locataire peut usucaper contre le bailleur"], "La détention précaire exclut la possession utile.", 4),
    ("Que protège le principe de relativité des conventions pour les tiers ?", "Ils ne sont pas obligés par un contrat auquel ils n'ont pas consenti", ["Ils sont toujours obligés", "Ils profitent toujours du contrat", "Ils héritent des parties"], "Le contrat ne crée pas d'obligations à la charge des tiers.", 3),
    ("Un tiers peut-il engager la responsabilité d'un contractant à raison de l'inexécution d'un contrat dont il n'est pas partie ?", "Oui, sur le terrain extracontractuel, s'il prouve un dommage causé par ce manquement", ["Non, jamais", "Oui, sur le terrain contractuel", "Oui, par action en nullité"], "Le manquement contractuel est une faute à l'égard des tiers.", 5),
    ("Comment un contrat peut-il, en principe, être modifié au titre de la force obligatoire ?", "Par le consentement mutuel des parties ou pour les causes que la loi autorise", ["Par la volonté d'une seule partie", "Par une partie si elle l'estime utile", "Par le simple écoulement du temps"], "Les parties sont liées comme par la loi.", 3),
    ("Qu'est-ce que le principe de bonne foi dans l'exécution du contrat ?", "L'exigence de loyauté et de collaboration dans l'exécution", ["L'interdiction de contracter avec un mineur", "Le droit de ne jamais exécuter", "Une règle sur la preuve"], "Les parties doivent exécuter loyalement.", 2),
    ("La bonne foi permet-elle au juge de réécrire le contrat pour rétablir l'équilibre économique ?", "Non, en principe, sauf mécanisme légal ou conventionnel", ["Oui, toujours", "Oui, pour les contrats gratuits uniquement", "Oui, si une partie est en difficulté"], "La bonne foi ne permet pas de modifier le contrat.", 5),
    ("Un mineur non émancipé peut-il en principe passer seul tout contrat ?", "Non, ses actes sont soumis à la représentation, sauf actes de la vie courante", ["Oui, tous", "Oui, mais seulement à titre gratuit", "Non, jamais, même pour les actes courants"], "Les actes courants, conformes à l'usage, sont possibles.", 3),
    ("Un majeur sous tutelle peut-il en principe passer seul des actes importants ?", "Non, il est représenté par son tuteur", ["Oui, avec son voisin", "Oui, avec un témoin", "Oui, après avis du fisc"], "La tutelle est un régime de représentation.", 3),
], cat="Contrat", src=O)

mcq(D, "mq-resp", [
    ("Quelles sont les trois conditions classiques de la responsabilité civile pour faute ?", "Une faute, un dommage et un lien de causalité", ["Une faute, une sanction et une peine", "Un contrat, un dommage et un jugement", "Un dommage, une mise en demeure et un délai"], "Ce triptyque est le socle de la responsabilité pour faute.", 1),
    ("Quelle différence y a-t-il entre responsabilité civile et responsabilité pénale ?", "La civile tend à réparer le dommage, la pénale à sanctionner l'infraction", ["La civile punit, la pénale répare", "La civile concerne l'État, la pénale les particuliers", "Elles ont exactement le même but"], "Réparation d'un côté, répression de l'autre.", 2),
    ("Un même fait peut-il engager à la fois la responsabilité civile et pénale de son auteur ?", "Oui, car les deux responsabilités ont des finalités différentes", ["Non, jamais", "Oui, mais seulement si le dommage est matériel", "Non, la pénale absorbe la civile"], "Elles peuvent se cumuler.", 2),
    ("Peut-on en principe cumuler responsabilité contractuelle et extracontractuelle pour un même dommage ?", "Non, le principe du non-cumul impose la responsabilité contractuelle entre contractants", ["Oui, au choix de la victime", "Oui, si le contrat est nul", "Non, la responsabilité extracontractuelle est toujours exclue"], "Le non-cumul s'applique entre cocontractants.", 4),
    ("Le dommage doit-il être certain pour être réparé ?", "Oui, même futur s'il est la prolongation certaine d'un état de choses actuel", ["Non, un dommage purement hypothétique est réparé", "Oui, mais seulement s'il est déjà survenu", "Non, la faute suffit"], "Le préjudice futur certain est réparable.", 4),
    ("La victime doit-elle, en principe, prouver la faute en cas de responsabilité pour faute ?", "Oui, la faute doit être prouvée par la victime", ["Non, elle est toujours présumée", "Non, c'est au défendeur de la prouver", "Oui, mais seulement devant le juge pénal"], "La charge de la preuve pèse sur la victime.", 2),
    ("Qu'est-ce que la faute intentionnelle ?", "Un comportement par lequel l'auteur a voulu le dommage tel qu'il s'est produit", ["Une simple imprudence", "Une négligence légère", "Un acte accompli par un mineur"], "L'intention porte sur le dommage lui-même.", 3),
    ("La victime qui a commis une faute peut-elle être indemnisée ?", "Oui, mais l'indemnité peut être réduite proportionnellement à sa faute", ["Non, jamais", "Oui, intégralement dans tous les cas", "Oui, mais seulement par l'assureur"], "La faute de la victime exonère partiellement ou totalement.", 3),
    ("Que faut-il prouver pour retenir la responsabilité du fait des choses ?", "Que la chose, sous la garde du défendeur, a causé le dommage", ["Que la chose était défectueuse", "Que le gardien était de mauvaise foi", "Que la chose a été volée"], "Le gardien répond du fait de la chose, sans avoir à établir une faute.", 4),
    ("Qui est le gardien d'une chose ?", "Celui qui en a l'usage, la direction et le contrôle", ["Toujours son propriétaire", "Celui qui l'a fabriquée", "Celui qui l'a vendue en dernier"], "La garde est une notion de fait.", 3),
    ("Un propriétaire dont la voiture est volée reste-t-il gardien au moment de l'accident causé par le voleur ?", "Non, le voleur a la garde de fait", ["Oui, car il reste propriétaire", "Oui, car il doit surveiller son véhicule", "Il y a garde commune avec le voleur"], "La garde échappe au propriétaire dépossédé contre sa volonté.", 5),
    ("En quoi consiste la responsabilité du fait d'autrui ?", "À répondre du dommage causé par une personne dont on a la charge", ["À répondre du dommage causé par sa propre faute", "À répondre du dommage causé par une chose", "À répondre d'un contrat conclu par un tiers"], "Parents, commettants, associations éducatives en offrent des exemples.", 2),
    ("L'employeur est-il responsable du dommage causé par son salarié agissant dans le cadre de ses fonctions ?", "Oui, en principe, en tant que commettant", ["Non, seul le salarié répond", "Oui, mais seulement si le salarié est insolvable", "Non, sauf accord écrit"], "Le commettant répond de ses préposés.", 3),
    ("Que peut faire le commettant qui a indemnisé la victime à la place de son préposé ?", "Exercer une action récursoire contre le préposé dans certaines limites", ["Rien du tout", "Demander une sanction pénale automatique", "Se faire rembourser par la victime"], "Il garde un recours, limité selon la nature de la faute.", 5),
    ("Le parent est-il responsable du dommage causé par son enfant mineur vivant avec lui ?", "Oui, en principe, du seul fait de l'autorité parentale exercée", ["Non, jamais", "Oui, uniquement si l'enfant est majeur", "Non, sauf faute prouvée de l'enfant"], "La responsabilité des parents est une responsabilité de plein droit.", 3),
    ("Qu'est-ce qu'une responsabilité de plein droit ?", "Une responsabilité qui ne dépend pas de la preuve d'une faute du responsable", ["Une responsabilité prononcée par le ministère public", "Une responsabilité exigeant la bonne foi de la victime", "Une responsabilité qui échappe à tout recours"], "On peut s'en exonérer par la cause étrangère seulement.", 4),
    ("Qu'est-ce que le fait générateur d'une responsabilité ?", "L'événement qui engage la responsabilité, comme une faute ou le fait d'une chose", ["Le montant de l'indemnité", "Le jugement de condamnation", "La mise en demeure"], "C'est l'élément déclencheur.", 3),
    ("Que répare le préjudice d'affection ?", "La souffrance morale d'un proche à la suite du dommage subi par la victime", ["Le coût des soins", "La perte de revenus", "Les frais de justice"], "C'est un préjudice moral par ricochet.", 3),
    ("Que couvre le gain manqué ?", "Le bénéfice dont la victime a été privée par le fait dommageable", ["Les dépenses déjà engagées", "Le montant des frais de justice", "Une peine complémentaire"], "À côté de la perte subie, c'est un chef de préjudice matériel.", 3),
    ("Que désigne la perte subie, en matière de réparation ?", "La diminution effective du patrimoine de la victime", ["Le bénéfice espéré non réalisé", "Le préjudice moral exclusivement", "Une sanction du responsable"], "Par opposition au gain manqué.", 3),
    ("Que ne peut en principe pas écarter une clause exonératoire de responsabilité ?", "La faute lourde ou dolosive du débiteur", ["La force majeure", "La faute légère", "Le fait du créancier"], "On ne s'exonère pas de sa faute lourde ou de son dol.", 5),
    ("La force majeure exonère-t-elle en matière de responsabilité contractuelle ?", "Oui, elle exonère si ses conditions sont réunies", ["Non, jamais", "Oui, uniquement en matière pénale", "Oui, mais seulement pour les obligations de moyens"], "Elle rompt le lien de causalité ou rend l'exécution impossible.", 3),
    ("Que doit démontrer le débiteur d'une obligation de moyens pour échapper à la responsabilité ?", "Qu'il a agi avec la diligence requise", ["Qu'il a atteint un résultat plus important", "Qu'il a informé la victime", "Qu'il a payé un tiers"], "L'absence de faute suffit.", 3),
    ("Qu'est-ce que la réparation en nature ?", "La remise en état ou une prestation équivalente plutôt qu'une somme d'argent", ["Le paiement en marchandises uniquement", "Le paiement d'intérêts", "La sanction d'un délit"], "Elle s'oppose à la réparation par équivalent.", 3),
    ("Qu'est-ce que la réparation par équivalent ?", "L'indemnité en argent compensant le préjudice", ["La remise en état du bien", "L'exécution forcée de la prestation", "Une sanction pénale"], "Dommages et intérêts.", 2),
    ("Le droit à réparation d'un décès peut-il appartenir aux proches ?", "Oui, aux proches qui subissent un préjudice personnel du fait du décès", ["Non, jamais", "Oui, seulement à l'État", "Oui, seulement aux héritiers réservataires"], "Le dommage par ricochet est personnel.", 4),
    ("Peut-on être responsable d'un dommage causé par un animal dont on se sert, sans en être propriétaire ?", "Oui, celui qui en a la garde ou s'en sert répond du dommage", ["Non, seul le propriétaire", "Non, sauf faute grave", "Oui, mais seulement si l'animal est sauvage"], "La garde, comme pour les choses, est déterminante.", 4),
    ("Quelle est la différence entre faute lourde et faute légère ?", "La faute lourde dénote une négligence d'une extrême gravité confinant au dol", ["La faute lourde est toujours intentionnelle", "La faute légère entraîne une peine", "La faute lourde est involontaire et sans gravité"], "Elle est proche du dol dans ses effets.", 4),
], cat="Responsabilité civile", src=O)

mcq(D, "mq-ext", [
    ("Qu'est-ce qui distingue la gestion d'affaires du mandat ?", "Dans le mandat, le mandataire agit sur ordre du mandant ; dans la gestion d'affaires, spontanément", ["Le mandat est gratuit", "La gestion d'affaires est un contrat solennel", "Le mandat est un quasi-contrat"], "Mandat : contrat ; gestion : quasi-contrat.", 3),
    ("Quelle est la principale obligation du gérant d'affaires ?", "Gérer avec diligence l'affaire qu'il a entreprise", ["Verser une indemnité au maître", "Obtenir l'accord écrit du maître", "Céder le bien au maître"], "Il doit apporter à la gestion les soins d'une personne diligente.", 4),
    ("Le maître de l'affaire doit-il rembourser les dépenses utiles du gérant ?", "Oui, si la gestion a été utile", ["Non, jamais", "Oui, même si la gestion a été nuisible", "Oui, mais jamais les frais"], "Le gérant est indemnisé des dépenses utiles.", 3),
    ("En cas de paiement de l'indu, que peut réclamer celui qui a payé ?", "La restitution de ce qu'il a payé sans en être débiteur", ["Des dommages et intérêts supérieurs", "La nullité du contrat de base", "Rien, car le paiement est définitif"], "Action en répétition de l'indu.", 2),
    ("Quelle est la condition essentielle de l'action pour enrichissement injustifié ?", "Un enrichissement et un appauvrissement corrélatifs sans cause légitime", ["Une faute du défendeur", "Un contrat écrit", "Une mise en demeure préalable"], "Ni faute ni contrat n'est requis.", 4),
    ("Que peut obtenir celui qui exerce l'action pour enrichissement injustifié ?", "Une indemnité limitée à la moindre des deux sommes : enrichissement et appauvrissement", ["Le total de l'enrichissement dans tous les cas", "Des dommages et intérêts punitifs", "L'annulation de tout contrat"], "L'indemnité est plafonnée par le plus faible des deux montants.", 5),
    ("Pourquoi l'action pour enrichissement injustifié est-elle dite subsidiaire ?", "Parce qu'elle n'est ouverte qu'à défaut d'une autre action disponible", ["Parce qu'elle ne concerne que les petites sommes", "Parce qu'elle est portée devant un juge spécial", "Parce qu'elle ne s'applique qu'à l'État"], "Elle comble les lacunes du droit.", 5),
    ("Dans la compensation légale, comment opèrent les dettes réciproques ?", "De plein droit, dès qu'elles coexistent et remplissent les conditions", ["Seulement après jugement", "Seulement par accord écrit", "Seulement après notification au notaire"], "L'extinction a lieu automatiquement.", 4),
    ("Quelles qualités doivent présenter les dettes pour une compensation légale ?", "Certaines, liquides, exigibles et fongibles entre les mêmes personnes", ["Gagées sur un bien", "Nées de contrats différents seulement", "Constatées par un acte authentique"], "Ce sont les conditions classiques.", 5),
    ("Que remplace la novation par changement de débiteur ?", "L'ancien débiteur, libéré, par un nouveau débiteur", ["Le créancier par un autre", "L'objet de la dette sans changer de parties", "La cause du contrat"], "La dette est éteinte et remplacée.", 4),
    ("La novation se présume-t-elle ?", "Non, la volonté de nover doit être certaine", ["Oui, toujours", "Oui, si elle est notariée", "Oui, si les parties sont commerçantes"], "L'intention d'éteindre l'ancienne obligation doit résulter clairement des actes.", 4),
    ("Quelle est la conséquence de la novation à l'égard des sûretés de l'ancienne obligation ?", "Elles s'éteignent en principe avec l'ancienne obligation", ["Elles garantissent toujours la nouvelle", "Elles sont transmises automatiquement", "Elles deviennent des privilèges"], "Elles s'éteignent, sauf réserve convenue.", 5),
    ("Que se passe-t-il lorsque le créancier renonce gratuitement à sa créance ?", "Il y a remise de dette, qui libère le débiteur", ["Il y a novation", "Il y a compensation", "Il y a confusion"], "La remise de dette est une renonciation volontaire.", 2),
    ("La remise de dette consentie au débiteur principal libère-t-elle les cautions ?", "Oui, par application du caractère accessoire du cautionnement", ["Non, les cautions restent engagées", "Oui, mais seulement la moitié", "Non, sauf accord des cautions"], "L'accessoire suit le principal.", 5),
    ("Quel est l'effet de la prescription extinctive sur l'obligation ?", "Elle prive le créancier du droit d'agir en justice pour l'exiger", ["Elle fait disparaître la dette dans tous ses effets, y compris moraux", "Elle rend la dette conditionnelle", "Elle transforme la dette en dation"], "Le droit d'agir est perdu ; un paiement volontaire reste valable.", 4),
    ("En droit français, le juge peut-il soulever d'office la prescription ?", "En principe non, c'est un moyen que le défendeur doit invoquer", ["Oui, toujours", "Oui, en matière contractuelle uniquement", "Oui, dans tous les procès"], "Le juge ne la relève pas d'office dans la règle générale.", 5),
    ("Quelle distinction y a-t-il entre prescription extinctive et acquisitive ?", "L'une éteint un droit d'agir, l'autre fait acquérir un droit par la possession", ["L'une concerne les meubles, l'autre les immeubles", "L'une est de droit public, l'autre de droit privé", "Elles sont synonymes"], "Perdre ou gagner un droit par le temps.", 3),
    ("En droit français, peut-on renoncer à une prescription non encore acquise ?", "Non, la renonciation anticipée est en principe prohibée", ["Oui, librement", "Oui, par acte notarié uniquement", "Oui, si l'autre partie est commerçant"], "On ne peut renoncer qu'à une prescription acquise.", 5),
    ("Que fait l'interruption de la prescription ?", "Elle efface le délai écoulé et fait courir un nouveau délai", ["Elle arrête le délai sans l'effacer", "Elle supprime toute dette", "Elle prolonge la dette"], "L'interruption remet les compteurs à zéro.", 4),
    ("Que fait la suspension de la prescription ?", "Elle arrête provisoirement le cours du délai sans effacer le temps déjà écoulé", ["Elle efface le temps écoulé", "Elle éteint la dette", "Elle fait renaître la dette"], "Le temps repart là où il s'était arrêté.", 4),
    ("Peut-on obliger le créancier à accepter un paiement partiel ?", "Non, en principe, le débiteur ne peut forcer un paiement partiel", ["Oui, toujours", "Oui, pour les petites dettes", "Oui, si le débiteur est de bonne foi"], "Le créancier peut refuser un paiement en plusieurs fois contre son gré.", 4),
    ("Que prévoit la règle de l'imputation des paiements ?", "L'affectation du paiement à l'une de plusieurs dettes du même débiteur", ["La transformation d'un paiement en prêt", "La répartition entre les créanciers", "L'extinction de toutes les dettes"], "Le débiteur peut désigner la dette qu'il acquitte.", 4),
    ("À qui appartient en principe le choix de la dette payée lorsqu'il y en a plusieurs ?", "Au débiteur qui paie", ["Au créancier", "Au juge", "Au notaire"], "Le débiteur désigne, sauf impossibilité.", 4),
    ("Que prouve la quittance ?", "Le paiement fait par le débiteur", ["La formation du contrat", "L'identité des parties", "La bonne foi du créancier"], "Écrit constatant le paiement.", 1),
    ("Qu'est-ce que la confusion éteint ?", "L'obligation lorsque créancier et débiteur ne forment plus qu'une personne", ["La preuve du contrat", "Une servitude toujours", "Le droit de propriété"], "Impossible d'être son propre créancier.", 3),
    ("Qu'est-ce que l'exigibilité d'une dette ?", "Le droit pour le créancier d'en demander immédiatement le paiement", ["La preuve de la dette", "Le montant de la dette", "La date de naissance de la dette"], "Elle s'apprécie à l'échéance.", 3),
    ("Que doit offrir le débiteur qui se libère par la consignation ?", "La somme due, déposée auprès d'un tiers après offre au créancier", ["Un bien différent", "Une caution", "Une promesse de payer plus tard"], "Elle suit le refus du créancier de recevoir.", 5),
], cat="Extinction des obligations", src=O)

mcq(D, "mq-preuve", [
    ("Sur qui pèse en principe la charge de la preuve d'une obligation ?", "Sur celui qui s'en prétend créancier", ["Sur le débiteur", "Sur le juge", "Sur le greffier"], "Celui qui réclame l'exécution doit prouver l'obligation.", 1),
    ("Après que le créancier a prouvé l'obligation, que doit prouver le débiteur qui se prétend libéré ?", "Le paiement ou le fait qui a éteint l'obligation", ["Rien", "Sa bonne foi", "L'existence d'un dommage"], "La charge de la preuve se renverse.", 2),
    ("Comment peut-on prouver un fait juridique, en principe ?", "Par tout moyen", ["Uniquement par écrit", "Uniquement par témoin", "Uniquement par expertise"], "La preuve est libre pour les faits.", 2),
    ("Comment doit-on, en principe, prouver un acte juridique au-delà d'un certain montant ?", "Par écrit", ["Par simple témoignage", "Par rumeur", "Par un seul aveu extrajudiciaire"], "L'écrit est exigé à partir d'un seuil fixé par les textes.", 3),
    ("Qu'est-ce qu'une preuve préconstituée ?", "Une preuve établie à l'avance pour servir en cas de litige, comme un écrit", ["Une preuve obtenue après le litige", "Une preuve apportée par l'adversaire", "Une preuve par présomption"], "L'écrit est préconstitué.", 3),
    ("Qu'est-ce qui distingue l'acte authentique de l'acte sous seing privé sur le plan probatoire ?", "L'acte authentique fait foi jusqu'à inscription de faux de ce que l'officier a constaté lui-même", ["L'acte authentique est toujours moins fort", "L'acte sous seing privé fait foi à tout jamais", "Il n'y a aucune différence"], "La force probante de l'acte authentique est supérieure.", 5),
    ("Que doit faire l'adversaire qui conteste l'écriture d'un acte sous seing privé ?", "Désavouer son écriture ou sa signature, ce qui engage la vérification", ["Rien", "Payer immédiatement", "Obtenir l'avis du fisc"], "Le désaveu lance la vérification d'écriture.", 4),
    ("Quelle valeur peut avoir une signature électronique fiable ?", "Elle peut avoir la même valeur qu'une signature manuscrite", ["Elle n'a aucune valeur", "Elle ne vaut que devant un notaire", "Elle est exclue des contrats civils"], "Les textes reconnaissent la valeur de l'écrit électronique sous conditions.", 3),
    ("Qu'est-ce qu'un aveu extrajudiciaire ?", "Une reconnaissance faite en dehors du procès, laissée à l'appréciation du juge", ["Une déclaration faite à l'audience", "Une preuve écrite d'un notaire", "Un serment"], "Il est apprécié librement par le juge.", 4),
    ("L'aveu judiciaire peut-il être rétracté librement ?", "Non, il est en principe irrévocable, sauf erreur de fait", ["Oui, à tout moment", "Oui, après un jugement", "Oui, par simple lettre"], "Il lie son auteur.", 5),
    ("Une présomption simple peut-elle être renversée ?", "Oui, par la preuve contraire", ["Non, jamais", "Oui, mais seulement par aveu", "Oui, mais seulement par le juge"], "Définition de la présomption simple.", 2),
    ("En droit français, peut-on prouver contre un écrit par témoins ?", "En principe non, sauf commencement de preuve par écrit ou impossibilité de preuve écrite", ["Oui, toujours", "Non, même avec un commencement de preuve", "Oui, mais seulement si le juge pénal l'accepte"], "La règle de preuve par écrit connaît des exceptions.", 5),
    ("Qu'appelle-t-on impossibilité morale de se procurer un écrit ?", "Une situation où il est inhabituel de dresser un écrit, par exemple entre proches", ["L'oubli de l'écrit", "La perte volontaire d'un acte", "Le refus du notaire"], "Elle justifie une preuve par tout moyen.", 5),
    ("Que signifie la règle selon laquelle nul ne peut se constituer de preuve à soi-même ?", "L'impossibilité de s'appuyer sur ses propres écrits unilatéraux comme preuve contre l'autre", ["L'interdiction de plaider seul", "L'interdiction de contracter avec soi-même", "L'obligation de se faire assister"], "Une preuve unilatérale n'a pas en principe de force probante contre autrui.", 5),
    ("Qu'est-ce que la copie fiable d'un acte ?", "Une reproduction ayant la même valeur que l'original sous conditions de fidélité et de durabilité", ["Une copie manuscrite quelconque", "Un simple résumé", "Un acte secret"], "Elle peut remplacer l'original si elle est fiable.", 4),
    ("Quel est le rôle de l'expertise judiciaire ?", "Éclairer le juge sur une question technique par l'avis d'un expert", ["Remplacer le jugement", "Donner force exécutoire au jugement", "Fixer la peine"], "Le juge n'est pas lié par l'avis.", 3),
    ("Quelle est la conséquence de la chose jugée au civil entre les mêmes parties ?", "Interdire de rejuger la même demande fondée sur la même cause", ["Autoriser un second procès identique", "Écarter toutes les voies de recours", "Empêcher tout recours en cassation"], "Triple identité : parties, objet, cause.", 4),
    ("Quelles sont les trois identités exigées par l'autorité de la chose jugée ?", "Identité de parties, d'objet et de cause", ["Identité de juge, de lieu et de date", "Identité de preuve, de jugement et de peine", "Identité d'avocat, de partie et d'objet"], "Triple identité classique.", 4),
], cat="Preuve des obligations", src=O)

mcq(D, "mq-biens", [
    ("Quelle différence essentielle y a-t-il entre un droit réel et un droit personnel ?", "Le droit réel porte directement sur une chose, le droit personnel sur une prestation d'une personne", ["Le droit réel est temporaire, le droit personnel perpétuel", "Le droit réel est écrit, le droit personnel oral", "Le droit réel se prouve par témoins, le personnel par écrit"], "Rapport direct à la chose contre rapport entre personnes.", 2),
    ("La propriété est-elle un droit réel ou un droit personnel ?", "Un droit réel", ["Un droit personnel", "Une obligation naturelle", "Un droit de créance"], "Elle porte directement sur la chose.", 1),
    ("Le bail confère-t-il au locataire un droit réel ou personnel, selon l'analyse classique ?", "Un droit personnel de jouissance contre le bailleur", ["Un droit réel principal", "Un droit réel accessoire", "Un droit de propriété partiel"], "Analyse classique : droit de créance de jouissance.", 4),
    ("Que permet le droit de suite au créancier hypothécaire ?", "Saisir l'immeuble hypothéqué entre les mains du tiers acquéreur", ["Exiger un paiement immédiat de toute dette", "Empêcher toute vente de l'immeuble", "Obtenir la propriété de l'immeuble"], "Il suit le bien en quelque main qu'il passe.", 4),
    ("Que permet le droit de préférence au créancier titulaire d'une sûreté réelle ?", "Être payé avant les créanciers chirographaires sur le prix du bien", ["Empêcher les autres créanciers d'agir", "Obtenir un intérêt supérieur", "Transférer sa créance"], "Il prime les créanciers sans garantie.", 3),
    ("Qu'est-ce qu'un créancier chirographaire ?", "Un créancier sans garantie particulière, titulaire seulement du gage commun", ["Un créancier hypothécaire", "Un créancier privilégié", "Un créancier gagiste"], "Il n'a aucune sûreté ni privilège.", 3),
    ("Que signifie le caractère absolu du droit de propriété ?", "Le propriétaire dispose du plus large pouvoir sur la chose, dans les limites légales", ["Il peut tout faire sans aucune limite", "Il peut violer les droits des voisins", "Il peut ignorer l'ordre public"], "Absolu ne veut pas dire illimité.", 3),
    ("Quelle est la limite de la propriété posée par l'interdiction de l'abus de droit ?", "Un usage dans le seul but de nuire à autrui est fautif", ["Aucune limite", "L'usage de la chose est interdit aux propriétaires", "Un usage supérieur à la moyenne est nul"], "Un droit ne doit pas être exercé pour nuire.", 4),
    ("Que permet l'accession au propriétaire d'un fonds ?", "Acquérir ce qui s'unit ou s'incorpore à son bien", ["Vendre son bien sans formalité", "Annuler tout bail", "Percevoir les fruits de l'État"], "Ce qui s'ajoute à la chose appartient à son propriétaire.", 3),
    ("En droit français, qui acquiert les fruits d'un bien possédé de bonne foi ?", "Le possesseur de bonne foi", ["Toujours le propriétaire", "L'État", "Le notaire"], "Le possesseur de bonne foi fait siens les fruits.", 4),
    ("Que doit faire en principe l'usufruitier à l'extinction de l'usufruit ?", "Restituer la chose au nu-propriétaire", ["La conserver définitivement", "La détruire", "La vendre au profit de l'État"], "L'usufruit est temporaire.", 2),
    ("L'usufruitier peut-il vendre la pleine propriété du bien grevé ?", "Non, il ne peut disposer que de son droit d'usufruit", ["Oui, librement", "Oui, avec son voisin", "Oui, mais seulement par testament"], "Il n'a pas l'abusus sur la chose.", 3),
    ("En droit français, qui supporte en principe les grosses réparations d'un bien grevé d'usufruit ?", "Le nu-propriétaire", ["L'usufruitier", "Le locataire", "Le notaire"], "Les réparations d'entretien incombent à l'usufruitier.", 5),
    ("Un usufruit peut-il être perpétuel ?", "Non, il s'éteint au plus tard au décès de l'usufruitier personne physique", ["Oui, toujours", "Oui, pour les immeubles", "Oui, par testament"], "L'usufruit est viager au plus.", 4),
    ("À quoi sert une servitude de passage ?", "À permettre l'accès d'un fonds enclavé à la voie publique", ["À interdire la vente du fonds", "À percevoir un loyer", "À garantir une dette"], "La servitude de passage désenclave un fonds.", 3),
    ("Une servitude est-elle un droit réel ?", "Oui, elle est attachée à un fonds et non à une personne", ["Non, un droit personnel", "Non, une obligation conjointe", "Oui, mais attachée à une personne"], "Elle suit le fonds.", 4),
    ("En droit français, dans l'indivision, chaque indivisaire a-t-il le droit d'en sortir ?", "Oui, en principe nul ne peut être contraint de demeurer dans l'indivision", ["Non, jamais", "Oui, avec l'accord de tous", "Non, sauf décès"], "Principe : le partage peut toujours être demandé.", 4),
    ("À quoi sert le partage ?", "À mettre fin à l'indivision en divisant les biens entre indivisaires", ["À créer une indivision", "À ouvrir une succession", "À transférer à l'État"], "Il matérialise les droits abstraits des indivisaires.", 2),
    ("Que doit réunir une possession pour mener à l'usucapion ?", "Être continue, paisible, publique et non équivoque", ["Être discrète et occasionnelle", "Être violente et rapide", "Être précaire et fractionnée"], "Ces qualités sont celles de la possession utile.", 3),
    ("Un locataire peut-il acquérir par usucapion le bien loué contre son bailleur ?", "Non, il détient à titre précaire, sauf interversion du titre", ["Oui, toujours", "Oui, après un certain temps", "Oui, s'il paie le loyer"], "La détention précaire exclut la possession utile.", 5),
    ("Qu'est-ce que l'interversion de titre ?", "Le changement de cause de la détention, par laquelle le détenteur se comporte en propriétaire", ["La vente du bien", "L'échange de deux biens", "L'hypothèque d'un bien"], "Elle transforme la détention en possession.", 5),
    ("La possession est-elle un droit ou un fait ?", "Un fait protégé par le droit", ["Un droit réel principal", "Un droit personnel", "Une obligation"], "La possession est un état de fait générateur d'effets de droit.", 3),
    ("Que protège l'action possessoire ?", "La possession en tant que telle, sans examen du droit de propriété", ["Le titre de propriété", "L'état civil", "L'hypothèque"], "Elle tranche la question de la possession.", 4),
    ("Que doit prouver le demandeur à l'action en revendication ?", "Son droit de propriété sur la chose", ["Sa bonne foi", "Le trouble subi", "La possession seule"], "Le revendiquant doit établir son droit.", 3),
    ("Que protège la règle « en fait de meubles, la possession vaut titre » en droit français ?", "L'acquéreur de bonne foi d'un meuble corporel", ["Le propriétaire d'un immeuble", "Le vendeur de mauvaise foi", "Le créancier hypothécaire"], "Elle sécurise la circulation des meubles corporels.", 5),
    ("Que prouve en principe un titre foncier dans un système d'immatriculation ?", "L'existence du droit de propriété qui y est inscrit", ["Une simple possession", "Le montant de la valeur du bien", "L'identité de l'architecte"], "Le titre foncier est le point de départ du droit inscrit.", 4),
    ("Un bien du domaine public peut-il en principe être acquis par usucapion ?", "Non, il est inaliénable et imprescriptible tant qu'il appartient au domaine public", ["Oui, comme tout bien", "Oui, après décision préfectorale", "Oui, par le premier occupant"], "Inaliénabilité et imprescriptibilité en droit français.", 5),
    ("Que suppose, en principe, l'expropriation pour cause d'utilité publique en droit français ?", "Une indemnité juste et préalable", ["Une absence totale d'indemnité", "L'accord de tous les voisins", "Un jugement pénal préalable"], "Garantie du propriétaire.", 4),
    ("Quel est le critère des troubles anormaux de voisinage ?", "Le caractère excessif de la nuisance, indépendamment de toute faute", ["La faute intentionnelle du voisin", "L'ancienneté de l'établissement", "Le consentement du propriétaire"], "La responsabilité est sans faute.", 5),
    ("Que doit être un meuble pour devenir immeuble par destination ?", "Affecté par le propriétaire au service et à l'exploitation d'un immeuble", ["Vendu avec le fonds", "Déplacé dans l'immeuble", "Hypothéqué séparément"], "Le lien d'affectation est le critère.", 5),
], cat="Droit des biens", src=B)

mcq(D, "mq-suretes", [
    ("Quelle est la nature du cautionnement ?", "Un contrat accessoire à l'obligation garantie", ["Un contrat principal indépendant", "Une obligation naturelle", "Un acte unilatéral sans accord"], "Il ne subsiste pas sans dette garantie.", 3),
    ("Que devient le cautionnement si l'obligation principale est éteinte par paiement ?", "Il s'éteint par voie de conséquence", ["Il subsiste indéfiniment", "Il devient une dette personnelle", "Il se transforme en hypothèque"], "L'accessoire suit le principal.", 3),
    ("Que peut opposer la caution simple au créancier qui la poursuit d'abord ?", "Le bénéfice de discussion, pour obliger le créancier à poursuivre d'abord le débiteur", ["Le bénéfice d'inventaire", "Le bénéfice d'éviction", "Le bénéfice de renonciation"], "Il est propre à la caution simple.", 4),
    ("Le cautionnement se présume-t-il ?", "Non, il doit être exprès et certain", ["Oui, toujours", "Oui, entre commerçants", "Oui, entre parents"], "Nul ne s'engage sans l'avoir voulu clairement.", 3),
    ("La caution qui a payé a-t-elle un recours contre le débiteur ?", "Oui, un recours personnel et subrogatoire", ["Non, elle perd tout", "Oui, mais seulement contre l'État", "Oui, mais seulement si elle est solidaire"], "Elle peut se retourner contre le débiteur principal.", 4),
    ("Peut-on cautionner une dette future ?", "Oui, si elle est déterminable", ["Non, jamais", "Oui, sans aucune condition", "Oui, mais jamais par écrit"], "Le cautionnement peut garantir une dette à venir déterminable.", 4),
    ("La caution peut-elle opposer au créancier les exceptions inhérentes à la dette ?", "Oui, les moyens de défense du débiteur tenant à la dette elle-même", ["Non, jamais", "Seulement celles de sa propre situation", "Seulement la prescription"], "Caractère accessoire du cautionnement.", 5),
    ("Qu'est-ce qui distingue le cautionnement de la garantie autonome ?", "La garantie autonome est indépendante de l'obligation garantie, le cautionnement lui est accessoire", ["Le cautionnement est gratuit, la garantie payante", "La garantie autonome est réelle", "Le cautionnement est un droit réel"], "Autonomie contre accessoire.", 5),
    ("Quelle est la différence entre cautionnement et solidarité passive ?", "La caution répond de la dette d'autrui, le codébiteur solidaire d'une dette commune", ["La caution est solidaire par nature", "Le codébiteur solidaire a le bénéfice de discussion", "Il n'y a aucune différence"], "Dette d'autrui contre dette propre.", 5),
    ("Quelle est la nature de l'hypothèque ?", "Une sûreté réelle sans dépossession", ["Une sûreté personnelle", "Un droit personnel", "Une sûreté avec dépossession"], "Le constituant garde le bien.", 2),
    ("Sur quels biens porte en principe l'hypothèque ?", "Sur des immeubles, ou des droits réels immobiliers", ["Sur des meubles corporels uniquement", "Sur des créances", "Sur les meubles incorporels uniquement"], "Hypothèque, sûreté immobilière.", 3),
    ("Qu'est-ce qui rend l'hypothèque opposable aux tiers ?", "Son inscription sur le registre approprié", ["Sa signature privée", "Une simple lettre au créancier", "Le seul accord des parties"], "La publicité fixe le rang.", 4),
    ("Que garantit l'hypothèque ?", "Le paiement d'une créance déterminée", ["La valeur de l'immeuble", "La vente de l'immeuble", "La possession du bien"], "C'est un droit accessoire de la créance.", 3),
    ("Que devient l'hypothèque lorsque la créance garantie est entièrement payée ?", "Elle s'éteint, car elle est accessoire", ["Elle subsiste pour une autre dette", "Elle devient un privilège", "Elle se transforme en gage"], "Principe de l'accessoire.", 3),
    ("Que doit faire le créancier gagiste d'un gage avec dépossession ?", "Conserver le bien remis et le restituer après paiement", ["Vendre le bien immédiatement", "Utiliser le bien librement", "Transférer la propriété au tiers"], "Il a la garde du bien jusqu'au paiement.", 3),
    ("Le créancier gagiste devient-il propriétaire du bien à défaut de paiement ?", "Non, il doit en principe faire vendre le bien ou demander son attribution en justice", ["Oui, automatiquement", "Oui, après un mois", "Oui, après mise en demeure"], "Le pacte commissoire est restreint.", 5),
    ("Qu'est-ce qu'un gage sans dépossession ?", "Un gage où le constituant conserve la détention du bien, avec publicité", ["Un gage sans publicité", "Un gage entre deux voisins", "Un gage sur un immeuble"], "Il est inscrit sur un registre pour être opposable aux tiers.", 5),
    ("Qu'est-ce qu'un privilège ?", "Un droit de préférence accordé par la loi à une créance en raison de sa qualité", ["Une sûreté conventionnelle", "Un droit réel principal", "Une garantie personnelle"], "Il tient à la qualité de la créance.", 3),
    ("Un privilège peut-il être créé par simple convention entre les parties ?", "Non, il est créé par la loi", ["Oui, toujours", "Oui, par acte notarié", "Oui, devant témoins"], "Les privilèges sont d'origine légale.", 4),
    ("Que protège le droit de rétention ?", "Le détenteur d'une chose qui la garde jusqu'au paiement de sa créance connexe", ["Le propriétaire contre le voleur", "Le vendeur contre l'acquéreur de bonne foi", "L'État contre le débiteur"], "Moyen de pression et de garantie.", 4),
    ("Quelle est la différence entre sûreté personnelle et sûreté réelle ?", "La première ajoute un débiteur, la seconde affecte un bien", ["La première affecte un bien, la seconde ajoute un débiteur", "La première est légale, la seconde conventionnelle", "Elles sont synonymes"], "Débiteur supplémentaire contre bien affecté.", 2),
    ("Quel est l'intérêt d'une sûreté réelle par rapport à un cautionnement ?", "Elle garantit un droit sur un bien déterminé, indépendamment de la solvabilité d'une personne", ["Elle supprime la dette", "Elle dispense de tout paiement", "Elle est toujours gratuite"], "Le bien est affecté en garantie.", 4),
    ("Qu'est-ce que la propriété réservée dans une vente ?", "Une clause retenant la propriété jusqu'au paiement complet du prix", ["Une clause supprimant la vente", "Une clause garantissant le prix par une caution", "Une servitude"], "Elle joue comme une garantie du vendeur.", 4),
    ("À quoi sert l'inscription d'une hypothèque pour les créanciers entre eux ?", "À déterminer leur rang de paiement", ["À augmenter leur créance", "À éteindre la dette", "À transférer la propriété"], "Premier inscrit, premier payé.", 4),
    ("Le créancier hypothécaire dont l'hypothèque n'est pas inscrite a-t-il un rang opposable aux tiers ?", "Non, l'inscription est nécessaire pour l'opposabilité", ["Oui, dès la signature", "Oui, en cas d'urgence", "Oui, si le débiteur est de bonne foi"], "La publicité est une condition d'opposabilité.", 5),
], cat="Sûretés", src=B)
