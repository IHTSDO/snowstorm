# Code Systems & Branches

In Snowstorm a code system is an Edition or Extension of the SNOMED CT terminology. A list of available code systems can be fetched using `GET /codesystems`.

Code systems have: 
- a Short Name
  - Examples: SNOMEDCT, SNOMEDCT-US, SNOMEDCT-DK
- a Working Branch
  - Examples: "MAIN", "MAIN/SNOMEDCT-US", "MAIN/SNOMEDCT-DK"

**N.B. Working branches should NOT be used to fetch versioned content.** 
Content in the working branch can not be relied upon; this branch often contains daily-build content that has not yet been published and is subject to change or deletion. 

Versioned content should be accessed using a code system version branch.

## Code System Versions

The latest version of each code system included in the code system listing under `latestVersion > branchPath`. Examples: "MAIN/2021-07-31", "MAIN/SNOMEDCT-NZ/2021-10-01".

The complete list of versions loaded for a code system can be fetched using `GET /codesystem/{shortName}/versions`.

### Note: Future Versions
If content is loaded into Snowstorm before the publication date, by default it will not be listed as the latest version of the code system until the effectiveDate matches the 
date on the 
server. This behaviour can be changed using the configuration flag `codesystem.all.latest-version.allow-future`.

Similarly the code system versions listing will not display future dated versions unless the `showFutureVersions=true` parameter is included in the request.
