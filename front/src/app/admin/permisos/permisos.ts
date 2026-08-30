import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { AuthService } from '../../auth/auth.service';
import { CodigoError } from '../../auth/auth.types';
import { Volver } from '../../shared/volver/volver';
import { AdminService } from '../admin.service';
import { AREAS, MiembroResumen, Rol } from '../admin.types';

/**
 * Mensajes en castellano para los códigos de error que devuelve el backend en
 * estas acciones. `Partial<Record<CodigoError, string>>`: si el backend añade un
 * código nuevo y se nos olvida mapearlo, cae al mensaje por defecto; y una errata
 * en una clave de aquí es error de compilación.
 */
const MENSAJES: Partial<Record<CodigoError, string>> = {
  ULTIMO_ADMIN: 'No puedes dejar la peña sin ningún administrador.',
  NO_TE_PUEDES_DEGRADAR: 'No puedes quitarte a ti mismo el rol de admin.',
  NO_TE_PUEDES_DESACTIVAR: 'No puedes desactivarte a ti mismo.',
  SOLO_EL_SUPERADMIN: 'A esa persona solo puede tocarla ella misma (es quien fundó la peña).',
  SIN_PERMISO: 'Solo un administrador puede nombrar administradores.',
  MIEMBRO_NO_ENCONTRADO: 'Ese miembro ya no está. Recargo la lista.',
  VALIDACION: 'No se pudo guardar el cambio (datos no válidos).',
};

/**
 * Pantalla de administración de permisos: rol, alta/baja y áreas de cada
 * miembro. Se pinta en el `<router-outlet>` de `Panel` en
 * `/panel/administracion/permisos`, tras pasar `areaGuard('ADMIN_PERMISOS')`.
 *
 * Estado en signals:
 * - `miembros`: las filas que se pintan.
 * - `estado`: 'cargando' | 'lista' | 'error' — controla el `@switch` de la plantilla.
 * - `mensaje`: banda de aviso arriba de la lista.
 * - `tipoMensaje`: 'error' la pinta en rojo; 'info' con el estilo de marca (confirmaciones).
 *
 * Las tres acciones (rol / activo / áreas) llaman a su endpoint y, tanto en
 * éxito como en error, recargan toda la lista con `cargar()`. Así los toggles,
 * que se pintan a partir de `miembros()`, siempre reflejan el estado real del
 * backend y nunca se queda un cambio "a medias" tras un rechazo.
 *
 * `mensaje` NO se limpia en `cargar()`: así el aviso (confirmación o error) que
 * ponen las acciones sobrevive a la recarga. Solo se limpia en las entradas
 * "frescas" —carga inicial y botón Reintentar— pasando `cargar(true)`.
 *
 * La fila del propio usuario sale con todos los controles deshabilitados: el
 * backend ya bloquea auto-degradarse / auto-desactivarse, y así cerramos también
 * el hueco de las áreas ("no te puedes dejar fuera del panel tú mismo").
 *
 * Cada fila se pinta contraída (nombre + teléfono + rol, y "Inactivo" si aplica).
 * `abierto` guarda el id de la única fila desplegada —acordeón—: al abrir otra se
 * cierra la anterior. La recarga tras una acción no toca `abierto`, así el panel
 * que estabas usando sigue abierto al volver.
 */
@Component({
  selector: 'app-admin-permisos',
  imports: [Volver],
  styleUrl: './permisos.css',
  templateUrl: './permisos.html',
})
export class AdminPermisos implements OnInit {
  private readonly adminService = inject(AdminService);
  protected readonly auth = inject(AuthService);

  protected readonly miembros = signal<MiembroResumen[]>([]);
  protected readonly estado = signal<'cargando' | 'lista' | 'error'>('cargando');
  protected readonly mensaje = signal('');
  protected readonly tipoMensaje = signal<'info' | 'error'>('info');

  /** `[clave, etiqueta][]` de las áreas conocidas, para pintar los checkboxes. */
  protected readonly areasConocidas = Object.entries(AREAS);

  /** Id del miembro con el panel desplegado, o `null` si están todos contraídos. */
  protected readonly abierto = signal<number | null>(null);

  ngOnInit(): void {
    this.cargar(true);
  }

  /** Despliega la fila indicada y cierra cualquier otra; si ya estaba abierta, la cierra. */
  protected alternar(id: number): void {
    this.abierto.set(this.abierto() === id ? null : id);
  }

  /** Rol legible para la cabecera: el superadmin manda sobre el rol de la peña. */
  protected etiquetaRol(m: MiembroResumen): string {
    if (m.esSuperadmin) return 'Superadmin';
    return m.rol === 'ADMIN' ? 'Admin' : 'Miembro';
  }

  /**
   * Recarga la lista desde el backend. `limpiar` a `true` (carga inicial y botón
   * Reintentar) borra cualquier aviso anterior; sin argumento (tras una acción)
   * el aviso que se acaba de poner sobrevive a la recarga.
   */
  protected cargar(limpiar = false): void {
    if (limpiar) {
      this.mensaje.set('');
      this.tipoMensaje.set('info');
    }
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
      next: () => this.trasCambio(),
      error: (e: HttpErrorResponse) => this.avisarError(e),
    });
  }

  protected activar(m: MiembroResumen, activo: boolean): void {
    this.adminService.cambiarActivo(m.id, activo).subscribe({
      next: () => this.trasCambio(),
      error: (e: HttpErrorResponse) => this.avisarError(e),
    });
  }

  protected alternarArea(m: MiembroResumen, area: string, incluir: boolean): void {
    const nuevas = incluir ? [...m.areas, area] : m.areas.filter((a) => a !== area);
    this.adminService.cambiarAreas(m.id, nuevas).subscribe({
      next: () => this.trasCambio(),
      error: (e: HttpErrorResponse) => this.avisarError(e),
    });
  }

  /** Éxito de cualquiera de las tres acciones: confirma, recarga y refresca la sesión. */
  private trasCambio(): void {
    this.mensaje.set('Cambio guardado.');
    this.tipoMensaje.set('info');
    this.cargar();
    // Refresca el usuario de la sesión: si el cambio le afectó (p. ej. sus áreas),
    // el nav de Panel se entera; si fue a otra persona, es inofensivo.
    this.auth.yo().subscribe({ error: () => {} });
  }

  private avisarError(e: HttpErrorResponse): void {
    const codigo = e.error?.codigo as CodigoError | undefined;
    if (codigo && MENSAJES[codigo]) {
      this.mensaje.set(MENSAJES[codigo] as string);
    } else if (e.status === 0) {
      this.mensaje.set('Sin conexión con el servidor.');
    } else {
      this.mensaje.set('No se pudo guardar el cambio.');
    }
    this.tipoMensaje.set('error');
    this.cargar();
  }
}
