FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY . .

RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 9090

ENTRYPOINT ["java", "-jar", "app.jar"]