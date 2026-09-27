package com.cleanroute.api;
import com.cleanroute.domain.SavedPlace; import com.cleanroute.repository.SavedPlaceRepository; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*; import org.springframework.web.server.ResponseStatusException; import org.springframework.http.HttpStatus; import java.time.Instant; import java.util.*;
@RestController @RequestMapping("/api/places") public class PlaceController {
 private final SavedPlaceRepository places; public PlaceController(SavedPlaceRepository p){places=p;}
 public record PlaceRequest(@NotBlank @Size(max=120) String name,@DecimalMin("-90.0") @DecimalMax("90.0") double latitude,@DecimalMin("-180.0") @DecimalMax("180.0") double longitude,@Size(max=500) String address){}
 public record PlaceResponse(String id,String name,double latitude,double longitude,String address,Instant createdAt){}
 @PostMapping public PlaceResponse create(Authentication a,@Valid @RequestBody PlaceRequest r){return view(places.save(new SavedPlace(id(a),r.name().trim(),r.latitude(),r.longitude(),r.address())));}
 @GetMapping public List<PlaceResponse> list(Authentication a){return places.findAllByUserIdOrderByCreatedAtDesc(id(a)).stream().map(this::view).toList();}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(Authentication a,@PathVariable UUID id){var p=places.findByIdAndUserId(id,id(a)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Saved place not found"));places.delete(p);}
 private UUID id(Authentication a){return (UUID)a.getPrincipal();} private PlaceResponse view(SavedPlace p){return new PlaceResponse(p.getId().toString(),p.getName(),p.getLatitude(),p.getLongitude(),p.getAddress(),p.getCreatedAt());}
}
