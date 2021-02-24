package entity;

import java.util.List;

public class KeyAware {
	private String keyName;
	private List<String> fields;
	
	public KeyAware(String keyName, List<String> fields) {
		this.keyName = keyName;
		this.fields = fields;
	}

	public String getKeyName() {
		return keyName;
	}

	public void setKeyName(String keyName) {
		this.keyName = keyName;
	}

	public List<String> getFields() {
		return fields;
	}

	public void setFields(List<String> fields) {
		this.fields = fields;
	}

}
