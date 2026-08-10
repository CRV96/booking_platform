import { ApplicationConfig, importProvidersFrom, inject } from '@angular/core';
import { provideRouter, withRouterConfig } from '@angular/router';
import { provideHttpClient, withInterceptors, HttpHandlerFn, HttpRequest } from '@angular/common/http';
import { APOLLO_OPTIONS, ApolloModule } from 'apollo-angular';
import { HttpLink } from 'apollo-angular/http';
import { InMemoryCache } from '@apollo/client/core';
import { switchMap } from 'rxjs/operators';
import { routes } from './app.routes';
import { AuthService } from './core/auth.service';

/** Auth mutations must not trigger a token refresh — they run without (or establish) a session. */
const AUTH_OPERATIONS = new Set(['Login', 'Register', 'RefreshToken', 'Logout']);

function authInterceptor(req: HttpRequest<unknown>, next: HttpHandlerFn) {
  const isGraphql = req.url.includes('/graphql');
  const operationName = (req.body as { operationName?: string } | null)?.operationName;

  // Auth mutations (and non-GraphQL calls) are sent WITHOUT an Authorization header.
  // They are public and self-authenticating (refresh/logout use the refresh token in the
  // body). Attaching a stale access token here would make the gateway's OAuth2 resource
  // server reject them with 401 before they run — which would break the refresh itself.
  if (!isGraphql || (operationName && AUTH_OPERATIONS.has(operationName))) {
    return next(req);
  }

  // Everything else: ensure the access token is fresh (refresh if expired) before sending.
  const auth = inject(AuthService);
  return auth.getFreshToken().pipe(
    switchMap(token => {
      const authed = token
        ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
        : req;
      return next(authed);
    }),
  );
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes, withRouterConfig({ onSameUrlNavigation: 'reload' })),
    provideHttpClient(withInterceptors([authInterceptor])),
    importProvidersFrom(ApolloModule),
    {
      provide: APOLLO_OPTIONS,
      useFactory: (httpLink: HttpLink) => ({
        cache: new InMemoryCache(),
        link: httpLink.create({ uri: '/graphql' }),
        defaultOptions: {
          watchQuery: { fetchPolicy: 'network-only' as const },
          query: { fetchPolicy: 'network-only' as const },
        },
      }),
      deps: [HttpLink],
    },
  ],
};
