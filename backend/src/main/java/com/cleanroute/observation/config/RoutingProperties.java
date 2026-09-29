package com.cleanroute.observation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.routing")
public class RoutingProperties {
    private String provider = "mock";
    private String osrmUrl = "https://router.project-osrm.org";
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 5000;
    private int maxGeometryPoints = 5000;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getOsrmUrl() { return osrmUrl; }
    public void setOsrmUrl(String osrmUrl) { this.osrmUrl = osrmUrl; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    public int getMaxGeometryPoints() { return maxGeometryPoints; }
    public void setMaxGeometryPoints(int maxGeometryPoints) { this.maxGeometryPoints = maxGeometryPoints; }
}
