# w7-08 — `LinkJournal` (journal circulaire sans secret), `SelfTest` (une phrase, une action), `LinkTexts`, `OemBattery`

**Vague 7a · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 8.2, § 9, § 5.3. Branche `claude/sonnet-w7-08`. Rapport : `docs/agent-reports/sonnet-w7-08.md`. Remplace le cahier `sonnet-w2-09` (« Dépannage », `TvReachability`) : **ne pas l'exécuter**.

## Objectif
(1) `LinkJournal` : anneau de 500 événements `{t, kind, route, code, ms, note}` persisté (≤ 64 Kio, ≤ 1 écriture/s), redaction systématique, export texte ; (2) `SelfTest.run(obs) → Verdict(sentence, action, code)` : fonction pure sur des observations, table **exacte** du § 8.2 figée par test ; (3) `LinkTexts` : tous les textes français de W7 (puce, mur, dialogues, notifications), aucun ailleurs ; (4) `OemBattery` : table fabricant → intention de réglage (Samsung, Xiaomi/Redmi/Poco, Tecno/Infinix/Itel, Huawei/Honor, Oppo/Realme/OnePlus, Vivo, repli) ; (5) `Diagnostics` existant gagne 4 étapes (`lan`, `isolation`, `sync`, `identity`).

## Pourquoi (preuves)
- `C/trust/Diagnostics.kt:22-24` (statuts), `:45-53` (`Redact.scrub`), `:69-147` (11 étapes) : base à étendre, redaction à réutiliser.
- `C/tunnel/TunnelJournal.kt` (journal rotatif 24 Ko) : style ; `docs/TELEMETRY.md:55-58` (clés interdites) : règle de redaction.
- `C/trust/LinkText.kt:13-16` (`LinkAction` labels), `:52-108` : textes existants des états fins — **ne pas dupliquer** : `LinkTexts` ne contient que les **nouveaux** textes.
- `S/BtPermission.kt:37-65` (« Ouvrir les réglages de l'app ») : action existante.

## Fichiers possédés
Nouveaux : `C/link/LinkJournal.kt`, `C/link/SelfTest.kt`, `C/link/LinkTexts.kt`, `C/link/OemBattery.kt`, `CT/link/LinkJournalTest.kt`, `CT/link/SelfTestTest.kt`, `CT/link/LinkTextsTest.kt`. Modifié : `C/trust/Diagnostics.kt`, `CT/DiagnosticsTest.kt` (ou le test existant qui le couvre). **Hors zone** : `LinkText.kt`, `S/**`, `R/**`.

## Signatures à respecter (contrat pour w7-15, w7-16, w7-19, w7-20)
```kotlin
package castbridge.core.link
enum class JKind { DISCOVER, ROUTE_UP, ROUTE_DOWN, HS_OK, HS_FAIL, SYNC_DELTA, SYNC_NOTIFY, SYNC_CONFLICT, XFER, JOB_RUN, JOB_MISSED, PERM, SELF_TEST, PAIR, READOPT, ERROR }
data class JEvent(val t: Long, val kind: JKind, val route: String?, val code: String?, val ms: Long?, val note: String?)
class LinkJournal(store: TextStore, val max: Int = 500, val now: () -> Long) { fun add(e: JEvent); fun recent(n: Int): List<JEvent>; fun export(): String /* redigé */ ; fun count(kind: JKind, sinceMs: Long): Int }
data class Observations(val btAdapter: Boolean, val btOn: Boolean, val btPermission: PermState, val netUp: Boolean, val vpn: Boolean, val phoneSsid: String?, val tvSsid: String?, val tvIps: List<String>, val lanProbeOk: Boolean?, val btHelloOk: Boolean?, val sdpSeen: Boolean?, val mdnsSeen: Boolean?, val isolation: Isolation, val clockDoubt: Boolean, val identity: PinCheck?, val linkState: LinkState?, val missedJobs: Int, val oem: String?, val cdm: Boolean?)
enum class PermState { GRANTED, DENIED, BLOCKED, NOT_NEEDED }
data class Verdict(val code: String, val sentence: String, val action: RepairAction, val detail: String? = null)
enum class RepairAction { ENABLE_BT, GRANT_PERMISSION, OPEN_APP_SETTINGS, OPEN_WIFI, USE_WIFI_DIRECT, RETRY, CHECK_CLOCK, CONFIRM_TV, READOPT, REASSOCIATE, OPEN_OEM_BATTERY, DISABLE_VPN, OPEN_TV_APP, NONE }
object SelfTest { fun run(o: Observations): Verdict }
object OemBattery { data class Hint(val label: String, val intentAction: String?, val component: String?, val fallbackAction: String); fun forManufacturer(m: String?): Hint }
```

## Étapes
1. `LinkJournal` : format texte une ligne/événement (`t\tkind\troute\tcode\tms\tnote`), rotation en mémoire, `export()` applique `Redact.scrub` + masque SSID (`« Maison-5G »` → `« M…G »` ? **non** : le SSID n'est jamais écrit dans le journal, seulement comparé ; `note` ne contient jamais d'IP complète : `192.168.0.•••`).
2. `SelfTest` : ordre exact § 8.2 (Bluetooth → permission → VPN → réseau différent → isolation → TV muette → app fermée → heure → identité → refus/retiré → tueur de batterie → OK) ; chaque verdict a un `code` stable (`BT_OFF`, `PERM_DENIED`, `PERM_BLOCKED`, `VPN`, `OTHER_NETWORK`, `ISOLATED`, `TV_SILENT`, `TV_APP_CLOSED`, `CLOCK`, `IDENTITY`, `FORGOTTEN`, `OEM_KILL`, `OK`).
3. `LinkTexts` : puce (`chip(phase, route)`), notifications (« <TV> activée en production », « Transfert repris », « La TV a changé d'identité : confirmez »), dialogues (ré-adoption, CDM, SAS), bandeau TV, « synchro simple (TV à mettre à jour) », mur d'isolation ; test : aucun texte ne contient d'adresse/jeton/PIN ; tous les `RepairAction` ont un libellé.
4. `OemBattery` : table figée (test) ; intentions documentées comme **non garanties** (fabricants changent) avec repli `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`.
5. `Diagnostics` : étapes `lan` (sonde IP mémorisée), `isolation`, `sync` (session CBSX/HELLO), `identity` (empreinte, épinglée ?) ; `report.text()` inchangé pour les anciennes étapes.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.link.*Test' --tests '*Diagnostics*'    # vert ; SelfTestTest ≥ 1 cas par code
grep -rn 'import android' android/core/src/main/kotlin/castbridge/core/link/ | wc -l    # 0
```

## Cas limites
Observations incomplètes (`null`) ⇒ le test passe à l'étape suivante, jamais d'exception ; deux verdicts possibles ⇒ le premier de l'ordre ; `oem` inconnu ⇒ repli ; journal corrompu ⇒ vidé et noté.

## À ne pas faire
Pas de chaîne française de W7 hors `LinkTexts`/`SelfTest` (test de source dans w7-19/w7-20) ; ne pas modifier `LinkText.kt`.

## Rapport
`STATUT`, table code → phrase → action, signatures.
