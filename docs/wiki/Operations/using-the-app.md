# Using the App

A task-oriented walkthrough of the platform from the user's side. The UI is the Angular SPA (`http://localhost:4200` in dev); for the component-level tour see the [Frontend guide](frontend-guide).

## Roles

Three roles, sourced from the Keycloak JWT (`realm_access.roles`):

| Role | Who | Can |
|------|-----|-----|
| **(public)** | Not logged in | Browse and search published events |
| **`customer`** | Registered attendee | Everything public + cart, lovelist, book, pay, view bookings/tickets, manage profile |
| **`employee`** | Organizer | Everything a customer can + create/manage events, read event stats, validate tickets |
| **`admin`** | Administrator | Employee permissions + user search/management |

### Test users (local)

| Username | Password | Role |
|----------|----------|------|
| `admin` | `admin123` | employee (organizer) |
| `john.doe` | `customer123` | customer |
| `jane.smith` | `customer123` | customer |
| `carlos.garcia` | `customer123` | customer |

## Public: browse & search

- Browse published events; filter by **category**, **city**, **date**, or free-text.
- Toggle **✨ AI Search** to add semantic "smart results" — events matched by meaning, not keywords (see [event-service](event-service)). Off unless enabled server-side.
- Cart and lovelist are hidden until you sign in as a customer.

## Customer journey

1. **Register / log in.** Registration creates the account in Keycloak and sends a verification email (view it in MailHog at `http://localhost:8025` locally).
2. **Add to cart / lovelist.** ♥ saves an event to your lovelist; add seats to the cart. The cart is single-currency — mixing currencies prompts you to clear it first.
3. **Checkout.** Enter a **billing address** (a saved address is pre-filled; you can enter a different one and optionally save it to your account). Pay with a card — real Stripe test cards, or the [mock test cards](payment-test-cards) in mock mode.
4. **Confirmation.** On success the booking is confirmed, tickets are issued, and a confirmation email (with the event name) is sent. Abandoning checkout (pressing back) discards the unpaid reservation and releases the seats.
5. **My Bookings** — see upcoming and past bookings; cancel a booking (which refunds and cancels its tickets).
6. **My Tickets** — each ticket shows a **QR code** (its ticket number) used for entry.
7. **My Account** — update profile, preferences, and the saved billing address.

The end-to-end runtime sequence (reserve → pay → confirm → ticket) is in [Application flows](application-flows); cart/lovelist mechanics in [Cart & Lovelist overview](overview).

## Organizer journey (`employee`)

1. **Dashboard** — overview stats and your events (published / draft / cancelled).
2. **Create / edit an event** — set venue, date (and optional end date), seat categories with prices, and an optional image. New events start as **DRAFT**.
3. **Publish** — makes the event public; **Cancel** notifies attendees and stops sales.
4. **Stats** — per event: capacity, seats booked vs available, per-category breakdown, revenue from confirmed bookings, and a purchases-over-time chart. Organizers see stats only for **their own** events.
5. **Scan tickets** — at the venue, validate a ticket (mark it USED); already-used or cancelled tickets are rejected.

## Getting a token without the UI

For API/Postman testing, fetch a token straight from Keycloak:

```bash
curl -s -X POST http://localhost:8180/realms/booking-platform/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=booking-app&username=john.doe&password=customer123"
```

Use the `access_token` as `Authorization: Bearer <token>`. The [Postman collections](INSTALLATION) automate login and token capture.

## Related

- [Frontend guide](frontend-guide) · [Application flows](application-flows) · [Payment test cards](payment-test-cards) · [Cart & Lovelist overview](overview)
