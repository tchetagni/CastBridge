# w20-14 — Délégation de siège téléphone par la TV (voucher `cbj1`) — **NON RETENU par décision du propriétaire 2026-10-04**

> **Statut : RETIRÉ. Ne pas exécuter.** Décision du propriétaire (2026-10-04, verbatim) : « Pas de délégation en phase 2. Seule une TV connectée à internet joue. Le pire des modes de connexion est la passerelle bluetooth ». Fichier gardé pour mémoire.
> **Modèle : —** · **Groupe : —** · **Jauge : 0** (non exécuté)

## Ce qui avait été proposé (2026-10-04, pour mémoire)
Permettre à un téléphone synchronisé à une TV activée de rejoindre une salle Internet **directement** (sans passer par sa TV), comme **siège délégué** de sa TV : (a) voucher autonome `cbj1` signé hors ligne par la TV (clé d'installation) — écarté dès l'analyse : la clé d'installation n'est pas certifiée par l'activation et `TvProof` remet l'activation brute aux téléphones appariés, donc un téléphone désapparié pourrait fabriquer des vouchers ; (b) **délégation vivante** : la TV, sur sa connexion authentifiée au service, enregistre l'empreinte d'un secret de siège par téléphone (≤ 8 par TV), le téléphone entre par une preuve HMAC liée à un nonce du service, le siège meurt quand la TV se déconnecte, est révoquée ou retire le téléphone.

## Pourquoi ce n'est pas fait
La règle retenue est plus simple et plus sûre : **le seul client du service est une TV activée connectée à Internet** (box, point d'accès Wi-Fi, ou passerelle Bluetooth du téléphone) ; les téléphones sont des **joueurs locaux relayés** par leur TV ; aucun téléphone ne se connecte jamais au service. Voir `docs/coordination/DESIGN-W20-AMENDEMENT-TV-SEULEMENT-2026-10-04.md` (§ 1, § 4, § 10) et les cahiers du POC `sonnet-w20-04b-poc-join-tv-seulement-relais.md`, `sonnet-w20-05a-poc-client-tv-coeur.md`, `sonnet-w20-05-tv-wiring-play.md` (amendé).
