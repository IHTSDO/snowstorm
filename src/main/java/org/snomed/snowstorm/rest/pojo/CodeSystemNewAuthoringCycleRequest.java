package org.snomed.snowstorm.rest.pojo;

public class CodeSystemNewAuthoringCycleRequest {

    private String packageName;
    private String previousRelease;

    public CodeSystemNewAuthoringCycleRequest() {

    }

    public CodeSystemNewAuthoringCycleRequest(String packageName, String previousRelease) {
        this.packageName = packageName;
        this.previousRelease = previousRelease;
    }

    public String getPreviousRelease() {
        return previousRelease;
    }

    public void setPreviousRelease(String previousRelease) {
        this.previousRelease = previousRelease;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }
}
