# LawnPilot

The company LawnPilot has decided to develop an automatic lawn mower designed for rectangular areas.

The mower can be programmed to cover the entire surface.
The position of the mower is represented by a combination of coordinates `(x, y)` and a letter indicating the orientation using the English cardinal notation `(N, E, W, S)`. The lawn is divided into a grid to simplify navigation.

For example, the position of the mower can be `0, 0, N` which means it is located in the bottom-left corner of the lawn and facing North.

To control the mower, a simple sequence of letters is sent to it. The possible letters are `R`, `L`, and `F`. `R` and
`L` rotate the mower 90° to the right or left, respectively, without moving it. `F` means the mower moves forward one square in the direction it is facing, without changing its orientation.

If the position after movement is outside the lawn, the mower does not move, keeps its orientation, and processes the next command.

It is assumed that the square directly north of the position `(x, y)` has coordinates `(x, y+1)`.

To program the mower, an input file is provided, structured as follows:

- The first line defines lawn geometry:
  - Rectangular (backward compatible): `maxX maxY` where lower-left is assumed to be `(0,0)`.
  - Non-rectangular mask: `MASK x,y x,y ...` where each `x,y` pair is an allowed cell of the lawn.
- The rest of the file is used to control all deployed mowers. Each mower has two associated lines:
  - The first line gives the initial position of the mower and its orientation. The position and orientation are provided as two numbers and a letter, separated by a space.
  - The second line is a series of instructions directing the mower to explore the lawn. The instructions are a sequence of characters without spaces.

Each mower moves sequentially, meaning the second mower will not move until the first mower has completed its entire instruction set.

When a mower finishes its instruction sequence, it communicates its position and orientation.

### Input examples

Rectangular lawn (existing format):

```
5 5
1 2 N
LFLFLFLFF
3 3 E
FFRFFRFRRF
```

Non-rectangular lawn (masked cells):

```
MASK 0,0 1,0 1,1 2,1
0 0 E
FFLFF
```

For masked lawns, malformed cells (for example `1x1`), negative coordinates, and duplicate cells are rejected with explicit input errors.

## Objective

Design and write a program running on a JVM 17 or higher that implements the above specification and passes the following test.

### Test

The following file is provided as input:

```
5 5
1 2 N
LFLFLFLFF
3 3 E
FFRFFRFRRF
```

We expect the following result as the final position of the mowers:

```
1 3 N
5 1 E
```

## Phase 5 Local Development (REST API + Vue)

## Phase 6 Local Development (Tenant and Fleet Slice)

Environment variables are now wired from repository root scripts with local defaults:

- `API_HOST` (default: `http://localhost:8080`)
- `FRONTEND_ORIGIN` (default: `http://localhost:5173`)
- `VITE_API_BASE_URL` (default: inherited from `API_HOST`)
- `OPENAPI_URL` (default: `${API_HOST}/v3/api-docs`)
- `APP_CORS_ALLOWED_ORIGINS` (default: inherited from `FRONTEND_ORIGIN`)

Examples:

```bash
# root defaults
npm run dev

# custom backend host + frontend origin
API_HOST=http://localhost:8090 FRONTEND_ORIGIN=http://localhost:4173 npm run dev

# force explicit frontend API base
VITE_API_BASE_URL=http://localhost:8090 npm run build:frontend
```

### Backend (Spring Boot)

Run from the repository root:

```bash
npm run start:backend
```

`npm run start:backend` now always uses the same origin source used by root `npm run dev`:

- `APP_CORS_ALLOWED_ORIGINS` from env when provided
- otherwise falls back to `FRONTEND_ORIGIN`
- `FRONTEND_ORIGIN` default is `http://localhost:5173`

Alternative:

```bash
cd lawnpilot
./gradlew bootRun
```

API endpoint:

- `POST /api/v1/simulations`
- Body:

```json
{
  "inputLines": ["5 5", "1 2 N", "LFLFLFLFF", "3 3 E", "FFRFFRFRRF"]
}
```

Backend CORS configuration:

- Property: `app.cors.allowed-origins`
- Environment variable override: `APP_CORS_ALLOWED_ORIGINS`
- Default (when unset): `http://localhost:5173`
- Multiple origins: comma-separated

Examples:

```bash
# one origin
export APP_CORS_ALLOWED_ORIGINS=http://localhost:5173

# multiple origins
export APP_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:4173
```

### Frontend (Vue 3 + TypeScript)

Run from the repository root:

```bash
cd frontend
npm install
npm run dev
```

The frontend generates TypeScript API types from the backend OpenAPI schema at build/dev time.
Default schema URL: `http://localhost:8080/v3/api-docs`
If the backend is temporarily unavailable, the build falls back to the last generated types in `frontend/src/generated/api.ts`.

Tenant console flow (Phase 6 UI):

1. Enter `tenantId` and select role (`ADMIN`, `OPERATOR`, `VIEWER`).
2. Create a fleet, then list fleets to refresh tenant state.
3. Select a fleet, register a mower, and list mowers for that fleet.
4. Run tenant simulation with input lines.
5. Load tenant simulation history summary.

Role behavior in UI/API calls:

- All tenant routes send `X-Role` header from the selected role.
- Mutation actions (`create fleet`, `register mower`, `run simulation`) require `ADMIN` or `OPERATOR`.
- Read actions (`list fleets`, `list mowers`, `history summary`) support `VIEWER`.
- 4xx/5xx responses are rendered with a friendly message and backend details when available.

### Run Backend + Frontend Together

Run from the repository root:

```bash
npm install
cd frontend && npm install && cd ..
npm run dev
```

Defaults:

- Backend: `http://localhost:8080`
- Frontend: `http://localhost:5173`

Root `npm run dev` wiring:

- Backend CORS: `APP_CORS_ALLOWED_ORIGINS` or `FRONTEND_ORIGIN`
- Frontend runtime API URL: `VITE_API_BASE_URL` or `API_HOST`
- Frontend OpenAPI generation URL: `OPENAPI_URL` or `${API_HOST}/v3/api-docs`

### Tenant/Fleet API (Phase 6)

Role header (required on all tenant routes):

- `X-Role: ADMIN`
- `X-Role: OPERATOR`
- `X-Role: VIEWER`

Tenant-scoped endpoints:

- `POST /api/v1/tenants/{tenantId}/fleets`
- `GET /api/v1/tenants/{tenantId}/fleets`
- `POST /api/v1/tenants/{tenantId}/fleets/{fleetId}/mowers`
- `GET /api/v1/tenants/{tenantId}/fleets/{fleetId}/mowers`
- `POST /api/v1/tenants/{tenantId}/simulations`
- `GET /api/v1/tenants/{tenantId}/simulations/history/summary`

Examples:

```bash
# create fleet (ADMIN/OPERATOR)
curl -i -X POST http://localhost:8080/api/v1/tenants/tenant-alpha/fleets \
  -H 'Content-Type: application/json' \
  -H 'X-Role: ADMIN' \
  -d '{"fleetId":"fleet-1","displayName":"North Campus"}'

# register mower (ADMIN/OPERATOR)
curl -i -X POST http://localhost:8080/api/v1/tenants/tenant-alpha/fleets/fleet-1/mowers \
  -H 'Content-Type: application/json' \
  -H 'X-Role: OPERATOR' \
  -d '{"mowerId":"mower-42","model":"LP-X"}'

# list fleets (VIEWER allowed)
curl -i http://localhost:8080/api/v1/tenants/tenant-alpha/fleets -H 'X-Role: VIEWER'

# tenant simulation run + summary
curl -i -X POST http://localhost:8080/api/v1/tenants/tenant-alpha/simulations \
  -H 'Content-Type: application/json' \
  -H 'X-Role: OPERATOR' \
  -d '{"inputLines":["5 5","1 2 N","LFLFLFLFF","3 3 E","FFRFFRFRRF"]}'

curl -i http://localhost:8080/api/v1/tenants/tenant-alpha/simulations/history/summary -H 'X-Role: VIEWER'
```

Validation behavior:

- Invalid/missing role header returns `400` with clear message.
- Invalid tenant/fleet/mower identifiers return `400`.
- Unauthorized role for mutation returns `403`.
- Missing tenant fleet returns `404`.
- Duplicate fleet/mower identifiers return `409`.

### Verification

Backend tests:

```bash
cd lawnpilot
./gradlew test
```

Frontend build:

```bash
cd frontend
npm run build
```
