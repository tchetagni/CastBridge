# Chaîne de production multimédia (outils Google, sous supervision)

> Décision du propriétaire : le multimédia (voix, images, courtes vidéos) est produit **avec les outils de Google**, par un exécutant (« Agy », sous la supervision de Claude Code) qui **seul détient les accès**. Les sessions cloud n'ont **aucun accès Google** et n'en auront pas : elles préparent **tout ce qui entoure l'appel**. Budget actuel **petit** : tout est **estimé avant** d'être produit. Outil : `tools/media-pipeline/` (Python 3, bibliothèque standard ; `ffmpeg`/`ffprobe` pour les contrôles). Copie utilisable dans `castbridge-content`.
> **Rien de ce document n'est un avis juridique.** Aucun secret, aucun prix, aucun choix de voix n'est dans le dépôt.

## 1. Flux de bout en bout
```
paquets de langue ──► mp.py requests ──► media-requests.jsonl ──► mp.py estimate ──► mp.py plan (plafonds, reprise)
                                                    │                                         │
                          registres + legal/ ◄──────┘                  VALIDATION DU PROPRIÉTAIRE (plan, puis échantillon de 5 pistes)
                                                                                           │
                                          exécutant Google (Agy) : produit, transcrit (ASR), mp.py record après chaque média
                                                                                           │
                                  mp.py receive : contrôles techniques + manifeste + ASR ──► media.json (acceptés) / rejets.json / attente.json
```
| Étape | Qui | Automatique ? |
|---|---|---|
| Choisir moteurs et voix, remplir `pricing.json`, valider les textes juridiques, **autoriser** un lot et un plafond | **propriétaire** | non |
| Générer les demandes, estimer, planifier sous plafond, recevoir et contrôler | Claude Code (cloud) | oui, sans Google |
| Appeler Google, produire, transcrire (ASR), `record` | exécutant (accès Google) | oui, **sur autorisation écrite** |
| Écouter, valider les tons du mandarin, l'accent du japonais, la prosodie | **locuteur natif** | non |

## 2. Demandes de médias (`mp.py requests <paquets> <sortie.jsonl>`)
Une ligne par média, **déterministe** : mêmes paquets = mêmes octets, quel que soit l'ordre des fichiers. Champs : `id` (celui de `m:<id>` du paquet), `kind` (audio / image / video), `lang`, `payload` (**texte exact** copié du paquet, jamais corrigé ; ou description de l'image), `voiceClass` (variété, genre, âge, registre, rôle : **jamais un nom de voix**), `constraints` (codec, débit 16 kbit/s pour un mot et 24 pour une phrase, 16 kHz mono, −16 LUFS, durée max…), `outputPath`, `source` (paquet, unité, élément), `priority`, **`fingerprint`**.
- **Empreinte** = SHA-256 de la demande *sans* chemin ni priorité : même demande = même fichier = **jamais produite ni facturée deux fois**. Texte modifié = autre empreinte.
- **Rien n'est deviné** : un texte à dire impossible à déterminer (audio d'un QCM **sans** le champ additif `text` de l'exercice, qui porte le mot ou la phrase exacts dits par l'audio ; dictée : `answers[0]`), une variété à choisir (anglais, espagnol), plus de trois voix dans un dialogue → la demande est **`blocked`** avec la raison, et n'est jamais produite. Un même `id` avec deux textes différents est un **conflit** signalé (code de sortie 1), jamais arbitré en silence : sur le paquet d'exemple `zh-a0-salut-fr`, `zh-nihao` est dit « 你好 » (vocabulaire) et « 你好！ » (réplique) : l'auteur du contenu doit trancher. Même `id`, **même texte** et contraintes différentes (un mot de vocabulaire repris par un QCM ou une dictée) : une seule demande, sur la contrainte la plus stricte, tracée dans `sharedWith`. Un exercice oral (`speak`) sans texte propre réutilise l'audio déjà demandé sous le même `id` si son modèle (note entre parenthèses ignorée) est la lecture ou le texte de cet audio : tracé dans `sharedWith` (`audio (réutilisé)`) ; sinon conflit (« réutilisation invérifiable » sans modèle ou sans lecture).
- **Politique** (`templates/request-policy.json`, modifiable) : variétés par défaut (zh-CN, ja-JP, de-DE, it-IT, fr-FR ; `null` pour l'anglais et l'espagnol = à choisir), registre « lent » pour A0-A1, genre par rôle (A/B différents : une voix par locuteur).
- **Priorité** (petit budget) : A0 à A2 d'abord (même une image d'A0 avant un B2), puis audio avant image avant vidéo, puis niveau, puis mots, phrases, exercices, dialogues entiers, histoires.

## 3. Estimation, plafonds, reprise
- `mp.py estimate` : caractères à synthétiser, images, secondes de vidéo (durée **maximale** demandée : estimation par excès) et **coût** d'après `pricing.json`. **Vide par défaut** : aucun tarif en dur ; un tarif manquant donne « **coût inconnu** », jamais zéro. Le propriétaire le remplit d'après la grille Google **en vigueur** (monnaie, date, référence).
- `mp.py plan` : tranche de demandes dans l'ordre de priorité, sous plafonds `--max-cost`, `--max-chars`, `--max-images`, `--max-video-seconds`, `--max-requests`. On **ne dépasse jamais** : la demande qui ne rentre plus est reportée. Codes de sortie : **0** tout rentre, **3** plafond atteint (arrêt), **2** refus (plafond en monnaie **sans** tarif connu : « aucune dépense sans tarif »).
- Reprise : `mp.py record` écrit `state.json` après **chaque** média ; une empreinte déjà enregistrée n'est ni refaite ni recomptée ; la dépense déjà faite compte dans les plafonds.
- Procédure d'arrêt : `templates/castbridge-content/STOP-BUDGET.md`.

## 4. Registres et gabarits juridiques
- `registry/engines-registry.json` : moteur, modèle, version, usage permis, conditions, **date de vérification**, preuve sous `legal/`, marquage synthétique exigé ?, statut. **Vide** à la livraison.
- `registry/voices-registry.json` : voix concrètes **choisies par le propriétaire** (`status: approved`), `mapping` classe de voix → voix. **Vide** à la livraison. `mp.py registries <moteurs> <voix> --requests …` valide les registres et **liste les classes de voix à faire choisir**. Refusés : voix absente, non `approved`, moteur non approuvé, clonage, voix personnalisée, audio de référence, imitation, type autre que « voix de catalogue préconstruite ».
- `legal/` : **quatre gabarits « à faire valider par un juriste »** (sorties générées, marquage synthétique, interdiction d'imiter une personne réelle, traçabilité) : des **questions**, pas des réponses. Aucun texte définitif.

## 5. Manifeste de provenance
Entrée par média, **champs additifs** à `MEDIA-MANIFEST.json` (MEDIA-POLICY.md) : `synthetic: true`, `statut: "bêta : non validé"`, moteur, `voiceId` (registre), classe de voix, `requestFingerprint`, `producedAt`, `sha256` et `bytes` **du fichier produit**, durée, codec, `family`, `licence`, `clonage_vocal: false`, `imitation_personne_reelle: false`, transcription et légende. **Aucun secret.** La **licence de sortie est provisoire** (`licenceStatus` : « à valider par un juriste ») : famille « libre » = `CC-BY-SA-4.0`, « réservé » = `CastBridge-original` (décisions du 2026-10-01, LANGUES.md § 13) ; une famille **ne se mélange pas** avec l'autre : le manifeste est rejeté si licence et famille divergent.

## 6. Contrôles à la réception (`mp.py receive`)
Sans Google, testés sur des fichiers générés par `ffmpeg` : **audio** (OGG/Opus, mono, fréquence d'entrée lue dans `OpusHead` : ffprobe annonce toujours 48 kHz pour Opus, débit avec tolérance, durée, silence en tête et en queue, **niveau mesuré en LUFS**, métadonnées personnelles) ; **image** (WebP, ≤ 120 Ko, pas d'EXIF/XMP) ; **vidéo** (H.264, ≤ 480p, ≤ 30 s, métadonnées) ; **plafonds cumulés** (lot ≤ 100 Mo, quota téléphone 500 Mo, enveloppe 6 Go) ; **secrets** (chemin et type seulement, jamais la valeur) ; conformité au manifeste et aux registres ; **fichier non listé dans les demandes = rejeté**. Un outil de mesure absent donne « non vérifiable dans cet environnement », jamais « conforme ».
Sorties : `media.json` (**acceptés seulement**), `rejets.json`, `attente.json`, `controle.json`, `rapport-reception.md`.
- **Cohérence texte/audio (ASR)** : l'interface est définie (`mp/asr.py`) et **non implémentée ici** : l'exécutant écrit `asr-results.jsonl` ; `receive` compare (casse, ponctuation, espaces ignorés pour la comparaison seulement). **Un écart = rejet.** **Pas de résultat = ni accepté ni rejeté** : « contrôle ASR non vérifiable dans cet environnement » (bloquant).

## 7. Gabarits de consignes (`tools/media-pipeline/templates/castbridge-content/`)
`AGENTS.md` et `.claude/CLAUDE.md` pour `castbridge-content` (lecture seule contre écriture, périmètres, interdictions : jamais de secret, de voix hors registre, de média non listé, de commit hors branche autorisée, de validation simulée), `LOT-DE-TRAVAIL.md` (format d'échange : demandes → plan → autorisation du propriétaire → production → réception), `STOP-BUDGET.md` (procédure d'arrêt).

## 8. Limites honnêtes
- **Rien n'a été produit ni appelé** : aucun accès Google ici ; les contrôles sont testés sur des sons de test, pas sur des voix Google.
- **Qualité des voix** : chinois (tons) et japonais (accent, lecture des kanji) **doivent être écoutés par un locuteur natif** ; l'ASR ne le remplace pas. Aucune voix n'est « naturelle » ou « certifiée » sans écoute humaine.
- **Juridique** : propriété et licence des sorties, marquage, redistribution sous CC BY-SA, vente de lots : **à faire valider par un juriste** ; dépendance aux **conditions de Google en vigueur** (à dater et archiver).
- **Coûts** : inconnus tant que `pricing.json` est vide ; l'estimation d'une vidéo se fait à la durée maximale demandée.
- Variétés (anglais, espagnol) et voix : décisions du propriétaire ; le gabarit n'en impose aucune.
- Les demandes sont tirées des paquets de **Langues** (`content/langues/<scope>/langue.json`) ; Apprendre et Quiz n'ont pas de média audio à produire aujourd'hui.

## 9. Commandes
```
python3 tools/media-pipeline/mp.py requests content/langues work/media-requests.jsonl
python3 tools/media-pipeline/mp.py estimate work/media-requests.jsonl --pricing pricing.json
python3 tools/media-pipeline/mp.py registries registry/engines-registry.json registry/voices-registry.json --requests work/media-requests.jsonl
python3 tools/media-pipeline/mp.py plan work/media-requests.jsonl work/plan.json --pricing pricing.json --state work/state.json --max-cost <plafond>
python3 tools/media-pipeline/mp.py record work/media-requests.jsonl work/state.json <id> …
python3 tools/media-pipeline/mp.py receive work/media-requests.jsonl work work/produced.json registry/engines-registry.json registry/voices-registry.json --asr work/asr-results.jsonl --family reserve --out work/reception
python3 tools/media-pipeline/mp.py secrets .
cd tools/media-pipeline/tests && python3 -m unittest test_pipeline        # 39 tests ; ffmpeg requis pour 8 d'entre eux (sautés sans)
```

## 10. Copie dans castbridge-content
- **Source** : le dépôt de code (`tools/media-pipeline/`) ; `castbridge-content` n'en porte qu'une copie synchronisée, jamais modifiée à la main (une correction se fait ici, puis se recopie).
- **Synchroniser** : copier `tools/media-pipeline/` vers `castbridge-content/tools/media-pipeline/` (sans `templates/`, déjà instancié à la racine du dépôt contenu), puis relancer les tests des deux côtés.
- **Tracer** : noter le commit du dépôt de code copié et la date dans `castbridge-content/tools/media-pipeline/SYNC.txt`, dans le même commit que la copie.
