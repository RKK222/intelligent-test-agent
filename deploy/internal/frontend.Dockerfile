# syntax=docker/dockerfile:1.7

FROM node:22-bookworm-slim AS build

ARG NPM_REGISTRY=https://registry.npmmirror.com
ARG COREPACK_NPM_REGISTRY=https://registry.npmmirror.com
ARG VITE_TEST_AGENT_API_BASE_URL=

ENV COREPACK_ENABLE_DOWNLOAD_PROMPT=0
ENV COREPACK_NPM_REGISTRY=${COREPACK_NPM_REGISTRY}

WORKDIR /workspace/frontend

RUN corepack enable \
    && corepack prepare pnpm@10.25.0 --activate \
    && pnpm config set registry "${NPM_REGISTRY}"

COPY frontend/ ./
RUN corepack pnpm install --frozen-lockfile
RUN VITE_TEST_AGENT_API_BASE_URL="${VITE_TEST_AGENT_API_BASE_URL}" \
    corepack pnpm --filter @test-agent/agent-web build

FROM nginx:1.27-alpine

COPY deploy/internal/nginx/frontend.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/frontend/apps/agent-web/dist/ /usr/share/nginx/html/

EXPOSE 8080
