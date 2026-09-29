package com.cleanroute.geocoding.api;

import com.cleanroute.geocoding.domain.GeocodingModels.LocationSearchResult;
import com.cleanroute.geocoding.service.GeocodingService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/places")
public class PlaceSearchController {
    private final GeocodingService geocoding;

    public PlaceSearchController(GeocodingService geocoding) {
        this.geocoding = geocoding;
    }

    @GetMapping("/search")
    public List<LocationSearchResult> search(@RequestParam(name = "q", required = false) String query) {
        return geocoding.search(query);
    }
}
