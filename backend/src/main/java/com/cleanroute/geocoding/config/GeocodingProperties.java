package com.cleanroute.geocoding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.geocoding")
public class GeocodingProperties {
    private String provider = "photon";
    private String photonUrl = "https://photon.komoot.io/api/";
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 5000;
    private double coverageRadiusMeters = 35000.0;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getPhotonUrl() { return photonUrl; }
    public void setPhotonUrl(String photonUrl) { this.photonUrl = photonUrl; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    public double getCoverageRadiusMeters() { return coverageRadiusMeters; }
    public void setCoverageRadiusMeters(double coverageRadiusMeters) { this.coverageRadiusMeters = coverageRadiusMeters; }
}
