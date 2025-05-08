# Syndication Terminologies

This document describes the different terminologies that can be loaded using the Snowstorm syndication mechanism.
Snowstorm offers the possibility to import a specific terminology version for certain terminologies. For others, a default version is imported.  
We can call the first category **custom-version terminologies** and the latter **fixed-version terminologies**.

## Custom-version Terminologies

Custom-version terminologies are **not stored** inside the Docker image, keeping it lightweight.  
They are **downloaded during application startup**.

### Loading modes
These terminologies can be loaded in three different ways:

- **Load the latest version**: The app checks whether the latest version is already imported. If not, it downloads and imports it.
- **Load a specific version**: The app checks for the presence of a specific version. If not already imported, it downloads and imports it.
- **Load a local version**: If you already have the terminology file locally, the app can import it from the local filesystem.  
  ➔ Ensure the file is present in the Docker container and that the filename matches the format specified in the `application.properties` file (`syndication.hl7.fileNamePattern`, `syndication.loinc.fileNamePattern`, etc.).

### SNOMED CT

- 🔐 Requires login credentials (environment variables)
- 📦 Supports international editions and optional country-specific extensions
- ⏱️ Import Time: ~30 minutes (Belgian + International edition)
- 🌐 Source: [MLDS](https://mlds.ihtsdotools.org/#/viewReleases)
- 🔗 Edition URI Format: [SNOMED URI Examples](https://confluence.ihtsdotools.org/display/DOCEXTPG/4.4.2+Edition+URI+Examples)
- 📜 License: Use of SNOMED CT requires agreement to SNOMED’s licensing terms.

### LOINC

- 🔐 Requires login credentials (environment variables)
- ⏱️ Import Time: ~6–8 minutes
- 🌐 Latest: [loinc.org/downloads](https://loinc.org/downloads/)
- 📚 Archive: [loinc.org/downloads/archive](https://loinc.org/downloads/archive/)
- 📦 Downloaded via Puppeteer script (`download_loinc.mjs`)
- 📜 License: Use of LOINC is subject to the Regenstrief Institute's terms and conditions.

### HL7 terminology

- ⏱️ Import Time: ~3–4 minutes
- 🌐 Source: [Simplifier.net HL7 Terminology](https://simplifier.net/packages/hl7.terminology)
- 📜 License: Creative commons (free to use and modify, see [License](https://terminology.hl7.org/license.html))

---

## Fixed-version Terminologies and codesystems
The below codesystems are all already stored on the docker image. 
It ensures maximum stability, since they don't need to be fetched during the app runtime.
They are automatically imported during the application startup when the syndication flag is used (see syndication-with-docker.md):

### ATC

- 🌐 Source: File `ATC_DDD_Index.csv` obtained via webscraper from [atcddd.fhi.no](https://atcddd.fhi.no/atc_ddd_index/).
- 🔗 Webscraper Repo: [Webscrap Health Monitoring](https://github.com/sarrabenyahia/webscrap_health_monitoring)
- 📜 License: Usage must comply with the Norwegian Medicines Agency’s terms (FHI).

### BCP13

- 🌐 Source: [IANA Media Types Registry](https://www.iana.org/assignments/media-types/media-types.xhtml)
- 📜 License: Public domain (no license restrictions).

### BCP47

- 🌐 Source: [Simplifier.net Language Codes Package](https://simplifier.net/packages)
- 📜 License: Based on [IETF BCP47](https://tools.ietf.org/html/bcp47), public use permitted.

### ISO3166 and ISO3166-2

- 🌐 Source: [Simplifier.net Country Codes Package](https://simplifier.net/packages)
- 📜 License: Data adapted from the ISO 3166 standard, subject to ISO copyright.

### UCUM

- 🌐 Source: [UCUM GitHub Repository](https://github.com/ucum-org/ucum)
- 💡 The codesystem file actually represents a grammar, that can generate an infinite amount of valid codes.  
- 📜 License: Freely available under the UCUM Terms (open access for non-commercial use).

## Environment Variables
If loading SNOMED and LOINC, make sure their credentials are accessible by Snowstorm as environment variables.
You could for example create an `.env` file such as the one below.
The `SYNDICATION_SECRET` environment variable acts as a basic protection mechanism for the `PUT /syndication/import` endpoint (see syndication-on-runtime.md).

```env
SNOMED_USERNAME=username@mail.com
SNOMED_PASSWORD=snomedPassword
LOINC_USERNAME=username
LOINC_PASSWORD=loincPassword
SYNDICATION_SECRET=secret
```

If you are **not** using `docker-compose` with the `env_file` configuration, ensure that these variables are provided through a secure alternative (e.g., Kubernetes secrets, AWS Secrets Manager).
