package com.huanxin.multiple.nacos.converter;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.huanxin.multiple.model.AggregatedInstance;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class NacosInstanceToAggregatedInstanceConverter {

    public AggregatedInstance convert(String namespace, String serviceName, Instance instance) {
        AggregatedInstance aggregatedInstance = new AggregatedInstance();
        aggregatedInstance.setServiceName(serviceName);
        aggregatedInstance.setIp(instance.getIp());
        aggregatedInstance.setPort(instance.getPort());
        aggregatedInstance.setHealthy(instance.isHealthy());
        aggregatedInstance.setWeight(instance.getWeight());
        aggregatedInstance.setChosenNamespace(namespace);
        aggregatedInstance.setAvailableNamespaces(new ArrayList<String>());
        aggregatedInstance.getAvailableNamespaces().add(namespace);
        aggregatedInstance.setMetadata(new LinkedHashMap<String, String>());
        if (instance.getMetadata() != null) {
            aggregatedInstance.getMetadata().putAll(instance.getMetadata());
        }
        return aggregatedInstance;
    }
}
