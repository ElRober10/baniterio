-- El concepto de una cuota era "Cuota de Roberto — Fiestas de San Miguel 2026";
-- el evento sobra (la hoja ya es de esa cuenta). Se deja solo "Cuota de Roberto".
UPDATE movimiento_cuenta
   SET concepto = split_part(concepto, ' — ', 1)
 WHERE origen = 'CUOTA' AND concepto LIKE '% — %';
