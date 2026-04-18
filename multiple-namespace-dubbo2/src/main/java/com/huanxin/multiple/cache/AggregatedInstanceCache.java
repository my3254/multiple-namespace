package com.huanxin.multiple.cache;

import com.huanxin.multiple.model.AggregatedInstance;

import java.util.List;

public interface AggregatedInstanceCache {

    void put(String cacheKey, List<AggregatedInstance> instances);

    List<AggregatedInstance> get(String cacheKey);

    void evict(String cacheKey);

    void clear();
}
