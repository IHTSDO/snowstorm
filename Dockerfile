# Use Amazon Corretto as base image, matching the docker.base-image property in pom.xml
FROM amazoncorretto:25

# Set up environment variables
ENV APP_HOME=/app

# Set working directory
WORKDIR $APP_HOME

# Copy Snowstorm JAR (you need to build it first with Maven)
COPY target/snowstorm*.jar /app/snowstorm.jar

# Expose application port
EXPOSE 8080