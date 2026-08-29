import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../auth.service';

/**
 * Pantalla de registro (alta de cuenta). Componente standalone de Angular.
 *
 * - `form`: nombre, apellidos, mote (opcional), teléfono (regex), email,
 *   contraseña (mín. 6). La validación de cliente evita llamadas inútiles.
 * - `estado` (signal): 'idle' | 'enviando' | 'ok' | 'no_autorizado' | 'error'.
 *   registro.html usa un `@switch` sobre este signal para mostrar el formulario,
 *   el mensaje de éxito o el aviso de teléfono no autorizado.
 * - `mensajeError` (signal): cuando `estado === 'error'`, el texto concreto
 *   según lo que devolvió el backend (o si no hubo conexión).
 * - `enviar()`: llama a AuthService.registro. En éxito → 'ok' y a los 1,2 s
 *   redirige a /login. Un 403 TELEFONO_NO_AUTORIZADO → pantalla 'no_autorizado';
 *   el resto de errores → 'error' con su mensaje.
 */
type Estado = 'idle' | 'enviando' | 'ok' | 'no_autorizado' | 'error';

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-registro',
  styleUrl: './registro.css',
  templateUrl: './registro.html',
})
export class Registro {
  private readonly formBuilder = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  protected readonly estado = signal<Estado>('idle');
  protected readonly mensajeError = signal('');

  protected readonly form = this.formBuilder.group({
    nombre: ['', [Validators.required]],
    apellidos: ['', [Validators.required]],
    mote: [''],
    telefono: ['', [Validators.required, Validators.pattern(/^[67]\d{8}$/)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.estado.set('enviando');
    const v = this.form.getRawValue();

    this.auth
      .registro({
        nombre: v.nombre!,
        apellidos: v.apellidos!,
        mote: v.mote ?? '',
        telefono: v.telefono!,
        email: v.email!,
        password: v.password!,
      })
      .subscribe({
        next: () => {
          this.estado.set('ok');
          setTimeout(() => this.router.navigateByUrl('/login'), 1200);
        },
        error: (err: HttpErrorResponse) => {
          if (err.error?.codigo === 'TELEFONO_NO_AUTORIZADO') {
            this.estado.set('no_autorizado');
            return;
          }
          this.mensajeError.set(this.mensajeDe(err));
          this.estado.set('error');
        },
      });
  }

  /** Va al formulario de solicitud de acceso llevando lo ya tecleado. */
  protected irASolicitarAcceso(): void {
    const v = this.form.getRawValue();
    this.router.navigate(['/solicitar-acceso'], {
      state: { nombre: v.nombre, apellidos: v.apellidos, telefono: v.telefono, email: v.email },
    });
  }

  private mensajeDe(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return 'No se pudo conectar con el servidor. Inténtalo de nuevo en un momento.';
    }
    switch (err.error?.codigo) {
      case 'YA_REGISTRADO':
        return 'Ya existe una cuenta con ese teléfono o ese email.';
      case 'VALIDACION':
        return 'Revisa los datos del formulario: hay algún campo incorrecto.';
      default:
        return 'No se pudo completar el registro. Inténtalo de nuevo más tarde.';
    }
  }
}
