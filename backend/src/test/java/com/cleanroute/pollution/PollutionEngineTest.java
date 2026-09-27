package com.cleanroute.pollution;

import com.cleanroute.observation.domain.ObservationModels.PollutionObservation;
import com.cleanroute.pollution.config.PollutionScoringProperties;
import com.cleanroute.pollution.service.PollutionEngine;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;

class PollutionEngineTest {
    private final PollutionScoringProperties properties = new PollutionScoringProperties();
    private final PollutionEngine engine = new PollutionEngine(properties);
    private final Instant time = Instant.parse("2026-09-27T09:00:00Z");

    @Test void normalizesAvailableConcentrationsAndLeavesMissingMeasurementsOutOfTheMean() {
        var assessment = engine.assess(new PollutionObservation("cell", time, 80, 17.5, 100.0,
                null, null, null, null, "provider", true));
        assertThat(assessment.burdenScore()).isEqualTo(75.0);
        assertThat(assessment.availablePollutantCount()).isEqualTo(2);
        assertThat(assessment.pollutantCoveragePercent()).isEqualTo(33);
        assertThat(assessment.missingPollutants()).containsExactly("NO2", "SO2", "CO", "O3");
        assertThat(assessment.pollutants()).filteredOn(c -> c.score() != null && !c.pollutant().equals("AQI"))
                .extracting(c -> c.includedInBurden()).containsOnly(true);
        assertThat(assessment.generated()).isTrue();
    }

    @Test void usesProviderAqiOnlyWhenAllIndividualPollutantsAreMissing() {
        var assessment = engine.assess(new PollutionObservation("cell", time, 250, null, null,
                null, null, null, null, "provider", false));
        assertThat(assessment.burdenScore()).isEqualTo(50.0);
        assertThat(assessment.usedAqiFallback()).isTrue();
        assertThat(assessment.pollutants()).filteredOn(c -> c.pollutant().equals("AQI"))
                .singleElement().satisfies(c -> assertThat(c.includedInBurden()).isTrue());
    }

    @Test void refusesToInventABurdenWhenEveryMeasurementIsMissing() {
        assertThatThrownBy(() -> engine.assess(new PollutionObservation("cell", time, null, null, null,
                null, null, null, null, "provider", true)))
                .isInstanceOf(PollutionEngine.InsufficientPollutionDataException.class);
    }

    @Test void rejectsNonFiniteNegativeAndOutOfScaleInputs() {
        assertThatThrownBy(() -> engine.assess(new PollutionObservation("cell", time, 10, Double.NaN, null,
                null, null, null, null, "provider", true))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.assess(new PollutionObservation("cell", time, 10, Double.POSITIVE_INFINITY, null,
                null, null, null, null, "provider", true))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.assess(new PollutionObservation("cell", time, 10, -1.0, null,
                null, null, null, null, "provider", true))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.assess(new PollutionObservation("cell", time, 501, null, null,
                null, null, null, null, "provider", true))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void usesConfiguredPollutantReferenceValues() {
        properties.setPm25Reference(70.0);
        var assessment = engine.assess(new PollutionObservation("cell", time, null, 17.5, null,
                null, null, null, null, "provider", false));
        assertThat(assessment.burdenScore()).isEqualTo(25.0);
    }
}
