package com.huanxin.multiple.nacos.naming;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class NacosNamingServiceFactory {

    private static final Logger LOG = LoggerFactory.getLogger(NacosNamingServiceFactory.class);

    public NamingService create(String serverAddr, String namespace, String username, String password) throws NacosException {
        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr);
        properties.put("namespace", namespace);
        if (username != null && !username.trim().isEmpty()) {
            properties.put("username", username);
        }
        if (password != null && !password.trim().isEmpty()) {
            properties.put("password", password);
        }
        LOG.info("开始创建 Nacos NamingService，serverAddr={}, namespace={}, username={}", serverAddr, namespace, username);
        return NamingFactory.createNamingService(properties);
    }
}
