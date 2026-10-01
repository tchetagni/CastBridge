"""Gestion et comptabilité (L1, L2, L3 économie) : comptabilité générale, analyse financière, gestion de l'entreprise.
Faits rédigés par l'assistant : tous `review`. Aucun taux, seuil ni montant légal : les calculs viennent des énoncés."""
from .sup_kit import *      # source, table, classify, mcq, fq, pairs, pick, cap

CAT = "Gestion et comptabilité"
source("sup-gc-compta", "Comptabilité générale : principes, bilan, résultat, écritures", "cours de comptabilité générale (L1-L2 économie)",
       "Comparer avec un manuel de comptabilité générale (principes, partie double, régularisations, stocks, amortissements) et le référentiel comptable applicable.")
source("sup-gc-analyse", "Analyse financière : SIG, CAF, FR, BFR, ratios", "cours d'analyse financière (L2-L3 économie)",
       "Comparer avec un manuel d'analyse financière ; les formules de ratios varient légèrement selon les auteurs, vérifier la définition retenue en cours.")
source("sup-gc-gestion", "Gestion de l'entreprise : management, marketing, RH, stratégie, entrepreneuriat", "cours de gestion (L1-L3 économie)",
       "Comparer avec un manuel d'introduction à la gestion et au management (Taylor, Fayol, Mayo, Mintzberg, Porter, mix marketing).")
source("sup-gc-finance", "Finance d'entreprise et contrôle de gestion", "cours de finance d'entreprise et de contrôle de gestion (L2-L3 économie)",
       "Comparer avec un manuel de gestion financière (VAN, TRI, coût du capital, levier) et de contrôle de gestion (coûts, budgets, écarts).")
source("sup-gc-ohada", "Système comptable OHADA : notions générales", "texte officiel (notions générales)",
       "Comparer avec l'Acte uniforme OHADA relatif au droit comptable et à l'information financière et le plan comptable en vigueur.")
S_C, S_A, S_G, S_F, S_O = "sup-gc-compta", "sup-gc-analyse", "sup-gc-gestion", "sup-gc-finance", "sup-gc-ohada"
L1, L2, L3 = "l1-eco", "l2-eco", "l3-eco"
_mcq = mcq


def mcq(course, tpl, rows, **kw):
    """Les énoncés à compléter (finissant par « … ») deviennent des questions."""
    out = []
    for q, r, w, e, d in rows:
        if q.endswith("…"):
            q = "Quelle suite complète correctement la phrase : « " + q + " » ?"
        out.append((q, r, w, e, d))
    _mcq(course, tpl, out, **kw)

# ============================================================ L1 : tables
table(L1, "gc1-bilan", [
    ("l'actif du bilan", "l'ensemble des biens et créances que possède l'entreprise", 1),
    ("le passif du bilan", "l'ensemble des ressources de l'entreprise : capitaux propres et dettes", 2),
    ("les capitaux propres", "les ressources apportées par les propriétaires ou accumulées par l'entreprise", 2),
    ("une dette", "une somme que l'entreprise doit à un tiers", 1),
    ("une créance", "un droit de l'entreprise de recevoir une somme d'un tiers", 1),
    ("une immobilisation", "un bien destiné à rester durablement dans l'entreprise", 1),
    ("un stock", "des marchandises, matières ou produits détenus pour être vendus ou transformés", 2),
    ("la trésorerie", "les sommes disponibles en caisse et en banque", 1),
    ("le patrimoine", "l'ensemble des biens, droits et dettes d'une personne ou d'une entreprise", 2),
    ("le résultat de l'exercice", "l'écart entre les produits et les charges de la période", 1),
    ("une charge", "une consommation de ressources qui diminue le résultat", 2),
    ("un produit", "un gain ou revenu qui augmente le résultat", 2),
    ("un exercice comptable", "la période, en général d'un an, au terme de laquelle on établit les comptes", 1),
    ("le capital social", "le montant des apports des associés fixé dans les statuts de la société", 2),
    ("les réserves", "les bénéfices non distribués que l'entreprise conserve", 3),
    ("un client", "un tiers qui doit de l'argent à l'entreprise pour une vente à crédit", 1),
    ("un fournisseur", "un tiers à qui l'entreprise doit de l'argent pour un achat à crédit", 1),
    ("l'inventaire", "le relevé détaillé, à une date donnée, des biens, créances et dettes", 2),
], cat=CAT, src=S_C, fwd="Que désigne {a} ?", rev="Quel terme désigne {b} ?")

table(L1, "gc1-princ", [
    ("le principe de prudence", "estimer les risques et pertes probables sans anticiper les profits incertains", 2),
    ("le principe de continuité d'exploitation", "établir les comptes en supposant que l'entreprise poursuivra son activité", 2),
    ("le principe d'indépendance des exercices", "rattacher à chaque exercice les charges et produits qui le concernent", 2),
    ("le principe de permanence des méthodes", "appliquer les mêmes méthodes d'un exercice à l'autre pour rendre les comptes comparables", 2),
    ("le principe d'image fidèle", "présenter de façon sincère le patrimoine, la situation financière et le résultat", 3),
    ("le principe de la partie double", "enregistrer chaque opération au débit et au crédit pour des montants égaux", 2),
    ("le principe du coût historique", "évaluer les biens à leur coût d'entrée dans le patrimoine", 3),
    ("le principe de non-compensation", "ne pas compenser entre eux les postes d'actif et de passif, ni les charges et les produits", 4),
    ("le principe d'intangibilité du bilan d'ouverture", "reprendre à l'ouverture d'un exercice le bilan de clôture du précédent sans le modifier", 4),
], cat=CAT, src=S_C, fwd="En quoi consiste {a} ?", rev="Quel principe comptable revient à {b} ?")

table(L1, "gc1-docs", [
    ("le journal", "le livre où les opérations sont enregistrées jour après jour", 1),
    ("le grand livre", "l'ensemble des comptes où les écritures sont regroupées compte par compte", 2),
    ("la balance", "un tableau qui récapitule les totaux et soldes de tous les comptes", 2),
    ("le bilan", "le document qui présente, à une date donnée, l'actif et le passif", 1),
    ("le compte de résultat", "le document qui récapitule les charges et les produits d'un exercice", 1),
    ("une pièce justificative", "le document (facture, reçu) qui prouve la réalité d'une opération", 2),
    ("l'annexe", "le document qui complète et commente les chiffres du bilan et du compte de résultat", 3),
    ("le plan comptable", "la liste normalisée des comptes avec les règles de leur fonctionnement", 2),
    ("une écriture comptable", "l'enregistrement d'une opération, avec comptes débités et comptes crédités", 2),
], cat=CAT, src=S_C, fwd="Que désigne {a} en comptabilité ?", rev="Quel élément comptable correspond à {b} ?")

table(L1, "gc1-classes", [
    ("la classe 1", "les comptes de ressources durables", 3),
    ("la classe 2", "les comptes d'actif immobilisé", 3),
    ("la classe 3", "les comptes de stocks", 3),
    ("la classe 4", "les comptes de tiers", 3),
    ("la classe 5", "les comptes de trésorerie", 3),
    ("la classe 6", "les comptes de charges des activités ordinaires", 3),
    ("la classe 7", "les comptes de produits des activités ordinaires", 3),
], cat=CAT, src=S_O, region="AF", fwd="Dans le plan comptable OHADA, que regroupe {a} ?", rev="Dans le plan comptable OHADA, quelle classe regroupe {b} ?")

# ============================================================ L1 : classify
classify(L1, "gc1-cl-bilan", {
    "un élément d'actif": ["une machine de production", "un stock de marchandises", "une créance sur un client", "le solde du compte en banque", "un bâtiment"],
    "une dette (passif)": ["un emprunt auprès d'une banque", "une dette envers un fournisseur", "une dette envers l'administration fiscale", "des salaires restant à payer"],
    "un élément des capitaux propres": ["le capital social", "les réserves", "le bénéfice de l'exercice", "le report à nouveau"],
    "une charge": ["le loyer payé pour le local", "les salaires du personnel", "l'achat de marchandises destinées à la revente", "les intérêts payés sur un emprunt"],
    "un produit": ["les ventes de marchandises", "les intérêts reçus d'un placement", "les prestations de services facturées", "un loyer perçu d'un locataire"],
}, cat=CAT, src=S_C, fwd="Dans les comptes d'une entreprise, comment classe-t-on {item} ?", rev="Lequel de ces éléments est classé comme {group} ?", expl="{item} se range dans la catégorie : {group}.", diff=1)

classify(L1, "gc1-cl-fonct", {
    "la fonction commerciale": ["prospecter de nouveaux clients", "organiser une campagne de promotion", "suivre les commandes des clients", "fixer la politique de prix de vente"],
    "la fonction de production": ["fabriquer les produits", "planifier la fabrication", "assurer la maintenance des machines", "contrôler la qualité en atelier"],
    "la fonction financière": ["rechercher un financement bancaire", "établir un plan de trésorerie", "choisir entre emprunt et autofinancement", "placer les excédents de trésorerie"],
    "la fonction ressources humaines": ["recruter des salariés", "organiser la formation du personnel", "gérer les carrières", "gérer les congés et les absences"],
    "la fonction approvisionnement": ["passer commande aux fournisseurs", "négocier les conditions d'achat", "sélectionner les fournisseurs", "suivre les livraisons de matières"],
    "la fonction comptable": ["enregistrer les opérations", "établir le bilan", "tenir le journal", "préparer la balance"],
}, cat=CAT, src=S_G, fwd="Dans une entreprise, la tâche « {item} » relève de quelle fonction ?", rev="Quelle tâche relève de {group} ?", expl="Cette tâche relève de {group}.", diff=2)

classify(L1, "gc1-cl-4p", {
    "le produit": ["le nom de la marque", "l'emballage", "la gamme proposée", "le design de l'article"],
    "le prix": ["le tarif de vente", "la remise pour achat en quantité", "les conditions de paiement", "le prix de lancement"],
    "la place (distribution)": ["le choix des grossistes", "l'ouverture de points de vente", "l'organisation des livraisons", "l'entreposage des produits finis"],
    "la promotion (communication)": ["la publicité à la radio", "le parrainage d'un événement", "la distribution d'échantillons gratuits", "les relations avec la presse"],
}, cat=CAT, src=S_G, fwd="Dans le mix marketing, « {item} » relève de quel « P » ?", rev="Dans le mix marketing, lequel de ces éléments relève de {group} ?", expl="Dans le mix marketing, cet élément relève de {group}.", diff=2)

# ============================================================ L1 : mcq
mcq(L1, "gc1-mcq-compta", [
    ("Dans la comptabilité en partie double, que se passe-t-il pour chaque opération enregistrée ?", "Le total des montants débités est égal au total des montants crédités", ["Un seul compte est modifié, une seule fois", "Le débit est toujours supérieur au crédit", "Le crédit est toujours supérieur au débit"], "Chaque opération a deux faces égales : un ou plusieurs débits, un ou plusieurs crédits.", 1),
    ("Quelle égalité est toujours vérifiée dans un bilan ?", "Total de l'actif = total du passif", ["Total de l'actif = capitaux propres seuls", "Total du passif = dettes seules", "Total de l'actif = résultat de l'exercice"], "Les emplois (actif) sont financés par les ressources (passif).", 1),
    ("Un bilan présente la situation de l'entreprise…", "à une date précise", ["sur toute la durée de sa vie", "uniquement sur un mois", "à sa date de création seulement"], "Le bilan est une photographie à la date de clôture.", 1),
    ("Quel document donne les charges, les produits et le résultat d'un exercice ?", "Le compte de résultat", ["Le bilan", "Le journal de caisse", "L'état de rapprochement"], "Le compte de résultat décrit l'activité de la période.", 1),
    ("Une entreprise réalise un bénéfice lorsque…", "ses produits sont supérieurs à ses charges", ["ses charges sont supérieures à ses produits", "son chiffre d'affaires dépasse son capital", "sa trésorerie est positive à la clôture"], "Résultat = produits - charges.", 1),
    ("Dans la présentation en emplois et ressources, que représentent les emplois ?", "L'utilisation des fonds : les biens et créances de l'actif", ["L'origine des fonds : capitaux propres et dettes", "Les produits de l'exercice", "Les dépenses de personnel uniquement"], "Les emplois sont à l'actif, les ressources au passif.", 2),
    ("Dans la présentation en emplois et ressources, que représentent les ressources ?", "L'origine des fonds : capitaux propres et dettes", ["L'utilisation des fonds en immobilisations", "Les stocks et les créances", "Les charges de l'exercice"], "Les ressources sont au passif et financent l'actif.", 2),
    ("Un client risque de ne pas payer une créance. Quel principe invite à en tenir compte dans les comptes ?", "La prudence", ["La permanence des méthodes", "La continuité d'exploitation", "L'indépendance des exercices"], "La prudence impose de prendre en compte les risques et pertes probables.", 2),
    ("Une facture de loyer de décembre de l'année N arrive en janvier de N+1. Quel principe conduit à la rattacher à l'exercice N ?", "L'indépendance des exercices", ["La prudence", "La permanence des méthodes", "L'image fidèle"], "Chaque exercice reçoit les charges qui le concernent, même non encore facturées.", 2),
    ("Pourquoi garder la même méthode d'évaluation d'un exercice à l'autre ?", "Pour que les comptes de différents exercices restent comparables", ["Pour payer moins d'impôt chaque année", "Parce que la comptabilité interdit toute évolution", "Pour éviter d'enregistrer des amortissements"], "C'est le principe de permanence des méthodes ; un changement doit rester exceptionnel et justifié.", 2),
    ("Que suppose l'hypothèse de continuité d'exploitation ?", "Que l'entreprise poursuivra son activité dans un avenir prévisible", ["Que l'entreprise sera liquidée prochainement", "Que les ventes augmenteront chaque année", "Que l'entreprise ne changera jamais d'activité"], "Sans cette hypothèse, on évaluerait les biens à leur valeur de liquidation.", 2),
    ("Que signifie que des comptes donnent une image fidèle ?", "Ils reflètent sincèrement le patrimoine, la situation financière et le résultat", ["Ils montrent toujours un bénéfice", "Ils sont identiques à ceux des concurrents", "Ils ne contiennent aucune estimation"], "L'image fidèle est l'objectif général de la comptabilité.", 3),
    ("Quelle est la différence entre le patrimoine et le capital social d'une société ?", "Le patrimoine regroupe biens, droits et dettes ; le capital social est le montant des apports", ["Ils désignent exactement la même chose", "Le capital social comprend les dettes, pas le patrimoine", "Le patrimoine ne comprend que la trésorerie"], "Le capital social est un chiffre fixé par les statuts, le patrimoine évolue avec l'activité.", 3),
    ("Les capitaux propres sont égaux à…", "l'actif moins les dettes", ["l'actif plus les dettes", "les dettes moins les créances", "l'actif circulant moins les stocks"], "Actif = capitaux propres + dettes.", 2),
    ("Laquelle de ces opérations n'a pas d'incidence directe sur le résultat ?", "L'emprunt contracté auprès d'une banque", ["Le paiement d'un loyer", "La vente de marchandises", "Le versement des salaires"], "Un emprunt augmente la trésorerie et les dettes sans produit ni charge.", 2),
    ("L'achat d'une machine de production est enregistré…", "comme une immobilisation, non comme une charge de l'exercice", ["comme une charge de l'exercice entier", "comme un produit exceptionnel", "comme une dette d'exploitation"], "Un bien durable entre à l'actif puis est amorti.", 2),
    ("Une entreprise achète des marchandises payées par chèque bancaire. Quel compte est crédité ?", "Le compte de banque", ["Le compte de caisse", "Le compte fournisseurs", "Le compte de ventes"], "Le paiement par chèque diminue la banque : crédit ; l'achat est débité.", 2),
    ("Une entreprise vend des marchandises à crédit. Quel compte est débité ?", "Le compte clients", ["Le compte fournisseurs", "Le compte de ventes", "Le compte de banque"], "La créance sur le client naît : débit des clients, crédit des ventes.", 2),
    ("Un client règle en espèces une facture déjà enregistrée. Quels comptes sont concernés ?", "Caisse (débit) et clients (crédit)", ["Clients (débit) et caisse (crédit)", "Caisse (débit) et ventes (crédit)", "Banque (débit) et fournisseurs (crédit)"], "La caisse augmente, la créance sur le client s'éteint.", 2),
    ("Une entreprise paie un fournisseur par virement. Quel est l'effet sur le bilan ?", "Les dettes fournisseurs et la trésorerie diminuent du même montant", ["Les dettes augmentent et la trésorerie diminue", "Les stocks augmentent et les dettes diminuent", "Seul le résultat diminue"], "Une dette est éteinte avec des disponibilités.", 2),
    ("Dans un compte en T, de quel côté figure le débit ?", "À gauche", ["À droite", "Au centre", "En haut"], "Par convention, débit à gauche et crédit à droite.", 1),
    ("Qu'appelle-t-on le solde d'un compte ?", "La différence entre le total des débits et celui des crédits", ["La somme des débits et des crédits", "Le montant de la dernière écriture", "Le plus grand des mouvements"], "Le solde est débiteur ou créditeur selon le côté le plus élevé.", 2),
    ("Lequel de ces comptes a normalement un solde débiteur ?", "Le compte clients", ["Le compte fournisseurs", "Le compte de capital", "Le compte de ventes de marchandises"], "Un compte d'actif est en principe débiteur.", 3),
    ("Lequel de ces comptes a normalement un solde créditeur ?", "Le compte de capital", ["Le compte de caisse", "Le compte de stocks", "Le compte d'achats"], "Les comptes de ressources et de produits sont créditeurs.", 3),
    ("Dans quel ordre les opérations sont-elles inscrites au journal ?", "Dans l'ordre chronologique", ["Par ordre alphabétique des clients", "Par montant décroissant", "Compte par compte"], "Le journal est tenu jour après jour.", 1),
    ("Que fait-on lorsqu'on reporte les écritures du journal au grand livre ?", "On les classe compte par compte", ["On les supprime du journal", "On les additionne en un seul montant", "On les classe par date de paiement"], "Le grand livre regroupe les mouvements par compte.", 2),
    ("Que comprend la trésorerie d'une entreprise ?", "Les disponibilités en caisse et en banque", ["Les stocks de marchandises", "Les créances sur les clients", "Les immobilisations corporelles"], "La trésorerie est l'argent immédiatement disponible.", 1),
    ("Une somme que l'entreprise a déjà payée mais qu'elle ne retrouve pas à l'actif est…", "une charge de l'exercice", ["une immobilisation incorporelle", "une créance sur l'État", "un capital reçu"], "Une dépense qui ne crée pas un actif durable est une charge.", 2),
    ("Le total de l'actif d'un bilan vaut 7 000 000 FCFA ; les dettes valent 5 000 000 FCFA. Que valent les capitaux propres ?", "2 000 000 FCFA", ["12 000 000 FCFA", "5 000 000 FCFA", "7 000 000 FCFA"], "Capitaux propres = actif - dettes.", 1),
    ("Les capitaux propres valent 2 000 000 FCFA et les dettes 5 000 000 FCFA. Que vaut le total de l'actif ?", "7 000 000 FCFA", ["3 000 000 FCFA", "2 000 000 FCFA", "10 000 000 FCFA"], "Actif = capitaux propres + dettes.", 1),
    ("Sur un exercice, les produits s'élèvent à 5 400 000 FCFA et les charges à 4 900 000 FCFA. Quel est le résultat ?", "Un bénéfice de 500 000 FCFA", ["Une perte de 500 000 FCFA", "Un bénéfice de 10 300 000 FCFA", "Un bénéfice de 5 400 000 FCFA"], "5 400 000 - 4 900 000 = 500 000.", 1),
    ("Des marchandises achetées 500 000 FCFA sont revendues 800 000 FCFA. Quelle est la marge commerciale sur cette vente ?", "300 000 FCFA", ["1 300 000 FCFA", "500 000 FCFA", "800 000 FCFA"], "Marge = prix de vente - coût d'achat.", 1),
    ("Pourquoi amortit-on une machine ?", "Pour constater sa perte de valeur due à l'usage et au temps", ["Pour augmenter sa valeur d'achat", "Pour la payer à crédit", "Pour couvrir un risque de procès"], "L'amortissement répartit le coût du bien sur sa durée d'utilisation.", 2),
    ("L'amortissement linéaire répartit le coût d'un bien…", "de façon égale sur chaque année de sa durée d'utilisation", ["en totalité sur le premier exercice", "de façon croissante chaque année", "en fonction du bénéfice de l'exercice"], "La dotation est identique chaque année.", 2),
    ("Une machine achetée 1 200 000 FCFA est amortie linéairement sur 5 ans, sans valeur résiduelle. Quelle est la dotation annuelle ?", "240 000 FCFA", ["300 000 FCFA", "120 000 FCFA", "600 000 FCFA"], "1 200 000 / 5 = 240 000.", 2),
    ("Un équipement coûte 1 000 000 FCFA, sa valeur résiduelle est estimée à 200 000 FCFA et sa durée d'utilisation à 4 ans. Quelle est la dotation linéaire annuelle ?", "200 000 FCFA", ["250 000 FCFA", "800 000 FCFA", "150 000 FCFA"], "(1 000 000 - 200 000) / 4 = 200 000.", 3),
    ("Un matériel acheté 600 000 FCFA a déjà été amorti de 200 000 FCFA. Quelle est sa valeur nette comptable ?", "400 000 FCFA", ["800 000 FCFA", "200 000 FCFA", "600 000 FCFA"], "VNC = valeur d'origine - amortissements cumulés.", 2),
    ("La TVA est un impôt qui pèse en définitive sur…", "le consommateur final", ["l'entreprise vendeuse", "le fournisseur de matières", "l'actionnaire"], "Chaque entreprise reverse la TVA collectée, nette de la TVA déductible : la charge finale revient au consommateur.", 2),
    ("Dans le mécanisme général de la TVA, que désigne la TVA collectée ?", "La TVA facturée par l'entreprise à ses clients sur ses ventes", ["La TVA payée à ses fournisseurs", "La TVA remboursée par l'État", "La TVA payée sur les salaires"], "L'entreprise la collecte pour le compte de l'État.", 2),
    ("Dans le mécanisme général de la TVA, que désigne la TVA déductible ?", "La TVA payée sur les achats, que l'entreprise peut imputer sur la TVA collectée", ["La TVA facturée aux clients", "La TVA non récupérable sur les amendes", "La TVA qui s'ajoute au capital"], "TVA à payer = TVA collectée - TVA déductible.", 2),
    ("Que contient le compte de résultat ?", "Les charges et les produits de l'exercice", ["Les immobilisations et les stocks", "Les capitaux propres et les dettes", "Les noms des clients et fournisseurs"], "Il détaille la formation du résultat.", 1),
    ("Que contient la partie « passif » du bilan ?", "Les capitaux propres et les dettes", ["Les stocks et les créances", "Les immobilisations", "Les charges et les produits"], "Le passif décrit l'origine des financements.", 1),
], cat=CAT, src=S_C)

mcq(L1, "gc1-mcq-gestion", [
    ("Quelle est la finalité d'une entreprise ?", "Produire des biens ou services destinés au marché", ["Percevoir des impôts", "Rendre la justice", "Émettre la monnaie"], "L'entreprise est une unité de production de biens et services.", 1),
    ("Une entreprise individuelle est…", "exploitée par une seule personne physique, sans société distincte", ["une société dont le capital est divisé en actions", "détenue en totalité par l'État", "un groupement à but non lucratif"], "L'exploitant et l'entreprise ne forment pas deux personnes distinctes.", 2),
    ("Dans une SARL, la responsabilité des associés est…", "limitée au montant de leurs apports", ["illimitée et solidaire", "limitée au chiffre d'affaires", "engagée seulement envers l'État"], "Les associés ne risquent que leurs apports (société à responsabilité limitée).", 2),
    ("Dans une société en nom collectif, les associés…", "répondent indéfiniment et solidairement des dettes sociales", ["ne répondent que de leurs apports", "ne sont jamais poursuivis", "ne répondent que de leurs dettes personnelles"], "La SNC est une société de personnes à responsabilité indéfinie.", 3),
    ("Le capital d'une société anonyme est divisé en…", "actions", ["parts de coopérative", "obligations", "bons de caisse"], "Les actionnaires détiennent des actions.", 2),
    ("Le capital d'une SARL est divisé en…", "parts sociales", ["actions cotées", "obligations", "bons du Trésor"], "Les associés d'une SARL détiennent des parts sociales.", 2),
    ("Une association se caractérise généralement par…", "un but autre que le partage de bénéfices entre ses membres", ["la répartition des bénéfices entre ses membres", "un capital divisé en actions", "la responsabilité indéfinie des membres"], "Le but non lucratif au sens de partage des gains la distingue de la société.", 2),
    ("Qu'est-ce qu'une entreprise publique ?", "Une entreprise dont le capital est détenu en totalité ou en majorité par la puissance publique", ["Une entreprise ouverte à tous les clients", "Une entreprise cotée en bourse", "Une entreprise sans aucun salarié"], "La notion tient à la détention du capital par l'État ou une collectivité.", 2),
    ("Dans une coopérative, comment se prennent en général les décisions en assemblée ?", "Selon le principe d'une personne, une voix", ["Selon le nombre d'actions détenues", "Selon l'ancienneté dans l'entreprise", "Selon le chiffre d'affaires de chacun"], "Le principe démocratique d'une voix par membre est caractéristique des coopératives.", 3),
    ("Une exploitation agricole relève du secteur…", "primaire", ["secondaire", "tertiaire", "associatif"], "Le secteur primaire prélève les ressources naturelles.", 1),
    ("Une entreprise de construction relève du secteur…", "secondaire", ["primaire", "tertiaire", "associatif"], "Le secteur secondaire transforme les matières.", 1),
    ("Une compagnie de transport de voyageurs relève du secteur…", "tertiaire", ["primaire", "secondaire", "quaternaire"], "Le secteur tertiaire regroupe les services.", 1),
    ("Qui est considéré comme le père de l'organisation scientifique du travail ?", "Frederick W. Taylor", ["Henri Fayol", "Elton Mayo", "Henry Mintzberg"], "Taylor a étudié les tâches pour en améliorer le rendement.", 1),
    ("Quel auteur a proposé les éléments de l'administration : prévoir, organiser, commander, coordonner, contrôler ?", "Henri Fayol", ["Frederick W. Taylor", "Elton Mayo", "Abraham Maslow"], "C'est la célèbre définition de l'administration par Fayol.", 2),
    ("Quel auteur est associé aux expériences de Hawthorne ?", "Elton Mayo", ["Frederick W. Taylor", "Henri Fayol", "Frederick Herzberg"], "Mayo et son équipe ont étudié l'effet du groupe sur la productivité.", 2),
    ("Que cherche à obtenir Taylor par le chronométrage des tâches ?", "Déterminer le temps normal d'exécution pour accroître la productivité", ["Mesurer la satisfaction des ouvriers", "Fixer le prix de vente des produits", "Contrôler la qualité des matières"], "Le chronométrage sert à fonder des standards de travail.", 2),
    ("Que sépare Taylor dans l'organisation du travail ?", "La conception des tâches, confiée aux cadres, et leur exécution, confiée aux ouvriers", ["Le travail de jour et le travail de nuit", "Les salariés syndiqués et non syndiqués", "La comptabilité et la finance"], "Taylor sépare ceux qui pensent le travail de ceux qui l'exécutent.", 3),
    ("Quel principe de Fayol veut qu'un salarié ne reçoive d'ordres que d'un seul supérieur ?", "L'unité de commandement", ["L'unité de direction", "La division du travail", "La centralisation"], "Un seul chef par agent évite les ordres contradictoires.", 3),
    ("Que mettent en évidence les expériences de Hawthorne ?", "L'attention portée aux salariés et le groupe influencent la productivité", ["L'éclairage est le seul facteur de productivité", "Seul le salaire explique l'effort au travail", "La hiérarchie doit être supprimée"], "Elles ont conduit à l'école des relations humaines.", 3),
    ("L'école des relations humaines met l'accent sur…", "les aspects psychologiques et sociaux du travail", ["la seule rationalité technique des tâches", "la standardisation des produits", "la division mécanique du travail"], "Elle réagit à l'approche purement technique de Taylor.", 2),
    ("Quels sont les 4P du marketing mix ?", "Produit, prix, place (distribution), promotion", ["Produit, personnel, processus, preuve physique", "Prix, profit, production, publicité", "Place, plan, personnel, paiement"], "Le mix classique combine produit, prix, distribution et communication.", 1),
    ("Que désigne la segmentation de marché ?", "Le découpage du marché en groupes de clients aux besoins ou comportements homogènes", ["La division du capital en actions", "Le découpage de l'entreprise en services", "La répartition des produits en lots de fabrication"], "Elle permet de cibler chaque groupe avec une offre adaptée.", 2),
    ("Que désigne le positionnement d'une marque ?", "La place qu'elle cherche à occuper dans l'esprit des clients par rapport aux concurrents", ["L'emplacement du siège de l'entreprise", "Le rang de l'entreprise selon son chiffre d'affaires", "Le prix le plus bas du marché"], "Le positionnement est une perception visée dans l'esprit des clients.", 3),
    ("Quelles sont les phases du cycle de vie d'un produit ?", "Lancement, croissance, maturité, déclin", ["Conception, production, vente, retrait", "Naissance, développement, stabilité, faillite", "Étude, test, lancement, fidélisation"], "Le modèle classique compte quatre phases.", 2),
    ("À quelle phase du cycle de vie les ventes progressent-elles le plus vite ?", "À la croissance", ["Au lancement", "À la maturité", "Au déclin"], "Le produit est connu, la demande se développe fortement.", 2),
    ("Comment se caractérise la phase de maturité d'un produit ?", "La croissance des ventes ralentit et la concurrence est vive", ["Les ventes sont nulles et le produit inconnu", "Les ventes explosent sans concurrent", "Le produit est retiré du marché"], "Le marché est largement équipé, les ventes se stabilisent.", 3),
    ("À quoi sert une étude de marché ?", "À recueillir des informations sur la demande, la concurrence et l'environnement avant de décider", ["À fixer le montant de l'impôt", "À enregistrer les ventes de l'exercice", "À recruter les commerciaux"], "Elle réduit l'incertitude avant le lancement d'une activité.", 1),
    ("Une enquête par questionnaire auprès d'un large échantillon de consommateurs est une étude…", "quantitative", ["qualitative", "fiscale", "technique"], "Elle produit des données chiffrées sur un grand nombre de personnes.", 2),
    ("Des entretiens approfondis avec quelques consommateurs relèvent d'une étude…", "qualitative", ["quantitative", "comptable", "juridique"], "Elle explore les motivations et opinions plutôt que de les mesurer.", 2),
    ("Que sont les données secondaires en étude de marché ?", "Des données déjà existantes (statistiques, rapports) que l'on réutilise", ["Des données recueillies pour la première fois par enquête", "Des données de moindre importance", "Des données concernant seulement les clients occasionnels"], "Les données secondaires se distinguent des données primaires collectées sur mesure.", 3),
    ("Qu'est-ce qu'un business plan ?", "Un document présentant un projet d'entreprise, son marché, ses moyens et ses prévisions", ["Un modèle de contrat de travail", "Le bilan de clôture de l'exercice", "Un registre des délibérations"], "Il sert à structurer le projet et à convaincre.", 1),
    ("À qui s'adresse notamment un business plan ?", "Aux financeurs et partenaires que l'on veut convaincre", ["Aux seuls clients de l'entreprise", "Au juge d'instruction", "Aux concurrents directs"], "Il démontre la viabilité du projet.", 2),
    ("Qu'est-ce que la microfinance ?", "L'offre de petits services financiers (épargne, crédit) aux personnes que les banques desservent peu", ["Le placement en bourse d'un petit épargnant", "La monnaie émise par une banque centrale", "L'impôt prélevé sur les petits revenus"], "Elle favorise l'inclusion financière.", 2),
    ("Qu'est-ce qu'une tontine ?", "Un groupe dont les membres cotisent régulièrement et reçoivent à tour de rôle la somme collectée", ["Une société cotée en bourse", "Un impôt local sur le commerce", "Un contrat d'assurance-vie"], "C'est une forme traditionnelle d'épargne et de crédit rotatifs.", 1),
    ("Quel est l'intérêt d'une tontine pour ses membres ?", "Accéder à une épargne ou à un crédit sans passer par une banque", ["Obtenir une garantie de l'État sur le capital", "Bénéficier d'un taux fixé par la banque centrale", "Ne payer aucun impôt sur ses revenus"], "La tontine repose sur la confiance et la solidarité du groupe.", 3),
    ("Qu'est-ce qu'un entrepreneur ?", "Une personne qui crée ou reprend une activité en prenant un risque pour la développer", ["Un salarié chargé du contrôle de gestion", "Un agent des impôts", "Un prêteur de capitaux seulement"], "L'entrepreneur assume le risque de l'activité.", 1),
    ("Qu'est-ce qu'une start-up ?", "Une jeune entreprise innovante en recherche de croissance rapide", ["Une entreprise publique ancienne", "Une association de quartier", "Une filiale d'une banque"], "L'innovation et la croissance rapide la caractérisent.", 2),
    ("Qu'est-ce qu'un apport en capital fait par un associé ?", "Ce qu'il met dans la société en échange de parts ou d'actions", ["Un prêt qu'il peut réclamer à tout moment", "Un impôt payé par la société", "Une subvention de l'État"], "L'apport forme le capital de la société.", 2),
    ("À quoi sert un incubateur d'entreprises ?", "À accompagner de jeunes projets (conseil, locaux, réseau)", ["À contrôler les comptes des PME", "À fixer les prix de marché", "À accorder des prêts obligatoires aux entreprises"], "Il aide les créateurs aux premières étapes.", 2),
    ("Qu'est-ce que le financement participatif (crowdfunding) ?", "La collecte de fonds auprès d'un grand nombre de personnes, souvent via une plateforme en ligne", ["Un emprunt auprès d'une seule banque", "Une avance de l'État", "Une émission de monnaie par la banque centrale"], "Chaque contributeur apporte une petite somme.", 2),
    ("Dans une analyse SWOT, les forces et les faiblesses sont des éléments…", "internes à l'entreprise", ["externes à l'entreprise", "uniquement fiscaux", "toujours financiers"], "Les opportunités et menaces sont, elles, externes.", 1),
    ("Dans un SWOT, une réglementation nouvelle favorable au secteur est…", "une opportunité", ["une force", "une faiblesse", "une menace"], "C'est un élément externe favorable.", 2),
    ("Dans un SWOT, l'arrivée d'un concurrent très puissant sur le marché est…", "une menace", ["une opportunité", "une force", "une faiblesse"], "C'est un élément externe défavorable.", 2),
    ("Dans un SWOT, un savoir-faire technique rare détenu par l'équipe est…", "une force", ["une opportunité", "une menace", "une faiblesse"], "C'est un atout interne.", 1),
    ("Que signifie l'acronyme SWOT ?", "Strengths, Weaknesses, Opportunities, Threats (forces, faiblesses, opportunités, menaces)", ["Strategy, Work, Objectives, Tactics (stratégie, travail, objectifs, tactique)", "Sales, Wages, Orders, Taxes (ventes, salaires, commandes, impôts)", "Structure, Workforce, Organisation, Tools (structure, effectifs, organisation, outils)"], "SWOT est un acronyme anglais d'analyse stratégique.", 1),
    ("Que signifie l'acronyme OHADA ?", "Organisation pour l'harmonisation en Afrique du droit des affaires", ["Organisation des hommes d'affaires de l'Afrique", "Office d'harmonisation des audits en Afrique", "Observatoire des hautes administrations africaines"], "L'OHADA vise un droit des affaires commun à ses États membres.", 1),
    ("Que désigne le SYSCOHADA ?", "Le système comptable de l'OHADA, commun aux États membres", ["Le système de paiement de la CEMAC", "Le tribunal arbitral de l'OHADA", "Le système douanier de l'Afrique centrale"], "C'est le référentiel comptable des États de l'OHADA.", 2),
    ("Quel est l'avantage d'un système comptable commun à plusieurs États ?", "Des comptes établis selon les mêmes règles, donc comparables d'un État à l'autre", ["Des impôts identiques dans chaque État", "Une monnaie unique pour le monde entier", "L'absence de tout contrôle des comptes"], "L'harmonisation facilite la comparaison et l'information des investisseurs.", 2),
    ("Lequel de ces documents fait partie des états financiers annuels d'une entreprise ?", "Le bilan", ["Le bon de commande", "La facture proforma", "Le registre du personnel"], "Bilan et compte de résultat sont au cœur des états financiers.", 1),
], cat=CAT, src=S_G)

# ============================================================ L1 : complément (classement fonctionnel)
classify(L1, "gc1-cl-fonct-bilan", {
    "un emploi stable (actif immobilisé)": ["un terrain", "un brevet d'exploitation", "un camion de livraison", "un logiciel acquis pour plusieurs années"],
    "un actif circulant (hors trésorerie)": ["un stock de matières premières", "une créance sur un client à 30 jours", "des marchandises en magasin", "un stock de produits finis"],
    "une ressource stable": ["le capital social", "un emprunt remboursable sur dix ans", "les réserves", "le report à nouveau"],
    "une dette à court terme (hors trésorerie)": ["une dette fournisseur à 30 jours", "les salaires à payer à la fin du mois", "la TVA à reverser à l'administration", "les impôts exigibles ce trimestre"],
}, cat=CAT, src=S_A, fwd="Dans une lecture du bilan par cycles, comment classe-t-on {item} ?", rev="Dans une lecture du bilan par cycles, lequel de ces éléments est {group} ?", expl="{item} : {group}.", diff=2)

# ============================================================ L2 : tables
table(L2, "gc2-stocks", [
    ("la méthode du coût moyen unitaire pondéré (CMUP)", "une valorisation des sorties au coût moyen calculé à partir du stock initial et des entrées", 3),
    ("la méthode FIFO (premier entré, premier sorti)", "une valorisation des sorties aux coûts des lots les plus anciens", 3),
    ("la méthode LIFO (dernier entré, premier sorti)", "une valorisation des sorties aux coûts des lots les plus récents", 3),
    ("l'inventaire permanent", "un suivi continu de chaque stock par des fiches d'entrées et de sorties", 3),
    ("l'inventaire intermittent", "un constat des stocks par comptage à des dates déterminées", 3),
    ("le stock initial", "le stock existant au début de l'exercice", 1),
    ("le stock final", "le stock existant à la clôture de l'exercice", 1),
    ("la variation de stocks", "l'écart entre le stock final et le stock initial", 3),
], cat=CAT, src=S_C, fwd="Que désigne {a} ?", rev="Quelle notion correspond à {b} ?")

table(L2, "gc2-amort", [
    ("l'amortissement", "la constatation de la perte de valeur d'un bien durable due à l'usage et au temps", 2),
    ("la dépréciation", "la constatation d'une perte de valeur d'un actif jugée probable et pouvant être reprise", 3),
    ("la provision pour risques", "la prise en compte d'une perte ou charge probable dont le montant ou l'échéance est incertain", 3),
    ("la dotation aux amortissements", "la charge de l'exercice qui constate l'amortissement des immobilisations", 2),
    ("la reprise de provision", "le produit constaté lorsqu'une provision devient sans objet", 3),
    ("la valeur nette comptable", "la valeur d'origine du bien diminuée des amortissements cumulés", 2),
    ("la valeur d'origine", "le coût d'acquisition ou de production d'un bien à son entrée dans le patrimoine", 2),
    ("la valeur résiduelle", "la valeur estimée du bien à la fin de sa durée d'utilisation", 3),
    ("l'amortissement dégressif", "un mode d'amortissement dont les dotations sont plus fortes les premières années", 3),
    ("l'amortissement linéaire", "un mode d'amortissement dont les dotations sont égales chaque année", 2),
], cat=CAT, src=S_C, fwd="Que désigne {a} ?", rev="Quelle notion correspond à {b} ?")

table(L2, "gc2-regul", [
    ("une charge à payer", "une charge de l'exercice dont la facture ou le paiement n'interviendra qu'après la clôture", 3),
    ("une charge constatée d'avance", "une charge enregistrée sur l'exercice mais qui concerne un exercice suivant", 3),
    ("un produit à recevoir", "un produit acquis sur l'exercice dont la facturation ou l'encaissement viendra plus tard", 3),
    ("un produit constaté d'avance", "un produit encaissé ou facturé sur l'exercice mais qui concerne un exercice suivant", 3),
    ("les écritures d'inventaire", "les écritures passées à la clôture pour rattacher chaque charge et chaque produit à son exercice", 2),
    ("l'extourne", "l'écriture qui annule, au début de l'exercice suivant, une écriture de régularisation de clôture", 4),
    ("la dotation aux provisions", "la charge qui constate à la clôture une dépréciation ou un risque probable", 3),
], cat=CAT, src=S_C, fwd="Que désigne {a} ?", rev="Quelle notion correspond à {b} ?")

table(L2, "gc2-sig", [
    ("la marge commerciale", "les ventes de marchandises moins le coût d'achat des marchandises vendues", 2),
    ("la valeur ajoutée", "la richesse créée : marge commerciale plus production, moins consommations en provenance de tiers", 3),
    ("l'excédent brut d'exploitation (EBE)", "la valeur ajoutée plus les subventions d'exploitation, moins impôts, taxes et charges de personnel", 4),
    ("le résultat d'exploitation", "le résultat de l'activité d'exploitation, après prise en compte des dotations et reprises", 3),
    ("le résultat financier", "l'écart entre les produits financiers et les charges financières", 2),
    ("le résultat courant avant impôt", "la somme du résultat d'exploitation et du résultat financier", 3),
    ("le résultat exceptionnel", "l'écart entre les produits et charges étrangers à l'activité courante", 3),
    ("la production stockée", "la variation, entre début et fin d'exercice, du stock de produits finis et d'en-cours", 4),
    ("la capacité d'autofinancement (CAF)", "les ressources internes que l'activité de l'exercice permet de dégager pour financer l'entreprise", 3),
    ("l'autofinancement", "la capacité d'autofinancement diminuée des dividendes distribués", 4),
], cat=CAT, src=S_A, fwd="Que désigne {a} ?", rev="Quelle notion correspond à {b} ?")

table(L2, "gc2-fr", [
    ("le fonds de roulement", "l'excédent des ressources stables sur les emplois stables", 3),
    ("le besoin en fonds de roulement", "le besoin de financement du cycle d'exploitation : stocks et créances moins dettes d'exploitation", 3),
    ("la trésorerie nette", "le fonds de roulement diminué du besoin en fonds de roulement", 3),
    ("les ressources stables", "les capitaux propres et les dettes à long terme", 2),
    ("les emplois stables", "les immobilisations, c'est-à-dire l'actif durable de l'entreprise", 2),
    ("le cycle d'exploitation", "l'ensemble des opérations qui vont de l'achat des stocks à l'encaissement des ventes", 3),
    ("les dettes à court terme", "les dettes exigibles dans un délai court, en général inférieur à un an", 2),
    ("un fonds de roulement négatif", "une situation où les ressources stables ne financent pas la totalité des emplois stables", 3),
], cat=CAT, src=S_A, fwd="Que désigne {a} ?", rev="Quelle notion correspond à {b} ?")

table(L2, "gc2-ratios", [
    ("le ratio de liquidité générale", "le rapport entre l'actif circulant et les dettes à court terme", 3),
    ("le ratio de liquidité immédiate", "le rapport entre les disponibilités et les dettes à court terme", 3),
    ("le ratio d'autonomie financière", "le rapport entre les capitaux propres et l'ensemble des dettes", 3),
    ("la rentabilité financière", "le rapport entre le résultat net et les capitaux propres", 3),
    ("la rentabilité économique", "le rapport entre le résultat d'exploitation et l'ensemble des capitaux investis", 4),
    ("la marge nette", "le rapport entre le résultat net et le chiffre d'affaires", 3),
    ("le taux de marge commerciale", "le rapport entre la marge commerciale et le coût d'achat hors taxes des marchandises", 4),
    ("le taux de marque", "le rapport entre la marge commerciale et le prix de vente hors taxes", 4),
], cat=CAT, src=S_A, fwd="Comment se définit {a} ?", rev="Quel indicateur correspond à {b} ?")

table(L2, "gc2-auteurs", [
    ("Frederick W. Taylor", "l'organisation scientifique du travail et le chronométrage des tâches", 2),
    ("Henri Fayol", "la doctrine administrative : fonctions de l'entreprise et principes de direction", 2),
    ("Elton Mayo", "l'école des relations humaines à partir des expériences de Hawthorne", 2),
    ("Henry Mintzberg", "les rôles du manager et les configurations structurelles des organisations", 3),
    ("Abraham Maslow", "la hiérarchie des besoins humains en cinq niveaux", 2),
    ("Frederick Herzberg", "la distinction entre facteurs d'hygiène et facteurs de motivation", 3),
    ("Douglas McGregor", "l'opposition entre théorie X et théorie Y sur l'homme au travail", 3),
    ("Michael Porter", "les cinq forces concurrentielles et les stratégies génériques", 3),
    ("Igor Ansoff", "la matrice produits-marchés des stratégies de croissance", 4),
    ("Max Weber", "l'analyse de la bureaucratie comme forme rationnelle d'organisation", 4),
], cat=CAT, src=S_G, fwd="À quelle théorie ou notion associe-t-on {a} ?", rev="Quel auteur est associé à {b} ?")

table(L2, "gc2-mintz", [
    ("la structure simple", "une configuration centrée sur le sommet stratégique, avec supervision directe", 4),
    ("la bureaucratie mécaniste", "une configuration fondée sur la standardisation des procédés de travail", 4),
    ("la bureaucratie professionnelle", "une configuration fondée sur la standardisation des qualifications", 4),
    ("la structure divisionnalisée", "une configuration fondée sur la standardisation des résultats par division", 4),
    ("l'adhocratie", "une configuration flexible, organisée par projets, fondée sur l'ajustement mutuel", 4),
], cat=CAT, src=S_G, fwd="Selon Mintzberg, que désigne {a} ?", rev="Selon Mintzberg, quelle configuration correspond à {b} ?")

table(L2, "gc2-mkt", [
    ("le marketing mix", "la combinaison des leviers produit, prix, distribution et communication", 2),
    ("le ciblage", "le choix des segments de marché sur lesquels l'entreprise concentre son offre", 3),
    ("la fidélisation", "l'ensemble des actions qui incitent les clients à rester et à racheter", 2),
    ("une marque", "un signe distinctif qui identifie les produits d'une entreprise", 1),
    ("un panel", "un échantillon fixe de personnes interrogées à intervalles réguliers", 3),
    ("un sondage", "l'interrogation d'un échantillon pour estimer l'opinion d'une population", 2),
    ("un échantillon", "une partie de la population choisie pour représenter l'ensemble", 2),
    ("la veille concurrentielle", "le suivi organisé des actions et des offres des concurrents", 3),
    ("le benchmarking", "la comparaison de ses pratiques avec celles des meilleures entreprises", 3),
], cat=CAT, src=S_G, fwd="Que désigne {a} ?", rev="Quelle notion correspond à {b} ?")

table(L2, "gc2-rh", [
    ("le recrutement", "la sélection et l'embauche d'un candidat pour pourvoir un poste", 1),
    ("une fiche de poste", "le document décrivant les missions, responsabilités et exigences d'un emploi", 2),
    ("la GPEC", "l'anticipation des besoins futurs en emplois et en compétences de l'entreprise", 3),
    ("la formation continue", "les actions qui permettent aux salariés de développer leurs compétences au fil de leur carrière", 2),
    ("l'évaluation du personnel", "l'appréciation périodique des résultats et des compétences d'un salarié", 2),
    ("le turn-over", "le renouvellement du personnel par les départs et les arrivées", 3),
    ("la mobilité interne", "le changement de poste ou de service au sein de la même entreprise", 2),
    ("un organigramme", "la représentation graphique de la structure et des liens hiérarchiques", 2),
], cat=CAT, src=S_G, fwd="Que désigne {a} ?", rev="Quelle notion correspond à {b} ?")

table(L2, "gc2-cdg", [
    ("un coût direct", "un coût affecté directement à un produit, sans calcul de répartition", 2),
    ("un coût indirect", "un coût commun à plusieurs produits, réparti selon une clé", 2),
    ("une charge fixe", "une charge qui ne varie pas avec le niveau d'activité à court terme", 2),
    ("une charge variable", "une charge qui évolue avec le niveau d'activité", 2),
    ("la marge sur coût variable", "la différence entre le chiffre d'affaires et les charges variables", 3),
    ("le coût complet", "l'ensemble des charges directes et indirectes imputées à un produit", 3),
    ("le seuil de rentabilité (point mort)", "le niveau d'activité pour lequel le résultat est nul", 3),
    ("un budget", "une prévision chiffrée de l'activité sur une période, assortie d'objectifs", 2),
    ("un écart", "la différence entre une donnée réelle et la donnée prévue", 2),
    ("un tableau de bord", "un ensemble d'indicateurs clés présentés pour piloter l'activité", 2),
    ("la comptabilité analytique", "la comptabilité qui calcule les coûts et résultats par produit ou activité", 3),
    ("le coût marginal", "le coût de production d'une unité supplémentaire", 4),
], cat=CAT, src=S_F, fwd="Que désigne {a} ?", rev="Quelle notion correspond à {b} ?")

# ============================================================ L2 : classify
classify(L2, "gc2-cl-auteurs", {
    "Frederick W. Taylor": ["le chronométrage des tâches", "la recherche de la meilleure méthode de travail", "la sélection et la formation scientifiques des ouvriers", "la rémunération au rendement"],
    "Henri Fayol": ["les éléments de l'administration : prévoir, organiser, commander, coordonner, contrôler", "le principe d'unité de commandement", "les six groupes d'opérations de l'entreprise", "le principe d'unité de direction"],
    "Elton Mayo": ["les expériences de Hawthorne", "l'importance du groupe informel au travail", "l'effet de l'attention portée aux travailleurs"],
    "Henry Mintzberg": ["les dix rôles du manager", "la typologie des configurations structurelles", "l'adhocratie", "la bureaucratie professionnelle"],
    "Abraham Maslow": ["la pyramide des besoins", "le besoin d'accomplissement de soi", "les besoins physiologiques à la base"],
    "Frederick Herzberg": ["les facteurs d'hygiène", "les facteurs de motivation", "la théorie des deux facteurs"],
    "Douglas McGregor": ["la théorie X", "la théorie Y"],
}, cat=CAT, src=S_G, fwd="Parmi les auteurs du management, à qui associe-t-on « {item} » ?", rev="Laquelle de ces notions est associée à {group} ?", expl="Cette notion est associée à {group}.", diff=3)

classify(L2, "gc2-cl-charges", {
    "une charge fixe": ["le loyer mensuel du local", "la prime d'assurance annuelle du bâtiment", "le salaire mensuel du gardien", "l'abonnement annuel à un logiciel de gestion"],
    "une charge variable": ["la matière première incorporée au produit", "la commission proportionnelle au chiffre d'affaires", "l'emballage de chaque produit vendu", "les frais d'expédition par colis"],
    "une charge semi-variable": ["l'électricité facturée avec un abonnement et une consommation", "le téléphone avec forfait et dépassement facturé", "la location d'une photocopieuse avec loyer et coût par copie", "le salaire d'un commercial avec fixe et commission"],
    "une charge calculée (non décaissée)": ["la dotation aux amortissements", "la dotation aux provisions pour risques", "la dotation aux dépréciations des créances clients"],
}, cat=CAT, src=S_F, fwd="Pour une entreprise, comment qualifier {item} ?", rev="Lequel de ces éléments est {group} ?", expl="{item} est {group}.", diff=3)

classify(L2, "gc2-cl-regul", {
    "une charge à payer": ["l'électricité consommée en décembre et facturée en janvier", "les salaires de décembre payés en janvier", "un honoraire d'audit de l'exercice non encore facturé"],
    "une charge constatée d'avance": ["une prime d'assurance payée en novembre couvrant l'année suivante", "un loyer payé d'avance pour le premier trimestre suivant", "une cotisation payée pour l'année suivante"],
    "un produit à recevoir": ["des intérêts acquis sur un placement, non encore encaissés", "un loyer de décembre que le locataire paiera en janvier", "des prestations réalisées en décembre et non encore facturées"],
    "un produit constaté d'avance": ["un abonnement annuel encaissé dont une partie concerne l'exercice suivant", "un loyer encaissé d'avance pour le trimestre suivant", "une prestation facturée en décembre qui sera exécutée l'an prochain"],
}, cat=CAT, src=S_C, fwd="À la clôture, comment régulariser : {item} ?", rev="Laquelle de ces situations correspond à {group} ?", expl="{item} : {group}.", diff=3)

# ============================================================ L2 : mcq comptabilité
mcq(L2, "gc2-mcq-stocks", [
    ("Un stock initial de 200 unités valorisé 8 000 FCFA reçoit 300 unités achetées 15 000 FCFA au total. Quel est le CMUP, en supposant un calcul sur l'ensemble ?", "46 FCFA l'unité", ["45 FCFA l'unité", "50 FCFA l'unité", "40 FCFA l'unité"], "(8 000 + 15 000) / (200 + 300) = 46 ; ce n'est pas la moyenne simple des deux prix unitaires.", 3),
    ("Un lot de 10 unités à 100 FCFA entre en stock, puis un lot de 10 unités à 120 FCFA. On sort 15 unités. Quelle est la valeur de la sortie en FIFO ?", "1 600 FCFA", ["1 650 FCFA", "1 800 FCFA", "1 500 FCFA"], "10 x 100 + 5 x 120 = 1 600 : on sort d'abord les plus anciennes.", 3),
    ("Même situation (10 unités à 100 FCFA, puis 10 unités à 120 FCFA, sortie de 15 unités). Quelle est la valeur du stock final en FIFO ?", "600 FCFA", ["550 FCFA", "500 FCFA", "1 100 FCFA"], "Il reste 5 unités du lot le plus récent : 5 x 120 = 600.", 3),
    ("En période de hausse des prix, comparée au CMUP, la méthode FIFO conduit en général à…", "un stock final plus élevé et un résultat plus élevé", ["un stock final plus faible et un résultat plus faible", "un stock final plus élevé et un résultat plus faible", "un stock final identique et un résultat plus faible"], "Les sorties sont valorisées aux anciens coûts plus bas ; le stock restant l'est aux coûts récents plus élevés.", 5),
    ("Le stock a une valeur de 800 000 FCFA au début de l'exercice et de 1 100 000 FCFA à la fin. Que peut-on dire ?", "Le stock a augmenté de 300 000 FCFA", ["Le stock a diminué de 300 000 FCFA", "Le stock a augmenté de 1 900 000 FCFA", "Le stock a diminué de 1 100 000 FCFA"], "Variation = stock final - stock initial = +300 000.", 2),
    ("Le stock initial est de 500 000 FCFA, les achats de marchandises de 2 000 000 FCFA et le stock final de 700 000 FCFA. Quel est le coût d'achat des marchandises vendues ?", "1 800 000 FCFA", ["2 200 000 FCFA", "3 200 000 FCFA", "2 000 000 FCFA"], "Achats + stock initial - stock final = 2 000 000 + 500 000 - 700 000.", 3),
    ("Quelle est la différence entre inventaire permanent et inventaire intermittent ?", "Le premier suit chaque mouvement en continu, le second compte les stocks à des dates fixées", ["Le premier concerne les matières, le second les marchandises", "Le premier est annuel, le second est mensuel", "Le premier est facultatif, le second est supprimé"], "L'inventaire permanent tient des fiches de stock à jour en continu.", 3),
    ("Dans la méthode du CMUP calculé après chaque entrée, le coût moyen…", "est recalculé à chaque nouvel achat", ["n'est calculé qu'une fois à la clôture", "est toujours égal au dernier prix d'achat", "est égal au prix du lot le plus ancien"], "À la différence du CMUP de fin de période, il tient compte de chaque entrée au fur et à mesure.", 4),
    ("Pourquoi le CMUP lisse-t-il les variations de prix ?", "Parce qu'il valorise toutes les sorties à une moyenne pondérée des coûts", ["Parce qu'il ignore les prix d'achat", "Parce qu'il valorise toujours au prix de vente", "Parce qu'il n'est jamais modifié par les entrées"], "La pondération par les quantités répartit les écarts de prix sur toutes les unités.", 3),
    ("Pourquoi un stock est-il parfois déprécié ?", "Parce que sa valeur probable de réalisation est inférieure à son coût", ["Parce qu'il est acheté à crédit", "Parce qu'il a été entièrement vendu", "Parce que le fournisseur n'est pas payé"], "Prudence : on constate la perte de valeur probable (invendus, obsolescence).", 3),
], cat=CAT, src=S_C)

mcq(L2, "gc2-mcq-amort", [
    ("Un matériel de 1 000 000 FCFA est amorti en dégressif au taux de 40 % de la valeur nette comptable. Quelle est la dotation de la deuxième année ?", "240 000 FCFA", ["400 000 FCFA", "160 000 FCFA", "600 000 FCFA"], "1re année : 400 000 ; VNC 600 000 ; 2e année : 40 % x 600 000 = 240 000.", 4),
    ("Un équipement de 2 400 000 FCFA est amorti linéairement sur 8 ans. Quelle est sa valeur nette comptable après 3 ans ?", "1 500 000 FCFA", ["900 000 FCFA", "2 100 000 FCFA", "1 800 000 FCFA"], "Dotation 300 000 ; cumul 900 000 ; VNC 2 400 000 - 900 000.", 3),
    ("Un bien dont la valeur nette comptable est de 400 000 FCFA est vendu 550 000 FCFA. Quel est le résultat de cession ?", "Une plus-value de 150 000 FCFA", ["Une moins-value de 150 000 FCFA", "Une plus-value de 550 000 FCFA", "Une plus-value de 950 000 FCFA"], "Prix de cession - VNC = 150 000.", 3),
    ("Un bien dont la valeur nette comptable est de 700 000 FCFA est vendu 500 000 FCFA. Quel est le résultat de cession ?", "Une moins-value de 200 000 FCFA", ["Une plus-value de 200 000 FCFA", "Une moins-value de 500 000 FCFA", "Une moins-value de 1 200 000 FCFA"], "500 000 - 700 000 = - 200 000.", 3),
    ("Quelle est la différence essentielle entre amortissement et dépréciation ?", "L'amortissement constate une usure certaine ; la dépréciation, une perte probable et réversible", ["L'amortissement ne concerne que les stocks ; la dépréciation que les immobilisations", "L'amortissement est un produit ; la dépréciation une charge", "Il n'y a aucune différence, les deux mots sont synonymes"], "L'amortissement est systématique ; la dépréciation est conditionnée à une perte de valeur constatée.", 4),
    ("Pourquoi un terrain n'est-il en général pas amorti ?", "Parce qu'on considère qu'il ne se déprécie pas par l'usage ni par le temps", ["Parce qu'il est toujours acheté à crédit", "Parce qu'il appartient toujours à l'État", "Parce qu'il n'a pas de valeur comptable"], "L'amortissement suppose une perte de valeur régulière, ce qui n'est pas le cas d'un terrain nu.", 3),
    ("Quand constate-t-on une provision pour risques ?", "Lorsqu'une perte ou charge est probable à la clôture, avec un montant ou une date incertains", ["Lorsque la perte est certaine et son montant exactement connu", "Lorsque l'entreprise réalise un bénéfice exceptionnel", "Lorsqu'un bien est vendu plus cher que sa valeur"], "Une dette certaine et chiffrée n'est pas une provision.", 3),
    ("Quel est l'effet d'une reprise de provision devenue sans objet ?", "Elle constitue un produit qui augmente le résultat", ["Elle constitue une charge qui réduit le résultat", "Elle augmente le capital social", "Elle diminue les stocks"], "La reprise annule la charge anticipée par la dotation.", 3),
    ("Pourquoi la dotation aux amortissements est-elle une charge dite « calculée » ?", "Parce qu'elle ne correspond à aucun décaissement dans l'exercice", ["Parce qu'elle est fixée par l'État", "Parce qu'elle est payée en espèces", "Parce qu'elle constitue un produit"], "Le bien a déjà été payé ; la dotation répartit son coût sur plusieurs exercices.", 3),
    ("Quelle est l'incidence d'une dotation aux amortissements sur le résultat et sur la trésorerie ?", "Elle diminue le résultat sans diminuer la trésorerie", ["Elle diminue le résultat et la trésorerie", "Elle augmente le résultat et la trésorerie", "Elle augmente le résultat sans effet sur la trésorerie"], "Charge sans décaissement.", 4),
    ("Qu'est-ce qui fait varier la base amortissable d'un bien amorti linéairement ?", "Sa valeur d'origine moins sa valeur résiduelle éventuelle", ["Le chiffre d'affaires de l'exercice", "Le nombre de salariés de l'entreprise", "Le prix de vente de l'année suivante"], "Dotation annuelle = base amortissable / durée d'utilisation.", 3),
    ("Un matériel est amorti linéairement sur 5 ans. Quel est le taux d'amortissement annuel ?", "20 %", ["5 %", "25 %", "50 %"], "Taux linéaire = 1 / durée = 1/5 = 20 %.", 2),
    ("Un camion de 5 000 000 FCFA est amorti linéairement sur 10 ans. Après 4 ans, quel est le total des amortissements cumulés ?", "2 000 000 FCFA", ["500 000 FCFA", "3 000 000 FCFA", "4 500 000 FCFA"], "Dotation 500 000 x 4 ans = 2 000 000.", 2),
    ("Pourquoi l'amortissement dégressif est-il parfois préféré par l'entreprise pour des matériels qui vieillissent vite ?", "Il charge davantage les premières années, où la perte de valeur est la plus forte", ["Il supprime toute charge la première année", "Il évite d'enregistrer le bien à l'actif", "Il augmente le résultat chaque année"], "Les dotations décroissantes suivent le rythme de dépréciation.", 4),
], cat=CAT, src=S_C)

mcq(L2, "gc2-mcq-regul", [
    ("Une prime d'assurance annuelle de 1 200 000 FCFA est payée le 1er octobre pour douze mois. À la clôture du 31 décembre, quelle est la charge constatée d'avance ?", "900 000 FCFA", ["300 000 FCFA", "1 200 000 FCFA", "600 000 FCFA"], "9 mois restants x 100 000 = 900 000.", 3),
    ("Un loyer de 600 000 FCFA couvre trois mois (novembre à janvier) et est payé le 1er novembre. À la clôture du 31 décembre, quelle est la charge constatée d'avance ?", "200 000 FCFA", ["400 000 FCFA", "600 000 FCFA", "1 800 000 FCFA"], "Seul janvier concerne l'exercice suivant : 600 000 / 3.", 3),
    ("Les salaires de décembre, de 3 000 000 FCFA, seront payés en janvier. Comment les traiter à la clôture ?", "Les constater en charge de l'exercice, comme charge à payer de 3 000 000 FCFA", ["Les ignorer jusqu'au paiement en janvier", "Les enregistrer comme charge constatée d'avance", "Les enregistrer comme un produit à recevoir"], "Indépendance des exercices : le travail de décembre est une charge de décembre.", 3),
    ("Un abonnement annuel de 480 000 FCFA est encaissé le 1er septembre pour douze mois. Quel produit constaté d'avance au 31 décembre ?", "320 000 FCFA", ["160 000 FCFA", "480 000 FCFA", "40 000 FCFA"], "8 mois restants x 40 000 = 320 000.", 3),
    ("Où figure une charge constatée d'avance dans le bilan ?", "À l'actif", ["Au passif", "Dans les capitaux propres", "Dans le compte de résultat en produit"], "C'est un compte de régularisation d'actif : un droit à une prestation à venir.", 3),
    ("Lequel de ces éléments de régularisation figure normalement au passif ?", "Un produit constaté d'avance", ["Un produit à recevoir", "Une charge constatée d'avance", "Un stock de marchandises"], "Il représente une prestation encore due au client.", 3),
    ("Pourquoi passe-t-on des écritures de régularisation à la clôture ?", "Pour rattacher à l'exercice les charges et produits qui le concernent", ["Pour corriger des erreurs de saisie au journal", "Pour augmenter le résultat imposable", "Pour supprimer les écritures de l'exercice précédent"], "Principe d'indépendance des exercices.", 2),
    ("À quoi sert l'extourne d'une charge à payer au début de l'exercice suivant ?", "À annuler l'écriture de clôture pour que la charge ne soit pas comptée deux fois", ["À doubler la charge enregistrée à la clôture", "À supprimer le stock initial", "À clôturer les comptes de produits"], "Lors de la facture réelle, la charge est enregistrée normalement.", 4),
    ("Des intérêts acquis sur un placement au 31 décembre ne seront encaissés qu'en mars. Comment les traiter à la clôture ?", "Comme un produit à recevoir de l'exercice", ["Comme un produit constaté d'avance", "Comme une charge à payer", "Ils ne sont pas pris en compte avant l'encaissement"], "Le produit est acquis sur l'exercice, même s'il n'est pas encaissé.", 3),
], cat=CAT, src=S_C)

mcq(L2, "gc2-mcq-tva", [
    ("La TVA collectée est de 450 000 FCFA et la TVA déductible de 300 000 FCFA. Quel montant l'entreprise doit-elle reverser ?", "150 000 FCFA", ["750 000 FCFA", "300 000 FCFA", "450 000 FCFA"], "TVA à payer = collectée - déductible.", 2),
    ("La TVA collectée est de 240 000 FCFA et la TVA déductible de 310 000 FCFA. Quelle est la situation ?", "Un crédit de TVA de 70 000 FCFA", ["Une TVA à payer de 70 000 FCFA", "Une TVA à payer de 550 000 FCFA", "Un crédit de TVA de 240 000 FCFA"], "La déductible excède la collectée : crédit de TVA.", 3),
    ("Pourquoi dit-on que la TVA est neutre pour une entreprise assujettie ?", "Elle collecte la TVA pour l'État et déduit celle qu'elle a payée, sans la supporter elle-même", ["Elle ne la verse jamais à l'État", "Elle fait partie de son résultat d'exploitation", "Elle est un produit financier"], "La charge économique finale pèse sur le consommateur.", 3),
    ("Une vente à crédit de 1 000 000 FCFA hors taxes est facturée avec une TVA de 150 000 FCFA. Quel montant est débité au compte clients ?", "1 150 000 FCFA", ["1 000 000 FCFA", "150 000 FCFA", "850 000 FCFA"], "Le client doit le prix TTC.", 2),
    ("Un achat de marchandises est facturé 600 000 FCFA hors taxes avec 90 000 FCFA de TVA récupérable. Quel est le coût d'achat pour l'entreprise ?", "600 000 FCFA", ["690 000 FCFA", "90 000 FCFA", "510 000 FCFA"], "La TVA déductible n'est pas une charge : le coût d'achat est le montant hors taxes.", 3),
    ("Dans quel cas l'entreprise ne peut-elle pas récupérer de la TVA sur ses achats ?", "Lorsqu'elle n'est pas assujettie ou que la dépense est exclue du droit à déduction", ["Lorsqu'elle achète à crédit", "Lorsqu'elle réalise un bénéfice", "Lorsqu'elle paie par virement"], "Le droit à déduction dépend du statut et de la nature de la dépense (selon la réglementation en vigueur).", 4),
    ("À quel moment le compte de TVA est-il apuré dans le mécanisme général ?", "À la déclaration périodique, par la différence entre TVA collectée et déductible", ["À la vente de chaque produit, en espèces", "À la clôture annuelle seulement, par le résultat", "Jamais : la TVA reste en dette"], "Le solde est reversé à l'administration ou reporté en crédit.", 3),
], cat=CAT, src=S_C)

mcq(L2, "gc2-mcq-bancaire", [
    ("À quoi sert le rapprochement bancaire ?", "À comparer le compte banque de l'entreprise au relevé de la banque et à expliquer les écarts", ["À calculer l'impôt dû sur les intérêts", "À fixer le prix des marchandises", "À remplacer le grand livre"], "Il vérifie la concordance entre deux sources d'information.", 2),
    ("Un chèque émis par l'entreprise n'a pas encore été présenté à l'encaissement. Où figure-t-il ?", "En comptabilité de l'entreprise, mais pas encore sur le relevé", ["Sur le relevé, mais pas en comptabilité", "Ni en comptabilité ni sur le relevé", "Sur le relevé avec un signe inversé"], "L'entreprise l'a enregistré à l'émission ; la banque le débitera à la présentation.", 3),
    ("Des frais bancaires apparaissent sur le relevé mais n'ont pas été enregistrés par l'entreprise. Que fait-elle ?", "Elle les enregistre en charge dans sa comptabilité", ["Elle les ignore, la banque s'est trompée", "Elle les ajoute au solde du relevé", "Elle les enregistre en produit"], "Le relevé fait foi pour des opérations dont l'entreprise n'avait pas connaissance.", 3),
    ("Un chèque reçu d'un client a été enregistré par l'entreprise mais pas encore crédité par la banque. Où figure-t-il ?", "En comptabilité de l'entreprise, mais pas encore sur le relevé", ["Sur le relevé, mais pas en comptabilité", "Sur le relevé et en comptabilité", "Ni en comptabilité ni sur le relevé"], "L'encaissement bancaire est différé.", 3),
    ("Le solde comptable est de 2 000 000 FCFA. Un chèque émis de 300 000 FCFA n'a pas été débité par la banque ; aucune autre différence. Quel est le solde du relevé ?", "2 300 000 FCFA", ["1 700 000 FCFA", "2 000 000 FCFA", "2 600 000 FCFA"], "La banque n'a pas encore déduit le chèque que l'entreprise a déjà comptabilisé.", 4),
    ("Le relevé affiche 1 500 000 FCFA, dont un virement reçu de 200 000 FCFA non enregistré par l'entreprise ; aucune autre différence. Quel était le solde comptable avant régularisation ?", "1 300 000 FCFA", ["1 700 000 FCFA", "1 500 000 FCFA", "200 000 FCFA"], "L'entreprise ignore encore ces 200 000 FCFA.", 4),
    ("Quelle situation est une cause normale d'écart entre le compte banque et le relevé ?", "Un chèque émis en fin de mois encaissé le mois suivant", ["Une erreur de signature du directeur", "Un changement de monnaie nationale", "Un bilan déjà publié"], "Les décalages de date sont la première cause d'écart.", 3),
], cat=CAT, src=S_C)

mcq(L2, "gc2-mcq-sig", [
    ("Les ventes de marchandises sont de 5 000 000 FCFA et le coût d'achat des marchandises vendues de 3 200 000 FCFA. Quelle est la marge commerciale ?", "1 800 000 FCFA", ["8 200 000 FCFA", "3 200 000 FCFA", "5 000 000 FCFA"], "Ventes - coût d'achat des marchandises vendues.", 2),
    ("La marge commerciale est de 1 800 000 FCFA, la production de 4 000 000 FCFA et les consommations en provenance de tiers de 2 500 000 FCFA. Quelle est la valeur ajoutée ?", "3 300 000 FCFA", ["5 800 000 FCFA", "1 500 000 FCFA", "8 300 000 FCFA"], "1 800 000 + 4 000 000 - 2 500 000.", 3),
    ("Valeur ajoutée de 3 300 000 FCFA, subventions d'exploitation de 200 000, impôts et taxes de 300 000, charges de personnel de 1 900 000. Quel est l'EBE ?", "1 300 000 FCFA", ["1 100 000 FCFA", "1 600 000 FCFA", "5 700 000 FCFA"], "3 300 000 + 200 000 - 300 000 - 1 900 000.", 4),
    ("Quel solde mesure la richesse créée par l'entreprise dans son activité ?", "La valeur ajoutée", ["La marge commerciale", "Le résultat net", "Le résultat financier"], "Elle mesure ce que l'entreprise ajoute aux biens et services achetés à des tiers.", 2),
    ("Quel solde est le moins influencé par les choix d'amortissement et de financement ?", "L'excédent brut d'exploitation", ["Le résultat net", "Le résultat d'exploitation", "Le résultat courant"], "L'EBE est calculé avant dotations et avant charges financières.", 5),
    ("La marge commerciale concerne…", "uniquement l'activité d'achat et de revente de marchandises", ["la production de l'entreprise industrielle", "les opérations financières", "les opérations exceptionnelles"], "Une entreprise industrielle a une production, pas de marge commerciale.", 2),
    ("Comment obtient-on le résultat courant avant impôt ?", "Résultat d'exploitation plus résultat financier", ["Résultat d'exploitation moins valeur ajoutée", "EBE plus résultat exceptionnel", "Résultat net plus impôt et dividendes"], "Le résultat courant ne comprend pas les éléments exceptionnels.", 3),
    ("Qu'est-ce que la production stockée ?", "La variation entre début et fin d'exercice du stock de produits finis et d'en-cours", ["La production vendue pendant l'exercice", "La production conservée dans un entrepôt de l'État", "La production immobilisée par l'entreprise"], "Si le stock final de produits dépasse le stock initial, la production stockée est positive.", 4),
    ("Que mesure la capacité d'autofinancement ?", "Les ressources internes que l'activité de l'exercice permet de dégager", ["Le montant maximum que la banque peut prêter", "La valeur nette des actifs de l'entreprise", "Les dividendes imposés par la loi"], "La CAF est un flux potentiel de trésorerie issu de l'activité.", 3),
    ("Résultat net de 900 000 FCFA, dotations aux amortissements et provisions de 600 000, reprises de 100 000 ; aucune cession. Quelle est la CAF par la méthode additive ?", "1 400 000 FCFA", ["1 500 000 FCFA", "1 600 000 FCFA", "400 000 FCFA"], "900 000 + 600 000 - 100 000.", 4),
    ("La CAF est de 1 400 000 FCFA et les dividendes de 500 000 FCFA. Quel est l'autofinancement ?", "900 000 FCFA", ["1 900 000 FCFA", "500 000 FCFA", "1 400 000 FCFA"], "Autofinancement = CAF - dividendes.", 3),
    ("Pourquoi la CAF diffère-t-elle du résultat net ?", "Elle réintègre les charges calculées non décaissées, comme les dotations", ["Elle inclut les dettes de l'entreprise", "Elle exclut tous les produits", "Elle ne concerne que les ventes au comptant"], "Les dotations réduisent le résultat sans sortie de trésorerie.", 3),
    ("Pourquoi la CAF n'est-elle pas égale à la trésorerie réellement disponible ?", "Elle ne tient pas compte des variations du BFR ni du décalage entre encaissements et décaissements", ["Elle ne tient pas compte du chiffre d'affaires", "Elle est toujours inférieure à zéro", "Elle ne concerne que les immobilisations"], "La trésorerie dépend aussi des délais clients, fournisseurs et stocks.", 5),
], cat=CAT, src=S_A)

mcq(L2, "gc2-mcq-fr", [
    ("Les ressources stables sont de 5 000 000 FCFA et les emplois stables de 3 800 000 FCFA. Quel est le fonds de roulement ?", "1 200 000 FCFA", ["8 800 000 FCFA", "3 800 000 FCFA", "5 000 000 FCFA"], "FR = ressources stables - emplois stables.", 2),
    ("Les stocks et créances d'exploitation valent 2 600 000 FCFA et les dettes d'exploitation 1 500 000 FCFA. Quel est le BFR ?", "1 100 000 FCFA", ["4 100 000 FCFA", "1 500 000 FCFA", "2 600 000 FCFA"], "BFR = emplois d'exploitation - ressources d'exploitation.", 2),
    ("Le FR est de 1 200 000 FCFA et le BFR de 1 100 000 FCFA. Quelle est la trésorerie nette ?", "Une trésorerie nette positive de 100 000 FCFA", ["2 300 000 FCFA", "Un découvert net de 100 000 FCFA", "1 100 000 FCFA"], "TN = FR - BFR.", 2),
    ("Le FR est de 800 000 FCFA et le BFR de 1 000 000 FCFA. Quelle est la situation de trésorerie ?", "Trésorerie nette négative de 200 000 FCFA, donc besoin de financement à court terme", ["Trésorerie nette positive de 200 000 FCFA", "Trésorerie nette positive de 1 800 000 FCFA", "Trésorerie nette nulle"], "TN = 800 000 - 1 000 000 = - 200 000.", 3),
    ("Quelle relation lie la trésorerie nette, le FR et le BFR ?", "Trésorerie nette égale FR moins BFR", ["Trésorerie nette égale FR plus BFR", "Trésorerie nette égale BFR moins FR", "Trésorerie nette égale FR multiplié par BFR"], "Le FR finance d'abord le BFR ; le reste constitue la trésorerie.", 2),
    ("Que signifie un fonds de roulement positif ?", "Les ressources stables financent les emplois stables et dégagent un excédent", ["L'entreprise a réalisé un bénéfice", "L'entreprise n'a aucune dette", "Les stocks sont nuls"], "Un excédent de ressources stables finance une part du cycle d'exploitation.", 2),
    ("Quel calcul, par le bas du bilan, donne aussi le fonds de roulement ?", "Actif circulant moins dettes à court terme", ["Actif circulant plus dettes à court terme", "Emplois stables moins ressources stables", "Stocks plus créances moins capitaux propres"], "FR = actif circulant - dettes à court terme (trésorerie comprise).", 4),
    ("Que se passe-t-il pour le BFR si les clients paient plus tardivement ?", "Il augmente", ["Il diminue", "Il reste identique", "Il devient toujours négatif"], "Les créances clients, emploi du cycle, augmentent.", 3),
    ("Laquelle de ces actions réduit le BFR ?", "Obtenir de plus longs délais de paiement des fournisseurs", ["Accorder de plus longs délais aux clients", "Augmenter le stock de sécurité", "Payer les fournisseurs plus vite"], "Les dettes d'exploitation augmentent, ce qui allège le besoin.", 3),
    ("Qu'est-ce qu'un BFR négatif ?", "Une situation où les ressources d'exploitation dépassent les emplois d'exploitation", ["Une situation où l'entreprise est en faillite", "Une erreur de calcul du bilan", "Une situation où les stocks sont nuls"], "Typique d'activités qui encaissent avant de payer leurs fournisseurs, comme la grande distribution.", 5),
    ("Une entreprise a un FR positif mais une trésorerie nette négative. Que cela indique-t-il ?", "Le FR ne suffit pas à financer entièrement le BFR", ["Le FR est négatif en réalité", "Le BFR est nul", "L'entreprise n'a pas de dettes"], "TN = FR - BFR < 0 si le BFR dépasse le FR.", 4),
    ("Quelle est l'utilité de l'analyse du FR, du BFR et de la trésorerie nette ?", "Apprécier l'équilibre financier et la capacité à payer à court terme", ["Calculer l'impôt sur les sociétés", "Fixer le prix de vente", "Déterminer le nombre de salariés"], "Ces trois grandeurs décrivent la structure de financement.", 2),
], cat=CAT, src=S_A)

mcq(L2, "gc2-mcq-ratios", [
    ("L'actif circulant est de 3 000 000 FCFA et les dettes à court terme de 2 000 000 FCFA. Quel est le ratio de liquidité générale ?", "1,5", ["0,67", "2,5", "6"], "3 000 000 / 2 000 000 = 1,5.", 2),
    ("Les disponibilités sont de 400 000 FCFA et les dettes à court terme de 1 000 000 FCFA. Quel est le ratio de liquidité immédiate ?", "0,4", ["2,5", "0,6", "4"], "400 000 / 1 000 000.", 2),
    ("Capitaux propres de 6 000 000 FCFA, dettes de 3 000 000 FCFA. Quel est le ratio d'autonomie financière (capitaux propres sur dettes) ?", "2", ["0,5", "9", "3"], "6 000 000 / 3 000 000.", 2),
    ("Résultat net de 450 000 FCFA, capitaux propres de 3 000 000 FCFA. Quelle est la rentabilité financière ?", "15 %", ["1,5 %", "150 %", "6,7 %"], "450 000 / 3 000 000 = 0,15.", 2),
    ("Résultat net de 200 000 FCFA pour un chiffre d'affaires de 4 000 000 FCFA. Quelle est la marge nette ?", "5 %", ["20 %", "2 %", "50 %"], "200 000 / 4 000 000 = 0,05.", 2),
    ("Prix d'achat hors taxes de 80 000 FCFA, prix de vente hors taxes de 100 000 FCFA. Quel est le taux de marque ?", "20 %", ["25 %", "80 %", "125 %"], "Marge 20 000 / prix de vente 100 000 = 20 %.", 4),
    ("Prix d'achat hors taxes de 80 000 FCFA, prix de vente hors taxes de 100 000 FCFA. Quel est le taux de marge commerciale ?", "25 %", ["20 %", "80 %", "125 %"], "Marge 20 000 / coût d'achat 80 000 = 25 %.", 4),
    ("Pourquoi une entreprise rentable peut-elle manquer de liquidité ?", "La rentabilité mesure le résultat, la liquidité la capacité de payer à court terme", ["La rentabilité et la liquidité sont toujours égales", "Une entreprise rentable n'a jamais de dettes", "La liquidité dépend uniquement du capital social"], "Un bénéfice peut être immobilisé en stocks ou en créances.", 4),
    ("Lequel de ces ratios mesure la solvabilité ?", "Capitaux propres sur total des dettes", ["Résultat net sur chiffre d'affaires", "Actif circulant sur dettes à court terme", "Résultat d'exploitation sur capitaux investis"], "La solvabilité concerne la capacité à honorer ses dettes à terme.", 3),
    ("Un ratio de liquidité générale inférieur à 1 signifie que…", "l'actif circulant ne couvre pas les dettes à court terme", ["l'entreprise réalise une perte", "les capitaux propres sont négatifs", "l'entreprise n'a pas de stocks"], "Il y a un risque pour faire face aux échéances proches.", 3),
    ("Que mesurent les ratios de rentabilité ?", "L'aptitude de l'entreprise à dégager un résultat par rapport aux moyens engagés", ["La capacité à payer les dettes à court terme", "La part des dettes dans le financement", "La rapidité de rotation des stocks"], "Ils rapportent un résultat à un chiffre d'affaires ou à des capitaux.", 2),
    ("Que mesurent les ratios de liquidité ?", "La capacité de l'entreprise à faire face à ses dettes à court terme", ["Le résultat dégagé par euro investi", "La part du capital détenue par l'État", "Le niveau de dividendes par action"], "Ils comparent actifs à court terme et dettes à court terme.", 2),
    ("Que mesurent les ratios de solvabilité ?", "La capacité de l'entreprise à rembourser ses dettes sur le long terme", ["La rapidité d'encaissement des clients", "La marge sur chaque produit vendu", "La croissance du chiffre d'affaires"], "Ils comparent l'endettement et les ressources propres.", 2),
], cat=CAT, src=S_A)

mcq(L2, "gc2-mcq-ohada", [
    ("À quelle classe du plan comptable OHADA appartient un compte de banque ?", "La classe 5 (trésorerie)", ["La classe 4 (tiers)", "La classe 3 (stocks)", "La classe 2 (immobilisations)"], "Les comptes de caisse et de banque relèvent de la trésorerie.", 3),
    ("À quelle classe appartiennent les comptes de clients et de fournisseurs ?", "La classe 4 (tiers)", ["La classe 5 (trésorerie)", "La classe 7 (produits)", "La classe 3 (stocks)"], "Clients et fournisseurs sont des tiers.", 3),
    ("À quelle classe appartient un compte de marchandises en stock ?", "La classe 3", ["La classe 2", "La classe 6", "La classe 1"], "Les stocks forment la classe 3.", 2),
    ("À quelle classe appartient le compte de capital social ?", "La classe 1 (ressources durables)", ["La classe 2", "La classe 4", "La classe 7"], "Le capital est une ressource stable.", 3),
    ("À quelle classe appartient un compte d'achats de marchandises ?", "La classe 6 (charges)", ["La classe 7 (produits)", "La classe 3 (stocks)", "La classe 4 (tiers)"], "Les achats sont des charges.", 3),
    ("À quelle classe appartient un compte de ventes de marchandises ?", "La classe 7 (produits)", ["La classe 6", "La classe 5", "La classe 4"], "Les ventes sont des produits.", 3),
    ("À quelle classe appartient une machine ?", "La classe 2 (actif immobilisé)", ["La classe 3", "La classe 6", "La classe 5"], "Les immobilisations forment la classe 2.", 2),
    ("À quelle classe d'un plan comptable de type OHADA appartiennent les emprunts à long terme ?", "La classe 1 (ressources durables)", ["La classe 4 (tiers)", "La classe 5 (trésorerie)", "La classe 6 (charges)"], "Les emprunts font partie des ressources durables.", 4),
], cat=CAT, src=S_O, region="AF")

# ============================================================ L2 : mcq gestion
mcq(L2, "gc2-mcq-mgt", [
    ("Les dix rôles de Mintzberg se regroupent en trois catégories. Lesquelles ?", "Interpersonnels, informationnels et décisionnels", ["Techniques, commerciaux et financiers", "Stratégiques, tactiques et opérationnels", "Politiques, économiques et sociaux"], "Ce sont les trois familles de rôles du manager décrites par Mintzberg.", 3),
    ("Dans la pyramide de Maslow, quel besoin est à la base ?", "Les besoins physiologiques", ["Les besoins de sécurité", "Les besoins d'estime", "Le besoin d'accomplissement de soi"], "On satisfait d'abord les besoins vitaux.", 2),
    ("Dans la pyramide de Maslow, quel besoin est au sommet ?", "L'accomplissement de soi", ["La sécurité", "L'appartenance", "Les besoins physiologiques"], "Il représente la réalisation de son potentiel.", 2),
    ("Selon Herzberg, lequel de ces éléments est un facteur de motivation ?", "La reconnaissance du travail accompli", ["Les conditions matérielles de travail", "La politique générale de l'entreprise", "Les relations avec les collègues"], "Les trois autres sont des facteurs d'hygiène.", 4),
    ("Selon Herzberg, que provoque l'absence de facteurs d'hygiène ?", "De l'insatisfaction, sans que leur présence suffise à motiver", ["Une motivation accrue", "Une disparition de tout conflit", "Une hausse automatique de la productivité"], "L'hygiène évite l'insatisfaction ; seule la motivation crée l'engagement.", 4),
    ("Que suppose la théorie X de McGregor ?", "Que les salariés n'aiment pas travailler et doivent être contrôlés", ["Que le travail est source d'épanouissement spontané", "Que les salariés doivent être payés à la tâche seulement", "Que l'entreprise doit supprimer toute hiérarchie"], "La théorie X justifie un management de contrôle et de contrainte.", 3),
    ("Que suppose la théorie Y de McGregor ?", "Que le travail peut être source d'épanouissement et que les salariés peuvent s'autodiriger", ["Que les salariés fuient toute responsabilité", "Que seule la contrainte permet d'obtenir un effort", "Que la hiérarchie doit être renforcée"], "La théorie Y mise sur l'initiative et l'engagement.", 3),
    ("Quelles sont les six fonctions de l'entreprise distinguées par Fayol ?", "Technique, commerciale, financière, de sécurité, comptable, administrative", ["Production, vente, stockage, transport, publicité, formation", "Technique, juridique, sociale, fiscale, bancaire, douanière", "Stratégique, tactique, opérationnelle, logistique, sociale, éthique"], "L'administration n'est qu'une des six fonctions.", 3),
    ("Quelle fonction, selon Fayol, comprend prévoir, organiser, commander, coordonner et contrôler ?", "La fonction administrative", ["La fonction technique", "La fonction commerciale", "La fonction comptable"], "Fayol définit ainsi le rôle de la direction.", 3),
    ("Quelle fonction de Fayol vise à protéger les biens et les personnes ?", "La fonction de sécurité", ["La fonction financière", "La fonction commerciale", "La fonction technique"], "Elle protège contre le vol, l'incendie, les accidents.", 3),
    ("Quel mode de rémunération Taylor privilégiait-il ?", "Un salaire lié au rendement", ["Un salaire fixe identique pour tous", "Un salaire à l'ancienneté seule", "Une rémunération uniquement en nature"], "Taylor associait salaire et productivité.", 3),
    ("Qu'est-ce que le fordisme ?", "La production en série sur chaîne de montage de produits standardisés", ["La fabrication à l'unité par des artisans", "La production à la demande sans aucune standardisation", "La suppression de l'usine au profit du travail à domicile"], "Il prolonge le taylorisme par la production de masse.", 3),
    ("Dans une structure fonctionnelle, les activités sont regroupées…", "par grandes fonctions (production, commercial, finance…)", ["par produit ou par zone géographique", "par client uniquement", "au hasard selon les projets"], "À la différence de la structure divisionnelle, elle regroupe par métiers.", 3),
    ("Dans une structure divisionnelle, l'entreprise est découpée en…", "unités autonomes par produit, marché ou zone géographique", ["fonctions regroupées sous un seul directeur", "équipes temporaires sans responsable", "services dépendant uniquement du comptable"], "Chaque division dispose d'une autonomie de gestion.", 3),
    ("Qu'est-ce que la délégation ?", "Le transfert à un collaborateur du pouvoir de décider ou d'agir dans un domaine précis", ["La suppression d'un poste", "Le remplacement d'un salarié absent par un intérimaire", "Le transfert de l'entreprise à l'État"], "Le responsable confie une partie de ses attributions.", 2),
    ("En quoi consiste le management par objectifs ?", "Fixer des objectifs aux collaborateurs puis évaluer les résultats obtenus", ["Interdire toute initiative aux salariés", "Payer tous les salariés de façon identique", "Supprimer les évaluations"], "Les objectifs guident l'action et servent de base à l'évaluation.", 3),
    ("Dans un style de direction participatif…", "le dirigeant associe ses collaborateurs aux décisions", ["le dirigeant décide seul sans consulter", "le dirigeant laisse chacun faire sans cadre", "les décisions sont prises par tirage au sort"], "Il se distingue des styles directif et laissez-faire.", 2),
    ("Qu'est-ce que la division du travail ?", "La répartition des tâches entre les personnes pour gagner en efficacité", ["La séparation de l'entreprise en deux sociétés", "La baisse du nombre d'heures travaillées", "Le partage des bénéfices entre les salariés"], "Elle conduit à la spécialisation.", 2),
    ("Que critique-t-on le plus souvent à l'organisation tayloriste ?", "La monotonie des tâches et la faible implication des ouvriers", ["Une productivité trop faible", "L'absence de toute standardisation", "Un excès d'autonomie accordé aux ouvriers"], "Les relations humaines ont été une réaction à cette critique.", 3),
    ("Qu'est-ce que l'effet Hawthorne ?", "L'effet de l'attention portée à des travailleurs sur leur comportement et leur productivité", ["L'effet de la baisse des salaires sur la productivité", "L'effet de la mécanisation sur l'emploi", "L'effet de la concurrence sur les prix"], "Les travailleurs observés ont modifié leur comportement parce qu'on s'intéressait à eux.", 4),
    ("Selon Weber, la bureaucratie repose notamment sur…", "des règles écrites, une hiérarchie claire et une division précise des tâches", ["l'autorité personnelle du fondateur seulement", "l'absence de toute règle écrite", "la rotation aléatoire des fonctions"], "Weber décrit une forme rationnelle d'organisation.", 4),
], cat=CAT, src=S_G)

mcq(L2, "gc2-mcq-mkt", [
    ("Pourquoi un produit en phase de lancement exige-t-il souvent de forts efforts de communication ?", "Pour faire connaître le produit alors que les ventes sont encore faibles", ["Parce que la concurrence est inexistante", "Parce que les coûts sont nuls", "Parce que le produit est en déclin"], "Il faut créer la notoriété.", 3),
    ("Quel critère de segmentation est de type démographique ?", "L'âge", ["La région d'habitation", "Le style de vie", "La fréquence d'achat"], "Âge, sexe, revenu, taille du ménage sont des critères démographiques.", 2),
    ("La fréquence d'achat est un critère de segmentation…", "comportemental", ["démographique", "géographique", "juridique"], "Il concerne la façon dont le client achète.", 3),
    ("La région d'habitation est un critère de segmentation…", "géographique", ["comportemental", "psychographique", "démographique"], "Pays, région, ville : critères géographiques.", 2),
    ("Que signifie un circuit de distribution court ?", "Il comporte peu ou pas d'intermédiaires entre le producteur et le consommateur", ["Il comporte de nombreux grossistes", "Il ne concerne que l'exportation", "Il est uniquement en ligne"], "Le circuit long ajoute grossistes et détaillants.", 2),
    ("En quoi consiste la politique de prix d'écrémage ?", "Fixer un prix élevé au lancement pour viser d'abord les clients prêts à payer plus", ["Fixer un prix très bas pour conquérir rapidement le marché", "Aligner son prix exactement sur celui du concurrent", "Vendre gratuitement le produit"], "Le prix baisse ensuite pour toucher des clients plus sensibles au prix.", 4),
    ("En quoi consiste la politique de prix de pénétration ?", "Fixer un prix bas au lancement pour conquérir rapidement des parts de marché", ["Fixer un prix élevé pour viser une clientèle aisée", "Ne jamais modifier le prix", "Vendre uniquement sur commande"], "Elle s'oppose à l'écrémage.", 4),
    ("Qu'est-ce que la notoriété d'une marque ?", "La proportion de personnes qui la connaissent", ["Le prix de ses produits", "Le nombre de points de vente", "Le nombre de ses brevets"], "On mesure la notoriété spontanée ou assistée.", 3),
    ("Quelle est la différence entre la population mère et l'échantillon ?", "La population mère est l'ensemble étudié, l'échantillon la partie interrogée", ["L'échantillon est l'ensemble étudié, la population mère la partie interrogée", "Ils désignent la même chose", "La population mère est l'ensemble des concurrents"], "L'échantillon doit représenter la population mère.", 3),
    ("Qu'est-ce qu'un échantillon par quotas ?", "Un échantillon qui reproduit la structure de la population selon certains critères (âge, sexe…)", ["Un échantillon tiré au hasard sans aucune contrainte", "Un échantillon composé uniquement de volontaires", "Un échantillon limité aux clients fidèles"], "Les quotas fixent le nombre de personnes de chaque catégorie.", 4),
    ("Qu'est-ce qu'un échantillon aléatoire ?", "Un échantillon où chaque individu a une probabilité connue d'être choisi", ["Un échantillon choisi par l'enquêteur selon sa préférence", "Un échantillon des seuls clients de l'entreprise", "Un échantillon dont on connaît d'avance les réponses"], "Le tirage au sort limite les biais de sélection.", 4),
    ("Quels P s'ajoutent aux 4P dans le mix des services (7P) ?", "Personnes, processus, preuve physique", ["Profit, publicité, paiement", "Production, personnel, planification", "Plan, portefeuille, politique"], "Les services sont intangibles : les personnes et l'environnement comptent.", 3),
    ("Un produit atteint la phase de déclin. Quelle décision est cohérente ?", "Réduire les investissements ou retirer progressivement le produit", ["Multiplier les dépenses de lancement", "Doubler le prix sans changer l'offre", "Ignorer la baisse des ventes"], "Les ventes diminuent durablement.", 3),
    ("Qu'est-ce que le ciblage ?", "Le choix des segments sur lesquels l'entreprise concentre son offre", ["Le découpage du marché en groupes", "La fixation du prix de vente", "La mesure de la satisfaction des clients"], "La segmentation précède le ciblage, qui précède le positionnement.", 3),
    ("Dans la démarche de marketing stratégique, quel est l'ordre logique ?", "Segmentation, ciblage, positionnement", ["Positionnement, segmentation, ciblage", "Ciblage, positionnement, segmentation", "Promotion, prix, produit"], "On découpe le marché, on choisit ses cibles, puis on se positionne.", 4),
    ("Pourquoi une entreprise fait-elle de la veille concurrentielle ?", "Pour suivre les actions et offres de ses concurrents et anticiper ses décisions", ["Pour fixer ses impôts", "Pour recruter ses salariés", "Pour obtenir des subventions"], "La veille réduit l'incertitude sur l'environnement.", 2),
], cat=CAT, src=S_G)

mcq(L2, "gc2-mcq-rh", [
    ("Quelle étape précède l'annonce d'offre d'emploi lors d'un recrutement ?", "La définition du besoin et du profil du poste", ["Le versement du premier salaire", "L'évaluation annuelle du candidat", "La rupture de la période d'essai"], "On commence par préciser ce qu'on cherche.", 3),
    ("Quelles sont les deux sources de recrutement ?", "Interne (promotion, mobilité) et externe (candidats extérieurs)", ["Publique et privée", "Nationale et internationale uniquement", "Écrite et orale"], "L'entreprise peut pourvoir un poste en interne ou en externe.", 2),
    ("À quoi sert la GPEC ?", "À anticiper l'adéquation entre emplois et compétences futurs et ressources en personnel", ["À calculer le résultat de l'exercice", "À fixer le prix des produits", "À gérer la trésorerie quotidienne"], "Elle est prospective.", 3),
    ("Une entreprise prévoit de nombreux départs en retraite de techniciens et forme dès maintenant de jeunes salariés. De quelle démarche s'agit-il ?", "D'une démarche de GPEC", ["D'un audit comptable", "D'un benchmarking", "D'un licenciement économique"], "Elle anticipe un besoin de compétences.", 3),
    ("Qu'est-ce qu'un turn-over élevé peut révéler ?", "Des difficultés possibles de climat social, de rémunération ou de management", ["Une augmentation certaine des bénéfices", "Une trésorerie excédentaire", "Un excellent climat social"], "Beaucoup de départs signalent un malaise potentiel.", 3),
    ("De quoi se compose une compétence selon l'approche courante ?", "Savoirs, savoir-faire et savoir-être", ["Salaire, ancienneté, diplôme", "Prix, qualité, délai", "Capital, travail, terre"], "Connaissances, aptitudes pratiques et comportements.", 3),
    ("Quel document formalise la relation de travail entre employeur et salarié ?", "Le contrat de travail", ["La facture", "Le bon de livraison", "Le relevé bancaire"], "Il fixe notamment la fonction et la rémunération.", 1),
    ("À quoi sert l'évaluation du personnel ?", "À apprécier les résultats et compétences pour décider formation, évolution ou rémunération", ["À calculer l'impôt sur les bénéfices", "À fixer la capacité de production", "À sélectionner les fournisseurs"], "Elle nourrit la gestion des carrières.", 2),
    ("Quelle est la différence entre formation initiale et formation continue ?", "La première précède l'entrée dans la vie active, la seconde se poursuit pendant la carrière", ["La première est payante, la seconde gratuite", "La première est réservée aux cadres", "Elles désignent exactement la même chose"], "La formation continue actualise les compétences.", 2),
], cat=CAT, src=S_G)

mcq(L2, "gc2-mcq-cdg", [
    ("Un produit consomme 3 000 FCFA de matières, 2 000 FCFA de main-d'œuvre directe et 1 500 FCFA de charges indirectes imputées. Quel est son coût de production ?", "6 500 FCFA", ["5 000 FCFA", "4 500 FCFA", "3 500 FCFA"], "Somme des coûts directs et indirects.", 2),
    ("Les charges fixes sont de 600 000 FCFA et la marge sur coût variable représente 40 % du chiffre d'affaires. Quel est le seuil de rentabilité ?", "1 500 000 FCFA", ["240 000 FCFA", "600 000 FCFA", "2 400 000 FCFA"], "Seuil = charges fixes / taux de marge sur coût variable = 600 000 / 0,4.", 3),
    ("Prix de vente unitaire 500 FCFA, coût variable unitaire 300 FCFA, charges fixes 400 000 FCFA. Quel est le seuil de rentabilité en quantité ?", "2 000 unités", ["800 unités", "1 333 unités", "200 unités"], "Marge unitaire 200 ; 400 000 / 200 = 2 000.", 3),
    ("Avec les mêmes données (prix 500, coût variable 300, charges fixes 400 000 FCFA), quel est le résultat pour 2 500 unités vendues ?", "Un bénéfice de 100 000 FCFA", ["Un bénéfice de 500 000 FCFA", "Une perte de 100 000 FCFA", "Un bénéfice de 400 000 FCFA"], "2 500 x 200 - 400 000 = 100 000.", 4),
    ("Quand l'activité augmente, que devient la charge fixe par unité produite ?", "Elle diminue", ["Elle augmente", "Elle reste identique", "Elle devient variable"], "La charge fixe est répartie sur davantage d'unités.", 3),
    ("Pourquoi le budget des ventes est-il en général élaboré en premier ?", "Parce que la production et les achats dépendent du niveau de ventes prévu", ["Parce qu'il est exigé par le fisc", "Parce qu'il ne repose sur aucune hypothèse", "Parce qu'il est toujours le plus faible"], "Les autres budgets en découlent.", 3),
    ("Le chiffre d'affaires prévu est de 5 000 000 FCFA et le chiffre d'affaires réalisé de 5 400 000 FCFA. Quel est l'écart ?", "Un écart favorable de 400 000 FCFA", ["Un écart défavorable de 400 000 FCFA", "Un écart favorable de 10 400 000 FCFA", "Un écart nul"], "Le réalisé dépasse le prévu.", 2),
    ("Les charges prévues sont de 2 500 000 FCFA et les charges réelles de 2 700 000 FCFA. Quel est l'écart sur charges ?", "Un écart défavorable de 200 000 FCFA", ["Un écart favorable de 200 000 FCFA", "Un écart défavorable de 5 200 000 FCFA", "Un écart nul"], "Les charges réelles sont supérieures au budget.", 2),
    ("Qu'est-ce qu'un indicateur de performance ?", "Une mesure chiffrée qui permet de suivre l'atteinte d'un objectif", ["Un document comptable obligatoire", "Un type de contrat de travail", "Une unité de mesure des stocks"], "Il alimente le tableau de bord.", 2),
    ("Que doit contenir en priorité un bon tableau de bord ?", "Un nombre limité d'indicateurs clés, actualisés régulièrement", ["Toutes les écritures du journal", "Les statuts de la société", "Les seuls chiffres du dernier exercice"], "Il doit rester lisible pour décider.", 3),
    ("Quelle est la différence entre comptabilité générale et comptabilité analytique ?", "La générale décrit le patrimoine et le résultat ; l'analytique calcule des coûts internes", ["La générale sert aux seuls clients ; l'analytique à l'État", "La générale est facultative ; l'analytique est obligatoire", "Il n'y en a qu'une, appelée comptabilité des coûts"], "L'analytique éclaire les décisions de gestion.", 3),
    ("À quoi sert une unité d'œuvre dans le calcul des coûts ?", "À mesurer l'activité d'une section pour répartir ses coûts (heure de main-d'œuvre, kilogramme…)", ["À fixer le prix de vente", "À mesurer le capital social", "À calculer la TVA"], "Le coût de l'unité d'œuvre s'obtient en divisant les charges de la section par son activité.", 4),
    ("Que fait-on avec une clé de répartition ?", "On répartit une charge indirecte entre plusieurs produits ou sections", ["On calcule le bénéfice net", "On fixe le seuil de rentabilité", "On détermine le taux d'amortissement"], "Une charge indirecte ne peut être affectée directement.", 3),
    ("Quelle charge est directe pour un produit donné ?", "La matière première incorporée uniquement à ce produit", ["Le loyer de l'usine qui fabrique tous les produits", "Le salaire du directeur général", "L'assurance du bâtiment commun"], "Elle est affectée sans répartition.", 2),
    ("Qu'appelle-t-on le coût de revient ?", "Le total des coûts d'achat, de production et de distribution d'un produit vendu", ["Le prix de vente au client final", "Le seul coût des matières premières", "Le prix d'achat hors taxes des marchandises"], "Il comprend aussi les coûts de distribution.", 4),
    ("Que mesure la marge sur coût variable ?", "La contribution du chiffre d'affaires à la couverture des charges fixes et au résultat", ["Le bénéfice net après impôt", "Le montant des dividendes versés", "La valeur résiduelle d'une machine"], "Marge sur coût variable = CA - charges variables.", 3),
], cat=CAT, src=S_F)

mcq(L2, "gc2-mcq-strat", [
    ("Quelles sont les trois stratégies génériques selon Porter ?", "Domination par les coûts, différenciation, concentration (focalisation)", ["Diversification, intégration, externalisation", "Croissance, stabilité, repli", "Pénétration, développement, écrémage"], "Ce sont les stratégies pour obtenir un avantage concurrentiel.", 3),
    ("Une entreprise vend moins cher que ses concurrents grâce à de faibles coûts de revient. Quelle stratégie applique-t-elle ?", "La domination par les coûts", ["La différenciation", "La focalisation", "La diversification"], "Elle fonde son avantage sur le prix.", 2),
    ("Une entreprise propose un produit perçu comme unique (qualité, design) pour justifier un prix plus élevé. Quelle stratégie ?", "La différenciation", ["La domination par les coûts", "La liquidation", "L'externalisation"], "L'avantage vient de la valeur perçue.", 2),
    ("Une entreprise se spécialise sur un segment étroit de clientèle. Quelle stratégie ?", "La focalisation (concentration)", ["La domination par les coûts sur tout le marché", "La différenciation de masse", "La diversification conglomérale"], "Elle cible un segment précis.", 3),
    ("Quelques gros clients achètent l'essentiel de la production d'un secteur. Quelle force de Porter est élevée ?", "Le pouvoir de négociation des clients", ["La menace des produits de substitution", "Le pouvoir de négociation des fournisseurs", "La menace de nouveaux entrants"], "Les acheteurs concentrés peuvent imposer leurs conditions.", 3),
    ("Qu'est-ce qu'une barrière à l'entrée ?", "Un obstacle (capital élevé, brevets, réglementation) qui limite l'arrivée de nouveaux concurrents", ["Un impôt payé à l'entrée du magasin", "Un obstacle à la sortie des clients", "Une clôture entourant l'usine"], "Elle protège les entreprises déjà installées.", 3),
    ("Que signifie PESTEL ?", "Politique, économique, socioculturel, technologique, écologique, légal", ["Production, emploi, stock, taxe, effectif, logistique", "Prix, entreprise, service, technique, étude, lancement", "Planification, évaluation, stratégie, tactique, éthique, leadership"], "C'est une grille d'analyse de l'environnement général.", 3),
    ("Quelle différence y a-t-il entre stratégie et tactique ?", "La stratégie fixe les orientations à long terme ; la tactique les moyens à court terme", ["La stratégie est de court terme ; la tactique de long terme", "La stratégie concerne la comptabilité ; la tactique la production", "Il n'y a aucune différence"], "La tactique décline la stratégie.", 3),
    ("Qu'est-ce qu'une stratégie d'entreprise ?", "L'ensemble des choix à long terme sur ses activités, ses ressources et son positionnement", ["La liste des dépenses du mois", "Le calendrier des paiements", "Le contrat avec un client"], "Elle vise à atteindre les objectifs fixés.", 2),
    ("Dans une analyse de l'environnement, la hausse du niveau d'éducation de la population relève du facteur…", "socioculturel", ["technologique", "légal", "politique"], "Les évolutions de la population relèvent du socioculturel.", 3),
], cat=CAT, src=S_G)

mcq(L2, "gc2-mcq-entre", [
    ("Dans une tontine simple de 10 membres qui cotisent chacun 10 000 FCFA par séance, combien reçoit le bénéficiaire d'une séance si tous ont cotisé ?", "100 000 FCFA", ["10 000 FCFA", "110 000 FCFA", "90 000 FCFA"], "10 x 10 000.", 2),
    ("Dans une tontine simple sans enchère, un membre cotise 5 000 FCFA par mois dans un cycle de 12 membres. Combien reçoit-il lorsque vient son tour ?", "60 000 FCFA", ["5 000 FCFA", "12 000 FCFA", "55 000 FCFA"], "12 cotisations de 5 000.", 2),
    ("Que signifie la garantie solidaire dans un microcrédit de groupe ?", "Les membres du groupe se portent mutuellement garants du remboursement", ["La banque centrale garantit le prêt", "Le prêteur renonce à être remboursé", "Un immeuble de l'État garantit le prêt"], "La pression et la confiance du groupe remplacent les garanties matérielles.", 3),
    ("Quelle commission régionale supervise les banques et les établissements de microfinance en zone CEMAC ?", "La COBAC (Commission bancaire de l'Afrique centrale)", ["La CIMA", "La Commission de l'UEMOA", "La CIPRES"], "Les deux autres sont dédiées aux assurances et à la prévoyance sociale, ou à une autre zone.", 3),
    ("Qu'est-ce qu'un plan de financement ?", "Un tableau qui met en regard les besoins de financement d'un projet et les ressources prévues", ["Un contrat de vente à crédit", "La liste des clients d'une entreprise", "Un budget des seules dépenses de personnel"], "Il vérifie que le projet est finançable.", 3),
    ("Quelle est la différence entre un apport en nature et un apport en numéraire ?", "L'apport en nature est un bien (matériel, local), l'apport en numéraire une somme d'argent", ["L'apport en nature est un prêt, l'apport en numéraire un don", "L'apport en nature concerne les stocks seulement", "Il n'y a aucune différence"], "Tous deux sont des apports au capital.", 2),
    ("Pourquoi les prévisions financières sont-elles essentielles dans un business plan ?", "Elles montrent si le projet peut être rentable et financé", ["Elles remplacent l'étude de marché", "Elles dispensent de tenir une comptabilité", "Elles fixent le salaire du personnel par la loi"], "Elles chiffrent les ventes, charges, besoins et résultats.", 2),
], cat=CAT, src=S_G)
