# Querying the server

Once you have built the application and loaded the data, you can access the server in your browser - <http://localhost:8080/> - where you'll see the swagger UI.

## Querying 101

- To retrieve a SNOMED concept based up an identifier: <http://localhost:8080/browser/MAIN/concepts/138875005>
- To run a simple search (here for Asthma): <http://localhost:8080/MAIN/concepts?stated=false&term=asthma&page=0&size=10>
