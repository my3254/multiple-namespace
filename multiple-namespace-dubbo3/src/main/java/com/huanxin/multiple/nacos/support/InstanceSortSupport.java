package com.huanxin.multiple.nacos.support;

import com.huanxin.multiple.model.AggregatedInstance;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class InstanceSortSupport {

    public void sort(List<AggregatedInstance> instances, final String primaryNamespace) {
        Collections.sort(instances, new Comparator<AggregatedInstance>() {
            @Override
            public int compare(AggregatedInstance left, AggregatedInstance right) {
                boolean leftPrimary = primaryNamespace != null && primaryNamespace.equals(left.getChosenNamespace());
                boolean rightPrimary = primaryNamespace != null && primaryNamespace.equals(right.getChosenNamespace());
                if (leftPrimary != rightPrimary) {
                    return leftPrimary ? -1 : 1;
                }
                int weightCompare = Double.compare(right.getWeight(), left.getWeight());
                if (weightCompare != 0) {
                    return weightCompare;
                }
                int hostCompare = safe(left.getIp()).compareTo(safe(right.getIp()));
                if (hostCompare != 0) {
                    return hostCompare;
                }
                return Integer.compare(left.getPort(), right.getPort());
            }
        });
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
