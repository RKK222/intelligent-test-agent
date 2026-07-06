# syntax=docker/dockerfile:1.7

FROM golang:1.23-bookworm AS manager-build

ARG GOPROXY=https://goproxy.cn,direct
ENV GOPROXY=${GOPROXY}
ENV CGO_ENABLED=0
ENV GOOS=linux
ENV GOARCH=amd64

WORKDIR /workspace/opencode-manager

COPY opencode-manager/go.mod opencode-manager/go.sum ./
RUN go mod download

COPY opencode-manager/ ./
RUN go build -trimpath -ldflags="-s -w" -o /out/opencode-manager ./cmd/opencode-manager

FROM node:22-bookworm-slim

ARG DEBIAN_MIRROR=https://mirrors.tuna.tsinghua.edu.cn/debian
ARG DEBIAN_SECURITY_MIRROR=https://mirrors.tuna.tsinghua.edu.cn/debian-security
ARG NPM_REGISTRY=https://registry.npmmirror.com
ARG OPENCODE_AI_PACKAGE=opencode-ai@1.17.8

RUN set -eux; \
    for file in /etc/apt/sources.list /etc/apt/sources.list.d/debian.sources; do \
      if [ -f "${file}" ]; then \
        sed -i \
          -e "s|http://deb.debian.org/debian|${DEBIAN_MIRROR}|g" \
          -e "s|http://security.debian.org/debian-security|${DEBIAN_SECURITY_MIRROR}|g" \
          -e "s|https://deb.debian.org/debian|${DEBIAN_MIRROR}|g" \
          -e "s|https://security.debian.org/debian-security|${DEBIAN_SECURITY_MIRROR}|g" \
          "${file}"; \
      fi; \
    done; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
      bash \
      ca-certificates \
      git \
      openssh-client \
      procps \
      ripgrep \
      tini; \
    rm -rf /var/lib/apt/lists/*

RUN npm config set registry "${NPM_REGISTRY}" \
    && npm install -g "${OPENCODE_AI_PACKAGE}" \
    && npm cache clean --force

COPY --from=manager-build /out/opencode-manager /usr/local/bin/opencode-manager

ENV OPENCODE_BIN=/usr/local/bin/opencode
ENV OPENCODE_MANAGER_STATE_DIR=/data/.testagent/agent-opencode/manager

WORKDIR /data/.testagent/agent-opencode/workspace

ENTRYPOINT ["tini", "--"]
CMD ["opencode-manager", "run"]
