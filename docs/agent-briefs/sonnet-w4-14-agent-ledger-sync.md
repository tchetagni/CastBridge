# w4-14 — Journal des ventes sur le téléphone : fichier à ajout seul, synchronisation serveur, écran des ventes, reçus

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (après w4-11)
> **Groupe : W4c-2** (vague W4c) · prérequis : w4-11 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Ledger*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 4c · Effort M (≈ 2 j) · Statut PRÊT (après w4-11 ; en parallèle de w4-13 ; le bout en bout exige w4-16).** Conception : `docs/coordination/DESIGN-W4-VENTE-TERRAIN.md` § 4, § 9. Branche `claude/sonnet-w4-14`. Rapport : `docs/agent-reports/sonnet-w4-14.md`.

## Objectif
Le journal des ventes de l'agent vit dans un fichier **à ajout seul** avec `fsync`, se synchronise vers le serveur quand le téléphone est en ligne (curseur, idempotence, anomalies renvoyées), s'affiche (ventes, solde à remettre, versements), et chaque vente peut re-partager son reçu. Rien ne s'efface.

## Pourquoi (preuves)
- `C/sales/SalesLedger.kt` (w4-11) : modèle, chaîne, `LedgerStore` interface, `MemoryLedgerStore`.
- `C/owner/SafeFile.kt` (écriture atomique de fichiers **entiers**) : il faut un **ajout** durable : `C/io/Durable.kt` si w1-02 l'a créé (vérifier `ls android/core/src/main/kotlin/castbridge/core/io`), sinon ici.
- `C/policy/OrderTransport.kt:13-35` (client HTTP du téléphone, `Authorization: Bearer <jeton d'appareil>`, échecs = null) : même style ; `S/OrdersRuntime.kt` (tâche périodique `JobScheduler`) : même mécanique.

## Fichiers possédés
Nouveaux `C/sales/LedgerFile.kt` (`FileLedgerStore : LedgerStore` : `ledger.jsonl`, ajout + `fsync`, relecture avec vérification de chaîne au chargement, ligne tronquée finale ignorée **et** signalée, jamais réécrit), `C/sales/LedgerSync.kt` (`SyncClient` interface + `HttpSyncClient` : `POST /api/v1/agent/sync` corps `{agent, delegation, entries[], registry[]}`, réponse `{cursor, revoked, expiresAt, anomalies[]}` ; `SyncPlanner` pur : quelles entrées envoyer depuis `cursor`, que faire de `revoked`), `S/focal/LedgerFile.kt` (emplacement `files/focal/ledger.jsonl`, exclusion de sauvegarde : vérifier w1-01), `S/focal/LedgerSyncJob.kt`, `S/focal/LedgerScreen.kt`, `S/focal/ReceiptShare.kt`, tests `CT/sales/LedgerFileTest.kt`, `CT/sales/LedgerSyncTest.kt`. **Hors zone** : `S/focal/Focal*.kt`, `S/focal/Pending*.kt`, `S/MainActivity.kt` (w4-13), `C/sales/{SalesLedger,PriceGrid,Receipt}.kt` (w4-11), `backend/` (w4-16).

## Étapes
1. `FileLedgerStore` : `append` écrit `entry.canonical() + "\n"` en `FileOutputStream(append = true)` + `fd.sync()` ; `all()` relit et vérifie (`SalesLedger.chain`) ; une ligne finale tronquée (coupure) est ignorée et `loadNote` le dit (l'entrée sera réécrite par l'app : `seq` identique, même contenu : idempotent) ; une **divergence** au milieu = fichier corrompu ⇒ `degraded = true` (plus de vente possible, synchronisation seulement, message « journal altéré : contactez le propriétaire ») ; copie `.corrupt-<ms>` gardée.
2. `SyncPlanner.plan(entries, serverCursor): List<Entry>` ; `apply(response)` : `revoked` ⇒ l'app passe en lecture seule (w4-13 lit `FocalStore.revoked`), `anomalies` affichées.
3. `HttpSyncClient` (HttpLite, 20 s, corps ≤ 1 Mo : par lots de 200 entrées) ; jeton d'appareil : celui de l'enregistrement existant du téléphone (`DeviceService.register`, côté `S/` : trouver où le téléphone garde son jeton : `grep -rn 'deviceToken\|Bearer' android/sender/src/main --include='*.kt' | head`).
4. `LedgerSyncJob` : à chaque connexion réseau et toutes les 6 h (`JobScheduler`, contrainte réseau), après chaque vente si en ligne ; journalise le dernier succès ; bouton « Synchroniser maintenant ».
5. `LedgerScreen` : liste (date, TV, article, prix, encaissé, reçu, statut envoyé/non), totaux (encaissé, remis, **à remettre**), « Déclarer un versement » (`REMIT`, montant, date) ; filtre par mois ; aucune action de suppression.
6. `ReceiptShare.share(ctx, entry, name, contact)` : `Receipt.text` → `Intent.ACTION_SEND` ; `contact` = `BuildConfig.OWNER_CONTACT` (vide ⇒ ligne omise).
7. Exclusion de sauvegarde : `files/focal/` exclu (w1-01 possède les règles : si la règle manque, `STATUT: BLOQUÉ` partiel, ne pas éditer `res/xml`).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.sales.*'   # vert
cd android && gradle --offline :sender:compileDebugKotlin   # si SDK
grep -rn 'delete()\|writeText(' android/core/src/main/kotlin/castbridge/core/sales/LedgerFile.kt   # 0 hit (ajout seul ; la copie .corrupt utilise copyTo)
```
Tests : ajout + relecture ; coupure simulée (ligne tronquée) ; divergence ⇒ `degraded` ; plan de synchronisation depuis un curseur ; réponse `revoked` ; lot de 200.

## Cas limites
- Deux téléphones pour un même agent : interdit par conception (une clé = un téléphone) ; le serveur verrait deux chaînes divergentes ⇒ anomalie ; le dire dans le KDoc.
- Changement d'heure du téléphone : `at` est informatif ; `seq` fait foi.

## À ne pas faire
Pas de commit sur les branches partagées ; aucune suppression/réécriture du journal ; aucun secret ; pas d'envoi du jeton d'activation (seulement `fp`) ; français.

## Rapport
`STATUT`, format exact d'une ligne du journal (exemple), endpoint et corps convenus avec w4-16 (copier le JSON), où le téléphone garde son jeton d'appareil.
