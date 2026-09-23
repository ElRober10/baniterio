import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { UsuarioDto } from '../../auth/auth.types';
import { environment } from '../../../environments/environment';
import { AdminLogs } from './logs';

/** Tests de AdminLogs: admin ve las filas del registro; no-admin es redirigido. */
describe('AdminLogs', () => {
  let fixture: ComponentFixture<AdminLogs>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  const usuario = signal<UsuarioDto | null>(null);
  const authFalso: Partial<AuthService> = {
    usuarioActual: usuario,
    asegurarYo: () => of(usuario()),
  };

  function crear(u: Partial<UsuarioDto> | null) {
    usuario.set(u as UsuarioDto | null);
    TestBed.configureTestingModule({
      imports: [AdminLogs],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: authFalso },
      ],
    });
    fixture = TestBed.createComponent(AdminLogs);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => httpMock.verify());

  it('un no-admin es redirigido a /panel', () => {
    crear({ rol: 'MIEMBRO', esSuperadmin: false });
    const spy = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();
    expect(spy).toHaveBeenCalledWith('/panel');
  });

  it('un admin ve las filas que devuelve el backend', () => {
    crear({ rol: 'ADMIN', esSuperadmin: false });
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === `${base}/logs`).flush({
      contenido: [
        {
          id: 1,
          origen: 'MOBILE',
          usuarioId: null,
          usuarioNombre: null,
          metodo: null,
          ruta: 'cuentas.crearMovimiento',
          estado: null,
          codigoError: null,
          mensaje: 'IOException',
          creadoEn: '2026-09-23T10:00:00Z',
        },
      ],
      total: 1,
      pagina: 0,
      tamano: 50,
    });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('cuentas.crearMovimiento');
    expect(el.textContent).toContain('IOException');
  });
});
