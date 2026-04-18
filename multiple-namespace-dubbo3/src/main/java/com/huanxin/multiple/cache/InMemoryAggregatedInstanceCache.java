package com.huanxin.multiple.cache;

import com.huanxin.multiple.model.AggregatedInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAggregatedInstanceCache implements AggregatedInstanceCache {

    private final Map<String, List<AggregatedInstance>> cache = new ConcurrentHashMap<String, List<AggregatedInstance>>();

    @Override
    public void put(String cacheKey, List<AggregatedInstance> instances) {
        cache.put(cacheKey, new ArrayList<AggregatedInstance>(instances));
    }

    @Override
    public List<AggregatedInstance> get(String cacheKey) {
        List<AggregatedInstance> instances = cache.get(cacheKey);
        if (instances == null) {
            return Collections.emptyList();
        }
        return new ArrayList<AggregatedInstance>(instances);
    }

    @Override
    public void evict(String cacheKey) {
        cache.remove(cacheKey);
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
