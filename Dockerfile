# Dockerfile responsible of creating the snowstorm image. 
# The container is dependent on elasticsearch to be up and running (e.g. on port 9200).

# Use a Debian-based OpenJDK 17 image (includes apt)
FROM openjdk:17-jdk-buster

# Set up environment variables
ENV APP_HOME=/app

# Set working directory
WORKDIR $APP_HOME

# Copy Snowstorm JAR (you need to build it first with Maven)
COPY target/snowstorm*.jar /app/snowstorm.jar

# Testing purposes (import RF from disk)
COPY international_sample.zip /app/international_sample.zip

# Install net-tools and npm
RUN apt-get update && apt-get install -y \
    net-tools \
    npm \
    && rm -rf /var/lib/apt/lists/*

# Create a non-root user
RUN useradd -m -d $APP_HOME -s /bin/bash appuser

# Change ownership of the app files
RUN chown -R appuser:appuser $APP_HOME

# Switch to the non-root user
USER appuser

# Expose application port
EXPOSE 8080

# Run the app
ENTRYPOINT ["java", "-Xms2g", "-Xmx4g", "--add-opens", "java.base/java.lang=ALL-UNNAMED", "--add-opens", "java.base/java.util=ALL-UNNAMED", "-jar", "/app/snowstorm.jar"]

# Using arguments that are likely to be customized
CMD ["--elasticsearch.urls=http://es:9200","--snomed-version=http://snomed.info/sct/11000172109/version/20250315"]