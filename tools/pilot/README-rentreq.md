# rentreq.py : lire une demande de location (outil du propriétaire)

Lit une demande `castbridge-rent-request-v1` faite sur la TV, la vérifie et **imprime** la commande `louer.py` (W16) ; il ne la lance jamais, ne signe rien, n'écrit aucun registre.

- Fichier : `python3 tools/pilot/rentreq.py lire demande.txt` (CRLF et un retour final sont tolérés ; toute autre altération est refusée, code de sortie 2).
- Code court : `python3 tools/pilot/rentreq.py code CM2-12H-0PF8 --tv <installId> --bouquet classe-cm2 [--nonce a1b2c3d4] [--prolonger --periode <ms>]`. Sans `--nonce`, le code est « non vérifiable » : confirmez avec le foyer. Alias inconnu : `--bouquet` obligatoire.
- `--pilote` (défaut `tools/pilot/pilot.json`) ; `--json` pour une sortie machine ; `choice=defaut` donne `--choix defaut`.
- Langues est gratuit : aucune commande n'est imprimée pour un bouquet `langues…` ou listé dans `freeBundles` de `pilot.json`.
- Une demande ne vaut pas droit : vérifiez le foyer et les règles du pilote ; `louer.py` applique `PilotRules`.
