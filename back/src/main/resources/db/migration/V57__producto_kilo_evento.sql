-- Embutidos que se compran siempre en Jamones Duriber (paletilla, salchichon y
-- chorizo ibericos): precio por kilo y peso estimado de la pieza. El precio de
-- la pieza en la lista de la compra sale de multiplicarlos. Sin fila en un
-- evento, se hereda el ultimo apuntado en otro evento de la pena.
CREATE TABLE producto_kilo_evento (
    id BIGSERIAL PRIMARY KEY,
    evento_id BIGINT NOT NULL REFERENCES evento(id),
    nombre_articulo VARCHAR(120) NOT NULL,
    precio_kilo NUMERIC(8, 2) NOT NULL CHECK (precio_kilo >= 0),
    peso_kg NUMERIC(5, 2) NOT NULL CHECK (peso_kg >= 0),
    UNIQUE (evento_id, nombre_articulo)
);
