-- V40 añadió "Tortilla de patatas" a la plantilla (regla_compra), pero
-- regla_compra_evento solo se copia de la plantilla la primera vez que se abre
-- la lista de un evento (ver ListaCompraService.materializar). Los eventos que
-- ya tenían sus reglas materializadas antes de V40 se quedan sin la tortilla
-- para siempre si no se les añade a mano aquí.
INSERT INTO regla_compra_evento
    (evento_id, categoria, nombre, tamano, tipo_formula, factor, por_cada, orden, origen, cantidad_ajustada, activa)
SELECT e.id, 'COMIDA', 'Tortilla de patatas', 'unidad', 'POR_CADA_N_PENISTAS_DIA', 1, 7, 9, 'PLANTILLA', NULL, TRUE
FROM evento e
JOIN pena p ON p.id = e.pena_id
WHERE p.slug = 'baniterio'
  AND EXISTS (SELECT 1 FROM regla_compra_evento re WHERE re.evento_id = e.id)
  AND NOT EXISTS (
      SELECT 1 FROM regla_compra_evento re2
      WHERE re2.evento_id = e.id AND re2.categoria = 'COMIDA'
        AND re2.nombre = 'Tortilla de patatas' AND re2.tipo_formula = 'POR_CADA_N_PENISTAS_DIA'
  );
