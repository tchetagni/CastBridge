# H-PLAY-POC : relevé humain du Quiz en ligne (POC « TV à TV avec relais »)

> Cahier `w20-05` (écrans TV). Aucun appareil n'a été touché par l'agent : ce relevé est fait par le propriétaire, sur de vraies TV activées et le service public déployé.
> Prérequis côté service : voir « Ce que la production doit avoir » dans `docs/agent-reports/sonnet-w20-05.md`.

## Avant
1. APK CastBridge-TV **verrouillé** (`-PrequireActivation=true`) installé sur deux TV (A hôte, B invitée), activations de production ou d'essai en place, heure juste.
2. TV A sur la box. TV B reliée à Internet par la **passerelle Bluetooth** du téléphone G (variante de secours : point d'accès Wi-Fi d'un téléphone).
3. Téléphone A1 apparié à la TV A, B1 à la TV B (comme aujourd'hui, aucune nouvelle app).
4. Le réglage « Quiz en ligne » est allumé par défaut (menu de la TV : « Quiz en ligne : activé »). Rien à manipuler.

## Pas à pas (10 minutes)
| t | Geste | Attendu |
|---|---|---|
| 0:00 | TV A : Quiz ▸ « Partie Internet » ▸ « Créer une partie » | bandeau « ◎ Internet · … » ; code `XXXX-XXXX` en grand ; « Ici : aucun joueur · 8 places libres » |
| 1:00 | A1 : application CastBridge ▸ Quiz ▸ pseudonyme | TV A : « Ici : 1 joueur » |
| 2:00 | TV B : Quiz ▸ « Partie Internet » ▸ « Rejoindre avec un code » ▸ saisir le code à la grille ▸ « Valider » | ouverture en 4 à 6 s en liaison lente ; bandeau **orange** « Internet par le téléphone (Bluetooth) · lent » (dégradé, pas une panne) |
| 3:00 | B1 : application ▸ Quiz ▸ pseudonyme | « Ici : 1 joueur » sur B ; 2 joueurs au total |
| 3:30 | TV A : « Commencer » | question sur les deux TV, pas de réponse avant la fin |
| 3:30-8:00 | A1 et B1 répondent | classement commun après chaque question |
| 5:30 | (option) éloigner G 15 s | TV B : orange « liaison en reprise (N s) » puis retour ; la partie continue |
| 8:00 | fin | classement final identique |
| 8:30 | navigateur ▸ `https://bridge.sti-cm.com/play` | page d'information, aucune saisie de code |

## Cas à constater aussi
- Réglage « Quiz en ligne : désactivé » dans le menu de la TV ⇒ la carte « Partie Internet » disparaît du Quiz.
- TV sans Internet ⇒ carte grisée « Connexion Internet requise » (jamais vide).
- Service arrêté ⇒ carte grisée « Quiz en ligne : service indisponible ».
- Profil enfant actif ⇒ carte grisée avec le texte du contrôle parental, même avec le code parental.
- TV non activée ⇒ « Activez la TV pour créer une partie Internet ».
- Coupure de la box de la TV B > 60 s ⇒ « Partie Internet perdue », bouton « Retour au Quiz ».

## À relever (pour décider de la phase 2)
Durée d'ouverture de la salle (s), délai entre la réponse sur le téléphone et son accusé (s, objectif 1,5 s non tenu au plancher 40 kbps selon w20-05a), fluidité de la question suivante, comportement à la coupure de G.
