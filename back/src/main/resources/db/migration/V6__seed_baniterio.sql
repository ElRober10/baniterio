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
