ARG PYTHON_IMAGE=python:3.12.11-slim-bookworm@sha256:519591d6871b7bc437060736b9f7456b8731f1499a57e22e6c285135ae657bf7
ARG UV_IMAGE=ghcr.io/astral-sh/uv:0.8.17@sha256:e4644cb5bd56fdc2c5ea3ee0525d9d21eed1603bccd6a21f887a938be7e85be1

FROM ${UV_IMAGE} AS uv-bin

FROM ${PYTHON_IMAGE} AS builder

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    UV_NO_CACHE=1 \
    UV_PROJECT_ENVIRONMENT=/opt/test-agent-memory/.venv \
    PATH=/opt/test-agent-memory/.venv/bin:${PATH}

COPY --from=uv-bin /uv /usr/local/bin/uv
WORKDIR /opt/test-agent-memory
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project
COPY src ./src
COPY alembic.ini ./alembic.ini
COPY alembic ./alembic
RUN uv sync --frozen --no-dev \
    && test "$(python -c 'import sys; print(".".join(map(str, sys.version_info[:2])))')" = "3.12"

FROM ${PYTHON_IMAGE}

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH=/opt/test-agent-memory/.venv/bin:${PATH} \
    MEM0_TELEMETRY=False \
    MEM0_TELEMETRY_SAMPLE_RATE=0 \
    DO_NOT_TRACK=1

RUN set -eux; \
    groupadd --gid 10004 memory; \
    useradd --uid 10004 --gid 10004 --no-create-home --home-dir /nonexistent memory; \
    mkdir -p /opt/test-agent-memory; \
    chown 10004:10004 /opt/test-agent-memory

ENV MEM0_DIR=/tmp/mem0

WORKDIR /opt/test-agent-memory
COPY --from=builder --chown=10004:10004 /opt/test-agent-memory/.venv ./.venv
COPY --from=builder --chown=10004:10004 /opt/test-agent-memory/src ./src
COPY --from=builder --chown=10004:10004 /opt/test-agent-memory/alembic.ini ./alembic.ini
COPY --from=builder --chown=10004:10004 /opt/test-agent-memory/alembic ./alembic

USER 10004:10004
EXPOSE 18888
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=12 CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:18888/health', timeout=2).read()"]
ENTRYPOINT ["testagent-memory"]
CMD ["--host", "0.0.0.0", "--port", "18888"]
