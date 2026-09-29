# Administrer CastBridge TV à distance (guide pour humains et agents)

CastBridge TV (module `:receiver`) est une app Android TV qui expose une API HTTP JSON sur le port **8765**.
Tout reste dans le stockage privé de l'app (`getExternalFilesDir("videos")`) : aucune permission de stockage,
aucun contournement des protections d'Android.

## 1. Trouver la TV

- mDNS : service `_castbridge._tcp`, attribut TXT `role=receiver` (le serveur PC annonce `role=server`, à ignorer).
  - macOS : `dns-sd -B _castbridge._tcp` puis `dns-sd -L "<nom>" _castbridge._tcp`
  - Linux : `avahi-browse -rt _castbridge._tcp`
- Ou l'adresse IP affichée sur l'écran d'attente de la TV (`192.168.x.y:8765`).
- Wi-Fi Direct (sans routeur) : la TV est à `192.168.49.1:8765` une fois connecté à son réseau (voir §6).
- Vérification sans PIN : `curl http://TV:8765/api/hello` -> `{"app":"castbridge-tv","v":"0.3","pinRequired":true}`

## 2. Obtenir le PIN

Un code à 6 chiffres est généré au premier lancement de l'app et **affiché sur l'écran d'attente de la TV**.
Il n'existe volontairement aucun moyen de le lire à distance. Il est conservé dans les préférences privées de l'app
et n'est jamais écrit dans les journaux.

Toutes les routes sauf `GET /` (page web) et `GET /api/hello` exigent le PIN :
en-tête `X-CB-Pin: 123456` (recommandé) ou paramètre `?pin=123456`.
Après **5 échecs** depuis une même IP, cette IP est verrouillée **60 s** (même le bon PIN est refusé pendant ce temps).
Un agent qui reçoit `401` ne doit pas réessayer en boucle : voir `retryAfter`.

```sh
export TV=http://192.168.0.117:8765 PIN=123456
alias tv='curl -sS -H "X-CB-Pin: $PIN"'
```

## 3. Routes (JSON, encodage UTF-8)

Les noms de fichiers sont sans `/`, `\`, ni `.part` final, 200 caractères max. Les erreurs ont la forme
`{"error":"..."}`.

| Méthode et route | Effet | Réponse (200) |
|---|---|---|
| `GET /` | page web d'administration (sans PIN, ne contient aucune donnée) | HTML |
| `GET /api/hello` | identification (sans PIN) | `{"app","v","pinRequired"}` |
| `GET /api/info` | fichiers, espace libre, lecteur | `{"files":[{"name","size"}],"free":octets,"player":{"state":"idle\|playing\|paused\|ended\|error","name","pos":ms,"dur":ms}}` |
| `GET /api/sysinfo` | infos appareil | `{"model","android","ip","battery":%\|null,"charging":bool\|null,"uptime":ms,"app","volume":0-100\|null}` |
| `POST /api/volume?pct=0..100` | volume média de la TV | comme `sysinfo` |
| `POST /api/restart` | redémarre **l'app** (pas la TV) | `{"restarting":true}` |
| `GET /api/part?name=` | octets déjà reçus d'un envoi | `{"name","length","done":bool}` |
| `PUT /upload/<nom>?offset=N&total=T` | ajoute les octets `[N,T)` (corps = octets restants, `Content-Length` obligatoire) | `{"name","length","done"}` |
| `POST /api/reset?name=` | efface le `.part` | idem `part` |
| `POST /api/play?name=&pos=ms` | lit un fichier présent | comme `info` |
| `POST /api/pause` `POST /api/resume` `POST /api/stop` | contrôle | comme `info` |
| `POST /api/seek?pos=ms` | saut | comme `info` |
| `POST /api/delete?name=` | supprime fichier et `.part` | comme `info` |
| `POST /api/rename?name=&to=` | renomme (409 si la cible existe) | comme `info` |
| `GET /api/usb` | état de l'import USB et volumes détectés | `{"running","message","volumes":[chemins]}` |
| `POST /api/usb/import` | copie les vidéos des clés détectées (dossier de l'app sur la clé) | comme `usb` |

Codes : `400` paramètre invalide, `401` PIN faux ou IP verrouillée, `404`, `405` (utiliser POST), `409` mauvais
offset (le corps donne `length`), `501` non supporté sur cet appareil, `507` espace insuffisant (moins de 100 Mo libres
après l'envoi).

## 4. Envoyer un fichier, reprise incluse

```sh
F=film.mp4; N=$(basename "$F"); T=$(stat -f%z "$F" 2>/dev/null || stat -c%s "$F")
# reprise : demander où en est la TV
OFF=$(tv "$TV/api/part?name=$N" | sed -E 's/.*"length":([0-9]+).*/\1/')
tail -c +$((OFF+1)) "$F" | tv -X PUT --data-binary @- -H "Content-Type: application/octet-stream" \
  "$TV/upload/$N?offset=$OFF&total=$T"
```

Si la connexion tombe, relancer les mêmes commandes : l'envoi repart de `length`. Le fichier apparaît sous son nom
définitif quand `length == total`. Un `409` signifie que l'offset envoyé n'est pas celui de la TV : reprendre à
`length`. Le même `.part` peut être repris par le canal Bluetooth ou par la page web.

Commandes courantes :

```sh
tv $TV/api/info                                  # inventaire + espace libre
tv -X POST "$TV/api/play?name=$N"                # lecture
tv -X POST "$TV/api/volume?pct=30"               # volume
tv -X POST "$TV/api/rename?name=a.mp4&to=b.mp4"
tv -X POST "$TV/api/delete?name=b.mp4"
tv $TV/api/sysinfo
```

## 5. Autres canaux (résumé)

- **Page web** : `http://TV:8765/` depuis un navigateur ; envoi par morceaux de 4 Mo, repris seul.
- **Bluetooth (RFCOMM)** : appairer le téléphone à la TV, envoi depuis l'app téléphone (onglet CastBridge TV > Bluetooth).
  Protocole `BtProtocol` (module `:core`) : `CBT1` + PIN + nom + taille, reprise au `.part` existant. Non pilotable en HTTP.
- **Clé USB / OTG** : touche MENU de la télécommande > « USB… » (scan des volumes, ou sélecteur de dossier système si la TV en
  a un), ou `POST /api/usb/import`. Le scan des volumes lit uniquement le **dossier de l'app sur la clé**
  (`<clé>/Android/data/castbridge.receiver/files/`), qu'un PC peut remplir ; un dossier quelconque de la clé n'est lisible que
  via le sélecteur système.

## 6. Wi-Fi Direct

Sur la TV : MENU > « Wi-Fi Direct : activer » (désactivé par défaut : créer un groupe peut perturber le Wi-Fi de la TV).
La TV affiche le nom du réseau (`DIRECT-CB-CastBridge`), le mot de passe et le PIN. Connecter le téléphone ou l'ordinateur à ce
réseau, puis utiliser `http://192.168.49.1:8765`.

## 7. Limites (non négociables)

- L'app tourne avec **son propre uid Android**, sans root : elle ne peut ni redémarrer la TV, ni changer les réglages système,
  ni écrire hors de son stockage privé, ni lire celui des autres apps. `POST /api/restart` ne relance que l'application.
- Le volume peut être ignoré par une TV à volume fixe (HDMI-CEC / ampli externe).
- La batterie est `null` sur une TV (pas de batterie).
- Sans PIN valide, aucun accès aux fichiers. Le PIN n'est jamais journalisé. Ne pas exposer le port 8765 sur Internet.
- **Accès SSH / shell** : demandé mais **non livré** dans cette version (voir README, section « Non livré »).

## 8. Pour un agent (checklist)

1. Découvrir la TV (mDNS ou IP) puis `GET /api/hello`.
2. Demander le PIN à l'humain (il est sur l'écran de la TV). Ne jamais le deviner.
3. Envoyer `X-CB-Pin` sur chaque appel ; sur `401` ne pas insister (verrouillage 60 s).
4. Avant un gros envoi : `GET /api/info` (`free`), puis `PUT /upload` avec reprise sur `length`.
5. Toujours confirmer avec l'humain avant `delete`, `rename` ou `restart`.
