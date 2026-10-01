# Brief : outils de génération des jetons d'activation (Mac/Windows/Linux, téléphone propriétaire, serveur)

Agent cloud (à lancer quand `claude/trial-edition` aura poussé le **format filaire** des activations : `docs/ACTIVATION-FORMAT.md` + vecteurs de test). Base `origin/integration/agents` + cette branche. Branche `claude/activation-tools`. Protocole `docs/COORDINATION.md` (rapport vivant `docs/agent-reports/activation-tools.md`). Pas de PR, pas de main, rien sur le serveur de production. Français, aucun secret.

## Demande du propriétaire
Un outil qui, à partir des **identifiants fournis en entrée** (code d'appareil de la TV, droits, durée), **génère les jetons d'activation** : (1) une **application de bureau Mac, Windows et Linux** ; (2) la partie **superadmin de l'application téléphone** (version propriétaire, jamais publiée) ; (3) le **serveur**. Les trois doivent produire des jetons **identiques et acceptés par la TV**.

## Principe : une seule définition, trois habillages
- **Une spécification et des vecteurs de test communs** (`docs/ACTIVATION-FORMAT.md`, `tools/activation/test-vectors.json`) : mêmes entrées → mêmes octets. Chaque outil passe les mêmes vecteurs (signature Ed25519 déterministe, codage, somme de contrôle). **Ne jamais écrire trois formats.**
- **Bibliothèque émettrice** `ActivationIssuer` dans `core` (JVM, testée) : entrées = code d'appareil (avec les empreintes de facteurs), droits (essai, à la carte : lots/bouquets, abonnement, « tout ouvert » ≤ 30 jours), durée (≤ 1 an hors ligne), identifiant de clé (`kid`), nonce ; sortie = jeton signé + codages (texte saisissable par groupes avec somme de contrôle, fichier `activation`, charge utile Bluetooth). Refuse toute entrée invalide (code d'appareil mal formé, durée hors bornes, droit inconnu).
- **Une clé par outil, pas une clé partagée** (moindre privilège) : clé **bureau** (maître, hors ligne, coffre), clé **téléphone propriétaire** (terrain), clé **serveur** (phase en ligne, **sans** droit « tout ouvert », seulement essai et achats). La TV accepte un **ensemble de clés publiques avec leur portée** (`kid`, droits permis, révocation). Compromission d'un outil = révoquer sa seule clé.

## À livrer
1. **Bureau (Mac, Windows, Linux)** : application JVM (Java 17+) avec **interface graphique simple et ligne de commande** : saisie du code d'appareil, choix des droits et de la durée, déverrouillage de la clé (code du propriétaire + dérivation gourmande en mémoire, jamais stocké), jeton en texte à copier, en **fichier `activation`** (pour la clé USB de la TV) et en **code QR** (à lire sur le téléphone propriétaire), **journal** des jetons émis (date, TV, droits, durée ; jamais la clé). Emballage : JAR exécutable multiplateforme **et** installeurs natifs par `jpackage` ; **dis honnêtement** que chaque installeur doit être construit sur son système (Mac ici ; Windows/Linux via un flux GitHub Actions que tu écris et que le propriétaire activera). Tests JVM + vecteurs.
2. **Téléphone propriétaire (spécification et noyau testé ; l'écran Android sera compilé par le coordinateur)** : écran « Générer un jeton » dans la console propriétaire (gabarit `owner`, autre identifiant d'application, non publié) : saisie ou **lecture du code d'appareil** (saisie, QR, ou reçu par Bluetooth de la TV), choix des droits, déverrouillage, **envoi direct à la TV par Bluetooth**, copie, fichier. Clé de signature dans le coffre du téléphone + code (voir cahier `trial-edition`).
3. **Serveur** : route d'administration (authentifiée par le jeton d'administration existant, journalisée, limitée en débit) `POST /api/v1/admin/activations` et `GET /api/v1/admin/activations` (liste) dans `backend/` : même format, mêmes vecteurs (Java : Ed25519 du JDK), clé serveur lue dans le dossier des secrets (jamais en clair dans l'image ni les journaux). Pas de paiement. Le coordinateur déploie en préproduction (port 7091) avant toute bascule ; ne touche ni au déploiement ni au serveur partagé.
4. **Vérificateur côté TV** (si `trial-edition` ne l'a pas déjà fait) : acceptation par `kid` et portée, révocation, nonce, expiration, tolérance d'horloge, mauvaise TV, jeton altéré ; tests dans `core`.
5. **Sécurité** : les trois outils partagent la bibliothèque mais **pas les clés** ; rien de secret dans le dépôt ; rapport de risques (le bureau détient la clé maîtresse : où et comment la sauvegarder hors ligne ; le téléphone est volable ; le serveur est exposé) et procédure de **rotation/révocation**.
6. Docs : `docs/ACTIVATION-TOOLS.md` (guide d'usage pour le propriétaire : comment activer une TV, pas à pas), `docs/HANDOFF.md`.

## Coordination
Rapport vivant ; relis la section ci-dessous à chaque jalon.

## Réponses du coordinateur
(aucune pour l'instant)
