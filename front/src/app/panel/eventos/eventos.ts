import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { CodigoErrorEvento, EventoResumen } from './eventos.types';
import { EventosService } from './eventos.service';

/**
 * Mensajes en castellano para los códigos de error del backend al solicitar un
 * crédito de creación. `Partial<Record<...>>`: una errata en una clave es error
 * de compilación y un código no mapeado cae al mensaje por defecto.
 */
const MENSAJES: Partial<Record<CodigoErrorEvento, string>> = {
  SOLICITUD_EVENTO_YA_PENDIENTE: 'Ya tienes una solicitud de evento pendiente.',
  CREDITO_SIN_CONSUMIR: 'Ya tienes un evento autorizado sin crear. Créalo antes de pedir otro.',
  SOLICITUD_EVENTO_NO_APLICA: 'Como administrador puedes crear eventos directamente.',
};

/**
 * Listado de eventos de la peña. Cada evento es un botón que lleva a su detalle
 * (`/panel/eventos/:id`). Arriba, según los permisos que devuelve el backend:
 * "Crear evento" (`puedeCrear`) o "Solicitar crear evento" (`puedeSolicitar`,
 * abre un diálogo con un mensaje opcional). Paginado de 8 (lo pagina el backend).
 */
@Component({
  selector: 'app-eventos',
  templateUrl: './eventos.html',
  styleUrl: './eventos.css',
})
export class Eventos implements OnInit {
  private readonly eventosService = inject(EventosService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  protected readonly estado = signal<'cargando' | 'lista' | 'error'>('cargando');
  protected readonly eventos = signal<EventoResumen[]>([]);
  protected readonly pagina = signal(0);
  protected readonly totalPaginas = signal(1);
  protected readonly puedeCrear = signal(false);
  protected readonly puedeSolicitar = signal(false);
  protected readonly esAdmin = signal(false);

  protected readonly dialogoSolicitud = signal(false);
  protected readonly mensajeSolicitud = signal('');
  protected readonly aviso = signal('');

  ngOnInit(): void {
    this.cargar(0);
    this.auth.asegurarYo().subscribe((u) =>
      this.esAdmin.set(u?.rol === 'ADMIN' || u?.esSuperadmin === true),
    );
  }

  protected cargar(pagina: number): void {
    this.estado.set('cargando');
    this.eventosService.listar(pagina).subscribe({
      next: (r) => {
        this.eventos.set(r.eventos);
        this.pagina.set(r.pagina);
        this.totalPaginas.set(r.totalPaginas);
        this.puedeCrear.set(r.puedeCrear);
        this.puedeSolicitar.set(r.puedeSolicitar);
        this.estado.set('lista');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected anterior(): void {
    if (this.pagina() > 0) this.cargar(this.pagina() - 1);
  }

  protected siguiente(): void {
    if (this.pagina() + 1 < this.totalPaginas()) this.cargar(this.pagina() + 1);
  }

  protected abrir(e: EventoResumen): void {
    this.router.navigate(['/panel/eventos', e.id]);
  }

  protected crear(): void {
    this.router.navigate(['/panel/eventos/nuevo']);
  }

  protected verOcultos(): void {
    this.router.navigate(['/panel/eventos/ocultos']);
  }

  protected abrirDialogoSolicitud(): void {
    this.mensajeSolicitud.set('');
    this.dialogoSolicitud.set(true);
  }

  protected enviarSolicitud(): void {
    this.eventosService.solicitarCrear(this.mensajeSolicitud() || undefined).subscribe({
      next: () => {
        this.dialogoSolicitud.set(false);
        this.puedeSolicitar.set(false);
        this.aviso.set('Solicitud enviada. Un administrador tiene que autorizarla.');
      },
      error: (e: HttpErrorResponse) => {
        const codigo = e.error?.codigo as CodigoErrorEvento | undefined;
        this.dialogoSolicitud.set(false);
        this.aviso.set((codigo && MENSAJES[codigo]) || 'No se pudo enviar la solicitud.');
        this.cargar(this.pagina());
      },
    });
  }
}
