import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { Apollo } from 'apollo-angular';
import { GET_EVENTS } from '../../shared/graphql/documents';
import { Event, EventConnection, SeatCategory } from '../../shared/models/models';
import { EventArtComponent } from '../../shared/event-art.component';
import { AuthService } from '../../core/auth.service';
import { CartService } from '../../core/cart.service';
import { LovelistService } from '../../core/lovelist.service';

const CATEGORIES = [
  { id: '', label: 'All' },
  { id: 'CONCERT', label: 'Music' },
  { id: 'SPORTS', label: 'Sport' },
  { id: 'THEATRE', label: 'Theatre' },
  { id: 'CONFERENCE', label: 'Talks' },
  { id: 'FESTIVAL', label: 'Festival' },
  { id: 'OTHER', label: 'Other' },
];

@Component({
  selector: 'app-event-list',
  standalone: true,
  imports: [RouterLink, FormsModule, DatePipe, EventArtComponent],
  template: `
    <div class="container" style="padding-top:48px;padding-bottom:60px">
      <!-- Section head -->
      <div class="section-head">
        <div class="kicker">Programme</div>
        <h2>Upcoming <em>events</em></h2>
        <p class="sub">Concerts, dinners, talks and more — curated across six cities.</p>
      </div>

      <!-- Filters -->
      <div class="filters-row">
        <div class="cat-pills">
          @for (c of categories; track c.id) {
            <button class="cat-pill" [class.active]="filters.category === c.id" (click)="setCategory(c.id)">
              {{ c.label }}
            </button>
          }
        </div>
        <div class="row gap-8">
          <input class="inp" style="width:200px" [(ngModel)]="filters.query" placeholder="Search events…" (keyup.enter)="search()">
          <button class="btn btn-primary btn-sm" (click)="search()">Search</button>
          <label class="ai-toggle" title="Also surface semantic 'smart' matches found by meaning">
            <input type="checkbox" [(ngModel)]="aiSearch" (change)="search()">
            <span>✨ AI Search</span>
          </label>
        </div>
      </div>

      @if (loading()) {
        <div class="loading-center"><div class="spinner spinner-lg"></div></div>
      } @else if (error()) {
        <div class="alert alert-error">{{ error() }}</div>
      } @else if (events().length === 0) {
        <div class="empty-state">
          <h3>Nothing found</h3>
          <p>Try adjusting your search or clearing the filters.</p>
          <button class="btn btn-ghost btn-sm" style="margin-top:16px" (click)="clear()">Clear filters</button>
        </div>
      } @else {
        <div class="ev-grid">
          @for (ev of events(); track ev.id) {
            <a class="ev-card fade-up" [routerLink]="['/events', ev.id]">
              <app-event-art [seed]="artSeed(ev)" [title]="ev.title" />
              <div class="ev-card-meta">
                <span>{{ ev.category }}</span>
                <span class="dot"></span>
                <span>{{ ev.venue.city }}</span>
                <span class="dot"></span>
                <span>{{ ev.dateTime | date:'d MMM' }}</span>
              </div>
              <div class="ev-card-title">{{ ev.title }}</div>
              <div class="ev-card-foot">
                <div class="ev-card-price">
                  @if (minPrice(ev) === 0) { Free }
                  @else { From €{{ minPrice(ev) }}<small>/ ticket</small> }
                </div>
                @if (auth.isOrganizer()) {
                  <span class="badge" [class]="statusBadge(ev.status)">{{ ev.status }}</span>
                }
              </div>
              @if (!auth.isOrganizer()) {
                <div class="ev-card-actions">
                  <button type="button" class="ev-act ev-love" [class.loved]="lovelist.isLoved(ev.id)"
                          (click)="toggleLove(ev, $event)"
                          [attr.aria-pressed]="lovelist.isLoved(ev.id)"
                          [attr.aria-label]="lovelist.isLoved(ev.id) ? 'Remove from lovelist' : 'Add to lovelist'">
                    {{ lovelist.isLoved(ev.id) ? '♥' : '♡' }}
                  </button>
                  @if (isInCart(ev.id)) {
                    <button type="button" class="ev-act ev-cart in-cart" (click)="goToCart($event)">In cart ✓</button>
                  } @else {
                    <button type="button" class="ev-act ev-cart" [disabled]="!cheapestCategory(ev)"
                            (click)="addToCart(ev, $event)">Add to cart</button>
                  }
                </div>
              }
            </a>
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

      <!-- ✨ Smart Results: semantic matches the keyword search didn't surface. -->
      @if (!loading() && !error() && aiSearch && smartResults().length > 0) {
        <div class="section-head" style="margin-top:56px">
          <div class="kicker">✨ Smart results</div>
          <h2>You might also <em>like</em></h2>
          <p class="sub">Found by meaning — matches your keyword search didn't surface.</p>
        </div>
        <div class="ev-grid">
          @for (ev of smartResults(); track ev.id) {
            <a class="ev-card fade-up" [routerLink]="['/events', ev.id]">
              <app-event-art [seed]="artSeed(ev)" [title]="ev.title" />
              <div class="ev-card-meta">
                <span>{{ ev.category }}</span>
                <span class="dot"></span>
                <span>{{ ev.venue.city }}</span>
                <span class="dot"></span>
                <span>{{ ev.dateTime | date:'d MMM' }}</span>
              </div>
              <div class="ev-card-title">{{ ev.title }}</div>
              <div class="ev-card-foot">
                <div class="ev-card-price">
                  @if (minPrice(ev) === 0) { Free }
                  @else { From €{{ minPrice(ev) }}<small>/ ticket</small> }
                </div>
                @if (auth.isOrganizer()) {
                  <span class="badge" [class]="statusBadge(ev.status)">{{ ev.status }}</span>
                }
              </div>
              @if (!auth.isOrganizer()) {
                <div class="ev-card-actions">
                  <button type="button" class="ev-act ev-love" [class.loved]="lovelist.isLoved(ev.id)"
                          (click)="toggleLove(ev, $event)"
                          [attr.aria-pressed]="lovelist.isLoved(ev.id)"
                          [attr.aria-label]="lovelist.isLoved(ev.id) ? 'Remove from lovelist' : 'Add to lovelist'">
                    {{ lovelist.isLoved(ev.id) ? '♥' : '♡' }}
                  </button>
                  @if (isInCart(ev.id)) {
                    <button type="button" class="ev-act ev-cart in-cart" (click)="goToCart($event)">In cart ✓</button>
                  } @else {
                    <button type="button" class="ev-act ev-cart" [disabled]="!cheapestCategory(ev)"
                            (click)="addToCart(ev, $event)">Add to cart</button>
                  }
                </div>
              }
            </a>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .filters-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 32px; gap: 16px; flex-wrap: wrap; }
    .cat-pills { display: flex; gap: 6px; flex-wrap: wrap; }
    .cat-pill { padding: 6px 14px; font-size: 13px; border-radius: 999px; border: 1px solid var(--line); background: transparent; color: var(--ink-3); cursor: pointer; transition: all 0.15s; font-family: inherit; }
    .cat-pill:hover, .cat-pill.active { background: var(--ink); color: var(--bg); border-color: var(--ink); }
    .ai-toggle { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; color: var(--ink-3); cursor: pointer; user-select: none; white-space: nowrap; }
    .ai-toggle input { accent-color: var(--ink); cursor: pointer; margin: 0; }
    .ev-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
    @media (max-width: 900px) { .ev-grid { grid-template-columns: repeat(2, 1fr); } }
    @media (max-width: 600px) { .ev-grid { grid-template-columns: 1fr; } }
    .pagination { display: flex; align-items: center; justify-content: center; gap: 16px; margin-top: 40px; }
    /* Push the price + action buttons to the bottom so they align across the row regardless of title length. */
    .ev-card-foot { margin-top: auto; }
    .ev-card-actions { display: flex; align-items: center; gap: 8px; }
    .ev-act {
      font-family: inherit; font-size: 13px; cursor: pointer;
      border: 1px solid var(--line); border-radius: 8px; background: transparent;
      color: var(--ink-3); transition: all 0.15s;
    }
    .ev-act:hover { border-color: var(--ink-4); color: var(--ink); }
    .ev-act:disabled { opacity: 0.45; cursor: not-allowed; }
    .ev-love {
      width: 36px; height: 36px; flex-shrink: 0; font-size: 16px; line-height: 1;
      display: inline-flex; align-items: center; justify-content: center;
    }
    .ev-love.loved { color: #e0245e; border-color: #e0245e; }
    .ev-cart { flex: 1; height: 36px; padding: 0 12px; }
    .ev-cart.in-cart { color: var(--ink); border-color: var(--ink-4); }
  `]
})
export class EventListComponent implements OnInit {
  private apollo = inject(Apollo);
  private router = inject(Router);
  auth = inject(AuthService);
  cart = inject(CartService);
  lovelist = inject(LovelistService);
  /** Event ids currently in the cart — for the "In cart ✓" state on each card. */
  private cartEventIds = computed(() => new Set(this.cart.items().map(i => i.eventId)));
  categories = CATEGORIES;
  filters = { query: '', category: '' };
  aiSearch = false;
  events = signal<Event[]>([]);
  smartResults = signal<Event[]>([]);
  connection = signal<EventConnection | null>(null);
  loading = signal(false);
  error = signal('');
  page = signal(0);

  ngOnInit() { this.load(); }

  setCategory(cat: string) { this.filters.category = cat; this.page.set(0); this.load(); }
  search() { this.page.set(0); this.load(); }
  clear() { this.filters = { query: '', category: '' }; this.page.set(0); this.load(); }
  prevPage() { this.page.update(p => p - 1); this.load(); }
  nextPage() { this.page.update(p => p + 1); this.load(); }

  private load() {
    this.loading.set(true);
    this.error.set('');
    this.apollo.query<{ events: EventConnection }>({
      query: GET_EVENTS,
      variables: { query: this.filters.query || null, category: this.filters.category || null, page: this.page(), pageSize: 12, aiSearch: this.aiSearch }
    }).subscribe({
      next: r => {
        this.loading.set(false);
        this.connection.set(r.data!.events);
        this.events.set(r.data!.events.events);
        this.smartResults.set(r.data!.events.smartResults ?? []);
      },
      error: err => { this.loading.set(false); this.error.set(err.message || 'Failed to load events'); }
    });
  }

  artSeed(ev: Event): number {
    return ev.id ? ev.id.split('').reduce((a, c) => a + c.charCodeAt(0), 0) % 100 : 1;
  }

  minPrice(ev: Event): number {
    if (!ev.seatCategories?.length) return 0;
    return Math.min(...ev.seatCategories.map(s => parseFloat(s.price)));
  }

  statusBadge(status: string): string {
    return ({ PUBLISHED: 'badge badge-live', DRAFT: 'badge badge-draft', CANCELLED: 'badge badge-danger', COMPLETED: 'badge' } as Record<string, string>)[status] ?? 'badge';
  }

  /** The lowest-priced seat category, used for one-click "Add to cart" from the listing. */
  cheapestCategory(ev: Event): SeatCategory | null {
    if (!ev.seatCategories?.length) return null;
    return ev.seatCategories.reduce((min, s) => parseFloat(s.price) < parseFloat(min.price) ? s : min);
  }

  isInCart(eventId: string): boolean {
    return this.cartEventIds().has(eventId);
  }

  addToCart(ev: Event, e: MouseEvent): void {
    e.preventDefault();
    e.stopPropagation();
    if (!this.auth.isAuthenticated()) { this.router.navigate(['/auth/login']); return; }
    const cat = this.cheapestCategory(ev);
    if (!cat) return;
    // Cheapest category, qty 1 — no seats are reserved yet; the booking is created at checkout.
    this.cart.add({
      eventId: ev.id,
      eventTitle: ev.title,
      seatCategory: cat.name,
      unitPrice: cat.price,
      currency: cat.currency,
      quantity: 1,
    });
  }

  goToCart(e: MouseEvent): void {
    e.preventDefault();
    e.stopPropagation();
    this.router.navigate(['/cart']);
  }

  toggleLove(ev: Event, e: MouseEvent): void {
    e.preventDefault();
    e.stopPropagation();
    if (!this.auth.isAuthenticated()) { this.router.navigate(['/auth/login']); return; }
    this.lovelist.toggle(ev.id);
  }
}
