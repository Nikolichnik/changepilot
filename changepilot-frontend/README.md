# ChangePilot Frontend

Self-contained React + TypeScript + Vite frontend for the ChangePilot engineering-change API.

## Architecture

- `src/api`: typed API client and `ApiClientError`.
- `src/types`: frontend types matching the backend DTO contract.
- `src/components`: concise shared UI pieces, including the shared create/edit form.
- `src/pages`: routed screens for list, create/detail-edit, and not-found.
- `src/styles/index.css`: plain CSS with a compact lifecycle rail / control-panel visual language.

The frontend uses local React state only. Backend detail responses remain the source of truth after every mutation.

## Backend contract dependency

This app is implemented against `/workspace/changepilot-api` as inspected source, especially:

- `GET /api/engineering-changes`
- `GET /api/engineering-changes/{id}`
- `POST /api/engineering-changes`
- `PUT /api/engineering-changes/{id}`
- `PATCH /api/engineering-changes/{id}/criteria/{criterionId}`
- `PATCH /api/engineering-changes/{id}/status`
- `DELETE /api/engineering-changes/{id}`

Key backend-driven behavior respected here:

- `availableTransitions` drives transition buttons.
- `deletable` drives draft delete visibility.
- `VERIFIED` locks criteria text/membership/completion while metadata remains editable.
- `DONE` is treated as fully read-only.
- Criterion completion is changed only through the dedicated PATCH endpoint.

## Prerequisites

- Node.js 20+ recommended
- npm 10+ recommended
- ChangePilot API running locally on port `8080`

## Environment configuration

Set `VITE_API_BASE_URL` to the backend origin if different from the default.

Example `.env.local`:

```bash
VITE_API_BASE_URL=http://localhost:8080
```

The frontend appends `/api/engineering-changes` internally.

## Routes

- `/`: engineering change list with backend status filtering
- `/changes/new`: create a new change
- `/changes/:id`: detail view with edit, transition, toggle, and draft delete actions
- `*`: not-found page

## Commands

```bash
npm install
npm run dev
npm run lint
npm run test
npm run build
```

## Behavior notes

- IDs and status query values are URL-encoded in the API client.
- API errors preserve `status`, backend `code`, `message`, and `fieldErrors` via `ApiClientError`.
- The shared form preserves existing criterion IDs on edit, omits removed criteria, and sends new criteria without IDs.
- After create/update/toggle/transition, local detail state is replaced with the returned backend detail payload.

## Limitations

- No authentication is included because the backend does not provide it.
- The app assumes backend enum values from the inspected API source.
