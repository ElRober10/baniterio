-- Borrar un evento pasa a ser "ocultarlo" (recuperable) en vez de un DELETE de
-- verdad: por si se pulsa sin querer. Solo lo hacen y lo deshacen admin/superadmin
-- (ver V24 y el cambio de EventoService.puedeGestionar). Los ocultos no salen en
-- el listado normal ni en "pendientes de respuesta"; hay un listado aparte para
-- verlos y recuperarlos.
ALTER TABLE evento ADD COLUMN oculto BOOLEAN NOT NULL DEFAULT false;
