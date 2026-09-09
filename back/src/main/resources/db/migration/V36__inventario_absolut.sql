-- Corrige la siembra V35: la segunda botella de alcohol (1 L) no es Four Roses
-- sino Absolut (vodka). El nombre de Four Roses en 70 cl se queda como está.
UPDATE articulo_inventario a
SET nombre = 'Absolut'
FROM pena p
WHERE a.pena_id = p.id
  AND p.slug = 'baniterio'
  AND a.categoria = 'ALCOHOL'
  AND a.nombre = 'Four Roses'
  AND a.tamano = '1 L';
