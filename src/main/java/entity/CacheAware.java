package entity;

import java.util.List;

public class CacheAware {
	private KeyAware keyAware;
	private List<String> values;
	
	public CacheAware(KeyAware keyAware, List<String> values) {
		this.keyAware = keyAware;
		this.values = values;
	}

	public KeyAware getKeyAware() {
		return keyAware;
	}
	
	public void setKeyAware(KeyAware keyAware) {
		this.keyAware = keyAware;
	}

	public List<String> getValues() {
		return values;
	}

	public void setValues(List<String> values) {
		this.values = values;
	}

}
