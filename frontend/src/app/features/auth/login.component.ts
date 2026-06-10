import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <div class="auth-box">
        <div class="auth-sub">Welcome back</div>
        <h1 class="auth-title">Log <em>in</em></h1>

        @if (error()) {
          <div class="alert alert-error">{{ error() }}</div>
        }

        <form [formGroup]="form" (ngSubmit)="submit()" class="stack gap-16">
          <div class="field">
            <label>Email</label>
            <input formControlName="username" class="inp" placeholder="you@somewhere.com" autocomplete="username">
          </div>
          <div class="field">
            <label>Password</label>
            <input formControlName="password" type="password" class="inp" placeholder="Your password" autocomplete="current-password">
          </div>
          <button class="btn btn-primary btn-lg btn-block" type="submit" [disabled]="loading()">
            @if (loading()) { <span class="spinner"></span> }
            Log in
          </button>
        </form>

        <div class="auth-switch">
          New here? <a routerLink="/auth/register">Create an account</a>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .auth-page { min-height: calc(100vh - 57px); display: flex; align-items: center; justify-content: center; padding: 2rem 1rem; background: var(--bg); }
    .auth-box { width: 100%; max-width: 440px; }
    .auth-sub { font-family: var(--mono); font-size: 11px; text-transform: uppercase; letter-spacing: 0.12em; color: var(--ink-4); margin-bottom: 8px; }
    .auth-title { font-family: var(--serif); font-size: 42px; letter-spacing: -0.02em; margin-bottom: 28px; }
    .auth-title em { font-style: italic; color: var(--accent-ink); }
    .auth-switch { margin-top: 20px; font-size: 13px; color: var(--ink-3); text-align: center; }
    .auth-switch a { color: var(--ink); text-decoration: underline; }
  `]
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  loading = signal(false);
  error = signal('');

  form = this.fb.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  submit() {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.error.set('');
    const { username, password } = this.form.value;
    this.auth.login(username!, password!).subscribe({
      next: (payload) => {
        this.loading.set(false);
        const roles = payload.user?.roles ?? [];
        this.router.navigate(roles.includes('employee') ? ['/organizer'] : ['/home']);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.message?.replace('ApolloError: ', '') || 'Invalid credentials');
      }
    });
  }
}
