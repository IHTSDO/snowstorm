package org.snomed.snowstorm.mrcm;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.mrcm.model.Attribute;
import org.snomed.snowstorm.mrcm.model.Domain;
import org.snomed.snowstorm.mrcm.model.MRCM;
import org.snomed.snowstorm.mrcm.model.load.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MRCMLoader {

	private static Logger logger = LoggerFactory.getLogger(MRCMLoader.class);

	public MRCM load() throws IOException {
		Map<Long, Domain> domainMap = new HashMap<>();
		Map<Long, Attribute> attributeMap = new HashMap<>();

		logger.info("Loading MRCM file.");
		XmlMapper mapper = new XmlMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		// TODO: accept stream from a known location on disk or over REST
		InputStream resourceAsStream = getClass().getResourceAsStream("/mrcm/mrcm.xmi");
		ConceptModel conceptModel = mapper.readValue(resourceAsStream, ConceptModel.class);
		for (Constraints constraints : conceptModel.getConstraints()) {
			org.snomed.snowstorm.mrcm.model.load.Domain loadDomain = constraints.getDomain();
			Long domainConceptId = loadDomain.getConceptId();
			logger.debug("domain " + domainConceptId);
			Predicate predicate = constraints.getPredicate();
			Predicate predicate1 = predicate.getPredicate();
			org.snomed.snowstorm.mrcm.model.load.Attribute loadAttribute = predicate1.getAttribute();
			if (loadAttribute != null) {

				Domain domain = domainMap.computeIfAbsent(domainConceptId,
						k -> new Domain(domainConceptId, loadDomain.getInclusionType()));

				Attribute attribute = new Attribute(loadAttribute.getConceptId(), loadAttribute.getInclusionType());
				attributeMap.put(attribute.getConceptId(), attribute);
				domain.getAttributes().add(attribute);

				logger.debug("attribute " + loadAttribute.getConceptId());
				Range range = predicate1.getRange();
				Long rangeConcepId = range.getConceptId();
				logger.debug("range");
				if (rangeConcepId != null) {
					logger.debug("- " + rangeConcepId);
					attribute.getRangeSet().add(new org.snomed.snowstorm.mrcm.model.Range(rangeConcepId, range.getInclusionType()));
				} else {
					for (Children rangeChild : range.getChildren()) {
						logger.debug("+- " + rangeChild.getConceptId());
						attribute.getRangeSet().add(new org.snomed.snowstorm.mrcm.model.Range(rangeChild.getConceptId(), rangeChild.getInclusionType()));
					}
				}
			} else {
				logger.debug("Lexical constraint will be ignored.");
			}
		}
		MRCM mrcm = new MRCM();
		mrcm.setDomainMap(domainMap);
		mrcm.setAttributeMap(attributeMap);
		logger.info("MRCM loaded with {} domains and {} attributes.", domainMap.size(), attributeMap.size());
		return mrcm;
	}
}
