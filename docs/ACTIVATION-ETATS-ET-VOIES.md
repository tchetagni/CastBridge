# Machine à états de l'activation : conditions initiales, insertion et migration de licence, voies par ordre de priorité

État du code au 2026-10-14 (CastBridge 1.2.56, CastBridge-TV 0.14.49). Ce document décrit ce que fait le code, pas ce qu'on voudrait qu'il fasse : les écarts sont dits aux § 6 et 7. Sources : `android/core/.../owner/FeatureGate.kt` (`FeatureGate`, `GateState`, `FleetMigration`, `LockedTexts`), `owner/Activation.kt` (`TvGate`, `TvAccess`, `ActivationVerifier`), `owner/ActivationRoutePlan.kt` (voies du téléphone), `owner/ActivationScreenState.kt` (ce que dit le téléphone), `android/receiver/.../ActivationCenter.kt` et `TvService.kt` (ce que la TV expose). Détail des voies : [TV-ACTIVATION-CLE-USB.md](TV-ACTIVATION-CLE-USB.md) ; édition d'essai et licences : [TRIAL-EDITION.md](TRIAL-EDITION.md) §§ 11, 12, 16 ; règle « le code suffit » : [DESIGN-ACTIVATION-SIMPLE](coordination/DESIGN-ACTIVATION-SIMPLE-2026-10-07.md) § 7.

## 1. Les états de la TV

`FeatureGate.state(exigence, accès, heure, migration)` rend un de quatre états, le premier test vrai gagnant : exigence éteinte ⇒ `NotRequired` ; clé installée ⇒ `Activated` (une clé gagne toujours sur la grâce) ; grâce en cours ⇒ `Grace` ; sinon `Locked`.

| État | Quand | Ce que la TV expose | Ce que dit « Activer la TV » (`ActivationScreenState.Kind`) |
|---|---|---|---|
| `NotRequired` | build sans verrou (`REQUIRE_ACTIVATION` faux : développement) | API complète | `NO_LOCK` |
| `Locked` | aucune clé comptante et pas de grâce | **seulement** la surface d'activation : route `GET /api/activation/device-request` + `POST /api/activation/install` derrière le code de connexion, annonce mDNS `locked=1`, annonce BLE `…0008` et groupe Wi-Fi Direct dérivé du code (écran d'activation ouvert) ; toute autre route répond 403 ; le cœur de la TV ne démarre pas (`TvService.activationWatch`) | `LOCKED` (en pratique jamais lu : `GET /api/activation` n'existe pas sur une TV verrouillée) |
| `Grace` | installation antérieure au verrou, jusqu'au 2026-11-01 | **API complète**, avec les restrictions de l'essai (`ActivationCenter.trial()` est vrai) ; AUCUNE route verrouillée, ni `locked=1`, ni BLE `…0008`, ni groupe | `GRACE` |
| `Activated` | une clé comptante est installée | API complète ; en essai, seules les routes de `TrialPolicy` | `TRIAL` ou `PRODUCTION` |

Le téléphone lit cet état par `GET /api/activation`, par sa liaison de confiance, toutes les 4 s : une TV non liée n'a pas d'état affiché. Les champs `grace` et `graceUntil` (ms UTC) ont été ajoutés le 2026-10-14, parce que `trial` est vrai aussi en grâce ; une CastBridge-TV de 0.14.49 ou avant ne les dit pas, et le téléphone la lit alors comme « version d'essai ».

Sous-états d'`Activated` (`TvAccess`) : **essai** (`trial` : clé d'essai seule, plafond d'usage, restrictions `TrialPolicy`), **production** (« Version complète », « Illimité », « Tout ouvert (temporaire) »), **super** (`superUnlimited`), **horloge suspendue** (`suspended` : saut en avant de plus de 45 jours, libellé « Vérifiez l'heure de la TV » ; la TV n'est PAS bloquée, les plafonds n'avancent pas). **« Activation terminée »** n'en est pas un : le plafond d'usage de la clé est passé, `TvGate` rend `keyInstalled = false` et l'état retombe sur `Locked` (ou `Grace` si la grâce de l'installation court encore).

## 2. Conditions initiales : de quoi dépend l'état au démarrage

| Condition | État | Pourquoi, et suite |
|---|---|---|
| Installation neuve (`firstInstallTime` après `lock.graceStartMs`, 2026-10-02) | `Locked` | pas de grâce pour une installation neuve (`FleetMigration.of`) ; parcours de la TV neuve (§ 3) |
| Installation antérieure au verrou ET `clock.txt` présent | `Grace` jusqu'au 2026-11-01 (`graceStartMs` + `lock.graceDays` = 30 jours, `version.properties`), puis `Locked` | grâce ABSOLUE : une mise à jour par-dessus ne la relance pas ; l'écran d'activation est proposé une fois par jour (`dailyPrompt`) ; `lock.graceDays=0` la supprime |
| Réinstallation après désinstallation | `Locked` | `firstInstallTime` neuf ⇒ pas de grâce ; la clé de l'ancienne activation, retrouvée sur la clé USB, est refusée après sa fenêtre d'installation de 48 h (« Activation périmée : à refaire ») ⇒ l'agent la RÉÉMET (même matériel, portée REACTIVATE, aucun poste consommé : TRIAL-EDITION § 12) |
| Données effacées (Réglages d'Android) | `Locked` | `activations.txt` et `clock.txt` disparaissent ; sans `clock.txt` au démarrage la grâce est refusée même pour une installation antérieure au verrou (`ActivationCenter.loadGrace`) ; activation à refaire comme ci-dessus |
| Plafond d'usage de la clé atteint (essai de 30 jours, production limitée) | `Locked` (« Activation terminée ») | `keyInstalled = false` ; même parcours qu'une TV neuve |
| Horloge de la TV en retard | clé récente acceptée | `ActivationVerifier` compte `max(heure de la TV, issuedAt)` : une clé signée prouve que le temps a atteint sa date d'émission ; l'usage ne gèle pas (`TvClock` suit l'horloge monotone) |
| Saut d'horloge de plus de 45 jours en avant | `Activated` + `suspended` | ni bloquée ni consommée ; `ActivationCenter.confirmClockAhead()` n'a AUCUN écran (w2-04 ouvert : D-ACT-2) |
| Nouvelle clé de la console du propriétaire (incident du 2026-10-11, kid `3447e186`) | refus « clé inconnue » | les clés de confiance sont COMPILÉES dans CastBridge-TV (`BuildConfig.TRUSTED_KEYS`) : une TV de 0.14.48 ou avant refuse tant qu'elle n'est pas mise à jour (D-ACT-3) |

## 3. Insérer une licence : les voies, par ordre de priorité

Le téléphone essaie dans cet ordre (`ActivationRoutePlan`, testé ; une voie impossible est sautée avec sa cause ; la première qui joint la TV arrête la recherche). La clé USB et la télécommande ne passent pas par le téléphone.

| # | Voie (borne) | `Locked` (TV neuve, réinstallée, terminée) | `Grace` et essai (migration vers la production, tuile « Passer en production ») |
|---|---|---|---|
| 1 | Réseau local (10 s) | oui : mDNS `locked=1` ⇒ `GET /api/activation/device-request` (code dans `X-CB-Pin`) ⇒ `POST /api/activation/install` | **seulement si le téléphone est déjà lié à cette TV** (candidat `linked`) : `GET /api/tv/device-request` avec le code ; une TV non liée n'annonce pas `locked=1` |
| 2 | Bluetooth sans appairage (25 s) | oui : annonce BLE `…0008`, PAKE sur le code, canal L2CAP puis RFCOMM « insecure » | non : pas d'annonce |
| 3 | Réseau de la TV, Wi-Fi Direct dérivé du code (50 s) | oui : groupe créé selon `ActivationGroupPolicy` | non : pas de groupe |
| 4 | Bluetooth appairé (20 s ; 90 s si un appairage est à valider) | oui ; seule voie des anciennes TV | oui : le canal propriétaire reste ouvert ; c'est la voie d'une TV non liée |
| 5 | Clé USB préparée par l'agent (`castbridge-activation emettre … --cle-usb`) | oui : bandeau « Clé USB : activation trouvée pour cette TV › Activer » | oui tant que `ActivationCenter.usbWatchWanted()` (état autre qu'`Activated`, ou essai) |
| 6 | Saisie à la télécommande (clé compacte de 165 caractères, ou collage) | oui : dernier recours | oui |

**Migration essai ⇒ production** : la clé de production s'AJOUTE à la clé d'essai (`InstalledActivations` garde les deux, l'essai sous la clé `trial`, la production sous « licence|poste ») ; `TvGate` rend alors « Version complète » et lève les restrictions de `TrialPolicy`. **Grâce ⇒ activée** : toute clé valable l'emporte. Une TV en production n'a plus rien à recevoir ; une autre clé s'ajoute par les mêmes voies. Où que la clé arrive, c'est le même chemin (`ActivationReceiver` : `ActivationVerifier`, puis `ActivationBinding`) : signature d'une clé de confiance, matériel (k parmi n), fenêtre de 48 h, clé d'installation de CETTE TV.

## 4. D'où vient la clé

`KeyAcquisition.Source` : **B1 console du propriétaire** (build superadmin : « Émettre et installer » signe sur place) ; **B2 serveur** (« Demander l'activation à CastBridge » : préparée mais ÉTEINTE, `ServerActivationRequests.ENABLED = false`, aucune requête n'existe) ; **B3 clé collée** (champ ; presse-papiers proposé, jamais installé seul) **ou fichier** (sélecteur du système). Hors téléphone : la clé USB préparée par l'agent (voie 5) et la saisie à la télécommande (voie 6). La TV a toujours le dernier mot.

## 5. Invariants (testés)

- **Le code de connexion** (6 chiffres) n'est ni journalisé ni imprimé (ACT-NF2). Le Bluetooth sans appairage ne le fait jamais circuler : la PAKE (CPace) interdit toute attaque hors ligne. Sur le Wi-Fi il voyage dans l'en-tête `X-CB-Pin` (réseau local, sans TLS) et le nom du groupe dérivé en laisse déduire (I-3) : d'où la porte de tentatives.
- **Porte de tentatives partagée** (`ActivationAttemptGate`, `PinGuard`) par la route HTTP et le Bluetooth sans appairage : 5 codes faux d'un même pair ⇒ 60 s de verrou ; 20 codes faux en 10 minutes, tous pairs confondus ⇒ tout est fermé.
- **Une clé n'est jamais renvoyée seule après un échec** (`KeyAcquisition` : l'échec termine le geste, une reconnexion ne réessaie pas, le « Installer » suivant est un nouveau geste) ; la clé et le code ne partent qu'à la TV qui a répondu 200 à la lecture de sa demande.
- **Une seule notification** « TV activée » (le nom de la TV, jamais le code ni la clé) ; ni la clé ni la demande ne figurent dans un journal ou un `toString`.
- **Aucun texte ne dit que le code d'appareil suffit** (R-48) : la clé se demande avec « Activer la TV », qui lit la demande COMPLÈTE (`LockedTexts.REQUEST`, `NOTICE`, `TrialPolicy.UPGRADE_EXPLAIN`).

## 6. Décisions ouvertes pour le propriétaire

- **D-ACT-1 : étendre « le code suffit » à la migration essai ⇒ production.** Aujourd'hui seule une TV `Locked` s'annonce (mDNS `locked=1`, BLE `…0008`, groupe) : pour passer de l'essai ou de la grâce à la production, le téléphone doit déjà être LIÉ à la TV, sinon il reste le Bluetooth appairé. Proposition : annoncer en mDNS et en BLE pendant l'écran « Passer en production » (même porte de tentatives). Coût : une TV déjà utilisable devient joignable par son code.
- **D-ACT-2 : écran « l'heure est juste » (w2-04).** Sans lui, une TV dont l'horloge a sauté en avant reste à « Vérifiez l'heure de la TV » sans que personne puisse le lui dire.
- **D-ACT-3 : rotation des clés de confiance sans mise à jour de la TV.** Une liste de clés signée par le propriétaire (sous une clé racine compilée) remplacerait la liste compilée ; sans elle, chaque nouvelle clé de console exige une nouvelle CastBridge-TV (incident du 2026-10-11).

## 7. Écarts connus (hors décision)

- `PlayerActivity` : la tuile « Passer en production » s'affiche aussi en grâce (`trial()` y est vrai) et sa phrase dit « demandez la clé de production avec le code de cette TV », contraire à R-48 : texte à corriger.
- Une TV en grâce n'a ni route verrouillée ni annonce : tant que D-ACT-1 n'est pas tranchée, c'est une limite de conception, pas un défaut.
