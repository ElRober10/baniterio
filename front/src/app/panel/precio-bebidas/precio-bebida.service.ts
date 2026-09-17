import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EventoPrecioBebida } from './precio-bebida.types';

/** Llamadas de la sección Precio bebidas. */
@Injectable({ providedIn: 'root' })
export class PrecioBebidaService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  eventos(): Observable<EventoPrecioBebida[]> {
    return this.http.get<EventoPrecioBebida[]>(`${this.base}/precio-bebida/eventos`);
  }
}
