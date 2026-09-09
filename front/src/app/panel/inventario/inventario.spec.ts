import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { Inventario } from './inventario';
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

describe('Inventario', () => {
  let fixture: ComponentFixture<Inventario>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Inventario],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(Inventario);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function cargar(puedoEditar: boolean): void {
    fixture.detectChanges();
    httpMock.expectOne(`${base}/inventario`).flush(RESPUESTA(puedoEditar));
    fixture.detectChanges();
  }

  it('pinta las categorías y sus artículos', () => {
    cargar(false);
    const txt = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(txt).toContain('Cerveza');
    expect(txt).toContain('Mahou Clásica');
    expect(txt).toContain('192');
    expect(txt).toContain('Nada apuntado todavía.');
  });

  it('sin permiso no enseña el botón Editar', () => {
    cargar(false);
    const botones = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'));
    expect(botones.some((b) => b.textContent?.includes('Editar'))).toBe(false);
  });

  it('con permiso, editar + guardar manda un PUT por fila cambiada', () => {
    cargar(true);

    const editar = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.includes('Editar'))!;
    editar.dispatchEvent(new Event('click'));
    fixture.detectChanges();

    const cantidad = (fixture.nativeElement as HTMLElement).querySelector(
      'input[type="number"]',
    ) as HTMLInputElement;
    cantidad.value = '5';
    cantidad.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const guardar = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.includes('Guardar'))!;
    guardar.dispatchEvent(new Event('click'));

    const put = httpMock.expectOne(`${base}/inventario/1`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body.cantidad).toBe(5);
    put.flush({ id: 1, nombre: 'Mahou Clásica', tamano: 'lata', cantidad: 5 });

    // tras guardar, recarga
    httpMock.expectOne(`${base}/inventario`).flush(RESPUESTA(true));
  });
});
