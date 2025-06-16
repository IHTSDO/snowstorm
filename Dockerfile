# Dockerfile responsible of creating the snowstorm image. 
# The container is dependent on elasticsearch to be up and running (e.g. on port 9200).

# Use a Ubuntu-based image (includes apt)
FROM ubuntu:22.04

# Install Nodejs and libraries necessary for puppeteer to work + utilities
RUN apt-get update && apt-get install -y curl && \
    curl -sL https://deb.nodesource.com/setup_18.x -o /tmp/nodesource_setup.sh && \
    bash /tmp/nodesource_setup.sh \
    && apt-get update && apt-get install -y \
    openjdk-17-jdk \
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
    wget \
    && rm -rf /var/lib/apt/lists/*

# Set up environment variables
ENV APP_HOME=/app
ENV HAPI_FHIR=$APP_HOME/hapi
ENV ICD10=$APP_HOME/icd10
ENV SNOMED_HOME=$APP_HOME/snomed
ENV LOINC_HOME=$APP_HOME/loinc
ENV HL7_HOME=$APP_HOME/hl7
ENV UCUM_HOME=$APP_HOME/ucum
ENV ATC_HOME=$APP_HOME/atc
ENV BCP13_HOME=$APP_HOME/bcp13
ENV BCP47_HOME=$APP_HOME/bcp47
ENV ISO3166_HOME=$APP_HOME/iso3166
ENV PUPPETEER_CACHE_DIR=$APP_HOME/.cache/puppeteer

#############
# HAPI_FHIR #
#############
WORKDIR $HAPI_FHIR
RUN curl -fsSL $(curl -s https://api.github.com/repos/hapifhir/hapi-fhir/releases/latest | jq -r '.assets[] | select(.name | endswith("cli.zip")).browser_download_url') -o hapi-fhir-cli.zip && \
    unzip hapi-fhir-cli.zip && \
    rm hapi-fhir-cli.zip

#############
### ICD10 ###
#############
WORKDIR $ICD10
RUN mkdir -p ./terminologyFiles

#############
### LOINC ###
#############
WORKDIR $LOINC_HOME
RUN mkdir -p ./terminologyFiles && npm i puppeteer
# Copy puppeteer script to image
COPY download_loinc.mjs $LOINC_HOME/download_loinc.mjs
# For local testing of loinc imports
#COPY Loinc_*-sample*.zip $LOINC_HOME/terminologyFiles

#############
#### HL7 ####
#############
WORKDIR $HL7_HOME
RUN mkdir -p ./terminologyFiles
# (Optional) For local testing of hl7 imports
#COPY hl7.terminology.*-sample*.tgz $HL7_HOME/terminologyFiles

##############
### SNOMED ###
##############
WORKDIR $SNOMED_HOME
RUN mkdir -p ./terminologyFiles
# For local testing of snomed imports
#COPY snomed-edition*-sample*.zip $SNOMED_HOME/terminologyFiles
#COPY snomed-extension*-sample*.zip $SNOMED_HOME/terminologyFiles

##############
#### UCUM ####
##############
WORKDIR $UCUM_HOME
RUN ZIPBALL_URL=$(curl -s https://api.github.com/repos/ucum-org/ucum/releases/latest | jq -r '.zipball_url') && \
        curl -fsSL "$ZIPBALL_URL" -o ucum-source.zip && \
        unzip ucum-source.zip && \
        find . -name ucum-essence.xml -exec mv {} "$UCUM_HOME/ucum-codesystem.xml" \; && \
        echo "ucum-essence.xml from UCUM (source zipball), © Regenstrief Institute. See https://unitsofmeasure.org for license." > /UCUM_LICENSE.txt && \
        rm -rf ucum-source.zip ucum-org-ucum-*

##############
### ATC ######
##############
WORKDIR $ATC_HOME

##############
### BCP13 ####
##############
WORKDIR $BCP13_HOME
RUN curl -fsSL https://www.iana.org/assignments/media-types/application.csv -o bcp13-application-codesystem.csv && \
    curl -fsSL https://www.iana.org/assignments/media-types/audio.csv -o bcp13-audio-codesystem.csv && \
    curl -fsSL https://www.iana.org/assignments/media-types/font.csv -o bcp13-font-codesystem.csv && \
    curl -fsSL https://www.iana.org/assignments/media-types/haptics.csv -o bcp13-haptics-codesystem.csv && \
    curl -fsSL https://www.iana.org/assignments/media-types/image.csv -o bcp13-image-codesystem.csv && \
    curl -fsSL https://www.iana.org/assignments/media-types/message.csv -o bcp13-message-codesystem.csv && \
    curl -fsSL https://www.iana.org/assignments/media-types/model.csv -o bcp13-model-codesystem.csv && \
    curl -fsSL https://www.iana.org/assignments/media-types/multipart.csv -o bcp13-multipart-codesystem.csv && \
    curl -fsSL https://www.iana.org/assignments/media-types/text.csv -o bcp13-text-codesystem.csv && \
    curl -fsSL https://www.iana.org/assignments/media-types/video.csv -o bcp13-video-codesystem.csv

##############
### BCP47 ####
##############
WORKDIR $BCP47_HOME
RUN npm --registry https://packages.simplifier.net install dk.ehealth.sundhed.fhir.ig.core@3.2.0 && \
    find . -name CodeSystem-urn-ietf-bcp-47.json -exec mv {} "$BCP47_HOME/bcp47-codesystem.json" \; && \
    rm -rf node_modules package*.json

##############
### ISO3166 ##
##############
WORKDIR $ISO3166_HOME
RUN npm --registry https://packages.simplifier.net install fhir.tx.support.r4@0.19.0 && \
    find . -name CodeSystem-iso3166.json -exec mv {} "$ISO3166_HOME/iso3166-codesystem.json" \; && \
    find . -name CodeSystem-iso3166-2.json -exec mv {} "$ISO3166_HOME/iso3166-2-codesystem.json" \; && \
    rm -rf node_modules package*.json

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

#USER appuser

# Run the app
ENTRYPOINT ["java", "-Xms2g", "-Xmx4g", "--add-opens", "java.base/java.lang=ALL-UNNAMED", "--add-opens", "java.base/java.util=ALL-UNNAMED", "-jar", "/app/snowstorm.jar"]

# Using arguments that are likely to be customized
CMD ["--elasticsearch.urls=http://es:9200","--snomed=http://snomed.info/sct/11000172109/version/20250315", "--extension-country-code=BE", "--loinc", "--hl7"]