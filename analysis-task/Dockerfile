# syntax=docker/dockerfile:1
ARG TOOLS_IMAGE=test-agent-opencode-worker:internal
FROM ${TOOLS_IMAGE}

USER root
COPY test-agent-analysis.py /usr/local/bin/test-agent-analysis
COPY test-agent-safe-shell /usr/local/bin/test-agent-safe-shell
COPY test-agent-model-relay.py /usr/local/bin/test-agent-model-relay
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends python3; \
    rm -rf /var/lib/apt/lists/*; \
    chmod 0555 /usr/local/bin/test-agent-analysis /usr/local/bin/test-agent-safe-shell /usr/local/bin/test-agent-model-relay; \
    if ! getent group 10001 >/dev/null; then groupadd --gid 10001 analysis; fi; \
    if ! getent group 10002 >/dev/null; then groupadd --gid 10002 analysis-relay; fi; \
    if ! getent group 10003 >/dev/null; then groupadd --gid 10003 analysis-output; fi; \
    if ! getent passwd 10001 >/dev/null; then useradd --uid 10001 --gid 10001 --no-create-home --home-dir /nonexistent analysis; fi; \
    if ! getent passwd 10002 >/dev/null; then useradd --uid 10002 --gid 10002 --no-create-home --home-dir /nonexistent analysis-relay; fi; \
    test "$(getconf GNU_LIBC_VERSION)" = "glibc 2.31"; \
    python3 --version; \
    test -x /usr/local/bin/test-agent-safe-shell; \
    test -x /usr/local/bin/test-agent-model-relay; \
    test -x /usr/local/lib/codex/bin/codex-official; \
    test -x /usr/local/lib/opencode/bin/opencode-official

USER 10001:10003
WORKDIR /workspace/repos
ENTRYPOINT ["/usr/bin/tail", "-f", "/dev/null"]
