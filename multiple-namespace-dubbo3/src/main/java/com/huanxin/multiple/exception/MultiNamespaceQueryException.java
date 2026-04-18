package com.huanxin.multiple.exception;

public class MultiNamespaceQueryException extends MultiNamespaceRegistryException {

    public MultiNamespaceQueryException(String namespace, String serviceName, Throwable cause) {
        super("查询 Nacos 实例失败，namespace=" + namespace + ", service=" + serviceName, cause);
    }
}
