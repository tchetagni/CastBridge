# Bluetooth plug and play (téléphones de confiance)

Objectif : le téléphone trouve et pilote la TV par Bluetooth, sans IP ni PIN.

## Sécurité
- Un téléphone n'est « de confiance » qu'après (1) l'appairage Android (comparaison de code sur les deux écrans) et (2) « Autoriser <nom> à piloter cette TV ? » validé à la télécommande, dans la fenêtre « Ajouter un téléphone » (2 min, ouverte par le propriétaire, un téléphone par ouverture, « Refuser » présélectionné, 3 refus = blocage 10 min).
- Sockets RFCOMM sécurisés uniquement. Le pair est identifié par l'adresse du socket (clé d'appairage), jamais par ce qu'il écrit ; la TV vérifie aussi qu'Android le dit toujours appairé.
- CBTH (HELLO) : un pair non approuvé reçoit 1 octet d'erreur, jamais nom, IP ni jeton. Un pair de confiance reçoit nom de la TV, version, IP:port, et un jeton propre au téléphone (256 bits, 12 h, renouvelé à mi-vie, stocké haché côté TV, révoqué avec le téléphone). Le PIN n'est jamais transmis.
- HTTP : en-tête `X-CB-Token`. Le jeton n'ouvre pas `/api/ssh*`, `/api/apk/install`, `/api/update/install` (PIN seulement).
- RFCOMM CBT1/CBTN/CBTR/passerelle : un pair de confiance envoie `------` dans le champ PIN, ignoré pour lui ; pour tout autre pair c'est un PIN faux (compté par le verrouillage). Le client Mac et les téléphones à PIN ne changent pas.
- Aucune télémétrie ajoutée ; l'adresse Bluetooth ne quitte jamais l'appareil (fichiers exclus des sauvegardes).

## Limites connues
- Jeton en clair sur le Wi-Fi local (HTTP) : rejouable par quelqu'un sur le même réseau pendant sa durée de vie.
- Téléphone volé et déverrouillé : accès jusqu'au « Retirer » sur la TV.
- Android exige un OK à la télécommande pour rendre la TV visible (boîte système).
