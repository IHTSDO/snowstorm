# 🐳 Syndication Using Docker

This guide explains the Docker and docker-compose parts of the **Snowstorm terminology server**.

---

## 📂 Overview

The Docker setup supports both:

- **Automated terminology downloads** (via scripts and APIs)
- **Local file-based imports** (for manual or internal use)

It is optimized for **runtime loading** and **modular configuration**, giving you full control over how and when terminologies are imported.

---

## 🧱 Dockerfile Highlights

### ✅ Base Image

```Dockerfile
FROM openjdk:17-jdk-buster
```

### 🌐 Installed Tools & Dependencies

- **Node.js + Puppeteer** for headless LOINC downloads
- Essential CLI tools: `curl`, `jq`, `unzip`, `xdg-utils`, etc.
- Required system libraries for Chromium-based Puppeteer to run in Docker

---

### 🌍 Environment Variables

```Dockerfile
ENV APP_HOME=/app
ENV SNOMED_HOME=$APP_HOME/snomed
ENV LOINC_HOME=$APP_HOME/loinc
ENV HL7_HOME=$APP_HOME/hl7
ENV UCUM_HOME=$APP_HOME/ucum
ENV ATC_HOME=$APP_HOME/atc
ENV BCP13_HOME=$APP_HOME/bcp13
ENV BCP47_HOME=$APP_HOME/bcp47
ENV ISO3166_HOME=$APP_HOME/iso3166
ENV PUPPETEER_CACHE_DIR=$APP_HOME/.cache/puppeteer
```

---

## 📥 Docker Terminology Setup (Per Type)

### 📘 LOINC

- Downloads **HAPI FHIR CLI**
- Installs Puppeteer
- Copies the `download_loinc.mjs` script
- Supports automated or local imports

### 📗 HL7

- Prepares local directory for HL7 `.tgz` packages (optional)

### 📕 SNOMED CT

- Prepared for either extension or full edition imports
- Supports direct URLs or local `.zip` files

### 📙 UCUM

- Fetches the latest release from GitHub
- Extracts and renames `ucum-essence.xml` to `ucum-codesystem.xml`

### 📒 ATC

- Copies `ATC_DDD_Index.csv` into the container renamed to `atc-codesystem.csv`

### 📓 BCP13

- Downloads all media type categories from IANA
- Each type is saved with the proper `*-codesystem.csv` name

### 📔 BCP47 & ISO3166

- Uses `npm` to pull terminology IGs from Simplifier
- Extracts and renames CodeSystems to proper paths

---

## 🚀 Runtime Application Setup

- Runs as a **non-root user** (`appuser`)
- JAR is copied to `/app`
- Port `8080` is exposed
- Entrypoint runs Snowstorm with default memory settings

```Dockerfile
ENTRYPOINT ["java", "-Xms2g", "-Xmx4g", "--add-opens", "java.base/java.lang=ALL-UNNAMED", "--add-opens", "java.base/java.util=ALL-UNNAMED", "-jar", "/app/snowstorm.jar"]
```

---


## ⚙️ Docker Compose Architecture
The provided `docker-compose.yml` is intended as a usage example.

### Networks and Volumes

```yaml
networks:
  elastic:

volumes:
  elastic:
```

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


