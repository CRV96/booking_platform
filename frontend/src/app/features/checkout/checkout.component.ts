import { Component, ElementRef, inject, signal, effect, viewChild, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Apollo } from 'apollo-angular';
import { loadStripe, Stripe, StripeCardElement } from '@stripe/stripe-js';
import { CREATE_BOOKING, CREATE_PAYMENT_INTENT, CONFIRM_MOCK_PAYMENT } from '../../shared/graphql/documents';
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
      } @else if (item()) {
        <div class="checkout-layout">
          <!-- Payment form -->
          <div class="checkout-main">
            <div class="booking-card" style="position:static">
              <div class="mono xs muted" style="text-transform:uppercase;letter-spacing:0.1em;margin-bottom:16px">Card details</div>

              @if (payError()) {
                <div class="alert alert-error" style="margin-bottom:16px">{{ payError() }}</div>
              }

              @if (provider() === 'stripe') {
                <!-- Real Stripe Elements mount here (Stripe-hosted iframe; card never touches our servers) -->
                <div #cardEl class="stripe-card"></div>
                @if (!stripeReady()) {
                  <div class="mono xs muted" style="margin-top:10px"><span class="spinner"></span> Loading secure card field…</div>
                }
              } @else {
                <!-- Mock stand-in form -->
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
                @if (paying()) { <span class="spinner"></span> } Pay {{ item()!.currency }} {{ cart.total() }}
              </button>
            </div>
          </div>

          <!-- Order summary -->
          <div class="checkout-summary">
            <div class="booking-card">
              <div class="mono xs muted" style="text-transform:uppercase;letter-spacing:0.1em;margin-bottom:16px">Order summary</div>
              <div style="font-family:var(--serif);font-size:18px;margin-bottom:4px">{{ item()!.eventTitle }}</div>
              <div class="mono xs muted">{{ item()!.seatCategory }} · {{ item()!.quantity }} ticket{{ item()!.quantity > 1 ? 's' : '' }}</div>
              <div class="summary-total">
                <span class="mono xs muted" style="text-transform:uppercase;letter-spacing:0.08em">Total</span>
                <span style="font-family:var(--serif);font-size:24px">{{ item()!.currency }} {{ cart.total() }}</span>
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
    .summary-total { display: flex; justify-content: space-between; align-items: baseline; padding-top: 16px; margin-top: 16px; border-top: 1px solid var(--line); }
    .mock-hint { margin-top: 20px; padding-top: 16px; border-top: 1px dashed var(--line); }
    .muted-2 { color: var(--ink-4); line-height: 1.6; }
    .loading-center { display: flex; flex-direction: column; align-items: center; padding: 80px 0; }
  `]
})
export class CheckoutComponent implements OnInit {
  private apollo = inject(Apollo);
  private router = inject(Router);
  cart = inject(CartService);

  item = this.cart.item;

  loading = signal(true);
  error = signal('');
  paying = signal(false);
  payError = signal('');
  bookingId = signal<string | null>(null);
  provider = signal<string>('');
  stripeReady = signal(false);
  cardNumber = '';

  private clientSecret: string | null = null;
  private stripe: Stripe | null = null;
  private card: StripeCardElement | null = null;
  private mounted = false;

  private cardEl = viewChild<ElementRef<HTMLDivElement>>('cardEl');

  constructor() {
    // Mount the Stripe card field once BOTH the DOM container exists and the card is created.
    // (loadStripe is async and the container only renders after the intent resolves.)
    effect(() => {
      const el = this.cardEl();
      if (el && this.stripeReady() && this.card && !this.mounted) {
        this.card.mount(el.nativeElement);
        this.mounted = true;
      }
    });
  }

  ngOnInit() {
    const item = this.cart.item();
    if (!item) {
      this.router.navigate(['/cart']);
      return;
    }
    // 1. Create the booking (reserve + hold). Idempotent on the cart's key → a reload reuses it.
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
    }).subscribe({
      next: r => this.startPayment(r.data!.createBooking.id),
      error: err => { this.loading.set(false); this.error.set(this.clean(err)); }
    });
  }

  // 2. Create (or resume) the payment intent, then branch on provider.
  private startPayment(bookingId: string) {
    this.bookingId.set(bookingId);
    this.apollo.mutate<{ createPaymentIntent: PaymentIntent }>({
      mutation: CREATE_PAYMENT_INTENT,
      variables: { bookingId }
    }).subscribe({
      next: r => {
        const pi = r.data!.createPaymentIntent;
        this.loading.set(false);
        this.provider.set(pi.provider);
        this.clientSecret = pi.clientSecret;

        if (pi.status === 'COMPLETED') { this.goToConfirmation(bookingId); return; }
        if (pi.provider === 'stripe') { this.initStripe(pi.publishableKey, pi.clientSecret); }
      },
      error: err => { this.loading.set(false); this.error.set(this.clean(err)); }
    });
  }

  // Load Stripe.js and build the card element; the effect mounts it once the container renders.
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

  // Real path: confirm the card client-side. Stripe handles 3-D Secure automatically.
  private async payWithStripe() {
    if (!this.stripe || !this.card || !this.clientSecret) return;
    const bookingId = this.bookingId();
    if (!bookingId) return;

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
      this.goToConfirmation(bookingId);
    } else {
      this.payError.set('Payment could not be completed. Please try again.');
    }
  }

  // Mock path: the card number selects the outcome.
  private payWithMock() {
    const bookingId = this.bookingId();
    if (!bookingId) return;
    this.paying.set(true);
    this.payError.set('');
    this.apollo.mutate<{ confirmMockPayment: PaymentIntent }>({
      mutation: CONFIRM_MOCK_PAYMENT,
      variables: { bookingId, cardNumber: this.cardNumber }
    }).subscribe({
      next: r => {
        this.paying.set(false);
        if (r.data!.confirmMockPayment.status === 'COMPLETED') {
          this.goToConfirmation(bookingId);
        } else {
          this.payError.set('Payment was declined. Try a different card.');
        }
      },
      error: err => { this.paying.set(false); this.payError.set(this.clean(err)); }
    });
  }

  private goToConfirmation(bookingId: string) {
    this.cart.clear();
    this.router.navigate(['/checkout/confirmation', bookingId]);
  }

  private clean(err: { message?: string }): string {
    return err.message?.replace('ApolloError: ', '') || 'Something went wrong. Please try again.';
  }
}
