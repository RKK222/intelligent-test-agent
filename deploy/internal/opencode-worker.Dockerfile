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

# Python 必须在与最终 worker 相同的 bullseye/glibc 2.31 基线上编译，
# 避免直接复制 bookworm Python 后在企业旧容器环境中产生 glibc 版本不兼容。
FROM ${NODE_IMAGE} AS python-runtime

ARG DEBIAN_MIRROR=https://mirrors.ustc.edu.cn/debian
ARG DEBIAN_SECURITY_MIRROR=https://mirrors.ustc.edu.cn/debian-security
ARG PYTHON_VERSION=3.13.14
ARG PYTHON_SOURCE_SIZE=23021880
ARG PYTHON_SOURCE_SHA256=639e43243c620a308f968213df9e00f2f8f62332f7adbaa7a7eeb9783057c690
ARG PYTHON_SOURCE_BASE_URL=https://mirrors.huaweicloud.com/python

COPY --from=runtime-assets /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt
COPY --from=runtime-assets /usr/share/ca-certificates /usr/share/ca-certificates

ENV PATH=/usr/local/bin:/usr/local/sbin:/usr/sbin:/usr/bin:/sbin:/bin
ENV LANG=C.UTF-8
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV PIP_DISABLE_PIP_VERSION_CHECK=1
ENV PIP_NO_CACHE_DIR=1

# 复用 Python 官方 Docker 镜像的源码构建方式，但不启用 Tk/蓝牙等非脚本运行依赖。
# 构建依赖在同一层完成后清理，只保留 ldd 解析出的运行库、pip、venv 和标准库。
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
      netbase \
      tzdata; \
    saved_apt_mark="$(apt-mark showmanual)"; \
    apt-get install -y --no-install-recommends \
      build-essential \
      curl \
      dpkg-dev \
      libbz2-dev \
      libffi-dev \
      libgdbm-dev \
      liblzma-dev \
      libncursesw5-dev \
      libreadline-dev \
      libsqlite3-dev \
      libssl-dev \
      uuid-dev \
      xz-utils \
      zlib1g-dev; \
    curl -fL --retry 3 --retry-delay 2 \
      "${PYTHON_SOURCE_BASE_URL}/${PYTHON_VERSION}/Python-${PYTHON_VERSION}.tar.xz" \
      -o /tmp/python.tar.xz; \
    test "$(stat -c '%s' /tmp/python.tar.xz)" = "${PYTHON_SOURCE_SIZE}"; \
    printf '%s  %s\n' "${PYTHON_SOURCE_SHA256}" /tmp/python.tar.xz | sha256sum -c -; \
    mkdir -p /usr/src/python; \
    tar -xJf /tmp/python.tar.xz -C /usr/src/python --strip-components=1; \
    cd /usr/src/python; \
    gnu_arch="$(dpkg-architecture --query DEB_BUILD_GNU_TYPE)"; \
    ./configure \
      --build="${gnu_arch}" \
      --enable-loadable-sqlite-extensions \
      --enable-option-checking=fatal \
      --enable-shared \
      --with-ensurepip=install; \
    make -j "$(nproc)"; \
    make install; \
    mkdir -p /usr/local/share/licenses/python /usr/local/lib/python-runtime; \
    install -m 0644 LICENSE /usr/local/share/licenses/python/LICENSE; \
    printf 'version=%s\nsource_size=%s\nsource_sha256=%s\nsource_base_url=%s\n' \
      "${PYTHON_VERSION}" \
      "${PYTHON_SOURCE_SIZE}" \
      "${PYTHON_SOURCE_SHA256}" \
      "${PYTHON_SOURCE_BASE_URL}" \
      > /usr/local/lib/python-runtime/RELEASE; \
    cd /; \
    rm -rf /tmp/python.tar.xz /usr/src/python; \
    find /usr/local -depth \
      \( \
        \( -type d -a \( -name test -o -name tests -o -name idle_test \) \) \
        -o \( -type f -a \( -name '*.pyc' -o -name '*.pyo' -o -name 'libpython*.a' \) \) \
      \) -exec rm -rf '{}' +; \
    ldconfig; \
    apt-mark auto '.*' >/dev/null; \
    apt-mark manual ${saved_apt_mark}; \
    find /usr/local -type f -executable -not -name '*tkinter*' -exec ldd '{}' ';' \
      | awk '/=>/ { so = $(NF-1); if (index(so, "/usr/local/") == 1) { next }; gsub("^/(usr/)?", "", so); printf "*%s\n", so }' \
      | sort -u \
      | xargs -rt dpkg-query --search \
      | awk 'sub(":$", "", $1) { print $1 }' \
      | sort -u \
      | xargs -r apt-mark manual; \
    apt-get purge -y --auto-remove -o APT::AutoRemove::RecommendsImportant=false; \
    rm -rf /var/lib/apt/lists/*; \
    for source in idle3 pip3 pydoc3 python3 python3-config; do \
      target="$(printf '%s' "${source}" | tr -d 3)"; \
      test -s "/usr/local/bin/${source}"; \
      test ! -e "/usr/local/bin/${target}"; \
      ln -s "${source}" "/usr/local/bin/${target}"; \
    done; \
    python3 --version | grep -Fx "Python ${PYTHON_VERSION}"; \
    python --version | grep -Fx "Python ${PYTHON_VERSION}"; \
    python3 -m pip --version; \
    python3 -m venv /tmp/python-smoke; \
    /tmp/python-smoke/bin/python -c 'import csv, hashlib, json, pathlib, sqlite3, ssl, urllib.request, zipfile; print("python stdlib and venv ok")'; \
    rm -rf /tmp/python-smoke

# 运行容器禁用用户包目录与公网索引；独立依赖包只通过受控 PYTHONPATH 只读挂载。
ENV PYTHONNOUSERSITE=1
ENV PIP_NO_INDEX=1

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

# Codex 使用官方 Linux amd64 musl 发布包；摘要、许可证和 NOTICE 均固定到同一 0.145.0 标签。
FROM ${GO_IMAGE} AS codex-download

ARG CODEX_VERSION=0.145.0
ARG CODEX_ASSET_NAME=codex-x86_64-unknown-linux-musl.tar.gz
ARG CODEX_ASSET_SIZE=113724150
ARG CODEX_ASSET_SHA256=bfaf13c9ba34f2ad764e4a916c49cf7177aeba329cf0f719e2227566fc8d662a
ARG CODEX_BWRAP_ASSET_NAME=bwrap-x86_64-unknown-linux-musl.tar.gz
ARG CODEX_BWRAP_ASSET_SIZE=261563
ARG CODEX_BWRAP_ASSET_SHA256=bf829ae02652acdb13732e3b00b3e656baaa56be2a65d50309d676df2b5d7581
ARG CODEX_BWRAP_BINARY_SHA256=77360cb751ccedc5971391444ac86a8a33c15b04d6b4a6fe45f5d25496e62c4c
ARG CODEX_BWRAP_COPYING_SHA256=b7993225104d90ddd8024fd838faf300bea5e83d91203eab98e29512acebd69c
ARG CODEX_RELEASE_BASE_URL=https://github.com/openai/codex/releases/download
ARG CODEX_LICENSE_SHA256=d17f227e4df5da1600391338865ce0f3055211760a36688f816941d58232d8dc
ARG CODEX_NOTICE_SHA256=9d71575ecfd9a843fc1677b0efb08053c6ba9fd686a0de1a6f5382fd3c220915

RUN set -eux; \
    asset_url="${CODEX_RELEASE_BASE_URL}/rust-v${CODEX_VERSION}/${CODEX_ASSET_NAME}"; \
    chunk_size=16777216; \
    index=0; \
    start=0; \
    mkdir -p /tmp/codex-parts /tmp/codex-extract /tmp/bwrap-extract /out/codex-resources; \
    while [ "${start}" -lt "${CODEX_ASSET_SIZE}" ]; do \
      end=$((start + chunk_size - 1)); \
      if [ "${end}" -ge "${CODEX_ASSET_SIZE}" ]; then \
        end=$((CODEX_ASSET_SIZE - 1)); \
      fi; \
      part="$(printf '/tmp/codex-parts/part-%03d' "${index}")"; \
      curl -fsSL --retry 3 --retry-delay 2 --range "${start}-${end}" "${asset_url}" -o "${part}" & \
      index=$((index + 1)); \
      start=$((end + 1)); \
    done; \
    wait; \
    cat /tmp/codex-parts/part-* > /tmp/codex.tar.gz; \
    test "$(stat -c '%s' /tmp/codex.tar.gz)" = "${CODEX_ASSET_SIZE}"; \
    printf '%s  %s\n' "${CODEX_ASSET_SHA256}" /tmp/codex.tar.gz | sha256sum -c -; \
    tar -xzf /tmp/codex.tar.gz -C /tmp/codex-extract; \
    codex_binary="$(find /tmp/codex-extract -maxdepth 2 -type f -name 'codex*' -print -quit)"; \
    test -n "${codex_binary}"; \
    install -m 0755 "${codex_binary}" /out/codex-official; \
    curl -fsSL --retry 3 --retry-delay 2 \
      "${CODEX_RELEASE_BASE_URL}/rust-v${CODEX_VERSION}/${CODEX_BWRAP_ASSET_NAME}" -o /tmp/bwrap.tar.gz; \
    test "$(stat -c '%s' /tmp/bwrap.tar.gz)" = "${CODEX_BWRAP_ASSET_SIZE}"; \
    printf '%s  %s\n' "${CODEX_BWRAP_ASSET_SHA256}" /tmp/bwrap.tar.gz | sha256sum -c -; \
    tar -xzf /tmp/bwrap.tar.gz -C /tmp/bwrap-extract; \
    bwrap_binary="$(find /tmp/bwrap-extract -maxdepth 2 -type f -name 'bwrap*' -print -quit)"; \
    test -n "${bwrap_binary}"; \
    install -m 0755 "${bwrap_binary}" /out/codex-resources/bwrap; \
    printf '%s  %s\n' "${CODEX_BWRAP_BINARY_SHA256}" /out/codex-resources/bwrap | sha256sum -c -; \
    curl -fsSL --retry 3 --retry-delay 2 \
      "https://raw.githubusercontent.com/openai/codex/rust-v${CODEX_VERSION}/LICENSE" -o /out/LICENSE; \
    curl -fsSL --retry 3 --retry-delay 2 \
      "https://raw.githubusercontent.com/openai/codex/rust-v${CODEX_VERSION}/NOTICE" -o /out/NOTICE; \
    curl -fsSL --retry 3 --retry-delay 2 \
      "https://raw.githubusercontent.com/openai/codex/rust-v${CODEX_VERSION}/codex-rs/vendor/bubblewrap/COPYING" -o /out/BWRAP-COPYING; \
    printf '%s  %s\n' "${CODEX_LICENSE_SHA256}" /out/LICENSE | sha256sum -c -; \
    printf '%s  %s\n' "${CODEX_NOTICE_SHA256}" /out/NOTICE | sha256sum -c -; \
    printf '%s  %s\n' "${CODEX_BWRAP_COPYING_SHA256}" /out/BWRAP-COPYING | sha256sum -c -; \
    test "$(/out/codex-official --version)" = "codex-cli ${CODEX_VERSION}"

# 固定 bullseye/glibc 2.31，兼容企业 Docker 18.09 宿主环境；
# Python 同样来自这一基线编译，不从宿主机或更高版本 glibc 镜像复制。
FROM python-runtime

ARG DEBIAN_MIRROR=https://mirrors.ustc.edu.cn/debian
ARG DEBIAN_SECURITY_MIRROR=https://mirrors.ustc.edu.cn/debian-security
ARG NPM_REGISTRY=https://registry.npmmirror.com
ARG OPENCODE_VERSION=1.18.4
ARG OPENCODE_RELEASE_COMMIT=49c69c5ed3ccf706b61b3febb43c8aaff7f8325e
ARG OPENCODE_ASSET_NAME=opencode-linux-x64-baseline.tar.gz
ARG OPENCODE_ASSET_SIZE=59265643
ARG OPENCODE_ASSET_SHA256=4d87e414607b77fef940256021e42fbbf37b8c62b06ced76b69e26c5dcbfbabc
ARG OPENCODE_BINARY_SHA256=6ce6570e7db9a40e7bd3304ebdfff607920bde8cafd2eb5587bd7a26f89ba0b5
ARG CODEX_VERSION=0.145.0
ARG CODEX_ASSET_NAME=codex-x86_64-unknown-linux-musl.tar.gz
ARG CODEX_ASSET_SIZE=113724150
ARG CODEX_ASSET_SHA256=bfaf13c9ba34f2ad764e4a916c49cf7177aeba329cf0f719e2227566fc8d662a
ARG CODEX_BWRAP_ASSET_NAME=bwrap-x86_64-unknown-linux-musl.tar.gz
ARG CODEX_BWRAP_ASSET_SIZE=261563
ARG CODEX_BWRAP_ASSET_SHA256=bf829ae02652acdb13732e3b00b3e656baaa56be2a65d50309d676df2b5d7581
ARG CODEX_BWRAP_BINARY_SHA256=77360cb751ccedc5971391444ac86a8a33c15b04d6b4a6fe45f5d25496e62c4c
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
      curl \
      git \
      jq \
      openssh-client \
      procps \
      unzip \
      zip; \
    rm -rf /var/lib/apt/lists/*; \
    test "$(getconf GNU_LIBC_VERSION)" = "glibc 2.31"; \
    git --version; \
    curl --version | head -n 1; \
    jq --version; \
    python3 --version; \
    python3 -m pip --version; \
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
COPY deploy/internal/codex-whitebox-mcp.mjs ./bin/codex-whitebox-mcp.mjs
RUN set -eux; \
    printf '%s\n' "${OPENCODE_VERSION}" > ./VERSION; \
    printf 'version=%s\nasset=%s\narchive_size=%s\narchive_sha256=%s\nbinary_sha256=%s\nrelease_commit=%s\n' \
      "${OPENCODE_VERSION}" \
      "${OPENCODE_ASSET_NAME}" \
      "${OPENCODE_ASSET_SIZE}" \
      "${OPENCODE_ASSET_SHA256}" \
      "${OPENCODE_BINARY_SHA256}" \
      "${OPENCODE_RELEASE_COMMIT}" > ./RELEASE; \
    chmod +x ./bin/opencode ./bin/opencode-official ./bin/codex-whitebox-mcp.mjs; \
    ln -s /usr/local/lib/opencode/bin/opencode /usr/local/bin/opencode; \
    /usr/local/bin/opencode --version; \
    node --input-type=module -e 'await Promise.all([import("@modelcontextprotocol/sdk/server/mcp.js"), import("@opencode-ai/plugin"), import("@opencode-ai/sdk"), import("effect"), import("zod")]); console.log("custom Tool and MCP runtime ok")'; \
    git --version; \
    ssh -V; \
    rg --version | head -n 1; \
    tini --version

WORKDIR /usr/local/lib/codex

COPY --from=codex-download /out/codex-official ./bin/codex-official
COPY --from=codex-download /out/codex-resources ./bin/codex-resources
COPY --from=codex-download /out/LICENSE ./LICENSE
COPY --from=codex-download /out/NOTICE ./NOTICE
COPY --from=codex-download /out/BWRAP-COPYING ./THIRD_PARTY_LICENSES/bubblewrap-COPYING
COPY deploy/internal/codex-whitebox-mcp-launcher.sh ./bin/test-agent-codex-mcp
COPY tools/probe-codex-whitebox-e2e.mjs ./tests/probe-codex-whitebox-e2e.mjs
COPY deploy/internal/codex-whitebox-requirements.toml /etc/codex/requirements.toml
RUN set -eux; \
    printf '%s\n' "${CODEX_VERSION}" > ./VERSION; \
    printf 'version=%s\nasset=%s\narchive_size=%s\narchive_sha256=%s\nbwrap_asset=%s\nbwrap_archive_size=%s\nbwrap_archive_sha256=%s\nbwrap_binary_sha256=%s\nlicense=Apache-2.0\n' \
      "${CODEX_VERSION}" \
      "${CODEX_ASSET_NAME}" \
      "${CODEX_ASSET_SIZE}" \
      "${CODEX_ASSET_SHA256}" \
      "${CODEX_BWRAP_ASSET_NAME}" \
      "${CODEX_BWRAP_ASSET_SIZE}" \
      "${CODEX_BWRAP_ASSET_SHA256}" \
      "${CODEX_BWRAP_BINARY_SHA256}" > ./RELEASE; \
    chmod 0755 ./bin/codex-official ./bin/codex-resources/bwrap ./bin/test-agent-codex-mcp; \
    chmod 0444 /etc/codex/requirements.toml; \
    ln -s /usr/local/lib/codex/bin/test-agent-codex-mcp /usr/local/bin/test-agent-codex-mcp; \
    test "$(./bin/codex-official --version)" = "codex-cli ${CODEX_VERSION}"; \
    printf '%s  %s\n' "${CODEX_BWRAP_BINARY_SHA256}" ./bin/codex-resources/bwrap | sha256sum -c -; \
    grep -Fx 'allowed_approval_policies = ["never"]' /etc/codex/requirements.toml; \
    grep -Fx 'allowed_web_search_modes = ["disabled"]' /etc/codex/requirements.toml

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
