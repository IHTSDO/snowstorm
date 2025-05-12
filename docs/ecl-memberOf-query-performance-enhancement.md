
# Use Concepts Lookup to Improve ECL "Member Of" Query Performance in Snowstorm

## Overview

Starting in version `10.8.0`, Snowstorm leverages Elasticsearch [Terms Lookup](https://www.elastic.co/docs/reference/query-languages/query-dsl/query-dsl-terms-query#query-dsl-terms-lookup) to significantly improve the performance of ECL `"member of"` queries.
This optimization is especially effective for reference sets with large volumes of data. Instead of querying referenced concepts via the `member` index,
Snowstorm uses a dedicated `concepts-lookup` index to efficiently retrieve referenced concept ids.

---

## Performance Impact

Example:  
ECL member of query – `^447562003 | SNOMED CT to ICD-10 extended map reference set |`

| Metric               | Before       | After        | Improvement      |
|----------------------|--------------|--------------|------------------|
| Search response time | 31.67 sec    | 0.91 sec     | ~34.8× faster    |

---

## How It Works

- A new index called `concepts-lookup` is introduced.
- For eligible refset ids, referenced concept ids are precomputed and stored in this index.
- ECL `"member of"` queries use this index to retrieve matches efficiently.

---

## Enabling & Disabling Concepts Lookup

- Enabled by default from version `10.8.0`
- Concepts lookup is only generated for refsets that are self or descendants of:

  - `446609009 | Simple Type Refset|`
  - `609331003 | Extended Map from SNOMED CT Type Refset|`
  - `447250001 | Complex Map from SNOMED CT Type Refset|`

  - The list of refset ids can be customized in `application.properties`:
  ```properties
      ecl.concepts-lookup.refset.ids=446609009, 609331003, 447250001
  ```
  
- Configure generation threshold in `application.properties`:
  ```properties
  ecl.concepts-lookup.generation.threshold=1000
  ```

- To disable concepts lookup:
  ```properties
  ecl.concepts-lookup.enabled=false
  ```

---

## How to Build the Concepts Lookup Index

For existing projects/tasks:

1. Use the Admin API to rebuild
   ```
   POST /admin/{branch}/actions/rebuild-referenced-concepts-lookup
   ```
2. Rebase any related projects or tasks after rebuilding. 

For new projects/tasks:

Import published RF2 snapshot files will build concepts lookup automatically when enabled.

---

## Concepts Lookup Management
- Automatically rebuilt during:
  - Content saves
  - RF2 imports
  - Branch rebasing
- Lookup list is updated automatically during content promotion
- The full list of referenced concepts is stored only at the CodeSystem (top-level) branch
- Project/task branches store only delta (adds/removals)
- Unlike other components in snowstorm, concepts-lookup doesn't use existing version control mechanism (i.e version replaced in the branch metadata)
---

## Additional Notes

It's unlikely that you need to do this but when the lookup is not updated correctly you can remove it manually:

To remove concepts-lookup for a branch:
```
POST /admin/{branch}/actions/remove-referenced-concepts-lookup
```

