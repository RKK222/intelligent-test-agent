ARG PYTHON_IMAGE
ARG UV_IMAGE

FROM ${UV_IMAGE} AS uv-bin

FROM ${PYTHON_IMAGE}

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    UV_NO_CACHE=1 \
    UV_PROJECT_ENVIRONMENT=/opt/test-agent-workflow/.venv \
    PATH=/opt/test-agent-workflow/.venv/bin:${PATH}

COPY --from=uv-bin /uv /usr/local/bin/uv

RUN set -eux; \
    groupadd --gid 10002 workflow; \
    useradd --uid 10002 --gid 10002 --no-create-home --home-dir /nonexistent workflow; \
    mkdir -p /opt/test-agent-workflow; \
    chown 10002:10002 /opt/test-agent-workflow

WORKDIR /opt/test-agent-workflow
COPY --chown=10002:10002 pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project

COPY --chown=10002:10002 src ./src
COPY --chown=10002:10002 migrations ./migrations
COPY --chown=10002:10002 alembic.ini ./alembic.ini
RUN uv sync --frozen --no-dev \
    && test "$(python -c 'import sys; print(".".join(map(str, sys.version_info[:2])))')" = "3.12"

USER 10002:10002
EXPOSE 8090
ENTRYPOINT ["testagent-workflow"]
CMD ["api", "--host", "0.0.0.0", "--port", "8090"]
