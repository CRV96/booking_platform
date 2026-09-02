import { Injectable, signal, computed, inject } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { GET_CART, ADD_TO_CART, UPDATE_CART_ITEM, REMOVE_FROM_CART, CLEAR_CART } from '../shared/graphql/documents';

/** A cart line — one event + seat category + quantity, as stored per-user on the server. */
export interface CartItem {
  id: string;          // server cart-line UUID — the handle for update/remove and checkout idempotency
  eventId: string;
  eventTitle: string;
  seatCategory: string;
  unitPrice: string;   // decimal as string, e.g. "49.99"
  currency: string;    // ISO 4217, e.g. "USD"
  quantity: number;
}

/** Fields needed to add a line — the server assigns the id. */
export type CartItemInput = {
  eventId: string;
  eventTitle: string;
  seatCategory: string;
  unitPrice: string;
  currency: string;
  quantity: number;
};

interface CartData {
  items: CartItem[];
  totalPrice: string;
  currency: string | null;
}

/**
 * Per-user cart, persisted server-side (booking-service) and reached through the GraphQL
 * gateway. A local signal mirrors the server state so the UI stays reactive; every mutation
 * replaces the mirror with the authoritative cart returned by the server.
 *
 * Requires an authenticated user — {@link load} and the mutations only make sense with a JWT.
 * On sign-out, {@link reset} clears the local mirror without touching the server.
 */
@Injectable({ providedIn: 'root' })
export class CartService {
  private apollo = inject(Apollo);

  private readonly _items = signal<CartItem[]>([]);

  readonly items = this._items.asReadonly();
  readonly hasItems = computed(() => this._items().length > 0);
  readonly count = computed(() => this._items().reduce((n, i) => n + i.quantity, 0));
  readonly currency = computed(() => this._items()[0]?.currency ?? '');
  readonly total = computed(() =>
    this._items().reduce((sum, i) => sum + parseFloat(i.unitPrice) * i.quantity, 0).toFixed(2));

  /** Load the authenticated user's cart from the server into the local mirror. */
  load(): void {
    this.apollo.query<{ cart: CartData }>({ query: GET_CART, fetchPolicy: 'network-only' })
      .subscribe({
        next: r => this._items.set(r.data!.cart.items),
        error: () => this._items.set([]),
      });
  }

  /**
   * Add a line (upsert on event + seat category server-side). A cart can only hold one
   * currency — payment is a single charge — so adding an item in a different currency prompts
   * the user to empty the cart first (confirm) and then adds only the new item.
   */
  add(input: CartItemInput): void {
    const current = this.currency();
    if (this.hasItems() && current && input.currency !== current) {
      const ok = window.confirm(
        `Your cart is in ${current}, but this item is priced in ${input.currency}. `
        + `A cart can only use one currency, so we'll empty your cart and add just this item. Continue?`);
      if (!ok) return;
      // Replace the cart: clear it on the server, then add the new item.
      this.apollo.mutate<{ clearCart: CartData }>({ mutation: CLEAR_CART })
        .subscribe({ next: () => this.mutateAdd(input) });
      return;
    }
    this.mutateAdd(input);
  }

  private mutateAdd(input: CartItemInput): void {
    this.apollo.mutate<{ addToCart: CartData }>({ mutation: ADD_TO_CART, variables: { input } })
      .subscribe({ next: r => this._items.set(r.data!.addToCart.items) });
  }

  setQuantity(cartItemId: string, quantity: number): void {
    this.apollo.mutate<{ updateCartItem: CartData }>({
      mutation: UPDATE_CART_ITEM, variables: { cartItemId, quantity },
    }).subscribe({ next: r => this._items.set(r.data!.updateCartItem.items) });
  }

  remove(cartItemId: string): void {
    this.apollo.mutate<{ removeFromCart: CartData }>({
      mutation: REMOVE_FROM_CART, variables: { cartItemId },
    }).subscribe({ next: r => this._items.set(r.data!.removeFromCart.items) });
  }

  /** Empty the cart on the server (used after a successful checkout). */
  clear(): void {
    this.apollo.mutate<{ clearCart: CartData }>({ mutation: CLEAR_CART })
      .subscribe({ next: r => this._items.set(r.data!.clearCart.items) });
  }

  /** Drop the local mirror only — used on sign-out, never touches the server cart. */
  reset(): void {
    this._items.set([]);
  }
}
