# Build and unit test environment for the plugin: JDK 11 (what RuneLite plugins target) and Gradle 8.10.
FROM gradle:8.10.2-jdk11

USER gradle
WORKDIR /home/gradle/plugin

COPY --chown=gradle:gradle build.gradle settings.gradle runelite-plugin.properties ./
COPY --chown=gradle:gradle src ./src

ENTRYPOINT ["gradle", "--no-daemon", "--console=plain"]
CMD ["build"]
