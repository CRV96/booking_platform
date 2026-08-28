import { Injectable, signal, computed } from '@angular/core';

/** A single cart line item (single-item cart for now). */
export interface CartItem {
  eventId: string;
  eventTitle: string;
  seatCategory: string;
  unitPrice: string;   // decimal as string, e.g. "49.99"
  currency: string;    // ISO 4217, e.g. "USD"
  quantity: number;
  /**
   * Stable idempotency key for the booking this cart will create at checkout. Regenerated
   * whenever the cart contents change (new item / quantity), so a checkout reload reuses the
   * same booking, but a real change starts a fresh one.
   */
  idempotencyKey: string;
}

/** Cart input without the internally-managed idempotency key. */
export type CartItemInput = Omit<CartItem, 'idempotencyKey'>;

/**
 * Client-side cart. Holds at most one line item, persisted to localStorage so it survives
 * reloads. The cart is purely a client-side intent — no seats are reserved until checkout
 * (that's when the booking is created and the hold starts).
 */
@Injectable({ providedIn: 'root' })
export class CartService {
  private static readonly STORAGE_KEY = 'bkg_cart';

  private readonly _item = signal<CartItem | null>(this.load());

  readonly item = this._item.asReadonly();
  readonly hasItem = computed(() => this._item() !== null);
  readonly count = computed(() => this._item()?.quantity ?? 0);
  readonly total = computed(() => {
    const i = this._item();
    return i ? (parseFloat(i.unitPrice) * i.quantity).toFixed(2) : '0.00';
  });

  /** Single-item cart: adding a new item replaces whatever was there (with a fresh idempotency key). */
  set(item: CartItemInput): void {
    this._item.set({ ...item, idempotencyKey: crypto.randomUUID() });
    this.persist();
  }

  setQuantity(quantity: number): void {
    // Amount changes → new idempotency key, so checkout creates a booking for the new quantity.
    this._item.update(i => (i ? { ...i, quantity, idempotencyKey: crypto.randomUUID() } : i));
    this.persist();
  }

  clear(): void {
    this._item.set(null);
    this.persist();
  }

  private persist(): void {
    try {
      const item = this._item();
      if (item) localStorage.setItem(CartService.STORAGE_KEY, JSON.stringify(item));
      else localStorage.removeItem(CartService.STORAGE_KEY);
    } catch {
      // localStorage unavailable (private mode, blocked) — cart stays in-memory only.
    }
  }

  private load(): CartItem | null {
    try {
      const raw = localStorage.getItem(CartService.STORAGE_KEY);
      if (!raw) return null;
      const item = JSON.parse(raw) as CartItem;
      // Backfill the idempotency key for carts persisted before this field existed,
      // so an older localStorage cart doesn't send a null key to createBooking.
      if (!item.idempotencyKey) item.idempotencyKey = crypto.randomUUID();
      return item;
    } catch {
      return null;
    }
  }
}
