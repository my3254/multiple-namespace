package com.huanxin.multiple.nacos.converter;

import com.alibaba.nacos.api.naming.pojo.Instance;
import org.apache.dubbo.common.URL;

import java.util.LinkedHashMap;
import java.util.Map;

public class DubboUrlToNacosInstanceConverter {

    public Instance convert(URL url) {
        Instance instance = new Instance();
        instance.setIp(url.getHost());
        instance.setPort(url.getPort());
        instance.setHealthy(true);
        instance.setEnabled(true);
        instance.setEphemeral(true);
        instance.setWeight(url.getParameter("weight", 1.0D));
        instance.setClusterName(url.getParameter("cluster", "DEFAULT"));
        instance.setMetadata(buildMetadata(url));
        return instance;
    }

    private Map<String, String> buildMetadata(URL url) {
        Map<String, String> metadata = new LinkedHashMap<String, String>(url.getParameters());
        metadata.put("category", url.getParameter("category", "providers"));
        metadata.put("protocol", url.getProtocol());
        metadata.put("path", url.getPath());
        metadata.put("instanceUniqueKey", url.getHost() + ":" + url.getPort());
        metadata.put("multiNsRegistered", "true");
        metadata.put("dubboApplication", url.getParameter("application", ""));
        metadata.put("dubbo.url", url.toFullString());
        metadata.put("dubbo.protocol", url.getProtocol());
        return metadata;
    }
}
