-- Todo evento pertenece a una cuenta: la de su "evento recurrente" (San Miguel,
-- Chuletas Santas...). Lo que se ingresa y se gasta en ese evento va a esa
-- cuenta y el saldo se arrastra de un año al siguiente.
--
-- Se añade la columna, se vinculan los 3 eventos sembrados (V15) a su cuenta
-- (V17) por coincidencia de nombre ("Fiestas de San Miguel 2026" -> "San
-- Miguel"), y se deja NOT NULL. En un despliegue limpio V15/V17/V18 corren
-- seguidas, así que solo hay esos 3 eventos y todos casan.
ALTER TABLE evento ADD COLUMN cuenta_id BIGINT REFERENCES cuenta (id);

UPDATE evento e
SET cuenta_id = c.id
FROM cuenta c
WHERE c.pena_id = e.pena_id
  AND e.cuenta_id IS NULL
  AND e.nombre ILIKE '%' || c.nombre || '%';

ALTER TABLE evento ALTER COLUMN cuenta_id SET NOT NULL;

CREATE INDEX ix_evento_cuenta ON evento (cuenta_id);
