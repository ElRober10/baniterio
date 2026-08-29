import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../auth.service';

/**
 * Pantalla de "solicitar acceso a la peña". Se llega aquí desde el registro
 * cuando el teléfono NO está autorizado (botón en el estado `no_autorizado`).
 *
 * - Los 4 datos de identidad se precargan con lo que el usuario ya tecleó en el
 *   registro (llega por `history.state`; si recargas la página se pierde y hay
 *   que reteclearlos).
 * - Los 3 textareas (motivo, relación, conocidos) son texto libre, mínimo 10
 *   caracteres cada uno.
 * - Al enviar OK: mensaje de confirmación. La solicitud queda PENDIENTE en la
 *   BBDD para que un administrador la revise (panel de admin: pendiente).
 */
type Estado = 'idle' | 'enviando' | 'ok' | 'error';

interface PrecargaRegistro {
  nombre?: string;
  apellidos?: string;
  telefono?: string;
  email?: string;
  /** Contraseña tecleada en el registro; se arrastra pero NUNCA se muestra. */
  password?: string;
}

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-solicitar-acceso',
  styleUrl: './solicitar-acceso.css',
  templateUrl: './solicitar-acceso.html',
})
export class SolicitarAcceso {
  private readonly formBuilder = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  protected readonly estado = signal<Estado>('idle');
  protected readonly mensajeError = signal('');

  /**
   * Contraseña que llegó desde el registro por `history.state`. Se guarda aquí
   * (no en el formulario, no se pinta) y solo se adjunta al cuerpo de la
   * solicitud. `null` si se llegó directo a /solicitar-acceso o se recargó.
   */
  private readonly passwordArrastrada: string | null;

  protected readonly form = this.formBuilder.group({
    nombre: ['', [Validators.required]],
    apellidos: ['', [Validators.required]],
    telefono: ['', [Validators.required, Validators.pattern(/^[67]\d{8}$/)]],
    email: ['', [Validators.required, Validators.email]],
    motivo: ['', [Validators.required, Validators.minLength(10)]],
    relacion: ['', [Validators.required, Validators.minLength(10)]],
    conocidos: ['', [Validators.required, Validators.minLength(10)]],
  });

  constructor() {
    const previo = (this.router.getCurrentNavigation()?.extras.state ??
      history.state ??
      {}) as PrecargaRegistro;
    this.passwordArrastrada = previo.password ?? null;

    // history.state se persiste a disco (restauración de sesión del navegador).
    // La contraseña ya está en memoria (passwordArrastrada); la borramos de la
    // entrada del historial para que no quede en disco. Los datos de identidad
    // se conservan en `resto` para que un refresco siga precargando el formulario.
    if (previo.password) {
      const { password: _omitida, ...resto } = previo;
      history.replaceState(resto, '');
    }

    this.form.patchValue({
      nombre: previo.nombre ?? '',
      apellidos: previo.apellidos ?? '',
      telefono: previo.telefono ?? '',
      email: previo.email ?? '',
    });
  }

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.estado.set('enviando');
    const v = this.form.getRawValue();

    this.auth
      .solicitarAcceso({
        nombre: v.nombre!,
        apellidos: v.apellidos!,
        telefono: v.telefono!,
        email: v.email!,
        motivo: v.motivo!,
        relacion: v.relacion!,
        conocidos: v.conocidos!,
        ...(this.passwordArrastrada ? { password: this.passwordArrastrada } : {}),
      })
      .subscribe({
        next: () => this.estado.set('ok'),
        error: (err: HttpErrorResponse) => {
          this.mensajeError.set(this.mensajeDe(err));
          this.estado.set('error');
        },
      });
  }

  private mensajeDe(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return 'No se pudo conectar con el servidor. Inténtalo de nuevo en un momento.';
    }
    switch (err.error?.codigo) {
      case 'SOLICITUD_YA_PENDIENTE':
        return 'Ya hay una solicitud pendiente para ese teléfono. Espera a que la revisen.';
      case 'TELEFONO_YA_AUTORIZADO':
        return 'Ese teléfono ya está autorizado. Puedes registrarte directamente.';
      case 'VALIDACION':
        return 'Revisa los datos del formulario: hay algún campo incorrecto.';
      default:
        return 'No se pudo enviar la solicitud. Inténtalo de nuevo más tarde.';
    }
  }
}
