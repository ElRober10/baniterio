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
      bloqueada: false,
      apuntados: 10,
      diasFiesta: 2,
      categorias: [
        {
          categoria: 'LIMPIEZA',
          etiqueta: 'Limpieza y utensilios',
          lineas: [
            {
              id: 1,
              nombre: 'Platos',
              tamano: 'unidad',
              cantidad: 30,
              cantidadCalculada: 30,
              ajustada: false,
              dinamica: false,
              necesitaFicha: false,
              comprada: false,
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
      bloqueada: false,
      apuntados: 3,
      diasFiesta: 1,
      categorias: [
        {
          categoria: 'CERVEZA',
          etiqueta: 'Cerveza',
          lineas: [
            {
              id: 1,
              nombre: 'Cerveza',
              tamano: 'lata',
              cantidad: 0,
              cantidadCalculada: 0,
              ajustada: false,
              dinamica: false,
              necesitaFicha: true,
              comprada: false,
            },
          ],
        },
      ],
    });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('necesita ficha de bebida');
    http.verify();
  });

  it('con permiso muestra "Comprado" en líneas pendientes y "Bloquear lista"', () => {
    const { fixture, http } = montar();
    fixture.detectChanges();
    http.expectOne(`${base}/eventos/7/lista-compra`).flush({
      puedoEditar: true,
      llevaFicha: false,
      bloqueada: false,
      apuntados: 4,
      diasFiesta: 1,
      categorias: [
        {
          categoria: 'LIMPIEZA',
          etiqueta: 'Limpieza y utensilios',
          lineas: [
            {
              id: 5,
              nombre: 'Platos',
              tamano: 'unidad',
              cantidad: 12,
              cantidadCalculada: 12,
              ajustada: false,
              dinamica: false,
              necesitaFicha: false,
              comprada: false,
            },
          ],
        },
      ],
    });
    fixture.detectChanges();
    const txt = (fixture.nativeElement as HTMLElement).textContent!;
    expect(txt).toContain('Comprado');
    expect(txt).toContain('Bloquear lista');
    http.verify();
  });

  it('con la lista bloqueada y permiso, se puede ajustar a mano la cantidad de una línea', () => {
    const { fixture, http } = montar();
    fixture.detectChanges();
    http.expectOne(`${base}/eventos/7/lista-compra`).flush({
      puedoEditar: true,
      llevaFicha: false,
      bloqueada: true,
      apuntados: 4,
      diasFiesta: 1,
      categorias: [
        {
          categoria: 'ALCOHOL',
          etiqueta: 'Alcohol',
          lineas: [
            {
              id: 9,
              nombre: "Seagram's",
              tamano: '1 L',
              cantidad: 1,
              cantidadCalculada: 1,
              ajustada: false,
              dinamica: true,
              necesitaFicha: false,
              comprada: false,
            },
          ],
        },
      ],
    });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    const botones = Array.from(el.querySelectorAll('button'));
    botones.find((b) => b.getAttribute('aria-label') === 'Ajustar cantidad')!.click();
    fixture.detectChanges();

    const input = el.querySelector('input[type="number"]') as HTMLInputElement;
    input.value = '0.8';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.includes('Guardar'))!
      .click();

    http.expectOne(`${base}/eventos/7/lista-compra/lineas/9`).flush(null);
    http.expectOne(`${base}/eventos/7/lista-compra`).flush({
      puedoEditar: true,
      llevaFicha: false,
      bloqueada: true,
      apuntados: 4,
      diasFiesta: 1,
      categorias: [],
    });
    http.verify();
  });

  it('en el alcohol el desplegable se abre desde el tamaño, no desde la cantidad', () => {
    const { fixture, http } = montar();
    fixture.detectChanges();
    http.expectOne(`${base}/eventos/7/lista-compra`).flush({
      puedoEditar: true,
      llevaFicha: true,
      bloqueada: true,
      apuntados: 4,
      diasFiesta: 2,
      categorias: [
        {
          categoria: 'ALCOHOL',
          etiqueta: 'Alcohol',
          lineas: [
            {
              id: 3,
              nombre: 'Barceló',
              tamano: '70 cl',
              cantidad: 7,
              cantidadCalculada: 7,
              ajustada: false,
              dinamica: true,
              necesitaFicha: false,
              comprada: false,
              info: {
                personas: 7,
                stock: 0,
                tamanos: [{ tamano: '1.75', tienda: 'Amazon', precio: 33.3, precioLitro: 19.03 }],
              },
            },
          ],
        },
      ],
    });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('[aria-label="Ajustar cantidad"]')).toBeNull();
    (el.querySelector('[aria-label="Cambiar tamaño"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(el.textContent).toContain('1.75');
    expect(el.textContent).toContain('19.03');
    http.verify();
  });

  it('sin la lista bloqueada no se ofrece "Ajustar"', () => {
    const { fixture, http } = montar();
    fixture.detectChanges();
    http.expectOne(`${base}/eventos/7/lista-compra`).flush({
      puedoEditar: true,
      llevaFicha: false,
      bloqueada: false,
      apuntados: 4,
      diasFiesta: 1,
      categorias: [
        {
          categoria: 'LIMPIEZA',
          etiqueta: 'Limpieza y utensilios',
          lineas: [
            {
              id: 5,
              nombre: 'Platos',
              tamano: 'unidad',
              cantidad: 12,
              cantidadCalculada: 12,
              ajustada: false,
              dinamica: false,
              necesitaFicha: false,
              comprada: false,
            },
          ],
        },
      ],
    });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[aria-label="Ajustar cantidad"]')).toBeNull();
    http.verify();
  });

  it('una línea comprada se muestra como "✓ Comprada" y sin botón', () => {
    const { fixture, http } = montar();
    fixture.detectChanges();
    http.expectOne(`${base}/eventos/7/lista-compra`).flush({
      puedoEditar: true,
      llevaFicha: false,
      bloqueada: false,
      apuntados: 4,
      diasFiesta: 1,
      categorias: [
        {
          categoria: 'LIMPIEZA',
          etiqueta: 'Limpieza y utensilios',
          lineas: [
            {
              id: 5,
              nombre: 'Platos',
              tamano: 'unidad',
              cantidad: 12,
              cantidadCalculada: 12,
              ajustada: false,
              dinamica: false,
              necesitaFicha: false,
              comprada: true,
            },
          ],
        },
      ],
    });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('✓ Comprada');
    expect(el.querySelector('button')?.textContent).not.toContain('Comprado');
    http.verify();
  });
});
