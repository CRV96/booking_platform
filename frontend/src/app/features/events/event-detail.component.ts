import { Component, inject, signal, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { Apollo } from 'apollo-angular';
import { GET_EVENT } from '../../shared/graphql/documents';
import { Event, SeatCategory } from '../../shared/models/models';
import { AuthService } from '../../core/auth.service';
import { CartService } from '../../core/cart.service';
import { LovelistService } from '../../core/lovelist.service';
import { EventArtComponent } from '../../shared/event-art.component';

@Component({
  selector: 'app-event-detail',
  standalone: true,
  imports: [RouterLink, FormsModule, DatePipe, EventArtComponent],
  template: `
    <div class="container page">
      @if (loading()) {
        <div class="loading-center"><div class="spinner spinner-lg"></div></div>
      } @else if (error()) {
        <div class="alert alert-error">{{ error() }}</div>
      } @else if (event()) {
        <div style="margin-bottom:24px">
          <a routerLink="/events" class="btn btn-ghost btn-sm" style="text-decoration:none">← Back to events</a>
        </div>

        <div class="detail-layout">
          <!-- Left column -->
          <div class="detail-main">
            <app-event-art [seed]="artSeed()" [title]="event()!.title" ratio="16/7" />

            <div style="margin-top:28px">
              <div class="detail-meta-row">
                <span class="badge" [class]="statusBadge(event()!.status)">{{ event()!.status }}</span>
                <span class="mono xs muted" style="margin-left:8px">{{ event()!.category }}</span>
                <button type="button" class="detail-love" [class.loved]="lovelist.isLoved(event()!.id)"
                        (click)="toggleLove()"
                        [attr.aria-pressed]="lovelist.isLoved(event()!.id)"
                        [attr.aria-label]="lovelist.isLoved(event()!.id) ? 'Remove from lovelist' : 'Add to lovelist'">
                  {{ lovelist.isLoved(event()!.id) ? '♥ Saved' : '♡ Save' }}
                </button>
              </div>

              <h1 class="detail-title">{{ event()!.title }}</h1>

              <div class="detail-info-grid">
                <div class="detail-info-item">
                  <div class="detail-info-label">Date</div>
                  <div class="detail-info-value">{{ event()!.dateTime | date:'EEEE, d MMMM y' }}</div>
                </div>
                <div class="detail-info-item">
                  <div class="detail-info-label">Time</div>
                  <div class="detail-info-value">{{ event()!.dateTime | date:'HH:mm' }}</div>
                </div>
                <div class="detail-info-item">
                  <div class="detail-info-label">Venue</div>
                  <div class="detail-info-value">{{ event()!.venue.name }}</div>
                  <div class="detail-info-sub">{{ event()!.venue.city }}, {{ event()!.venue.country }}</div>
                  @if (event()!.venue.address) {
                    <div class="detail-info-sub">{{ event()!.venue.address }}</div>
                  }
                </div>
                <div class="detail-info-item">
                  <div class="detail-info-label">Organizer</div>
                  <div class="detail-info-value">{{ event()!.organizer.name }}</div>
                  <div class="detail-info-sub">{{ event()!.organizer.email }}</div>
                </div>
              </div>

              @if (event()!.description) {
                <div class="detail-desc">
                  <div class="mono xs muted" style="margin-bottom:8px;text-transform:uppercase;letter-spacing:0.1em">About</div>
                  <p style="color:var(--ink-2);line-height:1.7;font-size:14px">{{ event()!.description }}</p>
                </div>
              }
            </div>
          </div>

          <!-- Right sidebar -->
          <div class="detail-sidebar">
            @if (event()!.status === 'PUBLISHED' && event()!.seatCategories.length > 0) {
              <div class="booking-card">
                <div class="mono xs muted" style="text-transform:uppercase;letter-spacing:0.1em;margin-bottom:16px">Reserve your seat</div>

                @if (!auth.isAuthenticated()) {
                  <p style="font-size:13px;color:var(--ink-3);margin-bottom:16px">
                    Please <a routerLink="/auth/login" style="color:var(--ink);text-decoration:underline">sign in</a> to book tickets.
                  </p>
                } @else {
                  <div style="margin-bottom:20px">
                    <div class="mono xs muted" style="text-transform:uppercase;letter-spacing:0.08em;margin-bottom:10px">Select category</div>
                    <div class="seat-tiles">
                      @for (cat of event()!.seatCategories; track cat.name) {
                        <div class="seat-tile" [class.selected]="selectedCategory()?.name === cat.name"
                             (click)="selectCategory(cat)">
                          <div class="seat-tile-name">{{ cat.name }}</div>
                          <div class="seat-tile-price">{{ cat.currency }} {{ cat.price }}</div>
                          <div class="seat-tile-avail">{{ cat.availableSeats }} left</div>
                        </div>
                      }
                    </div>
                  </div>

                  @if (selectedCategory()) {
                    <div class="field" style="margin-bottom:16px">
                      <label>Quantity</label>
                      <select class="inp" [(ngModel)]="quantity">
                        @for (n of [1,2,3,4,5,6,7,8]; track n) {
                          <option [value]="n">{{ n }}</option>
                        }
                      </select>
                    </div>
                    <div class="booking-total">
                      <span class="mono xs muted" style="text-transform:uppercase;letter-spacing:0.08em">Total</span>
                      <span style="font-family:var(--serif);font-size:24px">{{ selectedCategory()!.currency }} {{ total() }}</span>
                    </div>
                    <button class="btn btn-primary btn-lg btn-block" style="margin-top:16px" (click)="addToCart()">
                      Add to cart
                    </button>
                  }
                }
              </div>
            } @else if (event()!.status !== 'PUBLISHED') {
              <div class="booking-card">
                <p style="font-size:13px;color:var(--ink-3)">This event is not available for booking.</p>
              </div>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .detail-layout { display: grid; grid-template-columns: 1fr 320px; gap: 40px; align-items: start; }
    @media (max-width: 768px) { .detail-layout { grid-template-columns: 1fr; } }

    .detail-meta-row { display: flex; align-items: center; margin-bottom: 12px; }
    .detail-love {
      margin-left: auto; font-family: inherit; font-size: 13px; cursor: pointer;
      border: 1px solid var(--line); border-radius: 8px; background: transparent;
      color: var(--ink-3); padding: 6px 12px; transition: all 0.15s;
    }
    .detail-love:hover { border-color: var(--ink-4); color: var(--ink); }
    .detail-love.loved { color: #e0245e; border-color: #e0245e; }
    .detail-title { font-family: var(--serif); font-size: 42px; line-height: 1.05; letter-spacing: -0.02em; margin-bottom: 28px; }

    .detail-info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0; border-top: 1px solid var(--line); margin-bottom: 28px; }
    .detail-info-item { padding: 16px 0; border-bottom: 1px solid var(--line); }
    .detail-info-item:nth-child(odd) { padding-right: 24px; border-right: 1px solid var(--line); }
    .detail-info-item:nth-child(even) { padding-left: 24px; }
    .detail-info-label { font-family: var(--mono); font-size: 10px; text-transform: uppercase; letter-spacing: 0.1em; color: var(--ink-4); margin-bottom: 4px; }
    .detail-info-value { font-size: 14px; font-weight: 500; color: var(--ink); }
    .detail-info-sub { font-size: 12px; color: var(--ink-3); margin-top: 2px; }

    .detail-desc { padding-top: 20px; border-top: 1px solid var(--line); }

    .booking-card {
      position: sticky; top: 88px;
      background: var(--bg-card); border: 1px solid var(--line);
      border-radius: var(--radius-lg); padding: 20px;
    }

    .seat-tiles { display: flex; flex-direction: column; gap: 8px; }
    .seat-tile {
      padding: 12px 14px; border: 1px solid var(--line);
      border-radius: var(--radius); cursor: pointer; transition: all 0.15s;
    }
    .seat-tile:hover { border-color: var(--ink-4); }
    .seat-tile.selected { border-color: var(--ink); background: var(--bg-sunk); }
    .seat-tile-name { font-size: 13px; font-weight: 500; color: var(--ink); }
    .seat-tile-price { font-family: var(--serif); font-size: 18px; margin-top: 2px; }
    .seat-tile-avail { font-family: var(--mono); font-size: 10px; color: var(--ink-4); text-transform: uppercase; letter-spacing: 0.06em; margin-top: 4px; }

    .booking-total { display: flex; flex-direction: column; gap: 4px; padding-top: 16px; border-top: 1px solid var(--line); }
  `]
})
export class EventDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private apollo = inject(Apollo);
  auth = inject(AuthService);
  private cart = inject(CartService);
  lovelist = inject(LovelistService);
  private router = inject(Router);

  event = signal<Event | null>(null);
  loading = signal(false);
  error = signal('');
  selectedCategory = signal<SeatCategory | null>(null);
  quantity = 1;

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.loading.set(true);
    this.apollo.query<{ event: Event }>({ query: GET_EVENT, variables: { id } })
      .subscribe({
        next: r => { this.loading.set(false); this.event.set(r.data!.event); },
        error: err => { this.loading.set(false); this.error.set(err.message || 'Event not found'); }
      });
  }

  selectCategory(cat: SeatCategory) { this.selectedCategory.set(cat); }

  total() {
    const cat = this.selectedCategory();
    if (!cat) return '0.00';
    return (parseFloat(cat.price) * this.quantity).toFixed(2);
  }

  artSeed(): number {
    const ev = this.event();
    if (!ev?.id) return 1;
    return ev.id.split('').reduce((a, c) => a + c.charCodeAt(0), 0) % 100;
  }

  addToCart() {
    const ev = this.event()!;
    const cat = this.selectedCategory()!;
    // Add to the cart (same event+category replaces its line). No seats are reserved yet —
    // the bookings (and seat holds) are created at checkout.
    this.cart.add({
      eventId: ev.id,
      eventTitle: ev.title,
      seatCategory: cat.name,
      unitPrice: cat.price,
      currency: cat.currency,
      quantity: Number(this.quantity),
    });
    this.router.navigate(['/cart']);
  }

  toggleLove() {
    const ev = this.event()!;
    this.lovelist.toggle({
      eventId: ev.id,
      title: ev.title,
      category: ev.category,
      city: ev.venue.city,
      dateTime: ev.dateTime,
    });
  }

  statusBadge(status: string) {
    return ({ PUBLISHED: 'badge badge-live', DRAFT: 'badge badge-draft', CANCELLED: 'badge badge-danger', COMPLETED: 'badge' } as Record<string, string>)[status] ?? 'badge';
  }
}
