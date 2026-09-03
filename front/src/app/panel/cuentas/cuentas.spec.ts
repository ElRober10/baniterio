import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { Cuentas } from './cuentas';

/**
 * Tests del listado de cuentas: carga la lista y pinta un botón por cuenta;
 * si la lista viene vacía muestra el aviso; si el backend falla, un reintento.
 */
describe('Cuentas (listado)', () => {
  let fixture: ComponentFixture<Cuentas>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Cuentas],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(Cuentas);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('carga la lista y pinta un botón por cuenta', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${base}/cuentas`).flush([
      { id: 1, nombre: 'Chuletas Santas', descripcion: 'Balance de las Chuletas' },
      { id: 2, nombre: 'San Miguel', descripcion: null },
    ]);
    fixture.detectChanges();

    const txt = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(txt).toContain('Chuletas Santas');
    expect(txt).toContain('San Miguel');
  });

  it('lista vacía: muestra el aviso', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${base}/cuentas`).flush([]);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Todavía no hay cuentas');
  });

  it('error del backend: muestra el botón de reintentar y vuelve a pedir', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${base}/cuentas`).flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    const reintentar = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.includes('Reintentar'));
    expect(reintentar).toBeTruthy();
    reintentar?.dispatchEvent(new Event('click'));

    httpMock.expectOne(`${base}/cuentas`).flush([]);
  });
});
