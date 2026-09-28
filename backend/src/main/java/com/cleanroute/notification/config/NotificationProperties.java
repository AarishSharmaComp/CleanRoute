package com.cleanroute.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@ConfigurationProperties(prefix = "app.notifications")
public class NotificationProperties {
    @Min(0) @Max(500)
    private int highAqiThreshold = 100;
    @DecimalMin("0.0")
    private double highPm25Threshold = 35;
    @DecimalMin("0.0")
    private double cleanerExposureImprovement = 5;
    @Min(1) @Max(100)
    private int resultLimit = 50;

    public int getHighAqiThreshold() { return highAqiThreshold; }
    public void setHighAqiThreshold(int value) { highAqiThreshold = value; }
    public double getHighPm25Threshold() { return highPm25Threshold; }
    public void setHighPm25Threshold(double value) { highPm25Threshold = value; }
    public double getCleanerExposureImprovement() { return cleanerExposureImprovement; }
    public void setCleanerExposureImprovement(double value) { cleanerExposureImprovement = value; }
    public int getResultLimit() { return resultLimit; }
    public void setResultLimit(int value) { resultLimit = Math.max(1, Math.min(100, value)); }
}
