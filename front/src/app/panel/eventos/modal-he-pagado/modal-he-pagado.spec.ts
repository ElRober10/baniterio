import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { ModalHePagado, PagoDeclarado } from './modal-he-pagado';

describe('ModalHePagado', () => {
  let fixture: ComponentFixture<ModalHePagado>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  function montar() {
    TestBed.configureTestingModule({
      imports: [ModalHePagado],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(ModalHePagado);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('eventoId', 3);
    fixture.detectChanges();
    httpMock.expectOne(`${base}/eventos/3/asistentes`).flush({
      asistentes: [],
      totalCuotas: 0,
      totalPagado: 0,
      miCuota: 45,
      puedoPagarPor: [
        { nombre: 'Ana', cuota: 45, relacion: 'PAREJA', usuarioId: 8, asistenciaId: null },
      ],
    });
    fixture.detectChanges();
  }

  afterEach(() => httpMock.verify());

  it('Enviar está deshabilitado hasta que hay importe y método', () => {
    montar();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const comp = fixture.componentInstance as any;
    comp.importe.set(0);
    fixture.detectChanges();
    const boton = fixture.nativeElement.querySelector('button[data-test="enviar"]') as HTMLButtonElement;
    expect(boton.disabled).toBe(true);
  });

  it('al enviar emite importe, método y a quién cubre', () => {
    montar();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const comp = fixture.componentInstance as any;
    let emitido: PagoDeclarado | undefined;
    fixture.componentInstance.enviado.subscribe((e: PagoDeclarado) => (emitido = e));
    comp.metodo.set('BIZUM');
    comp.marcarUsuario(8);
    comp.importe.set(90);
    comp.enviar();
    expect(emitido).toEqual({
      importe: 90,
      metodo: 'BIZUM',
      cubre: { yo: true, usuarioIds: [8], asistenciaIds: [] },
    });
  });
});
