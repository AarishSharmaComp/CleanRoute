package com.cleanroute.observation.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.observations")
public class ObservationProperties {
    @Min(60_000) private long ingestionIntervalMs = 900_000;
    @Min(0) private long initialDelayMs = 900_000;
    @Min(100) @Max(60_000) private long providerTimeoutMs = 5_000;
    @Min(1_000) @Max(300_000) private long rateLimitBackoffMs = 60_000;

    public long getIngestionIntervalMs() { return ingestionIntervalMs; }
    public void setIngestionIntervalMs(long value) { ingestionIntervalMs = value; }
    public long getInitialDelayMs() { return initialDelayMs; }
    public void setInitialDelayMs(long value) { initialDelayMs = value; }
    public long getProviderTimeoutMs() { return providerTimeoutMs; }
    public void setProviderTimeoutMs(long value) { providerTimeoutMs = value; }
    public long getRateLimitBackoffMs() { return rateLimitBackoffMs; }
    public void setRateLimitBackoffMs(long value) { rateLimitBackoffMs = value; }
}
