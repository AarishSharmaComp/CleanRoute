package com.cleanroute.route.api;

import com.cleanroute.route.domain.RoutePlanningModels.CalculationRequest;
import com.cleanroute.route.domain.RoutePlanningModels.CalculationResult;
import com.cleanroute.route.service.RouteService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

@RestController
@RequestMapping("/api/routes")
public class RoutePlanningController {
    private final RouteService routes;
    public RoutePlanningController(RouteService routes) { this.routes = routes; }

    @PostMapping("/calculate")
    public CalculationResult calculate(@RequestBody CalculationRequest request, Authentication authentication) {
        return routes.calculate(request, (UUID) authentication.getPrincipal());
    }

    @GetMapping("/{id}")
    public JsonNode get(@PathVariable UUID id, Authentication authentication) {
        return routes.get(id, (UUID) authentication.getPrincipal());
    }
}
