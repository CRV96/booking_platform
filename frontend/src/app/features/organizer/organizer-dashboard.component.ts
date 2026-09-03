import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { Apollo } from 'apollo-angular';
import { GET_EVENTS, PUBLISH_EVENT, CANCEL_EVENT } from '../../shared/graphql/documents';
import { Event, EventConnection } from '../../shared/models/models';
import { AuthService } from '../../core/auth.service';
import { EventArtComponent } from '../../shared/event-art.component';

function timeOfDay(): string {
  const h = new Date().getHours();
  if (h < 12) return 'morning';
  if (h < 18) return 'afternoon';
  return 'evening';
}

@Component({
  selector: 'app-organizer-dashboard',
  standalone: true,
  imports: [RouterLink, DatePipe, EventArtComponent],
  template: `
    <div class="container page">
      <!-- Greeting -->
      <div class="section-head">
        <div class="kicker">Organizer Dashboard</div>
        <h2>Good {{ time }}, <em>{{ auth.user()?.firstName || auth.user()?.username }}</em>.</h2>
      </div>

      <!-- Stats grid -->
      <div class="stats-grid">
        <div class="stat-cell">
          <div class="stat-label">Total events</div>
          <div class="stat-value">{{ totalEvents() }}</div>
          <div class="stat-hint">All time</div>
        </div>
        <div class="stat-cell">
          <div class="stat-label">Published</div>
          <div class="stat-value">{{ publishedCount() }}</div>
          <div class="stat-hint">Live now</div>
        </div>
        <div class="stat-cell">
          <div class="stat-label">Drafts</div>
          <div class="stat-value">{{ draftCount() }}</div>
          <div class="stat-hint">Unpublished</div>
        </div>
        <div class="stat-cell">
          <div class="stat-label">Cancelled</div>
          <div class="stat-value">{{ cancelledCount() }}</div>
          <div class="stat-hint">All time</div>
        </div>
      </div>

      @if (loading()) {
        <div class="loading-center"><div class="spinner spinner-lg"></div></div>
      } @else if (error()) {
        <div class="alert alert-error">{{ error() }}</div>
      } @else {
        <div class="dash-layout">
          <!-- Left: Events list -->
          <div class="events-col">
            <div class="row" style="justify-content:space-between;margin-bottom:20px">
              <div style="font-family:var(--serif);font-size:20px;letter-spacing:-0.01em">Your Events</div>
              <a routerLink="/organizer/events/new" class="btn btn-primary btn-sm" style="text-decoration:none">+ New Event</a>
            </div>

            @if (events().length === 0) {
              <div class="empty-state" style="padding:48px 20px">
                <h3>No events yet</h3>
                <p>Create your first event to get started.</p>
                <a routerLink="/organizer/events/new" class="btn btn-primary btn-sm" style="margin-top:16px;text-decoration:none">Create Event</a>
              </div>
            } @else {
              <div class="events-list">
                @for (ev of events(); track ev.id) {
                  <div class="event-row">
                    <div class="event-row-art">
                      @if (imageOf(ev.images); as img) {
                        <img class="row-img" [src]="img" [alt]="ev.title">
                      } @else {
                        <app-event-art [seed]="artSeed(ev)" [title]="ev.title" ratio="1/1" />
                      }
                    </div>
                    <div class="event-row-info">
                      <div class="event-row-title">{{ ev.title }}</div>
                      <div class="event-row-meta">
                        <span class="mono xs muted">{{ ev.category }}</span>
                        <span class="mono xs muted-2">·</span>
                        <span class="mono xs muted">{{ ev.dateTime | date:'d MMM y' }}</span>
                        <span class="mono xs muted-2">·</span>
                        <span class="mono xs muted">{{ ev.venue.city }}</span>
                      </div>
                      <!-- Sell-through bar placeholder -->
                      <div class="sell-bar">
                        <div class="sell-bar-fill" [style.width]="sellThrough(ev) + '%'"></div>
                      </div>
                    </div>
                    <div class="event-row-right">
                      <span class="badge" [class]="statusBadge(ev.status)">{{ ev.status }}</span>
                      <div class="event-row-actions">
                        <a [routerLink]="['/organizer/events', ev.id, 'edit']" class="btn btn-ghost btn-sm" style="text-decoration:none">Edit</a>
                        <a [routerLink]="['/events', ev.id]" class="btn btn-ghost btn-sm" style="text-decoration:none">View</a>
                        @if (ev.status === 'DRAFT') {
                          <button class="btn btn-primary btn-sm" (click)="publish(ev)" [disabled]="acting() === ev.id">
                            @if (acting() === ev.id) { <span class="spinner"></span> } Publish
                          </button>
                        }
                        @if (ev.status === 'DRAFT' || ev.status === 'PUBLISHED') {
                          <button class="btn btn-danger btn-sm" (click)="cancel(ev)" [disabled]="acting() === ev.id">Cancel</button>
                        }
                      </div>
                    </div>
                  </div>
                }
              </div>
            }
          </div>

          <!-- Right: Quick actions -->
          <div class="actions-col">
            <div style="font-family:var(--serif);font-size:20px;letter-spacing:-0.01em;margin-bottom:20px">Quick Actions</div>
            <div class="action-list">
              <a routerLink="/organizer/tickets" class="action-item" style="text-decoration:none">
                <div class="action-icon">✓</div>
                <div>
                  <div class="action-title">Scan Tickets</div>
                  <div class="action-sub">Validate entry at the venue</div>
                </div>
              </a>
              <a routerLink="/organizer/events/new" class="action-item" style="text-decoration:none">
                <div class="action-icon">+</div>
                <div>
                  <div class="action-title">Create Event</div>
                  <div class="action-sub">Set up a new event</div>
                </div>
              </a>
              <div class="action-item action-disabled">
                <div class="action-icon">↓</div>
                <div>
                  <div class="action-title">Export Report</div>
                  <div class="action-sub mono xs" style="color:var(--ink-4)">Coming soon</div>
                </div>
              </div>
              <div class="action-item action-disabled">
                <div class="action-icon">👥</div>
                <div>
                  <div class="action-title">Manage Team</div>
                  <div class="action-sub mono xs" style="color:var(--ink-4)">Coming soon</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .stats-grid {
      display: grid; grid-template-columns: repeat(4, 1fr);
      border: 1px solid var(--line); border-radius: var(--radius-lg);
      margin-bottom: 40px; overflow: hidden;
    }
    @media (max-width: 640px) { .stats-grid { grid-template-columns: repeat(2, 1fr); } }
    .stat-cell {
      padding: 24px 28px; border-right: 1px solid var(--line);
      display: flex; flex-direction: column; gap: 4px;
    }
    .stat-cell:last-child { border-right: none; }
    .stat-label { font-family: var(--mono); font-size: 10px; text-transform: uppercase; letter-spacing: 0.1em; color: var(--ink-4); }
    .stat-value { font-family: var(--serif); font-size: 42px; line-height: 1; letter-spacing: -0.02em; }
    .stat-hint { font-size: 11px; color: var(--ink-4); }

    .dash-layout { display: grid; grid-template-columns: 1fr 240px; gap: 32px; }
    @media (max-width: 900px) { .dash-layout { grid-template-columns: 1fr; } }

    .events-list { display: flex; flex-direction: column; gap: 0; }
    .event-row {
      display: flex; gap: 16px; align-items: flex-start;
      padding: 16px 0; border-bottom: 1px solid var(--line);
    }
    .event-row:last-child { border-bottom: none; }
    .event-row-art { width: 64px; height: 64px; flex-shrink: 0; border-radius: var(--radius); overflow: hidden; }
    .row-img { width: 100%; height: 100%; object-fit: cover; display: block; }
    .event-row-info { flex: 1; min-width: 0; }
    .event-row-title { font-size: 14px; font-weight: 500; color: var(--ink); margin-bottom: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .event-row-meta { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 8px; }
    .sell-bar { height: 3px; background: var(--line); border-radius: 2px; width: 100%; max-width: 200px; }
    .sell-bar-fill { height: 100%; background: var(--accent); border-radius: 2px; transition: width 0.3s; }
    .event-row-right { flex-shrink: 0; display: flex; flex-direction: column; align-items: flex-end; gap: 8px; }
    .event-row-actions { display: flex; gap: 4px; flex-wrap: wrap; justify-content: flex-end; }

    .action-list { display: flex; flex-direction: column; gap: 0; }
    .action-item {
      display: flex; gap: 12px; align-items: flex-start;
      padding: 14px 0; border-bottom: 1px solid var(--line);
      color: var(--ink); cursor: pointer; transition: color 0.15s;
    }
    .action-item:hover:not(.action-disabled) { color: var(--ink-2); }
    .action-item:last-child { border-bottom: none; }
    .action-disabled { opacity: 0.45; cursor: default; }
    .action-icon {
      width: 32px; height: 32px; border-radius: var(--radius);
      background: var(--bg-sunk); display: flex; align-items: center; justify-content: center;
      font-size: 14px; flex-shrink: 0; color: var(--ink-2);
    }
    .action-title { font-size: 13px; font-weight: 500; }
    .action-sub { font-size: 11px; color: var(--ink-3); margin-top: 2px; }
  `]
})
export class OrganizerDashboardComponent implements OnInit {
  private apollo = inject(Apollo);
  auth = inject(AuthService);
  time = timeOfDay();
  events = signal<Event[]>([]);
  loading = signal(false);
  error = signal('');
  acting = signal('');

  totalEvents = signal(0);
  publishedCount = signal(0);
  draftCount = signal(0);
  cancelledCount = signal(0);

  ngOnInit() { this.load(); }

  private load() {
    this.loading.set(true);
    this.apollo.query<{ events: EventConnection }>({
      query: GET_EVENTS,
      variables: { page: 0, pageSize: 50, organizerId: this.auth.user()?.id }
    }).subscribe({
      next: r => {
        this.loading.set(false);
        const evs = r.data!.events.events;
        this.events.set(evs);
        this.totalEvents.set(evs.length);
        this.publishedCount.set(evs.filter((e: Event) => e.status === 'PUBLISHED').length);
        this.draftCount.set(evs.filter((e: Event) => e.status === 'DRAFT').length);
        this.cancelledCount.set(evs.filter((e: Event) => e.status === 'CANCELLED').length);
      },
      error: err => { this.loading.set(false); this.error.set(err.message || 'Failed to load'); }
    });
  }

  imageOf(images?: string[]): string | null {
    return images?.[0]?.trim() || null;
  }

  artSeed(ev: Event): number {
    return ev.id ? ev.id.split('').reduce((a, c) => a + c.charCodeAt(0), 0) % 100 : 1;
  }

  sellThrough(ev: Event): number {
    if (!ev.seatCategories?.length) return 0;
    const total = ev.seatCategories.reduce((a, c) => a + (c.totalSeats ?? 0), 0);
    const avail = ev.seatCategories.reduce((a, c) => a + (c.availableSeats ?? 0), 0);
    if (!total) return 0;
    return Math.round(((total - avail) / total) * 100);
  }

  publish(ev: Event) {
    if (!confirm(`Publish "${ev.title}"? It will become visible to all customers.`)) return;
    this.acting.set(ev.id);
    this.apollo.mutate<{ publishEvent: { id: string; status: string } }>({
      mutation: PUBLISH_EVENT, variables: { id: ev.id }
    }).subscribe({
      next: r => {
        this.acting.set('');
        const updated = r.data!.publishEvent;
        this.events.update(list => list.map(e => e.id === updated.id ? { ...e, status: updated.status } : e));
      },
      error: err => { this.acting.set(''); alert(err.message || 'Failed to publish'); }
    });
  }

  cancel(ev: Event) {
    if (!confirm(`Cancel "${ev.title}"? This will notify all attendees and cannot be undone.`)) return;
    this.acting.set(ev.id);
    this.apollo.mutate<{ cancelEvent: { id: string; status: string } }>({
      mutation: CANCEL_EVENT, variables: { id: ev.id }
    }).subscribe({
      next: r => {
        this.acting.set('');
        const updated = r.data!.cancelEvent;
        this.events.update(list => list.map(e => e.id === updated.id ? { ...e, status: updated.status } : e));
      },
      error: err => { this.acting.set(''); alert(err.message || 'Failed to cancel'); }
    });
  }

  statusBadge(status: string) {
    return ({ PUBLISHED: 'badge badge-live', DRAFT: 'badge badge-draft', CANCELLED: 'badge badge-danger', COMPLETED: 'badge' } as Record<string, string>)[status] ?? 'badge';
  }
}
