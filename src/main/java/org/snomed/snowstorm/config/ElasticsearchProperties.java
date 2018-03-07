package org.snomed.snowstorm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("elasticsearch")
public class ElasticsearchProperties {

	private String[] urls;

	public String[] getUrls() {
		return urls;
	}

	public void setUrls(String[] urls) {
		this.urls = urls;
	}
}
