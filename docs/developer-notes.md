
##Eclipse JUnit issues
Issue: java.lang.NoClassDefFoundError: com/amazonaws/ClientConfigurationFactory
is caused by Eclipse not handling the wildcard in the pom exclusion section for aws-signing-request-interceptor
Edit the .classpath file and change:
<classpathentry kind="var" path="M2_REPO/com/amazonaws/aws-java-sdk-core/1.10.19/aws-java-sdk-core-1.10.19.jar"/>
to
 <classpathentry kind="var" path="M2_REPO/com/amazonaws/aws-java-sdk-core/1.11.18/aws-java-sdk-core-1.11.18.jar"/>