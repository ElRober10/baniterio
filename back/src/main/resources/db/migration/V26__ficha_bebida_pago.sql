-- Confirmación de pago de la cuota de San Miguel por parte de un administrador
-- (pieza 5 recortada del subsistema Cuentas). El estado vive en la ficha; no hay
-- tabla `pago` todavía. `pagado` lo pone/quita solo el admin desde el modal de
-- asistentes; el modal de declaración del peñista sigue sin persistir.
ALTER TABLE ficha_bebida ADD COLUMN pagado BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE ficha_bebida ADD COLUMN metodo_pago VARCHAR(16);
ALTER TABLE ficha_bebida ADD COLUMN pagado_confirmado_por BIGINT REFERENCES usuario(id);
ALTER TABLE ficha_bebida ADD COLUMN pagado_at TIMESTAMPTZ;
