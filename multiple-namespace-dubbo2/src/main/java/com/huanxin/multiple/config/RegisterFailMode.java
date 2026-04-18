package com.huanxin.multiple.config;

public enum RegisterFailMode {
    ALL_SUCCESS,
    PARTIAL_SUCCESS;

    public static RegisterFailMode fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return PARTIAL_SUCCESS;
        }
        return RegisterFailMode.valueOf(value.trim().replace('-', '_').toUpperCase());
    }
}
