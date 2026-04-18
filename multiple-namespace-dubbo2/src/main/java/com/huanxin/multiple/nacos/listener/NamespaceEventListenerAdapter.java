package com.huanxin.multiple.nacos.listener;

import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.huanxin.multiple.cache.AggregatedInstanceCache;
import com.huanxin.multiple.cache.NamespaceInstanceCache;
import com.huanxin.multiple.config.MultiNamespaceRegistryConfig;
import com.huanxin.multiple.model.AggregatedInstance;
import com.huanxin.multiple.nacos.converter.NacosInstanceToAggregatedInstanceConverter;
import com.huanxin.multiple.nacos.registry.MultiNamespaceNacosRegistry;
import org.apache.dubbo.registry.NotifyListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class NamespaceEventListenerAdapter implements EventListener {

    private static final Logger LOG = LoggerFactory.getLogger(NamespaceEventListenerAdapter.class);

    private final String namespace;
    private final String serviceName;
    private final String groupName;
    private final NamespaceInstanceCache namespaceInstanceCache;
    private final AggregatedInstanceCache aggregatedInstanceCache;
    private final MultiNamespaceNacosRegistry registry;
    private final NotifyListener notifyListener;
    private final NacosInstanceToAggregatedInstanceConverter converter;
    private final MultiNamespaceRegistryConfig effectiveConfig;

    public NamespaceEventListenerAdapter(String namespace,
                                         String serviceName,
                                         String groupName,
                                         NamespaceInstanceCache namespaceInstanceCache,
                                         AggregatedInstanceCache aggregatedInstanceCache,
                                         MultiNamespaceNacosRegistry registry,
                                         NotifyListener notifyListener,
                                         NacosInstanceToAggregatedInstanceConverter converter,
                                         MultiNamespaceRegistryConfig effectiveConfig) {
        this.namespace = namespace;
        this.serviceName = serviceName;
        this.groupName = groupName;
        this.namespaceInstanceCache = namespaceInstanceCache;
        this.aggregatedInstanceCache = aggregatedInstanceCache;
        this.registry = registry;
        this.notifyListener = notifyListener;
        this.converter = converter;
        this.effectiveConfig = effectiveConfig;
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof NamingEvent)) {
            return;
        }
        NamingEvent namingEvent = (NamingEvent) event;
        LOG.info("收到 namespace 事件，namespace={}, serviceName={}, groupName={}, instanceCount={}",
            namespace, serviceName, groupName, namingEvent.getInstances().size());
        LOG.info("namespace 事件实例状态，namespace={}, serviceName={}, states={}",
            namespace, serviceName, describeStates(namingEvent.getInstances()));
        List<AggregatedInstance> current = new ArrayList<AggregatedInstance>();
        for (com.alibaba.nacos.api.naming.pojo.Instance instance : namingEvent.getInstances()) {
            if (!isSelectable(instance)) {
                LOG.info("事件实例已被多空间消费链过滤，namespace={}, serviceName={}, ip={}, port={}, healthy={}, enabled={}",
                    namespace, serviceName, instance.getIp(), instance.getPort(), instance.isHealthy(), instance.isEnabled());
                continue;
            }
            current.add(converter.convert(namespace, serviceName, instance));
        }
        // 先刷新单 namespace 缓存，再让注册中心按当前策略重建聚合结果。
        namespaceInstanceCache.put(serviceName, namespace, current);
        aggregatedInstanceCache.evict(registry.buildAggregateCacheKey(serviceName, groupName, effectiveConfig));
        LOG.info("namespace 缓存已刷新，namespace={}, serviceName={}, currentCount={}, instances={}",
            namespace, serviceName, current.size(), namingEvent.getInstances());
        registry.notifyFromCache(serviceName, groupName, notifyListener, effectiveConfig);
    }

    public String getNamespace() {
        return namespace;
    }

    private boolean isSelectable(com.alibaba.nacos.api.naming.pojo.Instance instance) {
        if (!instance.isEnabled()) {
            return false;
        }
        return !effectiveConfig.isHealthyOnly() || instance.isHealthy();
    }

    private List<String> describeStates(List<com.alibaba.nacos.api.naming.pojo.Instance> instances) {
        List<String> states = new ArrayList<String>();
        for (com.alibaba.nacos.api.naming.pojo.Instance instance : instances) {
            states.add(
                instance.getIp() + ":" + instance.getPort()
                    + ", enabled=" + instance.isEnabled()
                    + ", healthy=" + instance.isHealthy()
            );
        }
        return states;
    }
}
