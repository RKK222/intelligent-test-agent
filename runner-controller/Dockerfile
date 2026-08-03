ARG PYTHON_IMAGE
ARG UV_IMAGE

FROM ${UV_IMAGE} AS uv-bin

FROM ${PYTHON_IMAGE}

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    UV_NO_CACHE=1 \
    UV_PROJECT_ENVIRONMENT=/opt/test-agent-runner/.venv \
    PATH=/opt/test-agent-runner/.venv/bin:${PATH}

COPY --from=uv-bin /uv /usr/local/bin/uv

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends ca-certificates docker.io git git-lfs openssh-client tini; \
    rm -rf /var/lib/apt/lists/*; \
    groupadd --gid 10003 runner; \
    useradd --uid 10003 --gid 10003 --no-create-home --home-dir /nonexistent runner; \
    mkdir -p /opt/test-agent-runner; \
    chown 10003:10003 /opt/test-agent-runner; \
    git lfs version; \
    docker version --help >/dev/null

WORKDIR /opt/test-agent-runner
COPY --chown=10003:10003 pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project
COPY --chown=10003:10003 src ./src
RUN uv sync --frozen --no-dev \
    && test "$(python -c 'import sys; print(".".join(map(str, sys.version_info[:2])))')" = "3.12"

USER 10003:10003
EXPOSE 8091
ENTRYPOINT ["/usr/bin/tini", "--", "testagent-runner"]
CMD ["--host", "0.0.0.0", "--port", "8091"]
