# Loading & updating SNOMED CT with local Extensions or Editions

## Contents

- [Extensions vs Editions](#extensions-vs-editions)
- [Loading the initial data](#loading-the-initial-data)
- [Loading another Extension](#loading-another-extension)
- [Importing a new International Edition](#importing-a-new-international-edition)
- [Upgrading an Extension to the new International Edition](#upgrading-an-extension-to-the-new-international-edition)
- [Upgrading to a new local Edition or Extension](#upgrading-to-a-new-local-edition-or-extension)

## Editions vs Extensions

Technically speaking the difference between an edition and an extension is just a question of what the RF2 release package contains.  

- An edition release package contains both the content of the International edition and the content of a country extension.
In an edition package only one set of RF2 files are expected for each component type, for example one concept file containing both the International concepts and the extension concepts. 

- An extension release package contains just the extension content and does not include the International edition content.

_However please be aware that these terms are not always used consistently!_

A few known exceptions to the rule worth being aware of:

- **Spanish Edition**: referred to as an _Edition_ when in fact the package is an _Extension_ and should be loaded as documented below.
- **UK Edition**: packaged as a single zip file, but actually contains full International Edition folder and a UK Extension folder. 
Snowstorm is not able to process this directory structure so it's best to create a separate zip file containing just the UK Extension folder and load as documented below.

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
  "dependantVersion": 20210131
}
```
The dependantVersion is the version of the Edition which the extension being imported is dependant on. For example an extension with an effective date of 20210430 might be dependant on the International Edition 20210131.
This field is used when creating the extension branch so that the new branch can see content from the desired release in the parent branch. This dependantVersion will be changed when upgrading the extension. 

There are many optional fields available for that request that can be used to provide additional information about the code system. These are used by the [SNOMED Browser project](https://github.com/IHTSDO/sct-browser-frontend).

To run the command click 'Try it now'.

The Spanish Extension can now be imported. Start the import process by creating a new import job. Look for the Import endpoint ( http://localhost:8080/swagger-ui.html#!/Import/createImportJobUsingPOST ) and then create a new import using:

```json
{
  "branchPath": "MAIN/SNOMEDCT-ES",
  "createCodeSystemVersion": true,
  "type": "SNAPSHOT"
}
```

Click on 'Try it now' and note the ID of the import as you will need it for the next step (look for a UUID like `d0b30d96-3714-443e-99a5-2f282b1f1b0` in the Location response header). 
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

and then click on 'Try it now' and then note the id of the import as before. We now need to upload the July 2021 International release file as before:

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

and then click on 'Try it now' and then note the id of the import as before. You now need to upload the October 2021 Spanish release file as before -

```bash
curl -X POST --header 'Content-Type: multipart/form-data' --header 'Accept: application/json' -F file=@SnomedCT_SpanishRelease-es_Production_20211031T120000Z.zip  'http://localhost:8080/imports/<import id>/archive'
```

You can tail the system log to see how this is progressing, or simply to the import endpoint - http://localhost:8080/imports/<import id>

And that's it... rinse and repeat for the next time...
