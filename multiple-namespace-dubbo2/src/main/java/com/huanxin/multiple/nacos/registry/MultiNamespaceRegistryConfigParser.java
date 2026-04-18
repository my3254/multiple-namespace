package com.huanxin.multiple.nacos.registry;

import com.huanxin.multiple.config.MultiNamespaceRegistryConfig;
import com.huanxin.multiple.config.NamespaceSelectMode;
import com.huanxin.multiple.config.RegisterFailMode;
import com.huanxin.multiple.exception.MultiNamespaceConfigException;
import org.apache.dubbo.common.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class MultiNamespaceRegistryConfigParser {

    private static final Logger LOG = LoggerFactory.getLogger(MultiNamespaceRegistryConfigParser.class);

    private MultiNamespaceRegistryConfigParser() {
    }

    public static MultiNamespaceRegistryConfig parse(URL url) {
        MultiNamespaceRegistryConfig config = new MultiNamespaceRegistryConfig();
        config.setServerAddr(url.getAddress());
        config.setUsername(url.getUsername());
        config.setPassword(url.getPassword());

        List<String> configuredNamespaces = parseNamespaceList(url.getParameter("namespace"));
        config.setRegisterNamespaces(parseNamespaceList(url.getParameter("registerNamespaces")));
        config.setSubscribeNamespaces(parseNamespaceList(url.getParameter("subscribeNamespaces")));
        config.setPrimaryNamespace(url.getParameter("primaryNamespace"));
        config.setNamespaceSelectMode(NamespaceSelectMode.fromValue(url.getParameter("namespaceSelectMode", "primary-first")));
        config.setNamespaceFallback(url.getParameter("namespaceFallback", true));
        config.setRegisterFailMode(RegisterFailMode.fromValue(url.getParameter("registerFailMode", "partial-success")));
        config.setQueryParallel(url.getParameter("queryParallel", true));
        config.setQueryTimeoutMs(url.getParameter("queryTimeoutMs", 1500));
        config.setHealthyOnly(url.getParameter("healthyOnly", true));
        config.setAllowedFallbackNamespaces(parseNamespaceList(url.getParameter("allowedFallbackNamespaces")));
        config.setForbiddenFallbackNamespaces(parseNamespaceList(url.getParameter("forbiddenFallbackNamespaces")));
        config.setRequiredInstanceMetadata(parseMetadata(url.getParameters()));

        if (config.getRegisterNamespaces().isEmpty() && !configuredNamespaces.isEmpty()) {
            config.setRegisterNamespaces(new ArrayList<String>(configuredNamespaces));
        }
        if (config.getSubscribeNamespaces().isEmpty() && !configuredNamespaces.isEmpty()) {
            config.setSubscribeNamespaces(new ArrayList<String>(configuredNamespaces));
        }
        if (blank(config.getPrimaryNamespace())) {
            config.setPrimaryNamespace(resolvePrimaryNamespace(config));
        }
        if (config.getRegisterNamespaces().isEmpty() && !blank(config.getPrimaryNamespace())) {
            config.getRegisterNamespaces().add(config.getPrimaryNamespace());
        }
        if (config.getSubscribeNamespaces().isEmpty() && !blank(config.getPrimaryNamespace())) {
            config.getSubscribeNamespaces().add(config.getPrimaryNamespace());
        }

        validate(config);
        LOG.info(
            "多 namespace 配置解析完成，address={}, namespace={}, primaryNamespace={}, registerNamespaces={}, subscribeNamespaces={}, mode={}, fallback={}, healthyOnly={}",
            config.getServerAddr(),
            url.getParameter("namespace"),
            config.getPrimaryNamespace(),
            config.getRegisterNamespaces(),
            config.getSubscribeNamespaces(),
            config.getNamespaceSelectMode(),
            config.isNamespaceFallback(),
            config.isHealthyOnly()
        );
        return config;
    }

    private static void validate(MultiNamespaceRegistryConfig config) {
        if (blank(config.getPrimaryNamespace())
            && config.getRegisterNamespaces().isEmpty()
            && config.getSubscribeNamespaces().isEmpty()) {
            // 允许先不指定 namespace，后续由消费端 consumer.parameters.namespace 覆盖。
            return;
        }

        if (blank(config.getPrimaryNamespace())) {
            throw new MultiNamespaceConfigException("primaryNamespace 不能为空");
        }
        if (!config.getSubscribeNamespaces().isEmpty()
            && !config.getSubscribeNamespaces().contains(config.getPrimaryNamespace())) {
            throw new MultiNamespaceConfigException("primaryNamespace 必须包含在 subscribeNamespaces 中");
        }
        for (String namespace : config.getAllowedFallbackNamespaces()) {
            if (config.getForbiddenFallbackNamespaces().contains(namespace)) {
                throw new MultiNamespaceConfigException("allowedFallbackNamespaces 与 forbiddenFallbackNamespaces 冲突: " + namespace);
            }
        }
    }

    private static String resolvePrimaryNamespace(MultiNamespaceRegistryConfig config) {
        if (!config.getRegisterNamespaces().isEmpty()) {
            return config.getRegisterNamespaces().get(0);
        }
        if (!config.getSubscribeNamespaces().isEmpty()) {
            return config.getSubscribeNamespaces().get(0);
        }
        return null;
    }

    public static List<String> parseNamespaceList(String raw) {
        if (blank(raw)) {
            return new ArrayList<String>();
        }
        LinkedHashSet<String> namespaces = new LinkedHashSet<String>();
        for (String namespace : Arrays.asList(raw.split(","))) {
            String trimmed = namespace == null ? null : namespace.trim();
            if (!blank(trimmed)) {
                namespaces.add(trimmed);
            }
        }
        return new ArrayList<String>(namespaces);
    }

    private static Map<String, String> parseMetadata(Map<String, String> parameters) {
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (entry.getKey().startsWith("requiredInstanceMetadata.")) {
                metadata.put(entry.getKey().substring("requiredInstanceMetadata.".length()), entry.getValue());
            }
        }
        return metadata;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
