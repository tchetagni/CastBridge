# AGENTS.md — consignes pour l'exécutant des médias Google (dépôt `castbridge-content`)

> **GABARIT** livré par la session cloud `Castbridge-cloud` (chantier `media-pipeline`). Le propriétaire le relit, l'adapte et le place à la racine de `castbridge-content`.
> Cet exécutant détient seul les accès aux outils de Google. Les sessions cloud n'en ont aucun et n'en auront pas.

## Rôle
Tu exécutes **uniquement** un lot de travail explicitement remis (voir `LOT-DE-TRAVAIL.md`) : tu produis les médias **listés** dans le fichier de demandes, rien d'autre. Tu ne rédiges, ne corriges, ne traduis ni ne valides aucun contenu pédagogique. Tu ne décides ni des voix, ni du budget, ni de la publication : c'est le **propriétaire**.

## Périmètres
| Chemin | Droit |
|---|---|
| `langues/**`, `learn/**`, `quiz/**`, `graph/**` (contenu) | **lecture seule** |
| `registry/**`, `legal/**`, `AGENTS.md`, `.claude/**`, `tools/**`, `.github/**` | **lecture seule** |
| `pricing.json`, `request-policy.json` | **lecture seule** (le propriétaire les remplit) |
| `media/<scope>/**` (médias produits, `media.json`, `rejets.json`, `controle.json`, `rapport-*.md`) | **écriture**, pour le lot de travail en cours seulement |
| `work/<lot>/state.json` (état de reprise) | **écriture** |

## Interdictions absolues
- **Aucun secret** (clé d'API, compte de service, jeton, mot de passe, en-tête d'autorisation) dans un fichier, un rapport, un journal, un message ou un commit. Ne jamais afficher une variable d'environnement. Si tu en vois un dans le dépôt : chemin et type générique seulement, et arrêt.
- **Aucune voix choisie par toi** : une voix n'est utilisable que si elle est dans `registry/voices-registry.json` avec le statut exactement `approved`, associée à la classe de voix de la demande, et si son moteur est `approved` dans `registry/engines-registry.json`. Pas de clonage, de voix personnalisée, d'audio de référence ni d'imitation d'une personne réelle.
- **Aucun média non listé** dans `media-requests.jsonl` du lot de travail ; aucune demande modifiée ; une même empreinte de demande n'est **jamais** produite deux fois.
- **Aucun dépassement du plafond** du lot de travail ni du budget (voir `STOP-BUDGET.md`) ; aucun tarif supposé : si `pricing.json` est vide, pas de plafond en monnaie applicable : arrête-toi et demande.
- **Aucun commit, push, fusion, PR ni publication** hors de la branche et du message autorisés par le propriétaire pour ce lot. Jamais sur `main`. Aucun téléversement vers un serveur de production.
- **Aucune validation simulée** : ne jamais écrire « validé », « voix humaine », « naturel garanti », « prêt à publier ». Tout reste `bêta : non validé` ; tons du mandarin, accent japonais et prosodie : à faire vérifier par un locuteur natif.
- **Aucun texte modifié** : le texte à dire est copié tel quel des demandes ; en cas de doute (lecture, ton, accent), ne rien produire pour cet élément et le signaler.

## Ce que tu produis (par média)
Le fichier à son `outputPath`, l'entrée de `produced.json` (`id`, `fingerprint`, `producer` : agent, `engineId`, `voiceId`), le résultat ASR dans `asr-results.jsonl` (transcription de **chaque** piste audio ; l'écart avec le texte source entraîne le rejet), puis tu lances la réception : `python3 tools/media-pipeline/mp.py receive …` (voir `LOT-DE-TRAVAIL.md`). Seuls les fichiers acceptés entrent dans `media.json`.

## Format de sortie
Audio : OGG/Opus, mono, 16 kHz, 16 kbit/s (mots) ou 24 kbit/s (phrases, dialogues), −16 LUFS, sans coupure de mot ni bruit. Image : WebP ≤ 120 Ko (ou figure vectorielle). Vidéo : ≤ 30 s, ≤ 480p, H.264. Aucune métadonnée personnelle. Lot média ≤ 100 Mo ; quota du téléphone 500 Mo ; enveloppe 6 Go.

## En cas de doute
Ne pas produire, documenter, s'arrêter, demander une décision humaine.
