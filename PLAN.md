# Yerevan Homes — project plan

A rental listings search platform for Yerevan. Built as a public portfolio project to show
backend (Spring Boot, MongoDB, Elasticsearch), frontend (React + TypeScript), testing
(JUnit, Testcontainers, Playwright) and DevOps (Docker, GitHub Actions, deploy).

## Goals

- A live demo URL a client can open in 10 seconds.
- Fast full-text search with filters, facets and autocomplete.
- A clear README with an architecture diagram and one-command local start.
- Green CI on every push: unit, integration and end-to-end tests.

## Non-goals (version 1)

Payments, image uploads, admin panel, messaging, maps. Add later only if useful.

## Architecture

```
React + TS (Vite) ──HTTP──▶ Spring Boot API ──▶ MongoDB (source of truth, replica set)
                                  │                     │
                                  │ search queries      │ Change Streams
                                  ▼                     ▼
                            Elasticsearch ◀── ListingIndexer (sync service)
```

- MongoDB is the source of truth. All writes go there.
- A Change Stream listener indexes every insert, update and delete into Elasticsearch.
  It stores the resume token so it continues where it stopped after a restart.
- On startup, if the index is empty, a full reindex runs from MongoDB.
- Search and suggest read from Elasticsearch. Details and favorites read from MongoDB.
- MongoDB 9 runs as a single-node replica set, because Change Streams require one. (MongoDB 8
  refuses to start on Linux kernels 6.19+, which CI runners use.)

## Data model

**Listing** (MongoDB `listings`, Elasticsearch `listings`)

| Field | Type | Notes |
|---|---|---|
| id | string | |
| title | string | full-text, boosted |
| description | string | full-text |
| district | enum | Kentron, Arabkir, Davtashen, Ajapnyak, Malatia-Sebastia, Shengavit, Erebuni, Nor Nork, Avan, Kanaker-Zeytun, Nork-Marash, Nubarashen |
| address | string | |
| priceUsd | int | monthly rent |
| rooms | int | |
| areaSqm | int | |
| floor / totalFloors | int | |
| buildingType | enum | STONE, PANEL, MONOLITH |
| amenities | string[] | balcony, parking, elevator, air conditioning, dishwasher, ... |
| furnished | boolean | |
| petsAllowed | boolean | |
| imageUrl | string | placeholder images in v1 |
| createdAt / updatedAt | instant | |

**User** (MongoDB `users`): id, email (unique), passwordHash (BCrypt), favoriteListingIds[], createdAt.

## API

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/listings/search` | no | `q, district[], minPrice, maxPrice, rooms[], furnished, petsAllowed, sort (relevance, price_asc, price_desc, newest), page, size`. Returns results + facets (district counts, rooms counts, price range). |
| GET | `/api/listings/suggest?q=` | no | Autocomplete on titles and districts. |
| GET | `/api/listings/{id}` | no | Listing details from MongoDB. |
| POST | `/api/listings` | yes | Create a listing (to demo the sync). |
| PUT | `/api/listings/{id}` | yes | Update. |
| DELETE | `/api/listings/{id}` | yes | Delete. |
| POST | `/api/auth/register` | no | Returns JWT. |
| POST | `/api/auth/login` | no | Returns JWT. |
| GET | `/api/me/favorites` | yes | List favorites. |
| PUT / DELETE | `/api/me/favorites/{listingId}` | yes | Add / remove favorite. |

OpenAPI docs at `/swagger-ui.html`. Errors use RFC 9457 problem details.

## Frontend pages

1. **Search** — search box with autocomplete, filter sidebar with facet counts, result cards, sorting, pagination. Filters live in the URL so searches can be shared.
2. **Listing details** — all fields, favorite button.
3. **Login / Register**.
4. **Favorites**.

Responsive down to 375px width, keyboard accessible (needed for the Playwright tests).

## Decisions

- Frontend: React + TypeScript (Vite).
- Roles: `USER` (search, favorites), `OWNER` (manages only their own listings), `ADMIN` (manages all listings).
- Hosting: decided at the end.

## Complex backend cases

### Concurrency

- **Optimistic locking on edits.** Every listing has a `version`. `GET` returns it as an `ETag`;
  `PUT` and `DELETE` must send `If-Match`. If someone else saved first, the API returns
  `412 Precondition Failed` instead of silently overwriting their change. Missing `If-Match`
  returns `428 Precondition Required`.
- **Atomic favorites.** Adding and removing favorites uses `$addToSet` / `$pull` in a single
  MongoDB update, so two parallel requests (two tabs, double click) never lose or duplicate an entry.
- **Ordered index updates.** The Elasticsearch document stores the Mongo `version` and is written with
  external versioning, so an older event arriving late can never overwrite a newer one.
- **Tests:** two editors racing on the same version (one wins, one gets 412); 20 parallel favorite
  toggles end in a consistent state; out-of-order index events keep the newest data.

### Advanced search

- **Multilingual text.** Title and description are indexed with English, Russian and Armenian
  analyzers (stemming) plus a standard analyzer, queried together with `multi_match`.
- **Typo tolerance.** `fuzziness: AUTO` on text, so "apartmnt" or "Kentorn" still match.
- **Relevance tuning.** Title boosted over description; exact phrase matches ranked higher;
  newer listings get a small recency boost with a decay function.
- **Correct facets with filters.** Filters go into `post_filter` and each facet aggregation applies
  every filter except its own, so selecting "Kentron" still shows counts for the other districts.
- **Deep pagination.** `search_after` with a stable tiebreaker (`id`) instead of large `from` offsets,
  so page 50 is as fast as page 1 and results don't shift between pages.
- **Autocomplete.** `search_as_you_type` field on titles, plus district names.
- **Tests:** relevance order, typo matches, each language's stemming, facet counts with combined
  filters, paging through all results with no duplicates or gaps.

## Testing

- **Unit (JUnit 5, Mockito):** query building, mapping, validation, JWT.
- **Integration (Testcontainers):** real MongoDB replica set + Elasticsearch. Covers: create in Mongo → appears in search; update → reindexed; delete → removed; search filters and facets; auth flow.
- **End-to-end (Playwright, role-based locators):** search and filter, autocomplete with keyboard, URL keeps filters, listing details, register/login, add/remove favorite, mobile viewport (no horizontal scroll), 404 page.

## DevOps

- `docker compose up` starts MongoDB (replica set), Elasticsearch, backend, frontend, and seeds demo data.
- GitHub Actions on every push: backend tests, frontend lint + typecheck + build, Playwright against the compose stack with the HTML report as an artifact on failure.
- On push to `main`: build images, deploy to a small VPS with Docker Compose behind Caddy (HTTPS).

## Repository layout

```
backend/        Spring Boot app (Maven)
frontend/       React + TypeScript (Vite)
e2e/            Playwright tests
seed/           Demo data generator
docker-compose.yml
.github/workflows/
docs/           Architecture diagram, screenshots
```

## Schedule (about 1 week)

| Day | Work |
|---|---|
| 1 | Repo, Docker Compose (Mongo replica set + Elasticsearch), backend skeleton, Listing model + CRUD |
| 2 | Change Stream sync + full reindex, Testcontainers integration tests |
| 3 | Search endpoint: full text, filters, facets, sorting, paging; suggest endpoint |
| 4 | Auth (JWT) + favorites, OpenAPI, seed data |
| 5 | Frontend: search page, details, auth, favorites |
| 6 | Playwright suite, GitHub Actions CI |
| 7 | Deploy, README, screenshots, portfolio text |
