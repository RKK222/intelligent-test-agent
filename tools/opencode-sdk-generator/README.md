# opencode Java SDK Generator

This directory contains the generated Java SDK for opencode server plus the
configuration required to reproduce it.

## Client Choice

The SDK uses OpenAPI Generator's Java `webclient` library because the platform
architecture is based on Java 21, Spring Boot 4, WebFlux, SSE/event streaming,
and a future `OpencodeClientFacade` wrapper.

## Source

- OpenAPI source: `http://127.0.0.1:4096/doc`
- Raw snapshot: `pinned-opencode-spec.raw.json`
- Normalized snapshot: `pinned-opencode-spec.json`
- Generator version: `7.24.0`

The normalized spec only de-duplicates top-level `tags` by name and injects a
default `servers[0].url`. It does not modify `paths`, `components`, or
operation schemas.

## Regenerate

```bash
tools/generate-opencode-java-sdk.sh
```

Override the opencode server URL when needed:

```bash
OPENCODE_BASE_URL=http://127.0.0.1:4096 tools/generate-opencode-java-sdk.sh
```

## Verify

```bash
tools/opencode-sdk-generator/gradlew -p tools/opencode-sdk-generator clean build -x test --no-daemon
```

## Rules

- Do not hand edit generated Java sources.
- If generation or compilation fails, fix the generator config or safe spec
  normalization step first.
- Business modules should depend on a facade layer, not directly on generated
  SDK DTOs or API classes.
