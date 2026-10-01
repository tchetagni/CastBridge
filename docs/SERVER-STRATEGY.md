# Stratégie serveur (bridge.sti-cm.com)

Accès : le propriétaire a donné au coordinateur (session locale) un accès total pour la mise en œuvre du projet, pendant 3 mois (annonce du 2026-10-01 ; le premier accord courait jusqu'au 2026-11-30 : **à reconfirmer la date de fin exacte**). Les agents cloud n'ont PAS d'accès au serveur : ils spécifient et écrivent le code dans le dépôt, le coordinateur déploie.
Limites que je m'impose : rester dans le projet CastBridge (conteneurs `castbridge-*`, dossier du projet), ne jamais lire ni modifier les autres projets de la machine ; **toute modification de l'infrastructure partagée (nginx commun, certbot) se fait avec sauvegarde, test de configuration, et accord explicite du propriétaire** car elle touche d'autres services en production.

## État mesuré le 2026-10-01 (lecture seule)
- Machine : Ubuntu 24.04, 7,8 Go de RAM (≈ 4,6 Go disponibles), disque 145 Go (**82 Go libres**), en service depuis 22 semaines, **partagée** avec d'autres projets en production.
- `castbridge-api` (Spring, image `castbridge-api:current`, limite mémoire 512 Mo, `127.0.0.1:7090`) et `castbridge-db` (MySQL 8.4, 512 Mo) : en bonne santé. Volumes : `castbridge-apk` (33 Mo), `castbridge-mysql` (213 Mo).
- Projet dans `/home/ubuntu/castbridge/services/castbridge/backend` (pas un dépôt Git sur le serveur : déploiement par `deploy.sh`, sauvegarde par `backup.sh`). Le HTTPS public répond déjà par l'API (le nginx partagé relaie).
- **La version déployée date du 2026-09-30** : elle ne contient pas les contrôleurs de lots, de contenu et de droits qui existent dans le dépôt (`LotController`, `AdminLotController`, `ContentApiController`, `QuizPackController`…).

## Rôles du serveur dans la stratégie
1. **Magasin et distribution des lots** (Apprendre, Quiz, Langues) : le serveur ne produit rien. La base est préparée dans le dépôt privé `castbridge-content`, puis copiée (`rsync` avec empreintes) vers un dossier de préparation, vérifiée (budgets, `CONTENT-RELEASE.json`, `LOT-VERSIONS.json`), puis activée d'un coup (lien symbolique) ; retour arrière en un geste. Volume prévu : ≈ 3 Go (hors langues) + 6 Go (langues) + marge, soit ≈ 12 Go sur 82 Go libres.
2. **Distribution efficace des gros fichiers** (médias, langues) : jamais par la JVM (512 Mo). Le serveur web sert les fichiers (Range, ETag) via des **liens signés à courte durée** émis par l'API après contrôle des droits. Cela exige un bloc dédié dans le nginx partagé : **à ne faire qu'avec ton accord** (sauvegarde, `nginx -t`, rechargement sans coupure).
3. **Droits et monétisation** : essai gratuit (100 Mo), achat à la carte (lot/bouquet, définitif), abonnement (date de fin, période de grâce hors ligne). Tables MySQL (produits, bouquets, droits, abonnements, appareils), **jetons signés** (clé Ed25519 distincte de celle des mises à jour), liés à l'appareil. C'est la **vraie protection** : le serveur ne livre les lots payants qu'aux appareils autorisés. Les prix et le prestataire de paiement restent à décider par le propriétaire (le paiement par mobile money passera par un agrégateur : à choisir).
4. **Mises à jour des applications** (déjà en place) : canal bêta/stable, signature Ed25519, installation par l'application téléphone.
5. **Télémétrie et retours** : signalement d'erreur de contenu (« bêta : non validé »), indicateurs, effacement RGPD (`DELETE /api/v1/devices/me` existe).

## Feuille de route (ordre proposé, chaque étape réversible)
1. **Sauvegarde avant tout** : dump MySQL + étiquette de l'image courante (`current` → `previous`) + copie de `.env` hors dépôt. Vérifier que `backup.sh` est planifié.
2. **Déployer le backend actuel du dépôt en préproduction** (port local 7091, base de test) et le comparer à la production ; basculer seulement si les tests passent ; `previous` conservée pour revenir.
3. **Magasin de contenu** : volume `castbridge-content`, script d'ingestion `rsync` + vérification + bascule atomique, rapport des tailles.
4. **Service de droits** (après la conception de l'agent `trial-edition`) : tables, jetons, routes, tests ; zéro paiement tant que le prestataire n'est pas choisi.
5. **Distribution par liens signés** + bloc nginx dédié (accord du propriétaire).
6. **Exploitation** : surveillance (état de santé, disque, mémoire, certificats), rotation des journaux, limites de débit, sauvegardes testées par restauration, limites de ressources adaptées (la JVM à 512 Mo suffit si les médias ne passent pas par elle).

## Risques à surveiller
Machine partagée (mémoire et disque communs), limites de 512 Mo des conteneurs, aucun dépôt Git sur le serveur (déploiement à partir du dépôt GitHub : tracer la version déployée), fin de l'accord dans 3 mois au plus.
