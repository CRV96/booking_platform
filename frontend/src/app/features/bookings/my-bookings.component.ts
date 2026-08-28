import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { Apollo } from 'apollo-angular';
import { GET_MY_BOOKINGS, CANCEL_BOOKING } from '../../shared/graphql/documents';
import { Booking, BookingConnection } from '../../shared/models/models';
import { EventArtComponent } from '../../shared/event-art.component';

const STATUSES = ['PENDING','PAYMENT_PROCESSING','CONFIRMED','CANCELLED','REFUND_PENDING','REFUNDED'];

@Component({
  selector: 'app-my-bookings',
  standalone: true,
  imports: [RouterLink, DatePipe, EventArtComponent],
  template: `
    <div class="container page">
      <div class="section-head" style="margin-bottom:32px">
        <div class="kicker">Account</div>
        <h2>My <em>Bookings</em></h2>
      </div>

      <!-- Tab strip -->
      <div class="tab-strip">
        <button class="tab-btn" [class.active]="activeTab() === 'upcoming'" (click)="setTab('upcoming')">Upcoming</button>
        <button class="tab-btn" [class.active]="activeTab() === 'past'" (click)="setTab('past')">Past</button>
      </div>

      @if (loading()) {
        <div class="loading-center"><div class="spinner spinner-lg"></div></div>
      } @else if (error()) {
        <div class="alert alert-error">{{ error() }}</div>
      } @else if (bookings().length === 0) {
        <div class="empty-state">
          <h3>No bookings yet</h3>
          <p>Browse <a routerLink="/events" style="color:var(--ink);text-decoration:underline">upcoming events</a> and book your spot!</p>
        </div>
      } @else {
        <div class="bookings-list">
          @for (b of bookings(); track b.id) {
            <div class="booking-row">
              <div class="booking-thumb">
                <app-event-art [seed]="artSeed(b.eventId)" [title]="b.eventTitle" ratio="16/10" />
              </div>
              <div class="booking-info">
                <a [routerLink]="['/events', b.eventId]" class="booking-title" style="text-decoration:none">{{ b.eventTitle }}</a>
                <div class="booking-meta">
                  <span class="mono xs muted">{{ b.seatCategory }}</span>
                  <span class="mono xs muted-2">·</span>
                  <span class="mono xs muted">{{ b.quantity }} ticket{{ b.quantity > 1 ? 's' : '' }}</span>
                  <span class="mono xs muted-2">·</span>
                  <span class="mono xs muted">{{ b.currency }} {{ b.totalPrice }}</span>
                </div>
                <div class="booking-id mono xs muted-2">{{ b.id }}</div>
              </div>
              <div class="booking-right">
                <span class="badge" [class]="statusBadge(b.status)">{{ b.status }}</span>
                <div class="mono xs muted" style="margin-top:6px">{{ b.createdAt | date:'d MMM y' }}</div>
                @if (b.status === 'PENDING' || b.status === 'CONFIRMED') {
                  <button class="btn btn-danger btn-sm" style="margin-top:10px" (click)="cancel(b)" [disabled]="cancelling() === b.id">
                    @if (cancelling() === b.id) { <span class="spinner"></span> } Cancel
                  </button>
                }
              </div>
            </div>
          }
        </div>

        @if ((connection()?.totalPages ?? 0) > 1) {
          <div class="pagination">
            <button class="btn btn-ghost btn-sm" [disabled]="page() === 0" (click)="prevPage()">← Prev</button>
            <span class="mono xs muted">{{ page() + 1 }} / {{ connection()?.totalPages }}</span>
            <button class="btn btn-ghost btn-sm" [disabled]="page() + 1 >= (connection()?.totalPages ?? 0)" (click)="nextPage()">Next →</button>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .tab-strip { display: flex; gap: 0; border-bottom: 1px solid var(--line); margin-bottom: 28px; }
    .tab-btn { padding: 10px 20px; font-size: 13px; font-weight: 500; color: var(--ink-3); background: none; border: none; border-bottom: 2px solid transparent; cursor: pointer; transition: all 0.15s; font-family: inherit; margin-bottom: -1px; }
    .tab-btn:hover { color: var(--ink); }
    .tab-btn.active { color: var(--ink); border-bottom-color: var(--ink); }

    .bookings-list { display: flex; flex-direction: column; gap: 0; }
    .booking-row { display: flex; gap: 20px; align-items: flex-start; padding: 20px 0; border-bottom: 1px solid var(--line); }
    .booking-row:last-child { border-bottom: none; }
    .booking-thumb { width: 180px; flex-shrink: 0; border-radius: var(--radius); overflow: hidden; }
    .booking-info { flex: 1; min-width: 0; }
    .booking-title { font-family: var(--serif); font-size: 20px; line-height: 1.2; color: var(--ink); display: block; margin-bottom: 8px; }
    .booking-title:hover { color: var(--ink-2); }
    .booking-meta { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
    .booking-id { margin-top: 4px; word-break: break-all; }
    .booking-right { flex-shrink: 0; text-align: right; display: flex; flex-direction: column; align-items: flex-end; }
    .pagination { display: flex; align-items: center; justify-content: center; gap: 16px; margin-top: 32px; }
  `]
})
export class MyBookingsComponent implements OnInit {
  private apollo = inject(Apollo);
  activeTab = signal<'upcoming' | 'past'>('upcoming');
  bookings = signal<Booking[]>([]);
  connection = signal<BookingConnection | null>(null);
  loading = signal(false);
  error = signal('');
  cancelling = signal('');
  page = signal(0);

  ngOnInit() { this.load(); }

  setTab(tab: 'upcoming' | 'past') {
    this.activeTab.set(tab);
    this.page.set(0);
    this.load();
  }

  private static readonly UPCOMING_STATUSES = new Set(['PENDING', 'PAYMENT_PROCESSING', 'CONFIRMED']);

  load() {
    this.loading.set(true);
    this.error.set('');
    this.apollo.query<{ myBookings: BookingConnection }>({
      query: GET_MY_BOOKINGS,
      variables: { page: this.page(), pageSize: 50 },
      fetchPolicy: 'network-only',
    }).subscribe({
      next: r => {
        this.loading.set(false);
        const all = r.data!.myBookings.bookings;
        const isUpcoming = this.activeTab() === 'upcoming';
        this.bookings.set(all.filter((b: Booking) =>
          isUpcoming
            ? MyBookingsComponent.UPCOMING_STATUSES.has(b.status)
            : !MyBookingsComponent.UPCOMING_STATUSES.has(b.status)
        ));
        this.connection.set(r.data!.myBookings);
      },
      error: err => { this.loading.set(false); this.error.set(err.message || 'Failed to load'); }
    });
  }

  cancel(b: Booking) {
    if (!confirm(`Cancel booking for "${b.eventTitle}"?`)) return;
    this.cancelling.set(b.id);
    this.apollo.mutate({ mutation: CANCEL_BOOKING, variables: { id: b.id } }).subscribe({
      next: () => { this.cancelling.set(''); this.load(); },
      error: err => { this.cancelling.set(''); alert(err.message || 'Cancel failed'); }
    });
  }

  prevPage() { this.page.update(p => p - 1); this.load(); }
  nextPage() { this.page.update(p => p + 1); this.load(); }

  artSeed(eventId: string): number {
    return eventId ? eventId.split('').reduce((a, c) => a + c.charCodeAt(0), 0) % 100 : 1;
  }

  statusBadge(s: string) {
    const m: Record<string, string> = {
      CONFIRMED: 'badge badge-success', PENDING: 'badge badge-accent',
      CANCELLED: 'badge badge-danger', REFUNDED: 'badge',
      PAYMENT_PROCESSING: 'badge badge-accent', REFUND_PENDING: 'badge badge-accent',
    };
    return m[s] ?? 'badge';
  }
}
