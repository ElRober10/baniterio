-- La ropa deja de ser una casilla "pagada" sí/no: ahora se guarda cuántas
-- unidades y de qué talla pide cada peñista. El importe de la ropa se sumará a
-- la cuota cuando se desarrolle la sección de ropa; de momento son solo datos.
ALTER TABLE ficha_bebida DROP COLUMN camiseta_pagada;
ALTER TABLE ficha_bebida DROP COLUMN sudadera_pagada;

ALTER TABLE ficha_bebida ADD COLUMN camiseta_cantidad  INT NOT NULL DEFAULT 0;
ALTER TABLE ficha_bebida ADD COLUMN camiseta_talla     VARCHAR(24);
ALTER TABLE ficha_bebida ADD COLUMN sudadera_cantidad  INT NOT NULL DEFAULT 0;
ALTER TABLE ficha_bebida ADD COLUMN sudadera_talla     VARCHAR(24);

-- Se van los movimientos de ropa de la rev.2 (la ropa ya no es un movimiento suelto).
DELETE FROM movimiento_cuenta WHERE origen IN ('CAMISETA', 'SUDADERA');

-- Siembra aleatoria para ver las columnas en desarrollo. La talla de verdad se
-- elegirá en la sección de ropa.
UPDATE ficha_bebida SET
    camiseta_cantidad = floor(random() * 3)::int,
    sudadera_cantidad = floor(random() * 2)::int;
UPDATE ficha_bebida SET camiseta_talla =
    (ARRAY['S', 'M', 'L', 'XL'])[1 + floor(random() * 4)::int] || ' ' ||
    (ARRAY['chico', 'chica', 'niño'])[1 + floor(random() * 3)::int]
    WHERE camiseta_cantidad > 0;
UPDATE ficha_bebida SET sudadera_talla =
    (ARRAY['S', 'M', 'L', 'XL'])[1 + floor(random() * 4)::int] || ' ' ||
    (ARRAY['chico', 'chica', 'niño'])[1 + floor(random() * 3)::int]
    WHERE sudadera_cantidad > 0;
