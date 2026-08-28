import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-registro',
  styleUrl: './registro.css',
  templateUrl: './registro.html',
})
export class Registro {
  private readonly formBuilder = inject(FormBuilder);

  protected readonly form = this.formBuilder.group({
    nombre: ['', [Validators.required]],
    apellidos: ['', [Validators.required]],
    mote: [''],
    telefono: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  protected readonly estado = signal<'idle' | 'enviado'>('idle');

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    // El backend de registro (con la comprobación de teléfono autorizado) todavía
    // no existe; de momento solo mostramos que el formulario funciona, sin llamar
    // a ninguna API real. Cuando exista, un teléfono no autorizado debe ofrecer
    // aquí mismo el formulario de "solicitud de ingreso" como alternativa.
    this.estado.set('enviado');
  }
}
