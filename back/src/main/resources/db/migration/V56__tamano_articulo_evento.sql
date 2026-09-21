-- Tamaño de botella (litros) que se apunta junto al nombre de un refresco en la
-- rejilla de precios de un evento. Sin fila = 2 litros (el valor por defecto).
CREATE TABLE tamano_articulo_evento (
    id BIGSERIAL PRIMARY KEY,
    evento_id BIGINT NOT NULL REFERENCES evento(id),
    nombre_articulo VARCHAR(120) NOT NULL,
    litros NUMERIC(2, 1) NOT NULL CHECK (litros IN (1.0, 1.5, 2.0)),
    UNIQUE (evento_id, nombre_articulo)
);
