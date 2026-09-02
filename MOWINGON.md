# MOWINGON — LawnPilot Project History and Architecture

_Last updated: 2026-09-02. Covers the full evolution from the original Java CLI through Phase 7 and all in-session follow-on work._

---

## 1. Project Origin

LawnPilot began as a tightly coupled Java CLI program implementing a classic "mower simulator" kata. A rectangular lawn is described by its maximum X/Y coordinates; each mower is given a starting position and a sequence of `L`, `R`, and `F` instructions. Mowers execute sequentially, and the program prints each mower's final position.

The original codebase was correct for the reference case (`1 3 N` / `5 1 E`) but carried several structural liabilities:

- Input path was hardcoded in `Main.java`; no stdin support
- `InputParser` assumed a well-formed file and would throw raw exceptions on malformed input
- `Mower` mixed instruction decoding with movement state — a single God object
- No test harness at all; correctness was verified only by manual inspection
- `build.gradle` had no test dependencies

These were not defects in behavior — they were limits on evolution. The codebase could not safely be extended without first establishing structure and a safety net.

---

## 2. North Star Architecture

Before any code was touched, a hexagonal architecture was adopted as the governing principle. The layers:

1. **Domain Core** — immutable state model (`Lawn`, `Mower`, `Position`, `Direction`) and deterministic command execution
2. **Application Layer** — a `SimulationEngine` that orchestrates plugins, manages step events, and enforces safeguards
3. **Ports / Adapters** — CLI adapter for the original I/O path; REST and event adapters added in later phases
4. **Plugin Runtime** — independently loadable `DecisionStrategy`, `CollisionPolicy`, and `OutputFormatter` contracts with lifecycle management and isolated failure handling

The key architectural bet: keeping the original instruction-driven behavior as a default `DecisionStrategy` plugin means adding autonomous behavior is a new plugin, not a rewrite of anything that works.

---

## 3. Phase-by-Phase Narrative

### Phase 1 — Stabilization and Safety Net

**Problem:** Impossible to safely change anything without tests. Parser failures were invisible until runtime.

**What was built:**

- JUnit baseline tests covering the reference input/output
- Explicit exception model (`ParserException`, later consolidated under `exceptions/`) for malformed input
- `stdin` input path alongside the existing file path in `Main`

**Key decision:** Do not change any behavior. Every test was written against existing outputs before refactoring began. This created the safety net all subsequent work depends on.

**Quality gate:** Reference output `1 3 N` / `5 1 E` must pass unchanged after every subsequent phase.

---

### Phase 2 — Domain and Modularity Foundation

**Problem:** Core logic was tangled in a way that prevented testing individual behaviors in isolation.

**What was built:**

- `Position` and `ParsedMowerInstructions` value objects
- Parser split into `LawnDefinitionParser`, `MowerDefinitionParser`, `InstructionSequenceParser`, and `ListLineTokenizer`
- `MowerCommand` interface with `InstructionCommand` as the default implementation
- Direction helpers (`turn`, `moveForward`) extracted into `Direction` so `Mower` delegates rather than computes

**Key decision:** Parser decomposition is not cosmetic. Separating the tokenizer from the grammar validators means validation can be unit-tested without constructing a full simulation context.

---

### Phase 3 — Plugin Runtime Introduction

**Problem:** Adding new behavior (autonomous mode, alternate output formats) would require forking core logic without a plugin boundary.

**What was built:**

- Three plugin contracts: `DecisionStrategy`, `CollisionPolicy`, `OutputFormatter`
- `PluginRegistry` with versioned contract metadata checks, register/enable/disable lifecycle, and runtime fallback isolation
- Default implementations that exactly reproduce Phase 1/2 behavior

The plugin contract interface is concise by design:

```java
public interface DecisionStrategy {
    String contractVersion();
    MowerCommand decide(DecisionContext context);
}
```

**Key decision:** Plugin failure must not crash the simulation. The registry catches plugin-thrown exceptions and falls back to the default implementation, logging the failure. This isolation property is explicitly tested.

---

### Phase 4 — Autonomous Engine and Evented Execution

**Problem:** The kata simulator could only execute explicit instruction sequences. A real fleet management product needs mowers that can navigate autonomously and produce auditable traces.

**What was built:**

- `SimulationEngine.runWithTrace()` — instruction mode producing `StepEvent` records per move
- `SimulationEngine.runAutonomous()` — sensor-driven mode using `AutonomousSensorSnapshot` and `AutonomousDecisionContext` with a seeded random strategy
- Execution safeguards: max step count, timeout, `BLOCK_ALL` and `FIRST_MOVER_WINS` collision policies
- `TraceReplayService` — verifies replay continuity and final-state equality
- Non-rectangular lawn geometry via `LawnGeometry` abstraction and `MASK x,y ...` parser extension

**Key decision:** Autonomous and instruction modes share the same collision enforcement layer. No two mowers can occupy the same cell at the same logical time in either path. The domain enforces this; the `DecisionStrategy` sees occupancy as an input and cannot override it.

**Quality gate:** Determinism — identical seed produces identical autonomous run. Validated by stress tests with 400 mowers.

---

### Phase 5 — REST API and Vue Frontend

**Problem:** The simulator was only reachable from the command line. Building a product means exposing it as a web service with a typed frontend client.

**What was built:**

- Migrated backend build to Spring Boot while leaving all Phase 1–4 domain and runtime code untouched
- `POST /api/v1/simulations` — thin adapter over the existing `SimulationEngine`
- `frontend/` — Vue 3 + TypeScript project with Vite
- Build-time OpenAPI → TypeScript type generation (`scripts/generate-api.mjs` → `src/generated/api.ts`)
- Root `npm run dev` wiring both backend and frontend together

**Key decision:** The OpenAPI-derived type generation means the frontend type system is a machine-generated contract, not hand-maintained. When the backend DTO changes, `npm run build` will regenerate the types and TypeScript compilation will surface mismatches before deployment.

---

### Phase 6 — Tenant and Fleet Management Slice

**Problem:** A multi-customer product cannot share a single global namespace. Fleets, mowers, and simulation history must be scoped to tenants with role-based access.

**What was built:**

- `TenantFleetRepository` — in-memory per-tenant state tree (`TenantState → FleetState → MowerRegistration`)
- `TenantFleetService` — all mutation routes through role validation (`ADMIN`, `OPERATOR`, `VIEWER`)
- REST routes under `/api/v1/tenants/{tenantId}/...` for fleet CRUD, mower registration, tenant simulation, and history summary
- `X-Role` header-based role injection; `VIEWER` can read but not mutate
- Explicit 4xx error mappings: `TenantValidationException`, `RoleAuthorizationException`, `NotFoundException`, `ConflictException`
- Fleet geometry: each fleet carries `areaCenterLat`, `areaCenterLng`, `areaRadiusMeters` so the UI can show a geographic service area circle

**Key decision:** All tenant state lives in a ConcurrentHashMap tree rather than a database for Phase 6. This is the explicitly documented trade-off — fast to implement, correct for isolation, suitable for Phase 8 persistence promotion. Tenant scoping is enforced at the service boundary so no code below `TenantFleetService` is aware of tenants at all.

---

### Phase 7 — IoT Telemetry and Remote Command Control

**Problem:** A fleet management product is passive without live data from mowers and without the ability to issue commands back to them.

#### Backend

**Telemetry ingestion:**

- `POST .../telemetry/events` accepts `TelemetryEventDto` per mower — status, battery %, runtime, GPS coordinates, coverage
- Snapshots stored in a per-mower circular buffer (last 24) inside `FleetState`

**Remote command control:**

- `POST .../commands` issues a `MowerCommand` (PAUSE / RESUME / GOTO_AREA) with audit metadata (`requestedBy`, `issuedAt`, `reason`)
- Commands require OPERATOR or ADMIN role; VIEWER gets 403
- Safety guardrails enforced synchronously at the service layer: battery < 10% blocks PAUSE/RESUME, CRITICAL fleet health blocks GOTO_AREA, idempotent state conflicts rejected

**Telemetry progression:**

- `GET .../telemetry/mowers` returns live-advancing telemetry without needing a real edge device
- Each mower tracks a per-fleet `AtomicInteger` progression tick in `FleetState`
- Each read increments the tick and applies deterministic changes: position shifts ~0.0001° per tick in a rotating N/E/S/W pattern, battery drains 1%/tick for simulated mowers, runtime increments 1 minute/tick
- Geofence clamping (elliptical bounds around the fleet anchor) prevents coordinates drifting into water or out-of-bounds cells

**Key decision:** Guardrails are synchronous, not deferred. A rejected command returns 4xx immediately with a machine-readable reason in the body. This is intentional — an async guardrail check would create a race window where a dangerous command could be dispatched before the check completes.

#### Frontend (Phase 7)

- `MowerControlPanel.vue` — PAUSE / RESUME / RETURN_HOME / OVERRIDE buttons, visible only to OPERATOR and ADMIN
- `TrackingView.vue` — mower list, status indicators, command history panel (last 5 commands)
- `AnalyticsView.vue` — fleet health status with color-coded indicator and battery/coverage trend charts (`LineChart.vue`, `TrendChart.vue`)
- `FleetView.vue` — fleet and mower management with create/edit modals, simulated mower checkbox

#### Edge Simulator

`edge-sim/` is a standalone Node.js service. It reads a seed file (`frontend/src/data/telemetry.ts`) to discover which tenants/fleets/mowers exist, ensures topology (creates fleets/mowers if absent, tolerates 409 conflicts), and then emits a `MOWER_TELEMETRY` event per mower on a configurable interval (default 5 s). Configuration is entirely through environment variables (`EDGE_SIM_API_BASE_URL`, `EDGE_SIM_INTERVAL_MS`, `EDGE_SIM_DRY_RUN`, etc.).

**Verification:** All 47/47 frontend tests pass; backend `./gradlew test` BUILD SUCCESSFUL after Phase 7.

---

## 4. In-Session Follow-On Work (Post-UPGRADEME.md)

These changes were made after the Phase 7 UPGRADEME entry was written.

### Backend

**`DemoDataInitializer`** — A Spring `CommandLineRunner` that seeds a canonical demo tenant, 5 fleets, and 12 mowers (fleet-north carrying enough mowers to always appear populated) on every backend restart. Uses `ConflictException` catch to make re-seeding idempotent. The seed data mirrors the frontend's `telemetry.ts` so the UI works correctly against a freshly started backend without manual setup.

**CORS expansion** — `WebMvcConfigurer` CORS mapping extended to allow PUT and OPTIONS methods and the required request headers (`X-Role`, `Content-Type`, `Accept`, `Origin`). Without this, `updateFleet` and `updateMower` PUT calls were blocked by preflight.

### Frontend

**`FleetDto` typing** — `areaCenterLat`, `areaCenterLng`, `areaRadiusMeters` fields added to the TypeScript `FleetDto` type in `api/types.ts`. These fields were already in the API response body but missing from the type, causing silent `undefined` values in map rendering.

**Fleet area circles on main map** — `MowerMap.vue` now accepts a `fleetCircles` prop. `FleetView.vue` computes `tenantFleetCircles` from `fleets.value` and passes it down, so each fleet's service area is rendered as a Leaflet circle on the tracking map.

**All mowers always visible** — The main map now always renders `tenantMowers` (all fleets) rather than filtering to the currently selected fleet. This gives operators an at-a-glance fleet overview rather than a per-fleet tunnel view.

**Drag disable during circle drawing** — When `areaCircleDrawingEnabled` is true, the map calls `map.dragging.disable()` and sets a crosshair cursor. This prevents accidental map panning while a user is clicking to define the fleet area centre.

**Error messages inside modals** — Both the fleet modal and the mower modal now render the error `<div>` inside their own dialog rather than in the page body. When a modal is open, backend validation errors (e.g., duplicate fleet ID) are visible to the user rather than appearing behind the overlay.

**Auto-refresh on mower modal open** — When the mower registration/edit modal opens, the frontend makes a silent background call to re-fetch the fleet list. This ensures the geofence geometry is current when rendering geofence hints to the operator.

**Client-side geofence pre-validation** — A `startPinOutsideGeofence` computed property warns the operator before they submit if the mower's start pin lies outside the selected fleet's area circle. The backend also validates this, but surfacing it client-side reduces unnecessary round trips.

**`http.ts` PUT support** — The `method` union type in the typed HTTP client now includes `"PUT"`, resolving the TypeScript type gap that existed alongside the runtime usage.

**Favicon** — `frontend/public/favicon.svg` added, and `index.html` includes the `<link rel="icon">` tag.

---

## 5. Current System Architecture

```
┌─────────────────────────────────────────┐
│         Vue 3 + TypeScript Frontend     │
│  FleetView  TrackingView  AnalyticsView │
│  MowerMap   MowerControlPanel           │
│  tenantApi.ts  ←  api/types.ts          │
└────────────────────┬────────────────────┘
                     │ HTTP (REST + CORS)
┌────────────────────▼────────────────────┐
│         Spring Boot Backend             │
│  TenantFleetController  (REST adapter)  │
│  SimulationService      (REST adapter)  │
│      ↓ role-checked service boundary    │
│  TenantFleetService                     │
│  TenantFleetRepository  (in-memory)     │
│      ↓ domain calls                     │
│  SimulationEngine  PluginRegistry       │
│  Domain: Lawn  Mower  Direction  etc.   │
└─────────────────────────────────────────┘
                     ▲
                     │ HTTP (telemetry events)
┌────────────────────┴────────────────────┐
│         Edge Simulator (Node.js)        │
│  edge-sim/src/index.mjs                 │
│  Reads seed file → ensureTopology()     │
│  → emits MOWER_TELEMETRY events on loop │
└─────────────────────────────────────────┘
```

**In-memory state tree (per backend instance):**

```
TenantFleetRepository
  └─ TenantState (per tenantId)
       └─ FleetState (per fleetId)
            ├─ MowerRegistration[] (registered mowers)
            ├─ MowerTelemetryState (latest per mowerId)
            ├─ mowerTelemetryProgression: Map<mowerId, AtomicInteger>
            └─ CommandRecord[] (last 100, append-only)
```

---

## 6. Key Design Decisions

| Decision                                          | Rationale                                                                                                          |
| ------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| Hexagonal architecture from Phase 1               | Allowed REST, telemetry, and autonomous mode to be added as adapters/plugins without touching domain logic         |
| Plugin contracts with versioned metadata          | Incompatible plugin versions fail at registration, not at runtime                                                  |
| Instruction-driven behavior is the default plugin | Adding autonomous behavior required no changes to Phase 1–4 tests                                                  |
| In-memory state with ConcurrentHashMap            | Correct for multi-tenant isolation at current scale; promotes to persistence cleanly in Phase 8                    |
| Synchronous guardrail enforcement                 | No race window between validation and dispatch; rejection carries a machine-readable reason                        |
| `X-Role` header for role injection                | Stateless, easy to test, avoids session/cookie complexity for the MVP phase                                        |
| OpenAPI-derived TypeScript types                  | Frontend type safety is a build-time artefact of the API spec, not a hand-maintained file                          |
| Edge simulator reads frontend seed file           | Single source of truth for the demo dataset; no divergence between what the UI shows and what the simulator pushes |
| `DemoDataInitializer` idempotent seeding          | Backend restart is safe in dev; no manual setup needed for a frontend developer to get a working environment       |
| Geofence clamping on telemetry progression        | Prevents test data from drifting into water cells or implausible GPS coordinates — a hard-to-debug visual bug      |
| Drag disabled during area circle draw             | Eliminates a UX collision where a click-to-set-anchor gesture is misread as a pan gesture                          |
| Errors inside modals                              | Backend validation feedback is visible to users regardless of which modal layer is active                          |

---

## 7. Frontend Evolution

The frontend grew from a bare simulation form to a multi-view fleet operations console:

**Phase 5 — Simulation form only.** A single view accepted simulation input text and displayed the mower output lines. Types were generated from OpenAPI; the Vite dev server proxied to the Spring backend.

**Phase 6 — Fleet and mower management.** `FleetView.vue` became the primary management surface. Create/list fleets, register/list mowers. Role selector (ADMIN / OPERATOR / VIEWER) wired to the `X-Role` header. Error handling via `toFriendlyErrorMessage()` mapping API error codes to readable strings.

**Phase 7 — Live telemetry and command control.** `MowerMap.vue` (Leaflet-backed) added to `FleetView` and `TrackingView`. `AnalyticsView.vue` shows fleet health with `TrendChart.vue` (battery over time) and `LineChart.vue` (coverage). `MowerControlPanel.vue` provides command issue UI. Simulated mower registration flow added to `FleetView`. Edge simulator sends live coordinates that update the map markers in real time via polling.

**In-session refinements.** Fleet area circles rendered on the main map. All fleets visible simultaneously. Modals show inline errors. Client-side geofence pre-validation. Drag locked during area drawing. Favicon. PUT support in the HTTP client. `FleetDto` fully typed including geometry fields.

The central telemetry store (`frontend/src/data/telemetry.ts`) is the shared state layer accessed by all views. It is not a Pinia store — it is a module-level reactive singleton (`ref`/`computed` from Vue) that all views import directly. This keeps the architecture simple while supporting the polling update pattern used in Phase 7.

---

## 8. Test Coverage Summary

| Layer                 | Test class                                                       | What it covers                                                             |
| --------------------- | ---------------------------------------------------------------- | -------------------------------------------------------------------------- |
| Domain                | `DirectionTest`, `LawnTest`, `MowerTest`                         | Core model and movement rules                                              |
| Parser                | `InputParserTest`, `IntegrationTest`                             | Rectangular and MASK formats, error cases                                  |
| Commands              | `InstructionCommandTest`                                         | L/R/F execution                                                            |
| Plugins               | `PluginRegistryTest`                                             | Lifecycle, version rejection, isolation                                    |
| Simulation            | `SimulationServiceTest`                                          | Engine via REST DTO                                                        |
| Tenant / Fleet        | `TenantFleetServiceTest`, `TenantFleetServicePhase7Test`         | Tenant isolation, RBAC, command/telemetry flows                            |
| Telemetry progression | `TenantFleetServiceTelemetryProgressionTest`                     | Deterministic progression, battery drain, geofence, multi-tenant isolation |
| Remote commands       | `RemoteCommandServiceTest`                                       | Guardrail paths, command history                                           |
| Controller            | `TenantFleetControllerTest`                                      | 4xx error mapping, role rejection                                          |
| Frontend (Vitest)     | `App.spec.ts`, `FleetView.spec.ts`, `TrackingView.spec.ts`, etc. | Component rendering and API call wiring                                    |

Backend: 45+ JUnit tests. Frontend: 47 Vitest tests. All passing as of Phase 7 completion.

---

## 9. What Comes Next

The UPGRADEME.md roadmap identifies Phase 8 targets:

- Promote in-memory command and telemetry state to durable storage (relational or document store)
- Live status channel — polling or WebSocket — for near-real-time command lifecycle updates
- Fleet health endpoint completion and health-status UI finalization
- Role refinement: OPERATOR sees own commands + fleet commands; ADMIN sees all; VIEWER blocked from command history details
- Scale-readiness: observability hooks, deployment automation

The current in-memory architecture was designed from the start to promote to persistence without touching the domain layer or the REST surface. `TenantFleetRepository` is the only class that needs to change; `TenantFleetService` and the controller are already decoupled from the storage medium.
