FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw -q -DskipTests dependency:go-offline

COPY src ./src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN addgroup --system tripmind && adduser --system --ingroup tripmind tripmind

COPY --from=build /workspace/target/*.jar /app/tripmind.jar

ENV SERVER_PORT=8080
ENV SPRING_PROFILES_ACTIVE=docker
ENV SPRING_DATA_REDIS_HOST=redis
ENV SPRING_DATA_REDIS_PORT=6379

EXPOSE 8080

RUN mkdir -p /app/data && chown -R tripmind:tripmind /app
USER tripmind

ENTRYPOINT ["java","-jar","/app/tripmind.jar"]
