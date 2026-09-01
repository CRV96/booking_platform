import { Component, inject, signal, OnInit } from '@angular/core';
import { FormBuilder, FormArray, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Apollo } from 'apollo-angular';
import { GET_EVENT, CREATE_EVENT, UPDATE_EVENT } from '../../shared/graphql/documents';
import { Event, SeatCategory } from '../../shared/models/models';

const CATEGORIES = ['CONCERT', 'SPORTS', 'THEATRE', 'CONFERENCE', 'FESTIVAL', 'OTHER'];
const CURRENCIES = ['USD', 'EUR', 'GBP', 'CAD', 'AUD'];

@Component({
  selector: 'app-event-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="container page" style="max-width:760px">
      <div class="section-head" style="margin-bottom:32px">
        <div class="kicker">{{ isEdit ? 'Edit event' : 'Create event' }}</div>
        <h2>@if(isEdit){Edit <em>event</em>}@else{New <em>event</em>}</h2>
      </div>

      @if (loadError()) { <div class="alert alert-error">{{ loadError() }}</div> }
      @if (saveError()) { <div class="alert alert-error">{{ saveError() }}</div> }

      @if (loading()) {
        <div class="loading-center"><div class="spinner spinner-lg"></div></div>
      } @else {
        <form [formGroup]="form" (ngSubmit)="save()">
          <!-- Basic info -->
          <div class="form-card">
            <div class="form-card-label">Basic Info</div>
            <div class="field">
              <label>Title <span style="color:var(--danger)">*</span></label>
              <input formControlName="title" class="inp" placeholder="Rock Fest 2026">
            </div>
            <div class="field">
              <label>Description</label>
              <textarea formControlName="description" class="inp" placeholder="What's this event about?"></textarea>
            </div>
            <div class="form-row-2">
              <div class="field">
                <label>Category <span style="color:var(--danger)">*</span></label>
                <select formControlName="category" class="inp">
                  <option value="">Select category</option>
                  @for (c of categories; track c) { <option [value]="c">{{ c }}</option> }
                </select>
              </div>
              <div class="field">
                <label>Date & Time <span style="color:var(--danger)">*</span></label>
                <input formControlName="dateTime" type="datetime-local" class="inp">
              </div>
              <div class="field">
                <label>End Date & Time <span class="mono xs muted">(optional — for multi-day events)</span></label>
                <input formControlName="endDateTime" type="datetime-local" class="inp">
              </div>
            </div>
          </div>

          <!-- Venue -->
          <div class="form-card">
            <div class="form-card-label">Venue</div>
            <div formGroupName="venue">
              <div class="form-row-2" style="margin-bottom:12px">
                <div class="field">
                  <label>Venue name <span style="color:var(--danger)">*</span></label>
                  <input formControlName="name" class="inp" placeholder="The Arena">
                </div>
                <div class="field">
                  <label>Address</label>
                  <input formControlName="address" class="inp" placeholder="123 Main St">
                </div>
              </div>
              <div class="form-row-2" style="margin-bottom:12px">
                <div class="field">
                  <label>City <span style="color:var(--danger)">*</span></label>
                  <input formControlName="city" class="inp" placeholder="Amsterdam">
                </div>
                <div class="field">
                  <label>Country <span style="color:var(--danger)">*</span></label>
                  <input formControlName="country" class="inp" placeholder="NL">
                </div>
              </div>
              <div class="field">
                <label>Capacity</label>
                <input formControlName="capacity" type="number" class="inp" placeholder="20000">
              </div>
            </div>
          </div>

          <!-- Seat categories -->
          <div class="form-card">
            <div class="row" style="justify-content:space-between;margin-bottom:16px">
              <div class="form-card-label" style="margin-bottom:0">Seat Categories</div>
              <button type="button" class="btn btn-secondary btn-sm" (click)="addSeat()">+ Add Category</button>
            </div>

            @if (seats.length === 0) {
              <p style="font-size:13px;color:var(--ink-3)">Add at least one seat category.</p>
            }

            @for (seat of seats.controls; track $index) {
              <div class="seat-form" [formGroup]="seatGroup($index)">
                <div class="row" style="justify-content:space-between;margin-bottom:12px">
                  <span class="mono xs muted" style="text-transform:uppercase;letter-spacing:0.08em">Category {{ $index + 1 }}</span>
                  <button type="button" class="btn btn-danger btn-sm" (click)="removeSeat($index)">Remove</button>
                </div>
                <div class="form-row-2" style="margin-bottom:12px">
                  <div class="field">
                    <label>Name <span style="color:var(--danger)">*</span></label>
                    <input formControlName="name" class="inp" placeholder="Floor, VIP, General…">
                  </div>
                  <div class="field">
                    <label>Total seats <span style="color:var(--danger)">*</span></label>
                    <input formControlName="totalSeats" type="number" class="inp" placeholder="500">
                  </div>
                </div>
                <div class="form-row-2">
                  <div class="field">
                    <label>Price <span style="color:var(--danger)">*</span></label>
                    <input formControlName="price" type="number" step="0.01" class="inp" placeholder="49.99">
                  </div>
                  <div class="field">
                    <label>Currency</label>
                    <select formControlName="currency" class="inp">
                      @for (c of currencies; track c) { <option [value]="c">{{ c }}</option> }
                    </select>
                  </div>
                </div>
              </div>
            }
          </div>

          <div class="row gap-12" style="margin-top:8px">
            <button class="btn btn-primary btn-lg" type="submit" [disabled]="saving()">
              @if (saving()) { <span class="spinner"></span> } {{ isEdit ? 'Save Changes' : 'Create Event' }}
            </button>
            <a routerLink="/organizer" class="btn btn-secondary btn-lg" style="text-decoration:none">Cancel</a>
          </div>
        </form>
      }
    </div>
  `,
  styles: [`
    .form-card {
      background: var(--bg-card); border: 1px solid var(--line);
      border-radius: var(--radius-lg); padding: 24px;
      margin-bottom: 16px; display: flex; flex-direction: column; gap: 12px;
    }
    .form-card-label { font-family: var(--mono); font-size: 10px; text-transform: uppercase; letter-spacing: 0.12em; color: var(--ink-4); }
    .form-row-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .seat-form { border: 1px solid var(--line); border-radius: var(--radius); padding: 16px; margin-bottom: 12px; }
    .seat-form:last-child { margin-bottom: 0; }
    .inp.ng-invalid.ng-touched { border-color: var(--danger); }
  `]
})
export class EventFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private apollo = inject(Apollo);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  categories = CATEGORIES;
  currencies = CURRENCIES;
  isEdit = false;
  eventId: string | null = null;
  loading = signal(false);
  saving = signal(false);
  loadError = signal('');
  saveError = signal('');

  form = this.fb.group({
    title: ['', Validators.required],
    description: [''],
    category: ['', Validators.required],
    dateTime: ['', Validators.required],
    endDateTime: [''],
    venue: this.fb.group({
      name: ['', Validators.required],
      address: [''],
      city: ['', Validators.required],
      country: ['', Validators.required],
      capacity: [null as number | null],
    }),
    seatCategories: this.fb.array([]),
  });

  get seats() { return this.form.get('seatCategories') as FormArray; }
  seatGroup(i: number) { return this.seats.at(i) as ReturnType<typeof this.fb.group>; }

  ngOnInit() {
    this.eventId = this.route.snapshot.paramMap.get('id');
    this.isEdit = !!this.eventId;
    if (this.isEdit) this.loadEvent();
  }

  addSeat() {
    this.seats.push(this.fb.group({
      name: ['', Validators.required],
      price: [null as number | null, Validators.required],
      currency: ['EUR'],
      totalSeats: [null as number | null, Validators.required],
    }));
  }

  removeSeat(i: number) { this.seats.removeAt(i); }

  private loadEvent() {
    this.loading.set(true);
    this.apollo.query<{ event: Event }>({ query: GET_EVENT, variables: { id: this.eventId } })
      .subscribe({
        next: r => {
          this.loading.set(false);
          const ev = r.data!.event;
          const dt = ev.dateTime ? ev.dateTime.substring(0, 16) : '';
          const endDt = ev.endDateTime ? ev.endDateTime.substring(0, 16) : '';
          this.form.patchValue({
            title: ev.title, description: ev.description ?? '', category: ev.category, dateTime: dt, endDateTime: endDt,
            venue: { name: ev.venue.name, address: ev.venue.address ?? '', city: ev.venue.city, country: ev.venue.country, capacity: ev.venue.capacity ?? null },
          });
          ev.seatCategories.forEach((sc: SeatCategory) => {
            this.seats.push(this.fb.group({
              name: [sc.name], price: [parseFloat(sc.price)], currency: [sc.currency], totalSeats: [sc.totalSeats],
            }));
          });
        },
        error: err => { this.loading.set(false); this.loadError.set(err.message || 'Failed to load event'); }
      });
  }

  save() {
    this.saveError.set('');
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.saveError.set('Please fill in all required fields.');
      return;
    }
    if (this.seats.length === 0) {
      this.saveError.set('Add at least one seat category.');
      return;
    }

    // Seats across all categories cannot exceed the venue capacity (when one is set).
    const capacity = this.form.value.venue?.capacity ? Number(this.form.value.venue.capacity) : null;
    const totalSeats = (this.form.value.seatCategories as { totalSeats: number }[])
      .reduce((sum, sc) => sum + Number(sc.totalSeats || 0), 0);
    if (capacity != null && totalSeats > capacity) {
      this.saveError.set(`Total seats across categories (${totalSeats}) exceed the venue capacity (${capacity}).`);
      return;
    }

    // A multi-day event's end must be after its start.
    if (this.form.value.endDateTime && this.form.value.dateTime
        && new Date(this.form.value.endDateTime) <= new Date(this.form.value.dateTime)) {
      this.saveError.set('End date & time must be after the start.');
      return;
    }
    this.saving.set(true);

    const v = this.form.value;
    const dateTime = v.dateTime ? new Date(v.dateTime!).toISOString() : '';

    const input = {
      title: v.title,
      description: v.description || null,
      category: v.category,
      dateTime,
      endDateTime: v.endDateTime ? new Date(v.endDateTime).toISOString() : null,
      venue: { ...v.venue, capacity: v.venue?.capacity ? Number(v.venue.capacity) : null },
      seatCategories: (v.seatCategories as { name: string; price: number; currency: string; totalSeats: number }[])
        .map(sc => ({ ...sc, price: Number(sc.price), totalSeats: Number(sc.totalSeats) })),
    };

    const mutation$ = this.isEdit
      ? this.apollo.mutate({ mutation: UPDATE_EVENT, variables: { id: this.eventId, input } })
      : this.apollo.mutate({ mutation: CREATE_EVENT, variables: { input } });

    mutation$.subscribe({
      next: () => { this.saving.set(false); this.router.navigate(['/organizer']); },
      error: err => { this.saving.set(false); this.saveError.set(err.message?.replace('ApolloError: ', '') || 'Save failed'); }
    });
  }
}
