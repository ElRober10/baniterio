import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { PrecioBebidaAlcohol } from './precio-bebida-alcohol';

const base = environment.apiBaseUrl;

const grilla = {
  puedoEditar: true,
  tiendas: [
    { id: 1, nombre: 'Alcampo' },
    { id: 2, nombre: 'Makro' },
  ],
  tamanos: ['70 cl', '1 L'],
  bebidas: [{ id: 5, nombre: 'Barceló' }],
  precios: [{ bebidaId: 5, tamano: '70 cl', tiendaId: 1, precio: 8.5 }],
};

function montar(): { fixture: ComponentFixture<PrecioBebidaAlcohol>; http: HttpTestingController } {
  TestBed.configureTestingModule({
    imports: [PrecioBebidaAlcohol],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: new Map([['anio', '2026'], ['id', '9']]) } },
      },
    ],
  });
  return {
    fixture: TestBed.createComponent(PrecioBebidaAlcohol),
    http: TestBed.inject(HttpTestingController),
  };
}

describe('PrecioBebidaAlcohol', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('pinta marcas x tiendas y el precio de la pestaña activa', () => {
    const { fixture, http } = montar();
    fixture.detectChanges();
    http.expectOne(`${base}/precio-bebida/eventos/9/alcohol`).flush(grilla);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Barceló');
    expect(el.textContent).toContain('Alcampo');
    const input = el.querySelector('input[aria-label="Barceló en Alcampo"]') as HTMLInputElement;
    expect(input.value).toBe('8.5');
    http.verify();
  });

  it('cambiar de pestaña de tamaño no vuelve a pedir la rejilla', () => {
    const { fixture, http } = montar();
    fixture.detectChanges();
    http.expectOne(`${base}/precio-bebida/eventos/9/alcohol`).flush(grilla);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const botones = Array.from(el.querySelectorAll('button'));
    botones.find((b) => b.textContent?.trim() === '1 L')!.click();
    fixture.detectChanges();

    const input = el.querySelector('input[aria-label="Barceló en Alcampo"]') as HTMLInputElement;
    expect(input.value).toBe('');
    http.verify();
  });

  it('sin puedoEditar, las celdas son texto y no hay botones de añadir', () => {
    const { fixture, http } = montar();
    fixture.detectChanges();
    http.expectOne(`${base}/precio-bebida/eventos/9/alcohol`).flush({ ...grilla, puedoEditar: false });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('input')).toBeNull();
    expect(el.textContent).toContain('8.5');
    expect(el.textContent).not.toContain('Añadir tienda');
    http.verify();
  });

  it('escribir un precio hace el PUT de la celda', () => {
    const { fixture, http } = montar();
    fixture.detectChanges();
    http.expectOne(`${base}/precio-bebida/eventos/9/alcohol`).flush(grilla);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const input = el.querySelector('input[aria-label="Barceló en Alcampo"]') as HTMLInputElement;
    input.value = '9.2';
    input.dispatchEvent(new Event('change'));

    const req = http.expectOne(`${base}/precio-bebida/eventos/9/alcohol/precio`);
    expect(req.request.body).toEqual({ bebidaId: 5, tamano: '70 cl', tiendaId: 1, precio: 9.2 });
    req.flush(null);
    http.verify();
  });
});
