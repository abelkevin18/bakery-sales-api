# Etapa 1: build
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY src/main/java .
RUN mvn clean package -DskipTests

# Etapa 2: runtime (slim)
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
CMD ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]