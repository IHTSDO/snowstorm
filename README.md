![Snowstorm Terminology Server](docs/images/SNOWSTORM-logo.svg)

[![Build Status](https://travis-ci.org/IHTSDO/snowstorm.svg?branch=master)](https://travis-ci.org/IHTSDO/snowstorm) [![Language grade: Java](https://img.shields.io/lgtm/grade/java/g/IHTSDO/snowstorm.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/IHTSDO/snowstorm/context:java) [![codecov](https://codecov.io/gh/IHTSDO/snowstorm/branch/master/graph/badge.svg)](https://codecov.io/gh/IHTSDO/snowstorm)
[![Docker](https://img.shields.io/docker/pulls/snomedinternational/snowstorm)](https://hub.docker.com/r/snomedinternational/snowstorm)

**This is the Snowstorm-X project, a beta version of [Snowstorm](https://github.com/IHTSDO/snowstorm) with the latest implementation-focused features and enhancements. Please be aware these features have not yet been fully tested.**

Snowstorm is an open source terminology server with special support for SNOMED CT. It is built on top of Elasticsearch, with a focus on performance and enterprise scalability.

SNOMED International is not able to offer commercial support for this product. 
Support is provided by the community via this repository.

### APIs
Snowstorm has two APIs:
- **HL7 FHIR API** :fire:
  - Implements the Terminology Module
  - Recommended for implementers
  - Supports SNOMED CT, LOINC, ICD-10, ICD-10-CM and other code systems
- Specialist **SNOMED CT API**
  - Supports the management of SNOMED CT code systems
  - Supports the SNOMED CT Browser
  - Supports authoring SNOMED CT editions

## Advice for Implementers
SNOMED International recommends that implementers of SNOMED CT use a terminology service, such as Snowstorm, and a standard interface, such as the [HL7 FHIR API](http://hl7.org/fhir/).

This approach allows loose coupling of applications as well as access to powerful terminology features.

Snowstorm is a good choice for teams who are just getting started or who have terminology and technical support capability. [Other terminology servers](https://confluence.ihtsdotools.org/display/IMP/Terminology+Services) are available, some offer 
commercial support.

## SNOMED CT Browser Support
Snowstorm provides the terminology server API for the SNOMED International Browser including the International Edition and around fourteen national Editions.

Snowstorm can be used in local implementations to query SNOMED CT with the following features:
- Hosting multiple extensions alongside the International Edition of SNOMED CT
- Multi-lingual search and content retrieval
- Fully ECL v2.0 compliant
- Full history (depends on full RF2 import)
- Read-only FHIR API :fire:

## Authoring Use
Snowstorm also provides the terminology server API for the SNOMED International Authoring Platform.

The Authoring Platform is used for the maintenance of the International Edition of SNOMED CT as well as nine national Editions and several community content Extensions.

## Documentation

- Setup
  - [Getting Started (plain installation)](docs/getting-started.md)
  - [Configuration Guide](docs/configuration-guide.md)
    - [Security Configuration Guide](docs/security-configuration.md)
    - [Nginx Setup (SSL)](docs/nginx-setup.md)
  - [Docker Quickstart](docs/using-docker.md)
  - [Elasticsearch Index Mapping Changes](docs/index-mapping-changes.md)
- Loading SNOMED CT content
  - [Loading SNOMED](docs/loading-snomed.md)
  - [Loading & updating SNOMED CT with local Extensions or Editions](docs/updating-snomed-and-extensions.md)
- Authoring SNOMED CT
  - [Extension Authoring](docs/extension-authoring.md)
- Use
  - [Using the FHIR API](docs/using-the-fhir-api.md)
  - [Using the Specialist SNOMED API](docs/using-the-api.md)
    - [Code Systems & Branches](docs/code-systems-and-branches.md)
    - [Search Guide](docs/search.md)
    - [Language Specific Search Behaviour](docs/language-specific-search.md)
- Productionization
  - [Load Balancing](docs/load-balancing.md)

## Contributing

We welcome questions, ideas, issues and code contributions to this project. 

Use the [issues page](https://github.com/IHTSDO/snowstorm/issues) to get in touch with the community. 

If you would like to make a code contribution please fork the repository and create a 
[GitHub pull request](https://help.github.com/en/github/collaborating-with-issues-and-pull-requests) to the `develop` branch.

## License

Apache 2.0

See the included LICENSE file for details.

## Tools

For Java performance profiling we recommend the JProfiler [Java profiler](https://www.ej-technologies.com/products/jprofiler/overview.html).
