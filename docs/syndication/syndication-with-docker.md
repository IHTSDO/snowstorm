# 🐳 Syndication Using Docker

This guide explains how to use Docker to build and run the **Snowstorm terminology server**, including dynamic syndication of clinical terminologies like SNOMED CT, LOINC, HL7, and others.

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

## 📥 Terminology Setup (Per Type)

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

## ⚙️ Sample `docker-compose` Command Config
The `--syndicate` option instructs Snowstorm to import the version-specific terminologies (Loinc, Snomed-CT, Hl7) included in the command, as well as the custom-version terminologies (atc, bcp47, Ucum, ...)
This sample shows how to control what terminologies to load on container startup:

```yaml
command: [
  "--elasticsearch.urls=http://es:9200",
  "--syndicate",
  "--hl7",
  #"--hl7=local",
  #"--hl7=6.1.0",
  #"--loinc",
  "--loinc=local",
  #"--loinc=2.78",
  #"--snomed=http://snomed.info/sct/11000172109",
  #"--snomed=local",
  "--snomed=http://snomed.info/sct/11000172109/version/20250315",
  "--extension-country-code=BE"
]
```

> 💡 **Tip**: Comment/uncomment lines to enable specific terminology imports or versions.  
> Omit all import arguments (not the elasticsearch url) to skip terminology import entirely.

---

## ✅ Final Notes

- Ensure **Elasticsearch is up and reachable** at the configured URL before startup.
- You can modify the `CMD` in the Dockerfile or pass a custom `command` via `docker-compose`.
- Built to support **dynamic terminology environments**, especially for fast-moving clinical ecosystems.

