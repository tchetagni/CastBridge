# Administration à distance : tunnels SSH inverses des CastBridge-TV

Statut : conception et serveur (backend) prêts ; **rien n'est déployé ni activé**. Le module est éteint par défaut (`castbridge.tunnel.enabled=false` : toutes les routes répondent 404).
Fichiers d'exploitation : `ops/tunnel/` (modèles, jamais appliqués automatiquement). Code : `backend/src/main/java/castbridge/server/tunnel/`.

## 1. Décision du propriétaire

Chaque CastBridge-TV ouvre elle-même, **en sortie**, un tunnel SSH inverse vers le serveur. **Chaque TV a son propre port** sur le serveur. Toujours actif : pas de consentement par session ; la TV a seulement besoin d'Internet (une TV hors ligne reste hors ligne, comme toujours). Accès pour le propriétaire **et** pour des experts désignés, révocables.

## 2. Architecture

```
 CastBridge-TV (sshd de la TV, port 2222)                       Serveur bridge.sti-cm.com
 ┌────────────────────────────┐   ssh -R 127.0.0.1:22xxx:127.0.0.1:2222   ┌───────────────────────────────────┐
 │ client SSH, clé ed25519    │ ───────────────────────────────────────▶ │ sshd :2200, compte « cbtunnel »   │
 │ PROPRE à la TV (sortant)   │   (aucun port ouvert côté TV)             │ le port 22xxx écoute sur 127.0.0.1│
 └────────────────────────────┘                                           │ du serveur SEULEMENT              │
                                                                          │ (GatewayPorts no)                 │
 Expert / propriétaire                                                    │                                   │
 ssh -J cbexpert@serveur:2200 -p 22xxx tv@127.0.0.1  ───────────────────▶ │ compte « cbexpert » : rebond vers │
                                                                          │ 127.0.0.1:<port d'une TV> only    │
                                                                          └───────────────────────────────────┘
```

Personne n'atteint une TV sans d'abord s'authentifier auprès du serveur (clé d'expert), puis auprès du sshd de la TV.

### 2.1 Enrôlement (backend)

`POST /api/v1/tunnel/enroll`

```json
{"activation":"cbx1.<jeton>","sshPublicKey":"ssh-ed25519 AAAA… commentaire","deviceCode":"XXXX-XXXX-XXXX-XXXX"}
```

Réponse 200 :

```json
{"host":"bridge.sti-cm.com","sshPort":2200,"user":"cbtunnel","port":22100,"hostKeyFingerprint":"SHA256:…"}
```

(`hostKeyFingerprint` est omis tant que `castbridge.tunnel.host-key-fingerprint` est vide.) Contrôles, dans l'ordre :

1. module activé, limite par adresse IP (`enroll-per-hour`, 30 par défaut, 429 au-delà) ;
2. code d'appareil bien formé (caractère de contrôle), clé SSH **ed25519 uniquement** (analysée strictement, ré-émise sous forme normalisée : le texte de l'appelant n'entre jamais dans un fichier) ;
3. l'activation est vérifiée comme la TV le fait (`EnvelopeVerifier` : clés de confiance du serveur, droits de signature, sujet `tv`, clé révoquée, poste révoqué). Les activations d'**essai et de production** sont acceptées. Seule la fenêtre d'installation de 48 h n'est pas exigée (on s'enrôle après l'installation) ;
4. le code d'appareil doit être celui de la cible de l'activation ;
5. un appareil dont le tunnel a été révoqué par l'administrateur est refusé (403) : l'interrupteur ne se rouvre pas tout seul ;
6. port : le plus petit port libre de la plage (`port-range`, 22100-22999 par défaut), **stable** par code d'appareil. Même code = même port. Une **nouvelle** clé SSH pour un appareil connu n'est acceptée qu'avec une activation valide (rotation de clé) et remplace l'ancienne. Plage épuisée : 503.

Erreurs en français, sans détail interne. Journal d'audit (table `tunnel_audit`, page d'administration, journal applicatif) : enrôlement, rotation, refus, révocation, listes d'experts ; **jamais de clé, de jeton ni de code complet dans le journal applicatif** (`ABCD-****`).

### 2.2 Fichiers `authorized_keys` écrits par le backend

Écriture **atomique** (fichier temporaire dans le même dossier, `fsync`, renommage ; mode 0644 : clés publiques seulement), réécrite à chaque enrôlement, rotation, révocation, au démarrage et à chaque cycle de sondage (expiration d'un expert). Par défaut dans `{storage-dir}/tunnel/`.

- `authorized_keys` (compte `cbtunnel`), une ligne par TV non révoquée :
  `restrict,port-forwarding,permitlisten="127.0.0.1:<port>",command="/bin/false" ssh-ed25519 AAAA… tv-<code d'appareil>`
  (`restrict` coupe tout ; `port-forwarding` ne rend que le transfert de ports ; `permitlisten` limite le `-R` au port de **cette** TV ; `command` supprime tout shell.)
- `experts_authorized_keys` (compte `cbexpert`), une ligne par expert valide :
  `restrict,port-forwarding,permitopen="127.0.0.1:<port>",permitopen="127.0.0.1:<port>",… ssh-ed25519 AAAA… expert-<id>`
  OpenSSH n'accepte **pas** de plage de ports dans `permitopen` : une option par port de TV enrôlée et non révoquée. Sans aucune TV, `permitopen="127.0.0.1:1"` (une liste vide voudrait dire « partout »).

sshd lit ces fichiers par `AuthorizedKeysCommand` (`ops/tunnel/cb-authkeys.sh`) : le fichier vit dans un volume Docker, son propriétaire n'importe pas (contrairement à `AuthorizedKeysFile` + `StrictModes`).

La TV doit demander **explicitement** `127.0.0.1` comme adresse d'écoute (`-R 127.0.0.1:<port>:127.0.0.1:2222`) : OpenSSH traite le nom « localhost » (valeur par défaut) autrement que `127.0.0.1` pour `permitlisten`.

### 2.3 État des tunnels

Le backend sonde chaque port enrôlé (connexion TCP à `127.0.0.1:<port>`, ou lecture de la liste des ports à l'écoute écrite par l'hôte si le backend est en conteneur : `listening-ports-file`) toutes les 60 s (`probe-seconds`) et à la demande. Il affiche « connectée / hors ligne » et la **dernière fois vue en ligne** (conservée quand la TV tombe).

### 2.4 Liste des experts (signée hors ligne)

`GET /api/v1/tunnel/experts` relaie tel quel le fichier `experts.json` (404 s'il est absent) :

```json
{"generatedAt":1790000000000,"keyId":"…16 hex…","experts":[{"id":"alice","publicKey":"ssh-ed25519 AAAA…","notAfter":0}],"signature":"<base64 Ed25519>"}
```

Texte signé : `castbridge-experts-v1\ngeneratedAt=<ms>\nexpert=<id>|<publicKey>|<notAfter>\n…` (experts triés par `id`, `publicKey` exactement comme dans le fichier, `notAfter` = ms ou 0 = sans fin, pas de retour à la ligne final). Le serveur **ne signe jamais** cette liste. Avant de l'utiliser pour laisser les experts passer par SON sshd, il la vérifie : clé `keyId` connue des clés de confiance **avec le droit REGISTRY**, non révoquée, signature valide, identifiants et clés bien formés (pas d'identifiant en double), **pas plus ancienne** que la dernière liste acceptée (une liste de même date est acceptée : relecture). Sinon la liste est refusée, le motif est affiché sur la page et la dernière bonne liste continue de s'appliquer. Un expert dont `notAfter` est passé est retiré au cycle suivant.

### 2.5 Console d'administration

Page « Tunnels TV » (`/admin/tunnels`, session + CSRF + CSP stricte comme les autres) : compteurs (actifs, connectées, hors ligne, révoqués, ports libres), tableau (code d'appareil, licence et édition, port, état, dernière connexion, commande `ssh -J …` prête à copier), boutons « Sonder maintenant », « Révoquer le tunnel » / « Rétablir », liste des experts avec l'état de la dernière vérification et « Recharger la liste des experts », journal.

## 3. Configuration

Toutes les propriétés `castbridge.tunnel.*` (variables `CASTBRIDGE_TUNNEL_*`) :

| Propriété | Défaut |
|---|---|
| `enabled` | `false` |
| `host` | `bridge.sti-cm.com` |
| `ssh-port` | `2200` |
| `port-range` | `22100-22999` |
| `host-key-fingerprint` | vide (omise de la réponse) ; peut être le chemin d'un fichier |
| `authorized-keys-file` | `{storage-dir}/tunnel/authorized_keys` |
| `experts-file` | `{storage-dir}/tunnel/experts.json` |
| `experts-authorized-keys-file` | `{storage-dir}/tunnel/experts_authorized_keys` |
| `probe-seconds` | `60` (5 minimum) |
| `probe-host` | `127.0.0.1` |
| `listening-ports-file` | vide (sondage TCP) |
| `enroll-per-hour` | `30` par IP |
| `expert-user` | `cbexpert` (compte affiché dans les commandes) |

## 4. Menace (honnêtement)

**Ce que cela est : un accès permanent à des appareils de clients.** Quiconque détient une clé d'expert valide et est passé par le serveur peut tenter de se connecter à n'importe quelle TV enrôlée, à tout moment, sans que l'utilisateur de la TV le sache de façon interactive. C'est voulu, mais cela engage la responsabilité du propriétaire.

- **Vie privée et obligations légales.** Informer les clients : dans les conditions d'utilisation, à l'activation et dans la documentation, dire clairement que l'appareil maintient une connexion de maintenance avec le serveur, qui peut l'utiliser pour le diagnostic et l'assistance. Limiter la finalité (support), la durée de conservation des journaux et les personnes habilitées ; vérifier le droit applicable (protection des données du pays du client, droit de la consommation, exigences éventuelles de consentement). Faire relire par un juriste avant la commercialisation. Un expert s'engage par écrit (confidentialité, finalité, journal).
- **Interrupteur d'urgence** : « Révoquer le tunnel » dans la console retire la clé de la TV du fichier (effet à la prochaine connexion) ; couper aussitôt la session ouverte avec `ops/tunnel/kick.sh <port> --apply`. Pour tous les experts : retirer-les de `experts.json` (nouvelle liste signée, `generatedAt` plus récent) ou supprimer le fichier `experts_authorized_keys`. Pour tout couper : `CASTBRIDGE_TUNNEL_ENABLED=false` puis fichier vidé, ou arrêt du bloc `Match` de sshd.
- **Si la clé SSH d'une TV fuit** : l'attaquant ne peut, avec cette clé, que demander **le transfert distant du port de cette TV**, sur `127.0.0.1` du serveur ; pas de shell, pas de `-L`, pas d'autre port. Il peut au pire occuper ce port (déni de service de ce tunnel : la vraie TV échoue à lier le port) ou se faire passer pour la TV auprès d'un expert qui s'y connecte (d'où la vérification de l'empreinte de clé d'hôte de la TV côté expert). Réponse : révoquer ce tunnel, la TV refait une rotation de clé avec son activation.
- **Si la clé d'un expert fuit** : l'attaquant peut tenter de se connecter aux sshd des TV enrôlées (elles doivent donc avoir leur propre authentification forte : clé, pas de mot de passe par défaut). Réponse : nouvelle liste d'experts signée sans cette clé.
- **Si le serveur est compromis** : l'attaquant lit les clés publiques et les codes d'appareil, peut ajouter des lignes aux fichiers `authorized_keys` et donc ouvrir des accès. Il **ne peut pas** produire une liste d'experts valide (clé de signature hors ligne) : le backend refuse toute liste non signée ; mais un attaquant qui écrit directement le fichier `experts_authorized_keys` contourne le backend. La barrière réelle est donc le sshd des TV (authentification propre à la TV, seule la clé des experts) : **la TV ne doit accepter que les clés des experts signées par le propriétaire** (liste récupérée sur `GET /api/v1/tunnel/experts`, signature vérifiée par la TV avec la clé de mise à jour).
- **Ce qui n'est pas protégé** : un enrôlement valide ne prouve pas que l'appareil est « honnête » (il prouve qu'il détient une activation signée) ; une activation d'essai donne donc aussi un tunnel. Un tunnel ouvert à un appareil dont la licence est expirée reste ouvert tant qu'il n'est pas révoqué (la console permet de révoquer).

## 5. Mode d'emploi (runbook)

1. **Déployer** (une fois) : suivre `ops/tunnel/README.md` (simulation `setup.sh`, `Port 22` + `Port 2200`, `ufw allow 2200/tcp`, ne pas ouvrir 22100-22999, empreinte de la clé d'hôte, variables du backend, `CASTBRIDGE_TUNNEL_ENABLED=true`).
2. **Enrôler une TV** : automatique, par la TV, avec son activation et sa clé SSH (`POST /api/v1/tunnel/enroll`). Vérifier dans « Tunnels TV » : ligne ajoutée, port attribué, « connectée » après le premier sondage.
3. **Se connecter** : copier la commande de la console, `ssh -J cbexpert@bridge.sti-cm.com:2200 -p <port> tv@127.0.0.1`. Redirection de port : ajouter `-L 8080:127.0.0.1:8080`.
4. **Ajouter ou retirer un expert** : sur la machine du propriétaire, hors ligne, produire une nouvelle liste signée (`generatedAt` strictement plus récent), la déposer dans `{storage-dir}/tunnel/experts.json`, puis « Recharger la liste des experts » (ou attendre un cycle). Vérifier le message « Acceptée » et le nombre d'experts.
5. **Révoquer une TV** : « Révoquer le tunnel », puis `ops/tunnel/kick.sh <port> --apply`. Le port reste réservé à l'appareil ; « Rétablir » le rouvre.
6. **Rotation de la clé SSH d'une TV** : la TV génère une nouvelle clé et rappelle `enroll` avec la même activation et la nouvelle clé ; le port ne change pas, l'ancienne clé disparaît. Les rotations sont dans le journal.
7. **Plage de ports épuisée** : élargir `port-range` (les ports existants restent attribués) et ouvrir rien de plus au pare-feu.

## 6. Limites

- **Ligne des experts** : une option `permitopen` par TV ; les anciens OpenSSH (avant 8.8) ignorent une ligne de plus de 16 384 octets, soit environ 550 TV. Le backend écrit un avertissement près de la limite. Au-delà : OpenSSH récent (limite relevée), ou un compte expert par groupe de TV, ou `PermitOpen 127.0.0.1:*` dans `sshd_config` avec le pare-feu local comme barrière (moins strict).
- **Plage de ports** : 900 TV par défaut (22100-22999) ; un port n'est jamais libéré.
- **Révocation** : prend effet à la prochaine authentification ; une session déjà ouverte se coupe avec `kick.sh`.
- **Sondage** : « connectée » signifie qu'un port écoute sur le serveur, pas que le sshd de la TV répond ; en conteneur, il faut `listening.sh` ou un accès à la boucle locale de l'hôte.
- **Une seule instance du backend** (limite par IP et verrou d'enrôlement en mémoire).
- **IPv4 de bouclage** seulement (`127.0.0.1`).
- **Client TV et outil d'experts** (signature de la liste) : réalisés par ailleurs ; le contrat est celui décrit ici.
