# Joindre la TV depuis le Mac par la passerelle Bluetooth

## Pourquoi
Quand la TV n'est pas sur le Wi-Fi du téléphone (TV hors Wi-Fi, box absente, réseau isolé), le Mac
ne peut pas la joindre directement. Le téléphone, relié au Mac en USB (ADB) et appairé à la TV en
Bluetooth, sert de relais : CastBridge écoute sur 127.0.0.1 du téléphone et relaie vers la TV :
- 2222 : SSH de la TV (service « CastBridge SSH »), chiffré de bout en bout avec votre clé ;
- 18765 : API HTTP de la TV (service « CastBridge API »), les mêmes requêtes qu'en Wi-Fi.

## Étapes
1. Téléphone : CastBridge, onglet « CastBridge TV ». Si la TV ne répond pas sur le Wi-Fi, un bouton
   pleine largeur « Passerelle Bluetooth » apparaît sous la TV (« Pas de Wi-Fi ? Relier la TV par
   Bluetooth »). S'il n'y a qu'une seule TV appairée, un seul appui la démarre (API + SSH, boucle
   locale seulement). Sinon, « Choisir la TV et démarrer » ouvre Avancé > Bluetooth.
   Sans appairage : « Ajouter ma TV (Bluetooth) », puis valider sur la TV.
2. Avec la bascule automatique (activée par défaut, « Basculer sur Bluetooth quand le Wi-Fi est
   absent »), l'app démarre d'elle-même la passerelle API quand la TV est absente du Wi-Fi depuis
   8 s. L'état passe en orange : « Wi-Fi absent · liaison Bluetooth active ». La passerelle est
   arrêtée dès que la TV revient sur le Wi-Fi. Pour le Mac, touchez « Ajouter le SSH (pour le Mac) ».
3. Tant qu'elle tourne, une barre « Passerelle Bluetooth active vers … » (avec « Arrêter ») reste
   visible sur les autres onglets, et la carte de l'accueil affiche les lignes à taper, chacune
   avec « Copier ».
4. Mac (dossier du dépôt) :
   ```
   tools/remote/tv-tunnel.sh up          # vérifie l'écoute, fait les deux adb forward
   ssh -p 2222 tv@127.0.0.1 'cbdev status'
   curl -H 'X-CB-Pin: <code>' http://127.0.0.1:18765/api/hello
   tools/remote/tv-tunnel.sh status      # redirections, écoute, réponse de la TV
   tools/remote/tv-tunnel.sh down        # retire les redirections
   ```
   À la main : `adb forward tcp:2222 tcp:2222` et `adb forward tcp:18765 tcp:18765`.
   Plusieurs téléphones branchés : ajoutez le numéro de série (`adb devices`).

## Si ça bloque
- « SSH : … Service introuvable ou fermé par la TV » : sur la TV, MENU > Administration > activer SSH.
- `/api/hello` ne répond pas : TV allumée, à portée Bluetooth, CastBridge-TV ouverte,
  « API par Bluetooth » active (MENU > Administration).
- Débit Bluetooth ~100-300 ko/s : bien pour commander, lent pour de gros fichiers.

## Sécurité
- Le SSH exige une clé autorisée sur la TV ; la passerelle ne voit que des octets chiffrés.
- L'API exige le code de la TV ou un jeton : remplacez `<code>` vous-même. Ni l'app ni le
  script n'affichent, ne copient ni ne journalisent le code.
- Boucle locale par défaut : rien n'écoute sur les réseaux du téléphone. Exposer le SSH sur le
  réseau reste un choix explicite (Avancé > Bluetooth, avec avertissement) ; l'API, jamais.
- ADB donne au Mac un accès complet au téléphone : n'autorisez le débogage USB que sur votre
  propre Mac. `adb forward` n'ouvre les ports que sur 127.0.0.1 du Mac.
