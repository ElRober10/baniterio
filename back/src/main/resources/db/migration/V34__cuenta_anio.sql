-- Cada cuenta lleva un "año en curso". Los movimientos se apuntan a ese año y
-- la hoja muestra solo ese año. "Cerrar el año" arrastra el saldo al siguiente.
ALTER TABLE cuenta ADD COLUMN anio_actual INT NOT NULL DEFAULT 2026;
ALTER TABLE movimiento_cuenta ADD COLUMN anio INT NOT NULL DEFAULT 2026;
