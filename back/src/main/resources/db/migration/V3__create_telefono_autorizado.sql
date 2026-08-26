CREATE TABLE telefono_autorizado (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    telefono VARCHAR(20) NOT NULL,
    pena_id UUID NOT NULL REFERENCES pena (id),
    usado BOOLEAN NOT NULL DEFAULT FALSE,
    autorizado_por UUID REFERENCES usuario (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_telefono_autorizado_telefono UNIQUE (telefono)
);

CREATE INDEX ix_telefono_autorizado_pena ON telefono_autorizado (pena_id);
