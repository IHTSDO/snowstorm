package com.kaicube.elasticversioncontrol.api;

import com.kaicube.elasticversioncontrol.domain.Commit;
import com.kaicube.elasticversioncontrol.domain.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.repository.ElasticsearchCrudRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ComponentService {

	@Autowired
	private VersionControlHelper versionControlHelper;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public <C extends Component> Iterable<C> doSaveBatchComponents(Collection<C> components, Commit commit, Class<C> clazz, String idField, ElasticsearchCrudRepository<C, String> repository) {
		final List<C> changedComponents = components.stream().filter(component -> component.isChanged() || component.isDeleted()).collect(Collectors.toList());
		if (!changedComponents.isEmpty()) {
			logger.info("Saving batch of {} {}", changedComponents.size(), clazz.getSimpleName());
			final List<String> ids = changedComponents.stream().map(Component::getId).collect(Collectors.toList());
			versionControlHelper.endOldVersions(commit, idField, clazz, ids, repository);
			versionControlHelper.removeDeleted(changedComponents);
			versionControlHelper.removeDeleted(components);
			if (!changedComponents.isEmpty()) {
				versionControlHelper.setEntityMeta(changedComponents, commit);
				repository.save(changedComponents);
			}
		}
		return components;
	}

}
