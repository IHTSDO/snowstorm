# Loading SNOMED CT into Snowstorm

Before anything, get hold of the most recent [SNOMED CT International Edition RF2 release files](http://snomed.org/get-snomed) release files.

## Contents

- [Loading Release Snapshot](#loading-release-snapshot)
  * [Via REST](#via-rest)
  * [Via Command line](#via-command-line)
- [Generated Delta File Warning](#generated-delta-file-warning)
- [Loading Release Full Files](#loading-release-full-files)
- [Stopping After Loading](#stopping-after-loading)

## Loading Release Snapshot

This loads the content of the current release and skips loading outdated content. This is the recommended option for development due to the speed of loading. There are two ways to do this:

### Via REST

Once Snowstorm is running, you will need to start the import process by creating a new import job. Look for the Imports endpoint on Swagger, (http://localhost:8080) and then create a new import using

```json
{
  "branchPath": "MAIN",
  "createCodeSystemVersion": true,
  "type": "SNAPSHOT"
}
```

and then click on 'Execute' and then note the id of the import as you will need it for the next step (it will look something like - _d0b30d96-3714-443e-99a5-2f282b1f1b0_).

You now need to upload the SNOMED release zip file. You can do this through the swagger interface at the *imports/archive* end point, but the following will allow you to run it using curl to do this from the command line (*this example uses the January 2025 release*):

```bash
curl -X POST --header 'Content-Type: multipart/form-data' --header 'Accept: application/json' -F file=@SnomedCT_InternationalRF2_PRODUCTION_20250101T120000Z.zip 'http://localhost:8080/imports/<import id>/archive'
```

You can watch the log to see how this is progressing, or simply to the import endpoint - http://\<ip address>:8080/imports/\<import id> . This can take between 30-60 minutes depending on the performance of your machine.

### Via Command line

To delete any existing Snowstorm Elasticsearch indices and load the RF2 **Snapshot** start Snowstorm with the following arguments:

`java -Xms2g -Xmx4g -jar target/snowstorm*.jar --delete-indices --import=<Absolute-path-of-SNOMED-CT-RF2-zip>`

This will take between 30-60 minutes depending on the performance of your machine.

## Generated Delta File Warning
**-latest-state flag must be used when generating delta files!**  
Since Janurary 2022 the International Edition no longer contains RF2 delta files. There is a [tool to generate delta files](https://github.com/IHTSDO/delta-generator-tool) but we recommend using snapshot files with Snowstorm. If using the delta tool then the `-latest-state` flag **must** be used in the delta-generator-tool to prevent multiple states of components existing at the same time within Snowstorm. 

We recommend importing releases into Snowstorm using the SNAPSHOT import type. Importing using Snapshot now has about the same performance as using a Delta, and has the same outcome.
 
## Loading Release Full (History) Files

It's possible to load the RF2 **Full** files which gives you access to all previous releases of SNOMED CT in addition to the current content. However, this will  take longer (*last run took 2h15 on an m5.xlarge AWS instance with 4 vCPU and 16GB Memory*), but will not have an impact to the performance. It is very unlikely you will need to import the Full files. We recommend using the Snapshot files, they include all the active and inactive content of SNOMED CT.

To import the full type on the command line replace the `--import` argument above with `--import-full`, or if using the API use `type` = `FULL`.

## Stopping After Loading

To shutdown Snowstorm after loading data automatically include the `--exit` flag.

## Loading a Subontology Snapshot
A subontology of SNOMED CT can be generated using the [SNOMED Subontology Extraction project](https://github.com/IHTSDO/snomed-subontology-extraction). This can create an RF2
snapshot archive to be loaded into a terminology server.

Subontology packages are like edition packages in that they contain (some of) the International Edition. There are two alternative options for loading these into
Snowstorm.

### Option A - Host a subontology and no other SNOMED CT content
This approach is suited to those wanting to try SNOMED CT via the IPS ontology. No SNOMED CT licence is needed.

Steps:
- Simply start with an empty Snowstorm instance and load the snapshot onto the MAIN branch.

### Option B - Host a subontology alongside other content
This approach is suited to those who already have a Snowstorm instance with content loaded.
A subontolgy may be added for a number of reasons including hosting a browser for a clinical reference or research group.

Steps:
- Apply the admin technical-fix `CREATE_EMPTY_2000_VERSION` to create a blank version of the root code system, with effective time 20000101.
- Create a new code system for the subontology using 20000101 as the `dependantVersionEffectiveTime`.
    - For example: code system short name `SNOMEDCT-IPS`, branch path `MAIN/SNOMEDCT-IPS`.
- Load the snapshot into the branch of the new code system.
- No content will be inherited from the root code system because the subontology code system is dependant on a version of the root code system that has no content.
