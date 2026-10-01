# Bluetooth plug and play (téléphones de confiance)

Objectif : le téléphone trouve et pilote la TV par Bluetooth, sans IP ni PIN.

## Sécurité
- Un téléphone n'est « de confiance » qu'après (1) l'appairage Android (comparaison de code sur les deux écrans) et (2) « Autoriser <nom> à piloter cette TV ? » validé à la télécommande, dans la fenêtre « Ajouter un téléphone » (2 min, ouverte par le propriétaire, un téléphone par ouverture, « Refuser » présélectionné, 3 refus = blocage 10 min).
- Sockets RFCOMM sécurisés uniquement. Le pair est identifié par l'adresse du socket (clé d'appairage), jamais par ce qu'il écrit ; la TV vérifie aussi qu'Android le dit toujours appairé.
- CBTH (HELLO) : un pair non approuvé reçoit 1 octet d'erreur, jamais nom, IP ni jeton. Un pair de confiance reçoit nom de la TV, version, IP:port, et un jeton propre au téléphone (256 bits, 12 h, renouvelé à mi-vie, stocké haché côté TV, révoqué avec le téléphone). Le PIN n'est jamais transmis.
- HTTP : en-tête `X-CB-Token`. Le jeton n'ouvre pas `/api/ssh*`, `/api/apk/install`, `/api/update/install` (PIN seulement).
- RFCOMM CBT1/CBTN/CBTR/passerelle : un pair de confiance envoie `------` dans le champ PIN, ignoré pour lui ; pour tout autre pair c'est un PIN faux (compté par le verrouillage). Le client Mac et les téléphones à PIN ne changent pas.
- Aucune télémétrie ajoutée ; l'adresse Bluetooth ne quitte jamais l'appareil (fichiers exclus des sauvegardes).

## Service « CastBridge API » (tout par Bluetooth)
- Troisième service RFCOMM de la TV, UUID `7c5e3b9a-4d2f-4c61-9b0e-cb0000000003` : un tunnel d'octets vers l'API HTTP locale (détails : `docs/ADMIN.md` §11). Socket sécurisé, appareils appairés seulement.
- **Règle de confiance** : un appareil appairé est admis si l'interrupteur « API par Bluetooth » de la TV est actif (défaut : actif, comme le service fichiers) **ou** si c'est un **téléphone de confiance** (inscrit ici ET toujours appairé) ; sinon la TV répond « refusé » (octet d'état 2) et le journalise. Le serveur HTTP demande ensuite le jeton du téléphone de confiance ou le PIN (échecs comptés par appareil `bt:<adresse>`, jamais contre le Wi-Fi ni les autres appareils). Le jeton n'ouvre toujours pas `/api/ssh*`, `/api/apk/install`, `/api/update/install`.
- Retirer un téléphone de confiance (ou le dés-appairer) lui retire le droit d'utiliser l'API par Bluetooth dès que l'interrupteur est coupé ; interrupteur actif, il reste utilisable avec le PIN comme tout appareil appairé.

## Limites connues
- API par Bluetooth active par défaut : un appareil appairé (donc approuvé à l'appairage sur la TV) qui connaît le PIN peut tout faire par ce canal, comme par le Wi-Fi ; couper l'interrupteur pour ne garder que les téléphones de confiance.
- Jeton en clair sur le Wi-Fi local (HTTP) : rejouable par quelqu'un sur le même réseau pendant sa durée de vie.
- Téléphone volé et déverrouillé : accès jusqu'au « Retirer » sur la TV.
- Android exige un OK à la télécommande pour rendre la TV visible (boîte système).
