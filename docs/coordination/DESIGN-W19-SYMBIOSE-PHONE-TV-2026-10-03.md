# Conception W19 — Symbiose CastBridge ↔ CastBridge-TV : un seul système, deux applications, des versions qui ne coïncident jamais

> Document de conception (Fable, architecte, 2026-10-03). **Aucun code n'est modifié par ce document.** Exécution par les cahiers `docs/agent-briefs/sonnet-w19-NN-*.md` (index `SONNET-WAVE19-INDEX.md`), sur ordre du coordinateur, dans les règles du gel W15 (cœur pur d'abord, câblage mince après, audit Opus sur confiance/PIN/identifiants/chemins de perte de données). Branche de référence : `integration/agents` (HEAD `43e1c10b`, téléphone 1.2.39 code 69, TV 0.14.26 code 78). Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = `android/core/src/test/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`. Tous les fichiers cités ont été lus le 2026-10-03 ; les chiffres marqués « estimé » n'ont pas été mesurés.
>
> **Exigence du propriétaire (2026-10-03, verbatim)** : « garantis une synchronisation symbiotique entre app TV et l'app phone ».
>
> **Lecture appliquée** : CastBridge (téléphone) et CastBridge-TV se comportent comme **un seul système** : ce que l'une fait, l'autre le comprend, l'affiche de la même façon et s'en remet, à travers les versions, les redémarrages, les réinstallations, les liaisons perdues (Wi-Fi de la maison, Wi-Fi Direct, Bluetooth), plusieurs téléphones, plusieurs TV, **et quand les deux applications ne sont pas de la même livraison**. Le propriétaire installe les versions à des moments différents (TV 0.14.24/0.14.25/0.14.26 et téléphone 1.2.38/1.2.39 coexistent sur le terrain et dans le `Download` de la clé USB) : l'écart de versions est l'état **normal**, pas une exception.

## 0. En vingt lignes

1. **Le mal commun des 17 régressions** : chaque application tient sa propre vérité et la perd en silence. R-17 (téléphone 1.2.39, TV 0.14.25) : la TV refuse un envoi sans rien journaliser et ferme le socket sans lire le corps ; le téléphone voit « Broken pipe », ne lit jamais la raison, et recommence **toutes les 25 s pendant plus de 30 minutes** (`C/xfer/TransferClient.kt:141` : une `IOException` sur `begin` ⇒ attente ⇒ `continue`, **sans borne**). R-10 : le PIN rangé par clé d'écran, pas par identité de TV. R-12/R-13 : deux idées de « même fichier » et deux plans de rangement. 0.14.23 : un sélecteur Quiz défectueux que le téléphone ne pouvait pas connaître. R-15/R-16 : la TV ralentit la copie, le téléphone peut croire à un blocage.
2. **Ce que ce document ajoute** : un **contrat de symbiose** (S-1…S-12, § 1) testable sur JVM ; **une autorité par fait** avec un compteur d'époque et une règle de réconciliation (§ 1.3) ; une **enveloppe de raison** unique `{code, message_fr, retryable, retryAfterMs}` dans les deux sens, un **registre des refus** côté TV lu par le téléphone quand le socket meurt avant la réponse (absorbe R-17), des **réessais bornés et visibles** (§ 3.3) ; une **poignée de main de version et de capacités** additive (`/api/hello` enrichi, en-tête `X-CB-App` du téléphone, cache par TV dans `PinBook`, encouragements « Mettre à jour la TV / le téléphone » qui **ne bloquent jamais** un pair ancien compatible, § 2) ; un **état de synchronisation** `GET /api/sync/state` miroité sur le téléphone et une carte **« Cohérence »** vert/orange/rouge (§ 3) ; une **matrice de compatibilité** JVM qui fait tourner le vrai client du téléphone contre le vrai serveur de la TV pour **chaque couple de versions pris en charge**, avec des « personas » enregistrées une fois par livraison à partir des étiquettes git (`tv-0.14.22…26-beta`, `phone-1.2.38/39-beta` existent) et de l'injection de pannes (§ 4) ; une **porte de livraison** `tools/release/check_compat.py` qui refuse l'étiquette quand la matrice est rouge ou qu'un changement de protocole n'a pas sa note de migration additive (§ 4.4) ; une **règle de symbiose** pour tout exécutant (§ 5).
2′. **Garantie centrale S-REPRISE** (exigence ajoutée le 2026-10-03 : « il faut garantir les reprises / renégociation quel que soit le scénario ») : une **matrice de 30 scénarios** (§ 1.4, RS-01…RS-30 : coupures par voie, changement de voie, veille, app tuée, TV redémarrée/éteinte/réinstallée/mise à jour, DHCP, clé USB, disque plein, horloge, session 404, PIN/jeton, essai, Wi-Fi Direct, lecture qui ralentit, jours d'absence, « Déplacer », doublons, source modifiée), chacune avec l'état des deux applications, la procédure, le délai, le test et l'écran ; un **protocole de reprise unique** (§ 3.5 : identité stable `(sessionId, manifestRoot, queueEpoch, tvId)`, carte de blocs persistée des deux côtés, **la TV est autorité sur ce qu'elle a écrit et vérifié, le téléphone sur la source**, interrogation **avant** tout renvoi, renégociation des capacités/route/identifiants/taille de bloc/vitesse, quatre états finaux seulement : Terminé vérifié / Repris / Abandonné avec raison / En attente de la TV avec durée) ; un **chaos de reprise à graine** (§ 4.2) dans la porte : 5 fichiers, 40 évènements aléatoires, invariant « chaque octet arrive exactement une fois, vérifié, ou l'échec est visible avec sa cause des deux côtés ». Ce qui existe déjà (reprise par blocs R-08/R-09/R-12, sidecar, `Scheduler`) et les trous (garde de source, identité par racine, relecture de carte après coupure, `slice` par session, R-17) : § 3.5.6 ; **w19-01 est la première brique**, w19-13 la seconde.
3. **Chiffres du contrat** : raison visible des deux côtés en **≤ 5 s** (LAN/Wi-Fi Direct) ou **≤ 30 s** (Bluetooth) ; réessais **≤ 12 tentatives ou ≤ 10 min** par élément puis échec visible avec « Réessayer » ; divergence détectée en **≤ 30 s** au premier plan ; redémarrage de l'un ou l'autre pendant une copie ⇒ même état affiché des deux côtés en **≤ 60 s** ; fenêtre de versions prise en charge **TV N-3…N+1 pour un téléphone N** et réciproquement (par numéro de protocole, § 2.2), hors fenêtre : encouragement, jamais blocage des parcours de base (code, copie, file).
4. **Ce qui existe et sert de fondation** : `TvCredential`/`CredentialGate` (un identifiant refusé n'est jamais rejoué), `LinkMachine` (courbes 1,5→60 s, hystérésis), `PinBook` (identité de TV), `ContentIndex` + `/api/have` (identité par empreinte), `FilingPlan`, `LotManifest` (charge canonique hachée), `TransferHost.slowedNote`, `TvSignal` (vert/orange/rouge/noir), le harnais W14 (`TvSim` sur vraie `ReceiverServer` port 0, `restart()`), `tools/release/check_versions.py` et `tag-plan.sh`. **Rien n'est remplacé** : tout est additif.
5. **Effort** : 13 cahiers (≈ 23 agent·jours, ≈ 20 $), dont 10 pendant le gel (cœur pur, harnais, outils, docs) et 2 après (câblage mince téléphone et TV), 1 conditionnel ; 3 audits Opus obligatoires (w19-01 : chemin de données et `ReceiverServer` ; w19-13 : protocole de reprise, perte de données ; w19-10 : journalisation TV sans secret), 3 échantillons.
6. **Honnêteté** : `tools/smoke/smoke.py --tv fake` et `FakeTvMain` **n'existent pas** dans le dépôt (seuls w14-01, w14-05, w14-14 ont un rapport) ; la branche `claude/fix-broken-pipe` (R-17) **n'a aucun commit** au-delà de `integration/agents` au moment de la lecture ; R-17 n'a pas encore sa ligne dans `docs/REGRESSIONS.md` ; rien n'a été mesuré sur un appareil.

## 1. Le contrat de symbiose

### 1.1 Les garanties (S-1…S-12)

Chaque garantie est un **contrat observable** : préconditions, ce qui doit se voir sur les deux écrans, un délai, le test JVM qui la porte (cahier entre parenthèses).

| id | Garantie | Chiffre | Test (cahier) |
|---|---|---|---|
| **S-1** | **Aucun échec muet.** Tout refus, abandon ou arrêt a un **code de raison** stable (`Reason`, § 3.1), un message français, et il est **visible et identique** sur les deux applications. La TV journalise chaque refus à INFO (`route · code · http · pair masqué`), jamais un secret. | ≤ 5 s (LAN/WD), ≤ 30 s (BT) après l'évènement | `ReasonEnvelopeTest`, `RejectionLedgerTest` (w19-01) ; J-SYM-2 (w19-08) |
| **S-2** | **Aucun réessai sans fin.** Toute boucle de nouvel essai est **bornée et comptée** à l'écran (« essai 3/12 »). Refus non réessayable ⇒ 0 réessai. `retryAfterMs` respecté. Au-delà de la borne : échec visible avec la **dernière raison** et un bouton « Réessayer » (geste humain). | ≤ 12 tentatives **ou** ≤ 10 min cumulées par élément ; courbe 2, 4, 8, 15, 30, 60 s ±25 % | `RetryPolicyTest` (w19-01) ; J-SYM-8 |
| **S-3** | **Aucune vérité divergente.** Chaque fait a **une seule autorité**, un **compteur d'époque** et une **règle de réconciliation** (§ 1.3). Le miroir ne décide jamais contre l'autorité ; il affiche « d'après la TV / d'après le téléphone » quand il n'a pas encore reconcilié. | divergence détectée ≤ 30 s au premier plan, ≤ 5 min en fond | `CoherenceTest`, `EpochsTest` (w19-06) ; J-SYM-5, J-SYM-6 |
| **S-4** | **Idempotence.** Tout message répété ou rejoué est inoffensif : identifiant de transfert, `begin` qui répond `done`, blocs par empreinte, dédoublonnage par `(sha256, taille, tvId)`, lots par `(id, version, sha256)`, HELLO répété, `finish` second appel = 503 `verifying` (existant). | 0 effet de bord sur un rejeu ×3 | `IdempotenceTest` (w19-04) |
| **S-5** | **Sûreté au redémarrage.** L'une ou l'autre application qui redémarre (ou est réinstallée) au milieu d'un transfert **reprend ou abandonne de façon cohérente** ; les deux montrent le **même résultat** (reprise au même bloc, ou échec avec le même code). | ≤ 60 s après le retour | J-SYM-3 (w19-08) ; `FaultProxyTest.restartMidTransfer` (w19-04) |
| **S-6** | **Tolérance à l'écart de versions.** Téléphone de protocole P avec TV de protocole P-3…P+1 (et réciproquement) : les parcours de base (P-05 code, P-11 copie, P-39 file, P-45 PIN gardé) **passent** ; les fonctions absentes sont **nommées** (encouragement), jamais devinées. Hors fenêtre : encouragement + parcours de base **toujours tentés**. | fenêtre = 4 protocoles ; 0 blocage dur | matrice `CompatMatrixTest` (w19-03) ; J-SYM-1 |
| **S-7** | **Mise à jour et rétrogradation sans perte.** Les formats sur disque (`PinBook`, `trusted_phones.txt`, `.filing`, file d'envoi, coffre, cache de capacités) **gardent les clés inconnues** et sont relus par la version précédente ; un APK plus ancien refuse de s'installer par-dessus (409, existant P-30) mais **ne détruit rien** s'il est forcé par `adb`. | 0 donnée perdue à N→N+1→N | `FormatForwardCompatTest` (w19-07), fixtures gelées |
| **S-8** | **Indépendance de l'horloge.** Aucune décision de symbiose ne compare une heure du téléphone à une heure de la TV : durées sur horloge **monotone locale**, époques = compteurs, `serverTime` informatif seulement. Écart ±24 h sans effet (hors activation, hors de ce document). | ±24 h | `FaultProxyTest.clockSkew` (w19-04) |
| **S-9** | **Même identité de fichier, même rangement.** L'identité d'un fichier est `(sha256, taille)` sur les deux applications ; nom et date sont des **attributs**. Le plan de rangement a une **version** (`FilingPlan.VERSION`) et un **hachage de réglages** comparés dans l'état de synchronisation. | 0 recopie d'un contenu identique ; même dossier calculé des deux côtés | `FileIdentityTest`, `FilingAgreementTest` (w19-07) |
| **S-10** | **Capacité déclarée avant usage.** Après W19, le téléphone **n'appelle jamais** une route que la TV n'a pas déclarée (`caps`) ; pour chaque capacité absente, un **repli défini** (§ 2.4). Une TV ancienne sans `caps` = ensemble de capacités **déduit de son `v`/version** (table figée). | 0 404/501 « surprise » dans la matrice | `FeatureGateTest` (w19-02) |
| **S-11** | **Lots et questions d'accord.** Les lots du téléphone et les lots embarqués/installés de la TV se comparent par `(feature, scope, version, sha256)` ; un désaccord est un **plan** (envoyer, retirer, rien) jamais une erreur muette ; les identifiants de questions Quiz sont stables à travers une mise à jour (`QuizLots.questionHash`, existant). | plan calculé en < 1 s pour 500 lots | `LotAgreementTest` (w19-07) ; J-SYM-6 |
| **S-12** | **Plusieurs pairs, faits cloisonnés.** Chaque fait miroité est indexé par `(tvId)` côté téléphone et `(phoneKey)` côté TV ; un 401 de la TV B ne touche jamais la TV A (R-10, existant) ; une raison de refus porte le pair masqué, jamais l'adresse complète. | 0 fuite croisée dans 200 courses | `PinBookTest` (existant) + `CoherenceTest.twoTvs` (w19-06) |
| **S-REPRISE** | **Reprise et renégociation garanties quel que soit le scénario** (exigence du propriétaire, 2026-10-03 : « il faut garantir les reprises / renégociation quel que soit le scénario »). Pour **chaque** ligne de la matrice § 1.4, un transfert interrompu finit dans **un** des quatre états finaux (§ 3.5.5) : **Terminé vérifié**, **Repris** (en cours, même session ou session renégociée, sans octet perdu ni dupliqué sur le disque final), **Abandonné avec raison** (code `Reason`, visible des deux côtés), **En attente de la TV** (avec la durée écoulée et la borne). Ce qui est partiel n'est **jamais** présenté comme terminé ; aucune attente silencieuse ; aucune boucle infinie. | état final atteint ≤ 60 s après le retour de la condition (≤ 5 min en fond) ; invariant « chaque octet exactement une fois, vérifié » | matrice § 1.4 ; `ChaosResumeTest` (graine, w19-04) ; `ResumeProtocolTest` (w19-13) ; J-SYM-3, 10…14 |

### 1.4 S-REPRISE : la matrice des scénarios de reprise et de renégociation

Chaque ligne est un contrat : la condition, l'état attendu sur les **deux** applications pendant la coupure, la procédure de reprise (§ 3.5), le délai maximal jusqu'à l'état final, le test et ce que l'usager voit. Colonne « Existe » : **E** = déjà couvert par le code lu (`PartAssembler` sidecar, `begin` par nom, `Scheduler`, `ResumableUpload`, `CredentialGate`, `TransferQueue` persistée R-09, `MoveProof` R-12), **P** = partiel, **T** = trou. « JVM » = rejouable sur le harnais (horloge, radios factices, `FaultProxy`) ; « H » = relevé humain équivalent (`docs/test-plans/CHECKLIST-HUMAINE-LIVRAISON.md`, à étendre par w19-11).

| # | Scénario | Téléphone pendant | TV pendant | Reprise (§ 3.5) | Délai max | Existe | Test | Écran (téléphone / TV) |
|---|---|---|---|---|---|---|---|---|
| RS-01 | Coupure du Wi-Fi de la maison en plein bloc | élément « En attente de la TV · 0:12 » ; % figé, ne recule pas | session gardée ; carte « Réception en pause : le téléphone ne répond plus » après 20 s | reconnexion ⇒ `state(withHashes)` ⇒ renvoi des blocs manquants seulement | 60 s après retour | E (bloc) / P (carte TV) | `FaultProxy.rstMidBody` ; J-SYM-3 | « Liaison perdue : le Wi-Fi du téléphone » / « En pause » |
| RS-02 | Coupure du groupe Wi-Fi Direct en plein bloc (W18) | idem + « Wi-Fi Direct : re-jonction… » | idem ; groupe recréé ou gardé (bail) | re-jonction ×1 puis courbe ; après 3 échecs/10 min ⇒ **changement de voie** BT (RS-04) | 20 s re-jonction, 60 s voie | E (R-14 `rerouteAfterLoss`) | `RadioSim` W18 w18-06 + `FaultProxy` | « Par Bluetooth (lent) : Wi-Fi Direct perdu » |
| RS-03 | Coupure Bluetooth (CBT1) en plein bloc | « Bluetooth coupé » / « TV introuvable » (`LinkMachine`) | offset reçu gardé | reprise à l'offset annoncé (CBT1, existant) | 60 s | E | `InFlightTest` (existant) | « Reprise par Bluetooth » |
| RS-04 | **Changement de voie en cours** LAN→WD→BT et retour | « Changement de voie : <voie> » ; aucun octet renvoyé déjà confirmé | même session (`id`) : la TV ne connaît pas la voie | `state()` AVANT tout renvoi ; `maxStreams`/`slice` **renégociés** (`caps` de la voie) ; blocs confirmés gardés | 30 s | P (session reprise ; renégociation de `slice` **T**) | `ChaosResumeTest.routeFlip` | « Par Wi-Fi Direct » → « Par Bluetooth » |
| RS-05 | Wi-Fi ↔ données mobiles sur le téléphone | la route de données **ne suit jamais** le cellulaire ; « En attente de la TV » | — | reprise dès que le LAN/WD répond ; `BoundRoute` par requête (R-14) | 60 s | E | `FaultProxy.closeEarly` + `FakeEnv.wifiUp` | « Le Wi-Fi du téléphone a été perdu » |
| RS-06 | Veille / écran éteint du téléphone | FGS `dataSync` continue ; si Android gèle : élément « Repris » à l'allumage | idem RS-01 | reprise au réveil (job 15 min, ouverture, écran) | 60 s après réveil | E (FGS) / H | H (écran éteint 10 min) | notification de file persistante |
| RS-07 | App tuée (OOM, « Arrêt forcé », mise à jour de l'APK du téléphone) | file **relue** (R-09) ; élément « Repris » ; aucun doublon d'élément | session gardée ou 404 ⇒ `begin` de reprise | `TransferIdentity` (§ 3.5.1) relue ⇒ `state()` ⇒ renvoi des manquants | 60 s après relance | E (R-09) / P (identité = nom aujourd'hui) | J-SYM-3 (téléphone) ; `ChaosResumeTest.killPhone` | « Reprise de « film.mkv » (38 %) » |
| RS-08 | Redémarrage de la TV (P-16) | « <TV> ne répond plus… Allumez-la » ≤ 40 s | sidecar `.cbx`+map relu ; `id` perdu ⇒ 404 | 404 ⇒ `begin` même manifeste ⇒ carte de blocs renvoyée ⇒ reprise | 60 s après retour | E | `TvSim.restart()` (existant) | % repart de ~30 %, jamais 0 % |
| RS-09 | TV éteinte puis rallumée (heures) | élément « En attente de la TV · 2 h 14 » puis **pause** après 10 min (borne S-2), reprise au prochain contact (job, ouverture, ACL) | idem RS-08 ; sidecar conservé ≤ 7 j (`maxAgeMs`) | comme RS-08 ; si sidecar purgé ⇒ recommence à 0 **en le disant** | 60 s après contact | E / P (texte « pause ») | `ChaosResumeTest.longOutage` (horloge) | « En pause : la TV est éteinte. Reprise automatique. » |
| RS-10 | TV change d'adresse IP (DHCP) | « adresse retrouvée » ; même session | — | HELLO BT / mDNS / `wd` ⇒ nouvelle base ; `PinBook` par `tvId` (R-10) ; `state()` | 60 s | E (P-18) | `FakeTv.ips` | — |
| RS-11 | Téléphone change de réseau (autre box) | comme RS-05 ; si la TV n'y est pas : « TV sur un autre réseau » | — | route WD/BT (W18) | 60 s | P (texte) | `FakeEnv` | « Votre téléphone a changé de réseau » |
| RS-12 | Clé USB de la TV retirée puis remise (ou autre volume) | élément « En attente : la clé « X » est retirée » (503 `VOLUME_REMOVED`) | carte « Clé retirée : les envois reprendront à son retour » | `missingOwner` ⇒ 503 réessayable ; retour ⇒ même sidecar ; **autre** volume ⇒ `begin` propose les options (`options`) : jamais de choix muet | 60 s après retour | E (503) / P (autre volume = question) | `FaultProxy.inject(503)` + `VolumeRegistry` sim | « La clé a été retirée » |
| RS-13 | Disque plein en cours de copie | élément **Abandonné** `NO_SPACE` + « il manque N » + option autre volume | carte « Plus de place sur <volume> » ; partiel gardé | 0 réessai ; relance humaine (ou autre volume) ⇒ `begin` reprend le partiel | immédiat | E (507) / P (option) | `FaultProxy.inject(507)` | « Il manque 1,2 Go sur la clé » |
| RS-14 | Panne d'alimentation (TV et/ou téléphone) | comme RS-07 + RS-08 | sidecar fsync (W15 `SafeFile`) : au pire le dernier bloc est renvoyé | comme RS-08 | 60 s après retour | E | `TvSim.restart()` + file relue | — |
| RS-15 | Horloge décalée (±24 h, l'une ou l'autre) | aucun effet : durées monotones | aucun effet ; jeton : marge 60 s + durées sur horloge téléphone (existant) | — | — | E / P (époques mono + `bootId`, w19-06) | `FaultProxy.clockSkew` | — |
| RS-16 | Session expirée côté TV (404 `SESSION_UNKNOWN`) | « la TV a perdu le transfert : reprise » | éviction (D-W15-01a) ou purge | `begin` même manifeste ⇒ sidecar par nom ⇒ carte renvoyée ; 6 fois max | 30 s | E (`attempts > 6`) | `CopyQueueServerTest` + `FaultProxy.inject(404)` | — |
| RS-17 | **Jeton/PIN change ou est retiré pendant le transfert** | identité **même TV** (`installId`/`tvId` égal) : jeton renouvelé par HELLO **sans ressaisie**, reprise ; code PIN changé : **une** demande ; téléphone **retiré** : « Réassocier », élément « En attente » | `trust` +1 ; 401 non compté pour un jeton | `CredentialGate` (jamais rejouer un refus) ; `state()` après nouvel identifiant ; **renégociation d'identité** si `installId` diffère (TV réinstallée : RS-19) | 60 s (jeton) / humain (PIN) | E (P-07, P-10, R-10) | J-SYM-4 ; `LinkDriverTest` | « Autorisation de la TV à renouveler » / « Code de la TV à saisir » |
| RS-18 | **TV mise à jour pendant un transfert** (N→N+1, même signature) | « La TV se met à jour… » puis reprise **ou** `Abandonné(UNSUPPORTED)` si le format de sidecar a changé **sans lecteur de l'ancien** (interdit par S-7) | sidecar relu par N+1 (formats avant-compatibles) ; `protocol` peut changer ⇒ le téléphone **renégocie caps** | `hello` ⇒ `caps` rafraîchies ⇒ `state()` ⇒ reprise | 90 s | P (sidecar relu : à prouver par fixture) | `FormatForwardCompatTest` (w19-07) ; `ChaosResumeTest.tvUpgrade` (persona N puis HEAD sur le même dossier) | « TV mise à jour : reprise » |
| RS-19 | TV **réinstallée** (registre perdu, autre `installId`) pendant un transfert | « TV réinitialisée : Réassocier » ; élément « En attente » ; **jamais** de renvoi avec l'ancien jeton | registre vide ; sidecar peut survivre | renégociation complète (appairage/PIN W18) puis `begin` ; si le sidecar a survécu : reprise, sinon 0 % dit | humain | E (P-02) / P (texte) | `FakeTv.reinstall()` | « La TV a été réinstallée » |
| RS-20 | Téléphone réinstallé / mis à jour pendant une file | mis à jour : file relue (formats avant-compatibles, S-7) ; réinstallé : file **perdue** (données privées) ⇒ la TV garde ses partiels, l'usager relance ; **aucun doublon** grâce à `begin`/`have` | partiels gardés ≤ 7 j | `begin` d'un fichier déjà partiel ⇒ reprise ; déjà complet ⇒ `done` | 60 s | E | `FormatForwardCompatTest` ; `CopyQueueTest` | — |
| RS-21 | Deux téléphones vers une TV ; un téléphone vers deux TV | sessions distinctes ; faits par `tvId` ; un refus de B n'altère pas A | sessions par pair ; `NAME_TAKEN` si même nom autre taille ; **même contenu** ⇒ `done`/`have` | identité par `(sha256,taille)` ; jamais de fusion de sessions | — | E (R-09 réservation atomique, R-10) | J-SYM-9 | — |
| RS-22 | **Refus de la TV** (tout 4xx non réessayable) | `Abandonné(code)` immédiat, raison en français, « Réessayer » | registre + INFO + carte « Refusé : … » | 0 réessai (§ 3.3) ; Broken pipe ⇒ lecture du registre (§ 3.2) | ≤ 5 s | **T** (R-17) ⇒ **w19-01** | J-SYM-2, J-SYM-8 | les deux écrans, même code |
| RS-23 | TV d'essai qui atteint son quota / fin d'usage en cours de copie | `Abandonné(TRIAL_CLOSED)` + « Passez en production » **une fois** (P-19) | 403 `{"trial":true}` + carte ; partiel supprimé ou gardé **dit** | 0 réessai ; relance après activation ⇒ `begin` | immédiat | E (403) / P (quota atteint **pendant** : à tester) | `ReceiverServer(routeGuard)` + bascule en cours | « Version d'essai : copie arrêtée » |
| RS-24 | Perte du groupe Wi-Fi Direct **pendant un cast** (W18 § 5.3) | lecture s'arrête ; re-jonction ; `playUrl` à la position connue ; > 20 s ⇒ proposition « Copier et lire » | lecteur en « buffering » ≤ 30 s (existant) | W18 | 20 s | P (W18 w18-02) | w18-06 | « Liaison Wi-Fi perdue : rapprochez-vous » |
| RS-25 | Lecture vidéo sur la TV qui **ralentit** la copie (R-15/R-16) | « Copie ralentie pour ne pas gêner la lecture » ; **jamais** « bloqué » tant que `received` avance (≥ 1 o / 30 s) ; pause TV bornée 10 s | `slowed` ; plancher 512 Ko/s ; icône | pas une reprise : une **renégociation de vitesse** (`receiveCap`, 429 `retryMs`) | — | E (TV) / P (téléphone : seuil de blocage) | J-SYM-7 ; `BufferGovernorTest` | les deux écrans |
| RS-26 | Reprise après plusieurs heures/jours (file persistée) | file relue ; éléments « En pause » avec durée ; reprise au contact | sidecar ≤ 7 j ; au-delà purge ⇒ 0 % **dit** | comme RS-09 | 60 s après contact | E (R-09) / P (purge dite) | `ChaosResumeTest.longOutage` | « Reprise après 2 jours » |
| RS-27 | « Déplacer » interrompu | original **jamais** supprimé sans `MoveProof` (R-12) ; reprise comme une copie ; suppression seulement après `finish` vérifié + preuve | partiel gardé | existant | — | E (R-12) | `MoveFallbackServerTest` (existant) | « L'original est conservé jusqu'à la vérification » |
| RS-28 | Doublons après reprise | `begin` ⇒ `done` si déjà complet ; `/api/have` par empreinte ; même contenu sous un autre nom ⇒ non recopié | index | S-9 | — | E (R-12) | `DedupDecisionTest` | « Déjà sur la TV » |
| RS-29 | **Fichier source modifié ou supprimé pendant la copie** | supprimé : `Abandonné(SOURCE_GONE)` « Accès au fichier perdu » (R-09) ; **modifié** : `Abandonné(SOURCE_CHANGED)` et la TV **jette** le partiel (jamais un fichier mi-ancien mi-nouveau) | `abort` reçu ⇒ partiel supprimé | **garde de source** à chaque reprise : taille + mtime + empreinte des bords (§ 3.5.2) | immédiat | E (supprimé) / **T** (modifié) ⇒ w19-13 | `ResumeProtocolTest.sourceChanged` | « Le fichier a changé pendant la copie : relancez-le » |
| RS-30 | Changement de **taille de bloc / nombre de voies** entre deux versions ou deux voies | la carte de blocs confirmés reste valable **si** `slice` identique ; sinon la TV répond la carte dans **sa** granularité et le téléphone recommence les blocs non alignés **en le disant** | `caps` par voie | renégociation `caps` à chaque `begin` (existant : `caps()` lu avant `begin`) ; `slice` figé **par session** (§ 3.5.4) | 30 s | P | `ChaosResumeTest.routeFlip` | — |

**Ce que la JVM ne simule pas** (et le relevé humain équivalent) : les radios réelles (RS-01/02/03/05/06/24 : coupure physique, re-jonction P2P, Doze de Samsung) ⇒ relevé **H-REPRISE** de 15 min dans la liste humaine : (1) couper le Wi-Fi de la box à 30 % d'un envoi de 500 Mo, remettre à 2 min ; (2) débrancher la TV à 30 %, rallumer ; (3) éteindre l'écran du téléphone 10 min ; (4) « Arrêt forcé » de CastBridge à 30 % ; (5) retirer la clé USB à 30 %, remettre ; (6) « Déplacer » puis couper la TV avant la fin. Attendu à chaque fois : même % des deux côtés après reprise, aucun 0 %, aucun « Terminé » sans fichier complet, et le journal INFO de la TV sans secret.

### 1.2 Vue d'ensemble

```
  CastBridge (téléphone)                                   CastBridge-TV
  ┌──────────────────────────────┐                        ┌──────────────────────────────┐
  │ autorité : file d'envoi,     │   HELLO BT / /api/hello │ autorité : index bibliothèque │
  │ cache capacités (PinBook),   │ ◄──── caps, proto ────► │ (sha256,taille), plan de      │
  │ fiche PIN/jeton par tvId,    │   X-CB-App: ver, caps   │ rangement appliqué, lots      │
  │ lots téléchargés, octets     │                        │ installés, édition, lecteur,  │
  │ ENVOYÉS                      │   GET /api/sync/state   │ octets REÇUS, registre de     │
  │                              │ ◄──── époques, refus ── │ confiance, registre des refus │
  │ miroir : tout ce qui est à   │                        │ miroir : file du téléphone    │
  │ droite, horodaté mono local  │   enveloppe de raison   │ (sessions actives), version   │
  │                              │ ◄──── {code,...} ─────► │ du téléphone                  │
  │ Cohérence : compare(époques) │                        │ ligne « Téléphone » : version │
  └──────────────────────────────┘                        └──────────────────────────────┘
          ▲  même code, même phrase, même couleur TvSignal, dans les 5 s (LAN/WD) / 30 s (BT)  ▲
```

### 1.3 Une autorité par fait

| Fait | Autorité | Époque (compteur) | Miroir | Réconciliation quand ils diffèrent |
|---|---|---|---|---|
| Index de la bibliothèque TV et identité des fichiers | **TV** (`ContentIndex`, `FiledIndex`) | `library` (+1 à chaque ajout/retrait/rangement) + `libraryRoot` = sha256 des `(sha256,taille)` triés (16 hex) | téléphone : cache de la liste (`TvDedupe`, écran Bibliothèque) | époque ou racine différente ⇒ le téléphone **relit** `GET /api/library` + `/api/have` avant toute décision de doublon ; jamais l'inverse |
| Plan de rangement | **cœur partagé** (`FilingPlan.VERSION`, règles pures) ; **application** = TV | `filing` = sha256(`VERSION` + réglages actifs : rangement on/off, langue) | les deux calculent ; la TV applique | hachages différents ⇒ carte Cohérence orange « Le rangement de la TV suit une autre règle (TV 0.14.24) : les dossiers peuvent différer » ; le téléphone **n'impose pas** son plan |
| File d'envoi (ordre, états, causes) | **téléphone** (`TransferQueue`, persistée R-09) | `queue` (+1 à chaque mutation) | TV : sessions actives (`TransferHost`) | une session TV sans élément de file ⇒ `abort` par le téléphone au prochain état ; un élément de file « en cours » sans session TV ⇒ `begin` de reprise (existant) ; après 2 cycles ⇒ échec `SESSION_UNKNOWN` visible |
| Progression d'une copie | **TV** pour les octets reçus ; téléphone pour les octets envoyés | — (valeur, pas époque) | affichage % = **reçu par la TV** dès que `state` répond ; sinon envoyé, marqué « (envoyé) » | écart > 1 tranche pendant 20 s ⇒ ligne « la TV n'a pas encore confirmé » ; jamais un % qui recule |
| « Copie ralentie » | **TV** (`PlaybackPriority`, `slowedNote`) | — | téléphone : état « Copie ralentie pour ne pas gêner la lecture » | le téléphone **suspend son détecteur de blocage** tant que `slowed` est vrai et que `received` avance d'au moins 1 octet / 30 s (R-15/R-16) |
| Signalétique (vert/orange/rouge/noir) | **chacun pour sa liaison**, sémantique unique `TvSignal` | — | la couleur du **même fait** ne peut différer : la table § 3.4 fixe la couleur par code | désaccord = défaut de test (`SignalAgreementTest`) |
| Confiance : téléphones, jetons, `installId` | **TV** (`TrustRegistry`) | `trust` (+1 à chaque ajout/retrait/rotation/réinstallation) | téléphone : `SavedTv`, jeton, `PinBook` | époque différente ⇒ HELLO ; `installId` différent ⇒ « TV réinitialisée » (existant) |
| Code PIN de la TV | **TV** (`TvPrefs`) | inclus dans `trust` | téléphone : `PinBook` par `tvId` (R-10) | 401 `AUTH_BAD_PIN` ⇒ une seule demande (R-10) ; `AUTH_LOCKED` ⇒ attente affichée, jamais l'assistant |
| Édition / quota | **TV** (`TrialPolicy`, activation) | `edition` (+1 à chaque changement d'état d'activation) | téléphone : fiche de TV | le téléphone adapte ses boutons (P-37) ; un 403 `TRIAL_CLOSED` n'est jamais réessayé |
| Lots installés (Apprendre, Langues, Quiz) | **TV** (`LotStore.manifest()`) | `lots` = hachage canonique de `LotManifest` (existant) | téléphone : `OwnedLots`, `DeliveryQueue` | hachage différent ⇒ `LotAgreement.plan` (envoyer / retirer / rien) ; un lot refusé porte `LOT_REJECTED` + cause |
| État du lecteur TV | **TV** (`/api/info.player`) | — | télécommande du téléphone | existant (`RemoteClock`) |
| Capacités et version du pair | **chacun pour soi**, déclaré au pair | `proto`, `versionCode` | cache `cap.<tvId>` (téléphone), ligne « Téléphone » (TV) | cache plus vieux que 24 h ou `versionCode` différent au `hello` ⇒ rafraîchi avant toute décision de capacité |

## 2. Poignée de main de version et de capacités

### 2.1 Ce qui existe (vérifié)

- `GET /api/hello` (`C/tv/ReceiverServer.kt:554-555`) répond **exactement** `{"app":"castbridge-tv","v":"0.7","pinRequired":true|false}` ; `VERSION = "0.7"` (`:1746`) est un numéro de **protocole**, inchangé depuis des dizaines de livraisons : **aucune version d'application, aucun code de build, aucune liste de capacités** en HTTP. `GET /api/transfer/caps` (`:919-920`) ne décrit que le transfert (`version:1`, `maxStreams`, `slice`).
- HELLO Bluetooth (`C/trust/HelloHandler.kt:53`) : `HelloInfo(tvName, version, mdnsName, token, ttl, link(), installId)` ; `LinkInfo` (`C/tv/BtProtocol.kt:394`) porte `wd.cap`, `wd.err` (R-14). La **version d'application** de la TV y voyage, mais seulement pour un pair de confiance, et le téléphone ne la range nulle part de durable.
- Le téléphone n'envoie **rien** sur lui-même : la TV ignore sa version (impossible de dire « votre téléphone est trop ancien » ni de journaliser quel téléphone a été refusé).
- `tvctx-01` (identifiant `id` dans `/api/hello`) : cahier écrit, **non exécuté** (aucun rapport) ; w19-02 le reprend.
- Le téléphone lit `/api/hello` à six endroits (`S/WdManualCard.kt:158`, `S/BtGatewayCard.kt:79`, `S/AutoWifiDirect.kt:486`, `S/BtUploadService.kt:136`, …) sans jamais en garder le contenu.

### 2.2 Ce qui est ajouté (additif, aucun ancien pair cassé)

**Réponse `/api/hello` enrichie** (les anciens téléphones ignorent les clés inconnues : analyse par `TvClient.str/num` ; `v` reste `"0.7"` tant que la **forme** des réponses existantes ne change pas) :

```json
{"app":"castbridge-tv","v":"0.7","pinRequired":true,
 "protocol":8,"appVersion":"0.14.27-beta","versionCode":80,"id":"3f9a1c2e",
 "caps":["envelope","sync","dedup","filing2","queue","cbtn","wd","wdpersist","slowed","copybadge","lots1","quizlots","transfer2","resume","diagwd","pinhello"],
 "edition":"trial|prod|super|grace|none","storage":"usb|internal|none","routes":["lan","wd","bt"],
 "lots":{"schema":1},"filing":{"version":3}}
```

- `protocol` : **entier** par livraison de protocole (8 = W19 ; 7 = tout ce qui répond `v:"0.7"` sans `protocol`) ; `v` n'est plus jamais modifié (il casserait des analyseurs anciens).
- `caps` : chaînes **stables** définies dans `C/sync/Caps.kt` (une constante, un commentaire « depuis quelle version », un repli). La table figée `Caps.impliedBy(appVersion)` donne les capacités d'une TV **ancienne** qui n'en déclare pas (0.14.22 : rien ; 0.14.24 : `queue`, `dedup`, `filing2` ; 0.14.25 : + `cbtn`, `slowed` ; 0.14.26 : + `copybadge`) : à **vérifier** par l'exécutant au `git show <tag>:…` de chaque étiquette, pas de mémoire.
- `id` : identifiant public court de la TV (8 hex, `InstallKey` W7 / tvctx-01), **pas** l'`installId` de confiance.
- Aucun secret, aucune adresse : `/api/hello` reste publique.

**En-tête de requête du téléphone** (additif ; une TV ancienne l'ignore) : `X-CB-App: castbridge/1.2.40 (70); proto=8; caps=envelope,sync,dedup,queue`. Posé par `TvCredential` (une seule voie, existant) sur **toute** requête HTTP, et une ligne `app=` dans le HELLO Bluetooth (drapeau additif comme `HELLO_HAS_INSTALL_ID`). La TV le journalise avec chaque refus et l'affiche sur la page « Téléphone ».

**Cache côté téléphone** : `PinBook` gagne la clé `cap.<tvId>` = `proto \t versionCode \t appVersion \t caps(csv) \t edition \t seenMono` (même toit que `pin.<tvId>`, `wd.<tvId>` : hors sauvegarde, effacé par « Oublier »). Lecture pure `TvCaps.of(tvId)` ; **périmé après 24 h** ou dès qu'un `hello` montre un autre `versionCode`.

**Encouragements** (`UpdateNudge.decide(me, peer, wanted)` pur) :

| Situation | Téléphone affiche | TV affiche | Bloque ? |
|---|---|---|---|
| TV sans la capacité voulue (ex. `dedup` absent) | « Cette TV (0.14.24) ne sait pas encore éviter les doublons : la copie partira quand même. Mettez-la à jour depuis le Download de la clé USB. » | — | **non** |
| TV de protocole < P-3 | « CastBridge-TV à mettre à jour (0.14.19) : les fonctions récentes sont désactivées ; code, copie et file marchent. » | — | non |
| Téléphone de protocole < P-3 (lu dans `X-CB-App`) | — | page « Téléphone » : « CastBridge 1.2.31 sur Galaxy : mise à jour conseillée (doublons, file, rangement). » | non |
| Téléphone de protocole > P+1 (TV très ancienne) | « Cette TV est trop ancienne pour la file et les doublons ; copie simple seulement. » | — | non (copie simple reste) |
| `ERR_MAGIC` HELLO (existant) | « CastBridge-TV à mettre à jour » (inchangé) | — | déjà géré |

Règle : **un encouragement ne s'affiche qu'une fois par jour et par TV**, jamais en pleine copie, toujours avec la **raison exacte** (la fonction qui manque), jamais « version obsolète » seul.

### 2.3 Fenêtre prise en charge

```
   protocole de la TV →      P-4   P-3   P-2   P-1    P    P+1   P+2
   téléphone P              [enc] [ ok ] [ ok ] [ ok ] [ok] [ ok ] [enc]
   ok  = tous les parcours de base + fonctions communes aux deux ; matrice VERTE exigée
   enc = encouragement + parcours de base tentés ; matrice « meilleur effort » (jaune toléré, jamais rouge sur P-05/P-11)
```

Aujourd'hui tout le parc est à P = 7 (aucune TV ne déclare `protocol`) : la première livraison W19 (P = 8) est **compatible avec tout le parc** par construction (tout est additif). Le numéro P n'augmente que lorsqu'une **forme** change (nouveau champ obligatoire, route renommée, sens d'un code modifié) et exige une note dans `docs/PROTOCOL-CHANGES.md` (§ 4.4).

### 2.4 Repli par capacité absente (ce que fait le téléphone)

| Cap absente | Repli | Parcours touché |
|---|---|---|
| `envelope` | lire `error`/`message` comme aujourd'hui, code déduit par table `Reason.fromLegacy(http, error)` (ex. 409 + « name taken » ⇒ `NAME_TAKEN`) | tous |
| `sync` | pas de carte Cohérence (gris « TV ancienne : cohérence non vérifiable ») ; époques remplacées par `/api/info` + `/api/library` | § 3 |
| `dedup` | pas d'appel `/api/have` ; dédoublonnage nom+taille seulement, l'écran le dit | P-46 |
| `filing2` | pas de comparaison de plan ; note « rangement de la TV ancien » | P-47 |
| `queue` | la file reste côté téléphone (elle y est déjà) ; `NAME_TAKEN` traité comme échec d'un élément | P-39 |
| `cbtn` / `wd` / `wdpersist` | bouton « Wi-Fi Direct » **masqué** (pas grisé) avec la ligne « Cette TV ne propose pas Wi-Fi Direct » ; `BulkRoute` ne demande jamais de groupe | P-49, P-50 |
| `slowed` | détecteur de blocage avec seuil **long** (90 s sans octet) pendant qu'une lecture TV est en cours (`/api/info.player.state`) | P-51 |
| `copybadge` | rien (affichage TV seulement) | P-52 |
| `lots1` / `quizlots` | pas d'envoi de lots ; « Cette TV ne reçoit pas les lots : mettez-la à jour » | P-32, P-37 |
| `transfer2` | chemin `PUT /upload/` classique (existant `Result.Unsupported`) | P-12 |
| `pinhello` | « Ajouter ma TV » garde la fenêtre « Autoriser » (W18 D-W18-4) | P-01 |

## 3. Détection de désynchronisation et auto-réparation

### 3.1 Enveloppe de raison (les deux sens)

`C/sync/Reason.kt` : `enum class ReasonCode(val http: Int, val retryable: Boolean, val fr: String)` et `data class Reason(code, messageFr, retryable, retryAfterMs: Long?, detail: Map<String,String>)`.

| Code | HTTP | Réessayable | Message (modèle) | Où il naît aujourd'hui |
|---|---|---|---|---|
| `AUTH_BAD_PIN` | 401 | non (jusqu'à nouveau code) | Code de la TV refusé. | `denied()` `bad pin` |
| `AUTH_LOCKED` | 401 | oui (`retryAfterMs`) | Trop d'essais : nouvel essai dans N s. | `PinGuard.locked` |
| `AUTH_BAD_TOKEN` | 401 | non (HELLO) | Autorisation de la TV à renouveler. | `denied()` `bad token` |
| `AUTH_PIN_REQUIRED` | 403 | non | Cette action demande le code de la TV. | `TvAuth.tokenMayCall` |
| `TRIAL_CLOSED` | 403 | non | Fonction fermée en version d'essai. | `routeGuard` `{"trial":true}` |
| `CHILD_ACTIVE` | 403 | non | La TV est en mode enfant. | parental |
| `HOST_FORBIDDEN` | 403 | non | Hôte non autorisé. | `HostGuard` |
| `NAME_TAKEN` | 409 | non | Un autre fichier du même nom est déjà sur la TV. | `nameTaken()` |
| `NO_SPACE` | 507 | non | Il manque N sur <volume>. | `spaceRefusal` |
| `VOLUME_REMOVED` | 503 | oui (reprise) | La clé qui contient cet envoi est retirée. | `missingOwner` |
| `BUSY` | 429 | oui (`retryMs`) | La TV est occupée : reprise dans N s. | `transferChunk` 429 |
| `VERIFYING` | 503 | oui (`retryMs`) | La TV vérifie encore le fichier. | `finish` |
| `SESSION_UNKNOWN` | 404 | oui ×1 (`begin`) | La TV a perdu le transfert : reprise. | `unknown transfer` |
| `BODY_TOO_LARGE` | 413 | non | Corps trop grand. | `extBody` |
| `UNSUPPORTED` | 501 | non | Cette TV ne sait pas faire cela. | `Result.Unsupported` |
| `LOT_REJECTED` | 422 | non | Lot refusé : <cause>. | `LotPush` |
| `PROTOCOL_TOO_OLD` | 426 | non | Mettez à jour <l'autre application>. | **nouveau** (jamais émis en W19 : réservé) |
| `GAVE_UP` | — (local) | non | Abandon après N essais : <dernière raison>. | `RetryPolicy` (téléphone) |
| `LINK_LOST` | — (local) | oui | Liaison perdue : <quel côté>. | `LinkMachine` (existant) |
| `INTERNAL` | 500 | non | Erreur de la TV (code n). | tout le reste |

Règles : (1) **tout** `json(error)` de `ReceiverServer` et des extensions passe par `Reason.envelope()` qui ajoute `code`, `retryable`, `retryAfterMs` **sans retirer** `error`/`message` (anciens téléphones) ; (2) l'en-tête `X-CB-Reason: <code>` accompagne la réponse (lisible même si le corps est tronqué) ; (3) côté téléphone, `Reason.parse(http, headers, body)` tente `code`, puis `X-CB-Reason`, puis `fromLegacy(http, error)` ; (4) les messages français vivent dans `Reason` **et nulle part ailleurs** (test de source : aucun texte de refus en dur dans `S/`, `R/`) ; (5) **aucun secret ni adresse complète** dans `detail`.

### 3.2 Le registre des refus (absorbe R-17)

Incident (a) : la TV refuse et **ferme sans lire le corps** ; le téléphone, qui écrit encore un bloc, reçoit « Broken pipe » **avant** d'avoir pu lire la réponse : la raison n'arrive jamais. Deux mesures :

1. **TV : `RejectionLedger`** (`C/sync/RejectionLedger.kt`, mémoire + fichier `rejections.log` tournant de 32 lignes) : chaque refus (route, code, http, session id, pair masqué `ip:…x.y` ou `bt:…ab`, `X-CB-App` du pair, horodatage **monotone** + heure locale informative) y est écrit **et** journalisé à INFO. Exposé par `GET /api/sync/state.rejections` et, pour une session, dans `GET /api/transfer/state?id=`.`lastRejection`. Coût : < 1 µs par refus, 32 × 200 o.
2. **Téléphone : lecture du registre après une coupure côté écriture** : `TransferClient`, sur `IOException` pendant `begin`/`chunk`/`finish`, fait **une** requête `GET /api/transfer/state?id=` (ou `/api/sync/state` si pas d'id) avant de décider : si `lastRejection` est **plus récent** que le début de la tentative et non réessayable ⇒ échec **immédiat** avec ce code ; sinon ⇒ `RetryPolicy` (§ 3.3). Sur une TV sans `sync` : comportement actuel borné par § 3.3.
3. **TV : vidage borné** : avant de fermer une connexion refusée sur `/api/transfer/chunk` ou `/upload/`, lire et jeter le corps restant **si ≤ 1 MiB** (déjà fait pour 429 : `bodyDrained`), sinon fermer. Ainsi la plupart des refus arrivent dans le corps, et les gros par le registre.

```
  téléphone                          TV
  PUT chunk (16 Mio) ───────────►  refuse (409) : écrit registre + INFO, vide ≤ 1 Mio, ferme
  ◄─── « Broken pipe » (écriture)
  GET /api/transfer/state?id ───►  {…,"lastRejection":{"code":"NAME_TAKEN","t":…}}
  élément ⇒ ÉCHEC « Un autre fichier du même nom est déjà sur la TV » · 0 réessai · bouton Réessayer
  carte TV : « Refusé : nom déjà pris — <fichier> (téléphone Galaxy) »  (même code, ≤ 5 s)
```

### 3.3 Réessais bornés (`RetryPolicy`, pur)

| Classe | Exemples | Réessais | Attente | Issue à la borne |
|---|---|---|---|---|
| **Transitoire** | `IOException`, `VOLUME_REMOVED`, `BUSY`, `VERIFYING`, `LINK_LOST` | ≤ 12 **ou** ≤ 10 min cumulées par élément | `retryAfterMs` sinon 2, 4, 8, 15, 30, 60 s ±25 % | `GAVE_UP(dernier code)`, élément en échec visible, file continue |
| **Refus** | `NAME_TAKEN`, `NO_SPACE`, `TRIAL_CLOSED`, `CHILD_ACTIVE`, `UNSUPPORTED`, `LOT_REJECTED` | 0 | — | échec immédiat, raison affichée des deux côtés |
| **Identifiant** | `AUTH_*` | 0 jusqu'à nouveau identifiant (`CredentialGate`, existant) ; `AUTH_LOCKED` : 1 à `retryAfterMs` | — | demande de code (une fois, R-10) |
| **Session** | `SESSION_UNKNOWN` | 1 `begin` de reprise, puis ×6 (existant `attempts > 6`) | immédiat | `GAVE_UP` |

Pendant un « ralenti » (`slowed`), le compteur de **blocage** ne court pas tant que `received` avance (≥ 1 octet / 30 s).

### 3.4 État de synchronisation et carte « Cohérence »

`GET /api/sync/state` (PIN ou jeton ; **ouvert en essai** en lecture, D-W19-4 ; aucun secret) :

```json
{"protocol":8,"appVersion":"0.14.27-beta","versionCode":80,"id":"3f9a1c2e","mono":1234567,
 "epochs":{"library":412,"filing":"a1b2c3d4","lots":"e5f6…","trust":17,"edition":3,"queue":null},
 "libraryRoot":"9c0d…","filing":{"version":3,"on":true,"lang":"fr"},
 "transfers":[{"id":"t8","name":"film.mkv","state":"receiving","received":52428800,"total":734003200,"slowed":true,"code":null}],
 "rejections":[{"mono":1230000,"route":"/api/transfer/chunk","code":"NAME_TAKEN","http":409,"session":"t7","peer":"ip:…0.23","app":"castbridge/1.2.39 (69)"}],
 "edition":{"kind":"prod","until":null},"peers":[{"app":"castbridge/1.2.39 (69)","proto":7,"seenMonoAgo":4000}]}
```

Le téléphone tient `PhoneSyncState` (époques vues, file, lots, capacités) et `Coherence.compare(tv, phone, now)` rend :

| Niveau | Condition | Texte (français, un par cause) | Réparation automatique |
|---|---|---|---|
| **VERT** ● | toutes les époques vues = époques TV ; aucune session orpheline ; aucun refus non affiché | « TV et téléphone concordent » | — |
| **ORANGE** ▲ | époque `library` ou `libraryRoot` différente ; `filing` différent ; `lots` différent ; TV sans `sync` | « La bibliothèque de la TV a changé depuis la dernière lecture : actualisation… » / « Le rangement de la TV suit une autre règle (TV 0.14.24) » / « 2 lots à envoyer, 1 à retirer » / « TV ancienne : cohérence non vérifiable » | relire index / recalculer plan de lots / rien |
| **ROUGE** ■ | session TV sans élément de file depuis 2 cycles ; élément « en cours » sans session depuis 2 cycles ; `trust` changé avec jeton encore utilisé ; refus répété (≥ 3 même code en 5 min) non montré | « La TV reçoit « film.mkv » que ce téléphone n'envoie pas : un autre téléphone ? » / « Envoi perdu par la TV : relance… » / « La TV ne vous reconnaît plus : réassociez » / « La TV refuse « x » (nom déjà pris) : retirez-le de la file » | `abort` orphelin ; `begin` de reprise ; HELLO ; marquer l'élément en échec |
| **NOIR** ○ | aucune TV jointe | « Cohérence : TV non jointe » | — |

**Ordre de vérité** quand ils se contredisent : (1) le registre de confiance de la TV (`trust`) ; (2) l'état du disque de la TV (index, sessions, lots installés) ; (3) la file du téléphone ; (4) les caches du téléphone ; (5) l'affichage. Une réparation va toujours **du bas vers le haut** (on corrige l'affichage et les caches, puis la file, jamais le disque de la TV depuis un cache du téléphone).

Rythme : au premier plan, `sync/state` toutes les **15 s** sur LAN/WD (fusionné avec le garde-vivant existant `GET /api/info` : même minuterie, une requête de plus **seulement** si l'époque de `/api/info` change ; sinon toutes les 60 s), toutes les **60 s** sur Bluetooth ; en fond, à l'étape du job 15 min. Taille : ≈ 1-2 Ko.

### 3.5 Le protocole de reprise unique (S-REPRISE)

Un seul protocole pour toutes les voies (LAN, Wi-Fi Direct, Bluetooth-tunnel) et toutes les lignes de § 1.4. Il **prolonge** l'existant (`/api/transfer/{caps,begin,chunk,state,finish,abort}`, `PartAssembler` avec sidecar, `Scheduler`, `ResumableUpload`, `TransferQueue` persistée) ; il ne le remplace pas.

#### 3.5.1 Identité de transfert stable

`TransferIdentity = (sessionId, manifestRoot, queueEpoch, tvId)` :
- `manifestRoot` = racine SHA-256 du manifeste (taille, `slice`, empreintes de blocs connues ; existant `Manifest.root`) : **c'est le nom durable** du transfert ; `sessionId` (`id` de `begin`) est **éphémère** (perdu au redémarrage de la TV : 404, existant) ; la TV retrouve le sidecar **par nom et par taille** (existant) et, après W19, **par `manifestRoot`** écrit dans le sidecar (évite de reprendre le partiel d'un autre fichier homonyme de même taille).
- `queueEpoch` : l'époque de la file du téléphone à la création de l'élément ; un `state()` qui revient avec une session dont l'identité ne correspond à aucun élément ⇒ orpheline ⇒ `abort` (Cohérence ROUGE, § 3.4).
- Persistée côté **téléphone** dans l'élément de file (R-09 : fichier JSON privé ; champ additif `identity`), côté **TV** dans le sidecar `.cbx.meta` (champ additif `root`). Les anciennes versions ignorent le champ (S-7).

#### 3.5.2 Qui est autorité sur quoi, et la réconciliation à chaque reconnexion

```
  reconnexion (toute cause : RS-01…RS-30)
  téléphone                                            TV
  1. hello (si cache > 24 h ou voie changée) ────────► caps, protocol  ── renégociation capacités
  2. caps() de la voie ─────────────────────────────► maxStreams, slice ── renégociation voies/bloc
  3. state(id, hashes=1)  ── ou 404 ⇒ begin(manifest) ─► carte de blocs ÉCRITS ET VÉRIFIÉS (autorité TV)
  4. garde de source (taille, mtime, bords) ─ autorité téléphone ─ si changé ⇒ abort(SOURCE_CHANGED)
  5. renvoi des SEULS blocs absents de la carte ; un bloc déjà présent ⇒ `Already` (idempotent)
  6. finish(root) ⇒ Done | Missing(carte) | Corrupt(carte) | Verifying(retryMs) ⇒ boucle bornée
```

- **La TV est autorité** sur ce qu'elle a **durablement écrit et vérifié** (bloc haché à l'écriture, map + hashes dans le sidecar, fsync W15). Le téléphone **n'envoie jamais** avant d'avoir lu cette carte (règle « interroger avant de renvoyer » : déjà vraie dans `TransferClient.run` via `api.state(b.id, withHashes = true)` ; W19 la rend **obligatoire** après toute coupure en cours de `Scheduler`, pas seulement au début d'une tentative).
- **Le téléphone est autorité** sur la **source** : à chaque reprise, `SourceGuard.check(uri)` compare taille + `lastModified` + empreinte de 2 bords de 64 Kio (ou du fichier entier ≤ 64 Mio, comme `MoveProof`) à ce qui a été relevé au premier `begin` ; différence ⇒ `abort` + `Abandonné(SOURCE_CHANGED)` ; la TV jette le partiel. **Trou aujourd'hui** (aucun `lastModified` lu dans `S/UploadService.kt`/`S/TransferQueue.kt`) ⇒ w19-13.
- **Bitmap des deux côtés** : la TV l'a (sidecar) ; le téléphone garde, dans l'élément de file, la **dernière carte lue** et son `HashBook` (empreintes déjà calculées) pour ne pas re-hacher 4 Go à chaque reprise ; cette copie n'est **jamais** une autorité : elle est remplacée par `state()` à chaque reconnexion.

#### 3.5.3 Idempotence et unicité des octets

Invariant **I-1** : sur le disque final de la TV, chaque bloc est écrit **une fois** et vérifié par son empreinte ; un bloc reçu deux fois (voies parallèles, `duplicateCandidate` du `Scheduler`, rejeu après coupure) ⇒ `Already`, ignoré. Invariant **I-2** : `finish` ne renomme en nom final que si `map.complete()` et la racine concorde ; un second `finish` = 503 `verifying` (existant). Invariant **I-3** : un `begin` d'un fichier déjà complet ⇒ `done:true`, 0 octet. Invariant **I-4** : un `abort` est idempotent (404 après coup = succès).

#### 3.5.4 Renégociation

| Quoi | Quand | Comment | Si l'autre côté ne sait pas |
|---|---|---|---|
| Capacités, protocole | voie changée, cache > 24 h, 404/501 inattendu, TV mise à jour (RS-18) | `hello` ⇒ `TvCaps` ; `FeatureGate` | table `impliedBy` (§ 2.2) |
| Route | perte confirmée (sonde ×2), `rerouteAfterLoss` (R-14), LAN revenu | `WdPolicy`/`BulkRoute` (W18) ; **même session** : la TV ne connaît pas la voie | BT seul : `/api/transfer` par le tunnel mux ou CBT1 (offset) |
| Identifiants | 401 `AUTH_BAD_TOKEN` ⇒ HELLO ; `AUTH_BAD_PIN` ⇒ une demande ; `installId` différent ⇒ ré-appairage | `CredentialGate` (jamais rejouer un refus), `PinBook` par `tvId` | — |
| Taille de bloc, voies | à chaque `begin` (`caps()` lu d'abord) ; **`slice` figé par session** : si la TV annonce un autre `slice` pour une session existante ⇒ la TV répond la carte dans sa granularité, le téléphone recommence les blocs non alignés et **le dit** (RS-30) | `Caps.slice`, `maxStreams` | ancien téléphone : `slice` constant (existant) |
| Vitesse | 429 `BUSY(retryMs)`, `receiveCap`, `slowed` | `PlaybackPriority` (R-15/R-16) ; le téléphone n'y voit **pas** un blocage | — |

#### 3.5.5 Temporisations et états finaux

- Attente de reconnexion : courbe `LinkMachine` (1,5 → 60 s, ±25 %) ; **abandon** d'une tentative après 12 essais ou 10 min cumulées (S-2) ⇒ l'élément passe **« En pause : <raison> · reprise automatique au prochain contact »** (pas « Abandonné » : la TV éteinte n'est pas une faute), reprise déclenchée par un évènement (ouverture, écran, réseau, ACL, job 15 min) ; **jamais** une attente sans texte ni durée.
- Les **quatre états finaux** d'un élément de file, et aucun autre : `TERMINÉ_VÉRIFIÉ` (racine concordante, fichier renommé, `verifiedWhole`), `REPRIS` (en cours après une coupure : « Reprise de « x » (38 %) »), `ABANDONNÉ(code)` (raison `Reason`, « Réessayer »), `EN_ATTENTE_TV(depuis, borne)` (« En attente de la TV · 0:42 », puis « En pause » à la borne). Un partiel n'est **jamais** « Terminé » : test de source sur `XferTexts` (aucune phrase « Terminé » hors `TERMINÉ_VÉRIFIÉ`).
- Même machine côté TV (`TransferProgress.Item`) : `receiving` / `paused(reason, since)` / `verifying` / `done` / `refused(code)` / `aborted(code)` ; la carte TV et la notification du téléphone disent **le même mot** pour le même état (table `SignalAgreement`, w19-06).

#### 3.5.6 Ce qui existe déjà et les trous (classement)

| Brique | Existe (lu) | Trou que W19 comble |
|---|---|---|
| Reprise par blocs, carte TV, hachage à l'écriture, sidecar survivant au redémarrage | `PartAssembler` (:41, :73, :94-121), `TransferHost.stateJson` (:136), `begin` par nom (`ReceiverServer.kt:939`) | sidecar sans `manifestRoot` (homonyme de même taille) ⇒ w19-13 |
| Boucle de reprise du téléphone | `TransferClient.run` (`SessionLost` ×6, `Verifying.retryMs`, `state(withHashes)`) | `IOException` sur `begin`/`finish` **sans borne** (R-17) ⇒ **w19-01** ; carte non relue après coupure en cours de `Scheduler` ⇒ w19-13 |
| Voies parallèles, doublons tolérés | `Scheduler` (`duplicates`, `Already`, `Busy.retryMs`) | renégociation de `slice` par session ⇒ w19-13 |
| File persistée, réservation atomique, réessai, causes | R-09 (`TransferQueue`, `CopyQueueTest/AuditTest/ReviewTest`) | identité `TransferIdentity` additive ; états finaux normalisés (§ 3.5.5) ⇒ w19-13 ; codes `Reason` ⇒ w19-01 |
| Identifiants jamais rejoués, jeton renouvelé en cours | `CredentialGate`, `ResumableUpload` (BT-PLUG-AND-PLAY « Opérations en cours ») | — |
| Changement de voie | `BulkRoute.rerouteAfterLoss` (R-14), W18 `WdPolicy` | « sans octet renvoyé déjà confirmé » : à prouver (`ChaosResumeTest.routeFlip`) |
| « Déplacer » sans perte | `MoveProof` (R-12) | — |
| Doublons après reprise | `/api/have`, `DedupDecision` (R-12) | — |
| Source modifiée pendant la copie | **rien** | `SourceGuard` ⇒ w19-13 |
| Refus lisible, journalisé, non réessayé | partiel (`NAME_TAKEN` JSON existe ; aucun journal TV ; Broken pipe illisible) | **w19-01** : enveloppe, registre, vidage borné, `RetryPolicy` |
| Quota d'essai atteint **pendant** une copie | `routeGuard` par requête (403) | comportement du partiel à fixer (D-W19-11) |

**Journalisation TV** (contrat) : chaque refus ⇒ une ligne INFO `CB-REFUS route=<r> code=<c> http=<n> session=<id|-> peer=<masqué> app=<X-CB-App|-> reason=<message court>` ; jamais de PIN, jeton, mot de passe, adresse complète ; test de source sur `Redact.scrub`. Lisible par `adb logcat -s CastBridge` et dans l'écran « Diagnostic » existant (« Copier le rapport »).

## 4. Tests de contrat : la vraie garantie

### 4.1 Matrice de compatibilité JVM (sans appareil)

```
                         TV réelle HEAD      TV persona 0.14.26   0.14.25   0.14.24   0.14.22
  téléphone réel HEAD     [A: harnais W14]    [B: HEAD→transcript]  [B]       [B]       [B·enc]
  persona 1.2.39          [C: transcript→HEAD] [D: transcript↔transcript : vérifié à l'enregistrement]
  persona 1.2.38          [C]                  [D]
  A = vrai client contre vrai serveur (JourneyKit/TvSim, existant) + FaultProxy
  B = vrai client HEAD contre TranscriptTv (réponses enregistrées de la TV à cette étiquette)
  C = TranscriptPhone (requêtes enregistrées du téléphone à cette étiquette) contre vraie ReceiverServer HEAD
  D = couple déjà livré : sa cohérence est un fait enregistré, pas un test (sert de référence)
```

**Persona** = dossier `android/core/src/test/resources/compat/<étiquette>/` : `manifest.json` (étiquette, sha du commit, date, protocole observé, `caps` observées/impliquées, sha256 de chaque échange), puis un fichier texte par **échange canonique** `NN-<nom>.http` (requête brute + réponse brute, en-têtes et corps, secrets remplacés par `<PIN>`/`<TOKEN>`). Les 14 échanges canoniques : `hello`, `info`, `bad-pin`, `locked`, `transfer-caps`, `begin-ok`, `begin-done`, `begin-name-taken`, `begin-no-space`, `chunk-busy-429`, `finish-verifying`, `finish-ok`, `have` (si route), `lots-manifest` (si route). Une route absente à l'étiquette est enregistrée comme `404` : c'est une donnée.

**Comment geler une persona, pas cher** (`tools/compat/record_persona.py --tag tv-0.14.25-beta`) :
1. `git worktree add .compat/<tag> <tag>` (lecture seule, jamais `main`).
2. Copie dans ce worktree, sous `core/src/test/kotlin/castbridge/compat/`, le **shim** `PersonaMain.kt` (de HEAD, `tools/compat/shim/`) et l'**adaptateur** de l'étiquette `tools/compat/adapters/<tag>.kt` (≤ 40 lignes : comment construire `ReceiverServer` et `TransferClient` **à cette étiquette** : les constructeurs changent).
3. `gradle --offline :core:testClasses` dans le worktree, puis `java -cp … castbridge.compat.PersonaMain --port 0 --out <dossier>` : le shim lance la TV de l'étiquette sur loopback et joue les 14 échanges avec le client **de la même étiquette** ; un **tee TCP** du script (Python, bibliothèque standard) enregistre les octets bruts des deux sens.
4. Masquage, `manifest.json`, `sha256` ; le script **refuse** d'écrire si un secret ressemblant à un PIN/jeton reste (regex, comme `Redact`).
5. Coût : ≈ 5-10 min de machine par étiquette, **une fois** ; aucun appareil. Si le worktree ne compile pas hors ligne (dépendance absente du cache Gradle), le script le dit et propose `--handwritten` : l'exécutant écrit les échanges **à la main** depuis `git show <tag>:…ReceiverServer.kt` (marqués `"origin":"handwritten"`, confiance moindre, listés dans le rapport).
6. Après chaque **livraison**, la persona de la version livrée est enregistrée à l'étiquette (porte § 4.4) : la matrice grandit d'une colonne ou d'une ligne par livraison ; les personas hors fenêtre (P-4 et moins) passent en `enc` (meilleur effort) puis sont **archivées** (déplacées dans `compat/archive/`, non jouées).

**Rejoueurs** (`CT/compat/`) : `TranscriptTv` = `NanoHTTPD` port 0 qui, pour une requête entrante, cherche l'échange par `(méthode, chemin, paramètres clés)` et répond **octet pour octet** (en-têtes, statut, corps, et **le comportement de fermeture** : « ferme sans lire le corps » est enregistré comme drapeau `closeWithoutDrain` dans le manifeste) ; requête inconnue ⇒ `404` + échec du test « le client HEAD appelle une route que cette TV n'a pas » (S-10). `TranscriptPhone` = joue les requêtes enregistrées contre la vraie `ReceiverServer` HEAD et vérifie, par échange, des **assertions de compatibilité** déclarées (`expect.json` : classe de statut, champs que l'ancien analyseur lit : `error` ou `message`, `length`, `done`, `retryMs`, `id`) : « l'ancien téléphone comprend encore la nouvelle TV ».

### 4.2 Injection de pannes (`FaultProxy`, `CT/compat/`)

Mandataire TCP loopback en test entre le client et la TV (vraie ou persona), piloté par scénario : `closeEarly(afterBytes)`, `slow(bytesPerSec)`, `inject(429|503|500, body)`, `rstMidBody(atFraction)`, `restartServer(atFraction)` (via `TvSim.restart()`), `clockSkew(±ms)` (horloges injectées `JourneyClock`). Chaque scénario affirme une garantie : S-1 (raison présente des deux côtés ≤ 5 s simulées), S-2 (nombre de tentatives ≤ 12, durée ≤ 10 min simulées, `GAVE_UP` visible), S-4 (rejeu ×3 sans effet), S-5 (après `restart`, même état ≤ 60 s), S-8 (±24 h sans changement d'issue).

**Chaos de reprise reproductible (`ChaosResumeTest`, S-REPRISE)** : une file de **5 fichiers** (1 Mio, 8 Mio, 64 Mio, un homonyme de taille différente, un « Déplacer ») envoyée par le vrai `TransferClient` à la vraie `ReceiverServer` à travers `FaultProxy` et `RadioSim` ; un **générateur à graine** (`seed` dans le nom du test et dans le rapport : `CHAOS seed=…`) tire **N = 40 évènements** à des instants aléatoires : coupure en plein bloc, RST, redémarrage TV, « kill » téléphone (nouvelle instance de `PhoneSim` sur la même file persistée), changement de voie LAN→WD→BT→LAN (bases différentes vers le **même** `ReceiverServer`), 404 de session, 429, 503 volume retiré/remis, 507 à mi-chemin, rotation de jeton, horloge ±24 h, source modifiée sur le 3ᵉ fichier. **Invariant vérifié à la fin** (horloge simulée ≤ 2 h) : pour chaque fichier, **soit** le fichier final existe, taille exacte, SHA-256 égal à la source, compteur de blocs écrits sur disque = nombre de blocs (**chaque octet exactement une fois**), état `TERMINÉ_VÉRIFIÉ` sur le téléphone et `done` sur la TV ; **soit** `ABANDONNÉ(code)` sur le téléphone **et** le même `code` dans le registre de la TV, aucun fichier final, partiel supprimé ou gardé selon le code (table). Jamais d'autre issue ; jamais plus de 12 tentatives consécutives sans évènement ; aucune phrase « Terminé » avant `finish`. Trois graines fixes dans la porte (`1, 2026, 424242`) + une graine aléatoire journalisée (reproductible par `-Dchaos.seed=`). Durée cible : < 90 s par graine.

**Ce que ce chaos ne prouve pas** : les radios réelles (association P2P, Doze Samsung, pile Bluetooth « already opened »), la clé exFAT réelle, `startForeground` sous GaiaOS : relevé **H-REPRISE** (§ 1.4, 15 min).

### 4.3 Parcours de symbiose J-SYM (harnais W14, `CT/journey/SymbiosisJourneyTest.kt`)

| id | Parcours | Attendu téléphone | Attendu TV |
|---|---|---|---|
| J-SYM-1 | Écart de versions : téléphone HEAD, TV persona 0.14.24 (sans `dedup`, `sync`) | copie P-11 passe ; encouragement « ne sait pas encore éviter les doublons » une fois ; aucune route non déclarée appelée | (transcript) aucune requête inconnue |
| J-SYM-2 | Copie refusée avec raison des deux côtés : TV refuse `NAME_TAKEN` en fermant sans vider | élément en échec `NAME_TAKEN` ≤ 5 s, 0 réessai, « Réessayer » | registre : 1 refus, INFO 1 ligne, carte « Refusé : nom déjà pris » |
| J-SYM-3 | Redémarrage TV à 30 % (existant P-16) **et** redémarrage téléphone à 30 % (file relue R-09) | reprise au même bloc ≤ 60 s ; % ne recule pas | session reconnue ou `begin` de reprise ; même % |
| J-SYM-4 | PIN tourné sur la TV pendant une file de 3 | **une** demande de code ; file en pause nommée ; reprise après saisie | 1 seul `AUTH_BAD_PIN` dans le registre |
| J-SYM-5 | Divergence de bibliothèque : le téléphone croit « présent », la TV a perdu le volume | Cohérence orange « bibliothèque changée » puis relecture ; copie décidée sur l'état **TV** | `libraryRoot` changé, époque +1 |
| J-SYM-6 | Lots : téléphone v3, TV v2 ; un lot d'une fonction inconnue de la TV | plan « 1 à envoyer » ; lot inconnu ⇒ `LOT_REJECTED` affiché avec cause | manifeste à jour ; refus journalisé |
| J-SYM-7 | « Copie ralentie » 2 min (`slowedNote`) | état « Copie ralentie pour ne pas gêner la lecture », **aucun** « bloqué » / délai | `slowed:true` dans `sync/state` |
| J-SYM-8 | Incident (a) rejoué : TV ferme à chaque `begin` 40 fois | abandon à ≤ 12 tentatives / ≤ 10 min, `GAVE_UP(LINK_LOST)` visible, file continue | ≤ 12 lignes INFO, pas 72 |
| J-SYM-9 | Deux TV, deux téléphones : refus de B n'apparaît pas sur la fiche de A | faits cloisonnés par `tvId` | registre par pair |
| J-SYM-10 | Changement de voie LAN→WD→BT→LAN pendant un envoi de 64 Mio (RS-04) | % monotone ; blocs renvoyés = 0 déjà confirmés (`duplicates` = voies parallèles seulement) ; « Par Bluetooth (lent) » puis « Par le Wi-Fi » | une session ; carte complète ; fichier identique |
| J-SYM-11 | Source modifiée à 40 % (RS-29) | `ABANDONNÉ(SOURCE_CHANGED)` « Le fichier a changé pendant la copie » | partiel supprimé ; registre `SOURCE_CHANGED` |
| J-SYM-12 | Clé retirée à 30 %, remise à 2 min ; puis autre volume (RS-12) | « En attente : la clé est retirée » puis reprise ; autre volume ⇒ question, jamais de choix muet | carte « Clé retirée » ; même sidecar |
| J-SYM-13 | TV mise à jour pendant un envoi (persona N ⇒ HEAD sur le même dossier, RS-18) | « TV mise à jour : reprise » ; caps rafraîchies ; même % | sidecar relu par HEAD |
| J-SYM-14 | TV éteinte 2 jours pendant une file (RS-09/RS-26, horloge) | « En pause : la TV est éteinte » après 10 min ; reprise au contact ; aucun 0 % sans texte | sidecar < 7 j conservé |

Fumée `tools/smoke/` : **n'existe pas** (w14-07/08/10 non exécutés). Les J-SYM vivent donc sur JVM ; un cahier **conditionnel** (w19-12) les ajoute à la fumée le jour où elle existe.

### 4.4 Porte de livraison `tools/release/check_compat.py`

Lecture seule, bibliothèque standard, codes 0/1/2 comme `check_versions.py`. Refuse l'étiquette (`tag-plan.sh --apply` l'appelle et s'arrête sur 1) quand :

1. la matrice est rouge : `gradle --offline :core:test --tests 'castbridge.core.compat.*' --tests 'castbridge.core.journey.Symbiosis*'` (ou lecture des `TEST-*.xml` fournis par `--results`) ;
2. **un changement de protocole sans note** : `git diff <dernier tag de l'app>..HEAD -- C/sync/Caps.kt C/sync/Reason.kt C/tv/ReceiverServer.kt(/api/hello, `VERSION`) C/tv/BtProtocol.kt C/trust/HelloHandler.kt` non vide **et** `docs/PROTOCOL-CHANGES.md` sans entrée pour la version en cours ; l'entrée doit cocher « additif : oui » ou donner le nouveau `protocol` et la migration ;
3. la persona de la version **précédente** de la même app n'est pas enregistrée (`compat/<tag précédent>/manifest.json` absent) : la matrice aurait un trou ;
4. `check_versions.py --strict` rouge.

`--allow-red "raison"` existe pour le propriétaire seul : la raison est **écrite** dans `PROTOCOL-CHANGES.md` par le script (append), jamais silencieuse. Après l'étiquette, le même script `--record` appelle `record_persona.py` pour la version qui vient d'être livrée (dans la fenêtre : 5-10 min de machine).

## 5. Règle de symbiose pour tout exécutant (à ajouter au gabarit)

> **Règle de symbiose (W19).** Tout changement qui touche un message téléphone↔TV (route HTTP, champ JSON, trame Bluetooth, en-tête, code de refus, format sur disque partagé) doit : **(1)** être **additif** (aucune clé retirée ni renommée, aucun sens changé ; un nouveau champ obligatoire = nouveau `protocol` + note dans `docs/PROTOCOL-CHANGES.md`) ; **(2)** **nommer la capacité** dans `C/sync/Caps.kt` (constante, « depuis », repli) et la **gérer absente** côté téléphone ; **(3)** passer tout refus par `Reason` (code, français, réessayable) et vérifier qu'il est **affiché sur les deux applications** (test de parcours ou capture) ; **(4)** mettre à jour les **tests de contrat** (`CT/compat/`, `expect.json`, et la persona courante si l'échange canonique change) ; **(5)** écrire dans le rapport la ligne `SYMBIOSE: cap=<nom> · proto=<inchangé|n> · reason=<codes> · deux écrans=<test>`. Un cahier qui ne peut pas cocher les cinq points s'arrête avec `QUESTION:`.

## 6. Ce qu'il faut construire, dimensionné et ordonné

| Ordre | Cahier | Livre (valeur) | Gel | Modèle · effort | Dépend |
|---|---|---|---|---|---|
| 1 | **w19-01** enveloppe de raison, registre des refus, réessais bornés | S-1, S-2 ; **première brique de S-REPRISE** (RS-22, borne de RS-09/26) ; absorbe **R-17** ; journalisation TV | cœur (zone `ReceiverServer` : R2, un seul cahier) | sonnet L + **audit Opus** | — |
| 1′ | **w19-13** protocole de reprise unique : `TransferIdentity`, `SourceGuard`, relecture de carte après coupure, `slice` par session, états finaux | **S-REPRISE** (RS-04, 07, 18, 29, 30) | cœur (`C/xfer/`, zone `TransferClient`/`PartAssembler` ; `ReceiverServer` **après** w19-01) | sonnet L + **audit Opus** (perte de données) | w19-01 |
| 2 | **w19-02** `/api/hello` additif, `X-CB-App`, cache `cap.<tvId>`, `UpdateNudge`, `FeatureGate` | S-6, S-10 ; (d), (e) | cœur | sonnet M (échantillon) | w19-01 fusionné (même fichier) |
| 3 | **w19-03** enregistreur de personas + rejoueurs + matrice | S-6 prouvée ; 6 personas | outils + tests | sonnet L | w19-02 (pour `Caps.impliedBy`) |
| 4 | **w19-04** `FaultProxy`, `RadioSim` de voie, assertions S-1/2/4/5/8 et **`ChaosResumeTest` à graine** (S-REPRISE) | la garantie sous panne, l'invariant « chaque octet une fois » | tests | sonnet L | w19-01, w19-13 |
| 5 | **w19-05** porte `check_compat.py` + `PROTOCOL-CHANGES.md` + crochet `tag-plan.sh` | plus jamais de livraison incompatible | outils | sonnet S | w19-03 |
| 6 | **w19-06** `SyncState`, époques, `Coherence`, route `/api/sync/state` | S-3, S-11 (carte) | cœur (route additive par extension) | sonnet L (échantillon) | w19-01 |
| 7 | **w19-07** `FileIdentity`, `FilingPlan.VERSION`+hash, `LotAgreement`, fixtures de formats | S-7, S-9, S-11 ; (c), (g) | cœur | sonnet M | — |
| 8 | **w19-08** J-SYM-1…9 dans le harnais | preuve bout en bout | tests | sonnet M | 01, 02, 03, 06, 07 |
| 9 | **w19-11** docs : `SYMBIOSE.md`, R-17 dans REGRESSIONS, PARCOURS P-53/P-54, HANDOFF, gabarit | mémoire du projet | docs | haiku S | 01…08 |
| 10 | **w19-09** câblage téléphone (`S/`) : codes dans la file/notification, encouragements, carte Cohérence, bouton WD par cap | ce que l'usager voit | **après le gel** | sonnet M | 01, 02, 06 |
| 11 | **w19-10** câblage TV (`R/`) : INFO des refus, page « Téléphone » (version + encouragement), carte « Refusé », époques depuis `TvService` | ce que l'usager voit | **après le gel** | sonnet M + **audit Opus** | 01, 02, 06 |
| 12 | **w19-12** (conditionnel) J-SYM dans la fumée `tools/smoke/` | — | quand la fumée existe | haiku S | w14-07/10 |

Effort ≈ **23 agent·jours**, ≈ **20 $** (détail dans l'index). Pendant le gel : 1-9 et 1′ (≈ 19 j, ≈ 17 $). Parallélisme : au plus 3 ; **jamais** deux cahiers sur `ReceiverServer.kt` (w19-01, puis w19-13, puis w19-02 ; w19-06 passe par une classe d'extension, une ligne d'enregistrement). Ordre de valeur : w19-01 (R-17 visible, borné) → w19-13 (reprise prouvée) → w19-04 (chaos) → w19-02/03/05 (versions) → w19-06/07/08 → docs → câblage.

## 7. Décisions à prendre par le propriétaire

| id | Question | Recommandation | Si non |
|---|---|---|---|
| D-W19-1 | Ajouter `protocol:8` (entier) à côté de `v:"0.7"` gardé tel quel ? | **Oui** : `v` ne bouge plus jamais, `protocol` compte les formes | on continue à ne pas pouvoir distinguer les TV ; S-6 non testable |
| D-W19-2 | Fenêtre prise en charge : P-3…P+1 (≈ 4 livraisons de protocole) ? | **Oui** ; au-delà encouragement, parcours de base toujours tentés | fenêtre plus large = matrice plus lourde (chaque persona ≈ 1 min de test) |
| D-W19-3 | Borne des réessais : 12 tentatives ou 10 min par élément, puis échec visible avec « Réessayer » ? | **Oui** (R-17 : 72 essais silencieux en 30 min) | une borne plus longue retarde la vérité ; sans borne = R-17 |
| D-W19-4 | `GET /api/sync/state` lisible en édition d'essai (aucun secret, lecture seule) ? | **Oui** : la cohérence sert aussi à l'essai | la carte Cohérence est grise en essai |
| D-W19-5 | Le téléphone annonce sa version à la TV (`X-CB-App`, réseau local seulement) ? | **Oui** : la TV journalise qui elle refuse et peut encourager la mise à jour du téléphone | la TV reste aveugle sur les téléphones |
| D-W19-6 | Enregistrer maintenant les personas des étiquettes déjà livrées (`tv-0.14.24/25/26`, `phone-1.2.38/39` ; `tv-0.14.22` meilleur effort) ? | **Oui** : ≈ 1 h de machine, une fois ; sinon la matrice ne protège que l'avenir | la matrice démarre vide : première colonne à la prochaine livraison |
| D-W19-7 | La porte refuse l'étiquette sur matrice rouge, avec `--allow-red "raison"` écrit dans `PROTOCOL-CHANGES.md` ? | **Oui** | la porte ne fait qu'avertir : R-17 bis possible |
| D-W19-8 | Vidage borné du corps refusé : ≤ 1 MiB puis fermeture (le reste par le registre) ? | **Oui** (≈ 1 s de Wi-Fi au pire par refus) | tout par le registre : une requête de plus à chaque refus |
| D-W19-9 | Emplacement de la carte « Cohérence » : fiche de la TV sur le téléphone + ligne sur la page « Téléphone » de la TV ? | **Oui**, repliée en vert, dépliée en orange/rouge | onglet dédié : une touche de plus |
| D-W19-10 | Encouragement « Mettre à jour » : au plus une fois par jour et par TV, jamais pendant une copie ? | **Oui** | plus fréquent = bruit ; moins = oubli |
| D-W19-11 | Quota d'essai atteint **pendant** une copie (RS-23) : la TV garde le partiel 24 h (reprise après activation) ou le supprime aussitôt ? | **Garder 24 h** : l'activation suit souvent de près ; le partiel n'est pas lisible | suppression : copie recommencée après activation |
| D-W19-12 | Après la borne de 10 min sans TV, l'élément passe « En pause · reprise automatique au prochain contact » (et non « Abandonné ») ? | **Oui** : une TV éteinte n'est pas une faute ; l'abandon reste réservé aux refus et à `GAVE_UP` sur erreurs répétées | tout est « Abandonné » : l'usager relance à la main |
| D-W19-13 | Source modifiée pendant la copie (RS-29) : abandon + partiel jeté (recommandé) ou reprise sur le nouveau contenu ? | **Abandon dit** : un fichier mi-ancien mi-nouveau est pire qu'une relance | reprise « intelligente » = complexité et risque de corruption |
| D-W19-14 | Durée de conservation des partiels sur la TV : 7 jours (valeur lue `maxAgeMs`) confirmée ? | **Oui**, et la purge est **dite** au téléphone (0 % avec texte) | plus long = clé pleine de partiels |

**BLOQUÉ (faits, pas opinions)** : aucun. Deux faits à connaître : la cause TV de l'incident (a) reste **inconnue** (rien n'était journalisé) : W19 la rend observable, elle ne la devine pas ; la branche `claude/fix-broken-pipe` est **vide** (aucun commit propre) : w19-01 l'absorbe.

## 8. Risques

| # | Risque | Prob. | Impact | Parade |
|---|---|---|---|---|
| 1 | Une étiquette ancienne ne compile pas hors ligne (cache Gradle) ⇒ persona impossible | moyenne | matrice incomplète pour 0.14.22/0.14.24 | `--handwritten` marqué ; priorité aux 3 dernières TV et 2 téléphones (D-W19-6) |
| 2 | Un adaptateur de persona mal écrit enregistre un comportement qui n'est pas celui du terrain (constructeur de test ≠ `TvService` réel) | moyenne | fausse confiance | le manifeste note les options de construction ; les échanges `hello`/`info` sont recoupés avec `TVro` (`GET /api/hello` sur la vraie TV, lecture seule) |
| 3 | `ReceiverServer.kt` est un fichier chaud (R2) : w19-01 y touche en profondeur (tous les `json(error)`) | haute | conflits avec branches non fusionnées (R-12/13/14/15/16 déjà fusionnées ? `fix-broken-pipe` vide) | w19-01 passe par un **unique** point `Reason.envelope()` appelé dans `json()` : diff étroit ; audit Opus |
| 4 | Vidage borné : un refus sur un bloc de 16 MiB coûte jusqu'à 1 MiB de lecture inutile par refus | faible | ≈ 1 s | borne D-W19-8 ; le registre couvre le reste |
| 5 | `sync/state` toutes les 15 s = charge sur une TV 4×A53 pendant une lecture | faible | saccades | fusionné avec le garde-vivant existant ; corps ≈ 1-2 Ko ; jamais de calcul d'empreinte à la demande (`libraryRoot` tenu incrémentalement par `ContentIndex`) |
| 6 | Un encouragement « Mettre à jour » mal compris : l'usager croit à une panne | moyenne | confusion | texte avec la fonction qui manque, « marche quand même », une fois par jour (D-W19-10) |
| 7 | `X-CB-App` révèle la version du téléphone sur le réseau local | faible | vie privée minime | réseau local seulement, pas d'identifiant d'appareil ; D-W19-5 |
| 8 | La fenêtre P-3…P+1 est oubliée : un jour une TV du parc est à P-5 | moyenne | encouragement permanent | la porte liste les personas hors fenêtre ; le propriétaire décide d'archiver |
| 9 | Horloge monotone remise à zéro au redémarrage ⇒ `mono` des refus incomparables entre deux vies de la TV | certaine | « plus récent que » faux après reboot | `mono` + `bootId` (aléatoire par processus) ; le téléphone compare dans un même `bootId` seulement |
| 10 | Les deux audits Opus arrivent tard (gel) | moyenne | w19-01 bloque w19-02/06 | w19-01 en premier, seul sur `ReceiverServer` ; w19-07 et w19-03 en parallèle |

## 9. Ce qui n'a pas pu être vérifié

- Aucun appareil touché : les délais (5 s / 30 s / 60 s) sont des **objectifs** de contrat, à mesurer sur le S21+ et la TV GaiaOS (P-53, P-54 à écrire par w19-11).
- `tools/smoke/smoke.py --tv fake` et `FakeTvMain` : **absents** du dépôt ; les cahiers w14-07/08/10 n'ont pas de rapport.
- `claude/fix-broken-pipe` : aucun commit propre ; R-17 absent de `REGRESSIONS.md` ; la cause TV de l'incident (a) est inconnue.
- Le comportement exact de NanoHTTPD quand la réponse est écrite alors que le client écrit encore (RST vs FIN) : à observer dans `FaultProxyTest` ; l'enregistrement de `closeWithoutDrain` dans les personas en dépend.
- Les capacités **impliquées** par version (`Caps.impliedBy`) : à établir par lecture de chaque étiquette, pas de mémoire.
- La compilation hors ligne des étiquettes anciennes (risque 1).
- `tvctx-01` (identifiant `id` dans `/api/hello`) non exécuté : w19-02 le reprend ; si entre-temps il est fusionné, w19-02 se rebase.
