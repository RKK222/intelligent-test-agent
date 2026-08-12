ARG PYTHON_IMAGE=python:3.12.11-slim-bookworm@sha256:519591d6871b7bc437060736b9f7456b8731f1499a57e22e6c285135ae657bf7
ARG UV_IMAGE=ghcr.io/astral-sh/uv:0.8.17@sha256:e4644cb5bd56fdc2c5ea3ee0525d9d21eed1603bccd6a21f887a938be7e85be1

FROM ${UV_IMAGE} AS uv-bin
FROM ${PYTHON_IMAGE} AS builder

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    UV_NO_CACHE=1 \
    UV_PROJECT_ENVIRONMENT=/opt/test-agent-embedding/.venv \
    PATH=/opt/test-agent-embedding/.venv/bin:${PATH}

COPY --from=uv-bin /uv /usr/local/bin/uv
WORKDIR /opt/test-agent-embedding
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project
COPY scripts/download_model.py ./scripts/download_model.py
RUN python ./scripts/download_model.py \
      --destination /models/BAAI__bge-small-zh-v1.5 \
      --model BAAI/bge-small-zh-v1.5 \
      --revision 7999e1d3359715c523056ef9478215996d62a620
COPY src ./src
RUN uv sync --frozen --no-dev

FROM ${PYTHON_IMAGE}
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH=/opt/test-agent-embedding/.venv/bin:${PATH} \
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
    groupadd --gid 10005 embedding; \
    useradd --uid 10005 --gid 10005 --no-create-home --home-dir /nonexistent embedding; \
    mkdir -p /opt/test-agent-embedding; \
    chown 10005:10005 /opt/test-agent-embedding

WORKDIR /opt/test-agent-embedding
COPY --from=builder --chown=10005:10005 /opt/test-agent-embedding/.venv ./.venv
COPY --from=builder --chown=10005:10005 /opt/test-agent-embedding/src ./src
COPY --from=builder --chown=10005:10005 /models /models
RUN test -f /models/BAAI__bge-small-zh-v1.5/.testagent-embedding-model.json

USER 10005:10005
EXPOSE 18989
HEALTHCHECK --interval=10s --timeout=3s --start-period=120s --retries=12 CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:18989/health', timeout=2).read()"]
ENTRYPOINT ["testagent-embedding"]
CMD ["--host", "0.0.0.0", "--port", "18989"]
