package com.cleanroute.security;

import com.cleanroute.domain.AppUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey key; private final long expirationMs;
    public JwtService(@Value("${app.jwt.secret:}") String secret,@Value("${app.jwt.expiration-ms:86400000}") long expirationMs) {
        if(secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
        this.key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); this.expirationMs=expirationMs;
    }
    public String create(AppUser user) { Date now=new Date(); return Jwts.builder().subject(user.getId().toString()).claim("email",user.getEmail()).issuedAt(now).expiration(new Date(now.getTime()+expirationMs)).signWith(key).compact(); }
    public UUID subject(String token) { return UUID.fromString(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject()); }
}
