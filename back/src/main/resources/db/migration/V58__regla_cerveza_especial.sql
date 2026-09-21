-- Cervezas "especiales" (sin gluten, sin alcohol...) que la gente escribe en la
-- ficha: regla dinamica con una linea por nombre distinto, 5 latas por penista y
-- dia (igual que la cerveza normal).
ALTER TABLE regla_compra DROP CONSTRAINT ck_regla_compra_tipo;
ALTER TABLE regla_compra ADD CONSTRAINT ck_regla_compra_tipo CHECK (tipo_formula IN (
    'POR_PENISTA', 'POR_PENISTA_DIA', 'POR_DIA', 'POR_EVENTO', 'POR_CADA_N_PENISTAS',
    'POR_CADA_N_PENISTAS_DIA', 'CERVEZA_ALTERNATIVA', 'TINTO_ALTERNATIVA',
    'ALCOHOL_SELECCIONADO', 'REFRESCO_SELECCIONADO', 'CERVEZA_ESPECIAL_SELECCIONADA'));

ALTER TABLE regla_compra_evento DROP CONSTRAINT ck_regla_compra_evento_tipo;
ALTER TABLE regla_compra_evento ADD CONSTRAINT ck_regla_compra_evento_tipo CHECK (tipo_formula IN (
    'POR_PENISTA', 'POR_PENISTA_DIA', 'POR_DIA', 'POR_EVENTO', 'POR_CADA_N_PENISTAS',
    'POR_CADA_N_PENISTAS_DIA', 'CERVEZA_ALTERNATIVA', 'TINTO_ALTERNATIVA',
    'ALCOHOL_SELECCIONADO', 'REFRESCO_SELECCIONADO', 'CERVEZA_ESPECIAL_SELECCIONADA'));

INSERT INTO regla_compra (pena_id, categoria, nombre, tamano, tipo_formula, factor, por_cada, orden)
SELECT p.id, 'CERVEZA', '', 'lata', 'CERVEZA_ESPECIAL_SELECCIONADA', 5, NULL, 2
FROM pena p
WHERE p.slug = 'baniterio'
  AND NOT EXISTS (
      SELECT 1 FROM regla_compra r
      WHERE r.pena_id = p.id AND r.categoria = 'CERVEZA'
        AND r.nombre = '' AND r.tipo_formula = 'CERVEZA_ESPECIAL_SELECCIONADA'
  );

-- Backfill: los eventos que ya tenian sus reglas materializadas no la recibirian sola.
INSERT INTO regla_compra_evento
    (evento_id, categoria, nombre, tamano, tipo_formula, factor, por_cada, orden, origen, cantidad_ajustada, activa)
SELECT e.id, 'CERVEZA', '', 'lata', 'CERVEZA_ESPECIAL_SELECCIONADA', 5, NULL, 2, 'PLANTILLA', NULL, TRUE
FROM evento e
JOIN pena p ON p.id = e.pena_id
WHERE p.slug = 'baniterio'
  AND EXISTS (SELECT 1 FROM regla_compra_evento re WHERE re.evento_id = e.id)
  AND NOT EXISTS (
      SELECT 1 FROM regla_compra_evento re2
      WHERE re2.evento_id = e.id AND re2.categoria = 'CERVEZA'
        AND re2.nombre = '' AND re2.tipo_formula = 'CERVEZA_ESPECIAL_SELECCIONADA'
  );
