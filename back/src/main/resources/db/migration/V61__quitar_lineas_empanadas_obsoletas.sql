-- Las lineas de la lista sin comprar de "Empanadas" quedaron obsoletas al dividirse
-- en dos (V60); se vuelven a crear solas al abrir la lista.
DELETE FROM linea_compra_evento
WHERE categoria = 'COMIDA' AND nombre = 'Empanadas' AND comprada = FALSE;
