FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY pyproject.toml ./
COPY src/ ./
RUN ./gradlew --no-daemon :app-all:developmentBootJar :agent-connection-relay-service:bootJar \
    && app_jar="$(find app-all/build/libs -maxdepth 1 -name '*-container.jar' -print -quit)" \
    && relay_jar="$(find agent-connection-relay-service/build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -print -quit)" \
    && test -n "$app_jar" && test -n "$relay_jar" \
    && cp "$app_jar" /workspace/app.jar \
    && cp "$relay_jar" /workspace/relay.jar

FROM eclipse-temurin:21-jre AS app-all
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 python3-venv \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/pyproject.toml /opt/preprocessing/pyproject.toml
COPY --from=build /workspace/preprocessing_agent /opt/preprocessing/src/preprocessing_agent
RUN python3 -m venv /opt/preprocessing/venv \
    && /opt/preprocessing/venv/bin/pip install --no-cache-dir /opt/preprocessing \
    && /opt/preprocessing/venv/bin/python -c "import preprocessing_agent.adapters.process_cli"
ENV PATH="/opt/preprocessing/venv/bin:${PATH}"
COPY --from=build /workspace/app.jar ./app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM eclipse-temurin:21-jre AS relay
WORKDIR /app
COPY --from=build /workspace/relay.jar ./app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
