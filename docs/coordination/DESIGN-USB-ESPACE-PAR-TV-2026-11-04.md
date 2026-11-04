# Conception : un espace par TV sur chaque clé USB (2026-11-04)

Exigence du propriétaire (4 octobre 2026) : une clé USB quelconque peut être branchée à n'importe quel moment de la vie de l'app TV ; la TV la traite comme un
stockage propre à SA clé d'activation. Une même version de l'app, sur deux TV différentes (identifiants d'appareil différents), ne recouvre jamais la même donnée.

## 1. Problème

Aujourd'hui (`UsbLayout`, docs/STORAGE.md § 9) : `<clé>/Download/CastBridge/{Bibliotheque,Telechargements,Medias,index}`, à plat, sans identifiant de TV. Deux TV
qui partagent une clé voient la même bibliothèque et écrivent dans les mêmes dossiers.

## 2. Disposition cible

```
<clé>/Download/CastBridge/
    activation                      (inchangé : fichier d'activation déposé, lié à son appareil par sa signature, lu automatiquement)
    <id TV : 12 hex>/               espace de CETTE TV
        .espace                     {"id":"<id>","createdMs":<date>}  (aucune donnée personnelle)
        Bibliotheque/ Telechargements/ Medias/ index/
    <id autre TV>/                  espace d'une autre TV : signalé, jamais ouvert
    Bibliotheque/ ... (à plat)      anciennes données : à adopter (§ 5)
```

## 3. Décisions (hypothèses à confirmer par le propriétaire)

1. **Identifiant** = 12 premiers caractères hexadécimaux de SHA-256(`castbridge-espace-v1|` + code d'appareil normalisé : majuscules, sans tirets ni espaces). Le code d'appareil existe avant l'activation et ne change pas à l'activation : l'espace est le même avant, pendant l'essai et en production. Jamais le code en clair sur la clé. 48 bits : collision négligeable pour quelques TV partageant une clé.
2. **Aucun accès à l'espace d'une autre TV**, ni en lecture ni en écriture. `UsbSpace.foreign` ne fait que lister les noms de dossiers marqués ; message « Cette clé contient les données d'une autre TV ».
3. **Anciennes données à plat** : la première TV qui ouvre la clé PROPOSE de les adopter ; confirmation explicite, « Annuler » préselectionné ; DÉPLACEMENT par renommage atomique dans le même volume (`UsbAdoption`). Jamais de copie suivie d'une suppression ; jamais de suppression de donnée ; un conflit de nom laisse les deux fichiers.
4. Le fichier `activation` à la racine de `CastBridge/` reste lu automatiquement.
5. L'espace est choisi à chaque branchement à chaud, quel que soit le moment de la vie de l'app.
6. Le volume reste identifié par `VolumeRegistry`. L'espace est créé à la demande (`UsbSpace.ensure`), jamais sur une clé en lecture seule, jamais sur une clé pleine.
7. Marque d'appartenance `.espace` : identifiant et date de création seulement. Une marque dont l'identifiant ne correspond pas au nom du dossier est refusée (dossier trafiqué).
8. Les écrans et notifications disent le nom de la clé et « espace de cette TV ». Le téléphone ne sait rien de l'espace : la TV décide.

## 4. Cœur pur (`castbridge.core.tv`, fichier `UsbSpace.kt`)

- `UsbSpace.idOf(code)`, `isId`, `dirOf`, `readMark`, `ensure(root, id, nowMs, writable, free)` → `Ready | ReadOnly | Full | Failed`, `foreign(root, myId)`, `hasLegacy(root)`.
- `UsbAdoption(root, id, fs, writable, fault, now, free)` : `plan()` (déplacements, conflits, ignorés, sans rien toucher) et `run(confirmed, alive)`.

### Adoption : déroulement et reprise
1. Sans `confirmed = true` : rien ne bouge (`NOT_CONFIRMED`).
2. Refus avant toute écriture : clé en lecture seule, moins de 1 Mio libre, revendication fraîche d'une autre TV.
3. Revendication `CastBridge/.adoption` (`{id, startedMs}`), créée en exclusivité : deux TV ne peuvent pas adopter en même temps les mêmes données ; une revendication de plus de 24 h est reprenable.
4. Espace créé et marqué, puis un renommage atomique par fichier. L'état est dans le système de fichiers : un fichier est dans l'ancien dossier ou dans l'espace, jamais aux deux, jamais nulle part. Une coupure à n'importe quel point (point d'injection `fault`) se reprend en relançant `run`.
5. Seuls les dossiers vides de l'ancienne arborescence sont retirés (jamais de suppression récursive). La revendication est levée à la fin.
6. Ignorés, laissés en place : liens symboliques (jamais suivis), exécutables, noms interdits ou trop longs, trop profond. Conflits (nom déjà présent dans l'espace, ou noms ne différant que par la casse sur exFAT/FAT/NTFS) : laissés des deux côtés, signalés.

## 5. Câblage Android (non codé : pas de SDK ici, voir docs/agent-briefs/usb-espace-par-tv.md)
`AndroidVolumes` : `heavyRoot` passe de `rootOf(drive)` à `UsbSpace.dirOf(rootOf(drive), id)` après `UsbSpace.ensure`. `UsbImport` et la réadoption lisent l'espace seul. Écran/route d'adoption et message « autre TV ».

## 6. Limites connues
- Le renommage ne protège pas contre un écrasement si un fichier apparaît à la destination entre la vérification et le renommage (fenêtre de quelques ms ; la destination n'est écrite que par la TV propriétaire).
- Une TV sans horloge fiable : `startedMs` de la revendication peu fiable ; effet : reprise de revendication trop tôt ou trop tard, jamais de perte.
- Deux TV dont le code d'appareil est identique (clone) partagent un espace : c'est le même appareil au sens de l'activation.
