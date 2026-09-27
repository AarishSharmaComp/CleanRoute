package com.cleanroute.observation.service;

import com.cleanroute.observation.config.ObservationProperties;
import com.cleanroute.observation.domain.ObservationModels.*;
import com.cleanroute.observation.provider.*;
import com.cleanroute.observation.repository.ObservationRepository;
import com.cleanroute.observation.repository.ProviderFreshnessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ObservationIngestionScheduler {
    private static final Logger log = LoggerFactory.getLogger(ObservationIngestionScheduler.class);
    private static final List<GeographicCell> CELLS = List.of(
            new GeographicCell("demo-delhi-central", 28.6139, 77.2090),
            new GeographicCell("demo-delhi-south", 28.5355, 77.2100),
            new GeographicCell("demo-delhi-north", 28.7041, 77.1025));
    private final AQIProvider aqi;
    private final WeatherProvider weather;
    private final TrafficProvider traffic;
    private final ObservationRepository repository;
    private final ProviderFreshnessRepository freshness;
    private final ObservationProviderCallExecutor callExecutor;
    private final ObservationProperties properties;
    private final ConcurrentMap<String, Instant> rateLimitBackoffUntil = new ConcurrentHashMap<>();

    public ObservationIngestionScheduler(AQIProvider aqi, WeatherProvider weather, TrafficProvider traffic,
            ObservationRepository repository, ProviderFreshnessRepository freshness,
            ObservationProviderCallExecutor callExecutor, ObservationProperties properties) {
        this.aqi = aqi;
        this.weather = weather;
        this.traffic = traffic;
        this.repository = repository;
        this.freshness = freshness;
        this.callExecutor = callExecutor;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDemoHistory() {
        Instant end = floorQuarterHour(Instant.now());
        for (GeographicCell cell : CELLS) repository.saveCell(cell);
        for (int i = 7 * 24 * 4; i >= 0; i--) ingestAt(end.minus(i * 15L, ChronoUnit.MINUTES), i == 0);
        log.info("Completed seven-day generated observation seed attempt for {} cells", CELLS.size());
    }

    @Scheduled(fixedDelayString = "${app.observations.ingestion-interval-ms:900000}",
            initialDelayString = "${app.observations.initial-delay-ms:900000}")
    public void scheduledIngestion() { ingestAt(floorQuarterHour(Instant.now()), true); }

    private void ingestAt(Instant timestamp, boolean recordFreshness) {
        for (GeographicCell cell : CELLS) {
            runIndependently(aqi.providerId(), cell, recordFreshness, () -> {
                List<PollutionObservation> response = callExecutor.call(aqi.providerId(), () -> aqi.observations(cell, timestamp));
                if (response == null || response.isEmpty()) throw new IllegalArgumentException("Empty AQI response");
                response.stream().map(o -> ObservationNormalizer.pollution(o, aqi.providerId(), cell.cellId(), Instant.now()))
                        .forEach(repository::save);
            });
            runIndependently(weather.providerId(), cell, recordFreshness, () -> {
                WeatherObservation response = callExecutor.call(weather.providerId(), () -> weather.observation(cell, timestamp));
                repository.save(ObservationNormalizer.weather(response, weather.providerId(), cell.cellId(), Instant.now()));
            });
            runIndependently(traffic.providerId(), cell, recordFreshness, () -> {
                TrafficObservation response = callExecutor.call(traffic.providerId(), () -> traffic.observation(cell, timestamp));
                repository.save(ObservationNormalizer.traffic(response, traffic.providerId(), cell.cellId(), Instant.now()));
            });
        }
    }

    private void runIndependently(String providerId, GeographicCell cell, boolean recordFreshness, Runnable task) {
        Instant now = Instant.now();
        Instant blockedUntil = rateLimitBackoffUntil.get(providerId);
        if (blockedUntil != null && now.isBefore(blockedUntil)) {
            log.debug("Skipping {} for {} while provider rate-limit backoff is active", providerId, cell.cellId());
            return;
        }
        try {
            task.run();
            rateLimitBackoffUntil.remove(providerId);
            if (recordFreshness) recordSuccess(providerId, cell.cellId(), Instant.now());
        } catch (ProviderFailureException failure) {
            if (failure.getType() == ProviderFailureException.Type.RATE_LIMITED) {
                rateLimitBackoffUntil.put(providerId, Instant.now().plus(rateLimitDelay(failure)));
            }
            if (recordFreshness || failure.getType() == ProviderFailureException.Type.RATE_LIMITED)
                recordFailure(providerId, cell.cellId(), failure.getType().name());
            log.warn("{} ingestion {} for {}", providerId, failure.getType(), cell.cellId());
        } catch (IllegalArgumentException invalid) {
            if (recordFreshness) recordFailure(providerId, cell.cellId(), "INVALID_RESPONSE");
            log.warn("{} ingestion rejected an invalid response for {}", providerId, cell.cellId());
        } catch (Exception failure) {
            if (recordFreshness) recordFailure(providerId, cell.cellId(), ProviderFailureException.Type.TEMPORARY_FAILURE.name());
            log.warn("{} ingestion failed for {} ({})", providerId, cell.cellId(), failure.getClass().getSimpleName());
        }
    }

    private Duration rateLimitDelay(ProviderFailureException failure) {
        Duration configuredMaximum = Duration.ofMillis(properties.getRateLimitBackoffMs());
        Duration requested = failure.getRetryAfter();
        if (requested == null || requested.isNegative() || requested.isZero()) return configuredMaximum;
        return requested.compareTo(configuredMaximum) > 0 ? configuredMaximum : requested;
    }

    private void recordSuccess(String providerId, String cellId, Instant at) {
        try { freshness.recordSuccess(providerId, cellId, at); }
        catch (Exception failure) { log.warn("Could not record freshness for {} at {}", providerId, cellId); }
    }

    private void recordFailure(String providerId, String cellId, String type) {
        try { freshness.recordFailure(providerId, cellId, Instant.now(), type); }
        catch (Exception failure) { log.warn("Could not record failed freshness for {} at {}", providerId, cellId); }
    }

    static Instant floorQuarterHour(Instant t) { return Instant.ofEpochSecond(Math.floorDiv(t.getEpochSecond(), 900) * 900); }
}
