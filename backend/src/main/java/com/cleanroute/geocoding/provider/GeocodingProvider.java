package com.cleanroute.geocoding.provider;

import com.cleanroute.geocoding.domain.GeocodingModels.LocationSearchResult;
import java.util.List;

public interface GeocodingProvider {
    String providerId();
    List<LocationSearchResult> search(String query);
}
