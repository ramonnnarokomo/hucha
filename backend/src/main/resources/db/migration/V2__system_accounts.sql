-- Cuentas internas del banco. Representan la contrapartida de los movimientos que salen
-- o entran de fuera de Hucha y pueden quedar en negativo; las de clientes no.
INSERT INTO accounts (id, iban, owner_id, type, name, status, balance, currency, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'ES8099990001450000000001', NULL, 'SYSTEM',
        'Compensación SEPA', 'ACTIVE', 0.00, 'EUR', CURRENT_TIMESTAMP);

INSERT INTO accounts (id, iban, owner_id, type, name, status, balance, currency, created_at)
VALUES ('00000000-0000-0000-0000-000000000002', 'ES1099990001410000000002', NULL, 'SYSTEM',
        'Liquidación de tarjetas', 'ACTIVE', 0.00, 'EUR', CURRENT_TIMESTAMP);

INSERT INTO accounts (id, iban, owner_id, type, name, status, balance, currency, created_at)
VALUES ('00000000-0000-0000-0000-000000000003', 'ES8899990001440000000003', NULL, 'SYSTEM',
        'Fondos propios de Hucha', 'ACTIVE', 0.00, 'EUR', CURRENT_TIMESTAMP);
