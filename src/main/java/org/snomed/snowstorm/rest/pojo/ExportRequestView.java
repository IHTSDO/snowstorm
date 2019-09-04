package org.snomed.snowstorm.rest.pojo;

import io.swagger.annotations.ApiModelProperty;
import org.snomed.snowstorm.core.data.domain.jobs.ExportConfiguration;

import java.util.Date;

public class ExportRequestView extends ExportConfiguration {

	@Override
	@ApiModelProperty(hidden = true)
	public String getId() {
		return super.getId();
	}

	@Override
	@ApiModelProperty(hidden = true)
	public Date getStartDate() {
		return super.getStartDate();
	}
}
