package com.huanxin.multiple.config;

public enum NamespaceSelectMode {
    PRIMARY_ONLY,
    PRIMARY_FIRST,
    MERGE_ALL;

    public static NamespaceSelectMode fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return PRIMARY_FIRST;
        }
        return NamespaceSelectMode.valueOf(value.trim().replace('-', '_').toUpperCase());
    }
}
