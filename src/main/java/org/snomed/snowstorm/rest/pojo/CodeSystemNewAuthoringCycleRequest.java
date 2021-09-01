package org.snomed.snowstorm.rest.pojo;

public class CodeSystemNewAuthoringCycleRequest {

    private String previousPackage;
    private String previousRelease;

    public CodeSystemNewAuthoringCycleRequest() {

    }

    public CodeSystemNewAuthoringCycleRequest(String previousPackage, String previousRelease) {
        this.previousPackage = previousPackage;
        this.previousRelease = previousRelease;
    }

    public String getPreviousRelease() {
        return previousRelease;
    }

    public void setPreviousRelease(String previousRelease) {
        this.previousRelease = previousRelease;
    }

    public String getPreviousPackage() {
        return previousPackage;
    }

    public void setPreviousPackage(String previousPackage) {
        this.previousPackage = previousPackage;
    }
}
