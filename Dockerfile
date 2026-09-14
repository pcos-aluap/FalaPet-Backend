FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S falapet && adduser -S falapet -G falapet
WORKDIR /app
COPY --chown=falapet:falapet target/falapet-backend-*.jar app.jar

USER falapet
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
