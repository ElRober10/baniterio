-- Corrige nombres inconsistentes entre catálogo de bebida, lista de la compra
-- (regla_compra) e inventario (articulo_inventario / articulo_evento), y crea
-- en el inventario dos artículos de limpieza que ya existían en la lista de
-- la compra pero no en el inventario.

-- "Mantel" (lista de la compra) y "Mantel 1,2 x 5" (inventario) son el mismo
-- artículo: se queda el nombre corto.
UPDATE articulo_inventario SET nombre = 'Mantel' WHERE nombre = 'Mantel 1,2 x 5';

-- "Fanta limón" (inventario/inventario de la fiesta) y "Fanta Limón" (catálogo
-- de bebida) son el mismo producto: se queda con mayúscula, como el catálogo.
UPDATE articulo_inventario SET nombre = 'Fanta Limón' WHERE nombre = 'Fanta limón';
UPDATE articulo_evento SET nombre = 'Fanta Limón' WHERE nombre = 'Fanta limón';

-- "Ballantines" (catálogo de bebida) estaba mal escrito; la marca real es
-- "Ballantine's", como ya estaba en el inventario.
UPDATE bebida SET nombre = 'Ballantine''s' WHERE nombre = 'Ballantines';

-- "Negruita" era un duplicado mal escrito de "Negrita" en el catálogo de
-- bebida. Se redirige cualquier ficha que la use hacia "Negrita" y se borra
-- el duplicado.
UPDATE ficha_bebida SET alcohol_bebida_id = (SELECT id FROM bebida WHERE nombre = 'Negrita' AND tipo = 'ALCOHOL')
    WHERE alcohol_bebida_id = (SELECT id FROM bebida WHERE nombre = 'Negruita' AND tipo = 'ALCOHOL');
DELETE FROM bebida WHERE nombre = 'Negruita' AND tipo = 'ALCOHOL';

-- Artículos de limpieza que ya estaban en la lista de la compra pero no
-- existían todavía en el inventario general de la peña 1.
INSERT INTO articulo_inventario (pena_id, categoria, nombre, tamano, cantidad, orden)
VALUES
    (1, 'LIMPIEZA', 'Vasos de invitar', 'unidad', 0, 10),
    (1, 'LIMPIEZA', 'Film transparente', 'rollo', 0, 11);
