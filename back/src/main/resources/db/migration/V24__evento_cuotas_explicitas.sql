-- Sustituye la "cuota máxima" (de la que se derivaban las demás por fórmula)
-- por 5 precios explícitos que fija un administrador, uno por combinación de
-- modalidad de bebida (cubatas/cervezas) y si va la peña completa o un día;
-- embarazada es un precio único, no varía por días. NULL = esa cuota aún no
-- está puesta.
ALTER TABLE evento DROP COLUMN cuota_maxima;

ALTER TABLE evento
    ADD COLUMN cuota_cubatas NUMERIC(7, 2),
    ADD COLUMN cuota_cervezas NUMERIC(7, 2),
    ADD COLUMN cuota_cubatas_1dia NUMERIC(7, 2),
    ADD COLUMN cuota_cervezas_1dia NUMERIC(7, 2),
    ADD COLUMN cuota_embarazada NUMERIC(7, 2);

ALTER TABLE evento ADD CONSTRAINT ck_evento_cuotas CHECK (
    (cuota_cubatas IS NULL OR cuota_cubatas >= 0)
    AND (cuota_cervezas IS NULL OR cuota_cervezas >= 0)
    AND (cuota_cubatas_1dia IS NULL OR cuota_cubatas_1dia >= 0)
    AND (cuota_cervezas_1dia IS NULL OR cuota_cervezas_1dia >= 0)
    AND (cuota_embarazada IS NULL OR cuota_embarazada >= 0)
);
