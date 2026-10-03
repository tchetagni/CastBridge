# Conception W20 — Quiz en ligne : trois périmètres de jeu, un signe « Partie sûre », une passerelle temps réel sur bridge.sti-cm.com

> Document de conception (Fable, architecte, 2026-10-03). **Aucun code n'est modifié par ce document.** Exécution par les cahiers `docs/agent-briefs/sonnet-w20-NN-*.md` (index `SONNET-WAVE20-INDEX.md`), sur ordre du coordinateur, dans les règles du gel W15 (cœur pur d'abord, câblage mince après, audit Opus obligatoire sur la passerelle, les droits et la sécurité). Branche de référence : `integration/agents` (HEAD `145c9e2d`, téléphone 1.2.40 code 70, TV 0.14.27 code 80). Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = tests cœur, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `B/` = `backend/src/main/java/castbridge/server/`. Tous les fichiers cités ont été lus le 2026-10-03 ; les chiffres marqués « estimé » n'ont pas été mesurés ; **les faits sur le nginx et le pare-feu de production ne sont pas dans le dépôt** (§ 9).
>
> **Exigence du propriétaire (2026-10-03, verbatim)** : « On doit pouvoir être capable de jouer sur internet quand la TV est connectée avec un signe que la partie est sûre : TV seule, en réseau local, depuis internet. Le serveur doit avoir un port approprié pour la mise en œuvre de parties en ligne sur internet. C'est un enjeu commercial fort de pouvoir jouer sur internet. »
>
> **Lecture appliquée** : le Quiz garde ses trois façons de jouer d'aujourd'hui (seul à la télécommande, Millionnaire avec le public, Duel), mais chaque partie a désormais un **périmètre** affiché en permanence sur la TV et sur chaque téléphone : **TV seule**, **Réseau local**, **Internet**. Le périmètre dit *qui peut entrer* et *ce qui sort de la maison* ; la couleur `TvSignal` dit si la promesse du périmètre est tenue. Le hors-ligne reste le cas des 80 % et ne doit **jamais** se dégrader : l'Internet est une **extension explicite** d'une partie, jamais un prérequis. Jetons virtuels seulement (juridique reporté au 2026-12-31).

## 0. En vingt lignes

1. **Trois périmètres, un signe.** `PlayScope` ∈ {`TV_ONLY` « TV seule » ▣, `LAN` « Réseau local » ⌂, `INTERNET` « Internet » ◎} et `SafetySign.of(facts)` (pur, `C/quiz/online/`) rendent `(scope, SignalLevel, texte, action)` ; les deux applications dessinent le **même** résultat (test `SafetySignAgreementTest`). Vert = la promesse du périmètre est tenue ; orange = tenue mais dégradée (Internet perdu, joueur distant en reprise, invité QR dans le groupe Wi-Fi Direct) ; rouge = la promesse n'est pas tenue (TLS invalide, serveur injoignable au milieu d'une partie Internet, profil enfant qui tente Internet) ; noir = périmètre inactif par choix (Internet désactivé, mode enfant). § 1.
2. **Changer de périmètre est un geste** : « Ouvrir sur Internet » (télécommande, confirmation, 1 écran) et « Fermer Internet » (réversible : les joueurs distants deviennent spectateurs 30 s puis sortent). Une partie qui **perd** Internet descend en orange, attend 60 s, puis **continue en local** si le périmètre d'origine le permet (§ 1.5, S-REPRISE).
3. **Code de salle par périmètre** : 4 chiffres en local (inchangé), **code Internet de 8 caractères sur 32 symboles = 40 bits**, TTL 2 h, 10 essais faux / IP / 5 min, 50 essais faux sur une salle ⇒ nouveau code ; lien `https://bridge.sti-cm.com/play/j/<code>` et lien d'application `castbridge://play?code=`. QR sur la TV. § 1.6.
4. **Le port approprié est le 443** : `wss://bridge.sti-cm.com/play/ws` derrière le nginx partagé existant (WebSocket avec repli SSE + long-poll sur le même chemin). Un port dédié (ex. 7443) est **bloqué par la plupart des réseaux mobiles, scolaires et d'entreprise** et n'apporte rien au joueur ; il n'est gardé que comme **écouteur secondaire de diagnostic, fermé par défaut** (D-W20-2). Nom `play.bridge.sti-cm.com` réservé pour la montée en charge, pas nécessaire au départ. § 2.1.
5. **Un petit service dédié `castbridge-play`** (Kotlin/JVM, Spring Boot, module Gradle `server-play/` qui dépend de `:core` : **le même `QuizRoom`, `QuizDuel`, `QuizGame`, `QuizBank` qu'en local**), conteneur séparé sur `127.0.0.1:7091`, schéma MySQL `castbridge_play` avec son propre utilisateur, **aucun secret d'activation ni de licence** : il ne vérifie que des **tickets** signés par l'API principale (clé publique seule). § 2.2, § 4.5.
6. **Autorité** : en local, `LocalAuthority` (= `QuizRoom` sur la TV, inchangé) ; sur Internet, **salle autoritaire côté serveur** (`ServerAuthority`) : le serveur tire les questions dans les lots publiés **et réservés** (si l'hôte y a droit), tient l'horloge, note, ne publie la bonne réponse qu'après la clôture ; la TV est l'écran et la télécommande de l'hôte, et le **relais** de ses joueurs locaux (une seule connexion sortante, fonctionne derrière CGNAT). Le relais pur « TV autoritaire » est **écarté** (triche par l'hôte, NAT, perte des parties quand la TV tombe). § 2.4.
7. **Modèle commercial** : créer une salle Internet = TV de **production** (ou location valide) ; les **30 % réservées** ne sont servies que dans les salles dont l'hôte y a droit (ticket) ; rejoindre est **gratuit** pour tout téléphone (app ou page web) ; TV d'essai : salles privées, questions libres seulement, 3 parties Internet par jour (D-W20-5). Salons publics, classements, tournois : questions libres, jetons virtuels, **aucun lot ni prix** avant le 2026-12-31. § 2.5.
8. **Équité et latence** (Afrique mobile 150-600 ms) : le temps de réponse est `arrivée serveur − ouverture serveur − min(RTT/2, 400 ms)` avec un RTT mesuré par le serveur ; les joueurs locaux relayés par la TV portent en plus l'horodatage monotone de la TV, **borné** par le RTT de la TV ; fenêtre de 20 s + grâce `min(RTT, 1 s)` ; une seule réponse par joueur ; **jamais** d'horloge de téléphone. § 2.6.
9. **Capacité d'une VM modeste** (7,8 Go partagés, conteneur 384 Mo) : ≈ 60-120 Ko par salle, ≈ 40-60 Ko par connexion WebSocket ⇒ plafonds de départ **400 salles / 3 000 connexions / 3 000 messages/s**, soit ≈ 2 500-3 000 joueurs simultanés ; au-delà, second conteneur derrière le même nginx avec affinité de salle par chemin (§ 2.3). Le nginx partagé doit avoir `worker_connections` ≥ 4 096 (**fait à vérifier**, § 9).
10. **Sécurité** : TLS 1.2+ (nginx), vérification d'`Origin`, tickets Ed25519 à durée courte, codes à 40 bits avec verrouillage, jetons de joueur 128 bits, validation stricte de chaque message (taille ≤ 2 Ko, schéma), limites par IP/appareil/salle, pare-feu inchangé (seul 443 est exposé), journaux sans secret ni pseudonyme en clair ; le PIN de `docs/ADMIN.md` n'est **jamais** utilisé sur Internet. § 4.
11. **Données personnelles** : pseudonyme (modéré), empreinte d'appareil hachée, pays déduit de l'IP (GeoIP existant) ; IP brute ≤ 7 jours dans les journaux ; transcription de salle 24 h ; classement 12 mois ; signalements 90 jours ; profil enfant : Internet **noir par défaut**, ouvrable par le code parental pour les salles privées seulement, jamais les salons publics pour -12. § 3.
12. **Effort** : 13 cahiers (≈ 31 agent·jours, ≈ 29 $), dont 9 pendant le gel (cœur pur, service, tests, docs, exploitation) et 3 après (câblage TV, téléphone, page web), 1 conditionnel (tournois/écoles) ; **4 audits Opus obligatoires** (passerelle, tickets/droits, anti-triche/limites, câblage TV). § 6.
13. **Honnêteté** : rien n'a été mesuré ; le nginx, le certificat, `ufw` et le pare-feu du fournisseur ne sont **pas dans le dépôt** (deux propositions non appliquées dans `backend/README.md:147-190`) ; `TrialPolicy` ferme aujourd'hui tout le Quiz sur une clé d'essai alors que `QuizEdition.TRIAL_OPEN` décrit un contenu d'essai jouable : contradiction à trancher (D-W20-5) ; le téléphone n'a jamais été essayé sur un vrai appareil pour le Quiz (`docs/QUIZ.md` § 9).

## 1. Les trois périmètres et le signe « Partie sûre »

### 1.1 Définitions

| Périmètre | Icône · étiquette (français) | Qui peut entrer | Ce qui sort de la maison | Ce que le serveur voit | Anti-triche | Données gardées |
|---|---|---|---|---|---|---|
| `TV_ONLY` | ▣ **TV seule** | personne : la télécommande seule (solo, Millionnaire sans public) | **rien** ; hors ligne OK | rien | sans objet | scores sur la TV (existant `HighScores`) |
| `LAN` | ⌂ **Réseau local** | téléphones du **même Wi-Fi** ou du **groupe Wi-Fi Direct** de la TV, code 4 chiffres ou QR ; ≤ 8 joueurs | **rien** : le serveur de la TV (port 8765, `/quiz/*`) ; aucune requête Internet pendant la partie | rien (télémétrie `quiz_game` plus tard, si consentie, comme aujourd'hui) | horloge de la TV, une réponse par joueur, bonne réponse jamais envoyée avant clôture (existant, testé) | historique 300 parties, jetons virtuels, scores : **locaux** (`QuizMerge`) |
| `INTERNET` | ◎ **Internet** | joueurs **distants** par le serveur (code 8 car., lien, QR) **et** les joueurs locaux de la TV (relayés) ; ≤ 8 joueurs par table, ≤ 8 tables par salle (§ 2.4) | état de la salle, pseudonymes, réponses, temps de réponse, ticket de la TV (identifiant d'appareil, édition) ; **jamais** le PIN, jamais un fichier, jamais la banque locale | tout ce qui précède, pays par IP, IP ≤ 7 j | **serveur** : horloge, tirage, notation, secret des questions, limites, détection de robots | § 3.2 |

Règles : (1) le périmètre est **choisi avant** la salle d'attente et affiché **toujours** (bandeau TV, en-tête de la page de jeu et de l'écran Quiz du téléphone) ; (2) `TV_ONLY` et `LAN` **ne font aucun appel réseau sortant** (le test `QuizLotsTest.theStarterIsWithinBudgetAndAFullGameWorksOnItAlone` avec `ProxySelector` espion est étendu aux deux périmètres) ; (3) `INTERNET` n'est proposé que si la TV **voit** Internet (`TvFacts.internet`) **et** possède un ticket valide (§ 2.5) **et** le profil actif y a droit (§ 3.4) ; sinon la tuile dit pourquoi (jamais grisée en silence).

### 1.2 Ce qui rend une partie « sûre » (la promesse par périmètre)

| Promesse | TV seule | Réseau local | Internet |
|---|---|---|---|
| Transport chiffré | sans objet | non chiffré **mais local** (limite connue `BT-PLUG-AND-PLAY.md:130` ; le jeton de joueur ne donne accès qu'à `/quiz/*`) | **TLS 1.2+ obligatoire** (`wss://`, HSTS) ; un certificat invalide ⇒ **rouge**, pas de salle |
| Données personnelles | aucune | pseudonyme, identifiant de page (`dev:`) sur la TV | pseudonyme **modéré** (§ 3.3), empreinte d'appareil hachée, pays ; **rien d'autre**, pas de compte, pas d'adresse courriel |
| Vie de la salle | fermée en quittant | 10 min d'inactivité (existant) | **expire** : 2 h après création ou 10 min sans activité ; code à usage borné |
| Qui commande | télécommande | télécommande (hôte) | hôte = la TV (ou le téléphone hôte, D-W20-6) ; **expulser** et **rendre muet** (pseudonyme masqué) un joueur ; expulsé = jeton révoqué, même appareil refusé 1 h dans la salle |
| Signalement | — | « Signaler une erreur » (question, existant) | + « Signaler ce joueur » (pseudonyme) : file de modération, § 3.5 |
| Mineurs | — | mode enfant inchangé (`ParentalEngine.check(GAMES)`) | profil enfant : Internet **noir** par défaut ; code parental pour ouvrir « salles privées seulement » ; salons publics **jamais** pour -12 ; aucune zone de texte libre à part le pseudonyme |
| Argent | jetons virtuels | jetons virtuels | jetons virtuels **seulement**, et en salle privée seulement ; aucun lot, aucun classement monétisé (§ 2.5) |

### 1.3 Le signe : `SafetySign` (pur, même sémantique sur les deux applications)

```
  ┌────────────────────────────────────────────────────────────────┐
  │  ◎ Internet · ● Partie sûre · 5 joueurs (2 ici, 3 à distance)   │   ← bandeau TV (haut), 1 ligne, D-pad : détail
  └────────────────────────────────────────────────────────────────┘
  téléphone (page /play ou onglet Quiz) : même pastille, même mot, même texte, sous le code de salle
```

`C/quiz/online/SafetySign.kt` : `data class SafetyFacts(scope, tls: TlsState, serverLink: LinkState3, lanLink, remotePlayers, localPlayers, guestsViaQr, kidProfile, internetAllowedByParent, ticketValid, clockDoubt)` → `SafetyView(scope, level: SignalLevel, word, text, action, detail: List<String>)`. Table figée (une ligne par cause ; la **première** cause rencontrée est dite, gravité `SignalLevel.severity`) :

| Périmètre | Niveau | Condition | Texte | Action |
|---|---|---|---|---|
| TV seule | ● VERT | toujours | « TV seule · personne d'autre ne peut entrer » | — |
| Réseau local | ● VERT | salle ouverte, 0 invité QR | « Réseau local · rien ne sort de la maison » | — |
| Réseau local | ▲ ORANGE | ≥ 1 invité entré par le QR `WIFI:` du groupe (W18 D-W18-8) | « Réseau local · 1 invité dans le Wi-Fi de la TV » | « Changer le mot de passe Wi-Fi Direct à la fin » |
| Réseau local | ▲ ORANGE | un téléphone déconnecté > 20 s pendant une question | « Réseau local · Amina ne répond plus » | — |
| Réseau local | ■ ROUGE | la TV n'a plus aucune adresse (LAN et groupe perdus) pendant une partie | « Réseau local perdu : les téléphones ne peuvent plus répondre » | « Télécommande : continuer seul, ou attendre » |
| Internet | ● VERT | TLS OK, serveur joint, ticket valide, tous les joueurs présents | « Internet · partie sûre · chiffrée, pseudonymes seulement » | — |
| Internet | ▲ ORANGE | serveur perdu depuis < 60 s, ou ≥ 1 joueur distant en reprise, ou RTT > 1 500 ms | « Internet · liaison en reprise (12 s) » / « Internet · Koffi en reprise » / « Internet · réseau lent » | — |
| Internet | ■ ROUGE | TLS invalide, ticket refusé, serveur perdu ≥ 60 s | « Internet : impossible · certificat non valide » / « Internet perdu : la partie continue en local » (après le repli § 1.5) | « Fermer Internet » / « Réessayer » |
| Internet | ○ NOIR | Internet désactivé par le propriétaire, profil enfant sans autorisation parentale, TV sans Internet | « Internet : désactivé » / « Internet : réservé aux adultes (code parental) » / « Internet : la TV n'est pas connectée » | « Contrôle parental » / « Connexion & réglages » |

Invariants testés (`SafetySignTest`, `SafetySignAgreementTest`) : (1) `TV_ONLY` n'est jamais autre chose que vert ; (2) aucun périmètre n'est vert si une connexion sortante vers autre chose que le serveur `play` existe ; (3) le mot et la forme (`SignalLevel.shape`) accompagnent toujours la couleur ; (4) la page web `/play` et l'app téléphone affichent `SafetyView` **envoyé par l'autorité** (le serveur en Internet, la TV en local), jamais une couleur calculée côté client ; (5) la TV sans Internet reste **noire**, jamais rouge (règle `TvSignal`).

### 1.4 Changer de périmètre : un geste visible et réversible

```
  Salle d'attente (LAN, 3 téléphones)              « Ouvrir sur Internet » (télécommande › OK)
  ┌──────────────────────────────┐                 ┌─────────────────────────────────────────────┐
  │ ⌂ Réseau local · ● sûre      │  ──────────►    │ ◎ Ouvrir cette salle sur Internet ?          │
  │ code 4821 · 3 joueurs        │                 │ • des joueurs distants pourront entrer avec  │
  │ [Commencer] [Ouvrir sur      │                 │   le code Internet affiché                   │
  │  Internet] [Réglages]        │                 │ • pseudonymes et réponses passent par notre  │
  └──────────────────────────────┘                 │   serveur (chiffré) ; rien d'autre           │
                                                   │ • vous pouvez fermer Internet à tout moment  │
                                                   │        [Ouvrir]   [Annuler]  (Annuler préféré)│
                                                   └─────────────────────────────────────────────┘
          │ Ouvrir ⇒ ticket (≤ 2 s) ⇒ salle serveur créée ⇒ les 3 joueurs locaux sont inscrits à la table « TV Salon »
          ▼
  ┌──────────────────────────────────────────────────────────────┐
  │ ◎ Internet · ● sûre · code  K7M2-QX4T · bridge.sti-cm.com/play │   [Fermer Internet] toujours visible
  │ 3 ici · 0 à distance · QR                                      │
  └──────────────────────────────────────────────────────────────┘
```

Règles : (1) le changement n'est possible qu'en **salle d'attente** (LOBBY) ou entre deux parties (FINISHED), jamais pendant une question ; (2) « Fermer Internet » : les joueurs distants voient « L'hôte a fermé la partie Internet » 30 s (spectateurs, scores figés) puis la page revient à l'accueil ; la salle redevient `LAN` avec les mêmes joueurs locaux et le même code 4 chiffres ; (3) le passage LAN → Internet **n'envoie jamais** l'historique, les scores ni les jetons locaux : la table serveur part de zéro et c'est **dit** (« Nouvelle partie : les scores de la maison restent sur la TV ») ; (4) le téléphone de confiance peut **demander** l'ouverture (`POST /api/quiz/scope` avec le PIN, comme `POST /api/quiz/open` aujourd'hui) mais la **confirmation reste sur la TV** (télécommande) : le PIN prouve la présence, il n'autorise pas Internet seul (D-W20-7).

### 1.5 Perte d'Internet pendant une partie Internet (S-REPRISE appliqué au jeu)

| t | TV (hôte) | Joueur distant (page/app) | Serveur |
|---|---|---|---|
| 0 | WebSocket perdu ⇒ **orange** « Internet · liaison en reprise (0 s) » ; la question en cours reste affichée, **pas de nouvelle question** (le serveur tire) | si lui seul est tombé : « reprise… », rejoint avec son jeton (§ 2.7) | la salle vit ; la TV est « en reprise » ; la question en cours **se clôt à l'heure du serveur** |
| 0-60 s | re-connexion : immédiate ×1 puis 2, 4, 8, 15, 30 s (`LinkMachine`) ; à la reprise : `resume(roomId, tvToken, lastSeq)` ⇒ rattrapage des évènements manqués | voit « L'hôte est en reprise » si la TV est l'hôte ; les joueurs distants continuent de répondre | si **tous** les joueurs locaux sont derrière la TV, le serveur **met la table en pause** (question suspendue ≤ 60 s) ; sinon il continue |
| 60 s | **rouge** « Internet perdu : la partie continue en local » : la TV **repasse en `LAN`** avec ses joueurs locaux et une **nouvelle partie locale** (même réglages, scores locaux repartent de 0, c'est dit) ; la salle serveur reste joignable 10 min si Internet revient (« Reprendre la partie Internet » proposé **une fois**, en salle d'attente seulement) | « L'hôte a quitté : la partie se termine » ; classement partiel affiché avec la mention « partie interrompue » (non comptée au classement) | la table de la TV est marquée `ABANDONED(HOST_LOST)` ; les autres tables de la salle continuent |
| après | si Internet revient en < 10 min : proposition de reprise ; sinon la salle expire | — | purge à 10 min |

Pourquoi « nouvelle partie locale » et pas « même partie » : le serveur détient les questions non encore posées et la bonne réponse ; la TV ne peut pas continuer la même partie sans recevoir la banque (secret) ou sans inventer un score. Mieux vaut un repli honnête qu'une continuité fausse. Si le périmètre d'origine était `TV_ONLY` (candidat seul à la télécommande ouvert sur Internet pour le public), le repli est une partie solo.

### 1.6 Code de salle et entrée par périmètre

| | Réseau local (existant) | Internet (nouveau) |
|---|---|---|
| Code | 4 chiffres (`QuizRoom.code`), 10⁴ | **8 caractères, alphabet Crockford (32 symboles, sans I/L/O/U) = 40 bits**, affiché `K7M2-QX4T` |
| Durée | vie de la salle (≤ 10 min d'inactivité) | **2 h** maximum (vie de la salle), renouvelé (« Nouveau code ») par l'hôte ou automatiquement après 50 essais faux |
| Essais faux | 10 / IP / 5 min (existant `MAX_BAD_CODES`) | 10 / IP / 5 min, 100 / IP / jour, **50 par salle** ⇒ code renouvelé et hôte prévenu ; au-delà de 1 000 / h sur le service ⇒ alerte (§ 2.8) |
| Probabilité de deviner | sans objet (réseau local) | 50 essais sur 2⁴⁰ avec ≤ 400 salles vivantes : ≈ 2 × 10⁻⁸ par salle et par heure |
| Entrée | QR `http://<ip TV>:8765/quiz?code=` ; app : code récupéré avec le PIN | QR + lien `https://bridge.sti-cm.com/play/j/K7M2QX4T` (page web, sans app) ; **lien d'application** `castbridge://play?code=K7M2QX4T` (app installée : l'app s'ouvre sur l'onglet Quiz, tout de suite dans la salle) ; saisie manuelle du code sur `/play` |
| Jeton de joueur | 128 bits (existant) | 128 bits, lié à `(roomId, playerId, deviceHash)`, expire avec la salle ; **jamais dans l'URL** (dans un en-tête ou le premier message WebSocket ; `sessionStorage` côté page) |

## 2. Le serveur : la passerelle de jeu en ligne

### 2.1 Le port approprié : évaluation honnête et recommandation

| Option | Passe les réseaux (mobile CM/CEMAC, écoles, entreprises, hôtels) | TLS | Ops | Diagnostic | Verdict |
|---|---|---|---|---|---|
| **A. 443 + chemin `/play/…` sur `bridge.sti-cm.com`** (nginx partagé existant) | **≈ 99 %** : c'est le port du web ; WebSocket sur 443 traverse les proxys mobiles et CGNAT ; blocage DPI de WebSocket rare (repli SSE/long-poll couvre le reste) | certificat **existant** (certbot), rien à émettre | **un bloc `location`** dans le site existant (§ 2.9) ; même fichier que la future `location` des médias signés (`SERVER-STRATEGY.md:14`) | journaux nginx partagés ; pas de séparation de charge | **RECOMMANDÉ (D-W20-1)** |
| B. 443 + nom `play.bridge.sti-cm.com` | idem A | nouveau certificat (certbot, DNS à créer) | un `server` de plus ; permet plus tard de pointer vers une **autre VM** sans toucher aux clients | séparation de journaux et de limites | **réservé** : prévoir le nom DNS maintenant (coût nul), basculer quand la charge le justifie (§ 2.3) |
| C. Port haut dédié (ex. 7443, TLS direct du service, sans nginx) | **mauvais** : ports non standard filtrés sur beaucoup de réseaux mobiles d'entreprise, d'école, d'hôtel et par certains opérateurs ; échec « muet » pour l'usager | certificat à distribuer au service (copie du certbot : couplage) | `ufw allow 7443`, pare-feu fournisseur, renouvellement de certificat à câbler | utile : mesure sans nginx, test de charge, contournement d'un nginx saturé | **écouteur secondaire, fermé par défaut** (D-W20-2) ; jamais le chemin des joueurs |
| D. 80 clair | passe partout | **aucun** : interdit (pseudonymes, jetons) | — | — | **rejeté** |

Décision recommandée : **le port approprié est le 443**, chemin `/play/` ; le client essaie dans l'ordre `wss://…/play/ws` → SSE `https://…/play/events` + `POST /play/act` → long-poll `/play/state` (25 s), **sur le même hôte et le même port** : un seul trou dans n'importe quel pare-feu. Le port dédié n'existe que pour l'exploitant (bloc `server { listen 7443 ssl; }` **ou** écouteur TLS du service), ouvert à la demande et refermé.

### 2.2 Architecture

```
  Internet (joueurs : page /play, app CastBridge ; TV CastBridge-TV : connexion SORTANTE seulement, derrière box/CGNAT)
      │ 443 TLS (certbot)                                                 ▲ aucune connexion entrante vers la maison
      ▼                                                                   │
  ┌──────────────────── nginx partagé (infra-nginx, hors dépôt) ───────────────────────────────┐
  │ server bridge.sti-cm.com                                                                     │
  │   location /api/ /admin/ /dl/ …  → 127.0.0.1:7090  (castbridge-api, existant)                │
  │   location /play/                → 127.0.0.1:7091  (castbridge-play, NOUVEAU) + Upgrade/WS   │
  │   [location /play/ws] proxy_read_timeout 75s ; ping toutes les 25 s                          │
  └──────────────────────────────────────────────────────────────────────────────────────────────┘
      │ 7090                                        │ 7091
      ▼                                             ▼
  ┌─────────────────────────┐   POST /api/v1/play/ticket   ┌───────────────────────────────────────┐
  │ castbridge-api (Java)   │ ◄───(Bearer deviceToken)──── │ (la TV demande le ticket à l'API)      │
  │ licences, activation,   │                              │                                        │
  │ lots, télémétrie, admin │   clé privée play-ticket.key │ castbridge-play (Kotlin, :core)        │
  │ secrets : /run/secrets  │ ───► ticket Ed25519 ───────► │ vérifie avec la CLÉ PUBLIQUE seulement │
  └───────────┬─────────────┘                              │ salles en mémoire ; QuizRoom/QuizDuel  │
              │ MySQL castbridge (schéma castbridge)        │ lots quiz (volume RO) ; schéma         │
              ▼                                             │ castbridge_play (user dédié)           │
  ┌─────────────────────────┐                              │ mem 384 Mo, read_only, cap_drop ALL     │
  │ castbridge-db (MySQL)   │ ◄────────────────────────────┘                                        │
  └─────────────────────────┘      réseau castbridge-internal                                       │
                                                                                                    │
  Isolation : castbridge-play n'a NI admin-token, NI clé de signature, NI clé de licence, NI accès au schéma castbridge.
```

**Pourquoi un service dédié et non le Spring Boot existant** : (1) `castbridge-api` est en **Java/Maven** et ne connaît pas `:core` ; réécrire `QuizRoom`/`QuizDuel` en Java = deux logiques de jeu à tenir d'accord (contraire au but « même cœur »). Un module **Gradle Kotlin/JVM `server-play/`** inclus dans `android/settings.gradle.kts` (précédent : `:activation-desktop` dans `tools/`) dépend de `:core` et embarque Spring Boot (`spring-boot-starter-websocket`, `-web`, `-jdbc`, Flyway) ; (2) isolation du **rayon d'explosion** : le service exposé au public le plus large (joueurs anonymes, WebSocket) ne tient aucun secret d'activation (§ 4.5) ; (3) `castbridge-api` a 40 threads Tomcat et 512 Mo : 3 000 WebSockets y étoufferaient les routes de licence ; (4) redémarrage et montée de version indépendants (les salles sont volatiles, l'API ne l'est pas). Coût : un `Dockerfile`, un service compose, un `location`.

### 2.3 Capacité (VM : 7,8 Go partagés, ≈ 4,6 Go disponibles, nombre de vCPU **inconnu**)

| Grandeur | Estimation (non mesurée) | Base |
|---|---|---|
| Mémoire par salle (table de 8, 15 questions chargées, état, 50 derniers évènements) | **60-120 Ko** | `QuizRoom` + `QuizGame`/`QuizDuel` + 15 `Question` (≈ 600 o chacune) + ring d'évènements 50 × 1 Ko |
| Mémoire par connexion WebSocket (Tomcat NIO, tampons 8 Ko × 2, session, file de sortie bornée 32 Ko) | **40-60 Ko** | Tomcat WS par défaut |
| Conteneur 384 Mo, JVM `MaxRAMPercentage=70` ≈ 270 Mo de tas, 100 Mo réservés au code et aux lots en mémoire (index seulement, questions lues à la demande depuis le volume : ≈ 20 Mo pour 93 lots indexés) | **≈ 150 Mo** pour salles + connexions | |
| Plafonds de départ | **400 salles**, **3 000 connexions**, 3 000 messages/s | 400 × 100 Ko + 3 000 × 50 Ko ≈ 190 Mo : marge faible ⇒ plafonds appliqués **avant** la mémoire |
| Messages par salle (Duel, 8 joueurs, question de 20 s) | 8 réponses entrantes + ≈ 12 diffusions × 8 = ≈ 100 messages / question ⇒ **≈ 5 msg/s/salle** ; 400 salles ⇒ 2 000 msg/s × ≈ 800 o ⇒ **1,6 Mo/s** | diffusion d'état **différentielle** (`seq`, champs changés) et **coalescée** (≤ 10 diffusions/s par salle) |
| CPU | tirage, notation, JSON : < 0,1 ms par message ⇒ 2 000 msg/s ≈ 20 % d'un cœur | estimé |
| Joueurs simultanés ≈ 2 500-3 000 ; **parties par jour** (≈ 8 min par partie, 60 % d'occupation) ≈ **40 000 joueurs-parties/jour** | | |

**Montée en charge** (plus tard, aucune décision maintenant) : (1) 2 à 4 conteneurs `castbridge-play-N` derrière nginx avec **affinité par salle** (`/play/r/<roomId>/…` ⇒ `hash $room consistent`) : les salles ne se parlent pas, aucun bus nécessaire ; (2) classements et modération dans MySQL (partagés) ; (3) au-delà, `play.bridge.sti-cm.com` sur une VM dédiée. Ce qui bloque avant tout : `worker_connections` du nginx partagé (défaut Debian **768**, insuffisant ; **à lire sur le serveur**, § 9) et le nombre de descripteurs du conteneur (`ulimits nofile 16384`).

### 2.4 Modèle d'autorité

| Critère | **Salle autoritaire côté serveur** (recommandé) | TV autoritaire, serveur relais |
|---|---|---|
| Triche par l'hôte | impossible sur l'horloge, le tirage et la notation | l'hôte (APK modifié, horloge) décide tout : classement public **invérifiable** |
| Secret des questions réservées | les questions ne quittent le serveur qu'une à la fois, à l'ouverture | la TV doit **avoir** les questions ⇒ une TV d'essai ou non titulaire ne peut pas héberger de niveau réservé ; les réservées de l'APK d'essai sont déjà en clair (`quiz-toutes-les-questions.md` § 6) |
| TV sans banque pour un niveau | **sans objet** : le serveur a les 93 lots + 25 paquets réservés | la TV héberge seulement ce qu'elle détient (« bientôt ») |
| NAT/CGNAT | TV = client sortant ; joueurs = clients sortants | idem (le relais résout le NAT) mais tout message fait **deux sauts** (joueur→serveur→TV→serveur→joueur : +1 RTT TV) |
| Perte de la TV | la table continue ou se termine proprement (§ 1.5) | la partie **meurt** avec la TV |
| Latence des joueurs locaux | +1 RTT TV (relais) : compensé § 2.6 | 0 pour les locaux, +2 RTT pour les distants |
| Réutilisation du cœur | **oui** : `QuizRoom` tourne **sur le serveur** (JVM) avec `clock` serveur et `QuizBank` serveur | oui, sur la TV |
| Repli hors ligne | nouvelle partie locale (§ 1.5) | continuité locale naturelle |

**Recommandation (D-W20-3) : serveur autoritaire.** La salle serveur **est** un `QuizRoom` (le même code, `autoTick` serveur, `clock = System::nanoTime` monotone, `random` sécurisé, `histories` par appareil côté serveur pour la règle des 300 parties **en ligne**, `wallet` virtuel serveur). La TV et les téléphones sont des **vues** : ils reçoivent `QuizRoom.view(token)` (déjà sérialisable, déjà sans la bonne réponse avant clôture : `QuizGame.toMap`, `duelMap`) et envoient `act(...)`. Le **relais** des joueurs locaux : la TV garde son `QuizHttp` local pour ses téléphones (page `/quiz`, SSE) et pousse leurs `act` au serveur avec `localRecvMono` ; le serveur en fait des joueurs ordinaires de la table marqués `via=tv`.

**Structure d'une salle Internet** : une *salle* (`roomId`, code Internet, réglages, hôte) contient 1 à 8 **tables** de ≤ 8 joueurs (une table = un `QuizRoom`) ; la table de l'hôte s'appelle « TV Salon » ; les joueurs distants remplissent la première table libre, ou choisissent une table (classe : « Table 3e A »). Toutes les tables d'une salle jouent **la même séquence de questions** (graine commune) : classement **par table** et **de salle** (§ 2.10). V1 : 1 table (8 joueurs) ; tables multiples en V2 (w20-13).

### 2.5 Modèle commercial et droits (sans argent réel)

| Acteur | Peut créer une salle Internet | Questions servies | Peut rejoindre | Classements publics | Notes |
|---|---|---|---|---|---|
| TV **production** (clé installée, `TvAccess.access` ≠ vide) sans location Quiz | oui, privée et publique | **libres** (70 %) | — | oui | ticket `edition=prod` |
| TV production **avec** location/achat d'un bouquet Quiz (`Right.Rental`/`Purchase` couvrant le lot) | oui | libres **+ réservées** des lots couverts, pour **tous** les joueurs de la salle (le droit est celui de l'hôte) | — | oui | ticket porte `scopes=[cm2, 3e, …]` |
| TV **essai** (`TvAccess.trial`) | **privée seulement**, 3 parties / jour, ≤ 8 joueurs | libres | — | non (parties non classées) | D-W20-5 ; aujourd'hui `TrialPolicy` **ferme tout le Quiz** en essai : à trancher |
| TV en **grâce** / horloge douteuse | comme production, 14 jours (grâce) ; horloge douteuse ⇒ **non** (le ticket exige une heure plausible) | selon droits | — | | `clockDoubt` ⇒ texte « Vérifiez l'heure de la TV » |
| TV **verrouillée / suspendue / révoquée** | non | — | peut rejoindre comme un téléphone ? **non** (la TV n'est pas un joueur) | | `GET /api/v1/revocations` déjà consommé par les tickets (l'API refuse) |
| Téléphone **avec app**, lié à une TV de confiance | V1 : **non** (rejoindre seulement) ; V2 : salle privée « sans TV » si une TV de confiance a été vue dans les 30 jours (D-W20-6) | — | oui, gratuit | oui | identifiant = `installId` haché |
| Téléphone **sans app** (page web) | non | — | oui, gratuit, pseudonyme | oui (empreinte navigateur faible : classement **par appareil enregistré** seulement pour l'app, D-W20-9) | |
| Enseignant (salle de classe) | TV production **ou** licence « école » (w20-13) | selon droits de l'école | élèves par lien/QR | classement **par classe**, pas public | après D-W20-10 |
| Salon public (matchmaking) | le **serveur** crée les tables ; pas d'hôte | **libres seulement** (jamais de réservée hors salle d'un titulaire) | tout le monde | oui | aucune mise |
| Salle **sponsorisée** / tournoi | opérateur (console admin) | libres + réservées payées par le sponsor | tout le monde | oui | **attend le juridique** (lots, prix, publicité) |

Ce qui **attend le 2026-12-31** (juridique, `project-legal-deferred`) : toute récompense en argent, bon d'achat, forfait data ou objet ; mises en argent ; tournois à droits d'entrée ; publicité ciblée ; collecte d'identité (KYC) ; classements liés à une récompense. Ce qui **ne l'attend pas** : jetons virtuels sans valeur (existant), classements par points, salles privées et publiques, salles de classe avec un enseignant, salles sponsorisées **sans prix** (nom du sponsor sur l'écran seulement, D-W20-10).

**Vérité des droits : le ticket.** `POST /api/v1/play/ticket` (API principale, `Authorization: Bearer <deviceToken>`, existant pour `/devices/*`) : l'API relit l'état du device (bloqué ? révoqué ?) et l'**activation** que la TV joint (`cbx1.…`, comme `POST /api/v1/tunnel/enroll` qui prend déjà une activation comme preuve, `REMOTE-TUNNEL-TV.md:16`), applique `TvGate.evaluate` **côté serveur** (le cœur est JVM : l'API Java ne l'a pas ⇒ le ticket est calculé par **`castbridge-play` lui-même** à partir d'une **attestation** minimale de l'API ? Non : la clé reste à l'API) — règle retenue : l'API principale **n'évalue pas** les droits (elle ne connaît pas `:core`) ; elle **atteste** `{deviceId, blocked, revoked, registeredAt, country}` dans le ticket ; la TV joint au ticket son **activation signée `cbx1`** et ses **lignes de location** ; `castbridge-play` (qui a `:core`) vérifie les signatures `cbx1` avec les **clés publiques** des émetteurs (déjà distribuées : `CASTBRIDGE_LICENSES_TRUSTED_KEYS`, format public) et applique `TvGate.evaluate`/`Entitlements` **avec son horloge** : aucune clé privée de licence ne sort de l'API. Durée du ticket : **10 min** ; renouvelé en tâche de fond pendant la salle ; révocations relues par `castbridge-play` toutes les 15 min (`GET /api/v1/revocations`, signé, public).

```
  ticket  = "cbp1." + b64url(JSON{deviceId, blocked:false, country:"CM", iat, exp:+600s, nonce}) + "." + b64url(Ed25519 sig par play-ticket.key)
  création de salle (TV → play) = { ticket, activation:"cbx1.…", rentals:["rental|…", …], wantScope:[…], settings }
  play vérifie : sig(ticket) + exp ; sig(cbx1) par clé émettrice de confiance ; TvGate.evaluate(now serveur) ⇒ TvAccess ; Entitlements.access(rentals) ⇒ lots couverts
```

**Questions réservées sur le serveur** : les 25 paquets réservés (`quiz-<lot>-reserved-pN-vN.quiz.zip`, non publiés, `quiz-toutes-les-questions.md` § « Conséquence pour le serveur » point 3) sont montés **en lecture seule** dans `castbridge-play` ; **jamais** servis en bloc par aucune route ; seule la **question courante** sort, à l'ouverture, **sans** `answer` ni `explanation` (ajoutés au message `reveal`). Le gel de `content/quiz/reserved-ids.json` (point 1) est un **prérequis** (w20-04 le lit).

### 2.6 Équité et latence

| Mesure | Règle | Pourquoi |
|---|---|---|
| Ouverture | `tOpen` = horloge monotone serveur à l'envoi de `question` ; durée 20 s (Duel) / 30-45 s (Millionnaire) sur l'horloge **serveur** | jamais l'horloge du téléphone (règle existante, étendue) |
| RTT | ping/pong WebSocket toutes les 5 s pendant une question, EWMA α = 0,3, borné [0, 2 000 ms] ; joueur relayé par la TV : `rtt = rttTV + rttLocal` où `rttLocal` est mesuré par la TV (SSE : ≈ 0-50 ms sur LAN/WD) | mesuré par l'autorité, pas déclaré |
| Temps de réponse | `elapsed = tArrive − tOpen − min(rtt/2, 400 ms)` ; pour un joueur relayé, la TV joint `localElapsedMono` (temps entre l'affichage et l'appui sur **sa** horloge) : le serveur prend `max(localElapsedMono, elapsedServeur − rttTV)` : la TV ne peut **qu'augmenter** le temps d'un joueur, jamais le réduire sous ce que le réseau prouve | compensation plafonnée : à 600 ms de RTT, 300 ms rendus ⇒ ≤ 7,5 points sur 1 000 (Duel) ; incitation à tricher sur le RTT nulle (le plafond est atteint vite) |
| Clôture | la question se ferme à `tOpen + durée` ; les réponses arrivées dans la **grâce** `min(rtt, 1 000 ms)` après la clôture sont acceptées **si** `elapsed` ≤ durée ; la révélation est envoyée **après** la grâce maximale de la table | un joueur à 600 ms n'est pas puni par la révélation anticipée |
| Fin anticipée | « tout le monde a répondu » ⇒ fermeture immédiate (existant) ; sur Internet, attendre `max rtt` de la table avant la révélation | un `reveal` qui croise une réponse en vol |
| Égalités | points puis `elapsed` ; égalité stricte ⇒ ex æquo (existant) | |
| Une réponse | première réponse définitive (existant `QuizDuel.Result.ALREADY_ANSWERED`) ; un `act` dupliqué (rejeu) = `SAME`, sans effet | idempotence S-4 |
| Spectateurs | reçoivent la vue `audience` (existant : rôle `audience` du Millionnaire) : question et répartition **après** clôture seulement ; ≤ 50 par table ; jamais la vue d'un joueur | |

### 2.7 Reconnexion et abandon (S-REPRISE)

| Scénario | Règle | Délai |
|---|---|---|
| Joueur distant perd le réseau | jeton gardé (`sessionStorage`/app) ; `resume(roomId, token, lastSeq)` ⇒ rattrapage ; sa place est gardée **jusqu'à la fin de la salle** ; une question manquée = 0 (comme aujourd'hui) | reprise ≤ 60 s sans pénalité ; après, il reprend à la question en cours |
| TV (hôte) perd le réseau | § 1.5 | 60 s puis repli local |
| Hôte quitte volontairement | « Terminer pour tout le monde » (classement final) **ou** « Laisser la table finir » : la table passe en **auto-hôte** (le serveur enchaîne les questions avec les minuteries par défaut ; plus de jokers « public/ami » = simulés) ; **migration** vers un joueur distant **non** en V1 (D-W20-8) | immédiat |
| Serveur redémarré (déploiement) | salles **volatiles** : les clients reçoivent `ROOM_GONE` et la raison « Mise à jour du serveur : la partie est terminée » ; déploiement en **drain** (refus de nouvelles salles, attente ≤ 10 min que les tables finissent, `SIGTERM` gracieux Spring `shutdown: graceful` 20 s) ; classements : seules les parties **terminées** comptent | ≤ 10 min |
| Doublon d'appareil | un `deviceHash` ne peut tenir qu'**un** siège par salle (le second rejoint comme spectateur) ; ≤ 3 salles simultanées par appareil | |

### 2.8 Contrôles d'abus (pures, testées, puis câblées en filtres)

| Limite | Valeur de départ | Où |
|---|---|---|
| Nouvelles connexions | 20 / IP / min ; 200 / IP / h ; 60 / s globales (file d'attente 429 `Retry-After`) | filtre HTTP avant l'upgrade ; nginx `limit_conn` en plus (§ 2.9) |
| Connexions ouvertes | 8 / IP (une famille derrière une box) ; 3 000 globales | compteur par IP (`X-Forwarded-For` **écrasé** par nginx, comme pour `RateLimitFilter`) |
| Messages | 10 / s / connexion, rafale 30 (mêmes valeurs que `QuizHttp` aujourd'hui) ; > 3 dépassements / min ⇒ fermeture 1008 | gestionnaire WS |
| Taille | message ≤ 2 Ko ; trame texte seulement ; pas de fragmentation > 4 Ko ; `maxTextMessageBufferSize = 8 Ko` | configuration WS |
| Codes faux | § 1.6 | |
| Salles | ≤ 400 vivantes ; ≤ 2 salles vivantes par TV ; ≤ 8 tables ; ≤ 8 joueurs / table ; ≤ 50 spectateurs | `RoomRegistry` |
| Slow-loris / inactivité | WS sans pong 40 s ⇒ fermé ; SSE sans lecture 60 s ⇒ fermé ; `connection-timeout 10 s` Tomcat ; nginx `proxy_read_timeout 75 s` avec ping 25 s | |
| Robots | heuristiques **pures** (`BotScore`) : réponses toujours < 300 ms sur 10 questions, exactitude > 95 % sur réservées, 100 % même intervalle, plusieurs sièges même IP et même `User-Agent` jouant à l'identique ⇒ score ; score élevé ⇒ **partie non classée** (jamais d'accusation à l'écran : « classement non pris en compte ») et marque pour la modération | `C/quiz/online/BotScore.kt` |
| Pseudonymes | § 3.3 | |
| Tickets | 20 demandes / device / h à l'API ; ticket rejeté ⇒ 1 essai puis « Internet : droit refusé (code) » | |
| TLS | 1.2 minimum, suites modernes (nginx, `ssl_protocols TLSv1.2 TLSv1.3`) ; HSTS déjà en place ? **inconnu** | |

### 2.9 Ce que l'exploitant (propriétaire) devra faire — rien n'est fait par les cahiers

| # | Où (serveur) | Quoi | Fichier / commande exacte (proposition ; le vrai chemin est à lire sur la machine) |
|---|---|---|---|
| O-1 | nginx partagé, site `bridge.sti-cm.com` (`/etc/nginx/sites-enabled/…` ou conf du conteneur `infra-nginx` : **inconnu**) | bloc `location /play/` vers `127.0.0.1:7091` avec upgrade WebSocket | ```map $http_upgrade $connection_upgrade { default upgrade; '' close; }``` (bloc `http`) ; ```location /play/ { proxy_pass http://127.0.0.1:7091; proxy_http_version 1.1; proxy_set_header Upgrade $http_upgrade; proxy_set_header Connection $connection_upgrade; proxy_set_header Host $host; proxy_set_header X-Forwarded-For $remote_addr; proxy_set_header X-Forwarded-Proto $scheme; proxy_read_timeout 75s; proxy_send_timeout 75s; proxy_buffering off; client_max_body_size 64k; limit_conn play_conn 16; }``` + `limit_conn_zone $binary_remote_addr zone=play_conn:10m;` (bloc `http`) ; **`X-Forwarded-For` écrasé, pas ajouté** (règle existante `README.md:149`) |
| O-2 | nginx | `worker_connections` ≥ 4 096 et `worker_rlimit_nofile 8192` | `/etc/nginx/nginx.conf` (`events { worker_connections 4096; }`) ; vérifier `nginx -T \| grep worker_connections` d'abord |
| O-3 | nginx | TLS 1.2+ et HSTS si absents | `ssl_protocols TLSv1.2 TLSv1.3;` `add_header Strict-Transport-Security "max-age=31536000" always;` ; `nginx -t` puis `systemctl reload nginx` (ou `docker exec infra-nginx nginx -s reload`) ; **sauvegarde du fichier avant** (`SERVER-STRATEGY.md:4`) |
| O-4 | compose CastBridge (`<root>/current/docker-compose.override.yml` **ou** `docker-compose.yml` versionné + `.env`) | service `castbridge-play` : image `castbridge-play:current`, `ports: "127.0.0.1:7091:8090"`, `mem_limit: 384m`, `read_only`, `cap_drop ALL`, réseaux `castbridge-internal` + `castbridge-edge`, volume `castbridge-apk:/data/apk:ro` (lots et paquets réservés), secret **public** `play-ticket.pub`, `ulimits: nofile: 16384` | `backend/docker-compose.yml` (zone additive ; w20-03 l'écrit, **le propriétaire déploie**) ; `.env` : `CASTBRIDGE_PLAY_DB_PASSWORD`, `CASTBRIDGE_PLAY_TICKET_PUBKEY_FILE` |
| O-5 | MySQL | schéma `castbridge_play` + utilisateur `castbridge_play` limité à ce schéma | `CREATE DATABASE castbridge_play …; CREATE USER 'castbridge_play'@'%' …; GRANT ALL ON castbridge_play.* TO …;` (une fois ; w20-08 donne le script) |
| O-6 | API principale | secret `play-ticket.key` (Ed25519, privé) dans `secrets/`, `CASTBRIDGE_PLAY_TICKET_KEY_FILE` ; sa clé publique vers `castbridge-play` | `openssl genpkey -algorithm ed25519 -out secrets/play-ticket.key && openssl pkey -in secrets/play-ticket.key -pubout -out secrets/play-ticket.pub` ; droits 0400 uid 10001 (comme les autres secrets) |
| O-7 | pare-feu | **rien à ouvrir** (443 l'est déjà) ; vérifier que 7091 **n'est pas** publié hors loopback (`ss -ltnp \| grep 7091`) ; port de diagnostic 7443 : `ufw allow from <IP du Mac> to any port 7443 proto tcp` **à la demande**, puis `ufw delete` | `ufw status numbered` |
| O-8 | DNS (plus tard) | `play.bridge.sti-cm.com` CNAME → `bridge.sti-cm.com` ; certbot `-d play.bridge.sti-cm.com` | quand D-W20-1 (B) est activée |
| O-9 | déploiement | `tools/release/deploy-server.sh` étendu par w20-08 : `--service play` (image `castbridge-play:candidate` → `current`, healthcheck `GET /play/health`, rollback) ; **jamais `main`** ; `tag server-play-<v>` | |
| O-10 | surveillance | `GET /play/health` (sans secret : salles, connexions, mémoire) dans la surveillance existante (`sonnet-w1-12`) ; alerte si > 1 000 codes faux / h ou > 80 % du plafond de connexions | |

### 2.10 Produit : salles, salons, classements, évènements

| Fonction | V1 (W20) | V2 (après décisions) |
|---|---|---|
| (a) Salle privée | code 8 car. + lien + QR ; hôte TV ; 1 table de 8 ; Millionnaire (public distant vote, « ami » distant) et Duel | tables multiples ; hôte téléphone (D-W20-6) |
| (b) Salon public | **Duel rapide** : file d'attente par `(niveau, filière, langue)` ; table remplie dès 2 joueurs, départ à 4 ou 20 s ; questions libres ; 10 questions ; pas de mise | choix de la durée, « revanche » |
| (c) Classements | **par appareil enregistré** (app ou TV), points de Duel sur 30 jours glissants, mondial / par pays (IP) ; **anti-triche** : parties marquées `BotScore` exclues, ≥ 4 joueurs humains distincts requis, plafond de 20 parties classées / appareil / jour, écart-type des temps > 0 | par école (licence école), par classe (enseignant) ; badges |
| (d) Évènements | **non** (hors juridique : horaires et salles sponsorisées sans prix possibles en V2) | tournois horaires (serveur crée les salles à l'heure dite, tables multiples, même graine) ; salles de classe (enseignant = hôte téléphone, liste de présence par pseudonyme, export CSV local) ; salles sponsorisées (nom à l'écran) |
| (e) Télémétrie | évènements **agrégés** par salle à la fin : `play_room {scope, mode, level, players, remote, tables, durationMs, abandoned, botFlag}` ; par joueur **rien de nominatif** ; KPI `/admin/kpi` : salles/jour, joueurs/jour, pays, taux d'abandon, RTT médian par pays | |

## 3. Protection des données

### 3.1 Inventaire

| Donnée | Qui | Pourquoi | Où | Durée |
|---|---|---|---|---|
| Pseudonyme (≤ 16 car., modéré) | joueur | affichage | mémoire de salle ; classement (si classé) | salle : 24 h après fin ; classement : 12 mois |
| `deviceHash` = SHA-256(sel serveur ‖ `installId` app / `deviceId` TV / identifiant aléatoire du navigateur en `localStorage`) | joueur, TV | un siège par appareil, classement, abus | mémoire ; table `play_device` | 12 mois sans activité puis purge |
| Pays (code ISO) | déduit de l'IP (GeoIP existant, `castbridge.geo`) | classement par pays, KPI | `play_result` | 12 mois |
| IP brute | connexion | limites, journal d'abus | journaux du service (ECS) ; **jamais** en base | **7 jours** (plus court que les 30 j de l'API : `ip-days`) |
| Réponses et temps | joueur | partie, anti-triche | mémoire ; agrégats | fin de salle + 24 h ; agrégats sans identifiant |
| Ticket, activation `cbx1`, lignes de location | TV | droits | mémoire de salle seulement ; **jamais journalisés** | durée de la salle |
| Signalements | joueur qui signale, pseudonyme signalé, `deviceHash`, salle, horodatage | modération | `play_report` | 90 jours |
| Bannissements | `deviceHash`, raison, durée | abus | `play_ban` | durée + 30 jours |

Principes (Cameroun : loi n° 2010/012 sur la cybersécurité et la cybercriminalité, projet de loi données personnelles ; CEMAC ; RGPD comme référence de bonne pratique à l'export) : **minimisation** (aucun compte, aucun courriel, aucun numéro), **finalité** (jeu, abus, classement), **durée** (table ci-dessus, purge automatique `PlayRetentionJob` nocturne, testée), **transparence** (page `/play/confidentialite` en français, lisible sans compte, texte dans `docs/QUIZ-EN-LIGNE.md`), **droit d'effacement** (bouton « Effacer mes données de jeu » dans l'app ⇒ `DELETE /play/me` par `deviceHash` ; page web : effacement du `localStorage` + purge à 12 mois), **sécurité** (§ 4). Les textes juridiques définitifs attendent la relecture du 2026-12-31 ; le fonctionnement ci-dessus n'en dépend pas.

### 3.2 Enfants

- Profil enfant actif sur la TV (`ParentalEngine.activeProfile()`, bande -12 / 12-15 / 16-17) ⇒ `INTERNET` **noir** : « Internet : réservé aux adultes (code parental) ». Le parent peut, **par le code parental** (corps POST, jamais URL, règle `PARENTAL.md`), activer « Quiz Internet : salles privées » pour un profil 12-15 / 16-17 ; **-12 : jamais** (même avec le code) ; salons publics et classements : 16-17 seulement si activé. Réglage `ChildProfile.blocked` : la catégorie `INTERNET` existe déjà (`ParentalModel.kt:74`) : le Quiz Internet **la respecte** (aucune nouvelle catégorie).
- Page web `/play` : case « J'ai 13 ans ou plus, ou un parent m'accompagne » **sans vérification** (il n'en existe pas d'honnête) ; aucune collecte au-delà du pseudonyme : le risque pour un mineur est le contact par pseudonyme ⇒ **aucun texte libre** (pas de chat), pseudonymes modérés, « Signaler ».
- Un parent peut voir dans le rapport parental existant (`ParentalReports`) les minutes « Quiz Internet » comme une catégorie de jeu : additif.

### 3.3 Pseudonymes

`C/quiz/online/Pseudonym.kt` (pur) : normalisation Unicode NFKC, 2-16 caractères, lettres/chiffres/espace/`-`/`'`, pas de suite de ≥ 7 chiffres (numéros de téléphone), pas d'URL, liste de mots interdits fr/en/pidgin (fichier `castbridge/quiz/online/blocklist.txt`, avec variantes « leet »), pas d'usurpation (`CastBridge`, `Admin`, `Modérateur`, `Prof`) ; refus = `BAD_NAME` avec le motif ; collision dans la salle ⇒ suffixe (existant `unique`). Un pseudonyme signalé 3 fois par des appareils distincts ⇒ remplacé par « Joueur 7 » dans la salle (muet), file de modération.

### 3.4 Signalement et bannissement

```
  joueur ─ « Signaler ce joueur » (motif : pseudonyme, triche, autre) ─► play_report
  ≥ 3 signalements d'appareils distincts en 24 h ─► pseudonyme masqué ; BotScore ↑
  modération (console /admin de l'API principale, lecture du schéma play en RO via une vue, ou page /play/admin protégée par le même compte admin ? → D-W20-11 : page minimale dans castbridge-play, protégée par un jeton admin DISTINCT) ─► bannir deviceHash (1 j / 7 j / 30 j / définitif), lever, voir les signalements
  banni ─► « Vous ne pouvez pas rejoindre de partie Internet jusqu'au <date> » (code BANNED, retryAfter) ; jamais en local
```

### 3.5 Journaux

Format ECS existant ; **jamais** : jeton, ticket, activation, pseudonyme en clair (haché à 8 hex pour corréler), IP complète au-delà de 7 jours (masquage `…x.y` comme `Redact.scrub` côté TV), question/réponse. Toujours : `roomId`, `code` (ok : il expire), évènement, compteurs, `deviceHash` (8 hex), pays.

## 4. Sécurité : modèle de menace

| # | Menace | Vecteur | Parade | Test |
|---|---|---|---|---|
| T-1 | Deviner un code de salle | énumération HTTP/WS | 40 bits, 10/IP/5 min, 100/IP/j, 50/salle ⇒ rotation, 429 avec `Retry-After`, journal | `RoomCodeGuardTest` |
| T-2 | Vol de jeton de joueur | URL partagée, journal, capture | jeton hors URL, lié à `deviceHash`, expire avec la salle, TLS ; vol ⇒ expulsion possible par l'hôte | `TokenBindingTest` |
| T-3 | Rejeu / double réponse | renvoi d'`act` | idempotence par `(questionId, playerId)` ; `seq` croissant par connexion ; une réponse définitive (existant) | `IdempotenceTest` |
| T-4 | Réponse falsifiée (temps, choix, question future) | client modifié | le serveur ne croit **aucun** temps client ; `questionId` doit être la question **ouverte** ; choix ∈ 0..3 après mélange serveur ; questions futures jamais envoyées | `ServerAuthorityTest` |
| T-5 | Fuite de la banque | route de masse, `view` bavarde, `reveal` anticipé | aucune route de liste ; `view` serveur = `QuizRoom.view` (déjà testé « bonne réponse jamais avant clôture ») ; réservées lues à la demande ; **test de source** : aucun champ `answer` dans un message avant `reveal` | `NoAnswerLeakTest` (fuzz 1 000 parties) |
| T-6 | DoS : connexions, messages, slow-loris, mémoire | flots | § 2.8 ; plafonds **avant** mémoire ; coalescence des diffusions ; files de sortie bornées (fermeture 1008 si > 64 Ko en attente) ; `limit_conn` nginx | `LimitsTest`, charge `PlayLoadTest` (JVM, 500 salles simulées) |
| T-7 | Pseudonymes malveillants, harcèlement | texte | § 3.3 ; pas de chat ; signalement ; bannissement | `PseudonymTest` |
| T-8 | Robots | scripts | `BotScore`, parties non classées, limites par appareil/IP, salons publics avec file d'attente | `BotScoreTest` |
| T-9 | Compromission du service `play` | 0-day, fuite | **aucun secret d'activation** : clé publique de ticket seule, clés publiques d'émetteurs, utilisateur MySQL limité au schéma `play`, volume lots **RO**, pas d'`admin-token`, conteneur `read_only`, `cap_drop ALL`, réseau interne sans Internet sortant sauf l'API (`/api/v1/revocations`) ; rayon : salles en cours, classements, signalements | revue Opus ; `docker inspect` dans le runbook |
| T-10 | Compromission de l'API principale via `play` | appel interne | `play` n'appelle l'API qu'en **lecture publique** (`/api/v1/revocations`) ; le ticket va de la TV à l'API puis à `play`, jamais de `play` vers l'API avec un privilège | revue |
| T-11 | TLS | downgrade, certificat expiré | nginx 1.2+ ; clients : `wss://` obligatoire, **épinglage non** (certbot tourne) ; certificat invalide ⇒ rouge, refus (jamais « continuer quand même ») ; surveillance 14 j (existant `README.md:273`) | `TlsPolicyTest` (client) |
| T-12 | WebSocket cross-site | page tierce ouvre `wss://…/play/ws` | vérification d'`Origin` (liste : `https://bridge.sti-cm.com`, `https://play.bridge.sti-cm.com`, `null`/absent pour l'app et la TV **avec** jeton) ; `SameSite` sans objet (pas de cookie) ; CSRF : POST de repli exigent l'en-tête `X-CB-Play-Token` | `OriginCheckTest` |
| T-13 | Entrée non valide | JSON malformé, champs géants, Unicode | schéma strict par type de message, `Json` du cœur (déjà sans dépendance) avec limites ; rejet silencieux + compteur ; 3 rejets ⇒ fermeture | `MessageSchemaTest` (fuzz) |
| T-14 | Hôte tricheur (TV modifiée) | relais local | la TV ne peut qu'**allonger** les temps de ses joueurs (§ 2.6) ; ne tire pas, ne note pas, ne voit pas la réponse avant les autres (même `reveal`) | `RelayFairnessTest` |
| T-15 | Collusion (même pièce, réponses partagées) | humains | inévitable ; limiter l'enjeu (jetons virtuels), classements par appareil avec ≥ 4 humains distincts, tables de salon public mélangées par pays/IP | — (dit honnêtement dans la doc) |
| T-16 | Faux ticket, ticket rejoué | forge | Ed25519 + `exp` 10 min + `nonce` mémorisé 10 min ; activation `cbx1` vérifiée par clé d'émetteur ; `deviceId` du ticket = `deviceId` de l'activation | `TicketTest` |
| T-17 | PIN de la TV sur Internet | confusion | le PIN **n'existe pas** dans le protocole `play` ; `docs/ADMIN.md` inchangé ; la TV n'écoute rien de nouveau (connexion sortante seule) | test de source : aucun `X-CB-Pin` dans `server-play/` |
| T-18 | Journaux bavards | debug | § 3.5 ; test de `toString` sur `Ticket`, `PlayerToken`, `RoomSecrets` | `RedactionTest` |

**Approbations du propriétaire avant toute production** : O-1…O-7 (§ 2.9) sont des actes sur le serveur partagé ⇒ **jamais** par un exécutant ; `deploy-server.sh --service play --apply` **seulement** par le propriétaire ; publication des paquets réservés sur le volume `play` **seulement** après le gel des `reserved-ids` et la republication des lots libres v+1 (`quiz-toutes-les-questions.md` ordre a-e).

## 5. Les clients

### 5.1 CastBridge-TV

```
  QuizHub.open(ctx)  ──►  QuizRoom (LocalAuthority, inchangé)  ──►  QuizHttp /quiz/* (téléphones locaux, inchangé)
         │
         │ « Ouvrir sur Internet » (télécommande)
         ▼
  PlayClient (C/quiz/online/, pur : automate + codec) + PlayTransport (R/, mince : OkHttp WebSocket ; repli SSE/long-poll)
         │  sortant, TLS, wss://bridge.sti-cm.com/play/ws ; derrière box/CGNAT : AUCUN port entrant (la TV initie, le serveur répond sur la même connexion)
         ▼
  ServerAuthority : la TV affiche la vue reçue (même QuizViews), relaie act() de ses téléphones locaux avec localElapsedMono, affiche SafetyView reçue
```

- Internet de la TV : Wi-Fi/Ethernet de la TV, **ou** la passerelle Internet du téléphone (Bluetooth/Wi-Fi, route de repli de `Routes`, existante pour les lots) : **honnêteté** : par la passerelle Bluetooth du téléphone (100-300 Ko/s, RTT +100-300 ms) le Quiz Internet **marche** (≈ 1 Ko/s par table) mais le signe reste **orange** « Internet par le téléphone (lent) » ; par Wi-Fi Direct **sans** Internet sur la TV : la TV n'a **pas** Internet (le groupe P2P n'en donne pas) ⇒ noir, sauf passerelle du téléphone.
- Le câblage TV (`R/`) attend la **sortie du gel** ; le cœur `PlayClient` est pur et testé contre le vrai service en **boucle locale** (§ 5.4).

### 5.2 CastBridge (téléphone)

- **Avec l'app** : onglet Quiz ⇒ « Rejoindre une partie Internet » (code ou lien `castbridge://play?code=`, QR scanné par la caméra) ⇒ WebView sur `https://bridge.sti-cm.com/play/j/<code>?app=1` (**même page** que sans app : un seul client web à maintenir, comme aujourd'hui `/quiz` dans une WebView `QuizScreen.kt:168`) ; l'app ajoute : `deviceHash` stable (`installId`), le lien d'application, la notification « la partie commence », et, avec une TV de confiance, « Ouvrir la salle de ma TV sur Internet » (`POST /api/quiz/scope`, PIN ; confirmation sur la TV).
- **Sans l'app** : `https://bridge.sti-cm.com/play` (page statique servie par `castbridge-play`, dérivée de `castbridge/quiz/play.html` 24 Ko : même ergonomie, même rendu des rôles `player/audience/friend/candidate`, WebSocket avec repli SSE/long-poll, `sessionStorage` pour le jeton, aucune dépendance externe, ≤ 60 Ko, fonctionne sur Android 8 / Safari 14).
- **Hors ligne d'abord** : l'onglet Quiz **ne change pas** sans Internet ; aucune roue d'attente réseau ; le bouton Internet n'apparaît que si le téléphone **a** Internet (sinon ligne noire « Internet : non connecté »).

### 5.3 Un seul cœur de jeu, deux autorités

```
  C/quiz/online/Authority.kt
    interface GameAuthority { fun view(token): Map ; fun act(token, action, questionId, choice, arg): Act ; fun join(code, name, token, device): JoinResult ; val events: Flow/Listener(seq) ; fun safety(): SafetyView }
    class LocalAuthority(room: QuizRoom, facts: () -> SafetyFacts) : GameAuthority     // la TV, aujourd'hui, enveloppé tel quel
    class ServerAuthority(client: PlayClient) : GameAuthority                           // la TV et le téléphone en Internet : vue reçue, act envoyé
  server-play/… ServerRoom = QuizRoom + RoomCode + Tables + RttBook + Limits            // le serveur : le MÊME QuizRoom, autorité réelle
```

Tests : les tests existants de `QuizRoom` (`QuizRoomTest`, `QuizSoloTest`, `QuizRoomHistoryTest`) tournent **inchangés** ; `AuthorityContractTest` joue le **même scénario** (Duel 3 joueurs, 10 questions, une déconnexion, une réponse en double, un rejeu) contre `LocalAuthority` **et** contre `ServerAuthority` relié à un `castbridge-play` démarré **sur un port aléatoire dans la JVM de test** (Spring Boot `webEnvironment = RANDOM_PORT`), et exige la **même séquence d'états** (modulo horodatages) : c'est la preuve « un seul cœur ». Horloge injectée (`clock`), `random` à graine, latence simulée (`LatencySim` 0 / 150 / 600 ms, pertes 2 %).

### 5.4 Ce qui est réutilisé (vérifié)

`QuizRoom` (salle, jetons, `view`, idempotence), `QuizDuel`/`QuizGame` (règles, minuteries, jokers, simulations), `QuizBank`/`QuizHistoryBook` (tirage, 300 parties), `Json`, `QrCode` (QR du code Internet sur la TV), `play.html` (page de jeu : rôles, SSE, long-poll), `QuizHttp` (limites : mêmes constantes), `TvSignal` (niveaux, formes, couleurs), `Reason` (W19 : enveloppe de refus, codes nouveaux `PLAY_*` additifs), `LinkMachine` (courbe de reprise), `Entitlements`/`TvGate`/`RentalLines` (droits, **sur le serveur** via `:core`), `Routes` (passerelle Internet du téléphone), `RateLimitFilter` (modèle du seau par IP), GeoIP (`castbridge.geo`), `deploy-server.sh` (déploiement tracé), `ParentalEngine` (catégorie `INTERNET`).

## 6. Ce qu'il faut construire, dimensionné et ordonné

| Ordre | Cahier | Livre (valeur) | Gel | Modèle · effort | Audit Opus | Dépend |
|---|---|---|---|---|---|---|
| 1 | **w20-01** `PlayScope`, `SafetyFacts/SafetySign`, `GameAuthority` + `LocalAuthority`, codes `Reason` `PLAY_*` | le signe et l'abstraction, testés, sans réseau | cœur pur | sonnet M | échantillon | — |
| 2 | **w20-02** protocole `play-v1` (messages, codec, `seq`, versions) + `ServerRoom`/`RoomCode`/`Tables` purs | la machine à états de la salle Internet, sans Spring | cœur pur | sonnet L | échantillon | w20-01 |
| 3 | **w20-03** service `server-play/` (Gradle, Spring Boot, WS + SSE + long-poll, page `/play`, `/play/health`), **test en boucle locale** `PlayLoopbackTest` + `AuthorityContractTest` | une salle Internet qui tourne sur une JVM de test, de bout en bout | service + tests (hors `S/`, `R/`) | sonnet L | **obligatoire** | w20-02 |
| 4 | **w20-04** droits : ticket `cbp1` (API principale : route + clé), vérification côté `play`, `cbx1` + locations ⇒ `TvAccess`/lots couverts, questions réservées à la demande, `reserved-ids` | le modèle commercial appliqué, prouvé par tests | cœur + API + service | sonnet L | **obligatoire** | w20-03 |
| 5 | **w20-07** anti-triche et limites : `RttBook`, notation compensée, grâce, `BotScore`, `Limits` (IP/appareil/salle), `Pseudonym`, `OriginCheck`, schéma de messages (fuzz) | la sûreté sous charge et sous triche | cœur + service | sonnet M | **obligatoire** | w20-03 |
| 6 | **w20-11** `LatencySim`, chaos de reprise (joueur, TV, serveur en drain), `NoAnswerLeakTest` fuzz 1 000 parties, charge 500 salles | la preuve S-REPRISE et T-5 | tests | sonnet M | — | w20-03, 07 |
| 7 | **w20-09** salons publics (file d'attente, tables), classements (schéma `castbridge_play`, Flyway, purge), `DELETE /play/me` | produit (b), (c), rétention | service | sonnet L | échantillon | w20-04, 07 |
| 8 | **w20-10** modération : signalements, bannissements, page admin minimale, `PlayRetentionJob`, page `/play/confidentialite`, `docs/QUIZ-EN-LIGNE.md` (données) | (d) sécurité des personnes, données | service + docs | sonnet M | échantillon | w20-09 |
| 9 | **w20-08** exploitation : `docs/PLAY-OPS.md` (O-1…O-10 avec fichiers exacts), compose additif, `Dockerfile`, `deploy-server.sh --service play`, script SQL, surveillance | ce que le propriétaire exécute, sans surprise | outils + docs | sonnet S | — | w20-03 |
| 10 | **w20-05** câblage TV (`R/`) : `PlayTransport` OkHttp WS, bandeau `SafetyView`, « Ouvrir/Fermer Internet », repli local § 1.5, relais des `act` locaux, passerelle Internet du téléphone | ce que l'usager voit sur la TV | **après le gel** | sonnet L | **obligatoire** | w20-01…04, 07 |
| 11 | **w20-06** câblage téléphone (`S/`) + page web : onglet Quiz (rejoindre, lien d'application, QR), WebView `/play`, `deviceHash`, « Ouvrir la salle de ma TV » | ce que l'usager voit sur le téléphone et sans app | **après le gel** (page web : pendant) | sonnet M | échantillon | w20-03, 05 |
| 12 | **w20-12** docs : `QUIZ.md` § 10, `HANDOFF.md`, `PARCOURS` P-55…P-58, `SECURITY` divulgation, gabarit (règle « périmètre ») | mémoire du projet | docs | haiku S | — | 01…11 |
| 13 | **w20-13** (conditionnel) tables multiples, salles de classe (enseignant hôte téléphone, présence, CSV), tournois horaires, salles sponsorisées **sans prix** | commercial V2 | service + `S/` | sonnet L | échantillon | D-W20-6/10 ; w20-09 |

Effort ≈ **31 agent·jours**, ≈ **29 $** (détail dans l'index). Pendant le gel : 1-9 (≈ 23 j, ≈ 22 $). Parallélisme : au plus 3 ; w20-03 **seul** sur `server-play/` jusqu'à sa fusion ; w20-04 seul sur `B/` (une route additive `PlayTicketController`) ; aucun cahier W20 ne touche `C/tv/ReceiverServer.kt` (la route `POST /api/quiz/scope` passe par l'extension Quiz existante `QuizHttp`/`QuizPackApi`, zone w20-05). Ordre de valeur : w20-01 (le signe, visible en local dès la sortie du gel) → 02/03 (une salle Internet qui tourne en test) → 04 (le commercial) → 07/11 (sûr) → 08 (déployable par le propriétaire) → 05/06 (visible) → 09/10 (produit) → 12 → 13.

## 7. Décisions du propriétaire (chacune avec recommandation)

| id | Question | Recommandation | Si non |
|---|---|---|---|
| D-W20-1 | Le « port approprié » = **443**, chemin `/play/` sur `bridge.sti-cm.com` (A), nom `play.` réservé pour plus tard (B) ? | **Oui (A), DNS de (B) créé maintenant** | un port dédié = joueurs perdus sur les réseaux mobiles/écoles ; (B) seul = certificat et bloc nginx de plus dès le départ |
| D-W20-2 | Écouteur de diagnostic 7443, **fermé par défaut**, ouvert par `ufw` à une IP et refermé ? | **Oui** | aucun moyen de mesurer sans nginx ; acceptable |
| D-W20-3 | Autorité **serveur** pour Internet (la TV = écran, télécommande, relais) ; repli = **nouvelle partie locale** après 60 s ? | **Oui** | TV autoritaire : triche de l'hôte, parties mortes avec la TV, réservées impossibles pour une TV non titulaire |
| D-W20-4 | Service dédié `castbridge-play` (Kotlin, `:core`, conteneur séparé, schéma séparé, **aucun secret de licence**) plutôt que l'API Java existante ? | **Oui** | deux logiques de jeu à maintenir ; licences et joueurs anonymes dans le même processus |
| D-W20-5 | TV d'**essai** : salles Internet **privées**, questions libres, 3 parties/jour ; et trancher la contradiction `TrialPolicy` (Quiz fermé en essai) vs `QuizEdition.TRIAL_OPEN` ? | **Oui** ; ouvrir le Quiz en essai dans `TrialPolicy` (local + Internet privé), ce qui correspond à « phase d'essai : toutes les questions disponibles » | l'essai ne montre pas l'argument commercial ; ou l'essai héberge du public avec des réservées en clair (déjà extractibles de l'APK d'essai) |
| D-W20-6 | Hôte **téléphone** (salle sans TV) en V1 ? | **Non en V1** (la TV est le produit) ; V2 si une TV de confiance a été vue en 30 jours | V1 plus large mais l'argument « achetez la TV » s'affaiblit |
| D-W20-7 | « Ouvrir sur Internet » exige une **confirmation sur la TV** (télécommande), même demandé par le téléphone avec le PIN ? | **Oui** (présence physique devant l'écran, cohérent W18) | un téléphone de confiance volé ouvre la maison sur Internet |
| D-W20-8 | Hôte qui part : « Terminer » ou « Laisser finir en auto-hôte » ; migration vers un joueur : **non** en V1 ? | **Oui** | migration = autorité d'un inconnu sur les réglages |
| D-W20-9 | Classements **par appareil enregistré** (app, TV) ; page web sans app : jouable, non classée ? | **Oui** (empreinte navigateur trop faible, robots) | classement pollué dès la première semaine |
| D-W20-10 | V2 : salles de classe (enseignant) et salles sponsorisées **sans prix** avant le juridique ; tout prix/lot/mise en argent **après** le 2026-12-31 ? | **Oui** | rien de commercial avant janvier ; ou risque juridique |
| D-W20-11 | Modération : page admin **minimale dans `castbridge-play`**, jeton admin **distinct** de `CASTBRIDGE_ADMIN_TOKEN` ? | **Oui** | le jeton principal entre dans le service exposé (rayon T-9) |
| D-W20-12 | Durées : IP 7 j, salle 24 h, classement 12 mois, signalements 90 j, bannissements durée + 30 j ? | **Oui** | plus long = plus de données à protéger pour rien |
| D-W20-13 | Enfants : -12 **jamais** Internet ; 12-17 salles privées **par code parental** ; salons publics 16-17 seulement ? | **Oui** | plus permissif = contact d'inconnus par pseudonyme |
| D-W20-14 | Publier les 25 paquets réservés sur le volume `play` **seulement** après gel des `reserved-ids` et republication des lots libres v+1 ? | **Oui** (ordre a-e du rapport) | réservées accessibles à tous pendant la transition |
| D-W20-15 | Plafonds de départ 400 salles / 3 000 connexions / 8 connexions par IP ? | **Oui**, réglables par `.env` sans redéploiement | trop haut = OOM du conteneur ; trop bas = 429 à la première école |

**BLOQUÉ (faits, pas opinions)** : **B-W20-1** configuration réelle du nginx partagé (fichier, `worker_connections`, TLS, HSTS, `limit_conn`) et du pare-feu (`ufw`, fournisseur) : **absente du dépôt** ; w20-08 écrit le runbook **avec des chemins proposés**, le propriétaire les confirme sur la machine (`nginx -T`, `ufw status`, `ss -ltnp`). **B-W20-2** nombre de vCPU de la VM : inconnu (capacités § 2.3 à confirmer par `PlayLoadTest` puis une mesure réelle). **B-W20-3** tout ce qui touche à l'argent, aux prix, aux tournois payants, à la publicité : **juridique reporté au 2026-12-31**.

## 8. Risques

| # | Risque | Prob. | Impact | Parade |
|---|---|---|---|---|
| 1 | Le nginx partagé n'a pas assez de `worker_connections` ou coupe les WebSockets à 60 s | haute | déconnexions toutes les minutes | ping 25 s, `proxy_read_timeout 75 s`, O-2 ; repli SSE/long-poll automatique et **dit** (orange « réseau lent ») |
| 2 | 384 Mo trop justes pour 3 000 connexions | moyenne | OOM, salles perdues | plafonds avant mémoire ; `PlayLoadTest` ; `mem_limit` 512 m si la VM le permet (4,6 Go dispo) |
| 3 | Module Gradle serveur dans le build Android (`server-play/`) : CI, versions Kotlin/Spring | moyenne | build cassé | module isolé (`:play-server`), pas de plugin Android ; `tools/core-harness/run.sh :play-server:test` ; sinon repli : publier `:core` en jar local consommé par un build Gradle séparé |
| 4 | Latence mobile > 600 ms rend le Duel frustrant | moyenne | abandon | compensation § 2.6, « réseau lent » affiché, mode Duel « posé » (40 s) proposé quand RTT médian > 800 ms |
| 5 | Robots et collusion polluent le classement | haute | classement sans valeur | `BotScore`, classement par appareil enregistré, ≥ 4 humains, 20 parties/j ; dire les limites (T-15) |
| 6 | Réservées en clair dans l'APK d'essai + servies en ligne | certaine tant que l'essai dure | l'argument « réservé » affaibli | D-W20-14, cercle d'essai fermé ; le serveur ne les sert qu'une à la fois |
| 7 | Le repli « nouvelle partie locale » surprend l'usager | moyenne | confusion | texte explicite, scores locaux préservés sur la TV, proposition de reprise une fois |
| 8 | Deux services, un déploiement : erreur de version entre `play` et `:core` de la TV | moyenne | messages incompris | `play-v1` versionné et additif (règle W19), `caps` dans le `hello` du protocole, matrice `CompatMatrixTest` étendue (persona `play`) |
| 9 | Juridique : classements ou salles sponsorisées requalifiés en jeu d'argent | faible (aucun prix) | arrêt | aucune valeur, aucun prix, texte « jetons virtuels — démo » partout ; relecture 2026-12-31 |
| 10 | La TV n'a presque jamais Internet (85 % sans box) ⇒ la fonction sert peu | **certaine pour la majorité** | ROI | la passerelle Internet **du téléphone** (existante pour les lots) rend la TV joignable au serveur via les données mobiles du téléphone : c'est **le** chemin dans 85 % des foyers ; à mesurer (RTT +100-300 ms) ; le signe le dit (orange « par le téléphone ») |
| 11 | Audits Opus tardifs (4 obligatoires) | moyenne | w20-04/05 bloqués | w20-03 et 07 en premier ; audits groupés par deux |
| 12 | Page web sur vieux navigateurs (Android 8, Safari 14) sans WebSocket fiable derrière proxy opérateur | moyenne | joueur ne peut pas entrer | repli SSE/long-poll sur 443 (même chemin), testé en `PlayLoopbackTest` pour les trois transports |

## 9. Ce qui n'a pas pu être vérifié

- Aucun appareil ni serveur touché : latences, mémoire par connexion, débit par la passerelle Bluetooth du téléphone, RTT depuis le Cameroun sont des **estimations**.
- Le nginx partagé (`infra-nginx`, `infra-certbot`) : aucun fichier dans le dépôt ; seules deux propositions non appliquées (`backend/README.md:147-190`) ; `worker_connections`, TLS, HSTS, `limit_conn` inconnus (B-W20-1) ; le pare-feu fournisseur « non vérifié » (`REMOTE-MANAGEMENT.md:6-7`).
- vCPU de la VM : non documentés (B-W20-2) ; RAM 7,8 Go dont ≈ 4,6 Go disponibles (`SERVER-STRATEGY.md:7-8`).
- `castbridge-api` n'a **ni** `spring-boot-starter-websocket` **ni** `SseEmitter` (vérifié : zéro occurrence) : tout le temps réel est à créer dans `server-play/`.
- Comportement des WebSockets dans la WebView Android 8 et dans Safari 14 derrière les proxys des opérateurs camerounais : à mesurer avec la page `/play` (w20-06, relevé humain).
- `TrialPolicy` ferme le Quiz en essai (liste blanche, `GAMES = setOf("sudoku")`) alors que `QuizEdition.TRIAL_OPEN = true` : contradiction réelle, à trancher (D-W20-5), pas devinée.
- Le gel des `reserved-ids.json` et la republication des lots libres v+1 ne sont **pas faits** (rapport `quiz-toutes-les-questions.md`) : prérequis de w20-04 côté contenu.
- Le compte des questions **jouables** en ligne par niveau dépend de `PlayPolicy`/`QuizPlay.isPlayable` (canal bêta) : le serveur appliquera la **même** politique (`:core`) ; le canal d'une salle Internet = celui de la TV hôte (`QuizEdition.playChannel`).
