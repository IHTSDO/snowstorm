# Loading SNOMED CT

First [download](http://www.snomed.org/) the most recent International Edition.

## Loading Release Snapshot

This loads the content of the current release and skips loading outdated content. This is the recommended option.

To delete any existing Snowstorm Elasticsearch indices and load the RF2 **Snapshot** start Snowstorm with the following arguments:

`java -Xmx4g -jar target/snowstorm*.jar --delete-indices --import=<Absolute-path-of-SNOMED-CT-RF2-zip>`

This will take between 30-60 minutes depending on the performance of your machine/server.

## Loading Release Full Files

It's possible to load the RF2 **Full** files which gives you access to previous releases in addition to the current content. This will probably take many hours.
Simply replace the `--import` argument above with `--import-full`.
