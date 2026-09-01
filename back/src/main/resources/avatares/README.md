# Avatares predefinidos

Catálogo fijo de avatares que un miembro puede elegir para su tarjeta en lugar
de subir una foto propia.

Estas imágenes se empaquetan dentro del jar del backend y las sirve la API en
`GET /api/v1/media/avatares/{id}.png`. Las usan tanto la web como el móvil, así
que solo hay que subirlas aquí una vez.

## Nombres

`NN_chico.png` y `NN_chica.png` (dos dígitos + `_chico` / `_chica`). El sufijo
indica el género para poder filtrar el catálogo; el selector muestra **todos por
defecto** y deja filtrar a solo chicos o solo chicas.

El `id` que guarda el perfil (`perfil.imagen_ref`) es el nombre sin extensión,
p. ej. `03_chica`.

## Formato

- PNG con transparencia, ~500×500 px (se recortan en círculo al pintarse).
- Peso por archivo por debajo de ~250 KB.

## Manifiesto

`avatares.json` (en esta carpeta) lista los avatares disponibles con su género.
El backend lo lee para ofrecer el catálogo a los clientes; **al añadir o quitar
un avatar hay que regenerarlo**. No se hace listado del directorio (incómodo
desde un jar).
