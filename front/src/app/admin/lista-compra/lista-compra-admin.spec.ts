import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { ListaCompraAdmin } from './lista-compra-admin';

const base = environment.apiBaseUrl;

describe('ListaCompraAdmin', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('pinta un enlace por evento', () => {
    TestBed.configureTestingModule({
      imports: [ListaCompraAdmin],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const fixture = TestBed.createComponent(ListaCompraAdmin);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(`${base}/admin/lista-compra/eventos`).flush([
      { id: 3, nombre: 'San Miguel', fecha: '2026-09-25', fechaFin: '2026-09-26' },
      { id: 4, nombre: 'Chuletas', fecha: '2027-05-01', fechaFin: null },
    ]);
    fixture.detectChanges();

    const enlaces = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('a'))
      .map((a) => a.getAttribute('href'))
      .filter((h): h is string => !!h);
    expect(enlaces.some((h) => h.includes('/panel/administracion/lista-compra/3'))).toBe(true);
    expect(enlaces.some((h) => h.includes('/panel/administracion/lista-compra/4'))).toBe(true);
    http.verify();
  });
});
