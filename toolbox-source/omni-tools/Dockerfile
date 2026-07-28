ARG TOOLBOX_NODE_BASE_IMAGE=node:20.18.0-alpine3.20@sha256:a1d39fe127e43881c6770abf2f0843c955607fb56eb9b45bf6f103c992c5442a
ARG TOOLBOX_NGINX_BASE_IMAGE=nginx:1.27.2-alpine3.20@sha256:d213b2a02ef4e7ec85882e8955343cdd08ab49d6548995ad18623f47017c65ee
FROM ${TOOLBOX_NODE_BASE_IMAGE} AS build
ENV CI=true
ENV HUSKY=0
ENV BASE_URL=/toolbox/apps/omni-tools/
WORKDIR /source
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm,sharing=locked \
  npm ci --ignore-scripts --no-audit --no-fund --prefer-offline
COPY . .
RUN npm run build

FROM ${TOOLBOX_NGINX_BASE_IMAGE}
COPY --from=build /source/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
CMD ["nginx", "-g", "daemon off;"]
