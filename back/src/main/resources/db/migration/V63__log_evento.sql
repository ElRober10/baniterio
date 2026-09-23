CREATE TABLE log_evento (
    id BIGSERIAL PRIMARY KEY,
    pena_id BIGINT NOT NULL REFERENCES pena(id),
    usuario_id BIGINT NULL REFERENCES usuario(id),
    origen VARCHAR(20) NOT NULL,
    metodo VARCHAR(10) NULL,
    ruta VARCHAR(300) NULL,
    estado INTEGER NULL,
    codigo_error VARCHAR(60) NULL,
    mensaje TEXT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_log_evento_pena_creado ON log_evento (pena_id, creado_en DESC);
CREATE INDEX idx_log_evento_usuario ON log_evento (usuario_id);
