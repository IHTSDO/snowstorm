package org.snomed.snowstorm.extension;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.domain.Branch;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.elasticsearch.common.Strings;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.CodeSystem;
import org.snomed.snowstorm.core.data.services.BranchMetadataHelper;
import org.snomed.snowstorm.core.data.services.CodeSystemService;
import org.snomed.snowstorm.core.data.services.NotFoundException;
import org.snomed.snowstorm.core.rf2.RF2Constants;
import org.snomed.snowstorm.core.util.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.snomed.snowstorm.core.data.services.BranchMetadataKeys.*;

@Service
public class ExtensionAdditionalLanguageRefsetUpgradeService {
	@Autowired
	private CodeSystemService codeSystemService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private VersionedContentResourceConfig versionedContentResourceConfig;

	@Autowired
	private ResourceLoader resourceLoader;

	@Autowired
	private BranchMetadataHelper branchMetadataHelper;

	private static final String LANGUAGE_REFSET_DELTA = "der2_cRefset_LanguageDelta-en_";

	private static final String GB_ENGLISH_LANGUAGE_REFSET_ID = "900000000000508004";

	private Logger logger = LoggerFactory.getLogger(ExtensionAdditionalLanguageRefsetUpgradeService.class);

	private Gson gson = new GsonBuilder().create();

	private ResourceManager resourceManager;

	@PostConstruct
	public void init() {
		resourceManager = new ResourceManager(versionedContentResourceConfig, resourceLoader);
	}

	public void generateAdditionalLanguageRefsetDelta(CodeSystem codeSystem, OutputStream resultOutputStream) throws IOException, ReleaseImportException {
		AdditionalRefsetExecutionConfig executionConfig = createExecutionConfig(codeSystem);
		generateAdditionalLanguageRefsetDelta(executionConfig, resultOutputStream);
	}

	void generateAdditionalLanguageRefsetDelta(AdditionalRefsetExecutionConfig executionConfig, OutputStream rf2DeltaZipResultStream) throws IOException, ReleaseImportException {
		logger.info("Start generating additional language refset for code system {}", executionConfig.getCodeSystem().getShortName());
		logger.debug("Additional language refset execution config {}", executionConfig);
		String dependencyPackage = executionConfig.getDependencyPackage();
		InputStream dependencyPackageInputStream = resourceManager.readResourceStreamOrNullIfNotExists(dependencyPackage);
		if (dependencyPackageInputStream == null) {
			throw new NotFoundException(String.format("%s is not found.", dependencyPackage));
		}

		String previousPackage = executionConfig.getPreviousPackage();
		InputStream previousPackageInputStream = resourceManager.readResourceStreamOrNullIfNotExists(previousPackage);
		if (previousPackageInputStream == null) {
			throw new NotFoundException(String.format("%s is not found.", previousPackage));
		}

		// load extension english language refsets from snapshot
		LoadingProfile extensionLoadingProfile = LoadingProfile.complete.withJustRefsets().withRefsets(executionConfig.getDefaultEnglishLanguageRefsetId());

		LanguageRefsetComponentsLoader extensionRefsetLoader = new LanguageRefsetComponentsLoader(executionConfig.getDefaultEnglishLanguageRefsetId());
		new ReleaseImporter().loadSnapshotReleaseFiles(previousPackageInputStream, extensionLoadingProfile, extensionRefsetLoader);
		logger.info("Extension language refset snapshot file loading completed.");

		LoadingProfile gbLanguageRefsetLoadingProfile = LoadingProfile.complete.withJustRefsets().withRefsets(GB_ENGLISH_LANGUAGE_REFSET_ID);
		try (ZipOutputStream zipOutputStream = new ZipOutputStream(rf2DeltaZipResultStream)) {
			LanguageRefsetComponentCopier extensionComponentCopier = new LanguageRefsetComponentCopier(() -> {
				try {
					zipOutputStream.putNextEntry(new ZipEntry(executionConfig.getLanguageRefsetDeltaFilename()));
					return new BufferedWriter(new OutputStreamWriter(zipOutputStream));
				} catch (IOException e) {
					logger.error("Failed to start zip entry", e);
				}
				return null;
			}, executionConfig.getDefaultEnglishLanguageRefsetId(), executionConfig.getDefaultModuleId(), extensionRefsetLoader.getReferencedComponentToUUIDMap());

			if (extensionRefsetLoader.getReferencedComponentToUUIDMap().isEmpty()) {
				logger.info("No existing components found with refset id {} in the extension language refset snapshot file.", executionConfig.getDefaultEnglishLanguageRefsetId());
				// upgrade for the first time
				// copy everything from international GB language refset snapshot and change the UUID, effective time, module id and refset id
				new ReleaseImporter().loadSnapshotReleaseFiles(dependencyPackageInputStream, gbLanguageRefsetLoadingProfile, extensionComponentCopier);
			} else {
				// load international GB language refset members from delta
				logger.info("Not the first time upgrade therefore only need to check the changes in international release delta file.");
				new ReleaseImporter().loadDeltaReleaseFiles(dependencyPackageInputStream, gbLanguageRefsetLoadingProfile, extensionComponentCopier);
			}
			extensionComponentCopier.complete();
			zipOutputStream.closeEntry();
		}
		logger.info("Additional language refset changes are generated for {}", executionConfig.getCodeSystem().getShortName());
	}


	private AdditionalRefsetExecutionConfig createExecutionConfig(CodeSystem codeSystem) {
		AdditionalRefsetExecutionConfig config = new AdditionalRefsetExecutionConfig(codeSystem);
		Branch branch = branchService.findLatest(codeSystem.getBranchPath());
		Map<String, String> metadata = branch.getMetadata();
		String dependencyPackage = metadata != null ? metadata.get(DEPENDENCY_PACKAGE) : null;
		String previousPackage = metadata != null ? metadata.get(PREVIOUS_PACKAGE) : null;
		if (Strings.isNullOrEmpty(dependencyPackage)) {
			throw new IllegalStateException("Missing branch metadata for " + PREVIOUS_PACKAGE);
		}
		if (Strings.isNullOrEmpty(previousPackage)) {
			throw new IllegalStateException("Missing branch metadata for " + DEPENDENCY_PACKAGE);
		}
		config.setDependencyPackage(dependencyPackage);
		config.setPreviousPackage(previousPackage);
		Map<String, Object> expandedMetadata = branchMetadataHelper.expandObjectValues(metadata);
		Object jsonObject = expandedMetadata.get(REQUIRED_LANGUAGE_REFSETS);
		LanguageRefsetMetadataConfig[] configs = gson.fromJson(jsonObject.toString(), LanguageRefsetMetadataConfig[].class);
		String defaultEnglishLanguageRefsetId = null;
		for (LanguageRefsetMetadataConfig metadataConfig : configs) {
			if (metadataConfig.usedByDefault && metadataConfig.getEnglishLanguageRestId() != null)
			{
				defaultEnglishLanguageRefsetId = metadataConfig.getEnglishLanguageRestId();
				break;
			}
		}

		if (defaultEnglishLanguageRefsetId == null) {
			throw new IllegalStateException("Missing default language resfet id for en language in the metadata.");
		}
		String defaultModuleId = metadata.get(DEFAULT_MODULE_ID);
		if (defaultModuleId == null) {
			throw new IllegalStateException("Missing default module id config in the metadata.");
		}
		String namespace = metadata.get(DEFAULT_NAMESPACE);
		String shortName = metadata.get(SHORTNAME);
		String filename = LANGUAGE_REFSET_DELTA + shortName + namespace + "_" + DateUtil.getTodaysEffectiveTime() + ".txt";
		config.setDefaultEnglishLanguageRefsetId(defaultEnglishLanguageRefsetId);
		config.setDefaultModuleId(defaultModuleId);
		config.setLanguageRefsetDeltaFilename(filename);
		return config;
	}

	void setResourceManager(ResourceManager resourceManager) {
		this.resourceManager = resourceManager;
	}

	private static class LanguageRefsetComponentsLoader extends ImpotentComponentFactory {
		private Map<Long, String> referencedComponentToUUIDMap = new Long2ObjectOpenHashMap<>();
		private String languageRefsetId;
		public LanguageRefsetComponentsLoader(String refsetId) {
			this.languageRefsetId = refsetId;
		}
		public void newReferenceSetMemberState(String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
			// extra checking to be safe
			if (languageRefsetId.equals(refsetId)) {
				referencedComponentToUUIDMap.put(new Long(referencedComponentId), id);
			}
		}

		public Map<Long, String> getReferencedComponentToUUIDMap() {
			return this.referencedComponentToUUIDMap;
		}
	}

	 private static class AdditionalRefsetExecutionConfig {

		private String dependencyPackage;
		private String previousPackage;
		private CodeSystem codeSystem;
		private String languageRefsetDeltaFilename;
		private String defaultModuleId;
		private String defaultEnglishLanguageRefsetId;

		public AdditionalRefsetExecutionConfig(CodeSystem codeSystem) {
			this.codeSystem = codeSystem;
		}

		public String getDependencyPackage() {
			return dependencyPackage;
		}

		public String getPreviousPackage() { return previousPackage; }

		public void setDependencyPackage(String dependencyPackage) {
			this.dependencyPackage = dependencyPackage;
		}

		public void setPreviousPackage(String previousPackage) {
			this.previousPackage = previousPackage;
		}

		public void setDefaultEnglishLanguageRefsetId(String defaultEnglishLanguageRefsetId) {
			this.defaultEnglishLanguageRefsetId = defaultEnglishLanguageRefsetId;
		}

		public String getDefaultEnglishLanguageRefsetId() {
			return defaultEnglishLanguageRefsetId;
		}

		public CodeSystem getCodeSystem() {
			return codeSystem;
		}

		public String getLanguageRefsetDeltaFilename() {
			return languageRefsetDeltaFilename;
		}

		public void setLanguageRefsetDeltaFilename(String languageRefsetDeltaFilename) {
			this.languageRefsetDeltaFilename = languageRefsetDeltaFilename;
		}

		public String getDefaultModuleId() {
			return defaultModuleId;
		}

		public void setDefaultModuleId(String defaultModuleId) {
			this.defaultModuleId = defaultModuleId;
		}
	}

	private static class LanguageRefsetComponentCopier extends ImpotentComponentFactory {

		private static final String TAB = "\t";
		private final Supplier<BufferedWriter> startFunction;
		private boolean entryStarted;
		private BufferedWriter writer;
		private final List<IOException> exceptionsThrown;
		private Map<Long, String> componentsToIdMap;
		private String extensionlanguageRefsetId;
		private String extensionModuleId;

		LanguageRefsetComponentCopier(Supplier<BufferedWriter> startFunction, String languageRefsetId, String moduleId, Map<Long, String> componentToIdMap) {
			exceptionsThrown = new ArrayList<>();
			this.startFunction = startFunction;
			this.componentsToIdMap = componentToIdMap;
			extensionlanguageRefsetId = languageRefsetId;
			extensionModuleId = moduleId;
		}

		@Override
		public void newReferenceSetMemberState(String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
			// id	effectiveTime	active	moduleId	refsetId	referencedComponentId	acceptabilityId
			if (refsetId.equals(GB_ENGLISH_LANGUAGE_REFSET_ID)) {
				try {
					startEntry();
					String uuid = componentsToIdMap.get(new Long(referencedComponentId));
					writer.write(uuid == null ? UUID.randomUUID().toString() : uuid);
					writer.write(TAB);
					//don't need effective time so that can be imported as delta into a task
					writer.write(TAB);
					writer.write(active);
					writer.write(TAB);
					writer.write(extensionModuleId);
					writer.write(TAB);
					writer.write(extensionlanguageRefsetId);
					writer.write(TAB);
					writer.write(referencedComponentId);
					writer.write(TAB);
					writer.write(otherValues[0]);
					writer.newLine();
				} catch (IOException e) {
					exceptionsThrown.add(e);
				}
			}
		}

		private void startEntry() throws IOException {
			if (!entryStarted) {
				writer = startFunction.get();
				writer.write(RF2Constants.LANGUAGE_REFSET_HEADER);
				writer.newLine();
				entryStarted = true;
			}
		}

		private void complete() throws IOException {
			startEntry();
			writer.flush();
			// Let exceptions from component factory bubble up
			if (!exceptionsThrown.isEmpty()) {
				throw exceptionsThrown.get(0);
			}
		}
	}

	private static class LanguageRefsetMetadataConfig {

		@SerializedName(value = "usedByDefault", alternate = "default")
		private boolean usedByDefault;

		@SerializedName(value = "englishLanguageRefsetId", alternate = "en")
		private String englishLanguageRestId;

		private boolean readOnly;

		private String dialectName;

		public boolean isUsedByDefault() {
			return usedByDefault;
		}

		public void setUsedByDefault(boolean usedByDefault) {
			this.usedByDefault = usedByDefault;
		}

		public String getEnglishLanguageRestId() {
			return englishLanguageRestId;
		}

		public void setEnglishLanguageRefsetId(String englishLanguageRestId) {
			this.englishLanguageRestId = englishLanguageRestId;
		}

		public boolean isReadOnly() {
			return readOnly;
		}

		public void setReadOnly(boolean readOnly) {
			this.readOnly = readOnly;
		}

		public String getDialectName() {
			return dialectName;
		}

		public void setDialectName(String dialectName) {
			this.dialectName = dialectName;
		}

		@Override
		public String toString() {
			return "LanguageRefsetMetadataConfig{" +
					"usedByDefault=" + usedByDefault +
					", englishLanguageRestId='" + englishLanguageRestId + '\'' +
					", readOnly=" + readOnly +
					", dialectName='" + dialectName + '\'' +
					'}';
		}
	}
}
