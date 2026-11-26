# 서비스 공용. jar 는 맥에서 bootJar 로 만들고, build context 는 서비스의 build/libs
FROM eclipse-temurin:25-jre
ARG SERVICE
COPY ${SERVICE}-0.0.1-SNAPSHOT.jar /app/app.jar
USER 1000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
