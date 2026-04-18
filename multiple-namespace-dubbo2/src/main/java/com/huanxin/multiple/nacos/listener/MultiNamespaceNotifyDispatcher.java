package com.huanxin.multiple.nacos.listener;

import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.common.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MultiNamespaceNotifyDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(MultiNamespaceNotifyDispatcher.class);

    private final Map<String, Map<NotifyListener, List<NamespaceEventListenerAdapter>>> listeners =
        new ConcurrentHashMap<String, Map<NotifyListener, List<NamespaceEventListenerAdapter>>>();
    private final Map<String, Map<NotifyListener, URL>> listenerUrls =
        new ConcurrentHashMap<String, Map<NotifyListener, URL>>();

    public void bind(String serviceKey, NotifyListener listener, List<NamespaceEventListenerAdapter> adapters, URL subscribeUrl) {
        Map<NotifyListener, List<NamespaceEventListenerAdapter>> serviceListeners = listeners.get(serviceKey);
        if (serviceListeners == null) {
            serviceListeners = new ConcurrentHashMap<NotifyListener, List<NamespaceEventListenerAdapter>>();
            listeners.put(serviceKey, serviceListeners);
        }
        serviceListeners.put(listener, new ArrayList<NamespaceEventListenerAdapter>(adapters));
        Map<NotifyListener, URL> serviceUrls = listenerUrls.get(serviceKey);
        if (serviceUrls == null) {
            serviceUrls = new ConcurrentHashMap<NotifyListener, URL>();
            listenerUrls.put(serviceKey, serviceUrls);
        }
        serviceUrls.put(listener, subscribeUrl);
        LOG.info("已绑定通知监听器，serviceKey={}, namespaceCount={}", serviceKey, adapters.size());
    }

    public List<NamespaceEventListenerAdapter> get(String serviceKey, NotifyListener listener) {
        Map<NotifyListener, List<NamespaceEventListenerAdapter>> serviceListeners = listeners.get(serviceKey);
        if (serviceListeners == null) {
            return Collections.emptyList();
        }
        List<NamespaceEventListenerAdapter> adapters = serviceListeners.get(listener);
        if (adapters == null) {
            return Collections.emptyList();
        }
        return new ArrayList<NamespaceEventListenerAdapter>(adapters);
    }

    public void remove(String serviceKey, NotifyListener listener) {
        Map<NotifyListener, List<NamespaceEventListenerAdapter>> serviceListeners = listeners.get(serviceKey);
        if (serviceListeners != null) {
            serviceListeners.remove(listener);
            LOG.info("已移除通知监听器，serviceKey={}", serviceKey);
        }
        Map<NotifyListener, URL> serviceUrls = listenerUrls.get(serviceKey);
        if (serviceUrls != null) {
            serviceUrls.remove(listener);
        }
    }

    public URL getSubscribeUrl(String serviceKey, NotifyListener listener) {
        Map<NotifyListener, URL> serviceUrls = listenerUrls.get(serviceKey);
        if (serviceUrls == null) {
            return null;
        }
        return serviceUrls.get(listener);
    }

    public void clear() {
        listeners.clear();
        listenerUrls.clear();
    }
}
