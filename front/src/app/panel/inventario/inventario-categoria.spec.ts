import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { InventarioCategoria } from './inventario-categoria';
import { InventarioResponse } from './inventario.types';

const RESPUESTA = (puedoEditar: boolean): InventarioResponse => ({
  puedoEditar,
  categorias: [
    {
      categoria: 'CERVEZA',
      etiqueta: 'Cerveza',
      tamanos: ['lata', 'botellín', 'tercio'],
      articulos: [
        { id: 1, nombre: 'Mahou Clásica', tamano: 'lata', cantidad: 192 },
        { id: 2, nombre: 'Mixta', tamano: 'lata', cantidad: 3 },
      ],
    },
    { categoria: 'COMIDA', etiqueta: 'Comida', tamanos: ['unidad', 'kg'], articulos: [] },
  ],
});

function montar(slug: string): {
  fixture: ComponentFixture<InventarioCategoria>;
  httpMock: HttpTestingController;
} {
  TestBed.configureTestingModule({
    imports: [InventarioCategoria],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: new Map([['categoria', slug]]) } },
      },
    ],
  });
  return {
    fixture: TestBed.createComponent(InventarioCategoria),
    httpMock: TestBed.inject(HttpTestingController),
  };
}

describe('InventarioCategoria', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('pinta solo los artículos de su categoría', () => {
    const { fixture, httpMock } = montar('cerveza');
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/inventario`).flush(RESPUESTA(false));
    fixture.detectChanges();

    const txt = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(txt).toContain('Cerveza');
    expect(txt).toContain('Mahou Clásica');
    expect(txt).toContain('192');
    expect(txt).not.toContain('Comida');
    httpMock.verify();
  });

  it('slug desconocido: estado de error sin llamar al backend', () => {
    const { fixture, httpMock } = montar('chuches');
    fixture.detectChanges();
    httpMock.verify(); // no debe haber ninguna petición
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'No se ha podido cargar el listado.',
    );
  });

  it('con permiso, editar + guardar manda un PUT por fila cambiada', () => {
    const { fixture, httpMock } = montar('cerveza');
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/inventario`).flush(RESPUESTA(true));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Editar'))!
      .dispatchEvent(new Event('click'));
    fixture.detectChanges();

    const cantidad = el.querySelector('input[type="number"]') as HTMLInputElement;
    cantidad.value = '5';
    cantidad.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Guardar'))!
      .dispatchEvent(new Event('click'));

    const put = httpMock.expectOne(`${environment.apiBaseUrl}/inventario/1`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body.cantidad).toBe(5);
    put.flush({ id: 1, nombre: 'Mahou Clásica', tamano: 'lata', cantidad: 5 });

    httpMock.expectOne(`${environment.apiBaseUrl}/inventario`).flush(RESPUESTA(true));
    httpMock.verify();
  });
});
