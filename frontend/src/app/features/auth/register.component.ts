import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <div class="auth-box">

        @if (registered()) {
          <div class="auth-sub">Account created</div>
          <h1 class="auth-title">Check your <em>inbox</em></h1>
          <p class="verify-msg">
            We sent a verification link to <strong>{{ registeredEmail() }}</strong>.
            Click it to activate your account, then come back to log in.
          </p>
          <a routerLink="/auth/login" class="btn btn-primary btn-lg btn-block">Go to login</a>
        } @else {
          <div class="auth-sub">Get started</div>
          <h1 class="auth-title">Join <em>CRV Bookings</em></h1>

          <div class="role-tabs">
            <button type="button" class="role-tab" [class.active]="role() === 'customer'" (click)="role.set('customer')">
              <div class="role-tab-label">As a customer</div>
              <div class="role-tab-sub">Book tickets</div>
            </button>
            <button type="button" class="role-tab" [class.active]="role() === 'organizer'" (click)="role.set('organizer')">
              <div class="role-tab-label">As an organizer</div>
              <div class="role-tab-sub">Host events</div>
            </button>
          </div>

          @if (error()) { <div class="alert alert-error">{{ error() }}</div> }

          <form [formGroup]="form" (ngSubmit)="submit()" class="stack gap-16">
            <div class="row gap-12">
              <div class="field" style="flex:1">
                <label>First name</label>
                <input formControlName="firstName" class="inp" placeholder="Alex">
              </div>
              <div class="field" style="flex:1">
                <label>Last name</label>
                <input formControlName="lastName" class="inp" placeholder="Reyes">
              </div>
            </div>
            <div class="field">
              <label>Email</label>
              <input formControlName="email" type="email" class="inp" placeholder="you@somewhere.com" autocomplete="email">
              @if (form.get('email')?.touched && form.get('email')?.invalid) {
                <div class="field-error">Enter a valid email address.</div>
              }
            </div>
            <div class="field">
              <label>Password</label>
              <input formControlName="password" type="password" class="inp" placeholder="At least 8 characters" autocomplete="new-password">
              @if (form.get('password')?.touched && form.get('password')?.invalid) {
                <div class="field-error">Password must be at least 8 characters.</div>
              }
            </div>
            <div class="row gap-12">
              <div class="field" style="flex:1">
                <label>Phone</label>
                <input formControlName="phoneNumber" class="inp" placeholder="+31 6 0000 0000">
              </div>
              <div class="field" style="flex:1">
                <label>Country</label>
                <input formControlName="country" class="inp" placeholder="NL">
              </div>
            </div>
            <button class="btn btn-primary btn-lg btn-block" type="submit" [disabled]="loading()">
              @if (loading()) { <span class="spinner"></span> } Create account
            </button>
          </form>

          <div class="auth-switch">
            Already have one? <a routerLink="/auth/login">Log in</a>
          </div>
        }

      </div>
    </div>
  `,
  styles: [`
    .auth-page { min-height: calc(100vh - 57px); display: flex; align-items: center; justify-content: center; padding: 2rem 1rem; background: var(--bg); }
    .auth-box { width: 100%; max-width: 440px; }
    .auth-sub { font-family: var(--mono); font-size: 11px; text-transform: uppercase; letter-spacing: 0.12em; color: var(--ink-4); margin-bottom: 8px; }
    .auth-title { font-family: var(--serif); font-size: 42px; letter-spacing: -0.02em; margin-bottom: 28px; }
    .auth-title em { font-style: italic; color: var(--accent-ink); }
    .role-tabs { display: grid; grid-template-columns: 1fr 1fr; gap: 0; padding: 3px; background: var(--bg-sunk); border-radius: var(--radius); margin-bottom: 24px; }
    .role-tab { padding: 10px 12px; border-radius: var(--radius); background: transparent; text-align: left; transition: all 0.15s; cursor: pointer; }
    .role-tab.active { background: var(--bg-card); box-shadow: 0 1px 3px rgba(0,0,0,0.06); }
    .role-tab-label { font-size: 13px; font-weight: 500; color: var(--ink-3); }
    .role-tab.active .role-tab-label { color: var(--ink); }
    .role-tab-sub { font-size: 11px; color: var(--ink-4); margin-top: 2px; }
    .auth-switch { margin-top: 20px; font-size: 13px; color: var(--ink-3); text-align: center; }
    .auth-switch a { color: var(--ink); text-decoration: underline; }
    .field-error { font-size: 12px; color: var(--error, #e53935); margin-top: 4px; }
    .verify-msg { font-size: 15px; color: var(--ink-3); line-height: 1.6; margin-bottom: 28px; }
    .verify-msg strong { color: var(--ink); }
  `]
})
export class RegisterComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);

  role = signal<'customer' | 'organizer'>('customer');
  loading = signal(false);
  error = signal('');
  registered = signal(false);
  registeredEmail = signal('');

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    phoneNumber: ['', Validators.required],
    country: ['', Validators.required],
  });

  submit() {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.error.set('Please fill in all required fields correctly.');
      return;
    }
    this.error.set('');
    this.loading.set(true);
    const v = this.form.value;
    this.auth.register({
      email: v.email!, password: v.password!,
      firstName: v.firstName!, lastName: v.lastName!,
      phoneNumber: v.phoneNumber || undefined,
      country: v.country || undefined,
      role: this.role(),
    }).subscribe({
      next: () => {
        this.loading.set(false);
        this.registeredEmail.set(v.email!);
        this.registered.set(true);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.message?.replace('ApolloError: ', '') || 'Registration failed');
      }
    });
  }
}
