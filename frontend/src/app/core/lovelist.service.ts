import { Injectable, signal, computed } from '@angular/core';

/** A loved event — a small snapshot so the Lovelist page can render without a refetch. */
export interface LovelistItem {
  eventId: string;
  title: string;
  category: string;
  city: string;
  dateTime: string;
}

/**
 * Client-side "Lovelist" (favourites), persisted to localStorage and keyed by event.
 *
 * Phase 1 is intentionally client-only so the UI works immediately; Phase 5 swaps the data
 * source to per-user server storage (GraphQL) while keeping this same public API.
 */
@Injectable({ providedIn: 'root' })
export class LovelistService {
  private static readonly KEY = 'bkg_lovelist';

  private readonly _items = signal<LovelistItem[]>(this.load());

  readonly items = this._items.asReadonly();
  readonly count = computed(() => this._items().length);
  /** Set of loved event ids, for O(1) lookups in templates. */
  private readonly ids = computed(() => new Set(this._items().map(i => i.eventId)));

  isLoved(eventId: string): boolean {
    return this.ids().has(eventId);
  }

  /** Add if absent, remove if present. Returns the new loved state. */
  toggle(item: LovelistItem): boolean {
    if (this.isLoved(item.eventId)) {
      this.remove(item.eventId);
      return false;
    }
    this._items.update(items => [...items, item]);
    this.persist();
    return true;
  }

  remove(eventId: string): void {
    this._items.update(items => items.filter(i => i.eventId !== eventId));
    this.persist();
  }

  clear(): void {
    this._items.set([]);
    this.persist();
  }

  private persist(): void {
    try {
      localStorage.setItem(LovelistService.KEY, JSON.stringify(this._items()));
    } catch {
      // localStorage unavailable — lovelist stays in-memory only.
    }
  }

  private load(): LovelistItem[] {
    try {
      const raw = localStorage.getItem(LovelistService.KEY);
      return raw ? (JSON.parse(raw) as LovelistItem[]) : [];
    } catch {
      return [];
    }
  }
}
