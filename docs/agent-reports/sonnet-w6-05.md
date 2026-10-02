STATUT: TERMINÉ
CAHIER: sonnet-w6-05 · MODÈLE: sonnet · BRANCHE: claude/sonnet-w6-05 · COMMIT: voir git log
JETONS: inconnu
PORTE: gradle-lock.sh gradle --offline :core:test --tests '*Collect*' --tests '*TvJournal*' --tests 'castbridge.core.parental.tab.*' → VERT (6 s)
SUITE COMPLÈTE: :core:test → VERT (2004 tests, 0 échec) ; écarts : aucun ; grep TvConnect|ServerLink|HttpLite dans parental → 0
FICHIERS: android/core/src/main/kotlin/castbridge/core/parental/Collect.kt (neuf) ; .../parental/tab/{TvJournal,SessionTracker,TabModel}.kt ; tests CollectTest.kt (12), tab/TvJournalTest.kt (3) ; docs/agent-reports/sonnet-w6-05.md
CHOIX: API publique (pour w6-07, w6-08, w6-13) :
- Buffered : dirty, flushIfDue(nowMs, 60_000), flushNow(nowMs) ; écriture seulement si dirty
- UsageSlots(store, now, zone) : add(day,pkg,label,minuteOfDay,dtMs), launch(day,pkg,label), forDay(day): List<AppSlots>, days() ; clé "usageslots"
- ScreenSegments(store, now, zone) : observe(screenOn, nowMs), forDay(day): Segments(day,onMin,segments[Seg(fromSec,toSec)]) ; clé "screensegs"
- ConnectionLog(store, zone) : note(kind,label,nowMs): Boolean, forDay(day): List<ConnEntry> ; ConnectionLog.sanitize ; ConnectionKind ; clé "connlog"
- AppInventoryDiff(store) : apply(day, installed): InventoryDelta, forDay(day) ; 1er appel = base de référence ; clé "invdiff"
- DailyAggregates(store) : roll(day, slots, screen?), get(day), week(days): WeekAgg(daysPresent, screenOnMin, apps) ; 35 j ; clé "dailyagg"
- minuteOfDay = minute à la FIN de l'intervalle (le tick) ; dt coupé par tranche et à minuit (partie avant minuit sur la veille, tranche 23) ; stockage interne en secondes
- apply() et note() prennent day/zone en paramètre (le cahier ne le précisait pas) ; EventType ajoutés : SUDOKU(GAMES), SCREEN, CONNECTION (OTHER)
- Fusion à 201 segments : le plus court est absorbé par le voisin le plus proche ; onMin reste exact
- Tailles mesurées : usageslots 40 apps x 24 tranches (3 min/tranche) = 5478 octets (< 6 Ko) ; journal 800 évts ≈ 160 Ko (conception)
NON FAIT / À VALIDER SUR MATÉRIEL: branchement dans ParentalHub (w6-13), chiffrement du magasin (§ 2.4), TV sans UsageStats (structure vide)
QUESTION: aucune
AUTOCONTRÔLE: [x] zone [x] porte [x] suite [x] secrets [x] dépendances [x] FR [x] diff ≤ plafond [x] un commit
