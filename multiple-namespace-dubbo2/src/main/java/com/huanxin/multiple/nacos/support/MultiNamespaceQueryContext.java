package com.huanxin.multiple.nacos.support;

import com.huanxin.multiple.aggregator.MultiNamespaceInstanceAggregator;
import com.huanxin.multiple.config.MultiNamespaceRegistryConfig;
import com.huanxin.multiple.policy.InstanceMetadataMatchPolicy;
import com.huanxin.multiple.policy.NamespaceFilterPolicy;

public class MultiNamespaceQueryContext {

    private final MultiNamespaceRegistryConfig config;
    private final NacosInstanceQueryService instanceQueryService;
    private final FallbackNamespaceQueryService fallbackNamespaceQueryService;
    private final NamespaceFilterPolicy namespaceFilterPolicy;
    private final InstanceMetadataMatchPolicy metadataMatchPolicy;
    private final MultiNamespaceInstanceAggregator aggregator;

    public MultiNamespaceQueryContext(MultiNamespaceRegistryConfig config,
                                      NacosInstanceQueryService instanceQueryService,
                                      FallbackNamespaceQueryService fallbackNamespaceQueryService,
                                      NamespaceFilterPolicy namespaceFilterPolicy,
                                      InstanceMetadataMatchPolicy metadataMatchPolicy,
                                      MultiNamespaceInstanceAggregator aggregator) {
        this.config = config;
        this.instanceQueryService = instanceQueryService;
        this.fallbackNamespaceQueryService = fallbackNamespaceQueryService;
        this.namespaceFilterPolicy = namespaceFilterPolicy;
        this.metadataMatchPolicy = metadataMatchPolicy;
        this.aggregator = aggregator;
    }

    public MultiNamespaceRegistryConfig getConfig() {
        return config;
    }

    public NacosInstanceQueryService getInstanceQueryService() {
        return instanceQueryService;
    }

    public FallbackNamespaceQueryService getFallbackNamespaceQueryService() {
        return fallbackNamespaceQueryService;
    }

    public NamespaceFilterPolicy getNamespaceFilterPolicy() {
        return namespaceFilterPolicy;
    }

    public InstanceMetadataMatchPolicy getMetadataMatchPolicy() {
        return metadataMatchPolicy;
    }

    public MultiNamespaceInstanceAggregator getAggregator() {
        return aggregator;
    }
}
