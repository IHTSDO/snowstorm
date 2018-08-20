FROM openjdk:8-jdk-alpine

VOLUME /tmp
ADD target/snowstorm-1.2.0-SNAPSHOT.jar snowstorm-1.2.0-SNAPSHOT.jar
ENTRYPOINT ["java","-jar","snowstorm-1.2.0-SNAPSHOT.jar"]
