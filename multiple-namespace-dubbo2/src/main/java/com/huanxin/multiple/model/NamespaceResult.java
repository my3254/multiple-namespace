package com.huanxin.multiple.model;

import java.util.Collections;
import java.util.List;

public class NamespaceResult {

    private final String namespace;
    private final List<AggregatedInstance> instances;
    private final Throwable error;

    private NamespaceResult(String namespace, List<AggregatedInstance> instances, Throwable error) {
        this.namespace = namespace;
        this.instances = instances;
        this.error = error;
    }

    public static NamespaceResult success(String namespace, List<AggregatedInstance> instances) {
        return new NamespaceResult(namespace, instances, null);
    }

    public static NamespaceResult failure(String namespace, Throwable error) {
        return new NamespaceResult(namespace, Collections.<AggregatedInstance>emptyList(), error);
    }

    public String getNamespace() {
        return namespace;
    }

    public List<AggregatedInstance> getInstances() {
        return instances;
    }

    public Throwable getError() {
        return error;
    }

    public boolean isSuccess() {
        return error == null;
    }
}
