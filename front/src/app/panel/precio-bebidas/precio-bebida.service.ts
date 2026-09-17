import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EventoPrecioBebida, GrillaAlcohol, Tienda } from './precio-bebida.types';

/** Llamadas de la sección Precio bebidas. */
@Injectable({ providedIn: 'root' })
export class PrecioBebidaService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  eventos(): Observable<EventoPrecioBebida[]> {
    return this.http.get<EventoPrecioBebida[]>(`${this.base}/precio-bebida/eventos`);
  }

  tiendas(): Observable<Tienda[]> {
    return this.http.get<Tienda[]>(`${this.base}/precio-bebida/tiendas`);
  }

  crearTienda(nombre: string): Observable<Tienda> {
    return this.http.post<Tienda>(`${this.base}/precio-bebida/tiendas`, { nombre });
  }

  alcohol(eventoId: number): Observable<GrillaAlcohol> {
    return this.http.get<GrillaAlcohol>(`${this.base}/precio-bebida/eventos/${eventoId}/alcohol`);
  }

  guardarPrecio(
    eventoId: number,
    body: { bebidaId: number; tamano: string; tiendaId: number; precio: number | null },
  ): Observable<void> {
    return this.http.put<void>(`${this.base}/precio-bebida/eventos/${eventoId}/alcohol/precio`, body);
  }

  anadirTamano(eventoId: number, tamano: string): Observable<string[]> {
    return this.http.post<string[]>(`${this.base}/precio-bebida/eventos/${eventoId}/alcohol/tamanos`, {
      tamano,
    });
  }
}
