package com.cleanroute.dashboard.api;

import com.cleanroute.dashboard.domain.DashboardModels.DashboardResponse;
import com.cleanroute.dashboard.service.DashboardService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboard;
    public DashboardController(DashboardService dashboard) { this.dashboard = dashboard; }
    @GetMapping public DashboardResponse get(Authentication authentication) {
        return dashboard.get((UUID) authentication.getPrincipal());
    }
}
