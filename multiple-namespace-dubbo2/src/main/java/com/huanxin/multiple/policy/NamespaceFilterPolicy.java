package com.huanxin.multiple.policy;

import com.huanxin.multiple.config.MultiNamespaceRegistryConfig;

import java.util.ArrayList;
import java.util.List;

public class NamespaceFilterPolicy {

    public List<String> fallbackNamespaces(MultiNamespaceRegistryConfig config) {
        List<String> result = new ArrayList<String>();
        for (String namespace : config.getSubscribeNamespaces()) {
            if (namespace.equals(config.getPrimaryNamespace())) {
                continue;
            }
            if (!config.getForbiddenFallbackNamespaces().isEmpty()
                && config.getForbiddenFallbackNamespaces().contains(namespace)) {
                continue;
            }
            if (!config.getAllowedFallbackNamespaces().isEmpty()
                && !config.getAllowedFallbackNamespaces().contains(namespace)) {
                continue;
            }
            result.add(namespace);
        }
        return result;
    }
}
