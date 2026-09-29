package com.cleanroute.observation.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.environmental")
public class EnvironmentalProperties {
    @NotBlank private String provider = "mock";
    @NotBlank private String openMeteoUrl = "https://air-quality-api.open-meteo.com/v1/air-quality";
    @Min(100) private int connectTimeoutMs = 2000;
    @Min(100) private int readTimeoutMs = 5000;
    @Min(1) private int routeSampleIntervalMeters = 250;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getOpenMeteoUrl() { return openMeteoUrl; }
    public void setOpenMeteoUrl(String openMeteoUrl) { this.openMeteoUrl = openMeteoUrl; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    public int getRouteSampleIntervalMeters() { return routeSampleIntervalMeters; }
    public void setRouteSampleIntervalMeters(int routeSampleIntervalMeters) { this.routeSampleIntervalMeters = routeSampleIntervalMeters; }
}
