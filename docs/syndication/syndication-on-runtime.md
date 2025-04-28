# 🩺 Runtime Syndication API Documentation

This document outlines how to dynamically change or update clinical terminology versions in a Snowstorm-based application **after the application has fully started** and **completed the startup import process**.

---

## 📌 Endpoint

This API endpoint enables **runtime loading of clinical terminologies**—no application restart required. It’s designed for **system administrators** who need to **import or update** terminology versions on-the-fly.

```http
PUT /syndication/import
```

---

## 📝 Description

Invoking this endpoint triggers a **background job** that imports or updates a terminology version. It supports:

- **Custom-version terminologies** (e.g., SNOMED CT, LOINC, HL7 FHIR)
- **Fixed-version terminologies** (e.g., ISO codes, UCUM)

> ⚠️ **Security Notice**: A secret key (`syndicationSecret`) must be provided in the request body. This secret must match 
> the value defined in your environment variables (see syndication-terminologies.md) to prevent unauthorized access.

---

## 📦 Request Body

The request must be in JSON format and follow the structure defined in `SyndicationImportRequest.java`.

---

## 🧪 Custom-Version Terminologies

This option is ideal for terminology sources that release regular updates (e.g., monthly LOINC updates or SNOMED CT regional extensions). Downgrading to earlier versions is also supported.

### ▶️ Examples

#### LOINC - Latest Version
```json
{
  "terminologyName": "loinc",
  "syndicationSecret": "theSecret"
}
```

#### LOINC - Specific Version (2.80)
```json
{
  "terminologyName": "loinc",
  "version": "2.80",
  "syndicationSecret": "theSecret"
}
```

#### SNOMED CT - Belgian Extension + Latest International Edition
```json
{
  "terminologyName": "snomed",
  "version": "http://snomed.info/sct/11000172109",
  "extensionName": "BE",
  "syndicationSecret": "theSecret"
}
```

#### SNOMED CT - Belgian Extension + Specific Version
```json
{
  "terminologyName": "snomed",
  "version": "http://snomed.info/sct/11000172109/version/20250315",
  "extensionName": "BE",
  "syndicationSecret": "theSecret"
}
```

#### HL7 - Latest Version
```json
{
  "terminologyName": "hl7",
  "syndicationSecret": "theSecret"
}
```

---

## 📁 Fixed-Version Terminologies

This option re-imports terminology files **already present on the Docker container's file system**. It's useful when:

- You want to re-trigger an import without restarting the app.
- You’ve manually copied a new file version to the container during runtime or want to import the original one.

> 📌 File naming convention must follow: `*-codesystem.*`  
> Example: `bcp13-codesystem.json`, `iso3166-codesystem.json`

### 📂 Example File Paths

- `/app/atc/atc-codesystem.csv`
- `/app/bcp13/bcp13-application-codesystem.json`
- `/app/bcp13/bcp13-image-codesystem.json`
- `/app/bcp47/bcp47-codesystem.json`
- `/app/iso3166/iso3166-codesystem.json`
- `/app/iso3166/iso3166-2-codesystem.json`
- `/ucum/ucum-codesystem.xml`

### 🏷️ Supported Terminology Names

| Terminology       | Value in Request |
|-------------------|------------------|
| BCP13             | `bcp13`          |
| BCP47             | `bcp47`          |
| ATC               | `atc`            |
| ISO3166           | `iso3166`        |
| M49               | `m49`            |
| UCUM              | `ucum`           |

### ▶️ Example: Import BCP13
```json
{
  "terminologyName": "bcp13",
  "syndicationSecret": "theSecret"
}
```
