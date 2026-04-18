package com.huanxin.multiple.nacos.support;

import com.huanxin.multiple.config.MultiNamespaceRegistryConfig;
import com.huanxin.multiple.model.AggregatedInstance;
import com.huanxin.multiple.model.NamespaceResult;
import com.huanxin.multiple.policy.InstanceMetadataMatchPolicy;
import com.huanxin.multiple.policy.NamespaceFilterPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class FallbackNamespaceQueryService {

    private static final Logger LOG = LoggerFactory.getLogger(FallbackNamespaceQueryService.class);

    private final NamespaceFilterPolicy namespaceFilterPolicy;
    private final InstanceMetadataMatchPolicy metadataMatchPolicy;
    private final ExecutorService executorService;

    public FallbackNamespaceQueryService(NamespaceFilterPolicy namespaceFilterPolicy,
                                         InstanceMetadataMatchPolicy metadataMatchPolicy,
                                         ExecutorService executorService) {
        this.namespaceFilterPolicy = namespaceFilterPolicy;
        this.metadataMatchPolicy = metadataMatchPolicy;
        this.executorService = executorService;
    }

    public List<AggregatedInstance> queryFirstAvailable(String serviceName,
                                                        String groupName,
                                                        MultiNamespaceQueryContext context) {
        MultiNamespaceRegistryConfig config = context.getConfig();
        List<String> fallbacks = namespaceFilterPolicy.fallbackNamespaces(config);
        LOG.info("准备执行降级查询，serviceName={}, groupName={}, primaryNamespace={}, candidates={}, parallel={}",
            serviceName, groupName, config.getPrimaryNamespace(), fallbacks, config.isQueryParallel());
        if (fallbacks.isEmpty()) {
            return Collections.emptyList();
        }
        if (config.isQueryParallel()) {
            return queryParallel(fallbacks, serviceName, groupName, context);
        }
        return querySequential(fallbacks, serviceName, groupName, context);
    }

    private List<AggregatedInstance> querySequential(List<String> fallbacks,
                                                     String serviceName,
                                                     String groupName,
                                                     MultiNamespaceQueryContext context) {
        for (String namespace : fallbacks) {
            List<AggregatedInstance> current = filter(context.getInstanceQueryService()
                .query(namespace, serviceName, groupName, context.getConfig().isHealthyOnly()), context);
            if (!current.isEmpty()) {
                LOG.info("降级查询命中，serviceName={}, groupName={}, namespace={}, count={}",
                    serviceName, groupName, namespace, current.size());
                return current;
            }
        }
        LOG.info("降级查询未命中，serviceName={}, groupName={}", serviceName, groupName);
        return Collections.emptyList();
    }

    private List<AggregatedInstance> queryParallel(List<String> fallbacks,
                                                   final String serviceName,
                                                   final String groupName,
                                                   final MultiNamespaceQueryContext context) {
        List<CompletableFuture<NamespaceResult>> futures = new ArrayList<CompletableFuture<NamespaceResult>>();
        for (final String namespace : fallbacks) {
            futures.add(CompletableFuture.supplyAsync(new java.util.function.Supplier<NamespaceResult>() {
                @Override
                public NamespaceResult get() {
                    try {
                        List<AggregatedInstance> result = context.getInstanceQueryService()
                            .query(namespace, serviceName, groupName, context.getConfig().isHealthyOnly());
                        return NamespaceResult.success(namespace, filter(result, context));
                    } catch (Throwable ex) {
                        return NamespaceResult.failure(namespace, ex);
                    }
                }
            }, executorService));
        }

        long deadline = System.currentTimeMillis() + context.getConfig().getQueryTimeoutMs();
        List<NamespaceResult> results = new ArrayList<NamespaceResult>();
        for (CompletableFuture<NamespaceResult> future : futures) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0L) {
                break;
            }
            try {
                results.add(future.get(remaining, TimeUnit.MILLISECONDS));
            } catch (Exception ignored) {
                // 单个 namespace 超时或失败时，继续尝试其他 namespace。
            }
        }
        for (String namespace : fallbacks) {
            for (NamespaceResult result : results) {
                if (namespace.equals(result.getNamespace()) && !result.getInstances().isEmpty()) {
                    LOG.info("并行降级查询命中，serviceName={}, groupName={}, namespace={}, count={}",
                        serviceName, groupName, namespace, result.getInstances().size());
                    return result.getInstances();
                }
            }
        }
        LOG.info("并行降级查询未命中，serviceName={}, groupName={}", serviceName, groupName);
        return Collections.emptyList();
    }

    private List<AggregatedInstance> filter(List<AggregatedInstance> instances, MultiNamespaceQueryContext context) {
        List<AggregatedInstance> filtered = new ArrayList<AggregatedInstance>();
        for (AggregatedInstance instance : instances) {
            if (metadataMatchPolicy.match(instance, context.getConfig().getRequiredInstanceMetadata())) {
                filtered.add(instance);
            }
        }
        return filtered;
    }
}
