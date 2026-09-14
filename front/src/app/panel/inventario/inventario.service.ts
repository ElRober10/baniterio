import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ArticuloInventario,
  CambioArticulo,
  CategoriaClave,
  EventoAbierto,
  InventarioFiestaResponse,
  InventarioResponse,
  NuevoArticulo,
} from './inventario.types';

/**
 * Llamadas de la sección Inventario (`/api/v1/inventario*`). Un método por
 * endpoint; no maneja errores, los deja propagar para que la pantalla traduzca
 * el `codigo` del backend (mismo patrón que `CuentasService`).
 */
@Injectable({ providedIn: 'root' })
export class InventarioService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  ver(): Observable<InventarioResponse> {
    return this.http.get<InventarioResponse>(`${this.base}/inventario`);
  }

  actualizar(id: number, cambio: CambioArticulo): Observable<ArticuloInventario> {
    return this.http.put<ArticuloInventario>(`${this.base}/inventario/${id}`, cambio);
  }

  crear(nuevo: NuevoArticulo): Observable<ArticuloInventario> {
    return this.http.post<ArticuloInventario>(`${this.base}/inventario`, nuevo);
  }

  borrar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/inventario/${id}`);
  }

  /** Eventos "abiertos" para el selector de "enviar a evento". */
  eventosAbiertos(): Observable<EventoAbierto[]> {
    return this.http.get<EventoAbierto[]>(`${this.base}/eventos/abiertos`);
  }

  /** Mueve toda la cantidad de un artículo al inventario de un evento. */
  enviarAEvento(articuloId: number, eventoId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/inventario/${articuloId}/enviar`, { eventoId });
  }

  /** "Enviar todo": mueve al evento todas las filas de la categoría con cantidad > 0. */
  enviarCategoria(categoria: CategoriaClave, eventoId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/inventario/enviar-categoria`, { categoria, eventoId });
  }

  /** El inventario que se ha enviado a un evento. */
  inventarioFiesta(eventoId: number): Observable<InventarioFiestaResponse> {
    return this.http.get<InventarioFiestaResponse>(`${this.base}/inventario/evento/${eventoId}`);
  }

  /** Devuelve la parte de stock de una línea del inventario de la fiesta al inventario general. */
  devolverAInventario(eventoId: number, articuloEventoId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/inventario/evento/${eventoId}/${articuloEventoId}/devolver`,
      {},
    );
  }

  /** Devuelve la parte comprada de una línea del inventario de la fiesta a la lista de la compra. */
  devolverALista(eventoId: number, articuloEventoId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/inventario/evento/${eventoId}/${articuloEventoId}/devolver-a-lista`,
      {},
    );
  }
}
