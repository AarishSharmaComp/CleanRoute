package com.cleanroute.geocoding.provider;

import com.cleanroute.geocoding.domain.GeocodingModels.LocationSearchResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class MockGeocodingProvider implements GeocodingProvider {
    private static final List<LocationSearchResult> DEMO_PLACES = List.of(
            new LocationSearchResult("Central Delhi", "Central Delhi, Delhi, India", "Delhi, India", 28.6139, 77.2090, true),
            new LocationSearchResult("South Delhi", "South Delhi, Delhi, India", "Delhi, India", 28.5355, 77.2100, true),
            new LocationSearchResult("North Delhi", "North Delhi, Delhi, India", "Delhi, India", 28.7041, 77.1025, true),
            new LocationSearchResult("Connaught Place", "Connaught Place, Central Delhi, Delhi, India", "Central Delhi, Delhi, India", 28.6315, 77.2167, true),
            new LocationSearchResult("India Gate", "India Gate, New Delhi, Delhi, India", "New Delhi, Delhi, India", 28.6129, 77.2295, true),
            new LocationSearchResult("Hauz Khas", "Hauz Khas, South Delhi, Delhi, India", "South Delhi, Delhi, India", 28.5494, 77.2001, true),
            new LocationSearchResult("Civil Lines", "Civil Lines, North Delhi, Delhi, India", "North Delhi, Delhi, India", 28.6814, 77.2227, true),
            new LocationSearchResult("Noida", "Noida, Gautam Buddha Nagar, Uttar Pradesh, India", "Gautam Buddha Nagar, Uttar Pradesh, India", 28.5355, 77.3910, true),
            new LocationSearchResult("Gurgaon", "Gurgaon, Gurugram, Haryana, India", "Gurugram, Haryana, India", 28.4595, 77.0266, true),
            new LocationSearchResult("Mumbai", "Mumbai, Maharashtra, India", "Maharashtra, India", 19.0760, 72.8777, false),
            new LocationSearchResult("Bengaluru", "Bengaluru, Karnataka, India", "Karnataka, India", 12.9716, 77.5946, false),
            new LocationSearchResult("London", "London, Greater London, England, United Kingdom", "Greater London, England, United Kingdom", 51.5074, -0.1278, false),
            new LocationSearchResult("New York", "New York, New York, United States", "New York, United States", 40.7128, -74.0060, false)
    );

    @Override
    public String providerId() {
        return "mock-geocoding";
    }

    @Override
    public List<LocationSearchResult> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.length() < 3) return List.of();

        List<LocationSearchResult> matched = DEMO_PLACES.stream()
                .filter(p -> p.name().toLowerCase(Locale.ROOT).contains(q)
                        || p.displayName().toLowerCase(Locale.ROOT).contains(q))
                .limit(6)
                .toList();

        return matched;
    }
}
