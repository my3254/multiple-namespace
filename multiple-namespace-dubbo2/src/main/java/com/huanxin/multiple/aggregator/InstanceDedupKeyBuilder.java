package com.huanxin.multiple.aggregator;

import com.huanxin.multiple.model.AggregatedInstance;

import java.util.Map;

public class InstanceDedupKeyBuilder {

    public String build(AggregatedInstance instance) {
        Map<String, String> metadata = instance.getMetadata();
        if (metadata != null) {
            String uniqueKey = metadata.get("instanceUniqueKey");
            if (uniqueKey != null && !uniqueKey.trim().isEmpty()) {
                return uniqueKey;
            }
        }
        String serviceName = instance.getServiceName() == null ? "" : instance.getServiceName();
        return serviceName + "#" + instance.getIp() + ":" + instance.getPort();
    }
}
