# 🩺 Runtime Syndication API Guide

This guide explains how to **dynamically import or update clinical terminology versions** in a Snowstorm-based application **after it has fully started** and completed its initial import process.

---

## 📌 API Endpoint

The following endpoint allows **runtime terminology updates** without requiring a server restart. It is designed for **system administrators** who need to manage terminology data on-the-fly.

```http
PUT /syndication/import
```

---

## 📝 Overview

Calling this endpoint triggers a **background job** that imports or updates a clinical terminology. It supports:

* **Versioned terminologies** (e.g. SNOMED CT, LOINC, HL7 FHIR)
* **Fixed-code terminologies** (e.g. ISO standards, UCUM)

> ⚠️ **Security Requirement**: A `syndicationSecret` must be included in the request body. This secret must match the value configured in the application’s environment (see `syndication-terminologies.md`) to prevent unauthorized access.

---

## 📦 Request Format

The request must be JSON-formatted and conform to the structure defined in `SyndicationImportRequest.java`.

---

## 🔄 Custom-Version Terminologies

Use this option for terminologies that are frequently updated (e.g., SNOMED CT extensions, LOINC). You can also **downgrade** to earlier versions or load **local** files.

### 📁 Local Imports

To use local terminology files:

1. Copy the file into the running container to the appropriate location. For example for HL7:

   ```bash
   docker cp ./hl7.terminology.r4-6.2.0.tgz snowstorm:/app/hl7
   ```

2. Use the version `"local"` in the request. The table below lists the appropriated paths and filename patterns to respect for each custom-version terminology.

| Terminology | Path          | Filename Pattern                                |
| ----------- | ------------- | ----------------------------------------------- |
| HL7         | `/app/hl7`    | `hl7.terminology.*.tgz`                         |
| LOINC       | `/app/loinc`  | `Loinc*.zip`                                    |
| SNOMED CT   | `/app/snomed` | `snomed*edition*.zip` + `snomed*extension*.zip` |

---

### ▶️ Examples

#### ✅ LOINC – Latest Version

```json
{
  "terminologyName": "loinc",
  "syndicationSecret": "theSecret"
}
```

#### 📌 LOINC – Specific Version (2.80)

```json
{
  "terminologyName": "loinc",
  "version": "2.80",
  "syndicationSecret": "theSecret"
}
```

#### 🗂️ LOINC – Local File

```json
{
  "terminologyName": "loinc",
  "version": "local",
  "syndicationSecret": "theSecret"
}
```

#### 🌍 SNOMED CT – Latest BE Extension + International Edition

```json
{
  "terminologyName": "snomed",
  "version": "http://snomed.info/sct/11000172109",
  "extensionName": "BE",
  "syndicationSecret": "theSecret"
}
```

#### 📌 SNOMED CT – Specific Version

```json
{
  "terminologyName": "snomed",
  "version": "http://snomed.info/sct/11000172109/version/20250315",
  "extensionName": "BE",
  "syndicationSecret": "theSecret"
}
```

#### 🗂️ SNOMED CT – Local Import

```json
{
  "terminologyName": "snomed",
  "version": "local",
  "extensionName": "BE",
  "syndicationSecret": "theSecret"
}
```

#### ✅ HL7 – Latest Version

```json
{
  "terminologyName": "hl7",
  "syndicationSecret": "theSecret"
}
```

#### 🗂️ HL7 – Local File

```json
{
  "terminologyName": "hl7",
  "version": "local",
  "syndicationSecret": "theSecret"
}
```

---

## 📁 Fixed-Version Terminologies

For terminologies with fixed versions (e.g. ISO, UCUM), this mode re-imports the terminology **from the container’s filesystem**.

### ✅ Use Cases

* Re-import a terminology without restarting the server
* Load a manually updated file during runtime

> 📌 **File Naming Requirement**: Filenames must follow the `*-codesystem.*` pattern (e.g. `bcp13-codesystem.json`)

### 📂 File Path Examples

| Terminology | Path           | Example Filename                    |
| ----------- | -------------- | ----------------------------------- |
| ATC         | `/app/atc`     | `atc-codesystem.csv`                |
| BCP13       | `/app/bcp13`   | `bcp13-application-codesystem.json` |
| BCP47       | `/app/bcp47`   | `bcp47-codesystem.json`             |
| ISO3166     | `/app/iso3166` | `iso3166-codesystem.json`           |
| ISO3166-2   | `/app/iso3166` | `iso3166-2-codesystem.json`         |
| UCUM        | `/ucum`        | `ucum-codesystem.xml`               |

### 🏷️ Supported Values

| Terminology | Value in Request |
| ----------- | ---------------- |
| ATC         | `atc`            |
| BCP13       | `bcp13`          |
| BCP47       | `bcp47`          |
| ISO3166     | `iso3166`        |
| M49         | `m49`            |
| UCUM        | `ucum`           |

### ▶️ Example: Re-import BCP13

```json
{
  "terminologyName": "bcp13",
  "syndicationSecret": "theSecret"
}
```
