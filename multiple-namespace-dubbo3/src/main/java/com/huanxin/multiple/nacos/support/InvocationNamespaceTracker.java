package com.huanxin.multiple.nacos.support;

import com.huanxin.multiple.model.AggregatedInstance;
import org.apache.dubbo.common.URL;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InvocationNamespaceTracker {

    private static final ConcurrentMap<String, String> ADDRESS_NAMESPACE = new ConcurrentHashMap<String, String>();

    private InvocationNamespaceTracker() {
    }

    public static void refresh(URL consumerUrl, List<URL> providerUrls, List<AggregatedInstance> instances) {
        clearService(consumerUrl);
        int size = Math.min(providerUrls.size(), instances.size());
        for (int i = 0; i < size; i++) {
            record(providerUrls.get(i), instances.get(i).getChosenNamespace());
        }
    }

    public static String lookup(URL url) {
        if (url == null) {
            return null;
        }
        return ADDRESS_NAMESPACE.get(buildAddressKey(url));
    }

    public static void clearService(URL consumerUrl) {
        if (consumerUrl == null) {
            return;
        }
        String serviceKeyPrefix = buildServiceKey(consumerUrl) + "@";
        for (String key : ADDRESS_NAMESPACE.keySet()) {
            if (key.startsWith(serviceKeyPrefix)) {
                ADDRESS_NAMESPACE.remove(key);
            }
        }
    }

    private static void record(URL providerUrl, String namespace) {
        if (providerUrl == null || namespace == null || namespace.trim().isEmpty()) {
            return;
        }
        ADDRESS_NAMESPACE.put(buildAddressKey(providerUrl), namespace);
    }

    private static String buildAddressKey(URL url) {
        return buildServiceKey(url) + "@" + url.getHost() + ":" + url.getPort();
    }

    private static String buildServiceKey(URL url) {
        return resolveServiceName(url)
            + "|group=" + safeValue(url.getParameter("group"))
            + "|version=" + safeValue(url.getParameter("version"));
    }

    private static String resolveServiceName(URL url) {
        if (url.getServiceInterface() != null && !url.getServiceInterface().trim().isEmpty()) {
            return url.getServiceInterface();
        }
        return url.getPath();
    }

    private static String safeValue(String value) {
        return value == null ? "" : value;
    }
}
