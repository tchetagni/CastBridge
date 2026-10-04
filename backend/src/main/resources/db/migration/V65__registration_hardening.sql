-- Correctifs de l'audit Opus w23-05 (docs/agent-reports/audit-opus-w23-05.md). Additif : aucune donnée existante n'est modifiée ou supprimée.
-- Numérotation : « plus haut existant + 1 » (V64 = enregistrement des activations notifiées, déjà sur la branche). Retour arrière : db/rollback/U65__registration_hardening_rollback.sql.

-- HIGH-1 : le jeton porte-t-il, SIGNÉE, la clé d'installation de la TV (droit « ik|<hex> ») ? Sert l'affichage de la liste de décision du propriétaire (clé signée ou clé du premier présentateur).
ALTER TABLE lic_registration ADD COLUMN ik_signed BOOLEAN NOT NULL DEFAULT FALSE;

-- MEDIUM-2 : la clé qui a signé ce jeton peut-elle CRÉER (ISSUE_PRODUCTION) ? Seuls les droits d'usage de ces jetons forment les fenêtres pendant lesquelles une licence ouverte par notification paie
-- (un renouvellement après une interruption ne paie pas l'interruption) ; une clé de réactivation ne prolonge rien.
ALTER TABLE lic_registration ADD COLUMN signer_creates BOOLEAN NOT NULL DEFAULT TRUE;

-- MEDIUM-3 : une ligne par clé d'outil ; la transaction qui compte le plafond du jour (10) et du mois (50) la verrouille d'abord : le comptage est exact sous concurrence.
CREATE TABLE lic_key_gate (
    kid        VARCHAR(16) NOT NULL PRIMARY KEY,
    touched_at DATETIME(6) NOT NULL
);

-- HIGH-3 : une période d'une TV n'est payée qu'UNE fois par monnaie, quelle que soit la licence. (identité, monnaie, case) est UNIQUE ; la case est la période la plus proche de la grille de l'identité
-- (arrondi à l'entier le plus proche de (début de période - ancre) / durée de période). cur = NDEM, MBOKO ou OPEN (l'ouverture illimitée, case unique).
CREATE TABLE wallet_period_claim (
    holder       VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    cur          VARCHAR(8)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    slot         BIGINT      NOT NULL,
    period_start DATETIME(6) NOT NULL,
    idem_key     VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    claimed_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (holder, cur, slot)
);
