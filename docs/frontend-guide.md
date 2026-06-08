# Frontend Guide — CRV Bookings Angular App

> A complete walkthrough of the `frontend/` application for someone new to Angular.
> After reading this you will understand how the app is structured, how every piece fits together,
> and where to go when you want to make a change.

---

## Table of Contents

1. [Quick Start](#1-quick-start)
2. [What Is Angular?](#2-what-is-angular)
3. [Project Layout](#3-project-layout)
4. [How Angular Boots Up](#4-how-angular-boots-up)
5. [Routing — How Pages Work](#5-routing--how-pages-work)
6. [Components — The Building Blocks](#6-components--the-building-blocks)
7. [Signals — Reactive State](#7-signals--reactive-state)
8. [Forms](#8-forms)
9. [GraphQL with Apollo](#9-graphql-with-apollo)
10. [Authentication Flow](#10-authentication-flow)
11. [Guards — Protecting Routes](#11-guards--protecting-routes)
12. [Design System](#12-design-system)
13. [Shared Components](#13-shared-components)
14. [File-by-File Reference](#14-file-by-file-reference)
15. [Common Tweaks Cookbook](#15-common-tweaks-cookbook)

---

## 1. Quick Start

```bash
cd frontend
npm install          # install dependencies (only needed once)
npm start            # dev server at http://localhost:4200
```

`npm start` runs `ng serve --proxy-config proxy.conf.json`.
The proxy forwards all `/graphql` requests to `http://localhost:8080` so Angular's
dev server and the Spring Boot gateway never conflict on ports.

---

## 2. What Is Angular?

Angular is a framework for building web applications from reusable **components**.
Each component controls a piece of the screen — a button, a form, an entire page.

Core ideas you will see throughout this codebase:

| Concept | One-liner |
|---------|-----------|
| **Component** | A TypeScript class + HTML template that renders a piece of UI |
| **Signal** | A reactive variable — when it changes, Angular re-renders only the affected HTML |
| **Route** | A URL path mapped to a component |
| **Guard** | A function that runs before a route loads — can block or redirect |
| **Pipe** | A template helper that transforms a value for display (`date`, `currency`, …) |
| **Service** | A singleton class that holds shared logic (auth, HTTP, etc.) |

This app uses **standalone components** (Angular 17+). Each component declares exactly
what it needs in its own `imports: []` array instead of sharing a global module.
You will see this pattern in every component file.

---

## 3. Project Layout

```
frontend/
├── angular.json            ← Angular CLI config (build, serve, output paths)
├── tsconfig.json           ← TypeScript compiler config (skipLibCheck: true for apollo-angular)
├── proxy.conf.json         ← Dev-server proxy: /graphql → localhost:8080
├── package.json            ← npm dependencies and scripts
└── src/
    ├── main.ts             ← Entry point — bootstraps the app
    ├── index.html          ← Single HTML page; loads Google Fonts
    ├── styles.css          ← Global design system (variables, utility classes, base styles)
    └── app/
        ├── app.component.ts      ← Root shell: navbar + router-outlet + footer
        ├── app.config.ts         ← Providers: Apollo, router, HTTP interceptor
        ├── app.routes.ts         ← All route definitions
        │
        ├── core/
        │   ├── auth.service.ts   ← JWT storage, login/logout/register, role signals
        │   └── auth.guard.ts     ← authGuard + organizerGuard
        │
        ├── shared/
        │   ├── navbar.component.ts          ← Sticky frosted-glass header
        │   ├── footer.component.ts          ← Bottom bar with cities + copyright
        │   ├── event-art.component.ts       ← Deterministic SVG art generator
        │   ├── models/models.ts             ← TypeScript interfaces mirroring GraphQL schema
        │   └── graphql/documents.ts         ← All GraphQL queries and mutations
        │
        └── features/
            ├── auth/
            │   ├── login.component.ts       ← Sign-in page (Customer / Organizer tabs)
            │   └── register.component.ts    ← Sign-up page (role tabs)
            ├── customer/
            │   └── customer-home.component.ts  ← Dashboard shown after customer login
            ├── events/
            │   ├── event-list.component.ts  ← Public event browser with filters
            │   └── event-detail.component.ts   ← Single event + booking sidebar
            ├── bookings/
            │   └── my-bookings.component.ts ← Customer's booking history
            ├── tickets/
            │   └── my-tickets.component.ts  ← Customer's split-ticket cards with QR
            ├── profile/
            │   └── profile.component.ts     ← Edit account details
            └── organizer/
                ├── organizer-dashboard.component.ts  ← Stats grid + event management
                ├── event-form.component.ts            ← Create / Edit event form
                └── ticket-scanner.component.ts        ← Look up & validate tickets
```

**Rule of thumb:** every `.component.ts` file is one page or UI element.
When you want to change something visual, find the component for that page.

---

## 4. How Angular Boots Up

### `src/main.ts`

```ts
bootstrapApplication(AppComponent, appConfig)
```

This is the very first line that runs. It tells Angular:
- Start with `AppComponent` as the root
- Use the configuration defined in `appConfig`

### `src/app/app.config.ts`

This is where the "plumbing" lives — things every part of the app needs.

```ts
export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
    importProvidersFrom(ApolloModule),
    {
      provide: APOLLO_OPTIONS,
      useFactory: (httpLink: HttpLink) => ({
        cache: new InMemoryCache(),
        link: httpLink.create({ uri: '/graphql' })
      }),
      deps: [HttpLink]
    }
  ],
};
```

**The auth interceptor** is defined inline in this file:

```ts
function authInterceptor(req, next) {
  const token = localStorage.getItem('bkg_access_token');
  if (token && req.url.includes('/graphql')) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req);
}
```

Every HTTP request to `/graphql` automatically gets `Authorization: Bearer <token>`
attached. You never have to add the header manually in a component.

> **Why `importProvidersFrom(ApolloModule)` instead of `provideApollo`?**
> apollo-angular v6 does not export a `provideApollo` function. The token-based
> approach above is the correct way for this version.

### `src/app/app.component.ts`

The root shell. Renders navbar, the active page, and the footer:

```ts
template: `
  <div class="app">
    <app-navbar />
    <main class="main">
      <router-outlet />   ← active page component is injected here
    </main>
    <app-footer />
  </div>
`
```

`<router-outlet>` is a placeholder. When the URL changes, Angular swaps the
component inside it without refreshing the browser.

### `src/index.html`

Loads three Google Fonts that define the visual identity:

| Font | Variable | Used for |
|------|----------|---------|
| Instrument Serif (italic) | `--serif` | Headings, event titles, prices, logo |
| Inter | `--sans` | All body text and UI labels |
| JetBrains Mono | `--mono` | Kickers, badges, metadata, logo subtitle |

---

## 5. Routing — How Pages Work

### `src/app/app.routes.ts`

Every URL in the app is listed here:

```ts
export const routes: Routes = [
  { path: '',           redirectTo: 'events', pathMatch: 'full' },
  { path: 'events',     loadComponent: () => import('./features/events/event-list.component')... },
  { path: 'events/:id', loadComponent: () => import('./features/events/event-detail.component')... },
  { path: 'home',       canActivate: [authGuard], loadComponent: ... },
  { path: 'bookings',   canActivate: [authGuard], loadComponent: ... },
  { path: 'tickets',    canActivate: [authGuard], loadComponent: ... },
  { path: 'profile',    canActivate: [authGuard], loadComponent: ... },
  { path: 'organizer',  canActivate: [organizerGuard], children: [
    { path: '',       loadComponent: ... },   // /organizer
    { path: 'tickets', loadComponent: ... },  // /organizer/tickets
    { path: 'events/new',     loadComponent: ... },
    { path: 'events/:id/edit', loadComponent: ... },
  ]},
  { path: 'auth/login',    loadComponent: ... },
  { path: 'auth/register', loadComponent: ... },
];
```

**Key concepts:**

- **`loadComponent`** — lazy loading. The component's bundle is only downloaded when
  the user first visits that URL, keeping the initial load fast.
- **`canActivate`** — runs a guard before loading the component. If it returns `false`
  or a redirect URL, navigation is blocked.
- **`children`** — nested routes. `/organizer/events/new` lives under the `/organizer`
  parent which already has `organizerGuard` applied, so child routes inherit the guard.
- **`:id`** — a dynamic segment. Inside a component you read it with
  `this.route.snapshot.paramMap.get('id')`.

### How to add a new page

1. Create `src/app/features/your-feature/your-page.component.ts`
2. Add an entry to `app.routes.ts`:
   ```ts
   {
     path: 'your-path',
     loadComponent: () => import('./features/your-feature/your-page.component')
                           .then(m => m.YourPageComponent)
   }
   ```
3. Add a link in the navbar or another component: `<a routerLink="/your-path">Go there</a>`

---

## 6. Components — The Building Blocks

Every component file follows the same structure:

```ts
@Component({
  selector: 'app-my-component',   // HTML tag name when embedding in another component
  standalone: true,
  imports: [RouterLink, DatePipe, FormsModule, ...],  // what this template uses
  template: `...`,                 // the HTML
  styles: [`...`]                  // CSS scoped to this component only
})
export class MyComponent {
  // TypeScript logic here
}
```

### Template syntax quick-reference

```html
<!-- Display a variable -->
{{ user.firstName }}

<!-- Property binding — set an HTML attribute from a variable -->
<input [value]="myVariable">
<button [disabled]="loading()">...</button>

<!-- Event binding — call a method on an event -->
<button (click)="submit()">Save</button>
<input (keyup.enter)="search()">

<!-- Two-way binding (FormsModule) — sync input ↔ variable -->
<input [(ngModel)]="filters.query">

<!-- Conditional rendering -->
@if (loading()) {
  <div class="loading-center"><div class="spinner spinner-lg"></div></div>
} @else if (events().length === 0) {
  <div class="empty-state">...</div>
} @else {
  <!-- content -->
}

<!-- Loops -->
@for (ev of events(); track ev.id) {
  <div>{{ ev.title }}</div>
}

<!-- Router link (no page reload) -->
<a routerLink="/events">Browse Events</a>
<a [routerLink]="['/events', ev.id]">{{ ev.title }}</a>

<!-- Mark active nav link -->
<a routerLink="/bookings" routerLinkActive="hdr-nav-active">My Bookings</a>

<!-- Pipe — transform a value for display -->
{{ event.dateTime | date:'d MMM y' }}

<!-- Inject raw HTML (SVG) safely -->
<div [innerHTML]="svgString"></div>
```

### Dependency injection with `inject()`

Instead of writing a constructor, Angular 17 lets you use `inject()`:

```ts
export class MyComponent {
  private apollo  = inject(Apollo);          // GraphQL client
  private auth    = inject(AuthService);     // auth state + actions
  private router  = inject(Router);          // programmatic navigation
  private route   = inject(ActivatedRoute);  // current URL params
  private sanitizer = inject(DomSanitizer);  // for safe HTML injection
}
```

---

## 7. Signals — Reactive State

Signals are how this app manages component state. A signal holds a value and
automatically updates the template when it changes.

```ts
// Declare
loading = signal(false);
events  = signal<Event[]>([]);
error   = signal('');
page    = signal(0);

// Read (always called as a function)
this.loading()       // → false
this.events().length // → 0

// Write
this.loading.set(true);
this.error.set('Something went wrong');

// Update based on previous value
this.page.update(p => p + 1);
this.events.update(list => list.map(e => e.id === id ? { ...e, status: 'CANCELLED' } : e));

// Computed signal — derived from other signals, auto-updates
readonly isLoggedIn = computed(() => !!this.token());
```

In the template, signals are called the same way:

```html
@if (loading()) { <div class="spinner spinner-lg"></div> }
<p>Page {{ page() + 1 }}</p>
```

**Why signals instead of regular variables?**
If you used `let loading = false` and changed it, Angular would not know to
re-render the template. Signals tell Angular exactly when to update.

---

## 8. Forms

Two approaches are used in this app:

### Reactive Forms (`ReactiveFormsModule`)

Used on login, register, profile, and event-form. Best for complex validation
and dynamic fields.

```ts
// In the component class
private fb = inject(FormBuilder);

form = this.fb.group({
  email:    ['', [Validators.required, Validators.email]],
  password: ['', [Validators.required, Validators.minLength(8)]],
});

// Read values
const { email, password } = this.form.value;

// Check validity before submitting
if (this.form.invalid) { this.form.markAllAsTouched(); return; }
```

```html
<!-- In the template -->
<form [formGroup]="form" (ngSubmit)="submit()">
  <input formControlName="email" class="inp">
  <input formControlName="password" type="password" class="inp">
  <button type="submit" [disabled]="saving()">Submit</button>
</form>
```

**`FormArray`** is used in `event-form.component.ts` for dynamic seat categories:

```ts
get seats() { return this.form.get('seatCategories') as FormArray; }

addSeat() {
  this.seats.push(this.fb.group({
    name: ['', Validators.required],
    price: [null, Validators.required],
    currency: ['EUR'],
    totalSeats: [null, Validators.required],
  }));
}

removeSeat(i: number) { this.seats.removeAt(i); }
```

### Template-driven Forms (`FormsModule`)

Used for simple search filters with `[(ngModel)]`:

```ts
// In the component class
filters = { query: '', category: '' };
```

```html
<input [(ngModel)]="filters.query" placeholder="Search events…">
<select [(ngModel)]="filters.category">...</select>
```

---

## 9. GraphQL with Apollo

All API calls go through Apollo Angular. The endpoint is `/graphql` (proxied to
`http://localhost:8080/graphql` on the dev server).

### Where the queries live

`src/app/shared/graphql/documents.ts` contains every query and mutation as
tagged template literals using `gql`:

```ts
export const GET_EVENTS = gql`
  query GetEvents($query: String, $category: String, $page: Int, $pageSize: Int) {
    events(query: $query, category: $category, page: $page, pageSize: $pageSize) {
      events { id title category dateTime status venue { city } seatCategories { price } }
      totalCount totalPages
    }
  }
`;
```

### Executing a query in a component

```ts
private apollo = inject(Apollo);
events = signal<Event[]>([]);
loading = signal(false);
error = signal('');

load() {
  this.loading.set(true);
  this.apollo.query<{ events: EventConnection }>({
    query: GET_EVENTS,
    variables: { page: 0, pageSize: 12 }
  }).subscribe({
    next: r  => { this.loading.set(false); this.events.set(r.data.events.events); },
    error: err => { this.loading.set(false); this.error.set(err.message || 'Failed'); }
  });
}
```

- `apollo.query()` — one-time fetch (reads)
- `apollo.mutate()` — writes (create, update, cancel, etc.)
- `variables` — maps to the `$variables` declared in the GraphQL document

### Executing a mutation

```ts
this.apollo.mutate<{ createBooking: Booking }>({
  mutation: CREATE_BOOKING,
  variables: {
    input: {
      eventId: ev.id,
      seatCategory: 'VIP',
      quantity: 2,
      idempotencyKey: crypto.randomUUID(),
    }
  }
}).subscribe({
  next: r  => this.router.navigate(['/bookings']),
  error: err => this.error.set(err.message?.replace('ApolloError: ', '') || 'Failed')
});
```

### Where error messages come from

Apollo wraps server errors. The backend returns a gRPC status which the gateway
converts to a GraphQL error message. Strip the prefix if needed:

```ts
error: err => this.error.set(err.message?.replace('ApolloError: ', '') || 'Something went wrong')
```

---

## 10. Authentication Flow

### Storage

Three keys in `localStorage`:

| Key | Value |
|-----|-------|
| `bkg_access_token` | JWT access token (short-lived, ~5 min) |
| `bkg_refresh_token` | Refresh token (longer-lived) |
| `bkg_user` | JSON-serialised `User` object |

### `auth.service.ts`

The central authority for everything auth-related. Exposes read-only signals:

```ts
readonly user            = signal<User | null>(...);  // currently logged-in user
readonly token           = signal<string | null>(...);
readonly isAuthenticated = computed(() => !!this._token());
readonly isOrganizer     = computed(() => this._user()?.roles.includes('employee') ?? false);
readonly isCustomer      = computed(() => this._user()?.roles.includes('customer') ?? false);
```

**Login flow:**
1. Component calls `auth.login(username, password)` — returns an Observable
2. Service sends `LOGIN` mutation to GraphQL
3. On success, `storeSession()` saves tokens + user to `localStorage` and updates signals
4. The login component subscribes and redirects:
   - `employee` role → `/organizer`
   - anything else → `/home`

**Logout flow:**
1. Sends `LOGOUT` mutation to invalidate the refresh token on Keycloak
2. Calls `clearSession()` — wipes `localStorage`, resets signals, resets Apollo cache
3. Redirects to `/auth/login`

**Reading the current user anywhere in the app:**

```ts
private auth = inject(AuthService);

// In a template:
{{ auth.user()?.firstName || auth.user()?.username }}

// In a method:
const userId = this.auth.user()!.id;

// Checking role:
if (this.auth.isOrganizer()) { ... }
```

### Roles (from Keycloak)

The JWT contains `realm_access.roles`. The gateway decodes it and attaches the
roles array to the GraphQL `User.roles` field.

| Keycloak role | Meaning in the frontend |
|---------------|------------------------|
| `customer` | Regular user — browse events, book tickets, view their tickets |
| `employee` | Organizer — create/publish/cancel events, validate tickets at the door |

---

## 11. Guards — Protecting Routes

Defined in `src/app/core/auth.guard.ts`.

```ts
// Blocks unauthenticated users
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.isAuthenticated()) return true;
  return inject(Router).createUrlTree(['/auth/login']);
};

// Blocks non-organizer users
export const organizerGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.isAuthenticated() && auth.isOrganizer()) return true;
  if (!auth.isAuthenticated()) return inject(Router).createUrlTree(['/auth/login']);
  return inject(Router).createUrlTree(['/events']); // logged in but wrong role
};
```

Applied in `app.routes.ts` via `canActivate`:

```ts
{ path: 'home',      canActivate: [authGuard],      loadComponent: ... },
{ path: 'organizer', canActivate: [organizerGuard],  children: [...] },
```

---

## 12. Design System

The entire visual identity is defined in `src/styles.css` as CSS custom properties.
All components reference these variables so the look is consistent and easy to change globally.

### Color palette

```css
:root {
  /* Backgrounds */
  --bg:       #fafaf8;   /* warm off-white — page background */
  --bg-sunk:  #f2f1ec;   /* slightly darker — sunken sections, active pills */
  --bg-card:  #ffffff;   /* card surfaces */

  /* Ink (text) — 4 levels of hierarchy */
  --ink:   #14130f;      /* primary text */
  --ink-2: #3b3a34;      /* secondary text */
  --ink-3: #6b6a62;      /* muted labels */
  --ink-4: #a3a198;      /* very muted, placeholder-level */

  /* Lines / borders */
  --line:   #e6e4dc;     /* default border */
  --line-2: #d3d0c4;     /* slightly stronger border */

  /* Accent — amber / golden, oklch color space */
  --accent:      oklch(0.72 0.14 70);   /* accent fill (buttons, highlights) */
  --accent-ink:  oklch(0.38 0.10 70);   /* dark amber for text on light surfaces */
  --accent-soft: oklch(0.94 0.04 80);   /* very light amber for badge backgrounds */

  /* Status */
  --success: oklch(0.62 0.12 150);   /* green */
  --danger:  oklch(0.58 0.17 25);    /* red */

  /* Shape */
  --radius:    4px;
  --radius-lg: 8px;

  /* Typography */
  --serif: "Instrument Serif", serif;
  --sans:  "Inter", system-ui, sans-serif;
  --mono:  "JetBrains Mono", monospace;
}
```

### Typography hierarchy

| Use case | Font | Example class / usage |
|----------|------|----------------------|
| Page headings | `var(--serif)` 48px | `.section-head h2` |
| Event titles on cards | `var(--serif)` 24px | `.ev-card-title` |
| Prices | `var(--serif)` 20px | `.ev-card-price` |
| Section kicker labels | `var(--mono)` 11px uppercase | `.section-head .kicker` |
| Badges, metadata | `var(--mono)` 11px | `.badge`, `.ev-card-meta` |
| Body / UI | `var(--sans)` 14px | default |

Italic `<em>` inside a serif heading renders in the amber accent colour — used throughout
for visual emphasis ("Upcoming **events**", "My **Tickets**", etc.):

```css
.section-head h2 em { font-style: italic; color: var(--accent-ink); }
```

### Buttons

```html
<button class="btn btn-primary">Black fill (primary action)</button>
<button class="btn btn-secondary">White with border</button>
<button class="btn btn-ghost">Text only, hover reveals background</button>
<button class="btn btn-accent">Amber fill</button>
<button class="btn btn-danger">Red outline (destructive)</button>

<!-- Size modifiers -->
<button class="btn btn-primary btn-sm">Small</button>
<button class="btn btn-primary btn-lg">Large</button>
<button class="btn btn-primary btn-block">Full width</button>
```

### Form inputs

All inputs use the `.inp` class:

```html
<div class="field">
  <label>Title <span style="color:var(--danger)">*</span></label>
  <input class="inp" formControlName="title" placeholder="Rock Fest 2026">
</div>
```

`.field` provides the label + input column layout with a 6px gap.
`.inp:focus` sets the border to `var(--ink)` for a clear focus ring.

### Badges

Monospace, uppercase, small. Used for statuses everywhere:

```html
<span class="badge">Default (grey)</span>
<span class="badge badge-success">VALID / CONFIRMED</span>
<span class="badge badge-accent">PENDING</span>
<span class="badge badge-live">PUBLISHED</span>
<span class="badge badge-draft">DRAFT</span>
<span class="badge badge-danger">CANCELLED</span>
```

### Alerts

```html
<div class="alert alert-error">{{ error() }}</div>
<div class="alert alert-success">Saved successfully.</div>
```

### Section heading pattern

Used at the top of every page:

```html
<div class="section-head">
  <div class="kicker">Programme</div>          <!-- mono uppercase label -->
  <h2>Upcoming <em>events</em></h2>            <!-- serif; em = amber italic -->
  <p class="sub">Curated across six cities.</p>
</div>
```

### Layout helpers

```html
<div class="container">...</div>         <!-- max-width 1200px, centred -->
<div class="page">...</div>              <!-- padding-top: 48px, padding-bottom: 60px -->
<div class="row gap-12">...</div>        <!-- flex row with 12px gap -->
<div class="stack">...</div>             <!-- flex column -->
<hr class="divider">                     <!-- 1px line, var(--line) colour -->
```

### Animations

Two entry animations available:

```html
<div class="fade-up">Fades in + slides up 8px on mount</div>
<div class="fade-in">Fades in on mount</div>
```

Event cards in the grid use `fade-up` automatically.

---

## 13. Shared Components

### `EventArtComponent` — `shared/event-art.component.ts`

Generates a unique piece of abstract SVG art for every event. No images needed —
art is computed purely from a numeric seed derived from the event's ID.

**Usage:**
```html
<app-event-art [seed]="artSeed(ev)" [title]="ev.title" ratio="16/10" />
```

**Inputs:**

| Input | Type | Default | Purpose |
|-------|------|---------|---------|
| `seed` | `number` | `1` | Drives which palette + renderer is selected |
| `title` | `string` | `''` | Used by the `typeArt` renderer to show the first letter |
| `ratio` | `string` | `'16/10'` | CSS `aspect-ratio` of the art container (e.g. `'1/1'`, `'16/7'`) |

**How the seed is computed in every component that uses it:**
```ts
artSeed(ev: Event): number {
  return ev.id.split('').reduce((a, c) => a + c.charCodeAt(0), 0) % 100;
}
```
This turns the event's UUID into a stable number 0–99. The same event always
gets the same art.

**Under the hood:**

The component picks from 8 colour palettes and 6 renderer functions:

| Palette index | Background | Accent |
|---|---|---|
| 0 | Dark charcoal | Amber |
| 1 | Near-black | Sage green |
| 2 | Warm paper | Terracotta |
| 3 | Deep teal | Gold |
| 4 | Tan | Rust brown |
| 5 | Dark navy | Violet |
| 6 | Warm grey | Forest green |
| 7 | Dark mahogany | Salmon |

| Renderer | What it draws |
|----------|---------------|
| `rings` | Concentric circles with an accent dot at the centre |
| `stripes` | Vertical strips of varying width, one highlighted in accent |
| `grid` | Dot matrix; some cells lit in accent colour |
| `arc` | Two flowing quadratic curves + a ghost circle |
| `typeArt` | Giant italic serif letter (first letter of event title) with an accent bar |
| `noise` | 70 random dots, scattered with occasional accent-coloured ones |

The SVG is injected via `DomSanitizer.bypassSecurityTrustHtml()`. This is safe
because the SVG is 100% generated from our own code — no user input ever reaches the SVG string.

---

### `NavbarComponent` — `shared/navbar.component.ts`

Sticky frosted-glass header that adapts to the current user's role.

**Logo:**
```
CRV Bookings   ← Instrument Serif, 24px
Est. 2026      ← JetBrains Mono, 9px, muted uppercase
```

**Nav links — conditional on role:**

| State | Links shown |
|-------|-------------|
| Not logged in | Events |
| Logged in as customer | Events · My Dashboard · My Bookings · My Tickets |
| Logged in as organizer | Events · Manage Events · Scan Tickets |

**Right-side user area:**
- Logged out: Sign in (ghost) + Sign up (primary) buttons
- Logged in: avatar circle with initials + display name + "Organizer" amber badge (organizers only) + Sign out button

---

### `FooterComponent` — `shared/footer.component.ts`

Simple monospace footer with copyright and a list of cities served.

---

### Ticket QR Code (`qrSvg` function)

Both `my-tickets.component.ts` and `ticket-scanner.component.ts` contain an
identical `qrSvg(seed: string)` function. It generates a fake but visually
convincing QR code pattern from the ticket number's character codes.

**Structure:**
- 11×11 cell grid — each cell on/off determined by `charCode × row × col % 7 < 3`
- Three corner markers (top-left, top-right, bottom-left) — standard QR finder pattern
- Dimmed to 25% opacity (`qr-invalid` class) for non-VALID tickets

```ts
qr(ticketNumber: string): string {
  return qrSvg(ticketNumber || 'X');
}
```

```html
<div class="qr-box" [innerHTML]="qr(t.ticketNumber)"></div>
```

---

## 14. File-by-File Reference

### `src/app/app.component.ts`
Root shell. Renders `<app-navbar>`, `<router-outlet>`, `<app-footer>` inside a
`.app` flex column. Rarely needs changes.

### `src/app/app.config.ts`
Bootstrap configuration. Touch this to:
- Change the GraphQL endpoint URI
- Add a new global HTTP interceptor
- Configure Apollo cache behaviour (e.g., add `typePolicies` for pagination)

### `src/app/app.routes.ts`
Route table. Touch this to:
- Add a new page
- Apply or remove a route guard
- Change a URL path

### `src/app/core/auth.service.ts`
All authentication logic. Touch this to:
- Change where tokens are stored
- Wire in automatic token refresh (call `refreshAccessToken()` from the interceptor
  when a 401 is received)
- Add a new role signal (e.g., `isAdmin`)

### `src/app/core/auth.guard.ts`
Route protection. Touch this to:
- Add a new guard (e.g., `adminGuard` for a future admin area)
- Change redirect targets when access is denied

### `src/app/shared/navbar.component.ts`
Top navigation. Touch this to:
- Add or remove nav links per role
- Change the brand name or logo subtitle
- Add a mobile hamburger menu

### `src/app/shared/footer.component.ts`
Bottom bar. Touch this to change the cities listed or the copyright text.

### `src/app/shared/event-art.component.ts`
SVG art generator. Touch this to:
- Add a new palette (append to the `PALETTES` array)
- Add a new renderer (implement the `Renderer` function type and add to `RENDERERS`)
- Change the default aspect ratio

### `src/app/shared/models/models.ts`
TypeScript interfaces mirroring the GraphQL schema. Touch this when:
- A new field is added to the schema (add it here so TypeScript knows about it)
- A new GraphQL type is introduced

### `src/app/shared/graphql/documents.ts`
Every GraphQL query and mutation. Touch this when:
- You need to fetch additional fields (add them to the relevant `gql` block)
- You add a new GraphQL operation

### `src/app/features/auth/login.component.ts`
Login page with Customer / Organizer tab strip. Touch this to:
- Change post-login redirect URLs
- Adjust the tab labels or UI

### `src/app/features/auth/register.component.ts`
Registration form with role tabs. Touch this to:
- Add or remove fields from the signup form
- Change which fields are required per role

### `src/app/features/customer/customer-home.component.ts`
Dashboard shown after customer login. Shows recent bookings + tickets panels
with a "Browse Events" CTA band at the bottom. Touch this to:
- Add more panels (e.g., upcoming events the user bookmarked)
- Change how many items are previewed (currently 5 bookings, 5 tickets)

### `src/app/features/events/event-list.component.ts`
Public event browser. Category pill filters + search input, 3-column responsive grid of `ev-card` elements with `EventArtComponent`, pagination. Touch this to:
- Add more filter fields (e.g., city, date range)
- Change page size (currently 12)

### `src/app/features/events/event-detail.component.ts`
Single event page. Two-column layout (content left, sticky booking sidebar right).
Left: large art (16/7 ratio), serif title, info grid, description.
Right: seat category tiles, quantity selector, total price, Book Now button. Touch this to:
- Display more event fields
- Add a seat map or calendar widget

### `src/app/features/bookings/my-bookings.component.ts`
Customer's booking history with tab strip (Upcoming / Past). Booking rows include
a 180px `EventArtComponent` thumbnail, serif event title, mono booking ID, cancel button. Touch this to:
- Add a booking detail modal
- Add downloadable invoice

### `src/app/features/tickets/my-tickets.component.ts`
Split-card ticket list. Left panel: serif event title, mono ticket number, seat, status badge.
Dashed separator. Right panel: generated QR code (88×88px), dimmed when ticket is not VALID.
Paginated. Touch this to:
- Replace the fake QR with a real QR library (`qrcode` npm package)
- Add ticket PDF download

### `src/app/features/profile/profile.component.ts`
Two-column profile editor: 240px identity card (initials circle, name, email, role badge)
+ form with sections for personal info, preferences, notifications. Touch this to:
- Add or remove profile fields
- Add a password-change section

### `src/app/features/organizer/organizer-dashboard.component.ts`
Organizer's main page. Stats grid (Total events / Published / Drafts / Cancelled),
then a two-column layout: events list (art thumbnail, sell-through progress bar,
publish/cancel/edit actions) and a Quick Actions side panel. Touch this to:
- Add analytics charts
- Add sorting or filtering of the events list

### `src/app/features/organizer/event-form.component.ts`
Reused for both Create and Edit flows. Checks `route.snapshot.paramMap.get('id')` to
decide mode. Three card sections: Basic Info, Venue, Seat Categories (dynamic `FormArray`).
Touch this to:
- Add new event fields
- Add an image upload for the event cover

### `src/app/features/organizer/ticket-scanner.component.ts`
Ticket lookup tool. Ticket number input → `GET_TICKET` query → displays the same split-card
design as the customer tickets page. For VALID tickets: Validate (mark USED) + Cancel buttons.
Touch this to:
- Integrate a real camera QR scanner library
- Add bulk validation from a CSV list

---

## 15. Common Tweaks Cookbook

### Change the accent colour everywhere

`src/styles.css`:
```css
:root {
  --accent:      oklch(0.65 0.18 250);   /* blue instead of amber */
  --accent-ink:  oklch(0.35 0.14 250);
  --accent-soft: oklch(0.94 0.04 250);
}
```

### Change the GraphQL endpoint URL

`src/app/app.config.ts`:
```ts
link: httpLink.create({ uri: '/graphql' })
//                             ^^^^^^^^ change this
```

And `proxy.conf.json` for the dev server:
```json
{ "/graphql": { "target": "http://localhost:8080" } }
```

### Add a new field to an existing query

In `src/app/shared/graphql/documents.ts`, add the field to the selection set:

```ts
// Before
export const GET_EVENT = gql`
  query GetEvent($id: ID!) {
    event(id: $id) { id title dateTime }
  }
`;

// After — added description
export const GET_EVENT = gql`
  query GetEvent($id: ID!) {
    event(id: $id) { id title dateTime description }
  }
`;
```

Then add the field to the TypeScript interface in `models.ts` if it isn't already there.

### Change where a user lands after login

`src/app/features/auth/login.component.ts`:
```ts
next: (payload) => {
  const roles = payload.user?.roles ?? [];
  if (roles.includes('employee')) {
    this.router.navigate(['/organizer']);  // ← organizer destination
  } else {
    this.router.navigate(['/home']);       // ← customer destination
  }
}
```

### Add a new nav link for customers only

`src/app/shared/navbar.component.ts` — inside the `@else` block:
```html
} @else {
  <a routerLink="/home"       routerLinkActive="hdr-nav-active">My Dashboard</a>
  <a routerLink="/bookings"   routerLinkActive="hdr-nav-active">My Bookings</a>
  <a routerLink="/tickets"    routerLinkActive="hdr-nav-active">My Tickets</a>
  <a routerLink="/new-page"   routerLinkActive="hdr-nav-active">New Page</a>  ← add here
}
```

### Add a new form field to the event creation form

`src/app/features/organizer/event-form.component.ts`:

1. Add to `form`:
   ```ts
   form = this.fb.group({
     ...,
     website: [''],
   });
   ```
2. Add to the template inside a `form-card`:
   ```html
   <div class="field">
     <label>Website</label>
     <input formControlName="website" class="inp" placeholder="https://...">
   </div>
   ```
3. Add to the `input` object in `save()`:
   ```ts
   const input = { ..., website: v.website || null };
   ```

### Show a loading state while data loads

Every page already follows this pattern — copy it:

```ts
loading = signal(false);
error   = signal('');

load() {
  this.loading.set(true);
  this.error.set('');
  this.apollo.query(...).subscribe({
    next: r  => { this.loading.set(false); this.data.set(r.data.something); },
    error: err => { this.loading.set(false); this.error.set(err.message || 'Failed'); }
  });
}
```

```html
@if (loading()) {
  <div class="loading-center"><div class="spinner spinner-lg"></div></div>
} @else if (error()) {
  <div class="alert alert-error">{{ error() }}</div>
} @else {
  <!-- actual content -->
}
```

### Add a confirmation dialog before a destructive action

```ts
cancel(ev: Event) {
  if (!confirm(`Cancel "${ev.title}"? This cannot be undone.`)) return;
  // proceed with mutation
}
```

### Use the section-head pattern on a new page

```html
<div class="section-head">
  <div class="kicker">Your Kicker Text</div>
  <h2>Page <em>Title</em></h2>
  <p class="sub">Optional subtitle text here.</p>
</div>
```

### Add a new event art palette

`src/app/shared/event-art.component.ts`:
```ts
const PALETTES = [
  ...,
  { bg: '#0d1117', fg: '#e6edf3', acc: '#58a6ff' },  // GitHub dark + blue
];
```

The new palette is automatically available — existing seeds don't shift because
the component uses `seed % PALETTES.length` and there are already 8 entries
(adding a 9th only affects seeds divisible by 9).

### Show an empty state

```html
@if (items().length === 0) {
  <div class="empty-state">
    <h3>Nothing here yet</h3>
    <p>Some explanation of why and what to do.</p>
    <button class="btn btn-primary btn-sm" style="margin-top:16px" (click)="doSomething()">
      Call to Action
    </button>
  </div>
}
```

---

*Last updated: April 2026*
