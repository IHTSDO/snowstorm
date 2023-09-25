package org.snomed.snowstorm.core.data.services;

import org.springframework.stereotype.Service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;

@Service
public class ValidatorService {

	private final ValidatorFactory validatorFactory;

	public ValidatorService() {
		validatorFactory = Validation.buildDefaultValidatorFactory();
	}

	public <T extends Object> void validate(T entity) {
		Validator validator = validatorFactory.getValidator();
		validate(validator, entity);
	}

	public <T extends Object> void validate(Iterable<T> entities) {
		Validator validator = validatorFactory.getValidator();
		for (T entity : entities) {
			validate(validator, entity);
		}
	}

	public <T extends Object> void validate(Validator validator, T entity) {
		Set<ConstraintViolation<T>> violations = validator.validate(entity);
		if (!violations.isEmpty()) {
			ConstraintViolation<T> violation = violations.iterator().next();
			throw new IllegalArgumentException(String.format("Invalid property value %s %s", violation.getPropertyPath().toString(), violation.getMessage()));
		}
	}
}
