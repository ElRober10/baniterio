-- Parte de la cuota cobrada por bizum/efectivo y aún sin ingresar en la cuenta de
-- la peña. NULL = toda la cuota (comportamiento de siempre); solo se rellena al
-- actualizar una cuota ya cobrada, cuando pendiente es solo la diferencia.
ALTER TABLE ficha_bebida ADD COLUMN importe_pendiente_envio NUMERIC(7,2);
