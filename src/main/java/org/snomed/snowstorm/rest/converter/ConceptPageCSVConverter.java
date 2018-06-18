package org.snomed.snowstorm.rest.converter;

import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.rest.pojo.ItemsPage;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractGenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.util.HashSet;

public class ConceptPageCSVConverter extends AbstractGenericHttpMessageConverter<ItemsPage<ConceptMini>> {

	private static final String TAB = "\t";

	public ConceptPageCSVConverter() {
		super(new MediaType("text", "csv"));
	}

	@Override
	protected boolean supports(Class<?> clazz) {
		return ItemsPage.class.isAssignableFrom(clazz);
	}

	@Override
	protected void writeInternal(ItemsPage<ConceptMini> conceptItemsPage, Type type, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
		try (BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputMessage.getBody()))) {
			bufferedWriter.write("id\tfsn\teffectiveTime\tactive\tmoduleId\tdefinitionStatus");
			bufferedWriter.newLine();
			for (ConceptMini concept : conceptItemsPage.getItems()) {
				bufferedWriter.write(concept.getConceptId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(concept.getFsn());
				bufferedWriter.write(TAB);
				bufferedWriter.write(concept.getEffectiveTime());
				bufferedWriter.write(TAB);
				bufferedWriter.write(concept.getActive() != null ? concept.getActive().toString() : "");
				bufferedWriter.write(TAB);
				bufferedWriter.write(concept.getModuleId());
				bufferedWriter.write(TAB);
				bufferedWriter.write(concept.getDefinitionStatus());
				bufferedWriter.newLine();
			}
		}
	}

	@Override
	public ItemsPage<ConceptMini> read(Type type, Class<?> contextClass, HttpInputMessage inputMessage) throws HttpMessageNotReadableException {
		return new ItemsPage<>(new HashSet<>());
	}

	@Override
	protected ItemsPage<ConceptMini> readInternal(Class<? extends ItemsPage<ConceptMini>> clazz, HttpInputMessage inputMessage) throws HttpMessageNotReadableException {
		return new ItemsPage<>(new HashSet<>());
	}
}
