STATUT: TERMINÉ (compilation et tests JVM seulement ; rien n'a tourné sur un téléphone ni une TV)
CAHIER: wd-manual-button (demande du propriétaire du 2026-10-03) · MODÈLE: sonnet · BRANCHE: claude/wd-manual-button · COMMIT: voir `git log -1`
PORTE: `:core:test` complet → voir la ligne SUITE ; `:sender:compileDebugKotlin` et `:receiver:compileDebugKotlin` → voir SUITE
FICHIERS: android/core/src/main/kotlin/castbridge/core/link/{WdManual,WdDiagnostic}.kt ; android/core/src/test/kotlin/castbridge/core/link/{WdManualTest,WdDiagnosticTest}.kt ; android/sender/src/main/kotlin/castbridge/sender/{AutoWifiDirect,TvHome,WdManualCard}.kt ; docs/agent-reports/wd-manual-button.md ; docs/test-plans/PARCOURS-CRITIQUES.md (P-50)
CHOIX:
- Aucune route de test sur la TV : `/upload/` écrit un vrai fichier dans la bibliothèque et `/api/gateway/speed` ne mesure que la passerelle Bluetooth. Rien n'a été inventé côté TV : le débit est ESTIMÉ par une lecture de 2 Mo (`/stream/`, plage d'un fichier ≥ 4 Mo de la mémoire interne) dans le sens TV→téléphone, et le relevé le dit. Latence = 20 × `GET /api/hello`. Clé USB = même lecture sur un fichier de la clé, sinon « inconnu ».
- « Journal de connexion du lien » : il n'existe pas côté téléphone. Réutilisation de la classe de cœur `TunnelJournal` (fichier tournant, 24 ko) dans `files/wifi-direct-journal.log`, lignes passées par `Redact.scrub` ; aucun mot de passe, code ni jeton n'entre dans une ligne (le texte du relevé ne reçoit même pas ces valeurs, et ne garde du nom du réseau que « DIRECT-xx »).
- `AutoWifiDirect` : ajouts seulement (`startNow`, `stopManual`, `manualFacts`, `screenVisible`, bail manuel) ; `WdClient`, `BulkRoute`, `PinStore`, `TvLink` inchangés. Session manuelle = l'automate passe `busy=true` à chaque tic (le délai de 30 s ne s'applique donc qu'à l'automatique) ; `WdManualLease` la rend après 10 minutes hors écran, sauf copie en cours.
- Le bouton contourne le seuil de 5 Mo, la pause de 10 minutes et le réglage automatique ; il ne contourne jamais le lien Bluetooth, le code valide, la capacité `wd` de la TV, la TV d'essai, ni la permission (demandée une fois, juste à temps). Une cause « Wi-Fi de la TV éteint » retenue par l'automatique est effacée au toucher (l'usager réessaie à la main).
- TV dont le HELLO ne dit pas `wd.cap` (ancienne) : bouton grisé « Cette TV ne propose pas Wi-Fi Direct ». Profil enfant sur la TV : le bouton reste utilisable (aucune donnée touchée) ; seule la mesure sur fichier devient « inconnu » si la TV la refuse. Causes ajoutées : « La liaison Bluetooth avec la TV n'est pas encore établie » (appairée mais sans réponse, action Réessayer) et « Allumez le Wi-Fi du téléphone ».
- Tests écrits avec le code dans la même passe (un seul `:core:test` autorisé sur ce Mac) : le ROUGE par assertion n'a pas été rejoué séparément.
NON FAIT / À VALIDER SUR MATÉRIEL: tout l'affichage (Samsung S21+ Android 14, TV GaiaOS), la jonction réelle, le chiffre de débit, la boîte « Se connecter à l'appareil ? » d'Android 10-12 ; FUMÉE: à lancer par le coordinateur (tools/smoke/smoke.py --tv fake)
QUESTION: aucune

# Test de terrain de 10 minutes : la puce Wi-Fi USB de la TV peut-elle créer un groupe Wi-Fi Direct, et à quel débit ?

Avant : CastBridge-TV et CastBridge à jour (cette branche), TV déjà appairée en Bluetooth avec le téléphone et code ou jeton valide (la carte « Connectée » de l'onglet TV). Mettre au moins un film de plus de 4 Mo dans la mémoire interne de la TV, et un autre sur la clé USB si la clé doit être testée. Téléphone Android 13 ou plus de préférence (aucune boîte) ; Android 10-12 demande une touche sur « Se connecter à l'appareil ? ».

## Les étapes

1. TV (min. 1) : CastBridge-TV ouvert, Wi-Fi de la TV ALLUMÉ (la puce USB branchée). Noter si la TV est sur le Wi-Fi de la maison ou non : c'est ce qui rend la ligne « Wi-Fi de la TV conservé » parlante. Option : pour tester « sans box », couper le Wi-Fi du téléphone de tout réseau (sans l'éteindre) et ne pas connecter la TV.
2. Téléphone (min. 2) : onglet CastBridge TV. Descendre jusqu'à la carte « Wi-Fi Direct ». Si le bouton est grisé, lire la cause en orange et toucher l'action (une seule) ; la liste des causes est plus bas.
3. Téléphone (min. 3) : toucher « Wi-Fi Direct ». Si Android demande « Appareils à proximité » : Autoriser (une seule fois). Si un réseau commun marche, la question « Un réseau commun fonctionne déjà : utiliser quand même Wi-Fi Direct ? » : répondre « Utiliser Wi-Fi Direct ».
4. Téléphone (min. 3 à 4) : la ligne passe orange « Mise en place… » puis verte « Par Wi-Fi Direct » ; le bouton devient « Wi-Fi Direct actif · Arrêter ». Rester sur cet écran : « Mesure en cours (environ 10 secondes)… » puis le relevé s'affiche.
5. Téléphone (min. 5) : toucher « Copier le rapport », coller le texte dans le message au coordinateur (il ne contient ni mot de passe ni code). Noter aussi : durée entre le toucher et le vert (à la montre), et si une boîte est apparue sur la TV ou le téléphone.
6. Téléphone (min. 6 à 8) : lancer une vraie copie d'un gros fichier (« Copier sur la TV », 200 Mo) pendant que le groupe est actif ; noter le Mo/s de la carte de file et la ligne d'état. Vérifier que le Wi-Fi du téléphone et Internet du téléphone marchent encore (Android 10-12 : le téléphone peut quitter son Wi-Fi).
7. (min. 9) Toucher « Arrêter ». Vérifier : le bouton redevient « Wi-Fi Direct », la carte de la TV ne montre plus de réseau Wi-Fi Direct. Puis recommencer une fois (le second essai doit être aussi rapide).
8. (min. 10) Quitter l'onglet TV (ou l'app) et ne rien toucher pendant plus de 10 minutes : le groupe doit tomber seul. Une copie en cours le garde.

## Lire le relevé, ligne par ligne

`Groupe : DIRECT-xx (créé par la TV) · Adresse : 192.168.49.1 · Joint en N,N s · Latence médiane N ms (n/20 réponses) · Débit estimé N,N Mo/s (TV→téléphone, lecture de 2,0 Mo : la TV n'a pas de route d'envoi de test) · Wi-Fi de la TV conservé : oui/non/inconnu · Clé USB : lecture N,N Mo/s`

| Champ | Ce que cela veut dire |
|---|---|
| Groupe : DIRECT-xx (créé par la TV) | la TV a bien créé un groupe Wi-Fi Direct (elle en est le propriétaire) : c'est la réponse à « la puce USB peut-elle être Group Owner ? ». « inconnu » = la TV n'a pas donné de groupe (voir la ligne rouge). |
| Adresse : 192.168.49.1 | adresse de la TV dans le groupe (192.168.49.1 est l'adresse habituelle d'un propriétaire de groupe Android). Une autre adresse est notable : la noter. |
| Joint en N,N s | du toucher à « le téléphone joint ET la TV répond à `/api/hello` ». Moins de 15 s est bon ; 20 s ou plus : jonction fragile. |
| Latence médiane N ms (n/20) | médiane de 20 `GET /api/hello`. Moins de 20 ms : très bon ; plus de 100 ms ou moins de 10 réponses sur 20 : lien instable. « inconnu » : moins de 10 réponses. |
| Débit estimé | lecture de 2 Mo d'un fichier de la mémoire interne, mesurée à partir du premier bloc. **Sens TV→téléphone** (la TV n'a pas de route de test pour l'envoi) : c'est une estimation du lien, pas la vitesse d'écriture sur la TV. « inconnu » = aucun fichier de 4 Mo ou plus, TV d'essai, ou lecture refusée. |
| Wi-Fi de la TV conservé | « oui » : la TV garde son réseau pendant que le groupe existe (réseau + groupe en même temps). « non » : le groupe lui a coûté son réseau. « inconnu » : la TV n'avait pas de réseau avant (rien à garder) ou n'a pas répondu. |
| Clé USB : lecture N,N Mo/s | même lecture sur un fichier de la clé USB de la TV : borne haute de ce que la clé peut servir par le Wi-Fi Direct. « inconnu » : aucun fichier de 4 Mo ou plus sur la clé, ou clé absente. |
| Verdict | « bon » (≥ 5 Mo/s), « acceptable pour la copie » (1 à 5), « niveau Bluetooth » (< 1). |

## Table de décision

| Constat | Décision |
|---|---|
| Groupe créé par la TV : oui, débit ≥ 5 Mo/s | **GO** : le Wi-Fi Direct est la bonne voie « sans box » ; garder l'automatique actif pour les gros envois. |
| Groupe créé, 1 à 5 Mo/s | **GO limité** : suffisant pour copier (200 Mo en 40 s à 2 minutes) ; pas pour lire en direct un film lourd ; à surveiller. |
| Groupe créé, moins de 1 Mo/s | **NO-GO comme voie rapide** : niveau Bluetooth ; la puce USB ne tient pas la charge (sinon refaire à moins de 2 m de la TV, antenne USB dégagée). |
| Pas de groupe (ligne rouge) | Lire la cause : « Le Wi-Fi de la TV est éteint » (l'allumer), « Cette TV ne gère pas le Wi-Fi Direct » (la puce ne peut pas : **NO-GO** pour cette TV), « Wi-Fi Direct non autorisé sur la TV », « n'a pas pu créer son réseau » (**NO-GO** probable : noter le modèle de la puce USB). |
| Wi-Fi de la TV conservé : non | Le groupe coupe le réseau de la TV : le garder pour les gros envois seulement, et l'annoncer. |
| Wi-Fi de la TV conservé : oui | Aucun inconvénient pour la TV : l'automatique peut rester actif par défaut. |
| Clé USB nettement plus lente que le débit estimé (par exemple 3 fois moins) | La clé limitera la copie : le débit utile = min(débit estimé, écriture de la clé). Tester la même copie vers la mémoire interne pour comparer. |

## Les causes affichées en orange sur le bouton gris (une action chacune)

« Associez d'abord la TV en Bluetooth » (Associer la TV) · « La liaison Bluetooth avec la TV n'est pas encore établie » (Réessayer) · « Entrez le code de la TV » (Entrer le code) · « Cette TV ne propose pas Wi-Fi Direct » · « Version d'essai de la TV : pas de Wi-Fi Direct » · « Ce téléphone (Android 9 ou plus ancien)… » · « Autorisez les appareils à proximité » (Autoriser, ouvre les réglages de l'app) · « Allumez le Wi-Fi du téléphone » (Ouvrir le Wi-Fi). Le journal local du téléphone (`files/wifi-direct-journal.log`, jamais de secret) garde les demandes, arrêts, échecs et le relevé.
