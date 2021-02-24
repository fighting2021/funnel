package tools;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class CacheTool {
	
	public abstract void save(String key, String value, int expire);
	
	public abstract Optional<String> get(String key);

	public abstract void hmset(String key, Map<String, String> value, int expire);
	
	public abstract Optional<List<String>> hmget(String key, List<String> fields);
	
}
