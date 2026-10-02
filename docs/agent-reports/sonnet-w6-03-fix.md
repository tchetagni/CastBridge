# sonnet-w6-03-fix : correctifs de l'audit Opus (branche claude/sonnet-w6-03-fix)

Base : claude/sonnet-w6-03 (04b9ce5). Un seul commit, pas de push.

## Constats traités
- 1 et 4 (liaison clé d'installation / activation, codes compacts) : NON traités (conception Fable en parallèle). Limite documentée par le test `borrowedActivationAcceptedUntilBindingLands` et le vecteur `proof-borrowed-activation-accepted` (à inverser quand la liaison arrive).
- 2 : `NeedsPin` / `IdentityChanged` ne sortent qu'après la chaîne complète (état, activation, CODE_MISMATCH, ACTIVATION_INVALID...) quand la preuve porte une activation ; un refus de la chaîne l'emporte. Sans activation : question de pin directe (rien n'est débloqué). Le `lastSeq` n'est opposé qu'à la clé épinglée (compteur propre à une clé), sinon une TV réinstallée (seq 0) serait un faux rejeu.
- 3 : `ProofCache.pin` avec une clé différente efface `lastSeq`, la preuve et `validUntil` ; même clé : rien ne change.
- 5 : `revocations` obligatoire ; `lastActivation: ActivationMark?` obligatoire (clé émettrice + seq) : une activation plus ancienne de la même clé émettrice est refusée (ACTIVATION_INVALID). Le cache mémorise la marque par TV (survit à `dropProof` et au re-pin, car l'activation appartient à la TV).
- 6 : `NonceBook` (émission, consommation unique, expiration 10 min sur TvClock, borné à 32). `verify` exige le nonce vivant (sinon REPLAY) et le consomme après toute réponse sauf NeedsPin / IdentityChanged. Le paramètre `consumed` disparaît ; la tolérance SKEW de 24 h reste.
- 7 : KDoc d'OwnerFrames corrigée (l'ancienne TV répond RESULT(0)) ; w6-12 doit mapper cette réponse vers TV_OLD_VERSION (PhoneGate).
- 8 : `loadOrCreate` : `wrapper` obligatoire, une relecture après échec, exception du wrapper relancée, ancien blob gardé par `InstallSignerStore.keepUnreadable` (fichier `.unreadable-<ms>`) avant tout remplacement ; échec de sauvegarde = exception, ancien blob intact. Limite : `SecretWrapper.unwrap` renvoie null sans distinguer mauvais tag / alias absent / panne transitoire, donc la politique est « null deux fois = perdu » (blob conservé).
- 9 : `ProofCache` prend un `SecretWrapper` obligatoire ; le jeton est stocké scellé (`activationSealed`, base64) ; l'ancien format en clair (`activation`) est relu et scellé à l'écriture suivante ; clé du wrapper perdue : la preuve tombe, le pin reste.

## Vecteurs
Les 26 vecteurs existants sont inchangés (diff : 158 lignes ajoutées, 0 supprimées). Ajouts : foreign-code et unknown-activation-key sans pin, identity-changed-bad-activation, identity-changed-seq-restart, activation-rollback, activation-newer-accepted, nonce-not-issued, borrowed-activation-accepted. Nouveau champ optionnel `lastActivation` (le champ `consumed` reste : un nonce listé n'est pas émis dans le carnet du harnais).

## Risques
- Rupture d'API volontaire : `verify` (nonces, lastActivation, revocations), `ProofCache(…, wrapper)`, `InstallSigner.loadOrCreate(store, wrapper)`, `InstallSignerStore.keepUnreadable`. w6-09/w6-12 devront suivre.
- Hypothèse : le `seq` d'activation croît avec les émissions d'une même clé émettrice pour une même TV ; si la production le réinitialise, la règle de non-régression refuserait une activation légitime.
- NonceBook en mémoire : un redémarrage de l'app invalide les défis en cours (le téléphone redemande).
