ARG PYTHON_IMAGE=python:3.12.11-slim-bookworm
ARG UV_IMAGE=ghcr.io/astral-sh/uv:0.8.17

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
COPY scripts/download_model.py ./scripts/download_model.py
RUN python ./scripts/download_model.py \
      --destination /models/BAAI__bge-small-zh-v1.5 \
      --model BAAI/bge-small-zh-v1.5 \
      --revision 7999e1d3359715c523056ef9478215996d62a620
COPY src ./src
RUN uv sync --frozen --no-dev \
    && test "$(python -c 'import sys; print(".".join(map(str, sys.version_info[:2])))')" = "3.12"

FROM ${PYTHON_IMAGE}

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH=/opt/test-agent-memory/.venv/bin:${PATH} \
    MEM0_TELEMETRY=False \
    MEM0_TELEMETRY_SAMPLE_RATE=0 \
    HF_HUB_OFFLINE=1 \
    TRANSFORMERS_OFFLINE=1 \
    HF_DATASETS_OFFLINE=1 \
    DO_NOT_TRACK=1 \
    HF_HOME=/nonexistent/.cache/huggingface \
    SENTENCE_TRANSFORMERS_HOME=/nonexistent/.cache/sentence-transformers

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends libgomp1; \
    rm -rf /var/lib/apt/lists/*; \
    groupadd --gid 10004 memory; \
    useradd --uid 10004 --gid 10004 --no-create-home --home-dir /nonexistent memory; \
    mkdir -p /opt/test-agent-memory /data; \
    chown 10004:10004 /opt/test-agent-memory /data

ENV MEM0_DIR=/data/mem0

WORKDIR /opt/test-agent-memory
COPY --from=builder --chown=10004:10004 /opt/test-agent-memory/.venv ./.venv
COPY --from=builder --chown=10004:10004 /opt/test-agent-memory/src ./src
COPY --from=builder --chown=10004:10004 /models /models
RUN test -f /models/BAAI__bge-small-zh-v1.5/.qa-memory-model.json

USER 10004:10004
VOLUME ["/data"]
EXPOSE 18888
HEALTHCHECK --interval=10s --timeout=3s --start-period=120s --retries=12 CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:18888/memory-api/v1/health', timeout=2).read()"]
ENTRYPOINT ["testagent-memory"]
CMD ["--host", "0.0.0.0", "--port", "18888"]
