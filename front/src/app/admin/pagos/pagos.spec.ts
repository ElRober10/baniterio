import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { UsuarioDto } from '../../auth/auth.types';
import { environment } from '../../../environments/environment';
import { AdminPagos } from './pagos';

/** Tests de AdminPagos: admin ve la cola y confirma/rechaza; no-admin es redirigido. */
describe('AdminPagos', () => {
  let fixture: ComponentFixture<AdminPagos>;
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
      imports: [AdminPagos],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: authFalso },
      ],
    });
    fixture = TestBed.createComponent(AdminPagos);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => httpMock.verify());

  const pago = {
    id: 5,
    eventoId: 3,
    eventoNombre: 'San Miguel 2026',
    declaradoPor: 'Roberto',
    importe: 52,
    metodoPago: 'BIZUM',
    createdAt: '2026-09-07T10:00:00Z',
    cubre: [
      { nombre: 'Roberto', cuota: 26 },
      { nombre: 'Miriam', cuota: 26 },
    ],
  };

  it('un no-admin es redirigido a /panel', () => {
    crear({ rol: 'MIEMBRO', esSuperadmin: false });
    const spy = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();
    expect(spy).toHaveBeenCalledWith('/panel');
  });

  it('un admin ve la cola y "Confirmar" hace POST y quita la fila', () => {
    crear({ rol: 'ADMIN', esSuperadmin: false });
    fixture.detectChanges();
    httpMock.expectOne(`${base}/admin/pagos-declarados`).flush([pago]);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Roberto');
    expect(el.textContent).toContain('Cubre: Roberto, Miriam');
    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Confirmar')
      ?.dispatchEvent(new Event('click'));

    const post = httpMock.expectOne(`${base}/admin/pagos-declarados/5/confirmar`);
    expect(post.request.method).toBe('POST');
    post.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    expect(el.querySelectorAll('li').length).toBe(0);
    expect(el.textContent).toContain('No hay pagos pendientes');
  });

  it('"Rechazar" hace POST /rechazar', () => {
    crear({ rol: 'ADMIN', esSuperadmin: false });
    fixture.detectChanges();
    httpMock.expectOne(`${base}/admin/pagos-declarados`).flush([pago]);
    fixture.detectChanges();

    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Rechazar')
      ?.dispatchEvent(new Event('click'));
    const post = httpMock.expectOne(`${base}/admin/pagos-declarados/5/rechazar`);
    expect(post.request.method).toBe('POST');
    post.flush(null, { status: 204, statusText: 'No Content' });
  });
});
