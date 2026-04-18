package com.huanxin.multiple.policy;

import com.huanxin.multiple.model.AggregatedInstance;

import java.util.Map;
import java.util.Objects;

public class InstanceMetadataMatchPolicy {

    public boolean match(AggregatedInstance instance, Map<String, String> requiredMetadata) {
        if (requiredMetadata == null || requiredMetadata.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, String> entry : requiredMetadata.entrySet()) {
            if (!Objects.equals(instance.getMetadata().get(entry.getKey()), entry.getValue())) {
                return false;
            }
        }
        return true;
    }
}
