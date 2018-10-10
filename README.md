# ❄️ Snowstorm Terminology Server

Snowstorm is a prototype SNOMED CT terminology server built on top of Elasticsearch, with a focus on performance and enterprise scalability.

## Project Status

**read-only** - tested and already used in the SNOMED International Browser. Snowstorm can be used in local implementations to query SNOMED CT with the following features:
- hosting multiple extensions alongside the International Edition of SNOMED CT
- multi-lingual for others languages aside from English
- fully ECL v1.3 compliant
- full history (depending on initial import decision)

**write/update** - although features are available, this aspect is currently not production ready having not been fully tested, and should be consider to still be a proof of concept

Please add any issues or any questions in the [GitHub issues page](https://github.com/IHTSDO/snowstorm/issues).

## Documentation
Documentation is sparse for now, but will be improved as the project moves out of a proof of concept phase.

- [Getting Started (plain installation)](docs/getting-started.md)
- [Loading SNOMED](docs/loading-snomed.md)
- [Using the API](docs/using-the-api.md)
- [Configuration Guide](docs/configuration-guide.md)
- [Docker Quickstart (Experimental)](docs/using-docker.md)

## Contributing

1. Fork it!
2. Create your feature branch: `git checkout -b my-new-feature`
3. Commit your changes: `git commit -am 'Add some feature'`
4. Push to the branch: `git push origin my-new-feature`
5. Submit a pull request

## License

Apache 2.0 

See the included LICENSE file for details.