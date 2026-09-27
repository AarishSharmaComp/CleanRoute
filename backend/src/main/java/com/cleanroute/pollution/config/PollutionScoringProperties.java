package com.cleanroute.pollution.config;

import jakarta.validation.constraints.DecimalMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configurable reference points and weights for a comparative demo score, not health limits. */
@Validated
@ConfigurationProperties(prefix = "app.pollution.scoring")
public class PollutionScoringProperties {
    @DecimalMin("1.0") private double aqiIndexMax = 500.0;
    @DecimalMin("0.001") private double pm25Reference = 35.0;
    @DecimalMin("0.001") private double pm10Reference = 50.0;
    @DecimalMin("0.001") private double no2Reference = 40.0;
    @DecimalMin("0.001") private double so2Reference = 20.0;
    @DecimalMin("0.001") private double coReference = 4.0;
    @DecimalMin("0.001") private double o3Reference = 100.0;
    @DecimalMin("0.001") private double pollutionWeight = 0.75;
    @DecimalMin("0.0") private double trafficWeight = 0.15;
    @DecimalMin("0.0") private double weatherWeight = 0.10;
    @DecimalMin("0.001") private double windReferenceMps = 10.0;

    public double getAqiIndexMax() { return aqiIndexMax; }
    public void setAqiIndexMax(double value) { aqiIndexMax = value; }
    public double getPm25Reference() { return pm25Reference; }
    public void setPm25Reference(double value) { pm25Reference = value; }
    public double getPm10Reference() { return pm10Reference; }
    public void setPm10Reference(double value) { pm10Reference = value; }
    public double getNo2Reference() { return no2Reference; }
    public void setNo2Reference(double value) { no2Reference = value; }
    public double getSo2Reference() { return so2Reference; }
    public void setSo2Reference(double value) { so2Reference = value; }
    public double getCoReference() { return coReference; }
    public void setCoReference(double value) { coReference = value; }
    public double getO3Reference() { return o3Reference; }
    public void setO3Reference(double value) { o3Reference = value; }
    public double getPollutionWeight() { return pollutionWeight; }
    public void setPollutionWeight(double value) { pollutionWeight = value; }
    public double getTrafficWeight() { return trafficWeight; }
    public void setTrafficWeight(double value) { trafficWeight = value; }
    public double getWeatherWeight() { return weatherWeight; }
    public void setWeatherWeight(double value) { weatherWeight = value; }
    public double getWindReferenceMps() { return windReferenceMps; }
    public void setWindReferenceMps(double value) { windReferenceMps = value; }
}
