import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { EventosOcultos } from './eventos-ocultos';

/** Tests de la pantalla de eventos ocultos: listado y recuperar uno. */
describe('EventosOcultos', () => {
  let fixture: ComponentFixture<EventosOcultos>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  function crear() {
    TestBed.configureTestingModule({
      imports: [EventosOcultos],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(EventosOcultos);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => httpMock.verify());

  function responder(eventos: unknown[]) {
    httpMock.expectOne(`${base}/eventos/ocultos`).flush({ eventos });
  }

  it('pinta cada evento oculto', () => {
    crear();
    fixture.detectChanges();
    responder([{ id: 1, nombre: 'San Miguel 2026', fecha: '2026-09-25' }]);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('San Miguel 2026');
  });

  it('sin ninguno oculto muestra el mensaje vacío', () => {
    crear();
    fixture.detectChanges();
    responder([]);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'No hay ningún evento oculto',
    );
  });

  it('al pulsar Recuperar hace PUT /recuperar y lo quita de la lista', () => {
    crear();
    fixture.detectChanges();
    responder([{ id: 1, nombre: 'San Miguel 2026', fecha: '2026-09-25' }]);
    fixture.detectChanges();

    const recuperar = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.trim() === 'Recuperar');
    recuperar?.dispatchEvent(new Event('click'));

    const put = httpMock.expectOne(`${base}/eventos/1/recuperar`);
    expect(put.request.method).toBe('PUT');
    put.flush({});
    fixture.detectChanges();

    const txt = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(txt).toContain('«San Miguel 2026» recuperado');
    expect(txt).toContain('No hay ningún evento oculto');
  });
});
