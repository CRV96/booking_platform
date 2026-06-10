import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { Apollo } from 'apollo-angular';
import { GET_EVENTS } from '../../shared/graphql/documents';
import { Event, EventConnection } from '../../shared/models/models';
import { EventArtComponent } from '../../shared/event-art.component';

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
                <span class="badge" [class]="statusBadge(ev.status)">{{ ev.status }}</span>
              </div>
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
    </div>
  `,
  styles: [`
    .filters-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 32px; gap: 16px; flex-wrap: wrap; }
    .cat-pills { display: flex; gap: 6px; flex-wrap: wrap; }
    .cat-pill { padding: 6px 14px; font-size: 13px; border-radius: 999px; border: 1px solid var(--line); background: transparent; color: var(--ink-3); cursor: pointer; transition: all 0.15s; font-family: inherit; }
    .cat-pill:hover, .cat-pill.active { background: var(--ink); color: var(--bg); border-color: var(--ink); }
    .ev-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
    @media (max-width: 900px) { .ev-grid { grid-template-columns: repeat(2, 1fr); } }
    @media (max-width: 600px) { .ev-grid { grid-template-columns: 1fr; } }
    .pagination { display: flex; align-items: center; justify-content: center; gap: 16px; margin-top: 40px; }
  `]
})
export class EventListComponent implements OnInit {
  private apollo = inject(Apollo);
  categories = CATEGORIES;
  filters = { query: '', category: '' };
  events = signal<Event[]>([]);
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
      variables: { query: this.filters.query || null, category: this.filters.category || null, page: this.page(), pageSize: 12 }
    }).subscribe({
      next: r => { this.loading.set(false); this.connection.set(r.data.events); this.events.set(r.data.events.events); },
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
}
