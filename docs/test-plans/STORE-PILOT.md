# Liste humaine de la Boutique (pilote, 10 points) : téléphone CastBridge et TV CastBridge-TV

> **Date :** ____ · **Versions installées** (écran « Version ») : CastBridge ____ · CastBridge-TV ____ · **Qui exécute :** ____ · **Heure début / fin :** ____ / ____
> Préalable : `tools/agents/gate-w17.sh` est **VERT** (rapport joint) et la fumée `tools/smoke/` est **PASS** ; sinon, ne pas commencer.
> Matériel : TV de référence (GaiaOS 32 bits) allumée depuis plus de 2 min, téléphone S21+ (utilisateur principal), accès Internet sur le téléphone, la TV et le téléphone sur le même Wi-Fi. **La TV du propriétaire n'est jamais la cible d'un script** : le point 7 se fait sur l'émulateur ou une TV d'essai dédiée avec une clé d'essai de TEST.
> **Aucun secret** dans le compte rendu (jamais le PIN, jamais une clé). Pour chaque **échec** : le point, ce qui s'affiche (photo ou capture), l'heure ; puis `docs/REGRESSIONS.md`.

| # | Appareil | Pré-condition | Geste | Résultat attendu observable | OK / KO |
|---|---|---|---|---|---|
| 1 | Téléphone | Drapeau `store.enabled` éteint (état de départ) | Ouvrir CastBridge, regarder la barre du haut | « Activer la TV · Locations » ; **aucune** entrée Boutique | ☐ |
| 2 | Téléphone, puis TV | Le bureau envoie l'ordre signé `flag.set store.enabled=1` ; téléphone et TV reçoivent l'ordre | Rouvrir CastBridge ; allumer la TV | Téléphone : « Boutique » à la place de « Locations ». TV : tuile « Boutique » placée après « Langues » | ☐ |
| 3 | Téléphone | Internet disponible | Boutique › « Actualiser » | Bandeau « Catalogue du JJ/MM » (date du jour ou la plus récente) ; rayons Apprendre, Langues, Quiz présents | ☐ |
| 4 | Téléphone et TV | Point 3 fait ; téléphone et TV sur le même Wi-Fi | Laisser le téléphone se connecter à la TV ; ouvrir la tuile Boutique de la TV | TV : le **même** « Catalogue du JJ/MM », les **mêmes** articles et les **mêmes** états que sur le téléphone | ☐ |
| 5 | Téléphone, puis TV | CM2 n'est pas encore sur la TV (« Pas sur la TV ») | Téléphone : « Envoyer à la TV » sur CM2 ; attendre la fin | Téléphone : progression puis « Sur la TV ». TV : la carte CM2 dit « Sur cette TV » | ☐ |
| 6 | TV, puis téléphone | CM2 louable, TV et téléphone à portée | TV : « Louer » CM2 · « 12 heures d'utilisation » ; lire l'écran ; téléphone : notification « La TV demande … » › Confirmer ; au bureau W16 : `louer.py --demande` | TV : écran « Demande enregistrée » + code court **lisible à 3 m**. Téléphone : notification puis confirmation. Après livraison de la location : TV « Loué · il vous reste 12 h d'utilisation » | ☐ |
| 7 | TV d'essai (émulateur, **pas** la TV de référence) | Clé d'essai de TEST installée sur l'émulateur ; catalogue relayé | Ouvrir la Boutique ; essayer « Louer » | Vitrine **visible** ; chaque carte « Version complète nécessaire » ; aucune demande créée | ☐ |
| 8 | TV (profil enfant), téléphone | Profil enfant actif sur la TV | TV : « Louer » sur un article louable | TV : « Demandez à un parent » ; **aucune** ligne de demande ; le téléphone ne reçoit **rien** (pas de notification) | ☐ |
| 9 | TV, puis téléphone | Une demande « en cours » créée sur la TV ; téléphone éteint 24 h | Rallumer le téléphone, l'approcher de la TV | La TV garde la vitrine et la demande reste « en cours » pendant l'absence ; au retour le téléphone la **relève** (notification) | ☐ |
| 10 | TV (propriétaire) | TV de référence ; `adb` seulement par le propriétaire, jamais par un agent | `dumpsys meminfo` de CastBridge-TV avant, puis pendant la Boutique ouverte ; ouvrir la tuile Boutique | Écart de mémoire **< 1,5 Mo** ; ouverture **< 300 ms** ressentie | ☐ |

**Durée :** ≈ 20 min (le point 9 se prépare la veille).

Verdict : **10/10** ⇒ le pilote de la Boutique peut continuer ; un échec sur **1-6** ⇒ **STOP** : drapeau éteint par ordre signé, ligne dans `docs/REGRESSIONS.md`, test ajouté avant tout correctif ; un échec sur **7-10** ⇒ pilote possible **avec** la ligne de régression ouverte et l'accord explicite du propriétaire.

Compte rendu (5 lignes, dans `docs/agent-reports/store-pilot-<version>.md`, sans secret) : versions CastBridge et CastBridge-TV, heure début/fin, points KO avec photo, verdict, qui a testé.
