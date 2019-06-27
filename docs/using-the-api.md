# Using the Snowstorm REST API
Once Snowstorm is running, the Swagger API documentation will be accessable here <http://[serverIp]:8421/doc.html?showMenuApi=1&showDes=1&cache=1&cacheApi=1&filterApi=1&filterApiType=POST&lang=en>.



## Querying 101
- Retrieve a SNOMED concept by identifier: <http://[serverIp]:8421/browser/MAIN/concepts/138875005>
- Find concepts by FSN term prefix: <http://[serverIp]:8421/MAIN/concepts?term=asthma>
- Find concepts by [ECL](http://snomed.org/ecl) query: [http://[serverIp]:8421/MAIN/concepts?ecl=<404684003|Clinical finding|](http://[serverIp]:8080/MAIN/concepts?ecl=%3C404684003%7CClinical%20finding%7C)
