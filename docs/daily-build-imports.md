## Extension Authoring: Daily Build Imports
### Background
Snowstorm can automatically import work-in-progress content from another environment. This can be useful for hosting a publicly accessible daily-build browser.

- _Note: When the [SNOMED CT Browser](https://github.com/IHTSDO/sct-browser-frontend) is accessed using a DNS name that includes "dailybuild" it will switch to daily-build browser mode:_ 
  - _Only Code Systems with `dailyBuildAvailable=true` are listed._ 
  - _The Code System working-branch is used rather than a release branch._ 
  - _A "Daily Build" tab lists changes in the current authoring cycle._ 
  - _There is highlighting for unversioned components._
 
The daily-build import relies on RF2 Delta packages. The SNOMED Release Service can be configured produce these and export them to a shared location. The shared location could be a locally mounted folder or an AWS S3 bucket.

The RF2 delta files should not contain anything in the effectiveTime field. To import actual released/versioned content use the standard RF2 Import function instead. The daily-build process is for previewing content that has not been released yet.

### Configuration
The daily-build import requires application level and Code System level configuration.
- At application level set `daily-build.delta-import.enabled=true` and configured the shared location using the `daily-build.import.resources.xxx` properties that can be found in the default application.properties file.
- On each Code System that should have a daily-build delta imported set the `dailyBuildAvailable` to `true` using the update Code System endpoint.

### Behaviour
When the daily-build import feature is enabled Snowstorm will run the process every 5 minutes by default with the following behaviour:

- For each Code System in turn that has dailyBuildAvailable:
    - Check for a file that is newer than the latest commit on the Code System branch and not in the future
        - Using file mask `{shared-location}/{CodeSystemShortName}/{yyyy-MM-dd-HHmmss}.zip`
            - For example: `local-directory/SNOMEDCT-NO/2024-09-23-121036.zip`
    - If a new daily-build archive is found the old daily-build import is removed:
        - The daily-build import rollback process. Rollback the commits on the Code System branch (for example `MAIN/SNOMEDCT-NO`) until either:
            - the latest commit is a Code System upgrade commit
            - or matches the timestamp of the latest Code System version branch (for example `/MAIN/SNOMEDCT-NO/2024-09-15`)
        - The new Delta is imported

Daily build archives in the shared location can be future-dated to prevent them being imported immediately.

### Other Behaviour Changes
If a CodeSystem upgrade is requested when dailyBuildAvailable is set to true Snowstorm will perform the daily-build rollback process before upgrading the Code System.

### Daily Build API Endpoints
- Check if the latest imported daily build matches today's date
    - `GET /codesystems/{shortName}/daily-build/check`
- Manually trigger a daily-build import process rather than using the scheduled process
    - `POST /codesystems/{shortName}/daily-build/import`
- Run the daily-build import rollback process. Consider disabling the daily-build for that Code System first to prevent the content being imported again.
    - `POST /codesystems/{shortName}/daily-build/rollback`
