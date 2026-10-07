# Audit anti-régression (Opus, lecture seule, 2026-10-07) — `android/` de 6565f330 à cf6cc286 (15 commits, 88 fichiers, +4430/−266)

CONFIRMÉ = lu dans le code ; PLAUSIBLE = scénario non exécuté. Compatibilité filaire 1.2.51 ↔ TV 0.14.39/0.14.43 : aucune régression (champs `profile`, `cause`, `stalled` optionnels ; tranches déjà connues de la TV ; `blockSize` jamais émis par la TV donc branche téléphone inactive). Fuites : aucune trouvée. Règles pures : toutes testées, sauf les cas listés.

## HIGH
- **H1 (R-22, CONFIRMÉ)** `TransferQueue.anchorNew` / `SourceAnchor.CacheGuard.fits` : fichier non ancrable (taille inconnue, > 2 Go, > 25 % de l'espace libre) ⇒ « à repartager » immédiat sans essai, alors qu'il est lisible (ex. film Telegram 1,5 Go avec 4 Go libres). Correctif : ancrage volatil (URI d'origine), RESHARE seulement si `readable()` échoue à l'envoi.
- **H2 (R-21, PLAUSIBLE)** `Scheduler.tick()/report()` : tout 429 compte comme renvoi, la progression ignore les octets en cours ⇒ sur Wi-Fi lent avec TV qui limite les flux, arrêt à tort « ne les enregistre pas » après 90 s. Correctif : ne compter que `cause=="stalled"`, inclure `run.written` ; corriger `R21SlowLinkTest:109`.
- **H3 (5 GHz, PLAUSIBLE/code CONFIRMÉ)** `WifiDirectGroup.config` : seul le matériel TV est vérifié ; un téléphone 2,4 GHz seul ne voit pas le groupe. Correctif : AUTO pour le groupe automatique `forPhone`, 5 GHz pour le groupe MENU, repli si personne ne rejoint.

## MEDIUM
- **M1 (CONFIRMÉ)** ancrage R-22 sur le fil principal (4 requêtes MediaStore, 64 Kio lus, `freeBytes`) depuis `OpenWithActivity`, `CastSession`, `TvHome` (N fichiers ⇒ ANR) ; copies en cache parallèles sans remesure. Correctif : IO + sérialisation.
- **M2 (PLAUSIBLE → MESURÉ)** `ResourceProfile` : la TV de référence a `ro.config.low_ram=true`, heapgrowthlimit 160m, 981 Mo, 4 cœurs ⇒ ÉCONOME par le seul drapeau, contraire à « NORMAL = avant ». Règle à corriger (drapeau seul avec RAM > 768 et heap > 96 ⇒ NORMAL, vignettes réduites seulement).
- **M3 (CONFIRMÉ)** `TvLink.recoverKnownTv` / `RecoveryCandidates.eligible` : TV aux UUID inconnus (cache SDP vide) exclue ⇒ un téléphone réinstallé ne retrouve plus sa TV ; gain nul contre les écouteurs (déjà exclus). Correctif : inconnu éligible avec backoff.
- **M4 (CONFIRMÉ)** `UploadService.helloOk` sans `BoundRoute` ⇒ sur Wi-Fi Direct avec données mobiles par défaut, sonde toujours en échec (attente 2 h puis TV_GONE).
- **M5 (CONFIRMÉ)** `hintFor`/`trustedBases`/`TvAddressMemory` indexés par nom de base (« (2) » retiré) alors que `PinBook` distingue : envoi vers la mauvaise TV du même modèle, 401, verrou PinGuard. Peut attendre.
- **M6 (CONFIRMÉ)** `ReceiverServer.diskFailure` else ⇒ 500 `cause:"io"` fatal, y compris `ClosedChannelException` (assembleur fermé en parallèle). Correctif : 503 `interrupted`, `retry:true`.

## LOW
`ServiceKeepAlive` : `service_stopped_by_owner` jamais écrit ; relance FGS depuis job/alarme refusée sans exemption batterie (silencieux). `LaneRegistry.writeSlice` sans `touch()`. `HttpConn` 15 s : relecture d'un bloc de 8 Mio sur clé lente peut dépasser ⇒ RST en boucle (PLAUSIBLE). `OpenWithActivity` : « Valider et envoyer » = copie toujours ; rotation pendant la permission média perd l'action. `CODE_REQUIRED_LINE` « Touchez pour saisir » sans bouton ; `isCodeRequired("(403)")` englobe la TV verrouillée. `LockedPinRotation` : à documenter pour le support. Télécommande : facteur 0,7 ⇒ ~31 dp (< 48) ; |◀◀ ▶▶| ressemblent à Précédent/Suivant. TV : `FileBrowser` 2000 lignes sur le fil principal ; `lockedWifiInfo()` à chaque poll. `TvHome` « Choisir le fichier » à vérifier à 360 dp.

## Tests manquants
H2 (bloc lent + 429 régulation), M2 (1 Go avec memoryClass 96/128), `RecoveryCandidates` UUID inconnus, `anchorNew` taille inconnue / trop gros.

## Ordre des correctifs avant diffusion
H1, H2, H3, M1, M2, M3, M4 (confiés à un agent Sonnet le 2026-10-07) ; M5, M6, LOW peuvent attendre (M6 ajouté au lot car peu coûteux).
