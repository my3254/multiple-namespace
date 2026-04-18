package com.huanxin.multiple.cache;

import com.huanxin.multiple.model.AggregatedInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryNamespaceInstanceCache implements NamespaceInstanceCache {

    private final Map<String, List<AggregatedInstance>> cache = new ConcurrentHashMap<String, List<AggregatedInstance>>();

    @Override
    public void put(String serviceName, String namespace, List<AggregatedInstance> instances) {
        cache.put(buildKey(serviceName, namespace), new ArrayList<AggregatedInstance>(instances));
    }

    @Override
    public List<AggregatedInstance> get(String serviceName, String namespace) {
        List<AggregatedInstance> instances = cache.get(buildKey(serviceName, namespace));
        if (instances == null) {
            return Collections.emptyList();
        }
        return new ArrayList<AggregatedInstance>(instances);
    }

    @Override
    public void evict(String serviceName, String namespace) {
        cache.remove(buildKey(serviceName, namespace));
    }

    @Override
    public void clearService(String serviceName) {
        for (String key : new ArrayList<String>(cache.keySet())) {
            if (key.startsWith(serviceName + "#")) {
                cache.remove(key);
            }
        }
    }

    @Override
    public void clear() {
        cache.clear();
    }

    private String buildKey(String serviceName, String namespace) {
        return serviceName + "#" + namespace;
    }
}
