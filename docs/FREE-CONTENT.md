# Archive des contenus libres (CC BY-SA)

> Exigence du propriétaire (2026-10-02) : « Mets tous les contenus taggués à cette license : CC BY-SA téléchargeables dans un zip même lorsqu'on est en mode appli non activée, via un bouton télécharger tous les contenus libres ».
> Ce document couvre l'**inventaire** et l'**outil de construction** de l'archive (`tools/free-content/build_free_archive.py`). Le point d'accès serveur et le bouton TV sont traités ailleurs.
> Aucun avis juridique : les points de la section 4 sont à faire trancher par le propriétaire ou un juriste.

## 1. Règle

- N'entre dans l'archive que le contenu **explicitement étiqueté « CC BY-SA » avec sa version** (par exemple « CC BY-SA 4.0 ») dans son propre fichier (`license` / `licence` / `licenses` de `pack.json` ou `langue.json`), ou déclaré par le propriétaire dans `tools/free-content/license-tags.json`. Rien n'est déduit des documents de politique.
- Un lot **réservé** (`reserved` de `content/*/lots.json`) n'entre **jamais** dans l'archive ; un lot libre n'est jamais scellé ni loué (`LotFamilies`, `RentalPolicy`).
- Les médias d'un pack retenu doivent avoir une licence connue (CASTBRIDGE-ORIGINAL, CC0, PD, CC-BY-2.0/3.0/4.0, CC-BY-SA-3.0/4.0) ; auteur et source obligatoires pour CC BY et CC BY-SA.

## 2. Inventaire au 2026-10-02 (branche `integration/agents`)

**Résultat : 0 pack étiqueté CC BY-SA ; l'archive réelle serait donc vide aujourd'hui.** Aucun `pack.json`, `langue.json`, `lessons/*.json`, `figures/*.json` ni `media.json` du dépôt ne porte de champ de licence de pack. La mention « CC BY-SA » n'existe que dans des **documents de politique** (`docs/LANGUES.md` § 5, 7, 13 ; `docs/RENTAL-LOTS.md` § 7), dans le commentaire de `content/langues/embedded.txt` et dans le code (`LangPack.kt`, `RentalPolicy.kt`).

Sorties de `python3 tools/free-content/build_free_archive.py --dry-run` :

| Catégorie | Nombre |
|---|---|
| Packs examinés | 271 (259 Apprendre, 12 Langues) |
| Inclus | 0 |
| Exclus, sans étiquette de licence | 271 |
| Exclus, autre licence | 0 |
| Refusés (contradiction ou média inconnu) | 0 |
| Enregistrés libres mais non étiquetés | 1 (`zh-a0-salut-fr`) |
| Enregistrés réservés | 0 (la liste `reserved` est vide) |
| Famille inconnue (dans aucune liste) | 270 |
| Non suivis par git (dossiers non enregistrés) | 11 |

### Packs Langues (`content/langues/`)

Tous : `license` de pack absente ; médias `CASTBRIDGE-ORIGINAL`, `synthetic: true`, `voiceLicense: GPL-3.0` (sortie du moteur espeak-ng, licence du moteur seulement). Lot média jumeau : `content/langues-media/<cible>-<niveau>-<thème>/audio/` (non suivi par git, comme les packs ci-dessous).

| Pack | Étiquette trouvée | Attribution / source | Famille | Dans l'archive | Pourquoi |
|---|---|---|---|---|---|
| `zh-a0-salut-fr` | aucune | CastBridge (média : « voix de synthèse libre, GPL-3.0, moteur uniquement ») | libre (seul lot libre enregistré, aussi dans `embedded.txt`) | non | libre sans étiquette : contradiction ; à étiqueter par le propriétaire |
| `zh-a0-salut-en`, `zh-a0-famille-fr`, `zh-a0-famille-en`, `zh-a0-nombres-fr`, `zh-a0-nombres-en` | aucune | idem | inconnue, non enregistré, non suivi par git | non | pas d'étiquette ; hors registre |
| `ja-a0-salut-fr`, `ja-a0-salut-en`, `ja-a0-famille-fr`, `ja-a0-famille-en`, `ja-a0-nombres-fr`, `ja-a0-nombres-en` | aucune | idem | inconnue, non enregistré, non suivi par git | non | pas d'étiquette ; hors registre |

### Packs Apprendre (`content/learn/`, 259 packs)

Champs présents : `authors` (« CastBridge (brouillon IA, bêta) »), `programRef` (référence au programme officiel MINESEC, « à vérifier »). Aucun champ de licence. `content/learn/lots.json` ne contient aucune liste `free`/`reserved` : famille **inconnue** pour tous. Tous exclus : « aucune étiquette de licence ». Les 259 sont dans le tableau complet affiché par `--dry-run` (un pack par ligne : id, étiquette, famille, suivi git, statut, raison).

Les contenus `content/quiz` et `content/graph` n'ont ni étiquette ni lot libre : hors périmètre de l'outil (pas de pack Apprendre/Langues).

## 3. Outil

```
python3 tools/free-content/build_free_archive.py --dry-run
python3 tools/free-content/build_free_archive.py --out dist/ --generated-at 2026-10-02T12:00:00Z --build 1
python3 tools/free-content/build_free_archive.py --check dist/castbridge-contenus-libres-20261002-v1.zip
```

Options : `--out DIR`, `--only-tagged-as "CC BY-SA"` (ou « CC BY-SA 4.0 »), `--dry-run`, `--check ZIP`, `--tags FICHIER` (défaut `tools/free-content/license-tags.json`), `--generated-at ISO` et `--build N` (reproductibilité), `--require-registered` (refuse un pack non enregistré libre), `--allow-empty`, `--repo`. Codes de sortie : 0 ok, 2 refus, 3 usage, 4 archive altérée.

**Refus (code 2, message en français)** : pack étiqueté mais enregistré réservé ou à la fois libre et réservé ; étiquette ambiguë ou mixte (plusieurs licences, « / », « et »…) ; version de licence absente ; média sans licence, de licence inconnue/non libre, sans auteur/source (CC BY, CC BY-SA) ou fichier manquant ; leçons Apprendre avec médias référencés ; aucun pack retenu (sauf `--allow-empty`). Un pack sans étiquette ou avec une autre licence est seulement **exclu avec avertissement**.

**Contenu du zip** (`castbridge-contenus-libres-<aaaammjj>-v<N>.zip`, entrées triées, horodatage 1980-01-01, deux constructions = mêmes octets) : `LISEZ-MOI.txt`, `ATTRIBUTION.md`, `MANIFEST.json` (pack, version, étiquette, SHA-256 et taille de chaque fichier, taille totale, `generatedAt`, version du constructeur, `licenseTextVerbatim`), `LICENSE-CC-BY-SA-<version>.txt`, `contenus/learn/<pack>/`, `contenus/langues/<pack>/`, `contenus/langues-media/<jumeau>/` (seuls les médias listés par `media.json`).

**Texte de la licence** : le dépôt ne contient **aucune copie** du texte légal CC BY-SA ; il n'est donc pas reproduit de mémoire. Le fichier `LICENSE-*.txt` contient les liens officiels et `licenseTextVerbatim: false`. Pour le texte intégral, déposer l'original officiel dans `tools/free-content/licenses/CC-BY-SA-4.0.txt` (et `-3.0.txt` si besoin) : l'outil l'utilise alors tel quel et passe `licenseTextVerbatim` à `true`.

## 4. Ambiguïtés et lacunes juridiques (propriétaire / juriste)

1. **Aucune étiquette dans les fichiers.** Les décisions du 2026-10-01 (§ 13 de `LANGUES.md`) restent dans les documents ; il faut ajouter `"license": "CC BY-SA 4.0"` aux packs concernés (générateurs `tools/langues/gen_a0_pilot.py`, `tools/build-learn-packs`, etc.) ou remplir `license-tags.json` : décision du propriétaire.
2. **Contradiction de définition.** `LANGUES.md` § 13 et `RentalPolicy.kt` : « libre = *dérivé de sources CC BY-SA* », « réservé = *original ou domaine public* ». Mais `LangPack.kt` et § 5/11 disent aussi que le contenu **original CastBridge est publié en CC BY-SA 4.0**. Or `zh-a0-salut-fr` est `CASTBRIDGE-ORIGINAL` et enregistré libre. Il faut dire si « original » est libre ou réservé : sinon un même contenu serait à la fois vendable/louable et offert en CC BY-SA.
3. **Libre enregistré mais non étiqueté** : `zh-a0-salut-fr` (embarqué dans l'APK de la TV). **Étiqueté mais réservé** : aucun cas aujourd'hui (l'outil refuserait).
4. **`CASTBRIDGE-ORIGINAL` n'est pas une licence** : c'est une provenance. Il ne dit pas « CC BY-SA 4.0 » ; sa mention n'existe que dans le texte de politique.
5. **Audio de synthèse** : `voiceLicense: GPL-3.0` désigne la licence du moteur espeak-ng ; que celle-ci ne s'applique pas à la sortie audio (ni ne contamine l'archive CC BY-SA) est une hypothèse à faire valider. Les voix neuronales envisagées (Kokoro, MeloTTS, Piper) ont chacune leur licence ; `docs/MEDIA-PIPELINE.md` § « Juridique » et `tools/media-pipeline/templates/legal/SORTIES-GENEREES.md` signalent déjà « à valider par un juriste » et la valeur `CC-BY-SA-4.0` « provisoire à confirmer ».
6. **Sources tierces** (KanjiVG CC BY-SA 3.0, CC-CEDICT, JMdict, Wiktionary) : CC BY-SA 3.0 ne peut pas être relicencié en 4.0 par CastBridge sans vérifier la clause de compatibilité ; l'outil accepte un média CC-BY-SA-3.0 (licence conservée, texte 3.0 joint) mais aucun n'existe encore. Make Me a Hanzi (licence Arphic) : « à faire valider avant tout usage ».
7. **Licence libre + logiciel verrouillé + lots payants** : le § 13 de `LANGUES.md` demande une validation juridique avant toute vente ; la mesure technique de verrouillage ne doit pas toucher le contenu SA (CC BY-SA 4.0 § 2(a)(4)).
8. **Apprendre** : 259 packs sans étiquette ni famille ; `programRef` cite des programmes officiels MINESEC « à vérifier » : l'archive ne couvrira Apprendre que si le propriétaire déclare ce contenu original et libre (voir point 2).
9. **Attribution** : « modifié par CastBridge » est affiché pour chaque pack, y compris les œuvres originales, par prudence ; auteurs d'un pack Langues : non renseignés dans `langue.json` (« CastBridge » par défaut dans `ATTRIBUTION.md`).
10. **Packs hors registre** : les 11 packs `zh-a0-*` et `ja-a0-*` non suivis par git ne sont dans aucun `lots.json`; à enregistrer (libre ou réservé) avant publication.

## 5. Procédure de publication proposée

1. Le propriétaire tranche les points 1, 2 et 5.
2. Étiqueter les packs, enregistrer leur famille dans `content/langues/lots.json`, déposer le texte officiel de la licence.
3. `--dry-run` : plus aucun avertissement inattendu.
4. Construire, puis `--check`, puis déposer le zip sur le serveur (point d'accès public sans activation, hors de ce document).

Tests : `python3 -m unittest discover -s tools/tests -p 'test_free_content.py'`.
