import { Component, ElementRef, inject, signal, effect, viewChild, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Apollo } from 'apollo-angular';
import { forkJoin } from 'rxjs';
import { loadStripe, Stripe, StripeCardElement } from '@stripe/stripe-js';
import { CREATE_BOOKING, CREATE_ORDER_PAYMENT_INTENT, CONFIRM_MOCK_PAYMENT } from '../../shared/graphql/documents';
import { Booking, PaymentIntent } from '../../shared/models/models';
import { CartService } from '../../core/cart.service';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [RouterLink, FormsModule],
  template: `
    <div class="container page">
      <div class="section-head" style="margin-bottom:32px">
        <div class="kicker">Checkout</div>
        <h2>Payment</h2>
      </div>

      @if (loading()) {
        <div class="loading-center"><div class="spinner spinner-lg"></div><p class="mono xs muted" style="margin-top:12px">Reserving your seats…</p></div>
      } @else if (error()) {
        <div class="alert alert-error">{{ error() }}</div>
        <a routerLink="/cart" class="btn btn-ghost btn-sm" style="margin-top:16px;text-decoration:none">← Back to cart</a>
      } @else if (cart.hasItems()) {
        <div class="checkout-layout">
          <!-- Payment form -->
          <div class="checkout-main">
            <div class="booking-card" style="position:static">
              <div class="mono xs muted" style="text-transform:uppercase;letter-spacing:0.1em;margin-bottom:16px">Card details</div>

              @if (payError()) {
                <div class="alert alert-error" style="margin-bottom:16px">{{ payError() }}</div>
              }

              @if (provider() === 'stripe') {
                <div #cardEl class="stripe-card"></div>
                @if (!stripeReady()) {
                  <div class="mono xs muted" style="margin-top:10px"><span class="spinner"></span> Loading secure card field…</div>
                }
              } @else {
                <div class="field" style="margin-bottom:16px">
                  <label>Card number</label>
                  <input class="inp" [(ngModel)]="cardNumber" placeholder="4242 4242 4242 4242" inputmode="numeric" autocomplete="off" />
                </div>
                <div style="display:flex;gap:12px;margin-bottom:4px">
                  <div class="field" style="flex:1"><label>Expiry</label><input class="inp" placeholder="12 / 34" /></div>
                  <div class="field" style="width:100px"><label>CVC</label><input class="inp" placeholder="123" /></div>
                </div>
                <div class="mock-hint">
                  <div class="mono xs muted" style="text-transform:uppercase;letter-spacing:0.08em;margin-bottom:6px">Test cards (mock mode)</div>
                  <div class="mono xs muted-2">4242 4242 4242 4242 — success</div>
                  <div class="mono xs muted-2">4000 0000 0000 0002 — declined</div>
                  <div class="mono xs muted-2">4000 0025 0000 3155 — 3-D Secure</div>
                </div>
              }

              <button class="btn btn-primary btn-lg btn-block" style="margin-top:20px" (click)="pay()" [disabled]="!canPay()">
                @if (paying()) { <span class="spinner"></span> } Pay {{ cart.currency() }} {{ cart.total() }}
              </button>
            </div>
          </div>

          <!-- Order summary -->
          <div class="checkout-summary">
            <div class="booking-card">
              <div class="mono xs muted" style="text-transform:uppercase;letter-spacing:0.1em;margin-bottom:16px">Order summary</div>
              @for (item of cart.items(); track item.eventId + '|' + item.seatCategory) {
                <div class="order-item">
                  <div>
                    <div style="font-size:14px">{{ item.eventTitle }}</div>
                    <div class="mono xs muted">{{ item.seatCategory }} · {{ item.quantity }}×</div>
                  </div>
                  <div style="font-size:14px">{{ item.currency }} {{ lineTotal(item) }}</div>
                </div>
              }
              <div class="summary-total">
                <span class="mono xs muted" style="text-transform:uppercase;letter-spacing:0.08em">Total</span>
                <span style="font-family:var(--serif);font-size:24px">{{ cart.currency() }} {{ cart.total() }}</span>
              </div>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .checkout-layout { display: grid; grid-template-columns: 1fr 320px; gap: 40px; align-items: start; }
    @media (max-width: 768px) { .checkout-layout { grid-template-columns: 1fr; } }

    .booking-card {
      position: sticky; top: 88px;
      background: var(--bg-card); border: 1px solid var(--line);
      border-radius: var(--radius-lg); padding: 20px;
    }
    .stripe-card { padding: 12px 14px; border: 1px solid var(--line); border-radius: var(--radius); background: var(--bg); }
    .order-item { display: flex; justify-content: space-between; gap: 12px; padding: 8px 0; border-bottom: 1px solid var(--line); }
    .summary-total { display: flex; justify-content: space-between; align-items: baseline; padding-top: 16px; margin-top: 12px; border-top: 1px solid var(--line); }
    .mock-hint { margin-top: 20px; padding-top: 16px; border-top: 1px dashed var(--line); }
    .muted-2 { color: var(--ink-4); line-height: 1.6; }
    .loading-center { display: flex; flex-direction: column; align-items: center; padding: 80px 0; }
  `]
})
export class CheckoutComponent implements OnInit {
  private apollo = inject(Apollo);
  private router = inject(Router);
  cart = inject(CartService);

  loading = signal(true);
  error = signal('');
  paying = signal(false);
  payError = signal('');
  provider = signal<string>('');
  stripeReady = signal(false);
  cardNumber = '';

  private orderId = '';
  private bookingIds: string[] = [];
  private clientSecret: string | null = null;
  private stripe: Stripe | null = null;
  private card: StripeCardElement | null = null;
  private mounted = false;

  private cardEl = viewChild<ElementRef<HTMLDivElement>>('cardEl');

  constructor() {
    effect(() => {
      const el = this.cardEl();
      if (el && this.stripeReady() && this.card && !this.mounted) {
        this.card.mount(el.nativeElement);
        this.mounted = true;
      }
    });
  }

  ngOnInit() {
    const items = this.cart.items();
    if (items.length === 0) {
      this.router.navigate(['/cart']);
      return;
    }
    this.orderId = this.cart.orderId();

    // 1. Create a booking per cart item (reserve + hold). Each is idempotent on its own key,
    //    so a checkout reload reuses the same bookings.
    const requests = items.map(item =>
      this.apollo.mutate<{ createBooking: Booking }>({
        mutation: CREATE_BOOKING,
        variables: {
          input: {
            eventId: item.eventId,
            seatCategory: item.seatCategory,
            quantity: item.quantity,
            idempotencyKey: item.idempotencyKey,
          }
        }
      })
    );

    forkJoin(requests).subscribe({
      next: results => {
        this.bookingIds = results.map(r => r.data!.createBooking.id);
        this.startPayment();
      },
      error: err => { this.loading.set(false); this.error.set(this.clean(err)); }
    });
  }

  // 2. One payment intent for the whole order.
  private startPayment() {
    this.apollo.mutate<{ createOrderPaymentIntent: PaymentIntent }>({
      mutation: CREATE_ORDER_PAYMENT_INTENT,
      variables: { orderId: this.orderId, bookingIds: this.bookingIds }
    }).subscribe({
      next: r => {
        const pi = r.data!.createOrderPaymentIntent;
        this.loading.set(false);
        this.provider.set(pi.provider);
        this.clientSecret = pi.clientSecret;

        if (pi.status === 'COMPLETED') { this.goToConfirmation(); return; }
        if (pi.provider === 'stripe') { this.initStripe(pi.publishableKey, pi.clientSecret); }
      },
      error: err => { this.loading.set(false); this.error.set(this.clean(err)); }
    });
  }

  private async initStripe(publishableKey: string | null, clientSecret: string | null) {
    if (!publishableKey || !clientSecret) {
      this.error.set('Payment is misconfigured (missing Stripe key). Please try again later.');
      return;
    }
    this.stripe = await loadStripe(publishableKey);
    if (!this.stripe) {
      this.error.set('Could not load the payment form. Please retry.');
      return;
    }
    this.card = this.stripe.elements().create('card');
    this.stripeReady.set(true);
  }

  canPay(): boolean {
    if (this.paying()) return false;
    return this.provider() === 'stripe' ? this.stripeReady() : this.cardNumber.trim().length > 0;
  }

  pay() {
    if (this.provider() === 'stripe') this.payWithStripe();
    else this.payWithMock();
  }

  private async payWithStripe() {
    if (!this.stripe || !this.card || !this.clientSecret) return;
    this.paying.set(true);
    this.payError.set('');
    const result = await this.stripe.confirmCardPayment(this.clientSecret, {
      payment_method: { card: this.card }
    });
    this.paying.set(false);

    if (result.error) {
      this.payError.set(result.error.message ?? 'Your card was declined.');
      return;
    }
    if (result.paymentIntent?.status === 'succeeded') {
      this.goToConfirmation();
    } else {
      this.payError.set('Payment could not be completed. Please try again.');
    }
  }

  // Mock mode: confirm the order payment by its orderId (the payment's primary handle).
  private payWithMock() {
    this.paying.set(true);
    this.payError.set('');
    this.apollo.mutate<{ confirmMockPayment: PaymentIntent }>({
      mutation: CONFIRM_MOCK_PAYMENT,
      variables: { bookingId: this.orderId, cardNumber: this.cardNumber }
    }).subscribe({
      next: r => {
        this.paying.set(false);
        if (r.data!.confirmMockPayment.status === 'COMPLETED') {
          this.goToConfirmation();
        } else {
          this.payError.set('Payment was declined. Try a different card.');
        }
      },
      error: err => { this.paying.set(false); this.payError.set(this.clean(err)); }
    });
  }

  private goToConfirmation() {
    const bookings = this.bookingIds.join(',');
    this.cart.clear();
    this.router.navigate(['/checkout/confirmation'], { queryParams: { bookings } });
  }

  lineTotal(item: { unitPrice: string; quantity: number }): string {
    return (parseFloat(item.unitPrice) * item.quantity).toFixed(2);
  }

  private clean(err: { message?: string }): string {
    return err.message?.replace('ApolloError: ', '') || 'Something went wrong. Please try again.';
  }
}
