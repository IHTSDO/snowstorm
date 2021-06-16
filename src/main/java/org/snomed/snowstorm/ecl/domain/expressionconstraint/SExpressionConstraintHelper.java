package org.snomed.snowstorm.ecl.domain.expressionconstraint;

import io.kaicode.elasticvc.api.BranchCriteria;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.snomed.langauges.ecl.domain.filter.*;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.services.DescriptionService;
import org.snomed.snowstorm.core.data.services.DialectConfigurationService;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.data.services.pojo.DescriptionCriteria;
import org.snomed.snowstorm.core.util.SearchAfterPage;
import org.snomed.snowstorm.ecl.ConceptSelectorHelper;
import org.snomed.snowstorm.ecl.domain.RefinementBuilder;
import org.snomed.snowstorm.ecl.domain.RefinementBuilderImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.*;
import java.util.stream.Collectors;

public class SExpressionConstraintHelper {

	// Used to force no match
	public static final String MISSING = "missing";
	public static final Long MISSING_LONG = 111L;

	protected static Optional<Page<Long>> select(SExpressionConstraint sExpressionConstraint, String path, BranchCriteria branchCriteria, boolean stated,
			Collection<Long> conceptIdFilter, PageRequest pageRequest, QueryService queryService) {

		// perform ecl description filters if present
		if (sExpressionConstraint instanceof  SSubExpressionConstraint) {
			if (!((SSubExpressionConstraint) sExpressionConstraint).getFilterConstraints().isEmpty()) {
				List<Long> filterResults = select((SSubExpressionConstraint)sExpressionConstraint, path, branchCriteria, pageRequest, queryService);
				if (conceptIdFilter != null) {
					conceptIdFilter.addAll(filterResults);
				} else {
					conceptIdFilter = filterResults;
				}
			}
		}

		BoolQueryBuilder query = ConceptSelectorHelper.getBranchAndStatedQuery(branchCriteria.getEntityBranchCriteria(QueryConcept.class), stated);
		RefinementBuilder refinementBuilder = new RefinementBuilderImpl(query, path, branchCriteria, stated, queryService);
		sExpressionConstraint.addCriteria(refinementBuilder);// This can add an inclusionFilter to the refinementBuilder.
		return Optional.of(ConceptSelectorHelper.fetchIds(query, conceptIdFilter, refinementBuilder.getInclusionFilter(), pageRequest, queryService));
	}

	protected static Optional<Page<Long>> select(SExpressionConstraint sExpressionConstraint, RefinementBuilder refinementBuilder) {
		return select(sExpressionConstraint, refinementBuilder.getPath(), refinementBuilder.getBranchCriteria(), refinementBuilder.isStated(), null, null, refinementBuilder.getQueryService());
	}

	protected static List<Long> select(SSubExpressionConstraint sSubExpressionConstraint, String path, BranchCriteria branchCriteria, PageRequest pageRequest, QueryService queryService) {
		List<FilterConstraint> filterConstraints = sSubExpressionConstraint.getFilterConstraints();
		if (filterConstraints.isEmpty()) {
			return Collections.emptyList();
		}
		List<Long> filteredConcepts = new ArrayList<>();
		filterConstraints.forEach(filterConstraint -> {
			Collection<Long> filterResults = applyFilterConstraint(queryService, path, pageRequest, filterConstraint);
			if (filteredConcepts.isEmpty()) {
				filteredConcepts.addAll(filterResults);
			} else {
				filteredConcepts.retainAll(filterResults);
			}
		});
		return filteredConcepts;
	}

	private static Collection<Long> applyFilterConstraint(QueryService queryService, String path, PageRequest pageRequest, FilterConstraint filterConstraint) {

		QueryService.ConceptQueryBuilder queryBuilder = queryService.createQueryBuilder(true).activeFilter(true);
		// description type filter
		filterConstraint.getDescriptionTypeFilters().forEach(descriptionTypeFilter -> {
			queryBuilder.descriptionCriteria(descriptionCriteria -> {
				descriptionCriteria.type(descriptionTypeFilter.getTypes().stream().map(DescriptionType::getTypeId).map(Long::valueOf).collect(Collectors.toList()));
			});
		});

		// language filter
		Set<String> languageCodes = new HashSet<>();
		filterConstraint.getLanguageFilters().forEach(languageFilter -> languageCodes.addAll(languageFilter.getLanguageCodes()));
		queryBuilder.descriptionCriteria(descriptionCriteria -> descriptionCriteria.searchLanguageCodes(languageCodes));

		// dialect filter
		applyDialectFilters(filterConstraint, queryBuilder);

		// term filters
		Set<Long> filterResults = new HashSet<>();
		if (!filterConstraint.getTermFilters().isEmpty()) {
			filterConstraint.getTermFilters().stream().forEach( termFilter -> {
				termFilter.getTypedSearchTerms().forEach(typedSearchTerm -> {
					queryBuilder.descriptionCriteria(descriptionCriteria -> {
						descriptionCriteria.active(true)
								.term(TermFilter.getTerm(typedSearchTerm))
								.searchMode(resolveSearchMode(TermFilter.getSearchType(typedSearchTerm)));
					});
					// perform search
					SearchAfterPage<Long> queryResults = queryService.searchForIds(queryBuilder, path, pageRequest);
					filterResults.addAll(queryResults.getContent());
				});
			});
		} else {
			SearchAfterPage<Long> queryResults = queryService.searchForIds(queryBuilder, path, pageRequest);
			filterResults.addAll(queryResults.getContent());
		}
		return filterResults;
	}

	private static void applyDialectFilters(FilterConstraint filterConstraint, QueryService.ConceptQueryBuilder queryBuilder) {
		final Set<Long> preferredOrAcceptable = new HashSet<>();
		final Set<Long> preferred = new HashSet<>();
		final Set<Long> acceptable = new HashSet<>();
		List<DescriptionCriteria.DisjunctionAcceptabilityCriteria> disjunctionAcceptabilityList = new ArrayList<>();
		filterConstraint.getDialectFilters().forEach(dialectFilter -> {
			// process dialect set
			if (dialectFilter.getDialects().size() > 1) {
				final Set<Long> preferredOr = new HashSet<>();
				final Set<Long> acceptableOr = new HashSet<>();
				final Set<Long> disjunctionPreferredOrAcceptable = new HashSet<>();
				if (dialectFilter.getAcceptabilityMap().isEmpty()) {
					// preferred or acceptable
					dialectFilter.getDialects().forEach(dialect -> disjunctionPreferredOrAcceptable.add(getDialectId(dialect)));

				} else {
					for (Dialect dialect : dialectFilter.getDialects()) {
						if (dialectFilter.getAcceptabilityMap().get(dialect).size() > 1) {
							disjunctionPreferredOrAcceptable.add(getDialectId(dialect));
						} else if (dialectFilter.getAcceptabilityMap().get(dialect).contains(Acceptability.ACCEPTABLE)) {
							acceptableOr.add(getDialectId(dialect));
						} else if (dialectFilter.getAcceptabilityMap().get(dialect).contains(Acceptability.PREFERRED)) {
							preferredOr.add(getDialectId(dialect));
						}
					}
				}
				if (!preferredOr.isEmpty() || !acceptableOr.isEmpty() || !disjunctionPreferredOrAcceptable.isEmpty()) {
					disjunctionAcceptabilityList.add(new DescriptionCriteria.DisjunctionAcceptabilityCriteria(preferredOr, acceptableOr, disjunctionPreferredOrAcceptable));
				}
			} else {
				Dialect dialect = dialectFilter.getDialects().get(0);
				if (dialectFilter.getAcceptabilityMap().isEmpty()) {
					preferredOrAcceptable.add(getDialectId(dialect));
				} else {
					if (dialectFilter.getAcceptabilityMap().get(dialect).size() > 1) {
						preferredOrAcceptable.add(getDialectId(dialect));
					} else if (dialectFilter.getAcceptabilityMap().get(dialect).contains(Acceptability.ACCEPTABLE)) {
						acceptable.add(getDialectId(dialect));
					} else if (dialectFilter.getAcceptabilityMap().get(dialect).contains(Acceptability.PREFERRED)) {
						preferred.add(getDialectId(dialect));
					}
				}
			}
		});
		queryBuilder.descriptionCriteria(descriptionCriteria -> {
			descriptionCriteria.preferredOrAcceptableIn(preferredOrAcceptable);
			descriptionCriteria.preferredIn(preferred);
			descriptionCriteria.acceptableIn(acceptable);
			descriptionCriteria.disjunctionAcceptabilityCriteria(disjunctionAcceptabilityList);
		});
	}

	private static Long getDialectId(Dialect dialect) {
		Long dialectId = null;
		if (dialect.getAlias() != null) {
			dialectId = DialectConfigurationService.instance().findRefsetForDialect(dialect.getAlias());
			if (dialectId == null) {
				throw new IllegalArgumentException(String.format("Unknown dialect %s", dialect.getAlias()));
			}
		} else if (dialect.getDialectId() != null) {
			dialectId = Long.parseLong(dialect.getDialectId());
		}
		return dialectId;
	}

	private static DescriptionService.SearchMode resolveSearchMode(SearchType searchType) {
		if (SearchType.WILDCARD == searchType) {
			return DescriptionService.SearchMode.WILDCARD;
		} else {
			return DescriptionService.SearchMode.STANDARD;
		}
	}
}
