# --- Stage 1: Build ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copier le pom.xml et télécharger les dépendances (cache)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copier le code source et compiler le JAR
COPY src ./src
RUN mvn clean package -DskipTests

# --- Stage 2: Runtime ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copier le JAR généré depuis l'étape de build
COPY --from=build /app/target/*.jar app.jar

# Render définit PORT au runtime (10000 par défaut)
EXPOSE 10000

# Health check géré par Render via render.yaml (healthCheckPath)
ENTRYPOINT ["java", "-Xmx420m", "-Xms160m", "-jar", "app.jar"]
