# 🩺 Runtime Syndication API Guide

This guide explains how to **dynamically import or update clinical terminology versions** in a Snowstorm-based application **after it has fully started** and completed its initial import process.
Please note that these terminology updates won't prevent the terminologies (the one being updated included) from being used.
There are two options available:

1. **Automated updates** using a CRON job.
2. **Manual updates** via the REST API endpoint.

---

## 📌 Automated Updates with a CRON Job

The CRON job is **disabled by default**. To enable it, set the `SYNDICATION_CRON` environment variable.
You can configure it the same way as SNOMED or LOINC credentials — for example, by defining it in your `.env` file.

**Example configuration:**

```properties
SYNDICATION_CRON=0 0 0 * * *
```

This will trigger the syndication process **once every day at midnight**.

✅ **Advantages:**

* Automatically keeps your terminology data up to date.
* No need to restart the Snowstorm container.

⚠️ **Important notes:**

* Updates only work if the terminology is configured with `latest`.

   * Example: `--hl7=latest` or `--hl7` ✅
   * Example: `--hl7=6.1.0` ❌ (specific version, will not update)
* **Fixed-version terminologies** (except for **ATC** and **ICPC-2**) will not be updated automatically. These terminologies are embedded in the Docker image and are not subject to change.

---

## 📌 Manual Updates with the API

If you need immediate control, you can trigger a terminology import at runtime via the following endpoint:

```http
PUT /syndication/import
```

This method is useful for **system administrators** who want to:

* Force updates on demand.
* Retry a failed import.
* Test updates in controlled scenarios.

### 📝 Overview

Calling this endpoint triggers a **background job** that imports or updates a clinical terminology. It supports:

* **Versioned terminologies** (e.g. SNOMED CT, LOINC, HL7 FHIR)
* **Fixed-code terminologies** (e.g. ISO standards, UCUM)

> ⚠️ **Security Requirement**: A `syndicationSecret` must be included in the request body. This secret must match the value configured in the `SYNDICATION_SECRET` environment variable to prevent unauthorized access to non-admin users. 
> The `SYNDICATION_SECRET` environment variable acts as a basic protection mechanism for the `PUT /syndication/import` endpoint

---

### 📦 Request Format

The request must be JSON-formatted and conform to the structure defined in `SyndicationImportRequest.java`.

---

### 🔄 Custom-Version Terminologies

Use this option for terminologies that are frequently updated (e.g., SNOMED CT extensions, LOINC). You can also **downgrade** to earlier versions or load **local** files.
If the specified version is detected to already have been imported, the import won't be triggered.

#### 📁 Local Imports

To use local terminology files:

1. Copy the file into the running container to the appropriate location. For example for HL7:

   ```bash
   docker cp ./hl7.terminology.r4-6.2.0.tgz snowstorm:/app/hl7
   ```

2. Use the version `"local"` in the request. The table below lists the appropriated paths and filename patterns to respect for each custom-version terminology.

| Terminology | Path           | Filename Pattern                                |
|-------------|----------------|-------------------------------------------------|
| HL7         | `/app/hl7`     | `hl7.terminology.*.tgz`                         |
| LOINC       | `/app/loinc`   | `Loinc*.zip`                                    |
| SNOMED CT   | `/app/snomed`  | `snomed*edition*.zip` + `snomed*extension*.zip` |
| ICD-10      | `/app/icd10`   | `*.zip`                                         |
| ICD-10-BE   | `/app/icd10be` | `*.xlsx`                                        |

---

#### ▶️ Examples

##### ✅ LOINC – Latest Version

```json
{
  "terminologyName": "loinc",
  "syndicationSecret": "theSecret"
}
```

##### 📌 LOINC – Specific Version (2.80)

```json
{
  "terminologyName": "loinc",
  "version": "2.80",
  "syndicationSecret": "theSecret"
}
```

##### 🗂️ LOINC – Local File

```json
{
  "terminologyName": "loinc",
  "version": "local",
  "syndicationSecret": "theSecret"
}
```

##### 🌍 SNOMED CT – Latest BE Extension + International Edition

```json
{
  "terminologyName": "snomed",
  "version": "http://snomed.info/sct/11000172109",
  "extensionName": "BE",
  "syndicationSecret": "theSecret"
}
```

##### 📌 SNOMED CT – Specific Version

```json
{
  "terminologyName": "snomed",
  "version": "http://snomed.info/sct/11000172109/version/20250315",
  "extensionName": "BE",
  "syndicationSecret": "theSecret"
}
```

##### 🗂️ SNOMED CT – Local Import

```json
{
  "terminologyName": "snomed",
  "version": "local",
  "extensionName": "BE",
  "syndicationSecret": "theSecret"
}
```

##### ✅ HL7 – Latest Version

```json
{
  "terminologyName": "hl7",
  "syndicationSecret": "theSecret"
}
```

##### 🗂️ HL7 – Local File

```json
{
  "terminologyName": "hl7",
  "version": "local",
  "syndicationSecret": "theSecret"
}
```

---

### 📁 Fixed-Version Terminologies

For terminologies with fixed versions (e.g. ISO, UCUM), this mode (re-)imports the terminology **from the container’s filesystem**.
In case of ATC and ICPC-2, this will fetch the latest version via a URL and then do the import.
Note that for these terminologies, an import will be triggered in any case, even if the corresponding terminology file hasn't changed.

#### ✅ Use Cases

* Re-import a Fixed-Version terminology without restarting the server
* Load a manually updated file during the container runtime and launch the import.

> 📌 **File Naming Requirement**: Filenames must follow the `*-codesystem.*` pattern (e.g. `bcp13-codesystem.json`)
> 
>️️ ⚠️ **ICPC2 terminology**: Since the file is not present on the docker image for licensing reasons, you must ensure the file has been copied to `/app/icpc2` before triggering the reimport
> 
#### 📂 File Path Examples

| Terminology | Path           | Example Filename                              |
|------------|----------------|-----------------------------------------------|
| ATC        | `/app/atc`     | `atc-codesystem.csv`                          |
| BCP13      | `/app/bcp13`   | `bcp13-application-codesystem.json`           |
| BCP47      | `/app/bcp47`   | `bcp47-codesystem.json`                       |
| ISO3166    | `/app/iso3166` | `iso3166-codesystem.json`                     |
| ISO3166-2  | `/app/iso3166` | `iso3166-2-codesystem.json`                   |
| UCUM       | `/app/ucum`    | `ucum-codesystem.xml`                         |
| ICPC-2     | `/app/icpc2`   | `icpc2-codesystem.txt` |

#### 🏷️ Supported Values

| Terminology | Value in Request |
| ----------- | ---------------- |
| ATC         | `atc`            |
| BCP13       | `bcp13`          |
| BCP47       | `bcp47`          |
| ISO3166     | `iso3166`        |
| M49         | `m49`            |
| UCUM        | `ucum`           |
| ICPC-2      | `icpc2`          |

#### ▶️ Example: Re-import BCP13

```json
{
  "terminologyName": "bcp13",
  "syndicationSecret": "theSecret"
}
```
