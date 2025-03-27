# Dockerfile responsible of creating the snowstorm image. 
# The container is dependent on elasticsearch to be up and running (e.g. on port 9200).

# Use a Debian-based OpenJDK 17 image (includes apt)
FROM openjdk:17-jdk-buster

# Set up environment variables
ENV APP_HOME=/app

# Set working directory
WORKDIR $APP_HOME

# Install tools and libraries necessary for puppeteer to work
RUN apt-get update && apt-get install -y \
    net-tools \
    unzip \
    ca-certificates \
    curl \
    fontconfig \
    locales \
    libatk1.0-0 \
    libatk-bridge2.0-0 \
    libcups2 \
    libdrm2 \
    libgbm1 \
    libglib2.0-0 \
    libgtk-3-0 \
    libnspr4 \
    libnss3 \
    libpango-1.0-0 \
    libx11-xcb1 \
    libxcomposite1 \
    libxdamage1 \
    libxrandr2 \
    libasound2 \
    libxshmfence1 \
    xdg-utils \
    && rm -rf /var/lib/apt/lists/*

# install npm & nodejs
RUN curl -sL https://deb.nodesource.com/setup_18.x -o /tmp/nodesource_setup.sh  &&  \
    bash /tmp/nodesource_setup.sh &&  \
    apt-get install -y nodejs

# Download the hl7 FHIR terminilogy package using npm to the /app directory
RUN npm --registry https://packages.simplifier.net pack hl7.terminology.r4

# Download the tool that will assist performing the LOINC import
RUN curl -fsSL https://github.com/hapifhir/hapi-fhir/releases/download/v7.2.2/hapi-fhir-7.2.2-cli.zip -o hapi-fhir-cli.zip && \
    unzip hapi-fhir-cli.zip

# Copy Snowstorm JAR (you need to build it first with Maven)
COPY target/snowstorm*.jar /app/snowstorm.jar

# Testing purposes (import RF from disk)
COPY international_sample.zip /app/international_sample.zip

# Copy Snowstorm JAR (you need to build it first with Maven)
COPY download_loinc.mjs /app/download_loinc.mjs

# Install puppeteer for downloading the LOINC release file on container startup
ENV PUPPETEER_CACHE_DIR=/app/.cache/puppeteer
RUN npm i puppeteer

# Expose application port
EXPOSE 8080

COPY entrypoint.sh $APP_HOME/entrypoint.sh

RUN chmod +x $APP_HOME/entrypoint.sh

# Create a non-root user
RUN useradd -m -d $APP_HOME -s /bin/bash appuser

# Change ownership of the app files
RUN chown -R appuser:appuser $APP_HOME

# Switch to the non-root user
USER appuser

ENTRYPOINT ["/app/entrypoint.sh"]