-- Una linea modificada a mano (cantidad, tamano o tienda) se queda como esta aunque se
-- desbloquee la lista y se vuelva a calcular. Guarda la necesidad (lo que pedia la formula)
-- en el momento de la modificacion: si cambia -- se apunta alguien mas --, se descarta la
-- modificacion y se recalcula. NULL = linea sin modificar.
ALTER TABLE linea_compra_evento ADD COLUMN necesidad_base NUMERIC(10, 2);
