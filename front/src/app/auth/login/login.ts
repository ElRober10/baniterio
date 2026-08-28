import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../auth.service';

/**
 * Pantalla de login. Componente standalone de Angular.
 *
 * - `form`: formulario reactivo con validación de teléfono (regex ^[67]\d{8}$)
 *   y contraseña obligatoria. La plantilla (login.html) pinta los errores.
 * - `estado`: signal con el estado de la pantalla ('idle' | 'enviando' |
 *   'error'). La plantilla reacciona a él (deshabilita el botón, muestra el
 *   mensaje de error). Un "signal" es un valor reactivo de Angular.
 * - `enviar()`: si el form es válido, llama a AuthService.login. En éxito
 *   navega a /panel (el token ya lo guardó AuthService). En error muestra el
 *   mensaje genérico.
 *
 * Nota: cualquier error HTTP (incluido servidor caído) se muestra hoy como
 * "credenciales incorrectas" — mejora pendiente anotada en el plan.
 */
@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-login',
  styleUrl: './login.css',
  templateUrl: './login.html',
})
export class Login {
  private readonly formBuilder = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  protected readonly estado = signal<'idle' | 'enviando' | 'error'>('idle');

  protected readonly form = this.formBuilder.group({
    telefono: ['', [Validators.required, Validators.pattern(/^[67]\d{8}$/)]],
    password: ['', [Validators.required]],
  });

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.estado.set('enviando');
    const { telefono, password } = this.form.getRawValue();

    this.auth.login({ telefono: telefono!, password: password! }).subscribe({
      next: () => this.router.navigateByUrl('/panel'),
      error: (err: HttpErrorResponse) => {
        this.estado.set('error');
        console.debug('login falló', err.status);
      },
    });
  }
}
