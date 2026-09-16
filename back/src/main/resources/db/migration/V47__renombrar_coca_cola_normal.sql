-- El catálogo de bebidas (tabla bebida) tiene "Coca-Cola"; el inventario tenía
-- "Coca-Cola normal" para el mismo producto. Un solo nombre para poder
-- relacionar más adelante lista de la compra e inventario.
UPDATE articulo_inventario SET nombre = 'Coca-Cola' WHERE nombre = 'Coca-Cola normal';
UPDATE articulo_evento SET nombre = 'Coca-Cola' WHERE nombre = 'Coca-Cola normal';
