# Snowstorm Configuration Guide

## Overview
Snowstorm uses the Spring Boot framework so this [general information about Spring Boot configuration](https://docs.spring.io/spring-boot/docs/2.0.1.RELEASE/reference/htmlsingle/#boot-features-external-config) is recommended.

See also: 
- [Security Configuration Guide](security-configuration.md)
- [Language Specific Search Behaviour](language-specific-search.md)

## Default Configuration Options

The full list of the configuration options, their defaults and an explanation of each option can be found in [src/main/resources/**application.properties**](/src/main/resources/application.properties). 

## Override the default

The defaults can be overridden using either an external properties file or command line parameters.

### Using a properties file
For example to override the HTTP port configuration using a properties file.
Create a properties named **application-local.properties** with content:
```properties
server.port=8095
snowstorm.rest-api.readonly=true
```
Then start Snowstorm with an extra JVM parameter:
```bash
--spring.config.additional-location=application-local.properties
```

### Using command line arguments
Properties can also be overridden using command line arguments. 
For example by starting Snowstorm with an extra JVM parameter:
```bash
--server.port=8095
--snowstorm.rest-api.readonly=true
```

#### Note - when running in an AWS environment

Snowstorm uses Spring Cloud libraries which will detect when the application is run in AWS. The libraries expect there to be credentials in the environment which can be used to load resources from S3. If there are no credentials available startup will fail with the following error reported `com.amazonaws.SdkClientException: Unable to load AWS credentials from any provider in the chain`.

This AWS auto configuration behaviour can be disabled using this property in your application.properties file:

```bash
spring.autoconfigure.exclude=org.springframework.cloud.aws.autoconfigure.context.ContextStackAutoConfiguration
```

Or by setting the same property using a command line argument when starting Snowstorm:

```
--spring.autoconfigure.exclude=org.springframework.cloud.aws.autoconfigure.context.ContextStac
```
