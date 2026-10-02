# ops/tunnel : tunnels SSH inverses des CastBridge-TV

Fichiers MODÈLES pour l'administration à distance (voir `docs/REMOTE-TUNNEL.md` pour l'architecture, la menace et le mode d'emploi).
**Rien ici n'est appliqué automatiquement** : ni le serveur de production, ni sshd, ni le pare-feu ne sont touchés par ces fichiers tant que le propriétaire n'a pas lancé lui-même `setup.sh --apply`.

| Fichier | Rôle |
|---|---|
| `sshd_config.d/castbridge-tunnel.conf` | blocs `Match` : compte `cbtunnel` (TV, `-R` vers `127.0.0.1` seulement) et compte `cbexpert` (experts, `-J`/`-L` seulement), sans shell ni TTY |
| `cb-authkeys.sh` | `AuthorizedKeysCommand` : lit les fichiers `authorized_keys` écrits par le backend (le propriétaire du fichier n'a pas d'importance) |
| `setup.sh` | crée les comptes, installe les fichiers ; **simulation par défaut, `--apply` requis** ; ne modifie jamais `sshd_config` ni le pare-feu |
| `listening.sh` | liste des ports à l'écoute sur la boucle locale (pour un backend en conteneur, voir ci-dessous) |
| `kick.sh` | coupe la session SSH ouverte d'une TV révoquée (`--apply` requis) |
| `logrotate-castbridge-tunnel` | rotation des journaux propres aux tunnels |
| `fail2ban-castbridge-tunnel.local` | jail pour le port 2200 |

## Ordre de déploiement (propriétaire, jamais en aveugle)

1. Lire `docs/REMOTE-TUNNEL.md` (surtout « Menace » et « Limites ») ; décider de l'information donnée aux clients.
2. `sudo ops/tunnel/setup.sh` (simulation) puis relire. Avec une session root ouverte : `sudo ops/tunnel/setup.sh --apply`.
3. Dans `/etc/ssh/sshd_config` : `Port 22` **et** `Port 2200` (jamais `Port 2200` seul). `sshd -t`, `systemctl reload ssh`, tester une nouvelle connexion sur 22 ET sur 2200 avant de fermer la session root.
4. Pare-feu : `ufw allow 2200/tcp`. **Ne pas ouvrir 22100-22999** (les ports des tunnels n'écoutent que sur `127.0.0.1` du serveur ; ils doivent rester fermés à Internet).
5. Empreinte de la clé d'hôte, à donner au backend (les TV la vérifient) : `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` puis `CASTBRIDGE_TUNNEL_HOST_KEY_FINGERPRINT=SHA256:…`.
6. Backend : le dossier `{storage-dir}/tunnel/` doit être le même que `/var/lib/castbridge/tunnel` de l'hôte (volume Docker monté sur ce chemin, ou `CB_TUNNEL_DIR` pour `setup.sh`/`cb-authkeys.sh`). Variables :
   `CASTBRIDGE_TUNNEL_ENABLED=true`, `CASTBRIDGE_TUNNEL_HOST`, `CASTBRIDGE_TUNNEL_SSH_PORT`, `CASTBRIDGE_TUNNEL_PORT_RANGE=22100-22999`, `CASTBRIDGE_TUNNEL_AUTHORIZED_KEYS_FILE`, `CASTBRIDGE_TUNNEL_EXPERTS_FILE`, `CASTBRIDGE_TUNNEL_EXPERTS_AUTHORIZED_KEYS_FILE`, `CASTBRIDGE_TUNNEL_PROBE_SECONDS=60`.
7. Liste des experts : l'outil du propriétaire (hors ligne) produit `experts.json` signé ; la déposer dans `{storage-dir}/tunnel/experts.json`. Le serveur ne signe jamais cette liste.

## Backend en conteneur : le sondage

Les ports de tunnel écoutent sur la boucle locale de l'**hôte** ; un conteneur ne la voit pas. Deux solutions :

- `CASTBRIDGE_TUNNEL_LISTENING_PORTS_FILE=/data/apk/tunnel/listening-ports` (le dossier partagé) et le cron installé par `setup.sh` (`listening.sh`, chaque minute) : le backend lit la liste au lieu de se connecter ;
- ou backend en réseau hôte (déconseillé ici : l'API est publiée sur `127.0.0.1` seulement, voir `backend/docker-compose.yml`).

## Côté TV (contrat, pour l'équipe Android)

La TV se connecte en SORTANT, avec sa propre clé ed25519, et demande **explicitement** `127.0.0.1` comme adresse d'écoute (sinon `permitlisten` ne correspond pas : OpenSSH traite « localhost » autrement que `127.0.0.1`) :

```
ssh -N -i <clé de la TV> -p 2200 -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -o ServerAliveCountMax=3 \
    -R 127.0.0.1:<port>:127.0.0.1:2222 cbtunnel@bridge.sti-cm.com
```

`<port>` et `hostKeyFingerprint` viennent de `POST /api/v1/tunnel/enroll`. La TV vérifie l'empreinte de la clé d'hôte (pas de `StrictHostKeyChecking=no`).

## Se connecter (experts et propriétaire)

```
ssh -J cbexpert@bridge.sti-cm.com:2200 -p <port de la TV> tv@127.0.0.1
```

La page « Tunnels TV » de la console d'administration affiche la commande prête à copier pour chaque TV.

## Journaux et surveillance

- sshd : `journalctl -u ssh -g cbtunnel` (connexions des TV), `-g cbexpert` (accès des experts : la clé utilisée est identifiée par le commentaire `expert-<id>` de sa ligne : `LogLevel VERBOSE` si l'on veut l'empreinte dans le journal).
- backend : lignes `tunnel audit event=… device=ABCD-****` (aucune clé, aucun jeton) et table `tunnel_audit` (page « Tunnels TV », journal).
- fail2ban : copier `fail2ban-castbridge-tunnel.local` dans `/etc/fail2ban/jail.d/` puis `fail2ban-client reload`.
- limite des lignes : voir `docs/REMOTE-TUNNEL.md`, « Limites » (ligne `authorized_keys` des experts).
