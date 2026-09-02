# Loading & updating SNOMED CT with local Extensions or Editions

## Contents

- [Editions vs Extensions](#editions-vs-extensions)
- [Loading an Edition](#loading-an-edition)
- [Loading Extensions](#loading-extensions)
- [Importing a new International Edition](#importing-a-new-international-edition)
- [Upgrading an Extension/Edition to the new International Edition](#upgrading-an-extensionedition-to-the-new-international-edition)
- [Upgrading to a new local Edition or Extension](#upgrading-to-a-new-local-edition-or-extension)
- [UK Edition loading tips](#uk-edition-loading-tips)
- [Rolling back changes](#rolling-back-changes)


## Editions vs Extensions

Technically speaking the difference between an edition and an extension is just a question of what the RF2 release package contains.  

- An edition release package contains both the content of the International edition and the content of a country extension.
In an edition package only one set of RF2 files are expected for each component type, for example one concept file containing both the International concepts and the extension concepts. 

- An extension release package contains just the extension content and does not include the International edition content.

_However please be aware that these terms are not always used consistently!_

A few known exceptions to the rule worth being aware of:

- **Spanish Edition**: referred to as an _Edition_ when in fact the package is an _Extension_ and should be loaded as documented below.
- **UK Edition**: use the TRUD **SNOMED CT UK Monolith Edition, RF2: Snapshot** (`uk_sct2mo_*.zip`). Older `SnomedCT_UKEditionRF_*` packages put the International Edition and the UK Extension in one zip; Snowstorm cannot load that layout. See [UK Edition loading tips](#uk-edition-loading-tips).

In the case of real editions (for example the US, Canada and Australia editions) loading the International Edition is not required and you could load the edition directly into MAIN the same way as the International Edition.

## Loading an Edition

An edition must be loaded before loading any extensions. As a minimum the SNAPSHOT of the International Edition should be loaded. You may also choose to run the import in 
FULL mode which will import all the versions of SNOMED CT in the RF2 archive you have and make them available on separate release branches. 
There are other true "editions" whose RF2 package contains the whole of the International Edition (see above). 
See [loading SNOMED](loading-snomed.md).

**NOTE** - Make sure that you wait for the original import to complete before going any further forward. 
You can see it is completed by looking at the status of the import (`http://localhost:8080/imports/<import id>`) where it will say **COMPLETED** when done.

## Loading Extensions

Once an edition has been loaded an extension can be loaded on top. If you haven't loaded an edition yet please go back a step!

For each extension added a CodeSystem must be created on the server. 

For this example, we are going to use the Spanish Edition.
On the swagger interface, look for the create a code system POST on the code systems endpoint ( http://localhost:8080/swagger-ui.html#!/Code_Systems/createCodeSystemUsingPOST ). 
Use the following in the request to create the branch:

```json
{
  "shortName": "SNOMEDCT-ES",
  "branchPath": "MAIN/SNOMEDCT-ES",
  "dependantVersionEffectiveTime": 20210131
}
```
The dependantVersion is the version of the Edition which the extension being imported is dependant on. For example an extension with an effective date of 20210430 might be dependant on the International Edition 20210131.
This field is used when creating the extension branch so that the new branch can see content from the desired release in the parent branch. This dependantVersion will be changed when upgrading the extension. 

There are many optional fields available for that request that can be used to provide additional information about the code system. These are used by the [SNOMED Browser project](https://github.com/IHTSDO/sct-browser-frontend).

To run the command click 'Execute'.

The Spanish Extension can now be imported. Start the import process by creating a new import job. Look for the Import endpoint ( http://localhost:8080/swagger-ui.html#!/Import/createImportJobUsingPOST ) and then create a new import using:

```json
{
  "branchPath": "MAIN/SNOMEDCT-ES",
  "createCodeSystemVersion": true,
  "type": "SNAPSHOT"
}
```

Click on 'Execute' and note the ID of the import as you will need it for the next step (look for a UUID like `d0b30d96-3714-443e-99a5-2f282b1f1b0` in the Location response header). 
As before, the RF2 file needs to be uploaded next. This can be done through Swagger using the /imports/{importId}/archive endpoint, or via curl. In both cases, specify the ID recovered in the previous step:

```bash
curl -X POST --header 'Content-Type: multipart/form-data' --header 'Accept: application/json' -F file=@SnomedCT_SpanishRelease-es_Production_20210430T120000Z.zip 'http://localhost:8080/imports/<import id>/archive'
```

You can watch log to see how this is progressing, or simply to the import endpoint - http://localhost:8080/imports/<import id>. This will take around 5-6 minutes.

You can check the import has been a success using the Branching endpoint - http://localhost:8080/branches, where you should now see a MAIN/SNOMEDCT-ES and a MAIN/SNOMEDCT-ES/2021-04-30 branch.

## Importing a new International Edition

Since Janurary 2022 a new SNOMED-CT International Edition release is published every month. **Please do not import delta archives created with the Delta Generator Tool into Snowstorm because this will make the content inconsistent.** New releases should be imported onto the `MAIN` branch using the `SNAPSHOT` import type. First, we need to create an import job as above

```json
{
  "branchPath": "MAIN",
  "createCodeSystemVersion": true,
  "type": "SNAPSHOT"
}
```

and then click on 'Execute' and then note the id of the import as before. We now need to upload the July 2021 International release file as before:

```bash
curl -X POST --header 'Content-Type: multipart/form-data' --header 'Accept: application/json' -F file=@SnomedCT_InternationalRF2_PRODUCTION_20210731T120000Z.zip  'http://localhost:8080/imports/<import id>/archive'
```

You can tail the system log to see how this is progressing, or simply to the import endpoint - http://localhost:8080/imports/<import id>

## Upgrading an Extension/Edition to the new International Edition

Once a new version of the International Edition is imported local extensions/editions can be upgraded. 

In our example, we will now merge the MAIN branch into the SNOMEDCT-ES branch using the CodeSystem upgrade endpoint using the shortname, SNOMEDCT-ES, `/codesystems/SNOMEDCT-ES/upgrade`:
```json
{
  "newDependantVersion": 20210731
}
```

You can check this has been successful by checking the status of the branch and seeing if it is forward.

## Upgrading to a new local Edition or Extension

The edition or extension upgrade is an import again. The SNAPSHOT import type can always be used for upgrades onto the relevant code system branch. If the extension archive contains delta RF2 files then the DELTA import type could also be used for a slightly faster import. First, we need to create an import job as before:

```json
{
  "branchPath": "MAIN/SNOMEDCT-ES",
  "createCodeSystemVersion": true,
  "type": "SNAPSHOT"
}
```

and then click on 'Execute' and then note the id of the import as before. You now need to upload the October 2021 Spanish release file as before -

```bash
curl -X POST --header 'Content-Type: multipart/form-data' --header 'Accept: application/json' -F file=@SnomedCT_SpanishRelease-es_Production_20211031T120000Z.zip  'http://localhost:8080/imports/<import id>/archive'
```

You can tail the system log to see how this is progressing, or simply to the import endpoint - http://localhost:8080/imports/<import id>

And that's it... rinse and repeat for the next time...


## UK Edition loading tips

The UK Edition of SNOMED CT needs some extra handling. It is larger than most editions, and some packages put multiple concept files in one zip. It loads well in Snowstorm if you follow these steps.

### Use the Monolith Snapshot package

This is labelled in TRUD as **SNOMED CT UK Monolith Edition, RF2: Snapshot**.

The download filename has the form `uk_sct2mo_39.4.0_20250115000001Z.zip`.

Other distribution formats, for example `SnomedCT_UKEditionRF_PRODUCTION20250115.zip`, will **not** load correctly. Those older packages contain a full International Edition folder and a UK Extension folder; Snowstorm cannot process that directory structure. If you only have that layout, create a separate zip of the UK Extension folder and load it as an extension as documented above.

### Increase the max-terms count

The default `elasticsearch.index.max.terms.count` is `500000`. Recent UK monolith packages need more than `1000000` (that value fails as of March 2026; `1500000` succeeds).

Add this to the Java startup command, or put it in a config file. See the [Configuration Guide](configuration-guide.md).

```
--elasticsearch.index.max.terms.count=1500000
```

### Assign enough memory when loading

The monolith loads in Docker with about 12g overall: Elasticsearch 6g and Snowstorm 4g. Recommended changes against the repo `docker-compose.yml`:

```yaml
services:
  elasticsearch:
    environment:
      - "ES_JAVA_OPTS=-Xms6g -Xmx6g"
    mem_reservation: 6g

  snowstorm:
    entrypoint: [
      "java",
      "-Xms4g",
      "-Xmx4g",
      "--add-opens", "java.base/java.lang=ALL-UNNAMED",
      "--add-opens", "java.base/java.util=ALL-UNNAMED",
      "-jar", "/app/snowstorm.jar"
    ]
    command: [
      "--elasticsearch.urls=http://es:9200",
      "--elasticsearch.index.max.terms.count=1500000"
    ]
    mem_reservation: 4g
```

Set Docker's overall memory limit to 12g or above.

## Rolling back changes
Sometimes content changes are imported into a Snowstorm instance for preview, before they are part of a proper release. 
If changes of this type have been imported onto the codesystem branch they should be removed before importing the next release archive.

If the daily-build function has been enabled this must be disabled before starting the rollback. Daily-build can be enabled again after importing the latest release.    

Follow these steps to rollback a codesystem branch to the point of the latest release:
- Get the codesystem to discover the latest **release branch**
  - Use `GET /codesystems/{shortName}`. For example `GET /codesystems/SNOMEDCT-SE`
  - In the response the latest **release branch** can be found under `latestVersion > branchPath`. For example `MAIN/SNOMEDCT-SE/2023-11-30`.
- Get the latest **release branch** to check the branch state
  - Use `GET /branches/{branch}`. For example `GET /branches/MAIN/SNOMEDCT-SE/2023-11-30`.
  - In the response the branch `state` is shown.
- The state of the **release branch** is dependant on the content of the **codesystem branch**.
- If the **release branch** state is `UP_TO_DATE` that means the **codesystem branch** does not contain any changes since the latest release import and there is no need to rollback.
- If the **release branch** state is `BEHIND` that means the **codesystem branch** contains changes since the latest release import that should be rolled back before importing a new release.

- Rollback one commit on the **codesystem branch**:
  - Get the **codesystem branch** using `GET /branches/{branch}`. For example `GET /branches/MAIN/SNOMEDCT-SE`.
    - Make a note of the `headTimestamp`. For example `1701321893134`.
  - Use the admin rollback function to revert the head commit of the **codesystem branch**
    - Use `POST /admin/{branch}/actions/rollback-commit`, supplying the **codesystem branch** and the head timestamp.
  - Get the **release branch**. If the status is `BEHIND` that means the **codesystem branch** needs rolling back further. Follow these steps again to rollback another commit. Repeat as many times as needed.
 
Once the **release branch** state is `UP_TO_DATE` the codesystem is ready for upgrade. You probably need to import a new International Edition release and upgrade the extension before importing a new extension release. Steps for these are listed above.
