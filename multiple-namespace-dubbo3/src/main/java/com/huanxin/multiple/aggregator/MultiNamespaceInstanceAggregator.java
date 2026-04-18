package com.huanxin.multiple.aggregator;

import com.huanxin.multiple.model.AggregatedInstance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class MultiNamespaceInstanceAggregator {

    private final InstanceDedupKeyBuilder dedupKeyBuilder;

    public MultiNamespaceInstanceAggregator(InstanceDedupKeyBuilder dedupKeyBuilder) {
        this.dedupKeyBuilder = dedupKeyBuilder;
    }

    public List<AggregatedInstance> aggregate(List<AggregatedInstance> instances, String primaryNamespace) {
        Map<String, AggregatedInstance> deduped = new LinkedHashMap<String, AggregatedInstance>();
        for (AggregatedInstance current : instances) {
            String key = dedupKeyBuilder.build(current);
            AggregatedInstance existing = deduped.get(key);
            if (existing == null) {
                deduped.put(key, copy(current));
                continue;
            }
            merge(existing, current, primaryNamespace);
        }
        return new ArrayList<AggregatedInstance>(deduped.values());
    }

    private void merge(AggregatedInstance existing, AggregatedInstance current, String primaryNamespace) {
        LinkedHashSet<String> namespaces = new LinkedHashSet<String>(existing.getAvailableNamespaces());
        namespaces.addAll(current.getAvailableNamespaces());
        existing.setAvailableNamespaces(new ArrayList<String>(namespaces));

        if (primaryNamespace != null && primaryNamespace.equals(current.getChosenNamespace())) {
            existing.setChosenNamespace(current.getChosenNamespace());
            existing.setHealthy(current.isHealthy());
            existing.setWeight(current.getWeight());
            existing.setMetadata(new LinkedHashMap<String, String>(current.getMetadata()));
        }
    }

    private AggregatedInstance copy(AggregatedInstance source) {
        AggregatedInstance target = new AggregatedInstance();
        target.setServiceName(source.getServiceName());
        target.setIp(source.getIp());
        target.setPort(source.getPort());
        target.setHealthy(source.isHealthy());
        target.setWeight(source.getWeight());
        target.setChosenNamespace(source.getChosenNamespace());
        target.setAvailableNamespaces(new ArrayList<String>(source.getAvailableNamespaces()));
        target.setMetadata(new LinkedHashMap<String, String>(source.getMetadata()));
        return target;
    }
}
