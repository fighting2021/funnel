package core;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.CacheTool;
import tools.JsonTool;

import com.fasterxml.jackson.core.type.TypeReference;

import entity.CacheAware;
import entity.KeyAware;


public abstract class Funnel<T, R> {
	private static final Logger LOGGER = LoggerFactory.getLogger(Funnel.class);
	private R res;
	private Function<T, KeyAware> keyAwareFunc;
	private Function<CacheAware, R> cacheRecoverFunc;
	private Predicate<R> predicate;
	private Function<T, R> querySyncFunc;
	private BiFunction<T, R, CacheAware> cacheAwarefunc;
	// 缓存过期时间，以秒为单位，默认30分钟
    private int expire = 1800;
	// 使用funnel组件的服务名称
    private String serviceName = "";
	// 运行相关的指标，QRY_CACHE代表查询缓存次数，CACHE_EXISTS代表命中缓存次数，QRY_DB代表查询库的次数
    private static final int QRY_CACHE = 0, CACHE_EXISTS = 1, QRY_DB = 2;
    // 计数器，key代表使用funnel组件的服务名称，value数组代表该服务对应指标的数据
    private static Map<String, int[]> counter = new HashMap<String, int[]>();
    // 没有命中缓存的阈值
    private static final int QRY_DB_THRESHOLD = 1000;
    
    // 打印缓存的状态信息
    private void logCache(int step) {
        int[] operIndex;
        if (counter.containsKey(serviceName)) {
            operIndex = counter.get(serviceName);
        } else {
            operIndex = new int[3];
            counter.put(serviceName, operIndex);
        }
        if(step > -1 && step < 3){
            operIndex[step]++;
        }
        if (operIndex[QRY_DB] > QRY_DB_THRESHOLD) {
            LOGGER.info("[Funnel]funnelName: {}, qryCache:{}, cacheExists:{}, qryDb:{}",
            		serviceName, operIndex[QRY_CACHE], operIndex[CACHE_EXISTS], operIndex[QRY_DB]);
            resetCounter();
        }
    }
    
    // 重置counter数组的每个元素
    private void resetCounter() {
        int[] operIndex = counter.get(serviceName);
        for(int i = 0; i < operIndex.length; i++){
            operIndex[i] = 0 ;
        }
    }
	
    public Funnel<T, R> bind(Function<T, R> querySyncFunc, Predicate<R> predicate) {
        this.querySyncFunc = querySyncFunc;
        this.predicate = predicate;
        return this;
    }
    
    public Funnel<T, R> useCache(Function<T, KeyAware> keyAwareFunc) {
        this.keyAwareFunc = keyAwareFunc;
        return this;
    }
    
    public Funnel<T, R> cacheResult(BiFunction<T, R, CacheAware> cacheAwarefunc) {
        this.cacheAwarefunc = cacheAwarefunc;
        return this;
    }
    
    public Funnel<T, R> configFunnel(String serviceName, int expire){
        this.serviceName = serviceName;
        this.expire = expire;
        return this;
    }
	
	public abstract void build();
	
	public abstract CacheTool getCacheTool();
	
	public Funnel() {
		build();
	}
	
	public R get(String key, T req, Class<R> type) {
        R res = parseCache2Obj(queryFromCache(key), type);
        if (res != null) {
            logCache(CACHE_EXISTS);
            return res;
        }
        return queryFromDB(key, req);
    }
	
	public R get(String key, T req, TypeReference<R> type) {
        R res = parseCache2Obj(queryFromCache(key), type);
        if (res != null) {
            logCache(CACHE_EXISTS);
            return res;
        }
        return queryFromDB(key, req);
    }
	
	private String queryFromCache(String key) {
        logCache(QRY_CACHE);
        return getCacheTool().get(key).orElse(null);
    }
	
	private R parseCache2Obj(String value, Class<R> type) {
        if (isNotEmpty(value)) {
            try {
                return JsonTool.fromJson(value, type);
            } catch (IOException e) {
                LOGGER.error("[Funnel]parseCache2Obj error: ", e);
            }
        }
        return null;
    }
	
	private R parseCache2Obj(String value, TypeReference<R> type) {
        if (isNotEmpty(value)) {
            try {
                return JsonTool.fromJson(value, type);
            } catch (IOException e) {
                LOGGER.error("[Funnel]parseCache2Obj error: ", e);
            }
        }
        return null;
    }
	
	private R queryFromDB(String key, T req) {
        logCache(QRY_DB);
        if (querySyncFunc == null) {
            LOGGER.info("[Funnel]querySyncFunc is null！");
            return null;
        }
        R res = querySyncFunc.apply(req);
        String jsonValue = JsonTool.toJsonQuietly(res);
        getCacheTool().save(key, jsonValue, expire);
        LOGGER.info("[Funnel]write cache, serviceName:{}, key:{}, req:{}, res:{}",
        		serviceName, key, fmt(JsonTool.toJsonQuietly(req)), fmt(jsonValue));
        return res;
    }
	
	public R getWithCache(T req, Function<CacheAware, R> cacheRecoverFunc) {
		this.cacheRecoverFunc = cacheRecoverFunc;
		return get(req);
	}
	
	private R get(T req) {
		if (req == null) {
			return null;
		}
		CacheAware cacheAware = null;
		if (keyAwareFunc != null) {
			logCache(QRY_CACHE);
			KeyAware keyAware = keyAwareFunc.apply(req);
			cacheAware = queryFromCache(keyAware);
		}
		if (cacheAware != null && cacheRecoverFunc != null) {
			res = cacheRecoverFunc.apply(cacheAware);
		}
		if (predicate != null && predicate.test(res)) {
			logCache(CACHE_EXISTS);
			return res;
		}
		if (querySyncFunc != null) {
			logCache(QRY_DB);
			res = querySyncFunc.apply(req);
			if (predicate.test(res) && cacheAwarefunc != null) {
				cacheAware = cacheAwarefunc.apply(req, res);
				cache(cacheAware);
			}
		}
		return predicate != null && predicate.test(res) ? res : null;
	}

	private CacheAware queryFromCache(KeyAware keyAware) {
		if (isEmpty(keyAware) || isEmpty(keyAware.getKeyName()) || isEmpty(keyAware.getFields())) {
			return null;
		}
		List<String> mvalues = getCacheTool().hmget(keyAware.getKeyName(), keyAware.getFields())
				.filter(c -> isNotEmpty(c)).orElse(Collections.EMPTY_LIST);
		return keyAware.getFields().size() != mvalues.size() ? null : new CacheAware(keyAware, mvalues);
	}
	
	private void cache(CacheAware cacheAware) {
		if (cacheAware != null && cacheAware.getKeyAware() != null 
				&& cacheAware.getKeyAware().getFields() != null
				&& cacheAware.getValues() != null) {
			KeyAware keyAware = cacheAware.getKeyAware();
			Map<String, String> mvalues = new HashMap<String, String>();
			List<String> fields = keyAware.getFields();
			for (int i = 0; i < fields.size(); i++) {
				String field = fields.get(i);
				String value = cacheAware.getValues().get(i);
				mvalues.put(field, value);
			}
			if (isNotEmpty(keyAware.getKeyName()) && isNotEmpty(mvalues)) {
				getCacheTool().hmset(keyAware.getKeyName(), mvalues, expire);
			}
		}
	}
	
	// 根据参数类型判断是否为null或空
	private static boolean isEmpty(Object o) {
		if (o == null) {
			return true;
		} else if (o instanceof String) {
			String value = (String) o;
			return value.trim().length() == 0;
		} else if (o instanceof Collection) {
			Collection c = (Collection) o;
			return c.isEmpty();
		} else if (o instanceof Map) {
			Map map = (Map) o;
			return map.isEmpty();
		} else if (o.getClass().isArray()) {
			Object[] arr = (Object[]) o;
			return arr.length == 0;
		}
		return false;
	}
	
	private static boolean isNotEmpty(Object o) {
		return !isEmpty(o);
	}
	
	// 限制输出日志的长度，最大不能超过1000字符，如果超过，则显示IgnoreBigStr
	public static String fmt(String info){
        String res = "IgnoreBigStr";
        return info != null && info.length() > 1000 ? res : info;
    }
	
}