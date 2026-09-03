-- Las 3 cuentas con las que arranca la peña piloto, una por evento recurrente.
-- Idempotente por (pena_id, nombre) — hay constraint única, así que ON CONFLICT.
INSERT INTO cuenta (pena_id, nombre, descripcion)
SELECT p.id, v.nombre, v.descripcion
FROM pena p
CROSS JOIN (VALUES
    ('Chuletas Santas', 'Ingresos, gastos y balance de las Chuletas Santas.'),
    ('Migas Santas',    'Ingresos, gastos y balance de las Migas Santas.'),
    ('San Miguel',      'Ingresos, gastos y balance de las Fiestas de San Miguel.')
) AS v(nombre, descripcion)
WHERE p.slug = 'baniterio'
ON CONFLICT (pena_id, nombre) DO NOTHING;
