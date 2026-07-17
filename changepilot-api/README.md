# ChangePilot API

Spring Boot 3 / Java 21 API for managing engineering changes.

## Architecture

- `com.changepilot.change.api`: HTTP DTOs and controller.
- `com.changepilot.change.service`: business rules, lifecycle validation, normalization.
- `com.changepilot.change.persistence`: JPA repository.
- `com.changepilot.change.domain`: entities and enums.
- `com.changepilot.change.common`: CORS and error handling.

The implementation keeps transport, business logic, and persistence separate without extra indirection.

## Prerequisites

- Java 21
- No separate Maven installation is required; the included wrapper uses Maven 3.9.9.

## Run

```bash
./mvnw spring-boot:run
```

## Test

```bash
./mvnw test
./mvnw verify
```

## Key URLs

- API base: `http://localhost:8080/api/engineering-changes`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- H2 console: `http://localhost:8080/h2-console`

## Configuration

- `CHANGEPILOT_ALLOWED_ORIGIN` or `changepilot.allowed-origin`: CORS origin, default `http://localhost:5173`
- `changepilot.demo-data.enabled`: default `true`; disabled in tests

## Endpoint summary

- `POST /api/engineering-changes`: create a `DRAFT` change
- `GET /api/engineering-changes?status=...`: list changes, optionally filtered by status
- `GET /api/engineering-changes/{id}`: detail view
- `PUT /api/engineering-changes/{id}`: update editable content
- `PATCH /api/engineering-changes/{id}/criteria/{criterionId}`: toggle criterion completion with `{ "completed": true|false }`
- `PATCH /api/engineering-changes/{id}/status`: transition status with `{ "targetStatus": "..." }`
- `DELETE /api/engineering-changes/{id}`: delete draft only

## Validation and normalization decisions

- Title, description, and criterion text are trimmed and must remain non-blank.
- Affected components may be empty, but each entry is trimmed and must remain non-blank.
- Duplicate affected component values are rejected after trimming.
- During `PUT`, criterion IDs update existing criteria while preserving completion; missing IDs create new incomplete criteria; omitted existing criteria are removed.
- In `VERIFIED`, criterion order, membership, text, and completion are locked. Metadata remains editable.
- `DONE` is fully read-only.

## Demo data

When demo data is enabled, startup seeds:

- `Draft API Cleanup` (`DRAFT`, `LOW`, incomplete)
- `Production Firewall Rollout` (`IN_PROGRESS`, `HIGH`, partial completion)
- `Verified Observability Upgrade` (`VERIFIED`, `MEDIUM`, all complete)

## Limitations

- H2 in-memory storage is intended for local/demo use only.
- No authentication or authorization is included.
