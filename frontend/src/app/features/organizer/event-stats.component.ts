import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { DatePipe } from '@angular/common';
import { Apollo } from 'apollo-angular';
import { forkJoin } from 'rxjs';
import { GET_EVENT, GET_EVENT_BOOKINGS } from '../../shared/graphql/documents';
import { Event, Booking } from '../../shared/models/models';

/** One column of the purchases-over-time chart. */
interface DayBucket { day: string; seats: number; }

@Component({
  selector: 'app-event-stats',
  standalone: true,
  imports: [RouterLink, DatePipe],
  template: `
    <div class="container page">
      <a routerLink="/organizer" class="back-link mono xs">← Back to dashboard</a>

      @if (loading()) {
        <div class="loading-center"><div class="spinner spinner-lg"></div></div>
      } @else if (error()) {
        <div class="alert alert-error">{{ error() }}</div>
      } @else if (event(); as ev) {
        <div class="section-head">
          <div class="kicker">Event Statistics</div>
          <h2>{{ ev.title }}</h2>
          <div class="mono xs muted">{{ ev.dateTime | date:'EEE d MMM y, HH:mm' }} · {{ ev.venue.city }}</div>
        </div>

        <!-- Summary tiles -->
        <div class="stats-grid">
          <div class="stat-cell">
            <div class="stat-label">Capacity</div>
            <div class="stat-value">{{ capacity() }}</div>
            <div class="stat-hint">Total seats</div>
          </div>
          <div class="stat-cell">
            <div class="stat-label">Booked</div>
            <div class="stat-value">{{ booked() }}</div>
            <div class="stat-hint">{{ pctSold() }}% sold</div>
          </div>
          <div class="stat-cell">
            <div class="stat-label">Available</div>
            <div class="stat-value">{{ available() }}</div>
            <div class="stat-hint">Seats left</div>
          </div>
          <div class="stat-cell">
            <div class="stat-label">Revenue</div>
            <div class="stat-value">{{ money(revenue()) }}</div>
            <div class="stat-hint">Confirmed bookings</div>
          </div>
        </div>

        <div class="stats-layout">
          <!-- Left: seats by category -->
          <div>
            <div class="block-title">Seats by category</div>
            <div class="cat-list">
              @for (c of categories(); track c.name) {
                <div class="cat-row">
                  <div class="cat-head">
                    <span class="cat-name">{{ c.name }}</span>
                    <span class="mono xs muted">{{ c.booked }} / {{ c.total }} · {{ c.pct }}%</span>
                  </div>
                  <div class="cat-bar"><div class="cat-bar-fill" [style.width.%]="c.pct"></div></div>
                  <div class="cat-sub mono xs muted-2">{{ c.available }} available · {{ money(c.price) }} each</div>
                </div>
              }
            </div>

            <div class="block-title" style="margin-top:32px">Booking status</div>
            <div class="status-row">
              <span class="badge badge-live">{{ statusCount('CONFIRMED') }} confirmed</span>
              <span class="badge badge-draft">{{ statusCount('PENDING') }} pending</span>
              <span class="badge badge-danger">{{ statusCount('CANCELLED') }} cancelled</span>
            </div>
          </div>

          <!-- Right: purchases over time -->
          <div>
            <div class="block-title">Purchases over time</div>
            @if (daily().length === 0) {
              <div class="empty-mini mono xs muted">No seats booked yet.</div>
            } @else {
              <div class="chart">
                @for (d of daily(); track d.day) {
                  <div class="chart-col" [title]="(d.day | date:'d MMM y') + ' — ' + d.seats + ' seat(s)'">
                    <div class="chart-bar-wrap">
                      <span class="chart-count mono xs">{{ d.seats }}</span>
                      <div class="chart-bar" [style.height.%]="barHeight(d.seats)"></div>
                    </div>
                    <div class="chart-x mono xs muted-2">{{ d.day | date:'d MMM' }}</div>
                  </div>
                }
              </div>
              <div class="mono xs muted-2" style="margin-top:8px">Seats booked per day (excludes cancelled)</div>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .back-link { display: inline-block; margin-bottom: 20px; color: var(--ink-3); text-decoration: none; }
    .back-link:hover { color: var(--ink); }

    .stats-grid {
      display: grid; grid-template-columns: repeat(4, 1fr);
      border: 1px solid var(--line); border-radius: var(--radius-lg);
      margin: 24px 0 40px; overflow: hidden;
    }
    @media (max-width: 640px) { .stats-grid { grid-template-columns: repeat(2, 1fr); } }
    .stat-cell { padding: 24px 28px; border-right: 1px solid var(--line); display: flex; flex-direction: column; gap: 4px; }
    .stat-cell:last-child { border-right: none; }
    .stat-label { font-family: var(--mono); font-size: 10px; text-transform: uppercase; letter-spacing: 0.1em; color: var(--ink-4); }
    .stat-value { font-family: var(--serif); font-size: 38px; line-height: 1; letter-spacing: -0.02em; }
    .stat-hint { font-size: 11px; color: var(--ink-4); }

    .stats-layout { display: grid; grid-template-columns: 1fr 1fr; gap: 40px; }
    @media (max-width: 860px) { .stats-layout { grid-template-columns: 1fr; } }
    .block-title { font-family: var(--serif); font-size: 18px; letter-spacing: -0.01em; margin-bottom: 16px; }

    .cat-list { display: flex; flex-direction: column; gap: 18px; }
    .cat-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 6px; }
    .cat-name { font-size: 13px; font-weight: 500; color: var(--ink); }
    .cat-bar { height: 6px; background: var(--line); border-radius: 3px; overflow: hidden; }
    .cat-bar-fill { height: 100%; background: var(--accent); border-radius: 3px; transition: width 0.3s; }
    .cat-sub { margin-top: 5px; }

    .status-row { display: flex; gap: 8px; flex-wrap: wrap; }

    .empty-mini { padding: 32px 0; }
    .chart { display: flex; align-items: flex-end; gap: 10px; height: 200px; padding-top: 8px; overflow-x: auto; }
    .chart-col { flex: 1 0 36px; display: flex; flex-direction: column; align-items: center; height: 100%; }
    .chart-bar-wrap { flex: 1; width: 100%; display: flex; flex-direction: column; justify-content: flex-end; align-items: center; gap: 4px; }
    .chart-count { color: var(--ink-3); }
    .chart-bar { width: 100%; max-width: 40px; min-height: 2px; background: var(--accent); border-radius: 3px 3px 0 0; transition: height 0.3s; }
    .chart-x { margin-top: 8px; white-space: nowrap; }
  `]
})
export class EventStatsComponent implements OnInit {
  private apollo = inject(Apollo);
  private route = inject(ActivatedRoute);

  event = signal<Event | null>(null);
  bookings = signal<Booking[]>([]);
  loading = signal(true);
  error = signal('');

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id')!;
    forkJoin({
      event: this.apollo.query<{ event: Event }>({ query: GET_EVENT, variables: { id }, fetchPolicy: 'network-only' }),
      bookings: this.apollo.query<{ eventBookings: Booking[] }>({ query: GET_EVENT_BOOKINGS, variables: { eventId: id }, fetchPolicy: 'network-only' }),
    }).subscribe({
      next: ({ event, bookings }) => {
        this.event.set(event.data!.event);
        this.bookings.set(bookings.data!.eventBookings ?? []);
        this.loading.set(false);
      },
      error: err => { this.loading.set(false); this.error.set(err.message || 'Failed to load statistics'); }
    });
  }

  private cats = computed(() => this.event()?.seatCategories ?? []);

  capacity = computed(() => this.cats().reduce((s, c) => s + (c.totalSeats ?? 0), 0));
  available = computed(() => this.cats().reduce((s, c) => s + (c.availableSeats ?? 0), 0));
  booked = computed(() => this.capacity() - this.available());
  pctSold = computed(() => this.capacity() > 0 ? Math.round((this.booked() / this.capacity()) * 100) : 0);

  categories = computed(() => this.cats().map(c => {
    const total = c.totalSeats ?? 0;
    const bk = total - (c.availableSeats ?? 0);
    return {
      name: c.name,
      total,
      available: c.availableSeats ?? 0,
      booked: bk,
      price: c.price,
      pct: total > 0 ? Math.round((bk / total) * 100) : 0,
    };
  }));

  /** Revenue from confirmed bookings only. */
  revenue = computed(() =>
    this.bookings()
      .filter(b => b.status === 'CONFIRMED')
      .reduce((s, b) => s + (parseFloat(b.totalPrice) || 0), 0)
  );

  /** Seats booked per calendar day, excluding cancelled bookings, oldest first. */
  daily = computed<DayBucket[]>(() => {
    const map = new Map<string, number>();
    for (const b of this.bookings()) {
      if (b.status === 'CANCELLED') continue;
      const day = (b.createdAt || '').substring(0, 10);
      if (!day) continue;
      map.set(day, (map.get(day) ?? 0) + b.quantity);
    }
    return [...map.entries()]
      .sort((a, b) => a[0].localeCompare(b[0]))
      .map(([day, seats]) => ({ day, seats }));
  });

  private maxDaily = computed(() => Math.max(1, ...this.daily().map(d => d.seats)));

  barHeight(seats: number): number {
    return Math.round((seats / this.maxDaily()) * 100);
  }

  statusCount(status: string): number {
    return this.bookings().filter(b => b.status === status).length;
  }

  private currency(): string {
    return this.cats()[0]?.currency || this.bookings()[0]?.currency || 'USD';
  }

  money(amount: number | string): string {
    const n = typeof amount === 'string' ? (parseFloat(amount) || 0) : amount;
    try {
      return new Intl.NumberFormat(undefined, { style: 'currency', currency: this.currency() }).format(n);
    } catch {
      return `${this.currency()} ${n.toFixed(2)}`;
    }
  }
}
