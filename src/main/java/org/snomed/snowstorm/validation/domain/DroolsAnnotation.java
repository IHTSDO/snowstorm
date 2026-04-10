package org.snomed.snowstorm.validation.domain;

import org.ihtsdo.drools.domain.Annotation;

public class DroolsAnnotation implements Annotation {

    private final org.snomed.snowstorm.core.data.domain.Annotation annotation;

    public DroolsAnnotation(org.snomed.snowstorm.core.data.domain.Annotation annotation) {
        this.annotation = annotation;
    }

    public String getReleaseHash() {
        return annotation.getReleaseHash();
    }

    @Override
    public String getTypeId() {
        return annotation.getTypeId();
    }

    @Override
    public String getValue() {
        return annotation.getValue();
    }

    @Override
    public String getConceptId() {
        return annotation.getConceptId();
    }

    @Override
    public String getLanguageDialectCode() {
        return annotation.getLanguageDialectCode();
    }

    @Override
    public String getId() {
        return annotation.getId();
    }

    @Override
    public boolean isActive() {
        return annotation.isActive();
    }

    @Override
    public boolean isPublished() {
        return annotation.getEffectiveTimeI() != null;
    }

    @Override
    public boolean isReleased() {
        return annotation.isReleased();
    }

    @Override
    public String getModuleId() {
        return annotation.getModuleId();
    }

    @Override
    public String getEffectiveTime() {
        return annotation.getEffectiveTime();
    }
}
