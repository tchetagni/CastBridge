# PROTECT — Index des cahiers (protection TV, Fable 2026-10-02, révisé après décisions propriétaire)

Plan d'exécution des cahiers `protect-NN-*.md`. Conception : `docs/coordination/PROTECTION-TV-FABLE-2026-10-02.md`.
**Décisions propriétaire intégrées** : PD1 pas de lib native ; PD2 drapeau + refus du contenu LOUÉ seulement si hook ; PD3 filigrane par build puis par licencié ; PD4 INCERTAIN passe, blocage/autorisation ultérieurs par **ordre signé** (protect-09). **Prémisse corrigée** : cibles = tous les OS de smart TV (Android TV, Google TV, Fire TV, GaiaOS, autres) ; signaux génériques, indépendants de l'ABI ; le 32 bits est un budget, pas une empreinte. **W4 (boîte v2 X25519) = dépendance de conception, non implémentée.**

**Règles communes** : ne jamais committer ni pousser (le coordinateur committe) ; ne jamais toucher `main`, `~/.castbridge-signing`, les secrets ; livrables et messages en français ; garder verts émulateur / debug / `DEV_BUILD` / CastBridge Dev / `adb install` ; dégrader en douceur (message FR + assistance), jamais destructif ; ne pas dupliquer w1/w2/w3.

## Vagues et ordre de dispatch

| Vague | Cahiers | Parallèle ? | Dépend de |
|---|---|---|---|
| **A — Fondation build/paquet** | protect-01 | seul | — |
| **B — Contrôles d'exécution** | protect-02, protect-03, protect-04 | **oui** (fichiers neufs disjoints) | protect-01 |
| **C — Serveur, traçabilité, ordre signé** | protect-05, protect-06, **protect-09** | 05 ∥ 06 ; **09 après protect-02 et w2-01** | 01 ; 09 : 02 + w2-01 ; coord. w3-09 |
| **D — Étiquettes éthiques** | protect-07 | oui, indépendant | coord. w3-13 |
| **E — Mesure** | protect-08 | après A (idéalement après B) | 01 ; coord. w3-12 |

Dispatch : A → (B ∥ D) → (C : 05 ∥ 06, puis 09 dès que 02 et w2-01 sont livrés) → E. protect-07 et protect-08 peuvent partir tôt.

## Matrice de propriété des fichiers (disjointe par vague)

| Cahier | Modèle | Fichiers possédés | Points chauds partagés (édit minimale) |
|---|---|---|---|
| protect-01 | **sonnet** | `android/receiver/build.gradle.kts`, `android/receiver/src/main/AndroidManifest.xml` | — |
| protect-02 | **sonnet** | `core/.../tv/DeviceClass.kt` + test (neufs) ; `receiver/.../DeviceClassGate.kt` (neuf) | `receiver/.../PlayerActivity.kt` (1 appel) |
| protect-03 | **sonnet** | `core/.../owner/ApkIntegrity.kt` + test (neufs) ; `receiver/.../SelfIntegrity.kt` (neuf) | `receiver/.../ActivationCenter.kt` (1 point d'appel) |
| protect-04 | **sonnet** | `core/.../owner/EnvTrust.kt` + test (neufs) ; `receiver/.../TamperSignals.kt` (neuf) | `receiver/.../RentalHub.kt` (refus PD2 contenu loué, minimal) |
| protect-05 | **sonnet** | `backend/.../devices/DeviceReport.java`, `DeviceService.java`, `licenses/AbuseService.java` | `core/.../device/DeviceReport.kt` |
| protect-06 | **haiku** | `LICENSE-NOTICE` (neuf), `receiver/.../assets/NOTICE` (neuf), `receiver/.../res/values/strings.xml` | — |
| protect-07 | **haiku** | `SECURITY.md`, `ETHICS.md` (neufs) | — |
| protect-08 | **haiku** | `docs/agent-reports/protect-mesure-apk.md` (neuf) | — (lecture + build) |
| **protect-09** | **sonnet** | `core/.../policy/PolicyActions.kt`, `PolicyState.kt`, `PolicyGate.kt`, test `DeviceClassOrderTest.kt` (neuf), `receiver/.../DeviceClassBlockActivity.kt` (neuf), `docs/ORDRES.md` | `receiver/.../PolicyHub.kt` (exposer l'état, lancer l'écran) |

> **Anti-conflit** : `strings.xml` → protect-06 seul ; `build.gradle.kts`/manifeste → protect-01 seul ; `PlayerActivity` → 02 ; `ActivationCenter` → 03 ; `RentalHub` → 04 ; `DeviceReport.kt` → 05 ; `core/policy/*` + `PolicyHub` → 09 seul. B est parallélisable car 02/03/04 touchent des fichiers différents. protect-09 ne touche ni PlayerActivity ni ActivationCenter.

## Classe de coût et modèle

| Cahier | Objet | Coût | Modèle & pourquoi |
|---|---|---|---|
| protect-01 | TV-only paquet + BuildConfig | Moyen | sonnet : Gradle + placeholders + exceptions dev |
| protect-02 | Porte de classe d'appareil (générique, multi-OS) | Moyen | sonnet : logique pure + vecteurs par OS + UX |
| protect-03 | Épinglage signature + auto-intégrité redondante | Moyen | sonnet : PackageManager multi-API, redondance |
| protect-04 | Heuristiques d'environnement (PD2) | Moyen | sonnet : faux positifs, refus ciblé contenu loué |
| protect-05 | Heartbeat + registre + anomalies serveur | Moyen-élevé | sonnet : Kotlin + Java + tests |
| protect-06 | Étiquettes in-APK + filigrane (PD3) | Faible | haiku : fichiers/ressources |
| protect-07 | SECURITY.md + ETHICS.md | Faible | haiku : rédaction cadrée |
| protect-08 | Mesure taille/démarrage (v7a + arm64) | Faible | haiku : build + mesure |
| protect-09 | Ordre signé « classe d'appareil » (PD4) | Moyen-élevé | sonnet : moteur de politiques, cibles, seq, persistance, tests |

## Décisions / dépendances restantes
- **D12** (keystore release) : protect-03 épingle une empreinte réelle seulement après ; vide = ignoré.
- **D7** (contact) : protect-02/06/07/09 laissent un placeholder de contact marqué.
- **w2-01** (révocation persistée + canaux) : prérequis de protect-09 pour la livraison ; protect-09 reste testable par fichier.
- **W4** (boîte v2) : conception en cours par un autre agent ; aucun cahier PROTECT ne l'implémente ni n'en dépend pour compiler, mais la couche 1 du plan n'est complète qu'avec lui.

## Routage des modèles (Fable, 2026-10-02) — PROTECT (confirme la colonne « Modèle » : aucune divergence)

Source : `docs/coordination/ROUTAGE-AGENTS-EXECUTION-2026-10-02.md` (grille, jauges, audits, dispatch) ; table machine `docs/agent-briefs/routing.json` ; `python3 tools/agents/dispatch-plan.py --wave <vague> --done <ids>` donne les cahiers lançables. Chaque cahier porte un en-tête « Modèle · Groupe · Jauge ». Modèle **explicite** à chaque lancement ; un seul build JVM à la fois (`tools/agents/gradle-lock.sh`) ; au plus 3 agents en parallèle.

- **haiku** (3) : protect-06, protect-07, protect-08 — cahiers à convertir en forme mécanique (avant/après) avant lancement.
- **sonnet** (6) : protect-01, protect-02, protect-03, protect-04, protect-05, protect-09.
- **audit Opus avant fusion** (4) : protect-02, protect-03, protect-04, protect-09.
- **non lançables** : aucun.
- **opus** n'exécute jamais ; **fable** ne figure dans aucun routage.
