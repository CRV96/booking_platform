import { Injectable, signal, computed, inject } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { GET_LOVELIST, ADD_FAVORITE, REMOVE_FAVORITE } from '../shared/graphql/documents';

/** The live event details hydrated for a lovelist entry (null if the event no longer exists). */
export interface LovelistEvent {
  id: string;
  title: string;
  category: string;
  dateTime: string;
  venue: { city: string };
}

/** A lovelist entry — a pointer to an event, plus its hydrated details for display. */
export interface LovelistItem {
  eventId: string;
  createdAt: string | null;
  event: LovelistEvent | null;
}

/**
 * Per-user "lovelist" (favourites), persisted server-side (booking-service) via the GraphQL
 * gateway. A local signal mirrors the server state; every mutation replaces it with the
 * authoritative list the server returns.
 *
 * Requires an authenticated user. On sign-out, {@link reset} clears the local mirror only.
 */
@Injectable({ providedIn: 'root' })
export class LovelistService {
  private apollo = inject(Apollo);

  private readonly _items = signal<LovelistItem[]>([]);

  readonly items = this._items.asReadonly();
  readonly count = computed(() => this._items().length);
  /** Set of loved event ids, for O(1) lookups in templates. */
  private readonly ids = computed(() => new Set(this._items().map(i => i.eventId)));

  isLoved(eventId: string): boolean {
    return this.ids().has(eventId);
  }

  /** Load the authenticated user's lovelist from the server. */
  load(): void {
    this.apollo.query<{ lovelist: LovelistItem[] }>({ query: GET_LOVELIST, fetchPolicy: 'network-only' })
      .subscribe({
        next: r => this._items.set(r.data!.lovelist),
        error: () => this._items.set([]),
      });
  }

  /** Add if absent, remove if present. */
  toggle(eventId: string): void {
    if (this.isLoved(eventId)) {
      this.remove(eventId);
    } else {
      this.add(eventId);
    }
  }

  add(eventId: string): void {
    this.apollo.mutate<{ addFavorite: LovelistItem[] }>({ mutation: ADD_FAVORITE, variables: { eventId } })
      .subscribe({ next: r => this._items.set(r.data!.addFavorite) });
  }

  remove(eventId: string): void {
    this.apollo.mutate<{ removeFavorite: LovelistItem[] }>({ mutation: REMOVE_FAVORITE, variables: { eventId } })
      .subscribe({ next: r => this._items.set(r.data!.removeFavorite) });
  }

  /** Drop the local mirror only — used on sign-out. */
  reset(): void {
    this._items.set([]);
  }
}
