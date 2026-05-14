# FHIR `tx-resource` Parameter Handling

## Background

### What is `tx-resource`

`tx-resource` is defined in the **[FHIR Terminology Ecosystem IG](https://build.fhir.org/ig/HL7/fhir-tx-ecosystem-ig)**,
not in the core FHIR R4/R4B/R5 specification. It does not appear in the standard `$expand` or
`$validate-code` operation definitions on hl7.org. The IG requires that servers accept `ValueSet` and
`CodeSystem` resources passed via `tx-resource` and use them when resolving imports in other `ValueSet`
definitions.

`tx-resource` lets a client pass a terminology resource inline in the request body, rather than
requiring the server to have it pre-loaded. Resources are scoped to the single operation — the
`Parameters` resource is **"purely a request/response envelope mechanism"** and carries no expectation
of server-side persistence.

### Intended use case

A client authoring tool may repeatedly call `$expand` while iterating on a draft `CodeSystem` or
`ValueSet`. Rather than `PUT`-ing intermediate versions to the server (which is slow and pollutes
permanent storage), the client sends the current draft inline as a `tx-resource`. Only when the
author is satisfied do they persist the resource via a proper `POST`/`PUT`. This pattern is a
first-class design intent of the IG.

---

## When to Store vs. When to Keep Transient

The HTTP interaction type determines the intent, not the resource type.

### Transient — functional operations

When a resource arrives inside the `Parameters` body of a terminology operation, the client is saying:
_"Use this definition for this calculation only. Do not commit it to your permanent library."_

| HTTP request | Example | Server behaviour |
|---|---|---|
| `POST [base]/ValueSet/$expand` | Expand with an inline draft `ValueSet` | Use and discard |
| `POST [base]/CodeSystem/$validate-code` | Validate against an inline `CodeSystem` | Use and discard |

**Reasons clients keep resources transient:**

- **Drafting and testing** — A terminologist iterates through many versions of a `ValueSet` before
  publishing. Persisting every draft pollutes the server for all users.
- **Data sovereignty** — The client may hold a proprietary or sensitive `CodeSystem` they are licensed
  to use for local validation but are not permitted to store on a shared server.
- **Performance** — Skipping a database write makes the round-trip for `$expand` or `$validate-code`
  measurably faster in high-frequency authoring workflows.

### Persistent — lifecycle interactions

A resource should be stored only when the client explicitly targets the resource endpoint with a
lifecycle verb — a deliberate act to publish the resource for future use by all callers.

| HTTP request | Example | Server behaviour |
|---|---|---|
| `POST [base]/ValueSet` | Create a new `ValueSet` | Store permanently |
| `PUT [base]/ValueSet/[id]` | Update an existing `ValueSet` | Store permanently |
| `POST [base]/CodeSystem` | Create a new `CodeSystem` | Store permanently |

### Summary

| Client intent | HTTP pattern | `tx-resource` involved | Persist? |
| --- | --- | --- | --- |
| Test a draft resource | `POST $expand` / `$validate-code` | Yes | No |
| Use a sensitive resource once | `POST $expand` / `$validate-code` | Yes | No |
| Publish a finalised resource | `POST`/`PUT` to resource endpoint | No | Yes |

---

## Previous Behaviour (before fix)

`FHIRHelper.handleTxResources()` packed the inline resources into a temporary npm `.tgz` and called
`FHIRLoadPackageService.uploadPackageResources()`, which wrote them **permanently** to Elasticsearch
via `FHIRCodeSystemService.createUpdate()` and `FHIRConceptService.saveAllConceptsOfCodeSystemVersion()`.

### Why that was wrong

1. **Spec violation** — inline resources are request-scoped; the client has no expectation they will
   appear on the server afterwards.
2. **Database pollution** — every `$expand` iteration during authoring created a permanent record.
3. **Race condition** — before re-importing, the old version was deleted (`deleteCodeSystemVersion`).
   Two concurrent requests at the same URL could silently overwrite each other's data.
4. **No cleanup** — resources accumulated indefinitely; there was no post-request removal.
5. **Read-only inconsistency** — in read-only mode the server refused to upload and checked for
   pre-existing resources instead, making the two deployment modes behave differently.

---

## Implementation: Request-Scoped In-Memory Overlay

tx-resources are treated as a **read-only, short-lived overlay** on top of Elasticsearch. The overlay
is populated at the provider layer, consulted during resolution, and discarded when the request ends.
No database writes occur at any point.

### New classes

| Class | Role |
|---|---|
| `TxResourceContext` | `ThreadLocal` holder for the per-request overlay map — `set()`, `lookup()`, `clear()` |
| `TxResourceOverlay` | Domain utilities for working with inline resources — `getVersionsByUrl()`, `findConcept()`, `toVersion()` |
| `TxResourceAware` | Mixin interface implemented by services that need overlay access |

### Flow

1. **Extract** — `FHIRHelper.extractTxResources()` reads `CodeSystem` and `ValueSet` resources from
   the `Parameters` body and builds an unmodifiable map keyed by `url|version` (or plain `url` when
   no version is present), allowing multiple versions of the same URL to coexist.

2. **Store** — Providers (`FHIRValueSetProvider`, `FHIRCodeSystemProvider`) call `TxResourceContext.set()`
   before invoking the service, and `TxResourceContext.clear()` in a `finally` block. This scopes the
   overlay exactly to the request and prevents leakage across thread-pool reuse.

3. **Resolve** — Services check the overlay before hitting Elasticsearch. `TxResourceContext.lookup(url, version)`
   tries the versioned key first, then falls back to the plain URL key. Overlay checks are placed in
   `FHIRCodeSystemService.findCodeSystemVersion()` (non-SNOMED path only) and
   `FHIRValueSetFinderService.findOrInferValueSet()`.

4. **Concept lookup** — `TxResourceOverlay.findConcept()` resolves a code against an inline `CodeSystem`
   via the `@Transient inlineCodeSystem` field on `FHIRCodeSystemVersion`, with no Elasticsearch access.

### What was removed

| Removed | Replacement |
|---|---|
| `FHIRHelper.handleTxResources()` | `FHIRHelper.extractTxResources()` + `TxResourceContext.set()` |
| `FHIRHelper.verifyTxResourcesExist()` | Not needed — overlay works in read-only mode without DB access |
| `FHIRHelper.validateAndGetUrl()` | Not needed |
| `FHIRLoadPackageService.uploadPackageResources()` call for tx-resources | Not needed |
| `loadPackageService` injection in `FHIRValueSetProvider` and `FHIRCodeSystemProvider` | Removed |
