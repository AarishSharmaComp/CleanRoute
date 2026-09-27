package com.cleanroute.observation;

import com.cleanroute.observation.domain.ObservationModels.*;
import com.cleanroute.observation.provider.*;
import com.cleanroute.observation.service.ObservationNormalizer;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

class ObservationProviderTest {
    private final GeographicCell cell = new GeographicCell("cell-a", 28.6, 77.2);
    private final Instant time = Instant.parse("2026-09-27T10:07:55Z");
    @Test void mockAqiIsDeterministicLabeledAndLeavesUnavailableValuesNull() {
        var provider = new MockAQIProvider(); var a = provider.observations(cell,time).getFirst();
        assertThat(provider.observations(cell,time).getFirst()).isEqualTo(a);
        assertThat(a.generated()).isTrue(); assertThat(a.provider()).startsWith("mock-demo");
        assertThat(a.aqi()).isNotNull(); assertThat(a.pm25()).isNotNull(); assertThat(a.pm10()).isNotNull();
        assertThat(a.no2()).isNotNull(); assertThat(a.o3()).isNotNull(); assertThat(a.so2()).isNull(); assertThat(a.co()).isNull();
    }
    @Test void mockWeatherAndTrafficAreDeterministicGeneratedData() {
        var weather = new MockWeatherProvider(); var traffic = new MockTrafficProvider();
        assertThat(weather.observation(cell,time)).isEqualTo(weather.observation(cell,time));
        assertThat(weather.observation(cell,time).generated()).isTrue();
        assertThat(weather.observation(cell,time).condition()).isNotBlank();
        assertThat(traffic.observation(cell,time)).isEqualTo(traffic.observation(cell,time));
        assertThat(traffic.observation(cell,time).generated()).isTrue();
        assertThat(traffic.observation(cell,time).congestionFactor()).isBetween(1.0,5.0);
    }
    @Test void normalizationAlignsTimeAndDoesNotFillMissingPollutants() {
        var input = new PollutionObservation("cell-a",time,42,12.0,20.0,null,null,null,8.0,"provider",true);
        var output = ObservationNormalizer.pollution(input, "provider", "cell-a", time);
        assertThat(output.observedAt()).isEqualTo(Instant.parse("2026-09-27T10:00:00Z"));
        assertThat(output.no2()).isNull(); assertThat(output.co()).isNull(); assertThat(output.generated()).isTrue();
    }
    @Test void normalizationRejectsEmptyAndInvalidResponses() {
        assertThatIllegalArgumentException().isThrownBy(() -> ObservationNormalizer.pollution(new PollutionObservation("c",time,null,null,null,null,null,null,null,"p",false), "p", "c", time));
        assertThatIllegalArgumentException().isThrownBy(() -> ObservationNormalizer.pollution(new PollutionObservation("c",time,600,1.0,null,null,null,null,null,"p",false), "p", "c", time));
    }
    @Test void weatherRejectsNonFiniteMeasurementsAndKeepsMissingValuesNull() {
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            var badTemperature = new WeatherObservation("cell-a", time, value, null, null, null, null, null, "weather", false);
            var badDirection = new WeatherObservation("cell-a", time, null, null, null, value, null, "CLEAR", "weather", false);
            assertThatIllegalArgumentException().isThrownBy(() -> ObservationNormalizer.weather(badTemperature, "weather", "cell-a", time));
            assertThatIllegalArgumentException().isThrownBy(() -> ObservationNormalizer.weather(badDirection, "weather", "cell-a", time));
        }
        var missing = ObservationNormalizer.weather(new WeatherObservation("cell-a",time,21.0,null,null,null,null,null,"weather",false), "weather", "cell-a", time);
        assertThat(missing.humidityPercent()).isNull();
    }
    @Test void trafficRejectsNonFiniteMeasurements() {
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            var badFactor = new TrafficObservation("cell-a",time,"MODERATE",value,25.0,"traffic",false);
            var badSpeed = new TrafficObservation("cell-a",time,"MODERATE",2.0,value,"traffic",false);
            assertThatIllegalArgumentException().isThrownBy(() -> ObservationNormalizer.traffic(badFactor, "traffic", "cell-a", time));
            assertThatIllegalArgumentException().isThrownBy(() -> ObservationNormalizer.traffic(badSpeed, "traffic", "cell-a", time));
        }
    }
    @Test void rejectsProviderCellAndUnreasonablyFutureTimestampMismatches() {
        var observation = new PollutionObservation("cell-a",time,42,12.0,null,null,null,null,null,"provider",false);
        assertThatIllegalArgumentException().isThrownBy(() -> ObservationNormalizer.pollution(observation, "other-provider", "cell-a", time));
        assertThatIllegalArgumentException().isThrownBy(() -> ObservationNormalizer.pollution(observation, "provider", "other-cell", time));
        var future = new PollutionObservation("cell-a",time.plus(Duration.ofMinutes(3)),42,12.0,null,null,null,null,null,"provider",false);
        assertThatIllegalArgumentException().isThrownBy(() -> ObservationNormalizer.pollution(future, "provider", "cell-a", time));
        var allowedClockSkew = new PollutionObservation("cell-a",time.plus(Duration.ofMinutes(1)),42,12.0,null,null,null,null,null,"provider",false);
        assertThatNoException().isThrownBy(() -> ObservationNormalizer.pollution(allowedClockSkew, "provider", "cell-a", time));
        assertThatIllegalArgumentException().isThrownBy(() -> ObservationNormalizer.weather(
                new WeatherObservation("cell-a",time.plus(Duration.ofMinutes(3)),20.0,null,null,null,null,null,"weather",false), "weather", "cell-a", time));
        assertThatIllegalArgumentException().isThrownBy(() -> ObservationNormalizer.traffic(
                new TrafficObservation("cell-a",time.plus(Duration.ofMinutes(3)),"LOW",1.2,null,"traffic",false), "traffic", "cell-a", time));
    }
}
