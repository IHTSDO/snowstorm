package org.snomed.snowstorm.core.data.services.identifier;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.domain.Description;
import org.snomed.snowstorm.core.data.domain.Relationship;
import org.snomed.snowstorm.core.data.domain.SnomedComponent;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

/**
 * Generates SNOMED Component identifiers locally using random numbers.
 * The store is queried to check that the numbers are unique.
 */
public class LocalRandomIdentifierSource implements IdentifierSource {

	private final ElasticsearchRestTemplate elasticsearchTemplate;

	private ItemIdProvider itemIdProvider;

	public LocalRandomIdentifierSource(ElasticsearchRestTemplate elasticsearchTemplate) {
		this.elasticsearchTemplate = elasticsearchTemplate;
		itemIdProvider = new RandomItemIdProvider();
	}

	@Override
	public List<Long> reserveIds(int namespaceId, String partitionId, int quantity) {
		Set<Long> newIdentifiers = new LongLinkedOpenHashSet();
		List<Long> newIdentifierList = null;
		do {
			String hackId = itemIdProvider.getItemIdentifier();
			String namespace = namespaceId == 0 ? "" : namespaceId + "";
			String sctidWithoutCheck = hackId + namespace + partitionId;
			char verhoeff = VerhoeffCheck.calculateChecksum(sctidWithoutCheck, 0, false);
			long newSctid = Long.parseLong(sctidWithoutCheck + verhoeff);
			newIdentifiers.add(newSctid);
			if (newIdentifiers.size() == quantity) {
				newIdentifierList = new LongArrayList(newIdentifiers);
				// Bulk unique check
				List<Long> alreadyExistingIdentifiers = new LongArrayList();
				for (List<Long> newIdentifierBatch : Lists.partition(newIdentifierList, 10_000)) {
					switch (partitionId) {
						case "00":
						case "10":
							// Concept identifier
							alreadyExistingIdentifiers.addAll(findExistingIdentifiersInAnyBranch(newIdentifierBatch, Concept.class, Concept.Fields.CONCEPT_ID));
							break;
						case "01":
						case "11":
							// Description identifier
							alreadyExistingIdentifiers.addAll(findExistingIdentifiersInAnyBranch(newIdentifierBatch, Description.class, Description.Fields.DESCRIPTION_ID));
							break;
						case "02":
						case "12":
							// Relationship identifier
							alreadyExistingIdentifiers.addAll(findExistingIdentifiersInAnyBranch(newIdentifierBatch, Relationship.class, Relationship.Fields.RELATIONSHIP_ID));
							break;
					}
				}
				// Remove any identifiers which already exist in storage - more will be generated in the next loop.
				newIdentifiers.removeAll(alreadyExistingIdentifiers);
			}
		} while (newIdentifiers.size() < quantity);

		return newIdentifierList;
	}

	// Finds and returns matching existing identifiers
	private List<Long> findExistingIdentifiersInAnyBranch(List<Long> identifiers, Class<? extends SnomedComponent> snomedComponentClass, String idField) {
		NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
				.withQuery(termsQuery(idField, identifiers))
				.withPageable(PageRequest.of(0, identifiers.size()));
		return elasticsearchTemplate.search(queryBuilder.build(), snomedComponentClass)
				.stream().map(hit -> Long.parseLong(hit.getContent().getId())).collect(Collectors.toList());
	}

	@Override
	public void registerIds(int namespace, Collection<Long> idsAssigned) {
		// Not required for this implementation.
	}

	public ItemIdProvider getItemIdProvider() {
		return itemIdProvider;
	}

	void setItemIdProvider(ItemIdProvider itemIdProvider) {
		this.itemIdProvider = itemIdProvider;
	}

	private static final class RandomItemIdProvider implements ItemIdProvider {

		// Generates a string of a random number with a guaranteed length of 8 digits.
		@Override
		public String getItemIdentifier() {
			String id;
			do {
				id = "" + Math.round(Math.random() * 10000000000f);
			} while (id.length() < 8);

			return id.substring(0, 8);
		}

	}

	interface ItemIdProvider {
		String getItemIdentifier();
	}
}
