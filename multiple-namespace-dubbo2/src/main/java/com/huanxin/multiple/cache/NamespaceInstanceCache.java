package com.huanxin.multiple.cache;

import com.huanxin.multiple.model.AggregatedInstance;

import java.util.List;

public interface NamespaceInstanceCache {

    void put(String serviceName, String namespace, List<AggregatedInstance> instances);

    List<AggregatedInstance> get(String serviceName, String namespace);

    void evict(String serviceName, String namespace);

    void clearService(String serviceName);

    void clear();
}
