import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-login',
  styleUrl: './login.css',
  templateUrl: './login.html',
})
export class Login {
  private readonly formBuilder = inject(FormBuilder);
  private readonly router = inject(Router);

  protected readonly form = this.formBuilder.group({
    telefono: ['', [Validators.required]],
    password: ['', [Validators.required]],
  });

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    // El backend de login (JWT) todavía no existe; de momento cualquier
    // teléfono/contraseña se acepta como válido para poder construir el
    // resto del front autenticado.
    this.router.navigateByUrl('/panel');
  }
}
