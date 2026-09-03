# event-service

Owns the **event catalog**: creation, the draft → published → cancelled lifecycle, seat categories and availability, and search. Search is where this service is distinctive — it offers classic keyword search **and** optional AI-powered semantic "smart" search.

| Property | Value |
|----------|-------|
| HTTP port | 8082 |
| gRPC port | 9093 |
| Store | MongoDB `eventdb` (+ `event_vectors` for semantic search) |
| Publishes | `events.event.created / updated / published / cancelled` |

## gRPC API

`event/event_service.proto`: `CreateEvent`, `GetEvent`, `UpdateEvent`, `PublishEvent`, `CancelEvent`, `SearchEvents`, `UpdateSeatAvailability`.

`UpdateSeatAvailability` is called by [booking-service](booking-service) to decrement/restore seats as bookings are made and released.

## Package layout

`cache · config · constants · document · dto · exception · grpc · init · mapper · messaging · properties · repository · service · util · validator`

- **`document`** — `EventDocument` (title, description, category, venue, `seatCategories` with total/available seats, images, `dateTime`/`endDateTime`, status).
- **`cache`** — `FeaturedEventsCacheService`, a `@Scheduled` refresh of featured events.
- **`service` / `messaging` (semantic search)** — `SmartSearchService`, `EventSemanticSearchService`, `EventVectorIndexer`, `VectorIndexConsumer`, plus `VectorBackfillRunner` and `SemanticSearchIndexConfig`.

## Event lifecycle

`DRAFT → PUBLISHED → CANCELLED` (and `COMPLETED` after the event date). Only **published** events are visible to the public; organizers manage their own drafts. Each transition emits the matching Kafka event, which drives notifications and re-indexing. The state machine diagram is in [Application flows](application-flows).

## Semantic ("smart") search

Off by default (`SEMANTIC_SEARCH_ENABLED`). When on, `events(... aiSearch: true)` returns `smartResults` — events matched by **meaning** that the keyword query missed.

- **Embeddings:** Spring AI + a local **Ollama** `nomic-embed-text` model (no API key, no cost).
- **Storage/search:** MongoDB `$vectorSearch` over an `event_vectors` collection.
- **Off the write path:** create/update/publish/cancel emit Kafka events; `VectorIndexConsumer` embeds and upserts vectors asynchronously (with retries → DLT). `VectorBackfillRunner` indexes pre-existing events on first enable.
- **Resilient:** if Ollama is down, search degrades to keyword-only.

Setup and tuning: [Installation → Semantic Search](INSTALLATION).

## Related

- [booking-service](booking-service) (seat availability) · [notification-service](notification-service) (event emails) · [Application flows](application-flows)
