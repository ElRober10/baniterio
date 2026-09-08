-- Las fichas que ya estaban confirmadas antes de la rev.2 (cuando confirmar un
-- pago aún no escribía en el libro) no tienen su fila de CUOTA, así que su
-- dinero no cuenta en el saldo. Se les crea la fila ahora, una vez.
INSERT INTO movimiento_cuenta (cuenta_id, concepto, importe, fecha, origen, ficha_asistencia_id, creado_por)
SELECT e.cuenta_id,
       'Cuota de ' || coalesce(u.nombre, a.nombre) || ' — ' || e.nombre,
       f.cuota,
       current_date,
       'CUOTA',
       f.asistencia_id,
       NULL
FROM ficha_bebida f
JOIN asistencia_evento a ON a.id = f.asistencia_id
JOIN evento e            ON e.id = a.evento_id
LEFT JOIN usuario u      ON u.id = a.usuario_id
WHERE f.cuota IS NOT NULL
  AND f.estado_pago IN ('CONFIRMADO_PENDIENTE_ENVIO', 'CONFIRMADO_EN_CUENTA')
  AND NOT EXISTS (
      SELECT 1 FROM movimiento_cuenta m
      WHERE m.ficha_asistencia_id = f.asistencia_id AND m.origen = 'CUOTA'
  );
