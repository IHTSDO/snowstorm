# Updating SNOMED CT and working with other Extensions or Editions

## Loading the initial data

Now, you will need to load a SNAPSHOT of the International Edition (this could be another edition including the US, CA or NL editions). Instructions on how to do that are in the Snowstorm readme - https://github.com/IHTSDO/snowstorm/blob/master/docs/loading-snomed.md.

**NOTE** - Make sure that you wait for the original import to complete before going any further forward. You can see it is completed by looking at the status of the import - http://localhost:8080/imports/<import id> - where it will say **COMPLETED**


## Loading another Extension

As this is the first time we will be importing anothe extension, we will need to create a CodeSystem on the server. For this example, we are going to use the Spanish Edition but it should be the same for any other edition or extension. On the swagger interface, look for the create a code system POST on the admin endpoint. Use the following in the request to create the branch

```json
{
  "branchPath": "MAIN/SNOMEDCT-ES",
  "shortName": "SNOMEDCT-ES"
}
```

and then click on 'Try it now'. You now need to import the Spanish extension. You need to start the import process by creating a new import job. Look for the Import endpoint and then create a new import using

```json
{
  "branchPath": "MAIN/SNOMEDCT-ES",
  "createCodeSystemVersion": true,
  "type": "SNAPSHOT"
}
```

and then click on 'Try it now' and then note the id of the import as you will need it for the next step (it will look something like - d0b30d96-3714-443e-99a5-2f282b1f1b0). As before, you now need to upload the file. And again, we will use curl to do this:

```bash
curl -X POST --header 'Content-Type: multipart/form-data' --header 'Accept: application/json' -F file=@SnomedCT_SpanishRelease-es_Production_20180430T120000Z.zip 'http://localhost:8080/imports/<import id>/archive'
```

You can watch log to see how this is progressing, or simply to the import endpoint - http://localhost:8080/imports/<import id>. This will take around 5-6 minutes.

You can check the import has been a success using the Branching endpoint - http://localhost:8080/branches, where you should now see a MAIN/SNOMEDCT-ES and a MAIN/SNOMEDCT-ES/2018-04-30 branch.

## Upgrading MAIN to a new International Edition

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
curl -X POST --header 'Content-Type: multipart/form-data' --header 'Accept: application/json' -F file=@SnomedCT_InternationalRF2_PRODUCTION_20180731T120000Z.zip  'http://localhost:8080/imports/<import id>/archive'
```

You can tail the system log to see how this is progressing, or simply to the import endpoint - http://localhost:8080/imports/<import id>

## Upgrading the Edition branch to the new International Edition (merging the branch) and new Edition version

You should only really do this once the relevant edition update is also available. We first need to merge the Spanish branch and so we will now merge the MAIN branch into the SNOMEDCT-SE branch using the merge endpoint

```json
{
  "commitComment": "Merging in the 2018-07-31 edition",
  "source": "MAIN",
  "target": "MAIN/SNOMEDCT-ES"
}
```

You can check this has been successful by checking the status of the branch and seeing if it is forward.

Then the edition upgrade is an import again, but for this time, it is a DELTA import onto the relevant branch. First, we need to create an import job as before:

```json
{
  "branchPath": "MAIN/SNOMEDCT-ES",
  "createCodeSystemVersion": true,
  "type": "DELTA"
}
```

and then click on 'Try it now' and then note the id of the import as before. You now need to upload the October 2018 Spanish release file as before -

```bash
curl -X POST --header 'Content-Type: multipart/form-data' --header 'Accept: application/json' -F file=@SnomedCT_SpanishRelease-es_Production_20181031T120000Z.zip  'http://localhost:8080/imports/<import id>/archive'
```

You can tail the system log to see how this is progressing, or simply to the import endpoint - http://localhost:8080/imports/<import id>

And that's it... rinse and repeat for the next time...