import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { convertToParamMap } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { EventoDetalleComponent } from './evento-detalle';

/**
 * Tests del detalle de un evento: carga por id, y los botones de gestión
 * dependen de los permisos que devuelve el backend.
 */
describe('EventoDetalleComponent', () => {
  let fixture: ComponentFixture<EventoDetalleComponent>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  function crear(id = '5') {
    TestBed.configureTestingModule({
      imports: [EventoDetalleComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id }) } },
        },
      ],
    });
    fixture = TestBed.createComponent(EventoDetalleComponent);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => httpMock.verify());

  function responder(extra: Record<string, unknown> = {}) {
    httpMock.expectOne(`${base}/eventos/5`).flush({
      id: 5,
      nombre: 'San Miguel',
      descripcion: 'La fiesta grande',
      lugar: 'La plaza',
      fecha: '2026-09-25',
      fechaFin: '2026-09-26',
      pasado: false,
      cuenta: { id: 2, nombre: 'San Miguel' },
      creadoPor: null,
      puedoEditar: false,
      puedoBorrar: false,
      borradoPendiente: false,
      ...extra,
    });
  }

  it('pinta el nombre, las fechas, el lugar, la descripción y la cuenta', () => {
    crear();
    fixture.detectChanges();
    responder();
    fixture.detectChanges();
    const txt = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(txt).toContain('San Miguel');
    expect(txt).toContain('2026-09-25');
    expect(txt).toContain('La plaza');
    expect(txt).toContain('La fiesta grande');
    expect(txt).toContain('Cuenta:');
    const enlace = (fixture.nativeElement as HTMLElement).querySelector('a[href="/panel/cuentas/2"]');
    expect(enlace).toBeTruthy();
  });

  it('sin permisos no muestra botones de gestión', () => {
    crear();
    fixture.detectChanges();
    responder();
    fixture.detectChanges();
    const txt = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(txt).not.toContain('Gestionar');
    expect(txt).not.toContain('Borrar');
  });

  it('con puedoBorrar y borradoPendiente, el botón está deshabilitado', () => {
    crear();
    fixture.detectChanges();
    responder({ puedoBorrar: true, borradoPendiente: true });
    fixture.detectChanges();
    const boton = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.includes('Borrado pendiente'));
    expect(boton).toBeTruthy();
    expect((boton as HTMLButtonElement).disabled).toBe(true);
  });

  it('al borrar, si el backend responde 202 muestra el aviso de solicitud', () => {
    crear();
    fixture.detectChanges();
    responder({ puedoBorrar: true, creadoPor: { id: 9, nombre: 'Ana' } });
    fixture.detectChanges();

    const borrar = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.includes('Solicitar borrado'));
    borrar?.dispatchEvent(new Event('click'));

    const del = httpMock.expectOne(`${base}/eventos/5`);
    expect(del.request.method).toBe('DELETE');
    del.flush({ estado: 'PENDIENTE' }, { status: 202, statusText: 'Accepted' });
    // recarga tras el aviso
    responder({ puedoBorrar: true, borradoPendiente: true, creadoPor: { id: 9, nombre: 'Ana' } });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Solicitud de borrado enviada');
  });
});
