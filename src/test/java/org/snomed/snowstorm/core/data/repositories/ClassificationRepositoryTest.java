package org.snomed.snowstorm.core.data.repositories;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.classification.Classification;
import org.snomed.snowstorm.core.data.domain.classification.ClassificationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class ClassificationRepositoryTest extends AbstractTest {

	@Autowired
	private ClassificationRepository repository;

	@Test
	void testFind() {
		Date now = new Date();
		final Classification classification = new Classification();
		classification.setStatus(ClassificationStatus.SAVED);
		classification.setSaveDate(now);
		final String path = "MAIN/A/A-1";
		classification.setPath(path);
		repository.save(classification);

		final Classification found = repository.findOneByPathAndStatusAndSaveDate(path, ClassificationStatus.SAVED, now.getTime());
		assertNotNull(found);
	}

}
