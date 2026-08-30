import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { CodigoError } from '../../auth/auth.types';
import { Volver } from '../../shared/volver/volver';
import { AdminService } from '../admin.service';
import { SolicitudResumen } from '../admin.types';

/**
 * Mensajes en castellano para los códigos de error del backend en aprobar/rechazar.
 * `Partial<Record<CodigoError, string>>`: una errata en una clave es error de
 * compilación y un código no mapeado cae al mensaje por defecto.
 */
const MENSAJES: Partial<Record<CodigoError, string>> = {
  SIN_PERMISO: 'No tienes permiso para esto.',
  YA_REGISTRADO: 'Ya existe una cuenta con ese teléfono o email.',
};

/**
 * Pantalla de administración de solicitudes de ingreso. Se pinta en el
 * `<router-outlet>` de `Panel` en `/panel/administracion/solicitudes`, tras
 * pasar `areaGuard('ADMIN_SOLICITUDES')`.
 *
 * Lista las solicitudes pendientes y deja aprobarlas (el backend decide si crea
 * la cuenta o solo autoriza el teléfono) o rechazarlas con un motivo opcional.
 *
 * Estado en signals:
 * - `solicitudes`: las filas que se pintan.
 * - `estado`: 'cargando' | 'lista' | 'error' — controla el `@switch` de la plantilla.
 * - `mensaje`: banda de aviso (éxito o error) arriba de la lista.
 * - `tipoMensaje`: 'error' la pinta en rojo; 'info' con el estilo de marca (éxito).
 * - `rechazandoId`: id de la solicitud cuyo textarea de motivo está abierto (o null).
 * - `motivoRechazo`: texto del textarea. Sin `FormsModule`: se enlaza a mano con
 *   `[value]` + `(input)` en la plantilla.
 *
 * `avisarError` traduce el `codigo` que trae el backend a un mensaje en castellano;
 * si la solicitud ya la resolvió otra persona, además recarga la lista.
 */
@Component({
  selector: 'app-admin-solicitudes',
  imports: [Volver],
  styleUrl: './solicitudes.css',
  templateUrl: './solicitudes.html',
})
export class AdminSolicitudes implements OnInit {
  private readonly adminService = inject(AdminService);

  protected readonly solicitudes = signal<SolicitudResumen[]>([]);
  protected readonly estado = signal<'cargando' | 'lista' | 'error'>('cargando');
  protected readonly mensaje = signal('');
  protected readonly tipoMensaje = signal<'info' | 'error'>('info');
  protected readonly rechazandoId = signal<number | null>(null);
  protected readonly motivoRechazo = signal('');

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.adminService.listarSolicitudes().subscribe({
      next: (lista) => {
        this.solicitudes.set(lista);
        this.estado.set('lista');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected aprobar(s: SolicitudResumen): void {
    this.adminService.aprobarSolicitud(s.id).subscribe({
      next: (r) => {
        this.mensaje.set(
          r.resultado === 'CUENTA_CREADA'
            ? 'Cuenta creada y correo enviado.'
            : 'Teléfono autorizado y correo enviado.',
        );
        this.tipoMensaje.set('info');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.avisarError(e),
    });
  }

  protected abrirRechazo(id: number): void {
    this.rechazandoId.set(id);
    this.motivoRechazo.set('');
  }

  protected cancelarRechazo(): void {
    this.rechazandoId.set(null);
  }

  protected confirmarRechazo(s: SolicitudResumen): void {
    this.adminService.rechazarSolicitud(s.id, this.motivoRechazo() || undefined).subscribe({
      next: () => {
        this.rechazandoId.set(null);
        this.mensaje.set('Solicitud rechazada. Se ha enviado el correo.');
        this.tipoMensaje.set('info');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.avisarError(e),
    });
  }

  private avisarError(e: HttpErrorResponse): void {
    const codigo = e.error?.codigo as CodigoError | undefined;
    this.tipoMensaje.set('error');
    if (codigo === 'SOLICITUD_YA_RESUELTA') {
      this.mensaje.set('Esa solicitud ya la había resuelto alguien. Recargo la lista.');
      this.cargar();
      return;
    }
    if (codigo && MENSAJES[codigo]) {
      this.mensaje.set(MENSAJES[codigo] as string);
      return;
    }
    if (e.status === 0) {
      this.mensaje.set('Sin conexión con el servidor.');
      return;
    }
    this.mensaje.set('No se pudo completar la acción.');
  }
}
