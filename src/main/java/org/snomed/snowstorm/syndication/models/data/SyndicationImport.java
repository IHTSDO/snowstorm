package org.snomed.snowstorm.syndication.models.data;

import org.snomed.snowstorm.core.rf2.rf2import.ImportJob;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "syndication_import")
public class SyndicationImport {

    @Id
    private String terminology;

    @Field(type = FieldType.Keyword)
    private String requestedVersion;

    @Field(type = FieldType.Keyword)
    private String actualVersion;

    @Field(type = FieldType.Keyword)
    private ImportJob.ImportStatus status;

    @Field(type = FieldType.Keyword)
    private String exception;

    @Field(type = FieldType.Long)
    private long timestamp;

    public SyndicationImport() {}

    public SyndicationImport(String terminology, String requestedVersion, String actualVersion, ImportJob.ImportStatus status, String exception) {
        this.terminology = terminology;
        this.requestedVersion = requestedVersion;
        this.actualVersion = actualVersion;
        this.status = status;
        this.exception = exception;
        this.timestamp = System.currentTimeMillis();
    }

    public String getTerminology() {
        return terminology;
    }

    public String getRequestedVersion() {
        return requestedVersion;
    }

    public String getActualVersion() {
        return actualVersion;
    }

    public ImportJob.ImportStatus getStatus() {
        return status;
    }

    public String getException() {
        return exception;
    }

    public long getTimestamp() {
        return timestamp;
    }

}

