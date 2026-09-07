import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { EventosService } from './eventos.service';
import { GuardarEventoRequest } from './eventos.types';

/**
 * Un caso por método: comprueba que cada llamada de EventosService pega al
 * endpoint correcto con el método/cuerpo/params correctos. Mismo patrón que
 * PerfilService.spec.ts.
 */
describe('EventosService', () => {
  let service: EventosService;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(EventosService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar() hace GET a /eventos con el parámetro pagina', () => {
    service.listar(2).subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/eventos`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('pagina')).toBe('2');
    req.flush({ eventos: [], pagina: 2, totalPaginas: 3, puedeCrear: false, puedeSolicitar: true });
  });

  it('detalle() hace GET a /eventos/:id', () => {
    service.detalle(7).subscribe();
    const req = httpMock.expectOne(`${base}/eventos/7`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('asistentesEvento() hace GET a /eventos/:id/asistentes', () => {
    service.asistentesEvento(7).subscribe();
    const req = httpMock.expectOne(`${base}/eventos/7/asistentes`);
    expect(req.request.method).toBe('GET');
    req.flush({ asistentes: [], totalCuotas: 0, totalPagado: 0, puedoPagarPor: [], miCuota: null });
  });

  it('crear() hace POST a /eventos con el cuerpo', () => {
    const body: GuardarEventoRequest = {
      nombre: 'Cena',
      descripcion: null,
      lugar: null,
      fecha: '2027-12-01',
      fechaFin: null,
      cuentaId: 2,
      cuentaNueva: false,
      cuotaCubatas: null,
      precioCamiseta: null,
      precioSudadera: null,
    };
    service.crear(body).subscribe();
    const req = httpMock.expectOne(`${base}/eventos`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('editar() hace PUT a /eventos/:id', () => {
    const body: GuardarEventoRequest = {
      nombre: 'Cena 2',
      descripcion: null,
      lugar: null,
      fecha: '2027-12-02',
      fechaFin: null,
      cuentaId: 2,
      cuentaNueva: false,
      cuotaCubatas: null,
      precioCamiseta: null,
      precioSudadera: null,
    };
    service.editar(7, body).subscribe();
    const req = httpMock.expectOne(`${base}/eventos/7`);
    expect(req.request.method).toBe('PUT');
    req.flush({});
  });

  it('ocultar() hace DELETE a /eventos/:id', () => {
    service.ocultar(7).subscribe();
    const req = httpMock.expectOne(`${base}/eventos/7`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('recuperar() hace PUT a /eventos/:id/recuperar', () => {
    service.recuperar(7).subscribe();
    const req = httpMock.expectOne(`${base}/eventos/7/recuperar`);
    expect(req.request.method).toBe('PUT');
    req.flush({});
  });

  it('listarOcultos() hace GET a /eventos/ocultos', () => {
    service.listarOcultos().subscribe();
    const req = httpMock.expectOne(`${base}/eventos/ocultos`);
    expect(req.request.method).toBe('GET');
    req.flush({ eventos: [] });
  });

  it('solicitarCrear() hace POST a /eventos/solicitudes con el mensaje', () => {
    service.solicitarCrear('quiero organizar la cena').subscribe();
    const req = httpMock.expectOne(`${base}/eventos/solicitudes`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ mensaje: 'quiero organizar la cena' });
    req.flush({ id: 1, estado: 'PENDIENTE' });
  });

  it('responder() hace PUT a /eventos/:id/asistencia con el estado', () => {
    service.responder(5, 'NO_VOY').subscribe();
    const req = httpMock.expectOne(`${base}/eventos/5/asistencia`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ estado: 'NO_VOY', paraUsuarioId: null });
    req.flush({});
  });

  it('responder() por otro manda su paraUsuarioId', () => {
    service.responder(5, 'APUNTADO', 9).subscribe();
    const req = httpMock.expectOne(`${base}/eventos/5/asistencia`);
    expect(req.request.body).toEqual({ estado: 'APUNTADO', paraUsuarioId: 9 });
    req.flush({});
  });

  it('mandarNotificacion() hace POST a /eventos/:id/notificacion con el texto', () => {
    service.mandarNotificacion(5, 'nos vemos').subscribe();
    const req = httpMock.expectOne(`${base}/eventos/5/notificacion`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ texto: 'nos vemos' });
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('mandarNotificacion() sin texto manda cuerpo vacío', () => {
    service.mandarNotificacion(5).subscribe();
    const req = httpMock.expectOne(`${base}/eventos/5/notificacion`);
    expect(req.request.body).toEqual({});
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('anadirAsistente() hace POST a /eventos/:id/asistencias', () => {
    service.anadirAsistente(5, 'Primo de Juan', 'APUNTADO').subscribe();
    const req = httpMock.expectOne(`${base}/eventos/5/asistencias`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ nombre: 'Primo de Juan', estado: 'APUNTADO' });
    req.flush({ id: 1, nombre: 'Primo de Juan', estado: 'APUNTADO', esManual: true });
  });

  it('quitarAsistente() hace DELETE a /eventos/:id/asistencias/:asistenciaId', () => {
    service.quitarAsistente(5, 9).subscribe();
    const req = httpMock.expectOne(`${base}/eventos/5/asistencias/9`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('pendientesRespuesta() hace GET a /eventos/pendientes-respuesta', () => {
    service.pendientesRespuesta().subscribe();
    const req = httpMock.expectOne(`${base}/eventos/pendientes-respuesta`);
    expect(req.request.method).toBe('GET');
    req.flush({ eventos: [] });
  });

  it('catalogoBebidas() hace GET a /bebidas/catalogo', () => {
    service.catalogoBebidas().subscribe();
    const req = httpMock.expectOne(`${base}/bebidas/catalogo`);
    expect(req.request.method).toBe('GET');
    req.flush({ alcohol: [], refresco: [] });
  });

  it('guardarFichaBebida() hace PUT a /eventos/:id/ficha-bebida', () => {
    const body = {
      estado: 'APUNTADO' as const,
      alcoholBebidaId: null,
      alcoholOtra: null,
      refrescoBebidaId: 3,
      refrescoOtra: null,
      alternativa: 'NADA' as const,
      cervezaEspecial: null,
      embarazada: false,
      asisteDia1: true,
      asisteDia2: true,
    };
    service.guardarFichaBebida(5, body).subscribe();
    const req = httpMock.expectOne(`${base}/eventos/5/ficha-bebida`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(body);
    req.flush({ modalidad: 'COMPLETA', cuota: 26, cuotaPendiente: false });
  });

  it('anadirAsistente() con ficha la manda en el cuerpo', () => {
    const ficha = {
      estado: 'APUNTADO' as const,
      alcoholBebidaId: null,
      alcoholOtra: null,
      refrescoBebidaId: 3,
      refrescoOtra: null,
      alternativa: 'NADA' as const,
      cervezaEspecial: null,
      embarazada: false,
      asisteDia1: true,
      asisteDia2: true,
    };
    service.anadirAsistente(5, 'Primo', 'APUNTADO', ficha).subscribe();
    const req = httpMock.expectOne(`${base}/eventos/5/asistencias`);
    expect(req.request.body).toEqual({ nombre: 'Primo', estado: 'APUNTADO', ficha });
    req.flush({ id: 1, nombre: 'Primo', estado: 'APUNTADO', esManual: true, cuota: 26, modalidad: 'COMPLETA' });
  });

  it('bebidasPendientes() hace GET a /bebidas con estado=PENDIENTE', () => {
    service.bebidasPendientes().subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/bebidas`);
    expect(req.request.params.get('estado')).toBe('PENDIENTE');
    req.flush([]);
  });

  it('aceptarBebida() hace POST a /bebidas/:id/aceptar', () => {
    service.aceptarBebida(3).subscribe();
    const req = httpMock.expectOne(`${base}/bebidas/3/aceptar`);
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  const listadoVacio = {
    asistentes: [],
    totalCuotas: 0,
    totalPagado: 0,
    puedoPagarPor: [],
    miCuota: null,
    puedoConfirmarPagos: true,
  };

  it('confirmarPago() hace PUT al endpoint de pago con el método', () => {
    let resp: unknown;
    service.confirmarPago(3, 7, 'BIZUM').subscribe((r) => (resp = r));
    const req = httpMock.expectOne(`${base}/eventos/3/asistencias/7/pago`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ metodo: 'BIZUM' });
    req.flush(listadoVacio);
    expect(resp).toBeTruthy();
  });

  it('deshacerPago() hace DELETE al endpoint de pago', () => {
    service.deshacerPago(3, 7).subscribe();
    const req = httpMock.expectOne(`${base}/eventos/3/asistencias/7/pago`);
    expect(req.request.method).toBe('DELETE');
    req.flush(listadoVacio);
  });

  it('declararPago() hace POST a /eventos/:id/pagos-declarados con el cuerpo', () => {
    service
      .declararPago(3, { importe: 16, metodo: 'BIZUM', cubreUsuarioIds: [9], cubreAsistenciaIds: [] })
      .subscribe();
    const req = httpMock.expectOne(`${base}/eventos/3/pagos-declarados`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      importe: 16,
      metodo: 'BIZUM',
      cubreUsuarioIds: [9],
      cubreAsistenciaIds: [],
    });
    req.flush({});
  });

  it('anularPagoDeclarado() hace DELETE a /eventos/:id/pagos-declarados/mia', () => {
    service.anularPagoDeclarado(3).subscribe();
    const req = httpMock.expectOne(`${base}/eventos/3/pagos-declarados/mia`);
    expect(req.request.method).toBe('DELETE');
    req.flush({});
  });

  it('pagosDeclaradosPendientes() hace GET a /admin/pagos-declarados', () => {
    service.pagosDeclaradosPendientes().subscribe();
    const req = httpMock.expectOne(`${base}/admin/pagos-declarados`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('confirmarPagoDeclarado() hace POST a /admin/pagos-declarados/:id/confirmar', () => {
    service.confirmarPagoDeclarado(5).subscribe();
    const req = httpMock.expectOne(`${base}/admin/pagos-declarados/5/confirmar`);
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('rechazarPagoDeclarado() hace POST a /admin/pagos-declarados/:id/rechazar', () => {
    service.rechazarPagoDeclarado(5).subscribe();
    const req = httpMock.expectOne(`${base}/admin/pagos-declarados/5/rechazar`);
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });
});
