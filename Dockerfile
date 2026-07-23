# =======================================================
# Stage 1: Build the Frontend (React + TypeScript + Vite)
# =======================================================
FROM node:20-alpine AS frontend-builder
WORKDIR /app/frontend

# Copy dependencies list and install
COPY frontend/package.json frontend/.npmrc ./
RUN npm install --include=optional --no-package-lock

# Copy code and build
COPY frontend/ ./
RUN npm run build

# =======================================================
# Stage 2: Build the Spring Boot Backend with Maven
# =======================================================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS backend-builder
WORKDIR /app

# Copy root pom and module poms first to cache dependencies
COPY pom.xml ./
COPY common/pom.xml ./common/
COPY persistence/pom.xml ./persistence/
COPY backend/pom.xml ./backend/
COPY frontend/pom.xml ./frontend/

# Download dependencies (offline mode)
RUN mvn dependency:go-offline -B

# Copy backend source code
COPY common/src ./common/src
COPY persistence/src ./persistence/src
COPY backend/src ./backend/src

# Copy the frontend built assets from Stage 1 into backend resources static folder
COPY --from=frontend-builder /app/frontend/dist ./backend/src/main/resources/static

# Build the final self-contained jar file
RUN mvn clean package -pl backend -am -DskipTests

# Rename jar for consistent layer extraction naming
RUN cp backend/target/forgesys-backend.jar /app/application.jar

# Extract Spring Boot layers for efficient Docker layer caching
RUN java -Djarmode=tools -jar /app/application.jar extract --layers --destination /app/extracted

# =======================================================
# Stage 3: Minimal Java Runtime Environment
# =======================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-root system user for security
RUN addgroup -S systemgroup && adduser -S systemuser -G systemgroup
USER systemuser

# Copy extracted layers — each COPY creates a separate Docker layer
# Order matters: least likely to change first (dependencies) → most likely last (application)
COPY --from=backend-builder /app/extracted/dependencies/ ./
COPY --from=backend-builder /app/extracted/spring-boot-loader/ ./
COPY --from=backend-builder /app/extracted/snapshot-dependencies/ ./
COPY --from=backend-builder /app/extracted/application/ ./

# Expose backend server port
EXPOSE 8080

# Configure Spring profile and run
ENV SPRING_PROFILES_ACTIVE=prod

# Health check — verifies app + DB + Redis connectivity via actuator
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# JVM container awareness: MaxRAMPercentage lets the JVM read container memory limits
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+UseG1GC", "-jar", "application.jar"]
