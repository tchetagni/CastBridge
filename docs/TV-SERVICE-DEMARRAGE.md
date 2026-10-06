# CastBridge-TV : service permanent et démarrage avec la TV

Exigence du propriétaire (2026-10-06) : la version TV doit fonctionner en service et au démarrage, sur toutes sortes de boîtiers.
Beaucoup de boîtiers tuent les services d'arrière-plan ou ne livrent pas `BOOT_COMPLETED` à une app jamais ouverte.

## Comment ça tient

| Situation | Mécanisme | Délai visé |
|---|---|---|
| La TV démarre | `BootReceiver` (BOOT_COMPLETED, QUICKBOOT_POWERON, HTC QUICKBOOT, MY_PACKAGE_REPLACED) ; wake lock de 30 s ; `TvService.start` ; une ligne de journal « démarré au boot (raison : …) » | `/api/hello` ≤ 90 s |
| Android tue le processus (mémoire) | `START_STICKY` (si le système le rend) puis chien de garde `ServiceKeepAlive` : `JobScheduler` périodique de 15 min, persistant (`setPersisted(true)`), + `AlarmManager.setInexactRepeating` de repli | ≤ 15 min |
| L'utilisateur balaie l'app des récentes | `TvService.onTaskRemoved` : alarme `setAndAllowWhileIdle` à +3 s | ≈ 3 s |
| Mise à jour de l'app | `MY_PACKAGE_REPLACED` (inchangé) | quelques secondes |

Le chien de garde est armé au démarrage du service, au boot et après une mise à jour (idempotent : un seul job, une seule alarme). La décision de relancer est la règle pure `core/tv/KeepAlivePolicy.kt` : on ne relance **pas** si le service vit déjà, si « Démarrer avec la TV » est désactivé, ni après un arrêt volontaire du propriétaire (réglage `service_stopped_by_owner`, aucun écran ne le pose aujourd'hui : le MENU ne propose pas d'arrêt du service).

## Ordre de démarrage (5 secondes)

`startForegroundService` impose `startForeground` dans les 5 s. Dans `TvService.onCreate`, `startInForeground()` est le premier appel (avant le canal de transfert, le chien de garde et `startCore`). `startCore` (index, contenu, serveur) reste sur le fil principal comme avant : il est après `startForeground`, donc le délai de 5 s est tenu, mais un démarrage très lent peut encore retarder l'écran. Non déplacé hors du fil principal (risque de régression élevé, rien mesuré).

## Limites honnêtes

- Depuis Android 12, un service de premier plan lancé depuis l'arrière-plan peut être refusé (`ForegroundServiceStartNotAllowedException`). Les diffusions de démarrage en sont exemptées, le job et l'alarme du chien de garde ne le sont pas partout : l'échec est capturé et journalisé, la relance suivante réessaie. L'exemption de batterie (ci-dessous) aide justement ce cas.
- Boîtiers qui ne livrent pas BOOT_COMPLETED à une app jamais ouverte : ouvrir l'app une fois ; le job persistant fait ensuite le reste.
- `LOCKED_BOOT_COMPLETED` **n'est pas ajouté** : le receiver devrait être `directBootAware`, or le service lit `TvPrefs`, le trousseau (activation, lots) et les médias, tous dans le stockage chiffré indisponible avant le premier déverrouillage. `BOOT_COMPLETED` arrive dès que le stockage est disponible, ce qui suffit sur une TV sans écran de verrouillage. Test : `LOCKED_BOOT_COMPLETED` ne démarre rien.
- Exemption d'optimisation de batterie : nouvelle permission `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, la seule ajoutée. Justification : seul moyen d'éviter la mise en sommeil du service par les boîtiers à économie d'énergie agressive. Une ligne du MENU, « Autoriser CastBridge-TV à rester actif en arrière-plan », n'apparaît que si l'app n'est pas déjà exemptée, que l'écran système existe (`resolveActivity`) et qu'elle n'a pas déjà été choisie ; le choix (accord ou refus) est mémorisé (`battery_exemption_asked`), jamais de boucle.

## Vérifier

- Service : INFO > « Service : vivant depuis 1 h 12 · démarré au boot ».
- `adb shell dumpsys activity services castbridge.receiver` : `TvService` avec `isForeground=true`.
- `adb shell dumpsys jobscheduler | grep -A3 castbridge.receiver` : job `KeepAliveJob` (id 3101) périodique 15 min.
- `adb logcat -d | grep CastBridgeTV` : « démarré au boot », « chien de garde : service absent, relance ».
- `cbdev status`, puis `curl http://<ip-tv>:8765/api/hello` après `adb reboot` (décision du propriétaire).
- Tuer : `adb shell am kill castbridge.receiver` ou `adb shell run-as`… puis attendre ≤ 15 min (forcer : `adb shell cmd jobscheduler run -f castbridge.receiver 3101`).
- Balayage : retirer l'app des récentes, `TvService` revient ≈ 3 s plus tard (sur les boîtiers qui tuent le service au balayage).

Parcours : P-31 de `docs/test-plans/PARCOURS-CRITIQUES.md`. Tests JVM : `KeepAlivePolicyTest`, `BackgroundPolicyTest`. Non mesuré sur la TV réelle.
