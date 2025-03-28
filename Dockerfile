# Dockerfile responsible of creating the snowstorm image. 
# The container is dependent on elasticsearch to be up and running (e.g. on port 9200).

# Use a Debian-based OpenJDK 17 image (includes apt)
FROM openjdk:17-jdk-buster

# Set up environment variables
ENV APP_HOME=/app
ENV SNOMED_HOME=$APP_HOME/snomed
ENV LOINC_HOME=$APP_HOME/loinc
ENV HL7_HOME=$APP_HOME/hl7
ENV PUPPETEER_CACHE_DIR=$APP_HOME/.cache/puppeteer

# Install Nodejs and libraries necessary for puppeteer to work + utilities
RUN curl -sL https://deb.nodesource.com/setup_18.x -o /tmp/nodesource_setup.sh &&\
    bash /tmp/nodesource_setup.sh \
    && apt-get update && apt-get install -y \
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
    nodejs \
    && rm -rf /var/lib/apt/lists/*

#############
#### HL7 ####
#############
WORKDIR $HL7_HOME
# Download the hl7 FHIR terminology package
RUN npm --registry https://packages.simplifier.net pack hl7.terminology.r4

#############
### LOINC ###
#############
WORKDIR $LOINC_HOME
# Download the tool that will assist performing the LOINC import + puppeteer for downloading the LOINC file
RUN curl -fsSL https://github.com/hapifhir/hapi-fhir/releases/download/v7.2.2/hapi-fhir-7.2.2-cli.zip -o hapi-fhir-cli.zip && \
    unzip hapi-fhir-cli.zip && \
    rm hapi-fhir-cli.zip && \
    npm i puppeteer
# Copy puppeteer script to image
COPY download_loinc.mjs $LOINC_HOME/download_loinc.mjs

##############
### SNOMED ###
##############
WORKDIR $SNOMED_HOME
# Testing purposes (import RF from disk)
COPY international_sample.zip $SNOMED_HOME/international_sample.zip

##############
### Common ###
##############
WORKDIR $APP_HOME

# Create a non-root user, add ownership to app files and switch to it
RUN useradd -m -d $APP_HOME -s /bin/bash appuser
RUN chown -R appuser:appuser $APP_HOME

# Expose application port
EXPOSE 8080

# Prepare entrypoint
COPY entrypoint.sh $APP_HOME/entrypoint.sh
RUN chmod +x $APP_HOME/entrypoint.sh

# Copy Snowstorm JAR (you need to have built it first with Maven beforehand) + the entrypoint
COPY target/snowstorm*.jar ./snowstorm.jar

USER appuser

ENTRYPOINT ["/app/entrypoint.sh"]