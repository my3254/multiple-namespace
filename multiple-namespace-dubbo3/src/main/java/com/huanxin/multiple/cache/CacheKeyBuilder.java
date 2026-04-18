package com.huanxin.multiple.cache;

import com.huanxin.multiple.config.MultiNamespaceRegistryConfig;

public class CacheKeyBuilder {

    public String build(String serviceName, String groupName, MultiNamespaceRegistryConfig config) {
        return groupName
            + "@@"
            + serviceName
            + "@@"
            + config.getPrimaryNamespace()
            + "@@"
            + config.getNamespaceSelectMode()
            + "@@"
            + config.getSubscribeNamespaces();
    }
}
