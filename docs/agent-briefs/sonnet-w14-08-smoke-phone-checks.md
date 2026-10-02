# w14-08 — `tools/smoke/checks/phone*` : les pas « téléphone » de la fumée (installation, Ouvrir avec, PIN, copie avec notification, permissions) par `adb`
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : aucune · statut : PRÊT
> **Groupe : W14-e** (vague W14, tranche S2) · prérequis : w14-07 et w14-10 fusionnés ; w14-11 souhaité (marqueurs) · porte : `python3 -m unittest discover -s tools/tests -p 'test_smoke_phone.py'`
> **Jauge : ≈ 350 k jetons entrée / 22 k sortie** (effort M) · audit Opus : non

**Vague 14e (outils) · Effort M (≈ 1,5 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W14` § 2.2 (familles de vérifications). Parcours P-05, P-06, P-07, P-11, P-22, P-23, P-24, P-29, P-35, P-36. Branche `claude/sonnet-w14-08`. Rapport : `docs/agent-reports/sonnet-w14-08.md`.

## Objectif
Écrire les pas de fumée côté téléphone, exécutables contre la **fausse TV du Mac** (`--tv fake`, scénarios) ou l'émulateur TV, avec un téléphone émulé ou réel (lecture + `install -r` seulement). Chaque pas affirme un **observable** (notification, écran, logcat, fichier reçu) et dépose sa preuve.

## Pourquoi (preuves)
- R-02 n'est visible que dans la **boîte** « Ouvrir avec » (`S/OpenWithActivity.kt`) : `uiautomator dump` la lit (`text="Copier vers la TV"`, `enabled`).
- R-04/R-01 : la notification (`dumpsys notification --noredact` : `pkg=castbridge.sender`, `android.title`, `android.text`, `android.progress`) et le logcat (`UploadService`, `TvLink`, `TransferQueue` ; marqueurs `CB_JOURNEY` après w14-11).
- Permissions en boucle (R-05) : `GrantPermissionsActivity` dans `dumpsys activity activities`, compte sur 30 s.
- Intent de partage : `am start -a android.intent.action.SEND -t video/mp4 --eu android.intent.extra.STREAM content://... -n castbridge.sender/.OpenWithActivity` (vérifier le nom exact de l'activité dans `android/sender/src/main/AndroidManifest.xml`).
- Fichiers de test : `adb push` d'un fichier généré (5 Mio et 256 Mio déterministes) dans `/sdcard/Download/cb-smoke/`, exposé par `content://media/external/file/<id>` après `am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE`.

## Fichiers possédés
Nouveaux : `tools/smoke/checks/phone_install.py` (P-29 partiel : install `-r`, version, signature), `tools/smoke/checks/phone_pin.py` (P-05/06/07 : saisie par `input text` sur l'écran « Avancé », une seule tentative), `tools/smoke/checks/phone_openwith.py` (P-22/23/24 : états `fake` `fresh`/`pin-only`/`reinstalled`, boîte lue), `tools/smoke/checks/phone_copy.py` (P-11/P-36 : copie 5 Mio, notification échantillonnée toutes les 2 s, fichier côté TV par `GET /api/library`), `tools/smoke/checks/phone_permissions.py` (P-35), `tools/smoke/lib/{ui,notif,logcat,media}.py` (parseurs), `tools/tests/test_smoke_phone.py` (parseurs sur extraits figés `tools/tests/fixtures/smoke/*.txt`). **Hors zone** : `tools/smoke/core/**`, `checks/tv_*`, `migrate_test.py`.

## Étapes
1. `lib/notif.py` : parse `dumpsys notification --noredact` ⇒ liste `{pkg,title,text,progress,max,ongoing}` ; `lib/ui.py` : `uiautomator dump` ⇒ XML ⇒ `find(text=…, enabled=…)` ; `lib/logcat.py` : `logcat -d -v time -s <tags>` ⇒ lignes, détection `FATAL EXCEPTION`/`ANR in castbridge` ; `lib/media.py` : génération et `push` des fichiers, URI.
2. `phone_openwith.py` : pour chaque état fourni par la fausse TV (`fresh` sans association ⇒ attendu « Aucune TV ajoutée » ; `pin-only` après `phone_pin` ⇒ attendu nom de TV + « Copier » actif ; `reinstalled` ⇒ texte « réinstallée » ou file d'attente, jamais « Aucune TV ») : lancer l'intent, attendre la boîte ≤ 5 s, dump, assert, `screencap` en preuve, `BACK`.
3. `phone_copy.py` : intent de partage puis toucher « Copier » (`input tap` sur les coordonnées du nœud) ; échantillonner la notification jusqu'à `final` (≤ 60 s) ⇒ progression monotone, titre = nom, dernier texte « Terminé » ; `GET /api/library` de la TV contient le nom avec la bonne taille ; logcat sans `FATAL`.
4. `phone_pin.py` : `am start` de l'écran Avancé (ou navigation par `uiautomator`), `input text 000000` ⇒ texte d'erreur présent, **aucune** 2ᵉ requête (la fausse TV compte : `GET /__smoke/stats` de w14-10) ; puis bon code ⇒ « Connectée ».
5. `phone_permissions.py` : `pm revoke castbridge.sender android.permission.BLUETOOTH_CONNECT` (émulateur seulement), `am start` ×3 ⇒ au plus 1 `GrantPermissionsActivity` par type sur 30 s.
6. Modes : tous les pas `{"fake","emu"}` ; aucun en `real-ro`.

## Critères d'acceptation (hors ligne)
Porte verte (parseurs sur fixtures) ; `python3 tools/smoke/smoke.py --dry-run --tv fake --phone emu` liste les 5 pas ; aucun `pm uninstall`/`pm clear`/`reboot` dans `checks/phone_*.py` (`grep -c` ⇒ 0).

## Cas limites
Samsung (`real`) : `dumpsys notification` peut masquer le texte sans `--noredact` (API 30+ : présent) ; `uiautomator dump` échoue si le clavier est ouvert (fermer par `BACK`) ; émulateur sans Bluetooth : les pas « confiance » passent par le HELLO TCP de la fausse TV (w14-10) ; sinon SKIP motivé.

## À ne pas faire
Aucune écriture destructrice sur un téléphone réel ; aucune saisie du vrai PIN de la TV du propriétaire (mode `fake` seulement pour `phone_pin`) ; pas de dépendance Python.

## Rapport
`STATUT`, pas livrés et modes, extraits de `REPORT.md` d'un passage `--tv fake --phone emu` si le coordinateur a lancé la fausse TV (sinon : « non exécuté, parseurs testés sur fixtures »), limites Samsung constatées ou supposées.
