-- Bluetooth MAC address of the device (reported by the app, never assumed). A technical fact only.

ALTER TABLE device ADD COLUMN bt_address VARCHAR(32) NULL;
