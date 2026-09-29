package com.cleanroute.geocoding.domain;

public final class GeocodingModels {
    private GeocodingModels() {}

    public record LocationSearchResult(
            String name,
            String displayName,
            String context,
            double latitude,
            double longitude,
            boolean supportedArea
    ) {}
}
