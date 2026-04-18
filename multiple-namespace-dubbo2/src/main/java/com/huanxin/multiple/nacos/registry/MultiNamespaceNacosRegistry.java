package com.huanxin.multiple.nacos.registry;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.huanxin.multiple.aggregator.InstanceDedupKeyBuilder;
import com.huanxin.multiple.aggregator.MultiNamespaceInstanceAggregator;
import com.huanxin.multiple.cache.AggregatedInstanceCache;
import com.huanxin.multiple.cache.CacheKeyBuilder;
import com.huanxin.multiple.cache.InMemoryAggregatedInstanceCache;
import com.huanxin.multiple.cache.InMemoryNamespaceInstanceCache;
import com.huanxin.multiple.cache.NamespaceInstanceCache;
import com.huanxin.multiple.config.MultiNamespaceRegistryConfig;
import com.huanxin.multiple.config.NamespaceSelectMode;
import com.huanxin.multiple.config.RegisterFailMode;
import com.huanxin.multiple.exception.MultiNamespaceRegisterException;
import com.huanxin.multiple.exception.MultiNamespaceSubscribeException;
import com.huanxin.multiple.model.AggregatedInstance;
import com.huanxin.multiple.nacos.converter.DubboUrlToNacosInstanceConverter;
import com.huanxin.multiple.nacos.converter.NacosInstanceToAggregatedInstanceConverter;
import com.huanxin.multiple.nacos.listener.MultiNamespaceNotifyDispatcher;
import com.huanxin.multiple.nacos.listener.NamespaceEventListenerAdapter;
import com.huanxin.multiple.nacos.naming.NacosNamingServiceFactory;
import com.huanxin.multiple.nacos.naming.NacosNamingServiceHolder;
import com.huanxin.multiple.nacos.support.FallbackNamespaceQueryService;
import com.huanxin.multiple.nacos.support.InstanceSortSupport;
import com.huanxin.multiple.nacos.support.InvocationNamespaceTracker;
import com.huanxin.multiple.nacos.support.MultiNamespaceQueryContext;
import com.huanxin.multiple.nacos.support.NacosInstanceQueryService;
import com.huanxin.multiple.policy.InstanceMetadataMatchPolicy;
import com.huanxin.multiple.policy.NamespaceFilterPolicy;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.registry.nacos.NacosServiceName;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.registry.RegistryFactory;
import org.apache.dubbo.registry.support.FailbackRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MultiNamespaceNacosRegistry extends FailbackRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(MultiNamespaceNacosRegistry.class);
    private static final String DEFAULT_GROUP = "DEFAULT_GROUP";
    private static final RegistryFactory NATIVE_NACOS_REGISTRY_FACTORY =
        ExtensionLoader.getExtensionLoader(RegistryFactory.class).getExtension("nacos");

    private final MultiNamespaceRegistryConfig config;
    private final NacosNamingServiceHolder namingServiceHolder;
    private final DubboUrlToNacosInstanceConverter dubboConverter;
    private final NacosInstanceToAggregatedInstanceConverter nacosConverter;
    private final NamespaceInstanceCache namespaceInstanceCache;
    private final AggregatedInstanceCache aggregatedInstanceCache;
    private final CacheKeyBuilder cacheKeyBuilder;
    private final MultiNamespaceInstanceAggregator aggregator;
    private final NamespaceFilterPolicy namespaceFilterPolicy;
    private final InstanceMetadataMatchPolicy metadataMatchPolicy;
    private final NacosInstanceQueryService instanceQueryService;
    private final FallbackNamespaceQueryService fallbackNamespaceQueryService;
    private final MultiNamespaceNotifyDispatcher notifyDispatcher;
    private final InstanceSortSupport instanceSortSupport;
    private final ExecutorService executorService;
    private final MultiNamespaceQueryContext queryContext;
    private final ConcurrentMap<String, Registry> nativeProviderRegistries = new ConcurrentHashMap<String, Registry>();
    private volatile boolean available = true;

    public MultiNamespaceNacosRegistry(URL url) {
        super(url);
        this.config = MultiNamespaceRegistryConfigParser.parse(url);
        this.executorService = Executors.newCachedThreadPool();
        this.namingServiceHolder = new NacosNamingServiceHolder(new NacosNamingServiceFactory());
        this.namingServiceHolder.init(config);
        this.dubboConverter = new DubboUrlToNacosInstanceConverter();
        this.nacosConverter = new NacosInstanceToAggregatedInstanceConverter();
        this.namespaceInstanceCache = new InMemoryNamespaceInstanceCache();
        this.aggregatedInstanceCache = new InMemoryAggregatedInstanceCache();
        this.cacheKeyBuilder = new CacheKeyBuilder();
        this.aggregator = new MultiNamespaceInstanceAggregator(new InstanceDedupKeyBuilder());
        this.namespaceFilterPolicy = new NamespaceFilterPolicy();
        this.metadataMatchPolicy = new InstanceMetadataMatchPolicy();
        this.instanceQueryService = new NacosInstanceQueryService(namingServiceHolder, nacosConverter);
        this.fallbackNamespaceQueryService = new FallbackNamespaceQueryService(namespaceFilterPolicy, metadataMatchPolicy, executorService);
        this.notifyDispatcher = new MultiNamespaceNotifyDispatcher();
        this.instanceSortSupport = new InstanceSortSupport();
        this.queryContext = new MultiNamespaceQueryContext(
            config,
            instanceQueryService,
            fallbackNamespaceQueryService,
            namespaceFilterPolicy,
            metadataMatchPolicy,
            aggregator
        );
        LOG.info("多 namespace 注册中心已创建，registryAddress={}, primaryNamespace={}, registerNamespaces={}, subscribeNamespaces={}",
            url.getAddress(), config.getPrimaryNamespace(), config.getRegisterNamespaces(), config.getSubscribeNamespaces());
    }

    @Override
    public void doRegister(URL url) {
        String serviceName = buildServiceName(url);
        String groupName = buildGroupName(url);
        Instance instance = dubboConverter.convert(url);
        List<String> success = new ArrayList<String>();
        List<String> failed = new ArrayList<String>();

        for (String namespace : config.getRegisterNamespaces()) {
            try {
                NamingService namingService = namingServiceHolder.get(namespace);
                LOG.info("开始注册实例，serviceName={}, groupName={}, namespace={}, address={}:{}",
                    serviceName, groupName, namespace, instance.getIp(), instance.getPort());
                LOG.info("注册实例关键元数据，namespace={}, serviceName={}, category={}, protocol={}, path={}, metadata={}",
                    namespace,
                    serviceName,
                    instance.getMetadata().get("category"),
                    instance.getMetadata().get("protocol"),
                    instance.getMetadata().get("path"),
                    instance.getMetadata());
                getNativeProviderRegistry(namespace).register(url);
                success.add(namespace);
            } catch (Exception ex) {
                failed.add(namespace);
                LOG.error("注册 namespace 失败，namespace={}, service={}", namespace, serviceName, ex);
            }
        }

        if (!failed.isEmpty() && config.getRegisterFailMode() == RegisterFailMode.ALL_SUCCESS) {
            throw new MultiNamespaceRegisterException("注册失败，success=" + success + ", failed=" + failed);
        }
        LOG.info("实例注册完成，serviceName={}, groupName={}, primaryNamespace={}, successNamespaces={}, failedNamespaces={}",
            serviceName, groupName, config.getPrimaryNamespace(), success, failed);
    }

    @Override
    public void doUnregister(URL url) {
        String serviceName = buildServiceName(url);
        String groupName = buildGroupName(url);
        Instance instance = dubboConverter.convert(url);
        for (String namespace : config.getRegisterNamespaces()) {
            try {
                LOG.info("开始注销实例，serviceName={}, groupName={}, namespace={}, address={}:{}",
                    serviceName, groupName, namespace, instance.getIp(), instance.getPort());
                getNativeProviderRegistry(namespace).unregister(url);
            } catch (Exception ex) {
                LOG.warn("注销 namespace 失败，namespace={}, service={}", namespace, serviceName, ex);
            }
        }
        LOG.info("实例注销完成，serviceName={}, groupName={}, namespaces={}",
            serviceName, groupName, config.getRegisterNamespaces());
    }

    @Override
    public void doSubscribe(URL url, NotifyListener listener) {
        String serviceName = buildServiceName(url);
        String groupName = buildGroupName(url);
        String serviceKey = buildServiceKey(serviceName, groupName);
        MultiNamespaceRegistryConfig effectiveConfig = resolveEffectiveConsumerConfig(url);
        LOG.info("开始订阅服务，serviceName={}, groupName={}, primaryNamespace={}, subscribeNamespaces={}, consumerNamespaceParam={}",
            serviceName, groupName, effectiveConfig.getPrimaryNamespace(), effectiveConfig.getSubscribeNamespaces(), url.getParameter("namespace"));
        List<NamespaceEventListenerAdapter> adapters = new ArrayList<NamespaceEventListenerAdapter>();

        primeNamespaceCache(serviceName, groupName, effectiveConfig);

        for (String namespace : effectiveConfig.getSubscribeNamespaces()) {
            NamespaceEventListenerAdapter adapter = new NamespaceEventListenerAdapter(
                namespace,
                serviceName,
                groupName,
                namespaceInstanceCache,
                aggregatedInstanceCache,
                this,
                listener,
                nacosConverter,
                effectiveConfig
            );
            try {
                namingServiceHolder.get(namespace).subscribe(serviceName, groupName, adapter);
                adapters.add(adapter);
            } catch (Exception ex) {
                throw new MultiNamespaceSubscribeException(
                    "subscribe namespace failed, namespace=" + namespace + ", service=" + serviceName, ex);
            }
        }

        notifyDispatcher.bind(serviceKey, listener, adapters, url);
        notify(url, listener, normalizeNotifyUrls(url, lookup(url)));
        LOG.info("服务订阅完成，serviceName={}, groupName={}, listenerNamespaces={}",
            serviceName, groupName, effectiveConfig.getSubscribeNamespaces());
    }

    @Override
    public void doUnsubscribe(URL url, NotifyListener listener) {
        String serviceName = buildServiceName(url);
        String groupName = buildGroupName(url);
        String serviceKey = buildServiceKey(serviceName, groupName);
        LOG.info("开始取消订阅，serviceName={}, groupName={}, serviceKey={}", serviceName, groupName, serviceKey);
        InvocationNamespaceTracker.clearService(url);
        for (NamespaceEventListenerAdapter adapter : notifyDispatcher.get(serviceKey, listener)) {
            try {
                namingServiceHolder.get(adapter.getNamespace()).unsubscribe(serviceName, groupName, adapter);
            } catch (Exception ex) {
                LOG.warn("取消订阅 namespace 失败，namespace={}, service={}", adapter.getNamespace(), serviceName, ex);
            }
        }
        notifyDispatcher.remove(serviceKey, listener);
        LOG.info("取消订阅完成，serviceName={}, groupName={}, serviceKey={}", serviceName, groupName, serviceKey);
    }

    @Override
    public List<URL> lookup(URL url) {
        String serviceName = buildServiceName(url);
        String groupName = buildGroupName(url);
        MultiNamespaceRegistryConfig effectiveConfig = resolveEffectiveConsumerConfig(url);
        LOG.info("开始查询服务地址，serviceName={}, groupName={}, primaryNamespace={}, subscribeNamespaces={}, mode={}",
            serviceName, groupName, effectiveConfig.getPrimaryNamespace(), effectiveConfig.getSubscribeNamespaces(), effectiveConfig.getNamespaceSelectMode());
        List<AggregatedInstance> instances = lookupAggregated(serviceName, groupName, effectiveConfig);
        List<URL> urls = toDubboUrls(instances);
        LOG.info("查询服务地址完成，serviceName={}, groupName={}, aggregatedCount={}, urlCount={}, urls={}",
            serviceName, groupName, instances.size(), urls.size(), urls);
        return urls;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void destroy() {
        available = false;
        LOG.info("开始销毁注册中心，primaryNamespace={}, registerNamespaces={}, subscribeNamespaces={}",
            config.getPrimaryNamespace(), config.getRegisterNamespaces(), config.getSubscribeNamespaces());
        notifyDispatcher.clear();
        namespaceInstanceCache.clear();
        aggregatedInstanceCache.clear();
        for (Registry registry : nativeProviderRegistries.values()) {
            try {
                registry.destroy();
            } catch (Exception ex) {
                LOG.warn("销毁原生 nacos registry 失败", ex);
            }
        }
        nativeProviderRegistries.clear();
        executorService.shutdownNow();
        namingServiceHolder.destroy();
        super.destroy();
        LOG.info("注册中心销毁完成");
    }

    public void notifyFromCache(String serviceName, String groupName, NotifyListener listener,
                                MultiNamespaceRegistryConfig effectiveConfig) {
        String serviceKey = buildServiceKey(serviceName, groupName);
        URL subscribeUrl = notifyDispatcher.getSubscribeUrl(serviceKey, listener);
        if (subscribeUrl == null) {
            subscribeUrl = listenerUrl(serviceName);
        }
        List<AggregatedInstance> aggregatedInstances = rebuildFromCache(serviceName, groupName, effectiveConfig);
        List<URL> urls = toDubboUrls(aggregatedInstances);
        InvocationNamespaceTracker.refresh(subscribeUrl, urls, aggregatedInstances);
        LOG.info("通知前 namespace 缓存摘要，serviceName={}, groupName={}, summary={}",
            serviceName, groupName, describeNamespaceCache(serviceName, effectiveConfig));
        LOG.info("从缓存触发通知，serviceName={}, groupName={}, primaryNamespace={}, aggregatedCount={}, urlCount={}, urls={}",
            serviceName, groupName, effectiveConfig.getPrimaryNamespace(), aggregatedInstances.size(), urls.size(), urls);
        notify(subscribeUrl, listener, normalizeNotifyUrls(subscribeUrl, urls));
    }

    public String buildAggregateCacheKey(String serviceName, String groupName, MultiNamespaceRegistryConfig effectiveConfig) {
        return cacheKeyBuilder.build(serviceName, groupName, effectiveConfig);
    }

    private URL listenerUrl(String serviceName) {
        return getUrl().setPath(serviceName);
    }

    private Registry getNativeProviderRegistry(String namespace) {
        Registry existing = nativeProviderRegistries.get(namespace);
        if (existing != null) {
            return existing;
        }
        Registry created = NATIVE_NACOS_REGISTRY_FACTORY.getRegistry(buildNativeRegistryUrl(namespace));
        Registry previous = nativeProviderRegistries.putIfAbsent(namespace, created);
        return previous == null ? created : previous;
    }

    private URL buildNativeRegistryUrl(String namespace) {
        return getUrl()
            .setProtocol("nacos")
            .addParameter("namespace", namespace)
            .removeParameter("registerNamespaces")
            .removeParameter("subscribeNamespaces")
            .removeParameter("primaryNamespace")
            .removeParameter("namespaceSelectMode")
            .removeParameter("namespaceFallback")
            .removeParameter("registerFailMode")
            .removeParameter("queryParallel")
            .removeParameter("queryTimeoutMs")
            .removeParameter("healthyOnly")
            .removeParameter("allowedFallbackNamespaces")
            .removeParameter("forbiddenFallbackNamespaces");
    }

    private List<URL> normalizeNotifyUrls(URL consumerUrl, List<URL> urls) {
        if (urls != null && !urls.isEmpty()) {
            return urls;
        }
        InvocationNamespaceTracker.clearService(consumerUrl);
        List<URL> normalized = new ArrayList<URL>(1);
        normalized.add(
            consumerUrl
                .setProtocol("empty")
                .setPath(buildServiceName(consumerUrl))
                .addParameter("category", "providers")
        );
        LOG.info("本次通知没有可用实例，已转换为 empty 协议通知，serviceName={}, notifyUrl={}",
            buildServiceName(consumerUrl), normalized);
        return normalized;
    }

    private List<AggregatedInstance> lookupAggregated(String serviceName, String groupName,
                                                      MultiNamespaceRegistryConfig effectiveConfig) {
        String cacheKey = buildAggregateCacheKey(serviceName, groupName, effectiveConfig);
        List<AggregatedInstance> cached = aggregatedInstanceCache.get(cacheKey);
        if (!cached.isEmpty()) {
            LOG.info("命中聚合缓存，serviceName={}, groupName={}, cacheKey={}, count={}",
                serviceName, groupName, cacheKey, cached.size());
            return cached;
        }
        LOG.info("未命中聚合缓存，serviceName={}, groupName={}, cacheKey={}", serviceName, groupName, cacheKey);

        List<AggregatedInstance> selected = selectInstances(serviceName, groupName, effectiveConfig);
        aggregatedInstanceCache.put(cacheKey, selected);
        return selected;
    }

    public List<AggregatedInstance> rebuildFromCache(String serviceName, String groupName,
                                                     MultiNamespaceRegistryConfig effectiveConfig) {
        String cacheKey = buildAggregateCacheKey(serviceName, groupName, effectiveConfig);
        List<AggregatedInstance> cached = aggregatedInstanceCache.get(cacheKey);
        if (!cached.isEmpty()) {
            LOG.info("命中重建缓存，serviceName={}, groupName={}, cacheKey={}, count={}",
                serviceName, groupName, cacheKey, cached.size());
            return cached;
        }

        List<AggregatedInstance> all = new ArrayList<AggregatedInstance>();
        for (String namespace : effectiveConfig.getSubscribeNamespaces()) {
            all.addAll(namespaceInstanceCache.get(serviceName, namespace));
        }

        List<AggregatedInstance> selected = finalizeInstances(serviceName, groupName, all, effectiveConfig);
        LOG.info("根据 namespace 缓存重建结果，serviceName={}, groupName={}, namespaceCount={}, mergedCount={}",
            serviceName, groupName, effectiveConfig.getSubscribeNamespaces().size(), selected.size());
        aggregatedInstanceCache.put(cacheKey, selected);
        return selected;
    }

    private List<AggregatedInstance> selectInstances(String serviceName, String groupName,
                                                     MultiNamespaceRegistryConfig effectiveConfig) {
        if (effectiveConfig.getNamespaceSelectMode() == NamespaceSelectMode.PRIMARY_ONLY) {
            LOG.info("按主空间独占模式选择实例，serviceName={}, groupName={}, primaryNamespace={}",
                serviceName, groupName, effectiveConfig.getPrimaryNamespace());
            List<AggregatedInstance> primary = queryAndCache(effectiveConfig.getPrimaryNamespace(), serviceName, groupName, effectiveConfig);
            return finalizeInstances(serviceName, groupName, primary, effectiveConfig);
        }

        if (effectiveConfig.getNamespaceSelectMode() == NamespaceSelectMode.MERGE_ALL) {
            LOG.info("按全量合并模式选择实例，serviceName={}, groupName={}, namespaces={}",
                serviceName, groupName, effectiveConfig.getSubscribeNamespaces());
            List<AggregatedInstance> all = new ArrayList<AggregatedInstance>();
            for (String namespace : effectiveConfig.getSubscribeNamespaces()) {
                all.addAll(queryAndCache(namespace, serviceName, groupName, effectiveConfig));
            }
            return finalizeInstances(serviceName, groupName, all, effectiveConfig);
        }

        // 默认策略是主 namespace 优先，只有主空间没有可用实例时才触发 fallback。
        List<AggregatedInstance> primary = queryAndCache(effectiveConfig.getPrimaryNamespace(), serviceName, groupName, effectiveConfig);
        List<AggregatedInstance> filteredPrimary = applyMetadataFilter(primary);
        if (!filteredPrimary.isEmpty() || !effectiveConfig.isNamespaceFallback()) {
            LOG.info("主空间命中实例，serviceName={}, groupName={}, primaryNamespace={}, count={}, fallbackEnabled={}",
                serviceName, groupName, effectiveConfig.getPrimaryNamespace(), filteredPrimary.size(), effectiveConfig.isNamespaceFallback());
            return finalizeInstances(serviceName, groupName, filteredPrimary, effectiveConfig);
        }

        LOG.info("主空间没有可用实例，开始降级查询，serviceName={}, groupName={}, primaryNamespace={}",
            serviceName, groupName, effectiveConfig.getPrimaryNamespace());
        List<AggregatedInstance> fallback = fallbackNamespaceQueryService.queryFirstAvailable(
            serviceName,
            groupName,
            new MultiNamespaceQueryContext(
                effectiveConfig,
                instanceQueryService,
                fallbackNamespaceQueryService,
                namespaceFilterPolicy,
                metadataMatchPolicy,
                aggregator
            )
        );
        for (AggregatedInstance instance : fallback) {
            addToNamespaceCache(serviceName, instance.getChosenNamespace(), instance);
        }
        LOG.info("降级查询已选中实例，serviceName={}, groupName={}, count={}", serviceName, groupName, fallback.size());
        return finalizeInstances(serviceName, groupName, fallback, effectiveConfig);
    }

    private void primeNamespaceCache(String serviceName, String groupName, MultiNamespaceRegistryConfig effectiveConfig) {
        LOG.info("开始预热 namespace 缓存，serviceName={}, groupName={}, namespaces={}",
            serviceName, groupName, effectiveConfig.getSubscribeNamespaces());
        for (String namespace : effectiveConfig.getSubscribeNamespaces()) {
            queryAndCache(namespace, serviceName, groupName, effectiveConfig);
        }
    }

    private List<AggregatedInstance> queryAndCache(String namespace, String serviceName, String groupName,
                                                   MultiNamespaceRegistryConfig effectiveConfig) {
        List<AggregatedInstance> queried = instanceQueryService.query(namespace, serviceName, groupName, effectiveConfig.isHealthyOnly());
        namespaceInstanceCache.put(serviceName, namespace, queried);
        LOG.info("namespace 缓存已更新，serviceName={}, groupName={}, namespace={}, count={}",
            serviceName, groupName, namespace, queried.size());
        return queried;
    }

    private void addToNamespaceCache(String serviceName, String namespace, AggregatedInstance instance) {
        List<AggregatedInstance> existing = new ArrayList<AggregatedInstance>(namespaceInstanceCache.get(serviceName, namespace));
        existing.add(instance);
        namespaceInstanceCache.put(serviceName, namespace, existing);
    }

    private List<AggregatedInstance> finalizeInstances(String serviceName, String groupName,
                                                       List<AggregatedInstance> candidates,
                                                       MultiNamespaceRegistryConfig effectiveConfig) {
        List<AggregatedInstance> working = new ArrayList<AggregatedInstance>(applyMetadataFilter(candidates));
        LOG.info("开始整理实例结果，serviceName={}, groupName={}, candidateCount={}, filteredCount={}, primaryNamespace={}, mode={}",
            serviceName, groupName, candidates.size(), working.size(), effectiveConfig.getPrimaryNamespace(), effectiveConfig.getNamespaceSelectMode());

        if (effectiveConfig.getNamespaceSelectMode() == NamespaceSelectMode.PRIMARY_FIRST
            && containsPrimaryInstance(working, effectiveConfig)) {
            // 主优先模式下，一旦主 namespace 有实例，就只保留主 namespace 的结果。
            working = retainPrimaryOnly(working, effectiveConfig);
            LOG.info("主优先模式仅保留主空间实例，serviceName={}, groupName={}, retainedCount={}",
                serviceName, groupName, working.size());
        }

        List<AggregatedInstance> aggregated = aggregator.aggregate(working, effectiveConfig.getPrimaryNamespace());
        instanceSortSupport.sort(aggregated, effectiveConfig.getPrimaryNamespace());
        aggregatedInstanceCache.put(buildAggregateCacheKey(serviceName, groupName, effectiveConfig), aggregated);
        LOG.info("实例结果整理完成，serviceName={}, groupName={}, resultCount={}",
            serviceName, groupName, aggregated.size());
        return aggregated;
    }

    private boolean containsPrimaryInstance(List<AggregatedInstance> instances,
                                            MultiNamespaceRegistryConfig effectiveConfig) {
        for (AggregatedInstance instance : instances) {
            if (effectiveConfig.getPrimaryNamespace().equals(instance.getChosenNamespace())) {
                return true;
            }
        }
        return false;
    }

    private List<AggregatedInstance> retainPrimaryOnly(List<AggregatedInstance> instances,
                                                       MultiNamespaceRegistryConfig effectiveConfig) {
        List<AggregatedInstance> primary = new ArrayList<AggregatedInstance>();
        for (AggregatedInstance instance : instances) {
            if (effectiveConfig.getPrimaryNamespace().equals(instance.getChosenNamespace())) {
                primary.add(instance);
            }
        }
        return primary;
    }

    private List<AggregatedInstance> applyMetadataFilter(List<AggregatedInstance> instances) {
        List<AggregatedInstance> filtered = new ArrayList<AggregatedInstance>();
        for (AggregatedInstance instance : instances) {
            if (metadataMatchPolicy.match(instance, config.getRequiredInstanceMetadata())) {
                filtered.add(instance);
            }
        }
        return filtered;
    }

    private List<URL> toDubboUrls(List<AggregatedInstance> instances) {
        List<URL> urls = new ArrayList<URL>();
        for (AggregatedInstance instance : instances) {
            urls.add(toDubboUrl(instance));
        }
        LOG.info("当前返回给 Dubbo 的实例明细：{}", describeInstances(instances));
        return urls;
    }

    public URL toDubboUrl(AggregatedInstance instance) {
        String rawUrl = instance.getMetadata().get("dubbo.url");
        if (rawUrl != null && !rawUrl.trim().isEmpty()) {
            URL result = URL.valueOf(rawUrl).setHost(instance.getIp()).setPort(instance.getPort());
            LOG.debug("聚合实例已转换为 Dubbo URL，serviceName={}, namespace={}, address={}:{}",
                instance.getServiceName(), instance.getChosenNamespace(), instance.getIp(), instance.getPort());
            return result;
        }

        String servicePath = resolveServicePath(instance);
        URL base = new URL(
            instance.getMetadata().get("dubbo.protocol") == null ? "dubbo" : instance.getMetadata().get("dubbo.protocol"),
            instance.getIp(),
            instance.getPort(),
            servicePath,
            instance.getMetadata()
        );
        LOG.debug("聚合实例已转换为 Dubbo URL，serviceName={}, namespace={}, address={}:{}",
            servicePath, instance.getChosenNamespace(), instance.getIp(), instance.getPort());
        return base;
    }

    private String resolveServicePath(AggregatedInstance instance) {
        String path = instance.getMetadata().get("path");
        if (path != null && !path.trim().isEmpty()) {
            return path;
        }
        String serviceInterface = instance.getMetadata().get("interface");
        if (serviceInterface != null && !serviceInterface.trim().isEmpty()) {
            return serviceInterface;
        }
        return instance.getServiceName();
    }

    private String buildServiceName(URL url) {
        return NacosServiceName.valueOf(url).getValue();
    }

    private String buildGroupName(URL url) {
        return DEFAULT_GROUP;
    }

    private String buildServiceKey(String serviceName, String groupName) {
        return groupName + "@@" + serviceName;
    }

    private MultiNamespaceRegistryConfig resolveEffectiveConsumerConfig(URL consumerUrl) {
        List<String> consumerNamespaces = MultiNamespaceRegistryConfigParser.parseNamespaceList(consumerUrl.getParameter("namespace"));
        if (consumerNamespaces.isEmpty()) {
            LOG.info("消费端使用注册中心默认配置，primaryNamespace={}, subscribeNamespaces={}",
                config.getPrimaryNamespace(), config.getSubscribeNamespaces());
            return config;
        }

        // 消费端显式传了 namespace 时，只覆盖消费链路，不影响 provider 注册链路。
        MultiNamespaceRegistryConfig effectiveConfig = copyConfig(config);
        effectiveConfig.setSubscribeNamespaces(new ArrayList<String>(consumerNamespaces));
        if (blank(consumerUrl.getParameter("primaryNamespace"))) {
            effectiveConfig.setPrimaryNamespace(consumerNamespaces.get(0));
        } else {
            effectiveConfig.setPrimaryNamespace(consumerUrl.getParameter("primaryNamespace"));
        }
        LOG.info("消费端已根据 namespace 参数生成生效配置，consumerNamespaceParam={}, primaryNamespace={}, subscribeNamespaces={}",
            consumerUrl.getParameter("namespace"), effectiveConfig.getPrimaryNamespace(), effectiveConfig.getSubscribeNamespaces());
        return effectiveConfig;
    }

    private MultiNamespaceRegistryConfig copyConfig(MultiNamespaceRegistryConfig source) {
        MultiNamespaceRegistryConfig target = new MultiNamespaceRegistryConfig();
        target.setServerAddr(source.getServerAddr());
        target.setUsername(source.getUsername());
        target.setPassword(source.getPassword());
        target.setRegisterNamespaces(new ArrayList<String>(source.getRegisterNamespaces()));
        target.setSubscribeNamespaces(new ArrayList<String>(source.getSubscribeNamespaces()));
        target.setPrimaryNamespace(source.getPrimaryNamespace());
        target.setNamespaceSelectMode(source.getNamespaceSelectMode());
        target.setNamespaceFallback(source.isNamespaceFallback());
        target.setRegisterFailMode(source.getRegisterFailMode());
        target.setQueryParallel(source.isQueryParallel());
        target.setQueryTimeoutMs(source.getQueryTimeoutMs());
        target.setHealthyOnly(source.isHealthyOnly());
        target.setAllowedFallbackNamespaces(new ArrayList<String>(source.getAllowedFallbackNamespaces()));
        target.setForbiddenFallbackNamespaces(new ArrayList<String>(source.getForbiddenFallbackNamespaces()));
        target.setRequiredInstanceMetadata(new LinkedHashMap<String, String>(source.getRequiredInstanceMetadata()));
        return target;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private List<String> describeInstances(List<AggregatedInstance> instances) {
        List<String> descriptions = new ArrayList<String>();
        for (AggregatedInstance instance : instances) {
            descriptions.add(
                "namespace=" + instance.getChosenNamespace()
                    + ", address=" + instance.getIp() + ":" + instance.getPort()
                    + ", service=" + instance.getServiceName()
            );
        }
        return descriptions;
    }

    private List<String> describeNamespaceCache(String serviceName, MultiNamespaceRegistryConfig effectiveConfig) {
        List<String> summary = new ArrayList<String>();
        for (String namespace : effectiveConfig.getSubscribeNamespaces()) {
            List<AggregatedInstance> cached = namespaceInstanceCache.get(serviceName, namespace);
            summary.add(namespace + "=" + describeInstances(cached));
        }
        return summary;
    }
}
