-- Precio unitario estimado (snapshot, como tienda) de una línea de la lista de
-- la compra: solo lo rellena ALCOHOL_SELECCIONADO cuando eligió la combinación
-- más barata a partir de la rejilla de precios. El resto de líneas lo dejan a
-- NULL (no hay precio con el que calcular nada).
ALTER TABLE linea_compra_evento ADD COLUMN precio_unitario NUMERIC(8, 2);
