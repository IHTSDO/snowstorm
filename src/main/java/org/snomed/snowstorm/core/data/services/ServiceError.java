package org.snomed.snowstorm.core.data.services;

public class ServiceError extends Error {
    public ServiceError() {

    }

    public ServiceError(final String message) {
        super(message);
    }
}
