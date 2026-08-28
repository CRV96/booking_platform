import { Component, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Apollo } from 'apollo-angular';
import { Subscription, timer } from 'rxjs';
import { GET_BOOKING } from '../../shared/graphql/documents';
import { Booking } from '../../shared/models/models';

type ConfirmState = 'processing' | 'confirmed' | 'timeout' | 'failed';

@Component({
  selector: 'app-confirmation',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="container page" style="max-width:640px">
      @switch (state()) {
        @case ('processing') {
          <div class="conf-center">
            <div class="spinner spinner-lg"></div>
            <h2 style="margin-top:20px">Finalising your order…</h2>
            <p class="muted" style="margin-top:8px">Your payment went through — we're confirming your booking and issuing tickets.</p>
          </div>
        }
        @case ('confirmed') {
          <div class="conf-center">
            <div class="conf-check">✓</div>
            <div class="kicker" style="margin-top:16px">Confirmed</div>
            <h2 style="margin-top:4px">You're going! 🎉</h2>
            @if (booking(); as b) {
              <div class="conf-summary">
                <div style="font-family:var(--serif);font-size:20px;margin-bottom:6px">{{ b.eventTitle }}</div>
                <div class="mono xs muted">{{ b.seatCategory }} · {{ b.quantity }} ticket{{ b.quantity > 1 ? 's' : '' }} · {{ b.currency }} {{ b.totalPrice }}</div>
                <div class="mono xs muted-2" style="margin-top:8px">Booking {{ b.id }}</div>
              </div>
            }
            <div class="conf-actions">
              <a routerLink="/tickets" class="btn btn-primary" style="text-decoration:none">View my tickets</a>
              <a routerLink="/bookings" class="btn btn-ghost" style="text-decoration:none">My bookings</a>
            </div>
          </div>
        }
        @case ('timeout') {
          <div class="conf-center">
            <div class="conf-check conf-check-muted">…</div>
            <h2 style="margin-top:16px">Almost there</h2>
            <p class="muted" style="margin-top:8px">Your payment succeeded, but confirmation is taking longer than usual. It'll appear in your bookings shortly.</p>
            <div class="conf-actions">
              <a routerLink="/bookings" class="btn btn-primary" style="text-decoration:none">Go to my bookings</a>
            </div>
          </div>
        }
        @case ('failed') {
          <div class="conf-center">
            <div class="conf-check conf-check-danger">✕</div>
            <h2 style="margin-top:16px">Booking not completed</h2>
            <p class="muted" style="margin-top:8px">This booking was cancelled. If you were charged, it will be refunded automatically.</p>
            <div class="conf-actions">
              <a routerLink="/events" class="btn btn-primary" style="text-decoration:none">Browse events</a>
            </div>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .conf-center { text-align: center; padding: 60px 0; }
    .conf-check {
      width: 64px; height: 64px; margin: 0 auto; border-radius: 50%;
      background: var(--success, #2e7d32); color: #fff;
      display: flex; align-items: center; justify-content: center; font-size: 32px;
    }
    .conf-check-muted { background: var(--bg-sunk); color: var(--ink-3); }
    .conf-check-danger { background: var(--danger, #c0392b); color: #fff; }
    .conf-summary {
      margin: 24px auto 0; max-width: 380px; padding: 20px;
      background: var(--bg-card); border: 1px solid var(--line); border-radius: var(--radius-lg); text-align: left;
    }
    .conf-actions { display: flex; gap: 12px; justify-content: center; margin-top: 28px; }
    .muted { color: var(--ink-3); }
    .muted-2 { color: var(--ink-4); word-break: break-all; }
  `]
})
export class ConfirmationComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private apollo = inject(Apollo);

  state = signal<ConfirmState>('processing');
  booking = signal<Booking | null>(null);

  private bookingId = '';
  private attempts = 0;
  private static readonly MAX_ATTEMPTS = 15;   // ~30s at 2s intervals
  private static readonly INTERVAL_MS = 2000;
  private pollSub?: Subscription;

  ngOnInit() {
    this.bookingId = this.route.snapshot.paramMap.get('bookingId') ?? '';
    if (!this.bookingId) { this.state.set('failed'); return; }
    this.poll();
  }

  ngOnDestroy() {
    this.pollSub?.unsubscribe();
  }

  // Payment completion → booking CONFIRMED happens asynchronously via the saga, so we poll.
  private poll() {
    this.apollo.query<{ booking: Booking }>({
      query: GET_BOOKING,
      variables: { id: this.bookingId },
      fetchPolicy: 'network-only',
    }).subscribe({
      next: r => {
        const b = r.data!.booking;
        this.booking.set(b);
        if (b.status === 'CONFIRMED') { this.state.set('confirmed'); return; }
        if (b.status === 'CANCELLED') { this.state.set('failed'); return; }
        this.retryOr('timeout');   // still PENDING / PAYMENT_PROCESSING
      },
      error: () => this.retryOr('timeout'),
    });
  }

  private retryOr(finalState: ConfirmState) {
    if (++this.attempts >= ConfirmationComponent.MAX_ATTEMPTS) {
      this.state.set(finalState);
      return;
    }
    this.pollSub = timer(ConfirmationComponent.INTERVAL_MS).subscribe(() => this.poll());
  }
}
