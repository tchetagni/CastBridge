# Lot de travail (format d'échange entre le propriétaire, Claude Code et l'exécutant Google)

Un lot de travail = **un dossier `work/<lot>/`** :

| Fichier | Qui l'écrit | Contenu |
|---|---|---|
| `media-requests.jsonl` | `mp.py requests` (Claude Code) | les médias à produire : `id`, `kind`, `lang`, texte exact ou description, classe de voix, contraintes, `outputPath`, `fingerprint`, priorité |
| `plan.json` | `mp.py plan` (Claude Code) | demandes **retenues** sous plafond, demandes reportées et motif, résumé chiffré ; arrêt si plafond |
| `pricing.json` (copie) | le propriétaire | tarifs Google en vigueur ; **vide = coût inconnu** |
| `AUTORISATION.md` | **le propriétaire** | date, plafonds (`maxCost`, `maxChars`, `maxImages`, `maxVideoSeconds`, `maxRequests`), voix et moteurs approuvés, branche et message de commit autorisés ; **sans cela, ne rien produire** |
| `produced.json` | l'exécutant | pour chaque média produit : `id`, `fingerprint`, `file`, `producer` {`agent`, `engineId`, `voiceId`} |
| `asr-results.jsonl` | l'exécutant | pour chaque piste audio : `id`, `fingerprint`, `engine`, `transcript`, `checkedOn` |
| `state.json` | `mp.py record` | empreintes produites et dépense cumulée (reprise) |
| `media/<scope>/…` | l'exécutant | les fichiers produits, à leur `outputPath` |
| `reception/` | `mp.py receive` | `media.json` (acceptés), `rejets.json`, `attente.json`, `controle.json`, `rapport-reception.md` |

Procédure :
1. Lire `AUTORISATION.md`. Absente ou périmée → **ne rien produire**.
2. Ne traiter que les ids de `plan.json › retenues`, dans l'ordre. Une empreinte déjà dans `state.json` est sautée.
3. Après **chaque** média produit : `mp.py record <demandes> <state.json> <id>` (la reprise après interruption repose là-dessus).
4. À la fin (ou à l'arrêt) : `mp.py receive …`, puis rapport au propriétaire : produits, rejetés, en attente d'ASR, dépense, ce qui n'a pas pu être vérifié.
5. Échantillon d'abord : **5 pistes au plus par langue et par voix**, puis arrêt jusqu'à la validation du propriétaire.
