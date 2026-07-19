# Common Parameter Memory Values Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans and superpowers:test-driven-development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Query and refresh explicitly registered common-parameter values in every live Java process without caching the remaining common parameters.

**Architecture:** A domain SPI models one memory-backed common parameter. Configuration management owns the local registry and refresh lifecycle; API routing queries or refreshes exact `backendProcessId` targets through the shared Java router and forwarder; the existing night capacity registry becomes the first SPI implementation.

**Tech Stack:** Java 21, Spring Boot events/WebFlux, PostgreSQL/Flyway, Redis runtime topology, Vue 3, TypeScript, TanStack Vue Query, Element Plus, JUnit 5, Mockito, Vitest.

## Global Constraints

- Preserve `RepositoryCommonParameterValues` database-direct behavior for every non-registered parameter.
- Reuse `BackendJavaRouteResolver` and `BackendHttpForwarder`; do not add a private router or Redis parameter snapshot.
- Only `SUPER_ADMIN` may query exact memory values or trigger refresh.
- Runtime refresh failure retains the last valid value; startup load failure remains fatal.
- Preserve unrelated working-tree changes, do not create a branch, and finish with a Chinese commit.

### Task 1: Memory parameter SPI and local registry

- [x] Add failing tests for unique keys, stable ordering, startup load, matching/bulk events, manual refresh, and last-good retention.
- [x] Add domain key/state/result/SPI types.
- [x] Implement the configuration-management registry and local process response service.
- [x] Run configuration-management tests and keep them green.

### Task 2: Night capacity integration

- [x] Adapt the existing night-capacity tests to the SPI state contract.
- [x] Refactor `NightExecutionCapacityRegistry` into the first memory entry and remove duplicate event/startup orchestration.
- [x] Preserve the pending migration, positive-integer management validation, dynamic consumers, and environment-variable removal.
- [x] Run night runtime, configuration management, and migration tests.

### Task 3: Exact backend routing and HTTP API

- [x] Add failing resolver tests for exact backend IDs, same-server multiple Java, current fallback, and expired targets.
- [x] Extend the shared resolver and add the API routing service with concurrency 8, limit 500, and 10-second per-process timeout.
- [x] Add failing controller tests for permissions, four endpoints, partial results, and offline targets.
- [x] Implement the controller/DTOs and run API tests.

### Task 4: Frontend diagnostics drawer

- [x] Add failing shared-type/backend-api tests for the four endpoints.
- [x] Add failing component tests for on-demand loading, process cards, all/single refresh, and partial failures.
- [x] Implement types, client methods, Vue Query state, drawer markup, and scoped styles.
- [x] Run frontend tests and typecheck.

### Task 5: Documentation, verification, and commit

- [x] Update stable API, deployment, database, architecture/security standards, backend/frontend README and package docs; confirm no event-stream change.
- [x] Run targeted Maven tests, backend package, frontend test/typecheck/build, stale-reference scans, and `git diff --check`.
- [x] Review all `.agents/session-log*.md`, append one consolidated local entry when warranted, inspect the staged diff, and commit only task-owned files with a Chinese message.
