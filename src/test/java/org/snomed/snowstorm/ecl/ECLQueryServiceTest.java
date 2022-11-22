package org.snomed.snowstorm.ecl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.stream.Collectors;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ECLQueryServiceTestConfig.class)
public class ECLQueryServiceTest extends AbstractECLQueryServiceTest {

	@BeforeEach
	void setup() {
		branchCriteria = versionControlHelper.getBranchCriteria(MAIN);
		allConceptIds = eclQueryService.selectConceptIds("*", branchCriteria, false, PageRequest.of(0, 1000))
				.getContent().stream().map(Object::toString).collect(Collectors.toSet());
	}

}
