import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { UsuarioDto } from '../../auth/auth.types';
import { environment } from '../../../environments/environment';
import { AdminBebidas } from './bebidas';

/** Tests de AdminBebidas: admin ve la lista y acepta; no-admin es redirigido. */
describe('AdminBebidas', () => {
  let fixture: ComponentFixture<AdminBebidas>;
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
      imports: [AdminBebidas],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: authFalso },
      ],
    });
    fixture = TestBed.createComponent(AdminBebidas);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => httpMock.verify());

  it('un no-admin es redirigido a /panel', () => {
    crear({ rol: 'MIEMBRO', esSuperadmin: false });
    const spy = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();
    expect(spy).toHaveBeenCalledWith('/panel');
  });

  it('un admin ve la lista y "Aceptar" hace POST /bebidas/:id/aceptar', () => {
    crear({ rol: 'ADMIN', esSuperadmin: false });
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === `${base}/bebidas`).flush([
      { id: 3, tipo: 'ALCOHOL', nombre: 'Ron del abuelo', propuestaPor: { id: 1, nombre: 'Ana' }, createdAt: '2026-09-04' },
    ]);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Ron del abuelo');
    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Aceptar')
      ?.dispatchEvent(new Event('click'));

    const post = httpMock.expectOne(`${base}/bebidas/3/aceptar`);
    expect(post.request.method).toBe('POST');
    post.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    expect(el.querySelectorAll('li').length).toBe(0);
    expect(el.textContent).toContain('No hay bebidas pendientes');
  });
});
