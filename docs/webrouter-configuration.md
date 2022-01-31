# Web Router Configuration

## Design Overview

The Web Router endpoint is primarily designed to facilitate resolution of SNOMED URIs on a per namespace, per application basis.  The default configuration provided will resolve any URL to http://snomed.info/id/<SCTID> to either the native json representation of the concept, or if "application/fhir+json" is detected as an HTTP Accept Header, the FHIR API  - both with a redirection to the local instance.  This configuration will work with a default installation of Snowstorm.

SNOMED International maintains the following configuration in it's application.properties file to redirect SCTID URI lookups to the publicly available SI browser:

```
uri.dereferencing.namespaceConfig.default.all=text/html|https://browser.ihtsdotools.org/?perspective=full&conceptId1={SCTID}&edition={BRANCH}
uri.dereferencing.namespaceConfig.default.json=application/json|https://browser.ihtsdotools.org/snowstorm/snomed-ct/browser/{BRANCH}/concepts/{SCTID}
uri.dereferencing.namespaceConfig.default.fhir=application/fhir+json|https://browser.ihtsdotools.org/fhir/CodeSystem/$lookup?code={SCTID}&version={VERSION_URI}&property=normalForm
uri.dereferencing.namespaceConfig.fall-back=*/*|https://cis.ihtsdotools.org/api/sct/check/{SCTID}
```

The redirection will depend on the HTTP Accept Headers being used.  By default ("all") or if "text/html" is received then the 302 redirect issued will point to the public browser website.   If "application/json" is received, then a URL pointing to the native json representation will be returned.   If "application/fhir+json" is received then a URL pointing to the FHIR CodeSystem Lookup endpoint will be returned.

In addition, because HTTP Accept Headers are not always available (eg when using a browser) it is acceptable to specify ?_format=<accept alias> to force a particular application context when working with such restrictions eg from a browser address bar.


## Documentation

The format of each redirection configuration is:

```
uri.dereferencing.namespaceConfig.<namespace>.<accept alias>=<accept headers> PIPE <redirection template>
```

The redirection template can use placeholders (wrapped in curly braces) as follows:

BRANCH - if the concept is found, this will be the branch path that it exists on - likely the most recent version for a given extension.
SCTID - this is the SNOMED SCTID being referenced
VERSION_URI - this is the full URI where the concept was published including the module and effective time.

Note that the server will attempt to direct ALL lookups to the default namespace if the SCTID can be found on the current server.  Specific Namespaces are _only_ expected to be configured where the owner of that namespace (or the configured server) has _not_ elected to have their extension made available on that server.   So for example, if AU had not supplied SNOMED International with their extension to host in the SI Public Browser, the Web Router should be configured to redirect to AU's equivalent application server:

```
uri.dereferencing.namespace.1000036.all=*/*|http://ontoserver.csiro.au/shrimp/?concept={SCTID}
```

When an SCTID is received which is NOT found on the hosting server, and also the namespace indicated _has not_ been configured, then the fall-back configuration will be invoked.   In this case this directs to a lookup of the namespace itself, which is the most helpful thing that can be return in that circumstance.

Obviously these lookups are expected to be received by a server at http://snomed.info To allow for testing in other environments, the URI prefix can be configured eg:

```
uri.dereferencing.prefix=http://dev.snomed.info/
```

This allows for servers other than snomed.info to receive URI lookups and still be able to dereference them without indicating an error.  The canonical URI format is then used in all forwarding instructions.

###Examples

#### Concept identifiers

The identifier for a concept will be looked up across all CodeSystems, so this example for American Cheese when looked up using a web browser will resolve to the US Edition in the SNOMED CT Browser: 

http://snomed.info/id/443971000124108

#### Module specific identifiers

The URI for a module will resolve to the concept that represents that module:

http://dev.snomed.info/sct/45991000052106  

And then for a specific concept in that module:

http://dev.snomed.info/sct/45991000052106/id/60091000052109

#### Version specific identifiers

And in a particular version of that extension:

http://dev.snomed.info/sct/45991000052106/version/20210531/id/60091000052109