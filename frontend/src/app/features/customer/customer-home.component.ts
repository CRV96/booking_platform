import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Apollo } from 'apollo-angular';
import { GET_MY_BOOKINGS, GET_MY_TICKETS } from '../../shared/graphql/documents';
import { Booking, BookingConnection, Ticket, TicketConnection } from '../../shared/models/models';
import { AuthService } from '../../core/auth.service';
import { EventArtComponent } from '../../shared/event-art.component';

function timeOfDay(): string {
  const h = new Date().getHours();
  if (h < 12) return 'morning';
  if (h < 18) return 'afternoon';
  return 'evening';
}

@Component({
  selector: 'app-customer-home',
  standalone: true,
  imports: [RouterLink, EventArtComponent],
  template: `
    <div class="container page">
      <!-- Greeting -->
      <div class="section-head">
        <div class="kicker">Dashboard</div>
        <h2>Good {{ time }}, <em>{{ auth.user()?.firstName || auth.user()?.username }}</em>.</h2>
      </div>

      <div class="home-grid">
        <!-- Bookings panel -->
        <div class="home-panel">
          <div class="home-panel-head">
            <h3 class="home-panel-title">Recent Bookings</h3>
            <a routerLink="/bookings" class="btn btn-ghost btn-sm" style="text-decoration:none">View all →</a>
          </div>

          @if (bookingsLoading()) {
            <div class="loading-center" style="padding:30px"><div class="spinner"></div></div>
          } @else if (bookings().length === 0) {
            <div style="padding:24px 0;text-align:center;color:var(--ink-3);font-size:13px">
              <p>No bookings yet.</p>
              <a routerLink="/events" class="btn btn-primary btn-sm" style="margin-top:12px;text-decoration:none">Browse Events</a>
            </div>
          } @else {
            <div class="panel-items">
              @for (b of bookings(); track b.id) {
                <div class="panel-item">
                  <div class="panel-item-art">
                    @if (imageOf(b.event?.images); as img) {
                      <img class="panel-item-img" [src]="img" [alt]="b.eventTitle">
                    } @else {
                      <app-event-art [seed]="artSeed(b.eventId)" [title]="b.eventTitle" ratio="1/1" />
                    }
                  </div>
                  <div class="panel-item-info">
                    <a [routerLink]="['/events', b.eventId]" class="panel-item-title" style="text-decoration:none">{{ b.eventTitle }}</a>
                    <div class="row gap-6" style="margin-top:4px;flex-wrap:wrap">
                      <span class="mono xs muted">{{ b.seatCategory }}</span>
                      <span class="mono xs muted-2">·</span>
                      <span class="mono xs muted">{{ b.quantity }} ticket{{ b.quantity > 1 ? 's' : '' }}</span>
                      <span class="mono xs muted-2">·</span>
                      <span class="mono xs muted">{{ b.currency }} {{ b.totalPrice }}</span>
                    </div>
                  </div>
                  <span class="badge" [class]="statusBadge(b.status)">{{ b.status }}</span>
                </div>
              }
            </div>
            @if (totalBookings() > 5) {
              <div style="padding-top:12px;border-top:1px solid var(--line);font-size:12px;color:var(--ink-3)">
                +{{ totalBookings() - 5 }} more · <a routerLink="/bookings" style="color:var(--ink);text-decoration:underline">See all bookings</a>
              </div>
            }
          }
        </div>

        <!-- Tickets panel -->
        <div class="home-panel">
          <div class="home-panel-head">
            <h3 class="home-panel-title">My Tickets</h3>
            <a routerLink="/tickets" class="btn btn-ghost btn-sm" style="text-decoration:none">View all →</a>
          </div>

          @if (ticketsLoading()) {
            <div class="loading-center" style="padding:30px"><div class="spinner"></div></div>
          } @else if (tickets().length === 0) {
            <div style="padding:24px 0;text-align:center;color:var(--ink-3);font-size:13px">
              <p>No tickets yet. Tickets appear once your booking is confirmed.</p>
            </div>
          } @else {
            <div class="panel-items">
              @for (t of tickets(); track t.id) {
                <div class="panel-item">
                  <div class="panel-item-info">
                    <div class="panel-item-title">{{ t.eventTitle }}</div>
                    <div class="mono xs muted" style="margin-top:4px">{{ t.ticketNumber }}</div>
                    <div class="mono xs muted-2" style="margin-top:2px">{{ t.seatCategory }}</div>
                  </div>
                  <span class="badge" [class]="ticketBadge(t.status)">{{ t.status }}</span>
                </div>
              }
            </div>
            @if (totalTickets() > 5) {
              <div style="padding-top:12px;border-top:1px solid var(--line);font-size:12px;color:var(--ink-3)">
                +{{ totalTickets() - 5 }} more · <a routerLink="/tickets" style="color:var(--ink);text-decoration:underline">See all tickets</a>
              </div>
            }
          }
        </div>
      </div>

      <!-- CTA -->
      <div class="cta-band">
        <div>
          <div class="mono xs muted" style="text-transform:uppercase;letter-spacing:0.1em;margin-bottom:8px">Discover</div>
          <div style="font-family:var(--serif);font-size:28px;letter-spacing:-0.01em">Find your next <em style="font-style:italic;color:var(--accent-ink)">experience</em></div>
          <div style="font-size:13px;color:var(--ink-3);margin-top:6px">Concerts, dinners, talks and more across six cities.</div>
        </div>
        <a routerLink="/events" class="btn btn-primary btn-lg" style="text-decoration:none;flex-shrink:0">Browse Events →</a>
      </div>
    </div>
  `,
  styles: [`
    .home-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; margin-bottom: 40px; }
    @media (max-width: 768px) { .home-grid { grid-template-columns: 1fr; } }

    .home-panel { background: var(--bg-card); border: 1px solid var(--line); border-radius: var(--radius-lg); padding: 20px; }
    .home-panel-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .home-panel-title { font-family: var(--serif); font-size: 18px; letter-spacing: -0.01em; }

    .panel-items { display: flex; flex-direction: column; gap: 0; }
    .panel-item { display: flex; align-items: center; gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--line); }
    .panel-item:last-child { border-bottom: none; }
    .panel-item-art { width: 44px; height: 44px; flex-shrink: 0; border-radius: var(--radius); overflow: hidden; }
    .panel-item-img { width: 100%; height: 100%; object-fit: cover; display: block; }
    .panel-item-info { flex: 1; min-width: 0; }
    .panel-item-title { font-size: 13px; font-weight: 500; color: var(--ink); display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .panel-item-title:hover { color: var(--ink-2); }

    .cta-band {
      display: flex; justify-content: space-between; align-items: center; gap: 24px;
      padding: 32px 36px; background: var(--bg-card);
      border: 1px solid var(--line); border-radius: var(--radius-lg);
    }
    @media (max-width: 600px) { .cta-band { flex-direction: column; align-items: flex-start; } }
  `]
})
export class CustomerHomeComponent implements OnInit {
  private apollo = inject(Apollo);
  auth = inject(AuthService);
  time = timeOfDay();

  bookings = signal<Booking[]>([]);
  tickets = signal<Ticket[]>([]);
  totalBookings = signal(0);
  totalTickets = signal(0);
  bookingsLoading = signal(false);
  ticketsLoading = signal(false);

  ngOnInit() {
    this.loadBookings();
    this.loadTickets();
  }

  private loadBookings() {
    this.bookingsLoading.set(true);
    this.apollo.query<{ myBookings: BookingConnection }>({
      query: GET_MY_BOOKINGS,
      variables: { page: 0, pageSize: 5 }
    }).subscribe({
      next: r => {
        this.bookingsLoading.set(false);
        this.bookings.set(r.data!.myBookings.bookings);
        this.totalBookings.set(r.data!.myBookings.totalCount);
      },
      error: () => this.bookingsLoading.set(false)
    });
  }

  private loadTickets() {
    this.ticketsLoading.set(true);
    this.apollo.query<{ myTickets: TicketConnection }>({
      query: GET_MY_TICKETS,
      variables: { page: 0, pageSize: 5 }
    }).subscribe({
      next: r => {
        this.ticketsLoading.set(false);
        this.tickets.set(r.data!.myTickets.tickets);
        this.totalTickets.set(r.data!.myTickets.totalCount);
      },
      error: () => this.ticketsLoading.set(false)
    });
  }

  imageOf(images?: string[]): string | null {
    return images?.[0]?.trim() || null;
  }

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

  ticketBadge(s: string) {
    return ({ VALID: 'badge badge-success', USED: 'badge', CANCELLED: 'badge badge-danger' } as Record<string, string>)[s] ?? 'badge';
  }
}
