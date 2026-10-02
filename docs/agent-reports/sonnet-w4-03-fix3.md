# sonnet-w4-03-fix3 : simplification du cycle de vie de la clé d'installation (branche claude/sonnet-w4-03-fix3, depuis claude/sonnet-w4-03-fix2)

Principe : trois audits ont montré que chaque récupération automatique crée un nouveau chemin de perte. On SUPPRIME le code de récupération au lieu de le corriger.

## Nouveau cycle de vie
- Une clé `install.key` existante n'est JAMAIS écrasée, déplacée ni régénérée automatiquement.
- Première création seulement (ni `install.key` ni `.bak`, dossier listé sans erreur) : clé enveloppée par le Keystore (`KeystoreWrapper.creator()`), relue par l'enveloppe avant écriture ; si le Keystore échoue, RIEN n'est écrit (pas de clé en clair, `PlainWrapper` reste réservé aux tests), état `unavailable`/`pending`, réessai par le seul délai (1 min x3 puis 1 h, une ligne de journal par processus).
- Clé existante : lue avec l'enveloppe de son `wrap=` (`keystore` seul en production). Enveloppe qui rend null (mauvais tag, clé invalidée, alias absent confirmé deux fois sur un KeyStore rechargé : `containsAlias` faux ET `getKey` null) ou fichier abîmé sans copie lisible => `unreadable`, collant jusqu'à la réinitialisation. Toute autre exception => `unavailable`, réessai éternel.
- `POST /api/activation/install-key/reset?confirm=RESET` (PIN : `TvAuth.tokenMayCall` garde déjà tout `/api/activation/install*` au PIN, donc plus strict que « PIN ou téléphone de confiance ») : ordre clé neuve écrite sous `install.key.reset-new` et relue -> copie main->.bak si absent -> `install.key` renommée `.reset-<ms>` -> neuve renommée en `install.key` -> ancienne `.bak` renommée `.bak.reset-<ms>` -> copie main->.bak. Une coupure à n'importe quelle étape laisse une clé lisible (ancienne jusqu'à l'étape 3, nouvelle après) ; rien n'est supprimé. Note : « clé d'installation réinitialisée : réémettez les locations ». Route ajoutée à `routes.txt`, refusée en essai (`TrialPolicy.DENIED_UNDER_ALLOWED`, `TrialRoutesTest`), bouton + confirm dans `admin.html`.
- `installKeyProtection` : `keystore` | `pending` | `unavailable` | `unreadable` (libellés français dans l'admin).
- Découplage : coffre de locations, registre et balayeur (`sweep`, `meterOneMinute`, `statuses`, routes `/api/rental*`) sont construits SANS la clé ; seuls `onActivation` et `requestText` l'utilisent. Clé non prête : l'activation reste enregistrée, la note le dit, `installKeyed` est rejoué sur `allActivations()` dès que la clé devient prête (ou après une réinitialisation).

## Les six constats
1. (bloquant) `containsAlias=false` pris pour une perte puis `generate()` : FERMÉ. `unwrap` ne conclut à la perte qu'après double confirmation sur un KeyStore rechargé ; `wrap()` du lecteur `install()` ne crée jamais d'alias ; seul `creator()` (première création ou reset) peut générer, après la même double confirmation. Plus de sonde, plus de repli en clair.
2. Résurrection d'une ancienne clé K1 sur K2 : FERMÉ par suppression de toute la restauration `.unreadable-*`/`.superseded-*`. Une erreur transitoire sur le fichier principal ne bascule pas non plus sur la `.bak` (fenêtre étroite du reset).
3. Restauration qui déplace main+.bak avant d'écrire : FERMÉ (code supprimé) ; le reset écrit la clé neuve d'abord, puis renomme.
4. Coupure entre `SafeFile.write` et la copie `.bak` avec `.bak` en clair : FERMÉ (plus de migration, jamais d'écriture en clair).
5. sweep/meter/statuses arrêtés quand la clé est illisible : FERMÉ par le découplage (test `sweepStatusesAndMeterRunWhileTheInstallationKeyIsUnreadable` avec un Keystore en panne).
6. Seuil 8 échecs / 3 démarrages sans durée minimale : FERMÉ (plus de seuil, plus de fichier `install.key.retry`, plus de régénération).

## Bilan de lignes (git diff, hors rapport)
Total : +430 / -617 = **-187 lignes**. Production : InstallKey.kt -42, InstallKeyPolicy.kt -46, KeystoreWrapper.kt -8, RentalHub.kt +12, TvService +5, admin.html +7, TrialPolicy 0, routes +1 (net environ -70) ; tests : -143 (InstallKeyMigrationTest supprimé, InstallKeyPolicyTest/InstallKeyTest réécrits, +19 dans RentalTest).

## Tests
- `InstallKeyTest` (11) : machine d'états (absent, pending, ready, unavailable, unreadable collant), première création sans jamais d'écriture en clair (enveloppe en panne, aller-retour incohérent, dossier vide), clé existante jamais modifiée sous 8 séquences d'exceptions x avec/sans `.bak` (fichiers identiques après chaque essai), `.bak` quand le principal est abîmé, reset (renommages, rien supprimé, noms libres à la même milliseconde, clé neuve relue), reset avec enveloppe en panne sans effet, coupure d'alimentation à chacune des 5 étapes du reset x avec/sans `.bak` (clé ancienne ou neuve, jamais une troisième, ancien texte toujours présent).
- `InstallKeyPolicyTest` (5) : tableau de classification (double confirmation de l'alias absent, 3x3x3), libellés, calendrier d'attente avec horloge injectée, `LazyKeyedApi`.
- `RentalSweepTest.sweepStatusesAndMeterRunWhileTheInstallationKeyIsUnreadable`.
- Nouveaux tests d'abord (32/32, après correction de 2 échecs de test/code : fichier de préparation vide laissé par un reset en panne, repli sur `.bak` après erreur transitoire), puis `:core:test` complet : 2101 tests, 0 échec ; `:receiver:compileDebugKotlin` OK.

## Conséquences assumées
- Une clé `wrap=plain` issue d'un build intermédiaire (fix/fix2, non publié) est illisible (`unreadable`) : réinitialisation par l'administration.
- Après réinitialisation, les locations déjà émises pour l'ancienne clé doivent être réémises.
- Docs (`docs/RENTAL-LOTS.md`, `docs/HANDOFF.md`) non réécrites ici : elles décrivent encore la régénération automatique, à aligner au merge.

## À confirmer sur une vraie TV
Création de l'alias et de la clé au premier démarrage hors fil principal ; exceptions réelles du Keystore GaiaOS (classification AEADBadTag / KeyPermanentlyInvalidated / `containsAlias` après rechargement) ; réponse du Keystore pendant un redémarrage du démon ; route de réinitialisation (PIN) puis réémission d'une location ; aucun plantage de `TvService.onCreate`.
