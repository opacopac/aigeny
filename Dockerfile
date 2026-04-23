# ──────────────────────────────────────────────────────────────────────────
#  AIgeny - Spring Boot Web Application
#  Access at: http://localhost:8080
# ──────────────────────────────────────────────────────────────────────────

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
COPY target/aigeny-*.jar aigeny.jar

# Config directory - mount to persist external application.yml
RUN mkdir -p /root/.aigeny
VOLUME ["/root/.aigeny"]

EXPOSE 8080

# Pass Spring Boot external config location so users can override settings
# by placing a aigeny.yml in the mounted ~/.aigeny directory
ENV SPRING_CONFIG_ADDITIONAL_LOCATION="optional:file:/root/.aigeny/aigeny.yml"

HEALTHCHECK --interval=20s --timeout=5s --start-period=30s \
    CMD curl -f http://localhost:8080/api/status || exit 1

ENTRYPOINT ["java", "-jar", "aigeny.jar"]
