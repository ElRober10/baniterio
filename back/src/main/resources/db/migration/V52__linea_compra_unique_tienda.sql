-- uq_lce no incluía la tienda: dos botellas de la misma marca y tamaño pero de
-- súpers distintos (la combinación más barata del ALCOHOL_SELECCIONADO puede
-- dar justo eso) chocaban contra la restricción única. Postgres trata NULL
-- como distinto de NULL en un UNIQUE normal, así que esto ya permite varias
-- líneas con tienda NULL para el mismo (evento, categoria, nombre, tamano).
ALTER TABLE linea_compra_evento DROP CONSTRAINT uq_lce;
ALTER TABLE linea_compra_evento ADD CONSTRAINT uq_lce
    UNIQUE (evento_id, categoria, nombre, tamano, tienda);
