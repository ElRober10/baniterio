-- Migración Flyway nº 6. Flyway ejecuta en orden los ficheros V1__*, V2__*, ...
-- al arrancar la app la primera vez y anota cada uno en la tabla flyway_schema_history
-- para no repetirlo. Las V1-V5 crean las tablas; esta V6 mete datos iniciales (siembra).
--
-- No editar tras aplicarse: Flyway valida el checksum. Teléfonos nuevos → nueva migración V7.

-- Peña piloto. Idempotente: si ya existe (por slug), no se duplica.
INSERT INTO pena (nombre, slug, activa)
VALUES ('Bañiterio', 'baniterio', TRUE)
ON CONFLICT (slug) DO NOTHING;

-- Único teléfono sembrado: el del usuario fundador (coincide con app.identidad.telefono-fundador).
-- El resto de teléfonos autorizados de la peña piloto son datos personales y no viven en git:
-- se cargan a mano con back/scripts/seed-telefonos-baniterio.local.sql (ver README).
INSERT INTO telefono_autorizado (telefono, pena_id)
SELECT '600000001', (SELECT id FROM pena WHERE slug = 'baniterio')
ON CONFLICT (telefono) DO NOTHING;
