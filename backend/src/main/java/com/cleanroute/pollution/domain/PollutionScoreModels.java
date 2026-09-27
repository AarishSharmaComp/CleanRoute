package com.cleanroute.pollution.domain;

import java.time.Instant;
import java.util.List;

public final class PollutionScoreModels {
    private PollutionScoreModels() {}

    public record PollutantComponent(String pollutant, Double measuredValue, String unit,
                                     Double referenceValue, Double score, boolean includedInBurden) {}

    public record PollutionAssessment(double burdenScore, int availablePollutantCount,
                                      int pollutantCoveragePercent, boolean usedAqiFallback,
                                      List<PollutantComponent> pollutants, List<String> missingPollutants,
                                      String provider, boolean generated) {}

    public record ScoreComponent(String name, double score, double configuredWeight,
                                 double appliedWeight, String explanation) {}

    public record PollutionScore(String cellId, Instant observedAt, double score,
                                 String direction, String methodology,
                                 double pollutionBurdenScore, double durationFactor,
                                 double distanceFactor, double modeFactor,
                                 List<ScoreComponent> components,
                                 List<PollutantComponent> pollutants,
                                 int pollutantCoveragePercent, List<String> missingPollutants,
                                 String pollutionProvider, String weatherProvider, String trafficProvider,
                                 boolean generated, List<String> caveats) {}
}
