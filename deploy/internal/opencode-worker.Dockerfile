ARG GO_IMAGE=golang@sha256:167053a2bb901972bf2c1611f8f52c44d5fe7e762e5cab213708d82c421614db
ARG BUN_IMAGE=oven/bun@sha256:9dba1a1b43ce28c9d7931bfc4eb00feb63b0114720a0277a8f939ae4dfc9db6f
ARG NODE_IMAGE=node@sha256:6c74791e557ce11fc957704f6d4fe134a7bc8d6f5ca4403205b2966bd488f6b3
ARG DEBIAN_MIRROR=https://mirrors.tuna.tsinghua.edu.cn/debian
ARG DEBIAN_SECURITY_MIRROR=https://mirrors.tuna.tsinghua.edu.cn/debian-security

FROM ${GO_IMAGE} AS manager-build

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

FROM --platform=$BUILDPLATFORM ${BUN_IMAGE} AS opencode-node-build

ARG OPENCODE_VERSION=1.17.8
ARG OPENCODE_SOURCE_COMMIT=11e47f91496005aab4d7c5a2d0a7da5d2651b4ac
ARG OPENCODE_SOURCE_REPOSITORY=https://github.com/anomalyco/opencode.git
ARG NPM_REGISTRY=https://registry.npmmirror.com
ARG DEBIAN_MIRROR
ARG DEBIAN_SECURITY_MIRROR

# Bun 只在联网构建阶段把上游 TypeScript 打成 Node ESM bundle；最终运行镜像不复制 Bun。
# 固定 tag 对应 commit，避免上游 tag 漂移后在不知情的情况下生成不同交付物。
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends ca-certificates; \
    rm -rf /var/lib/apt/lists/*

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
    apt-get install -y --no-install-recommends git; \
    rm -rf /var/lib/apt/lists/*; \
    git clone --depth 1 --branch "v${OPENCODE_VERSION}" "${OPENCODE_SOURCE_REPOSITORY}" /workspace/opencode; \
    test "$(git -C /workspace/opencode rev-parse HEAD)" = "${OPENCODE_SOURCE_COMMIT}"

WORKDIR /workspace/opencode

# 只安装 Node server 构建所需 workspace，跳过所有生命周期脚本；运行期原生 PTY
# 依赖会在目标 linux/amd64 Node 镜像中单独按 lockfile 安装。
RUN bun install \
      --no-save \
      --ignore-scripts \
      --filter opencode \
      --filter @opencode-ai/script \
      --registry "${NPM_REGISTRY}"

COPY deploy/internal/opencode-node-compat.patch /tmp/opencode-node-compat.patch
COPY deploy/internal/opencode-models.snapshot.json /tmp/opencode-models.snapshot.json
RUN git apply --check /tmp/opencode-node-compat.patch \
    && git apply /tmp/opencode-node-compat.patch

RUN MODELS_DEV_API_JSON=/tmp/opencode-models.snapshot.json \
      bun run --cwd packages/opencode script/build-node.ts \
    && test -s packages/opencode/dist/node/node.js

FROM ${NODE_IMAGE}

ARG DEBIAN_MIRROR=https://mirrors.tuna.tsinghua.edu.cn/debian
ARG DEBIAN_SECURITY_MIRROR=https://mirrors.tuna.tsinghua.edu.cn/debian-security
ARG NPM_REGISTRY=https://registry.npmmirror.com
ARG OPENCODE_VERSION=1.17.8

# node:bookworm-slim 初始层不保证包含 CA 根证书；先用默认 Debian 源安装证书，
# 再切到可配置的 HTTPS 镜像源，避免 apt update 在证书校验阶段失败。
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends ca-certificates; \
    rm -rf /var/lib/apt/lists/*

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
      git \
      openssh-client \
      procps \
      ripgrep \
      tini; \
    rm -rf /var/lib/apt/lists/*

WORKDIR /usr/local/lib/opencode-node

COPY deploy/internal/opencode-node-runtime.package.json ./package.json
COPY deploy/internal/opencode-node-runtime.package-lock.json ./package-lock.json
RUN npm config set registry "${NPM_REGISTRY}" \
    && npm config set replace-registry-host always \
    && npm ci --omit=dev --ignore-scripts --no-audit --no-fund \
    && npm cache clean --force

COPY --from=opencode-node-build /workspace/opencode/packages/opencode/dist/node ./server
COPY --from=opencode-node-build /workspace/opencode/LICENSE ./LICENSE
COPY deploy/internal/opencode-node-launcher.mjs ./bin/opencode
RUN set -eux; \
    printf '%s\n' "${OPENCODE_VERSION}" > ./VERSION; \
    chmod +x ./bin/opencode; \
    ln -s /usr/local/lib/opencode-node/bin/opencode /usr/local/bin/opencode; \
    /usr/local/bin/opencode --version; \
    node -e 'import("@lydell/node-pty").then(() => process.stdout.write("node-pty ok\\n"))'

COPY --from=manager-build /out/opencode-manager /usr/local/bin/opencode-manager
COPY deploy/internal/opencode-worker-entrypoint.sh /usr/local/bin/opencode-worker-entrypoint
RUN chmod +x /usr/local/bin/opencode-manager /usr/local/bin/opencode-worker-entrypoint

ENV SYS_DATA_ROOT_DIR=/data/testagent/data
ENV OPENCODE_BIN=/usr/local/bin/opencode
ENV OPENCODE_MANAGER_STATE_DIR=/data/testagent/data/agent-opencode/manager
ENV TEST_AGENT_PROGRAM_ROOT=/data/testagent/programs

WORKDIR /data/testagent/data/agent-opencode/workspace

ENTRYPOINT ["tini", "--", "opencode-worker-entrypoint"]
CMD ["run"]
