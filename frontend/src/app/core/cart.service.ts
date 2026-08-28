import { Injectable, signal, computed } from '@angular/core';

/** A cart line item — one event + seat category + quantity. */
export interface CartItem {
  eventId: string;
  eventTitle: string;
  seatCategory: string;
  unitPrice: string;   // decimal as string, e.g. "49.99"
  currency: string;    // ISO 4217, e.g. "USD"
  quantity: number;
  /** Per-booking idempotency key. Regenerated when this line changes, so checkout reuses the
   *  same booking on reload but a real change starts a fresh one. */
  idempotencyKey: string;
}

/** Cart input without the internally-managed idempotency key. */
export type CartItemInput = Omit<CartItem, 'idempotencyKey'>;

/**
 * Client-side multi-item cart, persisted to localStorage. Holds several line items and one
 * `orderId` — the idempotency key for the single payment that covers the whole cart. The order
 * id is regenerated on any cart change so a changed cart starts a fresh order/payment.
 */
@Injectable({ providedIn: 'root' })
export class CartService {
  private static readonly ITEMS_KEY = 'bkg_cart_items';
  private static readonly ORDER_KEY = 'bkg_cart_order';

  private readonly _items = signal<CartItem[]>(this.loadItems());
  private readonly _orderId = signal<string>(this.loadOrderId());

  readonly items = this._items.asReadonly();
  readonly orderId = this._orderId.asReadonly();
  readonly hasItems = computed(() => this._items().length > 0);
  readonly count = computed(() => this._items().reduce((n, i) => n + i.quantity, 0));
  readonly currency = computed(() => this._items()[0]?.currency ?? '');
  readonly total = computed(() =>
    this._items().reduce((sum, i) => sum + parseFloat(i.unitPrice) * i.quantity, 0).toFixed(2));

  /** Add a line. If the same event+category is already in the cart, it's replaced (updated qty). */
  add(input: CartItemInput): void {
    const items = [...this._items()];
    const idx = items.findIndex(i => this.sameLine(i, input));
    const line: CartItem = { ...input, idempotencyKey: crypto.randomUUID() };
    if (idx >= 0) items[idx] = line;
    else items.push(line);
    this._items.set(items);
    this.onCartChanged();
  }

  setQuantity(eventId: string, seatCategory: string, quantity: number): void {
    this._items.update(items => items.map(i =>
      (i.eventId === eventId && i.seatCategory === seatCategory)
        ? { ...i, quantity, idempotencyKey: crypto.randomUUID() }   // amount changed → new key
        : i));
    this.onCartChanged();
  }

  remove(eventId: string, seatCategory: string): void {
    this._items.update(items => items.filter(i => !(i.eventId === eventId && i.seatCategory === seatCategory)));
    this.onCartChanged();
  }

  clear(): void {
    this._items.set([]);
    this.onCartChanged();
  }

  private sameLine(a: { eventId: string; seatCategory: string }, b: { eventId: string; seatCategory: string }): boolean {
    return a.eventId === b.eventId && a.seatCategory === b.seatCategory;
  }

  /** Any cart change starts a fresh order (new payment), and re-persists. */
  private onCartChanged(): void {
    this._orderId.set(crypto.randomUUID());
    this.persist();
  }

  private persist(): void {
    try {
      localStorage.setItem(CartService.ITEMS_KEY, JSON.stringify(this._items()));
      localStorage.setItem(CartService.ORDER_KEY, this._orderId());
    } catch {
      // localStorage unavailable — cart stays in-memory only.
    }
  }

  private loadItems(): CartItem[] {
    try {
      const raw = localStorage.getItem(CartService.ITEMS_KEY);
      const items = raw ? (JSON.parse(raw) as CartItem[]) : [];
      // Backfill missing idempotency keys (carts saved before this field existed).
      return items.map(i => i.idempotencyKey ? i : { ...i, idempotencyKey: crypto.randomUUID() });
    } catch {
      return [];
    }
  }

  private loadOrderId(): string {
    try {
      return localStorage.getItem(CartService.ORDER_KEY) || crypto.randomUUID();
    } catch {
      return crypto.randomUUID();
    }
  }
}
