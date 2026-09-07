-- Rev.2 de la hoja de cuentas: gastos/ingresos manuales con categoría y recibo,
-- ropa (camiseta/sudadera) por peñista. Ver el spec 2026-09-07-saldo-cuentas.

-- Amplía los orígenes del libro y añade categoría, recibo y quién lo adelantó.
ALTER TABLE movimiento_cuenta DROP CONSTRAINT ck_movimiento_origen;
ALTER TABLE movimiento_cuenta ADD CONSTRAINT ck_movimiento_origen
    CHECK (origen IN ('SALDO_INICIAL', 'CUOTA', 'CAMISETA', 'SUDADERA',
                      'GASTO', 'INGRESO', 'AJUSTE'));

ALTER TABLE movimiento_cuenta ADD COLUMN categoria      VARCHAR(24);
ALTER TABLE movimiento_cuenta ADD COLUMN recibo_archivo VARCHAR(80);
ALTER TABLE movimiento_cuenta ADD COLUMN adelantado_por BIGINT REFERENCES usuario (id) ON DELETE SET NULL;

-- Una ficha puede tener ahora cuota + camiseta + sudadera (3 movimientos): el
-- único pasa a ser por (ficha, origen).
DROP INDEX ux_movimiento_ficha;
CREATE UNIQUE INDEX ux_movimiento_ficha_origen
    ON movimiento_cuenta (ficha_asistencia_id, origen) WHERE ficha_asistencia_id IS NOT NULL;

-- Precio de la ropa por evento (lo fija el admin, como las cuotas).
ALTER TABLE evento ADD COLUMN precio_camiseta NUMERIC(7,2);
ALTER TABLE evento ADD COLUMN precio_sudadera NUMERIC(7,2);

-- Ropa pagada por peñista.
ALTER TABLE ficha_bebida ADD COLUMN camiseta_pagada BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE ficha_bebida ADD COLUMN sudadera_pagada BOOLEAN NOT NULL DEFAULT false;
