import { Injectable, signal, computed } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { Router } from '@angular/router';
import { map, tap } from 'rxjs/operators';
import { LOGIN, REGISTER, REFRESH_TOKEN, LOGOUT } from '../shared/graphql/documents';
import { AuthPayload, User } from '../shared/models/models';

const ACCESS_TOKEN_KEY = 'bkg_access_token';
const REFRESH_TOKEN_KEY = 'bkg_refresh_token';
const USER_KEY = 'bkg_user';

@Injectable({ providedIn: 'root' })
export class AuthService {

  private _user = signal<User | null>(this.loadUser());
  private _token = signal<string | null>(localStorage.getItem(ACCESS_TOKEN_KEY));

  readonly user = this._user.asReadonly();
  readonly token = this._token.asReadonly();
  readonly isAuthenticated = computed(() => !!this._token());
  readonly isOrganizer = computed(() => this._user()?.roles.includes('employee') ?? false);
  readonly isCustomer = computed(() => this._user()?.roles.includes('customer') ?? false);

  constructor(private apollo: Apollo, private router: Router) {}

  login(username: string, password: string) {
    return this.apollo.mutate<{ login: AuthPayload }>({
      mutation: LOGIN,
      variables: { input: { username, password } },
    }).pipe(
      map(r => r.data!.login),
      tap(payload => this.storeSession(payload))
    );
  }

  register(input: {
    email: string; password: string; firstName: string; lastName: string;
    phoneNumber?: string; country?: string; preferredLanguage?: string; role?: string;
  }) {
    return this.apollo.mutate<{ register: AuthPayload }>({
      mutation: REGISTER,
      variables: { input },
    }).pipe(
      map(r => r.data!.register),
      tap(payload => { if (payload.accessToken) this.storeSession(payload); })
    );
  }

  refreshAccessToken() {
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (!refreshToken) return;
    this.apollo.mutate<{ refreshToken: AuthPayload }>({
      mutation: REFRESH_TOKEN,
      variables: { refreshToken },
    }).pipe(map(r => r.data!.refreshToken)).subscribe({
      next: payload => this.storeSession(payload),
      error: () => this.logout(),
    });
  }

  logout() {
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (refreshToken) {
      this.apollo.mutate({ mutation: LOGOUT, variables: { refreshToken } }).subscribe();
    }
    this.clearSession();
    this.router.navigate(['/auth/login']);
  }

  getToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  private storeSession(payload: AuthPayload) {
    localStorage.setItem(ACCESS_TOKEN_KEY, payload.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, payload.refreshToken);
    if (payload.user) {
      localStorage.setItem(USER_KEY, JSON.stringify(payload.user));
      this._user.set(payload.user);
    }
    this._token.set(payload.accessToken);
  }

  private clearSession() {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this._user.set(null);
    this._token.set(null);
    this.apollo.client.resetStore();
  }

  private loadUser(): User | null {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  }
}
