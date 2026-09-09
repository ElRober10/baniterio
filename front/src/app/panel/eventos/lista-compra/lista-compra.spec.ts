import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { ListaCompra } from './lista-compra';

const base = environment.apiBaseUrl;

function montar(): { fixture: ComponentFixture<ListaCompra>; http: HttpTestingController } {
  TestBed.configureTestingModule({
    imports: [ListaCompra],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: new Map([['id', '7']]) } } },
    ],
  });
  return {
    fixture: TestBed.createComponent(ListaCompra),
    http: TestBed.inject(HttpTestingController),
  };
}

describe('ListaCompra', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('pinta las categorías y sus líneas', () => {
    const { fixture, http } = montar();
    fixture.detectChanges();
    http.expectOne(`${base}/eventos/7/lista-compra`).flush({
      puedoEditar: false,
      llevaFicha: true,
      apuntados: 10,
      diasFiesta: 2,
      categorias: [
        {
          categoria: 'LIMPIEZA',
          etiqueta: 'Limpieza y utensilios',
          lineas: [
            {
              nombre: 'Platos',
              tamano: 'unidad',
              cantidad: 30,
              cantidadCalculada: 30,
              ajustada: false,
              dinamica: false,
              necesitaFicha: false,
            },
          ],
        },
      ],
    });
    fixture.detectChanges();
    const txt = (fixture.nativeElement as HTMLElement).textContent!;
    expect(txt).toContain('Platos');
    expect(txt).toContain('30');
    http.verify();
  });

  it('marca las líneas que necesitan ficha de bebida', () => {
    const { fixture, http } = montar();
    fixture.detectChanges();
    http.expectOne(`${base}/eventos/7/lista-compra`).flush({
      puedoEditar: false,
      llevaFicha: false,
      apuntados: 3,
      diasFiesta: 1,
      categorias: [
        {
          categoria: 'CERVEZA',
          etiqueta: 'Cerveza',
          lineas: [
            {
              nombre: 'Cerveza',
              tamano: 'lata',
              cantidad: 0,
              cantidadCalculada: 0,
              ajustada: false,
              dinamica: false,
              necesitaFicha: true,
            },
          ],
        },
      ],
    });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('necesita ficha de bebida');
    http.verify();
  });
});
