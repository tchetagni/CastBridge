# Liste humaine de livraison (≤ 15 min, 12 points) : ce que seule la vraie TV confirme

> À faire par le propriétaire ou un testeur **avant** qu'une APK « candidate » remplace la build de la TV de référence (GaiaOS 32 bits) et **avant** toute copie dans le `Download` de la clé USB. Préalable : la suite de fumée `tools/smoke/` est **PASS** (rapport joint) et la liste JVM est verte ; sinon, ne pas commencer.
> Matériel : TV de référence allumée depuis > 2 min, téléphone S21+ (utilisateur principal, pas « Dual App »), clé USB exFAT branchée, un fichier vidéo de ~5 Mo et un de ~500 Mo sur le téléphone, Telegram avec une vidéo reçue. Chronomètre : noter l'heure de début. **Aucun secret** dans le compte rendu (jamais le code PIN, jamais une clé).
> Règle : **mise à jour par-dessus** (même signature, numéro supérieur), **jamais** de désinstallation de CastBridge-TV (elle efface les téléphones de confiance et change le code). Garder l'APK précédente sur la clé (retour arrière : `docs/RELEASES.md` § 10).

Cocher, et pour chaque **échec** noter : le point, ce qui s'affiche (photo de la TV, capture du téléphone), l'heure ; puis `docs/REGRESSIONS.md` (le coordinateur ouvre la ligne).

| # | Point (parcours) | Faire | Attendu (téléphone / TV) | Durée | OK |
|---|---|---|---|---|---|
| 1 | Mise à jour en place conserve tout (P-28) | Installer la candidate TV **par-dessus** (clé USB `Download/` ou `cbdev install`) ; rouvrir CastBridge-TV | TV : badge d'activation **inchangé** (ESSAI/PRODUCTION), même code à l'accueil, bibliothèque complète, « Téléphones » liste toujours le S21+. Téléphone : puce **verte** sans « Réassocier » | 2 min | ☐ |
| 2 | Démarrage à froid après redémarrage (P-31) | Éteindre/rallumer la TV à la prise ; attendre 90 s | TV : CastBridge-TV revient seule à « Prêt à recevoir ». Téléphone : puce rouge puis **verte** sans toucher l'app | 2 min | ☐ |
| 3 | Copie LAN 5 Mo avec progression des deux côtés (P-11, P-36) | TV › Envoyer › vidéo de 5 Mo | Téléphone : notification « <nom> · NN % » qui monte, puis « Terminé ». TV : **carte de réception** avec % pendant la copie (pas « Prêt à recevoir » figé), puis fichier dans sa catégorie | 1 min | ☐ |
| 4 | Copie LAN 500 Mo, débit et fin (P-12) | Envoyer la vidéo de 500 Mo ; regarder 30 s, puis laisser finir | Téléphone : % monotone, débit affiché, aucune pause muette > 20 s. TV : carte avec %, puis lecture du fichier possible depuis la bibliothèque | 3 min (en fond pendant 5-7) | ☐ |
| 5 | « Ouvrir avec CastBridge » depuis Telegram (P-22, P-23) | Telegram › vidéo › Partager › CastBridge | Boîte : nom de la TV, « Copier » **actif** (jamais « Aucune TV ajoutée » alors que l'accueil dit « Connectée ») ; la copie part et la notification suit | 1 min | ☐ |
| 6 | Coupure TV pendant une copie, reprise (P-16) | Pendant le point 4 : éteindre la TV 30 s, rallumer | Téléphone : « <TV> ne répond plus… » ≤ 40 s, puis **reprise seule** au même % après le retour. TV : la copie finit, fichier lisible | 2 min | ☐ |
| 7 | Code PIN faux : message, pas de verrou surprise (P-06) | Accueil › Avancé › saisir un faux code, valider une fois | Téléphone : « code incorrect » **lisible**, puce **non verte**, aucun essai automatique. TV : rien ne casse ; le bon code est accepté juste après | 1 min | ☐ |
| 8 | Téléphone de confiance : liaison Bluetooth réelle (P-01/P-02) | Wi-Fi du téléphone **coupé** ; ouvrir CastBridge ; attendre 20 s | Puce « par Bluetooth » (jaune/verte), pas « Aucune TV ». Rallumer le Wi-Fi : retour LAN ≤ 30 s | 1 min | ☐ |
| 9 | Copie Bluetooth 2 Mo (P-13) | Wi-Fi coupé ; envoyer la vidéo de 2 Mo | Téléphone : « par Bluetooth : plus lent », % monotone, fin. TV : carte avec %, fichier présent | 1 min | ☐ |
| 10 | Activation : état propagé (P-25) | Écran « Activer la TV » du téléphone (sans rien envoyer) | Affiche l'état réel de la TV (ESSAI / PRODUCTION, jours restants) ≤ 5 s ; identique au badge de la TV | 30 s | ☐ |
| 11 | Apprendre et Langues (P-33) | TV : tuile Apprendre › une classe › une fiche ; tuile Langues | Écrans ouverts, D-pad fonctionne, texte lisible à 3 m, retour à l'accueil par BACK | 1 min | ☐ |
| 12 | Permissions et dialogues : rien en boucle (P-35) | Téléphone : ouvrir/fermer CastBridge 3 fois ; TV : ouvrir/fermer « Ajouter un téléphone » 2 fois | **Aucun** dialogue de permission ni « rendre visible » qui revient à chaque ouverture ; rien à cliquer en boucle | 1 min | ☐ |

**Total : ≈ 15 min** (le point 4 tourne en fond pendant 5-7).

Verdict : **12/12** ⇒ la candidate devient la build de la TV et va dans le `Download` de la clé (`docs/RELEASES.md` § 8) ; **un seul échec sur 1-7** ⇒ **STOP**, retour arrière (APK précédente de la clé), ligne dans `docs/REGRESSIONS.md`, test ajouté avant tout correctif ; échec sur 8-12 ⇒ livraison possible **avec** la ligne de régression ouverte et l'accord explicite du propriétaire.

Compte rendu (5 lignes, dans `docs/agent-reports/livraison-<version>.md`, sans secret) : versions TV/téléphone installées (écran « Version »), heure début/fin, points KO avec photo, verdict, qui a testé.
