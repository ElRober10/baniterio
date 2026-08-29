-- Migración Flyway nº 7: la solicitud de ingreso guarda lo que cuenta el
-- solicitante. La tabla se creó en V5 sin estos campos (el flujo de solicitud
-- quedó para más adelante); ahora se implementa.
--
-- La tabla está vacía en todos los entornos, así que se pueden añadir como
-- NOT NULL sin valor por defecto.
ALTER TABLE solicitud_ingreso
    ADD COLUMN motivo    TEXT NOT NULL,
    ADD COLUMN relacion  TEXT NOT NULL,
    ADD COLUMN conocidos TEXT NOT NULL;
