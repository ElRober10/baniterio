-- Una ficha por asistencia (solo eventos de San Miguel). modalidad y cuota las
-- calcula el servicio; cuota es NULL si el evento aún no tiene cuota máxima.
CREATE TABLE ficha_bebida (
    asistencia_id      BIGINT       PRIMARY KEY REFERENCES asistencia_evento (id) ON DELETE CASCADE,
    alcohol_bebida_id  BIGINT       REFERENCES bebida (id),
    refresco_bebida_id BIGINT       NOT NULL REFERENCES bebida (id),
    alternativa        VARCHAR(20)  NOT NULL DEFAULT 'NADA',
    cerveza_especial   VARCHAR(80),
    embarazada         BOOLEAN      NOT NULL DEFAULT false,
    asiste_dia_1       BOOLEAN      NOT NULL DEFAULT true,
    asiste_dia_2       BOOLEAN      NOT NULL DEFAULT true,
    modalidad          VARCHAR(16)  NOT NULL,
    cuota              NUMERIC(7,2),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_ficha_alternativa CHECK (alternativa IN ('CERVEZA','TINTO_VERANO','NADA','CERVEZA_ESPECIAL')),
    CONSTRAINT ck_ficha_modalidad   CHECK (modalidad IN ('COMPLETA','SOLO_CERVEZA','UN_DIA','EMBARAZADA')),
    CONSTRAINT ck_ficha_algun_dia   CHECK (asiste_dia_1 OR asiste_dia_2)
);
