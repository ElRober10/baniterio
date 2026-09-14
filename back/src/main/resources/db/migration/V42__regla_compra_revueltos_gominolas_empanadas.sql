-- Tres reglas nuevas de COMIDA: bolsa de revueltos grande (fija, 1 por evento),
-- gominolas (1 bolsa por día de fiesta) y empanadas (1 por cada 7 peñistas y día,
-- misma fórmula que la tortilla de patatas de V40).
INSERT INTO regla_compra (pena_id, categoria, nombre, tamano, tipo_formula, factor, por_cada, orden)
SELECT p.id, v.categoria, v.nombre, v.tamano, v.tipo_formula, v.factor, v.por_cada, v.orden
FROM pena p
CROSS JOIN (VALUES
    ('COMIDA', 'Revueltos grande', 'bolsa', 'POR_EVENTO',              1, NULL, 10),
    ('COMIDA', 'Gominolas',        'bolsa', 'POR_DIA',                 1, NULL, 11),
    ('COMIDA', 'Empanadas',        'unidad', 'POR_CADA_N_PENISTAS_DIA', 1, 7,   12)
) AS v(categoria, nombre, tamano, tipo_formula, factor, por_cada, orden)
WHERE p.slug = 'baniterio'
  AND NOT EXISTS (
      SELECT 1 FROM regla_compra r
      WHERE r.pena_id = p.id AND r.categoria = v.categoria
        AND r.nombre = v.nombre AND r.tipo_formula = v.tipo_formula
  );

-- Backfill igual que V41: los eventos que ya tenían sus reglas materializadas se
-- quedan sin las nuevas si no se copian aquí a mano.
INSERT INTO regla_compra_evento
    (evento_id, categoria, nombre, tamano, tipo_formula, factor, por_cada, orden, origen, cantidad_ajustada, activa)
SELECT e.id, v.categoria, v.nombre, v.tamano, v.tipo_formula, v.factor, v.por_cada, v.orden, 'PLANTILLA', NULL, TRUE
FROM evento e
JOIN pena p ON p.id = e.pena_id
CROSS JOIN (VALUES
    ('COMIDA', 'Revueltos grande', 'bolsa', 'POR_EVENTO',              1, NULL, 10),
    ('COMIDA', 'Gominolas',        'bolsa', 'POR_DIA',                 1, NULL, 11),
    ('COMIDA', 'Empanadas',        'unidad', 'POR_CADA_N_PENISTAS_DIA', 1, 7,   12)
) AS v(categoria, nombre, tamano, tipo_formula, factor, por_cada, orden)
WHERE p.slug = 'baniterio'
  AND EXISTS (SELECT 1 FROM regla_compra_evento re WHERE re.evento_id = e.id)
  AND NOT EXISTS (
      SELECT 1 FROM regla_compra_evento re2
      WHERE re2.evento_id = e.id AND re2.categoria = v.categoria
        AND re2.nombre = v.nombre AND re2.tipo_formula = v.tipo_formula
  );
