FROM ${DOCKER_REGISTRY:-maven}:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

COPY .mvn/settings.xml /root/.m2/settings.xml
COPY pom.xml .

RUN mvn dependency:go-offline -Dmaven.repo.local=/root/.m2/repository -Dmaven.test.skip=true

COPY src ./src

RUN mvn clean package -Dmaven.repo.local=/root/.m2/repository -Dmaven.test.skip=true

FROM ${DOCKER_REGISTRY:-eclipse-temurin}:17-jre

WORKDIR /app

COPY --from=builder /app/target/seat-stats-1.0.0.jar app.jar

EXPOSE ${SERVER_PORT:-8140}

ENTRYPOINT ["java", "-jar", "app.jar"]