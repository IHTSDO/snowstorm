# Mock MLDS Feed

These files allow the simulation of a test MLDS feed to support development and testing of the Snowstorm Syndication functionality.

## How to use
Navigate to this directory on the command line then serve the content over HTTP on port 8000 using Python:
```
python -m http.server 8000
```
Set Snowstorm configuration `syndication.url=http://localhost:8000`

## Content Available
The RF2 release files included are valid but too small to be meaningful.

The International Edition contains some metadata concepts, a valid MDRS and three clinical concepts: 
- `404684003 |Clinical finding (finding)|`
- `438949009 |Alive (finding)|`
- `131148009 |Bleeding (finding)|`. 

The Belgian release contains a valid MDRS and only translations of the concept `438949009 |Alive (finding)|`.
