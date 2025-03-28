#!/bin/sh

node ./loinc/download_loinc.mjs

java \
-Xdebug \
-agentlib:jdwp=transport=dt_socket,server=y,address=*:5005,suspend=n \
-Xms2g -Xmx4g \
--add-opens java.base/java.lang=ALL-UNNAMED \
--add-opens java.base/java.util=ALL-UNNAMED \
-jar /app/snowstorm.jar \
--elasticsearch.urls=http://es:9200 \
--import=/app/snomed/international_sample.zip \
--snomed-version=http://snomed.info/sct/11000172109/version/20250315 \
--extension-country-code=BE \
--import-loinc-terminology
#--import-hl7-terminology \