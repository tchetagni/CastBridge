# cbt-rfcomm (macOS)

Client Bluetooth RFCOMM bas niveau pour CastBridge TV (framework IOBluetooth), sans passer par le téléphone.

    swiftc -O -framework IOBluetooth main.swift -o cbt-rfcomm
    ./cbt-rfcomm list 74:24:CA:06:00:46                 # requête SDP fraîche : services et canaux RFCOMM
    ./cbt-rfcomm send 74:24:CA:06:00:46 <PIN> film.mp4   # envoi (protocole CBT1, reprenable)

Pré-requis : Mac appairé avec la TV, app CastBridge TV lancée (le service n'est publié qu'à ce moment).
Mesure du 29/09/2026 : 5 Mo en 46 s (≈110 ko/s), SHA-256 identique à l'arrivée.
