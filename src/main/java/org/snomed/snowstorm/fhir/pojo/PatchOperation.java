package org.snomed.snowstorm.fhir.pojo;

public class PatchOperation {

	private String op;
	private String path;
	private PatchCode value;

	public String getOp() {
		return op;
	}

	public void setOp(String op) {
		this.op = op;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public PatchCode getValue() {
		return value;
	}

	public void setValue(PatchCode value) {
		this.value = value;
	}
}
