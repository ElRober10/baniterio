-- Token de notificaciones push (FCM/APNs) de cada instalación de la app.
-- Un token identifica un dispositivo+app; si otra persona inicia sesión en ese
-- móvil, el token pasa a ser suyo (UPDATE de usuario_id), de ahí el UNIQUE.
CREATE TABLE dispositivo (
    id             BIGSERIAL PRIMARY KEY,
    usuario_id     BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    token          TEXT NOT NULL UNIQUE,
    plataforma     TEXT NOT NULL,
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dispositivo_usuario ON dispositivo (usuario_id);
