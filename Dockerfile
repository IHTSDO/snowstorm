FROM openjdk:8-jdk-alpine
LABEL maintainer="IHTSDO <tooling@snomed.org>"

VOLUME /tmp
RUN mkdir /snomed-drools-rules
ARG JAR_FILE
ADD ${JAR_FILE} snowstorm.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","snowstorm.jar"]
