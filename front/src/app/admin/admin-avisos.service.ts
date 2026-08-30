import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { environment } from '../../environments/environment';
import { PendientesPorArea } from './admin.types';

/**
 * Estado compartido de la "campanita" del panel: cuántas cosas tiene sin atender
 * el usuario por área. Vive en `root` para que la home del panel, el índice de
 * administración y la pantalla de solicitudes lean el mismo número.
 *
 * `refrescar()` se llama al entrar en esas pantallas y tras resolver una
 * solicitud. Es silencioso ante error (la campanita es información secundaria):
 * deja el último valor conocido y no rompe la pantalla.
 */
@Injectable({ providedIn: 'root' })
export class AdminAvisosService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  private readonly _pendientes = signal<PendientesPorArea>({});

  /** Pendientes por área. Área ausente = 0. */
  readonly pendientes = this._pendientes.asReadonly();

  /** Suma de todos los pendientes (para la tarjeta "Administración"). */
  readonly total = computed(() =>
    Object.values(this._pendientes()).reduce((suma, n) => suma + (n ?? 0), 0),
  );

  /** Vuelve a pedir el recuento al backend. */
  refrescar(): void {
    this.http.get<PendientesPorArea>(`${this.base}/admin/pendientes`).subscribe({
      next: (mapa) => this._pendientes.set(mapa),
      error: () => {},
    });
  }
}
