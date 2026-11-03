FROM gradle:8.14.3-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle :frontend:jsBrowserDistribution :backend:installDist --no-daemon
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/backend/build/install/backend ./backend
COPY --from=build /app/frontend/build/dist/js/productionExecutable ./frontend
COPY --from=build /app/data ./data
ENV FRONTEND_PATH=/app/frontend CATALOG_PATH=/app/data/catalog.json PORT=8080
EXPOSE 8080
ENTRYPOINT ["/app/backend/bin/backend"]
