import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { AsistenciaResumen } from '../eventos.types';
import { ModalAnadirAsistente } from './modal-anadir-asistente';

describe('ModalAnadirAsistente', () => {
  let fixture: ComponentFixture<ModalAnadirAsistente>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  function montar(llevaFicha: boolean) {
    TestBed.configureTestingModule({
      imports: [ModalAnadirAsistente],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(ModalAnadirAsistente);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('eventoId', 5);
    fixture.componentRef.setInput('llevaFicha', llevaFicha);
    fixture.componentRef.setInput('diasEvento', ['2026-09-25']);
    fixture.detectChanges();
    if (llevaFicha) {
      httpMock.expectOne(`${base}/bebidas/catalogo`).flush({ alcohol: [], refresco: [] });
      fixture.detectChanges();
    }
  }

  afterEach(() => httpMock.verify());

  it('sin ficha de bebida, añadir hace POST con nombre y teléfono y emite el resultado', () => {
    montar(false);
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const comp = fixture.componentInstance as any;
    comp.nombre.set('Primo de Juan');
    comp.telefono.set('612345678');
    let emitido: AsistenciaResumen | undefined;
    fixture.componentInstance.anadido.subscribe((r: AsistenciaResumen) => (emitido = r));

    comp.anadirSinFicha();
    const post = httpMock.expectOne(`${base}/eventos/5/asistencias`);
    expect(post.request.body).toEqual({
      nombre: 'Primo de Juan',
      telefono: '612345678',
      estado: 'APUNTADO',
    });
    post.flush({ id: 9, nombre: 'Primo de Juan', telefono: '612345678', estado: 'APUNTADO', esManual: true });

    expect(emitido?.id).toBe(9);
  });

  it('con ficha y "confirmar el pago ahora" marcado, añade y confirma el pago', () => {
    montar(true);
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const comp = fixture.componentInstance as any;
    comp.nombre.set('Ana');
    comp.confirmarPago.set(true);
    comp.metodoPago.set('BIZUM');

    const ficha = {
      estado: 'APUNTADO' as const,
      alcoholBebidaId: null,
      alcoholOtra: null,
      refrescoBebidaId: null,
      refrescoOtra: 'Agua',
      alternativa: 'NADA' as const,
      cervezaEspecial: null,
      embarazada: false,
      asisteDia1: true,
      asisteDia2: true,
    };
    comp.anadirConFicha(ficha);

    const post = httpMock.expectOne(`${base}/eventos/5/asistencias`);
    expect(post.request.body).toEqual({ nombre: 'Ana', telefono: null, estado: 'APUNTADO', ficha });
    post.flush({ id: 11, nombre: 'Ana', telefono: null, estado: 'APUNTADO', esManual: true, cuota: 26, modalidad: 'COMPLETA' });

    const put = httpMock.expectOne(`${base}/eventos/5/asistencias/11/pago`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ metodo: 'BIZUM' });
    put.flush({ asistentes: [], totalCuotas: 0, totalPagado: 0, miCuota: null, puedoPagarPor: [] });
  });
});
