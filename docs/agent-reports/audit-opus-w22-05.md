STATUT: TERMINÉ (audit, lecture seule ; aucun code modifié dans le dépôt, aucun commit, aucun accès serveur)
AUDIT: w22-05 (mises cbe1, règlement cbr1, rendus échus, conversion, codes de réception, transfert, collecteur) · MODÈLE: Opus · BRANCHE: integration/agents (code du portefeuille identique de 8f12cf2d à db246a25) · DATE: 2026-10-04

## Ce qui a tourné, ce qui n'a pas pu tourner

- `mvn -o -q test -Dtest='castbridge.server.wallet.**'` sur une COPIE de `backend/` (bloc-notes de la session) : **141 tests, 0 échec, 2 ignorés** (`WalletMySqlContainerTest`, pas de Docker).
- **Preuves** : 6 tests (`AuditW2205ProofTest`, copie hors dépôt) qui affirment le comportement attendu par la conception ou le cahier : **les 6 échouent** (sorties citées plus bas).
- **Mutations** : 15 mutations appliquées une par une à la copie (suite `ops` relancée à chaque fois) : **9 survivent** (tableau § Qualité des tests).
- **Collecteur** : `collect-results.sh` lancé contre un serveur HTTP factice local (127.0.0.1), avec un lien symbolique dans le dossier source : preuve ci-dessous.
- **Pas lancé** : MySQL 8.4 (ni Docker ni serveur MySQL ici), `docker cp` réel, le Kotlin (lu seulement), `server-play` (w22-04 n'est pas fusionné : aucune vérification de `cbe1` côté service de jeu n'existe encore). Tout ce qui touche à MySQL est une **analyse**.
- Les constats de l'audit w22-02 (C1, H1 à H4, M1 à M5) ne sont pas répétés, sauf là où le code de w22-05 les **aggrave** (§ Aggravations).

## Constats classés

### CRITIQUE

Aucun. Je n'ai trouvé **aucune création ni destruction de valeur** dans le code de w22-05 : chaque opération est une seule `post` du grand livre, qui revérifie sous verrou (blocage `OPEN`, conservation, découvert). Voir « Vérifié correct ».

### ÉLEVÉ

**E1. Les droits de mise sont lus dans l'édition MÉMORISÉE à la dernière synchronisation : une licence ILLIMITÉE révoquée (ou dont le poste est passé sur une autre TV) garde la mise MBOKO ; un essai échu garde la mise NDEM. Le `cbw1` signé le confirme (`ed=UNLIMITED`, `stakesM=true`).**
- Preuve : `EscrowService.java:94-97`. Sans activations jointes (le corps de `/escrow` les rend facultatives, `WalletOpsController.java:141-142`), si `licenses.forDevice(code)` est vide, l'édition vient de `wallet_identity.edition` ; `"UNLIMITED" && ls.isEmpty()` ⇒ ILLIMITÉE (pensé pour le droit « super »). Or `LicenseService.revoke()` libère les postes et `JdbcLicenseFacts` ne lit que `lic_seat.state='ACTIVE'` (M2 de w22-02) : une licence révoquée **disparaît**, et l'étiquette mémorisée `UNLIMITED` (posée par `touchSync`, `GrantService.java:118`) suffit. Même chose après un transfert ou une libération de poste. `"TRIAL"` mémorisé ⇒ ESSAI sans regarder la date de fin (`:96`). C'est la TV qui décide si elle joint ses activations et quand elle se synchronise : une TV qui ne se synchronise plus garde ces droits **indéfiniment**.
- Tests (échouent) :
  ```java
  Tv tv = productionTv(null, 14, "ACTIVE");                          // illimitée
  jdbc.update("UPDATE lic_license SET state='REVOKED' WHERE license_id=?", "lic-" + tv.code().toLowerCase());
  jdbc.update("UPDATE lic_seat SET state='RELEASED', slot_no=NULL WHERE device_code=?", tv.code());
  assertEquals(409, escrow(tv, "MBOKO", 10, 2, "rev-0001").status());   // ÉCHOUE : 200, cbw1 {ed:UNLIMITED, stakesM:true}
  ```
  `p2_expiredTrialWithoutSyncMustNotStake` : essai de 30 j, horloge + 40 j, `escrow NDEM` ⇒ **200** (attendu 409 `ACTIVATE`).
- Correctif : (1) ne jamais dériver un droit de mise de `wallet_identity.edition` ; mémoriser séparément `super_key BOOLEAN` (posé seulement quand `reading.superKey()`), et ne reconnaître « super » que par lui ; (2) pour l'essai, mémoriser `trial_end_at` (fin de l'intervalle d'essai lu dans `cbx1`) et refuser après ; (3) plus simple et plus sûr : **exiger les activations sur `/escrow`** (comme `sync`) et refuser sans elles (`ACTIVATE`) ; (4) le même calcul sert à `snapshot()` (`WalletOpsController.java:121-131`) : il signe aujourd'hui `stakesM=true` pour une licence révoquée.

### MOYEN

**M1. Un rejeu de `/escrow` avec la même clé, le même MONTANT et un autre couple (per, k) rend un SECOND `cbe1`, signé, qui contredit le blocage : la partie jouée avec lui ne se règle jamais.**
- Preuve : `EscrowService.java:116-135`. Le blocage existe (`known`), donc aucun contrôle ; `Txn.lock` ne met dans l'empreinte que les écritures (`DISPO −20 / BLOQUE +20`), identiques pour 10 × 2 et 20 × 1 ⇒ `replayed=true` ; `readAndFill` ne réécrit pas `per` (`AND per IS NULL`) ; `sign(...)` signe les `per`/`k` de la REQUÊTE.
- Test (échoue) : `p3_…` ⇒ `reçu 200 per=20 k=1` ; le règlement d'une partie à 20 par siège : `400 « Mise par siège différente de celle du blocage »`.
- Effet : aucune valeur, mais une TV peut rendre toute partie non réglable (toutes les mises de la salle restent bloquées 6 h 30, puis rendues). Correctif : signer le `cbe1` à partir de `per`/`k` LUS en base (`readAndFill` les rend), et refuser `IDEM_CONFLICT` si la requête diffère ; ou mettre `per:k` dans l'empreinte (référence `eid|per|k` dans `refs` ou empreinte du contenu calculée sur la requête).

**M2. Écart 1 (code de réception) : deux transactions SQL au lieu d'une ; code brûlé sans transfert, code rendu après un transfert réussi, plafond sérialisé dans la seule JVM.**
- Preuve : `TransferService.java:71-78`, `ReceiveCodeService.java:163-170`.
  - Fenêtre 1 (prouvée) : arrêt de l'API entre `claim` (validé) et `ledger.post` ⇒ code `used_at` posé, aucun `TRANSFER`. Le réessai de la TV avec la même clé répond `CODE_UNKNOWN`. Test `p4_…` ⇒ `code brûlé sans transfert ; réponse au réessai : 409 CODE_UNKNOWN`. Le destinataire doit redemander un code ; aucune valeur perdue.
  - Fenêtre 2 (analyse) : `codes.mask(...)` est DANS le `try` (`:73-74`) ; si `mask` échoue après une pose validée (base indisponible un instant), le `catch` **rend** le code alors que le transfert est fait (usage unique violé ; aucune valeur créée : le code ne sert qu'à recevoir). Idem si la validation (COMMIT) réussit mais que l'accusé est perdu.
  - Fenêtre 3 (analyse) : si `release` échoue après un refus du grand livre ⇒ code brûlé.
  - Le plafond quotidien est lu (`sentToday`) HORS de la transaction et protégé par un `synchronized` sur 64 bandes **de la JVM** : exact avec un seul conteneur API ; faux dès deux instances. Le verrou est tenu pendant `ledger.post` (attentes de verrou MySQL jusqu'à 50 s) et partagé avec `ReceiveCodeService.create` d'identités sans rapport (même bande) : blocage en tête de file possible.
  - Il n'y a **pas de double dépense** : le débit est toujours vérifié par le grand livre sous verrou.
- Correctif exact : voir § « Correctif de l'atomicité du code » (une méthode `JdbcLedger.post(..., InTx guard)`).

**M3. Un service de jeu compromis (clé « résultat ») vide TOUS les blocages ouverts du serveur de même (monnaie, mise par siège), sans plafond, sans alerte, sans interrupteur à chaud.**
- Preuve : `SettleService.java:59-105` ne vérifie que la signature, l'existence, l'état, la monnaie, `per` et le titulaire. Un seul `cbr1` peut lister 16 blocages de **parties différentes** (rien ne lie un blocage à une salle) ; le nombre de `cbr1` est illimité (60 / min / adresse). `pay` n'est borné que par Σ pay = Σ used. Aucun plafond par règlement, par jour ou par identité (R-E8 est au niveau 2), aucune alerte sur un gain anormal (prévue par S-7), et aucun `switch.settle` : retirer la clé exige un redémarrage.
- Ordre de grandeur : bornes par défaut 100 MBOKO × 8 sièges = 800 MBOKO par blocage ; tout l'encours `BLOQUE` (visible dans `reconcile`) est exposé.
- Correctif : (1) interrupteur `switch.settle` dans `wallet_policy` (lu à chaque appel ; coupé ⇒ 503, le collecteur réessaie) ; (2) alerte au journal quand une ligne paie plus de `2 × used` et plus de 200 MBOKO, ou quand une identité gagne plus de 500 MBOKO / jour ; (3) au niveau 2 : perte nette quotidienne bornée (R-E8) ; (4) lier le blocage à la salle : la TV déclare `room` au blocage, `cbe1` le porte, `cbr1.room` doit l'égaler (voir M4).

**M4. Réutilisation d'un même `cbe1` dans deux salles (« jouer deux parties avec une seule mise ») : rien côté API ne l'empêche ; la parade appartient à w22-04, qui n'existe pas encore.**
- Analyse : `cbe1` ne porte pas la salle (`EscrowService.java:150-151`). Une TV peut présenter le même `cbe1` à deux salles ; le premier `cbr1` posté règle, le second est refusé en entier (`ESCROW_CLOSED`) et toutes les mises de l'autre salle restent bloquées 6 h 30. Un hôte qui fait durer la partie qu'il perd obtient ainsi l'issue de la partie qui finit d'abord.
- Correctif : (1) exigence pour w22-04 : le service de jeu refuse un `eid` déjà vu (persisté jusqu'à `exp + 6 h 30`) ; (2) côté API : champ `room` facultatif dans `/escrow`, porté par `cbe1`, vérifié au règlement.

**M5. Ferme d'essais par transferts : aucun plafond propre à l'essai.**
- Constat : un essai peut transférer jusqu'à 10 000 NDEM et 100 MBOKO par jour, et envoyer des MBOKO reçus (`TransferService.java:69`, plafond unique de niveau 1). Rendement réel faible (100 NDEM / clé / mois), borné par l'émission des clés, mais rien ne limite la concentration.
- Proposition (calibrée pour l'honnête) : émetteur d'essai : 1 000 NDEM / jour, 0 MBOKO (R-E6) ; compte émetteur âgé d'au moins 72 h ; destinataire : au plus 3 donateurs distincts / 24 h, au-delà refus `DAILY_CAP` côté réception et alerte ; plafond par couple émetteur → destinataire (5 000 NDEM / 50 MBOKO / jour) ; au niveau 2 la poche BONUS (R-E4) rend les attributions d'essai non transférables, ce qui ferme la ferme. Les licences et activations ne sont jamais transférables : **vérifié** (le code de w22-05 ne touche aucune table `lic_*`).

### FAIBLE

- **F1. Un blocage dont aucun siège n'a joué peut être payé** (`Settlement.check`, `SettleService.java:91`) : `used = 0, pay = 10` accepté. Test `p5_…` ⇒ `reçu 200`. Pas de création (Σ conservée) ; à refuser pour `END` (`pay > 0 ⇒ used > 0`), ce qui réduit aussi M3.
- **F2. Réessai d'une conversion réussie après un changement de frais ⇒ `IDEM_CONFLICT`** : la TV croit que rien n'est fait alors que la conversion a eu lieu. Test `p6_…` ⇒ `409 IDEM_CONFLICT`. Correctif : sur conflit, relire la transaction `cvt:<id>:<idem>` et rendre `replayed=true` avec les montants RÉELLEMENT appliqués (lus dans ses écritures) ; aujourd'hui la réponse d'un rejeu porte le taux et les frais ACTUELS (`ConvertService.java:43`).
- **F3. Collecteur** (`tools/wallet/collect-results.sh`) :
  - liens symboliques suivis : `[ -f "$f" ]` (`:40`) puis `curl --data-binary "@$f"` lisent la CIBLE. Preuve (serveur factice) : un lien `evil.cbr1 → fichier de l'hôte` ⇒ corps reçu par l'API : `SECRET-HOST-FILE-CONTENT`. `docker cp` copie les liens tels quels, donc un conteneur de jeu compromis fait lire au cron (root) n'importe quel fichier de l'hôte et l'envoie à l'API locale (qui ne le renvoie pas et ne journalise pas les corps : fuite non constatée, mais lecture faite). Correctif : `docker cp` vers un dossier neuf, puis `find … -maxdepth 1 -type f ! -type l -name '*.cbr1' -size -8k` ; refuser tout lien ;
  - 403 `RESULT_FORGED` est classé définitif (`:49`) : pendant une rotation (service de jeu déjà sur la clé n° 2, API pas encore), tous les résultats partent dans `rejected/` et ne sont jamais réessayés ⇒ les gagnants ne sont pas payés (mises rendues à 6 h 30). Traiter 403 comme « à réessayer » pendant 6 h, ou rejouer `rejected/` à la main ;
  - pas de verrou (`flock`) : deux passages qui se chevauchent postent deux fois (sans danger, idempotent) ;
  - un nom de fichier réutilisé par le service de jeu serait ignoré pour toujours (dédoublonnage par nom) : nommer les fichiers par `rid` (exigence w22-04) ;
  - aucune injection de shell trouvée (tout est entre guillemets, aucun `eval`) ; aucun secret.
- **F4. Le code de réception apparaît dans le journal d'accès** : `GET /api/v1/wallet/receive-code/{code}` est journalisé avec son chemin (`AccessLogFilter.java:36`) et par nginx. Faible valeur (10 min, sert seulement à RECEVOIR) ; passer le code en `POST` corps, ou masquer le chemin.
- **F5. Motifs inconnus de la TV** : le serveur envoie `LICENSE_PENDING`, `CONVERT_SUSPENDED`, `TRANSFER_SUSPENDED`, `RESULT_AFTER_REFUND`, `CODE_LIMIT`, `LOOKUP_LIMIT`, `RESULT_BAD`, `RESULT_FORGED` (et du cœur : `IDEM_CONFLICT`, `ESCROW_UNKNOWN`, `ESCROW_CLOSED`, `BAD_TXN`, `UNBALANCED`), absents de `android/core/.../wallet/WalletReason.kt`. Aujourd'hui aucun code Kotlin ne lit `details[0]` (câblage w22-07) : rien ne casse. Exigence pour w22-07 : lecture tolérante (`entries.firstOrNull { it.name == s }`), à défaut afficher le `message` du serveur, jamais `valueOf` (exception). Les 429 des débits (`WalletPolicyService.java:97-98`, règlement) n'ont **aucun** motif fermé : en ajouter un (`RATE_LIMIT`).
- **F6.** `wallet_recv_code` n'est jamais purgée et n'a pas d'index sur `holder` : `COUNT(*) … WHERE holder = ?` (`ReceiveCodeService.java:92`) parcourt toute la table. Ajouter `INDEX(holder, used_at, exp_at)` et une purge des codes de plus de 7 jours.
- **F7.** Débit de règlement par adresse cliente (`X-Forwarded-For` appliqué, `forward-headers-strategy: framework` : le doute du rapport w22-05 est levé) : derrière un CGNAT d'opérateur mobile, des centaines de TV partagent 60 / min. Sans danger (le collecteur rattrape), à surveiller.
- **F8.** Un rejeu de transfert après un gel répond `FROZEN` (`TransferService.java:53` avant la recherche de la clé) ; après purge du code, `IDEM_CONFLICT`. Mettre le rejeu avant ces contrôles.
- **F9.** Plafond de transfert par jour **UTC** (`TransferService.java:84`) ; le reste du produit compte en Africa/Douala (UTC+1) : le « jour » change à 01:00 heure locale. À décider (pas de risque).
- **F10.** `PlayResultKeys` ne refuse pas au démarrage une clé égale à la clé « portefeuille » ou à celle des tickets ; la séparation de domaine protège, mais c'est une erreur d'exploitation à détecter.
- **F11.** La carte TV (`WalletView.balanceLine`) n'affiche que `n`/`m` ; les jetons bloqués (`nb`/`mb`, bien signés dans `cbw1`) restent invisibles jusqu'à 6 h 30 : ajouter « dont 40 MBOKO en jeu ».

## Aggravations des constats de w22-02 par w22-05

- **M5 (liaison au premier venu) devient exploitable** : les sorties existent maintenant. Une `cbx1` copiée, synchronisée avant la vraie TV, lie le compte à l'attaquant, qui transfère (10 000 NDEM + 100 MBOKO / jour) et mise. À fermer AVANT d'allumer `switch.transfer`.
- **C1 (rejeu de l'historique au transfert de poste)** : les MBOKO créés deux fois sont maintenant transférables, convertibles et misables : la valeur sort du compte.
- **H3** : le contributeur `escrow-expiry` est appelé aussi dans la branche « lecture seule » de `sync` : un inconnu peut déclencher les rendus échus d'une autre identité (sans dommage : ces rendus sont dus).

## Vérifié correct

- **Conservation (I-1, I-3, I-8)** sous chaque opération : une seule `post` par opération ; `ESCROW_LOCK` crée la ligne `wallet_escrow` dans la même transaction que `BLOQUE +a` ; `SETTLE`/`REFUND` ferment par `UPDATE … WHERE state='OPEN'` avec `n != 1 ⇒` annulation (`JdbcLedger.java:141-142`) ; `reconcile` vérifie `BLOQUE = Σ blocages ouverts` et `SYS:POT = 0`. Suite de 160 opérations aléatoires verte.
- **Mise** : solde insuffisant ⇒ `INSUFFICIENT` sous verrou ; même clé ⇒ rejeu (même `cbe1` si mêmes per/k, voir M1) ; gelé, interrupteur coupé, bornes de `wallet_policy` relues à chaque appel ; essai + MBOKO ⇒ `TRIAL_NO_MBOKO` ; licence SUSPENDED / REVOKED (poste encore actif) / grâce épuisée ⇒ refus, relue à chaque mise ; `eid` = SHA-256(identité, clé) : deux TV ne se heurtent pas ; `per × k ≤ 8·10⁹` (pas de débordement).
- **Règlement** : signature vérifiée AVANT toute lecture de base (aucune fuite d'existence d'`eid` sans `cbr1` authentique) ; lecture stricte (clés exactes, réécriture compacte identique, entiers seulement, base64url canonique, ≤ 16 lignes, ≤ 4 096 caractères) ; domaine `castbridge-play-result-v1` séparé de `cbe1`/`cbw1` ; clé « portefeuille » refusée ; deux clés de rotation ; rejeu même `rid` même contenu ⇒ même réponse (5 rejeux), autre contenu ⇒ `IDEM_CONFLICT` ; blocage d'une autre identité ⇒ refus ; `eid` inconnu ⇒ refus total ; blocage rendu ⇒ `RESULT_AFTER_REFUND` noté, rien réglé, puis réponse stable ; ABORT ⇒ `used = pay = 0` imposé, tout rendu ; Σ pay = Σ used, `used ≤ amount`, `used` multiple de `per` : ni création ni poussière (le partage `Pot.split` est fait par le service de jeu, l'API n'arrondit rien) ; 8 fils sur le même `cbr1` ⇒ une transaction (le grand livre transforme les perdants en rejeu) ; arrêt entre la pose et `remember` ⇒ le rejeu suivant retrouve `settled_rid = rid` et répond pareil.
- **Course mise / règlement / rendu** : le rendu lit hors verrou puis pose `refund:<eid>` ; le grand livre ne ferme qu'une fois ; si le rendu gagne, le règlement relit et répond `RESULT_AFTER_REFUND`. Même horloge (`WalletClock`, serveur) pour `created_at`, `exp_at` et l'échéance ; l'horloge de la TV n'intervient pas. Bornes 6 h 29 min 59 s / 6 h 31 testées (mutation « 5 h » tuée, mutation « exp − 1 s » tuée).
- **Conversion** : 1 000 : 1 dans les deux sens, multiples exacts (la quantité est en MBOKO, aucun reste), frais `ceil(gross × bp / 10 000)` contre le joueur, `0 ≤ bp ≤ 2 000` vérifié à l'écriture de la politique, `gross ≤ 10¹⁵`, `gross × bp ≤ 2·10¹⁸ < 2⁶³` ; `q ≥ 1` ; même clé autre sens ⇒ `IDEM_CONFLICT` ; gel ⇒ refus ; une licence suspendue n'empêche pas de convertir (conforme : toute identité peut détenir et convertir).
- **Transfert** : NDEM ou MBOKO seulement (toute autre monnaie ⇒ 400) ; montant 1..10⁹ ; soi-même refusé (deux barrières) ; destinataire = titulaire d'un code existant (aucun compte inexistant possible) ; destinataire gelé : reçoit (conforme § 5.4) ; deux émetteurs sur un même code ⇒ un seul gagne ; débit du grand livre sous verrou ⇒ **aucune double dépense** ; rejeu ⇒ une écriture.
- **Codes** : `SecureRandom`, 40 bits, contrôle ; deviner un code ne rapporte rien (il sert à DONNER) ; 3 actifs ; consultation 10 / h / identité ; masque « TV de K… · …4F2Q », jamais le code entier.
- **Accès** : toutes les routes TV exigent jeton d'appareil + type `tv` + appareil non bloqué + `deviceCode` + identité connue + **même appareil que la liaison** (`BOUND_OTHER_TV`) + débit d'écriture (sauf F-mutations ci-dessous) ; `/settle` sans authentification par conception ; `/admin/wallet/escrow-expiry` sous `/api/v1/admin/**` ⇒ jeton d'administration ; aucun cookie ⇒ aucun CSRF possible (`SecurityConfig` : CSRF désactivé, sans session) ; aucune route ne crédite sur la parole de la TV (I-7).
- **SQL** : tout est paramétré ; la seule concaténation (`EscrowExpiryContributor.java:49`) est un fragment constant. Aucun `@Transactional` autour de `JdbcLedger.post` (L2 de w22-02 non déclenché).
- **Journaux** : ni clé, ni jeton, ni activation, ni montant de solde d'un tiers ; `INSUFFICIENT` ne montre que le solde de l'émetteur lui-même (F4 mis à part).
- **Collecteur** : aucun secret, n'appelle que 127.0.0.1, quoting correct, classement 2xx / 4xx / 429-5xx juste, auto-test vert.

## Correctif de l'atomicité du code (écart 1) : une transaction, garde dans le grand livre

1. `JdbcLedger` (w22-02, à faire par son propriétaire) :
   ```java
   /** Garde exécutée DANS la transaction de la pose, après les verrous, l'idempotence et le contrôle de découvert, avant toute écriture ; jamais exécutée sur un rejeu. */
   @FunctionalInterface public interface InTx { void run(JdbcTemplate jdbc); }

   public Posted post(Txn txn, String actor, String holder, String reason, InTx guard) { return attempt(txn, actor, holder, reason, true, guard); }
   // postOnce : … étape 2 (rejeu ⇒ return new Posted(true) SANS appeler guard) ; étape 3 (forme, découvert) ;
   //            if (guard != null) guard.run(jdbc);          // une exception (LedgerException) annule TOUT, garde comprise
   //            étape 4 (écritures).
   ```
   Le réessai borné reste juste : un interblocage annule la transaction entière, garde comprise, et la relance la rejoue en entier.
2. `TransferService.transfer` :
   ```java
   Instant now = clock.now();
   Timestamp usedAt = Timestamp.from(now.truncatedTo(ChronoUnit.MICROS));
   Ledger.Posted p = ledger.post(Txn.transfer(sender, code.holder(), cur, amt, key), "tv", sender, null, j -> {
       policies.get().checkTransfer(cur, amt, sentToday(j, sender, cur, now));   // sous le verrou du DISPO de l'émetteur : plafond exact, même à plusieurs JVM
       int n = j.update("UPDATE wallet_recv_code SET used_at = ?, used_key = ? WHERE code = ? AND holder = ? AND used_at IS NULL AND exp_at > ?",
               usedAt, key, canonical, code.holder(), Timestamp.from(now));
       if (n != 1) throw new LedgerException(WalletReason.CODE_UNKNOWN);
   });
   return new Done(p.replayed(), codes.mask(code.holder()));                   // hors transaction : un échec ici ne rend plus le code
   ```
   Supprimer `claim`, `release` et le `synchronized` (le verrou `FOR UPDATE` du DISPO de l'émetteur sérialise ses transferts ; deux émetteurs sur un même code se sérialisent sur le DISPO du destinataire, puis le perdant voit `n = 0`).
3. Ordre des verrous : comptes `wallet_balance` (ordre croissant), puis la ligne `wallet_recv_code` par clé primaire : toujours le même ordre, aucun cycle (la création d'un code ne verrouille aucun solde).
4. Colonne `used_key VARCHAR(200) NULL` (lien code ⇔ transfert, audit) : à ajouter dans la migration du portefeuille tant qu'elle n'est déployée nulle part (voir H4 de w22-02 pour la numérotation).
5. Tests : `p4_…` (code jamais marqué sans transfert) ; garde qui lève après l'`UPDATE` ⇒ code libre et aucune transaction ; course de 8 émetteurs sur un code ⇒ 1 transfert ; 8 transferts parallèles du même émetteur au plafond ⇒ total ≤ plafond ; rejeu après succès ⇒ `replayed`, code inchangé.

## Risques propres à MySQL (non prouvables sur H2) et tests à lancer sur MySQL 8.4 avant déploiement

Risques : (a) H2 est en READ COMMITTED, MySQL en REPEATABLE READ : la vue cohérente se fixe à la première lecture non verrouillante ; les poses lisent l'idempotence APRÈS les `FOR UPDATE` (correct par analyse) ; (b) `release` compare `used_at = ?` sur `DATETIME(6)` : exact si Connector/J envoie les microsecondes (valeur tronquée côté Java) ; à mesurer ; disparaît avec le correctif ci-dessus ; (c) `claim` compare `exp_at > ?` avec des nanosecondes (arrondi MySQL à la microseconde) : négligeable ; (d) insertion concurrente d'un même `eid` : `UNIQUE(idem_key)` sur `wallet_txn` d'abord, puis la clé primaire de `wallet_escrow` ⇒ `DuplicateKeyException` ⇒ réessai ⇒ rejeu : à vérifier (code 1062 traduit) ; (e) verrous d'intervalle : toutes les lectures verrouillantes sont par clé primaire ; `refundDue` lit `state='OPEN'` sans verrou (balayage, pas de verrou) ; (f) plans : `COUNT(*)` des codes (pas d'index), `sentToday` (jointure `wallet_entry` ⇒ `wallet_txn`, filtre sur `t.kind`/`t.created_at` sans index) ; (g) CHECK violé : erreur 3819, traduction Spring à vérifier.

À lancer (machine avec Docker, image et options de `backend/docker-compose.yml`), en ajoutant les classes au conteneur de `WalletMySqlContainerTest` :
1. `SettleRaceTest` (8 fils, une transaction) et une variante 16 fils × 50 résultats distincts sur des blocages croisés : `ledger.retries()` = 0, aucun interblocage (`SHOW ENGINE INNODB STATUS`).
2. `TransferApiTest` complet, dont la course sur un code ; ajouter : A→B et B→A en parallèle (16 × 200) + règlements concurrents sur A et B : aucun interblocage, Σ = 0.
3. `EscrowExpiryTest` (bornes 6 h 29 min 59 s / 6 h 31 sur `DATETIME(6)`), JVM en `TZ=Africa/Douala` puis `TZ=UTC`.
4. `EscrowApiTest` + nouveau test : 8 fils, même clé de blocage ⇒ un blocage, même `cbe1`.
5. `ConvertApiTest`, `SettleFixturesTest`, `OpsConservationTest` (160 opérations) puis `reconcile` ⇒ ok.
6. `claim`/`release` : prise, refus du grand livre, rendu ⇒ code réutilisable (vérifie l'égalité à la microseconde) — jusqu'au correctif.
7. `EXPLAIN` des requêtes `COUNT(*) wallet_recv_code`, `sentToday`, `refundDue` sur 100 000 lignes.
8. `WalletMySqlContainerTest` (oracle 2 000 suites, 16 × 500) et la liste MySQL de l'audit w22-02.
9. Les 6 tests de preuve de ce rapport, ajoutés au dépôt : ils doivent passer après les correctifs.

## Qualité des tests : mutations qui survivent (suite `ops` verte, mutation par mutation)

| # | Mutation (copie hors dépôt) | Test à ajouter |
|---|---|---|
| 1 | `ConvertService.java:38` : contrôle du gel retiré | identité gelée ⇒ `convert` 409 `FROZEN`, soldes inchangés |
| 2 | `TransferService.java:53` : contrôle du gel retiré | émetteur gelé ⇒ 409 `FROZEN` ; destinataire gelé ⇒ reçoit |
| 3 | `WalletOpsController` : `transfer`, `convert`, `receive-code` sans `checkWrite` (3 mutations, toutes survivent) | 31e écriture de la minute sur CHACUNE de ces routes ⇒ 429 |
| 4 | `EscrowService.java:97` : chemin « super » (`UNLIMITED` sans licence) retiré | TV à droit `super` : mise MBOKO permise ; et le test E1 (révoquée ⇒ refusée) |
| 5 | `limitSettle("global")` au lieu de l'adresse | deux adresses (`X-Forwarded-For` / `with(remoteAddr)`) ont chacune leur quota |
| — | autres survivantes, gardes redondantes avec le grand livre : `claim` sans `exp_at > ?` (course seulement), titulaire du blocage non revérifié dans `SettleService:71` (le grand livre refuse quand même, motif `BAD_TXN`) | test direct de la course d'échéance ; motif attendu précis |

Tuées (pour mémoire) : contrôle de `per` au règlement, `used` multiple de `per`, `remember` retiré, plafond MBOKO compté en NDEM, « blocage toujours connu » (contrôles sautés), échéance sans `exp_at`.

## Liste avant déploiement

1. Corriger E1 (droits de mise sans édition mémorisée ; activations exigées sur `/escrow`) et M1 (`cbe1` signé depuis la base).
2. Appliquer le correctif d'atomicité du code (M2) dans `JdbcLedger` + `TransferService`.
3. Ajouter `switch.settle`, l'alerte de gain anormal et `pay > 0 ⇒ used > 0` (M3, F1).
4. Exigences w22-04 notées : refus d'un `eid` déjà vu, fichiers de résultats nommés par `rid` (M4, F3) ; durcir le collecteur (liens, 403, `flock`).
5. Plafonds d'essai pour les transferts (M5) ou garder `switch.transfer = 0` jusqu'au niveau 2.
6. Avant d'allumer les sorties : C1, H1, H3, M5 de w22-02 corrigés (M5 surtout : liaison au premier venu).
7. Motif `RATE_LIMIT` sur les 429 ; lecture tolérante des motifs dans w22-07 (F5) ; index + purge de `wallet_recv_code` (F6).
8. Les 5 tests de mutation ci-dessus et les 6 tests de preuve ajoutés au dépôt.
9. Passer la liste MySQL 8.4 ci-dessus ; `reconcile` ok ; `retries()` = 0.
10. Livraison : module éteint (`CASTBRIDGE_WALLET_ENABLED=0`) à la première livraison ; `CASTBRIDGE_WALLET_PLAY_RESULT_PUBKEYS` posé avec la clé publique réelle du service de jeu ; cron du collecteur installé par le propriétaire après l'auto-test.

## Résumé (12 lignes)

1. Suite du portefeuille : 141 tests verts, 2 ignorés (MySQL) ; rien n'a tourné sur MySQL, Docker, le Kotlin ni le service de jeu.
2. Aucune création ni destruction de valeur trouvée dans w22-05 : chaque opération est une seule pose revérifiée sous verrou.
3. 6 tests de preuve écrits hors dépôt : les 6 échouent.
4. ÉLEVÉ E1 : licence illimitée révoquée ou poste parti ⇒ la TV mise encore des MBOKO ; essai échu ⇒ mise NDEM (prouvé).
5. MOYEN M1 : même clé, même montant, autre per/k ⇒ second `cbe1` signé, partie non réglable (prouvé).
6. MOYEN M2 : code de réception en deux transactions ; code brûlé (prouvé), rendu après succès possible ; correctif exact fourni.
7. MOYEN M3 : clé « résultat » compromise ⇒ tous les blocages ouverts exposés, sans plafond, alerte ni interrupteur.
8. MOYEN M4/M5 : un `cbe1` réutilisable dans deux salles (à fermer dans w22-04) ; aucun plafond propre à l'essai.
9. FAIBLE : paiement sans siège joué, rejeu de conversion en conflit, collecteur qui suit les liens (prouvé), code au journal.
10. w22-05 rend exploitables M5 (liaison au premier venu) et C1 de w22-02 : à corriger avant d'allumer les sorties.
11. Tests : 9 mutations survivent sur 15 (gel de conversion et de transfert, débits de 3 routes, chemin « super », débit de règlement).
12. Avant déploiement : E1, M1, M2, M3, puis la liste MySQL 8.4 et les correctifs de w22-02.
