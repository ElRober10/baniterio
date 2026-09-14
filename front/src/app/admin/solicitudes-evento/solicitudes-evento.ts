import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { AdminService } from '../admin.service';
import { SolicitudEventoResumen } from '../admin.types';

/**
 * Bloque "Solicitudes de evento" que se embebe en la pantalla de solicitudes de
 * ingreso. El contenedor solo lo monta si el usuario es admin/superadmin (el
 * backend además lo exige: 403 SIN_PERMISO si no). Lista las de tipo CREAR y
 * BORRAR pendientes y deja aprobar / rechazar (con motivo opcional).
 */
@Component({
  selector: 'app-solicitudes-evento',
  templateUrl: './solicitudes-evento.html',
  styleUrl: './solicitudes-evento.css',
})
export class SolicitudesEvento implements OnInit {
  private readonly adminService = inject(AdminService);

  protected readonly solicitudes = signal<SolicitudEventoResumen[]>([]);
  protected readonly estado = signal<'cargando' | 'lista' | 'error'>('cargando');
  protected readonly mensaje = signal('');
  protected readonly rechazandoId = signal<number | null>(null);
  protected readonly motivo = signal('');
  protected readonly confirmandoBorrarId = signal<number | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.adminService.listarSolicitudesEvento().subscribe({
      next: (l) => {
        this.solicitudes.set(l);
        this.estado.set('lista');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected aprobar(s: SolicitudEventoResumen): void {
    if (s.tipo === 'BORRAR') {
      this.confirmandoBorrarId.set(s.id);
      return;
    }
    this.ejecutarAprobacion(s);
  }

  protected cancelarConfirmacionBorrar(): void {
    this.confirmandoBorrarId.set(null);
  }

  protected confirmarAprobarBorrado(s: SolicitudEventoResumen): void {
    this.confirmandoBorrarId.set(null);
    this.ejecutarAprobacion(s);
  }

  private ejecutarAprobacion(s: SolicitudEventoResumen): void {
    this.adminService.aprobarSolicitudEvento(s.id).subscribe({
      next: () => {
        this.mensaje.set(s.tipo === 'CREAR' ? 'Crédito concedido.' : 'Evento borrado.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.avisar(e),
    });
  }

  protected abrirRechazo(id: number): void {
    this.rechazandoId.set(id);
    this.motivo.set('');
  }

  protected cancelarRechazo(): void {
    this.rechazandoId.set(null);
  }

  protected confirmarRechazo(s: SolicitudEventoResumen): void {
    this.adminService.rechazarSolicitudEvento(s.id, this.motivo() || undefined).subscribe({
      next: () => {
        this.rechazandoId.set(null);
        this.mensaje.set('Solicitud rechazada.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.avisar(e),
    });
  }

  private avisar(e: HttpErrorResponse): void {
    const codigo = e.error?.codigo as string | undefined;
    if (codigo === 'SOLICITUD_EVENTO_YA_RESUELTA') {
      this.mensaje.set('Esa solicitud ya la resolvió alguien. Recargo la lista.');
      this.cargar();
      return;
    }
    this.mensaje.set(codigo === 'SIN_PERMISO' ? 'No tienes permiso.' : 'No se pudo completar la acción.');
  }
}
