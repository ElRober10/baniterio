import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { ModalAsistentes } from './modal-asistentes';

describe('ModalAsistentes', () => {
  let fixture: ComponentFixture<ModalAsistentes>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ModalAsistentes],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(ModalAsistentes);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('eventoId', 3);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('carga el listado y pinta una fila por asistente con su cuota', () => {
    httpMock.expectOne(`${base}/eventos/3/asistentes`).flush({
      asistentes: [
        {
          nombre: 'Ana',
          estado: 'APUNTADO',
          esManual: false,
          bebida: { alcohol: 'Barceló', refresco: 'Coca-Cola', alternativa: 'NADA', modalidad: 'COMPLETA' },
          cuota: 45,
          pagado: false,
        },
        { nombre: 'Luis', estado: 'EN_DUDA', esManual: true, bebida: null, cuota: null, pagado: false },
      ],
      totalCuotas: 45,
      totalPagado: 0,
      puedoPagarPor: [],
      miCuota: 45,
    });
    fixture.detectChanges();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Ana');
    expect(texto).toContain('45 €');
    expect(texto).toContain('Luis');
    expect(texto).toContain('en duda');
    expect(texto).toContain('Total cuotas');
  });

  it('emite (cerrar) al pulsar Cerrar', () => {
    httpMock.expectOne(`${base}/eventos/3/asistentes`).flush({
      asistentes: [],
      totalCuotas: 0,
      totalPagado: 0,
      puedoPagarPor: [],
      miCuota: null,
    });
    fixture.detectChanges();
    let cerrado = false;
    fixture.componentInstance.cerrar.subscribe(() => (cerrado = true));
    const boton = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === 'Cerrar',
    );
    boton?.click();
    expect(cerrado).toBe(true);
  });
});
