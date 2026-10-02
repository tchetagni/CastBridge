# w14-09 — `tools/smoke/checks/tv_*` : les pas « TV » de la fumée (API HTTP, écran, redémarrage, lecture seule sur la vraie TV par le relais, `cbdev status`)
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : aucune · statut : PRÊT
> **Groupe : W14-e** (vague W14, tranche S2) · prérequis : w14-07 fusionné · porte : `python3 -m unittest discover -s tools/tests -p 'test_smoke_tv.py'`
> **Jauge : ≈ 300 k jetons entrée / 18 k sortie** (effort S-M) · audit Opus : non

**Vague 14e (outils) · Effort S-M (≈ 1 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W14` § 2.1 (mode `real-ro`, D-W14-3), § 2.2. Parcours P-05, P-11, P-25, P-31, P-33, P-36. Branche `claude/sonnet-w14-09`. Rapport : `docs/agent-reports/sonnet-w14-09.md`.

## Objectif
Écrire les pas côté TV : contre l'émulateur (actifs : redémarrage, écran, Apprendre/Langues, carte de réception pendant une copie) et contre la **vraie TV en lecture seule** (état avant/après livraison, versions, badge, bibliothèque intacte, aucune session orpheline, capture d'écran), sans jamais modifier l'état de la TV du propriétaire.

## Pourquoi (preuves)
- Routes utiles (`tools/routes/routes.txt`, port 8765, en-tête `X-CB-Pin`) : `GET /api/hello` (public : `{"app":"castbridge-tv","v":…,"pinRequired":…}`), `/api/info`, `/api/activation` (`required, locked, label`), `/api/library`, `/api/transfer/state?id`, `/api/apk`, `/api/connections` (`StatusIconModel`), `/api/screenshot` (PNG des fenêtres CastBridge-TV seulement : `ScreenCapture.kt`).
- `cbdev status` (SSH 2223, clé) : Android/API/ABI, autorisation d'installer, versions et codes de `castbridge.*` (`docs/DEV-BRIDGE.md`, `android/devbridge/.../DevCommands.kt`) ; **ne lit pas le logcat** de CastBridge-TV (uid différent).
- R-04 : « Prêt à recevoir » figé = `R/HomeScreen.kt:124-126` ; pendant une copie, l'écran doit changer : sur l'émulateur `uiautomator dump` ; sur la vraie TV, **différence** entre deux `/api/screenshot` (avant/pendant) > seuil.
- Verrou PIN : 5 refus/60 s (`C/tv/Security.kt`) ⇒ en `real-ro`, le PIN n'est **jamais** deviné : il est fourni par `--tv-pin-file` (fichier hors dépôt, 0600) ou l'invite, et toutes les routes authentifiées sont tentées **une fois**.

## Fichiers possédés
Nouveaux : `tools/smoke/checks/tv_api.py` (P-05 `hello`/`info` ; P-25 `activation` ; bibliothèque listée), `tools/smoke/checks/tv_state_snapshot.py` (`real-ro` : empreinte avant/après = versions `/api/apk` + `cbdev status`, badge, nombre de fichiers par catégorie, sessions de transfert, `/api/connections` ; comparaison et diff lisible), `tools/smoke/checks/tv_screen.py` (émulateur : `uiautomator dump` côté TV pendant `phone_copy` ; réelle : différence d'images `/api/screenshot` via `zlib`/PNG brut sans dépendance : comparer les octets décompressés de l'IDAT, seuil 2 %), `tools/smoke/checks/tv_reboot.py` (émulateur seulement : `adb -s emulator-5580 reboot`, attendre `/api/hello` ≤ 90 s, `GET /api/connections`), `tools/smoke/checks/tv_learn.py` (P-33 : `am start` des activités Apprendre/Langues, dump non vide, logcat TV sans `FATAL`), `tools/smoke/lib/{relay,png,cbdev}.py`, `tools/tests/test_smoke_tv.py` (fixtures JSON/PNG minimales). **Hors zone** : `tools/smoke/core/**`, `checks/phone_*`.

## Étapes
1. `lib/relay.py` : établir/fermer le relais (`adb forward` + `nc` en tâche de fond sur le téléphone ; sonde `GET /api/hello` ≤ 5 s) ; `lib/cbdev.py` : `ssh -p 2223 -o BatchMode=yes tv@<ip> cbdev status` (via relais SSH `adb forward tcp:18767 tcp:9877` + `nc … 2223`) ⇒ dict versions ; `lib/png.py` : lecture dimensions + IDAT.
2. `tv_api.py` : modes `{"fake","emu","real-ro"}` ; en `real-ro` : GET seulement, `TvTarget.get` l'impose.
3. `tv_state_snapshot.py` : `before` (début de session) et `after` (fin) ; en `real-ro` l'assertion est « identique sauf les champs attendus » (versions si une livraison a eu lieu **par le propriétaire**, jamais par le script).
4. `tv_screen.py` : s'abonne au pas `phone_copy` (hook `on_progress` du registre w14-07) pour capturer **pendant** ; assertion : écran différent de « Prêt à recevoir » pendant, identique après.
5. `tv_reboot.py`, `tv_learn.py` : `{"emu"}` seulement.
6. Tests : parseurs sur fixtures ; refus d'un POST en `real-ro` (exception) ; seuil d'image.

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -n 'method=\|POST\|PUT\|DELETE' tools/smoke/checks/tv_*.py | grep -v real_ro_refuses | wc -l` ⇒ 0 ; `--dry-run --tv real-ro` liste exactement `tv_api`, `tv_state_snapshot`, `tv_screen` ; aucun chemin de PIN en clair dans le dépôt.

## Cas limites
`/api/screenshot` peut renvoyer 404 sur une ancienne TV ⇒ SKIP motivé, pas FAIL ; `nc` absent ⇒ `real-ro` impossible, message clair ; relais SSH et HTTP sur deux ports distincts.

## À ne pas faire
Aucune route d'écriture, aucune installation, aucun `cbdev install`, aucun redémarrage de la vraie TV, aucun PIN deviné ; aucune dépendance Python.

## Rapport
`STATUT`, pas et modes, format de l'empreinte `real-ro`, ce qui n'a pas pu être exercé (vraie TV non jointe depuis le poste de l'agent), seuil d'image retenu.
