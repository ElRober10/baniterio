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
      articulos: [{ id: 5, nombre: 'Tanqueray', tamano: '70 cl', cantidad: 1.5 }],
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

  it('sin permiso no muestra el botón de devolver', () => {
    const { fixture, httpMock } = montar();
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/inventario/evento/7`).flush(RESPUESTA(false));
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Devolver a inventario');
    httpMock.verify();
  });
});
