# w11-01 — CastBridge (téléphone) : onglet de départ = accueil, mémoire du dernier onglet, barre du haut réduite à une action « Plus »
<!-- routage Fable 2026-10-02 -->
> **Modèle : haiku** · escalade : sonnet si une ancre « avant » est introuvable ou si la compilation échoue deux fois · statut : PRÊT
> **Groupe : W11-a** (vague W11) · prérequis : aucun · porte : `grep -c 'DropdownMenuItem' android/sender/src/main/kotlin/castbridge/sender/MainActivity.kt  # 4`
> **Jauge : ≈ 60 k jetons entrée / 5 k sortie** (effort S) · audit Opus : non

**Vague 11a (gain rapide, livrable seul) · Effort S (≈ 0,5 j) · Modèle : haiku · Statut PRÊT.** Conception : `docs/coordination/DESIGN-W11-NAVIGATION-ALLEGEE-2026-10-02.md` § 1.1, § 3.1, § 9.1 (Q1, Q6). Branche `claude/sonnet-w11-01`. Rapport : `docs/agent-reports/sonnet-w11-01.md`.

## Objectif
Au lancement, l'app s'ouvre sur l'onglet « CastBridge TV » (l'accueil des tâches) et non sur « TV DLNA » ; elle rouvre ensuite le dernier onglet utilisé. La barre du haut ne garde que le logo (entrée propriétaire cachée, inchangée) et **un** bouton ⋮ « Plus » dont le menu déroulant porte les quatre anciennes actions. Aucune fonction n'est retirée.

## Pourquoi (preuves)
- `android/sender/src/main/kotlin/castbridge/sender/MainActivity.kt:83` : `var tab by rememberSaveable { mutableStateOf(0) }` ⇒ onglet 0 = « TV DLNA » (`:117`), fonction rare, ouverte à chaque lancement.
- `MainActivity.kt:106-111` : quatre actions permanentes (« Activer la TV », « Locations », cadenas, réglages) à côté de six onglets défilants (`:115-123`) ⇒ 11 cibles de navigation permanentes (conception § 1.1).
- `MainActivity.kt:99-104` : l'entrée propriétaire cachée (7 touches + appui long sur le logo) doit rester identique.

## Fichiers possédés
- Modifié : `android/sender/src/main/kotlin/castbridge/sender/MainActivity.kt` (fonction `Root()` seulement : lignes 82-136).
- Hors zone : tout le reste (`TvHome.kt`, `ConnectScreens.kt`, `ParentalTab.kt`, ressources). Ne pas créer de nouveau fichier.

## Étapes
1. Onglet initial : lire `SharedPreferences("castbridge_home").getInt("last_tab", 1)` dans `Root()` (clé `last_tab`, défaut **1** = « CastBridge TV ») ; `rememberSaveable` conserve la rotation ; dans `select(i)` écrire `last_tab` (sauf pour l'onglet Parental, index 5 : on ne rouvre jamais l'app sur l'onglet protégé ; écrire 1 à la place).
2. Barre du haut : remplacer les quatre `actions` (`:107-110`) par un seul `IconButton` (icône `Icons.Filled.MoreVert`, `contentDescription = "Plus"`) qui ouvre un `DropdownMenu` à quatre entrées, dans cet ordre et avec ces libellés exacts : « Activer la TV », « Locations sur la TV », « Contrôle parental », « Réglages ». Chaque entrée appelle exactement ce que l'ancien bouton appelait (`ActivateTvActivity.open`, `RentalDeliveryActivity.open`, `ParentalActivity.open`, `settings = true`). Icône de charte devant chaque entrée quand elle existe (`R.drawable.ic_cb_reglages` pour Réglages, `Icons.Filled.Lock` pour le parental).
3. Télémétrie : conserver l'appel `PhoneConnect.feature("settings")` tel qu'il est fait par `SettingsScreen` (rien à ajouter) ; ne pas créer d'id.
4. Vérifier que `PhoneConnect.screens.enter(...)` (`:88`) reçoit toujours l'id du bon onglet au premier affichage (il suit `tab`).

## Éditions (forme mécanique Haiku : ancre « avant » unique → « après »)
Fichier : `android/sender/src/main/kotlin/castbridge/sender/MainActivity.kt`.

E1 — avant (ligne 83) :
```kotlin
        var tab by rememberSaveable { mutableStateOf(0) }
```
après :
```kotlin
        val prefs = remember { getSharedPreferences("castbridge_home", android.content.Context.MODE_PRIVATE) }
        var tab by rememberSaveable { mutableStateOf(prefs.getInt("last_tab", 1).let { if (it in 0..5 && it != 5) it else 1 }) }
```
E2 — avant (lignes 89-92) :
```kotlin
        fun select(i: Int) {
            if (i != tab) listOf("cast", null, "games", "player", "learn", null)[i]?.let { PhoneConnect.feature(it, "tile") }
            tab = i
        }
```
après :
```kotlin
        fun select(i: Int) {
            if (i != tab) listOf("cast", null, "games", "player", "learn", null)[i]?.let { PhoneConnect.feature(it, "tile") }
            tab = i
            prefs.edit().putInt("last_tab", if (i == 5) 1 else i).apply()
        }
```
E3 — avant (lignes 106-111) :
```kotlin
                        actions = {
                            TextButton({ ActivateTvActivity.open(this@MainActivity) }) { Text("Activer la TV") }
                            TextButton({ RentalDeliveryActivity.open(this@MainActivity) }) { Text("Locations") }
                            IconButton({ ParentalActivity.open(this@MainActivity) }) { Icon(Icons.Filled.Lock, "Contrôle parental") }
                            IconButton({ settings = true }) { CbIcon(R.drawable.ic_cb_reglages, "Réglages") }
                        },
```
après :
```kotlin
                        actions = {
                            var more by remember { mutableStateOf(false) }
                            IconButton({ more = true }) { Icon(Icons.Filled.MoreVert, "Plus") }
                            DropdownMenu(more, { more = false }) {
                                DropdownMenuItem(text = { Text("Activer la TV") }, onClick = { more = false; ActivateTvActivity.open(this@MainActivity) })
                                DropdownMenuItem(text = { Text("Locations sur la TV") }, onClick = { more = false; RentalDeliveryActivity.open(this@MainActivity) })
                                DropdownMenuItem(text = { Text("Contrôle parental") }, leadingIcon = { Icon(Icons.Filled.Lock, null) }, onClick = { more = false; ParentalActivity.open(this@MainActivity) })
                                DropdownMenuItem(text = { Text("Réglages") }, leadingIcon = { CbIcon(R.drawable.ic_cb_reglages, null) }, onClick = { more = false; settings = true })
                            }
                        },
```
Commandes, dans l'ordre : les quatre `grep` ci-dessous (résultats attendus en commentaire) ; puis `tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin` si le SDK Android est présent (sinon : écrire « compilation non vérifiée (SDK absent) » dans le rapport). Règle d'arrêt : un `grep` non conforme ⇒ `STATUT: ÉCHEC` avec la ligne fautive.

## Critères d'acceptation
```sh
cd android && grep -c 'TextButton({ ActivateTvActivity.open' sender/src/main/kotlin/castbridge/sender/MainActivity.kt   # 0
grep -c '"last_tab"' sender/src/main/kotlin/castbridge/sender/MainActivity.kt                                          # ≥ 2
grep -c 'DropdownMenuItem' sender/src/main/kotlin/castbridge/sender/MainActivity.kt                                     # 4
grep -c 'detectTapGestures(onTap = { seq.tap() }' sender/src/main/kotlin/castbridge/sender/MainActivity.kt              # 1 (inchangé)
```
Observable (émulateur ou téléphone, sans télémétrie) : lancer l'app ⇒ onglet « CastBridge TV » ; ouvrir « Jeux », tuer l'app, relancer ⇒ « Jeux » ; ouvrir « Parental », tuer, relancer ⇒ « CastBridge TV » ; la barre du haut montre logo + ⋮ ; les 4 entrées du menu ouvrent les mêmes écrans qu'avant ; 7 touches + appui long sur le logo ouvrent toujours la console (build propriétaire).

## Cas limites
Première installation (pas de préférence) ⇒ 1. Préférence hors bornes (0..5) ⇒ 1. L'écran de consentement (`Gate()`, `:67-78`) et la mise à jour obligatoire passent avant, inchangés.

## À ne pas faire
Ne pas toucher aux onglets eux-mêmes (ils changent en w11-07). Ne pas retirer la `ScrollableTabRow`. Ne pas modifier `CastMiniBar`. Pas de nouvelle chaîne en anglais.

## Rapport
`STATUT`, deux captures (barre du haut, menu ⋮), résultat des quatre `grep`, et la liste des écrans ouverts par chaque entrée du menu.
