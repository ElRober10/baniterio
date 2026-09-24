-- No editar tras aplicarse: Flyway valida el checksum. Teléfonos nuevos → nueva migración V7.

-- Peña piloto. Idempotente: si ya existe (por slug), no se duplica.
INSERT INTO pena (nombre, slug, activa)
VALUES ('Bañiterio', 'baniterio', TRUE)
ON CONFLICT (slug) DO NOTHING;

-- Los teléfonos autorizados (incluido el del fundador, app.identidad.telefono-fundador) son datos
-- personales y no viven en git: se cargan a mano con back/scripts/seed-telefonos-baniterio.local.sql
-- (ver README).
