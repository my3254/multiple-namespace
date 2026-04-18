package com.huanxin.multiple.nacos.support;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.huanxin.multiple.exception.MultiNamespaceQueryException;
import com.huanxin.multiple.model.AggregatedInstance;
import com.huanxin.multiple.nacos.converter.NacosInstanceToAggregatedInstanceConverter;
import com.huanxin.multiple.nacos.naming.NacosNamingServiceHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class NacosInstanceQueryService {

    private static final Logger LOG = LoggerFactory.getLogger(NacosInstanceQueryService.class);

    private final NacosNamingServiceHolder namingServiceHolder;
    private final NacosInstanceToAggregatedInstanceConverter converter;

    public NacosInstanceQueryService(NacosNamingServiceHolder namingServiceHolder,
                                     NacosInstanceToAggregatedInstanceConverter converter) {
        this.namingServiceHolder = namingServiceHolder;
        this.converter = converter;
    }

    public List<AggregatedInstance> query(String namespace, String serviceName, String groupName, boolean healthyOnly) {
        try {
            LOG.info("开始查询实例，namespace={}, serviceName={}, groupName={}, healthyOnly={}",
                namespace, serviceName, groupName, healthyOnly);
            NamingService namingService = namingServiceHolder.get(namespace);
            List<Instance> instances = namingService.getAllInstances(serviceName, groupName, healthyOnly);
            List<AggregatedInstance> result = new ArrayList<AggregatedInstance>();
            for (Instance instance : instances) {
                if (!isSelectable(instance, healthyOnly)) {
                    LOG.info("实例已被多空间消费链过滤，namespace={}, serviceName={}, ip={}, port={}, healthy={}, enabled={}",
                        namespace, serviceName, instance.getIp(), instance.getPort(), instance.isHealthy(), instance.isEnabled());
                    continue;
                }
                result.add(converter.convert(namespace, serviceName, instance));
            }
            LOG.info("查询实例完成，namespace={}, serviceName={}, groupName={}, count={}",
                namespace, serviceName, groupName, result.size());
            return result;
        } catch (Exception ex) {
            LOG.error("查询实例失败，namespace={}, serviceName={}, groupName={}", namespace, serviceName, groupName, ex);
            throw new MultiNamespaceQueryException(namespace, serviceName, ex);
        }
    }

    private boolean isSelectable(Instance instance, boolean healthyOnly) {
        if (!instance.isEnabled()) {
            return false;
        }
        return !healthyOnly || instance.isHealthy();
    }
}
