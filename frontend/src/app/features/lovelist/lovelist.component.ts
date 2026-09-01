import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { LovelistService } from '../../core/lovelist.service';
import { EventArtComponent } from '../../shared/event-art.component';

@Component({
  selector: 'app-lovelist',
  standalone: true,
  imports: [RouterLink, DatePipe, EventArtComponent],
  template: `
    <div class="container" style="padding-top:48px;padding-bottom:60px">
      <div class="section-head">
        <div class="kicker">Saved</div>
        <h2>Your <em>lovelist</em></h2>
        <p class="sub">Events you've saved to come back to.</p>
      </div>

      @if (lovelist.items().length === 0) {
        <div class="empty-state">
          <h3>Nothing saved yet</h3>
          <p>Tap the ♥ on any event to keep it here.</p>
          <a class="btn btn-primary btn-sm" routerLink="/events" style="margin-top:16px;text-decoration:none">Browse events</a>
        </div>
      } @else {
        <div class="love-list">
          @for (item of lovelist.items(); track item.eventId) {
            <div class="love-row">
              <a class="love-link" [routerLink]="['/events', item.eventId]" style="text-decoration:none;color:inherit">
                <div class="love-art">
                  <app-event-art [seed]="artSeed(item.eventId)" [title]="item.event?.title || ''" ratio="16/10" />
                </div>
                <div class="love-info">
                  <div class="love-title">{{ item.event?.title || 'Event unavailable' }}</div>
                  <div class="love-meta">
                    @if (item.event) {
                      <span>{{ item.event.category }}</span>
                      <span class="dot"></span>
                      <span>{{ item.event.venue.city }}</span>
                      <span class="dot"></span>
                      <span>{{ item.event.dateTime | date:'d MMM y' }}</span>
                    } @else {
                      <span>This event is no longer available</span>
                    }
                  </div>
                </div>
              </a>
              <button type="button" class="btn btn-ghost btn-sm" (click)="lovelist.remove(item.eventId)">Remove</button>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .love-list { display: flex; flex-direction: column; gap: 10px; }
    .love-row {
      display: flex; align-items: center; justify-content: space-between; gap: 16px;
      padding: 16px 18px; border: 1px solid var(--line); border-radius: var(--radius-lg);
      background: var(--bg-card);
    }
    .love-link { display: flex; align-items: center; gap: 14px; flex: 1; min-width: 0; }
    .love-art { width: 92px; flex-shrink: 0; }
    .love-info { flex: 1; min-width: 0; }
    .love-title { font-family: var(--serif); font-size: 20px; line-height: 1.15; }
    .love-meta {
      display: flex; align-items: center; gap: 8px; margin-top: 4px;
      font-family: var(--mono); font-size: 11px; color: var(--ink-3);
      text-transform: uppercase; letter-spacing: 0.06em;
    }
    .love-meta .dot { width: 3px; height: 3px; border-radius: 50%; background: var(--ink-4); flex-shrink: 0; }
  `]
})
export class LovelistComponent {
  lovelist = inject(LovelistService);

  /** Same deterministic seed the events listing uses, so a card's art matches its lovelist thumbnail. */
  artSeed(eventId: string): number {
    return eventId ? eventId.split('').reduce((a, c) => a + c.charCodeAt(0), 0) % 100 : 1;
  }
}
