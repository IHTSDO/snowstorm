package org.snomed.snowstorm.syndication.models.integration;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.List;
import java.util.Objects;

@XmlRootElement(name = "entry")
public class SyndicationFeedEntry {

	private String title;
	private String contentItemIdentifier;
	private String contentItemVersion;
	private SyndicationCategory category;
	private List<SyndicationLink> links;
	private SyndicationDependency packageDependency;

	public SyndicationLink getZipLink() {
		if (links != null) {
			for (SyndicationLink link : links) {
				if ("application/zip".equals(link.getType())) {
					return link;
				}
			}
		}
		return null;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	@XmlElement(namespace = "http://ns.electronichealth.net.au/ncts/syndication/asf/extensions/1.0.0", name = "contentItemIdentifier")
	public String getContentItemIdentifier() {
		return contentItemIdentifier;
	}

	public void setContentItemIdentifier(String contentItemIdentifier) {
		this.contentItemIdentifier = contentItemIdentifier;
	}

	@XmlElement(namespace = "http://ns.electronichealth.net.au/ncts/syndication/asf/extensions/1.0.0", name = "contentItemVersion")
	public String getContentItemVersion() {
		return contentItemVersion;
	}

	public void setContentItemVersion(String contentItemVersion) {
		this.contentItemVersion = contentItemVersion;
	}

	public SyndicationCategory getCategory() {
		return category;
	}

	public void setCategory(SyndicationCategory category) {
		this.category = category;
	}

	@XmlElement(name="link", type = SyndicationLink.class)
	public List<SyndicationLink> getLinks() {
		return links;
	}

	public void setLinks(List<SyndicationLink> links) {
		this.links = links;
	}

	@XmlElement(namespace = "http://snomed.info/syndication/sct-extension/1.0.0", name = "packageDependency")
	public SyndicationDependency getPackageDependency() {
		return packageDependency;
	}

	public void setPackageDependency(SyndicationDependency packageDependency) {
		this.packageDependency = packageDependency;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SyndicationFeedEntry entry = (SyndicationFeedEntry) o;
		return Objects.equals(title, entry.title) && Objects.equals(contentItemIdentifier, entry.contentItemIdentifier) && Objects.equals(contentItemVersion, entry.contentItemVersion) && Objects.equals(category, entry.category);
	}

	@Override
	public int hashCode() {
		return Objects.hash(title, contentItemIdentifier, contentItemVersion, category);
	}
}
