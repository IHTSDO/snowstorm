# Using the Snowstorm REST API
Once Snowstorm is running, the Swagger API documentation will be accessable here <http://localhost:8080/>.

## Querying 101
- Retrieve a SNOMED concept by identifier: <http://localhost:8080/browser/MAIN/concepts/138875005>
- Find concepts by FSN term prefix: <http://localhost:8080/MAIN/concepts?term=asthma>
- Find concepts by [ECL](http://snomed.org/ecl) query: [http://localhost:8080/MAIN/concepts?ecl=<404684003|Clinical finding|](http://localhost:8080/MAIN/concepts?ecl=%3C404684003%7CClinical%20finding%7C)
