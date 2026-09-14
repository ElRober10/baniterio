-- Nueva fórmula "por cada N peñistas y día" (usada por la tortilla de patatas:
-- 1 unidad por cada 7 peñistas y día de fiesta).
ALTER TABLE regla_compra DROP CONSTRAINT ck_regla_compra_tipo;
ALTER TABLE regla_compra ADD CONSTRAINT ck_regla_compra_tipo CHECK (tipo_formula IN (
    'POR_PENISTA', 'POR_PENISTA_DIA', 'POR_DIA', 'POR_EVENTO', 'POR_CADA_N_PENISTAS',
    'POR_CADA_N_PENISTAS_DIA', 'CERVEZA_ALTERNATIVA', 'TINTO_ALTERNATIVA',
    'ALCOHOL_SELECCIONADO', 'REFRESCO_SELECCIONADO'));

ALTER TABLE regla_compra_evento DROP CONSTRAINT ck_regla_compra_evento_tipo;
ALTER TABLE regla_compra_evento ADD CONSTRAINT ck_regla_compra_evento_tipo CHECK (tipo_formula IN (
    'POR_PENISTA', 'POR_PENISTA_DIA', 'POR_DIA', 'POR_EVENTO', 'POR_CADA_N_PENISTAS',
    'POR_CADA_N_PENISTAS_DIA', 'CERVEZA_ALTERNATIVA', 'TINTO_ALTERNATIVA',
    'ALCOHOL_SELECCIONADO', 'REFRESCO_SELECCIONADO'));

INSERT INTO regla_compra (pena_id, categoria, nombre, tamano, tipo_formula, factor, por_cada, orden)
SELECT p.id, 'COMIDA', 'Tortilla de patatas', 'unidad', 'POR_CADA_N_PENISTAS_DIA', 1, 7, 9
FROM pena p
WHERE p.slug = 'baniterio'
  AND NOT EXISTS (
      SELECT 1 FROM regla_compra r
      WHERE r.pena_id = p.id AND r.categoria = 'COMIDA'
        AND r.nombre = 'Tortilla de patatas' AND r.tipo_formula = 'POR_CADA_N_PENISTAS_DIA'
  );
