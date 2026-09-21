-- "Sprite Zero" sale de todas las listas: en la peña se pide "Sprite" a secas.
-- Las fichas que lo tenían pasan a "Sprite"; luego se borra del catálogo.
UPDATE ficha_bebida
   SET refresco_bebida_id = (SELECT id FROM bebida WHERE tipo = 'REFRESCO' AND nombre = 'Sprite')
 WHERE refresco_bebida_id = (SELECT id FROM bebida WHERE tipo = 'REFRESCO' AND nombre = 'Sprite Zero')
   AND EXISTS (SELECT 1 FROM bebida WHERE tipo = 'REFRESCO' AND nombre = 'Sprite');

DELETE FROM precio_articulo_evento WHERE nombre_articulo = 'Sprite Zero';

DELETE FROM bebida
 WHERE tipo = 'REFRESCO' AND nombre = 'Sprite Zero'
   AND NOT EXISTS (SELECT 1 FROM ficha_bebida f WHERE f.refresco_bebida_id = bebida.id);
