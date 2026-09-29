package com.cleanroute.geocoding.service;

import com.cleanroute.geocoding.config.GeocodingProperties;
import com.cleanroute.geocoding.domain.GeocodingModels.LocationSearchResult;
import com.cleanroute.geocoding.provider.GeocodingProvider;
import com.cleanroute.observation.provider.ProviderFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class GeocodingService {
    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);
    private final GeocodingProperties properties;
    private final List<GeocodingProvider> providers;

    public GeocodingService(GeocodingProperties properties,
                            List<GeocodingProvider> providers) {
        this.properties = properties;
        this.providers = providers;
    }

    public List<LocationSearchResult> search(String query) {
        if (query == null || query.trim().length() < 3)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query must contain at least 3 characters");
        GeocodingProvider active = providers.stream()
                .filter(candidate -> candidate.providerId().equalsIgnoreCase(properties.getProvider())
                        || ("mock".equalsIgnoreCase(properties.getProvider()) && candidate.providerId().equals("mock-geocoding"))
                        || ("photon".equalsIgnoreCase(properties.getProvider()) && candidate.providerId().equals("photon-geocoding")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Configured geocoding provider is unavailable"));

        try {
            return active.search(query);
        } catch (ProviderFailureException failure) {
            log.warn("Geocoding provider {} failure: {} ({})", active.providerId(), failure.getType(), failure.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Location search provider is temporarily unavailable");
        } catch (Exception ex) {
            log.warn("Unexpected geocoding provider failure ({})", ex.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Location search provider is temporarily unavailable");
        }
    }
}
