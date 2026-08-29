import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Registro } from './registro';

/**
 * Test del arrastre de la contraseña: `irASolicitarAcceso()` debe incluir el
 * `password` tecleado en el `state` de la navegación a /solicitar-acceso, junto
 * con los datos de identidad.
 */
describe('Registro · irASolicitarAcceso', () => {
  let componente: Registro;
  let navigateSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Registro],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    const router = TestBed.inject(Router);
    navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    componente = TestBed.createComponent(Registro).componentInstance;
  });

  it('lleva el password (y la identidad) en el state de la navegación', () => {
    (componente as unknown as { form: { patchValue: (v: unknown) => void } }).form.patchValue({
      nombre: 'Ada',
      apellidos: 'Lovelace',
      telefono: '600000000',
      email: 'ada@example.com',
      password: 'secreto123',
    });

    (componente as unknown as { irASolicitarAcceso: () => void }).irASolicitarAcceso();

    expect(navigateSpy).toHaveBeenCalledTimes(1);
    const [comando, extras] = navigateSpy.mock.calls[0] as [unknown, { state: unknown }];
    expect(comando).toEqual(['/solicitar-acceso']);
    expect(extras.state).toEqual({
      nombre: 'Ada',
      apellidos: 'Lovelace',
      telefono: '600000000',
      email: 'ada@example.com',
      password: 'secreto123',
    });
  });
});
