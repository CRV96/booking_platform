import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { Apollo } from 'apollo-angular';
import { GET_TICKET, VALIDATE_TICKET, CANCEL_TICKET } from '../../shared/graphql/documents';
import { Ticket } from '../../shared/models/models';

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
  const corner = (x: number, y: number) =>
    `<rect x="${x}" y="${y}" width="19" height="19" fill="currentColor"/>` +
    `<rect x="${x+3}" y="${y+3}" width="13" height="13" fill="white"/>` +
    `<rect x="${x+6}" y="${y+6}" width="7" height="7" fill="currentColor"/>`;
  return `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg" style="color:currentColor">${rects}${corner(4,4)}${corner(77,4)}${corner(4,77)}</svg>`;
}

@Component({
  selector: 'app-ticket-scanner',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <div class="container page" style="max-width:640px">
      <div class="section-head" style="margin-bottom:32px">
        <div class="kicker">Organizer</div>
        <h2>Scan <em>Tickets</em></h2>
      </div>

      <div style="background:var(--bg-card);border:1px solid var(--line);border-radius:var(--radius-lg);padding:20px;margin-bottom:20px">
        <div class="field">
          <label>Ticket number</label>
          <div class="row gap-8">
            <input class="inp" style="flex:1" [(ngModel)]="ticketNumber" placeholder="TKT-XXXXXXXX"
                   (keyup.enter)="lookup()" [disabled]="looking()">
            <button class="btn btn-primary" (click)="lookup()" [disabled]="!ticketNumber || looking()">
              @if (looking()) { <span class="spinner"></span> } Look up
            </button>
          </div>
        </div>
      </div>

      @if (lookupError()) {
        <div class="alert alert-error">{{ lookupError() }}</div>
      }

      @if (ticket()) {
        <div class="ticket-card">
          <!-- Left panel -->
          <div class="ticket-left">
            <div class="ticket-top-meta">
              <span class="mono xs" style="text-transform:uppercase;letter-spacing:0.1em;color:var(--ink-4)">{{ ticket()!.seatCategory }}</span>
              <span class="dot-sep"></span>
              <span class="mono xs muted">{{ ticket()!.createdAt | date:'d MMM y' }}</span>
            </div>
            <div class="ticket-event-title">{{ ticket()!.eventTitle }}</div>
            @if (ticket()!.seatNumber) {
              <div class="mono xs muted" style="margin-top:4px">Seat {{ ticket()!.seatNumber }}</div>
            }
            <div class="ticket-code">
              <span class="mono xs muted" style="text-transform:uppercase;letter-spacing:0.08em;margin-bottom:4px;display:block">Ticket</span>
              <span class="mono" style="font-size:12px;color:var(--ink-2)">{{ ticket()!.ticketNumber }}</span>
            </div>
            <div class="mono xs muted" style="margin-top:8px">User {{ ticket()!.userId }}</div>
            <div style="margin-top:auto;padding-top:12px">
              <span class="badge" [class]="statusBadge(ticket()!.status)">{{ ticket()!.status }}</span>
            </div>
          </div>

          <div class="ticket-sep"></div>

          <div class="ticket-right">
            <div class="qr-box" [class.qr-invalid]="ticket()!.status !== 'VALID'" [innerHTML]="qr(ticket()!.ticketNumber)"></div>
            <div class="mono xs muted" style="margin-top:8px;text-align:center;font-size:10px;text-transform:uppercase;letter-spacing:0.08em">
              {{ ticket()!.status === 'VALID' ? 'Valid' : ticket()!.status }}
            </div>
          </div>
        </div>

        @if (actionSuccess()) {
          <div class="alert alert-success" style="margin-top:16px">{{ actionSuccess() }}</div>
        }
        @if (actionError()) {
          <div class="alert alert-error" style="margin-top:16px">{{ actionError() }}</div>
        }

        @if (ticket()!.status === 'VALID') {
          <div class="row gap-12" style="margin-top:16px">
            <button class="btn btn-primary btn-lg" (click)="validate()" [disabled]="acting()">
              @if (acting()) { <span class="spinner"></span> } Validate (Mark Used)
            </button>
            <button class="btn btn-danger btn-lg" (click)="cancelTicket()" [disabled]="acting()">
              Cancel Ticket
            </button>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .ticket-card {
      display: flex; align-items: stretch;
      background: var(--bg-card); border: 1px solid var(--line);
      border-radius: var(--radius-lg); overflow: hidden;
    }
    .ticket-left { flex: 1; padding: 20px 24px; display: flex; flex-direction: column; gap: 8px; min-height: 160px; }
    .ticket-top-meta { display: flex; align-items: center; gap: 8px; }
    .dot-sep { width: 3px; height: 3px; border-radius: 50%; background: var(--ink-4); }
    .ticket-event-title { font-family: var(--serif); font-size: 22px; line-height: 1.15; letter-spacing: -0.01em; color: var(--ink); }
    .ticket-code { margin-top: 8px; padding-top: 8px; border-top: 1px solid var(--line); }
    .ticket-sep {
      width: 1px; background: transparent;
      border-left: 2px dashed var(--line);
      margin: 16px 0; flex-shrink: 0;
    }
    .ticket-right {
      width: 120px; flex-shrink: 0;
      display: flex; flex-direction: column;
      align-items: center; justify-content: center;
      padding: 16px;
    }
    .qr-box { width: 88px; height: 88px; color: var(--ink); }
    .qr-invalid { opacity: 0.25; }
  `]
})
export class TicketScannerComponent {
  private apollo = inject(Apollo);
  ticketNumber = '';
  ticket = signal<Ticket | null>(null);
  looking = signal(false);
  acting = signal(false);
  lookupError = signal('');
  actionSuccess = signal('');
  actionError = signal('');

  qr(ticketNumber: string): string {
    return qrSvg(ticketNumber || 'X');
  }

  lookup() {
    if (!this.ticketNumber.trim()) return;
    this.looking.set(true);
    this.lookupError.set('');
    this.ticket.set(null);
    this.actionSuccess.set('');
    this.actionError.set('');

    this.apollo.query<{ ticket: Ticket }>({
      query: GET_TICKET, variables: { ticketNumber: this.ticketNumber.trim() }
    }).subscribe({
      next: r => { this.looking.set(false); this.ticket.set(r.data!.ticket); },
      error: err => { this.looking.set(false); this.lookupError.set(err.message || 'Ticket not found'); }
    });
  }

  validate() {
    this.acting.set(true);
    this.actionError.set('');
    this.apollo.mutate<{ validateTicket: Ticket }>({
      mutation: VALIDATE_TICKET, variables: { ticketNumber: this.ticket()!.ticketNumber }
    }).subscribe({
      next: r => {
        this.acting.set(false);
        this.ticket.update(t => t ? { ...t, status: r.data!.validateTicket.status } : null);
        this.actionSuccess.set('Ticket validated — entry granted.');
      },
      error: err => { this.acting.set(false); this.actionError.set(err.message || 'Validation failed'); }
    });
  }

  cancelTicket() {
    if (!confirm('Cancel this ticket? This cannot be undone.')) return;
    this.acting.set(true);
    this.actionError.set('');
    this.apollo.mutate<{ cancelTicket: Ticket }>({
      mutation: CANCEL_TICKET, variables: { ticketNumber: this.ticket()!.ticketNumber }
    }).subscribe({
      next: r => {
        this.acting.set(false);
        this.ticket.update(t => t ? { ...t, status: r.data!.cancelTicket.status } : null);
        this.actionSuccess.set('Ticket cancelled.');
      },
      error: err => { this.acting.set(false); this.actionError.set(err.message || 'Cancel failed'); }
    });
  }

  statusBadge(s: string) {
    return ({ VALID: 'badge badge-success', USED: 'badge', CANCELLED: 'badge badge-danger' } as Record<string, string>)[s] ?? 'badge';
  }
}
