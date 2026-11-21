# ===== 前端构建：Vue 3 + Vite =====
FROM node:20-alpine AS web
WORKDIR /web
ARG NPM_REGISTRY=https://registry.npmmirror.com
ENV NPM_CONFIG_REGISTRY=${NPM_REGISTRY} \
    NPM_CONFIG_FETCH_RETRIES=5 \
    NPM_CONFIG_FETCH_RETRY_MINTIMEOUT=20000 \
    NPM_CONFIG_FETCH_RETRY_MAXTIMEOUT=120000 \
    NPM_CONFIG_FETCH_TIMEOUT=600000
COPY wms-web/package*.json ./
RUN npm ci
COPY wms-web/ ./
RUN npm run build

# ===== 后端构建：Spring Boot =====
FROM maven:3.9-eclipse-temurin-21 AS server
WORKDIR /app
ARG MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
ENV MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL}
RUN set -eux; \
    if [ -n "${MAVEN_MIRROR_URL}" ]; then \
      printf '<settings><mirrors><mirror><id>central</id><mirrorOf>central</mirrorOf><url>%s</url></mirror></mirrors></settings>\n' "${MAVEN_MIRROR_URL}" > /app/settings-mirror.xml; \
    fi; \
    printf '#!/bin/sh\nexec mvn ${MAVEN_MIRROR_URL:+-s /app/settings-mirror.xml} "$@"\n' > /usr/local/bin/mvn-mirror; \
    chmod +x /usr/local/bin/mvn-mirror
COPY wms-server/pom.xml .
RUN mvn-mirror -q -DskipTests dependency:go-offline
COPY wms-server/src ./src
RUN mvn-mirror -q -DskipTests package

# ===== 运行时：JRE + nginx 同容器，由 deploy/entrypoint.sh 同时拉起 =====
FROM eclipse-temurin:21-jre
WORKDIR /app
# curl 供 healthcheck；nginx 承载前端静态资源并反代 /api/v1 到本容器内的 Java
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl nginx \
    && rm -rf /var/lib/apt/lists/*

COPY --from=server /app/target/wms-server-*.jar app.jar
COPY --from=web /web/dist /usr/share/nginx/html
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY deploy/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh \
    && rm -f /etc/nginx/sites-enabled/default

# 80 = nginx（页面 + API 单入口）；8088 = Java（仅容器内部，供 nginx 反代）；9089 = management
EXPOSE 80 8088 9089
ENTRYPOINT ["/entrypoint.sh"]
