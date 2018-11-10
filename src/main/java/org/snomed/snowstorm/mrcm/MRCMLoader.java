package org.snomed.snowstorm.mrcm;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.mrcm.model.Attribute;
import org.snomed.snowstorm.mrcm.model.Domain;
import org.snomed.snowstorm.mrcm.model.MRCM;
import org.snomed.snowstorm.mrcm.model.load.*;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

class MRCMLoader {

	private final String mrcmXmlPath;

	private static Logger logger = LoggerFactory.getLogger(MRCMLoader.class);
	private static final String MRCM_FILE_NAME = "mrcm.xmi";

	public MRCMLoader(String mrcmXmlPath) {
		this.mrcmXmlPath = mrcmXmlPath;
	}

	Map<String, MRCM> loadFromFiles() throws ServiceException {
		// Create MRCM config directory if it does not exist
		File mrcmDir = new File(mrcmXmlPath);
		File mrcmMainDir = new File(mrcmDir, "MAIN");
		if (!mrcmMainDir.exists()){
			if (!mrcmMainDir.mkdirs()) {
				throw new ServiceException("Failed to create MRCM configuration directory " + mrcmMainDir.getAbsolutePath());
			}
		}

		// Create MAIN MRCM file from the default if it does not yet exist
		File mrcmMainFile = new File(mrcmMainDir, "mrcm.xmi");
		if (!mrcmMainFile.isFile()) {
			try {
			if (!mrcmMainFile.createNewFile()) {
				throw new ServiceException("Failed to create MRCM configuration file " + mrcmMainFile.getAbsolutePath());
			}
				Streams.copy(getClass().getResourceAsStream("/mrcm/" + MRCM_FILE_NAME), new FileOutputStream(mrcmMainFile), true);
			} catch (IOException e) {
				throw new ServiceException("Failed to write default MRCM configuration file " + mrcmMainFile.getAbsolutePath());
			}
		}

		// Walk mrcm directories. The directory path will be used as the branch path for each mrcm.xmi file.

		Map<String, MRCM> branchMrcmMap = new HashMap<>();
		try {
			Files.walkFileTree(mrcmDir.toPath(), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) throws IOException {
					File file = filePath.toFile();
					if (file.isFile() && file.getName().equals(MRCM_FILE_NAME)) {
						MRCM mrcm = load(new FileInputStream(file));
						String branchPath = file.getAbsolutePath().substring(mrcmDir.getAbsolutePath().length());
						branchMrcmMap.put(branchPath, mrcm);
					}
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}

		return branchMrcmMap;
	}

	private MRCM load(InputStream mrcmXmlStream) throws IOException {
		Map<Long, Domain> domainMap = new HashMap<>();
		Map<Long, Attribute> attributeMap = new HashMap<>();

		logger.info("Loading MRCM from XML document.");
		XmlMapper mapper = new XmlMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		ConceptModel conceptModel = mapper.readValue(mrcmXmlStream, ConceptModel.class);
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
