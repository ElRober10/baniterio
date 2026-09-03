-- Los 3 eventos con los que arranca la peña piloto. creado_por queda NULL: los
-- gestionan los administradores. Las fechas cambian cada año; se editan por la
-- UI. Idempotente por (pena_id, nombre) — no hay constraint única, así que
-- INSERT ... WHERE NOT EXISTS.
INSERT INTO evento (pena_id, nombre, fecha, fecha_fin)
SELECT p.id, v.nombre, v.fecha, v.fecha_fin
FROM pena p
CROSS JOIN (VALUES
    ('Fiestas de San Miguel 2026', DATE '2026-09-25', DATE '2026-09-26'),
    ('Chuletas Santas 2027',       DATE '2027-03-26', NULL),
    ('Migas Santas 2027',          DATE '2027-03-27', NULL)
) AS v(nombre, fecha, fecha_fin)
WHERE p.slug = 'baniterio'
  AND NOT EXISTS (
      SELECT 1 FROM evento e WHERE e.pena_id = p.id AND e.nombre = v.nombre
  );
