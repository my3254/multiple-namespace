package com.huanxin.multiple.exception;

public class MultiNamespaceRegistryException extends RuntimeException {

    public MultiNamespaceRegistryException(String message) {
        super(message);
    }

    public MultiNamespaceRegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
