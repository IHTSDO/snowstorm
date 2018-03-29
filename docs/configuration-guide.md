# Snowstorm Configuration Guide

## Overview
Snowstorm uses the Spring Boot framework so this [general information about Spring Boot configuration](https://docs.spring.io/spring-boot/docs/2.0.0.RC2/reference/htmlsingle/#boot-features-external-config) is recommended.

## The Defaults

The default configuration can be found in src/main/resources/**application.properties**. 

This file also contains an explanation of each option.

## Override the defaults

The defaults can be overridden using either an external properties file or command line parameters.

### Using a a properties file
For example to override the HTTP port configuration using a properties file.
Create a properties named **application-local.properties** with content:
```properties
server.port=8095
```
Then start Snowstorm with an extra JVM parameter:
```bash
--spring.config.location=application-local.properties
```

### Using command line arguments
Properties can also be overridden using command line arguments. 
For example by starting Snowstorm with an extra JVM parameter:
```bash
--server.port=8095
```
