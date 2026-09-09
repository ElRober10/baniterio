import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { ListaCompraAdminEvento } from './lista-compra-admin-evento';

const base = environment.apiBaseUrl;

const RESPUESTA = {
  evento: { id: 7, nombre: 'San Miguel', fecha: '2026-09-25', fechaFin: '2026-09-26' },
  apuntados: 10,
  diasFiesta: 2,
  reglas: [
    {
      id: 1,
      categoria: 'LIMPIEZA',
      etiqueta: 'Limpieza y utensilios',
      nombre: 'Platos',
      tamano: 'unidad',
      tipoFormula: 'POR_PENISTA',
      factor: 3,
      porCada: null,
      origen: 'PLANTILLA',
      cantidadCalculada: 30,
      cantidadAjustada: null,
      cantidadFinal: 30,
      activa: true,
    },
    {
      id: 2,
      categoria: 'COMIDA',
      etiqueta: 'Comida',
      nombre: 'Servilletas',
      tamano: 'paquete',
      tipoFormula: 'POR_EVENTO',
      factor: 2,
      porCada: null,
      origen: 'MANUAL',
      cantidadCalculada: 2,
      cantidadAjustada: null,
      cantidadFinal: 2,
      activa: true,
    },
  ],
};

function montar(): { fixture: ComponentFixture<ListaCompraAdminEvento>; http: HttpTestingController } {
  TestBed.configureTestingModule({
    imports: [ListaCompraAdminEvento],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: new Map([['id', '7']]) } } },
    ],
  });
  const fixture = TestBed.createComponent(ListaCompraAdminEvento);
  const http = TestBed.inject(HttpTestingController);
  fixture.detectChanges();
  http.expectOne(`${base}/admin/lista-compra/eventos/7`).flush(RESPUESTA);
  fixture.detectChanges();
  return { fixture, http };
}

describe('ListaCompraAdminEvento', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('ajustar una regla hace PUT con cantidad y activa', () => {
    const { fixture, http } = montar();
    const el = fixture.nativeElement as HTMLElement;
    const input = el.querySelector('input[type="number"]') as HTMLInputElement;
    input.value = '50';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Guardar')!
      .dispatchEvent(new Event('click'));

    const put = http.expectOne(`${base}/admin/lista-compra/eventos/7/reglas/1`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ cantidadAjustada: 50, activa: true });
    put.flush(null);
    http.expectOne(`${base}/admin/lista-compra/eventos/7`).flush(RESPUESTA);
    http.verify();
  });

  it('añadir artículo hace POST', () => {
    const { fixture, http } = montar();
    const el = fixture.nativeElement as HTMLElement;

    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Añadir artículo')!
      .dispatchEvent(new Event('click'));
    fixture.detectChanges();

    const texto = el.querySelectorAll('input[type="text"], input:not([type])');
    (texto[0] as HTMLInputElement).value = 'Film';
    texto[0].dispatchEvent(new Event('input'));
    (texto[1] as HTMLInputElement).value = 'rollo';
    texto[1].dispatchEvent(new Event('input'));
    fixture.detectChanges();

    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Añadir')!
      .dispatchEvent(new Event('click'));

    const post = http.expectOne(`${base}/admin/lista-compra/eventos/7/reglas`);
    expect(post.request.method).toBe('POST');
    expect(post.request.body.nombre).toBe('Film');
    post.flush({});
    http.expectOne(`${base}/admin/lista-compra/eventos/7`).flush(RESPUESTA);
    http.verify();
  });

  it('quitar una regla MANUAL hace DELETE', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const { fixture, http } = montar();
    const el = fixture.nativeElement as HTMLElement;

    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Quitar')!
      .dispatchEvent(new Event('click'));

    const del = http.expectOne(`${base}/admin/lista-compra/eventos/7/reglas/2`);
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
    http.expectOne(`${base}/admin/lista-compra/eventos/7`).flush(RESPUESTA);
    http.verify();
  });
});
