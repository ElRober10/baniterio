import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { Volver } from '../../shared/volver/volver';
import { AdminAvisosService } from '../admin-avisos.service';
import { AvisoPendientes } from '../../shared/aviso-pendientes/aviso-pendientes';
import { AREAS, Area } from '../admin.types';

/** Una sección del panel de administración, con su área, su ruta y el texto que la describe. */
interface SeccionAdmin {
  area: Area;
  descripcion: string;
  ruta: string;
}

const SECCIONES_ADMIN: SeccionAdmin[] = [
  {
    area: 'ADMIN_SOLICITUDES',
    descripcion: 'Revisa y resuelve las peticiones de acceso a la peña.',
    ruta: '/panel/administracion/solicitudes',
  },
  {
    area: 'ADMIN_PERMISOS',
    descripcion: 'Rol y accesos de cada miembro.',
    ruta: '/panel/administracion/permisos',
  },
];

/**
 * Índice del panel de administración. Se pinta en el `<router-outlet>` de `Panel`
 * en `/panel/administracion`, al que se llega desde la tarjeta "Administración"
 * de la home del panel.
 *
 * Muestra como tarjetas clicables solo las secciones para las que el usuario
 * tiene el área concedida (`auth.tieneArea`). No lleva guard propio: cualquier
 * miembro puede abrirlo; si no tiene ninguna área, `disponibles` queda vacío y
 * se pinta el aviso. El guard de verdad (`areaGuard`) sigue estando en cada
 * sub-ruta.
 */
@Component({
  selector: 'app-admin-indice',
  imports: [RouterLink, Volver, AvisoPendientes],
  templateUrl: './indice.html',
})
export class AdminIndice implements OnInit {
  private readonly auth = inject(AuthService);
  protected readonly avisos = inject(AdminAvisosService);

  /** Etiqueta legible de cada área (`AREAS['ADMIN_SOLICITUDES'] === 'Solicitudes'`). */
  protected readonly etiquetas = AREAS;

  /** Secciones que este usuario puede abrir, según sus áreas. */
  protected readonly disponibles = SECCIONES_ADMIN.filter((s) => this.auth.tieneArea(s.area));

  ngOnInit(): void {
    this.avisos.refrescar();
  }
}
