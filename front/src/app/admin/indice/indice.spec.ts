import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { environment } from '../../../environments/environment';
import { PendientesPorArea } from '../admin.types';
import { AdminIndice } from './indice';

/**
 * Tests de `AdminIndice`: pinta como tarjetas solo las secciones para las que el
 * usuario tiene el área; en `ngOnInit` pide `GET /admin/pendientes` y cada
 * tarjeta muestra su campanita (nada si esa área está a 0).
 */
describe('AdminIndice · tarjetas por área', () => {
  let areas: string[] = [];
  const authFalso: Partial<AuthService> = {
    tieneArea: (area: string) => areas.includes(area),
  };
  const pendientesUrl = `${environment.apiBaseUrl}/admin/pendientes`;

  let httpMock: HttpTestingController;

  beforeEach(() => {
    areas = [];
    TestBed.configureTestingModule({
      imports: [AdminIndice],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authFalso },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function render(areasConcedidas: string[], pendientes: PendientesPorArea = {}): HTMLElement {
    areas = areasConcedidas;
    const fixture = TestBed.createComponent(AdminIndice);
    fixture.detectChanges();
    httpMock.expectOne(pendientesUrl).flush(pendientes);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('sin áreas: muestra el aviso', () => {
    expect(render([]).textContent).toContain(
      'No tienes ninguna sección de administración disponible.',
    );
  });

  it('con ADMIN_SOLICITUDES: tarjeta "Solicitudes" presente, "Permisos" ausente', () => {
    const texto = render(['ADMIN_SOLICITUDES']).textContent ?? '';
    expect(texto).toContain('Solicitudes');
    expect(texto).not.toContain('Permisos');
  });

  it('la tarjeta de un área con pendientes muestra el número; la de 0 no', () => {
    const el = render(['ADMIN_SOLICITUDES', 'ADMIN_PERMISOS'], { ADMIN_SOLICITUDES: 4 });
    const status = el.querySelectorAll('[role="status"]');
    expect(status.length).toBe(1);
    expect(status[0].textContent).toContain('4');
  });
});
