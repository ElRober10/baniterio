import { Component } from '@angular/core';

/**
 * Pantalla de administración de permisos: rol, alta/baja y áreas de cada
 * miembro. Se pinta en el `<router-outlet>` de `Panel` en
 * `/panel/administracion/permisos`, tras pasar `areaGuard('ADMIN_PERMISOS')`.
 *
 * Stub: la implementación real (listado de miembros + edición) llega en Task 5.
 */
@Component({
  selector: 'app-admin-permisos',
  styleUrl: './permisos.css',
  templateUrl: './permisos.html',
})
export class AdminPermisos {}
