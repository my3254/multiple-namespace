package com.huanxin.multiple.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MultiNamespaceRegistryConfig {

    private String serverAddr;
    private String username;
    private String password;
    private List<String> registerNamespaces = new ArrayList<String>();
    private List<String> subscribeNamespaces = new ArrayList<String>();
    private String primaryNamespace;
    private NamespaceSelectMode namespaceSelectMode = NamespaceSelectMode.PRIMARY_FIRST;
    private boolean namespaceFallback = true;
    private RegisterFailMode registerFailMode = RegisterFailMode.PARTIAL_SUCCESS;
    private boolean queryParallel = true;
    private long queryTimeoutMs = 1500L;
    private boolean healthyOnly = true;
    private List<String> allowedFallbackNamespaces = new ArrayList<String>();
    private List<String> forbiddenFallbackNamespaces = new ArrayList<String>();
    private Map<String, String> requiredInstanceMetadata = new LinkedHashMap<String, String>();

    public String getServerAddr() {
        return serverAddr;
    }

    public void setServerAddr(String serverAddr) {
        this.serverAddr = serverAddr;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<String> getRegisterNamespaces() {
        return registerNamespaces;
    }

    public void setRegisterNamespaces(List<String> registerNamespaces) {
        this.registerNamespaces = registerNamespaces;
    }

    public List<String> getSubscribeNamespaces() {
        return subscribeNamespaces;
    }

    public void setSubscribeNamespaces(List<String> subscribeNamespaces) {
        this.subscribeNamespaces = subscribeNamespaces;
    }

    public String getPrimaryNamespace() {
        return primaryNamespace;
    }

    public void setPrimaryNamespace(String primaryNamespace) {
        this.primaryNamespace = primaryNamespace;
    }

    public NamespaceSelectMode getNamespaceSelectMode() {
        return namespaceSelectMode;
    }

    public void setNamespaceSelectMode(NamespaceSelectMode namespaceSelectMode) {
        this.namespaceSelectMode = namespaceSelectMode;
    }

    public boolean isNamespaceFallback() {
        return namespaceFallback;
    }

    public void setNamespaceFallback(boolean namespaceFallback) {
        this.namespaceFallback = namespaceFallback;
    }

    public RegisterFailMode getRegisterFailMode() {
        return registerFailMode;
    }

    public void setRegisterFailMode(RegisterFailMode registerFailMode) {
        this.registerFailMode = registerFailMode;
    }

    public boolean isQueryParallel() {
        return queryParallel;
    }

    public void setQueryParallel(boolean queryParallel) {
        this.queryParallel = queryParallel;
    }

    public long getQueryTimeoutMs() {
        return queryTimeoutMs;
    }

    public void setQueryTimeoutMs(long queryTimeoutMs) {
        this.queryTimeoutMs = queryTimeoutMs;
    }

    public boolean isHealthyOnly() {
        return healthyOnly;
    }

    public void setHealthyOnly(boolean healthyOnly) {
        this.healthyOnly = healthyOnly;
    }

    public List<String> getAllowedFallbackNamespaces() {
        return allowedFallbackNamespaces;
    }

    public void setAllowedFallbackNamespaces(List<String> allowedFallbackNamespaces) {
        this.allowedFallbackNamespaces = allowedFallbackNamespaces;
    }

    public List<String> getForbiddenFallbackNamespaces() {
        return forbiddenFallbackNamespaces;
    }

    public void setForbiddenFallbackNamespaces(List<String> forbiddenFallbackNamespaces) {
        this.forbiddenFallbackNamespaces = forbiddenFallbackNamespaces;
    }

    public Map<String, String> getRequiredInstanceMetadata() {
        return requiredInstanceMetadata;
    }

    public void setRequiredInstanceMetadata(Map<String, String> requiredInstanceMetadata) {
        this.requiredInstanceMetadata = requiredInstanceMetadata;
    }
}
