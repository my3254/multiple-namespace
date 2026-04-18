package com.huanxin.multiple.nacos.registry;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.registry.RegistryFactory;

public class MultiNamespaceNacosRegistryFactory implements RegistryFactory {

    @Override
    public Registry getRegistry(URL url) {
        return new MultiNamespaceNacosRegistry(url);
    }
}
