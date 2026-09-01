import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { GuardarPerfilRequest } from './perfil.types';
import { PerfilService, urlMedia } from './perfil.service';

/**
 * Un caso por método: comprueba que cada llamada de PerfilService pega al
 * endpoint correcto con el método/cuerpo/params correctos. Mismo patrón que
 * AuthService.spec.ts.
 */
describe('PerfilService', () => {
  let service: PerfilService;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PerfilService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('miPerfil() hace GET a /perfil', () => {
    service.miPerfil().subscribe();
    const req = httpMock.expectOne(`${base}/perfil`);
    expect(req.request.method).toBe('GET');
    req.flush({ usuarioId: 1 });
  });

  it('guardar() hace PUT a /perfil con el cuerpo', () => {
    const body: GuardarPerfilRequest = {
      nombre: 'Ada',
      apellidos: 'Lovelace',
      mote: null,
      sobreMi: null,
      imagenTipo: 'AVATAR',
      imagenRef: '01_chica',
      tienePareja: false,
      parejaNombre: null,
      parejaTelefono: null,
      hijos: [],
    };
    service.guardar(body).subscribe();
    const req = httpMock.expectOne(`${base}/perfil`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(body);
    req.flush({ usuarioId: 1 });
  });

  it('subirFoto() hace POST multipart a /perfil/foto con el archivo', () => {
    const archivo = new File(['contenido'], 'foto.png', { type: 'image/png' });
    service.subirFoto(archivo).subscribe();
    const req = httpMock.expectOne(`${base}/perfil/foto`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBe(true);
    expect((req.request.body as FormData).get('archivo')).toBe(archivo);
    req.flush({ imagenRef: 'x.jpg' });
  });

  it('avatares() hace GET a /perfil/avatares', () => {
    service.avatares().subscribe();
    const req = httpMock.expectOne(`${base}/perfil/avatares`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('aceptarPareja() hace POST a /perfil/pareja/aceptar', () => {
    service.aceptarPareja().subscribe();
    const req = httpMock.expectOne(`${base}/perfil/pareja/aceptar`);
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('rechazarPareja() hace POST a /perfil/pareja/rechazar', () => {
    service.rechazarPareja().subscribe();
    const req = httpMock.expectOne(`${base}/perfil/pareja/rechazar`);
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('romperPareja() hace DELETE a /perfil/pareja', () => {
    service.romperPareja().subscribe();
    const req = httpMock.expectOne(`${base}/perfil/pareja`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('miembros() hace GET a /miembros', () => {
    service.miembros().subscribe();
    const req = httpMock.expectOne(`${base}/miembros`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});

describe('urlMedia', () => {
  const origen = environment.apiBaseUrl.replace(/\/api\/v1$/, '');

  it('devuelve null si no hay imagen', () => {
    expect(urlMedia(null)).toBeNull();
  });

  it('antepone el origen del backend a la URL relativa del backend', () => {
    expect(urlMedia('/api/v1/media/avatares/01_chico.png')).toBe(
      `${origen}/api/v1/media/avatares/01_chico.png`,
    );
  });
});
