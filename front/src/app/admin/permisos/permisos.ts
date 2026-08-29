import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { AdminService } from '../admin.service';
import { AREAS, MiembroResumen, Rol } from '../admin.types';

/**
 * Pantalla de administración de permisos: rol, alta/baja y áreas de cada
 * miembro. Se pinta en el `<router-outlet>` de `Panel` en
 * `/panel/administracion/permisos`, tras pasar `areaGuard('ADMIN_PERMISOS')`.
 *
 * Estado en signals:
 * - `miembros`: las filas que se pintan.
 * - `estado`: 'cargando' | 'lista' | 'error' — controla el `@switch` de la plantilla.
 * - `mensaje`: banda de aviso (normalmente un error) arriba de la lista.
 *
 * Las tres acciones (rol / activo / áreas) llaman a su endpoint y, tanto en
 * éxito como en error, recargan toda la lista con `cargar()`. Así los toggles,
 * que se pintan a partir de `miembros()`, siempre reflejan el estado real del
 * backend y nunca se queda un cambio "a medias" tras un rechazo.
 *
 * `avisarError` traduce el `codigo` que trae el backend a un mensaje en castellano.
 */
@Component({
  selector: 'app-admin-permisos',
  styleUrl: './permisos.css',
  templateUrl: './permisos.html',
})
export class AdminPermisos implements OnInit {
  private readonly adminService = inject(AdminService);

  protected readonly miembros = signal<MiembroResumen[]>([]);
  protected readonly estado = signal<'cargando' | 'lista' | 'error'>('cargando');
  protected readonly mensaje = signal('');

  /** `[clave, etiqueta][]` de las áreas conocidas, para pintar los checkboxes. */
  protected readonly areasConocidas = Object.entries(AREAS);

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.adminService.listarMiembros().subscribe({
      next: (lista) => {
        this.miembros.set(lista);
        this.estado.set('lista');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected ponerRol(m: MiembroResumen, rol: Rol): void {
    if (m.rol === rol) return;
    this.adminService.cambiarRol(m.id, rol).subscribe({
      next: () => this.cargar(),
      error: (e: HttpErrorResponse) => this.avisarError(e),
    });
  }

  protected activar(m: MiembroResumen, activo: boolean): void {
    this.adminService.cambiarActivo(m.id, activo).subscribe({
      next: () => this.cargar(),
      error: (e: HttpErrorResponse) => this.avisarError(e),
    });
  }

  protected alternarArea(m: MiembroResumen, area: string, incluir: boolean): void {
    const nuevas = incluir ? [...m.areas, area] : m.areas.filter((a) => a !== area);
    this.adminService.cambiarAreas(m.id, nuevas).subscribe({
      next: () => this.cargar(),
      error: (e: HttpErrorResponse) => this.avisarError(e),
    });
  }

  private avisarError(e: HttpErrorResponse): void {
    const codigo = e.error?.codigo as string | undefined;
    const mensajes: Record<string, string> = {
      ULTIMO_ADMIN: 'No puedes dejar la peña sin ningún administrador.',
      NO_TE_PUEDES_DEGRADAR: 'No puedes quitarte a ti mismo el rol de admin.',
      NO_TE_PUEDES_DESACTIVAR: 'No puedes desactivarte a ti mismo.',
      SOLO_EL_SUPERADMIN: 'A esa persona solo puede tocarla ella misma (es quien fundó la peña).',
      SIN_PERMISO: 'Solo un administrador puede nombrar administradores.',
      MIEMBRO_NO_ENCONTRADO: 'Ese miembro ya no está. Recargo la lista.',
      VALIDACION: 'No se pudo guardar el cambio (datos no válidos).',
    };
    if (codigo && mensajes[codigo]) {
      this.mensaje.set(mensajes[codigo]);
    } else if (e.status === 0) {
      this.mensaje.set('Sin conexión con el servidor.');
    } else {
      this.mensaje.set('No se pudo guardar el cambio.');
    }
    this.cargar();
  }
}
