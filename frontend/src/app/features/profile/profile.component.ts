import { Component, inject, signal, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Apollo } from 'apollo-angular';
import { ME, UPDATE_PROFILE } from '../../shared/graphql/documents';
import { User } from '../../shared/models/models';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <div class="container page">
      <div class="section-head" style="margin-bottom:32px">
        <div class="kicker">Account</div>
        <h2>My <em>Profile</em></h2>
      </div>

      @if (loading()) {
        <div class="loading-center"><div class="spinner spinner-lg"></div></div>
      } @else {
        @if (success()) { <div class="alert alert-success">Profile updated successfully.</div> }
        @if (error()) { <div class="alert alert-error">{{ error() }}</div> }

        <div class="profile-layout">
          <!-- Left: Identity card -->
          <div class="identity-card">
            <div class="avatar-circle">{{ initials() }}</div>
            <div class="identity-name">{{ user()?.firstName }} {{ user()?.lastName }}</div>
            <div class="identity-email mono xs muted">{{ user()?.email }}</div>
            <div style="margin-top:10px;display:flex;gap:6px;flex-wrap:wrap;justify-content:center">
              @for (role of user()?.roles ?? []; track role) {
                @if (role === 'customer' || role === 'employee') {
                  <span class="badge badge-accent">{{ role }}</span>
                }
              }
            </div>
          </div>

          <!-- Right: Form -->
          <div class="profile-form-area">
            <form [formGroup]="form" (ngSubmit)="save()">
              <div class="form-section">
                <div class="form-section-label">Personal info</div>
                <div class="form-row-2">
                  <div class="field">
                    <label>First name</label>
                    <input formControlName="firstName" class="inp">
                  </div>
                  <div class="field">
                    <label>Last name</label>
                    <input formControlName="lastName" class="inp">
                  </div>
                </div>
                <div class="field">
                  <label>Email</label>
                  <input formControlName="email" type="email" class="inp">
                </div>
                <div class="form-row-2">
                  <div class="field">
                    <label>Phone number</label>
                    <input formControlName="phoneNumber" class="inp">
                  </div>
                  <div class="field">
                    <label>Country</label>
                    <input formControlName="country" class="inp" placeholder="NL">
                  </div>
                </div>
              </div>

              <div class="form-section">
                <div class="form-section-label">Preferences</div>
                <div class="form-row-2">
                  <div class="field">
                    <label>Preferred language</label>
                    <input formControlName="preferredLanguage" class="inp" placeholder="en">
                  </div>
                  <div class="field">
                    <label>Currency</label>
                    <input formControlName="preferredCurrency" class="inp" placeholder="EUR">
                  </div>
                </div>
                <div class="field">
                  <label>Timezone</label>
                  <input formControlName="timezone" class="inp" placeholder="Europe/Amsterdam">
                </div>
              </div>

              <div class="form-section">
                <div class="form-section-label">Notifications</div>
                <div class="notif-row">
                  <label class="toggle-label">
                    <input type="checkbox" formControlName="emailNotifications">
                    <span>Email notifications</span>
                  </label>
                  <label class="toggle-label">
                    <input type="checkbox" formControlName="smsNotifications">
                    <span>SMS notifications</span>
                  </label>
                </div>
              </div>

              <div style="margin-top:24px">
                <button class="btn btn-primary" type="submit" [disabled]="saving()">
                  @if (saving()) { <span class="spinner"></span> } Save changes
                </button>
              </div>
            </form>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .profile-layout { display: grid; grid-template-columns: 240px 1fr; gap: 40px; align-items: start; }
    @media (max-width: 768px) { .profile-layout { grid-template-columns: 1fr; } }

    .identity-card {
      background: var(--bg-card); border: 1px solid var(--line);
      border-radius: var(--radius-lg); padding: 28px 20px;
      display: flex; flex-direction: column; align-items: center; text-align: center;
      position: sticky; top: 88px;
    }
    .avatar-circle {
      width: 88px; height: 88px; border-radius: 50%;
      background: var(--ink); color: var(--bg);
      display: flex; align-items: center; justify-content: center;
      font-size: 28px; font-family: var(--serif); font-style: italic;
      margin-bottom: 16px; flex-shrink: 0;
    }
    .identity-name { font-family: var(--serif); font-size: 20px; letter-spacing: -0.01em; margin-bottom: 4px; }
    .identity-email { word-break: break-all; }

    .profile-form-area { display: flex; flex-direction: column; gap: 0; }
    .form-section { padding: 20px 0; border-bottom: 1px solid var(--line); display: flex; flex-direction: column; gap: 12px; }
    .form-section:last-of-type { border-bottom: none; }
    .form-section-label { font-family: var(--mono); font-size: 10px; text-transform: uppercase; letter-spacing: 0.12em; color: var(--ink-4); margin-bottom: 4px; }
    .form-row-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }

    .notif-row { display: flex; gap: 20px; flex-wrap: wrap; }
    .toggle-label { display: flex; align-items: center; gap: 8px; font-size: 13px; cursor: pointer; color: var(--ink-2); }
    .toggle-label input { cursor: pointer; }
  `]
})
export class ProfileComponent implements OnInit {
  private fb = inject(FormBuilder);
  private apollo = inject(Apollo);
  private auth = inject(AuthService);

  user = signal<User | null>(null);
  loading = signal(false);
  saving = signal(false);
  success = signal(false);
  error = signal('');

  form = this.fb.group({
    firstName: [''], lastName: [''], email: [''],
    phoneNumber: [''], country: [''], preferredLanguage: [''],
    preferredCurrency: [''], timezone: [''],
    emailNotifications: [false], smsNotifications: [false],
  });

  ngOnInit() {
    this.loading.set(true);
    this.apollo.query<{ me: User }>({ query: ME }).subscribe({
      next: r => {
        this.loading.set(false);
        const u = r.data.me;
        this.user.set(u);
        this.form.patchValue({
          firstName: u.firstName ?? '', lastName: u.lastName ?? '',
          email: u.email, phoneNumber: u.phoneNumber ?? '', country: u.country ?? '',
          preferredLanguage: u.preferredLanguage ?? '', preferredCurrency: u.preferredCurrency ?? '',
          timezone: u.timezone ?? '',
          emailNotifications: u.emailNotifications ?? false,
          smsNotifications: u.smsNotifications ?? false,
        });
      },
      error: err => { this.loading.set(false); this.error.set(err.message || 'Failed to load'); }
    });
  }

  save() {
    this.saving.set(true);
    this.success.set(false);
    this.error.set('');
    const v = this.form.value;
    this.apollo.mutate<{ updateProfile: User }>({
      mutation: UPDATE_PROFILE,
      variables: { input: v }
    }).subscribe({
      next: r => {
        this.saving.set(false);
        this.success.set(true);
        this.user.set(r.data!.updateProfile);
      },
      error: err => { this.saving.set(false); this.error.set(err.message || 'Update failed'); }
    });
  }

  initials() {
    const u = this.user();
    if (!u) return '?';
    if (u.firstName && u.lastName) return (u.firstName[0] + u.lastName[0]).toUpperCase();
    return u.username?.[0]?.toUpperCase() ?? '?';
  }
}
