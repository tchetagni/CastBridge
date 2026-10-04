# w23-02 — Suivi des activations : pages web d'administration `/admin/activations/**` (liste, fiche TV, tableau de bord, alertes, outils, intégrité, lectures), rafraîchissement par interrogation longue, adaptées au téléphone
<!-- routage architecte 2026-10-04 (W23) -->
> **Modèle : sonnet (4.6)** · escalade : audit Opus **obligatoire** (contrôle d'accès des routes web, CSRF, TOTP des actions sensibles, toute lecture auditée) · statut : **ATTEND w23-01**
> **Groupe : W23** (ordre 2, en parallèle de w23-03 et w23-04) · porte : `cd backend && ./mvnw -q test -Dtest='castbridge.server.activations.web.**'`
> **Jauge : ≈ 400 k jetons entrée / 25 k sortie** (effort M, ≈ 1,5 j) · exécutant le moins cher compétent : sonnet 4.6

**Conception** : `docs/coordination/DESIGN-W23-SUIVI-ACTIVATIONS-CONSOLE-2026-10-04.md` (§ 4.3, § 4.4, § 6.2, § 7.1, § 7.3). Branche `claude/w23-02-suivi-activations-pages`. Rapport : `docs/agent-reports/sonnet-w23-02.md`.

## Objectif (autonome)
Donner au propriétaire, **dans un navigateur d'ordinateur ou de téléphone**, la vue du serveur sur toutes les activations, en s'appuyant **uniquement** sur les services publiés par w23-01 (aucune requête SQL dans ce cahier) : ce qui est montré est ce que le serveur sait, daté (« à jour il y a X »).

## Fichiers possédés
- **Nouveaux** : `backend/src/main/java/castbridge/server/activations/web/{ActivationsPages,ActivationsPageModel,FreshnessFmt}.java` ; gabarits `backend/src/main/resources/templates/admin/act-{list,tv,dashboard,alerts,tools,integrity,reads,nav}.html` ; `backend/src/main/resources/static/admin/assets/act.js` (interrogation longue, mise à jour des lignes, aucune bibliothèque nouvelle ; `chart.umd.min.js` existant pour les courbes) ; tests `backend/src/test/java/castbridge/server/activations/web/**`.
- **Zone additive** : `templates/admin/layout.html` (une entrée de menu « Activations », visible avec `ACT_READ`) ; `static/admin/assets/admin.css` (règles préfixées `.act-`).
- **Interdit** : services et tables de w23-01 (consommés ; une méthode manquante ⇒ la demander), `B/licenses/**` (liens seulement), Android.

## Spécification
1. **Liste** `/admin/activations` : bandeau de compteurs (émises, activées, non constatées, remplacées, terminées, révoquées ; alertes par gravité ; fraîcheur des outils), filtres (état, édition, outil, période, drapeau, recherche par code d'appareil complet ou 4 derniers caractères, ou étiquette de 8 hex), colonnes du § 7.1, pagination par curseur (« Suivant »), boutons d'export (visibles seulement avec `ACT_EXPORT`, qui déclenche TOTP).
2. **Fiche TV** `/admin/activations/tv/{code}` : en-tête (code, version de CastBridge-TV, édition, fin d'usage, licence et poste avec leur état **séparé** de celui de l'activation, fraîcheur et voie du dernier contact, installations liées), **chronologie** complète (évènements de w23-01, libellés français, avant/après pour `LICENSE_CHANGED`), alertes de cette TV, **liens** vers les actions du module des licences (révoquer, libérer, réémettre) — aucune action destructrice dans ces pages. TV jamais vue : « Émise le … pour cette TV, jamais constatée · fenêtre close le … ».
3. **Tableau de bord** : compteurs édition × outil × état, activations par jour (30/90/365 j depuis `act_daily`), versions de CastBridge-TV parmi les TV activées, TV par fraîcheur (vert < 26 h, orange 26 h-7 j, rouge > 7 j, gris jamais), bandeau « Module des licences éteint : suivi partiel » si c'est le cas.
4. **Alertes** : file triée gravité puis date ; « Accuser réception » (`ACT_ALERT_ACK`) ; « Classer » (`ACT_ALERT_DECIDE`, motif obligatoire, TOTP).
5. **Outils** : par `kid` (type, dernier lot, entrées, trous, chaîne) ; **téléversement** d'un journal `.cbj` et rappel du lien vers l'import du registre existant.
6. **Intégrité** : `verify()` des deux chaînes à la demande, liste des points de contrôle, téléchargement `checkpoints.json` (`ACT_EXPORT`).
7. **Lectures** (OWNER) : journal d'audit des lectures, filtrable par acteur et période.
8. **Rafraîchissement** : `act.js` appelle `GET /api/v1/admin/activations/changes?after=&wait=25` **par la session web** (le service de w23-01 est exposé aussi sous `/admin/activations/changes`, même chaîne de sécurité que la page, CSRF inutile en GET) ; met à jour les lignes visibles et l'horodatage « à jour il y a X » (chaque seconde, côté client) ; > 5 min sans réponse ⇒ bandeau « Connexion perdue : données au … ».
9. **Téléphone** : mise en page utilisable à 360 px (liste en cartes, filtres repliables, chronologie verticale) : c'est **la** vue du suivi depuis le téléphone du propriétaire tant que D-W23-3 = B.
10. **Gel** : ces pages ne sont pas distribuées aux consommateurs ; recommandation de la conception : le gel ne s'y applique pas (D-W23-7) ; si le propriétaire répond non, ce cahier attend.

## Critères d'acceptation (mutations au rapport)
- Matrice rôle × page × action (READONLY, SUPPORT, OWNER avec et sans TOTP) : chaque refus attendu (403 ou redirection vers TOTP), chaque bouton absent quand la permission manque **et** la route refusée même appelée à la main (mutation : cacher le bouton sans vérifier côté serveur ⇒ échec).
- CSRF : tout `POST` sans jeton CSRF ⇒ refus.
- Chaque affichage de page de lecture produit **une** ligne d'`adm_read_audit` (assertion par page ; mutation : contourner le service audité ⇒ échec).
- Aucun jeton, clé compacte ni code complet dans le HTML de la liste (le code complet n'apparaît que sur la fiche TV, pour un rôle qui a `ACT_READ`).
- Rendu à 360 px : test de gabarit (présence des classes de mise en page mobile) ; capture manuelle facultative dite au rapport.
- `act.js` : test JS minimal sans dépendance nouvelle (ou test MockMvc du point d'interrogation longue : réponse vide au bout de `wait`, évènement rendu dès qu'il existe).

## À ne pas faire
- Ajouter une action destructrice dans ces pages ; lire la base directement ; afficher un jeton ou une clé compacte ; charger une bibliothèque externe.
