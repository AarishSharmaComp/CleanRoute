package com.cleanroute.route.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Availability is not model accuracy: percentages use represented route distance, not confidence. */
public record EnvironmentalQuality(double distanceCoveragePercent, double pollutantCoveragePercent,
        List<String> missingPollutants, Map<String, Integer> issues, String dataType, boolean generated,
        boolean comparisonEligible, String comparisonBasis, Instant earliestSampleAt, Instant latestSampleAt) {
    public EnvironmentalQuality {
        missingPollutants = List.copyOf(missingPollutants);
        issues = Map.copyOf(issues);
    }

    /** Shared by ranking and notifications. Missing legacy evidence fails closed. */
    public static boolean comparable(List<EnvironmentalQuality> evidence) {
        if (evidence.size() < 2) return false;
        EnvironmentalQuality first = evidence.getFirst();
        if (first == null || !first.comparisonEligible() || first.comparisonBasis() == null) return false;
        return evidence.stream().allMatch(item -> item != null && item.comparisonEligible()
                && first.comparisonBasis().equals(item.comparisonBasis()));
    }
}
