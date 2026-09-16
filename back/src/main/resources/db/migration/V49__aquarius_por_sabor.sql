-- "Aquarius" generico en el catalogo de bebida no se puede relacionar con el
-- inventario, que lo tiene por sabor ("Aquarius limón" / "Aquarius naranja").
-- Se olvida la ficha que ya lo tenia elegido (queda sin refresco) y se
-- sustituye la opcion generica por las dos de sabor.
UPDATE ficha_bebida SET refresco_bebida_id = NULL
    WHERE refresco_bebida_id = (SELECT id FROM bebida WHERE nombre = 'Aquarius' AND tipo = 'REFRESCO');

DELETE FROM bebida WHERE nombre = 'Aquarius' AND tipo = 'REFRESCO';

INSERT INTO bebida (tipo, nombre, estado)
VALUES
    ('REFRESCO', 'Aquarius limón', 'ACEPTADA'),
    ('REFRESCO', 'Aquarius naranja', 'ACEPTADA');
