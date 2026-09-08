-- El admin confirma cuándo ha entrado en la cuenta el dinero de una prenda;
-- solo entonces suma al saldo (movimiento CAMISETA / SUDADERA).
ALTER TABLE ficha_bebida ADD COLUMN camiseta_confirmada BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE ficha_bebida ADD COLUMN sudadera_confirmada BOOLEAN NOT NULL DEFAULT false;

-- De momento nadie lleva ropa: se limpia lo sembrado al azar en V31.
UPDATE ficha_bebida SET
    camiseta_cantidad = 0, camiseta_talla = NULL,
    sudadera_cantidad = 0, sudadera_talla = NULL;
DELETE FROM movimiento_cuenta WHERE origen IN ('CAMISETA', 'SUDADERA');
