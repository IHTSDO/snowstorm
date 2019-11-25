# Loading SNOMED CT into Snowstorm

Before anything, get hold of the most recent [SNOMED CT International Edition RF2 release files](https://www.snomed.org/snomed-ct/get-snomed-ct) release files.

## Contents

- [Loading Release Snapshot](#loading-release-snapshot)
  * [Via REST](#via-rest)
  * [Via Command line](#via-command-line)
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

and then click on 'Try it now' and then note the id of the import as you will need it for the next step (it will look something like - _d0b30d96-3714-443e-99a5-2f282b1f1b0_).

You now need to upload the file. You can do this through the swagger interface at the *imports/archive* end point, but the following will allow you to run it using curl to do this from the command line (*this example uses the January 2018 release file*):

```bash
curl -X POST --header 'Content-Type: multipart/form-data' --header 'Accept: application/json' -F file=@SnomedCT_InternationalRF2_PRODUCTION_20180131T120000Z.zip 'http://localhost:8080/imports/<import id>/archive'
```

You can watch the log to see how this is progressing, or simply to the import endpoint - http://<ip address>:8080/imports/<import id> . This can take between 20-60 minutes depending on the performance of your machine.

### Command line

To delete any existing Snowstorm Elasticsearch indices and load the RF2 **Snapshot** start Snowstorm with the following arguments:

`java -Xms2g -Xmx2g -jar target/snowstorm*.jar --delete-indices --import=<Absolute-path-of-SNOMED-CT-RF2-zip>`

This will take between 30-60 minutes depending on the performance of your machine.

## Loading Release Full Files

It's possible to load the RF2 **Full** files which gives you access to all previous releases of SNOMED CT in addition to the current content. However, this will  take longer (*last run took 2h15 on an m5.xlarge AWS instance with 4 vCPU and 16GB Memory*), but will not have an impact to the performance.

Simply replace the `--import` argument above with `--import-full` or the `type` with `FULL` within Swagger.

## Stopping After Loading

To shutdown Snowstorm after loading data automatically include the `--exit` flag.
