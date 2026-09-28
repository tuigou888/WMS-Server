FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
ARG MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
ENV MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL}
RUN set -eux; \
    if [ -n "${MAVEN_MIRROR_URL}" ]; then \
      printf '<settings><mirrors><mirror><id>central</id><mirrorOf>central</mirrorOf><url>%s</url></mirror></mirrors></settings>\n' "${MAVEN_MIRROR_URL}" > /app/settings-mirror.xml; \
    fi; \
    printf '#!/bin/sh\nexec mvn ${MAVEN_MIRROR_URL:+-s /app/settings-mirror.xml} "$@"\n' > /usr/local/bin/mvn-mirror; \
    chmod +x /usr/local/bin/mvn-mirror
COPY pom.xml .
RUN mvn-mirror -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn-mirror -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
# curl 仅供容器 healthcheck 探活使用（/api/v1/health/ready）
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/wms-server-*.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java","-jar","/app/app.jar"]
