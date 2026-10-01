"""Économie du supérieur (L1, L2, L3) : micro, macro, monnaie et banque, commerce international, zone franc / CEMAC, développement.
Faits rédigés à la main (principes, définitions, institutions, attributions d'auteurs) : aucun chiffre d'actualité, aucune date
incertaine. Tout est en statut `review` : une relecture par un enseignant d'économie reste nécessaire."""
from .sup_kit import *      # source, table, classify, mcq, fq, pairs, pick, cap

L1, L2, L3 = "l1-eco", "l2-eco", "l3-eco"
source("sup-eco-micro", "Microéconomie : consommateur, producteur, marchés, externalités, information, jeux", "cours de L2/L3 économie",
       "Comparer avec un manuel de microéconomie intermédiaire (ex. Varian, Mas-Colell pour le L3, Mankiw pour l'introduction).")
source("sup-eco-macro", "Macroéconomie : comptabilité nationale, croissance, cycles, chômage, inflation, politiques, IS-LM", "cours de L2/L3 économie",
       "Comparer avec un manuel de macroéconomie (ex. Blanchard, Mankiw, Burda-Wyplosz) et le cours de comptabilité nationale.")
source("sup-eco-monnaie", "Monnaie, banque et finance : fonctions, création monétaire, banque centrale, marchés, risques", "cours de L2/L3 économie",
       "Comparer avec un manuel d'économie monétaire et bancaire et les textes de la banque centrale concernée.")
source("sup-eco-commerce", "Commerce international et change : avantages, protectionnisme, régimes de change, balance des paiements", "cours de L2/L3 économie",
       "Comparer avec un manuel d'économie internationale (ex. Krugman-Obstfeld) et les textes de l'OMC.")
source("sup-eco-cemac", "Zone franc, CEMAC, institutions monétaires et financières régionales (niveau général)", "textes institutionnels",
       "Comparer avec les sites de la BEAC, de la CEMAC et de la COBAC et avec le traité instituant la CEMAC ; ne contient volontairement ni taux ni date.")
source("sup-eco-dev", "Développement économique : indicateurs, secteurs, dette, aide, théories", "cours de L2/L3 économie",
       "Comparer avec un manuel d'économie du développement (ex. Todaro, Hugon) et les rapports du PNUD.")
source("sup-eco-pensee", "Histoire de la pensée économique : écoles et auteurs", "cours de L1/L2 économie",
       "Comparer avec un manuel d'histoire de la pensée économique (ex. Gide et Rist, Beaud et Dostaler).")



def split(c_lo, c_hi, tpl, rows, *, cat, src, cut=4, region="WORLD"):
    """Répartit des questions rédigées à la main : difficulté < cut -> c_lo, sinon c_hi (cours plus avancé)."""
    mcq(c_lo, tpl, [r for r in rows if r[4] < cut], cat=cat, src=src, region=region)
    mcq(c_hi, tpl.replace("eco2", "eco3"), [r for r in rows if r[4] >= cut], cat=cat, src=src, region=region)


def split3(tpl, rows, *, cat, src, region="WORLD"):
    """Difficulté <= 2 -> L1, 3 -> L2, >= 4 -> L3 (même sujet, cours de plus en plus avancé)."""
    mcq(L1, tpl.replace("eco2", "eco1"), [r for r in rows if r[4] <= 2], cat=cat, src=src, region=region)
    mcq(L2, tpl, [r for r in rows if r[4] == 3], cat=cat, src=src, region=region)
    mcq(L3, tpl.replace("eco2", "eco3"), [r for r in rows if r[4] >= 4], cat=cat, src=src, region=region)

# =====================================================================================================================
# L2 : MICROÉCONOMIE
# =====================================================================================================================
table(L2, "eco2-mic-conso", [
    ("l'utilité marginale", "la variation d'utilité due à la consommation d'une unité supplémentaire", 2),
    ("le taux marginal de substitution", "la quantité d'un bien cédée pour une unité de l'autre à utilité inchangée", 3),
    ("une courbe d'indifférence", "l'ensemble des paniers de biens qui procurent au consommateur la même utilité", 2),
    ("la contrainte budgétaire", "l'ensemble des paniers accessibles avec le revenu aux prix en vigueur", 2),
    ("l'effet de substitution", "la variation de la demande causée par le changement des prix relatifs", 3),
    ("l'effet de revenu", "la variation de la demande causée par le changement du pouvoir d'achat réel", 3),
    ("un bien de Giffen", "un bien dont la demande augmente avec son prix, l'effet de revenu dominant", 4),
    ("des biens substituts", "des biens dont la demande de l'un croît quand le prix de l'autre augmente", 2),
    ("des biens complémentaires", "des biens consommés ensemble, la demande de l'un baissant quand le prix de l'autre monte", 3),
    ("l'optimum du consommateur", "le panier de la contrainte budgétaire qui procure l'utilité la plus élevée", 3),
    ("la loi de l'utilité marginale décroissante", "la baisse de l'utilité apportée par chaque unité supplémentaire consommée", 2),
], cat="Microéconomie : consommateur", src="sup-eco-micro",
    fwd="Comment définit-on {a} en théorie du consommateur ?", rev="Quelle notion de la théorie du consommateur est décrite ici : {b} ?")

table(L2, "eco2-mic-prod", [
    ("l'isoquante", "l'ensemble des combinaisons de facteurs qui donnent le même niveau de production", 2),
    ("l'isocoût", "l'ensemble des combinaisons de facteurs qui coûtent le même montant", 3),
    ("la productivité marginale du travail", "la production supplémentaire obtenue avec une unité de travail de plus", 2),
    ("la productivité moyenne du travail", "la production divisée par la quantité de travail utilisée", 2),
    ("le coût marginal", "l'augmentation du coût total due à la production d'une unité supplémentaire", 2),
    ("le coût variable", "la part du coût total qui change avec le volume produit", 1),
    ("le coût fixe", "la part du coût total indépendante du volume produit à court terme", 1),
    ("le coût moyen", "le coût total divisé par la quantité produite", 1),
    ("la recette marginale", "la variation de la recette totale due à la vente d'une unité de plus", 3),
    ("le profit économique", "la recette totale moins tous les coûts, y compris les coûts d'opportunité", 3),
    ("un coût irrécupérable", "une dépense déjà engagée qui ne peut plus être récupérée et ne doit pas guider le choix", 4),
    ("les économies d'échelle", "la baisse du coût moyen quand la taille de la production augmente", 2),
    ("les économies d'envergure", "la baisse du coût de produire plusieurs biens ensemble plutôt que séparément", 4),
], cat="Microéconomie : producteur et coûts", src="sup-eco-micro",
    fwd="Comment définit-on {a} dans la théorie du producteur ?", rev="Quelle notion de la théorie du producteur est décrite ici : {b} ?")

table(L2, "eco2-mic-marche", [
    ("le monopole", "un seul vendeur, aucun substitut proche et de fortes barrières à l'entrée", 2),
    ("l'oligopole", "quelques grandes firmes en situation d'interdépendance stratégique", 2),
    ("la concurrence monopolistique", "de nombreuses firmes vendant des produits différenciés avec libre entrée", 3),
    ("le monopsone", "un seul acheteur face à de nombreux vendeurs", 3),
    ("l'oligopsone", "quelques acheteurs seulement face à de nombreux vendeurs", 4),
    ("le monopole bilatéral", "un vendeur unique face à un acheteur unique", 4),
    ("le cartel", "un accord entre firmes pour restreindre la production et soutenir les prix", 2),
    ("le monopole naturel", "un marché où une firme unique produit au moindre coût grâce à des coûts moyens décroissants", 4),
], cat="Microéconomie : structures de marché", src="sup-eco-micro",
    fwd="Quelle est la caractéristique de {a} ?", rev="Quelle structure de marché est décrite par : {b} ?")

table(L3, "eco3-mic-ext", [
    ("une externalité positive", "un effet bénéfique de l'activité d'un agent sur des tiers, sans compensation", 2),
    ("une externalité négative", "un coût imposé à des tiers par l'activité d'un agent, sans compensation", 2),
    ("l'internalisation d'une externalité", "sa prise en compte dans les calculs de l'agent qui la cause", 3),
    ("une taxe pigouvienne", "une taxe égale au dommage marginal, destinée à corriger une externalité négative", 4),
    ("un permis d'émission négociable", "un droit échangeable qui plafonne le volume total d'émissions", 3),
    ("le passager clandestin", "l'agent qui profite d'un bien public sans participer à son financement", 2),
    ("la sélection adverse", "une asymétrie d'information avant le contrat qui évince les offres de bonne qualité", 4),
    ("l'aléa moral", "un comportement modifié après le contrat, que l'autre partie ne peut pas observer", 3),
    ("la signalisation", "l'action coûteuse d'un agent informé pour révéler sa qualité aux autres", 4),
    ("le problème principal-agent", "le conflit d'intérêts entre un mandant et un mandataire mieux informé", 4),
    ("une ressource commune", "un bien rival mais non exclusif, exposé à la surexploitation", 3),
    ("un bien de club", "un bien non rival dont l'accès peut être réservé à ses membres", 3),
    ("un bien privé", "un bien à la fois rival et exclusif", 2),
], cat="Microéconomie : externalités, biens publics, information", src="sup-eco-micro",
    fwd="Comment définit-on {a} ?", rev="De quelle notion s'agit-il : {b} ?")

table(L3, "eco3-mic-jeux", [
    ("un équilibre de Nash", "une situation où aucun joueur n'a intérêt à changer seul de stratégie", 3),
    ("une stratégie dominante", "une stratégie meilleure pour un joueur quelle que soit celle des autres", 3),
    ("une stratégie dominée", "une stratégie moins bonne qu'une autre quel que soit le choix des autres", 3),
    ("un jeu à somme nulle", "un jeu où le gain d'un joueur est exactement la perte des autres", 2),
    ("un jeu séquentiel", "un jeu où les joueurs choisissent à tour de rôle en observant les coups précédents", 3),
    ("un jeu simultané", "un jeu où chaque joueur choisit sans connaître le choix des autres", 2),
    ("la forme normale d'un jeu", "sa représentation par une matrice de gains", 3),
    ("la forme extensive d'un jeu", "sa représentation par un arbre de décision", 3),
    ("l'induction à rebours", "la résolution d'un jeu séquentiel en partant de la dernière décision", 4),
    ("une stratégie mixte", "le choix aléatoire entre plusieurs stratégies selon des probabilités", 4),
    ("le dilemme du prisonnier", "un jeu où la poursuite de l'intérêt individuel mène à un résultat collectivement moins bon", 3),
], cat="Microéconomie : théorie des jeux", src="sup-eco-micro",
    fwd="Comment définit-on {a} en théorie des jeux ?", rev="Quel concept de théorie des jeux correspond à : {b} ?")

table(L2, "eco2-pensee-aut", [
    ("Adam Smith", "la main invisible et la division du travail comme source de richesse", 2),
    ("David Ricardo", "la théorie de l'avantage comparatif et de la rente foncière", 3),
    ("Thomas Malthus", "la crainte d'une population croissant plus vite que les subsistances", 3),
    ("Jean-Baptiste Say", "la loi des débouchés selon laquelle l'offre crée sa propre demande", 3),
    ("Karl Marx", "la théorie de la plus-value et de l'exploitation du travail par le capital", 2),
    ("Léon Walras", "la théorie de l'équilibre général et du tâtonnement", 3),
    ("Alfred Marshall", "l'analyse en équilibre partiel et l'offre et la demande comme ciseaux", 4),
    ("Vilfredo Pareto", "la notion d'optimum où l'on ne peut améliorer un sort sans en détériorer un autre", 3),
    ("John Maynard Keynes", "le rôle de la demande effective et la possibilité d'un chômage involontaire", 2),
    ("Joseph Schumpeter", "l'innovation et la destruction créatrice comme moteurs du capitalisme", 3),
    ("Milton Friedman", "le monétarisme et l'hypothèse du revenu permanent", 3),
    ("Friedrich Hayek", "le rôle des prix comme transmetteurs d'information et l'ordre spontané du marché", 4),
    ("François Quesnay", "le Tableau économique et la physiocratie", 3),
    ("Thorstein Veblen", "la consommation ostentatoire", 4),
], cat="Histoire de la pensée économique", src="sup-eco-pensee",
    fwd="Quel apport est associé à {a} ?", rev="Quel économiste est associé à cet apport : {b} ?")

table(L3, "eco3-pensee-mod", [
    ("Franco Modigliani", "l'hypothèse du cycle de vie de l'épargne et de la consommation", 4),
    ("Robert Solow", "un modèle de croissance où le progrès technique est exogène", 3),
    ("Robert Lucas", "la critique selon laquelle les comportements changent avec la politique menée", 5),
    ("Edward Chamberlin", "la théorie de la concurrence monopolistique", 4),
    ("Augustin Cournot", "un modèle de duopole où les firmes choisissent leurs quantités", 4),
    ("Joseph Bertrand", "un modèle de duopole où les firmes choisissent leurs prix", 5),
    ("Elinor Ostrom", "l'étude de la gestion collective des ressources communes", 4),
    ("Gary Becker", "la théorie du capital humain appliquée aux choix de formation", 4),
    ("Amartya Sen", "l'approche du développement par les capabilités et les libertés", 4),
    ("George Akerlof", "l'analyse du marché des « citrons » et de la sélection adverse", 4),
    ("Michael Spence", "la théorie de la signalisation sur le marché du travail", 5),
    ("Ronald Coase", "les coûts de transaction pour expliquer l'existence de la firme", 4),
], cat="Histoire de la pensée économique", src="sup-eco-pensee",
    fwd="Quel apport est associé à {a} ?", rev="Quel économiste est associé à cet apport : {b} ?")

split(L2, L3, "eco2-mic-q1", [
    ("Que traduit la convexité des courbes d'indifférence vers l'origine ?", "Une préférence pour les paniers équilibrés, avec un taux marginal de substitution décroissant", ["Des biens parfaitement substituables", "Une utilité marginale croissante pour chaque bien", "Un revenu qui augmente avec la consommation"], "La convexité signifie que le consommateur renonce à de moins en moins de l'un pour obtenir plus de l'autre.", 3),
    ("Pourquoi, sous les hypothèses usuelles, deux courbes d'indifférence d'un même consommateur ne se coupent-elles pas ?", "Cela contredirait la transitivité et la non-satiété de ses préférences", ["Parce que les prix seraient alors égaux", "Parce que son revenu serait alors nul", "Parce que le taux marginal de substitution serait infini sur toute la longueur de la courbe"], "Un point commun à deux niveaux d'utilité distincts serait contradictoire.", 4),
    ("Dans le plan (bien 1, bien 2), quelle est la pente en valeur absolue de la droite de budget ?", "Le rapport des prix p1/p2", ["Le revenu divisé par p1", "Le rapport des utilités totales", "L'utilité marginale du bien 1"], "La droite de budget a pour équation p1·x1 + p2·x2 = R.", 3),
    ("À l'optimum intérieur du consommateur, que vaut le taux marginal de substitution ?", "Le rapport des prix des deux biens", ["Zéro", "Le rapport des quantités consommées", "Le revenu divisé par le prix du bien 1"], "À l'optimum, la courbe d'indifférence est tangente à la droite de budget.", 3),
    ("Quel effet a une hausse du revenu, prix inchangés, sur la droite de budget ?", "Elle se translate parallèlement vers l'extérieur", ["Elle pivote autour d'un de ses points d'intersection avec un axe", "Elle se translate parallèlement vers l'origine", "Elle devient verticale"], "Les prix relatifs, donc la pente, ne changent pas.", 2),
    ("Que devient la droite de budget quand seul le prix du bien 1 (en abscisse) baisse ?", "Elle pivote autour de son intersection avec l'axe du bien 2", ["Elle se translate parallèlement vers l'origine", "Elle pivote autour de son intersection avec l'axe du bien 1", "Elle reste inchangée"], "L'ordonnée à l'origine R/p2 est inchangée, l'abscisse à l'origine R/p1 augmente.", 4),
    ("Quelle forme ont les courbes d'indifférence entre deux biens parfaitement complémentaires ?", "Des angles droits (en forme de L)", ["Des droites de pente négative", "Des droites verticales parallèles", "Des cercles concentriques"], "Les biens sont consommés en proportions fixes (ex. chaussure gauche et chaussure droite).", 3),
    ("Quelle forme ont les courbes d'indifférence entre deux biens parfaitement substituables ?", "Des droites de pente négative", ["Des angles droits", "Des courbes concaves vers l'origine", "Des droites horizontales"], "Le taux marginal de substitution est alors constant.", 3),
    ("Si la demande d'un bien est élastique, que fait la recette totale du vendeur après une baisse du prix ?", "Elle augmente", ["Elle diminue", "Elle reste inchangée", "Elle devient nulle"], "La hausse des quantités l'emporte sur la baisse du prix unitaire.", 3),
    ("Si la demande d'un bien est inélastique, que fait la recette totale après une hausse du prix ?", "Elle augmente", ["Elle diminue", "Elle reste inchangée", "Elle double"], "La baisse des quantités est proportionnellement plus faible que la hausse du prix.", 3),
    ("Quel signe a l'élasticité-prix croisée entre deux biens substituts ?", "Positif", ["Négatif", "Nul", "Infini"], "La hausse du prix de l'un fait monter la demande de l'autre.", 3),
    ("Quel signe a l'élasticité-revenu de la demande d'un bien inférieur ?", "Négatif", ["Positif et supérieur à 1", "Positif et inférieur à 1", "Toujours nul"], "La demande de ce bien baisse quand le revenu augmente.", 3),
    ("Comment qualifie-t-on la demande d'un bien dont l'élasticité-revenu est supérieure à 1 ?", "Celle d'un bien de luxe", ["Celle d'un bien inférieur", "Celle d'un bien de Giffen", "Celle d'un bien public"], "La demande croît plus vite que le revenu.", 3),
    ("Que caractérise le court terme dans la théorie du producteur ?", "Au moins un facteur de production est fixe", ["Aucun facteur n'est fixe, tous peuvent être ajustés", "Le prix des biens est fixe", "La production est nécessairement nulle"], "Le long terme est la période où tous les facteurs sont ajustables.", 2),
    ("Que stipule la loi des rendements marginaux décroissants ?", "Au-delà d'un seuil, chaque unité de facteur variable ajoute moins à la production que la précédente", ["La production diminue dès la première unité de travail", "Le coût fixe baisse avec la production", "La productivité moyenne est toujours décroissante"], "Cela s'observe quand on ajoute un facteur à des facteurs fixes.", 3),
    ("Que signifient des rendements d'échelle croissants ?", "Multiplier tous les facteurs par λ multiplie la production par plus de λ", ["Multiplier un seul facteur multiplie la production par plus de λ", "La production diminue quand on ajoute du travail", "Le coût marginal est constant"], "Il s'agit d'une propriété de long terme, tous facteurs variant dans la même proportion.", 3),
    ("Quelle est la condition de maximisation du profit d'une firme preneuse de prix ?", "Le prix est égal au coût marginal (avec un coût marginal croissant)", ["Le prix est égal au coût fixe moyen", "La recette totale est égale au coût total", "Le coût moyen est minimal quel que soit le prix"], "Produire jusqu'à ce que la recette marginale (le prix) égale le coût marginal.", 3),
    ("Quelle est la condition de maximisation du profit d'un monopole ?", "La recette marginale est égale au coût marginal", ["Le prix est égal au coût marginal", "Le prix est égal au coût moyen minimal", "La recette moyenne est égale à zéro"], "Au monopole, le prix excède la recette marginale.", 3),
    ("Pour un monopole face à une demande décroissante, que peut-on dire de la recette marginale par rapport au prix ?", "Elle lui est inférieure", ["Elle lui est égale", "Elle lui est supérieure", "Elle est toujours constante"], "Pour vendre une unité de plus, il doit baisser le prix sur toutes les unités.", 4),
    ("À court terme, sous quelle condition une firme concurrentielle cesse-t-elle de produire ?", "Le prix est inférieur au minimum du coût variable moyen", ["Le prix est inférieur au coût marginal minimal", "Le prix est supérieur au coût fixe moyen", "Le profit est inférieur au profit de l'année précédente"], "En dessous, chaque unité vendue aggrave les pertes par rapport à l'arrêt.", 4),
    ("Que devient le profit économique d'une firme en concurrence parfaite à l'équilibre de long terme avec libre entrée ?", "Il est nul", ["Il est maximal", "Il est positif et croissant", "Il est négatif"], "Les profits attirent des entrants jusqu'à leur disparition.", 3),
    ("À court terme, quelle partie de la courbe de coût marginal constitue l'offre d'une firme concurrentielle ?", "La partie au-dessus du minimum du coût variable moyen", ["La partie de la courbe située au-dessous de la courbe du coût fixe moyen", "La partie décroissante", "La totalité de la courbe"], "Sous ce minimum, la firme ne produit pas.", 4),
    ("En quel point la courbe de coût marginal coupe-t-elle la courbe de coût moyen ?", "Au minimum du coût moyen", ["Au maximum du coût moyen", "À l'origine", "Au minimum du coût marginal"], "Quand le coût marginal est inférieur au coût moyen, ce dernier baisse ; quand il est supérieur, il monte.", 4),
    ("Qu'appelle-t-on perte sèche du monopole ?", "La perte de surplus total due à une quantité produite inférieure à l'optimum", ["Les pertes comptables du monopole", "Le gain de surplus du consommateur", "Le coût fixe du monopole"], "Des échanges mutuellement avantageux n'ont pas lieu.", 4),
    ("En quoi consiste la discrimination parfaite par les prix ?", "Faire payer à chaque consommateur le maximum qu'il est prêt à payer", ["Vendre au même prix à tous", "Vendre à perte aux plus pauvres", "Baisser le prix lorsque la quantité demandée baisse, afin de la rétablir pour tous"], "Le monopole s'approprie ainsi tout le surplus du consommateur.", 4),
    ("Quelle est la caractéristique d'un monopole naturel ?", "Des coûts moyens décroissants sur toute l'étendue de la demande", ["Un brevet détenu par l'État", "Un prix imposé par la loi", "Un grand nombre de petites firmes"], "Une seule firme produit alors au moindre coût.", 3),
    ("Laquelle de ces caractéristiques est propre à la concurrence monopolistique ?", "Des produits différenciés avec une libre entrée sur le marché", ["Un produit homogène et un vendeur unique", "Une barrière à l'entrée absolue", "Un nombre de vendeurs limité à deux"], "Chaque firme a un pouvoir de marché limité, sans barrière à l'entrée.", 3),
    ("Dans le modèle de Cournot, que choisissent simultanément les firmes ?", "Leurs quantités produites", ["Leurs prix", "Leur ordre de passage", "Leurs dépenses publicitaires uniquement"], "Chaque firme prend la production de l'autre comme donnée.", 4),
    ("Dans le modèle de Bertrand avec produit homogène et coûts identiques, quel est le prix d'équilibre ?", "Le coût marginal", ["Le prix de monopole", "Le double du coût marginal", "Un prix indéterminé"], "Chaque firme a intérêt à baisser légèrement son prix jusqu'au coût marginal (paradoxe de Bertrand).", 5),
    ("Dans le modèle de Stackelberg, qui joue en premier ?", "La firme meneuse choisit sa quantité avant la suiveuse", ["Chaque firme joue en même temps que l'autre", "La firme suiveuse impose son prix", "L'État fixe la quantité"], "La suiveuse réagit en observant le choix du leader.", 4),
    ("Pourquoi un cartel est-il généralement instable ?", "Chaque membre a intérêt à tricher en produisant davantage", ["Parce que la loi l'interdit toujours", "Parce que le prix monte toujours", "Parce que les consommateurs refusent d'acheter"], "Le prix élevé rend rentable le non-respect des quotas, ce qui est le dilemme du prisonnier.", 4),
    ("Dans un jeu, qu'est-ce qu'un équilibre de Nash ?", "Une combinaison de stratégies où aucun joueur ne gagne à dévier seul", ["La combinaison qui maximise la somme des gains", "La stratégie dominante d'un seul joueur", "Un accord imposé par un arbitre"], "Chaque stratégie est une meilleure réponse aux autres.", 3),
    ("Dans le dilemme du prisonnier, quel résultat obtient-on à l'équilibre ?", "Chaque joueur trahit (stratégie dominante) et le résultat est moins bon que la coopération", ["Chaque joueur coopère toujours", "Un seul joueur gagne tout", "Il n'existe aucun équilibre"], "La trahison est dominante mais le résultat commun est inférieur à celui de la coopération.", 3),
    ("Qu'appelle-t-on stratégie dominante ?", "Celle qui est la meilleure quel que soit le choix des autres joueurs", ["Celle qui rapporte le gain le plus élevé possible dans le jeu", "Celle choisie par la majorité des joueurs", "Celle qui impose son choix aux autres"], "Elle ne dépend pas des anticipations sur les autres.", 2),
    ("Quelle méthode utilise-t-on pour résoudre un jeu séquentiel fini ?", "L'induction à rebours", ["Le tâtonnement walrasien", "L'élimination des stratégies mixtes", "La loi des grands nombres"], "On part de la dernière décision et on remonte l'arbre.", 4),
    ("Dans un jeu répété un grand nombre de fois, qu'est-ce qui peut rendre la coopération soutenable ?", "La menace de représailles lors des tours futurs", ["La disparition de toute information", "L'interdiction de parler aux autres joueurs avant chaque tour de jeu", "Le fait de jouer une seule fois"], "L'avenir pèse sur les décisions présentes.", 4),
    ("Quel est le propre d'une externalité négative ?", "Le coût social marginal dépasse le coût privé marginal", ["Le coût privé marginal dépasse le coût social marginal", "Le prix baisse toujours", "L'agent reçoit une compensation intégrale"], "Le marché produit alors trop du bien polluant.", 3),
    ("Que dit le théorème de Coase ?", "Avec peu de coûts de transaction, la négociation mène à une solution efficace", ["L'État doit toujours taxer les pollueurs", "La pollution est impossible à réduire par le marché, quels que soient les coûts de transaction", "Les droits de propriété sont inutiles"], "La répartition initiale des droits n'affecte pas l'efficacité de la solution négociée.", 4),
    ("Quel est l'objectif d'une taxe pigouvienne ?", "Internaliser une externalité négative en alignant le coût privé sur le coût social", ["Augmenter les recettes de l'État quel que soit son effet", "Interdire la production du bien", "Subventionner le consommateur"], "Elle fait payer au pollueur le dommage qu'il cause.", 3),
    ("Qu'est-ce qu'un bien de club ?", "Un bien non rival mais exclusif", ["Un bien rival et non exclusif", "Un bien rival et exclusif", "Un bien non rival et non exclusif"], "L'accès est réservé aux membres, mais la consommation de l'un ne prive pas l'autre.", 3),
    ("Qu'est-ce qu'une ressource commune ?", "Un bien rival mais non exclusif", ["Un bien non rival et non exclusif", "Un bien non rival mais exclusif", "Un bien rival et exclusif"], "Exemple : un stock de poissons en haute mer.", 3),
    ("Qu'appelle-t-on tragédie des biens communs (Hardin) ?", "La surexploitation d'une ressource en accès libre faute de coordination", ["La disparition d'un bien public pur", "L'échec d'un monopole d'État", "L'effondrement des prix agricoles"], "Chaque usager ignore le coût qu'il impose aux autres.", 3),
    ("Quel problème pose le financement d'un bien public pur par contributions volontaires ?", "Le comportement de passager clandestin", ["L'excès de concurrence", "La rareté du travail", "La déflation"], "Chacun préfère ne pas payer en espérant que les autres paient.", 2),
    ("Que décrit le marché des « citrons » d'Akerlof ?", "La sélection adverse : les mauvais produits chassent les bons si la qualité est inconnue", ["Un marché de fruits en concurrence parfaite", "Une taxe sur les produits agricoles", "Un monopole d'État"], "Si l'acheteur ne peut distinguer la qualité, le prix moyen fait fuir les bons vendeurs.", 4),
    ("Un assuré, une fois couvert, prend moins de précautions. De quel problème s'agit-il ?", "De l'aléa moral", ["De la sélection adverse", "De l'effet de revenu", "Du passager clandestin"], "Le comportement change après la conclusion du contrat.", 3),
    ("Un assureur ne distingue pas les bons risques des mauvais avant la signature du contrat. De quel problème s'agit-il ?", "De la sélection adverse", ["De l'aléa moral", "De l'effet d'éviction", "Du dumping"], "L'asymétrie d'information précède le contrat.", 3),
    ("Dans le modèle de Spence, à quoi sert un diplôme ?", "À signaler aux employeurs la productivité d'un candidat", ["À augmenter mécaniquement sa productivité dans tous les cas", "À réduire le chômage frictionnel", "À fixer le salaire minimum"], "Le signal est coûteux, donc crédible, s'il est plus facile à obtenir pour les plus productifs.", 5),
    ("Qu'est-ce qu'un optimum de Pareto ?", "Une situation où l'on ne peut améliorer le sort d'un agent sans détériorer celui d'un autre", ["Une situation où tous les agents ont le même revenu", "Une situation où le profit des firmes est maximal", "Une situation où l'État fixe tous les prix"], "Il s'agit d'un critère d'efficacité, non d'équité.", 3),
    ("Que dit le premier théorème du bien-être ?", "Sous des hypothèses standard, un équilibre concurrentiel est un optimum de Pareto", ["Tout optimum de Pareto est équitable", "Le monopole est toujours efficace", "L'État doit fixer les prix"], "Les hypothèses incluent l'absence d'externalités et d'asymétries d'information.", 4),
    ("Que dit la loi de Walras ?", "À tous prix, la valeur de la somme des excès de demande sur l'ensemble des marchés est nulle", ["L'offre crée sa propre demande", "Le prix est égal au coût marginal", "La monnaie est neutre à court terme"], "Si n−1 marchés sont équilibrés, le dernier l'est aussi.", 5),
    ("À quoi sert la boîte d'Edgeworth ?", "Représenter l'échange de deux biens entre deux consommateurs", ["Représenter les coûts d'un monopole et de ses concurrents potentiels", "Calculer le PIB d'un pays", "Mesurer l'inflation"], "Elle sert à visualiser les allocations efficaces au sens de Pareto.", 4),
    ("Qu'est-ce que le surplus du producteur ?", "La différence entre le prix reçu et le coût minimal auquel il accepterait de vendre", ["Le prix payé par le consommateur", "Le coût moyen de production", "La subvention reçue de l'État"], "Il se lit entre le prix de marché et la courbe d'offre.", 2),
    ("Un prix plafond fixé en dessous du prix d'équilibre provoque en général ?", "Une pénurie", ["Un excédent d'offre", "Une hausse de la production", "Une baisse de la demande"], "À prix bas, la demande excède l'offre.", 2),
    ("Un prix plancher fixé au-dessus du prix d'équilibre provoque en général ?", "Un excédent d'offre", ["Une pénurie", "Une hausse de la demande", "Un équilibre stable"], "À prix élevé, l'offre excède la demande.", 2),
    ("Qui supporte la plus grande part d'une taxe sur un bien, toutes choses égales par ailleurs ?", "Le côté du marché dont l'élasticité est la plus faible", ["Le côté du marché dont l'élasticité est la plus forte, quelle que soit sa taille", "Toujours le vendeur", "Toujours l'acheteur"], "Le côté le moins capable de réagir en modifiant ses quantités porte plus de charge.", 4),
    ("Que mesure l'indice de Lerner d'un marché ?", "Le pouvoir de marché, par l'écart relatif entre le prix et le coût marginal", ["Le niveau de l'inflation", "Le degré d'ouverture commerciale", "Le coefficient de Gini"], "L = (P − Cm)/P ; il est nul en concurrence parfaite.", 5),
    ("Que mesure l'indice de Herfindahl-Hirschman ?", "La concentration d'un marché, par la somme des carrés des parts de marché", ["La somme des parts de marché non élevées au carré, divisée par le nombre de firmes", "L'inégalité des revenus", "Le taux de marge d'une firme"], "Plus il est élevé, plus le marché est concentré.", 4),
    ("Qu'est-ce qu'une barrière à l'entrée ?", "Un obstacle qui empêche de nouvelles firmes de pénétrer un marché", ["Un impôt sur les bénéfices distribués", "Une subvention à l'exportation", "Une limite de crédit des ménages"], "Brevets, coûts fixes élevés ou licences en sont des exemples.", 2),
    ("Que décrit la courbe de demande de travail d'une firme concurrentielle ?", "La productivité marginale du travail en valeur, comparée au salaire", ["Le coût moyen du capital", "La somme des salaires versés", "Le taux de chômage"], "La firme embauche tant que la valeur de la productivité marginale excède le salaire.", 4),
    ("Que traduit une fonction d'utilité concave en revenu pour un individu ?", "Une aversion pour le risque", ["Un goût pour le risque", "Une indifférence totale au risque", "Une préférence pour le présent"], "Il préfère l'espérance certaine à la loterie de même espérance.", 4),
    ("Que dit le critère de l'espérance d'utilité (von Neumann-Morgenstern) ?", "Un individu choisit l'option dont l'utilité moyenne pondérée par les probabilités est la plus élevée", ["Un individu choisit toujours l'option de gain monétaire maximal, sans tenir compte du risque", "Un individu évite tout risque", "Un individu ignore les probabilités"], "Il compare des espérances d'utilité, non des espérances de gain.", 5),
    ("Que résout, selon les marginalistes, le paradoxe de l'eau et du diamant ?", "La valeur dépend de l'utilité marginale et non de l'utilité totale", ["La valeur dépend du seul temps de travail", "La valeur dépend de la monnaie", "La valeur dépend du seul coût de transport et de la distance au marché"], "L'eau, abondante, a une utilité marginale faible.", 4),
    ("Quel est le critère de minimisation du coût pour une firme avec deux facteurs ?", "Le taux marginal de substitution technique égale le rapport des prix des facteurs", ["Le coût fixe égale le coût variable", "Chaque facteur est employé en quantité égale à l'autre, quel que soit son prix relatif", "Le prix du produit égale le salaire"], "L'isoquante est tangente à la droite d'isocoût.", 4),
    ("Que mesure le taux marginal de substitution technique ?", "La quantité d'un facteur cédée contre une unité de l'autre à production constante", ["Le taux de profit de la firme", "La part du travail dans le revenu national", "La productivité moyenne du capital"], "C'est la pente (en valeur absolue) de l'isoquante.", 4),
    ("Que mesure la courbe des possibilités de production ?", "Les combinaisons maximales de biens atteignables avec les ressources et la technique données", ["Les combinaisons de biens qu'un consommateur préfère", "Les prix d'équilibre de deux marchés", "Les recettes de l'État"], "Sa pente reflète un coût d'opportunité.", 2),
    ("Que montre l'allure concave de la frontière des possibilités de production ?", "Un coût d'opportunité croissant", ["Un coût d'opportunité constant", "Des rendements d'échelle constants", "Une baisse du chômage"], "Les facteurs sont inégalement adaptés à chaque bien.", 3),
    ("Quelle est la forme de la courbe des possibilités de production si le coût d'opportunité est constant ?", "Une droite", ["Une courbe convexe vers l'origine", "Une courbe concave vers l'origine", "Un L"], "La pente ne varie pas.", 3),
    ("Quel rôle doit jouer un coût irrécupérable dans la décision future d'une firme ?", "Aucun : il ne doit pas influencer le choix", ["Il doit être ajouté au coût marginal", "Il doit être divisé par la production pour obtenir un coût moyen futur", "Il doit remplacer le coût d'opportunité"], "Seuls les coûts et recettes à venir comptent.", 4),
], cat="Microéconomie", src="sup-eco-micro")

# =====================================================================================================================
# L2 : MACROÉCONOMIE, MONNAIE, COMMERCE
# =====================================================================================================================
table(L2, "eco2-mac-cn", [
    ("le PIB", "la somme des valeurs ajoutées, augmentée des impôts sur les produits nets de subventions", 3),
    ("la consommation intermédiaire", "la valeur des biens et services transformés ou détruits au cours de la production", 2),
    ("la formation brute de capital fixe", "l'acquisition par les unités productives de biens durables destinés à produire", 3),
    ("la variation des stocks", "la différence entre les entrées et les sorties de stocks sur la période", 3),
    ("le revenu national brut", "le PIB corrigé des revenus des facteurs reçus de l'étranger et versés à l'étranger", 4),
    ("le revenu disponible des ménages", "leurs revenus après impôts et cotisations, augmentés des prestations reçues", 3),
    ("l'épargne des ménages", "la part du revenu disponible qui n'est pas consommée", 2),
    ("le déflateur du PIB", "le rapport du PIB nominal au PIB réel, qui mesure l'évolution générale des prix", 3),
    ("le PIB réel", "le PIB évalué à prix constants, donc corrigé de l'inflation", 2),
    ("le PIB par habitant", "le PIB divisé par la population", 1),
    ("la consommation de capital fixe", "la dépréciation du capital fixe due à l'usure et à l'obsolescence", 3),
    ("les exportations nettes", "la différence entre les exportations et les importations de biens et services", 2),
], cat="Macroéconomie : comptabilité nationale", src="sup-eco-macro",
    fwd="Comment définit-on {a} en comptabilité nationale ?", rev="Quel agrégat ou concept de comptabilité nationale est décrit ici : {b} ?")

table(L2, "eco2-mac-chom", [
    ("le chômage frictionnel", "un chômage de courte durée lié à la recherche d'un emploi et à la mobilité", 3),
    ("le chômage structurel", "un chômage durable dû à l'inadéquation entre qualifications offertes et emplois demandés", 3),
    ("le chômage conjoncturel", "un chômage lié aux fluctuations de l'activité, notamment en période de récession", 3),
    ("le chômage classique", "un chômage dû à un salaire réel supérieur à celui qui équilibre le marché du travail", 4),
    ("le chômage saisonnier", "un chômage lié aux variations de l'activité selon les saisons", 2),
    ("le chômage technologique", "un chômage dû au remplacement de travailleurs par des machines ou des procédés nouveaux", 3),
    ("le chômage volontaire", "la situation de personnes qui refusent les emplois proposés aux salaires en vigueur", 3),
    ("le sous-emploi", "la situation de personnes qui travaillent moins qu'elles ne le souhaitent", 3),
    ("le taux d'activité", "le rapport de la population active à la population en âge de travailler", 3),
    ("le taux de chômage", "le rapport du nombre de chômeurs à la population active", 2),
], cat="Macroéconomie : emploi et chômage", src="sup-eco-macro",
    fwd="Comment définit-on {a} ?", rev="Quel concept relatif à l'emploi est décrit ici : {b} ?")

table(L2, "eco2-mac-infl", [
    ("l'inflation par la demande", "une hausse des prix due à une demande globale supérieure à l'offre disponible", 3),
    ("l'inflation par les coûts", "une hausse des prix due à l'augmentation des coûts de production intérieurs (salaires, marges)", 3),
    ("l'inflation importée", "une hausse des prix due au renchérissement des produits achetés à l'étranger", 3),
    ("l'hyperinflation", "une inflation extrêmement rapide qui détruit la valeur de la monnaie", 3),
    ("la désinflation", "un ralentissement du rythme de hausse des prix sans baisse du niveau des prix", 4),
    ("la stagflation", "la coexistence d'une faible croissance, d'un chômage élevé et d'une inflation forte", 3),
    ("l'inflation sous-jacente", "la mesure de l'inflation qui exclut les prix les plus volatils, comme l'énergie", 4),
    ("l'inflation anticipée", "la hausse des prix que les agents prévoient et intègrent dans leurs décisions", 3),
], cat="Macroéconomie : inflation", src="sup-eco-macro",
    fwd="Comment définit-on {a} ?", rev="Quel concept d'inflation correspond à : {b} ?")

table(L3, "eco3-mac-pol", [
    ("la politique budgétaire", "l'action sur l'économie par les dépenses publiques, les impôts et le solde du budget", 2),
    ("la politique monétaire", "l'action sur l'économie par les taux d'intérêt et la quantité de monnaie", 2),
    ("la politique de change", "l'action sur la valeur de la monnaie nationale par rapport aux devises", 3),
    ("la politique structurelle", "l'action sur les institutions et les structures de l'économie à long terme", 4),
    ("les stabilisateurs automatiques", "les mécanismes budgétaires qui amortissent les fluctuations sans nouvelle décision", 4),
    ("l'effet d'éviction", "la baisse de l'investissement privé causée par un emprunt public qui fait monter les taux", 4),
    ("l'effet multiplicateur", "l'amplification d'une hausse initiale de la dépense par les revenus qu'elle engendre", 3),
    ("l'équivalence ricardienne", "l'idée qu'un déficit n'a pas d'effet réel car les ménages anticipent les impôts futurs", 5),
    ("la courbe de Laffer", "la relation en cloche entre le taux d'imposition et les recettes fiscales", 4),
    ("le solde primaire", "le solde budgétaire hors charges d'intérêts de la dette", 4),
    ("la dette publique", "le stock des engagements financiers cumulés de l'État", 2),
    ("le déficit public", "le flux annuel d'insuffisance des recettes par rapport aux dépenses", 2),
], cat="Macroéconomie : politiques économiques", src="sup-eco-macro",
    fwd="Comment définit-on {a} ?", rev="Quel concept de politique économique correspond à : {b} ?")

table(L2, "eco2-mon-concepts", [
    ("la monnaie fiduciaire", "les pièces et billets, dont la valeur repose sur la confiance", 2),
    ("la monnaie scripturale", "les soldes de comptes bancaires mobilisables par chèque, virement ou carte", 2),
    ("la monnaie marchandise", "un bien utilisé comme monnaie parce qu'il a une valeur propre", 3),
    ("la base monétaire", "les billets en circulation et les réserves des banques auprès de la banque centrale", 4),
    ("la liquidité d'un actif", "la facilité avec laquelle il se convertit en moyen de paiement sans perte", 2),
    ("la vitesse de circulation de la monnaie", "le nombre moyen de fois qu'une unité monétaire sert à payer pendant la période", 4),
    ("le seigneuriage", "le profit tiré de l'émission de monnaie, écart entre sa valeur faciale et son coût", 5),
    ("le taux d'intérêt nominal", "le taux exprimé sans correction de l'inflation", 2),
    ("le taux d'intérêt réel", "le taux nominal corrigé de l'inflation", 2),
    ("le taux directeur", "le taux auquel la banque centrale refinance les banques et qui oriente les autres taux", 2),
    ("les réserves obligatoires", "la part des dépôts que les banques doivent conserver auprès de la banque centrale", 3),
    ("les opérations d'open market", "les achats et ventes de titres par la banque centrale pour agir sur la liquidité", 4),
    ("le prêteur en dernier ressort", "le rôle de la banque centrale qui fournit des liquidités aux banques en difficulté", 3),
], cat="Monnaie et banque", src="sup-eco-monnaie",
    fwd="Comment définit-on {a} ?", rev="Quel concept monétaire correspond à : {b} ?")

table(L3, "eco3-mon-risques", [
    ("le risque de crédit", "la possibilité qu'un emprunteur ne rembourse pas tout ou partie de sa dette", 2),
    ("le risque de liquidité", "la possibilité de ne pouvoir faire face aux retraits ou échéances à court terme", 3),
    ("le risque de taux", "la possibilité de perte liée à la variation des taux d'intérêt", 3),
    ("le risque de change", "la possibilité de perte liée à la variation du cours d'une monnaie", 3),
    ("le risque opérationnel", "la possibilité de perte due à des défaillances de procédures, de personnes ou de systèmes", 4),
    ("le risque systémique", "la possibilité que la défaillance d'un acteur se propage à tout le système financier", 4),
    ("la solvabilité d'une banque", "son aptitude à honorer ses dettes à long terme grâce à ses fonds propres", 4),
], cat="Monnaie et banque : risques", src="sup-eco-monnaie",
    fwd="Comment définit-on {a} ?", rev="Quel risque ou notion bancaire est décrit ici : {b} ?")

table(L2, "eco2-com-instr", [
    ("l'avantage absolu", "la capacité de produire un bien avec moins de ressources qu'un autre pays", 2),
    ("l'avantage comparatif", "la capacité de produire un bien à un coût d'opportunité plus faible qu'un autre pays", 3),
    ("le droit de douane", "une taxe prélevée sur les marchandises à leur entrée sur le territoire", 1),
    ("le quota d'importation", "une limite quantitative fixée aux marchandises qui peuvent entrer", 2),
    ("la subvention à l'exportation", "une aide publique qui abaisse le prix auquel les producteurs vendent à l'étranger", 3),
    ("le dumping", "la vente à l'étranger à un prix inférieur à celui du marché d'origine ou au coût", 3),
    ("la clause de la nation la plus favorisée", "l'obligation d'étendre à tous les membres l'avantage accordé à l'un d'eux", 4),
    ("les termes de l'échange", "le rapport entre l'indice des prix des exportations et celui des importations", 4),
    ("le taux de couverture", "le rapport des exportations aux importations", 3),
    ("le protectionnisme éducateur", "la protection temporaire d'industries naissantes pour qu'elles deviennent compétitives", 4),
    ("une barrière non tarifaire", "une mesure autre qu'un droit ou un quota qui gêne les importations, comme une norme technique", 3),
], cat="Commerce international", src="sup-eco-commerce",
    fwd="Comment définit-on {a} en commerce international ?", rev="Quel concept de commerce international est décrit ici : {b} ?")

table(L2, "eco2-com-integ", [
    ("une zone de libre-échange", "un espace où les droits de douane entre membres sont supprimés, chacun gardant ses tarifs externes", 3),
    ("une union douanière", "une zone de libre-échange dotée d'un tarif extérieur commun", 3),
    ("un marché commun", "une union douanière où le travail et le capital circulent aussi librement", 4),
    ("une union économique et monétaire", "un marché commun doté d'une monnaie commune et de politiques coordonnées", 4),
    ("une préférence tarifaire", "un tarif réduit accordé à certains partenaires seulement", 4),
], cat="Commerce international : intégration régionale", src="sup-eco-commerce",
    fwd="Comment définit-on {a} ?", rev="De quel niveau d'intégration s'agit-il : {b} ?")

table(L3, "eco3-com-change", [
    ("le taux de change nominal", "le prix d'une monnaie exprimé en unités d'une autre", 2),
    ("le taux de change réel", "le taux nominal corrigé des écarts de prix entre pays", 4),
    ("un régime de change fixe", "un régime où la banque centrale maintient la parité de la monnaie avec une autre ou un panier", 3),
    ("un régime de change flottant", "un régime où le cours de la monnaie est déterminé par l'offre et la demande", 3),
    ("le flottement administré", "un régime où le cours flotte mais la banque centrale intervient pour le guider", 4),
    ("la dépréciation", "une baisse de la valeur d'une monnaie sur le marché des changes", 3),
    ("la dévaluation", "une baisse décidée officiellement de la parité d'une monnaie à change fixe", 3),
    ("la réévaluation", "une hausse décidée officiellement de la parité d'une monnaie à change fixe", 3),
    ("l'appréciation", "une hausse de la valeur d'une monnaie sur le marché des changes", 3),
    ("la parité de pouvoir d'achat", "la théorie selon laquelle le taux de change reflète le rapport des niveaux de prix", 4),
], cat="Commerce international : change", src="sup-eco-commerce",
    fwd="Comment définit-on {a} ?", rev="Quel concept de change est décrit ici : {b} ?")

table(L3, "eco3-com-bp", [
    ("la balance courante", "le solde des échanges de biens, de services, de revenus et de transferts courants", 3),
    ("le compte financier", "les mouvements d'investissements directs, de portefeuille et d'autres placements", 4),
    ("les réserves de change", "les actifs en devises détenus par la banque centrale", 3),
    ("un investissement direct étranger", "une participation durable dans une entreprise étrangère pour exercer un contrôle ou une influence", 3),
    ("la balance des services", "le solde des exportations et des importations de services, comme le transport ou le tourisme", 3),
    ("la balance des revenus", "le solde des revenus du travail et du capital reçus de l'étranger et versés à l'étranger", 4),
    ("la balance des transferts courants", "le solde des dons et envois de fonds sans contrepartie, notamment des migrants", 3),
], cat="Balance des paiements", src="sup-eco-commerce",
    fwd="Comment définit-on {a} ?", rev="Quel poste de la balance des paiements est décrit ici : {b} ?")

split(L2, L3, "eco2-mac-q1", [
    ("Quelles sont les trois optiques de calcul du PIB ?", "La production, la dépense et le revenu", ["La production, l'épargne et l'impôt", "L'offre, la demande et le prix", "Le travail, le capital et la terre"], "Les trois optiques donnent le même agrégat par construction comptable.", 3),
    ("Dans l'optique des dépenses, comment écrit-on le PIB d'une économie ouverte ?", "C + I + G + (X − M)", ["C + S + T", "C × I × G", "C + S + G + (M − X)"], "Consommation, investissement, dépenses publiques et exportations nettes.", 2),
    ("Quelle différence y a-t-il entre PIB nominal et PIB réel ?", "Le PIB réel est évalué à prix constants, le PIB nominal aux prix courants", ["Le PIB réel exclut les services", "Le PIB nominal exclut l'État", "Le PIB réel est exprimé en devises"], "Seul le PIB réel permet de mesurer l'évolution des quantités produites.", 2),
    ("Comment calcule-t-on le déflateur du PIB ?", "PIB nominal divisé par PIB réel (×100)", ["PIB réel divisé par PIB nominal (×100)", "PIB nominal moins PIB réel", "PIB réel multiplié par la population"], "Il mesure l'évolution du niveau général des prix de la production.", 3),
    ("Que comprend le produit national brut par rapport au PIB ?", "Il ajoute les revenus de facteurs reçus de l'étranger et retranche ceux versés à l'étranger", ["Il exclut les exportations", "Il inclut le travail domestique non rémunéré", "Il est toujours inférieur au PIB"], "Le PIB est territorial, le revenu national porte sur les résidents.", 4),
    ("Laquelle de ces activités n'entre généralement pas dans le PIB mesuré ?", "Le travail domestique non rémunéré au sein du foyer", ["La production d'un service public d'éducation", "La production d'une usine exportatrice", "Les loyers de logements loués"], "La comptabilité nationale retient les activités marchandes et certaines non marchandes évaluées au coût, pas ce travail.", 3),
    ("Que mesure la formation brute de capital fixe ?", "Les achats de biens durables destinés à la production, comme les machines", ["Les achats de biens de consommation durables des ménages", "Les achats de titres financiers", "Le seul stock de marchandises invendues"], "C'est l'investissement productif au sens de la comptabilité nationale.", 3),
    ("Parmi ces opérations, laquelle est un investissement au sens de la comptabilité nationale ?", "L'achat d'une machine par une entreprise", ["L'achat d'actions en Bourse par un ménage", "L'achat d'un téléphone par un ménage pour son usage", "Le versement d'un salaire"], "L'achat de titres est un placement financier, non une dépense de capital.", 3),
    ("Comment calcule-t-on le taux d'épargne des ménages ?", "Épargne divisée par revenu disponible", ["Revenu disponible divisé par épargne", "Épargne divisée par le PIB de l'État", "Consommation divisée par épargne"], "On rapporte l'épargne au revenu disponible brut.", 2),
    ("Que décrit le circuit économique ?", "Les flux réels et monétaires entre les agents économiques", ["Les trajets des marchandises par route", "Le calendrier du budget de l'État", "Les phases du cycle des affaires"], "Il relie ménages, entreprises, administrations, institutions financières et reste du monde.", 2),
    ("À qui doit-on le Tableau économique, première représentation du circuit ?", "À François Quesnay", ["À Adam Smith", "À Léon Walras", "À John Maynard Keynes"], "Il l'a conçu au XVIIIe siècle dans le courant physiocratique.", 3),
    ("Quelle est la définition habituelle de la croissance économique ?", "L'augmentation soutenue, sur longue période, de la production d'une économie", ["La hausse des prix sur une année", "L'augmentation du nombre d'habitants", "La hausse du déficit public"], "On la mesure par le taux de variation du PIB réel.", 2),
    ("Quelle différence y a-t-il entre croissance et développement ?", "Le développement ajoute des transformations structurelles et sociales à la hausse de la production", ["La croissance désigne uniquement le secteur agricole", "Le développement désigne uniquement les exportations", "Ils sont toujours strictement synonymes"], "Un pays peut croître sans que les conditions de vie de tous ne s'améliorent.", 3),
    ("Comment appelle-t-on la phase du cycle économique où la production recule ?", "La récession", ["L'expansion", "La reprise", "La désinflation"], "On parle souvent de récession quand le recul du PIB persiste plusieurs trimestres.", 2),
    ("Quelle est la bonne succession des phases d'un cycle classique ?", "Expansion, sommet, récession, creux", ["Creux, récession, sommet, expansion", "Sommet, expansion, creux, récession", "Récession, expansion, creux, sommet"], "Le cycle alterne montée puis baisse de l'activité autour de la tendance.", 3),
    ("Selon le Bureau international du travail (BIT), qu'est-ce qu'un chômeur ?", "Une personne sans emploi, disponible pour travailler et en recherche active", ["Toute personne sans emploi, y compris les retraités", "Une personne qui refuse tout travail", "Un étudiant à temps plein"], "Les trois critères sont cumulatifs.", 3),
    ("Comment calcule-t-on le taux de chômage ?", "Nombre de chômeurs divisé par la population active", ["Nombre de chômeurs divisé par la population totale", "Nombre d'inactifs divisé par la population active", "Nombre d'actifs occupés divisé par les chômeurs"], "La population active regroupe actifs occupés et chômeurs.", 2),
    ("De quoi se compose la population active ?", "Des actifs occupés et des chômeurs", ["Des chômeurs et des retraités", "Des étudiants et des chômeurs", "Des actifs occupés et des inactifs"], "Les inactifs (étudiants, retraités, etc.) n'en font pas partie.", 2),
    ("Que mesure le taux d'emploi ?", "La part des personnes ayant un emploi dans la population en âge de travailler", ["La part des chômeurs dans la population active", "La part des emplois publics dans l'emploi total", "Le nombre d'heures travaillées par semaine"], "Il se distingue du taux d'activité, qui inclut les chômeurs.", 3),
    ("Qu'est-ce que le secteur informel ?", "L'ensemble des activités économiques échappant en grande partie à l'enregistrement et à la fiscalité", ["L'ensemble des services publics gratuits", "Les activités exercées uniquement la nuit", "Les seules activités agricoles de subsistance"], "Il est très présent dans de nombreuses économies en développement.", 3),
    ("Selon Keynes, d'où vient le chômage involontaire ?", "D'une insuffisance de la demande effective", ["D'un excès de syndicats", "D'une trop forte épargne publique", "Du seul refus des chômeurs de travailler"], "L'ajustement par les salaires ne suffit pas à rétablir le plein emploi.", 3),
    ("Selon les économistes classiques, que provoque un salaire réel supérieur à son niveau d'équilibre ?", "Un chômage dit classique", ["Un chômage frictionnel", "Une inflation par la demande", "Un excédent budgétaire"], "L'offre de travail excède alors la demande de travail.", 4),
    ("Quelle relation la courbe de Phillips originelle (1958) établit-elle ?", "Une relation inverse entre le chômage et la variation des salaires nominaux", ["Une relation positive entre chômage et inflation", "Une relation inverse entre le PIB et la dette", "Une relation positive entre le taux d'intérêt et la production"], "Phillips l'avait observée pour le Royaume-Uni sur une longue période.", 3),
    ("Quel phénomène des années 1970 a remis en cause la stabilité de l'arbitrage inflation-chômage ?", "La stagflation", ["La déflation", "La dévaluation", "Le plein-emploi"], "Inflation et chômage s'élevaient ensemble.", 4),
    ("Selon Friedman et Phelps, à long terme, comment est la courbe de Phillips ?", "Verticale au niveau du taux de chômage naturel", ["Horizontale au niveau du taux d'inflation d'équilibre de court terme", "Toujours décroissante", "Croissante"], "Les agents ajustent leurs anticipations : on ne peut pas maintenir un chômage inférieur à son niveau naturel par l'inflation.", 4),
    ("Que dit la relation de Fisher ?", "Le taux d'intérêt nominal est égal au taux réel plus l'inflation anticipée", ["Le taux réel est égal au taux nominal multiplié par le PIB", "L'inflation est égale au taux de croissance de la production nominale du pays", "Le taux nominal est toujours nul"], "Au niveau d'approximation usuel.", 4),
    ("Que dit la loi d'Okun ?", "Il existe une relation inverse entre la croissance de la production et la variation du chômage", ["L'inflation est toujours un phénomène monétaire", "Les dépenses publiques croissent plus vite que le PIB", "La part de l'alimentation baisse avec le revenu"], "Une croissance forte tend à faire baisser le chômage.", 4),
    ("Que dit la loi d'Engel ?", "La part du revenu consacrée à l'alimentation diminue quand le revenu augmente", ["La part de l'épargne diminue avec l'âge", "La mauvaise monnaie chasse la bonne", "Le chômage baisse quand le PIB croît"], "Observée à partir des budgets de familles.", 3),
    ("Que dit la loi de Gresham ?", "La mauvaise monnaie chasse la bonne", ["La bonne monnaie chasse la mauvaise", "La monnaie est neutre", "L'offre crée sa propre demande"], "Quand deux monnaies circulent à un cours légal fixe, la moins bonne reste en circulation.", 4),
    ("Que dit la théorie quantitative de la monnaie (MV = PT) ?", "À vitesse et production données, les prix varient comme la masse monétaire", ["Les prix ne dépendent que du salaire minimum", "La monnaie n'a aucun effet sur les prix, même à long terme, ni sur la production", "La vitesse de circulation est égale à la production"], "Elle inspire l'idée d'une inflation liée à l'excès de monnaie.", 4),
    ("Qu'affirmait Milton Friedman à propos de l'inflation ?", "Elle est toujours et partout un phénomène monétaire", ["Elle est toujours causée par les salaires", "Elle disparaît avec un déficit budgétaire", "Elle ne dépend jamais de la monnaie"], "Il met en cause la croissance trop rapide de la masse monétaire.", 3),
    ("Quel est le principal effet d'une inflation non anticipée sur un débiteur à taux fixe ?", "Le poids réel de sa dette diminue", ["Le poids réel de sa dette augmente", "Sa dette devient nulle", "Son salaire réel diminue forcément"], "Il rembourse avec une monnaie qui vaut moins qu'à l'emprunt.", 4),
    ("Que fait une politique budgétaire de relance ?", "Elle augmente les dépenses publiques ou baisse les impôts pour soutenir la demande", ["Elle augmente les taux directeurs pour freiner le crédit", "Elle réduit la masse monétaire", "Elle dévalue la monnaie"], "Elle vise à stimuler la demande globale.", 2),
    ("Lequel est un exemple de stabilisateur automatique ?", "Les allocations de chômage qui augmentent en période de récession", ["Une loi de finances rectificative votée en urgence", "Une hausse décidée du taux directeur", "Une dévaluation décidée par le gouvernement"], "Le mécanisme joue sans nouvelle décision politique.", 3),
    ("Que décrit l'effet d'éviction ?", "La baisse de l'investissement privé quand l'emprunt public fait monter les taux d'intérêt", ["La hausse de l'inflation importée", "La disparition d'un monopole", "L'expulsion d'un agent d'un marché"], "Plus d'emprunt public peut renchérir le crédit pour le privé.", 3),
    ("Comment la courbe de Laffer lie-t-elle le taux d'imposition aux recettes fiscales ?", "Les recettes croissent puis décroissent quand le taux devient très élevé", ["Les recettes croissent toujours avec le taux", "Les recettes sont indépendantes du taux", "Les recettes baissent dès le premier taux positif"], "Un taux trop élevé décourage l'activité et réduit l'assiette.", 3),
    ("Quelle est la différence entre déficit et dette publics ?", "Le déficit est un flux annuel, la dette un stock accumulé", ["Le déficit est un stock, la dette un flux annuel", "Ils sont toujours identiques", "Le déficit concerne les ménages, la dette l'État"], "La dette est la somme des déficits passés, nette des excédents.", 2),
    ("Que mesure le solde primaire ?", "Recettes moins dépenses hors charges d'intérêts de la dette", ["Recettes moins dépenses, intérêts de la dette publique compris", "La dette divisée par le PIB", "L'épargne nationale"], "Il isole l'effort budgétaire indépendamment du poids de la dette passée.", 4),
    ("Dans le modèle keynésien simple sans impôts ni importations, si la propension marginale à consommer vaut c, que vaut le multiplicateur ?", "1/(1 − c)", ["1/c", "c/(1 − c)", "(1 − c)/c"], "Chaque euro de dépense initial est dépensé en proportion c à chaque tour.", 3),
    ("Que montre le théorème d'Haavelmo du budget équilibré ?", "Dans le modèle simple, une hausse égale des dépenses et des impôts accroît le revenu du même montant", ["Un budget équilibré ne peut jamais exister", "Les dépenses publiques réduisent toujours le revenu", "Les impôts augmentent toujours le revenu davantage que les dépenses publiques de même montant"], "Le multiplicateur du budget équilibré vaut 1.", 5),
    ("Quelle est l'idée de l'équivalence ricardienne (Barro) ?", "Un déficit n'accroît pas la demande : les ménages épargnent en prévision des impôts futurs", ["Les prix ne dépendent que des coûts", "Un déficit relance toujours l'activité", "La dette extérieure est toujours soutenable"], "Le mode de financement (impôt ou dette) devient indifférent.", 5),
    ("Que peut-on dire de la soutenabilité de la dette si la croissance du PIB nominal dépasse le taux d'intérêt, avec un solde primaire nul ?", "Le ratio dette/PIB tend à baisser", ["Le ratio dette/PIB augmente nécessairement", "La dette devient nulle", "L'inflation est forcément nulle"], "Le dénominateur croît plus vite que les intérêts.", 5),
    ("Comment s'écrit en principe la dynamique de la dette en pourcentage du PIB ?", "Elle dépend de l'écart entre le taux d'intérêt et la croissance, et du solde primaire", ["Elle dépend seulement du taux de change", "Elle dépend uniquement du taux de chômage", "Elle dépend de l'âge moyen des contribuables"], "Un écart intérêt-croissance positif alourdit le ratio, un excédent primaire l'allège.", 5),
], cat="Macroéconomie", src="sup-eco-macro")

split(L2, L3, "eco2-mon-q1", [
    ("Quelles sont les trois fonctions classiques de la monnaie ?", "Intermédiaire des échanges, unité de compte, réserve de valeur", ["Épargne, crédit, assurance", "Production, distribution, consommation", "Impôt, dette, change"], "Ces fonctions s'appuient sur la confiance dans la monnaie.", 2),
    ("Pourquoi la monnaie résout-elle le problème du troc ?", "Elle supprime la nécessité d'une double coïncidence des besoins", ["Elle supprime toute inflation", "Elle interdit le crédit", "Elle fixe le prix de tous les biens"], "Avec la monnaie, chacun vend contre monnaie puis achète ce dont il a besoin.", 3),
    ("Quel est le rôle de la monnaie comme unité de compte ?", "Exprimer les prix et les dettes dans un même étalon", ["Garantir les dépôts bancaires", "Financer les entreprises", "Fixer le taux d'intérêt"], "On compare ainsi des biens différents.", 2),
    ("Comment les banques commerciales créent-elles de la monnaie ?", "En accordant des crédits qui se traduisent par des dépôts", ["En imprimant des billets", "En prélevant des impôts", "En fixant le taux directeur"], "Un crédit crée un dépôt, donc de la monnaie scripturale.", 3),
    ("Quelle est la fonction du multiplicateur de crédit simple ?", "Montrer qu'un dépôt peut engendrer un volume de monnaie supérieur par les crédits successifs", ["Mesurer la hausse des prix", "Calculer le taux de change", "Fixer le salaire minimum"], "Il est lié à la part des dépôts conservée en réserves.", 4),
    ("Dans le modèle simple du multiplicateur de crédit, quelle est la valeur maximale du multiplicateur si le taux de réserves vaut r ?", "1/r", ["r", "r/(1 + r)", "(1 − r)/r"], "Chaque tour de prêts réinjecte (1 − r) des dépôts reçus.", 4),
    ("De quoi la base monétaire est-elle composée ?", "Des billets en circulation et des réserves des banques à la banque centrale", ["Des dépôts à vue des ménages uniquement, à l'exclusion de tout billet et de toute réserve", "Des actions cotées", "Des créances de l'État sur les ménages et les entreprises du secteur privé"], "C'est la monnaie émise par la banque centrale.", 4),
    ("Laquelle de ces missions relève d'une banque centrale ?", "Conduire la politique monétaire et émettre la monnaie légale", ["Collecter l'impôt sur les sociétés", "Octroyer les crédits de campagne aux particuliers", "Fixer les prix des produits de base"], "Elle est la banque des banques.", 2),
    ("Que signifie « prêteur en dernier ressort » ?", "La banque centrale fournit des liquidités aux banques en difficulté pour éviter la panique", ["L'État rembourse les créanciers des entreprises", "Le dernier prêt accordé est toujours remboursé en premier", "Le FMI finance les ménages"], "Elle protège ainsi la stabilité du système financier.", 3),
    ("Que se passe-t-il, en principe, quand la banque centrale relève son taux directeur ?", "Le crédit devient plus cher, ce qui freine la demande et l'inflation", ["Le crédit devient moins cher et la demande augmente", "La monnaie perd sa fonction de réserve", "Les prix baissent immédiatement"], "C'est le canal du taux d'intérêt de la politique monétaire.", 2),
    ("Quelle opération d'open market injecte de la liquidité dans le système bancaire ?", "L'achat de titres par la banque centrale", ["La vente de titres par la banque centrale", "Le relèvement des réserves obligatoires", "L'augmentation du taux directeur"], "Elle paie les titres en monnaie centrale.", 4),
    ("Quel effet a une hausse des réserves obligatoires ?", "Elle réduit la capacité de crédit des banques", ["Elle augmente la capacité de crédit des banques", "Elle augmente directement les prix", "Elle supprime le risque de crédit"], "Les banques ont moins de ressources à prêter.", 3),
    ("Quelle est la différence essentielle entre marché monétaire et marché financier ?", "Le marché monétaire traite les capitaux à court terme, le marché financier les capitaux à long terme", ["Le marché monétaire est réservé aux particuliers", "Le marché financier ne traite que des devises", "Il n'y a aucune différence"], "Actions et obligations relèvent du marché financier.", 3),
    ("Qu'est-ce que le marché interbancaire ?", "Le marché où les banques se prêtent des liquidités à court terme", ["Le marché où les banques vendent des actions à leurs clients", "Le marché où l'État fixe le prix de l'or", "Le marché des changes manuels"], "Il fait partie du marché monétaire.", 3),
    ("Que représente une action ?", "Une part de capital d'une société, donnant en principe droit à des dividendes et à un vote", ["Une créance remboursable à échéance avec intérêts fixes", "Un dépôt à vue", "Un billet à ordre"], "L'actionnaire est propriétaire d'une fraction de la société.", 2),
    ("Que représente une obligation ?", "Une part d'emprunt, créance sur l'émetteur rémunérée par des intérêts", ["Une part de capital d'une société", "Un droit de vote en assemblée", "Un titre de propriété immobilière"], "Le porteur est prêteur, non propriétaire.", 2),
    ("Que se passe-t-il sur le marché primaire ?", "Les titres nouvellement émis sont vendus pour la première fois", ["Les titres déjà émis sont échangés entre investisseurs", "Les devises sont échangées", "Les dettes sont annulées"], "Le marché secondaire est celui de la revente.", 3),
    ("Qu'est-ce que l'intermédiation financière ?", "Le rôle des banques qui collectent l'épargne et la transforment en crédits", ["La fixation des prix agricoles", "La gestion du budget de l'État", "La collecte de l'impôt"], "Elle met en relation agents à capacité et à besoin de financement.", 3),
    ("Qu'est-ce que la transformation d'échéances par une banque ?", "Elle finance des prêts à long terme avec des ressources à court terme", ["Elle prête exclusivement des devises aux États et jamais aux entreprises ni aux ménages", "Elle émet des chèques pour l'État", "Elle remplace les dépôts par des actions"], "Elle l'expose à un risque de liquidité.", 4),
    ("Quel rôle joue le Comité de Bâle ?", "Élaborer des normes de supervision prudentielle des banques", ["Fixer le taux directeur mondial", "Émettre une monnaie internationale commune aux banques de la zone", "Arbitrer les litiges commerciaux"], "Les règles de Bâle fixent notamment des exigences de fonds propres.", 4),
    ("Pourquoi les banques doivent-elles disposer de fonds propres suffisants ?", "Pour absorber des pertes sans mettre en danger les déposants", ["Pour payer les salaires de l'État", "Pour acheter des devises", "Pour éviter de verser des intérêts"], "C'est l'objet des ratios de solvabilité.", 3),
    ("Que protège un système de garantie des dépôts ?", "Une partie des dépôts des clients en cas de défaillance d'une banque", ["Les actionnaires de la banque", "Les emprunts de l'État", "Les bénéfices bancaires"], "Il vise à éviter les ruées bancaires.", 3),
    ("Quel est le principal motif de la ruée bancaire ?", "La crainte de ne pas pouvoir retirer ses dépôts", ["Une hausse du taux directeur", "Une baisse du salaire minimum", "Une hausse des exportations"], "Chacun retire pour ne pas être le dernier servi, ce qui peut provoquer la faillite.", 3),
    ("Selon Keynes, quels sont les trois motifs de détention de monnaie ?", "Transaction, précaution et spéculation", ["Épargne, crédit et assurance", "Salaire, profit et rente", "Production, échange, consommation et répartition des revenus"], "Le motif de spéculation dépend du taux d'intérêt.", 4),
    ("Qu'appelle-t-on trappe à liquidité ?", "Une situation où, à taux très bas, la monnaie supplémentaire est thésaurisée", ["Une panne du système de paiement interbancaire qui bloque les règlements pendant plusieurs jours", "Une fuite des capitaux vers l'étranger", "Une hausse brutale du taux directeur"], "La politique monétaire perd alors de son efficacité.", 4),
    ("Qu'est-ce que la neutralité de la monnaie ?", "À long terme, la monnaie n'influence que les variables nominales, pas les réelles", ["La monnaie n'a aucune valeur", "La monnaie ne circule jamais", "La banque centrale ne peut émettre de monnaie qu'avec l'accord du ministère chargé du budget"], "Elle est discutée à court terme.", 4),
    ("Quelle est la différence entre le taux d'intérêt nominal et le taux d'intérêt réel ?", "Le taux réel est corrigé de l'inflation, le taux nominal ne l'est pas", ["Le taux nominal est fixé par l'État, le taux réel par la banque", "Le taux réel s'applique aux seules entreprises", "Il n'y a aucune différence"], "Taux réel ≈ taux nominal − inflation.", 2),
    ("Quel type de monnaie représente la majeure partie de la masse monétaire dans une économie moderne ?", "La monnaie scripturale", ["Les pièces métalliques", "Les billets", "L'or monétaire"], "Elle est créée par le système bancaire à travers les dépôts.", 3),
    ("Que contiennent généralement les agrégats monétaires M1, M2, M3, du plus étroit au plus large ?", "Des actifs de moins en moins liquides, M1 étant le plus liquide", ["Des actifs de plus en plus liquides", "Seulement des devises", "Seulement des dépôts à terme"], "Leur définition précise varie selon les banques centrales.", 3),
    ("Qu'est-ce qu'un taux d'intérêt directeur ?", "Le taux auquel la banque centrale prête aux banques et qui oriente les autres taux", ["Le taux de rendement d'une obligation d'entreprise", "Le taux du livret d'épargne des ménages", "Le taux de change officiel"], "C'est le principal instrument de la politique monétaire.", 2),
    ("Quelle différence y a-t-il entre dépréciation et dévaluation ?", "La dévaluation est une décision officielle, la dépréciation résulte du marché", ["La dépréciation est une décision officielle, la dévaluation résulte du marché", "Ce sont deux hausses de la monnaie", "La dévaluation concerne uniquement le secteur public"], "La dévaluation suppose un régime de change fixe.", 3),
], cat="Monnaie et banque", src="sup-eco-monnaie")

split(L2, L3, "eco2-com-q1", [
    ("Quelle est l'idée centrale de l'avantage comparatif de Ricardo ?", "Chaque pays gagne à se spécialiser dans le bien où son coût d'opportunité est le plus faible", ["Un pays gagne seulement s'il produit tout moins cher que les autres", "Le commerce profite à un seul des deux pays", "Il faut interdire les importations pour s'enrichir"], "Même un pays moins efficace en tout gagne à l'échange.", 3),
    ("Quelle différence y a-t-il entre avantage absolu (Smith) et avantage comparatif (Ricardo) ?", "L'avantage absolu compare les coûts de production, l'avantage comparatif les coûts d'opportunité", ["L'avantage absolu concerne les services, l'avantage comparatif les biens, sans autre différence", "Ils sont identiques", "L'avantage comparatif ne concerne que le tarif douanier"], "Le second explique le commerce même sans avantage absolu.", 4),
    ("Dans l'exemple type de Ricardo, quelles marchandises échangent l'Angleterre et le Portugal ?", "Du drap et du vin", ["De l'or et du blé", "Du pétrole et du cacao", "Du coton et du café"], "Cet exemple est l'illustration classique du gain à l'échange.", 3),
    ("Que prédit le modèle de Heckscher-Ohlin ?", "Un pays exporte le bien intensif dans le facteur dont il est relativement abondant", ["Un pays exporte le bien qu'il produit le moins", "Les pays ne commercent que si leurs technologies diffèrent, jamais pour leurs dotations", "Le commerce est toujours intra-branche"], "Les avantages comparatifs viennent des dotations factorielles.", 4),
    ("Quelle hypothèse distingue Heckscher-Ohlin du modèle ricardien ?", "Les technologies sont identiques et les pays diffèrent par leurs dotations en facteurs", ["Les pays ont des technologies différentes", "Un seul facteur de production existe", "Les biens sont différenciés"], "Le modèle ricardien repose sur des différences de productivité du travail.", 4),
    ("Qu'est-ce que le paradoxe de Leontief ?", "Les exportations américaines étaient moins capitalistiques que les produits importés concurrents", ["Les États-Unis n'avaient pas de commerce extérieur", "Les importations croissent toujours plus vite que les exportations dans les pays développés", "Les termes de l'échange sont toujours stables"], "Il a conduit à approfondir l'analyse du commerce.", 5),
    ("Quel théorème dit qu'une hausse du prix relatif d'un bien accroît le rendement réel du facteur utilisé intensivement pour le produire ?", "Le théorème de Stolper-Samuelson", ["Le théorème de Coase", "Le théorème de Modigliani-Miller", "Le théorème de Haavelmo"], "Il met en évidence les gagnants et perdants du commerce.", 5),
    ("Quelle observation la nouvelle théorie du commerce (Krugman) explique-t-elle ?", "Le commerce intra-branche entre pays semblables, fondé sur échelle et différenciation", ["Le commerce entre pays de revenus très différents seulement, fondé sur les écarts de dotations", "L'absence de commerce entre pays voisins", "Le seul commerce de matières premières"], "Elle repose sur la concurrence monopolistique et les rendements croissants.", 4),
    ("Que décrit le cycle de vie du produit de Vernon ?", "Un produit né dans un pays avancé voit sa production se déplacer vers des pays à bas coûts", ["Le cycle des prix agricoles", "L'évolution des dépenses publiques", "La rotation des stocks d'une entreprise"], "La production se déplace au fil de la standardisation.", 4),
    ("Quel est l'effet d'un droit de douane sur le prix intérieur d'un bien importé ?", "Il tend à l'augmenter", ["Il tend à le diminuer", "Il le rend toujours nul", "Il n'a aucun effet"], "Le droit s'ajoute au prix mondial.", 2),
    ("Qui bénéficie d'un droit de douane dans un petit pays preneur de prix mondial ?", "Les producteurs nationaux et l'État, au détriment des consommateurs", ["Les consommateurs seuls", "Les producteurs étrangers", "Personne, le droit n'a aucun effet"], "Le surplus des consommateurs baisse plus que la somme des gains, d'où une perte sèche.", 3),
    ("Quelle différence y a-t-il entre quota et droit de douane ?", "Le quota limite la quantité importée, le droit de douane renchérit le prix", ["Le quota est une taxe, le droit de douane une limite", "Ce sont deux types de subvention", "Le quota concerne uniquement les exportations"], "Le quota peut créer une rente pour ceux qui détiennent les licences.", 3),
    ("Qu'est-ce que l'argument de l'industrie naissante ?", "Protéger temporairement une industrie jeune, le temps qu'elle devienne compétitive", ["Interdire toute industrie nouvelle", "Taxer toutes les exportations", "Taxer les importations à vie"], "Il est associé notamment à Friedrich List.", 4),
    ("Qu'est-ce que la thèse de Prebisch et Singer ?", "La baisse tendancielle des termes de l'échange des produits primaires face aux produits manufacturés", ["Une hausse constante des prix des matières premières", "L'égalisation des revenus entre pays", "L'inutilité de l'industrialisation"], "Elle a servi à justifier l'industrialisation par substitution aux importations.", 5),
    ("Qu'est-ce que l'industrialisation par substitution aux importations ?", "Produire localement ce qui était importé, derrière des protections", ["Importer toute la production industrielle", "Exporter uniquement des matières premières et importer uniquement des biens finis", "Supprimer tous les droits de douane"], "Elle fut très pratiquée dans de nombreux pays en développement.", 4),
    ("Qu'est-ce qu'une amélioration des termes de l'échange ?", "Le prix des exportations monte par rapport à celui des importations", ["Le prix des importations monte par rapport à celui des exportations", "Le volume des exportations double", "Le taux de change se déprécie"], "Un pays peut acheter plus d'importations avec la même quantité d'exportations.", 3),
    ("Que mesure le taux de couverture ?", "Le rapport des exportations aux importations", ["Le rapport de la dette aux exportations", "Le rapport du PIB aux importations", "Le rapport de l'épargne au PIB"], "Un taux supérieur à 100 % correspond à un excédent commercial.", 2),
    ("Que signifie un excédent de la balance commerciale ?", "Les exportations de biens dépassent les importations de biens", ["Les importations dépassent les exportations", "L'État dégage un excédent budgétaire", "Le chômage est nul"], "Le solde est positif.", 2),
    ("Selon la condition de Marshall-Lerner, quand une dépréciation améliore-t-elle la balance commerciale ?", "Quand la somme des élasticités-prix des exportations et des importations dépasse 1", ["Quand l'élasticité des exportations est nulle", "Quand le pays est une économie fermée", "Quand les élasticités des importations et des exportations sont toutes deux nulles ou presque"], "Sinon, l'effet prix l'emporte sur l'effet volume.", 5),
    ("Que décrit la courbe en J ?", "À court terme, une dépréciation dégrade d'abord la balance commerciale avant de l'améliorer", ["Une hausse permanente des importations", "L'évolution du taux d'intérêt après une relance", "L'effet d'un quota sur les prix"], "Les volumes s'ajustent plus lentement que les prix.", 5),
    ("Que dit la parité de pouvoir d'achat absolue ?", "Le taux de change égalise le prix d'un même panier de biens exprimé dans la même monnaie", ["Le taux de change est égal au taux d'intérêt", "Les prix sont toujours fixes", "Le taux de change dépend uniquement du déficit"], "En pratique, elle est surtout vérifiée à long terme et approximativement.", 4),
    ("Que décrit le syndrome hollandais ?", "La hausse de la monnaie liée à un boom d'exportations de ressources, qui pénalise l'industrie", ["Le manque de main-d'œuvre agricole", "Une dévaluation compétitive", "L'effondrement des importations"], "Le nom vient de l'expérience néerlandaise après la découverte de gaz.", 5),
    ("Quel est le principe du trilemme de Mundell ?", "Impossible de combiner change fixe, libre circulation des capitaux et autonomie monétaire", ["Il faut choisir entre inflation, chômage et dette, car les trois ne peuvent baisser ensemble", "On ne peut avoir à la fois croissance, emploi et équité", "Aucun régime de change n'est possible"], "Un pays doit renoncer à l'un des trois objectifs.", 4),
    ("Que signifie un régime de change fixe pour la banque centrale ?", "Elle doit défendre la parité par ses interventions et ses réserves", ["Elle laisse le cours se fixer librement", "Elle supprime ses réserves de change", "Elle abandonne toute politique monétaire à la monnaie étrangère par principe"], "Elle perd en général la liberté de fixer ses taux indépendamment.", 3),
    ("Quelle fut une caractéristique du système de Bretton Woods ?", "Des changes fixes mais ajustables autour du dollar, lui-même convertible en or", ["Des changes totalement flottants", "L'abandon de toute monnaie nationale", "Une monnaie unique mondiale"], "Il fut mis en place à la fin de la Seconde Guerre mondiale avec le FMI et la Banque mondiale.", 4),
    ("Que sont les droits de tirage spéciaux (DTS) ?", "Un avoir de réserve créé par le FMI, attribué à ses membres", ["Un impôt prélevé par l'OMC", "Une monnaie émise par la Banque mondiale pour les pays en développement", "Un fonds d'aide aux ménages"], "Leur valeur est liée à un panier de monnaies.", 4),
    ("Quelle est la mission principale de l'OMC ?", "Superviser les règles du commerce international et régler les différends entre membres", ["Fixer les taux de change", "Octroyer des prêts aux États", "Émettre une monnaie internationale commune aux banques de la zone"], "Elle a succédé au GATT.", 2),
    ("Quel est le principe de la clause de la nation la plus favorisée ?", "Un avantage commercial accordé à un membre doit être étendu à tous les autres", ["Chaque membre peut fixer ses propres droits à volonté pour chaque partenaire", "L'État le plus riche fixe les règles", "Seuls les pays pauvres ont droit aux avantages"], "Elle vise à éviter la discrimination entre partenaires.", 4),
    ("Quelle est la différence entre une zone de libre-échange et une union douanière ?", "L'union douanière ajoute un tarif extérieur commun", ["La zone de libre-échange a un tarif extérieur commun", "L'union douanière libère les capitaux mais pas les biens", "Il n'y a pas de différence"], "Dans la zone de libre-échange, chaque pays garde ses propres tarifs.", 3),
    ("Qu'est-ce que le dumping ?", "Vendre à l'étranger à un prix inférieur à celui du marché d'origine ou aux coûts", ["Surtaxer les importations", "Interdire les exportations", "Dévaluer sa monnaie"], "L'OMC autorise des mesures antidumping sous conditions.", 3),
    ("Que mesure la compétitivité-prix d'un pays ?", "Le niveau de ses prix comparés à ceux des concurrents, exprimés dans la même monnaie", ["Le niveau de son PIB", "Le nombre de ses exportateurs", "Son taux d'épargne"], "Elle dépend des prix et du taux de change.", 3),
    ("Quelle est la relation entre une hausse du taux d'intérêt intérieur et le taux de change en mobilité des capitaux ?", "Elle attire des capitaux et tend à apprécier la monnaie", ["Elle déprécie toujours la monnaie", "Elle supprime toute entrée de capitaux étrangers dans le pays concerné", "Elle fixe le taux de change"], "Les placements deviennent plus rémunérateurs.", 4),
], cat="Commerce international", src="sup-eco-commerce")

# =====================================================================================================================
# CEMAC, ZONE FRANC (niveau général : ni taux ni date incertaine)
# =====================================================================================================================
table(L2, "eco2-cemac-inst", [
    ("la BEAC", "la banque centrale de la CEMAC, qui émet le franc CFA d'Afrique centrale", 2),
    ("la BCEAO", "la banque centrale des États de l'Afrique de l'Ouest qui partagent le franc CFA ouest-africain", 3),
    ("la COBAC", "la Commission bancaire de l'Afrique centrale, chargée de contrôler les banques de la zone", 3),
    ("la BDEAC", "la banque de développement des États d'Afrique centrale, qui finance des projets de développement", 4),
    ("la CEMAC", "la Communauté économique et monétaire de l'Afrique centrale", 2),
    ("l'UEMOA", "l'Union économique et monétaire ouest-africaine", 3),
    ("la CEEAC", "la Communauté économique des États de l'Afrique centrale", 4),
    ("la CEDEAO", "la Communauté économique des États de l'Afrique de l'Ouest", 3),
    ("la ZLECAf", "la zone de libre-échange continentale africaine", 3),
    ("la BAD", "la Banque africaine de développement", 2),
], cat="Zone franc et CEMAC", src="sup-eco-cemac", region="AF",
    fwd="Que désigne {a} ?", rev="De quelle institution ou organisation s'agit-il : {b} ?")

split3("eco2-cemac-q1", [
    ("Quelle institution émet le franc CFA utilisé au Cameroun ?", "La Banque des États de l'Afrique centrale (BEAC)", ["La Banque centrale des États de l'Afrique de l'Ouest (BCEAO)", "La Banque de France", "La Banque mondiale"], "La BEAC est la banque centrale de la CEMAC.", 2),
    ("Dans quelle ville se trouve le siège de la BEAC ?", "Yaoundé", ["Douala", "Libreville", "Brazzaville"], "Le siège de la BEAC est à Yaoundé.", 2),
    ("Quelle institution émet le franc CFA en Afrique de l'Ouest ?", "La BCEAO", ["La BEAC", "La BAD", "La BDEAC"], "La BCEAO est la banque centrale de l'UEMOA.", 2),
    ("Combien d'États compte la CEMAC ?", "Six", ["Quatre", "Huit", "Onze"], "Cameroun, République du Congo, Gabon, Guinée équatoriale, République centrafricaine et Tchad.", 2),
    ("Lequel de ces pays est membre de la CEMAC ?", "Le Gabon", ["Le Sénégal", "Le Ghana", "La Côte d'Ivoire"], "Le Sénégal et la Côte d'Ivoire sont dans l'UEMOA, le Ghana a sa propre monnaie.", 2),
    ("Lequel de ces pays n'est pas membre de la CEMAC ?", "Le Nigeria", ["Le Tchad", "La Guinée équatoriale", "La République centrafricaine"], "Le Nigeria a sa propre monnaie, le naira.", 3),
    ("Lequel de ces pays n'est pas membre de la CEMAC ?", "Le Mali", ["La République du Congo", "Le Gabon", "Le Cameroun"], "Le Mali appartient à l'UEMOA.", 3),
    ("Quel est le rôle de la COBAC ?", "Contrôler et superviser les établissements de crédit de la zone CEMAC", ["Émettre les billets de la zone", "Fixer le taux de change du franc CFA", "Arbitrer les litiges commerciaux"], "C'est l'autorité de supervision bancaire de la zone.", 3),
    ("Quelle est la mission principale de la BEAC ?", "Garantir la stabilité de la monnaie et mener la politique monétaire commune", ["Collecter les impôts des États membres", "Gérer les écoles de la région", "Fixer les prix des produits agricoles"], "Elle émet le franc CFA et conduit la politique monétaire commune.", 2),
    ("Comment la parité du franc CFA est-elle caractérisée ?", "Elle est fixe par rapport à l'euro", ["Elle flotte librement chaque jour", "Elle est fixée par un vote de l'OMC", "Elle est déterminée par le prix de l'or uniquement"], "Le franc CFA est arrimé à l'euro, avec une parité fixe.", 3),
    ("Qui apporte en principe la garantie de convertibilité du franc CFA d'Afrique centrale à parité fixe ?", "Le Trésor français", ["La Banque mondiale", "L'OMC", "Le Parlement européen"], "Cette garantie accompagne l'arrimage à l'euro.", 4),
    ("Le franc CFA de la zone CEMAC et celui de l'UEMOA sont-ils interchangeables légalement ?", "Non, ce sont deux monnaies distinctes émises par deux banques centrales", ["Oui, ce sont deux billets de la même monnaie, émis par une banque centrale unique", "Oui, mais seulement au Cameroun", "Non, parce que l'un est en euros"], "Ils partagent un nom et un régime de parité, mais pas la circulation légale.", 4),
    ("Quelle est la devise officielle du Cameroun ?", "Le franc CFA (franc de la Coopération financière en Afrique centrale)", ["L'euro", "Le naira", "Le dollar"], "La BEAC en assure l'émission.", 1),
    ("Que désigne l'abréviation UEMOA ?", "Union économique et monétaire ouest-africaine", ["Union économique et monétaire d'Afrique centrale", "Union économique mondiale de l'Afrique", "Union européenne de la monnaie ouest-africaine"], "C'est l'union de plusieurs États d'Afrique de l'Ouest.", 3),
    ("Que désigne l'abréviation CEMAC ?", "Communauté économique et monétaire de l'Afrique centrale", ["Commission économique et monétaire d'Afrique centrale", "Conférence des États du marché africain commun", "Communauté européenne des marchés d'Afrique centrale"], "Elle compte six États membres.", 2),
    ("Que comprend la zone franc ?", "Des États africains partageant un franc CFA, ainsi que les Comores", ["Uniquement le Cameroun", "Seulement les pays francophones d'Europe et leurs anciens territoires d'outre-mer", "L'ensemble des pays du continent"], "Elle réunit les zones CEMAC et UEMOA ainsi que les Comores.", 4),
    ("Que sont les deux unions de la CEMAC ?", "L'union économique (UEAC) et l'union monétaire (UMAC)", ["L'union douanière et l'union fiscale et budgétaire", "L'union agricole et l'union industrielle", "L'union politique et l'union militaire"], "La CEMAC repose sur ces deux piliers.", 4),
    ("Dans quelle ville est établi le siège de la Commission de la CEMAC ?", "Bangui", ["Yaoundé", "Libreville", "N'Djamena"], "Le siège de la Commission de la CEMAC est à Bangui.", 4),
    ("Dans quelle ville est établi le siège de la BCEAO ?", "Dakar", ["Abidjan", "Ouagadougou", "Lomé"], "La BCEAO a son siège à Dakar.", 3),
    ("Quel est l'objectif d'une union monétaire ?", "Partager une monnaie commune et une politique monétaire unique", ["Supprimer toute monnaie", "Fixer les mêmes impôts partout", "Abolir les frontières politiques"], "Elle suppose une banque centrale commune.", 3),
    ("Quel est le rôle d'une banque centrale commune dans une union monétaire ?", "Conduire la politique monétaire pour l'ensemble des États membres", ["Voter les budgets nationaux", "Percevoir l'impôt", "Désigner les ministres"], "Chaque État renonce à sa politique monétaire nationale.", 3),
    ("Quelle contrainte une monnaie commune fait-elle peser sur les États membres ?", "Ils ne peuvent plus utiliser seuls le taux de change comme variable d'ajustement", ["Ils doivent supprimer leur budget", "Ils ne peuvent plus commercer", "Ils renoncent à toute fiscalité"], "Les chocs spécifiques doivent être absorbés autrement.", 4),
    ("Quel est le principal intérêt d'un taux de change fixe avec l'euro pour un pays de la zone ?", "La stabilité du cours avec les partenaires de la zone euro", ["Une inflation toujours plus élevée", "Une dépréciation automatique", "Le contrôle total de la politique monétaire par la banque centrale nationale"], "Elle facilite le commerce avec ce partenaire.", 4),
    ("Quel est un inconvénient possible d'un taux de change fixe avec l'euro ?", "L'impossibilité de dévaluer pour retrouver de la compétitivité", ["Une hausse automatique des exportations vers la zone euro et le reste du monde", "La suppression de tout risque de dette", "Une inflation nulle garantie"], "Le pays ne peut plus ajuster le cours seul.", 4),
    ("Quelle banque finance des projets de développement dans les États de la CEMAC ?", "La BDEAC", ["La COBAC", "La BCEAO", "L'OMC"], "Elle a son siège à Brazzaville.", 4),
    ("Dans quelle ville se trouve le siège de la BDEAC ?", "Brazzaville", ["Yaoundé", "Malabo", "Bangui"], "La Banque de développement des États de l'Afrique centrale est établie à Brazzaville.", 5),
    ("Comment appelle-t-on la Bourse de valeurs du Cameroun ?", "La Douala Stock Exchange (DSX)", ["La Bourse de Paris", "La Bourse d'Abidjan", "La Bourse de Londres"], "Elle est établie à Douala.", 3),
    ("Que permet une zone de libre-échange continentale africaine (ZLECAf) ?", "Réduire les barrières commerciales entre pays africains membres", ["Créer une monnaie unique africaine immédiate", "Supprimer toutes les banques centrales", "Fusionner les États membres"], "Elle vise à créer un grand marché continental.", 3),
    ("Quel est le code ISO du franc CFA d'Afrique centrale ?", "XAF", ["XOF", "EUR", "CFA"], "XOF désigne le franc CFA d'Afrique de l'Ouest.", 5),
    ("Quel est le code ISO du franc CFA d'Afrique de l'Ouest ?", "XOF", ["XAF", "USD", "NGN"], "XAF est celui de l'Afrique centrale.", 5),
    ("Que désigne une dévaluation du franc CFA ?", "Une baisse officielle de sa parité par rapport à la monnaie d'ancrage", ["Une hausse du taux directeur", "Une baisse du PIB", "Un abandon du franc CFA"], "La parité fixe a été modifiée à la baisse notamment en 1994.", 3),
    ("Quel organe de la CEMAC comprend les chefs d'État des pays membres ?", "La Conférence des chefs d'État", ["La Cour de justice", "La COBAC", "Le Parlement communautaire"], "C'est l'organe suprême de la Communauté.", 4),
    ("Quelle institution de la CEMAC exerce la fonction juridictionnelle communautaire ?", "La Cour de justice communautaire", ["La COBAC", "La BEAC", "La Banque mondiale"], "Elle a une compétence juridictionnelle propre.", 4),
], cat="Zone franc et CEMAC", src="sup-eco-cemac", region="AF")

# =====================================================================================================================
# L3 : MACROÉCONOMIE AVANCÉE, IS-LM, CROISSANCE, ANTICIPATIONS
# =====================================================================================================================
mcq(L3, "eco3-mac-islm", [
    ("Que représente la courbe IS dans le modèle IS-LM ?", "Les couples taux d'intérêt-revenu pour lesquels le marché des biens est en équilibre", ["Les couples taux d'intérêt-revenu pour lesquels le marché de la monnaie est en équilibre", "Les couples prix-quantité du marché du travail", "Les couples taux de change-balance commerciale"], "IS : investissement égal à l'épargne.", 3),
    ("Que représente la courbe LM dans le modèle IS-LM ?", "Les couples taux d'intérêt-revenu pour lesquels l'offre et la demande de monnaie s'égalisent", ["Les couples pour lesquels le budget de l'État est équilibré", "Les couples taux d'intérêt-revenu pour lesquels la balance des paiements est équilibrée", "Les couples pour lesquels l'inflation est nulle"], "LM : liquidité égale à la monnaie.", 3),
    ("Pourquoi la courbe IS est-elle décroissante ?", "Un taux plus bas stimule l'investissement, donc la demande et le revenu d'équilibre", ["Un taux plus élevé stimule la consommation", "Un revenu plus élevé fait baisser l'épargne", "L'État fixe le taux selon le revenu"], "Le taux d'intérêt agit négativement sur l'investissement.", 4),
    ("Pourquoi la courbe LM est-elle croissante ?", "Un revenu plus élevé accroît la demande de monnaie, d'où un taux plus élevé à l'équilibre", ["Un revenu plus élevé réduit la demande de monnaie", "Le taux d'intérêt dépend seulement de l'offre de biens et non de la quantité de monnaie", "La banque centrale fixe un taux croissant"], "À offre de monnaie donnée, il faut un taux plus haut pour rationner la demande.", 4),
    ("Qui a proposé le schéma IS-LM en 1937 pour formaliser la Théorie générale de Keynes ?", "John R. Hicks", ["Milton Friedman", "Robert Solow", "Léon Walras"], "Il fut développé ensuite par Hansen.", 4),
    ("Que provoque, dans IS-LM, une hausse des dépenses publiques ?", "Un déplacement de IS vers la droite : le revenu et le taux d'intérêt augmentent", ["Un déplacement de LM vers la droite : le taux baisse et le revenu diminue aussi", "Une baisse du revenu et du taux", "Aucun déplacement"], "La demande augmente ; la demande de monnaie aussi, d'où la hausse du taux.", 4),
    ("Que provoque, dans IS-LM, une hausse de l'offre de monnaie ?", "Un déplacement de LM vers la droite : le taux baisse et le revenu augmente", ["Un déplacement de IS vers la gauche", "Une hausse du taux et du revenu", "Une baisse du revenu"], "Le taux plus bas stimule l'investissement.", 4),
    ("Comment IS-LM illustre-t-il l'effet d'éviction ?", "La hausse de G fait monter le taux d'intérêt, ce qui réduit l'investissement privé", ["La hausse de G fait baisser le taux d'intérêt, ce qui réduit l'épargne et la consommation", "La baisse de G fait monter le revenu", "L'effet d'éviction n'apparaît jamais"], "L'éviction est partielle si LM est croissante.", 4),
    ("Que vaut l'effet d'une relance budgétaire dans le cas de la trappe à liquidité (LM horizontale) ?", "Elle est maximale : pas d'éviction, le taux ne bouge pas", ["Elle est nulle", "Elle est négative", "Elle est maximale : le taux monte fortement et l'investissement est évincé"], "Le taux ne monte pas, donc l'investissement n'est pas évincé.", 5),
    ("Que vaut l'effet d'une politique monétaire en trappe à liquidité ?", "Elle est inefficace, le taux ne baisse pas davantage", ["Elle est très efficace, car le taux d'intérêt baisse fortement et stimule l'investissement", "Elle fait monter le taux", "Elle réduit les prix"], "Les agents absorbent la monnaie nouvelle sans changer leur demande de titres.", 5),
    ("Dans le cas « classique » d'une LM verticale, quel est l'effet d'une hausse des dépenses publiques sur le revenu ?", "Nul : l'éviction est totale", ["Maximal", "Négatif", "Positif et doublé"], "Le revenu ne dépend que de la quantité de monnaie.", 5),
    ("Si l'investissement ne dépend pas du taux d'intérêt, comment est la courbe IS ?", "Verticale", ["Horizontale", "Croissante", "Concave"], "Dans ce cas, la politique monétaire n'a pas d'effet sur le revenu.", 5),
    ("Dans IS-LM, que peut-on dire d'un policy mix associant relance budgétaire et resserrement monétaire ?", "Le taux d'intérêt augmente sans ambiguïté, l'effet sur le revenu est incertain", ["Le taux baisse sans ambiguïté", "Le revenu augmente sans ambiguïté", "Il n'y a aucun effet sur le taux"], "La relance déplace IS à droite, le resserrement déplace LM à gauche.", 5),
    ("Dans le modèle offre globale-demande globale, que provoque un choc d'offre négatif (hausse des coûts de l'énergie) ?", "Hausse des prix et baisse de la production", ["Baisse des prix et hausse de la production", "Hausse des prix et hausse de la production", "Baisse des prix et baisse de la production"], "La situation s'apparente à la stagflation.", 4),
    ("Comment est la courbe d'offre globale de long terme dans la vision classique ?", "Verticale, au niveau de production potentielle", ["Horizontale", "Croissante", "Décroissante"], "À long terme, la production ne dépend pas du niveau des prix.", 4),
    ("Pourquoi la courbe d'offre globale de court terme est-elle croissante dans l'approche keynésienne ?", "Parce que les prix ou les salaires sont rigides à court terme", ["Parce que la monnaie est neutre", "Parce que les prix sont toujours flexibles et s'ajustent instantanément", "Parce que l'État fixe la production"], "Les rigidités nominales font varier la production avec la demande.", 4),
    ("Dans le modèle de Mundell-Fleming avec change flottant et parfaite mobilité des capitaux, quelle politique est efficace sur le revenu ?", "La politique monétaire", ["La politique budgétaire", "Chacune, avec la même force", "Ni l'une ni l'autre"], "La relance budgétaire fait monter le taux, attire des capitaux, apprécie la monnaie et réduit les exportations nettes.", 5),
    ("Dans le modèle de Mundell-Fleming avec change fixe et parfaite mobilité des capitaux, quelle politique est efficace ?", "La politique budgétaire", ["La politique monétaire", "Chacune, avec la même force", "Ni l'une ni l'autre"], "La banque centrale doit défendre la parité : l'offre de monnaie devient endogène.", 5),
    ("Que dit la parité des taux d'intérêt non couverte ?", "L'écart de taux entre deux pays reflète la dépréciation anticipée de la monnaie du pays à taux élevé", ["Les taux d'intérêt sont toujours égaux", "Le taux de change est fixé par la banque centrale", "Les prix sont égaux dans tous les pays"], "Le rendement attendu des placements s'égalise.", 5),
    ("Que dit l'identité entre le solde courant, l'épargne et l'investissement d'une économie ouverte ?", "Le solde courant égale l'épargne nationale moins l'investissement", ["Le solde courant égale l'investissement plus l'épargne", "Le solde courant égale la dette publique", "Le solde courant égale la masse monétaire"], "Un pays qui investit plus qu'il n'épargne s'endette envers l'extérieur.", 5),
    ("Que dit l'hypothèse des déficits jumeaux ?", "Un déficit public tend à s'accompagner d'un déficit courant", ["Un déficit public annule automatiquement le déficit courant du pays", "Le déficit courant est toujours nul", "Ces déficits sont toujours indépendants"], "Elle est discutée empiriquement.", 5),
    ("Que décrit le paradoxe de l'épargne de Keynes ?", "Si tous épargnent davantage, le revenu baisse et l'épargne totale peut ne pas augmenter", ["Plus on épargne, plus on devient riche", "L'épargne détruit toujours l'investissement", "L'épargne est toujours égale à zéro"], "Une demande plus faible réduit les revenus et donc l'épargne.", 4),
    ("Selon le principe de l'accélérateur, de quoi l'investissement dépend-il ?", "De la variation de la demande ou de la production", ["Du seul taux d'intérêt nominal", "Du seul niveau du salaire", "De la masse monétaire en circulation uniquement et de son taux de croissance"], "Une hausse de la demande entraîne une hausse plus que proportionnelle de l'investissement.", 4),
    ("Qu'est-ce que le q de Tobin ?", "Le rapport entre la valeur boursière d'une firme et le coût de remplacement de son capital", ["Le rapport entre profit et chiffre d'affaires", "Le rapport entre dette et capitaux propres", "Le taux de croissance de la production"], "Un q supérieur à 1 incite à investir.", 5),
    ("Que dit l'hypothèse du revenu permanent de Friedman ?", "La consommation dépend du revenu moyen anticipé sur longue période plus que du revenu courant", ["La consommation dépend uniquement du revenu courant", "La consommation est indépendante du revenu", "L'épargne est toujours nulle"], "Les ménages lissent leur consommation.", 4),
    ("Que dit l'hypothèse du cycle de vie de Modigliani ?", "Les ménages épargnent pendant la vie active pour consommer pendant la retraite", ["Les ménages consomment toujours leur revenu courant", "Les ménages n'épargnent qu'en période d'inflation", "Les ménages dépensent plus qu'ils ne gagnent pendant toute leur vie active"], "L'épargne lisse la consommation sur le cycle de vie.", 4),
], cat="Macroéconomie : IS-LM et offre-demande globales", src="sup-eco-macro")

mcq(L3, "eco3-mac-croiss", [
    ("Dans le modèle de Solow, quelle est la source de la croissance du revenu par tête à long terme ?", "Le progrès technique", ["L'accumulation du capital seule", "L'augmentation du taux d'épargne seule", "Les dépenses publiques"], "Les rendements décroissants du capital limitent l'accumulation.", 4),
    ("Que traduit l'hypothèse de rendements décroissants du capital dans le modèle de Solow ?", "L'accumulation de capital seule ne permet pas une croissance par tête indéfinie", ["L'accumulation du capital permet une croissance infinie du revenu par tête", "Le travail est inutile", "Le progrès technique est impossible"], "Chaque unité de capital supplémentaire est moins productive.", 4),
    ("Qu'est-ce que l'état régulier (état stationnaire) du modèle de Solow ?", "Une situation où le capital par tête cesse de varier", ["Une situation où le PIB est nul", "Une situation où l'épargne est nulle", "Une situation sans aucun progrès technique ni investissement, par définition"], "L'investissement y compense exactement la dépréciation et la croissance de la population.", 4),
    ("Que prévoit la convergence conditionnelle de Solow ?", "Les pays convergent vers leur propre état régulier, qui dépend de l'épargne, de la démographie, etc.", ["Les pays atteignent le même revenu quelle que soit l'épargne", "Les pays riches s'éloignent toujours des pauvres", "Aucun pays ne converge"], "Les pays pauvres rattrapent seulement s'ils ont les mêmes déterminants.", 5),
    ("Que mesure le résidu de Solow ?", "La part de la croissance non expliquée par l'accumulation des facteurs", ["La part du capital dans le PIB", "Le taux d'inflation", "Le déficit budgétaire"], "Il approxime la progression de la productivité globale des facteurs.", 4),
    ("Que dit la règle d'or de l'accumulation du capital ?", "Le niveau de capital qui maximise la consommation par tête à l'état régulier", ["Le niveau de capital qui maximise le profit de la firme représentative à l'état régulier", "Le niveau de capital nul", "Le niveau de capital égal à l'épargne"], "Au-delà, on épargne trop pour la consommation future.", 5),
    ("Que dit le modèle de Harrod-Domar à propos de la croissance ?", "Elle dépend du taux d'épargne et du coefficient de capital", ["Elle dépend de la seule monnaie", "Elle dépend des termes de l'échange et du taux de change réel", "Elle est indépendante de l'épargne"], "Le taux de croissance est de l'ordre de s/v.", 4),
    ("Qu'est-ce qui distingue les modèles de croissance endogène de celui de Solow ?", "La croissance de long terme y résulte de décisions internes (capital humain, R&D)", ["La croissance y résulte d'un progrès technique tombé du ciel, indépendant de toute décision", "La croissance y est toujours nulle", "La croissance y dépend de la démographie seule"], "Les rendements du capital au sens large ne sont plus décroissants.", 4),
    ("Que suppose un modèle de croissance de type AK ?", "Des rendements constants du capital, permettant une croissance durable", ["Des rendements décroissants rapides, qui empêchent toute croissance durable du revenu", "L'absence de capital", "Un taux d'épargne nul"], "La production est proportionnelle au capital au sens large.", 5),
    ("Quel rôle la connaissance joue-t-elle dans le modèle de Romer ?", "C'est un bien non rival dont la diffusion engendre des rendements croissants", ["C'est un bien rival et exclusif", "C'est un facteur sans effet", "C'est un coût fixe sans lien avec la production, que chaque entreprise doit supporter seule"], "Un même savoir peut être utilisé par plusieurs sans s'épuiser.", 5),
    ("Quel facteur Robert Lucas place-t-il au centre de la croissance dans son modèle de 1988 ?", "Le capital humain", ["Les ressources naturelles", "La monnaie", "Le taux de change"], "L'éducation produit des effets externes.", 5),
    ("Quelle est l'idée de la croissance schumpétérienne d'Aghion et Howitt ?", "La croissance vient d'innovations qui rendent obsolètes les technologies précédentes", ["La croissance vient de la planification", "La croissance est indépendante de l'innovation", "La croissance vient de la baisse des impôts uniquement, sans rôle de l'innovation ni du capital"], "Elle formalise la destruction créatrice.", 5),
    ("Quelle proposition défend Thomas Piketty dans sa thèse r > g ?", "Quand le rendement du capital dépasse la croissance, la concentration du patrimoine tend à augmenter", ["Les inégalités baissent toujours avec la croissance", "Le capital ne rapporte rien", "La croissance dépasse toujours le rendement du capital"], "Elle est discutée dans la littérature.", 4),
    ("Que décrit la courbe de Kuznets ?", "Une relation en U inversé entre le développement et les inégalités de revenus", ["Une relation en U entre inflation et chômage", "Une relation positive et linéaire entre dette publique et croissance du revenu par habitant", "Une relation constante entre épargne et revenu"], "Les inégalités augmenteraient puis diminueraient avec le revenu moyen.", 5),
    ("Que décrit un cycle de Kondratiev ?", "Une onde longue de l'activité économique", ["Une fluctuation saisonnière de quelques mois", "Un cycle lié aux stocks de très court terme", "Un cycle électoral"], "Il est associé aux grandes vagues d'innovation.", 4),
    ("Quel cycle économique est lié aux variations de stocks et de court terme ?", "Le cycle de Kitchin", ["Le cycle de Kondratiev", "Le cycle de Kuznets", "Le cycle de Pareto"], "Il s'agit d'oscillations courtes liées aux ajustements de stocks.", 5),
    ("Quelle est l'idée centrale de l'école du cycle réel (RBC) ?", "Les fluctuations proviennent surtout de chocs réels, comme les chocs technologiques", ["Les fluctuations proviennent seulement de chocs monétaires imprévus de la banque centrale", "Les fluctuations n'existent pas", "Les prix sont toujours rigides"], "Elle est associée à Kydland et Prescott.", 5),
    ("Que sont les anticipations rationnelles ?", "Des prévisions qui utilisent toute l'information disponible, sans erreur systématique", ["Des prévisions fondées uniquement sur le passé récent, sans autre information disponible ni modèle", "Des prévisions toujours exactes", "Des prévisions fixées par l'État"], "Les erreurs sont aléatoires et non prévisibles.", 4),
    ("Que dit la proposition d'inefficacité de la politique monétaire (Sargent-Wallace) ?", "Avec anticipations rationnelles et prix flexibles, une hausse anticipée de monnaie est neutre", ["Toute politique monétaire est efficace", "La politique budgétaire est toujours inefficace", "La monnaie est toujours une dette de l'État"], "Seules les surprises monétaires auraient un effet réel.", 5),
    ("Que dit la critique de Lucas ?", "Les paramètres estimés sur le passé changent quand la politique change, car les agents s'adaptent", ["Les politiques économiques n'ont aucun effet", "Les modèles n'ont aucune utilité", "Les anticipations sont toujours adaptatives"], "Elle met en cause l'évaluation de politiques par des modèles non structurels.", 5),
    ("Que dit le problème d'incohérence temporelle (Kydland-Prescott) ?", "Une politique optimale annoncée peut cesser de l'être une fois les agents engagés : d'où des règles", ["Les politiques annoncées sont toujours respectées", "Les anticipations sont toujours adaptatives", "La monnaie est sans effet"], "Cela soutient l'idée de banques centrales indépendantes ou de règles.", 5),
    ("Qu'appelle-t-on coûts de menu (nouveaux keynésiens) ?", "Les coûts de modification des prix qui expliquent des rigidités nominales", ["Les coûts d'impression des billets", "Les coûts des repas d'entreprise", "Les coûts des importations de nourriture pour les restaurants et les cantines des entreprises"], "Même des coûts faibles peuvent causer des rigidités avec des effets réels.", 5),
    ("Que sont les salaires d'efficience ?", "Des salaires supérieurs au salaire d'équilibre pour inciter à l'effort et réduire la rotation", ["Des salaires toujours minimaux", "Des salaires versés à la tâche", "Des salaires indexés sur le taux de change"], "Ils peuvent expliquer un chômage persistant.", 5),
    ("Que décrit la théorie insiders-outsiders ?", "Les salariés en place protégés pèsent sur les salaires au détriment des chômeurs", ["Les chômeurs fixent le salaire", "Les entreprises étrangères fixent les prix des biens échangés et les salaires nominaux locaux", "Les banques fixent les salaires"], "Elle explique la persistance du chômage.", 5),
    ("Qu'est-ce que l'hystérèse du chômage ?", "Le fait qu'un chômage conjoncturel passé peut laisser un chômage durable", ["La baisse du chômage après une hausse de l'inflation observée sur le long terme", "Un chômage qui disparaît immédiatement", "Le chômage saisonnier"], "La perte de compétences ou de réseaux peut rendre le chômage persistant.", 5),
    ("Que décrit la règle de Taylor ?", "Un taux directeur qui réagit à l'écart d'inflation et à l'écart de production", ["Un taux fixé par le gouvernement", "Un taux égal à l'inflation passée", "Un taux nul en toutes circonstances"], "Elle sert de référence pour juger la politique monétaire.", 4),
    ("En quoi consiste le ciblage d'inflation ?", "La banque centrale annonce un objectif d'inflation et ajuste ses instruments pour l'atteindre", ["Elle fixe les prix de tous les biens", "Elle réduit la masse monétaire à zéro", "Elle fixe le taux de change"], "L'ancrage des anticipations en est l'enjeu.", 4),
    ("Que sont les politiques monétaires non conventionnelles ?", "Des mesures comme les achats massifs d'actifs quand le taux directeur est proche de zéro", ["Des mesures de politique budgétaire", "Des interdictions de crédit", "Des fixations de prix"], "Elles incluent l'assouplissement quantitatif.", 4),
    ("Qu'est-ce que le NAIRU ?", "Le taux de chômage compatible avec une inflation stable", ["Le taux d'inflation minimal", "Le taux d'intérêt directeur fixé par la banque centrale au jour le jour", "Le taux de chômage nul"], "Il joue un rôle central dans les courbes de Phillips augmentées.", 4),
    ("Selon Hyman Minsky, comment évolue la stabilité financière ?", "La stabilité prolongée encourage l'endettement et prépare l'instabilité", ["La stabilité est toujours permanente", "Les crises n'ont jamais lieu", "Les marchés s'autorégulent toujours parfaitement, sans aucun risque d'instabilité"], "C'est l'hypothèse d'instabilité financière.", 5),
    ("Quel mécanisme relie un déficit public financé par la création monétaire à l'inflation ?", "Une hausse de la masse monétaire supérieure à la production fait monter les prix", ["Il n'y a aucun lien", "La monnaie fait baisser les prix", "Les déficits réduisent la masse monétaire"], "Les hyperinflations sont souvent liées au financement monétaire des déficits.", 4),
    ("Que mesure la productivité globale des facteurs ?", "L'efficacité avec laquelle le capital et le travail sont combinés", ["La seule productivité du travail", "Le seul rendement du capital", "Le prix moyen des facteurs de production, pondéré par leur part dans le coût total"], "Elle se mesure par le résidu de la fonction de production.", 4),
], cat="Macroéconomie : croissance, cycles, anticipations", src="sup-eco-macro")

# =====================================================================================================================
# L3 : MICROÉCONOMIE AVANCÉE
# =====================================================================================================================
mcq(L3, "eco3-mic-q1", [
    ("Que dit l'équation de Slutsky ?", "L'effet total d'un prix est la somme d'un effet de substitution et d'un effet de revenu", ["L'effet total est égal au seul effet de substitution", "L'effet total est égal à l'effet de revenu moins l'effet de substitution dans tous les cas", "L'effet total est nul pour tout bien"], "Elle décompose la réaction du consommateur à un changement de prix.", 4),
    ("Pour un bien normal, que peut-on dire des effets de substitution et de revenu après une hausse du prix ?", "Ils vont dans le même sens : la demande baisse", ["Ils vont en sens opposés", "Chacun augmente la demande", "L'effet de revenu est nul par définition"], "La loi de la demande est ainsi vérifiée.", 4),
    ("Quelle condition est nécessaire pour qu'un bien soit un bien de Giffen ?", "Il doit être un bien inférieur", ["Il doit être un bien de luxe", "Il doit être un bien public", "Il doit être complémentaire d'un bien normal quelconque"], "L'effet de revenu doit être de sens contraire et dominer l'effet de substitution.", 5),
    ("Que signifie que la demande d'un consommateur est homogène de degré zéro en prix et revenu ?", "Si tous les prix et le revenu sont multipliés par un même facteur, la demande ne change pas", ["La demande double quand les prix doublent", "La demande est nulle", "La demande dépend du seul revenu"], "Cela revient à dire que seules les valeurs relatives comptent.", 5),
    ("Avec une utilité de type Cobb-Douglas U = x^a · y^(1−a), quelle part du revenu est dépensée en bien x ?", "La part a, quelle que soit la valeur des prix", ["La part 1 − a", "La part dépendant du prix de y seulement, indépendante du revenu", "Une part égale à 50 % toujours"], "Pour ces préférences, les parts de dépense sont constantes.", 5),
    ("Comment la courbe de coût moyen de long terme se rapporte-t-elle aux courbes de court terme ?", "Elle en est l'enveloppe inférieure", ["Elle les coupe toujours en leur minimum", "Elle est toujours au-dessus", "Elle ne leur est pas reliée"], "À long terme, la firme choisit la taille la moins coûteuse pour chaque volume.", 5),
    ("Qu'implique la règle de l'élasticité inverse pour un monopole ?", "L'écart relatif prix-coût marginal est égal à l'inverse de l'élasticité-prix (en valeur absolue)", ["Le prix est égal au coût marginal", "Le prix est indépendant de l'élasticité", "L'écart est égal à l'élasticité-revenu"], "(P − Cm)/P = 1/|e|.", 5),
    ("Dans une discrimination du troisième degré, comment le prix varie-t-il entre groupes ?", "Il est plus élevé pour le groupe dont la demande est la moins élastique", ["Il est plus élevé pour le groupe dont la demande est la plus élastique et la plus nombreuse", "Il est identique pour tous", "Il est fixé par l'État"], "Le monopole segmente le marché selon l'élasticité.", 5),
    ("À l'équilibre de long terme en concurrence monopolistique, comment se comparent prix et coût moyen ?", "Ils sont égaux, avec un prix supérieur au coût marginal", ["Le prix est égal au coût marginal", "Le prix est inférieur au coût moyen", "Le profit est positif et croissant tant que la libre entrée est possible"], "La libre entrée annule le profit, mais le pouvoir de marché subsiste.", 5),
    ("Dans un oligopole de Cournot à n firmes (coût marginal constant), vers quoi tend le prix quand n augmente ?", "Vers le coût marginal", ["Vers le prix de monopole", "Vers zéro dans tous les cas", "Vers l'infini"], "La concurrence augmente avec le nombre d'entreprises.", 5),
    ("Que sont des courbes de réaction décroissantes dans le modèle de Cournot ?", "La quantité optimale d'une firme baisse quand celle de l'autre augmente", ["La quantité optimale d'une firme augmente avec celle de l'autre, de façon proportionnelle", "Les prix baissent à mesure que la demande croît", "Les coûts décroissent avec la production"], "On parle de substituts stratégiques.", 5),
    ("Qu'est-ce qu'une menace non crédible dans un jeu séquentiel ?", "Une menace que le joueur n'a pas intérêt à exécuter une fois le moment venu", ["Une menace toujours exécutée", "Une menace faite en public", "Une menace de baisser son prix"], "Elle ne subsiste pas dans l'équilibre parfait en sous-jeux.", 4),
    ("Que garantit l'équilibre parfait en sous-jeux (Selten) ?", "Les stratégies forment un équilibre de Nash dans chaque sous-jeu", ["Les joueurs jouent toujours simultanément, sans voir les coups précédents ni leurs gains", "Les gains sont toujours égaux", "Aucun joueur n'a de stratégie dominante"], "Il élimine les menaces non crédibles.", 5),
    ("Dans une enchère de Vickrey (au second prix), quelle est la stratégie dominante ?", "Enchérir sa véritable valeur", ["Enchérir moins que sa valeur", "Enchérir plus que sa valeur", "Ne pas participer"], "Le gagnant paie la deuxième meilleure offre.", 5),
    ("Qu'énonce le théorème d'impossibilité d'Arrow ?", "Aucune règle de vote ne satisfait à la fois un ensemble de conditions raisonnables d'agrégation", ["Le vote majoritaire est toujours efficace", "Les électeurs sont irrationnels", "Il n'existe aucun gouvernement stable"], "Il met en évidence les limites du choix collectif.", 5),
    ("Que décrit le paradoxe de Condorcet ?", "La possibilité de préférences collectives cycliques avec un vote majoritaire", ["La hausse de la participation électorale avec l'âge des citoyens dans un pays donné", "La baisse du pouvoir d'achat", "L'indifférence des électeurs"], "A est préféré à B, B à C et C à A.", 4),
    ("Que dit le théorème de l'électeur médian ?", "Avec des préférences à sommet unique, la majorité retient la position de l'électeur médian", ["La majorité retient la position la plus extrême", "Le résultat dépend de la moyenne des revenus des électeurs, quelles que soient leurs préférences", "Le vote ne produit jamais de résultat"], "Il prédit la convergence des programmes vers le centre.", 5),
    ("Quelle est la condition d'efficacité de Samuelson pour un bien public ?", "La somme des taux marginaux de substitution des individus égale le coût marginal", ["Chaque individu paie son coût marginal", "Le prix égale le coût moyen", "La production est nulle"], "Contrairement aux biens privés, la demande se somme verticalement.", 5),
    ("Dans l'analyse du monopsone sur le marché du travail, comment le salaire se compare-t-il à la productivité marginale ?", "Il lui est inférieur", ["Il lui est supérieur", "Il lui est toujours égal", "Il est nul"], "L'employeur tient compte de l'effet de l'embauche sur le salaire de tous.", 5),
    ("Dans un modèle de monopsone, que peut produire un salaire minimum modéré ?", "Une hausse de l'emploi", ["Une baisse certaine de l'emploi", "Aucun effet", "Une hausse certaine du chômage"], "Il peut rapprocher le salaire de la productivité marginale.", 5),
    ("Qu'est-ce que la discrimination statistique (Phelps, Arrow) ?", "L'usage de caractéristiques moyennes d'un groupe pour juger un individu faute d'information", ["Une discrimination fondée sur le goût", "Une discrimination interdite par la loi seule", "Une discrimination mesurée par la statistique"], "Elle peut exister sans préjugé hostile.", 5),
    ("Qu'est-ce que l'équilibre séparateur dans un modèle d'assurance de Rothschild-Stiglitz ?", "Chaque type de risque choisit un contrat différent, ce qui révèle son type", ["Chaque assuré choisit le même contrat, quel que soit son niveau de risque, par construction", "L'assureur ne propose aucun contrat", "Les assurés ne paient aucune prime"], "Les contrats se distinguent par la prime et la franchise ou la couverture.", 5),
    ("Que décrit le « screening » (filtrage) en information asymétrique ?", "Le côté mal informé propose des contrats ou tests pour amener l'autre à révéler son type", ["Le côté bien informé choisit un signal", "L'État tire au sort les contrats", "Aucun contrat n'est proposé"], "La signalisation (signaling) est l'initiative inverse.", 5),
    ("Que décrit la théorie des marchés contestables (Baumol) ?", "La menace d'entrée peut discipliner même un monopole en l'absence de coûts irrécupérables", ["Les monopoles sont toujours inefficaces", "L'entrée est toujours impossible", "Les firmes ne réagissent jamais aux entrants"], "Le prix reste proche du coût moyen.", 5),
    ("Quel critère d'évaluation propose Kaldor-Hicks pour un changement de politique ?", "Les gagnants pourraient compenser les perdants et rester gagnants", ["Personne ne doit perdre", "La majorité doit gagner", "Le revenu doit augmenter pour chaque individu concerné par le changement de politique"], "Il élargit le critère de Pareto.", 5),
    ("Quelle est l'idée de la rente différentielle de Ricardo ?", "Les terres plus fertiles ou mieux situées rapportent une rente du fait de leur productivité", ["Toutes les terres rapportent la même rente", "La rente vient du travail seul", "La rente est fixée par l'État"], "La terre marginale ne rapporte pas de rente.", 4),
    ("Qu'est-ce que la spécificité des actifs (Williamson) ?", "Un actif dont la valeur est bien plus élevée dans une relation précise que dans un autre usage", ["Un actif toujours revendable au même prix", "Un actif réservé à l'État", "Un actif sans valeur"], "Elle favorise l'intégration au sein de la firme.", 5),
    ("Quelle explication Coase donne-t-il à l'existence des firmes ?", "Le recours au marché a des coûts de transaction que l'organisation interne peut réduire", ["Les firmes servent à éviter l'impôt", "Les marchés n'ont aucun coût", "Les firmes sont créées par l'État"], "La firme se développe jusqu'à ce que le coût d'organisation égale celui du marché.", 4),
    ("Que décrit un équilibre de Nash en stratégies mixtes ?", "Chaque joueur rend l'autre indifférent entre ses stratégies pures", ["Chaque joueur choisit toujours la même stratégie, quel que soit le jeu joué", "Un joueur maximise le gain de l'autre", "Aucune stratégie n'est jouée avec probabilité"], "Il existe dans tout jeu fini (théorème de Nash).", 5),
    ("Que dit le théorème de Nash sur l'existence d'un équilibre ?", "Tout jeu fini possède au moins un équilibre de Nash en stratégies mixtes", ["Aucun jeu ne possède d'équilibre", "Seuls les jeux à somme nulle ont un équilibre", "Tout jeu a un équilibre en stratégies dominantes pour chacun des joueurs"], "L'équilibre peut exiger de la randomisation.", 5),
    ("Que dit le théorème de Modigliani-Miller (sans impôts, marchés parfaits) ?", "La valeur de la firme est indépendante de sa structure financière", ["La dette augmente toujours la valeur", "Les capitaux propres augmentent toujours la valeur de la firme, quelle que soit la structure", "Les dividendes déterminent seuls la valeur"], "Il sert de référence pour étudier les imperfections.", 5),
    ("Que décrit l'hypothèse d'efficience des marchés (Fama) ?", "Les prix des actifs reflètent l'information disponible", ["Les prix des actifs sont toujours fixes", "Les marchés sont fermés", "Les investisseurs sont toujours irrationnels"], "On distingue formes faible, semi-forte et forte.", 4),
    ("Dans la forme semi-forte de l'efficience, que reflètent les prix ?", "Toute l'information publique disponible", ["Seulement les prix passés", "Toute information, même privée", "Aucune information"], "La forme faible se limite aux prix passés, la forme forte inclut l'information privée.", 5),
    ("Que dit le MEDAF (CAPM) à propos du rendement attendu d'un actif ?", "Il est égal au taux sans risque plus le bêta fois la prime de risque du marché", ["Il est égal au taux sans risque seul", "Il dépend du risque spécifique seul", "Il est toujours égal à zéro"], "Le bêta mesure le risque systématique.", 5),
    ("Que peut réduire la diversification d'un portefeuille ?", "Le risque spécifique d'un actif, non le risque systématique", ["Le risque systématique, non le risque spécifique", "Chacun des deux risques, jusqu'à zéro", "Aucun risque"], "Le risque de marché subsiste.", 4),
    ("Comment varie le prix d'une obligation à taux fixe quand les taux d'intérêt du marché montent ?", "Il baisse", ["Il monte", "Il reste constant", "Il double"], "Les flux fixes sont actualisés à un taux plus élevé.", 4),
    ("Qu'est-ce que la titrisation ?", "La transformation de créances en titres financiers négociables", ["La création d'un titre de propriété foncière sur des terres agricoles", "La suppression des dettes", "L'impression de monnaie"], "Elle permet de sortir des créances du bilan d'une banque.", 4),
    ("Quelle est la logique du critère de la valeur actuelle nette pour choisir un projet ?", "Accepter le projet si la somme des flux actualisés dépasse l'investissement initial", ["Accepter le projet si l'investissement initial est faible, quels que soient les flux futurs", "Accepter le projet le plus long", "Refuser tout projet sans profit immédiat"], "Une VAN positive crée de la valeur.", 3),
    ("Qu'est-ce qu'un instrument dérivé ?", "Un contrat dont la valeur dépend de celle d'un actif sous-jacent", ["Un titre de propriété foncière sur un bien immobilier, qui ne dépend d'aucun sous-jacent", "Une monnaie étrangère", "Un impôt indirect"], "Options et contrats à terme en sont des exemples.", 4),
    ("Que décrit le risque systématique d'un actif ?", "La part du risque liée à l'évolution générale du marché", ["La part du risque propre à une entreprise ou à un secteur d'activité précis", "Un risque inexistant", "Le risque de défaut d'un État uniquement"], "Il ne se diversifie pas.", 4),
    ("Qu'est-ce que la macroprudence ?", "Une régulation visant le risque systémique du système financier dans son ensemble", ["La supervision d'une seule banque seulement, sans considération pour l'ensemble du système", "La réduction des dépenses de l'État", "La fixation des prix"], "Elle complète la microprudence centrée sur chaque établissement.", 5),
    ("Quel est le risque d'aléa moral lié aux banques « trop grandes pour faire faillite » ?", "Elles peuvent prendre des risques excessifs en comptant sur un soutien public", ["Elles refusent de prêter", "Elles ferment leurs guichets", "Elles fixent le taux directeur"], "Les gains sont privés, les pertes potentiellement socialisées.", 4),
    ("Selon Keynes, comment le taux d'intérêt est-il déterminé ?", "Par l'offre et la demande de monnaie : il rémunère le renoncement à la liquidité", ["Par la seule épargne", "Par le seul investissement", "Par le prix de l'or"], "C'est la théorie de la préférence pour la liquidité.", 5),
    ("Quelle est la principale caractéristique d'un taux d'intérêt réel négatif ?", "L'inflation dépasse le taux nominal", ["L'inflation est nulle", "Le taux nominal est supérieur à l'inflation", "Le chômage est nul"], "Taux réel ≈ nominal − inflation.", 3),
], cat="Microéconomie avancée et finance", src="sup-eco-micro")

# =====================================================================================================================
# DÉVELOPPEMENT ÉCONOMIQUE (L1 / L2 / L3 selon la difficulté)
# =====================================================================================================================
table(L3, "eco3-dev-concepts", [
    ("l'indice de développement humain", "un indicateur composite combinant santé, éducation et niveau de vie", 2),
    ("le coefficient de Gini", "une mesure des inégalités allant de 0 (égalité parfaite) à 1 (inégalité maximale)", 3),
    ("la courbe de Lorenz", "la représentation de la part cumulée du revenu détenue par la part cumulée de la population", 4),
    ("le seuil de pauvreté absolue", "le niveau de ressources en dessous duquel on ne satisfait pas les besoins essentiels", 3),
    ("la pauvreté relative", "la situation de ceux dont le revenu est nettement inférieur à celui du reste de la population", 3),
    ("l'aide publique au développement", "les flux publics concessionnels destinés aux pays en développement", 3),
    ("l'initiative PPTE", "un dispositif d'allègement de la dette des pays pauvres très endettés", 4),
    ("le Club de Paris", "un groupe de créanciers publics qui négocie le traitement de la dette des États", 4),
    ("le Club de Londres", "un cadre de négociation de la dette d'États avec leurs créanciers bancaires privés", 5),
    ("un programme d'ajustement structurel", "un ensemble de réformes conditionnant un financement du FMI ou de la Banque mondiale", 4),
    ("la malédiction des ressources", "la tendance de pays riches en ressources naturelles à connaître une croissance décevante", 4),
    ("l'industrialisation par substitution d'importations", "la production locale de biens auparavant importés derrière des protections", 4),
    ("la transition démographique", "le passage d'une natalité et d'une mortalité élevées à une natalité et une mortalité faibles", 4),
    ("la révolution verte", "la diffusion de variétés à haut rendement, d'engrais et d'irrigation dans l'agriculture", 3),
    ("l'investissement direct étranger", "l'acquisition durable d'une participation dans une entreprise d'un autre pays", 3),
    ("le capital humain", "l'ensemble des connaissances, compétences et de la santé des travailleurs", 3),
], cat="Développement économique", src="sup-eco-dev",
    fwd="Comment définit-on {a} ?", rev="Quel concept de développement est décrit ici : {b} ?")

table(L2, "eco2-dev-theor", [
    ("Arthur Lewis", "un modèle dualiste où le secteur traditionnel fournit au secteur moderne une main-d'œuvre abondante", 4),
    ("Walt Rostow", "des étapes de la croissance, avec un « décollage » après des conditions préalables", 3),
    ("Ragnar Nurkse", "le cercle vicieux de la pauvreté, où la faible épargne bloque l'investissement", 4),
    ("Paul Rosenstein-Rodan", "la théorie du grand élan (big push) : un investissement coordonné pour sortir du sous-développement", 5),
    ("Raúl Prebisch", "l'analyse centre-périphérie et la dégradation des termes de l'échange des produits primaires", 4),
    ("Amartya Sen", "le développement entendu comme l'extension des libertés réelles et des capabilités", 4),
    ("Michael Todaro", "un modèle de migration rurale-urbaine fondé sur le revenu urbain espéré", 5),
    ("John Williamson", "la formulation de l'expression « consensus de Washington » en 1989", 5),
], cat="Développement économique : auteurs", src="sup-eco-dev", extra_a=("Joseph Schumpeter", "Karl Marx", "David Ricardo"),
    fwd="Quel apport est associé à {a} ?", rev="Quel auteur est associé à : {b} ?", min_rows=5)

table(L1, "eco1-dev-sect", [
    ("le secteur primaire", "les activités d'extraction et de production à partir de la nature : agriculture, pêche, mines", 1),
    ("le secteur secondaire", "les activités de transformation des matières premières : industrie, construction", 1),
    ("le secteur tertiaire", "les activités de services : commerce, transport, banque, éducation, santé", 1),
    ("le secteur informel", "les activités économiques qui échappent en grande partie à l'enregistrement et à la fiscalité", 2),
    ("le secteur public", "l'ensemble des activités des administrations et des entreprises contrôlées par l'État", 2),
    ("le secteur privé", "l'ensemble des entreprises appartenant à des particuliers ou à des sociétés non étatiques", 2),
], cat="Développement économique : secteurs", src="sup-eco-dev",
    fwd="Comment définit-on {a} ?", rev="De quel secteur s'agit-il : {b} ?")

split3("eco2-dev-q1", [
    ("Quelles dimensions l'indice de développement humain (IDH) combine-t-il ?", "La santé, l'éducation et le niveau de vie", ["Le chômage, l'inflation et la dette", "La population, la superficie et le climat", "Les exportations, les importations et le change"], "Il est publié par le Programme des Nations unies pour le développement.", 2),
    ("Quelle institution publie l'indice de développement humain ?", "Le Programme des Nations unies pour le développement (PNUD)", ["L'Organisation mondiale du commerce", "Le Fonds monétaire international", "La BEAC"], "Il a été conçu notamment avec Mahbub ul Haq et Amartya Sen.", 3),
    ("Entre quelles valeurs se situe l'indice de développement humain ?", "Entre 0 et 1", ["Entre 0 et 100", "Entre −1 et 1", "Entre 1 et 10"], "Plus il est proche de 1, plus le niveau de développement humain est élevé.", 3),
    ("Que mesure un coefficient de Gini égal à 0 ?", "Une égalité parfaite des revenus", ["Une inégalité maximale", "Une pauvreté absolue", "Un taux de chômage nul"], "Il vaut 1 quand une seule personne détient tout le revenu.", 3),
    ("Selon Amartya Sen, quel est l'objectif du développement ?", "Étendre les capabilités et les libertés réelles des personnes", ["Maximiser le seul PIB", "Maximiser les exportations et la production de matières premières du pays", "Réduire la population"], "Le revenu est un moyen, non une fin.", 4),
    ("Que décrit le modèle dualiste de Lewis ?", "Un secteur traditionnel à main-d'œuvre excédentaire alimente un secteur moderne", ["Deux monnaies circulant dans un même pays", "Deux régimes de change simultanés", "Deux banques centrales"], "Le transfert de travail alimente l'accumulation du secteur moderne.", 5),
    ("Quelle étape de la croissance de Rostow correspond à l'essor rapide de l'investissement et de l'industrialisation ?", "Le décollage", ["La société traditionnelle", "La consommation de masse", "La maturité"], "Elle suit les conditions préalables.", 4),
    ("Que décrit le cercle vicieux de la pauvreté de Nurkse ?", "Le faible revenu limite l'épargne et l'investissement, ce qui maintient le faible revenu", ["La hausse du revenu réduit l'épargne", "La hausse de l'épargne réduit l'investissement", "L'inflation réduit la population"], "Il illustre le piège du sous-développement.", 4),
    ("Que propose la théorie du grand élan (big push) ?", "Un investissement massif et coordonné pour sortir d'un équilibre de bas niveau", ["Un investissement très faible et progressif, projet après projet, sans aucune coordination", "L'arrêt de toute industrie", "La baisse de tous les impôts"], "Elle s'appuie sur les complémentarités entre projets.", 5),
    ("Quel est l'argument de la thèse dite « de la dépendance » ?", "Les pays périphériques sont structurellement liés à des pays du centre dans un échange défavorable", ["Chaque pays échange à égalité avec les autres", "Le sous-développement vient d'un climat seul", "La croissance est toujours endogène"], "Elle critique la vision linéaire du développement.", 5),
    ("Que sont les programmes d'ajustement structurel ?", "Des réformes conditionnant l'aide du FMI ou de la Banque mondiale à des pays en difficulté", ["Des programmes de construction de routes", "Des plans de relance par la dépense publique uniquement, sans aucune réforme ni conditionnalité", "Des programmes d'alphabétisation"], "Ils se sont développés dans les années 1980.", 4),
    ("Qu'est-ce que le « consensus de Washington » ?", "Un ensemble de recommandations libérales pour les pays en développement", ["Un traité commercial régional africain signé pour supprimer les droits de douane", "Une monnaie commune", "Un accord de défense"], "Il comprend discipline budgétaire, libéralisation et privatisations.", 4),
    ("À quoi sert l'initiative PPTE ?", "À alléger la dette de pays pauvres très endettés", ["À accorder des prêts à taux nul aux pays riches", "À taxer les exportations", "À fixer les prix du pétrole"], "Elle est portée par le FMI et la Banque mondiale.", 3),
    ("Que négocie le Club de Paris ?", "Le traitement de la dette publique bilatérale d'États débiteurs", ["La dette privée des entreprises", "La parité des monnaies", "Les droits de douane"], "Les créanciers y sont des États.", 4),
    ("Qu'est-ce que l'aide publique au développement (APD) ?", "Des flux publics concessionnels vers des pays en développement", ["Des investissements privés étrangers", "Des exportations de biens vers l'étranger", "Des prêts commerciaux sans condition"], "Elle comprend dons et prêts à conditions avantageuses.", 3),
    ("Quelle différence y a-t-il entre aide bilatérale et aide multilatérale ?", "L'aide bilatérale va d'un État à un autre, l'aide multilatérale transite par une organisation", ["L'aide bilatérale est toujours privée", "L'aide multilatérale est toujours un prêt", "Il n'y a aucune différence"], "Banque mondiale et PNUD sont des canaux multilatéraux.", 3),
    ("Qu'est-ce qu'une aide liée ?", "Une aide conditionnée à l'achat de biens ou de services au pays donateur", ["Une aide versée sans aucune condition ni contrepartie, par un donateur privé uniquement", "Une aide privée", "Une aide à l'exportation du bénéficiaire"], "Elle réduit la valeur réelle de l'aide.", 4),
    ("Que sont les transferts de fonds des migrants ?", "Des envois d'argent vers le pays d'origine par des travailleurs à l'étranger", ["Des dons de l'État à ses citoyens", "Des impôts payés à l'étranger", "Des prêts de la Banque mondiale"], "Ils représentent une source de devises pour de nombreux pays.", 2),
    ("Qu'est-ce que la fuite des cerveaux ?", "L'émigration de personnes qualifiées vers l'étranger", ["L'immigration de travailleurs peu qualifiés", "La baisse du taux d'alphabétisation", "L'achat de brevets"], "Elle prive le pays d'origine de capital humain.", 2),
    ("Que décrit le modèle de Todaro ?", "La migration vers la ville dépend du revenu urbain espéré et du risque de chômage", ["La migration dépend de la seule distance", "La migration est impossible", "La migration dépend de la monnaie"], "Les migrants comparent le revenu espéré en ville à celui des campagnes.", 5),
    ("Que décrit le syndrome hollandais pour un pays exportateur de matières premières ?", "Un boom des exportations peut renchérir la monnaie et pénaliser les autres secteurs", ["Un boom des exportations fait baisser durablement les prix de tous les biens échangeables", "Un boom des exportations supprime le chômage", "Un boom des importations fait monter le taux"], "Les secteurs exposés à la concurrence internationale perdent en compétitivité.", 5),
    ("Que décrit le rapport Brundtland sur le développement durable ?", "Un développement qui répond aux besoins du présent sans compromettre ceux des générations futures", ["Un développement fondé sur la seule croissance du PIB", "Un développement fondé sur l'aide", "Un développement fondé sur l'industrie lourde"], "La définition date de 1987.", 3),
    ("Quels sont les trois piliers du développement durable ?", "Économique, social et environnemental", ["Politique, militaire et culturel", "Public, privé et mixte", "Primaire, secondaire et tertiaire"], "Ils doivent être poursuivis ensemble.", 2),
    ("Que mesure l'indice de pauvreté multidimensionnelle ?", "Les privations simultanées en matière de santé, d'éducation et de conditions de vie", ["Le seul revenu par habitant", "Le seul taux de chômage", "La seule dette extérieure"], "Il va au-delà de la pauvreté monétaire.", 4),
    ("Que propose Fourastié à propos de l'évolution des emplois ?", "Un transfert progressif des emplois du primaire vers le secondaire puis le tertiaire", ["Un transfert progressif des emplois tertiaires vers le primaire, puis vers le secondaire", "Une disparition de tous les emplois", "Une hausse constante de l'emploi primaire"], "Fourastié a décrit ce mouvement entre grands secteurs.", 4),
    ("Qu'est-ce que la microfinance ?", "L'offre de petits services financiers (crédits, épargne) à des personnes exclues des banques", ["La finance des très grandes entreprises", "Un impôt sur les petits revenus", "Un type d'obligation d'État"], "Muhammad Yunus et la Grameen Bank en sont l'illustration.", 3),
    ("Quel est l'objectif d'une tontine rotative d'épargne ?", "Permettre à chaque membre de recevoir à tour de rôle la cagnotte collectée", ["Distribuer des actions à la Bourse", "Fixer le taux directeur", "Remplacer la banque centrale"], "Elle est répandue dans de nombreux pays africains, dont le Cameroun.", 2),
    ("Dans quel secteur classe-t-on la transformation du cacao en chocolat ?", "Le secteur secondaire", ["Le secteur primaire", "Le secteur tertiaire", "Le secteur informel"], "Il s'agit d'une transformation de matière première.", 2),
    ("Dans quel secteur classe-t-on la culture du cacao ?", "Le secteur primaire", ["Le secteur secondaire", "Le secteur tertiaire", "Le secteur public"], "L'agriculture est une activité du secteur primaire.", 1),
    ("Dans quel secteur classe-t-on l'activité d'une banque ?", "Le secteur tertiaire", ["Le secteur primaire", "Le secteur secondaire", "L'économie agricole"], "Les services marchands relèvent du tertiaire.", 1),
    ("Dans quel secteur classe-t-on l'extraction du pétrole brut ?", "Le secteur primaire", ["Le secteur secondaire", "Le secteur tertiaire", "Le secteur informel"], "L'extraction de ressources naturelles est une activité primaire.", 2),
    ("Dans quel secteur classe-t-on une raffinerie ?", "Le secteur secondaire", ["Le secteur primaire", "Le secteur tertiaire", "Le secteur public"], "Elle transforme le pétrole brut en produits raffinés.", 2),
    ("Dans quel secteur classe-t-on l'enseignement ?", "Le secteur tertiaire", ["Le secteur primaire", "Le secteur secondaire", "Le secteur extractif"], "L'enseignement est un service.", 1),
    ("Dans quel secteur classe-t-on la pêche ?", "Le secteur primaire", ["Le secteur secondaire", "Le secteur tertiaire", "Le secteur industriel"], "Elle consiste à prélever une ressource naturelle.", 1),
    ("Dans quel secteur classe-t-on la construction de routes et de bâtiments ?", "Le secteur secondaire", ["Le secteur primaire", "Le secteur tertiaire", "Le secteur extractif"], "Le BTP est classé avec l'industrie.", 2),
    ("Dans quel secteur classe-t-on le transport de marchandises ?", "Le secteur tertiaire", ["Le secteur primaire", "Le secteur secondaire", "Le secteur extractif"], "C'est un service.", 1),
], cat="Développement économique", src="sup-eco-dev")

# =====================================================================================================================
# L1 : NOTIONS DE BASE
# =====================================================================================================================
table(L1, "eco1-agents", [
    ("les ménages", "les agents qui consomment, offrent leur travail et épargnent une part de leur revenu", 1),
    ("les entreprises", "les agents qui produisent des biens et des services destinés au marché", 1),
    ("les administrations publiques", "les agents qui fournissent des services collectifs et redistribuent des revenus", 2),
    ("les institutions financières", "les agents qui collectent l'épargne et accordent des crédits", 2),
    ("le reste du monde", "les agents non résidents qui échangent avec l'économie nationale", 2),
], cat="Bases de l'économie : agents économiques", src="sup-eco-macro",
    fwd="Quel est le rôle économique de {a} ?", rev="De quels agents économiques s'agit-il : {b} ?")

table(L1, "eco1-biens", [
    ("un bien de consommation", "un bien destiné à satisfaire directement les besoins des ménages", 1),
    ("un bien d'équipement", "un bien durable utilisé pour produire d'autres biens", 2),
    ("un bien intermédiaire", "un bien transformé ou consommé au cours du processus de production", 2),
    ("un bien durable", "un bien qui peut être utilisé de nombreuses fois", 1),
    ("un bien non durable", "un bien détruit ou consommé dès la première utilisation", 1),
    ("un service", "une prestation immatérielle qui ne se stocke pas", 1),
    ("un bien libre", "un bien disponible en abondance, sans coût de production ni prix", 2),
    ("un bien économique", "un bien rare, qui exige des ressources pour être produit et qui a un prix", 2),
], cat="Bases de l'économie : biens et services", src="sup-eco-micro",
    fwd="Comment définit-on {a} ?", rev="De quel type de bien ou de service s'agit-il : {b} ?")

table(L1, "eco1-marche", [
    ("l'offre", "la quantité qu'un producteur est prêt à vendre à un prix donné", 1),
    ("la demande", "la quantité que les acheteurs souhaitent acquérir à un prix donné", 1),
    ("le prix d'équilibre", "le prix auquel l'offre et la demande s'égalisent", 1),
    ("une pénurie", "une situation où la demande dépasse l'offre au prix en vigueur", 2),
    ("un excédent", "une situation où l'offre dépasse la demande au prix en vigueur", 2),
    ("un prix plafond", "un prix maximal fixé par la loi ou par l'autorité", 2),
    ("un prix plancher", "un prix minimal fixé par la loi ou par l'autorité", 2),
    ("la concurrence", "la rivalité entre vendeurs ou entre acheteurs sur un marché", 1),
    ("une élasticité", "la sensibilité d'une quantité à la variation d'une autre variable, en pourcentage", 3),
], cat="Bases de l'économie : marché", src="sup-eco-micro",
    fwd="Que signifie {a} ?", rev="Quelle notion de marché correspond à : {b} ?")

table(L1, "eco1-monnaie", [
    ("la monnaie", "tout moyen de paiement généralement accepté dans les échanges", 1),
    ("le troc", "l'échange direct d'un bien contre un autre, sans monnaie", 1),
    ("une banque commerciale", "un établissement qui reçoit des dépôts et accorde des crédits", 1),
    ("un dépôt bancaire", "une somme confiée à une banque par un client", 1),
    ("un crédit", "une somme avancée à un emprunteur contre remboursement et intérêts", 1),
    ("l'intérêt", "la rémunération payée par l'emprunteur au prêteur", 1),
    ("le taux d'intérêt", "le prix de l'argent prêté, exprimé en pourcentage du capital", 2),
    ("une bourse de valeurs", "un marché où s'échangent des titres comme les actions et les obligations", 2),
    ("un virement", "un transfert d'argent d'un compte bancaire à un autre", 1),
], cat="Bases de l'économie : monnaie et banque", src="sup-eco-monnaie",
    fwd="Que signifie {a} ?", rev="Quelle notion monétaire correspond à : {b} ?")

table(L1, "eco1-budget", [
    ("le budget de l'État", "le document qui prévoit et autorise les recettes et les dépenses publiques de l'année", 2),
    ("un impôt direct", "un impôt prélevé directement sur le revenu ou le patrimoine du contribuable", 2),
    ("un impôt indirect", "un impôt prélevé sur la dépense ou la consommation, comme la TVA", 2),
    ("une subvention", "une aide financière publique versée à une entreprise ou à un secteur", 2),
    ("la redistribution", "le transfert de revenus d'un groupe à un autre par les impôts et les prestations", 3),
    ("le salaire", "la rémunération du travail d'un salarié", 1),
    ("le profit", "la différence entre les recettes et les coûts d'une entreprise", 1),
    ("le chiffre d'affaires", "le total des ventes d'une entreprise sur une période", 1),
    ("la productivité", "le rapport entre la production obtenue et les ressources utilisées", 2),
    ("la division du travail", "la répartition des tâches entre travailleurs pour accroître la productivité", 2),
], cat="Bases de l'économie : finances publiques et entreprise", src="sup-eco-macro",
    fwd="Que signifie {a} ?", rev="Quelle notion correspond à : {b} ?")

table(L1, "eco1-ecoles", [
    ("les mercantilistes", "l'idée que la richesse repose sur l'accumulation de métaux précieux grâce à l'excédent commercial", 3),
    ("les physiocrates", "l'idée que la terre est la source de toute richesse", 3),
    ("les classiques", "l'étude de la croissance, de la division du travail et des vertus du marché libre", 3),
    ("les marginalistes", "l'idée que la valeur dépend de l'utilité de la dernière unité consommée", 4),
    ("les keynésiens", "l'idée que la demande globale détermine l'activité à court terme et que l'État peut la soutenir", 3),
    ("les monétaristes", "l'idée que la monnaie est le principal déterminant de l'inflation", 3),
    ("les marxistes", "une critique du capitalisme fondée sur l'exploitation du travail et la lutte des classes", 3),
], cat="Histoire de la pensée économique", src="sup-eco-pensee",
    fwd="Quelle idée centrale caractérise {a} ?", rev="Quelle école de pensée est caractérisée par : {b} ?")

classify(L1, "eco1-cls-agent", {
    "un ménage": ["une famille qui achète sa nourriture au marché", "un salarié qui épargne une partie de son salaire", "un étudiant qui paie son loyer"],
    "une entreprise": ["une brasserie qui produit de la bière", "un atelier de menuiserie qui vend des meubles", "une société de transport qui vend des billets"],
    "une administration publique": ["un ministère qui construit des routes", "un hôpital public géré par l'État", "la direction des impôts"],
    "une institution financière": ["une banque commerciale qui accorde des crédits", "une compagnie d'assurance", "une institution de microfinance"],
}, cat="Bases de l'économie : agents économiques", src="sup-eco-macro",
    fwd="Dans quelle catégorie d'agents économiques range-t-on {item} ?", rev="Lequel de ces exemples illustre la catégorie « {group} » ?", diff=1)

classify(L1, "eco1-cls-biens", {
    "un bien de consommation": ["un paquet de biscuits acheté par un ménage", "une paire de chaussures achetée pour un usage personnel", "une bouteille d'eau achetée au marché"],
    "un bien d'équipement": ["un tracteur utilisé dans une exploitation agricole", "une machine à coudre dans un atelier", "un camion de livraison d'une entreprise"],
    "un bien intermédiaire": ["la farine achetée par une boulangerie", "le bois acheté par un menuisier", "le cacao brut acheté par une usine de transformation"],
    "un service": ["une consultation médicale", "une course en taxi", "un cours particulier"],
}, cat="Bases de l'économie : biens et services", src="sup-eco-micro",
    fwd="De quelle catégorie relève {item} ?", rev="Lequel de ces exemples correspond à la catégorie « {group} » ?", diff=2)

classify(L1, "eco1-cls-zone", {
    "un État membre de la CEMAC": ["le Cameroun", "le Gabon", "le Tchad", "la République centrafricaine", "la Guinée équatoriale", "la République du Congo"],
    "un État membre de l'UEMOA": ["le Sénégal", "la Côte d'Ivoire", "le Mali", "le Burkina Faso", "le Niger", "le Togo"],
    "un État d'Afrique de l'Est": ["le Kenya", "la Tanzanie", "l'Ouganda", "l'Éthiopie"],
    "un État d'Afrique australe": ["la Zambie", "le Botswana", "la Namibie", "le Mozambique"],
}, cat="Zone franc et CEMAC", src="sup-eco-cemac", region="AF",
    fwd="À quel ensemble appartient {item} ?", rev="Lequel de ces pays relève de la catégorie « {group} » ?", diff=2)

classify(L2, "eco2-cls-chom", {
    "le chômage frictionnel": ["un diplômé qui cherche son premier poste depuis quelques semaines", "un salarié qui a démissionné et compare plusieurs offres"],
    "le chômage structurel": ["un ouvrier dont le métier a disparu faute de formation adaptée", "des chômeurs dans une région sans entreprise alors que des offres existent ailleurs"],
    "le chômage conjoncturel": ["des licenciements dus à un net recul de la demande pendant une récession", "des embauches gelées parce que les commandes baissent partout"],
    "le chômage saisonnier": ["des ouvriers agricoles sans travail entre deux récoltes", "des guides touristiques sans emploi en basse saison"],
}, cat="Macroéconomie : emploi et chômage", src="sup-eco-macro",
    fwd="De quel type de chômage relève la situation suivante : {item} ?", rev="Laquelle de ces situations correspond à « {group} » ?", diff=3)

classify(L2, "eco2-cls-marche", {
    "un monopole": ["une entreprise unique fournissant l'eau d'une ville sans aucun concurrent", "une société unique de chemin de fer sans concurrent"],
    "un oligopole": ["un marché de la bière dominé par deux ou trois grandes brasseries", "un marché du ciment partagé entre quelques cimentiers"],
    "une concurrence monopolistique": ["les restaurants d'une grande ville, chacun avec sa propre carte", "les salons de coiffure d'un quartier proposant des services différents"],
    "une concurrence pure et parfaite": ["des milliers de petits cultivateurs de maïs vendant un produit identique", "des centaines de petits producteurs de tomates preneurs de prix"],
}, cat="Microéconomie : structures de marché", src="sup-eco-micro",
    fwd="Quelle structure de marché illustre la situation suivante : {item} ?", rev="Laquelle de ces situations illustre « {group} » ?", diff=3)

classify(L2, "eco2-cls-politique", {
    "la politique budgétaire": ["une hausse des dépenses d'investissement public", "une baisse de l'impôt sur le revenu", "la création d'un nouvel impôt indirect"],
    "la politique monétaire": ["une baisse du taux directeur", "une hausse des réserves obligatoires", "des achats de titres par la banque centrale"],
    "la politique commerciale": ["l'instauration d'un quota d'importation", "la hausse d'un droit de douane", "une subvention à l'exportation"],
    "la politique de change": ["la dévaluation officielle de la monnaie", "l'achat de sa propre monnaie par la banque centrale sur le marché des changes", "le passage d'un change fixe à un change flottant"],
}, cat="Macroéconomie : politiques économiques", src="sup-eco-macro",
    fwd="De quelle politique économique relève {item} ?", rev="Laquelle de ces mesures relève de « {group} » ?", diff=3)

classify(L3, "eco3-cls-info", {
    "la sélection adverse": ["un assureur ne distingue pas les conducteurs prudents des imprudents avant la signature", "un vendeur connaît la qualité d'une voiture d'occasion que l'acheteur ignore"],
    "l'aléa moral": ["un assuré contre le vol néglige de fermer sa porte", "un emprunteur prend des risques excessifs une fois le crédit accordé"],
    "la signalisation": ["un candidat obtient un diplôme coûteux pour montrer sa capacité", "une entreprise offre une longue garantie pour prouver la qualité de ses produits"],
    "le passager clandestin": ["un habitant profite de l'éclairage public sans participer aux frais", "un membre d'un groupe ne travaille pas en comptant sur les autres"],
}, cat="Microéconomie : externalités, biens publics, information", src="sup-eco-micro",
    fwd="Quel problème économique illustre la situation suivante : {item} ?", rev="Laquelle de ces situations illustre « {group} » ?", diff=4)

split3("eco2-eco-q1", [
    ("Dans le circuit économique simple, que reçoivent les ménages en échange de leur travail ?", "Des revenus, notamment des salaires", ["Des machines", "Des impôts", "Des devises étrangères"], "Les entreprises versent des revenus qui financent la consommation.", 1),
    ("Pour un bien normal, que provoque une hausse du revenu des consommateurs sur la courbe de demande ?", "Elle la déplace vers la droite", ["Elle la déplace vers la gauche", "Elle la rend verticale", "Elle ne la modifie pas"], "À chaque prix, les consommateurs demandent davantage.", 2),
    ("Que provoque une hausse du coût des matières premières sur la courbe d'offre ?", "Elle la déplace vers la gauche", ["Elle la déplace vers la droite", "Elle la rend horizontale", "Elle la laisse inchangée"], "À chaque prix, les producteurs offrent moins.", 2),
    ("Que provoque une amélioration de la technologie de production sur la courbe d'offre ?", "Elle la déplace vers la droite", ["Elle la déplace vers la gauche", "Elle la rend verticale", "Elle la supprime"], "Les coûts baissent, l'offre augmente.", 2),
    ("Que provoque une hausse du prix d'un bien substitut sur la demande du bien considéré ?", "Elle la déplace vers la droite", ["Elle la déplace vers la gauche", "Elle la supprime", "Elle l'immobilise"], "Les consommateurs se reportent sur le bien considéré.", 2),
    ("Une variation du prix du bien lui-même provoque quel type de mouvement ?", "Un déplacement le long de la courbe de demande", ["Un déplacement de toute la courbe de demande", "Une hausse du revenu", "Un déplacement de la courbe d'offre"], "Seuls les autres facteurs déplacent la courbe.", 2),
    ("Que se passe-t-il si la demande dépasse l'offre au prix en vigueur ?", "Le prix tend à augmenter", ["Le prix tend à baisser", "Le prix reste inchangé nécessairement", "L'offre disparaît"], "La pénurie pousse le prix à la hausse jusqu'à l'équilibre.", 1),
    ("Qu'indique un point situé à l'intérieur de la frontière des possibilités de production ?", "Un sous-emploi ou une inefficacité dans l'usage des ressources", ["Un niveau de production inatteignable", "Une production maximale", "Un coût d'opportunité nul"], "On pourrait produire davantage avec les mêmes ressources.", 2),
    ("Qu'indique un point situé au-delà de la frontière des possibilités de production ?", "Un niveau de production inatteignable avec les ressources actuelles", ["Un niveau de production efficace", "Une inefficacité", "Un point d'équilibre"], "Les ressources ne le permettent pas.", 2),
    ("Lequel de ces éléments est un coût variable pour une boulangerie ?", "La farine consommée", ["Le loyer du local", "L'assurance annuelle du bâtiment", "L'amortissement du four"], "Les coûts variables dépendent du volume produit.", 1),
    ("Lequel de ces éléments est un coût fixe à court terme pour une boulangerie ?", "Le loyer du local", ["La farine consommée", "L'électricité consommée par les fours quand ils tournent", "Les emballages utilisés"], "Un coût fixe ne dépend pas du volume produit.", 2),
    ("Lequel de ces impôts est un impôt indirect ?", "La taxe sur la valeur ajoutée", ["L'impôt sur le revenu des personnes", "L'impôt sur les bénéfices des sociétés", "L'impôt sur le patrimoine"], "Il frappe la consommation et non le revenu.", 2),
    ("Lequel de ces impôts est un impôt direct ?", "L'impôt sur le revenu des personnes", ["La taxe sur la valeur ajoutée", "Un droit de douane", "Un droit d'accises"], "Il est prélevé sur le revenu du contribuable.", 2),
    ("Lequel de ces éléments relève de la monnaie scripturale ?", "Un solde disponible sur un compte courant", ["Un billet de banque", "Une pièce de monnaie", "Une lettre de change"], "Elle existe sous forme d'écritures dans les livres des banques.", 2),
    ("Qui accorde les crédits aux particuliers et aux entreprises ?", "Les banques commerciales", ["La banque centrale en priorité", "Le Parlement", "Le tribunal"], "Les banques commerciales financent l'économie par le crédit.", 1),
    ("Que fait en général l'inflation au pouvoir d'achat d'un revenu nominal inchangé ?", "Elle le réduit", ["Elle l'augmente", "Elle le laisse inchangé", "Elle le rend nul"], "Les prix montent alors que le revenu stagne.", 1),
    ("Dans quelle catégorie classe-t-on un étudiant à temps plein qui ne cherche pas d'emploi ?", "Les inactifs", ["Les chômeurs", "Les actifs occupés", "Les sous-employés"], "Il n'est pas en recherche active d'emploi.", 2),
    ("Quel auteur est associé à l'expression « main invisible » ?", "Adam Smith", ["Karl Marx", "John Maynard Keynes", "Léon Walras"], "Smith décrit comment la poursuite de l'intérêt personnel peut servir l'intérêt collectif.", 1),
    ("Quel auteur est associé à l'idée de lutte des classes dans l'analyse du capitalisme ?", "Karl Marx", ["Adam Smith", "David Ricardo", "Jean-Baptiste Say"], "Marx oppose les détenteurs du capital aux travailleurs.", 2),
    ("Quel auteur est associé à l'idée que l'État peut soutenir la demande en période de crise ?", "John Maynard Keynes", ["Jean-Baptiste Say", "Thomas Malthus", "Friedrich Hayek"], "Keynes défend une politique de relance contra-cyclique.", 2),
    ("Quel auteur craignait que la population croisse plus vite que les subsistances ?", "Thomas Malthus", ["Adam Smith", "Karl Marx", "Léon Walras"], "Son Essai sur le principe de population est célèbre.", 2),
    ("Que signifie la loi des débouchés de Jean-Baptiste Say ?", "L'offre crée sa propre demande", ["La demande crée sa propre offre", "Les prix sont toujours rigides", "L'État fixe la demande"], "Les produits s'échangent contre des produits.", 3),
    ("Qu'étudie la macroéconomie ?", "Les grands agrégats comme la production, l'emploi, les prix et la monnaie au niveau d'une économie", ["Le comportement d'un consommateur isolé", "La comptabilité d'une entreprise", "Le droit des contrats"], "Elle s'oppose à la microéconomie, centrée sur les décisions individuelles.", 1),
    ("Qu'étudie la microéconomie ?", "Les décisions des agents individuels et le fonctionnement des marchés particuliers", ["Le PIB d'un pays", "L'inflation générale", "Le solde de la balance des paiements"], "Consommateurs, entreprises et marchés en sont les objets.", 1),
    ("Quelle est la différence entre une analyse positive et une analyse normative ?", "L'analyse positive décrit ce qui est, la normative dit ce qui devrait être", ["L'analyse positive dit ce qui devrait être, la normative décrit ce qui est", "Il n'y a aucune différence", "L'une est mathématique, l'autre juridique"], "La positive énonce des faits vérifiables, la normative des jugements de valeur.", 3),
    ("Que signifie l'expression « toutes choses égales par ailleurs » (ceteris paribus) ?", "On étudie l'effet d'une variable en gardant les autres constantes", ["On suppose que tout varie ensemble", "On étudie seulement les prix", "On supprime les hypothèses"], "Cela permet d'isoler une relation causale.", 2),
    ("Que mesure un indice des prix à la consommation égal à 110 par rapport à une base 100 ?", "Une hausse de 10 % des prix du panier par rapport à la période de base", ["Une baisse de 10 % des prix", "Une hausse de 110 %", "Une hausse de 1,1 %"], "L'indice compare le panier à la période de référence.", 2),
    ("Quelle est la différence entre un stock et un flux ?", "Un stock se mesure à un instant, un flux sur une période", ["Un stock se mesure sur une période, un flux à un instant", "Il n'y a aucune différence", "Un stock est monétaire, un flux est réel"], "La dette est un stock, le déficit annuel est un flux.", 2),
    ("Dans quel cas parle-t-on de bien public pur ?", "Un bien non rival et non exclusif", ["Un bien vendu par l'État", "Un bien rival et exclusif", "Un bien importé"], "L'éclairage public en est un exemple.", 2),
], cat="Bases de l'économie", src="sup-eco-micro")

# =====================================================================================================================
# PETITS CALCULS À DONNÉES FICTIVES (chaque résultat est recalculé par une seconde méthode : fractions exactes)
# =====================================================================================================================
from fractions import Fraction as _F
from ..core import fr as _fr, NB as _NB


def _pc(x, dec=1):
    """Pourcentage à la française à partir d'un Fraction ou d'un nombre."""
    return _fr(float(x), dec) + _NB + "%"


def _nq(course, tpl, text, right, wrongs, expl, d, cat, src="sup-eco-macro"):
    ws = [w for w in dict.fromkeys(wrongs) if w != right]
    assert len(ws) >= 3, text
    fq(course, tpl, text, right, ws[:4], expl, src, "WORLD", cat, d)


# --- L1 : taux de chômage
for A, C in [(4_500_000, 500_000), (2_400_000, 600_000), (8_500_000, 1_500_000)]:
    r = _F(C, A + C) * 100
    assert abs(float(r) - (C * 100 / (A + C))) < 1e-9
    _nq(L1, "eco1-num-chom", "Dans un pays fictif, on compte %s actifs occupés et %s chômeurs. Quel est le taux de chômage ?" % (_fr(A), _fr(C)),
        _pc(r), [_pc(_F(C, A) * 100), _pc(_F(A, A + C) * 100), _pc(r + 5), _pc(r - 3 if r > 5 else r + 8)],
        "Taux de chômage = chômeurs / (actifs occupés + chômeurs) = %s / %s." % (_fr(C), _fr(A + C)), 2, "Macroéconomie : emploi et chômage")

# --- L1 : taux de croissance
for P0, P1 in [(2000, 2100), (5000, 5400), (1600, 1680)]:
    r = _F(P1 - P0, P0) * 100
    assert abs(float(r) - ((P1 / P0 - 1) * 100)) < 1e-9
    _nq(L1, "eco1-num-croiss", "Le PIB réel d'un pays fictif passe de %s à %s milliards de FCFA d'une année à la suivante. Quel est le taux de croissance ?" % (_fr(P0), _fr(P1)),
        _pc(r), [_pc(_F(P1 - P0, P1) * 100), _pc(_F(P1, P0) * 100), _pc(r + 2), _pc(r * 2)],
        "Taux de croissance = (PIB final − PIB initial) / PIB initial.", 2, "Macroéconomie : croissance")

# --- L1 : inflation à partir d'un indice des prix
for I0, I1 in [(100, 106), (125, 130), (200, 210)]:
    r = _F(I1 - I0, I0) * 100
    assert abs(float(r) - ((I1 / I0 - 1) * 100)) < 1e-9
    _nq(L1, "eco1-num-infl", "L'indice des prix à la consommation d'un pays fictif passe de %s à %s. Quel est le taux d'inflation sur la période ?" % (_fr(I0), _fr(I1)),
        _pc(r), [_pc(I1 - I0), _pc(_F(I1 - I0, I1) * 100), _pc(r + 2), _pc(_F(I1, I0) * 100)],
        "Inflation = (indice final − indice initial) / indice initial.", 2, "Macroéconomie : inflation")

# --- L1 : taux de couverture
for X, M in [(600, 800), (900, 600), (450, 500)]:
    r = _F(X, M) * 100
    assert abs(float(r) - (X / M * 100)) < 1e-9
    _nq(L1, "eco1-num-couv", "Un pays fictif exporte pour %s milliards de FCFA et importe pour %s milliards de FCFA. Quel est son taux de couverture ?" % (_fr(X), _fr(M)),
        _pc(r), [_pc(_F(M, X) * 100), _pc(_F(X, X + M) * 100), _pc(_F(X - M, M) * 100 if X != M else r + 3), _pc(r + 10)],
        "Taux de couverture = exportations / importations.", 2, "Commerce international")

# --- L1 / L2 : prix d'équilibre (offre et demande linéaires)
for course, (a, b, c, d), diff in [(L1, (100, 2, 10, 3), 2), (L1, (200, 4, 20, 5), 2), (L2, (90, 1, -10, 4), 3), (L2, (500, 10, 50, 20), 3)]:
    P = _F(a - c, b + d)
    assert P.denominator == 1 and a - b * P == c + d * P
    Q = a - b * P
    _nq(course, "eco%s-num-eq" % course[1], "La demande est Qd = %d − %dP et l'offre Qs = %s + %dP. Quel est le prix d'équilibre ?" % (a, b, str(c).replace("-", "−"), d),
        _fr(int(P)), [_fr(int(Q)), _fr((a + c) // (b + d)), _fr(int(P) + 5), _fr(int(P) - 4)],
        "On résout Qd = Qs : P = (a − c)/(b + d) = %s." % _fr(int(P)), diff, "Microéconomie : équilibre de marché", "sup-eco-micro")

# --- L2 : PIB par les dépenses
for C, I, G, X, M in [(600, 200, 150, 100, 120), (2000, 500, 600, 400, 300)]:
    Y = C + I + G + (X - M)
    _nq(L2, "eco2-num-pib", "Dans une économie ouverte fictive, C = %s, I = %s, G = %s, X = %s et M = %s (en milliards de FCFA). Quel est le PIB par l'optique des dépenses ?" % (_fr(C), _fr(I), _fr(G), _fr(X), _fr(M)),
        _fr(Y), [_fr(C + I + G + X + M), _fr(C + I + G), _fr(C + I + G + M - X), _fr(Y + 100)],
        "PIB = C + I + G + X − M.", 2, "Macroéconomie : comptabilité nationale")

# --- L2 : déflateur et PIB réel
for N, R in [(2400, 2000), (1800, 2000)]:
    r = _F(N, R) * 100
    assert abs(float(r) - (N / R * 100)) < 1e-9
    _nq(L2, "eco2-num-defl", "Le PIB nominal d'un pays fictif est de %s et son PIB réel (à prix constants) de %s. Quel est le déflateur du PIB (base 100) ?" % (_fr(N), _fr(R)),
        _fr(float(r), 1), [_fr(float(_F(R, N) * 100), 1), _fr(N - R), _fr(float(r) + 10, 1), _fr(float(r) - 10, 1)],
        "Déflateur = PIB nominal / PIB réel × 100.", 3, "Macroéconomie : comptabilité nationale")
for N, D in [(3300, 110), (4800, 120)]:
    R = _F(N * 100, D)
    assert R.denominator == 1
    _nq(L2, "eco2-num-pibreel", "Le PIB nominal est de %s milliards de FCFA et le déflateur du PIB de %s (base 100). Quel est le PIB réel ?" % (_fr(N), _fr(D)),
        _fr(int(R)), [_fr(N * D // 100), _fr(N - D), _fr(int(R) + 100), _fr(int(R) - 150)],
        "PIB réel = PIB nominal / déflateur × 100.", 3, "Macroéconomie : comptabilité nationale")

# --- L2 : multiplicateur keynésien simple
for c, dG in [(_F(3, 4), 100), (_F(4, 5), 50), (_F(9, 10), 20)]:
    k = 1 / (1 - c)
    dY = k * dG
    assert dY.denominator == 1
    _nq(L2, "eco2-num-mult", "Dans un modèle keynésien simple (ni impôts ni importations), la propension marginale à consommer vaut %s. Une hausse des dépenses publiques de %s milliards de FCFA accroît le revenu de combien ?" % (_fr(float(c), 2), _fr(dG)),
        _fr(int(dY)) + " milliards", [_fr(int(c * dG / (1 - c))) + " milliards", _fr(dG) + " milliards", _fr(int(dG / c)) + " milliards", _fr(int(dY) + dG) + " milliards"],
        "ΔY = ΔG/(1 − c) = %s." % _fr(int(dY)), 3, "Macroéconomie : multiplicateur")

# --- L2 : élasticité-prix
for dp, dq in [(5, -10), (4, -6), (10, -5)]:
    e = _F(dq, dp)
    assert abs(float(e) - (dq / dp)) < 1e-9
    _nq(L2, "eco2-num-elast", "Quand le prix d'un bien augmente de %d %%, la quantité demandée diminue de %d %%. Quelle est la valeur absolue de l'élasticité-prix de la demande ?" % (dp, -dq),
        _fr(abs(float(e)), 2), [_fr(abs(float(1 / e)), 2), _fr(abs(float(e)) + 1, 2), _fr(abs(float(dq - dp)), 2), _fr(abs(float(dq)), 2)],
        "|Élasticité| = variation relative de la quantité / variation relative du prix = %s." % _fr(abs(float(e)), 2), 3, "Microéconomie : élasticités", "sup-eco-micro")

# --- L2 : coût marginal
for q0, c0, c1 in [(10, 500, 530), (50, 2500, 2580)]:
    _nq(L2, "eco2-num-cmg", "Le coût total d'une firme est de %s FCFA pour %d unités et de %s FCFA pour %d unités. Quel est le coût marginal de la %de unité ?" % (_fr(c0), q0, _fr(c1), q0 + 1, q0 + 1),
        _fr(c1 - c0) + _NB + "FCFA", [_fr(c0 // q0) + _NB + "FCFA", _fr(c1) + _NB + "FCFA", _fr(c1 // (q0 + 1)) + _NB + "FCFA", _fr(c1 - c0 + 20) + _NB + "FCFA"],
        "Coût marginal = variation du coût total pour une unité de plus.", 2, "Microéconomie : coûts", "sup-eco-micro")

# --- L2 : seuil de rentabilité
for CF, p, cv in [(600_000, 1500, 900), (2_000_000, 2500, 1500)]:
    q = _F(CF, p - cv)
    assert q.denominator == 1 and q * p - q * cv - CF == 0
    _nq(L2, "eco2-num-seuil", "Une entreprise a des coûts fixes de %s FCFA, vend à %s FCFA l'unité et supporte un coût variable de %s FCFA par unité. Quel est son seuil de rentabilité en quantités ?" % (_fr(CF), _fr(p), _fr(cv)),
        _fr(int(q)) + " unités", [_fr(CF // p) + " unités", _fr(CF // cv) + " unités", _fr(CF // (p + cv)) + " unités", _fr(int(q) + 500) + " unités"],
        "Seuil = coûts fixes / (prix − coût variable unitaire).", 3, "Microéconomie : coûts", "sup-eco-micro")

# --- L2 : valeur actuelle
for FV, n, r in [(1_100_000, 1, _F(1, 10)), (1_440_000, 2, _F(1, 5))]:
    V = _F(FV) / (1 + r) ** n
    assert V.denominator == 1
    V = int(V)
    _nq(L2, "eco2-num-actu", "Quelle est la valeur actuelle d'une somme de %s FCFA reçue dans %d an%s, avec un taux d'actualisation de %s ?" % (_fr(FV), n, "s" if n > 1 else "", _pc(r * 100, 0)),
        _fr(V) + _NB + "FCFA", [_fr(int(FV * (1 - r))) + _NB + "FCFA", _fr(FV - int(FV * r * n)) + _NB + "FCFA", _fr(int(FV * (1 + r) ** n)) + _NB + "FCFA", _fr(V + 100_000) + _NB + "FCFA"],
        "Valeur actuelle = valeur future / (1 + taux)^n.", 3, "Finance : actualisation", "sup-eco-monnaie")

# --- L2 : multiplicateur de crédit simple
for D0, rr in [(2_000_000, _F(1, 10)), (4_000_000, _F(1, 4))]:
    tot = D0 / rr
    assert tot.denominator == 1
    tot = int(tot)
    # seconde méthode : somme de la série géométrique D0 (1 + (1-r) + (1-r)^2 + ...) tronquée très loin
    s = sum(D0 * (1 - rr) ** k for k in range(400))
    assert abs(float(s) - tot) < 1e-6 * tot
    _nq(L2, "eco2-num-credit", "Dans le modèle simple de création monétaire (réserves obligatoires de %s, aucune fuite en billets, aucune réserve excédentaire), un dépôt initial de %s FCFA permet au maximum un total de dépôts de combien ?" % (_pc(rr * 100, 0), _fr(D0)),
        _fr(tot) + _NB + "FCFA", [_fr(int(D0 * rr)) + _NB + "FCFA", _fr(int(D0 * (1 - rr))) + _NB + "FCFA", _fr(int(D0 / (1 - rr))) + _NB + "FCFA", _fr(tot + D0) + _NB + "FCFA"],
        "Total des dépôts = dépôt initial / taux de réserves.", 4, "Monnaie et banque", "sup-eco-monnaie")

# --- L3 : indice de Herfindahl-Hirschman
for shares in [(40, 30, 20, 10), (50, 30, 20), (60, 20, 10, 10)]:
    assert sum(shares) == 100
    h = sum(s * s for s in shares)
    _nq(L3, "eco3-num-hhi", "Sur un marché, les parts de marché des firmes sont de %s %%. Quel est l'indice de Herfindahl-Hirschman (parts en pourcentage) ?" % ", ".join(str(s) for s in shares),
        _fr(h), [_fr(100), _fr(h // 100), _fr(max(shares) ** 2), _fr(h - min(shares) ** 2)],
        "HHI = somme des carrés des parts de marché.", 4, "Microéconomie : concentration", "sup-eco-micro")

# --- L3 : indice de Lerner
for P, Cm in [(100, 80), (200, 150)]:
    L = _F(P - Cm, P)
    assert abs(float(L) - ((P - Cm) / P)) < 1e-9
    _nq(L3, "eco3-num-lerner", "Une firme vend à %s FCFA, son coût marginal étant de %s FCFA. Quel est son indice de Lerner ?" % (_fr(P), _fr(Cm)),
        _fr(float(L), 2), [_fr(float(_F(P - Cm, Cm)), 2), _fr(float(_F(Cm, P)), 2), _fr(P - Cm), _fr(float(L) + 0.1, 2)],
        "Indice de Lerner = (prix − coût marginal) / prix.", 4, "Microéconomie : pouvoir de marché", "sup-eco-micro")

# --- L3 : monopole à demande linéaire (vérifié par recherche du maximum)
for a, b, c in [(100, 2, 20), (80, 1, 20), (120, 3, 30)]:
    Qm = _F(a - c, 2 * b)
    assert Qm.denominator == 1
    Qm = int(Qm)
    best = max(range(0, a // b + 1), key=lambda q: (a - b * q - c) * q)
    assert best == Qm
    Pm = a - b * Qm
    assert Pm == (a + c) // 2
    _nq(L3, "eco3-num-mono", "Un monopole fait face à la demande inverse P = %d − %dQ et a un coût marginal constant de %d. Quel prix maximise son profit ?" % (a, b, c),
        _fr(Pm), [_fr(c), _fr((a - c) // b), _fr(Pm + 10), _fr(Pm - 10)],
        "Recette marginale = a − 2bQ égale au coût marginal : Q = %d, d'où P = %d." % (Qm, Pm), 4, "Microéconomie : monopole", "sup-eco-micro")

# --- L3 : duopole de Cournot symétrique
for a, b, c in [(100, 1, 10), (120, 2, 30)]:
    q = _F(a - c, 3 * b)
    assert q.denominator == 1
    q = int(q)
    # seconde méthode : itération des fonctions de réaction q_i = (a - c - b q_j)/(2b)
    x = _F(0)
    y = _F(0)
    for _ in range(200):
        x, y = (a - c - b * y) / (2 * b), (a - c - b * x) / (2 * b)
    assert abs(float(x) - q) < 1e-9 and abs(float(y) - q) < 1e-9
    P = a - b * 2 * q
    _nq(L3, "eco3-num-cournot", "Deux firmes identiques se font concurrence en quantités (Cournot) avec la demande inverse P = %d − %dQ et un coût marginal de %d. Quelle quantité produit chaque firme ?" % (a, b, c),
        _fr(q), [_fr((a - c) // (2 * b)), _fr((a - c) // b), _fr(q + 5), _fr((a - c) // (4 * b))],
        "Chaque firme produit (a − c)/(3b) = %d ; le prix d'équilibre est %d." % (q, P), 5, "Microéconomie : oligopole", "sup-eco-micro")

# --- L3 : capital par tête à l'état régulier (Solow, y = racine de k)
for s, nd in [(_F(1, 5), _F(1, 10)), (_F(3, 10), _F(1, 10)), (_F(1, 4), _F(1, 20))]:
    k = (s / nd) ** 2
    assert k.denominator == 1
    x = 1.0
    for _ in range(20000):
        x = x + float(s) * x ** 0.5 - float(nd) * x
    assert abs(x - float(k)) < 1e-6
    k = int(k)
    _nq(L3, "eco3-num-solow", "Dans un modèle de Solow sans progrès technique, la production par tête est y = √k, le taux d'épargne vaut %s et le taux (croissance de la population + dépréciation) vaut %s. Quel est le capital par tête à l'état régulier ?" % (_pc(s * 100, 0), _pc(nd * 100, 0)),
        _fr(k), [_fr(int(s / nd)), _fr(int((s / nd) ** 3)) if (s / nd) ** 3 != k else _fr(k + 7), _fr(k + 5), _fr(max(1, k - 3))],
        "À l'état régulier, s·√k = (n + δ)·k, donc √k = s/(n + δ) et k = (s/(n + δ))².", 5, "Macroéconomie : croissance")

# --- L3 : multiplicateur avec importations
for c, m in [(_F(4, 5), _F(1, 5)), (_F(9, 10), _F(1, 10)), (_F(3, 5), _F(2, 5))]:
    k = 1 / (1 - c + m)
    assert abs(float(k) - (1 / (1 - float(c) + float(m)))) < 1e-9
    _nq(L3, "eco3-num-multopen", "Dans un modèle keynésien d'économie ouverte sans impôts, la propension marginale à consommer est %s et la propension marginale à importer %s. Quel est le multiplicateur des dépenses ?" % (_fr(float(c), 1), _fr(float(m), 1)),
        _fr(float(k), 2), [_fr(float(1 / (1 - c)), 2), _fr(float(1 / (1 + m)), 2), _fr(float(1 / (c + m)), 2), _fr(float(k) + 1, 2)],
        "k = 1/(1 − c + m) : une partie de la dépense s'échappe vers l'étranger.", 4, "Macroéconomie : multiplicateur")

# --- L3 : multiplicateur du budget équilibré (impôts forfaitaires)
for c, X in [(_F(4, 5), 100), (_F(3, 4), 200)]:
    k = 1 / (1 - c)
    dY = k * X - c * k * X
    assert dY == X
    _nq(L3, "eco3-num-haavelmo", "Dans un modèle keynésien simple avec impôts forfaitaires et une propension marginale à consommer de %s, l'État accroît ses dépenses et ses impôts de %s milliards de FCFA chacun. De combien varie le revenu ?" % (_fr(float(c), 2), _fr(X)),
        _fr(X) + " milliards", [_fr(int(k * X)) + " milliards", _fr(int(c * k * X)) + " milliards", _fr(0) + " milliard", _fr(int(k * X + c * k * X)) + " milliards"],
        "ΔY = ΔG/(1 − c) − c·ΔT/(1 − c) = ΔG quand ΔT = ΔG : le multiplicateur du budget équilibré vaut 1.", 5, "Macroéconomie : multiplicateur")

# --- L3 : solde courant = épargne − investissement
for S, I in [(300, 250), (180, 220)]:
    ca = S - I
    lab = lambda v: ("Un excédent de %s" % _fr(v)) if v > 0 else ("Un déficit de %s" % _fr(-v))
    _nq(L3, "eco3-num-courant", "Une économie ouverte fictive a une épargne nationale de %s et un investissement de %s milliards de FCFA. Quel est son solde courant ?" % (_fr(S), _fr(I)),
        lab(ca) + " milliards", [lab(-ca) + " milliards", "Un solde nul", lab(S + I) + " milliards", lab(ca * 2) + " milliards"],
        "Solde courant = épargne nationale − investissement.", 4, "Macroéconomie : balance des paiements")

# --- L3 : prix payé par les acheteurs après une taxe unitaire sur les vendeurs
for a, b, d, t in [(100, 2, 3, 5), (120, 3, 5, 8), (150, 2, 3, 5)]:
    P1 = _F(a + d * t, b + d)
    P0 = _F(a, b + d)
    assert P1.denominator == 1 and P0.denominator == 1
    Qd = a - b * P1
    Qs = d * (P1 - t)
    assert Qd == Qs
    _nq(L3, "eco3-num-taxe", "La demande est Qd = %d − %dP et l'offre Qs = %dP. Une taxe de %d FCFA par unité est prélevée sur les vendeurs. Quel est le nouveau prix payé par les acheteurs ?" % (a, b, d, t),
        _fr(int(P1)), [_fr(int(P0)), _fr(int(P0) + t), _fr(int(P1) - t), _fr(int(P1) + 3)],
        "L'offre devient Qs = %d(P − %d) ; l'égalité avec la demande donne P = %d (avant taxe : %d)." % (d, t, int(P1), int(P0)), 4, "Microéconomie : incidence d'une taxe", "sup-eco-micro")

# --- L3 : rendements d'échelle d'une fonction Cobb-Douglas
for al, be in [(_F(3, 10), _F(7, 10)), (_F(1, 2), _F(2, 5)), (_F(3, 5), _F(3, 5))]:
    tot = al + be
    lam = 2.0
    ratio = (lam ** float(al)) * (lam ** float(be))
    label = "Constants" if tot == 1 else ("Croissants" if tot > 1 else "Décroissants")
    assert (abs(ratio - 2) < 1e-9) == (label == "Constants") and (ratio > 2 + 1e-9) == (label == "Croissants")
    _nq(L3, "eco3-num-rend", "Quels sont les rendements d'échelle d'une fonction de production Y = K^%s · L^%s ?" % (_fr(float(al), 1), _fr(float(be), 1)),
        label, [x for x in ("Constants", "Croissants", "Décroissants", "Nuls") if x != label],
        "La somme des exposants vaut %s : %s." % (_fr(float(tot), 1), "doubler les facteurs " + ("double" if tot == 1 else "plus que double" if tot > 1 else "moins que double") + " la production"), 4, "Microéconomie : producteur", "sup-eco-micro")

# =====================================================================================================================
# L3 (complément) : ÉCONOMÉTRIE CONCEPTUELLE, ÉCONOMIE PUBLIQUE, FINANCE INTERNATIONALE, DÉVELOPPEMENT, MACRO AVANCÉE
# (réponses de longueurs voisines : la bonne n'est pas systématiquement la plus longue)
# =====================================================================================================================
mcq(L3, "eco3-econometrie", [
    ("Que minimise la méthode des moindres carrés ordinaires ?", "La somme des carrés des résidus de la régression", ["La somme des valeurs absolues de la variable expliquée", "Le carré de la somme des erreurs de mesure avant tout ajustement", "La corrélation entre les variables explicatives"], "Elle choisit la droite qui rend les écarts quadratiques les plus petits.", 3),
    ("Que mesure le coefficient de détermination R² d'une régression ?", "La part de la variance de la variable expliquée rendue par le modèle", ["La probabilité que le modèle soit exactement vrai sur toute la population", "Le nombre de variables explicatives significatives retenues dans le modèle", "L'écart moyen entre la prévision et la valeur observée"], "Il varie entre 0 et 1 dans un modèle avec constante.", 3),
    ("Qu'est-ce que l'endogénéité d'un régresseur ?", "Sa corrélation avec le terme d'erreur du modèle", ["Sa dépendance à des variables mesurées à l'étranger", "Le fait qu'il soit toujours exprimé en logarithme dans l'équation", "Sa variance trop faible dans l'échantillon étudié"], "Elle biaise l'estimateur des moindres carrés.", 4),
    ("Quelles conditions doit remplir une variable instrumentale valide ?", "Être corrélée au régresseur endogène sans l'être avec l'erreur", ["Être fortement corrélée avec l'erreur et indépendante du régresseur", "Avoir une variance plus grande que celle de la variable expliquée", "Être mesurée sans aucune erreur et dans la même unité"], "Pertinence et exogénéité sont les deux conditions.", 5),
    ("Que provoque l'omission d'une variable pertinente corrélée avec un régresseur inclus ?", "Un biais de l'estimateur du coefficient du régresseur inclus", ["Une variance nulle des résidus dans l'échantillon", "Une baisse automatique de la valeur du coefficient R²", "Aucun problème, dès que l'échantillon est de grande taille et bien mélangé"], "Le coefficient capte en partie l'effet de la variable omise.", 4),
    ("Qu'est-ce que l'hétéroscédasticité ?", "Une variance des erreurs qui n'est pas constante entre observations", ["Une corrélation entre erreurs de deux dates successives", "Une relation non linéaire entre la variable expliquée et un régresseur", "Une forte corrélation entre deux variables explicatives"], "Elle rend faux les écarts-types usuels sans biaiser forcément les coefficients.", 4),
    ("Qu'est-ce que l'autocorrélation des erreurs ?", "Une corrélation entre les erreurs de périodes différentes", ["Une variance des erreurs différente selon les individus ou les périodes étudiées", "Une corrélation entre régresseurs de même période", "Une erreur de mesure sur la variable expliquée"], "On la rencontre surtout avec des séries temporelles.", 4),
    ("Quel est l'effet principal d'une forte multicolinéarité entre régresseurs ?", "Des écarts-types élevés pour les coefficients concernés", ["Un biais systématique de tous les coefficients du modèle", "Une impossibilité de calculer le coefficient R²", "Des résidus toujours de même signe pour les observations"], "On sépare mal l'effet de chaque variable.", 4),
    ("Que signifie qu'un estimateur est sans biais ?", "Son espérance est égale à la vraie valeur du paramètre", ["Sa variance est la plus faible de tous les estimateurs possibles", "Sa valeur est la même sur tous les échantillons tirés", "Il converge vers zéro quand l'échantillon augmente"], "Il ne se trompe pas en moyenne.", 3),
    ("Que signifie qu'un estimateur est convergent ?", "Il tend vers la vraie valeur quand la taille de l'échantillon augmente", ["Il est exact pour n'importe quel échantillon de petite taille", "Il est égal à la moyenne des observations en toutes circonstances et pour tout n", "Il est toujours positif quel que soit le paramètre"], "Il s'agit d'une propriété asymptotique.", 4),
    ("Que dit le théorème de Gauss-Markov ?", "Sous ses hypothèses, les MCO sont le meilleur estimateur linéaire sans biais", ["Les MCO sont toujours le meilleur estimateur, même avec endogénéité", "Les erreurs d'un modèle linéaire suivent obligatoirement une loi normale centrée réduite", "Tout estimateur sans biais est aussi un estimateur convergent"], "On dit que les MCO sont BLUE.", 5),
    ("Comment définit-on la p-value d'un test ?", "La probabilité d'un résultat au moins aussi extrême sous l'hypothèse nulle", ["La probabilité que l'hypothèse nulle soit vraie après la réalisation du test et des calculs", "La probabilité que l'hypothèse alternative soit fausse", "Le seuil de risque choisi avant de regarder les données"], "Ce n'est pas la probabilité que H0 soit vraie.", 5),
    ("Qu'est-ce qu'une erreur de première espèce ?", "Rejeter l'hypothèse nulle alors qu'elle est vraie", ["Ne pas rejeter l'hypothèse nulle alors qu'elle est fausse", "Accepter une hypothèse alternative sans calculer la statistique", "Estimer un coefficient avec un signe contraire à la théorie"], "Son risque est le niveau du test.", 4),
    ("Qu'est-ce qu'une erreur de seconde espèce ?", "Ne pas rejeter l'hypothèse nulle alors qu'elle est fausse", ["Rejeter l'hypothèse nulle alors qu'elle est vraie", "Utiliser un échantillon trop grand pour le test", "Rejeter une hypothèse alternative vraie en la formulant mal"], "Elle est liée à la puissance du test.", 4),
    ("Dans une régression log-log, que représente le coefficient d'un régresseur ?", "Une élasticité de la variable expliquée par rapport à ce régresseur", ["La variation absolue de la variable expliquée pour une unité de plus du régresseur", "Le taux de croissance moyen de la variable expliquée", "La part de variance expliquée par le régresseur"], "Il lie des variations en pourcentage.", 4),
    ("Qu'est-ce qu'une variable muette (indicatrice) ?", "Une variable qui vaut 1 si une caractéristique est présente et 0 sinon", ["Une variable dont la valeur est inconnue pour toutes les observations de l'échantillon", "Une variable sans effet statistique sur la variable expliquée", "Une variable instrumentale non corrélée à l'erreur"], "Elle sert à intégrer des catégories dans une régression.", 3),
    ("Qu'est-ce qu'une donnée de panel ?", "Des observations répétées sur les mêmes individus à plusieurs dates", ["Des observations de plusieurs individus à une seule date", "Une seule série chronologique d'un indicateur agrégé", "Un échantillon tiré au hasard à chaque période"], "Elle combine dimensions individuelle et temporelle.", 3),
    ("Que contrôle un modèle à effets fixes individuels sur données de panel ?", "Les caractéristiques inobservées de chaque individu qui ne varient pas dans le temps", ["Les chocs communs à tous les individus à chaque date, qui varient dans le temps seulement", "Les erreurs de mesure sur les variables explicatives", "Les variables qui changent chaque année pour tous"], "Il élimine un biais d'hétérogénéité fixe.", 5),
    ("Qu'est-ce qu'une série temporelle stationnaire ?", "Une série dont moyenne, variance et autocovariances sont stables dans le temps", ["Une série qui croît à un rythme constant chaque année", "Une série qui prend toujours la même valeur à chaque date d'observation", "Une série dont toutes les observations sont indépendantes"], "On parle de stationnarité au sens faible.", 4),
    ("Que décrit une régression fallacieuse (Granger et Newbold) ?", "Une forte relation apparente entre séries non stationnaires sans lien réel", ["Une régression estimée sur un trop petit nombre de variables et d'observations", "Une relation vraie mais masquée par des variables muettes", "Une régression dont le R² est exactement nul"], "Elle apparaît souvent avec des tendances stochastiques.", 5),
    ("À quoi sert le test de Dickey-Fuller ?", "À tester la présence d'une racine unitaire dans une série", ["À tester l'égalité des variances de deux échantillons indépendants et de même taille", "À tester la normalité des résidus d'une régression", "À tester l'exogénéité d'une variable instrumentale"], "Il aide à décider si une série est stationnaire.", 5),
    ("Qu'est-ce que la cointégration ?", "Une combinaison stable de séries non stationnaires, qui est elle-même stationnaire", ["Une corrélation entre deux séries stationnaires de même moyenne et de même variance", "Une régression dont les erreurs sont nulles", "Une série dont la tendance est parfaitement linéaire"], "Elle formalise une relation d'équilibre de long terme.", 5),
    ("Que signifie la causalité au sens de Granger ?", "Les valeurs passées de X aident à prévoir Y mieux que les seules valeurs passées de Y", ["X est la cause physique de Y, démontrée par une expérience contrôlée et reproductible", "X et Y sont corrélés à la même date", "Y n'a aucune influence sur X à aucune date"], "C'est une notion de prévision, non de cause au sens strict.", 5),
    ("Pourquoi l'assignation aléatoire d'un traitement facilite-t-elle l'inférence causale ?", "Elle rend les groupes comparables en moyenne avant le traitement", ["Elle garantit que l'effet du traitement est positif pour tous les individus traités", "Elle supprime toute erreur de mesure du résultat", "Elle rend inutile l'échantillon de grande taille"], "Elle élimine le biais de sélection en moyenne.", 4),
    ("Qu'est-ce que le biais de sélection ?", "Une différence entre groupes comparés qui existe avant le traitement étudié", ["Une erreur de calcul de l'écart-type des coefficients", "Le fait de retenir une variable expliquée trop volatile pour l'étude du phénomène", "Une corrélation entre deux régresseurs inclus"], "Les individus qui reçoivent un traitement diffèrent souvent des autres.", 4),
    ("Que compare la méthode des doubles différences ?", "L'évolution d'un groupe traité à celle d'un groupe témoin avant et après", ["Les moyennes de deux groupes à une seule date", "Les variances de deux estimateurs différents calculés sur un même échantillon", "Les résidus de deux régressions à effets fixes"], "Elle suppose des tendances parallèles en l'absence de traitement.", 5),
    ("Qu'exploite la régression par discontinuité ?", "Un seuil qui change l'accès à un traitement pour des individus proches", ["Un échantillon tiré avec des probabilités égales dans la population étudiée", "Une rupture de tendance dans une série agrégée", "Un instrument sans lien avec le traitement"], "Les individus juste au-dessus et juste en dessous sont comparables.", 5),
    ("À quoi servent les modèles logit et probit ?", "À expliquer une variable dépendante binaire (0 ou 1)", ["À modéliser une variable continue sans borne", "À estimer des séries chronologiques non stationnaires", "À corriger l'hétéroscédasticité d'un modèle linéaire"], "Ils modélisent une probabilité.", 4),
    ("Que signifie l'expression « corrélation n'est pas causalité » ?", "Une association statistique ne prouve pas qu'une variable en cause une autre", ["Deux variables corrélées n'ont jamais de lien économique ni de relation causale", "La causalité implique toujours une corrélation nulle", "Les corrélations sont toujours dues au hasard"], "Une variable tierce ou une causalité inverse peut expliquer le lien.", 3),
    ("Qu'est-ce que le problème d'identification d'une équation d'offre et de demande ?", "Les prix et quantités observés résultent de l'intersection des deux courbes", ["Les prix observés sont toujours fixés par l'État", "Les quantités sont toujours mesurées avec erreur", "Chaque équation a les mêmes coefficients par construction du modèle, sans exception"], "On ne sait pas distinguer une courbe de l'autre sans variable supplémentaire.", 5),
    ("Que dit la loi des grands nombres ?", "La moyenne d'un échantillon se rapproche de l'espérance quand n augmente", ["La moyenne d'un petit échantillon est toujours égale à l'espérance de la population", "Les observations suivent toutes une loi normale", "La variance d'un échantillon est toujours nulle"], "Elle fonde la convergence de nombreux estimateurs.", 4),
], cat="Économétrie conceptuelle", src="sup-eco-micro")

mcq(L3, "eco3-pub", [
    ("Qu'est-ce qu'un impôt forfaitaire (lump-sum) idéal en théorie ?", "Un impôt qui ne dépend d'aucun choix de l'individu", ["Un impôt dont le taux augmente avec le revenu déclaré", "Un impôt perçu uniquement sur les produits importés", "Un impôt dont le produit est affecté à une dépense précise"], "Il ne modifie pas les décisions, donc ne crée pas de distorsion.", 4),
    ("Qu'appelle-t-on charge excédentaire (perte sèche) d'un impôt ?", "La perte de bien-être supérieure aux recettes, due aux distorsions des choix", ["Le coût administratif de perception des recettes", "La part des recettes publiques affectée aux dépenses militaires de l'État", "La baisse des recettes causée par la fraude"], "Elle apparaît quand l'impôt modifie les comportements.", 4),
    ("Que préconise la règle de Ramsey pour la taxation de plusieurs biens ?", "Des taxes plus élevées sur les biens dont la demande est peu élastique", ["Des taxes identiques pour tous les biens quelle que soit leur demande", "Des taxes plus élevées sur les biens dont la demande est très élastique", "Des taxes nulles sur tous les biens de première nécessité sans exception"], "Cela minimise la distorsion pour un niveau de recettes donné.", 5),
    ("Quelle est la différence entre un impôt progressif et un impôt proportionnel ?", "Le taux moyen croît avec le revenu dans le progressif et reste constant dans le proportionnel", ["Le taux moyen décroît avec le revenu dans le progressif", "Le progressif ne s'applique qu'aux entreprises", "Le proportionnel varie chaque année selon le budget"], "Le taux marginal excède alors le taux moyen dans le premier cas.", 3),
    ("Qu'est-ce qu'un impôt régressif ?", "Un impôt dont le poids relatif diminue quand le revenu augmente", ["Un impôt dont le poids relatif augmente avec le revenu", "Un impôt dont le taux diminue chaque année par décision du Parlement ou de l'exécutif", "Un impôt prélevé seulement sur les revenus du capital"], "Certaines taxes à la consommation ont cet effet.", 3),
    ("Quelle différence y a-t-il entre incidence légale et incidence économique d'une taxe ?", "La première désigne qui paie à l'administration, la seconde qui supporte réellement la charge", ["La première désigne qui supporte réellement la charge, la seconde qui paie à l'État", "Chacune désigne toujours le même agent dans tous les cas", "La première concerne la TVA et les taxes indirectes, la seconde l'impôt sur le revenu"], "L'incidence économique dépend des élasticités.", 4),
    ("Que propose Rawls avec le voile d'ignorance ?", "Choisir les règles de justice sans connaître sa propre position sociale", ["Choisir les règles après avoir connu sa position dans la société", "Laisser le marché fixer seul toutes les inégalités", "Maximiser le revenu moyen de la société quelle que soit sa répartition entre individus"], "Il conduit au critère du maximin.", 4),
    ("Que prescrit le critère du maximin de Rawls ?", "Améliorer au maximum la situation des plus défavorisés", ["Maximiser la somme des utilités de tous les individus", "Égaliser strictement les revenus de tous", "Maximiser la richesse des plus riches pour qu'elle ruisselle"], "Il juge une situation à celle du moins bien loti.", 4),
    ("Que prescrit le critère utilitariste classique ?", "Maximiser la somme des utilités individuelles", ["Maximiser l'utilité du plus défavorisé seulement", "Rendre toutes les utilités égales", "Interdire tout changement qui fait des perdants"], "Il est associé à Bentham.", 3),
    ("Que dit le principe de subsidiarité en économie publique ?", "Les décisions doivent être prises au niveau le plus proche des citoyens, quand c'est possible", ["Toutes les décisions doivent revenir à l'État central", "Les décisions locales doivent toujours être ratifiées par le niveau le plus élevé du pouvoir central", "Seul le secteur privé peut décider des services publics"], "Il est proche de l'idée de décentralisation.", 4),
    ("Quel principe porte le théorème de décentralisation d'Oates ?", "Un bien public local doit être fourni au niveau de gouvernement qui correspond à son étendue", ["Tout bien public doit être financé par l'État central, quelle que soit sa zone d'utilité", "Les biens publics locaux doivent être vendus au privé", "Les collectivités ne doivent produire aucun bien public"], "Cela évite l'uniformité inadaptée aux préférences locales.", 5),
    ("Que décrit le modèle de Tiebout ?", "Les ménages choisissent leur commune selon les services publics et les impôts qu'elle offre", ["Les ménages ne se déplacent jamais entre communes", "Les communes fixent les mêmes impôts partout", "Les ménages votent toujours à l'unanimité"], "On parle de « vote avec les pieds ».", 5),
    ("Qu'est-ce que la capture du régulateur (Stigler) ?", "Le régulateur finit par servir les intérêts du secteur qu'il contrôle", ["Le régulateur est révoqué à chaque changement de gouvernement, sans exception", "Le régulateur fixe les prix sans consulter les entreprises", "Le régulateur manque de fonds pour contrôler quoi que ce soit"], "Elle explique certains échecs de la régulation.", 5),
    ("Que décrit le concept de recherche de rente (rent-seeking) ?", "Des ressources dépensées pour obtenir un avantage sans créer de richesse", ["Des investissements productifs financés par l'impôt et gérés par l'administration", "Des loyers payés pour des terres agricoles", "Des revenus tirés du travail qualifié"], "Lobbying et licences en sont des exemples.", 4),
    ("Dans la théorie du choix public, comment décrit-on les responsables politiques ?", "Comme des agents poursuivant leurs propres objectifs, comme les autres", ["Comme des agents toujours désintéressés", "Comme des agents sans aucune influence sur les politiques publiques décidées", "Comme des agents qui ignorent les électeurs"], "Buchanan et Tullock sont associés à ce courant.", 4),
    ("Quel est le problème de la tarification au coût marginal d'un monopole naturel ?", "Elle laisse un déficit, car le coût marginal est inférieur au coût moyen", ["Elle génère un profit supérieur à celui d'un monopole libre et sans régulation", "Elle suppose des coûts moyens croissants", "Elle impose un prix supérieur au prix de monopole"], "Une subvention ou un tarif en deux parties peut combler l'écart.", 5),
    ("Qu'est-ce qu'un tarif binôme ?", "Une redevance fixe à laquelle s'ajoute un prix par unité consommée", ["Un prix identique pour tous les consommateurs, sans redevance fixe", "Un prix doublé en période de pointe seulement", "Une subvention versée en deux tranches"], "Il aide à couvrir les coûts fixes d'un monopole régulé.", 4),
    ("Qu'est-ce qu'un bien tutélaire (merit good) ?", "Un bien que l'autorité juge bénéfique et dont elle encourage la consommation", ["Un bien produit uniquement par l'État", "Un bien dont l'accès est interdit aux mineurs", "Un bien non rival fourni gratuitement par le marché aux ménages à faible revenu"], "L'éducation en est souvent citée.", 4),
    ("Que sont les prix de Lindahl ?", "Des prix personnalisés reflétant la valeur qu'attache chaque individu à un bien public", ["Des prix uniques fixés par l'État pour tous", "Des prix nuls pour un bien public", "Des prix négociés en monopole bilatéral"], "Ils servent de référence théorique pour financer un bien public.", 5),
    ("Quel arbitrage domine l'analyse de la fiscalité optimale (Mirrlees) ?", "L'arbitrage entre équité et efficacité", ["L'arbitrage entre salaire et profit", "L'arbitrage entre exportations et importations", "L'arbitrage entre épargne et dépenses militaires"], "Redistribuer par l'impôt décourage parfois l'effort.", 5),
    ("Qu'est-ce qu'un impôt négatif sur le revenu (Friedman) ?", "Un transfert versé aux ménages dont le revenu est inférieur à un seuil", ["Un impôt prélevé uniquement sur les revenus supérieurs à un certain seuil", "Un impôt qui diminue chaque année", "Un impôt versé aux entreprises déficitaires uniquement"], "Il combine impôt et prestation sociale.", 4),
    ("Que dit le critère coût-bénéfice appliqué à un projet public ?", "Retenir le projet dont les bénéfices actualisés dépassent les coûts actualisés", ["Retenir le projet le moins coûteux sans regarder ses effets sur le bien-être, ni sur le temps", "Retenir le projet qui emploie le plus de fonctionnaires", "Retenir le projet financé par un emprunt extérieur"], "Il suppose un taux d'actualisation social.", 3),
    ("Que dit la loi de Wagner à propos des dépenses publiques ?", "Leur part dans le revenu national tend à croître avec le développement", ["Leur part tend à décroître avec le développement", "Elles sont toujours égales à un quart du PIB", "Elles ne dépendent pas du niveau de développement ni du revenu moyen du pays"], "Elle est discutée empiriquement.", 5),
    ("Qu'est-ce que la règle d'or des finances publiques ?", "N'emprunter que pour financer des dépenses d'investissement", ["Ne jamais dépenser plus que les recettes de l'année, même pour investir", "Emprunter toujours au taux le plus bas possible", "Équilibrer le budget de chaque ministère séparément"], "Elle réserve la dette à ce qui profite aux générations futures.", 4),
], cat="Économie publique", src="sup-eco-micro")

mcq(L3, "eco3-fininter", [
    ("Que dit la parité des taux d'intérêt couverte ?", "L'écart de taux entre deux pays est compensé par la prime ou décote du taux de change à terme", ["L'écart de taux est toujours égal à la dépréciation observée", "Les taux d'intérêt sont identiques dans tous les pays, par arbitrage de la banque centrale", "Le taux à terme est toujours égal au taux au comptant"], "L'arbitrage sans risque annule les gains.", 5),
    ("Que décrit un modèle de crise de change de première génération (Krugman) ?", "Un déficit financé par création monétaire épuise les réserves et déclenche une attaque", ["Une attaque sans lien avec les fondamentaux du pays", "Une crise causée par un excès de réserves de change", "Une crise provoquée par un excédent budgétaire persistant et une accumulation de réserves"], "La parité devient intenable à cause d'une politique incohérente.", 5),
    ("Que décrit le « péché originel » (Eichengreen, Hausmann) ?", "L'incapacité d'un pays à emprunter à l'étranger dans sa propre monnaie", ["L'excès de réserves de change détenues par la banque centrale d'un pays émergent", "L'incapacité à lever des impôts sur les exportations", "L'interdiction pour les banques de prêter en devises"], "Il expose les débiteurs au risque de change.", 5),
    ("Qu'est-ce qu'un arrêt brutal des flux de capitaux (sudden stop) ?", "Une chute soudaine des entrées de capitaux étrangers vers un pays", ["Une hausse soudaine des exportations de matières premières vers les pays voisins", "Une interruption de la production d'une usine", "Un gel des prix décidé par l'État"], "Il contraint à un ajustement brutal du solde courant.", 4),
    ("Que dit la théorie de la zone monétaire optimale de Mundell ?", "Une union fonctionne mieux si le travail est mobile, les prix flexibles et les chocs symétriques", ["Une union fonctionne mieux si les pays ont des chocs très différents et des cycles opposés", "Une union exige seulement le même taux de TVA partout", "Une union est efficace sans aucune mobilité des facteurs"], "Ces critères réduisent le coût de la perte du change.", 5),
    ("Qu'est-ce que la dollarisation ?", "L'adoption d'une devise étrangère comme monnaie nationale", ["La hausse des importations venant des États-Unis", "La dévaluation du dollar contre toutes les devises décidée par le FMI", "L'interdiction des devises étrangères"], "Le pays perd sa politique monétaire propre.", 3),
    ("Que propose la taxe Tobin ?", "Taxer les transactions de change pour freiner la spéculation de court terme", ["Taxer les exportations de matières premières", "Taxer les revenus du travail des non-résidents pour financer les dépenses publiques", "Taxer les dépôts bancaires des ménages"], "Elle viserait à « mettre du sable dans les rouages » de la finance.", 4),
    ("Que décrit l'effet Balassa-Samuelson ?", "Une productivité croissante dans les biens échangeables appréciant le taux de change réel", ["Une baisse de la productivité des biens échangeables qui déprécie le taux de change nominal", "Une dévaluation qui améliore automatiquement la balance commerciale", "Une hausse des exportations liée à l'inflation importée"], "Les prix des biens non échangeables montent avec les salaires.", 5),
    ("Que décrit l'effet de surréaction (overshooting) de Dornbusch ?", "Le taux de change dépasse son niveau de long terme après un choc monétaire, du fait des prix rigides", ["Le taux de change reste fixe après un choc monétaire", "Les prix s'ajustent plus vite que le taux de change", "Le taux de change baisse après une baisse des taux"], "Il retourne ensuite vers son niveau d'équilibre.", 5),
    ("Que décrit le dilemme de Triffin ?", "L'émetteur d'une monnaie de réserve doit fournir des liquidités mais sape ainsi la confiance", ["Un pays ne peut avoir à la fois inflation et chômage", "Un pays doit choisir entre commerce et finance, car les deux ne peuvent croître ensemble", "Un pays ne peut émettre de monnaie sans l'accord de l'OMC"], "Il est associé au système de Bretton Woods.", 5),
    ("Que décrit le mécanisme d'ajustement de Hume (price-specie flow) ?", "Un excédent commercial attire de l'or, fait monter les prix et réduit l'excédent", ["Un excédent commercial fait baisser durablement les prix", "Un déficit commercial attire de l'or, fait monter les prix et accroît l'excédent", "Les prix sont indépendants des flux d'or"], "Il décrit l'ajustement automatique sous l'étalon-or.", 5),
    ("Qu'est-ce que l'effet de bilan d'une dépréciation pour un emprunteur endetté en devises ?", "La dette exprimée en monnaie locale augmente, ce qui dégrade sa situation financière", ["La dette exprimée en monnaie locale diminue", "Les recettes en monnaie locale disparaissent", "Le taux d'intérêt devient négatif"], "Il peut déclencher des faillites.", 4),
    ("Que sont les réserves de change de précaution ?", "Des actifs en devises accumulés pour faire face aux chocs extérieurs", ["Des devises détenues uniquement pour spéculer à court terme sur les marchés", "Des billets conservés par les touristes", "Des dépôts des banques commerciales à l'étranger"], "Elles soutiennent la confiance dans la monnaie.", 3),
    ("Que décrit le carry trade ?", "Emprunter dans une monnaie à bas taux pour placer dans une monnaie à taux élevé", ["Acheter des marchandises à bas prix pour les revendre plus cher sur un autre marché", "Placer à l'étranger les recettes d'exportation", "Vendre des devises à terme sans les posséder"], "Le risque est une dépréciation de la monnaie de placement.", 4),
    ("Qu'est-ce que la contagion financière ?", "La transmission d'une crise d'un pays ou d'un marché à d'autres", ["La hausse des taux d'intérêt dans un seul pays, sans effet sur les autres", "L'effet d'une épidémie sur la production", "La hausse des prix à l'intérieur d'un secteur"], "Elle passe par les liens commerciaux et financiers.", 3),
    ("Quelle différence y a-t-il entre investissement direct et investissement de portefeuille ?", "Le premier vise un contrôle ou une influence durable, le second un simple placement financier", ["Le premier est toujours à court terme et purement spéculatif, le second est toujours à long terme", "Le premier ne concerne que l'État, le second que les ménages", "Il n'y a aucune différence"], "Le premier est en général plus stable.", 3),
    ("Qu'est-ce que le taux de change effectif réel ?", "Un taux pondéré par les partenaires commerciaux et corrigé des écarts de prix", ["Le prix de la monnaie nationale en dollars américains seulement, sans pondération", "Le taux auquel les banques se prêtent entre elles", "Le taux de change officiel fixé par l'État"], "Il sert à mesurer la compétitivité-prix.", 4),
    ("Quel est le rôle du FMI ?", "Contribuer à la stabilité monétaire mondiale et prêter aux pays en difficulté de paiements", ["Fixer les droits de douane de ses membres", "Financer directement les entreprises privées", "Arbitrer les litiges commerciaux entre États"], "Ses prêts s'accompagnent de conditions.", 3),
    ("Quelle est la mission de la Banque mondiale ?", "Financer le développement et la réduction de la pauvreté par des prêts et de l'assistance", ["Stabiliser le cours des monnaies", "Contrôler la masse monétaire mondiale", "Fixer les règles du commerce mondial"], "Elle comprend la BIRD et l'IDA.", 3),
    ("Que décrit la déflation par la dette (Fisher) ?", "La baisse des prix alourdit le poids réel des dettes et aggrave la dépression", ["La baisse des prix allège le poids réel des dettes", "La hausse des prix fait disparaître les dettes en les rendant impossibles à rembourser", "Les dettes baissent dès que le PIB augmente"], "Elle fut décrite à propos de la crise des années 1930.", 5),
    ("Que contient la balance courante d'un pays ?", "Les échanges de biens et de services, les revenus et les transferts courants", ["Les seuls échanges de biens avec l'étranger", "Les investissements directs et de portefeuille, qui relèvent du compte financier seulement", "Les variations des réserves de change uniquement"], "Les mouvements de capitaux relèvent du compte financier.", 3),
    ("Comment l'identité comptable relie-t-elle soldes sectoriels privé, public et extérieur ?", "Leur somme est nulle : un déficit d'un secteur est un excédent des autres", ["Leur somme est toujours positive", "Chacun est toujours égal à zéro", "Seul le solde public peut être négatif, les autres soldes étant toujours positifs"], "L'épargne d'un secteur finance le déficit d'un autre.", 5),
    ("Quel est le rôle d'un fonds souverain ?", "Placer à l'étranger des excédents publics pour les générations futures ou la stabilisation", ["Financer directement les ménages en difficulté", "Remplacer la banque centrale dans l'émission de monnaie et la fixation des taux directeurs", "Fixer le prix des matières premières"], "Il est souvent alimenté par des revenus de ressources.", 4),
    ("Que décrit le paradoxe de Lucas ?", "Le capital circule moins des pays riches vers les pays pauvres que ne le prédit la théorie", ["Le capital circule exclusivement vers les pays pauvres, où les rendements sont plus élevés", "Les pays pauvres épargnent toujours plus que les riches et investissent donc davantage", "Les taux d'intérêt sont identiques dans tous les pays, par arbitrage de la banque centrale"], "Les institutions et le capital humain l'expliquent en partie.", 5),
    ("Quel est l'effet d'un contrôle des capitaux ?", "Il limite les entrées ou sorties de capitaux pour réduire la volatilité financière", ["Il supprime tout besoin de réserves de change", "Il libère totalement les flux de capitaux", "Il impose l'adoption d'une monnaie étrangère"], "Il restreint la mobilité des capitaux du trilemme.", 3),
], cat="Finance internationale", src="sup-eco-commerce")

mcq(L3, "eco3-dev2", [
    ("Que décrit l'idée de piège à pauvreté ?", "Un équilibre où la faiblesse du revenu entretient celle de l'investissement", ["Un équilibre où la hausse de l'épargne appauvrit durablement le pays dans son ensemble", "Un blocage causé par un excès de réserves de change", "Un niveau de prix fixé au-dessous du coût"], "Il peut justifier une impulsion extérieure coordonnée.", 4),
    ("Qu'est-ce que la convergence bêta ?", "La croissance plus rapide des pays initialement plus pauvres", ["La hausse de la dispersion des revenus entre pays", "La baisse du taux d'intérêt mondial", "L'égalisation des prix entre pays"], "Elle est souvent conditionnelle aux caractéristiques des pays.", 5),
    ("Que décrit la distinction entre institutions inclusives et extractives (Acemoglu, Robinson) ?", "Les premières protègent les droits et ouvrent les opportunités, les secondes servent une élite", ["Les premières captent la richesse pour une élite étroite, les secondes protègent les droits de tous", "Les premières sont publiques, les secondes privées", "Les premières ne concernent que la finance"], "Elle explique des trajectoires de développement divergentes.", 5),
    ("Quelle est l'idée d'une responsabilité solidaire dans les prêts de groupe de microfinance ?", "Les membres répondent ensemble du remboursement, ce qui réduit les risques d'information", ["Un seul membre rembourse pour tout le groupe", "Le prêt est accordé sans aucun remboursement", "La banque centrale garantit chaque prêt"], "La pression des pairs remplace la garantie matérielle.", 4),
    ("Que sont les transferts monétaires conditionnels ?", "Des aides versées aux ménages pauvres à condition de scolariser ou de soigner les enfants", ["Des aides versées sans aucune condition à tous", "Des prêts remboursables à taux élevé", "Des subventions versées aux entreprises exportatrices pour réduire leurs coûts de production"], "Ils visent à accroître le capital humain.", 4),
    ("Qu'apporte la méthode des essais contrôlés randomisés à l'économie du développement ?", "L'évaluation causale de politiques par comparaison de groupes tirés au hasard", ["Un calcul exact du PIB à partir de registres administratifs de toutes les entreprises du pays", "La prévision des cours de matières premières", "La mesure de l'inflation par satellite"], "Banerjee, Duflo et Kremer l'ont popularisée.", 4),
    ("Que décrit la thèse du ruissellement (trickle-down) ?", "La croissance des revenus des plus riches finirait par bénéficier à tous", ["La croissance des plus pauvres freine toujours celle des plus riches du pays", "L'aide extérieure remplace l'épargne intérieure", "La hausse des prix profite aux producteurs agricoles"], "Elle est contestée empiriquement.", 3),
    ("Quelles dimensions la sécurité alimentaire couvre-t-elle selon la FAO ?", "La disponibilité, l'accès, l'utilisation et la stabilité", ["La production, le stockage, le transport et l'exportation", "Le prix, la qualité et le conditionnement", "La monnaie, le crédit et l'assurance"], "Les quatre dimensions sont cumulatives.", 4),
    ("Que faisait valoir l'analyse du métayage de Stiglitz ?", "Il partage le risque tout en offrant des incitations partielles", ["Il supprime tout risque pour le propriétaire et pour le métayer", "Il incite à un effort maximal du métayer", "Il est toujours inefficace"], "Il répond à des marchés d'assurance et de crédit imparfaits.", 5),
    ("Quel est le sens de l'expression « dividende démographique » ?", "Le gain de croissance quand la part des actifs dans la population augmente", ["La hausse du coût des retraites avec le vieillissement de la population active", "Le bénéfice tiré d'une natalité en hausse permanente", "L'économie réalisée par l'émigration des jeunes"], "Elle suppose des emplois pour les nouveaux actifs.", 4),
    ("Que décrit le modèle des « oies sauvages » (Akamatsu) ?", "Une industrialisation par vagues successives de pays suivant un chef de file", ["Une migration saisonnière de travailleurs agricoles entre régions voisines d'un même pays", "Une spécialisation durable dans le secteur primaire", "Une hausse simultanée des prix dans plusieurs pays"], "Il a été appliqué à l'Asie de l'Est.", 5),
    ("Que sont les chaînes de valeur mondiales ?", "La répartition des étapes de production d'un bien entre plusieurs pays", ["Le transport de marchandises par conteneurs entre les grands ports du monde", "La liste des prix des biens exportés", "Un accord de libre-échange régional"], "Les pays peuvent chercher à monter en gamme.", 3),
    ("Qu'est-ce que la volatilité des prix des matières premières pour un pays exportateur ?", "Des variations de recettes qui compliquent les budgets et l'investissement", ["Une stabilité des recettes d'exportation, qui facilite la planification des budgets publics", "Une hausse permanente de la production", "Une baisse des importations de services"], "Un fonds de stabilisation peut l'atténuer.", 3),
    ("Qui a introduit le concept de secteur informel dans l'analyse du développement ?", "Keith Hart", ["Arthur Lewis", "Walt Rostow", "Amartya Sen"], "Il l'a mis en avant à propos de l'emploi urbain au Ghana.", 5),
    ("Que sont les Objectifs de développement durable (ODD) ?", "Un ensemble de 17 objectifs fixés par l'ONU pour l'horizon 2030", ["Un traité commercial signé entre États africains au sein de l'Union africaine", "Un fonds de l'ONU destiné aux banques centrales", "Un indice de bonne gouvernance publié par le FMI"], "Ils ont succédé aux Objectifs du millénaire.", 3),
    ("Qu'est-ce que l'Agenda 2063 de l'Union africaine ?", "Un cadre stratégique de développement du continent à long terme", ["Un calendrier de scrutins et de sommets organisés dans les États membres", "Un traité de défense collective", "Un programme de dévaluation concertée"], "Il vise la transformation économique et l'intégration.", 4),
    ("Qu'est-ce que le NEPAD ?", "Le Nouveau partenariat pour le développement de l'Afrique, programme de l'Union africaine", ["Une banque de développement ouest-africaine", "Une bourse de valeurs panafricaine", "Un fonds de l'ONU pour les réfugiés"], "Il a été lancé au début des années 2000.", 4),
    ("Que décrit la courbe de Kuznets environnementale ?", "Une pollution qui augmenterait puis diminuerait avec le revenu par habitant", ["Une pollution qui augmente toujours avec le revenu par habitant, sans jamais diminuer ensuite", "Une pollution indépendante du revenu", "Une pollution qui diminue d'abord puis augmente"], "Elle est discutée empiriquement.", 5),
    ("Qu'est-ce que le capital social dans l'analyse du développement ?", "Les réseaux, normes et relations de confiance facilitant l'action collective", ["Le capital versé par les actionnaires d'une société lors de sa création, avant toute activité", "L'épargne publique investie dans le social", "Le patrimoine financier d'un ménage"], "Les tontines en sont une illustration.", 4),
    ("Que montre une dépendance à l'égard d'une seule ressource d'exportation ?", "Une vulnérabilité aux chocs de prix et une diversification limitée", ["Une protection contre les chocs extérieurs et une diversification assurée", "Une baisse certaine de la monnaie", "Une inflation nulle"], "La diversification est un objectif de long terme.", 3),
    ("Que montrent Acemoglu, Johnson et Robinson à propos de la colonisation ?", "Les institutions héritées, liées aux conditions de colonisation, influencent le revenu actuel", ["Le climat explique seul les écarts de revenu actuels", "La colonisation n'a eu aucun effet de long terme", "Les écarts de revenu viennent uniquement des ressources du sol et du sous-sol de chaque pays"], "Ils utilisent la mortalité des colons comme instrument.", 5),
    ("Qu'est-ce que la gouvernance en économie du développement ?", "La façon dont les pouvoirs sont exercés pour gérer les ressources et les politiques", ["Le montant de l'aide reçue par un pays", "Le niveau de la dette publique d'un État", "Le nombre de fonctionnaires par habitant"], "Corruption et qualité des institutions en font partie.", 3),
    ("Que contient l'expression « avantage comparatif dynamique » ?", "Un avantage construit au fil du temps par apprentissage et accumulation de capacités", ["Un avantage fixé à jamais par les dotations naturelles et qui ne peut jamais être construit", "Un avantage tenant à la seule taille du marché", "Un avantage lié aux prix de l'énergie"], "Il justifie des politiques industrielles temporaires.", 5),
    ("Quelle est la logique d'une zone économique spéciale ?", "Offrir un régime fiscal et administratif favorable pour attirer l'investissement et l'exportation", ["Interdire toute activité industrielle dans une région", "Isoler une région de tout échange avec l'extérieur", "Imposer des prix plafonds aux produits agricoles"], "Elle est utilisée pour stimuler l'industrialisation.", 3),
], cat="Développement économique", src="sup-eco-dev")

mcq(L3, "eco3-macro2", [
    ("Que représente la courbe de Beveridge ?", "La relation entre le taux de postes vacants et le taux de chômage", ["La relation entre inflation et chômage", "La relation entre dette publique et croissance du PIB sur longue période", "La relation entre épargne et investissement"], "Elle est généralement décroissante.", 5),
    ("Que décrivent les modèles d'appariement (Mortensen, Pissarides) ?", "La recherche d'emplois et la rencontre entre chômeurs et postes vacants", ["La fixation des prix par les monopoles", "La répartition des revenus entre capital et travail dans l'économie nationale", "La détermination du taux de change"], "Ils expliquent la coexistence de chômage et de postes vacants.", 5),
    ("Que décrit l'effet Pigou ?", "La baisse des prix accroît la valeur réelle de la monnaie détenue et soutient la consommation", ["La hausse des prix accroît la valeur réelle de la monnaie détenue et soutient la consommation", "La baisse des prix réduit le revenu réel des ménages", "La baisse des taux d'intérêt réduit l'investissement"], "C'est un effet de richesse.", 5),
    ("Quelle est la caractéristique du modèle de Ramsey-Cass-Koopmans ?", "Le taux d'épargne y résulte du choix d'un ménage à horizon infini", ["Le taux d'épargne y est une constante exogène fixée par la politique budgétaire", "Il n'y a aucun capital dans le modèle", "L'État fixe la consommation de chaque période"], "Il endogénéise l'épargne par rapport à Solow.", 5),
    ("Que décrit un modèle à générations imbriquées (Samuelson, Diamond) ?", "Des individus qui vivent plusieurs périodes et échangent avec d'autres générations", ["Un seul agent représentatif vivant à l'infini, qui n'échange avec aucune autre génération", "Des firmes qui se succèdent sur un marché", "Des États qui renouvellent leur dette"], "Il peut conduire à une inefficience dynamique.", 5),
    ("Que décrit l'équation d'Euler de la consommation ?", "Le lissage de la consommation selon le taux d'intérêt et la préférence pour le présent", ["La répartition d'un revenu entre biens de consommation à une date donnée et à prix fixes", "La production de plein emploi d'une économie", "Le multiplicateur d'une relance budgétaire"], "Elle fonde l'arbitrage intertemporel.", 5),
    ("Qu'est-ce qu'un modèle DSGE ?", "Un modèle macroéconomique dynamique fondé sur des comportements optimisateurs et des chocs", ["Un modèle statique sans anticipations", "Un modèle de comptabilité nationale", "Un modèle de prévision purement statistique"], "Il intègre anticipations rationnelles et rigidités.", 5),
    ("Que décrit le biais inflationniste (Barro-Gordon) ?", "Un décideur discrétionnaire crée de l'inflation pour réduire le chômage, sans y parvenir durablement", ["Un décideur ne peut jamais provoquer d'inflation", "Un décideur gagne toujours en réduisant le chômage", "L'inflation est toujours nulle à l'équilibre"], "Les agents anticipent et l'inflation s'installe sans gain d'emploi.", 5),
    ("Qu'est-ce qu'un banquier central conservateur (Rogoff) ?", "Un banquier central qui accorde plus d'importance à la stabilité des prix que la société", ["Un banquier central qui refuse toute intervention", "Un banquier central qui ignore l'inflation", "Un banquier central élu chaque année"], "Cela réduit le biais inflationniste.", 5),
    ("Qu'est-ce que l'accélérateur financier (Bernanke, Gertler) ?", "L'amplification des chocs par les conditions de financement liées à la valeur des garanties", ["La hausse du taux d'intérêt due à l'inflation", "L'accélération de l'investissement public", "La vitesse de circulation de la monnaie"], "La valeur des actifs détermine l'accès au crédit.", 5),
    ("Que décrit le canal du crédit de la politique monétaire ?", "L'effet de la politique monétaire sur l'offre de prêts des banques et la capacité d'emprunt", ["L'effet sur le taux de change seulement", "L'effet sur les prix de l'énergie", "L'effet sur les exportations par les droits de douane"], "Il s'ajoute au canal du taux d'intérêt.", 5),
    ("Qu'est-ce que le taux d'intérêt naturel (Wicksell) ?", "Le taux compatible avec l'égalité de l'épargne et de l'investissement à prix stables", ["Le taux directeur de la banque centrale", "Le taux d'intérêt le plus bas du marché", "Le taux de rendement des actions"], "Un écart avec le taux de marché engendre inflation ou déflation.", 5),
    ("Que décrit le paradoxe de Solow sur la productivité ?", "Les ordinateurs étaient partout, sauf dans les statistiques de productivité", ["La productivité augmente toujours plus vite que le capital dans les pays avancés", "L'investissement en capital fait chuter la production", "Les prix des ordinateurs sont constants"], "Il pose la question de la mesure des gains de l'informatique.", 5),
    ("Que sont les faits stylisés de Kaldor ?", "Des régularités de long terme comme la stabilité des parts du travail et du capital dans le revenu", ["Des lois mathématiques sur le chômage", "Des règles de comptabilité des entreprises", "Des modèles de formation des prix"], "Ils ont guidé la construction de modèles de croissance.", 5),
    ("Que décrit le cycle politico-économique (Nordhaus) ?", "Des politiques de relance avant les élections et de rigueur après", ["Des politiques de rigueur avant les élections et de relance après le scrutin", "Une politique neutre sur tout le mandat", "Un cycle déterminé par la météo"], "Il met en jeu des motivations électorales.", 4),
    ("Que sont les stabilisateurs automatiques côté recettes ?", "L'effet des impôts progressifs qui baissent quand le revenu baisse, sans nouvelle décision", ["Les hausses d'impôts votées après une crise", "Les subventions fixées par ordonnance", "Les droits de douane modifiés en urgence"], "Ils atténuent les fluctuations.", 4),
    ("Qu'est-ce que la politique macroprudentielle ?", "Un ensemble d'outils visant à limiter le risque systémique et les cycles du crédit", ["Un ensemble d'outils visant le seul taux de change et le niveau des réserves de la banque centrale", "La supervision comptable d'une seule banque", "La politique de prix des entreprises publiques"], "Elle diffère de la supervision d'établissements isolés.", 5),
    ("Que décrit la courbe de Phillips des nouveaux keynésiens ?", "Une relation entre inflation courante, inflation anticipée et écart de production", ["Une relation entre inflation passée et taux de change", "Une relation entre dette et déficit", "Une relation entre salaires nominaux et production agricole dans les pays en développement"], "Elle intègre les anticipations et les rigidités.", 5),
    ("Que décrit le concept de dominance budgétaire ?", "Une banque centrale contrainte de financer l'État, ce qui compromet l'objectif de prix", ["Un budget toujours plus élevé que les recettes", "La domination d'un ministère sur les autres", "Un déficit qui se réduit tout seul"], "La politique budgétaire impose son rythme à la politique monétaire.", 5),
    ("Quelle est l'idée de la désinflation compétitive ?", "Rendre les prix plus faibles que ceux des partenaires pour regagner des parts de marché", ["Relever tous les prix pour financer l'investissement des entreprises et des administrations", "Dévaluer la monnaie pour supprimer le chômage", "Fixer les salaires au niveau mondial"], "Elle remplace la dévaluation dans un régime de change fixe.", 5),
    ("Que décrit la trappe déflationniste ?", "Une déflation qui relève les taux réels et alourdit les dettes, freinant la demande", ["Une inflation qui allège toutes les dettes", "Une baisse des prix qui accroît l'investissement en allégeant le poids réel des dettes", "Un taux d'intérêt nul qui stimule la demande"], "La politique monétaire perd en efficacité.", 5),
    ("Quel est l'objectif de l'ancrage nominal pour une banque centrale ?", "Fixer un repère (taux de change, agrégat, cible d'inflation) pour guider les anticipations de prix", ["Fixer les prix de chaque bien", "Garantir la hausse des salaires", "Interdire tout crédit bancaire"], "Un régime de change fixe est un ancrage.", 4),
    ("Que mesure la productivité du travail ?", "La production par heure travaillée ou par travailleur", ["Le nombre d'heures par semaine", "Le salaire moyen horaire", "Le nombre moyen de travailleurs par entreprise et par secteur d'activité"], "Elle est un moteur du niveau de vie.", 3),
    ("Que signifie un solde budgétaire structurel ?", "Le solde corrigé de l'effet du cycle économique", ["Le solde des seules dépenses d'investissement public de l'année", "Le solde des recettes exceptionnelles", "Le solde après paiement de la dette"], "Il isole l'effort discrétionnaire.", 4),
    ("Que décrit la loi de Verdoorn ?", "Une productivité qui croît avec la production, par économies d'échelle dynamiques", ["Une productivité qui baisse quand la production croît, par déséconomies d'échelle croissantes", "Un chômage qui augmente avec la production", "Un prix qui baisse avec la population"], "Elle appartient à la tradition postkeynésienne.", 5),
    ("Que décrit l'effet d'hystérèse sur la production potentielle ?", "Une récession peut réduire durablement le niveau de production potentielle", ["Une récession n'a aucun effet durable", "Une récession augmente toujours la production potentielle des années suivantes", "La production potentielle dépend du seul taux d'intérêt"], "Elle passe par l'investissement et le capital humain.", 5),
], cat="Macroéconomie avancée", src="sup-eco-macro")

mcq(L3, "eco3-compl", [
    ("Qu'est-ce que l'aléa moral dans une relation entre un employeur et un salarié ?", "L'effort du salarié n'est pas observable", ["Le salarié a une qualification trompeuse dès l'embauche", "L'employeur ne paie jamais les cotisations sociales exigibles", "Le salarié refuse de signer un contrat écrit"], "L'employeur ne peut contrôler l'effort une fois le contrat signé.", 4),
    ("Que décrit une courbe d'Engel ?", "Le lien entre le revenu d'un ménage et sa dépense pour un bien", ["Le lien entre le prix d'un bien et la quantité offerte par les firmes", "Le lien entre le taux d'intérêt et l'investissement des entreprises", "Le lien entre le prix d'un bien et celui de ses substituts"], "Elle permet de distinguer biens normaux, de luxe et inférieurs.", 4),
    ("Que mesure l'élasticité de substitution entre deux facteurs de production ?", "La facilité avec laquelle la firme remplace un facteur par l'autre", ["La part du capital dans la valeur ajoutée de l'entreprise", "La variation du profit quand le salaire double", "Le rapport entre la production et la population active"], "Elle vaut 1 pour une fonction Cobb-Douglas.", 5),
    ("Quelle propriété a une fonction Cobb-Douglas à propos des parts des facteurs dans le revenu ?", "Elles sont constantes", ["Elles varient avec le rapport capital-travail de la firme", "Elles dépendent de la taille du marché de biens", "Elles s'annulent quand les rendements sont constants"], "Chaque facteur reçoit une part fixe égale à son exposant.", 5),
    ("Que décrit l'effet de cliquet sur la consommation ?", "Les ménages réduisent peu leur consommation quand le revenu baisse", ["Les ménages augmentent fortement leur épargne à chaque hausse du revenu", "Les ménages consomment davantage de biens inférieurs quand le revenu monte", "Les ménages règlent leurs achats à crédit en période de croissance"], "La consommation s'ajuste plus lentement à la baisse qu'à la hausse.", 5),
    ("Que décrit le concept de rationnement du crédit (Stiglitz, Weiss) ?", "Des prêteurs refusent des prêts même à un taux plus élevé pour limiter la sélection adverse", ["Des prêteurs accordent tous les prêts demandés au taux de marché", "Des prêteurs relèvent toujours le taux jusqu'à égalité de l'offre et de la demande", "Des États interdisent tout crédit aux ménages"], "Un taux élevé attire les emprunteurs risqués.", 5),
    ("Qu'est-ce que le taux de rendement interne d'un projet ?", "Le taux d'actualisation pour lequel la valeur actuelle nette est nulle", ["Le taux d'intérêt de l'emprunt qui finance le projet", "Le rapport du profit annuel à la taille de l'entreprise", "Le taux de croissance du chiffre d'affaires du projet"], "On le compare au coût du capital.", 4),
    ("Que mesure le ratio dette sur PIB ?", "Le poids de la dette publique rapporté à la production annuelle", ["Le montant des intérêts payés chaque année par l'État", "Le déficit budgétaire de l'année rapporté aux recettes", "La part de la dette détenue par des non-résidents"], "C'est l'indicateur usuel de soutenabilité.", 3),
    ("Qu'appelle-t-on dette extérieure d'un pays ?", "Les engagements de ses résidents envers des non-résidents", ["Les engagements de l'État envers ses seuls fonctionnaires", "Les dettes libellées uniquement en monnaie nationale", "Les créances de ses résidents sur le reste du monde"], "Elle concerne l'État mais aussi les agents privés.", 3),
    ("Qu'est-ce que la valeur ajoutée brute d'une branche ?", "Sa production moins ses consommations intermédiaires", ["Sa production moins ses salaires et ses impôts", "Ses ventes moins ses achats de biens d'équipement", "Son profit après impôt et dividendes versés"], "Le PIB en est la somme, plus les impôts nets sur les produits.", 3),
    ("Que montre une courbe de Lorenz proche de la diagonale ?", "Une répartition proche de l'égalité", ["Une inégalité très forte entre les ménages", "Un revenu moyen très élevé dans le pays", "Un chômage proche de zéro"], "La diagonale représente l'égalité parfaite.", 3),
    ("Comment le coefficient de Gini est-il lié à la courbe de Lorenz ?", "Il mesure l'écart entre la courbe et la diagonale d'égalité", ["Il mesure la pente de la courbe à l'origine", "Il mesure la position du revenu médian sur la courbe", "Il mesure la somme des revenus des plus pauvres"], "Plus l'aire est grande, plus l'inégalité est forte.", 4),
    ("Que signifie l'expression « prix à la production » par rapport à l'indice des prix à la consommation ?", "Les prix reçus par les producteurs, et non ceux payés par les ménages", ["Les prix payés par les ménages, hors taxes", "Les prix des seuls biens agricoles du pays", "Les prix des importations en monnaie étrangère"], "Les deux indices peuvent évoluer différemment.", 4),
    ("Qu'est-ce que le taux de change à terme ?", "Un cours fixé aujourd'hui pour un échange de devises à une date future", ["Un cours qui sera publié par la banque centrale demain", "Le cours moyen constaté sur l'année écoulée", "Le cours de la devise sur le marché des billets"], "Il sert notamment à se couvrir contre le risque de change.", 4),
    ("Que fait une couverture de change pour un exportateur ?", "Elle fixe le taux auquel il convertira ses recettes futures", ["Elle supprime toute concurrence étrangère sur ses marchés", "Elle garantit un profit minimum sur chaque vente", "Elle réduit ses coûts de production en devises"], "Elle protège contre une variation défavorable du cours.", 4),
    ("Que sont les engagements hors bilan d'une banque ?", "Des garanties ou lignes de crédit qui n'apparaissent ni à l'actif ni au passif", ["Les dépôts qui ne rapportent aucun intérêt", "Les crédits déjà remboursés par les clients", "Les capitaux propres affectés aux dividendes"], "Ils comportent un risque potentiel.", 5),
    ("Qu'est-ce qu'un effet de levier financier ?", "L'accroissement de la rentabilité des fonds propres par l'endettement", ["La baisse du taux d'intérêt due à l'emprunt", "La hausse du chiffre d'affaires due à la publicité", "L'augmentation des dividendes versées aux salariés"], "Il accroît aussi le risque lorsque le rendement des actifs baisse.", 4),
    ("Que sont des anticipations adaptatives ?", "Des prévisions construites à partir des valeurs passées de la variable", ["Des prévisions qui utilisent tout le modèle de l'économie", "Des prévisions toujours identiques à la réalité", "Des prévisions imposées par la banque centrale"], "Elles s'opposent aux anticipations rationnelles.", 4),
    ("Que dit la règle de Wicksell à propos de l'inflation ?", "Si le taux de marché est inférieur au taux naturel, la demande s'emballe et les prix montent", ["Si le taux de marché est supérieur au taux naturel, les prix montent", "Les prix ne dépendent pas des taux d'intérêt", "Les taux d'intérêt sont toujours égaux à l'inflation"], "Un crédit trop bon marché stimule la dépense.", 5),
    ("Qu'est-ce que le multiplicateur de dépôt dans un système bancaire avec fuites en billets ?", "Il est plus faible que 1/r car une partie de la monnaie est détenue en billets", ["Il est plus fort que 1/r car les billets circulent plus vite", "Il est égal à 1/r quelle que soit la préférence pour les billets", "Il est nul quand les billets sont détenus par le public"], "Les fuites réduisent le crédit que les banques peuvent créer.", 5),
    ("Quel est l'effet d'une hausse du taux d'épargne sur le revenu par tête de long terme dans le modèle de Solow ?", "Un niveau plus élevé, mais sans effet durable sur le taux de croissance", ["Un taux de croissance permanent plus élevé", "Aucun effet sur le niveau ni sur le taux de croissance", "Une baisse du niveau du revenu par tête à long terme"], "L'effet sur la croissance n'est que transitoire.", 5),
    ("Que décrit le concept de rattrapage (catch-up) ?", "La croissance plus rapide d'un pays en retard qui adopte des technologies déjà existantes", ["La hausse des prix d'un pays qui rattrape son inflation passée", "L'obligation de rembourser une dette en retard de paiement", "Le retour d'un cycle à sa tendance après une récession"], "Il est lié à la convergence.", 4),
    ("Qu'est-ce qu'une politique de taux de change réel favorable à l'exportation ?", "Un taux de change réel déprécié qui améliore la compétitivité-prix", ["Un taux de change réel apprécié qui améliore la compétitivité-prix", "Un taux de change nominal fixé au niveau des importations", "Une hausse des prix intérieurs supérieure à celle des partenaires"], "Les produits nationaux deviennent moins chers pour l'étranger.", 4),
    ("Que décrit le coefficient de Gini égal à 1 ?", "Toute la richesse est détenue par une seule personne", ["Chaque ménage a exactement le même revenu", "Le revenu moyen est égal au revenu médian", "La moitié de la population est sous le seuil de pauvreté"], "C'est la situation d'inégalité maximale.", 3),
    ("Qu'est-ce que la préférence pour le présent ?", "Le fait de valoriser davantage une unité de consommation immédiate qu'une unité future", ["Le fait de préférer les biens importés aux biens locaux", "Le fait de payer toujours comptant plutôt qu'à crédit", "Le fait d'épargner une part fixe de son revenu"], "Elle explique le taux d'actualisation.", 4),
    ("Que mesure le taux marginal d'imposition ?", "La part de la dernière unité de revenu qui est prélevée", ["La part moyenne du revenu total qui est prélevée", "Le montant maximal de l'impôt possible", "Le taux appliqué aux revenus du capital seulement"], "Il influence les décisions de travail et d'épargne.", 3),
    ("Que décrit le concept d'avantage comparatif révélé ?", "Les secteurs où un pays exporte relativement plus que la moyenne mondiale", ["Les secteurs où un pays importe le plus de biens finis", "Les secteurs protégés par des quotas d'importation", "Les secteurs les plus subventionnés par l'État"], "Il se déduit des données d'exportation.", 5),
    ("Que sont des biens échangeables ?", "Des biens qui peuvent être vendus sur les marchés internationaux", ["Des biens qui sont toujours produits par l'État", "Des biens que seuls les ménages peuvent acheter", "Des biens non stockables comme les services de coiffure"], "Les biens non échangeables restent sur le marché local.", 3),
    ("Que décrit l'hypothèse de revenu relatif de Duesenberry ?", "La consommation d'un ménage dépend de son revenu par rapport à celui des autres et à son passé", ["La consommation dépend uniquement du revenu moyen du pays", "La consommation est indépendante de tout revenu", "La consommation dépend du seul taux d'intérêt réel"], "Elle fonde l'effet de démonstration et le cliquet.", 5),
    ("Quelle est la relation entre le taux d'intérêt réel et l'investissement dans la théorie keynésienne ?", "Elle est inverse : un taux plus bas encourage l'investissement", ["Elle est directe : un taux plus élevé encourage l'investissement", "Elle est nulle dans tous les modèles de l'économie", "Elle dépend seulement du taux de change du pays"], "L'efficacité marginale du capital est comparée au taux d'intérêt.", 3),
    ("Que décrit l'efficacité marginale du capital chez Keynes ?", "Le taux de rendement attendu d'une unité d'investissement supplémentaire", ["Le rapport de la production au stock de capital existant", "Le taux d'intérêt du marché obligataire", "La part des profits dans la valeur ajoutée"], "On investit tant qu'elle excède le taux d'intérêt.", 5),
    ("Qu'est-ce que l'inertie des prix (rigidité nominale) ?", "Le fait que les prix s'ajustent lentement après un choc de demande", ["Le fait que les prix soient toujours égaux au coût marginal", "Le fait que les prix soient fixés par la banque centrale", "Le fait que les prix baissent instantanément après un choc"], "Elle donne un rôle aux politiques de demande à court terme.", 4),
    ("Que décrit la distinction entre inflation anticipée et inflation non anticipée ?", "Seule la seconde redistribue la richesse de façon imprévue entre débiteurs et créanciers", ["Seule la première redistribue la richesse entre débiteurs et créanciers", "Chacune a toujours les mêmes effets sur les contrats", "Ni l'une ni l'autre n'a d'effet sur les contrats de crédit"], "Les contrats intègrent l'inflation anticipée.", 4),
    ("Que signifie une courbe des taux de pente positive ?", "Les taux à long terme sont supérieurs aux taux à court terme", ["Les taux à court terme sont supérieurs aux taux à long terme", "Les taux à court et long terme sont identiques", "Les taux directeurs sont toujours nuls"], "C'est la configuration habituelle.", 4),
], cat="Économie : compléments avancés", src="sup-eco-macro")
