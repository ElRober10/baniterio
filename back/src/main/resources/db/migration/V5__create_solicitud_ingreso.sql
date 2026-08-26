CREATE TABLE solicitud_ingreso (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pena_id UUID NOT NULL REFERENCES pena (id),
    telefono VARCHAR(20) NOT NULL,
    email VARCHAR(160) NOT NULL,
    nombre VARCHAR(80) NOT NULL,
    apellidos VARCHAR(120) NOT NULL,
    mote VARCHAR(60),
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    motivo_rechazo VARCHAR(255),
    resuelta_por UUID REFERENCES usuario (id),
    resuelta_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_solicitud_estado CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA'))
);

CREATE INDEX ix_solicitud_ingreso_pena ON solicitud_ingreso (pena_id);
CREATE INDEX ix_solicitud_ingreso_estado ON solicitud_ingreso (estado);
