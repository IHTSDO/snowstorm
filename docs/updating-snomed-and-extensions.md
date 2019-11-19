# Loading & updating SNOMED CT with local Extensions or Editions

## Contents

- [Extensions vs Editions](#extensions-vs-editions)
- [Loading the initial data](#loading-the-initial-data)
- [Loading another Extension](#loading-another-extension)
- [Importing a new International Edition](#importing-a-new-international-edition)
- [Upgrading an Extension to the new International Edition](#upgrading-an-extension-to-the-new-international-edition)
- [Upgrading to a new local Edition or Extension](#upgrading-to-a-new-local-edition-or-extension)

## Extensions vs Editions

To try to defuse any confusion about what SNOMED CT versions you are working, this is a brief summary of the difference between an edition and an extension. Editions generally bundle international and local counry content into a single release. An extension is local content extended on top of an already published International Edition.

A few exceptions to the rule worth being aware of:

- **Spanish Edition**: referred to as en edition when in fact it is an *extension* and should be loaded as documented below
- **UK Edition**: packaged as a single zip file, but actually contains full International Edition folder and a UK Extension folder. The latter should be loaded as documented below.

In the case of real editions (the US, Canada and Australia editions are included in the list), there is no need to load the International Edition, and you should load the relevat adition in the same way as the International Edition.

## Loading the initial data

Before doing anything, you will need to have a SNAPSHOT (or Full version) of the International Edition loaded into Snowstorm (this could be another edition including the US, CA or NL editions). Instructions on how to do that are in the [Snowstorm documents here](loading-snomed.md). Make sure to initially load the correct version that the extension you want to load is dependent upon.

**NOTE** - Make sure that you wait for the original import to complete before going any further forward. You can see it is completed by looking at the status of the import - http://localhost:8080/imports/<import id> - where it will say **COMPLETED**

## Loading another Extension

As this is the first time we will be importing another extension, we will need to create a CodeSystem on the server. For this example, we are going to use the Spanish Edition but it should be the same for any other edition or extension. 


On the swagger interface, look for the create a code system POST on the code systems endpoint ( http://localhost:8080/swagger-ui.html#!/Code_Systems/createCodeSystemUsingPOST ). Use the following in the request to create the branch:

```json
{
  "branchPath": "MAIN/SNOMEDCT-ES",
  "shortName": "SNOMEDCT-ES"
}
```

and then click on 'Try it now'.

You now need to import the Spanish extension. You need to start the import process by creating a new import job. Look for the Import endpoint ( http://localhost:8080/swagger-ui.html#!/Import/createImportJobUsingPOST ) and then create a new import using:

```json
{
  "branchPath": "MAIN/SNOMEDCT-ES",
  "createCodeSystemVersion": true,
  "type": "SNAPSHOT"
}
```

and then click on 'Try it now' and then note the id of the import as you will need it for the next step (it will look something like - d0b30d96-3714-443e-99a5-2f282b1f1b0). As before, you now need to upload the file. This can be done through Swagger using the /imports/{importId}/archive endpoint, or via curl.  In both cases, specify the ID recovered in the previous step:

```bash
curl -X POST --header 'Content-Type: multipart/form-data' --header 'Accept: application/json' -F file=@SnomedCT_SpanishRelease-es_Production_20190430T120000Z.zip 'http://localhost:8080/imports/<import id>/archive'
```

You can watch log to see how this is progressing, or simply to the import endpoint - http://localhost:8080/imports/<import id>. This will take around 5-6 minutes.

You can check the import has been a success using the Branching endpoint - http://localhost:8080/branches, where you should now see a MAIN/SNOMEDCT-ES and a MAIN/SNOMEDCT-ES/2019-04-30 branch.

## Importing a new International Edition

Every 6 months there is a new International Edition and it is important to keep your terminology server up to date. An upgrade is an import again, but for the International Edition, it is a DELTA import onto MAIN. First, we need to create an import job as above

```json
{
  "branchPath": "MAIN",
  "createCodeSystemVersion": true,
  "type": "DELTA"
}
```

and then click on 'Try it now' and then note the id of the import as before. We now need to upload the July 2018 International release file as before:

```bash
curl -X POST --header 'Content-Type: multipart/form-data' --header 'Accept: application/json' -F file=@SnomedCT_InternationalRF2_PRODUCTION_20190731T120000Z.zip  'http://localhost:8080/imports/<import id>/archive'
```

You can tail the system log to see how this is progressing, or simply to the import endpoint - http://localhost:8080/imports/<import id>

## Upgrading an Extension/Edition to the new International Edition

We now need to upgrade the local extension/edition branch. 

In our example, we will now merge the MAIN branch into the SNOMEDCT-ES branch using the CodeSystem upgrade endpoint using the shortname, SNOMEDCT-ES, `/codesystems/SNOMEDCT-ES/upgrade`:
```json
{
  "newDependantVersion": 20190731
}
```

You can check this has been successful by checking the status of the branch and seeing if it is forward.

## Upgrading to a new local Edition or Extension

The Edition or Extension upgrade is an import again, but for this time, it is a DELTA import onto the relevant branch. First, we need to create an import job as before:

```json
{
  "branchPath": "MAIN/SNOMEDCT-ES",
  "createCodeSystemVersion": true,
  "type": "DELTA"
}
```

and then click on 'Try it now' and then note the id of the import as before. You now need to upload the October 2018 Spanish release file as before -

```bash
curl -X POST --header 'Content-Type: multipart/form-data' --header 'Accept: application/json' -F file=@SnomedCT_SpanishRelease-es_Production_20191031T120000Z.zip  'http://localhost:8080/imports/<import id>/archive'
```

You can tail the system log to see how this is progressing, or simply to the import endpoint - http://localhost:8080/imports/<import id>

And that's it... rinse and repeat for the next time...
