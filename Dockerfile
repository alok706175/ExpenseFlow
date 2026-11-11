# ==========================================
# Multi-Stage Dockerfile for ExpenseFlow
# ==========================================

# 1. Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build application
COPY src ./src
RUN mvn clean package -DskipTests

# 2. Runtime stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

COPY --from=build /app/target/expense-tracker-1.0.0.jar app.jar
RUN chown appuser:appuser app.jar

USER appuser

# Render.com provides $PORT dynamically
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=mysql
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} -jar app.jar"]

