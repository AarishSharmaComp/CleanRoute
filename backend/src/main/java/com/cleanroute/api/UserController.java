package com.cleanroute.api;
import com.cleanroute.domain.*; import com.cleanroute.repository.*; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.security.core.Authentication; import org.springframework.transaction.annotation.Transactional; import org.springframework.web.bind.annotation.*; import org.springframework.web.server.ResponseStatusException; import org.springframework.http.HttpStatus; import java.util.UUID;
@RestController @RequestMapping("/api/users/me") public class UserController {
 private final AppUserRepository users; private final UserPreferenceRepository preferences;
 public UserController(AppUserRepository u,UserPreferenceRepository p){users=u;preferences=p;}
 public record ProfileRequest(@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(max=100) String displayName){}
 public record PreferenceRequest(@NotNull TravelMode preferredTravelMode,@NotNull RoutePreference routePreference,boolean notificationsEnabled,@Min(1) @Max(5) int pollutionSensitivity){}
 public record UserResponse(String id,String email,String displayName,PreferenceResponse preferences){}
 public record PreferenceResponse(TravelMode preferredTravelMode,RoutePreference routePreference,boolean notificationsEnabled,int pollutionSensitivity){}
 @GetMapping public UserResponse get(Authentication auth){return view(find(id(auth)));}
 @PutMapping @Transactional public UserResponse update(Authentication auth,@Valid @RequestBody ProfileRequest r){AppUser u=find(id(auth));u.updateProfile(r.email().trim().toLowerCase(),r.displayName().trim());return view(u);}
 @PutMapping("/preferences") @Transactional public UserResponse updatePreferences(Authentication auth,@Valid @RequestBody PreferenceRequest r){UUID id=id(auth);find(id);UserPreference p=preferences.findById(id).orElseGet(()->preferences.save(new UserPreference(id)));p.update(r.preferredTravelMode(),r.routePreference(),r.notificationsEnabled(),r.pollutionSensitivity());return view(find(id));}
 private UUID id(Authentication a){return (UUID)a.getPrincipal();} private AppUser find(UUID id){return users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found"));}
 private UserResponse view(AppUser u){var p=preferences.findById(u.getId()).orElseGet(()->new UserPreference(u.getId()));return new UserResponse(u.getId().toString(),u.getEmail(),u.getDisplayName(),new PreferenceResponse(p.getPreferredTravelMode(),p.getRoutePreference(),p.isNotificationsEnabled(),p.getPollutionSensitivity()));}
}
