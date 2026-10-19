# Architecture de CastBridge : page et diagrammes BPMN 2.0

La page `ARCHITECTURE.html` décrit les produits, les modules, les protocoles, les données, la sécurité, le déploiement et la méthode. Sa section 10 montre dix
processus en BPMN 2.0, rendus par bpmn-js (`bpmn-viewer.production.min.js` 17.11.1, depuis unpkg). Les diagrammes sont aussi des fichiers versionnés, ouvrables
tels quels dans Camunda Modeler ou sur bpmn.io.

## Fichiers

| Chemin | Rôle |
|---|---|
| `docs/architecture/ARCHITECTURE.html` | la page (un seul fichier, sans enveloppe `html` ni `head` : contrat des artefacts) |
| `docs/architecture/bpmn/<id>.bpmn` | un diagramme BPMN 2.0 par processus (collaboration, pools, lanes, diagramme `bpmndi` complet) |
| `tools/architecture/processes.py` | la description compacte des dix processus : acteurs, nœuds, flux de séquence, flux de message, résumé, repères dans le dépôt |
| `tools/architecture/bpmn_gen.py` | le générateur : mise en page, fichiers `.bpmn`, cartes et XML insérés dans la page |

Les dix processus : `copie-fichier`, `lien-confiance`, `activation-tv`, `lecture-video`, `mise-a-jour-tv`, `publication-version`, `quiz-en-ligne`,
`jetons-et-lots`, `assistance-a-distance`, `controle-parental`.

## Régénérer

```
python3 tools/architecture/bpmn_gen.py            # écrit les .bpmn et met la page à jour
python3 tools/architecture/bpmn_gen.py --check    # échoue si un fichier ou la page n'est pas à jour (aucune écriture)
python3 tools/architecture/bpmn_gen.py --verify   # contrôle la structure des .bpmn déjà écrits
python3 tools/architecture/bpmn_gen.py --only copie-fichier --lint   # un seul processus, avec les avertissements de mise en page
```

La page contient trois zones générées, entre marqueurs : `<!-- BPMN-CARDS:BEGIN -->` et `<!-- BPMN-CARDS:END -->` (les cartes de la section 10 : titre, résumé,
viewer, tableau des repères), `<!-- BPMN:BEGIN -->` et `<!-- BPMN:END -->` (le `<script>` qui porte `const BPMN_XML = { id: "…xml…" }`). Tout le reste de la page
est écrit à la main. Ne modifiez jamais une zone générée : changez `processes.py`, puis régénérez.

## Décrire un processus

`processes.py` décrit chaque processus par des lignes : `id | lane | type | libellé | options | documentation` pour un nœud, `a -> b : libellé` pour un flux de
séquence (un libellé qui finit par `*` marque le flux par défaut d'une passerelle), `a => b : libellé` pour un flux de message entre deux pools. L'en-tête du
fichier liste les types (début, fin, tâches, passerelles exclusive, parallèle, inclusive et à événements, événements intermédiaires et limites). Le chargeur refuse
une référence inconnue, un nœud sans flux, un flux de séquence entre deux pools ou un flux de message dans un même pool. Un processus compte de 12 à 30 nœuds.

## Mise en page (déterministe)

- Rang d'un nœud : plus long chemin depuis les débuts (flux de séquence +1, flux de message +0, boucles retirées par un parcours en profondeur), puis rangs compactés.
  Colonnes de 190 px : `x = 80 + rang × 190` (bord gauche d'une tâche de 110 × 80 ; passerelle 50 × 50 ; événement 36 × 36).
- Lanes empilées dans leur pool (hauteur de 150 px au moins, une rangée de 110 px par ligne de nœuds) ; pools empilés, de même largeur, dont l'écart croît avec
  le nombre de flux de message qui le traversent.
- Flux orthogonaux, tracés par un A* sur une grille de 5 px qui évite les nœuds et les libellés, pénalise les virages, les superpositions et les croisements. Un
  flux sort à droite et entre à gauche ; une passerelle utilise ses sommets haut et bas pour les autres branches ; un retour en arrière passe par le bas ; un flux
  de message part du bas ou du haut de sa tâche.
- Couleurs par famille d'acteur (propriété `bioc:fill` et `bioc:stroke`) : téléphone vert, TV ambre, serveur bleu-gris, clé USB indigo, Mac turquoise,
  utilisateur gris. Le viewer a un fond clair dans les deux thèmes de la page.

## Vérifications

`--verify` contrôle pour chaque fichier : XML bien formé, identifiants uniques, `bpmnElement` résolus, un `BPMNShape` par nœud, pool et lane (pools et lanes en
`isHorizontal="true"`), un `BPMNEdge` par flux, segments orthogonaux dont les extrémités sont sur le bord de leurs formes, flux de message entre deux pools.

## Notes

- **Filigrane bpmn.io.** Le viewer affiche par défaut un logo bpmn.io. Il est masqué par CSS (`.bjs-powered-by`) à la demande du propriétaire, et la section 10 cite bpmn-js (bpmn.io). La licence de bpmn.io demande de conserver ce filigrane : à revoir avant toute diffusion publique de la page.
- **Page sans enveloppe.** La page commence par `<title>` puis `<style>` et n'a ni `<html>`, ni `<head>`, ni `<body>` : c'est le contrat des artefacts. Les couleurs sont des jetons de `:root`, redéfinis pour le thème sombre (`@media (prefers-color-scheme: dark)` et `:root[data-theme="dark"]`) ; le fond du viewer BPMN (`--diagram-bg`) reste clair dans les deux thèmes.
- **Scripts externes.** Un seul : `https://unpkg.com/bpmn-js@17.11.1/dist/bpmn-viewer.production.min.js`. Les feuilles `diagram-js.css` et `bpmn-js.css` du même paquet sont recopiées, minifiées et sans règle qui charge une ressource, dans le `<style>`. Mermaid est fourni par la plateforme (`<pre class="mermaid">`).
- **Boutons.** « Copier le XML » écrit dans le presse-papiers depuis le gestionnaire de clic (`navigator.clipboard.writeText`) et, s'il est refusé, sélectionne le XML dans une zone de texte. Il n'y a aucun lien de téléchargement.
