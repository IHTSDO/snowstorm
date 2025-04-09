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
    jq \
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
### LOINC ###
#############
WORKDIR $LOINC_HOME
# Download latest version of the tool that will assist performing the LOINC import + puppeteer for downloading the LOINC file
RUN curl -fsSL $(curl -s https://api.github.com/repos/hapifhir/hapi-fhir/releases/latest | jq -r '.assets[] | select(.name | endswith("cli.zip")).browser_download_url') -o hapi-fhir-cli.zip && \
    unzip hapi-fhir-cli.zip && \
    rm hapi-fhir-cli.zip && \
    npm i puppeteer
# Copy puppeteer script to image
COPY download_loinc.mjs $LOINC_HOME/download_loinc.mjs
# For local testing of loinc imports
#COPY Loinc_*-sample*.zip $LOINC_HOME/

#############
#### HL7 ####
#############
WORKDIR $HL7_HOME
# (Optional) For local testing of hl7 imports
#COPY hl7.terminology.*-sample*.tgz $HL7_HOME/

##############
### SNOMED ###
##############
WORKDIR $SNOMED_HOME
# For local testing of snomed imports
#COPY snomed-edition*-sample*.zip $SNOMED_HOME/
#COPY snomed-extension*-sample*.zip $SNOMED_HOME/

##############
### Common ###
##############
WORKDIR $APP_HOME

# Create a non-root user, add ownership to app files and switch to it
RUN useradd -m -d $APP_HOME -s /bin/bash appuser
RUN chown -R appuser:appuser $APP_HOME

# Expose application port
EXPOSE 8080

# Copy Snowstorm JAR (you need to have built it first with Maven beforehand) + the entrypoint
COPY target/snowstorm*.jar ./snowstorm.jar

USER appuser

# Run the app
ENTRYPOINT ["java", "-Xms2g", "-Xmx4g", "--add-opens", "java.base/java.lang=ALL-UNNAMED", "--add-opens", "java.base/java.util=ALL-UNNAMED", "-jar", "/app/snowstorm.jar"]

# Using arguments that are likely to be customized
CMD ["--elasticsearch.urls=http://es:9200","--import-snomed-terminology=http://snomed.info/sct/11000172109/version/20250315", "--extension-country-code=BE", "--import-loinc-terminology", "--import-hl7-terminology"]