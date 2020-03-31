package org.snomed.snowstorm.util;

import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.factory.ComponentFactory;
import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.langauges.ecl.ECLException;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.langauges.ecl.domain.expressionconstraint.CompoundExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.snowstorm.core.data.domain.Concepts;
import org.snomed.snowstorm.core.data.domain.ReferenceSetMember;
import org.snomed.snowstorm.ecl.SECLObjectFactory;

import java.io.FileInputStream;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ExpressionsDiffUtil {

	private static Logger logger = LoggerFactory.getLogger(ExpressionsDiffUtil.class);

	public static ECLQueryBuilder eclQueryBuilder = new ECLQueryBuilder(new SECLObjectFactory());

	static final Comparator<SubExpressionConstraint> EXPRESSION_CONSTRAINT_COMPARATOR_BY_CONCEPT_ID = Comparator
			.comparing(SubExpressionConstraint::getConceptId, Comparator.nullsFirst(String::compareTo));

	private static boolean ignoreSorting = true;

	private static boolean ignoreCardinality = true;

	private static boolean outputBeforeAndAfter = false;

	private static boolean outputDifferences = true;

	public static void  main(String[] args) throws Exception {
		if (args == null || args.length !=2) {
			System.out.println("Please enter two versions of MRCM to compare");
		}
		String published = args[0];
		String actual = args[1];
		// load MRCM refsets
		LoadingProfile mrcmLoadingProfile = LoadingProfile.complete.withJustRefsets().withRefsets("723560006", "723562003");
		ComponentFactory publishedMRCMs = new MRCMRefsetComponentsLoader();
		ComponentFactory actualMRCMs = new MRCMRefsetComponentsLoader();
		if (published.endsWith(".zip") && actual.endsWith(".zip")) {
			new ReleaseImporter().loadDeltaReleaseFiles(new FileInputStream(published), mrcmLoadingProfile, publishedMRCMs);
			new ReleaseImporter().loadDeltaReleaseFiles(new FileInputStream(actual), mrcmLoadingProfile, actualMRCMs);
		}

		List<ReferenceSetMember> publishedMembers = ((MRCMRefsetComponentsLoader) publishedMRCMs).getAllMembers();

		List<ReferenceSetMember> actualMembers = ((MRCMRefsetComponentsLoader) actualMRCMs).getAllMembers();

		if (publishedMembers.size() != actualMembers.size()) {
			System.out.println(String.format("%d mrcm refset members found in %s", publishedMembers.size(), published));
			System.out.println(String.format("%d mrcm refset members found in %s", actualMembers.size(), actual));
		} else {
			System.out.println(String.format("%d mrcm refset members found in %s and %s", publishedMembers.size(), published, actual));
		}

		// diff after parsing attribute rule and range constraint
		diffAttributeRangeConctraintAndRules(publishedMembers, actualMembers);

		// diff domain templates
//		diffDomainTemplates(publishedMembers, actualMembers);
	}

	public static boolean diffTemplates(String published, String actual, boolean ignoreCardinality) {
		return hasDiff(published, actual, ignoreCardinality);
	}

	public static boolean diffExpressions(String memberId, String published, String actual, boolean ignoreCardinality) {
		String publishedSorted = sortExpressionConstraintByConceptId(published, memberId);
		if (publishedSorted.equals(actual)) {
			return false;
		}
		if (outputDifferences) {
			System.out.println("Published sorted vs Actual for member " + memberId);
			System.out.println(publishedSorted);
			System.out.println(actual);
		}
		return true;
	}

	private static void diffDomainTemplates(List<ReferenceSetMember> published, List<ReferenceSetMember> actual) {
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

		diffPrecoordinationTemplates(publishedDomainMapById, actualDomainMapById);
		diffPostcoordinationTemplates(publishedDomainMapById, actualDomainMapById);
	}

	private static void diffAttributeRangeConctraintAndRules(List<ReferenceSetMember> publishedRanges, List<ReferenceSetMember> actualRanges) {

		Map<String, ReferenceSetMember> actualRangeMapById = actualRanges.stream()
				.filter(ReferenceSetMember :: isActive)
				.filter(range -> "723562003".equals(range.getRefsetId()))
				.collect(Collectors.toMap(ReferenceSetMember :: getMemberId, Function.identity()));

		Map<String, ReferenceSetMember> publishedRangeMapById = publishedRanges.stream()
				.filter(ReferenceSetMember :: isActive)
				.filter(range ->  "723562003".equals(range.getRefsetId()))
				.collect(Collectors.toMap(ReferenceSetMember :: getMemberId, Function.identity()));

		diffRangeConstraints(actualRangeMapById, publishedRangeMapById);
		diffAttributeRules(actualRangeMapById, publishedRangeMapById);
	}

	private static void diffAttributeRules(Map<String,ReferenceSetMember> actualRangeMapById, Map<String,ReferenceSetMember> publishedRangeMapById) {

		List<String> matchedExactly = new ArrayList<>();
		List<String> matchedWhenIngoringCardinality = new ArrayList<>();
		List<String> newMembers = new ArrayList<>();
		List<String> diffMembers = new ArrayList<>();

		for (String memberId : actualRangeMapById.keySet()) {
			if (!publishedRangeMapById.keySet().contains(memberId)) {
				// new member
				newMembers.add(memberId);
				continue;
			}
			String publishedRule = publishedRangeMapById.get(memberId).getAdditionalField("attributeRule");
			String actualRule = actualRangeMapById.get(memberId).getAdditionalField("attributeRule");

			if (!actualRule.equals(publishedRule)) {
				if (diffExpressions(memberId, publishedRule, actualRule, true)) {
					outputBeforeAndAfter(publishedRule, actualRule);
					diffMembers.add(memberId);
				} else {
					matchedWhenIngoringCardinality.add(memberId);
				}
			} else {
				matchedExactly.add(memberId);
			}
		}

		System.out.println("Total attribute rules updated = " + actualRangeMapById.size());
		System.out.println("Total new components created = " + newMembers.size());
		System.out.println("Total attribute rules are the same without change = " + matchedExactly.size());
		System.out.println("Total attribute rules are the same when cardinality and sorting are ignored = " + matchedWhenIngoringCardinality.size());
		System.out.println("Total attribute rules found with diffs = " + diffMembers.size());
	}

	private static void diffRangeConstraints(Map<String,ReferenceSetMember> actualRangeMapById, Map<String,ReferenceSetMember> publishedRangeMapById) {

		List<String> matchedExactly = new ArrayList<>();
		List<String> matchedWhenIngoringCardinality = new ArrayList<>();
		List<String> newMembers = new ArrayList<>();
		List<String> diffMembers = new ArrayList<>();

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
				if (diffExpressions(memberId, publishedConstraint, actualConstraint, ignoreCardinality)) {
					outputBeforeAndAfter(publishedConstraint, actualConstraint);
					diffMembers.add(memberId);
				} else {
					matchedWhenIngoringCardinality.add(memberId);
				}
			}
		}
		System.out.println("Total range constraints updated = " + actualRangeMapById.keySet().size());
		System.out.println("Total new components created = " + newMembers.size());
		System.out.println("Total range constraints are the same without change = " + matchedExactly.size());
		System.out.println("Total range constraints are the same when cardinality and sorting are ignored = " + matchedWhenIngoringCardinality.size() );
		System.out.println("Total range constraints found with diffs = " + diffMembers.size());
	}


	private static void outputBeforeAndAfter(String published, String actual) {
		if (outputBeforeAndAfter) {
			System.out.println("before = " + published);
			System.out.println("after = " + actual);
		}
	}

	public static void diffPrecoordinationTemplates(Map<String, ReferenceSetMember> publishedDomains, Map<String, ReferenceSetMember> actualDomains) {
		int matched = 0;
		int diffOnlyInSorting = 0;
		int diffCounter = 0;
		List<String> newMembers = new ArrayList<>();
		for (String memberId : actualDomains.keySet()) {
			if (!publishedDomains.keySet().contains(memberId)) {
				newMembers.add(memberId);
				continue;
			}
			String published = publishedDomains.get(memberId).getAdditionalField("domainTemplateForPrecoordination");
			String actual = actualDomains.get(memberId).getAdditionalField("domainTemplateForPrecoordination");
			if (!published.equals(actual)) {
				System.out.println("Analyzing precoordinationdomain template for domain id " + publishedDomains.get(memberId).getReferencedComponentId());
				if (hasDiff(published, actual, true)) {
					diffCounter++;
					outputBeforeAndAfter(published, actual);
				} else {
					System.out.println("domain template is the same when cardinality and sorting are ignored " + memberId);
					diffOnlyInSorting++;
				}
			} else {
				matched++;
				System.out.println("domain template is the same for " + memberId);
			}
		}
		System.out.println("Total templates updated = " + actualDomains.keySet().size());
		System.out.println("Total new members = " + newMembers.size());
		System.out.println("Total templates are the same without change = " + matched);
		System.out.println("Total templates are the same when cardinality and sorting is ignored = " + diffOnlyInSorting);
		System.out.println("Total templates found with diffs = " + diffCounter);
	}


	public static void diffPostcoordinationTemplates(Map<String, ReferenceSetMember> publishedDomains, Map<String, ReferenceSetMember> actualDomains) {
		int sameCounter = 0;
		int sameWhenSortingIngored = 0;
		int diffCounter = 0;
		List<String> newMembers = new ArrayList<>();
		for (String memberId : actualDomains.keySet()) {
			if (!publishedDomains.keySet().contains(memberId)) {
				newMembers.add(memberId);
				continue;
			}
			String published = publishedDomains.get(memberId).getAdditionalField("domainTemplateForPostcoordination");
			String actual = actualDomains.get(memberId).getAdditionalField("domainTemplateForPostcoordination");
			if (!published.equals(actual)) {
				System.out.println("Analyzing postcoordinationdomain template for domain id " + publishedDomains.get(memberId).getReferencedComponentId());
				if (hasDiff(published, actual, true)) {
					diffCounter++;
					System.out.println("before = " + published);
					System.out.println("after = " + actual);
				} else {
					System.out.println("domain template is the same when cardinality and sorting is ignored " + memberId);
					sameWhenSortingIngored++;
				}
			} else {
				sameCounter++;
				System.out.println("domain template is the same for " + memberId);
			}
		}
		System.out.println("Total templates updated = " + actualDomains.keySet().size());
		System.out.println("Total new members = " + newMembers.size());
		System.out.println("Total templates are the same without change = " + sameCounter);
		System.out.println("Total templates are the same when cardinality and sorting are ignored = " + sameWhenSortingIngored);
		System.out.println("Total templates found with diffs = " + diffCounter);
	}


	public static boolean hasDiff(String published, String actual, boolean ignoreCardinality) {
		boolean hasDiff = false;
		List<String> publishedSorted = split(published, ignoreCardinality);
		List<String> actualSorted = split(actual, ignoreCardinality);

		StringBuilder msgBuilder = null;
		if (outputDifferences) {
			msgBuilder = new StringBuilder();
			msgBuilder.append("Token is in the published version but is missing in the new generated:");
		}
		for (String token : publishedSorted) {
			if (!actualSorted.contains(token)) {
				if (outputDifferences) {
					msgBuilder.append(token);
					msgBuilder.append("/n");
				}
				hasDiff = true;
			}
		}

		if (msgBuilder != null) {
			msgBuilder.append("/n");
			msgBuilder.append("Token is in the new generated but is missing from the published");
		}
		for (String token : actualSorted) {
			if (!publishedSorted.contains(token)) {
				if (outputDifferences) {
					msgBuilder.append(token);
					msgBuilder.append("/n");
				}
				hasDiff = true;
			}
		}
		return hasDiff;
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
