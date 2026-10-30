# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :server:installDist --no-daemon -q

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 bfp
WORKDIR /app
COPY --from=build /src/server/build/install/server/ /app/
USER 10001
ENV PORT=8080 FARE_PROVIDER=sample
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD ["sh","-c","exec 3<>/dev/tcp/127.0.0.1/${PORT} && printf 'GET /v1/health HTTP/1.0\\r\\n\\r\\n' >&3 && head -n1 <&3 | grep -q 200"]
ENTRYPOINT ["/app/bin/server"]
