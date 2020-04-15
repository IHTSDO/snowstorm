package org.snomed.snowstorm.util;

import com.google.common.base.Strings;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.factory.ComponentFactory;
import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.snomed.langauges.ecl.ECLException;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.langauges.ecl.domain.expressionconstraint.CompoundExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.ecl.SECLObjectFactory;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ExpressionsDiffUtil {

	public static ECLQueryBuilder eclQueryBuilder = new ECLQueryBuilder(new SECLObjectFactory());

	static final Comparator<SubExpressionConstraint> EXPRESSION_CONSTRAINT_COMPARATOR_BY_CONCEPT_ID = Comparator
			.comparing(SubExpressionConstraint::getConceptId, Comparator.nullsFirst(String::compareTo));

	private static boolean ignoreSorting = true;

	private static boolean ignoreCardinality = true;

	private static boolean outputBeforeAndAfter = false;

	private static final String RELEASE_FILES_ARG = "-releaseFiles";

	private static final String IGNORE_CARDINALITY_ARG = "-ignoreCardinality";

	/**
	 *
	 * @param args -releaseFiles [previous, current] -ignoreCardinality [true]
	 * @throws Exception
	 */
	public static void  main(String[] args) throws Exception {
		if (args == null || args.length < 3 || !RELEASE_FILES_ARG.equals(args[0])) {
			System.out.println("Usage:-releaseFiles [previous, current] -ignoreCardinality [true]");
			return;
		}
		String published = args[1];
		String actual = args[2];
		if (args.length == 5 && IGNORE_CARDINALITY_ARG.equals(args[3])) {
			ignoreCardinality = Boolean.valueOf(args[4]);
		}

		// load MRCM refsets
		LoadingProfile mrcmLoadingProfile = LoadingProfile.complete.withJustRefsets().withRefsets("723560006", "723562003");
		ComponentFactory publishedMRCMs = new MRCMRefsetComponentsLoader();
		ComponentFactory actualMRCMs = new MRCMRefsetComponentsLoader();
		if (published.endsWith(".zip") && actual.endsWith(".zip")) {
			new ReleaseImporter().loadSnapshotReleaseFiles(new FileInputStream(published), mrcmLoadingProfile, publishedMRCMs);
			new ReleaseImporter().loadSnapshotReleaseFiles(new FileInputStream(actual), mrcmLoadingProfile, actualMRCMs);
		} else {
			System.out.println("Please specify release packages with .zip file names");
		}

		List<ReferenceSetMember> publishedMembers = ((MRCMRefsetComponentsLoader) publishedMRCMs).getAllMembers();

		List<ReferenceSetMember> actualMembers = ((MRCMRefsetComponentsLoader) actualMRCMs).getAllMembers();

		if (publishedMembers.size() != actualMembers.size()) {
			System.out.println(String.format("%d mrcm refset members found in %s", publishedMembers.size(), published));
			System.out.println(String.format("%d mrcm refset members found in %s", actualMembers.size(), actual));
		} else {
			if (publishedMembers.size() == 0) {
				System.out.println("No MRCM refset members found please upload Snapshot files not delta files");
			}
			System.out.println(String.format("%d mrcm refset members found in %s and %s", publishedMembers.size(), published, actual));
		}

		File reportDir = new File(published).getParentFile();
		// diff after parsing attribute rule and range constraint
		performDiffAttributeRangeConstraintAndRules(publishedMembers, actualMembers, reportDir);

		// diff domain templates
		peformDiffDomainTemplates(publishedMembers, actualMembers, reportDir);
		System.out.println("Please find the diff reports in folder " + reportDir);
	}

	private static void peformDiffDomainTemplates(List<ReferenceSetMember> published, List<ReferenceSetMember> actual, File reportDir) throws  IOException {
		Map<String, ReferenceSetMember> actualDomainMapById = actual.stream()
				.filter(ReferenceSetMember :: isActive)
				.filter(domain ->  "723560006".equals(domain.getRefsetId()))
				.collect(Collectors.toMap(ReferenceSetMember :: getMemberId, Function.identity()));

		Map<String, ReferenceSetMember> publishedDomainMapById = published.stream()
				.filter(ReferenceSetMember :: isActive)
				.filter(domain ->  "723560006".equals(domain.getRefsetId()))
				.collect(Collectors.toMap(ReferenceSetMember :: getMemberId, Function.identity()));

		if (publishedDomainMapById.keySet().size() != actualDomainMapById.keySet().size()) {
			System.out.println(String.format("%d found in the published and %d found in the actual", publishedDomainMapById.keySet().size(), actualDomainMapById.keySet().size()));
		} else {
			System.out.println(String.format("%d found", publishedDomainMapById.keySet().size()));
		}

		diffDomainTemplates(publishedDomainMapById, actualDomainMapById, reportDir, "domainTemplateForPrecoordination");
		diffDomainTemplates(publishedDomainMapById, actualDomainMapById, reportDir, "domainTemplateForPostcoordination");
	}

	private static void performDiffAttributeRangeConstraintAndRules(List<ReferenceSetMember> publishedRanges, List<ReferenceSetMember> actualRanges, File reportDir) throws IOException {

		Map<String, ReferenceSetMember> actualRangeMapById = actualRanges.stream()
				.filter(ReferenceSetMember :: isActive)
				.filter(range -> "723562003".equals(range.getRefsetId()))
				.collect(Collectors.toMap(ReferenceSetMember :: getMemberId, Function.identity()));

		Map<String, ReferenceSetMember> publishedRangeMapById = publishedRanges.stream()
				.filter(ReferenceSetMember :: isActive)
				.filter(range ->  "723562003".equals(range.getRefsetId()))
				.collect(Collectors.toMap(ReferenceSetMember :: getMemberId, Function.identity()));

		diffRangeConstraints(actualRangeMapById, publishedRangeMapById, reportDir);
		diffAttributeRules(actualRangeMapById, publishedRangeMapById, reportDir);
	}

	private static void diffAttributeRules(Map<String,ReferenceSetMember> actualRangeMapById, Map<String,ReferenceSetMember> publishedRangeMapById, File reportDir) throws IOException {
		List<String> matchedExactly = new ArrayList<>();
		List<String> matchedWhenIgnoringSorting = new ArrayList<>();
		List<String> newMembers = new ArrayList<>();
		List<String> diffMembers = new ArrayList<>();
		Map<String, String> publishedSortedMapByMemberId = new HashMap<>();

		for (String memberId : actualRangeMapById.keySet()) {
			if (!publishedRangeMapById.keySet().contains(memberId)) {
				// new member
				newMembers.add(memberId);
				continue;
			}
			ReferenceSetMember published = publishedRangeMapById.get(memberId);
			ReferenceSetMember actual = actualRangeMapById.get(memberId);
			if (!published.getReferencedComponentId().equals(actual.getReferencedComponentId())) {
				throw new IllegalStateException(String.format("%s has different referencedComponentIds", memberId));
			}
			String publishedRule = publishedRangeMapById.get(memberId).getAdditionalField("attributeRule");
			String actualRule = actualRangeMapById.get(memberId).getAdditionalField("attributeRule");
			if (publishedRule.equals(actualRule)) {
				matchedExactly.add(memberId);
			} else {
				if (ignoreSorting) {
					String publishedSorted = sortExpressionConstraintByConceptId(publishedRule, memberId);
					publishedSortedMapByMemberId.put(memberId, publishedSorted);
					if (publishedSorted.equals(actualRule)) {
						matchedWhenIgnoringSorting.add(memberId);
					} else {
						outputBeforeAndAfter(publishedRule, actualRule);
						diffMembers.add(memberId);
					}
				} else {
					outputBeforeAndAfter(publishedRule, actualRule);
					diffMembers.add(memberId);
				}
			}
		}

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(reportDir, "MRCMAttributeRulesDiff.txt")))) {
			writer.append("Total attribute rules updated = " + actualRangeMapById.keySet().size());
			writer.newLine();
			writer.append("Total new components created = " + newMembers.size());
			writer.newLine();
			writer.append(newMembers.toString());
			writer.newLine();
			writer.append("Total attribute rules are the same without change = " + matchedExactly.size());
			writer.newLine();
			writer.append(matchedExactly.toString());
			writer.newLine();
			writer.append("Total attribute rules are the same when cardinality and sorting are ignored = " + matchedWhenIgnoringSorting.size());
			writer.newLine();
			writer.append(matchedWhenIgnoringSorting.toString());
			writer.newLine();
			writer.append("Total attribute rules found with diffs = " + diffMembers.size());
			writer.newLine();
			for (int i = 0; i < diffMembers.size(); i++) {
				String memberId = diffMembers.get(i);
				writer.append("Diff " + i + ": " + memberId);
				writer.newLine();
				writer.append("Published original:");
				writer.newLine();
				writer.append(publishedRangeMapById.get(memberId).getAdditionalField("attributeRule"));
				writer.newLine();
				if (publishedSortedMapByMemberId.containsKey(memberId)) {
					writer.append("Published and sorted:");
					writer.newLine();
					writer.append(publishedSortedMapByMemberId.get(memberId));
					writer.newLine();
				}
				writer.append("Actual:");
				writer.newLine();
				writer.append( actualRangeMapById.get(memberId).getAdditionalField("attributeRule"));
				writer.newLine();
			}
		}
	}

	private static void diffRangeConstraints(Map<String,ReferenceSetMember> actualRangeMapById, Map<String,ReferenceSetMember> publishedRangeMapById,
											 File reportDir) throws IOException {

		List<String> matchedExactly = new ArrayList<>();
		List<String> matchedWhenIgnoringSorting = new ArrayList<>();
		List<String> newMembers = new ArrayList<>();
		List<String> diffMembers = new ArrayList<>();
		Map<String, String> publishedSortedMapByMemberId = new HashMap<>();

		for (String memberId : actualRangeMapById.keySet()) {
			if (!publishedRangeMapById.keySet().contains(memberId)) {
				// new member
				newMembers.add(memberId);
				continue;
			}
			ReferenceSetMember published = publishedRangeMapById.get(memberId);
			ReferenceSetMember actual = actualRangeMapById.get(memberId);
			if (!published.getReferencedComponentId().equals(actual.getReferencedComponentId())) {
				throw new IllegalStateException(String.format("%s has different referencedComponentIds", memberId));
			}
			String publishedConstraint = published.getAdditionalField("rangeConstraint");
			String actualConstraint = actual.getAdditionalField("rangeConstraint");
			if (publishedConstraint.equals(actualConstraint)) {
				matchedExactly.add(memberId);
			} else {
				if (ignoreSorting) {
					String publishedSorted = sortExpressionConstraintByConceptId(publishedConstraint, memberId);
					publishedSortedMapByMemberId.put(memberId, publishedSorted);
					if (publishedSorted.equals(actualConstraint)) {
						matchedWhenIgnoringSorting.add(memberId);
					} else {
						outputBeforeAndAfter(publishedConstraint, actualConstraint);
						diffMembers.add(memberId);
					}
				} else {
					outputBeforeAndAfter(publishedConstraint, actualConstraint);
					diffMembers.add(memberId);
				}
			}
		}

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(reportDir, "MRCMRangeConstraintDiff.txt")))) {
			writer.append("Total range constraints updated = " + actualRangeMapById.keySet().size());
			writer.newLine();
			writer.append("Total new components created = " + newMembers.size());
			writer.newLine();
			writer.append(newMembers.toString());
			writer.newLine();
			writer.append("Total range constraints are the same without change = " + matchedExactly.size());
			writer.newLine();
			writer.append(matchedExactly.toString());
			writer.newLine();
			writer.append("Total range constraints are the same when cardinality and sorting are ignored = " + matchedWhenIgnoringSorting.size());
			writer.newLine();
			writer.append(matchedWhenIgnoringSorting.toString());
			writer.newLine();
			writer.append("Total range constraints found with diffs = " + diffMembers.size());
			writer.newLine();
			for (int i = 0; i < diffMembers.size(); i++) {
				String memberId = diffMembers.get(i);
				writer.append("Diff " + i + ": " + memberId);
				writer.newLine();
				writer.append("Published original:");
				writer.newLine();
				writer.append(publishedRangeMapById.get(memberId).getAdditionalField("rangeConstraint"));
				writer.newLine();
				if (publishedSortedMapByMemberId.containsKey(memberId)) {
					writer.append("Published and sorted:");
					writer.newLine();
					writer.append(publishedSortedMapByMemberId.get(memberId));
					writer.newLine();
				}
				writer.append("Actual:");
				writer.newLine();
				writer.append( actualRangeMapById.get(memberId).getAdditionalField("rangeConstraint"));
				writer.newLine();
			}
		}
	}


	private static void outputBeforeAndAfter(String published, String actual) {
		if (outputBeforeAndAfter) {
			System.out.println("before = " + published);
			System.out.println("after = " + actual);
		}
	}

	public static void diffDomainTemplates(Map<String, ReferenceSetMember> publishedDomains,
										   Map<String, ReferenceSetMember> actualDomains,
										   File reportDir, String domainTempalteFieldName) throws IOException {
		List<String> matchedExactly = new ArrayList<>();
		List<String> matchedWhenSortingIgnored = new ArrayList<>();
		List<String> newMembers = new ArrayList<>();
		List<String> diffMembers = new ArrayList<>();
		Map<String, String> messagesMappedByMemberId = new HashMap<>();
		for (String memberId : actualDomains.keySet()) {
			if (!publishedDomains.keySet().contains(memberId)) {
				newMembers.add(memberId);
				continue;
			}
			String published = publishedDomains.get(memberId).getAdditionalField(domainTempalteFieldName);
			String actual = actualDomains.get(memberId).getAdditionalField(domainTempalteFieldName);
			if (!published.equals(actual)) {
				String diffMsg = hasDiff(published, actual, ignoreCardinality);
				if (!Strings.isNullOrEmpty(diffMsg)) {
					diffMembers.add(memberId);
					outputBeforeAndAfter(published, actual);
					StringBuilder msgBuilder = new StringBuilder();
					msgBuilder.append("Domain id " + publishedDomains.get(memberId).getReferencedComponentId());
					msgBuilder.append("\n");
					msgBuilder.append("Member id:" + memberId);
					msgBuilder.append("\n");
					msgBuilder.append(diffMsg);
					messagesMappedByMemberId.put(memberId, msgBuilder.toString());
				} else {
					matchedWhenSortingIgnored.add(memberId);
				}
			} else {
				matchedExactly.add(memberId);
			}
		}
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(reportDir, "MRCM" + domainTempalteFieldName + "Diff.txt")))) {
			writer.append("Summary for " + domainTempalteFieldName);
			writer.newLine();
			writer.append("Total templates updated = " + actualDomains.keySet().size());
			writer.newLine();
			writer.append("Total new members = " + newMembers.size());
			writer.newLine();
			writer.append("Total templates are the same without change = " + matchedExactly.size());
			writer.newLine();
			writer.append("Total templates are the same when cardinality and sorting is ignored = " + matchedWhenSortingIgnored.size());
			writer.newLine();
			writer.append("Total templates found with diffs = " + diffMembers.size());
			writer.newLine();
			for (String memberId : messagesMappedByMemberId.keySet()) {
				writer.append(messagesMappedByMemberId.get(memberId));
				writer.newLine();
			}
		}

	}

	public static String hasDiff(String published, String actual, boolean ignoreCardinality) {
		boolean hasDiff = false;
		List<String> publishedSorted = split(published, ignoreCardinality);
		List<String> actualSorted = split(actual, ignoreCardinality);
		StringBuilder msgBuilder = new StringBuilder();;
		msgBuilder.append("Token is in the published version but is missing in the newly generated:\n");
		for (String token : publishedSorted) {
			if (!actualSorted.contains(token)) {
				msgBuilder.append(token);
				msgBuilder.append("\n");
				hasDiff = true;
			}
		}
		if (msgBuilder != null) {
			msgBuilder.append("\n");
			msgBuilder.append("Token is in the newly generated but is missing from the published:\n");
		}
		for (String token : actualSorted) {
			if (!publishedSorted.contains(token)) {
				msgBuilder.append(token);
				msgBuilder.append("\n");
				hasDiff = true;
			}
		}
		if (hasDiff) {
			return msgBuilder.toString();
		}
		return null;
	}

	private static List<String> split(String expression, boolean ignoreCardinality) {
		List<String> result = new ArrayList<>();
		for (String part : expression.split(",", -1)) {
			if (part.contains(":")) {
				result.addAll(Arrays.asList(part.split(":", -1)));
			} else {
				result.add(part.trim());
			}
		}
		if (ignoreCardinality) {
			List<String> updated = new ArrayList<>();
			for (String token : result) {
				if (token.contains("..")) {
					if (token.endsWith("}")) {
						token = token.replace("}", "");
					}
					updated.add(token.substring(token.lastIndexOf("..") + 5, token.length()).trim());
				}
			}
			result = updated;
		}
		Collections.sort(result);
		return result;
	}

	public static String sortExpressionConstraintByConceptId(String rangeConstraint, String memberId) {
		if (rangeConstraint == null || rangeConstraint.trim().isEmpty()) {
			return rangeConstraint;
		}
		ExpressionConstraint constraint = null;
		try {
			constraint = eclQueryBuilder.createQuery(rangeConstraint);
		} catch(ECLException e) {
			throw new IllegalStateException(String.format("Invalid range constraint %s found in member %s.", rangeConstraint, memberId));
		}

		if (constraint instanceof CompoundExpressionConstraint) {
			StringBuilder expressionBuilder = new StringBuilder();
			CompoundExpressionConstraint compound = (CompoundExpressionConstraint) constraint;
			if (compound.getConjunctionExpressionConstraints() != null) {
				List<SubExpressionConstraint> conJunctions = compound.getConjunctionExpressionConstraints();
				Collections.sort(conJunctions, EXPRESSION_CONSTRAINT_COMPARATOR_BY_CONCEPT_ID);
				for (int i = 0; i < conJunctions.size(); i++) {
					if (i > 0) {
						expressionBuilder.append( " AND ");
					}
					expressionBuilder.append(constructExpression(conJunctions.get(i)));
				}
			}
			if (compound.getDisjunctionExpressionConstraints() != null) {
				List<SubExpressionConstraint> disJunctions = compound.getDisjunctionExpressionConstraints();
				Collections.sort(disJunctions, EXPRESSION_CONSTRAINT_COMPARATOR_BY_CONCEPT_ID);
				for (int i = 0; i < disJunctions.size(); i++) {
					if (i > 0) {
						expressionBuilder.append( " OR ");
					}
					expressionBuilder.append(constructExpression(disJunctions.get(i)));
				}
			}

			if (compound.getExclusionExpressionConstraint() != null) {
				expressionBuilder.append(" MINUS ");
				expressionBuilder.append(constructExpression(compound.getExclusionExpressionConstraint()));
			}
			return expressionBuilder.toString();
		} else {
			return rangeConstraint;
		}
	}

	public static String constructExpression(SubExpressionConstraint constraint) {
		StringBuilder expressionBuilder = new StringBuilder();
		if (constraint.getOperator() != null) {
			expressionBuilder.append(constraint.getOperator().getText());
			expressionBuilder.append(" ");
		}
		expressionBuilder.append(constraint.getConceptId());
		expressionBuilder.append(" ");
		expressionBuilder.append("|");
		expressionBuilder.append(constraint.getTerm());
		expressionBuilder.append("|");
		return expressionBuilder.toString();
	}

	private static class MRCMRefsetComponentsLoader  extends ImpotentComponentFactory {
		List<ReferenceSetMember> mrcmRefsetMembers = new ArrayList<>();
		public void newReferenceSetMemberState(String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
			if (!Concepts.MRCM_INTERNATIONAL_REFSETS.contains(refsetId)) {
				return;
			}
			ReferenceSetMember member = new ReferenceSetMember();
			member.setMemberId(id);
			if (effectiveTime != null && !effectiveTime.isEmpty()) {
				member.setEffectiveTimeI(Integer.valueOf(effectiveTime));
			}
			member.setActive("1".equals(active));
			member.setModuleId(moduleId);
			member.setRefsetId(refsetId);
			member.setReferencedComponentId(referencedComponentId);

			if (fieldNames.length > 6) {
				for (int i = 6; i < fieldNames.length-1; i++) {
					member.setAdditionalField(fieldNames[i], otherValues[i-6]);
				}
			}
			mrcmRefsetMembers.add(member);
		}
		List<ReferenceSetMember> getAllMembers() {
			return this.mrcmRefsetMembers;
		}
	}
}
