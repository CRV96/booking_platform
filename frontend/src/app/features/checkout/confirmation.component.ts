import { Component, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Apollo } from 'apollo-angular';
import { Subscription, forkJoin, timer } from 'rxjs';
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
            <p class="muted" style="margin-top:8px">Your payment went through — we're confirming your bookings and issuing tickets.</p>
          </div>
        }
        @case ('confirmed') {
          <div class="conf-center">
            <div class="conf-check">✓</div>
            <div class="kicker" style="margin-top:16px">Confirmed</div>
            <h2 style="margin-top:4px">You're going! 🎉</h2>
            <div class="conf-summary">
              @for (b of bookings(); track b.id) {
                <div class="conf-line">
                  <span>{{ b.eventTitle }} <span class="mono xs muted">· {{ b.seatCategory }} · {{ b.quantity }}×</span></span>
                  <span class="mono xs muted">{{ b.currency }} {{ b.totalPrice }}</span>
                </div>
              }
            </div>
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
            <p class="muted" style="margin-top:8px">Your payment succeeded, but confirmation is taking longer than usual. Your bookings will appear shortly.</p>
            <div class="conf-actions">
              <a routerLink="/bookings" class="btn btn-primary" style="text-decoration:none">Go to my bookings</a>
            </div>
          </div>
        }
        @case ('failed') {
          <div class="conf-center">
            <div class="conf-check conf-check-danger">✕</div>
            <h2 style="margin-top:16px">Order not completed</h2>
            <p class="muted" style="margin-top:8px">Some bookings were cancelled. If you were charged, it will be refunded automatically.</p>
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
      margin: 24px auto 0; max-width: 420px; padding: 8px 20px;
      background: var(--bg-card); border: 1px solid var(--line); border-radius: var(--radius-lg); text-align: left;
    }
    .conf-line { display: flex; justify-content: space-between; gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--line); font-size: 14px; }
    .conf-line:last-child { border-bottom: none; }
    .conf-actions { display: flex; gap: 12px; justify-content: center; margin-top: 28px; }
    .muted { color: var(--ink-3); }
  `]
})
export class ConfirmationComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private apollo = inject(Apollo);

  state = signal<ConfirmState>('processing');
  bookings = signal<Booking[]>([]);

  private bookingIds: string[] = [];
  private attempts = 0;
  private static readonly MAX_ATTEMPTS = 15;   // ~30s at 2s intervals
  private static readonly INTERVAL_MS = 2000;
  private pollSub?: Subscription;

  ngOnInit() {
    this.bookingIds = (this.route.snapshot.queryParamMap.get('bookings') ?? '')
      .split(',').map(s => s.trim()).filter(Boolean);
    if (this.bookingIds.length === 0) { this.state.set('failed'); return; }
    this.poll();
  }

  ngOnDestroy() {
    this.pollSub?.unsubscribe();
  }

  // Payment success → each booking CONFIRMED happens asynchronously via the saga, so we poll all.
  private poll() {
    const queries = this.bookingIds.map(id =>
      this.apollo.query<{ booking: Booking }>({
        query: GET_BOOKING,
        variables: { id },
        fetchPolicy: 'network-only',
      })
    );

    this.pollSub = forkJoin(queries).subscribe({
      next: results => {
        const bookings = results.map(r => r.data!.booking);
        this.bookings.set(bookings);

        if (bookings.some(b => b.status === 'CANCELLED')) { this.state.set('failed'); return; }
        if (bookings.every(b => b.status === 'CONFIRMED')) { this.state.set('confirmed'); return; }
        this.retryOr('timeout');
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
