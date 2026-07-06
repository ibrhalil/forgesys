# =======================================================
# Stage 1: Build the Frontend (React + TypeScript + Vite)
# =======================================================
FROM node:20-alpine AS frontend-builder
WORKDIR /app/frontend

# Copy dependencies list and install
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci

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
COPY backend/pom.xml ./backend/
COPY frontend/pom.xml ./frontend/

# Download dependencies (offline mode)
RUN mvn dependency:go-offline -B

# Copy backend source code
COPY backend/src ./backend/src

# Copy the frontend built assets from Stage 1 into backend resources static folder
COPY --from=frontend-builder /app/frontend/dist ./backend/src/main/resources/static

# Build the final self-contained jar file
# We build only the backend module since the frontend was already built in Stage 1
RUN mvn clean package -pl backend -DskipTests -Drevision=0.0.2

# =======================================================
# Stage 3: Minimal Java Runtime Environment
# =======================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-root system user for security
RUN addgroup -S systemgroup && adduser -S systemuser -G systemgroup
USER systemuser

# Copy the built jar from the builder stage
COPY --from=backend-builder /app/backend/target/systemforge-backend.jar ./app.jar

# Expose backend server port
EXPOSE 8080

# Configure Spring profile and run
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "app.jar"]
