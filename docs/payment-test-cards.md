# Payment Test Cards

> Cards to use when testing the checkout flow. No real money moves — everything here is test mode.
> Applies to both the **real Stripe** path (test mode + Stripe CLI) and the **local mock** gateway.

The checkout flow can run against two payment backends, selected by `payment.gateway.type` in
`config/dev/payment-service.properties`:

| `payment.gateway.type` | What runs | Card entry |
| --- | --- | --- |
| `stripe` | Real Stripe **test mode** (needs a test API key + the Stripe CLI locally) | Real Stripe Elements iframe |
| `mock`   | The in-app mock gateway (no network, no Stripe) | Plain stand-in form |

Both honour the **same card numbers** below, so the habit transfers 1:1 between them.

## Test cards

| Card number | Outcome | Simulates |
| --- | --- | --- |
| `4242 4242 4242 4242` | **Success** — booking is CONFIRMED, tickets issued, confirmation email sent | Happy path |
| `4000 0000 0000 0002` | **Declined** (`card_declined`) — payment fails, booking CANCELLED, seats released | Generic decline |
| `4000 0000 0000 9995` | **Declined** (`insufficient_funds`) | A different decline reason |
| `4000 0025 0000 3155` | **Requires authentication** (3-D Secure), then succeeds | SCA / 3DS challenge |
| anything else (mock only) | **Declined** (generic) | Forces a known card |

For every card:

- **Expiry** — any date in the future, e.g. `12 / 34`
- **CVC** — any 3 digits, e.g. `123`
- **ZIP / postal code** — any value, e.g. `12345`

## Notes

- These are Stripe's official test numbers. The full list is at
  <https://docs.stripe.com/testing>.
- **3-D Secure** (`4000 0025 0000 3155`) triggers an authentication step. In real mode Stripe shows
  the bank's challenge modal; in mock mode a stand-in "Authenticate" button appears, then the payment
  succeeds.
- In **mock** mode the "card number" is a fake selector string only — no real card, no real charge.
  In **real** mode the card is entered into Stripe's iframe and never touches our backend (PCI).
- Real-mode webhooks need the Stripe CLI running locally so Stripe can reach `localhost`:
  ```bash
  stripe listen --forward-to localhost:8084/api/webhooks/stripe
  ```
