# Brief (REMPLACÉ le 2026-10-01) : entrée « Super administration » locale des applications du téléphone

**Décision du propriétaire** : le mot de passe superadmin **déverrouille l'APK du téléphone exclusivement et n'a aucun lien avec le serveur**. L'ancienne conception (vérification par le serveur, TOTP, jeton court) est **annulée**. **EN ATTENTE** de deux confirmations (ci-dessous) ; ne pas lancer avant.

## Conception retenue (locale)
1. **Le mot de passe ouvre l'écran, il ne donne pas le pouvoir.** Le pouvoir vient du **coffre de clés propre à chaque téléphone** (créé par le propriétaire, chiffré par son code, jamais livré dans l'APK : `core/.../owner/OwnerVault.kt`, `ActivationIssuer`). Un APK modifié qui saute la vérification n'obtient qu'une interface vide.
2. **Haché dans l'APK, pas dans le dépôt** : le haché bcrypt (coût 12) est lu **à la compilation** depuis un fichier protégé hors dépôt (`~/.castbridge-signing/superadmin.bcrypt`, droits 600) et injecté en `BuildConfig` ; **si le fichier est absent, l'entrée cachée est désactivée** (jamais de haché de secours dans le code). Bibliothèque bcrypt sous licence permissive (Apache-2.0 ou ISC), notice à jour dans `NOTICE`.
3. **Limite d'essais sur le téléphone** : `UnlockGuard` (délais croissants puis verrouillage temporaire, état persisté), **verrouillage automatique** (2 min d'inactivité ou sortie de l'écran), `FLAG_SECURE`, aucune trace du mot de passe (ni journal, ni presse-papiers, ni sauvegarde).
4. **Entrée cachée** : combinaison de gestes paramétrable à la compilation (par exemple 7 appuis sur le titre puis un appui long), aucune mention dans l'interface ni la documentation publique ; limite de tentatives.
5. **Écran « Super administration »** = la console propriétaire (`:owner`) intégrée à l'application du téléphone derrière cette porte (activation complète ou compacte, clé publique, journal) ; **les clés de signature restent par téléphone** ; sans coffre, l'écran propose d'en **créer un** (code neuf) : une clé que **aucune TV ne reconnaît** tant qu'elle n'a pas été **ajoutée à l'anneau des clés de confiance** : sans danger pour les autres installations.
6. **Limites à écrire dans la documentation** : (a) le haché dans l'APK permet de chercher le mot de passe **hors ligne** : seule sa **longueur et son hasard** protègent (16 caractères ou plus, idéalement une phrase de passe) ; (b) la vérification locale se **contourne** : c'est pourquoi le pouvoir réel repose sur le coffre, pas sur elle ; (c) pas de verrouillage à distance, pas de journal côté serveur, pas de limite d'essais commune à tous les appareils ; (d) le **bureau** (`castbridge-activation-desktop`) a son propre coffre protégé par scrypt et ne dépend pas de ce mot de passe.
7. **Tests** (JVM) : mauvais mot de passe, bon mot de passe, blocage après erreurs et reprise, verrouillage automatique, entrée cachée (bonne et mauvaise séquence, délai), fichier de haché absent = fonction désactivée, aucune fuite du mot de passe dans les journaux ; compilation Android par le coordinateur.

## À ne pas faire
Mettre le haché dans le dépôt ; livrer une clé de signature ou un coffre dans l'APK ; créer un lien avec le serveur ; afficher la moindre indication de l'entrée cachée.

## Questions ouvertes pour le propriétaire
1. Quelles applications du téléphone : CastBridge et la console propriétaire, ou aussi d'autres applications (hors CastBridge) ?
2. Le mot de passe est-il **long (16 caractères ou plus) et jamais saisi dans une conversation** ? C'est ce qui le protège ici.

## Réponses du coordinateur
(aucune pour l'instant)
