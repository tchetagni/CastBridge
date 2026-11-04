# Cahier : câblage Android de « un espace par TV sur la clé » (à faire par un agent avec le SDK Android)

Agent cloud. Base : `claude/usb-espace-par-tv` (cœur et tests déjà là). PAS de pull request sans demande du propriétaire. Ne pas toucher `main`. Ne pas incrémenter `version.properties`
sans instruction. Textes utilisateur en français. Lire d'abord : `docs/coordination/DESIGN-USB-ESPACE-PAR-TV-2026-11-04.md`, `core/.../tv/UsbSpace.kt` (+ `UsbSpaceTest`), `docs/STORAGE.md` § 9.

Le cœur (`UsbSpace`, `UsbAdoption`) est pur et testé ; il ne manque que ce qui demande Android (jamais compilé ici : SDK Android absent de l'environnement du cloud).

## 1. `receiver/.../AndroidVolumes.kt` (l. ~72)
Aujourd'hui : `UsbLayout.driveRootOf(base)?.let(UsbLayout::rootOf)?.takeIf { UsbLayout.ensure(it) }`.
Cible :
```kotlin
val spaceId = UsbSpace.idOf(ActivationCenter.deviceCode)                    // null si le code n'est pas prêt : pas d'espace, repli dossier de l'app
val drive = UsbLayout.driveRootOf(base)?.let(UsbLayout::rootOf)
val heavy = if (prefs.getBool("heavy_on_usb", true) && spaceId != null && drive != null)
    (UsbSpace.ensure(drive, spaceId, System.currentTimeMillis(), writable = !readOnlyMount, free = base.usableSpace) as? UsbSpace.Ensured.Ready)?.dir else null
```
`heavyLib = heavy?.let(UsbLayout::libraryOf)` reste inchangé (`libraryOf(espace)`). `StorageVolume.heavyRoot` devient l'ESPACE (`CastBridge/<id>/`). Le fournisseur de volumes ne doit pas dépendre de l'activation au point de bloquer : `deviceCode` est calculé au démarrage (`ActivationCenter`) ; si absent, ne pas créer d'espace.
Mettre `foreignSpaces` (liste `UsbSpace.foreign(drive, spaceId)`) et `legacy` (`UsbSpace.hasLegacy(drive)`) dans `StorageVolume` ou dans un état de `TvService`.

## 2. `TvService.readopt()` (l. ~1043)
`v.heavyRoot` est déjà l'espace : le scan et l'index vivent donc dans l'espace sans autre changement. Ajouter dans `readoptState` : `foreign` (nombre), `legacy` (booléen).

## 3. Adoption (écran TV + route)
- Si `legacy` : écran TV et notification « Cette clé contient des données d'avant (à plat). Les adopter dans l'espace de cette TV ? » Boutons « Annuler » (PRÉSELECTIONNÉ) et « Adopter ». Montrer `UsbAdoption.plan()` (nombre de fichiers, taille, conflits, ignorés).
- Seul « Adopter » appelle `UsbAdoption(root, id, fs, writable).run(confirmed = true, alive = { le volume est toujours monté })`, hors du fil principal. Jamais d'adoption automatique.
- Afficher le `Report.status` : `DONE`, `DONE_WITH_CONFLICTS` (lister les conflits laissés), `REFUSED_READ_ONLY`, `REFUSED_FULL`, `REFUSED_CLAIMED` (« Une autre TV est en train d'adopter ces données »), `INTERRUPTED` (« Clé retirée : remettez-la, l'adoption reprendra »).
- `readoptDone` est déjà par volume : relancer `readopt()` après l'adoption.

## 4. Message « autre TV »
Si `foreign` non vide : « Cette clé contient les données d'une autre TV » (sans nom, sans ouvrir). Les écrans et notifications qui montrent un chemin ou un volume disent le nom de la clé et « espace de cette TV ».

## 5. `UsbImport.kt` et le reste
Vérifier que tout chemin vers la clé passe par `heavyRoot` (l'espace) : `rg "rootOf|libraryOf|UsbLayout" android/receiver` ; aucun chemin ne doit reconstruire `CastBridge/Bibliotheque` à plat. L'import du fichier `activation` à la racine de `CastBridge/` reste inchangé.
`POST /api/storage` : `heavy.root` reste `Download/CastBridge` (jamais de chemin absolu) ; ajouter additivement `heavy.space` = « espace de cette TV » (pas l'identifiant).

## 6. Vérifications
`gradle :core:test :receiver:assembleDebug` ; test manuel : deux TV, une clé (docs/STORAGE.md § protocole) ; clé ancienne à plat ; clé retirée pendant l'adoption ; clé en lecture seule ; clé pleine.
