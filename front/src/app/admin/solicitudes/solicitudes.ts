import { Component } from '@angular/core';

/**
 * Pantalla de administración de solicitudes de ingreso. Se pinta en el
 * `<router-outlet>` de `Panel` en `/panel/administracion/solicitudes`, tras
 * pasar `areaGuard('ADMIN_SOLICITUDES')`.
 *
 * Stub: la implementación real (listado + aprobar/rechazar) llega en Task 4.
 */
@Component({
  selector: 'app-admin-solicitudes',
  styleUrl: './solicitudes.css',
  templateUrl: './solicitudes.html',
})
export class AdminSolicitudes {}
