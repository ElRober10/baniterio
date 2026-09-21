-- Articulos que se venden en packs (platos, vasos, papel...): cuantas unidades
-- trae el pack que se apunta en la rejilla de precios (1 = se vende suelto).
ALTER TABLE precio_articulo_evento
    ADD COLUMN cantidad INT NOT NULL DEFAULT 1 CHECK (cantidad >= 1);

-- El precio por unidad de un pack (p. ej. 1,50 / 50 = 0,03) necesita mas decimales
-- para que cantidad x precio siga dando el coste real; y una nota corta de la compra.
ALTER TABLE linea_compra_evento ALTER COLUMN precio_unitario TYPE NUMERIC(10, 4);
ALTER TABLE linea_compra_evento ADD COLUMN detalle VARCHAR(80);
