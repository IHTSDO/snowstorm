package org.snomed.snowstorm.syndication.data;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "syndication_import_status")
public class SyndicationImportStatus {

    @Id
    private String terminology;

    @Field(type = FieldType.Keyword)
    private String requestedVersion;

    @Field(type = FieldType.Keyword)
    private String actualVersion;

    @Field(type = FieldType.Boolean)
    private boolean success;

    @Field(type = FieldType.Keyword)
    private String exception;

    @Field(type = FieldType.Long)
    private long timestamp;

    public SyndicationImportStatus() {
    }

    public SyndicationImportStatus(String terminology, String requestedVersion, String actualVersion, boolean success, String exception) {
        this.terminology = terminology;
        this.requestedVersion = requestedVersion;
        this.actualVersion = actualVersion;
        this.success = success;
        this.exception = exception;
        this.timestamp = System.currentTimeMillis();
    }

    public String getTerminology() {
        return terminology;
    }

    public void setTerminology(String terminology) {
        this.terminology = terminology;
    }

    public String getRequestedVersion() {
        return requestedVersion;
    }

    public void setRequestedVersion(String version) {
        this.requestedVersion = version;
    }

    public String getActualVersion() {
        return actualVersion;
    }

    public void setActualVersion(String version) {
        this.actualVersion = version;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getException() {
        return exception;
    }

    public void setException(String exception) {
        this.exception = exception;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}

