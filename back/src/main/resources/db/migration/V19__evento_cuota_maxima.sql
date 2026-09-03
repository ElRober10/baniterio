-- Cuota máxima del evento (euros), la fija un administrador. De momento solo la
-- usa San Miguel; el resto de eventos la dejan a NULL. Las reglas para calcular
-- la cuota de cada peñista a partir de esta (solo cerveza, un día, embarazada)
-- son constantes de la aplicación, no viven en BBDD.
ALTER TABLE evento ADD COLUMN cuota_maxima NUMERIC(7, 2);

ALTER TABLE evento ADD CONSTRAINT ck_evento_cuota_maxima
    CHECK (cuota_maxima IS NULL OR cuota_maxima >= 0);
