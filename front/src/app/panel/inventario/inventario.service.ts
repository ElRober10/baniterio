import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ArticuloInventario,
  CambioArticulo,
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
}
