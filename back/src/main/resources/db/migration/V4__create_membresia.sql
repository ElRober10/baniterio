CREATE TABLE membresia (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario (id),
    pena_id UUID NOT NULL REFERENCES pena (id),
    rol VARCHAR(20) NOT NULL,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_membresia_usuario_pena UNIQUE (usuario_id, pena_id),
    CONSTRAINT ck_membresia_rol CHECK (rol IN ('ADMIN', 'MIEMBRO'))
);

CREATE INDEX ix_membresia_pena ON membresia (pena_id);
CREATE INDEX ix_membresia_usuario ON membresia (usuario_id);
