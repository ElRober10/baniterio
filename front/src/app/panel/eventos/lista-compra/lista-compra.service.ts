import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  AjustarRegla,
  CrearRegla,
  EventoListaCompra,
  ListaCompraAdminResponse,
  ListaCompraResponse,
  ReglaCompraEvento,
} from './lista-compra.types';

/**
 * Llamadas de la lista de la compra por evento. Un método por endpoint; los
 * errores se dejan propagar para que la pantalla traduzca el `codigo` del
 * backend (mismo patrón que `InventarioService`).
 */
@Injectable({ providedIn: 'root' })
export class ListaCompraService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  lista(eventoId: number): Observable<ListaCompraResponse> {
    return this.http.get<ListaCompraResponse>(`${this.base}/eventos/${eventoId}/lista-compra`);
  }

  adminEventos(): Observable<EventoListaCompra[]> {
    return this.http.get<EventoListaCompra[]>(`${this.base}/admin/lista-compra/eventos`);
  }

  adminEvento(eventoId: number): Observable<ListaCompraAdminResponse> {
    return this.http.get<ListaCompraAdminResponse>(`${this.base}/admin/lista-compra/eventos/${eventoId}`);
  }

  ajustarRegla(eventoId: number, reglaId: number, body: AjustarRegla): Observable<void> {
    return this.http.put<void>(
      `${this.base}/admin/lista-compra/eventos/${eventoId}/reglas/${reglaId}`,
      body,
    );
  }

  crearRegla(eventoId: number, body: CrearRegla): Observable<ReglaCompraEvento> {
    return this.http.post<ReglaCompraEvento>(
      `${this.base}/admin/lista-compra/eventos/${eventoId}/reglas`,
      body,
    );
  }

  borrarRegla(eventoId: number, reglaId: number): Observable<void> {
    return this.http.delete<void>(
      `${this.base}/admin/lista-compra/eventos/${eventoId}/reglas/${reglaId}`,
    );
  }

  /** Marca una línea como comprada: pasa al inventario de la fiesta. */
  comprado(eventoId: number, lineaId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/eventos/${eventoId}/lista-compra/lineas/${lineaId}/comprado`,
      {},
    );
  }

  /** Bloquea o desbloquea el auto-cálculo de la lista de la compra. */
  bloqueo(eventoId: number, bloqueada: boolean): Observable<void> {
    return this.http.put<void>(`${this.base}/eventos/${eventoId}/lista-compra/bloqueo`, { bloqueada });
  }

  /** Quita la modificación a mano de la marca o artículo de una línea y lo recalcula. */
  restablecerLinea(eventoId: number, lineaId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/eventos/${eventoId}/lista-compra/lineas/${lineaId}/restablecer`,
      {},
    );
  }

  /** Quita todas las modificaciones a mano de la lista. */
  restablecerTodo(eventoId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/eventos/${eventoId}/lista-compra/restablecer`, {});
  }

  /** Ajusta a mano la cantidad de una línea; solo con la lista bloqueada. */
  ajustarLinea(eventoId: number, lineaId: number, cantidad: number, tamano?: string, tienda?: string): Observable<void> {
    return this.http.put<void>(
      `${this.base}/eventos/${eventoId}/lista-compra/lineas/${lineaId}`,
      tienda ? { cantidad, tienda } : tamano ? { cantidad, tamano } : { cantidad },
    );
  }
}
