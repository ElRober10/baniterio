import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { SolicitarAcceso } from './solicitar-acceso';

/**
 * Test del arrastre de la contraseña: si `SolicitarAcceso` recibe `password` en
 * `history.state` (venido del registro), lo reenvía en el cuerpo de
 * `POST /auth/solicitudes`; si no llega, el cuerpo no lleva la clave `password`.
 * La contraseña nunca se mete en el formulario.
 */
describe('SolicitarAcceso · password arrastrada', () => {
  let httpMock: HttpTestingController;
  const url = `${environment.apiBaseUrl}/auth/solicitudes`;

  const datosTextoLibre = {
    motivo: 'Quiero entrar en la peña porque me habéis invitado',
    relacion: 'Soy amigo de Pepe desde hace años',
    conocidos: 'Pepe, Juan y María me conocen bien',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SolicitarAcceso],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    history.replaceState({}, '');
  });

  function crear(state: Record<string, unknown>): SolicitarAcceso {
    history.replaceState(state, '');
    const componente = TestBed.createComponent(SolicitarAcceso).componentInstance;
    (componente as unknown as { form: { patchValue: (v: unknown) => void } }).form.patchValue(
      datosTextoLibre,
    );
    return componente;
  }

  it('reenvía el password recibido en history.state y no lo mete en el form', () => {
    const componente = crear({
      nombre: 'Ada',
      apellidos: 'Lovelace',
      telefono: '600000000',
      email: 'ada@example.com',
      password: 'secreto123',
    });

    const form = (componente as unknown as { form: { value: Record<string, unknown> } }).form;
    expect('password' in form.value).toBe(false);

    (componente as unknown as { enviar: () => void }).enviar();

    const req = httpMock.expectOne(url);
    expect(req.request.body.password).toBe('secreto123');
    req.flush(null, { status: 201, statusText: 'Created' });
  });

  it('borra la contraseña de history.state (no debe quedar en disco) y conserva la identidad', () => {
    crear({
      nombre: 'Ada',
      apellidos: 'Lovelace',
      telefono: '600000000',
      email: 'ada@example.com',
      password: 'secreto123',
    });

    expect('password' in history.state).toBe(false);
    expect(history.state.nombre).toBe('Ada');
    expect(history.state.email).toBe('ada@example.com');
  });

  it('sin password en el state, el cuerpo no lleva la clave password', () => {
    const componente = crear({
      nombre: 'Ada',
      apellidos: 'Lovelace',
      telefono: '600000000',
      email: 'ada@example.com',
    });

    (componente as unknown as { enviar: () => void }).enviar();

    const req = httpMock.expectOne(url);
    expect('password' in req.request.body).toBe(false);
    req.flush(null, { status: 201, statusText: 'Created' });
  });
});
