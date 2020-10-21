package org.snomed.snowstorm.rest.pojo;

public class LocalFileImportCreationRequest extends ImportCreationRequest {

	private String filePath;

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}
}
