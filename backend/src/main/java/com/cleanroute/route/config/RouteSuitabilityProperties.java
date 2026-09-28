package com.cleanroute.route.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;

/** Transparent demo suitability weights and thresholds; these are not safety limits. */
@Validated
@ConfigurationProperties(prefix = "app.routes.suitability")
public class RouteSuitabilityProperties {
    @DecimalMin("0.001")
    private double joggerMinimumDistanceMeters = 2000;
    @DecimalMin("0.001")
    private double joggerIdealMaximumDistanceMeters = 8000;
    @DecimalMin("0.001")
    private double joggerMaximumDistanceMeters = 12000;
    @DecimalMin("0.0")
    private double joggerMaximumPollutionExposure = 50;
    @DecimalMin("0.0")
    private double joggerMaximumCongestionFactor = 2.5;
    @DecimalMin("0.0")
    private double cyclistMaximumPollutionExposure = 50;
    @DecimalMin("0.0")
    private double cyclistMaximumCongestionFactor = 3.0;
    @DecimalMin("0.0")
    private double pollutionWeight = 0.5;
    @DecimalMin("0.0")
    private double trafficWeight = 0.3;
    @DecimalMin("0.0")
    private double distanceWeight = 0.2;
    private boolean preferGreenAreas = true;
    @DecimalMin("0.001")
    private double maximumElevationGainMeters = 250;

    public double getJoggerMinimumDistanceMeters() { return joggerMinimumDistanceMeters; }
    public void setJoggerMinimumDistanceMeters(double value) { joggerMinimumDistanceMeters = value; }
    public double getJoggerIdealMaximumDistanceMeters() { return joggerIdealMaximumDistanceMeters; }
    public void setJoggerIdealMaximumDistanceMeters(double value) { joggerIdealMaximumDistanceMeters = value; }
    public double getJoggerMaximumDistanceMeters() { return joggerMaximumDistanceMeters; }
    public void setJoggerMaximumDistanceMeters(double value) { joggerMaximumDistanceMeters = value; }
    public double getJoggerMaximumPollutionExposure() { return joggerMaximumPollutionExposure; }
    public void setJoggerMaximumPollutionExposure(double value) { joggerMaximumPollutionExposure = value; }
    public double getJoggerMaximumCongestionFactor() { return joggerMaximumCongestionFactor; }
    public void setJoggerMaximumCongestionFactor(double value) { joggerMaximumCongestionFactor = value; }
    public double getCyclistMaximumPollutionExposure() { return cyclistMaximumPollutionExposure; }
    public void setCyclistMaximumPollutionExposure(double value) { cyclistMaximumPollutionExposure = value; }
    public double getCyclistMaximumCongestionFactor() { return cyclistMaximumCongestionFactor; }
    public void setCyclistMaximumCongestionFactor(double value) { cyclistMaximumCongestionFactor = value; }
    public double getPollutionWeight() { return pollutionWeight; }
    public void setPollutionWeight(double value) { pollutionWeight = value; }
    public double getTrafficWeight() { return trafficWeight; }
    public void setTrafficWeight(double value) { trafficWeight = value; }
    public double getDistanceWeight() { return distanceWeight; }
    public void setDistanceWeight(double value) { distanceWeight = value; }
    @AssertTrue(message = "JOGGER pollution, traffic, and distance weights must have a finite positive total")
    public boolean isJoggerWeightTotalValid() {
        double total = pollutionWeight + trafficWeight + distanceWeight;
        return Double.isFinite(pollutionWeight) && Double.isFinite(trafficWeight) && Double.isFinite(distanceWeight)
                && Double.isFinite(total) && total > 0;
    }
    public boolean isPreferGreenAreas() { return preferGreenAreas; }
    public void setPreferGreenAreas(boolean value) { preferGreenAreas = value; }
    public double getMaximumElevationGainMeters() { return maximumElevationGainMeters; }
    public void setMaximumElevationGainMeters(double value) { maximumElevationGainMeters = value; }
}
