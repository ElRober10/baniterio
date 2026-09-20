-- Precio de un artículo sin variantes de tamaño (refrescos, cerveza/tinto de
-- verano, limpieza y utensilios, comida): una fila por evento + categoría +
-- nombre + tienda. Distinto de precio_bebida_evento (que sí combina tamaños
-- para el alcohol).
CREATE TABLE precio_articulo_evento (
    id BIGSERIAL PRIMARY KEY,
    evento_id BIGINT NOT NULL REFERENCES evento(id),
    categoria VARCHAR(20) NOT NULL
        CHECK (categoria IN ('CERVEZA', 'REFRESCOS', 'LIMPIEZA', 'COMIDA')),
    nombre_articulo VARCHAR(120) NOT NULL,
    tienda_id BIGINT NOT NULL REFERENCES tienda(id),
    precio NUMERIC(8, 2) NOT NULL CHECK (precio >= 0),
    UNIQUE (evento_id, categoria, nombre_articulo, tienda_id)
);

CREATE INDEX idx_precio_articulo_evento ON precio_articulo_evento (evento_id, categoria, nombre_articulo);
