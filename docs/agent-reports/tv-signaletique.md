STATUT: TERMINÉ
CAHIER: tv-signaletique · BRANCHE: claude/tv-status-signals

Cause : la puce de l'accueil de CastBridge-TV ne regardait que « le serveur existe » : verte même sans réseau.

Livré
- Cœur : `C/ux/TvSignal.kt` (`TvSignal.of(TvFacts)` → niveau, texte, action, 5 indicateurs ; `SignalColors` ; `phoneLevel(LinkState)`). Test : `CT/ux/TvSignalTest.kt` (8 tests, table de 19 situations + balayage exhaustif « jamais plus vert que le pire indicateur »).
- TV : `TvSignalViews.kt` (pastilles dessinées, rangée, relevé des faits), puce + rangée dans `HomeScreen.kt`, `signal()` + légende dans l'aide + lignes de tête dans « Connexion & réglages » + tuile Téléchargements orange « Internet requis » (`PlayerActivity.kt`, `TvCards.kt`).
- Téléphone : la pastille de la fiche TV (`TvPairScreen.kt`) utilise `phoneLevel` ; aucun texte changé.
- Docs : section « Signalétique de la TV » (DESIGN-UX-ERGONOMIE…), parcours P-71.

Preuves
- ROUGE par assertion avant la fonction réelle (comportement ancien « toujours vert » simulé) : 3 échecs sur 8 ; puis VERT.
- `:core:test` complet VERT ; `:sender:compileDebugKotlin` et `:receiver:compileDebugKotlin` VERTS.

Tableau final : vert = LAN actif + écoute + stockage ≥ 1 Go ; orange = Bluetooth seul, Wi-Fi sans adresse, signal faible, stockage < 1 Go, transfert échoué, clé lente, code qui change ; rouge = aucun réseau et Bluetooth coupé/inutilisable, service muet, stockage < 100 Mo, contrôle parental bloquant ; noir = inactif par choix, Internet absent.

Captures (émulateur seulement, `docs/agent-reports/tv-signaletique/`) : avant-tout-ok, avant-sans-reseau (puce verte à tort), apres-sans-reseau-bt-actif (orange), apres-sans-reseau-bt-coupe (rouge), apres-tout-ok (vert), apres-lan-sans-internet (vert + Internet noir). Stockage faible non atteignable (test unitaire). Réglages restaurés (Wi-Fi, données, Bluetooth, 1280x720 @160, URLs captive supprimées).

Non alimentés : contrôle parental bloquant, transfert échoué, code qui change (faits à false) ; pas d'arrêt dur d'horloge ; Wi-Fi Direct non compté comme LAN.
Seule la vraie TV confirme : Ethernet/Wi-Fi réels, lisibilité à 3 m, glyphes, chevauchement avec la barre d'icônes (marge 220 dp posée).
FUMÉE: à lancer par le coordinateur (tools/smoke/smoke.py --tv fake)
