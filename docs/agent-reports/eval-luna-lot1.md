# Évaluation indépendante (Opus, lecture seule, 2026-10-07) du lot 1 de Luna et comparaison avec la Relève

## Fait majeur
L'auto-note de Luna (34/40) n'utilise **pas** la grille officielle `PEDAGOGY-RUBRIC` (3 critères sur 10 en commun). Mon contrôle du jour l'avait reprise telle quelle et contenait deux erreurs (très高兴认识你 : **six** syllabes hěn gāoxìng rènshi nǐ, tons 3-1-4-4-neutre-3, formule HSK 1 légitime en A0 comme bloc à ajouter au banc).

## Notes sur la grille officielle (0-4)
| Critère | Luna (plan) | Relève (de-a0-nombres-fr) |
|---|---:|---:|
| Clarté | 3 | 3 (coquille « Combien ça coûte ?. » ×48, consignes identiques) |
| Progression | 3 (L5 empile 4 tâches ; prérequis « à confirmer » alors que `graph/langue-*.json` existe) | 3 (L5 chargée) |
| Exemples résolus | **1** (aucun pour l'apprenant) | 1,5 (aucune fausse piste) |
| Idées fausses | 3 (pièges nommés, aucun distracteur ; paire /ʏ/–/uː/ non minimale) | 3 |
| Pertinence culturelle | 2 (seul ancrage : prénom Amina) | **1,5** (euros, Allemagne seule, pas de FCFA/marché) |
| Niveau de langue | 2,5 (aucun texte d'explication rédigé) | 3,5 |
| Charge cognitive | 2,5 (banc allemand 16 items > 15 ; L1 exige 8/10 au premier contact) | 3 |
| Accessibilité | **2** (pas d'alt écrit, pas de `reducedMotion`, polices/3 m non vérifiés) | 2 |
| Alignement de l'évaluation | 2 (activités sans item ni corrigé ; expression écrite A0 absente) | 2,5 (60 exercices **sans explication** ; x59 frôle la production libre) |
| Fidélité au niveau | 3 | 3 |
| **Total** | **24/40 = 2,4 → revise** | **26,5/40 = 2,65 → revise** |

Critère « au niveau » du mandat non mesurable tant que les grilles diffèrent : à redéfinir sur la grille officielle.

## Exactitude linguistique
- **Chinois** : banc juste ; sandhi correctement traité. À confirmer par un natif : 下午好/晚上好 (scolaires), enchaînement 很高兴认识你 → 再见 abrupt (B : 你好 / 我也很高兴认识你).
- **Japonais (certain)** : 何 isolé = なに *nani* (なん seulement devant です) ; « ū » en L5 sans item porteur ; お名前 / お願い contiennent des kanji (願 19 traits) → furigana ou kana ; アミナ/アンナ en katakana hors banc ; ordre du premier échange : はじめまして ouvre, よろしくお願いします dit par les deux. À confirmer : さようなら entre inconnus, お名前は？ vs 何ですか, ありがとう → ありがとうございます.
- **Allemand (certain)** : **der Name** (jamais un nom sans article, LANGUES § 2.1) ; paire /ʏ/–/uː/ : *Tschüss/du* varie aussi la longueur → *Tür/Tour* ou *für/fuhr* ; *Danke/Bitte/Hallo* : majuscule d'énoncé, pas de nom (à dire, sinon idée fausse ; la Relève y tombe). À confirmer : *Tschüss* aussi avec Sie aujourd'hui.
- **Aucune paire minimale** dans aucune langue, alors que LANGUES § 2.2 en exige à chaque unité A0-A2.

## Fidélité au cahier : dérives
1. Livrables manquants (§ 3 du mandat) : scripts JSON, demandes JSONL, figures image par image, provenance, journal, QA officiel : **un plan, pas un lot**. 2. Réemploi affirmé (67 %), non démontré ; L1 ne peut rien réactiver. 3. Banc allemand 16 items. 4. Conflit non signalé : LANGUES § 4.1 « un thème = 8-15 unités ≈ 3-6 h » vs mandat « 6-12 leçons » (Luna : 6 × 8-10 min ≈ 1 h) → **décision du propriétaire**. 5. Licence : `CC-BY-SA-4.0` dans le gabarit, alors que l'élément synthétisé est `CASTBRIDGE-ORIGINAL` (MEDIA-POLICY § 5 : original / généré / CC0) ; la licence de sortie du lot (CC BY-SA) et celle de l'élément sont confondues. 6. Expression écrite A0 non couverte.

## Kit de relève v1
Reproduit la forme, pas le jugement. Manquent : schéma réel de `langue.json` (`LangPack.kt` : `level` majuscule, `state` ∈ review/validated/needs-fix/rejected, `truefalse` correct 0 = vrai) et de `mp.py` ; modélisation du réemploi (par référence, pas par duplication : la Relève a 87 entrées pour **34 mots distincts**, *null* porte 5 identifiants → doublons probables dans la répétition espacée `Srs`) ; exemples négatifs complets ; règles tranchées (L1 exemptée ? reconnaissance hors thème ? écriture de mémoire en A0 ? qui écrit les explications ? une voix par réplique) ; grille d'auto-contrôle alignée sur la grille officielle ; exemplaires dorés par langue et par type.

## Relève vs Luna
Relève mieux : paquet réel outillé (6 leçons, 60 exercices corrigés, 7 dialogues, 53 demandes à texte unique, 0 conflit, réemploi chiffré 60,9 %, prérequis `de.a0-co/ce/po/pe` vérifiés, 48 Ko, IPA entièrement juste, QA honnête sur la grille officielle). Luna mieux : items à fonction communicative (la Relève remplit avec Hallo/Ja/Nein/Bitte/Danke/die Zahl), dialogues avec but, pièges par langue, arbitrage de registre ; la Relève : un seul audio par dialogue en voix « neutre » (le mandat exige A f / B m), g102 induit une idée fausse sur les majuscules, `state: "draft"` hors liste, « s'il vous plaît » (x53) vs « s'il te plaît » (d7).

## Verdict et 5 correctifs prioritaires
Luna apporte le jugement didactique et linguistique multi-langue ; pas (encore) de livrables exécutables ni de notation conforme. 1. Renoter Luna et la Relève sur les 10 clés officielles (format JSON § 4, `validate_report.py`) ; aligner le kit ; redéfinir « au niveau ». 2. Compléter le lot 1 selon le § 3 (scripts avec corrigés **et explications**, ≥ 1 fausse piste par leçon, demandes par `mp.py`, alt, provenance `CASTBRIDGE-ORIGINAL`, activité d'expression écrite A0, paires minimales). 3. Corrections certaines : なに ; ū ; furigana/kana ; der Name ; Tür/Tour ; 很高兴认识你 au banc ; clip japonais réordonné ; banc allemand ≤ 15. 4. Relecture native avant de figer les points « à confirmer ». 5. Kit v2 : schémas réels, réemploi par référence, L1 exemptée, une voix par réplique, contexte camerounais obligatoire (FCFA, marché, école), exemples négatifs tirés de la Relève ; conflit § 4.1 / mandat à trancher par le propriétaire.
