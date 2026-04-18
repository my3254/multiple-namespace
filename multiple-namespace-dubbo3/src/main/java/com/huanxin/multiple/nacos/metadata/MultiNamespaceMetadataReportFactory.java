package com.huanxin.multiple.nacos.metadata;

import com.huanxin.multiple.nacos.registry.MultiNamespaceRegistryConfigParser;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.metadata.report.MetadataReport;
import org.apache.dubbo.metadata.report.MetadataReportFactory;
import org.apache.dubbo.metadata.store.nacos.NacosMetadataReportFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MultiNamespaceMetadataReportFactory implements MetadataReportFactory {

    private static final Logger LOG = LoggerFactory.getLogger(MultiNamespaceMetadataReportFactory.class);

    private final MetadataReportFactory delegate = new NacosMetadataReportFactory();

    @Override
    public MetadataReport getMetadataReport(URL url) {
        URL normalizedUrl = normalize(url);
        LOG.info("元数据中心固定使用主空间，primaryNamespace={}, originalUrl={}, normalizedUrl={}",
            normalizedUrl.getParameter("namespace"), url.toFullString(), normalizedUrl.toFullString());
        return delegate.getMetadataReport(normalizedUrl);
    }

    private URL normalize(URL url) {
        String primaryNamespace = firstNonBlank(
            url.getParameter("primaryNamespace"),
            firstNamespace(url.getParameter("namespace")),
            firstNamespace(url.getParameter("registerNamespaces")),
            firstNamespace(url.getParameter("subscribeNamespaces"))
        );

        URL normalized = url.setProtocol("nacos");
        if (primaryNamespace != null && !primaryNamespace.trim().isEmpty()) {
            normalized = normalized
                .addParameter("namespace", primaryNamespace)
                .removeParameter("registerNamespaces")
                .removeParameter("subscribeNamespaces")
                .removeParameter("primaryNamespace");
        }
        return normalized;
    }

    private String firstNamespace(String raw) {
        List<String> namespaces = MultiNamespaceRegistryConfigParser.parseNamespaceList(raw);
        if (namespaces.isEmpty()) {
            return null;
        }
        return namespaces.get(0);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }
}
