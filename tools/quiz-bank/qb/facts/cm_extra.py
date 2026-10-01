"""Compléments Cameroun et Afrique : villes -> départements, régions frontalières, adhésions, plus grandes villes africaines.
Faits écrits par l'assistant : tous `review`."""
from ..facts_engine import fq, pairs, pick
from .cm_admin import DEPTS
from .cm_culture import G
from ..facts_engine import SOURCES

CM = "general"
ALL_DEPTS = [d for d, _, _ in DEPTS]

TOWN_DEPT = [("Mbanga", "Moungo"), ("Loum", "Moungo"), ("Obala", "Lékié"), ("Mbandjock", "Haute-Sanaga"), ("Saa", "Lékié"), ("Tiko", "Fako"), ("Muyuka", "Fako"), ("Idenau", "Fako"),
             ("Mutengene", "Fako"), ("Fontem", "Lebialem"), ("Bamusso", "Ndian"), ("Bali", "Mezam"), ("Bafut", "Mezam"), ("Batibo", "Momo"), ("Ndu", "Donga-Mantung"), ("Fundong", "Boyo"),
             ("Jakiri", "Bui"), ("Bamendjou", "Hauts-Plateaux"), ("Foumbot", "Noun"), ("Galim", "Bamboutos"), ("Figuil", "Mayo-Louti"), ("Lagdo", "Bénoué"), ("Pitoa", "Bénoué"),
             ("Rey-Bouba", "Mayo-Rey"), ("Waza", "Logone-et-Chari"), ("Maga", "Mayo-Danay"), ("Guidiguis", "Mayo-Kani"), ("Garoua-Boulaï", "Lom-et-Djérem"), ("Bétaré-Oya", "Lom-et-Djérem"),
             ("Lomié", "Haut-Nyong"), ("Ngoura", "Lom-et-Djérem"), ("Moloundou", "Boumba-et-Ngoko"), ("Ngaoundal", "Djérem"), ("Campo", "Océan"), ("Lolodorf", "Océan"), ("Akom II", "Océan"),
             ("Bipindi", "Océan"), ("Zoétélé", "Dja-et-Lobo"), ("Djoum", "Dja-et-Lobo"), ("Meyomessala", "Dja-et-Lobo"), ("Mintom", "Dja-et-Lobo")]
pairs(CM, "cm-town-dept", TOWN_DEPT, "Dans quel département du Cameroun se trouve la localité de {a} ?", None, region=G, cat="Géographie du Cameroun", src="cm-geo", diff=4,
      expl="{a} se trouve dans le département {b}.", extra_b=ALL_DEPTS)

BORDERS = [
    ("Quelle région du Cameroun est frontalière de la Guinée équatoriale ?", "Le Sud", ["Le Littoral", "Le Centre", "L'Est", "L'Ouest", "Le Nord"], "Le Sud touche la Guinée équatoriale, le Gabon et le Congo.", 3),
    ("Quelle région du Cameroun est frontalière du Tchad et du Nigeria ?", "L'Extrême-Nord", ["Le Sud-Ouest", "L'Est", "Le Centre", "Le Littoral", "L'Ouest"], "L'Extrême-Nord est bordé par le Nigeria et le Tchad.", 3),
    ("Quelle région du Cameroun est frontalière de la République centrafricaine et du Congo ?", "L'Est", ["Le Nord-Ouest", "Le Littoral", "L'Ouest", "Le Centre", "Le Sud-Ouest"], "L'Est touche la Centrafrique, le Congo et le Gabon (via le Sud-Est).", 3),
    ("Quelle région du Cameroun est frontalière du Nigeria à l'ouest et au sud-ouest ?", "Le Sud-Ouest", ["L'Est", "Le Centre", "Le Sud", "Le Littoral", "L'Adamaoua"], "Le Sud-Ouest et le Nord-Ouest bordent le Nigeria.", 3),
    ("Quelle région du Cameroun n'a aucune frontière avec un pays voisin ni avec l'océan ?", "Le Centre", ["Le Sud", "Le Littoral", "L'Est", "Le Sud-Ouest", "L'Extrême-Nord"], "Le Centre est entièrement à l'intérieur des terres, sans frontière internationale.", 4),
    ("Quelles régions du Cameroun ont une façade sur l'océan Atlantique ?", "Le Littoral, le Sud-Ouest et le Sud", ["L'Ouest, le Centre et l'Est", "Le Nord et l'Extrême-Nord", "L'Adamaoua et l'Est", "Le Nord-Ouest et l'Ouest", "Le Centre et l'Adamaoua"], "Trois régions bordent le golfe de Guinée.", 3),
    ("En quelle année le Cameroun a-t-il rejoint le Commonwealth ?", "1995", ["1961", "1972", "1984", "1990", "2005"], "Le Cameroun a adhéré au Commonwealth en novembre 1995.", 4),
    ("En quelle année le Cameroun est-il devenu membre de l'ONU ?", "1960", ["1945", "1957", "1961", "1972", "1990"], "Le Cameroun est admis à l'ONU en septembre 1960.", 3),
    ("Quelle organisation internationale francophone compte le Cameroun parmi ses membres ?", "L'Organisation internationale de la Francophonie", ["La Ligue arabe", "L'OTAN", "Le Mercosur", "L'ASEAN", "L'OPEP"], "Le Cameroun est membre de la Francophonie et du Commonwealth.", 3),
    ("Quelle langue de l'Afrique centrale est la langue officielle de la Guinée équatoriale voisine ?", "L'espagnol", ["Le français", "L'anglais", "Le portugais", "L'arabe", "Le swahili"], "La Guinée équatoriale a trois langues officielles : espagnol, français et portugais.", 3),
]
for q, right, wr, expl, d in BORDERS:
    fq(CM, "cm-borders", q, right, wr, expl, "cm-geo", G, "Géographie du Cameroun", d)

BIG = [("le Nigeria", "Lagos"), ("la Côte d'Ivoire", "Abidjan"), ("la Tanzanie", "Dar es Salaam"), ("le Bénin", "Cotonou"), ("le Maroc", "Casablanca"), ("l'Afrique du Sud", "Johannesburg"),
       ("le Soudan", "Omdourman")]
OTHER = ["Pretoria", "Le Cap", "Durban", "Mombasa", "Kano", "Abuja", "Yamoussoukro", "Porto-Novo", "Marrakech", "Rabat", "Lubumbashi", "Gitega", "Alexandrie", "Bobo-Dioulasso", "Thiès"]


def de(n):
    if n.startswith("le "):
        return "du " + n[3:]
    if n.startswith("la "):
        return "de la " + n[3:]
    if n.startswith("les "):
        return "des " + n[4:]
    return "de " + n


for n, c in BIG:
    fq(CM, "af-biggest-city", f"Quelle est la plus grande ville {de(n)} ?", c, pick("big" + n, [x[1] for x in BIG] + OTHER, c, 9), f"La plus grande ville {de(n)} est {c}.", "af-pays", "AF", "Afrique : pays et capitales", 3)
