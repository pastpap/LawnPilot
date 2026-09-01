# LawnPilot Edge Simulator

Lightweight local IoT mower simulator that reuses the existing frontend seed mower list and emits one telemetry stream per mower to the backend telemetry ingestion endpoint.

## What it does

- Loads mower seed data from `frontend/src/data/telemetry.ts` (or a JSON file).
- Ensures each tenant/fleet/mower exists in backend (idempotent: 409 conflicts are treated as already-present).
- Starts one simulator stream per mower.
- Emits periodic telemetry events to:
  - `POST /api/v1/tenants/{tenantId}/fleets/{fleetId}/mowers/{mowerId}/telemetry/events`

## Quick start

From repo root:

```bash
cd edge-sim
npm install
cp .env.example .env
npm run start
```

Dry-run mode (no HTTP calls, only generated telemetry logs):

```bash
cd edge-sim
npm run start:dry
```

## Environment

See `.env.example`.

Main settings:

- `EDGE_SIM_API_BASE_URL` (default `http://localhost:8080/api/v1`)
- `EDGE_SIM_INTERVAL_MS` (default `5000`)
- `EDGE_SIM_ROLE` (default `OPERATOR`)
- `EDGE_SIM_MOWERS_FILE` (default `../frontend/src/data/telemetry.ts`)
- `EDGE_SIM_TENANT_FILTER` (optional: `tenant-alpha,tenant-beta`)
- `EDGE_SIM_DRY_RUN` (`true` or `false`)
- `EDGE_SIM_MAX_TICKS` (optional stop for local testing)

## Notes

- Telemetry event payload stores detailed mower state JSON in `eventData`.
- Backend currently persists telemetry as event-log entries; existing mower summary endpoint stays server-driven.
- Use `EDGE_SIM_ROLE=ADMIN` if your local role policy requires elevated mutation permissions.
