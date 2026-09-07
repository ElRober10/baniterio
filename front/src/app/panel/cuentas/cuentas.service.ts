import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CuentaResumen, CuentaDetalle } from './cuentas.types';

/**
 * Llamadas de la sección Cuentas (`/api/v1/cuentas*`). Un método por endpoint;
 * no maneja errores, los deja propagar para que cada pantalla traduzca el
 * `codigo` del backend (mismo patrón que `EventosService`).
 */
@Injectable({ providedIn: 'root' })
export class CuentasService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  listar(): Observable<CuentaResumen[]> {
    return this.http.get<CuentaResumen[]>(`${this.base}/cuentas`);
  }

  detalle(id: number): Observable<CuentaDetalle> {
    return this.http.get<CuentaDetalle>(`${this.base}/cuentas/${id}`);
  }

  /** El admin marca que ha ingresado en la cuenta de la peña lo cobrado por bizum/efectivo. */
  marcarTransferido(id: number): Observable<CuentaDetalle> {
    return this.http.post<CuentaDetalle>(`${this.base}/cuentas/${id}/transferencia-a-pena`, {});
  }
}
