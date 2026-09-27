package com.cleanroute.pollution;

import com.cleanroute.observation.domain.ObservationModels.PollutionObservation;
import com.cleanroute.pollution.provider.HistoricalAverageForecastProvider;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoricalAverageForecastProviderTest {
    private final HistoricalAverageForecastProvider provider = new HistoricalAverageForecastProvider();

    @Test void aggregatesMatchingWeekdayQuarterHourWithRecentWeightAndPreservesMissingPollutants() {
        Instant target = Instant.parse("2026-09-28T12:00:00Z");
        var older = new PollutionObservation("cell", target.minusSeconds(14 * 86400), 90, 30.0, null,
                null, null, null, null, "demo", true);
        var recent = new PollutionObservation("cell", target.minusSeconds(7 * 86400), 30, 10.0, null,
                null, null, null, null, "demo", true);
        var result = provider.predict("cell", target, List.of(older, recent));
        assertThat(result.pm25()).isBetween(10.0, 20.0);
        assertThat(result.aqi()).isBetween(30, 60);
        assertThat(result.pm10()).isNull();
        assertThat(result.sampleCount()).isEqualTo(2);
        assertThat(result.quality()).isIn("LOW", "MEDIUM");
        assertThat(result.sourceGenerated()).isTrue();
    }

    @Test void sparseHistoryUsesTimeOfDayFallbackAndReportsLowQuality() {
        Instant target = Instant.parse("2026-09-28T12:00:00Z");
        var result = provider.predict("cell", target, List.of(new PollutionObservation("cell",
                Instant.parse("2026-09-27T12:00:00Z"), 100, 20.0, null, null, null, null,
                null, "observed", false)));
        assertThat(result.pm25()).isEqualTo(20.0);
        assertThat(result.quality()).isEqualTo("LOW");
        assertThat(result.qualityScore()).isBetween(0, 39);
        assertThat(result.sourceGenerated()).isFalse();
    }

    @Test void refusesMisalignedTargetsAndEmptyHistory() {
        Instant aligned = Instant.parse("2026-09-28T12:00:00Z");
        assertThatThrownBy(() -> provider.predict("cell", aligned.plusSeconds(1), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.predict("cell", aligned, List.of()))
                .isInstanceOf(HistoricalAverageForecastProvider.ForecastUnavailableException.class);
    }

    @Test void mixedGeneratedAndObservedHistoryIsLabeledAsUsingGeneratedInputs() {
        Instant target = Instant.parse("2026-09-28T12:00:00Z");
        var rows = List.of(
                new PollutionObservation("cell", target.minusSeconds(7 * 86400), null, 12.0, null,
                        null, null, null, null, "real", false),
                new PollutionObservation("cell", target.minusSeconds(14 * 86400), null, 18.0, null,
                        null, null, null, null, "demo", true));
        assertThat(provider.predict("cell", target, rows).sourceGenerated()).isTrue();
    }

    @Test void multipleProvidersAtOneIntervalCountAsOneHistoricalSample() {
        Instant target = Instant.parse("2026-09-28T12:00:00Z");
        var rows = List.of(
                new PollutionObservation("cell", target.minusSeconds(7 * 86400), null, 20.0, null,
                        null, null, null, null, "provider-a", false),
                new PollutionObservation("cell", target.minusSeconds(7 * 86400), null, 40.0, null,
                        null, null, null, null, "provider-b", true),
                new PollutionObservation("cell", target.minusSeconds(14 * 86400), null, 100.0, null,
                        null, null, null, null, "provider-a", false));

        var result = provider.predict("cell", target, rows);

        assertThat(result.sampleCount()).isEqualTo(2);
        assertThat(result.pm25()).isBetween(62.0, 64.0);
        assertThat(result.pm10()).isNull();
        assertThat(result.sourceGenerated()).isTrue();
        assertThat(provider.predict("cell", target, List.of(rows.get(2), rows.get(1), rows.get(0))))
                .isEqualTo(result);
    }
}
