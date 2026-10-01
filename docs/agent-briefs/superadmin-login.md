# Brief : entrée « Super administration » des applications du téléphone, authentifiée par le serveur

**EN ATTENTE** de (1) la confirmation du propriétaire des trois questions ci-dessous et (2) la fin de `license-admin-cbx1`. Ne pas lancer avant. Base `origin/integration/agents`. Branche `claude/superadmin-login`. Protocole `docs/COORDINATION.md`. Français, aucun secret.

## Décision du propriétaire (2026-10-01)
Toutes les applications du téléphone doivent ouvrir l'interface de super administration avec **son mot de passe**. Le serveur détient déjà le haché bcrypt (coût 12) dans `…/backend/secrets/owner-password.bcrypt` (droits 400). **Le mot de passe en clair a été exposé dans une conversation : il doit être considéré comme connu ; un second facteur est donc obligatoire.**

## Conception retenue (le serveur est la serrure ; ne l'altère pas sans le dire)
1. **Aucun secret dans les APK** : ni mot de passe, ni haché, ni vérification locale. Une vérification faite dans l'application se contourne et ne protège rien.
2. **Serveur** (module licences de `backend/`) : `POST /api/v1/superadmin/login` (mot de passe + code TOTP), comparaison `BCryptPasswordEncoder` avec le haché lu dans le dossier des secrets (jamais dans l'image, jamais dans les journaux) ; **TOTP** obligatoire (sans service externe, clé dans les secrets) ; **limites strictes** (par appareil et par IP : délais croissants puis blocage temporaire, comptées côté serveur, journal d'audit chaîné sans le mot de passe ni le code) ; réponse = **jeton court (15 min)** lié à l'appareil, renouvelable seulement par un nouveau couple mot de passe + code ; `POST /api/v1/superadmin/logout` ; routes d'administration des licences ouvertes **seulement** avec ce jeton (et rôle `propriétaire`) ; interrupteur de fonctionnalité éteint par défaut ; temps de réponse constant pour un utilisateur inconnu ou un mauvais mot de passe.
3. **Applications du téléphone** (code Android minimal, le coordinateur le compile) : **entrée cachée** (combinaison de gestes : par exemple 7 appuis sur le titre puis un appui long, paramétrable à la compilation), écran « Super administration » avec mot de passe + TOTP, appel HTTPS vers le serveur, jeton gardé en mémoire seulement (jamais sur disque), **verrouillage automatique** (2 minutes d'inactivité, sortie de l'écran), interdiction de capture d'écran, écran = administration du serveur (licences, postes, émission d'activations avec la clé serveur). Aucun rôle donné par l'application : elle affiche ce que le serveur autorise.
4. **Limite à écrire dans la documentation** : la clé du serveur ne peut ni transférer ni « tout ouvrir » ; ces pouvoirs exigent la **clé locale** (coffre du téléphone propriétaire, déverrouillé par le code) : deux niveaux distincts.
5. **Tests** : mauvais mot de passe, bon mot de passe sans TOTP, TOTP rejoué, jeton expiré, jeton d'un autre appareil, limites et blocage, journal d'audit sans secret, interrupteur éteint = 404, comparaison à temps constant, aucune trace du mot de passe ni du haché dans les journaux ; `./mvnw clean test`.
6. **À ne pas faire** : mettre le haché ou le mot de passe dans un APK, dans le dépôt ou dans les journaux ; brancher le haché au compte administrateur **en service** (décision du propriétaire, après confirmation qu'il possède bien le mot de passe correspondant) ; ouvrir un accès sans TOTP.

## Questions ouvertes pour le propriétaire
1. Quelles applications : CastBridge (téléphone) et la console propriétaire, ou aussi d'autres applications hors CastBridge ?
2. « Super administration » = administration du serveur (licences, émissions) depuis le téléphone : confirmé ?
3. TOTP obligatoire : confirmé ?

## Réponses du coordinateur
(aucune pour l'instant)
