package com.cleanroute.observation;

import com.cleanroute.observation.config.ObservationProperties;
import com.cleanroute.observation.domain.ObservationModels.*;
import com.cleanroute.observation.provider.*;
import com.cleanroute.observation.repository.ObservationRepository;
import com.cleanroute.observation.repository.ProviderFreshnessRepository;
import com.cleanroute.observation.service.ObservationIngestionScheduler;
import com.cleanroute.observation.service.ObservationProviderCallExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;

class ObservationFailureIsolationTest {
    private final ObservationRepository repository = mock(ObservationRepository.class);
    private final ProviderFreshnessRepository freshness = mock(ProviderFreshnessRepository.class);
    private final ObservationProperties properties = new ObservationProperties();
    private final ObservationProviderCallExecutor calls = new ObservationProviderCallExecutor(properties);

    @AfterEach void closeExecutor() { calls.close(); }

    @Test void temporaryAqiFailureDoesNotPreventWeatherOrTraffic() {
        AQIProvider aqi = mock(AQIProvider.class);
        when(aqi.providerId()).thenReturn("aqi-test");
        when(aqi.observations(any(), any())).thenThrow(ProviderFailureException.temporary());
        WeatherProvider weather = weatherProvider("weather-test");
        TrafficProvider traffic = trafficProvider("traffic-test");

        scheduler(aqi, weather, traffic).scheduledIngestion();

        verify(repository, times(3)).save(any(WeatherObservation.class));
        verify(repository, times(3)).save(any(TrafficObservation.class));
        verify(freshness, times(3)).recordFailure(eq("aqi-test"), anyString(), any(), eq("TEMPORARY_FAILURE"));
    }

    @Test void rateLimitBackoffSkipsFurtherCallsToThatProviderButContinuesOtherProviders() {
        AQIProvider aqi = mock(AQIProvider.class);
        when(aqi.providerId()).thenReturn("aqi-limited");
        when(aqi.observations(any(), any())).thenThrow(ProviderFailureException.rateLimited(Duration.ofHours(1)));

        scheduler(aqi, weatherProvider("weather-test"), trafficProvider("traffic-test")).scheduledIngestion();

        verify(aqi, times(1)).observations(any(), any());
        verify(repository, times(3)).save(any(WeatherObservation.class));
        verify(repository, times(3)).save(any(TrafficObservation.class));
        verify(freshness).recordFailure(eq("aqi-limited"), anyString(), any(), eq("RATE_LIMITED"));
    }

    @Test void timedOutProviderDoesNotBlockSchedulerOrOtherProviders() {
        properties.setProviderTimeoutMs(100);
        CountDownLatch release = new CountDownLatch(1);
        AQIProvider aqi = mock(AQIProvider.class);
        when(aqi.providerId()).thenReturn("aqi-hanging");
        when(aqi.observations(any(), any())).thenAnswer(invocation -> {
            while (release.getCount() != 0) {
                try { release.await(20, TimeUnit.MILLISECONDS); }
                catch (InterruptedException ignored) { /* provider does not honor cancellation */ }
            }
            return List.of();
        });
        long started = System.nanoTime();
        try {
            scheduler(aqi, weatherProvider("weather-test"), trafficProvider("traffic-test")).scheduledIngestion();
        } finally {
            release.countDown();
        }
        org.assertj.core.api.Assertions.assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(2_000);
        verify(repository, times(3)).save(any(WeatherObservation.class));
        verify(repository, times(3)).save(any(TrafficObservation.class));
        verify(freshness, times(3)).recordFailure(eq("aqi-hanging"), anyString(), any(), eq("TIMEOUT"));
    }

    @Test void mismatchedProviderResponseIsRejectedBeforePersistence() {
        AQIProvider aqi = mock(AQIProvider.class);
        when(aqi.providerId()).thenReturn("aqi-requested");
        when(aqi.observations(any(), any())).thenAnswer(invocation -> List.of(
                new PollutionObservation("demo-delhi-central", invocation.getArgument(1), 40, 10.0, null, null, null, null, null, "aqi-other", true)));

        scheduler(aqi, weatherProvider("weather-test"), trafficProvider("traffic-test")).scheduledIngestion();

        verify(repository, never()).save(any(PollutionObservation.class));
        verify(freshness, times(3)).recordFailure(eq("aqi-requested"), anyString(), any(), eq("INVALID_RESPONSE"));
    }

    @Test void mismatchedCellResponseIsRejectedBeforePersistence() {
        AQIProvider aqi = mock(AQIProvider.class);
        when(aqi.providerId()).thenReturn("aqi-test");
        when(aqi.observations(any(), any())).thenAnswer(invocation -> List.of(
                new PollutionObservation("different-cell", invocation.getArgument(1), 40, 10.0, null, null, null, null, null, "aqi-test", true)));

        scheduler(aqi, weatherProvider("weather-test"), trafficProvider("traffic-test")).scheduledIngestion();

        verify(repository, never()).save(any(PollutionObservation.class));
    }

    @Test void invalidProviderResponseDoesNotPreventOtherProviders() {
        AQIProvider aqi = mock(AQIProvider.class);
        when(aqi.providerId()).thenReturn("aqi-test");
        when(aqi.observations(any(), any())).thenAnswer(invocation -> List.of(
                new PollutionObservation("demo-delhi-central", invocation.getArgument(1), 600, 10.0, null, null, null, null, null, "aqi-test", true)));
        WeatherProvider weather = weatherProvider("weather-test");
        TrafficProvider traffic = trafficProvider("traffic-test");

        scheduler(aqi, weather, traffic).scheduledIngestion();

        verify(repository, never()).save(any(PollutionObservation.class));
        verify(repository, times(3)).save(any(WeatherObservation.class));
        verify(repository, times(3)).save(any(TrafficObservation.class));
    }

    @Test void nonFiniteWeatherValueIsNotPersisted() {
        AQIProvider aqi = validAqiProvider();
        WeatherProvider weather = mock(WeatherProvider.class);
        when(weather.providerId()).thenReturn("weather-invalid");
        when(weather.observation(any(), any())).thenAnswer(invocation -> {
            GeographicCell cell = invocation.getArgument(0); Instant at = invocation.getArgument(1);
            return new WeatherObservation(cell.cellId(), at, Double.POSITIVE_INFINITY, null, null, null, null, null, "weather-invalid", true);
        });

        scheduler(aqi, weather, trafficProvider("traffic-test")).scheduledIngestion();

        verify(repository, never()).save(any(WeatherObservation.class));
        verify(repository, times(3)).save(any(PollutionObservation.class));
        verify(freshness, times(3)).recordFailure(eq("weather-invalid"), anyString(), any(), eq("INVALID_RESPONSE"));
    }

    @Test void nonFiniteTrafficValueIsNotPersisted() {
        TrafficProvider traffic = mock(TrafficProvider.class);
        when(traffic.providerId()).thenReturn("traffic-invalid");
        when(traffic.observation(any(), any())).thenAnswer(invocation -> {
            GeographicCell cell = invocation.getArgument(0); Instant at = invocation.getArgument(1);
            return new TrafficObservation(cell.cellId(), at, "LOW", Double.NaN, 30.0, "traffic-invalid", true);
        });

        scheduler(validAqiProvider(), weatherProvider("weather-test"), traffic).scheduledIngestion();

        verify(repository, never()).save(any(TrafficObservation.class));
        verify(repository, times(3)).save(any(PollutionObservation.class));
        verify(freshness, times(3)).recordFailure(eq("traffic-invalid"), anyString(), any(), eq("INVALID_RESPONSE"));
    }

    private ObservationIngestionScheduler scheduler(AQIProvider aqi, WeatherProvider weather, TrafficProvider traffic) {
        return new ObservationIngestionScheduler(aqi, weather, traffic, repository, freshness, calls, properties);
    }

    private AQIProvider validAqiProvider() {
        AQIProvider provider = mock(AQIProvider.class);
        when(provider.providerId()).thenReturn("aqi-test");
        when(provider.observations(any(), any())).thenAnswer(invocation -> {
            GeographicCell cell = invocation.getArgument(0); Instant at = invocation.getArgument(1);
            return List.of(new PollutionObservation(cell.cellId(), at, 40, 10.0, null, null, null, null, null, "aqi-test", true));
        });
        return provider;
    }

    private WeatherProvider weatherProvider(String id) {
        WeatherProvider provider = mock(WeatherProvider.class);
        when(provider.providerId()).thenReturn(id);
        when(provider.observation(any(), any())).thenAnswer(invocation -> {
            GeographicCell cell = invocation.getArgument(0); Instant at = invocation.getArgument(1);
            return new WeatherObservation(cell.cellId(), at, 20.0, 50.0, 2.0, 90.0, 0.0, "CLEAR", id, true);
        });
        return provider;
    }

    private TrafficProvider trafficProvider(String id) {
        TrafficProvider provider = mock(TrafficProvider.class);
        when(provider.providerId()).thenReturn(id);
        when(provider.observation(any(), any())).thenAnswer(invocation -> {
            GeographicCell cell = invocation.getArgument(0); Instant at = invocation.getArgument(1);
            return new TrafficObservation(cell.cellId(), at, "LOW", 1.5, 30.0, id, true);
        });
        return provider;
    }
}
