import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CartService } from '../../core/cart.service';
import { AuthService } from '../../core/auth.service';
import { EventArtComponent } from '../../shared/event-art.component';

@Component({
  selector: 'app-cart',
  standalone: true,
  imports: [RouterLink, FormsModule, EventArtComponent],
  template: `
    <div class="container page">
      <div class="section-head" style="margin-bottom:32px">
        <div class="kicker">Checkout</div>
        <h2>Your <em>Cart</em></h2>
      </div>

      @if (!cart.hasItem()) {
        <div class="empty-state">
          <h3>Your cart is empty</h3>
          <p>Browse <a routerLink="/events" style="color:var(--ink);text-decoration:underline">upcoming events</a> and add a ticket.</p>
        </div>
      } @else {
        <div class="cart-layout">
          <!-- Line item -->
          <div class="cart-main">
            <div class="cart-row">
              <div class="cart-thumb">
                <app-event-art [seed]="artSeed(cart.item()!.eventId)" [title]="cart.item()!.eventTitle" ratio="16/10" />
              </div>
              <div class="cart-info">
                <a [routerLink]="['/events', cart.item()!.eventId]" class="cart-title" style="text-decoration:none">{{ cart.item()!.eventTitle }}</a>
                <div class="mono xs muted" style="margin-top:6px">{{ cart.item()!.seatCategory }}</div>
                <div style="margin-top:4px">{{ cart.item()!.currency }} {{ cart.item()!.unitPrice }} <span class="mono xs muted">each</span></div>

                <div class="field" style="max-width:120px;margin-top:16px">
                  <label>Quantity</label>
                  <select class="inp" [ngModel]="cart.item()!.quantity" (ngModelChange)="cart.setQuantity(+$event)">
                    @for (n of quantities; track n) {
                      <option [value]="n">{{ n }}</option>
                    }
                  </select>
                </div>
              </div>
              <div class="cart-remove">
                <button class="btn btn-ghost btn-sm" (click)="cart.clear()">Remove</button>
              </div>
            </div>
          </div>

          <!-- Summary -->
          <div class="cart-summary">
            <div class="booking-card">
              <div class="mono xs muted" style="text-transform:uppercase;letter-spacing:0.1em;margin-bottom:16px">Order summary</div>
              <div class="summary-line">
                <span class="muted">{{ cart.item()!.quantity }} × {{ cart.item()!.seatCategory }}</span>
                <span>{{ cart.item()!.currency }} {{ cart.total() }}</span>
              </div>
              <div class="summary-total">
                <span class="mono xs muted" style="text-transform:uppercase;letter-spacing:0.08em">Total</span>
                <span style="font-family:var(--serif);font-size:24px">{{ cart.item()!.currency }} {{ cart.total() }}</span>
              </div>

              @if (!auth.isAuthenticated()) {
                <p style="font-size:13px;color:var(--ink-3);margin:16px 0 0">
                  Please <a routerLink="/auth/login" style="color:var(--ink);text-decoration:underline">sign in</a> to check out.
                </p>
              }

              <button class="btn btn-primary btn-lg btn-block" style="margin-top:16px" (click)="checkout()">
                Proceed to checkout →
              </button>
              <a routerLink="/events" class="btn btn-ghost btn-sm btn-block" style="margin-top:10px;text-decoration:none">Continue browsing</a>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .cart-layout { display: grid; grid-template-columns: 1fr 320px; gap: 40px; align-items: start; }
    @media (max-width: 768px) { .cart-layout { grid-template-columns: 1fr; } }

    .cart-row { display: flex; gap: 20px; align-items: flex-start; padding: 20px 0; border-top: 1px solid var(--line); }
    .cart-thumb { width: 200px; flex-shrink: 0; border-radius: var(--radius); overflow: hidden; }
    .cart-info { flex: 1; min-width: 0; }
    .cart-title { font-family: var(--serif); font-size: 22px; line-height: 1.2; color: var(--ink); display: block; }
    .cart-title:hover { color: var(--ink-2); }
    .cart-remove { flex-shrink: 0; }

    .booking-card {
      position: sticky; top: 88px;
      background: var(--bg-card); border: 1px solid var(--line);
      border-radius: var(--radius-lg); padding: 20px;
    }
    .summary-line { display: flex; justify-content: space-between; font-size: 14px; padding: 8px 0; }
    .summary-total { display: flex; justify-content: space-between; align-items: baseline; padding-top: 16px; margin-top: 8px; border-top: 1px solid var(--line); }
    .muted { color: var(--ink-3); }
  `]
})
export class CartComponent {
  cart = inject(CartService);
  auth = inject(AuthService);
  private router = inject(Router);

  readonly quantities = [1, 2, 3, 4, 5, 6, 7, 8];

  checkout(): void {
    if (!this.auth.isAuthenticated()) {
      this.router.navigate(['/auth/login']);
      return;
    }
    this.router.navigate(['/checkout']);
  }

  artSeed(eventId: string): number {
    return eventId ? eventId.split('').reduce((a, c) => a + c.charCodeAt(0), 0) % 100 : 1;
  }
}
