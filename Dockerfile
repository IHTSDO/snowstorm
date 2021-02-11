FROM maven:3.3-jdk-8 AS builder
COPY pom.xml /usr/src/snowstorm/pom.xml
RUN cd /usr/src/snowstorm && mvn dependency:go-offline -B --fail-never
COPY . /usr/src/snowstorm
RUN cd /usr/src/snowstorm && mvn clean install -DskipTests


FROM openjdk:8-jdk-alpine
LABEL maintainer="SNOMED International <tooling@snomed.org>"

ARG SUID=1042
ARG SGID=1042

VOLUME /tmp

# Create a working directory
RUN mkdir /app
WORKDIR /app

RUN mkdir /snomed-drools-rules

# Copy necessary files
COPY --from=builder /usr/src/snowstorm/target/snowstorm-*.jar snowstorm.jar

# Create the snowstorm user
RUN addgroup -g $SGID snowstorm && \
    adduser -D -u $SUID -G snowstorm snowstorm

# Change permissions.
RUN chown -R snowstorm:snowstorm /app

# Run as the snowstorm user.
USER snowstorm
EXPOSE 8080
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","snowstorm.jar"]
