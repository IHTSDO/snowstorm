# Multiple Code System Dependencies (Configuration Guide)

This document explains how to configure **multiple code system dependencies** in Snowstorm (primarily for **authoring**), why you might need them, and how to validate compatibility before enabling them.

## What are “multiple code system dependencies” used for?

A code system (typically an extension) always depends on **SNOMEDCT (International)** at a specific *dependent version* (effective time). **Multiple code system dependencies** extends this model by allowing an extension to additionally depend on one or more other code systems (for example, LOINC) at specific releases.

Multiple code system dependencies **rely on the Module Dependency Reference Set (MDRS) being set up correctly** (for example: correct MDRS members, module IDs, and target effective times / version branches). If MDRS is missing or incorrectly configured, dependencies may not resolve as expected and related upgrade/versioning workflows can fail.

This capability is used when you need to:
- **Author/translate content that belongs to another dependent code system within the same authoring environment**
  - Example: enabling authoring/translating LOINC content in an extension by using a **holding module** so those changes are clearly separated from the extension’s core module.
- **Ensure upgrade/version alignment across multiple dependent code systems**
  - Snowstorm can compute which International versions are compatible across the extension + additional code system(s) before you commit to an upgrade.
- **Materialize the dependency relationship formally**
  - Dependencies are expressed using **Module Dependency Reference Set (MDRS)** members, and Snowstorm maintains derived metadata (`vc.additional-dependent-branches`) for operational visibility.

## Important warning (read before enabling)

**Use multiple code system dependencies with clear thought.**

Once you set up multiple dependencies for **authoring**, reverting is usually **extra work**:
- Removing MDRS/mapping metadata is only part of the change.
- You may need to re-home content, re-run validations, and review release processes.
- Treat this as a deliberate design decision and trial in non-production first.

## Walkthrough: Adding the LOINC Code System Dependency to an existing extension

Example scenario:
- Extension: `SNOMEDCT-XX`
- Additional dependency: `SNOMEDCT-LOINC`
- Goal: allow authoring/translating LOINC content inside the extension using a holding module.

### 1) Create a holding module (in the extension)

Create a **module concept** in the extension code system to link with LOINC content.
- Example name: “LOINC holding module”
- This module is created in the extension branch.

**API (create concept)**
- `POST /browser/{branch}/concepts`
  - `{branch}` should be the extension working branch (e.g. `MAIN/SNOMEDCT-XX`)

### 2) Create MDRS for the holding module (link it to the extension default module)

Create an MDRS member that links:
- **moduleId** = holding module id
- **referencedComponentId** = extension default module id

**API (create reference set member)**
- `POST /{branch}/members`

Example:

```json
{
  "active": true,
  "moduleId": "210221000315102",
  "refsetId": "900000000000534007",
  "referencedComponentId": "11000315107"
}
```

Notes:
- `refsetId = 900000000000534007` is the **Module Dependency Reference Set**.
- This step establishes the holding module’s relationship to the extension’s default module.
- Note: **AMS-53** was created to enable users to add MDRS via the authoring UI (when available).

### 3) Update branch metadata (Expected Extension Modules)

Add the holding module id into branch metadata so it is considered part of the extension’s modules.

**Branch metadata keys**
- `expectedExtensionModules` (include the holding module id)
- `defaultModuleId` (ensure correct extension default module remains set)

**API**
- `PUT /branches/{branch}/metadata-upsert`

### 4) Verify dependent International versions compatibility (before adding LOINC dependency)

Check whether the extension and LOINC code system are compatible at the extension’s current dependent International version.

**API: Get Compatible Dependent International Versions**
- `GET /codesystems/{shortName}/dependencies/compatible-versions`
- With the additional code system included:
  - `GET /codesystems/{shortName}/dependencies/compatible-versions?with=SNOMEDCT-LOINC`

Decision:
- If the extension’s dependent International version appears in `compatibleVersions`, **no upgrade is required**.
- Otherwise, upgrade the extension to one of the compatible International versions returned, then retry.

### 5) Add the additional LOINC code system dependency

**API: Add Additional Code System Dependency**
- `POST /codesystems/{shortName}/dependencies?holdingModule={holdingModuleId}&with=SNOMEDCT-LOINC`

This will:
- validate parameters,
- ensure there is no duplicate dependency,
- validate compatibility against the current dependent version,
- create the MDRS entries needed for the additional dependency.

### 6) View code system dependencies

**API: View code system dependencies**
- `GET /codesystems/{shortName}/dependencies`

This returns the dependency list including `SNOMEDCT` and additional dependencies such as `SNOMEDCT-LOINC` (with version information if available).

### 7) Create a new project for authoring/translating LOINC content (authoring practice)

For authoring/translating LOINC content inside the extension:
- Create a dedicated authoring project/workflow (per your governance).
- **Set the holding module as the default module ID** in that project context before translating/authoring LOINC content into the extension.

Note:
- You do **not** need a specific project just to **view** LOINC content; the project setup is primarily for controlled authoring/translating.

## Core dependency APIs (summary)

- **Get compatible dependent International versions (multiple dependencies):**
  - `GET /codesystems/{shortName}/dependencies/compatible-versions?with=CS1,CS2`
- **Add an additional code system dependency:**
  - `POST /codesystems/{shortName}/dependencies?holdingModule={moduleId}&with={dependencyShortName}`
- **View code system dependencies:**
  - `GET /codesystems/{shortName}/dependencies`
- **Create MDRS / other reference set members:**
  - `POST /{branch}/members`
- **Upsert branch metadata:**
  - `PUT /branches/{branch}/metadata-upsert`

