import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';
import {
  CatalogoBebidas,
  CodigoErrorEvento,
  EstadoAsistencia,
  EventoDetalle,
  FichaBebidaBody,
} from '../eventos.types';
import { EventosService } from '../eventos.service';
import { FichaBebida } from '../ficha-bebida/ficha-bebida';

/** Texto legible de la modalidad de peñista. */
function modalidadTexto(m: string): string {
  return (
    {
      COMPLETA: 'peña completa',
      SOLO_CERVEZA: 'solo cerveza',
      UN_DIA: 'un día',
      EMBARAZADA: 'embarazada',
    }[m] ?? m
  );
}

/**
 * Vista de un evento. Además de los datos y los botones de gestión
 * (`puedoEditar`/`puedoBorrar`), lleva la parte de asistencia (pieza 3a):
 * responder Me apunto / No voy / En duda, y —solo admin u organizador— mandar
 * la notificación de convocatoria y añadir asistentes a mano.
 */
@Component({
  selector: 'app-evento-detalle',
  imports: [Volver, RouterLink, DatePipe, FichaBebida],
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
  private readonly id = Number(this.route.snapshot.paramMap.get('id'));

  protected readonly opciones: { valor: EstadoAsistencia; texto: string }[] = [
    { valor: 'APUNTADO', texto: 'Me apunto' },
    { valor: 'NO_VOY', texto: 'No voy' },
    { valor: 'EN_DUDA', texto: 'En duda' },
  ];

  // Diálogo "Mandar notificación".
  protected readonly dialogoNotif = signal(false);
  protected readonly textoNotif = signal('');
  protected readonly enviandoNotif = signal(false);

  // Bloque "Añadir a mano".
  protected readonly nombreManual = signal('');
  protected readonly estadoManual = signal<EstadoAsistencia>('APUNTADO');

  // Ficha de bebida (San Miguel).
  protected readonly catalogo = signal<CatalogoBebidas | null>(null);
  protected readonly resultadoFicha = signal('');

  /** La ficha se muestra en un evento de San Miguel al que ya he respondido Me apunto / En duda. */
  protected readonly mostrarFicha = computed(() => {
    const a = this.evento()?.asistencia;
    return (
      !!a?.ficha.llevaFicha &&
      (a.miAsistencia === 'APUNTADO' || a.miAsistencia === 'EN_DUDA') &&
      this.catalogo() != null
    );
  });

  /** En el alta manual sale la ficha si el evento es de San Miguel y el estado elegido la pide. */
  protected readonly fichaEnAltaManual = computed(() => {
    const lleva = this.evento()?.asistencia.ficha.llevaFicha;
    return (
      !!lleva &&
      this.catalogo() != null &&
      (this.estadoManual() === 'APUNTADO' || this.estadoManual() === 'EN_DUDA')
    );
  });

  /** `true` mientras no se pueda reenviar la notificación (último envío + 48 h aún en el futuro). */
  protected readonly reenvioBloqueado = computed(() => {
    const at = this.evento()?.asistencia.notificacionReenviableAt;
    return at != null && new Date(at).getTime() > Date.now();
  });

  private valor(e: Event): string {
    return (e.target as HTMLInputElement | HTMLSelectElement).value;
  }

  protected cambiarNombreManual(e: Event): void {
    this.nombreManual.set(this.valor(e));
  }

  protected cambiarEstadoManual(e: Event): void {
    this.estadoManual.set(this.valor(e) as EstadoAsistencia);
  }

  protected cambiarTextoNotif(e: Event): void {
    this.textoNotif.set(this.valor(e));
  }

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.eventosService.detalle(this.id).subscribe({
      next: (e) => {
        this.evento.set(e);
        this.estado.set('listo');
        if (e.asistencia.ficha.llevaFicha && this.catalogo() == null) {
          this.eventosService.catalogoBebidas().subscribe((c) => this.catalogo.set(c));
        }
      },
      error: () => this.estado.set('error'),
    });
  }

  protected guardarFicha(body: FichaBebidaBody): void {
    this.resultadoFicha.set('');
    this.eventosService.guardarFichaBebida(this.id, body).subscribe({
      next: (r) => {
        this.resultadoFicha.set(
          r.cuota != null
            ? `Tu cuota: ${r.cuota} € (${modalidadTexto(r.modalidad)})`
            : 'Cuota pendiente de que se fije la cuota máxima del evento.',
        );
        this.cargar();
      },
      error: () => this.resultadoFicha.set('No se pudo guardar la ficha.'),
    });
  }

  protected anadirConFicha(ficha: FichaBebidaBody): void {
    const nombre = this.nombreManual().trim();
    if (!nombre) {
      this.aviso.set('Escribe el nombre antes de guardar la ficha.');
      return;
    }
    this.aviso.set('');
    this.eventosService.anadirAsistente(this.id, nombre, this.estadoManual(), ficha).subscribe({
      next: (r) => {
        this.nombreManual.set('');
        this.estadoManual.set('APUNTADO');
        this.aviso.set(
          r.cuota != null ? `«${nombre}» añadido — cuota ${r.cuota} €.` : `«${nombre}» añadido.`,
        );
        this.cargar();
      },
      error: () => this.aviso.set('No se pudo añadir a esa persona.'),
    });
  }

  protected gestionar(): void {
    this.router.navigate(['/panel/eventos', this.id, 'editar']);
  }

  protected borrar(): void {
    this.eventosService.borrar(this.id).subscribe({
      next: (r) => {
        if (r && r.estado === 'PENDIENTE') {
          this.aviso.set('Solicitud de borrado enviada. Un administrador tiene que autorizarla.');
          this.cargar();
        } else {
          this.router.navigate(['/panel/eventos']);
        }
      },
      error: (e: HttpErrorResponse) => {
        const codigo = e.error?.codigo as CodigoErrorEvento | undefined;
        this.aviso.set(
          codigo === 'SOLICITUD_EVENTO_YA_PENDIENTE'
            ? 'Ya hay una solicitud de borrado pendiente para este evento.'
            : 'No se pudo borrar el evento.',
        );
        this.cargar();
      },
    });
  }

  protected responder(estado: EstadoAsistencia): void {
    this.aviso.set('');
    this.eventosService.responder(this.id, estado).subscribe({
      next: (e) => this.evento.set(e),
      error: (e: HttpErrorResponse) => {
        const codigo = e.error?.codigo as CodigoErrorEvento | undefined;
        this.aviso.set(
          codigo === 'EVENTO_YA_PASADO'
            ? 'El evento ya ha pasado, no se puede cambiar la respuesta.'
            : 'No se pudo guardar tu respuesta.',
        );
      },
    });
  }

  protected abrirDialogoNotif(): void {
    this.textoNotif.set('');
    this.dialogoNotif.set(true);
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

  protected anadirManual(): void {
    const nombre = this.nombreManual().trim();
    if (!nombre) {
      return;
    }
    this.aviso.set('');
    this.eventosService.anadirAsistente(this.id, nombre, this.estadoManual()).subscribe({
      next: () => {
        this.nombreManual.set('');
        this.estadoManual.set('APUNTADO');
        this.aviso.set(`«${nombre}» añadido.`);
        this.cargar();
      },
      error: () => this.aviso.set('No se pudo añadir a esa persona.'),
    });
  }
}
