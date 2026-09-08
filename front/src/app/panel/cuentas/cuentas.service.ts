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

  /** El admin marca que ha ingresado en el banco lo cobrado por bizum/efectivo. */
  marcarTransferido(id: number): Observable<CuentaDetalle> {
    return this.http.post<CuentaDetalle>(`${this.base}/cuentas/${id}/transferencia-a-pena`, {});
  }

  /** Alta de un gasto o ingreso manual. `datos` es un FormData (puede llevar el recibo). */
  crearMovimiento(id: number, datos: FormData): Observable<CuentaDetalle> {
    return this.http.post<CuentaDetalle>(`${this.base}/cuentas/${id}/movimientos`, datos);
  }

  borrarMovimiento(movId: number): Observable<CuentaDetalle> {
    return this.http.delete<CuentaDetalle>(`${this.base}/cuentas/movimientos/${movId}`);
  }

  /** Apunta cuánta camiseta/sudadera pide un peñista y de qué talla (admin). */
  marcarRopa(
    cuentaId: number,
    asistenciaId: number,
    cambio: {
      camisetaCantidad?: number;
      camisetaTalla?: string;
      camisetaConfirmada?: boolean;
      sudaderaCantidad?: number;
      sudaderaTalla?: string;
      sudaderaConfirmada?: boolean;
    },
  ): Observable<CuentaDetalle> {
    return this.http.put<CuentaDetalle>(
      `${this.base}/cuentas/${cuentaId}/asistencias/${asistenciaId}/ropa`,
      cambio,
    );
  }

  /** URL del recibo de un movimiento (se sirve por /media, nombre UUID). */
  urlRecibo(archivo: string): string {
    return `${this.base}/media/recibos/${archivo}`;
  }
}
