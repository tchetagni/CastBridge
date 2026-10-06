-- Fermeture des chemins de transfert (docs/agent-reports/sonnet-w22-02.md, décision du propriétaire 2026-10-06) : une licence ne se transfère pas, aucun poste ne change de téléviseur.
-- Plafond de transferts à 0 pour toutes les licences existantes. Retour arrière : db/rollback/U67__transfer_cap_zero_rollback.sql.
-- Compatible H2 (mode MySQL) et MySQL 8.4 : instructions standard seulement.

-- les valeurs d'avant, gardées pour le retour arrière (seules les licences dont le plafond change)
CREATE TABLE bak_transfer_cap_v67 (
    license_pk   BIGINT NOT NULL PRIMARY KEY,
    transfer_cap INT    NOT NULL
);
INSERT INTO bak_transfer_cap_v67 (license_pk, transfer_cap) SELECT id, transfer_cap FROM lic_license WHERE transfer_cap <> 0;

UPDATE lic_license SET transfer_cap = 0 WHERE transfer_cap <> 0;
ALTER TABLE lic_license ALTER COLUMN transfer_cap SET DEFAULT 0;
