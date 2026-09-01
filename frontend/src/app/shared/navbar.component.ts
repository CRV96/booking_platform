import { Component, inject, signal, effect, HostListener } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { CartService } from '../core/cart.service';
import { LovelistService } from '../core/lovelist.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  template: `
    <header class="hdr">
      <a routerLink="/" class="logo" style="text-decoration:none;color:inherit">
        <span class="logo-mark">CRV <em>Bookings</em></span>
        <span class="logo-sub">Est. 2026</span>
      </a>

      <nav class="hdr-nav">
        <a routerLink="/events" routerLinkActive="hdr-nav-active" [routerLinkActiveOptions]="{exact:true}"
           style="text-decoration:none">Events</a>

        @if (auth.isAuthenticated()) {
          @if (auth.isOrganizer()) {
            <a routerLink="/organizer" routerLinkActive="hdr-nav-active" style="text-decoration:none">Manage Events</a>
            <a routerLink="/organizer/tickets" routerLinkActive="hdr-nav-active" style="text-decoration:none">Scan Tickets</a>
          } @else {
            <a routerLink="/home" routerLinkActive="hdr-nav-active" style="text-decoration:none">My Dashboard</a>
            <a routerLink="/bookings" routerLinkActive="hdr-nav-active" style="text-decoration:none">My Bookings</a>
            <a routerLink="/tickets" routerLinkActive="hdr-nav-active" style="text-decoration:none">My Tickets</a>
          }
        }
      </nav>

      <div class="hdr-right">
        @if (!auth.isOrganizer()) {
          <a routerLink="/lovelist" routerLinkActive="hdr-nav-active" class="hdr-cart" style="text-decoration:none">
            ♥ Lovelist
            @if (lovelist.count() > 0) { <span class="hdr-cart-badge">{{ lovelist.count() }}</span> }
          </a>
          <a routerLink="/cart" routerLinkActive="hdr-nav-active" class="hdr-cart" style="text-decoration:none">
            Cart
            @if (cart.count() > 0) { <span class="hdr-cart-badge">{{ cart.count() }}</span> }
          </a>
        }
        @if (auth.isAuthenticated()) {
          <div class="hdr-menu">
            <button type="button" class="hdr-user" (click)="toggleMenu($event)"
                    [attr.aria-expanded]="menuOpen()" aria-haspopup="menu">
              <span class="hdr-avatar">{{ initials() }}</span>
              <span class="hdr-user-name">{{ auth.user()?.firstName || auth.user()?.username }}</span>
              @if (auth.isOrganizer()) {
                <span class="badge badge-accent">Organizer</span>
              }
              <span class="hdr-caret" [class.open]="menuOpen()">▾</span>
            </button>
            @if (menuOpen()) {
              <div class="hdr-dropdown" role="menu">
                <a routerLink="/profile" class="hdr-dropdown-item" role="menuitem" (click)="closeMenu()">My Account</a>
                <button type="button" class="hdr-dropdown-item" role="menuitem" (click)="signOut()">Sign out</button>
              </div>
            }
          </div>
        } @else {
          <a routerLink="/auth/login" class="btn btn-primary btn-sm" style="text-decoration:none">Login</a>
        }
      </div>
    </header>
  `,
  styles: [`
    .hdr {
      position: sticky; top: 0; z-index: 40;
      display: flex; align-items: center; gap: 32px;
      padding: 16px 40px;
      background: color-mix(in oklch, #fafaf8 88%, transparent);
      backdrop-filter: blur(10px);
      border-bottom: 1px solid #e6e4dc;
    }
    .logo { display: flex; flex-direction: column; gap: 0; line-height: 1; flex-shrink: 0; }
    .logo-mark { font-family: "Instrument Serif", serif; font-size: 24px; letter-spacing: -0.02em; }
    .logo-mark em { font-style: italic; color: oklch(0.38 0.10 70); }
    .logo-sub { font-family: "JetBrains Mono", monospace; font-size: 9px; color: #a3a198; text-transform: uppercase; letter-spacing: 0.14em; margin-top: 3px; }

    .hdr-nav { display: flex; gap: 4px; flex: 1; }
    .hdr-nav a {
      padding: 7px 12px; font-size: 13px; color: #6b6a62;
      border-radius: 4px; transition: color 0.15s, background 0.15s;
    }
    .hdr-nav a:hover { color: #14130f; }
    .hdr-nav a.hdr-nav-active { color: #14130f; background: #f2f1ec; }

    .hdr-right { margin-left: auto; display: flex; align-items: center; gap: 12px; }
    .hdr-menu { position: relative; }
    .hdr-user {
      display: flex; align-items: center; gap: 8px;
      background: transparent; border: none; cursor: pointer; color: inherit;
      font-family: inherit; padding: 4px 8px; border-radius: 6px; transition: background 0.15s;
    }
    .hdr-user:hover { background: #f2f1ec; }
    .hdr-caret { font-size: 10px; color: #a3a198; transition: transform 0.15s; }
    .hdr-caret.open { transform: rotate(180deg); }
    .hdr-dropdown {
      position: absolute; top: calc(100% + 8px); right: 0; z-index: 50;
      min-width: 184px; padding: 6px;
      background: #fafaf8; border: 1px solid #e6e4dc; border-radius: 10px;
      box-shadow: 0 10px 28px rgba(20, 19, 15, 0.10);
      display: flex; flex-direction: column; gap: 2px;
    }
    .hdr-dropdown-item {
      display: block; width: 100%; text-align: left;
      padding: 9px 12px; font-size: 13px; color: #4a4942;
      background: transparent; border: none; border-radius: 6px; cursor: pointer;
      font-family: inherit; text-decoration: none; transition: background 0.15s, color 0.15s;
    }
    .hdr-dropdown-item:hover { background: #f2f1ec; color: #14130f; }
    .hdr-avatar {
      width: 28px; height: 28px; border-radius: 50%; flex-shrink: 0;
      background: #14130f; color: #fafaf8;
      display: flex; align-items: center; justify-content: center;
      font-size: 11px; font-weight: 600;
    }
    .hdr-user-name { font-size: 13px; font-weight: 500; color: #14130f; }

    .hdr-cart {
      display: inline-flex; align-items: center; gap: 6px;
      padding: 7px 12px; font-size: 13px; color: #6b6a62;
      border-radius: 4px; transition: color 0.15s, background 0.15s;
    }
    .hdr-cart:hover { color: #14130f; }
    .hdr-cart.hdr-nav-active { color: #14130f; background: #f2f1ec; }
    .hdr-cart-badge {
      min-width: 18px; height: 18px; padding: 0 5px; border-radius: 9px;
      background: #14130f; color: #fafaf8;
      display: inline-flex; align-items: center; justify-content: center;
      font-size: 11px; font-weight: 600;
    }
  `]
})
export class NavbarComponent {
  auth = inject(AuthService);
  cart = inject(CartService);
  lovelist = inject(LovelistService);
  menuOpen = signal(false);

  constructor() {
    // Load the server-backed cart & lovelist whenever the user is authenticated — covers both
    // page refresh (session already present) and login. Runs after DI completes, so it avoids the
    // re-entrancy that broke loading from inside AuthService's constructor.
    effect(() => {
      if (this.auth.isAuthenticated() && !this.auth.isOrganizer()) {
        this.cart.load();
        this.lovelist.load();
      }
    });
  }

  toggleMenu(e: MouseEvent) {
    e.stopPropagation();   // keep the document click-away handler from immediately re-closing
    this.menuOpen.update(open => !open);
  }

  closeMenu() { this.menuOpen.set(false); }

  signOut() {
    this.closeMenu();
    this.auth.logout();
  }

  /** Close on any click outside the menu, or on Escape. */
  @HostListener('document:click')
  onDocumentClick() { if (this.menuOpen()) this.menuOpen.set(false); }

  @HostListener('document:keydown.escape')
  onEscape() { if (this.menuOpen()) this.menuOpen.set(false); }

  initials() {
    const u = this.auth.user();
    if (!u) return '?';
    if (u.firstName && u.lastName) return (u.firstName[0] + u.lastName[0]).toUpperCase();
    return u.username[0].toUpperCase();
  }
}
