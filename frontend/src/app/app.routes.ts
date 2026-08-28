import { Routes } from '@angular/router';
import { authGuard, organizerGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'events', pathMatch: 'full' },

  {
    path: 'auth',
    children: [
      { path: 'login', loadComponent: () => import('./features/auth/login.component').then(m => m.LoginComponent) },
      { path: 'register', loadComponent: () => import('./features/auth/register.component').then(m => m.RegisterComponent) },
    ]
  },

  // Customer home — shown after customer login
  {
    path: 'home',
    canActivate: [authGuard],
    loadComponent: () => import('./features/customer/customer-home.component').then(m => m.CustomerHomeComponent),
  },

  {
    path: 'events',
    children: [
      { path: '', loadComponent: () => import('./features/events/event-list.component').then(m => m.EventListComponent) },
      { path: ':id', loadComponent: () => import('./features/events/event-detail.component').then(m => m.EventDetailComponent) },
    ]
  },

  {
    path: 'cart',
    loadComponent: () => import('./features/cart/cart.component').then(m => m.CartComponent),
  },

  {
    path: 'checkout',
    canActivate: [authGuard],
    children: [
      { path: '', loadComponent: () => import('./features/checkout/checkout.component').then(m => m.CheckoutComponent) },
      { path: 'confirmation/:bookingId', loadComponent: () => import('./features/checkout/confirmation.component').then(m => m.ConfirmationComponent) },
    ]
  },

  {
    path: 'bookings',
    canActivate: [authGuard],
    loadComponent: () => import('./features/bookings/my-bookings.component').then(m => m.MyBookingsComponent),
  },

  {
    path: 'tickets',
    canActivate: [authGuard],
    loadComponent: () => import('./features/tickets/my-tickets.component').then(m => m.MyTicketsComponent),
  },

  {
    path: 'profile',
    canActivate: [authGuard],
    loadComponent: () => import('./features/profile/profile.component').then(m => m.ProfileComponent),
  },

  {
    path: 'organizer',
    canActivate: [organizerGuard],
    children: [
      { path: '', loadComponent: () => import('./features/organizer/organizer-dashboard.component').then(m => m.OrganizerDashboardComponent) },
      { path: 'events/new', loadComponent: () => import('./features/organizer/event-form.component').then(m => m.EventFormComponent) },
      { path: 'events/:id/edit', loadComponent: () => import('./features/organizer/event-form.component').then(m => m.EventFormComponent) },
      { path: 'tickets', loadComponent: () => import('./features/organizer/ticket-scanner.component').then(m => m.TicketScannerComponent) },
    ]
  },

  { path: '**', redirectTo: 'events' },
];
