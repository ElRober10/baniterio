import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { AdminService } from './admin.service';
import { MiembroResumen, SolicitudResumen } from './admin.types';

/**
 * Tests de AdminService: cada método pega a la URL correcta con el verbo y el
 * cuerpo correctos y devuelve la respuesta parseada. Un caso comprueba que un
 * error del backend (403 SIN_PERMISO) se propaga al que se suscribe.
 */
describe('AdminService', () => {
  let service: AdminService;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listarSolicitudes() hace GET a /admin/solicitudes con ?estado=PENDIENTE por defecto', () => {
    const datos: SolicitudResumen[] = [
      {
        id: 1,
        nombre: 'Ada',
        apellidos: 'Lovelace',
        telefono: '600000000',
        email: 'ada@example.com',
        motivo: 'Quiero entrar',
        relacion: 'Amiga de',
        conocidos: 'Pepe',
        traeContrasena: true,
        estado: 'PENDIENTE',
        createdAt: '2026-08-29T10:00:00Z',
      },
    ];
    let recibido: SolicitudResumen[] | undefined;
    service.listarSolicitudes().subscribe((r) => (recibido = r));

    const req = httpMock.expectOne(`${base}/admin/solicitudes?estado=PENDIENTE`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('estado')).toBe('PENDIENTE');
    req.flush(datos);

    expect(recibido).toEqual(datos);
  });

  it('listarSolicitudes(estado) respeta el estado que se le pasa', () => {
    service.listarSolicitudes('RECHAZADA').subscribe();
    const req = httpMock.expectOne(`${base}/admin/solicitudes?estado=RECHAZADA`);
    expect(req.request.params.get('estado')).toBe('RECHAZADA');
    req.flush([]);
  });

  it('aprobarSolicitud(id) hace POST a /admin/solicitudes/{id}/aprobar con cuerpo vacio', () => {
    let recibido: { resultado: string } | undefined;
    service.aprobarSolicitud(42).subscribe((r) => (recibido = r));

    const req = httpMock.expectOne(`${base}/admin/solicitudes/42/aprobar`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ resultado: 'CUENTA_CREADA' });

    expect(recibido).toEqual({ resultado: 'CUENTA_CREADA' });
  });

  it('aprobarSolicitud() propaga el error cuando el backend responde 403 SIN_PERMISO', () => {
    let error: unknown;
    service.aprobarSolicitud(42).subscribe({ error: (e) => (error = e) });

    httpMock
      .expectOne(`${base}/admin/solicitudes/42/aprobar`)
      .flush({ codigo: 'SIN_PERMISO' }, { status: 403, statusText: 'Forbidden' });

    expect((error as { status: number }).status).toBe(403);
    expect((error as { error: { codigo: string } }).error.codigo).toBe('SIN_PERMISO');
  });

  it('rechazarSolicitud(id, motivo) hace POST a /rechazar con { motivo }', () => {
    service.rechazarSolicitud(7, 'No te conoce nadie').subscribe();
    const req = httpMock.expectOne(`${base}/admin/solicitudes/7/rechazar`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ motivo: 'No te conoce nadie' });
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('rechazarSolicitud(id) sin motivo manda cuerpo vacio', () => {
    service.rechazarSolicitud(7).subscribe();
    const req = httpMock.expectOne(`${base}/admin/solicitudes/7/rechazar`);
    expect(req.request.body).toEqual({});
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('listarMiembros() hace GET a /admin/miembros', () => {
    const datos: MiembroResumen[] = [
      {
        id: 3,
        nombre: 'Grace',
        apellidos: 'Hopper',
        mote: 'Amazing Grace',
        telefono: '611111111',
        rol: 'ADMIN',
        activo: true,
        esSuperadmin: false,
        areas: ['ADMIN_PERMISOS'],
      },
    ];
    let recibido: MiembroResumen[] | undefined;
    service.listarMiembros().subscribe((r) => (recibido = r));

    const req = httpMock.expectOne(`${base}/admin/miembros`);
    expect(req.request.method).toBe('GET');
    req.flush(datos);

    expect(recibido).toEqual(datos);
  });

  it('cambiarRol(id, rol) hace PUT a /admin/miembros/{id}/rol con { rol }', () => {
    service.cambiarRol(3, 'MIEMBRO').subscribe();
    const req = httpMock.expectOne(`${base}/admin/miembros/3/rol`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ rol: 'MIEMBRO' });
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('cambiarActivo(id, activo) hace PUT a /admin/miembros/{id}/activo con { activo }', () => {
    service.cambiarActivo(3, false).subscribe();
    const req = httpMock.expectOne(`${base}/admin/miembros/3/activo`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ activo: false });
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('cambiarAreas(id, areas) hace PUT a /admin/miembros/{id}/areas con { areas }', () => {
    service.cambiarAreas(3, ['ADMIN_SOLICITUDES', 'ADMIN_PERMISOS']).subscribe();
    const req = httpMock.expectOne(`${base}/admin/miembros/3/areas`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ areas: ['ADMIN_SOLICITUDES', 'ADMIN_PERMISOS'] });
    req.flush(null, { status: 204, statusText: 'No Content' });
  });
});
