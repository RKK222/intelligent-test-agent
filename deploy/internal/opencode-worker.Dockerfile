ARG GO_IMAGE=golang@sha256:e87b2a5f6df2dff71ea330d55d54f4979eb380ae58a7e3aabc9d53121243e689
ARG NODE_IMAGE=node@sha256:b042c6d46a90773b82ea3f95b05457ea93ee127a73b1b47ad5ebbb1a08ec3df8

FROM ${GO_IMAGE} AS manager-build

ARG GOPROXY=https://goproxy.cn,direct
ARG MANAGER_BUILD_VERSION
ENV GOPROXY=${GOPROXY}
ENV CGO_ENABLED=0
ENV GOOS=linux
ENV GOARCH=amd64

WORKDIR /workspace/opencode-manager

COPY opencode-manager/go.mod opencode-manager/go.sum ./
RUN go mod download

COPY opencode-manager/ ./
RUN printf '%s' "${MANAGER_BUILD_VERSION}" | grep -Eq '^V[0-9]{8}\.[0-9]{6}$' \
    && go build -trimpath \
      -ldflags="-s -w -X github.com/enterprise/test-agent/opencode-manager/internal/control.buildVersion=${MANAGER_BUILD_VERSION}" \
      -o /out/opencode-manager ./cmd/opencode-manager

# tini 与 ripgrep 使用上游静态程序并校验摘要；此阶段同时向 bullseye 提供首次 HTTPS 所需的 CA 数据。
FROM ${GO_IMAGE} AS runtime-assets

ARG TINI_VERSION=0.19.0
ARG TINI_SHA256=c5b0666b4cb676901f90dfcb37106783c5fe2077b04590973b885950611b30ee
ARG RIPGREP_VERSION=15.2.0
ARG RIPGREP_SHA256=33e15bcf1624b25cdd2a55813a47a2f95dbe126268203e76aa6a585d1e7b149c

RUN set -eux; \
    curl -fL --retry 3 --retry-delay 2 \
      "https://github.com/krallin/tini/releases/download/v${TINI_VERSION}/tini-static-amd64" \
      -o /tmp/tini; \
    printf '%s  %s\n' "${TINI_SHA256}" /tmp/tini | sha256sum -c -; \
    install -m 0755 /tmp/tini /usr/local/bin/tini; \
    curl -fL --retry 3 --retry-delay 2 \
      "https://github.com/BurntSushi/ripgrep/releases/download/${RIPGREP_VERSION}/ripgrep-${RIPGREP_VERSION}-x86_64-unknown-linux-musl.tar.gz" \
      -o /tmp/ripgrep.tar.gz; \
    printf '%s  %s\n' "${RIPGREP_SHA256}" /tmp/ripgrep.tar.gz | sha256sum -c -; \
    mkdir -p /tmp/ripgrep; \
    tar -xzf /tmp/ripgrep.tar.gz -C /tmp/ripgrep; \
    install -m 0755 \
      "/tmp/ripgrep/ripgrep-${RIPGREP_VERSION}-x86_64-unknown-linux-musl/rg" \
      /usr/local/bin/rg; \
    tini --version; \
    rg --version | head -n 1

# 企业 worker 只接收上游官方 baseline 程序；源码快照用于审计和 SDK 对照，不参与二进制构建。
FROM ${GO_IMAGE} AS opencode-download

ARG OPENCODE_VERSION=1.18.4
ARG OPENCODE_ASSET_NAME=opencode-linux-x64-baseline.tar.gz
ARG OPENCODE_ASSET_SIZE=59265643
ARG OPENCODE_ASSET_SHA256=4d87e414607b77fef940256021e42fbbf37b8c62b06ced76b69e26c5dcbfbabc
ARG OPENCODE_BINARY_SHA256=6ce6570e7db9a40e7bd3304ebdfff607920bde8cafd2eb5587bd7a26f89ba0b5
ARG OPENCODE_RELEASE_BASE_URL=https://github.com/anomalyco/opencode/releases/download

RUN set -eux; \
    asset_url="${OPENCODE_RELEASE_BASE_URL}/v${OPENCODE_VERSION}/${OPENCODE_ASSET_NAME}"; \
    chunk_size=16777216; \
    index=0; \
    start=0; \
    mkdir -p /tmp/opencode-parts; \
    while [ "${start}" -lt "${OPENCODE_ASSET_SIZE}" ]; do \
      end=$((start + chunk_size - 1)); \
      if [ "${end}" -ge "${OPENCODE_ASSET_SIZE}" ]; then \
        end=$((OPENCODE_ASSET_SIZE - 1)); \
      fi; \
      part="$(printf '/tmp/opencode-parts/part-%03d' "${index}")"; \
      curl -fsSL --retry 3 --retry-delay 2 \
        --range "${start}-${end}" \
        "${asset_url}" \
        -o "${part}" & \
      index=$((index + 1)); \
      start=$((end + 1)); \
    done; \
    wait; \
    cat /tmp/opencode-parts/part-* > /tmp/opencode.tar.gz; \
    test "$(stat -c '%s' /tmp/opencode.tar.gz)" = "${OPENCODE_ASSET_SIZE}"; \
    printf '%s  %s\n' "${OPENCODE_ASSET_SHA256}" /tmp/opencode.tar.gz | sha256sum -c -; \
    mkdir -p /out; \
    tar -xzf /tmp/opencode.tar.gz -C /out; \
    if [ "${OPENCODE_BINARY_SHA256}" != "not-recorded" ]; then \
      printf '%s  %s\n' "${OPENCODE_BINARY_SHA256}" /out/opencode | sha256sum -c -; \
    fi; \
    chmod +x /out/opencode; \
    test "$(/out/opencode --version)" = "${OPENCODE_VERSION}"

# 固定 bullseye/glibc 2.31，兼容企业 Docker 18.09 宿主环境。
FROM ${NODE_IMAGE}

ARG DEBIAN_MIRROR=https://mirrors.ustc.edu.cn/debian
ARG DEBIAN_SECURITY_MIRROR=https://mirrors.ustc.edu.cn/debian-security
ARG NPM_REGISTRY=https://registry.npmmirror.com
ARG OPENCODE_VERSION=1.18.4
ARG OPENCODE_RELEASE_COMMIT=49c69c5ed3ccf706b61b3febb43c8aaff7f8325e
ARG OPENCODE_ASSET_NAME=opencode-linux-x64-baseline.tar.gz
ARG OPENCODE_ASSET_SIZE=59265643
ARG OPENCODE_ASSET_SHA256=4d87e414607b77fef940256021e42fbbf37b8c62b06ced76b69e26c5dcbfbabc
ARG OPENCODE_BINARY_SHA256=6ce6570e7db9a40e7bd3304ebdfff607920bde8cafd2eb5587bd7a26f89ba0b5
ARG OPENCODE_RUNTIME_PACKAGE_JSON=deploy/internal/opencode-node-runtime.package.json
ARG OPENCODE_RUNTIME_PACKAGE_LOCK=deploy/internal/opencode-node-runtime.package-lock.json

# node:bullseye-slim 初始层没有 CA；先从固定官方镜像复制证书数据，再通过 HTTPS 签名源安装既有工具。
COPY --from=runtime-assets /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt
COPY --from=runtime-assets /usr/share/ca-certificates /usr/share/ca-certificates
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
    apt-get \
      -o Acquire::ForceIPv4=true \
      -o Acquire::Languages=none \
      -o Acquire::PDiffs=false \
      update; \
    apt-get install -y --no-install-recommends \
      ca-certificates \
      git \
      openssh-client \
      procps; \
    rm -rf /var/lib/apt/lists/*; \
    test "$(getconf GNU_LIBC_VERSION)" = "glibc 2.31"; \
    git --version; \
    ssh -V; \
    ps --version | head -n 1

COPY --from=runtime-assets /usr/local/bin/tini /usr/local/bin/tini
COPY --from=runtime-assets /usr/local/bin/rg /usr/local/bin/rg

WORKDIR /usr/local/lib/opencode

COPY ${OPENCODE_RUNTIME_PACKAGE_JSON} ./package.json
COPY ${OPENCODE_RUNTIME_PACKAGE_LOCK} ./package-lock.json
RUN npm config set registry "${NPM_REGISTRY}" \
    && npm config set replace-registry-host always \
    && npm ci --omit=dev --ignore-scripts --no-audit --no-fund \
    && npm cache clean --force

COPY --from=opencode-download /out/opencode ./bin/opencode-official
COPY opencode-source/opencode-1.18.4/LICENSE ./LICENSE
COPY deploy/internal/opencode-runtime.gitignore ./opencode-runtime.gitignore
COPY deploy/internal/opencode-official-launcher.mjs ./bin/opencode
RUN set -eux; \
    printf '%s\n' "${OPENCODE_VERSION}" > ./VERSION; \
    printf 'version=%s\nasset=%s\narchive_size=%s\narchive_sha256=%s\nbinary_sha256=%s\nrelease_commit=%s\n' \
      "${OPENCODE_VERSION}" \
      "${OPENCODE_ASSET_NAME}" \
      "${OPENCODE_ASSET_SIZE}" \
      "${OPENCODE_ASSET_SHA256}" \
      "${OPENCODE_BINARY_SHA256}" \
      "${OPENCODE_RELEASE_COMMIT}" > ./RELEASE; \
    chmod +x ./bin/opencode ./bin/opencode-official; \
    ln -s /usr/local/lib/opencode/bin/opencode /usr/local/bin/opencode; \
    /usr/local/bin/opencode --version; \
    node --input-type=module -e 'await Promise.all([import("@opencode-ai/plugin"), import("@opencode-ai/sdk"), import("effect"), import("zod")]); console.log("custom Tool runtime ok")'; \
    git --version; \
    ssh -V; \
    rg --version | head -n 1; \
    tini --version

COPY --from=manager-build /out/opencode-manager /usr/local/bin/opencode-manager
COPY deploy/internal/opencode-worker-entrypoint.sh /usr/local/bin/opencode-worker-entrypoint
RUN chmod +x /usr/local/bin/opencode-manager /usr/local/bin/opencode-worker-entrypoint

ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
ENV SYS_DATA_ROOT_DIR=/data/testagent/data
ENV OPENCODE_BIN=/usr/local/bin/opencode
ENV OPENCODE_MANAGER_STATE_DIR=/data/testagent/data/agent-opencode/manager
ENV TEST_AGENT_PROGRAM_ROOT=/data/testagent/programs

WORKDIR /data/testagent/data/agent-opencode/workspace

ENTRYPOINT ["tini", "--", "opencode-worker-entrypoint"]
CMD ["run"]
