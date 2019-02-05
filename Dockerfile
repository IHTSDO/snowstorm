FROM openjdk:8-jdk-alpine

ARG SUID=1042
ARG SGID=1042
ENV PROJ_VERSION '2.1.0'
ENV PROJ_JAR "snowstorm-$PROJ_VERSION.jar"

VOLUME /tmp

# Create a working directory
RUN mkdir /app
WORKDIR /app

# Copy necessary files
ADD target/$PROJ_JAR $PROJ_JAR
ADD docker-entrypoint.sh docker-entrypoint.sh

# Create the snowstorm user
RUN addgroup -g $SGID snowstorm && \
    adduser -D -u $SUID -G snowstorm snowstorm

# Change permissions.
RUN chown -R snowstorm:snowstorm /app && \
    chmod u+x /app/docker-entrypoint.sh

# Run as the snowstorm user.
USER snowstorm

ENTRYPOINT ["./docker-entrypoint.sh"]
