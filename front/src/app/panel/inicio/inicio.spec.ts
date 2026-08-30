import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { UsuarioDto } from '../../auth/auth.types';
import { environment } from '../../../environments/environment';
import { PanelInicio } from './inicio';

/**
 * Tests de `PanelInicio`:
 * - La tarjeta "Administración" solo aparece si el usuario tiene alguna área.
 * - Si la tiene, en `ngOnInit` se pide `GET /admin/pendientes` y la tarjeta
 *   muestra el total (campanita). Sin áreas no se pide nada.
 */
describe('PanelInicio · tarjeta de administración', () => {
  const usuarioSesion = signal<UsuarioDto | null>(null);
  const authFalso: Partial<AuthService> = { usuarioActual: usuarioSesion };
  const pendientesUrl = `${environment.apiBaseUrl}/admin/pendientes`;

  let httpMock: HttpTestingController;

  function crear(): ComponentFixture<PanelInicio> {
    const fixture = TestBed.createComponent(PanelInicio);
    fixture.detectChanges();
    return fixture;
  }

  function usuario(areas: string[]): UsuarioDto {
    return {
      id: 1,
      nombre: 'Ada',
      apellidos: 'Lovelace',
      mote: null,
      esSuperadmin: false,
      rol: 'MIEMBRO',
      areas,
    };
  }

  beforeEach(() => {
    usuarioSesion.set(null);
    TestBed.configureTestingModule({
      imports: [PanelInicio],
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

  it('sin usuario cargado todavía: no hay tarjeta "Administración" ni se pide el recuento', () => {
    const fixture = crear();
    httpMock.expectNone(pendientesUrl);
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Administración');
  });

  it('usuario sin áreas: no hay tarjeta "Administración" ni se pide el recuento', () => {
    usuarioSesion.set(usuario([]));
    const fixture = crear();
    httpMock.expectNone(pendientesUrl);
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Administración');
  });

  it('usuario con área: pide el recuento y la tarjeta muestra el total', async () => {
    usuarioSesion.set(usuario(['ADMIN_SOLICITUDES']));
    const fixture = crear();

    httpMock.expectOne(pendientesUrl).flush({ ADMIN_SOLICITUDES: 2 });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Administración');
    expect(texto).toContain('Miembros');
    const status = (fixture.nativeElement as HTMLElement).querySelector('[role="status"]');
    expect(status?.textContent).toContain('2');
  });
});
