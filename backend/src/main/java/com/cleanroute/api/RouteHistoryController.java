package com.cleanroute.api;
import com.cleanroute.domain.RouteHistory; import com.cleanroute.repository.RouteHistoryRepository; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*; import java.time.Instant; import java.util.*;
@RestController @RequestMapping("/api/routes/history") public class RouteHistoryController {
 private final RouteHistoryRepository history; public RouteHistoryController(RouteHistoryRepository h){history=h;}
 public record HistoryResponse(String id,String originName,String destinationName,com.cleanroute.domain.TravelMode travelMode,com.cleanroute.domain.RoutePreference routePreference,Double pollutionScore,Integer estimatedTravelTimeSeconds,Double distanceMeters,Instant createdAt){}
 @GetMapping public List<HistoryResponse> list(Authentication a){return history.findAllByUserIdOrderByCreatedAtDesc((UUID)a.getPrincipal()).stream().map(r->new HistoryResponse(r.getId().toString(),r.getOriginName(),r.getDestinationName(),r.getTravelMode(),r.getRoutePreference(),r.getPollutionScore(),r.getEstimatedTravelTimeSeconds(),r.getDistanceMeters(),r.getCreatedAt())).toList();}
}
