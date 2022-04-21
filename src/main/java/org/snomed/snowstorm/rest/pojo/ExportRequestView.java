package org.snomed.snowstorm.rest.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import org.snomed.snowstorm.core.data.domain.jobs.ExportConfiguration;

import java.util.Date;

import static io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY;

public class ExportRequestView extends ExportConfiguration {

	@Override
	@Schema(accessMode = READ_ONLY)
	public String getId() {
		return super.getId();
	}

	@Override
	@Schema(accessMode = READ_ONLY)
	public Date getStartDate() {
		return super.getStartDate();
	}
}
