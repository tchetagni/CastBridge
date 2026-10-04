-- Second audit Opus w23-05 (docs/agent-reports/audit-opus-w23-05-bis.md), HIGH-A. Additif : aucune donnée existante n'est modifiée ou supprimée.
-- Retour arrière : db/rollback/U66__install_key_binding_rollback.sql.

-- Réaffectation de liaison par le propriétaire : il lit sur l'écran de la TV l'EMPREINTE de sa clé d'installation (32 hexadécimaux, SHA-256 tronqué) et la saisit ; l'identité libérée ne se lie
-- qu'à la clé qui a cette empreinte (jamais au premier appareil qui arrive). NULL = aucune attente (identité liée, ou jamais réaffectée).
ALTER TABLE wallet_identity ADD COLUMN expected_install_fp VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL;
