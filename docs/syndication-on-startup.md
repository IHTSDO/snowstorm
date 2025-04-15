# Startup Syndication Documentation

This document provides a complete overview of how terminology loading works during the startup of a Snowstorm-based application. It explains how to configure imports, what services are involved, and how to optimize and monitor the process.

---

## Terminology Loading on (runtime) Startup

The application supports automatic loading of the following healthcare terminologies:

- **SNOMED CT**
- **LOINC**
- **HL7 Terminology**

These terminologies can be loaded in three different ways:

### Import Options

- **Load the latest version**: The app checks whether the latest version is already imported. If not, it downloads and imports it.
- **Load a specific version**: The app checks for the presence of a specific version. If not already imported, it will download and import it.
- **Load a local version**: If you already have the terminology file locally, the app can import it from the local filesystem. 
Please then make sure that the file is present in the docker container and that the filename respects the format specified in the application.properties file (see syndication.hl7.fileNamePattern, syndication.loinc.fileNamePattern, ...).

These options must be passed as command-line arguments to the application (see Docker Compose section).

### Design Philosophy

- **Minimal Docker Image**: No terminology files are bundled in the image, keeping it lightweight.
- **Runtime Fetching**: Terminologies are downloaded on startup from their official sources.
- **Licensing and Access**: Some terminologies (e.g., SNOMED CT and LOINC) require licensed access and credentials.

---

## Terminology Loading on Runtime

This API endpoint allows for dynamic loading of clinical terminologies at runtime, without requiring a restart of the application. It is intended for system administrators that need to update or import a terminology into the application environment.

### Endpoint
```
PUT /syndication/import
```

### Description

This endpoint initiates a background job to import or update a terminology version. It can be used to keep terminologies like SNOMED CT, LOINC, or HL7 FHIR CodeSystems up to date in environments where updates are required (e.g. monthly LOINC refreshes or SNOMED CT extension releases).
Downgrades to lower versions are also possible using this endpoint.

### Supported Terminologies

- **SNOMED CT**
- **LOINC**
- **HL7 FHIR**

### Request Body

The request must be in JSON format and conform to the `SyndicationImportRequest` structure. A few examples are listed below.
Each time, a secret needs to be passed. The value must match with the one defined in the environment variables (see Environment File section below). 
This basic security mechanism prevents unwanted non-admin type users from updating terminologies.

Loinc latest version
```json
{
  "terminologyName": "loinc",
  "syndicationSecret": "theSecret"
}
```

Loinc version 2.80
```json
{
  "terminologyName": "loinc",
  "version": "2.80",
  "syndicationSecret": "theSecret"
}
```

Snomed belgian extension + international edition latest version
```json
{
  "terminologyName": "snomed",
  "version": "http://snomed.info/sct/11000172109",
  "extensionName": "BE",
  "syndicationSecret": "theSecret"
}
```

Snomed belgian extension + international edition version 20250315
```json
{
  "terminologyName": "snomed",
  "version": "http://snomed.info/sct/11000172109/version/20250315",
  "extensionName": "BE",
  "syndicationSecret": "theSecret"
}
```

Hl7 latest version
```json
{
  "terminologyName": "hl7",
  "syndicationSecret": "theSecret"
}
```

---

## Terminology Sources and Import Behavior

### HL7 Terminology

- ✅ No credentials required
- ⏱️ Import Time: ~3–4 minutes
- 🌐 Source: [simplifier.net](https://simplifier.net/packages/hl7.terminology)

### LOINC Terminology

- 🔐 Requires login credentials (env vars)
- ⏱️ Import Time: ~6–8 minutes
- 🌐 Latest: [loinc.org/downloads](https://loinc.org/downloads/)
- 📚 Archive: [loinc.org/downloads/archive](https://loinc.org/downloads/archive/)
- 📦 Downloaded via Puppeteer script (`download_loinc.mjs`)
- 📜 License: You accept LOINC's terms and conditions whilst importing this terminology source using Snowstorm

### SNOMED CT Terminology

- 🔐 Requires login credentials (env vars)
- 📦 Supports international editions and optional country-specific extensions
- ⏱️ Import Time: ~30 minutes (Belgian + International edition)
- 🌐 Source: [MLDS](https://mlds.ihtsdotools.org/#/viewReleases)
- 🔗 Edition URI format: [SNOMED URI Examples](https://confluence.ihtsdotools.org/display/DOCEXTPG/4.4.2+Edition+URI+Examples)
- 📜 License: You accept SNOMED's terms and conditions whilst importing this terminology source using Snowstorm
---

## Docker Compose Architecture

### Services

#### Elasticsearch
- 📦 Image: `docker.elastic.co/elasticsearch/elasticsearch:8.11.1`
- 🛠️ Configured as a single-node cluster
- 🔒 Security: Disabled
- 💾 Memory: 4 GB
- 🔌 Port: `9200`

#### Snowstorm
- 🛠️ Built from local Dockerfile
- ⚙️ Loads terminologies on startup based on CLI args
- 📞 App Port: `8080`, Debug Port: `5005`
- ⛓️ Depends on healthy Elasticsearch instance

#### SNOMED CT Browser
- 📦 Image: `snomedinternational/snomedct-browser:latest`
- 🔌 Port: `80`
- 🔗 Connects to Snowstorm API

### Networks and Volumes

```yaml
networks:
  elastic:

volumes:
  elastic:
```

---

## Dockerfile Highlights

### Base Image
```Dockerfile
FROM openjdk:17-jdk-buster
```

### Key Environment Variables
```Dockerfile
ENV APP_HOME=/app
ENV SNOMED_HOME=$APP_HOME/snomed
ENV LOINC_HOME=$APP_HOME/loinc
ENV HL7_HOME=$APP_HOME/hl7
ENV PUPPETEER_CACHE_DIR=$APP_HOME/.cache/puppeteer
```

### Installed Tools and Dependencies

- `Node.js`, `Puppeteer` for automated LOINC downloads
- System libraries required for headless browser execution
- Common tools: `jq`, `curl`, `unzip`, etc.

### LOINC Setup

- Downloads the latest [HAPI FHIR CLI](https://github.com/hapifhir/hapi-fhir)
- Installs Puppeteer and adds the `download_loinc.mjs` script

### SNOMED/HL7 Setup

- Creates directories for local testing (optional)
- Copies files if available locally

### Application Setup

- Copies built `snowstorm.jar`
- Creates non-root `appuser`
- Entrypoint launches the Java app

---

## Sample Docker Compose Arguments

This example imports:
- the latest HL7 terminology,
- a local LOINC file,
- a specific SNOMED CT edition (Belgian + Intl)

```yaml
command: [
  "--elasticsearch.urls=http://es:9200",
  "--import-hl7-terminology",
  #"--import-hl7-terminology=local",
  #"--import-hl7-terminology=6.1.0",
  #"--import-loinc-terminology",
  "--import-loinc-terminology=local",
  #"--import-loinc-terminology=2.78",
  #"--import-snomed-terminology=http://snomed.info/sct/11000172109", # latest belgian extension (+ international edition dependency)
  #"--import-snomed-terminology=local",
  "--import-snomed-terminology=http://snomed.info/sct/11000172109/version/20250315",
  "--extension-country-code=BE", # mandatory when loading snomed extensions
]
```

You can comment/uncomment lines to select the desired loading mode.

> 💡 Tip: You can omit all import arguments to skip terminology import entirely on startup.

---

## Environment File (`.env`)

Create a `.env` file to securely pass credentials required for SNOMED and LOINC downloads. The SYNDICATION_SECRET environment variable will be used as a basic security mechanism to prevent unwanted users from using the PUT /syndication/import endpoint.

```env
SNOMED_USERNAME=username@mail.com
SNOMED_PASSWORD=snomedPassword
LOINC_USERNAME=username
LOINC_PASSWORD=loincPassword
SYNDICATION_SECRET=secret
```

If you're not using `docker-compose` and its 'env_file' configuration, ensure these are provided via another secure mechanism (e.g. environment injection, secrets manager).

---

## Notes & Best Practices

- ✅ The application avoids re-importing already-loaded versions.
- 🔐 Avoid committing `.env` files or credentials into version control.
- 📉 Imports are only triggered if no previous successful import is found for the requested version.
- 🔎 Use the `GET /syndication/status` endpoint to monitor the progress or troubleshoot issues.
- 💡 In the future, the dockerfile will be published. The docker-compose file in this project is just an example and can be used as a reference.
---

## Resources

- Snowstorm: https://github.com/IHTSDO/snowstorm
- SNOMED URI Examples: https://confluence.ihtsdotools.org/display/DOCEXTPG/4.4.2+Edition+URI+Examples
- LOINC Archive: https://loinc.org/downloads/archive/
- HL7 Terminology: https://simplifier.net/packages/hl7.terminology
- HAPI FHIR CLI: https://github.com/hapifhir/hapi-fhir

For further assistance, consult the official documentation of each terminology provider.

