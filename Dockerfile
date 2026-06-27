# syntax=docker/dockerfile:1

# --- Build stage: Gradle로 실행 가능한 jar 빌드 ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성 캐싱을 위해 빌드 스크립트와 wrapper를 먼저 복사
COPY gradlew ./
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true

# 소스 복사 후 jar 빌드 (테스트는 배포 빌드에서 제외)
COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test

# --- Run stage: JRE만 포함한 가벼운 런타임 이미지 ---
FROM eclipse-temurin:21-jre
WORKDIR /app

# 컨테이너 메모리에 맞춰 JVM 힙을 자동 조정 (Render 무료 인스턴스는 메모리가 작음)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

COPY --from=build /app/build/libs/*.jar app.jar

# Render는 PORT 환경변수로 포트를 주입한다. 문서/로컬 참고용 노출.
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
