# LawnPilot Growth and Modernization Roadmap

Date: 2026-08-30
Started by: stefan
Scope: Behavior-preserving modernization plus autonomous, plug-and-play, web-scale evolution

## Current State

LawnPilot is a correct but tightly coupled Java CLI simulator. It is a strong baseline for domain behavior but not yet ready for autonomous capabilities, plugin expansion, or web-scale usage.

Evidence:

- Hardcoded execution input path in main: lawnpilot/src/main/java/org/lawnpilot/Main.java
- Parser assumes happy-path shape and can fail on malformed input: lawnpilot/src/main/java/org/lawnpilot/InputParser.java
- Behavior logic and instruction decoding are coupled in mower execution: lawnpilot/src/main/java/org/lawnpilot/Mower.java
- Build configuration is minimal with no test harness: lawnpilot/build.gradle

## North Star Architecture

Adopt a hexagonal, modular architecture with the following layers:

1. Domain Core: immutable state model and deterministic command execution.
2. Application Layer: orchestration engine with pluggable strategies and policies.
3. Ports/Adapters: CLI adapter now, REST and event adapters for web later.
4. Plugin Runtime: independently loadable feature modules through stable contracts.

This keeps current behavior as the default strategy while allowing autonomous strategies to be added without rewriting core logic.

## Target Capabilities

1. Autonomous behavior mode in addition to instruction-driven mode.
2. Plug-and-play modules for decision strategy, collision policy, and output formatting.
3. Web API and Vue.js frontend with a path from MVP to multi-tenant operations.
4. Scale-ready runtime with observability, deployment automation, and reliability gates.
5. Tenant-scoped fleet onboarding, device registration, and role-based access control.
6. IoT sensor telemetry ingestion with replayable traces and fleet health monitoring.
7. Remote command and override control for individual mowers with safety guardrails.

## Multi-Phase Plan

## Phase 1 - Stabilization and Safety Net

Goal: Preserve current behavior and eliminate immediate engineering risk.

Work:

1. Add JUnit-based unit and integration baseline tests.
2. Add parser validation and explicit exception model.
3. Add file/stdin input path in main without changing default behavior semantics.

Quality gates:

1. Reference output remains exactly:
   1 3 N
   5 1 E
2. Malformed inputs produce readable errors.
3. Test suite passes before any structural refactors.

Completion update (2026-08-30): Completed and validated.

## Phase 2 - Domain and Modularity Foundation

Goal: Prepare clean extension seams while keeping behavior parity.

Work:

1. Introduce value objects for position and parsed mower instructions.
2. Extract direction turning/movement helpers to reduce duplication.
3. Split parser responsibilities into focused components.
4. Introduce command abstraction for L/R/F execution.

Quality gates:

1. No output changes for valid existing scenarios.
2. Domain logic is testable independent of I/O.
3. Cyclomatic complexity in main execution path decreases.

Completion update (2026-08-30): Completed and validated.

Verification notes:

- Executed test command: ./gradlew test
- Result: BUILD SUCCESSFUL
- Phase 2 implementation evidence: added Position and ParsedMowerInstructions value objects, extracted LawnDefinitionParser/MowerDefinitionParser/InstructionSequenceParser/ListLineTokenizer, introduced MowerCommand plus InstructionCommand abstraction, and moved turn/move-forward helpers into Direction and Mower collaboration.

## Phase 3 - Plugin Runtime Introduction

Goal: Support plug-and-play features without core rewrites.

Initial plugin contracts:

1. DecisionStrategy: instruction-driven default and autonomous alternatives.
2. CollisionPolicy: current boundary-only default and multi-mower policy options.
3. OutputFormatter: current text output default and JSON/web-friendly formats.

Work:

1. Defined plugin interfaces and default implementations.
2. Added plugin registry and versioned contract metadata checks.
3. Added lifecycle management hooks (register, enable, disable).
4. Added runtime fallback isolation so plugin failure does not crash simulation.

Quality gates:

1. Default plugins reproduce baseline behavior exactly.
2. Plugin failure is isolated and does not crash core engine.
3. Plugin contract compatibility tests are enforced.

Completion update (2026-08-30): Completed and validated.

Verification notes:

- Executed test command: ./gradlew test
- Result: BUILD SUCCESSFUL
- Behavior parity evidence: SimulationEngine default plugin test preserves reference outputs 1 3 N and 5 1 E.
- Isolation evidence: failing DecisionStrategy, CollisionPolicy, and OutputFormatter tests confirm runtime fallback to baseline behavior.
- Contract enforcement evidence: PluginRegistry tests reject incompatible contract versions and verify register/enable/disable lifecycle behavior.

## Phase 4 - Autonomous Engine and Evented Execution

Goal: Add autonomous mode as an explicit engine path.

Work:

1. Build autonomous simulation engine with sensor/context input.
2. Add step events for trace, replay, and diagnostics.
3. Add execution safeguards: max steps, timeout, conflict handling.
4. Enforce occupancy-safe multi-mower movement in instruction and autonomous modes.
5. Support non-rectangular plot geometry while preserving rectangular input compatibility.

Quality gates:

1. Instruction-driven mode remains unchanged.
2. Autonomous runs are deterministic for identical seeds/config.
3. Execution traces are replayable and auditable.
4. No two mowers can occupy the same cell at the same logical time.
5. Non-rectangular geometry is supported in domain/runtime without breaking parser-based rectangular inputs.

Completion update (2026-08-30): Completed and validated.

Verification notes:

- Executed test command: ./gradlew test
- Result: BUILD SUCCESSFUL
- Implementation evidence: `SimulationEngine` now provides `runWithTrace` for instruction mode and `runAutonomous` for explicit autonomous execution.
- Sensor/context evidence: autonomous execution uses `AutonomousSensorSnapshot` and `AutonomousDecisionContext` with deterministic seeded strategy behavior.
- Trace evidence: `StepEvent` captures step index, command, pre/post mower state, mode, and logical tick/timestamp for every executed autonomous/instruction step.
- Replay evidence: `TraceReplayService` verifies replay continuity and final state equality against generated execution traces.
- Safeguard evidence: autonomous tests confirm explicit halt reasons for max step bounds and timeout bounds; conflict handling policy tests validate both `BLOCK_ALL` and `FIRST_MOVER_WINS` behavior.
- Extension evidence: runtime occupancy guard blocks moves into cells occupied by another mower in both instruction and autonomous paths, including deterministic swap/cross outcomes.
- Geometry evidence: domain-level `LawnGeometry` abstraction keeps rectangular parser path as default and enables mask-based non-rectangular plots in runtime tests.
- Phase 4.x extension evidence: parser now accepts `MASK x,y [x,y ...]` as first-line lawn syntax, validates malformed/duplicate/negative cells with explicit input errors, and wires masked geometry directly into runtime execution.
- Phase 4.x stress evidence: deterministic tests now cover high mower-count autonomous conflicts (400 mowers) and long autonomous bounds (`maxSteps=5000`) with practical CI runtime.

## Phase 5 - REST API and Vue Frontend

Goal: Expose simulation as a web API and deliver a practical typed frontend path.

Work:

1. Migrated backend build to Spring Boot while preserving existing simulation domain/runtime behavior.
2. Added REST endpoint `POST /api/v1/simulations` backed by existing parser + simulation engine.
3. Added Vue 3 + TypeScript frontend in a root `frontend/` folder.
4. Added build-time OpenAPI-derived TypeScript generation into frontend source (`src/generated/api.ts`).
5. Added root scripts to run backend and frontend together.

Quality gates:

1. Existing backend tests from Phases 1-4 still pass.
2. API supports reference simulation flow with typed request/response DTOs.
3. Frontend consumes generated API types for simulation call flow.
4. Local workflow is runnable with one root command.

Completion update (2026-08-30): Completed with local validation.

Verification notes:

- Executed backend tests: `cd lawnpilot && ./gradlew test`
- Result: PASS
- Executed frontend build path with backend available: `cd frontend && npm run build`
- Result: PASS (includes `prebuild` OpenAPI type generation)

## Phase 6 - Tenant and Fleet Management Slice

Goal: Introduce tenant-scoped backend structures and role-based access while keeping existing simulation API behavior stable.

Work:

1. Added tenant-scoped in-memory repository and service layer for fleets, mower registration, and simulation history summary.
2. Added tenant REST routes under `/api/v1/tenants/{tenantId}/...` for fleet creation/listing, mower registration/listing, tenant simulation execution, and history summary.
3. Added role model from request header `X-Role` with supported values `ADMIN`, `OPERATOR`, and `VIEWER`.
4. Added explicit 4xx mappings for tenant/role validation, authorization failures, missing resources, and duplicates.
5. Updated root npm scripts so backend CORS and frontend API/OpenAPI hosts are driven by shared environment defaults.

Quality gates:

1. Existing `/api/v1/simulations` endpoint remains available and unchanged.
2. No cross-tenant fleet/mower/history leakage.
3. Viewer role can read but cannot mutate tenant data.
4. Invalid role/tenant input returns explicit 4xx responses.

Completion update (2026-08-31): Completed and validated.

Verification notes:

- Executed backend tests: `cd lawnpilot && ./gradlew test`
- Result: PASS
- Coverage evidence: `TenantFleetServiceTest` verifies tenant isolation and role restrictions; `TenantFleetControllerTest` verifies explicit role/tenant 4xx behavior.

## Phase 7 - IoT Telemetry Ingestion and Remote Mower Command Control

Goal: Add IoT sensor telemetry ingestion with fleet health monitoring and remote command control with safety guardrails. Enable operators to query fleet health and issue commands (pause/resume/goto) to individual mowers while maintaining safety constraints and auditability.

Work:

### Backend Layer

1. **Telemetry Ingestion and Storage:**
   - Enhance `MowerTelemetryState` to track telemetry history (timestamps, battery trend, coverage progression).
   - Add telemetry ingestion endpoint `POST /api/v1/tenants/{tenantId}/fleets/{fleetId}/mowers/{mowerId}/telemetry` to accept sensor updates.
   - Store telemetry snapshots in per-mower circular buffer (last 24 snapshots) for trend analysis.

2. **Fleet Health Aggregation:**
   - Add `FleetHealthDto` model with: operational mower count, total mower count, average battery %, fleet coverage progress, health status (HEALTHY/DEGRADED/CRITICAL).
   - Add endpoint `GET /api/v1/tenants/{tenantId}/fleets/{fleetId}/health` to compute fleet health from telemetry.
   - Health calculation rules: HEALTHY if >80% mowers operational and avg battery >50%; DEGRADED if >50% operational; CRITICAL otherwise.

3. **Mower Command and Control:**
   - Define `MowerCommand` domain model with: targetMowerId, action (PAUSE/RESUME/GOTO_AREA), parameters, issuedAt, issuedBy (role).
   - Add `MowerCommandRequest` DTO with: action, optional targetArea/coordinates, reason (audit log).
   - Add endpoint `POST /api/v1/tenants/{tenantId}/fleets/{fleetId}/mowers/{mowerId}/command` to issue commands.
   - Store commands in per-fleet command history (append-only, last 100 per fleet).

4. **Safety Guardrails:**
   - Commands require OPERATOR or ADMIN role (VIEWER cannot issue).
   - Battery guardrail: reject PAUSE/RESUME if mower battery <10%.
   - Fleet health guardrail: reject GOTO_AREA if fleet health is CRITICAL.
   - Mower state guardrail: reject PAUSE if mower already cutting (idempotent), reject RESUME if already idle.
   - Rate limit: max 10 commands per mower per minute per tenant.
   - Return explicit 4xx (400 for validation, 403 for guardrail violation) with guardrail reason in message.

5. **Command Trace and Auditability:**
   - Add endpoint `GET /api/v1/tenants/{tenantId}/fleets/{fleetId}/command-history` to list recent commands (sorted by issuedAt descending).
   - Include command outcome (ACCEPTED/REJECTED) and rejection reason (if any).
   - Commands persist across sessions (in-memory for Phase 7, candidates for persistence in Phase 8+).

6. **Telemetry Replay for Verification:**
   - Add `TelemetryReplayService` similar to `TraceReplayService` to verify telemetry sequence integrity.
   - Support replay of command sequences to validate fleet state transitions.
   - Enable diagnostics endpoint (internal/ops only) to validate command→telemetry causality.

### Frontend Layer

7. **Fleet Health Monitoring View:**
   - Create or enhance `AnalyticsView.vue` to display: fleet health status (visual indicator), operational mower count, average battery, coverage progress (bar chart).
   - Real-time updates via polling (5-second interval) or WebSocket prep.
   - Color-coded health status: green (HEALTHY), yellow (DEGRADED), red (CRITICAL).

8. **Mower Command Control View:**
   - Create or enhance `TrackingView.vue` to show mower list with current status and action buttons.
   - Action buttons: PAUSE, RESUME, GOTO AREA (with area selector), visible only to OPERATOR/ADMIN roles.
   - Command result feedback: show acceptance/rejection with guardrail reason.
   - Recent command log: display last 5 commands on the same view.

9. **Telemetry Integration:**
   - Frontend `tenantApi.ts` adds methods: `getMowerTelemetry()`, `getFleetHealth()`, `issueMowerCommand()`, `getCommandHistory()`.
   - Generated API types for all new DTOs via existing prebuild OpenAPI generation.

### Testing and Verification Gates

Quality gates:

1. All Phase 6 tests still pass (behavior regression check).
2. Telemetry ingestion: valid sensor updates increment history buffer; invalid inputs rejected with 400.
3. Fleet health: calculations match documented thresholds; health status transitions correctly with mower status changes.
4. Command acceptance: valid commands by OPERATOR role are accepted; safety guardrails block dangerous commands with explicit reason.
5. Command rejection: VIEWER role, low battery (<10%), CRITICAL fleet health, mower state conflicts all produce correct 4xx with reason message.
6. Auditability: all issued commands (accepted and rejected) appear in command history with issuedBy, issuedAt, outcome, reason.
7. Role-based access: VIEWER cannot see command history details; ADMIN can see all; OPERATOR sees own commands + fleet commands.
8. Rate limiting: >10 commands per mower per minute rejected with 429 (or 400 with rate-limit reason).
9. Telemetry replay: command-triggered telemetry changes are verifiable via replay validation.
10. Frontend: AnalyticsView displays fleet health with correct color coding; TrackingView action buttons enforce role visibility and show command results.

Architectural notes:

- All telemetry and command state lives in `TenantState` → `FleetState` (per-tenant, per-fleet scoping via existing model).
- Commands and telemetry are append-only within their buffers (last N = last 100 commands, last 24 telemetry snapshots per mower).
- Safety guardrails are enforced synchronously at API layer (TenantFleetService, not deferred to async processing).
- Telemetry replay is a diagnostics feature (operators can verify command→telemetry causality) but not a blocking gate.
- Existing simulation engine and traces (Phase 4) are orthogonal; Phase 7 commands operate on **registered mower fleet state**, not simulation input/output.

Completion update (2026-08-31): **Specification and architectural design complete. Implementation to be assigned to backend/frontend specialists.**

Verification plan:

- Backend: TenantFleetServiceTest + TenantFleetControllerTest extended with telemetry, health, command, guardrail test cases.
- Frontend: AnalyticsView.spec.ts + TrackingView.spec.ts for health display and command control UX.
- Integration: End-to-end test covering tenant → fleet → mower registration → telemetry ingestion → health calculation → command issuance → guardrail enforcement → command history audit.
- Load: Rate limiting validation under 50 commands/sec per fleet.
