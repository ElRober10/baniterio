import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../auth/auth.service';
import { UsuarioDto } from '../auth/auth.types';
import { environment } from '../../environments/environment';
import { AdminAvisosService } from './admin-avisos.service';

/**
 * `AdminAvisosService`: `refrescar()` pide GET /admin/pendientes y publica el
 * resultado en el signal `pendientes`; `total` suma los valores. Un error del
 * backend no rompe: deja el último valor conocido. Al cerrar sesión el signal
 * se vacía para no contaminar la siguiente sesión de la misma pestaña.
 */
describe('AdminAvisosService', () => {
  let servicio: AdminAvisosService;
  let httpMock: HttpTestingController;
  const url = `${environment.apiBaseUrl}/admin/pendientes`;

  const usuarioSesion = signal<UsuarioDto | null>({
    id: 1,
    nombre: 'Ada',
    apellidos: 'Lovelace',
    mote: null,
    esSuperadmin: false,
    rol: 'MIEMBRO',
    areas: ['ADMIN_SOLICITUDES'],
  });
  const authFalso: Partial<AuthService> = { usuarioActual: usuarioSesion };

  beforeEach(() => {
    usuarioSesion.set({
      id: 1,
      nombre: 'Ada',
      apellidos: 'Lovelace',
      mote: null,
      esSuperadmin: false,
      rol: 'MIEMBRO',
      areas: ['ADMIN_SOLICITUDES'],
    });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authFalso },
      ],
    });
    servicio = TestBed.inject(AdminAvisosService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('empieza vacío con total 0', () => {
    expect(servicio.pendientes()).toEqual({});
    expect(servicio.total()).toBe(0);
  });

  it('refrescar() puebla el signal y total suma los valores', () => {
    servicio.refrescar();
    httpMock.expectOne({ method: 'GET', url }).flush({ ADMIN_SOLICITUDES: 3 });

    expect(servicio.pendientes()).toEqual({ ADMIN_SOLICITUDES: 3 });
    expect(servicio.total()).toBe(3);
  });

  it('total suma los pendientes de todas las áreas', () => {
    servicio.refrescar();
    httpMock.expectOne({ method: 'GET', url }).flush({ ADMIN_SOLICITUDES: 3, ADMIN_PERMISOS: 2 });

    expect(servicio.total()).toBe(5);
  });

  it('un error del backend deja el último valor conocido', () => {
    servicio.refrescar();
    httpMock.expectOne({ method: 'GET', url }).flush({ ADMIN_SOLICITUDES: 2 });

    servicio.refrescar();
    httpMock
      .expectOne({ method: 'GET', url })
      .error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });

    expect(servicio.pendientes()).toEqual({ ADMIN_SOLICITUDES: 2 });
    expect(servicio.total()).toBe(2);
  });

  it('al cerrar sesión vacía el signal', () => {
    servicio.refrescar();
    httpMock.expectOne({ method: 'GET', url }).flush({ ADMIN_SOLICITUDES: 4 });
    expect(servicio.total()).toBe(4);

    usuarioSesion.set(null);
    TestBed.tick();

    expect(servicio.pendientes()).toEqual({});
    expect(servicio.total()).toBe(0);
  });
});
