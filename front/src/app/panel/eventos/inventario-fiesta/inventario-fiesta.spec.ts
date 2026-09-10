import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { InventarioFiesta } from './inventario-fiesta';

const RESPUESTA = (puedoEditar: boolean) => ({
  puedoEditar,
  categorias: [
    {
      categoria: 'ALCOHOL',
      etiqueta: 'Alcohol',
      articulos: [
        { id: 5, nombre: 'Tanqueray', tamano: '70 cl', cantidad: 1.5, cantidadComprada: 0 },
      ],
    },
  ],
});

function montar(): { fixture: ComponentFixture<InventarioFiesta>; httpMock: HttpTestingController } {
  TestBed.configureTestingModule({
    imports: [InventarioFiesta],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: new Map([['id', '7']]) } } },
    ],
  });
  return {
    fixture: TestBed.createComponent(InventarioFiesta),
    httpMock: TestBed.inject(HttpTestingController),
  };
}

describe('InventarioFiesta', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('pinta lo enviado y, con permiso, "Devolver a inventario" hace el POST', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const { fixture, httpMock } = montar();
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/inventario/evento/7`).flush(RESPUESTA(true));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Tanqueray');

    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Devolver a inventario')!
      .dispatchEvent(new Event('click'));

    const post = httpMock.expectOne(`${environment.apiBaseUrl}/inventario/evento/7/5/devolver`);
    expect(post.request.method).toBe('POST');
    post.flush(null);
    httpMock.expectOne(`${environment.apiBaseUrl}/inventario/evento/7`).flush(RESPUESTA(true));
    httpMock.verify();
  });

  it('muestra "Devolver a la lista" cuando cantidadComprada > 0 y oculta el de stock', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const { fixture, httpMock } = montar();
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/inventario/evento/7`).flush({
      puedoEditar: true,
      categorias: [
        {
          categoria: 'LIMPIEZA',
          etiqueta: 'Limpieza y utensilios',
          articulos: [
            { id: 9, nombre: 'Platos', tamano: 'unidad', cantidad: 12, cantidadComprada: 12 },
          ],
        },
      ],
    });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const botones = Array.from(el.querySelectorAll('button')).map((b) => b.textContent?.trim());
    expect(botones).toContain('Devolver a la lista');
    expect(botones).not.toContain('Devolver a inventario');

    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Devolver a la lista')!
      .dispatchEvent(new Event('click'));
    const post = httpMock.expectOne(
      `${environment.apiBaseUrl}/inventario/evento/7/9/devolver-a-lista`,
    );
    expect(post.request.method).toBe('POST');
    post.flush(null);
    httpMock.expectOne(`${environment.apiBaseUrl}/inventario/evento/7`).flush(RESPUESTA(true));
    httpMock.verify();
  });

  it('sin permiso no muestra el botón de devolver', () => {
    const { fixture, httpMock } = montar();
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/inventario/evento/7`).flush(RESPUESTA(false));
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Devolver a inventario');
    httpMock.verify();
  });
});
