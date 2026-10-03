# tvctx-04 — Jeton renouvelé pour CHAQUE TV, et fiche chiffrée par l'Android Keystore
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (`LinkDriver`, secret)
> **Groupe : TVCTX-A** (risqué : un seul cahier à la fois sur `C/trust/LinkDriver.kt`, `S/TvLink.kt`) · prérequis : `claude/pin-persistence`, w15-03 · porte : `:core:test --tests '*LinkDriver*' --tests '*PinBook*' --tests '*Trust*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M)

Règles communes : `docs/COORDINATION.md`, en-tête de `SONNET-WAVES-INDEX.md` (branche `claude/<id>` depuis `origin/integration/agents`, un commit, rapport `docs/agent-reports/<id>.md`, jamais `main`, ni serveur ni secret, français, « CastBridge » / « CastBridge-TV »). **Test rouge d'abord, par assertion** ; à la fin `:core:test` complet + `:sender:compileDebugKotlin` + `:receiver:compileDebugKotlin` par `tools/agents/gradle-lock.sh`. Lire d'abord `docs/coordination/DESIGN-TV-CONTEXT-MULTI-TV-2026-10-03.md` et `docs/agent-reports/pin-persistence.md`. Ne jamais affaiblir l'authentification ; aucun code ni jeton dans un journal, une notification, une sauvegarde ; garder R-01…R-10.

## Objectif
1. `LinkDriver` ne renouvelle aujourd'hui que la TV **par défaut** (`step` part de `saved.default()`) : le jeton d'une 2ᵉ TV meurt après 12 h et seul le code gardé la rejoint. Renouveler à mi-vie le jeton de **chaque** TV enregistrée joignable (une tentative par TV et par cycle ; `AttemptLimiter` par pair déjà là), sans changer la carte d'état de la TV par défaut.
2. Chiffrer les valeurs `id:*` de `castbridge_pins` par une clé AES-256-GCM de l'`AndroidKeyStore` (motif de `R/KeystoreWrapper.kt` ; classement `InstallKeyPolicy` : « clé perdue » ⇒ fiche illisible ⇒ **une** demande de code avec la cause `KEY_LOST` ; « transitoire » ⇒ réessayer, jamais de demande). Migration : une valeur sans préfixe `k1:` est lue en clair et réécrite chiffrée au premier succès ; l'entrée en clair n'est retirée qu'après l'écriture chiffrée réussie (aucune perte). Les entrées héritées (une par clé d'écran) suivent la même règle.

## Fichiers (possédés)
`C/trust/LinkDriver.kt`, `C/trust/PinBook.kt` (interface `SecretWrapper` injectée, cause `KEY_LOST` dans `CredentialDecision`), nouveau `S/PinCrypto.kt`, `S/PinStore.kt`.

## Tests (rouges d'abord)
`LinkDriverTest` : deux TV, 30 h simulées ⇒ la 2ᵉ a toujours un jeton valide, la carte de la 1ʳᵉ ne bouge pas, pas plus d'un HELLO par TV et par demi-vie. `PinBookTest` : enveloppe factice ; clé perdue ⇒ `AskPin(KEY_LOST)` une fois puis le nouveau code est gardé ; transitoire ⇒ aucune demande ; migration clair → chiffré sans perte, retour arrière lisible.
