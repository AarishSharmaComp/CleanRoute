package com.cleanroute.api;
import com.cleanroute.domain.*; import com.cleanroute.repository.*; import com.cleanroute.security.JwtService; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.http.*; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.transaction.annotation.Transactional; import org.springframework.web.bind.annotation.*; import org.springframework.web.server.ResponseStatusException; import java.util.Locale;
@RestController @RequestMapping("/api/auth") public class AuthController {
 private final AppUserRepository users; private final UserPreferenceRepository preferences; private final PasswordEncoder encoder; private final JwtService jwt;
 public AuthController(AppUserRepository u,UserPreferenceRepository p,PasswordEncoder e,JwtService j){users=u;preferences=p;encoder=e;jwt=j;}
 public record RegisterRequest(@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(min=8,max=72) String password,@NotBlank @Size(max=100) String displayName){}
 public record LoginRequest(@NotBlank @Email String email,@NotBlank String password){}
 public record AuthResponse(String token,String tokenType,UserView user){}
 public record UserView(String id,String email,String displayName){}
 @PostMapping("/register") @Transactional public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request){String email=request.email().trim().toLowerCase(Locale.ROOT);if(users.existsByEmail(email))throw new ResponseStatusException(HttpStatus.CONFLICT,"Email is already registered");AppUser u=users.save(new AppUser(email,encoder.encode(request.password()),request.displayName().trim()));preferences.save(new UserPreference(u.getId()));return ResponseEntity.status(201).body(response(u));}
 @PostMapping("/login") public AuthResponse login(@Valid @RequestBody LoginRequest request){AppUser u=users.findByEmail(request.email().trim().toLowerCase(Locale.ROOT)).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password"));if(!encoder.matches(request.password(),u.getPasswordHash()))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password");return response(u);}
 private AuthResponse response(AppUser u){return new AuthResponse(jwt.create(u),"Bearer",new UserView(u.getId().toString(),u.getEmail(),u.getDisplayName()));}
}
