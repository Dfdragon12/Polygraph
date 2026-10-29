# Build en dos etapas: la primera compila el .jar con Maven, la segunda arma la imagen final que
# de verdad se despliega — así la imagen final no carga con Maven ni el código fuente, solo el
# .jar y lo que hace falta en tiempo de ejecución (JRE + LibreOffice para DocumentoPdfConverter).
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
# Descarga las dependencias en su propia capa — solo se repite si pom.xml cambia, no en cada build.
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:17-jre
WORKDIR /app

# libreoffice-writer (no el paquete "libreoffice" completo) trae solo lo necesario para abrir y
# convertir .docx a PDF — evita instalar Calc/Impress/Draw, que no se usan acá y pesan bastante.
# Ver DocumentoPdfConverter: queda en el PATH del contenedor como "soffice", que es justo el valor
# por defecto de app.libreoffice.binario en application.yml — no hace falta ninguna variable de
# entorno adicional para que esto funcione en producción.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libreoffice-writer fonts-dejavu \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /build/target/*.jar app.jar

# Memoria de la JVM relativa al límite del contenedor, no a la RAM del host:
#  · MaxRAMPercentage=60: el heap usa a lo sumo el 60% del límite. El 40% restante queda para
#    metaspace/hilos y, sobre todo, para los procesos de LibreOffice (~150-300 MB cada uno, FUERA
#    del heap; hasta LIBREOFFICE_MAX_CONCURRENTES a la vez). Con más del 60% el kernel mata el
#    contenedor por OOM en vez de que Java lo maneje.
#  · ExitOnOutOfMemoryError: si igual se agota el heap, el proceso termina de una y Docker lo
#    reinicia (restart policy) — mejor que quedar vivo a medias, respondiendo errores al azar.
#    Los trabajos que estaban corriendo se recuperan solos (LimpiezaTrabajosJob).
# JAVA_OPTS permite ajustar sin reconstruir la imagen (ej. -XX:MaxRAMPercentage=50).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
