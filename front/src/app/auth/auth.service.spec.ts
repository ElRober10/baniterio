import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';
import { UsuarioDto } from './auth.types';

/**
 * Tests de AuthService centrados en lo que aporta el panel de administración:
 * leer `rol` + `areas` de GET /auth/yo, cachearlos en el signal `usuarioActual`
 * y responder `tieneArea(...)`.
 */
describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  const usuarioAdmin: UsuarioDto = {
    id: 7,
    nombre: 'Ada',
    apellidos: 'Lovelace',
    mote: null,
    esSuperadmin: false,
    rol: 'ADMIN',
    areas: ['ADMIN_SOLICITUDES', 'ADMIN_PERMISOS'],
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('yo() hace GET a /auth/yo y puebla usuarioActual() con rol y areas', () => {
    let recibido: UsuarioDto | undefined;
    service.yo().subscribe((u) => (recibido = u));

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/yo`);
    expect(req.request.method).toBe('GET');
    req.flush(usuarioAdmin);

    expect(recibido).toEqual(usuarioAdmin);
    expect(service.usuarioActual()?.rol).toBe('ADMIN');
    expect(service.usuarioActual()?.areas).toEqual(['ADMIN_SOLICITUDES', 'ADMIN_PERMISOS']);
  });

  it('tieneArea() es true para un area presente y false para una ausente', () => {
    service.yo().subscribe();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/yo`).flush(usuarioAdmin);

    expect(service.tieneArea('ADMIN_PERMISOS')).toBe(true);
    expect(service.tieneArea('ADMIN_INEXISTENTE')).toBe(false);
  });

  it('tieneArea() es false cuando aun no hay usuario', () => {
    expect(service.tieneArea('ADMIN_PERMISOS')).toBe(false);
  });

  it('cerrarSesion() deja usuarioActual() en null', () => {
    service.yo().subscribe();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/yo`).flush(usuarioAdmin);
    expect(service.usuarioActual()).not.toBeNull();

    service.cerrarSesion();

    expect(service.usuarioActual()).toBeNull();
  });

  it('asegurarYo() no vuelve a llamar al backend si el signal ya tiene valor', () => {
    service.yo().subscribe();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/yo`).flush(usuarioAdmin);

    let recibido: UsuarioDto | null | undefined;
    service.asegurarYo().subscribe((u) => (recibido = u));

    httpMock.expectNone(`${environment.apiBaseUrl}/auth/yo`);
    expect(recibido).toEqual(usuarioAdmin);
  });

  it('asegurarYo() llama a yo() si el signal esta vacio y devuelve null en error', () => {
    let recibido: UsuarioDto | null | undefined;
    service.asegurarYo().subscribe((u) => (recibido = u));

    httpMock
      .expectOne(`${environment.apiBaseUrl}/auth/yo`)
      .flush({ codigo: 'CREDENCIALES_INVALIDAS' }, { status: 401, statusText: 'Unauthorized' });

    expect(recibido).toBeNull();
    expect(service.usuarioActual()).toBeNull();
  });

  it('login() guarda el token y puebla usuarioActual()', () => {
    service.login({ telefono: '600000000', password: 'x' }).subscribe();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`);
    expect(req.request.method).toBe('POST');
    req.flush({ token: 'jwt.token.aqui', usuario: usuarioAdmin });

    expect(localStorage.getItem('baniterio.token')).toBe('jwt.token.aqui');
    expect(service.usuarioActual()).toEqual(usuarioAdmin);
  });
});
