# w7-20 — CastBridge (téléphone) : écran « Connexion » (diagnostic, routes, identité, âge par domaine, journal exportable), puce de liaison permanente, carte « tueur de batterie »

**Vague 7c · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après 7a/7b ; en parallèle de w7-16/18/19 : contrats `LinkRuntime`, `SyncClient`, `DomainStores`).** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 8.2, § 9, § 5.3. Branche `claude/sonnet-w7-20`. Rapport : `docs/agent-reports/sonnet-w7-20.md`.

## Objectif
(1) `LinkChip()` composable (contrat appelé par w7-19 dans `MainActivity`) : `● Salon · Wi-Fi` / `● Salon · Bluetooth (réduit)` / `◐ Reconnexion…` / `○ Hors de portée` / `✕ Action requise`, textes `LinkTexts.chip`, toucher ⇒ `ConnectionScreen` ; (2) `ConnectionScreen` : phase, routes vivantes avec latence (`RouteTable.all()`), identité de la TV (empreinte épinglée, « TOFU le JJ/MM »), état de synchro par domaine (« activation : à jour », « bibliothèque : il y a 2 min », « rapports : 3 en attente », « synchro simple (TV à mettre à jour) »), icônes de la TV (`ico`), CDM (associé / refusé → « Associer maintenant »), carte **tueur de batterie** (visible si `journal.count(JOB_MISSED, 24 h) ≥ 2`, lien `OemBattery`), derniers événements (50), boutons « Réparer la connexion » (w7-19 `RepairScreen`), « Copier le rapport » / « Partager » (`LinkJournal.export()` + `Diagnostics` existant), « Ré-adopter » / « Réassocier » / « Oublier » (existants via `TvLinkManager`), sélecteur de TV (multi-TV) ; (3) `TvHome` carte de liaison : ligne supplémentaire « Synchronisé il y a N s » (lecture seule, hors zone pour le reste : via un composable fourni ici et appelé par w7-19 — `LinkSyncLine()`).

## Pourquoi (preuves)
- `S/TvPairScreen.kt:59-98` (`TvLinkStatus` : carte + une action + « Mes TV » + « Diagnostic »), `:101-137` (`DiagnosticDialog`, « Copier le rapport »), `:283-317` (`ManageTvsDialog`) ; `S/MyTvActivity.kt` (écran « Ma TV » de la télécommande intelligente : **pas** le lien CastBridge : ne pas confondre, lien croisé seulement).
- `C/link/{LinkJournal,LinkTexts,OemBattery,SelfTest}.kt` (7a), `S/link/LinkRuntime.kt` (w7-16), `S/link/{SyncClient,DomainStores}.kt` (w7-18).
- DESIGN-W6 § 3.9 (puce « TV cible » : W6 prévoit `TargetTvChip` ; **si** w6-16 est fusionné, `LinkChip` s'intègre à côté sans doublon, sinon `LinkChip` porte aussi « · production » quand `DomainStores.act` le dit).

## Fichiers possédés
Nouveaux : `S/link/ConnectionScreen.kt`, `S/link/LinkChip.kt`, `S/link/LinkSyncLine.kt`, `S/link/JournalShare.kt` (partage texte via `Intent.ACTION_SEND`, pas de `FileProvider` nouveau : texte ≤ 100 Ko en `EXTRA_TEXT`), `S/link/OemBatteryCard.kt`. Modifié : `S/TvPairScreen.kt` ? **non** (w7-19) : `TvLinkStatus` gagne « Connexion » via un paramètre déjà existant `onDiagnose` ⇒ w7-19 le fait pointer sur `ConnectionScreen` (contrat). **Hors zone** : tout `S/*.kt` de niveau racine, `S/link/{LinkRuntime,SyncClient,RepairScreen}.kt`.

## Signatures à respecter
```kotlin
@Composable fun LinkChip(modifier: Modifier = Modifier, onClick: () -> Unit = { ConnectionScreen.open(ctx) })
@Composable fun LinkSyncLine(tv: SavedTv)
object ConnectionScreen { fun open(ctx: Context) /* Activity légère S/link/ConnectionActivity déclarée par w7-16 dans le manifeste : si absente, dialogue plein écran dans l'activité courante */ }
```

## Étapes
1. `LinkChip` : collecte `LinkRuntime.snapshot` + `SyncClient.state` ; couleurs du thème + symbole (jamais la couleur seule) ; `contentDescription` complète (TalkBack) ; texte ≤ 24 caractères.
2. `ConnectionScreen` : sections fixes, chaque chiffre avec son mot ; âge par domaine via `SyncClient.ageOf` (`LinkTexts.ageText`) ; identité : `Identity.fingerprint(TvPinStore.get)` et date ; `ico` ⇒ ligne « Sur la TV : 2 téléphones, clé USB » ; multi-TV : sélecteur ⇒ `TvLinkManager.makeDefault`.
3. Rapport : en-tête (versions app/TV, Android, fabricant, phase, routes, identité tronquée) + `Diagnostics` existant + journal (`export()` redigé) ; test de source : le texte passe par `Redact.scrub`.
4. Carte OEM : condition sur le journal ; bouton ⇒ intention `OemBattery.forManufacturer(Build.MANUFACTURER)` avec repli ; « Ne plus afficher » (préférence).
5. Tests : logique de présentation pure dans `C/link/LinkTexts` (déjà) ; `:sender` compile ; captures clair/sombre ; TalkBack sur la puce.

## Critères d'acceptation
```sh
cd android && gradle --offline :sender:compileDebugKotlin
grep -rn 'Build.MANUFACTURER' android/sender/src/main/kotlin/castbridge/sender/link/OemBatteryCard.kt | wc -l   # ≥ 1
grep -rn '"[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}\.' android/sender/src/main/kotlin/castbridge/sender/link/JournalShare.kt | wc -l   # 0 (aucune IP en dur)
```
Observable : couper le Wi-Fi ⇒ puce passe « Bluetooth (réduit) » sans clignoter (hystérésis existante) ; « Copier le rapport » ⇒ texte sans jeton, PIN, adresse complète, SSID.

## Cas limites
Aucune TV ⇒ puce « Aucune TV » ⇒ ouvre « Ajouter ma TV » ; journal vide ; 2 TV avec une seule active ⇒ la passive affichée « surveillée (mDNS) ».

## À ne pas faire
Pas de texte hors `LinkTexts` ; pas de `FileProvider` ; ne pas toucher `MyTvActivity.kt`/`BtRoutesScreen.kt` (télécommande).

## Rapport
`STATUT`, captures, exemple de rapport exporté (redigé), signatures.
