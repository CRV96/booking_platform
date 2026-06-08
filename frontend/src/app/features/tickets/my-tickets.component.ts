import { Component, inject, signal, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Apollo } from 'apollo-angular';
import { GET_MY_TICKETS } from '../../shared/graphql/documents';
import { Ticket, TicketConnection } from '../../shared/models/models';

function qrSvg(seed: string): string {
  const s = seed || 'X';
  const cells: boolean[][] = [];
  for (let r = 0; r < 11; r++) {
    cells[r] = [];
    for (let c = 0; c < 11; c++) {
      const v = s.charCodeAt((r * 11 + c) % s.length);
      cells[r][c] = ((v * (r + 1) * (c + 1)) % 7) < 3;
    }
  }
  let rects = '';
  for (let r = 0; r < 11; r++) {
    for (let c = 0; c < 11; c++) {
      if (cells[r][c]) {
        rects += `<rect x="${4 + c * 8}" y="${4 + r * 8}" width="7" height="7" fill="currentColor"/>`;
      }
    }
  }
  // Corner markers
  const corner = (x: number, y: number) =>
    `<rect x="${x}" y="${y}" width="19" height="19" fill="currentColor"/>` +
    `<rect x="${x+3}" y="${y+3}" width="13" height="13" fill="white"/>` +
    `<rect x="${x+6}" y="${y+6}" width="7" height="7" fill="currentColor"/>`;
  return `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg" style="color:currentColor">${rects}${corner(4,4)}${corner(77,4)}${corner(4,77)}</svg>`;
}

@Component({
  selector: 'app-my-tickets',
  standalone: true,
  imports: [DatePipe],
  template: `
    <div class="container page">
      <div class="section-head" style="margin-bottom:32px">
        <div class="kicker">Account</div>
        <h2>My <em>Tickets</em></h2>
      </div>

      @if (loading()) {
        <div class="loading-center"><div class="spinner spinner-lg"></div></div>
      } @else if (error()) {
        <div class="alert alert-error">{{ error() }}</div>
      } @else if (tickets().length === 0) {
        <div class="empty-state">
          <h3>No tickets yet</h3>
          <p>Tickets appear here once your bookings are confirmed.</p>
        </div>
      } @else {
        <div class="tickets-list">
          @for (t of tickets(); track t.id) {
            <div class="ticket-card">
              <!-- Left panel -->
              <div class="ticket-left">
                <div class="ticket-top-meta">
                  <span class="mono xs" style="text-transform:uppercase;letter-spacing:0.1em;color:var(--ink-4)">{{ t.seatCategory }}</span>
                  <span class="dot-sep"></span>
                  <span class="mono xs muted">{{ t.createdAt | date:'d MMM y' }}</span>
                </div>
                <div class="ticket-event-title">{{ t.eventTitle }}</div>
                @if (t.seatNumber) {
                  <div class="ticket-seat mono xs muted">Seat {{ t.seatNumber }}</div>
                }
                <div class="ticket-code">
                  <span class="mono xs muted" style="text-transform:uppercase;letter-spacing:0.08em;margin-bottom:4px;display:block">Ticket</span>
                  <span class="mono" style="font-size:12px;color:var(--ink-2)">{{ t.ticketNumber }}</span>
                </div>
                <div style="margin-top:auto;padding-top:12px">
                  <span class="badge" [class]="statusBadge(t.status)">{{ t.status }}</span>
                </div>
              </div>

              <!-- Dashed separator -->
              <div class="ticket-sep"></div>

              <!-- Right QR panel -->
              <div class="ticket-right">
                @if (t.status === 'VALID') {
                  <div class="qr-box" [innerHTML]="qr(t.ticketNumber)"></div>
                  <div class="mono xs muted" style="margin-top:8px;text-align:center;font-size:10px">Scan at entry</div>
                } @else {
                  <div class="qr-box qr-invalid" [innerHTML]="qr(t.ticketNumber)"></div>
                  <div class="mono xs muted" style="margin-top:8px;text-align:center;font-size:10px;text-transform:uppercase;letter-spacing:0.08em">{{ t.status }}</div>
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
    .tickets-list { display: flex; flex-direction: column; gap: 16px; }
    .ticket-card {
      display: flex; align-items: stretch;
      background: var(--bg-card); border: 1px solid var(--line);
      border-radius: var(--radius-lg); overflow: hidden;
    }
    .ticket-left { flex: 1; padding: 20px 24px; display: flex; flex-direction: column; gap: 8px; min-height: 160px; }
    .ticket-top-meta { display: flex; align-items: center; gap: 8px; }
    .dot-sep { width: 3px; height: 3px; border-radius: 50%; background: var(--ink-4); }
    .ticket-event-title { font-family: var(--serif); font-size: 22px; line-height: 1.15; letter-spacing: -0.01em; color: var(--ink); }
    .ticket-seat { margin-top: 2px; }
    .ticket-code { margin-top: 8px; padding-top: 8px; border-top: 1px solid var(--line); }
    .ticket-sep {
      width: 1px; background: transparent;
      border-left: 2px dashed var(--line);
      margin: 16px 0;
      flex-shrink: 0;
    }
    .ticket-right {
      width: 120px; flex-shrink: 0;
      display: flex; flex-direction: column;
      align-items: center; justify-content: center;
      padding: 16px;
    }
    .qr-box { width: 88px; height: 88px; color: var(--ink); }
    .qr-invalid { opacity: 0.25; }
    .pagination { display: flex; align-items: center; justify-content: center; gap: 16px; margin-top: 32px; }
  `]
})
export class MyTicketsComponent implements OnInit {
  private apollo = inject(Apollo);
  tickets = signal<Ticket[]>([]);
  connection = signal<TicketConnection | null>(null);
  loading = signal(false);
  error = signal('');
  page = signal(0);

  ngOnInit() { this.load(); }

  prevPage() { this.page.update(p => p - 1); this.load(); }
  nextPage() { this.page.update(p => p + 1); this.load(); }

  qr(ticketNumber: string): string {
    return qrSvg(ticketNumber || 'X');
  }

  private load() {
    this.loading.set(true);
    this.apollo.query<{ myTickets: TicketConnection }>({
      query: GET_MY_TICKETS,
      variables: { page: this.page(), pageSize: 12 }
    }).subscribe({
      next: r => {
        this.loading.set(false);
        this.connection.set(r.data.myTickets);
        this.tickets.set(r.data.myTickets.tickets);
      },
      error: err => { this.loading.set(false); this.error.set(err.message || 'Failed to load'); }
    });
  }

  statusBadge(s: string) {
    return ({ VALID: 'badge badge-success', USED: 'badge', CANCELLED: 'badge badge-danger' } as Record<string, string>)[s] ?? 'badge';
  }
}
