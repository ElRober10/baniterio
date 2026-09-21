-- "Empanadas" pasa a ser dos articulos: de carne y de pollo con setas, cada uno
-- con la misma formula que tenia (1 por cada 7 penistas y dia).
UPDATE regla_compra SET nombre = 'Empanada de carne'
WHERE categoria = 'COMIDA' AND nombre = 'Empanadas';

INSERT INTO regla_compra (pena_id, categoria, nombre, tamano, tipo_formula, factor, por_cada, orden)
SELECT pena_id, categoria, 'Empanada de pollo con setas', tamano, tipo_formula, factor, por_cada, orden + 1
FROM regla_compra r
WHERE r.categoria = 'COMIDA' AND r.nombre = 'Empanada de carne'
  AND NOT EXISTS (
      SELECT 1 FROM regla_compra r2
      WHERE r2.pena_id = r.pena_id AND r2.categoria = 'COMIDA'
        AND r2.nombre = 'Empanada de pollo con setas' AND r2.tipo_formula = r.tipo_formula
  );

-- Los eventos que ya tenian sus reglas materializadas.
UPDATE regla_compra_evento SET nombre = 'Empanada de carne'
WHERE categoria = 'COMIDA' AND nombre = 'Empanadas';

INSERT INTO regla_compra_evento
    (evento_id, categoria, nombre, tamano, tipo_formula, factor, por_cada, orden, origen, cantidad_ajustada, activa)
SELECT re.evento_id, re.categoria, 'Empanada de pollo con setas', re.tamano, re.tipo_formula, re.factor,
       re.por_cada, re.orden + 1, re.origen, re.cantidad_ajustada, re.activa
FROM regla_compra_evento re
WHERE re.categoria = 'COMIDA' AND re.nombre = 'Empanada de carne'
  AND NOT EXISTS (
      SELECT 1 FROM regla_compra_evento re2
      WHERE re2.evento_id = re.evento_id AND re2.categoria = 'COMIDA'
        AND re2.nombre = 'Empanada de pollo con setas' AND re2.tipo_formula = re.tipo_formula
  );

-- Precios ya apuntados en la rejilla para "Empanadas": pasan a la de carne.
UPDATE precio_articulo_evento SET nombre_articulo = 'Empanada de carne'
WHERE categoria = 'COMIDA' AND nombre_articulo = 'Empanadas';
