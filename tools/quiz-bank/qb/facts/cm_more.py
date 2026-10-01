"""Cameroun (suite) : clubs, universités, sigles d'institutions et d'entreprises, barrages, lacs, montagnes, fleuves, fêtes légales, minerais.
Faits écrits par l'assistant : tous `review`."""
from ..facts_engine import fq, pairs, pick, source

CM, G = "general", "CM"
source("cm-orga", "Sigles, entreprises, universités et clubs du Cameroun", "ouvrage de référence", "Comparer avec les sites officiels des institutions citées et l'annuaire des entreprises publiques et parapubliques.")
source("cm-hydro", "Barrages, lacs, montagnes et fleuves du Cameroun", "atlas", "Comparer avec un atlas du Cameroun et les fiches d'ENEO / de la Sanaga (barrages) ; les noms locaux varient.")
source("cm-cal", "Fêtes légales du Cameroun", "texte officiel", "Comparer avec le décret fixant les jours fériés et chômés au Cameroun (Code du travail).")

CLUBS = [("Canon Yaoundé", "Yaoundé"), ("Tonnerre Yaoundé", "Yaoundé"), ("Union Douala", "Douala"), ("Dynamo Douala", "Douala"), ("Les Astres FC", "Douala"), ("Coton Sport", "Garoua"),
         ("Fovu Club", "Baham"), ("Racing Club de Bafoussam", "Bafoussam"), ("Aigle Royal de la Menoua", "Dschang"), ("Panthère du Ndé", "Bangangté"), ("Colombe de Sangmélima", "Sangmélima"),
         ("Yong Sports Academy", "Bamenda")]
pairs(CM, "cm-club", CLUBS, "Dans quelle ville est basé le club de football {a} ?", None, region=G, cat="Sport", src="cm-orga", diff=3, expl="{a} est basé à {b}.",
      extra_b=["Garoua", "Maroua", "Ngaoundéré", "Bertoua", "Ebolowa", "Limbé", "Kribi", "Édéa", "Buea", "Nkongsamba"])
UNIS = [("l'Université de Yaoundé I", "Yaoundé"), ("l'Université de Yaoundé II", "Soa"), ("l'Université de Douala", "Douala"), ("l'Université de Dschang", "Dschang"), ("l'Université de Ngaoundéré", "Ngaoundéré"),
        ("l'Université de Buea", "Buea"), ("l'Université de Bamenda", "Bamenda"), ("l'Université de Maroua", "Maroua"), ("l'Université de Bertoua", "Bertoua"), ("l'Université d'Ebolowa", "Ebolowa"),
        ("l'ENAM (École nationale d'administration et de magistrature)", "Yaoundé"), ("l'ESSEC de Douala", "Douala"), ("l'ISSEA (statistique et économie appliquée)", "Yaoundé"), ("l'Université catholique d'Afrique centrale", "Yaoundé")]
pairs(CM, "cm-univ", UNIS, "Dans quelle ville se trouve {a} ?", None, region=G, cat="Éducation", src="cm-orga", diff=3, expl="{a} se trouve à {b}.",
      extra_b=["Garoua", "Bafoussam", "Limbé", "Kribi", "Édéa", "Nkongsamba", "Mbalmayo"])
SIGLES = [("CNPS", "Caisse nationale de prévoyance sociale"), ("CAMTEL", "Cameroon Telecommunications"), ("ENEO", "la société de distribution d'électricité du Cameroun"), ("SNH", "Société nationale des hydrocarbures"),
          ("SONARA", "Société nationale de raffinage"), ("SODECOTON", "Société de développement du coton"), ("CDC", "Cameroon Development Corporation"), ("CAMRAIL", "la société de transport ferroviaire du Cameroun"),
          ("PAD", "Port autonome de Douala"), ("PAK", "Port autonome de Kribi"), ("MINFOF", "Ministère des Forêts et de la Faune"), ("MINESEC", "Ministère des Enseignements secondaires"),
          ("MINEDUB", "Ministère de l'Éducation de base"), ("MINESUP", "Ministère de l'Enseignement supérieur"), ("MINSANTE", "Ministère de la Santé publique"), ("MINADER", "Ministère de l'Agriculture et du Développement rural"),
          ("MINFI", "Ministère des Finances"), ("DGSN", "Délégation générale à la Sûreté nationale"), ("BIR", "Bataillon d'intervention rapide"), ("CRTV", "Cameroon Radio Television"),
          ("INS", "Institut national de la statistique"), ("ELECAM", "Elections Cameroon, l'organisme chargé des élections"), ("CAMWATER", "Cameroon Water Utilities Corporation"), ("SOCAPALM", "Société camerounaise de palmeraies"),
          ("HEVECAM", "Hévéa du Cameroun"), ("BEAC", "Banque des États de l'Afrique centrale"), ("CEMAC", "Communauté économique et monétaire de l'Afrique centrale"), ("OAPI", "Organisation africaine de la propriété intellectuelle")]
pairs(CM, "cm-sigle", SIGLES, "Que signifie le sigle {a} (Cameroun) ?", "Quel sigle désigne : {b} ?", region=G, cat="Institutions et entreprises", src="cm-orga", diff=3, expl="{a} : {b}.")
HYDRO = [("Édéa", "la Sanaga"), ("Song Loulou", "la Sanaga"), ("Nachtigal", "la Sanaga"), ("Lagdo", "la Bénoué"), ("Memve'ele", "le Ntem"), ("Lom Pangar", "le Lom"), ("Mapé", "le Mapé")]
for n, r in HYDRO:
    fq(CM, "cm-dam", f"Sur quel cours d'eau se trouve le barrage de {n} ?", r[0].upper() + r[1:], pick("dam" + n, ["La Sanaga", "La Bénoué", "Le Ntem", "Le Lom", "Le Wouri", "Le Nyong", "Le Logone", "Le Mapé", "Le Noun"], r[0].upper() + r[1:], 9),
       f"Le barrage de {n} est sur {r}.", "cm-hydro", G, "Géographie du Cameroun", 4)
LAKES = [("le lac Nyos", "Nord-Ouest"), ("le lac Monoun", "Ouest"), ("le lac Oku", "Nord-Ouest"), ("le lac Barombi Mbo", "Sud-Ouest"), ("le lac Bamendjing", "Ouest"), ("le lac Mbakaou", "Adamaoua"),
         ("le lac Ossa", "Littoral"), ("le lac de Maga", "Extrême-Nord"), ("le lac de Lagdo", "Nord")]
pairs(CM, "cm-lake-region", LAKES, "Dans quelle région du Cameroun se trouve {a} ?", None, region=G, cat="Géographie du Cameroun", src="cm-hydro", diff=4, expl="{a} se trouve dans la région {b}.")
MOUNTS = [("le mont Cameroun", "Sud-Ouest"), ("le mont Oku", "Nord-Ouest"), ("les monts Bamboutos", "Ouest"), ("le mont Kupe", "Sud-Ouest"), ("les monts Mandara", "Extrême-Nord"), ("le massif du Tchabal Mbabo", "Adamaoua")]
pairs(CM, "cm-mount-region", MOUNTS, "Dans quelle région du Cameroun se trouve {a} ?", None, region=G, cat="Géographie du Cameroun", src="cm-hydro", diff=3, expl="{a} se trouve dans la région {b}.")
RIVERS = [("le Wouri", "Douala"), ("la Sanaga", "Édéa"), ("la Bénoué", "Garoua"), ("le Logone", "Kousséri"), ("le Nyong", "Akonolinga"), ("le Mungo", "Mbanga")]
pairs(CM, "cm-river-city", RIVERS, "Quelle ville camerounaise est située sur ou près de {a} ?", None, region=G, cat="Géographie du Cameroun", src="cm-hydro", diff=4, expl="{b} se trouve sur {a}.",
      extra_b=["Maroua", "Bertoua", "Bafoussam", "Ebolowa", "Kribi", "Ngaoundéré"])
HOL = [("le Jour de l'An", "1er janvier"), ("la Fête de la Jeunesse", "11 février"), ("la Journée internationale de la femme", "8 mars"), ("la Fête du Travail", "1er mai"), ("la Fête nationale du Cameroun", "20 mai"),
       ("l'Assomption", "15 août"), ("Noël", "25 décembre")]
pairs(CM, "cm-holiday", HOL, "À quelle date tombe {a} (jour férié au Cameroun) ?", "Quelle fête légale a lieu le {b} au Cameroun ?", region=G, cat="Calendrier camerounais", src="cm-cal", diff=1,
      expl="{a} : {b}.", extra_b=["2 janvier", "14 février", "21 mars", "25 mai", "1er juin", "10 octobre", "1er novembre", "31 décembre"])
MIN = [
    ("Quelle région du Cameroun renferme le gisement de bauxite de Minim-Martap ?", "L'Adamaoua", ["Le Nord", "L'Ouest", "Le Littoral", "Le Sud", "Le Centre"], "Minim-Martap est l'un des grands gisements de bauxite du pays.", 4),
    ("Dans quelle région se trouve le gisement de fer de Mbalam ?", "L'Est", ["L'Adamaoua", "Le Sud", "Le Centre", "L'Ouest", "Le Nord"], "Le projet de Mbalam-Nabeba est situé à l'Est, près de la frontière congolaise.", 4),
    ("Quelle région du Cameroun est connue pour l'orpaillage artisanal autour de Batouri ?", "L'Est", ["Le Nord", "L'Ouest", "Le Littoral", "Le Sud-Ouest", "L'Extrême-Nord"], "L'Est abrite des exploitations artisanales d'or.", 3),
    ("Où le Cameroun exploite-t-il l'essentiel de son pétrole en mer ?", "Au large du Sud-Ouest (Rio del Rey) et du Littoral", ["Dans le désert du Nord", "Dans les montagnes de l'Ouest", "Dans la forêt de l'Est", "Dans l'Adamaoua", "Dans l'Extrême-Nord"], "Les puits sont dans le golfe de Guinée.", 4),
    ("Quel instrument à cordes de la tradition fang/béti accompagne l'épopée du mvet ?", "Le mvet", ["Le balafon", "Le djembé", "La kora", "Le ngoni", "Le violon"], "Le mvet est à la fois un instrument et un genre d'épopée orale.", 4),
    ("Quel instrument traditionnel de percussion fait de lames de bois est répandu en Afrique de l'Ouest et du Centre ?", "Le balafon", ["Le mvet", "Le violon", "La trompette", "La flûte de Pan", "La lyre"], "Le balafon est un xylophone africain.", 3),
]
for q, right, wr, expl, d in MIN:
    fq(CM, "cm-more", q, right, wr, expl, "cm-hydro", G, "Nature et ressources du Cameroun", d)
