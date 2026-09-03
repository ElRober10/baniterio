import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { EditorEvento } from './editor-evento';

/**
 * Tests del editor de evento: modo crear (ruta sin id) hace POST; modo editar
 * (ruta con id) precarga con GET y luego hace PUT.
 */
describe('EditorEvento', () => {
  let fixture: ComponentFixture<EditorEvento>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  function crear(id: string | null) {
    TestBed.configureTestingModule({
      imports: [EditorEvento],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap(id ? { id } : {}) } },
        },
      ],
    });
    fixture = TestBed.createComponent(EditorEvento);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => httpMock.verify());

  function escribir(control: string, valor: string) {
    const input = (fixture.nativeElement as HTMLElement).querySelector(
      `[formControlName="${control}"]`,
    ) as HTMLInputElement;
    input.value = valor;
    input.dispatchEvent(new Event('input'));
  }

  it('modo crear: al enviar hace POST a /eventos con el cuerpo', () => {
    crear(null);
    fixture.detectChanges();
    escribir('nombre', 'Cena de Navidad');
    escribir('fecha', '2027-12-24');
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement).querySelector('form')!.dispatchEvent(new Event('submit'));

    const req = httpMock.expectOne(`${base}/eventos`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      nombre: 'Cena de Navidad',
      descripcion: null,
      lugar: null,
      fecha: '2027-12-24',
      fechaFin: null,
    });
    req.flush({ id: 3 });
  });

  it('modo editar: precarga con GET y al enviar hace PUT', () => {
    crear('7');
    fixture.detectChanges();
    httpMock.expectOne(`${base}/eventos/7`).flush({
      id: 7,
      nombre: 'San Miguel',
      descripcion: null,
      lugar: 'La plaza',
      fecha: '2026-09-25',
      fechaFin: '2026-09-26',
      pasado: false,
      creadoPor: null,
      puedoEditar: true,
      puedoBorrar: true,
      borradoPendiente: false,
    });
    fixture.detectChanges();

    escribir('nombre', 'San Miguel 2026');
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector('form')!.dispatchEvent(new Event('submit'));

    const put = httpMock.expectOne(`${base}/eventos/7`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body.nombre).toBe('San Miguel 2026');
    put.flush({ id: 7 });
  });

  it('muestra un mensaje si el backend responde SIN_CREDITO_EVENTO', () => {
    crear(null);
    fixture.detectChanges();
    escribir('nombre', 'X');
    escribir('fecha', '2027-01-01');
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector('form')!.dispatchEvent(new Event('submit'));

    httpMock
      .expectOne(`${base}/eventos`)
      .flush({ codigo: 'SIN_CREDITO_EVENTO' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'No tienes ningún evento autorizado sin crear',
    );
  });
});
