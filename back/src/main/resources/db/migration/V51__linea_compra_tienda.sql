-- Tienda (snapshot de texto, como nombre/tamano) donde comprar una línea de la
-- lista de la compra. Solo la rellena el cálculo de ALCOHOL_SELECCIONADO cuando
-- hay precios para elegir la combinación más barata; el resto de líneas la
-- dejan a NULL.
ALTER TABLE linea_compra_evento ADD COLUMN tienda VARCHAR(120);
