package com.kaicube.elasticversioncontrol.api;

import com.kaicube.elasticversioncontrol.domain.Commit;
import com.kaicube.elasticversioncontrol.domain.DomainEntity;
import net.jodah.typetools.TypeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ComponentService {

	@Autowired
	private VersionControlHelper versionControlHelper;

	public static final PageRequest LARGE_PAGE = new PageRequest(0, 10000);

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@SuppressWarnings("unchecked")
	public <C extends DomainEntity> Iterable<C> doSaveBatchComponents(Collection<C> components, Commit commit, String idField, ElasticsearchCrudRepository<C, String> repository) {
		final Class<?>[] classes = TypeResolver.resolveRawArguments(ElasticsearchCrudRepository.class, repository.getClass());
		Class<C> componentClass = (Class<C>) classes[0];
		final List<C> changedComponents = getChangedComponents(components);
		if (!changedComponents.isEmpty()) {
			logger.info("Saving batch of {} {}", changedComponents.size(), componentClass.getSimpleName());
			final List<String> ids = changedComponents.stream().map(DomainEntity::getId).collect(Collectors.toList());
			versionControlHelper.endOldVersions(commit, idField, componentClass, ids, repository);
			versionControlHelper.removeDeleted(changedComponents);
			versionControlHelper.removeDeleted(components);
			if (!changedComponents.isEmpty()) {
				versionControlHelper.setEntityMeta(changedComponents, commit);
				repository.save(changedComponents);
			}
		}
		return components;
	}

	protected <C extends DomainEntity> List<C> getChangedComponents(Collection<C> components) {
		return components.stream().filter(component -> component.isChanged() || component.isDeleted()).collect(Collectors.toList());
	}

}
