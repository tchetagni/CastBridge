# w14-07 — `tools/smoke/` : squelette de la suite de fumée (cibles, verrou, budget, pas, preuves, rapport PASS/FAIL d'une page)
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : aucune · statut : PRÊT
> **Groupe : W14-d** (vague W14, tranche S2) · prérequis : aucun pour le squelette (`--tv fake` réel exige w14-10) · porte : `python3 -m unittest discover -s tools/tests -p 'test_smoke.py' && python3 tools/smoke/smoke.py --dry-run --tv fake`
> **Jauge : ≈ 250 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 14d (outils) · Effort M (≈ 1,5 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W14` § 2 (modes, vérifications, budget, sorties, prérequis). Branche `claude/sonnet-w14-07`. Rapport : `docs/agent-reports/sonnet-w14-07.md`.

## Objectif
Créer l'ossature Python (stdlib seulement, 3.12) de la suite de fumée : ligne de commande, trois modes de TV (`fake`, `emu`, `real-ro`), deux modes de téléphone (`emu`, `real`), verrou d'exclusion, découverte des appareils `adb`, budget global (< 10 min) et par pas, enregistrement des **preuves**, rapport `REPORT.md` d'une page, code de sortie. Les vérifications concrètes (téléphone : w14-08 ; TV : w14-09 ; migration : w14-13) s'enregistrent comme **pas** dans ce squelette.

## Pourquoi (preuves)
- Aucun `tools/smoke/`, aucun `androidTest`, aucun job d'émulateur en CI ; `tools/rental-test/rental_test.py` est le seul script contre un appareil, sans assertion formelle et **modifiant** la TV.
- Partage des émulateurs et de `adb forward` entre sessions (`docs/HANDOFF.md` § 10) ⇒ verrou + ports de console dédiés ; modèle : `tools/agents/gradle-lock.sh`.
- Relais vers la vraie TV : mémoire « Relais téléphone → TV » (`adb forward tcp:18766 tcp:9876` + `nc` sur le téléphone) ; jamais automatisé.
- Modèle de tests d'outils : `tools/tests/test_release_tools.py` (faux `adb`/`ssh` par `PATH`).

## Fichiers possédés
Nouveaux : `tools/smoke/smoke.py` (entrée), `tools/smoke/core/{config,devices,lock,steps,evidence,report}.py`, `tools/smoke/checks/__init__.py` (registre des pas, vide ici sauf 2 pas de démonstration : `tv_hello`, `phone_installed`), `tools/smoke/README.md`, `tools/tests/test_smoke.py`. **Hors zone** : `tools/smoke/checks/{phone,tv_api,relay,…}.py` (w14-08/09), `tools/smoke/migrate_test.py` (w14-13), `tools/smoke/fake_tv.sh` (w14-10).

## Contrat (pour w14-08/09/13)
```python
# tools/smoke/core/steps.py
@dataclass
class Ctx: tv: TvTarget; phone: PhoneTarget; clock: Budget; evidence: Evidence; apk: dict; dry: bool
class Step:  # un pas = un parcours P-NN ou un sous-pas
    id: str; journey: str; title: str; timeout_s: int; modes: set[str]   # modes autorisés : {"fake","emu","real-ro"}
    def run(self, ctx: Ctx) -> Result  # Result(ok: bool, message: str, evidence: list[Path])
register(step)             # checks/__init__.py : REGISTRY ordonné ; smoke.py exécute ceux dont tv.mode ∈ modes
# tools/smoke/core/devices.py
class TvTarget: mode; base_url; pin (jamais imprimé) ; adb_serial|None ; def get(path) -> (status, body) ; def screenshot() -> Path ; def is_read_only()
class PhoneTarget: serial; def adb(*args) ; def install(apk) ; def dumpsys_notification() ; def ui_dump() -> xml ; def logcat(tags) ; def am_start(...)
```
`TvTarget.get` **refuse** toute méthode autre que GET en mode `real-ro` (exception explicite) ; `PhoneTarget` refuse `uninstall`, `pm clear`, `reboot` sur un sérial non émulateur sauf `--allow-phone-writes` (B-W14-1).

## Étapes
1. `smoke.py --tv {fake,emu,real-ro} --phone {emu,real} [--apk-tv P] [--apk-phone P] [--only P-11,P-22] [--dry-run] [--out DIR]` ; `--dry-run` liste les pas sélectionnés et leurs modes sans rien lancer.
2. `lock.py` : `tools/smoke/.lock` (pid + heure ; périmé après 20 min) ; émulateurs sur ports `5580` (TV) et `5582` (téléphone) si `--boot` (sinon on exige qu'ils tournent déjà : `adb devices` montre `emulator-5580`).
3. `devices.py` : relais `real-ro` = `adb -s <phone> forward tcp:18766 tcp:9876` + `adb shell "nc -s 127.0.0.1 -p 9876 -L nc <tv-ip> 8765"` en arrière-plan, nettoyé à la fin ; `base_url=http://127.0.0.1:18766`.
4. `evidence.py` : `out/<horodatage>/evidence/<step>/` (fichiers), redaction de 6 chiffres consécutifs et de `cbk_…` dans tout texte enregistré.
5. `report.py` : `REPORT.md` = en-tête (date, mode, commit `git rev-parse --short HEAD` + `-dirty` si `git status --porcelain` non vide, versions APK par `aapt2 dump badging` si disponible sinon nom de fichier), table `P-NN | pas | PASS/FAIL/SKIP | durée`, bloc « Premier échec » (pas, commande, 20 lignes de preuve, chemin), total et verdict ; code 0 si aucun FAIL.
6. Budget : horloge globale 600 s ; un pas qui dépasse son `timeout_s` = FAIL « délai » ; le reste est **SKIP** si le budget global est dépassé (le rapport le dit).
7. `test_smoke.py` : faux `adb` dans un `PATH` temporaire (comme `test_release_tools.py`), `--dry-run`, rapport sur 2 pas (un PASS, un FAIL), redaction, refus des écritures en `real-ro`, verrou périmé.

## Critères d'acceptation (hors ligne)
Porte verte ; `python3 tools/smoke/smoke.py --help` sans erreur ; `grep -n 'import ' tools/smoke/**/*.py | grep -v -E 'os|sys|json|re|time|subprocess|pathlib|dataclasses|argparse|xml|urllib|http|datetime|shutil|typing|contextlib|hashlib' | wc -l` ⇒ 0 (stdlib seulement) ; `bash -n` sans objet ; README ≤ 60 lignes avec les 3 commandes types.

## Cas limites
`aapt2` absent : versions = nom de fichier ; `nc` absent du téléphone : message clair « relais indisponible, mode real-ro impossible » ; deux sessions : le second `smoke.py` sort 2 avec le pid détenteur.

## À ne pas faire
Aucune vérification métier ici (w14-08/09) ; aucun secret imprimé ; aucun appel réseau hors `127.0.0.1` et `adb` ; ne pas démarrer d'émulateur sans `--boot`.

## Rapport
`STATUT`, arborescence livrée, sortie de `--dry-run`, décisions d'interface (champs `Ctx`), ce que w14-08/09/13 doivent fournir.
