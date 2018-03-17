FROM openjdk:8-jdk-alpine

VOLUME /tmp
ARG JAR_FILE
ADD ${JAR_FILE} target/snowstorm-1.0.0-SNAPSHOT.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","target/snowstorm-1.0.0-SNAPSHOT.jar"]
