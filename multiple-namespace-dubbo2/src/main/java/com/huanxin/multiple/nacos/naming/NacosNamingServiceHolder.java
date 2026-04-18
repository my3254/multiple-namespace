package com.huanxin.multiple.nacos.naming;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.huanxin.multiple.config.MultiNamespaceRegistryConfig;
import com.huanxin.multiple.exception.MultiNamespaceRegistryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class NacosNamingServiceHolder {

    private static final Logger LOG = LoggerFactory.getLogger(NacosNamingServiceHolder.class);

    private final NacosNamingServiceFactory factory;
    private final ConcurrentMap<String, NamingService> namingServices = new ConcurrentHashMap<String, NamingService>();
    private volatile MultiNamespaceRegistryConfig config;

    public NacosNamingServiceHolder(NacosNamingServiceFactory factory) {
        this.factory = factory;
    }

    public void init(MultiNamespaceRegistryConfig config) {
        this.config = config;
        Set<String> namespaces = new LinkedHashSet<String>();
        namespaces.addAll(config.getRegisterNamespaces());
        namespaces.addAll(config.getSubscribeNamespaces());
        LOG.info("开始初始化 NamingService，namespaces={}", namespaces);
        for (String namespace : namespaces) {
            get(namespace);
        }
    }

    public NamingService get(String namespace) {
        NamingService namingService = namingServices.get(namespace);
        if (namingService != null) {
            return namingService;
        }
        // 同一个 namespace 只初始化一次 NamingService，避免重复建连。
        synchronized (namingServices) {
            namingService = namingServices.get(namespace);
            if (namingService != null) {
                return namingService;
            }
            try {
                namingService = factory.create(config.getServerAddr(), namespace, config.getUsername(), config.getPassword());
                namingServices.put(namespace, namingService);
                LOG.info("Nacos NamingService 已就绪，namespace={}", namespace);
                return namingService;
            } catch (NacosException ex) {
                throw new MultiNamespaceRegistryException("创建 Nacos NamingService 失败，namespace=" + namespace, ex);
            }
        }
    }

    public void destroy() {
        LOG.info("开始销毁 NamingService，namespaces={}", namingServices.keySet());
        for (NamingService namingService : namingServices.values()) {
            try {
                namingService.shutDown();
            } catch (NacosException ignored) {
                // 关闭阶段不再向上抛错，避免影响应用退出。
            }
        }
        namingServices.clear();
    }
}
