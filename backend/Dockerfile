# Imagen del backend en dos etapas: compilar con Maven y ejecutar solo con un JRE.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
# Carpeta de imagenes para MEDIA_PROVIDER=local: docker-compose monta aqui un volumen (no queda dentro de la imagen)
RUN useradd --system --uid 1001 bloop && mkdir -p /data/media && chown bloop /data/media
COPY --from=build /app/target/bloop-backend-*.jar app.jar
USER bloop
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
