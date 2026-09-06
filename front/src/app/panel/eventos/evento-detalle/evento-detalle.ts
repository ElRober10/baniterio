import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';
import { FichaBebida } from '../ficha-bebida/ficha-bebida';
import { ModalAsistentes } from '../modal-asistentes/modal-asistentes';
import { ModalHePagado } from '../modal-he-pagado/modal-he-pagado';
import { CatalogoBebidas, EventoDetalle, FichaBebidaBody } from '../eventos.types';
import { EventosService } from '../eventos.service';

/**
 * Vista de un evento: sus datos y —solo si `puedoEditar`/`puedoBorrar`, es
 * decir, admin/superadmin— los botones Editar y Ocultar/Recuperar dentro de la
 * tarjeta. "Borrar" un evento lo oculta (no lo quita de la BBDD); `e.oculto`
 * dice si ya lo está, y entonces el botón pasa a ser "Recuperar". La parte de
 * asistencia (responder, ficha de bebida, convocatoria, añadir a mano) se movió
 * a `AsistenciaEventoComponent` mientras se rediseña; aquí solo queda el
 * recuento de asistentes y el botón de mandar/reenviar la convocatoria.
 */
@Component({
  selector: 'app-evento-detalle',
  imports: [Volver, RouterLink, DatePipe, FichaBebida, ModalAsistentes, ModalHePagado],
  templateUrl: './evento-detalle.html',
  styleUrl: './evento-detalle.css',
})
export class EventoDetalleComponent implements OnInit {
  private readonly eventosService = inject(EventosService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly evento = signal<EventoDetalle | null>(null);
  protected readonly aviso = signal('');
  protected readonly modalAsistentes = signal(false);
  protected readonly modalPago = signal(false);
  private readonly id = Number(this.route.snapshot.paramMap.get('id'));

  /** Se muestra el bloque de cuotas solo si un admin ha puesto al menos una. */
  protected readonly hayCuotas = computed(() => {
    const e = this.evento();
    return (
      !!e &&
      (e.cuotaCubatas != null ||
        e.cuotaCervezas != null ||
        e.cuotaCubatas1Dia != null ||
        e.cuotaCervezas1Dia != null ||
        e.cuotaEmbarazada != null)
    );
  });

  // Diálogo "Mandar notificación".
  protected readonly dialogoNotif = signal(false);
  protected readonly textoNotif = signal('');
  protected readonly enviandoNotif = signal(false);

  /** Confirmación antes de borrar (ocultar) el evento. */
  protected readonly confirmarBorrado = signal(false);

  /** `true` mientras no se pueda reenviar la notificación (último envío + 48 h aún en el futuro). */
  protected readonly reenvioBloqueado = computed(() => {
    const at = this.evento()?.asistencia.notificacionReenviableAt;
    return at != null && new Date(at).getTime() > Date.now();
  });

  // Editor de "mi ficha" (bebida y cuota): se puede cambiar hasta el mismo
  // día del evento, mismo límite que aplica el backend al guardar.
  protected readonly editandoFicha = signal(false);
  protected readonly catalogoFicha = signal<CatalogoBebidas | null>(null);
  protected readonly puedeEditarFicha = computed(() => {
    const e = this.evento();
    if (!e?.asistencia.ficha.miFicha) {
      return false;
    }
    const hoy = new Date();
    const hoyIso = `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`;
    return e.fecha >= hoyIso;
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.eventosService.detalle(this.id).subscribe({
      next: (e) => {
        this.evento.set(e);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected editar(): void {
    this.router.navigate(['/panel/eventos', this.id, 'editar']);
  }

  protected onPagoEnviado(): void {
    this.modalPago.set(false);
    this.aviso.set('Pago confirmado. Un administrador lo revisará.');
  }

  protected abrirEditorFicha(): void {
    this.aviso.set('');
    if (this.catalogoFicha()) {
      this.editandoFicha.set(true);
      return;
    }
    this.eventosService.catalogoBebidas().subscribe({
      next: (c) => {
        this.catalogoFicha.set(c);
        this.editandoFicha.set(true);
      },
      error: () => this.aviso.set('No se pudo cargar el catálogo de bebidas.'),
    });
  }

  protected cerrarEditorFicha(): void {
    this.editandoFicha.set(false);
  }

  protected guardarFicha(body: FichaBebidaBody): void {
    this.eventosService.guardarFichaBebida(this.id, body).subscribe({
      next: () => {
        this.editandoFicha.set(false);
        this.aviso.set('Tu ficha se ha actualizado.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.aviso.set(this.mensajeFichaError(e.error?.codigo)),
    });
  }

  private mensajeFichaError(codigo: string | undefined): string {
    if (codigo === 'EVENTO_YA_PASADO') {
      return 'Ya no se puede cambiar: el evento ya ha empezado.';
    }
    return 'No se pudo guardar tu ficha.';
  }

  protected pedirBorrar(): void {
    this.confirmarBorrado.set(true);
  }

  protected cancelarBorrado(): void {
    this.confirmarBorrado.set(false);
  }

  protected ocultar(): void {
    this.confirmarBorrado.set(false);
    this.eventosService.ocultar(this.id).subscribe({
      next: () => this.router.navigate(['/panel/eventos']),
      error: () => {
        this.aviso.set('No se pudo ocultar el evento.');
        this.cargar();
      },
    });
  }

  protected recuperar(): void {
    this.eventosService.recuperar(this.id).subscribe({
      next: (e) => {
        this.evento.set(e);
        this.aviso.set('Evento recuperado.');
      },
      error: () => this.aviso.set('No se pudo recuperar el evento.'),
    });
  }

  protected abrirDialogoNotif(): void {
    this.textoNotif.set('');
    this.dialogoNotif.set(true);
  }

  protected cambiarTextoNotif(e: Event): void {
    this.textoNotif.set((e.target as HTMLTextAreaElement).value);
  }

  protected enviarNotif(): void {
    this.enviandoNotif.set(true);
    const texto = this.textoNotif().trim();
    this.eventosService.mandarNotificacion(this.id, texto || undefined).subscribe({
      next: () => {
        this.enviandoNotif.set(false);
        this.dialogoNotif.set(false);
        this.aviso.set('Notificación enviada.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => {
        this.enviandoNotif.set(false);
        this.dialogoNotif.set(false);
        this.aviso.set(this.mensajeNotifError(e.error?.codigo));
        this.cargar();
      },
    });
  }

  private mensajeNotifError(codigo: string | undefined): string {
    if (codigo === 'NOTIFICACION_REENVIO_PRONTO') {
      return 'Aún no se puede reenviar: hay que esperar 48 h desde el último envío.';
    }
    if (codigo === 'SIN_PERMISO_EVENTO') {
      return 'No tienes permiso para mandar la notificación.';
    }
    return 'No se pudo enviar la notificación.';
  }
}
