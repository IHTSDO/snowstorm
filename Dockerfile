# Use official OpenJDK as base image
FROM openjdk:17-jdk

# Set up environment variables
ENV APP_HOME=/app

# Set working directory
WORKDIR $APP_HOME

# Copy Snowstorm JAR (you need to build it first with Maven)
COPY target/snowstorm*.jar /app/snowstorm.jar

# Expose application port
EXPOSE 8080