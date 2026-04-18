package com.huanxin.multiple.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AggregatedInstance {

    private String serviceName;
    private String ip;
    private int port;
    private boolean healthy;
    private double weight;
    private String chosenNamespace;
    private List<String> availableNamespaces = new ArrayList<String>();
    private Map<String, String> metadata = new LinkedHashMap<String, String>();

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public String getChosenNamespace() {
        return chosenNamespace;
    }

    public void setChosenNamespace(String chosenNamespace) {
        this.chosenNamespace = chosenNamespace;
    }

    public List<String> getAvailableNamespaces() {
        return availableNamespaces;
    }

    public void setAvailableNamespaces(List<String> availableNamespaces) {
        this.availableNamespaces = availableNamespaces;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}
