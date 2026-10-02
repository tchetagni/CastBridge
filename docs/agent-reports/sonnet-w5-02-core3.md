# sonnet-w5-02-core3 : correctifs de l'audit Opus final du porte-jetons (coeur)

Branche `claude/sonnet-w5-02-core3` (depuis integration/agents c743d9a). Coeur seulement, non branche. Aucun secret ; graines de test issues de textes publics.

## Corrections
1. MAJEUR (rejeu du bon d'ouverture) : `Mark` porte `grantSeq`; format `opened=<fp>|<chain>|<ms>|<grantSeq>` strict (4 champs, entiers canoniques, sous le MAC ; l'ancien format a 3 champs n'est plus une marque). `reopen` rend STALE si une marque valide existe et `g.grant <= grantSeq` (couvre « meme bon » : marque finale grantSeq == grant, et tout bon plus ancien), que le fichier soit BROKEN ou LOST.
2. Annulation de toute la reprise seulement si une marque valide existait avant (premiere ouverture avec marque non inscriptible : ouvre quand meme, note `MARK_RECREATE_FAILED`).
3. Ordre coupure de courant : deplacement a cote, puis marque PROVISOIRE (`grantSeq = grant-1`, pour que le MEME bon reste accepte apres coupure), puis nouveau fichier, puis marque finale (`grantSeq = grant`). Echec du fichier : ancienne marque restauree. Si la marque finale echoue, la provisoire reste coherente avec le fichier et est relevee a la relecture suivante (`openGrant` du fichier).
4. `broken-new` existant est renomme en `broken-<n+1>` (jumeau `.bak` compris) avant tout `moveAside` ; echec de renommage = refus sans rien perdre.
5. Verification de la marque une seule fois par texte (retour anticipe du cache) : une lecture ne reecrit jamais une marque non inscriptible (teste par compteur).
6. `wallet.lock` impossible a ouvrir/verrouiller : message dans un `WalletLog` injectable (constructeur et `of`), lectures permises, toute ecriture (depense, accuse, reprise, marque) refusee.

## Vecteurs (tools/activation/tokens-vectors.json, 82 cas)
Changement voulu : `checkMark` accepte `grantSeq`, `markWrite` aussi (defaut 0) ; `wallet-opening-voucher-reopens-an-empty-wallet` verifie `grantSeq: 1` (et ses lignes de fichier sont inchangees). Nouveaux : `wallet-same-voucher-replayed-after-broken-is-refused`, `wallet-same-voucher-replayed-after-lost-is-refused`, `wallet-older-grant-seq-is-refused`. Tous les autres vecteurs inchanges. Miroirs w5-05 : regenerer/rejouer, et gerer `grantSeq` dans les etapes `markWrite`/`checkMark`.

## Tests
TokenReopenTest : 9 -> 20 tests (boucle hors ligne BROKEN/LOST, rejeu de reponse en cache par passerelle, grant plus ancien, premiere ouverture sans marque, coupure avant marque / apres marque provisoire / apres fichier (guerison) / sans marque, `broken-new`, memoisation, verrou). Format strict de la marque couvert. `:core:test` complet vert.

## Risques
- Le serveur doit numeroter les bons d'ouverture au-dessus de tout bon deja emis a l'installation (sinon un nouveau bon legitime serait refuse STALE) : a verifier cote w5-05/w5-08.
- Une marque absente/fausse (root) reste tolerance J2 : le garde-fou est alors le serveur (`TOKEN_REPLAY`).
- Une coupure DANS l'ecriture atomique du fichier n'est pas simulee (SafeFile suppose atomique).
- Coupure entre marque provisoire et fichier : le numero de chaine saute (sans effet).
