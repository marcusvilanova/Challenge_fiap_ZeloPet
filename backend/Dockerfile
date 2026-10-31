# Backend Zelo (Spring Boot). Contexto de build: a pasta backend/.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
# Os testes rodam no CI/local (mvnw verify); aqui só empacota.
RUN ./mvnw -B -q -DskipTests package && cp target/zelo-chatbot-*.jar /app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --create-home zelo
USER zelo
WORKDIR /home/zelo
COPY --from=build /app.jar app.jar
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"
ENTRYPOINT ["java", "-jar", "app.jar"]
