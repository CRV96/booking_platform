import { Injectable, signal, computed } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { Router } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { catchError, finalize, map, shareReplay, tap } from 'rxjs/operators';
import { LOGIN, REGISTER, REFRESH_TOKEN, LOGOUT } from '../shared/graphql/documents';
import { AuthPayload, User } from '../shared/models/models';

/** Refresh the access token this many ms before it actually expires, to avoid racing expiry. */
const TOKEN_EXPIRY_SKEW_MS = 30_000;

const ACCESS_TOKEN_KEY = 'bkg_access_token';
const REFRESH_TOKEN_KEY = 'bkg_refresh_token';
const USER_KEY = 'bkg_user';

@Injectable({ providedIn: 'root' })
export class AuthService {

  private _user = signal<User | null>(this.loadUser());
  private _token = signal<string | null>(localStorage.getItem(ACCESS_TOKEN_KEY));

  /** In-flight refresh, shared so concurrent requests trigger only one refresh call. */
  private refresh$: Observable<AuthPayload> | null = null;

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

  /**
   * Returns the current access token, refreshing it first if it is expired (or about
   * to expire). Concurrent callers share a single in-flight refresh. Emits {@code null}
   * if no valid session can be produced (e.g. refresh token also expired → logout).
   */
  getFreshToken(): Observable<string | null> {
    const token = this.getToken();
    if (token && !this.isTokenExpired(token)) {
      return of(token);
    }
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (!refreshToken) {
      return of(null);
    }
    return this.refreshAccessToken().pipe(
      map(payload => payload.accessToken),
      catchError(() => of(null)),
    );
  }

  /** Refreshes the session via the refresh token. Shared while in flight; logs out on failure. */
  refreshAccessToken(): Observable<AuthPayload> {
    if (this.refresh$) return this.refresh$;

    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (!refreshToken) {
      this.logout();
      return throwError(() => new Error('No refresh token available'));
    }

    this.refresh$ = this.apollo.mutate<{ refreshToken: AuthPayload }>({
      mutation: REFRESH_TOKEN,
      variables: { refreshToken },
    }).pipe(
      map(r => r.data!.refreshToken),
      tap(payload => this.storeSession(payload)),
      catchError(err => { this.logout(); return throwError(() => err); }),
      finalize(() => { this.refresh$ = null; }),
      shareReplay(1),
    );
    return this.refresh$;
  }

  /** Decodes the JWT {@code exp} claim and reports whether it is within the skew window. */
  private isTokenExpired(token: string): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      if (!payload.exp) return true;
      return payload.exp * 1000 <= Date.now() + TOKEN_EXPIRY_SKEW_MS;
    } catch {
      return true;
    }
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
