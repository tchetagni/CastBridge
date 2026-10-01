# cbt-rfcomm (macOS)

Client Bluetooth RFCOMM bas niveau pour CastBridge-TV (framework IOBluetooth), sans passer par le téléphone.

    swiftc -O -framework IOBluetooth main.swift -o cbt-rfcomm
    ./cbt-rfcomm list  74:24:CA:06:00:46                 # requête SDP fraîche : services et canaux RFCOMM (fichiers, API, SSH)
    ./cbt-rfcomm send  74:24:CA:06:00:46 <PIN> film.mp4   # envoi (protocole CBT1, reprenable)
    ./cbt-rfcomm proxy 74:24:CA:06:00:46 18765 api        # 127.0.0.1:18765 -> API HTTP de la TV, par Bluetooth
    ./cbt-rfcomm proxy 74:24:CA:06:00:46 2222 ssh         # 127.0.0.1:2222  -> serveur SSH de la TV, par Bluetooth

Pré-requis : Mac appairé avec la TV, app CastBridge-TV lancée (les services ne sont publiés qu'à ce moment ; « CastBridge SSH » seulement quand SSH est activé sur la TV).

## `proxy` : tout par Bluetooth, sans téléphone
`proxy <adresse> <port> api|ssh [--channel N]` écoute sur **127.0.0.1 seulement** et relie chaque connexion TCP à une liaison RFCOMM neuve vers le service de la TV.
Il ne demande ni n'affiche **aucun code** : le code de la TV est envoyé par votre client (`curl -H`), et la TV le vérifie, avec un verrouillage propre à ce Mac.
Le canal RFCOMM est cherché dans l'ordre : `--channel`, requête SDP (par UUID puis par nom), canal mémorisé (`~/.cache/cbt-rfcomm.json`), nouvelle requête SDP, puis sondage des
canaux 1 à 30 (l'API répond un octet d'état 0 à 4, le SSH sa bannière « SSH- »). Chaque échec est expliqué en français sur la sortie d'erreur ; pour l'API, le client `curl`
reçoit aussi une réponse `502` avec la raison.

    curl -H "X-CB-Pin: $PIN" http://127.0.0.1:18765/api/hello
    ssh -p 2222 tv@127.0.0.1

`bt-api.sh` regroupe les usages courants (variable `CB_PIN`, jamais dans une URL ni affichée) : `hello`, `info`, `library`, `upload <fichier>` (reprenable),
`install <nom.apk>`, `ssh-key <fichier.pub>` (active SSH et inscrit la clé), `screenshot <sortie.png>`, `raw GET|POST <chemin>`. Détails et règle de confiance : `docs/ADMIN.md` §11.

Linux : `python3 tools/bt-ssh-bridge.py <adresse> --service api --listen 18765` (même usage) ; sans option, il reste le `ProxyCommand` SSH.

**Non testé** : `proxy` (et ses ajouts à `list`) n'a pas pu être compilé ni essayé dans l'environnement cloud (pas de macOS, pas de matériel) : à compiler et essayer sur le Mac.
Mesure de `send` du 29/09/2026 : 5 Mo en 46 s (≈110 ko/s), SHA-256 identique à l'arrivée.
