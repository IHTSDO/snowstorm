# Startup Syndication Documentation

This document provides an overview of how to configure the loading of terminologies on application startup.

## Application startup arguments
The `--syndicate` option instructs Snowstorm to import the version-specific terminologies (Loinc, Snomed-CT, Hl7) included in the command, as well as the custom-version terminologies (atc, bcp47, Ucum, ...) 
Other arguments can be provided to control which custom-version terminologies to import and which loading mode (see syndication-terminologies.md) is used.

### Notes
- Ensure **Elasticsearch is up and reachable** at the configured URL before Snowstorm's startup.
- You can modify the `CMD` in the Dockerfile or pass a custom `command` via `docker-compose`.
- SNOMED requires an additional `extension-country-code` argument.

## ⚙️ Sample `docker-compose` Command Config
This sample shows how to control what terminologies to load on container startup:

```yaml
command: [
  "--elasticsearch.urls=http://es:9200",
  "--syndicate",
  "--hl7",
  #"--hl7=local",
  #"--hl7=6.1.0",
  #"--loinc",
  "--loinc=local",
  #"--loinc=2.78",
  #"--snomed=http://snomed.info/sct/11000172109",
  #"--snomed=local",
  "--snomed=http://snomed.info/sct/11000172109/version/20250315",
  "--extension-country-code=BE"
]
```

> 💡 **Tip**: Comment/uncomment lines to enable specific terminology imports or versions.  
> Omit all import arguments (not the elasticsearch url) to skip terminology import entirely.

---

## Useful resources

- [SNOMED URI Examples](https://confluence.ihtsdotools.org/display/DOCEXTPG/4.4.2+Edition+URI+Examples)
- [LOINC Archive](https://loinc.org/downloads/archive/)
- [HL7 Terminology Package on Simplifier](https://simplifier.net/packages/hl7.terminology)

