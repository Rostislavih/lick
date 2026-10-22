# Образ серверной части Lick.
# Jar собирается заранее на хосте: ./gradlew :server:buildFatJar
# (модуль :core тянет Android-таргет, поэтому собирать внутри контейнера
# пришлось бы вместе с Android SDK — это лишние гигабайты).

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# не запускаем приложение от root
RUN addgroup -S lick && adduser -S lick -G lick
USER lick

COPY build/libs/server-all.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
