package org.snomed.snowstorm.rest;

import org.junit.jupiter.api.Test;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.core.data.services.ReferenceSetMemberService;
import org.snomed.snowstorm.loadtest.ItemsPagePojo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestConfig.class)
class ReferenceSetMemberControllerTest extends AbstractTest {
	private static final ParameterizedTypeReference<ItemsPagePojo<ReferenceSetMember>> FIND_REFSET_MEMBERS_RESPONSE_TYPE = new ParameterizedTypeReference<>() {
	};

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ReferenceSetMemberService referenceSetMemberService;

	@Test
	void findRefsetMembers_ShouldPage_WhenGivenSearchAfterRequestParameter() throws InterruptedException {
		// create & assert first reference set member
		ReferenceSetMember memberA = new ReferenceSetMember(Concepts.MODEL_MODULE, Concepts.REFSET_DESCRIPTOR_REFSET, Concepts.REFSET_MOVED_FROM_ASSOCIATION);
		referenceSetMemberService.createMember("MAIN", memberA);
		ItemsPagePojo<ReferenceSetMember> firstRequest = findRefsetMembers();
		assertEquals(1, firstRequest.getItems().size()); // memberA

		// create & assert second reference set member
		ReferenceSetMember memberB = new ReferenceSetMember(Concepts.MODEL_MODULE, Concepts.REFSET_DESCRIPTOR_REFSET, Concepts.REFSET_MOVED_TO_ASSOCIATION);
		referenceSetMemberService.createMember("MAIN", memberB);
		assertEquals(2, findRefsetMembers().getItems().size()); // memberA & memberB

		// query first page (using offset & limit)
		ItemsPagePojo<ReferenceSetMember> firstPageMembers = findRefsetMembers(0, 1);
		assertEquals(1, firstPageMembers.getItems().size());
		ReferenceSetMember firstPageMember = getReferenceSetMember(firstPageMembers);

		// query second page (using searchAfter token from firstPage)
		ItemsPagePojo<ReferenceSetMember> secondPageMembers = findRefsetMembers(firstPageMembers.getSearchAfter());
		Thread.sleep(10000);
		assertEquals(1, secondPageMembers.getItems().size());
		ReferenceSetMember secondPageMember = getReferenceSetMember(secondPageMembers);

		// should be different, thus paged
		assertNotEquals(firstPageMember, secondPageMember);
	}

	@Test
	void findRefsetMembers_ShouldReturnSameMember_WhenUsingDifferentPagingMechanisms() {
		// given
		Set<ReferenceSetMember> referenceSetMembers = new HashSet<>();
		Collections.addAll(
				referenceSetMembers,
				// Members are assigned UUIDs & returned in descending order. Cannot know order returned from API beforehand for more specific assertions.
				new ReferenceSetMember(Concepts.MODEL_MODULE, Concepts.REFSET_DESCRIPTOR_REFSET, Concepts.REFSET_MOVED_FROM_ASSOCIATION),
				new ReferenceSetMember(Concepts.MODEL_MODULE, Concepts.REFSET_DESCRIPTOR_REFSET, Concepts.REFSET_MOVED_TO_ASSOCIATION),
				new ReferenceSetMember(Concepts.MODEL_MODULE, Concepts.REFSET_DESCRIPTOR_REFSET, Concepts.REFSET_HISTORICAL_ASSOCIATION),
				new ReferenceSetMember(Concepts.MODEL_MODULE, Concepts.REFSET_DESCRIPTOR_REFSET, Concepts.REFSET_MODULE_DEPENDENCY),
				new ReferenceSetMember(Concepts.MODEL_MODULE, Concepts.REFSET_DESCRIPTOR_REFSET, Concepts.REFSET_SIMPLE)
		);
		referenceSetMemberService.createMembers("MAIN", referenceSetMembers);
		ItemsPagePojo<ReferenceSetMember> searchAfterTokenRequest = findRefsetMembers(0, 1);

		// when
		ItemsPagePojo<ReferenceSetMember> firstRequest = findRefsetMembers(searchAfterTokenRequest.getSearchAfter(), 1); // Requesting second entry (skipping first via searchAfter token)
		ItemsPagePojo<ReferenceSetMember> secondRequest = findRefsetMembers(1, 1); // Requesting second entry (skipping first via offset)

		// then
		ReferenceSetMember firstRequestMember = getReferenceSetMember(firstRequest);
		ReferenceSetMember secondRequestMember = getReferenceSetMember(secondRequest);
		assertEquals(firstRequestMember.getMemberId(), secondRequestMember.getMemberId());
		assertEquals(firstRequestMember.getReferencedComponentId(), secondRequestMember.getReferencedComponentId());
	}

	private ReferenceSetMember getReferenceSetMember(ItemsPagePojo<ReferenceSetMember> firstPageMembers) {
		return firstPageMembers.getItems().iterator().next();
	}

	private ItemsPagePojo<ReferenceSetMember> findRefsetMembers() {
		return this.restTemplate.exchange("http://localhost:" + port + "/MAIN/members", HttpMethod.GET, new HttpEntity<>(null), FIND_REFSET_MEMBERS_RESPONSE_TYPE).getBody();
	}

	private ItemsPagePojo<ReferenceSetMember> findRefsetMembers(int offset, int limit) {
		return this.restTemplate.exchange("http://localhost:" + port + "/MAIN/members?offset=" + offset + "&limit=" + limit, HttpMethod.GET, new HttpEntity<>(null), FIND_REFSET_MEMBERS_RESPONSE_TYPE).getBody();
	}

	private ItemsPagePojo<ReferenceSetMember> findRefsetMembers(String searchAfter) {
		return this.restTemplate.exchange("http://localhost:" + port + "/MAIN/members?searchAfter=" + searchAfter, HttpMethod.GET, new HttpEntity<>(null), FIND_REFSET_MEMBERS_RESPONSE_TYPE).getBody();
	}

	private ItemsPagePojo<ReferenceSetMember> findRefsetMembers(String searchAfter, int limit) {
		return this.restTemplate.exchange("http://localhost:" + port + "/MAIN/members?searchAfter=" + searchAfter + "&limit=" + limit, HttpMethod.GET, new HttpEntity<>(null), FIND_REFSET_MEMBERS_RESPONSE_TYPE).getBody();
	}
}
