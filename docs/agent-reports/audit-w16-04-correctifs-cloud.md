# Audit indépendant (cloud) : correctifs W16-04, règles d'émission du pilote

**Date** : 2026-10-03 · **Base** : `origin/integration/agents` @ `94ab90af` (fusion w16-04 = `c99418f8`) · **Auditeur** : lecture seule, aucun fichier de code modifié.

## VERDICT : NON MERGEABLE en l'état (vers `main`)

Raison unique : la **réémission** (correctif n° 2) ne fait pas ce qu'elle promet. Le garde-fou « clé d'installation différente » repose sur une clé **déclarée** par la demande et non prouvée. Le moteur TV **fusionne** toutes les lignes d'un même `(produit, period)`, quelle que soit leur enveloppe. Une réémission ajoutée à l'ancienne activation donne donc une fin à peu près doublée : au-delà du 01/12 en jours, au-delà du 16/11 en heures. En heures, elle rend aussi le budget déjà consommé. Démonstration exécutée, voir B1.

`spec` (nouvelle location) et `extend` (prolongation) sont **corrects** sur les points demandés : 96 h, 16/11, unités, fenêtre, quota, Langues. Si la réémission est **désactivée** (refus systématique) ou corrigée selon B1, le verdict devient **MERGEABLE AVEC CORRECTIFS LISTÉS** (I1 à I4).

### Essais exécutés
- `tools/core-harness/run.sh :core:test --tests '*Pilot*' --tests '*RentalDurations*'` (le `./gradlew` d'`android/` est absent dans ce conteneur, d'où le harnais) : **VERT**, PilotRulesTest 38/38, PilotRegistryTest 9/9, RentalDurationsTest 7/7 (54 tests, 0 échec).
- Preuves de concept dans un **worktree jetable** (supprimé ensuite, rien poussé) : 4 tests passés, sorties reproduites ci-dessous.

```
POC days:  reissue days=29 merged end=2026-12-30T12:00+01:00
POC hours: reissue min=2760 days=13 merged budget=5760 end=2026-11-28T12:00+01:00
POC checkChosen: 419 -> les heures d'utilisation se comptent en heures entières
POC checkChosen days: 25 -> « CP » : une location en jours va de 1 à 7 jours (25)
POC overflow: Success(RentalSpec(..., days=1, maxUsageMinutes=44, ...))   // Choice.Hours(71582789)
```

---

## BLOQUANT

### B1. La réémission se fusionne avec l'activation d'origine : fin au-delà du 01/12 / du 16/11, heures rendues
- **Où** : `android/core/src/main/kotlin/castbridge/core/lots/PilotRules.kt:309-323`. Le commentaire l. 309-310 et le rapport w16-04 (correctif 2, « la fusion est exclue par l'exigence d'installation différente ») affirment le contraire de ce que fait le code.
- **Pourquoi** :
  1. `RentalEngine.contracts` (`RentalEngine.kt:111-131`) regroupe **toutes** les lignes par `(productId, period)`, sans regarder l'enveloppe : `end = max(end, startsAt) + days`, budget = somme (borné à 96 h).
  2. La clé de location ne dépend que de `(master, licence, siège, produit, period)` (`RentalKeys.rentalKey`) : elle est **identique** pour la ligne d'origine et la ligne réémise.
  3. `RentalLedger.install` répond « clé déjà en place » (`RentalLedger.kt:111`) **avant** d'essayer d'ouvrir l'enveloppe.
  4. La TV garde **toutes** les activations acceptées, dédupliquées sur la seule signature (`receiver/.../ActivationCenter.kt:20-26`).
  5. La ligne `install=x25519|…` de la demande d'appareil n'est **pas authentifiée** : `DeviceRequest.parse` vérifie le code contre les empreintes, jamais la clé d'installation. `PilotRules.reissue` compare deux chaînes (`new == old`, l. 313).
- **Scénario 1 (sans aucune fraude technique)** : une location « défaut » 30 j est émise le 01/11 à 12:00 (fin 01/12 12:00). L'utilisateur efface les données de la TV, ce qui produit une nouvelle installation et une vraie nouvelle clé. Réémission le 02/11 à 12:00 : 29 jours. L'utilisateur **réimporte aussi** l'ancien jeton d'activation, qu'il a encore (même matériel, donc accepté). Le moteur fusionne : fin **30/12 12:00** (PoC). L'enveloppe retenue est `used.last().box`, celle de la réémission, que la nouvelle installation ouvre. La borne du 01/12 est franchie de 29 jours.
- **Scénario 2 (heures)** : 96 h émises le 01/11 à 12:00 (fin 15/11), 50 h utilisées, réémission le 02/11 : 46 h et 13 jours. Fusion : budget **96 h** (et non 46 h) sur la nouvelle installation, dont le compteur repart de zéro. Fin **28/11** (PoC), au-delà du 16/11. Chaque réémission (jusqu'à 3) ajoute encore.
- **Scénario 3 (TV non réinstallée)** : on forge la ligne `install=` avec une clé quelconque, on obtient une réémission et on l'importe dans la TV d'origine. La clé est déjà au coffre, donc fin repoussée de la même façon.
- **Correctif proposé** (au choix, à arbitrer) :
  - (a) La réémission émet une **nouvelle `period`**, donc un contrat distinct qui ne fusionne pas. Elle doit alors être comptée dans les 3 contrats actifs, et l'ancien contrat doit être marqué terminé dans le registre. La « même `period` » du design § 1.4 est à amender.
  - (b) Côté moteur (hors zone w16-04) : ne fusionner que les lignes dont l'enveloppe s'ouvre pour l'installation courante, ou ignorer une ligne dont `startsAt` > `period` et dont l'enveloppe vise une autre installation.
  - (c) En attendant : **refuser toute réémission** dans `PilotRules.reissue` (tranche 1) et exiger la preuve d'installation (W5 § 3.4 a) en tranche 2.
  - Dans tous les cas, ajouter un test **moteur** qui fusionne `act(origine)` et `act(réémission)` et vérifie que la fin ≤ fin d'origine et que le budget ≤ reste. Ce test manque aujourd'hui : c'est lui qui aurait détecté le défaut.

---

## IMPORTANT

### I1. `usedMinutes` n'est pas défini sur plusieurs installations : heures remises à zéro à chaque réémission
- **Où** : `PilotRules.kt:300, 318-320`.
- **Calcul** : `rest = min(granted, 96 h) − usedMinutes`, où `granted` est la somme accordée sur **toutes** les lignes d'origine. Après une première réémission, la nouvelle installation compte à partir de 0 sur le seul reste.
- **Scénario** : 96 h accordées, 90 h utilisées, réémission de 6 h. Sur la nouvelle TV, 1 h utilisée. Deuxième réémission : l'appelant passe `existing.maxUsageMinutes = 5760` (somme accordée, comme le dit la KDoc) et `usedMinutes = 60`, le relevé de la nouvelle TV. Résultat : **95 h** réémises au lieu de 5 h.
- Quand le relevé est absent ou perdu (cas même d'une réinstallation), la valeur passée est 0 : rendu complet. Seuls le quota de 192 h et les 3 réémissions bornent l'abus, soit jusqu'à 96 + 3 × 96 = **384 h** pour une location de 96 h sur sa durée de vie.
- **Correctif** : `usedMinutes` = consommation **cumulée** connue de l'émetteur, au minimum `granted − (reste déjà réémis)`. Ou bien `ContractSummary` porte le dernier reste réémis et `rest = min(dernier reste, …) − usage depuis`. Le documenter dans la KDoc et le cahier w16-05.

### I2. Les réémissions sont incompatibles avec `RentalDurations.checkChosen`, crochet prévu pour `IssueSpec.rentalCheck`
- **Où** : `RentalDurations.kt:523-524, 526-527` ; `LicensedIssuer.kt:161` applique `rentalCheck` à toute location.
- **Scénario A** : réémission en heures avec un reste non entier (720 − 301 = 419 min), refusée « heures entières » (PoC).
- **Scénario B** : réémission en heures d'un contrat prolongé jusqu'au 16/11, demandée tôt (`days` > 30 = `hourly.validityDays`), refusée.
- **Scénario C** : réémission en jours d'un bouquet `rentalDays = 7` prolongé (25 jours restants), refusée « 1 à 7 jours » (PoC).
- Conséquence : dès que w16-05 branche `checkChosen`, les réémissions légitimes échouent. Sinon, w16-05 contournera le contrôle pour les réémissions.
- **Correctif** : passer à `checkChosen` un mode « réémission », ou arrondir `rest` à l'heure **inférieure**, ce qui reste en faveur de l'émetteur.

### I3. Le registre ne permet pas de reconstruire `ContractSummary` (fin fusionnée, `installPub`, `reissues`, `endedAt`)
- **Où** : `PilotRegistry.kt:360` (9 champs, ni `installPub` ni fin), `PilotRules.kt:262-292`.
- Le plafond du 16/11 et celui de 96 h ne tiennent que si l'appelant fournit la **vraie** fin fusionnée et la **somme** accordée. Les tests le vérifient avec des `ContractSummary` écrits à la main (`PilotRulesTest.kt:124`). Aucun code ne calcule ces valeurs depuis le registre.
- **Scénario** : w16-05 calcule `endsAt = period + jours de la 1re ligne`. Chaque prolongation d'une heure ajoute un jour sur la TV, mais le contrôle `checkEnd(newStart + DAY, …, slack = DAY)` voit toujours l'ancienne fin. Une suite de prolongations dépasse alors le 16/11, jusqu'au plafond de 96 h, soit au plus 96 jours de plus.
- **Correctif** : ajouter `PilotRegistry.summary(licence, bouquet, now)` qui applique **la même règle que le moteur** : `endsAt = period + Σ jours` des lignes nouvelle/prolongation, budget = Σ heures, `reissues` = nombre de lignes `reemission`. Le tester contre `RentalEngine.contracts`. Stocker l'`installPub` (empreinte, pas de secret) dans le registre ou une table voisine. Le rapport w16-04 le signale déjà comme « risque restant ». Cela conditionne la validité du correctif n° 1.

### I4. Langues : `checkChosen` (le seul contrôle branché dans `LicensedIssuer.issue`) ne refuse pas Langues
- **Où** : `RentalDurations.kt:512-530`.
- Le refus par type, préfixe ou `freeBundles` n'existe que dans `PilotRules.bundleProduct`. Un chemin qui passe par `RightsSyntax.rental("loc-langues-fr=langues-fr:30")` avec `rentalCheck = checkChosen` ne voit plus que `RentalPolicy.refusals`, qui s'appuie sur des familles éventuellement fausses (le cas du `Cli.kt` « reserved = all − free » cité dans le test).
- **Correctif** : reprendre dans `checkChosen` le refus par type, préfixe et `freeBundles`, en défense en profondeur.

---

## MINEUR

- **M1. Débordement d'entier dans `extend` (heures)** : `PilotRules.kt:273-274`. `extend` ne borne pas `choice.h` par le haut, donc `choice.h * 60` déborde. `Choice.Hours(71582789)` donne 44 minutes, acceptées quand `weeklyQuotaHours = 0` (PoC). Sans conséquence de droits (44 min réelles), et `Choice.parse` limite à 4 chiffres, mais l'appel direct de l'API n'est pas protégé. Ajouter `if (choice.h !in 1..cap) refuse(…)` comme dans `spec`. Avec le quota actif, `hoursIssuedLast7d + h` peut aussi déborder pour `h` proche de `Int.MAX_VALUE` (`PilotRules.kt:211`) : passer en `Long`.
- **M2. La prolongation « une seule fois » n'est pas comptée** : `PilotRules.kt:277-279`. La tolérance d'un jour s'applique à **chaque** prolongation. Une location émise le 12/10 (fin 11/11) peut être prolongée 5 fois jusqu'au 16/11 (ce que le test l. 122-125 admet). La borne absolue du 16/11 23:59:59.999 tient, mais le texte du rapport (« UNE fois par location ») est inexact. À reformuler, ou à compter réellement.
- **M3. Repli silencieux sur `.bak`** : `PilotRegistry.kt:401-407, 418`. Comme `SafeFile.read` sert le `.bak` (l'avant-dernier état), `update` réécrit depuis ce dernier et **perd la dernière émission** sans le signaler. Effets : quota sous-compté (jusqu'à 96 h), `period` en cours perdue (prolongation refusée). Lire `Read.fromBackup` et refuser l'émission, ou l'avertir.
- **M4. `isDuplicate` ignore le type** : `PilotRegistry.kt:434`. Une nouvelle location de 3 h et une prolongation de 3 h le même jour sont vues comme un doublon. Faux positif, contourné par `--encore`.
- **M5. Un `Entry` de réémission accepte une quantité de 0** : `PilotRegistry.kt:368`. Si l'appelant écrit l'arrondi inférieur, une réémission de 59 min ne compte pas au quota. Imposer `quantity ≥ 1` quand `unit = heures`.
- **M6. Bord avec `graceDays = 0`** : `PilotRules.kt:186` donne au moins 1 jour, donc le dernier jour du pilote toute location en heures est refusée par `checkEnd` (fin le lendemain). Sans effet sur les valeurs du pilote (grâce de 14 jours).
- **M7. Heures futures exclues du quota** : `PilotRegistry.kt:444` ignore les entrées horodatées dans le futur (horloge d'un autre émetteur en avance). Elles pourraient compter jusqu'à `now + 168 h`.
- **M8. Mutations qui survivent aux tests** :
  - supprimer le `FileChannel.lock` (`PilotRegistry.kt:416-417`) ne casse aucun test, car seul le verrou intra-processus est exercé ;
  - le test du `.bak` (`>= 24`) n'affirme pas quelle copie a répondu ;
  - supprimer `slackMs = DAY` de `reissue` (l. 322) ne casse aucun test (pas de réémission d'un contrat qui finit le 16/11) ;
  - remplacer l'arrondi inférieur par un arrondi supérieur dans `reissue` en heures passe le test l. 241 (22,0 jours exacts), mais le test des jours (21,75) le détecte ;
  - aucun test moteur de fusion réémission + origine (voir B1).

## Points vérifiés conformes
- **96 h** : `spec` (1 à 96), `extend` (somme ≤ 5760 min, test 60 + 36 accepté, 37 refusé), `HARD_CAP_HOURS` lié au clamp du moteur, `hourly.maxUseHours` borné à 96.
- **Fin des heures** : l'émission initiale reste ≤ 15/11 23:59:59.999 (validité = jours entiers jusqu'au 15/11, au plus 30). Une prolongation donne au plus 16/11 23:59:59.999 (bords exacts testés). Une seconde prolongation qui pousserait plus loin est refusée, **à condition que `endsAt` soit la fin fusionnée** (I3).
- **Fenêtre** : du 12/10 00:00 au 01/11 23:59:59.999 (Douala UTC+1 explicite), bords testés. Prolongation fermée après le 01/11, réémission ouverte jusqu'à la fin d'origine.
- **Jours et défaut** : honorés jusqu'au 01/12 23:59:59.999. Une prolongation ne dépasse pas 30 jours restants.
- **Quota** : 192 h sur 168 h **glissantes**. Fenêtre `(now − 168 h, now]`, une date seule compte jusqu'à la fin de son jour (prudent). Les réémissions comptent.
- **3 contrats actifs** par licence (`activeCount`). La réémission et la prolongation n'ajoutent pas de contrat, ce qui est cohérent tant que la réémission garde sa `period` (voir B1a).
- **Langues** : refus par type, préfixe ou `freeBundles`, même avec des familles fausses, dans `spec`, `extend`, `reissue` et en mode `userChosen = 0`. `PilotParams.parse` refuse les clés inconnues. Catalogue absent (bouquet inconnu), bouquet sans lot, famille inconnue ou lot libre : refus (fail closed).
- **Registre** : écriture atomique (SafeFile), verrou du processus et du fichier, code TV masqué imposé par `Entry` et `parse`, aucun secret (licence opaque, ni clé ni enveloppe).
- **Compatibilité** : `RentalLines.parse` (10 champs), `IssueSpec`, `RentalSpec` et `RentalIssuing.right` sont **inchangés** par la fusion `c99418f8` (le diff de `LicensedIssuer.kt` est purement additif : `RentalChoice`, `rentalChoice`). Lignes produites épinglées et `RentalLines.bounds == null`.

## Ce que seul un essai sur appareil réel confirmera
1. **B1 de bout en bout** : sur une vraie TV, effacer les données, recevoir une réémission, réimporter l'ancien jeton. Vérifier que l'activation est acceptée, que le contrat fusionné affiche la fin repoussée et que la clé s'installe (`used.last().box`). Faire de même sur la TV d'origine avec une `install=` forgée.
2. **Fin du 16/11** : après une prolongation tardive, la bannière, la phrase de fin et l'extinction réelle à 16/11 23:59 (heure de Douala), y compris avec une horloge TV décalée ou hors ligne.
3. **Compteur d'usage** : écart entre le relevé et l'usage réel (≤ 5 %, HP9), coupures de courant et redémarrages près du budget de 96 h, extinction effective par l'usage.
4. **Plafond de 3 simultanées** sur la TV (`OVER_LIMIT`) quand une licence a 3 contrats plus une réémission ou un contrat d'une autre période.
5. **Verrou du registre** entre deux processus réels (bureau et `louer.py`), sur le système de fichiers du poste émetteur (verrou `FileChannel` sur un partage réseau ou un disque Windows).
6. **Réinstallation réelle** : vérifier que `install.key` change et que `BoxResult.OtherInstall` s'affiche sur l'ancienne enveloppe.
