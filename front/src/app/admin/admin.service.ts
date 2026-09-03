import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AprobarResultado,
  MiembroResumen,
  Rol,
  SolicitudEventoResumen,
  SolicitudResumen,
} from './admin.types';

/**
 * Acciones del panel de administración contra /api/v1/admin/* (listar y resolver
 * solicitudes, gestionar miembros). Un método por endpoint; los componentes de
 * /panel/administracion se suscriben. No maneja errores: los deja propagar para
 * que cada pantalla decida qué mensaje enseñar según el `codigo` que traiga el
 * backend.
 *
 * <p>El recuento de la campanita (`GET /admin/pendientes`) NO pasa por aquí: lo
 * pide `AdminAvisosService` directamente, porque es estado compartido con signal
 * y no una acción puntual de pantalla.
 */
@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  /** Solicitudes de ingreso filtradas por estado (por defecto, las pendientes). */
  listarSolicitudes(estado = 'PENDIENTE'): Observable<SolicitudResumen[]> {
    return this.http.get<SolicitudResumen[]>(`${this.base}/admin/solicitudes`, {
      params: { estado },
    });
  }

  /** Aprueba una solicitud. El backend responde qué hizo (crear cuenta o solo autorizar el teléfono). */
  aprobarSolicitud(id: number): Observable<{ resultado: AprobarResultado }> {
    return this.http.post<{ resultado: AprobarResultado }>(
      `${this.base}/admin/solicitudes/${id}/aprobar`,
      {},
    );
  }

  /** Rechaza una solicitud, con un motivo opcional para el registro. */
  rechazarSolicitud(id: number, motivo?: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/admin/solicitudes/${id}/rechazar`,
      motivo ? { motivo } : {},
    );
  }

  listarMiembros(): Observable<MiembroResumen[]> {
    return this.http.get<MiembroResumen[]>(`${this.base}/admin/miembros`);
  }

  cambiarRol(id: number, rol: Rol): Observable<void> {
    return this.http.put<void>(`${this.base}/admin/miembros/${id}/rol`, { rol });
  }

  cambiarActivo(id: number, activo: boolean): Observable<void> {
    return this.http.put<void>(`${this.base}/admin/miembros/${id}/activo`, { activo });
  }

  cambiarAreas(id: number, areas: string[]): Observable<void> {
    return this.http.put<void>(`${this.base}/admin/miembros/${id}/areas`, { areas });
  }

  // --- Solicitudes de evento (solo admin/superadmin; el backend exige rol) ---

  listarSolicitudesEvento(estado = 'PENDIENTE'): Observable<SolicitudEventoResumen[]> {
    return this.http.get<SolicitudEventoResumen[]>(`${this.base}/admin/solicitudes-evento`, {
      params: { estado },
    });
  }

  aprobarSolicitudEvento(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/solicitudes-evento/${id}/aprobar`, {});
  }

  rechazarSolicitudEvento(id: number, motivo?: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/admin/solicitudes-evento/${id}/rechazar`,
      motivo ? { motivo } : {},
    );
  }
}
