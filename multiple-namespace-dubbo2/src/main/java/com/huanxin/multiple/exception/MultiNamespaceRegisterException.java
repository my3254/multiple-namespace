package com.huanxin.multiple.exception;

public class MultiNamespaceRegisterException extends MultiNamespaceRegistryException {

    public MultiNamespaceRegisterException(String message) {
        super(message);
    }

    public MultiNamespaceRegisterException(String message, Throwable cause) {
        super(message, cause);
    }
}
