import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

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
