# w12-12 — Campagne de test § W12 (≈ 30 étapes, TV de référence + émulateur + serveur de préproduction), CI des outils `tools/settings`, dépendances, liste de contrôle du calendrier de teasing
<!-- routage Fable 2026-10-02 -->
> **Modèle : haiku** · escalade : sonnet si une étape exige d'interpréter un comportement non décrit · statut : PRÊT (après w12-05 pour la CI ; les étapes de campagne peuvent être écrites dès maintenant)
> **Groupe : W12-d** (vague W12) · prérequis : aucun pour la campagne ; w12-05 fusionné pour la CI · porte : `python3 -c "import yaml,glob;[yaml.safe_load(open(f)) for f in glob.glob('.github/workflows/*.yml')]" && grep -c '^- \[ \]' docs/TEST-CAMPAIGN.md`
> **Jauge : ≈ 60 k jetons entrée / 5 k sortie** (effort S) · audit Opus : non

**Vague 12d (campagne, CI) · Effort S (≈ 1 j) · Modèle : haiku · Statut PRÊT.** Conception : `DESIGN-W12-REGLAGES-TEASING-2026-10-02.md` § 2.3, § 2.5, § 6.1, § 6.2, § 7 (porte de la tranche). Branche `claude/sonnet-w12-12`. Rapport : `docs/agent-reports/sonnet-w12-12.md`. **Aucune exécution sur une vraie TV ni sur le serveur de production par l'exécutant** : il écrit les étapes ; le propriétaire les joue.

## Objectif (édition mécanique)
1. **`docs/TEST-CAMPAIGN.md`** : nouvelle section `## W12 — Réglages signés et teasing` avec des cases `- [ ]` groupées :
   - **Cœur / serveur (hors ligne)** : vecteurs Kotlin, Java, Python verts ; parité `schema.json` ; `NEVER` refusé ; concurrence des seq.
   - **Console** (préproduction, port 7091, clé de test) : publier seq 1 = défauts ; diff vide ; publier seq 2 (une clé €, TOTP) ; retour arrière ⇒ seq 3 ; interrupteur ⇒ seq 4 ; `audit/verify` vert ; historique ; téléchargement du jeton et du QR.
   - **TV de référence (GaiaOS 32 bits, 720p)** : fichier `Download/CastBridge/settings` sur clé USB : accepté (ligne « Réglages : v2 » dans Connexion & réglages, badge « Lots locatifs : … » cohérent) ; fichier altéré (un caractère) refusé, état inchangé ; ancien seq refusé ; fichier absent après « Effacer les données » = « défauts » ; `GET /api/settings` depuis le téléphone ; `POST /api/settings/install` avec et sans PIN ; mode verrouillé : `GET` ouvert, `POST` selon la règle ; `settings_applied` visible dans `/admin` (parc par version).
   - **Téléphone** : tirage 6 h (forcer), relais vers la TV, adoption inverse (TV plus récente par USB), collage, fichier, ligne À propos, message affiché une fois.
   - **Émetteurs** : console pré-remplit 14 j après seq 2 ; bureau `reglages-serveur` puis `emettre` ; serveur : clé d'essai émise avec la durée du document ; document périmé ⇒ avertissement.
   - **Bluetooth** (si w12-09) : trames 16-21 : ordre appliqué, réglages appliqués, ancien téléphone ignoré.
   - **Expériences** (si w12-02/10) : deux sujets ⇒ deux bras ; TV et téléphone du même foyer ⇒ même bras ; `exp` absent des événements essentiels ; page KPI.
   - **Sécurité** : jeton signé par la clé d'essai du propriétaire (sans `POLICY`) refusé ; `target=device` refusé ; clé révoquée refusée ; serveur éteint ⇒ 404 et rien ne change sur les appareils.
2. **CI** : `.github/workflows/tools.yml` : étape `python3 -m unittest discover -s tools/settings -p 'test_*.py'` et `python3 tools/activation/verify_vectors.py` (si déjà présent, ne pas doubler) ; `tools/requirements-dev.txt` : `qrcode` **optionnel** commenté (ne pas rendre obligatoire) ; `docs/COORDINATION.md` § CI : une ligne.
3. **`docs/coordination/CALENDRIER-TEASING-W12.md`** (nouveau, ≤ 40 lignes) : le tableau § 6.2 de la conception recopié avec une colonne « Fait le » vide, et la liste des décisions D-W12-1…8 avec une colonne « Décision du propriétaire » vide.

## Fichiers possédés
`docs/TEST-CAMPAIGN.md` (§ W12 seulement), `.github/workflows/tools.yml`, `tools/requirements-dev.txt`, `docs/COORDINATION.md` (§ CI : une ligne), `docs/coordination/CALENDRIER-TEASING-W12.md` (nouveau). Hors zone : tout code, les autres docs (w12-11).

## Critères d'acceptation
- Porte verte ; ≥ 30 cases `- [ ]` dans § W12 ; YAML valide ; aucune étape ne demande `adb` à un agent (elles s'adressent au propriétaire) ; rapport : nombre d'étapes par groupe.
