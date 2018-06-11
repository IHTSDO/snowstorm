# Command Line Utilities

## CIS Client
The Snowstorm application can be used as a simple client of the Component Identifier Service to increment identifier counters. This is useful when restoring data in a UAT environment from production.

In order to reserve a block of identifiers to increment counters in CIS for a known namespace and partition use the following:

`java -jar snowstorm*.jar --cis-client --bulk-reserve http://CIS_URL USERNAME PASSWORD NAMESPACE_ID PARTITION_ID QUANTITY`
