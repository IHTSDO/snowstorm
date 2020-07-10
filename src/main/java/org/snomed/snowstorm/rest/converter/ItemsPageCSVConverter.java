package org.snomed.snowstorm.rest.converter;

import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.classification.RelationshipChange;
import org.snomed.snowstorm.core.pojo.TermLangPojo;
import org.snomed.snowstorm.core.util.DescriptionHelper;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractGenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.CollectionUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.*;

public class ItemsPageCSVConverter extends AbstractGenericHttpMessageConverter<ItemsPage<?>> {

	private static final String TAB = "\t";

	private static final String PREFERRED_TERM_PREFIX = "pt_";

	public ItemsPageCSVConverter() {
		super(new MediaType("text", "csv"));
	}

	@Override
	protected boolean supports(Class<?> clazz) {
		return ItemsPage.class.isAssignableFrom(clazz);
	}

	@Override
	protected void writeInternal(ItemsPage<?> itemsPage, Type type, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
		try (BufferedWriter writer = new NullSafeBufferedWriter(new OutputStreamWriter(outputMessage.getBody()))) {
			Collection<?> items = itemsPage.getItems();
			if (!items.isEmpty()) {
				Object item = items.iterator().next();
				if (ConceptMini.class.isAssignableFrom(item.getClass())) {
					writeConceptMinis(writer, (Collection<ConceptMini>) items);
				} else if (RelationshipChange.class.isAssignableFrom(item.getClass())) {
					writeRelationshipChanges(writer, (Collection<RelationshipChange>) items);
				} else {
					writer.write("No rows");
					writer.newLine();
				}
			}
		}
	}

	private void writeRelationshipChanges(BufferedWriter writer, Collection<RelationshipChange> items) throws IOException {
		writer.write("changeNature\tsourceId\tsourceFsn\ttypeId\ttypeFsn\tdestinationId\tdestinationFsn\tdestinationNegated\tcharacteristicTypeId\tgroup\tid\tunionGroup\tmodifier");
		writer.newLine();
		for (RelationshipChange change : items) {
			// changeNature
			writer.write(change.getChangeNature().toString());
			writer.write(TAB);
			// sourceId
			writer.write(change.getSourceId());
			writer.write(TAB);
			// sourceFsn
			writer.write("\"");
			writer.write(change.getSourceFsn());
			writer.write("\"");
			writer.write(TAB);
			// typeId
			writer.write(change.getTypeId());
			writer.write(TAB);
			// typeFsn
			writer.write("\"");
			writer.write(change.getTypeFsn());
			writer.write("\"");
			writer.write(TAB);
			// destinationId
			writer.write(change.getDestinationId());
			writer.write(TAB);
			// destinationFsn
			writer.write("\"");
			writer.write(change.getDestinationFsn());
			writer.write("\"");
			writer.write(TAB);
			// destinationNegated
			writer.write("false");
			writer.write(TAB);
			// characteristicTypeId
			writer.write(Concepts.INFERRED_RELATIONSHIP);
			writer.write(TAB);
			// group
			writer.write(change.getGroup() + "");
			writer.write(TAB);
			// id
			writer.write(change.getRelationshipId());
			writer.write(TAB);
			// unionGroup
			writer.write(change.getUnionGroup() + "");
			writer.write(TAB);
			// modifier
			writer.write("EXISTENTIAL");
			writer.write(TAB);
			writer.newLine();
		}
	}

	private void writeConceptMinis(BufferedWriter writer, Collection<ConceptMini> items) throws IOException {
		boolean isHeaderWritten = false;
		writer.write("id\tfsn\teffectiveTime\tactive\tmoduleId\tdefinitionStatus");
		List<String> ptColumns = new ArrayList<>();
		for (ConceptMini concept : items) {
			Map<String, String> extraFields = this.getConceptExtraFields(concept);
			// Add new additional columns for preferred terms
			if (!isHeaderWritten) {
				for (String key : extraFields.keySet()) {
					writer.write(TAB);
					writer.write(key);
					ptColumns.add(key);
				}
				writer.newLine();
				isHeaderWritten = true;
			}
			writer.write(concept.getConceptId());
			writer.write(TAB);
			writer.write(concept.getFsnTerm());
			writer.write(TAB);
			writer.write(concept.getEffectiveTime());
			writer.write(TAB);
			writer.write(concept.getActive() != null ? concept.getActive().toString() : "");
			writer.write(TAB);
			writer.write(concept.getModuleId());
			writer.write(TAB);
			writer.write(concept.getDefinitionStatus());
			for (int i = 0; i <  ptColumns.size(); i++) {
				writer.write(TAB);
				writer.write(extraFields.get(ptColumns.get(i)));
			}
			writer.newLine();
		}
	}

	@Override
	public ItemsPage<ConceptMini> read(Type type, Class<?> contextClass, HttpInputMessage inputMessage) throws HttpMessageNotReadableException {
		return new ItemsPage<>(new HashSet<>());
	}

	@Override
	protected ItemsPage<ConceptMini> readInternal(Class<? extends ItemsPage<?>> clazz, HttpInputMessage inputMessage) throws HttpMessageNotReadableException {
		return new ItemsPage<>(new HashSet<>());
	}

	private Map<String, String> getConceptExtraFields(ConceptMini concept) {
		Map<String, String> extraFields = new HashMap <>();
		if (!CollectionUtils.isEmpty(concept.getRequestedLanguageDialects())) {
			concept.getRequestedLanguageDialects().forEach(languageDialect -> {
				if (languageDialect.getLanguageReferenceSet() != null) {
					TermLangPojo termLangPojo = DescriptionHelper.getPtDescriptionTermAndLang(concept.getActiveDescriptions(), Arrays.asList(languageDialect));
					extraFields.put(PREFERRED_TERM_PREFIX + languageDialect.getLanguageReferenceSet().toString(), termLangPojo.getTerm());
				}
			});
		}

		return extraFields;
	}

	private static final class NullSafeBufferedWriter extends BufferedWriter {

		public NullSafeBufferedWriter(Writer out) {
			super(out);
		}

		@Override
		public void write(String str) throws IOException {
			if (str == null) {
				str = "";
			}
			super.write(str);
		}
	}
}
